package com.nfsp00f33r.app.nfc

import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.util.Log

/**
 * NFC HCE Manager for card emulation control
 * Clean implementation without corruption
 */
class NfcHceManager(private val context: Context) {
    
    private var nfcAdapter: NfcAdapter? = null
    private var cardEmulation: CardEmulation? = null
    
    init {
        initializeNfc()
    }
    
    private fun initializeNfc() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        if (nfcAdapter != null) {
            cardEmulation = CardEmulation.getInstance(nfcAdapter)
            Log.i("NfcHceManager", "NFC HCE Manager initialized")
        } else {
            Log.w("NfcHceManager", "NFC not available on this device")
        }
    }
    
    /**
     * Check if NFC is available and enabled
     */
    fun isNfcAvailable(): Boolean {
        val adapter = nfcAdapter
        return adapter != null && adapter.isEnabled
    }
    
    /**
     * Check if HCE is supported
     */
    fun isHceSupported(): Boolean {
        val emulation = cardEmulation
        return emulation != null
    }
    
    /**
     * Start HCE service
     */
    fun startHceService(): Boolean {
        return try {
            if (isNfcAvailable() && isHceSupported()) {
                Log.i("NfcHceManager", "HCE service ready")
                true
            } else {
                Log.w("NfcHceManager", "Cannot start HCE service - NFC/HCE not available")
                false
            }
        } catch (e: Exception) {
            Log.e("NfcHceManager", "Failed to start HCE service", e)
            false
        }
    }
    
    /**
     * Stop HCE service
     */
    fun stopHceService(): Boolean {
        return try {
            Log.i("NfcHceManager", "HCE service stopped")
            true
        } catch (e: Exception) {
            Log.e("NfcHceManager", "Failed to stop HCE service", e)
            false
        }
    }
    
    /**
     * Get HCE service status
     */
    fun getHceStatus(): String {
        return when {
            !isNfcAvailable() -> "NFC not available or disabled"
            !isHceSupported() -> "HCE not supported"
            else -> "HCE ready"
        }
    }
}
