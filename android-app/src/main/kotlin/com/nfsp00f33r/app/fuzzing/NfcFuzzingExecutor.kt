package com.nfsp00f33r.app.fuzzing

import android.nfc.tech.IsoDep
import com.nfsp00f33r.app.hardware.PN532DeviceModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.io.IOException

/**
 * NFC Fuzzing Executor - Integrates with PN532Module and Android NFC
 * Provides real hardware fuzzing capabilities
 */
class NfcFuzzingExecutor(
    private val pn532Module: PN532DeviceModule? = null
) {
    
    enum class ExecutionMode {
        PN532_HARDWARE,    // Use PN532 device
        ANDROID_NFC,       // Use Android internal NFC (IsoDep)
        MOCK              // Mock executor for testing
    }
    
    private var currentMode = ExecutionMode.MOCK
    private var androidNfcTag: IsoDep? = null
    
    /**
     * Set execution mode
     */
    fun setExecutionMode(mode: ExecutionMode) {
        currentMode = mode
        Timber.i("🔧 Fuzzing execution mode: $mode")
    }
    
    /**
     * Set Android NFC tag for fuzzing
     */
    fun setAndroidNfcTag(tag: IsoDep?) {
        androidNfcTag = tag
        if (tag != null) {
            Timber.i("📱 Android NFC tag connected: ${tag.tag}")
        }
    }
    
    /**
     * Execute APDU command based on current mode
     * @return Pair<response, executionTime>
     */
    suspend fun executeCommand(command: ByteArray, timeoutMs: Long = 5000): Pair<ByteArray?, Long> {
        return when (currentMode) {
            ExecutionMode.PN532_HARDWARE -> executeWithPN532(command, timeoutMs)
            ExecutionMode.ANDROID_NFC -> executeWithAndroidNfc(command, timeoutMs)
            ExecutionMode.MOCK -> executeMockCommand(command)
        }
    }
    
    /**
     * Execute via PN532 hardware module
     */
    private suspend fun executeWithPN532(command: ByteArray, timeoutMs: Long): Pair<ByteArray?, Long> {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            
            try {
                // Check if PN532 is connected
                if (pn532Module == null || !pn532Module.isConnected()) {
                    Timber.w("⚠️ PN532 not connected")
                    return@withContext Pair(null, 0L)
                }
                
                // Execute with timeout
                val response = withTimeoutOrNull(timeoutMs) {
                    // Use PN532Module's transceive method
                    // This would need to be added to PN532Module
                    pn532Module.transceiveCommand(command)
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                
                if (response == null) {
                    Timber.w("⏱️ PN532 command timeout: ${command.toHexString()}")
                }
                
                Pair(response, executionTime)
                
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                Timber.e(e, "❌ PN532 execution error: ${command.toHexString()}")
                Pair(null, executionTime)
            }
        }
    }
    
    /**
     * Execute via Android internal NFC (IsoDep)
     */
    private suspend fun executeWithAndroidNfc(command: ByteArray, timeoutMs: Long): Pair<ByteArray?, Long> {
        return withContext(Dispatchers.IO) {
            val tag = androidNfcTag
            if (tag == null) {
                Timber.w("⚠️ No Android NFC tag connected")
                return@withContext Pair(null, 0L)
            }
            
            val startTime = System.currentTimeMillis()
            
            try {
                // Ensure tag is connected
                if (!tag.isConnected) {
                    tag.connect()
                }
                
                // Set timeout
                tag.timeout = timeoutMs.toInt()
                
                // Transceive command
                val response = withTimeoutOrNull(timeoutMs) {
                    tag.transceive(command)
                }
                
                val executionTime = System.currentTimeMillis() - startTime
                
                if (response == null) {
                    Timber.w("⏱️ Android NFC timeout: ${command.toHexString()}")
                }
                
                Pair(response, executionTime)
                
            } catch (e: IOException) {
                val executionTime = System.currentTimeMillis() - startTime
                Timber.e(e, "❌ Android NFC I/O error: ${command.toHexString()}")
                
                // Tag might be lost, try to reconnect
                try {
                    if (tag.isConnected) {
                        tag.close()
                    }
                } catch (closeException: Exception) {
                    Timber.e(closeException, "Failed to close NFC tag")
                }
                
                Pair(null, executionTime)
                
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                Timber.e(e, "❌ Android NFC error: ${command.toHexString()}")
                Pair(null, executionTime)
            }
        }
    }
    
    /**
     * Mock executor for testing
     */
    private suspend fun executeMockCommand(command: ByteArray): Pair<ByteArray?, Long> {
        // Simulate realistic timing
        val simulatedDelay = 10L + kotlin.random.Random.nextInt(100)
        kotlinx.coroutines.delay(simulatedDelay)
        
        // Mock response generator
        val mockResponse = when {
            command.size < 5 -> null // Simulate crash for malformed
            command[1] == 0xA4.toByte() -> byteArrayOf(0x90.toByte(), 0x00) // SELECT success
            command[1] == 0xB2.toByte() -> byteArrayOf(0x6A.toByte(), 0x82.toByte()) // READ RECORD error
            else -> {
                val data = ByteArray((0..20).random())
                data + byteArrayOf(0x90.toByte(), 0x00)
            }
        }
        
        return Pair(mockResponse, simulatedDelay)
    }
    
    /**
     * Check if hardware is ready for fuzzing
     */
    suspend fun isReady(): Boolean {
        return when (currentMode) {
            ExecutionMode.PN532_HARDWARE -> pn532Module?.isConnected() ?: false
            ExecutionMode.ANDROID_NFC -> androidNfcTag?.isConnected == true
            ExecutionMode.MOCK -> true
        }
    }
    
    /**
     * Get current execution mode name
     */
    fun getModeName(): String {
        return when (currentMode) {
            ExecutionMode.PN532_HARDWARE -> "PN532 Hardware"
            ExecutionMode.ANDROID_NFC -> "Android NFC"
            ExecutionMode.MOCK -> "Mock (Testing)"
        }
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            androidNfcTag?.close()
            androidNfcTag = null
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up NFC executor")
        }
    }
    
    private fun ByteArray.toHexString(): String =
        joinToString("") { "%02X".format(it) }
}

/**
 * Extension function for PN532Module to support transceive
 * This would need to be added to the actual PN532DeviceModule
 */
private suspend fun PN532DeviceModule.transceiveCommand(command: ByteArray): ByteArray? {
    // This is a placeholder - actual implementation would depend on PN532Module API
    // The module would need to expose a method like:
    // suspend fun transceive(command: ByteArray): ByteArray?
    Timber.d("🔧 PN532 transceive: ${command.joinToString("") { "%02X".format(it) }}")
    
    // For now, return mock response
    // In production, this would call actual PN532 hardware
    return byteArrayOf(0x90.toByte(), 0x00)
}
