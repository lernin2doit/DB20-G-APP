# DB20-G Interface Box — Build Guide

## Overview

This document describes how to build a custom **USB Interface Box** that connects
your Android phone to a Radioddity DB20-G GMRS mobile radio. The interface box
replaces both the programming cable and the hand microphone, allowing the phone
app to fully control the radio: programming, live audio, PTT, and monitoring.

The box also provides a **pass-through RJ-45 port** so the original hand
microphone can be plugged in alongside the phone and either device can operate
the radio.

### Design Goals

| Goal | How |
|------|-----|
| Program the radio from the phone | USB-serial adapter (CP2102) for clone-mode protocol |
| PTT from the phone | CP2102 RTS line → NPN transistor → radio PTT pin |
| Phone audio ↔ radio audio | USB sound card (CM108) line-out → radio mic, radio spk → line-in |
| Handset pass-through | RJ-45 jack wired in parallel with priority switching |
| Single cable to phone | USB-C OTG hub inside the box combines serial + audio |
| Mount radio out of sight | Only the USB-C cable runs to the phone on the dash |

---

## Parts List

All parts are available on Amazon (search terms provided). Most items come in
multi-packs, so total cost is **~$55-75** with plenty of spares.

### Core Electronics

| # | Part | Search Term | Qty | ~Price |
|---|------|-------------|-----|--------|
| 1 | **CP2102 USB-to-TTL Module** (6-pin with 3.3V/5V jumper) | "HiLetgo CP2102 USB to TTL 6PIN" | 1 | $7 (5-pack) |
| 2 | **USB Sound Card** (CM108/CM119 chipset, with separate mic+headphone 3.5mm jacks) | "Sabrent AU-MMSA USB External Stereo Sound Adapter" or "DuKabel USB to 3.5mm Audio Adapter" | 1 | $8 |
| 3 | **USB-C OTG Hub** (USB-C male → 4x USB-A female, with power pass-through preferred) | "USB C Hub 4 Port USB 3.0 OTG" | 1 | $10 |
| 4 | **5V SPDT Relay Module** (SRD-05VDC-SL-C, pre-mounted on PCB with transistor driver) | "HiLetgo 1-Channel 5V relay module Arduino" | 1 | $6 (5-pack) |

### Connectors & Breakouts

| # | Part | Search Term | Qty | ~Price |
|---|------|-------------|-----|--------|
| 5 | **RJ-45 Screw Terminal Breakout Board** | "RJ45 breakout board screw terminal" | 2 | $8 (5-pack) |
| 6 | **RJ-45 Ethernet Cable** (short, 1-2 ft, Cat5e) | "1 foot Cat5e ethernet cable" | 1 | $5 (5-pack) |
| 7 | **USB-C Female Breakout Board** | "USB Type-C female breakout board" | 1 | $7 (5-pack) |
| 8 | **3.5mm TRRS Breakout Board** (for CM108 sound card audio connections) | "3.5mm audio jack breakout board" | 2 | $7 (5-pack) |

### Discrete Components

| # | Part | Search Term | Qty | ~Price |
|---|------|-------------|-----|--------|
| 9 | **2N2222A NPN Transistor** | "2N2222A NPN transistor" (or electronics assortment kit) | 2 | $6 (100-pack) |
| 10 | **1N4148 Signal Diode** | "1N4148 diode" | 2 | included in kits |
| 11 | **Resistor Assortment** (need: 1kΩ, 4.7kΩ, 10kΩ) | "resistor assortment kit 1/4W" | 1 | $7 |
| 12 | **Capacitor: 10µF electrolytic** (16V or higher) | "electrolytic capacitor assortment" | 2 | $7 |
| 13 | **Capacitor: 0.1µF ceramic** | included in assortment | 2 | — |

### Enclosure & Misc

| # | Part | Search Term | Qty | ~Price |
|---|------|-------------|-----|--------|
| 14 | **ABS Project Enclosure** (~150×100×50 mm / 6×4×2 in) | "Zulkit ABS project box 150x100x50" | 1 | $8 |
| 15 | **Panel-Mount RJ-45 Coupler** (Female-Female keystone) | "RJ45 keystone coupler panel mount" | 1 | $6 (5-pack) |
| 16 | **Panel-Mount USB-C Female** (or just bolt the breakout board) | see item 7 | — | — |
| 17 | **DPDT Mini Toggle Switch** (ON-ON type) | "DPDT mini toggle switch ON-ON" | 1 | $7 (10-pack) |
| 18 | **Hookup Wire** (22-26 AWG, stranded) | "hookup wire kit 22 AWG" | 1 | $7 |
| 19 | **Solder + Soldering Iron** (if you don't already have one) | "soldering iron kit" | 1 | $15 |
| 20 | **Hot Glue / Cable Ties** | — | — | — |

**Estimated Total: $55-75** (most parts come in multi-packs)

---

## DB20-G RJ-45 Mic Connector Pinout

The DB20-G uses a standard **RJ-45 (8P8C)** connector for the hand microphone.
Pin numbering follows T-568B standard (looking at the plug from the cable end
with the latch tab facing up, pin 1 is on the left).

> **⚠️ IMPORTANT: Verify the pinout on YOUR specific radio before wiring!**
> Radioddity has used slightly different pinouts across models. Use the
> testing procedure in the "Verification" section below.
>
> The pinout is **configurable in the app** (Settings → Hardware Pinout).
> If your radio uses a different pin arrangement, update the configuration
> so the app's documentation and guidance match your wiring.

### Most Likely Pinout (Kenwood-Mobile Compatible)

```
RJ-45 Plug (looking at contacts, latch up)
┌─────────────────────┐
│ 1 2 3 4 5 6 7 8     │
└────────┬────────────┘
         │
Pin 1: MIC IN        — Mic audio input (electret level, ~50mV pp)
Pin 2: +5V/+8V       — Power for hand mic electronics
Pin 3: KEY UP         — Up button on hand mic
Pin 4: KEY DOWN       — Down button on hand mic
Pin 5: PTT            — Push-to-Talk (active LOW: short to GND to transmit)
Pin 6: SPEAKER OUT    — Speaker audio output (~1V pp into 8Ω)
Pin 7: SQL            — Squelch detect (open collector, low = signal present)
Pin 8: GND            — Ground / common
```

### Programming Cable Pin Usage

The Radioddity PC002 programming cable (or compatible) typically uses
the same RJ-45 connector and routes serial data through the MIC and
SPEAKER pins (or dedicated data pins, depending on model):

```
Programming mode:
  CP2102 TXD → Pin 1 (MIC IN)       [serial data overlaid on mic pin]
  CP2102 RXD ← Pin 6 (SPEAKER OUT)  [serial data overlaid on spk pin]
  GND        → Pin 8 (GND)
```

> During programming, the audio pins carry 9600 baud serial data
> instead of analog audio. The radio enters programming mode via
> the magic handshake, then data flows on these same pins.

---

## Verification Procedure

Before wiring the interface box, verify your radio's pinout with a multimeter:

### Step 1: Identify Ground and Power
1. Set multimeter to DC voltage
2. Plug the hand mic into the radio, power on the radio
3. Touch black probe to a known ground (radio chassis, antenna connector shell)
4. Probe each RJ-45 pin on a **breakout board** connected to the radio
5. **GND** reads 0V, **VCC** reads +5V to +8V

### Step 2: Identify PTT
1. Set multimeter to continuity/resistance mode
2. Probe each pin against GND while pressing the PTT button on the hand mic
3. **PTT pin** will show continuity to GND when pressed, open when released
4. Alternatively: probe at the radio's RJ-45 breakout — the pin that goes
   to ~0V when you manually short it to GND and the radio starts transmitting

### Step 3: Identify Speaker
1. Set multimeter to AC voltage (or use an oscilloscope)
2. Tune the radio to an active channel with squelch open
3. The pin showing AC voltage (~0.5-2V AC) is the **speaker output**

### Step 4: Identify Mic
1. Use the process of elimination + test
2. Connect a known signal source (like the CM108 headphone output playing
   a tone) to each remaining pin through a 10µF coupling capacitor
3. The pin where you hear the tone on another radio monitoring the same
   frequency is the **MIC input**

### Step 5: Identify SQL (optional)
1. Set multimeter to DC voltage
2. The SQL pin typically reads HIGH (~5V) when squelch is closed (no signal)
   and LOW (~0V) when a signal breaks squelch
3. Tune to an active channel and watch the pin toggle

Record your results:
```
My DB20-G pinout:
  Pin __: MIC IN
  Pin __: VCC (+__V)
  Pin __: PTT
  Pin __: SPEAKER OUT
  Pin __: SQL
  Pin __: GND
  Pin __: KEY UP
  Pin __: KEY DOWN
```

---

## Schematic

### Block Diagram

```
┌────────────┐        ┌──────────────────────────────────────────────────────┐
│            │        │              INTERFACE BOX                           │
│   PHONE    │        │                                                     │
│            │  USB-C │  ┌──────────┐    ┌──────────┐                       │
│  DB20-G    ├────────┼──┤ USB-C    ├────┤ 4-Port   │                       │
│ Controller │        │  │ Hub      │    │ USB Hub  ├──Port A──►[CP2102]    │
│   App      │        │  │ Board    │    │ Board    ├──Port B──►[CM108 ]    │
│            │        │  └──────────┘    └──────────┘                       │
└────────────┘        │                                                     │
                      │  ┌─────────────────────────────────────────────┐    │
                      │  │                 WIRING                      │    │
┌────────────┐        │  │                                             │    │
│  ORIGINAL  │  RJ-45 │  │  CP2102 TXD ──────────────► Radio Pin 1    │    │
│   HAND     ├────────┼──┤  CP2102 RXD ◄────────────── Radio Pin 6    │    │
│   MIC      │        │  │  CP2102 GND ──────────────── Radio Pin 8   │    │
│            │        │  │  CP2102 RTS ──►[PTT CKT]──► Radio Pin 5    │    │
└────────────┘        │  │                                             │    │
                      │  │  CM108 Spk Out ─►[ATTEN]──► Relay COM      │    │
                      │  │  CM108 Mic In ◄──[ATTEN]◄── Radio Pin 6    │    │
                      │  │                                             │    │
   ┌─────────┐  RJ-45 │  │  Handset Mic ────────────► Relay NC        │    │
   │  RADIO  ◄────────┼──┤  Relay COM ──────────────► Radio Pin 1     │    │
   │  DB20-G │        │  │  Handset PTT ─►[D1]──┬──► Radio Pin 5     │    │
   │  MIC    │        │  │  PTT CKT out ─►[D2]──┘                    │    │
   │  JACK   │        │  │                                             │    │
   └─────────┘        │  │  Handset Spk ◄───────────── Radio Pin 6    │    │
                      │  │  (paralleled with CM108 Mic In)             │    │
                      │  └─────────────────────────────────────────────┘    │
                      │                                                     │
                      │  [DPDT SWITCH] ── selects Phone/Handset mic source  │
                      │  [RELAY]       ── controlled by switch or CP2102    │
                      └──────────────────────────────────────────────────────┘
```

### PTT Circuit Detail

The PTT pin on the radio is **active-low**: connecting it to GND triggers
transmit. Both the phone (via CP2102 RTS) and the hand mic PTT button can
trigger it independently.

```
                          +5V (from CP2102 VCC)
                           │
                           R3 (10kΩ) pull-up
                           │
CP2102 RTS ── R1 (1kΩ) ── B┐                  ┌──── To Radio PTT (Pin 5)
                            │ Q1 (2N2222A)     │
                            E│                 │
                            │                  │
                           GND     D1 (1N4148) │ (cathode toward PTT)
                                   ┌───┤◄──────┘
                                   │
Handset PTT pin ───────────────────┘
(goes to GND when pressed)
```

**How it works:**
- **Phone PTT**: App sets RTS HIGH → Q1 base gets current through R1 →
  Q1 conducts → PTT pin pulled to GND through Q1 → radio transmits
- **Handset PTT**: User presses PTT on hand mic → PTT pin shorted to
  GND through the hand mic's internal switch → radio transmits
- **D1** prevents back-feed from Q1 into the hand mic PTT circuit
- **R3** keeps PTT HIGH (not transmitting) when neither source is active

> **App PTT Config**: Set PTT line to **RTS**, inverted **OFF** in the app.
> The transistor does the inversion (RTS HIGH = PTT active-low = transmit).

### Audio Circuit Detail

#### Phone → Radio (Transmit Audio)

The CM108 sound card headphone output (~1V pp) needs to be attenuated
to electret microphone level (~50mV pp) for the radio's mic input.

```
CM108 Headphone Out
       │
       C1 (10µF electrolytic, + toward CM108)    DC blocking
       │
       R4 (10kΩ)                                 ┐
       │                                          > Voltage divider
       ├──────────────────► To Relay COM ──►     ┘  ~20:1 attenuation
       │                     (then to Radio MIC)
       R5 (470Ω)
       │
      GND
```

The voltage divider gives approximately **21:1 attenuation**
(470 / (10000+470) ≈ 0.045), reducing 1V pp to ~45mV pp — right in the
electret mic range. Adjust R5 if your radio needs more/less:
- Louder: increase R5 (680Ω, 1kΩ)
- Quieter: decrease R5 (220Ω, 330Ω)

#### Radio → Phone (Receive Audio)

The radio's speaker output (~1V pp, low impedance) needs attenuation
for the CM108's mic input (~20mV pp expected).

```
Radio SPEAKER (Pin 6)
       │
       C2 (10µF electrolytic, + toward radio)     DC blocking
       │
       R6 (47kΩ)                                  ┐
       │                                           > ~50:1 attenuation
       ├──────────────────► CM108 Mic Input        ┘
       │
       R7 (1kΩ)
       │
      GND


(Also parallel: R8 (1kΩ) → Handset SPK pin for pass-through)
```

### Mic Source Switching (Relay + DPDT Switch)

A DPDT toggle switch on the box selects the mic source. The switch
directly controls the relay coil through a transistor:

```
                        +5V (from USB hub / CP2102 VCC)
                         │
Switch ── R9 (1kΩ) ── B┐ │
(PHONE                  │ Q2 (2N2222A)
 position)              E│
                        │
                       GND
                               │
                         Relay │Coil (+)
                               │
                         Relay Coil (-)
                               │
                              GND
                          (D2 1N4148 flyback diode across coil)

Relay contacts:
  COM ──────────► Radio MIC pin (Pin 1)
  NC  ◄────────── Handset MIC (straight through when switch=HANDSET)
  NO  ◄────────── CM108 audio out (attenuated, connected when switch=PHONE)
```

**Switch positions:**
- **HANDSET** (switch open): Relay de-energized → NC contact → Handset mic
  feeds radio. Hand mic works normally.
- **PHONE** (switch closed): Relay energized → NO contact → CM108 audio
  feeds radio. Phone app controls everything.

> **Note:** PTT works from either source regardless of switch position.
> The switch only selects the mic audio source. You can PTT from the phone
> even while the handset mic is selected.

### Speaker Distribution

The radio's speaker output is **paralleled** to both the CM108 mic input
and the handset speaker pin. Both receive audio simultaneously:

```
Radio SPEAKER (Pin 6) ──┬──► C2 → R6/R7 divider → CM108 Mic In
                         │
                         └──► Direct to Handset SPK pin (via pass-through)
```

This means you can monitor radio audio from the phone app AND the hand
mic speaker at the same time.

---

## Full Wiring Diagram (Pin-by-Pin)

```
============================================================================
                      RADIO RJ-45 CONNECTOR
============================================================================

Radio Pin 1 (MIC) ◄──── Relay COM output
Radio Pin 2 (VCC) ────► Handset Pin 2 (VCC pass-through)
Radio Pin 3 (UP)  ────► Handset Pin 3 (pass-through)
Radio Pin 4 (DOWN)────► Handset Pin 4 (pass-through)
Radio Pin 5 (PTT) ◄──┬─ Q1 collector (Phone PTT)
                      └─ D1 anode ◄── Handset Pin 5 (Handset PTT)
Radio Pin 6 (SPK) ──┬─► C2 → R6/R7 → CM108 Mic In
                     └─► Handset Pin 6 (SPK pass-through)
Radio Pin 7 (SQL) ──┬─► Handset Pin 7 (pass-through)
                     └─► CP2102 CTS (optional squelch detect)
Radio Pin 8 (GND) ────► Common GND (all grounds tied together)

============================================================================
                      CP2102 MODULE
============================================================================

CP2102 TXD ──────────► Radio Pin 1 (MIC) [via programming path*]
CP2102 RXD ◄────────── Radio Pin 6 (SPK) [via programming path*]
CP2102 RTS ──── R1 (1kΩ) ──► Q1 Base (PTT circuit)
CP2102 CTS ◄──── Radio Pin 7 (SQL) [optional]
CP2102 VCC ──────► +5V bus (relay, pull-ups)
CP2102 GND ──────► Common GND

* Note: During programming, serial data shares the MIC/SPK pins.
  Set the box switch to PHONE mode during programming so the relay
  disconnects the handset mic. The app handles switching between
  serial data mode and audio mode in software.

============================================================================
                      CM108 USB SOUND CARD
============================================================================

CM108 Headphone Out ──► C1 → R4/R5 divider → Relay NO contact
CM108 Mic In ◄──────── C2 → R6/R7 divider ◄── Radio Pin 6 (SPK)
CM108 GND ──────────── Common GND

============================================================================
                      USB HUB (inside box)
============================================================================

USB-C Input ◄────────── Phone cable (USB-C to USB-C)
USB-A Port 1 ──────────► CP2102 module (USB plug)
USB-A Port 2 ──────────► CM108 sound card (USB plug)
USB-A Port 3 ──────────► (spare)
```

---

## Assembly Instructions

### Step 1: Prepare the Enclosure

1. Mark and drill holes in the project box:
   - **Front face**: USB-C panel mount hole, RJ-45 keystone hole,
     DPDT toggle switch hole, LED indicator hole (optional)
   - **Back face** (or side): RJ-45 cable exit hole (for the cable going
     to the radio), strain relief grommet
2. Mount the USB-C breakout board, RJ-45 keystone coupler, and toggle switch
3. Label: "PHONE" / "HANDSET" next to the toggle switch
4. Label: "USB-C (Phone)" and "RJ-45 (Hand Mic)" next to their ports

### Step 2: Prepare RJ-45 Cables

1. Cut the short RJ-45 cable in half
2. Strip and separate all 8 conductors on each cut end
3. **Cable A** (RJ-45 plug end): This plugs into the radio's mic jack.
   Wire to "Radio Breakout" screw terminal board
4. **Cable B** (RJ-45 plug end): This connects the handset keystone jack
   to the internal handset breakout board

> **Wire color guide (T-568B standard):**
> | Pin | Color | Signal |
> |-----|-------|--------|
> | 1 | Orange/White | MIC |
> | 2 | Orange | VCC |
> | 3 | Green/White | UP |
> | 4 | Blue | DOWN |
> | 5 | Blue/White | PTT |
> | 6 | Green | SPK |
> | 7 | Brown/White | SQL |
> | 8 | Brown | GND |

### Step 3: Build the PTT Circuit

1. On a small piece of perfboard or dead-bug style:
2. Solder Q1 (2N2222A):
   - **Base** → 1kΩ resistor (R1) → wire to CP2102 RTS pad
   - **Collector** → wire to Radio PTT (Pin 5)
   - **Emitter** → GND
3. Solder R3 (10kΩ) from Radio PTT (Pin 5) to +5V (pull-up)
4. Solder D1 (1N4148): cathode to Radio PTT (Pin 5), anode to
   Handset PTT (Pin 5)
5. Test: applying +3.3V to Q1 base through R1 should pull the PTT
   line to GND

### Step 4: Build the Audio Attenuators

**TX path (CM108 → Radio):**
1. Solder C1 (10µF) in series with CM108 headphone output (+ toward CM108)
2. After C1, solder R4 (10kΩ) in series
3. From the R4/R5 junction, run a wire to the Relay NO terminal
4. Solder R5 (470Ω) from the junction to GND

**RX path (Radio → CM108):**
1. Solder C2 (10µF) in series with Radio Speaker pin (+ toward radio)
2. After C2, solder R6 (47kΩ) in series
3. From the R6/R7 junction, run a wire to CM108 Mic Input
4. Solder R7 (1kΩ) from the junction to GND

### Step 5: Wire the Relay

1. Mount the relay module in the box
2. Connect relay coil control:
   - If using the dedicated relay module with transistor driver:
     just connect VCC, GND, and Signal (from toggle switch)
   - If using bare relay: wire Q2 (2N2222A) with R9 (1kΩ) on base,
     collector to relay coil, flyback diode (D2) across coil
3. Connect relay switch terminals:
   - **COM** → Radio Pin 1 (MIC input)
   - **NC** → Handset Pin 1 (MIC from hand mic)
   - **NO** → TX attenuator output (CM108 audio)

### Step 6: Wire the Pass-Through

Connect the handset RJ-45 breakout to the radio RJ-45 breakout for all
pass-through signals:

| Signal | Handset Pin | → | Radio Pin | Notes |
|--------|-------------|---|-----------|-------|
| VCC | 2 | → | 2 | Direct pass-through |
| UP | 3 | → | 3 | Direct pass-through |
| DOWN | 4 | → | 4 | Direct pass-through |
| PTT | 5 | → D1 → | 5 | Through diode D1 |
| SPK | 6 | ← | 6 | Direct (paralleled with CM108 input) |
| SQL | 7 | ← | 7 | Direct pass-through |
| GND | 8 | → | 8 | Direct pass-through |
| MIC | 1 | → Relay NC | (1) | Through relay NC contact |

### Step 7: Connect the USB Hub

1. Open the USB-C hub and remove its plastic shell (or use bare hub PCB)
2. Desolder the USB-C cable and wire it to the panel-mount USB-C breakout
3. Plug CP2102 into USB-A port 1
4. Plug CM108 sound card into USB-A port 2
5. Secure everything with hot glue or double-sided tape
6. Verify the hub provides enough power (test with phone connected)

### Step 8: Final Assembly

1. Route all wires neatly inside the box
2. Secure the perfboard/PTT circuit with hot glue or standoffs
3. Use cable ties for wire management
4. Close the enclosure
5. Label all external connections clearly

---

## Testing Procedure

### Test 1: USB Enumeration
1. Connect the phone to the box via USB-C
2. The phone should detect **two** USB devices:
   - CP2102 serial adapter (shows in app device list)
   - USB audio device (shows in Android audio settings)
3. If not detected, try a different USB-C cable (must support data, not
   charge-only)

### Test 2: Programming Mode
1. Set switch to PHONE
2. Open the app → Tools tab → Download from Radio
3. Should successfully read all channels
4. If it fails: check CP2102 TXD/RXD wiring to the correct pins

### Test 3: PTT
1. Set switch to PHONE
2. Connect to the radio in the app
3. Go to Live tab, press and hold PTT button
4. Radio should key up (TX indicator on radio should light)
5. Release → radio should drop back to RX
6. If PTT doesn't work: check Q1 wiring, verify RTS toggles with
   multimeter on CP2102 RTS pin

### Test 4: Audio TX (Phone → Radio)
1. Monitor the radio's frequency from another radio
2. Set switch to PHONE
3. Hold PTT in the app, speak into the phone mic
4. You should hear audio on the monitoring radio
5. Adjust volume: use Android volume controls or change R5 value
6. If no audio: check CM108 is selected as audio output, check
   relay is in PHONE position, check attenuator wiring

### Test 5: Audio RX (Radio → Phone)
1. Transmit on the same frequency from another radio
2. You should hear audio through the phone's speaker
3. Check audio level bar in the app moves
4. If no audio: check C2/R6/R7 wiring, check CM108 mic input

### Test 6: Handset Pass-Through
1. Set switch to HANDSET
2. Plug the original hand mic into the RJ-45 port on the box
3. The hand mic should work normally: PTT, audio, up/down buttons
4. If not: check pass-through wiring pin by pin

### Test 7: Dual Operation
1. With hand mic plugged in, set switch to PHONE
2. PTT from the phone app → radio should transmit
3. PTT from the hand mic → radio should also transmit
4. Speak on phone → audio goes to radio
5. Receive audio → heard on both phone and hand mic speaker

---

## Troubleshooting

| Problem | Likely Cause | Fix |
|---------|-------------|-----|
| Phone doesn't see USB devices | Bad USB-C cable, no OTG support | Try different cable, check phone supports USB OTG |
| Programming works but PTT doesn't | RTS not wired, Q1 backwards | Check Q1 pinout (flat side facing you: E-B-C left to right for TO-92) |
| PTT works but no TX audio | Relay in wrong position, attenuator issue | Check switch position, measure signal at relay COM |
| TX audio distorted/too loud | Attenuator ratio wrong | Increase R4 or decrease R5 |
| RX audio too quiet | Attenuator too aggressive | Decrease R6 or increase R7 |
| Handset doesn't work in pass-through | Pin wiring reversed | Re-verify pinout with multimeter |
| Relay buzzes/chatters | Insufficient drive current | Check 5V supply, verify Q2 is saturating |
| Phone loses USB connection intermittently | Power issue | Use USB-C hub with power delivery, or add external 5V supply |

---

## Advanced: SQL Pin for RX Detection

If your radio provides a squelch output on Pin 7:

1. Wire Radio Pin 7 → CP2102 CTS (through the breakout)
2. The app reads CTS to detect when the squelch opens (signal present)
3. This enables the RX indicator in the Live tab
4. The SQL pin is typically open-collector: LOW when signal present,
   pulled HIGH when squelch is closed

Wire a pull-up resistor (10kΩ to +5V) on the CTS line if the signal
floats when no SQL output is driving it.

---

## Cable Routing for Vehicle Installation

For mounting the radio out of sight:

```
┌────────────────┐
│  Phone on      │
│  dash mount    │
│       │        │
│   USB-C cable  │ (1-2 meter USB-C extension)
│       │        │
│  ┌────▼─────┐  │
│  │Interface │  │  ← Mount under dash or center console
│  │  Box     │  │
│  └──┬───┬───┘  │
│     │   │      │
│   RJ-45 │      │  ← Short RJ-45 cable to radio
│     │   DC     │  ← Radio power cable
│  ┌──▼───▼──┐   │
│  │ DB20-G  │   │  ← Mount in trunk, under seat, or behind panel
│  │  Radio  │   │
│  └─────────┘   │
└────────────────┘
```

- Use a **1-2 meter USB-C extension cable** from the dash to the interface box
- Mount the interface box near the radio (short cable runs = less noise)
- Use ferrite chokes on the USB-C cable if you experience RF interference
- Route the radio's antenna cable to the roof as usual
- Optionally, bring the hand mic cable to the dash as a backup

---

## Parts Checklist (for ordering)

Copy-paste these Amazon search terms:

```
□ HiLetgo CP2102 USB to TTL 6PIN module
□ Sabrent AU-MMSA USB External Stereo Sound Adapter
□ USB C Hub 4 Port USB 3.0 OTG
□ HiLetgo 1-Channel 5V relay module Arduino
□ RJ45 breakout board screw terminal
□ 1 foot Cat5e ethernet cable
□ USB Type-C female breakout board
□ 2N2222A NPN transistor
□ 1N4148 signal diode (or electronics assortment kit)
□ Resistor assortment kit 1/4W
□ Electrolytic capacitor assortment
□ ABS project box enclosure 150x100x50mm
□ RJ45 keystone coupler panel mount
□ DPDT mini toggle switch ON-ON
□ Hookup wire kit 22 AWG stranded
□ Soldering iron kit (if needed)
```
