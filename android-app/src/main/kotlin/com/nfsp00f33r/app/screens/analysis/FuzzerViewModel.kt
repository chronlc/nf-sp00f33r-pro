package com.nfsp00f33r.app.screens.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfsp00f33r.app.fuzzing.FuzzingEngine
import com.nfsp00f33r.app.fuzzing.models.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * ViewModel for Terminal Fuzzer screen
 * Manages fuzzing engine and UI state
 */
class FuzzerViewModel : ViewModel() {
    
    private val fuzzingEngine = FuzzingEngine()
    
    // UI state
    private val _uiState = MutableStateFlow(FuzzerUiState())
    val uiState: StateFlow<FuzzerUiState> = _uiState.asStateFlow()
    
    // Configuration
    private val _selectedStrategy = MutableStateFlow(FuzzingStrategyType.MUTATION)
    val selectedStrategy: StateFlow<FuzzingStrategyType> = _selectedStrategy.asStateFlow()
    
    private val _maxTests = MutableStateFlow(500)
    val maxTests: StateFlow<Int> = _maxTests.asStateFlow()
    
    private val _testsPerSecond = MutableStateFlow(10)
    val testsPerSecond: StateFlow<Int> = _testsPerSecond.asStateFlow()
    
    init {
        // Observe engine state
        viewModelScope.launch {
            fuzzingEngine.sessionState.collect { state ->
                _uiState.value = _uiState.value.copy(sessionState = state)
            }
        }
        
        // Observe metrics
        viewModelScope.launch {
            fuzzingEngine.currentMetrics.collect { metrics ->
                _uiState.value = _uiState.value.copy(metrics = metrics)
            }
        }
    }
    
    fun startFuzzing() {
        viewModelScope.launch {
            val config = FuzzConfig(
                strategy = _selectedStrategy.value,
                maxTests = _maxTests.value,
                timeoutMs = 5000,
                testsPerSecond = _testsPerSecond.value,
                enableCrashDetection = true,
                saveResults = true
            )
            
            Timber.i("🎯 Starting fuzzing with config: $config")
            fuzzingEngine.startFuzzing(config)
        }
    }
    
    fun pauseFuzzing() {
        fuzzingEngine.pauseFuzzing()
    }
    
    fun resumeFuzzing() {
        fuzzingEngine.resumeFuzzing()
    }
    
    fun stopFuzzing() {
        fuzzingEngine.stopFuzzing()
        
        // Update UI with final results
        viewModelScope.launch {
            val findings = fuzzingEngine.getInterestingFindings()
            val anomalies = fuzzingEngine.getAllAnomalies()
            _uiState.value = _uiState.value.copy(
                interestingFindings = findings,
                anomalies = anomalies
            )
        }
    }
    
    fun resetEngine() {
        fuzzingEngine.reset()
        _uiState.value = FuzzerUiState()
    }
    
    fun setStrategy(strategy: FuzzingStrategyType) {
        _selectedStrategy.value = strategy
    }
    
    fun setMaxTests(tests: Int) {
        _maxTests.value = tests.coerceIn(10, 10000)
    }
    
    fun setTestsPerSecond(rate: Int) {
        _testsPerSecond.value = rate.coerceIn(1, 100)
    }
    
    fun loadInterestingFindings() {
        viewModelScope.launch {
            val findings = fuzzingEngine.getInterestingFindings()
            _uiState.value = _uiState.value.copy(interestingFindings = findings)
        }
    }
    
    fun loadAnomalies() {
        viewModelScope.launch {
            val anomalies = fuzzingEngine.getAllAnomalies()
            _uiState.value = _uiState.value.copy(anomalies = anomalies)
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        fuzzingEngine.reset()
    }
}

/**
 * UI state for Fuzzer screen
 */
data class FuzzerUiState(
    val sessionState: FuzzingSessionState = FuzzingSessionState.IDLE,
    val metrics: FuzzingMetrics = FuzzingMetrics(),
    val interestingFindings: List<FuzzTestResult> = emptyList(),
    val anomalies: List<Anomaly> = emptyList(),
    val errorMessage: String? = null
)
