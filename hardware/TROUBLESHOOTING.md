# DB20-G Interface Box — Troubleshooting Guide

Common issues and solutions for building and using the interface box.

---

## 1. Phone Doesn't Detect USB Devices

**Symptoms:** No "USB device connected" notification. App shows "No USB serial devices found."

| Check | Action |
|-------|--------|
| Cable | Try a different USB-C cable. Charge-only cables lack data wires. |
| OTG | Ensure phone supports USB OTG (most phones since 2018 do). |
| Power | Measure 5V on VBUS at the interface box. If zero, cable or connector issue. |
| Hub | Check FE1.1s solder joints, especially pin 1 (VCC) and GND pins. |
| Crystal | Verify Y1 (12MHz) is soldered properly. FE1.1s won't enumerate without clock. |
| Caps | Ensure C1 (100nF) and C2 (10µF) are near FE1.1s VCC pin. |
| Orientation | FE1.1s pin 1 must match PCB silkscreen dot. Rotated 180° = dead. |

**Quick Test:** Use a USB OTG adapter + regular USB-A hub. If the phone sees the hub, your USB-C receptacle (J1) may have a solder issue.

---

## 2. Serial Device Detected But Can't Connect to Radio

**Symptoms:** App shows CP2102 in device list, connects, but "Download from Radio" fails or times out.

| Check | Action |
|-------|--------|
| Data cable | Verify the 3.5mm TRS cable is connected to the radio's **rear data port** (not the front mic jack). |
| Pinout | Tip = TX (from interface to radio), Ring = RX (from radio), Sleeve = GND. Swap tip/ring if reversed. |
| Baud rate | Must be 9600 8N1. This is hardcoded in the app — no user adjustment needed. |
| Radio mode | The radio must be powered ON and in normal mode (not scanning or in a menu). |
| Cable length | Keep data cable under 2 meters. Long cables degrade 3.3V TTL signals. |
| 3.3V rail | Measure U4 (AMS1117) output. Must be 3.2-3.4V. If zero, U4 is bad or C8 is shorted. |
| CP2102N orient. | QFN-28 pin 1 must align with PCB marking. Rotated = wrong TX/RX. |

**Quick Test:** Use a USB-to-serial adapter (like a standalone CP2102 breakout) directly. If that works, the issue is on the interface box PCB.

---

## 3. No Audio (Can't Hear Radio / Radio Can't Hear Phone)

**Symptoms:** Serial control works fine, PTT works, but no audio in either direction.

### Can't Hear Radio on Phone (RX Path)

| Check | Action |
|-------|--------|
| Audio mode | In Live tab, ensure audio is set to **USB** (not Phone Speaker). |
| RJ-45 cable | Verify RJ-45 cable is connected between interface box J2 and radio handset port. |
| Speaker pins | Measure AC voltage on J2 pin 5 with radio receiving audio. Should see ~0.5-2V AC. |
| Coupling cap | C9 (1µF) must be present. Without it, DC bias from radio can damage CM108 input. |
| Attenuator | Check R3 (10kΩ) and R4 (1kΩ) values. Swapped values = 10× too loud or quiet. |
| CM108 solder | Inspect CM108AH for solder bridges, especially on MIC_IN pin. |

### Radio Can't Hear Phone (TX Path)

| Check | Action |
|-------|--------|
| PTT first | You must be keyed up (PTT pressed) for the radio to accept mic audio. |
| Volume | Set phone media volume to ~75%. Too low = inaudible, too high = distorted. |
| Coupling cap | C10 (1µF) must be present. |
| Attenuator | Check R5 (10kΩ) and R6 (1kΩ). |
| Mic pin | Verify J2 pin 2 (MIC) has audio when PTT is held and phone is playing audio. |

**Quick Test:** Plug headphones into the 3.5mm audio output header test point (if populated) to verify CM108 is outputting audio.

---

## 4. PTT Doesn't Work (Radio Won't Transmit)

**Symptoms:** Pressing PTT in app does nothing. Radio stays in receive mode.

| Check | Action |
|-------|--------|
| PTT config | In Live tab → PTT Config, try both **RTS** and **DTR** modes. |
| PTT polarity | Try toggling "Inverted" in PTT config. Some cables use active-low. |
| Transistor | Verify Q1 is oriented correctly (flat side per silkscreen). E-B-C pinout. |
| Base resistor | R1 (10kΩ) must be between CP2102 RTS and Q1 base. |
| PTT line | Measure J2 pin 4: should be ~5-8V when idle, near 0V when PTT pressed. |
| Connection | Pin 4 on RJ-45 must make contact. Damaged RJ-45 crimps are common. |
| Relay path | If transistor PTT fails, try relay: set PTT to DTR mode, check Q2 + K1. |
| Relay diode | D1 (1N4148) must be oriented correctly or relay may not release cleanly. |

**Quick Test:** With a multimeter on J2 pin 4 (PTT) relative to pin 3 (GND), press PTT in the app. Voltage should drop to near 0V. If it doesn't, the transistor driver circuit has an issue.

---

## 5. Audio Hum or Noise

**Symptoms:** Audible buzz, hum, or digital interference in received or transmitted audio.

| Cause | Solution |
|-------|----------|
| Ground loop | Add a ferrite snap-on bead to the RJ-45 cable near the interface box. |
| USB noise | Try a shorter USB-C cable. Some cables pick up interference. |
| Missing coupling caps | C9 and C10 block DC. Without them, bias current creates noise. |
| Power supply ripple | Verify C7/C8 near AMS1117. Add a 100µF electrolytic if ripple is high. |
| Serial interference | CP2102 TX/RX lines can couple into audio. Ensure traces are separated on PCB. |
| Phone interference | Some phones emit RF near USB port. Aluminum foil shielding can help. |

**Audio level adjustment:**
- RX too loud → increase R3 from 10kΩ to 22kΩ or 47kΩ
- RX too quiet → decrease R3 to 4.7kΩ
- TX too loud → increase R5 from 10kΩ to 22kΩ or 47kΩ
- TX too quiet → decrease R5 to 4.7kΩ

---

## 6. Hand Microphone Doesn't Work Through Pass-through

**Symptoms:** Radio audio/PTT only works through the app, not through the hand mic plugged into J3.

| Check | Action |
|-------|--------|
| J3 wiring | All 8 RJ-45 pins on J3 must connect directly to corresponding J2 pins. |
| Solder joints | Check all 16 through-hole solder joints on J2 and J3. |
| Cable | Use a straight-through (not crossover) RJ-45 patch cable. |
| Priority | When both hand mic PTT and app PTT are pressed simultaneously, both paths ground pin 4 — this is normal. |

---

## 7. LEDs Don't Work

**Symptoms:** One or more LEDs are always off.

| LED | Check |
|-----|-------|
| All dark | Verify 5V power. If power LED (LED1) is dark, no power reaching the board. |
| LED1 (Power) | Check R7 (330Ω) and LED1 polarity. Cathode mark toward GND. |
| LED2 (PTT) | Only lights when PTT is active. Check R8 and connection to Q1 collector. |
| LED3 (Audio) | Connected to CM108 GPIO. Check R9 and CM108AH GPIO4 configuration. |
| LED4 (Serial) | Connected to CP2102 TX/RX LED output. Check R10. Only blinks during data transfer. |

---

## 8. USB Hub Shows Only 1 Device (Should Show 2)

**Symptoms:** Phone detects either CP2102 OR CM108, but not both.

| Check | Action |
|-------|--------|
| Hub ports | Verify FE1.1s downstream port connections to both ICs. |
| Power | Both CP2102N and CM108AH need sufficient power. Measure VCC at each IC. |
| Solder bridges | Inspect FE1.1s USB data pins (D+/D-) for both downstream ports. |
| Short circuit | A short on one downstream device can prevent the hub from initializing the other. |

---

## 9. Radio Shows Wrong Text / Garbled Data After Upload

**Symptoms:** Channel names are garbage, settings are wrong after uploading from app.

| Check | Action |
|-------|--------|
| Protocol | Ensure the app uses the GA-510 protocol (this is the default for DB20-G). |
| Baud rate | Must be exactly 9600. CP2102N crystal/oscillator issues can cause baud mismatch. |
| Data cable | Poor connections cause bit errors during programming. Try a better cable. |
| Memory range | The app reads/writes 0x0000-0x1C00. Modified ranges will cause issues. |
| Retry | Try downloading first, then re-uploading. Sometimes the first transfer after connecting glitches. |

---

## 10. Interface Box Gets Hot

**Symptoms:** Enclosure or PCB are noticeably warm after a few minutes.

| Check | Action |
|-------|--------|
| Short circuit | Disconnect immediately. Check for solder bridges, especially on power pins. |
| Regulator | AMS1117 normally gets warm (up to 50°C). If burning hot, check for shorts on 3.3V rail. |
| Current draw | Normal idle: ~100mA. During audio: ~150mA. During programming: ~200mA. If >300mA, find the short. |
| Relay coil | K1 relay draws ~75mA when energized. If relay is stuck ON, DTR line may be floating high. |

---

## Getting Help

If these steps don't resolve your issue:

1. Take clear photos of your PCB (both sides) under good lighting
2. Note which test points pass/fail from the [Assembly Guide](ASSEMBLY.md)
3. Include your phone model and Android version
4. Post in the project's GitHub Issues with the above information
