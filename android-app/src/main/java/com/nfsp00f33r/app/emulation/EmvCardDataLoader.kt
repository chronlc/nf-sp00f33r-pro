package com.nfsp00f33r.app.emulation

import com.nfsp00f33r.app.data.EmvCardData
import com.nfsp00f33r.app.data.AttackCompatibilityAnalysis
import android.content.Context
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import timber.log.Timber

/**
 * PRODUCTION-GRADE EMV Card Data Loader
 * Loads and processes EMV card data with RFIDIOt/Proxmark3 patterns
 * NO SAFE CALL OPERATORS - Explicit null checks only per newrule.md
 */
class EmvCardDataLoader(private val context: Context) {

    private val _loadingState = MutableLiveData<LoadingState>()
    val loadingState: MutableLiveData<LoadingState> = _loadingState

    private val _currentCardData = MutableLiveData<EmvCardData?>()
    val currentCardData: MutableLiveData<EmvCardData?> = _currentCardData

    enum class LoadingState {
        IDLE,
        LOADING_CARD_DATA,
        PROCESSING_EMV_TAGS,
        ANALYZING_CRYPTOGRAM,
        COMPLETE,
        ERROR
    }

    /**
     * Load EMV card data with comprehensive validation and processing
     */
    fun loadCardData(rawEmvData: Map<String, Any>, lifecycleOwner: LifecycleOwner) {
        _loadingState.value = LoadingState.LOADING_CARD_DATA
        
        try {
            val emvCardData = processRawEmvData(rawEmvData)
            validateCardData(emvCardData)
            
            _currentCardData.value = emvCardData
            _loadingState.value = LoadingState.COMPLETE
            
            Timber.d("EMV card data loaded successfully: PAN=${emvCardData.pan}")
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to load EMV card data")
            _loadingState.value = LoadingState.ERROR
        }
    }

    /**
     * Process raw EMV data into structured EmvCardData object
     */
    private fun processRawEmvData(rawData: Map<String, Any>): EmvCardData {
        _loadingState.value = LoadingState.PROCESSING_EMV_TAGS
        
        return EmvCardData(
            pan = extractPan(rawData),
            track2Data = extractTrack2Data(rawData),
            expiryDate = extractExpiryDate(rawData),
            cardholderName = extractCardholderName(rawData),
            applicationInterchangeProfile = extractAip(rawData),
            applicationFileLocator = extractAfl(rawData),
            applicationTransactionCounter = extractAtc(rawData),
            applicationCryptogram = extractCryptogram(rawData),
            availableAids = extractAvailableAids(rawData),
            emvTags = processEmvTags(rawData),
            attackCompatibility = analyzeAttackCompatibility(rawData)
        )
    }

    /**
     * Extract PAN from raw EMV data
     */
    private fun extractPan(rawData: Map<String, Any>): String {
        val pan = rawData["5A"] as? String // PAN tag
        if (pan != null) {
            return pan.replace("F", "") // Remove padding
        }
        
        // Try Track2 extraction
        val track2 = rawData["57"] as? String // Track 2 Equivalent Data
        if (track2 != null) {
            val panFromTrack2 = track2.substringBefore("D")
            if (panFromTrack2.isNotEmpty()) {
                return panFromTrack2
            }
        }
        
        return "UNKNOWN_PAN"
    }

    /**
     * Extract Track2 data from raw EMV data
     */
    private fun extractTrack2Data(rawData: Map<String, Any>): String {
        val track2 = rawData["57"] as? String // Track 2 Equivalent Data
        if (track2 != null) {
            return track2
        }
        
        return "NO_TRACK2_DATA"
    }

    /**
     * Extract expiry date from raw EMV data
     */
    private fun extractExpiryDate(rawData: Map<String, Any>): String {
        val expiryDate = rawData["5F24"] as? String // Application Expiration Date
        if (expiryDate != null) {
            return expiryDate
        }
        
        // Try Track2 extraction
        val track2 = rawData["57"] as? String
        if (track2 != null) {
            val parts = track2.split("D")
            if (parts.size >= 2) {
                val expiryFromTrack2 = parts[1].substring(0, 4)
                if (expiryFromTrack2.length == 4) {
                    return expiryFromTrack2
                }
            }
        }
        
        return "0000"
    }

    /**
     * Extract cardholder name from raw EMV data
     */
    private fun extractCardholderName(rawData: Map<String, Any>): String {
        val cardholderName = rawData["5F20"] as? String // Cardholder Name
        if (cardholderName != null) {
            return cardholderName.trim()
        }
        
        return "CARDHOLDER"
    }

    /**
     * Extract AIP from raw EMV data
     */
    private fun extractAip(rawData: Map<String, Any>): String {
        val aip = rawData["82"] as? String // Application Interchange Profile
        if (aip != null) {
            return aip
        }
        
        return "0000"
    }

    /**
     * Extract AFL from raw EMV data
     */
    private fun extractAfl(rawData: Map<String, Any>): String {
        val afl = rawData["94"] as? String // Application File Locator
        if (afl != null) {
            return afl
        }
        
        return "00000000"
    }

    /**
     * Extract ATC from raw EMV data
     */
    private fun extractAtc(rawData: Map<String, Any>): String {
        val atc = rawData["9F36"] as? String // Application Transaction Counter
        if (atc != null) {
            return atc
        }
        
        return "0000"
    }

    /**
     * Extract application cryptogram from raw EMV data
     */
    private fun extractCryptogram(rawData: Map<String, Any>): String {
        // Try ARQC first
        val arqc = rawData["9F26"] as? String // Application Cryptogram
        if (arqc != null) {
            return arqc
        }
        
        // Try TC
        val tc = rawData["9F26"] as? String // Transaction Certificate
        if (tc != null) {
            return tc
        }
        
        return "0000000000000000"
    }

    /**
     * Extract available AIDs from raw EMV data
     */
    private fun extractAvailableAids(rawData: Map<String, Any>): List<String> {
        val aids = mutableListOf<String>()
        
        // Check for common EMV AIDs
        val commonAids = listOf(
            "A0000000031010", // VISA
            "A0000000041010", // MasterCard
            "A0000000980840", // VISA Debit
            "A0000000152001"  // Discover
        )
        
        commonAids.forEach { aid ->
            if (rawData.containsKey("aid_$aid")) {
                aids.add(aid)
            }
        }
        
        if (aids.isEmpty()) {
            aids.add("A0000000031010") // Default VISA AID
        }
        
        return aids
    }

    /**
     * Process EMV tags into structured map
     */
    private fun processEmvTags(rawData: Map<String, Any>): Map<String, String> {
        val emvTags = mutableMapOf<String, String>()
        
        rawData.forEach { (key, value) ->
            if (value is String && key.matches(Regex("[0-9A-F]+"))) {
                emvTags[key] = value
            }
        }
        
        return emvTags
    }

    /**
     * Analyze attack compatibility based on EMV data
     */
    private fun analyzeAttackCompatibility(rawData: Map<String, Any>): AttackCompatibilityAnalysis {
        val supportedAttacks = mutableListOf<String>()
        
        // PPSE Poisoning compatibility
        if (rawData.containsKey("6F")) {
            supportedAttacks.add("ppse_poisoning")
        }
        
        // AIP manipulation compatibility
        val aip = rawData["82"] as? String
        if (aip != null) {
            supportedAttacks.add("aip_manipulation")
        }
        
        // Track2 spoofing compatibility
        val track2 = rawData["57"] as? String
        if (track2 != null) {
            supportedAttacks.add("track2_spoofing")
        }
        
        // Cryptogram manipulation compatibility
        val cryptogram = rawData["9F26"] as? String
        if (cryptogram != null) {
            supportedAttacks.add("cryptogram_manipulation")
        }
        
        val riskLevel = when {
            supportedAttacks.size >= 3 -> "HIGH"
            supportedAttacks.size >= 1 -> "MEDIUM"
            else -> "LOW"
        }
        
        return AttackCompatibilityAnalysis(
            supportedAttacks = supportedAttacks,
            riskLevel = riskLevel
        )
    }

    /**
     * Validate loaded card data for completeness and integrity
     */
    private fun validateCardData(cardData: EmvCardData) {
        _loadingState.value = LoadingState.ANALYZING_CRYPTOGRAM
        
        // Validate PAN
        val pan = cardData.pan
        if (pan != null && (pan.length < 13 || pan.length > 19)) {
            throw IllegalArgumentException("Invalid PAN length: ${pan.length}")
        }
        
        // Validate Track2 data - NO SAFE CALL OPERATOR
        val track2Data = cardData.track2Data
        if (track2Data != null && track2Data.isNotEmpty()) {
            if (!track2Data.contains("D")) {
                Timber.w("Track2 data missing separator: $track2Data")
            }
            
            // Extract PAN from Track2 for validation
            val panFromTrack2 = track2Data.substringBefore("D")
            if (panFromTrack2.length >= 13) {
                val maskedPan = if (panFromTrack2.length >= 6) {
                    panFromTrack2.substring(0, 6) + "******"
                } else {
                    "******"
                }
                Timber.d("Track2 PAN validation successful: $maskedPan")
            }
        }
        
        // Validate cryptogram
        val cryptogram = cardData.applicationCryptogram
        if (cryptogram != null && cryptogram.length != 16) {
            Timber.w("Invalid cryptogram length: ${cryptogram.length}")
        }
        
        Timber.d("Card data validation complete")
    }

    /**
     * Get formatted card summary for display
     */
    fun getFormattedCardSummary(): String {
        val cardData = _currentCardData.value
        if (cardData == null) {
            return "No card data loaded"
        }
        
        val builder = StringBuilder()
        val pan = cardData.pan
        if (pan != null) {
            builder.append("PAN: $pan\n")
        } else {
            builder.append("PAN: Not available\n")
        }
        
        // Track2 formatting - NO SAFE CALL OPERATOR
        val track2Data = cardData.track2Data
        if (track2Data != null && track2Data.isNotEmpty()) {
            val displayTrack2 = if (track2Data.length > 20) {
                track2Data.substring(0, 20) + "..."
            } else {
                track2Data
            }
            builder.append("Track2: $displayTrack2\n")
        } else {
            builder.append("Track2: Not available\n")
        }
        
        val expiryDate = cardData.expiryDate
        if (expiryDate != null) {
            builder.append("Expiry: $expiryDate\n")
        }
        
        val cardholderName = cardData.cardholderName
        if (cardholderName != null) {
            builder.append("Cardholder: $cardholderName\n")
        }
        
        val atc = cardData.applicationTransactionCounter
        if (atc != null) {
            builder.append("ATC: $atc\n")
        }
        
        return builder.toString()
    }

    /**
     * Clear loaded card data
     */
    fun clearCardData() {
        _currentCardData.value = null
        _loadingState.value = LoadingState.IDLE
        Timber.d("Card data cleared")
    }

    /**
     * Observe loading state changes
     */
    fun observeLoadingState(lifecycleOwner: LifecycleOwner, observer: Observer<LoadingState>) {
        _loadingState.observe(lifecycleOwner, observer)
    }

    /**
     * Observe card data changes
     */
    fun observeCardData(lifecycleOwner: LifecycleOwner, observer: Observer<EmvCardData?>) {
        _currentCardData.observe(lifecycleOwner, observer)
    }
}
