package com.nfsp00f33r.app.data

data class DatabaseCard(
    val cardholderName: String,
    val pan: String,
    val expiry: String,
    val cardType: String,
    val category: String,
    val apduCount: Int,
    val isEncrypted: Boolean,
    val lastUsed: String
)