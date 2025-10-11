package com.nfsp00f33r.app.cardreading

import com.nfsp00f33r.app.security.RocaVulnerabilityAnalyzer
import timber.log.Timber

/**
 * Professional EMV BER-TLV Parser with Comprehensive Tag Validation
 * Based on Proxmark3 RFIDResearchGroup implementation and EMV 4.3 specification
 * 
 * Features:
 * - Multi-byte tag support (up to 4 bytes)
 * - Proper length field parsing (definite and indefinite)
 * - Nested template handling with recursion depth control
 * - Comprehensive tag validation using EmvTagDictionary
 * - Professional error handling and recovery
 * - Data integrity validation
 */
object EmvTlvParser {
    
    private const val TAG = "EmvTlvParser"
    private const val MAX_RECURSION_DEPTH = 10
    private const val MAX_TAG_LENGTH = 4
    private const val MAX_LENGTH_FIELD_SIZE = 5
    
    // Store ROCA analysis results for UI display
    private val rocaAnalysisResults = mutableMapOf<String, RocaVulnerabilityAnalyzer.RocaAnalysisResult>()
    
    // Template tags that contain nested TLV structures
    // Based on ChAP.py: tags marked as TEMPLATE contain nested TLV data
    private val TEMPLATE_TAGS = setOf(
        "6F",   // FCI Template - BINARY TEMPLATE
        "70",   // Record Template - BINARY TEMPLATE  
        "77",   // Response Message Template Format 2 - BINARY TEMPLATE
        "A5",   // FCI Proprietary Template - BINARY TEMPLATE
        "61",   // Application Template - implied TEMPLATE
        "73",   // Directory Discretionary Template - implied TEMPLATE
        "BF0C", // FCI Issuer Discretionary Data - BER_TLV TEMPLATE
        "8C",   // CDOL1 - BINARY TEMPLATE (contains DOL entries)
        "8D",   // CDOL2 - BINARY TEMPLATE (contains DOL entries)
        "9F38"  // PDOL - BINARY TEMPLATE (contains DOL entries)
    )
    
    // Tags that are ALWAYS primitive (never templates) despite having constructed bit set
    // These are EMV spec quirks where the tag encoding doesn't match the data structure
    // Based on ChAP.py reference: tags marked as ITEM are primitive, TEMPLATE are constructed
    private val ALWAYS_PRIMITIVE_TAGS = setOf(
        "82",  // Application Interchange Profile (AIP) - BINARY ITEM - 2-byte bitmask, NOT a template
        "84",  // DF Name - MIXED ITEM - Contains AID/DF Name directly, NOT nested tags
        "86",  // Issuer Script Command - BER_TLV ITEM but NOT recursive template
        "87",  // Application Priority Indicator - BER_TLV ITEM
        "90",  // Issuer Public Key Certificate - BINARY ITEM - Raw RSA certificate bytes
        "92",  // Issuer Public Key Remainder - BINARY ITEM - Raw RSA key remainder bytes
        "93",  // Signed Static Application Data - BINARY ITEM - Raw signature data
        "94",  // Application File Locator - BINARY ITEM - AFL records, not TLV
        "95",  // Terminal Verification Results (TVR) - BINARY ITEM - 5-byte bitmask
        "97",  // Transaction Certificate Data Object List (TDOL) - BER_TLV ITEM
        "9B",  // Transaction Status Information (TSI) - BINARY ITEM - 2-byte bitmask
        "9F32", // Issuer Public Key Exponent - BINARY ITEM - Raw exponent bytes
        "9F33", // Terminal Capabilities - BINARY ITEM - 3-byte bitmask
        "9F34", // CVM Results - BINARY ITEM - 3-byte result
        "9F35", // Terminal Type - BINARY ITEM - 1-byte value
        "9F40", // Additional Terminal Capabilities - BINARY ITEM - 5-byte bitmask
        "9F46", // ICC Public Key Certificate - BINARY ITEM - Raw RSA certificate bytes
        "9F47", // ICC Public Key Exponent - BINARY ITEM - Raw exponent bytes
        "9F48", // ICC Public Key Remainder - BINARY ITEM - Raw RSA key remainder bytes
        "5F50", // Issuer URL - TEXT ITEM
        "9F0B", // Cardholder Name Extended - TEXT ITEM
        "9F4B", // Signed Dynamic Application Data - BINARY ITEM - Raw signature bytes
        "9F4A"  // Static Data Authentication Tag List - BINARY ITEM
    )
    
    // Constructed tags (bit 6 of first tag byte = 1)
    private fun isConstructedTag(firstTagByte: Byte): Boolean {
        return (firstTagByte.toInt() and 0x20) != 0
    }
    
    /**
     * Enhanced TLV parsing result with validation info
     */
    data class TlvParseResult(
        val tags: Map<String, String>,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList(),
        val validTags: Int = 0,
        val invalidTags: Int = 0,
        val templateDepth: Int = 0
    )
    
    /**
     * Parse BER-TLV data with comprehensive validation
     */
    fun parseEmvTlvData(
        data: ByteArray,
        context: String = "TLV",
        validateTags: Boolean = true
    ): TlvParseResult {
        val tags = mutableMapOf<String, String>()
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var validTags = 0
        var invalidTags = 0
        var maxDepth = 0
        
        try {
            Timber.d("$TAG 🔍 Parsing $context data: ${bytesToHex(data)} (${data.size} bytes)")
            
            val result = parseTlvRecursive(
                data, 0, data.size, tags, context, 0, 
                errors, warnings, validateTags
            )
            
            validTags = result.first
            invalidTags = result.second
            maxDepth = result.third
            
            Timber.d("$TAG ✅ $context parsing complete: ${tags.size} tags, depth: $maxDepth")
            
        } catch (e: Exception) {
            val error = "TLV parsing failed for $context: ${e.message}"
            errors.add(error)
            Timber.e("$TAG ❌ $error", e)
        }
        
        return TlvParseResult(
            tags = tags.toMap(),
            errors = errors.toList(),
            warnings = warnings.toList(),
            validTags = validTags,
            invalidTags = invalidTags,
            templateDepth = maxDepth
        )
    }
    
    /**
     * Recursive TLV parser with depth control and validation
     * FIXED: Properly distinguishes template tags (contain nested tags) from regular data tags
     */
    private fun parseTlvRecursive(
        data: ByteArray,
        start: Int,
        end: Int,
        tags: MutableMap<String, String>,
        context: String,
        depth: Int,
        errors: MutableList<String>,
        warnings: MutableList<String>,
        validateTags: Boolean
    ): Triple<Int, Int, Int> {
        var offset = start
        val indent = "  ".repeat(depth)
        var validCount = 0
        var invalidCount = 0
        var maxDepth = depth
        
        // Recursion depth protection
        if (depth > MAX_RECURSION_DEPTH) {
            val error = "Maximum recursion depth exceeded at $context"
            errors.add(error)
            Timber.w("$TAG ⚠️ $error")
            return Triple(validCount, invalidCount, maxDepth)
        }
        
        while (offset < end) {
            try {
                // Parse tag
                val tagResult = parseTagAtOffset(data, offset, end)
                if (tagResult == null) {
                    val error = "Invalid tag at offset $offset in $context"
                    errors.add(error)
                    Timber.w("$TAG ❌ $error")
                    break
                }
                
                val (tagBytes, tagSize) = tagResult
                val tagHex = bytesToHex(tagBytes).uppercase()
                offset += tagSize
                
                if (offset >= end) {
                    val error = "Truncated tag length at offset $offset"
                    errors.add(error)
                    break
                }
                
                // Parse length
                val lengthResult = parseLengthAtOffset(data, offset, end)
                if (lengthResult == null) {
                    val error = "Invalid length field for tag $tagHex at offset $offset"
                    errors.add(error)
                    Timber.w("$TAG ❌ $error")
                    break
                }
                
                val (length, lengthSize) = lengthResult
                offset += lengthSize
                
                // Validate data bounds
                if (offset + length > end) {
                    val error = "Tag $tagHex length ($length) exceeds data bounds at offset $offset"
                    errors.add(error)
                    Timber.w("$TAG ❌ $error")
                    break
                }
                
                // Get tag description and validate
                val tagDescription = if (validateTags) {
                    EmvTagDictionary.getTagDescription(tagHex)
                } else {
                    "Tag $tagHex"
                }
                
                val isKnownTag = validateTags && EmvTagDictionary.EMV_TAGS.containsKey(tagHex)
                
                // CRITICAL FIX: Template detection using constructed bit and known template tags
                // Template tags have CONSTRUCTED bit set (bit 6 of first tag byte = 1)
                val isTemplate = isTemplateTag(tagHex, tagBytes[0])
                
                if (validateTags && !isKnownTag && !tagHex.startsWith("DF") && !tagHex.startsWith("FF")) {
                    warnings.add("Unknown tag: $tagHex in $context")
                    invalidCount++
                } else {
                    validCount++
                }
                
                // CRITICAL: Check if this is actually a template based on data structure
                val looksLikeTemplate = if (length > 0 && isTemplate) {
                    // Verify if data contains valid TLV structure (peek at first byte)
                    val nextByte = data[offset].toInt() and 0xFF
                    // Valid tag byte: bit 8 is NOT 0 (all 0 is invalid)
                    nextByte != 0 && (nextByte and 0xE0) != 0
                } else false
                
                if (looksLikeTemplate && length > 0) {
                    // Parse nested template (contains other TLV tags)
                    Timber.d("$TAG $indent🔧 $context: $tagHex ($tagDescription) - TEMPLATE [${length} bytes, contains nested tags]")
                    
                    val nestedResult = parseTlvRecursive(
                        data, offset, offset + length, tags, 
                        "$context/$tagHex", depth + 1, 
                        errors, warnings, validateTags
                    )
                    
                    validCount += nestedResult.first
                    invalidCount += nestedResult.second
                    maxDepth = maxOf(maxDepth, nestedResult.third)
                    
                } else if (length > 0) {
                    // Extract primitive value (regular data tag)
                    val value = data.copyOfRange(offset, offset + length)
                    val valueHex = bytesToHex(value)
                    
                    tags[tagHex] = valueHex
                    Timber.d("$TAG $indent🏷️ $context: $tagHex ($tagDescription) - DATA [${length} bytes] = ${valueHex.take(32)}${if (valueHex.length > 32) "..." else ""}")
                    
                    // Log critical tags specifically
                    if (EmvTagDictionary.isCriticalTag(tagHex)) {
                        Timber.d("$TAG $indent🚨 CRITICAL TAG: $tagHex = $valueHex")
                    }
                    
                    // Check for ROCA vulnerable tags
                    if (EmvTagDictionary.isRocaVulnerableTag(tagHex)) {
                        Timber.d("$TAG $indent⚠️ ROCA VULNERABLE TAG: $tagHex")
                        warnings.add("ROCA vulnerable certificate tag found: $tagHex")
                        
                        // Perform ROCA vulnerability analysis
                        performRocaAnalysis(tagHex, valueHex, tagDescription, warnings)
                    }
                    
                } else {
                    // Zero length tag
                    tags[tagHex] = ""
                    Timber.d("$TAG $indent🏷️ $context: $tagHex ($tagDescription) = [EMPTY]")
                }
                
                offset += length
                
            } catch (e: Exception) {
                val error = "Error parsing TLV at offset $offset in $context: ${e.message}"
                errors.add(error)
                Timber.e("$TAG ❌ $error", e)
                break
            }
        }
        
        return Triple(validCount, invalidCount, maxDepth)
    }
    
    /**
     * Parse tag field with multi-byte support (up to 4 bytes)
     */
    private fun parseTagAtOffset(data: ByteArray, offset: Int, end: Int): Pair<ByteArray, Int>? {
        if (offset >= end) return null
        
        val firstByte = data[offset]
        var tagSize = 1
        
        // Check if this is a multi-byte tag (bits 1-5 of first byte all set)
        if ((firstByte.toInt() and 0x1F) == 0x1F) {
            // Multi-byte tag
            tagSize = 1
            var currentOffset = offset + 1
            
            while (currentOffset < end && tagSize < MAX_TAG_LENGTH) {
                val nextByte = data[currentOffset]
                tagSize++
                currentOffset++
                
                // Check if more bytes follow (bit 8 clear means last byte)
                if ((nextByte.toInt() and 0x80) == 0) {
                    break
                }
            }
            
            // Validate we didn't exceed bounds
            if (currentOffset > end || tagSize > MAX_TAG_LENGTH) {
                return null
            }
        }
        
        return Pair(data.copyOfRange(offset, offset + tagSize), tagSize)
    }
    
    /**
     * Parse length field with definite and indefinite length support
     */
    private fun parseLengthAtOffset(data: ByteArray, offset: Int, end: Int): Pair<Int, Int>? {
        if (offset >= end) return null
        
        val firstByte = data[offset].toInt() and 0xFF
        
        if (firstByte == 0x80) {
            // Indefinite length (not commonly used in EMV, but supported)
            return Pair(end - offset - 1, 1)
        }
        
        if ((firstByte and 0x80) == 0) {
            // Short form: length is in first byte (0-127)
            return Pair(firstByte, 1)
        }
        
        // Long form: first byte indicates number of length bytes
        val lengthOfLength = firstByte and 0x7F
        
        if (lengthOfLength == 0 || lengthOfLength > MAX_LENGTH_FIELD_SIZE || 
            offset + 1 + lengthOfLength > end) {
            return null
        }
        
        var length = 0
        for (i in 1..lengthOfLength) {
            length = (length shl 8) or (data[offset + i].toInt() and 0xFF)
        }
        
        return Pair(length, 1 + lengthOfLength)
    }
    
    /**
     * Determine if a tag is a template (contains nested TLV structures)
     * CRITICAL: Some tags have constructed bit set but are actually primitive (EMV spec quirks)
     */
    private fun isTemplateTag(tagHex: String, firstTagByte: Byte): Boolean {
        // ALWAYS check primitive list first - these override constructed bit
        if (ALWAYS_PRIMITIVE_TAGS.contains(tagHex)) {
            return false
        }
        
        return TEMPLATE_TAGS.contains(tagHex) || 
               isConstructedTag(firstTagByte) ||
               EmvTagDictionary.getTagCategory(tagHex) == "Core EMV"
    }
    
    /**
     * Convert bytes to hex string
     */
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
    
    /**
     * Perform ROCA vulnerability analysis on certificate tags
     */
    private fun performRocaAnalysis(
        tagId: String,
        certificateData: String,
        tagDescription: String,
        warnings: MutableList<String>
    ) {
        try {
            val rocaAnalyzer = RocaVulnerabilityAnalyzer()
            val analysisResult = rocaAnalyzer.analyzeEmvCertificate(
                tagId = tagId,
                certificateData = certificateData,
                tagDescription = tagDescription
            )
            
            when {
                analysisResult.isVulnerable && analysisResult.confidence == RocaVulnerabilityAnalyzer.VulnerabilityConfidence.CONFIRMED -> {
                    Timber.e("$TAG 🚨 CONFIRMED ROCA vulnerability in tag $tagId!")
                    warnings.add("🚨 CRITICAL: ROCA vulnerability CONFIRMED in $tagId certificate")
                    
                    if (analysisResult.factorAttempt?.successful == true) {
                        Timber.e("$TAG 💥 RSA key FACTORED! Private key compromised!")
                        warnings.add("💥 CRITICAL: RSA private key successfully factored - COMPROMISED!")
                    }
                }
                
                analysisResult.isVulnerable && analysisResult.confidence == RocaVulnerabilityAnalyzer.VulnerabilityConfidence.HIGHLY_LIKELY -> {
                    Timber.w("$TAG ⚠️ HIGHLY LIKELY ROCA vulnerability in tag $tagId")
                    warnings.add("⚠️ HIGH RISK: ROCA vulnerability highly likely in $tagId certificate")
                }
                
                analysisResult.isVulnerable && analysisResult.confidence == RocaVulnerabilityAnalyzer.VulnerabilityConfidence.POSSIBLE -> {
                    Timber.w("$TAG ⚡ POSSIBLE ROCA vulnerability in tag $tagId")
                    warnings.add("⚡ MODERATE RISK: Possible ROCA vulnerability in $tagId certificate")
                }
                
                analysisResult.confidence == RocaVulnerabilityAnalyzer.VulnerabilityConfidence.UNLIKELY -> {
                    Timber.d("$TAG ✅ ROCA analysis: Tag $tagId appears safe")
                    warnings.add("✅ ROCA analysis: $tagId certificate appears safe")
                }
                
                else -> {
                    Timber.d("$TAG ❓ ROCA analysis inconclusive for tag $tagId")
                    warnings.add("❓ ROCA analysis inconclusive for $tagId certificate")
                }
            }
            
            // Store analysis result for UI display
            rocaAnalysisResults[tagId] = analysisResult
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG ROCA analysis failed for tag $tagId")
            warnings.add("❌ ROCA analysis failed for $tagId: ${e.message}")
        }
    }
    
    /**
     * Validate TLV structure integrity
     */
    fun validateTlvStructure(data: ByteArray): List<String> {
        val issues = mutableListOf<String>()
        
        try {
            val result = parseEmvTlvData(data, "Validation", validateTags = true)
            issues.addAll(result.errors)
            
            if (result.templateDepth > 5) {
                issues.add("Unusually deep template nesting (${result.templateDepth} levels)")
            }
            
            if (result.invalidTags > result.validTags) {
                issues.add("More unknown tags (${result.invalidTags}) than known tags (${result.validTags})")
            }
            
        } catch (e: Exception) {
            issues.add("TLV structure validation failed: ${e.message}")
        }
        
        return issues
    }
    
    /**
     * Get ROCA analysis results for UI display
     */
    fun getRocaAnalysisResults(): Map<String, RocaVulnerabilityAnalyzer.RocaAnalysisResult> {
        return rocaAnalysisResults.toMap()
    }
    
    /**
     * Clear ROCA analysis results
     */
    fun clearRocaAnalysisResults() {
        rocaAnalysisResults.clear()
    }
    
    // ==================== ADVANCED PROXMARK3-INSPIRED PARSING ====================
    // Based on RFIDResearchGroup/proxmark3 emv_tags.c implementation
    
    /**
     * Parse DOL (Data Object List) - PDOL, CDOL1, CDOL2, DDOL
     * Returns list of (tag, length) pairs
     */
    data class DolEntry(
        val tag: String,
        val length: Int,
        val tagName: String
    )
    
    fun parseDol(dolData: String): List<DolEntry> {
        val entries = mutableListOf<DolEntry>()
        
        try {
            val bytes = hexToBytes(dolData)
            var offset = 0
            
            while (offset < bytes.size) {
                // Parse tag
                val tagResult = parseTagAtOffset(bytes, offset, bytes.size)
                if (tagResult == null) break
                
                val (tagBytes, tagSize) = tagResult
                val tagHex = bytesToHex(tagBytes).uppercase()
                offset += tagSize
                
                if (offset >= bytes.size) break
                
                // Parse length (single byte in DOL)
                val length = bytes[offset].toInt() and 0xFF
                offset++
                
                val tagName = EmvTagDictionary.getTagDescription(tagHex)
                entries.add(DolEntry(tagHex, length, tagName))
                
                Timber.d("$TAG DOL Entry: $tagHex ($tagName) - Length: $length bytes")
            }
            
        } catch (e: Exception) {
            Timber.e("$TAG DOL parsing error: ${e.message}", e)
        }
        
        return entries
    }
    
    /**
     * Parse AFL (Application File Locator) - Tag 94
     * Returns list of (SFI, startRecord, endRecord, offlineRecords)
     */
    data class AflEntry(
        val sfi: Int,
        val startRecord: Int,
        val endRecord: Int,
        val offlineRecords: Int
    )
    
    fun parseAfl(aflData: String): List<AflEntry> {
        val entries = mutableListOf<AflEntry>()
        
        try {
            val bytes = hexToBytes(aflData)
            
            if (bytes.size % 4 != 0) {
                Timber.w("$TAG AFL data length invalid: ${bytes.size} bytes (must be multiple of 4)")
                return entries
            }
            
            for (i in bytes.indices step 4) {
                val sfi = (bytes[i].toInt() and 0xFF) shr 3
                val startRecord = bytes[i + 1].toInt() and 0xFF
                val endRecord = bytes[i + 2].toInt() and 0xFF
                val offlineRecords = bytes[i + 3].toInt() and 0xFF
                
                entries.add(AflEntry(sfi, startRecord, endRecord, offlineRecords))
                Timber.d("$TAG AFL Entry: SFI=$sfi, Records=$startRecord-$endRecord, Offline=$offlineRecords")
            }
            
        } catch (e: Exception) {
            Timber.e("$TAG AFL parsing error: ${e.message}", e)
        }
        
        return entries
    }
    
    /**
     * Parse AIP (Application Interchange Profile) - Tag 82
     * Returns structured capability information
     */
    data class AipCapabilities(
        val sdaSupported: Boolean,
        val ddaSupported: Boolean,
        val cdaSupported: Boolean,
        val cardholderVerificationSupported: Boolean,
        val terminalRiskManagement: Boolean,
        val issuerAuthenticationSupported: Boolean,
        val msdSupported: Boolean,
        val rawBytes: String
    )
    
    fun parseAip(aipData: String): AipCapabilities? {
        try {
            val bytes = hexToBytes(aipData)
            if (bytes.size < 2) {
                Timber.w("$TAG AIP data too short: ${bytes.size} bytes")
                return null
            }
            
            val byte1 = bytes[0].toInt() and 0xFF
            val byte2 = bytes[1].toInt() and 0xFF
            
            val capabilities = AipCapabilities(
                sdaSupported = (byte1 and 0x40) != 0,
                ddaSupported = (byte1 and 0x20) != 0,
                cdaSupported = (byte1 and 0x01) != 0,
                cardholderVerificationSupported = (byte1 and 0x10) != 0,
                terminalRiskManagement = (byte1 and 0x08) != 0,
                issuerAuthenticationSupported = (byte1 and 0x04) != 0,
                msdSupported = (byte2 and 0x80) != 0,
                rawBytes = aipData
            )
            
            Timber.d("$TAG AIP Analysis:")
            Timber.d("$TAG   SDA Supported: ${capabilities.sdaSupported}")
            Timber.d("$TAG   DDA Supported: ${capabilities.ddaSupported}")
            Timber.d("$TAG   CDA Supported: ${capabilities.cdaSupported}")
            Timber.d("$TAG   CVM Supported: ${capabilities.cardholderVerificationSupported}")
            Timber.d("$TAG   MSD Supported: ${capabilities.msdSupported}")
            
            return capabilities
            
        } catch (e: Exception) {
            Timber.e("$TAG AIP parsing error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Parse CID (Cryptogram Information Data) - Tag 9F27
     * Returns AC type and advice/referral information
     */
    data class CidInfo(
        val acType: String,
        val adviceRequired: Boolean,
        val reasonCode: String,
        val rawByte: String
    )
    
    fun parseCid(cidData: String): CidInfo? {
        try {
            val bytes = hexToBytes(cidData)
            if (bytes.isEmpty()) return null
            
            val cidByte = bytes[0].toInt() and 0xFF
            
            val acType = when (cidByte and 0xC0) {
                0x00 -> "AAC (Transaction declined)"
                0x40 -> "TC (Transaction approved)"
                0x80 -> "ARQC (Online authorisation requested)"
                0xC0 -> "RFU"
                else -> "Unknown"
            }
            
            val adviceRequired = (cidByte and 0x08) != 0
            
            val reasonCode = when (cidByte and 0x07) {
                0 -> "No information given"
                1 -> "Service not allowed"
                2 -> "PIN Try Limit exceeded"
                3 -> "Issuer authentication failed"
                else -> "RFU (${cidByte and 0x07})"
            }
            
            Timber.d("$TAG CID Analysis: $acType, Advice=$adviceRequired, Reason=$reasonCode")
            
            return CidInfo(acType, adviceRequired, reasonCode, cidData)
            
        } catch (e: Exception) {
            Timber.e("$TAG CID parsing error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Parse CVM_LIST (Cardholder Verification Method List) - Tag 8E
     * Returns X/Y values and CVM rules
     */
    data class CvmRule(
        val method: String,
        val condition: String,
        val failOnUnsuccessful: Boolean,
        val rawBytes: String
    )
    
    data class CvmList(
        val amountX: Long,
        val amountY: Long,
        val rules: List<CvmRule>
    )
    
    fun parseCvmList(cvmData: String): CvmList? {
        try {
            val bytes = hexToBytes(cvmData)
            
            if (bytes.size < 10 || bytes.size % 2 != 0) {
                Timber.w("$TAG CVM_LIST invalid length: ${bytes.size} bytes")
                return null
            }
            
            // Parse X and Y amounts (4 bytes each, BCD)
            val amountX = ((bytes[0].toInt() and 0xFF) shl 24) or
                         ((bytes[1].toInt() and 0xFF) shl 16) or
                         ((bytes[2].toInt() and 0xFF) shl 8) or
                         (bytes[3].toInt() and 0xFF)
            
            val amountY = ((bytes[4].toInt() and 0xFF) shl 24) or
                         ((bytes[5].toInt() and 0xFF) shl 16) or
                         ((bytes[6].toInt() and 0xFF) shl 8) or
                         (bytes[7].toInt() and 0xFF)
            
            val rules = mutableListOf<CvmRule>()
            
            // Parse CVM rules (2 bytes each)
            for (i in 8 until bytes.size step 2) {
                val methodByte = bytes[i].toInt() and 0xFF
                val conditionByte = bytes[i + 1].toInt() and 0xFF
                
                val method = when (methodByte and 0x3F) {
                    0x00 -> "Fail CVM processing"
                    0x01 -> "Plaintext PIN verification by ICC"
                    0x02 -> "Enciphered PIN verified online"
                    0x03 -> "Plaintext PIN by ICC and signature"
                    0x04 -> "Enciphered PIN verification by ICC"
                    0x05 -> "Enciphered PIN by ICC and signature"
                    0x1E -> "Signature (paper)"
                    0x1F -> "No CVM required"
                    0x3F -> "NOT AVAILABLE"
                    else -> "Unknown method (${methodByte and 0x3F})"
                }
                
                val condition = when (conditionByte) {
                    0x00 -> "Always"
                    0x01 -> "If unattended cash"
                    0x02 -> "If not unattended cash and not manual cash and not purchase with cashback"
                    0x03 -> "If terminal supports the CVM"
                    0x04 -> "If manual cash"
                    0x05 -> "If purchase with cashback"
                    0x06 -> "If transaction in application currency and under X"
                    0x07 -> "If transaction in application currency and over X"
                    0x08 -> "If transaction in application currency and under Y"
                    0x09 -> "If transaction in application currency and over Y"
                    else -> "Unknown condition ($conditionByte)"
                }
                
                val failOnUnsuccessful = (methodByte and 0x40) == 0
                
                rules.add(CvmRule(method, condition, failOnUnsuccessful, 
                    bytesToHex(byteArrayOf(bytes[i], bytes[i + 1]))))
                
                Timber.d("$TAG CVM Rule: $method - $condition [${if (failOnUnsuccessful) "FAIL" else "CONTINUE"} if unsuccessful]")
            }
            
            return CvmList(amountX.toLong(), amountY.toLong(), rules)
            
        } catch (e: Exception) {
            Timber.e("$TAG CVM_LIST parsing error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Parse bitmask tags (AIP, TVR, AUC, CTQ, TTQ, CVR)
     * Returns human-readable interpretation
     */
    fun parseBitmask(tagId: String, bitmaskData: String): List<String> {
        val interpretations = mutableListOf<String>()
        
        try {
            val bytes = hexToBytes(bitmaskData)
            
            when (tagId.uppercase()) {
                "82" -> { // AIP
                    if (bytes.size >= 2) {
                        val byte1 = bytes[0].toInt() and 0xFF
                        val byte2 = bytes[1].toInt() and 0xFF
                        
                        if (byte1 and 0x40 != 0) interpretations.add("SDA supported")
                        if (byte1 and 0x20 != 0) interpretations.add("DDA supported")
                        if (byte1 and 0x10 != 0) interpretations.add("Cardholder verification supported")
                        if (byte1 and 0x08 != 0) interpretations.add("Terminal risk management required")
                        if (byte1 and 0x04 != 0) interpretations.add("Issuer authentication supported")
                        if (byte1 and 0x01 != 0) interpretations.add("CDA supported")
                        if (byte2 and 0x80 != 0) interpretations.add("MSD supported")
                    }
                }
                
                "9F07" -> { // AUC - Application Usage Control
                    if (bytes.size >= 2) {
                        val byte1 = bytes[0].toInt() and 0xFF
                        val byte2 = bytes[1].toInt() and 0xFF
                        
                        if (byte1 and 0x80 != 0) interpretations.add("Valid for domestic cash")
                        if (byte1 and 0x40 != 0) interpretations.add("Valid for international cash")
                        if (byte1 and 0x20 != 0) interpretations.add("Valid for domestic goods")
                        if (byte1 and 0x10 != 0) interpretations.add("Valid for international goods")
                        if (byte1 and 0x08 != 0) interpretations.add("Valid for domestic services")
                        if (byte1 and 0x04 != 0) interpretations.add("Valid for international services")
                        if (byte1 and 0x02 != 0) interpretations.add("Valid for ATMs")
                        if (byte1 and 0x01 != 0) interpretations.add("Valid at terminals other than ATMs")
                        if (byte2 and 0x80 != 0) interpretations.add("Domestic cashback allowed")
                        if (byte2 and 0x40 != 0) interpretations.add("International cashback allowed")
                    }
                }
                
                "9F6C" -> { // CTQ - Card Transaction Qualifiers
                    if (bytes.size >= 2) {
                        val byte1 = bytes[0].toInt() and 0xFF
                        val byte2 = bytes[1].toInt() and 0xFF
                        
                        if (byte1 and 0x80 != 0) interpretations.add("Online PIN required")
                        if (byte1 and 0x40 != 0) interpretations.add("Signature required")
                        if (byte1 and 0x20 != 0) interpretations.add("Go online if ODA fails and reader online capable")
                        if (byte1 and 0x10 != 0) interpretations.add("Switch interface if ODA fails and reader supports VIS")
                        if (byte1 and 0x08 != 0) interpretations.add("Go online if application expired")
                        if (byte1 and 0x04 != 0) interpretations.add("Switch interface for cash transactions")
                        if (byte1 and 0x02 != 0) interpretations.add("Switch interface for cashback transactions")
                        if (byte2 and 0x80 != 0) interpretations.add("Consumer Device CVM performed")
                        if (byte2 and 0x40 != 0) interpretations.add("Card supports issuer update processing at POS")
                    }
                }
                
                "9F66" -> { // TTQ - Terminal Transaction Qualifiers
                    if (bytes.size >= 3) {
                        val byte1 = bytes[0].toInt() and 0xFF
                        val byte2 = bytes[1].toInt() and 0xFF
                        val byte3 = bytes[2].toInt() and 0xFF
                        
                        if (byte1 and 0x80 != 0) interpretations.add("MSD supported")
                        if (byte1 and 0x40 != 0) interpretations.add("VSDC supported")
                        if (byte1 and 0x20 != 0) interpretations.add("qVSDC supported")
                        if (byte1 and 0x10 != 0) interpretations.add("EMV contact chip supported")
                        if (byte1 and 0x08 != 0) interpretations.add("Offline-only reader")
                        if (byte1 and 0x04 != 0) interpretations.add("Online PIN supported")
                        if (byte1 and 0x02 != 0) interpretations.add("Signature supported")
                        if (byte2 and 0x80 != 0) interpretations.add("Online cryptogram required")
                        if (byte2 and 0x40 != 0) interpretations.add("CVM required")
                        if (byte2 and 0x20 != 0) interpretations.add("Contact Chip Offline PIN supported")
                        if (byte3 and 0x80 != 0) interpretations.add("Issuer Update Processing supported")
                        if (byte3 and 0x40 != 0) interpretations.add("Mobile functionality supported")
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e("$TAG Bitmask parsing error for $tagId: ${e.message}", e)
        }
        
        return interpretations
    }
    
    /**
     * Convert hex string to byte array
     */
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace(":", "")
        return ByteArray(cleanHex.length / 2) { i ->
            cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }
    
    /**
     * Parse date in YYMMDD format - Tag 5F24, 5F25, 9A
     */
    fun parseYymmdd(dateData: String): String? {
        try {
            val bytes = hexToBytes(dateData)
            if (bytes.size != 3) return null
            
            val yy = String.format("%02d", bytes[0].toInt() and 0xFF)
            val mm = String.format("%02d", bytes[1].toInt() and 0xFF)
            val dd = String.format("%02d", bytes[2].toInt() and 0xFF)
            
            return "20$yy-$mm-$dd"
            
        } catch (e: Exception) {
            Timber.e("$TAG Date parsing error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Parse numeric BCD data - Tags like 5F28, 5F2A, 9F02, 9F03
     */
    fun parseNumeric(numericData: String): Long? {
        try {
            val bytes = hexToBytes(numericData)
            var result = 0L
            
            for (byte in bytes) {
                val highNibble = (byte.toInt() and 0xF0) shr 4
                val lowNibble = byte.toInt() and 0x0F
                
                result = result * 10 + highNibble
                result = result * 10 + lowNibble
            }
            
            return result
            
        } catch (e: Exception) {
            Timber.e("$TAG Numeric parsing error: ${e.message}", e)
            return null
        }
    }
    
    /**
     * Get human-readable string from ASCII hex data - Tags like 50, 5F20, 5F2D
     */
    fun parseString(stringData: String): String? {
        try {
            val bytes = hexToBytes(stringData)
            return bytes.toString(Charsets.US_ASCII).trim()
            
        } catch (e: Exception) {
            Timber.e("$TAG String parsing error: ${e.message}", e)
            return null
        }
    }
}