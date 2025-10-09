package com.nfsp00f33r.app.hardware

/**

 * Interface for PN532 hardware adapters
 * Supports both USB and Bluetooth HC-06 connections
 */
interface PN532Adapter {

    /**

     * Connect to PN532 device
     */
    suspend fun connect(): Boolean


    /**
     * Disconnect from PN532 device
     */
    fun disconnect()

    /**
     * Send APDU command to target card via PN532
     */
    suspend fun sendApduCommand(command: ByteArray): ByteArray

    /**
     * Check if adapter is connected
     */
    fun isConnected(): Boolean

    /**
     * Get connection information
     */
    fun getConnectionInfo(): String
}
