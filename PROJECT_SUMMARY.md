# PROJECT SUMMARY - Quick Reference for AI Agents

**Last Updated**: October 9, 2025  
**Current Phase**: Phase 2A Complete + Quick Wins Integration  
**Build Status**: ✅ BUILD SUCCESSFUL (crash on runtime - under investigation)  
**Package**: `com.nfsp00f33r.app`  
**Branch**: `feature/framework-adoption`

---

## 🚨 CRITICAL ISSUE (Active Investigation)

**App crashes on launch with**:
```
java.lang.IllegalStateException: CardDataStoreModule not initialized. 
Application may not have started properly.
at NfSp00fApplication$Companion.getCardDataStoreModule(NfSp00fApplication.kt:243)
at DashboardViewModel.<init>(DashboardViewModel.kt:47)
```

**Root Cause**: DashboardViewModel tries to access `NfSp00fApplication.cardDataStoreModule` (via companion object) before `Application.onCreate()` completes module initialization.

**Location**: `DashboardViewModel.kt` line 47 (initialization block)

**Next Steps**:
1. Change companion object access to lazy initialization in ViewModels
2. Use ModuleRegistry.getModule() instead of direct companion access
3. Add null checks or use suspend initialization in ViewModels
4. Test module initialization order in Application.onCreate()

---

## 📁 Project Structure

```
/home/user/DEVCoDE/FINALS/nf-sp00f33r/
├── android-app/                    # MAIN APPLICATION - DO NOT MODIFY STRUCTURE
│   ├── src/main/java/              # Source code (Kotlin in java/ dir)
│   │   └── com/nfsp00f33r/app/
│   │       ├── activities/         # MainActivity.kt (Compose host)
│   │       ├── application/        # NfSp00fApplication.kt (App class)
│   │       ├── core/               # Module system (ModuleRegistry, BaseModule, FrameworkLogger)
│   │       ├── emulation/          # EmulationModule + 5 attack modules
│   │       ├── roca/               # RocaDetector, RocaExploiter, RocaVulnerabilityAnalyzer
│   │       ├── screens/            # Compose UI (Dashboard, CardReading, Emulation, Database)
│   │       ├── storage/            # CardDataStore, SecureMasterPasswordManager, modules
│   │       └── models/             # Data classes (CardProfile, EmulationProfile, AttackType)
│   ├── build.gradle                # Module dependencies
│   └── gradle.properties           # Build properties
├── backups/                        # PRESERVE - Timestamped backups
├── scripts/                        # Development tools (audit, naming, corruption checks)
├── .github/instructions/           # AI MEMORY - Read before making changes
├── build.gradle                    # Root build config
├── settings.gradle                 # Project modules
├── README.md                       # User documentation
└── PROJECT_SUMMARY.md              # This file (AI quick reference)
```

---

## 🎯 Current State

### Completed Features
- ✅ **Phase 1A**: Encrypted storage with AES-256-GCM (CardDataStore)
- ✅ **Phase 1B**: ROCA vulnerability detection and exploitation
- ✅ **Phase 2A**: Module system (ModuleRegistry, 6 modules registered)
- ✅ **Quick Wins Integration**: Analytics + ROCA ViewModels, automatic ROCA checks
- ✅ **Build System**: Compiles successfully, zero compilation errors
- ✅ **5 EMV Attack Modules**: Track2, CVM, AIP, Cryptogram, PPSE

### Active Work
- 🔴 **Debugging**: App crash on launch (module initialization order)
- ⏳ **UI Integration**: ViewModels ready, screens need updating for analytics display
- ⏳ **Testing**: Blocked by crash, cannot test UI flows

### Pending Tasks
1. **Fix module initialization crash** (CRITICAL)
2. Update Dashboard/Database/CardReading screens with analytics UI
3. End-to-end testing of all features
4. Documentation updates

---

## 🏗️ Architecture Overview

### Module System (Phase 2A)
**File**: `android-app/src/main/java/com/nfsp00f33r/app/core/ModuleRegistry.kt`

**Registered Modules** (6 total):
1. **LoggingModule**: Centralized logging with FrameworkLogger
2. **SecureMasterPasswordModule**: Keystore-backed encryption keys
3. **CardDataStoreModule**: AES-256-GCM encrypted card storage
4. **EmulationModule**: 5 attack modules + AttackAnalytics + RocaBatchScanner
5. **RocaAnalysisModule**: ROCA vulnerability analysis
6. **ApduModule**: APDU data extraction

**Lifecycle**: Initialize → Start → Health Monitor (30s) → Stop → Cleanup

**Initialization Order** (in `NfSp00fApplication.onCreate()`):
```kotlin
1. BouncyCastle security provider
2. SecureMasterPasswordManager
3. ModuleRegistry.initialize()
4. LoggingModule → register
5. SecureMasterPasswordModule → register
6. CardDataStoreModule → register (stores in companion object)
7. EmulationModule → register
8. Start all modules (async)
```

### ViewModels (Compose)
**File**: `android-app/src/main/java/com/nfsp00f33r/app/screens/`

**Key ViewModels**:
- `DashboardViewModel`: Analytics summary, recent attacks, card stats
- `CardReadingViewModel`: Card scanning + automatic ROCA check
- `DatabaseViewModel`: Card profiles + ROCA batch scanning
- `EmulationViewModel`: Attack execution, profile management
- `AnalysisViewModel`: Dedicated analytics + ROCA scanning UI

**Problem**: ViewModels access `NfSp00fApplication.cardDataStoreModule` in `init{}` block before module initialization completes.

### Enhanced Features (Quick Wins Integration)
**Files**: `android-app/src/main/java/com/nfsp00f33r/app/emulation/`

1. **AttackAnalytics.kt** (310 lines): Attack statistics, success rates, timing analysis
2. **RocaBatchScanner.kt** (380 lines): Batch ROCA vulnerability scanning
3. **ApduDataExtractor.kt** (395 lines): APDU command/response extraction
4. **AttackChainCoordinator.kt** (325 lines): Multi-attack orchestration (not integrated)

---

## 🔐 Security Architecture

### Encryption Flow
```
User Data → CardProfile → JSON Serialization → AES-256-GCM Encryption → EncryptedSharedPreferences
                                                      ↑
                                            Master Key (Android Keystore)
```

**Key Components**:
- `SecureMasterPasswordManager`: Keystore-backed password storage
- `CardDataStore`: Encrypted card profile persistence
- `BouncyCastle`: Cryptographic provider (AES-256-GCM)

### ROCA Detection Flow
```
Card Scan → Extract ICC Public Key → RocaDetector.isVulnerable() → 
            Fingerprint Test (167 primes) → Confidence Score → UI Alert
```

**Integration**: Automatic check in `CardReadingViewModel.onCardRead()` after save

---

## 🛠️ Development Guidelines

### Universal Laws (STRICTLY ENFORCED)
1. ❌ **No safe-call operators** (`?.`) in production code
2. ✅ **PascalCase** for classes, **camelCase** for methods
3. ✅ **Batch operations** for multi-file changes
4. ✅ **BUILD SUCCESSFUL** before task completion
5. ❌ **No placeholders** or TODO comments in production code
6. ✅ **DELETE→REGENERATE** protocol for file corruption
7. ✅ **No feature regression** - all functionality must be preserved

### Code Standards
```kotlin
// ❌ WRONG: Safe-call operator
val data = module?.getData()

// ✅ CORRECT: Explicit null check
if (module != null) {
    val data = module.getData()
} else {
    throw IllegalStateException("Module not initialized")
}
```

### Build Commands
```bash
# Full build
cd android-app && ./gradlew build

# Debug APK
./gradlew assembleDebug

# Install on device
adb install -r build/outputs/apk/debug/android-app-debug.apk

# Clear logcat and launch
adb logcat -c && adb shell am start -n com.nfsp00f33r.app/.activities.MainActivity
```

---

## 📊 Statistics

### Codebase Metrics
- **Total Lines**: ~15,000+ (excluding backups)
- **Kotlin Files**: 50+ production files
- **Modules**: 6 registered in ModuleRegistry
- **Attack Modules**: 5 EMV attack implementations
- **ViewModels**: 5 Compose ViewModels
- **Screens**: 4 primary Compose screens

### Phase Breakdown
- **Phase 1A** (Encrypted Storage): 4 days, 4 commits
- **Phase 1B** (ROCA Detection): 2 days, 2 commits
- **Phase 2A** (Module System): 10 days, 4 commits, 2,366 insertions
- **Quick Wins Integration**: 3 ViewModels, automatic ROCA checks

---

## 🐛 Debugging Tips

### Check Module Initialization
```bash
adb logcat | grep -E "NfSp00fApplication|ModuleRegistry|CardDataStoreModule"
```

### Get Crash Logs
```bash
adb logcat -c && adb shell am start -n com.nfsp00f33r.app/.activities.MainActivity && sleep 3 && adb logcat -d | grep -E "FATAL|AndroidRuntime"
```

### Check Build Configuration
```bash
cd android-app && ./gradlew dependencies
```

---

## 📝 AI Agent Instructions

### When Starting New Chat
1. **Read this file first** for project context
2. **Check** `.github/instructions/memory.instructions.md` for workspace memory
3. **Review** known issues section above
4. **Verify** current branch: `feature/framework-adoption`

### Before Making Changes
1. **Check BUILD status**: `./gradlew build` must be successful
2. **Read relevant files**: Don't assume, verify actual code
3. **Follow naming conventions**: PascalCase classes, camelCase methods
4. **No safe-call operators**: Explicit null checks only

### After Making Changes
1. **Verify compilation**: `BUILD SUCCESSFUL` required
2. **Test on device**: Install APK and check for crashes
3. **Update this file**: If major changes made
4. **Document issues**: Add to Known Issues section if bugs found

---

## 🔗 Key Files Reference

### Application Core
- `android-app/src/main/java/com/nfsp00f33r/app/application/NfSp00fApplication.kt` - App entry point, module initialization
- `android-app/src/main/java/com/nfsp00f33r/app/core/ModuleRegistry.kt` - Module lifecycle manager

### ViewModels (Current Crash Location)
- `android-app/src/main/java/com/nfsp00f33r/app/screens/dashboard/DashboardViewModel.kt` - **CRASH HERE** line 47
- `android-app/src/main/java/com/nfsp00f33r/app/screens/cardreading/CardReadingViewModel.kt` - ROCA auto-check
- `android-app/src/main/java/com/nfsp00f33r/app/screens/database/DatabaseViewModel.kt` - Batch scanning

### Enhanced Features
- `android-app/src/main/java/com/nfsp00f33r/app/emulation/AttackAnalytics.kt` - Analytics engine
- `android-app/src/main/java/com/nfsp00f33r/app/emulation/RocaBatchScanner.kt` - ROCA scanner
- `android-app/src/main/java/com/nfsp00f33r/app/roca/RocaDetector.kt` - Vulnerability detection

### Storage & Encryption
- `android-app/src/main/java/com/nfsp00f33r/app/storage/CardDataStore.kt` - Encrypted persistence
- `android-app/src/main/java/com/nfsp00f33r/app/storage/SecureMasterPasswordManager.kt` - Keystore manager

---

## 📞 Quick Commands

```bash
# Build project
cd /home/user/DEVCoDE/FINALS/nf-sp00f33r/android-app && ./gradlew build

# Install APK
adb install -r /home/user/DEVCoDE/FINALS/nf-sp00f33r/android-app/build/outputs/apk/debug/android-app-debug.apk

# Launch app
adb shell am start -n com.nfsp00f33r.app/.activities.MainActivity

# View crash logs
adb logcat -d | grep -A 50 "FATAL EXCEPTION"

# Clear build cache
cd android-app && ./gradlew clean

# Check connected devices
adb devices
```

---

**End of Project Summary**  
**For detailed documentation, see README.md**  
**For code standards, see .github/instructions/law.instructions.md**
