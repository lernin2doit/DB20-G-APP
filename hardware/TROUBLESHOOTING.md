# DB20-G Interface Box v10 — Troubleshooting Guide

> **⚠ UNTESTED DESIGN** — This guide covers the v10 ESP32 Bluetooth design.
> Build at your own risk.

Common issues and solutions for building and using the v10 Bluetooth
interface box.

---

## 1. ESP32 Won't Boot / No Power LED

**Symptoms:** LED1 (green) never lights. No Bluetooth device visible on phone.

| Check | Action |
|-------|--------|
| Power supply | Verify 5 V on J4 input or radio handset pin 1 with a multimeter. |
| Polyfuse F1 | Measure voltage on both sides of F1. If input has 5 V but output is 0 V, F1 has tripped — check for shorts. |
| AMS1117 output | Measure U2 output pin. Must be 3.2–3.4 V. If 0 V, check solder joints or shorted 3.3 V rail. |
| ESP32 EN pin | Must be HIGH (3.3 V). Check R11 (10 kΩ pull-up) and C7 (100 nF). |
| GPIO0 | Must be HIGH at boot (run mode). Check R12 (10 kΩ pull-up). If LOW, chip enters flash mode. |
| Solder joints | Inspect all ESP32 ground pads (pins 1, 15, 38). Poor GND = no boot. |
| Bulk cap | C6 (22 µF) must be close to ESP32 VCC. Missing → brownout resets. |

**Quick Test:** Touch the AMS1117 tab — warm is normal, burning hot means a short on the 3.3 V rail. Disconnect power immediately and check for solder bridges.

---

## 2. Bluetooth Not Visible on Phone

**Symptoms:** Power LED blinks, but phone doesn't see "DB20G-Interface" in Bluetooth scan.

| Check | Action |
|-------|--------|
| Firmware | Was the firmware flashed successfully? Connect a USB-UART adapter to J5 and open a serial monitor at 115200 baud. Look for boot messages. |
| Antenna | The ESP32-WROOM-32E has a PCB antenna. Make sure no copper pour or ground plane is under the antenna area of the module. |
| Distance | Try pairing within 1 metre first. |
| Phone BT | Toggle Bluetooth off/on on the phone. Some phones cache old scan results. |
| Android 12+ | Ensure the app has `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions granted in phone Settings → Apps. |
| Classic BT | The ESP32 advertises Bluetooth Classic SPP, not BLE. The phone must scan for classic devices (not "Low Energy only"). |
| Re-flash | Erase flash completely: hold GPIO0 LOW, power cycle, then flash with `--erase-all` flag in esptool. |

---

## 3. Bluetooth Connects But No Serial Communication

**Symptoms:** Phone shows "Connected" but Download/Upload to radio fails.

| Check | Action |
|-------|--------|
| UART wiring | Verify UART2: GPIO17 (TX) → K1 relay NO contact → J2 pin 2 (MIC). GPIO16 (RX) ← K1 relay NO contact ← J2 pin 5 (SPK). |
| Relay state | For serial mode, relay K1 must be energized (NO contacts closed). Send a relay ON command from the app or check GPIO5 is HIGH during programming. |
| Baud rate | Must be 9600 8N1. If firmware has wrong baud, re-flash with correct settings. |
| Radio mode | Radio must be powered ON and idle (not scanning, not in menu). |
| RJ-45 cable | Use a straight-through (not crossover) RJ-45 cable between interface box J2 and radio handset port. |
| TX/RX swap | If consistently failing, try swapping TX and RX wires at K1 relay. |

**Quick Test:** With J5 serial monitor connected, send a test byte from the phone via the app. You should see it echoed in the monitor if the BT→UART bridge is working.

---

## 4. No Audio (Can't Hear Radio / Radio Can't Hear Phone)

**Symptoms:** Serial programming works, PTT works, but no voice audio.

### Can't Hear Radio on Phone (RX Path)

| Check | Action |
|-------|--------|
| Relay mode | Audio mode requires relay K1 de-energized (NC contacts). Send relay OFF command. |
| Audio enabled | Ensure the app is in audio/live mode and BT SCO link is active. |
| ADC wiring | Radio SPK signal → R3/R4 voltage divider → C8 → ESP32 GPIO36 (ADC). |
| Divider values | R3 = 10 kΩ (upper), R4 = 4.7 kΩ (lower). Gives ~0.32× attenuation. |
| ADC voltage | Measure GPIO36 with a scope/meter while radio receives audio. Should see 0.5–1.5 V AC riding on ~1.5 V DC bias. Must stay below 3.3 V. |
| RJ-45 pin 5 | Measure AC voltage on J2 pin 5 while radio receives. Should see ~0.5–2 V AC. |
| Gain setting | Try raising `rx_gain` via the app's config command (NVS key 0x02). |

### Radio Can't Hear Phone (TX Path)

| Check | Action |
|-------|--------|
| PTT first | Radio ignores MIC input unless PTT is held. Key up first. |
| DAC wiring | ESP32 GPIO25 (DAC) → R5/R6 voltage divider → C4 → J2 pin 2 (MIC via relay NC). |
| Divider values | R5 = 10 kΩ, R6 = 4.7 kΩ. Attenuates DAC output to radio mic level. |
| DAC output | Measure GPIO25 with a scope while phone plays audio. Should see ~0.3–1.5 V AC signal. |
| Coupling cap | C4 must be present for AC coupling — DC offset from DAC will bias the radio mic input. |
| Gain setting | Try raising `tx_gain` via the app's config command (NVS key 0x01). |
| VOX threshold | If audio frames aren't being sent, the VOX threshold may be too high. Check `AUDIO_VOX_THRESH` in firmware config. |

---

## 5. PTT Doesn't Work (Radio Won't Transmit)

**Symptoms:** Pressing PTT in app does nothing. Radio stays in receive.

| Check | Action |
|-------|--------|
| BT connected | PTT requires an active Bluetooth SPP connection. Check LED4 (blue) is solid. |
| GPIO4 | Measure ESP32 GPIO4 with a meter. Should go HIGH when PTT is pressed in app. |
| Q1 transistor | Check orientation (flat side per silkscreen). E-B-C pinout. |
| Base resistor | R1 (10 kΩ) between GPIO4 and Q1 base. Missing = no drive. |
| PTT line | Measure J2 pin 4: should be ~5–8 V idle, near 0 V when PTT active. |
| RJ-45 contact | Pin 4 on RJ-45 must make good contact. Try a different cable. |
| FCC timeout | PTT auto-releases after 3 minutes (FCC TOT). LED2 turns off and phone receives a PTT-off notification. Wait a moment and re-key. |
| Clamp diode | D1 (1N4148) across Q1 collector-emitter protects against inductive kick. Reversed polarity = PTT stuck. |

**Quick Test:** Temporarily jumper J2 pin 4 to pin 3 (GND). Radio should transmit. If it does, the issue is in the PTT driver circuit (Q1/R1/GPIO4).

---

## 6. Relay Doesn't Switch (Stuck in Audio or Serial Mode)

**Symptoms:** Always in one mode. Can't switch between serial programming and live audio.

| Check | Action |
|-------|--------|
| GPIO5 | Measure ESP32 GPIO5. Should toggle when relay command is sent. |
| Q2 transistor | Same checks as Q1 — orientation, base resistor R2 (10 kΩ). |
| Relay coil | Measure voltage across K1 coil. Should see ~5 V when energized. |
| 5 V rail | Relay coil needs 5 V (not 3.3 V). Verify 5 V is present on the relay coil supply. |
| Flyback diode | D2 (1N5819) must be across relay coil, cathode to +5 V. Wrong polarity = coil can't de-energize cleanly. |
| Click test | Send relay toggle from app. You should hear/feel the relay click. If no click, coil isn't getting current. |
| Contact wiring | Verify K1 COM, NC, NO pins are wired correctly per WIRING.md. Swapped NC/NO = inverted mode logic. |

---

## 7. Audio Hum, Buzz, or Noise

**Symptoms:** Audible interference in received or transmitted audio.

| Cause | Solution |
|-------|----------|
| Ground loop | Add a ferrite snap-on bead to the RJ-45 cable near the box. |
| Missing AC coupling caps | C4 (TX) and C8 (ADC filter) block DC. Without them, bias creates noise. |
| ESP32 digital noise | ADC shares die with WiFi/BT radio. Keep ADC traces short and away from antenna. |
| Power supply ripple | Check C1–C4 near AMS1117. Add a 100 µF electrolytic on the 5 V rail if ripple is high. |
| Long RJ-45 cable | Keep cable under 2 metres. Longer cables pick up RF from the radio. |
| Gain too high | Reduce `tx_gain` or `rx_gain` via app config. Clipping causes distortion artifacts. |

**Audio level adjustment (hardware):**
- RX too loud → increase R3 from 10 kΩ to 22 kΩ
- RX too quiet → decrease R3 to 4.7 kΩ
- TX too loud → increase R5 from 10 kΩ to 22 kΩ
- TX too quiet → decrease R5 to 4.7 kΩ

**Audio level adjustment (firmware):**
- Use the app's Config screen or send CMD_CONFIG (0x05) with NVS keys:
  - Key 0x01 = TX gain (0–255, default 128)
  - Key 0x02 = RX gain (0–255, default 128)

---

## 8. OTA Firmware Update Fails

**Symptoms:** Can't connect to "DB20G-Update" WiFi or upload times out.

| Check | Action |
|-------|--------|
| OTA mode | Send the OTA command from the app (CMD_CONFIG key 0xFF). ESP32 should stop BT and start WiFi AP. |
| WiFi AP | Look for SSID "DB20G-Update" on your phone's WiFi list. Password: `db20gota!` |
| Browse to 192.168.4.1 | Open a browser and go to `http://192.168.4.1`. You should see the firmware upload page. |
| File size | The .bin file must fit in the OTA partition (~1.5 MB with `min_spiffs` partition table). |
| Upload timeout | Large files over slow WiFi can time out. Stay close to the ESP32 (< 3 m). |
| Partition table | Firmware must be built with `min_spiffs.csv` partition scheme. Check `platformio.ini`. |
| Stuck in OTA | If OTA mode won't exit, power-cycle the ESP32. It boots to normal mode by default. |
| Flash fallback | If OTA is broken, use J5 UART header with esptool to flash directly. |

---

## 9. Hand Microphone Pass-through Doesn't Work

**Symptoms:** Hand mic plugged into J3 doesn't control the radio.

| Check | Action |
|-------|--------|
| J3 wiring | All 8 RJ-45 pins on J3 must connect to corresponding J2 pins. |
| Solder joints | Check all 16 through-hole joints on J2 and J3 connectors. |
| Cable | Use a straight-through (not crossover) RJ-45 cable. |
| Relay position | In audio mode (relay de-energized), hand mic PTT and audio should pass through on pins 2, 4, 5. In serial mode, mic/spk are routed to ESP32 UART instead. |
| Priority | Both hand mic PTT and app PTT can ground pin 4 simultaneously — this is normal. |

---

## 10. LEDs Behave Unexpectedly

| LED | Expected Behaviour | If Wrong |
|-----|-------------------|----------|
| LED1 (green) | 1 Hz heartbeat blink | Stuck off = no power or ESP32 crashed. Stuck on = firmware hang in `setup()`. |
| LED2 (red) | ON when PTT active | Stuck on = PTT stuck (check GPIO4 / Q1). Never lights = PTT driver issue. |
| LED3 (yellow) | Blinks on RX audio activity | Never blinks = no ADC audio (check RX path). Always on = ADC noise floor too high. |
| LED4 (blue) | Solid when BT connected, off when not | Never solid = BT not connecting. Solid but phone says disconnected = state mismatch (power-cycle ESP32). |

---

## 11. ESP32 Resets Randomly (Brownout)

**Symptoms:** Board randomly restarts. Serial monitor shows "Brownout detector was triggered".

| Check | Action |
|-------|--------|
| Power supply | ESP32 draws up to 350 mA during BT + WiFi TX. Supply must provide ≥ 500 mA at 5V. |
| Bulk cap C6 | 22 µF must be close to ESP32 VCC pin. Without it, transient current spikes cause dips. |
| AMS1117 thermal | If regulator is very hot, input voltage may be too high (>8 V). Check J4 input. |
| USB power bank | Some power banks go to sleep at low current, then ESP32 draws a spike on wake → brownout. Use a "dumb" 5 V supply. |
| Relay inrush | K1 relay coil draws ~75 mA when energized. If supply is marginal, relay switching can cause a brownout. Add 100 µF electrolytic on 5 V rail. |

---

## 12. Interface Box Gets Hot

| Check | Action |
|-------|--------|
| Short circuit | Disconnect power immediately. Check for solder bridges, especially on ESP32 GND pads and 3.3 V rail. |
| AMS1117 | Warm (up to 60 °C) is normal when dropping from 8 V to 3.3 V at 250 mA. Burning hot = shorted 3.3 V rail. |
| Current budget | Normal idle: ~80 mA. BT active: ~170 mA. BT + audio + relay: ~350 mA. Anything over 400 mA indicates a fault. |
| Input voltage | If powered from 12 V directly (instead of 5 V), AMS1117 must dissipate (12-3.3) × 0.25 = 2.2 W — it **will** overheat. Use a buck converter to 5 V first for 12 V inputs. |

---

## Firmware Flashing via J5 Header

If you need to recover a bricked ESP32 or flash firmware for the first time:

1. Connect a USB-UART adapter (e.g., CP2102 or CH340) to J5:
   - J5 pin 1 (3V3) → **do not connect** (board has its own 3.3 V supply)
   - J5 pin 2 (TX) → adapter RX
   - J5 pin 3 (RX) → adapter TX
   - J5 pin 4 (GND) → adapter GND
2. Hold GPIO0 LOW (jumper GPIO0 pad to GND) and power-cycle the ESP32.
3. Run: `pio run --target upload` (PlatformIO) or use esptool directly.
4. Remove the GPIO0 jumper and power-cycle to boot normally.

---

## Getting Help

If these steps don't resolve your issue:

1. Take clear photos of your PCB (both sides) under good lighting
2. Capture the ESP32 serial monitor output (115200 baud on J5) showing the boot sequence
3. Note your phone model, Android version, and app version
4. Post in the project's GitHub Issues with the above information
