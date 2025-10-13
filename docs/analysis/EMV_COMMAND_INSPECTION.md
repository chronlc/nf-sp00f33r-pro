# EMV Command Flow Inspection
**Date:** October 9, 2025  
**Project:** nf-sp00f33r EMV Security Research Platform  
**File:** CardReadingViewModel.kt - executeProxmark3EmvWorkflow()

---

## 📋 COMPLETE EMV TRANSACTION FLOW

### **PHASE 1: SELECT PPSE (Proximity Payment System Environment)**
**Progress:** 10%  
**Purpose:** Discover which payment applications are available on the card

**Command:**
```
00 A4 04 00 0E 32 50 41 59 2E 53 59 53 2E 44 44 46 30 31 00
```

**Breakdown:**
- `00` - CLA (Class byte)
- `A4` - INS (SELECT instruction)
- `04` - P1 (Select by DF name)
- `00` - P2 (First or only occurrence)
- `0E` - Lc (Length = 14 bytes)
- `32 50 41 59 2E 53 59 53 2E 44 44 46 30 31` - ASCII "2PAY.SYS.DDF01" (PPSE name)
- `00` - Le (Expect response data)

**Expected Response:** FCI Template containing Directory Definition File (DDF) with AIDs

**Dynamic Behavior:**
- ✅ Extracts real AIDs from PPSE response using `extractAidsFromPpseResponse()`
- ✅ Stores extracted AIDs in `extractedAids` list
- ✅ Falls back to common AIDs only if PPSE fails

**Status Words:**
- `9000` - Success, PPSE found
- `6A82` - File not found (card doesn't support PPSE)
- Other - PPSE selection failed

---

### **PHASE 2: SELECT AID (Application Identifier)**
**Progress:** 20%  
**Purpose:** Select specific payment application (Visa/MasterCard/Amex/etc)

**Command Template:**
```
00 A4 04 00 [AID_LENGTH] [AID_BYTES]
```

**Example Commands:**
```
00 A4 04 00 07 A0 00 00 00 04 10 10    # MasterCard
00 A4 04 00 07 A0 00 00 00 03 10 10    # Visa
00 A4 04 00 06 A0 00 00 00 25 01       # Amex
00 A4 04 00 07 A0 00 00 01 52 30 10    # Discover
```

**Breakdown:**
- `00` - CLA
- `A4` - INS (SELECT)
- `04` - P1 (Select by DF name)
- `00` - P2 (First or only occurrence)
- `[LENGTH]` - Length of AID (6-16 bytes)
- `[AID]` - Application Identifier

**Dynamic Behavior:**
- ✅ Uses AIDs extracted from PPSE response (NOT hardcoded list)
- ✅ Converts hex string AIDs to ByteArray: `.chunked(2).map { it.toInt(16).toByte() }.toByteArray()`
- ✅ Only falls back to common AIDs if PPSE failed
- ✅ Tries each AID until one succeeds
- ✅ Extracts FCI (File Control Information) from successful response

**Status Words:**
- `9000` - Success, application selected
- `6A82` - Application not found
- `6A81` - Application not supported
- Other - Selection failed

---

### **PHASE 3: GET PROCESSING OPTIONS (GPO)**
**Progress:** 40%  
**Purpose:** Initialize transaction and retrieve AIP (Application Interchange Profile) and AFL (Application File Locator)

**Command Template:**
```
80 A8 00 00 [PDOL_DATA_LENGTH] 83 [PDOL_DATA_LENGTH-2] [PDOL_DATA...]
```

**Example (with PDOL):**
```
80 A8 00 00 1D 83 1B [PDOL_DATA_29_BYTES]
```

**Example (without PDOL):**
```
80 A8 00 00 02 83 00
```

**Breakdown:**
- `80` - CLA (Proprietary class)
- `A8` - INS (GET PROCESSING OPTIONS)
- `00` - P1
- `00` - P2
- `[LENGTH]` - Length of data field
- `83` - Tag for command template
- `[PDOL_LENGTH]` - Length of PDOL data
- `[PDOL_DATA]` - Built PDOL data based on card requirements

**Dynamic PDOL Building (buildPdolData):**
- ✅ Extracts PDOL from AID selection response using `extractPdolFromAllResponses()`
- ✅ Parses PDOL structure with `EmvTlvParser.parseDol(pdolData)`
- ✅ Builds dynamic PDOL data for each requested tag:

**PDOL Tags Supported:**
- `9F37` (4 bytes) - **Unpredictable Number** - SecureRandom cryptographically secure
- `9A` (3 bytes) - **Transaction Date** - YYMMDD format from SimpleDateFormat
- `9C` (1 byte) - **Transaction Type** - 0x00 (goods/services)
- `5F2A` (2 bytes) - **Currency Code** - 0x0840 (USD)
- `9F02` (6 bytes) - **Amount Authorized** - BCD format, 000000000000
- `9F03` (6 bytes) - **Amount Other** - Usually 0
- `9F1A` (2 bytes) - **Terminal Country** - 0x0840 (USA)
- `9F33` (3 bytes) - **Terminal Capabilities** - 0xE0F0C8 (full POS capabilities)
- `9F35` (1 byte) - **Terminal Type** - 0x22 (attended online-only)
- `9F40` (5 bytes) - **Additional Terminal Capabilities** - 0xF000F00000

**Expected Response:**
- **Format 1:** `80 [length] [AIP 2 bytes] [AFL variable length]`
- **Format 2:** `77 [length] [TLV data with tags 82 (AIP) and 94 (AFL)]`

**Dynamic Behavior:**
- ✅ Logs actual GPO command with built PDOL data (NOT hardcoded)
- ✅ Parses AIP with `EmvTlvParser.parseAip()` to detect:
  - SDA (Static Data Authentication)
  - DDA (Dynamic Data Authentication)
  - CDA (Combined Data Authentication)
  - CVM (Cardholder Verification Method) support
  - MSD (Magnetic Stripe Data) support
- ✅ Parses AFL with `EmvTlvParser.parseAfl()` for intelligent record reading

**Status Words:**
- `9000` - Success, AIP/AFL returned
- `6985` - Conditions not satisfied
- `6A88` - Referenced data not found

---

### **PHASE 4: READ RECORDS (Application Data)**
**Progress:** 60%  
**Purpose:** Read application data from files specified by AFL

**Command Template:**
```
00 B2 [RECORD] [P2] 00
```

**P2 Calculation:**
```
P2 = (SFI << 3) | 0x04
```

**Example Commands:**
```
00 B2 01 14 00    # Read SFI 1, Record 1 (P2=0x14 = (2<<3)|4)
00 B2 02 14 00    # Read SFI 1, Record 2
00 B2 01 0C 00    # Read SFI 2, Record 1 (P2=0x0C = (1<<3)|4)
00 B2 01 1C 00    # Read SFI 3, Record 1 (P2=0x1C = (3<<3)|4)
```

**Breakdown:**
- `00` - CLA
- `B2` - INS (READ RECORD)
- `[RECORD]` - Record number (1-based)
- `[P2]` - (SFI << 3) | 0x04
- `00` - Le (Expect full record)

**Dynamic AFL-Based Reading:**
- ✅ Extracts AFL from GPO response using `extractAflFromAllResponses()`
- ✅ Parses AFL with `EmvTlvParser.parseAfl()` to get list of AFL entries
- ✅ Each AFL entry contains:
  - `sfi` - Short File Identifier
  - `startRecord` - First record to read
  - `endRecord` - Last record to read
  - `numRecordsInvolved` - Records used in offline data authentication
- ✅ Iterates through ALL records specified by AFL (no hardcoded records)
- ✅ Falls back to common record locations only if no AFL found

**Fallback Records (only if no AFL):**
```
SFI 1: Records 1, 2, 3
SFI 2: Records 1, 2
SFI 3: Records 1, 2
```

**Expected Data in Records:**
- `5A` - Primary Account Number (PAN)
- `57` - Track 2 Equivalent Data
- `5F20` - Cardholder Name
- `5F24` - Application Expiration Date
- `5F25` - Application Effective Date
- `5F28` - Issuer Country Code
- `82` - Application Interchange Profile (AIP)
- `84` - Dedicated File Name
- `8C` - Card Risk Management Data Object List 1 (CDOL1)
- `8D` - Card Risk Management Data Object List 2 (CDOL2)
- `8E` - Cardholder Verification Method (CVM) List
- `8F` - Certification Authority Public Key Index
- `90` - Issuer Public Key Certificate
- `92` - Issuer Public Key Remainder
- `93` - Signed Static Application Data
- `9F07` - Application Usage Control
- `9F08` - Application Version Number
- `9F0D` - Issuer Action Code - Default
- `9F0E` - Issuer Action Code - Denial
- `9F0F` - Issuer Action Code - Online
- `9F10` - Issuer Application Data
- `9F32` - Issuer Public Key Exponent
- `9F46` - ICC Public Key Certificate
- `9F47` - ICC Public Key Exponent
- `9F48` - ICC Public Key Remainder
- `9F4A` - Static Data Authentication Tag List

**Dynamic Behavior:**
- ✅ Parses ALL EMV tags from each record using `extractDetailedEmvData()`
- ✅ Extracts 60+ EMV tags with descriptions
- ✅ Updates `parsedEmvFields` with all discovered data
- ✅ Logs detailed EMV data for each record

**Status Words:**
- `9000` - Success, record read
- `6A83` - Record not found
- `6A82` - File not found

---

### **PHASE 5: GET DATA (Specific EMV Tags)**
**Progress:** 80%  
**Purpose:** Retrieve specific EMV data objects

**Command Template:**
```
80 CA [P1] [P2] 00
```

**Example Commands:**
```
80 CA 9F 13 00    # GET DATA 9F13 (Last Online ATC)
80 CA 9F 17 00    # GET DATA 9F17 (PIN Try Counter)
80 CA 9F 36 00    # GET DATA 9F36 (Application Transaction Counter)
80 CA 9F 4F 00    # GET DATA 9F4F (Log Format)
```

**Breakdown:**
- `80` - CLA (Proprietary)
- `CA` - INS (GET DATA)
- `[P1]` - First byte of tag (e.g., 9F)
- `[P2]` - Second byte of tag (e.g., 13)
- `00` - Le (Expect data)

**Tags Retrieved:**
- `9F13` - Last Online ATC (Application Transaction Counter)
- `9F17` - PIN Try Counter (remaining PIN attempts)
- `9F36` - Application Transaction Counter (ATC)
- `9F4F` - Log Format (transaction log structure)

**Status Words:**
- `9000` - Success, data returned
- `6A88` - Referenced data not found
- `6A81` - Function not supported

---

### **PHASE 6: COMPLETION**
**Progress:** 100%  
**Purpose:** Finalize scan and create card profile

**Actions:**
- ✅ Closes IsoDep connection
- ✅ Creates comprehensive EmvCardData with all extracted fields
- ✅ Creates VirtualCard for UI display
- ✅ Updates scan state to "Complete"

---

## 🎯 DYNAMIC vs STATIC BEHAVIOR

### ✅ **DYNAMIC (Real EMV Transaction)**
1. **PPSE Response Parsing** - Extracts real AIDs from card's PPSE response
2. **AID Selection** - Uses extracted AIDs, not hardcoded list
3. **PDOL Building** - Parses card's PDOL requirements and builds data dynamically
4. **GPO Command** - Logs actual command with built PDOL data
5. **AIP Analysis** - Detects SDA/DDA/CDA capabilities from card
6. **AFL Parsing** - Extracts AFL and reads exact SFI/record combinations
7. **Record Reading** - Reads only records specified by AFL
8. **TLV Parsing** - Recursively parses nested TLV structures

### ❌ **STATIC (Hardcoded - REMOVED)**
1. ~~Common AID list~~ - Now only used as fallback if PPSE fails
2. ~~Hardcoded GPO command~~ - Fixed to log actual command
3. ~~Fixed record locations~~ - Now only used if no AFL found

---

## 📊 COMMAND SUMMARY

| Phase | Command | CLA | INS | Purpose | Dynamic |
|-------|---------|-----|-----|---------|---------|
| 1 | SELECT PPSE | `00` | `A4` | Discover applications | ✅ Parses AIDs |
| 2 | SELECT AID | `00` | `A4` | Select application | ✅ Uses PPSE AIDs |
| 3 | GET PROCESSING OPTIONS | `80` | `A8` | Initialize transaction | ✅ Builds PDOL |
| 4 | READ RECORD | `00` | `B2` | Read application data | ✅ AFL-based |
| 5 | GET DATA | `80` | `CA` | Get specific tags | ✅ Multiple tags |

---

## 🔍 KEY IMPROVEMENTS IMPLEMENTED

1. **Dynamic AID Extraction** - Real PPSE parsing instead of static list
2. **Dynamic PDOL Building** - Builds data based on card requirements
3. **Actual GPO Logging** - Shows real command with PDOL data
4. **AFL-Based Record Reading** - Intelligent SFI/record iteration
5. **AIP Capability Detection** - Detects SDA/DDA/CDA support
6. **Comprehensive Tag Parsing** - 60+ EMV tags extracted
7. **Real-Time APDU Display** - Newest commands at top

---

## 📝 NOTES

- All commands follow ISO 7816-4 and EMV Book 3 specifications
- PDOL data uses cryptographically secure random for unpredictable number (9F37)
- Transaction date uses current system date in YYMMDD format
- AFL parsing ensures all records are read (no blind guessing)
- Status word validation provides live transaction feedback
- Thread-safe UI updates using `withContext(Dispatchers.Main)`

---

**Status:** ✅ All EMV commands inspected and documented  
**Commit:** 74795ce - Fixed APDU terminal reversal + GPO logging  
**Next:** Test dynamic EMV workflow with real card
