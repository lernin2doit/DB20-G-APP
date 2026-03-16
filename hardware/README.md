# DB20-G Interface Box — Hardware Design

Custom USB interface box that connects an Android phone to a Radioddity DB20-G GMRS mobile radio, providing serial programming control, USB audio I/O, and PTT keying through a single USB-C cable.

## What This Does

```
┌──────────┐    USB-C     ┌──────────────────────────┐    RJ-45 + Data    ┌──────────┐
│  Android  │◄───────────►│   DB20-G Interface Box    │◄────────────────►│  DB20-G   │
│   Phone   │  (single    │                           │  (handset port   │   Radio   │
│           │   cable)    │  CP2102N  → Serial Data   │   + data port)   │           │
│           │             │  CM108AH  → Audio I/O     │                  │           │
│           │             │  PTT Ckt  → Transmit Key  │                  │           │
└──────────┘              └──────────────────────────┘                   └──────────┘
```

## Features

- **USB-C to phone** — single cable carries serial + audio + power
- **FE1.1s USB hub** — splits USB into CP2102N (serial) + CM108AH (audio)
- **CP2102N USB-UART** — 9600 baud 8N1 for GA-510 programming protocol
- **CM108AH USB audio codec** — line-level audio in/out for radio voice
- **Transistor PTT driver** — keys radio via RTS/DTR serial control line
- **Relay backup PTT** — hardware relay for reliable PTT in noisy conditions
- **Audio attenuators** — matches CM108 line levels to radio mic/speaker levels
- **RJ-45 handset pass-through** — use the hand mic normally while interface is connected
- **LED indicators** — power, PTT active, audio activity, serial TX/RX
- **3.5mm data plug** — connects to DB20-G rear data/programming port

## Directory Structure

```
hardware/
├── README.md                           ← You are here
├── BOM.md                              ← Bill of materials with sourcing links
├── BOM.csv                             ← Machine-readable BOM for ordering
├── ASSEMBLY.md                         ← Step-by-step build guide
├── WIRING.md                           ← Pin mappings and hookup reference
├── TROUBLESHOOTING.md                  ← Common issues and solutions
├── kicad/
│   ├── DB20G-Interface.kicad_pro       ← KiCad 8 project file
│   ├── DB20G-Interface.kicad_sch       ← Schematic
│   └── DB20G-Interface.kicad_pcb       ← PCB layout
└── enclosure/
    └── DB20G-Enclosure.scad            ← OpenSCAD parametric enclosure (→ export STL)
```

## Circuit Overview

### Block Diagram

```
                            ┌─────────────────────────────────────────────────┐
                            │           DB20-G Interface Box                  │
                            │                                                 │
USB-C ──►┌─────────┐       │    ┌──────────┐   TX ────────► Data Port Pin 2  │
(Phone)  │ FE1.1s  │ Port1 │───►│ CP2102N  │   RX ◄──────── Data Port Pin 3  │
         │ USB Hub │       │    │ USB-UART │   RTS ──►[PTT Driver]──► PTT    │
         │         │ Port2 │    └──────────┘                                  │
         │         │───────│───►┌──────────┐   SPK_OUT ──►[Atten]──► MIC In  │
         └─────────┘       │    │ CM108AH  │   MIC_IN  ◄──[Atten]◄── SPK Out │
                            │    │ USB Audio│                                  │
              5V ──────────│───►│          │                                  │
              │            │    └──────────┘                                  │
              ▼            │                                                  │
         ┌─────────┐       │    ┌──────────┐                                  │
         │AMS1117  │ 3.3V ─┤───►│  LEDs ×4 │ PWR / PTT / AUDIO / SERIAL     │
         │ 3.3V    │       │    └──────────┘                                  │
         └─────────┘       │                                                  │
                            │    ┌──────────┐                                  │
                            │    │  Relay   │──► PTT (backup hardware key)    │
                            │    │  G5V-1   │                                  │
                            │    └──────────┘                                  │
                            │                                                  │
                            │    RJ-45 IN ◄──────────────► RJ-45 OUT          │
                            │    (Radio)      Pass-through   (Handset)        │
                            └─────────────────────────────────────────────────┘
```

### Radio Connections

**Data Port (rear of DB20-G) — 3.5mm TRS plug:**
| Pin     | Signal       | Direction     |
|---------|-------------|---------------|
| Tip     | TX Data     | Phone → Radio |
| Ring    | RX Data     | Radio → Phone |
| Sleeve  | Ground      | Common        |

**Handset Port (front of DB20-G) — RJ-45:**
| RJ-45 Pin | Signal          | Interface Box Use        |
|-----------|-----------------|--------------------------|
| 1         | +V Supply       | Pass-through             |
| 2         | Microphone      | CM108 SPK_OUT → Atten    |
| 3         | Ground          | Common ground            |
| 4         | PTT (active low)| Transistor/Relay driver  |
| 5         | Speaker+        | Atten → CM108 MIC_IN     |
| 6         | Speaker-        | Pass-through             |
| 7         | UP Button       | Pass-through             |
| 8         | DOWN Button     | Pass-through             |

## Quick Start

1. Order components from [BOM.md](BOM.md)
2. Get PCBs fabricated — upload `kicad/` Gerber exports to JLCPCB or PCBWay
3. 3D-print the enclosure — export STL from `enclosure/DB20G-Enclosure.scad`
4. Follow [ASSEMBLY.md](ASSEMBLY.md) step-by-step
5. Wire to radio per [WIRING.md](WIRING.md)
6. If issues, check [TROUBLESHOOTING.md](TROUBLESHOOTING.md)
7. Use the in-app **Hardware Guide** (Tools tab) for interactive checklist

## Design Decisions

- **FE1.1s hub** chosen for availability, low cost ($0.50), and no external crystal requirement on some variants. We use a 12MHz crystal for stability.
- **CP2102N** over CH340 because Android has better driver support via usb-serial-for-android, and the CP2102N has configurable GPIO for future expansion.
- **CM108AH** over CM119 for lower cost and simpler routing. Line-in works fine for the DB20-G speaker output level.
- **2N2222A transistor PTT** for zero-latency keying from RTS/DTR serial control lines. The relay provides a backup path for rigs where the transistor PTT doesn't sink enough current.
- **RJ-45 pass-through** so the hand mic works normally — critical for vehicle installations where you might want to grab the mic.

## License

Hardware designs are released under CERN Open Hardware License v2 — Permissive (CERN-OHL-P-2.0).
