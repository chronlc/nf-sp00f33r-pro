# 📋 Changelog

All notable changes to nf-sp00f33r EMV Security Research Platform.

---

## [Unreleased]

### In Progress
- Advanced grammar-based EMV fuzzing
- Enhanced ROCA exploitation with prime factorization
- Real-time attack visualization dashboard

---

## [2025-12-06] - Database Auto-Refresh Fix

### 🔧 Fixed
- **Database Screen Auto-Refresh Issue** ([DatabaseViewModel.kt](android-app/src/main/java/com/nfsp00f33r/app/screens/database/DatabaseViewModel.kt))
  - **ROOT CAUSE:** Database Screen only loaded data once in `init {}` using `getAllSessions()` suspend function
  - **SYMPTOM:** Cards scanned in Read Screen did NOT appear in Database Screen without manual navigation away/back
  - **FIX:** Migrated to reactive Flow observation using `getAllSessionsFlow().collectLatest {}`
  - Cards now appear in Database Screen **INSTANTLY** after scan completes
  - Removed manual `refreshData()` calls after delete/clear operations (Flow handles automatically)
  - Added optional `manualRefresh()` public method for explicit user refresh (pull-to-refresh)
  - Improved logging: "PHASE 4.5: Auto-refresh complete - X cards displayed"
  
### 🎯 Improved
- **Real-time Database Synchronization**
  - Database Screen now observes `emvSessionDao.getAllSessionsFlow()` for live updates
  - Automatic UI updates when Read Screen saves new cards
  - No user action required - cards appear immediately upon scan completion
  - Deletion and bulk clear operations also trigger automatic refresh via Flow

### 📝 Technical Details
- **Flow Collection:** `collectLatest` ensures only latest emission is processed
- **Thread Safety:** Database queries on `Dispatchers.IO`, UI updates on `Dispatchers.Main`
- **Performance:** Flow only emits when database actually changes (Room optimization)

---

## [Phase 3] - 2025-10-10

### 🔐 ROCA Integration + iCVV/Dynamic CVV + Contact Mode (Nightly Release)

**Added:**
- **ROCA Vulnerability Analysis Integration** (CardReadingViewModel)
  - Auto-clears ROCA results at workflow start for fresh analysis per scan
  - `checkRocaVulnerability()` extracts results after each TLV parse phase
  - 4 new EmvCardData fields: rocaVulnerable, rocaVulnerabilityStatus, rocaAnalysisDetails, rocaCertificatesAnalyzed
  - Confidence levels: CONFIRMED, HIGHLY_LIKELY, POSSIBLE, UNLIKELY
  - Real-time vulnerability detection during certificate tag parsing (9F46, 92, 9F32, 9F47)

- **iCVV/Dynamic CVV Calculation** (CardReadingViewModel)
  - `calculateUnSize()` using Brian Kernighan bit counting algorithm from ChAP.py
  - `calculateDynamicCvvParams()` extracts Track 1/2 bitmaps (9F63, 9F64, 9F65, 9F66)
  - 8 new EmvCardData fields: icvvCapable, icvvTrack1Bitmap, icvvTrack1AtcDigits, icvvTrack1UnSize, icvvTrack2Bitmap, icvvTrack2UnSize, icvvStatus, icvvParameters
  - Automatic UN size calculation for cards with Track bitmap support
  - Formatted iCVV parameter storage for analysis

- **Contact Mode Toggle** (CardReadingViewModel)
  - `forceContactMode` flag with `setContactMode()` function
  - PPSE respects mode: forced 1PAY (contact) or auto 2PAY→1PAY fallback
  - User-controllable for testing different card interfaces (contactless vs contact)
  - Automatic fallback when contactless fails

**Enhanced:**
- **Comprehensive TLV Parsing** (CardReadingViewModel)
  - `displayParsedData()` uses EmvTlvParser for ALL tags (60-80+ tags vs previous 17)
  - `parsedEmvFields` state variable accumulates all tags across workflow phases
  - `buildRealEmvTagsMap()` uses parsedEmvFields instead of hardcoded subset
  - `extractDetailedEmvData()` uses EmvTlvParser for complete extraction
  - Template-aware recursive parsing with error/warning reporting

- **CDOL1 Extraction Bug Fixes** (CardReadingViewModel)
  - Skip READ RECORD responses to avoid RSA certificate false matches
  - Validate CDOL1 length (must be ≥4 hex chars for valid tag-length pair)
  - Byte-boundary search with proper length validation
  - Example: "9F3704" valid, "F6" invalid

**Technical Details:**
- EmvTlvParser.clearRocaAnalysisResults() called at workflow start
- checkRocaVulnerability() called after each TLV parse (PPSE, AID, GPO, READ_RECORD, GENERATE AC, GET DATA)
- extractRocaAnalysisDetails() formats vulnerability info with tag ID, confidence, key size, fingerprint
- All changes follow Universal Laws (read-map-validate pattern)
- Thread-safe UI updates with withContext(Dispatchers.Main)

**Commits:**
- `dc49ad6` - Complete ROCA integration + iCVV/Dynamic CVV + Contact mode toggle + Comprehensive TLV parsing
- `5fe42a1` - Fix ROCA property references (keySize, fingerprint)

**Build Status:** ✅ BUILD SUCCESSFUL  
**APK Size:** 74 MB  
**Files Modified:** 2 (CardReadingViewModel.kt, EmvCardData.kt)  
**Lines Changed:** +631, -182

---

## [Phase 3] - 2025-10-09

### 🎉 UI Automation System Complete (25% → 100%)

**Added:**
- **UIAutomationCommands.kt** (520 lines) - Complete UI automation engine
  - `dump_ui` - Get current screen and visible views
  - `find` - Find UI elements by text/id/type
  - `click` - Click elements or coordinates
  - `input` - Input text into fields
  - `screenshot` - Capture screen to PNG file
  - `hierarchy` - Complete view hierarchy
  - `assert_visible` - Assert element visibility
  - `back` - Navigate back programmatically

**Enhanced:**
- **DebugCommandProcessor.kt** - Integrated 8 UI automation commands
- Total ADB debug commands: 16 (8 backend + 8 UI automation)

**Documentation:**
- Complete UI automation guide (699 lines)
- 4 automated test scenarios (bash scripts)
- Python automation framework
- Bash test framework

**Commits:**
- `d4b3958` - Add complete UI automation system to ADB debug
- `5b54217` - Add complete UI automation documentation
- `638806c` - Add UI automation mission complete summary

---

### 🔧 PN532 Hardware Testing System

**Added:**
- **pn532_controller.py** (750 lines) - Complete PN532 protocol implementation
  - Connection management via rfcomm0 Bluetooth serial
  - Low-level protocol (frame building, checksums, ACK/NACK)
  - Card detection (ISO14443A)
  - APDU transceive
  - Card emulation mode
  - 3 built-in test scenarios

**Documentation:**
- **PN532_TESTING_GUIDE.md** (400 lines) - Complete hardware setup guide
- **PN532_QUICK_REF.md** (180 lines) - Quick reference card
- **setup_pn532_testing.sh** - Automated environment setup
- **requirements.txt** - Python dependencies (pyserial)

**Test Scenarios:**
1. Phone reads NFC card (Android NFC)
2. PN532 reads card via Bluetooth
3. PN532 emulates card, phone reads it

**Commits:**
- `0ba3564` - Add PN532 terminal controller via rfcomm0
- `2ad3127` - Add PN532 quick reference card

---

### 🎯 Terminal Fuzzer Features Complete

**Added (7 files, 1,281 lines):**

1. **Navigation Button** (AnalysisScreen.kt)
   - Added Terminal Fuzzer as first tool with BugReport icon

2. **NFC Fuzzing Executor** (228 lines)
   - 3 execution modes: PN532_HARDWARE, ANDROID_NFC, MOCK
   - Timeout handling with auto-reconnection
   - Mock response generation for testing

3. **Room Database Persistence** (330 lines)
   - **FuzzingSessionEntity** - Session metadata and metrics
   - **FuzzingAnomalyEntity** - Discovered vulnerabilities
   - **TerminalEntity** - Terminal mapping and history
   - **FuzzingPresetEntity** - Vulnerability-specific configurations
   - **FuzzingSessionDao** - 30+ query methods with Flow support

4. **JSON Export System** (280 lines)
   - Single/multiple session export
   - Comprehensive metrics (tests executed, coverage, anomalies)
   - Timestamped file generation
   - Pretty-printed JSON format

5. **Crash Reproducibility Tester** (240 lines)
   - Automatic reproducibility testing (configurable attempts)
   - Reproduction rate calculation
   - Consistency checks (null responses, timeouts, identical behavior)
   - Comprehensive text report generation

6. **Fuzzing Presets** (350 lines)
   - 9 vulnerability-specific presets:
     * ROCA_VULNERABILITY (200 tests, 5 tests/sec)
     * TRACK2_MANIPULATION (300 tests, 10 tests/sec)
     * CVM_BYPASS (250 tests, 8 tests/sec)
     * AIP_MODIFICATION (200 tests, 10 tests/sec)
     * CRYPTOGRAM_GENERATION (300 tests, 5 tests/sec)
     * PDOL_FUZZING (250 tests, 10 tests/sec)
     * AFL_FUZZING (200 tests, 15 tests/sec)
     * QUICK_SCAN (100 tests, 20 tests/sec)
     * DEEP_PROTOCOL_TEST (1000 tests, 10 tests/sec)

**Commits:**
- `fe676a3` - Add Terminal Fuzzer navigation button
- `22515b6` - Add NFC fuzzing executor with PN532/Android/Mock modes
- `41e9ca3` - Add Room DB persistence for fuzzing sessions
- `ac56408` - Add fuzzing features (export, reproducibility, presets)

---

## [Phase 2B] - 2025-10-09

### 🔥 Dynamic EMV Flow Enhancements

**Added:**
- **EmvTlvParser Consolidation** (650 lines)
  - Unified all TLV parsing to single parser
  - Proxmark3-inspired advanced methods:
    * `parseDol()` - PDOL/CDOL parsing
    * `parseAfl()` - AFL record reading
    * `parseAip()` - SDA/DDA/CDA detection
    * `parseCid()` - AC type parsing
    * `parseCvmList()` - CVM rules
    * `parseBitmask()` - AIP/TVR/AUC/CTQ/TTQ
    * `parseYymmdd()` - Date parsing
    * `parseNumeric()` - BCD numbers
    * `parseString()` - ASCII strings

**Enhanced:**
- **CardReadingViewModel** - Dynamic EMV flow implementation
  - `buildPdolData()` - Random unpredictable number (SecureRandom), current date/time
  - AIP-based flow strategy (detects SDA/DDA/CDA)
  - AFL-driven record reading (parses exact SFI/record combinations)
  - Template-aware recursive parsing (6F, A5, 70, 77, 80)
  - CDOL support (extracts CDOL1/CDOL2, builds CDOL data)
  - Threading safety (withContext(Dispatchers.Main) for UI updates)

**Commits:**
- `0d2c12a` - Dynamic EMV flow enhancements Phase 2
- Previous commits consolidated TLV parser

---

## [Phase 2A] - 2025-10-06 to 2025-10-09

### 🏗️ Module System Implementation

**Added (4 commits, 10 days, 2,366 insertions):**

1. **ModuleRegistry** (390 lines)
   - Centralized lifecycle manager
   - Dependency resolution with topological sort
   - Health monitoring (30-second intervals)
   - Auto-restart capabilities
   - Thread-safe operations with Mutex
   - Event system for inter-module communication

2. **BaseModule** (287 lines)
   - Abstract base class with lifecycle hooks
   - Automatic state management
   - Error recovery mechanisms
   - Graceful shutdown with cleanup

3. **FrameworkLogger** (150 lines)
   - Centralized logging system
   - File rotation support
   - Configurable log levels
   - Performance optimized

4. **Module Interface** (160 lines)
   - Contract for all modules
   - Health reporting
   - Dependency declaration

**Registered Modules (6):**
1. LoggingModule - Centralized logging
2. SecureMasterPasswordModule - Keystore-backed encryption
3. CardDataStoreModule - AES-256-GCM encrypted storage
4. PN532DeviceModule - Hardware device management
5. NfcHceModule - Host Card Emulation
6. EmulationModule - EMV attack emulation

**Module States:**
- UNINITIALIZED → INITIALIZING → RUNNING → ERROR/SHUTTING_DOWN

---

## [Phase 1B] - 2025-10-05 to 2025-10-06

### 🛡️ ROCA Vulnerability Detection & Exploitation

**Added (2 commits, 2 days):**

**Day 5:**
- **RocaDetector** (400+ lines)
  - Fingerprint testing with 167 prime divisibility checks
  - Vulnerable range detection (512/1024/2048-bit RSA keys)
  - Confidence scoring with probability estimation
  - Comprehensive vulnerability reporting

**Day 6:**
- **RocaExploiter** (420+ lines)
  - `factorModulus()` - Pollard's Rho algorithm implementation
  - `reconstructPrivateKey()` - Private key reconstruction from factors
  - PEM export for private keys
  - Time/cost estimation for exploitation
  - Integration with RocaDetector for automated workflow

**Enhanced:**
- **DatabaseViewModel** - ROCA batch scanning
- **CardReadingViewModel** - Automatic ROCA check after card scan
- **DashboardViewModel** - ROCA statistics display

---

## [Phase 1A] - 2025-10-01 to 2025-10-04

### 🔒 Encrypted Storage System

**Added (4 commits, 4 days):**

**Day 1:**
- kotlinx.serialization dependency
- BouncyCastle 1.70 for AES-256-GCM
- **CardDataStore** with encrypted storage
- Card profile serialization

**Day 2:**
- BouncyCastle provider initialization in NfSp00fApplication
- Security provider registration

**Day 3:**
- **CardProfileAdapter** for RecyclerView
- Migrated 3 ViewModels to encrypted storage:
  * DashboardViewModel
  * DatabaseViewModel
  * CardReadingViewModel

**Day 4:**
- **SecureMasterPasswordManager** with Android Keystore
- **MasterPasswordSetupScreen** UI (Material3 Compose)
- 5-level password strength validation
- EncryptedSharedPreferences integration

**Security:**
- AES-256-GCM encryption for all card data
- Hardware-backed key storage (Android Keystore)
- No plaintext storage of sensitive data
- Secure memory wiping after operations

---

## [Pre-Phase 1] - 2025-09-28 to 2025-09-30

### 🎨 UI/UX Foundation

**Added:**
- Material3 Design System integration
- 5 professional screens:
  * DashboardScreen - Statistics and quick actions
  * CardReadingScreen - NFC scanning with APDU terminal
  * EmulationScreen - Attack module management
  * DatabaseScreen - Card profile browsing
  * AnalysisScreen - Advanced analysis tools
- Matrix green theme (`#4CAF50`)
- Black status bar for premium feel
- Professional card-based layouts

### 📱 Core Features

**Added:**
- **EMV Card Reading:**
  * BER-TLV parsing with payneteasy library
  * Real-time APDU logging (20 commands visible)
  * TX/RX color coding (GREEN/BLUE)
  * Enhanced terminal with hex visualization

- **Emulation System:**
  * 5 attack modules (Track2, CVM, AIP, Cryptogram, PPSE)
  * AttackChainCoordinator for multi-stage attacks
  * ApduDataExtractor for EMV data parsing
  * Real-time attack monitoring

- **Database System:**
  * Room database for card profiles
  * Search and filter capabilities
  * Export/import functionality

- **Hardware Integration:**
  * PN532DeviceModule for external NFC hardware
  * Bluetooth and USB support
  * Auto-discovery and connection management

### 🏛️ Architecture

**Established:**
- Single-activity architecture (MainActivity as host)
- MVVM pattern with ViewModels
- Jetpack Compose for all UI
- Dependency injection preparation
- Clean Architecture principles

---

## [Initial Setup] - 2025-09-25

### 🚀 Project Initialization

**Created:**
- Android Studio project structure
- Package structure: `com.nfsp00f33r.app`
- Gradle build configuration
- Min SDK 28, Target SDK 34
- Kotlin 1.9.20 with serialization
- Compose BOM 2024.02.00

**Dependencies:**
- Jetpack Compose Material3
- Room 2.6.1
- BouncyCastle 1.70
- BER-TLV 1.0-11
- kotlinx.coroutines 1.7.3
- Timber for logging

---

## Statistics

### Total Contributions (Phases 1-3)
- **Commits:** 25+ commits
- **Lines Added:** 12,000+ lines of Kotlin/Python code
- **Documentation:** 3,500+ lines of markdown
- **Files Created:** 100+ new files
- **Build Status:** ✅ BUILD SUCCESSFUL

### Module Breakdown
- **Core Framework:** 6 registered modules
- **Attack Modules:** 5 EMV attack types
- **UI Screens:** 5 professional Material3 screens
- **Security Features:** ROCA detection, AES-256-GCM encryption
- **Debug Commands:** 16 ADB commands (8 backend + 8 UI)
- **Fuzzing Presets:** 9 vulnerability-specific configurations
- **Hardware Support:** PN532 via Bluetooth/USB + Android internal NFC

---

## Future Releases

See `FEATURES.md` for upcoming features and roadmap.

---

**Format:** [Phase] - YYYY-MM-DD  
**Versioning:** Phase-based development cycle  
**Status:** ✅ Phase 3 Complete, Phase 4 Planning
