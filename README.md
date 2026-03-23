# DB20-G Controller

**Open-source Android app and Bluetooth interface hardware for programming and operating the Radioddity DB20-G GMRS mobile radio.**

> **⚠️ PROJECT STATUS: EARLY DEVELOPMENT — UNTESTED & INCOMPLETE**
>
> This project is in early development and is **not ready for production use**. Both the Android app and the v10 Bluetooth interface hardware exist only as designs at this point — **neither has been fully tested**. The app contains numerous known bugs that still need to be resolved, and to our knowledge **no one has built the hardware yet** for proper end-to-end testing. If you choose to build or use any part of this project, you do so entirely at your own risk. Contributions and testing feedback are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

Connect your Android phone to your DB20-G wirelessly over Bluetooth — program channels, manage repeaters, key PTT, and stream live audio, all from a beginner-friendly interface designed around real GMRS community feedback. Your phone stays on its charger while the interface box talks to the radio.

---

## Overview

The DB20-G Controller project is a complete ecosystem for GMRS radio operation:

- **Android App** — Program channels, download/upload radio memory, operate live with PTT, search repeaters, stream audio, and more
- **Bluetooth Interface Box** — Custom open-hardware PCB with ESP32 that wirelessly bridges your phone to the radio (serial + audio + PTT over Bluetooth)
- **ESP32 Firmware** — PlatformIO-based firmware for the interface box (BT SPP, audio streaming, OTA updates)
- **3D-Printable Enclosure** — Parametric OpenSCAD design for housing the interface board

The Radioddity DB20-G is a popular 20-watt GMRS mobile radio, but programming it has been the #1 user complaint across every GMRS community. This project solves that with a 60-second setup wizard, live repeater database, and FCC-compliant validation — no PC required.

## Features

### App

- **60-Second Setup Wizard** — Callsign entry, location permission, radio auto-detect, one-tap programming
- **Channel Management** — Edit all 500 channels with inline validation, frequency/power/tone checking
- **GMRS Channel Templates** — Standard 22, Emergency Kit, Family Pack presets
- **RepeaterBook Integration** — Search repeaters by location, radius, or frequency with local caching
- **Travel Route Planner** — Auto-generate optimal repeater lists for road trips with coverage gap detection
- **Live Operation** — PTT keying, DTMF keypad, channel up/down, RX activity detection, VOX
- **Download/Upload** — Read and write full radio memory over USB serial
- **CHIRP Compatibility** — Import/export CHIRP `.csv` and `.img` files
- **FCC Compliance Tools** — License lookup (ULS API), power limit validation, Part 95 quick reference
- **Emergency Mode** — High-contrast SOS beacon with GPS coordinates, dead-man's switch, emergency net checklist
- **Bluetooth PTT** — Wireless PTT button support (BLE HID) for Sena and generic buttons, plus in-app PTT over Bluetooth SPP
- **Scanner & Priority Scan** — Software-controlled scan lists, priority channel, dual-watch
- **Signal Monitoring** — Audio level meter, RSSI history, spectrum analyzer, range test mode
- **Text Messaging** — AFSK Bell 202 (1200 baud) text-over-radio with GPS position sharing
- **SSTV** — Slow-scan TV image send/receive (Robot36, Martin M1)
- **Android Auto** — Simplified driving-safe UI with voice commands and steering wheel PTT
- **Weather Alerts** — NOAA severe weather notifications by location
- **On-Device Translation** — Real-time radio audio translation (English ↔ Spanish) via ML Kit, fully offline
- **Accessibility** — TalkBack/screen reader support, high-contrast mode, configurable font sizes, haptic feedback
- **QSO Logging** — Contact database, net schedules, ADIF/CSV export
- **Multi-Radio Support** — Manage multiple USB serial radios with profiles and cross-radio channel sync

### Hardware (Bluetooth Interface Box — v10)

```
┌───────────┐  Bluetooth  ┌───────────────────────────┐     RJ-45      ┌──────────┐
│  Android  │◄───────────►│   DB20-G Interface Box    │◄──────────────►│  DB20-G  │
│   Phone   │  SPP serial │                           │ (handset port) │   Radio  │
│           │  SCO audio  │  ESP32-WROOM-32E          │                │          │
│           │             │   → BT serial bridge      │                │          │
│           │             │   → DAC/ADC audio          │                │          │
│           │             │   → PTT / relay control    │                │          │
└───────────┘             └───────────────────────────┘                └──────────┘
```

- **Bluetooth Classic** (SPP + SCO) — phone connects wirelessly; no cable to the phone
- **ESP32-WROOM-32E** — single module replaces the former USB hub, UART bridge, and audio codec
- **9600 baud UART bridge** — BT SPP ↔ ESP32 UART2 for the DB20-G GA-510 programming protocol
- **DAC/ADC audio** — 8 kHz 8-bit mono voice; ESP32 DAC (GPIO25) → radio MIC, radio SPK → ESP32 ADC (GPIO36)
- **Transistor + relay PTT** — GPIO-driven PTT with 3-minute FCC timeout; DPDT relay switches serial ↔ audio mode
- **RJ-45 handset pass-through** — Use the hand mic normally while the interface is connected
- **Audio level matching** — Voltage dividers match ESP32 levels to radio mic/speaker levels
- **LED indicators** — Power/heartbeat (green), PTT (red), audio activity (yellow), BT status (blue)
- **OTA firmware updates** — ESP32 WiFi AP mode with HTTP upload for field-upgradeable firmware
- **NVS config** — Persistent storage for TX/RX gain, PTT timeout, BT name
- **External 5 V power** — Powered from radio handset port, USB adapter, or vehicle lighter. Phone stays on its charger.
- **~30 components, ~$5.28 total BOM** — 2-layer FR4 PCB, hand-solderable

## Screenshots

*Coming soon — contributions welcome!*

## Getting Started

### Prerequisites

- **Android device** running Android 8.0+ (API 26) with Bluetooth
- **Radioddity DB20-G** GMRS mobile radio
- **DB20-G Interface Box** ([build your own](#building-the-interface-box)) — ESP32 Bluetooth interface board
- **RJ-45 patch cable** for connecting the interface box to the radio's handset port
- **5 V power source** — USB adapter, vehicle lighter port, or radio handset port power

### Install the App

#### From Source

```bash
git clone https://github.com/lernin2doit/DB20-G-APP.git
cd DB20-G-APP
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`. Transfer it to your Android device and install.

#### Pre-Built APK

Check the [Releases](https://github.com/lernin2doit/DB20-G-APP/releases) page for signed APKs.

### First Use

1. Connect the interface box to your radio's handset port (RJ-45)
2. Power the interface box (5 V via J4 or radio handset port)
3. Open the app and pair with "DB20G-Interface" via Bluetooth
4. Grant Bluetooth permission when prompted
5. The setup wizard will walk you through callsign entry, location, and programming your first channels

> **USB fallback:** The app also supports direct USB serial connection (CP2102, CH340, etc.) for firmware development and bench testing without the Bluetooth interface box.

### Building the Interface Box

Full hardware build documentation is in the [`hardware/`](hardware/) directory:

| Document | Description |
|----------|-------------|
| [hardware/README.md](hardware/README.md) | Overview, block diagram, ESP32 pin assignments |
| [hardware/BOM.md](hardware/BOM.md) | Full bill of materials (~30 components, ~$5.28) |
| [hardware/ASSEMBLY.md](hardware/ASSEMBLY.md) | Step-by-step build guide (5 phases) |
| [hardware/WIRING.md](hardware/WIRING.md) | ESP32 GPIO map, connector pinouts, signal routing |
| [hardware/TROUBLESHOOTING.md](hardware/TROUBLESHOOTING.md) | Common issues and diagnostics |
| [hardware/KICAD-CHANGES.md](hardware/KICAD-CHANGES.md) | v9→v10 schematic change log |

**Tools needed:** Soldering iron with fine tip, solder (0.5 mm recommended), flux, multimeter, USB-UART adapter (for initial ESP32 flashing), 3D printer (for enclosure, optional).

**PCB fabrication:** Upload the KiCad files from `hardware/kicad/` to [JLCPCB](https://jlcpcb.com), [PCBWay](https://www.pcbway.com), or your preferred fab house. Default settings (2-layer, 1.6 mm FR4, HASL) work fine.

**ESP32 firmware:** Flash via J5 UART header using PlatformIO: `cd firmware && pio run --target upload`. See [firmware/README.md](firmware/README.md) for details.

**Enclosure:** Open `hardware/enclosure/DB20G-Enclosure.scad` in [OpenSCAD](https://openscad.org/) and export to STL for 3D printing.

## Building from Source

### Requirements

- **Android Studio** (Hedgehog or newer recommended)
- **JDK 17**
- **Android SDK 34**
- **Gradle 8.7.3** (included via wrapper)

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build (unsigned)
./gradlew assembleRelease

# Run tests
./gradlew test
```

### Project Structure

```
DB20-G-APP/
├── app/src/main/
│   ├── java/com/db20g/controller/
│   │   ├── ui/             # Activities, fragments, adapters (MVVM)
│   │   ├── protocol/       # DB20-G serial protocol, channel/settings data classes
│   │   ├── serial/         # USB serial communication (CP2102, PL2303, FTDI, CH340)
│   │   ├── transport/      # RadioTransport interface, USB + Bluetooth implementations
│   │   ├── repeater/       # RepeaterBook API, repeater database, callsign ID
│   │   ├── audio/          # DTMF, Morse, VOX, audio routing, Bluetooth audio bridge
│   │   ├── auto/           # Android Auto integration
│   │   ├── bluetooth/      # Bluetooth PTT button support, BT SPP transport
│   │   ├── emergency/      # Emergency mode, SOS beacon
│   │   ├── translation/    # On-device ML Kit translation
│   │   ├── compliance/     # FCC license validation, power limit checking
│   │   └── service/        # Foreground service for persistent radio connection
│   ├── res/                # Layouts, drawables, strings (English + Spanish)
│   └── AndroidManifest.xml
├── firmware/               # ESP32 firmware (PlatformIO + Arduino)
│   ├── src/main.cpp        # BT SPP bridge, audio streaming, OTA, NVS
│   ├── include/pins.h      # GPIO pin definitions
│   ├── include/config.h    # Firmware constants
│   └── platformio.ini      # Build configuration
├── hardware/
│   ├── kicad/              # KiCad 9 schematic (v10 ESP32 Bluetooth design)
│   ├── enclosure/          # OpenSCAD 3D-printable enclosure
│   ├── BOM.md              # Bill of materials
│   ├── ASSEMBLY.md         # Build guide
│   ├── WIRING.md           # Pin mappings, signal routing
│   ├── TROUBLESHOOTING.md  # Diagnostic guide
│   ├── KICAD-CHANGES.md    # v9→v10 schematic change log
│   └── archive/            # Archived v9 USB design files
└── ROADMAP.md              # Feature roadmap (P0–P3 + Bluetooth redesign)
```

## Technology Stack

| Component | Technology |
|-----------|-----------|
| Language | Kotlin 2.0 |
| UI | Material Design 3, ViewPager2, RecyclerView |
| Architecture | MVVM (ViewModel + LiveData + Coroutines) |
| Transport | RadioTransport interface (USB serial + Bluetooth SPP) |
| USB Serial | [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) 3.7.3 |
| Location | Google Play Services Location 21.1 |
| Translation | ML Kit Translate 17.0 (on-device) |
| Android Auto | AndroidX Car App 1.4 |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 14 (API 34) |
| Build | Gradle 8.7.3, JDK 17 |
| Firmware | PlatformIO + Arduino framework (ESP32) |
| PCB Design | KiCad 9 |
| Enclosure | OpenSCAD |

## Radio Protocol

The app communicates with the DB20-G using the GA-510 "clone mode" serial protocol:

- **Baud rate:** 9600 8N1
- **Read/write:** 32-byte blocks
- **Total memory:** 7,232 bytes (0x1C40)

| Address Range | Contents |
|---------------|----------|
| `0x0000–0x0800` | Channel data (454 slots × 16 bytes) |
| `0x0C00–0x1400` | Channel names (454 × 16-byte ASCII) |
| `0x1A00–0x1C40` | Radio settings (squelch, VOX, TOT, beep, scan, etc.) |

The clone-mode memory exposes 454 programmable channel slots. The radio's 500-channel capacity includes 30 fixed GMRS channels (1–30), 9 customizable repeater channels (31–39), 454 fully programmable channels (40–493), and 7 NOAA weather channels (494–500). Channels 40–493 can be programmed to any frequency the radio is capable of receiving, including VHF (136–174 MHz) and UHF (400–490 MHz). The 30 GMRS and 7 NOAA channels have fixed frequencies stored in firmware.

Frequencies are stored as 4-byte big-endian BCD. CTCSS/DCS tones use 14-bit indices into standard lookup tables (50 CTCSS tones, 104 DCS codes).

> **Note:** Firmware versions from the manufacturer exist for other jurisdictions that remove the RX-only restrictions, enabling transmit on any programmed frequency. Transmitting outside the GMRS band without an appropriate license (e.g., amateur radio) is illegal under FCC rules and may result in fines or equipment seizure.

## Contributing

Contributions are welcome! Whether it's bug fixes, new features, documentation, hardware improvements, or translations — all help is appreciated.

### How to Contribute

1. **Fork** the repository
2. **Create a branch** for your feature or fix (`git checkout -b feature/my-feature`)
3. **Make your changes** and commit with clear messages
4. **Test** your changes (build the app, verify on a real device if possible)
5. **Open a Pull Request** with a description of what you changed and why

### Areas Where Help Is Needed

- **Testing** on different Android devices and USB adapters
- **Repeater database** contributions (especially regional data)
- **Translations** (Spanish is started; other languages welcome)
- **Hardware testing** and build feedback
- **Documentation** and tutorials
- **UI/UX improvements** and accessibility enhancements

### Code Style

- Kotlin with Android conventions
- MVVM architecture — keep business logic in ViewModels
- Coroutines for async work (no raw threads or AsyncTask)
- Material Design 3 components for UI

### Reporting Issues

Open an issue on GitHub with:
- Android device model and OS version
- USB adapter type (if applicable)
- Steps to reproduce
- Expected vs. actual behavior
- Logcat output (if relevant)

## Roadmap

See [ROADMAP.md](ROADMAP.md) for the full prioritized feature plan. Key areas:

- **P0 (Critical)** — Setup wizard, RepeaterBook integration, validation engine, CHIRP compatibility ✅
- **P1 (High)** — Travel route planner, emergency features, Bluetooth PTT ✅
- **P2 (Medium)** — Scanner, signal monitoring, multi-radio, QSO logging ✅
- **P3 (Nice-to-have)** — Android Auto, SSTV, text messaging, weather, spectrum display ✅
- **BT (v2.0 Redesign)** — ESP32 Bluetooth hardware, firmware, app refactor, docs ✅

## License

This is a dual-licensed open-source project:

- **Software** (Android app, source code) — [MIT License](LICENSE.md)
- **Hardware** (schematics, PCB, enclosure) — [CERN Open Hardware Licence v2 — Permissive (CERN-OHL-P-2.0)](hardware/LICENSE)

See [LICENSE.md](LICENSE.md) for the full software license text.

## Acknowledgments

- **[usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android)** — USB serial driver library by mik3y
- **[RepeaterBook](https://www.repeaterbook.com/)** — GMRS repeater database API
- **[CHIRP](https://chirp.danplanet.com/)** — Cross-platform radio programming tool (file format compatibility)
- **[OpenSCAD](https://openscad.org/)** — Parametric 3D modeling for the enclosure
- **[KiCad](https://www.kicad.org/)** — Open-source PCB design tools
- The **r/gmrs** and **r/amateurradio** communities for feedback, feature requests, and testing

## Disclaimer & Legal Notice

### No Warranty

THIS SOFTWARE AND HARDWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS, CONTRIBUTORS, OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES, OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT, OR OTHERWISE, ARISING FROM, OUT OF, OR IN CONNECTION WITH THE SOFTWARE, HARDWARE DESIGNS, OR THE USE OR OTHER DEALINGS THEREIN.

### Use at Your Own Risk

This project — including the Android application, USB interface hardware designs, PCB schematics, enclosure files, and all associated documentation — is provided for **educational and hobbyist purposes only**. By using, building, or deploying any part of this project, you acknowledge and agree that:

- You do so **entirely at your own risk**.
- The authors and contributors **are not responsible** for any damage to your radio, phone, vehicle, computer, or any other equipment.
- The authors and contributors **are not liable** for any personal injury, property damage, financial loss, or legal consequences resulting from the use or misuse of this project.

### Radio Communications & FCC Compliance

- This project is **not affiliated with, endorsed by, or sponsored by Radioddity, Anytone, or any radio manufacturer**.
- **GMRS operation in the United States requires a valid FCC license** (FCC Part 95, Subpart E). No exam is required, but a license must be obtained before transmitting. Operating without a license is a federal violation subject to fines and enforcement action.
- Users are **solely responsible** for ensuring their radio operation complies with all applicable **federal, state, and local laws and regulations**, including but not limited to FCC Part 95 rules.
- The DB20-G hardware is capable of transmitting outside the GMRS band via third-party firmware modifications — **doing so without the appropriate license (e.g., amateur radio license for amateur bands) is illegal** and may result in FCC enforcement action, including fines up to $100,000+ per violation.
- This software may allow programming of frequencies or settings that are **not legal for transmission** in your jurisdiction. It is **your responsibility** to verify that any programmed configuration complies with applicable law before transmitting.
- The authors **do not encourage, condone, or support** any illegal radio operation, intentional interference, or violation of any telecommunications regulation.

### Distracted Driving & Mobile Device Use

- This application is designed for use with a mobile phone connected to a vehicle-mounted radio. **Many jurisdictions prohibit or restrict the use of mobile devices while driving.**
- **Do not operate this application while driving.** Program your radio while the vehicle is safely parked and stationary.
- The authors and contributors **are not responsible** for any traffic violations, accidents, injuries, or fatalities resulting from the use of this application while operating a motor vehicle.
- You are **solely responsible** for complying with all distracted-driving laws in your jurisdiction.

### Hardware & Electrical Safety

- The USB interface hardware involves custom electronics connected to both your phone and your radio. **Incorrect assembly, wiring, or soldering can damage your devices, create electrical hazards, or cause fire.**
- The hardware designs have **not been professionally certified** for safety (e.g., UL, CE, FCC Part 15 emissions). You build and use them at your own risk.
- The authors **are not responsible** for any damage, injury, or loss caused by building, modifying, or using the hardware.

### Software Bugs & Data Loss

- This application is in **early development** and contains **known and unknown bugs**. It may corrupt radio memory, program incorrect frequencies, cause unexpected radio behavior, or fail during critical operations.
- **Always back up your radio's configuration** before using this software to write to it.
- The authors **are not responsible** for any data loss, radio misconfiguration, or equipment damage caused by software defects.

### Limitation of Liability

TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, IN NO EVENT SHALL ANY AUTHOR, CONTRIBUTOR, OR COPYRIGHT HOLDER BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE OR HARDWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

---

**Built for the GMRS community, by the GMRS community.**
