# 🎯 Dashboard + Card Reading Debug Report
**Date:** October 9, 2025  
**Session:** Loaded from .github/LOAD_SESSION.md  
**Status:** ✅ BUILD SUCCESSFUL | APK Installed | App Running (PID: 18073)

---

## 📊 Current Implementation Status

### ✅ Completed Features

**Dashboard Screen:**
- Real-time hardware detection (NFC, Bluetooth, PN532)
- Card statistics display (total cards, scanned today, unique brands, APDU counts)
- Recent cards section with VirtualCardView
- Hardware score calculation (0-100)
- PN532 detailed status card
- Hardware components grid (5 components)
- 3-second refresh cycle
- Empty state handling ("No cards scanned yet")
- Material3 dark theme with matrix green (#4CAF50)

**Card Reading Screen:**
- Proxmark3 Iceman EMV workflow implementation
- PPSE → AID Selection → GPO → Record Reading
- Dynamic PDOL data generation (SecureRandom, SimpleDateFormat)
- AIP-based flow strategy (SDA/DDA/CDA detection)
- AFL-driven record reading (EmvTlvParser integration)
- Real-time APDU terminal (TX/RX color-coded)
- EMV data extraction and display
- ROCA vulnerability detection
- NFC tag monitoring from MainActivity
- Reader selection (Android NFC, PN532 Bluetooth/USB, Mock)
- Technology selection (EMV/ISO-DEP, Auto-Detect)
- Card data persistence via CardDataStore (AES-256-GCM)

**Infrastructure:**
- 6 registered modules (all RUNNING state)
- Module system with health monitoring
- Encrypted storage (CardDataStore + SecureMasterPasswordManager)
- PN532DeviceModule integration
- EmulationModule with 5 attack types
- Threading safety (withContext(Dispatchers.Main) for UI updates)

---

## 🔍 Known Architecture

### DashboardViewModel
**Initialization:**
```kotlin
init {
    initializeServices()
    setupCardProfileListener()
}
```

**Key Features:**
1. Waits for NfSp00fApplication initialization (50 attempts × 100ms)
2. Uses simplified hardware polling (not full HardwareDetectionService)
3. Polls NFC/Bluetooth adapters directly
4. Checks PN532Module.isConnected() with error handling
5. Loads cards from CardDataStore (encrypted)
6. Converts only profiles with real PAN data (length >= 13)
7. Filters out cards without valid data
8. Updates every 3 seconds via startPeriodicRefresh()

**Statistics Calculation:**
- totalCards: profiles.size
- cardsToday: Count where createdAt > 24h ago
- uniqueBrands: Detected from PAN (VISA, MASTERCARD, AMEX, DISCOVER)
- totalApduCommands: Sum of profile.apduLogs.size
- securityIssues: Profiles with unencrypted data or missing cryptograms

### CardReadingViewModel
**Initialization:**
```kotlin
init {
    initializeHardwareDetection()
    setupCardProfileListener()
    startNfcMonitoring()
}
```

**Key Features:**
1. Monitors MainActivity.currentNfcTag every 500ms
2. Processes NFC tags via executeProxmark3EmvWorkflow()
3. Implements complete EMV flow:
   - PPSE selection (2PAY.SYS.DDF01)
   - AID selection (tries MasterCard, Visa, Amex, Discover)
   - GPO with dynamic PDOL
   - AFL-based record reading
   - Data extraction and parsing
4. Uses EmvTlvParser for all TLV operations
5. Updates parsedEmvFields map for real-time display
6. Saves to CardDataStore after successful scan
7. ROCA vulnerability check automatic

---

## 🐛 Potential Issues to Debug

### Issue Category 1: Dashboard Initialization

**Symptom:** Loading screen "Initializing Hardware Detection..."

**Potential Causes:**
1. Application initialization timeout (waiting for 5 seconds)
2. Permission issues (Location, Bluetooth not granted)
3. PN532Module throwing exceptions during isConnected() check
4. HardwareStatus not updating properly

**Debug Steps:**
- [ ] Check if isInitialized flag ever becomes true
- [ ] Verify NfSp00fApplication.isInitializationComplete() returns true
- [ ] Test PN532Module.isConnected() in isolation
- [ ] Check permission states in PermissionManager

### Issue Category 2: Card Data Display

**Symptom:** "No cards scanned yet" even after scans

**Potential Causes:**
1. CardDataStore.getAllProfiles() returning empty list
2. Profile PAN filtering too strict (length < 13)
3. CardProfileAdapter conversion issues
4. Encryption/decryption failures

**Debug Steps:**
- [ ] Query CardDataStore directly to see stored profiles
- [ ] Check if scans are saving to database
- [ ] Verify PAN extraction logic in EMV parsing
- [ ] Test CardProfileAdapter.toAppProfile() conversion

### Issue Category 3: NFC Tag Detection

**Symptom:** Cards not detected when tapped

**Potential Causes:**
1. MainActivity NFC foreground dispatch not enabled
2. IsoDep.get(tag) returning null
3. NFC intent not delivered to onNewIntent()
4. MainActivity.currentNfcTag not being set
5. CardReadingViewModel not polling fast enough

**Debug Steps:**
- [ ] Verify enableNfcReaderMode() is called in onResume()
- [ ] Check NFC adapter isEnabled status
- [ ] Test onNewIntent() delivery with logs
- [ ] Monitor MainActivity.nfcDebugMessage updates

### Issue Category 4: Threading Issues

**Symptom:** UI updates not visible or app freezes

**Potential Causes:**
1. UI state updates not wrapped in withContext(Dispatchers.Main)
2. Heavy operations blocking Main thread
3. Coroutine scope issues with ViewModel lifecycle

**Debug Steps:**
- [ ] Audit all mutableStateOf assignments
- [ ] Verify all apduLog updates use withContext(Dispatchers.Main)
- [ ] Check scanState updates in processNfcTag()
- [ ] Test parsedEmvFields updates

---

## 🧪 Manual Test Plan

### Dashboard Tests (15 minutes)

1. **App Launch** (2 min)
   - Cold start from launcher
   - Observe loading screen
   - Time until Dashboard appears
   - Check for crashes

2. **Hardware Status** (3 min)
   - Verify NFC status (Active/Disabled/Not Available)
   - Verify Bluetooth status
   - Check PN532 status (Ready/Scanning)
   - Hardware score displayed (0-100)
   - Phase indicator shown

3. **Card Statistics** (2 min)
   - Total Cards count
   - Scanned Today count
   - Hardware Score badge
   - APDU Commands count
   - Card Brands count
   - PN532 Status badge

4. **Recent Cards** (3 min)
   - Empty state: "No cards scanned yet"
   - LazyRow scrolling (if cards exist)
   - VirtualCardView display
   - Card count indicator

5. **Real-time Updates** (5 min)
   - Wait for 3-second refresh
   - Toggle Bluetooth on/off
   - Toggle NFC on/off
   - Observe score changes
   - Status message updates

### Card Reading Tests (20 minutes)

1. **UI Initial State** (3 min)
   - Navigate to Card Reading tab
   - Verify "EMV CARD SCANNER" header
   - Check reader selection dropdown
   - Check protocol selection dropdown
   - Scan state shows "IDLE"
   - NFC status shows "WAITING"
   - APDU Terminal shows waiting message

2. **Reader Management** (4 min)
   - Open reader dropdown
   - Select "Android NFC"
   - Verify reader status updates
   - Open protocol dropdown
   - Select "EMV/ISO-DEP"
   - Test "Auto-Detect" option

3. **Scan Controls** (3 min)
   - Click "START SCAN" button
   - Verify state changes to "SCANNING"
   - Button text changes to "STOP SCAN"
   - Click "STOP SCAN"
   - State returns to "IDLE"

4. **NFC Card Detection** (5 min)
   - Start scanning
   - Tap NFC card to phone
   - Observe card detection event
   - Check MainActivity.nfcDebugMessage
   - Verify auto-navigation to Card Reading tab
   - Check if APDU log starts populating

5. **EMV Workflow** (5 min)
   - Observe PPSE selection (TX/RX)
   - Watch AID selection attempts
   - Monitor GPO execution
   - View record reading commands
   - Check status message updates
   - Verify progress bar movement

6. **Data Display** (5 min)
   - EMV fields appear in real-time
   - ROCA status card shows
   - Virtual card view appears
   - APDU terminal scrolls
   - Card count increments

---

## 🛠️ Debugging Tools

### ADB Commands

```bash
# Launch app
adb shell am start -n com.nfsp00f33r.app/.activities.SplashActivity

# Monitor logs
adb logcat | grep -E "(nfsp00f33r|DashboardViewModel|CardReadingViewModel)"

# Check NFC status
adb shell dumpsys nfc | grep "mState"

# Check permissions
adb shell dumpsys package com.nfsp00f33r.app | grep "permission"

# Navigate tabs
adb shell input tap 200 2400  # Dashboard
adb shell input tap 400 2400  # Card Reading
adb shell input tap 600 2400  # Emulation

# Screenshot
adb shell screencap -p /sdcard/debug.png && adb pull /sdcard/debug.png

# Memory usage
adb shell dumpsys meminfo com.nfsp00f33r.app | grep "TOTAL"
```

### Code Inspection Points

**DashboardViewModel.kt:**
- Line 100-134: `initializeServices()` - Check initialization flow
- Line 153-218: `setupHardwareStatusPolling()` - Hardware detection logic
- Line 242-298: `refreshCardData()` - Card data loading
- Line 307-351: `calculateCardStatistics()` - Statistics calculation

**CardReadingViewModel.kt:**
- Line 144-165: `startNfcMonitoring()` - NFC tag polling
- Line 171-189: `processNfcTag()` - Tag processing entry point
- Line 198-500: `executeProxmark3EmvWorkflow()` - Complete EMV flow
- Line 1600-1655: `saveCardProfile()` - Data persistence

**MainActivity.kt:**
- Line 182-199: `enableNfcReaderMode()` - NFC foreground dispatch
- Line 219-243: `handleNfcIntent()` - Intent processing
- Line 253-273: `handleNfcTag()` - Tag routing logic
- Line 157-162: `updateNfcState()` - Shared state management

---

## ✅ Success Criteria

**Dashboard:**
- [ ] Loads within 3 seconds
- [ ] Hardware score displays correctly (0-100)
- [ ] NFC/Bluetooth status accurate
- [ ] PN532 status updates (if hardware present)
- [ ] Card statistics show real data (no placeholders)
- [ ] Recent cards display (or empty state)
- [ ] Real-time updates every 3 seconds

**Card Reading:**
- [ ] UI loads instantly
- [ ] Reader selection works
- [ ] Scan button toggles properly
- [ ] NFC card detected within 1 second
- [ ] APDU log populates in real-time
- [ ] EMV workflow completes (<5 seconds)
- [ ] Parsed fields display correctly
- [ ] ROCA check runs automatically
- [ ] Card saves to database
- [ ] Dashboard updates with new card

---

## 📝 Next Actions

1. **Run manual tests** - Follow test plan above
2. **Document findings** - Update DEBUG_SESSION_OCT9.md
3. **Apply fixes** - Address issues systematically
4. **Rebuild and verify** - Test after each fix
5. **Commit changes** - Descriptive messages per universal laws

---

**Session Status:** 🟡 READY FOR TESTING  
**Estimated Time:** 35-40 minutes for complete test suite  
**Priority:** Dashboard initialization + NFC detection  
