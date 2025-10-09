package com.nfsp00f33r.app.data

data class VirtualCard(
    val cardholderName: String,
    val maskedPan: String,
    val expiryDate: String,
    val apduCount: Int,
    val cardType: String,
    val isEncrypted: Boolean = false,
    val lastUsed: String = "Never",
    val category: String = "PAYMENT"
)