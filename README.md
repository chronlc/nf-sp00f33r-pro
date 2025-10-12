# 🏴‍☠️ nf-sp00f33r - EMV Security Research Platform

[![Android](https://img.shields.io/badge/Android-SDK%2028+-brightgreen.svg)](https://developer.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.20-blue.svg)](https://kotlinlang.org/)
[![Material3](https://img.shields.io/badge/Material3-UI-purple.svg)](https://m3.material.io)
[![License](https://img.shields.io/badge/License-Research-red.svg)](LICENSE)

**Professional-grade Android application for EMV contactless card security research and vulnerability analysis.**

---

## 🎯 Overview

nf-sp00f33r is an advanced EMV security research platform designed for security researchers, penetration testers, and academic institutions. Built with cutting-edge Material Design 3 and professional-grade architecture, this framework provides comprehensive tools for payment card security assessment.

### Key Capabilities

- 📖 **EMV Card Reading** - Complete contactless card data extraction
- 🔄 **Host Card Emulation** - Advanced NFC/HCE attack simulation
- 🛡️ **Vulnerability Detection** - Automated ROCA (CVE-2017-15361) scanning
- 💳 **Attack Modules** - 5 production-grade EMV attack simulations
- 🔒 **Encrypted Storage** - AES-256-GCM with Android Keystore
- 📊 **Analytics Engine** - Attack success rates and timing analysis
- 🎨 **Material3 UI** - Professional Compose interface with matrix green theme

---

## 🏗️ Architecture

```
android-app/
├── activities/          # Material3 Activities (MainActivity, SplashActivity)
├── screens/             # 5 Professional Screens (Dashboard, CardReading, Emulation, Database, Analysis)
├── components/          # Reusable UI Components (CardView, APDUTerminal, StatsDisplay)
├── viewmodels/          # MVVM ViewModels (DashboardVM, CardReadingVM, EmulationVM)
├── core/                # Module System (ModuleRegistry, BaseModule, FrameworkLogger)
├── emulation/           # Attack Modules & Coordinator
│   ├── modules/         # 5 Attack Types (Track2, CVM, AIP, Cryptogram, PPSE)
│   └── coordinator/     # AttackChainCoordinator, ApduDataExtractor
├── cardreading/         # NFC Reading Infrastructure (EnhancedHceService, EmvTlvParser)
├── storage/             # Encrypted Storage (CardDataStore, SecureMasterPasswordManager)
├── security/            # ROCA Detection & Exploitation
├── hardware/            # PN532 Device Module (Bluetooth/USB support)
├── nfc/                 # NFC HCE Module
└── data/                # Models & Entities
```

### Technology Stack

- **Language:** 100% Kotlin (null-safe, production-grade)
- **UI Framework:** Jetpack Compose with Material3
- **Min SDK:** 28 (Android 9.0+), Target SDK: 34 (Android 14)
- **Build System:** Gradle 8.6 with JDK 17
- **Security:** BouncyCastle 1.70, Android Keystore, EncryptedSharedPreferences
- **Database:** Room 2.6.1 for local storage
- **EMV Parsing:** BER-TLV 1.0-11 for EMV data structures
- **Coroutines:** kotlinx.coroutines 1.7.3 for async operations

---

## 🚀 Quick Start

### Prerequisites

- **Android Studio:** Hedgehog (2023.1.1) or later
- **JDK:** 17 or higher
- **Android SDK:** API 28+ with build tools
- **NFC Device:** Android phone with NFC capability (for card reading)

### Installation

```bash
# Clone repository
git clone https://github.com/nf-sp00f33r/nf-sp00f33r.git
cd nf-sp00f33r/android-app

# Build debug APK
./gradlew assembleDebug

# Install on device
adb install -r build/outputs/apk/debug/android-app-debug.apk

# Launch application
adb shell am start -n com.nfsp00f33r.app/.activities.SplashActivity
```

### First Launch

1. **Set Master Password** - Required for encrypted storage (5-level strength validation)
2. **Grant NFC Permission** - Enable NFC in device settings
3. **Scan Test Card** - Dashboard → Card Reading → Hold card to phone
4. **View Results** - Automatic ROCA vulnerability check after scan

---

## 📱 Features

### 1. Dashboard Screen
- **Card Statistics:** Total scanned, unique profiles, attack success rates
- **Recent Activity:** Last 5 attacks with timestamps and results
- **Quick Actions:** Navigate to all modules
- **Health Monitoring:** Real-time module status display

### 2. Card Reading Screen
- **EMV Data Extraction:** PAN, expiry, track data, ICC public keys
- **Real-time APDU Logging:** 20 commands visible with TX/RX color coding
- **Automatic ROCA Check:** Color-coded alerts (🔴 vulnerable / 🟢 safe)
- **Enhanced Terminal:** Professional hex visualization with descriptions

### 3. Emulation Screen
- **5 Attack Modules:** Track2, CVM Bypass, AIP, Cryptogram, PPSE Poisoning
- **Profile Management:** Save/load attack configurations
- **Live Monitoring:** Real-time attack execution status
- **Success Analytics:** Per-attack type statistics

### 4. Database Screen
- **Card Profiles:** View all scanned cards with encrypted storage
- **ROCA Batch Scanning:** Scan all stored cards for vulnerabilities
- **Search & Filter:** Find cards by PAN, issuer, date
- **Export Options:** Backup encrypted profiles

### 5. Analysis Screen
- **Terminal Fuzzer:** EMV protocol fuzzing with PN532/Android NFC integration
  - Room DB persistence for fuzzing sessions
  - 9 vulnerability-specific presets (ROCA, Track2, CVM, AIP, etc.)
  - Crash reproducibility testing
  - JSON export with comprehensive metrics
- **Attack Statistics:** Success rates, timing analysis, failure reasons
- **Batch Reports:** Comprehensive vulnerability scan results

---

## 🛡️ Security Features

### Encryption & Storage
- **AES-256-GCM:** All card data encrypted at rest
- **Android Keystore:** Hardware-backed master key storage
- **EncryptedSharedPreferences:** Secure password persistence
- **BouncyCastle Provider:** Cryptographic operations
- **No Plaintext Storage:** All sensitive data encrypted

### ROCA Vulnerability Detection
- **Fingerprint Testing:** 167 prime divisibility checks
- **Vulnerable Range Detection:** 512/1024/2048-bit RSA keys
- **Confidence Scoring:** Probability estimation
- **Factorization Estimates:** Time/cost predictions
- **Batch Scanning:** Automated analysis of stored cards

### Attack Modules (Research)
1. **Track2 Data Spoofing** - Magnetic stripe emulation attacks
2. **CVM Bypass** - Cardholder verification bypass techniques
3. **AIP Force Offline** - Authorization bypass
4. **Cryptogram Downgrade** - Transaction security degradation
5. **PPSE AID Poisoning** - Application selection manipulation

---

## 🧪 Testing & Debugging

### Build System

```bash
# Full build
./gradlew assembleDebug

# Run lint checks
./gradlew lint

# Clean build
./gradlew clean assembleDebug

# Force rebuild (if needed)
chmod +x force_build.sh
./force_build.sh
```

### ADB Debug System (16 Commands)

**Backend Debugging (8 commands):**
```bash
# View application logs
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command logcat

# Database inspection
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command db --es params '{"query":"count"}'

# Module health check
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command state

# Real-time metrics
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command health
```

**UI Automation (8 commands):**
```bash
# Get current screen info
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command dump_ui

# Click element
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command click --es params '{"text":"Scan Card"}'

# Capture screenshot
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command screenshot

# Assert visibility
adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND --es command assert_visible --es params '{"target":"Dashboard","expected":true}'
```

See `docs/ADB_DEBUG_GUIDE.md` for complete reference.

### PN532 Hardware Testing

For advanced hardware testing with PN532 NFC module via Bluetooth:

```bash
# Setup (one-time)
./setup_pn532_testing.sh

# Run tests
python3 scripts/pn532_controller.py --mode pn532-reads-card
```

See `docs/PN532_TESTING_GUIDE.md` for complete setup.

---

## 🔬 Research Applications

### Academic Research
- EMV Protocol Analysis - Deep dive into payment workflows
- NFC Security Assessment - Comprehensive attack surface analysis
- Payment Card Vulnerabilities - Real-world security research

### Professional Testing
- Payment System Auditing - Enterprise security assessment
- Compliance Validation - EMV specification conformance
- Security Training - Educational security demonstration

### Bug Bounty & Red Team
- Payment Infrastructure Testing - Authorized penetration testing
- Mobile Payment Security - iOS/Android payment app analysis
- IoT Payment Device Research - Embedded system security

---

## ⚠️ Legal & Disclaimer

**⚠️ RESEARCH & EDUCATIONAL USE ONLY ⚠️**

This framework is designed exclusively for:
- ✅ Academic security research
- ✅ Authorized penetration testing
- ✅ Educational security training
- ✅ EMV specification compliance testing

**STRICTLY PROHIBITED:**
- ❌ Unauthorized payment card cloning
- ❌ Fraudulent transaction generation
- ❌ Illegal financial system exploitation
- ❌ Commercial misuse without permission

**Users are solely responsible for compliance with all applicable laws and regulations.**

---

## 🤝 Contributing

We welcome contributions from security researchers and developers:

1. Fork the repository
2. Create feature branch (`git checkout -b feature/advanced-analysis`)
3. Commit changes with descriptive messages
4. Ensure BUILD SUCCESSFUL before push
5. Open Pull Request with detailed description

### Development Guidelines
- **Kotlin Code Style:** Follow Android conventions
- **Material3 Compliance:** Maintain design system consistency
- **Security Focus:** All features must enhance research capabilities
- **Professional Quality:** Production-grade code standards
- **Documentation:** Comprehensive inline and external docs

---

## 📚 Documentation

- **CHANGELOG.md** - Version history and changes
- **FEATURES.md** - Current features and future roadmap
- **docs/ADB_DEBUG_GUIDE.md** - Complete ADB debug reference
- **docs/PN532_TESTING_GUIDE.md** - Hardware testing setup
- **docs/PN532_QUICK_REF.md** - Quick reference card

---

## 🏆 Credits

**nf-sp00f33r Framework** - Advanced EMV Research Platform

- **Architecture:** Clean Android MVVM with Material3
- **EMV Research:** Comprehensive payment security analysis
- **UI/UX Design:** Professional Material Design 3 implementation
- **Security Modules:** Advanced attack simulation framework

---

<div align="center">

**🏴‍☠️ Built for Security Researchers by Security Researchers 🏴‍☠️**

*Advancing payment security through responsible research*

[![Matrix](https://img.shields.io/badge/-Research%20Framework-4CAF50?style=for-the-badge&logo=android&logoColor=white)](https://github.com/nf-sp00f33r/nf-sp00f33r)

</div>
