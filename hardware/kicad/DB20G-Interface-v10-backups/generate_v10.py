#!/usr/bin/env python3
"""
Generate a fresh KiCad 9.0 schematic for DB20G-Interface v10 (Bluetooth).

v10 ARCHITECTURE REDESIGN:
  - ESP32-WROOM-32E replaces FE1.1s + CP2102N + CM108AH + Y1
  - Bluetooth Classic for SPP serial + HFP/A2DP audio
  - Board powered externally (vehicle 12V, radio, or USB adapter)
  - Phone connects wirelessly — USB port stays free for charging

REMOVED from v9:
  - U1 FE1.1s (USB hub)
  - U2 CP2102N (USB-UART bridge)
  - U3 CM108AH (USB audio codec)
  - Y1 12MHz crystal + C7,C8 load caps
  - J1 USB-C connector
  - R11,R12 (CC pull-downs)
  - R13 (FE reset pull-up), R14 (CTS pull-up), R15 (CM reset pull-up)
  - C1-C8, C12-C15 (IC-specific bypass/bulk caps)
  - D3 (Schottky for phone charging)
  - Nets: VD18OUT, VD33OUT, XI, XO, FE_RESET, HUB_P1_DM, HUB_P1_DP,
    HUB_P2_DM, HUB_P2_DP, USB_DP, USB_DM, VBUS, CC1, CC2, EXT_5V,
    CP_TXD, CP_RXD, CP_TXLED, CTS_PU, PTT_CTRL (renamed), DTR_CTRL (renamed),
    CM_VDD18, CM_VREF, CM_GPIO4, CM_RESET, AUDIO_OUT

ADDED in v10:
  - U1 ESP32-WROOM-32E (MCU + BT + WiFi)
  - J5 1x4 pin header (UART flash: 3.3V, TX, RX, GND)
  - R11 10k (EN pull-up), R12 10k (GPIO0 pull-up)
  - C7 100nF (EN decoupling), C8 22uF (ESP32 bulk)
  - Nets: ESP_TX2, ESP_RX2, ESP_DAC1, ESP_ADC0, ESP_PTT, ESP_RELAY,
    ESP_LED1, ESP_LED2, ESP_LED3, ESP_LED4, ESP_TX0, ESP_RX0, ESP_EN, ESP_IO0

KEPT (modified):
  - U2 AMS1117-3.3 (renumbered from U4)
  - K1 DPDT relay, Q1/Q2 transistors, D1/D2 diodes
  - J2/J3 RJ-45, J4 external power
  - F1 polyfuse, LEDs, MH1-2
  - R1-R6 (base/divider resistors — values adjusted for 3.3V GPIO)
  - R7-R10 (LED resistors — reduced to 220Ω for 3.3V)
  - C1-C6, C9 (reduced decoupling set)
"""

import uuid
import textwrap

def make_uuid():
    return str(uuid.uuid4())


# ============================================================
# SYMBOL LIBRARY DEFINITIONS
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
    """AMS1117-3.3 voltage regulator - SOT-223 - 3 pins"""
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


def sym_Conn_01x02():
    """Generic 2-pin connector header"""
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


def sym_Conn_01x04():
    """Generic 4-pin connector header (for UART flash)"""
    return textwrap.dedent("""\
    (symbol "DB20G:Conn_01x04"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "J"
        (at 0 6.35 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "Conn_01x04"
        (at 0 -6.35 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" ""
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "Generic connector, single row, 4 pins"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "Conn_01x04_0_1"
        (rectangle
          (start -1.27 5.08)
          (end 1.27 -5.08)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "Conn_01x04_1_1"
        (pin passive line (at -3.81 3.81 0) (length 2.54) (name "1" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin passive line (at -3.81 1.27 0) (length 2.54) (name "2" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin passive line (at -3.81 -1.27 0) (length 2.54) (name "3" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin passive line (at -3.81 -3.81 0) (length 2.54) (name "4" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
      )
      (embedded_fonts no)
    )""")


def sym_ESP32_WROOM_32E():
    """ESP32-WROOM-32E WiFi+BT module - 38 pins.
    
    Pin layout based on ESP32-WROOM-32E datasheet.
    Left side: power + control + UART0 + general GPIO
    Right side: ADC/DAC + SPI + remaining GPIO
    Bottom: GND pad
    """
    return textwrap.dedent("""\
    (symbol "DB20G:ESP32-WROOM-32E"
      (pin_names
        (offset 1.016))
      (exclude_from_sim no)
      (in_bom yes)
      (on_board yes)
      (property "Reference" "U"
        (at 0 29.21 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Value" "ESP32-WROOM-32E"
        (at 0 -29.21 0)
        (effects (font (size 1.27 1.27)))
      )
      (property "Footprint" "RF_Module:ESP32-WROOM-32E"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Datasheet" "https://www.espressif.com/sites/default/files/documentation/esp32-wroom-32e_esp32-wroom-32ue_datasheet_en.pdf"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (property "Description" "ESP32-WROOM-32E WiFi+BT+BLE MCU Module"
        (at 0 0 0)
        (effects (font (size 1.27 1.27)) (hide yes))
      )
      (symbol "ESP32-WROOM-32E_0_1"
        (rectangle
          (start -12.7 27.94)
          (end 12.7 -27.94)
          (stroke (width 0.254) (type default))
          (fill (type background))
        )
      )
      (symbol "ESP32-WROOM-32E_1_1"
        (pin power_in line (at -15.24 25.4 0) (length 2.54) (name "3V3" (effects (font (size 1.0 1.0)))) (number "1" (effects (font (size 1.0 1.0)))))
        (pin input line (at -15.24 22.86 0) (length 2.54) (name "EN" (effects (font (size 1.0 1.0)))) (number "2" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 20.32 0) (length 2.54) (name "IO36/VP" (effects (font (size 1.0 1.0)))) (number "3" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 17.78 0) (length 2.54) (name "IO39/VN" (effects (font (size 1.0 1.0)))) (number "4" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 15.24 0) (length 2.54) (name "IO34" (effects (font (size 1.0 1.0)))) (number "5" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 12.7 0) (length 2.54) (name "IO35" (effects (font (size 1.0 1.0)))) (number "6" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 10.16 0) (length 2.54) (name "IO32" (effects (font (size 1.0 1.0)))) (number "7" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 7.62 0) (length 2.54) (name "IO33" (effects (font (size 1.0 1.0)))) (number "8" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 5.08 0) (length 2.54) (name "IO25/DAC1" (effects (font (size 1.0 1.0)))) (number "9" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 2.54 0) (length 2.54) (name "IO26/DAC2" (effects (font (size 1.0 1.0)))) (number "10" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 0 0) (length 2.54) (name "IO27" (effects (font (size 1.0 1.0)))) (number "11" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -2.54 0) (length 2.54) (name "IO14" (effects (font (size 1.0 1.0)))) (number "12" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -5.08 0) (length 2.54) (name "IO12" (effects (font (size 1.0 1.0)))) (number "13" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at -15.24 -7.62 0) (length 2.54) (name "GND" (effects (font (size 1.0 1.0)))) (number "14" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -10.16 0) (length 2.54) (name "IO13" (effects (font (size 1.0 1.0)))) (number "15" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 -10.16 180) (length 2.54) (name "IO9/SD2" (effects (font (size 1.0 1.0)))) (number "16" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 -7.62 180) (length 2.54) (name "IO10/SD3" (effects (font (size 1.0 1.0)))) (number "17" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 -5.08 180) (length 2.54) (name "IO11/CMD" (effects (font (size 1.0 1.0)))) (number "18" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at 15.24 -2.54 180) (length 2.54) (name "VIN_5V" (effects (font (size 1.0 1.0)))) (number "19" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 0 180) (length 2.54) (name "IO6/CLK" (effects (font (size 1.0 1.0)))) (number "20" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 2.54 180) (length 2.54) (name "IO7/SD0" (effects (font (size 1.0 1.0)))) (number "21" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 5.08 180) (length 2.54) (name "IO8/SD1" (effects (font (size 1.0 1.0)))) (number "22" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 7.62 180) (length 2.54) (name "IO15" (effects (font (size 1.0 1.0)))) (number "23" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 10.16 180) (length 2.54) (name "IO2" (effects (font (size 1.0 1.0)))) (number "24" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 12.7 180) (length 2.54) (name "IO0" (effects (font (size 1.0 1.0)))) (number "25" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 15.24 180) (length 2.54) (name "IO4" (effects (font (size 1.0 1.0)))) (number "26" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 17.78 180) (length 2.54) (name "IO16/RX2" (effects (font (size 1.0 1.0)))) (number "27" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 20.32 180) (length 2.54) (name "IO17/TX2" (effects (font (size 1.0 1.0)))) (number "28" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 22.86 180) (length 2.54) (name "IO5" (effects (font (size 1.0 1.0)))) (number "29" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 25.4 180) (length 2.54) (name "IO18" (effects (font (size 1.0 1.0)))) (number "30" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at 15.24 -12.7 180) (length 2.54) (name "IO23" (effects (font (size 1.0 1.0)))) (number "31" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -12.7 0) (length 2.54) (name "IO19" (effects (font (size 1.0 1.0)))) (number "32" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -15.24 0) (length 2.54) (name "NC" (effects (font (size 1.0 1.0)))) (number "33" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -17.78 0) (length 2.54) (name "IO21" (effects (font (size 1.0 1.0)))) (number "34" (effects (font (size 1.0 1.0)))))
        (pin output line (at -15.24 -20.32 0) (length 2.54) (name "IO3/RX0" (effects (font (size 1.0 1.0)))) (number "35" (effects (font (size 1.0 1.0)))))
        (pin input line (at -15.24 -22.86 0) (length 2.54) (name "IO1/TX0" (effects (font (size 1.0 1.0)))) (number "36" (effects (font (size 1.0 1.0)))))
        (pin bidirectional line (at -15.24 -25.4 0) (length 2.54) (name "IO22" (effects (font (size 1.0 1.0)))) (number "37" (effects (font (size 1.0 1.0)))))
        (pin power_in line (at 0 -30.48 90) (length 2.54) (name "GND_PAD" (effects (font (size 1.0 1.0)))) (number "38" (effects (font (size 1.0 1.0)))))
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
# ============================================================

PIN_POS = {
    "2N2222A": {"1": (0, 0), "2": (2.54, 2.54), "3": (2.54, -2.54)},
    "AMS1117-3.3": {"1": (-7.62, 0), "2": (7.62, 0), "3": (-7.62, -2.54)},
    "C": {"1": (0, 3.81), "2": (0, -3.81)},
    "R": {"1": (0, 3.81), "2": (0, -3.81)},
    "LED": {"1": (-3.81, 0), "2": (3.81, 0)},
    "D": {"1": (-3.81, 0), "2": (3.81, 0)},
    "Polyfuse": {"1": (-3.81, 0), "2": (3.81, 0)},
    "MountingHole_Pad": {"1": (0, -2.54)},
    "Conn_01x02": {"1": (-3.81, 1.27), "2": (-3.81, -1.27)},
    "Conn_01x04": {"1": (-3.81, 3.81), "2": (-3.81, 1.27), "3": (-3.81, -1.27), "4": (-3.81, -3.81)},
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
    "ESP32-WROOM-32E": {
        # Left side (pin at -15.24, various Y)
        "1": (-15.24, 25.4),    # 3V3
        "2": (-15.24, 22.86),   # EN
        "3": (-15.24, 20.32),   # IO36/VP (ADC1_CH0 — RX audio in)
        "4": (-15.24, 17.78),   # IO39/VN
        "5": (-15.24, 15.24),   # IO34
        "6": (-15.24, 12.7),    # IO35
        "7": (-15.24, 10.16),   # IO32
        "8": (-15.24, 7.62),    # IO33
        "9": (-15.24, 5.08),    # IO25/DAC1 (TX audio out)
        "10": (-15.24, 2.54),   # IO26/DAC2
        "11": (-15.24, 0),      # IO27
        "12": (-15.24, -2.54),  # IO14
        "13": (-15.24, -5.08),  # IO12
        "14": (-15.24, -7.62),  # GND
        "15": (-15.24, -10.16), # IO13
        "32": (-15.24, -12.7),  # IO19
        "33": (-15.24, -15.24), # NC
        "34": (-15.24, -17.78), # IO21
        "35": (-15.24, -20.32), # IO3/RX0
        "36": (-15.24, -22.86), # IO1/TX0
        "37": (-15.24, -25.4),  # IO22
        # Right side (pin at 15.24, various Y)
        "16": (15.24, -10.16),  # IO9/SD2
        "17": (15.24, -7.62),   # IO10/SD3
        "18": (15.24, -5.08),   # IO11/CMD
        "19": (15.24, -2.54),   # VIN_5V
        "20": (15.24, 0),       # IO6/CLK
        "21": (15.24, 2.54),    # IO7/SD0
        "22": (15.24, 5.08),    # IO8/SD1
        "23": (15.24, 7.62),    # IO15
        "24": (15.24, 10.16),   # IO2
        "25": (15.24, 12.7),    # IO0
        "26": (15.24, 15.24),   # IO4
        "27": (15.24, 17.78),   # IO16/RX2
        "28": (15.24, 20.32),   # IO17/TX2
        "29": (15.24, 22.86),   # IO5
        "30": (15.24, 25.4),    # IO18
        "31": (15.24, -12.7),   # IO23
        # Bottom
        "38": (0, -30.48),      # GND_PAD
    },
}


# ============================================================
# NET ASSIGNMENTS PER COMPONENT
# None = intentionally unconnected (no_connect marker)
# ============================================================

NETS = {
    # ---- ESP32-WROOM-32E ----
    "U1": {
        "1": "+3.3V",               # 3V3 power
        "2": "ESP_EN",              # EN (chip enable) — pull-up R11 + C7
        "3": "ESP_ADC0",            # IO36/VP → ADC1_CH0 — RX audio in from radio via divider
        "4": None,                  # IO39/VN — unused (input-only)
        "5": None,                  # IO34 — unused (input-only)
        "6": None,                  # IO35 — unused (input-only)
        "7": None,                  # IO32 — unused (reserve for future)
        "8": None,                  # IO33 — unused (reserve for future)
        "9": "ESP_DAC1",            # IO25/DAC1 — TX audio out to radio via divider
        "10": None,                 # IO26/DAC2 — unused
        "11": None,                 # IO27 — unused
        "12": None,                 # IO14 — unused
        "13": None,                 # IO12 — CAUTION: must be LOW at boot (MTDI/JTAG)
        "14": "GND",                # GND
        "15": None,                 # IO13 — unused
        "16": None,                 # IO9/SD2 — internal flash, do not use
        "17": None,                 # IO10/SD3 — internal flash, do not use
        "18": None,                 # IO11/CMD — internal flash, do not use
        "19": None,                 # VIN_5V — not used (we power via 3V3 pin)
        "20": None,                 # IO6/CLK — internal flash, do not use
        "21": None,                 # IO7/SD0 — internal flash, do not use
        "22": None,                 # IO8/SD1 — internal flash, do not use
        "23": None,                 # IO15 — unused (MTDO, pulled up internally)
        "24": None,                 # IO2 — unused (must be LOW for boot from serial, leave floating)
        "25": "ESP_IO0",            # IO0 — boot mode select, pull-up R12
        "26": "ESP_PTT",            # IO4 — PTT driver → R1 → Q1 base
        "27": "ESP_RX2",            # IO16/RX2 — UART2 RX from radio via K1 P2_NO
        "28": "ESP_TX2",            # IO17/TX2 — UART2 TX to radio via K1 P1_NO
        "29": "ESP_RELAY",          # IO5 — Relay driver → R2 → Q2 base
        "30": "ESP_LED1",           # IO18 — LED1 (Power) via R7
        "31": None,                 # IO23 — unused
        "32": "ESP_LED2",           # IO19 — LED2 (PTT active) via R8
        "33": None,                 # NC pin
        "34": "ESP_LED3",           # IO21 — LED3 (Audio activity) via R9
        "35": "ESP_RX0",            # IO3/RX0 — Debug UART RX from J5
        "36": "ESP_TX0",            # IO1/TX0 — Debug UART TX to J5
        "37": "ESP_LED4",           # IO22 — LED4 (BT connected) via R10
        "38": "GND",                # GND pad
    },

    # ---- AMS1117-3.3 LDO ----
    "U2": {
        "1": "GND",                 # GND/Adj
        "2": "+3.3V",               # VO output
        "3": "+5V",                 # VI input
    },

    # ---- RJ-45 RADIO (to DB20-G handset port) ----
    "J2": {
        "1": "RJ45_P1",             # Radio pin 1 (pass-through)
        "2": "RADIO_MIC",           # MIC/TX to radio ← K1 P1_COM
        "3": "PTT_OUT",             # PTT ← Q1 collector
        "4": "RADIO_SQL",           # Squelch detect (pass-through)
        "5": "RADIO_SPK",           # SPK/RX from radio → K1 P2_COM
        "6": "GND", "7": "GND", "8": "GND",
        "9": "GND",                 # Shield
    },

    # ---- RJ-45 HANDSET (stock handset pass-through) ----
    "J3": {
        "1": "RJ45_P1",
        "2": "RADIO_MIC",
        "3": "PTT_OUT",
        "4": "RADIO_SQL",
        "5": "RADIO_SPK",
        "6": "GND", "7": "GND", "8": "GND",
        "9": "GND",
    },

    # ---- External Power Input ----
    "J4": {"1": "+5V_EXT", "2": "GND"},

    # ---- UART Flash Header (3.3V, TX, RX, GND) ----
    "J5": {
        "1": "+3.3V",               # 3.3V to external USB-UART adapter
        "2": "ESP_TX0",             # ESP32 TX → adapter RX
        "3": "ESP_RX0",             # ESP32 RX ← adapter TX
        "4": "GND",
    },

    # ---- Transistors ----
    "Q1": {"1": "PTT_BASE", "2": "PTT_OUT", "3": "GND"},
    "Q2": {"1": "RELAY_BASE", "2": "RELAY_COIL_LO", "3": "GND"},

    # ---- DPDT Relay ----
    "K1": {
        "1": "+5V",                 # Coil+
        "2": "RELAY_COIL_LO",       # Coil- (driven by Q2)
        "3": "RADIO_MIC",           # P1_COM → J2.2 (TX to radio)
        "4": "ESP_TX2",             # P1_NO  → ESP32 UART2 TX (serial mode)
        "5": "AUDIO_TX_DIV",        # P1_NC  → R5/R6 divider (audio mode)
        "6": "RADIO_SPK",           # P2_COM → J2.5 (RX from radio)
        "7": "ESP_RX2",             # P2_NO  → ESP32 UART2 RX (serial mode)
        "8": "AUDIO_RX",            # P2_NC  → R3 input (audio mode)
    },

    # ---- Diodes ----
    "D1": {"1": "+5V", "2": "PTT_OUT"},        # PTT clamp diode
    "D2": {"1": "+5V", "2": "RELAY_COIL_LO"},  # Flyback diode across K1 coil

    # ---- Polyfuse ----
    "F1": {"1": "+5V_EXT", "2": "+5V"},        # External input → fused +5V rail

    # ---- Resistors ----
    # R1-R2: Transistor base resistors (ESP32 GPIO is 3.3V, 10k gives ~0.33mA base)
    "R1":  {"1": "ESP_PTT",     "2": "PTT_BASE"},       # 10k: GPIO4 → Q1 base
    "R2":  {"1": "ESP_RELAY",   "2": "RELAY_BASE"},     # 10k: GPIO5 → Q2 base
    # R3-R6: Audio dividers (adjusted for ESP32 ADC 0-3.3V range)
    "R3":  {"1": "AUDIO_RX",    "2": "AUDIO_RX_DIV"},   # 10k: RX divider upper
    "R4":  {"1": "AUDIO_RX_DIV","2": "GND"},             # 4.7k: RX divider lower (gives ~1.1V from 3.3V)
    "R5":  {"1": "ESP_DAC1",    "2": "AUDIO_TX_DIV"},   # 10k: TX divider upper
    "R6":  {"1": "AUDIO_TX_DIV","2": "GND"},             # 4.7k: TX divider lower
    # R7-R10: LED current limiting (220Ω for 3.3V GPIO → ~10mA)
    "R7":  {"1": "ESP_LED1",  "2": "LED1_R"},           # 220Ω: LED1 (Power)
    "R8":  {"1": "ESP_LED2",  "2": "LED2_R"},           # 220Ω: LED2 (PTT)
    "R9":  {"1": "ESP_LED3",  "2": "LED3_R"},           # 220Ω: LED3 (Audio)
    "R10": {"1": "ESP_LED4",  "2": "LED4_R"},           # 220Ω: LED4 (BT Status)
    # R11-R12: ESP32 boot/enable pull-ups
    "R11": {"1": "+3.3V",    "2": "ESP_EN"},            # 10k: EN pin pull-up
    "R12": {"1": "+3.3V",    "2": "ESP_IO0"},           # 10k: GPIO0 pull-up (run mode)

    # ---- LEDs (pin 1=Cathode, pin 2=Anode) ----
    "LED1": {"1": "GND",       "2": "LED1_R"},   # Power (Green)
    "LED2": {"1": "PTT_OUT",   "2": "LED2_R"},   # PTT (Red): lights when PTT active
    "LED3": {"1": "GND",       "2": "LED3_R"},   # Audio (Yellow): ESP32 drives directly
    "LED4": {"1": "GND",       "2": "LED4_R"},   # BT Status (Blue): ESP32 drives directly

    # ---- Capacitors ----
    "C1":  {"1": "+5V",     "2": "GND"},   # 100nF: AMS1117 input bypass
    "C2":  {"1": "+5V",     "2": "GND"},   # 10uF:  AMS1117 input bulk
    "C3":  {"1": "+3.3V",   "2": "GND"},   # 100nF: AMS1117 output bypass
    "C4":  {"1": "+3.3V",   "2": "GND"},   # 10uF:  AMS1117 output bulk
    "C5":  {"1": "+3.3V",   "2": "GND"},   # 100nF: ESP32 3V3 bypass (close to pin 1)
    "C6":  {"1": "+3.3V",   "2": "GND"},   # 22uF:  ESP32 3V3 bulk decoupling
    "C7":  {"1": "ESP_EN",  "2": "GND"},   # 100nF: EN pin RC delay for stable boot
    "C8":  {"1": "ESP_ADC0","2": "GND"},   # 100nF: ADC input filtering
    "C9":  {"1": "+5V_EXT", "2": "GND"},   # 100nF: Input power filtering

    # ---- Mounting Holes ----
    "MH1": {"1": "GND"}, "MH2": {"1": "GND"},

    # ---- Power Flags ----
    "#FLG01": {"1": "+5V"},
    "#FLG02": {"1": "GND"},
    "#FLG03": {"1": "+3.3V"},
}


# ============================================================
# HELPERS
# ============================================================

def get_symbol_type(lib_id):
    """Extract symbol type from lib_id."""
    return lib_id.split(":")[-1]


def stub_direction(rx, ry):
    """Determine wire stub direction from pin's relative position."""
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
        sym_2N2222A(), sym_AMS1117(), sym_C(), sym_Conn_01x02(),
        sym_Conn_01x04(), sym_D(), sym_ESP32_WROOM_32E(), sym_LED(),
        sym_MountingHole_Pad(), sym_PWR_FLAG(), sym_Polyfuse(), sym_R(),
        sym_RJ45(), sym_Relay_DPDT(),
    ]

    # ================================================================
    # COMPONENT DEFINITIONS
    # (lib_id, ref, value, footprint, x, y, pin_list)
    # ================================================================
    comp_defs = [
        # === ICs ===
        ("DB20G:ESP32-WROOM-32E", "U1", "ESP32-WROOM-32E",
         "RF_Module:ESP32-WROOM-32E",
         400, 70, [str(i) for i in range(1, 39)]),

        ("DB20G:AMS1117-3.3", "U2", "AMS1117-3.3",
         "Package_TO_SOT_SMD:SOT-223-3_TabPin2",
         500, 50, ["1", "2", "3"]),

        # === Connectors ===
        ("DB20G:RJ45", "J2", "RADIO",
         "Connector_RJ:RJ45_Amphenol_RJHSE5380",
         400, 160, [str(i) for i in range(1, 10)]),

        ("DB20G:RJ45", "J3", "HANDSET",
         "Connector_RJ:RJ45_Amphenol_RJHSE5380",
         460, 160, [str(i) for i in range(1, 10)]),

        ("DB20G:Conn_01x02", "J4", "EXT_PWR",
         "Connector_PinHeader_2.54mm:PinHeader_1x02_P2.54mm_Vertical",
         520, 160, ["1", "2"]),

        ("DB20G:Conn_01x04", "J5", "UART_FLASH",
         "Connector_PinHeader_2.54mm:PinHeader_1x04_P2.54mm_Vertical",
         560, 160, ["1", "2", "3", "4"]),

        # === Transistors ===
        ("DB20G:2N2222A", "Q1", "2N2222A",
         "Package_TO_SOT_THT:TO-92_Inline",
         400, 230, ["1", "2", "3"]),

        ("DB20G:2N2222A", "Q2", "2N2222A",
         "Package_TO_SOT_THT:TO-92_Inline",
         440, 230, ["1", "2", "3"]),

        # === Relay ===
        ("DB20G:Relay_DPDT", "K1", "G5V-2-DC5",
         "Relay_THT:Relay_DPDT_Omron_G5V-2",
         500, 230, [str(i) for i in range(1, 9)]),

        # === Diodes ===
        ("DB20G:D", "D1", "1N4148",
         "Diode_SMD:D_SOD-323",
         560, 230, ["1", "2"]),

        ("DB20G:D", "D2", "1N5819",
         "Diode_SMD:D_SOD-323",
         590, 230, ["1", "2"]),

        # === Fuse ===
        ("DB20G:Polyfuse", "F1", "500mA",
         "Fuse:Fuse_1812_4532Metric",
         400, 280, ["1", "2"]),
    ]

    # === LEDs ===
    led_colors = ["Green", "Red", "Yellow", "Blue"]
    for i, color in enumerate(led_colors):
        comp_defs.append((
            "DB20G:LED", f"LED{i+1}", color,
            "LED_SMD:LED_0805_2012Metric",
            440 + i * 30, 280, ["1", "2"]))

    # === Resistors R1-R12 ===
    r_values = [
        "10k", "10k",           # R1-2: PTT base, Relay base
        "10k", "4.7k",          # R3-4: RX audio divider
        "10k", "4.7k",          # R5-6: TX audio divider
        "220", "220", "220", "220",  # R7-10: LED current limiting (3.3V GPIO)
        "10k", "10k",           # R11-12: EN pull-up, IO0 pull-up
    ]
    for i, val in enumerate(r_values):
        col = i % 6
        row = i // 6
        comp_defs.append((
            "DB20G:R", f"R{i+1}", val,
            "Resistor_SMD:R_0805_2012Metric",
            400 + col * 25, 330 + row * 30, ["1", "2"]))

    # === Capacitors C1-C9 ===
    c_values = [
        "100nF", "10uF",        # C1-2: AMS1117 input bypass + bulk
        "100nF", "10uF",        # C3-4: AMS1117 output bypass + bulk
        "100nF", "22uF",        # C5-6: ESP32 3V3 bypass + bulk
        "100nF",                # C7: EN pin RC delay
        "100nF",                # C8: ADC input filter
        "100nF",                # C9: Input power filter
    ]
    for i, val in enumerate(c_values):
        col = i % 5
        row = i // 5
        comp_defs.append((
            "DB20G:C", f"C{i+1}", val,
            "Capacitor_SMD:C_0805_2012Metric",
            400 + col * 25, 400 + row * 30, ["1", "2"]))

    # === Mounting Holes ===
    for i in range(2):
        comp_defs.append((
            "DB20G:MountingHole_Pad", f"MH{i+1}", "M3",
            "MountingHole:MountingHole_3.2mm_M3_Pad_Via",
            400 + i * 30, 470, ["1"]))

    # === Power Flags ===
    comp_defs.append(("DB20G:PWR_FLAG", "#FLG01", "PWR_FLAG", "", 400, 500, ["1"]))
    comp_defs.append(("DB20G:PWR_FLAG", "#FLG02", "PWR_FLAG", "", 430, 500, ["1"]))
    comp_defs.append(("DB20G:PWR_FLAG", "#FLG03", "PWR_FLAG", "", 460, 500, ["1"]))

    # ================================================================
    # BUILD COMPONENTS AND NET CONNECTIVITY
    # ================================================================
    components = []
    all_wires = []
    all_labels = []
    all_no_connects = []

    for lib_id, ref, value, footprint, cx, cy, pins in comp_defs:
        # Snap to 1.27mm grid
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
                    all_no_connects.append(make_no_connect(abs_x, abs_y))
                else:
                    dx, dy = stub_direction(rx, -ry)
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
    output_lines.append('\t\t(title "DB20G-Interface v10 Bluetooth")')
    output_lines.append('\t\t(date "2026-03-20")')
    output_lines.append('\t\t(rev "10.0")')
    output_lines.append('\t\t(comment 1 "ESP32-WROOM-32E Bluetooth + DPDT Relay Switching")')
    output_lines.append('\t\t(comment 2 "DB20-G GMRS Radio Controller — Wireless Interface Board")')
    output_lines.append('\t\t(comment 3 "BT Classic SPP serial + HFP audio — phone connects wirelessly")')
    output_lines.append('\t\t(comment 4 "External 5V power; UART header for firmware flash")')
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
    output_path = r"c:\Code\Radio\DB20-G-APP\hardware\kicad\DB20G-Interface-v10.kicad_sch"
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
