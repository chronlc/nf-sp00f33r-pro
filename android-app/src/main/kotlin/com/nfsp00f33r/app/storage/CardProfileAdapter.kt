package com.nfsp00f33r.app.storage

import com.nfsp00f33r.app.models.CardProfile as AppCardProfile
import com.nfsp00f33r.app.storage.models.*
import com.nfsp00f33r.app.data.ApduLogEntry
import java.time.Instant
import java.util.Date

/**
 * CardProfileAdapter - Phase 1A Implementation
 * 
 * Converts between:
 * - App CardProfile (com.nfsp00f33r.app.models.CardProfile) - Used by UI/ViewModels
 * - Storage CardProfile (com.nfsp00f33r.app.storage.models.CardProfile) - Used by CardDataStore
 * 
 * This adapter enables encrypted persistent storage while preserving all existing app functionality.
 * 
 * Note: In Phase 2A, we may consolidate these models when adopting the full Module System.
 */
object CardProfileAdapter {

    /**
     * Convert App CardProfile to Storage CardProfile for encrypted persistence
     */
    fun toStorageProfile(appProfile: AppCardProfile): CardProfile {
        val emvData = appProfile.emvCardData
        
        // Convert to StaticCardData
        val staticData = StaticCardData(
            pan = emvData.pan ?: "",
            panSequenceNumber = null,
            expiryDate = emvData.expiryDate ?: "",
            cardholderName = emvData.cardholderName,
            serviceCode = null,
            track1Data = null, // Not available in EmvCardData
            track2EquivalentData = emvData.track2Data,
            track2Data = emvData.track2Data,
            issuerCountryCode = null,
            currencyCode = null,
            languagePreference = null,
            applicationLabel = emvData.applicationLabel,
            applicationPreferredName = null
        )
        
        // ENHANCED: Extract EMV data from APDU logs
        val extractedData = com.nfsp00f33r.app.cardreading.ApduDataExtractor.extractEmvData(appProfile.apduLogs)
        
        // Convert to DynamicCardData with real extracted values
        val dynamicData = DynamicCardData(
            atc = extractedData.atc ?: 0,
            lastOnlineATC = extractedData.lastOnlineAtc ?: 0,
            pinTryCounter = null,
            logFormat = null,
            transactionLog = extractedData.transactionLogs.map { log ->
                "ATC=${log.atc},Amount=${log.amount},Date=${log.date},Cryptogram=${log.cryptogram}"
            }
        )
        
        // Convert to CardConfiguration with real extracted values
        val cardConfiguration = CardConfiguration(
            aid = emvData.applicationIdentifier ?: emvData.selectedAid ?: "",
            aip = extractedData.aip ?: emvData.applicationInterchangeProfile ?: "",
            afl = null,
            cvmList = extractedData.cvmList.mapIndexed { index, cvmDescription ->
                CVMRule(
                    cvmCode = index.toString(),
                    cvmCondition = "extracted",
                    description = cvmDescription
                )
            },
            iacDefault = null,
            iacDenial = null,
            iacOnline = null,
            tacDefault = null,
            tacDenial = null,
            tacOnline = null,
            pdol = null,
            cdol1 = null,
            cdol2 = null,
            applicationVersionNumber = null,
            applicationUsageControl = null,
            applicationEffectiveDate = null,
            applicationExpirationDate = emvData.expiryDate
        )
        
        // Convert to CryptographicData - Extract from emvTags map (parsed by comprehensive TLV parser)
        val cryptographicData = CryptographicData(
            issuerPublicKeyCertificate = emvData.emvTags["90"], // Tag 90: Issuer Public Key Certificate
            issuerPublicKeyExponent = emvData.emvTags["9F32"], // Tag 9F32: Issuer Public Key Exponent
            issuerPublicKeyRemainder = emvData.emvTags["92"], // Tag 92: Issuer Public Key Remainder
            iccPublicKeyCertificate = emvData.emvTags["9F46"], // Tag 9F46: ICC Public Key Certificate
            iccPublicKeyExponent = emvData.emvTags["9F47"], // Tag 9F47: ICC Public Key Exponent
            iccPublicKeyRemainder = emvData.emvTags["9F48"], // Tag 9F48: ICC Public Key Remainder
            caPublicKeyIndex = emvData.emvTags["8F"], // Tag 8F: CA Public Key Index
            sdaTagList = emvData.emvTags["9F4A"], // Tag 9F4A: SDA Tag List
            offlineDataAuthenticationMethod = OfflineAuthMethod.NONE,
            vulnerabilities = emptyList() // Will be populated by ROCA analyzer in Phase 1B
        )
        
        android.util.Log.d("CardProfileAdapter", "🔐 Mapped certificate data from emvTags:")
        android.util.Log.d("CardProfileAdapter", "  - Issuer Cert (90): " + (cryptographicData.issuerPublicKeyCertificate?.let { "${it.length} chars" } ?: "null"))
        android.util.Log.d("CardProfileAdapter", "  - Issuer Exp (9F32): " + (cryptographicData.issuerPublicKeyExponent ?: "null"))
        android.util.Log.d("CardProfileAdapter", "  - Issuer Rem (92): " + (cryptographicData.issuerPublicKeyRemainder?.let { "${it.length} chars" } ?: "null"))
        android.util.Log.d("CardProfileAdapter", "  - ICC Cert (9F46): " + (cryptographicData.iccPublicKeyCertificate ?: "null"))
        android.util.Log.d("CardProfileAdapter", "  - CA Index (8F): " + (cryptographicData.caPublicKeyIndex ?: "null"))
        
        // Convert to ProfileMetadata
        val metadata = ProfileMetadata(
            captureMethod = CaptureMethod.NFC_READER,
            captureDevice = "PN532",
            captureLocation = null,
            notes = appProfile.notes,
            customFields = appProfile.tags.associateWith { it }
        )
        
        // Detect card type from PAN
        val cardType = when {
            staticData.pan.startsWith("4") -> CardType.VISA
            staticData.pan.startsWith("5") || staticData.pan.startsWith("2") -> CardType.MASTERCARD
            staticData.pan.startsWith("3") -> CardType.AMERICAN_EXPRESS
            staticData.pan.startsWith("6") -> CardType.DISCOVER
            else -> CardType.UNKNOWN
        }
        
        return CardProfile(
            profileId = appProfile.id,
            cardType = cardType,
            issuer = appProfile.issuerName ?: "Unknown",
            staticData = staticData,
            dynamicData = dynamicData,
            configuration = cardConfiguration,
            cryptographicData = cryptographicData,
            transactionHistory = emptyList(),
            metadata = metadata,
            tags = appProfile.tags.toSet(),
            version = 1,
            createdAt = dateToInstant(appProfile.createdAt),
            lastModified = dateToInstant(appProfile.createdAt)
        )
    }

    /**
     * Convert Storage CardProfile to App CardProfile for UI display
     */
    fun toAppProfile(storageProfile: CardProfile): AppCardProfile {
        val staticData = storageProfile.staticData
        
        // Convert to EmvCardData
        val emvData = com.nfsp00f33r.app.data.EmvCardData(
            id = storageProfile.profileId,
            cardUid = null, // Not stored in framework model
            pan = staticData.pan,
            cardholderName = staticData.cardholderName,
            expiryDate = staticData.expiryDate,
            applicationIdentifier = storageProfile.configuration.aid,
            applicationLabel = staticData.applicationLabel ?: "",
            track2Data = staticData.track2Data,
            applicationCryptogram = null
        )
        
        // Convert APDU logs (empty for now, will be enhanced in future phases)
        val apduLogs = mutableListOf<ApduLogEntry>()
        
        return AppCardProfile(
            id = storageProfile.profileId,
            createdAt = instantToDate(storageProfile.createdAt),
            emvCardData = emvData,
            apduLogs = apduLogs,
            aid = storageProfile.configuration.aid,
            cardholderName = staticData.cardholderName,
            expirationDate = staticData.expiryDate,
            issuerName = storageProfile.issuer,
            applicationLabel = staticData.applicationLabel,
            notes = storageProfile.metadata.notes,
            tags = storageProfile.tags.toList()
        )
    }

    /**
     * Convert Date to Instant
     */
    private fun dateToInstant(date: Date): Instant {
        return Instant.ofEpochMilli(date.time)
    }

    /**
     * Convert Instant to Date
     */
    private fun instantToDate(instant: Instant): Date {
        return Date(instant.toEpochMilli())
    }
}
