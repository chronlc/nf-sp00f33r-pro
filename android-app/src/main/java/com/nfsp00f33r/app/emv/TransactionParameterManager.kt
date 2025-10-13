package com.nfsp00f33r.app.emv

import timber.log.Timber
import java.util.Calendar
import kotlin.random.Random

/**
 * Terminal Transaction Parameters Manager
 * Generates and manages terminal-side EMV data for PDOL/CDOL processing
 * 
 * Based on Proxmark3 ParamLoadDefaults() and InitTransactionParameters()
 * but adapted for Android environment
 */
object TransactionParameterManager {
    
    // ==================== Terminal Configuration ====================
    
    /** Tag 0x9F1A - Terminal Country Code (ISO 3166-1 numeric) */
    var terminalCountryCode: ByteArray = byteArrayOf(0x08, 0x40) // USA = 0x0840
    
    /** Tag 0x5F2A - Transaction Currency Code (ISO 4217 numeric) */
    var transactionCurrencyCode: ByteArray = byteArrayOf(0x08, 0x40) // USD = 0x0840
    
    /** Tag 0x9F1E - Interface Device Serial Number (8 bytes ASCII) */
    var interfaceDeviceSerial: ByteArray = "ANDROID1".toByteArray()
    
    /** Tag 0x9F33 - Terminal Capabilities (3 bytes bitmask) */
    var terminalCapabilities: ByteArray = byteArrayOf(
        0xE0.toByte(), // Byte 1: Manual key entry, Magnetic stripe, IC with contacts
        0xF8.toByte(), // Byte 2: Plaintext PIN, Enciphered PIN online, Signature, Enciphered PIN offline, No CVM
        0xC8.toByte()  // Byte 3: SDA, DDA, Card capture, CDA
    )
    
    /** Tag 0x9F35 - Terminal Type */
    enum class TerminalType(val code: Byte) {
        ATTENDED_ONLINE(0x22),           // Most common for POS
        ATTENDED_OFFLINE(0x12),
        UNATTENDED_ONLINE(0x24),         // ATM-like
        UNATTENDED_OFFLINE(0x14)
    }
    var terminalType: TerminalType = TerminalType.ATTENDED_ONLINE
    
    /** Tag 0x9F40 - Additional Terminal Capabilities (5 bytes) */
    var additionalTerminalCapabilities: ByteArray = byteArrayOf(
        0xF0.toByte(), // Cash, Goods, Services, Cashback, Inquiry
        0x00.toByte(),
        0xF0.toByte(),
        0x00.toByte(),
        0x00.toByte()
    )
    
    // ==================== Transaction Parameters ====================
    
    /** Tag 0x9C - Transaction Type */
    enum class TransactionTypeCode(val code: Byte, val label: String) {
        GOODS_AND_SERVICES(0x00, "Purchase"),
        CASH(0x01, "Cash Withdrawal"),
        CASHBACK(0x09, "Purchase + Cashback"),
        REFUND(0x20, "Refund"),
        INQUIRY(0x30, "Balance Inquiry"),
        PAYMENT(0x50, "Payment"),
        TRANSFER(0x60, "Transfer")
    }
    
    // ==================== Dynamic Parameter Generation ====================
    
    /**
     * Tag 0x9A - Transaction Date (YYMMDD in BCD)
     */
    fun getTransactionDate(): ByteArray {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR) % 100
        val month = calendar.get(Calendar.MONTH) + 1
        val day = calendar.get(Calendar.DAY_OF_MONTH)
        
        return byteArrayOf(
            year.toByte(),
            month.toByte(),
            day.toByte()
        )
    }
    
    /**
     * Tag 0x9F21 - Transaction Time (HHMMSS in BCD)
     */
    fun getTransactionTime(): ByteArray {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        
        return byteArrayOf(
            hour.toByte(),
            minute.toByte(),
            second.toByte()
        )
    }
    
    /**
     * Tag 0x9F37 - Unpredictable Number (4 bytes random)
     * Critical for cryptographic security
     */
    fun generateUnpredictableNumber(): ByteArray {
        return Random.nextBytes(4)
    }
    
    /**
     * Tag 0x9F6A - Unpredictable Number (MSD for UDOL) (4 bytes)
     */
    fun generateUnpredictableNumberMsd(): ByteArray {
        return Random.nextBytes(4)
    }
    
    /**
     * Tag 0x9F02 - Amount, Authorised (Numeric, 6 bytes BCD)
     * Amount in smallest currency unit (e.g., cents for USD)
     */
    fun encodeAmount(amountCents: Long): ByteArray {
        val amountStr = String.format("%012d", amountCents)
        return amountStr.chunked(2).map { 
            ((it[0] - '0') * 16 + (it[1] - '0')).toByte() 
        }.toByteArray()
    }
    
    /**
     * Tag 0x9F03 - Amount, Other (Numeric, 6 bytes BCD)
     * Typically used for cashback amount
     */
    fun encodeOtherAmount(amountCents: Long): ByteArray {
        return encodeAmount(amountCents)
    }
    
    /**
     * Tag 0x9F1A - Terminal Country Code as hex string
     */
    fun getTerminalCountryCodeHex(): String {
        return terminalCountryCode.joinToString("") { "%02X".format(it) }
    }
    
    /**
     * Tag 0x5F2A - Transaction Currency Code as hex string
     */
    fun getTransactionCurrencyCodeHex(): String {
        return transactionCurrencyCode.joinToString("") { "%02X".format(it) }
    }
    
    // ==================== Terminal Data Map Builder ====================
    
    /**
     * Build complete terminal data map for PDOL/CDOL processing
     * Maps tag hex string to byte array value
     */
    fun buildTerminalDataMap(
        transactionType: TransactionType,
        amountAuthorised: Long = 100, // cents (e.g., $1.00)
        amountOther: Long = 0,
        transactionTypeCode: TransactionTypeCode = TransactionTypeCode.GOODS_AND_SERVICES
    ): Map<String, ByteArray> {
        
        val dataMap = mutableMapOf<String, ByteArray>()
        
        // Core terminal identification
        dataMap["9F1A"] = terminalCountryCode
        dataMap["5F2A"] = transactionCurrencyCode
        dataMap["9F1E"] = interfaceDeviceSerial
        dataMap["9F33"] = terminalCapabilities
        dataMap["9F35"] = byteArrayOf(terminalType.code)
        dataMap["9F40"] = additionalTerminalCapabilities
        
        // Transaction parameters
        dataMap["9A"] = getTransactionDate()
        dataMap["9F21"] = getTransactionTime()
        dataMap["9C"] = byteArrayOf(transactionTypeCode.code)
        dataMap["9F02"] = encodeAmount(amountAuthorised)
        dataMap["9F03"] = encodeOtherAmount(amountOther)
        
        // Cryptographic randomness
        dataMap["9F37"] = generateUnpredictableNumber()
        dataMap["9F6A"] = generateUnpredictableNumberMsd()
        
        // Terminal Transaction Qualifiers (TTQ) - tag 0x9F66
        // Byte 1: Transaction type specific
        // Bytes 2-4: Usually 0x00
        dataMap["9F66"] = byteArrayOf(
            transactionType.ttqByte1,
            0x00.toByte(),
            0x00.toByte(),
            0x00.toByte()
        )
        
        // Terminal Verification Results (TVR) - tag 0x95 (5 bytes, initially all zeros)
        dataMap["95"] = ByteArray(5) { 0x00 }
        
        // Transaction Status Information (TSI) - tag 0x9B (2 bytes, initially all zeros)
        dataMap["9B"] = ByteArray(2) { 0x00 }
        
        Timber.d("📊 Built terminal data map with ${dataMap.size} entries for ${transactionType.label}")
        
        return dataMap
    }
    
    /**
     * Get terminal data for specific tag, with default fallback
     */
    fun getTerminalData(
        tag: String,
        length: Int,
        transactionType: TransactionType
    ): ByteArray {
        val dataMap = buildTerminalDataMap(transactionType)
        val data = dataMap[tag.uppercase()]
        
        return when {
            data == null -> {
                Timber.w("⚠️ Terminal data not found for tag $tag, using zeros")
                ByteArray(length) { 0x00 }
            }
            data.size == length -> data
            data.size < length -> {
                Timber.d("📏 Padding tag $tag from ${data.size} to $length bytes")
                data + ByteArray(length - data.size) { 0x00 }
            }
            else -> {
                Timber.d("✂️ Truncating tag $tag from ${data.size} to $length bytes")
                data.take(length).toByteArray()
            }
        }
    }
    
    /**
     * Log terminal configuration for debugging
     */
    fun logConfiguration() {
        Timber.d("════════════════════════════════════════")
        Timber.d("Terminal Configuration:")
        Timber.d("  Country Code: ${terminalCountryCode.joinToString("") { "%02X".format(it) }}")
        Timber.d("  Currency Code: ${transactionCurrencyCode.joinToString("") { "%02X".format(it) }}")
        Timber.d("  IFD Serial: ${String(interfaceDeviceSerial)}")
        Timber.d("  Terminal Type: ${terminalType.name}")
        Timber.d("════════════════════════════════════════")
    }
}
