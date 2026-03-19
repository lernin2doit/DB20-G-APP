# DB20-G Interface Box — Bill of Materials

Complete parts list for building the interface box. All components are commonly available from major distributors.

## Core ICs

| Ref  | Part               | Package    | Value/Spec                    | Qty | Est. Cost | Source                          |
|------|--------------------|-----------|-------------------------------|-----|-----------|----------------------------------|
| U1   | FE1.1s             | SSOP-28   | USB 2.0 4-port hub controller | 1   | $0.50     | LCSC C2688765 / AliExpress       |
| U2   | CP2102N-A01-GQFN28 | QFN-28    | USB-to-UART bridge, 3.3V     | 1   | $1.80     | DigiKey 336-3000-ND / LCSC       |
| U3   | CM108AH            | SSOP-28   | USB audio codec, 16-bit 48kHz | 1   | $0.80     | LCSC C7509 / AliExpress          |
| U4   | AMS1117-3.3        | SOT-223   | 3.3V LDO regulator, 1A       | 1   | $0.10     | LCSC C6186 / DigiKey             |

## Connectors

| Ref  | Part                     | Type        | Spec                           | Qty | Est. Cost | Source                    |
|------|--------------------------|-------------|--------------------------------|-----|-----------|---------------------------|
| J1   | USB-C Receptacle         | SMD mid-mount| USB 2.0, 16-pin              | 1   | $0.30     | LCSC C168688               |
| J2   | RJ-45 Jack               | Through-hole | 8P8C, shielded               | 1   | $0.40     | LCSC C386756               |
| J3   | RJ-45 Jack               | Through-hole | 8P8C, shielded (pass-through)| 1   | $0.40     | LCSC C386756               |
| J4   | 3.5mm TRS Audio Jack     | Through-hole | 3-conductor, panel mount     | 1   | $0.25     | LCSC C145822               |
| J5   | 2.54mm Pin Header 2×5    | Through-hole | Optional debug/expansion     | 1   | $0.10     | Any supplier               |

## Semiconductors

| Ref  | Part        | Package  | Spec                           | Qty | Est. Cost | Source               |
|------|-------------|---------|--------------------------------|-----|-----------|----------------------|
| Q1   | 2N2222A     | TO-92   | NPN transistor, PTT driver     | 1   | $0.05     | LCSC / DigiKey        |
| Q2   | 2N2222A     | TO-92   | NPN transistor, relay driver   | 1   | $0.05     | LCSC / DigiKey        |
| D1   | 1N4148      | DO-35   | Switching diode, relay flyback | 1   | $0.02     | LCSC C14516           |
| D2   | 1N5819      | DO-41   | Schottky diode, reverse prot.  | 1   | $0.03     | LCSC C2480            |

## Passive Components — Resistors (0805 SMD)

| Ref   | Value | Purpose                              | Qty | Est. Cost |
|-------|-------|--------------------------------------|-----|-----------|
| R1    | 10kΩ  | PTT base resistor (Q1)               | 1   | $0.01     |
| R2    | 10kΩ  | Relay driver base resistor (Q2)      | 1   | $0.01     |
| R3    | 10kΩ  | Audio attenuator input (SPK→CM108)   | 1   | $0.01     |
| R4    | 1kΩ   | Audio attenuator output (SPK→CM108)  | 1   | $0.01     |
| R5    | 10kΩ  | Audio attenuator input (CM108→MIC)   | 1   | $0.01     |
| R6    | 1kΩ   | Audio attenuator output (CM108→MIC)  | 1   | $0.01     |
| R7    | 330Ω  | LED1 current limit (Power, green)    | 1   | $0.01     |
| R8    | 330Ω  | LED2 current limit (PTT, red)        | 1   | $0.01     |
| R9    | 330Ω  | LED3 current limit (Audio, yellow)   | 1   | $0.01     |
| R10   | 330Ω  | LED4 current limit (Serial, blue)    | 1   | $0.01     |
| R11   | 4.7kΩ | USB D+ pull-up (FE1.1s)              | 1   | $0.01     |
| R12   | 1.5kΩ | USB D+ pull-up (hub upstream)        | 1   | $0.01     |
| R13   | 5.1kΩ | USB-C CC1 pull-down (device ID)      | 1   | $0.01     |
| R14   | 5.1kΩ | USB-C CC2 pull-down (device ID)      | 1   | $0.01     |
| R15   | 10kΩ  | CP2102N CTS pull-up to 3.3V          | 1   | $0.01     |

## Passive Components — Capacitors (0805 SMD unless noted)

| Ref   | Value  | Type       | Purpose                         | Qty | Est. Cost |
|-------|--------|-----------|----------------------------------|-----|-----------|
| C1    | 100nF  | MLCC      | FE1.1s bypass                    | 1   | $0.01     |
| C2    | 10µF   | MLCC      | FE1.1s bulk decoupling           | 1   | $0.02     |
| C3    | 100nF  | MLCC      | CP2102N bypass                   | 1   | $0.01     |
| C4    | 10µF   | MLCC      | CP2102N bulk decoupling          | 1   | $0.02     |
| C5    | 100nF  | MLCC      | CM108AH bypass                   | 1   | $0.01     |
| C6    | 10µF   | MLCC      | CM108AH bulk decoupling          | 1   | $0.02     |
| C7    | 10µF   | MLCC      | AMS1117 input                    | 1   | $0.02     |
| C8    | 22µF   | MLCC      | AMS1117 output (ESR critical)    | 1   | $0.03     |
| C9    | 1µF    | MLCC      | Audio coupling, SPK→CM108        | 1   | $0.01     |
| C10   | 1µF    | MLCC      | Audio coupling, CM108→MIC        | 1   | $0.01     |
| C11   | 22pF   | MLCC      | Crystal load cap 1               | 1   | $0.01     |
| C12   | 22pF   | MLCC      | Crystal load cap 2               | 1   | $0.01     |
| C13   | 4.7µF  | MLCC      | CM108AH VREF decoupling            | 1   | $0.02     |
| C14   | 100nF  | MLCC      | FE1.1s VDD18 bypass                 | 1   | $0.01     |

## Other Components

| Ref   | Part               | Package/Type | Spec                              | Qty | Est. Cost | Source           |
|-------|--------------------|-------------|-----------------------------------|-----|-----------|------------------|
| Y1    | 12MHz Crystal      | HC-49S      | 20ppm, 18pF load                  | 1   | $0.15     | LCSC C32180       |
| K1    | G5V-1-DC5          | Through-hole| 5V SPDT relay, 1A contacts        | 1   | $0.80     | LCSC C100024      |
| LED1  | Green LED          | 0805        | Power indicator                   | 1   | $0.02     | LCSC C2297        |
| LED2  | Red LED            | 0805        | PTT active indicator              | 1   | $0.02     | LCSC C84256       |
| LED3  | Yellow LED         | 0805        | Audio activity indicator           | 1   | $0.02     | LCSC C2296        |
| LED4  | Blue LED           | 0805        | Serial TX/RX indicator            | 1   | $0.02     | LCSC C72041       |
| F1    | Polyfuse 500mA     | 1206        | USB overcurrent protection        | 1   | $0.05     | LCSC C70069       |

## Cables (not on PCB)

| Item                        | Spec                                       | Est. Cost | Source          |
|-----------------------------|-------------------------------------------|-----------|-----------------|
| USB-C to USB-C cable        | USB 2.0, 1m, for phone connection         | $3.00     | Amazon / LCSC    |
| RJ-45 patch cable           | Cat5e, 0.5m, for handset port connection  | $1.00     | Amazon           |
| 3.5mm TRS cable             | 2.5mm or 3.5mm Kenwood-style prog cable   | $5.00     | Amazon / BaoFeng |

## Cost Summary

| Category        | Estimated Total |
|-----------------|----------------|
| ICs             | $3.20          |
| Connectors      | $1.45          |
| Semiconductors  | $0.15          |
| Resistors (×15) | $0.15          |
| Capacitors (×14)| $0.21          |
| Other           | $1.06          |
| **PCB Total**   | **~$6.16**     |
| PCB fabrication | $2-5 (5 pcs)  |
| Cables          | $9.00          |
| **Grand Total** | **~$17-20**    |

## Ordering Notes

1. **LCSC** — Most parts available. Use LCSC part numbers for JLCPCB assembly service.
2. **JLCPCB** — Upload Gerber files from the `kicad/` directory. 5 PCBs for ~$2 + shipping.
3. **PCBWay** — Alternative PCB fab. Same Gerber files work.
4. **SMD assembly** — JLCPCB offers SMT assembly. Upload BOM.csv + pick-and-place file.
5. **Through-hole parts** (Q1, Q2, K1, J2, J3, J4, Y1) must be hand-soldered.

## Audio Level Notes

The DB20-G speaker output is approximately 1-2V peak-to-peak. The CM108AH MIC_IN expects ~40mV peak. The 10:1 resistive divider (R3/R4) attenuates to the correct range. Adjust R3 if audio is too loud/quiet on receive.

The CM108AH SPK_OUT is approximately 1V peak-to-peak. The DB20-G microphone input expects ~20-50mV. The 10:1 divider (R5/R6) attenuates to the correct range. Adjust R5 if transmitted audio is too loud/quiet.
