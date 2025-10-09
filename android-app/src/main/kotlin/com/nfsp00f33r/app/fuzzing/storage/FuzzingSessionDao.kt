package com.nfsp00f33r.app.fuzzing.storage

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/**
 * DAO for fuzzing session persistence
 */
@Dao
interface FuzzingSessionDao {
    
    // Sessions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: FuzzingSessionEntity)
    
    @Update
    suspend fun updateSession(session: FuzzingSessionEntity)
    
    @Query("SELECT * FROM fuzzing_sessions WHERE session_id = :sessionId")
    suspend fun getSession(sessionId: String): FuzzingSessionEntity?
    
    @Query("SELECT * FROM fuzzing_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<FuzzingSessionEntity>>
    
    @Query("SELECT * FROM fuzzing_sessions WHERE terminal_id = :terminalId ORDER BY start_time DESC")
    fun getSessionsByTerminal(terminalId: String): Flow<List<FuzzingSessionEntity>>
    
    @Query("SELECT * FROM fuzzing_sessions WHERE strategy_type = :strategyType ORDER BY start_time DESC")
    fun getSessionsByStrategy(strategyType: String): Flow<List<FuzzingSessionEntity>>
    
    @Query("DELETE FROM fuzzing_sessions WHERE session_id = :sessionId")
    suspend fun deleteSession(sessionId: String)
    
    @Query("SELECT COUNT(*) FROM fuzzing_sessions")
    suspend fun getSessionCount(): Int
    
    // Anomalies
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomaly(anomaly: FuzzingAnomalyEntity): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnomalies(anomalies: List<FuzzingAnomalyEntity>)
    
    @Update
    suspend fun updateAnomaly(anomaly: FuzzingAnomalyEntity)
    
    @Query("SELECT * FROM fuzzing_anomalies WHERE session_id = :sessionId ORDER BY discovered_at DESC")
    suspend fun getAnomaliesBySession(sessionId: String): List<FuzzingAnomalyEntity>
    
    @Query("SELECT * FROM fuzzing_anomalies WHERE reproducible = 1 ORDER BY severity DESC, discovered_at DESC")
    suspend fun getReproducibleAnomalies(): List<FuzzingAnomalyEntity>
    
    @Query("SELECT * FROM fuzzing_anomalies WHERE severity = :severity ORDER BY discovered_at DESC")
    suspend fun getAnomaliesBySeverity(severity: String): List<FuzzingAnomalyEntity>
    
    @Query("UPDATE fuzzing_anomalies SET reproducible = :reproducible, reproduction_count = :count WHERE anomaly_id = :anomalyId")
    suspend fun updateAnomalyReproducibility(anomalyId: Long, reproducible: Boolean, count: Int)
    
    // Terminals
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTerminal(terminal: TerminalEntity)
    
    @Update
    suspend fun updateTerminal(terminal: TerminalEntity)
    
    @Query("SELECT * FROM terminals WHERE terminal_id = :terminalId")
    suspend fun getTerminal(terminalId: String): TerminalEntity?
    
    @Query("SELECT * FROM terminals ORDER BY last_fuzzed DESC")
    fun getAllTerminals(): Flow<List<TerminalEntity>>
    
    @Query("DELETE FROM terminals WHERE terminal_id = :terminalId")
    suspend fun deleteTerminal(terminalId: String)
    
    // Presets
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: FuzzingPresetEntity): Long
    
    @Update
    suspend fun updatePreset(preset: FuzzingPresetEntity)
    
    @Query("SELECT * FROM fuzzing_presets ORDER BY is_builtin DESC, preset_name ASC")
    fun getAllPresets(): Flow<List<FuzzingPresetEntity>>
    
    @Query("SELECT * FROM fuzzing_presets WHERE preset_id = :presetId")
    suspend fun getPreset(presetId: Long): FuzzingPresetEntity?
    
    @Query("SELECT * FROM fuzzing_presets WHERE target_vulnerability = :vulnerability")
    suspend fun getPresetsByVulnerability(vulnerability: String): List<FuzzingPresetEntity>
    
    @Query("DELETE FROM fuzzing_presets WHERE preset_id = :presetId AND is_builtin = 0")
    suspend fun deletePreset(presetId: Long)
    
    // Statistics
    @Query("""
        SELECT COUNT(*) as total_anomalies 
        FROM fuzzing_anomalies fa 
        INNER JOIN fuzzing_sessions fs ON fa.session_id = fs.session_id 
        WHERE fs.terminal_id = :terminalId
    """)
    suspend fun getTotalAnomaliesForTerminal(terminalId: String): Int
    
    @Query("""
        SELECT SUM(tests_executed) as total_tests 
        FROM fuzzing_sessions 
        WHERE terminal_id = :terminalId
    """)
    suspend fun getTotalTestsForTerminal(terminalId: String): Int?
}
