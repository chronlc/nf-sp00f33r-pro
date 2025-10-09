package com.nfsp00f33r.app.data

/**
 * EMV Workflow definitions for TTQ manipulation and advanced EMV data extraction
 * Based on comprehensive EMV 4.3 specifications and RFIDIOt research
 */
data class EmvWorkflow(
    val id: String,
    val name: String,
    val description: String,
    val ttqValue: String,
    val terminalCapabilities: String,
    val additionalCapabilities: String,
    val cvmCapability: String,
    val expectedDataPoints: List<String>
) {
    companion object {
        
        /**
         * Standard EMV Workflows for comprehensive data extraction
         */
        val STANDARD_CONTACTLESS = EmvWorkflow(
            id = "standard",
            name = "Standard Contactless",
            description = "Standard EMV contactless workflow with basic data retrieval",
            ttqValue = "27000000", // Standard contactless TTQ
            terminalCapabilities = "E0F8C8", // Standard capabilities
            additionalCapabilities = "6000F0A001", // Standard additional
            cvmCapability = "420000", // Online PIN supported
            expectedDataPoints = listOf("PAN", "Track2", "AIP", "AFL", "App Label")
        )
        
        val OFFLINE_FORCED = EmvWorkflow(
            id = "offline_forced",
            name = "Offline Pin Forced",
            description = "Force offline PIN verification to extract additional cryptographic data",
            ttqValue = "2F000000", // Force offline PIN + additional flags
            terminalCapabilities = "E0F8C8", // Same capabilities
            additionalCapabilities = "6000F0A001", // Enhanced additional
            cvmCapability = "1E0000", // Offline PIN forced
            expectedDataPoints = listOf("PAN", "Track2", "AEF", "CVM List", "PIN Block", "CDOL1", "CDOL2")
        )
        
        val CVM_REQUIRED = EmvWorkflow(
            id = "cvm_required", 
            name = "CVM Required",
            description = "Enforce cardholder verification to extract CVM data and preferences",
            ttqValue = "67000000", // CVM required + signature
            terminalCapabilities = "E0F8C8", // Full capabilities
            additionalCapabilities = "FF00F0A001", // Enhanced CVM capabilities
            cvmCapability = "5E0000", // All CVM methods supported
            expectedDataPoints = listOf("PAN", "Track2", "CVM List", "CVM Results", "PIN Try Counter", "Issuer Auth Data")
        )
        
        val ISSUER_AUTHENTICATION = EmvWorkflow(
            id = "issuer_auth",
            name = "Issuer Authentication",
            description = "Trigger issuer authentication to extract cryptographic keys and certificates",
            ttqValue = "A7000000", // Issuer auth required
            terminalCapabilities = "E0F8C8", // Crypto capabilities
            additionalCapabilities = "FF00F0A001", // Full crypto support
            cvmCapability = "5E0000", // All methods
            expectedDataPoints = listOf("PAN", "Track2", "Issuer Public Key", "ICC Public Key", "Issuer Auth Data", "Dynamic Data")
        )
        
        val ENHANCED_DISCOVERY = EmvWorkflow(
            id = "enhanced_discovery",
            name = "Enhanced Discovery", 
            description = "Maximum data extraction workflow for comprehensive EMV analysis",
            ttqValue = "FF800000", // All flags enabled for maximum data
            terminalCapabilities = "E0F8C8", // Full terminal capabilities
            additionalCapabilities = "FF00F0A001", // All additional capabilities
            cvmCapability = "5E0000", // All CVM methods
            expectedDataPoints = listOf("PAN", "Track2", "All TLV Tags", "CDOL1", "CDOL2", "CVM Data", "Crypto Data", "Issuer Data")
        )
        
        val CUSTOM_RESEARCH = EmvWorkflow(
            id = "custom_research",
            name = "Custom Research",
            description = "Customizable workflow for specific EMV security research",
            ttqValue = "27000000", // Default, user customizable
            terminalCapabilities = "E0F8C8", // Default, user customizable  
            additionalCapabilities = "6000F0A001", // Default, user customizable
            cvmCapability = "420000", // Default, user customizable
            expectedDataPoints = listOf("User Defined")
        )
        
        /**
         * All available workflows for selection
         */
        fun getAllWorkflows(): List<EmvWorkflow> {
            return listOf(
                STANDARD_CONTACTLESS,
                OFFLINE_FORCED,
                CVM_REQUIRED,
                ISSUER_AUTHENTICATION,
                ENHANCED_DISCOVERY,
                CUSTOM_RESEARCH
            )
        }
        
        /**
         * Get workflow by ID
         */
        fun getWorkflowById(id: String): EmvWorkflow? {
            return getAllWorkflows().find { it.id == id }
        }
        
        /**
         * TTQ bit breakdown for analysis
         */
        fun analyzeTtq(ttqHex: String): Map<String, Boolean> {
            val ttqValue = ttqHex.toLongOrNull(16) ?: 0L
            
            return mapOf(
                "Offline Data Authentication" to ((ttqValue and 0x80000000L) != 0L),
                "Cardholder Verification" to ((ttqValue and 0x40000000L) != 0L),
                "Card Capture" to ((ttqValue and 0x20000000L) != 0L),
                "Issuer Authentication" to ((ttqValue and 0x10000000L) != 0L),
                "CVM Required" to ((ttqValue and 0x08000000L) != 0L),
                "Online PIN Required" to ((ttqValue and 0x04000000L) != 0L),
                "Signature Required" to ((ttqValue and 0x02000000L) != 0L),
                "Go Online if Offline Failed" to ((ttqValue and 0x01000000L) != 0L)
            )
        }
    }
}
