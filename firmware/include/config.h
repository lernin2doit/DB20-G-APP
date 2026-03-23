#pragma once
// ============================================================
// DB20G Interface Board — Configuration Constants
// ============================================================

// Bluetooth
#define BT_DEVICE_NAME   "DB20G-Interface"
#define BT_PIN_CODE      "1234"    // Pairing PIN (user-configurable via NVS)

// Serial protocol framing (matches BluetoothRadioTransport.kt)
#define CMD_SERIAL_DATA  0x01      // Payload: len_hi, len_lo, data[]
#define CMD_PTT          0x02      // Payload: 0x01=down, 0x00=up
#define CMD_RELAY        0x03      // Payload: 0x01=serial, 0x00=audio
#define CMD_AUDIO        0x04      // Payload: raw 8-bit PCM samples
#define CMD_CONFIG       0x05      // Payload: key(1), value(n) — NVS config

// Audio
#define AUDIO_SAMPLE_RATE  8000    // 8 kHz — voice-grade for GMRS
#define AUDIO_DAC_BITS     8       // ESP32 DAC is 8-bit
#define AUDIO_BUF_SIZE     160     // 20 ms frame @ 8 kHz
#define AUDIO_VOX_THRESH   10      // ADC threshold for RX activity LED

// FCC PTT safety timeout (3 minutes = 180 seconds)
#define PTT_TIMEOUT_MS     180000UL

// LED blink patterns (ms)
#define LED_HEARTBEAT_ON   50
#define LED_HEARTBEAT_OFF  2950
#define LED_BT_SEARCH_ON   100
#define LED_BT_SEARCH_OFF  100
#define LED_BT_CONNECTED   1       // Solid on when connected

// OTA
#define OTA_WIFI_SSID    "DB20G-Update"
#define OTA_WIFI_PASS    "db20gota!"
#define OTA_HTTP_PORT    80

// NVS keys
#define NVS_NAMESPACE    "db20g"
#define NVS_KEY_BT_NAME  "bt_name"
#define NVS_KEY_TX_GAIN  "tx_gain"
#define NVS_KEY_RX_GAIN  "rx_gain"
#define NVS_KEY_PTT_TOT  "ptt_tot"
#define NVS_KEY_RELAY_DEF "relay_def"
