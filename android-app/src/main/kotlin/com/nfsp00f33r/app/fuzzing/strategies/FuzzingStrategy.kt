package com.nfsp00f33r.app.fuzzing.strategies

/**
 * Interface for fuzzing strategies
 * Each strategy implements a different approach to generating test inputs
 */
interface FuzzingStrategy {
    /**
     * Generate the next test input
     * @param seed Optional seed data from previous tests
     * @return ByteArray to be used as APDU command
     */
    fun generateNextInput(seed: ByteArray?): ByteArray
    
    /**
     * Check if this strategy has exhausted its test space
     * @return true if no more meaningful tests can be generated
     */
    fun shouldTerminate(): Boolean
    
    /**
     * Get human-readable name of this strategy
     */
    fun getName(): String
    
    /**
     * Reset strategy state for new session
     */
    fun reset()
    
    /**
     * Get estimated progress (0.0 to 1.0)
     */
    fun getProgress(): Double
}
