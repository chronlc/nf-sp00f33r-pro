package com.nfsp00f33r.app.cardreading

import com.nfsp00f33r.app.data.EmvCardData
import com.nfsp00f33r.app.data.ApduLogEntry

/**
 * Enhanced callback interface for real-time NFC card reading operations
 */
interface CardReadingCallback {
    
    /**
     * Called when NFC reading starts
     */
    fun onReadingStarted()
    
    /**
     * Called when NFC reading stops
     */
    fun onReadingStopped()
    
    /**
     * Called when a card is successfully read with complete data
     */
    fun onCardRead(cardData: EmvCardData)
    
    /**
     * Called when an error occurs during reading
     */
    fun onError(error: String)
    
    /**
     * Called for each APDU command/response during reading (real-time updates)
     */
    fun onApduExchanged(apduEntry: ApduLogEntry)
    
    /**
     * Called when card is detected but before reading starts
     */
    fun onCardDetected()
    
    /**
     * Called with progress updates during EMV workflow
     */
    fun onProgress(step: String, progress: Int, total: Int)
}
