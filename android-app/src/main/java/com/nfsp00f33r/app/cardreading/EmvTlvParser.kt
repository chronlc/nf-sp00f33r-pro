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
    private val TEMPLATE_TAGS = setOf(
        "6F", "A5", "70", "77", "80", "61", "73", "71", "72", "83",
        "BF0C", "E1", "E2", "E3", "E4", "E5"
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
                val isTemplate = isTemplateTag(tagHex, tagBytes[0])
                
                if (validateTags && !isKnownTag && !tagHex.startsWith("DF") && !tagHex.startsWith("FF")) {
                    warnings.add("Unknown tag: $tagHex in $context")
                    invalidCount++
                } else {
                    validCount++
                }
                
                if (isTemplate && length > 0) {
                    // Parse nested template
                    Timber.d("$TAG $indent🔧 $context: $tagHex ($tagDescription) - TEMPLATE (${length} bytes)")
                    
                    val nestedResult = parseTlvRecursive(
                        data, offset, offset + length, tags, 
                        "$context/$tagHex", depth + 1, 
                        errors, warnings, validateTags
                    )
                    
                    validCount += nestedResult.first
                    invalidCount += nestedResult.second
                    maxDepth = maxOf(maxDepth, nestedResult.third)
                    
                } else if (length > 0) {
                    // Extract primitive value
                    val value = data.copyOfRange(offset, offset + length)
                    val valueHex = bytesToHex(value)
                    
                    tags[tagHex] = valueHex
                    Timber.d("$TAG $indent🏷️ $context: $tagHex ($tagDescription) = $valueHex")
                    
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
     */
    private fun isTemplateTag(tagHex: String, firstTagByte: Byte): Boolean {
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
}