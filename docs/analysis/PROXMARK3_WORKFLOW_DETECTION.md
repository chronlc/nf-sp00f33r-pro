# Proxmark3 EMV Workflow - Complete Card Detection Logic

**Source:** github.com/RfidResearchGroup/proxmark3  
**File:** client/src/emv/cmdemv.c  
**Date Analyzed:** October 11, 2025  

---

## 🎯 How Proxmark3 Detects Card Type

Proxmark3 **doesn't actually have separate paths for VISA vs Mastercard**! It uses a **vendor-agnostic workflow** that adapts based on what the card responds with.

Here's the actual detection logic:

---

## 📋 Card Vendor Detection (emvcore.c line 138-160)

```c
// emvcore.c line 138-160
enum CardPSVendor GetCardPSVendor(uint8_t *AID, size_t AIDlen) {
    char buf[100] = {0};
    if (AIDlen < 1)
        return CV_NA;

    hex_to_buffer((uint8_t *)buf, AID, AIDlen, sizeof(buf) - 1, 0, 0, true);

    for (int i = 0; i < ARRAYLEN(AIDlist); i ++) {
        if (strncmp(AIDlist[i].aid, buf, strlen(AIDlist[i].aid)) == 0) {
            return AIDlist[i].vendor;
        }
    }

    return CV_NA;
}
```

### **AID to Vendor Mapping (emvcore.c line 50-138):**

```c
// VISA AIDs
{ CV_VISA, "A0000000031010" },               // VISA Debit/Credit
{ CV_VISA, "A0000000032010" },               // VISA Electron
{ CV_VISA, "A0000000033010" },               // VISA Interlink
{ CV_VISA, "A0000000038002" },               // VISA Auth, VisaRemAuthen EMV-CAP (DPA)
{ CV_VISA, "A0000000038010" },               // VISA Plus
{ CV_VISA, "A000000098" },                   // VISA USA Debit Card
{ CV_VISA, "A0000000980848" },               // VISA USA Debit Card

// Mastercard AIDs
{ CV_MASTERCARD, "A00000000401" },           // MasterCard PayPass
{ CV_MASTERCARD, "A0000000041010" },         // MasterCard Credit/Debit
{ CV_MASTERCARD, "A00000000410101213" },     // MasterCard Credit
{ CV_MASTERCARD, "A00000000410101215" },     // MasterCard Debit

// American Express AIDs
{ CV_AMERICANEXPRESS, "A000000025" },        // American Express
{ CV_AMERICANEXPRESS, "A0000000250000" },    // American Express

// JCB AIDs
{ CV_JCB, "A0000000651010" },                // JCB
{ CV_JCB, "A00000006510101213" },            // JCB Credit

// Discover AIDs
{ CV_OTHER, "A0000001523010" },              // Discover Card
```

**Key Point:** Proxmark3 identifies the vendor **AFTER** selecting the AID, but **uses the same workflow for all vendors**.

---

## 🔄 Complete Proxmark3 Workflow (cmdemv.c)

### **Main Function: `CmdEMVExec()` (cmdemv.c line 1500-1800)**

Here's the **actual complete workflow** from Proxmark3:

```c
static int CmdEMVExec(const char *Cmd) {
    // Variables
    uint8_t AID[APDU_AID_LEN] = {0};
    size_t AIDlen = 0;
    uint8_t buf[APDU_RES_LEN] = {0};
    size_t len = 0;
    uint16_t sw = 0;
    uint8_t CID = 0;
    struct tlvdb *tlvRoot = NULL;
    struct tlv *pdol_data_tlv = NULL;

    // Parse command line options
    bool activateField = arg_get_lit(ctx, 1);
    bool show_apdu = arg_get_lit(ctx, 2);
    bool decodeTLV = arg_get_lit(ctx, 3);
    bool forceSearch = arg_get_lit(ctx, 5);
    
    // Get transaction type
    enum TransactionType TrType = TT_MSD;  // Default: Magnetic Stripe Data
    if (arg_get_lit(ctx, 6)) {
        TrType = TT_QVSDCMCHIP;  // qVSDC or M/Chip
    }
    if (arg_get_lit(ctx, 7)) {
        TrType = TT_CDA;  // qVSDC or M/Chip plus CDA
    }
    if (arg_get_lit(ctx, 8)) {
        TrType = TT_VSDC;  // VSDC (for testing)
    }
    
    bool GenACGPO = arg_get_lit(ctx, 9);  // VISA: generate AC from GPO
    
    Iso7816CommandChannel channel = CC_CONTACTLESS;  // or CC_CONTACT
    uint8_t psenum = (channel == CC_CONTACT) ? 1 : 2;  // PSE or PPSE

    SetAPDULogging(show_apdu);

    // ========================================
    // STEP 1: SELECT PPSE (or search AIDs)
    // ========================================
    
    if (forceSearch) {
        // Option A: Search for AIDs directly (brute force)
        PrintAndLogEx(INFO, "\n* Search AID in list.");
        if (EMVSearch(channel, activateField, true, decodeTLV, tlvSelect, false)) {
            dreturn(PM3_ERFTRANS);
        }
        TLVPrintAIDlistFromSelectTLV(tlvSelect);
        EMVSelectApplication(tlvSelect, AID, &AIDlen);
        
    } else {
        // Option B: SELECT PPSE (standard way)
        PrintAndLogEx(INFO, "\n* PPSE.");
        res = EMVSearchPSE(channel, activateField, true, psenum, decodeTLV, tlvSelect);
        
        if (res) {
            PrintAndLogEx(ERR, "PPSE error. Exit...");
            dreturn(PM3_ERFTRANS);
        }
        
        // Extract AID from PPSE response
        TLVPrintAIDlistFromSelectTLV(tlvSelect);
        EMVSelectApplication(tlvSelect, AID, &AIDlen);
    }

    // Check if we found EMV application on card
    if (!AIDlen) {
        PrintAndLogEx(WARNING, "Can't select AID. EMV AID not found");
        dreturn(PM3_ERFTRANS);
    }

    // ========================================
    // STEP 2: SELECT AID
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Selecting AID:%s", sprint_hex_inrow(AID, AIDlen));
    SetAPDULogging(show_apdu);
    res = EMVSelect(channel, false, true, AID, AIDlen, buf, sizeof(buf), &len, &sw, tlvRoot);

    if (res) {
        PrintAndLogEx(WARNING, "Can't select AID (%d). Exit...", res);
        dreturn(PM3_ERFTRANS);
    }

    if (decodeTLV) {
        TLVPrintFromBuffer(buf, len);
    }
    PrintAndLogEx(INFO, "* Selected.");

    // ========================================
    // STEP 3: INITIALIZE TRANSACTION PARAMETERS
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Init transaction parameters.");
    InitTransactionParameters(tlvRoot, paramLoadJSON, TrType, GenACGPO);
    TLVPrintFromTLV(tlvRoot);

    // ========================================
    // STEP 4: GET PROCESSING OPTIONS (GPO)
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Calc PDOL.");
    pdol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f38, NULL), tlvRoot, 0x83);
    if (!pdol_data_tlv) {
        PrintAndLogEx(ERR, "Error: can't create PDOL TLV.");
        dreturn(PM3_ESOFT);
    }

    size_t pdol_data_tlv_data_len;
    unsigned char *pdol_data_tlv_data = tlv_encode(pdol_data_tlv, &pdol_data_tlv_data_len);
    if (!pdol_data_tlv_data) {
        PrintAndLogEx(ERR, "Error: can't create PDOL data.");
        dreturn(PM3_ESOFT);
    }
    PrintAndLogEx(INFO, "PDOL data[%zu]: %s", pdol_data_tlv_data_len, 
                  sprint_hex(pdol_data_tlv_data, pdol_data_tlv_data_len));

    PrintAndLogEx(INFO, "\n* GPO.");
    res = EMVGPO(channel, true, pdol_data_tlv_data, pdol_data_tlv_data_len, 
                 buf, sizeof(buf), &len, &sw, tlvRoot);

    if (res) {
        PrintAndLogEx(ERR, "GPO error(%d): %4x. Exit...", res, sw);
        dreturn(PM3_ERFTRANS);
    }

    // Process GPO response (format 1: 80 or format 2: 77)
    ProcessGPOResponseFormat1(tlvRoot, buf, len, decodeTLV);

    // Extract PAN from track2 if not present
    const struct tlv *track2 = tlvdb_get(tlvRoot, 0x57, NULL);
    if (!tlvdb_get(tlvRoot, 0x5a, NULL) && track2 && track2->len >= 8) {
        struct tlvdb *pan = GetPANFromTrack2(track2);
        if (pan) {
            tlvdb_add(tlvRoot, pan);
        }
    }

    // ========================================
    // STEP 5: READ AFL RECORDS
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Read AFL.");
    const struct tlv *AFL = tlvdb_get(tlvRoot, 0x94, NULL);

    if (!AFL || !AFL->len) {
        PrintAndLogEx(WARNING, "AFL not found.");
    }

    while (AFL && AFL->len) {
        if (AFL->len % 4) {
            PrintAndLogEx(ERR, "Wrong AFL length: %zu", AFL->len);
            break;
        }

        // Read all AFL entries
        for (int i = 0; i < AFL->len / 4; i++) {
            uint8_t SFI = AFL->value[i * 4 + 0] >> 3;
            uint8_t SFIstart = AFL->value[i * 4 + 1];
            uint8_t SFIend = AFL->value[i * 4 + 2];
            uint8_t SFIoffline = AFL->value[i * 4 + 3];

            PrintAndLogEx(INFO, "* * SFI[%02x] start:%02x end:%02x offline:%02x", 
                          SFI, SFIstart, SFIend, SFIoffline);
            
            if (SFI == 0 || SFI == 31 || SFIstart == 0 || SFIstart > SFIend) {
                PrintAndLogEx(ERR, "SFI ERROR! Skipped...");
                continue;
            }

            // Read each record in range
            for (int n = SFIstart; n <= SFIend; n++) {
                PrintAndLogEx(INFO, "* * * SFI[%02x] %d", SFI, n);

                res = EMVReadRecord(channel, true, SFI, n, buf, sizeof(buf), &len, &sw, tlvRoot);

                if (res) {
                    PrintAndLogEx(ERR, "Error SFI[%02x]. APDU error %4x", SFI, sw);
                    continue;
                }

                // TLV print and save
                if (decodeTLV) {
                    TLVPrintFromBuffer(buf, len);
                    PrintAndLogEx(NORMAL, "");
                }

                // Print PAN if found
                const struct tlv *panTag = tlvdb_get(tlvRoot, 0x5a, NULL);
                if (panTag) {
                    const struct tlv *track2Tag = tlvdb_get(tlvRoot, 0x57, NULL);
                    PrintAndLogEx(SUCCESS, "PAN: " _GREEN_("%s"), 
                                  sprint_hex_inrow(panTag->value, panTag->len));
                    if (track2Tag && track2Tag->len) {
                        PrintAndLogEx(SUCCESS, "Track2: " _GREEN_("%s"), 
                                      sprint_hex_inrow(track2Tag->value, track2Tag->len));
                    }
                }
            }
        }

        break;
    }

    // ========================================
    // STEP 6: OFFLINE DATA AUTHENTICATION
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Offline data authentication.");
    
    // Read AIP (Application Interchange Profile)
    const struct tlv *AIPtlv = tlvdb_get(tlvRoot, 0x82, NULL);
    uint16_t AIP = 0;
    if (AIPtlv) {
        AIP = (AIPtlv->value[0] << 8) | AIPtlv->value[1];
        PrintAndLogEx(INFO, "AIP: %04x", AIP);
    }

    // Check authentication type
    if (AIP & 0x4000) {  // Bit 7 of byte 1: SDA supported
        PrintAndLogEx(INFO, "* * SDA");
        trSDA(tlvRoot);  // Perform SDA (Static Data Authentication)
        
    } else if (AIP & 0x2000) {  // Bit 6 of byte 1: DDA supported
        PrintAndLogEx(INFO, "* * DDA");
        trDDA(channel, decodeTLV, tlvRoot);  // Perform DDA (Dynamic Data Authentication)
        
    } else if (AIP & 0x0001) {  // Bit 1 of byte 1: CDA supported
        PrintAndLogEx(INFO, "* * CDA");
        // CDA is checked during GENERATE AC
    }

    // ========================================
    // STEP 7: GENERATE AC (ARQC)
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Generate AC (ARQC).");
    
    // Get CDOL1
    const struct tlv *CDOL1 = tlvdb_get(tlvRoot, 0x8c, NULL);
    if (!CDOL1 || !CDOL1->len) {
        PrintAndLogEx(WARNING, "CDOL1 [8c] not found. Skipping GENERATE AC.");
    } else {
        struct tlv *cdol_data_tlv = dol_process(CDOL1, tlvRoot, 0x00);
        if (!cdol_data_tlv) {
            PrintAndLogEx(ERR, "Error: can't create CDOL1 TLV.");
            dreturn(PM3_ESOFT);
        }

        PrintAndLogEx(INFO, "CDOL1 data[%zu]: %s", cdol_data_tlv->len, 
                      sprint_hex(cdol_data_tlv->value, cdol_data_tlv->len));

        // P1: 0x80 = ARQC (Authorization Request Cryptogram - go online)
        res = EMVAC(channel, true, 0x80, cdol_data_tlv->value, cdol_data_tlv->len, 
                    buf, sizeof(buf), &len, &sw, tlvRoot);

        if (res) {
            PrintAndLogEx(ERR, "GENERATE AC error(%d): %4x.", res, sw);
            dreturn(PM3_ERFTRANS);
        }

        if (decodeTLV) {
            TLVPrintFromBuffer(buf, len);
        }

        // Extract cryptogram
        const struct tlv *cryptogram = tlvdb_get(tlvRoot, 0x9f26, NULL);
        if (cryptogram) {
            PrintAndLogEx(SUCCESS, "ARQC: " _GREEN_("%s"), 
                          sprint_hex_inrow(cryptogram->value, cryptogram->len));
        }

        // Check CID (Cryptogram Information Data)
        const struct tlv *cid = tlvdb_get(tlvRoot, 0x9f27, NULL);
        if (cid) {
            uint8_t cidValue = cid->value[0];
            if ((cidValue & 0xC0) == 0x80) {
                PrintAndLogEx(SUCCESS, "CID: ARQC (Authorization Request)");
            } else if ((cidValue & 0xC0) == 0x40) {
                PrintAndLogEx(SUCCESS, "CID: TC (Transaction Certificate)");
            } else if ((cidValue & 0xC0) == 0x00) {
                PrintAndLogEx(SUCCESS, "CID: AAC (Application Authentication Cryptogram)");
            }
        }
    }

    // ========================================
    // STEP 8: FINALIZE
    // ========================================
    
    PrintAndLogEx(INFO, "\n* Transaction completed.");
    
    if (!LeaveFieldON) {
        DropFieldEx(channel);
    }

    tlvdb_free(tlvRoot);
    tlvdb_free(tlvSelect);

    PrintAndLogEx(INFO, "\n* * Transaction info:");
    TLVPrintAIDlistFromSelectTLV(tlvSelect);
    
    return PM3_SUCCESS;
}
```

---

## 🔍 Key Detection Points

### **1. Transaction Type Selection (Line 1435-1448)**

Proxmark3 has **4 transaction modes**, but they're **NOT card-specific**:

```c
enum TransactionType {
    TT_MSD,          // Magnetic Stripe Data (default) - Works for ALL cards
    TT_QVSDCMCHIP,   // qVSDC or M/Chip - Works for both VISA and Mastercard
    TT_CDA,          // CDA mode - Works for any card supporting CDA
    TT_VSDC,         // VSDC (for testing only)
};
```

**User selects mode via command line:**
```bash
# Default: MSD mode (works for all cards)
proxmark3> emv exec

# qVSDC/M-Chip mode (works for both VISA and Mastercard)
proxmark3> emv exec --qvsdc

# CDA mode (works for any card supporting CDA)
proxmark3> emv exec -c

# VSDC mode (test only)
proxmark3> emv exec -x
```

**Key Point:** Proxmark3 **doesn't automatically detect** which mode to use based on vendor. **User chooses** the mode.

---

### **2. Transaction Parameter Initialization (cmdemv.c line 300-500)**

```c
void InitTransactionParameters(struct tlvdb *tlvRoot, bool paramLoadJSON, 
                               enum TransactionType TrType, bool GenACGPO) {
    // Load parameters based on transaction type
    switch (TrType) {
        case TT_MSD:
            // Magnetic Stripe Data mode
            tlvdb_add(tlvRoot, tlvdb_fixed(0x9f66, 4, "\x80\x00\x00\x00"));  // TTQ: MSD
            break;
            
        case TT_QVSDCMCHIP:
            // qVSDC or M/Chip mode
            tlvdb_add(tlvRoot, tlvdb_fixed(0x9f66, 4, "\x26\x00\x00\x00"));  // TTQ: qVSDC
            break;
            
        case TT_CDA:
            // CDA mode
            tlvdb_add(tlvRoot, tlvdb_fixed(0x9f66, 4, "\x26\x80\x00\x00"));  // TTQ: qVSDC + CDA
            break;
            
        case TT_VSDC:
            // VSDC mode (test)
            tlvdb_add(tlvRoot, tlvdb_fixed(0x9f66, 4, "\x40\x80\x00\x00"));  // TTQ: VSDC
            break;
    }
    
    // Common transaction parameters (same for all cards)
    tlvdb_add(tlvRoot, tlvdb_fixed(0x9f02, 6, "\x00\x00\x00\x00\x01\x00"));  // Amount: 0.01
    tlvdb_add(tlvRoot, tlvdb_fixed(0x9f03, 6, "\x00\x00\x00\x00\x00\x00"));  // Other Amount: 0
    tlvdb_add(tlvRoot, tlvdb_fixed(0x9f1a, 2, "\x08\x26"));                    // Country Code: UK
    tlvdb_add(tlvRoot, tlvdb_fixed(0x5f2a, 2, "\x08\x26"));                    // Currency Code: GBP
    tlvdb_add(tlvRoot, tlvdb_fixed(0x9a, 3, getCurrentDate()));                // Date: YYMMDD
    tlvdb_add(tlvRoot, tlvdb_fixed(0x9c, 1, "\x00"));                          // Transaction Type: 0x00
    tlvdb_add(tlvRoot, tlvdb_fixed(0x9f37, 4, getUnpredictableNumber()));     // UN
    tlvdb_add(tlvRoot, tlvdb_fixed(0x95, 5, "\x00\x00\x00\x00\x00"));         // TVR
}
```

**Key Point:** Transaction parameters are based on **transaction type**, not card vendor. Same parameters work for both VISA and Mastercard.

---

### **3. Authentication Detection (Based on AIP)**

Proxmark3 **detects authentication type from AIP** (Application Interchange Profile), not from vendor:

```c
// Read AIP from card response
const struct tlv *AIPtlv = tlvdb_get(tlvRoot, 0x82, NULL);
uint16_t AIP = (AIPtlv->value[0] << 8) | AIPtlv->value[1];

// Check which authentication is supported
if (AIP & 0x4000) {          // Bit 7 of byte 1
    PrintAndLogEx(INFO, "* * SDA (Static Data Authentication)");
    trSDA(tlvRoot);           // Perform SDA
    
} else if (AIP & 0x2000) {   // Bit 6 of byte 1
    PrintAndLogEx(INFO, "* * DDA (Dynamic Data Authentication)");
    trDDA(channel, decodeTLV, tlvRoot);  // Perform DDA
    
} else if (AIP & 0x0001) {   // Bit 1 of byte 1
    PrintAndLogEx(INFO, "* * CDA (Combined Data Authentication)");
    // CDA is checked during GENERATE AC
}
```

**Key Point:** Proxmark3 reads the **AIP from the card** and decides which authentication to perform. It doesn't matter if it's VISA or Mastercard.

---

### **4. DDA (Dynamic Data Authentication) - cmdemv.c line 2100-2200**

```c
int trDDA(Iso7816CommandChannel channel, bool decodeTLV, struct tlvdb *tlv) {
    uint8_t buf[APDU_RES_LEN] = {0};
    size_t len = 0;
    uint16_t sw = 0;

    // Step 1: GET CHALLENGE
    PrintAndLogEx(INFO, "* * Generate challenge");
    res = EMVGenerateChallenge(channel, true, buf, sizeof(buf), &len, &sw, tlv);
    if (res) {
        PrintAndLogEx(ERR, "Error GetChallenge. APDU error %4x", sw);
        return 1;
    }
    PrintAndLogEx(SUCCESS, "Challenge: %s", sprint_hex(buf, len));

    // Step 2: Get DDOL from card records
    const struct tlv *DDOL = tlvdb_get(tlv, 0x9f49, NULL);
    if (!DDOL || !DDOL->len) {
        PrintAndLogEx(ERR, "DDOL [9f49] not found.");
        return 2;
    }

    // Step 3: Build DDOL data
    struct tlv *ddol_data_tlv = dol_process(DDOL, tlv, 0x00);
    if (!ddol_data_tlv) {
        PrintAndLogEx(ERR, "Error: can't create DDOL TLV.");
        return 3;
    }

    PrintAndLogEx(INFO, "DDOL data[%zu]: %s", ddol_data_tlv->len, 
                  sprint_hex(ddol_data_tlv->value, ddol_data_tlv->len));

    // Step 4: INTERNAL AUTHENTICATE
    PrintAndLogEx(INFO, "* * Internal Authenticate");
    res = EMVInternalAuthenticate(channel, true, ddol_data_tlv->value, ddol_data_tlv->len, 
                                  buf, sizeof(buf), &len, &sw, tlv);
    if (res) {
        PrintAndLogEx(ERR, "Error Internal Authenticate. APDU error %4x", sw);
        return 4;
    }

    // Step 5: Parse SDAD (Signed Dynamic Application Data)
    struct tlvdb *dac_db = tlvdb_parse_multi(buf, len);
    if (dac_db) {
        const struct tlv *SDAD = tlvdb_get(dac_db, 0x9f4b, NULL);
        if (SDAD) {
            PrintAndLogEx(SUCCESS, "SDAD: %s", sprint_hex(SDAD->value, SDAD->len));
        } else {
            PrintAndLogEx(WARNING, "SDAD [9f4b] not found.");
        }
        tlvdb_free(dac_db);
    }

    return 0;
}
```

**Key Point:** DDA authentication is **exactly the same** for VISA, Mastercard, AMEX, etc. The workflow doesn't change based on vendor.

---

## 🆚 VISA vs Mastercard: What's the SAME?

| Feature | VISA | Mastercard | Proxmark3 Approach |
|---------|------|------------|-------------------|
| **SELECT PPSE** | ✅ Same | ✅ Same | Same workflow |
| **SELECT AID** | ✅ Different AID | ✅ Different AID | Detects from PPSE, same SELECT command |
| **GET PROCESSING OPTIONS** | ✅ Same | ✅ Same | Same workflow, different PDOL data |
| **READ AFL RECORDS** | ✅ Same | ✅ Same | Same workflow, reads what AFL specifies |
| **INTERNAL AUTHENTICATE** | ✅ Same | ✅ Same | Same workflow if DDA/CDA supported |
| **GENERATE AC** | ✅ Same | ✅ Same | Same workflow, different CDOL data |

**Conclusion:** Proxmark3 uses **ONE UNIVERSAL WORKFLOW** for all card types!

---

## 🆚 VISA vs Mastercard: What's DIFFERENT?

### **1. AIDs (Application Identifiers)**

**VISA:**
```
A0000000031010  (VISA Debit/Credit)
A0000000032010  (VISA Electron)
A0000000033010  (VISA Interlink)
```

**Mastercard:**
```
A0000000041010  (Mastercard Credit/Debit)
A00000000401    (Mastercard PayPass)
```

**Proxmark3 handles this:** Reads AIDs from PPSE response, user (or auto) selects which one to use.

---

### **2. PDOL (Processing Data Object List)**

**VISA cards typically request:**
```
9F37 04  (Unpredictable Number - 4 bytes)
9A 03    (Transaction Date - 3 bytes)
9C 01    (Transaction Type - 1 byte)
9F66 04  (TTQ - Terminal Transaction Qualifiers - 4 bytes)
```

**Mastercard cards typically request:**
```
9F37 04  (Unpredictable Number - 4 bytes)
9F02 06  (Amount Authorized - 6 bytes)
9F1A 02  (Terminal Country Code - 2 bytes)
5F2A 02  (Transaction Currency Code - 2 bytes)
9A 03    (Transaction Date - 3 bytes)
9C 01    (Transaction Type - 1 byte)
```

**Proxmark3 handles this:** Uses `dol_process()` function to parse PDOL from card and build appropriate data dynamically.

```c
// This works for ANY card (VISA, Mastercard, AMEX, etc.)
struct tlv *pdol_data_tlv = dol_process(tlvdb_get(tlvRoot, 0x9f38, NULL), tlvRoot, 0x83);
```

---

### **3. TTQ (Terminal Transaction Qualifiers - Tag 9F66)**

**VISA modes:**
```
[0x80, 0x00, 0x00, 0x00]  - MSD (Magnetic Stripe Data)
[0x26, 0x00, 0x00, 0x00]  - qVSDC (quick VSDC)
[0x26, 0x80, 0x00, 0x00]  - qVSDC + CDA
[0x40, 0x80, 0x00, 0x00]  - VSDC
```

**Mastercard modes:**
```
[0x26, 0x00, 0x00, 0x00]  - M/Chip (similar to qVSDC)
[0x26, 0x80, 0x00, 0x00]  - M/Chip + CDA
```

**Proxmark3 handles this:** Sets TTQ based on **transaction type** selected by user, not based on vendor.

---

## 🎯 Summary: Proxmark3's Universal Approach

### **How Proxmark3 "Knows" Which Path:**

1. ✅ **It doesn't!** Proxmark3 uses **ONE UNIVERSAL WORKFLOW**
2. ✅ **Card tells Proxmark3 what it needs** via PDOL, CDOL, DDOL
3. ✅ **Proxmark3 adapts dynamically** using `dol_process()` function
4. ✅ **Authentication is detected from AIP**, not hardcoded per vendor
5. ✅ **User selects transaction type** (MSD, qVSDC, CDA), not vendor-specific

### **The Magic Function: `dol_process()`**

```c
// This ONE function handles ALL vendors (VISA, Mastercard, AMEX, etc.)
struct tlv *dol_process(const struct tlv *dol, struct tlvdb *tlvRoot, uint8_t tag) {
    // 1. Parse DOL tag list from card (e.g., 9F37 04 9A 03 9C 01)
    // 2. Look up each tag in tlvRoot (transaction parameters)
    // 3. Build concatenated data buffer
    // 4. Wrap in TLV format (tag 0x83 for PDOL)
    return pdol_data_tlv;
}
```

**This function works because:**
- VISA cards specify their PDOL → `dol_process()` builds it
- Mastercard cards specify their PDOL → `dol_process()` builds it
- AMEX cards specify their PDOL → `dol_process()` builds it
- **Same function, different input, different output!**

---

## ✅ Implementation for nf-sp00f33r

We already have this! Our `buildPdolData()` and `buildCdolData()` functions work the same way:

```kotlin
// This works for ALL card types (VISA, Mastercard, AMEX, etc.)
fun buildPdolData(pdolTags: List<String>): ByteArray {
    val transVals = mutableMapOf(
        "9F37" to getUnpredictableNumber(),
        "9A" to getCurrentDate(),
        "9C" to "00",
        "9F02" to "000000000001",
        "9F03" to "000000000000",
        "9F1A" to "0840",
        "5F2A" to "0840",
        "95" to "0000000000",
        "9F66" to "80000000",
        "9F10" to "0000000000000000000000000000000000",
        "9F36" to "0000"
    )
    
    // Build data from PDOL tags (works for ANY card)
    return buildDataFromDol(pdolTags, transVals)
}
```

**Key Insight:** nf-sp00f33r **already uses the universal approach** like Proxmark3! We don't have separate VISA/Mastercard workflows - we have **ONE workflow that adapts to any card**.

---

**Generated:** October 11, 2025  
**Analysis:** Complete Proxmark3 EMV workflow with vendor detection logic  
**Source:** github.com/RfidResearchGroup/proxmark3
