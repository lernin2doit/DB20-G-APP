# KiCad Schematic Changes — J4 Removal & Serial-Through-RJ45

> **Status**: Manual changes required in KiCad schematic editor  
> **Schematic file**: `hardware/kicad/DB20G-Interface.kicad_sch`

## Background

The DB20-G does **not** have a rear 3.5mm data port. The front-panel RJ-45 handset
jack **is** the programming/data port. J4 (3.5mm TRS "DATA" connector) must be
removed and the CP2102N serial path rerouted through the RJ-45 (J2) via relay
switching.

## Changes Required

### 1. Remove J4 Symbol

- **Component**: J4, value "DATA", lib_id "DB20G:RJ45" (reused as TRS)
- **Footprint**: `Connector_Audio:Jack_3.5mm_CUI_SJ1-3533NG_Horizontal`
- **Action**: Delete the J4 symbol and all wires connected to it
- **Nets to disconnect**: CP2102N TXD → J4 pin (serial TX), CP2102N RXD → J4 pin (serial RX), GND

### 2. Reroute CP2102N Through K1 Relay (DPDT)

The K1 relay (G5V-1-DC5, DPDT) switches the RJ-45 MIC and SPK pins between
audio (CM108AH) and serial (CP2102N) modes.

**Relay wiring (DPDT has two independent poles):**

| Relay Terminal | Connection |
|---|---|
| **Pole 1 COM** → | J2 Pin 2 (MIC/TX line) |
| Pole 1 NC | CM108AH audio output (microphone TX path) |
| Pole 1 NO | CP2102N TXD |
| **Pole 2 COM** → | J2 Pin 5 (SPK/RX line) |
| Pole 2 NC | CM108AH audio input (speaker RX path) |
| Pole 2 NO | CP2102N RXD |
| Coil+ | +5V via Q2 switching transistor (DTR-controlled) |
| Coil− | GND |

**Mode logic:**
- **DTR LOW** (relay de-energized, NC contacts) → Audio mode: CM108AH ↔ MIC/SPK
- **DTR HIGH** (relay energized, NO contacts) → Serial mode: CP2102N ↔ MIC/SPK

### 3. DTR Relay Control

Connect the CP2102N DTR output through Q2 (NPN transistor) to drive the K1 relay coil:

```
CP2102N DTR → R? (1kΩ) → Q2 Base
Q2 Collector → K1 Coil+
Q2 Emitter → GND
K1 Coil− → GND
+5V → K1 Coil+ (through relay)
D? (1N4148) flyback diode across K1 coil (cathode to +5V)
```

> **Note**: If Q2 and the flyback diode are already present from the previous
> relay-backup-PTT circuit, reuse them — just reroute the connections.

### 4. Update PCB Layout

After schematic changes:
1. Remove J4 footprint from PCB
2. Remove J4 cutout from board edge (if present)
3. Route new relay traces
4. Run DRC to verify no unconnected nets

### 5. Net Summary

| Net | Old Route | New Route |
|---|---|---|
| CP2102N TXD | → J4 (3.5mm tip) | → K1 Pole 1 NO |
| CP2102N RXD | → J4 (3.5mm ring) | → K1 Pole 2 NO |
| CM108AH MIC out | → J2 Pin 2 (direct) | → K1 Pole 1 NC |
| CM108AH SPK in | → J2 Pin 5 (direct) | → K1 Pole 2 NC |
| K1 Pole 1 COM | (new) | → J2 Pin 2 |
| K1 Pole 2 COM | (new) | → J2 Pin 5 |
| DTR relay ctrl | (repurposed) | → Q2 base → K1 coil |
