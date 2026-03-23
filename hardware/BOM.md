# DB20-G Interface Box v10 — Bill of Materials (Bluetooth)

> **⚠ UNTESTED DESIGN** — This BOM is for the v10 ESP32 Bluetooth redesign.
> The PCB has not been fabricated or tested yet.

## Overview

The v10 board replaces the USB hub (FE1.1s) + USB-UART (CP2102N) + USB audio
codec (CM108AH) + 12 MHz crystal with a single ESP32-WROOM-32E module.  The
phone connects wirelessly over Bluetooth; the board is powered externally.

**Total component count: ~30** (down from ~55 in v9)

---

## MCU Module

| Ref | Part               | Package   | Spec                                       | Qty | Est. Cost | Source             |
|-----|--------------------|-----------|-------------------------------------------|-----|-----------|---------------------|
| U1  | ESP32-WROOM-32E    | Module    | WiFi + BT Classic + BLE, 4 MB flash, 38-pin | 1   | $2.50     | LCSC C701343 / DigiKey |

## Voltage Regulator

| Ref | Part          | Package  | Spec                          | Qty | Est. Cost | Source            |
|-----|---------------|---------|-------------------------------|-----|-----------|-------------------|
| U2  | AMS1117-3.3   | SOT-223 | 3.3 V LDO regulator, 1 A      | 1   | $0.10     | LCSC C6186         |

## Connectors

| Ref | Part              | Type          | Spec                               | Qty | Est. Cost | Source       |
|-----|-------------------|---------------|-----------------------------------|-----|-----------|--------------|
| J2  | RJ-45 Jack        | Through-hole  | 8P8C, shielded (to radio)          | 1   | $0.40     | LCSC C386756 |
| J3  | RJ-45 Jack        | Through-hole  | 8P8C, shielded (handset pass-through) | 1   | $0.40     | LCSC C386756 |
| J4  | Pin Header 1×2    | 2.54 mm       | External 5 V power input           | 1   | $0.05     | Any          |
| J5  | Pin Header 1×4    | 2.54 mm       | UART flash header (3V3/TX/RX/GND)  | 1   | $0.10     | Any          |

## Semiconductors

| Ref | Part    | Package | Spec                           | Qty | Est. Cost | Source        |
|-----|---------|---------|---------------------------------|-----|-----------|---------------|
| Q1  | 2N2222A | TO-92   | NPN transistor, PTT driver      | 1   | $0.05     | LCSC / DigiKey |
| Q2  | 2N2222A | TO-92   | NPN transistor, relay driver    | 1   | $0.05     | LCSC / DigiKey |
| D1  | 1N4148  | SOD-323 | Switching diode, PTT clamp      | 1   | $0.02     | LCSC C14516    |
| D2  | 1N5819  | SOD-323 | Schottky diode, relay flyback   | 1   | $0.03     | LCSC C2480     |

## Relay

| Ref | Part              | Package       | Spec                             | Qty | Est. Cost | Source       |
|-----|-------------------|---------------|----------------------------------|-----|-----------|--------------|
| K1  | G5V-2-DC5         | Through-hole  | 5 V DPDT relay, 2 A contacts     | 1   | $1.20     | LCSC C100025 |

## Resistors (0805 SMD)

| Ref      | Value | Purpose                                 | Qty | Est. Cost |
|----------|-------|-----------------------------------------|-----|-----------|
| R1       | 10 kΩ | PTT base resistor (GPIO4 → Q1)          | 1   | $0.01     |
| R2       | 10 kΩ | Relay driver base resistor (GPIO5 → Q2) | 1   | $0.01     |
| R3       | 10 kΩ | Audio RX divider upper (radio SPK in)   | 1   | $0.01     |
| R4       | 4.7 kΩ| Audio RX divider lower (→ ESP32 ADC)    | 1   | $0.01     |
| R5       | 10 kΩ | Audio TX divider upper (ESP32 DAC out)   | 1   | $0.01     |
| R6       | 4.7 kΩ| Audio TX divider lower (→ radio MIC)    | 1   | $0.01     |
| R7       | 220 Ω | LED1 current limit (Power, green)      | 1   | $0.01     |
| R8       | 220 Ω | LED2 current limit (PTT, red)          | 1   | $0.01     |
| R9       | 220 Ω | LED3 current limit (Audio, yellow)     | 1   | $0.01     |
| R10      | 220 Ω | LED4 current limit (BT status, blue)   | 1   | $0.01     |
| R11      | 10 kΩ | ESP32 EN pin pull-up                   | 1   | $0.01     |
| R12      | 10 kΩ | ESP32 GPIO0 pull-up (run mode)         | 1   | $0.01     |

## Capacitors (0805 SMD)

| Ref | Value  | Purpose                               | Qty | Est. Cost |
|-----|--------|---------------------------------------|-----|-----------|
| C1  | 100 nF | AMS1117 input bypass                  | 1   | $0.01     |
| C2  | 10 µF  | AMS1117 input bulk                    | 1   | $0.02     |
| C3  | 100 nF | AMS1117 output bypass                 | 1   | $0.01     |
| C4  | 10 µF  | AMS1117 output bulk                   | 1   | $0.02     |
| C5  | 100 nF | ESP32 3V3 bypass (close to pin 1)     | 1   | $0.01     |
| C6  | 22 µF  | ESP32 3V3 bulk decoupling             | 1   | $0.03     |
| C7  | 100 nF | EN pin RC delay (stable boot)         | 1   | $0.01     |
| C8  | 100 nF | ADC input filtering                   | 1   | $0.01     |
| C9  | 100 nF | Input power filtering                 | 1   | $0.01     |

## LEDs (0805 SMD)

| Ref  | Colour | Purpose                             | Qty | Est. Cost |
|------|--------|-------------------------------------|-----|-----------|
| LED1 | Green  | Power / heartbeat                   | 1   | $0.02     |
| LED2 | Red    | PTT active                          | 1   | $0.02     |
| LED3 | Yellow | Audio activity                      | 1   | $0.02     |
| LED4 | Blue   | Bluetooth connected                 | 1   | $0.02     |

## Other Components

| Ref | Part          | Package      | Spec                          | Qty | Est. Cost | Source      |
|-----|---------------|-------------|-------------------------------|-----|-----------|-------------|
| F1  | Polyfuse      | 1812        | 500 mA PTC resettable fuse    | 1   | $0.05     | LCSC C70069 |
| MH1 | Mounting hole | M3 pad      | 3.2 mm, padded                | 1   | —         | N/A         |
| MH2 | Mounting hole | M3 pad      | 3.2 mm, padded                | 1   | —         | N/A         |

## Not On PCB — External Items

| Item                        | Spec                                        | Est. Cost |
|-----------------------------|---------------------------------------------|-----------|
| 5 V power supply            | USB adapter, vehicle lighter, or radio port  | $2–$10    |
| RJ-45 patch cable           | Cat5e, 0.5 m, handset port connection        | $1.00     |
| USB-UART adapter (flashing) | CP2102 or CH340, 3.3 V logic                | $2.00     |

---

## Cost Summary

| Category             | Items | Estimated Total |
|----------------------|-------|-----------------|
| MCU module (ESP32)   | 1     | $2.50           |
| Voltage regulator    | 1     | $0.10           |
| Connectors           | 4     | $0.95           |
| Semiconductors       | 4     | $0.15           |
| Relay                | 1     | $1.20           |
| Resistors            | 12    | $0.12           |
| Capacitors           | 9     | $0.13           |
| LEDs                 | 4     | $0.08           |
| Fuse + mounting      | 3     | $0.05           |
| **PCB Total**        | **39**| **~$5.28**      |

*Prices approximate as of early 2025, sourced from LCSC/DigiKey.*
