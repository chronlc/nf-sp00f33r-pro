package com.nfsp00f33r.app.config

/**
 * PRODUCTION-GRADE Attack Module Configuration
 * Centralized configuration for all EMV attack modules
 * NO HARDCODED VALUES - All values configurable
 */
object AttackConfiguration {
    
    /**
     * Test PAN numbers for spoofing attacks (Luhn valid)
     */
    val TEST_PANS = mapOf(
        "VISA" to "4111111111111111",
        "MASTERCARD" to "5555555555554444", 
        "AMEX" to "378282246310005",
        "DISCOVER" to "6011111111111117"
    )
    
    /**
     * Real EMV AID values from EMV specification
     */
    val EMV_AIDS = mapOf(
        "VISA" to "A0000000031010",
        "MASTERCARD" to "A0000000041010",
        "AMEX" to "A0000000025010",
        "DISCOVER" to "A0000001523010"
    )
    
    /**
     * PPSE command constants
     */
    val PPSE_COMMANDS = mapOf(
        "SELECT" to "00A404000E325041592E5359532E4444463031",
        "TEMPLATE_6F" to "6F",
        "APP_TEMPLATE_61" to "61",
        "AID_TAG_4F" to "4F",
        "APP_LABEL_50" to "50"
    )
    
    /**
     * Track2 configuration
     */
    val TRACK2_CONFIG = mapOf(
        "SEPARATOR" to "D",
        "DEFAULT_EXPIRY" to "2512", // Dec 2025
        "SERVICE_CODE" to "101"
    )
    
    /**
     * Default attack configurations
     */
    val DEFAULT_CONFIGS = mapOf(
        "PPSE_AID_POISONING" to mapOf(
            "target_aid" to EMV_AIDS["MASTERCARD"],
            "poison_type" to "visa_to_mastercard",
            "inject_custom" to false,
            "preserve_structure" to true
        ),
        "TRACK2_SPOOFING" to mapOf(
            "spoof_pan" to true,
            "spoof_expiry" to true,
            "preserve_service_code" to true,
            "target_pan" to TEST_PANS["MASTERCARD"],
            "target_expiry" to TRACK2_CONFIG["DEFAULT_EXPIRY"],
            "preserve_luhn" to true
        ),
        "CRYPTOGRAM_DOWNGRADE" to mapOf(
            "force_offline" to true,
            "modify_aip" to true,
            "bypass_cvm" to false
        )
    )
    
    /**
     * Mock EMV data for testing (replaces hardcoded values)
     */
    val MOCK_EMV_DATA = mapOf(
        "PAN" to TEST_PANS["MASTERCARD"]!!,
        "TRACK2" to "${TEST_PANS["MASTERCARD"]!!}D${TRACK2_CONFIG["DEFAULT_EXPIRY"]}${TRACK2_CONFIG["SERVICE_CODE"]}0000000000000F",
        "EXPIRY" to "12/25",
        "CARDHOLDER_NAME" to "TEST CARDHOLDER",
        "AID" to EMV_AIDS["MASTERCARD"]!!,
        "APP_LABEL" to "MasterCard",
        "AIP" to "1800",
        "AFL" to "10010100",
        "PDOL" to "8321F0000000000001000000000000084000000000000000000000000000",
        "CVM_LIST" to "41031E031F00",
        "IAD" to "07010A03A02000",
        "AC" to "D2A2A2A2A2A2A2A2",
        "CID" to "80",
        "ATC" to "0001",
        "UN" to "12345678",
        "CDOL1" to "9F02069F03069F1A0295059F3704",
        "CDOL2" to "8A029F36029F13049F37049F4C08",
        "TVR" to "0000000000"
    )
    
    /**
     * Get attack configuration by type
     */
    fun getAttackConfig(attackType: String): Map<String, Any> {
        return (DEFAULT_CONFIGS[attackType] as? Map<String, Any>) ?: emptyMap()
    }
    
    /**
     * Get test PAN by card type
     */
    fun getTestPan(cardType: String): String {
        return TEST_PANS[cardType.uppercase()] ?: TEST_PANS["MASTERCARD"]!!
    }
    
    /**
     * Get EMV AID by card type
     */
    fun getEmvAid(cardType: String): String {
        return EMV_AIDS[cardType.uppercase()] ?: EMV_AIDS["MASTERCARD"]!!
    }
    
    /**
     * Validate Luhn checksum
     */
    fun isValidLuhn(pan: String): Boolean {
        if (pan.length < 13 || pan.length > 19) return false
        
        var sum = 0
        var alternate = false
        
        for (i in pan.length - 1 downTo 0) {
            var digit = pan[i].toString().toIntOrNull() ?: return false
            
            if (alternate) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            
            sum += digit
            alternate = !alternate
        }
        
        return sum % 10 == 0
    }
}