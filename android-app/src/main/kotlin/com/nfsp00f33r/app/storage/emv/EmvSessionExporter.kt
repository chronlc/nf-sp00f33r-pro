package com.nfsp00f33r.app.storage.emv

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.*

/**
 * EmvSessionExporter - Phase 5: Proxmark3-Compatible JSON Export
 * 
 * Exports complete EMV card session data in Proxmark3-compatible format
 * Includes all 200+ EMV tags, complete APDU log, and session metadata
 * 
 * Features:
 * - Single session export (by ID)
 * - All sessions bulk export
 * - Proxmark3-compatible format with complete tag data
 * - Human-readable timestamps and metadata
 * - Complete APDU command/response pairs
 * 
 * Output format compatible with:
 * - Proxmark3 EMV tools
 * - EMV analyzer applications
 * - Research and forensic analysis
 */
object EmvSessionExporter {
    
    private const val TAG = "EmvSessionExporter"
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    
    /**
     * Export single session to Proxmark3-compatible JSON
     */
    suspend fun toProxmark3Json(
        dao: EmvCardSessionDao,
        sessionId: String
    ): String? {
        return try {
            val session = dao.getSessionById(sessionId)
            if (session == null) {
                Timber.w("$TAG Session not found: $sessionId")
                return null
            }
            
            val jsonObject = sessionToProxmark3Json(session)
            jsonObject.toString(2) // Pretty print with 2-space indent
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to export session $sessionId")
            null
        }
    }
    
    /**
     * Export all sessions to Proxmark3-compatible JSON array
     */
    suspend fun exportAllSessions(dao: EmvCardSessionDao): String {
        return try {
            val sessions = dao.getAllSessions()
            Timber.i("$TAG Exporting ${sessions.size} sessions")
            
            val jsonArray = JSONArray()
            sessions.forEach { session ->
                val sessionJson = sessionToProxmark3Json(session)
                jsonArray.put(sessionJson)
            }
            
            // Wrap in container object with metadata
            val container = JSONObject().apply {
                put("export_version", "1.0")
                put("export_timestamp", dateFormatter.format(Date()))
                put("total_sessions", sessions.size)
                put("exporter", "nf-sp00f33r EmvSessionExporter")
                put("sessions", jsonArray)
            }
            
            container.toString(2) // Pretty print
            
        } catch (e: Exception) {
            Timber.e(e, "$TAG Failed to export all sessions")
            "{\"error\": \"Export failed: ${e.message}\"}"
        }
    }
    
    /**
     * Convert EmvCardSessionEntity to Proxmark3-compatible JSON
     */
    private fun sessionToProxmark3Json(session: EmvCardSessionEntity): JSONObject {
        return JSONObject().apply {
            // Session metadata
            put("session_id", session.sessionId)
            put("scan_timestamp", dateFormatter.format(Date(session.scanTimestamp)))
            put("scan_duration_ms", session.scanDuration)
            put("scan_status", session.scanStatus)
            if (session.errorMessage != null) {
                put("error_message", session.errorMessage)
            }
            
            // Card identification
            put("card_uid", session.cardUid)
            put("pan", session.pan ?: "")
            put("masked_pan", session.maskedPan ?: "")
            put("expiry_date", session.expiryDate ?: "")
            put("cardholder_name", session.cardholderName ?: "")
            put("card_brand", session.cardBrand ?: "UNKNOWN")
            put("application_label", session.applicationLabel ?: "")
            put("application_identifier", session.applicationIdentifier ?: "")
            
            // EMV capabilities
            put("aip", session.aip ?: "")
            put("capabilities", JSONObject().apply {
                put("sda", session.hasSda)
                put("dda", session.hasDda)
                put("cda", session.hasCda)
                put("cvm", session.supportsCvm)
            })
            
            // Cryptographic data
            put("cryptogram", JSONObject().apply {
                put("arqc", session.arqc ?: "")
                put("tc", session.tc ?: "")
                put("cid", session.cid ?: "")
                put("atc", session.atc ?: "")
            })
            
            // Security status
            put("security", JSONObject().apply {
                put("roca_vulnerable", session.rocaVulnerable)
                if (session.rocaKeyModulus != null) {
                    put("roca_key_modulus", session.rocaKeyModulus)
                }
                put("has_encrypted_data", session.hasEncryptedData)
            })
            
            // Complete EMV tags (200+ tags with enriched data)
            put("emv_tags", JSONObject().apply {
                session.allEmvTags.forEach { (tag, enrichedData) ->
                    put(tag, JSONObject().apply {
                        put("tag", enrichedData.tag)
                        put("name", enrichedData.name)
                        put("value", enrichedData.value)
                        put("value_decoded", enrichedData.valueDecoded ?: "")
                        put("phase", enrichedData.phase)
                        put("source", enrichedData.source)
                        put("length", enrichedData.length)
                    })
                }
            })
            
            // Complete APDU log (command/response pairs)
            put("apdu_log", JSONArray().apply {
                session.apduLog.forEach { apdu ->
                    put(JSONObject().apply {
                        put("sequence", apdu.sequence)
                        put("command", apdu.command)
                        put("response", apdu.response)
                        put("status_word", apdu.statusWord)
                        put("phase", apdu.phase)
                        put("description", apdu.description)
                        put("timestamp", apdu.timestamp)
                        put("execution_time_ms", apdu.executionTime)
                        put("success", apdu.isSuccess)
                    })
                }
            })
            
            // Phase-specific data (if available)
            if (session.ppseData != null) {
                put("ppse", JSONObject().apply {
                    put("fci_template", session.ppseData.fciTemplate ?: "")
                    put("df_name", session.ppseData.dfName ?: "")
                    put("application_template", session.ppseData.applicationTemplate ?: "")
                    put("aids", JSONArray(session.ppseData.aids))
                })
            }
            
            if (session.aidsData.isNotEmpty()) {
                put("aids", JSONArray().apply {
                    session.aidsData.forEach { aid ->
                        put(JSONObject().apply {
                            put("aid", aid.aid)
                            put("label", aid.label ?: "")
                            put("priority", aid.priority)
                            put("pdol", aid.pdol ?: "")
                        })
                    }
                })
            }
            
            if (session.gpoData != null) {
                put("gpo", JSONObject().apply {
                    put("aip", session.gpoData.aip)
                    put("afl", session.gpoData.afl)
                    put("response_format", session.gpoData.responseFormat)
                })
            }
            
            if (session.recordsData.isNotEmpty()) {
                put("records", JSONArray().apply {
                    session.recordsData.forEach { record ->
                        put(JSONObject().apply {
                            put("sfi", record.sfi)
                            put("record", record.record)
                            put("data", record.data)
                            put("tags", JSONObject(record.tags))
                        })
                    }
                })
            }
            
            if (session.cryptogramData != null) {
                put("cryptogram_data", JSONObject().apply {
                    put("arqc", session.cryptogramData.arqc ?: "")
                    put("tc", session.cryptogramData.tc ?: "")
                    put("aac", session.cryptogramData.aac ?: "")
                    put("cid", session.cryptogramData.cid)
                    put("atc", session.cryptogramData.atc)
                    put("iad", session.cryptogramData.iad ?: "")
                    put("cryptogram_type", session.cryptogramData.cryptogramType)
                })
            }
            
            // Statistics
            put("statistics", JSONObject().apply {
                put("total_apdus", session.totalApdus)
                put("total_tags", session.totalTags)
                put("record_count", session.recordCount)
            })
        }
    }
}
