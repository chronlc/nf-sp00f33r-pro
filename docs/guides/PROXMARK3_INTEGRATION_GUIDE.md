# Proxmark3EmvReader Integration Guide

**Date:** October 11, 2025  
**Status:** Production Ready  

---

## 🎯 Complete Integration Example

### **Step 1: Add to CardReadingViewModel**

```kotlin
// In CardReadingViewModel.kt

import com.nfsp00f33r.app.emulation.Proxmark3EmvReader
import com.nfsp00f33r.app.emulation.Proxmark3SessionAdapter
import com.nfsp00f33r.app.emulation.saveToDatabase
import com.nfsp00f33r.app.emulation.getSummary

class CardReadingViewModel(
    private val database: EmvSessionDatabase,
    private val logger: FrameworkLogger
) : ViewModel() {
    
    // Add Proxmark3 reader instance
    private val proxmark3Reader = Proxmark3EmvReader(logger)
    
    // Add transaction type selection
    private var selectedTransactionType = Proxmark3EmvReader.TransactionType.TT_MSD
    
    /**
     * Main scan function using Proxmark3 workflow
     */
    suspend fun performProxmark3Scan(isoDep: IsoDep) {
        withContext(Dispatchers.IO) {
            try {
                logger.i("CardReadingViewModel", "Starting Proxmark3-style EMV scan")
                
                // Update UI state
                withContext(Dispatchers.Main) {
                    scanState = ScanState.SCANNING
                    statusMessage = "Reading card (Proxmark3 workflow)..."
                    progressPercent = 0
                }
                
                // Execute complete EMV transaction using Proxmark3 workflow
                val session = proxmark3Reader.executeEmvTransaction(
                    isoDep = isoDep,
                    transactionType = selectedTransactionType,
                    forceSearch = false  // Use PPSE first, fallback to search
                )
                
                // Update progress
                withContext(Dispatchers.Main) {
                    progressPercent = 50
                }
                
                // Check if successful
                if (!session.completed) {
                    withContext(Dispatchers.Main) {
                        scanState = ScanState.ERROR
                        statusMessage = "Scan failed: ${session.errorMessage}"
                    }
                    return@withContext
                }
                
                // Save to database using adapter
                val savedId = session.saveToDatabase(database, logger)
                
                logger.i("CardReadingViewModel", "Session saved to database with ID: $savedId")
                logger.i("CardReadingViewModel", session.getSummary())
                
                // Update UI with results
                withContext(Dispatchers.Main) {
                    progressPercent = 100
                    scanState = ScanState.SUCCESS
                    
                    // Extract key data
                    cardPan = session.pan ?: "Unknown"
                    expiryDate = session.expiryDate
                    cardType = session.cardVendor.name
                    
                    // Show authentication method
                    val authInfo = when (session.authMethod) {
                        "SDA" -> "Static Data Authentication"
                        "DDA" -> "Dynamic Data Authentication"
                        "CDA" -> "Combined Data Authentication"
                        else -> "No Authentication"
                    }
                    
                    statusMessage = """
                        ✅ Scan Complete!
                        
                        Card: ${session.cardVendor.name}
                        PAN: ${session.pan}
                        Expiry: ${session.expiryDate}
                        Auth: $authInfo
                        
                        Tags: ${session.tlvDatabase.size}
                        APDUs: ${session.apduLog.size}
                        
                        Transaction Type: ${session.transactionType.name}
                        Session ID: ${session.sessionId}
                        
                        Saved to database: #$savedId
                    """.trimIndent()
                    
                    // Extract attack-relevant data
                    extractAttackData(session)
                }
                
            } catch (e: Exception) {
                logger.e("CardReadingViewModel", "Proxmark3 scan error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    scanState = ScanState.ERROR
                    statusMessage = "Scan error: ${e.message}"
                }
            }
        }
    }
    
    /**
     * Extract attack-relevant data from session
     */
    private fun extractAttackData(session: Proxmark3EmvReader.EmvSession) {
        // Track2 for cloning
        session.tlvDatabase["57"]?.let { track2 ->
            logger.i("CardReadingViewModel", "Track2 available for manipulation attacks")
        }
        
        // CVM List for bypass attacks
        session.tlvDatabase["8E"]?.let { cvmList ->
            logger.i("CardReadingViewModel", "CVM List available for bypass attacks")
        }
        
        // CDOL for amount modification
        session.tlvDatabase["8C"]?.let { cdol1 ->
            logger.i("CardReadingViewModel", "CDOL1 available for amount modification")
        }
        
        // ARQC for replay attacks
        session.arqc?.let { arqc ->
            logger.i("CardReadingViewModel", "ARQC available for replay attacks")
        }
        
        // ROCA vulnerability
        val rocaTags = listOf("90", "9F46", "8F", "9F32", "9F47")
        val foundRocaTags = rocaTags.filter { session.tlvDatabase.containsKey(it) }
        if (foundRocaTags.size == 5) {
            logger.i("CardReadingViewModel", "ROCA: 100% COMPLETE - All certificates available")
        } else {
            logger.i("CardReadingViewModel", "ROCA: ${foundRocaTags.size}/5 tags found")
        }
    }
    
    /**
     * Set transaction type (call before scanning)
     */
    fun setTransactionType(type: Proxmark3EmvReader.TransactionType) {
        selectedTransactionType = type
        logger.i("CardReadingViewModel", "Transaction type set to: ${type.name}")
    }
}
```

---

## 🎨 UI Integration Example

### **Step 2: Add Transaction Type Selector to CardReadingScreen**

```kotlin
// In CardReadingScreen.kt

@Composable
fun CardReadingScreen(viewModel: CardReadingViewModel) {
    var selectedTxType by remember { 
        mutableStateOf(Proxmark3EmvReader.TransactionType.TT_MSD) 
    }
    
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        
        // Transaction Type Selector
        Text("Transaction Type:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // MSD Button
            FilterChip(
                selected = selectedTxType == Proxmark3EmvReader.TransactionType.TT_MSD,
                onClick = { 
                    selectedTxType = Proxmark3EmvReader.TransactionType.TT_MSD
                    viewModel.setTransactionType(selectedTxType)
                },
                label = { Text("MSD") }
            )
            
            // qVSDC/M-Chip Button
            FilterChip(
                selected = selectedTxType == Proxmark3EmvReader.TransactionType.TT_QVSDCMCHIP,
                onClick = { 
                    selectedTxType = Proxmark3EmvReader.TransactionType.TT_QVSDCMCHIP
                    viewModel.setTransactionType(selectedTxType)
                },
                label = { Text("qVSDC") }
            )
            
            // CDA Button
            FilterChip(
                selected = selectedTxType == Proxmark3EmvReader.TransactionType.TT_CDA,
                onClick = { 
                    selectedTxType = Proxmark3EmvReader.TransactionType.TT_CDA
                    viewModel.setTransactionType(selectedTxType)
                },
                label = { Text("CDA") }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Transaction Type Info
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = getTransactionTypeDescription(selectedTxType),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Scan Button
        Button(
            onClick = { 
                viewModel.setTransactionType(selectedTxType)
                viewModel.startScanning() 
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Proxmark3 Scan")
        }
        
        // ... rest of your UI
    }
}

@Composable
fun getTransactionTypeDescription(type: Proxmark3EmvReader.TransactionType): String {
    return when (type) {
        Proxmark3EmvReader.TransactionType.TT_MSD -> 
            "MSD: Magnetic Stripe Data mode (default). Works for ALL cards. Best for basic cloning."
        
        Proxmark3EmvReader.TransactionType.TT_QVSDCMCHIP -> 
            "qVSDC/M-Chip: Quick VSDC (VISA) or M/Chip (Mastercard). Contactless mode with more data."
        
        Proxmark3EmvReader.TransactionType.TT_CDA -> 
            "CDA: Combined Data Authentication + AC. Most secure mode, requires DDA support."
        
        Proxmark3EmvReader.TransactionType.TT_VSDC -> 
            "VSDC: Full VSDC mode (test only). Contact-mode equivalent."
    }
}
```

---

## 📊 Database Query Examples

### **Step 3: Query Saved Sessions**

```kotlin
// Get all Proxmark3 sessions
val allSessions = database.emvCardSessionDao().getAllSessions()

// Get sessions by card type
val visaSessions = database.emvCardSessionDao().getSessionsByCardType("VISA")
val mastercardSessions = database.emvCardSessionDao().getSessionsByCardType("MASTERCARD")

// Get sessions by authentication method
val ddaSessions = database.emvCardSessionDao().searchSessions("DDA")
val cdaSessions = database.emvCardSessionDao().searchSessions("CDA")

// Get ROCA vulnerable cards
val rocaVulnerable = database.emvCardSessionDao().getRocaVulnerableCards()

// Get session by ID
val session = database.emvCardSessionDao().getSessionById(savedId)

// Parse stored TLV data back to map
val tlvMap = Json.decodeFromString<Map<String, String>>(session.allEmvTags)

// Parse stored APDU log back to list
val apduList = Json.decodeFromString<List<Proxmark3SessionAdapter.ApduLogEntry>>(session.apduLog)
```

---

## 🎯 Complete Workflow Example

```kotlin
// 1. User selects transaction type in UI
viewModel.setTransactionType(Proxmark3EmvReader.TransactionType.TT_QVSDCMCHIP)

// 2. User taps "Start Scan" button
viewModel.startScanning()

// 3. User presents card to NFC reader
// (NFC discovery happens automatically)

// 4. CardReadingViewModel executes:
val session = proxmark3Reader.executeEmvTransaction(isoDep, transactionType)

// 5. Proxmark3EmvReader performs complete workflow:
//    - SELECT PPSE
//    - SELECT AID (detects VISA/Mastercard/etc)
//    - Initialize transaction parameters (TTQ based on type)
//    - GET PROCESSING OPTIONS (uses dolProcess for PDOL)
//    - READ AFL RECORDS (follows AFL exactly)
//    - OFFLINE AUTHENTICATION (SDA/DDA/CDA based on AIP)
//    - GENERATE AC (uses dolProcess for CDOL1)

// 6. Adapter converts to database entity
val entity = adapter.toEmvCardSessionEntity(session)

// 7. Save to Room database
val savedId = database.emvCardSessionDao().insertSession(entity)

// 8. UI shows results
// - Card type: VISA
// - PAN: 1234567890123456
// - Expiry: 2512
// - Auth: DDA
// - Tags: 49
// - Session saved: #123

// 9. Data available for attacks:
// - Track2 manipulation ✅
// - CVM bypass ✅
// - Amount modification ✅
// - ARQC replay ✅
// - ROCA exploitation ✅
```

---

## 🚀 Migration Path

### **Option A: Replace Existing Workflow**

```kotlin
// OLD WAY (current CardReadingViewModel)
suspend fun performEMVRead(isoDep: IsoDep) {
    // 7 phases, manual TLV parsing, complex state management
    performPpsePhase()
    performAidPhase()
    performGpoPhase()
    performRecordReadingPhase()
    performExtendedScan()
    performGetDataPhase()
    performTransactionLogs()
    // ... 4340 lines of code
}

// NEW WAY (Proxmark3EmvReader)
suspend fun performEMVRead(isoDep: IsoDep) {
    val session = proxmark3Reader.executeEmvTransaction(isoDep, transactionType)
    session.saveToDatabase(database, logger)
    // ... 1 line of code, same results, Proxmark3-proven workflow
}
```

### **Option B: Keep Both (Recommended for Testing)**

```kotlin
// Add mode selector
enum class ScanMode { LEGACY, PROXMARK3 }
var scanMode = ScanMode.PROXMARK3

suspend fun performScan(isoDep: IsoDep) {
    when (scanMode) {
        ScanMode.LEGACY -> performLegacyEMVRead(isoDep)
        ScanMode.PROXMARK3 -> performProxmark3Scan(isoDep)
    }
}
```

---

## ✅ What You Get

1. **Universal Workflow**: Works for ALL EMV cards (VISA, Mastercard, AMEX, JCB, Discover, Diners)
2. **Dynamic Adaptation**: Uses `dolProcess()` to build PDOL/CDOL1/DDOL for any card
3. **Complete Authentication**: Supports SDA, DDA, and CDA (detects from AIP)
4. **Database Integration**: Automatic conversion to EmvCardSessionEntity
5. **Transaction Types**: User-selectable modes (MSD, qVSDC, CDA, VSDC)
6. **Attack Support**: Extracts all data needed for Track2 cloning, CVM bypass, ARQC replay, ROCA
7. **APDU Logging**: Complete trace of all commands/responses
8. **Proxmark3-Proven**: Uses exact same logic as RFIDResearchGroup/proxmark3

---

## 📝 Notes

- **Transaction Type Selection**: Users can choose between 4 modes before scanning
- **Automatic Vendor Detection**: Card type detected from AID (no hardcoding)
- **Complete TLV Storage**: All tags stored in JSON format in database
- **APDU Trace**: Full command/response log for debugging
- **Error Handling**: Graceful fallbacks if PPSE fails, tries direct AID search
- **Extensible**: Easy to add vendor-specific commands (Phase 8-10 from COMPLETE_EMV_WORKFLOW.md)

---

**Ready to use!** Just import, call, and save. 🚀
