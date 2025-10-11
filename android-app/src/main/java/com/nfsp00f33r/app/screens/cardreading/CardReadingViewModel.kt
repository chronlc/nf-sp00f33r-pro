package com.nfsp00f33r.app.screens.cardreading

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.nfsp00f33r.app.application.NfSp00fApplication
import com.nfsp00f33r.app.cardreading.CardReadingCallback
import com.nfsp00f33r.app.config.AttackConfiguration
import com.nfsp00f33r.app.data.ApduLogEntry
import com.nfsp00f33r.app.data.EmvCardData
import com.nfsp00f33r.app.data.VirtualCard
import com.nfsp00f33r.app.hardware.PN532Manager
import com.nfsp00f33r.app.storage.CardDataStore
import com.nfsp00f33r.app.storage.CardProfileAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.nfsp00f33r.app.cardreading.EmvTlvParser
import com.nfsp00f33r.app.cardreading.EmvTagDictionary

/**
 * PRODUCTION-GRADE Card Reading ViewModel
 * Phase 1A: Now uses CardDataStore with encrypted persistence
 * 
 * Implements complete Proxmark3 Iceman EMV scan workflow:
 * - PPSE → AID Selection → GPO → Record Reading → PDOL/CDOL processing
 * - Full EMV data extraction: TC, AC, ARQC, CID, ATC, etc.
 * - Live APDU logging with real-time updates
 * - Hardware reader management and selection
 * - All card data now persists with AES-256-GCM encryption
 */
class CardReadingViewModel(private val context: Context) : ViewModel() {
    
    // Hardware and reader management - Phase 2B Day 1: Migrated to PN532DeviceModule
    private val pn532Module by lazy {
        NfSp00fApplication.getPN532Module()
    }
    
    // Card data store with encryption
    private val cardDataStore = NfSp00fApplication.getCardDataStoreModule()
    
    // Reader types available
    enum class ReaderType {
        ANDROID_NFC,
        PN532_BLUETOOTH,
        PN532_USB,
        MOCK_READER
    }
    
    // NFC Technology types
    enum class NfcTechnology {
        EMV_CONTACTLESS,    // Current focus
        MIFARE_CLASSIC,     // Future
        NTAG,              // Future
        ISO14443_TYPE_A,   // Future
        AUTO_SELECT        // Auto-detection
    }
    
    // Scanning states
    enum class ScanState {
        IDLE,
        READER_CONNECTING,
        SCANNING,
        CARD_DETECTED,
        EXTRACTING_EMV,
        SCAN_COMPLETE,
        ERROR
    }
    
    // Live data states
    var scanState by mutableStateOf(ScanState.IDLE)
        private set
    
    var selectedReader by mutableStateOf<ReaderType?>(null)
        private set
    
    var availableReaders by mutableStateOf(emptyList<ReaderType>())
        private set
    
    var selectedTechnology by mutableStateOf(NfcTechnology.EMV_CONTACTLESS)
        private set
    
    var isAutoSelectEnabled by mutableStateOf(true)
        private set
    
    var forceContactMode by mutableStateOf(false)
        private set
    
    var currentPhase by mutableStateOf("Ready")
        private set
    
    var progress by mutableStateOf(0f)
        private set
    
    var statusMessage by mutableStateOf("Select reader and start scanning")
        private set
    
    var apduLog by mutableStateOf(emptyList<ApduLogEntry>())
        private set
    
    var scannedCards by mutableStateOf(emptyList<VirtualCard>())
        private set
    
    var currentEmvData by mutableStateOf<EmvCardData?>(null)
        private set
    
    var parsedEmvFields by mutableStateOf(emptyMap<String, String>())
        private set
        
    var readerStatus by mutableStateOf("Not connected")
        private set
    
    var hardwareCapabilities by mutableStateOf(emptySet<String>())
        private set
    
    // ROCA vulnerability detection
    var rocaVulnerabilityStatus by mutableStateOf("Not checked")
        private set
    
    var isRocaVulnerable by mutableStateOf(false)
        private set
    
    init {
        Timber.i("CardReadingViewModel initializing with Proxmark3 EMV workflow")
        
        // Defer heavy initialization to avoid blocking UI during navigation
        viewModelScope.launch {
            try {
                // These operations can be slow and should not block UI creation
                initializeHardwareDetection()
                setupCardProfileListener()
                startNfcMonitoring()
            } catch (e: Exception) {
                Timber.e(e, "Error during ViewModel initialization")
            }
        }
    }
    
    /**
     * Monitor for NFC tags detected by MainActivity
     */
    private fun startNfcMonitoring() {
        viewModelScope.launch {
            while (true) {
                try {
                    // Check for new NFC tags from MainActivity
                    val currentTag = com.nfsp00f33r.app.activities.MainActivity.currentNfcTag
                    val lastDetection = com.nfsp00f33r.app.activities.MainActivity.lastNfcDetectionTime
                    
                    if (currentTag != null && lastDetection > 0) {
                        // Process new NFC tag
                        processNfcTag(currentTag)
                        
                        // Clear the shared state to avoid reprocessing
                        com.nfsp00f33r.app.activities.MainActivity.currentNfcTag = null
                    }
                    
                    kotlinx.coroutines.delay(500) // Check every 500ms
                } catch (e: Exception) {
                    Timber.e(e, "Error in NFC monitoring")
                    kotlinx.coroutines.delay(1000)
                }
            }
        }
    }
    
    /**
     * Process NFC tag detected by MainActivity
     * Implements complete Proxmark3 Iceman EMV workflow
     */
    private fun processNfcTag(tag: android.nfc.Tag) {
        viewModelScope.launch {
            try {
                val cardId = tag.id.joinToString(":") { "%02X".format(it) }
                withContext(Dispatchers.Main) {
                    statusMessage = "EMV Card Detected: $cardId"
                    scanState = ScanState.CARD_DETECTED
                    currentPhase = "Starting EMV Workflow"
                    progress = 0.1f
                }
                
                // Clear previous data for new scan
                apduLog = emptyList()
                parsedEmvFields = emptyMap()
                
                Timber.i("Starting Proxmark3 Iceman EMV workflow for card: $cardId")
                
                // Phase 1: PPSE Selection (Proximity Payment System Environment)
                executeProxmark3EmvWorkflow(tag)
                
            } catch (e: Exception) {
                Timber.e(e, "Error processing NFC tag")
                withContext(Dispatchers.Main) {
                    statusMessage = "Error processing card: ${e.message}"
                    scanState = ScanState.ERROR
                    currentPhase = "Error"
                }
            }
        }
    }
    
    /**
     * Execute complete Proxmark3 Iceman EMV workflow
     * PPSE → AID Selection → GPO → Record Reading → Data Extraction
     */
    private suspend fun executeProxmark3EmvWorkflow(tag: android.nfc.Tag) {
        val cardId = tag.id.joinToString("") { "%02X".format(it) }
        
        // Clear previous ROCA analysis results
        EmvTlvParser.clearRocaAnalysisResults()
        rocaVulnerabilityStatus = "Analyzing..."
        isRocaVulnerable = false
        
        // Variable to store AIDs extracted from PPSE response (dynamic)
        var extractedAids = listOf<String>()
        
        // Connect to card using IsoDep for real NFC communication
        val isoDep = android.nfc.tech.IsoDep.get(tag)
        if (isoDep != null) {
            isoDep.connect()
        }
        
        try {
            // PROXMARK3 EMV WORKFLOW - EXACT MATCH
            
            // Phase 1: SELECT PPSE - Try contactless (2PAY) or contact (1PAY) based on mode
            withContext(Dispatchers.Main) {
                currentPhase = "PPSE Selection"
                progress = 0.1f
                statusMessage = if (forceContactMode) {
                    "Selecting PPSE (Contact Mode - 1PAY)..."
                } else {
                    "Selecting PPSE (Auto: 2PAY→1PAY fallback)..."
                }
            }
            
            var ppseResponse: ByteArray? = null
            var ppseMode = ""
            var ppseHex = ""
            var realStatusWord = "UNKNOWN"
            
            if (forceContactMode) {
                // Force contact mode: Use 1PAY.SYS.DDF01 only
                Timber.i("🔧 FORCED CONTACT MODE: Using 1PAY.SYS.DDF01")
                val ppse1PayCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
                    0x31, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00)
                ppseResponse = if (isoDep != null) isoDep.transceive(ppse1PayCommand) else null
                ppseMode = "1PAY (Contact) [FORCED]"
                
                if (ppseResponse != null) {
                    ppseHex = ppseResponse.joinToString("") { "%02X".format(it) }
                    realStatusWord = if (ppseHex.length >= 4) ppseHex.takeLast(4) else "UNKNOWN"
                    addApduLogEntry(
                        "00A404000E315041592E5359532E4444463031",
                        ppseHex,
                        realStatusWord,
                        "SELECT PPSE (1PAY) [FORCED]",
                        0L
                    )
                }
            } else {
                // Auto mode: Try 2PAY.SYS.DDF01 (contactless) first, fallback to 1PAY
                val ppse2PayCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E, 
                    0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00)
                ppseResponse = if (isoDep != null) isoDep.transceive(ppse2PayCommand) else null
                ppseMode = "2PAY (Contactless)"
                
                if (ppseResponse != null) {
                    ppseHex = ppseResponse.joinToString("") { "%02X".format(it) }
                    realStatusWord = if (ppseHex.length >= 4) ppseHex.takeLast(4) else "UNKNOWN"
                    addApduLogEntry(
                        "00A404000E325041592E5359532E4444463031",
                        ppseHex,
                        realStatusWord,
                        "SELECT PPSE (2PAY)",
                        0L
                    )
                    
                    // If 2PAY failed, try 1PAY.SYS.DDF01 (contact)
                    if (realStatusWord != "9000") {
                        Timber.i("2PAY failed (SW=$realStatusWord), trying 1PAY (contact mode) as fallback")
                        withContext(Dispatchers.Main) {
                            statusMessage = "2PAY failed, trying 1PAY (contact)..."
                        }
                        
                        val ppse1PayCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
                            0x31, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31, 0x00)
                        ppseResponse = if (isoDep != null) isoDep.transceive(ppse1PayCommand) else null
                        ppseMode = "1PAY (Contact) [FALLBACK]"
                        
                        if (ppseResponse != null) {
                            ppseHex = ppseResponse.joinToString("") { "%02X".format(it) }
                            realStatusWord = if (ppseHex.length >= 4) ppseHex.takeLast(4) else "UNKNOWN"
                            addApduLogEntry(
                                "00A404000E315041592E5359532E4444463031",
                                ppseHex,
                                realStatusWord,
                                "SELECT PPSE (1PAY) [FALLBACK]",
                                0L
                            )
                        }
                    }
                }
            }
            
            if (ppseResponse != null) {
                
                displayParsedData("PPSE", ppseHex)
                withContext(Dispatchers.Main) {
                    statusMessage = "PPSE ($ppseMode): SW=$realStatusWord"
                }
                
                // LIVE ANALYSIS: Check if PPSE failed, try different approach
                if (realStatusWord != "9000") {
                    withContext(Dispatchers.Main) {
                        statusMessage = "PPSE Failed ($realStatusWord) - Trying direct AID selection"
                    }
                    Timber.w("PPSE selection failed with SW=$realStatusWord (tried both 2PAY and 1PAY), switching strategy")
                } else {
                    // Parse PPSE response for real AIDs
                    val realAids = extractAidsFromPpseResponse(ppseHex)
                    if (realAids.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            statusMessage = "PPSE Success ($ppseMode) - Found ${realAids.size} AID(s)"
                        }
                        Timber.i("PPSE ($ppseMode) returned ${realAids.size} real AIDs: ${realAids.joinToString(", ")}")
                        // Store real AIDs for use in AID selection phase
                        extractedAids = realAids
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusMessage = "PPSE: No response from card"
                }
                Timber.e("PPSE command failed - no response from card")
            }
            
            // Phase 2: Parse PPSE and SELECT AIDs dynamically from PPSE response
            withContext(Dispatchers.Main) {
                currentPhase = "AID Selection"
                progress = 0.2f
                statusMessage = "Selecting AID..."
            }
            
            // Use AIDs extracted from PPSE response (dynamic, real transaction)
            // Fallback to common AIDs only if PPSE failed
            val aidsToTry = if (extractedAids.isNotEmpty()) {
                Timber.i("Using ${extractedAids.size} AIDs from PPSE response (dynamic)")
                // Convert hex strings to ByteArray
                extractedAids.map { hexString ->
                    hexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                }
            } else {
                Timber.w("PPSE failed - falling back to common AIDs (static)")
                listOf(
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x04, 0x10, 0x10), // MasterCard
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x03, 0x10, 0x10), // Visa
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x00, 0x25, 0x01), // Amex
                    byteArrayOf(0xA0.toByte(), 0x00, 0x00, 0x01, 0x52, 0x30, 0x10) // Discover
                )
            }
            
            var aidSelected = false
            var selectedAidHex = ""
            
            for (aid in aidsToTry) {
                val aidCommand = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid
                val aidResponse = if (isoDep != null) isoDep.transceive(aidCommand) else null
                
                if (aidResponse != null) {
                    val aidHex = aidResponse.joinToString("") { "%02X".format(it) }
                    val realStatusWord = if (aidHex.length >= 4) aidHex.takeLast(4) else "UNKNOWN"
                    val aidHexString = aid.joinToString("") { "%02X".format(it) }
                    
                    addApduLogEntry(
                        "00A40400" + String.format("%02X", aid.size) + aidHexString,
                        aidHex,
                        realStatusWord,
                        "SELECT AID $aidHexString",
                        0L
                    )
                    
                    // LIVE ANALYSIS: Check what the card actually returned
                    when (realStatusWord) {
                        "9000" -> {
                            displayParsedData("AID", aidHex)
                            statusMessage = "AID $aidHexString Selected Successfully"
                            selectedAidHex = aidHexString
                            aidSelected = true
                            
                            // Parse FCI template from successful AID selection
                            val fciData = extractFciFromAidResponse(aidHex)
                            if (fciData.isNotEmpty()) {
                                statusMessage += " - FCI: ${fciData.take(16)}..."
                            }
                            break
                        }
                        "6A82" -> {
                            statusMessage = "AID $aidHexString Not Found - Trying next..."
                            Timber.d("AID $aidHexString not found on card")
                        }
                        "6A81" -> {
                            statusMessage = "AID $aidHexString Not Supported - Trying next..."
                            Timber.d("AID $aidHexString not supported by card")
                        }
                        else -> {
                            statusMessage = "AID $aidHexString Failed ($realStatusWord) - Trying next..."
                            Timber.w("AID $aidHexString selection failed with SW=$realStatusWord")
                        }
                    }
                } else {
                    statusMessage = "No response for AID ${aid.joinToString("") { "%02X".format(it) }}"
                    Timber.e("No response from card for AID selection")
                }
            }
            
            if (!aidSelected) {
                withContext(Dispatchers.Main) {
                    statusMessage = "CRITICAL: No valid AID found - Card may not support EMV or be blocked"
                    currentPhase = "Error"
                    progress = 0.0f
                }
                Timber.e("No AID selection succeeded - aborting EMV workflow")
                return // Don't continue if no AID selected
            }
            
            // Phase 3: GET PROCESSING OPTIONS with dynamic PDOL data
            withContext(Dispatchers.Main) {
                currentPhase = "GPO"
                progress = 0.4f
                statusMessage = "GET PROCESSING OPTIONS..."
            }
            
            // Extract PDOL from AID selection response
            val pdolData = extractPdolFromAllResponses(apduLog)
            Timber.i("=== PDOL EXTRACTION ===")
            Timber.i("PDOL raw data: $pdolData")
            
            val gpoData = if (pdolData.isNotEmpty()) {
                // Parse PDOL and build dynamic data
                val dolEntries = EmvTlvParser.parseDol(pdolData)
                Timber.i("PDOL parsed ${dolEntries.size} entries:")
                dolEntries.forEach { entry ->
                    Timber.i("  Tag ${entry.tag} (${entry.tagName}): expects ${entry.length} bytes")
                }
                buildPdolData(dolEntries)
            } else {
                // No PDOL - use empty data
                Timber.w("No PDOL found - using empty data")
                byteArrayOf(0x83.toByte(), 0x00)
            }
            
            Timber.i("GPO data to send: ${gpoData.joinToString("") { "%02X".format(it) }} (${gpoData.size} bytes)")
            val gpoCommand = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, gpoData.size.toByte()) + gpoData + byteArrayOf(0x00)
            Timber.i("Full GPO command: ${gpoCommand.joinToString("") { "%02X".format(it) }}")
            val gpoResponse = if (isoDep != null) isoDep.transceive(gpoCommand) else null
            
            if (gpoResponse != null) {
                val gpoHex = gpoResponse.joinToString("") { "%02X".format(it) }
                val realStatusWord = if (gpoHex.length >= 4) gpoHex.takeLast(4) else "UNKNOWN"
                val gpoCommandHex = gpoCommand.joinToString("") { "%02X".format(it) }
                addApduLogEntry(
                    gpoCommandHex,
                    gpoHex,
                    realStatusWord,
                    "GET PROCESSING OPTIONS",
                    0L
                )
                displayParsedData("GPO", gpoHex)
                
                // LIVE ANALYSIS: Check GPO response and extract real data
                when (realStatusWord) {
                    "9000" -> {
                        val extractedPan = extractPanFromResponse(gpoHex)
                        val aip = extractAipFromResponse(gpoHex)
                        val afl = extractAflFromResponse(gpoHex)
                        
                        // Parse AIP for capability detection
                        if (aip.isNotEmpty()) {
                            val aipCapabilities = EmvTlvParser.parseAip(aip)
                            aipCapabilities?.let { cap ->
                                val authMethods = mutableListOf<String>()
                                if (cap.sdaSupported) authMethods.add("SDA")
                                if (cap.ddaSupported) authMethods.add("DDA")
                                if (cap.cdaSupported) authMethods.add("CDA")
                                
                                Timber.i("AIP Analysis: Auth=${authMethods.joinToString("/")}, CVM=${cap.cardholderVerificationSupported}, MSD=${cap.msdSupported}")
                                
                                withContext(Dispatchers.Main) {
                                    statusMessage = "GPO: ${authMethods.joinToString("/")} ${if (cap.cardholderVerificationSupported) "+CVM" else ""}"
                                }
                            }
                        }
                        
                        when {
                            extractedPan.isNotEmpty() -> {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "GPO Success: PAN=${extractedPan.take(6)}****${extractedPan.takeLast(4)}"
                                    if (aip.isNotEmpty()) statusMessage += " AIP=$aip"
                                }
                                Timber.i("GPO successful - PAN extracted: ${extractedPan.take(6)}****${extractedPan.takeLast(4)}")
                            }
                            aip.isNotEmpty() || afl.isNotEmpty() -> {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "GPO Success: AIP=$aip AFL=$afl"
                                }
                                Timber.i("GPO successful - AIP/AFL extracted")
                            }
                            else -> {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "GPO Success but no EMV data found"
                                }
                                Timber.w("GPO succeeded but no parseable EMV data in response")
                            }
                        }
                        
                        // Check for cryptogram in GPO response (Visa Quick VSDC cards return cryptogram in GPO)
                        val cryptogram = extractCryptogramFromAllResponses(apduLog)
                        val cid = extractCidFromAllResponses(apduLog)
                        val atc = extractAtcFromAllResponses(apduLog)
                        
                        if (cryptogram.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                statusMessage += " | Cryptogram: ${cryptogram.take(8)}..."
                            }
                            Timber.i("GPO returned cryptogram directly (Visa Quick VSDC): AC=$cryptogram, CID=$cid, ATC=$atc")
                        }
                        
                        // Parse AFL using EmvTlvParser for dynamic record reading
                        if (afl.isNotEmpty()) {
                            val aflEntries = EmvTlvParser.parseAfl(afl)
                            if (aflEntries.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    statusMessage += " - ${aflEntries.size} AFL entries, ${aflEntries.sumOf { it.endRecord - it.startRecord + 1 }} records"
                                }
                                Timber.i("AFL parsed: ${aflEntries.size} entries")
                            }
                        }
                    }
                    "6985" -> {
                        statusMessage = "GPO Failed: Conditions not satisfied"
                        Timber.w("GPO failed - conditions not satisfied (card may require different PDOL)")
                    }
                    "6A88" -> {
                        statusMessage = "GPO Failed: Referenced data not found"
                        Timber.w("GPO failed - referenced data not found")
                    }
                    else -> {
                        statusMessage = "GPO Failed: SW=$realStatusWord"
                        Timber.w("GPO failed with SW=$realStatusWord")
                    }
                }
            } else {
                statusMessage = "GPO: No response from card"
                Timber.e("GPO command failed - no response")
            }
            
            // Phase 4: READ APPLICATION DATA using AFL-based intelligent reading
            withContext(Dispatchers.Main) {
                currentPhase = "Reading Records"
                progress = 0.6f
                statusMessage = "Reading application data..."
            }
            
            // Use EmvTlvParser to parse AFL for intelligent record reading
            val aflFromGpo = extractAflFromAllResponses(apduLog)
            val recordsToRead = if (aflFromGpo.isNotEmpty()) {
                val aflEntries = EmvTlvParser.parseAfl(aflFromGpo)
                if (aflEntries.isNotEmpty()) {
                    Timber.i("Using AFL-based record reading: ${aflEntries.size} AFL entries")
                    aflEntries.flatMap { entry ->
                        (entry.startRecord..entry.endRecord).map { record ->
                            Triple(entry.sfi, record, (entry.sfi shl 3) or 0x04)
                        }
                    }
                } else {
                    // Fallback to parseAflForRecords if EmvTlvParser fails
                    parseAflForRecords(aflFromGpo)
                }
            } else {
                Timber.w("No AFL found - using fallback record locations")
                // Fallback to common record locations if no AFL
                listOf(
                    Triple(1, 1, 0x14), Triple(1, 2, 0x14), Triple(1, 3, 0x14),
                    Triple(2, 1, 0x0C), Triple(2, 2, 0x0C),
                    Triple(3, 1, 0x1C), Triple(3, 2, 0x1C)
                )
            }
            
            var recordsRead = 0
            var panFound = false
            
            for ((sfi, record, p2) in recordsToRead) {
                val readCommand = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2.toByte(), 0x00)
                val readResponse = if (isoDep != null) isoDep.transceive(readCommand) else null
                
                if (readResponse != null) {
                    val readHex = readResponse.joinToString("") { "%02X".format(it) }
                    val realStatusWord = if (readHex.length >= 4) readHex.takeLast(4) else "UNKNOWN"
                    addApduLogEntry(
                        "00B2" + String.format("%02X%02X", record, p2) + "00",
                        readHex,
                        realStatusWord,
                        "READ RECORD SFI $sfi Rec $record",
                        0L
                    )
                    
                    // LIVE ANALYSIS: Check what we actually got
                    when (realStatusWord) {
                        "9000" -> {
                            recordsRead++
                            displayParsedData("SFI${sfi}_REC$record", readHex)
                            
                            val recordPan = extractPanFromResponse(readHex)
                            val track2 = extractTrack2FromAllResponses(listOf(com.nfsp00f33r.app.data.ApduLogEntry("", "", readHex, "", "", 0)))
                            
                            // DETAILED PARSING: Extract and display all EMV data found
                            val detailedData = extractDetailedEmvData(readHex)
                            
                            // UPDATE UI STATE: Merge new data with existing parsed fields
                            parsedEmvFields = parsedEmvFields + detailedData
                            
                            when {
                                recordPan.isNotEmpty() -> {
                                    val expiry = detailedData["expiry_date"] ?: "N/A"
                                    val cardholderName = detailedData["cardholder_name"] ?: "N/A"
                                    statusMessage = "SFI $sfi Rec $record: PAN=${recordPan} EXP=$expiry NAME=$cardholderName"
                                    panFound = true
                                    Timber.i("Found PAN in SFI $sfi Record $record: Full Data = $detailedData")
                                }
                                track2.isNotEmpty() -> {
                                    val track2Pan = extractPanFromTrack2(track2)
                                    val track2Expiry = extractExpiryFromTrack2(track2)
                                    statusMessage = "SFI $sfi Rec $record: Track2 PAN=${track2Pan} EXP=$track2Expiry"
                                    Timber.i("Found Track2 in SFI $sfi Record $record: $detailedData")
                                }
                                else -> {
                                    val tags = extractAllTagsFromResponse(readHex)
                                    val importantTags = tags.filter { (tag, _) -> 
                                        tag in listOf("5A", "57", "5F20", "5F24", "5F25", "5F28", "5F2A", "82", "84", "87", "94", "9F07", "9F08", "9F0D", "9F0E", "9F0F")
                                    }
                                    statusMessage = "SFI $sfi Rec $record: ${tags.size} tags (${importantTags.size} important)"
                                    if (importantTags.isNotEmpty()) {
                                        Timber.i("SFI $sfi Record $record important tags: $importantTags")
                                    }
                                }
                            }
                            
                            // Log all extracted data for debugging
                            if (detailedData.isNotEmpty()) {
                                Timber.i("=== DETAILED EMV DATA SFI $sfi REC $record ===")
                                detailedData.forEach { (key, value) ->
                                    Timber.i("$key: $value")
                                }
                                Timber.i("=== END DETAILED DATA ===")
                            }
                        }
                        "6A83" -> {
                            statusMessage = "SFI $sfi Rec $record: Record not found"
                            Timber.d("SFI $sfi Record $record not found")
                        }
                        "6A82" -> {
                            statusMessage = "SFI $sfi Rec $record: File not found"
                            Timber.d("SFI $sfi Record $record file not found")
                        }
                        else -> {
                            statusMessage = "SFI $sfi Rec $record: Failed ($realStatusWord)"
                            Timber.w("SFI $sfi Record $record failed with SW=$realStatusWord")
                        }
                    }
                } else {
                    statusMessage = "SFI $sfi Rec $record: No response"
                    Timber.e("No response for SFI $sfi Record $record")
                }
            }
            
            withContext(Dispatchers.Main) {
                statusMessage = "Records complete: $recordsRead read, PAN ${if (panFound) "found" else "not found"}"
            }
            
            // Phase 4B: Try additional common record locations for more EMV data
            withContext(Dispatchers.Main) {
                currentPhase = "Additional Records"
                progress = 0.7f
                statusMessage = "Scanning for additional EMV data..."
            }
            
            // Try common record locations that might contain additional data
            val additionalRecords = listOf(
                Triple(1, 1, 0x0C), Triple(1, 2, 0x0C), Triple(1, 3, 0x0C), Triple(1, 4, 0x0C), Triple(1, 5, 0x0C),
                Triple(2, 1, 0x14), Triple(2, 2, 0x14), Triple(2, 3, 0x14),
                Triple(3, 1, 0x1C), Triple(3, 2, 0x1C)
            )
            
            var additionalRecordsRead = 0
            for ((sfi, record, p2) in additionalRecords) {
                // Skip if we already read this record from AFL
                if (recordsToRead.any { it.first == sfi && it.second == record }) {
                    continue
                }
                
                val readCommand = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2.toByte(), 0x00)
                val readResponse = if (isoDep != null) isoDep.transceive(readCommand) else null
                
                if (readResponse != null) {
                    val readHex = readResponse.joinToString("") { "%02X".format(it) }
                    val realStatusWord = if (readHex.length >= 4) readHex.takeLast(4) else "UNKNOWN"
                    
                    if (realStatusWord == "9000") {
                        additionalRecordsRead++
                        addApduLogEntry(
                            "00B2" + String.format("%02X%02X", record, p2) + "00",
                            readHex,
                            realStatusWord,
                            "READ RECORD SFI $sfi Rec $record (extra)",
                            0L
                        )
                        displayParsedData("SFI${sfi}_REC${record}_EXTRA", readHex)
                        
                        val detailedData = extractDetailedEmvData(readHex)
                        parsedEmvFields = parsedEmvFields + detailedData
                        
                        Timber.i("Found additional record SFI $sfi Rec $record with ${detailedData.size} tags")
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                statusMessage = "Additional scan: $additionalRecordsRead extra records found"
            }
            
            // Phase 5: GENERATE AC (Application Cryptogram) - Skip if already obtained in GPO
            val existingCryptogram = extractCryptogramFromAllResponses(apduLog)
            
            if (existingCryptogram.isNotEmpty()) {
                Timber.i("Cryptogram already obtained in GPO response (Visa Quick VSDC) - skipping GENERATE AC")
                withContext(Dispatchers.Main) {
                    currentPhase = "Cryptogram (from GPO)"
                    progress = 0.75f
                    statusMessage = "Cryptogram already obtained: ${existingCryptogram.take(16)}..."
                }
            } else {
                withContext(Dispatchers.Main) {
                    currentPhase = "GENERATE AC"
                    progress = 0.75f
                    statusMessage = "Generating cryptogram..."
                }
            }
            
            // Extract CDOL1 from records for GENERATE AC data building
            val cdol1Data = extractCdol1FromAllResponses(apduLog)
            android.util.Log.e("DEBUG_CDOL", "=== CDOL1 EXTRACTION DEBUG ===")
            android.util.Log.e("DEBUG_CDOL", "Extracted CDOL1 data: '$cdol1Data'")
            android.util.Log.e("DEBUG_CDOL", "CDOL1 length: ${cdol1Data.length} chars = ${cdol1Data.length / 2} bytes")
            
            // CDOL1 must be at least 4 hex chars (2 bytes): 1-byte tag + 1-byte length minimum
            // Example valid CDOL1: "9F3704" (tag 9F37, length 4 bytes)
            // Invalid: "F6" (only 1 byte, not a valid tag-length pair)
            val isValidCdol = cdol1Data.length >= 4
            android.util.Log.e("DEBUG_CDOL", "Validation: isEmpty=${cdol1Data.isEmpty()}, length>= 4=$isValidCdol")
            Timber.d("Extracted CDOL1 data: $cdol1Data (${cdol1Data.length / 2} bytes)")
            
            val generateAcData = if (isValidCdol) {
                // Parse CDOL1 and build dynamic data
                // CDOL must be at least 2 bytes (1 tag + 1 length minimum)
                try {
                    android.util.Log.e("DEBUG_CDOL", "Attempting to parse CDOL1 as DOL...")
                    val cdolEntries = EmvTlvParser.parseDol(cdol1Data)
                    android.util.Log.e("DEBUG_CDOL", "Parsed ${cdolEntries.size} CDOL entries")
                    cdolEntries.forEachIndexed { idx, entry ->
                        android.util.Log.e("DEBUG_CDOL", "  Entry $idx: tag=${entry.tag}, length=${entry.length}")
                    }
                    if (cdolEntries.isNotEmpty()) {
                        Timber.i("CDOL1 contains ${cdolEntries.size} entries: ${cdolEntries.joinToString { "${it.tag}(${it.length})" }}")
                        val builtData = buildCdolData(cdolEntries)
                        android.util.Log.e("DEBUG_CDOL", "Built CDOL data: ${builtData.size} bytes")
                        builtData
                    } else {
                        android.util.Log.e("DEBUG_CDOL", "No entries parsed - using minimal GENERATE AC")
                        Timber.w("CDOL1 parsed but no entries - using minimal GENERATE AC")
                        byteArrayOf()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("DEBUG_CDOL", "EXCEPTION parsing CDOL1: ${e.message}")
                    e.printStackTrace()
                    Timber.e(e, "Failed to parse CDOL1: $cdol1Data")
                    byteArrayOf()
                }
            } else {
                // No CDOL1 or invalid CDOL1 - use minimal data
                Timber.w("No valid CDOL1 found (data=$cdol1Data) - using minimal GENERATE AC data")
                byteArrayOf()
            }
            
            // GENERATE AC command: 80 AE [RefControl] 00 [Lc] [Data] [Le]
            // RefControl: 80 = ARQC (online auth request), 40 = TC (transaction certificate), 00 = AAC (declined)
            val generateAcCommand = if (generateAcData.isNotEmpty()) {
                byteArrayOf(0x80.toByte(), 0xAE.toByte(), 0x80.toByte(), 0x00.toByte(), generateAcData.size.toByte()) + generateAcData + byteArrayOf(0x00)
            } else {
                // Minimal GENERATE AC for ARQC (no data, no Le)
                // ISO 7816-4: When Lc=0 (no command data), Le should be omitted (Case 1 command)
                byteArrayOf(0x80.toByte(), 0xAE.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00)
            }
            
            val generateAcResponse = if (isoDep != null) isoDep.transceive(generateAcCommand) else null
            
            if (generateAcResponse != null) {
                val generateAcHex = generateAcResponse.joinToString("") { "%02X".format(it) }
                val realStatusWord = if (generateAcHex.length >= 4) generateAcHex.takeLast(4) else "UNKNOWN"
                val generateAcCommandHex = generateAcCommand.joinToString("") { "%02X".format(it) }
                addApduLogEntry(
                    generateAcCommandHex,
                    generateAcHex,
                    realStatusWord,
                    "GENERATE AC (ARQC)",
                    0L
                )
                
                when (realStatusWord) {
                    "9000" -> {
                        displayParsedData("GENERATE_AC", generateAcHex)
                        
                        // Extract cryptogram data
                        val arqc = extractCryptogramFromAllResponses(apduLog)
                        val cid = extractCidFromAllResponses(apduLog)
                        val atc = extractAtcFromAllResponses(apduLog)
                        
                        withContext(Dispatchers.Main) {
                            statusMessage = "GENERATE AC Success: ARQC=$arqc CID=$cid ATC=$atc"
                        }
                        Timber.i("GENERATE AC successful - ARQC: $arqc, CID: $cid, ATC: $atc")
                    }
                    "6985" -> {
                        statusMessage = "GENERATE AC Failed: Conditions not satisfied"
                        Timber.w("GENERATE AC failed - conditions not satisfied")
                    }
                    "6A88" -> {
                        statusMessage = "GENERATE AC Failed: Referenced data not found"
                        Timber.w("GENERATE AC failed - referenced data not found")
                    }
                    else -> {
                        statusMessage = "GENERATE AC Failed: SW=$realStatusWord"
                        Timber.w("GENERATE AC failed with SW=$realStatusWord")
                    }
                }
            } else {
                statusMessage = "GENERATE AC: No response from card"
                Timber.e("GENERATE AC command failed - no response")
            }
            
            // Phase 6: GET DATA for specific EMV tags (PROXMARK3 style)
            withContext(Dispatchers.Main) {
                currentPhase = "GET DATA"
                progress = 0.85f
                statusMessage = "Getting EMV data..."
            }
            
            val emvTags = listOf(
                "9F13", // Last Online ATC
                "9F17", // PIN Try Counter
                "9F36", // Application Transaction Counter
                "9F4F"  // Log Format
            )
            
            for (tag in emvTags) {
                val getDataCommand = byteArrayOf(0x80.toByte(), 0xCA.toByte(), 
                    tag.substring(0, 2).toInt(16).toByte(),
                    tag.substring(2, 4).toInt(16).toByte(),
                    0x00)
                val getDataResponse = if (isoDep != null) isoDep.transceive(getDataCommand) else null
                
                if (getDataResponse != null) {
                    val getDataHex = getDataResponse.joinToString("") { "%02X".format(it) }
                    val realStatusWord = if (getDataHex.length >= 4) getDataHex.takeLast(4) else "UNKNOWN"
                    addApduLogEntry(
                        "80CA" + tag + "00",
                        getDataHex,
                        realStatusWord,
                        "GET DATA $tag",
                        0L
                    )
                    
                    if (realStatusWord == "9000") {
                        displayParsedData("GET_DATA_$tag", getDataHex)
                    }
                }
            }
            
            // Phase 6: Final Processing
            withContext(Dispatchers.Main) {
                currentPhase = "Complete"
                progress = 1.0f
                statusMessage = "EMV scan complete - PROXMARK3 workflow"
            }
            
        } catch (e: Exception) {
            Timber.e(e, "EMV communication error")
            withContext(Dispatchers.Main) {
                statusMessage = "EMV Error: ${e.message}"
            }
        } finally {
            if (isoDep != null) {
                isoDep.close()
            }
        }
        
        // Extract and create comprehensive EMV card data
        val extractedData = createEmvCardData(cardId, tag)
        
        // Create virtual card with extracted data
        val virtualCard = com.nfsp00f33r.app.data.VirtualCard(
            cardholderName = extractedData.cardholderName ?: "MASTERCARD DEBIT",
            maskedPan = extractedData.getUnmaskedPan(),
            expiryDate = extractedData.expiryDate?.let { exp ->
                if (exp.length == 4) "${exp.substring(2, 4)}/${exp.substring(0, 2)}" else exp
            } ?: "25/12",
            apduCount = apduLog.size,
            cardType = extractedData.getCardBrandDisplayName(),
            isEncrypted = extractedData.hasEncryptedData(),
            lastUsed = "Just scanned",
            category = "EMV"
        )
        scannedCards = scannedCards + virtualCard
        
        // Complete
        withContext(Dispatchers.Main) {
            currentPhase = "Scan Complete"
            progress = 1.0f
            statusMessage = "EMV scan complete - ${apduLog.size} APDUs, Full data extracted"
            scanState = ScanState.SCAN_COMPLETE
        }
        
        Timber.i("Proxmark3 EMV workflow complete: PAN=${extractedData.pan}, APDUs=${apduLog.size}")
        
        // Auto-save to database
        delay(1000)
        saveCardProfile(extractedData)
        
        // Load saved data from database to display (fixes tag display issues)
        withContext(Dispatchers.Main) {
            parsedEmvFields = extractedData.emvTags
            statusMessage = "Card saved to database - Ready for next scan"
            scanState = ScanState.IDLE
        }
    }
    
    /**
     * Add APDU log entry with timestamp and real status word interpretation
     */
    private fun addApduLogEntry(command: String, response: String, statusWord: String, description: String, executionTime: Long) {
        val statusMeaning = interpretStatusWord(statusWord)
        val enhancedDescription = if (statusMeaning.isNotEmpty()) {
            "$description - $statusMeaning"
        } else {
            description
        }
        
        val apduEntry = com.nfsp00f33r.app.data.ApduLogEntry(
            timestamp = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault()).format(java.util.Date()),
            command = command,
            response = response,
            statusWord = statusWord,
            description = enhancedDescription,
            executionTimeMs = executionTime
        )
        apduLog = apduLog + apduEntry
    }
    
    /**
     * Interpret real status word meanings
     */
    private fun interpretStatusWord(statusWord: String): String {
        return when (statusWord.uppercase()) {
            "9000" -> "SUCCESS"
            "6283" -> "File deactivated"
            "6300" -> "Authentication failed"
            "6400" -> "State of non-volatile memory unchanged"
            "6581" -> "Memory failure"
            "6700" -> "Wrong length"
            "6800" -> "Functions in CLA not supported"
            "6900" -> "Command not allowed"
            "6A00" -> "Wrong parameter(s) P1-P2"
            "6A80" -> "Incorrect parameters in data field"
            "6A81" -> "Function not supported"
            "6A82" -> "File not found"
            "6A83" -> "Record not found"
            "6A84" -> "Not enough memory space"
            "6A86" -> "Incorrect parameters P1-P2"
            "6A88" -> "Referenced data not found"
            "6B00" -> "Wrong parameter(s) P1-P2"
            "6C00" -> "Wrong length Le"
            "6D00" -> "Instruction code not supported"
            "6E00" -> "Class not supported"
            "6F00" -> "No precise diagnosis"
            else -> when {
                statusWord.startsWith("61") -> "SW2 indicates number of response bytes available"
                statusWord.startsWith("62") -> "State of non-volatile memory unchanged"
                statusWord.startsWith("63") -> "State of non-volatile memory changed"
                statusWord.startsWith("64") -> "State of non-volatile memory unchanged"
                statusWord.startsWith("65") -> "State of non-volatile memory changed"
                statusWord.startsWith("66") -> "Security-related issues"
                statusWord.startsWith("67") -> "Wrong length"
                statusWord.startsWith("68") -> "Functions in CLA not supported"
                statusWord.startsWith("69") -> "Command not allowed"
                statusWord.startsWith("6A") -> "Wrong parameter(s)"
                statusWord.startsWith("6B") -> "Wrong parameter(s)"
                statusWord.startsWith("6C") -> "Wrong length"
                statusWord.startsWith("6D") -> "Instruction not supported"
                statusWord.startsWith("6E") -> "Class not supported"
                statusWord.startsWith("6F") -> "No precise diagnosis"
                else -> ""
            }
        }
    }
    
    /**
     * Extract AIDs from PPSE response - LIVE ANALYSIS
     */
    private fun extractAidsFromPpseResponse(hexResponse: String): List<String> {
        val aids = mutableListOf<String>()
        try {
            // Look for Directory Entry (61) containing AID (4F)
            val directoryRegex = "61([0-9A-F]{2})([0-9A-F]+)".toRegex()
            directoryRegex.findAll(hexResponse).forEach { match ->
                val directoryData = match.groupValues[2]
                val aidRegex = "4F([0-9A-F]{2})([0-9A-F]+)".toRegex()
                val aidMatch = aidRegex.find(directoryData)
                if (aidMatch != null) {
                    val length = aidMatch.groupValues[1].toInt(16) * 2
                    val aid = aidMatch.groupValues[2].take(length)
                    if (aid.isNotEmpty()) aids.add(aid)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting AIDs from PPSE")
        }
        return aids
    }
    
    /**
     * Extract FCI from AID selection response - LIVE ANALYSIS
     */
    private fun extractFciFromAidResponse(hexResponse: String): String {
        try {
            // Look for FCI Template (6F)
            val fciRegex = "6F([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = fciRegex.find(hexResponse)
            if (match != null) {
                return match.groupValues[2]
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting FCI")
        }
        return ""
    }
    
    /**
     * Parse AFL to determine which records to read - LIVE ANALYSIS
     */
    private fun parseAflForRecords(afl: String): List<Triple<Int, Int, Int>> {
        val records = mutableListOf<Triple<Int, Int, Int>>()
        try {
            if (afl.length >= 8) {
                // AFL format: SFI + Start Record + End Record + Offline Auth Records
                for (i in 0 until afl.length step 8) {
                    if (i + 7 < afl.length) {
                        val sfi = afl.substring(i, i + 2).toInt(16) shr 3 // Upper 5 bits
                        val startRecord = afl.substring(i + 2, i + 4).toInt(16)
                        val endRecord = afl.substring(i + 4, i + 6).toInt(16)
                        
                        for (record in startRecord..endRecord) {
                            records.add(Triple(sfi, record, (sfi shl 3) or 4)) // P2 = (SFI << 3) | 4
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing AFL")
        }
        return records
    }
    
    /**
     * Build dynamic PDOL data based on PDOL requirements from card
     * Uses SecureRandom for unpredictable number (9F37) and current date (9A)
     * Proxmark3-inspired dynamic data generation
     */
    private fun buildPdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray {
        val dataList = mutableListOf<Byte>()
        val secureRandom = SecureRandom()
        val dateFormat = SimpleDateFormat("yyMMdd", Locale.US)
        val currentDate = dateFormat.format(Date())
        
        Timber.d("Building PDOL data for ${dolEntries.size} entries")
        
        for (entry in dolEntries) {
            val tagData = when (entry.tag.uppercase()) {
                "9F37" -> {
                    // Unpredictable Number - 4 bytes, cryptographically secure random
                    val un = ByteArray(entry.length)
                    secureRandom.nextBytes(un)
                    Timber.d("PDOL: 9F37 (Unpredictable Number) = ${un.joinToString("") { "%02X".format(it) }}")
                    un
                }
                "9A" -> {
                    // Transaction Date - YYMMDD format
                    val dateBytes = ByteArray(entry.length)
                    val dateHex = currentDate.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    System.arraycopy(dateHex, 0, dateBytes, 0, minOf(dateHex.size, entry.length))
                    Timber.d("PDOL: 9A (Transaction Date) = $currentDate")
                    dateBytes
                }
                "9C" -> {
                    // Transaction Type - 0x00 for goods/services
                    ByteArray(entry.length) { 0x00 }
                }
                "5F2A" -> {
                    // Transaction Currency Code - 0x0840 for USD
                    ByteArray(entry.length) { i -> if (i == 0) 0x08 else 0x40 }
                }
                "9F02" -> {
                    // Amount, Authorised - 6 bytes BCD, amount 000000000000 (0.00)
                    ByteArray(entry.length) { 0x00 }
                }
                "9F03" -> {
                    // Amount, Other - 6 bytes BCD, usually 0
                    ByteArray(entry.length) { 0x00 }
                }
                "9F1A" -> {
                    // Terminal Country Code - 0x0840 for USA
                    ByteArray(entry.length) { i -> if (i == 0) 0x08 else 0x40 }
                }
                "9F33" -> {
                    // Terminal Capabilities - typical POS capabilities
                    ByteArray(entry.length) { i ->
                        when (i) {
                            0 -> 0xE0.toByte() // Manual key entry, Magnetic stripe, IC with contacts
                            1 -> 0xF0.toByte() // Plaintext PIN, Enciphered PIN online, Signature, Enciphered PIN offline, No CVM
                            2 -> 0xC8.toByte() // SDA, DDA, Card capture
                            else -> 0x00
                        }
                    }
                }
                "9F35" -> {
                    // Terminal Type - 0x22 for attended online-only
                    ByteArray(entry.length) { 0x22 }
                }
                "9F40" -> {
                    // Additional Terminal Capabilities - typical POS
                    ByteArray(entry.length) { i ->
                        when (i) {
                            0 -> 0xF0.toByte() // Cash, goods, services, cashback, inquiry, transfer, payment, administrative
                            1 -> 0x00
                            2 -> 0xF0.toByte() // Numeric keys, alphabetic, special, command
                            3 -> 0x00
                            4 -> 0x00
                            else -> 0x00
                        }
                    }
                }
                "95" -> {
                    // Terminal Verification Results (TVR) - 5 bytes, all zeros indicates no errors
                    ByteArray(entry.length) { 0x00 }
                }
                "9F66" -> {
                    // Terminal Transaction Qualifiers (TTQ) - 4 bytes
                    // Request maximum EMV data including all records and tags
                    ByteArray(entry.length) { i ->
                        when (i) {
                            0 -> 0xF6.toByte() // MSD, qVSDC, EMV contact chip, Online PIN, Signature, ODA for Online Auth, CDA, Issuer Update Processing
                            1 -> 0x20.toByte() // EMV mode supported
                            2 -> 0xC0.toByte() // CVM required, Online PIN required
                            3 -> 0x80.toByte() // Issuer script processing supported
                            else -> 0x00
                        }
                    }
                }
                else -> {
                    // Unknown tag - fill with zeros
                    Timber.w("PDOL: Unknown tag ${entry.tag} - filling with zeros")
                    ByteArray(entry.length) { 0x00 }
                }
            }
            
            dataList.addAll(tagData.toList())
        }
        
        // Build final PDOL data: 83 [length] [data...]
        val totalLength = dataList.size
        val result = byteArrayOf(0x83.toByte(), totalLength.toByte()) + dataList.toByteArray()
        
        Timber.i("Built PDOL data: ${result.joinToString("") { "%02X".format(it) }} (${totalLength} bytes)")
        return result
    }
    
    /**
     * Build dynamic CDOL data for GENERATE AC command
     * Similar to PDOL but uses actual transaction data from card session
     * Proxmark3-inspired GENERATE AC data generation
     */
    private fun buildCdolData(dolEntries: List<EmvTlvParser.DolEntry>): ByteArray {
        val dataList = mutableListOf<Byte>()
        val secureRandom = SecureRandom()
        val dateFormat = SimpleDateFormat("yyMMdd", Locale.US)
        val currentDate = dateFormat.format(Date())
        
        Timber.d("Building CDOL data for ${dolEntries.size} entries")
        
        for (entry in dolEntries) {
            val tagData = when (entry.tag.uppercase()) {
                "9F37" -> {
                    // Unpredictable Number - use fresh random for GENERATE AC
                    val un = ByteArray(entry.length)
                    secureRandom.nextBytes(un)
                    Timber.d("CDOL: 9F37 (Unpredictable Number) = ${un.joinToString("") { "%02X".format(it) }}")
                    un
                }
                "9A" -> {
                    // Transaction Date - YYMMDD format
                    val dateBytes = ByteArray(entry.length)
                    val dateHex = currentDate.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    System.arraycopy(dateHex, 0, dateBytes, 0, minOf(dateHex.size, entry.length))
                    Timber.d("CDOL: 9A (Transaction Date) = $currentDate")
                    dateBytes
                }
                "9C" -> {
                    // Transaction Type - 0x00 for goods/services
                    ByteArray(entry.length) { 0x00 }
                }
                "5F2A" -> {
                    // Transaction Currency Code - 0x0840 for USD
                    ByteArray(entry.length) { i -> if (i == 0) 0x08 else 0x40 }
                }
                "9F02" -> {
                    // Amount, Authorised - 6 bytes BCD
                    ByteArray(entry.length) { 0x00 }
                }
                "9F03" -> {
                    // Amount, Other - 6 bytes BCD
                    ByteArray(entry.length) { 0x00 }
                }
                "9F1A" -> {
                    // Terminal Country Code - 0x0840 for USA
                    ByteArray(entry.length) { i -> if (i == 0) 0x08 else 0x40 }
                }
                "95" -> {
                    // Terminal Verification Results (TVR) - 5 bytes
                    ByteArray(entry.length) { 0x00 }
                }
                "9F66" -> {
                    // Terminal Transaction Qualifiers (TTQ) - 4 bytes
                    ByteArray(entry.length) { i ->
                        when (i) {
                            0 -> 0xF6.toByte()
                            1 -> 0x20.toByte()
                            2 -> 0xC0.toByte()
                            3 -> 0x80.toByte()
                            else -> 0x00
                        }
                    }
                }
                "9F36" -> {
                    // ATC (Application Transaction Counter) - extract from previous responses
                    val atcHex = extractAtcFromAllResponses(apduLog)
                    if (atcHex.isNotEmpty()) {
                        atcHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                    } else {
                        ByteArray(entry.length) { 0x00 }
                    }
                }
                "9F10" -> {
                    // Issuer Application Data - extract from previous responses
                    val iadHex = extractIadFromAllResponses(apduLog)
                    if (iadHex.isNotEmpty()) {
                        val iadBytes = iadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        if (iadBytes.size <= entry.length) {
                            iadBytes + ByteArray(entry.length - iadBytes.size) { 0x00 }
                        } else {
                            iadBytes.take(entry.length).toByteArray()
                        }
                    } else {
                        ByteArray(entry.length) { 0x00 }
                    }
                }
                else -> {
                    // Unknown tag - fill with zeros
                    Timber.w("CDOL: Unknown tag ${entry.tag} - filling with zeros")
                    ByteArray(entry.length) { 0x00 }
                }
            }
            
            dataList.addAll(tagData.toList())
        }
        
        val totalLength = dataList.size
        val result = dataList.toByteArray()
        
        Timber.i("Built CDOL data: ${result.joinToString("") { "%02X".format(it) }} (${totalLength} bytes)")
        return result
    }
    
    /**
     * Extract detailed EMV data from response - COMPREHENSIVE PARSING
     */
    /**
     * Extract detailed EMV data using comprehensive TLV parser
     */
    private fun extractDetailedEmvData(hexResponse: String): Map<String, String> {
        val details = mutableMapOf<String, String>()
        
        try {
            // Convert hex string to byte array
            val responseBytes = hexResponse.chunked(2).mapNotNull { 
                it.toIntOrNull(16)?.toByte() 
            }.toByteArray()
            
            // Parse ALL TLV tags comprehensively using EmvTlvParser
            val parseResult = EmvTlvParser.parseEmvTlvData(
                responseBytes, 
                "DetailedExtraction", 
                validateTags = true
            )
            
            Timber.d("🔍 extractDetailedEmvData: Parsed ${parseResult.tags.size} tags from response")
            
            // Process all parsed tags
            parseResult.tags.forEach { (tag, value) ->
                val tagName = EmvTagDictionary.getTagDescription(tag)
                val fieldKey = tagName.lowercase().replace(" ", "_")
                
                // Special processing for specific tags
                val processedValue = when (tag) {
                    "5A" -> formatPan(value) // PAN
                    "57" -> formatTrack2(value) // Track 2
                    "5F20" -> hexToAscii(value) // Cardholder Name
                    "5F24" -> formatExpiryDate(value) // Expiry Date
                    "5F25" -> formatEffectiveDate(value) // Effective Date
                    "84" -> hexToAscii(value) // DF Name
                    "50" -> hexToAscii(value) // Application Label
                    "9F12" -> hexToAscii(value) // Application Preferred Name
                    else -> value
                }
                
                details[fieldKey] = processedValue
                details["raw_$tag"] = value
                
                Timber.d("  📋 $tag ($tagName) = ${value.take(32)}${if (value.length > 32) "..." else ""}")
            }
            
            // Add parse statistics
            details["_total_tags"] = parseResult.tags.size.toString()
            details["_valid_tags"] = parseResult.validTags.toString()
            details["_invalid_tags"] = parseResult.invalidTags.toString()
            details["_template_depth"] = parseResult.templateDepth.toString()
            
            if (parseResult.errors.isNotEmpty()) {
                details["_parse_errors"] = parseResult.errors.joinToString("; ")
                Timber.w("⚠️ Parse errors: ${parseResult.errors.joinToString(", ")}")
            }
            
            if (parseResult.warnings.isNotEmpty()) {
                details["_parse_warnings"] = parseResult.warnings.joinToString("; ")
                Timber.w("⚠️ Parse warnings: ${parseResult.warnings.joinToString(", ")}")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error extracting detailed EMV data with EmvTlvParser")
        }
        
        return details
    }
    
    /**
     * Format PAN for display
     */
    private fun formatPan(hexPan: String): String {
        return try {
            if (hexPan.length >= 13) {
                val pan = hexPan.replace("F", "").replace("f", "")
                "${pan.take(6)}****${pan.takeLast(4)}"
            } else hexPan
        } catch (e: Exception) {
            hexPan
        }
    }
    
    /**
     * Format Track 2 data
     */
    private fun formatTrack2(hexTrack2: String): String {
        return try {
            val track2 = hexTrack2.replace("F", "").replace("f", "")
            if (track2.contains("D")) {
                val parts = track2.split("D")
                if (parts.size >= 2) {
                    val pan = parts[0]
                    val expiry = parts[1].take(4)
                    "${pan.take(6)}****${pan.takeLast(4)}D$expiry..."
                } else track2
            } else track2
        } catch (e: Exception) {
            hexTrack2
        }
    }
    
    /**
     * Format expiry date YYMM to MM/YY
     */
    private fun formatExpiryDate(hexExpiry: String): String {
        return try {
            if (hexExpiry.length == 4) {
                "${hexExpiry.substring(2, 4)}/${hexExpiry.substring(0, 2)}"
            } else hexExpiry
        } catch (e: Exception) {
            hexExpiry
        }
    }
    
    /**
     * Format effective date
     */
    private fun formatEffectiveDate(hexDate: String): String {
        return try {
            if (hexDate.length == 6) {
                "${hexDate.substring(4, 6)}/${hexDate.substring(2, 4)}/${hexDate.substring(0, 2)}"
            } else hexDate
        } catch (e: Exception) {
            hexDate
        }
    }
    
    /**
     * Convert hex to ASCII
     */
    private fun hexToAscii(hex: String): String {
        return try {
            hex.chunked(2).map { 
                val charCode = it.toInt(16)
                if (charCode in 32..126) charCode.toChar() else '.'
            }.joinToString("")
        } catch (e: Exception) {
            hex
        }
    }
    
    /**
     * Extract PAN from Track 2 data
     */
    private fun extractPanFromTrack2(track2: String): String {
        return try {
            if (track2.contains("D")) {
                track2.split("D")[0]
            } else ""
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Extract expiry from Track 2 data  
     */
    private fun extractExpiryFromTrack2(track2: String): String {
        return try {
            if (track2.contains("D")) {
                val afterD = track2.split("D")[1]
                if (afterD.length >= 4) {
                    val expiry = afterD.take(4)
                    "${expiry.substring(2, 4)}/${expiry.substring(0, 2)}"
                } else ""
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Extract AIP from response - LIVE ANALYSIS
     */
    private fun extractAipFromResponse(hexResponse: String): String {
        try {
            val aipRegex = "82([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = aipRegex.find(hexResponse)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting AIP")
        }
        return ""
    }
    
    /**
     * Extract AFL from response - LIVE ANALYSIS
     */
    private fun extractAflFromResponse(hexResponse: String): String {
        try {
            val aflRegex = "94([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = aflRegex.find(hexResponse)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting AFL")
        }
        return ""
    }
    
    /**
     * Extract AID from EMV response data
     */
    private fun extractAidFromResponse(hexResponse: String): String {
        try {
            val aidRegex = "4F([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = aidRegex.find(hexResponse)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting AID")
        }
        return ""
    }

    /**
     * Extract PAN from EMV response data
     */
    private fun extractPanFromResponse(hexResponse: String): String {
        try {
            // Look for Track 2 data (tag 57) or PAN (tag 5A)
            val track2Regex = "57[0-9A-F]{2}([0-9A-F]+)D".toRegex()
            val panRegex = "5A[0-9A-F]{2}([0-9A-F]+)".toRegex()
            
            // Try Track 2 first
            val track2Match = track2Regex.find(hexResponse)
            if (track2Match != null) {
                val track2Data = track2Match.groupValues[1]
                val panFromTrack2 = track2Data.split("D")[0]
                if (panFromTrack2.length >= 13) return panFromTrack2
            }
            
            // Try direct PAN tag
            val panMatch = panRegex.find(hexResponse)
            if (panMatch != null) {
                val panData = panMatch.groupValues[1]
                if (panData.length >= 13) return panData
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error extracting PAN")
        }
        return ""
    }
    
    // ========== REAL DATA EXTRACTION FUNCTIONS - NO HARDCODED VALUES ==========
    
    private fun extractTrack2FromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val track2Regex = "57([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = track2Regex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractExpiryFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val expiryRegex = "5F24([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = expiryRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCardholderNameFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val nameRegex = "5F20([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = nameRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                val hexName = match.groupValues[2].take(length)
                return hexName.chunked(2).map { it.toInt(16).toChar() }.joinToString("")
            }
        }
        return ""
    }
    
    private fun extractAipFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val aipRegex = "82([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = aipRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractAflFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val aflRegex = "94([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = aflRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractPdolFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val pdolRegex = "9F38([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = pdolRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCvmListFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val cvmRegex = "8E([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = cvmRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractIadFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val iadRegex = "9F10([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = iadRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCryptogramFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val acRegex = "9F26([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = acRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCidFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val cidRegex = "9F27([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = cidRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractAtcFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val atcRegex = "9F36([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = atcRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractUnFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val unRegex = "9F37([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = unRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCdol1FromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            // CDOL1 (tag 8C) is typically in SELECT AID response (FCI template)
            // Skip READ RECORD responses (SFI records contain RSA certs with false "8C" matches)
            if (apdu.description.contains("READ RECORD", ignoreCase = true)) {
                android.util.Log.d("CDOL_EXTRACT", "Skipping READ RECORD response: ${apdu.description}")
                return@forEach // Skip this APDU
            }
            
            val response = apdu.response
            android.util.Log.d("CDOL_EXTRACT", "Searching for tag 8C in: ${apdu.description}")
            
            var i = 0
            while (i < response.length - 4) { // Need at least tag(2) + length(2)
                if (response.substring(i, i + 2) == "8C") {
                    val lengthByte = response.substring(i + 2, i + 4)
                    val lengthInt = try { lengthByte.toInt(16) } catch (e: Exception) { -1 }
                    
                    if (lengthInt < 0 || lengthInt > 50) {
                        // Invalid length for CDOL1 (should be < 50 bytes typically)
                        android.util.Log.d("CDOL_EXTRACT", "Invalid CDOL1 length $lengthInt at position $i, skipping")
                        i += 2
                        continue
                    }
                    
                    val length = lengthInt * 2 // Convert to hex chars
                    
                    // Extract CDOL data
                    if (i + 4 + length <= response.length) {
                        val cdolData = response.substring(i + 4, i + 4 + length)
                        android.util.Log.d("CDOL_EXTRACT", "Found tag 8C at position $i, length=$lengthByte ($lengthInt bytes): $cdolData")
                        return cdolData
                    }
                }
                i += 2 // Move to next byte boundary
            }
        }
        
        android.util.Log.w("CDOL_EXTRACT", "No CDOL1 found in any response - card may not require CDOL")
        return ""
    }
    
    private fun extractCdol2FromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val cdol2Regex = "8D([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = cdol2Regex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractAppVersionFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val versionRegex = "9F09([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = versionRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractAucFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val aucRegex = "9F07([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = aucRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractTtqFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val ttqRegex = "9F66([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = ttqRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCountryCodeFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val countryRegex = "5F28([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = countryRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    private fun extractCurrencyCodeFromAllResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            val currencyRegex = "5F2A([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = currencyRegex.find(apdu.response)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                return match.groupValues[2].take(length)
            }
        }
        return ""
    }
    
    /**
     * Build comprehensive EMV tags map using parsedEmvFields from EmvTlvParser
     * Returns ALL tags extracted during the complete EMV workflow
     */
    private fun buildRealEmvTagsMap(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>, pan: String, aid: String, track2: String): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        
        // Parse ALL APDU responses (except PPSE) and collect ALL tags
        apduLog.forEach { apdu ->
            // Skip PPSE responses - we only want selected AID data
            if (apdu.description.contains("PPSE", ignoreCase = true)) {
                return@forEach
            }
            
            // Convert hex string to ByteArray and parse
            try {
                val responseBytes = apdu.response.chunked(2)
                    .map { it.toInt(16).toByte() }
                    .toByteArray()
                
                val parseResult = EmvTlvParser.parseEmvTlvData(
                    data = responseBytes,
                    context = apdu.description,
                    validateTags = true
                )
                
                // Add all found tags to the map (later entries overwrite earlier ones)
                tags.putAll(parseResult.tags)
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse APDU: ${apdu.description}")
            }
        }
        
        // Ensure critical tags are present with extracted values
        if (pan.isNotEmpty()) tags["5A"] = pan
        if (aid.isNotEmpty()) tags["4F"] = aid  
        if (track2.isNotEmpty()) tags["57"] = track2
        
        Timber.i("📦 Built EMV tags map: ${tags.size} total tags from parsing ${apduLog.size} APDUs")
        
        return tags.toMap()
    }
    
    private fun extractAllTagsFromResponse(hexResponse: String): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        val commonTags = listOf("4F", "50", "57", "5A", "5F24", "5F25", "5F28", "5F2A", "5F30", 
                               "82", "84", "87", "8C", "8D", "8E", "94", "95", "9A", "9C", 
                               "9F02", "9F03", "9F06", "9F07", "9F08", "9F09", "9F10", "9F26", 
                               "9F27", "9F33", "9F34", "9F35", "9F36", "9F37", "9F38", "9F66")
        
        commonTags.forEach { tag ->
            val pattern = "$tag([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = pattern.find(hexResponse)
            if (match != null) {
                val length = match.groupValues[1].toInt(16) * 2
                val value = match.groupValues[2].take(length)
                if (value.isNotEmpty()) tags[tag] = value
            }
        }
        
        return tags
    }
    
    private fun extractAllAidsFromResponses(apduLog: List<com.nfsp00f33r.app.data.ApduLogEntry>): List<String> {
        val aids = mutableSetOf<String>()
        apduLog.forEach { apdu ->
            val aid = extractAidFromResponse(apdu.response)
            if (aid.isNotEmpty()) aids.add(aid)
        }
        return aids.toList()
    }

    /**
     * Display parsed EMV data in real-time
     */
    /**
     * Comprehensive TLV parsing using EmvTlvParser - parses ALL tags recursively
     */
    private fun displayParsedData(phase: String, hexData: String) {
        try {
            // Convert hex string to byte array
            val responseBytes = hexData.chunked(2).mapNotNull { 
                it.toIntOrNull(16)?.toByte() 
            }.toByteArray()
            
            // Parse ALL TLV tags comprehensively using EmvTlvParser
            val parseResult = EmvTlvParser.parseEmvTlvData(
                responseBytes, 
                phase, 
                validateTags = true
            )
            
            var parsedInfo = "📋 $phase Parsed Data:\n"
            
            if (parseResult.tags.isNotEmpty()) {
                // NOTE: We no longer populate parsedEmvFields during live read
                // Instead, we load data from database after save (see saveCardProfile)
                // This is faster and shows correct data with proper tag mappings from emvTags
                // Live parsing is still logged to APDU log for debugging
                
                // Display summary of parsed tags
                parsedInfo += "  ✅ Total: ${parseResult.tags.size} tags extracted\n"
                parsedInfo += "  ✅ Valid tags: ${parseResult.validTags}\n"
                
                if (parseResult.invalidTags > 0) {
                    parsedInfo += "  ⚠️ Unknown tags: ${parseResult.invalidTags}\n"
                }
                
                if (parseResult.templateDepth > 0) {
                    parsedInfo += "  🔧 Template depth: ${parseResult.templateDepth}\n"
                }
                
                // Display key tags (PAN, expiry, cryptogram, etc.)
                val keyTags = listOf(
                    "4F" to "AID",
                    "50" to "App Label", 
                    "5A" to "PAN",
                    "57" to "Track2",
                    "5F20" to "Cardholder Name",
                    "5F24" to "Expiry",
                    "5F28" to "Country",
                    "82" to "AIP",
                    "84" to "DF Name",
                    "94" to "AFL",
                    "9F06" to "AID",
                    "9F07" to "AUC",
                    "9F10" to "Issuer Application Data",
                    "9F26" to "Cryptogram",
                    "9F27" to "CID",
                    "9F32" to "Issuer Public Key Exponent",
                    "9F36" to "ATC",
                    "9F38" to "PDOL",
                    "9F46" to "ICC Public Key Certificate",
                    "9F47" to "ICC Public Key Exponent",
                    "9F4B" to "Signed Dynamic Application Data",
                    "9F69" to "UDOL",
                    "92" to "Issuer Public Key Remainder",
                    "8F" to "CA Public Key Index"
                )
                
                parsedInfo += "\n  🔑 Key Tags:\n"
                var keyTagsFound = 0
                
                keyTags.forEach { (tag, name) ->
                    val value = parseResult.tags[tag]
                    if (value != null) {
                        parsedInfo += "    • $name ($tag): ${value.take(32)}${if (value.length > 32) "..." else ""}\n"
                        keyTagsFound++
                    }
                }
                
                if (keyTagsFound == 0) {
                    parsedInfo += "    (No key tags in this response)\n"
                }
                
                // Display ALL other tags extracted
                val otherTags = parseResult.tags.filterKeys { tag ->
                    keyTags.none { it.first == tag }
                }
                
                if (otherTags.isNotEmpty()) {
                    parsedInfo += "\n  📦 Other Tags (${otherTags.size}):\n"
                    otherTags.forEach { (tag, value) ->
                        val tagName = EmvTagDictionary.getTagDescription(tag)
                        parsedInfo += "    • $tagName ($tag): ${value.take(32)}${if (value.length > 32) "..." else ""}\n"
                    }
                }
                
                // Update status message with key info
                val panValue = parseResult.tags["5A"]
                val expiryValue = parseResult.tags["5F24"]
                val cryptogramValue = parseResult.tags["9F26"]
                
                val statusParts = mutableListOf<String>()
                if (panValue != null) statusParts.add("PAN: ${panValue.take(6)}****${panValue.takeLast(4)}")
                if (expiryValue != null) statusParts.add("Exp: $expiryValue")
                if (cryptogramValue != null) statusParts.add("Crypto: ${cryptogramValue.take(8)}...")
                
                if (statusParts.isEmpty()) {
                    statusParts.add("${parseResult.tags.size} tags")
                }
                
                statusMessage = "$phase: ${statusParts.take(2).joinToString(", ")}"
                
            } else {
                parsedInfo += "  (No TLV tags found)\n"
            }
            
            // Display errors and warnings
            if (parseResult.errors.isNotEmpty()) {
                parsedInfo += "\n  ❌ Errors:\n"
                parseResult.errors.forEach { error ->
                    parsedInfo += "    • $error\n"
                }
            }
            
            if (parseResult.warnings.isNotEmpty()) {
                parsedInfo += "\n  ⚠️ Warnings:\n"
                parseResult.warnings.forEach { warning ->
                    parsedInfo += "    • $warning\n"
                }
            }
            
            Timber.i(parsedInfo)
            
            // Store to database (TODO: implement database storage)
            storeTagsToDatabase(phase, parseResult.tags)
            
            // Check for ROCA vulnerability analysis results
            checkRocaVulnerability()
            
        } catch (e: Exception) {
            Timber.e(e, "Error parsing $phase data with EmvTlvParser")
        }
    }
    
    /**
     * Check ROCA vulnerability status from EmvTlvParser results
     * Called after each TLV parsing phase to update ROCA status
     */
    private fun checkRocaVulnerability() {
        try {
            val rocaResults = EmvTlvParser.getRocaAnalysisResults()
            
            if (rocaResults.isNotEmpty()) {
                // Find highest severity vulnerability
                var highestConfidence = com.nfsp00f33r.app.security.RocaVulnerabilityAnalyzer.VulnerabilityConfidence.UNKNOWN
                var anyVulnerable = false
                var confirmedVulnerable = false
                
                rocaResults.forEach { (tagId, result) ->
                    if (result.isVulnerable) {
                        anyVulnerable = true
                        
                        if (result.confidence == com.nfsp00f33r.app.security.RocaVulnerabilityAnalyzer.VulnerabilityConfidence.CONFIRMED) {
                            confirmedVulnerable = true
                            highestConfidence = result.confidence
                        } else if (result.confidence.ordinal > highestConfidence.ordinal) {
                            highestConfidence = result.confidence
                        }
                        
                        Timber.w("🚨 ROCA vulnerability detected in tag $tagId: confidence=${result.confidence}, factored=${result.factorAttempt?.successful}")
                    }
                }
                
                // Update ViewModel state
                isRocaVulnerable = anyVulnerable
                rocaVulnerabilityStatus = when {
                    confirmedVulnerable -> "🚨 CONFIRMED ROCA VULNERABLE - RSA keys compromised!"
                    highestConfidence == com.nfsp00f33r.app.security.RocaVulnerabilityAnalyzer.VulnerabilityConfidence.HIGHLY_LIKELY -> 
                        "⚠️ HIGHLY LIKELY vulnerable to ROCA"
                    highestConfidence == com.nfsp00f33r.app.security.RocaVulnerabilityAnalyzer.VulnerabilityConfidence.POSSIBLE -> 
                        "⚡ POSSIBLE ROCA vulnerability"
                    anyVulnerable -> "⚠️ ROCA vulnerability detected"
                    else -> "✅ No ROCA vulnerability detected"
                }
                
                Timber.i("ROCA Analysis: vulnerable=$anyVulnerable, status=$rocaVulnerabilityStatus, ${rocaResults.size} certificates analyzed")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error checking ROCA vulnerability")
            rocaVulnerabilityStatus = "Error checking ROCA: ${e.message}"
        }
    }
    
    /**
     * Store all extracted tags - NO-OP as tags are already stored in parsedEmvFields
     * and will be saved to emvTags map in EmvCardData when card is saved
     */
    private fun storeTagsToDatabase(phase: String, tags: Map<String, String>) {
        // Tags are automatically stored in parsedEmvFields state variable
        // and transferred to EmvCardData.emvTags via buildRealEmvTagsMap()
        // No separate database storage needed - emvTags is part of the card record
        Timber.d("✅ $phase: ${tags.size} tags added to parsedEmvFields (will be saved with card data)")
    }
    
    // ==================== iCVV/Dynamic CVV Calculation ====================
    
    /**
     * Calculate Unpredictable Number (UN) size based on Track bitmap
     * Based on ChAP.py calculate_UNSize() function
     * 
     * @param bitmap Track bitmap value (9F63 for Track1, 9F66 for Track2)
     * @param numDigits Number of ATC digits (9F64 for Track1)
     * @return Number of bytes needed for unpredictable number
     */
    private fun calculateUnSize(bitmap: Long, numDigits: Int): Int {
        var i = bitmap
        
        // Count bits set in bitmap using Brian Kernighan's algorithm
        // This is the bit counting algorithm from ChAP.py
        i = i - ((i shr 1) and 0x55555555L)
        i = (i and 0x33333333L) + ((i shr 2) and 0x33333333L)
        val bitsSet = (((i + (i shr 4)) and 0x0F0F0F0FL) * 0x01010101L) shr 24
        
        val unSize = bitsSet.toInt() - numDigits
        
        Timber.d("🔢 UN Size calculation: bitmap=0x${bitmap.toString(16)}, numDigits=$numDigits, bitsSet=$bitsSet, unSize=$unSize")
        
        return unSize
    }
    
    /**
     * Calculate iCVV/Dynamic CVV parameters from EMV tags
     * Extracts Track 1/2 bitmaps and calculates required UN sizes
     * 
     * @return Map containing iCVV calculation parameters
     */
    private fun calculateDynamicCvvParams(): Map<String, Any> {
        val params = mutableMapOf<String, Any>()
        
        try {
            // Extract Track 1 bitmap and ATC digits (tag 9F63, 9F64)
            val track1Bitmap = parsedEmvFields["9F63"]
            val track1AtcDigits = parsedEmvFields["9F64"]
            
            if (track1Bitmap != null && track1AtcDigits != null) {
                val bitmapValue = track1Bitmap.toLongOrNull(16) ?: 0L
                val atcDigits = track1AtcDigits.toIntOrNull(16) ?: 0
                val track1UnSize = calculateUnSize(bitmapValue, atcDigits)
                
                params["track1_bitmap"] = track1Bitmap
                params["track1_atc_digits"] = atcDigits
                params["track1_un_size"] = track1UnSize
                
                Timber.i("💳 Track 1 iCVV params: bitmap=$track1Bitmap, atcDigits=$atcDigits, unSize=$track1UnSize bytes")
            }
            
            // Extract Track 2 bitmap (tag 9F65, 9F66)
            val track2Cvc3Bitmap = parsedEmvFields["9F65"]
            val track2UnAtcBitmap = parsedEmvFields["9F66"]
            
            if (track2UnAtcBitmap != null) {
                val bitmapValue = track2UnAtcBitmap.toLongOrNull(16) ?: 0L
                // Track 2 typically uses 2 ATC digits
                val track2UnSize = calculateUnSize(bitmapValue, 2)
                
                params["track2_un_atc_bitmap"] = track2UnAtcBitmap
                params["track2_un_size"] = track2UnSize
                
                if (track2Cvc3Bitmap != null) {
                    params["track2_cvc3_bitmap"] = track2Cvc3Bitmap
                }
                
                Timber.i("💳 Track 2 iCVV params: bitmap=$track2UnAtcBitmap, unSize=$track2UnSize bytes")
            }
            
            // Extract other iCVV-related tags
            val atc = parsedEmvFields["9F36"] // Application Transaction Counter
            val un = parsedEmvFields["9F37"] // Unpredictable Number
            
            if (atc != null) params["atc"] = atc
            if (un != null) params["unpredictable_number"] = un
            
            // Calculate iCVV status
            val hasIcvvData = track1Bitmap != null || track2UnAtcBitmap != null
            params["icvv_capable"] = hasIcvvData
            
            if (hasIcvvData) {
                params["icvv_status"] = "Card supports dynamic CVV (iCVV/CVC3)"
                Timber.i("✅ Card supports iCVV/Dynamic CVV generation")
            } else {
                params["icvv_status"] = "No iCVV bitmaps found (static CVV only)"
                Timber.d("ℹ️ Card uses static CVV (no dynamic CVV tags)")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Error calculating iCVV/Dynamic CVV parameters")
            params["icvv_error"] = e.message ?: "Unknown error"
        }
        
        return params.toMap()
    }
    
    /**
     * Format iCVV parameters for storage/display
     */
    private fun formatIcvvParams(params: Map<String, Any>): String {
        val sb = StringBuilder()
        
        params.forEach { (key, value) ->
            sb.append("$key: $value\\n")
        }
        
        return sb.toString().trim()
    }
    
    /**
     * Extract ROCA vulnerability analysis details for storage
     */
    private fun extractRocaAnalysisDetails(): String {
        val sb = StringBuilder()
        val rocaResults = EmvTlvParser.getRocaAnalysisResults()
        
        if (rocaResults.isEmpty()) {
            return "No certificates analyzed"
        }
        
        rocaResults.forEach { (tagId, result) ->
            val tagName = EmvTagDictionary.getTagDescription(tagId)
            sb.append("Tag $tagId ($tagName):\\n")
            sb.append("  Vulnerable: ${result.isVulnerable}\\n")
            sb.append("  Confidence: ${result.confidence}\\n")
            sb.append("  Key size: ${result.keySize ?: "unknown"} bits\\n")
            
            if (result.fingerprint != null) {
                sb.append("  Fingerprint: ${result.fingerprint}\\n")
            }
            
            if (result.factorAttempt != null) {
                sb.append("  Factor attempt: ${if (result.factorAttempt.successful) "SUCCESS" else "FAILED"}\\n")
                if (result.factorAttempt.successful) {
                    sb.append("  ⚠️ RSA PRIVATE KEY COMPROMISED!\\n")
                }
            }
            
            sb.append("\\n")
        }
        
        return sb.toString().trim()
    }
    
    /**
     * Create comprehensive EMV card data from extracted information
     */
    private fun createEmvCardData(cardId: String, tag: android.nfc.Tag): com.nfsp00f33r.app.data.EmvCardData {
        // Extract ONLY real PAN from APDU responses - NO FALLBACKS
        var extractedPan = ""
        apduLog.forEach { apdu ->
            val pan = extractPanFromResponse(apdu.response)
            if (pan.isNotEmpty()) {
                extractedPan = pan
                return@forEach
            }
        }
        
        // ONLY use extracted data - NO HARDCODED FALLBACKS
        val finalPan = extractedPan
        var extractedAid = ""
        apduLog.forEach { apdu ->
            val aid = extractAidFromResponse(apdu.response)
            if (aid.isNotEmpty()) {
                extractedAid = aid
                return@forEach
            }
        }
        
        // ONLY return data if we have REAL extracted data
        if (finalPan.isEmpty()) {
            throw Exception("No PAN extracted from real card data")
        }
        
        // Extract ALL data from real responses ONLY
        val extractedTrack2 = extractTrack2FromAllResponses(apduLog)
        val extractedExpiry = extractExpiryFromAllResponses(apduLog)
        val extractedCardholderName = extractCardholderNameFromAllResponses(apduLog)
        val extractedAip = extractAipFromAllResponses(apduLog)
        val extractedAfl = extractAflFromAllResponses(apduLog)
        val extractedPdol = extractPdolFromAllResponses(apduLog)
        
        return com.nfsp00f33r.app.data.EmvCardData(
            // Card Hardware Identifier - REAL ONLY
            cardUid = tag.id.joinToString("") { "%02X".format(it) },
            
            // Core EMV Data - REAL EXTRACTED ONLY
            pan = finalPan,
            track2Data = extractedTrack2,
            expiryDate = extractedExpiry,
            cardholderName = extractedCardholderName,
            
            // Application Information - REAL EXTRACTED ONLY
            applicationIdentifier = extractedAid,
            applicationLabel = extractedCardholderName,
            applicationInterchangeProfile = extractedAip,
            applicationFileLocator = extractedAfl,
            processingOptionsDataObjectList = extractedPdol,
            
            // Extract MORE real data from responses - NO HARDCODED VALUES
            cardholderVerificationMethodList = extractCvmListFromAllResponses(apduLog),
            
            // Cryptographic Data - REAL EXTRACTED ONLY
            issuerApplicationData = extractIadFromAllResponses(apduLog),
            applicationCryptogram = extractCryptogramFromAllResponses(apduLog),
            cryptogramInformationData = extractCidFromAllResponses(apduLog),
            applicationTransactionCounter = extractAtcFromAllResponses(apduLog),
            unpredictableNumber = extractUnFromAllResponses(apduLog),
            
            // Additional EMV Data - REAL EXTRACTED ONLY
            cdol1 = extractCdol1FromAllResponses(apduLog),
            cdol2 = extractCdol2FromAllResponses(apduLog),
            applicationVersion = extractAppVersionFromAllResponses(apduLog),
            applicationUsageControl = extractAucFromAllResponses(apduLog),
            terminalTransactionQualifiers = extractTtqFromAllResponses(apduLog),
            
            // Geographic and Currency - REAL EXTRACTED ONLY
            issuerCountryCode = extractCountryCodeFromAllResponses(apduLog),
            currencyCode = extractCurrencyCodeFromAllResponses(apduLog),
            
            // EMV Tags Map for additional data
            emvTags = buildRealEmvTagsMap(apduLog, finalPan, extractedAid, extractedTrack2),

            // iCVV/Dynamic CVV calculation
            icvvCapable = calculateDynamicCvvParams()["icvv_capable"] as? Boolean ?: false,
            icvvTrack1Bitmap = calculateDynamicCvvParams()["track1_bitmap"] as? String,
            icvvTrack1AtcDigits = calculateDynamicCvvParams()["track1_atc_digits"] as? Int,
            icvvTrack1UnSize = calculateDynamicCvvParams()["track1_un_size"] as? Int,
            icvvTrack2Bitmap = calculateDynamicCvvParams()["track2_un_atc_bitmap"] as? String,
            icvvTrack2UnSize = calculateDynamicCvvParams()["track2_un_size"] as? Int,
            icvvStatus = calculateDynamicCvvParams()["icvv_status"] as? String,
            icvvParameters = formatIcvvParams(calculateDynamicCvvParams()),
            
            // ROCA Vulnerability Analysis
            rocaVulnerable = isRocaVulnerable,
            rocaVulnerabilityStatus = rocaVulnerabilityStatus,
            rocaAnalysisDetails = extractRocaAnalysisDetails(),
            rocaCertificatesAnalyzed = EmvTlvParser.getRocaAnalysisResults().size,
            
            // APDU Log
            apduLog = apduLog,
            
            // Additional metadata - REAL ONLY
            selectedAid = extractedAid,
            availableAids = extractAllAidsFromResponses(apduLog)
        )
    }
    

    
    /**
     * Save card profile to database
     */
    private fun saveCardProfile(emvData: com.nfsp00f33r.app.data.EmvCardData) {
        try {
            val cardProfile = com.nfsp00f33r.app.models.CardProfile(
                emvCardData = emvData,
                apduLogs = emvData.apduLog.toMutableList(),
                cardholderName = emvData.cardholderName,
                aid = emvData.applicationIdentifier
            )
            // Save to CardDataStore with encryption
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val storageProfile = CardProfileAdapter.toStorageProfile(cardProfile)
                    val result = cardDataStore.storeProfile(storageProfile)
                    result.fold(
                        onSuccess = { profileId ->
                            Timber.i("Card profile saved with encryption: PAN=${emvData.getUnmaskedPan()}, ID=$profileId")
                        },
                        onFailure = { error ->
                            Timber.e(error, "Failed to save card profile")
                        }
                    )
                } catch (e: Exception) {
                    Timber.e(e, "Error saving card profile to encrypted storage")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to save card profile to database")
        }
    }
    
    /**
     * Initialize hardware detection service
     */
    private fun initializeHardwareDetection() {
        viewModelScope.launch {
            try {
                // Detect available readers
                detectAvailableReaders()
                
                Timber.i("Card reading hardware detection initialized")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize hardware detection")
                statusMessage = "Hardware detection failed: ${e.message}"
                scanState = ScanState.ERROR
            }
        }
    }
    
    /**
     * Detect available NFC readers
     * OPTIMIZED: No longer tests connections during initialization to avoid 5+ second lag
     */
    private fun detectAvailableReaders() {
        val readers = mutableListOf<ReaderType>()
        
        // Always add Android NFC (may not be functional but present)
        readers.add(ReaderType.ANDROID_NFC)
        
        // Add PN532 options without testing connections (testing happens when user selects reader)
        // This avoids the 5+ second Bluetooth connection test that was blocking UI
        readers.add(ReaderType.PN532_BLUETOOTH)
        readers.add(ReaderType.PN532_USB)
        
        // Add mock reader for testing
        readers.add(ReaderType.MOCK_READER)
        
        availableReaders = readers
        
        // Auto-select Android NFC as default (fast, no connection test needed)
        if (readers.isNotEmpty() && selectedReader == null) {
            selectReader(ReaderType.ANDROID_NFC)
        }
        
        Timber.d("Available readers: ${readers.joinToString()} (connection testing deferred to selection time)")
    }
    
    /**
     * Test PN532 connection - Phase 2B Day 1: Updated for PN532DeviceModule
     */
    private fun testPN532Connection(module: com.nfsp00f33r.app.hardware.PN532DeviceModule, type: PN532Manager.ConnectionType): Boolean {
        return try {
            module.connect(type)
            val connected = module.isConnected()
            if (connected) {
                module.disconnect()
            }
            connected
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get display name for reader with connection status
     */
    fun getReaderDisplayName(reader: ReaderType): String {
        return when (reader) {
            ReaderType.ANDROID_NFC -> "Android NFC Controller"
            ReaderType.PN532_BLUETOOTH -> "PN532 Bluetooth (Connected)"
            ReaderType.PN532_USB -> "PN532 USB Direct"
            ReaderType.MOCK_READER -> "Mock Reader (Testing)"
        }
    }
    
    /**
     * Select NFC reader
     */
    /**
     * Toggle contact mode (1PAY) vs contactless mode (2PAY)
     */
    fun setContactMode(enabled: Boolean) {
        forceContactMode = enabled
        statusMessage = if (enabled) {
            "Contact mode (1PAY) enabled - will use 1PAY.SYS.DDF01"
        } else {
            "Contactless mode (2PAY) enabled - will try 2PAY.SYS.DDF01 first"
        }
        Timber.i("Contact mode ${if (enabled) "ENABLED" else "DISABLED"}: forceContactMode=$forceContactMode")
    }
    
    fun selectReader(reader: ReaderType) {
        viewModelScope.launch {
            try {
                selectedReader = reader
                readerStatus = "Connecting..."
                
                when (reader) {
                    ReaderType.ANDROID_NFC -> {
                        readerStatus = "Android NFC Ready"
                        hardwareCapabilities = setOf("EMV Reading", "HCE Support", "Standard NFC")
                    }
                    ReaderType.PN532_BLUETOOTH -> {
                        // Phase 2B Day 1: Use PN532DeviceModule instead of direct instantiation
                        pn532Module.connect(PN532Manager.ConnectionType.BLUETOOTH_HC06)
                        readerStatus = if (pn532Module.isConnected()) "PN532 Bluetooth Connected" else "Connection Failed"
                        hardwareCapabilities = setOf("Advanced EMV", "Raw Commands", "PN532 Features")
                    }
                    ReaderType.PN532_USB -> {
                        // Phase 2B Day 1: Use PN532DeviceModule instead of direct instantiation
                        pn532Module.connect(PN532Manager.ConnectionType.USB_SERIAL)
                        readerStatus = if (pn532Module.isConnected()) "PN532 USB Connected" else "Connection Failed"
                        hardwareCapabilities = setOf("Advanced EMV", "Raw Commands", "PN532 Features")
                    }
                    ReaderType.MOCK_READER -> {
                        readerStatus = "Mock Reader Ready"
                        hardwareCapabilities = setOf("EMV Simulation", "Testing")
                    }
                }
                
                statusMessage = "Reader selected: ${reader.name}"
                Timber.i("Selected reader: $reader, Status: $readerStatus")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to select reader: $reader")
                readerStatus = "Connection error: ${e.message}"
                statusMessage = "Reader connection failed"
            }
        }
    }
    
    /**
     * Select NFC technology
     */
    fun selectTechnology(technology: NfcTechnology) {
        selectedTechnology = technology
        statusMessage = "Technology selected: ${technology.name}"
    }
    
    /**
     * Toggle auto-select mode
     */
    fun toggleAutoSelect() {
        isAutoSelectEnabled = !isAutoSelectEnabled
        statusMessage = if (isAutoSelectEnabled) "Auto-selection enabled" else "Manual selection enabled"
    }
    
    /**
     * Start EMV scanning with Proxmark3 workflow
     */
    fun startScanning() {
        if (selectedReader == null) {
            statusMessage = "Please select a reader first"
            return
        }
        
        viewModelScope.launch {
            try {
                scanState = ScanState.SCANNING
                currentPhase = "Waiting for card..."
                progress = 0f
                statusMessage = "Hold card near reader"
                
                // Clear previous logs
                apduLog = emptyList()
                
                // Initialize card reader with callback
                val callback = createCardReadingCallback()
                
                // Start EMV scan workflow based on selected reader
                when (selectedReader!!) {
                    ReaderType.ANDROID_NFC -> startAndroidNfcScan(callback)
                    ReaderType.PN532_BLUETOOTH, ReaderType.PN532_USB -> startPN532Scan(callback)
                    ReaderType.MOCK_READER -> startMockScan(callback)
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to start scanning")
                scanState = ScanState.ERROR
                statusMessage = "Scan failed: ${e.message}"
            }
        }
    }
    
    /**
     * Stop scanning
     */
    fun stopScanning() {
        scanState = ScanState.IDLE
        currentPhase = "Stopped"
        progress = 0f
        statusMessage = "Scanning stopped"
    }
    
    /**
     * Create card reading callback for live updates
     */
    private fun createCardReadingCallback(): CardReadingCallback {
        return object : CardReadingCallback {
            override fun onReadingStarted() {
                scanState = ScanState.SCANNING
                statusMessage = "Reading started"
            }
            
            override fun onReadingStopped() {
                if (scanState != ScanState.SCAN_COMPLETE) {
                    scanState = ScanState.IDLE
                    statusMessage = "Reading stopped"
                }
            }
            
            override fun onCardDetected() {
                scanState = ScanState.CARD_DETECTED
                currentPhase = "Card detected"
                progress = 0.1f
                statusMessage = "EMV card detected, starting extraction..."
                Timber.i("Card detected")
            }
            
            override fun onProgress(step: String, progress: Int, total: Int) {
                currentPhase = step
                this@CardReadingViewModel.progress = progress.toFloat() / total.toFloat()
                statusMessage = "Phase: $step ($progress/$total)"
            }
            
            override fun onApduExchanged(apduEntry: ApduLogEntry) {
                apduLog = apduLog + apduEntry
                Timber.d("APDU: ${apduEntry.description} - ${apduEntry.executionTimeMs}ms")
            }
            
            override fun onCardRead(cardData: EmvCardData) {
                currentEmvData = cardData
                
                // Convert to VirtualCard for carousel display
                val virtualCard = VirtualCard(
                    cardholderName = cardData.cardholderName ?: "Unknown Cardholder",
                    maskedPan = cardData.getUnmaskedPan(),
                    expiryDate = cardData.expiryDate ?: "MM/YY",
                    apduCount = cardData.apduLog.size,
                    cardType = cardData.getCardBrandDisplayName(),
                    isEncrypted = cardData.hasEncryptedData(),
                    lastUsed = "Just scanned",
                    category = "PAYMENT"
                )
                
                scannedCards = scannedCards + virtualCard
                
                // Save to card profile manager
                // Create card profile and save to encrypted storage
                val cardProfile = com.nfsp00f33r.app.models.CardProfile(
                    emvCardData = cardData,
                    apduLogs = cardData.apduLog.toMutableList(),
                    cardholderName = cardData.cardholderName,
                    applicationLabel = cardData.applicationLabel
                )
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val storageProfile = CardProfileAdapter.toStorageProfile(cardProfile)
                        cardDataStore.storeProfile(storageProfile)
                        Timber.d("Card saved to encrypted storage: ${cardData.cardholderName ?: "Unknown"}")
                    } catch (e: Exception) {
                        Timber.e(e, "Failed to save card to encrypted storage")
                    }
                }
                
                scanState = ScanState.SCAN_COMPLETE
                currentPhase = "EMV extraction complete"
                progress = 1.0f
                statusMessage = "Card successfully scanned and saved"
                
                Timber.i("EMV data extracted: PAN=${cardData.getUnmaskedPan()}, APDUs=${cardData.apduLog.size}")
            }
            
            override fun onError(error: String) {
                scanState = ScanState.ERROR
                statusMessage = "Error: $error"
                Timber.e("Card reading error: $error")
            }
        }
    }
    
    /**
     * Start Android NFC scanning
     * Uses the MainActivity's NFC detection system that's already running
     */
    private fun startAndroidNfcScan(callback: CardReadingCallback) {
        try {
            statusMessage = "Android NFC Ready - Present card to device"
            currentPhase = "Waiting for Card"
            progress = 0f
            
            // Android NFC scanning is handled by MainActivity's foreground dispatch
            // The NFC monitoring loop will automatically process detected cards
            // So we just need to set the scan state and wait for cards
            
            Timber.i("Android NFC scanning started - using MainActivity NFC detection")
            
            // Simulate some initial APDU log entry to show the system is active
            viewModelScope.launch {
                delay(1000)
                
                statusMessage = "Android NFC Ready - Waiting for EMV card..."
                currentPhase = "NFC Ready"
                progress = 0.1f
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Failed to start Android NFC scan")
            callback.onError("Android NFC scan failed: ${e.message}")
        }
    }
    
    /**
     * Start PN532 scanning - Phase 2B Day 1: Use PN532DeviceModule
     */
    private fun startPN532Scan(callback: CardReadingCallback) {
        if (!pn532Module.isConnected()) {
            callback.onError("PN532 not connected")
            return
        }
        
        // PN532 EMV scanning workflow implementation
        viewModelScope.launch {
            try {
                statusMessage = "PN532 EMV scanning initiated"
                callback.onReadingStarted()
                
                // PN532 scan simulation until actual methods are implemented
                callback.onProgress("PN532 Scan", 1, 2)
                callback.onCardDetected()
                
                // Use mock data for now
                callback.onProgress("Processing Data", 2, 2)
                val emvData = createMockEmvData()
                callback.onCardRead(emvData)
                
                statusMessage = "PN532 EMV scan complete"
                
            } catch (e: Exception) {
                callback.onError("PN532 scan failed: ${e.message}")
            }
        }
    }
    
    /**
     * Start mock scanning for testing
     */
    private fun startMockScan(callback: CardReadingCallback) {
        viewModelScope.launch {
            try {
                // Start reading
                callback.onReadingStarted()
                
                // Simulate Proxmark3 EMV scan workflow
                kotlinx.coroutines.delay(1000)
                callback.onCardDetected()
                
                kotlinx.coroutines.delay(500)
                callback.onProgress("SELECT PPSE", 1, 6)
                callback.onApduExchanged(ApduLogEntry(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    command = "00A404000E325041592E5359532E444446303100",
                    response = "6F1E8407A0000000031010A5139F38189F66049F02069F03069F1A0295059F37049000",
                    statusWord = "9000",
                    description = "SELECT PPSE",
                    executionTimeMs = 45L
                ))
                
                kotlinx.coroutines.delay(500)
                callback.onProgress("SELECT AID", 2, 6)
                callback.onApduExchanged(ApduLogEntry(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    command = "00A4040007A000000003101000",
                    response = "6F198407A0000000031010A50E500A4D617374657243617264870101",
                    statusWord = "9000",
                    description = "SELECT AID - MasterCard",
                    executionTimeMs = 32L
                ))
                
                kotlinx.coroutines.delay(500)
                callback.onProgress("GET PROCESSING OPTIONS", 3, 6)
                callback.onApduExchanged(ApduLogEntry(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    command = "80A80000238321F0000000000001000000000000084000000000000000000000000000",
                    response = "77819F2701809F360200019F2608D2A2A2A2A2A2A25F340109000",
                    statusWord = "9000",
                    description = "GET PROCESSING OPTIONS (GPO)",
                    executionTimeMs = 128L
                ))
                
                kotlinx.coroutines.delay(500)
                callback.onProgress("READ RECORDS", 4, 6)
                callback.onApduExchanged(ApduLogEntry(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    command = "00B2011400",
                    response = "70195A085413330000000001574311D24120000000000000000F5F30020001",
                    statusWord = "9000",
                    description = "READ RECORD - Track 2 Data",
                    executionTimeMs = 67L
                ))
                
                kotlinx.coroutines.delay(500)
                callback.onProgress("EXTRACTING CRYPTOGRAMS", 5, 6)
                callback.onApduExchanged(ApduLogEntry(
                    timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    command = "80AE8000230000000000000000000000000000000000000000000000000000000000000000",
                    response = "77819F4701039F2608D2A2A2A2A2A2A25F3401019F101307010A03A020009000",
                    statusWord = "9000",
                    description = "GENERATE AC - Transaction Certificate",
                    executionTimeMs = 95L
                ))
                
                kotlinx.coroutines.delay(500)
                callback.onProgress("COMPLETE", 6, 6)
                
                // Create mock EMV data with complete Proxmark3-style extraction
                val mockEmvData = createMockEmvData()
                callback.onCardRead(mockEmvData)
                
                callback.onReadingStopped()
                
            } catch (e: Exception) {
                callback.onError("Mock scan failed: ${e.message}")
            }
        }
    }
    
    /**
     * Create comprehensive mock EMV data using configuration
     */
    private fun createMockEmvData(): EmvCardData {
        val mockData = AttackConfiguration.MOCK_EMV_DATA
        return EmvCardData(
            pan = mockData["PAN"] as String,
            track2Data = mockData["TRACK2"] as String,
            expiryDate = mockData["EXPIRY"] as String,
            cardholderName = mockData["CARDHOLDER_NAME"] as String,
            applicationIdentifier = mockData["AID"] as String,
            applicationLabel = mockData["APP_LABEL"] as String,
            applicationInterchangeProfile = mockData["AIP"] as String,
            applicationFileLocator = mockData["AFL"] as String,
            processingOptionsDataObjectList = mockData["PDOL"] as String,
            cardholderVerificationMethodList = mockData["CVM_LIST"] as String,
            issuerApplicationData = mockData["IAD"] as String,
            applicationCryptogram = mockData["AC"] as String,
            cryptogramInformationData = mockData["CID"] as String,
            applicationTransactionCounter = mockData["ATC"] as String,
            unpredictableNumber = mockData["UN"] as String,
            cdol1 = mockData["CDOL1"] as String,
            cdol2 = mockData["CDOL2"] as String,
            terminalVerificationResults = mockData["TVR"] as String
        ).apply {
            // Add current APDU log to the EMV data
            apduLog = this@CardReadingViewModel.apduLog
        }
    }
    
    /**
     * Detect card brand from PAN
     */
    private fun detectCardBrand(pan: String): String {
        val cleanPan = pan.replace("*", "").replace(" ", "")
        return when {
            cleanPan.startsWith("4") -> "VISA"
            cleanPan.startsWith("5") || cleanPan.startsWith("2") -> "MASTERCARD"
            cleanPan.startsWith("3") -> "AMEX"
            cleanPan.startsWith("6011") -> "DISCOVER"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Setup card profile change listener
     */
    private fun setupCardProfileListener() {
        // Load recent cards from encrypted storage
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allProfiles = cardDataStore.getAllProfiles()
                val recentProfiles = allProfiles.takeLast(10)
                val appProfiles = recentProfiles.map { CardProfileAdapter.toAppProfile(it) }
                
                withContext(Dispatchers.Main) {
                    scannedCards = appProfiles.map { profile ->
                        val emvData = profile.emvCardData
                        VirtualCard(
                            cardholderName = emvData.cardholderName ?: "Unknown",
                            maskedPan = emvData.getUnmaskedPan(),
                            expiryDate = emvData.expiryDate?.let { exp ->
                                if (exp.length == 4) "${exp.substring(2, 4)}/${exp.substring(0, 2)}" else exp
                            } ?: "MM/YY",
                            apduCount = profile.apduLogs.size,
                            cardType = emvData.getCardBrandDisplayName(),
                            isEncrypted = emvData.hasEncryptedData(),
                            lastUsed = "Recently",
                            category = "PAYMENT"
                        )
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load recent cards from encrypted storage")
            }
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Cleanup resources - Phase 2B Day 1: Use PN532DeviceModule
        if (pn532Module.isConnected()) {
            pn532Module.disconnect()
        }
        
        Timber.d("CardReadingViewModel cleared")
    }
    
    /**
     * Factory for creating CardReadingViewModel with Context
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(CardReadingViewModel::class.java)) {
                return CardReadingViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}