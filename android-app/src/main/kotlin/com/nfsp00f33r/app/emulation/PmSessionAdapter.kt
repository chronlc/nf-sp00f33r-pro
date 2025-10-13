package com.nfsp00f33r.app.emulation

import timber.log.Timber
import com.nfsp00f33r.app.storage.emv.EmvCardSessionEntity
import com.nfsp00f33r.app.storage.emv.EmvSessionDatabase
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * PM Session Adapter - Converts PmEmvReader sessions to database entities
 * 
 * Bridges the gap between PmEmvReader EMV workflow and nf-sp00f33r database storage.
 * Uses existing EmvTlvParser for consistent tag parsing across the application.
 * 
 * @author nf-sp00f33r
 * @date October 11, 2025
 */
class PmSessionAdapter {
    // Using Timber for logging
    
    /**
     * Convert PM EmvSession to Room database entity
     * 
     * Maps all Proxmark3 session data to our database schema:
     * - TLV database → allEmvTags JSON
     * - APDU log → apduLog JSON
     * - Session metadata → individual fields
     * - Card vendor → vendor string
     * - Authentication → authMethod field
     * 
     * @param session PM EMV session with complete transaction data
     * @return EmvCardSessionEntity ready to insert into Room database
     */
    private fun getTagName(tag: String): String {
        // Basic EMV tag names - expand as needed
        return when (tag) {
            "50" -> "Application Label"
            "5A" -> "PAN"
            "5F20" -> "Cardholder Name"
            "5F24" -> "Expiry Date"
            "5F28" -> "Issuer Country Code"
            "82" -> "AIP"
            "84" -> "DF Name"
            "8C" -> "CDOL1"
            "8D" -> "CDOL2"
            "8E" -> "CVM List"
            "94" -> "AFL"
            "9F07" -> "Application Usage Control"
            "9F08" -> "Application Version Number"
            "9F0D" -> "IAC Default"
            "9F0E" -> "IAC Deny"
            "9F0F" -> "IAC Online"
            "9F10" -> "IAD"
            "9F26" -> "Cryptogram"
            "9F27" -> "CID"
            "9F36" -> "ATC"
            "9F37" -> "Unpredictable Number"
            "9F6E" -> "Third Party Data"
            else -> "Tag $tag"
        }
    }
    
    private fun tryDecodeValue(tag: String, value: ByteArray): String {
        return try {
            when (tag) {
                "5F20" -> String(value)  // Cardholder name
                "50" -> String(value)  // Application label
                "5F24" -> value.toHexString()  // Expiry date (YYMMDD)
                else -> value.toHexString()
            }
        } catch (e: Exception) {
            value.toHexString()
        }
    }
    
    fun toEmvCardSessionEntity(session: PmEmvReader.EmvSession): EmvCardSessionEntity {
        Timber.i("PmSessionAdapter", "Converting PM session to database entity")
        
        // Extract basic card info
        val pan = session.pan ?: extractPanFromTlv(session)
        val expiryDate = session.expiryDate ?: extractExpiryFromTlv(session)
        val cardholderName = extractCardholderName(session)
        
        // Extract track data
        val track1 = session.tlvDatabase["56"]?.toHexString()
        val track2 = session.track2 ?: session.tlvDatabase["57"]?.toHexString()
        
        // Extract service code from Track2
        val serviceCode = extractServiceCode(track2)
        
        // Extract AIP and AFL
        val aip = session.tlvDatabase["82"]?.toHexString()
        val afl = session.tlvDatabase["94"]?.toHexString()
        
        // Extract authentication data
        val issuerPublicKeyCert = session.tlvDatabase["90"]?.toHexString()
        val issuerPublicKeyRemainder = session.tlvDatabase["92"]?.toHexString()
        val issuerPublicKeyExponent = session.tlvDatabase["9F32"]?.toHexString()
        val iccPublicKeyCert = session.tlvDatabase["9F46"]?.toHexString()
        val iccPublicKeyRemainder = session.tlvDatabase["9F48"]?.toHexString()
        val iccPublicKeyExponent = session.tlvDatabase["9F47"]?.toHexString()
        
        // Extract cryptogram data
        val arqc = session.arqc?.toHexString() ?: session.tlvDatabase["9F26"]?.toHexString()
        val atc = session.atc?.toHexString() ?: session.tlvDatabase["9F36"]?.toHexString()
        val cid = session.tlvDatabase["9F27"]?.toHexString()
        
        // Extract CVM data
        val cvmList = session.tlvDatabase["8E"]?.toHexString()
        val cvmResults = session.tlvDatabase["9F34"]?.toHexString()
        
        // Extract DOL data
        val pdol = session.tlvDatabase["9F38"]?.toHexString()
        val cdol1 = session.tlvDatabase["8C"]?.toHexString()
        val cdol2 = session.tlvDatabase["8D"]?.toHexString()
        val ddol = session.tlvDatabase["9F49"]?.toHexString()
        
        // Extract additional tags
        val applicationLabel = session.tlvDatabase["50"]?.toString(Charsets.UTF_8)
        val applicationPreferredName = session.tlvDatabase["9F12"]?.toString(Charsets.UTF_8)
        val languagePreference = session.tlvDatabase["5F2D"]?.toString(Charsets.UTF_8)
        
        // Convert TLV database to JSON
        val allEmvTagsJson = convertTlvDatabaseToJson(session.tlvDatabase)
        
        // Convert APDU log to JSON
        val apduLogJson = convertApduLogToJson(session.apduLog)
        
        // Determine card type
        val cardType = when (session.cardVendor) {
            PmEmvReader.CardVendor.CV_VISA -> "VISA"
            PmEmvReader.CardVendor.CV_MASTERCARD -> "MASTERCARD"
            PmEmvReader.CardVendor.CV_AMERICANEXPRESS -> "AMEX"
            PmEmvReader.CardVendor.CV_JCB -> "JCB"
            PmEmvReader.CardVendor.CV_DISCOVER -> "DISCOVER"
            PmEmvReader.CardVendor.CV_DINERS -> "DINERS"
            else -> "UNKNOWN"
        }
        
        // Calculate ROCA vulnerability status
        val rocaStatus = calculateRocaStatus(session)
        
        Timber.i("PmSessionAdapter", "Conversion complete:")
        Timber.i("PmSessionAdapter", "  PAN: $pan")
        Timber.i("PmSessionAdapter", "  Expiry: $expiryDate")
        Timber.i("PmSessionAdapter", "  Vendor: $cardType")
        Timber.i("PmSessionAdapter", "  Auth Method: ${session.authMethod}")
        Timber.i("PmSessionAdapter", "  Tags: ${session.tlvDatabase.size}")
        Timber.i("PmSessionAdapter", "  APDUs: ${session.apduLog.size}")
        
        // Parse AIP for capabilities
        val aipValue = session.tlvDatabase["82"]?.let { aipBytes ->
            if (aipBytes.size >= 2) (aipBytes[0].toInt() and 0xFF shl 8) or (aipBytes[1].toInt() and 0xFF) else 0
        } ?: 0
        val hasSda = (aipValue and 0x4000) != 0
        val hasDda = (aipValue and 0x2000) != 0
        val hasCda = (aipValue and 0x0100) != 0
        val supportsCvm = (aipValue and 0x1000) != 0
        
        // Detect card brand from AID or vendor
        val aidHex = session.selectedAid?.toHexString()
        val cardBrand = when {
            cardType == "VISA" -> "Visa"
            cardType == "MASTERCARD" -> "Mastercard"
            cardType == "AMEX" -> "American Express"
            cardType == "JCB" -> "JCB"
            aidHex?.startsWith("A0000000031") == true -> "Visa"
            aidHex?.startsWith("A0000000041") == true -> "Mastercard"
            aidHex?.startsWith("A000000003") == true -> "Visa"
            aidHex?.startsWith("A000000004") == true -> "Mastercard"
            else -> "Unknown"
        }
        
        // Create masked PAN
        val maskedPan = pan?.let {
            if (it.length > 6) "${it.take(6)}******${it.takeLast(4)}" else it
        }
        
        // Convert TLV database to EnrichedTagData map
        val enrichedTags = session.tlvDatabase.mapValues { (tag, value) ->
            com.nfsp00f33r.app.cardreading.EnrichedTagData(
                tag = tag,
                name = getTagName(tag),
                value = value.toHexString(),
                valueDecoded = tryDecodeValue(tag, value),
                phase = "UNKNOWN",
                source = "CARD",
                length = value.size
            )
        }
        
        // Convert APDU log to ApduLogEntry list
        val apduLogEntries = session.apduLog.mapIndexed { index, entry ->
            com.nfsp00f33r.app.storage.emv.ApduLogEntry(
                sequence = index + 1,
                command = entry.command.toHexString(),
                response = entry.response.toHexString(),
                statusWord = entry.sw1sw2,
                phase = entry.description,
                description = entry.description,
                timestamp = session.timestamp + (index * 10),
                executionTime = 10L,
                isSuccess = entry.sw1sw2.startsWith("90")
            )
        }
        
        // Determine scan status
        val scanStatus = when {
            session.completed -> "SUCCESS"
            session.errorMessage != null -> "ERROR"
            else -> "PARTIAL"
        }
        
        return EmvCardSessionEntity(
            // Session metadata
            sessionId = session.sessionId,
            scanTimestamp = session.timestamp,
            scanDuration = System.currentTimeMillis() - session.timestamp,
            scanStatus = scanStatus,
            errorMessage = session.errorMessage,
            
            // Card identification
            cardUid = session.sessionId,  // Using sessionId as UID placeholder
            pan = pan,
            maskedPan = maskedPan,
            expiryDate = expiryDate,
            cardholderName = cardholderName,
            cardBrand = cardBrand,
            applicationLabel = applicationLabel,
            applicationIdentifier = aidHex,
            
            // EMV capabilities
            aip = aip,
            hasSda = hasSda,
            hasDda = hasDda,
            hasCda = hasCda,
            supportsCvm = supportsCvm,
            
            // Cryptographic data
            arqc = arqc,
            tc = arqc,  // Using ARQC as TC for now (both use tag 9F26)
            cid = cid,
            atc = atc,
            
            // Security status
            rocaVulnerable = rocaStatus.isVulnerable,
            rocaKeyModulus = null,
            hasEncryptedData = arqc != null || cid != null,
            
            // Complete EMV data (JSON storage)
            allEmvTags = enrichedTags,
            apduLog = apduLogEntries,
            
            // Phase-categorized data (not parsed separately in PmEmvReader)
            ppseData = null,
            aidsData = emptyList(),
            gpoData = null,
            recordsData = emptyList(),
            cryptogramData = if (arqc != null || cid != null) {
                com.nfsp00f33r.app.storage.emv.CryptogramData(
                    arqc = arqc,
                    tc = null,
                    aac = null,
                    cid = cid ?: "00",
                    atc = atc ?: "0000",
                    iad = session.tlvDatabase["9F10"]?.toHexString(),
                    cryptogramType = if (arqc != null) "ARQC" else "UNKNOWN"
                )
            } else null,
            
            // Stats
            totalApdus = session.apduLog.size,
            totalTags = session.tlvDatabase.size,
            recordCount = session.tlvDatabase.size
        )
    }
    
    /**
     * Extract PAN from TLV database (tag 5A)
     */
    private fun extractPanFromTlv(session: PmEmvReader.EmvSession): String? {
        return session.tlvDatabase["5A"]?.toHexString()
    }
    
    /**
     * Extract expiry date from TLV database (tag 5F24)
     */
    private fun extractExpiryFromTlv(session: PmEmvReader.EmvSession): String? {
        return session.tlvDatabase["5F24"]?.toHexString()
    }
    
    /**
     * Extract cardholder name from TLV database (tag 5F20)
     */
    private fun extractCardholderName(session: PmEmvReader.EmvSession): String? {
        return session.tlvDatabase["5F20"]?.let { nameBytes ->
            try {
                String(nameBytes, Charsets.UTF_8).trim()
            } catch (e: Exception) {
                null
            }
        }
    }
    
    /**
     * Extract service code from Track2 data
     * Track2 format: PAN D YYMM ServiceCode DiscretionaryData
     */
    private fun extractServiceCode(track2: String?): String? {
        if (track2 == null) return null
        
        try {
            val parts = track2.split('D')
            if (parts.size >= 2 && parts[1].length >= 7) {
                // Service code is 3 digits after YYMM
                return parts[1].substring(4, 7)
            }
        } catch (e: Exception) {
            Timber.w("PmSessionAdapter", "Error extracting service code: ${e.message}")
        }
        
        return null
    }
    
    /**
     * Calculate ROCA vulnerability status
     * 
     * ROCA requires these 5 tags:
     * - 90 (Issuer Public Key Certificate)
     * - 9F46 (ICC Public Key Certificate)
     * - 8F (Certification Authority Public Key Index)
     * - 9F32 (Issuer Public Key Exponent)
     * - 9F47 (ICC Public Key Exponent)
     */
    private fun calculateRocaStatus(session: PmEmvReader.EmvSession): RocaStatus {
        val requiredTags = listOf("90", "9F46", "8F", "9F32", "9F47")
        val foundTags = requiredTags.filter { session.tlvDatabase.containsKey(it) }
        
        val status = when (foundTags.size) {
            5 -> "100% COMPLETE"
            in 3..4 -> "PARTIAL (${foundTags.size}/5 tags)"
            in 1..2 -> "INSUFFICIENT (${foundTags.size}/5 tags)"
            else -> "NOT TESTABLE (0/5 tags)"
        }
        
        val isVulnerable = foundTags.size == 5
        
        return RocaStatus(status, isVulnerable)
    }
    
    data class RocaStatus(
        val status: String,
        val isVulnerable: Boolean
    )
    
    /**
     * Convert TLV database to JSON for storage
     * 
     * Format: {"tag": "value_hex", ...}
     * Example: {"5A": "1234567890123456", "57": "1234567890123456D2512..."}
     */
    private fun convertTlvDatabaseToJson(tlvDatabase: Map<String, ByteArray>): String {
        return try {
            val jsonMap = tlvDatabase.mapValues { (_, value) -> value.toHexString() }
            Json.encodeToString(jsonMap)
        } catch (e: Exception) {
            Timber.e("PmSessionAdapter", "Error converting TLV to JSON: ${e.message}")
            "{}"
        }
    }
    
    /**
     * Convert APDU log to JSON for storage
     * 
     * Format: [{"cmd": "...", "resp": "...", "sw": "9000", "desc": "..."}, ...]
     */
    private fun convertApduLogToJson(apduLog: List<PmEmvReader.ApduEntry>): String {
        return try {
            val jsonList = apduLog.map { entry ->
                ApduLogEntry(
                    command = entry.command.toHexString(),
                    response = entry.response.toHexString(),
                    sw1sw2 = entry.sw1sw2,
                    description = entry.description,
                    timestamp = entry.timestamp
                )
            }
            Json.encodeToString(jsonList)
        } catch (e: Exception) {
            Timber.e("PmSessionAdapter", "Error converting APDU log to JSON: ${e.message}")
            "[]"
        }
    }
    
    @Serializable
    data class ApduLogEntry(
        val command: String,
        val response: String,
        val sw1sw2: String,
        val description: String,
        val timestamp: Long
    )
}

/**
 * Extension: Save PM session to Room database
 * 
 * Usage:
 * ```
 * val session = pmReader.executeEmvTransaction(isoDep, transactionType)
 * val savedId = session.saveToDatabase(database, logger)
 * ```
 */
suspend fun PmEmvReader.EmvSession.saveToDatabase(
    database: com.nfsp00f33r.app.storage.emv.EmvSessionDatabase
): Long {
    val adapter = PmSessionAdapter()
    val entity = adapter.toEmvCardSessionEntity(this)
    return database.emvCardSessionDao().insert(entity)
}

/**
 * Extension: Get human-readable summary of PM session
 */
fun PmEmvReader.EmvSession.getSummary(): String {
    return buildString {
        appendLine("EMV Session Summary")
        appendLine("==================")
        appendLine("Session ID: $sessionId")
        appendLine("Transaction Type: ${transactionType.name}")
        appendLine("Channel: ${channelType.name}")
        appendLine()
        appendLine("Card Info:")
        appendLine("  Vendor: ${cardVendor.name}")
        appendLine("  PAN: ${pan ?: "N/A"}")
        appendLine("  Expiry: ${expiryDate ?: "N/A"}")
        appendLine("  AID: ${selectedAid?.toHexString() ?: "N/A"}")
        appendLine()
        appendLine("Authentication:")
        appendLine("  Method: $authMethod")
        appendLine("  AIP: ${String.format("0x%04X", aipValue)}")
        arqc?.let { appendLine("  ARQC: ${it.toHexString()}") }
        atc?.let { appendLine("  ATC: ${it.toHexString()}") }
        appendLine()
        appendLine("Data Collection:")
        appendLine("  Tags: ${tlvDatabase.size}")
        appendLine("  APDUs: ${apduLog.size}")
        appendLine()
        appendLine("Status: ${if (completed) "COMPLETED" else "INCOMPLETE"}")
        errorMessage?.let { appendLine("Error: $it") }
    }
}
