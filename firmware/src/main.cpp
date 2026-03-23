/*
 * DB20G Interface Board — ESP32 Firmware (v10)
 *
 * Bridges the Android phone to the Radioddity DB20-G radio over Bluetooth.
 *
 * Functions:
 *  1. BT SPP serial bridge — relays programming protocol data (9600 baud)
 *     between the phone app and the radio's UART (via DPDT relay K1).
 *  2. PTT control — GPIO4 drives Q1 to key the radio's PTT line.
 *     Includes FCC-mandated 3-minute transmit timeout (TOT).
 *  3. Relay control — GPIO5 switches K1 between serial mode and audio
 *     pass-through mode.
 *  4. Audio bridge — DAC (GPIO25) outputs TX audio from phone to radio mic;
 *     ADC (GPIO36) captures RX audio from radio speaker to phone.
 *     Streamed over BT as raw 8-bit PCM at 8 kHz using CMD_AUDIO (0x04).
 *  5. Status LEDs — Power, PTT, Audio activity, BT connected.
 *  6. OTA firmware update — ESP32 starts a WiFi AP with HTTP upload endpoint.
 *  7. NVS config storage — persistent settings (BT name, audio gains, TOT).
 */

#include <Arduino.h>
#include <BluetoothSerial.h>
#include <Preferences.h>
#include <WiFi.h>
#include <WebServer.h>
#include <Update.h>
#include <driver/adc.h>
#include <driver/dac.h>
#include "pins.h"
#include "config.h"

// ---- Globals ----

BluetoothSerial btSerial;
HardwareSerial  radioSerial(2);   // UART2 on GPIO16 (RX) / GPIO17 (TX)
Preferences     nvs;
WebServer       otaServer(OTA_HTTP_PORT);

static bool     btConnected   = false;
static bool     pttActive     = false;
static uint32_t pttStartMs    = 0;
static uint32_t pttTimeoutMs  = PTT_TIMEOUT_MS;
static uint32_t lastHeartbeat = 0;

// Audio state
static bool     audioEnabled  = false;
static uint8_t  txGain        = 128;      // 0-255 software gain for TX DAC
static uint8_t  rxGain        = 128;      // 0-255 software gain for RX ADC
static uint8_t  audioBuf[AUDIO_BUF_SIZE];
static uint16_t audioBufIdx   = 0;
static uint32_t lastAudioTick = 0;
static bool     rxAudioActive = false;

// OTA state
static bool     otaMode       = false;

// ---- Forward declarations ----

void handleBtData();
void processCommand(uint8_t cmd, const uint8_t *payload, uint16_t len);
void forwardRadioToPhone();
void updateLeds();
void sampleRxAudio();
void enforcePttTimeout();
void loadNvsConfig();
void saveNvsConfig(uint8_t key, const uint8_t *val, uint8_t len);
void startOtaMode();
void handleOtaUpload();
void handleOtaRoot();

// ---- Setup ----

void setup() {
    // Debug UART (J5 header / USB-UART adapter)
    Serial.begin(115200);
    Serial.println("[DB20G] Booting...");

    // Load persistent config from NVS
    loadNvsConfig();

    // Pin modes
    pinMode(PIN_PTT,   OUTPUT);
    pinMode(PIN_RELAY, OUTPUT);
    pinMode(PIN_LED1,  OUTPUT);
    pinMode(PIN_LED2,  OUTPUT);
    pinMode(PIN_LED3,  OUTPUT);
    pinMode(PIN_LED4,  OUTPUT);

    // Safe defaults
    digitalWrite(PIN_PTT,   LOW);   // PTT released
    digitalWrite(PIN_RELAY, LOW);   // Audio pass-through mode
    digitalWrite(PIN_LED1,  HIGH);  // Power LED on
    digitalWrite(PIN_LED2,  LOW);
    digitalWrite(PIN_LED3,  LOW);
    digitalWrite(PIN_LED4,  LOW);

    // ADC for RX audio input
    adc1_config_width(ADC_WIDTH_BIT_12);
    adc1_config_channel_atten(ADC1_CHANNEL_0, ADC_ATTEN_DB_11);  // GPIO36, 0-3.3V

    // DAC for TX audio output
    dac_output_enable(DAC_CHANNEL_1);  // GPIO25

    // Radio UART — matches DB20-G programming protocol
    radioSerial.begin(RADIO_BAUD, SERIAL_8N1, PIN_RADIO_RX, PIN_RADIO_TX);

    // Bluetooth SPP
    btSerial.begin(BT_DEVICE_NAME);
    btSerial.register_callback([](esp_spp_cb_event_t event, esp_spp_cb_param_t *param) {
        switch (event) {
            case ESP_SPP_SRV_OPEN_EVT:
                btConnected = true;
                Serial.println("[DB20G] BT client connected");
                break;
            case ESP_SPP_CLOSE_EVT:
                btConnected = false;
                // Safety: release PTT and reset relay on disconnect
                pttActive = false;
                digitalWrite(PIN_PTT, LOW);
                digitalWrite(PIN_RELAY, LOW);
                audioEnabled = false;
                Serial.println("[DB20G] BT client disconnected — PTT released");
                break;
            default:
                break;
        }
    });

    Serial.printf("[DB20G] Ready — BT name: %s, PTT timeout: %lu ms\n",
                  BT_DEVICE_NAME, pttTimeoutMs);
}

// ---- Main Loop ----

void loop() {
    if (otaMode) {
        otaServer.handleClient();
        // Blink LED1+LED3 during OTA mode
        bool blink = ((millis() / 200) % 2) == 0;
        digitalWrite(PIN_LED1, blink);
        digitalWrite(PIN_LED3, !blink);
        return;
    }

    // BT → Radio: parse framed commands from the phone
    if (btSerial.available()) {
        handleBtData();
    }

    // Radio → Phone: forward raw UART bytes back over BT
    forwardRadioToPhone();

    // Audio: sample RX from radio ADC and send to phone
    sampleRxAudio();

    // FCC safety: enforce PTT transmit timeout
    enforcePttTimeout();

    // Status LEDs
    updateLeds();
}

// ---- BT Command Processing ----

static uint8_t rxBuf[1024];
static uint16_t rxLen = 0;

void handleBtData() {
    while (btSerial.available()) {
        if (rxLen >= sizeof(rxBuf)) {
            rxLen = 0;  // overflow protection — discard
        }
        rxBuf[rxLen++] = btSerial.read();

        if (rxLen < 1) continue;

        uint8_t cmd = rxBuf[0];

        if (cmd == CMD_SERIAL_DATA) {
            if (rxLen < 3) continue;
            uint16_t payloadLen = ((uint16_t)rxBuf[1] << 8) | rxBuf[2];
            if (payloadLen > sizeof(rxBuf) - 3) {
                rxLen = 0;
                continue;
            }
            if (rxLen < 3 + payloadLen) continue;
            processCommand(cmd, rxBuf + 3, payloadLen);
            rxLen = 0;

        } else if (cmd == CMD_AUDIO) {
            // Audio frame: CMD_AUDIO + len_hi + len_lo + PCM data
            if (rxLen < 3) continue;
            uint16_t payloadLen = ((uint16_t)rxBuf[1] << 8) | rxBuf[2];
            if (payloadLen > sizeof(rxBuf) - 3) {
                rxLen = 0;
                continue;
            }
            if (rxLen < 3 + payloadLen) continue;
            processCommand(cmd, rxBuf + 3, payloadLen);
            rxLen = 0;

        } else if (cmd == CMD_PTT || cmd == CMD_RELAY) {
            if (rxLen < 2) continue;
            processCommand(cmd, rxBuf + 1, 1);
            rxLen = 0;

        } else if (cmd == CMD_CONFIG) {
            // Config: CMD_CONFIG + key(1) + value(n); min 3 bytes total
            if (rxLen < 3) continue;
            // Variable length — for now, fixed 2-byte payload (key + 1 value byte)
            processCommand(cmd, rxBuf + 1, rxLen - 1);
            rxLen = 0;

        } else {
            rxLen = 0;
        }
    }
}

void processCommand(uint8_t cmd, const uint8_t *payload, uint16_t len) {
    switch (cmd) {
        case CMD_SERIAL_DATA:
            radioSerial.write(payload, len);
            break;

        case CMD_PTT:
            if (len >= 1) {
                bool down = (payload[0] != 0);
                if (down && !pttActive) {
                    pttActive = true;
                    pttStartMs = millis();
                    digitalWrite(PIN_PTT, HIGH);
                    Serial.println("[DB20G] PTT DOWN");
                } else if (!down && pttActive) {
                    pttActive = false;
                    digitalWrite(PIN_PTT, LOW);
                    Serial.println("[DB20G] PTT UP");
                }
            }
            break;

        case CMD_RELAY:
            if (len >= 1) {
                bool serialMode = (payload[0] != 0);
                audioEnabled = !serialMode;
                digitalWrite(PIN_RELAY, serialMode ? HIGH : LOW);
                Serial.printf("[DB20G] Relay → %s mode\n",
                              serialMode ? "SERIAL" : "AUDIO");
            }
            break;

        case CMD_AUDIO:
            // TX audio from phone → DAC → radio MIC
            for (uint16_t i = 0; i < len; i++) {
                uint16_t scaled = ((uint16_t)payload[i] * txGain) >> 7;
                if (scaled > 255) scaled = 255;
                dac_output_voltage(DAC_CHANNEL_1, (uint8_t)scaled);
                // Pace DAC output at 8 kHz (125 µs per sample)
                delayMicroseconds(125);
            }
            break;

        case CMD_CONFIG:
            if (len >= 2) {
                saveNvsConfig(payload[0], payload + 1, len - 1);
            }
            break;
    }
}

// ---- Radio → Phone forwarding ----

void forwardRadioToPhone() {
    if (!btConnected) return;

    int avail = radioSerial.available();
    if (avail <= 0) return;

    if (avail > 512) avail = 512;

    uint8_t buf[512];
    int n = radioSerial.readBytes(buf, avail);
    if (n > 0) {
        btSerial.write(buf, n);
    }
}

// ---- RX Audio Sampling (ADC → BT) ----

void sampleRxAudio() {
    if (!btConnected || !audioEnabled) return;

    // Sample at 8 kHz: every 125 µs
    uint32_t now = micros();
    if (now - lastAudioTick < 125) return;
    lastAudioTick = now;

    // Read 12-bit ADC, scale to 8-bit
    int raw = adc1_get_raw(ADC1_CHANNEL_0);
    uint8_t sample = (uint8_t)(raw >> 4);  // 12-bit → 8-bit

    // Apply RX gain
    uint16_t scaled = ((uint16_t)sample * rxGain) >> 7;
    if (scaled > 255) scaled = 255;

    audioBuf[audioBufIdx++] = (uint8_t)scaled;

    // Track activity for LED
    rxAudioActive = (abs((int)sample - 128) > AUDIO_VOX_THRESH);

    // Send a frame when buffer is full (20 ms of audio)
    if (audioBufIdx >= AUDIO_BUF_SIZE) {
        uint8_t header[3] = {
            CMD_AUDIO,
            (uint8_t)(AUDIO_BUF_SIZE >> 8),
            (uint8_t)(AUDIO_BUF_SIZE & 0xFF)
        };
        btSerial.write(header, 3);
        btSerial.write(audioBuf, AUDIO_BUF_SIZE);
        audioBufIdx = 0;
    }
}

// ---- FCC PTT Timeout ----

void enforcePttTimeout() {
    if (!pttActive) return;

    if (millis() - pttStartMs >= pttTimeoutMs) {
        pttActive = false;
        digitalWrite(PIN_PTT, LOW);
        Serial.println("[DB20G] ⚠ PTT TIMEOUT — auto-released after 3 minutes (FCC TOT)");

        // Notify the phone app
        if (btConnected) {
            uint8_t notify[] = { CMD_PTT, 0x00 };
            btSerial.write(notify, 2);
        }
    }
}

// ---- NVS Configuration ----

void loadNvsConfig() {
    nvs.begin(NVS_NAMESPACE, true);  // read-only
    txGain      = nvs.getUChar(NVS_KEY_TX_GAIN, 128);
    rxGain      = nvs.getUChar(NVS_KEY_RX_GAIN, 128);
    pttTimeoutMs = nvs.getULong(NVS_KEY_PTT_TOT, PTT_TIMEOUT_MS);
    nvs.end();

    Serial.printf("[DB20G] NVS loaded: txGain=%u, rxGain=%u, pttTimeout=%lu ms\n",
                  txGain, rxGain, pttTimeoutMs);
}

void saveNvsConfig(uint8_t key, const uint8_t *val, uint8_t len) {
    nvs.begin(NVS_NAMESPACE, false);  // read-write

    switch (key) {
        case 0x01:  // TX gain
            if (len >= 1) { txGain = val[0]; nvs.putUChar(NVS_KEY_TX_GAIN, txGain); }
            break;
        case 0x02:  // RX gain
            if (len >= 1) { rxGain = val[0]; nvs.putUChar(NVS_KEY_RX_GAIN, rxGain); }
            break;
        case 0x03:  // PTT timeout (2 bytes, big-endian seconds)
            if (len >= 2) {
                uint16_t secs = ((uint16_t)val[0] << 8) | val[1];
                pttTimeoutMs = (uint32_t)secs * 1000UL;
                nvs.putULong(NVS_KEY_PTT_TOT, pttTimeoutMs);
            }
            break;
        case 0xFF:  // Enter OTA mode
            nvs.end();
            startOtaMode();
            return;
    }

    nvs.end();
    Serial.printf("[DB20G] NVS saved key=0x%02X\n", key);
}

// ---- OTA Firmware Update ----

void startOtaMode() {
    Serial.println("[DB20G] Entering OTA mode...");

    // Release PTT for safety
    pttActive = false;
    digitalWrite(PIN_PTT, LOW);
    digitalWrite(PIN_RELAY, LOW);

    // Stop BT to free memory for WiFi
    btSerial.end();
    btConnected = false;

    // Start WiFi AP
    WiFi.mode(WIFI_AP);
    WiFi.softAP(OTA_WIFI_SSID, OTA_WIFI_PASS);
    Serial.printf("[DB20G] WiFi AP started: SSID=%s, IP=%s\n",
                  OTA_WIFI_SSID, WiFi.softAPIP().toString().c_str());

    // HTTP routes
    otaServer.on("/", HTTP_GET, handleOtaRoot);
    otaServer.on("/update", HTTP_POST, []() {
        otaServer.sendHeader("Connection", "close");
        otaServer.send(200, "text/plain",
                       Update.hasError() ? "FAIL" : "OK — rebooting...");
        delay(500);
        ESP.restart();
    }, handleOtaUpload);

    otaServer.begin();
    otaMode = true;

    Serial.println("[DB20G] OTA server ready at http://192.168.4.1/");
}

void handleOtaRoot() {
    otaServer.send(200, "text/html",
        "<html><body>"
        "<h1>DB20G Firmware Update</h1>"
        "<form method='POST' action='/update' enctype='multipart/form-data'>"
        "<input type='file' name='firmware' accept='.bin'>"
        "<br><br><input type='submit' value='Upload Firmware'>"
        "</form></body></html>");
}

void handleOtaUpload() {
    HTTPUpload &upload = otaServer.upload();

    if (upload.status == UPLOAD_FILE_START) {
        Serial.printf("[DB20G] OTA upload: %s\n", upload.filename.c_str());
        if (!Update.begin(UPDATE_SIZE_UNKNOWN)) {
            Update.printError(Serial);
        }
    } else if (upload.status == UPLOAD_FILE_WRITE) {
        if (Update.write(upload.buf, upload.currentSize) != upload.currentSize) {
            Update.printError(Serial);
        }
    } else if (upload.status == UPLOAD_FILE_END) {
        if (Update.end(true)) {
            Serial.printf("[DB20G] OTA success: %u bytes\n", upload.totalSize);
        } else {
            Update.printError(Serial);
        }
    }
}

// ---- LED Management ----

void updateLeds() {
    uint32_t now = millis();

    // LED1: heartbeat (brief flash every 3s)
    if (now - lastHeartbeat >= LED_HEARTBEAT_ON + LED_HEARTBEAT_OFF) {
        lastHeartbeat = now;
    }
    digitalWrite(PIN_LED1, (now - lastHeartbeat < LED_HEARTBEAT_ON) ? HIGH : LOW);

    // LED2: PTT active
    digitalWrite(PIN_LED2, pttActive ? HIGH : LOW);

    // LED3: audio activity (blink when RX audio detected)
    if (audioEnabled && rxAudioActive) {
        digitalWrite(PIN_LED3, HIGH);
    } else {
        digitalWrite(PIN_LED3, LOW);
    }

    // LED4: BT connected
    if (btConnected) {
        digitalWrite(PIN_LED4, HIGH);
    } else {
        // Blink rapidly while advertising
        bool blink = ((now / LED_BT_SEARCH_ON) % 2) == 0;
        digitalWrite(PIN_LED4, blink ? HIGH : LOW);
    }
}
