# DB20-G Interface Board — Assembly Guide (v10, ESP32 Bluetooth)

> **Board revision:** v10 (ESP32-WROOM-32E Bluetooth)
> **Replaces:** v9 USB design (FE1.1s + CP2102N + CM108AH)
>
> ⚠ **This guide describes the v10 Bluetooth board.** The v9 USB assembly
> guide is archived in `archive/ASSEMBLY-v9.md`.

## Overview

The v10 board has significantly fewer components than v9 — one module
(ESP32-WROOM-32E) replaces the USB hub, serial bridge, and audio codec.
Assembly is divided into 5 phases, each testable independently.

**Tools required:**
- Soldering iron with fine tip (conical or chisel ≤ 1.5 mm)
- Solder (0.5 mm 63/37 leaded or SAC305 lead-free)
- Flux pen or no-clean flux
- Tweezers (for 0805 passives)
- Multimeter
- USB-UART adapter (3.3 V logic) for firmware flashing

**Component count:** ~39 (vs ~62 in v9)

---

## Phase 1 — Power Supply

**Components:** F1 (500 mA polyfuse), U2 (AMS1117-3.3), C1 (10 µF), C2 (22 µF), C3 (100 nF)

1. Solder U2 (AMS1117-3.3) SOT-223 — tab pad is GND.
2. Solder C1 (10 µF) input cap close to U2 pin 3 (Vin).
3. Solder C2 (22 µF) output cap close to U2 pin 2 (Vout).
4. Solder C3 (100 nF) bypass cap next to C2.
5. Solder F1 polyfuse between input power and U2 Vin.

**Test:** Apply 5 V to input. Measure 3.3 V ± 0.1 V on output rail. Current draw should be < 5 mA (no load).

---

## Phase 2 — ESP32 Module

**Components:** U1 (ESP32-WROOM-32E), R11 (10 k EN pull-up), R12 (10 k GPIO0 pull-up), C6 (22 µF bulk), C7 (100 nF EN decoupling)

1. **Tin the ground pad** on the PCB center pad for U1.
2. Align U1 (ESP32-WROOM-32E) — the antenna overhang goes to the board edge with no ground plane underneath.
3. Solder one corner pin first, verify alignment, then solder all castellated pads.
4. Reflow the center GND pad with hot air or pre-tinning.
5. Solder R11 (10 k) from EN to 3.3 V rail.
6. Solder C7 (100 nF) from EN to GND (RC delay for clean boot).
7. Solder R12 (10 k) from GPIO0 to 3.3 V rail.
8. Solder C6 (22 µF) bulk cap near ESP32 3.3 V pins.

**Test:** Connect USB-UART adapter to J5 (TX→RX, RX→TX, GND→GND). Power
the board. Open a terminal at 115200 baud — you should see the ESP32 boot
message. If GPIO0 is held LOW during power-on, it enters flash mode.

---

## Phase 3 — PTT & Relay Driver

**Components:** Q1, Q2 (2N2222A), R1, R2 (10 k), D1 (1N4148), D2 (1N5819), K1 (G5V-2-DC5 DPDT relay)

1. Solder Q1 (2N2222A) for PTT drive. Emitter to GND, collector to PTT line (J2/J3 pin).
2. Solder R1 (10 k) from ESP32 GPIO4 to Q1 base.
3. Solder D1 (1N4148) cathode-to-3.3 V, anode-to-PTT line (clamp diode).
4. Solder Q2 (2N2222A) for relay drive. Emitter to GND, collector to K1 coil.
5. Solder R2 (10 k) from ESP32 GPIO5 to Q2 base.
6. Solder D2 (1N5819) across K1 coil (cathode to +5 V, anode to collector) — flyback protection.
7. Solder K1 relay. Ensure correct pin orientation for DPDT contacts.

**Test:** Power the board and flash firmware (see Phase 5). Use serial
monitor: PTT DOWN / PTT UP should toggle Q1. Relay SERIAL / AUDIO should
click K1.

---

## Phase 4 — Audio Path, LEDs & Connectors

**Components:** R3–R6 (divider resistors), C4–C5 (audio coupling caps), R7–R10 (220 Ω LED resistors), LED1–LED4, J2, J3 (RJ-45), J4 (2-pin power header)

1. Solder R3/R4 voltage divider for DAC output (GPIO25 → radio MIC).
   - Scales ESP32 0–3.3 V DAC down to ~1 Vpp for radio mic input.
2. Solder C4 (100 nF) AC coupling cap in DAC output path.
3. Solder R5/R6 voltage divider for ADC input (radio SPK → GPIO36).
   - Biases and attenuates radio speaker output to 0–3.3 V ADC range.
4. Solder C5 (100 nF) AC coupling cap in ADC input path.
5. Solder R7–R10 (220 Ω) LED current limiters.
6. Solder LED1–LED4 (0805) noting polarity:
   - LED1 = Power/heartbeat (green)
   - LED2 = PTT active (red)
   - LED3 = Audio activity (yellow)
   - LED4 = BT connected (blue)
7. Solder J2, J3 (RJ-45 connectors) for radio and handset pass-through.
8. Solder J4 (2-pin header) for external 5 V power input.

**Test:** With firmware running, observe LED1 heartbeat blink. LED4 should
fast-blink when no BT client is connected, and go solid when paired.

---

## Phase 5 — Flash Header & First Boot

**Components:** J5 (1×4 pin header: 3.3 V, TX, RX, GND)

1. Solder J5 pin header on the board edge for easy access.
2. Connect USB-UART adapter:
   - J5 pin 1 (3.3 V) → adapter 3.3 V (optional — board is self-powered)
   - J5 pin 2 (TX) → adapter RX
   - J5 pin 3 (RX) → adapter TX
   - J5 pin 4 (GND) → adapter GND
3. Hold GPIO0 button/jumper LOW, power cycle the board (enters flash mode).
4. Flash firmware: `pio run -t upload` from the `firmware/` directory.
5. Release GPIO0, power cycle — board boots normally.
6. Pair phone: open Android Bluetooth settings, search for **"DB20G-Interface"**.
7. Open the DB20-G app → tap the connection pill → select the BT device.
8. Verify: connection status shows "Connected (BT)", all 4 LEDs behave correctly.

---

## Post-Assembly Checklist

| Check | Expected |
|-------|----------|
| 3.3 V rail | 3.30 V ± 0.05 V |
| ESP32 boot message (115200 baud) | `[DB20G] Booting...` / `[DB20G] Ready` |
| LED1 heartbeat | Brief flash every 3 s |
| LED4 BT advertising | Rapid blink when no client |
| BT pairing | "DB20G-Interface" visible on phone |
| PTT toggle | LED2 lights, relay clicks |
| Relay serial/audio | K1 clicks, UART path switches |
| Audio RX | LED3 blinks on received audio |

---

## Power Options

The board expects **5 V input** on J4 (or from the radio handset port — see
BT-5 in the roadmap). The AMS1117-3.3 regulates down to 3.3 V for the ESP32.

| Source | Connector | Notes |
|--------|-----------|-------|
| USB wall adapter | J4 2-pin header | Simplest; any 5 V / 1 A adapter |
| Vehicle USB port | J4 via USB cable | Cut a USB cable, use red (+5 V) and black (GND) |
| Radio handset port | J2 RJ-45 pin 2 | +5–8 V from radio backlight supply; see WIRING.md |
