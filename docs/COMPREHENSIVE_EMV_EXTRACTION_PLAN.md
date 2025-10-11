# 🎯 COMPREHENSIVE EMV DATA EXTRACTION PLAN
## Merging RFIDIOt ChAP Scripts + Proxmark3 `emv scan`

**Date:** October 10, 2025  
**Target:** nf-sp00f33r CardReadingViewModel.kt  
**Goal:** Ultimate EMV research tool with complete data extraction

---

## 📊 ANALYSIS SUMMARY

### **RFIDIOt ChAP Scripts Workflow**

```python
# ChAP.py / ChAP-paywave.py / ChAP-paypass.py workflow:

1. SELECT PSE/PPSE (1PAY.SYS.DDF01 or 2PAY.SYS.DDF01)
2. Read PSD records to extract ALL AIDs
3. **FOR EACH AID in aidlist:** # ← PROCESSES ALL AIDs
   - SELECT AID
   - Extract PDOL (tag 9F38) from SELECT response
   - Build PDOL data from TRANS_VAL dictionary:
     * 9F02: Amount (0x00..0x01)
     * 9F03: Other Amount (0x00..0x00)
     * 9F1A: Country Code (0x0826 = US)
     * 95: TVR (0x00..0x00)
     * 5F2A: Currency Code (0x0826 = US)
     * 9A: Transaction Date (YYMMDD)
     * 9C: Transaction Type (0x01)
     * 9F37: Unpredictable Number (0xBA 0xDF 0x00 0x0D)
   - GET PROCESSING OPTIONS (GPO) with PDOL data
   - decode_aip(data[2:]) # Parse AIP byte 1 bitmask
   - decode_afl(data[4:]) # Parse AFL entries
   - **FOR EACH AFL entry (SFI, start, end):**
     - **FOR record in range(start, end+1):**
       - READ RECORD (SFI, record)
       - decode_pse(response) # TLV decode each record
   - **GET DATA primitives:**
     - get_primitive(PIN_TRY_COUNTER) # 9F17
     - get_primitive(ATC) # 9F36
     - get_primitive(LAST_ATC) # 9F13
   - Extract CDOL1 (tag 8C) from records
   - **GENERATE AC (ChAP-paywave/paypass):**
     - Build CDOL1 data from TRANS_VAL
     - APDU: 0x80 0xAE [P1] 0x00 [Lc] [CDOL1 data] [Le]
     - P1: 0x00=AAC, 0x40=TC, 0x80=ARQC
     - Parse response: cryptogram, CID, ATC
   - **INTERNAL AUTHENTICATE (ChAP-paywave VISA):**
     - Extract DDOL (tag 9F49) from records
     - Build DDOL data
     - APDU: 0x00 0x88 0x00 0x00 [Lc] [DDOL data] [Le]
     - Parse SDAD (Signed Dynamic Application Data)
   - **COMPUTE CRYPTOGRAPHIC CHECKSUM (ChAP-paypass MasterCard):**
     - APDU: 0x80 0x2A 0x8E 0x80 [Lc] [UN] [Le]
     - For MSR (Magnetic Stripe) mode

Key Features:
- ✅ Processes ALL AIDs (multi-AID loop)
- ✅ Has GENERATE AC command
- ✅ Has INTERNAL AUTHENTICATE (VISA DDA)
- ✅ Has CCC (MasterCard MSR)
- ✅ Hardcoded TRANS_VAL dictionary for PDOL/CDOL
```

---

### **Proxmark3 `emv scan` Workflow**

```c
// client/src/emv/cmdemv.c - CmdEMVScan() workflow:

1. SELECT PSE (2PAY.SYS.DDF01)
2. EMVSearchPSE(channel, ..., tlvSelect)
3. **EMVSelectApplication(tlvSelect, AID, &AIDlen)** # ← FIRST AID ONLY!
4. EMVSelect(channel, AID, AIDlen, buf, ..., tlvRoot)
5. InitTransactionParameters(tlvRoot, paramLoadJSON, TrType, GenACGPO)
   - Loads from emv_defparams.json if paramLoadJSON=true
6. **dol_process(tlvdb_get(tlvRoot, 0x9f38, NULL), tlvRoot, 0x83)**
   - Processes PDOL tag list intelligently
   - Builds PDOL data from tlvRoot values
7. EMVGPO(channel, pdol_data, ..., sw, tlvRoot)
8. ProcessGPOResponseFormat1(tlvRoot, buf, len, decodeTLV)
   - Parses format 1 (0x80) or format 2 (0x77)
   - Extracts AIP (tag 82) and AFL (tag 94)
9. **FOR i in range(AFL->len / 4):** # ← COMPLETE AFL PROCESSING
     - uint8_t SFI = AFL->value[i*4 + 0] >> 3;
     - uint8_t SFIstart = AFL->value[i*4 + 1];
     - uint8_t SFIend = AFL->value[i*4 + 2];
     - uint8_t SFIoffline = AFL->value[i*4 + 3];
     - **FOR n in range(SFIstart, SFIend+1):**
       - EMVReadRecord(channel, SFI, n, buf, ..., tlvRoot)
       - TLVPrintFromBuffer(buf, len) if decodeTLV
10. **GET DATA for extra tags:**
    ```c
    uint16_t extra_data[] = { 0x9F36, 0x9F13, 0x9F17, 0x9F4D, 0x9F4F };
    for (int i = 0; i < ARRAYLEN(extra_data); i++) {
        EMVGetData(channel, true, extra_data[i], buf, ...);
    }
    ```
11. **Transaction log reading:**
    ```c
    if (tag 9F4D present) {
        // Parse log format (9F4F) to get SFI and record count
        uint8_t log_file_id = log_format[0] >> 3;
        uint8_t log_file_records = log_format[1];
        for (int i = 1; i <= log_file_records; i++) {
            EMVReadRecord(channel, true, log_file_id, i, buf, ...);
            emv_parse_log(tlogDB, buf, len);
        }
    }
    ```
12. ROCA vulnerability test (roca_test())
13. JSON export (JsonSaveStr, JsonSaveBufAsHex, JsonSaveTLVTree)

Key Features:
- ⚠️ Processes FIRST AID only (EMVSelectApplication picks first)
- ✅ Complete AFL reading (ALL records from ALL SFIs)
- ✅ GET DATA primitives (9F36, 9F13, 9F17, 9F4D, 9F4F)
- ✅ Transaction log extraction
- ✅ ROCA vulnerability testing
- ✅ JSON export functionality
- ✅ dol_process() for intelligent PDOL/CDOL building
```

---

## 🔥 OPTIMAL MERGED STRATEGY

### **The Ultimate EMV Extraction Workflow**

```kotlin
// CardReadingViewModel.kt - Enhanced EMV scan combining both approaches:

suspend fun executeComprehensiveEmvScan() {
    appendLog("═══════════════════════════════════════")
    appendLog("COMPREHENSIVE EMV SCAN (ChAP + Proxmark3)")
    appendLog("═══════════════════════════════════════")
    
    // Phase 1: PPSE Selection (Proxmark3 approach)
    appendLog("\n[Phase 1] SELECT PPSE")
    val ppseApdu = buildSelectPpseApdu() // 2PAY.SYS.DDF01
    val ppseResponse = sendApdu(ppseApdu)
    if (!ppseResponse.isSuccess()) {
        appendLog("⚠️ PPSE not found, trying PSE")
        val pseApdu = buildSelectPseApdu() // 1PAY.SYS.DDF01
        val pseResponse = sendApdu(pseApdu)
        if (!pseResponse.isSuccess()) {
            appendLog("❌ No PSE/PPSE found")
            return
        }
    }
    
    // Phase 2: Extract ALL AIDs (ChAP approach)
    appendLog("\n[Phase 2] Extract ALL AIDs from PSE/PPSE")
    val aidList = extractAllAidsFromPse(ppseResponse)
    appendLog("✅ Found ${aidList.size} AID(s)")
    
    // Phase 3: Process EACH AID (ChAP multi-AID approach)
    for ((aidIndex, aidEntry) in aidList.withIndex()) {
        appendLog("\n" + "═".repeat(40))
        appendLog("🔍 PROCESSING AID #${aidIndex + 1}/${aidList.size}")
        appendLog("   AID: ${aidEntry.aid}")
        appendLog("   Label: ${aidEntry.label}")
        appendLog("═".repeat(40))
        
        // 3a. SELECT AID
        appendLog("\n[3a] SELECT AID #${aidIndex + 1}")
        val selectAidApdu = buildSelectAidApdu(aidEntry.aid)
        val selectResp = sendApdu(selectAidApdu)
        if (!selectResp.isSuccess()) {
            appendLog("❌ Failed to select AID #${aidIndex + 1}")
            continue
        }
        
        // 3b. Parse PDOL and build PDOL data (Proxmark3 dol_process approach)
        appendLog("\n[3b] Parse PDOL")
        val pdolTag = extractTag(selectResp, "9F38")
        val pdolData = if (pdolTag.isNotEmpty()) {
            buildPdolData(pdolTag) // Dynamic PDOL building
        } else {
            byteArrayOf(0x83, 0x00) // Empty PDOL
        }
        appendLog("   PDOL Data: ${pdolData.toHexString()}")
        
        // 3c. GET PROCESSING OPTIONS (GPO)
        appendLog("\n[3c] GET PROCESSING OPTIONS (GPO)")
        val gpoApdu = buildGpoApdu(pdolData)
        val gpoResp = sendApdu(gpoApdu)
        if (!gpoResp.isSuccess()) {
            appendLog("❌ GPO failed for AID #${aidIndex + 1}")
            continue
        }
        
        // 3d. Parse AIP (detect SDA/DDA/CDA capability)
        appendLog("\n[3d] Analyze Application Interchange Profile (AIP)")
        val aip = extractTag(gpoResp, "82")
        val securityInfo = analyzeAip(aip, aidIndex)
        appendLog("   AIP: $aip")
        appendLog("   Security: ${securityInfo.summary}")
        if (securityInfo.isWeak) {
            appendLog("   ⚠️ WARNING: WEAK CRYPTO DETECTED!")
        }
        
        // 3e. Parse AFL
        appendLog("\n[3e] Parse Application File Locator (AFL)")
        val afl = extractTag(gpoResp, "94")
        val aflEntries = parseAfl(afl)
        appendLog("   AFL Entries: ${aflEntries.size}")
        
        // 3f. Read ALL records from ALL SFIs (Proxmark3 complete approach)
        appendLog("\n[3f] Read ALL Records from ALL SFIs")
        for ((aflIdx, aflEntry) in aflEntries.withIndex()) {
            appendLog("   SFI 0x${aflEntry.sfi.toHexString()}: Records ${aflEntry.startRec}-${aflEntry.endRec}")
            for (recNum in aflEntry.startRec..aflEntry.endRec) {
                val readRecApdu = buildReadRecordApdu(aflEntry.sfi, recNum)
                val recResp = sendApdu(readRecApdu)
                if (recResp.isSuccess()) {
                    appendLog("      Record $recNum: ${recResp.size} bytes")
                    parseAndStoreEmvTags(recResp, aidIndex, aflEntry.sfi, recNum)
                } else {
                    appendLog("      ⚠️ Record $recNum failed")
                }
            }
        }
        
        // 3g. GET DATA primitives (Proxmark3)
        appendLog("\n[3g] GET DATA Primitives")
        val getDataTags = listOf(
            0x9F36 to "ATC (Application Transaction Counter)",
            0x9F13 to "Last Online ATC",
            0x9F17 to "PIN Try Counter",
            0x9F4D to "Log Entry",
            0x9F4F to "Log Format"
        )
        for ((tag, name) in getDataTags) {
            val getDataApdu = buildGetDataApdu(tag)
            val getDataResp = sendApdu(getDataApdu)
            if (getDataResp.isSuccess()) {
                appendLog("   ✅ $name: ${getDataResp.toHexString()}")
            } else {
                appendLog("   ❌ $name: Not available")
            }
        }
        
        // 3h. Transaction logs (Proxmark3)
        appendLog("\n[3h] Transaction Log Extraction")
        val logFormat = extractTag(allResponses, "9F4F")
        if (logFormat.isNotEmpty() && logFormat.length >= 2) {
            val logSfi = (logFormat[0].toInt() shr 3) and 0x1F
            val logRecords = logFormat[1].toInt() and 0xFF
            appendLog("   Log SFI: 0x${logSfi.toHexString()}, Records: $logRecords")
            for (logRec in 1..logRecords) {
                val logApdu = buildReadRecordApdu(logSfi, logRec)
                val logResp = sendApdu(logApdu)
                if (logResp.isSuccess()) {
                    appendLog("   Log #$logRec: ${logResp.toHexString()}")
                    parseTransactionLog(logResp, logRec)
                }
            }
        } else {
            appendLog("   No transaction logs available")
        }
        
        // 3i. Parse CDOL1 from records
        appendLog("\n[3i] Parse CDOL1")
        val cdol1 = extractCdol1FromAllResponses()
        if (cdol1.isNotEmpty()) {
            appendLog("   CDOL1: ${cdol1.toHexString()}")
        }
        
        // 3j. GENERATE AC (ChAP - research mode)
        if (researchModeEnabled && cdol1.isNotEmpty()) {
            appendLog("\n[3j] GENERATE AC (ARQC)")
            val cdol1Data = buildCdolData(cdol1)
            val genAcApdu = buildGenerateAcApdu(0x80, cdol1Data) // 0x80 = ARQC
            val genAcResp = sendApdu(genAcApdu)
            if (genAcResp.isSuccess()) {
                appendLog("   ✅ ARQC Response: ${genAcResp.toHexString()}")
                parseGenerateAcResponse(genAcResp, aidIndex)
            } else {
                appendLog("   ❌ GENERATE AC failed")
            }
        }
        
        // 3k. INTERNAL AUTHENTICATE (ChAP-paywave - VISA DDA)
        if (researchModeEnabled && securityInfo.hasDDA && isVisaCard(aidEntry.aid)) {
            appendLog("\n[3k] INTERNAL AUTHENTICATE (VISA DDA)")
            val ddol = extractTag(allResponses, "9F49")
            if (ddol.isNotEmpty()) {
                val ddolData = buildDdolData(ddol)
                val intAuthApdu = buildInternalAuthApdu(ddolData)
                val intAuthResp = sendApdu(intAuthApdu)
                if (intAuthResp.isSuccess()) {
                    appendLog("   ✅ SDAD: ${intAuthResp.toHexString()}")
                }
            }
        }
        
        // 3l. Security analysis
        appendLog("\n[3l] Security Analysis for AID #${aidIndex + 1}")
        appendLog("   Crypto Support:")
        appendLog("      SDA: ${if (securityInfo.hasSDA) "✅" else "❌"}")
        appendLog("      DDA: ${if (securityInfo.hasDDA) "✅" else "❌"}")
        appendLog("      CDA: ${if (securityInfo.hasCDA) "✅" else "❌"}")
        if (aidIndex > 0) {
            compareAipWithPrevious(securityInfo, previousAidSecurity)
        }
    }
    
    // Phase 4: Multi-AID Security Summary
    appendLog("\n" + "═".repeat(40))
    appendLog("📊 MULTI-AID SECURITY SUMMARY")
    appendLog("═".repeat(40))
    summarizeMultiAidSecurity(allAidSecurityInfo)
    
    appendLog("\n✅ COMPREHENSIVE EMV SCAN COMPLETE")
}
```

---

## 📦 DATA STRUCTURES

```kotlin
data class AidEntry(
    val aid: String,
    val label: String,
    val priority: Int
)

data class AflEntry(
    val sfi: Int,
    val startRec: Int,
    val endRec: Int,
    val offlineAuthRecords: Int
)

data class SecurityInfo(
    val aip: String,
    val hasSDA: Boolean,
    val hasDDA: Boolean,
    val hasCDA: Boolean,
    val isWeak: Boolean,
    val summary: String
)

data class TransactionLog(
    val index: Int,
    val amount: String,
    val date: String,
    val atc: String,
    val cvr: String
)
```

---

## 🔧 KEY FUNCTIONS TO IMPLEMENT

### 1. **Multi-AID Extraction**
```kotlin
private fun extractAllAidsFromPse(pseResponse: ByteArray): List<AidEntry> {
    val aidList = mutableListOf<AidEntry>()
    // Parse FCI template
    // Extract all 0x61 (application template) tags
    // For each template, extract 0x4F (AID), 0x50 (label), 0x87 (priority)
    return aidList
}
```

### 2. **AIP Analysis**
```kotlin
private fun analyzeAip(aipHex: String, aidIndex: Int): SecurityInfo {
    if (aipHex.length < 4) return SecurityInfo("", false, false, false, true, "Invalid AIP")
    
    val aipBytes = aipHex.hexToByteArray()
    val byte1 = aipBytes[0].toInt()
    
    val hasSDA = (byte1 and 0x40) != 0
    val hasDDA = (byte1 and 0x20) != 0
    val hasCDA = (byte1 and 0x01) != 0
    
    val isWeak = !hasSDA && !hasDDA && !hasCDA
    
    val summary = buildString {
        if (hasSDA) append("SDA ")
        if (hasDDA) append("DDA ")
        if (hasCDA) append("CDA ")
        if (isWeak) append("(NO CRYPTO)")
    }.trim()
    
    return SecurityInfo(aipHex, hasSDA, hasDDA, hasCDA, isWeak, summary)
}
```

### 3. **AFL Parsing**
```kotlin
private fun parseAfl(aflHex: String): List<AflEntry> {
    val afl = aflHex.hexToByteArray()
    val entries = mutableListOf<AflEntry>()
    
    for (i in afl.indices step 4) {
        if (i + 3 >= afl.size) break
        val sfi = (afl[i].toInt() shr 3) and 0x1F
        val startRec = afl[i + 1].toInt() and 0xFF
        val endRec = afl[i + 2].toInt() and 0xFF
        val offlineAuth = afl[i + 3].toInt() and 0xFF
        entries.add(AflEntry(sfi, startRec, endRec, offlineAuth))
    }
    
    return entries
}
```

### 4. **GET DATA Commands**
```kotlin
private fun buildGetDataApdu(tag: Int): ByteArray {
    return byteArrayOf(
        0x80.toByte(), 0xCA.toByte(),
        (tag shr 8).toByte(),
        (tag and 0xFF).toByte(),
        0x00
    )
}
```

### 5. **Transaction Log Parsing**
```kotlin
private fun parseTransactionLog(logData: ByteArray, logIndex: Int): TransactionLog {
    // Parse 9F4D structure:
    // - Amount (9F02)
    // - Date (9A)
    // - ATC (9F36)
    // - CVR (Card Verification Results)
    return TransactionLog(...)
}
```

### 6. **GENERATE AC**
```kotlin
private fun buildGenerateAcApdu(cryptoType: Byte, cdolData: ByteArray): ByteArray {
    // cryptoType: 0x00=AAC, 0x40=TC, 0x80=ARQC
    return byteArrayOf(
        0x80.toByte(), 0xAE.toByte(),
        cryptoType,
        0x00,
        cdolData.size.toByte()
    ) + cdolData + byteArrayOf(0x00)
}
```

### 7. **INTERNAL AUTHENTICATE**
```kotlin
private fun buildInternalAuthApdu(ddolData: ByteArray): ByteArray {
    return byteArrayOf(
        0x00, 0x88.toByte(), 0x00, 0x00,
        ddolData.size.toByte()
    ) + ddolData + byteArrayOf(0x00)
}
```

---

## 🎯 IMPLEMENTATION PRIORITY

### **Phase A: Multi-AID Processing (CRITICAL)**
- [ ] Implement `extractAllAidsFromPse()`
- [ ] Add multi-AID loop in scanning workflow
- [ ] Implement `analyzeAip()` for security analysis
- [ ] Add per-AID security tracking
- [ ] Implement weak AID detection

### **Phase B: Complete AFL Reading (HIGH)**
- [ ] Implement `parseAfl()`
- [ ] Enhance record reading to process ALL records
- [ ] Add per-SFI progress tracking
- [ ] Store records with AID/SFI/Record metadata

### **Phase C: GET DATA Commands (HIGH)**
- [ ] Implement `buildGetDataApdu()`
- [ ] Add GET DATA execution for 9F36, 9F13, 9F17, 9F4D, 9F4F
- [ ] Parse GET DATA responses
- [ ] Store primitive data

### **Phase D: Transaction Logs (MEDIUM)**
- [ ] Implement transaction log detection (9F4F)
- [ ] Implement log record reading loop
- [ ] Implement `parseTransactionLog()`
- [ ] Display transaction history

### **Phase E: Advanced Commands (LOW - Research)**
- [ ] Implement `buildGenerateAcApdu()`
- [ ] Implement `parseGenerateAcResponse()`
- [ ] Implement `buildInternalAuthApdu()` (VISA)
- [ ] Implement CCC for MasterCard

### **Phase F: Security Analysis (MEDIUM)**
- [ ] Implement multi-AID security comparison
- [ ] Flag weak secondary applications
- [ ] Generate security summary report
- [ ] Add ROCA vulnerability testing

---

## 📝 EXPECTED OUTPUT EXAMPLE

```
═══════════════════════════════════════
COMPREHENSIVE EMV SCAN (ChAP + Proxmark3)
═══════════════════════════════════════

[Phase 1] SELECT PPSE
✅ PPSE found (2PAY.SYS.DDF01)

[Phase 2] Extract ALL AIDs from PSE/PPSE
✅ Found 2 AID(s)

═══════════════════════════════════════
🔍 PROCESSING AID #1/2
   AID: A0000000031010
   Label: VISA DEBIT
═══════════════════════════════════════

[3a] SELECT AID #1
✅ AID selected

[3b] Parse PDOL
   PDOL Data: 83119F3704BA DF000D9A0324100...

[3c] GET PROCESSING OPTIONS (GPO)
✅ GPO successful

[3d] Analyze Application Interchange Profile (AIP)
   AIP: 2000
   Security: SDA DDA
   ✅ STRONG CRYPTO

[3e] Parse Application File Locator (AFL)
   AFL Entries: 3

[3f] Read ALL Records from ALL SFIs
   SFI 0x01: Records 1-2
      Record 1: 143 bytes
      Record 2: 187 bytes
   SFI 0x02: Records 1-4
      Record 1: 98 bytes
      ...

[3g] GET DATA Primitives
   ✅ ATC: 0012
   ✅ Last Online ATC: 0010
   ✅ PIN Try Counter: 03
   ❌ Log Entry: Not available

[3h] Transaction Log Extraction
   No transaction logs available

[3l] Security Analysis for AID #1
   Crypto Support:
      SDA: ✅
      DDA: ✅
      CDA: ❌

═══════════════════════════════════════
🔍 PROCESSING AID #2/2
   AID: A0000000980840
   Label: US DEBIT
═══════════════════════════════════════

[3d] Analyze Application Interchange Profile (AIP)
   AIP: 0000
   Security: (NO CRYPTO)
   ⚠️ WARNING: WEAK CRYPTO DETECTED!

[3l] Security Analysis for AID #2
   ⚠️ CRITICAL: AID #2 is WEAKER than AID #1
   AID #1: SDA DDA
   AID #2: NO CRYPTO

═══════════════════════════════════════
📊 MULTI-AID SECURITY SUMMARY
═══════════════════════════════════════
Total AIDs: 2
Strong AIDs: 1
Weak AIDs: 1
⚠️ SECURITY RISK: Secondary AID has NO cryptographic protection!

✅ COMPREHENSIVE EMV SCAN COMPLETE
```

---

## 🚀 NEXT STEPS

1. **Start with Phase A**: Implement multi-AID processing first (most critical)
2. **Add Phase B**: Complete AFL reading
3. **Implement Phase C**: GET DATA primitives
4. **Add Phase D**: Transaction logs
5. **Test thoroughly**: Use your card 0588e7ca6a5300 (2 AIDs)
6. **Add Phase E/F**: Advanced features when core is stable

This plan gives you **THE MOST COMPREHENSIVE EMV RESEARCH TOOL** by combining the best of both RFIDIOt ChAP scripts and Proxmark3 `emv scan`!
