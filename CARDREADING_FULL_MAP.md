# CardReadingViewModel - Complete Mapping for Clean Refactor

## Phase 1: COMPLETE MAPPING

### UI Consumer Dependencies (CardReadingScreen.kt)

**Public Functions Called by UI:**
- `getReaderDisplayName(reader: ReaderType): String`
- `setContactMode(enabled: Boolean)`
- `selectReader(reader: ReaderType)`
- `selectTechnology(technology: NfcTechnology)`
- `toggleAutoSelect()`
- `startScanning()`
- `stopScanning()`

**State Variables Observed by UI:**
- `scanState: ScanState` - Current scanning state
- `selectedReader: ReaderType?` - Currently selected hardware reader
- `availableReaders: List<ReaderType>` - List of available readers
- `selectedTechnology: NfcTechnology` - Current NFC technology mode
- `isAutoSelectEnabled: Boolean` - Auto-detection toggle
- `forceContactMode: Boolean` - Contact mode toggle
- `statusMessage: String` - Current status message
- `scannedCards: List<VirtualCard>` - List of scanned cards for carousel
- `apduLog: List<ApduLogEntry>` - APDU command log
- `currentPhase: String` - Current EMV workflow phase
- `progress: Float` - Progress indicator (0.0 to 1.0)
- `parsedEmvFields: Map<String, String>` - Parsed EMV data for display
- `currentEmvData: EmvCardData?` - Current card data
- `readerStatus: String` - Reader connection status
- `hardwareCapabilities: Set<String>` - Hardware capabilities
- `rocaVulnerabilityStatus: String` - ROCA vulnerability status message
- `isRocaVulnerable: Boolean` - ROCA vulnerability flag

**Enums Used by UI:**
- `ReaderType` - ANDROID_NFC, PN532_BLUETOOTH, PN532_USB, MOCK_READER
- `NfcTechnology` - EMV_CONTACTLESS, AUTO_SELECT, etc.
- `ScanState` - IDLE, SCANNING, CARD_DETECTED, EXTRACTING_EMV, SCAN_COMPLETE, ERROR

### Data Classes
- `AidEntry(aid: String, label: String, priority: Int)` - AID from PPSE
- `SecurityInfo(hasSDA: Boolean, hasDDA: Boolean, hasCDA: Boolean, isWeak: Boolean, summary: String)` - Security analysis
- `SessionScanData` - Session tracking for Room database

### Private Functions - Core Workflow
- `init {}` - ViewModel initialization
- `startNfcMonitoring()` - Monitor MainActivity for NFC tags
- `processNfcTag(tag: android.nfc.Tag)` - Entry point for EMV workflow
- **`executeProxmark3EmvWorkflow(tag: android.nfc.Tag)`** - **TARGET FOR REFACTOR (1213 lines)**

### Private Helper Functions - Keep All These

**APDU Management:**
- `addApduLogEntry(command, response, statusWord, description, executionTime)`

**Data Building:**
- `buildPdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray`
- `buildCdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray`
- `buildGetDataApdu(tag: Int): ByteArray`
- `buildGenerateAcApdu(acType: Byte, cdolData: ByteArray): ByteArray`
- `buildInternalAuthApdu(ddolData: ByteArray): ByteArray`

**Data Extraction (from APDU responses):**
- `extractPdolFromAllResponses(apduLog): String`
- `extractCdol1FromAllResponses(apduLog): String`
- `extractCdol2FromAllResponses(apduLog): String`
- `extractAflFromAllResponses(apduLog): String`
- `extractCryptogramFromAllResponses(apduLog): String`
- `extractCidFromAllResponses(apduLog): String`
- `extractAtcFromAllResponses(apduLog): String`
- `extractAipFromAllResponses(apduLog): String`
- `extractTrack2FromAllResponses(apduLog): String`
- `extractExpiryFromAllResponses(apduLog): String`
- `extractCardholderNameFromAllResponses(apduLog): String`
- `extractCvmListFromAllResponses(apduLog): String`
- `extractIadFromAllResponses(apduLog): String`
- `extractLogFormatFromAllResponses(apduLog): String`
- `extractUnFromAllResponses(apduLog): String`
- `extractAppVersionFromAllResponses(apduLog): String`
- `extractAucFromAllResponses(apduLog): String`
- `extractAllAidsFromPpse(hexResponse): List<AidEntry>`
- `extractAidsFromPpseResponse(hexResponse): List<String>`
- `extractFciFromAidResponse(hexResponse): String`
- `extractAipFromResponse(hexResponse): String`
- `extractAflFromResponse(hexResponse): String`
- `extractAidFromResponse(hexResponse): String`
- `extractPanFromResponse(hexResponse): String`
- `extractPanFromTrack2(track2): String`
- `extractExpiryFromTrack2(track2): String`
- `extractDetailedEmvData(hexResponse): Map<String, String>`

**Data Parsing:**
- `parseAflForRecords(afl: String): List<Triple<Int, Int, Int>>`
- `parseCdol(cdolHex: String): List<EmvTlvParser.DolEntry>`
- `parseGenerateAcResponse(responseHex): GenerateAcResult`
- `parseInternalAuthResponse(responseHex): InternalAuthResult`

**Formatting:**
- `formatPan(hexPan): String`
- `formatTrack2(hexTrack2): String`
- `formatExpiryDate(hexExpiry): String`
- `formatEffectiveDate(hexDate): String`
- `hexToAscii(hex): String`

**Security Analysis:**
- `analyzeAip(aipHex: String): SecurityInfo`
- `supportsDda(aipHex): Boolean`
- `testRocaVulnerability(issuerPublicKeyHex): RocaTestResult`
- `isRocaFingerprint(modulus: BigInteger): Boolean`

**Status & Interpretation:**
- `interpretStatusWord(statusWord): String`
- `decodeCvmRule(cvmCode, cvmCondition): String`

**Database & Storage:**
- `createEmvCardData(cardId: String, tag: android.nfc.Tag): EmvCardData`
- `saveSessionToDatabase()`
- `exportEmvDataToJson(cardData, apduLog): String`

**Display & UI:**
- `displayParsedData(context, hexResponse)` (if exists)

**Hardware Management:**
- `initializeHardwareDetection()`
- `setupCardProfileListener()`
- `createMockEmvData(): EmvCardData`
- `detectCardBrand(pan): String`

**Lifecycle:**
- `onCleared()`

### Factory
- `class Factory(private val context: Context) : ViewModelProvider.Factory`

---

## Phase 2: ARCHITECTURE - Clean Modular Design

### Goal: Replace 1213-line `executeProxmark3EmvWorkflow()` with modular functions

**New Architecture:**
1. Main orchestrator: `executeProxmark3EmvWorkflow()` (~80 lines)
   - Initialize session
   - Call phase functions sequentially
   - Error handling
   - Finalize session

2. Phase functions (8 modular functions):
   - `executePhase1_PpseSelection(isoDep): List<AidEntry>` (~50 lines)
   - `executePhase2_AidSelection(isoDep, aidEntries): String` (~60 lines)
   - `executePhase3_Gpo(isoDep): String` (~70 lines)
   - `executePhase4_ReadRecords(isoDep, afl)` (~70 lines)
   - `executePhase5_GenerateAc(isoDep)` (~60 lines)
   - `executePhase6_GetData(isoDep): String` (~60 lines)
   - `executePhase7_TransactionLogs(isoDep, logFormat)` (~50 lines)
   - `finalizeSession(cardId, tag)` (~50 lines)

3. Helper: `sendPpseCommand(isoDep, ppseHex): Pair<String, String>` (~10 lines)

**Total: ~560 lines (down from 1213 lines)**

---

## Phase 3: GENERATION PLAN

**File Structure:**
```
1. Package + imports (same as original)
2. KDoc header (clean, concise)
3. Data classes (AidEntry, SecurityInfo, SessionScanData) - unchanged
4. ViewModel class declaration - unchanged
5. Lazy properties (pn532Module, cardDataStore, emvSessionDatabase, etc.) - unchanged
6. Session tracking variables - unchanged
7. Enums (ReaderType, NfcTechnology, ScanState) - unchanged
8. State variables (all var with mutableStateOf) - unchanged
9. init {} - unchanged
10. startNfcMonitoring() - unchanged
11. processNfcTag() - unchanged
12. **executeProxmark3EmvWorkflow() - NEW CLEAN VERSION**
13. **8 new phase functions - NEW**
14. **sendPpseCommand helper - NEW**
15. All existing helper functions - unchanged
16. Hardware management functions - unchanged
17. Public UI functions - unchanged
18. onCleared() - unchanged
19. Factory class - unchanged
```

**Preservation Requirements:**
- All public functions exactly as-is
- All state variables exactly as-is
- All enums exactly as-is
- All data classes exactly as-is
- All helper functions exactly as-is
- init, startNfcMonitoring, processNfcTag exactly as-is
- Factory exactly as-is

**Only Change:**
- Replace lines 285-1498 (executeProxmark3EmvWorkflow) with clean modular version

---

## Phase 4: VALIDATION

Check against Phase 1 mapping:
- [ ] All public functions present
- [ ] All state variables present
- [ ] All enums present
- [ ] All data classes present
- [ ] All helper functions present
- [ ] Clean executeProxmark3EmvWorkflow calls all helpers correctly
- [ ] No property name mismatches

---

## Phase 5: INTEGRATION

Verify:
- [ ] CardReadingScreen.kt still compiles
- [ ] All viewModel.property references still work
- [ ] All viewModel.function() calls still work
- [ ] No breaking changes to public API

---

## Phase 6: OPTIMIZATION

- Concise code (no bloat)
- Clear phase separation
- Proper error handling
- Consistent logging

---

## Phase 7: VERIFICATION

- [ ] ./gradlew compileDebugKotlin
- [ ] BUILD SUCCESSFUL
- [ ] No compilation errors
- [ ] UI loads without crashes
