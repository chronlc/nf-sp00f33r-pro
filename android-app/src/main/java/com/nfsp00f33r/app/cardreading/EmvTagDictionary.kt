package com.nfsp00f33r.app.cardreading

/**
 * EMV Tag Dictionary - Based on Proxmark3 RFIDResearchGroup/iceman fork
 * Reference: https://github.com/RfidResearchGroup/proxmark3/tree/master/client/src
 * Source files: emv_tags.c, emv_pki.c, emv_roca.c
 * 
 * Comprehensive EMV 4.3 specification tag definitions for TLV parsing
 * Used for APDU logging enhancement and real-time tag interpretation
 */
object EmvTagDictionary {
    
    /**
     * Core EMV Tag Dictionary - From Proxmark3 emv_tags.c
     * Maps tag hex values to human-readable descriptions
     */
    val EMV_TAGS = mapOf(
        // Payment System Directory (PSD) Tags
        "4F" to "Application Identifier (AID)",
        "50" to "Application Label",
        "87" to "Application Priority Indicator",
        "73" to "Directory Discretionary Template",
        "61" to "Application Template",
        
        // Application Data Tags
        "57" to "Track 2 Equivalent Data",
        "5A" to "Application Primary Account Number (PAN)",
        "5F20" to "Cardholder Name",
        "5F24" to "Application Expiration Date",
        "5F25" to "Application Effective Date",
        "5F28" to "Issuer Country Code",
        "5F2A" to "Transaction Currency Code",
        "5F2D" to "Language Preference",
        "5F30" to "Service Code",
        "5F34" to "Application PAN Sequence Number",
        "5F36" to "Transaction Currency Exponent",
        "5F50" to "Issuer URL",
        "5F53" to "International Bank Account Number (IBAN)",
        "5F54" to "Bank Identifier Code (BIC)",
        "5F55" to "Issuer Country Code (alpha2 format)",
        "5F56" to "Issuer Country Code (alpha3 format)",
        
        // Application Interchange Profile and Control
        "82" to "Application Interchange Profile (AIP)",
        "83" to "Command Template",
        "84" to "Dedicated File (DF) Name",
        "88" to "Short File Identifier (SFI)",
        "8A" to "Authorization Response Code",
        "8C" to "Card Risk Management Data Object List 1 (CDOL1)",
        "8D" to "Card Risk Management Data Object List 2 (CDOL2)",
        "8E" to "Cardholder Verification Method (CVM) List",
        "8F" to "Certification Authority Public Key Index",
        
        // Application File Locator and Records
        "94" to "Application File Locator (AFL)",
        "95" to "Terminal Verification Results (TVR)",
        "97" to "Transaction Certificate Data Object List (TDOL)",
        "98" to "Transaction Certificate (TC) Hash Value",
        "99" to "Transaction Personal Identification Number (PIN) Data",
        "9A" to "Transaction Date",
        "9B" to "Transaction Status Information (TSI)",
        "9C" to "Transaction Type",
        "9D" to "Directory Definition File (DDF) Name",
        
        // Cryptographic Data Objects
        "9F01" to "Acquirer Identifier",
        "9F02" to "Amount, Authorized (Numeric)",
        "9F03" to "Amount, Other (Numeric)",
        "9F04" to "Amount, Other (Binary)",
        "9F05" to "Application Discretionary Data",
        "9F06" to "Application Identifier (AID) - terminal",
        "9F07" to "Application Usage Control",
        "9F08" to "Application Version Number",
        "9F09" to "Application Version Number",
        "9F0A" to "Application Selection Registered Proprietary Data",
        "9F0B" to "Cardholder Name Extended",
        "9F0D" to "Issuer Action Code - Default",
        "9F0E" to "Issuer Action Code - Denial",
        "9F0F" to "Issuer Action Code - Online",
        
        // Dynamic Data Authentication
        "9F10" to "Issuer Application Data",
        "9F11" to "Issuer Code Table Index",
        "9F12" to "Application Preferred Name",
        "9F13" to "Last Online Application Transaction Counter (ATC) Register",
        "9F14" to "Lower Consecutive Offline Limit",
        "9F15" to "Merchant Category Code",
        "9F16" to "Merchant Identifier",
        "9F17" to "Personal Identification Number (PIN) Try Counter",
        "9F18" to "Issuer Script Identifier",
        "9F1A" to "Terminal Country Code",
        "9F1B" to "Terminal Floor Limit",
        "9F1C" to "Terminal Identification",
        "9F1D" to "Terminal Risk Management Data",
        "9F1E" to "Interface Device (IFD) Serial Number",
        "9F1F" to "Track 1 Discretionary Data",
        
        // Application Cryptogram and Authentication
        "9F20" to "Track 2 Discretionary Data",
        "9F21" to "Transaction Time",
        "9F22" to "Certification Authority Public Key Index",
        "9F23" to "Upper Consecutive Offline Limit",
        "9F24" to "Payment Account Reference (PAR)",
        "9F25" to "Last 4 Digits of PAN",
        "9F26" to "Application Cryptogram (AC)",
        "9F27" to "Cryptogram Information Data (CID)",
        "9F28" to "Issuer Public Key Certificate",
        "9F29" to "Issuer Public Key Exponent",
        "9F2A" to "Kernel Identifier",
        "9F2B" to "Terminal Capabilities",
        "9F2C" to "Integrated Circuit Card (ICC) Public Key Certificate",
        "9F2D" to "Integrated Circuit Card (ICC) Public Key Exponent",
        "9F2E" to "Integrated Circuit Card (ICC) Public Key Remainder",
        "9F2F" to "Integrated Circuit Card (ICC) Public Key Certificate",
        
        // Terminal and Transaction Processing
        "9F30" to "Paypass Directory",
        "9F31" to "Issuer Public Key Remainder",
        "9F32" to "Issuer Public Key Exponent",
        "9F33" to "Terminal Capabilities",
        "9F34" to "Cardholder Verification Method (CVM) Results",
        "9F35" to "Terminal Type",
        "9F36" to "Application Transaction Counter (ATC)",
        "9F37" to "Unpredictable Number",
        "9F38" to "Processing Options Data Object List (PDOL)",
        "9F39" to "Point-of-Service (POS) Entry Mode",
        "9F3A" to "Amount, Reference Currency",
        "9F3B" to "Currency Code, Reference",
        "9F3C" to "Transaction Reference Currency Code",
        "9F3D" to "Transaction Reference Currency Exponent",
        "9F40" to "Additional Terminal Capabilities",
        
        // Extended EMV Tags
        "9F41" to "Transaction Sequence Counter",
        "9F42" to "Application Currency Code",
        "9F43" to "Application Reference Currency Exponent",
        "9F44" to "Application Currency Exponent",
        "9F45" to "Data Authentication Code",
        "9F46" to "Integrated Circuit Card (ICC) Public Key Certificate",
        "9F47" to "Integrated Circuit Card (ICC) Public Key Exponent",
        "9F48" to "Integrated Circuit Card (ICC) Public Key Remainder",
        "9F49" to "Dynamic Data Authentication Data Object List (DDOL)",
        "9F4A" to "Static Data Authentication Tag List",
        "9F4B" to "Signed Dynamic Application Data",
        "9F4C" to "ICC Dynamic Number",
        "9F4D" to "Log Entry",
        "9F4E" to "Merchant Name and Location",
        "9F4F" to "Log Format",
        
        // Contactless and Mobile Payment Extensions
        "9F50" to "Offline Accumulator Balance",
        "9F51" to "Application Currency Code",
        "9F52" to "Card Verification Results (CVR)",
        "9F53" to "Consecutive Transaction Limit (International)",
        "9F54" to "Cumulative Total Transaction Amount Limit",
        "9F55" to "Geographic Indicator",
        "9F56" to "Issuer Authentication Indicator",
        "9F57" to "Issuer Country Code",
        "9F58" to "Lower Consecutive Offline Limit (Card Check)",
        "9F59" to "Upper Consecutive Offline Limit (Card Check)",
        "9F5A" to "Issuer URL2",
        "9F5B" to "Issuer Script Results",
        "9F5C" to "Cumulative Total Transaction Amount Upper Limit",
        "9F5D" to "Available Offline Spending Amount (AOSA)",
        "9F5E" to "Consecutive Transaction Limit (International - Country)",
        "9F5F" to "DS Slot Availability",
        
        // PHASE 8: Terminal Transaction Qualifiers (TTQ) and Kernel Support - ChAP Tags
        "9F60" to "CVC3 (Track1)",
        "9F61" to "CVC3 (Track2)",
        "9F62" to "PCVC3 (Track1)",
        "9F63" to "PUNATC (Track1)",
        "9F64" to "NATC (Track1)",
        "9F65" to "PCVC3 (Track2)",
        "9F66" to "Terminal Transaction Qualifiers (TTQ)",
        "9F67" to "NATC (Track2)",
        "9F68" to "Mag Stripe CVM List",
        "9F69" to "UDOL",
        "9F6A" to "Unpredictable Number (Numeric)",
        "9F6B" to "Track 2 NATC",
        "9F6C" to "Card Transaction Qualifiers (CTQ)",
        "9F6D" to "Mag Stripe Application Version Number (Reader)",
        "9F6E" to "Form Factor Indicator (FFI)",
        "9F6F" to "DS Slot Management Control",
        
        // Advanced Cryptographic Tags
        "9F70" to "Protected Data Envelope 1",
        "9F71" to "Protected Data Envelope 2",
        "9F72" to "Protected Data Envelope 3",
        "9F73" to "Protected Data Envelope 4",
        "9F74" to "Protected Data Envelope 5",
        "9F75" to "Unprotected Data Envelope 1",
        "9F76" to "Unprotected Data Envelope 2",
        "9F77" to "Unprotected Data Envelope 3",
        "9F78" to "Unprotected Data Envelope 4",
        "9F79" to "Unprotected Data Envelope 5",
        "9F7A" to "VLP Issuer Authorization Code",
        "9F7B" to "VLP Terminal Support Indicator",
        "9F7C" to "Customer Exclusive Data (CED)",
        "9F7D" to "DS Summary 1",
        "9F7E" to "Mobile Support Indicator",
        "9F7F" to "DS Summary Status",
        
        // Extended EMV Tags (8xxx series)
        "80" to "Response Message Template Format 1",
        "81" to "Amount, Authorized (Binary)",
        "86" to "Issuer Script Command",
        "89" to "Authorization Code",
        "8A" to "Authorization Response Code",
        "8B" to "Authorization Response Cryptogram",
        "8C" to "Card Risk Management Data Object List 1 (CDOL1)",
        "8D" to "Card Risk Management Data Object List 2 (CDOL2)",
        "8E" to "Cardholder Verification Method (CVM) List",
        "8F" to "Certification Authority Public Key Index",
        
        // BER-TLV Template Tags
        "70" to "READ RECORD Response Message Template",
        "77" to "Response Message Template Format 2",
        "80" to "Response Template",
        "A5" to "File Control Information (FCI) Proprietary Template",
        "6F" to "File Control Information (FCI) Template",
        "61" to "Application Template",
        "73" to "Directory Discretionary Template",
        "E1" to "IPS Template",
        "E2" to "IPS Template",
        "E3" to "IPS Template",
        "E4" to "IPS Template",
        "E5" to "IPS Template",
        
        // Mastercard Specific Tags (9Fxx extensions)
        "9F80" to "Electronic Commerce Indicator",
        "9F81" to "Electronic Commerce Indicator",
        "9F82" to "Electronic Commerce Indicator",
        "9F83" to "Electronic Commerce Indicator", 
        "9F84" to "Electronic Commerce Indicator",
        "9F85" to "Electronic Commerce Indicator",
        "9F86" to "Electronic Commerce Indicator",
        "9F87" to "Electronic Commerce Indicator",
        "9F88" to "Electronic Commerce Indicator",
        "9F89" to "Electronic Commerce Indicator",
        "9F8A" to "Electronic Commerce Indicator",
        
        // Visa Specific Tags  
        "9F90" to "Issuer Public Key Certificate",
        "9F91" to "Issuer Authentication Data",
        "9F92" to "Issuer Public Key Remainder",
        "9F93" to "Signed Static Application Data",
        "9F94" to "Signed Dynamic Application Data",
        "9F95" to "Terminal Verification Results",
        "9F96" to "TX Count",
        "9F97" to "TX Count Limit",
        "9F98" to "TX Count Upper Limit",
        "9F99" to "Transaction Personal Identification Number (PIN) Data",
        "9F9A" to "Transaction Status Information",
        "9F9B" to "Transaction Status Information",
        
        // American Express Specific Tags
        "9FA0" to "AX Specific Tag",
        "9FA1" to "AX Specific Tag",
        "9FA2" to "AX Specific Tag",
        "9FA3" to "AX Specific Tag",
        "9FA4" to "AX Specific Tag",
        "9FA5" to "AX Specific Tag",
        
        // JCU/UnionPay Specific Tags
        "9FB0" to "UnionPay Specific Tag",
        "9FB1" to "UnionPay Specific Tag", 
        "9FB2" to "UnionPay Specific Tag",
        "9FB3" to "UnionPay Specific Tag",
        "9FB4" to "UnionPay Specific Tag",
        
        // Additional BER-TLV Context Tags
        "A0" to "Context-Specific Template",
        "A1" to "Context-Specific Template",
        "A2" to "Context-Specific Template", 
        "A3" to "Context-Specific Template",
        "A4" to "Context-Specific Template",
        "A6" to "Context-Specific Template",
        "A7" to "Context-Specific Template",
        "A8" to "Context-Specific Template",
        "A9" to "Context-Specific Template",
        "AA" to "Context-Specific Template",
        "AB" to "Context-Specific Template",
        "AC" to "Context-Specific Template",
        "AD" to "Context-Specific Template",
        "AE" to "Context-Specific Template",
        "AF" to "Context-Specific Template",
        
        // Length and Primitive Tags (for parsing validation)
        "00" to "NULL",
        "01" to "BOOLEAN",
        "02" to "INTEGER", 
        "03" to "BIT STRING",
        "04" to "OCTET STRING",
        "05" to "NULL",
        "06" to "OBJECT IDENTIFIER",
        "07" to "OBJECT DESCRIPTOR",
        "08" to "EXTERNAL",
        "09" to "REAL",
        "0A" to "ENUMERATED",
        "0B" to "EMBEDDED PDV",
        "0C" to "UTF8String",
        "0D" to "RELATIVE-OID",
        
        // Additional Proxmark3 EMV Tags - From RFIDResearchGroup Source
        "41" to "Country code and national data",
        "42" to "Issuer Identification Number (IIN)",
        "51" to "File reference data element",
        "52" to "Command APDU",
        "53" to "Discretionary data (or template)",
        "56" to "Track 1 Data",
        "86" to "Issuer Script Command",
        "89" to "Authorization Code",
        "90" to "Issuer Public Key Certificate",
        "91" to "Issuer Authentication Data",
        "92" to "Issuer Public Key Remainder",
        "93" to "Signed Static Application Data",
        "96" to "Kernel Identifier",
        "97" to "Transaction Certificate Data Object List (TDOL)",
        "98" to "Transaction Certificate (TC) Hash Value",
        "99" to "Transaction Personal Identification Number (PIN) Data",
        
        // Extended 9Fxx Tags from Proxmark3
        "9F19" to "Token Requestor ID",
        "9F2A" to "Kernel Identifier", 
        "9F2D" to "ICC PIN Encipherment Public Key Certificate",
        "9F2E" to "ICC PIN Encipherment Public Key Exponent",
        "9F2F" to "ICC PIN Encipherment Public Key Remainder",
        "9F3B" to "Application Reference Currency",
        "9F51" to "Application Currency Code / DRDOL",
        "9F52" to "Application Default Action (ADA) / Terminal Compatibility Indicator",
        "9F53" to "Transaction Category Code",
        "9F54" to "DS ODS Card",
        "9F55" to "Mobile Support Indicator / Issuer Authentication Flags",
        "9F56" to "Issuer Authentication Indicator",
        "9F57" to "Issuer Country Code",
        "9F58" to "Consecutive Transaction Counter Limit (CTCL)",
        "9F59" to "Consecutive Transaction Counter Upper Limit (CTCUL)",
        "9F5A" to "Application Program Identifier",
        "9F5B" to "Issuer Script Results",
        "9F5C" to "Cumulative Total Transaction Amount Upper Limit (CTTAUL)",
        "9F5D" to "Application Capabilities Information",
        "9F5E" to "Data Storage Identifier",
        "9F6E" to "Form Factor Indicator",
        "9F6F" to "DS Slot Management Control",
        "9F7C" to "Merchant Custom Data / Customer Exclusive Data (CED)",
        "9F7D" to "DS Summary 1",
        "9F7E" to "Application Life Cycle Data",
        "9F7F" to "DS Unpredictable Number",
        
        // PHASE 8: Proprietary Tags from Proxmark3 + ChAP Extensions
        "DF01" to "Reference PIN",
        "DF02" to "Issuer Proprietary Data",
        "DF03" to "Card Data",
        "DF04" to "Cardholder Verification Rule",
        "DF05" to "Application Capabilities",
        "DF20" to "Issuer Proprietary Bitmap (IPB)",
        "DF21" to "Track 2 Data",
        "DF22" to "Track 1 Data",
        "DF23" to "Card Number",
        "DF24" to "Expiry Date",
        "DF25" to "Effective Date",
        "DF26" to "Issuer Country Code",
        "DF27" to "Payment Account Reference",
        "DF28" to "Card Sequence Number",
        "DF29" to "PAN Hash",
        "DF2A" to "Application Label",
        "DF2B" to "Application Priority Indicator",
        "DF2C" to "Terminal Risk Management Data",
        "DF2D" to "Terminal Capabilities",
        "DF2E" to "Terminal Type",
        "DF2F" to "Terminal Country Code",
        "DF30" to "Terminal Identification",
        "DF31" to "Terminal Floor Limit",
        "DF32" to "Threshold Value for Biased Random Selection",
        "DF33" to "Target Percentage for Random Selection",
        "DF34" to "Maximum Target Percentage",
        "DF35" to "Merchant Identifier",
        "DF36" to "Merchant Category Code",
        "DF37" to "Merchant Name and Location",
        "DF38" to "Acquirer Identifier",
        "DF39" to "Terminal Transaction Qualifiers",
        "DF3A" to "Terminal Action Code Default",
        "DF3B" to "Terminal Action Code Denial",
        "DF3C" to "Terminal Action Code Online",
        "DF3D" to "Reader Contactless Floor Limit",
        "DF3E" to "Reader Contactless Transaction Limit",
        "DF3F" to "Reader CVM Required Limit",
        "DF40" to "Time Out Value",
        "DF41" to "IDS Status",
        "DF42" to "Outcome Parameter Set",
        "DF43" to "User Interface Request Data",
        "DF44" to "Data Record",
        "DF45" to "Discretionary Data",
        "DF46" to "Enhanced Contactless Reader Capabilities",
        "DF47" to "Message Hold Time",
        "DF48" to "Hold Time Value",
        "DF49" to "Phone Message Table",
        "DF4A" to "Default TDOL",
        "DF4B" to "POS Cardholder Interaction Information",
        "DF4C" to "Kernel Configuration",
        "DF4D" to "Default DDOL",
        "DF4E" to "Terminal Expected Transmission Time",
        "DF4F" to "Terminal Expected Transmission Time for Relay Resistance",
        "DF50" to "RRP Counter",
        "DF51" to "Minimum Time for Processing Relay Resistance",
        "DF52" to "Max Time for Processing Relay Resistance",
        "DF53" to "Device Estimated Transmission Time",
        "DF54" to "Measured Relay Resistance Processing Time",
        "DF55" to "RRP Accuracy Threshold",
        "DF56" to "Terminal Action Code Relay Resistance",
        "DF57" to "Relay Resistance Accuracy Threshold",
        "DF58" to "Relay Resistance Time Limit",
        "DF59" to "Terminal Relay Resistance Entropy",
        "DF5A" to "Device Relay Resistance Entropy",
        "DF5B" to "Minimum Relay Resistance Grace Period",
        "DF5C" to "Maximum Relay Resistance Grace Period",
        "DF5D" to "Terminal Relay Resistance Transmission",
        "DF5E" to "Device Relay Resistance Transmission",
        "DF5F" to "Measured Relay Resistance Accuracy",
        "DF60" to "VISA Log Entry",
        "DF61" to "DS Digest H",
        "DF62" to "DS ODS Info",
        "DF63" to "DS ODS Term",
        
        // Kernel Configuration Tags (DFxxxx series)
        "DF8104" to "Balance Read Before Gen AC",
        "DF8105" to "Balance Read After Gen AC",
        "DF8106" to "Data Needed",
        "DF8107" to "CDOL1 Related Data",
        "DF8108" to "DS AC Type",
        "DF8109" to "DS Input (Term)",
        "DF810A" to "DS ODS Info For Reader",
        "DF810B" to "DS Summary Status",
        "DF810C" to "Kernel ID",
        "DF810D" to "DSVN Term",
        "DF810E" to "Post-Gen AC Put Data Status",
        "DF810F" to "Pre-Gen AC Put Data Status",
        "DF8110" to "Proceed To First Write Flag",
        "DF8111" to "PDOL Related Data",
        "DF8112" to "Tags To Read",
        "DF8113" to "DRDOL Related Data",
        "DF8114" to "Reference Control Parameter",
        "DF8115" to "Error Indication",
        "DF8116" to "User Interface Request Data",
        "DF8117" to "Card Data Input Capability",
        "DF8118" to "CVM Capability - CVM Required",
        "DF8119" to "CVM Capability - No CVM Required",
        "DF811A" to "Default UDOL",
        "DF811B" to "Kernel Configuration",
        "DF811C" to "Max Lifetime of Torn Transaction Log Record",
        "DF811D" to "Max Number of Torn Transaction Log Records",
        "DF811E" to "Mag-stripe CVM Capability - CVM Required",
        "DF811F" to "Security Capability",
        "DF8120" to "Terminal Action Code - Default",
        "DF8121" to "Terminal Action Code - Denial",
        "DF8122" to "Terminal Action Code - Online",
        "DF8123" to "Reader Contactless Floor Limit",
        "DF8124" to "Reader Contactless Transaction Limit (No On-device CVM)",
        "DF8125" to "Reader Contactless Transaction Limit (On-device CVM)",
        "DF8126" to "Reader CVM Required Limit",
        "DF8127" to "TIME_OUT_VALUE",
        "DF8128" to "IDS Status",
        "DF8129" to "Outcome Parameter Set",
        "DF812A" to "DD Card (Track1)",
        "DF812B" to "DD Card (Track2)",
        "DF812C" to "Mag-stripe CVM Capability - No CVM Required",
        "DF812D" to "Message Hold Time",
        
        // EMV Kernel Specific Tags (FFxxxx series)
        "FF8101" to "Torn Record",
        "FF8102" to "Tags To Write Before Gen AC",
        "FF8103" to "Tags To Write After Gen AC",
        "FF8104" to "Data To Send",
        "FF8105" to "Data Record",
        "FF8106" to "Discretionary Data",
        
        // Proprietary and Regional Tags  
        "A5" to "File Control Information (FCI) Proprietary Template",
        "BF0C" to "File Control Information (FCI) Issuer Discretionary Data",
        "C1" to "Application Control",
        "C2" to "Application Control",
        "C3" to "Application Control",
        "C4" to "Application Control",
        "C5" to "Application Control",
        "C6" to "PIN Change/Unblock",
        "C7" to "PIN Change/Unblock",
        "C8" to "PIN Change/Unblock",
        "C9" to "PIN Change/Unblock",
        "CA" to "Lower Consecutive Offline Limit (LCOL)",
        "CB" to "Upper Consecutive Offline Limit (UCOL)",
        "CD" to "CV Rule",
        "CE" to "CV Rule",
        "CF" to "CV Rule",
        "D1" to "PIN Change/Unblock",
        "D2" to "PIN Change/Unblock",
        "D3" to "Application Control",
        "D4" to "Application Control",
        "D5" to "Application Control",
        "D6" to "Application Control",
        "D7" to "Application Control",
        "D8" to "Application Control",
        "D9" to "Application Control",
        "DA" to "Static CVC3-TRACK1",
        "DB" to "Static CVC3-TRACK2",
        "DC" to "IVCVC3-TRACK1",
        "DD" to "IVCVC3-TRACK2",
        "DF01" to "Reference PIN",
        "DF02" to "Issuer Public Key Certificate",
        "DF03" to "Issuer Public Key Exponent",
        "DF04" to "Issuer Public Key Remainder",
        "DF05" to "IC Card Public Key Certificate",
        "DF06" to "IC Card Public Key Exponent",
        "DF07" to "IC Card Public Key Remainder"
    )
    
    /**
     * Get tag description with fallback
     */
    fun getTagDescription(tag: String): String {
        val cleanTag = tag.uppercase().replace(" ", "")
        return EMV_TAGS[cleanTag] ?: "Unknown Tag ($cleanTag)"
    }
    
    /**
     * Check if tag is a critical EMV tag for APDU logging
     */
    fun isCriticalTag(tag: String): Boolean {
        val criticalTags = setOf(
            "4F", "50", "57", "5A", "5F20", "5F24", "82", "94", 
            "9F02", "9F03", "9F10", "9F26", "9F27", "9F36", "9F37",
            "9F38", "9F66", "95", "9B"
        )
        return criticalTags.contains(tag.uppercase().replace(" ", ""))
    }
    
    /**
     * Get tag category for enhanced APDU logging
     */
    fun getTagCategory(tag: String): String {
        val cleanTag = tag.uppercase().replace(" ", "")
        return when {
            cleanTag.startsWith("9F2") -> "Authentication & Crypto"
            cleanTag.startsWith("9F0") -> "Application Control"
            cleanTag.startsWith("9F1") -> "Terminal Data"
            cleanTag.startsWith("9F3") -> "Transaction Processing"
            cleanTag.startsWith("9F4") -> "ICC Authentication"
            cleanTag.startsWith("9F5") -> "Risk Management"
            cleanTag.startsWith("9F6") -> "Contactless Extensions"
            cleanTag.startsWith("9F7") -> "Advanced Features"
            cleanTag.startsWith("5F") -> "Application Data"
            cleanTag in listOf("82", "94", "95", "9B") -> "Core EMV"
            cleanTag in listOf("4F", "50", "87") -> "Application Selection"
            cleanTag in listOf("57", "5A") -> "Account Data"
            else -> "Other"
        }
    }
    
    /**
     * Enhanced APDU log formatting with tag descriptions
     * Integrates with existing ApduLogEntry for real-time display
     */
    fun enhanceApduDescription(command: String, response: String, originalDescription: String): String {
        val enhancedDescription = StringBuilder(originalDescription)
        
        // Parse command for SELECT operations
        if (command.startsWith("00A404")) {
            enhancedDescription.append(" | SELECT Application")
        } else if (command.startsWith("00A40400")) {
            enhancedDescription.append(" | SELECT by Name")
        } else if (command.startsWith("80A8")) {
            enhancedDescription.append(" | GET PROCESSING OPTIONS")
        } else if (command.startsWith("00B2")) {
            enhancedDescription.append(" | READ RECORD")
        }
        
        // Parse response for EMV tags (basic BER-TLV detection)
        val tagMatches = Regex("([0-9A-Fa-f]{2,4})([0-9A-Fa-f]{2})").findAll(response)
        val foundTags = mutableListOf<String>()
        
        for (match in tagMatches) {
            val possibleTag = match.groupValues[1]
            if (EMV_TAGS.containsKey(possibleTag.uppercase())) {
                foundTags.add(possibleTag.uppercase())
            }
        }
        
        if (foundTags.isNotEmpty()) {
            val tagDescriptions = foundTags.take(3).joinToString(", ") { tag ->
                "${tag}=${getTagDescription(tag)}"
            }
            enhancedDescription.append(" | Tags: $tagDescriptions")
            if (foundTags.size > 3) {
                enhancedDescription.append(" (+${foundTags.size - 3} more)")
            }
        }
        
        return enhancedDescription.toString()
    }
    
    /**
     * ROCA Vulnerability Detection Tags - From Proxmark3 emv_roca.c
     * CVE-2017-15361 detection for RSA public key certificates
     */
    val ROCA_DETECTION_TAGS = setOf(
        "9F28", // Issuer Public Key Certificate
        "9F2C", // ICC Public Key Certificate  
        "9F46", // ICC Public Key Certificate
        "9F2F", // ICC Public Key Certificate
        "DF02", // Issuer Public Key Certificate (proprietary)
        "DF05"  // IC Card Public Key Certificate (proprietary)
    )
    
    /**
     * Check if tag contains RSA certificate data for ROCA analysis
     */
    fun isRocaVulnerableTag(tag: String): Boolean {
        return ROCA_DETECTION_TAGS.contains(tag.uppercase().replace(" ", ""))
    }
}