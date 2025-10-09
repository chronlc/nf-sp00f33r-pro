package com.nfsp00f33r.app.models

import com.nfsp00f33r.app.data.ApduLogEntry
import com.nfsp00f33r.app.data.EmvCardData
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * PRODUCTION-GRADE CardProfile - Complete EMV Card Profile Model
 * Per newrule.md: NO SIMPLIFIED CODE - FULL PRODUCTION FUNCTIONALITY
 */
data class CardProfile(
    val id: String = UUID.randomUUID().toString(),
    val createdAt: Date = Date(),
    
    // Core EMV data
    val emvCardData: EmvCardData,
    
    // APDU command/response logs for emulation attacks
    val apduLogs: MutableList<ApduLogEntry> = mutableListOf(),
    
    // Optional metadata
    val aid: String? = null,
    val cardholderName: String? = null,
    val expirationDate: String? = null,
    val issuerName: String? = null,
    val applicationLabel: String? = null,
    
    // Attack/emulation context
    val notes: String? = null,
    val tags: List<String> = emptyList()
) {
    
    /**
     * Created timestamp string for UI display
     */
    val createdTimestamp: String
        get() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(createdAt)
    
    /**
     * Get unmasked PAN for EMV security research
     */
    fun getUnmaskedPan(): String {
        val pan = emvCardData.pan ?: return "PAN Not Available"
        if (pan.length < 8) return pan
        
        return if (pan.length >= 16) {
            "${pan.substring(0, 4)}-${pan.substring(4, 8)}-${pan.substring(8, 12)}-${pan.substring(12, 16)}"
        } else {
            "${pan.substring(0, 4)}-${pan.substring(4)}"
        }
    }

    /**
     * Get masked PAN for display (DEPRECATED - Use getUnmaskedPan() for security research)
     */
    @Deprecated("Use getUnmaskedPan() for EMV security research")
    fun getMaskedPan(): String = getUnmaskedPan()
    
    /**
     * Detect card type from PAN
     */
    fun detectCardType(): String {
        val pan = emvCardData.pan ?: return "Unknown"
        
        return when {
            pan.startsWith("4") -> "Visa"
            pan.startsWith("5") || pan.startsWith("2") -> "Mastercard"
            pan.startsWith("3") -> "American Express"
            pan.startsWith("6") -> "Discover"
            else -> "Unknown"
        }
    }
    
    /**
     * Get attack compatibility analysis
     * Returns list of compatible attack vector names
     */
    fun getAttackCompatibility(): List<String> {
        val compatibleAttacks = mutableListOf<String>()
        
        // Check for PPSE AID Poisoning compatibility
        if (apduLogs.any { it.command.contains("325041592E5359532E444446303100", ignoreCase = true) }) {
            compatibleAttacks.add("PPSE AID Poisoning")
        }
        
        // Check for Track2 Spoofing compatibility
        if (!emvCardData.track2Data.isNullOrEmpty()) {
            compatibleAttacks.add("Track2 Spoofing")
        }
        
        // Check for GPO manipulation compatibility
        if (apduLogs.any { it.command.startsWith("80A8", ignoreCase = true) }) {
            compatibleAttacks.add("GPO Manipulation")
            compatibleAttacks.add("Failed Cryptogram Attack")
        }
        
        // Check for AIP Force Offline compatibility
        if (apduLogs.any { it.response.contains("2000", ignoreCase = true) || 
                          it.response.contains("2008", ignoreCase = true) }) {
            compatibleAttacks.add("AIP Force Offline")
        }
        
        // Check for CVM Bypass compatibility
        if (apduLogs.any { it.command.startsWith("80CA", ignoreCase = true) }) {
            compatibleAttacks.add("CVM Bypass")
        }
        
        // Check for Currency Manipulation compatibility
        val selectedAid = emvCardData.selectedAid
        if (selectedAid != null && (selectedAid.startsWith("A000000003") || selectedAid.startsWith("A000000004"))) {
            compatibleAttacks.add("Currency Manipulation")
        }
        
        // Check for Cryptogram Downgrade compatibility
        if (apduLogs.any { it.response.contains("9F26", ignoreCase = true) }) {
            compatibleAttacks.add("Cryptogram Downgrade")
        }
        
        return compatibleAttacks.distinct()
    }
    
    /**
     * Get storage size estimate in bytes
     */
    fun getStorageSize(): Int {
        var size = 0
        
        // EMV data size
        size += emvCardData.pan?.length ?: 0
        size += emvCardData.track2Data?.length ?: 0
        size += emvCardData.cardholderName?.length ?: 0
        size += emvCardData.expiryDate?.length ?: 0
        size += emvCardData.selectedAid?.length ?: 0
        
        // APDU logs size
        size += apduLogs.sumOf { 
            it.command.length + it.response.length + it.description.length + it.timestamp.length + 20
        }
        
        // Metadata size
        size += aid?.length ?: 0
        size += cardholderName?.length ?: 0
        size += notes?.length ?: 0
        size += tags.sumOf { it.length + 4 }
        
        // Base object overhead
        size += 200
        
        return size
    }
    
    /**
     * Get validation status for profile integrity
     */
    fun getValidationStatus(): String {
        val issues = mutableListOf<String>()
        
        // Check required EMV data
        if (emvCardData.pan.isNullOrEmpty()) {
            issues.add("Missing PAN")
        }
        
        if (emvCardData.track2Data.isNullOrEmpty()) {
            issues.add("Missing Track2")
        }
        
        if (apduLogs.isEmpty()) {
            issues.add("No APDU Logs")
        }
        
        // Check APDU log completeness
        val hasSelectPpse = apduLogs.any { 
            it.command.contains("325041592E5359532E444446303100", ignoreCase = true) 
        }
        val hasSelectAid = apduLogs.any { 
            it.command.startsWith("00A40400", ignoreCase = true) 
        }
        val hasGpo = apduLogs.any { 
            it.command.startsWith("80A8", ignoreCase = true) 
        }
        
        if (!hasSelectPpse && !hasSelectAid) {
            issues.add("Missing SELECT commands")
        }
        
        if (!hasGpo) {
            issues.add("Missing GPO command")
        }
        
        // Check for common attack vectors
        val attackVectors = getAttackCompatibility()
        if (attackVectors.isEmpty()) {
            issues.add("No attack compatibility")
        }
        
        return when {
            issues.isEmpty() -> "✅ VALID"
            issues.size <= 2 -> "⚠️ PARTIAL (${issues.size} issues)"
            else -> "❌ INVALID (${issues.size} issues)"
        }
    }
    
    /**
     * Add APDU log entry
     */
    fun addApduLog(command: String, response: String, description: String = "") {
        apduLogs.add(ApduLogEntry(
            timestamp = Date().toString(),
            command = command,
            response = response,
            statusWord = response.takeLast(4),
            description = description,
            executionTimeMs = 0
        ))
    }
    
    /**
     * Get summary for UI display
     */
    fun getSummary(): String {
        return buildString {
            append("${detectCardType()} ${getUnmaskedPan()}")
            cardholderName?.let { append(" - $it") }
            append(" (${apduLogs.size} APDUs)")
            
            val attackCount = getAttackCompatibility().size
            if (attackCount > 0) {
                append(" - $attackCount attacks")
            }
        }
    }
    
    /**
     * Check if profile has sufficient data for emulation attacks
     */
    fun isEmulationReady(): Boolean {
        return emvCardData.pan != null &&
               emvCardData.track2Data != null &&
               apduLogs.isNotEmpty() &&
               aid != null &&
               getAttackCompatibility().isNotEmpty()
    }
    
    /**
     * Get attack risk assessment level
     */
    fun getAttackRiskLevel(): String {
        val attackVectors = getAttackCompatibility()
        return when {
            attackVectors.size >= 5 -> "🔴 CRITICAL"
            attackVectors.size >= 3 -> "🟠 HIGH"
            attackVectors.size >= 1 -> "🟡 MEDIUM" 
            else -> "🟢 LOW"
        }
    }
    
    /**
     * Export profile to JSON-like string for debugging
     */
    fun exportToString(): String {
        return """
            Profile ID: $id
            Created: $createdTimestamp
            Card: ${detectCardType()} ${getUnmaskedPan()}
            Cardholder: ${emvCardData.cardholderName ?: "Unknown"}
            Expiry: ${emvCardData.expiryDate ?: "Unknown"}
            AID: ${emvCardData.selectedAid ?: "Unknown"}
            APDU Commands: ${apduLogs.size}
            Attack Vectors: ${getAttackCompatibility().size}
            Storage Size: ${getStorageSize()} bytes
            Validation: ${getValidationStatus()}
            Risk Level: ${getAttackRiskLevel()}
        """.trimIndent()
    }
}
