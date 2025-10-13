# CardReadingViewModel - Clean Workflow Implementation
**Date:** October 12, 2025  
**Approach:** Fresh start from SELECT PPSE using 7-Phase Universal Laws  
**Status:** 🚀 READY TO IMPLEMENT

## 📋 Phase 1: MAPPING (Complete)

### Scope Definition
- **Task:** Replace messy 1665-line `executeProxmark3EmvWorkflow()` with clean modular workflow
- **Start Point:** SELECT PPSE command
- **Success Criteria:** 
  - Main orchestrator ≤ 50 lines
  - Each phase ≤ 200 lines
  - BUILD SUCCESSFUL
  - All EMV data extraction preserved
  - Testable, maintainable architecture

### Ripple Effect Analysis
- **Changing:** `executeProxmark3EmvWorkflow()` (private method)
- **Consumers:** Only `processNfcTag()` at line 268
- **Impact:** LOW - internal refactor, no public API changes
- **UI Dependencies:** CardReadingScreen reads state variables (currentPhase, progress, statusMessage, parsedEmvFields)
- **Conclusion:** Safe to refactor - no consumer updates needed beyond method itself

### Dependencies Mapped

#### Android NFC API
- `android.nfc.Tag` - NFC tag reference
- `android.nfc.tech.IsoDep` - ISO-DEP communication
  - `connect(): Unit` - Connect to card
  - `transceive(command: ByteArray): ByteArray` - Send APDU
  - `close(): Unit` - Disconnect
  - `isConnected(): Boolean` - Check connection

#### Data Classes (Lines 55-100)
```kotlin
data class AidEntry(
    val aid: String,           // AID hex string (tag 4F)
    val label: String,         // Application Label (tag 50)
    val priority: Int          // Application Priority (tag 87)
)

data class SecurityInfo(
    val hasSDA: Boolean,
    val hasDDA: Boolean,
    val hasCDA: Boolean,
    val isWeak: Boolean,
    val summary: String
)

data class SessionScanData(
    var sessionId: String = "",
    var scanStartTime: Long = 0L,
    var cardUid: String = "",
    val allTags: MutableMap<String, EnrichedTagData> = mutableMapOf(),
    val apduEntries: MutableList<ApduLogEntry> = mutableListOf(),
    var ppseResponse: Map<String, EnrichedTagData>? = null,
    val aidResponses: MutableList<Map<String, EnrichedTagData>> = mutableListOf(),
    var gpoResponse: Map<String, EnrichedTagData>? = null,
    val recordResponses: MutableList<Map<String, EnrichedTagData>> = mutableListOf(),
    var cryptogramResponse: Map<String, EnrichedTagData>? = null,
    var getDataResponse: Map<String, EnrichedTagData>? = null,
    var totalApdus: Int = 0,
    var successfulApdus: Int = 0,
    var scanStatus: String = "IN_PROGRESS",
    var errorMessage: String? = null
)
```

#### State Variables (Lines 155-200)
```kotlin
var scanState: ScanState by mutableStateOf(ScanState.IDLE)
var forceContactMode: Boolean by mutableStateOf(false)
var currentPhase: String by mutableStateOf("Ready")
var progress: Float by mutableStateOf(0f)
var statusMessage: String by mutableStateOf("...")
var apduLog: List<ApduLogEntry> by mutableStateOf(emptyList())
var parsedEmvFields: Map<String, String> by mutableStateOf(emptyMap())
var rocaVulnerabilityStatus: String by mutableStateOf("Not checked")
var isRocaVulnerable: Boolean by mutableStateOf(false)

private var currentSessionData: SessionScanData? = null
private var currentSessionId: String = ""
private var sessionStartTime: Long = 0L
```

#### Helper Functions (Keep Existing - Already Clean)
- `buildPdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray`
- `buildCdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray`
- `addApduLogEntry(command: String, response: String, statusWord: String, description: String, executionTime: Long)`
- `interpretStatusWord(statusWord: String): String`
- `analyzeAip(aipHex: String): SecurityInfo`
- `EmvTlvParser.parseResponse()`, `EmvTlvParser.parseDol()`, `EmvTlvParser.parseAfl()`

#### UI Update Pattern
```kotlin
withContext(Dispatchers.Main) {
    currentPhase = "Phase Name"
    progress = 0.0f to 1.0f
    statusMessage = "Status message"
}
```

## 🏗️ Phase 2: NEW ARCHITECTURE DESIGN

### Clean Workflow Structure

```
executeProxmark3EmvWorkflow(tag: Tag)  [ORCHESTRATOR - ~50 lines]
├── initializeSession(tag)
├── connectToCard(tag) → IsoDep
├── executePhase1_PpseSelection(isoDep)
├── executePhase2_AidSelection(isoDep)
├── executePhase3_Gpo(isoDep)
├── executePhase4_ReadRecords(isoDep)
├── executePhase5_GenerateAc(isoDep)
├── executePhase6_GetData(isoDep)
├── executePhase7_TransactionLogs(isoDep)
└── finalizeSession()
```

### Phase Function Signatures

```kotlin
// Phase 1: PPSE Selection
private suspend fun executePhase1_PpseSelection(isoDep: IsoDep): List<AidEntry>

// Phase 2: AID Selection
private suspend fun executePhase2_AidSelection(
    isoDep: IsoDep, 
    aidEntries: List<AidEntry>
): String

// Phase 3: GPO
private suspend fun executePhase3_Gpo(isoDep: IsoDep): String  // Returns AFL

// Phase 4: Read Records
private suspend fun executePhase4_ReadRecords(
    isoDep: IsoDep,
    afl: String
): Int  // Returns records read count

// Phase 5: Generate AC
private suspend fun executePhase5_GenerateAc(isoDep: IsoDep)

// Phase 6: GET DATA
private suspend fun executePhase6_GetData(isoDep: IsoDep): String  // Returns log format

// Phase 7: Transaction Logs
private suspend fun executePhase7_TransactionLogs(
    isoDep: IsoDep,
    logFormat: String
)

// Finalize
private suspend fun finalizeSession()
```

### Error Handling Strategy
- Each phase function handles its own errors
- Failed phases log errors but don't crash workflow
- Continue to next phase even if current phase fails
- Final session status reflects what succeeded

## 📝 Phase 3: GENERATION (Ready to Code)

### Implementation Order
1. Create new clean orchestrator function
2. Implement Phase 1 (PPSE Selection)
3. Implement Phase 2 (AID Selection)
4. Implement Phase 3 (GPO)
5. Implement Phase 4 (Read Records)
6. Implement Phase 5 (Generate AC)
7. Implement Phase 6 (GET DATA)
8. Implement Phase 7 (Transaction Logs)
9. Implement finalizeSession()
10. Delete old 1665-line function
11. Build and test

### Phase 1: PPSE Selection (~150 lines)

**Purpose:** Select Payment System Environment (2PAY contactless or 1PAY contact)

**Logic:**
1. Try 2PAY.SYS.DDF01 (contactless) if not in force contact mode
2. If 2PAY fails or force contact mode, try 1PAY.SYS.DDF01
3. Parse PPSE response for AID entries (tag 4F)
4. Extract labels (tag 50) and priorities (tag 87)
5. Return sorted list of AidEntry objects

**APDU:**
- 2PAY: `00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`
- 1PAY: `00 A4 04 00 0E 31 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00`

**Success:** SW=9000, return list of AIDs  
**Failure:** SW≠9000, return empty list

### Phase 2: AID Selection (~120 lines)

**Purpose:** Try all AIDs from PPSE, select first successful

**Logic:**
1. Loop through all AID entries from Phase 1
2. Build SELECT AID command for each
3. Send command, check status word
4. Parse FCI template on success
5. Store all successful AID responses
6. Return first successful AID for GPO

**APDU:** `00 A4 04 00 [Lc] [AID bytes] 00`

**Success:** SW=9000 for at least one AID  
**Failure:** All AIDs return error status

### Phase 3: GPO (~180 lines)

**Purpose:** Get Processing Options with dynamic PDOL

**Logic:**
1. Extract PDOL (tag 9F38) from AID response
2. Parse PDOL structure with EmvTlvParser.parseDol()
3. Build PDOL data with buildPdolData()
4. Send GPO command
5. Parse response for AIP (tag 82) and AFL (tag 94)
6. Analyze AIP for security info
7. Return AFL for record reading

**APDU:** `80 A8 00 00 [Lc] 83 [PDOL length] [PDOL data] 00`

**Success:** SW=9000, AIP and AFL extracted  
**Failure:** SW≠9000, continue with fallback AFL

### Phase 4: Read Records (~150 lines)

**Purpose:** Read all records specified in AFL

**Logic:**
1. Parse AFL with EmvTlvParser.parseAfl()
2. For each AFL entry (SFI + record range):
   - Build READ RECORD command
   - Send command
   - Parse response if SW=9000
   - Store tags in session
3. Track records read and PAN found
4. Return total records read

**APDU:** `00 B2 [record] [P2] 00` where P2 = (SFI << 3) | 0x04

**Success:** Records read, tags stored  
**Failure:** Individual records may fail, continue

### Phase 5: Generate AC (~150 lines)

**Purpose:** Generate Application Cryptogram (ARQC/TC)

**Logic:**
1. Extract CDOL1 (tag 8C) from previous responses
2. Parse CDOL with EmvTlvParser.parseDol()
3. Build CDOL data with buildCdolData()
4. Send GENERATE AC command (ARQC type 0x80)
5. Parse response for cryptogram (tag 9F26), CID (tag 9F27), ATC (tag 9F36)
6. Store cryptogram data in session

**APDU:** `80 AE 80 00 [Lc] [CDOL data] 00`

**Success:** SW=9000, cryptogram extracted  
**Failure:** SW≠9000, no cryptogram

### Phase 6: GET DATA (~100 lines)

**Purpose:** Query additional EMV tags

**Logic:**
1. Define tag list (9F17, 9F36, 9F13, 9F4F, etc.)
2. For each tag:
   - Build GET DATA command
   - Send command
   - Parse response if SW=9000
   - Store tag in session
3. Check for Log Format (9F4F)
4. Return log format value for Phase 7

**APDU:** `80 CA [P1] [P2] 00` where P1P2 = tag bytes

**Success:** Additional tags retrieved  
**Failure:** Individual tags may not exist

### Phase 7: Transaction Logs (~120 lines)

**Purpose:** Read transaction history if supported

**Logic:**
1. If log format from Phase 6 is empty, skip
2. Parse log format for SFI and record count
3. Read each transaction log record
4. Parse and store transaction data
5. Return logs read count

**APDU:** `00 B2 [record] [P2] 00` where P2 = (log SFI << 3) | 0x04

**Success:** Transaction logs read  
**Failure:** No logs or not supported

### Finalize Session (~100 lines)

**Purpose:** Create EmvCardData, save to database, update UI

**Logic:**
1. Extract all collected tags from session
2. Build EmvCardData object
3. Create VirtualCard for carousel
4. Save session to Room database
5. Update UI with final status
6. Set scanState to COMPLETE

## ✅ Phase 4: VALIDATION CHECKLIST

Before implementation:
- [x] All dependencies mapped
- [x] All signatures documented
- [x] All state variables identified
- [x] Data flow understood
- [x] Error handling planned
- [x] UI update pattern consistent

During implementation:
- [ ] Copy exact names from mapping (no guessing)
- [ ] Use exact types from documentation
- [ ] Apply explicit type conversions
- [ ] Update UI state with withContext(Dispatchers.Main)
- [ ] Log all APDUs with addApduLogEntry()
- [ ] Store all parsed data in currentSessionData

After implementation:
- [ ] Self-review against mapping
- [ ] Check all property names match
- [ ] Verify all method signatures match
- [ ] Confirm all type conversions applied
- [ ] BUILD SUCCESSFUL on first try

## 🎯 Expected Results

### Code Metrics
- **Before:** 1 function, 1665 lines, complexity 9/10
- **After:** 9 functions, ~1200 lines total, complexity 3/10

### Main Orchestrator (~50 lines)
```kotlin
private suspend fun executeProxmark3EmvWorkflow(tag: android.nfc.Tag) {
    initializeSession(tag)
    val isoDep = connectToCard(tag) ?: return
    
    try {
        val aidEntries = executePhase1_PpseSelection(isoDep)
        if (aidEntries.isEmpty()) return
        
        val selectedAid = executePhase2_AidSelection(isoDep, aidEntries)
        if (selectedAid.isEmpty()) return
        
        val afl = executePhase3_Gpo(isoDep)
        executePhase4_ReadRecords(isoDep, afl)
        executePhase5_GenerateAc(isoDep)
        
        val logFormat = executePhase6_GetData(isoDep)
        executePhase7_TransactionLogs(isoDep, logFormat)
        
        finalizeSession()
    } catch (e: Exception) {
        Timber.e(e, "EMV workflow error")
        currentSessionData?.scanStatus = "ERROR"
        currentSessionData?.errorMessage = e.message
    } finally {
        isoDep.close()
    }
}
```

### Benefits
- ✅ Readable - clear workflow in orchestrator
- ✅ Maintainable - each phase is independent
- ✅ Testable - can test each phase separately
- ✅ Debuggable - easy to log each phase
- ✅ Extensible - easy to add new phases
- ✅ Clean - no duplication, single responsibility

## 🚀 Ready to Implement

Following 7-Phase Universal Laws:
1. ✅ MAPPING - Complete
2. ✅ ARCHITECTURE - Designed
3. ⏳ GENERATION - Next step
4. ⏳ VALIDATION - After generation
5. ⏳ INTEGRATION - Wire everything
6. ⏳ OPTIMIZATION - Polish
7. ⏳ VERIFICATION - Build & test

**Next Action:** Start Phase 3 (GENERATION) - implement clean workflow from SELECT PPSE
