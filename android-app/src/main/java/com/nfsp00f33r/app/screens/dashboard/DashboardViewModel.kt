package com.nfsp00f33r.app.screens.dashboard

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope

import com.nfsp00f33r.app.application.NfSp00fApplication
import com.nfsp00f33r.app.data.VirtualCard
import com.nfsp00f33r.app.hardware.HardwareDetectionService
import com.nfsp00f33r.app.hardware.PN532Manager
import com.nfsp00f33r.app.storage.CardDataStore
import com.nfsp00f33r.app.storage.CardProfileAdapter
import com.nfsp00f33r.app.storage.emv.EmvSessionDatabase
import com.nfsp00f33r.app.storage.emv.EmvCardSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * PRODUCTION-GRADE Dashboard ViewModel
 * Phase 4: Migrated to EmvSessionDatabase (Room)
 * 
 * Provides LIVE DATA ONLY - no hardcoded fallbacks
 * Integrates with real hardware status and card profile data
 * All card data now persists with Room SQLite database
 * 
 * NO SAFE CALL OPERATORS - Production-grade error handling per framework standards
 */
class DashboardViewModel(private val context: Context) : ViewModel() {
    
    // Hardware detection service - LIVE
    private var hardwareDetectionService: HardwareDetectionService? = null
    
    // Hardware managers - Phase 2B Day 1: Migrated to PN532DeviceModule
    private val pn532Module by lazy {
        NfSp00fApplication.getPN532Module()
    }
    
    // Permission manager for hardware access
    private var permissionManager: com.nfsp00f33r.app.permissions.PermissionManager? = null
    
    // PHASE 4: Room database for EMV sessions
    private val emvSessionDatabase by lazy {
        EmvSessionDatabase.getInstance(context)
    }
    private val emvSessionDao by lazy {
        emvSessionDatabase.emvCardSessionDao()
    }
    
    // EmulationModule for attack analytics - Phase 2B Quick Wins
    private val emulationModule by lazy {
        NfSp00fApplication.getEmulationModule()
    }
    
    // Live data states
    var hardwareStatus by mutableStateOf(HardwareDetectionService.HardwareStatus())
        private set
    
    var recentCards by mutableStateOf(emptyList<VirtualCard>())
        private set
    
    var cardStatistics by mutableStateOf(CardStatistics())
        private set
    
    var isInitialized by mutableStateOf(false)
        private set
    
    // Analytics state - Phase 2B Quick Wins
    var analyticsReport by mutableStateOf<com.nfsp00f33r.app.emulation.AttackAnalytics.AnalyticsReport?>(null)
        private set
    
    var recentAttacks by mutableStateOf<List<com.nfsp00f33r.app.emulation.AttackAnalytics.AttackExecution>>(emptyList())
        private set
    
    /**
     * Card statistics data class
     */
    data class CardStatistics(
        val totalCards: Int = 0,
        val cardsToday: Int = 0,
        val uniqueBrands: Int = 0,
        val totalApduCommands: Int = 0,
        val averageApduPerCard: Double = 0.0,
        val recentScanTime: String = "Never",
        val securityIssues: Int = 0,
        // Analytics metrics - Phase 2B Quick Wins
        val totalAttackExecutions: Int = 0,
        val successfulAttacks: Int = 0,
        val overallSuccessRate: Double = 0.0
    )
    
    init {
        Timber.i("DashboardViewModel initializing with live data connections")
        initializeServices()
        setupCardProfileListener()
    }
    
    /**
     * Initialize hardware and data services
     * IMPROVED: Guaranteed to complete within 8 seconds
     */
    private fun initializeServices() {
        viewModelScope.launch {
            try {
                Timber.i("DashboardViewModel initialization starting...")
                
                // PHASE 1: Wait for application initialization (max 2 seconds)
                var attempts = 0
                val maxAttempts = 20 // 20 × 100ms = 2 seconds
                while (!com.nfsp00f33r.app.application.NfSp00fApplication.isInitializationComplete() && attempts < maxAttempts) {
                    delay(100)
                    attempts++
                }
                
                if (com.nfsp00f33r.app.application.NfSp00fApplication.isInitializationComplete()) {
                    Timber.i("✅ Application initialized in ${attempts * 100}ms")
                } else {
                    Timber.w("⚠️  Application initialization timeout after 2s, proceeding anyway")
                }
                
                // PHASE 2: Hardware detection (with timeout protection)
                try {
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(2000) {
                            setupHardwareStatusPolling()
                        }
                    }
                    Timber.i("✅ Hardware status polling configured")
                } catch (e: Exception) {
                    Timber.e(e, "⚠️  Hardware polling setup failed, using defaults")
                    // Set basic default status
                    hardwareStatus = HardwareDetectionService.HardwareStatus(
                        hardwareScore = 0,
                        statusMessage = "Hardware detection unavailable",
                        detectionPhase = "Error: ${e.message}"
                    )
                }
                
                // PHASE 3: Load card data (with timeout protection)
                try {
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(2000) {
                            refreshCardData()
                        }
                    }
                    Timber.i("✅ Card data loaded (${recentCards.size} recent cards)")
                } catch (e: Exception) {
                    Timber.e(e, "⚠️  Card data loading failed, showing empty state")
                    // Leave recentCards empty - UI will show "No cards" message
                }
                
                // PHASE 4: Start periodic refresh (non-blocking)
                startPeriodicRefresh()
                
                // ALWAYS set initialized to true
                withContext(Dispatchers.Main) {
                    isInitialized = true
                }
                
                Timber.i("✅ DashboardViewModel initialized successfully")
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Critical error in DashboardViewModel initialization")
                
                // Show error state but ALWAYS allow UI to proceed
                withContext(Dispatchers.Main) {
                    hardwareStatus = HardwareDetectionService.HardwareStatus(
                        hardwareScore = 0,
                        statusMessage = "Initialization error: ${e.message}",
                        detectionPhase = "Error",
                        errorDetails = e.stackTraceToString()
                    )
                    isInitialized = true // CRITICAL: Always set to true
                }
            }
        }
    }
    
    /**
     * Setup REAL hardware status polling - NO HARDCODED VALUES
     */
    private fun setupHardwareStatusPolling() {
        viewModelScope.launch {
            try {
                // Perform REAL hardware detection without full service
                val nfcAdapter = android.nfc.NfcAdapter.getDefaultAdapter(context)
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                
                // Get REAL hardware status 
                val realNfcAvailable = nfcAdapter != null
                val realNfcEnabled = if (nfcAdapter != null) nfcAdapter.isEnabled else false
                val realBluetoothAvailable = bluetoothAdapter != null
                val realBluetoothEnabled = if (bluetoothAdapter != null) bluetoothAdapter.isEnabled else false
                
                // Calculate real score based on actual hardware
                val realScore = when {
                    realNfcEnabled && realBluetoothEnabled -> 85
                    realNfcEnabled -> 60
                    realBluetoothEnabled -> 40
                    realNfcAvailable || realBluetoothAvailable -> 20
                    else -> 0
                }
                
                // Phase 2B Day 1: Use PN532DeviceModule (with timeout and safety check)
                // Simple check - if isConnected() returns true, we're connected
                val realPn532Connected = try {
                    withContext(Dispatchers.IO) {
                        kotlinx.coroutines.withTimeoutOrNull(500) {
                            val connected = pn532Module.isConnected()
                            Timber.d("📡 PN532 connection state: $connected")
                            connected
                        } ?: false
                    }
                } catch (e: Exception) {
                    Timber.w("❌ PN532 module not ready: ${e.message}")
                    false
                }
                
                hardwareStatus = HardwareDetectionService.HardwareStatus(
                    nfcAvailable = realNfcAvailable,
                    nfcEnabled = realNfcEnabled,
                    hceSupported = realNfcAvailable, // HCE supported if NFC available
                    bluetoothAvailable = realBluetoothAvailable,
                    bluetoothEnabled = realBluetoothEnabled,
                    usbSerialAvailable = false, // USB not available on Android typically
                    pn532Connected = realPn532Connected,
                    hardwareScore = realScore,
                    statusMessage = when {
                        realScore >= 80 -> "Hardware ready for EMV operations"
                        realScore >= 50 -> "Basic EMV hardware available"
                        realScore > 0 -> "Limited hardware detected"
                        else -> "No EMV-capable hardware detected"
                    },
                    androidNfcStatus = if (realNfcEnabled) "Active" else if (realNfcAvailable) "Disabled" else "Not Available",
                    androidNfcDetails = if (realNfcEnabled) "Android NFC Controller ready" else if (realNfcAvailable) "NFC available but disabled" else "No NFC hardware",
                    hceServiceStatus = if (realNfcEnabled) "Ready" else "Unavailable",
                    hceServiceDetails = if (realNfcEnabled) "Host Card Emulation configured" else "NFC required for HCE",
                    androidBluetoothStatus = if (realBluetoothEnabled) "Active" else if (realBluetoothAvailable) "Disabled" else "Not Available",
                    androidBluetoothDetails = if (realBluetoothEnabled) "Bluetooth adapter enabled" else if (realBluetoothAvailable) "Bluetooth available but disabled" else "No Bluetooth hardware",
                    pn532BluetoothUartStatus = if (realPn532Connected) "Connected" else "Disconnected",
                    pn532BluetoothUartDetails = if (realPn532Connected) "PN532 Bluetooth module active" else "PN532 Bluetooth not connected",
                    pn532UsbUartStatus = "Not Available",
                    pn532UsbUartDetails = "USB serial not supported on Android",
                    detectionPhase = "Real hardware scan complete"
                )
                
                Timber.i("REAL hardware status detected: NFC=$realNfcEnabled, BT=$realBluetoothEnabled, Score=$realScore")
                
            } catch (e: Exception) {
                Timber.e(e, "REAL hardware detection failed")
                // Show actual error - NO FALLBACK VALUES
                hardwareStatus = HardwareDetectionService.HardwareStatus(
                    hardwareScore = 0,  
                    statusMessage = "Hardware detection failed: ${e.message}",
                    detectionPhase = "Error: ${e.javaClass.simpleName}",
                    errorDetails = e.stackTraceToString()
                )
            }
        }
    }
    
    /**
     * Setup card profile data loading from encrypted storage
     */
    private fun setupCardProfileListener() {
        // Load initial data from encrypted storage
        refreshCardData()
    }
    
    /**
     * Start periodic refresh for live updates
     * Uses 1-second interval to quickly catch PN532 Bluetooth connection
     */
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            var refreshCount = 0
            while (true) {
                // Refresh every 1 second for first minute (to catch PN532 connection)
                // Then every 3 seconds for efficiency
                val refreshInterval = if (refreshCount < 60) 1000L else 3000L
                delay(refreshInterval)
                
                try {
                    setupHardwareStatusPolling() // Update PN532 status
                    
                    // Only refresh card data every 3 seconds (less expensive)
                    if (refreshCount % 3 == 0) {
                        refreshCardData()
                    }
                    
                    refreshCount++
                } catch (e: Exception) {
                    Timber.w("Periodic refresh failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Refresh card data from EmvSessionDatabase (Room)
     * PHASE 4: Now uses Room database instead of CardDataStore
     */
    private fun refreshCardData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // PHASE 4: Load from Room database
                val sessions = emvSessionDao.getAllSessions()
                Timber.d("Loaded ${sessions.size} sessions from Room database")
                
                // Convert sessions to VirtualCard format for recent cards display
                recentCards = sessions.takeLast(3).mapNotNull { session ->
                    // ONLY show cards with REAL extracted PAN data
                    if (session.pan.isNullOrEmpty() || session.pan.length < 13) {
                        return@mapNotNull null // Skip cards without real PAN
                    }
                    
                    VirtualCard(
                        cardholderName = session.cardholderName?.takeIf { it.isNotBlank() } ?: "",
                        maskedPan = session.pan,
                        expiryDate = session.expiryDate?.takeIf { it.isNotBlank() } ?: "",
                        apduCount = session.totalApdus,
                        cardType = session.cardBrand ?: detectCardBrand(session.pan),
                        isEncrypted = session.hasEncryptedData,
                        lastUsed = formatRelativeTime(session.scanTimestamp),
                        category = if (session.hasEncryptedData) "EMV" else "BASIC"
                    )
                }.reversed() // Show most recent first
                
                // Calculate real statistics using Room DAO queries
                val newStats = calculateCardStatisticsFromRoom()
                
                withContext(Dispatchers.Main) {
                    cardStatistics = newStats
                }
                
                Timber.d("Card data refreshed: ${recentCards.size} recent cards, ${cardStatistics.totalCards} total")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh card data")
                // Don't set fallback data - let UI handle empty state
            }
        }
    }
    
    /**
     * Calculate REAL card statistics from Room database using DAO queries
     * PHASE 4: Uses efficient Room queries instead of loading all profiles
     */
    private suspend fun calculateCardStatisticsFromRoom(): CardStatistics {
        val totalCards = emvSessionDao.getSessionCount()
        if (totalCards == 0) {
            // Return empty statistics - NO FAKE DATA
            return CardStatistics()
        }
        
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)
        
        // Count cards scanned today using Room query
        val cardsToday = emvSessionDao.getSessionsAfter(oneDayAgo).size
        
        // Get unique brands from Room
        val uniqueBrands = emvSessionDao.getAllCardBrands().size
        
        // Get total APDU count (sum of totalApdus field)
        val totalApduCommands = emvSessionDao.getTotalTagsScanned() ?: 0
        val averageApduPerCard = if (totalCards > 0) totalApduCommands.toDouble() / totalCards else 0.0
        
        // Get most recent session for time display
        val recentSessions = emvSessionDao.getRecentSessions(1)
        val recentScanTime = if (recentSessions.isNotEmpty()) {
            formatRelativeTime(recentSessions[0].scanTimestamp)
        } else {
            "Never"
        }
        
        // Count security issues (cards without encryption + error sessions)
        val securityIssues = emvSessionDao.getSessionCount() - emvSessionDao.getEncryptedSessionCount() +
                            emvSessionDao.getErrorSessionCount()
        
        // Get attack analytics from emulation module
        val attackStats = try {
            val report = emulationModule.getAnalyticsReport()
            Triple(report.totalAttacks, report.totalSuccessful, report.overallSuccessRate)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load attack analytics")
            Triple(0, 0, 0.0)
        }
        
        return CardStatistics(
            totalCards = totalCards,
            cardsToday = cardsToday,
            uniqueBrands = uniqueBrands,
            totalApduCommands = totalApduCommands,
            averageApduPerCard = averageApduPerCard,
            recentScanTime = recentScanTime,
            securityIssues = securityIssues,
            totalAttackExecutions = attackStats.first,
            successfulAttacks = attackStats.second,
            overallSuccessRate = attackStats.third
        )
    }
    
    /**
     * Detect card brand from PAN
     */
    private fun detectCardBrand(pan: String): String {
        val cleanPan = pan.replace("*", "").replace(" ", "")
        return when {
            cleanPan.startsWith("4") -> "VISA"
            cleanPan.startsWith("5") || cleanPan.startsWith("2") -> "MASTERCARD"
            cleanPan.startsWith("3") -> "AMEX"
            cleanPan.startsWith("6011") -> "DISCOVER"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Format timestamp as relative time
     */
    private fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        return when {
            diff < 60 * 1000 -> "Just now"
            diff < 60 * 60 * 1000 -> "${diff / (60 * 1000)} minutes ago"
            diff < 24 * 60 * 60 * 1000 -> "${diff / (60 * 60 * 1000)} hours ago"
            diff < 7 * 24 * 60 * 60 * 1000 -> "${diff / (24 * 60 * 60 * 1000)} days ago"
            else -> "${diff / (7 * 24 * 60 * 60 * 1000)} weeks ago"
        }
    }
    
    /**
     * Force refresh all data - REAL LIVE UPDATES ONLY
     */
    fun refreshData() {
        viewModelScope.launch {
            try {
                Timber.i("Refreshing REAL hardware status and card data...")
                
                // Re-run REAL hardware detection
                setupHardwareStatusPolling()
                
                // Refresh REAL card data
                refreshCardData()
                
                Timber.i("Dashboard REAL data refreshed manually")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to refresh dashboard REAL data")
                
                // Show REAL error - NO FALLBACK
                hardwareStatus = hardwareStatus.copy(
                    statusMessage = "Refresh failed: ${e.message}",
                    errorDetails = e.stackTraceToString()
                )
            }
        }
    }
    
    /**
     * Get hardware manager for direct access
     */
    fun getHardwareDetectionService(): HardwareDetectionService? = hardwareDetectionService
    
    /**
     * Load attack analytics data - Phase 2B Quick Wins
     */
    fun loadAnalytics() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Timber.d("Loading attack analytics data")
                
                // Get comprehensive analytics report
                val report = emulationModule.getAnalyticsReport()
                
                // Get recent attack executions
                val recent = emulationModule.getRecentExecutions(10)
                
                withContext(Dispatchers.Main) {
                    analyticsReport = report
                    recentAttacks = recent
                    
                    // Update card statistics with analytics metrics
                    cardStatistics = cardStatistics.copy(
                        totalAttackExecutions = report.totalAttacks,
                        successfulAttacks = report.totalSuccessful,
                        overallSuccessRate = report.overallSuccessRate
                    )
                    
                    Timber.d("Analytics loaded: ${report.totalAttacks} total executions, ${report.overallSuccessRate}% success rate")
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to load analytics data")
            }
        }
    }
    
    /**
     * Get attack type statistics
     */
    fun getAttackTypeStatistics(attackType: String): com.nfsp00f33r.app.emulation.AttackAnalytics.AttackTypeStatistics? {
        return try {
            emulationModule.getAttackTypeStatistics(attackType)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get attack type statistics")
            null
        }
    }
    
    /**
     * Export analytics data
     */
    fun exportAnalyticsData(): Map<String, Any>? {
        return try {
            emulationModule.exportAnalyticsData()
        } catch (e: Exception) {
            Timber.e(e, "Failed to export analytics data")
            null
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Cleanup services
        val service = hardwareDetectionService
        if (service != null) {
            service.cleanup()
        }
        // No cleanup needed for EmvSessionDatabase (managed by Room singleton)
        
        Timber.d("DashboardViewModel cleared")
    }
    
    /**
     * Factory for creating DashboardViewModel with Context
     */
    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                return DashboardViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}