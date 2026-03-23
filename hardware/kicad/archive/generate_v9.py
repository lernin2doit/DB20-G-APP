#!/usr/bin/env python3
"""
Generate a fresh KiCad 9.0 schematic for DB20G-Interface v9.

This script produces a clean .kicad_sch file containing ONLY:
  - lib_symbols (component library definitions)
  - Component instances (unplaced, off-board)
  - NO wires, NO labels, NO junctions, NO no_connects

All components are placed in a grid pattern off the drawing area
so the designer can use KiCad's tools to place and wire them.

Design changes from previous version:
  - J4 (3.5mm DATA) REMOVED entirely
  - K1 changed from SPDT (G5V-1-DC5) to DPDT (G5V-2-DC5) 
  - J2 changed from AudioJack3 to RJ45 (matches its physical footprint)
  - Serial path now routes through DPDT relay to RJ-45
"""

import uuid
import textwrap

def make_uuid():
    return str(uuid.uuid4())


# ============================================================
# SYMBOL LIBRARY DEFINITIONS
# Each symbol defines the graphical representation and pins.
# These are embedded in the schematic's lib_symbols section.
# ============================================================

def sym_2N2222A():
    """NPN transistor - TO-92 - 3 pins: B(1), C(2), E(3)"""
    return textwrap.dedent("""\
    (symbol "DB20G:2N2222A"
      (pin_names
        (offset 0) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "Q"
        (at 5.08 1.905 0)
        (effects (font (size 1.27 1.27)) (justify left))
      )
      (property "Value" "2N2222A"
        (at 5.08 -1.905 0)
        (effects (font (size 1.27 1.27)) (justify left))
      )
      (property "Footprint" "Package_TO_SOT_THT:TO-92_Inline"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "NPN transistor, TO-92"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "2N2222A_0_1"
        (polyline
          (pts (xy 0.635 0.635) (xy 2.54 2.54))
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 0.635 -0.635) (xy 2.54 -2.54))
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 0.635 1.905) (xy 0.635 -1.905))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 1.27 -1.27) (xy 2.286 -2.286))
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 2.286 -2.286) (xy 1.778 -1.524))
          (stroke (width 0) (type default))
          (fill (type outline))
        )
        (circle
          (center 1.27 0)
          (radius 2.8194)
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
      )
      (symbol "2N2222A_1_1"
        (pin passive line (at 0 0 0) (length 0.635) (name "B" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 2.54 2.54 270) (length 0.635) (name "C" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 2.54 -2.54 90) (length 0.635) (name "E" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_AMS1117():
    """AMS1117-3.3 voltage regulator - SOT-223 - 4 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:AMS1117-3.3"
      (pin_names
        (offset 0.254))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "U"
        (at 0 6.35 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "AMS1117-3.3"
        (at 0 3.81 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Package_TO_SOT_SMD:SOT-223-3_TabPin2"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "1A LDO Voltage Regulator, 3.3V, SOT-223"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "AMS1117-3.3_0_1"
        (rectangle
          (start -5.08 2.54)
          (end 5.08 -5.08)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "AMS1117-3.3_1_1"
        (pin input line (at -7.62 0 0) (length 2.54) (name "GND/Adj" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin input line (at 7.62 0 180) (length 2.54) (name "VO" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin input line (at -7.62 -2.54 0) (length 2.54) (name "VI" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_C():
    """Capacitor - 0805 - 2 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:C"
      (pin_names
        (offset 0.254) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "C"
        (at 0.635 2.54 0)
        (effects (font (size 1.27 1.27)) (justify left))
      )
      (property "Value" "C"
        (at 0.635 -2.54 0)
        (effects (font (size 1.27 1.27)) (justify left))
      )
      (property "Footprint" "Capacitor_SMD:C_0805_2012Metric"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Unpolarized capacitor, 0805"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "C_0_1"
        (polyline
          (pts (xy -2.032 -0.762) (xy 2.032 -0.762))
          (stroke (width 0.508) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy -2.032 0.762) (xy 2.032 0.762))
          (stroke (width 0.508) (type default))
          (fill (type none))
        )
      )
      (symbol "C_1_1"
        (pin passive line (at 0 3.81 270) (length 2.794) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 0 -3.81 90) (length 2.794) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_R():
    """Resistor - 0805 - 2 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:R"
      (pin_names
        (offset 0) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "R"
        (at 2.032 0 90)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "R"
        (at -1.524 0 90)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Resistor_SMD:R_0805_2012Metric"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Resistor, 0805"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "R_0_1"
        (rectangle
          (start -1.016 -2.54)
          (end 1.016 2.54)
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
      )
      (symbol "R_1_1"
        (pin passive line (at 0 3.81 270) (length 1.27) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 0 -3.81 90) (length 1.27) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_LED():
    """LED - 0805 - 2 pins: K(1) A(2)"""
    return textwrap.dedent("""\
    (symbol "DB20G:LED"
      (pin_names
        (offset 1.016) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "LED"
        (at 1.524 2.54 0)
        (effects (font (size 1.27 1.27)) (justify left))
      )
      (property "Value" "LED"
        (at 1.524 -2.54 0)
        (effects (font (size 1.27 1.27)) (justify left))
      )
      (property "Footprint" "LED_SMD:LED_0805_2012Metric"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Light emitting diode, 0805"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "LED_0_1"
        (polyline
          (pts (xy -1.27 -1.27) (xy -1.27 1.27))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy -1.27 0) (xy 1.27 0))
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 1.27 -1.27) (xy 1.27 1.27) (xy -1.27 0) (xy 1.27 -1.27))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
      )
      (symbol "LED_1_1"
        (pin passive line (at -3.81 0 0) (length 2.54) (name "K" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 3.81 0 180) (length 2.54) (name "A" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_D():
    """Diode - 2 pins: K(1) A(2)"""
    return textwrap.dedent("""\
    (symbol "DB20G:D"
      (pin_names
        (offset 1.016) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "D"
        (at 0 2.54 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "D"
        (at 0 -2.54 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Diode_SMD:D_SOD-323"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Diode"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "D_0_1"
        (polyline
          (pts (xy -1.27 1.27) (xy -1.27 -1.27))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 1.27 -1.27) (xy 1.27 1.27) (xy -1.27 0) (xy 1.27 -1.27))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
      )
      (symbol "D_1_1"
        (pin passive line (at -3.81 0 0) (length 2.54) (name "K" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 3.81 0 180) (length 2.54) (name "A" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_Crystal():
    """Crystal - 2 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:Crystal"
      (pin_names
        (offset 1.016) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "Y"
        (at 0 3.81 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "Crystal"
        (at 0 -3.81 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Crystal:Crystal_HC49-4H_Vertical"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Crystal oscillator"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "Crystal_0_1"
        (rectangle
          (start -0.762 -1.524)
          (end 0.762 1.524)
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy -1.524 -1.778) (xy -1.524 1.778))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy 1.524 -1.778) (xy 1.524 1.778))
          (stroke (width 0.254) (type default))
          (fill (type none))
        )
      )
      (symbol "Crystal_1_1"
        (pin passive line (at -3.81 0 0) (length 2.286) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 3.81 0 180) (length 2.286) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_Polyfuse():
    """Polyfuse - 2 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:Polyfuse"
      (pin_names
        (offset 0.254) hide)
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "F"
        (at 0 2.54 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "Polyfuse"
        (at 0 -2.54 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Fuse:Fuse_1812_4532Metric"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Resettable fuse, polymeric positive temperature coefficient"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "Polyfuse_0_1"
        (rectangle
          (start -2.54 -1.016)
          (end 2.54 1.016)
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (polyline
          (pts (xy -2.54 -1.016) (xy 2.54 1.016))
          (stroke (width 0) (type default))
          (fill (type none))
        )
      )
      (symbol "Polyfuse_1_1"
        (pin passive line (at -3.81 0 0) (length 1.27) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 3.81 0 180) (length 1.27) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_MountingHole_Pad():
    """Mounting hole with pad - 1 pin"""
    return textwrap.dedent("""\
    (symbol "DB20G:MountingHole_Pad"
      (pin_names
        (offset 1.016) hide)
      (exclude_from_sim no)
      (in_bom no)
      (on_board yes)
      (property "Reference" "MH"
        (at 0 5.08 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "MountingHole_Pad"
        (at 0 3.175 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "MountingHole:MountingHole_3.2mm_M3_Pad_Via"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Mounting hole with connection pad"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "MountingHole_Pad_0_1"
        (circle
          (center 0 0)
          (radius 1.27)
          (stroke (width 1.27) (type default))
          (fill (type none))
        )
      )
      (symbol "MountingHole_Pad_1_1"
        (pin input line (at 0 -2.54 90) (length 1.27) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_PWR_FLAG():
    """Power flag symbol"""
    return textwrap.dedent("""\
    (symbol "DB20G:PWR_FLAG"
      (power)
      (pin_names
        (offset 0) hide)
      (exclude_from_sim no)
      (in_bom no)
      (on_board yes)
      (property "Reference" "#FLG"
        (at 0 1.905 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Value" "PWR_FLAG"
        (at 0 3.81 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Special symbol for telling ERC where power comes from"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "PWR_FLAG_0_1"
        (pin power_in line (at 0 0 90) (length 0) (name "pwr" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_RJ45():
    """RJ45 connector - 9 pins (1-8 + SHIELD/9)"""
    return textwrap.dedent("""\
    (symbol "DB20G:RJ45"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "J"
        (at 0 13.97 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "RJ45"
        (at 0 -15.24 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Connector_RJ:RJ45_Amphenol_RJHSE5380"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "RJ45 connector, 8P8C with shield"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "RJ45_0_1"
        (rectangle
          (start -5.08 12.7)
          (end 5.08 -13.97)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "RJ45_1_1"
        (pin passive line (at 7.62 10.16 180) (length 2.54) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 7.62 180) (length 2.54) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 5.08 180) (length 2.54) (name "3" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 2.54 180) (length 2.54) (name "4" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 0 180) (length 2.54) (name "5" (effects (font (size 1.0 1.0)))) (number "5" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 -2.54 180) (length 2.54) (name "6" (effects (font (size 1.0 1.0)))) (number "6" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 -5.08 180) (length 2.54) (name "7" (effects (font (size 1.0 1.0)))) (number "7" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 -7.62 180) (length 2.54) (name "8" (effects (font (size 1.0 1.0)))) (number "8" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 7.62 -12.7 180) (length 2.54) (name "SHIELD" (effects (font (size 1.0 1.0)))) (number "9" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_Relay_DPDT():
    """DPDT relay - G5V-2-DC5 - 8 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:Relay_DPDT"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "K"
        (at 0 11.43 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "G5V-2-DC5"
        (at 0 -11.43 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Relay_THT:Relay_DPDT_Omron_G5V-2"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "DPDT relay, Omron G5V-2, 5V coil"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "Relay_DPDT_0_1"
        (rectangle
          (start -7.62 10.16)
          (end 7.62 -10.16)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
        (polyline
          (pts (xy -3.81 -2.54) (xy -3.81 2.54))
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (arc
          (start -3.81 -2.54)
          (mid -3.175 -1.905)
          (end -3.81 -1.27)
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (arc
          (start -3.81 -1.27)
          (mid -3.175 -0.635)
          (end -3.81 0)
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (arc
          (start -3.81 0)
          (mid -3.175 0.635)
          (end -3.81 1.27)
          (stroke (width 0) (type default))
          (fill (type none))
        )
        (arc
          (start -3.81 1.27)
          (mid -3.175 1.905)
          (end -3.81 2.54)
          (stroke (width 0) (type default))
          (fill (type none))
        )
      )
      (symbol "Relay_DPDT_1_1"
        (pin passive line (at -10.16 5.08 0) (length 2.54) (name "Coil+" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at -10.16 -5.08 0) (length 2.54) (name "Coil-" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 7.62 180) (length 2.54) (name "P1_COM" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 5.08 180) (length 2.54) (name "P1_NO" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 2.54 180) (length 2.54) (name "P1_NC" (effects (font (size 1.0 1.0)))) (number "5" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -2.54 180) (length 2.54) (name "P2_COM" (effects (font (size 1.0 1.0)))) (number "6" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -5.08 180) (length 2.54) (name "P2_NO" (effects (font (size 1.0 1.0)))) (number "7" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -7.62 180) (length 2.54) (name "P2_NC" (effects (font (size 1.0 1.0)))) (number "8" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_USB_C_Receptacle():
    """USB-C receptacle - GCT USB4105 - 16 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:USB_C_Receptacle"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "J"
        (at 0 21.59 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "USB_C_Receptacle"
        (at 0 -21.59 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Connector_USB:USB_C_Receptacle_GCT_USB4105-xx-A_16P_TopMnt_Horizontal"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "USB Type-C Receptacle"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "USB_C_Receptacle_0_1"
        (rectangle
          (start -7.62 20.32)
          (end 7.62 -20.32)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "USB_C_Receptacle_1_1"
        (pin power_in line (at 10.16 17.78 180) (length 2.54) (name "VBUS" (effects (font (size 1.0 1.0)))) (number "A4" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 15.24 180) (length 2.54) (name "VBUS" (effects (font (size 1.0 1.0)))) (number "B4" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 10.16 180) (length 2.54) (name "CC1" (effects (font (size 1.0 1.0)))) (number "A5" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 7.62 180) (length 2.54) (name "CC2" (effects (font (size 1.0 1.0)))) (number "B5" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 2.54 180) (length 2.54) (name "D+" (effects (font (size 1.0 1.0)))) (number "A6" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 0 180) (length 2.54) (name "D-" (effects (font (size 1.0 1.0)))) (number "A7" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 -2.54 180) (length 2.54) (name "D+" (effects (font (size 1.0 1.0)))) (number "B6" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 -5.08 180) (length 2.54) (name "D-" (effects (font (size 1.0 1.0)))) (number "B7" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 -7.62 180) (length 2.54) (name "SBU1" (effects (font (size 1.0 1.0)))) (number "A8" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 10.16 -10.16 180) (length 2.54) (name "SBU2" (effects (font (size 1.0 1.0)))) (number "B8" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at 10.16 -15.24 180) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "A1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -17.78 180) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "B1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -12.7 180) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "A12" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -15.24 180) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "B12" (effects (font (size 1.0 1.0)))))
        (pin passive line (at 10.16 -20.32 180) (length 2.54) (name "SHIELD" (effects (font (size 1.0 1.0)))) (number "S1" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_Conn_01x02():
    """Generic 2-pin connector header - through-hole - 2 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:Conn_01x02"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "J"
        (at 0 3.81 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "Conn_01x02"
        (at 0 -3.81 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Connector_PinHeader_2.54mm:PinHeader_1x02_P2.54mm_Vertical"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Generic connector, single row, 2 pins"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "Conn_01x02_0_1"
        (rectangle
          (start -1.27 2.54)
          (end 1.27 -2.54)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "Conn_01x02_1_1"
        (pin passive line (at -3.81 1.27 0) (length 2.54) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at -3.81 -1.27 0) (length 2.54) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_FE1_1s():
    """FE1.1s USB 2.0 Hub Controller - SSOP-28 - 28 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:FE1.1s"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "U"
        (at 0 24.13 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "FE1.1s"
        (at 0 -24.13 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Package_SO:SSOP-28_5.3x10.2mm_P0.65mm"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "USB 2.0 High Speed 4-Port Hub Controller, SSOP-28"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "FE1.1s_0_1"
        (rectangle
          (start -10.16 22.86)
          (end 10.16 -22.86)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "FE1.1s_1_1"
        (pin input line (at -12.7 20.32 0) (length 2.54) (name "VD18OUT" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 17.78 0) (length 2.54) (name "DM0" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 15.24 0) (length 2.54) (name "DP0" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 12.7 0) (length 2.54) (name "VD33OUT" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 10.16 0) (length 2.54) (name "XI" (effects (font (size 1.0 1.0)))) (number "5" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 7.62 0) (length 2.54) (name "XO" (effects (font (size 1.0 1.0)))) (number "6" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 5.08 0) (length 2.54) (name "RESET" (effects (font (size 1.0 1.0)))) (number "7" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -12.7 2.54 0) (length 2.54) (name "VDD5" (effects (font (size 1.0 1.0)))) (number "8" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 0 0) (length 2.54) (name "PGANG" (effects (font (size 1.0 1.0)))) (number "9" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -2.54 0) (length 2.54) (name "OVCUR4" (effects (font (size 1.0 1.0)))) (number "10" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 -2.54 180) (length 2.54) (name "PWREN4" (effects (font (size 1.0 1.0)))) (number "11" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 0 180) (length 2.54) (name "DM4" (effects (font (size 1.0 1.0)))) (number "12" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 2.54 180) (length 2.54) (name "DP4" (effects (font (size 1.0 1.0)))) (number "13" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at 12.7 5.08 180) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "14" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 7.62 180) (length 2.54) (name "DM3" (effects (font (size 1.0 1.0)))) (number "15" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 10.16 180) (length 2.54) (name "DP3" (effects (font (size 1.0 1.0)))) (number "16" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -5.08 0) (length 2.54) (name "OVCUR3" (effects (font (size 1.0 1.0)))) (number "17" (effects (font (size 1.0 1.0)))))
        (pin output line (at -12.7 -7.62 0) (length 2.54) (name "PWREN3" (effects (font (size 1.0 1.0)))) (number "18" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -10.16 0) (length 2.54) (name "DM2" (effects (font (size 1.0 1.0)))) (number "19" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -12.7 0) (length 2.54) (name "DP2" (effects (font (size 1.0 1.0)))) (number "20" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -15.24 0) (length 2.54) (name "OVCUR2" (effects (font (size 1.0 1.0)))) (number "21" (effects (font (size 1.0 1.0)))))
        (pin output line (at -12.7 -17.78 0) (length 2.54) (name "PWREN2" (effects (font (size 1.0 1.0)))) (number "22" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 12.7 180) (length 2.54) (name "DM1" (effects (font (size 1.0 1.0)))) (number "23" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 15.24 180) (length 2.54) (name "DP1" (effects (font (size 1.0 1.0)))) (number "24" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 17.78 180) (length 2.54) (name "OVCUR1" (effects (font (size 1.0 1.0)))) (number "25" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 20.32 180) (length 2.54) (name "PWREN1" (effects (font (size 1.0 1.0)))) (number "26" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -20.32 0) (length 2.54) (name "TEST" (effects (font (size 1.0 1.0)))) (number "27" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 -5.08 180) (length 2.54) (name "EECS" (effects (font (size 1.0 1.0)))) (number "28" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_CP2102N():
    """CP2102N USB-UART Bridge - QFN-28 - 28 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:CP2102N"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "U"
        (at 0 24.13 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "CP2102N"
        (at 0 -24.13 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Package_DFN_QFN:QFN-28-1EP_5x5mm_P0.5mm_EP3.35x3.35mm"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "USB to UART Bridge, QFN-28"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "CP2102N_0_1"
        (rectangle
          (start -10.16 22.86)
          (end 10.16 -22.86)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "CP2102N_1_1"
        (pin power_in line (at -12.7 20.32 0) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 17.78 0) (length 2.54) (name "GPIO.0/CLK" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 15.24 0) (length 2.54) (name "GPIO.1" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -12.7 12.7 0) (length 2.54) (name "D+" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -12.7 10.16 0) (length 2.54) (name "D-" (effects (font (size 1.0 1.0)))) (number "5" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -12.7 7.62 0) (length 2.54) (name "VIO" (effects (font (size 1.0 1.0)))) (number "6" (effects (font (size 1.0 1.0)))))
        (pin power_out line (at -12.7 5.08 0) (length 2.54) (name "VDD" (effects (font (size 1.0 1.0)))) (number "7" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -12.7 2.54 0) (length 2.54) (name "REGIN" (effects (font (size 1.0 1.0)))) (number "8" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 0 0) (length 2.54) (name "~{RST}" (effects (font (size 1.0 1.0)))) (number "9" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -2.54 0) (length 2.54) (name "NC" (effects (font (size 1.0 1.0)))) (number "10" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -5.08 0) (length 2.54) (name "SUSPEND" (effects (font (size 1.0 1.0)))) (number "11" (effects (font (size 1.0 1.0)))))
        (pin output line (at -12.7 -7.62 0) (length 2.54) (name "~{SUSPEND}" (effects (font (size 1.0 1.0)))) (number "12" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -10.16 0) (length 2.54) (name "CHREN" (effects (font (size 1.0 1.0)))) (number "13" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -12.7 0) (length 2.54) (name "CHR0" (effects (font (size 1.0 1.0)))) (number "14" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 -12.7 180) (length 2.54) (name "CHR1" (effects (font (size 1.0 1.0)))) (number "15" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 -10.16 180) (length 2.54) (name "GPIO.5" (effects (font (size 1.0 1.0)))) (number "16" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 -7.62 180) (length 2.54) (name "GPIO.4" (effects (font (size 1.0 1.0)))) (number "17" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 -5.08 180) (length 2.54) (name "GPIO.3" (effects (font (size 1.0 1.0)))) (number "18" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 -2.54 180) (length 2.54) (name "TXD" (effects (font (size 1.0 1.0)))) (number "19" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 0 180) (length 2.54) (name "RXD" (effects (font (size 1.0 1.0)))) (number "20" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 2.54 180) (length 2.54) (name "RTS" (effects (font (size 1.0 1.0)))) (number "21" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 5.08 180) (length 2.54) (name "CTS" (effects (font (size 1.0 1.0)))) (number "22" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 7.62 180) (length 2.54) (name "DTR" (effects (font (size 1.0 1.0)))) (number "23" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 10.16 180) (length 2.54) (name "DSR" (effects (font (size 1.0 1.0)))) (number "24" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 12.7 180) (length 2.54) (name "DCD" (effects (font (size 1.0 1.0)))) (number "25" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 15.24 180) (length 2.54) (name "RI" (effects (font (size 1.0 1.0)))) (number "26" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 17.78 180) (length 2.54) (name "TX_LED" (effects (font (size 1.0 1.0)))) (number "27" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 20.32 180) (length 2.54) (name "RX_LED" (effects (font (size 1.0 1.0)))) (number "28" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_CM108AH():
    """CM108AH USB Audio Codec - SSOP-28 - 28 pins"""
    return textwrap.dedent("""\
    (symbol "DB20G:CM108AH"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "U"
        (at 0 24.13 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "CM108AH"
        (at 0 -24.13 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Package_SO:SSOP-28_5.3x10.2mm_P0.65mm"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "USB Audio Codec, SSOP-28"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "CM108AH_0_1"
        (rectangle
          (start -10.16 22.86)
          (end 10.16 -22.86)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "CM108AH_1_1"
        (pin bidirectional line (at -12.7 20.32 0) (length 2.54) (name "D+" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -12.7 17.78 0) (length 2.54) (name "D-" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -12.7 15.24 0) (length 2.54) (name "VDD" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin output line (at -12.7 12.7 0) (length 2.54) (name "VDD18" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -12.7 10.16 0) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "5" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 7.62 0) (length 2.54) (name "XI" (effects (font (size 1.0 1.0)))) (number "6" (effects (font (size 1.0 1.0)))))
        (pin output line (at -12.7 5.08 0) (length 2.54) (name "XO" (effects (font (size 1.0 1.0)))) (number "7" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 2.54 0) (length 2.54) (name "SPDIF" (effects (font (size 1.0 1.0)))) (number "8" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 0 0) (length 2.54) (name "CFG0" (effects (font (size 1.0 1.0)))) (number "9" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -2.54 0) (length 2.54) (name "CFG1" (effects (font (size 1.0 1.0)))) (number "10" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -5.08 0) (length 2.54) (name "CFG2" (effects (font (size 1.0 1.0)))) (number "11" (effects (font (size 1.0 1.0)))))
        (pin input line (at -12.7 -7.62 0) (length 2.54) (name "GPIO3_MUTE" (effects (font (size 1.0 1.0)))) (number "12" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -12.7 -10.16 0) (length 2.54) (name "GPIO4" (effects (font (size 1.0 1.0)))) (number "13" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -12.7 -12.7 0) (length 2.54) (name "AGND" (effects (font (size 1.0 1.0)))) (number "14" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 -12.7 180) (length 2.54) (name "MIC_IN" (effects (font (size 1.0 1.0)))) (number "15" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 -10.16 180) (length 2.54) (name "VREF" (effects (font (size 1.0 1.0)))) (number "16" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 -7.62 180) (length 2.54) (name "SPK_L" (effects (font (size 1.0 1.0)))) (number "17" (effects (font (size 1.0 1.0)))))
        (pin output line (at 12.7 -5.08 180) (length 2.54) (name "SPK_R" (effects (font (size 1.0 1.0)))) (number "18" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at 12.7 -2.54 180) (length 2.54) (name "AVDD" (effects (font (size 1.0 1.0)))) (number "19" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 0 180) (length 2.54) (name "GPIO0_VOLUP" (effects (font (size 1.0 1.0)))) (number "20" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 2.54 180) (length 2.54) (name "GPIO1_VOLDN" (effects (font (size 1.0 1.0)))) (number "21" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 5.08 180) (length 2.54) (name "GPIO2_PLAY" (effects (font (size 1.0 1.0)))) (number "22" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 7.62 180) (length 2.54) (name "GPIO_REC" (effects (font (size 1.0 1.0)))) (number "23" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 10.16 180) (length 2.54) (name "TEST" (effects (font (size 1.0 1.0)))) (number "24" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 12.7 180) (length 2.54) (name "SDA" (effects (font (size 1.0 1.0)))) (number "25" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 15.24 180) (length 2.54) (name "SCL" (effects (font (size 1.0 1.0)))) (number "26" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 17.78 180) (length 2.54) (name "SUS" (effects (font (size 1.0 1.0)))) (number "27" (effects (font (size 1.0 1.0)))))
        (pin input line (at 12.7 20.32 180) (length 2.54) (name "RES" (effects (font (size 1.0 1.0)))) (number "28" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


# ============================================================
# COMPONENT INSTANCE GENERATOR
# ============================================================

def make_instance(lib_id, ref, value, footprint, x, y, pin_count, pin_names_or_numbers, inst_uuid=None, project_name="DB20G-Interface", project_uuid_path=None):
    """Generate a component instance at (x,y) with unique UUIDs for each pin."""
    if inst_uuid is None:
        inst_uuid = make_uuid()
    if project_uuid_path is None:
        project_uuid_path = "/db2c6c2e-3e4a-4b8f-9d1a-1a2b3c4d5e6f"

    lines = []
    lines.append(f'\t(symbol')
    lines.append(f'\t\t(lib_id "{lib_id}")')
    lines.append(f'\t\t(at {x} {y} 0)')
    lines.append(f'\t\t(unit 1)')
    lines.append(f'\t\t(exclude_from_sim no)')
    lines.append(f'\t\t(in_bom yes)')
    lines.append(f'\t\t(on_board yes)')
    lines.append(f'\t\t(dnp no)')
    lines.append(f'\t\t(uuid "{inst_uuid}")')
    lines.append(f'\t\t(property "Reference" "{ref}"')
    lines.append(f'\t\t\t(at {x} {y - 2.54} 0)')
    lines.append(f'\t\t\t(effects')
    lines.append(f'\t\t\t\t(font')
    lines.append(f'\t\t\t\t\t(size 1.27 1.27)')
    lines.append(f'\t\t\t\t)')
    lines.append(f'\t\t\t)')
    lines.append(f'\t\t)')
    lines.append(f'\t\t(property "Value" "{value}"')
    lines.append(f'\t\t\t(at {x} {y - 5.08} 0)')
    lines.append(f'\t\t\t(effects')
    lines.append(f'\t\t\t\t(font')
    lines.append(f'\t\t\t\t\t(size 1.27 1.27)')
    lines.append(f'\t\t\t\t)')
    lines.append(f'\t\t\t)')
    lines.append(f'\t\t)')
    lines.append(f'\t\t(property "Footprint" "{footprint}"')
    lines.append(f'\t\t\t(at {x} {y} 0)')
    lines.append(f'\t\t\t(effects')
    lines.append(f'\t\t\t\t(font')
    lines.append(f'\t\t\t\t\t(size 1.27 1.27)')
    lines.append(f'\t\t\t\t)')
    lines.append(f'\t\t\t\t(hide yes)')
    lines.append(f'\t\t\t)')
    lines.append(f'\t\t)')
    lines.append(f'\t\t(property "Datasheet" ""')
    lines.append(f'\t\t\t(at {x} {y} 0)')
    lines.append(f'\t\t\t(effects')
    lines.append(f'\t\t\t\t(font')
    lines.append(f'\t\t\t\t\t(size 1.27 1.27)')
    lines.append(f'\t\t\t\t)')
    lines.append(f'\t\t\t\t(hide yes)')
    lines.append(f'\t\t\t)')
    lines.append(f'\t\t)')
    lines.append(f'\t\t(property "Description" ""')
    lines.append(f'\t\t\t(at {x} {y} 0)')
    lines.append(f'\t\t\t(effects')
    lines.append(f'\t\t\t\t(font')
    lines.append(f'\t\t\t\t\t(size 1.27 1.27)')
    lines.append(f'\t\t\t\t)')
    lines.append(f'\t\t\t\t(hide yes)')
    lines.append(f'\t\t\t)')
    lines.append(f'\t\t)')

    # Pin entries
    for pin_id in pin_names_or_numbers:
        lines.append(f'\t\t(pin "{pin_id}"')
        lines.append(f'\t\t\t(uuid "{make_uuid()}")')
        lines.append(f'\t\t)')

    # Instances
    lines.append(f'\t\t(instances')
    lines.append(f'\t\t\t(project "{project_name}"')
    lines.append(f'\t\t\t\t(path "{project_uuid_path}"')
    lines.append(f'\t\t\t\t\t(reference "{ref}")')
    lines.append(f'\t\t\t\t\t(unit 1)')
    lines.append(f'\t\t\t\t)')
    lines.append(f'\t\t\t)')
    lines.append(f'\t\t)')
    lines.append(f'\t)')

    return '\n'.join(lines)


# ============================================================
# PIN POSITIONS PER SYMBOL TYPE
# {symbol_type: {pin_number: (rel_x, rel_y)}}
# Extracted from symbol definitions above
# ============================================================

PIN_POS = {
    "2N2222A": {"1": (0, 0), "2": (2.54, 2.54), "3": (2.54, -2.54)},
    "AMS1117-3.3": {"1": (-7.62, 0), "2": (7.62, 0), "3": (-7.62, -2.54)},
    "C": {"1": (0, 3.81), "2": (0, -3.81)},
    "R": {"1": (0, 3.81), "2": (0, -3.81)},
    "LED": {"1": (-3.81, 0), "2": (3.81, 0)},
    "D": {"1": (-3.81, 0), "2": (3.81, 0)},
    "Crystal": {"1": (-3.81, 0), "2": (3.81, 0)},
    "Polyfuse": {"1": (-3.81, 0), "2": (3.81, 0)},
    "MountingHole_Pad": {"1": (0, -2.54)},
    "Conn_01x02": {"1": (-3.81, 1.27), "2": (-3.81, -1.27)},
    "PWR_FLAG": {"1": (0, 0)},
    "RJ45": {
        "1": (7.62, 10.16), "2": (7.62, 7.62), "3": (7.62, 5.08),
        "4": (7.62, 2.54), "5": (7.62, 0), "6": (7.62, -2.54),
        "7": (7.62, -5.08), "8": (7.62, -7.62), "9": (7.62, -12.7),
    },
    "Relay_DPDT": {
        "1": (-10.16, 5.08), "2": (-10.16, -5.08),
        "3": (10.16, 7.62), "4": (10.16, 5.08), "5": (10.16, 2.54),
        "6": (10.16, -2.54), "7": (10.16, -5.08), "8": (10.16, -7.62),
    },
    "USB_C_Receptacle": {
        "A4": (10.16, 17.78), "B4": (10.16, 15.24),
        "A5": (10.16, 10.16), "B5": (10.16, 7.62),
        "A6": (10.16, 2.54), "A7": (10.16, 0),
        "B6": (10.16, -2.54), "B7": (10.16, -5.08),
        "A8": (10.16, -7.62), "B8": (10.16, -10.16),
        "A1": (10.16, -15.24), "B1": (10.16, -17.78),
        "A12": (10.16, -12.7), "B12": (10.16, -15.24),
        "S1": (10.16, -20.32),
    },
    "FE1.1s": {
        "1": (-12.7, 20.32), "2": (-12.7, 17.78), "3": (-12.7, 15.24),
        "4": (-12.7, 12.7), "5": (-12.7, 10.16), "6": (-12.7, 7.62),
        "7": (-12.7, 5.08), "8": (-12.7, 2.54), "9": (-12.7, 0),
        "10": (-12.7, -2.54), "11": (12.7, -2.54), "12": (12.7, 0),
        "13": (12.7, 2.54), "14": (12.7, 5.08), "15": (12.7, 7.62),
        "16": (12.7, 10.16), "17": (-12.7, -5.08), "18": (-12.7, -7.62),
        "19": (-12.7, -10.16), "20": (-12.7, -12.7), "21": (-12.7, -15.24),
        "22": (-12.7, -17.78), "23": (12.7, 12.7), "24": (12.7, 15.24),
        "25": (12.7, 17.78), "26": (12.7, 20.32), "27": (-12.7, -20.32),
        "28": (12.7, -5.08),
    },
    "CP2102N": {
        "1": (-12.7, 20.32), "2": (-12.7, 17.78), "3": (-12.7, 15.24),
        "4": (-12.7, 12.7), "5": (-12.7, 10.16), "6": (-12.7, 7.62),
        "7": (-12.7, 5.08), "8": (-12.7, 2.54), "9": (-12.7, 0),
        "10": (-12.7, -2.54), "11": (-12.7, -5.08), "12": (-12.7, -7.62),
        "13": (-12.7, -10.16), "14": (-12.7, -12.7), "15": (12.7, -12.7),
        "16": (12.7, -10.16), "17": (12.7, -7.62), "18": (12.7, -5.08),
        "19": (12.7, -2.54), "20": (12.7, 0), "21": (12.7, 2.54),
        "22": (12.7, 5.08), "23": (12.7, 7.62), "24": (12.7, 10.16),
        "25": (12.7, 12.7), "26": (12.7, 15.24), "27": (12.7, 17.78),
        "28": (12.7, 20.32),
    },
    "CM108AH": {
        "1": (-12.7, 20.32), "2": (-12.7, 17.78), "3": (-12.7, 15.24),
        "4": (-12.7, 12.7), "5": (-12.7, 10.16), "6": (-12.7, 7.62),
        "7": (-12.7, 5.08), "8": (-12.7, 2.54), "9": (-12.7, 0),
        "10": (-12.7, -2.54), "11": (-12.7, -5.08), "12": (-12.7, -7.62),
        "13": (-12.7, -10.16), "14": (-12.7, -12.7), "15": (12.7, -12.7),
        "16": (12.7, -10.16), "17": (12.7, -7.62), "18": (12.7, -5.08),
        "19": (12.7, -2.54), "20": (12.7, 0), "21": (12.7, 2.54),
        "22": (12.7, 5.08), "23": (12.7, 7.62), "24": (12.7, 10.16),
        "25": (12.7, 12.7), "26": (12.7, 15.24), "27": (12.7, 17.78),
        "28": (12.7, 20.32),
    },
}


# ============================================================
# NET ASSIGNMENTS PER COMPONENT
# {ref: {pin_number: net_name_or_None}}
# None = intentionally unconnected (no_connect marker placed)
# ============================================================

NETS = {
    # ---- USB-C Connector ----
    "J1": {
        "A4": "VBUS", "B4": "VBUS",           # VBUS → fuse → +5V
        "A5": "CC1", "B5": "CC2",             # CC pull-downs for device ID
        "A6": "USB_DP", "A7": "USB_DM",       # D+/D- to FE1.1s hub
        "B6": "USB_DP", "B7": "USB_DM",       # D+/D- redundant pair
        "A8": None, "B8": None,               # SBU1/SBU2 unused
        "A1": "GND", "B1": "GND",             # GND
        "A12": "GND", "B12": "GND",           # GND
        "S1": "GND",                           # Shield
    },

    # ---- FE1.1s USB Hub ----
    "U1": {
        "1": "VD18OUT",                        # 1.8V regulator output → C12
        "2": "USB_DM", "3": "USB_DP",         # Upstream D-/D+ from J1
        "4": "VD33OUT",                        # 3.3V regulator output → C13
        "5": "XI", "6": "XO",                 # Crystal oscillator
        "7": "FE_RESET",                       # Reset, pull-up via R13
        "8": "+5V",                            # VDD5 power
        "9": "GND",                            # PGANG → GND (individual mode)
        "10": "+5V",                           # OVCUR4 → VDD (no overcurrent)
        "11": None,                            # PWREN4 output, unused
        "12": "GND", "13": "GND",             # DM4/DP4 → GND (port 4 unused)
        "14": "GND",                           # GND
        "15": "GND", "16": "GND",             # DM3/DP3 → GND (port 3 unused)
        "17": "+5V",                           # OVCUR3 → VDD
        "18": None,                            # PWREN3 output, unused
        "19": "HUB_P2_DM", "20": "HUB_P2_DP", # Port 2 → CM108AH
        "21": "+5V",                           # OVCUR2 → VDD
        "22": None,                            # PWREN2 output, unused
        "23": "HUB_P1_DM", "24": "HUB_P1_DP", # Port 1 → CP2102N
        "25": "+5V",                           # OVCUR1 → VDD
        "26": None,                            # PWREN1 output, unused
        "27": "GND",                           # TEST → GND
        "28": "GND",                           # EECS → GND (no EEPROM)
    },

    # ---- CP2102N USB-UART ----
    "U2": {
        "1": "GND",                            # GND
        "2": None, "3": None,                 # GPIO.0/CLK, GPIO.1 unused
        "4": "HUB_P1_DP", "5": "HUB_P1_DM",  # USB D+/D- from hub port 1
        "6": "+3.3V",                          # VIO
        "7": "+3.3V",                          # VDD (internal 3.3V)
        "8": "+5V",                            # REGIN (5V input to internal reg)
        "9": "+3.3V",                          # ~RST pull-up
        "10": None,                            # NC pin
        "11": None, "12": None,               # SUSPEND/~SUSPEND unused
        "13": "GND",                           # CHREN → GND (charger disabled)
        "14": None, "15": None,               # CHR0/CHR1 unused
        "16": None, "17": None, "18": None,   # GPIO.5/.4/.3 unused
        "19": "CP_TXD",                        # TXD → K1 P1_NO (serial to radio)
        "20": "CP_RXD",                        # RXD → K1 P2_NO (serial from radio)
        "21": "PTT_CTRL",                      # RTS → R1 → Q1 (PTT driver)
        "22": "CTS_PU",                        # CTS pull-up via R14
        "23": "DTR_CTRL",                      # DTR → R2 → Q2 (relay driver)
        "24": "GND", "25": "GND", "26": "GND", # DSR/DCD/RI tied low
        "27": "CP_TXLED",                      # TX_LED → LED4 indicator
        "28": None,                            # RX_LED unused
    },

    # ---- CM108AH USB Audio ----
    "U3": {
        "1": "HUB_P2_DP", "2": "HUB_P2_DM",  # USB D+/D- from hub port 2
        "3": "+5V",                            # VDD
        "4": "CM_VDD18",                       # VDD18 internal 1.8V → C15
        "5": "GND",                            # GND
        "6": "GND",                            # XI → GND (use internal osc)
        "7": None,                             # XO unconnected (internal osc)
        "8": "GND",                            # SPDIF → GND (unused)
        "9": "GND", "10": "GND", "11": "GND", # CFG0-2 → GND (default config)
        "12": "+5V",                           # GPIO3_MUTE pull-up (unmuted)
        "13": "CM_GPIO4",                      # GPIO4 → LED3 (audio activity)
        "14": "GND",                           # AGND
        "15": "AUDIO_RX_DIV",                  # MIC_IN ← R3/R4 divider
        "16": "CM_VREF",                       # VREF → C14
        "17": "AUDIO_OUT",                     # SPK_L → R5 (TX audio)
        "18": None,                            # SPK_R unused
        "19": "+5V",                           # AVDD
        "20": "+5V", "21": "+5V",             # GPIO0/1 pull-up (vol buttons)
        "22": "+5V", "23": "+5V",             # GPIO2/REC pull-up
        "24": "GND",                           # TEST → GND
        "25": None, "26": None,               # SDA/SCL unused
        "27": None,                            # SUS unused
        "28": "CM_RESET",                      # RES → pull-up via R15
    },

    # ---- AMS1117-3.3 LDO ----
    "U4": {
        "1": "GND",                            # GND/Adj
        "2": "+3.3V",                          # VO output
        "3": "+5V",                            # VI input
    },

    # ---- RJ-45 RADIO (to DB20-G handset port) ----
    "J2": {
        "1": "RJ45_P1",                        # Radio pin 1 (pass-through)
        "2": "RADIO_MIC",                      # MIC/TX to radio ← K1 P1_COM
        "3": "PTT_OUT",                        # PTT ← Q1 collector
        "4": "RADIO_SQL",                      # Squelch detect (pass-through)
        "5": "RADIO_SPK",                      # SPK/RX from radio → K1 P2_COM
        "6": "GND", "7": "GND", "8": "GND",   # GND
        "9": "GND",                            # Shield
    },

    # ---- RJ-45 HANDSET (stock handset pass-through) ----
    # All pins connected 1:1 to J2 so the handset works transparently
    "J3": {
        "1": "RJ45_P1",                        # Pass-through to J2.1
        "2": "RADIO_MIC",                      # Pass-through to J2.2
        "3": "PTT_OUT",                        # Pass-through to J2.3
        "4": "RADIO_SQL",                      # Pass-through to J2.4
        "5": "RADIO_SPK",                      # Pass-through to J2.5
        "6": "GND", "7": "GND", "8": "GND",   # GND
        "9": "GND",                            # Shield
    },

    # ---- Transistors ----
    "Q1": {"1": "PTT_BASE", "2": "PTT_OUT", "3": "GND"},     # PTT driver
    "Q2": {"1": "DTR_BASE", "2": "RELAY_COIL_LO", "3": "GND"}, # Relay driver

    # ---- DPDT Relay ----
    "K1": {
        "1": "+5V",                            # Coil+ (energize from +5V)
        "2": "RELAY_COIL_LO",                  # Coil- (driven by Q2)
        "3": "RADIO_MIC",                      # P1_COM → J2.2 (TX to radio)
        "4": "CP_TXD",                         # P1_NO  → CP2102N TXD (serial mode)
        "5": "AUDIO_TX_DIV",                   # P1_NC  → R5/R6 divider (audio mode)
        "6": "RADIO_SPK",                      # P2_COM → J2.5 (RX from radio)
        "7": "CP_RXD",                         # P2_NO  → CP2102N RXD (serial mode)
        "8": "AUDIO_RX",                       # P2_NC  → R3 input (audio mode)
    },

    # ---- Diodes ----
    "D1": {"1": "+5V", "2": "PTT_OUT"},        # PTT clamp diode
    "D2": {"1": "+5V", "2": "RELAY_COIL_LO"},  # Flyback diode across K1 coil
    "D3": {"1": "VBUS", "2": "EXT_5V"},         # Schottky: ext power → VBUS (charges phone)

    # ---- External Power Connector ----
    # External 5V feeds through D3 onto VBUS, powering the board AND
    # providing VBUS to the phone for OTG charging (most modern Android phones).
    # When J4 is disconnected, phone provides VBUS as normal.
    "J4": {"1": "EXT_5V", "2": "GND"},           # 2-pin header: external 5V input

    # ---- Polyfuse ----
    "F1": {"1": "VBUS", "2": "+5V"},           # USB VBUS → fused +5V rail

    # ---- Crystal ----
    "Y1": {"1": "XI", "2": "XO"},             # 12MHz for FE1.1s

    # ---- Resistors ----
    "R1":  {"1": "PTT_CTRL",  "2": "PTT_BASE"},     # 10k: RTS → Q1 base
    "R2":  {"1": "DTR_CTRL",  "2": "DTR_BASE"},     # 10k: DTR → Q2 base
    "R3":  {"1": "AUDIO_RX",  "2": "AUDIO_RX_DIV"}, # 10k: RX divider upper
    "R4":  {"1": "AUDIO_RX_DIV", "2": "GND"},       # 1k:  RX divider lower
    "R5":  {"1": "AUDIO_OUT", "2": "AUDIO_TX_DIV"}, # 10k: TX divider upper
    "R6":  {"1": "AUDIO_TX_DIV", "2": "GND"},       # 1k:  TX divider lower
    "R7":  {"1": "+5V",    "2": "LED1_R"},           # 330: LED1 (Power)
    "R8":  {"1": "+5V",    "2": "LED2_R"},           # 330: LED2 (PTT)
    "R9":  {"1": "+3.3V",  "2": "LED3_R"},           # 330: LED3 (Audio)
    "R10": {"1": "+3.3V",  "2": "LED4_R"},           # 330: LED4 (Serial)
    "R11": {"1": "CC1",    "2": "GND"},              # 5.1k: USB-C CC1 pull-down
    "R12": {"1": "CC2",    "2": "GND"},              # 5.1k: USB-C CC2 pull-down
    "R13": {"1": "+3.3V",  "2": "FE_RESET"},         # 10k: FE1.1s reset pull-up
    "R14": {"1": "+3.3V",  "2": "CTS_PU"},           # 10k: CP2102N CTS pull-up
    "R15": {"1": "+3.3V",  "2": "CM_RESET"},         # 10k: CM108AH reset pull-up

    # ---- LEDs (pin 1=Cathode, pin 2=Anode) ----
    "LED1": {"1": "GND",       "2": "LED1_R"},   # Power (Green): +5V→R7→LED1→GND
    "LED2": {"1": "PTT_OUT",   "2": "LED2_R"},   # PTT (Red): +5V→R8→LED2→PTT_OUT
    "LED3": {"1": "CM_GPIO4",  "2": "LED3_R"},   # Audio (Yellow): +3.3V→R9→LED3→GPIO4
    "LED4": {"1": "CP_TXLED",  "2": "LED4_R"},   # Serial (Blue): +3.3V→R10→LED4→TX_LED

    # ---- Capacitors (pin 1=top, pin 2=bottom/GND) ----
    "C1":  {"1": "+5V",     "2": "GND"},   # 100nF: FE1.1s VDD5 bypass
    "C2":  {"1": "+5V",     "2": "GND"},   # 10uF:  FE1.1s VDD5 bulk
    "C3":  {"1": "+3.3V",   "2": "GND"},   # 100nF: CP2102N VDD bypass
    "C4":  {"1": "+5V",     "2": "GND"},   # 10uF:  CP2102N REGIN bulk
    "C5":  {"1": "+5V",     "2": "GND"},   # 100nF: CM108AH VDD bypass
    "C6":  {"1": "+5V",     "2": "GND"},   # 10uF:  CM108AH VDD bulk
    "C7":  {"1": "XI",      "2": "GND"},   # 22pF:  Crystal XI load
    "C8":  {"1": "XO",      "2": "GND"},   # 22pF:  Crystal XO load
    "C9":  {"1": "+5V",     "2": "GND"},   # 100nF: AMS1117 input bypass
    "C10": {"1": "+3.3V",   "2": "GND"},   # 100nF: AMS1117 output bypass
    "C11": {"1": "+3.3V",   "2": "GND"},   # 10uF:  AMS1117 output bulk
    "C12": {"1": "VD18OUT", "2": "GND"},   # 1uF:   FE1.1s VD18OUT bypass
    "C13": {"1": "VD33OUT", "2": "GND"},   # 1uF:   FE1.1s VD33OUT bypass
    "C14": {"1": "CM_VREF", "2": "GND"},   # 1uF:   CM108AH VREF bypass
    "C15": {"1": "CM_VDD18","2": "GND"},   # 1uF:   CM108AH VDD18 bypass

    # ---- Mounting Holes (2 only, smaller board) ----
    "MH1": {"1": "GND"}, "MH2": {"1": "GND"},

    # ---- Power Flags ----
    "#FLG01": {"1": "+5V"},
    "#FLG02": {"1": "GND"},
    "#FLG03": {"1": "+3.3V"},
}


# ============================================================
# HELPER FUNCTIONS FOR NET CONNECTIVITY
# ============================================================

def get_symbol_type(lib_id):
    """Extract symbol type from lib_id (e.g., 'DB20G:FE1.1s' -> 'FE1.1s')."""
    return lib_id.split(":")[-1]


def stub_direction(rx, ry):
    """Determine wire stub direction from pin's relative position.
    
    Always go horizontal if pin has nonzero X offset (IC side pins),
    vertical only for pins directly above/below center (2-pin passives).
    This avoids overlaps between adjacent IC pins spaced 2.54mm apart.
    """
    if rx != 0:
        return (2.54 if rx > 0 else -2.54, 0)
    elif ry != 0:
        return (0, 2.54 if ry > 0 else -2.54)
    else:
        return (-2.54, 0)


def lbl_angle(dx, dy):
    """Determine global_label angle based on wire stub direction."""
    if dx > 0:
        return 0
    if dx < 0:
        return 180
    if dy < 0:
        return 90
    return 270


def make_global_label(net_name, x, y, angle=0):
    """Generate a global_label S-expression at (x, y)."""
    uid = make_uuid()
    return (
        f'\t(global_label "{net_name}"\n'
        f'\t\t(shape bidirectional)\n'
        f'\t\t(at {x} {y} {angle})\n'
        f'\t\t(effects\n'
        f'\t\t\t(font\n'
        f'\t\t\t\t(size 1.27 1.27)\n'
        f'\t\t\t)\n'
        f'\t\t)\n'
        f'\t\t(uuid "{uid}")\n'
        f'\t\t(property "Intersheetrefs" "${{INTERSHEET_REFS}}"\n'
        f'\t\t\t(at {x} {y} 0)\n'
        f'\t\t\t(effects\n'
        f'\t\t\t\t(font\n'
        f'\t\t\t\t\t(size 1.27 1.27)\n'
        f'\t\t\t\t)\n'
        f'\t\t\t\t(hide yes)\n'
        f'\t\t\t)\n'
        f'\t\t)\n'
        f'\t)'
    )


def make_wire(x1, y1, x2, y2):
    """Generate a wire S-expression from (x1,y1) to (x2,y2)."""
    uid = make_uuid()
    return (
        f'\t(wire\n'
        f'\t\t(pts\n'
        f'\t\t\t(xy {x1} {y1})\n'
        f'\t\t\t(xy {x2} {y2})\n'
        f'\t\t)\n'
        f'\t\t(stroke\n'
        f'\t\t\t(width 0)\n'
        f'\t\t\t(type default)\n'
        f'\t\t)\n'
        f'\t\t(uuid "{uid}")\n'
        f'\t)'
    )


def make_no_connect(x, y):
    """Generate a no_connect marker S-expression at (x, y)."""
    uid = make_uuid()
    return f'\t(no_connect (at {x} {y}) (uuid "{uid}"))'


# ============================================================
# MAIN GENERATION
# ============================================================

def main():
    project_uuid = "db2c6c2e-3e4a-4b8f-9d1a-1a2b3c4d5e6f"
    project_path = f"/{project_uuid}"

    # Collect all lib_symbols
    lib_syms = [
        sym_2N2222A(), sym_AMS1117(), sym_C(), sym_CM108AH(),
        sym_Conn_01x02(), sym_CP2102N(), sym_Crystal(), sym_D(),
        sym_FE1_1s(), sym_LED(), sym_MountingHole_Pad(), sym_PWR_FLAG(),
        sym_Polyfuse(), sym_R(), sym_RJ45(), sym_Relay_DPDT(),
        sym_USB_C_Receptacle(),
    ]

    # ================================================================
    # COMPONENT DEFINITIONS
    # (lib_id, ref, value, footprint, x, y, pin_list)
    # Placed in a grid OFF the board for quilter auto-placement.
    # ================================================================
    comp_defs = [
        # === ICs ===
        ("DB20G:FE1.1s", "U1", "FE1.1s",
         "Package_SO:SSOP-28_5.3x10.2mm_P0.65mm",
         400, 50, [str(i) for i in range(1, 29)]),

        ("DB20G:CP2102N", "U2", "CP2102N",
         "Package_DFN_QFN:QFN-28-1EP_5x5mm_P0.5mm_EP3.35x3.35mm",
         460, 50, [str(i) for i in range(1, 29)]),

        ("DB20G:CM108AH", "U3", "CM108AH",
         "Package_SO:SSOP-28_5.3x10.2mm_P0.65mm",
         520, 50, [str(i) for i in range(1, 29)]),

        ("DB20G:AMS1117-3.3", "U4", "AMS1117-3.3",
         "Package_TO_SOT_SMD:SOT-223-3_TabPin2",
         580, 50, ["1", "2", "3"]),

        # === Connectors ===
        ("DB20G:USB_C_Receptacle", "J1", "USB-C",
         "Connector_USB:USB_C_Receptacle_GCT_USB4105-xx-A_16P_TopMnt_Horizontal",
         400, 120,
         ["A4", "B4", "A5", "B5", "A6", "A7", "B6", "B7",
          "A8", "B8", "A1", "B1", "A12", "B12", "S1"]),

        ("DB20G:RJ45", "J2", "RADIO",
         "Connector_RJ:RJ45_Amphenol_RJHSE5380",
         460, 120, [str(i) for i in range(1, 10)]),

        ("DB20G:RJ45", "J3", "HANDSET",
         "Connector_RJ:RJ45_Amphenol_RJHSE5380",
         520, 120, [str(i) for i in range(1, 10)]),

        # === Transistors ===
        ("DB20G:2N2222A", "Q1", "2N2222A",
         "Package_TO_SOT_THT:TO-92_Inline",
         400, 190, ["1", "2", "3"]),

        ("DB20G:2N2222A", "Q2", "2N2222A",
         "Package_TO_SOT_THT:TO-92_Inline",
         440, 190, ["1", "2", "3"]),

        # === Relay ===
        ("DB20G:Relay_DPDT", "K1", "G5V-2-DC5",
         "Relay_THT:Relay_DPDT_Omron_G5V-2",
         480, 190, [str(i) for i in range(1, 9)]),

        # === Diodes ===
        ("DB20G:D", "D1", "1N4148",
         "Diode_SMD:D_SOD-323",
         520, 190, ["1", "2"]),

        ("DB20G:D", "D2", "1N5819",
         "Diode_SMD:D_SOD-323",
         550, 190, ["1", "2"]),

        # === Fuse ===
        ("DB20G:Polyfuse", "F1", "500mA",
         "Fuse:Fuse_1812_4532Metric",
         580, 190, ["1", "2"]),

        # === Crystal ===
        ("DB20G:Crystal", "Y1", "12MHz",
         "Crystal:Crystal_HC49-4H_Vertical",
         400, 240, ["1", "2"]),
    ]

    # === LEDs ===
    led_colors = ["Green", "Red", "Yellow", "Blue"]
    for i, color in enumerate(led_colors):
        comp_defs.append((
            "DB20G:LED", f"LED{i+1}", color,
            "LED_SMD:LED_0805_2012Metric",
            440 + i * 30, 240, ["1", "2"]))

    # === Resistors R1-R15 (updated values from design) ===
    r_values = [
        "10k", "10k", "10k", "1k", "10k",        # R1-5: PTT base, DTR base, RX div hi, RX div lo, TX div hi
        "1k", "330", "330", "330", "330",          # R6-10: TX div lo, LED1-4 current limit
        "5.1k", "5.1k", "10k", "10k", "10k",      # R11-15: CC1, CC2, FE_RST, CTS_PU, CM_RST
    ]
    for i, val in enumerate(r_values):
        col = i % 8
        row = i // 8
        comp_defs.append((
            "DB20G:R", f"R{i+1}", val,
            "Resistor_SMD:R_0805_2012Metric",
            400 + col * 25, 290 + row * 30, ["1", "2"]))

    # === Capacitors C1-C15 ===
    c_values = [
        "100nF", "10uF", "100nF", "10uF", "100nF",   # C1-5: FE VDD, FE bulk, CP VDD, CP REGIN, CM VDD
        "10uF", "22pF", "22pF", "100nF", "100nF",     # C6-10: CM bulk, XI load, XO load, AMS in, AMS out
        "10uF", "1uF", "1uF", "1uF", "1uF",           # C11-15: AMS bulk, VD18OUT, VD33OUT, VREF, CM_VDD18
    ]
    for i, val in enumerate(c_values):
        col = i % 8
        row = i // 8
        comp_defs.append((
            "DB20G:C", f"C{i+1}", val,
            "Capacitor_SMD:C_0805_2012Metric",
            400 + col * 25, 350 + row * 30, ["1", "2"]))

    # === Mounting Holes (2 only — smaller board) ===
    for i in range(2):
        comp_defs.append((
            "DB20G:MountingHole_Pad", f"MH{i+1}", "M3",
            "MountingHole:MountingHole_3.2mm_M3_Pad_Via",
            400 + i * 30, 420, ["1"]))

    # === External Power Input ===
    # D3: Schottky diode — external 5V ORed onto VBUS for phone charging
    comp_defs.append((
        "DB20G:D", "D3", "1N5819",
        "Diode_SMD:D_SOD-323",
        580, 190, ["1", "2"]))

    # J4: 2-pin header — external 5V + GND
    comp_defs.append((
        "DB20G:Conn_01x02", "J4", "EXT_5V",
        "Connector_PinHeader_2.54mm:PinHeader_1x02_P2.54mm_Vertical",
        580, 120, ["1", "2"]))

    # === Power Flags ===
    comp_defs.append(("DB20G:PWR_FLAG", "#FLG01", "PWR_FLAG", "", 400, 460, ["1"]))
    comp_defs.append(("DB20G:PWR_FLAG", "#FLG02", "PWR_FLAG", "", 430, 460, ["1"]))
    comp_defs.append(("DB20G:PWR_FLAG", "#FLG03", "PWR_FLAG", "", 460, 460, ["1"]))

    # ================================================================
    # BUILD COMPONENTS AND NET CONNECTIVITY
    # For each component pin with a net assignment, create:
    #   - A short wire stub (2.54mm) extending outward from the pin
    #   - A global_label at the stub endpoint with the net name
    # For pins with None assignment, create a no_connect marker.
    # ================================================================
    components = []
    all_wires = []
    all_labels = []
    all_no_connects = []

    for lib_id, ref, value, footprint, cx, cy, pins in comp_defs:
        # Snap component center to 1.27mm grid so all pin positions land on grid
        cx = round(cx / 1.27) * 1.27
        cy = round(cy / 1.27) * 1.27

        instance = make_instance(
            lib_id, ref, value, footprint, cx, cy,
            len(pins), pins, project_uuid_path=project_path)
        components.append(instance)

        sym_type = get_symbol_type(lib_id)
        pin_positions = PIN_POS.get(sym_type, {})
        net_assignments = NETS.get(ref, {})

        for pin_num in pins:
            if pin_num not in pin_positions:
                continue
            rx, ry = pin_positions[pin_num]
            abs_x = cx + rx
            abs_y = cy - ry  # Y-flip: symbol Y-up → schematic Y-down

            if pin_num in net_assignments:
                net = net_assignments[pin_num]
                if net is None:
                    # Intentionally unconnected
                    all_no_connects.append(make_no_connect(abs_x, abs_y))
                else:
                    # Connected — add wire stub + label
                    dx, dy = stub_direction(rx, -ry)  # schematic-space offset
                    wx, wy = abs_x + dx, abs_y + dy
                    all_wires.append(make_wire(abs_x, abs_y, wx, wy))
                    ang = lbl_angle(dx, dy)
                    all_labels.append(make_global_label(net, wx, wy, ang))

    # ================================================================
    # ASSEMBLE THE SCHEMATIC FILE
    # ================================================================
    output_lines = []

    # Header
    output_lines.append('(kicad_sch')
    output_lines.append('\t(version 20250114)')
    output_lines.append('\t(generator "eeschema")')
    output_lines.append('\t(generator_version "9.0")')
    output_lines.append(f'\t(uuid "{project_uuid}")')
    output_lines.append('\t(paper "A3")')

    # Title block
    output_lines.append('\t(title_block')
    output_lines.append('\t\t(title "DB20G-Interface")')
    output_lines.append('\t\t(date "2026-03-20")')
    output_lines.append('\t\t(rev "3.0")')
    output_lines.append('\t\t(comment 1 "USB Hub + CP2102N Serial + CM108AH Audio + DPDT Relay Switching")')
    output_lines.append('\t\t(comment 2 "DB20-G GMRS Radio Controller Interface Board")')
    output_lines.append('\t\t(comment 3 "J4 ext power input with D3 Schottky for phone OTG charging")')
    output_lines.append('\t\t(comment 4 "2 mounting holes — compact board; full netlist for quilter")')
    output_lines.append('\t)')

    # lib_symbols section
    output_lines.append('\t(lib_symbols')
    for sym in lib_syms:
        for line in sym.split('\n'):
            output_lines.append('\t\t' + line)
    output_lines.append('\t)')

    # Wires
    for wire in all_wires:
        output_lines.append(wire)

    # No-connect markers
    for nc in all_no_connects:
        output_lines.append(nc)

    # Global labels
    for lbl in all_labels:
        output_lines.append(lbl)

    # Component instances
    for comp in components:
        output_lines.append(comp)

    # Sheet instances
    output_lines.append('\t(sheet_instances')
    output_lines.append(f'\t\t(path "/{project_uuid}"')
    output_lines.append('\t\t\t(page "1")')
    output_lines.append('\t\t)')
    output_lines.append('\t)')

    # Close
    output_lines.append('\t(embedded_fonts no)')
    output_lines.append(')')

    # Write file
    output_path = r"c:\Code\Radio\DB20-G-APP\hardware\kicad\DB20G-Interface-v9.kicad_sch"
    with open(output_path, 'w', encoding='utf-8', newline='\n') as f:
        f.write('\n'.join(output_lines))

    # Statistics
    unique_nets = set()
    for ref_nets in NETS.values():
        for n in ref_nets.values():
            if n is not None:
                unique_nets.add(n)
    nc_count = sum(1 for ref_nets in NETS.values() for n in ref_nets.values() if n is None)

    print(f"Generated: {output_path}")
    print(f"Total components: {len(components)}")
    print(f"Total lib_symbols: {len(lib_syms)}")
    print(f"Total wires (stubs): {len(all_wires)}")
    print(f"Total global labels: {len(all_labels)}")
    print(f"Total no-connects: {len(all_no_connects)}")
    print(f"Unique nets: {len(unique_nets)}")
    print(f"Net names: {sorted(unique_nets)}")
    print(f"Total output lines: {len(output_lines)}")


if __name__ == "__main__":
    main()
