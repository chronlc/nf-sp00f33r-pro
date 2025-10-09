package com.nfsp00f33r.app.hardware

import android.content.Context
import timber.log.Timber
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

/**
 * Android USB Serial adapter for PN532 communication
 * Per newrule.md: Production-grade implementation with HardwareAdapter interface
 */
class AndroidUSBSerialAdapter(
    private val context: Context,
    private val devicePath: String,
    private val baudRate: Int
) : HardwareAdapter {
    
    companion object {
        private const val TAG = "AndroidUSBSerialAdapter"
    }
    
    private var inputStream: FileInputStream? = null
    private var outputStream: FileOutputStream? = null
    private var isConnected = false
    
    override fun connect(): Boolean {
        try {
            inputStream = FileInputStream(devicePath)
            outputStream = FileOutputStream(devicePath)
            
            isConnected = true
            Timber.d("$TAG Connected to USB: $devicePath at $baudRate baud")
            return true
            
        } catch (e: IOException) {
            Timber.e("$TAG USB connection failed: ${e.message}")
            disconnect()
            return false
        }
    }
    
    override fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
        } catch (e: IOException) {
            Timber.e("$TAG Disconnect error: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            isConnected = false
            Timber.d("$TAG Disconnected from USB")
        }
    }
    
    override fun sendCommand(command: ByteArray): ByteArray {
        if (!isConnected || outputStream == null || inputStream == null) {
            Timber.e("$TAG Not connected")
            return byteArrayOf()
        }
        
        return try {
            // Send command
            outputStream!!.write(command)
            outputStream!!.flush()
            
            // Wait for response
            Thread.sleep(100)
            
            // Read response
            val buffer = ByteArray(1024)
            val bytesRead = inputStream!!.read(buffer)
            
            if (bytesRead > 0) {
                val response = buffer.copyOf(bytesRead)
                Timber.d("$TAG Command sent: ${command.joinToString("") { "%02X".format(it) }}")
                Timber.d("$TAG Response: ${response.joinToString("") { "%02X".format(it) }}")
                response
            } else {
                Timber.w("$TAG No response received")
                byteArrayOf()
            }
            
        } catch (e: IOException) {
            Timber.e("$TAG Command failed: ${e.message}")
            byteArrayOf()
        } catch (e: InterruptedException) {
            Timber.e("$TAG Command interrupted: ${e.message}")
            byteArrayOf()
        }
    }
    
    override fun isConnected(): Boolean = isConnected
    
    override fun getDeviceInfo(): String {
        return if (isConnected) {
            "USB Serial: $devicePath ($baudRate baud)"
        } else {
            "USB Serial: Disconnected"
        }
    }
    
    override fun getConnectionType(): String = "USB_SERIAL"
}
