# DB20-G Interface Box — Wiring & Pin Mapping Reference

Complete wiring guide for connecting the interface box to the Radioddity DB20-G GMRS radio.

## System Wiring Diagram

```
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │                          DB20-G Interface Box                               │
  │                                                                             │
  │   ┌───────────┐        ┌───────────┐        ┌───────────┐                  │
  │   │           │  USB   │           │  USB   │           │                  │
  │   │  FE1.1s   │ Port1  │  CP2102N  │  UART  │   3.5mm   │◄── To Radio     │
  │   │  USB Hub  │───────►│  USB-UART ├───────►│  TRS Jack │    Data Port    │
  │   │    (U1)   │        │   (U2)    │  TX/RX │   (J4)    │                  │
  │   │           │        │           │        │           │                  │
  │   │           │        │     RTS ──┤──┐     └───────────┘                  │
  │   │           │        └───────────┘  │                                    │
  │   │           │                       │  ┌────────────┐                    │
  │   │           │                       └─►│ PTT Driver │                    │
  │   │           │                          │ Q1 2N2222A ├──┐                 │
  │   │           │                          └────────────┘  │                 │
  │   │           │        ┌───────────┐                     │                 │
  │   │           │  USB   │           │  SPK_OUT            │                 │
  │   │           │ Port2  │  CM108AH  ├──[R5]──[R6]──►MIC  │                 │
  │   │           │───────►│  USB Audio│                     │  ┌───────────┐  │
  │   │           │        │   (U3)    │  MIC_IN             ├─►│  RJ-45    │  │
  │   └───────────┘        │           │◄─[R3]──[R4]──SPK   │  │  Radio    │──┼── To Radio
  │        ▲               └───────────┘                     │  │  (J2)     │  │   Handset Port
  │        │                                                 │  │           │  │
  │   ┌────┴─────┐         ┌───────────┐                     │  └─────┬─────┘  │
  │   │  USB-C   │         │  AMS1117  │ 3.3V                │        │        │
  │   │   (J1)   │◄─Phone  │   (U4)    ├──► CP2102N VCC      │  ┌─────┴─────┐  │
  │   │          │  5V ──►│           │                      │  │  RJ-45    │  │
  │   └──────────┘         └───────────┘                      │  │ Pass-thru │──┼── To Handset
  │                                                           │  │  (J3)     │  │
  │   ┌──────────┐         ┌───────────┐                      │  └───────────┘  │
  │   │ LEDs ×4  │         │   Relay   │◄─── Q2 driver ──────┘                 │
  │   │ PWR/PTT/ │         │  G5V-1    ├──► PTT (backup path)                  │
  │   │ AUD/SER  │         │   (K1)    │                                       │
  │   └──────────┘         └───────────┘                                       │
  └─────────────────────────────────────────────────────────────────────────────┘
```

## Connector Pinouts

### J1 — USB-C Receptacle (Phone Connection)

Standard USB 2.0 over USB-C. Only D+/D-/VBUS/GND are used.

| Pin   | Signal | Direction     | Note                           |
|-------|--------|---------------|--------------------------------|
| A1,B1 | GND    | —             | Ground                         |
| A4,B4 | VBUS   | Phone → Box   | 5V power from phone            |
| A6    | D+     | Bidirectional | USB data positive              |
| A7    | D-     | Bidirectional | USB data negative              |
| B6    | D+     | Bidirectional | Tied to A6                     |
| B7    | D-     | Bidirectional | Tied to A7                     |
| A5,B5 | CC     | —             | 5.1kΩ pull-down for UFP detect |

### J2 — RJ-45 Radio Side (Handset Port Connection)

Connects to the DB20-G's front handset RJ-45 port. Standard T-568B wiring.

| RJ-45 Pin | Wire Color    | Signal           | Interface Box Connection          |
|-----------|---------------|------------------|-----------------------------------|
| 1         | Orange/White  | +V Supply (8V)   | Pass-through to J3 pin 1          |
| 2         | Orange        | Microphone (Hot)  | CM108 SPK_OUT via R5/R6 divider   |
| 3         | Green/White   | Ground            | Common GND plane                  |
| 4         | Blue          | PTT (Active Low)  | Q1 collector / K1 relay NO        |
| 5         | Blue/White    | Speaker+ (Hot)    | To R3/R4 divider → CM108 MIC_IN   |
| 6         | Green         | Speaker- (Return) | Pass-through to J3 pin 6          |
| 7         | Brown/White   | UP Button         | Pass-through to J3 pin 7          |
| 8         | Brown         | DOWN Button       | Pass-through to J3 pin 8          |

### J3 — RJ-45 Handset Pass-through

Directly connected to J2 with taps for audio and PTT. The hand microphone works normally.

| J3 Pin | Connected To | Tap                                    |
|--------|-------------|----------------------------------------|
| 1      | J2 pin 1    | None (direct pass-through)             |
| 2      | J2 pin 2    | Tapped: CM108 audio injected in parallel |
| 3      | J2 pin 3    | None (common ground)                   |
| 4      | J2 pin 4    | Tapped: PTT transistor in parallel     |
| 5      | J2 pin 5    | Tapped: Audio tapped to CM108 MIC_IN   |
| 6      | J2 pin 6    | None (direct pass-through)             |
| 7      | J2 pin 7    | None (direct pass-through)             |
| 8      | J2 pin 8    | None (direct pass-through)             |

### J4 — 3.5mm TRS (Data/Programming Port)

Connects to the rear data port on the DB20-G. This is a standard Kenwood-style programming cable pinout.

| TRS    | Signal       | Interface Box Connection | Direction     |
|--------|-------------|-------------------------|---------------|
| Tip    | TX Data      | CP2102N TXD (pin 25)    | Box → Radio   |
| Ring   | RX Data      | CP2102N RXD (pin 26)    | Radio → Box   |
| Sleeve | Ground       | Common GND              | —             |

**Note:** The DB20-G data port uses 3.3V TTL levels. The CP2102N outputs 3.3V by default when powered from the 3.3V rail — no level shifting needed.

### J5 — Debug/Expansion Header (2×5 pins)

| Pin | Signal    | Pin | Signal      |
|-----|-----------|-----|-------------|
| 1   | 3.3V      | 2   | 5V          |
| 3   | UART TX   | 4   | UART RX     |
| 5   | RTS       | 6   | DTR         |
| 7   | CTS       | 8   | GPIO (CM108)|
| 9   | GND       | 10  | GND         |

## PTT Circuit Detail

```
                  R1 (10kΩ)
CP2102N RTS ──────┤├────────► Q1 Base
                                │
                           ┌────┤ 2N2222A (NPN)
                           │    │
               J2 Pin 4 ◄─┤    │
               (PTT line)  │    ▼ Emitter
                           │    │
                           │   GND
                      Collector
                           │
                      ┌────┘
                      │
                      ▼ Also connects to relay path:

                  R2 (10kΩ)
CP2102N DTR ──────┤├────────► Q2 Base
                                │
                           ┌────┤ 2N2222A (NPN)
                           │    │
              K1 Coil (-) ◄┤    │
                           │    ▼ Emitter
                      ┌────┘    │
                      │        GND
                  K1 Coil (+) ──── 5V
                      │
                 D1 ──┤── (1N4148 flyback, cathode to 5V)
                      │
                  K1 NO ──────── J2 Pin 4 (PTT)
                  K1 COM ─────── GND
```

**How PTT works:**
1. **App presses PTT** → CP2102N RTS line goes HIGH
2. RTS HIGH → current flows through R1 (10kΩ) → Q1 base
3. Q1 turns ON → collector pulls J2 pin 4 (PTT) to GND
4. Radio sees PTT grounded → enters transmit mode
5. **App releases PTT** → RTS goes LOW → Q1 turns OFF → PTT line floats high

**Relay backup path:**
1. **App sets DTR HIGH** → current through R2 → Q2 base
2. Q2 turns ON → relay K1 energizes
3. K1 NO contact closes → PTT line pulled to GND
4. Use relay path if transistor PTT doesn't work (some radios need more current)

**Configure in app:** Live tab → PTT Config → select RTS (transistor) or DTR (relay)

## Audio Path Detail

### Receive (Radio Speaker → Phone)

```
DB20-G Speaker+ ──── J2 Pin 5 ──── C9 (1µF, DC block)
                                          │
                                     R3 (10kΩ)
                                          │
                                     ┌────┤
                                     │    │
                              CM108  │  R4 (1kΩ)
                              MIC_IN │    │
                                     │   GND
                                     │
                                     └──► CM108AH → USB Audio → Phone
```

**Attenuation:** 10:1 voltage divider (10kΩ / 1kΩ) reduces ~1V p-p speaker output to ~90mV for CM108 MIC_IN. Fine-tune by changing R3:
- Too loud: increase R3 to 22kΩ or 47kΩ
- Too quiet: decrease R3 to 4.7kΩ

### Transmit (Phone → Radio Microphone)

```
Phone Audio → USB → CM108AH SPK_OUT ──── C10 (1µF, DC block)
                                                │
                                           R5 (10kΩ)
                                                │
                                           ┌────┤
                                           │    │
                                   J2 Pin 2│  R6 (1kΩ)
                                   (MIC)   │    │
                                           │   GND
                                           │
                               Radio Mic ◄─┘
```

**Attenuation:** 10:1 voltage divider reduces ~1V p-p CM108 output to ~90mV for radio microphone input.

## LED Wiring

| LED  | Color  | Signal Source           | Behavior                      |
|------|--------|------------------------|-------------------------------|
| LED1 | Green  | 5V rail via R7 (330Ω)  | Solid ON when powered         |
| LED2 | Red    | Q1 collector via R8    | ON during PTT (transmitting)  |
| LED3 | Yellow | CM108 GPIO4 via R9     | Blinks with audio activity    |
| LED4 | Blue   | CP2102 TX LED via R10  | Blinks during serial TX/RX    |

## Cable Recommendations

### Phone Cable (USB-C)
- Standard USB 2.0 USB-C to USB-C cable, 1 meter
- Ensures data + power delivery
- Avoid charge-only cables (no data pins)

### Data Cable (3.5mm TRS)
- Use the programming cable that came with the DB20-G
- Or build one: 3.5mm TRS plug → 3 wires (tip=TX, ring=RX, sleeve=GND)
- Cable length: keep under 2 meters to maintain signal integrity at 9600 baud

### Handset Cable (RJ-45)
- Standard Cat5e patch cable, 0.5-1 meter
- Straight-through wiring (not crossover)
- Connects interface box J2 to radio front handset port

## Grounding Notes

- All ground connections (USB GND, audio GND, PTT GND, RJ-45 pin 3) must be connected to the common ground plane on the PCB
- The 3.5mm TRS sleeve provides the serial ground reference
- Ground loops between the serial and audio paths can cause hum — the coupling capacitors (C9, C10) help isolate DC paths
- If you hear a 50/60Hz hum, add a ferrite bead on the RJ-45 cable near the interface box
