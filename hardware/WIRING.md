# DB20-G Interface Board — Wiring & Signal Guide (v10, ESP32 Bluetooth)

> **Board revision:** v10 (ESP32-WROOM-32E Bluetooth)
>
> ⚠ The v9 USB wiring guide is archived in `archive/WIRING-v9.md`.

---

## Block Diagram

```
┌──────────────┐          ┌──────────────────────────────┐          ┌───────────┐
│ Android Phone│──── BT ──│  ESP32-WROOM-32E (U1)        │          │ DB20-G    │
│  (app)       │   SPP    │                              │          │ Radio     │
│              │   +SCO   │  UART2 (GPIO16/17)  ──K1 NO──│──── J2 ──│ (serial)  │
│              │          │  DAC (GPIO25)       ──K1 NC──│──── J2 ──│ (audio)   │
│              │          │  ADC (GPIO36)       ──K1 NC──│──── J2 ──│ (audio)   │
│              │          │  GPIO4 → Q1 ────────PTT──────│──── J2 ──│ PTT       │
│              │          │  GPIO5 → Q2 ────────K1 coil──│          │           │
└──────────────┘          │  GPIO18-22 → LEDs            │          └───────────┘
                          └──────────────────────────────┘
                                    │
                          ┌─────────┴─────────┐
                          │  AMS1117-3.3 (U2) │
                          │  5V → 3.3V LDO    │
                          └─────────┬─────────┘
                                    │
                            J4 (5V input) or
                            J2 pin 2 (radio power)
```

---

## ESP32 GPIO Pin Map

| GPIO | Function | Direction | Connect To | Notes |
|------|----------|-----------|------------|-------|
| 1 (TX0) | Debug UART TX | Out | J5 pin 2 | Firmware flash / serial monitor |
| 3 (RX0) | Debug UART RX | In | J5 pin 3 | Firmware flash / serial monitor |
| 16 | Radio UART RX | In | K1 P2_NO | Radio SPK → relay → ESP32 (serial mode) |
| 17 | Radio UART TX | Out | K1 P1_NO | ESP32 → relay → radio MIC (serial mode) |
| 25 | DAC1 — TX audio | Out | K1 P1_NC via R3/R4 | ESP32 → divider → relay → radio MIC (audio mode) |
| 36 (VP) | ADC1_CH0 — RX audio | In | K1 P2_NC via R5/R6 | Radio SPK → relay → divider → ESP32 (audio mode) |
| 4 | PTT driver | Out | R1 → Q1 base | GPIO HIGH = PTT keyed |
| 5 | Relay driver | Out | R2 → Q2 base | GPIO HIGH = serial mode; LOW = audio mode |
| 18 | LED1 (Power) | Out | R7 (220 Ω) → LED | Green — heartbeat blink |
| 19 | LED2 (PTT) | Out | R8 (220 Ω) → LED | Red — solid when keyed |
| 21 | LED3 (Audio) | Out | R9 (220 Ω) → LED | Yellow — blinks on RX audio |
| 22 | LED4 (BT) | Out | R10 (220 Ω) → LED | Blue — solid=connected, blink=advertising |
| 0 | Boot mode select | In | R12 (10 k pull-up) | Hold LOW during boot for flash mode |
| EN | Chip enable | In | R11 (10 k pull-up) + C7 (100 nF) | RC delay for stable boot |

---

## Connector Pinouts

### J2 — Radio RJ-45 (to DB20-G radio accessory port)

| Pin | Signal | Direction | Description |
|-----|--------|-----------|-------------|
| 1 | GND | — | Ground |
| 2 | +V Supply | In | +5–8 V from radio (handset backlight power) |
| 3 | SPK+ | Out (from radio) | Radio speaker / RX audio output |
| 4 | SPK− | — | Speaker return (often GND) |
| 5 | MIC | In (to radio) | Microphone / TX audio input |
| 6 | PTT | In (to radio) | Pull low to transmit |
| 7 | Serial Data | Bidirectional | 9600 baud programming data |
| 8 | GND | — | Ground |

> Pin numbering follows the DB20-G service manual / CHIRP convention.
> Cross-reference with `docs/cable-build-guide.md` for cable construction.

### J3 — Handset RJ-45 (pass-through for original handset)

Same pinout as J2. The DPDT relay K1 switches the MIC and SPK lines between
the ESP32 (for programming/audio) and the handset connector (for normal
handset operation).

### J4 — Power Input (2-pin header)

| Pin | Signal | Notes |
|-----|--------|-------|
| 1 | +5 V | Input to F1 polyfuse → U2 AMS1117-3.3 |
| 2 | GND | Board ground |

### J5 — UART Flash Header (1×4 pin header)

| Pin | Signal | Connect To |
|-----|--------|------------|
| 1 | 3.3 V | USB-UART adapter 3.3 V (optional — board is self-powered) |
| 2 | TX (ESP32 TX0) | USB-UART adapter RX |
| 3 | RX (ESP32 RX0) | USB-UART adapter TX |
| 4 | GND | USB-UART adapter GND |

---

## Signal Routing

### Serial Mode (Relay Energized — K1 HIGH)

```
Phone ──BT SPP──► ESP32 UART2 TX (GPIO17) → K1 P1_NO ──► Radio MIC (serial data)
Radio Serial Data → K1 P2_NO → ESP32 UART2 RX (GPIO16) ──BT SPP──► Phone
```

Used for programming the radio (reading/writing channels and settings).

### Audio Mode (Relay Released — K1 LOW, default)

```
Phone ──BT SCO──► ESP32 DAC (GPIO25) → R3/R4 divider → C4 → K1 P1_NC ──► Radio MIC
Radio SPK → K1 P2_NC → C5 → R5/R6 divider → ESP32 ADC (GPIO36) ──BT──► Phone
```

Used for voice transmission and reception. The relay is in its default
(de-energized) position, so audio passes through even if the ESP32 is off.

### PTT Path

```
ESP32 GPIO4 → R1 (10k) → Q1 base
                         Q1 collector → PTT line (J2 pin 6)
                         Q1 emitter → GND

GPIO4 HIGH → Q1 saturates → PTT pulled LOW → radio transmits
GPIO4 LOW  → Q1 off → PTT floats HIGH → radio receives
```

D1 (1N4148) clamps the PTT line to protect against voltage spikes.

### Relay Driver

```
ESP32 GPIO5 → R2 (10k) → Q2 base
                         Q2 collector → K1 coil (−)
                         Q2 emitter → GND
                         K1 coil (+) → 5V rail

GPIO5 HIGH → Q2 saturates → K1 energized → serial mode (NO contacts close)
GPIO5 LOW  → Q2 off → K1 released → audio mode (NC contacts close)
```

D2 (1N5819 Schottky) across K1 coil absorbs the inductive flyback spike.

---

## Audio Level Matching

### TX Path (ESP32 DAC → Radio MIC)

ESP32 DAC output: 0–3.3 V (8-bit). Radio mic input expects ~100 mVpp.

```
GPIO25 ──► R3 (33k) ──┬── R4 (3.3k) → GND
                       └── C4 (100nF) ──► K1 P1_NC → Radio MIC
```

Divider ratio: 3.3k / (33k + 3.3k) = 0.091 → ~300 mVpp max.
C4 removes DC offset. Adjust R3/R4 for desired TX audio level.

### RX Path (Radio SPK → ESP32 ADC)

Radio speaker output: ~1–3 Vpp. ESP32 ADC input: 0–3.3 V, 12-bit.

```
Radio SPK → K1 P2_NC → C5 (100nF) → R5 (10k) ──┬── R6 (10k) → GND
                                                  └── → GPIO36 (ADC)
```

Divider: 10k / (10k + 10k) = 0.5 → max ~1.65 Vpp at ADC.
C5 AC-couples to remove DC. ADC pin biased to ~1.65 V by midpoint divider.

---

## LED Wiring

All LEDs are 0805 SMD with 220 Ω series resistors for ~10 mA at 3.3 V.

| LED | GPIO | Color | Function |
|-----|------|-------|----------|
| LED1 | 18 | Green | Power heartbeat (50 ms flash every 3 s) |
| LED2 | 19 | Red | PTT active (solid while keyed) |
| LED3 | 21 | Yellow | RX audio activity (blinks on received audio) |
| LED4 | 22 | Blue | BT status (solid = connected, fast blink = advertising) |

---

## Power Design

### Primary: External 5 V via J4

```
J4 pin 1 (+5V) → F1 (500mA polyfuse) → U2 AMS1117-3.3 Vin
                                         U2 Vout → 3.3V rail → ESP32
J4 pin 2 (GND) → Board GND
```

### Alternative: Radio Handset Port Power (BT-5)

The DB20-G provides +5–8 V on J2 RJ-45 pin 2 for handset backlight power.
This voltage can feed the board directly:

```
J2 pin 2 (+5-8V) → F1 polyfuse → U2 AMS1117-3.3 → 3.3V
```

AMS1117-3.3 accepts up to 18 V input, so 5–8 V is well within range.
The polyfuse protects against overcurrent. No external power supply needed.

**Current budget:**
- ESP32 idle (BT connected): ~80 mA @ 3.3 V
- ESP32 BT + audio streaming: ~150 mA @ 3.3 V
- ESP32 peak (WiFi OTA): ~240 mA @ 3.3 V
- Relay K1 coil: ~50 mA @ 5 V
- LEDs (all on): ~40 mA @ 3.3 V
- **Total max:** ~350 mA @ 5 V input

---

## Grounding

- Single-point star ground near U2 LDO output.
- ESP32 GND pad must be solidly connected to the ground pour.
- Audio ground traces should be routed away from digital switching noise.
- K1 relay return current path should not cross audio signal traces.
