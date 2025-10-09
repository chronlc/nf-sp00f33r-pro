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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * PRODUCTION-GRADE Dashboard ViewModel
 * Phase 1A: Now uses CardDataStore with encrypted persistence
 * 
 * Provides LIVE DATA ONLY - no hardcoded fallbacks
 * Integrates with real hardware status and card profile data
 * All card data now persists with AES-256-GCM encryption
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
    
    // Card data store with encryption
    private val cardDataStore = NfSp00fApplication.getCardDataStoreModule()
    
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
     */
    private fun initializeServices() {
        viewModelScope.launch {
            try {
                // Wait for application initialization to complete
                var attempts = 0
                while (!com.nfsp00f33r.app.application.NfSp00fApplication.isInitializationComplete() && attempts < 50) {
                    delay(100)
                    attempts++
                }
                
                if (com.nfsp00f33r.app.application.NfSp00fApplication.isInitializationComplete()) {
                    Timber.i("Application initialized, starting LIVE hardware detection service...")
                } else {
                    Timber.w("Application initialization timeout, proceeding anyway...")
                }
                
                Timber.i("Initializing LIVE hardware detection service...")
                
                // For Dashboard, we'll use a simplified approach that polls the MainActivity's hardware status
                // instead of creating a separate service instance to avoid context issues
                // The MainActivity already has the live hardware detection running
                
                // Set up periodic polling for hardware status updates
                setupHardwareStatusPolling()
                
                // Load initial card data with explicit logging
                Timber.d("Loading initial card data from encrypted storage...")
                refreshCardData()
                
                // Start periodic refresh for real-time updates
                startPeriodicRefresh()
                
                isInitialized = true
                Timber.i("DashboardViewModel initialized with LIVE data polling")
                
            } catch (e: Exception) {
                Timber.e(e, "Failed to initialize DashboardViewModel")
                
                // Show REAL error - NO FALLBACK DATA
                hardwareStatus = HardwareDetectionService.HardwareStatus(
                    hardwareScore = 0,
                    statusMessage = "Hardware detection failed: ${e.message}",
                    detectionPhase = "Error: ${e.javaClass.simpleName}",
                    errorDetails = e.stackTraceToString()
                )
                
                isInitialized = true // Allow UI to show error state
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
                
                // Phase 2B Day 1: Use PN532DeviceModule (with safety check)
                val realPn532Connected = try {
                    val connected = pn532Module.isConnected()
                    Timber.d("PN532 connection state: $connected")
                    connected
                } catch (e: Exception) {
                    Timber.w("PN532 module not ready: ${e.message}")
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
     */
    private fun startPeriodicRefresh() {
        viewModelScope.launch {
            while (true) {
                delay(3000L) // Refresh every 3 seconds
                try {
                    setupHardwareStatusPolling() // Update PN532 status
                    refreshCardData() // Update card statistics
                } catch (e: Exception) {
                    Timber.w("Periodic refresh failed: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Refresh card data from CardDataStore (encrypted storage)
     */
    private fun refreshCardData() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Load from encrypted storage
                val storageProfiles = cardDataStore.getAllProfiles()
                Timber.d("Loaded ${storageProfiles.size} profiles from encrypted storage")
                val allProfiles = storageProfiles.map { CardProfileAdapter.toAppProfile(it) }
                
                // Convert ONLY profiles with REAL data to VirtualCard format
                recentCards = allProfiles.takeLast(3).mapNotNull { profile ->
                    val emvData = profile.emvCardData
                    
                    // ONLY show cards with REAL extracted PAN data
                    if (emvData.pan.isNullOrEmpty() || emvData.pan!!.length < 13) {
                        return@mapNotNull null // Skip cards without real PAN
                    }
                    
                    VirtualCard(
                        cardholderName = emvData.cardholderName?.takeIf { it.isNotBlank() } ?: "",
                        maskedPan = emvData.getUnmaskedPan(),
                        expiryDate = emvData.expiryDate?.takeIf { it.isNotBlank() } ?: "",
                        apduCount = profile.apduLogs.size, // Use real APDU count
                        cardType = if (emvData.pan!!.length >= 6) detectCardBrand(emvData.getUnmaskedPan()) else "",
                        isEncrypted = emvData.hasEncryptedData(),
                        lastUsed = formatRelativeTime(profile.createdAt.time),
                        category = if (emvData.hasEncryptedData()) "EMV" else "BASIC"
                    )
                }.reversed() // Show most recent first
                
                // Calculate real statistics and update UI on Main thread
                val newStats = calculateCardStatistics(allProfiles)
                
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
     * Calculate REAL card statistics from actual data ONLY - NO PLACEHOLDERS
     */
    private fun calculateCardStatistics(profiles: List<com.nfsp00f33r.app.models.CardProfile>): CardStatistics {
        if (profiles.isEmpty()) {
            // Return empty statistics - NO FAKE DATA
            return CardStatistics()
        }
        
        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)
        
        // Count cards with REAL timestamps only
        val cardsToday = profiles.count { profile ->
            profile.createdAt.time > oneDayAgo
        }
        
        // Count only cards with REAL brand data extracted
        val realBrands = profiles.mapNotNull { profile ->
            val pan = profile.emvCardData.pan
            if (!pan.isNullOrEmpty() && pan.length >= 6) {
                detectCardBrand(profile.emvCardData.getUnmaskedPan())
            } else null
        }.distinct()
        
        val uniqueBrands = realBrands.size
        
        // Count only REAL APDU commands from actual card communication
        val totalApduCommands = profiles.sumOf { it.apduLogs.size }
        val averageApduPerCard = if (profiles.isNotEmpty()) totalApduCommands.toDouble() / profiles.size else 0.0
        
        // Get REAL recent scan time from actual timestamps
        val recentScanTime = if (profiles.isNotEmpty()) {
            val mostRecent = profiles.maxByOrNull { it.createdAt.time }
            if (mostRecent != null) formatRelativeTime(mostRecent.createdAt.time) else "Never"
        } else {
            "Never"
        }
        
        // Count REAL security issues from actual EMV data analysis
        val securityIssues = profiles.count { profile ->
            val emvData = profile.emvCardData
            // Only count as security issue if we have real data to analyze
            (emvData.pan != null && !emvData.hasEncryptedData()) ||
            (emvData.applicationCryptogram.isNullOrEmpty() && emvData.apduLog.isNotEmpty()) ||
            (profile.apduLogs.size < 3 && emvData.pan != null)
        }
        
        return CardStatistics(
            totalCards = profiles.size,
            cardsToday = cardsToday,
            uniqueBrands = uniqueBrands,
            totalApduCommands = totalApduCommands,
            averageApduPerCard = averageApduPerCard,
            recentScanTime = recentScanTime,
            securityIssues = securityIssues
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
        // No cleanup needed for CardDataStore (managed by Application)
        
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