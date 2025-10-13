package com.nfsp00f33r.app.emv

import timber.log.Timber

/**
 * Cardholder Verification Method (CVM) Processor
 * Parses and evaluates CVM List from EMV card (tag 0x8E)
 * 
 * Based on Proxmark3 CVM processing but adapted for Android
 * EMV Book 3, Annex C3
 */
object CvmProcessor {
    
    /**
     * CVM Method codes (first byte of CVM rule)
     */
    enum class CvmMethod(val code: Int, val label: String, val requiresPin: Boolean) {
        FAIL_CVM(0x00, "Fail CVM Processing", false),
        PLAINTEXT_PIN_ICC(0x01, "Plaintext PIN verification by ICC", true),
        ENCIPHERED_PIN_ONLINE(0x02, "Enciphered PIN verification online", true),
        PLAINTEXT_PIN_ICC_AND_SIGNATURE(0x03, "Plaintext PIN by ICC and signature", true),
        ENCIPHERED_PIN_ICC(0x04, "Enciphered PIN verification by ICC", true),
        ENCIPHERED_PIN_ICC_AND_SIGNATURE(0x05, "Enciphered PIN by ICC and signature", true),
        SIGNATURE(0x1E, "Signature (paper)", false),
        NO_CVM(0x1F, "No CVM Required", false),
        RESERVED(0xFF, "Reserved/Unknown", false);
        
        companion object {
            fun from(code: Int): CvmMethod {
                val maskedCode = code and 0x3F // Remove condition fail/next bits
                return values().firstOrNull { it.code == maskedCode } ?: RESERVED
            }
        }
    }
    
    /**
     * CVM Condition codes (second byte of CVM rule)
     */
    enum class CvmCondition(val code: Int, val label: String) {
        ALWAYS(0x00, "Always"),
        IF_UNATTENDED_CASH(0x01, "If unattended cash"),
        IF_NOT_UNATTENDED_CASH_AND_NOT_MANUAL_CASH(0x02, "If not (unattended cash or manual cash) and not purchase with cashback"),
        IF_TERMINAL_SUPPORTS_CVM(0x03, "If terminal supports the CVM"),
        IF_MANUAL_CASH(0x04, "If manual cash"),
        IF_PURCHASE_WITH_CASHBACK(0x05, "If purchase with cashback"),
        IF_TRANSACTION_IN_APPLICATION_CURRENCY(0x06, "If transaction is in application currency"),
        IF_TRANSACTION_OVER_X(0x07, "If transaction is over X value"),
        IF_TRANSACTION_UNDER_X(0x08, "If transaction is under X value"),
        IF_TRANSACTION_OVER_Y(0x09, "If transaction is over Y value"),
        IF_TRANSACTION_UNDER_Y(0x0A, "If transaction is under Y value"),
        RESERVED(0xFF, "Reserved/Unknown");
        
        companion object {
            fun from(code: Int): CvmCondition {
                return values().firstOrNull { it.code == code } ?: RESERVED
            }
        }
    }
    
    /**
     * Parsed CVM rule
     */
    data class CvmRule(
        val method: CvmMethod,
        val condition: CvmCondition,
        val conditionFailAction: ConditionFailAction,
        val rawBytes: ByteArray
    ) {
        enum class ConditionFailAction {
            FAIL,  // Bit 7 = 0, Bit 6 = 0
            NEXT   // Bit 7 = 0, Bit 6 = 1
        }
        
        override fun toString(): String {
            return "${method.label} - ${condition.label} [${if (conditionFailAction == ConditionFailAction.NEXT) "Next" else "Fail"}]"
        }
    }
    
    /**
     * Parsed CVM List
     */
    data class CvmList(
        val amountX: Long,  // in cents
        val amountY: Long,  // in cents
        val rules: List<CvmRule>
    ) {
        fun getPreferredMethod(): CvmMethod? {
            return rules.firstOrNull()?.method
        }
        
        fun requiresPin(): Boolean {
            return rules.any { it.method.requiresPin }
        }
        
        fun supportsNoCvm(): Boolean {
            return rules.any { it.method == CvmMethod.NO_CVM }
        }
    }
    
    /**
     * Parse CVM List from tag 0x8E
     * 
     * Structure:
     * - Bytes 1-4: Amount X (BCD, 4 bytes)
     * - Bytes 5-8: Amount Y (BCD, 4 bytes)
     * - Bytes 9+: CVM rules (2 bytes each)
     *   - Byte 1: CVM code (bits 0-5) + fail action (bits 6-7)
     *   - Byte 2: CVM condition code
     */
    fun parseCvmList(cvmListBytes: ByteArray): CvmList? {
        if (cvmListBytes.size < 10) {
            Timber.w("⚠️ CVM List too short: ${cvmListBytes.size} bytes")
            return null
        }
        
        try {
            // Parse Amount X (bytes 0-3, BCD encoded)
            val amountX = decodeBcdAmount(cvmListBytes.sliceArray(0..3))
            
            // Parse Amount Y (bytes 4-7, BCD encoded)
            val amountY = decodeBcdAmount(cvmListBytes.sliceArray(4..7))
            
            // Parse CVM rules (remaining bytes, 2 bytes per rule)
            val rules = mutableListOf<CvmRule>()
            var offset = 8
            
            while (offset + 1 < cvmListBytes.size) {
                val byte1 = cvmListBytes[offset].toInt() and 0xFF
                val byte2 = cvmListBytes[offset + 1].toInt() and 0xFF
                
                // Decode CVM method (bits 0-5 of byte 1)
                val methodCode = byte1 and 0x3F
                val method = CvmMethod.from(methodCode)
                
                // Decode fail action (bits 6-7 of byte 1)
                val failAction = if ((byte1 and 0x40) != 0) {
                    CvmRule.ConditionFailAction.NEXT
                } else {
                    CvmRule.ConditionFailAction.FAIL
                }
                
                // Decode condition (byte 2)
                val condition = CvmCondition.from(byte2)
                
                rules.add(CvmRule(
                    method = method,
                    condition = condition,
                    conditionFailAction = failAction,
                    rawBytes = byteArrayOf(cvmListBytes[offset], cvmListBytes[offset + 1])
                ))
                
                offset += 2
            }
            
            val cvmList = CvmList(amountX, amountY, rules)
            
            Timber.d("🔐 Parsed CVM List:")
            Timber.d("  Amount X: \$%.2f", amountX / 100.0)
            Timber.d("  Amount Y: \$%.2f", amountY / 100.0)
            Timber.d("  Rules (${rules.size}):")
            rules.forEachIndexed { index, rule ->
                Timber.d("    ${index + 1}. $rule")
            }
            
            return cvmList
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Failed to parse CVM List")
            return null
        }
    }
    
    /**
     * Decode BCD amount from 4 bytes
     */
    private fun decodeBcdAmount(bytes: ByteArray): Long {
        if (bytes.size != 4) return 0
        
        var amount = 0L
        for (byte in bytes) {
            val highNibble = (byte.toInt() and 0xF0) shr 4
            val lowNibble = byte.toInt() and 0x0F
            amount = amount * 100 + highNibble * 10 + lowNibble
        }
        return amount
    }
    
    /**
     * Get human-readable CVM summary
     */
    fun getCvmSummary(cvmListBytes: ByteArray?): String {
        if (cvmListBytes == null) return "No CVM List"
        
        val cvmList = parseCvmList(cvmListBytes) ?: return "Invalid CVM List"
        
        return buildString {
            append("Preferred: ${cvmList.getPreferredMethod()?.label ?: "None"}")
            if (cvmList.requiresPin()) {
                append(" (PIN Required)")
            }
            if (cvmList.supportsNoCvm()) {
                append(" | No CVM Supported")
            }
        }
    }
}
