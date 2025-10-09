package com.nfsp00f33r.app.data

data class ApduLogEntry(
    val timestamp: String,
    val command: String,
    val response: String,
    val statusWord: String,
    val description: String,
    val executionTimeMs: Long
)
