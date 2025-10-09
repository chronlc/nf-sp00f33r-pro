package com.nfsp00f33r.app.fuzzing.strategies

import java.security.SecureRandom
import kotlin.random.Random

/**
 * Mutation-based fuzzing strategy
 * Takes valid APDUs and applies various mutations (bit flips, byte changes, etc.)
 */
class MutationFuzzingStrategy(
    private val seedCommands: List<ByteArray>,
    private val mutationsPerSeed: Int = 100
) : FuzzingStrategy {
    
    private val random = SecureRandom()
    private var currentSeedIndex = 0
    private var currentMutationCount = 0
    private var totalMutations = 0
    
    // Common interesting values for fuzzing
    private val interestingBytes = byteArrayOf(
        0x00, 0x01, 0x7F, 0x80.toByte(), 0xFF.toByte(),
        0x10, 0x20, 0x40
    )
    
    private val interestingWords = listOf(
        0x0000, 0x0001, 0x7FFF, 0x8000, 0xFFFF,
        0x0100, 0x1000, 0x00FF
    )
    
    override fun generateNextInput(seed: ByteArray?): ByteArray {
        if (seedCommands.isEmpty()) {
            // Fallback to random if no seeds provided
            return ByteArray(20).apply { random.nextBytes(this) }
        }
        
        val baseSeed = seedCommands[currentSeedIndex].copyOf()
        val mutated = applyMutation(baseSeed)
        
        totalMutations++
        currentMutationCount++
        
        if (currentMutationCount >= mutationsPerSeed) {
            currentMutationCount = 0
            currentSeedIndex = (currentSeedIndex + 1) % seedCommands.size
        }
        
        return mutated
    }
    
    private fun applyMutation(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        
        val mutationType = random.nextInt(10)
        val mutated = data.copyOf()
        
        when (mutationType) {
            0 -> {
                // Bit flip
                val byteIndex = random.nextInt(mutated.size)
                val bitIndex = random.nextInt(8)
                mutated[byteIndex] = (mutated[byteIndex].toInt() xor (1 shl bitIndex)).toByte()
            }
            1 -> {
                // Byte flip (bitwise NOT)
                val byteIndex = random.nextInt(mutated.size)
                mutated[byteIndex] = (mutated[byteIndex].toInt().inv()).toByte()
            }
            2 -> {
                // Replace with interesting byte
                val byteIndex = random.nextInt(mutated.size)
                mutated[byteIndex] = interestingBytes[random.nextInt(interestingBytes.size)]
            }
            3 -> {
                // Arithmetic mutation (add/subtract small value)
                val byteIndex = random.nextInt(mutated.size)
                val delta = (random.nextInt(35) - 17).toByte()
                mutated[byteIndex] = (mutated[byteIndex] + delta).toByte()
            }
            4 -> {
                // Truncate (reduce length)
                if (mutated.size > 5) {
                    val newLength = 5 + random.nextInt(mutated.size - 5)
                    return mutated.copyOf(newLength)
                }
            }
            5 -> {
                // Extend (increase length)
                val extension = ByteArray(random.nextInt(20) + 1)
                random.nextBytes(extension)
                return mutated + extension
            }
            6 -> {
                // Replace multiple bytes
                val startIndex = random.nextInt(mutated.size)
                val length = minOf(random.nextInt(4) + 1, mutated.size - startIndex)
                val replacement = ByteArray(length)
                random.nextBytes(replacement)
                System.arraycopy(replacement, 0, mutated, startIndex, length)
            }
            7 -> {
                // Insert bytes
                val insertIndex = random.nextInt(mutated.size)
                val insertData = ByteArray(random.nextInt(4) + 1)
                random.nextBytes(insertData)
                return mutated.copyOfRange(0, insertIndex) + insertData + 
                       mutated.copyOfRange(insertIndex, mutated.size)
            }
            8 -> {
                // Delete bytes
                if (mutated.size > 5) {
                    val deleteIndex = random.nextInt(mutated.size - 1)
                    val deleteLength = minOf(random.nextInt(3) + 1, mutated.size - deleteIndex - 1)
                    return mutated.copyOfRange(0, deleteIndex) + 
                           mutated.copyOfRange(deleteIndex + deleteLength, mutated.size)
                }
            }
            9 -> {
                // Shuffle bytes
                val start = random.nextInt(mutated.size)
                val end = start + minOf(random.nextInt(8) + 2, mutated.size - start)
                val section = mutated.copyOfRange(start, end)
                section.shuffle(Random(random.nextLong()))
                System.arraycopy(section, 0, mutated, start, section.size)
            }
        }
        
        return mutated
    }
    
    override fun shouldTerminate(): Boolean {
        return totalMutations >= (seedCommands.size * mutationsPerSeed)
    }
    
    override fun getName(): String = "Mutation Fuzzing"
    
    override fun reset() {
        currentSeedIndex = 0
        currentMutationCount = 0
        totalMutations = 0
    }
    
    override fun getProgress(): Double {
        val totalTests = seedCommands.size * mutationsPerSeed
        return totalMutations.toDouble() / totalTests.toDouble()
    }
}
