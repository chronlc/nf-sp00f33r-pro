package com.nfsp00f33r.app.storage.emv

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Delete
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Single DAO for ALL EMV card session operations
 * Phase 1: EmvSessionDatabase Creation (SIMPLIFIED DESIGN)
 * 
 * ONE DAO FOR EVERYTHING - Clean and simple!
 */
@Dao
interface EmvCardSessionDao {
    
    // === CREATE ===
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: EmvCardSessionEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(sessions: List<EmvCardSessionEntity>)
    
    // === READ (Basic) ===
    
    @Query("SELECT * FROM emv_card_sessions ORDER BY scanTimestamp DESC")
    suspend fun getAllSessions(): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions ORDER BY scanTimestamp DESC")
    fun getAllSessionsFlow(): Flow<List<EmvCardSessionEntity>>
    
    @Query("SELECT * FROM emv_card_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): EmvCardSessionEntity?
    
    // === READ (Filtered) ===
    
    @Query("SELECT * FROM emv_card_sessions WHERE cardUid = :cardUid ORDER BY scanTimestamp DESC")
    suspend fun getSessionsByCardUid(cardUid: String): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions WHERE pan = :pan ORDER BY scanTimestamp DESC")
    suspend fun getSessionsByPan(pan: String): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions WHERE cardBrand = :brand ORDER BY scanTimestamp DESC")
    suspend fun getSessionsByBrand(brand: String): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions WHERE rocaVulnerable = 1 ORDER BY scanTimestamp DESC")
    suspend fun getVulnerableSessions(): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions WHERE hasEncryptedData = 1 ORDER BY scanTimestamp DESC")
    suspend fun getEncryptedSessions(): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions WHERE scanStatus = :status ORDER BY scanTimestamp DESC")
    suspend fun getSessionsByStatus(status: String): List<EmvCardSessionEntity>
    
    // === READ (Search) ===
    
    @Query("""
        SELECT * FROM emv_card_sessions 
        WHERE pan LIKE '%' || :query || '%' 
           OR maskedPan LIKE '%' || :query || '%'
           OR cardholderName LIKE '%' || :query || '%'
           OR applicationLabel LIKE '%' || :query || '%'
           OR cardBrand LIKE '%' || :query || '%'
           OR cardUid LIKE '%' || :query || '%'
        ORDER BY scanTimestamp DESC
    """)
    suspend fun searchSessions(query: String): List<EmvCardSessionEntity>
    
    // === READ (Statistics) ===
    
    @Query("SELECT COUNT(*) FROM emv_card_sessions")
    suspend fun getSessionCount(): Int
    
    @Query("SELECT COUNT(*) FROM emv_card_sessions WHERE rocaVulnerable = 1")
    suspend fun getVulnerableSessionCount(): Int
    
    @Query("SELECT COUNT(*) FROM emv_card_sessions WHERE hasEncryptedData = 1")
    suspend fun getEncryptedSessionCount(): Int
    
    @Query("SELECT COUNT(*) FROM emv_card_sessions WHERE scanStatus = 'ERROR'")
    suspend fun getErrorSessionCount(): Int
    
    @Query("SELECT DISTINCT cardBrand FROM emv_card_sessions WHERE cardBrand IS NOT NULL ORDER BY cardBrand")
    suspend fun getAllCardBrands(): List<String>
    
    @Query("SELECT COUNT(DISTINCT cardUid) FROM emv_card_sessions")
    suspend fun getUniqueCardCount(): Int
    
    @Query("SELECT AVG(scanDuration) FROM emv_card_sessions WHERE scanStatus = 'SUCCESS'")
    suspend fun getAverageScanDuration(): Double?
    
    @Query("SELECT SUM(totalTags) FROM emv_card_sessions")
    suspend fun getTotalTagsScanned(): Int?
    
    // === READ (Time-based) ===
    
    @Query("SELECT * FROM emv_card_sessions WHERE scanTimestamp >= :timestamp ORDER BY scanTimestamp DESC")
    suspend fun getSessionsAfter(timestamp: Long): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions WHERE scanTimestamp BETWEEN :start AND :end ORDER BY scanTimestamp DESC")
    suspend fun getSessionsBetween(start: Long, end: Long): List<EmvCardSessionEntity>
    
    @Query("SELECT * FROM emv_card_sessions ORDER BY scanTimestamp DESC LIMIT :limit")
    suspend fun getRecentSessions(limit: Int): List<EmvCardSessionEntity>
    
    // === UPDATE ===
    
    @Update
    suspend fun update(session: EmvCardSessionEntity)
    
    @Query("UPDATE emv_card_sessions SET rocaVulnerable = :vulnerable, rocaKeyModulus = :modulus WHERE sessionId = :sessionId")
    suspend fun updateRocaStatus(sessionId: String, vulnerable: Boolean, modulus: String?)
    
    // === DELETE ===
    
    @Delete
    suspend fun delete(session: EmvCardSessionEntity)
    
    @Query("DELETE FROM emv_card_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)
    
    @Query("DELETE FROM emv_card_sessions WHERE cardUid = :cardUid")
    suspend fun deleteByCardUid(cardUid: String)
    
    @Query("DELETE FROM emv_card_sessions WHERE scanTimestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    @Query("DELETE FROM emv_card_sessions")
    suspend fun deleteAll()
}
