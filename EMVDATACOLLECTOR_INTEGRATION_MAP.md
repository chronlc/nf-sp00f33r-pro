# EMV Data Collector Integration Map
**Created:** October 10, 2025  
**Updated:** October 11, 2025 (Added dual database integration phase)  
**Purpose:** Complete mapping before implementing EmvDataCollector pattern  
**Scope:** Comprehensive analysis of CardReadingViewModel, existing database, and integration strategy

---

## 🎯 INTEGRATION APPROACH: SINGLE DATABASE MIGRATION + PROXMARK3 EXPORT

### ⚠️ BREAKING CHANGE: Replace CardDataStore with EmvSessionDatabase

**OLD System (CardDataStore - WILL BE REMOVED):**
- Location: `/data/data/com.nfsp00f33r.app/files/card_profiles/{profileId}.json`
- Type: Encrypted JSON files (AES-256-GCM)
- Purpose: Card profile summaries (~30-40 EMV tags)
- Used by: 5+ screens (Database, Dashboard, Analysis, CardReading, Debug)
- Problem: Incomplete data (missing 160+ EMV tags), file-based (no queries)

**NEW System (EmvSessionDatabase - SINGLE SOURCE OF TRUTH):**
- Location: `/data/data/com.nfsp00f33r.app/databases/emv_sessions.db`
- Type: Room SQLite database
- Purpose: Complete EMV sessions (200+ tags + full APDU log + metadata)
- Used by: ALL screens that need card data
- Benefits: Complete data, queryable, relational, better performance

### Migration Strategy

**Phase 1: Create EmvSessionDatabase** (Room with complete schema)

**Phase 2: Create EmvDataCollector** (single-pass parser)

**Phase 3: Update CardReadingViewModel** (save to EmvSessionDatabase only)

**Phase 4: Migrate ALL Consumer Screens:**
1. **DatabaseViewModel** - Replace `cardDataStore.getAllProfiles()` with `emvSessionDao.getAllSessions()`
2. **DashboardViewModel** - Replace `cardDataStore.getAllProfiles()` with `emvSessionDao.getAllSessions()`
3. **AnalysisViewModel** - Replace `cardDataStore.getAllProfiles()` with `emvSessionDao.getAllSessions()`
4. **DebugCommandProcessor** - Replace `cardDataStore` references with `emvSessionDatabase`

**Phase 5: Add Proxmark3 JSON Export**
- `EmvSessionExporter.toProxmark3Json(sessionId)` - Export single session
- `EmvSessionExporter.exportAllToProxmark3()` - Export all sessions
- Compatible with Proxmark3 `emv` command format
- Includes all 200+ tags in standard Proxmark3 structure

**Phase 6: Remove CardDataStore** (cleanup)
- Delete CardDataStore.kt
- Delete CardDataStoreModule.kt
- Delete file-based storage code
- Update all imports

### Data Migration
```kotlin
// One-time migration function in EmvSessionDatabase
suspend fun migrateFromCardDataStore() {
    val oldProfiles = cardDataStore.getAllProfiles()
    oldProfiles.forEach { profile ->
        // Convert CardProfile → EmvSessionEntity
        // Best-effort mapping (some data will be incomplete)
        val session = EmvSessionEntity.fromLegacyProfile(profile)
        emvSessionDao.insert(session)
    }
    // After migration, delete old files
    cardDataStore.deleteAllProfiles()
}
```

**Result:** Single database, complete data, Proxmark3-compatible export

---

## 🎯 CRITICAL DESIGN PRINCIPLE: SINGLE-PASS DATA PROCESSING

### Current Problem: Data Processed TWICE
```kotlin
// Pass 1: Add to APDU log
apduLog = apduLog + ApduLogEntry(command, response, statusWord, description, execTime)

// Pass 2: Manual parsing (separate function call)
val aids = extractAllAidsFromPpse(response)  // Parses same response AGAIN
```

### Solution: parseResponse() Does EVERYTHING Once
```kotlin
// Single call - data flows through system ONCE
emvCollector.parseResponse(
    phase = "PPSE",
    command = command,
    response = response,  // ← Data passed ONCE
    statusWord = statusWord,
    description = description,
    executionTime = execTime
)

// Inside parseResponse():
// 1. Add to APDU log          ← Done
// 2. Parse ALL TLV tags        ← Done (via EmvTlvParser)
// 3. Store in allTags          ← Done
// 4. Categorize by phase       ← Done
// 5. Extract common fields     ← Done
```

### Benefits
✅ **Zero Duplication** - Response data parsed once  
✅ **Zero Manual Extraction** - All automatic via EmvTlvParser  
✅ **Complete Capture** - 200+ tags vs current ~30-40  
✅ **Structured Storage** - Phase-categorized data structures  
✅ **Performance** - Single pass = faster  

### Implementation Rule
**Every APDU response goes through parseResponse() EXACTLY ONCE**
- PPSE response → parseResponse(phase="PPSE", ...)
- AID selection → parseResponse(phase="SELECT_AID", ...)
- GPO response → parseResponse(phase="GPO", ...)
- Record → parseResponse(phase="READ_RECORD", ...)
- GENERATE AC → parseResponse(phase="GENERATE_AC", ...)
- GET DATA → parseResponse(phase="GET_DATA", ...)

**No exceptions. No manual parsing. One pass per response.**

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

#### 1. EmvDataCollector.kt - **THE SINGLE-PASS UNIFIED PARSER**
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvDataCollector.kt`

```kotlin
class EmvDataCollector(private val context: Context) {
    private val sessionData = EmvSessionData()  // In-memory buffer
    private val parser = EmvTlvParser
    private val tagDictionary = EmvTagDictionary
    
    /**
     * SINGLE ENTRY POINT - Called once per APDU response
     * 
     * This is the ONLY parsing function needed. It does EVERYTHING:
     * - Adds APDU to log
     * - Extracts ALL TLV tags (via EmvTlvParser)
     * - Categorizes by phase (PPSE/AID/GPO/RECORD/AC/etc.)
     * - Stores in structured sessionData
     * 
     * DATA FLOWS THROUGH ONCE - No duplicate processing
     */
    fun parseResponse(
        phase: String,              // "PPSE", "SELECT_AID", "GPO", etc.
        command: ByteArray,         // APDU command
        response: ByteArray,        // APDU response ← PARSED ONCE
        statusWord: String,         // Status word
        description: String,        // Human-readable
        executionTime: Long         // Milliseconds
    ) {
        // 1. Add to APDU log (memory-only, instant)
        sessionData.apduLog.add(ApduLogEntry(...))
        
        // 2. Extract ALL TLV tags in ONE PASS (via EmvTlvParser)
        val allTags = parser.parseAllTags(response)
        sessionData.allTags.putAll(allTags)  // Master map: 200+ tags
        
        // 3. Smart categorization by phase
        when (phase) {
            "PPSE" -> sessionData.ppse = PpseData.fromTags(allTags)
            "SELECT_AID" -> sessionData.aids.add(AidData.fromTags(allTags))
            "GPO" -> sessionData.gpo = GpoData.fromTags(allTags)
            "READ_RECORD" -> sessionData.records.add(RecordData.fromTags(allTags))
            "GENERATE_AC" -> sessionData.cryptogram = CryptogramData.fromTags(allTags)
            // ... etc
        }
        
        // 4. Extract common fields (PAN/expiry/name) wherever they appear
        allTags["5A"]?.let { sessionData.pan = it }
        allTags["5F24"]?.let { sessionData.expiryDate = it }
        allTags["5F20"]?.let { sessionData.cardholderName = it }
        
        // NO DATABASE WRITE - Pure memory operation (microseconds)
    }
    
    // Async DB write AFTER scan complete
    suspend fun saveToDatabase(): String {
        return withContext(Dispatchers.IO) {
            // Convert sessionData -> Room entities
            // Insert into EmvSessionDatabase
            // Return session ID
        }
    }
    
    fun getSessionData(): EmvSessionData = sessionData
    fun reset() { sessionData = EmvSessionData() }
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

**🔑 KEY DESIGN: SINGLE-PASS UNIFIED PARSER**

**parseResponse()** - Called once per APDU response, does EVERYTHING:
```kotlin
fun parseResponse(
    phase: String,              // "PPSE", "SELECT_AID", "GPO", "READ_RECORD", etc.
    command: ByteArray,         // APDU command sent
    response: ByteArray,        // APDU response received
    statusWord: String,         // "9000", "6A82", etc.
    description: String,        // Human-readable description
    executionTime: Long         // Milliseconds
) {
    // 1. Add to APDU log (replaces addApduLogEntry)
    sessionData.apduLog.add(ApduLogEntry(...))
    
    // 2. Parse ALL TLV tags in response (uses EmvTlvParser)
    val tags = EmvTlvParser.parseAllTags(response)
    sessionData.allTags.putAll(tags)  // Store in master map
    
    // 3. Categorize by phase (smart routing)
    when (phase) {
        "PPSE" -> {
            sessionData.ppse = PpseData(
                fciTemplate = tags["6F"],
                dfName = tags["84"],
                aids = EmvTlvParser.extractAids(tags),
                // ... all PPSE-specific fields
            )
        }
        "SELECT_AID" -> {
            sessionData.aids.add(AidData(
                aid = currentAid,
                pdol = tags["9F38"],
                aip = tags["82"],
                afl = tags["94"],
                // ... all AID-specific fields
            ))
        }
        "GPO" -> { /* Parse GPO response */ }
        "READ_RECORD" -> { /* Parse record */ }
        "GENERATE_AC" -> { /* Parse ARQC/TC */ }
        // ... etc
    }
    
    // 4. Extract common fields (pan, expiry, cardholder name) wherever they appear
    tags["5A"]?.let { sessionData.pan = it }
    tags["5F24"]?.let { sessionData.expiryDate = it }
    tags["5F20"]?.let { sessionData.cardholderName = it }
}
```

**saveToDatabase()** - Called ONCE after complete scan:
- Async write to EmvSessionDatabase (Room)
- Converts sessionData → entities (EmvSessionEntity, EmvTagEntity, EmvApduLogEntity)
- Returns session ID

**Result:** Zero manual extraction, zero duplicate parsing, one pass per response

**Build & Verify:** Ensure EmvDataCollector compiles with database references

### Phase 3: Update CardReadingViewModel - Save to EmvSessionDatabase ONLY (1 hour)

#### Step 3.1: Add Collector Instance
```kotlin
private val emvCollector = EmvDataCollector(context)
```

#### Step 3.2: Replace Manual Extraction with parseResponse()

**BEFORE (Current - Lines 280-360):**
```kotlin
**BEFORE (Current - Manual Parsing - Lines 280-360):**
```kotlin
// Phase 1: PPSE - TWO SEPARATE OPERATIONS
val ppse1PayCommand = byteArrayOf(...)
val startTime = System.currentTimeMillis()
ppseResponse = isoDep.transceive(ppse1PayCommand)
val execTime = System.currentTimeMillis() - startTime

// Operation 1: Add to APDU log manually
withContext(Dispatchers.Main) {
    apduLog = apduLog + ApduLogEntry(
        command = bytesToHex(ppse1PayCommand),
        response = ppseHex,
        statusWord = realStatusWord,
        description = "SELECT PPSE 1PAY.SYS.DDF01",
        executionTime = execTime
    )
}

// Operation 2: Manual parsing (separate function call)
val realAidEntries = extractAllAidsFromPpse(ppseHex)  // Line 1362

// Problem: Data passed through system TWICE (once for log, once for parsing)
```

**AFTER (Single-Pass Unified Parser):**
```kotlin
// Phase 1: PPSE - ONE OPERATION, EVERYTHING AUTOMATIC
val ppse1PayCommand = byteArrayOf(...)
val startTime = System.currentTimeMillis()
ppseResponse = isoDep.transceive(ppse1PayCommand)
val execTime = System.currentTimeMillis() - startTime

// Single function call - does EVERYTHING in one pass:
emvCollector.parseResponse(
    phase = "PPSE",
    command = ppse1PayCommand,
    response = ppseResponse,           // ← Data passed ONCE
    statusWord = realStatusWord,
    description = "SELECT PPSE 1PAY.SYS.DDF01",
    executionTime = execTime
)

// ✅ AUTOMATIC RESULTS (single pass):
// - APDU log entry created
// - ALL TLV tags extracted (via EmvTlvParser)
// - Tags stored in allTags map (200+)
// - PPSE-specific data categorized (PPSEData structure)
// - AIDs extracted and stored
// - Common fields extracted (if present)
// 
// ✅ ZERO manual parsing
// ✅ ZERO duplicate processing
// ✅ ONE pass through response data
```
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

#### Step 3.5: Replace Database Save - EmvSessionDatabase ONLY

**At end of executeProxmark3EmvWorkflow (after line 1150):**

```kotlin
// SINGLE DATABASE SAVE (EmvSessionDatabase only)
try {
    viewModelScope.launch(Dispatchers.IO) {
        val sessionId = emvCollector.saveToDatabase()
        withContext(Dispatchers.Main) {
            statusMessage = "Card saved to database (ID: $sessionId)"
        }
        Timber.d("EMV session saved to Room database: $sessionId")
    }
} catch (e: Exception) {
    Timber.e(e, "Failed to save scan data to database")
    withContext(Dispatchers.Main) {
        statusMessage = "Database save failed: ${e.message}"
    }
}
```

**DELETE old saveCardProfile() function (line 3297) - no longer needed**

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

### Phase 4: Migrate ALL Consumer Screens (2-3 hours)

#### **🚨 CRITICAL: All screens must be updated to use EmvSessionDatabase**

#### Step 4.1: Migrate DatabaseViewModel (screens/database/DatabaseViewModel.kt)
**Current usage:** `cardDataStore.getAllProfiles()` at line 77

**Changes needed:**
```kotlin
// BEFORE
private val cardDataStore = NfSp00fApplication.getCardDataStoreModule()
val storageProfiles = cardDataStore.getAllProfiles()

// AFTER
private val emvSessionDao by lazy { EmvSessionDatabase.getInstance(context).emvSessionDao() }
val sessions = emvSessionDao.getAllSessions()

// Update UI state to use EmvSessionEntity instead of CardProfile
var cardProfiles by mutableStateOf(listOf<EmvSessionEntity>())
```

**Functions to update:**
- `refreshData()` - Use `emvSessionDao.getAllSessions()`
- `deleteCard(cardId)` - Use `emvSessionDao.deleteSession(sessionId)`
- `exportCard(cardId)` - Use `EmvSessionExporter.toProxmark3Json(sessionId)`
- `exportAll()` - Use `EmvSessionExporter.exportAllToProxmark3()`
- `scanForRoca()` - Query `emvSessionDao.getVulnerableSessions()`

#### Step 4.2: Migrate DashboardViewModel (screens/dashboard/DashboardViewModel.kt)
**Current usage:** `cardDataStore.getAllProfiles()` at line 305

**Changes needed:**
```kotlin
// BEFORE
private val cardDataStore = NfSp00fApplication.getCardDataStoreModule()
val storageProfiles = cardDataStore.getAllProfiles()

// AFTER
private val emvSessionDao by lazy { EmvSessionDatabase.getInstance(context).emvSessionDao() }
val sessions = emvSessionDao.getAllSessions()

// Update statistics calculation
- Total cards: sessions.size
- Encrypted cards: sessions.count { it.hasEncryptedData }
- ROCA vulnerable: sessions.count { it.rocaVulnerable }
```

**Functions to update:**
- `refreshData()` - Use `emvSessionDao.getAllSessions()`
- Statistics calculations (totalCards, encryptedCards, uniqueCategories)
- Card filtering/search

#### Step 4.3: Migrate AnalysisViewModel (screens/analysis/AnalysisViewModel.kt)
**Current usage:** `cardDataStore.getAllProfiles()` at line 195

**Changes needed:**
```kotlin
// BEFORE
private val cardDataStore by lazy { NfSp00fApplication.getCardDataStoreModule() }
val profiles = cardDataStore.getAllProfiles()

// AFTER
private val emvSessionDao by lazy { EmvSessionDatabase.getInstance(context).emvSessionDao() }
val sessions = emvSessionDao.getAllSessions()

// For deep analysis, load full tag data
val tags = emvTagDao.getTagsBySession(sessionId)
val apduLog = emvApduLogDao.getLogBySession(sessionId)
```

**Functions to update:**
- `loadCards()` - Use `emvSessionDao.getAllSessions()`
- `analyzeCard(cardId)` - Load session + tags + APDU log
- Export to JSON (Proxmark3 format)

#### Step 4.4: Migrate DebugCommandProcessor (if exists)
**Replace:** All `cardDataStore` references with `emvSessionDatabase`

### Phase 5: Add Proxmark3 JSON Export (1 hour)

#### Step 5.1: Create EmvSessionExporter.kt
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvSessionExporter.kt`

```kotlin
object EmvSessionExporter {
    /**
     * Export single session to Proxmark3-compatible JSON
     * Format matches Proxmark3 'emv' command output
     */
    suspend fun toProxmark3Json(sessionId: String): String {
        val session = emvSessionDao.getSessionById(sessionId)
        val tags = emvTagDao.getTagsBySession(sessionId)
        val apduLog = emvApduLogDao.getLogBySession(sessionId)
        
        return JSONObject().apply {
            put("timestamp", session.scanTimestamp)
            put("card_uid", session.cardUid)
            put("pan", session.pan)
            put("expiry_date", session.expiryDate)
            put("cardholder_name", session.cardholderName)
            
            // PPSE data
            put("ppse", JSONObject().apply {
                tags.filter { it.phase == "PPSE" }.forEach { tag ->
                    put(tag.tag, tag.value)
                }
            })
            
            // AIDs (multiple if present)
            put("aids", JSONArray().apply {
                tags.filter { it.phase == "SELECT_AID" }
                    .groupBy { it.metadata["aid"] }
                    .forEach { (aid, aidTags) ->
                        add(JSONObject().apply {
                            put("aid", aid)
                            aidTags.forEach { put(it.tag, it.value) }
                        })
                    }
            })
            
            // GPO response
            put("gpo", JSONObject().apply {
                tags.filter { it.phase == "GPO" }.forEach { tag ->
                    put(tag.tag, tag.value)
                }
            })
            
            // Records
            put("records", JSONArray().apply {
                tags.filter { it.phase == "READ_RECORD" }
                    .groupBy { it.metadata["sfi"] to it.metadata["record"] }
                    .forEach { (key, recordTags) ->
                        add(JSONObject().apply {
                            put("sfi", key.first)
                            put("record", key.second)
                            put("tags", JSONObject().apply {
                                recordTags.forEach { put(it.tag, it.value) }
                            })
                        })
                    }
            })
            
            // Cryptogram
            put("cryptogram", JSONObject().apply {
                tags.filter { it.phase == "GENERATE_AC" }.forEach { tag ->
                    put(tag.tag, tag.value)
                }
            })
            
            // Complete tag list (200+)
            put("all_tags", JSONObject().apply {
                tags.forEach { put(it.tag, it.value) }
            })
            
            // APDU log
            put("apdu_log", JSONArray().apply {
                apduLog.forEach { apdu ->
                    add(JSONObject().apply {
                        put("command", apdu.command)
                        put("response", apdu.response)
                        put("status_word", apdu.statusWord)
                        put("description", apdu.description)
                        put("execution_time_ms", apdu.executionTime)
                    })
                }
            })
        }.toString(2)  // Pretty print with indent=2
    }
    
    /**
     * Export all sessions to Proxmark3 JSON array
     */
    suspend fun exportAllToProxmark3(): String {
        val sessions = emvSessionDao.getAllSessions()
        return JSONArray().apply {
            sessions.forEach { session ->
                add(JSONObject(toProxmark3Json(session.sessionId)))
            }
        }.toString(2)
    }
    
    /**
     * Save export to file
     */
    suspend fun saveToFile(sessionId: String, file: File) {
        val json = toProxmark3Json(sessionId)
        file.writeText(json)
    }
}
```

#### Step 5.2: Add Export Functions to ViewModels
**DatabaseViewModel:**
```kotlin
fun exportToProxmark3(sessionId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        val json = EmvSessionExporter.toProxmark3Json(sessionId)
        // Save to Downloads folder
        val file = File(context.getExternalFilesDir(null), "emv_${sessionId}.json")
        file.writeText(json)
        Timber.i("Exported session $sessionId to ${file.absolutePath}")
    }
}

fun exportAllToProxmark3() {
    viewModelScope.launch(Dispatchers.IO) {
        val json = EmvSessionExporter.exportAllToProxmark3()
        val file = File(context.getExternalFilesDir(null), "emv_all_sessions.json")
        file.writeText(json)
        Timber.i("Exported all sessions to ${file.absolutePath}")
    }
}
```

### Phase 6: Initial Testing (30 minutes)

1. **Build Test**
   - Verify compilation after Phases 1-5
   - Check no missing dependencies
   
2. **Real Card Test**
   - Scan actual EMV card
   - Verify all 200+ tags captured in EmvSessionDatabase
   - Verify APDU log complete
   - Test Proxmark3 JSON export
   
3. **Screen Migration Test**
   - DatabaseScreen loads sessions correctly
   - DashboardScreen shows correct statistics
   - AnalysisScreen can analyze sessions
   - All CRUD operations work (view, delete, export)
   
4. **Performance Test**
   - Measure scan time (should be identical)
   - Verify no UI lag during scan
   - Confirm async DB write completes
   
5. **Export Test**
   - Export single session to Proxmark3 JSON
   - Verify JSON structure matches Proxmark3 format
   - Verify all 200+ tags present in export
   - Test "export all" functionality

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
- [ ] Create EmvDataCollector.kt with **SINGLE-PASS parseResponse()** method
- [ ] parseResponse() does EVERYTHING in one pass:
  - [ ] Add APDU log entry (replaces addApduLogEntry)
  - [ ] Extract ALL TLV tags via EmvTlvParser (replaces manual extraction)
  - [ ] Store tags in allTags map (200+ tags)
  - [ ] Categorize by phase (PPSE/AID/GPO/RECORD/AC/etc.)
  - [ ] Extract common fields (PAN/expiry/name wherever found)
- [ ] Implement saveToDatabase() method with EmvSessionDatabase integration
- [ ] Add bytesToHex() helper method
- [ ] Build and verify EmvDataCollector compiles with database references
- [ ] **VERIFY:** Data flows through parseResponse() ONCE per APDU

### Phase 3: Update CardReadingViewModel - EmvSessionDatabase ONLY
- [ ] Add emvCollector instance to CardReadingViewModel
- [ ] Replace all 7 EMV phases with collector.parseResponse() calls
- [ ] REMOVE old saveCardProfile() call - use collector.saveToDatabase() ONLY
- [ ] Remove old extraction functions (extractAllAidsFromPpse, etc.)
- [ ] Remove addApduLogEntry function (collector handles it)
- [ ] Remove saveCardProfile function (no longer needed)
- [ ] Update parsedEmvFields to use collector.getSessionData()
- [ ] Build and verify compilation

### Phase 4: Migrate ALL Consumer Screens (2-3 hours)
- [ ] DatabaseViewModel: Replace cardDataStore → emvSessionDao
- [ ] DashboardViewModel: Replace cardDataStore → emvSessionDao
- [ ] AnalysisViewModel: Replace cardDataStore → emvSessionDao  
- [ ] DebugCommandProcessor: Replace cardDataStore → emvSessionDatabase
- [ ] Update all CRUD operations (get, delete, export)
- [ ] Build and verify all screens compile

### Phase 5: Add Proxmark3 JSON Export (1 hour)
- [ ] Create EmvSessionExporter.kt with toProxmark3Json()
- [ ] Implement Proxmark3-compatible JSON structure (PPSE, AIDs, GPO, records, cryptogram, tags, APDU log)
- [ ] Implement exportAllToProxmark3() batch export
- [ ] Add export functions to DatabaseViewModel and AnalysisViewModel
- [ ] Test export with real card data

### Phase 6: Initial Integration Testing (30 minutes)
- [ ] Test real NFC card scan
- [ ] Verify EmvSessionDatabase has 200+ tags
- [ ] Test Proxmark3 JSON export
- [ ] Test all migrated screens (Database, Dashboard, Analysis)
- [ ] Build successful

### Phase 7: Remove CardDataStore (Cleanup) (30 minutes)

#### Step 7.1: Delete CardDataStore Files
```bash
# Delete file-based storage system
rm android-app/src/main/kotlin/com/nfsp00f33r/app/storage/CardDataStore.kt
rm android-app/src/main/kotlin/com/nfsp00f33r/app/storage/CardDataStoreModule.kt
rm android-app/src/main/kotlin/com/nfsp00f33r/app/storage/CardProfileAdapter.kt
rm -rf /data/data/com.nfsp00f33r.app/files/card_profiles/  # Old encrypted files
```

#### Step 7.2: Remove from NfSp00fApplication
```kotlin
// DELETE from NfSp00fApplication.kt
private lateinit var cardDataStoreModule: CardDataStoreModule
fun getCardDataStoreModule() = cardDataStoreModule

// DELETE from onCreate()
cardDataStoreModule = CardDataStoreModule(this)
moduleRegistry.register(cardDataStoreModule)
```

#### Step 7.3: Update Imports
Search and replace in all files:
- Remove: `import com.nfsp00f33r.app.storage.CardDataStore`
- Remove: `import com.nfsp00f33r.app.storage.CardDataStoreModule`
- Remove: `import com.nfsp00f33r.app.storage.CardProfileAdapter`

### Phase 8: File Separation (OPTIONAL - Do After Core Works)
- [ ] Create EmvWorkflowExecutor.kt (~1500 lines)
- [ ] Move executeProxmark3EmvWorkflow to EmvWorkflowExecutor
- [ ] Extract phase execution functions to EmvWorkflowExecutor
- [ ] Create EmvApduBuilder.kt (~400 lines)
- [ ] Move APDU construction functions to EmvApduBuilder
- [ ] Create EmvSecurityChecker.kt (~300 lines)
- [ ] Update CardReadingViewModel to use EmvWorkflowExecutor
- [ ] Remove moved code from CardReadingViewModel
- [ ] Build and verify compilation

### Phase 9: Final Testing
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

### Phase 10: Documentation & Commit
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

**Total:** ~8-10 hours for complete single-database migration with Proxmark3 export

### Core Implementation (REQUIRED)
- **Mapping:** 30 minutes ✅ COMPLETE (this document + DATABASE_INTEGRATION_STRATEGY.md)
- **Phase 1 (EmvSessionDatabase):** 1-2 hours - Create Room database entities/DAOs
- **Phase 2 (EmvDataCollector):** 1 hour - Create single-pass parser with parseResponse()
- **Phase 3 (CardReadingViewModel):** 1-1.5 hours - Replace CardDataStore with EmvSessionDatabase
- **Phase 4 (Migrate Screens):** 2-3 hours - Update DatabaseViewModel, DashboardViewModel, AnalysisViewModel
- **Phase 5 (Proxmark3 Export):** 1 hour - Add EmvSessionExporter with Proxmark3-compatible JSON
- **Phase 6 (Initial Testing):** 30 minutes - Verify database, screens, export work
- **Phase 7 (Remove CardDataStore):** 30 minutes - Delete old file-based system
- **Phase 8 (File Separation):** 1-2 hours - OPTIONAL - Extract EmvWorkflowExecutor (DO LATER)
- **Phase 9 (Final Testing):** 30-60 minutes - Comprehensive testing
- **Phase 10 (Documentation):** 30 minutes - Update docs and commit

**Core Subtotal (Phases 1-7, 9-10):** ~8-9 hours
**Optional Refactoring (Phase 8):** +1-2 hours

### Key Milestones
1. **After Phase 3:** CardReadingViewModel saves to EmvSessionDatabase (but screens still broken)
2. **After Phase 4:** All screens migrated and working
3. **After Phase 5:** Proxmark3 JSON export functional
4. **After Phase 7:** CardDataStore completely removed, single database system
5. **After Phase 9:** Production-ready with 200+ tags, complete APDU logs, Proxmark3 export

**Priority:** Complete Phases 1-7 for full migration, Phase 8 file separation can wait

---

**Ready to proceed with Phase 1: Create Core Classes?**
