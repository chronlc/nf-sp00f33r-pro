package com.nfsp00f33r.app.fuzzing.strategies

import java.security.SecureRandom

/**
 * Protocol-aware fuzzing strategy
 * Generates valid APDU structure with edge case values
 * Understands EMV/ISO 7816 command format: CLA INS P1 P2 [Lc Data] [Le]
 */
class ProtocolAwareFuzzingStrategy(
    private val maxTests: Int = 500
) : FuzzingStrategy {
    
    private val random = SecureRandom()
    private var testsGenerated = 0
    
    // Common EMV command classes
    private val commonCLA = byteArrayOf(
        0x00, 0x80.toByte(), 0x84.toByte(), 0x90.toByte(),
        0xA0.toByte(), 0xB0.toByte()
    )
    
    // Common EMV instructions
    private val commonINS = byteArrayOf(
        0xA4.toByte(), // SELECT
        0xB0.toByte(), // READ BINARY
        0xB2.toByte(), // READ RECORD
        0xC0.toByte(), // GET RESPONSE
        0xAE.toByte(), // GENERATE AC
        0x88.toByte(), // INTERNAL AUTHENTICATE
        0x84.toByte(), // GET CHALLENGE
        0xCA.toByte(), // GET DATA
        0xCB.toByte()  // GET PROCESSING OPTIONS (GPO)
    )
    
    // Edge case values for testing
    private val edgeCaseBytes = byteArrayOf(
        0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte()
    )
    
    override fun generateNextInput(seed: ByteArray?): ByteArray {
        testsGenerated++
        
        val fuzzType = random.nextInt(8)
        
        return when (fuzzType) {
            0 -> generateValidStructureEdgeData()
            1 -> generateInvalidLengthField()
            2 -> generateOversizedData()
            3 -> generateUndersizedData()
            4 -> generateBoundaryLengths()
            5 -> generateInvalidP1P2()
            6 -> generateMalformedTLV()
            else -> generateEdgeCaseCommand()
        }
    }
    
    private fun generateValidStructureEdgeData(): ByteArray {
        // Valid APDU structure with edge case values
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = commonINS[random.nextInt(commonINS.size)]
        val p1 = edgeCaseBytes[random.nextInt(edgeCaseBytes.size)]
        val p2 = edgeCaseBytes[random.nextInt(edgeCaseBytes.size)]
        
        val dataLength = random.nextInt(20)
        if (dataLength == 0) {
            // Case 1: No data, no Le
            return byteArrayOf(cla, ins, p1, p2)
        }
        
        val data = ByteArray(dataLength)
        random.nextBytes(data)
        
        // Case 3: With data, no Le
        return byteArrayOf(cla, ins, p1, p2, dataLength.toByte()) + data
    }
    
    private fun generateInvalidLengthField(): ByteArray {
        // Length field doesn't match actual data length
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = commonINS[random.nextInt(commonINS.size)]
        val p1 = random.nextInt(256).toByte()
        val p2 = random.nextInt(256).toByte()
        
        val declaredLength = random.nextInt(50) + 1
        val actualLength = random.nextInt(50) + 1 // Different from declared
        
        val data = ByteArray(actualLength)
        random.nextBytes(data)
        
        return byteArrayOf(cla, ins, p1, p2, declaredLength.toByte()) + data
    }
    
    private fun generateOversizedData(): ByteArray {
        // Data exceeding typical EMV limits
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = commonINS[random.nextInt(commonINS.size)]
        val p1 = random.nextInt(256).toByte()
        val p2 = random.nextInt(256).toByte()
        
        // EMV typically limits to 255 bytes, test beyond
        val dataLength = 200 + random.nextInt(56) // 200-255 bytes
        val data = ByteArray(dataLength)
        random.nextBytes(data)
        
        return byteArrayOf(cla, ins, p1, p2, 0xFF.toByte()) + data
    }
    
    private fun generateUndersizedData(): ByteArray {
        // Data much smaller than declared
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = commonINS[random.nextInt(commonINS.size)]
        val p1 = random.nextInt(256).toByte()
        val p2 = random.nextInt(256).toByte()
        
        val declaredLength = 50 + random.nextInt(50)
        val actualLength = random.nextInt(5) // Much smaller
        
        val data = ByteArray(actualLength)
        random.nextBytes(data)
        
        return byteArrayOf(cla, ins, p1, p2, declaredLength.toByte()) + data
    }
    
    private fun generateBoundaryLengths(): ByteArray {
        // Test boundary conditions (0, 1, 254, 255, 256+)
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = commonINS[random.nextInt(commonINS.size)]
        val p1 = random.nextInt(256).toByte()
        val p2 = random.nextInt(256).toByte()
        
        val boundaryLengths = intArrayOf(0, 1, 127, 128, 254, 255)
        val length = boundaryLengths[random.nextInt(boundaryLengths.size)]
        
        if (length == 0) {
            return byteArrayOf(cla, ins, p1, p2)
        }
        
        val data = ByteArray(length)
        random.nextBytes(data)
        
        return byteArrayOf(cla, ins, p1, p2, length.toByte()) + data
    }
    
    private fun generateInvalidP1P2(): ByteArray {
        // Valid command with invalid parameter bytes
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = commonINS[random.nextInt(commonINS.size)]
        
        // Use invalid combinations for P1/P2
        val p1 = edgeCaseBytes[random.nextInt(edgeCaseBytes.size)]
        val p2 = edgeCaseBytes[random.nextInt(edgeCaseBytes.size)]
        
        return byteArrayOf(cla, ins, p1, p2, 0x00)
    }
    
    private fun generateMalformedTLV(): ByteArray {
        // Generate data with malformed TLV structures
        val cla = commonCLA[random.nextInt(commonCLA.size)]
        val ins = 0xCA.toByte() // GET DATA commonly uses TLV
        val p1 = 0x9F.toByte()
        val p2 = random.nextInt(256).toByte()
        
        // Malformed TLV: tag without proper length/value
        val malformedTLV = when (random.nextInt(4)) {
            0 -> byteArrayOf(0x9F.toByte(), 0x36) // Tag without length
            1 -> byteArrayOf(0x9F.toByte(), 0x36, 0x05, 0x01) // Length > actual data
            2 -> byteArrayOf(0x9F.toByte(), 0x36, 0x00) // Zero length
            else -> byteArrayOf(0x9F.toByte(), 0x36, 0xFF.toByte()) // Invalid length
        }
        
        return byteArrayOf(cla, ins, p1, p2, malformedTLV.size.toByte()) + malformedTLV
    }
    
    private fun generateEdgeCaseCommand(): ByteArray {
        // Completely edge case values for all fields
        return byteArrayOf(
            edgeCaseBytes[random.nextInt(edgeCaseBytes.size)],
            edgeCaseBytes[random.nextInt(edgeCaseBytes.size)],
            edgeCaseBytes[random.nextInt(edgeCaseBytes.size)],
            edgeCaseBytes[random.nextInt(edgeCaseBytes.size)],
            edgeCaseBytes[random.nextInt(edgeCaseBytes.size)]
        )
    }
    
    override fun shouldTerminate(): Boolean {
        return testsGenerated >= maxTests
    }
    
    override fun getName(): String = "Protocol-Aware Fuzzing"
    
    override fun reset() {
        testsGenerated = 0
    }
    
    override fun getProgress(): Double {
        return testsGenerated.toDouble() / maxTests.toDouble()
    }
}
