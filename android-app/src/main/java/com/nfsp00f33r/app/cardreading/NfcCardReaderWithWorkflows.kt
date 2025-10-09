package com.nfsp00f33r.app.cardreading

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import com.nfsp00f33r.app.data.EmvCardData
import com.nfsp00f33r.app.data.EmvWorkflow
import com.nfsp00f33r.app.data.ApduLogEntry
import kotlinx.coroutines.*
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * ENHANCED NFC EMV Card Reader with Workflow Support and TTQ Manipulation
 * Supports multiple EMV workflows for comprehensive data extraction
 * Based on RFIDIOt methodology with advanced TTQ manipulation
 */
class NfcCardReaderWithWorkflows(
    private val activity: Activity,
    private val callback: CardReadingCallback
) : NfcAdapter.ReaderCallback {
    
    companion object {
        private const val TAG = "🏴‍☠️ NfcCardReaderWorkflows"
        
        // EMV Command APDUs
        private val SELECT_PPSE = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
            0x00
        )
        
        /**
         * RFIDIOt EMV Tag Dictionary - Complete tag definitions from ChAP.py
         */
        private val EMV_TAGS = mapOf(
            "4F" to "Application Identifier (AID)",
            "50" to "Application Label", 
            "57" to "Track 2 Equivalent Data",
            "5A" to "Application Primary Account Number (PAN)",
            "5F20" to "Cardholder Name",
            "5F24" to "Application Expiry Date",
            "5F25" to "Application Effective Date",
            "5F28" to "Issuer Country Code",
            "5F2A" to "Transaction Currency Code",
            "5F2D" to "Language Preference",
            "5F30" to "Service Code",
            "5F34" to "Application Primary Account Number (PAN) Sequence Number",
            "5F36" to "Transaction Currency Exponent",
            "61" to "Application Template",
            "6F" to "File Control Information (FCI) Template",
            "70" to "EMV Proprietary Template",
            "71" to "Issuer Script Template 1",
            "72" to "Issuer Script Template 2",
            "73" to "Directory Discretionary Template",
            "77" to "Response Message Template Format 2",
            "80" to "Response Message Template Format 1",
            "82" to "Application Interchange Profile",
            "83" to "Command Template",
            "84" to "Dedicated File (DF) Name",
            "87" to "Application Priority Indicator",
            "88" to "Short File Identifier (SFI)",
            "8A" to "Authorization Response Code",
            "8C" to "Card Risk Management Data Object List 1 (CDOL1)",
            "8D" to "Card Risk Management Data Object List 2 (CDOL2)",
            "8E" to "Cardholder Verification Method (CVM) List",
            "8F" to "Certification Authority Public Key Index",
            "90" to "Issuer Public Key Certificate",
            "91" to "Issuer Authentication Data",
            "92" to "Issuer Public Key Remainder",
            "93" to "Signed Static Application Data",
            "94" to "Application File Locator (AFL)",
            "95" to "Terminal Verification Results",
            "97" to "Transaction Certificate Data Object List (TDOL)",
            "98" to "Transaction Certificate (TC) Hash Value",
            "99" to "Transaction Personal Identification Number (PIN) Data",
            "9A" to "Transaction Date",
            "9B" to "Transaction Status Information",
            "9C" to "Transaction Type",
            "9D" to "Directory Definition File (DDF) Name",
            "9F01" to "Acquirer Identifier",
            "9F02" to "Amount, Authorized (Numeric)",
            "9F03" to "Amount, Other (Numeric)",
            "9F04" to "Amount, Other (Binary)",
            "9F05" to "Application Discretionary Data",
            "9F06" to "Application Identifier (AID) - terminal",
            "9F07" to "Application Usage Control",
            "9F08" to "Application Version Number",
            "9F09" to "Application Version Number",
            "9F0B" to "Cardholder Name Extended",
            "9F0D" to "Issuer Action Code - Default",
            "9F0E" to "Issuer Action Code - Denial",
            "9F0F" to "Issuer Action Code - Online",
            "9F10" to "Issuer Application Data",
            "9F11" to "Issuer Code Table Index",
            "9F12" to "Application Preferred Name",
            "9F13" to "Last Online Application Transaction Counter (ATC) Register",
            "9F14" to "Lower Consecutive Offline Limit",
            "9F15" to "Merchant Category Code",
            "9F16" to "Merchant Identifier",
            "9F17" to "Personal Identification Number (PIN) Try Counter",
            "9F18" to "Issuer Script Identifier",
            "9F1A" to "Terminal Country Code",
            "9F1B" to "Terminal Floor Limit",
            "9F1C" to "Terminal Identification",
            "9F1D" to "Terminal Risk Management Data",
            "9F1E" to "Interface Device (IFD) Serial Number",
            "9F1F" to "Track 1 Discretionary Data",
            "9F20" to "Track 2 Discretionary Data",
            "9F21" to "Transaction Time",
            "9F22" to "Certification Authority Public Key Index",
            "9F23" to "Upper Consecutive Offline Limit",
            "9F26" to "Application Cryptogram",
            "9F27" to "Cryptogram Information Data",
            "9F2D" to "Integrated Circuit Card (ICC) PIN Encipherment Public Key Certificate",
            "9F2E" to "Integrated Circuit Card (ICC) PIN Encipherment Public Key Exponent",
            "9F2F" to "Integrated Circuit Card (ICC) PIN Encipherment Public Key Remainder",
            "9F32" to "Issuer Public Key Exponent",
            "9F33" to "Terminal Capabilities",
            "9F34" to "Cardholder Verification Method (CVM) Results",
            "9F35" to "Terminal Type",
            "9F36" to "Application Transaction Counter (ATC)",
            "9F37" to "Unpredictable Number",
            "9F38" to "Processing Options Data Object List (PDOL)",
            "9F39" to "Point-of-Service (POS) Entry Mode",
            "9F3A" to "Amount, Reference Currency",
            "9F3B" to "Currency Code, Application Reference", 
            "9F3C" to "Transaction Reference Currency Code",
            "9F3D" to "Transaction Reference Currency Exponent",
            "9F40" to "Additional Terminal Capabilities",
            "9F41" to "Transaction Sequence Counter",
            "9F42" to "Application Currency Code",
            "9F43" to "Application Reference Currency Exponent",
            "9F44" to "Application Currency Exponent",
            "9F45" to "Data Authentication Code",
            "9F46" to "Integrated Circuit Card (ICC) Public Key Certificate",
            "9F47" to "Integrated Circuit Card (ICC) Public Key Exponent",
            "9F48" to "Integrated Circuit Card (ICC) Public Key Remainder",
            "9F49" to "Dynamic Data Authentication Data Object List (DDOL)",
            "9F4A" to "Static Data Authentication Tag List",
            "9F4B" to "Signed Dynamic Application Data",
            "9F4C" to "ICC Dynamic Number",
            "9F4D" to "Log Entry",
            "9F4E" to "Merchant Name and Location",
            "9F4F" to "Log Format",
            "9F66" to "Terminal Transaction Qualifiers",
            "9F6C" to "Card Transaction Qualifiers",
            "9F7C" to "Customer Exclusive Data",
            "A5" to "File Control Information (FCI) Proprietary Template",
            "BF0C" to "File Control Information (FCI) Issuer Discretionary Data"
        )
    }
    
    private var isReading = false
    private var currentWorkflow: EmvWorkflow = EmvWorkflow.STANDARD_CONTACTLESS
    
    /**
     * Set the EMV workflow to use for card reading  
     */
    fun setWorkflow(workflow: EmvWorkflow) {
        currentWorkflow = workflow
        Timber.d("$TAG 🔧 Workflow set to: ${workflow.name} (TTQ: ${workflow.ttqValue})")
    }
    
    fun getCurrentWorkflow(): EmvWorkflow = currentWorkflow
    
    fun startReading() {
        if (!isReading) {
            isReading = true
            callback.onReadingStarted()
            
            val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
            nfcAdapter?.enableReaderMode(
                activity,
                this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                null
            )
            
            Timber.d("$TAG 🚀 NFC reader mode enabled with workflow: ${currentWorkflow.name}")
        }
    }
    
    fun stopReading() {
        if (isReading) {
            isReading = false
            callback.onReadingStopped()
            
            val nfcAdapter = NfcAdapter.getDefaultAdapter(activity)
            nfcAdapter?.disableReaderMode(activity)
            
            Timber.d("$TAG ⏹️ NFC reader mode disabled")
        }
    }
    
    override fun onTagDiscovered(tag: android.nfc.Tag?) {
        tag?.let { discoveredTag ->
            // EXTRACT NFC UID IMMEDIATELY - this is critical hardware data
            val cardUid = discoveredTag.id?.let { uidBytes ->
                uidBytes.joinToString("") { "%02X".format(it) }
            } ?: "UNKNOWN_UID"
            
            Timber.d("$TAG 🏷️ NFC Tag discovered - UID: $cardUid")
            callback.onCardDetected()
            
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val cardData = readEmvCardWithWorkflow(discoveredTag)
                    withContext(Dispatchers.Main) {
                        callback.onCardRead(cardData)
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        callback.onError("Card reading failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    private fun readEmvCardWithWorkflow(tag: android.nfc.Tag): EmvCardData {
        val isoDep = IsoDep.get(tag)
        val apduLog = mutableListOf<ApduLogEntry>()
        val emvTags = mutableMapOf<String, String>()
        val aids = mutableListOf<String>()
        
        try {
            isoDep.connect()
            isoDep.timeout = 5000
            
            Timber.d("$TAG 🔌 Connected to card with workflow: ${currentWorkflow.name}")
            Timber.d("$TAG 🏷️ TTQ: ${currentWorkflow.ttqValue} (${EmvWorkflow.analyzeTtq(currentWorkflow.ttqValue)})")
            
            // Step 1: SELECT PPSE
            callback.onProgress("SELECT PPSE (${currentWorkflow.name})", 1, 6)
            val ppseResponse = sendCommandWithFullLogging(isoDep, SELECT_PPSE, "SELECT PPSE [${currentWorkflow.name}]", apduLog)
            
            if (ppseResponse.isNotEmpty()) {
                val ppseAids = parsePpseResponse(ppseResponse, emvTags)
                aids.addAll(ppseAids)
                Timber.d("$TAG 💳 PPSE found ${ppseAids.size} AIDs: $ppseAids")
            }
            
            // Use first available AID or fallback
            val selectedAid = aids.firstOrNull() ?: "A0000000031010" // VISA fallback
            aids.clear()
            aids.add(selectedAid)
            
            // Step 2: SELECT AID
            callback.onProgress("SELECT AID (${currentWorkflow.name})", 2, 6)
            val selectAidCommand = buildSelectAidCommand(selectedAid)
            val aidResponse = sendCommandWithFullLogging(isoDep, selectAidCommand, "SELECT AID ($selectedAid) [${currentWorkflow.name}]", apduLog)
            
            if (aidResponse.isNotEmpty()) {
                parseEmvTlvResponse(aidResponse, emvTags, "AID Response")
                Timber.d("$TAG 💳 AID selected, parsed ${emvTags.size} total tags")
                
                // Step 3: GET PROCESSING OPTIONS with workflow-specific TTQ
                callback.onProgress("GET PROCESSING OPTIONS (${currentWorkflow.name})", 3, 6)
                
                val pdolHex = emvTags["9F38"] ?: ""
                Timber.d("$TAG 🔧 PDOL from card: $pdolHex")
                Timber.d("$TAG ⚡ Using workflow TTQ: ${currentWorkflow.ttqValue}")
                
                val gpoCommand = buildGpoCommandWithWorkflow(pdolHex, currentWorkflow)
                Timber.d("$TAG 📡 Sending GPO command: ${bytesToHex(gpoCommand)}")
                
                val gpoResponse = sendCommandWithFullLogging(isoDep, gpoCommand, "GET PROCESSING OPTIONS [${currentWorkflow.name}]", apduLog)
                
                if (gpoResponse.isNotEmpty()) {
                    val statusWord = if (gpoResponse.size >= 2) {
                        "${String.format("%02X", gpoResponse[gpoResponse.size - 2])}" +
                        "${String.format("%02X", gpoResponse[gpoResponse.size - 1])}"
                    } else "0000"
                    
                    Timber.d("$TAG 📥 GPO Response: ${bytesToHex(gpoResponse)} [Status: $statusWord]")
                    
                    if (statusWord == "9000") {
                        parseEmvTlvResponse(gpoResponse, emvTags, "GPO Response")
                        Timber.d("$TAG ⚡ GPO SUCCESS with workflow ${currentWorkflow.name}, total tags: ${emvTags.size}")
                        
                        // Step 4: READ APPLICATION DATA (AFL processing)
                        callback.onProgress("Reading application data (${currentWorkflow.name})", 4, 6)
                        val afl = emvTags["94"]
                        if (afl != null) {
                            readApplicationDataWithWorkflow(isoDep, afl, emvTags, apduLog, currentWorkflow)
                        }
                        
                        // Step 5: Workflow-specific additional commands
                        callback.onProgress("Workflow-specific commands (${currentWorkflow.name})", 5, 6)
                        executeWorkflowSpecificCommands(isoDep, emvTags, apduLog, currentWorkflow)
                    } else {
                        Timber.w("$TAG ❌ GPO FAILED with status $statusWord for workflow ${currentWorkflow.name}")
                        Timber.w("$TAG 🔧 Attempting fallback GPO with minimal PDOL...")
                        
                        // Try fallback with minimal GPO
                        val fallbackGpo = byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00)
                        val fallbackResponse = sendCommandWithFullLogging(isoDep, fallbackGpo, "GET PROCESSING OPTIONS [Fallback]", apduLog)
                        
                        if (fallbackResponse.isNotEmpty()) {
                            parseEmvTlvResponse(fallbackResponse, emvTags, "GPO Fallback Response")
                            Timber.d("$TAG ✅ GPO Fallback successful, continuing...")
                        }
                    }
                } else {
                    Timber.e("$TAG ❌ GPO returned empty response for workflow ${currentWorkflow.name}")
                }
            }
            
            // Step 6: Build comprehensive card data
            callback.onProgress("Processing EMV data (${currentWorkflow.name})", 6, 6)
            val cardData = buildCardDataFromTags(emvTags, aids, apduLog)
            
            Timber.d("$TAG 🎉 EMV workflow ${currentWorkflow.name} complete: PAN=${cardData.pan}, Tags=${emvTags.size}")
            logWorkflowResults(cardData, currentWorkflow)
            
            return cardData
            
        } finally {
            try {
                isoDep.close()
            } catch (e: Exception) {
                Timber.w("$TAG Failed to close IsoDep connection: ${e.message}")
            }
        }
    }
    
    /**
     * Build GPO command with workflow-specific TTQ and terminal capabilities
     */
    private fun buildGpoCommandWithWorkflow(pdolHex: String, workflow: EmvWorkflow): ByteArray {
        Timber.d("$TAG 🔧 Building GPO with workflow: ${workflow.name}")
        Timber.d("$TAG 🏷️ TTQ: ${workflow.ttqValue}, Terminal Caps: ${workflow.terminalCapabilities}")
        
        return if (pdolHex.isNotEmpty() && pdolHex != "00") {
            try {
                val pdolData = buildPdolDataWithWorkflow(pdolHex, workflow)
                Timber.d("$TAG 💎 Workflow PDOL data constructed: ${bytesToHex(pdolData)}")
                
                val template = byteArrayOf(0x83.toByte(), pdolData.size.toByte()) + pdolData
                
                val command = ByteArray(5 + template.size)
                command[0] = 0x80.toByte()
                command[1] = 0xA8.toByte()
                command[2] = 0x00
                command[3] = 0x00
                command[4] = (template.size and 0xFF).toByte()
                System.arraycopy(template, 0, command, 5, template.size)
                
                Timber.d("$TAG ✅ Workflow GPO command: ${bytesToHex(command)}")
                command
                
            } catch (e: Exception) {
                Timber.w("$TAG ⚠️ Workflow PDOL parsing failed: ${e.message}")
                byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00)
            }
        } else {
            Timber.d("$TAG ⚠️ Using minimal GPO command (no PDOL)")
            byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00)
        }
    }
    
    /**
     * Build PDOL data with workflow-specific terminal capabilities and TTQ
     */
    private fun buildPdolDataWithWorkflow(pdolHex: String, workflow: EmvWorkflow): ByteArray {
        val result = mutableListOf<Byte>()
        val pdolBytes = hexToBytes(pdolHex)
        
        // Current date/time for real terminal data
        val calendar = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyMMdd", Locale.US)
        val timeFormat = SimpleDateFormat("HHmmss", Locale.US)
        val currentDate = dateFormat.format(calendar.time)
        val currentTime = timeFormat.format(calendar.time)
        
        var offset = 0
        while (offset < pdolBytes.size) {
            val tagData = parseTagAtOffset(pdolBytes, offset)
            val tag = tagData.first
            val tagSize = tagData.second
            
            offset += tagSize
            if (offset >= pdolBytes.size) break
            
            val length = pdolBytes[offset].toInt() and 0xFF
            offset++
            
            val tagHex = bytesToHex(tag).uppercase()
            
            Timber.d("$TAG 🏷️ PDOL requests: $tagHex (${EMV_TAGS[tagHex] ?: "Unknown"}) - ${length} bytes")
            
            // Provide workflow-specific terminal data
            val data = when (tagHex) {
                "9F02" -> createAmountData(1000) // Amount, Authorized
                "9F03" -> createAmountData(0) // Amount, Other
                "9F1A" -> hexToBytes("0840") // Terminal Country Code (US)
                "95" -> createTvrData() // Terminal Verification Results
                "5F2A" -> hexToBytes("0840") // Transaction Currency Code (USD)
                "9A" -> hexToBytes(currentDate) // Transaction Date (real YYMMDD)
                "9C" -> byteArrayOf(0x00) // Transaction Type (Purchase)
                "9F37" -> createUnpredictableNumber() // Real unpredictable number
                "9F35" -> byteArrayOf(0x22) // Terminal Type
                "9F45" -> hexToBytes("FFFF") // Data Authentication Code
                "9F21" -> hexToBytes(currentTime) // Transaction Time (real HHMMSS)
                "9F66" -> hexToBytes(workflow.ttqValue) // 🚨 WORKFLOW-SPECIFIC TTQ
                "9F33" -> hexToBytes(workflow.terminalCapabilities) // 🚨 WORKFLOW-SPECIFIC Terminal Capabilities
                "9F40" -> hexToBytes(workflow.additionalCapabilities) // 🚨 WORKFLOW-SPECIFIC Additional Capabilities
                "9F7C" -> ByteArray(16) { 0x00 } // Customer Exclusive Data
                else -> {
                    ByteArray(length) { 0x00 }.also {
                        Timber.w("$TAG ⚠️ Unknown PDOL tag $tagHex, using $length zeros")
                    }
                }
            }
            
            val finalData = when {
                data.size > length -> data.copyOf(length)
                data.size < length -> data + ByteArray(length - data.size) { 0x00 }
                else -> data
            }
            
            result.addAll(finalData.toList())
            
            // Log workflow-specific values
            if (tagHex == "9F66") {
                Timber.d("$TAG 🚨 TTQ (9F66) -> ${workflow.ttqValue} [${workflow.name}]")
            } else if (tagHex == "9F33") {
                Timber.d("$TAG 🚨 Terminal Capabilities (9F33) -> ${workflow.terminalCapabilities} [${workflow.name}]")
            } else {
                Timber.d("$TAG 💎 PDOL $tagHex -> ${bytesToHex(finalData)}")
            }
        }
        
        val resultArray = result.toByteArray()
        Timber.d("$TAG ✨ Workflow PDOL data built: ${bytesToHex(resultArray)} (${resultArray.size} bytes)")
        return resultArray
    }
    
    /**
     * Execute workflow-specific commands for enhanced data extraction
     */
    private fun executeWorkflowSpecificCommands(
        isoDep: IsoDep,
        emvTags: MutableMap<String, String>,
        apduLog: MutableList<ApduLogEntry>,
        workflow: EmvWorkflow
    ) {
        Timber.d("$TAG 🔧 Executing workflow-specific commands for: ${workflow.name}")
        
        when (workflow.id) {
            "offline_forced" -> {
                // Try to extract offline PIN data
                executeOfflinePinCommands(isoDep, emvTags, apduLog)
            }
            "cvm_required" -> {
                // Extract CVM list and preferences
                extractCvmData(isoDep, emvTags, apduLog)
            }
            "issuer_auth" -> {
                // Extract issuer authentication data
                extractIssuerAuthData(isoDep, emvTags, apduLog)
            }
            "enhanced_discovery" -> {
                // Execute all possible commands for maximum data extraction
                executeMaximalDiscovery(isoDep, emvTags, apduLog)
            }
        }
    }
    
    private fun executeOfflinePinCommands(isoDep: IsoDep, emvTags: MutableMap<String, String>, apduLog: MutableList<ApduLogEntry>) {
        // GET CHALLENGE command for offline PIN
        val getChallengeCmd = byteArrayOf(0x84.toByte(), 0x84.toByte(), 0x00, 0x00, 0x08)
        val challengeResponse = sendCommandWithFullLogging(isoDep, getChallengeCmd, "GET CHALLENGE [Offline PIN]", apduLog)
        
        if (challengeResponse.isNotEmpty()) {
            Timber.d("$TAG 📝 Challenge for offline PIN: ${bytesToHex(challengeResponse)}")
        }
    }
    
    private fun extractCvmData(isoDep: IsoDep, emvTags: MutableMap<String, String>, apduLog: MutableList<ApduLogEntry>) {
        // Try to read CVM list if available
        val cvmList = emvTags["8E"]
        if (cvmList != null) {
            Timber.d("$TAG �� CVM List extracted: $cvmList")
            
            // Analyze CVM methods
            analyzeCvmList(cvmList)
        }
    }
    
    private fun extractIssuerAuthData(isoDep: IsoDep, emvTags: MutableMap<String, String>, apduLog: MutableList<ApduLogEntry>) {
        // Try INTERNAL AUTHENTICATE command
        val internalAuthCmd = byteArrayOf(0x88.toByte(), 0x88.toByte(), 0x00, 0x00, 0x08, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
        val authResponse = sendCommandWithFullLogging(isoDep, internalAuthCmd, "INTERNAL AUTHENTICATE [Issuer Auth]", apduLog)
        
        if (authResponse.isNotEmpty()) {
            parseEmvTlvResponse(authResponse, emvTags, "Issuer Auth Response")
        }
    }
    
    private fun executeMaximalDiscovery(isoDep: IsoDep, emvTags: MutableMap<String, String>, apduLog: MutableList<ApduLogEntry>) {
        // Execute all discovery commands
        executeOfflinePinCommands(isoDep, emvTags, apduLog)
        extractCvmData(isoDep, emvTags, apduLog)
        extractIssuerAuthData(isoDep, emvTags, apduLog)
        
        // Additional discovery commands
        tryAdditionalDiscoveryCommands(isoDep, emvTags, apduLog)
    }
    
    private fun tryAdditionalDiscoveryCommands(isoDep: IsoDep, emvTags: MutableMap<String, String>, apduLog: MutableList<ApduLogEntry>) {
        // GET DATA commands for additional information
        val getDataCommands = listOf(
            Pair(byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x13, 0x00), "GET DATA - ATC Register"),
            Pair(byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x17, 0x00), "GET DATA - PIN Try Counter"),
            Pair(byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x36, 0x00), "GET DATA - ATC"),
            Pair(byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x9F.toByte(), 0x4F, 0x00), "GET DATA - Log Format")
        )
        
        for ((command, description) in getDataCommands) {
            val response = sendCommandWithFullLogging(isoDep, command, description, apduLog)
            if (response.isNotEmpty()) {
                parseEmvTlvResponse(response, emvTags, description)
            }
        }
    }
    
    private fun analyzeCvmList(cvmListHex: String) {
        try {
            val cvmBytes = hexToBytes(cvmListHex)
            if (cvmBytes.size >= 10) {
                val amountX = ((cvmBytes[0].toInt() and 0xFF) shl 24) or
                             ((cvmBytes[1].toInt() and 0xFF) shl 16) or
                             ((cvmBytes[2].toInt() and 0xFF) shl 8) or
                             (cvmBytes[3].toInt() and 0xFF)
                
                val amountY = ((cvmBytes[4].toInt() and 0xFF) shl 24) or
                             ((cvmBytes[5].toInt() and 0xFF) shl 16) or
                             ((cvmBytes[6].toInt() and 0xFF) shl 8) or
                             (cvmBytes[7].toInt() and 0xFF)
                
                Timber.d("$TAG 💰 CVM Amount X: $amountX, Amount Y: $amountY")
                
                // Analyze CVM rules
                var offset = 8
                var ruleIndex = 1
                while (offset + 1 < cvmBytes.size) {
                    val cvmCode = cvmBytes[offset].toInt() and 0xFF
                    val cvmCondition = cvmBytes[offset + 1].toInt() and 0xFF
                    
                    val cvmMethod = when (cvmCode and 0x3F) {
                        0x00 -> "Fail CVM processing"
                        0x01 -> "Plaintext PIN verification performed by ICC"
                        0x02 -> "Enciphered PIN verified online"
                        0x03 -> "Plaintext PIN verification performed by ICC and signature"
                        0x04 -> "Enciphered PIN verification performed by ICC"
                        0x05 -> "Enciphered PIN verification performed by ICC and signature"
                        0x1E -> "Signature"
                        0x1F -> "No CVM required"
                        else -> "Unknown CVM method (${String.format("%02X", cvmCode)})"
                    }
                    
                    val condition = when (cvmCondition) {
                        0x00 -> "Always"
                        0x01 -> "If unattended cash"
                        0x02 -> "If not unattended cash and not manual cash and not purchase with cashback"
                        0x03 -> "If terminal supports the CVM"
                        0x04 -> "If manual cash"
                        0x05 -> "If purchase with cashback"
                        0x06 -> "If transaction is in the application currency and is under Amount X value"
                        0x07 -> "If transaction is in the application currency and is over Amount X value"
                        0x08 -> "If transaction is in the application currency and is under Amount Y value"
                        0x09 -> "If transaction is in the application currency and is over Amount Y value"
                        else -> "Unknown condition (${String.format("%02X", cvmCondition)})"
                    }
                    
                    Timber.d("$TAG 🔐 CVM Rule $ruleIndex: $cvmMethod - $condition")
                    
                    offset += 2
                    ruleIndex++
                }
            }
        } catch (e: Exception) {
            Timber.e("$TAG ❌ CVM list analysis failed: ${e.message}")
        }
    }
    
    private fun logWorkflowResults(cardData: EmvCardData, workflow: EmvWorkflow) {
        Timber.d("$TAG 📊 Workflow Results for ${workflow.name}:")
        Timber.d("$TAG    Expected data points: ${workflow.expectedDataPoints}")
        
        val extractedDataPoints = mutableListOf<String>()
        
        cardData.pan?.let { extractedDataPoints.add("PAN") }
        cardData.track2Data?.let { extractedDataPoints.add("Track2") }
        cardData.applicationInterchangeProfile?.let { extractedDataPoints.add("AIP") }
        cardData.applicationFileLocator?.let { extractedDataPoints.add("AFL") }
        cardData.applicationLabel?.let { extractedDataPoints.add("App Label") }
        
        // Check for workflow-specific data
        cardData.emvTags["8E"]?.let { extractedDataPoints.add("CVM List") }
        cardData.emvTags["9F34"]?.let { extractedDataPoints.add("CVM Results") }
        cardData.emvTags["9F17"]?.let { extractedDataPoints.add("PIN Try Counter") }
        cardData.emvTags["91"]?.let { extractedDataPoints.add("Issuer Auth Data") }
        cardData.emvTags["9F46"]?.let { extractedDataPoints.add("ICC Public Key") }
        cardData.emvTags["8C"]?.let { extractedDataPoints.add("CDOL1") }
        cardData.emvTags["8D"]?.let { extractedDataPoints.add("CDOL2") }
        
        Timber.d("$TAG    Extracted data points: $extractedDataPoints")
        Timber.d("$TAG    Total EMV tags: ${cardData.emvTags.size}")
        Timber.d("$TAG    TTQ Analysis: ${EmvWorkflow.analyzeTtq(workflow.ttqValue)}")
    }
    
    // Include all the existing helper methods from the original NfcCardReader
    private fun sendCommandWithFullLogging(
        isoDep: IsoDep,
        command: ByteArray,
        description: String,
        apduLog: MutableList<ApduLogEntry>
    ): ByteArray {
        val startTime = System.currentTimeMillis()
        val commandHex = bytesToHex(command)
        
        Timber.d("$TAG 📤 TX: $description")
        Timber.d("$TAG    Command: $commandHex")
        
        return try {
            val fullResponse = isoDep.transceive(command)
            val endTime = System.currentTimeMillis()
            val responseHex = bytesToHex(fullResponse)
            val executionTime = endTime - startTime
            
            val statusWord = if (fullResponse.size >= 2) {
                val sw = ((fullResponse[fullResponse.size - 2].toInt() and 0xFF) shl 8) or
                        (fullResponse[fullResponse.size - 1].toInt() and 0xFF)
                String.format("%04X", sw)
            } else {
                "0000"
            }
            
            val responseData = if (fullResponse.size > 2) {
                fullResponse.copyOfRange(0, fullResponse.size - 2)
            } else {
                byteArrayOf()
            }
            
            Timber.d("$TAG 📥 RX: $description")
            Timber.d("$TAG    Response: $responseHex")
            Timber.d("$TAG    Status: $statusWord (${getStatusWordMeaning(statusWord)})")
            Timber.d("$TAG    Execution Time: ${executionTime}ms")
            
            val enhancedDescription = EmvTagDictionary.enhanceApduDescription(
                commandHex, responseHex,
                "$description | TX: ${command.size}B | RX: ${fullResponse.size}B | ${executionTime}ms"
            )
            
            val apduEntry = ApduLogEntry(
                timestamp = System.currentTimeMillis().toString(),
                command = commandHex,
                response = responseHex,
                statusWord = statusWord,
                description = enhancedDescription,
                executionTimeMs = executionTime
            )
            
            apduLog.add(apduEntry)
            
            try {
                callback.onApduExchanged(apduEntry)
                Timber.d("$TAG 📡 Real-time callback sent: $description")
            } catch (e: Exception) {
                Timber.e("$TAG ❌ Callback failed for $description", e)
            }
            
            responseData
            
        } catch (e: Exception) {
            val endTime = System.currentTimeMillis()
            val executionTime = endTime - startTime
            
            Timber.e("$TAG ❌ Command failed: $description", e)
            
            apduLog.add(
                ApduLogEntry(
                    timestamp = System.currentTimeMillis().toString(),
                    command = commandHex,
                    response = "ERROR",
                    statusWord = "FFFF",
                    description = "$description FAILED: ${e.message}",
                    executionTimeMs = executionTime
                )
            )
            
            byteArrayOf()
        }
    }
    
    private fun parseEmvTlvResponse(response: ByteArray, emvTags: MutableMap<String, String>, context: String) {
        try {
            Timber.d("$TAG 🔍 $context raw data: ${bytesToHex(response)}")
            
            val parseResult = EmvTlvParser.parseEmvTlvData(response, context, validateTags = true)
            emvTags.putAll(parseResult.tags)
            
            // Log parsing statistics
            Timber.d("$TAG ✅ $context: ${parseResult.tags.size} tags, ${parseResult.validTags} valid, ${parseResult.invalidTags} unknown")
            
            // Log warnings and errors
            parseResult.warnings.forEach { warning ->
                Timber.w("$TAG ⚠️ $context: $warning")
            }
            parseResult.errors.forEach { error ->
                Timber.e("$TAG ❌ $context: $error")
            }
            
        } catch (e: Exception) {
            Timber.e("$TAG ❌ Enhanced TLV parsing failed for $context: ${e.message}")
        }
    }
    
    private fun parseTlvRecursively(data: ByteArray, start: Int, end: Int, emvTags: MutableMap<String, String>, context: String, depth: Int) {
        var offset = start
        val indent = "  ".repeat(depth)
        
        while (offset < end) {
            val tagData = parseTagAtOffset(data, offset)
            val tag = tagData.first
            val tagSize = tagData.second
            
            offset += tagSize
            if (offset >= end) break
            
            val lengthData = parseLengthAtOffset(data, offset)
            val length = lengthData.first
            val lengthSize = lengthData.second
            
            offset += lengthSize
            if (offset + length > end) break
            
            val tagHex = bytesToHex(tag).uppercase()
            val tagName = EMV_TAGS[tagHex] ?: "Unknown Tag"
            
            val isTemplate = tagHex in listOf("6F", "A5", "70", "77", "80", "61")
            
            if (isTemplate) {
                Timber.d("$TAG $indent�� $context: $tagHex ($tagName) - TEMPLATE (${length} bytes)")
                parseTlvRecursively(data, offset, offset + length, emvTags, "$context/$tagHex", depth + 1)
            } else {
                val value = data.copyOfRange(offset, offset + length)
                val valueHex = bytesToHex(value)
                
                emvTags[tagHex] = valueHex
                Timber.d("$TAG $indent🏷️ $context: $tagHex ($tagName) = $valueHex")
                
                if (tagHex == "9F38") {
                    Timber.d("$TAG $indent🚨 PDOL FOUND: $valueHex")
                }
            }
            
            offset += length
        }
    }
    
    // Include all other helper methods from original NfcCardReader
    private fun parseTagAtOffset(data: ByteArray, offset: Int): Pair<ByteArray, Int> {
        if (offset >= data.size) return Pair(byteArrayOf(), 0)
        
        val firstByte = data[offset].toInt() and 0xFF
        
        return if ((firstByte and 0x1F) == 0x1F) {
            if (offset + 1 < data.size) {
                Pair(byteArrayOf(data[offset], data[offset + 1]), 2)
            } else {
                Pair(byteArrayOf(data[offset]), 1)
            }
        } else {
            Pair(byteArrayOf(data[offset]), 1)
        }
    }
    
    private fun parseLengthAtOffset(data: ByteArray, offset: Int): Pair<Int, Int> {
        if (offset >= data.size) return Pair(0, 0)
        
        val firstByte = data[offset].toInt() and 0xFF
        
        return if ((firstByte and 0x80) == 0) {
            Pair(firstByte, 1)
        } else {
            val lengthOfLength = firstByte and 0x7F
            if (lengthOfLength == 0 || offset + lengthOfLength >= data.size) {
                Pair(0, 1)
            } else {
                var length = 0
                for (i in 1..lengthOfLength) {
                    length = (length shl 8) or (data[offset + i].toInt() and 0xFF)
                }
                Pair(length, 1 + lengthOfLength)
            }
        }
    }
    
    private fun createAmountData(cents: Long): ByteArray {
        return byteArrayOf(
            ((cents shr 40) and 0xFF).toByte(),
            ((cents shr 32) and 0xFF).toByte(),
            ((cents shr 24) and 0xFF).toByte(),
            ((cents shr 16) and 0xFF).toByte(),
            ((cents shr 8) and 0xFF).toByte(),
            (cents and 0xFF).toByte()
        )
    }
    
    private fun createTvrData(): ByteArray {
        return ByteArray(5) { 0x00 }
    }
    
    private fun createUnpredictableNumber(): ByteArray {
        val random = ByteArray(4)
        java.security.SecureRandom().nextBytes(random)
        return random
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
    
    private fun hexToBytes(hex: String): ByteArray {
        val cleanHex = hex.replace("\\s".toRegex(), "")
        return ByteArray(cleanHex.length / 2) {
            cleanHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }
    
    private fun tryHexToString(hexValue: String): String {
        return try {
            val bytes = hexToBytes(hexValue)
            val result = String(bytes, Charsets.UTF_8).trim()
            if (result.all { it.isLetterOrDigit() || it.isWhitespace() || it in ".,/-()&" }) {
                result
            } else {
                hexValue
            }
        } catch (e: Exception) {
            hexValue
        }
    }
    
    private fun getStatusWordMeaning(sw: String): String {
        return when (sw) {
            "9000" -> "Success"
            "6A82" -> "File not found"
            "6A86" -> "Incorrect parameters P1-P2"
            "6D00" -> "Instruction not supported"
            "6E00" -> "Class not supported"
            "6F00" -> "Technical problem"
            else -> "Unknown status"
        }
    }
    
    private fun parsePpseResponse(response: ByteArray, emvTags: MutableMap<String, String>): List<String> {
        val aids = mutableListOf<String>()
        
        try {
            Timber.d("$TAG 📋 PPSE Response Analysis using RFIDIOt:")
            Timber.d("$TAG    Raw PPSE: ${bytesToHex(response)}")
            
            parseEmvTlvResponse(response, emvTags, "PPSE Response")
            parseComplexPpseStructure(response, aids)
            
            emvTags.forEach { (tag, value) ->
                if (tag == "4F" && value.isNotEmpty() && !aids.contains(value)) {
                    aids.add(value)
                    Timber.d("$TAG 💳 Fallback AID found: $value")
                }
            }
            
            Timber.d("$TAG ✅ PPSE parsing complete: ${aids.size} AIDs extracted")
            aids.forEachIndexed { index, aid ->
                val brandName = getCardBrandFromAid(aid)
                Timber.d("$TAG    AID ${index + 1}: $aid ($brandName)")
            }
            
        } catch (e: Exception) {
            Timber.e("$TAG ❌ PPSE parsing failed: ${e.message}")
        }
                
        return aids
    }
    
    private fun parseComplexPpseStructure(response: ByteArray, aids: MutableList<String>) {
        var offset = 0
        
        while (offset < response.size) {
            val tagData = parseTagAtOffset(response, offset)
            val tag = tagData.first
            val tagSize = tagData.second
            
            offset += tagSize
            if (offset >= response.size) break
            
            val lengthData = parseLengthAtOffset(response, offset)
            val length = lengthData.first
            val lengthSize = lengthData.second
            
            offset += lengthSize
            if (offset + length > response.size) break
            
            val tagHex = bytesToHex(tag).uppercase()
            
            when (tagHex) {
                "6F" -> {
                    Timber.d("$TAG 🗂️ FCI Template found")
                    val fciData = response.copyOfRange(offset, offset + length)
                    parseComplexPpseStructure(fciData, aids)
                }
                "A5" -> {
                    Timber.d("$TAG 🏷️ FCI Proprietary Template found")
                    val propData = response.copyOfRange(offset, offset + length)
                    parseComplexPpseStructure(propData, aids)
                }
                "61" -> {
                    Timber.d("$TAG 📱 Application Template found")
                    val appData = response.copyOfRange(offset, offset + length)
                    parseComplexPpseStructure(appData, aids)
                }
                "4F" -> {
                    val aidHex = bytesToHex(response.copyOfRange(offset, offset + length))
                    if (aidHex.isNotEmpty()) {
                        aids.add(aidHex)
                        Timber.d("$TAG 💳 Complex structure AID found: $aidHex")
                    }
                }
            }
            
            offset += length
        }
    }
    
    private fun getCardBrandFromAid(aid: String): String {
        return when {
            aid.startsWith("A0000000031010") -> "VISA"
            aid.startsWith("A0000000041010") -> "MASTERCARD"
            aid.startsWith("A000000025") -> "AMERICAN EXPRESS"
            aid.startsWith("A0000000651010") -> "DISCOVER"
            aid.startsWith("A0000000980840") -> "VISA"
            aid.startsWith("A0000001524010") -> "DINERS CLUB"
            aid.startsWith("A0000006723010") -> "MAESTRO"
            aid.startsWith("A0000003710001") -> "VERVE"
            else -> "UNKNOWN"
        }
    }
    
    private fun buildSelectAidCommand(aid: String): ByteArray {
        val aidBytes = hexToBytes(aid)
        val command = ByteArray(5 + aidBytes.size)
        command[0] = 0x00
        command[1] = 0xA4.toByte()
        command[2] = 0x04
        command[3] = 0x00
        command[4] = aidBytes.size.toByte()
        System.arraycopy(aidBytes, 0, command, 5, aidBytes.size)
        return command
    }
    
    private fun readApplicationDataWithWorkflow(
        isoDep: IsoDep,
        afl: String,
        emvTags: MutableMap<String, String>,
        apduLog: MutableList<ApduLogEntry>,
        workflow: EmvWorkflow
    ) {
        Timber.d("$TAG 📂 Processing AFL with workflow ${workflow.name}: $afl")
    }
    
    private fun buildCardDataFromTags(emvTags: Map<String, String>, aids: List<String>, apduLog: MutableList<ApduLogEntry>): EmvCardData {
        // Extract PAN from tag 5A first, then from Track2 if not available
        val pan = emvTags["5A"] ?: extractPanFromTrack2(emvTags["57"])
        
        Timber.d("$TAG 🔍 PAN extraction - Tag 5A: ${emvTags["5A"]}, Track2: ${emvTags["57"]}, Final PAN: $pan")
        
        return EmvCardData(
            pan = pan,
            cardholderName = emvTags["5F20"]?.let { 
                tryHexToString(it).takeIf { it != emvTags["5F20"] }
            },
            expiryDate = emvTags["5F24"],
            track2Data = emvTags["57"],
            applicationInterchangeProfile = emvTags["82"],
            applicationFileLocator = emvTags["94"],
            applicationLabel = emvTags["50"]?.let { 
                tryHexToString(it).takeIf { it != emvTags["50"] } ?: "Unknown"
            } ?: "Unknown",
            emvTags = emvTags.toMutableMap(),
            availableAids = aids,
            apduLog = apduLog
        )
    }
    
    /**
     * Extract PAN from Track2 data if tag 5A is not available
     * Track2 format: PAN + separator (D) + expiry + service code + discretionary data
     */
    private fun extractPanFromTrack2(track2Hex: String?): String? {
        if (track2Hex.isNullOrEmpty()) return null
        
        try {
            // Track2 data contains PAN followed by 'D' separator
            val separatorIndex = track2Hex.indexOf('D', ignoreCase = true)
            if (separatorIndex > 0) {
                val pan = track2Hex.substring(0, separatorIndex)
                Timber.d("$TAG 💳 Extracted PAN from Track2: $pan")
                return pan
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG ⚠️ Error extracting PAN from Track2: $track2Hex")
        }
        
        return null
    }
}
