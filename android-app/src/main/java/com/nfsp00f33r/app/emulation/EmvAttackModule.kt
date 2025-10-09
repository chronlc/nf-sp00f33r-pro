package com.nfsp00f33r.app.emulation

/**
 * EMV Attack Module Interface
 * Base interface for all EMV attack modules implementing specific attack vectors
 * Based on attack_module_architecture.md specifications
 * Updated for production-grade attack module system
 */
interface EmvAttackModule {
    
    /**
     * Get unique attack identifier
     * @return Attack ID string (e.g., "ppse_aid_poisoning")
     */
    fun getAttackId(): String
    
    /**
     * Get human-readable attack description
     * @return Description of what this attack does
     */
    fun getDescription(): String
    
    /**
     * Check if this attack applies to the current command/context
     * @param command The APDU command being processed
     * @param cardData Current card data context
     * @return true if attack should be applied, false otherwise
     */
    fun isApplicable(command: ByteArray, cardData: Map<String, Any>): Boolean
    
    /**
     * Apply attack to the APDU response
     * @param command Original APDU command
     * @param response Original APDU response
     * @param cardData Current card data context
     * @return Modified APDU response with attack applied
     */
    fun applyAttack(command: ByteArray, response: ByteArray, cardData: Map<String, Any>): ByteArray
    
    /**
     * Configure the attack module with runtime parameters
     * @param config Configuration map with attack-specific parameters
     */
    fun configure(config: Map<String, Any>)
    
    /**
     * Get current configuration of the attack module
     * @return Current configuration map
     */
    fun getConfiguration(): Map<String, Any>
    
    /**
     * Get attack statistics and performance metrics
     * @return Statistics map including attack count, success rate, etc.
     */
    fun getAttackStatistics(): Map<String, Any>
    
    /**
     * Reset attack statistics and state
     */
    fun reset()
}
