package com.nfsp00f33r.app.utils

import java.nio.charset.StandardCharsets
import java.util.Arrays

/**
 * PRODUCTION-GRADE Utility Functions per Reference Repo Patterns
 * Based on Android_HCE_Emulate_A_CreditCard repository patterns
 * NO SIMPLIFIED CODE per newrule.md - ALL PRODUCTION-GRADE FUNCTIONALITY
 */
object Utils {

    private const val HEX_CHARS = "0123456789ABCDEF"

    /**
     * Convert hex string to byte array
     * Based on reference repo hexStringToByteArray() pattern
     */
    @JvmStatic
    fun hexStringToByteArray(hex: String): ByteArray {
        val cleanHex = hex.replace(" ", "").replace("\n", "").replace("\t", "").uppercase()
        if (cleanHex.isEmpty()) return ByteArray(0)
        if (cleanHex.length % 2 != 0) {
            throw IllegalArgumentException("Invalid hex string length: ${cleanHex.length}")
        }
        
        return ByteArray(cleanHex.length / 2) { i ->
            val index = i * 2
            try {
                ((Character.digit(cleanHex[index], 16) shl 4) + 
                 Character.digit(cleanHex[index + 1], 16)).toByte()
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid hex character at position $index: ${cleanHex.substring(index, index + 2)}")
            }
        }
    }

    /**
     * Convert byte array to hex string - No Preceding Exception (NPE)
     * Based on reference repo bytesToHexNpe() pattern
     */
    @JvmStatic
    fun bytesToHexNpe(bytes: ByteArray?): String {
        if (bytes == null || bytes.isEmpty()) return ""
        
        val hexChars = CharArray(bytes.size * 2)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            hexChars[i * 2] = HEX_CHARS[v ushr 4]
            hexChars[i * 2 + 1] = HEX_CHARS[v and 0x0F]
        }
        return String(hexChars)
    }

    /**
     * Concatenate byte arrays with proper error handling
     * Based on reference repo concatenateByteArrays() pattern
     */
    @JvmStatic
    fun concatenateByteArrays(vararg arrays: ByteArray): ByteArray {
        if (arrays.isEmpty()) return ByteArray(0)
        
        val totalLength = arrays.sumOf { it.size }
        val result = ByteArray(totalLength)
        
        var offset = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }
        
        return result
    }

    /**
     * Compare byte arrays safely using Arrays.equals
     * Based on reference repo exact command matching pattern
     */
    @JvmStatic
    fun bytesEqual(array1: ByteArray?, array2: ByteArray?): Boolean {
        return Arrays.equals(array1, array2)
    }

    /**
     * Extract first N bytes from byte array safely
     * Used for dynamic GPO handling (first 2 bytes pattern)
     */
    @JvmStatic
    fun extractBytes(source: ByteArray, length: Int): ByteArray {
        if (source.isEmpty() || length <= 0) return ByteArray(0)
        val actualLength = minOf(length, source.size)
        return Arrays.copyOf(source, actualLength)
    }

    /**
     * Convert byte array to printable ASCII string for debugging
     * Based on reference repo UTF-8 conversion pattern
     */
    @JvmStatic
    fun bytesToPrintableString(bytes: ByteArray): String {
        return try {
            val str = String(bytes, StandardCharsets.UTF_8)
            str.replace(Regex("[\\x00-\\x1F\\x7F-\\xFF]"), "?")
        } catch (e: Exception) {
            bytesToHexNpe(bytes)
        }
    }

    /**
     * Build APDU response with status word
     * Based on reference repo concatenateByteArrays(response, RESPONSE_OK_SW) pattern
     */
    @JvmStatic
    fun buildApduResponse(data: ByteArray, statusWord: String = "9000"): ByteArray {
        val sw = hexStringToByteArray(statusWord)
        return concatenateByteArrays(data, sw)
    }

    /**
     * Extract status word from APDU response (last 2 bytes)
     */
    @JvmStatic
    fun extractStatusWord(response: ByteArray): String {
        if (response.size < 2) return "0000"
        val sw = ByteArray(2)
        sw[0] = response[response.size - 2]
        sw[1] = response[response.size - 1]
        return bytesToHexNpe(sw)
    }

    /**
     * Check if APDU response indicates success (9000)
     */
    @JvmStatic
    fun isSuccessResponse(response: ByteArray): Boolean {
        if (response.size < 2) return false
        return response[response.size - 2] == 0x90.toByte() && 
               response[response.size - 1] == 0x00.toByte()
    }

    /**
     * Check if response indicates error
     */
    @JvmStatic
    fun isErrorResponse(response: ByteArray): Boolean {
        if (response.size < 2) return true
        val sw = extractStatusWord(response)
        return sw.startsWith("6") // All 6xxx responses are errors per project_memory.md
    }

    /**
     * Format APDU command for logging with description
     * Based on reference repo step-by-step logging pattern
     */
    @JvmStatic
    fun formatApduLog(command: ByteArray, response: ByteArray?, description: String, execTimeMs: Long = 0): String {
        val cmdHex = bytesToHexNpe(command)
        val respHex = if (response != null) bytesToHexNpe(response) else "NO_RESPONSE"
        val status = if (response != null && isSuccessResponse(response)) "✅" else if (response != null) "❌" else "⏳"
        val timing = if (execTimeMs > 0) " (${execTimeMs}ms)" else ""
        
        return "$status $description$timing\n📤 CMD: $cmdHex\n📥 RSP: $respHex"
    }

    /**
     * Parse EMV tag from TLV data
     * Enhanced TLV parsing for production use
     */
    @JvmStatic
    fun parseEmvTag(data: ByteArray, tag: String): ByteArray? {
        val tagBytes = hexStringToByteArray(tag)
        if (data.size < tagBytes.size + 1) return null
        
        var i = 0
        while (i < data.size - tagBytes.size) {
            if (bytesEqual(Arrays.copyOfRange(data, i, i + tagBytes.size), tagBytes)) {
                // Found tag, extract length and value
                val lengthPos = i + tagBytes.size
                if (lengthPos >= data.size) return null
                
                val length = data[lengthPos].toInt() and 0xFF
                val valuePos = lengthPos + 1
                
                if (valuePos + length <= data.size) {
                    return Arrays.copyOfRange(data, valuePos, valuePos + length)
                }
                return null
            }
            i++
        }
        return null
    }
    
    /**
     * Alias for bytesToHexNpe for compatibility
     */
    @JvmStatic
    fun bytesToHex(bytes: ByteArray): String {
        return bytesToHexNpe(bytes)
    }
    
    /**
     * Alias for hexStringToByteArray for compatibility  
     */
    @JvmStatic
    fun hexToBytes(hex: String): ByteArray {
        return hexStringToByteArray(hex)
    }
    
    /**
     * Build SELECT command for AID
     */
    @JvmStatic
    fun buildSelectCommand(aidBytes: ByteArray): ByteArray {
        val header = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte())
        return concatenateByteArrays(header, aidBytes)
    }
    
    /**
     * Build APDU command with optional data
     */
    @JvmStatic
    fun buildApduCommand(cla: Int, ins: Int, p1: Int, p2: Int, data: ByteArray?): ByteArray {
        return if (data == null) {
            byteArrayOf(cla.toByte(), ins.toByte(), p1.toByte(), p2.toByte())
        } else {
            val header = byteArrayOf(cla.toByte(), ins.toByte(), p1.toByte(), p2.toByte(), data.size.toByte())
            concatenateByteArrays(header, data)
        }
    }
}