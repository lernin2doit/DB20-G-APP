# DB20G Interface Board — ESP32 Firmware

PlatformIO-based firmware for the DB20G v10 Bluetooth interface board.

## Hardware

- **MCU**: ESP32-WROOM-32E (4 MB flash, BT Classic + BLE + WiFi)
- **Board revision**: v10 (see `hardware/kicad/DB20G-Interface-v10.kicad_sch`)
- **Pin mapping**: see `include/pins.h`

## Building

```bash
# Install PlatformIO CLI (if not already)
pip install platformio

# Build
cd firmware
pio run

# Flash (connect USB-UART adapter to J5: 3.3V, TX, RX, GND)
# Hold BOOT button (or ground GPIO0) while pressing EN/reset to enter flash mode
pio run -t upload

# Serial monitor (115200 baud debug output on J5)
pio device monitor
```

## Architecture

The firmware acts as a bridge between the Android phone (Bluetooth) and the
DB20-G radio (wired via RJ-45).

### Data Flow

```
Phone App ──BT SPP──→ ESP32 ──UART2──→ K1 relay ──→ Radio (serial mode)
Phone App ──BT SCO──→ ESP32 ──DAC───→ K1 relay ──→ Radio (audio mode)
Radio ──→ K1 relay ──UART2──→ ESP32 ──BT SPP──→ Phone App (serial mode)
Radio ──→ K1 relay ──ADC────→ ESP32 ──BT SCO──→ Phone App (audio mode)
```

### Command Protocol

Commands from the phone arrive over BT SPP with this framing:

| Byte 0 | Byte 1-2     | Payload        | Description              |
|--------|-------------|----------------|--------------------------|
| `0x01` | len (16-bit BE) | raw bytes   | Serial data → radio UART |
| `0x02` | value       | —              | PTT: 0x01=down, 0x00=up |
| `0x03` | value       | —              | Relay: 0x01=serial, 0x00=audio |

Data from the radio UART is forwarded back to the phone as raw bytes
(no framing — the app knows the protocol and reads accordingly).

## Flashing

### Via J5 UART Header

1. Connect a 3.3V USB-UART adapter (e.g. CP2102 or CH340) to J5:
   - J5.1 → 3.3V (optional — board has its own regulator)
   - J5.2 → adapter RX
   - J5.3 → adapter TX
   - J5.4 → adapter GND
2. Ground GPIO0 (or press/hold a BOOT button if installed)
3. Press reset (or power-cycle)
4. Run `pio run -t upload`

### First-Time Setup

After flashing, the ESP32 will appear as a Bluetooth device named
`DB20G-Interface`. Pair it from your phone's Bluetooth settings, then
select it in the DB20-G Controller app.
