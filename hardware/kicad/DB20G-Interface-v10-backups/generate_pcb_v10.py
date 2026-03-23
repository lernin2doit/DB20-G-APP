#!/usr/bin/env python3
"""
Generate DB20G-Interface-v10.kicad_pcb with all v10 components.

Reads real footprint geometry from the KiCad 9.0 standard library,
assigns nets from the schematic, and places all components OFF the board
(in the workspace area) so the user can manually place them.

Board outline: small rectangular prototyping board.
"""

import os
import re
import uuid
import sys


def uid():
    return str(uuid.uuid4())


# ============================================================
# PATHS
# ============================================================

KICAD_FP_DIR = os.path.join(
    os.environ.get("LOCALAPPDATA", ""),
    "Programs", "KiCad", "9.0", "share", "kicad", "footprints",
)

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_PCB = os.path.join(SCRIPT_DIR, "DB20G-Interface-v10.kicad_pcb")


# ============================================================
# NET DEFINITIONS  (index 0 = unnamed/unconnected)
# ============================================================

NET_NAMES = [
    "",              # 0
    "GND",           # 1
    "+3.3V",         # 2
    "+5V",           # 3
    "+5V_EXT",       # 4
    "ESP_EN",        # 5
    "ESP_ADC0",      # 6
    "ESP_DAC1",      # 7
    "ESP_IO0",       # 8
    "ESP_PTT",       # 9
    "ESP_RX2",       # 10
    "ESP_TX2",       # 11
    "ESP_RELAY",     # 12
    "ESP_LED1",      # 13
    "ESP_LED2",      # 14
    "ESP_LED3",      # 15
    "ESP_LED4",      # 16
    "ESP_TX0",       # 17
    "ESP_RX0",       # 18
    "RJ45_P1",       # 19
    "RADIO_MIC",     # 20
    "PTT_OUT",       # 21
    "RADIO_SQL",     # 22
    "RADIO_SPK",     # 23
    "RELAY_COIL_LO", # 24
    "PTT_BASE",      # 25
    "RELAY_BASE",    # 26
    "AUDIO_TX_DIV",  # 27
    "AUDIO_RX_DIV",  # 28
    "AUDIO_RX",      # 29
    "LED1_R",        # 30
    "LED2_R",        # 31
    "LED3_R",        # 32
    "LED4_R",        # 33
]

NET_IDX = {name: idx for idx, name in enumerate(NET_NAMES)}


# ============================================================
# COMPONENT DEFINITIONS
# Each entry: (ref, value, fp_lib, fp_name, {pad_num: net_name})
# ============================================================

COMPONENTS = [
    # === ICs ===
    ("U1", "ESP32-WROOM-32E", "RF_Module", "ESP32-WROOM-32E", {
        "1": "+3.3V", "2": "ESP_EN", "3": "ESP_ADC0",
        "4": None, "5": None, "6": None, "7": None, "8": None,
        "9": "ESP_DAC1", "10": None, "11": None, "12": None,
        "13": None, "14": "GND", "15": None, "16": None,
        "17": None, "18": None, "19": None, "20": None,
        "21": None, "22": None, "23": None, "24": None,
        "25": "ESP_IO0", "26": "ESP_PTT", "27": "ESP_RX2",
        "28": "ESP_TX2", "29": "ESP_RELAY", "30": "ESP_LED1",
        "31": None, "32": "ESP_LED2", "33": None,
        "34": "ESP_LED3", "35": "ESP_RX0", "36": "ESP_TX0",
        "37": "ESP_LED4", "38": "GND",
        "39": "GND",  # Exposed GND pad on library footprint
    }),
    ("U2", "AMS1117-3.3", "Package_TO_SOT_SMD", "SOT-223-3_TabPin2", {
        "1": "GND", "2": "+3.3V", "3": "+5V",
    }),

    # === Connectors ===
    # RJ-45: pads 1-8 = signal pins, pad "SH" = shield (our schematic pin 9)
    ("J2", "RADIO", "Connector_RJ", "RJ45_Amphenol_RJHSE5380", {
        "1": "RJ45_P1", "2": "RADIO_MIC", "3": "PTT_OUT",
        "4": "RADIO_SQL", "5": "RADIO_SPK",
        "6": "GND", "7": "GND", "8": "GND", "SH": "GND",
    }),
    ("J3", "HANDSET", "Connector_RJ", "RJ45_Amphenol_RJHSE5380", {
        "1": "RJ45_P1", "2": "RADIO_MIC", "3": "PTT_OUT",
        "4": "RADIO_SQL", "5": "RADIO_SPK",
        "6": "GND", "7": "GND", "8": "GND", "SH": "GND",
    }),
    ("J4", "EXT_PWR", "Connector_PinHeader_2.54mm",
     "PinHeader_1x02_P2.54mm_Vertical", {
         "1": "+5V_EXT", "2": "GND",
     }),
    ("J5", "UART_FLASH", "Connector_PinHeader_2.54mm",
     "PinHeader_1x04_P2.54mm_Vertical", {
         "1": "+3.3V", "2": "ESP_TX0", "3": "ESP_RX0", "4": "GND",
     }),

    # === Transistors ===
    # TO-92 physical pinout: pad 1=Emitter, pad 2=Base, pad 3=Collector
    ("Q1", "2N2222A", "Package_TO_SOT_THT", "TO-92_Inline", {
        "1": "GND", "2": "PTT_BASE", "3": "PTT_OUT",
    }),
    ("Q2", "2N2222A", "Package_TO_SOT_THT", "TO-92_Inline", {
        "1": "GND", "2": "RELAY_BASE", "3": "RELAY_COIL_LO",
    }),

    # === Relay ===
    # G5V-2 physical pins: 1=Coil+, 16=Coil-, 4=P1_COM, 6=P1_NC, 8=P1_NO,
    #   9=P2_COM, 11=P2_NC, 13=P2_NO (from Omron datasheet)
    ("K1", "G5V-2-DC5", "Relay_THT", "Relay_DPDT_Omron_G5V-2", {
        "1": "+5V",              # Coil+
        "16": "RELAY_COIL_LO",   # Coil-
        "4": "RADIO_MIC",        # P1_COM
        "8": "ESP_TX2",          # P1_NO (serial mode)
        "6": "AUDIO_TX_DIV",     # P1_NC (audio mode)
        "9": "RADIO_SPK",        # P2_COM
        "13": "ESP_RX2",         # P2_NO (serial mode)
        "11": "AUDIO_RX",        # P2_NC (audio mode)
    }),

    # === Diodes ===
    ("D1", "1N4148", "Diode_SMD", "D_SOD-323", {
        "1": "+5V", "2": "PTT_OUT",
    }),
    ("D2", "1N5819", "Diode_SMD", "D_SOD-323", {
        "1": "+5V", "2": "RELAY_COIL_LO",
    }),

    # === Fuse ===
    ("F1", "500mA", "Fuse", "Fuse_1812_4532Metric", {
        "1": "+5V_EXT", "2": "+5V",
    }),

    # === LEDs ===
    ("LED1", "Green", "LED_SMD", "LED_0805_2012Metric", {
        "1": "GND", "2": "LED1_R",
    }),
    ("LED2", "Red", "LED_SMD", "LED_0805_2012Metric", {
        "1": "PTT_OUT", "2": "LED2_R",
    }),
    ("LED3", "Yellow", "LED_SMD", "LED_0805_2012Metric", {
        "1": "GND", "2": "LED3_R",
    }),
    ("LED4", "Blue", "LED_SMD", "LED_0805_2012Metric", {
        "1": "GND", "2": "LED4_R",
    }),

    # === Resistors ===
    ("R1", "10k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_PTT", "2": "PTT_BASE",
    }),
    ("R2", "10k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_RELAY", "2": "RELAY_BASE",
    }),
    ("R3", "10k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "AUDIO_RX", "2": "AUDIO_RX_DIV",
    }),
    ("R4", "4.7k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "AUDIO_RX_DIV", "2": "GND",
    }),
    ("R5", "10k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_DAC1", "2": "AUDIO_TX_DIV",
    }),
    ("R6", "4.7k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "AUDIO_TX_DIV", "2": "GND",
    }),
    ("R7", "220", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_LED1", "2": "LED1_R",
    }),
    ("R8", "220", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_LED2", "2": "LED2_R",
    }),
    ("R9", "220", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_LED3", "2": "LED3_R",
    }),
    ("R10", "220", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "ESP_LED4", "2": "LED4_R",
    }),
    ("R11", "10k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "+3.3V", "2": "ESP_EN",
    }),
    ("R12", "10k", "Resistor_SMD", "R_0805_2012Metric", {
        "1": "+3.3V", "2": "ESP_IO0",
    }),

    # === Capacitors ===
    ("C1", "100nF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+5V", "2": "GND",
    }),
    ("C2", "10uF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+5V", "2": "GND",
    }),
    ("C3", "100nF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+3.3V", "2": "GND",
    }),
    ("C4", "10uF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+3.3V", "2": "GND",
    }),
    ("C5", "100nF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+3.3V", "2": "GND",
    }),
    ("C6", "22uF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+3.3V", "2": "GND",
    }),
    ("C7", "100nF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "ESP_EN", "2": "GND",
    }),
    ("C8", "100nF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "ESP_ADC0", "2": "GND",
    }),
    ("C9", "100nF", "Capacitor_SMD", "C_0805_2012Metric", {
        "1": "+5V_EXT", "2": "GND",
    }),

    # === Mounting Holes ===
    ("MH1", "M3", "MountingHole", "MountingHole_3.2mm_M3_Pad_Via", {
        "1": "GND",
    }),
    ("MH2", "M3", "MountingHole", "MountingHole_3.2mm_M3_Pad_Via", {
        "1": "GND",
    }),
]


# ============================================================
# FOOTPRINT LIBRARY READER
# ============================================================

def read_footprint_file(fp_lib, fp_name):
    """Read a .kicad_mod file and return its content as a string."""
    path = os.path.join(KICAD_FP_DIR, f"{fp_lib}.pretty", f"{fp_name}.kicad_mod")
    if not os.path.isfile(path):
        print(f"WARNING: Footprint not found: {path}", file=sys.stderr)
        return None
    with open(path, "r", encoding="utf-8") as f:
        return f.read()


# Cache to avoid re-reading the same footprint file
_fp_cache = {}

def get_footprint_template(fp_lib, fp_name):
    """Get footprint file content, cached."""
    key = (fp_lib, fp_name)
    if key not in _fp_cache:
        _fp_cache[key] = read_footprint_file(fp_lib, fp_name)
    return _fp_cache[key]


def extract_pad_numbers(fp_content):
    """Extract all pad numbers/names from a footprint definition."""
    # Match (pad "N" ...) or (pad N ...)
    pads = re.findall(r'\(pad\s+"?([^"\s)]+)"?\s', fp_content)
    return pads


def transform_footprint(fp_content, ref, value, fp_lib, fp_name,
                         x_mm, y_mm, pad_nets):
    """
    Transform a library .kicad_mod footprint into a PCB footprint block.

    - Strips the outer (footprint ...) wrapper and rebuilds it for PCB format
    - Adds position (at x y)
    - Replaces REF** and VAL** with actual ref/value
    - Injects (net ...) into each (pad ...) block
    - Adds unique UUIDs
    """
    fp_lib_id = f"{fp_lib}:{fp_name}"

    # The .kicad_mod file starts with (footprint "Name" ...)
    # We need to change it to pcb format with layer, at, etc.

    # Read the inner content of the footprint (everything between outer parens)
    # Strip leading (footprint "name" and trailing )
    content = fp_content.strip()

    # Remove outer (footprint "..." and the final )
    # Find the first newline after the footprint declaration
    first_nl = content.index("\n")
    inner = content[first_nl:].rstrip()
    if inner.endswith(")"):
        inner = inner[:-1]

    # Replace reference text
    inner = re.sub(
        r'(\(property\s+"Reference"\s+)"[^"]*"',
        rf'\1"{ref}"',
        inner,
    )
    # Replace value text
    inner = re.sub(
        r'(\(property\s+"Value"\s+)"[^"]*"',
        rf'\1"{value}"',
        inner,
    )
    # Replace Footprint property
    inner = re.sub(
        r'(\(property\s+"Footprint"\s+)"[^"]*"',
        rf'\1"{fp_lib_id}"',
        inner,
    )

    # Inject net info into pads
    def pad_replacer(match):
        full_pad = match.group(0)
        pad_num = match.group(1)

        net_name = pad_nets.get(pad_num)
        if net_name is None:
            # Unconnected pad — no net injection needed, use net 0
            net_idx = 0
            net_str = '""'
        else:
            net_idx = NET_IDX.get(net_name, 0)
            net_str = f'"{net_name}"'

        # Check if this pad already has a (net ...) declaration
        if "(net " in full_pad:
            # Replace existing net
            full_pad = re.sub(
                r'\(net\s+\d+\s+"[^"]*"\)',
                f'(net {net_idx} {net_str})',
                full_pad,
            )
        else:
            # Insert net before the closing paren of the pad
            # Find the last ) in the pad block
            last_paren = full_pad.rindex(")")
            full_pad = (
                full_pad[:last_paren]
                + f"\n\t\t\t(net {net_idx} {net_str})\n\t\t)"
            )

        return full_pad

    # Match entire (pad ...) blocks (handling nested parens)
    def replace_pads(text):
        result = []
        i = 0
        while i < len(text):
            # Look for (pad
            pad_start = text.find("(pad ", i)
            if pad_start == -1:
                result.append(text[i:])
                break

            result.append(text[i:pad_start])

            # Find matching closing paren
            depth = 0
            j = pad_start
            while j < len(text):
                if text[j] == "(":
                    depth += 1
                elif text[j] == ")":
                    depth -= 1
                    if depth == 0:
                        break
                j += 1

            pad_block = text[pad_start:j + 1]

            # Extract pad number
            pad_match = re.match(r'\(pad\s+"?([^"\s)]+)"?', pad_block)
            if pad_match:
                pad_num = pad_match.group(1)
                net_name = pad_nets.get(pad_num)

                if net_name is None:
                    net_idx = 0
                    net_str = '""'
                else:
                    net_idx = NET_IDX.get(net_name, 0)
                    net_str = f'"{net_name}"'

                # Insert net before the closing paren
                pad_block = (
                    pad_block[:-1]
                    + f"\n\t\t\t(net {net_idx} {net_str})\n\t\t)"
                )

            result.append(pad_block)
            i = j + 1

        return "".join(result)

    inner = replace_pads(inner)

    # Replace all UUIDs with fresh ones
    inner = re.sub(
        r'\(uuid\s+"?[0-9a-f-]+"?\)',
        lambda m: f'(uuid "{uid()}")',
        inner,
    )

    # Build the PCB footprint block
    fp_uuid = uid()
    header = (
        f'\t(footprint "{fp_lib_id}"\n'
        f'\t\t(layer "F.Cu")\n'
        f'\t\t(uuid "{fp_uuid}")\n'
        f'\t\t(at {x_mm} {y_mm})'
    )

    return header + inner + "\n\t)"


# ============================================================
# BOARD OUTLINE
# ============================================================

def make_board_outline(x, y, w, h):
    """Create a rectangular board outline on Edge.Cuts layer.
    (x,y) = top-left corner, w = width, h = height, all in mm.
    """
    corners = [
        (x, y), (x + w, y), (x + w, y + h), (x, y + h),
    ]
    lines = []
    for i in range(4):
        x1, y1 = corners[i]
        x2, y2 = corners[(i + 1) % 4]
        lines.append(
            f'\t(gr_line\n'
            f'\t\t(start {x1} {y1})\n'
            f'\t\t(end {x2} {y2})\n'
            f'\t\t(stroke\n'
            f'\t\t\t(width 0.05)\n'
            f'\t\t\t(type default)\n'
            f'\t\t)\n'
            f'\t\t(layer "Edge.Cuts")\n'
            f'\t\t(uuid "{uid()}")\n'
            f'\t)'
        )
    return "\n".join(lines)


# ============================================================
# PCB FILE GENERATION
# ============================================================

def generate_pcb():
    """Generate the complete PCB file."""

    # --- Board dimensions ---
    # Small prototyping board: 65mm x 80mm
    # Origin at (100, 80) — board placed in middle of workspace
    board_x, board_y = 100, 80
    board_w, board_h = 65, 80

    # --- Component placement ---
    # All components go to the RIGHT of the board, off-board in workspace
    # Spread them out so they're easy to pick up
    workspace_x_start = board_x + board_w + 20   # 20mm gap from board edge
    workspace_y_start = board_y

    # Calculate positions for each component (grid layout in workspace)
    positions = []
    col_spacing = 30  # mm between columns
    row_spacing = 20  # mm between rows
    cols = 5
    for i, comp in enumerate(COMPONENTS):
        col = i % cols
        row = i // cols
        x = workspace_x_start + col * col_spacing
        y = workspace_y_start + row * row_spacing
        positions.append((x, y))

    # --- Build net declarations ---
    net_lines = []
    for idx, name in enumerate(NET_NAMES):
        net_lines.append(f'\t(net {idx} "{name}")')

    # --- Build footprint blocks ---
    fp_blocks = []
    for i, comp in enumerate(COMPONENTS):
        ref, value, fp_lib, fp_name, pad_nets = comp
        x, y = positions[i]

        fp_template = get_footprint_template(fp_lib, fp_name)
        if fp_template is None:
            print(f"ERROR: Cannot read footprint for {ref} ({fp_lib}:{fp_name})",
                  file=sys.stderr)
            continue

        fp_block = transform_footprint(
            fp_template, ref, value, fp_lib, fp_name, x, y, pad_nets
        )
        fp_blocks.append(fp_block)
        print(f"  {ref:6s} -> {fp_lib}:{fp_name} at ({x}, {y})")

    # --- Board outline ---
    outline = make_board_outline(board_x, board_y, board_w, board_h)

    # --- Assemble the PCB file ---
    pcb = f"""\
(kicad_pcb
\t(version 20241229)
\t(generator "generate_pcb_v10.py")
\t(generator_version "1.0")
\t(general
\t\t(thickness 1.6)
\t\t(legacy_teardrops no)
\t)
\t(paper "A4")
\t(layers
\t\t(0 "F.Cu" signal)
\t\t(31 "B.Cu" signal)
\t\t(32 "B.Adhes" user "B.Adhesive")
\t\t(33 "F.Adhes" user "F.Adhesive")
\t\t(34 "B.Paste" user)
\t\t(35 "F.Paste" user)
\t\t(36 "B.SilkS" user "B.Silkscreen")
\t\t(37 "F.SilkS" user "F.Silkscreen")
\t\t(38 "B.Mask" user "B.Mask")
\t\t(39 "F.Mask" user "F.Mask")
\t\t(40 "Dwgs.User" user "User.Drawings")
\t\t(41 "Cmts.User" user "User.Comments")
\t\t(42 "Eco1.User" user "User.Eco1")
\t\t(43 "Eco2.User" user "User.Eco2")
\t\t(44 "Edge.Cuts" user)
\t\t(45 "Margin" user)
\t\t(46 "B.CrtYd" user "B.Courtyard")
\t\t(47 "F.CrtYd" user "F.Courtyard")
\t\t(48 "B.Fab" user "B.Fab")
\t\t(49 "F.Fab" user "F.Fab")
\t\t(50 "User.1" user)
\t\t(51 "User.2" user)
\t\t(52 "User.3" user)
\t\t(53 "User.4" user)
\t\t(54 "User.5" user)
\t\t(55 "User.6" user)
\t\t(56 "User.7" user)
\t\t(57 "User.8" user)
\t\t(58 "User.9" user)
\t)
\t(setup
\t\t(pad_to_mask_clearance 0)
\t\t(allow_soldermask_bridges_in_footprints no)
\t\t(min_clearance 0.15)
\t\t(min_trace_width 0.15)
\t\t(pcbplotparams
\t\t\t(layerselection 0x00010fc_ffffffff)
\t\t\t(plot_on_all_layers_selection 0x0000000_00000000)
\t\t\t(disableapertmacros no)
\t\t\t(usegerberextensions no)
\t\t\t(usegerberattributes yes)
\t\t\t(usegerberadvancedattributes yes)
\t\t\t(creategerberjobfile yes)
\t\t\t(dashed_line_dash_ratio 12.000000)
\t\t\t(dashed_line_gap_ratio 3.000000)
\t\t\t(svgprecision 4)
\t\t\t(plotframeref no)
\t\t\t(viasonmask no)
\t\t\t(mode 1)
\t\t\t(useauxorigin no)
\t\t\t(hpglpennumber 1)
\t\t\t(hpglpenspeed 20)
\t\t\t(hpglpendiameter 15.000000)
\t\t\t(pdf_front_fp_property_popups yes)
\t\t\t(pdf_back_fp_property_popups yes)
\t\t\t(pdf_metadata yes)
\t\t\t(excludeedgelayer yes)
\t\t\t(linewidth 0.100000)
\t\t\t(plotinvisibletext no)
\t\t\t(sketchpadsonfab no)
\t\t\t(subtractmaskfromsilk no)
\t\t\t(outputformat 1)
\t\t\t(mirror no)
\t\t\t(drillshape 1)
\t\t\t(scaleselection 1)
\t\t\t(outputdirectory "")
\t\t)
\t)
{chr(10).join(net_lines)}
{outline}
{chr(10).join(fp_blocks)}
\t(embedded_fonts no)
)
"""
    with open(OUTPUT_PCB, "w", encoding="utf-8", newline="\n") as f:
        f.write(pcb)

    print(f"\nPCB written to: {OUTPUT_PCB}")
    print(f"  Board outline: {board_w}mm x {board_h}mm at ({board_x}, {board_y})")
    print(f"  Components: {len(fp_blocks)} footprints placed in workspace")
    print(f"  Nets: {len(NET_NAMES)} nets declared")


if __name__ == "__main__":
    print("Generating DB20G-Interface-v10.kicad_pcb ...")
    print(f"Reading footprints from: {KICAD_FP_DIR}")
    print()
    generate_pcb()
    print("\nDone! Open in KiCad and drag components onto the board.")
