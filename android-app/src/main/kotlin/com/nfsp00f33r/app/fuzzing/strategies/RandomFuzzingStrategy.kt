package com.nfsp00f33r.app.fuzzing.strategies

import java.security.SecureRandom
import kotlin.random.Random

/**
 * Random fuzzing strategy - generates completely random byte sequences
 * This is the simplest fuzzing approach with minimal protocol knowledge
 */
class RandomFuzzingStrategy(
    private val minLength: Int = 5,
    private val maxLength: Int = 255,
    private val maxTests: Int = 1000
) : FuzzingStrategy {
    
    private val random = SecureRandom()
    private var testsGenerated = 0
    
    override fun generateNextInput(seed: ByteArray?): ByteArray {
        testsGenerated++
        
        // Random length between min and max
        val length = minLength + random.nextInt(maxLength - minLength + 1)
        
        // Generate random bytes
        val bytes = ByteArray(length)
        random.nextBytes(bytes)
        
        return bytes
    }
    
    override fun shouldTerminate(): Boolean {
        return testsGenerated >= maxTests
    }
    
    override fun getName(): String = "Random Fuzzing"
    
    override fun reset() {
        testsGenerated = 0
    }
    
    override fun getProgress(): Double {
        return testsGenerated.toDouble() / maxTests.toDouble()
    }
}
