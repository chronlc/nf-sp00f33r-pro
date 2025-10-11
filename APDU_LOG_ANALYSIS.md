# APDU LOG ANALYSIS - Card Scan Debugging
**Generated:** October 11, 2025 05:18 AM  
**Card Scanned:** US DEBIT (A0000000980840)  
**Scan Result:** PARTIAL SUCCESS - Multiple Issues Identified

---

## APDU SEQUENCE ANALYSIS

### Phase 1: PPSE Selection ✅ SUCCESS
```
CMD: SELECT PPSE (2PAY.SYS.DDF01)
RSP: 6F5B840E325041592E5359532E4444463031... (95 bytes) SW=9000
Status: ✅ SUCCESSFUL
AIDs Found: 2
  1. A0000000031010 (VISA DEBIT, Priority 1)
  2. A0000000980840 (US DEBIT, Priority 2)
Tags Parsed: 7 tags
```

### Phase 2: AID Selection ✅ SUCCESS
```
CMD: SELECT AID A0000000980840
RSP: 6F4D8407A0000000980840A542... (81 bytes) SW=9000
Status: ✅ SUCCESSFUL
PDOL Found: 9F38 = 9F66049F02069F03069F1A0295055F2A029A039C019F3704
PDOL Length: 24 bytes
Tags Parsed: 11 tags
```

**⚠️ PROBLEM #1: PDOL PARSING INCORRECT**
```
PDOL Raw: 9F38189F66049F02069F03069F1A0295055F2A029A039C019F3704
Parser Output:
  - Tag 9F66 (TTQ): Length 4 ✅ CORRECT
  - Tag 03 (BIT STRING): Length 6 ❌ WRONG (should be part of 9F02)
  - Tag 2A (Unknown): Length 2 ❌ WRONG (should be 5F2A)
  - Tag 9C (Transaction Type): Length 1 ❌ WRONG (should be after 9A)

ACTUAL PDOL Structure (EMVCo spec):
  9F66 04  (TTQ, 4 bytes)
  9F02 06  (Amount Authorized, 6 bytes)
  9F03 06  (Amount Other, 6 bytes)
  9F1A 02  (Terminal Country Code, 2 bytes)
  95 05    (TVR, 5 bytes)
  5F2A 02  (Currency Code, 2 bytes)
  9A 03    (Transaction Date, 3 bytes)
  9C 01    (Transaction Type, 1 byte)
  9F37 04  (Unpredictable Number, 4 bytes)

Root Cause: EmvTlvParser treating PDOL as TLV template instead of DOL list
Impact: GPO data built correctly anyway (hardcoded logic works)
Fix Needed: Use EmvTlvParser.parseDol() for PDOL, not parseResponse()
```

### Phase 3: GET PROCESSING OPTIONS ✅ SUCCESS (with cryptogram)
```
CMD: 80A80000238321F620C080000000000000... (43 bytes)
RSP: 775D82020020940408080800... (97 bytes) SW=9000
Status: ✅ SUCCESSFUL - Visa Quick VSDC (cryptogram in GPO)

GPO Data Sent (35 bytes):
8321F620C0800000000000000000000000000840000000000008402510110025476004
Breakdown:
  83 - Command Template tag
  21 - Length (33 bytes)
  F6 - PDOL length/format
  20 - TTQ value
  C0800000000000000000000000000840 - Amount fields + Country
  000000000008402510110025476004 - TVR + Currency + Date + Type + UN

Tags in GPO Response:
  82 (AIP): 0020 ✅
  94 (AFL): 08080800 ✅
  57 (Track2): 4232233026621689D280820150000365 ✅
  5F20 (Cardholder): CARDHOLDER/VISA ✅
  9F26 (ARQC): 4F3A68BA62E60C26 ✅ CRYPTOGRAM OBTAINED
  9F27 (CID): 80 ✅
  9F36 (ATC): 0038 ✅
  9F6C (CTQ): 0000 ✅
  9F6E (FFI): 20700000 ✅

Total: 12 tags parsed ✅
```

### Phase 4: READ APPLICATION DATA ⚠️ PARTIAL FAILURE

**AFL Parsing:**
```
AFL Raw: 08080800
Parsed Result:
  Entry 1: SFI=1, Start=8, End=8, Offline=0

AFL Breakdown (4 bytes):
  Byte 0: 0x08 → SFI = 1 (0x08 >> 3), Record Start = 8 (0x08 & 0x1F)
  Byte 1: 0x08 → Record End = 8
  Byte 2: 0x08 → Should read records 8-8? ❌ CONFUSING
  Byte 3: 0x00 → No offline records

⚠️ PROBLEM #2: AFL INTERPRETATION UNCLEAR
Standard AFL format: [SFI+FirstRec][LastRec][NumOffline][RFU]
This AFL says: SFI 1, Records 8-8, which means READ RECORD SFI=1 REC=8 only

However, code actually read:
  - SFI 1 Record 8 ✅ CORRECT (from AFL)
  - SFI 1 Record 1 ❌ EXTRA (not in AFL)
  - SFI 2 Record 2 ❌ EXTRA (not in AFL)
  - SFI 2 Record 3 ❌ EXTRA (not in AFL)
```

**Records Actually Read:**

1. **SFI 1 Record 8** ✅ FROM AFL
```
CMD: READ RECORD SFI=1 REC=8
RSP: 70145F280208409F070200809F6907010000000000009000 (24 bytes)
Tags Found:
  5F28 (Issuer Country): 0840 (USA) ✅
  9F07 (AUC): 0080 ✅
  9F69 (UDOL): 01000000000000 ✅
Status: ✅ SUCCESSFUL
```

2. **SFI 1 Record 1** ❌ NOT IN AFL (EXTRA READ)
```
CMD: READ RECORD SFI=1 REC=1
RSP: 702757134232233026621689D28082015000036500001F5F200F... (43 bytes)
Tags Found:
  57 (Track2): 4232233026621689D280820150000365 ✅
  5F20 (Cardholder): CARDHOLDER/VISA ✅
Status: ✅ SUCCESSFUL (but shouldn't have been read)
```

3. **SFI 2 Record 2** ❌ NOT IN AFL (EXTRA READ)
```
CMD: READ RECORD SFI=2 REC=2
RSP: 7081FB9081F84A42BDE8263F1E73165DBF390CCE6B8B... (256 bytes)
Tags Found:
  90 (Issuer Cert): 4A42BDE8263F1E73... (248 bytes) ✅
Status: ✅ SUCCESSFUL (but shouldn't have been read)
```

4. **SFI 2 Record 3** ❌ NOT IN AFL (EXTRA READ)
```
CMD: READ RECORD SFI=2 REC=3
RSP: 7081F49F4681F00FDCA80C759737B1B77C484A1204799D... (249 bytes)
Tags Found:
  9F46 (ICC Cert): 0FDCA80C759737B1B77C484A1204799D... (240 bytes) ✅
Status: ✅ SUCCESSFUL (but shouldn't have been read)
```

**⚠️ PROBLEM #3: EXTRA RECORD READING**
```
Root Cause: Code reads SFI 1-2, Records 1-5 regardless of AFL
Evidence: Log shows "(extra)" suffix on SFI 1 Rec 1, SFI 2 Rec 2-3
Impact: Works by accident (finds data), but not AFL-compliant
Fix Needed: Remove hardcoded loop, use ONLY AFL-parsed records

Current Code Logic (CardReadingViewModel.kt ~line 800):
  for (sfi in 1..2) {
    for (record in 1..5) {
      readRecord(sfi, record)  // ❌ WRONG - ignores AFL
    }
  }

Should Be:
  val aflEntries = EmvTlvParser.parseAfl(aflData)
  for (entry in aflEntries) {
    for (record in entry.firstRecord..entry.lastRecord) {
      readRecord(entry.sfi, record)  // ✅ CORRECT - uses AFL
    }
  }
```

### Phase 5: GENERATE AC ⚠️ SKIPPED
```
Status: ❌ SKIPPED - "Cryptogram already obtained in GPO response (Visa Quick VSDC)"
Reason: GPO returned ARQC directly (tag 9F26)
Impact: No CDOL-based AC generation tested
Note: This is CORRECT behavior for Visa Quick VSDC, but GENERATE AC should still 
      be attempted for completeness testing
```

### Phase 6: GET DATA ❌ NOT EXECUTED
```
Status: ❌ NOT IN LOG
Expected Commands:
  GET DATA 9F17 (PIN Try Counter)
  GET DATA 9F36 (ATC)
  GET DATA 9F13 (Last Online ATC)
  GET DATA 9F4F (Log Format)
  GET DATA DF60-DF7F (Proprietary)

Impact: Missing transaction log, PIN counter, proprietary data
Fix Needed: Execute GET DATA phase even if cryptogram obtained in GPO
```

---

## DATABASE SAVE ANALYSIS

### Session Data Collected
```
Session ID: (Generated UUID)
Scan Duration: Unknown (not logged)
Total APDUs: 13
Successful APDUs: 9
Total Tags Parsed: 29 unique tags

Phase-Specific Data:
  PPSE: 7 tags ✅
  AID: 11 tags ✅
  GPO: 12 tags ✅
  Records: 4 + 3 + 1 + 1 = 9 tags ✅
  Cryptogram: Already in GPO ✅
  GET DATA: 0 tags ❌
```

### Tags Collected by Category

**Card Identification:**
- ✅ UID: 7f647124 (from NFC)
- ✅ PAN: 4232233026621689 (from Track2 tag 57)
- ✅ Expiry: 2808 (Aug 2028, from Track2)
- ✅ Cardholder: CARDHOLDER/VISA (tag 5F20)
- ✅ AID: A0000000980840 (tag 84/4F)
- ✅ Label: US DEBIT (tag 50)

**EMV Capabilities:**
- ✅ AIP: 0020 (tag 82)
  - Bit analysis: 0x0020 = 0000 0000 0010 0000
  - SDA: NO (bit 6 clear)
  - DDA: YES (bit 5 set)
  - CDA: NO (bit 0 clear)
- ✅ AFL: 08080800 (tag 94)
- ✅ PDOL: 24 bytes (tag 9F38)
- ❌ CVM List: NOT FOUND (tag 8E missing)
- ❌ CDOL1: NOT FOUND (tag 8C missing)
- ❌ CDOL2: NOT FOUND (tag 8D missing)

**Cryptographic Data:**
- ✅ ARQC: 4F3A68BA62E60C26 (tag 9F26)
- ✅ CID: 80 (ARQC requested, tag 9F27)
- ✅ ATC: 0038 (56 decimal, tag 9F36)
- ✅ IAD: 06011203A00000 (tag 9F10)
- ❌ TC: NOT FOUND
- ❌ AAC: NOT FOUND

**Security/Certificates:**
- ✅ Issuer Cert: 248 bytes (tag 90)
- ✅ ICC Cert: 240 bytes (tag 9F46)
- ❌ CA Public Key Index: NOT FOUND (tag 8F)
- ❌ Issuer Exponent: NOT FOUND (tag 9F32)
- ❌ ICC Exponent: NOT FOUND (tag 9F47)
- ❌ Signed Static Data: NOT FOUND (tag 93)

**Transaction Control:**
- ✅ AUC: 0080 (tag 9F07)
- ✅ CTQ: 0000 (tag 9F6C)
- ✅ Issuer Country: 0840 (USA, tag 5F28)
- ❌ TVR: NOT FOUND (generated locally, not from card)
- ❌ Currency Code: NOT FOUND (tag 9F42)

### Missing Critical Data

**HIGH PRIORITY MISSING:**
1. ❌ **CVM List (tag 8E)** - Required for CVM bypass attacks
2. ❌ **CDOL1/CDOL2 (tags 8C/8D)** - Required for GENERATE AC
3. ❌ **CA Public Key Index (tag 8F)** - Required for ROCA analysis
4. ❌ **Issuer Public Key Exponent (tag 9F32)** - Required for ROCA
5. ❌ **ICC Public Key Exponent (tag 9F47)** - Required for ROCA

**MEDIUM PRIORITY MISSING:**
6. ❌ **PIN Try Counter (tag 9F17)** - GET DATA not executed
7. ❌ **Log Format (tag 9F4F)** - GET DATA not executed
8. ❌ **Transaction Logs** - Log reading not executed
9. ❌ **Application Currency Code (tag 9F42)** - Not in records
10. ❌ **Application Effective/Expiry Dates (tags 5F25/5F24)** - Not in records

---

## PROBLEMS SUMMARY

### 🔴 CRITICAL ISSUES

**Issue #1: PDOL Parsing Treats DOL as TLV**
```
Location: EmvTlvParser.parseResponse() called on PDOL data
Impact: Wrong tags identified (03, 2A instead of 9F02, 5F2A)
Fix: Use EmvTlvParser.parseDol() for PDOL/CDOL, not parseResponse()
Status: BROKEN but GPO works anyway due to hardcoded buildPdolData()
```

**Issue #2: AFL Parsing Returns Wrong Records**
```
Location: EmvTlvParser.parseAfl() or record reading logic
AFL Says: SFI 1, Records 8-8 (1 record only)
Code Reads: SFI 1-2, Records 1-8 (16 attempts, 4 successful)
Impact: Non-AFL-compliant, wastes time, may fail on strict cards
Fix: Use ONLY parsed AFL entries, remove hardcoded SFI/record loops
Status: BROKEN but finds data by brute force
```

**Issue #3: GET DATA Phase Not Executing**
```
Location: CardReadingViewModel.kt Phase 6
Expected: 5+ GET DATA commands (9F17, 9F36, 9F13, 9F4F, etc.)
Actual: 0 GET DATA commands executed
Impact: Missing PIN counter, transaction logs, proprietary data
Fix: Remove "skip if cryptogram obtained" logic, always run GET DATA
Status: BROKEN - entire phase skipped
```

### 🟡 HIGH PRIORITY ISSUES

**Issue #4: CDOL Not Found in Records**
```
Expected: Tag 8C (CDOL1) in one of the records
Actual: NOT FOUND (log shows "Searching for tag 8C... SUCCESS" but data not extracted)
Impact: Cannot build proper GENERATE AC command
Fix: Verify CDOL extraction logic, may be in record not read by AFL
Status: DATA MISSING from card or extraction broken
```

**Issue #5: CVM List Not Found**
```
Expected: Tag 8E (CVM List) in records
Actual: NOT FOUND
Impact: CVM bypass attacks not possible without CVM rules
Fix: Read ALL records (not just AFL), CVM List may be in record 2-7
Status: DATA MISSING - need full record scan
```

**Issue #6: ROCA Analysis Incomplete**
```
Expected: Tags 8F, 9F32, 9F47, 93 for complete ROCA scan
Actual: Only 90 (Issuer Cert) and 9F46 (ICC Cert) found
Impact: ROCA vulnerability cannot be properly analyzed
Fix: Read additional records, exponents may be in records 4-7
Status: PARTIAL DATA - need more records
```

### 🟢 MEDIUM PRIORITY ISSUES

**Issue #7: Transaction Log Not Read**
```
Expected: If tag 9F4F found, read transaction log
Actual: GET DATA not executed, so 9F4F never requested
Impact: Cannot analyze transaction history
Fix: Execute GET DATA phase
Status: PHASE SKIPPED
```

**Issue #8: Extra Tags Not Attempted**
```
Expected: GET DATA for tags DF60-DF7F (proprietary), 9F6D, 9F6E, etc.
Actual: No GET DATA attempts
Impact: Missing proprietary attack vectors
Fix: Add comprehensive GET DATA list
Status: NOT IMPLEMENTED
```

---

## RECOMMENDED FIXES

### Fix #1: PDOL Parsing (CRITICAL)
```kotlin
// WRONG (current):
val pdolTags = EmvTlvParser.parseResponse(pdolHex, "PDOL")

// CORRECT (should be):
val pdolStructure = EmvTlvParser.parseDol(pdolHex)
// Returns: List<DolEntry(tag, length)>
// Example: [(9F66, 4), (9F02, 6), (9F03, 6), ...]
```

### Fix #2: AFL-Based Record Reading (CRITICAL)
```kotlin
// WRONG (current):
for (sfi in 1..2) {
  for (record in 1..5) {
    readRecord(sfi, record)  // Brute force
  }
}

// CORRECT (should be):
val aflData = allEmvTags["94"]?.value ?: ""
val aflEntries = EmvTlvParser.parseAfl(aflData)
for (entry in aflEntries) {
  val sfi = entry.sfi
  for (record in entry.firstRecord..entry.lastRecord) {
    readRecord(sfi, record)  // Precise, AFL-driven
  }
}
```

### Fix #3: Force GET DATA Execution (CRITICAL)
```kotlin
// WRONG (current):
if (arqcObtainedInGpo) {
  Timber.i("Skipping GENERATE AC and GET DATA")
  return@launch
}

// CORRECT (should be):
if (arqcObtainedInGpo) {
  Timber.i("Cryptogram obtained in GPO, skipping GENERATE AC only")
}
// Continue to GET DATA phase regardless
executeGetDataPhase()
```

### Fix #4: Expand GET DATA Tags (HIGH)
```kotlin
val getDataTags = listOf(
  "9F17",  // PIN Try Counter
  "9F36",  // ATC
  "9F13",  // Last Online ATC
  "9F4F",  // Log Format
  "9F6D",  // Mag-stripe Track1 Data
  "9F6E",  // Form Factor Indicator (already obtained)
  "9F6C",  // CTQ (already obtained)
  "DF60", "DF61", "DF62", "DF63", "DF64",  // Proprietary
  "DF65", "DF66", "DF67", "DF68", "DF69",
  "DF6A", "DF6B", "DF6C", "DF6D", "DF6E", "DF6F",
  "DF70", "DF71", "DF72", "DF73", "DF74", "DF75"
)
```

### Fix #5: Read ALL Records (HIGH)
```kotlin
// After AFL-based reading, attempt SFI 1-3, Records 1-16 to find missing tags
val targetTags = listOf("8E", "8C", "8D", "8F", "9F32", "9F47", "93")
if (targetTags.any { tag -> !allEmvTags.containsKey(tag) }) {
  Timber.i("Missing critical tags, attempting full record scan")
  for (sfi in 1..3) {
    for (record in 1..16) {
      val response = readRecord(sfi, record)
      if (response.statusWord == "9000") {
        val tags = EmvTlvParser.parseResponse(response.data, "RECORD")
        // Check if we found missing tags
      }
    }
  }
}
```

---

## ATTACK COMPATIBILITY ANALYSIS

### Based on Current Data Collection

| Attack | Required Data | Status | Compatibility |
|--------|--------------|--------|---------------|
| **Track 2 Manipulation** | Track2 (57) | ✅ FOUND | ✅ 100% READY |
| **ARQC Replay** | ARQC (9F26), ATC (9F36) | ✅ FOUND | ✅ 100% READY |
| **AIP Modification** | AIP (82) | ✅ FOUND | ✅ 100% READY |
| **CVM Bypass** | CVM List (8E) | ❌ MISSING | ❌ 0% READY |
| **ROCA Exploitation** | Certs (90, 9F46), Exponents (9F32, 9F47), Index (8F) | ⚠️ PARTIAL | ⚠️ 40% READY |
| **Offline Approval** | AUC (9F07), AIP (82), CVM (8E) | ⚠️ PARTIAL | ⚠️ 66% READY |
| **Amount Modification** | CDOL (8C), ARQC (9F26) | ❌ MISSING CDOL | ❌ 50% READY |
| **Transaction Log** | Log Format (9F4F), Logs | ❌ NOT ATTEMPTED | ❌ 0% READY |
| **AID Selection** | Multiple AIDs (4F) | ✅ FOUND | ✅ 100% READY |

**Overall Attack Readiness: 50% (5/9 attacks fully supported)**

---

## NEXT STEPS (PRIORITY ORDER)

1. **FIX #1** - PDOL parsing (use parseDol(), not parseResponse())
2. **FIX #2** - AFL-based record reading (remove hardcoded loops)
3. **FIX #3** - Force GET DATA execution (remove skip logic)
4. **FIX #4** - Expand GET DATA tag list (proprietary tags)
5. **FIX #5** - Full record scan for missing critical tags
6. **TEST** - Re-scan card with fixes applied
7. **VERIFY** - All 29+ expected tags collected
8. **IMPLEMENT** - Attack modules using collected data

---

## CONCLUSION

**Current Status:** 50% functional - collects basic EMV data but misses critical tags

**Working:**
- ✅ PPSE/AID selection
- ✅ GPO with dynamic PDOL
- ✅ Basic record reading (by accident)
- ✅ Cryptogram capture
- ✅ Database save

**Broken:**
- ❌ PDOL parsing (wrong tags identified)
- ❌ AFL-based record reading (brute force instead)
- ❌ GET DATA phase (completely skipped)
- ❌ Missing CVM List (critical for attacks)
- ❌ Missing CDOL (critical for GENERATE AC)
- ❌ Incomplete ROCA data (missing exponents)

**Recommendation:** Apply fixes #1-#3 immediately, then re-scan card to verify all data collected.
