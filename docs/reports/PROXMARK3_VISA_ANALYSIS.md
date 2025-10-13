# Proxmark3 VISA EMV Analysis

**Repository:** github.com/RfidResearchGroup/proxmark3  
**Focus:** EMV contactless card reading implementation  
**Main Files:** cmdemv.c, emvcore.c, emvcore.h  
**Date Analyzed:** October 11, 2025  

---

## 📋 Executive Summary

Proxmark3's EMV implementation is a **general-purpose contactless EMV reader** with comprehensive support for standard EMV workflow, DDA/CDA authentication, and ROCA vulnerability testing. Unlike RFIDIOt (which is attack-focused), Proxmark3 emphasizes **complete EMV data extraction** for analysis and research.

**Key Similarities with nf-sp00f33r:**
- Both follow standard EMV workflow (PPSE → AID → GPO → Records → GET DATA)
- Both support comprehensive data collection (not attack-specific)
- Both use standard APDU commands (no proprietary VISA extensions)

**Key Difference from RFIDIOt:**
- Proxmark3: Research/forensics tool, vendor-agnostic
- RFIDIOt: Attack-focused tool, VISA-specific dynamic CVV
- nf-sp00f33r: Android research platform, comprehensive data extraction

---

## 🎯 Proxmark3 EMV Workflow

### **Standard EMV Command Sequence** (cmdemv.c)

```c
// From cmdemv.c line 2198+
// 1. SELECT PPSE (Payment System Environment)
res = EMVSelectPSE(channel, true, true, 2, buf, sizeof(buf), &len, &sw);

// 2. SELECT AID (Application Identifier)
res = EMVSelect(channel, false, true, AID, AIDlen, buf, sizeof(buf), &len, &sw, tlvRoot);

// 3. GET PROCESSING OPTIONS (GPO)
pdol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f38, NULL), tlvRoot, 0x83);
res = EMVGPO(channel, true, pdol_data_tlv, pdol_len, buf, sizeof(buf), &len, &sw, tlvRoot);

// 4. READ RECORDS from AFL
const struct tlv *AFL = tlvdb_get(tlvRoot, 0x94, NULL);
for (int i = 0; i < AFL->len / 4; i++) {
    uint8_t SFI = AFL->value[i * 4 + 0] >> 3;
    uint8_t SFIstart = AFL->value[i * 4 + 1];
    uint8_t SFIend = AFL->value[i * 4 + 2];
    uint8_t SFIoffline = AFL->value[i * 4 + 3];
    
    for (int n = SFIstart; n <= SFIend; n++) {
        res = EMVReadRecord(channel, true, SFI, n, buf, sizeof(buf), &len, &sw, tlvRoot);
    }
}

// 5. GENERATE AC (if CDOL present)
res = EMVAC(channel, true, RefControl, CDOL, CDOLLen, buf, sizeof(buf), &len, &sw, tlvRoot);

// 6. GET DATA (various tags)
res = EMVGetData(channel, true, tag, buf, sizeof(buf), &len, &sw, tlvRoot);

// 7. INTERNAL AUTHENTICATE (for DDA/CDA)
res = EMVInternalAuthenticate(channel, true, DDOL, DDOLLen, buf, sizeof(buf), &len, &sw, tlvRoot);
```

---

## 🔍 Key APDU Commands (emvcore.c)

### **1. SELECT PPSE/PSE**

```c
// emvcore.c line 297-315
int EMVSelectPSE(Iso7816CommandChannel channel, bool ActivateField, bool LeaveFieldON, 
                 uint8_t PSENum, uint8_t *Result, size_t MaxResultLen, size_t *ResultLen, uint16_t *sw) {
    uint8_t buf[APDU_AID_LEN] = {0};
    *ResultLen = 0;
    int len = 0;
    
    switch (PSENum) {
        case 1: // PSE (contact)
            param_gethex_to_eol("1PAY.SYS.DDF01", 0, buf, sizeof(buf), &len);
            break;
        case 2: // PPSE (contactless)
            param_gethex_to_eol("2PAY.SYS.DDF01", 0, buf, sizeof(buf), &len);
            break;
    }
    
    return Iso7816Select(channel, ActivateField, LeaveFieldON, buf, len, Result, MaxResultLen, ResultLen, sw);
}
```

**APDU Structure:**
- **CLA:** 0x00
- **INS:** 0xA4 (SELECT)
- **P1:** 0x04 (Select by name)
- **P2:** 0x00
- **Data:** "2PAY.SYS.DDF01" (14 bytes)
- **Le:** 0x00

---

### **2. GET PROCESSING OPTIONS (GPO)**

```c
// emvcore.c line 565-580
int EMVGPO(Iso7816CommandChannel channel, bool LeaveFieldON, uint8_t *PDOL, size_t PDOLLen, 
           uint8_t *Result, size_t MaxResultLen, size_t *ResultLen, uint16_t *sw, struct tlvdb *tlv) {
    return EMVExchangeEx(channel, false, LeaveFieldON, 
                        (sAPDU_t) {0x80, 0xa8, 0x00, 0x00, PDOLLen, PDOL}, 
                        true, Result, MaxResultLen, ResultLen, sw, tlv);
}
```

**APDU Structure:**
- **CLA:** 0x80
- **INS:** 0xA8 (GET PROCESSING OPTIONS)
- **P1:** 0x00
- **P2:** 0x00
- **Data:** PDOL data (variable length)
- **Le:** Requested

**PDOL Processing (cmdemv.c line 1571):**
```c
pdol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f38, NULL), tlvRoot, 0x83);
```

---

### **3. READ RECORD**

```c
// emvcore.c (via EMVReadRecord wrapper)
int EMVReadRecord(Iso7816CommandChannel channel, bool LeaveFieldON, uint8_t SFI, uint8_t SFIrec, 
                  uint8_t *Result, size_t MaxResultLen, size_t *ResultLen, uint16_t *sw, struct tlvdb *tlv) {
    return EMVExchangeEx(channel, false, LeaveFieldON, 
                        (sAPDU_t) {0x00, 0xB2, SFIrec, (SFI << 3) | 0x04, 0, NULL}, 
                        true, Result, MaxResultLen, ResultLen, sw, tlv);
}
```

**APDU Structure:**
- **CLA:** 0x00
- **INS:** 0xB2 (READ RECORD)
- **P1:** Record number (1-based)
- **P2:** (SFI << 3) | 0x04 (SFI in bits 7-3, P2 bit 2 = 1 for "read record")
- **Data:** None
- **Le:** Requested

**AFL-Driven Reading (cmdemv.c line 2631-2653):**
```c
const struct tlv *AFL = tlvdb_get(tlvRoot, 0x94, NULL);
while (AFL && AFL->len) {
    for (int i = 0; i < AFL->len / 4; i++) {
        uint8_t SFI = AFL->value[i * 4 + 0] >> 3;
        uint8_t SFIstart = AFL->value[i * 4 + 1];
        uint8_t SFIend = AFL->value[i * 4 + 2];
        uint8_t SFIoffline = AFL->value[i * 4 + 3];  // For offline authentication
        
        for (int n = SFIstart; n <= SFIend; n++) {
            res = EMVReadRecord(channel, true, SFI, n, buf, sizeof(buf), &len, &sw, tlvRoot);
            // TLV parse and add to tree
        }
    }
}
```

---

### **4. GET DATA**

```c
// emvcore.c line 588-593
int EMVGetData(Iso7816CommandChannel channel, bool LeaveFieldON, uint16_t tag, 
               uint8_t *Result, size_t MaxResultLen, size_t *ResultLen, uint16_t *sw, struct tlvdb *tlv) {
    return EMVExchangeEx(channel, false, LeaveFieldON, 
                        (sAPDU_t) {0x80, 0xCA, ((tag >> 8) & 0xFF), (tag & 0xFF), 0, NULL}, 
                        true, Result, MaxResultLen, ResultLen, sw, tlv);
}
```

**APDU Structure:**
- **CLA:** 0x80
- **INS:** 0xCA (GET DATA)
- **P1:** Tag byte 1 (e.g., 0x9F for 9F36)
- **P2:** Tag byte 2 (e.g., 0x36 for 9F36)
- **Data:** None
- **Le:** Requested

**Common Tags Queried:**
- 9F17 (PIN Try Counter)
- 9F36 (ATC - Application Transaction Counter)
- 9F13 (Last Online ATC)
- 9F4F (Log Format)

---

### **5. INTERNAL AUTHENTICATE (DDA/CDA)**

```c
// emvcore.c line 600-608
int EMVInternalAuthenticate(Iso7816CommandChannel channel, bool LeaveFieldON, 
                            uint8_t *DDOL, size_t DDOLLen, 
                            uint8_t *Result, size_t MaxResultLen, size_t *ResultLen, uint16_t *sw, struct tlvdb *tlv) {
    return EMVExchangeEx(channel, false, LeaveFieldON, 
                        (sAPDU_t) {0x00, 0x88, 0x00, 0x00, DDOLLen, DDOL}, 
                        true, Result, MaxResultLen, ResultLen, sw, tlv);
}
```

**APDU Structure:**
- **CLA:** 0x00
- **INS:** 0x88 (INTERNAL AUTHENTICATE)
- **P1:** 0x00
- **P2:** 0x00
- **Data:** DDOL data (Dynamic Data Object List)
- **Le:** Requested

**Usage (cmdemv.c line 1199+):**
```c
// Generate challenge
res = EMVGenerateChallenge(channel, leaveSignalON, buf, sizeof(buf), &len, &sw, NULL);

// Build DDOL data (typically includes challenge + other transaction data)
struct tlv *ddol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f49, NULL), tlvRoot, 0x00);

// Send INTERNAL AUTHENTICATE with DDOL data
res = EMVInternalAuthenticate(channel, leaveSignalON, ddol_data, ddol_len, buf, sizeof(buf), &len, &sw, tlvRoot);

// Response contains SDAD (Signed Dynamic Application Data) for DDA/CDA verification
```

---

### **6. GENERATE AC (Application Cryptogram)**

```c
// emvcore.c (EMVAC wrapper)
int EMVAC(Iso7816CommandChannel channel, bool LeaveFieldON, uint8_t RefControl, 
          uint8_t *CDOL, size_t CDOLLen, 
          uint8_t *Result, size_t MaxResultLen, size_t *ResultLen, uint16_t *sw, struct tlvdb *tlv) {
    return EMVExchangeEx(channel, false, LeaveFieldON, 
                        (sAPDU_t) {0x80, 0xAE, RefControl, 0x00, CDOLLen, CDOL}, 
                        true, Result, MaxResultLen, ResultLen, sw, tlv);
}
```

**APDU Structure:**
- **CLA:** 0x80
- **INS:** 0xAE (GENERATE AC)
- **P1:** Reference Control Parameter
  - 0x00: AAC (Application Authentication Cryptogram - decline)
  - 0x40: TC (Transaction Certificate - approve offline)
  - 0x80: ARQC (Authorization Request Cryptogram - go online)
- **P2:** 0x00
- **Data:** CDOL1 data (Card Risk Management Data Object List 1)
- **Le:** Requested

---

## 🆚 Comparison: Proxmark3 vs RFIDIOt vs nf-sp00f33r

### **Workflow Comparison:**

| Phase | Proxmark3 | RFIDIOt (VISA) | nf-sp00f33r |
|-------|-----------|----------------|-------------|
| **SELECT PPSE** | ✅ Standard | ✅ Standard | ✅ Standard |
| **SELECT AID** | ✅ Multi-AID | ✅ VISA-only | ✅ Multi-AID |
| **GET PROCESSING OPTIONS** | ✅ PDOL via dol_process() | ✅ VISA TTQ config | ✅ Dynamic PDOL |
| **READ RECORDS** | ✅ **AFL-based ONLY** | ❌ Minimal | ✅ **AFL + Extended Scan** |
| **INTERNAL AUTHENTICATE** | ✅ **DDA/CDA support** | ✅ **VISA-specific** | ❌ Not implemented |
| **GENERATE AC** | ✅ Full CDOL support | ❌ Skipped | ✅ ARQC attempted |
| **GET DATA** | ✅ Tag-specific | ❌ Minimal | ✅ **12 tags** |
| **Transaction Logs** | ❌ Not implemented | ❌ Not implemented | ✅ **Phase 7 complete** |
| **COMPUTE CHECKSUM** | ❌ Not implemented | ✅ **VISA-specific** | ❌ Not implemented |

---

### **Data Collection Comparison:**

| Data Type | Proxmark3 | RFIDIOt (VISA) | nf-sp00f33r |
|-----------|-----------|----------------|-------------|
| **PAN** | ✅ Via Track2 or 5A | ✅ | ✅ |
| **Track2** | ✅ Tag 57 | ✅ | ✅ |
| **Expiry** | ✅ Tag 5F24 | ✅ | ✅ |
| **CVM List (8E)** | ✅ Via AFL | ⚠️ If available | ✅ **Extended scan** |
| **CDOL1 (8C)** | ✅ Via AFL | ⚠️ If available | ✅ **Extended scan** |
| **Issuer Cert (90)** | ✅ **For ROCA** | ❌ Not prioritized | ✅ **Always collected** |
| **ICC Cert (9F46)** | ✅ **For ROCA** | ❌ Not prioritized | ✅ **Always collected** |
| **Exponents (9F32, 9F47)** | ✅ Via AFL | ❌ Not collected | ✅ **Extended scan** |
| **Dynamic CVV** | ❌ Not implemented | ✅ **VISA-specific** | ❌ Not implemented |
| **ATC (9F36)** | ⚠️ Via GET DATA | ⚠️ Via CTQ | ✅ **GET DATA** |
| **Log Format (9F4F)** | ❌ Not implemented | ❌ Not implemented | ✅ **Phase 6** |
| **DDA/CDA (SDAD)** | ✅ **INTERNAL AUTH** | ✅ **VISA-specific** | ❌ Not implemented |

---

### **VISA-Specific Features:**

| Feature | Proxmark3 | RFIDIOt (VISA) | nf-sp00f33r |
|---------|-----------|----------------|-------------|
| **COMPUTE CHECKSUM (0x802A8E80)** | ❌ | ✅ **5 CVV modes** | ❌ |
| **TTQ Configuration (9F66)** | ❌ | ✅ **Per-mode** | ❌ |
| **CTQ Decoding (9F6C)** | ❌ | ✅ **VISA-specific** | ❌ |
| **UN Size Calculation** | ❌ | ✅ **Track1/Track2** | ❌ |
| **DDOL Processing (VSDC)** | ✅ **Generic** | ✅ **VISA-specific** | ❌ |

---

## 🔬 Proxmark3 Transaction Types (cmdemv.c)

```c
// cmdemv.c line 1435-1448
enum TransactionType {
    TT_MSD,          // Magnetic Stripe Data (default)
    TT_QVSDCMCHIP,   // qVSDC or M/Chip (quick VSDC)
    TT_CDA,          // Combined Data Authentication (qVSDC + CDA)
    TT_VSDC,         // VSDC (for testing, not standard)
    TT_END
};

const char *TransactionTypeStr[] = {
    "MSD",
    "qVSDC or M/Chip",
    "qVSDC or M/Chip plus CDA (SDAD generation)",
    "VSDC",
};
```

**Command Line Usage:**
```bash
# Default: MSD mode
proxmark3> emv exec

# qVSDC/M-Chip mode
proxmark3> emv exec --qvsdc

# qVSDC + CDA mode
proxmark3> emv exec -c

# VSDC mode (test only)
proxmark3> emv exec -x

# VISA-specific: Generate AC from GPO
proxmark3> emv exec -g
```

---

## 🎯 Key Proxmark3 Features

### **1. TLV Tree Management (emvcore.c)**

Proxmark3 uses a **TLV database (tlvdb)** to store and manage all EMV data:

```c
// cmdemv.c line 1529+
struct tlvdb *tlvRoot = tlvdb_fixed(1, strlen("Root terminal TLV tree"), 
                                    (const unsigned char *)"Root terminal TLV tree");

// All responses are parsed and added to tree
EMVSelect(channel, false, true, AID, AIDlen, buf, sizeof(buf), &len, &sw, tlvRoot);
EMVGPO(channel, true, pdol_data_tlv, pdol_len, buf, sizeof(buf), &len, &sw, tlvRoot);
EMVReadRecord(channel, true, SFI, n, buf, sizeof(buf), &len, &sw, tlvRoot);

// Extract data from tree
const struct tlv *pan = tlvdb_get(tlvRoot, 0x5a, NULL);
const struct tlv *track2 = tlvdb_get(tlvRoot, 0x57, NULL);
const struct tlv *aip = tlvdb_get(tlvRoot, 0x82, NULL);
const struct tlv *afl = tlvdb_get(tlvRoot, 0x94, NULL);
```

---

### **2. DOL Processing (dol_process)**

**PDOL (Processing Data Object List) Handling:**

```c
// cmdemv.c line 1571
struct tlv *pdol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f38, NULL), tlvRoot, 0x83);

// CDOL (Card Risk Management Data Object List) Handling:
struct tlv *cdol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x8c, NULL), tlvRoot, 0x00);

// DDOL (Dynamic Data Object List) Handling:
struct tlv *ddol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f49, NULL), tlvRoot, 0x00);
```

**dol_process() Function:**
1. Parses DOL tag list (e.g., 9F38 contains tags 9F37, 9A, 9C, etc.)
2. Looks up each tag in tlvRoot (from transaction parameters)
3. Builds concatenated data buffer
4. Wraps in TLV format (tag 0x83 for PDOL)

---

### **3. Transaction Parameter Initialization**

```c
// cmdemv.c (InitTransactionParameters function)
// Default transaction values
tlvdb_add(tlvRoot, tlvdb_fixed(0x9f02, 6, "\x00\x00\x00\x00\x01\x00"));  // Amount: 0.01
tlvdb_add(tlvRoot, tlvdb_fixed(0x9f03, 6, "\x00\x00\x00\x00\x00\x00"));  // Other Amount: 0
tlvdb_add(tlvRoot, tlvdb_fixed(0x9f1a, 2, "\x08\x26"));                    // Country Code: 0826 (UK)
tlvdb_add(tlvRoot, tlvdb_fixed(0x5f2a, 2, "\x08\x26"));                    // Currency Code: 0826 (GBP)
tlvdb_add(tlvRoot, tlvdb_fixed(0x9a, 3, getCurrentDate()));                // Date: YYMMDD
tlvdb_add(tlvRoot, tlvdb_fixed(0x9c, 1, "\x00"));                          // Transaction Type: 0x00
tlvdb_add(tlvRoot, tlvdb_fixed(0x9f37, 4, getUnpredictableNumber()));     // UN
tlvdb_add(tlvRoot, tlvdb_fixed(0x95, 5, "\x00\x00\x00\x00\x00"));         // TVR
```

---

### **4. ROCA Vulnerability Detection (cmdemv.c line 2514+)**

```c
// cmdemv.c line 2514-2545
static int CmdEMVRoca(const char *Cmd) {
    // Select PPSE
    res = EMVSearchPSE(channel, true, true, psenum, false, tlv);
    
    // Select AID
    EMVSelectApplication(tlv, AID, &AIDlen);
    res = EMVSelect(channel, false, true, AID, AIDlen, buf, sizeof(buf), &len, &sw, tlv);
    
    // Get Processing Options
    struct tlv *pdol_data_tlv = dol_process(tlvdb_get(tlv, 0x9f38, NULL), tlv, 0x83);
    res = EMVGPO(channel, true, pdol_data_tlv->value, pdol_data_tlv->len, buf, sizeof(buf), &len, &sw, tlv);
    
    // Read AFL records
    const struct tlv *AFL = tlvdb_get(tlv, 0x94, NULL);
    for (int i = 0; i < AFL->len / 4; i++) {
        uint8_t SFI = AFL->value[i * 4 + 0] >> 3;
        uint8_t SFIstart = AFL->value[i * 4 + 1];
        uint8_t SFIend = AFL->value[i * 4 + 2];
        
        for (int n = SFIstart; n <= SFIend; n++) {
            res = EMVReadRecord(channel, true, SFI, n, buf, sizeof(buf), &len, &sw, tlv);
        }
    }
    
    // Recover certificates and check for ROCA
    int res = RecoveryCertificates(tlv, root);  // Extracts 90, 9F46, 8F, 9F32, 9F47
    
    return roca_check(root);  // Checks if modulus is vulnerable
}
```

---

## 📊 What Proxmark3 Does Better Than RFIDIOt

### **1. Comprehensive Certificate Extraction**

**ROCA Vulnerability Analysis:**
- RFIDIOt: Skips certificate extraction (focused on dynamic CVV)
- Proxmark3: **Full certificate recovery** (tags 90, 9F46, 8F, 9F32, 9F47)
- nf-sp00f33r: **Always collects all 5 ROCA tags** (extended scan)

---

### **2. AFL-Driven Record Reading**

**Record Reading Strategy:**
- RFIDIOt: Reads only what's needed for attack (Track2 + cryptogram)
- Proxmark3: **Follows AFL exactly** (reads all records specified)
- nf-sp00f33r: **AFL + Extended Scan** (finds missing tags in SFI 1-3, Records 1-16)

**Proxmark3 AFL Reading (cmdemv.c line 2631-2653):**
```c
const struct tlv *AFL = tlvdb_get(tlvRoot, 0x94, NULL);
while (AFL && AFL->len) {
    for (int i = 0; i < AFL->len / 4; i++) {
        uint8_t SFI = AFL->value[i * 4 + 0] >> 3;
        uint8_t SFIstart = AFL->value[i * 4 + 1];
        uint8_t SFIend = AFL->value[i * 4 + 2];
        uint8_t SFIoffline = AFL->value[i * 4 + 3];  // Not used in nf-sp00f33r
        
        for (int n = SFIstart; n <= SFIend; n++) {
            res = EMVReadRecord(channel, true, SFI, n, buf, sizeof(buf), &len, &sw, tlvRoot);
        }
    }
}
```

**nf-sp00f33r Extended Scan (Phase 4B):**
```kotlin
// CardReadingViewModel.kt (after AFL reading)
// If critical tags missing, scan SFI 1-3, Records 1-16
for (sfi in 1..3) {
    for (record in 1..16) {
        val apdu = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), ((sfi shl 3) or 0x04).toByte(), 0x00)
        val response = sendApdu(apdu)
        if (response.statusWord == "9000") {
            // Found additional data not in AFL
        }
    }
}
```

---

### **3. DDA/CDA Authentication Support**

**INTERNAL AUTHENTICATE Implementation:**

**Proxmark3:**
```c
// cmdemv.c line 1177-1199
// 1. Generate Challenge (GET CHALLENGE)
res = EMVGenerateChallenge(channel, leaveSignalON, buf, sizeof(buf), &len, &sw, NULL);

// 2. Build DDOL data (includes challenge + transaction data)
struct tlv *ddol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f49, NULL), tlvRoot, 0x00);

// 3. Send INTERNAL AUTHENTICATE with DDOL data
res = EMVInternalAuthenticate(channel, leaveSignalON, ddol_data, ddol_len, buf, sizeof(buf), &len, &sw, tlvRoot);

// Response: SDAD (Signed Dynamic Application Data) for DDA/CDA verification
```

**RFIDIOt (VISA-specific):**
```python
# ChAPlibVISA.py line 581-596
def internal_authenticate(authdata, cardservice):
    P1 = 0x00
    P2 = 0x00
    le = 0x00
    lc = len(authdata)
    apdu = INTERNAL_AUTHENTICATE + [P1, P2, lc] + authdata + [le]
    response, sw1, sw2 = send_apdu(apdu, cardservice)
    if check_return(sw1, sw2):
        print 'SDAD generated!'
        return response
```

**nf-sp00f33r:**
- ❌ Not implemented yet
- ✅ Recommendation: Add Proxmark3-style INTERNAL AUTHENTICATE

---

## 🆚 What RFIDIOt Does Better Than Proxmark3

### **1. VISA Dynamic CVV Generation**

**RFIDIOt-Specific APDU:**
```python
# ChAPlibVISA.py line 568-580
COMPUTE_CRYPTOGRAPHIC_CHECKSUM = [0x80, 0x2a, 0x8e, 0x80]

def compute_cryptographic_checksum(un, cardservice):
    unstring = "{:0>8d}".format(un)  # 4-byte UN as 8-digit string
    unlist = list(unstring.decode("hex"))
    apdu = COMPUTE_CRYPTOGRAPHIC_CHECKSUM + [len(unlist)] + unlist + [0x00]
    response, sw1, sw2 = send_apdu(apdu, cardservice)
    if check_return(sw1, sw2):
        return True, response  # dCVV returned
```

**Proxmark3:**
- ❌ Not implemented (not VISA-specific)

**nf-sp00f33r:**
- ❌ Not implemented
- ⚠️ Could add VISA-specific module (see RFIDIOT_VISA_ANALYSIS.md)

---

### **2. TTQ (Terminal Transaction Qualifiers) Configuration**

**RFIDIOt VISA Modes:**
```python
# ChAP-paywave.py line 183-191
if CVV == DCVV:
    TRANS_VALS[0x9f66] = [0x80, 0x00, 0x00, 0x00]  # MSD required
elif CVV == CVN17:
    TRANS_VALS[0x9f66] = [0x80, 0x80, 0x00, 0x00]  # MSD + cryptogram
elif CVV == FDDA0:
    TRANS_VALS[0x9f66] = [0x20, 0x00, 0x00, 0x00]  # qVSDC
elif CVV == VSDC:
    TRANS_VALS[0x9f66] = [0x40, 0x80, 0x00, 0x00]  # VSDC
```

**Proxmark3:**
- ⚠️ Uses generic transaction parameters
- ❌ No TTQ mode-specific configuration

---

## 🎓 Lessons from Proxmark3 for nf-sp00f33r

### **1. TLV Database Approach**

**Proxmark3 Advantage:**
- All EMV data stored in structured TLV tree
- Easy to query any tag: `tlvdb_get(tlvRoot, 0x5a, NULL)`
- Automatic TLV parsing on every response

**nf-sp00f33r Current:**
- Manual tag extraction with EmvTlvParser
- Tags stored in MutableMap<String, EnrichedTagData>

**Recommendation:**
Consider creating a TlvDatabase class similar to Proxmark3's tlvdb for better organization.

---

### **2. DOL Processing (PDOL/CDOL/DDOL)**

**Proxmark3 Excellence:**
```c
// Generic DOL processor
struct tlv *dol_process(const struct tlv *dol, struct tlvdb *tlvRoot, uint8_t tag) {
    // 1. Parse DOL tag list (e.g., 9F37 04 9A 03 9C 01)
    // 2. Look up each tag in tlvRoot
    // 3. Build concatenated data
    // 4. Wrap in TLV (tag 0x83 for PDOL)
    return pdol_data_tlv;
}
```

**nf-sp00f33r Current:**
```kotlin
// CardReadingViewModel.kt
fun buildPdolData(pdolTags: List<String>): ByteArray {
    val transVals = mutableMapOf(
        "9F37" to getUnpredictableNumber(),
        "9A" to getCurrentDate(),
        "9C" to "00",
        // ... etc
    )
    return buildPdolFromTags(pdolTags, transVals)
}
```

**Recommendation:**
✅ nf-sp00f33r already implements this correctly, but could refactor into generic DOL processor for CDOL/DDOL too.

---

### **3. INTERNAL AUTHENTICATE Implementation**

**Proxmark3 Workflow:**
1. GET CHALLENGE (0x0084) - Get random number from card
2. Parse DDOL from card records (tag 9F49)
3. Build DDOL data (challenge + transaction data)
4. Send INTERNAL AUTHENTICATE (0x0088) with DDOL data
5. Parse SDAD response for DDA/CDA verification

**Implementation for nf-sp00f33r:**

```kotlin
// Add to CardReadingViewModel.kt

suspend fun performDdaAuthentication(): Boolean {
    // 1. Check if DDA/CDA supported
    val aipBytes = parsedEmvFields["82"]?.value?.hexToByteArray() ?: return false
    val supportsDDA = (aipBytes[0].toInt() and 0x20) != 0  // Bit 6 of AIP byte 1
    val supportsCDA = (aipBytes[0].toInt() and 0x01) != 0  // Bit 1 of AIP byte 1
    
    if (!supportsDDA && !supportsCDA) {
        Timber.i("Card does not support DDA/CDA")
        return false
    }
    
    // 2. Send GET CHALLENGE
    val challengeApdu = byteArrayOf(0x00, 0x84.toByte(), 0x00, 0x00, 0x08)  // Request 8 bytes
    val challengeResponse = sendApdu(challengeApdu)
    if (challengeResponse.statusWord != "9000") {
        Timber.w("GET CHALLENGE failed: ${challengeResponse.statusWord}")
        return false
    }
    val challenge = challengeResponse.data
    Timber.i("Challenge: ${challenge.toHexString()}")
    
    // 3. Extract DDOL from card records (tag 9F49)
    val ddol = parsedEmvFields["9F49"]?.value ?: run {
        Timber.w("DDOL (9F49) not found in card records")
        return false
    }
    
    // 4. Build DDOL data
    val ddolData = buildDdolData(ddol, challenge)
    
    // 5. Send INTERNAL AUTHENTICATE
    val intAuthApdu = byteArrayOf(
        0x00, 0x88.toByte(), 0x00, 0x00,
        ddolData.size.toByte()
    ) + ddolData + byteArrayOf(0x00)
    
    val sdadResponse = sendApdu(intAuthApdu)
    if (sdadResponse.statusWord != "9000") {
        Timber.w("INTERNAL AUTHENTICATE failed: ${sdadResponse.statusWord}")
        return false
    }
    
    // 6. Parse SDAD (Signed Dynamic Application Data)
    val sdadTags = EmvTlvParser.parseResponse(sdadResponse.data)
    parsedEmvFields.putAll(sdadTags.tags)
    Timber.i("✅ DDA/CDA authentication successful - SDAD retrieved")
    
    return true
}

private fun buildDdolData(ddol: String, challenge: ByteArray): ByteArray {
    // Parse DDOL tag list (similar to PDOL parsing)
    val ddolTags = EmvTlvParser.parseDol(ddol.hexToByteArray())
    
    val transVals = mutableMapOf(
        "9F37" to challenge.toHexString(),  // Unpredictable Number (use challenge as UN)
        "9A" to getCurrentDate(),            // Transaction Date
        "9C" to "00",                        // Transaction Type
        "9F02" to "000000000100",            // Amount
        // ... add more as needed
    )
    
    return buildDataFromDol(ddolTags, transVals)
}
```

---

## 📋 Summary: Proxmark3 vs RFIDIOt vs nf-sp00f33r

### **Tool Purposes:**

1. **Proxmark3:**
   - **Purpose:** Universal RFID/NFC research platform
   - **Focus:** Comprehensive EMV data extraction, forensics
   - **Target:** Security researchers, forensics professionals
   - **Strength:** Complete EMV workflow, TLV tree management, ROCA detection

2. **RFIDIOt:**
   - **Purpose:** Attack-focused EMV tool
   - **Focus:** VISA dynamic CVV generation, card cloning
   - **Target:** Security testers, attackers
   - **Strength:** VISA-specific commands (COMPUTE CHECKSUM), minimal data collection

3. **nf-sp00f33r:**
   - **Purpose:** Android EMV research platform
   - **Focus:** Multi-card comprehensive data extraction, attack coverage
   - **Target:** Mobile security researchers, EMV researchers
   - **Strength:** Extended record scan, transaction logs, database integration

---

### **Data Collection Philosophy:**

| Tool | Philosophy | Data Collection |
|------|-----------|-----------------|
| **Proxmark3** | **"Collect everything via standard EMV workflow"** | AFL-based, certificate-focused, TLV tree |
| **RFIDIOt** | **"Collect only what's needed for attack"** | Minimal (Track2 + CVV + cryptogram) |
| **nf-sp00f33r** | **"Collect everything + search for missing data"** | AFL + Extended Scan + GET DATA + Transaction Logs |

---

### **Attack Coverage:**

| Attack Type | Proxmark3 | RFIDIOt | nf-sp00f33r |
|-------------|-----------|---------|-------------|
| **Track2 Clone** | ✅ | ✅ | ✅ |
| **Dynamic CVV Clone** | ❌ | ✅ (VISA only) | ❌ |
| **ROCA Exploitation** | ✅ | ❌ | ✅ |
| **CVM Bypass** | ✅ | ⚠️ | ✅ |
| **DDA/CDA Bypass** | ✅ | ✅ (VISA only) | ❌ (not yet) |
| **Amount Modification** | ✅ | ❌ | ✅ |

---

## 🚀 Recommendations for nf-sp00f33r

### **Priority 1: Add INTERNAL AUTHENTICATE (from Proxmark3)**

**Benefits:**
- Complete DDA/CDA authentication support
- Generate SDAD for offline transaction signing
- Bypass DDA authentication on vulnerable cards

**Implementation:** See "INTERNAL AUTHENTICATE Implementation" section above

---

### **Priority 2: Consider VISA-Specific Module (from RFIDIOt)**

**Benefits:**
- Support VISA dynamic CVV generation
- Add TTQ configuration for different VISA modes
- Expand attack coverage for VISA cards

**Implementation:** See RFIDIOT_VISA_ANALYSIS.md Priority 1-4

---

### **Priority 3: Improve DOL Processing (from Proxmark3)**

**Benefits:**
- Generic DOL processor for PDOL/CDOL/DDOL
- Cleaner code architecture
- Easier to add new transaction parameters

**Current Status:** ✅ Already implemented correctly, but could refactor for generalization

---

### **Priority 4: Add Transaction Log Reading (DONE ✅)**

**Status:** ✅ **Phase 7 already implemented**
- Proxmark3: ❌ Not implemented
- nf-sp00f33r: ✅ Full transaction log reading

**Advantage over Proxmark3:** nf-sp00f33r reads transaction logs (9F4F), Proxmark3 doesn't!

---

## 📊 Final Comparison Matrix

| Feature | Proxmark3 | RFIDIOt (VISA) | nf-sp00f33r | Winner |
|---------|-----------|----------------|-------------|--------|
| **AFL-Based Record Reading** | ✅ | ❌ | ✅ | nf-sp00f33r (with extended scan) |
| **Extended Record Scan** | ❌ | ❌ | ✅ | **nf-sp00f33r** |
| **ROCA Certificate Collection** | ✅ | ❌ | ✅ | Tie (Proxmark3 & nf-sp00f33r) |
| **Transaction Logs** | ❌ | ❌ | ✅ | **nf-sp00f33r** |
| **GET DATA (12 tags)** | ⚠️ | ❌ | ✅ | **nf-sp00f33r** |
| **INTERNAL AUTHENTICATE** | ✅ | ✅ | ❌ | Proxmark3 & RFIDIOt |
| **COMPUTE CHECKSUM (VISA)** | ❌ | ✅ | ❌ | **RFIDIOt** |
| **TLV Tree Management** | ✅ | ❌ | ⚠️ | **Proxmark3** |
| **DOL Processing** | ✅ | ⚠️ | ✅ | Tie (Proxmark3 & nf-sp00f33r) |
| **Database Integration** | ❌ | ❌ | ✅ | **nf-sp00f33r** |
| **Mobile Platform** | ❌ | ❌ | ✅ | **nf-sp00f33r** |

---

## 🎯 Conclusion

**Proxmark3:**
- ✅ Complete EMV workflow with TLV tree management
- ✅ INTERNAL AUTHENTICATE for DDA/CDA
- ✅ ROCA vulnerability detection
- ✅ AFL-based record reading
- ❌ No transaction log reading
- ❌ No VISA-specific dynamic CVV

**RFIDIOt:**
- ✅ VISA dynamic CVV generation (COMPUTE CHECKSUM)
- ✅ Attack-focused minimal data collection
- ✅ TTQ configuration for different VISA modes
- ❌ No comprehensive data extraction
- ❌ No certificate collection for ROCA

**nf-sp00f33r:**
- ✅ **Best comprehensive data extraction** (AFL + Extended Scan)
- ✅ **Transaction log reading** (unique feature)
- ✅ **GET DATA for 12 tags**
- ✅ **ROCA certificate collection** (100% coverage)
- ✅ **Room database integration**
- ✅ **Android mobile platform**
- ❌ No INTERNAL AUTHENTICATE (yet)
- ❌ No VISA dynamic CVV (yet)

**Best of All Three:**
Combine nf-sp00f33r's comprehensive data extraction + Proxmark3's INTERNAL AUTHENTICATE + RFIDIOt's VISA-specific dynamic CVV = **Ultimate EMV Research Platform**

---

**Generated:** October 11, 2025  
**Analyst:** GitHub Copilot EMV Research Team  
**Report Version:** 1.0  
**Based On:** Proxmark3 main branch (RfidResearchGroup/proxmark3)
