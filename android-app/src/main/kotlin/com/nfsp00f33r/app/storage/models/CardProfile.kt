package com.nfsp00f33r.app.storage.models

import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Complete card profile containing all captured EMV data
 */
@Serializable
data class CardProfile(
    val profileId: String = UUID.randomUUID().toString(),
    val cardType: CardType,
    val issuer: String,
    val staticData: StaticCardData,
    val dynamicData: DynamicCardData,
    val configuration: CardConfiguration,
    val cryptographicData: CryptographicData,
    val transactionHistory: List<TransactionRecord> = emptyList(),
    val metadata: ProfileMetadata = ProfileMetadata(),
    val tags: Set<String> = emptySet(),
    val version: Int = 1,
    @Serializable(with = InstantSerializer::class)
    val createdAt: Instant = Instant.now(),
    @Serializable(with = InstantSerializer::class)
    val lastModified: Instant = Instant.now()
)

/**
 * Card type enumeration
 */
@Serializable
enum class CardType {
    VISA,
    MASTERCARD,
    AMERICAN_EXPRESS,
    DISCOVER,
    JCB,
    UNIONPAY,
    MAESTRO,
    UNKNOWN
}

/**
 * Static card data (unchanging between transactions)
 */
@Serializable
data class StaticCardData(
    val pan: String,                    // Primary Account Number
    val panSequenceNumber: String? = null,
    val expiryDate: String,             // YYMM format
    val cardholderName: String? = null,
    val serviceCode: String? = null,
    val track1Data: String? = null,
    val track2EquivalentData: String? = null,
    val track2Data: String? = null,
    val issuerCountryCode: String? = null,
    val currencyCode: String? = null,
    val languagePreference: String? = null,
    val applicationLabel: String? = null,
    val applicationPreferredName: String? = null
)

/**
 * Dynamic card data (changes with each transaction)
 */
@Serializable
data class DynamicCardData(
    val atc: Int,                       // Application Transaction Counter
    val lastOnlineATC: Int = 0,
    val pinTryCounter: Int? = null,
    val logFormat: String? = null,
    val transactionLog: List<String> = emptyList()
)

/**
 * Card configuration and capabilities
 */
@Serializable
data class CardConfiguration(
    val aid: String,                    // Application Identifier
    val aip: String,                    // Application Interchange Profile
    val afl: String? = null,            // Application File Locator
    val cvmList: List<CVMRule> = emptyList(),
    val iacDefault: String? = null,     // Issuer Action Code - Default
    val iacDenial: String? = null,      // Issuer Action Code - Denial
    val iacOnline: String? = null,      // Issuer Action Code - Online
    val tacDefault: String? = null,     // Terminal Action Code - Default
    val tacDenial: String? = null,      // Terminal Action Code - Denial
    val tacOnline: String? = null,      // Terminal Action Code - Online
    val pdol: String? = null,           // Processing Options Data Object List
    val cdol1: String? = null,          // Card Risk Management Data Object List 1
    val cdol2: String? = null,          // Card Risk Management Data Object List 2
    val applicationVersionNumber: String? = null,
    val applicationUsageControl: String? = null,
    val applicationEffectiveDate: String? = null,
    val applicationExpirationDate: String? = null
)

/**
 * Cardholder Verification Method rule
 */
@Serializable
data class CVMRule(
    val cvmCode: String,
    val cvmCondition: String,
    val description: String
)

/**
 * Cryptographic data and keys
 */
@Serializable
data class CryptographicData(
    val issuerPublicKeyCertificate: String? = null,
    val issuerPublicKeyExponent: String? = null,
    val issuerPublicKeyRemainder: String? = null,
    val iccPublicKeyCertificate: String? = null,
    val iccPublicKeyExponent: String? = null,
    val iccPublicKeyRemainder: String? = null,
    val caPublicKeyIndex: String? = null,
    val sdaTagList: String? = null,
    val offlineDataAuthenticationMethod: OfflineAuthMethod = OfflineAuthMethod.NONE,
    val vulnerabilities: List<CryptoVulnerability> = emptyList()
)

/**
 * Offline data authentication method
 */
@Serializable
enum class OfflineAuthMethod {
    NONE,
    SDA,    // Static Data Authentication
    DDA,    // Dynamic Data Authentication
    CDA,    // Combined DDA/Application Cryptogram
    FDDA    // Fast DDA (Visa)
}

/**
 * Cryptographic vulnerability
 */
@Serializable
data class CryptoVulnerability(
    val type: VulnerabilityType,
    val severity: Severity,
    val description: String,
    val cveId: String? = null,
    @Serializable(with = InstantSerializer::class)
    val detectedAt: Instant = Instant.now()
)

@Serializable
enum class VulnerabilityType {
    ROCA,
    WEAK_KEYS,
    PREDICTABLE_UN,
    LOW_ENTROPY_ATC,
    DOWNGRADE_POSSIBLE,
    CVM_BYPASS,
    RELAY_VULNERABLE,
    OTHER
}

@Serializable
enum class Severity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/**
 * Transaction record
 */
@Serializable
data class TransactionRecord(
    val transactionId: String = UUID.randomUUID().toString(),
    val amount: Long,
    val currency: String,
    val merchantName: String? = null,
    val merchantId: String? = null,
    val terminalId: String? = null,
    val transactionType: TransactionType,
    val authorizationCode: String? = null,
    val arqc: String? = null,           // Application Request Cryptogram
    val tc: String? = null,             // Transaction Certificate
    val aac: String? = null,            // Application Authentication Cryptogram
    val cid: String? = null,            // Cryptogram Information Data
    val atc: Int,
    val unpredictableNumber: String,
    val tvr: String? = null,            // Terminal Verification Results
    val tsi: String? = null,            // Transaction Status Information
    val cvmResults: String? = null,
    val iad: String? = null,            // Issuer Application Data
    @Serializable(with = InstantSerializer::class)
    val timestamp: Instant = Instant.now(),
    val rawApduLog: List<String> = emptyList()
)

@Serializable
enum class TransactionType {
    PURCHASE,
    CASH_ADVANCE,
    REFUND,
    BALANCE_INQUIRY,
    CASH_DEPOSIT,
    PAYMENT,
    TRANSFER,
    OTHER
}

/**
 * Profile metadata
 */
@Serializable
data class ProfileMetadata(
    val captureMethod: CaptureMethod = CaptureMethod.UNKNOWN,
    val captureDevice: String? = null,
    val captureLocation: String? = null,
    val notes: String? = null,
    val customFields: Map<String, String> = emptyMap()
)

@Serializable
enum class CaptureMethod {
    NFC_READER,
    CONTACT_READER,
    MITM_PROXY,
    TERMINAL_EMULATION,
    CARD_EMULATION,
    UNKNOWN
}

/**
 * Instant serializer for kotlinx.serialization
 */
object InstantSerializer : kotlinx.serialization.KSerializer<Instant> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor(
        "Instant",
        kotlinx.serialization.descriptors.PrimitiveKind.STRING
    )
    
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Instant) {
        encoder.encodeString(value.toString())
    }
    
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Instant {
        return Instant.parse(decoder.decodeString())
    }
}