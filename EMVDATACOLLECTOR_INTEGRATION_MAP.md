# EMV Data Collector Integration Map
**Created:** October 10, 2025  
**Updated:** October 11, 2025 (Added dual database integration phase)  
**Purpose:** Complete mapping before implementing EmvDataCollector pattern  
**Scope:** Comprehensive analysis of CardReadingViewModel, existing database, and integration strategy

---

## 🎯 INTEGRATION APPROACH: PARALLEL DATABASE SYSTEM

### Why Two Databases?

**CardDataStore (EXISTING - File JSON):**
- Location: `/data/data/com.nfsp00f33r.app/files/card_profiles/{profileId}.json`
- Type: Encrypted JSON files (AES-256-GCM)
- Purpose: Card profile summaries (~30-40 EMV tags)
- Used by: 5+ screens (Database, Dashboard, Analysis, CardReading, Debug)
- Status: ✅ Production-grade, MUST NOT MODIFY

**EmvSessionDatabase (NEW - Room SQLite):**
- Location: `/data/data/com.nfsp00f33r.app/databases/emv_sessions.db`
- Type: Room SQLite database
- Purpose: Complete EMV sessions (200+ tags + full APDU log)
- Used by: Future forensics/analysis screens
- Status: ⚠️ To be created

### Integration Strategy
**PARALLEL SAVE** - Both databases write after each scan:
```kotlin
// After NFC scan completes
viewModelScope.launch(Dispatchers.IO) {
    // NEW: Save to EmvSessionDatabase (Room)
    emvCollector.saveToDatabase()
    
    // EXISTING: Save to CardDataStore (File JSON)
    saveCardProfile(extractedData)  // Line 3297 - NO CHANGES
}
```

**Zero Risk:** No modifications to existing CardDataStore code → Zero breaking changes

---

## 📊 CURRENT STATE ANALYSIS

### CardReadingViewModel.kt (3855 lines)
**Location:** `/android-app/src/main/java/com/nfsp00f33r/app/screens/cardreading/CardReadingViewModel.kt`

#### State Variables (15)
```kotlin
// UI State
var scanState: ScanState                              // Line 106
var selectedReader: ReaderType?                       // Line 109
var availableReaders: List<ReaderType>                // Line 112
var selectedTechnology: NfcTechnology                 // Line 115
var isAutoSelectEnabled: Boolean                      // Line 118
var forceContactMode: Boolean                         // Line 121

// Progress & Status
var currentPhase: String                              // Line 124
var progress: Float                                   // Line 127
var statusMessage: String                             // Line 130
var readerStatus: String                              // Line 145
var hardwareCapabilities: Set<String>                 // Line 148

// Data (CURRENT - NEEDS ENHANCEMENT)
var apduLog: List<ApduLogEntry>                       // Line 133 ✅ Good
var scannedCards: List<VirtualCard>                   // Line 136
var currentEmvData: EmvCardData?                      // Line 139
var parsedEmvFields: Map<String, String>              // Line 142 ⚠️ FLAT - needs structure

// ROCA
var rocaVulnerabilityStatus: String                   // Line 152
var isRocaVulnerable: Boolean                         // Line 155
```

#### Data Classes (4)
```kotlin
1. AidEntry(aid: String, label: String, priority: Int)                  // Line 51
2. SecurityInfo(hasSDA, hasDDA, hasCDA, isWeak, summary)                // Line 61
3. GenerateAcResult(arqc, cid, atc, iad, statusWord)                    // Line ~1772
4. InternalAuthResult(signedData, challenge, ddaSupported, statusWord)  // Line ~1846
```

#### Enums (3)
```kotlin
1. ReaderType { ANDROID_NFC, PN532_BLUETOOTH, PN532_USB, MOCK_READER }  // Line 78
2. NfcTechnology { EMV_CONTACTLESS, MIFARE_CLASSIC, NTAG, ... }         // Line 86
3. ScanState { IDLE, READER_CONNECTING, SCANNING, ... }                 // Line 95
```

#### Key Functions (50+)
```kotlin
// Core Workflow
- executeProxmark3EmvWorkflow(tag)                    // Line 241 - MAIN ENTRY POINT
- processNfcTag(tag)                                  // Line 206

// PPSE Phase
- (PPSE selection embedded in executeProxmark3EmvWorkflow)

// AID Phase
- extractAllAidsFromPpse(hexResponse)                 // Line 1362
- extractAidsFromPpseResponse(hexResponse)            // Line 1416

// GPO Phase
- buildPdolData(dolEntries)                           // Line 1508
- (GPO embedded in executeProxmark3EmvWorkflow)

// Record Reading Phase
- parseAflForRecords(afl)                             // Line 1445
- (Record reading loop embedded)

// GENERATE AC Phase
- buildCdolData(dolEntries)                           // Line 1622
- parseCdol(cdolHex)                                  // Line 1727
- buildGenerateAcApdu(acType, cdolData)               // Line 1751
- parseGenerateAcResponse(responseHex)                // Line 1772

// INTERNAL_AUTH Phase
- buildInternalAuthApdu(ddolData)                     // Line 1825
- parseInternalAuthResponse(responseHex)              // Line 1846
- supportsDda(aipHex)                                 // Line 1888

// GET DATA Phase
- buildGetDataApdu(tag)                               // Line 1491
- (GET DATA loop embedded)

// ROCA Testing
- testRocaVulnerability(issuerPublicKeyHex)           // Line 1915

// Utilities
- addApduLogEntry(command, response, statusWord, description, executionTime)  // Line 1202
- exportEmvDataToJson(cardData, apduLog)              // Line 1228
- interpretStatusWord(statusWord)                     // Line 1313
- extractFciFromAidResponse(hexResponse)              // Line 1423
```

---

## 📚 EXISTING DATABASE STRUCTURES

### CardProfile (Current - In CardDataStore)
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/models/CardProfile.kt`

```kotlin
data class CardProfile(
    val profileId: String,
    val cardType: CardType,
    val issuer: String,
    val staticData: StaticCardData,        // PAN, expiry, name, track data
    val dynamicData: DynamicCardData,      // ATC, PIN try counter, transaction log
    val configuration: CardConfiguration,  // AID, AIP, AFL, CVM, PDOL, CDOL
    val cryptographicData: CryptographicData,  // Issuer/ICC public keys, auth method, vulnerabilities
    val transactionHistory: List<TransactionRecord>,
    val metadata: ProfileMetadata,
    val tags: Set<String>,
    val version: Int,
    val createdAt: Instant,
    val lastModified: Instant
)
```

**Nested Structures:**
- `StaticCardData` - PAN, expiry, name, track1/2, issuer country, currency, language, labels
- `DynamicCardData` - ATC, last online ATC, PIN try counter, log format, transaction log
- `CardConfiguration` - AID, AIP, AFL, CVM list, IAC/TAC codes, PDOL, CDOL1/2, version, usage control, dates
- `CryptographicData` - Issuer/ICC public keys (cert, exponent, remainder), CA index, SDA tag list, auth method, vulnerabilities
- `TransactionRecord` - Full transaction with amount, currency, merchant, ARQC, TC, AAC, CID, ATC, UN, TVR, TSI, CVM results, IAD, APDU log

**Enums:**
- `CardType` - VISA, MASTERCARD, AMERICAN_EXPRESS, DISCOVER, JCB, UNIONPAY, MAESTRO, UNKNOWN
- `OfflineAuthMethod` - NONE, SDA, DDA, CDA, FDDA
- `VulnerabilityType` - ROCA, WEAK_KEYS, PREDICTABLE_UN, LOW_ENTROPY_ATC, DOWNGRADE_POSSIBLE, CVM_BYPASS, RELAY_VULNERABLE, OTHER
- `Severity` - LOW, MEDIUM, HIGH, CRITICAL
- `TransactionType` - PURCHASE, CASH_ADVANCE, REFUND, BALANCE_INQUIRY, CASH_DEPOSIT, PAYMENT, TRANSFER, OTHER
- `CaptureMethod` - NFC_READER, CONTACT_READER, MITM_PROXY, TERMINAL_EMULATION, CARD_EMULATION, UNKNOWN

### EmvCardData (Current - In ViewModel)
**Location:** `/android-app/src/main/java/com/nfsp00f33r/app/data/EmvCardData.kt`

```kotlin
data class EmvCardData(
    val id: String,
    var cardUid: String?,
    var pan: String?,
    var track2Data: String?,
    var expiryDate: String?,
    var cardholderName: String?,
    var applicationIdentifier: String?,
    var applicationLabel: String,
    var applicationInterchangeProfile: String?,
    var applicationFileLocator: String?,
    var processingOptionsDataObjectList: String?,
    var cardholderVerificationMethodList: String?,
    var issuerApplicationData: String?,
    var applicationCryptogram: String?,
    val cryptogramInformationData: String?,
    val applicationTransactionCounter: String?,
    val unpredictableNumber: String?,
    // ... 20+ more EMV fields
    val emvTags: Map<String, String>,  // ⚠️ FLAT MAP - all tags here
    val apduLog: List<ApduLogEntry>,   // ✅ Good structure
    // ... metadata
)
```

---

## 🔍 PROXMARK3 DATA STRUCTURE REFERENCE

From previous research of rfidresearchgroup/proxmark3:

### 10 Main Categories (200+ EMV tags)
1. **PPSE** - Payment System Environment (tag 6F, A5, 84, 50, 87, 88, 9F11, 9F12)
2. **Application Selection** - AIDs (tags 4F, 50, 87, 9F11, 9F12)
3. **GPO (Get Processing Options)** - AIP, AFL, PDOL (tags 82, 94, 9F38, 77, 80)
4. **Records** - All SFI/record data (tags 70, 57, 5A, 5F20, 5F24, 5F28, 5F30, 8C, 8D, 8E, 8F, 9F07, 9F08, etc.)
5. **GET_DATA** - Issuer scripts, logs, PIN counter (tags 9F13, 9F17, 9F36, 9F4F, DF60-DF69)
6. **GENERATE_AC** - ARQC/TC/AAC generation (tags 9F26, 9F27, 9F10, 9F36)
7. **INTERNAL_AUTH** - DDA/CDA authentication (tags 9F4B, 9F4A)
8. **Transaction Logs** - Historical transactions
9. **Public Keys** - Issuer/ICC certificates (tags 90, 92, 9F32, 9F46, 9F47, 9F48)
10. **ATR** - Answer To Reset data

---

## 🎯 PROBLEM IDENTIFICATION

### Current Issues
1. **Scattered Parsing** - 10+ separate extraction functions (`extractPdol...`, `extractAfl...`, `extractTrack2...`)
2. **Flat Storage** - `parsedEmvFields: Map<String, String>` loses structure and phase context
3. **No Database Persistence** - All data in memory, lost after scan
4. **Manual Extraction** - Each tag extracted individually, not comprehensive
5. **Missing Proxmark3 Coverage** - Only ~30-40 tags extracted, missing 160+ others
6. **No Phase Context** - Can't tell which tags came from which phase (PPSE vs GPO vs Records)
7. **Threading Issues** - UI state updates without proper Main dispatcher wrapping

### What We Need
1. **Single parseResponse() Entry Point** - One function called at each phase
2. **Structured Storage** - Like CardProfile, organized by category
3. **Comprehensive Capture** - ALL 200+ tags automatically extracted
4. **Phase-Aware** - Know which tags came from PPSE vs GPO vs Records
5. **Database Persistence** - Save everything for history/analysis
6. **Zero Slowdown** - In-memory buffer during scan, async DB write after
7. **Clean Code** - Remove all manual extraction functions

---

## 🏗️ PROPOSED ARCHITECTURE

### New Components to Create

#### 1. EmvDataCollector.kt
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvDataCollector.kt`

```kotlin
class EmvDataCollector(private val context: Context) {
    private val sessionData = EmvSessionData()  // In-memory buffer
    private val parser = EmvTlvParser
    private val tagDictionary = EmvTagDictionary
    
    // SINGLE ENTRY POINT - Called at each workflow phase
    fun parseResponse(
        command: ByteArray,
        response: ByteArray,
        statusWord: String,
        phase: EmvPhase
    ) {
        // 1. Add to APDU log (memory)
        // 2. Auto-extract ALL EMV tags via EmvTlvParser
        // 3. Smart categorization by phase
        // 4. Store in sessionData (structured)
        // NO DATABASE HIT - just memory!
    }
    
    // Async DB write AFTER scan complete
    suspend fun saveToDatabase() {
        withContext(Dispatchers.IO) {
            // Save to Room database
            // Convert sessionData -> CardProfile
            // Insert EmvSessionEntity, EmvTagEntity, EmvApduLogEntity
        }
    }
    
    fun getSessionData(): EmvSessionData = sessionData
    fun reset() { /* Clear for next scan */ }
}

enum class EmvPhase {
    PPSE, SELECT_AID, GPO, READ_RECORD, GENERATE_AC, INTERNAL_AUTH, GET_DATA
}
```

#### 2. EmvSessionData.kt
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvSessionData.kt`

```kotlin
// Structured data matching Proxmark3's 10 categories
data class EmvSessionData(
    val timestamp: Instant = Instant.now(),
    var cardUid: String? = null,
    
    // 1. PPSE Data
    var ppse: PpseData = PpseData(),
    
    // 2. Application Selection
    var aids: List<AidData> = emptyList(),
    
    // 3. GPO Data
    var gpo: GpoData = GpoData(),
    
    // 4. Records
    var records: List<RecordData> = emptyList(),
    
    // 5. GET_DATA
    var getData: Map<String, String> = emptyMap(),
    
    // 6. Cryptogram (GENERATE_AC)
    var cryptogram: CryptogramData = CryptogramData(),
    
    // 7. Authentication (INTERNAL_AUTH)
    var authentication: AuthenticationData = AuthenticationData(),
    
    // 8. Transaction Logs
    var transactionLogs: List<String> = emptyList(),
    
    // 9. Public Keys
    var publicKeys: PublicKeyData = PublicKeyData(),
    
    // 10. APDU Log
    var apduLog: List<ApduEntry> = emptyList(),
    
    // ALL TAGS (200+) - complete flat map for comprehensive capture
    var allTags: Map<String, String> = emptyMap(),
    
    // ROCA Result
    var rocaResult: RocaTestResult? = null
)

data class PpseData(
    var aid: String? = null,
    var fciTemplate: String? = null,
    var tags: Map<String, String> = emptyMap()
)

data class AidData(
    val aid: String,
    val label: String,
    val priority: Int,
    val tags: Map<String, String> = emptyMap()
)

data class GpoData(
    var aip: String? = null,
    var afl: String? = null,
    var pdol: String? = null,
    var tags: Map<String, String> = emptyMap()
)

data class RecordData(
    val sfi: Int,
    val recordNum: Int,
    val tags: Map<String, String> = emptyMap()
)

data class CryptogramData(
    var arqc: String? = null,
    var cid: String? = null,
    var atc: String? = null,
    var iad: String? = null,
    var cdol1: String? = null,
    var cdol2: String? = null,
    var tags: Map<String, String> = emptyMap()
)

data class AuthenticationData(
    var ddaSupported: Boolean = false,
    var signedData: String? = null,
    var challenge: String? = null,
    var tags: Map<String, String> = emptyMap()
)

data class PublicKeyData(
    var issuerCertificate: String? = null,
    var issuerExponent: String? = null,
    var issuerRemainder: String? = null,
    var iccCertificate: String? = null,
    var iccExponent: String? = null,
    var iccRemainder: String? = null,
    var caPublicKeyIndex: String? = null,
    var tags: Map<String, String> = emptyMap()
)

data class ApduEntry(
    val command: ByteArray,
    val response: ByteArray,
    val statusWord: String,
    val phase: EmvPhase,
    val executionTime: Long,
    val description: String
)

data class RocaTestResult(
    val isVulnerable: Boolean,
    val status: String,
    val details: String,
    val certificatesAnalyzed: Int
)
```

#### 3. Database Entities (NEW)
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/`

```kotlin
// EmvSessionEntity.kt
@Entity(tableName = "emv_sessions")
data class EmvSessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val cardUid: String?,
    
    // Summary data
    val ppseAid: String?,
    val selectedAid: String?,
    val applicationLabel: String?,
    val pan: String?,
    val expiryDate: String?,
    
    // Key EMV fields
    val aip: String?,
    val afl: String?,
    val pdol: String?,
    val cdol1: String?,
    val cdol2: String?,
    
    // Cryptogram
    val arqc: String?,
    val cid: String?,
    val atc: String?,
    val iad: String?,
    
    // ROCA
    val rocaVulnerable: Boolean,
    val rocaStatus: String?,
    
    // Metadata
    val totalAids: Int,
    val totalRecords: Int,
    val totalTags: Int,
    val scanDurationMs: Long
)

// EmvTagEntity.kt - Normalized tag storage (200+ rows per session)
@Entity(
    tableName = "emv_tags",
    foreignKeys = [ForeignKey(
        entity = EmvSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("tag"), Index("phase")]
)
data class EmvTagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val tag: String,          // e.g., "5A", "9F26"
    val tagName: String,      // e.g., "PAN", "ARQC"
    val value: String,        // Hex value
    val phase: String,        // PPSE, SELECT_AID, GPO, READ_RECORD, etc.
    val category: String      // PPSE, Application, GPO, Records, Cryptogram, etc.
)

// EmvApduLogEntity.kt - Full APDU history
@Entity(
    tableName = "emv_apdu_log",
    foreignKeys = [ForeignKey(
        entity = EmvSessionEntity::class,
        parentColumns = ["sessionId"],
        childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId"), Index("phase")]
)
data class EmvApduLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val command: String,      // Hex
    val response: String,     // Hex
    val statusWord: String,   // "9000", "6A82", etc.
    val phase: String,
    val description: String,
    val executionTime: Long,
    val sequenceNum: Int
)
```

#### 4. Room DAO Interfaces
```kotlin
@Dao
interface EmvSessionDao {
    @Insert
    suspend fun insertSession(session: EmvSessionEntity): Long
    
    @Query("SELECT * FROM emv_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<EmvSessionEntity>
    
    @Query("SELECT * FROM emv_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): EmvSessionEntity?
    
    @Query("DELETE FROM emv_sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface EmvTagDao {
    @Insert
    suspend fun insertTags(tags: List<EmvTagEntity>)
    
    @Query("SELECT * FROM emv_tags WHERE sessionId = :sessionId")
    suspend fun getTagsForSession(sessionId: String): List<EmvTagEntity>
    
    @Query("SELECT * FROM emv_tags WHERE sessionId = :sessionId AND phase = :phase")
    suspend fun getTagsForPhase(sessionId: String, phase: String): List<EmvTagEntity>
}

@Dao
interface EmvApduLogDao {
    @Insert
    suspend fun insertLog(logs: List<EmvApduLogEntity>)
    
    @Query("SELECT * FROM emv_apdu_log WHERE sessionId = :sessionId ORDER BY sequenceNum")
    suspend fun getLogForSession(sessionId: String): List<EmvApduLogEntity>
}
```

---

## 🔄 INTEGRATION STRATEGY

### Phase 1: Create EmvSessionDatabase (Room) - NEW DATABASE

**⚠️ CRITICAL:** Create separate Room database to avoid risk to existing CardDataStore

#### Step 1.1: Create Database Entities
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/`

**Files to create:**
1. `EmvSessionEntity.kt` - Session summary with key EMV fields
2. `EmvTagEntity.kt` - Normalized tag storage (200+ rows per session)
3. `EmvApduLogEntity.kt` - Complete APDU log with timing

**Key Design:**
- EmvSessionEntity has foreign key link to CardDataStore profileId (optional)
- Cascade delete: Delete session → Delete all tags + APDU logs
- Indices on: sessionId, tag, phase, cardUid, rocaVulnerable

#### Step 1.2: Create DAO Interfaces
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/`

**Files to create:**
1. `EmvSessionDao.kt` - Session CRUD + queries
2. `EmvTagDao.kt` - Tag queries (by session, by phase, by tag)
3. `EmvApduLogDao.kt` - APDU log queries

#### Step 1.3: Create EmvSessionDatabase
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvSessionDatabase.kt`

```kotlin
@Database(
    entities = [EmvSessionEntity::class, EmvTagEntity::class, EmvApduLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class EmvSessionDatabase : RoomDatabase() {
    abstract fun emvSessionDao(): EmvSessionDao
    abstract fun emvTagDao(): EmvTagDao
    abstract fun emvApduLogDao(): EmvApduLogDao
    
    companion object {
        private const val DATABASE_NAME = "emv_sessions.db"
        // Singleton instance pattern (see HealthDatabase for reference)
    }
}
```

**Build & Verify:** Ensure Room generates DAO implementations

---

### Phase 2: Create EmvDataCollector (In-Memory Buffer)

#### Step 2.1: Create EmvSessionData.kt
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvSessionData.kt`

- All 10 data classes (PpseData, AidData, GpoData, RecordData, etc.)
- No database dependencies yet
- Pure data structures for in-memory buffer

#### Step 2.2: Create EmvDataCollector.kt
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvDataCollector.kt`

- `parseResponse()` function - Called at each EMV phase
- Integrate with EmvTlvParser for automatic tag extraction
- Smart categorization logic by phase
- In-memory sessionData buffer
- `saveToDatabase()` function - Async DB write after scan
- Links to EmvSessionDatabase (created in Phase 1)

**Build & Verify:** Ensure EmvDataCollector compiles with database references

### Phase 3: Integrate into CardReadingViewModel (1 hour)

#### Step 3.1: Add Collector Instance
```kotlin
private val emvCollector = EmvDataCollector(context)
```

#### Step 3.2: Replace Manual Extraction with parseResponse()

**BEFORE (Current - Lines 280-360):**
```kotlin
// Phase 1: PPSE
val ppse1PayCommand = byteArrayOf(...)
val startTime = System.currentTimeMillis()
ppseResponse = isoDep.transceive(ppse1PayCommand)
val execTime = System.currentTimeMillis() - startTime

withContext(Dispatchers.Main) {
    apduLog = apduLog + ApduLogEntry(
        command = bytesToHex(ppse1PayCommand),
        response = ppseHex,
        statusWord = realStatusWord,
        description = "SELECT PPSE 1PAY.SYS.DDF01",
        executionTime = execTime
    )
}

// Then manual parsing...
val realAidEntries = extractAllAidsFromPpse(ppseHex)
extractedAidEntries = realAidEntries
```

**AFTER (New - Lines 280-295):**
```kotlin
// Phase 1: PPSE
val ppse1PayCommand = byteArrayOf(...)
val startTime = System.currentTimeMillis()
ppseResponse = isoDep.transceive(ppse1PayCommand)
val execTime = System.currentTimeMillis() - startTime

// Single call - automatic extraction + logging
emvCollector.parseResponse(
    command = ppse1PayCommand,
    response = ppseResponse,
    statusWord = realStatusWord,
    phase = EmvPhase.PPSE
)

// Access structured data
val ppseData = emvCollector.getSessionData().ppse
val aids = emvCollector.getSessionData().aids
```

#### Step 3.3: Replace All Phases
- PPSE phase (line ~280-360)
- SELECT_AID phase (line ~407-550)
- GPO phase (line ~560-680)
- READ_RECORD phase (line ~700-850)
- GENERATE_AC phase (line ~900-1050)
- INTERNAL_AUTH phase (line ~1060-1150)
- GET_DATA phase (line ~1160-1200)

#### Step 3.4: Add Database Save at End
```kotlin
// At end of executeProxmark3EmvWorkflow (after all phases)
try {
    emvCollector.saveToDatabase()
    Timber.d("EMV session saved to database")
} catch (e: Exception) {
    Timber.e(e, "Failed to save EMV session to database")
}
```

#### Step 3.5: Update Dual Database Save

**At end of executeProxmark3EmvWorkflow (after line 1150):**

```kotlin
// DUAL DATABASE SAVE (both systems)
try {
    // 1. Save to EmvSessionDatabase (NEW - Room)
    viewModelScope.launch(Dispatchers.IO) {
        emvCollector.saveToDatabase()
        Timber.d("EMV session saved to Room database")
    }
    
    // 2. Save to CardDataStore (EXISTING - File JSON)
    saveCardProfile(extractedData)  // Existing function at line 3297
    
} catch (e: Exception) {
    Timber.e(e, "Failed to save scan data to databases")
}
```

**No changes to existing saveCardProfile() function - it continues saving to CardDataStore**

#### Step 3.6: Remove Old Functions (Cleanup)
Delete manual extraction functions (Lines ~1362-1900):
- `extractAllAidsFromPpse()`
- `extractAidsFromPpseResponse()`
- `extractFciFromAidResponse()`
- `parseAflForRecords()`
- `parseCdol()`
- `parseGenerateAcResponse()`
- `parseInternalAuthResponse()`
- All other manual extraction functions

Keep utility functions:
- `buildPdolData()` - Still needed for APDU construction
- `buildCdolData()` - Still needed for APDU construction
- `buildGetDataApdu()` - Still needed for APDU construction
- `buildGenerateAcApdu()` - Still needed for APDU construction
- `buildInternalAuthApdu()` - Still needed for APDU construction
- `interpretStatusWord()` - Still useful
- `testRocaVulnerability()` - Still needed
- `addApduLogEntry()` - Can be REMOVED (collector handles it)

### Phase 4: Initial Testing (30 minutes)

1. **Build Test**
   - Verify compilation after Phases 1-3
   - Check no missing dependencies
   
2. **Real Card Test**
   - Scan actual EMV card
   - Verify all 200+ tags captured
   - Check EmvSessionDatabase entries created
   - Verify CardDataStore still saves correctly
   - Verify APDU log complete in both databases
   
3. **Performance Test**
   - Measure scan time (should be identical)
   - Verify no UI lag during scan
   - Confirm async DB writes work (both databases)
   
4. **Database Query Test**
   - Retrieve session by ID from EmvSessionDatabase
   - Query tags by phase from EmvSessionDatabase
   - Get profiles from CardDataStore (existing functionality)
   - Export session to JSON

---

## 📝 PHASE 5: FILE SEPARATION PLAN

### Problem
CardReadingViewModel.kt is 3855 lines - too large!

### Solution: Extract to Separate Files (After Core Integration Works)

#### Keep in CardReadingViewModel.kt (UI State Management)
- State variables (15 vars)
- UI-facing functions (startScan, stopScan, selectReader, etc.)
- Hardware initialization
- Card profile listener setup
- ~800-1000 lines

#### Extract to EmvWorkflowExecutor.kt (EMV Logic)
**NEW FILE:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvWorkflowExecutor.kt`

```kotlin
class EmvWorkflowExecutor(
    private val context: Context,
    private val collector: EmvDataCollector
) {
    suspend fun executeCompleteEmvWorkflow(
        tag: android.nfc.Tag,
        onPhaseUpdate: (phase: String, progress: Float) -> Unit,
        onStatusUpdate: (message: String) -> Unit,
        onApduLog: (entry: ApduLogEntry) -> Unit
    ): Result<EmvSessionData> {
        // All EMV workflow logic here
        // PPSE, AID, GPO, Records, GENERATE_AC, INTERNAL_AUTH, GET_DATA
        // ~1500 lines
    }
    
    private suspend fun executePpsePhase(isoDep: IsoDep): PpseResult
    private suspend fun executeAidSelectionPhase(isoDep: IsoDep, aids: List<AidData>): AidResult
    private suspend fun executeGpoPhase(isoDep: IsoDep, aid: String): GpoResult
    private suspend fun executeRecordReadingPhase(isoDep: IsoDep, afl: String): List<RecordData>
    private suspend fun executeGenerateAcPhase(isoDep: IsoDep, cdol1: String): CryptogramData
    private suspend fun executeInternalAuthPhase(isoDep: IsoDep): AuthenticationData
    private suspend fun executeGetDataPhase(isoDep: IsoDep): Map<String, String>
}
```

#### Extract to EmvApduBuilder.kt (APDU Construction)
**NEW FILE:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvApduBuilder.kt`

```kotlin
object EmvApduBuilder {
    fun buildPdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray
    fun buildCdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray
    fun buildGenerateAcApdu(acType: Byte, cdolData: ByteArray): ByteArray
    fun buildInternalAuthApdu(ddolData: ByteArray): ByteArray
    fun buildGetDataApdu(tag: Int): ByteArray
    fun interpretStatusWord(statusWord: String): String
    
    // ~400 lines
}
```

#### Keep in EmvTlvParser.kt (Already Exists)
- All TLV parsing logic
- ~900 lines
- Already good

#### Result After Separation
```
CardReadingViewModel.kt         ~800 lines   (UI state)
EmvWorkflowExecutor.kt         ~1500 lines   (NEW - EMV logic)
EmvApduBuilder.kt               ~400 lines   (NEW - APDU construction)
EmvDataCollector.kt             ~300 lines   (NEW - Data collection)
EmvSessionData.kt               ~200 lines   (NEW - Data structures)
EmvTlvParser.kt                 ~900 lines   (EXISTS - TLV parsing)
---------------------------------------------------
Total: ~4100 lines across 6 well-organized files
```

---

## ✅ INTEGRATION CHECKLIST

### Pre-Integration (Mapping Phase)
- [x] Map all CardReadingViewModel state variables
- [x] Map all CardReadingViewModel functions
- [x] Map existing database structures (CardProfile, CardDataStore)
- [x] Map EmvCardData structure
- [x] Map EmvTlvParser capabilities
- [x] Identify Proxmark3 data structure (10 categories, 200+ tags)
- [x] Identify integration points (all 7 EMV phases)
- [x] Plan file separation strategy
- [x] Map current storage flow (CardDataStore at line 3309, saveCardProfile at line 3297)
- [x] Design parallel database system (EmvSessionDatabase + CardDataStore)

### Phase 1: Create EmvSessionDatabase (Room)
- [ ] Create EmvSessionEntity.kt with session summary and metadata
- [ ] Create EmvTagEntity.kt with normalized tag storage (200+ per session)
- [ ] Create EmvApduLogEntity.kt with complete APDU log
- [ ] Create EmvSessionDao.kt with CRUD and query methods
- [ ] Create EmvTagDao.kt with tag queries (by session, phase, tag)
- [ ] Create EmvApduLogDao.kt with APDU log queries
- [ ] Create EmvSessionDatabase.kt with Room @Database annotation
- [ ] Build and verify Room generates DAO implementations
- [ ] Test database initialization (singleton pattern)

### Phase 2: Create EmvDataCollector (In-Memory Buffer)
- [ ] Create EmvSessionData.kt with all 10 data classes (PpseData, AidData, etc.)
- [ ] Create EmvDataCollector.kt with parseResponse() method
- [ ] Implement automatic tag extraction via EmvTlvParser integration
- [ ] Implement smart categorization by phase (PPSE, AID, GPO, etc.)
- [ ] Implement saveToDatabase() method with EmvSessionDatabase integration
- [ ] Add bytesToHex() helper method
- [ ] Build and verify EmvDataCollector compiles with database references

### Phase 3: Integration into CardReadingViewModel
- [ ] Add emvCollector instance to CardReadingViewModel
- [ ] Replace PPSE phase manual extraction with collector.parseResponse()
- [ ] Replace SELECT_AID phase manual extraction with collector.parseResponse()
- [ ] Replace GPO phase manual extraction with collector.parseResponse()
- [ ] Replace READ_RECORD phase manual extraction with collector.parseResponse()
- [ ] Replace GENERATE_AC phase manual extraction with collector.parseResponse()
- [ ] Replace INTERNAL_AUTH phase manual extraction with collector.parseResponse()
- [ ] Replace GET_DATA phase manual extraction with collector.parseResponse()
- [ ] Add collector.saveToDatabase() at end of workflow
- [ ] Remove old extraction functions (extractAllAidsFromPpse, etc.)
- [ ] Remove addApduLogEntry function (collector handles it)
- [ ] Update parsedEmvFields to use collector.getSessionData()
- [ ] Build and verify compilation

### Phase 4: Initial Integration Testing (30 minutes)
- [ ] Test real NFC card scan after Phases 1-3
- [ ] Verify EmvSessionDatabase entries created
- [ ] Verify CardDataStore still saves correctly
- [ ] Verify both databases populated
- [ ] Verify all 200+ tags captured in EmvSessionDatabase
- [ ] Build and verify no compilation errors

### Phase 5: File Separation (OPTIONAL - Do After Core Works)
- [ ] Create EmvWorkflowExecutor.kt (~1500 lines)
- [ ] Move executeProxmark3EmvWorkflow to EmvWorkflowExecutor
- [ ] Extract phase execution functions to EmvWorkflowExecutor
- [ ] Create EmvApduBuilder.kt (~400 lines)
- [ ] Move APDU construction functions to EmvApduBuilder
- [ ] Create EmvSecurityChecker.kt (~300 lines)
- [ ] Update CardReadingViewModel to use EmvWorkflowExecutor
- [ ] Remove moved code from CardReadingViewModel
- [ ] Build and verify compilation

### Phase 6: Final Testing
- [ ] Test real NFC card scan
- [ ] Verify all 200+ tags captured in allTags map
- [ ] Verify structured data populated (ppse, aids, gpo, records, etc.)
- [ ] Verify EmvSessionDatabase entries created (EmvSessionEntity, EmvTagEntity, EmvApduLogEntity)
- [ ] Verify CardDataStore still works (existing screens: Database, Dashboard, Analysis)
- [ ] Verify APDU log complete and in correct sequence in EmvSessionDatabase
- [ ] Test database queries:
  - [ ] Get session by ID from EmvSessionDatabase
  - [ ] Get tags by phase from EmvSessionDatabase
  - [ ] Get all profiles from CardDataStore (existing functionality)
  - [ ] Query vulnerable sessions (ROCA) from EmvSessionDatabase
- [ ] Performance test - verify zero slowdown during NFC scan
- [ ] Performance test - verify async DB writes complete successfully (both databases)
- [ ] Test with multiple cards
- [ ] Test error handling (card removed during scan, database errors, etc.)
- [ ] Verify database file sizes reasonable (emv_sessions.db vs card_profiles/)

### Phase 7: Cleanup & Documentation
- [ ] Remove all commented-out old code
- [ ] Add comprehensive KDoc comments to new classes
- [ ] Update CHANGELOG.md
- [ ] Update memory.instructions.md with integration details
- [ ] Commit changes with comprehensive message
- [ ] Create summary report

---

## 🚨 CRITICAL NOTES

### Threading Safety
**ALL UI state updates MUST use:**
```kotlin
withContext(Dispatchers.Main) {
    scanState = ScanState.SCANNING
    statusMessage = "Reading card..."
}
```

**EmvDataCollector operations are IO-bound:**
```kotlin
// parseResponse() - instant, memory-only (no withContext needed)
collector.parseResponse(...)

// saveToDatabase() - IO operation (already has withContext(Dispatchers.IO) inside)
collector.saveToDatabase()
```

### Zero Performance Impact Strategy
1. **During NFC Scan:** All operations in memory (instant)
   - collector.parseResponse() just adds to sessionData (microseconds)
   - No database writes
   - No file I/O

2. **After NFC Scan:** Async database write
   - collector.saveToDatabase() in background thread
   - User can continue using UI
   - No blocking

### No Junk Code Left Over
**Functions to DELETE after integration:**
- Lines 1362-1415: extractAllAidsFromPpse()
- Lines 1416-1422: extractAidsFromPpseResponse()
- Lines 1423-1444: extractFciFromAidResponse()
- Lines 1445-1490: parseAflForRecords()
- Lines 1727-1750: parseCdol()
- Lines 1772-1824: parseGenerateAcResponse()
- Lines 1846-1887: parseInternalAuthResponse()
- Lines 1202-1227: addApduLogEntry() (collector handles this)
- Any other manual "extract*FromAllResponses" functions

**Functions to MOVE (not delete):**
- Lines 1508-1621: buildPdolData() → EmvApduBuilder
- Lines 1622-1726: buildCdolData() → EmvApduBuilder
- Lines 1751-1771: buildGenerateAcApdu() → EmvApduBuilder
- Lines 1825-1845: buildInternalAuthApdu() → EmvApduBuilder
- Lines 1491-1507: buildGetDataApdu() → EmvApduBuilder
- Lines 1313-1361: interpretStatusWord() → EmvApduBuilder
- Lines 241-1200: executeProxmark3EmvWorkflow() → EmvWorkflowExecutor

**Functions to KEEP:**
- Lines 1915+: testRocaVulnerability() (still needed)
- Lines 177-205: startNfcMonitoring() (UI monitoring)
- Lines 206-240: processNfcTag() (UI event handler)
- All UI state management functions

---

## 📊 SUCCESS METRICS

### Code Quality
- [ ] CardReadingViewModel reduced from 3855 → ~800 lines
- [ ] All manual extraction functions removed
- [ ] Single parseResponse() entry point implemented
- [ ] Clean separation of concerns (UI, Workflow, APDU, Parsing)
- [ ] Zero duplicate code
- [ ] All threading wrapped properly

### Data Completeness
- [ ] ALL 200+ EMV tags captured automatically
- [ ] Structured data populated for all 10 categories
- [ ] Complete APDU log with phase context
- [ ] Database persistence working
- [ ] Export to JSON working

### Performance
- [ ] NFC scan time unchanged (< 3 seconds)
- [ ] Zero UI lag during scan
- [ ] Database write < 100ms (async, background)
- [ ] Memory usage reasonable (< 10MB per session)

### User Experience
- [ ] All existing UI functionality preserved
- [ ] No breaking changes to CardReadingScreen
- [ ] Database query UI can retrieve past sessions
- [ ] Export functionality works

---

## 🎯 FINAL GOAL

**Single Clean Call:**
```kotlin
// During EMV workflow at each phase
collector.parseResponse(command, response, statusWord, phase)

// After complete workflow
collector.saveToDatabase()

// Access structured data
val sessionData = collector.getSessionData()
val allTags = sessionData.allTags  // 200+ tags
val ppseData = sessionData.ppse
val aids = sessionData.aids
val gpoData = sessionData.gpo
val records = sessionData.records
val cryptogram = sessionData.cryptogram
val apduLog = sessionData.apduLog
```

**Zero Manual Extraction. Zero Slowdown. Complete Data Capture.**

---

## 📅 TIMELINE

**Total:** ~5-7 hours for complete, production-grade implementation with dual database system

### Core Implementation (REQUIRED)
- **Mapping:** 30 minutes ✅ COMPLETE (this document + DATABASE_INTEGRATION_STRATEGY.md)
- **Phase 1 (EmvSessionDatabase):** 1-2 hours - Create Room database entities/DAOs
- **Phase 2 (EmvDataCollector):** 1 hour - Create in-memory buffer with parseResponse()
- **Phase 3 (Integration):** 1-1.5 hours - Integrate into CardReadingViewModel
- **Phase 4 (Initial Testing):** 30 minutes - Verify dual database save works
- **Phase 6 (Final Testing):** 30-60 minutes - Comprehensive testing
- **Phase 7 (Cleanup):** 30 minutes - Documentation and commit

**Core Subtotal:** ~5-6 hours

### Optional Refactoring (DO LATER)
- **Phase 5 (File Separation):** 1-2 hours - Extract EmvWorkflowExecutor/EmvApduBuilder
- File separation should be done AFTER core functionality works

**Priority:** Get Phases 1-4 working first, then decide on Phase 5 file separation

---

**Ready to proceed with Phase 1: Create Core Classes?**
