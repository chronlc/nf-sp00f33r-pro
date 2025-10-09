package com.nfsp00f33r.app.utils

/**
 * Centralized Hex Utilities for EMV Attack Modules
 * Eliminates duplicate extension functions across attack modules
 * Single source of truth for hex/byte array conversions
 */

/**
 * Convert byte array to hex string
 */
fun ByteArray.toHexString(): String = 
    joinToString("") { "%02X".format(it) }

/**
 * Convert hex string to byte array
 */
fun String.hexToByteArray(): ByteArray =
    chunked(2).map { it.toInt(16).toByte() }.toByteArray()
