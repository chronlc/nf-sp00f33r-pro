package com.nfsp00f33r.app.screens.cardreading.emv

/**
 * EMV Data Formatter - Convert Raw EMV Data to Human-Readable Formats
 * 
 * **PURPOSE:**
 * This class provides formatting utilities for EMV data extracted from payment cards.
 * Raw EMV data is stored in compact hex format - this class makes it readable for users.
 * 
 * **WHAT IT DOES:**
 * - Formats PANs with masking (show first 6 + last 4 digits only)
 * - Formats Track 2 magnetic stripe data with masking
 * - Converts date formats (YYMMDD → MM/YY, YYMMDD → DD/MM/YY)
 * - Converts hex-encoded ASCII to readable text
 * - Extracts specific fields from Track 2 data
 * 
 * **WHY IT EXISTS:**
 * EMV data comes in raw hex format that's:
 * - Hard to read: "5413330000000019D25121011234567890"
 * - Violates PCI-DSS if displayed unmasked
 * - Not user-friendly for display in UI
 * 
 * This class transforms it to:
 * - Easy to read: "541333******0019 Exp: 12/25"
 * - PCI-DSS compliant (only first 6 + last 4 digits shown)
 * - UI-ready formatted strings
 * 
 * **HOW TO USE:**
 * ```kotlin
 * // Format PAN for display (masked)
 * val rawPan = "5413330000000019"
 * val formatted = EmvDataFormatter.formatPan(rawPan)
 * // Result: "541333******0019"
 * 
 * // Format expiry date
 * val rawExpiry = "2512" // YYMM format
 * val formatted = EmvDataFormatter.formatExpiryDate(rawExpiry)
 * // Result: "12/25" (MM/YY format)
 * 
 * // Convert hex to ASCII
 * val hexName = "4A4F484E20534D495448" // "JOHN SMITH" in hex
 * val name = EmvDataFormatter.hexToAscii(hexName)
 * // Result: "JOHN SMITH"
 * ```
 * 
 * **PCI-DSS COMPLIANCE:**
 * Payment Card Industry Data Security Standard (PCI-DSS) requires:
 * - NEVER store full PAN in logs/databases
 * - Only display first 6 + last 4 digits (BIN + last 4)
 * - Mask middle digits with asterisks
 * 
 * All formatting functions in this class follow PCI-DSS guidelines.
 * 
 * @see <a href="https://www.pcisecuritystandards.org/">PCI Security Standards</a>
 */
object EmvDataFormatter {
    
    // ═══════════════════════════════════════════════════════════════════════════
    // PAN (PRIMARY ACCOUNT NUMBER) FORMATTING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Format PAN (Primary Account Number) for Display - PCI-DSS Compliant Masking
     * 
     * **WHAT IT DOES:**
     * Formats card number with masking to comply with PCI-DSS requirements.
     * Shows only BIN (first 6 digits) + last 4 digits, masks the rest.
     * 
     * **PAN STRUCTURE:**
     * ```
     * Example PAN: 5413 3300 0000 0019
     *              ^^^^ ^^             = BIN (Bank Identification Number) - first 6 digits
     *                     ^^^^ ^^^^    = Account identifier - MASKED
     *                              ^^^^ = Last 4 digits - shown for verification
     * 
     * Card brands by BIN:
     * - 4xxxxx = Visa
     * - 5xxxxx = Mastercard
     * - 3xxxxx = American Express
     * - 6xxxxx = Discover
     * ```
     * 
     * **WHY MASK:**
     * PCI-DSS (Payment Card Industry Data Security Standard) requires:
     * 1. NEVER display full PAN in UI/logs
     * 2. Only show first 6 (BIN) + last 4 digits
     * 3. Mask middle digits with asterisks
     * 
     * **PADDING CHARACTER HANDLING:**
     * EMV PANs are stored in BCD (Binary Coded Decimal) format.
     * If PAN has odd length, trailing 'F' is added as padding.
     * Examples:
     * - "5413330000000019F" → "5413330000000019" (remove trailing F)
     * - "541333000000001F"  → "541333000000001"  (remove trailing F)
     * 
     * **ERROR HANDLING:**
     * - If PAN too short (< 13 digits): Returns original unmodified
     * - If parsing fails: Returns original hex string
     * - If null/empty: Returns empty string
     * 
     * @param hexPan Raw PAN in hex format (may include padding 'F')
     * @return Formatted PAN: "541333******0019" or original if invalid
     * 
     * @example
     * ```kotlin
     * formatPan("5413330000000019")   // → "541333******0019"
     * formatPan("5413330000000019F")  // → "541333******0019" (F stripped)
     * formatPan("4111111")            // → "4111111" (too short, returned as-is)
     * ```
     */
    fun formatPan(hexPan: String): String {
        return try {
            // Remove padding 'F' characters (BCD padding for odd-length PANs)
            val pan = hexPan.replace("F", "").replace("f", "")
            
            // Validate minimum length (13 digits minimum for valid PAN)
            if (pan.length >= 13) {
                // PCI-DSS compliant format: first 6 + **** + last 4
                "${pan.take(6)}****${pan.takeLast(4)}"
            } else {
                // Invalid PAN length - return original
                hexPan
            }
        } catch (e: Exception) {
            // Parsing error - return original to prevent data loss
            hexPan
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRACK 2 MAGNETIC STRIPE DATA FORMATTING
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Format Track 2 Equivalent Data - Magnetic Stripe Format with Masking
     * 
     * **WHAT IS TRACK 2:**
     * Track 2 is magnetic stripe data stored on the EMV chip.
     * Format: PAN + separator + expiry + service code + discretionary data
     * 
     * **TRACK 2 STRUCTURE:**
     * ```
     * Example: 5413330000000019D2512101123456789F
     *          ^^^^^^^^^^^^^^^^ = PAN (16 digits)
     *                          ^ = Separator ('D' or '=')
     *                           ^^^^ = Expiry (YYMM = Dec 2025)
     *                               ^^^ = Service code
     *                                  ^^^^^^^^^ = Discretionary data
     *                                           ^ = Padding 'F'
     * 
     * Formatted: 541333******0019D2512...
     *            ^^^^^^        ^^^^ = BIN + last 4 (PCI-DSS compliant)
     *                         ^ = Separator
     *                          ^^^^ = Expiry shown (not sensitive)
     *                              ^^^ = Service code + data hidden
     * ```
     * 
     * **SEPARATOR CHARACTER:**
     * - EMV uses 'D' as separator (hex encoding of '=' from mag stripe)
     * - Original mag stripe uses '=' separator
     * - Both mean the same thing, 'D' is EMV standard
     * 
     * **SERVICE CODE:**
     * 3-digit code after expiry:
     * ```
     * First digit: Interchange/technology
     *   1 = International, mag stripe
     *   2 = International, chip
     *   6 = National, mag stripe
     *   7 = National, chip
     * 
     * Second digit: Authorization method
     *   0 = Normal authorization
     *   2 = Contact issuer
     *   4 = Contact issuer except under bilateral agreement
     * 
     * Third digit: Allowed services
     *   0 = No restrictions
     *   1 = No PIN
     *   3 = ATM only
     *   5 = Goods/services only
     * ```
     * 
     * **WHY MASK:**
     * - PAN must be masked (PCI-DSS requirement)
     * - Discretionary data may contain CVV (must hide)
     * - Expiry is OK to show (not considered sensitive)
     * 
     * @param hexTrack2 Raw Track 2 data in hex format
     * @return Formatted Track 2: "541333******0019D2512..." or original if invalid
     * 
     * @example
     * ```kotlin
     * formatTrack2("5413330000000019D2512101123456789F")
     * // → "541333******0019D2512..."
     * 
     * formatTrack2("4111111111111111=25061011234567890")
     * // → "411111******1111=2506..." (note: = separator, not D)
     * ```
     */
    fun formatTrack2(hexTrack2: String): String {
        return try {
            // Remove padding 'F' characters
            val track2 = hexTrack2.replace("F", "").replace("f", "")
            
            // Check for 'D' separator (EMV standard)
            if (track2.contains("D")) {
                val parts = track2.split("D")
                
                if (parts.size >= 2) {
                    // Part 0: PAN
                    val pan = parts[0]
                    
                    // Part 1: Expiry (first 4 chars) + service code + discretionary
                    val expiry = parts[1].take(4)
                    
                    // Format: masked PAN + separator + expiry + ellipsis (hide rest)
                    "${pan.take(6)}****${pan.takeLast(4)}D$expiry..."
                } else {
                    // Invalid format (no data after separator) - return as-is
                    track2
                }
            } else {
                // No separator found - not valid Track 2 format
                track2
            }
        } catch (e: Exception) {
            // Parsing error - return original
            hexTrack2
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // DATE FORMATTING UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Format Expiry Date from YYMM to MM/YY (User-Friendly Format)
     * 
     * **WHAT IT DOES:**
     * Converts EMV expiry format (YYMM) to standard credit card format (MM/YY).
     * 
     * **WHY THIS FORMAT:**
     * EMV stores dates in YYMM format (compact storage):
     * - "2512" = December 2025
     * 
     * Users expect MM/YY format (what's printed on physical cards):
     * - "12/25" = December 2025
     * 
     * **DATE EXAMPLES:**
     * ```
     * Input   → Output   = Meaning
     * "2512"  → "12/25"  = December 2025
     * "3001"  → "01/30"  = January 2030
     * "2306"  → "06/23"  = June 2023 (expired!)
     * ```
     * 
     * **EXPIRY VALIDATION:**
     * This function does NOT validate if card is expired.
     * It only reformats the date string.
     * For expiry checking, compare with current date elsewhere.
     * 
     * **ERROR HANDLING:**
     * - If not exactly 4 characters: Returns original string
     * - If parsing fails: Returns original hex string
     * - Invalid months (>12) or years: Still formats (caller validates)
     * 
     * @param hexExpiry Expiry date in YYMM format (4 hex chars)
     * @return Formatted date in MM/YY format, or original if invalid
     * 
     * @example
     * ```kotlin
     * formatExpiryDate("2512")  // → "12/25"
     * formatExpiryDate("3001")  // → "01/30"
     * formatExpiryDate("251")   // → "251" (invalid length, returned as-is)
     * ```
     */
    fun formatExpiryDate(hexExpiry: String): String {
        return try {
            if (hexExpiry.length == 4) {
                // YYMM format: positions 0-1 = YY, positions 2-3 = MM
                val year = hexExpiry.substring(0, 2)
                val month = hexExpiry.substring(2, 4)
                
                // Return as MM/YY
                "$month/$year"
            } else {
                // Invalid length - return original
                hexExpiry
            }
        } catch (e: Exception) {
            hexExpiry
        }
    }
    
    /**
     * Format Effective Date from YYMMDD to DD/MM/YY (Full Date Format)
     * 
     * **WHAT IT DOES:**
     * Converts 6-digit EMV date (YYMMDD) to readable format (DD/MM/YY).
     * 
     * **WHEN USED:**
     * - Application Effective Date: When card application became active
     * - Transaction dates in logs
     * - Validity dates
     * 
     * **DATE FORMAT:**
     * ```
     * Input: "251231" = YYMMDD
     *        YY = 25 (year 2025)
     *        MM = 12 (December)
     *        DD = 31 (31st day)
     * 
     * Output: "31/12/25" = DD/MM/YY
     * ```
     * 
     * **INTERNATIONAL FORMAT:**
     * Uses DD/MM/YY (European/ISO standard) not MM/DD/YY (US format).
     * This matches EMV specification standards.
     * 
     * **2-DIGIT YEAR INTERPRETATION:**
     * YY values 00-99 map to years:
     * - 00-49 → 2000-2049
     * - 50-99 → 1950-1999
     * (Standard 2-digit year interpretation)
     * 
     * @param hexDate Date in YYMMDD format (6 hex chars)
     * @return Formatted date in DD/MM/YY format, or original if invalid
     * 
     * @example
     * ```kotlin
     * formatEffectiveDate("251231")  // → "31/12/25" (Dec 31, 2025)
     * formatEffectiveDate("200101")  // → "01/01/20" (Jan 1, 2020)
     * formatEffectiveDate("12345")   // → "12345" (invalid length)
     * ```
     */
    fun formatEffectiveDate(hexDate: String): String {
        return try {
            if (hexDate.length == 6) {
                // YYMMDD format: positions 0-1 = YY, 2-3 = MM, 4-5 = DD
                val year = hexDate.substring(0, 2)
                val month = hexDate.substring(2, 4)
                val day = hexDate.substring(4, 6)
                
                // Return as DD/MM/YY (international format)
                "$day/$month/$year"
            } else {
                // Invalid length - return original
                hexDate
            }
        } catch (e: Exception) {
            hexDate
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // HEX/ASCII CONVERSION UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Convert Hex-Encoded ASCII to Readable Text
     * 
     * **WHAT IT DOES:**
     * Converts hex-encoded text back to readable ASCII characters.
     * Used for cardholder names, issuer names, and other text fields.
     * 
     * **HOW HEX ENCODING WORKS:**
     * Each ASCII character is represented by 2 hex digits:
     * ```
     * Character → ASCII Code → Hex
     * 'J'       → 74         → 4A
     * 'O'       → 79         → 4F
     * 'H'       → 72         → 48
     * 'N'       → 78         → 4E
     * ' '       → 32         → 20
     * 'S'       → 83         → 53
     * 'M'       → 77         → 4D
     * 'I'       → 73         → 49
     * 'T'       → 84         → 54
     * 'H'       → 72         → 48
     * 
     * Result: "4A4F484E20534D495448" → "JOHN SMITH"
     * ```
     * 
     * **ALGORITHM:**
     * 1. Split hex string into 2-character chunks
     * 2. Convert each chunk to integer (base 16)
     * 3. Convert integer to ASCII character
     * 4. Join all characters into final string
     * 
     * **CHARACTER FILTERING:**
     * Only printable ASCII (codes 32-126) are converted:
     * - 32-126: Letters, numbers, punctuation, space
     * - 0-31: Control characters (replaced with '.')
     * - 127+: Extended ASCII (replaced with '.')
     * 
     * **WHY FILTER:**
     * Control characters (0x00-0x1F) and extended ASCII (0x7F+) can:
     * - Break UI rendering
     * - Cause security issues (terminal escape sequences)
     * - Represent binary data, not text
     * 
     * **COMMON EMV TEXT FIELDS:**
     * - Tag 5F20: Cardholder Name (e.g., "JOHN DOE")
     * - Tag 9F12: Application Preferred Name (e.g., "VISA DEBIT")
     * - Tag 50: Application Label (e.g., "MASTERCARD")
     * 
     * @param hex Hex-encoded ASCII string (even number of characters)
     * @return Decoded ASCII text, with non-printable chars as '.'
     * 
     * @example
     * ```kotlin
     * hexToAscii("4A4F484E20534D495448")  // → "JOHN SMITH"
     * hexToAscii("56495341")              // → "VISA"
     * hexToAscii("00414243")              // → ".ABC" (0x00 = control char)
     * ```
     */
    fun hexToAscii(hex: String): String {
        return try {
            hex.chunked(2)  // Split into 2-char chunks
                .map { hexByte ->
                    // Convert hex byte to integer
                    val charCode = hexByte.toInt(16)
                    
                    // Only convert printable ASCII (32-126)
                    // Others become '.' to prevent rendering issues
                    if (charCode in 32..126) {
                        charCode.toChar()
                    } else {
                        '.'  // Non-printable character placeholder
                    }
                }
                .joinToString("")  // Combine all characters
        } catch (e: Exception) {
            // Parsing error (odd length hex, invalid chars, etc.)
            hex
        }
    }
    
    // ═══════════════════════════════════════════════════════════════════════════
    // TRACK 2 FIELD EXTRACTION UTILITIES
    // ═══════════════════════════════════════════════════════════════════════════
    
    /**
     * Extract PAN from Track 2 Equivalent Data
     * 
     * **WHAT IT DOES:**
     * Extracts just the PAN portion from Track 2 data.
     * Track 2 contains: PAN + separator + expiry + other data
     * This function returns only the PAN.
     * 
     * **WHY NEEDED:**
     * Sometimes card doesn't have tag 5A (dedicated PAN tag).
     * In that case, PAN must be extracted from Track 2 (tag 57).
     * 
     * **TRACK 2 STRUCTURE REMINDER:**
     * ```
     * Full Track 2: 5413330000000019D2512101123456789F
     *               ^^^^^^^^^^^^^^^^ = PAN (this is what we extract)
     *                               ^ = Separator 'D'
     *                                ^^^^^^^^^^^^^^^ = Rest (ignored)
     * ```
     * 
     * @param track2 Track 2 data (with 'D' separator)
     * @return PAN only, or empty string if invalid format
     * 
     * @example
     * ```kotlin
     * extractPanFromTrack2("5413330000000019D2512101123456789F")
     * // → "5413330000000019"
     * 
     * extractPanFromTrack2("invalid data")
     * // → "" (no separator found)
     * ```
     */
    fun extractPanFromTrack2(track2: String): String {
        return try {
            if (track2.contains("D")) {
                // PAN is everything before the 'D' separator
                track2.split("D")[0]
            } else {
                // No separator = invalid Track 2 format
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * Extract and Format Expiry Date from Track 2 Data
     * 
     * **WHAT IT DOES:**
     * Extracts expiry date from Track 2 and formats it to MM/YY.
     * Combines extraction + formatting in one step.
     * 
     * **TRACK 2 EXPIRY LOCATION:**
     * ```
     * Track 2: 5413330000000019D2512101123456789F
     *          ^^^^^^^^^^^^^^^^ = PAN
     *                          ^ = Separator
     *                           ^^^^ = Expiry (YYMM) ← This part extracted
     *                               ^^^ = Service code
     *                                  ^^^^^^^^^ = Discretionary data
     * ```
     * 
     * **PROCESS:**
     * 1. Split Track 2 at 'D' separator
     * 2. Take first 4 characters after 'D' (YYMM)
     * 3. Reformat from YYMM to MM/YY
     * 
     * @param track2 Track 2 data (with 'D' separator)
     * @return Formatted expiry (MM/YY), or empty string if invalid
     * 
     * @example
     * ```kotlin
     * extractExpiryFromTrack2("5413330000000019D2512101123456789F")
     * // → "12/25" (extracted "2512", formatted to "12/25")
     * 
     * extractExpiryFromTrack2("5413330000000019D25")
     * // → "" (not enough data after separator)
     * ```
     */
    fun extractExpiryFromTrack2(track2: String): String {
        return try {
            if (track2.contains("D")) {
                // Get everything after the 'D' separator
                val afterD = track2.split("D")[1]
                
                // Expiry is first 4 characters after separator (YYMM)
                if (afterD.length >= 4) {
                    val expiry = afterD.take(4)
                    
                    // Reformat from YYMM to MM/YY
                    val year = expiry.substring(0, 2)
                    val month = expiry.substring(2, 4)
                    "$month/$year"
                } else {
                    // Not enough data after separator
                    ""
                }
            } else {
                // No separator found
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
