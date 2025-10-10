# nf-sp00f33r-pro

**Professional EMV Security Research Platform for Android**

## Repository Structure

This repository contains a single module:

- **`/android-app/`** - Android application source code (Kotlin, Jetpack Compose Material3)

## Technical Specifications

- **Language**: Kotlin 1.9.20
- **UI Framework**: Jetpack Compose with Material3 Design System
- **Minimum SDK**: 28 (Android 9.0 Pie)
- **Target SDK**: 34 (Android 14)
- **Build System**: Gradle 8.6 with Kotlin DSL
- **Architecture**: MVVM pattern with StateFlow/Compose integration

## Core Features

- ISO 7816-4 APDU command/response processing
- BER-TLV parsing (EMV 4.3 specification compliant)
- NFC Host Card Emulation (HCE) with ISO-DEP protocol support
- PN532 external reader integration via USB/Serial
- AES-256-GCM encrypted card profile storage (Android Keystore)
- ROCA vulnerability detection (CVE-2017-15361)
- Real-time APDU terminal with intelligent parsing

## Security Research Capabilities

- EMV application selection and file system traversal
- Dynamic PDOL/CDOL construction with cryptographic nonce generation
- Application File Locator (AFL) parsing for record extraction
- Application Interchange Profile (AIP) analysis (SDA/DDA/CDA detection)
- Track 2 Equivalent Data extraction and manipulation
- RSA public key certificate analysis for ROCA vulnerability assessment (integrated workflow)
- iCVV/Dynamic CVV analysis with Track bitmap processing (Brian Kernighan algorithm)
- Contact/Contactless mode toggle (1PAY/2PAY) with automatic fallback
- Comprehensive TLV parsing: 60-80+ tags per card (EmvTlvParser)

## Build Instructions

```bash
cd android-app
./gradlew assembleDebug
adb install -r build/outputs/apk/debug/android-app-debug.apk
```

## Documentation

Comprehensive documentation available in `/android-app/`:
- **README.md** - Application architecture and module system
- **CHANGELOG.md** - Version history and feature timeline
- **FEATURES.md** - Current capabilities and future roadmap
- **docs/** - Technical guides (ADB debugging, PN532 integration)

## License

Research and educational purposes only. Unauthorized use against systems without explicit permission is prohibited.

## Technical Support

For implementation details, refer to inline documentation and `/android-app/docs/` technical guides.
