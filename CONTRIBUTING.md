# Contributing to DB20-G Controller

Thank you for your interest in contributing! This is a community-driven project and all contributions are welcome — code, hardware improvements, documentation, translations, testing, and bug reports.

## Quick Start

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/my-feature`
3. Make your changes
4. Test on a real device if possible
5. Commit with clear messages: `git commit -m "Add repeater search radius filter"`
6. Push and open a Pull Request

## Development Setup

### Android App

- **Android Studio** Hedgehog or newer
- **JDK 17**
- **Android SDK 34**
- Clone, open in Android Studio, and sync Gradle — the wrapper handles the rest

```bash
./gradlew assembleDebug   # Build debug APK
./gradlew test            # Run unit tests
```

### Hardware

- **KiCad 9** for schematic and PCB editing
- **OpenSCAD** for enclosure modifications
- Hardware files are in `hardware/kicad/` and `hardware/enclosure/`

## What to Work On

### High-Impact Areas

- **Testing** — Try the app on different Android devices, USB adapters, and radio configurations
- **Bug reports** — File detailed issues with device info, steps to reproduce, and logcat output
- **Translations** — Spanish is started (`values-es/`); other languages welcome
- **Hardware builds** — Build the interface box and share feedback, photos, or improvements
- **Documentation** — Tutorials, wiring guides, setup videos

### Code Contributions

- Follow existing Kotlin + Android conventions
- Use MVVM architecture — business logic belongs in ViewModels
- Use Coroutines for async work (no raw threads or AsyncTask)
- Use Material Design 3 components
- Keep commits focused and atomic

### Hardware Contributions

- Run DRC/ERC in KiCad before submitting PCB changes
- Update `BOM.md` and `BOM.csv` if components change
- Test changes on a real build if possible
- Document any wiring or pinout changes in `WIRING.md`

## Reporting Bugs

Open a GitHub issue with:

- **Device**: Android model and OS version
- **USB adapter**: Type and chipset (CP2102, PL2303, etc.)
- **Steps to reproduce**: What you did, step by step
- **Expected behavior**: What should have happened
- **Actual behavior**: What actually happened
- **Logcat**: Filtered to `com.db20g.controller` if possible

## Pull Request Guidelines

- Keep PRs focused on a single change
- Describe what you changed and why
- Reference any related issues (`Fixes #42`)
- Ensure the project builds without errors
- Add or update tests if applicable

## Code of Conduct

Be respectful and constructive. We're all here because we enjoy GMRS and open-source. Harassment, discrimination, and toxic behavior will not be tolerated.

## License

By contributing, you agree that your contributions will be licensed under:

- **MIT License** for software contributions
- **CERN-OHL-P-2.0** for hardware contributions
