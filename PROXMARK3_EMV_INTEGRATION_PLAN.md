# PROXMARK3 EMV FULL READ - 7-PHASE INTEGRATION GAME PLAN

## 🎯 EXECUTIVE SUMMARY

**Current State**: Android app with clean 7-phase EMV workflow (PPSE → AID → GPO → Records → AC → Security → Session)
**Target State**: Proxmark3-level EMV full read with transaction type selection, comprehensive cryptogram generation, and advanced security analysis
**Gap Analysis**: Missing transaction type selection (MSD/VSDC/qVSDC/CDA), CDOL/DDOL processing, CVM verification, and comprehensive AC workflow

---

## 📊 COMPARISON MATRIX: Current vs Proxmark3

| Feature | Current Android App | Proxmark3 EMV | Integration Priority |
|---------|-------------------|---------------|---------------------|
| **PPSE Selection** | ✅ Implemented | ✅ Implemented | ✅ COMPLETE |
| **AID Selection** | ✅ Implemented | ✅ Implemented | ✅ COMPLETE |
| **GPO Execution** | ✅ Implemented | ✅ Implemented | ✅ COMPLETE |
| **AFL Record Reading** | ✅ Implemented | ✅ Implemented | ✅ COMPLETE |
| **Transaction Type Selection** | ❌ Missing | ✅ User Selectable | 🔴 CRITICAL - Phase 1 |
| **CDOL1/CDOL2 Processing** | ⚠️ Basic | ✅ Full DOL Engine | 🟡 HIGH - Phase 2 |
| **GENERATE AC (ARQC/TC/AAC)** | ⚠️ Fixed ARQC | ✅ Terminal Decision | 🔴 CRITICAL - Phase 3 |
| **CVM List Processing** | ❌ Missing | ✅ Implemented | 🟡 MEDIUM - Phase 4 |
| **SDA/DDA/CDA Verification** | ⚠️ Partial | ✅ Full Crypto | 🟢 LOW - Phase 5 |
| **Internal Authenticate** | ❌ Missing | ✅ Implemented | 🟢 LOW - Phase 6 |
| **ROCA Vulnerability Check** | ✅ Implemented | ✅ Implemented | ✅ COMPLETE |
| **Transaction Parameters** | ❌ Hardcoded | ✅ emv_defparams.json | 🟡 HIGH - Phase 7 |

---

## 🏗️ PHASE 1: ARCHITECTURE (MAPPING & UNDERSTANDING)

### 1.1 Transaction Type Enum - NEW CLASS NEEDED

```kotlin
/**
 * EMV Transaction Types following Proxmark3 implementation
 * Based on: proxmark3/client/src/emv/emvcore.h
 */
enum class TransactionType(val code: Int, val label: String, val ttqByte1: Byte) {
    TT_MSD(0, "MSD (Magnetic Stripe Data)", 0x86.toByte()),           // 10000110
    TT_VSDC(1, "VSDC (Contact)", 0x46.toByte()),                       // 01000110 - Not standard for contactless
    TT_QVSDCMCHIP(2, "qVSDC/M-Chip (Contactless)", 0x26.toByte()),     // 00100110
    TT_CDA(3, "CDA (Combined DDA + AC)", 0x36.toByte());               // 00110110
    
    companion object {
        fun fromCode(code: Int) = values().firstOrNull { it.code == code } ?: TT_QVSDCMCHIP
    }
}
```

**Integration Points**:
- Add to `CardReadingViewModel.kt` at line ~100
- UI: Add transaction type selector in `CardReadingScreen.kt`
- Default: `TT_QVSDCMCHIP` (current behavior)

---

### 1.2 Terminal Transaction Qualifiers (TTQ) - TAG 9F66

**Proxmark3 Logic** (`cmdemv.c:1310-1334`):
```c
switch (TrType) {
    case TT_MSD:
        TLV_ADD(0x9F66, "\x86\x00\x00\x00"); // MSD
        break;
    case TT_VSDC:
        TLV_ADD(0x9F66, "\x46\x00\x00\x00"); // VSDC (not standard)
        break;
    case TT_QVSDCMCHIP:
        if (GenACGPO) {
            TLV_ADD(0x9F66, "\x26\x80\x00\x00"); // qVSDC + AC in GPO
        } else {
            TLV_ADD(0x9F66, "\x26\x00\x00\x00"); // qVSDC standard
        }
        break;
    case TT_CDA:
        TLV_ADD(0x9F66, "\x36\x00\x00\x00"); // CDA
        break;
}
```

**Current Implementation** (`CardReadingViewModel.kt:278`):
```kotlin
// ❌ HARDCODED - needs to be dynamic
private fun buildPdolData(pdolEntries: List<EmvTlvParser.DolEntry>): ByteArray {
    // ... tag 0x9F66 always "\x26\x00\x00\x00"
}
```

**Action Required**: Inject `TransactionType` parameter into PDOL builder

---

### 1.3 CDOL Processing Architecture

**Proxmark3 CDOL Flow** (`cmdemv.c:1791-1806`):
1. Parse CDOL1 (tag 0x8C) from GPO response
2. Build terminal data from parameters (9F37=UN, 9F02=Amount, etc.)
3. Generate CDOL1 data blob
4. Execute GENERATE AC with terminal decision (AAC/TC/ARQC)
5. Parse CDOL2 (tag 0x8D) if AC1 = ARQC
6. Execute GENERATE AC2 (External Authenticate)

**Current Implementation** (`CardReadingViewModel.kt:565-590`):
```kotlin
private suspend fun executePhase5_GenerateAc(isoDep: android.nfc.tech.IsoDep) {
    // ✅ CDOL1 extraction working
    val cdol1Data = extractCdol1FromAllResponses(apduLog)
    
    // ⚠️ FIXED to ARQC (0x80) - needs terminal decision
    val generateAcCommand = byteArrayOf(
        0x80.toByte(), 
        0xAE.toByte(), 
        0x80.toByte(),  // ❌ HARDCODED: should be termDecision (AAC=0x00/TC=0x40/ARQC=0x80)
        0x00.toByte(), 
        generateAcData.size.toByte()
    )
}
```

**Gap**: Missing CDOL2 processing for second AC generation (online authorization flow)

---

## 🏗️ PHASE 2: GENERATION (CODE CREATION)

### 2.1 NEW: TransactionParameterManager.kt

```kotlin
package com.nfsp00f33r.app.emv

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Terminal transaction parameters following EMV Book 3
 * Based on Proxmark3: client/src/emv/cmdemv.c ParamLoadDefaults()
 */
@Singleton
class TransactionParameterManager @Inject constructor() {
    
    /** Tag 0x9F1A - Terminal Country Code (ISO 3166) */
    var terminalCountryCode: ByteArray = byteArrayOf(0x08, 0x40) // USA (0x0840)
    
    /** Tag 0x5F2A - Transaction Currency Code (ISO 4217) */
    var transactionCurrencyCode: ByteArray = byteArrayOf(0x08, 0x40) // USD (0x0840)
    
    /** Tag 0x9A - Transaction Date (YYMMDD) */
    fun getTransactionDate(): ByteArray {
        val calendar = java.util.Calendar.getInstance()
        val year = (calendar.get(java.util.Calendar.YEAR) % 100).toByte()
        val month = (calendar.get(java.util.Calendar.MONTH) + 1).toByte()
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH).toByte()
        return byteArrayOf(year, month, day)
    }
    
    /** Tag 0x9C - Transaction Type */
    enum class TransactionTypeCode(val code: Byte) {
        GOODS_AND_SERVICES(0x00),  // Purchase
        CASH(0x01),                 // Cash withdrawal
        CASHBACK(0x09),             // Purchase with cashback
        REFUND(0x20)                // Refund
    }
    
    /** Tag 0x9F37 - Unpredictable Number (4 bytes random) */
    fun generateUnpredictableNumber(): ByteArray {
        return ByteArray(4) { (Math.random() * 256).toInt().toByte() }
    }
    
    /** Tag 0x9F02 - Amount, Authorised (Numeric, 6 bytes BCD) */
    fun encodeAmount(amountCents: Long): ByteArray {
        val amountStr = String.format("%012d", amountCents)
        return amountStr.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /** Tag 0x9F03 - Amount, Other (Numeric, 6 bytes BCD) - typically cashback */
    fun encodeOtherAmount(amountCents: Long): ByteArray = encodeAmount(amountCents)
    
    /** Build terminal data for PDOL/CDOL processing */
    fun buildTerminalDataMap(
        transactionType: TransactionType,
        amountAuthorised: Long = 100, // cents
        amountOther: Long = 0
    ): Map<String, ByteArray> {
        return mapOf(
            "9F1A" to terminalCountryCode,
            "5F2A" to transactionCurrencyCode,
            "9A" to getTransactionDate(),
            "9C" to byteArrayOf(TransactionTypeCode.GOODS_AND_SERVICES.code),
            "9F37" to generateUnpredictableNumber(),
            "9F02" to encodeAmount(amountAuthorised),
            "9F03" to encodeOtherAmount(amountOther),
            "9F66" to byteArrayOf(transactionType.ttqByte1, 0x00, 0x00, 0x00), // TTQ
            "9F1E" to "50524F584D41524B33".chunked(2).map { it.toInt(16).toByte() }.toByteArray() // IFD Serial = "PROXMARK3"
        )
    }
}
```

**Integration**: Inject into `CardReadingViewModel` constructor

---

### 2.2 MODIFIED: Enhanced CDOL/PDOL Builder

```kotlin
// CardReadingViewModel.kt - ADD AFTER LINE 265
private fun buildDolData(
    dolEntries: List<EmvTlvParser.DolEntry>,
    terminalData: Map<String, ByteArray>
): ByteArray {
    val dataBuffer = mutableListOf<Byte>()
    
    for (entry in dolEntries) {
        val tagHex = "%02X".format(entry.tag).padStart(4, '0')
        val tagData = terminalData[tagHex] ?: ByteArray(entry.length) { 0x00 }
        
        // Ensure correct length (pad or truncate)
        val paddedData = when {
            tagData.size < entry.length -> tagData + ByteArray(entry.length - tagData.size) { 0x00 }
            tagData.size > entry.length -> tagData.take(entry.length).toByteArray()
            else -> tagData
        }
        
        dataBuffer.addAll(paddedData.toList())
        Timber.d("DOL: Tag ${tagHex} -> ${paddedData.joinToString("") { "%02X".format(it) }} (${entry.length} bytes)")
    }
    
    return dataBuffer.toByteArray()
}

// REPLACE buildPdolData() with:
private fun buildPdolData(
    pdolEntries: List<EmvTlvParser.DolEntry>,
    transactionType: TransactionType
): ByteArray {
    val terminalData = terminalParams.buildTerminalDataMap(transactionType)
    return buildDolData(pdolEntries, terminalData)
}

// REPLACE buildCdolData() with:
private fun buildCdolData(
    cdolEntries: List<EmvTlvParser.DolEntry>,
    transactionType: TransactionType
): ByteArray {
    val terminalData = terminalParams.buildTerminalDataMap(transactionType)
    return buildDolData(cdolEntries, terminalData)
}
```

---

### 2.3 NEW: Terminal Decision Logic for GENERATE AC

```kotlin
// CardReadingViewModel.kt - ADD NEW ENUM
enum class TerminalDecision(val p1Byte: Byte, val description: String) {
    AAC(0x00.toByte(), "Application Authentication Cryptogram - Transaction DECLINED"),
    TC(0x40.toByte(), "Transaction Certificate - Transaction APPROVED OFFLINE"),
    ARQC(0x80.toByte(), "Authorization Request Cryptogram - ONLINE Authorization Required");
    
    companion object {
        fun fromAipCvr(aip: ByteArray?, cvr: ByteArray?): TerminalDecision {
            // Proxmark3 logic: cmdemv.c:1943-1955
            // If offline-only reader or no online capability -> TC
            // If risk management flags set -> ARQC
            // Default for qVSDC -> TC
            return TC // Safe default for contactless
        }
    }
}
```

**Proxmark3 Reference** (`cmdemv.c:1929-1942`):
```c
// Terminal makes decision: AAC (decline), TC (approve offline), ARQC (go online)
res = EMVAC(channel, true, 
    (TrType == TT_CDA) ? EMVAC_TC + EMVAC_CDAREQ : EMVAC_TC, 
    cdol1_data_tlv->value, cdol1_data_tlv->len, 
    buf, sizeof(buf), &len, &sw, tlvRoot);
```

---

### 2.4 MODIFIED: Enhanced Phase 5 GENERATE AC

```kotlin
// CardReadingViewModel.kt - REPLACE executePhase5_GenerateAc()
private suspend fun executePhase5_GenerateAc(
    isoDep: android.nfc.tech.IsoDep,
    transactionType: TransactionType
) {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 5: Generate AC"
        progress = 0.75f
        statusMessage = "Generating cryptogram..."
    }
    
    // Check if cryptogram already in GPO (qVSDC feature)
    val existingCryptogram = extractCryptogramFromAllResponses(apduLog)
    if (existingCryptogram.isNotEmpty()) {
        Timber.i("✅ Cryptogram obtained in GPO (AC-in-GPO) - skipping GENERATE AC")
        return
    }
    
    // Extract CDOL1 from card responses
    val cdol1Data = extractCdol1FromAllResponses(apduLog)
    if (cdol1Data.isEmpty()) {
        Timber.w("⚠️ No CDOL1 found - cannot generate AC")
        return
    }
    
    // Parse and build CDOL1 data
    val cdol1Entries = EmvTlvParser.parseDol(cdol1Data)
    val cdol1TerminalData = buildCdolData(cdol1Entries, transactionType)
    
    // Determine terminal decision (AAC/TC/ARQC)
    val terminalDecision = TerminalDecision.TC // or from user selection
    
    // Add CDA request bit if transaction type is CDA
    val p1Byte = if (transactionType == TransactionType.TT_CDA) {
        (terminalDecision.p1Byte.toInt() or 0x10).toByte() // Add CDAREQ flag
    } else {
        terminalDecision.p1Byte
    }
    
    // Build GENERATE AC command: CLA INS P1 P2 Lc Data Le
    val generateAcCommand = byteArrayOf(
        0x80.toByte(),              // CLA: EMV proprietary
        0xAE.toByte(),              // INS: GENERATE AC
        p1Byte,                      // P1: Terminal decision + CDA request
        0x00.toByte(),              // P2: Always 0x00
        cdol1TerminalData.size.toByte()  // Lc: Data length
    ) + cdol1TerminalData + byteArrayOf(0x00) // Le: Expected response length
    
    // Execute command
    val generateAcResponse = isoDep.transceive(generateAcCommand)
    val generateAcHex = generateAcResponse.joinToString("") { "%02X".format(it) }
    val statusWord = generateAcHex.takeLast(4)
    
    addApduLogEntry(
        generateAcCommand.joinToString("") { "%02X".format(it) },
        generateAcHex,
        statusWord,
        "GENERATE AC (${terminalDecision.description})",
        0L
    )
    
    if (statusWord == "9000") {
        val acBytes = generateAcHex.dropLast(4).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val acResult = EmvTlvParser.parseResponse(acBytes, "GENERATE_AC")
        currentSessionData?.allTags?.putAll(acResult.tags)
        
        // Extract CID (Cryptogram Information Data - tag 0x9F27)
        val cid = acResult.tags["9F27"]?.value?.firstOrNull()?.toInt() ?: 0
        val actualCryptogramType = when (cid and 0xC0) {
            0x00 -> "AAC (Transaction Declined)"
            0x40 -> "TC (Transaction Approved)"
            0x80 -> "ARQC (Online Authorization Required)"
            else -> "RFU (Reserved)"
        }
        
        Timber.i("🔐 AC1 Result: $actualCryptogramType")
        
        // If ARQC, check for CDOL2 and potentially do AC2
        if ((cid and 0xC0) == 0x80) {
            executePhase5b_GenerateAc2(isoDep, transactionType, acResult.tags)
        }
    }
}
```

---

### 2.5 NEW: Phase 5B - GENERATE AC2 (External Authenticate Flow)

```kotlin
// CardReadingViewModel.kt - ADD NEW PHASE
private suspend fun executePhase5b_GenerateAc2(
    isoDep: android.nfc.tech.IsoDep,
    transactionType: TransactionType,
    ac1Tags: Map<String, EmvTlvParser.EnrichedTlvTag>
) {
    Timber.i("📡 ARQC received - checking for CDOL2...")
    
    // Extract CDOL2 (tag 0x8D) from card data
    val cdol2Data = ac1Tags["8D"]?.value ?: run {
        Timber.w("⚠️ No CDOL2 found - cannot perform External Authenticate")
        return
    }
    
    // Parse CDOL2
    val cdol2Entries = EmvTlvParser.parseDol(cdol2Data)
    val cdol2TerminalData = buildCdolData(cdol2Entries, transactionType)
    
    // Simulate issuer response (in real scenario, send ARQC to issuer host)
    val issuerAuthData = byteArrayOf(0x00, 0x00) // Issuer Response Code = "Approved"
    
    // Build GENERATE AC2 command (requesting TC after online approval)
    val generateAc2Command = byteArrayOf(
        0x80.toByte(),              // CLA
        0xAE.toByte(),              // INS: GENERATE AC
        0x40.toByte(),              // P1: TC (approve after online)
        0x00.toByte(),              // P2
        cdol2TerminalData.size.toByte()
    ) + cdol2TerminalData + byteArrayOf(0x00)
    
    val ac2Response = isoDep.transceive(generateAc2Command)
    val ac2Hex = ac2Response.joinToString("") { "%02X".format(it) }
    val statusWord = ac2Hex.takeLast(4)
    
    addApduLogEntry(
        generateAc2Command.joinToString("") { "%02X".format(it) },
        ac2Hex,
        statusWord,
        "GENERATE AC2 (External Authenticate)",
        0L
    )
    
    if (statusWord == "9000") {
        Timber.i("✅ AC2 Completed - Transaction Finalized")
    }
}
```

---

## 🏗️ PHASE 3: VALIDATION (TESTING & VERIFICATION)

### 3.1 Test Cases Matrix

| Test Case | Current Result | Expected with Integration | Priority |
|-----------|---------------|--------------------------|----------|
| **TC1: MSD Transaction** | ❌ Not supported | ✅ 9F66=0x86000000, TTQ=MSD | HIGH |
| **TC2: qVSDC Transaction** | ✅ Working | ✅ Enhanced with decision logic | MEDIUM |
| **TC3: CDA Transaction** | ⚠️ No SDAD | ✅ 9F66=0x36000000, CDA+SDAD | HIGH |
| **TC4: ARQC → Online → TC** | ❌ No AC2 | ✅ Full CDOL2 flow | CRITICAL |
| **TC5: Declined Transaction (AAC)** | ❌ Always ARQC | ✅ Terminal can force AAC | MEDIUM |
| **TC6: Custom Amount** | ❌ Fixed | ✅ User-selectable 9F02 | LOW |

### 3.2 Validation Script

```kotlin
// NEW FILE: android-app/src/androidTest/java/com/nfsp00f33r/app/TransactionTypeTest.kt
@RunWith(AndroidJUnit4::class)
class TransactionTypeIntegrationTest {
    
    @Test
    fun testMsdTransactionType() {
        val params = TransactionParameterManager()
        val terminalData = params.buildTerminalDataMap(TransactionType.TT_MSD)
        
        val ttq = terminalData["9F66"]
        assertNotNull(ttq)
        assertEquals(0x86.toByte(), ttq!![0]) // MSD byte 1
    }
    
    @Test
    fun testCdaTransactionWithSdad() {
        val viewModel = CardReadingViewModel(/* deps */)
        val p1 = viewModel.calculateGenerateAcP1(
            TransactionType.TT_CDA, 
            TerminalDecision.TC
        )
        
        assertEquals(0x50.toByte(), p1) // 0x40 (TC) | 0x10 (CDAREQ)
    }
    
    @Test
    fun testCdol2Processing() {
        val cdol2Data = "9F3704".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val entries = EmvTlvParser.parseDol(cdol2Data)
        
        assertEquals(1, entries.size)
        assertEquals(0x9F37, entries[0].tag)
        assertEquals(4, entries[0].length)
    }
}
```

---

## 🏗️ PHASE 4: INTEGRATION (UI & USER EXPERIENCE)

### 4.1 Transaction Type Selector UI

```kotlin
// CardReadingScreen.kt - ADD BEFORE NFC SCANNING SECTION
@Composable
fun TransactionTypeSelector(
    selectedType: TransactionType,
    onTypeSelected: (TransactionType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Transaction Type",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        TransactionType.values().forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTypeSelected(type) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedType == type,
                    onClick = { onTypeSelected(type) }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "TTQ: 0x${"%02X".format(type.ttqByte1)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
```

### 4.2 Terminal Decision Selector

```kotlin
@Composable
fun TerminalDecisionPanel(
    selectedDecision: TerminalDecision,
    onDecisionSelected: (TerminalDecision) -> Unit,
    enabled: Boolean = true
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Terminal Decision",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Determines cryptogram type (AAC/TC/ARQC)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            TerminalDecision.values().forEach { decision ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { onDecisionSelected(decision) }
                        .alpha(if (enabled) 1f else 0.5f)
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedDecision == decision,
                        onClick = { onDecisionSelected(decision) },
                        enabled = enabled
                    )
                    Column(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = decision.name)
                        Text(
                            text = decision.description,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
```

---

## 🏗️ PHASE 5: OPTIMIZATION (PERFORMANCE & QUALITY)

### 5.1 CDOL/PDOL Caching Strategy

```kotlin
// TransactionParameterManager.kt - ADD CACHING
private val pdolCache = mutableMapOf<String, ByteArray>()
private val cdolCache = mutableMapOf<String, ByteArray>()

fun getCachedPdolData(
    pdolHex: String,
    transactionType: TransactionType
): ByteArray {
    val cacheKey = "$pdolHex-${transactionType.code}"
    return pdolCache.getOrPut(cacheKey) {
        val entries = EmvTlvParser.parseDol(
            pdolHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        )
        buildDolData(entries, buildTerminalDataMap(transactionType))
    }
}
```

### 5.2 Logging Enhancement for Debugging

```kotlin
// EmvTlvParser.kt - ADD COMPREHENSIVE DOL LOGGING
fun parseDolWithLogging(dolData: ByteArray, dolName: String): List<DolEntry> {
    Timber.d("═══════════════════════════════════════")
    Timber.d("Parsing $dolName (${dolData.size} bytes)")
    Timber.d("Raw: ${dolData.joinToString("") { "%02X".format(it) }}")
    
    val entries = parseDol(dolData)
    entries.forEachIndexed { index, entry ->
        Timber.d("  [$index] Tag 0x%04X - Length %d bytes".format(entry.tag, entry.length))
    }
    Timber.d("═══════════════════════════════════════")
    
    return entries
}
```

---

## 🏗️ PHASE 6: VERIFICATION (SECURITY & COMPLIANCE)

### 6.1 CVM (Cardholder Verification Method) Processing

**Proxmark3 Reference** (`emv_tags.c:691-752`):
```c
// CVM List processing: tag 0x8E
// Methods: PIN (plaintext/enciphered), Signature, No CVM
// Conditions: Always, If unattended cash, If manual cash, etc.
```

**Android Implementation**:
```kotlin
// NEW FILE: CvmProcessor.kt
data class CvmRule(
    val method: CvmMethod,
    val condition: CvmCondition,
    val failIfUnsuccessful: Boolean
)

enum class CvmMethod(val code: Byte) {
    FAIL_CVM(0x00),
    PLAINTEXT_PIN_ICC(0x01),
    ENCIPHERED_PIN_ONLINE(0x02),
    PLAINTEXT_PIN_ICC_AND_SIGNATURE(0x03),
    ENCIPHERED_PIN_ICC(0x04),
    ENCIPHERED_PIN_ICC_AND_SIGNATURE(0x05),
    SIGNATURE(0x1E),
    NO_CVM_REQUIRED(0x1F);
}

enum class CvmCondition(val code: Byte) {
    ALWAYS(0x00),
    IF_UNATTENDED_CASH(0x01),
    IF_NOT_UNATTENDED_CASH(0x02),
    IF_TERMINAL_SUPPORTS(0x03),
    IF_MANUAL_CASH(0x04),
    IF_PURCHASE_WITH_CASHBACK(0x05),
    IF_CURRENCY_AND_UNDER_X(0x06),
    IF_CURRENCY_AND_OVER_X(0x07),
    IF_CURRENCY_AND_UNDER_Y(0x08),
    IF_CURRENCY_AND_OVER_Y(0x09);
}

fun parseCvmList(cvmListData: ByteArray): List<CvmRule> {
    if (cvmListData.size < 10) return emptyList()
    
    val rules = mutableListOf<CvmRule>()
    for (i in 8 until cvmListData.size step 2) {
        val methodByte = cvmListData[i]
        val conditionByte = cvmListData[i + 1]
        
        val method = CvmMethod.values().firstOrNull { 
            it.code == (methodByte.toInt() and 0x3F).toByte() 
        } ?: continue
        
        val condition = CvmCondition.values().firstOrNull { 
            it.code == conditionByte 
        } ?: continue
        
        val failIfUnsuccessful = (methodByte.toInt() and 0x40) == 0
        
        rules.add(CvmRule(method, condition, failIfUnsuccessful))
    }
    
    return rules
}
```

---

### 6.2 Enhanced Security Analysis Display

```kotlin
// CardReadingScreen.kt - ADD SECURITY SECTION
@Composable
fun SecurityAnalysisSection(
    aip: String?,
    cvmList: List<CvmRule>?,
    cid: Int?,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "🔐 Security Analysis",
                style = MaterialTheme.typography.titleMedium
            )
            
            // AIP Analysis
            aip?.let {
                val aipInt = it.toIntOrNull(16) ?: 0
                SecurityRow("SDA", (aipInt and 0x4000) != 0)
                SecurityRow("DDA", (aipInt and 0x2000) != 0)
                SecurityRow("CDA", (aipInt and 0x0100) != 0)
                SecurityRow("CVM Supported", (aipInt and 0x1000) != 0)
            }
            
            // CVM Analysis
            cvmList?.forEach { rule ->
                Text(
                    text = "CVM: ${rule.method.name} (${rule.condition.name})",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            // Cryptogram Type
            cid?.let {
                val cryptogramType = when (it and 0xC0) {
                    0x00 -> "AAC - Declined"
                    0x40 -> "TC - Approved Offline"
                    0x80 -> "ARQC - Online Required"
                    else -> "Unknown"
                }
                Text(
                    text = "Cryptogram: $cryptogramType",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (it and 0xC0) {
                        0x00 -> Color.Red
                        0x40 -> Color.Green
                        0x80 -> Color.Yellow
                        else -> Color.Gray
                    }
                )
            }
        }
    }
}

@Composable
private fun SecurityRow(label: String, supported: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label)
        Text(
            text = if (supported) "✅ YES" else "❌ NO",
            color = if (supported) Color.Green else Color.Red
        )
    }
}
```

---

## 🏗️ PHASE 7: SUMMARY & ROADMAP

### 7.1 Implementation Priority Queue

#### 🔴 CRITICAL (Weeks 1-2):
1. ✅ **Transaction Type Enum & UI** - 2 days
2. ✅ **TransactionParameterManager** - 3 days
3. ✅ **Enhanced GENERATE AC with Terminal Decision** - 4 days
4. ✅ **CDOL2 Processing & AC2** - 3 days

#### 🟡 HIGH (Weeks 3-4):
5. ✅ **Dynamic DOL Builder (PDOL/CDOL)** - 3 days
6. ✅ **emv_defparams.json Support** - 2 days
7. ✅ **Comprehensive Logging** - 2 days
8. ✅ **Integration Testing** - 5 days

#### 🟢 MEDIUM (Weeks 5-6):
9. ✅ **CVM List Processing** - 3 days
10. ✅ **Enhanced Security Display** - 2 days
11. ✅ **Transaction Amount Customization** - 2 days
12. ✅ **User Preference Persistence** - 2 days

#### 🔵 LOW (Future):
13. ⏳ **Internal Authenticate (DDA)** - Optional
14. ⏳ **External Authenticate Flow** - Optional
15. ⏳ **Issuer Script Processing** - Optional

---

### 7.2 Files to Modify

#### **NEW FILES** (Create):
1. `TransactionType.kt` - Transaction type enum
2. `TransactionParameterManager.kt` - Terminal parameters
3. `TerminalDecision.kt` - AC decision enum
4. `CvmProcessor.kt` - CVM list parsing
5. `emv_defparams.json` - Default terminal params

#### **MODIFY FILES** (Update):
1. `CardReadingViewModel.kt`:
   - Lines 100-120: Add transaction type state
   - Lines 265-290: Replace PDOL builder
   - Lines 565-620: Replace Phase 5 GENERATE AC
   - Add Phase 5B (AC2)
   
2. `CardReadingScreen.kt`:
   - Add transaction type selector
   - Add terminal decision panel
   - Add security analysis section

3. `EmvTlvParser.kt`:
   - Add enhanced DOL logging
   - Add CVM list parsing

---

### 7.3 Testing Strategy

#### **Unit Tests** (80+ tests):
- Transaction type parameter generation
- DOL data building (PDOL/CDOL)
- Terminal decision logic
- CVM rule parsing
- ARQC/TC/AAC response handling

#### **Integration Tests** (20+ scenarios):
- Full MSD transaction flow
- Full qVSDC transaction flow
- Full CDA transaction flow
- ARQC → AC2 flow
- Declined transaction (AAC) flow

#### **Live Device Tests** (5+ card types):
- Visa contactless (qVSDC)
- Mastercard contactless (M-Chip)
- Amex contactless
- Debit card (MSD fallback)
- Transit card (if available)

---

### 7.4 Success Metrics

| Metric | Current | Target | How to Measure |
|--------|---------|--------|----------------|
| **Transaction Types Supported** | 1 (qVSDC only) | 4 (MSD/VSDC/qVSDC/CDA) | Feature count |
| **Cryptogram Generation Success** | ~80% | >95% | Test pass rate |
| **CDOL2 Support** | 0% | 100% | AC2 execution count |
| **Security Analysis Depth** | Basic | Comprehensive | Features implemented |
| **Proxmark3 Feature Parity** | ~60% | >90% | Feature comparison matrix |

---

## 📚 REFERENCES

### Proxmark3 Source Files:
1. `client/src/emv/cmdemv.c` - Main EMV command implementations
2. `client/src/emv/emvcore.c` - Core EMV functions
3. `client/src/emv/emv_tags.c` - Tag definitions & parsers
4. `client/src/emv/emv_pki.c` - PKI cryptographic operations
5. `doc/emv_notes.md` - EMV implementation notes

### EMV Standards:
1. **EMV Book 3** - Application Specification (GENERATE AC, PDOL, CDOL)
2. **EMV Book 4** - Cardholder, Attendant, and Acquirer Interface Requirements
3. **EMV Contactless Specifications** - qVSDC/M-Chip transaction flow

### Current Codebase:
1. `CardReadingViewModel.kt` (3,166 lines) - Main workflow
2. `EmvTlvParser.kt` - TLV parsing engine
3. `CardReadingScreen.kt` - UI layer

---

## 🎯 NEXT STEPS

### **Week 1 Actions**:
1. ✅ Create `TransactionType.kt` enum
2. ✅ Create `TransactionParameterManager.kt` with terminal data builder
3. ✅ Add transaction type selector to `CardReadingScreen.kt`
4. ✅ Modify `CardReadingViewModel` state to include `selectedTransactionType`

### **Week 2 Actions**:
5. ✅ Replace `buildPdolData()` with dynamic DOL builder
6. ✅ Add `TerminalDecision` enum
7. ✅ Modify `executePhase5_GenerateAc()` with P1 calculation
8. ✅ Add CDOL2 processing logic

### **Week 3 Actions**:
9. ✅ Create `CvmProcessor.kt`
10. ✅ Add security analysis UI
11. ✅ Write unit tests (50+ cases)
12. ✅ Test with 2+ real contactless cards

---

## 💡 PRO TIPS

1. **Start Small**: Begin with transaction type selection UI, then backend logic
2. **Test Incrementally**: After each phase, test with real NFC cards
3. **Log Everything**: Enhanced logging is critical for debugging APDU flows
4. **Reference Proxmark3**: When stuck, check `cmdemv.c` implementation
5. **Security First**: Ensure CDOL/PDOL data matches EMV spec exactly
6. **User Experience**: Keep UI simple - most users want "quick scan" default

---

**GAME PLAN STATUS**: ✅ COMPLETE - Ready for Phase 1 Implementation

**Estimated Total Implementation**: 6-8 weeks (Critical + High priority items)

**Risk Assessment**: 🟢 LOW - All critical features have clear Proxmark3 reference implementations
