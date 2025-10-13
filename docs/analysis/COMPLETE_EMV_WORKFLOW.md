# Complete EMV Card Reading Workflow
## Universal Data Extraction for All Card Types

**Version:** 2.0 (October 11, 2025)  
**Target:** ALL EMV cards (VISA, Mastercard, AMEX, Discover, JCB, etc.)  
**Goal:** Extract 100% of available data regardless of card vendor  

---

## 🎯 Complete 10-Phase Workflow

This workflow extracts **ALL possible data** from any EMV contactless card:

```
Phase 1:  SELECT PPSE (2PAY.SYS.DDF01)
Phase 2:  SELECT AID (from PPSE response)
Phase 3:  GET PROCESSING OPTIONS (with dynamic PDOL)
Phase 4A: READ AFL RECORDS (Application File Locator - exact records)
Phase 4B: EXTENDED RECORD SCAN (SFI 1-31, Records 1-16 for missing tags)
Phase 4C: INTERNAL AUTHENTICATE (DDA/CDA if supported)
Phase 5:  GENERATE AC (ARQC generation with CDOL1)
Phase 6:  GET DATA PRIMITIVES (12 critical tags)
Phase 7:  TRANSACTION LOG READING (if 9F4F present)
Phase 8:  GET CHALLENGE (for DDA cards)
Phase 9:  ADDITIONAL GET DATA (vendor-specific tags)
Phase 10: PROPRIETARY COMMANDS (card-specific extensions)
```

---

## 📋 Phase-by-Phase Breakdown

### **Phase 1: SELECT PPSE**

**Purpose:** Discover available payment applications on the card

**APDU Command:**
```
CLA:  00
INS:  A4 (SELECT)
P1:   04 (Select by name)
P2:   00
Data: 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31  ("2PAY.SYS.DDF01")
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun selectPpse(): Boolean {
    val ppseAid = "325041592E5359532E4444463031"  // "2PAY.SYS.DDF01"
    val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E) + 
               ppseAid.hexToByteArray() + 
               byteArrayOf(0x00)
    
    val response = sendApdu(apdu)
    if (response.statusWord != "9000") {
        Timber.w("SELECT PPSE failed: ${response.statusWord}")
        return false
    }
    
    // Parse FCI template (6F)
    val tags = EmvTlvParser.parseResponse(response.data)
    parsedEmvFields.putAll(tags.tags)
    
    // Extract AIDs from template
    discoveredAids = extractAidsFromPpse(tags)
    Timber.i("✅ PPSE selected, found ${discoveredAids.size} AIDs")
    return true
}
```

**Expected Response Tags:**
- **6F** - FCI Template
- **84** - DF Name (PPSE)
- **A5** - FCI Proprietary Template
- **BF0C** - FCI Issuer Discretionary Data
- **61** - Application Template (one per AID)
- **4F** - AID (Application Identifier)
- **50** - Application Label
- **87** - Application Priority Indicator
- **9F38** - PDOL (Processing Data Object List)

---

### **Phase 2: SELECT AID**

**Purpose:** Select specific payment application

**APDU Command:**
```
CLA:  00
INS:  A4 (SELECT)
P1:   04 (Select by name)
P2:   00
Data: <AID bytes> (e.g., A0000000031010 for VISA)
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun selectAid(aid: String): Boolean {
    val aidBytes = aid.hexToByteArray()
    val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + 
               aidBytes + 
               byteArrayOf(0x00)
    
    val response = sendApdu(apdu)
    if (response.statusWord != "9000") {
        Timber.w("SELECT AID failed: ${response.statusWord}")
        return false
    }
    
    // Parse FCI template
    val tags = EmvTlvParser.parseResponse(response.data)
    parsedEmvFields.putAll(tags.tags)
    
    // Extract critical tags
    pdolTemplate = tags.tags["9F38"]?.value  // PDOL
    cdol1Template = tags.tags["8C"]?.value   // CDOL1 (may be here or in records)
    applicationLabel = tags.tags["50"]?.value?.hexToAscii()
    
    Timber.i("✅ AID selected: $applicationLabel")
    return true
}
```

**Expected Response Tags:**
- **6F** - FCI Template
- **84** - DF Name (AID)
- **A5** - FCI Proprietary Template
- **50** - Application Label
- **87** - Application Priority Indicator
- **9F38** - PDOL (Processing Data Object List)
- **5F2D** - Language Preference
- **9F11** - Issuer Code Table Index
- **9F12** - Application Preferred Name
- **BF0C** - FCI Issuer Discretionary Data

---

### **Phase 3: GET PROCESSING OPTIONS**

**Purpose:** Initiate transaction and get AIP + AFL

**PDOL Building:**
```kotlin
fun buildPdolData(pdolTags: List<String>): ByteArray {
    val transVals = mutableMapOf(
        "9F37" to getUnpredictableNumber(),      // UN: 4 bytes random
        "9A" to getCurrentDate(),                 // Date: YYMMDD
        "9C" to "00",                             // Transaction Type: 0x00
        "9F02" to "000000000001",                 // Amount: 0.01
        "9F03" to "000000000000",                 // Other Amount: 0
        "9F1A" to "0840",                         // Terminal Country Code: 0840 (USA)
        "5F2A" to "0840",                         // Currency Code: 0840 (USD)
        "95" to "0000000000",                     // TVR: 5 bytes zero
        "9F66" to "80000000",                     // TTQ: 4 bytes (MSD for VISA)
        "9F10" to "0000000000000000000000000000000000", // Issuer Application Data
        "9F36" to "0000"                          // ATC: 2 bytes
    )
    
    return buildDataFromDol(pdolTags, transVals)
}

fun getUnpredictableNumber(): String {
    return SecureRandom().nextInt().toString(16).padStart(8, '0')
}

fun getCurrentDate(): String {
    val sdf = SimpleDateFormat("yyMMdd", Locale.US)
    return sdf.format(Date()).hexEncode()
}
```

**APDU Command:**
```
CLA:  80
INS:  A8 (GET PROCESSING OPTIONS)
P1:   00
P2:   00
Data: 83 <length> <PDOL data>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun getProcessingOptions(): Boolean {
    val pdolData = if (pdolTemplate != null) {
        buildPdolData(EmvTlvParser.parseDol(pdolTemplate!!.hexToByteArray()))
    } else {
        byteArrayOf()  // Empty PDOL
    }
    
    val commandData = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
    val apdu = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, commandData.size.toByte()) + 
               commandData + 
               byteArrayOf(0x00)
    
    val response = sendApdu(apdu)
    if (response.statusWord != "9000") {
        Timber.w("GPO failed: ${response.statusWord}")
        return false
    }
    
    // Parse response (format 1: 80 or format 2: 77)
    val tags = EmvTlvParser.parseResponse(response.data)
    parsedEmvFields.putAll(tags.tags)
    
    // Extract AIP and AFL
    aip = tags.tags["82"]?.value ?: run {
        // Format 1: 80 <length> <2-byte AIP> <AFL bytes>
        val data = response.data
        if (data[0].toInt() and 0xFF == 0x80) {
            aip = data.copyOfRange(2, 4).toHexString()
            afl = data.copyOfRange(4, data.size).toHexString()
        }
        aip
    }
    
    afl = tags.tags["94"]?.value ?: afl
    
    // Parse AIP for capabilities
    val aipBytes = aip?.hexToByteArray()
    if (aipBytes != null && aipBytes.size >= 2) {
        supportsSDA = (aipBytes[0].toInt() and 0x40) != 0  // Bit 7 of byte 1
        supportsDDA = (aipBytes[0].toInt() and 0x20) != 0  // Bit 6 of byte 1
        supportsCDA = (aipBytes[0].toInt() and 0x01) != 0  // Bit 1 of byte 1
        supportsIssuerAuth = (aipBytes[1].toInt() and 0x04) != 0  // Bit 3 of byte 2
    }
    
    Timber.i("✅ GPO complete - AIP: $aip, AFL: $afl")
    Timber.i("Card capabilities - SDA:$supportsSDA, DDA:$supportsDDA, CDA:$supportsCDA")
    return true
}
```

**Expected Response Tags:**
- **80** - Response Message Template Format 1 (AIP + AFL raw)
  - **OR**
- **77** - Response Message Template Format 2 (TLV)
  - **82** - AIP (Application Interchange Profile)
  - **94** - AFL (Application File Locator)
  - **57** - Track 2 Equivalent Data (sometimes)
  - **5A** - PAN (sometimes)
  - **9F10** - Issuer Application Data (sometimes)

---

### **Phase 4A: READ AFL RECORDS**

**Purpose:** Read records specified in AFL

**AFL Format:** 4 bytes per entry
- Byte 1: SFI (bits 7-3) + 00 (bits 2-0)
- Byte 2: First record number
- Byte 3: Last record number
- Byte 4: Number of records for offline authentication

**APDU Command:**
```
CLA:  00
INS:  B2 (READ RECORD)
P1:   <record number>
P2:   <(SFI << 3) | 0x04>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun readAflRecords(): Int {
    var recordsRead = 0
    val aflBytes = afl?.hexToByteArray() ?: return 0
    
    if (aflBytes.size % 4 != 0) {
        Timber.w("Invalid AFL length: ${aflBytes.size}")
        return 0
    }
    
    // Parse AFL entries
    for (i in aflBytes.indices step 4) {
        val sfi = (aflBytes[i].toInt() and 0xFF) shr 3
        val startRecord = aflBytes[i + 1].toInt() and 0xFF
        val endRecord = aflBytes[i + 2].toInt() and 0xFF
        val offlineRecords = aflBytes[i + 3].toInt() and 0xFF
        
        Timber.i("AFL Entry: SFI=$sfi, Records $startRecord-$endRecord, Offline=$offlineRecords")
        
        // Read each record in range
        for (record in startRecord..endRecord) {
            val p2 = ((sfi shl 3) or 0x04).toByte()
            val apdu = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2, 0x00)
            
            val response = sendApdu(apdu)
            if (response.statusWord == "9000") {
                val tags = EmvTlvParser.parseResponse(response.data)
                parsedEmvFields.putAll(tags.tags)
                recordsRead++
                Timber.i("✅ Read SFI $sfi Record $record - ${tags.tags.size} tags")
            } else {
                Timber.w("Failed to read SFI $sfi Record $record: ${response.statusWord}")
            }
        }
    }
    
    return recordsRead
}
```

**Expected Tags in Records:**
- **70** - Data Template
- **5A** - PAN (Application Primary Account Number)
- **57** - Track 2 Equivalent Data
- **5F20** - Cardholder Name
- **5F24** - Application Expiration Date
- **5F25** - Application Effective Date
- **5F28** - Issuer Country Code
- **5F30** - Service Code
- **8C** - CDOL1 (Card Risk Management Data Object List 1)
- **8D** - CDOL2 (Card Risk Management Data Object List 2)
- **8E** - CVM List (Cardholder Verification Method List)
- **8F** - Certification Authority Public Key Index
- **90** - Issuer Public Key Certificate
- **92** - Issuer Public Key Remainder
- **93** - Signed Static Application Data
- **9F07** - Application Usage Control
- **9F08** - Application Version Number
- **9F0D** - IAC Default
- **9F0E** - IAC Denial
- **9F0F** - IAC Online
- **9F32** - Issuer Public Key Exponent
- **9F42** - Application Currency Code
- **9F44** - Application Currency Exponent
- **9F46** - ICC Public Key Certificate
- **9F47** - ICC Public Key Exponent
- **9F48** - ICC Public Key Remainder
- **9F49** - DDOL (Dynamic Data Object List)
- **9F4A** - SDA Tag List

---

### **Phase 4B: EXTENDED RECORD SCAN**

**Purpose:** Find missing critical tags not in AFL

**Target Tags:**
- **8C** - CDOL1 (required for GENERATE AC)
- **8D** - CDOL2
- **8E** - CVM List (required for CVM attacks)
- **8F** - CA Public Key Index (required for ROCA)
- **9F32** - Issuer Public Key Exponent (required for ROCA)
- **9F47** - ICC Public Key Exponent (required for ROCA)
- **9F49** - DDOL (required for INTERNAL AUTHENTICATE)

**Kotlin Implementation:**
```kotlin
suspend fun extendedRecordScan(): Int {
    val criticalTags = listOf("8C", "8D", "8E", "8F", "9F32", "9F47", "9F49")
    val missingTags = criticalTags.filter { !parsedEmvFields.containsKey(it) }
    
    if (missingTags.isEmpty()) {
        Timber.i("All critical tags present, skipping extended scan")
        return 0
    }
    
    Timber.i("⚠️ Missing critical tags: $missingTags - starting extended scan")
    var tagsFound = 0
    
    // Scan SFI 1-31, Records 1-16
    for (sfi in 1..31) {
        for (record in 1..16) {
            val p2 = ((sfi shl 3) or 0x04).toByte()
            val apdu = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2, 0x00)
            
            val response = sendApdu(apdu)
            if (response.statusWord == "9000") {
                val tags = EmvTlvParser.parseResponse(response.data)
                
                // Check if we found any missing critical tags
                val foundCritical = tags.tags.keys.any { it in missingTags }
                if (foundCritical) {
                    parsedEmvFields.putAll(tags.tags)
                    tagsFound += tags.tags.keys.count { it in missingTags }
                    Timber.i("✅ EXTENDED: Found critical tags in SFI $sfi Record $record")
                }
            }
            
            // Stop if we found all missing tags
            if (missingTags.all { parsedEmvFields.containsKey(it) }) {
                Timber.i("✅ All critical tags found, stopping extended scan")
                return tagsFound
            }
        }
    }
    
    return tagsFound
}
```

---

### **Phase 4C: INTERNAL AUTHENTICATE (DDA/CDA)**

**Purpose:** Get dynamic card signature (SDAD) for DDA/CDA cards

**Prerequisites:**
1. Card supports DDA or CDA (from AIP bit 6 or bit 1)
2. DDOL (9F49) present in card records
3. Challenge obtained via GET CHALLENGE

**Step 1: GET CHALLENGE**
```
CLA:  00
INS:  84 (GET CHALLENGE)
P1:   00
P2:   00
Le:   08 (request 8 bytes)
```

**Step 2: Build DDOL Data**
```kotlin
fun buildDdolData(ddol: String, challenge: ByteArray): ByteArray {
    val ddolTags = EmvTlvParser.parseDol(ddol.hexToByteArray())
    
    val transVals = mutableMapOf(
        "9F37" to challenge.toHexString(),       // UN: Use challenge
        "9A" to getCurrentDate(),                 // Date: YYMMDD
        "9C" to "00",                             // Transaction Type
        "9F02" to "000000000001",                 // Amount
        "9F03" to "000000000000",                 // Other Amount
        "9F1A" to "0840",                         // Country Code
        "5F2A" to "0840",                         // Currency Code
        "95" to "0000000000",                     // TVR
        "9F36" to parsedEmvFields["9F36"]?.value ?: "0000"  // ATC
    )
    
    return buildDataFromDol(ddolTags, transVals)
}
```

**Step 3: INTERNAL AUTHENTICATE**
```
CLA:  00
INS:  88 (INTERNAL AUTHENTICATE)
P1:   00
P2:   00
Data: <DDOL data>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun performDdaAuthentication(): Boolean {
    // Check if DDA/CDA supported
    if (!supportsDDA && !supportsCDA) {
        Timber.i("Card does not support DDA/CDA, skipping")
        return false
    }
    
    // Check if DDOL present
    val ddol = parsedEmvFields["9F49"]?.value
    if (ddol == null) {
        Timber.w("DDOL (9F49) not found, cannot perform DDA")
        return false
    }
    
    // Step 1: GET CHALLENGE
    val challengeApdu = byteArrayOf(0x00, 0x84.toByte(), 0x00, 0x00, 0x08)
    val challengeResponse = sendApdu(challengeApdu)
    if (challengeResponse.statusWord != "9000") {
        Timber.w("GET CHALLENGE failed: ${challengeResponse.statusWord}")
        return false
    }
    val challenge = challengeResponse.data
    Timber.i("Challenge: ${challenge.toHexString()}")
    
    // Step 2: Build DDOL data
    val ddolData = buildDdolData(ddol, challenge)
    
    // Step 3: INTERNAL AUTHENTICATE
    val intAuthApdu = byteArrayOf(
        0x00, 0x88.toByte(), 0x00, 0x00,
        ddolData.size.toByte()
    ) + ddolData + byteArrayOf(0x00)
    
    val sdadResponse = sendApdu(intAuthApdu)
    if (sdadResponse.statusWord != "9000") {
        Timber.w("INTERNAL AUTHENTICATE failed: ${sdadResponse.statusWord}")
        return false
    }
    
    // Parse SDAD (Signed Dynamic Application Data)
    val sdadTags = EmvTlvParser.parseResponse(sdadResponse.data)
    parsedEmvFields.putAll(sdadTags.tags)
    
    Timber.i("✅ DDA/CDA authentication successful")
    Timber.i("SDAD tags: ${sdadTags.tags.keys.joinToString()}")
    return true
}
```

**Expected Response Tags:**
- **80** - Response Message Template Format 1
- **9F4B** - Signed Dynamic Application Data (SDAD)

---

### **Phase 5: GENERATE AC**

**Purpose:** Generate ARQC (Authorization Request Cryptogram)

**Prerequisites:**
- CDOL1 (8C) present (from records or extended scan)
- Transaction parameters prepared

**APDU Command:**
```
CLA:  80
INS:  AE (GENERATE AC)
P1:   80 (ARQC request) / 40 (TC request) / 00 (AAC request)
P2:   00
Data: <CDOL1 data>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun generateAc(): Boolean {
    val cdol1 = extractCdol1FromAllResponses()
    if (cdol1 == null) {
        Timber.w("CDOL1 not found, skipping GENERATE AC")
        return false
    }
    
    // Build CDOL1 data
    val cdol1Data = buildCdolData(cdol1)
    
    // P1: 0x80 = ARQC (go online), 0x40 = TC (approve offline), 0x00 = AAC (decline)
    val apdu = byteArrayOf(
        0x80.toByte(), 0xAE.toByte(), 0x80.toByte(), 0x00,
        cdol1Data.size.toByte()
    ) + cdol1Data + byteArrayOf(0x00)
    
    val response = sendApdu(apdu)
    if (response.statusWord == "9000") {
        val tags = EmvTlvParser.parseResponse(response.data)
        parsedEmvFields.putAll(tags.tags)
        
        val arqc = tags.tags["9F26"]?.value
        val cid = tags.tags["9F27"]?.value
        val atc = tags.tags["9F36"]?.value
        
        Timber.i("✅ GENERATE AC successful")
        Timber.i("ARQC: $arqc, CID: $cid, ATC: $atc")
        return true
    } else {
        Timber.w("GENERATE AC failed: ${response.statusWord}")
        return false
    }
}

fun buildCdolData(cdol1: String): ByteArray {
    val cdolTags = EmvTlvParser.parseDol(cdol1.hexToByteArray())
    
    val transVals = mutableMapOf(
        "9F37" to getUnpredictableNumber(),
        "9A" to getCurrentDate(),
        "9C" to "00",
        "5F2A" to "0840",
        "9F02" to "000000000001",
        "9F03" to "000000000000",
        "9F1A" to "0840",
        "95" to "0000000000",
        "9F66" to "80000000",
        "9F10" to "0000000000000000000000000000000000",
        "9F36" to parsedEmvFields["9F36"]?.value ?: "0000"
    )
    
    return buildDataFromDol(cdolTags, transVals)
}
```

**Expected Response Tags:**
- **77** - Response Message Template Format 2
- **9F27** - CID (Cryptogram Information Data)
- **9F26** - Application Cryptogram (ARQC/TC/AAC)
- **9F36** - ATC (Application Transaction Counter)
- **9F10** - Issuer Application Data

---

### **Phase 6: GET DATA PRIMITIVES**

**Purpose:** Query individual tags via GET DATA command

**Target Tags (12 critical):**
1. **9F17** - PIN Try Counter
2. **9F36** - ATC (Application Transaction Counter)
3. **9F13** - Last Online ATC Register
4. **9F4F** - Log Format
5. **9F6E** - Form Factor Indicator
6. **DF60** - CVC3 Track1
7. **DF61** - CVC3 Track2
8. **9F14** - Lower Consecutive Offline Limit
9. **9F23** - Upper Consecutive Offline Limit
10. **9F17** - Personal Identification Number (PIN) Try Counter
11. **9F63** - PUNATC (Track1)
12. **9F64** - NATC (Track1)

**APDU Command:**
```
CLA:  80
INS:  CA (GET DATA)
P1:   <tag byte 1>
P2:   <tag byte 2>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun getDataPrimitives(): Int {
    val tagsToQuery = listOf(
        "9F17", "9F36", "9F13", "9F4F", "9F6E", "DF60", "DF61",
        "9F14", "9F23", "9F63", "9F64", "9F67"
    )
    
    var successCount = 0
    
    for (tagStr in tagsToQuery) {
        val tagBytes = tagStr.hexToByteArray()
        val apdu = byteArrayOf(
            0x80.toByte(), 0xCA.toByte(),
            tagBytes[0], tagBytes[1],
            0x00
        )
        
        val response = sendApdu(apdu)
        if (response.statusWord == "9000") {
            val tags = EmvTlvParser.parseResponse(response.data)
            parsedEmvFields.putAll(tags.tags)
            successCount++
            Timber.i("✅ GET DATA $tagStr successful")
        } else if (response.statusWord != "6A88") {  // 6A88 = Data not found (expected)
            Timber.d("GET DATA $tagStr: ${response.statusWord}")
        }
    }
    
    Timber.i("✅ GET DATA complete: $successCount/${tagsToQuery.size} tags retrieved")
    return successCount
}
```

---

### **Phase 7: TRANSACTION LOG READING**

**Purpose:** Read transaction history from card

**Prerequisites:**
- Log Format (9F4F) present from GET DATA

**APDU Command:**
```
CLA:  80
INS:  CA (GET DATA)
P1:   9F
P2:   4D (for log record)
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun readTransactionLogs(): Int {
    val logFormat = parsedEmvFields["9F4F"]?.value
    if (logFormat == null) {
        Timber.i("Log Format (9F4F) not available, skipping transaction logs")
        return 0
    }
    
    Timber.i("Log Format: $logFormat")
    var logsRead = 0
    
    // Try reading up to 30 logs (typical max)
    for (logIndex in 1..30) {
        val apdu = byteArrayOf(
            0x80.toByte(), 0xCA.toByte(), 0x9F, 0x4D.toByte(), 0x00
        )
        
        val response = sendApdu(apdu)
        if (response.statusWord == "9000") {
            val tags = EmvTlvParser.parseResponse(response.data)
            parsedEmvFields.putAll(tags.tags)
            logsRead++
            Timber.i("✅ Transaction Log $logIndex read")
        } else if (response.statusWord == "6A83" || response.statusWord == "6A82") {
            // 6A83 = Record not found, 6A82 = File not found
            Timber.i("No more transaction logs available")
            break
        } else {
            Timber.w("Transaction log $logIndex failed: ${response.statusWord}")
            break
        }
    }
    
    Timber.i("✅ Read $logsRead transaction logs")
    return logsRead
}
```

**Expected Tags:**
- **9F4D** - Log Entry

---

### **Phase 8: GET CHALLENGE (Alternative)**

**Purpose:** Get random challenge for DDA (if not done in Phase 4C)

**APDU Command:**
```
CLA:  00
INS:  84 (GET CHALLENGE)
P1:   00
P2:   00
Le:   04 or 08 (4 or 8 bytes)
```

**Kotlin Implementation:**
```kotlin
suspend fun getChallenge(length: Int = 8): ByteArray? {
    val apdu = byteArrayOf(0x00, 0x84.toByte(), 0x00, 0x00, length.toByte())
    
    val response = sendApdu(apdu)
    if (response.statusWord != "9000") {
        Timber.w("GET CHALLENGE failed: ${response.statusWord}")
        return null
    }
    
    Timber.i("✅ Challenge: ${response.data.toHexString()}")
    return response.data
}
```

---

### **Phase 9: ADDITIONAL GET DATA (Vendor-Specific)**

**Purpose:** Query vendor-specific proprietary tags

**VISA-Specific Tags:**
- **9F63** - PUNATC (Track1) - Plaintext/Enciphered UN and ATC
- **9F64** - NATC (Track1) - Number of ATC
- **9F66** - TTQ (Terminal Transaction Qualifiers)
- **9F67** - NATC (Track2)
- **9F6C** - CTQ (Card Transaction Qualifiers)

**Mastercard-Specific Tags:**
- **9F6D** - Mag Stripe Application Version Number (Card)
- **9F6E** - Form Factor Indicator
- **9F70** - Protected Data Envelope 1
- **9F71** - Protected Data Envelope 2

**American Express-Specific Tags:**
- **9F5D** - Available Offline Spending Amount
- **DF20** - AMEX Proprietary

**Kotlin Implementation:**
```kotlin
suspend fun additionalGetData(): Int {
    // Determine card vendor from AID
    val aid = parsedEmvFields["4F"]?.value ?: return 0
    val vendor = determineVendor(aid)
    
    val vendorTags = when (vendor) {
        "VISA" -> listOf("9F63", "9F64", "9F66", "9F67", "9F6C")
        "MASTERCARD" -> listOf("9F6D", "9F6E", "9F70", "9F71")
        "AMEX" -> listOf("9F5D", "DF20")
        else -> emptyList()
    }
    
    var successCount = 0
    for (tagStr in vendorTags) {
        val tagBytes = tagStr.hexToByteArray()
        val apdu = byteArrayOf(
            0x80.toByte(), 0xCA.toByte(),
            tagBytes[0], tagBytes[1],
            0x00
        )
        
        val response = sendApdu(apdu)
        if (response.statusWord == "9000") {
            val tags = EmvTlvParser.parseResponse(response.data)
            parsedEmvFields.putAll(tags.tags)
            successCount++
        }
    }
    
    Timber.i("✅ Vendor-specific GET DATA: $successCount tags retrieved")
    return successCount
}

fun determineVendor(aid: String): String {
    return when {
        aid.startsWith("A0000000031010") -> "VISA"
        aid.startsWith("A0000000041010") -> "MASTERCARD"
        aid.startsWith("A000000025") -> "AMEX"
        aid.startsWith("A0000000651010") -> "JCB"
        aid.startsWith("A0000001523010") -> "DISCOVER"
        else -> "UNKNOWN"
    }
}
```

---

### **Phase 10: PROPRIETARY COMMANDS (Card-Specific)**

**Purpose:** Execute card-specific proprietary commands

#### **10A: VISA COMPUTE CRYPTOGRAPHIC CHECKSUM (Dynamic CVV)**

**APDU Command:**
```
CLA:  80
INS:  2A (COMPUTE CRYPTOGRAPHIC CHECKSUM)
P1:   8E
P2:   80
Data: <4-byte Unpredictable Number>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun visaComputeChecksum(): Boolean {
    val vendor = determineVendor(parsedEmvFields["4F"]?.value ?: "")
    if (vendor != "VISA") {
        Timber.i("Not a VISA card, skipping COMPUTE CHECKSUM")
        return false
    }
    
    val un = getUnpredictableNumber().hexToByteArray()
    val apdu = byteArrayOf(
        0x80.toByte(), 0x2A.toByte(), 0x8E.toByte(), 0x80.toByte(),
        0x04  // 4 bytes UN
    ) + un + byteArrayOf(0x00)
    
    val response = sendApdu(apdu)
    if (response.statusWord == "9000") {
        val dCVV = response.data.toHexString()
        parsedEmvFields["dCVV"] = EnrichedTagData(
            tag = "dCVV",
            value = dCVV,
            name = "Dynamic CVV (VISA)",
            description = "VISA payWave dynamic CVV"
        )
        Timber.i("✅ VISA COMPUTE CHECKSUM successful: $dCVV")
        return true
    } else {
        Timber.w("VISA COMPUTE CHECKSUM failed: ${response.statusWord}")
        return false
    }
}
```

---

#### **10B: Mastercard CCC (Compute Cryptographic Checksum)**

**APDU Command:**
```
CLA:  80
INS:  2A (COMPUTE CRYPTOGRAPHIC CHECKSUM)
P1:   8E
P2:   80
Data: <UDOL data>
Le:   00
```

**Kotlin Implementation:**
```kotlin
suspend fun mastercardComputeChecksum(): Boolean {
    val vendor = determineVendor(parsedEmvFields["4F"]?.value ?: "")
    if (vendor != "MASTERCARD") {
        Timber.i("Not a Mastercard, skipping CCC")
        return false
    }
    
    // Extract UDOL (if present)
    val udol = parsedEmvFields["9F69"]?.value
    val udolData = if (udol != null) {
        buildUdolData(udol)
    } else {
        byteArrayOf()  // Empty UDOL
    }
    
    val apdu = byteArrayOf(
        0x80.toByte(), 0x2A.toByte(), 0x8E.toByte(), 0x80.toByte(),
        udolData.size.toByte()
    ) + udolData + byteArrayOf(0x00)
    
    val response = sendApdu(apdu)
    if (response.statusWord == "9000") {
        Timber.i("✅ Mastercard CCC successful")
        return true
    } else {
        Timber.w("Mastercard CCC failed: ${response.statusWord}")
        return false
    }
}
```

---

## 🎯 Complete Workflow Implementation

```kotlin
suspend fun performCompleteEmvScan() {
    Timber.i("=== Starting Complete EMV Scan ===")
    scanStartTime = System.currentTimeMillis()
    
    try {
        // Phase 1: SELECT PPSE
        updateProgress(0.05f, "Phase 1: SELECT PPSE")
        if (!selectPpse()) {
            throw Exception("SELECT PPSE failed")
        }
        
        // Phase 2: SELECT AID
        updateProgress(0.10f, "Phase 2: SELECT AID")
        val aid = discoveredAids.firstOrNull() ?: throw Exception("No AID found")
        if (!selectAid(aid)) {
            throw Exception("SELECT AID failed")
        }
        
        // Phase 3: GET PROCESSING OPTIONS
        updateProgress(0.20f, "Phase 3: GET PROCESSING OPTIONS")
        if (!getProcessingOptions()) {
            throw Exception("GPO failed")
        }
        
        // Phase 4A: READ AFL RECORDS
        updateProgress(0.30f, "Phase 4A: READ AFL RECORDS")
        val aflRecords = readAflRecords()
        Timber.i("Read $aflRecords AFL records")
        
        // Phase 4B: EXTENDED RECORD SCAN
        updateProgress(0.45f, "Phase 4B: EXTENDED RECORD SCAN")
        val extendedTags = extendedRecordScan()
        Timber.i("Extended scan found $extendedTags additional tags")
        
        // Phase 4C: INTERNAL AUTHENTICATE (DDA/CDA)
        updateProgress(0.55f, "Phase 4C: INTERNAL AUTHENTICATE")
        if (supportsDDA || supportsCDA) {
            performDdaAuthentication()
        }
        
        // Phase 5: GENERATE AC
        updateProgress(0.65f, "Phase 5: GENERATE AC")
        generateAc()
        
        // Phase 6: GET DATA PRIMITIVES
        updateProgress(0.75f, "Phase 6: GET DATA PRIMITIVES")
        val getDataTags = getDataPrimitives()
        Timber.i("GET DATA retrieved $getDataTags tags")
        
        // Phase 7: TRANSACTION LOG READING
        updateProgress(0.85f, "Phase 7: TRANSACTION LOG READING")
        val logs = readTransactionLogs()
        Timber.i("Read $logs transaction logs")
        
        // Phase 8: GET CHALLENGE (if not done)
        if (!supportsDDA && !supportsCDA) {
            updateProgress(0.88f, "Phase 8: GET CHALLENGE")
            getChallenge(8)
        }
        
        // Phase 9: ADDITIONAL GET DATA (Vendor-Specific)
        updateProgress(0.92f, "Phase 9: ADDITIONAL GET DATA")
        additionalGetData()
        
        // Phase 10: PROPRIETARY COMMANDS
        updateProgress(0.96f, "Phase 10: PROPRIETARY COMMANDS")
        val vendor = determineVendor(parsedEmvFields["4F"]?.value ?: "")
        when (vendor) {
            "VISA" -> visaComputeChecksum()
            "MASTERCARD" -> mastercardComputeChecksum()
        }
        
        // Complete
        updateProgress(1.0f, "Scan Complete")
        val duration = System.currentTimeMillis() - scanStartTime
        Timber.i("=== Complete EMV Scan Finished in ${duration}ms ===")
        Timber.i("Total tags collected: ${parsedEmvFields.size}")
        
        // Save to database
        saveToDatabase()
        
    } catch (e: Exception) {
        Timber.e(e, "EMV Scan failed")
        scanState = ScanState.ERROR
        statusMessage = "Scan failed: ${e.message}"
    }
}
```

---

## 📊 Expected Data Collection

### **Minimum Tags (Basic Card):**
- 30-40 tags
- PAN, Track2, Expiry, Cardholder Name
- Basic attack coverage (Track2 manipulation)

### **Standard Tags (Most Cards):**
- 40-60 tags
- All basic tags + CVM List, CDOL1, CDOL2
- Good attack coverage (CVM Bypass, Amount Modification)

### **Comprehensive Tags (Modern Cards with DDA/CDA):**
- 60-80+ tags
- All standard tags + Certificates, SDAD, ARQC, Transaction Logs
- Excellent attack coverage (ROCA, DDA Bypass, Dynamic CVV)

---

## 🎯 Attack Coverage by Phase

| Attack Type | Required Phases | Tags Needed |
|-------------|----------------|-------------|
| **Track2 Manipulation** | 1-4A | 57 (Track2) |
| **PAN Modification** | 1-4A | 5A (PAN), 57 (Track2) |
| **CVM Bypass** | 1-4B | 8E (CVM List) |
| **Amount Modification** | 1-5 | 8C (CDOL1), 9F26 (ARQC) |
| **ARQC Replay** | 1-5 | 9F26 (ARQC), 9F36 (ATC) |
| **AIP Modification** | 1-3 | 82 (AIP) |
| **ROCA Exploitation** | 1-4B | 90, 9F46, 8F, 9F32, 9F47 |
| **DDA/CDA Bypass** | 1-4C | 9F49 (DDOL), 9F4B (SDAD) |
| **Dynamic CVV Clone** | 1-10A | dCVV (VISA-specific) |
| **Transaction History** | 1-7 | 9F4F (Log Format), 9F4D (Logs) |

---

## ✅ Verification Checklist

After complete scan, verify:

1. ✅ **Basic Data (100% required):**
   - [ ] PAN (5A) or Track2 (57) present
   - [ ] Expiry Date (5F24) present
   - [ ] Application Label (50) present

2. ✅ **Certificate Data (ROCA analysis):**
   - [ ] Issuer Certificate (90) present
   - [ ] ICC Certificate (9F46) present
   - [ ] CA Index (8F) present
   - [ ] Issuer Exponent (9F32) present
   - [ ] ICC Exponent (9F47) present

3. ✅ **Attack Data (CVM/CDOL):**
   - [ ] CVM List (8E) present or confirmed absent
   - [ ] CDOL1 (8C) present or confirmed absent
   - [ ] CDOL2 (8D) checked

4. ✅ **Authentication Data (DDA/CDA):**
   - [ ] AIP (82) analyzed for capabilities
   - [ ] DDOL (9F49) checked if DDA/CDA supported
   - [ ] SDAD attempted if DDA/CDA supported

5. ✅ **Transaction Data:**
   - [ ] Log Format (9F4F) checked
   - [ ] Transaction logs attempted if 9F4F present
   - [ ] ATC (9F36) retrieved

---

**Generated:** October 11, 2025  
**Version:** 2.0 Complete Workflow  
**For:** nf-sp00f33r EMV Research Platform
