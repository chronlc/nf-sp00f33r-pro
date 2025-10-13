package com.nfsp00f33r.app.emv

/**
 * EMV Transaction Types following EMV Contactless specifications
 * Based on Proxmark3 implementation but adapted to Android/Kotlin
 * 
 * Controls the Terminal Transaction Qualifiers (TTQ - tag 0x9F66)
 * which determine how the card processes the transaction
 */
enum class TransactionType(
    val code: Int,
    val label: String,
    val description: String,
    val ttqByte1: Byte
) {
    /**
     * MSD - Magnetic Stripe Data
     * Legacy mode for older cards, TTQ = 0x86000000
     */
    MSD(
        code = 0,
        label = "MSD",
        description = "Magnetic Stripe Data (Legacy)",
        ttqByte1 = 0x86.toByte()
    ),
    
    /**
     * VSDC - Visa Smart Debit/Credit
     * Contact mode only, not standard for contactless
     * TTQ = 0x46000000
     */
    VSDC(
        code = 1,
        label = "VSDC",
        description = "Visa Smart D/C (Contact)",
        ttqByte1 = 0x46.toByte()
    ),
    
    /**
     * qVSDC/M-Chip - Quick VSDC / Mastercard Chip
     * Standard contactless mode, TTQ = 0x26000000
     * Most common for tap-to-pay transactions
     */
    QVSDC(
        code = 2,
        label = "qVSDC",
        description = "Quick VSDC/M-Chip (Standard)",
        ttqByte1 = 0x26.toByte()
    ),
    
    /**
     * CDA - Combined Data Authentication
     * Enhanced security with combined DDA + AC generation
     * TTQ = 0x36000000, requires SDAD in response
     */
    CDA(
        code = 3,
        label = "CDA",
        description = "Combined DDA + AC (Enhanced)",
        ttqByte1 = 0x36.toByte()
    );
    
    companion object {
        /**
         * Get transaction type from code, default to qVSDC
         */
        fun fromCode(code: Int): TransactionType {
            return values().firstOrNull { it.code == code } ?: QVSDC
        }
        
        /**
         * Get recommended type based on card capabilities (AIP)
         */
        fun fromAip(aipHex: String?): TransactionType {
            if (aipHex == null || aipHex.length < 4) return QVSDC
            
            val aipInt = aipHex.take(4).toIntOrNull(16) ?: return QVSDC
            
            // Check AIP bits:
            // Bit 1.1 (0x0100): CDA supported
            // Bit 1.2 (0x0200): DDA supported
            // Bit 1.7 (0x4000): SDA supported
            
            return when {
                (aipInt and 0x0100) != 0 -> CDA      // CDA supported
                (aipInt and 0x0200) != 0 -> QVSDC    // DDA supported, use qVSDC
                else -> QVSDC                         // Default to qVSDC
            }
        }
    }
}

/**
 * Terminal Decision for GENERATE AC command
 * Determines what type of cryptogram the card should generate
 */
enum class TerminalDecision(
    val p1Byte: Byte,
    val label: String,
    val description: String
) {
    /**
     * AAC - Application Authentication Cryptogram
     * Transaction DECLINED by terminal
     */
    AAC(
        p1Byte = 0x00.toByte(),
        label = "AAC",
        description = "Transaction DECLINED"
    ),
    
    /**
     * TC - Transaction Certificate
     * Transaction APPROVED for offline processing
     */
    TC(
        p1Byte = 0x40.toByte(),
        label = "TC",
        description = "Transaction APPROVED Offline"
    ),
    
    /**
     * ARQC - Authorization Request Cryptogram
     * Online authorization REQUIRED
     */
    ARQC(
        p1Byte = 0x80.toByte(),
        label = "ARQC",
        description = "Online Authorization Required"
    );
    
    companion object {
        /**
         * Determine terminal decision based on card capabilities
         * and transaction parameters
         */
        fun fromRiskManagement(
            aip: ByteArray?,
            tvr: ByteArray?,
            isOnlineCapable: Boolean
        ): TerminalDecision {
            // Simple logic: default to TC for offline approval
            // In production, this would involve complex risk management
            return if (isOnlineCapable) ARQC else TC
        }
    }
}
