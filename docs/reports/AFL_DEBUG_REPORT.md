# AFL Record Reading Flow Debug Report
**Generated:** October 11, 2025  
**Issue:** AFL-based record reading not executing correctly

---

## PROBLEM IDENTIFIED

### AFL Data Found
```
AFL Raw Hex: 08080800
Parsed breakdown:
- Byte 0: 0x08 = SFI calculation: 0x08 >> 3 = 1
- Byte 1: 0x08 = Start Record: 8  
- Byte 2: 0x08 = End Record: 8
- Byte 3: 0x00 = Offline Auth Records: 0

Expected behavior: Read SFI 1, Record 8
```

### Current Code Flow (CardReadingViewModel.kt lines 747-790)

```kotlin
// Line 758: Extract AFL from GPO response
val aflFromGpo = extractAflFromAllResponses(apduLog)

// Line 760: Parse AFL using EmvTlvParser
val aflEntries = EmvTlvParser.parseAfl(aflFromGpo)

// Line 767-772: Build records list
val records = aflEntries.flatMap { entry ->
    (entry.startRecord..entry.endRecord).map { record ->
        Triple(entry.sfi, record, (entry.sfi shl 3) or 0x04)
    }
}
// This SHOULD create: Triple(1, 8, 0x0C)
// SFI=1, Record=8, P2=0x0C (binary: 00001100)
```

### EmvTlvParser.parseAfl() (EmvTlvParser.kt lines 535-560)

```kotlin
fun parseAfl(aflData: String): List<AflEntry> {
    val entries = mutableListOf<AflEntry>()
    
    try {
        val bytes = hexToBytes(aflData)
        
        if (bytes.size % 4 != 0) {
            Timber.w("AFL data length invalid: ${bytes.size} bytes")
            return entries
        }
        
        for (i in bytes.indices step 4) {
            val sfi = (bytes[i].toInt() and 0xFF) shr 3  // ✅ Correct: 0x08 >> 3 = 1
            val startRecord = bytes[i + 1].toInt() and 0xFF  // ✅ Correct: 8
            val endRecord = bytes[i + 2].toInt() and 0xFF    // ✅ Correct: 8
            val offlineRecords = bytes[i + 3].toInt() and 0xFF  // ✅ Correct: 0
            
            entries.add(AflEntry(sfi, startRecord, endRecord, offlineRecords))
            Timber.d("AFL Entry: SFI=$sfi, Records=$startRecord-$endRecord, Offline=$offlineRecords")
        }
        
    } catch (e: Exception) {
        Timber.e("AFL parsing error: ${e.message}", e)
    }
    
    return entries
}
```

**Parser Logic:** ✅ CORRECT

### P2 Byte Calculation (Line 770)

```kotlin
Triple(entry.sfi, record, (entry.sfi shl 3) or 0x04)
```

**For SFI=1, Record=8:**
```
P2 = (1 << 3) | 0x04
P2 = 0x08 | 0x04
P2 = 0x0C (binary: 00001100)
```

**P2 Byte Format (ISO 7816-4):**
```
Bits 7-3: SFI (Short File Identifier)
Bits 2-0: Reference control (0x04 = P1 is record number)

0x0C = 00001100
       ^^^^^ ^^^
       SFI=1 RefCtrl=4
```

**P2 Calculation:** ✅ CORRECT

### READ RECORD Command Format

```
Command: 00 B2 [Record] [P2] 00
For SFI=1, Record=8:
00 B2 08 0C 00
```

**Command Format:** ✅ CORRECT

---

## ROOT CAUSE ANALYSIS

### Hypothesis 1: extractAflFromAllResponses() Returns Empty String
**Test:** Check if AFL extraction is failing

```kotlin
// Line 758
val aflFromGpo = extractAflFromAllResponses(apduLog)
Timber.i("AFL extracted from responses: ${if (aflFromGpo.isNotEmpty()) aflFromGpo else "NONE"}")
```

**If AFL is empty:**
- Code falls through to line 778-787 (fallback records)
- Reads hardcoded SFI 1-3, Records 1-3 instead of AFL-based

### Hypothesis 2: EmvTlvParser.parseAfl() Returns Empty List
**Test:** Check if parser is returning results

```kotlin
// Line 760
val aflEntries = EmvTlvParser.parseAfl(aflFromGpo)
if (aflEntries.isNotEmpty()) {
    Timber.i("✓ AFL parsed successfully: ${aflEntries.size} entries")
    // ... should execute
} else {
    Timber.w("✗ AFL parsing failed - using fallback")
    // ... fallback executes instead
}
```

### Hypothesis 3: AFL Not Present in GPO Response
**Scenario:** Card uses Format 1 GPO response (AIP+AFL inline)

**Format 1 GPO Response:**
```
Response: 80 0E [AIP 2 bytes] [AFL n bytes]
Example: 80 0E 1C 00 08 08 08 00 9000
         ^^^^^ ^^^^^ ^^^^^^^^^^
         Tag   AIP   AFL(4 bytes)
```

**Format 2 GPO Response (Tag 77 or 80):**
```
Response: 77 [Len] [TLV Data with tag 82=AIP, 94=AFL]
```

**Issue:** extractAflFromAllResponses() may not handle Format 1 correctly

---

## DIAGNOSTIC COMMANDS NEEDED

### 1. Check AFL Extraction
```kotlin
// Add debug logging in CardReadingViewModel
val aflFromGpo = extractAflFromAllResponses(apduLog)
android.util.Log.e("DEBUG_AFL", "Extracted AFL: '$aflFromGpo' (length=${aflFromGpo.length})")
android.util.Log.e("DEBUG_AFL", "AFL isEmpty: ${aflFromGpo.isEmpty()}")
```

### 2. Check Parser Output
```kotlin
val aflEntries = EmvTlvParser.parseAfl(aflFromGpo)
android.util.Log.e("DEBUG_AFL", "Parsed AFL entries: ${aflEntries.size}")
aflEntries.forEachIndexed { idx, entry ->
    android.util.Log.e("DEBUG_AFL", "  Entry $idx: SFI=${entry.sfi}, Records=${entry.startRecord}-${entry.endRecord}")
}
```

### 3. Check Records List
```kotlin
val records = aflEntries.flatMap { entry ->
    (entry.startRecord..entry.endRecord).map { record ->
        Triple(entry.sfi, record, (entry.sfi shl 3) or 0x04)
    }
}
android.util.Log.e("DEBUG_AFL", "Records to read: ${records.size}")
records.forEach { (sfi, rec, p2) ->
    android.util.Log.e("DEBUG_AFL", "  Will read: SFI=$sfi Record=$rec P2=0x${p2.toString(16).uppercase()}")
}
```

---

## SUSPECTED ISSUE: extractAflFromAllResponses()

### Current Implementation (needs verification)
```kotlin
private fun extractAflFromAllResponses(apduLog: List<ApduLogEntry>): String {
    // Searches for tag 94 in APDU responses
    // May not handle Format 1 GPO (80 tag) correctly
}
```

### Possible Bug Scenarios

**Scenario A: Format 1 GPO Not Parsed**
```
GPO Response: 80 0E 1C 00 08 08 08 00 9000
              ^^^^^ ^^^^^ ^^^^^^^^^^
              Tag80 AIP   AFL
              
extractAflFromAllResponses() searches for tag 94 (not found)
Returns empty string
Falls back to hardcoded records
```

**Fix:** Handle Format 1 GPO response (tag 80)

**Scenario B: Tag 94 Nested in Template 77**
```
GPO Response: 77 [Len] 82 02 [AIP] 94 04 [AFL] 9000

extractAflFromAllResponses() may not parse nested TLV correctly
Returns empty string
Falls back to hardcoded records  
```

**Fix:** Use EmvTlvParser.parseResponse() to extract tag 94 from parsed results

---

## RECOMMENDED FIX

### Option 1: Use Parsed GPO Response (BEST)

```kotlin
// Line 758-790 replacement
val aflFromGpo = currentSessionData?.gpoResponse?.get("94")?.value ?: ""

Timber.i("=".repeat(80))
Timber.i("AFL PARSING AND RECORD READING")
Timber.i("AFL from parsed GPO: ${if (aflFromGpo.isNotEmpty()) aflFromGpo else "NONE"}")

val recordsToRead = if (aflFromGpo.isNotEmpty()) {
    val aflEntries = EmvTlvParser.parseAfl(aflFromGpo)
    if (aflEntries.isNotEmpty()) {
        Timber.i("✓ AFL parsed successfully: ${aflEntries.size} entries")
        aflEntries.forEach { entry ->
            Timber.i("  SFI ${entry.sfi}: Records ${entry.startRecord}-${entry.endRecord}, Offline=${entry.offlineRecords}")
        }
        val records = aflEntries.flatMap { entry ->
            (entry.startRecord..entry.endRecord).map { record ->
                Triple(entry.sfi, record, (entry.sfi shl 3) or 0x04)
            }
        }
        Timber.i("Will read ${records.size} total records: ${records.map { (s,r,_) -> "SFI$s-R$r" }.joinToString()}")
        records
    } else {
        Timber.w("✗ AFL parsing failed - using fallback")
        fallbackRecords()
    }
} else {
    Timber.w("✗ No AFL found - using fallback record locations")
    fallbackRecords()
}
Timber.i("=".repeat(80))

// Continue with record reading loop...
```

### Option 2: Debug extractAflFromAllResponses()

Add logging to understand why it's not finding AFL:

```kotlin
private fun extractAflFromAllResponses(apduLog: List<ApduLogEntry>): String {
    android.util.Log.e("DEBUG_AFL_EXTRACT", "=== Searching for AFL in ${apduLog.size} APDUs ===")
    
    for (entry in apduLog) {
        android.util.Log.e("DEBUG_AFL_EXTRACT", "APDU: ${entry.command} -> ${entry.response.take(40)}...")
        
        // Check for Format 1 GPO (tag 80)
        if (entry.response.startsWith("80")) {
            android.util.Log.e("DEBUG_AFL_EXTRACT", "Found Format 1 GPO response!")
            // Parse Format 1: 80 Len AIP(2) AFL(n)
            // Extract AFL starting at byte 4 (after 80 Len AIP)
        }
        
        // Check for Format 2 GPO (tag 77 or nested 94)
        if (entry.response.contains("94")) {
            android.util.Log.e("DEBUG_AFL_EXTRACT", "Found tag 94 in response!")
            // Use parser to extract tag 94 value
        }
    }
    
    android.util.Log.e("DEBUG_AFL_EXTRACT", "=== AFL extraction complete ===")
    return ""  // Current implementation
}
```

---

## NEXT STEPS

1. **Add Debug Logging**
   - Insert AFL extraction debug logs
   - Insert AFL parsing debug logs
   - Insert records list debug logs

2. **Scan Test Card**
   - Run `adb logcat -c`
   - Scan card
   - Capture logs with `adb logcat -d | grep DEBUG_AFL`

3. **Analyze Results**
   - Verify AFL extraction returns `08080800`
   - Verify parser creates `AflEntry(sfi=1, start=8, end=8, offline=0)`
   - Verify records list contains `Triple(1, 8, 0x0C)`
   - Verify READ RECORD command sent: `00 B2 08 0C 00`

4. **Apply Fix**
   - If extraction fails: Fix extractAflFromAllResponses()
   - If parser fails: Debug EmvTlvParser.parseAfl()
   - If records list wrong: Fix flatMap logic

---

## WORKAROUND (Temporary)

Force AFL value for testing:

```kotlin
// Line 758 - temporary override
val aflFromGpo = "08080800"  // Force known AFL value
Timber.w("DEBUG: Using hardcoded AFL for testing")

// Continue with normal flow...
```

This will bypass extraction and test if parsing/reading logic works.

---

## CONCLUSION

**Most Likely Issue:** `extractAflFromAllResponses()` is not finding AFL in GPO response

**Evidence:**
- EmvTlvParser correctly found tag 94 = `08080800` in logs
- Parser logic is correct (verified manually)
- P2 calculation is correct (verified manually)
- Command format is correct (verified manually)
- But code falls back to hardcoded records 1-5

**Next Action:** Debug `extractAflFromAllResponses()` with detailed logging to see why it returns empty when AFL exists in response.
