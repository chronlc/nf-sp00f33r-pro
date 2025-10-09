package com.nfsp00f33r.app.cardreading

import com.nfsp00f33r.app.data.ApduLogEntry
import timber.log.Timber

/**
 * ApduDataExtractor - Feature Enhancement Phase
 * 
 * Advanced APDU log analyzer for extracting EMV data
 * Parses APDU command/response logs to extract:
 * - Application Transaction Counter (ATC)
 * - Application Interchange Profile (AIP)
 * - Card Verification Method (CVM) list
 * - Cryptographic data
 * - Transaction logs
 * 
 * Implements real EMV tag parsing as per EMV Book 3
 */
object ApduDataExtractor {
    
    private const val TAG = "📋 ApduExtractor"
    
    /**
     * Extracted EMV data from APDU logs
     */
    data class ExtractedEmvData(
        val atc: Int?,
        val lastOnlineAtc: Int?,
        val aip: String?,
        val cvmList: List<String>,
        val cryptogramData: CryptogramData?,
        val transactionLogs: List<TransactionLogEntry>,
        val allTags: Map<String, String>
    )
    
    /**
     * Cryptogram data extracted from APDU
     */
    data class CryptogramData(
        val cryptogram: String,        // Tag 9F26
        val cryptogramType: String,    // Tag 9F27 (CID)
        val unpredictableNumber: String?, // Tag 9F37
        val transactionData: String?   // Tag 9F02
    )
    
    /**
     * Transaction log entry
     */
    data class TransactionLogEntry(
        val atc: Int,
        val amount: String?,
        val date: String?,
        val cryptogram: String?,
        val rawData: String
    )
    
    /**
     * Extract all EMV data from APDU logs
     */
    fun extractEmvData(apduLogs: List<ApduLogEntry>): ExtractedEmvData {
        Timber.d("$TAG Extracting EMV data from ${apduLogs.size} APDU entries")
        
        val allTags = mutableMapOf<String, String>()
        val transactionLogs = mutableListOf<TransactionLogEntry>()
        var atc: Int? = null
        var lastOnlineAtc: Int? = null
        var aip: String? = null
        val cvmList = mutableListOf<String>()
        var cryptogramData: CryptogramData? = null
        
        // Parse each APDU response
        apduLogs.forEach { logEntry ->
            val response = logEntry.response
            if (response.isNotEmpty() && response != "null") {
                try {
                    val tags = parseTlvResponse(response)
                    allTags.putAll(tags)
                    
                    // Extract specific data
                    tags["9F36"]?.let { atcHex ->
                        atc = parseHexToInt(atcHex)
                        Timber.d("$TAG Found ATC: $atc")
                    }
                    
                    tags["9F13"]?.let { lastOnlineAtcHex ->
                        lastOnlineAtc = parseHexToInt(lastOnlineAtcHex)
                        Timber.d("$TAG Found Last Online ATC: $lastOnlineAtc")
                    }
                    
                    tags["82"]?.let { aipHex ->
                        aip = aipHex
                        Timber.d("$TAG Found AIP: $aip")
                    }
                    
                    tags["8E"]?.let { cvmListHex ->
                        val parsedCvmList = parseCvmList(cvmListHex)
                        cvmList.addAll(parsedCvmList)
                        Timber.d("$TAG Found CVM List: ${parsedCvmList.size} entries")
                    }
                    
                    // Extract cryptogram data
                    if (tags.containsKey("9F26")) {
                        cryptogramData = CryptogramData(
                            cryptogram = tags["9F26"] ?: "",
                            cryptogramType = tags["9F27"] ?: "",
                            unpredictableNumber = tags["9F37"],
                            transactionData = tags["9F02"]
                        )
                        Timber.d("$TAG Found cryptogram data")
                    }
                    
                    // Extract transaction log entries
                    tags["9F4D"]?.let { logData ->
                        val logEntry = parseTransactionLogEntry(logData, tags)
                        transactionLogs.add(logEntry)
                    }
                    
                } catch (e: Exception) {
                    Timber.w("$TAG Failed to parse APDU response: ${e.message}")
                }
            }
        }
        
        Timber.i("$TAG Extraction complete: ATC=$atc, AIP=$aip, ${allTags.size} total tags")
        
        return ExtractedEmvData(
            atc = atc,
            lastOnlineAtc = lastOnlineAtc,
            aip = aip,
            cvmList = cvmList,
            cryptogramData = cryptogramData,
            transactionLogs = transactionLogs,
            allTags = allTags
        )
    }
    
    /**
     * Parse TLV-encoded response
     */
    private fun parseTlvResponse(response: String): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        
        try {
            var pos = 0
            val data = response.replace(" ", "")
            
            while (pos < data.length - 4) {
                // Parse tag
                val tagByte = data.substring(pos, pos + 2).toInt(16)
                var tagLength = 2
                
                // Check for multi-byte tag
                if ((tagByte and 0x1F) == 0x1F) {
                    tagLength = 4
                    if (pos + tagLength > data.length) break
                }
                
                val tag = data.substring(pos, pos + tagLength)
                pos += tagLength
                
                // Parse length
                if (pos + 2 > data.length) break
                var lengthByte = data.substring(pos, pos + 2).toInt(16)
                pos += 2
                
                // Handle multi-byte length
                if ((lengthByte and 0x80) != 0) {
                    val numLengthBytes = lengthByte and 0x7F
                    if (pos + (numLengthBytes * 2) > data.length) break
                    
                    var length = 0
                    repeat(numLengthBytes) {
                        length = (length shl 8) or data.substring(pos, pos + 2).toInt(16)
                        pos += 2
                    }
                    lengthByte = length
                }
                
                val valueLength = lengthByte * 2
                if (pos + valueLength > data.length) break
                
                val value = data.substring(pos, pos + valueLength)
                pos += valueLength
                
                tags[tag] = value
            }
            
        } catch (e: Exception) {
            Timber.w("$TAG TLV parsing error: ${e.message}")
        }
        
        return tags
    }
    
    /**
     * Parse CVM list
     */
    private fun parseCvmList(cvmListHex: String): List<String> {
        val cvmList = mutableListOf<String>()
        
        try {
            // CVM list format: X Y (CVM Rule 1) (CVM Rule 2) ... 
            // X = Amount X, Y = Amount Y
            if (cvmListHex.length < 16) return cvmList
            
            // Skip X and Y amounts (8 bytes)
            var pos = 16
            
            // Parse CVM rules (each is 4 bytes)
            while (pos + 4 <= cvmListHex.length) {
                val cvmCode = cvmListHex.substring(pos, pos + 2).toInt(16)
                val cvmCondition = cvmListHex.substring(pos + 2, pos + 4).toInt(16)
                
                val cvmDescription = decodeCvmCode(cvmCode)
                val conditionDescription = decodeCvmCondition(cvmCondition)
                
                cvmList.add("$cvmDescription ($conditionDescription)")
                pos += 4
            }
            
        } catch (e: Exception) {
            Timber.w("$TAG CVM list parsing error: ${e.message}")
        }
        
        return cvmList
    }
    
    /**
     * Decode CVM code
     */
    private fun decodeCvmCode(code: Int): String {
        return when (code and 0x3F) {
            0x00 -> "Fail CVM processing"
            0x01 -> "Plaintext PIN verification by ICC"
            0x02 -> "Enciphered PIN verification online"
            0x03 -> "Plaintext PIN verification by ICC and signature"
            0x04 -> "Enciphered PIN verification by ICC"
            0x05 -> "Enciphered PIN verification by ICC and signature"
            0x1E -> "Signature (paper)"
            0x1F -> "No CVM required"
            0x3F -> "Not available for use"
            else -> "Unknown CVM (0x${code.toString(16)})"
        }
    }
    
    /**
     * Decode CVM condition
     */
    private fun decodeCvmCondition(condition: Int): String {
        return when (condition) {
            0x00 -> "Always"
            0x01 -> "If unattended cash"
            0x02 -> "If not unattended cash and not manual cash and not purchase with cashback"
            0x03 -> "If terminal supports CVM"
            0x04 -> "If manual cash"
            0x05 -> "If purchase with cashback"
            0x06 -> "If transaction is in application currency"
            0x07 -> "If transaction is above X value"
            0x08 -> "If transaction is below X value"
            else -> "Condition 0x${condition.toString(16)}"
        }
    }
    
    /**
     * Parse transaction log entry
     */
    private fun parseTransactionLogEntry(logData: String, allTags: Map<String, String>): TransactionLogEntry {
        return TransactionLogEntry(
            atc = allTags["9F36"]?.let { parseHexToInt(it) } ?: 0,
            amount = allTags["9F02"],
            date = allTags["9A"],
            cryptogram = allTags["9F26"],
            rawData = logData
        )
    }
    
    /**
     * Parse hex string to integer
     */
    private fun parseHexToInt(hex: String): Int {
        return try {
            hex.toLong(16).toInt()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Extract ATC from APDU logs (convenience method)
     */
    fun extractAtc(apduLogs: List<ApduLogEntry>): Int? {
        return extractEmvData(apduLogs).atc
    }
    
    /**
     * Extract AIP from APDU logs (convenience method)
     */
    fun extractAip(apduLogs: List<ApduLogEntry>): String? {
        return extractEmvData(apduLogs).aip
    }
    
    /**
     * Extract CVM list from APDU logs (convenience method)
     */
    fun extractCvmList(apduLogs: List<ApduLogEntry>): List<String> {
        return extractEmvData(apduLogs).cvmList
    }
}
