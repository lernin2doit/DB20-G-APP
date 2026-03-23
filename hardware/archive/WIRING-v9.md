# DB20-G Interface Box — Wiring & Pin Mapping Reference

Complete wiring guide for connecting the interface box to the Radioddity DB20-G GMRS radio.

> **⚠️ RJ-45 Pinout Note:** The pinout below is the *assumed standard* for the DB20-G
> based on Kenwood-mobile-compatible radios. **It has not been verified on actual hardware.**
> The pin mapping is configurable in the app (Settings → Hardware Pinout) so you can
> adjust it to match your specific radio if the default doesn't work.
> See the [Verification Procedure](#verification-procedure) and the
> [cable build guide](../docs/cable-build-guide.md) for testing instructions.

## System Wiring Diagram

```
  ┌─────────────────────────────────────────────────────────────────────────────┐
  │                          DB20-G Interface Box                               │
  │                                                                             │
  │   ┌───────────┐        ┌───────────┐        ┌───────────────┐              │
  │   │           │  USB   │           │  TXD   │               │              │
  │   │  FE1.1s   │ Port1  │  CP2102N  ├───────►│  K1 Relay     │              │
  │   │  USB Hub  │───────►│  USB-UART │  RXD   │  (DPDT)       │              │
  │   │    (U1)   │        │   (U2)    │◄───────┤               │              │
  │   │           │        │           │        │  COM1→J2 MIC  ├──┐           │
  │   │           │        │     RTS ──┤──┐     │  COM2←J2 SPK  │  │           │
  │   │           │        └───────────┘  │     │               │  │           │
  │   │           │                       │     │  NC1←CM108 OUT│  │           │
  │   │           │                       │     │  NC2→CM108 IN │  │           │
  │   │           │                       │     │               │  │           │
  │   │           │        ┌───────────┐  │     │  NO1←CP2102TX │  │           │
  │   │           │  USB   │           │  │     │  NO2→CP2102RX │  │           │
  │   │           │ Port2  │  CM108AH  │  │     └───────┬───────┘  │           │
  │   │           │───────►│  USB Audio│  │             │          │           │
  │   │           │        │   (U3)    │  │     ┌───────┴───────┐  │           │
  │   └───────────┘        └───────────┘  │     │  PTT Driver   │  │  ┌──────────┐
  │        ▲                              └────►│  Q1 2N2222A   ├──┼─►│  RJ-45   │
  │        │                                    └───────────────┘  │  │  Radio   │──── To Radio
  │   ┌────┴─────┐         ┌───────────┐                           │  │  (J2)    │  Handset Port
  │   │  USB-C   │         │  AMS1117  │ 3.3V                     │  │          │
  │   │   (J1)   │◄─Phone  │   (U4)    ├──► CP2102N VCC           │  └────┬─────┘
  │   │          │  5V ──►│           │                            │       │
  │   └──────────┘         └───────────┘                           │  ┌────┴─────┐
  │                                                                │  │  RJ-45   │
  │   ┌──────────┐         ┌───────────┐                           │  │Pass-thru │──── To Handset
  │   │ LEDs ×4  │         │  Relay    │◄── Q2 driver ─── DTR     │  │  (J3)    │
  │   │ PWR/PTT/ │         │  G5V-1    │                           │  └──────────┘
  │   │ AUD/SER  │         │   (K1)    │                           │
  │   └──────────┘         └───────────┘                           │
  └─────────────────────────────────────────────────────────────────┘
```

### Serial / Audio Mode Switching

The K1 relay (DPDT) switches the J2 RJ-45 MIC and SPK pins between two sources:

| K1 State        | DTR Line | MIC pin (J2) source          | SPK pin (J2) destination      | Mode            |
|-----------------|----------|------------------------------|-------------------------------|-----------------|
| **De-energized** (NC) | LOW  | CM108AH SPK_OUT (audio TX)   | CM108AH MIC_IN (audio RX)    | Normal audio    |
| **Energized** (NO)    | HIGH | CP2102N TXD (serial TX)      | CP2102N RXD (serial RX)      | Serial/programming |

**How it works:**
1. In normal operation, the relay is de-energized. Audio flows between CM108AH and the radio through the MIC/SPK pins.
2. When the app needs to program the radio, it sets the DTR line HIGH, energizing K1.
3. K1 switches the MIC pin from CM108 audio output to CP2102N serial TXD, and the SPK pin from CM108 audio input to CP2102N serial RXD.
4. Serial data flows at 9600 baud over the same physical pins.
5. After programming completes, DTR goes LOW, K1 de-energizes, and audio mode resumes.

> **Note:** The app automatically handles mode switching — users don't need to toggle anything manually.

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

Connects to the DB20-G's front handset RJ-45 port. This is also the programming port — serial data shares the MIC/SPK pins via relay switching.

> **⚠️ This pinout is configurable in the app.** The table below shows the assumed
> default. If your radio uses a different pin arrangement, update it in the app
> under Settings → Hardware Pinout. See the [Verification Procedure](#verification-procedure).

| RJ-45 Pin | Wire Color    | Signal           | Interface Box Connection                    |
|-----------|---------------|------------------|---------------------------------------------|
| 1         | Orange/White  | +V Supply (8V)   | Pass-through to J3 pin 1                    |
| 2         | Orange        | Microphone (Hot)  | K1 relay COM1 (audio or serial TX via relay) |
| 3         | Green/White   | Ground            | Common GND plane                            |
| 4         | Blue          | PTT (Active Low)  | Q1 collector (PTT transistor driver)        |
| 5         | Blue/White    | Speaker+ (Hot)    | K1 relay COM2 (audio or serial RX via relay) |
| 6         | Green         | Speaker- (Return) | Pass-through to J3 pin 6                    |
| 7         | Brown/White   | UP Button         | Pass-through to J3 pin 7                    |
| 8         | Brown         | DOWN Button       | Pass-through to J3 pin 8                    |

### J3 — RJ-45 Handset Pass-through

Directly connected to J2 with taps for audio/serial relay switching and PTT. The hand microphone works normally when the relay is in audio mode.

| J3 Pin | Connected To | Tap                                              |
|--------|-------------|--------------------------------------------------|
| 1      | J2 pin 1    | None (direct pass-through)                       |
| 2      | J2 pin 2    | Tapped: K1 relay COM1 (audio TX or serial TX)    |
| 3      | J2 pin 3    | None (common ground)                             |
| 4      | J2 pin 4    | Tapped: PTT transistor in parallel               |
| 5      | J2 pin 5    | Tapped: K1 relay COM2 (audio RX or serial RX)    |
| 6      | J2 pin 6    | None (direct pass-through)                       |
| 7      | J2 pin 7    | None (direct pass-through)                       |
| 8      | J2 pin 8    | None (direct pass-through)                       |

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
```

**How PTT works:**
1. **App presses PTT** → CP2102N RTS line goes HIGH
2. RTS HIGH → current flows through R1 (10kΩ) → Q1 base
3. Q1 turns ON → collector pulls J2 pin 4 (PTT) to GND
4. Radio sees PTT grounded → enters transmit mode
5. **App releases PTT** → RTS goes LOW → Q1 turns OFF → PTT line floats high

**Configure in app:** Live tab → PTT Config → select RTS (default)

## Relay Switching Circuit Detail

```
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


K1 Relay Contacts (DPDT):

  Audio Mode (NC — relay de-energized, DTR LOW):
    COM1 ─── NC1 ─── CM108AH SPK_OUT → [R5/R6 divider] → J2 Pin 2 (MIC)
    COM2 ─── NC2 ─── J2 Pin 5 (SPK) → [R3/R4 divider] → CM108AH MIC_IN

  Serial Mode (NO — relay energized, DTR HIGH):
    COM1 ─── NO1 ─── CP2102N TXD → J2 Pin 2 (MIC/TX Data)
    COM2 ─── NO2 ─── J2 Pin 5 (SPK/RX Data) → CP2102N RXD
```

**Mode switching:**
1. **Normal audio mode** (default) — DTR LOW, relay de-energized, NC contacts closed
   - CM108AH SPK_OUT (attenuated) feeds radio MIC pin for voice TX
   - Radio SPK pin (attenuated) feeds CM108AH MIC_IN for voice RX
2. **Serial/programming mode** — DTR HIGH, relay energized, NO contacts closed
   - CP2102N TXD feeds radio MIC pin as serial data
   - Radio SPK pin carries serial RX data to CP2102N RXD
3. App controls DTR automatically during download/upload operations

## Audio Path Detail

### Receive (Radio Speaker → Phone)

```
DB20-G Speaker+ ──── J2 Pin 5 ──── K1 COM2 ──NC── C9 (1µF, DC block)
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
Phone Audio → USB → CM108AH SPK_OUT ──── K1 NC1 ──── C10 (1µF, DC block)
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

### Handset Cable (RJ-45)
- Standard Cat5e patch cable, 0.5m-1 meter
- Straight-through wiring (not crossover)
- Connects interface box J2 to radio front handset port

## Configurable Pinout

The RJ-45 pinout varies between radio models and even between production runs of the same model. The DB20-G Controller app allows you to configure which RJ-45 pin carries which signal.

### Default Pinout (Assumed DB20-G Standard)

| Pin | Signal    |
|-----|-----------|
| 1   | +V Supply |
| 2   | MIC       |
| 3   | GND       |
| 4   | PTT       |
| 5   | SPK+      |
| 6   | SPK−      |
| 7   | UP        |
| 8   | DOWN      |

### Alternative Pinout (Kenwood-Mobile)

Some Kenwood-compatible radios use a different arrangement:

| Pin | Signal    |
|-----|-----------|
| 1   | MIC       |
| 2   | +V Supply |
| 3   | UP        |
| 4   | DOWN      |
| 5   | PTT       |
| 6   | SPK       |
| 7   | SQL       |
| 8   | GND       |

### Configuring in the App

1. Open the app → Settings → Hardware Pinout
2. Select a preset (DB20-G Default or Kenwood-Mobile) or create a custom mapping
3. Assign each signal (MIC, SPK, PTT, VCC, GND, UP, DOWN) to a pin number
4. The app stores this configuration and uses it for documentation display and hardware guide references
5. If you change the pinout, **you must also rewire the physical RJ-45 connections** to match

> **Note:** The pinout configuration in the app changes which pins the app *expects* signals on.
> The actual hardware wiring on the PCB must match. If you use a non-default pinout, you'll need
> to modify the PCB traces or use fly-wires to route the correct signals.

## Verification Procedure

Before committing to a pinout, verify your radio's actual pin assignments:

1. **Identify Ground:** Probe each RJ-45 pin against the radio chassis with a multimeter in continuity mode
2. **Identify Power:** Measure DC voltage on each pin — the +V pin reads 5-8V DC
3. **Identify PTT:** Short each remaining pin to GND one at a time — the PTT pin will key the radio
4. **Identify Speaker:** With the radio receiving audio, measure AC voltage — the SPK pin shows ~0.5-2V AC
5. **Identify MIC:** Send a test tone from CM108 through each remaining pin and listen on another radio
6. **UP/DOWN:** Press the hand mic buttons and observe which pins change state

Record your results and enter them in the app's Hardware Pinout configuration.

## Grounding Notes

- All ground connections (USB GND, audio GND, PTT GND, RJ-45 GND) must be connected to the common ground plane on the PCB
- Ground loops between the serial and audio paths can cause hum — the coupling capacitors (C9, C10) help isolate DC paths
- If you hear a 50/60Hz hum, add a ferrite bead on the RJ-45 cable near the interface box
