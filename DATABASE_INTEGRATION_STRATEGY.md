# Database Integration Strategy
**Created:** October 11, 2025  
**Purpose:** Map existing database infrastructure and plan integration with EmvDataCollector  
**Critical:** Preserve ALL existing DB functionality while adding EMV session tracking

---

## 🗄️ EXISTING DATABASE INFRASTRUCTURE

### 1. CardDataStore (Primary Card Storage)
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/CardDataStore.kt`  
**Type:** File-based encrypted storage (NOT Room) using JSON + AES-256-GCM  
**Status:** ✅ PRODUCTION - Used by 5+ screens

**Storage Model:**
```kotlin
CardProfile (from storage.models)
├─ profileId: String (UUID)
├─ cardType: CardType (VISA, MASTERCARD, etc.)
├─ issuer: String
├─ staticData: StaticCardData (PAN, expiry, name, track1/2)
├─ dynamicData: DynamicCardData (ATC, PIN try counter, transaction log)
├─ configuration: CardConfiguration (AID, AIP, AFL, CVM, PDOL, CDOL)
├─ cryptographicData: CryptographicData (Issuer/ICC keys, vulnerabilities)
├─ transactionHistory: List<TransactionRecord>
├─ metadata: ProfileMetadata
├─ tags: Set<String>
├─ version: Int
├─ createdAt: Instant
└─ lastModified: Instant
```

**Key Methods:**
```kotlin
fun storeProfile(profile: CardProfile): Result<String>
fun getProfile(profileId: String): CardProfile?
fun getAllProfiles(): List<CardProfile>
fun searchProfiles(cardNumber, cardType, issuer, tags): List<CardProfile>
fun deleteProfile(profileId: String): Boolean
fun exportProfile(profileId: String, outputPath: String): Result<Unit>
fun importProfile(inputPath: String): Result<String>
fun compareProfiles(profileId1, profileId2): ProfileComparison?
fun addTransaction(profileId, transaction): Boolean
```

**Used By:**
- CardReadingViewModel (lines 75, 3309, 3585, 3805) - Saves scanned cards
- DatabaseViewModel (line 29) - Displays all cards
- DashboardViewModel (line 294) - Shows recent cards
- AnalysisViewModel (line 35, 195) - Analyzes card patterns
- DebugCommandProcessor (line 144) - Debug commands

**Storage Location:** `context.filesDir/card_profiles/` (encrypted JSON files)

---

### 2. HealthDatabase (Room - Health Monitoring)
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/data/health/HealthDatabase.kt`  
**Type:** Room SQLite database  
**Status:** ✅ PRODUCTION - Phase 3 Health Monitoring

**Entities:**
- `HealthHistoryEntity` (table: health_history)

**DAO:**
- `HealthHistoryDao` - insert, getAll, getBySeverity, deleteAll, keepLastN

**Database Name:** `health_monitoring.db`

**Purpose:** Tracks module health status over time (separate from card data)

---

### 3. FuzzingDatabase (Implicit - No Database File Found)
**Location:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/fuzzing/storage/`  
**Type:** Room entities + DAO (Database class NOT FOUND)  
**Status:** ⚠️ PARTIAL - Entities and DAO exist, but no @Database class

**Entities:**
- `FuzzingSessionEntity` (table: fuzzing_sessions)
- `FuzzingAnomalyEntity` (table: fuzzing_anomalies)
- `TerminalEntity` (table: terminals)
- `FuzzingPresetEntity` (table: fuzzing_presets)

**DAO:**
- `FuzzingSessionDao` - Comprehensive fuzzing session management

**Status:** Entities/DAO ready but Database class missing (likely future feature)

---

## 🎯 INTEGRATION STRATEGY

### Decision: Parallel Database System

**WHY:** CardDataStore is file-based JSON, NOT Room. Creating a new Room database for EMV sessions is SAFER than modifying CardDataStore's encryption/serialization.

### Option 1: Separate EmvSessionDatabase (RECOMMENDED ✅)

**Create NEW Room database for EMV session tracking:**

```kotlin
@Database(
    entities = [
        EmvSessionEntity::class,
        EmvTagEntity::class,
        EmvApduLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EmvSessionDatabase : RoomDatabase() {
    abstract fun emvSessionDao(): EmvSessionDao
    abstract fun emvTagDao(): EmvTagDao
    abstract fun emvApduLogDao(): EmvApduLogDao
    
    companion object {
        private const val DATABASE_NAME = "emv_sessions.db"
        // Singleton instance pattern
    }
}
```

**Benefits:**
- ✅ Zero risk to existing CardDataStore (no changes to encryption/serialization)
- ✅ Room database optimized for queries (history, tags by phase, exports)
- ✅ Easy rollback if issues arise
- ✅ Clear separation: CardDataStore = card profiles, EmvSessionDatabase = scan sessions
- ✅ Can add more EMV-specific tables later (e.g., transaction logs, vulnerability scans)

**How They Work Together:**

```
NFC Card Scan
     ↓
EmvDataCollector (in-memory buffer)
     ↓
After scan completes:
     ├─→ EmvSessionDatabase (Room) - Save full session with 200+ tags
     │   └─ EmvSessionEntity (1 row)
     │   └─ EmvTagEntity (200+ rows)
     │   └─ EmvApduLogEntity (10-20 rows)
     │
     └─→ CardDataStore (File-based) - Save card profile summary
         └─ CardProfile (1 encrypted JSON file)
```

**CardProfile (Existing) Contains:**
- Summary data (PAN, expiry, name, AID)
- Configuration (AIP, AFL, PDOL, CDOL)
- Cryptographic data (keys, vulnerabilities)
- Transaction history

**EmvSessionEntity (NEW) Contains:**
- Full scan session details
- 200+ EMV tags (normalized in EmvTagEntity)
- Complete APDU log (EmvApduLogEntity)
- ROCA analysis
- Scan metadata (duration, phase timings)

---

### Option 2: Extend CardDataStore (NOT RECOMMENDED ❌)

**Add EMV session data to CardProfile:**

**Problems:**
- ❌ Risk breaking existing encryption/serialization
- ❌ CardProfile already complex (10+ nested data classes)
- ❌ File-based storage not optimal for querying 200+ tags
- ❌ Would need migration of ALL existing profiles
- ❌ No type safety for queries
- ❌ Harder to query by phase/tag/statusWord

---

## 📐 REVISED ARCHITECTURE

### Parallel Storage System

```
┌─────────────────────────────────────────────────────┐
│  CardReadingViewModel                               │
│  - executeProxmark3EmvWorkflow()                    │
│  - Uses EmvDataCollector                            │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  EmvDataCollector                                   │
│  - parseResponse() at each phase                    │
│  - In-memory sessionData buffer                     │
│  - saveToDatabase() after scan                      │
└─────────────────────────────────────────────────────┘
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
┌──────────────────┐   ┌────────────────────┐
│ EmvSessionDB     │   │ CardDataStore      │
│ (Room SQLite)    │   │ (File-based JSON)  │
│ - Full session   │   │ - Card profiles    │
│ - 200+ tags      │   │ - Summary data     │
│ - APDU log       │   │ - Encryption       │
│ - Queryable      │   │ - Export/Import    │
└──────────────────┘   └────────────────────┘
```

### Data Flow

**During Scan (In-Memory):**
```kotlin
// PPSE Phase
collector.parseResponse(ppseCommand, ppseResponse, "9000", EmvPhase.PPSE)
// sessionData.ppse updated, sessionData.allTags["84"] = ppseAid

// SELECT_AID Phase
collector.parseResponse(aidCommand, aidResponse, "9000", EmvPhase.SELECT_AID)
// sessionData.aids updated, sessionData.allTags["4F"] = selectedAid

// ... all phases ...
```

**After Scan Completes:**
```kotlin
// 1. Save to EmvSessionDatabase (Room)
viewModelScope.launch(Dispatchers.IO) {
    collector.saveToDatabase()  // Saves EmvSessionEntity + tags + APDU log
}

// 2. Save to CardDataStore (existing logic)
saveCardProfile(emvData)  // Existing function at line 3296
```

---

## 🔨 IMPLEMENTATION PLAN (REVISED)

### Phase 1: Create EmvSessionDatabase (NEW)

#### Step 1.1: Create Database Entities
**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvSessionEntity.kt`

```kotlin
@Entity(tableName = "emv_sessions")
data class EmvSessionEntity(
    @PrimaryKey val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Long,
    val cardUid: String?,
    
    // Summary (for quick queries)
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
    val rocaVulnerable: Boolean = false,
    val rocaStatus: String?,
    
    // Metadata
    val totalAids: Int,
    val totalRecords: Int,
    val totalTags: Int,
    val scanDurationMs: Long,
    
    // Link to CardDataStore profile (optional)
    val cardProfileId: String? = null
)
```

**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvTagEntity.kt`

```kotlin
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
    val category: String      // Application, GPO, Records, Cryptogram, etc.
)
```

**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvApduLogEntity.kt`

```kotlin
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

#### Step 1.2: Create DAOs
**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvSessionDao.kt`

```kotlin
@Dao
interface EmvSessionDao {
    @Insert
    suspend fun insertSession(session: EmvSessionEntity): Long
    
    @Query("SELECT * FROM emv_sessions ORDER BY timestamp DESC")
    suspend fun getAllSessions(): List<EmvSessionEntity>
    
    @Query("SELECT * FROM emv_sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): EmvSessionEntity?
    
    @Query("SELECT * FROM emv_sessions WHERE cardUid = :cardUid ORDER BY timestamp DESC")
    suspend fun getSessionsByCardUid(cardUid: String): List<EmvSessionEntity>
    
    @Query("SELECT * FROM emv_sessions WHERE rocaVulnerable = 1")
    suspend fun getVulnerableSessions(): List<EmvSessionEntity>
    
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
    
    @Query("SELECT * FROM emv_tags WHERE tag = :tag ORDER BY sessionId DESC")
    suspend fun getAllOccurrencesOfTag(tag: String): List<EmvTagEntity>
}

@Dao
interface EmvApduLogDao {
    @Insert
    suspend fun insertLog(logs: List<EmvApduLogEntity>)
    
    @Query("SELECT * FROM emv_apdu_log WHERE sessionId = :sessionId ORDER BY sequenceNum")
    suspend fun getLogForSession(sessionId: String): List<EmvApduLogEntity>
}
```

#### Step 1.3: Create Database Class
**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/storage/emv/EmvSessionDatabase.kt`

```kotlin
@Database(
    entities = [
        EmvSessionEntity::class,
        EmvTagEntity::class,
        EmvApduLogEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class EmvSessionDatabase : RoomDatabase() {
    
    abstract fun emvSessionDao(): EmvSessionDao
    abstract fun emvTagDao(): EmvTagDao
    abstract fun emvApduLogDao(): EmvApduLogDao
    
    companion object {
        @Volatile
        private var INSTANCE: EmvSessionDatabase? = null
        
        private const val DATABASE_NAME = "emv_sessions.db"
        
        fun getInstance(context: Context): EmvSessionDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EmvSessionDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                
                INSTANCE = instance
                instance
            }
        }
    }
}
```

---

### Phase 2: Create EmvDataCollector

#### Step 2.1: EmvSessionData (In-Memory Buffer)
**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvSessionData.kt`

```kotlin
data class EmvSessionData(
    val sessionId: String = UUID.randomUUID().toString(),
    val timestamp: Instant = Instant.now(),
    var cardUid: String? = null,
    
    // 10 categories (same as previous mapping)
    var ppse: PpseData = PpseData(),
    var aids: List<AidData> = emptyList(),
    var gpo: GpoData = GpoData(),
    var records: List<RecordData> = emptyList(),
    var getData: Map<String, String> = emptyMap(),
    var cryptogram: CryptogramData = CryptogramData(),
    var authentication: AuthenticationData = AuthenticationData(),
    var transactionLogs: List<String> = emptyList(),
    var publicKeys: PublicKeyData = PublicKeyData(),
    var apduLog: MutableList<ApduEntry> = mutableListOf(),
    
    // ALL TAGS (200+)
    var allTags: MutableMap<String, TagData> = mutableMapOf(),
    
    // ROCA
    var rocaResult: RocaTestResult? = null,
    
    // Metadata
    val startTime: Long = System.currentTimeMillis(),
    var endTime: Long? = null
)

data class TagData(
    val tag: String,
    val value: String,
    val phase: EmvPhase,
    val tagName: String,
    val category: String
)

// ... other data classes from previous mapping ...
```

#### Step 2.2: EmvDataCollector
**File:** `/android-app/src/main/kotlin/com/nfsp00f33r/app/cardreading/EmvDataCollector.kt`

```kotlin
class EmvDataCollector(private val context: Context) {
    private val database = EmvSessionDatabase.getInstance(context)
    private val parser = EmvTlvParser
    private val tagDictionary = EmvTagDictionary
    private val sessionData = EmvSessionData()
    
    fun parseResponse(
        command: ByteArray,
        response: ByteArray,
        statusWord: String,
        phase: EmvPhase,
        description: String = ""
    ) {
        // 1. Add to APDU log
        sessionData.apduLog.add(ApduEntry(
            command = command,
            response = response,
            statusWord = statusWord,
            phase = phase,
            executionTime = 0, // Will be set by caller
            description = description
        ))
        
        // 2. Extract ALL tags from response
        val parseResult = parser.parseEmvTlvData(response, phase.name)
        parseResult.tags.forEach { (tag, value) ->
            val tagName = tagDictionary.getTagName(tag)
            val category = categorizeTag(tag, phase)
            
            sessionData.allTags[tag] = TagData(
                tag = tag,
                value = value,
                phase = phase,
                tagName = tagName,
                category = category
            )
        }
        
        // 3. Smart categorization by phase
        when (phase) {
            EmvPhase.PPSE -> updatePpseData(parseResult.tags)
            EmvPhase.SELECT_AID -> updateAidData(parseResult.tags)
            EmvPhase.GPO -> updateGpoData(parseResult.tags)
            EmvPhase.READ_RECORD -> addRecordData(parseResult.tags)
            EmvPhase.GENERATE_AC -> updateCryptogramData(parseResult.tags)
            EmvPhase.INTERNAL_AUTH -> updateAuthenticationData(parseResult.tags)
            EmvPhase.GET_DATA -> updateGetData(parseResult.tags)
        }
    }
    
    suspend fun saveToDatabase() = withContext(Dispatchers.IO) {
        sessionData.endTime = System.currentTimeMillis()
        
        // Create EmvSessionEntity
        val sessionEntity = EmvSessionEntity(
            sessionId = sessionData.sessionId,
            timestamp = sessionData.timestamp.toEpochMilli(),
            cardUid = sessionData.cardUid,
            ppseAid = sessionData.ppse.aid,
            selectedAid = sessionData.aids.firstOrNull()?.aid,
            applicationLabel = sessionData.aids.firstOrNull()?.label,
            pan = sessionData.allTags["5A"]?.value,
            expiryDate = sessionData.allTags["5F24"]?.value,
            aip = sessionData.gpo.aip,
            afl = sessionData.gpo.afl,
            pdol = sessionData.gpo.pdol,
            cdol1 = sessionData.cryptogram.cdol1,
            cdol2 = sessionData.cryptogram.cdol2,
            arqc = sessionData.cryptogram.arqc,
            cid = sessionData.cryptogram.cid,
            atc = sessionData.cryptogram.atc,
            iad = sessionData.cryptogram.iad,
            rocaVulnerable = sessionData.rocaResult?.isVulnerable ?: false,
            rocaStatus = sessionData.rocaResult?.status,
            totalAids = sessionData.aids.size,
            totalRecords = sessionData.records.size,
            totalTags = sessionData.allTags.size,
            scanDurationMs = (sessionData.endTime ?: 0) - sessionData.startTime,
            cardProfileId = null // Will be set by caller if CardDataStore profile exists
        )
        
        // Insert session
        database.emvSessionDao().insertSession(sessionEntity)
        
        // Insert all tags
        val tagEntities = sessionData.allTags.values.mapIndexed { _, tagData ->
            EmvTagEntity(
                sessionId = sessionData.sessionId,
                tag = tagData.tag,
                tagName = tagData.tagName,
                value = tagData.value,
                phase = tagData.phase.name,
                category = tagData.category
            )
        }
        database.emvTagDao().insertTags(tagEntities)
        
        // Insert APDU log
        val apduEntities = sessionData.apduLog.mapIndexed { index, apduEntry ->
            EmvApduLogEntity(
                sessionId = sessionData.sessionId,
                command = bytesToHex(apduEntry.command),
                response = bytesToHex(apduEntry.response),
                statusWord = apduEntry.statusWord,
                phase = apduEntry.phase.name,
                description = apduEntry.description,
                executionTime = apduEntry.executionTime,
                sequenceNum = index + 1
            )
        }
        database.emvApduLogDao().insertLog(apduEntities)
    }
    
    fun getSessionData(): EmvSessionData = sessionData
    fun reset() { /* Clear for next scan */ }
    
    // Helper methods for categorization
    private fun updatePpseData(tags: Map<String, String>) { /* ... */ }
    private fun updateAidData(tags: Map<String, String>) { /* ... */ }
    private fun updateGpoData(tags: Map<String, String>) { /* ... */ }
    // ... etc
}

enum class EmvPhase {
    PPSE, SELECT_AID, GPO, READ_RECORD, GENERATE_AC, INTERNAL_AUTH, GET_DATA
}
```

---

### Phase 3: Integrate into CardReadingViewModel

#### Changes to CardReadingViewModel

**Add collector instance (after line 75):**
```kotlin
private val cardDataStore = NfSp00fApplication.getCardDataStoreModule()
private val emvCollector = EmvDataCollector(context)  // NEW
```

**Replace manual extraction with collector (example at line 280):**

**BEFORE:**
```kotlin
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

val realAidEntries = extractAllAidsFromPpse(ppseHex)
```

**AFTER:**
```kotlin
val ppse1PayCommand = byteArrayOf(...)
val startTime = System.currentTimeMillis()
ppseResponse = isoDep.transceive(ppse1PayCommand)
val execTime = System.currentTimeMillis() - startTime

// Single call - automatic extraction + logging
emvCollector.parseResponse(
    command = ppse1PayCommand,
    response = ppseResponse,
    statusWord = realStatusWord,
    phase = EmvPhase.PPSE,
    description = "SELECT PPSE 1PAY.SYS.DDF01"
)

// Access structured data
val ppseData = emvCollector.getSessionData().ppse
val aids = emvCollector.getSessionData().aids
```

**At end of executeProxmark3EmvWorkflow (after line 1200):**

```kotlin
// Save to BOTH databases
try {
    // 1. Save to EmvSessionDatabase (NEW - Room)
    viewModelScope.launch(Dispatchers.IO) {
        emvCollector.saveToDatabase()
        Timber.d("EMV session saved to database")
    }
    
    // 2. Save to CardDataStore (EXISTING - File-based)
    saveCardProfile(emvData)  // Existing function at line 3296
    
} catch (e: Exception) {
    Timber.e(e, "Failed to save scan data")
}
```

---

## ✅ KEY BENEFITS

### 1. Zero Risk to Existing Data
- CardDataStore unchanged (no encryption/serialization changes)
- All 5 screens using CardDataStore continue working
- Easy rollback if EmvSessionDatabase has issues

### 2. Best of Both Worlds
- **CardDataStore**: Summary card profiles, export/import, encryption, versioning
- **EmvSessionDatabase**: Full scan sessions, 200+ tags, queryable, phase-based analysis

### 3. Future-Proof
- Can add more EMV-specific tables (transaction history, vulnerability scans)
- Room migrations handled cleanly
- CardDataStore remains stable

### 4. Clean Separation
- CardProfile = "What card is this?" (identity, configuration, transactions)
- EmvSession = "What happened during this scan?" (full APDU log, all tags, timing)

---

## 📊 FINAL ARCHITECTURE DIAGRAM

```
┌─────────────────────────────────────────────────────┐
│  CardReadingViewModel                               │
│  - NFC scan workflow                                │
│  - Uses EmvDataCollector                            │
└─────────────────────────────────────────────────────┘
                    ↓
┌─────────────────────────────────────────────────────┐
│  EmvDataCollector                                   │
│  - parseResponse() at each phase (in-memory)        │
│  - EmvSessionData buffer                            │
│  - saveToDatabase() after scan                      │
└─────────────────────────────────────────────────────┘
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
┌───────────────────┐   ┌──────────────────┐
│ EmvSessionDB      │   │ CardDataStore    │
│ (NEW - Room)      │   │ (EXISTING)       │
├───────────────────┤   ├──────────────────┤
│ EmvSessionEntity  │   │ CardProfile      │
│ - Summary         │   │ - Identity       │
│ - Metadata        │   │ - Config         │
│                   │   │ - Crypto         │
│ EmvTagEntity      │   │ - Transactions   │
│ - 200+ tags       │   │ - Metadata       │
│ - Phase context   │   │                  │
│                   │   │ Encrypted JSON   │
│ EmvApduLogEntity  │   │ File-based       │
│ - Full APDU log   │   │ Export/Import    │
│ - Timing          │   │ Versioning       │
└───────────────────┘   └──────────────────┘
        ↓                       ↓
┌───────────────────┐   ┌──────────────────┐
│ QUERY BY:         │   │ QUERY BY:        │
│ - Session ID      │   │ - Profile ID     │
│ - Card UID        │   │ - PAN            │
│ - Phase           │   │ - Card Type      │
│ - Tag             │   │ - Issuer         │
│ - Status Word     │   │ - Tags           │
│ - ROCA status     │   │                  │
└───────────────────┘   └──────────────────┘
```

---

## 🎯 NEXT STEPS

1. ✅ **Mapping Complete** - Existing DB infrastructure documented
2. ⏭️ **Create EmvSessionDatabase** - New Room database (Phase 1)
3. ⏭️ **Create EmvDataCollector** - In-memory buffer + DB save (Phase 2)
4. ⏭️ **Integrate into ViewModel** - Replace manual extraction (Phase 3)
5. ⏭️ **Test Both Systems** - Verify CardDataStore unchanged, EmvSessionDB working

**Ready to proceed with Phase 1: Create EmvSessionDatabase?**
