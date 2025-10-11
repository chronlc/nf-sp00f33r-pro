package com.nfsp00f33r.app.storage.emv

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Index
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import com.nfsp00f33r.app.cardreading.EnrichedTagData

/**
 * Single comprehensive entity for complete EMV card session
 * Phase 1: EmvSessionDatabase Creation (SIMPLIFIED DESIGN)
 * 
 * ONE ROW = ONE COMPLETE CARD SCAN
 * - All 200+ EMV tags stored as JSON map
 * - Complete APDU log as JSON array
 * - All metadata in simple fields
 * 
 * Benefits:
 * - Single DAO (clean and simple)
 * - One query gets everything
 * - Easy to export (already in structured format)
 * - No complex joins or foreign keys
 */
@Entity(
    tableName = "emv_card_sessions",
    indices = [
        Index(value = ["cardUid"], unique = false),
        Index(value = ["pan"], unique = false),
        Index(value = ["rocaVulnerable"], unique = false),
        Index(value = ["scanTimestamp"], unique = false),
        Index(value = ["cardBrand"], unique = false)
    ]
)
@TypeConverters(EmvCardSessionConverters::class)
data class EmvCardSessionEntity(
    @PrimaryKey
    val sessionId: String,  // UUID
    
    // === SESSION METADATA ===
    val scanTimestamp: Long,
    val scanDuration: Long,  // milliseconds
    val scanStatus: String,  // "SUCCESS", "PARTIAL", "ERROR"
    val errorMessage: String?,
    
    // === CARD IDENTIFICATION ===
    val cardUid: String,  // NFC UID
    val pan: String?,  // Primary Account Number (tag 5A)
    val maskedPan: String?,  // Masked for display
    val expiryDate: String?,  // Tag 5F24
    val cardholderName: String?,  // Tag 5F20
    val cardBrand: String?,  // "Visa", "Mastercard", etc.
    val applicationLabel: String?,  // Tag 50
    val applicationIdentifier: String?,  // Selected AID
    
    // === EMV CAPABILITIES ===
    val aip: String?,  // Application Interchange Profile (tag 82)
    val hasSda: Boolean,
    val hasDda: Boolean,
    val hasCda: Boolean,
    val supportsCvm: Boolean,
    
    // === CRYPTOGRAPHIC DATA ===
    val arqc: String?,  // Tag 9F26
    val tc: String?,
    val cid: String?,  // Tag 9F27
    val atc: String?,  // Tag 9F36
    
    // === SECURITY STATUS ===
    val rocaVulnerable: Boolean,
    val rocaKeyModulus: String?,
    val hasEncryptedData: Boolean,
    
    // === COMPLETE EMV DATA (JSON STORAGE) ===
    // Everything stored - one simple map, one simple array
    
    // All 200+ EMV tags with enriched data (tag, name, value, decoded, phase, source, length)
    val allEmvTags: Map<String, EnrichedTagData>,
    
    // Complete APDU log - just one JSON array!
    val apduLog: List<ApduLogEntry>,    // Phase-categorized data (for quick access without parsing allEmvTags)
    val ppseData: PpseData?,
    val aidsData: List<AidData>,
    val gpoData: GpoData?,
    val recordsData: List<RecordData>,
    val cryptogramData: CryptogramData?,
    
    // === STATS ===
    val totalApdus: Int,
    val totalTags: Int,
    val recordCount: Int
)

/**
 * APDU log entry (stored as JSON in apduLog array)
 */
@Serializable
data class ApduLogEntry(
    val sequence: Int,
    val command: String,
    val response: String,
    val statusWord: String,
    val phase: String,
    val description: String,
    val timestamp: Long,
    val executionTime: Long,
    val isSuccess: Boolean
)

/**
 * PPSE data structure
 */
@Serializable
data class PpseData(
    val fciTemplate: String?,
    val dfName: String?,
    val applicationTemplate: String?,
    val aids: List<String>
)

/**
 * AID data structure
 */
@Serializable
data class AidData(
    val aid: String,
    val label: String?,
    val priority: Int,
    val pdol: String?,
    val afl: String?,
    val languagePreference: String?
)

/**
 * GPO response data
 */
@Serializable
data class GpoData(
    val aip: String,
    val afl: String,
    val responseFormat: String  // "Format 1" or "Format 2"
)

/**
 * Record data structure
 */
@Serializable
data class RecordData(
    val sfi: Int,
    val record: Int,
    val data: String,
    val tags: Map<String, String>  // Tags found in this record
)

/**
 * Cryptogram data structure
 */
@Serializable
data class CryptogramData(
    val arqc: String?,
    val tc: String?,
    val aac: String?,
    val cid: String,
    val atc: String,
    val iad: String?,
    val cryptogramType: String  // "ARQC", "TC", "AAC"
)

/**
 * Type converters for Room database
 * Handles JSON serialization for complex types
 */
class EmvCardSessionConverters {
    
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }
    
    // === Map<String, EmvTagData> ===
    
    @TypeConverter
    fun fromEmvTagsMap(value: Map<String, EnrichedTagData>): String {
        return Json.encodeToString(value)
    }
    
    @TypeConverter
    fun toEmvTagsMap(value: String): Map<String, EnrichedTagData> {
        return Json.decodeFromString(value)
    }    // === List<ApduLogEntry> ===
    
    @TypeConverter
    fun fromApduLogList(value: List<ApduLogEntry>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toApduLogList(value: String): List<ApduLogEntry> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // === PpseData ===
    
    @TypeConverter
    fun fromPpseData(value: PpseData?): String? {
        return value?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toPpseData(value: String?): PpseData? {
        return value?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // === List<AidData> ===
    
    @TypeConverter
    fun fromAidDataList(value: List<AidData>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toAidDataList(value: String): List<AidData> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // === GpoData ===
    
    @TypeConverter
    fun fromGpoData(value: GpoData?): String? {
        return value?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toGpoData(value: String?): GpoData? {
        return value?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    // === List<RecordData> ===
    
    @TypeConverter
    fun fromRecordDataList(value: List<RecordData>): String {
        return json.encodeToString(value)
    }
    
    @TypeConverter
    fun toRecordDataList(value: String): List<RecordData> {
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    // === CryptogramData ===
    
    @TypeConverter
    fun fromCryptogramData(value: CryptogramData?): String? {
        return value?.let { json.encodeToString(it) }
    }
    
    @TypeConverter
    fun toCryptogramData(value: String?): CryptogramData? {
        return value?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}
