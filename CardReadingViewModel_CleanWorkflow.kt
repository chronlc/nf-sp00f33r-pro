// CLEAN EMV WORKFLOW IMPLEMENTATION
// Generated: October 12, 2025
// Following 7-Phase Universal Laws
// To be integrated into CardReadingViewModel.kt

/**
 * Execute complete Proxmark3 Iceman EMV workflow - CLEAN IMPLEMENTATION
 * PPSE → AID Selection → GPO → Record Reading → Generate AC → GET DATA → Transaction Logs
 * 
 * This is a modular, clean rewrite of the previous 1665-line monolithic function.
 * Each phase is extracted into a separate function for maintainability and testability.
 */
private suspend fun executeProxmark3EmvWorkflow(tag: android.nfc.Tag) {
    // Initialize session
    val cardId = tag.id.joinToString("") { "%02X".format(it) }
    currentSessionId = UUID.randomUUID().toString()
    sessionStartTime = System.currentTimeMillis()
    currentSessionData = SessionScanData(
        sessionId = currentSessionId,
        scanStartTime = sessionStartTime,
        cardUid = cardId
    )
    
    // Clear previous state
    apduLog = emptyList()
    parsedEmvFields = emptyMap()
    EmvTlvParser.clearRocaAnalysisResults()
    rocaVulnerabilityStatus = "Analyzing..."
    isRocaVulnerable = false
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("STARTING CLEAN EMV WORKFLOW - Session $currentSessionId")
    Timber.i("Card UID: $cardId")
    Timber.i("="  + "=".repeat(79))
    
    // Connect to card
    val isoDep = android.nfc.tech.IsoDep.get(tag)
    if (isoDep == null) {
        withContext(Dispatchers.Main) {
            statusMessage = "Card does not support ISO-DEP"
            scanState = ScanState.ERROR
        }
        Timber.e("Card does not support ISO-DEP protocol")
        return
    }
    
    isoDep.connect()
    
    try {
        // Phase 1: PPSE Selection
        val aidEntries = executePhase1_PpseSelection(isoDep)
        if (aidEntries.isEmpty()) {
            Timber.w("No AIDs found - aborting workflow")
            return
        }
        
        // Phase 2: AID Selection
        val selectedAid = executePhase2_AidSelection(isoDep, aidEntries)
        if (selectedAid.isEmpty()) {
            Timber.w("No AID selected successfully - aborting workflow")
            return
        }
        
        // Phase 3: GPO (Get Processing Options)
        val afl = executePhase3_Gpo(isoDep)
        
        // Phase 4: Read Records
        executePhase4_ReadRecords(isoDep, afl)
        
        // Phase 5: Generate AC
        executePhase5_GenerateAc(isoDep)
        
        // Phase 6: GET DATA
        val logFormat = executePhase6_GetData(isoDep)
        
        // Phase 7: Transaction Logs
        executePhase7_TransactionLogs(isoDep, logFormat)
        
        // Finalize Session
        finalizeSession(cardId, tag)
        
    } catch (e: Exception) {
        Timber.e(e, "EMV workflow error")
        withContext(Dispatchers.Main) {
            statusMessage = "Error: ${e.message}"
            scanState = ScanState.ERROR
        }
        currentSessionData?.scanStatus = "ERROR"
        currentSessionData?.errorMessage = e.message
    } finally {
        isoDep.close()
    }
}

/**
 * Phase 1: PPSE Selection - Try 2PAY/1PAY, parse AIDs
 */
private suspend fun executePhase1_PpseSelection(isoDep: android.nfc.tech.IsoDep): List<AidEntry> {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 1: PPSE"
        progress = 0.1f
        statusMessage = "SELECT PPSE..."
    }
    
    // Try PPSE selection (2PAY → 1PAY fallback if auto mode)
    val ppseCommand = if (forceContactMode) {
        "315041592E5359532E4444463031" // 1PAY.SYS.DDF01
    } else {
        "325041592E5359532E4444463031" // 2PAY.SYS.DDF01
    }
    
    var ppseResponse = sendPpseCommand(isoDep, ppseCommand)
    var statusWord = ppseResponse.first
    var responseHex = ppseResponse.second
    
    // Fallback to 1PAY if 2PAY failed and not in force mode
    if (statusWord != "9000" && !forceContactMode) {
        Timber.d("2PAY failed, trying 1PAY fallback")
        ppseResponse = sendPpseCommand(isoDep, "315041592E5359532E4444463031")
        statusWord = ppseResponse.first
        responseHex = ppseResponse.second
    }
    
    if (statusWord != "9000") {
        Timber.w("PPSE selection failed: SW=$statusWord")
        return emptyList()
    }
    
    // Parse AIDs from PPSE response
    val ppseBytes = responseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val ppseParseResult = EmvTlvParser.parseResponse(ppseBytes, "PPSE")
    
    currentSessionData?.ppseResponse = ppseParseResult.tags
    currentSessionData?.allTags?.putAll(ppseParseResult.tags)
    
    val aidEntries = ppseParseResult.tags.filter { it.key == "4F" }.map { (_, enrichedTag) ->
        val label = ppseParseResult.tags["50"]?.valueDecoded ?: "Unknown"
        val priority = ppseParseResult.tags["87"]?.value?.toIntOrNull(16) ?: 0
        AidEntry(enrichedTag.value, label, priority)
    }
    
    Timber.i("PPSE: ${aidEntries.size} AIDs found")
    return aidEntries
}

private suspend fun sendPpseCommand(isoDep: android.nfc.tech.IsoDep, ppseHex: String): Pair<String, String> {
    val ppseBytes = ppseHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    val command = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, ppseBytes.size.toByte()) + ppseBytes + byteArrayOf(0x00)
    val response = isoDep.transceive(command)
    val responseHex = response.joinToString("") { "%02X".format(it) }
    val statusWord = if (responseHex.length >= 4) responseHex.takeLast(4) else "UNKNOWN"
    addApduLogEntry(command.joinToString("") { "%02X".format(it) }, responseHex, statusWord, "SELECT PPSE", 0L)
    return Pair(statusWord, responseHex)
}

/**
 * Phase 2: Multi-AID Selection
 * Try all AIDs from PPSE response, return first successful AID
 */
private suspend fun executePhase2_AidSelection(
    isoDep: android.nfc.tech.IsoDep,
    aidEntries: List<AidEntry>
): String {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 2: Multi-AID Selection"
        progress = 0.2f
        statusMessage = "Analyzing all applications..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 2: MULTI-AID SELECTION")
    Timber.i("Processing ${aidEntries.size} AIDs from PPSE")
    Timber.i("="  + "=".repeat(79))
    
    var successfulAids = 0
    var failedAids = 0
    var selectedAid = ""
    
    for ((aidIndex, aidEntry) in aidEntries.withIndex()) {
        val aidHexString = aidEntry.aid
        val aidLabel = aidEntry.label
        val aidPriority = aidEntry.priority
        
        withContext(Dispatchers.Main) {
            statusMessage = "AID ${aidIndex + 1}/${aidEntries.size}: $aidLabel"
        }
        
        val aid = aidHexString.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val aidCommand = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()
        ) + aid + byteArrayOf(0x00)
        val aidResponse = isoDep.transceive(aidCommand)
        
        val aidHex = aidResponse.joinToString("") { "%02X".format(it) }
        val statusWord = if (aidHex.length >= 4) aidHex.takeLast(4) else "UNKNOWN"
        
        addApduLogEntry(
            "00A40400" + String.format("%02X", aid.size) + aidHexString,
            aidHex,
            statusWord,
            "SELECT AID #${aidIndex + 1}: $aidLabel",
            0L
        )
        
        when (statusWord) {
            "9000" -> {
                successfulAids++
                
                // Parse AID response
                val aidBytes = aidHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                val aidParseResult = EmvTlvParser.parseResponse(aidBytes, "SELECT_AID", aidLabel)
                
                // Store parsed AID data in session
                currentSessionData?.aidResponses?.add(aidParseResult.tags)
                currentSessionData?.allTags?.putAll(aidParseResult.tags)
                
                withContext(Dispatchers.Main) {
                    statusMessage = "✓ AID ${aidIndex + 1}/${aidEntries.size}: $aidLabel (Priority $aidPriority)"
                }
                Timber.i("✅ AID #${aidIndex + 1} selected: $aidLabel ($aidHexString), ${aidParseResult.tags.size} tags parsed")
                
                // Store first successful AID for GPO
                if (selectedAid.isEmpty()) {
                    selectedAid = aidHexString
                }
            }
            "6A82" -> {
                failedAids++
                withContext(Dispatchers.Main) {
                    statusMessage = "✗ AID ${aidIndex + 1}/${aidEntries.size}: $aidLabel (Not Found)"
                }
                Timber.d("✗ AID #${aidIndex + 1} not found: $aidLabel")
            }
            "6A81" -> {
                failedAids++
                withContext(Dispatchers.Main) {
                    statusMessage = "✗ AID ${aidIndex + 1}/${aidEntries.size}: $aidLabel (Not Supported)"
                }
                Timber.d("✗ AID #${aidIndex + 1} not supported: $aidLabel")
            }
            else -> {
                failedAids++
                withContext(Dispatchers.Main) {
                    statusMessage = "✗ AID ${aidIndex + 1}/${aidEntries.size}: $aidLabel ($statusWord)"
                }
                Timber.w("✗ AID #${aidIndex + 1} failed: $aidLabel with SW=$statusWord")
            }
        }
    }
    
    withContext(Dispatchers.Main) {
        statusMessage = "Multi-AID complete: $successfulAids/${aidEntries.size} selected"
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 2 COMPLETE")
    Timber.i("  Successful: $successfulAids / ${aidEntries.size}")
    Timber.i("  Failed: $failedAids / ${aidEntries.size}")
    Timber.i("  Selected for GPO: $selectedAid")
    Timber.i("="  + "=".repeat(79))
    
    return selectedAid
}

/**
 * Phase 3: GPO (Get Processing Options)
 * Build PDOL data dynamically and execute GPO command
 * Returns AFL (Application File Locator) for record reading
 */
private suspend fun executePhase3_Gpo(isoDep: android.nfc.tech.IsoDep): String {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 3: GPO"
        progress = 0.4f
        statusMessage = "GET PROCESSING OPTIONS..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 3: GET PROCESSING OPTIONS (GPO)")
    Timber.i("="  + "=".repeat(79))
    
    // Extract PDOL from AID selection response
    val pdolData = extractPdolFromAllResponses(apduLog)
    Timber.i("PDOL raw data: $pdolData")
    
    val gpoData = if (pdolData.isNotEmpty()) {
        val dolEntries = EmvTlvParser.parseDol(pdolData)
        Timber.i("PDOL parsed ${dolEntries.size} entries:")
        dolEntries.forEach { entry ->
            Timber.i("  Tag ${entry.tag} (${entry.tagName}): expects ${entry.length} bytes")
        }
        buildPdolData(dolEntries)
    } else {
        Timber.w("No PDOL found - using empty data")
        byteArrayOf(0x83.toByte(), 0x00)
    }
    
    Timber.i("GPO data to send: ${gpoData.joinToString("") { "%02X".format(it) }} (${gpoData.size} bytes)")
    val gpoCommand = byteArrayOf(
        0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, gpoData.size.toByte()
    ) + gpoData + byteArrayOf(0x00)
    
    val gpoResponse = isoDep.transceive(gpoCommand)
    val gpoHex = gpoResponse.joinToString("") { "%02X".format(it) }
    val statusWord = if (gpoHex.length >= 4) gpoHex.takeLast(4) else "UNKNOWN"
    
    addApduLogEntry(
        gpoCommand.joinToString("") { "%02X".format(it) },
        gpoHex,
        statusWord,
        "GET PROCESSING OPTIONS",
        0L
    )
    
    var afl = ""
    
    if (statusWord == "9000") {
        val gpoBytes = gpoHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val gpoParseResult = EmvTlvParser.parseResponse(gpoBytes, "GPO")
        
        // Store parsed GPO data in session
        currentSessionData?.gpoResponse = gpoParseResult.tags
        currentSessionData?.allTags?.putAll(gpoParseResult.tags)
        
        // Extract key fields
        val extractedPan = gpoParseResult.tags["5A"]?.value ?: ""
        val aip = gpoParseResult.tags["82"]?.value ?: ""
        afl = gpoParseResult.tags["94"]?.value ?: ""
        
        Timber.i("✅ GPO parsed ${gpoParseResult.tags.size} tags")
        Timber.i("  PAN: ${if (extractedPan.isNotEmpty()) "present" else "none"}")
        Timber.i("  AIP: $aip")
        Timber.i("  AFL: ${if (afl.isNotEmpty()) "${afl.length/2} bytes" else "none"}")
        
        // Analyze AIP for security
        if (aip.isNotEmpty()) {
            val securityInfo = analyzeAip(aip)
            Timber.i("  Security: ${securityInfo.summary}")
            
            withContext(Dispatchers.Main) {
                statusMessage = "GPO Success: ${securityInfo.summary}"
            }
        } else {
            withContext(Dispatchers.Main) {
                statusMessage = "GPO Success"
            }
        }
    } else {
        withContext(Dispatchers.Main) {
            statusMessage = "GPO Failed: SW=$statusWord"
        }
        Timber.w("❌ GPO failed with SW=$statusWord")
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 3 COMPLETE: AFL=${if (afl.isNotEmpty()) "present" else "none"}")
    Timber.i("="  + "=".repeat(79))
    
    return afl
}

/**
 * Phase 4: Read Records
 * Parse AFL and read all specified records
 */
private suspend fun executePhase4_ReadRecords(
    isoDep: android.nfc.tech.IsoDep,
    afl: String
) {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 4: Reading Records"
        progress = 0.6f
        statusMessage = "Reading application data..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 4: READ APPLICATION DATA")
    Timber.i("AFL: ${if (afl.isNotEmpty()) afl else "NONE (using fallback)"}")
    Timber.i("="  + "=".repeat(79))
    
    val recordsToRead = if (afl.isNotEmpty()) {
        val aflEntries = EmvTlvParser.parseAfl(afl)
        if (aflEntries.isNotEmpty()) {
            Timber.i("✓ AFL parsed successfully: ${aflEntries.size} entries")
            aflEntries.forEach { entry ->
                Timber.i("  SFI ${entry.sfi}: Records ${entry.startRecord}-${entry.endRecord}, Offline=${entry.offlineRecords}")
            }
            aflEntries.flatMap { entry ->
                (entry.startRecord..entry.endRecord).map { record ->
                    Triple(entry.sfi, record, (entry.sfi shl 3) or 0x04)
                }
            }
        } else {
            Timber.w("✗ AFL parsing failed - using fallback")
            listOf(
                Triple(1, 1, 0x14), Triple(1, 2, 0x14), Triple(1, 3, 0x14),
                Triple(2, 1, 0x0C), Triple(2, 2, 0x0C),
                Triple(3, 1, 0x1C), Triple(3, 2, 0x1C)
            )
        }
    } else {
        Timber.w("✗ No AFL found - using fallback record locations")
        listOf(
            Triple(1, 1, 0x14), Triple(1, 2, 0x14), Triple(1, 3, 0x14),
            Triple(2, 1, 0x0C), Triple(2, 2, 0x0C),
            Triple(3, 1, 0x1C), Triple(3, 2, 0x1C)
        )
    }
    
    Timber.i("Will read ${recordsToRead.size} total records")
    
    var recordsRead = 0
    var panFound = false
    
    for ((sfi, record, p2) in recordsToRead) {
        val readCommand = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2.toByte(), 0x00)
        val readResponse = isoDep.transceive(readCommand)
        
        val readHex = readResponse.joinToString("") { "%02X".format(it) }
        val statusWord = if (readHex.length >= 4) readHex.takeLast(4) else "UNKNOWN"
        
        addApduLogEntry(
            "00B2" + String.format("%02X%02X", record, p2) + "00",
            readHex,
            statusWord,
            "READ RECORD SFI $sfi Rec $record",
            0L
        )
        
        if (statusWord == "9000") {
            recordsRead++
            
            val readBytes = readHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val recordParseResult = EmvTlvParser.parseResponse(readBytes, "READ_RECORD", "SFI$sfi-REC$record")
            
            // Store parsed record data in session
            currentSessionData?.recordResponses?.add(recordParseResult.tags)
            currentSessionData?.allTags?.putAll(recordParseResult.tags)
            
            // Extract key fields
            val recordPan = recordParseResult.tags["5A"]?.value ?: ""
            val track2 = recordParseResult.tags["57"]?.value ?: ""
            
            // Update UI with detailed data
            val detailedData = recordParseResult.tags.mapNotNull { (tag, enriched) ->
                enriched.name.lowercase().replace(" ", "_") to (enriched.valueDecoded ?: enriched.value)
            }.toMap()
            parsedEmvFields = parsedEmvFields + detailedData
            
            Timber.i("✅ SFI $sfi Record $record parsed ${recordParseResult.tags.size} tags")
            
            if (recordPan.isNotEmpty()) {
                panFound = true
                Timber.i("  Found PAN in SFI $sfi Record $record")
            } else if (track2.isNotEmpty()) {
                Timber.i("  Found Track2 in SFI $sfi Record $record")
            }
        }
    }
    
    withContext(Dispatchers.Main) {
        statusMessage = "Records complete: $recordsRead read, PAN ${if (panFound) "found" else "not found"}"
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 4 COMPLETE: $recordsRead records read")
    Timber.i("="  + "=".repeat(79))
}

/**
 * Phase 5: Generate AC (Application Cryptogram)
 * Build CDOL data and generate cryptogram (ARQC/TC)
 */
private suspend fun executePhase5_GenerateAc(isoDep: android.nfc.tech.IsoDep) {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 5: Generate AC"
        progress = 0.75f
        statusMessage = "Generating cryptogram..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 5: GENERATE AC (Application Cryptogram)")
    Timber.i("="  + "=".repeat(79))
    
    // Check if cryptogram already obtained in GPO (Visa Quick VSDC)
    val existingCryptogram = extractCryptogramFromAllResponses(apduLog)
    if (existingCryptogram.isNotEmpty()) {
        Timber.i("✓ Cryptogram already obtained in GPO response (Visa Quick VSDC) - skipping GENERATE AC")
        withContext(Dispatchers.Main) {
            statusMessage = "Cryptogram already obtained: ${existingCryptogram.take(16)}..."
        }
        return
    }
    
    // Extract CDOL1 from records
    val cdol1Data = extractCdol1FromAllResponses(apduLog)
    Timber.i("CDOL1 raw data: '$cdol1Data' (${cdol1Data.length / 2} bytes)")
    
    val generateAcData = if (cdol1Data.length >= 4) {
        try {
            val cdolEntries = EmvTlvParser.parseDol(cdol1Data)
            if (cdolEntries.isNotEmpty()) {
                Timber.i("CDOL1 contains ${cdolEntries.size} entries")
                buildCdolData(cdolEntries)
            } else {
                Timber.w("CDOL1 parsed but no entries - using minimal GENERATE AC")
                byteArrayOf()
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse CDOL1: $cdol1Data")
            byteArrayOf()
        }
    } else {
        Timber.w("No valid CDOL1 found - using minimal GENERATE AC data")
        byteArrayOf()
    }
    
    val generateAcCommand = if (generateAcData.isNotEmpty()) {
        byteArrayOf(
            0x80.toByte(), 0xAE.toByte(), 0x80.toByte(), 0x00.toByte(), generateAcData.size.toByte()
        ) + generateAcData + byteArrayOf(0x00)
    } else {
        byteArrayOf(0x80.toByte(), 0xAE.toByte(), 0x80.toByte(), 0x00.toByte(), 0x00)
    }
    
    val generateAcResponse = isoDep.transceive(generateAcCommand)
    val generateAcHex = generateAcResponse.joinToString("") { "%02X".format(it) }
    val statusWord = if (generateAcHex.length >= 4) generateAcHex.takeLast(4) else "UNKNOWN"
    
    addApduLogEntry(
        generateAcCommand.joinToString("") { "%02X".format(it) },
        generateAcHex,
        statusWord,
        "GENERATE AC (ARQC)",
        0L
    )
    
    if (statusWord == "9000") {
        val generateAcBytes = generateAcHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val cryptogramParseResult = EmvTlvParser.parseResponse(generateAcBytes, "GENERATE_AC")
        
        // Store parsed cryptogram data in session
        currentSessionData?.cryptogramResponse = cryptogramParseResult.tags
        currentSessionData?.allTags?.putAll(cryptogramParseResult.tags)
        
        val arqc = cryptogramParseResult.tags["9F26"]?.value ?: ""
        val cid = cryptogramParseResult.tags["9F27"]?.value ?: ""
        val atc = cryptogramParseResult.tags["9F36"]?.value ?: ""
        
        withContext(Dispatchers.Main) {
            statusMessage = "GENERATE AC Success: ARQC=$arqc CID=$cid ATC=$atc"
        }
        Timber.i("✅ GENERATE AC parsed ${cryptogramParseResult.tags.size} tags - ARQC: $arqc, CID: $cid, ATC: $atc")
    } else {
        withContext(Dispatchers.Main) {
            statusMessage = "GENERATE AC Failed: SW=$statusWord"
        }
        Timber.w("❌ GENERATE AC failed with SW=$statusWord")
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 5 COMPLETE")
    Timber.i("="  + "=".repeat(79))
}

/**
 * Phase 6: GET DATA
 * Query additional EMV tags using GET DATA command
 * Returns log format value for Phase 7
 */
private suspend fun executePhase6_GetData(isoDep: android.nfc.tech.IsoDep): String {
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 6: GET DATA"
        progress = 0.85f
        statusMessage = "Querying additional EMV data..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 6: GET DATA PRIMITIVES")
    Timber.i("="  + "=".repeat(79))
    
    val getDataTags = listOf(
        "9F17" to "PIN Try Counter",
        "9F36" to "Application Transaction Counter (ATC)",
        "9F13" to "Last Online ATC Register",
        "9F4F" to "Log Format",
        "9F4D" to "Log Entry",
        "9F6E" to "Form Factor Indicator",
        "9F6D" to "Mag-stripe Track1 Data",
        "DF60" to "Proprietary Data 60",
        "DF61" to "Proprietary Data 61",
        "DF62" to "Proprietary Data 62"
    )
    
    var getDataSuccessCount = 0
    var logFormatValue = ""
    
    for ((tag, description) in getDataTags) {
        val getDataCommand = buildGetDataApdu(tag.toInt(16))
        val getDataResponse = isoDep.transceive(getDataCommand)
        
        val getDataHex = getDataResponse.joinToString("") { "%02X".format(it) }
        val statusWord = if (getDataHex.length >= 4) getDataHex.takeLast(4) else "UNKNOWN"
        
        if (statusWord == "9000") {
            getDataSuccessCount++
            
            addApduLogEntry(
                getDataCommand.joinToString("") { "%02X".format(it) },
                getDataHex,
                statusWord,
                "GET DATA $tag ($description)",
                0L
            )
            
            // Parse response
            val getDataBytes = getDataHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val getDataParseResult = EmvTlvParser.parseResponse(getDataBytes, "GET_DATA", tag)
            
            // Store in session
            if (currentSessionData?.getDataResponse == null) {
                currentSessionData?.getDataResponse = getDataParseResult.tags
            } else {
                currentSessionData?.getDataResponse = currentSessionData?.getDataResponse!! + getDataParseResult.tags
            }
            currentSessionData?.allTags?.putAll(getDataParseResult.tags)
            
            // Update UI
            val detailedData = getDataParseResult.tags.mapNotNull { (tagKey, enriched) ->
                enriched.name.lowercase().replace(" ", "_") to (enriched.valueDecoded ?: enriched.value)
            }.toMap()
            parsedEmvFields = parsedEmvFields + detailedData
            
            // Check for Log Format
            if (tag == "9F4F" && getDataParseResult.tags.containsKey("9F4F")) {
                logFormatValue = getDataParseResult.tags["9F4F"]?.value ?: ""
                Timber.i("✅ $description ($tag): $logFormatValue - TRANSACTION LOGS ENABLED")
            } else {
                val tagData = getDataParseResult.tags[tag]?.value ?: getDataHex.substring(0, minOf(getDataHex.length, 32))
                Timber.i("✅ $description ($tag): ${tagData.take(32)}${if (tagData.length > 32) "..." else ""}")
            }
        }
    }
    
    withContext(Dispatchers.Main) {
        statusMessage = "GET DATA complete: $getDataSuccessCount / ${getDataTags.size} tags"
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 6 COMPLETE: $getDataSuccessCount / ${getDataTags.size} GET DATA tags retrieved")
    Timber.i("="  + "=".repeat(79))
    
    return logFormatValue
}

/**
 * Phase 7: Transaction Logs
 * Read transaction history if log format found in Phase 6
 */
private suspend fun executePhase7_TransactionLogs(
    isoDep: android.nfc.tech.IsoDep,
    logFormat: String
) {
    if (logFormat.isEmpty()) {
        Timber.i("="  + "=".repeat(79))
        Timber.i("PHASE 7: TRANSACTION LOGS - SKIPPED (Log Format not found)")
        Timber.i("="  + "=".repeat(79))
        
        withContext(Dispatchers.Main) {
            statusMessage = "Transaction logs: Not supported by card"
        }
        return
    }
    
    withContext(Dispatchers.Main) {
        currentPhase = "Phase 7: Transaction Logs"
        progress = 0.92f
        statusMessage = "Reading transaction history..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 7: TRANSACTION LOG READING")
    Timber.i("Log Format (9F4F) found: $logFormat")
    Timber.i("="  + "=".repeat(79))
    
    try {
        val logFormatBytes = logFormat.chunked(2).map { it.toInt(16) }
        
        if (logFormatBytes.size >= 2) {
            val byte0 = logFormatBytes[0]
            val byte1 = logFormatBytes[1]
            
            val logSfi = (byte0 shr 3) and 0x1F
            val logRecordCount = byte1 and 0x1F
            
            Timber.i("Parsed Log Format: SFI=$logSfi, Record Count=$logRecordCount")
            
            if (logSfi in 1..31 && logRecordCount in 1..30) {
                var logsRead = 0
                val transactionLogs = mutableListOf<Map<String, EnrichedTagData>>()
                
                for (recordNum in 1..logRecordCount) {
                    val logP2 = (logSfi shl 3) or 0x04
                    val logReadCommand = byteArrayOf(0x00, 0xB2.toByte(), recordNum.toByte(), logP2.toByte(), 0x00)
                    val logReadResponse = isoDep.transceive(logReadCommand)
                    
                    val logReadHex = logReadResponse.joinToString("") { "%02X".format(it) }
                    val logStatusWord = if (logReadHex.length >= 4) logReadHex.takeLast(4) else "UNKNOWN"
                    
                    if (logStatusWord == "9000") {
                        logsRead++
                        
                        addApduLogEntry(
                            "00B2" + String.format("%02X%02X", recordNum, logP2) + "00",
                            logReadHex,
                            logStatusWord,
                            "READ TRANSACTION LOG #$recordNum",
                            0L
                        )
                        
                        // Parse log entry
                        val logBytes = logReadHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        val logParseResult = EmvTlvParser.parseResponse(logBytes, "TRANSACTION_LOG", "LOG$recordNum")
                        
                        transactionLogs.add(logParseResult.tags)
                        currentSessionData?.allTags?.putAll(logParseResult.tags)
                        
                        Timber.i("✅ Transaction Log #$recordNum: ${logParseResult.tags.size} tags")
                    } else if (logStatusWord == "6A83") {
                        Timber.i("Transaction log #$recordNum not found - end of logs")
                        break
                    }
                }
                
                Timber.i("TRANSACTION LOG READING COMPLETE: $logsRead / $logRecordCount logs read")
                
                withContext(Dispatchers.Main) {
                    statusMessage = "Transaction logs: $logsRead / $logRecordCount read ✅"
                }
                
                // Store transaction logs in session
                if (transactionLogs.isNotEmpty()) {
                    currentSessionData?.recordResponses?.addAll(transactionLogs)
                }
            }
        }
    } catch (e: Exception) {
        Timber.e(e, "Failed to parse Log Format: $logFormat")
        withContext(Dispatchers.Main) {
            statusMessage = "Transaction log parsing failed"
        }
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("PHASE 7 COMPLETE")
    Timber.i("="  + "=".repeat(79))
}

/**
 * Finalize Session
 * Create EmvCardData, save to database, update UI
 */
private suspend fun finalizeSession(cardId: String, tag: android.nfc.Tag) {
    withContext(Dispatchers.Main) {
        currentPhase = "Finalizing"
        progress = 1.0f
        statusMessage = "EMV scan complete - Saving data..."
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("FINALIZING SESSION")
    Timber.i("="  + "=".repeat(79))
    
    // Extract and create comprehensive EMV card data
    val extractedData = createEmvCardData(cardId, tag)
    
    // Create virtual card for carousel
    val virtualCard = VirtualCard(
        cardholderName = extractedData.cardholderName ?: "UNKNOWN",
        maskedPan = extractedData.getUnmaskedPan(),
        expiryDate = extractedData.expiryDate?.let { exp ->
            if (exp.length == 4) "${exp.substring(2, 4)}/${exp.substring(0, 2)}" else exp
        } ?: "MM/YY",
        apduCount = apduLog.size,
        cardType = extractedData.getCardBrandDisplayName(),
        isEncrypted = extractedData.hasEncryptedData(),
        lastUsed = "Just scanned",
        category = "EMV"
    )
    scannedCards = scannedCards + virtualCard
    
    // Save to database
    delay(500)
    saveSessionToDatabase()
    
    // Update UI with final state
    withContext(Dispatchers.Main) {
        parsedEmvFields = extractedData.emvTags
        statusMessage = "Card saved to database - Ready for next scan"
        scanState = ScanState.IDLE
        currentPhase = "Complete"
    }
    
    Timber.i("="  + "=".repeat(79))
    Timber.i("SESSION FINALIZED - ${apduLog.size} APDUs, ${extractedData.emvTags.size} tags")
    Timber.i("="  + "=".repeat(79))
}

/**
 * Build GET DATA APDU command
 */
private fun buildGetDataApdu(tag: Int): ByteArray {
    val tagByte1 = (tag shr 8) and 0xFF
    val tagByte2 = tag and 0xFF
    return byteArrayOf(
        0x80.toByte(),
        0xCA.toByte(),
        tagByte1.toByte(),
        tagByte2.toByte(),
        0x00
    )
}
