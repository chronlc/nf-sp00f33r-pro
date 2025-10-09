# NF-SP00F33R - EMV Security Research Platform# 🏴‍☠️ nf-sp00f33r Framework

### Professional EMV Research & NFC Security Testing Platform

[![Android](https://img.shields.io/badge/Android-SDK%2028+-brightgreen.svg)](https://developer.android.com/)

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-blue.svg)](https://kotlinlang.org/)[![Platform](https://img.shields.io/badge/platform-Android-green.svg)](https://android.com)

[![Compose](https://img.shields.io/badge/Jetpack%20Compose-Material3-orange.svg)](https://developer.android.com/jetpack/compose)[![API](https://img.shields.io/badge/API-28%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=28)

[![Kotlin](https://img.shields.io/badge/100%25-Kotlin-blue.svg)](https://kotlinlang.org)

**Production-grade Android application for EMV contactless card security research and vulnerability analysis.**[![Material3](https://img.shields.io/badge/Material3-UI-purple.svg)](https://m3.material.io)



---> **Advanced EMV Card Research Platform with Professional NFC/HCE Framework**



## 🎯 Project Overview## 🚀 Framework Overview



NF-SP00F33R is an advanced EMV security research platform that enables security researchers to:**nf-sp00f33r** is a sophisticated Android application designed for EMV (Europay, Mastercard, Visa) security research and NFC technology analysis. Built with cutting-edge Material Design 3 and professional-grade architecture, this framework provides researchers with comprehensive tools for payment card security assessment.

- **Read and analyze** EMV contactless payment cards (RFID/NFC)

- **Detect vulnerabilities** including ROCA (CVE-2017-15361)### ✨ Key Features

- **Emulate cards** with various attack payloads

- **Test security** of EMV implementations- **🎯 Professional EMV Analysis**: Complete EMV workflow research with BER-TLV parsing

- **Analyze cryptographic data** from card transactions- **📱 Modern Material3 UI**: Professional interface with Matrix green theme

- **🔒 NFC/HCE Framework**: Advanced Host Card Emulation capabilities  

### Core Capabilities- **💳 Virtual Card System**: Dynamic card visualization and management

- ✅ **Card Reading**: Full EMV contactless card data extraction- **📊 Real-time APDU Logging**: Comprehensive transaction monitoring

- ✅ **ROCA Detection**: Automatic RSA vulnerability scanning- **🛡️ Attack Module Framework**: Research-grade security testing modules

- ✅ **EMV Emulation**: 5 production attack modules (Track2, CVM, AIP, Cryptogram, PPSE)- **🗄️ Database Management**: Professional card profile storage system

- ✅ **Encrypted Storage**: AES-256-GCM with Android Keystore- **📈 Analysis Tools**: Advanced EMV data analysis and visualization

- ✅ **Analytics Engine**: Attack success rates, timing analysis, batch scanning

- ✅ **Material3 UI**: Modern Compose interface with dark theme## 🏗️ Architecture



---```

📦 nf-sp00f33r Framework

## 🏗️ Architecture├── 📱 android-app/          # Main Android application

│   ├── 🎯 activities/       # Material3 Activities (Main, Splash)

### Technology Stack│   ├── 🧩 components/       # UI Components (Cards, Stats, APDU)

- **Language**: Kotlin (100% production-grade, no safe-call operators)│   ├── 📺 screens/          # 5 Professional Screens

- **UI Framework**: Jetpack Compose with Material3│   ├── 💳 cardreading/      # NFC/EMV Reading Infrastructure

- **Min SDK**: 28 (Android 9.0+)│   ├── 🔄 emulation/        # HCE & Attack Modules

- **Target SDK**: 34 (Android 14)│   ├── 🗄️ data/            # Models & Database Layer

- **Build System**: Gradle 8.x│   ├── 🔧 hardware/         # Hardware Abstraction Layer

- **Security**: BouncyCastle, Android Keystore, EncryptedSharedPreferences│   └── 🎨 theme/           # Material3 Design System

├── 📚 docs/                 # Documentation & Research

### Module System (Phase 2A)├── 🛠️ scripts/             # Development Tools

```└── 💾 backups/             # Framework Backups

ModuleRegistry (Centralized Lifecycle)```

├── LoggingModule (Centralized logging with FrameworkLogger)

├── SecureMasterPasswordModule (Keystore-backed encryption keys)## 🖥️ User Interface

├── CardDataStoreModule (AES-256-GCM encrypted card storage)

└── EmulationModule (5 EMV attack modules + analytics)### 5 Professional Screens

```

1. **📊 Dashboard** - Real-time system overview and statistics

### Package Structure2. **📖 Card Reading** - NFC scanning and EMV data extraction  

```3. **🔄 Emulation** - HCE management and attack simulation

com.nfsp00f33r.app/4. **🗄️ Database** - Card profile management and storage

├── activities/          # MainActivity (Compose host)5. **📈 Analysis** - Advanced EMV data analysis tools

├── application/         # NfSp00fApplication (Module initialization)

├── core/                # ModuleRegistry, BaseModule, FrameworkLogger### 🎨 Design System

├── emulation/           # EmulationModule, AttackAnalytics, RocaBatchScanner

│   ├── modules/         # 5 attack modules (Track2, CVM, AIP, etc.)- **Material3 Design Language** with professional theming

│   └── coordinator/     # AttackChainCoordinator, ApduDataExtractor- **Matrix Green Primary** (`#4CAF50`) with dark accents

├── roca/                # RocaDetector, RocaExploiter, RocaVulnerabilityAnalyzer- **Black Status Bar** integration for premium feel

├── screens/             # Dashboard, CardReading, Emulation, Database (Compose)- **Responsive Card-based** layouts with elevation

├── storage/             # CardDataStore, SecureMasterPasswordManager- **Professional Typography** and consistent spacing

└── models/              # CardProfile, EmulationProfile, AttackType

```## 🛡️ Security Research Features



---### EMV Attack Modules



## 🚀 Quick Start- **🎯 PPSE AID Poisoning** - Application selection manipulation

- **📊 Track2 Data Spoofing** - Magnetic stripe emulation attacks  

### Prerequisites- **🔒 AIP Force Offline** - Authorization bypass techniques

- **Android Studio**: Hedgehog (2023.1.1) or later- **🔐 Cryptogram Downgrade** - Transaction security degradation

- **JDK**: 17 or later- **💳 CVM Bypass** - Cardholder verification bypass

- **Android SDK**: Level 28+ with SDK 34 installed

- **Physical Device**: NFC-enabled Android phone (required for card reading)### NFC/HCE Capabilities



### Build & Install- **Advanced Host Card Emulation** with real-time APDU processing

```bash- **EMV Workflow Simulation** supporting multiple card types

# Clone repository- **Dynamic Response Generation** with attack payload injection

git clone https://github.com/nf-sp00f33r/nf-sp00f33r.git- **Professional Transaction Logging** with hex visualization

cd nf-sp00f33r/android-app

## 🛠️ Technical Stack

# Build debug APK

./gradlew assembleDebug- **Platform**: Android 14+ (API 34), Minimum SDK 28

- **Language**: 100% Kotlin with null-safety

# Install on device- **UI Framework**: Jetpack Compose with Material3

adb install -r build/outputs/apk/debug/android-app-debug.apk- **Build System**: Gradle 8.6 with JDK 17

- **NFC Stack**: Advanced NFC-A/B/F with ISO-DEP

# Launch app- **EMV Parsing**: BER-TLV with payneteasy integration

adb shell am start -n com.nfsp00f33r.app/.activities.MainActivity- **Architecture**: Clean Architecture with MVVM pattern

```

## 🚀 Quick Start

### First Launch

1. **Set Master Password**: Required for encrypted storage (5-level strength validation)### Prerequisites

2. **Grant NFC Permission**: Enable NFC in device settings

3. **Scan Test Card**: Dashboard → Card Reading → Hold card to back of phone- **Android Studio** Arctic Fox or newer

4. **View Results**: Automatic ROCA vulnerability check after scan- **JDK 17** for optimal performance  

- **Android SDK 34** with build tools

---- **NFC-enabled Android device** for testing



## 📱 Features### Installation



### 1. Dashboard```bash

- **Card Statistics**: Total cards scanned, unique profiles, attack success rates# Clone the repository

- **Recent Activity**: Last 5 attacks with timestamps and resultsgit clone https://github.com/nf-sp00f33r/nf-sp00f33r.git

- **Analytics Summary**: Attack type breakdown, success/failure ratescd nf-sp00f33r

- **Quick Actions**: Navigate to Card Reading, Emulation, Database

# Build and install

### 2. Card Reading (Automatic ROCA Integration)cd android-app

- **EMV Data Extraction**: PAN, expiry, track data, ICC public key certificates./gradlew assembleDebug

- **ROCA Vulnerability Check**: Automatic after every card scan./gradlew installDebug

- **Color-Coded Alerts**: 🔴 Red (vulnerable) / 🟢 Green (safe)

- **Real-time Status**: Scan progress, APDU commands, card responses# Launch application

adb shell am start -n com.nfsp00f33r.app/.activities.MainActivity

### 3. EMV Emulation```

- **5 Attack Modules**: Track2 Spoofing, CVM Bypass, AIP Force Offline, Cryptogram Downgrade, PPSE AID Poisoning

- **Profile Management**: Save/load attack configurations### Development Setup

- **Live Monitoring**: Real-time attack execution status

- **Success Analytics**: Per-attack type statistics```bash

# Configure environment

### 4. Databaseexport ANDROID_HOME=$HOME/Android/Sdk

- **Card Profiles**: View all scanned cards with encrypted storageexport PATH=$PATH:$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools

- **ROCA Batch Scanning**: Scan all stored cards for vulnerabilities

- **Search & Filter**: Find cards by PAN, issuer, date# Force build (if needed)

- **Export Options**: Backup encrypted profileschmod +x android-app/force_build.sh

./android-app/force_build.sh

### 5. Analytics

- **Attack Statistics**: Total attacks, success rate, failure reasons# Monitor logs

- **Timing Analysis**: Average execution time per attack typeadb logcat | grep nfsp00f33r

- **Batch Reports**: Comprehensive ROCA scan results```

- **Trend Visualization**: Historical attack performance

## 📱 Usage Examples

---

### Basic NFC Reading

## 🔐 Security Features

```kotlin

### Encryption (Phase 1A)// Initialize NFC card reader

- **AES-256-GCM**: All card data encrypted at restval nfcReader = NfcCardReaderWithWorkflows()

- **Android Keystore**: Hardware-backed master key storagenfcReader.enableReaderMode(activity) { tag ->

- **EncryptedSharedPreferences**: Secure password persistence    // Process EMV card data

- **BouncyCastle Provider**: Cryptographic operations    val cardData = EmvTlvParser.parseEmvData(tag)

    // Display in professional UI

### ROCA Vulnerability Detection (Phase 1B)    updateCardInterface(cardData)

- **Fingerprint Testing**: 167 prime divisibility checks}

- **Vulnerable Range Detection**: 512/1024/2048-bit RSA keys```

- **Confidence Scoring**: Probability estimation

- **Factorization Estimates**: Time/cost predictions for exploitation### EMV Attack Simulation



### Data Protection```kotlin

- **No plaintext storage**: All sensitive data encrypted// Configure attack module

- **Secure wiping**: Memory cleared after operationsval attackModule = PpseAidPoisoningModule()

- **No network access**: Offline-only operationif (attackModule.isApplicable(commandApdu)) {

- **Sandboxed execution**: Android app isolation    val maliciousResponse = attackModule.applyAttack(

        commandApdu, 

---        originalResponse

    )

## 📊 Module System Details    // Log for research analysis

    ApduLogger.logTransaction(commandApdu, maliciousResponse)

### Phase 2A: Core Framework (COMPLETE)}

**4 commits over 10 days | 2,366 insertions / 43 deletions**```



#### Components## 🔬 Research Applications

- **ModuleRegistry** (390 lines): Dependency resolution, health monitoring, auto-restart

- **BaseModule** (287 lines): Abstract base with lifecycle hooks### Academic Research

- **FrameworkLogger** (150 lines): Centralized logging with file rotation- **EMV Protocol Analysis** - Deep dive into payment workflows

- **Module Interface** (160 lines): Contract for all modules- **NFC Security Assessment** - Comprehensive attack surface analysis  

- **Payment Card Vulnerabilities** - Real-world security research

#### Features

- ✅ Topological sort dependency resolution### Professional Testing

- ✅ Health monitoring (30-second intervals)- **Payment System Auditing** - Enterprise security assessment

- ✅ Automatic restart on failure- **Compliance Validation** - EMV specification conformance

- ✅ Event system for inter-module communication- **Security Training** - Educational security demonstration

- ✅ Graceful shutdown with cleanup

- ✅ Thread-safe state management### Bug Bounty & Red Team

- **Payment Infrastructure Testing** - Authorized penetration testing

### Phase 1A: Encrypted Storage (COMPLETE)- **Mobile Payment Security** - iOS/Android payment app analysis

**4 commits over 4 days**- **IoT Payment Device Research** - Embedded system security



- **Day 1**: kotlinx.serialization + BouncyCastle dependencies, CardDataStore with AES-256-GCM## 📊 Framework Statistics

- **Day 2**: BouncyCastle provider initialization in Application

- **Day 3**: CardProfileAdapter, migrated 3 ViewModels to encrypted storage- **🏗️ Architecture**: Clean MVVM with 15+ modules

- **Day 4**: SecureMasterPasswordManager with Keystore, MasterPasswordSetupScreen UI- **📱 UI Components**: 25+ professional Material3 components  

- **🔧 Core Classes**: 50+ production-grade Kotlin classes

### Phase 1B: ROCA Exploitation (COMPLETE)- **🛡️ Attack Modules**: 5 advanced EMV attack simulations

**2 commits over 2 days**- **📚 Documentation**: Comprehensive research documentation

- **⚡ Performance**: Optimized for real-time NFC processing

- **Day 5**: RocaDetector (400+ lines) with fingerprint testing, confidence scoring

- **Day 6**: RocaExploiter (420+ lines) with factorModulus, reconstructPrivateKey, PEM export## 🤝 Contributing



---We welcome contributions from security researchers and developers:



## 🧪 Testing1. **Fork** the repository

2. **Create** feature branch (`git checkout -b feature/advanced-analysis`)

### Build Status3. **Commit** changes (`git commit -m 'Add advanced EMV analysis'`)

```bash4. **Push** branch (`git push origin feature/advanced-analysis`)

# Compile all modules5. **Open** Pull Request with detailed description

./gradlew build

### Development Guidelines

# Expected output:

# BUILD SUCCESSFUL in 45s- **Kotlin Code Style**: Follow Android conventions

# 89 actionable tasks: 89 executed- **Material3 Compliance**: Maintain design system consistency

```- **Security Focus**: All features must enhance research capabilities

- **Professional Quality**: Production-grade code standards

### Run Tests- **Documentation**: Comprehensive inline and external docs

```bash

# Unit tests## 📄 License & Legal

./gradlew test

```

# Instrumented tests (requires device)⚠️  RESEARCH & EDUCATIONAL USE ONLY ⚠️

./gradlew connectedAndroidTest

```This framework is designed exclusively for:

✅ Academic security research

### Code Quality✅ Authorized penetration testing  

```bash✅ Educational security training

# Lint checks✅ EMV specification compliance testing

./gradlew lint

STRICTLY PROHIBITED:

# Naming audit❌ Unauthorized payment card cloning

python3 scripts/naming_auditor.py❌ Fraudulent transaction generation  

❌ Illegal financial system exploitation

# Corruption detection❌ Commercial misuse without permission

./scripts/corruption_detector.sh```

```

**Disclaimer**: This software is for educational and authorized research purposes only. Users are responsible for compliance with all applicable laws and regulations.

---

## 🏆 Credits & Recognition

## 📝 Development Rules

**nf-sp00f33r Framework** - Advanced EMV Research Platform

### Universal Laws (Strictly Enforced)- **Architecture**: Clean Android architecture with Material3

1. **No safe-call operators** (`?.`) in production code- **EMV Research**: Comprehensive payment security analysis  

2. **PascalCase** for classes, **camelCase** for methods/variables- **UI/UX Design**: Professional Material Design 3 implementation

3. **Batch operations** required for all multi-file changes- **Security Modules**: Advanced attack simulation framework

4. **BUILD SUCCESSFUL** mandatory before task completion

5. **Production-grade code only** - no placeholders or TODOs---

6. **DELETE→REGENERATE protocol** for file corruption prevention

7. **Efficiency rule**: No redundant actions, no feature regression<div align="center">



### Code Standards**🏴‍☠️ Built for Security Researchers by Security Researchers 🏴‍☠️**

- **Null safety**: Explicit null checks, not safe-calls

- **Error handling**: Try-catch with specific exceptions*Advancing payment security through responsible research*

- **Documentation**: KDoc for all public APIs

- **Testing**: Unit tests for business logic[![Matrix](https://img.shields.io/badge/-Research%20Framework-4CAF50?style=for-the-badge&logo=android&logoColor=white)](https://github.com/nf-sp00f33r/nf-sp00f33r)

- **Logging**: FrameworkLogger for all debug/error messages

</div>
---

## 🗂️ Project Structure

```
nf-sp00f33r/
├── android-app/              # Main Android application
│   ├── src/main/java/        # Kotlin source code
│   │   └── com/nfsp00f33r/app/
│   ├── build.gradle          # Module build config
│   └── gradle.properties     # Build properties
├── backups/                  # Automated backups (timestamped)
├── scripts/                  # Development tools
│   ├── audit_codebase.py     # Code quality checker
│   ├── naming_auditor.py     # Naming convention validator
│   ├── corruption_detector.sh # File integrity checker
│   └── backup_manager.py     # Backup automation
├── .github/instructions/     # AI agent memory/context
├── build.gradle              # Root build config
├── settings.gradle           # Project modules
├── README.md                 # This file
└── PROJECT_SUMMARY.md        # Quick reference for AI agents
```

---

## 🤝 Contributing

This is a security research project. Contributions should focus on:
- Improving EMV attack module accuracy
- Adding new vulnerability detection algorithms
- Enhancing encryption/security mechanisms
- UI/UX improvements for Material3 Compose
- Performance optimizations

### Commit Guidelines
- Descriptive commit messages with context
- One logical change per commit
- BUILD SUCCESSFUL verification before push
- Update PROJECT_SUMMARY.md for major changes

---

## ⚠️ Legal & Ethical Use

**FOR SECURITY RESEARCH AND EDUCATIONAL PURPOSES ONLY**

- ✅ Testing your own cards
- ✅ Authorized security research
- ✅ Educational demonstrations
- ❌ Unauthorized access to payment systems
- ❌ Fraud or theft
- ❌ Violating terms of service

**Users are solely responsible for compliance with applicable laws.**

---

## 📚 Documentation

- **User Manual**: See app's Help screen
- **API Reference**: KDoc comments in source code
- **Architecture Docs**: Phase completion reports in backups/
- **AI Agent Context**: `.github/instructions/*.instructions.md`

---

## 🐛 Known Issues

### App Crash on Launch (Current Investigation)
**Error**: `IllegalStateException: CardDataStoreModule not initialized`

**Stack Trace**:
```
at NfSp00fApplication$Companion.getCardDataStoreModule(NfSp00fApplication.kt:243)
at DashboardViewModel.<init>(DashboardViewModel.kt:47)
```

**Cause**: DashboardViewModel tries to access CardDataStoreModule before Application.onCreate() completes module initialization.

**Status**: 🔴 IN PROGRESS - Debugging module initialization order

**Workaround**: None currently - app requires fix before use

---

## 📄 License

**Proprietary Security Research Software**

Copyright © 2025 NF-SP00F33R Project. All rights reserved.

This software is provided for security research and educational purposes only. Unauthorized distribution, modification, or commercial use is prohibited.

---

## 📞 Contact

- **GitHub**: https://github.com/nf-sp00f33r/nf-sp00f33r
- **Issues**: https://github.com/nf-sp00f33r/nf-sp00f33r/issues
- **Security Reports**: Use GitHub Security Advisories

---

**Built with ❤️ for EMV security research**

Last Updated: October 9, 2025 | Version: Phase 2A Complete (Quick Wins Integration)
