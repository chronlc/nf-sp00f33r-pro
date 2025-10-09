package com.nfsp00f33r.app.fuzzing

import com.nfsp00f33r.app.fuzzing.models.*
import com.nfsp00f33r.app.fuzzing.strategies.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.time.Instant
import java.util.UUID

/**
 * Core fuzzing engine that orchestrates fuzzing sessions
 * Manages strategy execution, result analysis, and metrics tracking
 */
class FuzzingEngine {
    
    private val _sessionState = MutableStateFlow(FuzzingSessionState.IDLE)
    val sessionState: StateFlow<FuzzingSessionState> = _sessionState.asStateFlow()
    
    private val _currentMetrics = MutableStateFlow(FuzzingMetrics())
    val currentMetrics: StateFlow<FuzzingMetrics> = _currentMetrics.asStateFlow()
    
    private val analytics = FuzzingAnalytics()
    private var currentStrategy: FuzzingStrategy? = null
    private var fuzzingJob: Job? = null
    private var sessionId: String? = null
    private var startTime: Instant? = null
    private var config: FuzzConfig? = null
    
    // Mock NFC executor (in real implementation, this would be PN532Module)
    private var nfcExecutor: suspend (ByteArray) -> Pair<ByteArray?, Long> = { command ->
        delay(10L + kotlin.random.Random.nextInt(100)) // Simulate NFC timing
        
        // Mock response generator for testing
        val mockResponse = when {
            command.size < 5 -> null // Simulate crash for malformed
            command[1] == 0xA4.toByte() -> byteArrayOf(0x90.toByte(), 0x00) // SELECT success
            command[1] == 0xB2.toByte() -> byteArrayOf(0x6A.toByte(), 0x82.toByte()) // READ RECORD error
            else -> {
                val data = ByteArray((0..20).random())
                data + byteArrayOf(0x90.toByte(), 0x00)
            }
        }
        
        Pair(mockResponse, 10 + (0..100).random().toLong())
    }
    
    /**
     * Set custom NFC command executor
     * @param executor Function that takes ByteArray command and returns (response, executionTime)
     */
    fun setNfcExecutor(executor: suspend (ByteArray) -> Pair<ByteArray?, Long>) {
        this.nfcExecutor = executor
    }
    
    /**
     * Start a new fuzzing session
     */
    suspend fun startFuzzing(configuration: FuzzConfig) {
        if (_sessionState.value != FuzzingSessionState.IDLE && 
            _sessionState.value != FuzzingSessionState.STOPPED) {
            Timber.w("⚠️ Cannot start fuzzing: Session already active")
            return
        }
        
        Timber.i("🚀 Starting fuzzing session with ${configuration.strategy}")
        
        config = configuration
        sessionId = UUID.randomUUID().toString()
        startTime = Instant.now()
        analytics.reset()
        
        // Create strategy instance
        currentStrategy = createStrategy(configuration)
        currentStrategy?.reset()
        
        _sessionState.value = FuzzingSessionState.INITIALIZING
        delay(500) // Brief initialization
        
        _sessionState.value = FuzzingSessionState.RUNNING
        
        // Start fuzzing coroutine
        fuzzingJob = CoroutineScope(Dispatchers.Default).launch {
            executeFuzzingLoop(configuration)
        }
    }
    
    private fun createStrategy(config: FuzzConfig): FuzzingStrategy {
        return when (config.strategy) {
            FuzzingStrategyType.RANDOM -> RandomFuzzingStrategy(
                maxTests = config.maxTests
            )
            FuzzingStrategyType.MUTATION -> {
                // Common seed APDUs for mutation
                val seeds = listOf(
                    byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, 0x00), // SELECT
                    byteArrayOf(0x00, 0xB2.toByte(), 0x01, 0x0C, 0x00), // READ RECORD
                    byteArrayOf(0x00, 0xCA.toByte(), 0x9F.toByte(), 0x36, 0x00), // GET DATA
                    byteArrayOf(0x80.toByte(), 0xAE.toByte(), 0x80.toByte(), 0x00, 0x00), // GENERATE AC
                    byteArrayOf(0x80.toByte(), 0xA8.toByte(), 0x00, 0x00, 0x02, 0x83.toByte(), 0x00) // GPO
                )
                MutationFuzzingStrategy(
                    seedCommands = seeds,
                    mutationsPerSeed = config.maxTests / seeds.size
                )
            }
            FuzzingStrategyType.PROTOCOL_AWARE -> ProtocolAwareFuzzingStrategy(
                maxTests = config.maxTests
            )
        }
    }
    
    private suspend fun executeFuzzingLoop(config: FuzzConfig) {
        val strategy = currentStrategy ?: return
        var testNumber = 0
        val delayBetweenTests = 1000L / config.testsPerSecond // Rate limiting
        
        try {
            while (_sessionState.value == FuzzingSessionState.RUNNING && 
                   !strategy.shouldTerminate() && 
                   testNumber < config.maxTests) {
                
                // Generate next test input
                val command = strategy.generateNextInput(null)
                testNumber++
                
                // Execute command and measure timing
                val (response, executionTime) = withTimeoutOrNull(config.timeoutMs) {
                    nfcExecutor(command)
                } ?: (null to config.timeoutMs)
                
                // Extract status word if present
                val statusWord = response?.let {
                    if (it.size >= 2) {
                        "%02X%02X".format(it[it.size - 2], it[it.size - 1])
                    } else null
                }
                
                // Create test result
                val result = FuzzTestResult(
                    testNumber = testNumber,
                    command = command,
                    response = response,
                    statusWord = statusWord,
                    executionTimeMs = executionTime,
                    timestamp = Instant.now(),
                    anomalies = emptyList()
                )
                
                // Analyze result
                val anomalies = if (config.enableCrashDetection) {
                    analytics.analyzeResult(result.copy(anomalies = emptyList()))
                } else {
                    emptyList()
                }
                
                // Update result with detected anomalies
                val finalResult = result.copy(anomalies = anomalies)
                analytics.analyzeResult(finalResult)
                
                // Update metrics
                val elapsed = Instant.now().toEpochMilli() - (startTime?.toEpochMilli() ?: 0L)
                _currentMetrics.value = analytics.getMetrics(elapsed, strategy.getName())
                
                // Rate limiting
                delay(delayBetweenTests)
                
                // Check for pause
                while (_sessionState.value == FuzzingSessionState.PAUSED) {
                    delay(100)
                }
            }
            
            // Fuzzing complete
            _sessionState.value = FuzzingSessionState.STOPPED
            Timber.i("✅ Fuzzing session complete: $testNumber tests executed")
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Fuzzing error")
            _sessionState.value = FuzzingSessionState.ERROR
        }
    }
    
    /**
     * Pause the current fuzzing session
     */
    fun pauseFuzzing() {
        if (_sessionState.value == FuzzingSessionState.RUNNING) {
            _sessionState.value = FuzzingSessionState.PAUSED
            Timber.i("⏸️ Fuzzing paused")
        }
    }
    
    /**
     * Resume paused fuzzing session
     */
    fun resumeFuzzing() {
        if (_sessionState.value == FuzzingSessionState.PAUSED) {
            _sessionState.value = FuzzingSessionState.RUNNING
            Timber.i("▶️ Fuzzing resumed")
        }
    }
    
    /**
     * Stop the current fuzzing session
     */
    fun stopFuzzing() {
        fuzzingJob?.cancel()
        _sessionState.value = FuzzingSessionState.STOPPED
        Timber.i("⏹️ Fuzzing stopped")
    }
    
    /**
     * Get complete session data
     */
    fun getSession(): FuzzingSession {
        return FuzzingSession(
            sessionId = sessionId ?: "unknown",
            config = config ?: FuzzConfig(),
            state = _sessionState.value,
            startTime = startTime,
            endTime = if (_sessionState.value == FuzzingSessionState.STOPPED) Instant.now() else null,
            metrics = _currentMetrics.value,
            anomalies = analytics.getAllAnomalies(),
            interestingFindings = analytics.getInterestingFindings()
        )
    }
    
    /**
     * Get interesting findings from current session
     */
    fun getInterestingFindings(): List<FuzzTestResult> {
        return analytics.getInterestingFindings()
    }
    
    /**
     * Get all detected anomalies
     */
    fun getAllAnomalies(): List<Anomaly> {
        return analytics.getAllAnomalies()
    }
    
    /**
     * Reset engine to idle state
     */
    fun reset() {
        stopFuzzing()
        analytics.reset()
        currentStrategy = null
        sessionId = null
        startTime = null
        config = null
        _sessionState.value = FuzzingSessionState.IDLE
        _currentMetrics.value = FuzzingMetrics()
        Timber.i("🔄 Fuzzing engine reset")
    }
}
