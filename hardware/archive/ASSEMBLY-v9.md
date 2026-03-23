# DB20-G Interface Box — Assembly Guide

Step-by-step instructions for building the interface box. Assumes basic soldering skills and a temperature-controlled soldering iron.

## Required Tools

- Soldering iron (temperature-controlled, 300-350°C tip)
- Solder (0.5mm 63/37 leaded or SAC305 lead-free)
- Flux (no-clean liquid or paste)
- Tweezers (fine-tip, curved preferred)
- Magnification (loupe, headband magnifier, or USB microscope)
- Multimeter (continuity and voltage)
- Flush cutters
- Solder wick / desoldering pump
- Isopropyl alcohol + brush (for cleaning flux)
- Hot air station (optional, helpful for QFN and SSOP)

## PCB Fabrication

1. Open `kicad/DB20G-Interface.kicad_pcb` in KiCad 8
2. **Plot → Gerbers** with these settings:
   - Layers: F.Cu, B.Cu, F.SilkS, B.SilkS, F.Mask, B.Mask, Edge.Cuts
   - Format: Gerber X2
   - Drill: Generate separate drill file (Excellon)
3. Upload the Gerber zip to your PCB fabricator:
   - **JLCPCB**: Upload zip → 2-layer, 1.6mm, HASL, any color
   - **PCBWay**: Same settings, choose "quick order"
4. 5 PCBs typically cost $2-5 + $5-10 shipping

### SMT Assembly Service (Optional)

JLCPCB offers SMT assembly for the SMD components:
1. Upload `BOM.csv` and the pick-and-place file from KiCad
2. Select "Economic" assembly (bottom or top side)
3. The through-hole components (Q1, Q2, K1, J2, J3, Y1) must still be hand-soldered

## Assembly Order

Build in this order to make each step accessible before later components block access.

### Phase 1: Power Supply

**Goal:** Get stable 5V and 3.3V power rails working before populating ICs.

1. **U4 (AMS1117-3.3)** — Solder the 3.3V regulator first
   - Orient the tab marking (GND) toward the correct pad
   - SOT-223 is easy to hand-solder: tin one pad, place, solder others
2. **C7 (10µF)** — AMS1117 input capacitor
3. **C8 (22µF)** — AMS1117 output capacitor
4. **F1 (500mA polyfuse)** — USB input protection
5. **D2 (1N5819)** — Reverse polarity protection, cathode band toward 5V rail

**Test Point 1:** Apply 5V to the USB-C pads (or via a breakout board). Measure:
- 5V on the VBUS rail ✓
- 3.3V on the 3V3 rail ✓
- No shorts between power and ground ✓

### Phase 2: USB Hub

**Goal:** Get the USB hub enumerating on the phone.

6. **U1 (FE1.1s)** — SSOP-28, pin 1 dot toward top-left
   - Apply flux to all pads
   - Tack one corner pin, align, solder remaining pins
   - Check for bridges with magnification
7. **Y1 (12MHz crystal)** — Near U1, solder the two pads
8. **C11, C12 (22pF)** — Crystal load capacitors
9. **C1 (100nF)** — FE1.1s bypass capacitor, near VCC pin
10. **C2 (10µF)** — FE1.1s bulk decoupling
11. **R11 (4.7kΩ)** — USB D+ pull-up for downstream ports
12. **R12 (1.5kΩ)** — USB D+ pull-up for upstream port
13. **J1 (USB-C receptacle)** — Carefully align, solder shield pads first, then signal pins

**Test Point 2:** Connect to phone with USB-C cable. Check:
- `lsusb` (Linux) or USB debug app shows "FE1.1s USB Hub" ✓
- No smoke or excessive heat ✓
- Phone may show "USB device connected" notification ✓

### Phase 3: Serial (CP2102N)

**Goal:** Get serial communication with the radio working.

14. **U2 (CP2102N)** — QFN-28, requires careful placement
    - Apply solder paste/flux to all pads including thermal pad
    - Place chip with pin 1 indicator aligned
    - If hand-soldering: carefully solder exposed pads, then periphery
    - Hot air recommended: 380°C, low flow, 30-45 seconds
15. **C3 (100nF)** — CP2102N bypass
16. **C4 (10µF)** — CP2102N bulk decoupling

**Test Point 3:** Connect USB-C cable to phone.
- The app should detect a CP2102 serial device ✓
- Serial TX/RX is routed through the RJ-45 MIC/SPK pins (tested in Phase 6) ✓

### Phase 4: Audio (CM108AH)

**Goal:** Get audio flowing between phone and radio.

18. **U3 (CM108AH)** — SSOP-28, same technique as FE1.1s
19. **C5 (100nF)** — CM108AH bypass
20. **C6 (10µF)** — CM108AH bulk decoupling
21. **R3 (10kΩ)** — Audio input attenuator (radio speaker → CM108)
22. **R4 (1kΩ)** — Audio input attenuator ground leg
23. **C9 (1µF)** — Audio coupling capacitor (RX path)
24. **R5 (10kΩ)** — Audio output attenuator (CM108 → radio mic)
25. **R6 (1kΩ)** — Audio output attenuator ground leg
26. **C10 (1µF)** — Audio coupling capacitor (TX path)

**Test Point 4:** Connect to radio via RJ-45. In the app:
- Switch audio to USB mode in Live tab ✓
- Tune to an active channel — you should hear audio through the phone ✓
- Audio level should be comfortable (adjust R3 if too loud/quiet) ✓

### Phase 5: PTT Circuit

**Goal:** Get push-to-talk working from the app.

27. **Q1 (2N2222A)** — PTT transistor driver
    - Flat side orientation per PCB silkscreen (E-B-C pinout)
    - Solder through-hole leads, trim flush
28. **R1 (10kΩ)** — Q1 base resistor (limits base current from RTS/DTR)
29. **Q2 (2N2222A)** — Relay driver transistor
30. **R2 (10kΩ)** — Q2 base resistor
31. **K1 (G5V-1-DC5)** — 5V relay
    - Orient per silkscreen markings
    - Solder through-hole pins
32. **D1 (1N4148)** — Flyback diode across relay coil
    - Cathode band toward relay VCC side

**Test Point 5:** In the app Live tab:
- Press and hold PTT button ✓
- Red PTT LED should light ✓
- Radio should key up (TX indicator on radio display) ✓
- Release PTT — radio returns to RX ✓
- Try both RTS and DTR modes in PTT config ✓

### Phase 6: LEDs and RJ-45

33. **LED1 (green)** — Power indicator
34. **R7 (330Ω)** — LED1 current limit
35. **LED2 (red)** — PTT active indicator
36. **R8 (330Ω)** — LED2 current limit
37. **LED3 (yellow)** — Audio activity indicator
38. **R9 (330Ω)** — LED3 current limit
39. **LED4 (blue)** — Serial TX/RX indicator
40. **R10 (330Ω)** — LED4 current limit
41. **J2 (RJ-45)** — Radio-side handset connector
42. **J3 (RJ-45)** — Handset pass-through connector

**Test Point 6:** All four LEDs should light/blink appropriately:
- Green: solid when powered ✓
- Red: lights during PTT ✓
- Yellow: flickers with audio activity ✓
- Blue: flickers during serial communication ✓

### Phase 7: Debug Header (Optional)

43. **J5 (2×5 pin header)** — Debug/expansion header
    - Provides access to UART TX/RX, 3.3V, 5V, GND, GPIO
    - Useful for firmware debugging or future expansion
    - Can be omitted for production builds

## Final Assembly

1. Clean the PCB with isopropyl alcohol to remove flux residue
2. Inspect all solder joints under magnification
3. Check for solder bridges, especially on SSOP and QFN packages
4. Mount PCB in the 3D-printed enclosure (see `enclosure/DB20G-Enclosure.scad`)
5. Secure with M3×6mm screws through mounting holes

## Complete System Test

1. Connect USB-C cable from phone to interface box
2. Connect RJ-45 cable from interface box to radio handset port
3. Plug original handset into pass-through RJ-45 (J3)
4. Open the DB20-G Controller app

**Verify each function in order:**

| # | Test                            | Expected Result                              |
|---|----------------------------------|----------------------------------------------|
| 1 | App detects USB devices           | Shows CP2102 + CM108 in device list          |
| 2 | Connect to CP2102                 | "Connected" status in app                    |
| 3 | Download from radio               | All 500 channels read successfully           |
| 4 | Switch audio to USB               | Audio routes through CM108                   |
| 5 | Tune to active channel            | Hear received audio on phone speaker/headset |
| 6 | Press PTT in app                  | Radio keys up, red LED lights                |
| 7 | Speak into phone mic              | Audio transmits through radio                |
| 8 | Use hand mic (pass-through)       | Hand mic PTT and audio still work            |
| 9 | Upload channel changes            | Channels written to radio successfully       |
| 10| Disconnect USB                    | App shows "Disconnected" cleanly             |

## Troubleshooting

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for common issues and solutions.
