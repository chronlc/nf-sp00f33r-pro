package com.nfsp00f33r.app.emulation

import android.nfc.tech.IsoDep
import timber.log.Timber
import com.nfsp00f33r.app.cardreading.EmvTlvParser
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*

/**
 * PM EMV Reader - Proxmark3-Style EMV Card Reader
 * 
 * Clean, modular EMV reader mimicking Proxmark3's workflow.
 * Integrates with nf-sp00f33r's EmvTlvParser for consistent parsing.
 * 
 * Based on: github.com/RfidResearchGroup/proxmark3
 * - emvcore.c: Core EMV functions (EMVSelect, EMVGPO, EMVReadRecord, etc.)
 * - cmdemv.c: Command execution and workflow orchestration
 * 
 * @author nf-sp00f33r
 * @date October 11, 2025
 */
class PmEmvReader {
    // Using Timber for logging
    
    // ========================================
    // ENUMS AND DATA CLASSES
    // ========================================
    
    /**
     * Transaction Types (from Proxmark3 cmdemv.c line 1400)
     * 
     * Determines terminal capabilities and transaction flow.
     * User selects this before starting transaction.
     */
    enum class TransactionType {
        TT_MSD,          // Magnetic Stripe Data (default) - Works for ALL cards
        TT_QVSDCMCHIP,   // qVSDC or M/Chip - VISA Quick VSDC or Mastercard M/Chip
        TT_CDA,          // CDA mode - Combined Data Authentication + AC
        TT_VSDC          // VSDC (for testing only)
    }
    
    /**
     * Card Vendor (from Proxmark3 emvcore.c line 50-138)
     */
    enum class CardVendor {
        CV_NA,              // Not Available
        CV_VISA,            // VISA (A0000000031010)
        CV_MASTERCARD,      // Mastercard (A0000000041010)
        CV_AMERICANEXPRESS, // American Express (A000000025)
        CV_JCB,             // JCB (A0000000651010)
        CV_DISCOVER,        // Discover (A0000001523010)
        CV_DINERS,          // Diners Club (A0000001524010)
        CV_OTHER            // Other/Unknown
    }
    
    /**
     * Channel Type (from Proxmark3 emvcore.h)
     */
    enum class ChannelType {
        CC_CONTACTLESS,  // NFC/RFID contactless
        CC_CONTACT       // Contact chip
    }
    
    /**
     * Complete EMV Session Data
     */
    data class EmvSession(
        // Session metadata
        val sessionId: String = UUID.randomUUID().toString(),
        val timestamp: Long = System.currentTimeMillis(),
        val transactionType: TransactionType = TransactionType.TT_MSD,
        val channelType: ChannelType = ChannelType.CC_CONTACTLESS,
        
        // Card identification
        var cardVendor: CardVendor = CardVendor.CV_NA,
        var selectedAid: ByteArray? = null,
        var pan: String? = null,
        var expiryDate: String? = null,
        var track2: String? = null,
        
        // TLV data storage (mimics Proxmark3's tlvdb)
        val tlvDatabase: MutableMap<String, ByteArray> = mutableMapOf(),
        
        // APDU log
        val apduLog: MutableList<ApduEntry> = mutableListOf(),
        
        // Authentication results
        var aipValue: Int = 0,
        var authMethod: String = "NONE", // SDA, DDA, CDA, or NONE
        var arqc: ByteArray? = null,
        var atc: ByteArray? = null,
        
        // Status
        var completed: Boolean = false,
        var errorMessage: String? = null
    )
    
    data class ApduEntry(
        val command: ByteArray,
        val response: ByteArray,
        val sw1sw2: String,
        val description: String,
        val timestamp: Long = System.currentTimeMillis()
    )
    
    /**
     * Transaction Parameters (from Proxmark3 cmdemv.c InitTransactionParameters)
     */
    data class TransactionParameters(
        val amount: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x01), // 0.01
        val otherAmount: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x00), // 0
        val countryCode: ByteArray = byteArrayOf(0x08, 0x40), // USA = 0x0840
        val currencyCode: ByteArray = byteArrayOf(0x08, 0x40), // USD = 0x0840
        val transactionDate: ByteArray = getCurrentDate(),
        val transactionType: ByteArray = byteArrayOf(0x00), // Purchase
        val unpredictableNumber: ByteArray = getUnpredictableNumber(),
        val terminalVerificationResults: ByteArray = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00),
        val terminalTransactionQualifiers: ByteArray, // Set based on transaction type
        val issuerApplicationData: ByteArray = ByteArray(16) { 0x00 }, // 9F10
        val applicationTransactionCounter: ByteArray = byteArrayOf(0x00, 0x00) // 9F36
    )
    
    // ========================================
    // PROXMARK3 CORE FUNCTIONS
    // ========================================
    
    /**
     * Main EMV Execution Function
     * 
     * Mimics: CmdEMVExec() from cmdemv.c line 1500-1800
     * 
     * Complete workflow:
     * 1. SELECT PPSE (or search AIDs)
     * 2. SELECT AID
     * 3. Initialize Transaction Parameters
     * 4. GET PROCESSING OPTIONS (GPO)
     * 5. READ AFL RECORDS
     * 6. OFFLINE DATA AUTHENTICATION
     * 7. GENERATE AC (ARQC)
     * 8. Finalize
     * 
     * @param isoDep NFC IsoDep tag
     * @param transactionType User-selected transaction type
     * @param forceSearch Skip PPSE and search AIDs directly
     * @return EmvSession with complete transaction data
     */
    suspend fun executeEmvTransaction(
        isoDep: IsoDep,
        transactionType: TransactionType = TransactionType.TT_MSD,
        forceSearch: Boolean = false
    ): EmvSession {
        val session = EmvSession(
            transactionType = transactionType,
            channelType = ChannelType.CC_CONTACTLESS
        )
        
        try {
            Timber.i("Proxmark3EmvReader", "========================================")
            Timber.i("Proxmark3EmvReader", "EMV Transaction Started")
            Timber.i("Proxmark3EmvReader", "Transaction Type: ${transactionType.name}")
            Timber.i("Proxmark3EmvReader", "========================================")
            
            // Initialize transaction parameters
            val txParams = initTransactionParameters(transactionType)
            storeTxParamsInTlvDb(session, txParams)
            
            // Step 1: SELECT PPSE or Search AIDs
            val aids = if (forceSearch) {
                Timber.i("Proxmark3EmvReader", "\n* Search AID in list.")
                searchAids(isoDep, session)
            } else {
                Timber.i("Proxmark3EmvReader", "\n* PPSE.")
                selectPpse(isoDep, session)
            }
            
            if (aids.isEmpty()) {
                session.errorMessage = "No EMV applications found"
                return session
            }
            
            // Step 2: SELECT AID (use first available)
            val selectedAid = aids.first()
            session.selectedAid = selectedAid
            Timber.i("Proxmark3EmvReader", "\n* Selecting AID: ${selectedAid.toHexString()}")
            
            if (!selectAid(isoDep, session, selectedAid)) {
                session.errorMessage = "Failed to select AID"
                return session
            }
            
            // Detect card vendor
            session.cardVendor = getCardVendor(selectedAid)
            Timber.i("Proxmark3EmvReader", "* Card Vendor: ${session.cardVendor.name}")
            
            // Step 3: GET PROCESSING OPTIONS (GPO)
            Timber.i("Proxmark3EmvReader", "\n* GET PROCESSING OPTIONS")
            if (!performGpo(isoDep, session, txParams)) {
                session.errorMessage = "GPO failed"
                return session
            }
            
            // Extract PAN from Track2 if not already present
            extractPanFromTrack2(session)
            
            // Step 4: READ AFL RECORDS
            Timber.i("Proxmark3EmvReader", "\n* Read AFL")
            readAflRecords(isoDep, session)
            
            // Step 5: OFFLINE DATA AUTHENTICATION
            Timber.i("Proxmark3EmvReader", "\n* Offline Data Authentication")
            performOfflineAuthentication(isoDep, session)
            
            // Step 6: GENERATE AC (ARQC)
            Timber.i("Proxmark3EmvReader", "\n* Generate AC (ARQC)")
            performGenerateAc(isoDep, session, txParams)
            
            // Step 7: Finalize
            session.completed = true
            Timber.i("Proxmark3EmvReader", "\n* Transaction completed successfully")
            Timber.i("Proxmark3EmvReader", "* PAN: ${session.pan}")
            Timber.i("Proxmark3EmvReader", "* Expiry: ${session.expiryDate}")
            Timber.i("Proxmark3EmvReader", "* Vendor: ${session.cardVendor.name}")
            Timber.i("Proxmark3EmvReader", "* Auth Method: ${session.authMethod}")
            Timber.i("Proxmark3EmvReader", "* Tags Collected: ${session.tlvDatabase.size}")
            
        } catch (e: Exception) {
            Timber.e("Proxmark3EmvReader", "Transaction error: ${e.message}")
            session.errorMessage = e.message
        }
        
        return session
    }
    
    // ========================================
    // STEP 1: SELECT PPSE
    // ========================================
    
    /**
     * SELECT PPSE (Proximity Payment System Environment)
     * 
     * Mimics: EMVSearchPSE() from emvcore.c
     * 
     * APDU: 00 A4 04 00 0E 325041592E5359532E4444463031 00
     *       |  |  |  |  |  2PAY.SYS.DDF01
     *       |  |  |  |  Length (14 bytes)
     *       |  |  |  P2 (First/Only occurrence)
     *       |  |  P1 (Select by name)
     *       |  INS (SELECT)
     *       CLA (ISO 7816)
     */
    private suspend fun selectPpse(isoDep: IsoDep, session: EmvSession): List<ByteArray> {
        val ppseCommand = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, 0x0E,
            0x32, 0x50, 0x41, 0x59, 0x2E, 0x53, 0x59, 0x53, 0x2E, 0x44, 0x44, 0x46, 0x30, 0x31,
            0x00
        )
        
        val response = transceive(isoDep, ppseCommand, session, "SELECT PPSE")
        if (response == null || response.size < 2) {
            return emptyList()
        }
        
        // Check SW1SW2
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        if (sw1 != 0x90 || sw2 != 0x00) {
            Timber.w("Proxmark3EmvReader", "PPSE not found (SW=$sw1$sw2), will search AIDs")
            return emptyList()
        }
        
        // Parse PPSE response for AIDs
        val data = response.copyOf(response.size - 2)
        parseTlvIntoSession(session, data)
        
        // Extract AIDs from FCI Template (tag 6F -> A5 -> BF0C -> 61 -> 4F)
        return extractAidsFromPpse(data)
    }
    
    /**
     * Extract AIDs from PPSE response
     * 
     * Structure: 6F (FCI Template) -> A5 (FCI Proprietary) -> BF0C (FCI Issuer Disc Data) 
     *            -> 61 (Application Template) -> 4F (AID)
     */
    private fun extractAidsFromPpse(data: ByteArray): List<ByteArray> {
        val aids = mutableListOf<ByteArray>()
        
        try {
            val result = EmvTlvParser.parseEmvTlvData(data, "PPSE")
            val allTags = result.tags
            
            // Look for tag 61 (Application Template) which contains 4F (AID)
            for ((tag, value) in allTags) {
                if (tag == "61") {
                    // Parse inside 61 to find 4F - value is hex string, convert to bytes
                    val valueBytes = value.hexToByteArray()
                    val innerResult = EmvTlvParser.parseEmvTlvData(valueBytes, "APP_TEMPLATE")
                    innerResult.tags["4F"]?.let { aidHex ->
                        val aid = aidHex.hexToByteArray()
                        aids.add(aid)
                        Timber.i("Proxmark3EmvReader", "  Found AID: ${aid.toHexString()}")
                    }
                }
            }
            
        } catch (e: Exception) {
            Timber.e("Proxmark3EmvReader", "Error extracting AIDs: ${e.message}")
        }
        
        return aids
    }
    
    /**
     * Search for AIDs directly (if PPSE not supported)
     * 
     * Mimics: EMVSearch() from emvcore.c
     * 
     * Tries common AIDs:
     * - VISA: A0000000031010
     * - Mastercard: A0000000041010
     * - AMEX: A000000025
     * - Discover: A0000001523010
     * - JCB: A0000000651010
     */
    private suspend fun searchAids(isoDep: IsoDep, session: EmvSession): List<ByteArray> {
        val commonAids = listOf(
            "A0000000031010",       // VISA
            "A0000000041010",       // Mastercard
            "A000000025",           // AMEX
            "A0000001523010",       // Discover
            "A0000000651010",       // JCB
            "A0000001524010"        // Diners
        )
        
        val foundAids = mutableListOf<ByteArray>()
        
        for (aidHex in commonAids) {
            val aid = aidHex.hexToByteArray()
            val selectCommand = buildSelectCommand(aid)
            
            val response = transceive(isoDep, selectCommand, session, "Search AID: $aidHex")
            if (response != null && response.size >= 2) {
                val sw1 = response[response.size - 2].toInt() and 0xFF
                val sw2 = response[response.size - 1].toInt() and 0xFF
                
                if (sw1 == 0x90 && sw2 == 0x00) {
                    foundAids.add(aid)
                    Timber.i("Proxmark3EmvReader", "  Found AID: $aidHex")
                    
                    // Parse response
                    val data = response.copyOf(response.size - 2)
                    parseTlvIntoSession(session, data)
                }
            }
        }
        
        return foundAids
    }
    
    // ========================================
    // STEP 2: SELECT AID
    // ========================================
    
    /**
     * SELECT AID (Application Identifier)
     * 
     * Mimics: EMVSelect() from emvcore.c line 200
     * 
     * APDU: 00 A4 04 00 [len] [AID] 00
     */
    private suspend fun selectAid(isoDep: IsoDep, session: EmvSession, aid: ByteArray): Boolean {
        val selectCommand = buildSelectCommand(aid)
        
        val response = transceive(isoDep, selectCommand, session, "SELECT AID")
        if (response == null || response.size < 2) {
            return false
        }
        
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        
        if (sw1 != 0x90 || sw2 != 0x00) {
            Timber.e("Proxmark3EmvReader", "SELECT AID failed: SW=$sw1$sw2")
            return false
        }
        
        // Parse FCI response
        val data = response.copyOf(response.size - 2)
        parseTlvIntoSession(session, data)
        
        Timber.i("Proxmark3EmvReader", "* Selected AID successfully")
        return true
    }
    
    private fun buildSelectCommand(aid: ByteArray): ByteArray {
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)
    }
    
    // ========================================
    // STEP 3: INITIALIZE TRANSACTION PARAMETERS
    // ========================================
    
    /**
     * Initialize Transaction Parameters
     * 
     * Mimics: InitTransactionParameters() from cmdemv.c line 300-500
     * 
     * Sets TTQ (Terminal Transaction Qualifiers - tag 9F66) based on transaction type:
     * - TT_MSD: [0x80, 0x00, 0x00, 0x00] - Magnetic Stripe Data
     * - TT_QVSDCMCHIP: [0x26, 0x00, 0x00, 0x00] - qVSDC or M/Chip
     * - TT_CDA: [0x26, 0x80, 0x00, 0x00] - qVSDC + CDA
     * - TT_VSDC: [0x40, 0x80, 0x00, 0x00] - VSDC (test only)
     */
    private fun initTransactionParameters(transactionType: TransactionType): TransactionParameters {
        val ttq = when (transactionType) {
            TransactionType.TT_MSD -> byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x00)
            TransactionType.TT_QVSDCMCHIP -> byteArrayOf(0x26, 0x00, 0x00, 0x00)
            TransactionType.TT_CDA -> byteArrayOf(0x26, 0x80.toByte(), 0x00, 0x00)
            TransactionType.TT_VSDC -> byteArrayOf(0x40, 0x80.toByte(), 0x00, 0x00)
        }
        
        Timber.i("Proxmark3EmvReader", "* Init transaction parameters")
        Timber.i("Proxmark3EmvReader", "  Transaction Type: ${transactionType.name}")
        Timber.i("Proxmark3EmvReader", "  TTQ (9F66): ${ttq.toHexString()}")
        
        return TransactionParameters(terminalTransactionQualifiers = ttq)
    }
    
    /**
     * Store transaction parameters in TLV database
     */
    private fun storeTxParamsInTlvDb(session: EmvSession, params: TransactionParameters) {
        session.tlvDatabase["9F02"] = params.amount
        session.tlvDatabase["9F03"] = params.otherAmount
        session.tlvDatabase["9F1A"] = params.countryCode
        session.tlvDatabase["5F2A"] = params.currencyCode
        session.tlvDatabase["9A"] = params.transactionDate
        session.tlvDatabase["9C"] = params.transactionType
        session.tlvDatabase["9F37"] = params.unpredictableNumber
        session.tlvDatabase["95"] = params.terminalVerificationResults
        session.tlvDatabase["9F66"] = params.terminalTransactionQualifiers
        session.tlvDatabase["9F10"] = params.issuerApplicationData
        session.tlvDatabase["9F36"] = params.applicationTransactionCounter
    }
    
    // ========================================
    // STEP 4: GET PROCESSING OPTIONS (GPO)
    // ========================================
    
    /**
     * Perform GET PROCESSING OPTIONS
     * 
     * Mimics: EMVGPO() from emvcore.c line 300
     * 
     * APDU: 80 A8 00 00 [len] 83 [PDOL-len] [PDOL-data]
     * 
     * Uses dol_process() to build PDOL data dynamically
     */
    private suspend fun performGpo(
        isoDep: IsoDep,
        session: EmvSession,
        params: TransactionParameters
    ): Boolean {
        // Step 1: Get PDOL from card (tag 9F38)
        val pdolData = session.tlvDatabase["9F38"]
        
        val pdolDataEncoded = if (pdolData != null) {
            Timber.i("Proxmark3EmvReader", "* Calc PDOL")
            Timber.i("Proxmark3EmvReader", "  PDOL (9F38): ${pdolData.toHexString()}")
            
            // Parse PDOL and build data using dol_process()
            val pdolTags = parseDol(pdolData)
            val processedData = dolProcess(pdolTags, session)
            
            Timber.i("Proxmark3EmvReader", "  PDOL data[${processedData.size}]: ${processedData.toHexString()}")
            
            // Wrap in tag 83
            byteArrayOf(0x83.toByte(), processedData.size.toByte()) + processedData
        } else {
            // No PDOL: use tag 83 with length 0
            Timber.i("Proxmark3EmvReader", "* No PDOL, using empty tag 83")
            byteArrayOf(0x83.toByte(), 0x00)
        }
        
        // Step 2: Build GPO command
        val gpoCommand = byteArrayOf(
            0x80.toByte(), 0xA8.toByte(), 0x00, 0x00,
            pdolDataEncoded.size.toByte()
        ) + pdolDataEncoded
        
        // Step 3: Send GPO
        Timber.i("Proxmark3EmvReader", "\n* GPO")
        val response = transceive(isoDep, gpoCommand, session, "GET PROCESSING OPTIONS")
        if (response == null || response.size < 2) {
            return false
        }
        
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        
        if (sw1 != 0x90 || sw2 != 0x00) {
            Timber.e("Proxmark3EmvReader", "GPO failed: SW=$sw1$sw2")
            return false
        }
        
        // Step 4: Parse GPO response
        val data = response.copyOf(response.size - 2)
        processGpoResponse(session, data)
        
        return true
    }
    
    /**
     * Process GPO Response (Format 1 or Format 2)
     * 
     * Mimics: ProcessGPOResponseFormat1() from cmdemv.c
     * 
     * Format 1 (tag 80): Fixed format [AIP 2 bytes] [AFL variable]
     * Format 2 (tag 77): TLV format with tags 82 (AIP), 94 (AFL), etc.
     */
    private fun processGpoResponse(session: EmvSession, data: ByteArray) {
        if (data.isEmpty()) return
        
        try {
            // Check first byte
            when (data[0].toInt() and 0xFF) {
                0x80 -> {
                    // Format 1: Primitive data
                    if (data.size >= 3) {
                        val length = data[1].toInt() and 0xFF
                        if (data.size >= 2 + length) {
                            val value = data.copyOfRange(2, 2 + length)
                            
                            // First 2 bytes are AIP
                            if (value.size >= 2) {
                                val aip = value.copyOfRange(0, 2)
                                session.tlvDatabase["82"] = aip
                                session.aipValue = ((aip[0].toInt() and 0xFF) shl 8) or (aip[1].toInt() and 0xFF)
                                Timber.i("Proxmark3EmvReader", "  AIP (82): ${aip.toHexString()}")
                            }
                            
                            // Remaining bytes are AFL
                            if (value.size > 2) {
                                val afl = value.copyOfRange(2, value.size)
                                session.tlvDatabase["94"] = afl
                                Timber.i("Proxmark3EmvReader", "  AFL (94): ${afl.toHexString()}")
                            }
                        }
                    }
                }
                
                0x77 -> {
                    // Format 2: TLV format
                    parseTlvIntoSession(session, data)
                }
                
                else -> {
                    // Try parsing as TLV
                    parseTlvIntoSession(session, data)
                }
            }
            
        } catch (e: Exception) {
            Timber.e("Proxmark3EmvReader", "Error processing GPO response: ${e.message}")
        }
    }
    
    // ========================================
    // STEP 5: READ AFL RECORDS
    // ========================================
    
    /**
     * Read AFL Records
     * 
     * Mimics: AFL reading loop from cmdemv.c line 1650-1720
     * 
     * AFL Structure (tag 94): 4-byte entries
     * - Byte 1: SFI (bits 7-3) | 00100b
     * - Byte 2: First record number
     * - Byte 3: Last record number
     * - Byte 4: Number of records for offline auth
     * 
     * APDU: 00 B2 [record] [SFI<<3|04]
     */
    private suspend fun readAflRecords(isoDep: IsoDep, session: EmvSession) {
        val aflData = session.tlvDatabase["94"]
        if (aflData == null || aflData.isEmpty()) {
            Timber.w("Proxmark3EmvReader", "AFL not found")
            return
        }
        
        if (aflData.size % 4 != 0) {
            Timber.e("Proxmark3EmvReader", "Wrong AFL length: ${aflData.size}")
            return
        }
        
        // Read all AFL entries
        for (i in 0 until aflData.size / 4) {
            val sfi = (aflData[i * 4].toInt() and 0xFF) shr 3
            val startRec = aflData[i * 4 + 1].toInt() and 0xFF
            val endRec = aflData[i * 4 + 2].toInt() and 0xFF
            val offlineRec = aflData[i * 4 + 3].toInt() and 0xFF
            
            Timber.i("Proxmark3EmvReader", "* * SFI[$sfi] start:$startRec end:$endRec offline:$offlineRec")
            
            if (sfi == 0 || sfi == 31 || startRec == 0 || startRec > endRec) {
                Timber.e("Proxmark3EmvReader", "SFI ERROR! Skipped...")
                continue
            }
            
            // Read each record in range
            for (recNum in startRec..endRec) {
                Timber.i("Proxmark3EmvReader", "* * * SFI[$sfi] $recNum")
                
                val readRecordCommand = byteArrayOf(
                    0x00, 0xB2.toByte(),
                    recNum.toByte(),
                    ((sfi shl 3) or 0x04).toByte(),
                    0x00
                )
                
                val response = transceive(isoDep, readRecordCommand, session, "READ RECORD SFI=$sfi REC=$recNum")
                if (response == null || response.size < 2) {
                    continue
                }
                
                val sw1 = response[response.size - 2].toInt() and 0xFF
                val sw2 = response[response.size - 1].toInt() and 0xFF
                
                if (sw1 != 0x90 || sw2 != 0x00) {
                    Timber.e("Proxmark3EmvReader", "Error SFI[$sfi]. APDU error $sw1$sw2")
                    continue
                }
                
                // Parse record data
                val data = response.copyOf(response.size - 2)
                parseTlvIntoSession(session, data)
                
                // Check if PAN found
                session.tlvDatabase["5A"]?.let { pan ->
                    session.pan = pan.toHexString()
                    Timber.i("Proxmark3EmvReader", "      PAN: ${session.pan}")
                }
                
                // Check if Track2 found
                session.tlvDatabase["57"]?.let { track2 ->
                    session.track2 = track2.toHexString()
                    Timber.i("Proxmark3EmvReader", "      Track2: ${session.track2}")
                }
            }
        }
    }
    
    // ========================================
    // STEP 6: OFFLINE DATA AUTHENTICATION
    // ========================================
    
    /**
     * Perform Offline Data Authentication
     * 
     * Mimics: Offline authentication section from cmdemv.c line 1730-1760
     * 
     * Checks AIP (tag 82) to determine authentication method:
     * - Bit 7 of byte 1 (0x4000): SDA supported
     * - Bit 6 of byte 1 (0x2000): DDA supported
     * - Bit 1 of byte 1 (0x0001): CDA supported
     */
    private suspend fun performOfflineAuthentication(isoDep: IsoDep, session: EmvSession) {
        val aipData = session.tlvDatabase["82"]
        if (aipData == null || aipData.size < 2) {
            Timber.w("Proxmark3EmvReader", "AIP not found, skipping authentication")
            return
        }
        
        val aip = ((aipData[0].toInt() and 0xFF) shl 8) or (aipData[1].toInt() and 0xFF)
        session.aipValue = aip
        
        when {
            (aip and 0x4000) != 0 -> {
                // SDA supported
                Timber.i("Proxmark3EmvReader", "* * SDA (Static Data Authentication)")
                session.authMethod = "SDA"
                performSda(session)
            }
            
            (aip and 0x2000) != 0 -> {
                // DDA supported
                Timber.i("Proxmark3EmvReader", "* * DDA (Dynamic Data Authentication)")
                session.authMethod = "DDA"
                performDda(isoDep, session)
            }
            
            (aip and 0x0001) != 0 -> {
                // CDA supported
                Timber.i("Proxmark3EmvReader", "* * CDA (Combined Data Authentication)")
                session.authMethod = "CDA"
                // CDA is verified during GENERATE AC
            }
            
            else -> {
                Timber.i("Proxmark3EmvReader", "* * No offline authentication")
                session.authMethod = "NONE"
            }
        }
    }
    
    /**
     * Perform SDA (Static Data Authentication)
     * 
     * Mimics: trSDA() from cmdemv.c
     * 
     * Verifies static signature over card data using issuer public key.
     * For now, just logs that SDA would be performed.
     */
    private fun performSda(session: EmvSession) {
        Timber.i("Proxmark3EmvReader", "  SDA verification (signature check)")
        // Full SDA implementation would verify:
        // 1. Recover issuer public key from certificate (tag 90)
        // 2. Verify signed static application data (tag 93)
        // For nf-sp00f33r, we just log that we detected SDA support
    }
    
    /**
     * Perform DDA (Dynamic Data Authentication)
     * 
     * Mimics: trDDA() from cmdemv.c line 2100-2200
     * 
     * Steps:
     * 1. GET CHALLENGE (0x0084)
     * 2. Get DDOL from card (tag 9F49)
     * 3. Build DDOL data using dol_process()
     * 4. INTERNAL AUTHENTICATE (0x0088)
     * 5. Parse SDAD (Signed Dynamic Application Data - tag 9F4B)
     */
    private suspend fun performDda(isoDep: IsoDep, session: EmvSession) {
        // Step 1: GET CHALLENGE
        Timber.i("Proxmark3EmvReader", "* * Generate challenge")
        val challengeCommand = byteArrayOf(0x00, 0x84.toByte(), 0x00, 0x00, 0x00)
        
        val challengeResponse = transceive(isoDep, challengeCommand, session, "GET CHALLENGE")
        if (challengeResponse == null || challengeResponse.size < 2) {
            Timber.w("Proxmark3EmvReader", "GET CHALLENGE failed")
            return
        }
        
        val sw1 = challengeResponse[challengeResponse.size - 2].toInt() and 0xFF
        val sw2 = challengeResponse[challengeResponse.size - 1].toInt() and 0xFF
        
        if (sw1 != 0x90 || sw2 != 0x00) {
            Timber.w("Proxmark3EmvReader", "GET CHALLENGE error: $sw1$sw2")
            return
        }
        
        val challenge = challengeResponse.copyOf(challengeResponse.size - 2)
        Timber.i("Proxmark3EmvReader", "  Challenge: ${challenge.toHexString()}")
        
        // Step 2: Get DDOL
        val ddolData = session.tlvDatabase["9F49"]
        if (ddolData == null) {
            Timber.w("Proxmark3EmvReader", "DDOL [9F49] not found")
            return
        }
        
        Timber.i("Proxmark3EmvReader", "  DDOL (9F49): ${ddolData.toHexString()}")
        
        // Step 3: Build DDOL data using dol_process()
        val ddolTags = parseDol(ddolData)
        val ddolDataProcessed = dolProcess(ddolTags, session)
        
        Timber.i("Proxmark3EmvReader", "  DDOL data[${ddolDataProcessed.size}]: ${ddolDataProcessed.toHexString()}")
        
        // Step 4: INTERNAL AUTHENTICATE
        Timber.i("Proxmark3EmvReader", "* * Internal Authenticate")
        val internalAuthCommand = byteArrayOf(
            0x00, 0x88.toByte(), 0x00, 0x00,
            ddolDataProcessed.size.toByte()
        ) + ddolDataProcessed + byteArrayOf(0x00)
        
        val authResponse = transceive(isoDep, internalAuthCommand, session, "INTERNAL AUTHENTICATE")
        if (authResponse == null || authResponse.size < 2) {
            Timber.w("Proxmark3EmvReader", "INTERNAL AUTHENTICATE failed")
            return
        }
        
        val authSw1 = authResponse[authResponse.size - 2].toInt() and 0xFF
        val authSw2 = authResponse[authResponse.size - 1].toInt() and 0xFF
        
        if (authSw1 != 0x90 || authSw2 != 0x00) {
            Timber.w("Proxmark3EmvReader", "INTERNAL AUTHENTICATE error: $authSw1$authSw2")
            return
        }
        
        // Step 5: Parse SDAD
        val authData = authResponse.copyOf(authResponse.size - 2)
        parseTlvIntoSession(session, authData)
        
        session.tlvDatabase["9F4B"]?.let { sdad ->
            Timber.i("Proxmark3EmvReader", "  SDAD (9F4B): ${sdad.toHexString()}")
        }
    }
    
    // ========================================
    // STEP 7: GENERATE AC (ARQC)
    // ========================================
    
    /**
     * Perform GENERATE AC (Application Cryptogram)
     * 
     * Mimics: EMVAC() from emvcore.c line 400
     * 
     * APDU: 80 AE [RefControl] 00 [len] [CDOL1-data]
     * 
     * RefControl (P1):
     * - 0x80: ARQC (Authorization Request - go online)
     * - 0x40: TC (Transaction Certificate - offline approved)
     * - 0x00: AAC (Application Authentication Cryptogram - offline declined)
     */
    private suspend fun performGenerateAc(
        isoDep: IsoDep,
        session: EmvSession,
        params: TransactionParameters
    ) {
        // Step 1: Get CDOL1
        val cdol1Data = session.tlvDatabase["8C"]
        if (cdol1Data == null || cdol1Data.isEmpty()) {
            Timber.w("Proxmark3EmvReader", "CDOL1 [8C] not found. Skipping GENERATE AC.")
            return
        }
        
        Timber.i("Proxmark3EmvReader", "  CDOL1 (8C): ${cdol1Data.toHexString()}")
        
        // Step 2: Build CDOL1 data using dol_process()
        val cdol1Tags = parseDol(cdol1Data)
        val cdol1DataProcessed = dolProcess(cdol1Tags, session)
        
        Timber.i("Proxmark3EmvReader", "  CDOL1 data[${cdol1DataProcessed.size}]: ${cdol1DataProcessed.toHexString()}")
        
        // Step 3: Build GENERATE AC command
        val generateAcCommand = byteArrayOf(
            0x80.toByte(), 0xAE.toByte(),
            0x80.toByte(),  // P1: ARQC (Authorization Request)
            0x00,
            cdol1DataProcessed.size.toByte()
        ) + cdol1DataProcessed + byteArrayOf(0x00)
        
        // Step 4: Send GENERATE AC
        val response = transceive(isoDep, generateAcCommand, session, "GENERATE AC (ARQC)")
        if (response == null || response.size < 2) {
            return
        }
        
        val sw1 = response[response.size - 2].toInt() and 0xFF
        val sw2 = response[response.size - 1].toInt() and 0xFF
        
        if (sw1 != 0x90 || sw2 != 0x00) {
            Timber.w("Proxmark3EmvReader", "GENERATE AC error: $sw1$sw2")
            return
        }
        
        // Step 5: Parse response
        val data = response.copyOf(response.size - 2)
        parseTlvIntoSession(session, data)
        
        // Extract ARQC (tag 9F26)
        session.tlvDatabase["9F26"]?.let { arqc ->
            session.arqc = arqc
            Timber.i("Proxmark3EmvReader", "  ARQC (9F26): ${arqc.toHexString()}")
        }
        
        // Extract ATC (tag 9F36)
        session.tlvDatabase["9F36"]?.let { atc ->
            session.atc = atc
            Timber.i("Proxmark3EmvReader", "  ATC (9F36): ${atc.toHexString()}")
        }
        
        // Extract CID (tag 9F27)
        session.tlvDatabase["9F27"]?.let { cid ->
            val cidValue = cid[0].toInt() and 0xFF
            val cidType = when (cidValue and 0xC0) {
                0x80 -> "ARQC (Authorization Request)"
                0x40 -> "TC (Transaction Certificate)"
                0x00 -> "AAC (Application Authentication Cryptogram)"
                else -> "Unknown"
            }
            Timber.i("Proxmark3EmvReader", "  CID (9F27): ${cid.toHexString()} - $cidType")
        }
    }
    
    // ========================================
    // DOL PROCESSING (Proxmark3's dol_process)
    // ========================================
    
    /**
     * DOL Processing - Core of Proxmark3's dynamic EMV handling
     * 
     * Mimics: dol_process() from emvcore.c
     * 
     * This is THE MAGIC FUNCTION that makes Proxmark3 work for all cards.
     * 
     * How it works:
     * 1. Parse DOL tag list from card (e.g., "9F37 04 9A 03 9C 01")
     * 2. Look up each tag in tlvDatabase (transaction parameters)
     * 3. Concatenate values in order
     * 4. Return complete data buffer
     * 
     * Example:
     * Input DOL: 9F37 04 9A 03 9C 01
     * Means: Tag 9F37 (4 bytes) + Tag 9A (3 bytes) + Tag 9C (1 byte)
     * 
     * Output: [9F37 value 4 bytes][9A value 3 bytes][9C value 1 byte]
     * 
     * @param dolTags List of (tag, length) pairs from DOL
     * @param session Session containing tlvDatabase with transaction parameters
     * @return Concatenated data buffer ready for GPO/GENERATE AC
     */
    private fun dolProcess(dolTags: List<Pair<String, Int>>, session: EmvSession): ByteArray {
        val result = mutableListOf<Byte>()
        
        for ((tag, length) in dolTags) {
            // Look up tag in transaction parameters
            val value = session.tlvDatabase[tag]
            
            if (value != null) {
                // Use actual value, pad or truncate to required length
                if (value.size >= length) {
                    result.addAll(value.take(length).toList())
                } else {
                    // Pad with zeros
                    result.addAll(value.toList())
                    repeat(length - value.size) {
                        result.add(0x00)
                    }
                }
                
                Timber.d("Proxmark3EmvReader", "    DOL: $tag [$length] = ${value.toHexString()}")
            } else {
                // Tag not found, use zeros
                repeat(length) {
                    result.add(0x00)
                }
                Timber.d("Proxmark3EmvReader", "    DOL: $tag [$length] = (not found, using zeros)")
            }
        }
        
        return result.toByteArray()
    }
    
    /**
     * Parse DOL (Data Object List)
     * 
     * DOL format: [tag][length][tag][length]...
     * 
     * Example: 9F37 04 9A 03 9C 01
     * Result: [(9F37, 4), (9A, 3), (9C, 1)]
     */
    private fun parseDol(dolData: ByteArray): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        var i = 0
        
        while (i < dolData.size) {
            // Read tag
            var tag = (dolData[i].toInt() and 0xFF)
            var tagBytes = 1
            
            // Check if multi-byte tag
            if ((tag and 0x1F) == 0x1F) {
                // Multi-byte tag
                if (i + 1 < dolData.size) {
                    tag = (tag shl 8) or (dolData[i + 1].toInt() and 0xFF)
                    tagBytes = 2
                }
            }
            
            i += tagBytes
            
            // Read length
            if (i >= dolData.size) break
            val length = dolData[i].toInt() and 0xFF
            i++
            
            // Convert tag to hex string
            val tagHex = if (tagBytes == 1) {
                String.format("%02X", tag)
            } else {
                String.format("%04X", tag)
            }
            
            result.add(Pair(tagHex, length))
        }
        
        return result
    }
    
    // ========================================
    // HELPER FUNCTIONS
    // ========================================
    
    /**
     * Get Card Vendor from AID
     * 
     * Mimics: GetCardPSVendor() from emvcore.c line 138-160
     */
    private fun getCardVendor(aid: ByteArray): CardVendor {
        val aidHex = aid.toHexString()
        
        return when {
            aidHex.startsWith("A000000003") -> CardVendor.CV_VISA
            aidHex.startsWith("A00000000401") || aidHex.startsWith("A00000000410") -> CardVendor.CV_MASTERCARD
            aidHex.startsWith("A000000025") -> CardVendor.CV_AMERICANEXPRESS
            aidHex.startsWith("A00000006510") -> CardVendor.CV_JCB
            aidHex.startsWith("A00000015230") -> CardVendor.CV_DISCOVER
            aidHex.startsWith("A00000015240") -> CardVendor.CV_DINERS
            else -> CardVendor.CV_OTHER
        }
    }
    
    /**
     * Extract PAN from Track2 if not present
     * 
     * Mimics: GetPANFromTrack2() from cmdemv.c
     */
    private fun extractPanFromTrack2(session: EmvSession) {
        if (session.pan != null) return
        
        session.tlvDatabase["57"]?.let { track2 ->
            try {
                val track2Hex = track2.toHexString()
                val separatorIndex = track2Hex.indexOf('D')
                
                if (separatorIndex > 0) {
                    val pan = track2Hex.substring(0, separatorIndex)
                    session.pan = pan
                    
                    // Extract expiry date (YYMM after separator)
                    if (separatorIndex + 4 < track2Hex.length) {
                        val expiry = track2Hex.substring(separatorIndex + 1, separatorIndex + 5)
                        session.expiryDate = expiry
                    }
                    
                    Timber.i("Proxmark3EmvReader", "  Extracted PAN from Track2: $pan")
                    session.expiryDate?.let {
                        Timber.i("Proxmark3EmvReader", "  Extracted Expiry: $it")
                    }
                }
            } catch (e: Exception) {
                Timber.e("Proxmark3EmvReader", "Error extracting PAN from Track2: ${e.message}")
            }
        }
    }
    
    /**
     * Parse TLV data and store in session
     */
    private fun parseTlvIntoSession(session: EmvSession, data: ByteArray) {
        try {
            val result = EmvTlvParser.parseEmvTlvData(data, "SESSION")
            
            for ((tag, valueHex) in result.tags) {
                session.tlvDatabase[tag] = valueHex.hexToByteArray()
            }
        } catch (e: Exception) {
            Timber.e("Proxmark3EmvReader", "Error parsing TLV: ${e.message}")
        }
    }
    
    /**
     * Transceive APDU and log
     */
    private suspend fun transceive(
        isoDep: IsoDep,
        command: ByteArray,
        session: EmvSession,
        description: String
    ): ByteArray? {
        return try {
            Timber.d("Proxmark3EmvReader", ">>> $description")
            Timber.d("Proxmark3EmvReader", "    ${command.toHexString()}")
            
            val response = isoDep.transceive(command)
            
            val sw = if (response.size >= 2) {
                String.format("%02X%02X", 
                    response[response.size - 2].toInt() and 0xFF,
                    response[response.size - 1].toInt() and 0xFF
                )
            } else {
                "NONE"
            }
            
            Timber.d("Proxmark3EmvReader", "<<< SW=$sw [${response.size} bytes]")
            Timber.d("Proxmark3EmvReader", "    ${response.toHexString()}")
            
            session.apduLog.add(ApduEntry(command, response, sw, description))
            
            response
        } catch (e: Exception) {
            Timber.e("Proxmark3EmvReader", "Transceive error: ${e.message}")
            null
        }
    }
    
    // ========================================
    // UTILITY FUNCTIONS
    // ========================================
    
    companion object {
        /**
         * Get current date in YYMMDD format (tag 9A)
         */
        fun getCurrentDate(): ByteArray {
            val dateFormat = SimpleDateFormat("yyMMdd", Locale.US)
            val dateStr = dateFormat.format(Date())
            return dateStr.hexToByteArray()
        }
        
        /**
         * Get unpredictable number (tag 9F37) - 4 bytes random
         */
        fun getUnpredictableNumber(): ByteArray {
            val random = SecureRandom()
            return ByteArray(4).apply { random.nextBytes(this) }
        }
    }
}

// ========================================
// EXTENSION FUNCTIONS
// ========================================

fun ByteArray.toHexString(): String {
    return this.joinToString("") { "%02X".format(it) }
}

fun String.hexToByteArray(): ByteArray {
    val cleanHex = this.replace(" ", "").replace(":", "")
    return ByteArray(cleanHex.length / 2) { i ->
        cleanHex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
