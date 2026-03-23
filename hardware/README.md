# DB20-G Interface Box — Hardware Design (v10 Bluetooth)

> **⚠ UNTESTED DESIGN** — This is a v10 redesign. The PCB has not been
> fabricated or tested yet. Build at your own risk.

Custom wireless interface box that connects an Android phone to a
Radioddity DB20-G GMRS mobile radio over Bluetooth, providing serial
programming control, audio I/O, and PTT keying — **no USB cable to the
phone required**.

## What This Does

```
┌──────────┐  Bluetooth   ┌──────────────────────────┐  RJ-45 + Data   ┌──────────┐
│  Android  │◄────────────►│   DB20-G Interface Box    │◄──────────────►│  DB20-G   │
│   Phone   │  SPP serial  │                           │ (handset port  │   Radio   │
│           │  SCO audio   │  ESP32-WROOM-32E          │  + data port)  │           │
│           │              │   → Bluetooth serial      │                │           │
│           │              │   → Bluetooth audio       │                │           │
│           │              │   → PTT / relay control   │                │           │
└──────────┘              └──────────────────────────┘                  └──────────┘
```

## Features

- **Bluetooth Classic** — phone connects wirelessly (SPP for serial, SCO for audio)
- **ESP32-WROOM-32E** — single module replaces the USB hub + UART + audio codec
- **UART bridge** — 9600 baud 8N1 for DB20-G programming protocol over BT SPP
- **DAC/ADC audio** — ESP32 DAC output → radio MIC, radio SPK → ESP32 ADC
- **Transistor PTT driver** — keys radio via GPIO, controlled from phone
- **DPDT relay** — switches between audio mode and serial/data mode
- **Audio attenuators** — voltage dividers match ESP32 levels to radio
- **RJ-45 handset pass-through** — use the hand mic normally while connected
- **LED indicators** — power/heartbeat, PTT active, audio activity, BT status
- **UART flash header** — J5 (3V3/TX/RX/GND) for ESP32 firmware updates
- **External 5 V power** — powered from USB adapter, vehicle lighter, or radio

## Directory Structure

```
hardware/
├── README.md                           ← You are here (v10 Bluetooth design)
├── BOM.md                              ← Bill of materials (~30 components, ~$5.28)
├── ASSEMBLY.md                         ← Step-by-step build guide (5 phases)
├── WIRING.md                           ← ESP32 GPIO map, connector pinouts, signal routing
├── TROUBLESHOOTING.md                  ← Common issues and solutions
├── KICAD-CHANGES.md                    ← v9→v10 schematic change log
├── kicad/
│   ├── DB20G-Interface-v10.kicad_sch   ← v10 Bluetooth schematic (generated)
│   ├── generate_v10.py                 ← Python schematic generator
│   ├── ERC-v10.json                    ← ERC results (0 errors)
│   └── archive/                        ← Archived v9 KiCad files
├── enclosure/
│   └── DB20G-Enclosure.scad            ← OpenSCAD enclosure
└── archive/                            ← Archived v9 documentation
firmware/
├── platformio.ini                      ← PlatformIO build config
├── include/
│   ├── pins.h                          ← ESP32 GPIO pin definitions
│   └── config.h                        ← Firmware constants
├── src/
│   └── main.cpp                        ← ESP32 firmware (BT SPP bridge)
└── README.md                           ← Firmware documentation
```

## Circuit Overview

### Block Diagram

```
                            ┌────────────────────────────────────────────────┐
                            │          DB20-G Interface Box (v10)            │
                            │                                                │
 [Phone] ◄── Bluetooth ──► │    ┌──────────────────────┐                    │
           SPP serial       │    │  ESP32-WROOM-32E     │                    │
           SCO audio        │    │                      │                    │
                            │    │  UART2 TX (GPIO17) ──┼──► Data Port TX   │
                            │    │  UART2 RX (GPIO16) ◄─┼─── Data Port RX   │
                            │    │                      │                    │
 5V DC ─────────────────────┼──► │  DAC (GPIO25) ───────┼──►[Divider]──► MIC│
                ┌───────┐   │    │  ADC (GPIO36) ◄──────┼──[Divider]◄── SPK │
                │AMS1117│3.3│    │                      │                    │
                │ 3.3V  │──┤    │  GPIO4 ──►[Q1]───────┼──► PTT (pin 4)    │
                └───────┘   │    │  GPIO5 ──►[Q2]───────┼──►[K1 Relay]      │
                            │    │                      │                    │
                            │    │  GPIO18 → LED1 (PWR) │                    │
                            │    │  GPIO19 → LED2 (PTT) │                    │
                            │    │  GPIO21 → LED3 (AUD) │                    │
                            │    │  GPIO22 → LED4 (BT)  │                    │
                            │    └──────────────────────┘                    │
                            │                                                │
                            │    RJ-45 IN ◄────────────► RJ-45 OUT          │
                            │    (Radio)    Pass-through   (Handset)         │
                            └────────────────────────────────────────────────┘
```

### ESP32 Pin Assignment

| GPIO  | Function        | Direction | Net Name      | Notes                       |
|-------|-----------------|-----------|---------------|-----------------------------|
| 25    | DAC Ch1         | Output    | ESP_DAC1      | TX audio to radio MIC       |
| 36    | ADC Ch0 (VP)    | Input     | ESP_ADC0      | RX audio from radio SPK     |
| 16    | UART2 RX        | Input     | ESP_RX2       | Serial data from radio      |
| 17    | UART2 TX        | Output    | ESP_TX2       | Serial data to radio        |
| 4     | Digital out     | Output    | ESP_PTT       | PTT driver (Q1 base via R1) |
| 5     | Digital out     | Output    | ESP_RELAY     | Relay driver (Q2 base via R2) |
| 18    | Digital out     | Output    | ESP_LED1      | Power / heartbeat LED       |
| 19    | Digital out     | Output    | ESP_LED2      | PTT active LED              |
| 21    | Digital out     | Output    | ESP_LED3      | Audio activity LED          |
| 22    | Digital out     | Output    | ESP_LED4      | Bluetooth status LED        |
| EN    | Enable          | Input     | ESP_EN        | Pull-up R11 + C7 RC delay   |
| IO0   | Boot select     | Input     | ESP_IO0       | Pull-up R12 (HIGH = run)    |
| 1     | UART0 TX        | Output    | ESP_TX0       | Debug / flash (J5 pin 2)    |
| 3     | UART0 RX        | Input     | ESP_RX0       | Debug / flash (J5 pin 3)    |

### Radio Connections

**Data Port (rear of DB20-G) — 3.5mm TRS plug:**
| Pin     | Signal       | Direction     |
|---------|-------------|---------------|
| Tip     | TX Data     | Board → Radio |
| Ring    | RX Data     | Radio → Board |
| Sleeve  | Ground      | Common        |

**Handset Port (front of DB20-G) — RJ-45:**
| RJ-45 Pin | Signal          | Interface Box Use          |
|-----------|-----------------|----------------------------|
| 1         | +V Supply       | Pass-through               |
| 2         | Microphone      | ESP32 DAC → Divider → MIC  |
| 3         | Ground          | Common ground              |
| 4         | PTT (active low)| Q1 transistor driver       |
| 5         | Speaker+        | Divider → ESP32 ADC        |
| 6         | Speaker-        | Pass-through               |
| 7         | UP Button       | Pass-through               |
| 8         | DOWN Button     | Pass-through               |

### Command Protocol (Phone ↔ ESP32)

Communication over BT SPP uses a simple framed protocol:

| Command       | ID   | Format                                | Direction      |
|---------------|------|---------------------------------------|----------------|
| Serial data   | 0x01 | `[0x01] [len_hi] [len_lo] [payload…]` | Phone → ESP32  |
| PTT control   | 0x02 | `[0x02] [0x01=press / 0x00=release]`  | Phone → ESP32  |
| Relay mode    | 0x03 | `[0x03] [0x01=serial / 0x00=audio]`   | Phone → ESP32  |

Data from radio → phone flows as raw bytes (no framing).

## Quick Start

1. Order components from [BOM-v10.md](BOM-v10.md)
2. Get PCBs fabricated — upload KiCad Gerber exports to JLCPCB or PCBWay
3. 3D-print the enclosure — export STL from `enclosure/DB20G-Enclosure.scad`
4. Follow [ASSEMBLY.md](ASSEMBLY.md) step-by-step (needs v10 update)
5. Flash ESP32 firmware — see [../firmware/README.md](../firmware/README.md)
6. Wire to radio per [WIRING.md](WIRING.md)
7. If issues, check [TROUBLESHOOTING.md](TROUBLESHOOTING.md)

## Design Decisions

- **ESP32-WROOM-32E** replaces FE1.1s + CP2102N + CM108AH + crystal.  One $2.50
  module provides BT Classic SPP (serial), DAC/ADC (audio), GPIO (PTT/relay),
  and WiFi for future OTA updates. Component count cut nearly in half.
- **Bluetooth Classic** over BLE because SPP gives a UART-like byte stream (no
  MTU fragmentation) and SCO provides a standard audio channel. Android has
  mature BT Classic support.
- **External 5 V power** instead of USB host power — the phone is no longer
  physically connected, so the board needs its own power source. A USB phone
  charger or vehicle lighter adapter works well.
- **DPDT relay (G5V-2)** replaces SPDT — switches both audio channels (MIC +
  SPK) between ESP32 and handset pass-through.
- **2N2222A transistor PTT** retained for zero-latency keying. The relay
  provides a backup.
- **RJ-45 pass-through** so the hand mic works normally when the relay is in
  handset mode.

## License

Hardware designs are provided under the MIT License. See the project root
[LICENSE](../LICENSE) file.
