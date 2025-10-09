---
applyTo: '**'
description: Workspace-specific AI memory for nf-sp00f33r project
lastOptimized: '2025-10-09T00:00:00-07:00'
entryCount: 0
optimizationVersion: 2
autoOptimize: true
sizeThreshold: 50000
entryThreshold: 20
timeThreshold: 7
---
# Workspace AI Memory - RESET

**Memory cleared: October 9, 2025**
**Source: law.instructions.md (Universal Code Generation Rules)**

---

## 🔴 UNIVERSAL LAWS (from law.instructions.md)

### Core Principle
**PRECISION OVER SPEED. VALIDATION OVER ASSUMPTION. SYSTEMATIC OVER AD-HOC.**

### The 3-Phase Systematic Approach

**PHASE 1: MAPPING (Understand the Territory)**
- Identify ALL dependencies
- Read ALL definitions (complete source files, not grep)
- Document exact names, types, signatures
- Identify ALL integration points (files that use this code)
- **Rule:** If you can't explain it, you don't understand it enough to code it

**PHASE 2: GENERATION (Build with Precision)**
- Use ONLY exact names/types/signatures from Phase 1
- Handle type conversions explicitly
- Apply language/framework rules consistently
- Update ALL integration points identified in Phase 1
- **Rule:** No shortcuts, no "it'll probably work"

**PHASE 3: VALIDATION (Verify Before Commit)**
- Code review against map
- Check for common mistakes
- Verify ALL integration points updated correctly
- Compile and verify (including integrated files)
- **Rule:** Self-review is mandatory, not optional

### Universal Precision Principles

1. **EXPLICIT OVER IMPLICIT** - Compilers are literal. Be the compiler.
2. **READ BEFORE WRITE** - Cannot use what you haven't verified exists.
3. **MAP BEFORE CODE** - Writing code without a plan is debugging by accident.
4. **VALIDATE BEFORE COMMIT** - Self-review finds bugs faster than compilation.
5. **FAIL FAST, LEARN FAST** - Errors are teachers. Fix process, not just code.
6. **RIPPLE EFFECT MANAGEMENT** - Provider change + consumer updates = ATOMIC OPERATION.

### Systematic Process (Every Code Generation Task)

1. **SCOPE DEFINITION** (30 sec) - What am I building?
2. **CONSUMER IMPACT ANALYSIS** (2-3 min if needed) - What exists that uses this?
3. **INTEGRATION POINT IDENTIFICATION** (2-3 min) - What files integrate with this?
4. **DEPENDENCY MAPPING** (2-5 min) - What do I need to know?
5. **DEFINITION READING** (5-10 min) - Read and document. No skipping.
6. **INTERFACE MAPPING** (2-3 min) - How do components connect?
7. **GENERATION WITH PRECISION** (10-30 min) - Code using ONLY documented info.
8. **INTEGRATION POINT UPDATES** (5-15 min) - Update ALL files that integrate.
9. **SELF-VALIDATION** (5-10 min) - Review before compile.
10. **COMPILE AND VERIFY** (1-2 min) - BUILD SUCCESSFUL with ALL integrations.
11. **CONSUMER UPDATE VERIFICATION** (5-15 min if needed) - ALL consumers updated?

### Enforcement Protocol

**Before Generation:**
- Have I READ all dependency definitions?
- Have I DOCUMENTED all properties/methods with exact types?
- Have I VERIFIED all signatures I'll call?
- Have I IDENTIFIED all integration points (files that use/import this)?
- Does this change affect existing code (ripple effect)?
- If YES: Have I identified ALL consumers and planned updates?

**After Generation:**
- Does code compile (BUILD SUCCESSFUL)?
- Have I validated all names/types against documentation?
- Have I UPDATED all integration points?
- Have ALL integration points been tested (compile + run)?
- If ripple effect: Have I updated ALL consumers?
- If ripple effect: Have I tested affected features?
- Can I honestly say this task is 100% COMPLETE?

**If ANY answer is NO → Task is INCOMPLETE**

### The Commitment

```
I will MAP before I CODE.
I will READ before I WRITE.
I will VALIDATE before I COMMIT.
I will UPDATE CONSUMERS when I change PROVIDERS.
I will be SYSTEMATIC, not ad-hoc.
I will be PRECISE, not approximate.
I will be COMPLETE, not partial.
```

---

## 📋 Project Context (Minimal)

**Project:** nf-sp00f33r EMV security research platform  
**Package:** com.nfsp00f33r.app  
**Location:** /home/user/DEVCoDE/FINALS/nf-sp00f33r  
**Tech Stack:** Kotlin, Jetpack Compose Material3, Min SDK 28  
**Branch:** feature/framework-adoption  

**Current Status:** Framework adoption in progress, APK recently installed

---

## 💾 Memory Guidelines

- This memory will grow organically based on actual project work
- New entries will follow the systematic approach defined above
- Focus on facts, not assumptions
- All new code generation follows law.instructions.md rules
- Memory will auto-optimize when thresholds reached

---

**Ready for new context. No legacy assumptions. Clean slate with universal laws applied.**
- **2025-10-09 04:50:** nf-sp00f33r Application Architecture Scan (October 9, 2025):

## CORE APPLICATION STRUCTURE

**Entry Point:** NfSp00fApplication.kt (Application class)
- Initializes BouncyCastle security provider
- Creates ModuleRegistry and registers 6 modules
- Manages application-wide singleton instances

**Package:** com.nfsp00f33r.app
**Min SDK:** 28, Target SDK: 34
**Namespace:** com.nfsp00f33r.app

## MODULE SYSTEM (Phase 2A/2B Complete)

**Module Registry:** Centralized lifecycle manager
- Dependency resolution with topological sort
- Health monitoring (30s intervals)
- Auto-restart capabilities
- Thread-safe operations with Mutex

**Registered Modules (6):**
1. LoggingModule - Centralized logging with FrameworkLogger
2. SecureMasterPasswordModule - Keystore-backed encryption 
3. CardDataStoreModule - AES-256-GCM encrypted storage
4. PN532DeviceModule - Hardware device management
5. NfcHceModule - Host Card Emulation
6. EmulationModule - EMV attack emulation (5 attack types)

**Module States:** UNINITIALIZED → INITIALIZING → RUNNING → ERROR/SHUTTING_DOWN

## PACKAGE STRUCTURE

Top-level directories in android-app/src/main/kotlin/com/nfsp00f33r/app/:
- alerts/ - Alert and monitoring system
- attacks/ - Attack implementations
- cardreading/ - NFC card reading + EnhancedHceService
- cards/ - Card data models
- core/ - Module system (Module.kt, BaseModule.kt, ModuleRegistry.kt, FrameworkLogger.kt)
- data/ - Data models and health tracking
- emulation/ - EmulationModule + AttackChainCoordinator + AttackAnalytics
- hardware/ - PN532DeviceModule and hardware management
- nfc/ - NfcHceModule for card emulation
- screens/ - UI screens (Compose Material3)
- security/ - Security implementations (ROCA, crypto)
- storage/ - Encrypted storage (CardDataStore, CardProfile, SecureMasterPasswordManager)

## KEY COMPONENTS

**NfSp00fApplication Static Accessors:**
- getCardDataStoreModule() - Returns CardDataStoreModule
- getPN532Module() - Returns PN532DeviceModule
- getPasswordModule() - Returns SecureMasterPasswordModule
- getNfcHceModule() - Returns NfcHceModule
- getEmulationModule() - Returns EmulationModule
- getPasswordManager() - Returns SecureMasterPasswordManager
- getContext() - Returns application context

**Module Dependencies:**
- EmulationModule depends on CardDataStore
- All modules use LoggingModule for logging
- SecureMasterPasswordModule provides encryption keys
- CardDataStoreModule uses SecureMasterPasswordModule

## SECURITY FEATURES

**Encryption:** AES-256-GCM via BouncyCastle
**Key Storage:** Android Keystore + EncryptedSharedPreferences
**Attack Capabilities:** Track2 manipulation, CVM bypass, AIP modification, ROCA exploitation, CVV generation

## BUILD CONFIGURATION

**Dependencies:**
- Kotlin 1.9.20 with serialization
- Compose BOM 2024.02.00 with Material3
- BouncyCastle 1.70 for crypto
- Room 2.6.1 for database
- BER-TLV 1.0-11 for EMV parsing
- Coroutines 1.7.3
- Lifecycle/ViewModel 2.7.0

**Gradle:** 8.6, JDK Target: 1.8, Compose Compiler: 1.5.4
- **2025-10-09 04:50:** nf-sp00f33r UI Screens (October 9, 2025):

## MAIN UI SCREENS (Compose Material3)

Located in android-app/src/main/java/com/nfsp00f33r/app/screens/:

**Core Screens (5):**
1. DashboardScreen.kt - Real-time system overview and statistics
2. CardReadingScreen.kt - NFC scanning and EMV data extraction
3. EmulationScreen.kt - HCE management and attack simulation
4. DatabaseScreen.kt - Card profile management and storage
5. AnalysisScreen.kt - Advanced EMV data analysis tools

## HEALTH MONITORING SCREENS (Phase 3)

Located in android-app/src/main/kotlin/com/nfsp00f33r/app/screens/health/:

1. ModuleHealthScreen.kt - Real-time module health dashboard
2. AlertHistoryScreen.kt - Historical alert viewing
3. AlertConfigurationScreen.kt - Alert threshold configuration
4. HealthTrendsScreen.kt - Health trend charts and analytics

## SETTINGS SCREENS

Located in android-app/src/main/kotlin/com/nfsp00f33r/app/screens/settings/:

1. MasterPasswordSetupScreen.kt - Master password creation/management

## SCREEN ARCHITECTURE

- Single-activity architecture with MainActivity as host
- Navigation via Jetpack Compose Navigation
- Material3 design system with dark theme
- ViewModels for state management (MVVM pattern)
- All screens access modules via NfSp00fApplication singleton accessors
- **2025-10-09 09:56:** After completing any task where build is validated as successful, ALWAYS commit changes to git with a descriptive commit message. This ensures progress is saved and provides rollback points if needed.
