# 🎯 MINIMAL EMV ENHANCEMENT PLAN
## Complete Feature Implementation Roadmap

**Date:** October 10, 2025  
**Project:** nf-sp00f33r  
**File:** CardReadingViewModel.kt  
**Goal:** Merge RFIDIOt ChAP + Proxmark3 `emv scan` features

---

## 📋 COMPLETE FEATURE LIST

### ✅ **PHASE 1: MULTI-AID PROCESSING** (IMMEDIATE)
**What:** Process ALL AIDs found in PPSE, not just first one  
**Why:** Reveals weak secondary applications (your card has 2 AIDs: strong + weak)  
**Implementation:**
```kotlin
// 1. Add data class
data class AidEntry(val aid: String, val label: String, val priority: Int)

// 2. Extract all AIDs from PPSE
fun extractAllAidsFromPse(pseResponse: ByteArray): List<AidEntry>

// 3. Wrap current scan in FOR EACH loop
for (aidEntry in aidList) {
    selectAid(aidEntry.aid)
    // ... existing GPO/AFL/Record logic
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** ~400-700 (wrap executeRealEmvScan Phase 3-6)  
**Estimated time:** 30 mins

---

### ✅ **PHASE 2: COMPLETE AFL READING** (IMMEDIATE)
**What:** Read ALL records from ALL SFIs (not just templates)  
**Why:** Currently only reads records in templates, misses data  
**Implementation:**
```kotlin
// Already have parseAfl() - just enhance record loop
for (aflEntry in aflEntries) {
    for (recNum in aflEntry.startRec..aflEntry.endRec) {
        val readRecApdu = buildReadRecordApdu(aflEntry.sfi, recNum)
        val response = sendApdu(readRecApdu)
        // Parse ALL records, not just first
    }
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** ~580-650 (Phase 6 record reading)  
**Estimated time:** 15 mins

---

### ✅ **PHASE 3: AIP SECURITY ANALYSIS** (IMMEDIATE)
**What:** Analyze AIP to detect SDA/DDA/CDA support, flag weak AIDs  
**Why:** Security research - identify vulnerable cards  
**Implementation:**
```kotlin
// 1. Add data class
data class SecurityInfo(
    val hasSDA: Boolean, val hasDDA: Boolean, val hasCDA: Boolean,
    val isWeak: Boolean, val summary: String
)

// 2. Parse AIP byte 1 bitmask
fun analyzeAip(aipHex: String): SecurityInfo {
    val byte1 = aipHex.hexToByteArray()[0].toInt()
    return SecurityInfo(
        hasSDA = (byte1 and 0x40) != 0,
        hasDDA = (byte1 and 0x20) != 0,
        hasCDA = (byte1 and 0x01) != 0,
        isWeak = (byte1 and 0x61) == 0,
        summary = if (byte1 and 0x61 == 0) "NO CRYPTO" else "OK"
    )
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add after line ~1100 (helper functions)  
**Estimated time:** 20 mins

---

### ⚠️ **PHASE 4: GET DATA COMMANDS** (HIGH)
**What:** Execute GET DATA for primitives 9F36, 9F13, 9F17, 9F4D, 9F4F  
**Why:** Extract ATC, Last ATC, PIN Try Counter, Log info  
**Implementation:**
```kotlin
// 1. Build GET DATA APDU
fun buildGetDataApdu(tag: Int): ByteArray {
    return byteArrayOf(
        0x80.toByte(), 0xCA.toByte(),
        (tag shr 8).toByte(), (tag and 0xFF).toByte(), 0x00
    )
}

// 2. Execute after record reading (Phase 7)
val getDataTags = listOf(0x9F36, 0x9F13, 0x9F17, 0x9F4D, 0x9F4F)
for (tag in getDataTags) {
    val apdu = buildGetDataApdu(tag)
    val response = sendApdu(apdu)
    if (response.isSuccess()) {
        appendLog("GET DATA ${tag.toHex()}: ${response.toHex()}")
    }
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add Phase 7 after line ~750 (after record reading)  
**Estimated time:** 25 mins

---

### ⚠️ **PHASE 5: TRANSACTION LOG READING** (HIGH)
**What:** If tag 9F4D present, read transaction log records  
**Why:** Historical transaction data (amount, date, ATC)  
**Implementation:**
```kotlin
// After GET DATA, check for 9F4F (Log Format)
val logFormat = extractTag(allResponses, "9F4F")
if (logFormat.length >= 2) {
    val logSfi = (logFormat[0].toInt() shr 3) and 0x1F
    val logRecords = logFormat[1].toInt() and 0xFF
    
    for (i in 1..logRecords) {
        val logApdu = buildReadRecordApdu(logSfi, i)
        val logResp = sendApdu(logApdu)
        appendLog("Transaction Log #$i: ${logResp.toHex()}")
    }
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add Phase 8 after GET DATA (~line 800)  
**Estimated time:** 20 mins

---

### ⚠️ **PHASE 6: CVM LIST PARSER** (HIGH)
**What:** Parse tag 8E (CVM List) - cardholder verification methods  
**Why:** Shows PIN requirements, signature rules  
**Implementation:**
```kotlin
fun parseCvmList(cvmListHex: String): Map<String, Any> {
    val cvmList = cvmListHex.hexToByteArray()
    val amountX = cvmList.sliceArray(0..3).toLong()
    val amountY = cvmList.sliceArray(4..7).toLong()
    
    val rules = mutableListOf<String>()
    for (i in 8 until cvmList.size step 2) {
        val cvmCode = cvmList[i].toInt() and 0x3F
        val condition = cvmList[i+1].toInt()
        rules.add(decodeCvmRule(cvmCode, condition))
    }
    
    return mapOf("amountX" to amountX, "amountY" to amountY, "rules" to rules)
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add parser at ~line 1150, call in Phase 6  
**Estimated time:** 30 mins

---

### 🔧 **PHASE 7: CDOL1/CDOL2 PARSER** (MEDIUM)
**What:** Full DOL parsing for CDOL1 (tag 8C) and CDOL2 (tag 8D)  
**Why:** Build proper GENERATE AC requests  
**Implementation:**
```kotlin
fun parseDol(dolHex: String): List<Pair<Int, Int>> {
    // Parse tag-length pairs from DOL
    val dol = dolHex.hexToByteArray()
    val entries = mutableListOf<Pair<Int, Int>>()
    
    var i = 0
    while (i < dol.size) {
        val tag = if ((dol[i].toInt() and 0x1F) == 0x1F) {
            // 2-byte tag
            ((dol[i].toInt() and 0xFF) shl 8) or (dol[i+1].toInt() and 0xFF)
            i += 2
        } else {
            dol[i++].toInt() and 0xFF
        }
        val len = dol[i++].toInt() and 0xFF
        entries.add(tag to len)
    }
    
    return entries
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add at ~line 1200  
**Estimated time:** 25 mins

---

### 🔧 **PHASE 8: ENHANCED TAG DICTIONARY** (MEDIUM)
**What:** Add 100+ EMV tags from ChAP scripts  
**Why:** Complete tag decoding for all EMV data  
**Implementation:**
```kotlin
// Add to EmvTagDictionary.kt
val EXTENDED_TAG_DICTIONARY = mapOf(
    "9F60" to "CVC3 Track 1",
    "9F61" to "CVC3 Track 2",
    "9F62" to "Track 1 Bit Map for CVC3",
    "9F63" to "Track 1 Bit Map for UN and ATC",
    "9F64" to "Track 1 Nr of ATC Digits",
    "9F65" to "Track 2 Bit Map for CVC3",
    "9F66" to "Terminal Transaction Qualifiers (TTQ)",
    "9F67" to "Track 2 Nr of ATC Digits",
    // ... 90+ more tags from ChAP
)
```
**Files to modify:** EmvTagDictionary.kt  
**Lines:** Expand existing dictionary  
**Estimated time:** 15 mins (copy-paste from ChAP)

---

### 🔧 **PHASE 9: JSON EXPORT** (MEDIUM)
**What:** Export complete EMV data to JSON file  
**Why:** Data persistence, analysis, sharing  
**Implementation:**
```kotlin
fun exportToJson(): String {
    val jsonObj = JSONObject().apply {
        put("timestamp", System.currentTimeMillis())
        put("cardBrand", currentEmvData?.getCardBrandDisplayName())
        put("pan", currentEmvData?.pan)
        put("aids", aidSecurityInfo.map { ... })
        put("apduLog", apduLog.map { ... })
        put("emvTags", parsedEmvFields)
    }
    return jsonObj.toString(2)
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add at ~line 1300  
**Estimated time:** 20 mins

---

### 🧪 **PHASE 10: GENERATE AC** (LOW - Research)
**What:** Execute GENERATE AC command (0x80 0xAE)  
**Why:** Generate ARQC/TC/AAC cryptograms  
**Implementation:**
```kotlin
fun buildGenerateAcApdu(type: Byte, cdolData: ByteArray): ByteArray {
    // type: 0x00=AAC, 0x40=TC, 0x80=ARQC
    return byteArrayOf(
        0x80.toByte(), 0xAE.toByte(), type, 0x00,
        cdolData.size.toByte()
    ) + cdolData + byteArrayOf(0x00)
}

// After CDOL1 parsing
val cdol1Data = buildCdolData(cdol1)
val genAcApdu = buildGenerateAcApdu(0x80, cdol1Data)
val genAcResp = sendApdu(genAcApdu)
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add Phase 9 at ~line 850  
**Estimated time:** 30 mins

---

### 🧪 **PHASE 11: INTERNAL AUTHENTICATE** (LOW - Research)
**What:** Execute INTERNAL AUTHENTICATE (0x88) for VISA DDA  
**Why:** Generate SDAD (Signed Dynamic Application Data)  
**Implementation:**
```kotlin
fun buildInternalAuthApdu(ddolData: ByteArray): ByteArray {
    return byteArrayOf(
        0x00, 0x88.toByte(), 0x00, 0x00,
        ddolData.size.toByte()
    ) + ddolData + byteArrayOf(0x00)
}

// If VISA card and DDA supported
if (isVisaCard && securityInfo.hasDDA) {
    val ddol = extractTag(allResponses, "9F49")
    if (ddol.isNotEmpty()) {
        val ddolData = buildDdolData(ddol)
        val intAuthApdu = buildInternalAuthApdu(ddolData)
        val intAuthResp = sendApdu(intAuthApdu)
    }
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add Phase 10 at ~line 900  
**Estimated time:** 25 mins

---

### 🧪 **PHASE 12: ROCA VULNERABILITY TEST** (LOW - Research)
**What:** Test RSA public keys for ROCA vulnerability  
**Why:** Security research - detect weak keys  
**Implementation:**
```kotlin
fun testRocaVulnerability(issuerPublicKey: ByteArray): Boolean {
    // Extract modulus from public key
    val modulus = extractRsaModulus(issuerPublicKey)
    
    // Test ROCA fingerprint (modulus % small primes)
    val rocaMarkers = listOf(3, 5, 7, 11, 13, 17, 19, 23, 29, 31)
    val fingerprint = rocaMarkers.map { modulus.mod(it.toBigInteger()) }
    
    // Check against known vulnerable patterns
    return isRocaFingerprint(fingerprint)
}
```
**Files to modify:** CardReadingViewModel.kt  
**Lines:** Add at ~line 1400  
**Estimated time:** 40 mins (complex math)

---

## 📊 IMPLEMENTATION SUMMARY

### Minimal Effort (Core Features Only)
**Time:** ~2-3 hours  
**Phases:** 1, 2, 3, 4, 5, 6  
**Result:** Multi-AID, complete data extraction, security analysis

### Complete Implementation (All Features)
**Time:** ~4-5 hours  
**Phases:** All 12  
**Result:** Full ChAP + Proxmark3 feature parity

### Recommended Order:
1. **Phase 1** (Multi-AID) - 30 min - CRITICAL
2. **Phase 3** (AIP Analysis) - 20 min - Complements Phase 1
3. **Phase 2** (Complete AFL) - 15 min - Quick win
4. **Phase 4** (GET DATA) - 25 min - High value
5. **Phase 5** (Transaction Logs) - 20 min - Depends on Phase 4
6. **Phase 6** (CVM Parser) - 30 min - Research value
7. **Phase 7-9** (CDOL/Tags/JSON) - 60 min - Nice to have
8. **Phase 10-12** (Research) - 95 min - Optional

---

## 🎯 QUICK START GUIDE

### Bare Minimum (1 hour)
```bash
# Implement these 3 phases for immediate impact:
1. Multi-AID processing (Phase 1)
2. AIP security analysis (Phase 3)  
3. GET DATA commands (Phase 4)
```

### Recommended (2.5 hours)
```bash
# Add these for complete extraction:
4. Complete AFL reading (Phase 2)
5. Transaction logs (Phase 5)
6. CVM parser (Phase 6)
```

### Full Implementation (5 hours)
```bash
# Everything above + research features (Phases 7-12)
```

---

## 📂 FILES TO MODIFY

1. **CardReadingViewModel.kt** (main file)
   - Lines ~400-900: Main scan workflow
   - Lines ~1100-1500: Helper functions
   - Lines ~150-200: Data classes

2. **EmvTagDictionary.kt** (Phase 8)
   - Expand tag dictionary

3. **EmvTlvParser.kt** (optional)
   - Already has parseAfl, parseAip
   - May add parseCvmList, parseDol

---

## ✅ TESTING CHECKLIST

After each phase:
```bash
./gradlew assembleDebug
adb install -r android-app/build/outputs/apk/debug/*.apk
adb shell am start -n com.nfsp00f33r.app/.activities.SplashActivity

# Test with your card (0588e7ca6a5300) which has 2 AIDs
# Verify multi-AID processing shows both apps
# Check security analysis flags weak AID #2
```

---

## 🚀 EXPECTED OUTCOME

**Before:**
- Processes 1 AID only
- Reads some records
- Basic tag extraction

**After (Phases 1-6):**
- Processes ALL AIDs (2 for your card)
- Reads ALL records from ALL SFIs
- Security analysis per AID
- GET DATA primitives extracted
- Transaction logs displayed
- CVM rules parsed
- Multi-AID security summary

**After (All Phases):**
- Everything above +
- GENERATE AC support
- INTERNAL AUTHENTICATE (VISA)
- ROCA vulnerability testing
- JSON export
- 100+ tag dictionary

---

**Ready to implement?** Start with Phase 1 (Multi-AID) - it's the most critical feature that reveals weak secondary applications like we found in your card log!
