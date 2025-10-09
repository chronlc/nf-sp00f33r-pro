package com.nfsp00f33r.app.fuzzing.presets

import com.nfsp00f33r.app.fuzzing.models.FuzzConfig
import com.nfsp00f33r.app.fuzzing.models.FuzzingStrategyType
import com.nfsp00f33r.app.fuzzing.strategies.MutationFuzzingStrategy

/**
 * Built-in fuzzing presets for common EMV vulnerabilities
 */
object FuzzingPresets {
    
    /**
     * ROCA vulnerability testing preset
     * Targets RSA key generation weaknesses
     */
    val ROCA_VULNERABILITY = FuzzingPreset(
        name = "ROCA Vulnerability Test",
        description = "Tests for ROCA vulnerability in RSA certificate generation",
        targetVulnerability = "ROCA",
        strategyType = FuzzingStrategyType.PROTOCOL_AWARE,
        seedCommands = listOf(
            "00CA9F46FF", // GET DATA - Public Key Certificate
            "00CA9F4700", // GET DATA - Public Key Exponent
            "00CA9F4800", // GET DATA - Public Key Remainder
            "0084000008", // GET CHALLENGE
            "00880000027F", // INTERNAL AUTHENTICATE
        ),
        maxTests = 200,
        testsPerSecond = 5,
        notes = "Focuses on RSA operations and certificate retrieval"
    )
    
    /**
     * Track2 data manipulation preset
     * Tests for Track2 equivalent data vulnerabilities
     */
    val TRACK2_MANIPULATION = FuzzingPreset(
        name = "Track2 Data Manipulation",
        description = "Tests Track2 equivalent data handling and validation",
        targetVulnerability = "TRACK2",
        strategyType = FuzzingStrategyType.MUTATION,
        seedCommands = listOf(
            "00CA9F6B00", // GET DATA - Track2 Equivalent Data
            "00A4040007A000000003101000", // SELECT with Track2
            "00B2011400", // READ RECORD (Track2 location)
        ),
        maxTests = 300,
        testsPerSecond = 10,
        notes = "Mutates Track2 data structures"
    )
    
    /**
     * CVM (Cardholder Verification Method) bypass preset
     * Tests for CVM bypass vulnerabilities
     */
    val CVM_BYPASS = FuzzingPreset(
        name = "CVM Bypass Test",
        description = "Tests for CVM bypass vulnerabilities",
        targetVulnerability = "CVM_BYPASS",
        strategyType = FuzzingStrategyType.PROTOCOL_AWARE,
        seedCommands = listOf(
            "00CA9F3800", // GET DATA - PDOL
            "80A8000002830000", // GPO with minimal PDOL
            "80AE80000000", // GENERATE AC with no CVM
            "00CA9F0700", // GET DATA - AUC (Application Usage Control)
            "00CA9F6C00", // GET DATA - CTQ (Card Transaction Qualifiers)
        ),
        maxTests = 250,
        testsPerSecond = 8,
        notes = "Attempts to bypass cardholder verification"
    )
    
    /**
     * AIP (Application Interchange Profile) manipulation
     * Tests AIP modification vulnerabilities
     */
    val AIP_MODIFICATION = FuzzingPreset(
        name = "AIP Modification Test",
        description = "Tests AIP manipulation and SDA/DDA/CDA downgrade",
        targetVulnerability = "AIP_MODIFICATION",
        strategyType = FuzzingStrategyType.MUTATION,
        seedCommands = listOf(
            "80A800000283000000", // GPO
            "00B2011400", // READ RECORD SFI 2
            "00B2021400", // READ RECORD SFI 2
            "00B2011C00", // READ RECORD SFI 3
        ),
        maxTests = 200,
        testsPerSecond = 10,
        notes = "Mutates AIP byte to test authentication downgrades"
    )
    
    /**
     * Cryptogram generation testing
     * Tests ARQC/AAC/TC generation
     */
    val CRYPTOGRAM_GENERATION = FuzzingPreset(
        name = "Cryptogram Generation Test",
        description = "Tests cryptogram generation with various parameters",
        targetVulnerability = "CRYPTOGRAM",
        strategyType = FuzzingStrategyType.PROTOCOL_AWARE,
        seedCommands = listOf(
            "80AE4000002300000000000010000000000000003608250000009F02060000000001009F03060000000000009F1A0208405F2A0208409A031912319C0100", // GENERATE AC
            "80AE5000002300000000000010000000000000003608250000009F02060000000001009F03060000000000009F1A0208405F2A0208409A031912319C0100", // GENERATE AC (TC)
            "80AE8000000000", // GENERATE AC minimal
        ),
        maxTests = 300,
        testsPerSecond = 5,
        notes = "Tests cryptogram generation with edge cases"
    )
    
    /**
     * PDOL (Processing Data Object List) fuzzing
     * Tests PDOL parsing and handling
     */
    val PDOL_FUZZING = FuzzingPreset(
        name = "PDOL Fuzzing",
        description = "Tests PDOL parsing with malformed data",
        targetVulnerability = "PDOL",
        strategyType = FuzzingStrategyType.MUTATION,
        seedCommands = listOf(
            "80A80000028300", // GPO with minimal PDOL
            "80A8000002830000", // GPO with empty PDOL
            "80A800001083FFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", // GPO with max length
        ),
        maxTests = 250,
        testsPerSecond = 10,
        notes = "Fuzzes PDOL data structures"
    )
    
    /**
     * AFL (Application File Locator) testing
     * Tests AFL parsing and record reading
     */
    val AFL_FUZZING = FuzzingPreset(
        name = "AFL Fuzzing",
        description = "Tests AFL parsing and record reading",
        targetVulnerability = "AFL",
        strategyType = FuzzingStrategyType.PROTOCOL_AWARE,
        seedCommands = listOf(
            "00B2010C00", // READ RECORD SFI 1
            "00B2011400", // READ RECORD SFI 2
            "00B2011C00", // READ RECORD SFI 3
            "00B2012400", // READ RECORD SFI 4
            "00B20FFF00", // READ RECORD invalid
        ),
        maxTests = 200,
        testsPerSecond = 15,
        notes = "Tests record reading with various SFI/record combinations"
    )
    
    /**
     * Quick vulnerability scan
     * Fast scan covering multiple vulnerabilities
     */
    val QUICK_SCAN = FuzzingPreset(
        name = "Quick Vulnerability Scan",
        description = "Fast scan covering common vulnerabilities",
        targetVulnerability = "MULTIPLE",
        strategyType = FuzzingStrategyType.MUTATION,
        seedCommands = listOf(
            "00A4040007A0000000031010", // SELECT
            "80A8000002830000", // GPO
            "00B2011400", // READ RECORD
            "00CA9F3600", // GET DATA ATC
            "80AE800000", // GENERATE AC
        ),
        maxTests = 100,
        testsPerSecond = 20,
        notes = "Quick 100-test scan for rapid assessment"
    )
    
    /**
     * Deep protocol test
     * Comprehensive protocol fuzzing
     */
    val DEEP_PROTOCOL_TEST = FuzzingPreset(
        name = "Deep Protocol Test",
        description = "Comprehensive EMV protocol fuzzing",
        targetVulnerability = "MULTIPLE",
        strategyType = FuzzingStrategyType.PROTOCOL_AWARE,
        seedCommands = listOf(
            "00A4040007A0000000031010",
            "80A8000002830000",
            "00B2011400",
            "00CA9F3600",
            "80AE4000002300000000000010000000000000003608250000009F02060000000001009F03060000000000009F1A0208405F2A0208409A031912319C0100",
            "00CA9F4600",
            "0084000008",
        ),
        maxTests = 1000,
        testsPerSecond = 10,
        notes = "Comprehensive 1000-test protocol analysis"
    )
    
    /**
     * Get all built-in presets
     */
    fun getAllPresets(): List<FuzzingPreset> {
        return listOf(
            ROCA_VULNERABILITY,
            TRACK2_MANIPULATION,
            CVM_BYPASS,
            AIP_MODIFICATION,
            CRYPTOGRAM_GENERATION,
            PDOL_FUZZING,
            AFL_FUZZING,
            QUICK_SCAN,
            DEEP_PROTOCOL_TEST
        )
    }
    
    /**
     * Get preset by name
     */
    fun getPresetByName(name: String): FuzzingPreset? {
        return getAllPresets().find { it.name == name }
    }
    
    /**
     * Get presets by vulnerability type
     */
    fun getPresetsByVulnerability(vulnerability: String): List<FuzzingPreset> {
        return getAllPresets().filter { it.targetVulnerability == vulnerability }
    }
}

/**
 * Fuzzing preset data class
 */
data class FuzzingPreset(
    val name: String,
    val description: String,
    val targetVulnerability: String,
    val strategyType: FuzzingStrategyType,
    val seedCommands: List<String>, // Hex strings
    val maxTests: Int,
    val testsPerSecond: Int,
    val notes: String
) {
    /**
     * Convert to FuzzConfig
     */
    fun toFuzzConfig(): FuzzConfig {
        return FuzzConfig(
            strategy = strategyType,
            maxTests = maxTests,
            timeoutMs = 5000,
            testsPerSecond = testsPerSecond,
            enableCrashDetection = true,
            saveResults = true,
            seedData = null // Seeds are handled by strategy
        )
    }
    
    /**
     * Get seed commands as byte arrays
     */
    fun getSeedCommandsAsBytes(): List<ByteArray> {
        return seedCommands.map { hexToBytes(it) }
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        val cleaned = hex.replace(" ", "").replace(":", "")
        return cleaned.chunked(2)
            .map { it.toInt(16).toByte() }
            .toByteArray()
    }
}
