# 🌙 Nightly Release - October 10, 2025

**Version:** Nightly-2025-10-10  
**Build Date:** October 10, 2025, 2:21 PM UTC  
**APK Size:** 74 MB  
**Min SDK:** 28 (Android 9.0 Pie)  
**Target SDK:** 34 (Android 14)  
**Build Status:** ✅ BUILD SUCCESSFUL

---

## 🎉 What's New

### 1. 🔐 ROCA Vulnerability Integration (Production-Ready)

**Real-time ROCA detection during card scanning - no separate analysis needed!**

- **Automatic Detection**
  - ROCA analysis now runs automatically during EMV workflow
  - Certificate tags (9F46, 92, 9F32, 9F47) analyzed as they're parsed
  - Results updated in real-time with confidence levels
  - Auto-clears previous results at workflow start for clean state

- **Comprehensive Analysis**
  - 4 new card data fields: `rocaVulnerable`, `rocaVulnerabilityStatus`, `rocaAnalysisDetails`, `rocaCertificatesAnalyzed`
  - Confidence levels: CONFIRMED, HIGHLY_LIKELY, POSSIBLE, UNLIKELY, UNKNOWN
  - Detailed analysis includes tag ID, key size, fingerprint, factor attempt results
  - Warns when RSA private key is compromised

- **Workflow Integration**
  - `EmvTlvParser.clearRocaAnalysisResults()` at workflow start
  - `checkRocaVulnerability()` called after each TLV parse phase
  - `extractRocaAnalysisDetails()` formats vulnerability data for storage
  - All results saved with card record in database

**Example Output:**
```
🚨 CONFIRMED ROCA VULNERABLE - RSA keys compromised!
Tag 9F46 (ICC Public Key Certificate):
  Vulnerable: true
  Confidence: CONFIRMED
  Key size: 1024 bits
  Fingerprint: 0x1234ABCD
  Factor attempt: SUCCESS
  ⚠️ RSA PRIVATE KEY COMPROMISED!
```

---

### 2. 💳 iCVV/Dynamic CVV Analysis (Production-Ready)

**Analyze dynamic CVV generation capabilities - Track 1/2 bitmap processing!**

- **Brian Kernighan Bit Counting Algorithm**
  - `calculateUnSize()` function from ChAP.py research
  - Counts set bits in Track bitmaps to calculate UN (Unpredictable Number) size
  - Accurate bit counting using bitwise operations
  - Handles Track 1 and Track 2 bitmaps independently

- **Track Bitmap Extraction**
  - Tag 9F63: Track 1 Bit Map for UN and ATC
  - Tag 9F64: Track 1 Number of ATC Digits
  - Tag 9F65: Track 2 Bit Map for CVC3
  - Tag 9F66: Track 2 Bit Map for UN and ATC
  - Automatic extraction during TLV parsing

- **8 New Card Data Fields**
  - `icvvCapable`: Boolean indicating dynamic CVV support
  - `icvvTrack1Bitmap`: Track 1 bitmap hex string
  - `icvvTrack1AtcDigits`: Number of ATC digits in Track 1
  - `icvvTrack1UnSize`: Calculated UN size for Track 1
  - `icvvTrack2Bitmap`: Track 2 bitmap hex string
  - `icvvTrack2UnSize`: Calculated UN size for Track 2
  - `icvvStatus`: Human-readable status message
  - `icvvParameters`: Formatted parameter string for analysis

**Example Output:**
```
✅ Card supports iCVV/Dynamic CVV generation
Track 1 iCVV params: bitmap=8C000000, atcDigits=2, unSize=5 bytes
Track 2 iCVV params: bitmap=0C000000, unSize=2 bytes
```

---

### 3. 📡 Contact/Contactless Mode Toggle (Production-Ready)

**Test cards in both contact (1PAY) and contactless (2PAY) modes!**

- **Automatic Fallback**
  - Tries 2PAY.SYS.DDF01 (contactless) first by default
  - Automatically falls back to 1PAY.SYS.DDF01 (contact) on failure
  - Status messages show which mode succeeded
  - Logs PPSE selection strategy for debugging

- **Force Contact Mode**
  - `setContactMode(enabled: Boolean)` function
  - `forceContactMode` flag bypasses contactless attempt
  - Useful for testing contact-only terminals
  - PPSE selection respects mode configuration

- **Enhanced Logging**
  - "PPSE (2PAY Contactless): SW=9000" - Contactless success
  - "PPSE (1PAY Contact) [FALLBACK]: SW=9000" - Contact fallback success
  - "PPSE (1PAY Contact) [FORCED]: SW=9000" - Forced contact mode
  - Clear indication of which interface worked

**Usage:**
```kotlin
// Force contact mode
viewModel.setContactMode(enabled = true)

// Auto mode (contactless → contact fallback)
viewModel.setContactMode(enabled = false)
```

---

### 4. 📦 Comprehensive TLV Parsing (Enhanced)

**Extract ALL EMV tags - 60-80+ tags per card instead of just 17!**

- **Complete Tag Extraction**
  - `parsedEmvFields` state variable accumulates ALL tags across workflow
  - Every PPSE, AID, GPO, READ_RECORD, GENERATE AC, GET DATA response parsed
  - Template-aware recursive parsing (tags 6F, A5, 70, 77, 80)
  - Nested structure support with depth tracking

- **Intelligent Storage**
  - `buildRealEmvTagsMap()` uses `parsedEmvFields` instead of hardcoded subset
  - ALL tags stored in `EmvCardData.emvTags` map (tag → value)
  - No separate database table needed - tags part of card record
  - `storeTagsToDatabase()` simplified to no-op

- **Enhanced displayParsedData()**
  - Uses EmvTlvParser for comprehensive parsing
  - Displays parse statistics: total tags, valid tags, invalid tags, template depth
  - Shows key tags (PAN, expiry, cryptogram, etc.) with formatting
  - Lists ALL other tags with descriptions from EmvTagDictionary
  - Error and warning reporting for debugging

**Example Output:**
```
📋 GPO Parsed Data:
  ✅ Total: 68 tags extracted
  ✅ Valid tags: 65
  ⚠️ Unknown tags: 3
  🔧 Template depth: 2

  🔑 Key Tags:
    • PAN (5A): 1234567812345678
    • Expiry (5F24): 2512
    • AIP (82): 1F00
    • AFL (94): 08010300

  📦 Other Tags (64):
    • Issuer Country Code (5F28): 0840
    • Currency Code (5F2A): 0840
    ...
```

---

### 5. 🐛 CDOL1 Extraction Bug Fixes (Critical)

**Fixed false CDOL1 matches in RSA certificate data!**

- **Problem:** READ RECORD responses with RSA certificates contained false "8C" byte sequences
- **Solution:** Skip READ RECORD responses when searching for CDOL1 (tag 8C)
- **Validation:** Ensure CDOL1 length ≥4 hex chars (minimum: 1-byte tag + 1-byte length)
- **Result:** Clean CDOL1 extraction from SELECT AID response only

**Before:**
```
Found tag 8C at position 1823 in READ RECORD (RSA cert data) - WRONG!
```

**After:**
```
Skipping READ RECORD response: READ RECORD SFI 2 Record 1
Found tag 8C at position 156 in SELECT AID response - CORRECT!
```

---

## 🔧 Technical Details

### Architecture Changes

- **Thread Safety**: All UI state updates wrapped in `withContext(Dispatchers.Main)`
- **Memory Management**: ROCA results cleared at workflow start to prevent stale data
- **State Management**: `parsedEmvFields` accumulates tags across phases, transferred to `emvTags` map
- **Error Handling**: Comprehensive try-catch blocks with detailed error messages

### New Functions

```kotlin
// ROCA integration
private fun checkRocaVulnerability()
private fun extractRocaAnalysisDetails(): String

// iCVV calculation
private fun calculateUnSize(bitmap: Long, numDigits: Int): Int
private fun calculateDynamicCvvParams(): Map<String, Any>
private fun formatIcvvParams(params: Map<String, Any>): String

// Contact mode
fun setContactMode(enabled: Boolean)

// Enhanced parsing
private fun displayParsedData(phase: String, hexData: String)
```

### Database Schema Changes

**EmvCardData.kt** (12 new fields):

```kotlin
// iCVV/Dynamic CVV (8 fields)
val icvvCapable: Boolean = false
val icvvTrack1Bitmap: String? = null
val icvvTrack1AtcDigits: Int? = null
val icvvTrack1UnSize: Int? = null
val icvvTrack2Bitmap: String? = null
val icvvTrack2UnSize: Int? = null
val icvvStatus: String? = null
val icvvParameters: String? = null

// ROCA Vulnerability (4 fields)
val rocaVulnerable: Boolean = false
val rocaVulnerabilityStatus: String? = null
val rocaAnalysisDetails: String? = null
val rocaCertificatesAnalyzed: Int = 0
```

---

## 📊 Performance Metrics

- **Build Time**: 14 seconds
- **APK Size**: 74 MB (unchanged)
- **Compilation Warnings**: 42 (non-critical, shadowed variables)
- **Lines Changed**: +631, -182 (net +449)
- **Files Modified**: 2 (CardReadingViewModel.kt, EmvCardData.kt)

---

## 🧪 Testing Notes

### Test Scenarios

1. **ROCA Detection**
   - Scan card with RSA certificates (tags 9F46, 92, 9F32, 9F47)
   - Verify real-time detection during workflow
   - Check confidence level in card details
   - Confirm auto-clear on next scan

2. **iCVV Analysis**
   - Scan card with Track bitmaps (tags 9F63, 9F64, 9F65, 9F66)
   - Verify UN size calculations
   - Check formatted parameter string
   - Confirm storage in database

3. **Contact Mode**
   - Test auto fallback: 2PAY fails → 1PAY succeeds
   - Test forced contact: 1PAY only
   - Verify status messages show correct mode
   - Check APDU log for correct PPSE commands

4. **Comprehensive Parsing**
   - Compare tag count: old (17) vs new (60-80+)
   - Verify ALL tags stored in emvTags map
   - Check parse statistics in logs
   - Confirm no data loss

---

## 🚀 Installation Instructions

### Clean Install (Recommended)

```bash
# Uninstall old version
adb uninstall com.nfsp00f33r.app

# Install new version
adb install nf-sp00f33r-nightly-2025-10-10.apk

# Grant NFC permission
adb shell pm grant com.nfsp00f33r.app android.permission.NFC

# Launch app
adb shell am start -n com.nfsp00f33r.app/.MainActivity
```

### Upgrade Install

```bash
# Install over existing (preserves data)
adb install -r nf-sp00f33r-nightly-2025-10-10.apk

# Force stop and restart
adb shell am force-stop com.nfsp00f33r.app
adb shell am start -n com.nfsp00f33r.app/.MainActivity
```

---

## 📝 Known Issues

### Warnings (Non-Critical)

- Shadowed variable names (realStatusWord, tag) - cosmetic only, no functional impact
- Unused parameters in legacy code - scheduled for cleanup
- Redundant initializers - minor optimization opportunity

### Limitations

- ROCA detection requires RSA certificate tags (not all cards have them)
- iCVV analysis requires Track bitmap tags (not all cards support dynamic CVV)
- Contact mode requires NFC hardware that supports both contactless and contact interfaces

---

## 🔮 What's Next

### Immediate (1-2 weeks)

- [ ] UI components to display ROCA analysis results
- [ ] UI components to display iCVV parameters
- [ ] UI toggle for contact/contactless mode
- [ ] Enhanced APDU terminal with tag filtering

### Short-term (2-4 weeks)

- [ ] Grammar-based EMV fuzzing (Phase 4)
- [ ] Advanced ROCA exploitation with parallel factorization
- [ ] Real-time attack visualization dashboard
- [ ] Performance optimization (APDU log caching)

---

## 🤝 Contributors

- **Core Development**: chronlc
- **Security Research**: nf-sp00f33r team
- **Inspiration**: Proxmark3 Iceman, ChAP.py, RoCrack research

---

## 📄 License

Research and educational purposes only. Unauthorized use against systems without explicit permission is prohibited.

---

## 🐛 Bug Reports

Found a bug? Please open an issue on GitHub with:
- Device model and Android version
- Steps to reproduce
- Expected vs actual behavior
- Logcat output (if available)

---

## ⭐ Support

If you find this tool useful for your security research, please star the repository and share your findings!

**GitHub**: https://github.com/chronlc/nf-sp00f33r-pro

---

**Happy Hacking! 🚀**
