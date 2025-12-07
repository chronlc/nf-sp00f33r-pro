package com.nfsp00f33r.app.screens.cardreading.emv

import com.nfsp00f33r.app.data.ApduLogEntry
import com.nfsp00f33r.app.cardreading.EmvTagDictionary
import timber.log.Timber

/**
 * EMV Response Parser - Extract EMV Tags from APDU Response Data
 * 
 * **PURPOSE:**
 * This class consolidates all EMV tag extraction logic that was previously
 * duplicated across 20+ nearly-identical functions in CardReadingViewModel.
 * 
 * **WHAT IT DOES:**
 * - Searches through APDU response logs for specific EMV tags
 * - Extracts tag values using regex pattern matching  
 * - Handles TLV (Tag-Length-Value) structure parsing
 * - Converts hex data to human-readable formats where applicable
 * 
 * **WHY IT EXISTS:**
 * Original code had 20 functions with pattern: `extractXFromAllResponses()`
 * All used identical logic - only the EMV tag number changed
 * This was:
 *   - Hard to maintain (change regex format 20 times?)
 *   - Hard to understand (why 20 copies of same code?)
 *   - Violation of DRY (Don't Repeat Yourself) principle
 * 
 * **HOW TO USE:**
 * ```kotlin
 * val apduLog: List<ApduLogEntry> = ... // From EMV card scan
 * 
 * // Extract PAN (Primary Account Number) - Tag 5A
 * val pan = EmvResponseParser.extractTag(apduLog, "5A")
 * 
 * // Extract cardholder name - Tag 5F20 (ASCII decoded)
 * val name = EmvResponseParser.extractCardholderName(apduLog)
 * ```
 * 
 * **EMV TAG REFERENCE:**
 * Tags are standardized by EMVCo (Europay, Mastercard, Visa consortium)
 * Common tags extracted here:
 * - 5A: PAN (Primary Account Number / card number)
 * - 57: Track 2 Equivalent Data (mag stripe data)
 * - 5F20: Cardholder Name
 * - 5F24: Application Expiration Date (YYMMDD)
 * - 82: AIP (Application Interchange Profile - security capabilities)
 * - 94: AFL (Application File Locator - where to read data)
 * - 9F26: Application Cryptogram (ARQC/TC/AAC)
 * - 9F27: CID (Cryptogram Information Data)
 * - 9F36: ATC (Application Transaction Counter)
 * - ... and many more (see individual functions)
 * 
 * @see <a href="https://www.emvco.com/specifications/">EMVCo Specifications</a>
 * @see <a href="https://github.com/RfidResearchGroup/proxmark3/wiki/EMV">Proxmark3 EMV Guide</a>
 */
object EmvResponseParser {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CORE EXTRACTION LOGIC
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Generic EMV Tag Extractor - Core Function Used by All Others
     * 
     * **WHAT IT DOES:**
     * Searches through APDU response log for a specific EMV tag and extracts its value.
     * 
     * **HOW IT WORKS:**
     * 1. Loops through each APDU response in the log
     * 2. Builds regex pattern: TAG + LENGTH + VALUE
     *    Example for tag "5A": "5A([0-9A-F]{2})([0-9A-F]+)"
     *    - "5A" = tag identifier
     *    - ([0-9A-F]{2}) = 1-byte length field (2 hex chars)
     *    - ([0-9A-F]+) = value data (variable length)
     * 3. If match found, reads length byte to know how many chars to extract
     * 4. Returns extracted value (or empty string if not found)
     * 
     * **EMV TLV FORMAT:**
     * EMV uses TLV (Tag-Length-Value) encoding:
     * ```
     * Example: 5A 08 1234567890123456
     *          ^^=Tag  ^^=Length (8 bytes)  ^^^^^^^^=Value (16 hex chars = 8 bytes)
     * ```
     * 
     * **WHY GENERIC:**
     * Instead of 20 separate functions, we have ONE function that can extract
     * ANY tag by passing the tag number as a parameter. Much cleaner!
     * 
     * @param apduLog List of APDU command/response pairs from card communication
     * @param tagHex EMV tag to search for (e.g., "5A" for PAN, "57" for Track2)
     * @return Extracted tag value as hex string, or empty string if tag not found
     * 
     * @example
     * ```kotlin
     * // Extract PAN (tag 5A)
     * val pan = extractTag(apduLog, "5A")
     * // If found: "1234567890123456" (16-digit card number in hex)
     * // If not found: ""
     * ```
     */
    fun extractTag(apduLog: List<ApduLogEntry>, tagHex: String): String {
        // Loop through each APDU response
        apduLog.forEach { apdu ->
            // Build regex pattern for this specific tag
            // Pattern breakdown:
            //   - tagHex: The EMV tag we're looking for (e.g., "5A")
            //   - ([0-9A-F]{2}): Capture group 1 - length byte (2 hex chars)
            //   - ([0-9A-F]+): Capture group 2 - value data (any number of hex chars)
            val tagRegex = "$tagHex([0-9A-F]{2})([0-9A-F]+)".toRegex()
            
            // Try to find the tag in this response
            val match = tagRegex.find(apdu.response)
            
            if (match != null) {
                // Found it! Extract the value
                
                // Step 1: Get length byte (tells us how many bytes the value is)
                // groupValues[0] = entire match
                // groupValues[1] = first capture group (length)
                // groupValues[2] = second capture group (value)
                val lengthHex = match.groupValues[1]  // e.g., "08" = 8 bytes
                val length = lengthHex.toInt(16) * 2  // Convert to number of hex chars
                                                       // *2 because each byte = 2 hex chars
                
                // Step 2: Extract exactly 'length' characters from the value
                // This prevents grabbing too much data (important in concatenated responses)
                val value = match.groupValues[2].take(length)
                
                // Return the extracted value immediately (first match wins)
                return value
            }
        }
        
        // Tag not found in any response - return empty string
        return ""
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CARD IDENTIFICATION DATA (Who owns this card?)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract Track 2 Equivalent Data (Tag 57)
     * 
     * **WHAT IS TRACK 2:**
     * Magnetic stripe data encoded in EMV chip format. Contains:
     * - PAN (card number)
     * - Expiration date (YYMM)
     * - Service code (3 digits)
     * - Discretionary data
     * 
     * **FORMAT EXAMPLE:**
     * ```
     * 1234567890123456D25128123456789F
     * ^^^^^^^^^^^^^^^^ = PAN (card number)
     *                 ^ = separator (D or =)
     *                  ^^^^ = expiry (YYMM = Dec 2025)
     *                      ^^^ = service code
     *                         ^^^^^^^^ = discretionary data
     *                                 ^ = checksum/padding
     * ```
     * 
     * **WHY IT'S USEFUL:**
     * - Contains PAN if tag 5A is missing
     * - Includes expiry date
     * - Used for mag stripe emulation attacks
     * - Required for some offline transactions
     * 
     * @param apduLog APDU response log from card scan
     * @return Track 2 data as hex string (format: digits + D/= + more digits)
     */
    fun extractTrack2(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "57")
    }
    
    /**
     * Extract Card Expiration Date (Tag 5F24)
     * 
     * **FORMAT:**
     * YYMMDD in hex (3 bytes = 6 hex digits)
     * Example: "251231" = December 31, 2025
     * 
     * **WHY IT'S SEPARATE FROM TRACK2:**
     * Some cards don't have Track2 but do have explicit expiry tag.
     * Extract both for maximum compatibility.
     * 
     * @param apduLog APDU response log from card scan
     * @return Expiration date as hex string (YYMMDD format)
     */
    fun extractExpiryDate(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "5F24")
    }
    
    /**
     * Extract Cardholder Name (Tag 5F20) - WITH ASCII CONVERSION
     * 
     * **SPECIAL HANDLING:**
     * Unlike other tags, cardholder name is stored as ASCII text,
     * not raw hex data. We need to convert hex -> ASCII.
     * 
     * **HOW ASCII CONVERSION WORKS:**
     * ```
     * Hex:   "4A4F484E20534D495448"
     * Bytes:  4A 4F 48 4E 20 53 4D 49 54 48
     * ASCII:  J  O  H  N     S  M  I  T  H
     * Result: "JOHN SMITH"
     * ```
     * 
     * **ALGORITHM:**
     * 1. Find tag 5F20 in responses
     * 2. Extract hex value
     * 3. Split into 2-char chunks (each chunk = 1 byte)
     * 4. Convert each byte (0x4A) to ASCII char ('J')
     * 5. Join all chars into final string
     * 
     * @param apduLog APDU response log from card scan
     * @return Cardholder name as plain text string (e.g., "JOHN SMITH")
     */
    fun extractCardholderName(apduLog: List<ApduLogEntry>): String {
        // Search for tag 5F20 in all responses
        apduLog.forEach { apdu ->
            val nameRegex = "5F20([0-9A-F]{2})([0-9A-F]+)".toRegex()
            val match = nameRegex.find(apdu.response)
            
            if (match != null) {
                // Found the tag - extract hex data
                val lengthHex = match.groupValues[1]
                val length = lengthHex.toInt(16) * 2  // Convert to hex chars
                val hexName = match.groupValues[2].take(length)
                
                // Convert hex to ASCII
                // Example: "4A4F484E" -> ['4A','4F','48','4E'] -> [74,79,72,78] -> "JOHN"
                val asciiName = hexName
                    .chunked(2)                          // Split into 2-char chunks
                    .map { it.toInt(16).toChar() }       // Convert each hex byte to char
                    .joinToString("")                    // Combine into string
                
                return asciiName
            }
        }
        
        // Name tag not found
        return ""
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // SECURITY & AUTHENTICATION DATA (Is this card secure?)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract AIP - Application Interchange Profile (Tag 82)
     * 
     * **WHAT IS AIP:**
     * 2-byte bitfield that tells the terminal what security features the card supports.
     * Think of it as the card's "capabilities announcement".
     * 
     * **CRITICAL SECURITY BITS:**
     * Byte 1:
     * - Bit 7: RFU (Reserved for Future Use)
     * - Bit 6: SDA supported (Static Data Authentication - weak)
     * - Bit 5: DDA supported (Dynamic Data Authentication - strong)
     * - Bit 4: Cardholder verification supported
     * - Bit 3: Terminal risk management required
     * - Bit 2: Issuer authentication supported
     * - Bit 1: RFU
     * - Bit 0: CDA supported (Combined DDA/AC Generation - strongest)
     * 
     * Byte 2:
     * - Various other capability flags
     * 
     * **WHY IT'S IMPORTANT:**
     * - Cards with NO SDA/DDA/CDA are vulnerable (no auth!)
     * - Used to detect weak/vulnerable cards
     * - Determines what auth method to use in transaction
     * 
     * **EXAMPLE VALUES:**
     * - 0x0000: NO AUTHENTICATION (very weak!)
     * - 0x4000: SDA only (moderate)
     * - 0x2000: DDA only (strong)
     * - 0x0001: CDA only (strongest)
     * - 0x6001: SDA + DDA + CDA (paranoid mode)
     * 
     * @param apduLog APDU response log from card scan
     * @return AIP as 4-char hex string (2 bytes, e.g., "4000" or "2001")
     * 
     * @see analyzeAip For detailed security analysis of AIP value
     */
    fun extractAip(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "82")
    }
    
    /**
     * Extract AFL - Application File Locator (Tag 94)
     * 
     * **WHAT IS AFL:**
     * Tells the terminal WHERE to read EMV data from the card's filesystem.
     * Think of it as a "table of contents" for the card's data.
     * 
     * **AFL FORMAT:**
     * Sequence of 4-byte entries:
     * ```
     * [SFI] [First Record] [Last Record] [# of auth records]
     * 
     * Example AFL: 08010300 10010100
     *              ^^^^^^^^ Entry 1: SFI=01 (file 1), records 1-3, 0 need auth
     *              ^^^^^^^^ Entry 2: SFI=02 (file 2), records 1-1, 0 need auth
     * ```
     * 
     * **HOW IT'S USED:**
     * 1. Parse AFL into entries
     * 2. For each entry, send READ RECORD commands
     * 3. Read records [First] through [Last] from file [SFI]
     * 4. Collect all the EMV data tags from responses
     * 
     * **WHY IT'S CRITICAL:**
     * - Without AFL, we don't know where to read data
     * - Must be processed correctly or scan will fail
     * - Errors here = incomplete or wrong data extracted
     * 
     * @param apduLog APDU response log from card scan
     * @return AFL as hex string (variable length, multiple of 4 bytes)
     * 
     * @see CardReadingViewModel.executePhase4_ReadRecords Where AFL is parsed/used
     */
    fun extractAfl(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "94")
    }
    
    /**
     * Extract PDOL - Processing Options Data Object List (Tag 9F38)
     * 
     * **WHAT IS PDOL:**
     * List of data objects the card REQUIRES in the GPO (Get Processing Options) command.
     * Think of it as the card saying "Give me these values before I'll work".
     * 
     * **PDOL FORMAT:**
     * Sequence of [TAG][LENGTH] pairs:
     * ```
     * Example PDOL: 9F66049F02069F03069F1A0295055F2A029A039C01
     * Decoded:
     *   9F6604 = TTQ (Terminal Transaction Qualifiers), 4 bytes
     *   9F0206 = Amount Authorized, 6 bytes
     *   9F0306 = Amount Other, 6 bytes
     *   9F1A02 = Terminal Country Code, 2 bytes
     *   950 5 = TVR (Terminal Verification Results), 5 bytes
     *   5F2A02 = Currency Code, 2 bytes
     *   9A03 = Transaction Date, 3 bytes
     *   9C01 = Transaction Type, 1 byte
     * ```
     * 
     * **HOW IT'S USED:**
     * 1. Parse PDOL to see what data card wants
     * 2. Build response data with all required values
     * 3. Send in GPO command
     * 4. Card responds with AIP + AFL
     * 
     * **WHAT HAPPENS IF WRONG:**
     * - Card rejects GPO command (6985 - Conditions not satisfied)
     * - Scan fails - can't proceed past GPO phase
     * 
     * @param apduLog APDU response log from card scan
     * @return PDOL as hex string (variable length tag-length pairs)
     * 
     * @see CardReadingViewModel.buildPdolData Where PDOL is parsed/processed
     */
    fun extractPdol(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F38")
    }
    
    /**
     * Extract CVM List - Cardholder Verification Method List (Tag 8E)
     * 
     * **WHAT IS CVM:**
     * List of ways the terminal can verify the cardholder's identity.
     * Examples: PIN, signature, no verification needed, etc.
     * 
     * **CVM LIST FORMAT:**
     * ```
     * [Amount X (4 bytes)] [Amount Y (4 bytes)] [CVM Rules (2 bytes each)]
     * 
     * Example: 00000000 00000000 420300 1E0300
     *          ^^^^^^^^ X = $0 (no CVM required below this amount)
     *          ^^^^^^^^ Y = $0 (online PIN required above this amount)
     *          ^^^^^^ Rule 1: Enciphered PIN online + Signature
     *          ^^^^^^ Rule 2: Signature
     * ```
     * 
     * **CVM RULE FORMAT (2 bytes):**
     * ```
     * Byte 1: CVM Code (what method to use)
     *   0x00 = Fail CVM
     *   0x01 = Plaintext PIN verification (ICC)
     *   0x02 = Enciphered PIN verification (online)
     *   0x1E = Signature (paper)
     *   0x1F = No CVM required
     *   Bit 6 (0x40) = Apply next rule if this fails
     * 
     * Byte 2: CVM Condition (when to apply this method)
     *   0x00 = Always
     *   0x01 = If unattended cash
     *   0x03 = If terminal supports CVM
     *   0x06 = If transaction in app currency and under X
     *   0x07 = If transaction in app currency and over X
     * ```
     * 
     * **WHY IT'S IMPORTANT:**
     * - Determines if transaction requires PIN
     * - Used for security research (bypass attempts)
     * - Some cards have weak CVM (no PIN = easy fraud)
     * 
     * @param apduLog APDU response log from card scan
     * @return CVM list as hex string (8+ bytes minimum)
     * 
     * @see decodeCvmRule For human-readable CVM interpretation
     */
    fun extractCvmList(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "8E")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION CRYPTOGRAPHY (Proving transaction authenticity)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract IAD - Issuer Application Data (Tag 9F10)
     * 
     * **WHAT IS IAD:**
     * Proprietary data from the card issuer, used in cryptogram validation.
     * Format varies by issuer - often includes CVR (Card Verification Results).
     * 
     * **TYPICAL CONTENT:**
     * - Cryptogram version
     * - Card Verification Results (CVR) - what checks the card performed
     * - Issuer discretionary data
     * 
     * **WHY IT'S USEFUL:**
     * - Required for online authorization
     * - Contains security indicators
     * - Can reveal card's internal decision-making
     * 
     * @param apduLog APDU response log from card scan
     * @return IAD as hex string (variable length, issuer-specific)
     */
    fun extractIad(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F10")
    }
    
    /**
     * Extract Application Cryptogram (Tag 9F26)
     * 
     * **WHAT IS A CRYPTOGRAM:**
     * Cryptographic signature proving:
     * 1. This transaction is authentic
     * 2. Card participated in this specific transaction
     * 3. Transaction data hasn't been tampered with
     * 
     * **TYPES OF CRYPTOGRAMS:**
     * - ARQC (Authorization Request Cryptogram): "Ask issuer if OK"
     * - TC (Transaction Certificate): "Transaction approved offline"
     * - AAC (Application Authentication Cryptogram): "Transaction declined"
     * 
     * **HOW IT WORKS:**
     * ```
     * Card's Secret Key + Transaction Data = Cryptogram (8 bytes)
     * 
     * Issuer can verify:
     *   Cryptogram valid? = Card authentic + Data unmodified
     *   Cryptogram invalid? = Fraud / Tampering
     * ```
     * 
     * **FORMAT:**
     * 8 bytes (16 hex chars) of cryptographic output
     * Example: "1234567890ABCDEF"
     * 
     * **WHY IT'S CRITICAL:**
     * - Core of EMV security
     * - Without valid cryptogram, transaction fails
     * - Used for both online and offline authorization
     * 
     * @param apduLog APDU response log from card scan
     * @return Cryptogram as 16-char hex string (8 bytes)
     */
    fun extractCryptogram(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F26")
    }
    
    /**
     * Extract CID - Cryptogram Information Data (Tag 9F27)
     * 
     * **WHAT IS CID:**
     * 1-byte value that tells you:
     * 1. What TYPE of cryptogram was generated (ARQC/TC/AAC)
     * 2. What security features were used
     * 
     * **CID BITS:**
     * ```
     * Bit 7-6: Cryptogram type
     *   00 = AAC (declined)
     *   01 = TC (approved offline)
     *   10 = ARQC (ask online)
     *   11 = RFU
     * 
     * Bit 5-4: Advice/reason code
     * Bit 3-0: Additional info
     * ```
     * 
     * **EXAMPLE VALUES:**
     * - 0x80: ARQC (online authorization requested)
     * - 0x40: TC (offline approved)
     * - 0x00: AAC (transaction declined)
     * 
     * **WHY IT'S IMPORTANT:**
     * - Tells you card's decision (approve/decline/ask)
     * - Must match cryptogram type in 9F26
     * - Used in fraud detection
     * 
     * @param apduLog APDU response log from card scan
     * @return CID as 2-char hex string (1 byte, e.g., "80" or "40")
     */
    fun extractCid(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F27")
    }
    
    /**
     * Extract ATC - Application Transaction Counter (Tag 9F36)
     * 
     * **WHAT IS ATC:**
     * Counter that increments with EACH transaction the card processes.
     * Think of it as "how many times has this card been used?"
     * 
     * **FORMAT:**
     * 2 bytes (4 hex digits), big-endian counter
     * Example: "0042" = 66 transactions
     * 
     * **WHY IT'S IMPORTANT:**
     * - Anti-replay attack (can't reuse old cryptograms)
     * - Fraud detection (unusual jump in ATC = cloned card?)
     * - Required input for cryptogram generation
     * - Helps track card usage patterns
     * 
     * **SECURITY NOTE:**
     * ATC is included in cryptogram calculation, so:
     * - Each transaction has unique cryptogram (different ATC)
     * - Can't replay old transaction (ATC mismatch detected)
     * 
     * @param apduLog APDU response log from card scan
     * @return ATC as 4-char hex string (2 bytes, e.g., "0042")
     */
    fun extractAtc(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F36")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRANSACTION CONTEXT DATA (What kind of transaction is this?)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract UN - Unpredictable Number (Tag 9F37)
     * 
     * **WHAT IS UN:**
     * Random number generated by the TERMINAL for each transaction.
     * Used to ensure cryptogram is unique for THIS specific transaction.
     * 
     * **PURPOSE:**
     * - Prevents replay attacks
     * - Ensures cryptogram is tied to this exact transaction
     * - Terminal generates, card includes in cryptogram calculation
     * 
     * **FORMAT:**
     * 4 bytes (8 hex chars) of random data
     * Example: "12345678"
     * 
     * **HOW IT'S USED:**
     * ```
     * Terminal: Generates random UN = "ABCD1234"
     * Terminal: Sends UN to card in GENERATE AC command
     * Card: Includes UN in cryptogram calculation
     * Card: Returns cryptogram
     * Issuer: Validates cryptogram using same UN
     * ```
     * 
     * **WHY IT'S IMPORTANT:**
     * - Without UN, same transaction data = same cryptogram
     * - UN makes every cryptogram unique
     * - Critical anti-replay defense
     * 
     * @param apduLog APDU response log from card scan
     * @return UN as 8-char hex string (4 bytes of random data)
     */
    fun extractUn(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F37")
    }
    
    /**
     * Extract CDOL1 - Card Data Object List 1 (Tag 8C)
     * 
     * **WHAT IS CDOL1:**
     * List of data the card requires in GENERATE AC command.
     * Similar to PDOL, but for cryptogram generation phase.
     * 
     * **CDOL1 vs PDOL:**
     * - PDOL: Required for GPO (Get Processing Options)
     * - CDOL1: Required for GENERATE AC (first cryptogram)
     * 
     * **TYPICAL CDOL1 CONTENTS:**
     * - Amount Authorized
     * - Amount Other
     * - Terminal Country Code
     * - Terminal Verification Results (TVR)
     * - Transaction Date
     * - Transaction Type
     * - Unpredictable Number
     * - ... and more
     * 
     * **FORMAT:**
     * Same as PDOL - sequence of [TAG][LENGTH] pairs
     * 
     * **SPECIAL HANDLING:**
     * This function SKIPS READ RECORD responses to avoid false positives.
     * Why? RSA certificates in record data sometimes contain "8C" bytes
     * that look like CDOL1 tags but aren't - they're just part of key data.
     * 
     * @param apduLog APDU response log from card scan
     * @return CDOL1 as hex string (variable length tag-length pairs)
     * 
     * @see buildCdolData Where CDOL1 is parsed and used
     */
    fun extractCdol1(apduLog: List<ApduLogEntry>): String {
        apduLog.forEach { apdu ->
            // CRITICAL: Skip READ RECORD responses
            // RSA certificates in records can have "8C" bytes that aren't CDOL1
            if (apdu.description.contains("READ RECORD", ignoreCase = true)) {
                return@forEach // Skip this APDU
            }
            
            val response = apdu.response
            
            // Manual search for tag 8C (can't use simple regex due to false positives)
            var i = 0
            while (i < response.length - 4) { // Need at least tag(2) + length(2)
                if (response.substring(i, i + 2) == "8C") {
                    // Found potential CDOL1 tag
                    val lengthHex = response.substring(i + 2, i + 4)
                    val lengthInt = try { lengthHex.toInt(16) } catch (e: Exception) { -1 }
                    
                    // Validate length (CDOL1 should be < 50 bytes typically)
                    if (lengthInt < 0 || lengthInt > 50) {
                        i += 2 // Invalid, keep searching
                        continue
                    }
                    
                    val length = lengthInt * 2 // Convert to hex chars
                    
                    // Extract CDOL data if we have enough response data
                    if (i + 4 + length <= response.length) {
                        val cdolData = response.substring(i + 4, i + 4 + length)
                        return cdolData
                    }
                }
                i += 2 // Move to next byte
            }
        }
        
        // CDOL1 not found (some cards don't require it)
        return ""
    }
    
    /**
     * Extract CDOL2 - Card Data Object List 2 (Tag 8D)
     * 
     * **WHAT IS CDOL2:**
     * Similar to CDOL1, but for second GENERATE AC command.
     * 
     * **WHEN IT'S USED:**
     * Some EMV flows require TWO cryptograms:
     * 1. First AC: ARQC (ask for online auth)
     * 2. Second AC: TC (approve offline) OR AAC (decline)
     * 
     * CDOL2 specifies data needed for second AC generation.
     * 
     * **TYPICAL FLOW:**
     * ```
     * Card: "I need online auth" (CID=ARQC)
     * Terminal: Contacts issuer
     * Issuer: "Approved" (sends auth code)
     * Terminal: "Generate second AC with this auth code" (uses CDOL2)
     * Card: "OK, here's TC" (transaction approved)
     * ```
     * 
     * **WHY TWO CRYPTOGRAMS:**
     * - First: Request authorization
     * - Second: Finalize transaction with issuer's response
     * 
     * @param apduLog APDU response log from card scan
     * @return CDOL2 as hex string (variable length tag-length pairs)
     */
    fun extractCdol2(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "8D")
    }
    
    /**
     * Extract Log Format (Tag 9F4F)
     * 
     * **WHAT IS LOG FORMAT:**
     * Tells you what data is stored in the card's transaction log.
     * Similar to CDOL - it's a list of [TAG][LENGTH] pairs.
     * 
     * **WHY IT EXISTS:**
     * Cards keep internal transaction logs (last N transactions).
     * Log Format tells you what fields are in each log entry.
     * 
     * **TYPICAL LOG FIELDS:**
     * - Amount
     * - Date
     * - Transaction Type
     * - ATC (which transaction number this was)
     * - Country Code
     * - Currency Code
     * 
     * **HOW IT'S USED:**
     * ```
     * Terminal: GET DATA for log records
     * Card: Returns log entries
     * Terminal: Uses Log Format to parse each entry
     * Terminal: Displays transaction history
     * ```
     * 
     * **SECURITY NOTE:**
     * Transaction logs can reveal:
     * - Spending patterns
     * - Locations visited
     * - Merchant categories
     * Useful for fraud analysis or privacy research.
     * 
     * @param apduLog APDU response log from card scan
     * @return Log format as hex string (tag-length pairs defining log structure)
     */
    fun extractLogFormat(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F4F")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // CARD APPLICATION DATA (What app is this? What version?)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract Application Version Number (Tag 9F09)
     * 
     * **WHAT IT IS:**
     * Version of the EMV application on the card.
     * Think of it like "Android 12" or "iOS 16" for the card.
     * 
     * **FORMAT:**
     * 2 bytes (4 hex chars)
     * Example: "0002" = Version 0.2
     * 
     * **WHY IT'S USEFUL:**
     * - Some versions have known vulnerabilities
     * - Helps identify card generation/age
     * - Used for compatibility checks
     * - Research: older versions = weaker security
     * 
     * @param apduLog APDU response log from card scan
     * @return Application version as 4-char hex string (2 bytes)
     */
    fun extractAppVersion(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F09")
    }
    
    /**
     * Extract AUC - Application Usage Control (Tag 9F07)
     * 
     * **WHAT IS AUC:**
     * 2-byte bitfield controlling WHERE and HOW the card can be used.
     * 
     * **AUC BITS:**
     * Byte 1:
     * - Bit 8: Valid for domestic cash
     * - Bit 7: Valid for international cash
     * - Bit 6: Valid for domestic goods
     * - Bit 5: Valid for international goods
     * - Bit 4: Valid for domestic services
     * - Bit 3: Valid for international services
     * - Bit 2: Valid at ATMs
     * - Bit 1: Valid at terminals other than ATMs
     * 
     * Byte 2:
     * - Bit 8: Domestic cashback allowed
     * - Bit 7: International cashback allowed
     * - ... more restrictions
     * 
     * **EXAMPLE:**
     * - 0xFFFF: Can be used anywhere for anything (no restrictions)
     * - 0x0800: Only valid for domestic cash at ATMs
     * 
     * **WHY IT'S IMPORTANT:**
     * - Prevents misuse (e.g., gift card can't get cash)
     * - Enforces issuer policies
     * - Security research: bypass attempts
     * 
     * @param apduLog APDU response log from card scan
     * @return AUC as 4-char hex string (2 bytes of restrictions)
     */
    fun extractAuc(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F07")
    }
    
    /**
     * Extract TTQ - Terminal Transaction Qualifiers (Tag 9F66)
     * 
     * **WHAT IS TTQ:**
     * Bitfield sent BY THE TERMINAL to tell card what features it supports.
     * (Not from the card - terminal sends this!)
     * 
     * **WHY WE EXTRACT IT:**
     * Even though terminal sends it, card echoes it back in responses.
     * Useful to see what terminal capabilities were used.
     * 
     * **TTQ BITS:**
     * Byte 1:
     * - Bit 8: Contactless MSD supported
     * - Bit 7: Contactless qVSDC supported
     * - Bit 6: Contactless EMV mode supported
     * - Bit 5: EMV contact chip supported
     * - Bit 4: Offline-only reader
     * - Bit 3: Online PIN supported
     * - Bit 2: Signature supported
     * - Bit 1: Offline Data Authentication for Online Authorizations supported
     * 
     * **TYPICAL VALUES:**
     * - 0x36000000: Standard contactless (qVSDC + EMV mode)
     * - 0x26000000: Basic contactless (MSD only)
     * 
     * @param apduLog APDU response log from card scan
     * @return TTQ as 8-char hex string (4 bytes of terminal capabilities)
     */
    fun extractTtq(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "9F66")
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // GEOGRAPHIC & CURRENCY DATA (Where is this card used?)
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract Issuer Country Code (Tag 5F28)
     * 
     * **WHAT IT IS:**
     * ISO 3166-1 numeric country code of the card issuer.
     * 
     * **FORMAT:**
     * 3 decimal digits encoded as hex (weird, I know!)
     * Example: "0840" = USA (840 in decimal)
     * 
     * **COMMON CODES:**
     * - 840: United States
     * - 826: United Kingdom
     * - 250: France
     * - 276: Germany
     * - 392: Japan
     * - 156: China
     * 
     * **WHY IT'S USEFUL:**
     * - Fraud detection (card from Country A used in Country B?)
     * - Currency validation (ensure matching currencies)
     * - Sanctions/compliance checks
     * 
     * @param apduLog APDU response log from card scan
     * @return Country code as hex string (3 bytes, e.g., "0840" for USA)
     */
    fun extractCountryCode(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "5F28")
    }
    
    /**
     * Extract Application Currency Code (Tag 5F2A)
     * 
     * **WHAT IT IS:**
     * ISO 4217 numeric currency code for this application.
     * 
     * **FORMAT:**
     * 3 decimal digits encoded as hex
     * Example: "0840" = USD (840 in decimal)
     * 
     * **COMMON CODES:**
     * - 840: USD (US Dollar)
     * - 978: EUR (Euro)
     * - 826: GBP (British Pound)
     * - 392: JPY (Japanese Yen)
     * - 156: CNY (Chinese Yuan)
     * 
     * **WHY IT'S IMPORTANT:**
     * - Prevents currency mismatch errors
     * - Used in amount calculations
     * - Multi-currency cards have multiple applications
     * 
     * **EXAMPLE USE:**
     * ```
     * Card has two apps:
     *   App 1: Currency = 840 (USD account)
     *   App 2: Currency = 978 (EUR account)
     * Terminal in France selects EUR app
     * ```
     * 
     * @param apduLog APDU response log from card scan
     * @return Currency code as hex string (2 bytes, e.g., "0840" for USD)
     */
    fun extractCurrencyCode(apduLog: List<ApduLogEntry>): String {
        return extractTag(apduLog, "5F2A")
    }
}
