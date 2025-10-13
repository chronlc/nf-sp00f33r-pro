# PM EMV Reader - Q&A

**Date:** October 11, 2025  
**Module:** PmEmvReader (Proxmark3-style EMV reader)

---

## ❓ YOUR QUESTIONS ANSWERED

### **Q1: "Name too long?"**
✅ **FIXED:** Renamed to `PmEmvReader` (was `Proxmark3EmvReader`)

**Files:**
- `PmEmvReader.kt` - Main EMV reader module
- `PmSessionAdapter.kt` - Database adapter
- `CardReadingViewModelClean.kt` - Clean ViewModel example

---

### **Q2: "Is it still parsing with our parser?"**
✅ **YES!** PmEmvReader uses your existing `EmvTlvParser` everywhere.

**Evidence:**
```kotlin
// PmEmvReader.kt line ~600
private fun parseTlvIntoSession(session: EmvSession, data: ByteArray) {
    try {
        val parser = EmvTlvParser(logger)  // YOUR PARSER!
        val tags = parser.parseTlv(data)   // YOUR METHOD!
        
        for ((tag, value) in tags) {
            session.tlvDatabase[tag] = value
        }
    } catch (e: Exception) {
        logger.e("PmEmvReader", "Error parsing TLV: ${e.message}")
    }
}
```

**Every parse operation uses EmvTlvParser:**
- PPSE response parsing ✅
- AID selection response ✅
- GPO response ✅
- AFL record parsing ✅
- GENERATE AC response ✅
- GET DATA responses ✅

---

### **Q3: "Does our parser auto-store data?"**
**NO - and that's GOOD!** Here's why:

**EmvTlvParser.parseTlv():**
```kotlin
fun parseTlv(data: ByteArray): Map<String, ByteArray> {
    // Just parses TLV and returns a Map
    // Does NOT store to database
    return parsedTags
}
```

**PmEmvReader workflow:**
```kotlin
// 1. Parse with EmvTlvParser
val parser = EmvTlvParser(logger)
val tags = parser.parseTlv(response)

// 2. Store in session object (temporary)
for ((tag, value) in tags) {
    session.tlvDatabase[tag] = value
}

// 3. When scan completes, convert and save
val entity = PmSessionAdapter.toEmvCardSessionEntity(session)
database.insertSession(entity)
```

**Why separate steps?**
- Parsing = extracting tag/value pairs from bytes
- Storage = saving to database after complete scan
- If we saved every parse, we'd have incomplete sessions in database

---

### **Q4: "Will PM reader conflict with existing storage?"**
✅ **NO CONFLICTS!** Here's the architecture:

```
┌─────────────────────────────────────────────────────┐
│                  CardReadingViewModel                │
│  (Orchestration layer - NO APDU logic)              │
└─────────────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────┐
│                    PmEmvReader                       │
│  (Protocol layer - All APDU logic)                  │
│                                                      │
│  Uses EmvTlvParser for parsing ─────────────────┐   │
│  Builds EmvSession object (temporary)           │   │
└─────────────────────────────────────────────────│───┘
                         │                        │
                         ↓                        │
┌─────────────────────────────────────────────────│───┐
│                 PmSessionAdapter                 │   │
│  (Conversion layer)                             │   │
│                                                  │   │
│  Converts EmvSession → EmvCardSessionEntity ────┘   │
└─────────────────────────────────────────────────────┘
                         │
                         ↓
┌─────────────────────────────────────────────────────┐
│              EmvSessionDatabase (Room)               │
│  (Storage layer)                                     │
│                                                      │
│  DAO.insertSession(entity)                          │
└─────────────────────────────────────────────────────┘
```

**No conflicts because:**
1. **PmEmvReader** creates its own `EmvSession` object (separate from any existing storage)
2. **EmvTlvParser** just parses bytes → returns Map (no storage side effects)
3. **PmSessionAdapter** converts `EmvSession` → `EmvCardSessionEntity` (one-way)
4. **Database DAO** handles insert (Room handles uniqueness, no duplicates)

---

## 📊 DATA FLOW EXAMPLE

```kotlin
// Step 1: User scans card
viewModel.performEmvScan(isoDep)

// Step 2: PmEmvReader executes
val session = pmReader.executeEmvTransaction(isoDep, txType)
// session = EmvSession(
//     sessionId = "abc123...",
//     tlvDatabase = {"5A": "1234...", "57": "1234D2512...", ...},
//     apduLog = [ApduEntry(...), ApduEntry(...), ...],
//     cardVendor = CardVendor.CV_VISA,
//     authMethod = "DDA",
//     completed = true
// )

// Step 3: Adapter converts to database format
val adapter = PmSessionAdapter(logger)
val entity = adapter.toEmvCardSessionEntity(session)
// entity = EmvCardSessionEntity(
//     sessionId = "abc123...",
//     cardPan = "1234567890123456",
//     cardType = "VISA",
//     authMethod = "DDA",
//     allEmvTags = """{"5A":"1234...","57":"1234D2512..."}""",
//     apduLog = """[{"cmd":"00A4...","resp":"6F..."}]""",
//     ...
// )

// Step 4: Room DAO inserts
val savedId = database.emvCardSessionDao().insertSession(entity)
// savedId = 123 (auto-increment primary key)

// Step 5: UI updates
cardPan = session.pan
statusMessage = "Saved to database: #123"
```

---

## ✅ WHAT YOU GET

### **1. Clean Architecture**
```kotlin
// OLD: 4,340 lines of APDU logic in ViewModel
// NEW: 150 lines, just UI orchestration

class CardReadingViewModelClean(...) {
    private val pmReader = PmEmvReader(logger)
    
    fun performEmvScan(isoDep: IsoDep) {
        val session = pmReader.executeEmvTransaction(isoDep, txType)
        val savedId = session.saveToDatabase(database, logger)
        // Done! All clean!
    }
}
```

### **2. Consistent Parsing**
- **ALL TLV parsing** uses your `EmvTlvParser`
- **200+ EMV tags** defined in one place
- **No duplicate parsing logic**

### **3. No Storage Conflicts**
- PmEmvReader builds temporary session object
- Adapter converts to database entity
- DAO handles insert (one save per scan)

### **4. Transaction Type Selection**
```kotlin
// User chooses transaction type before scanning
viewModel.setTransactionType(PmEmvReader.TransactionType.TT_MSD)
viewModel.setTransactionType(PmEmvReader.TransactionType.TT_QVSDCMCHIP)
viewModel.setTransactionType(PmEmvReader.TransactionType.TT_CDA)
```

### **5. Complete APDU Logging**
```kotlin
// Every APDU automatically logged
val session = pmReader.executeEmvTransaction(...)
val apduTrace = session.apduLog
// [
//   ApduEntry(cmd="00A4...", resp="6F...", sw="9000", desc="SELECT PPSE"),
//   ApduEntry(cmd="00A4...", resp="6F...", sw="9000", desc="SELECT AID"),
//   ...
// ]
```

---

## 🚀 MIGRATION GUIDE

### **Option 1: Clean Slate (Recommended)**
```kotlin
// Replace your entire CardReadingViewModel with CardReadingViewModelClean.kt
// Rename file: CardReadingViewModelClean.kt → CardReadingViewModel.kt
// Update imports in CardReadingScreen.kt
// Done! 97% smaller, infinitely cleaner
```

### **Option 2: Side-by-Side Testing**
```kotlin
// Keep both ViewModels
class CardReadingViewModel(...) {
    private val pmReader = PmEmvReader(logger)
    var usePmReader = true  // Toggle for testing
    
    suspend fun performScan(isoDep: IsoDep) {
        if (usePmReader) {
            performPmScan(isoDep)  // New way
        } else {
            performLegacyScan(isoDep)  // Old way
        }
    }
    
    private suspend fun performPmScan(isoDep: IsoDep) {
        val session = pmReader.executeEmvTransaction(isoDep, selectedTransactionType)
        session.saveToDatabase(database, logger)
    }
    
    private suspend fun performLegacyScan(isoDep: IsoDep) {
        // Your existing 4,340 lines stay here for comparison
    }
}
```

---

## 📁 FILE STRUCTURE

```
android-app/src/main/kotlin/com/nfsp00f33r/app/
├── emulation/
│   ├── PmEmvReader.kt              # Main EMV reader (900 lines)
│   ├── PmSessionAdapter.kt         # Database adapter (300 lines)
│   └── EmvTlvParser.kt            # Existing parser (unchanged)
├── screens/cardreading/
│   ├── CardReadingViewModelClean.kt  # Clean ViewModel (150 lines)
│   └── CardReadingScreen.kt          # UI (add transaction type selector)
└── storage/
    ├── EmvSessionDatabase.kt       # Room database (unchanged)
    └── EmvCardSessionEntity.kt     # Database entity (unchanged)
```

---

## 🎯 SUMMARY

| Question | Answer |
|----------|--------|
| **Name too long?** | Fixed: `PmEmvReader` |
| **Uses our parser?** | YES! `EmvTlvParser` everywhere |
| **Parser auto-stores?** | NO - parser just parses, storage is separate |
| **Conflicts?** | NO - clean separation of concerns |
| **How to use?** | `pmReader.executeEmvTransaction()` → `session.saveToDatabase()` |
| **Lines of code?** | ViewModel: 4,340 → 150 (97% reduction) |

---

**Ready to migrate! Everything is modular, clean, and conflict-free.** 🚀
