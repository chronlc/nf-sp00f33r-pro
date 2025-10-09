package com.nfsp00f33r.app.hardware

/**
 * Hardware Adapter Interface for PN532 NFC Module Communication
 * Supports USB Serial and Bluetooth HC-06 connections
 * NO SAFE CALL OPERATORS - Production-grade interface per newrule.md
 */
interface HardwareAdapter {
    
    /**
     * Connect to hardware device
     * @return true if connection successful, false otherwise
     */
    fun connect(): Boolean
    
    /**
     * Disconnect from hardware device
     */
    fun disconnect()
    
    /**
     * Send command to hardware device
     * @param command byte array command to send
     * @return response byte array from device
     */
    fun sendCommand(command: ByteArray): ByteArray
    
    /**
     * Check if device is connected
     * @return true if connected, false otherwise
     */
    fun isConnected(): Boolean
    
    /**
     * Get device information string
     * @return device info for display
     */
    fun getDeviceInfo(): String
    
    /**
     * Get connection type identifier
     * @return connection type string (USB/Bluetooth)
     */
    fun getConnectionType(): String
}
