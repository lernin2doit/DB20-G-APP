# DB20-G Android App — Development Roadmap

> Prioritized feature roadmap for the Radioddity DB20-G GMRS remote control Android app and custom interface box. Informed by extensive community research across r/gmrs, r/amateurradio, CHIRP project, and real-world user feedback.

---

## Priority Legend

| Tag | Meaning |
|-----|---------|
| 🔴 P0 | Critical — build this first |
| 🟠 P1 | High — core experience improvements |
| 🟡 P2 | Medium — strong user demand |
| 🟢 P3 | Nice-to-have — polish and advanced features |

---

## P0 — Critical Priority (Build First)

### 1. Setup Wizard & Quick-Start Templates ✅
**Why:** Programming difficulty is the #1 complaint across every GMRS community. Users say "I got my radio but no idea how to use it" (121 upvotes), "my only complaint is programming it." This is the single biggest barrier to adoption.

- [x] First-launch wizard: callsign entry → location permission → auto-detect radio → one-tap program
- [x] Pre-built channel templates:
  - **GMRS Standard 22** — all simplex channels, correct power limits
  - **Emergency Kit** — Ch 20 (unofficial emergency), local repeaters, FRS interop channels
  - **Road Trip** — *(deferred to P1-5 Travel Route Repeater Planner)*
  - **Family Pack** — simplified subset with privacy codes pre-set
- [x] "Program My Radio in 60 Seconds" flow for absolute beginners
- [x] Plain-language channel descriptions (not just frequencies)

### 2. RepeaterBook API Integration ✅
**Why:** Our static JSON with 12 sample repeaters is inadequate. Community universally relies on RepeaterBook and myGMRS. "Repeater-book vs MyGMRS" is a recurring thread. Users need real, live repeater data.

- [x] RepeaterBook GMRS API integration (free, well-documented)
- [x] Search by location, radius, state, frequency
- [x] Cache results locally with configurable refresh interval
- [x] Show repeater details: tone, offset, status, last verified date, notes
- [x] One-tap "Program This Repeater" — writes to next available channel
- [ ] Map view with repeater pins and coverage radius estimates *(deferred — requires Maps SDK)*
- [x] Fallback to cached data when offline

### 3. Programming Validation Engine ✅
**Why:** Users can accidentally program illegal frequencies, wrong power levels, or mismatched tones. Multiple posts about "program it right" (284 upvotes). Bad programming can violate FCC rules or cause interference.

- [x] Real-time validation as channels are edited:
  - Frequency within GMRS allocation
  - Power level ≤ FCC limit for channel type (0.5W FRS-shared, 5W simplex-only, 50W repeater)
  - Correct repeater offset (± 5 MHz) for channels 15R–22R
  - CTCSS/DCS tone matches known repeater if programmed from database
- [x] Warning vs. Error distinction (yellow caution vs. red block)
- [x] "Why is this wrong?" explainer for each validation rule
- [x] Batch validation: scan all 128 channels and report issues
- [x] Block upload of clearly illegal configurations

### 4. CHIRP CSV Import/Export ✅
**Why:** CHIRP is the industry standard. Confirmed DB20-G is in CHIRP's supported list. Users who already have CHIRP files need seamless migration. Community repeatedly asks about programming tool compatibility.

- [x] Import CHIRP .csv and .img files
- [x] Export to CHIRP-compatible .csv
- [x] Field mapping for DB20-G-specific settings
- [x] Preview imported channels before writing to radio
- [x] Handle frequency/tone format differences gracefully
- [x] Support drag-and-drop or file picker import

---

## P1 — High Priority (Core Experience)

### 5. Travel Route Repeater Planner ✅
**Why:** Unique killer feature no other GMRS app offers. Users describe hitting repeaters 30-35 miles away on road trips. Channel limit (128) means "I have to pick and choose which repeaters" — route planning solves this.

- [x] Enter start/end addresses or pick on map
- [x] Query RepeaterBook for repeaters along route corridor
- [x] Auto-generate optimized channel list for the trip
- [x] Show coverage gaps where no repeaters exist
- [x] One-tap program entire route into radio
- [x] Save route plans for reuse
- [x] Estimated handoff points between repeaters

### 6. Emergency Features Suite ✅
**Why:** "Some GMRS Facts for Emergency Planning" (138 upvotes, 55 comments) shows massive community interest. No official emergency channel exists — users need guidance and tools.

- [x] Dedicated emergency mode (large PTT, high-contrast UI, essential info only)
- [x] Emergency channel quick-tune: Ch 20 (unofficial emergency), Ch 16 (calling), local repeaters
- [x] SOS beacon: automated distress call with GPS coordinates in voice (TTS)
- [x] Emergency net check-in protocol assistant (structured status reports)
- [x] GPS coordinate readout (voice/DTMF) for SAR teams
- [x] "Dead man's switch" — auto-transmit if no user input for configurable period
- [ ] Battery-saver scanning mode (radio duty-cycle optimization) *(deferred — requires firmware-level duty cycling)*
- [x] Emergency contact list with last-known check-in times
- [x] Offline operation — all critical features work without internet

### 7. Bluetooth PTT Button Support ✅
**Why:** Safe vehicle operation requires hands-free PTT. Users mount DB20-G in vehicles (trunk, under seat) and need remote control. Community discusses Android Auto integration and vehicle radio setups.

- [x] BLE HID button mapping for PTT
- [x] Support common Bluetooth PTT buttons (e.g., Sena, generic BLE buttons)
- [x] Configurable button actions (PTT, channel up/down, emergency)
- [x] Audio routing through Bluetooth headset/speaker
- [x] Multiple simultaneous Bluetooth devices (PTT button + audio headset)

### 8. Background Service & Persistent Notification ✅
**Why:** Radio must keep working when the phone screen is off or another app is in foreground. Users mount the phone in a vehicle and switch between navigation and radio.

- [x] Foreground service with persistent notification
- [x] Notification shows: current channel, frequency, signal activity
- [x] Quick actions from notification: PTT (if hardware button), channel up/down
- [x] Wake lock management for continuous monitoring
- [x] Configurable auto-sleep timer
- [x] Survive app process kills (auto-restart service)

### 9. Audio Recording & Logging ✅
**Why:** Community discusses logging for emergency coordination, repeater monitoring, and general record-keeping.

- [x] Record all received audio with timestamps
- [x] Record transmitted audio
- [x] Per-channel recording toggle
- [x] Playback with channel/time metadata
- [x] Export recordings as WAV
- [x] Activity log: PTT events, channel changes, repeater connections, callsign IDs
- [x] Storage management (auto-delete after X days, max size)

### 10. Channel & Memory Management
**Why:** DB20-G has 128 channel slots. Users complain about having to "pick and choose" repeaters. Smart management of this limited space is essential.

- [x] Channel groups/banks (e.g., "Local Repeaters," "Family," "Travel")
- [x] Drag-and-drop channel reordering
- [x] Bulk edit (select multiple → change power/tone/etc.)
- [x] Channel search/filter by name, frequency, tone
- [x] "Favorites" pin for quick access
- [x] Clone channel profiles between slots
- [x] Undo/redo for all channel edits
- [x] Diff view: compare radio contents vs. app configuration before upload
- [x] Backup/restore channel configurations to cloud or local file

---

## P2 — Medium Priority (Strong User Demand)

### 11. Dark Mode & AMOLED Theme ✅
**Why:** Night driving, camping, emergency use — all benefit from dark UI. AMOLED true-black saves battery. Standard expectation for modern Android apps.

- [x] Full Material Dark theme (default)
- [x] AMOLED true-black variant (pure #000000 backgrounds)
- [x] Auto-switch based on system setting or time of day
- [x] Red-light mode for night vision preservation (red-tinted UI, minimum brightness)
- [x] Theme persistence via SharedPreferences
- [x] Smooth theme transition without activity restart

### 12. Custom Interface Box PCB Design
**Why:** Current breadboard/perfboard design works but isn't durable for permanent vehicle installation. Community builds suggest more robust solutions.

- [ ] KiCad schematic and PCB layout
- [x] Single board: CP2102 + CM108 + PTT circuit + audio attenuators + relay
- [x] 3D-printable enclosure design (STL files)
- [x] USB-C connector for phone side
- [x] RJ-45 pass-through for handset
- [x] LED indicators: power, PTT active, audio activity, serial TX/RX
- [x] Gerber files for PCB fabrication (JLCPCB/PCBWay ready)
- [x] Bill of materials with component sourcing links
- [x] In-app hardware setup guide with interactive assembly checklist
- [x] Wiring diagram and pin mapping reference
- [x] Hardware troubleshooting guide (common issues + solutions)

### 13. Scanner / Priority Scan
**Why:** Users want to monitor multiple channels and stop on activity. The DB20-G supports scanning but configuration is clunky via keypad.

- [x] Software-controlled scan list configuration
- [x] Priority channel (auto-return to check priority channel during scan)
- [x] Scan speed configuration (slow/medium/fast)
- [x] Visual scan indicator showing current monitored channel
- [x] Activity log during scan (which channels had traffic, when)
- [x] "Nuisance delete" — temporarily skip noisy channels during scan
- [x] Dual-watch mode (monitor two channels, leverages DB20-G TDR)
- [x] Talk-back timer (auto-resume scan after TX ends)

### 14. Signal & Audio Quality Monitoring
**Why:** Users discuss range testing, antenna performance, and repeater reach. Quantitative signal data helps optimize setups.

- [x] Real-time audio level meter with peak hold
- [x] Signal strength history graph (RSSI proxy via squelch-break detection)
- [x] Audio quality scoring based on noise floor analysis
- [x] Range test mode: automated call-response with distance logging
- [x] Squelch level visualization and remote adjustment
- [x] Audio equalizer for RX clarity (bass cut, treble boost for voice)
- [x] FFT spectrum view of received audio
- [x] Export signal reports as CSV

### 15. Multi-Radio Support ✅
**Why:** Power users and emergency coordinators manage multiple radios. Some users have DB20-G mobile + GM-30 HT + base station.

- [x] Detect and manage multiple connected USB serial radios
- [x] Per-radio configuration profiles with naming/labeling
- [x] Cross-radio channel sync
- [x] Simultaneous monitoring of multiple radios (with interface boxes)
- [x] Radio inventory with firmware versions and serial numbers
- [x] Quick-switch between active radio

### 16. Social & Community Features ✅
**Why:** GMRS community is active but fragmented. In-app community features increase engagement and mutual aid. "Someone who can help has to be listening" — community networking solves this.

- [x] QSO logging with contact database (local, no server needed)
- [x] Net schedule database with local storage and import/export
- [x] In-app repeater reporting (status, tone corrections, coverage feedback)
- [x] Share channel configurations via Android share sheet (JSON export)
- [x] Contact log search and statistics (total contacts, per-channel, per-callsign)
- [x] Export QSO log as ADIF or CSV

---

## P3 — Nice-to-Have (Polish & Advanced)

### 17. Android Auto / Vehicle Head Unit Integration ✅
**Why:** "HAM radio phone apps on Android Auto" thread shows strong demand. Vehicle-mounted DB20-G with phone on dash mount is the primary use case.

- [x] Android Auto compatible UI (simplified, large buttons, driving-safe layout)
- [x] Voice commands: "Change to channel 19," "Key up," "What channel am I on?"
- [x] Steering wheel button mapping for PTT
- [x] Integration with vehicle Bluetooth for audio routing
- [x] Audio focus management (handle navigation prompt interruptions gracefully)
- [x] MediaSession integration for car head unit display

### 18. Text Messaging Over Radio (Data Modes) ✅
**Why:** "An app that sends messages over radio waves" (77 comments). GMRS allows data transmission. Text over audio is possible with existing hardware.

- [x] AFSK text messaging (Bell 202 standard 1200 baud for compatibility)
- [x] Pre-defined quick messages ("En route," "At destination," "Need assistance")
- [x] GPS position sharing via encoded audio bursts
- [x] Message acknowledgment protocol with configurable retry attempts
- [x] Conversation thread view with message history
- [x] Compatibility with existing amateur radio text modes where legal

### 19. SSTV Image Transmission ✅
**Why:** Amateur radio community desires SSTV on Android. Could work over GMRS audio channel for slow-scan images (weather maps, situation photos in emergencies).

- [x] SSTV encode/decode (Robot36, Martin M1 modes)
- [x] Camera capture → encode → transmit
- [x] Receive and display incoming SSTV images with auto-detection
- [x] Image gallery with received picture history
- [x] Thumbnail preview during reception

### 20. Spectrum Display / Waterfall ✅
**Why:** SDR enthusiasts want visual frequency monitoring. Limited without SDR hardware, but audio spectrum analysis is possible with CM108.

- [x] Audio waterfall display of received signal with color palette options
- [x] Tone decoder visualization (CTCSS/DCS detection)
- [x] Audio spectrum analyzer for troubleshooting
- [x] Configurable FFT window size and overlap
- [x] Screenshot/export capability for spectrum captures

### 21. Weather Alert Integration ✅
**Why:** GMRS users often operate during severe weather. Integration with NWS alerts adds safety value.

- [x] NOAA weather alert monitoring (internet-based, since DB20-G doesn't have weather band)
- [x] Location-based severe weather notifications with configurable radius
- [x] Auto-switch to emergency mode during active warnings
- [x] Weather channel frequencies reference (for radios that support them)
- [x] Alert severity color coding (watch/warning/emergency)
- [x] Push notifications for severe weather alerts

### 22. Widget & Quick Settings Tile
**Why:** Fast access without opening the full app. Useful for vehicle-mounted scenarios.

- [x] Home screen widget: current channel, PTT status, quick channel switch (multiple sizes: 2x1, 4x2)
- [x] Quick Settings tile: PTT toggle, current channel display
- [x] Lock screen controls for PTT and channel
- [x] Widget theme matching app theme

### 23. Comprehensive FCC Compliance Tools
**Why:** New GMRS licensees are often confused about rules. In-app guidance prevents violations and builds confidence.

- [x] License lookup and validation (FCC ULS database API)
- [x] Family member authorization tracker (GMRS license covers household)
- [x] Power limit reference by channel with visual chart
- [x] Callsign ID compliance monitor (enhance with logging/reporting)
- [x] FCC Part 95 quick-reference organized by common questions
- [x] License renewal reminders with expiration countdown
- [x] Real-time violation alerts (e.g., high power on FRS-only channel)

### 24. Localization, Accessibility & Real-Time Translation
**Why:** Broadens user base and ensures usability for all operators. Real-time translation over radio is a game-changer for cross-language emergency coordination, border-area operations, and international GMRS use.

- [x] Spanish language support (significant GMRS user base)
- [x] TalkBack/screen reader compatibility with content descriptions
- [x] High-contrast mode beyond dark theme
- [x] Configurable font sizes (small/medium/large/extra-large)
- [x] Haptic feedback for PTT confirmation (useful when not looking at screen)
- [x] **Real-time radio audio translation** — detect incoming spoken language and translate to user's preferred language via on-device speech-to-text → translation → TTS pipeline
  - [x] Toggle on/off per channel or globally
  - [x] On-device ML models (Android ML Kit / Whisper) for offline capability
  - [x] Supported language pairs: English ↔ Spanish (priority), expandable to other languages
  - [x] Visual transcript overlay showing original + translated text
  - [x] Adjustable confidence threshold (suppress low-confidence translations)
  - [x] Latency target: <2 seconds end-to-end for near-real-time experience
  - [x] Option to auto-translate outgoing TTS messages before transmission
  - [x] Translation log/history with export
  - [x] Language model download manager (show model sizes, manage storage)
  - [x] Privacy-focused: all processing on-device, no cloud upload of audio

---

## Implementation Phases

### Phase 1 — Foundation (P0)
Items 1–4. Establishes the app as a genuinely useful, safe, beginner-friendly programming tool that integrates with the GMRS ecosystem. This alone makes the app worth downloading.

### Phase 2 — Power Features (P1)
Items 5–10. Transforms the app from a programming tool into a full radio operating system. Travel planner and emergency suite are differentiators no competitor offers.

### Phase 3 — Polish & Community (P2)
Items 11–16. Refines the experience, hardens the hardware, and builds community features. Multi-radio support opens the door to power users and emergency coordinators.

### Phase 4 — Advanced & Experimental (P3)
Items 17–24. Pushes into vehicle integration, data modes, and advanced features. Each item is independently valuable and can be built as interest/demand materializes.

---

## Community Research Sources

- Reddit r/gmrs — DB20-G setup threads, emergency planning (138↑), programming complaints, repeater database discussions
- Reddit r/amateurradio — Software pain points (160↑), feature wish lists, vehicle integration, data mode interest (77 comments)
- Reddit r/RTLSDR — Android SDR integration, vehicle head unit radio projects
- CHIRP Project Wiki — Confirmed DB20-G support, data source integrations (RepeaterBook, RadioReference)
- Real user quotes: "my only complaint is programming it," "I have to pick and choose which repeaters," "software has to be the biggest sore spot"

---

*Last updated: 2026-03-13*
