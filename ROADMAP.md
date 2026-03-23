# DB20-G Android App — Development Roadmap

> Prioritized feature roadmap for the Radioddity DB20-G GMRS remote control Android app and custom interface box. Informed by extensive community research across r/gmrs, r/amateurradio, CHIRP project, and real-world user feedback.
>
> **v2.0 ARCHITECTURE REDESIGN — COMPLETE** — The interface board has been
> redesigned from a USB-tethered "dumb" pass-through to an ESP32-based
> Bluetooth smart controller. The phone connects wirelessly, leaving the
> phone's USB port free for vehicle charging. All phases (BT-1 through BT-5)
> are complete pending hardware integration testing.
> See [Phase 5](#phase-5--bluetooth-hardware-redesign) for full details.

---

## Priority Legend

| Tag | Meaning |
|-----|---------|
| 🔴 P0 | Critical — build this first |
| 🟠 P1 | High — core experience improvements |
| 🟡 P2 | Medium — strong user demand |
| 🟢 P3 | Nice-to-have — polish and advanced features |
| 🔵 BT | Bluetooth redesign — v2.0 hardware + firmware + app |

---

## 🔵 Phase 5 — Bluetooth Hardware Redesign

### Motivation
The USB-tethered design has fundamental UX problems:
1. **Phone battery drain** — phone must power the board via OTG, draining its battery
2. **Port occupied** — phone's only USB-C port is tied up; can't charge while using radio
3. **Physical tether** — cable between phone and radio limits placement in a vehicle
4. **Fragile** — USB connections vibrate loose in vehicles; cable snags are common

The Bluetooth redesign solves all of these:
- Phone connects wirelessly via Bluetooth Classic (SPP for data, audio for voice)
- Board powers itself from an external source (vehicle 12V, radio handset port, or USB adapter)
- Phone stays on its vehicle USB charger or wireless charging pad
- No cable to snag, vibrate loose, or limit placement

### Architecture Change Summary

| Aspect | v1 (USB) | v2 (Bluetooth) |
|--------|----------|----------------|
| Phone connection | USB-C wired (OTG) | Bluetooth Classic wireless |
| Board intelligence | Dumb pass-through | Smart (ESP32 MCU + firmware) |
| Serial to radio | CP2102N USB-UART on board → USB to phone | ESP32 UART direct to radio |
| Audio to/from radio | CM108AH USB audio codec → USB to phone | ESP32 ADC/DAC or I2S codec |
| USB hub | FE1.1s (mux serial + audio to single USB) | Not needed |
| Crystal | 12MHz external (for FE1.1s) | ESP32 internal oscillator |
| Power source | Phone VBUS (drains phone battery) | External: vehicle 12V / radio / USB adapter |
| PTT control | CP2102N RTS/DTR serial line → transistor | ESP32 GPIO → transistor (direct) |
| Relay switching | Driven by CP2102N DTR | Driven by ESP32 GPIO |
| Phone charging | Blocked (port in use) | Free (phone on vehicle USB or wireless) |
| Firmware | None (all logic in Android app) | ESP32 firmware (BT stack, UART, audio, PTT) |

### ICs Removed (v1 → v2)

| IC | Function | Why Removed |
|----|----------|-------------|
| U1 FE1.1s | USB 2.0 4-port hub | No USB; ESP32 handles both serial and audio natively |
| U2 CP2102N | USB-UART bridge | ESP32 has native UART; no USB path needed |
| U3 CM108AH | USB audio codec | ESP32 ADC/I2S handles audio; BT audio to phone |
| Y1 12MHz crystal | FE1.1s clock source | ESP32 has internal 40MHz crystal on module |

### IC Added

| IC | Function | Why |
|----|----------|-----|
| U1 ESP32-WROOM-32E | MCU + Bluetooth Classic + BLE + WiFi | Replaces all 3 former ICs; provides wireless connectivity, UART, ADC/DAC, GPIO |

### Components Kept (with modifications)

| Ref | Component | v1 Role | v2 Role | Changes |
|-----|-----------|---------|---------|---------|
| U2 | AMS1117-3.3 | 5V→3.3V LDO for FE1.1s/CP2102N | 5V→3.3V for ESP32 | Keep as-is if 5V input; replace with buck converter if 12V input |
| K1 | G5V-2-DC5 DPDT relay | Switch MIC/SPK between serial and audio modes | Same function — ESP32 drives relay via GPIO→Q2 | Coil driver unchanged |
| Q1 | 2N2222A | PTT driver (RTS→base) | PTT driver (ESP32 GPIO→base) | Base resistor stays; drive signal changes from CP2102N RTS to ESP32 GPIO |
| Q2 | 2N2222A | Relay driver (DTR→base) | Relay driver (ESP32 GPIO→base) | Same change as Q1 |
| D1 | 1N4148 | PTT clamp diode | Same | No change |
| D2 | 1N5819 | Flyback diode (relay coil) | Same | No change |
| F1 | 500mA polyfuse | USB VBUS protection | Input power protection | May need to upsize if 12V input; 500mA fine for 5V |
| J2 | RJ-45 | Radio connection | Same | No change |
| J3 | RJ-45 | Handset pass-through | Same | No change |
| J4 | 2-pin header | External 5V input | External power input (5V or 12V) | If 12V, add buck converter before LDO |
| R1-R2 | 10k | Transistor base resistors | Same | No change |
| R3-R6 | Resistive dividers | Audio level matching (radio ↔ CM108AH) | Audio level matching (radio ↔ ESP32 ADC/DAC) | Values may change — ESP32 ADC is 0–3.3V vs CM108AH |
| R7-R10 | 330Ω | LED current limiting | Same — ESP32 GPIO is 3.3V, LEDs need ~10mA | May adjust to 220Ω for same brightness at 3.3V |
| R11-R12 | 5.1k CC pull-downs | USB-C device identification | **REMOVED** — no USB-C on board anymore | |
| LED1-4 | 0805 LEDs | Power, PTT, Audio, Serial indicators | Power, PTT, Audio, BT status indicators | LED4 changes from "Serial TX" to "BT Connected" |
| C1-C15 | Various | IC bypass + bulk decoupling | Reduced set — only ESP32 + AMS1117 bypass needed | ~8 caps vs 15 |
| MH1-2 | Mounting holes | Board mounting | Same | No change |

### New Components

| Ref | Component | Purpose |
|-----|-----------|---------|
| U1 | ESP32-WROOM-32E module | MCU, Bluetooth Classic + BLE, WiFi, UART, ADC/DAC, GPIO |
| J1 | USB-C or Micro-USB (optional) | ESP32 firmware flashing/debug only — NOT connected to phone in normal operation |
| U3 | CP2102N (optional, for J1) | USB-UART for firmware flashing; could use ESP32-S3 native USB instead |
| R16 | 10k | ESP32 EN pin pull-up |
| R17 | 10k | ESP32 GPIO0 pull-up (boot mode select) |
| C16 | 100nF | ESP32 EN pin decoupling (RC delay for stable boot) |
| C17 | 22uF | ESP32 3.3V bulk decoupling |

### Power Design Options

**Option A — 5V input (simplest, recommended for prototyping)**
- J4 = 2-pin header or USB-C (power only)
- F1 → AMS1117-3.3 → 3.3V rail → ESP32 + peripherals
- Relay K1 driven directly from 5V
- Source: USB wall adapter, vehicle USB port, or power bank

**Option B — Radio handset port power (if available)**
- DB20-G may provide 5-8V on an RJ-45 pin for accessories
- Needs investigation: which pin, what voltage, current capacity
- If available, no external power connector needed — board self-powers from the radio

**Option C — Vehicle 12V (permanent installation)**
- J4 = barrel jack or screw terminal for 12V
- Add buck converter (e.g., MP2315 5V/2A) before AMS1117, or single 12V→3.3V buck
- Add TVS diode for automotive transient protection
- Relay K1 still 5V coil — need 5V intermediate rail from buck converter

### ESP32 Pin Assignments (Preliminary)

| ESP32 GPIO | Function | Direction | Notes |
|------------|----------|-----------|-------|
| GPIO1 (TX0) | Debug UART TX | Output | To USB-UART for firmware flash |
| GPIO3 (RX0) | Debug UART RX | Input | From USB-UART for firmware flash |
| GPIO16 (TX2) | Radio UART TX | Output | To K1 P1_NO → radio MIC (serial mode) |
| GPIO17 (RX2) | Radio UART RX | Input | From K1 P2_NO → radio SPK (serial mode) |
| GPIO25 | DAC1 — TX audio out | Output | To K1 P1_NC → radio MIC (audio mode) via divider |
| GPIO36 (VP) | ADC1_CH0 — RX audio in | Input | From K1 P2_NC → radio SPK (audio mode) via divider |
| GPIO4 | PTT driver | Output | → R1 → Q1 base |
| GPIO5 | Relay driver | Output | → R2 → Q2 base |
| GPIO18 | LED1 (Power) | Output | Via R7 |
| GPIO19 | LED2 (PTT active) | Output | Via R8 |
| GPIO21 | LED3 (Audio activity) | Output | Via R9 |
| GPIO22 | LED4 (BT connected) | Output | Via R10 |
| EN | Enable (chip enable) | Input | Pull-up R16 + C16 |
| GPIO0 | Boot mode | Input | Pull-up R17; hold low for flash mode |

---

## 🔵 BT-1: KiCad Schematic Redesign

### Status: ✅ Complete

> **Implementation:** `hardware/kicad/generate_v10.py` generates the complete
> v10 schematic (`DB20G-Interface-v10.kicad_sch`). ERC result: **0 errors,
> 42 warnings** (41 expected lib_symbol warnings + 1 cosmetic net overlap).
> 42 components, 14 lib_symbols, 119 wires, 33 nets, 21 no-connects.

**Remove:**
- [x] Delete U1 FE1.1s symbol, instance, and all nets (VD18OUT, VD33OUT, XI, XO, FE_RESET, HUB_P1_DM, HUB_P1_DP, HUB_P2_DM, HUB_P2_DP)
- [x] Delete U2 CP2102N symbol, instance, and all nets (CP_TXD, CP_RXD, CP_TXLED, CTS_PU, PTT_CTRL, DTR_CTRL)
- [x] Delete U3 CM108AH symbol, instance, and all nets (CM_VDD18, CM_VREF, CM_GPIO4, CM_RESET, AUDIO_OUT, AUDIO_RX, AUDIO_RX_DIV, AUDIO_TX_DIV)
- [x] Delete Y1 crystal and associated caps C7, C8
- [x] Delete J1 USB-C connector (or repurpose for debug-only)
- [x] Delete R11, R12 (CC pull-downs — no USB-C device mode)
- [x] Delete R13 (FE_RESET pull-up), R14 (CTS pull-up), R15 (CM_RESET pull-up)
- [x] Delete C1-C6 (FE/CP/CM bypass caps), C12-C15 (FE/CM internal rail caps)
- [x] Delete #FLG01, #FLG03 if net topology changes

**Add:**
- [x] Create ESP32-WROOM-32E symbol definition (38 pins: power, GPIO, UART, ADC/DAC, EN, GPIO0, antenna)
- [x] Add U1 ESP32-WROOM-32E instance with all pin nets
- [x] Add R11 (10k EN pull-up), R12 (10k GPIO0 pull-up) *(renumbered from R16/R17)*
- [x] Add C7 (100nF EN decoupling), C6 (22uF ESP32 bulk) *(renumbered from C16/C17)*
- [x] Deferred J1 USB-C + U3 CP2102N — using UART header for flashing
- [x] Add J5 (1x4 pin header) for UART flash: 3.3V, TX, RX, GND

**Modify:**
- [x] Change PTT_CTRL net: was CP2102N pin 21 (RTS) → now ESP32 GPIO4 (ESP_PTT)
- [x] Change DTR_CTRL net: was CP2102N pin 23 (DTR) → now ESP32 GPIO5 (ESP_RELAY)
- [x] Change LED3 drive: was CM_GPIO4 → now ESP32 GPIO21 (ESP_LED3)
- [x] Change LED4 drive: was CP_TXLED → now ESP32 GPIO22 (ESP_LED4)
- [x] Update relay K1 connections: P1_NO/P2_NO now connect to ESP32 UART2 TX/RX
- [x] Update relay K1 connections: P1_NC/P2_NC now connect to ESP32 DAC/ADC via dividers
- [x] Adjust R3-R6 divider values for ESP32 ADC (0–3.3V input range, ~1Vpp audio)
- [x] Reduce LED resistors R7-R10 to 220Ω (3.3V GPIO vs 5V)
- [x] Renumber/cleanup nets after IC removal

**Validate:**
- [x] Run ERC — 0 errors (42 warnings: 41 lib_symbol + 1 cosmetic)
- [x] Verify all ESP32 GPIO assignments avoid bootstrap-sensitive pins
- [x] Verify power rail decoupling is adequate (ESP32 draws ~240mA peak during TX)

---

## 🔵 BT-2: ESP32 Firmware (New Codebase)

### Status: ✅ Complete

> **Implementation:** `firmware/` directory — PlatformIO + Arduino project.
> `main.cpp` implements BT SPP bridge, UART2, PTT, relay, LED control,
> 8 kHz audio streaming (ADC/DAC), OTA updates via WiFi AP, NVS config
> storage, and 3-minute FCC PTT timeout. See `firmware/README.md`.

**Create `firmware/` directory** with PlatformIO + Arduino project structure. ✅

**Core modules (implemented in `main.cpp`):**

- [x] **BT SPP** — Bluetooth SPP (Serial Port Profile) server
  - Advertises as "DB20G-Interface"
  - Accepts SPP connections from the Android app
  - Bidirectional data channel using framed command protocol
  - Connection state callback (auto-releases PTT on disconnect)

- [x] **BT audio** — Bluetooth audio streaming
  - CMD_AUDIO (0x04) framed protocol for bidirectional voice
  - 8 kHz 8-bit PCM, 160-byte (20 ms) frames
  - ADC sampling (GPIO36) with ring buffer → BT SPP
  - DAC playback (GPIO25) from incoming CMD_AUDIO frames
  - Software TX/RX gain scaling, VOX threshold detection

- [x] **Radio UART** — UART communication with DB20-G
  - UART2 at 9600 baud 8N1
  - Bridge between BT SPP data and radio serial port
  - Transparent pass-through via CMD_SERIAL_DATA (0x01) framing
  - Raw bytes forwarded radio→phone without framing

- [x] **Radio audio** — Analog audio interface to radio
  - DAC output (GPIO25) for TX audio → voltage divider → radio MIC
  - ADC input (GPIO36) for RX audio ← voltage divider ← radio SPK
  - Sample rate: 8kHz mono (voice-grade, matches radio bandwidth)
  - 160-byte ring buffer for streaming between BT and ADC/DAC

- [x] **PTT control** — PTT and relay management
  - GPIO4 → Q1 → PTT (key/unkey radio) via CMD_PTT (0x02)
  - GPIO5 → Q2 → K1 relay (switch serial ↔ audio mode) via CMD_RELAY (0x03)
  - Safety: auto-releases PTT on BT disconnect
  - 3-minute FCC TOT safety timeout with phone notification

- [x] **LED status** — LED indicator control
  - LED1: Power / heartbeat (1 Hz blink)
  - LED2: PTT active (red when keyed)
  - LED3: Audio activity (yellow — blinks on RX audio detection)
  - LED4: BT connected (solid) / advertising (off)

- [x] **OTA update** — Over-the-air firmware updates via WiFi
  - ESP32 WiFi AP mode ("DB20G-Update" / "db20gota!") for firmware upload
  - HTTP OTA endpoint at 192.168.4.1 with HTML upload form
  - Dual-partition scheme (`min_spiffs.csv`) for safe rollback
  - Streaming firmware upload via Arduino Update library, auto-reboot

- [x] **Config storage** — NVS (Non-Volatile Storage) for settings
  - TX gain, RX gain (0–255, default 128)
  - PTT timeout (configurable, default 180000 ms)
  - Persistent across power cycles via ESP32 Preferences library
  - Remote config via CMD_CONFIG (0x05) from phone

**Build system:**
- [x] PlatformIO project with `platformio.ini` (Arduino framework)
- [x] Pin definitions in `include/pins.h`, constants in `include/config.h`
- [x] Flashing via J5 UART header (documented in `firmware/README.md`)
- [x] Partition table: `min_spiffs.csv` (factory + ota_0 + ota_1 + nvs)
- [ ] Kconfig for build-time options *(if migrating to ESP-IDF — deferred)*

---

## 🔵 BT-3: Android App Refactor

### Status: ✅ Complete

> **Implementation:** `RadioTransport` interface wired throughout the app.
> `DB20GProtocol` accepts `RadioTransport`. `RadioViewModel` supports dual
> USB + BT transport with runtime switching. Combined device picker in
> `MainActivity`. BT permissions already in manifest.

The existing app already has `BluetoothPttManager` for BLE HID buttons and audio routing. The refactor extends this to be the **primary** communication channel.

**New class: `BluetoothRadioTransport.kt`** *(was BluetoothSerialManager)*
- [x] Bluetooth Classic SPP client (connect to ESP32 "DB20G-Interface")
- [x] Same interface contract: `connect()`, `write()`, `read()`, `disconnect()`
- [x] Command framing protocol (0x01 serial, 0x02 PTT, 0x03 relay, 0x04 audio)
- [ ] Auto-reconnect with exponential backoff *(deferred — needs real hardware testing)*

**New class: `BluetoothAudioBridge.kt`**
- [x] BT SCO audio link management (start/stop SCO)
- [x] Route phone mic → BT SCO → ESP32 → DAC → radio MIC (TX path)
- [x] Route radio SPK → ADC → ESP32 → BT SCO → phone speaker (RX path)
- [x] VOX detection in the app (analyze audio amplitude)
- [x] Audio level metering (callback with peak amplitude)
- [ ] Full integration testing with real hardware *(deferred)*

**Refactor `RadioViewModel.kt`**
- [x] Abstract serial transport behind interface: `RadioTransport`
  - [x] `UsbRadioTransport` implements `RadioTransport` (wraps UsbSerialManager)
  - [x] `BluetoothRadioTransport` implements `RadioTransport` (BT SPP)
- [x] Transport selection: BT and USB both available via device picker
- [x] `activeTransport` property switches between USB and BT at runtime
- [x] Update `DB20GProtocol` to accept `RadioTransport` instead of `UsbSerialManager`
- [x] PTT, disconnect, pollRxStatus all route through `activeTransport`
- [x] `TransportType` enum (USB, BLUETOOTH) exposed via LiveData

**Update UI**
- [x] Combined device picker: shows BT paired devices + USB serial devices
- [x] Connection indicator: shows "(BT)" or "(USB)" in connection pill
- [ ] First-launch BT pairing wizard *(deferred — standard Android pairing works)*
- [ ] Firmware update screen for OTA *(deferred)*

**Permissions**
- [x] BT permissions in manifest: `BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN`
- [x] USB host permissions retained for fallback/development use

---

## 🔵 BT-4: Hardware Documentation Update

### Status: ✅ Complete

> **Implementation:** All hardware docs rewritten for v10 Bluetooth design.
> Old v9 files archived in `hardware/archive/`. v10 BOM and README promoted
> to primary filenames.

All hardware docs rewritten for the new architecture:

- [x] **hardware/README.md** — v10 block diagram, ESP32 pin table, command protocol, design decisions
- [x] **hardware/BOM.md** — v10 BOM (~30 components, ~$5.28 total), updated sourcing
- [x] **hardware/ASSEMBLY.md** — v10 build guide (5 phases: power, ESP32, PTT/relay, audio/LEDs, flash header)
- [x] **hardware/WIRING.md** — ESP32 GPIO map, connector pinouts, signal routing, audio level matching
- [x] **hardware/TROUBLESHOOTING.md** — v10 BT-specific troubleshooting (12 sections)
- [x] **hardware/KICAD-CHANGES.md** — Complete v9→v10 schematic change log
- [ ] **hardware/enclosure/DB20G-Enclosure.scad** — Resize for smaller board *(deferred — needs PCB dimensions)*
- [ ] **docs/cable-build-guide.md** — Archived (v9 USB cable no longer needed; RJ-45 cable is standard)

---

## 🔵 BT-5: Power Source Investigation

### Status: ✅ Resolved (Design Assumption)

> **Assumption:** The DB20-G handset port supplies +5–8 V on RJ-45 pin 1 for
> accessory power (the hand microphone is backlit, confirming voltage is
> present).  The board is designed to accept this as its primary power source.
> Physical measurement with a multimeter is still recommended before first
> power-on to confirm exact voltage and current capacity.

- [x] Design assumption: handset port RJ-45 pin 1 provides +5–8 V (backlit handset confirms supply)
- [x] Voltage regulation path: Pin 1 → F1 polyfuse → AMS1117-3.3 → 3.3 V rail
- [x] External power fallback: J4 2-pin header accepts 5 V from USB adapter or vehicle lighter
- [x] Current budget documented: ~350 mA peak (ESP32 BT TX + relay + LEDs)
- [x] AMS1117 thermal margin: 1.7 V drop × 0.35 A = 0.6 W — within SOT-223 limits for 5 V input
- [ ] **TODO (requires hardware):** Measure actual pin 1 voltage and current under load with multimeter
- [ ] **TODO (requires hardware):** Test ESP32 power consumption in all modes (idle, BT, BT+audio, WiFi OTA)
- [ ] **TODO (requires hardware):** Verify AMS1117 doesn't overheat if radio supplies 8 V (8-3.3)×0.35 = 1.6 W — may need heatsink

---

## Implementation Order

```
BT-1  KiCad Schematic ────┐
                           ├──► BT-4 Docs ──► BT-5 Power Investigation
BT-2  ESP32 Firmware ──────┤
                           │
BT-3  Android App Refactor ┘
```

**All phases complete.** BT-1 (schematic), BT-2 (firmware), BT-3 (app refactor),
BT-4 (docs), and BT-5 (power design) are done. Remaining work requires physical
hardware for integration testing and validation.

---

## P0 — Critical Priority (Build First)

### 1. Setup Wizard & Quick-Start Templates ✅
**Why:** Programming difficulty is the #1 complaint across every GMRS community. Users say "I got my radio but no idea how to use it" (121 upvotes), "my only complaint is programming it." This is the single biggest barrier to adoption.

- [x] First-launch wizard: callsign entry → location permission → auto-detect radio → one-tap program
- [x] Pre-built channel templates:
  - **GMRS Standard 22** — all simplex channels, correct power limits
  - **Emergency Kit** — Ch 20 (unofficial emergency), local repeaters, FRS interop channels
  - **Road Trip** — *(deferred to P1-5 Travel Route Repeater Planner)*
  - **Family Pack** — simplified subset with privacy codes pre-set
- [x] "Program My Radio in 60 Seconds" flow for absolute beginners
- [x] Plain-language channel descriptions (not just frequencies)

### 2. RepeaterBook API Integration ✅
**Why:** Our static JSON with 12 sample repeaters is inadequate. Community universally relies on RepeaterBook and myGMRS. "Repeater-book vs MyGMRS" is a recurring thread. Users need real, live repeater data.

- [x] RepeaterBook GMRS API integration (free, well-documented)
- [x] Search by location, radius, state, frequency
- [x] Cache results locally with configurable refresh interval
- [x] Show repeater details: tone, offset, status, last verified date, notes
- [x] One-tap "Program This Repeater" — writes to next available channel
- [ ] Map view with repeater pins and coverage radius estimates *(deferred — requires Maps SDK)*
- [x] Fallback to cached data when offline

### 3. Programming Validation Engine ✅
**Why:** Users can accidentally program illegal frequencies, wrong power levels, or mismatched tones. Multiple posts about "program it right" (284 upvotes). Bad programming can violate FCC rules or cause interference.

- [x] Real-time validation as channels are edited:
  - Frequency within GMRS allocation
  - Power level ≤ FCC limit for channel type (0.5W FRS-shared, 5W simplex-only, 50W repeater)
  - Correct repeater offset (± 5 MHz) for channels 15R–22R
  - CTCSS/DCS tone matches known repeater if programmed from database
- [x] Warning vs. Error distinction (yellow caution vs. red block)
- [x] "Why is this wrong?" explainer for each validation rule
- [x] Batch validation: scan all 500 channels and report issues
- [x] Block upload of clearly illegal configurations

### 4. CHIRP CSV Import/Export ✅
**Why:** CHIRP is the industry standard. Confirmed DB20-G is in CHIRP's supported list. Users who already have CHIRP files need seamless migration. Community repeatedly asks about programming tool compatibility.

- [x] Import CHIRP .csv and .img files
- [x] Export to CHIRP-compatible .csv
- [x] Field mapping for DB20-G-specific settings
- [x] Preview imported channels before writing to radio
- [x] Handle frequency/tone format differences gracefully
- [x] Support drag-and-drop or file picker import

---

## P1 — High Priority (Core Experience)

### 5. Travel Route Repeater Planner ✅
**Why:** Unique killer feature no other GMRS app offers. Users describe hitting repeaters 30-35 miles away on road trips. With 500 channels across GMRS, repeater, and programmable slots, route planning helps users organize optimal repeater lists for each trip.

- [x] Enter start/end addresses or pick on map
- [x] Query RepeaterBook for repeaters along route corridor
- [x] Auto-generate optimized channel list for the trip
- [x] Show coverage gaps where no repeaters exist
- [x] One-tap program entire route into radio
- [x] Save route plans for reuse
- [x] Estimated handoff points between repeaters

### 6. Emergency Features Suite ✅
**Why:** "Some GMRS Facts for Emergency Planning" (138 upvotes, 55 comments) shows massive community interest. No official emergency channel exists — users need guidance and tools.

- [x] Dedicated emergency mode (large PTT, high-contrast UI, essential info only)
- [x] Emergency channel quick-tune: Ch 20 (unofficial emergency), Ch 16 (calling), local repeaters
- [x] SOS beacon: automated distress call with GPS coordinates in voice (TTS)
- [x] Emergency net check-in protocol assistant (structured status reports)
- [x] GPS coordinate readout (voice/DTMF) for SAR teams
- [x] "Dead man's switch" — auto-transmit if no user input for configurable period
- [ ] Battery-saver scanning mode (radio duty-cycle optimization) *(deferred — requires firmware-level duty cycling)*
- [x] Emergency contact list with last-known check-in times
- [x] Offline operation — all critical features work without internet

### 7. Bluetooth PTT Button Support ✅
**Why:** Safe vehicle operation requires hands-free PTT. Users mount DB20-G in vehicles (trunk, under seat) and need remote control. Community discusses Android Auto integration and vehicle radio setups.

- [x] BLE HID button mapping for PTT
- [x] Support common Bluetooth PTT buttons (e.g., Sena, generic BLE buttons)
- [x] Configurable button actions (PTT, channel up/down, emergency)
- [x] Audio routing through Bluetooth headset/speaker
- [x] Multiple simultaneous Bluetooth devices (PTT button + audio headset)

### 8. Background Service & Persistent Notification ✅
**Why:** Radio must keep working when the phone screen is off or another app is in foreground. Users mount the phone in a vehicle and switch between navigation and radio.

- [x] Foreground service with persistent notification
- [x] Notification shows: current channel, frequency, signal activity
- [x] Quick actions from notification: PTT (if hardware button), channel up/down
- [x] Wake lock management for continuous monitoring
- [x] Configurable auto-sleep timer
- [x] Survive app process kills (auto-restart service)

### 9. Audio Recording & Logging ✅
**Why:** Community discusses logging for emergency coordination, repeater monitoring, and general record-keeping.

- [x] Record all received audio with timestamps
- [x] Record transmitted audio
- [x] Per-channel recording toggle
- [x] Playback with channel/time metadata
- [x] Export recordings as WAV
- [x] Activity log: PTT events, channel changes, repeater connections, callsign IDs
- [x] Storage management (auto-delete after X days, max size)

### 10. Channel & Memory Management
**Why:** DB20-G has 500 channels (30 GMRS + 9 repeater + 454 programmable + 7 NOAA). Users complain about wanting to organize repeaters efficiently. Smart management of channel space is essential.

- [x] Channel groups/banks (e.g., "Local Repeaters," "Family," "Travel")
- [x] Drag-and-drop channel reordering
- [x] Bulk edit (select multiple → change power/tone/etc.)
- [x] Channel search/filter by name, frequency, tone
- [x] "Favorites" pin for quick access
- [x] Clone channel profiles between slots
- [x] Undo/redo for all channel edits
- [x] Diff view: compare radio contents vs. app configuration before upload
- [x] Backup/restore channel configurations to cloud or local file

---

## P2 — Medium Priority (Strong User Demand)

### 11. Dark Mode & AMOLED Theme ✅
**Why:** Night driving, camping, emergency use — all benefit from dark UI. AMOLED true-black saves battery. Standard expectation for modern Android apps.

- [x] Full Material Dark theme (default)
- [x] AMOLED true-black variant (pure #000000 backgrounds)
- [x] Auto-switch based on system setting or time of day
- [x] Red-light mode for night vision preservation (red-tinted UI, minimum brightness)
- [x] Theme persistence via SharedPreferences
- [x] Smooth theme transition without activity restart

### 12. Custom Interface Box PCB Design
**Why:** Current breadboard/perfboard design works but isn't durable for permanent vehicle installation. Community builds suggest more robust solutions.

- [x] KiCad schematic (fully wired with labels, no-connects, all symbols)
- [x] KiCad PCB layout (all footprints with pad definitions, net assignments, partial routing)
- [x] Single board: CP2102N + CM108AH + PTT circuit + audio attenuators + relay
- [x] 3D-printable enclosure design (STL files)
- [x] USB-C connector for phone side
- [x] RJ-45 pass-through for handset
- [x] LED indicators: power, PTT active, audio activity, serial TX/RX
- [ ] Complete PCB trace routing (USB diff pairs, remaining signals — use KiCad autorouter or manual)
- [ ] Run DRC in KiCad (requires KiCad installation)
- [ ] Generate Gerber files for PCB fabrication (JLCPCB/PCBWay ready)
- [x] Bill of materials with component sourcing links
- [x] In-app hardware setup guide with interactive assembly checklist
- [x] Wiring diagram and pin mapping reference
- [x] Hardware troubleshooting guide (common issues + solutions)

### 13. Scanner / Priority Scan
**Why:** Users want to monitor multiple channels and stop on activity. The DB20-G supports scanning but configuration is clunky via keypad.

- [x] Software-controlled scan list configuration
- [x] Priority channel (auto-return to check priority channel during scan)
- [x] Scan speed configuration (slow/medium/fast)
- [x] Visual scan indicator showing current monitored channel
- [x] Activity log during scan (which channels had traffic, when)
- [x] "Nuisance delete" — temporarily skip noisy channels during scan
- [x] Dual-watch mode (monitor two channels, leverages DB20-G TDR)
- [x] Talk-back timer (auto-resume scan after TX ends)

### 14. Signal & Audio Quality Monitoring
**Why:** Users discuss range testing, antenna performance, and repeater reach. Quantitative signal data helps optimize setups.

- [x] Real-time audio level meter with peak hold
- [x] Signal strength history graph (RSSI proxy via squelch-break detection)
- [x] Audio quality scoring based on noise floor analysis
- [x] Range test mode: automated call-response with distance logging
- [x] Squelch level visualization and remote adjustment
- [x] Audio equalizer for RX clarity (bass cut, treble boost for voice)
- [x] FFT spectrum view of received audio
- [x] Export signal reports as CSV

### 15. Multi-Radio Support ✅
**Why:** Power users and emergency coordinators manage multiple radios. Some users have DB20-G mobile + GM-30 HT + base station.

- [x] Detect and manage multiple connected USB serial radios
- [x] Per-radio configuration profiles with naming/labeling
- [x] Cross-radio channel sync
- [x] Simultaneous monitoring of multiple radios (with interface boxes)
- [x] Radio inventory with firmware versions and serial numbers
- [x] Quick-switch between active radio

### 16. Social & Community Features ✅
**Why:** GMRS community is active but fragmented. In-app community features increase engagement and mutual aid. "Someone who can help has to be listening" — community networking solves this.

- [x] QSO logging with contact database (local, no server needed)
- [x] Net schedule database with local storage and import/export
- [x] In-app repeater reporting (status, tone corrections, coverage feedback)
- [x] Share channel configurations via Android share sheet (JSON export)
- [x] Contact log search and statistics (total contacts, per-channel, per-callsign)
- [x] Export QSO log as ADIF or CSV

---

## P3 — Nice-to-Have (Polish & Advanced)

### 17. Android Auto / Vehicle Head Unit Integration ✅
**Why:** "HAM radio phone apps on Android Auto" thread shows strong demand. Vehicle-mounted DB20-G with phone on dash mount is the primary use case.

- [x] Android Auto compatible UI (simplified, large buttons, driving-safe layout)
- [x] Voice commands: "Change to channel 19," "Key up," "What channel am I on?"
- [x] Steering wheel button mapping for PTT
- [x] Integration with vehicle Bluetooth for audio routing
- [x] Audio focus management (handle navigation prompt interruptions gracefully)
- [x] MediaSession integration for car head unit display

### 18. Text Messaging Over Radio (Data Modes) ✅
**Why:** "An app that sends messages over radio waves" (77 comments). GMRS allows data transmission. Text over audio is possible with existing hardware.

- [x] AFSK text messaging (Bell 202 standard 1200 baud for compatibility)
- [x] Pre-defined quick messages ("En route," "At destination," "Need assistance")
- [x] GPS position sharing via encoded audio bursts
- [x] Message acknowledgment protocol with configurable retry attempts
- [x] Conversation thread view with message history
- [x] Compatibility with existing amateur radio text modes where legal

### 19. SSTV Image Transmission ✅
**Why:** Amateur radio community desires SSTV on Android. Could work over GMRS audio channel for slow-scan images (weather maps, situation photos in emergencies).

- [x] SSTV encode/decode (Robot36, Martin M1 modes)
- [x] Camera capture → encode → transmit
- [x] Receive and display incoming SSTV images with auto-detection
- [x] Image gallery with received picture history
- [x] Thumbnail preview during reception

### 20. Spectrum Display / Waterfall ✅
**Why:** SDR enthusiasts want visual frequency monitoring. Limited without SDR hardware, but audio spectrum analysis is possible with CM108.

- [x] Audio waterfall display of received signal with color palette options
- [x] Tone decoder visualization (CTCSS/DCS detection)
- [x] Audio spectrum analyzer for troubleshooting
- [x] Configurable FFT window size and overlap
- [x] Screenshot/export capability for spectrum captures

### 21. Weather Alert Integration ✅
**Why:** GMRS users often operate during severe weather. Integration with NWS alerts adds safety value.

- [x] NOAA weather alert monitoring (internet-based, since DB20-G doesn't have weather band)
- [x] Location-based severe weather notifications with configurable radius
- [x] Auto-switch to emergency mode during active warnings
- [x] Weather channel frequencies reference (for radios that support them)
- [x] Alert severity color coding (watch/warning/emergency)
- [x] Push notifications for severe weather alerts

### 22. Widget & Quick Settings Tile
**Why:** Fast access without opening the full app. Useful for vehicle-mounted scenarios.

- [x] Home screen widget: current channel, PTT status, quick channel switch (multiple sizes: 2x1, 4x2)
- [x] Quick Settings tile: PTT toggle, current channel display
- [x] Lock screen controls for PTT and channel
- [x] Widget theme matching app theme

### 23. Comprehensive FCC Compliance Tools
**Why:** New GMRS licensees are often confused about rules. In-app guidance prevents violations and builds confidence.

- [x] License lookup and validation (FCC ULS database API)
- [x] Family member authorization tracker (GMRS license covers household)
- [x] Power limit reference by channel with visual chart
- [x] Callsign ID compliance monitor (enhance with logging/reporting)
- [x] FCC Part 95 quick-reference organized by common questions
- [x] License renewal reminders with expiration countdown
- [x] Real-time violation alerts (e.g., high power on FRS-only channel)

### 24. Localization, Accessibility & Real-Time Translation
**Why:** Broadens user base and ensures usability for all operators. Real-time translation over radio is a game-changer for cross-language emergency coordination, border-area operations, and international GMRS use.

- [x] Spanish language support (significant GMRS user base)
- [x] TalkBack/screen reader compatibility with content descriptions
- [x] High-contrast mode beyond dark theme
- [x] Configurable font sizes (small/medium/large/extra-large)
- [x] Haptic feedback for PTT confirmation (useful when not looking at screen)
- [x] **Real-time radio audio translation** — detect incoming spoken language and translate to user's preferred language via on-device speech-to-text → translation → TTS pipeline
  - [x] Toggle on/off per channel or globally
  - [x] On-device ML models (Android ML Kit / Whisper) for offline capability
  - [x] Supported language pairs: English ↔ Spanish (priority), expandable to other languages
  - [x] Visual transcript overlay showing original + translated text
  - [x] Adjustable confidence threshold (suppress low-confidence translations)
  - [x] Latency target: <2 seconds end-to-end for near-real-time experience
  - [x] Option to auto-translate outgoing TTS messages before transmission
  - [x] Translation log/history with export
  - [x] Language model download manager (show model sizes, manage storage)
  - [x] Privacy-focused: all processing on-device, no cloud upload of audio

---

## Implementation Phases

### Phase 1 — Foundation (P0)
Items 1–4. Establishes the app as a genuinely useful, safe, beginner-friendly programming tool that integrates with the GMRS ecosystem. This alone makes the app worth downloading.

### Phase 2 — Power Features (P1)
Items 5–10. Transforms the app from a programming tool into a full radio operating system. Travel planner and emergency suite are differentiators no competitor offers.

### Phase 3 — Polish & Community (P2)
Items 11–16. Refines the experience, hardens the hardware, and builds community features. Multi-radio support opens the door to power users and emergency coordinators.

### Phase 4 — Advanced & Experimental (P3)
Items 17–24. Pushes into vehicle integration, data modes, and advanced features. Each item is independently valuable and can be built as interest/demand materializes.

---

## 📋 Project Assessment — What Still Needs to Be Done

> **Full audit performed 2026-03-20.** Every Kotlin source file, firmware file,
> hardware document, resource file, and build config was inspected. Below is
> the complete list of remaining work, grouped by severity.

### Audit Summary

| Area | Status |
|------|--------|
| Android app (84 .kt files, 18,128 LOC) | ✅ All 21 features implemented as real code |
| ESP32 firmware (398 LOC) | ✅ Complete (SPP, audio, OTA, NVS, TOT) |
| KiCad v10 schematic | ✅ Complete (0 ERC errors) |
| Hardware documentation (6 docs) | ✅ Complete |
| Android resources (24 layouts, strings, themes) | ✅ Complete |
| **PCB layout & Gerber files** | ❌ **Not started** |
| **Enclosure (v10 update)** | ❌ **Stale — designed for v9** |
| **Test coverage** | ❌ **Zero tests** |
| **CI/CD pipeline** | ❌ **None** |
| **Play Store readiness** | ❌ **Multiple blockers** |

---

### 🔴 Critical — Blocks Hardware Fabrication

#### HW-1: PCB Layout & Gerber Generation *(Not Started)*

The v10 schematic is complete but there is **no PCB layout** — the board cannot be
manufactured. This is the single biggest blocker for the entire project.

- [ ] Create `DB20G-Interface-v10.kicad_pcb` with footprint placement
- [ ] Create `DB20G-Interface-v10.kicad_pro` project file linking schematic + PCB
- [ ] Route all traces (priority: ESP32 power, UART, analog audio, antenna keepout)
- [ ] USB differential pairs N/A for v10 — but analog audio traces need guard grounding
- [ ] ESP32 antenna keepout zone (≥10 mm clear of ground plane and copper on all layers)
- [ ] Run DRC in KiCad — resolve all errors
- [ ] Generate Gerber files (JLCPCB/PCBWay ready: F.Cu, B.Cu, F.Mask, B.Mask, F.Silk, B.Silk, Edge.Cuts, drill)
- [ ] Generate BOM CSV and pick-and-place CPL for PCBA services
- [ ] Review mounting hole count — schematic has MH1+MH2 but enclosure references 4× M3

**Depends on:** Nothing — can start immediately
**Blocks:** Hardware fabrication, enclosure update, integration testing

#### HW-2: Enclosure Update for v10 *(Stale)*

The OpenSCAD enclosure (`DB20G-Enclosure.scad`) was designed for the v9 USB board
and has the wrong cutouts and dimensions for v10.

- [ ] Update PCB dimensions to match v10 .kicad_pcb Edge.Cuts (after HW-1)
- [ ] Remove J1 USB-C cutout (v10 has no USB-C connector)
- [ ] Add J4 power header cutout (2-pin, likely on board edge)
- [ ] Add J5 UART flash header cutout (1×4 pin header)
- [ ] Add ESP32 antenna clearance window (≥10 mm keepout from enclosure wall, or open slot)
- [ ] Verify RJ-45 (J2/J3) cutout positions match v10 footprint placement
- [ ] Verify LED1-4 light pipe positions match v10 layout
- [ ] Update version label from "v2.0" to "v10" or remove version from enclosure
- [ ] Re-export STL and verify printability

**Depends on:** HW-1 (need PCB dimensions)

---

### 🟠 High — Blocks Release & Store Submission

#### APP-1: Fix `android.hardware.usb.host` Requirement

[AndroidManifest.xml line 4](app/src/main/AndroidManifest.xml) declares:
```xml
<uses-feature android:name="android.hardware.usb.host" android:required="true" />
```
This **blocks installation on any device without USB host hardware**. Since v10 is
primarily Bluetooth, USB is a fallback. Must change to `android:required="false"`.

- [ ] Change `android:required="true"` to `android:required="false"` in manifest
- [ ] Verify BT-only flow works without USB host support

#### APP-2: Update `targetSdk` to 35

Google Play requires `targetSdk 35` as of August 2025. Current value is 34.

- [ ] Bump `targetSdk` to 35 in `app/build.gradle.kts`
- [ ] Audit for Android 15 behavioral changes (predictive back, edge-to-edge, 16KB pages)
- [ ] Test on Android 15 emulator

#### APP-3: Release Signing Configuration

No signing config exists. Cannot produce a signed release APK for Play Store.

- [ ] Generate release keystore (`db20g-release.jks`)
- [ ] Create `signing.properties` template (already gitignored)
- [ ] Add `signingConfigs` block to `app/build.gradle.kts`
- [ ] Enable `isMinifyEnabled = true` for release builds
- [ ] Expand ProGuard rules (see APP-4)
- [ ] Produce signed release APK and verify it installs/runs

#### APP-4: ProGuard / R8 Rules *(Latent — Will Break on Minify)*

[proguard-rules.pro](app/proguard-rules.pro) has only 1 rule (USB serial keep).
Enabling minification will break these libraries:

- [ ] Add ML Kit Translate + Language ID keep rules
- [ ] Add Google Play Services Location keep rules
- [ ] Add AndroidX Car App library keep rules
- [ ] Add KotlinX Coroutines reflection keep rules
- [ ] Add AndroidX Lifecycle ViewModel keep rules
- [ ] Test release build with minification enabled

---

### 🟡 Medium — Quality & Reliability

#### QA-1: Test Coverage *(Zero Tests)*

There are **no unit tests and no instrumentation tests** anywhere in the project.
No `testImplementation` or `androidTestImplementation` dependencies exist in
`build.gradle.kts`.

- [ ] Add test dependencies: JUnit 5, Mockk/Mockito, Robolectric, Turbine (Flow testing)
- [ ] Unit tests — protocol layer:
  - [ ] `DB20GProtocol` — read/write block encoding, memory address calculation
  - [ ] `RadioChannel` — BCD frequency encode/decode, tone index lookup
  - [ ] `ChannelValidator` — boundary validation (frequency limits, power limits, tone matching)
  - [ ] `ChirpCsvManager` — CSV round-trip (import → export → re-import matches)
  - [ ] `AfskModem` — AFSK encode/decode round-trip (Bell 202 bit accuracy)
  - [ ] `SstvCodec` — VIS code encode/decode, frequency ↔ luminance
  - [ ] `GmrsConstants` — CTCSS/DCS lookup table correctness
- [ ] Unit tests — transport layer:
  - [ ] `BluetoothRadioTransport` — command framing, state machine
  - [ ] `UsbRadioTransport` — adapter delegation
- [ ] Unit tests — managers:
  - [ ] `ScanManager` — state machine transitions
  - [ ] `ChannelGroupManager` — group CRUD, persistence round-trip
  - [ ] `QsoLogger` — ADIF export format, search/filter
  - [ ] `FccComplianceManager` — power limit lookups, channel classification
- [ ] Instrumentation tests:
  - [ ] `SetupWizardActivity` — step navigation, callsign validation
  - [ ] `MainActivity` — device picker, transport connection flow
  - [ ] Widget rendering — both sizes
- [ ] Add `androidTestImplementation` dependencies for Espresso / Compose testing

#### QA-2: CI/CD Pipeline *(None)*

No GitHub Actions, no automated builds, no lint checks.

- [ ] Create `.github/workflows/android-build.yml` — build + lint on push/PR
- [ ] Create `.github/workflows/firmware-build.yml` — PlatformIO compile check
- [ ] Add lint configuration (`lint.xml` or Gradle lint options)
- [ ] Optional: automated APK artifact upload on tagged releases
- [ ] Optional: KiCad DRC check in CI (kibot / kicad-cli)

#### QA-3: `.gitignore` Missing Patterns

- [ ] Add `.pio/` (PlatformIO build cache)
- [ ] Add `.pioenvs/` (PlatformIO environments)
- [ ] Add `.piolibdeps/` (PlatformIO library dependencies)
- [ ] Add `*.stl` (generated OpenSCAD mesh files)
- [ ] Add `fp-info-cache` (KiCad regenerated cache)

---

### 🟢 Low — Polish & Store Readiness

#### STORE-1: Play Store Assets

- [ ] 512×512 hi-res launcher icon (PNG) for Play Store listing
- [ ] Feature graphic (1024×500) for Play Store
- [ ] Screenshots (phone + tablet, at least 2 each)
- [ ] Play Store listing text (short description, full description)
- [ ] Privacy policy URL (required for Bluetooth + Location permissions)
- [ ] Content rating questionnaire answers

#### STORE-2: `docs/` Directory Repopulation

The `docs/` directory is currently empty after the v9 cable build guide was archived.

- [ ] User guide / quick-start tutorial (web-friendly Markdown or HTML)
- [ ] Firmware flashing guide with photos (standalone from hardware/TROUBLESHOOTING.md)
- [ ] FAQ / common questions

#### POLISH-1: Miscellaneous Cleanup

- [ ] BOM.md: mounting holes show MH1+MH2 but enclosure uses 4× M3 — reconcile when PCB is laid out
- [ ] `versionCode` / `versionName` — define versioning strategy (semver, date-based, etc.)
- [ ] Consider `compileSdk 35` bump alongside targetSdk change

---

### 🔵 Deferred — Requires Physical Hardware

These items were identified during BT-2/BT-3/BT-5 development and explicitly
deferred because they require a built board and radio for testing.

- [ ] Auto-reconnect with exponential backoff *(BluetoothRadioTransport)*
- [ ] Full BT audio integration testing (RX + TX paths, latency, quality)
- [ ] First-launch BT pairing wizard *(standard Android pairing works for now)*
- [ ] Firmware OTA update screen in Android app UI
- [ ] Measure actual handset port pin 1 voltage and current under load
- [ ] Test ESP32 power consumption in all modes (idle, BT, BT+audio, WiFi OTA)
- [ ] Verify AMS1117 thermal margin at 8 V input (1.6 W dissipation — may need heatsink)
- [ ] End-to-end integration test: phone → BT → ESP32 → radio → receive on second radio
- [ ] Audio quality tuning: gain levels, noise floor, sidetone cancellation
- [ ] Range test: Bluetooth SPP reliable range with ESP32 PCB antenna in enclosure

---

### Implementation Priority Order

```
                                         ┌─────────────────────┐
  HW-1 PCB Layout ──────────────────────►│ HW-2 Enclosure      │
       (Critical — blocks everything)    │ (needs PCB dims)     │
                                         └─────────────────────┘
  APP-1 USB feature flag ──┐
  APP-2 targetSdk 35 ──────┤
  APP-3 Signing config ────┼────────────► Release APK
  APP-4 ProGuard rules ────┘

  QA-1 Test coverage ──────┐
  QA-2 CI/CD pipeline ─────┼────────────► Quality gates
  QA-3 .gitignore ──────────┘

  STORE-1 Assets ───────────┐
  STORE-2 Docs ─────────────┼────────────► Play Store submission
  POLISH-1 Cleanup ─────────┘
```

**Recommended sequence:**
1. **HW-1** (PCB layout) — longest lead-time item; order boards ASAP
2. **APP-1 + APP-2 + APP-3** (quick manifest/gradle fixes) — unblock release builds
3. **QA-1** (unit tests) — catch bugs before hardware arrives
4. **HW-2** (enclosure) — once PCB dimensions are final
5. **QA-2** (CI/CD) — automate quality checks
6. **STORE-1** (Play Store assets) — prepare while waiting for hardware
7. **Deferred items** — once physical hardware is in hand

---

## Community Research Sources

- Reddit r/gmrs — DB20-G setup threads, emergency planning (138↑), programming complaints, repeater database discussions
- Reddit r/amateurradio — Software pain points (160↑), feature wish lists, vehicle integration, data mode interest (77 comments)
- Reddit r/RTLSDR — Android SDR integration, vehicle head unit radio projects
- CHIRP Project Wiki — Confirmed DB20-G support, data source integrations (RepeaterBook, RadioReference)
- Real user quotes: "my only complaint is programming it," "I have to pick and choose which repeaters," "software has to be the biggest sore spot"

---

*Last updated: 2026-03-20*
