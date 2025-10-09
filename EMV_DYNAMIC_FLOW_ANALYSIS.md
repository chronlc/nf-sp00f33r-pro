# EMV Dynamic Flow Analysis - BER-TLV & PDOL Deep Dive

**Date:** October 9, 2025  
**Status:** Enhanced with dynamic capabilities based on template parsing

---

## 🎯 Overview

This document explains the dynamic EMV workflow improvements, BER-TLV structure handling, and PDOL data injection strategy.

---

## 📊 BER-TLV Structure Explained

### Standard BER-TLV Format

```
[TAG] [LENGTH] [VALUE]
```

**Tag Encoding:**
- **1-byte tags:** First byte & 0x1F != 0x1F (e.g., `5A`, `57`, `82`, `95`)
- **2-byte tags:** First byte & 0x1F == 0x1F (e.g., `9F02`, `9F37`, `5F20`)

**Length Encoding:**
- **Single-byte (0x00-0x7F):** Direct value (0-127)
- **Multi-byte (0x80-0xFF):**
  - First byte: 0x80 + number of length bytes
  - Example: `0x81 0xA5` = 165 bytes
  - Example: `0x82 0x01 0x23` = 291 bytes

**Constructed Tags (Templates):**
- Bit 6 set in first byte (firstByte & 0x20 != 0)
- Known templates: `6F`, `A5`, `70`, `77`, `80`, `61`, `BF0C`
- Contain nested TLV structures that must be recursively parsed

---

## 🔧 PDOL Special Structure

### PDOL is Different!

**PDOL (Processing Data Object List) - Tag 9F38:**

```
Card provides:  [TAG1][LEN1][TAG2][LEN2][TAG3][LEN3]...
Terminal sends: [VALUE1][VALUE2][VALUE3]... (NO tags/lengths!)
```

**Example:**
```
Card PDOL: 9F66 04 9F02 06 9F03 06 9F1A 02 95 05
           |    |  |    |  |    |  |    |  |  |
           |    |  |    |  |    |  |    |  |  +-- TVR: 5 bytes
           |    |  |    |  |    |  |    +-------- Country: 2 bytes
           |    |  |    |  |    +--------------- Amount Other: 6 bytes
           |    |  |    +----------------------- Amount Auth: 6 bytes
           |    +-------------------------------- TTQ: 4 bytes
           +------------------------------------- Tag for TTQ

Terminal response: F6200080 000000001000 000000000000 0840 8000000000
                   |        |            |            |    |
                   TTQ      Amount Auth  Amount Other Ctry TVR
                   (4 bytes)(6 bytes)    (6 bytes)    (2)  (5 bytes)
```

---

## 💉 PDOL Data Injection Strategy

### Current Implementation

#### Dynamic Values ✅

| Tag | Name | Value | Notes |
|-----|------|-------|-------|
| `9A` | Transaction Date | YYMMDD | **Generated from system time** |
| `9F37` | Unpredictable Number | 4 random bytes | **SecureRandom - CRITICAL for security** |

#### Configurable Static Values 🔧

| Tag | Name | Current Value | Purpose |
|-----|------|---------------|---------|
| `9F66` | Terminal Transaction Qualifiers | `F6 20 00 80` | Terminal capabilities flags |
| `9F02` | Amount Authorized | `00 00 00 00 10 00` | $1.00 in BCD format |
| `9F03` | Amount Other | `00 00 00 00 00 00` | $0.00 (cashback/surcharge) |
| `9F1A` | Terminal Country Code | `08 40` | USA (840) ISO 3166-1 |
| `95` | Terminal Verification Results | `80 00 00 00 00` | TVR byte 1 bit 8 set |
| `5F2A` | Transaction Currency Code | `08 40` | USD (840) ISO 4217 |
| `9C` | Transaction Type | `00` | Purchase (0x00) |
| `9F35` | Terminal Type | `22` | Attended terminal, online |
| `9F40` | Additional Terminal Capabilities | `F0 00 F0 A0 01` | Cash, goods, services, etc. |
| `9F33` | Terminal Capabilities | `E0 F8 C8` | Card input, CVM, security |

### Security Considerations

**🔴 CRITICAL:** `9F37` (Unpredictable Number)
- **OLD:** Hardcoded `12 34 56 78` ❌
- **NEW:** `SecureRandom()` generated ✅
- **Why:** Used in cryptogram generation - MUST be random for security
- **Impact:** Prevents replay attacks and cryptogram prediction

---

## 🎯 Dynamic Flow Enhancements

### 1. AIP (Application Interchange Profile) Parsing

**Tag:** `82` (2 bytes)

#### Byte 1 Analysis:
```
Bit 7: RFU
Bit 6: SDA Supported (Static Data Authentication)
Bit 5: DDA Supported (Dynamic Data Authentication)
Bit 4: Cardholder Verification Supported
Bit 3: Terminal Risk Management Required
Bit 2: Issuer Authentication Supported
Bit 1: RFU
Bit 0: CDA Supported (Combined DDA/AC Generation)
```

#### Flow Strategy Based on AIP:

```kotlin
if (CDA_SUPPORTED) {
    // Cryptogram in READ RECORD responses (tag 9F26 in template 77)
    strategy = "Read records with CDA validation"
}
else if (DDA_SUPPORTED) {
    // Will need INTERNAL AUTHENTICATE command
    strategy = "Read records + INTERNAL AUTHENTICATE for dynamic auth"
}
else if (SDA_SUPPORTED) {
    // Static certificates only
    strategy = "Read records + verify static signatures"
}
```

### 2. AFL (Application File Locator) Dynamic Reading

**Tag:** `94` (variable length, 4-byte entries)

**Format per entry:**
```
Byte 1: SFI (upper 5 bits)
Byte 2: First record number
Byte 3: Last record number
Byte 4: Number of records for offline data authentication
```

**Example:**
```
AFL: 08 01 03 00 10 01 01 00
     |  |  |  |  |  |  |  |
     |  |  |  |  |  |  |  +-- 0 records for offline auth
     |  |  |  |  |  |  +----- Record 1
     |  |  |  |  |  +-------- Record 1
     |  |  |  |  +----------- SFI 2 (0x10 >> 3)
     |  |  |  +-------------- 0 records for offline auth
     |  |  +----------------- Record 3 (last)
     |  +-------------------- Record 1 (first)
     +----------------------- SFI 1 (0x08 >> 3)

Result: Read SFI 1 Records 1,2,3 and SFI 2 Record 1
```

### 3. CDOL (Card Data Object List) Support

**Tags:** `8C` (CDOL1), `8D` (CDOL2)

**Purpose:** Build data for GENERATE AC command (Phase 6)

**Similar to PDOL but for cryptogram generation:**
```
GENERATE AC command:
  80 AE [RefControl] 00 [Lc] [CDOL data]
  
RefControl:
  0x80 = AAC (decline)
  0x40 = TC (approve offline)
  0x00 = ARQC (go online)
```

**CDOL Data Strategy:**
- Reuse unpredictable number from PDOL for consistency
- Use ATC (Application Transaction Counter) from card if available
- Include amounts, dates, country/currency codes
- Add Terminal Verification Results (calculated)

---

## 📈 Template-Aware Parsing Benefits

### Before (Regex-based):
```kotlin
// Missed nested tags in templates
val track2 = "57([0-9A-F]{2})([0-9A-F]+)".toRegex()
// Only found top-level tags
```

### After (Recursive TLV):
```kotlin
fun parseTlvData(hexData: String): Map<String, String> {
    if (isConstructed || tag in knownTemplateTags) {
        tags[tag] = value  // Store template
        val nestedTags = parseTlvData(value)  // Recurse!
        nestedTags.forEach { tags[it.key] = it.value }
    }
}
```

**Result:** Extracted 20+ tags including certificates from nested templates

---

## 🔄 Enhanced Workflow Phases

### Phase 1: PPSE Selection
- Select payment system environment
- Extract AID list from FCI template (tag 6F)

### Phase 2: AID Selection  
- Try each AID from PPSE
- **Extract PDOL requirement (tag 9F38)** ✅

### Phase 3: GET PROCESSING OPTIONS
- **Build PDOL data dynamically** ✅
- **Use random unpredictable number** ✅
- **Encode proper BER-TLV length** ✅
- Parse AIP for card capabilities ✅
- Extract AFL for record reading ✅

### Phase 4: READ APPLICATION DATA
- **Follow AFL exactly** ✅
- Parse template tag 70 recursively ✅
- Extract all nested EMV tags ✅
- Fallback to common records if no AFL ✅

### Phase 5: GET DATA
- Request certificate tags
- Modern cards return 6A81 (not supported) ✅
- Certificates actually in record templates ✅

### Phase 6: GENERATE AC (Future)
- **Build CDOL data** ✅ (implemented)
- Request cryptogram (TC/ARQC/AAC)
- Validate cryptogram
- Check ROCA vulnerability

---

## 🎨 Visual Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ PHASE 2: SELECT AID                                         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Response (FCI Template - Tag 6F):                       │ │
│ │   6F [FCI]                                              │ │
│ │   ├─ 84 [DF Name / AID]                                 │ │
│ │   └─ A5 [Proprietary Template]                          │ │
│ │      ├─ 50 [Application Label]                          │ │
│ │      └─ 9F38 [PDOL] ← EXTRACT THIS!                     │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 3: BUILD PDOL DATA                                    │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ PDOL: 9F66 04 9F02 06 9F37 04                           │ │
│ │       ↓     ↓  ↓     ↓  ↓     ↓                         │ │
│ │       TTQ   4  Amt   6  UN    4                         │ │
│ │                                                           │ │
│ │ Terminal builds:                                         │ │
│ │   F6200080 + 000000001000 + [4 RANDOM BYTES]            │ │
│ │   ↑          ↑               ↑                           │ │
│ │   TTQ value  Amount value    SecureRandom() ← DYNAMIC!  │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 3: SEND GPO                                           │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Command: 80 A8 00 00 [Lc] 83 [Len] [PDOL Data]         │ │
│ │                       ↑    ↑   ↑    ↑                   │ │
│ │                       │    │   │    Actual values       │ │
│ │                       │    │   Length of data           │ │
│ │                       │    Tag 83 (PDOL wrapper)        │ │
│ │                       Total length (83 + len + data)    │ │
│ └─────────────────────────────────────────────────────────┘ │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ Response (Tag 77 or 80):                                │ │
│ │   77 [Response Message Template]                        │ │
│ │   ├─ 82 [AIP] ← PARSE for capabilities!                │ │
│ │   ├─ 94 [AFL] ← Use to read records!                    │ │
│ │   └─ 9F26 [Cryptogram] (if CDA)                         │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ PHASE 4: READ RECORDS (AFL-driven)                         │
│ ┌─────────────────────────────────────────────────────────┐ │
│ │ AFL: 08 01 03 00 → Read SFI 1, Records 1-3             │ │
│ │                                                           │ │
│ │ Each record response (Template 70):                     │ │
│ │   70 [Record Template]                                  │ │
│ │   ├─ 5A [PAN]                                           │ │
│ │   ├─ 57 [Track 2]                                       │ │
│ │   ├─ 5F20 [Cardholder Name]                             │ │
│ │   ├─ 90 [Issuer Public Key Certificate] ← 176 bytes!   │ │
│ │   ├─ 9F32 [Issuer Public Key Exponent]                  │ │
│ │   └─ 8C [CDOL1] ← Extract for Phase 6!                  │ │
│ └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## ✅ What's Dynamic Now

1. ✅ **PDOL Data Generation**
   - Random unpredictable number (SecureRandom)
   - Current date/time
   - Proper BER-TLV length encoding
   - Configurable amounts, country, currency

2. ✅ **AIP-Based Flow Strategy**
   - Detects SDA/DDA/CDA support
   - Adjusts authentication strategy
   - Logs capabilities for debugging

3. ✅ **AFL-Driven Record Reading**
   - Parses AFL from GPO response
   - Reads exact SFI/record combinations
   - Fallback to common records if no AFL

4. ✅ **Template-Aware Parsing**
   - Recursive TLV parsing for nested data
   - Recognizes constructed tags
   - Extracts 20+ EMV tags including certificates

5. ✅ **CDOL Support**
   - Extracts CDOL1/CDOL2 from records
   - Builds CDOL data for GENERATE AC
   - Reuses unpredictable number for consistency

---

## 🚀 Future Enhancements

### User-Configurable Terminal Data
```kotlin
data class TerminalConfig(
    val amount: Long = 100,  // cents
    val currency: String = "USD",
    val country: String = "US",
    val terminalType: TerminalType = TerminalType.ATTENDED_ONLINE
)
```

### GENERATE AC Implementation
```kotlin
// Phase 6: Request cryptogram
val cdolData = buildCdolData(cdol1Tags, allTags)
val generateAcCommand = buildGenerateAcCommand(
    refControl = 0x40,  // Request TC (offline approval)
    cdolData = cdolData
)
```

### Dynamic CVM (Cardholder Verification Method)
```kotlin
// Parse CVM list (tag 8E) and determine verification strategy
val cvmList = parseCvmList(allTags["8E"])
val requiredCvm = selectCvm(cvmList, amount, terminalCapabilities)
```

---

## 📝 Summary

**Key Improvements:**
- 🎲 Random unpredictable number for security
- 📊 Proper BER-TLV length encoding
- 🎯 AIP parsing for dynamic flow decisions
- 📋 AFL-based intelligent record reading
- 🔧 CDOL support for cryptogram generation
- 🔄 Template-aware recursive parsing
- 📝 Comprehensive logging for debugging

**Result:** More realistic, secure, and flexible EMV testing platform that adapts to different card types and capabilities.

---

**Last Updated:** October 9, 2025  
**Next Steps:** Test with multiple card types, implement GENERATE AC, add user-configurable terminal data
