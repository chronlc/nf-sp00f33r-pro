package com.nfsp00f33r.app.application

import android.app.Application
import android.content.Context
import android.util.Log
import com.nfsp00f33r.app.core.ModuleRegistry
import com.nfsp00f33r.app.storage.CardDataStore
import com.nfsp00f33r.app.storage.CardDataStoreModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

/**
 * Initialization state with progress tracking
 */
data class InitializationState(
    val isComplete: Boolean = false,
    val currentStep: String = "",
    val progress: Float = 0f,
    val error: String? = null
)

/**
 * Main Application class for nf-sp00f EMV Security Platform
 * Production-ready implementation with proper package structure
 * 
 * Phase 1A Integration:
 * - BouncyCastle security provider initialization
 * - CardDataStore singleton with AES-256-GCM encryption
 * 
 * Phase 2A Integration (Days 4-6):
 * - ModuleRegistry for lifecycle management
 * - CardDataStoreModule integration
 * - Asynchronous module initialization
 */
class NfSp00fApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Initialization state tracking
    private var initializationCallback: ((InitializationState) -> Unit)? = null
    private var isInitialized = false
    private var isInitializing = false

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Initialize core services (but DON'T start module system yet)
        initializeLogging()
        initializeErrorHandling()
        
        // Report initial state
        reportProgress("Starting initialization...", 0.0f)
    }
    
    /**
     * Start the actual initialization process
     * Called by SplashActivity after callback is set
     */
    fun startInitialization() {
        if (isInitialized || isInitializing) {
            reportProgress("Already initialized", 1.0f)
            return
        }
        isInitializing = true
        
        applicationScope.launch {
            initializeSecurityProvider()
            initializeModuleSystem()
        }
    }    /**
     * Initialize BouncyCastle security provider for AES-256-GCM encryption
     */
    private suspend fun initializeSecurityProvider() {
        try {
            reportProgress("Initializing security provider...", 0.05f)
            delay(150L)
            // Insert BouncyCastle as the first security provider
            Security.insertProviderAt(BouncyCastleProvider(), 1)
            Log.i(TAG, "BouncyCastle security provider initialized successfully")
            reportProgress("Security provider ready", 0.10f)
            delay(150L)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BouncyCastle provider", e)
            reportProgress("Security init failed", 0.10f, e.message)
        }
    }

    /**
     * Initialize Module System
     * Phase 2A Days 4-6: ModuleRegistry with CardDataStoreModule
     */
    private suspend fun initializeModuleSystem() {
        try {
            Log.i(TAG, "Initializing module system...")
            reportProgress("Starting module system...", 0.15f)
                delay(150L)
                
                // Initialize password manager first
                reportProgress("Setting up password manager...", 0.20f)
                delay(150L)
                passwordManager = com.nfsp00f33r.app.storage.SecureMasterPasswordManager(applicationContext)
                
                // Get master password
                val masterPassword = if (passwordManager.isPasswordSet()) {
                    passwordManager.getMasterPassword()
                } else {
                    Log.w(TAG, "No master password set - using temporary default")
                    "TEMPORARY_FIRST_LAUNCH_PASSWORD_PLEASE_CHANGE"
                }
                
                if (masterPassword == null) {
                    Log.e(TAG, "Failed to retrieve master password - module initialization aborted")
                    reportProgress("Password init failed", 0.20f, "Master password unavailable")
                    delay(150L)
                    return
                }
                
                // Initialize ModuleRegistry
                reportProgress("Initializing module registry...", 0.25f)
                delay(150L)
                ModuleRegistry.initialize()
                
                // Create and register LoggingModule (Phase 2A Day 7)
                reportProgress("Registering Logging module...", 0.30f)
                delay(150L)
                val loggingModule = com.nfsp00f33r.app.core.LoggingModule(
                    minLogLevel = com.nfsp00f33r.app.core.LoggingModule.LogLevel.DEBUG,
                    enableFileLogging = false
                )
                ModuleRegistry.registerModule(loggingModule)
                
                // Create and register SecureMasterPasswordModule (Phase 2B Day 2)
                reportProgress("Registering Password module...", 0.38f)
                delay(150L)
                val passwordModule = com.nfsp00f33r.app.storage.SecureMasterPasswordModule(
                    context = applicationContext
                )
                ModuleRegistry.registerModule(passwordModule)
                
                // Create and register CardDataStoreModule
                reportProgress("Registering CardData module...", 0.46f)
                delay(150L)
                val cardDataStoreModule = CardDataStoreModule(
                    context = applicationContext,
                    encryptionEnabled = true,
                    masterPassword = masterPassword
                )
                ModuleRegistry.registerModule(cardDataStoreModule)
                
                // Create and register PN532DeviceModule (Phase 2B Day 1)
                reportProgress("Registering PN532 module...", 0.54f)
                delay(150L)
                val pn532DeviceModule = com.nfsp00f33r.app.hardware.PN532DeviceModule(
                    context = applicationContext
                )
                ModuleRegistry.registerModule(pn532DeviceModule)
                
                // Create and register NfcHceModule (Phase 2B Days 3-4)
                reportProgress("Registering NFC/HCE module...", 0.62f)
                delay(150L)
                val nfcHceModule = com.nfsp00f33r.app.nfc.NfcHceModule(
                    context = applicationContext
                )
                ModuleRegistry.registerModule(nfcHceModule)
                
                // Create and register EmulationModule (Phase 2B Days 5-6)
                reportProgress("Registering Emulation module...", 0.70f)
                delay(150L)
                val emulationModule = com.nfsp00f33r.app.emulation.EmulationModule(
                    context = applicationContext
                )
                ModuleRegistry.registerModule(emulationModule)
                
                // Start all modules
                reportProgress("Starting all modules...", 0.78f)
                delay(150L)
                val results = ModuleRegistry.startAll()
                
                // Log initialization results
                results.forEach { (moduleName, result) ->
                    when (result) {
                        is com.nfsp00f33r.app.core.InitializationResult.Success -> 
                            Log.i(TAG, "Module $moduleName initialized successfully")
                        is com.nfsp00f33r.app.core.InitializationResult.Failure -> 
                            Log.e(TAG, "Module $moduleName failed: ${result.error.message}")
                        is com.nfsp00f33r.app.core.InitializationResult.Skipped -> 
                            Log.w(TAG, "Module $moduleName skipped: ${result.reason}")
                    }
                }
                
                // Store reference to CardDataStoreModule
                cardDataStoreModuleInstance = cardDataStoreModule
                
                // Store reference to SecureMasterPasswordModule (Phase 2B Day 2)
                passwordModuleInstance = passwordModule
                
                // Store reference to PN532DeviceModule (Phase 2B Day 1)
                pn532DeviceModuleInstance = pn532DeviceModule
                
                // Store reference to NfcHceModule (Phase 2B Days 3-4)
                nfcHceModuleInstance = nfcHceModule
                
                // Store reference to EmulationModule (Phase 2B Days 5-6)
                emulationModuleInstance = emulationModule
                
                Log.i(TAG, "Module system initialized successfully")
                reportProgress("All modules ready", 0.95f)
                delay(150L)
                
                // Mark initialization as complete
                isInitialized = true
                reportProgress("Initialization complete!", 1.0f)
                delay(150L)
                
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize module system", e)
            reportProgress("Module init failed", 0.80f, e.message)
        }
    }
    
    /**
     * Report initialization progress to callback
     */
    private fun reportProgress(message: String, progress: Float, error: String? = null) {
        val state = InitializationState(
            isComplete = progress >= 1.0f,
            currentStep = message,
            progress = progress,
            error = error
        )
        initializationCallback?.invoke(state)
    }
    
    /**
     * Shutdown all modules gracefully
     */
    override fun onTerminate() {
        super.onTerminate()
        applicationScope.launch {
            try {
                Log.i(TAG, "Shutting down module system...")
                ModuleRegistry.stopAll()
                Log.i(TAG, "Module system shutdown complete")
            } catch (e: Exception) {
                Log.e(TAG, "Error during module shutdown", e)
            }
        }
    }

    private fun initializeLogging() {
        // Initialize Timber for logging throughout the app
        // This enables all Timber.d(), Timber.i(), Timber.w(), Timber.e() calls
        timber.log.Timber.plant(timber.log.Timber.DebugTree())
        Log.i(TAG, "Timber logging initialized")
    }

    private fun initializeErrorHandling() {
        // Set up global error handling and crash reporting
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            android.util.Log.e(
                "NfSp00fApplication",
                "Uncaught exception in thread ${thread.name}",
                exception
            )
        }
    }

    companion object {
        private const val TAG = "NfSp00fApplication"
        
        /** Singleton SecureMasterPasswordManager instance (legacy) */
        private lateinit var passwordManager: com.nfsp00f33r.app.storage.SecureMasterPasswordManager
        
        /** Singleton SecureMasterPasswordModule instance (Phase 2B Day 2) */
        private var passwordModuleInstance: com.nfsp00f33r.app.storage.SecureMasterPasswordModule? = null
        
        /** Singleton CardDataStoreModule instance (Phase 2A) */
        private var cardDataStoreModuleInstance: CardDataStoreModule? = null
        
        /** Singleton PN532DeviceModule instance (Phase 2B Day 1) */
        private var pn532DeviceModuleInstance: com.nfsp00f33r.app.hardware.PN532DeviceModule? = null
        
        /** Singleton NfcHceModule instance (Phase 2B Days 3-4) */
        private var nfcHceModuleInstance: com.nfsp00f33r.app.nfc.NfcHceModule? = null
        
        /** Singleton EmulationModule instance (Phase 2B Days 5-6) */
        private var emulationModuleInstance: com.nfsp00f33r.app.emulation.EmulationModule? = null
        
        private var instance: NfSp00fApplication? = null
        
        /** Get application context from anywhere */
        fun getContext(): Context? {
            return instance?.applicationContext
        }

        /**
         * Get CardDataStore instance (legacy compatibility)
         * Phase 2A: This now delegates to CardDataStoreModule
         * @throws IllegalStateException if CardDataStore not initialized
         */
        @Deprecated(
            "Use getCardDataStoreModule() instead for full module functionality",
            ReplaceWith("getCardDataStoreModule()")
        )
        fun getCardDataStore(): CardDataStore {
            throw UnsupportedOperationException(
                "CardDataStore direct access deprecated in Phase 2A. " +
                "Use getCardDataStoreModule() for module-based access or " +
                "ModuleRegistry.getModule<CardDataStoreModule>() for type-safe access."
            )
        }
        
        /**
         * Get CardDataStoreModule instance (Phase 2A)
         * @throws IllegalStateException if module not initialized
         */
        fun getCardDataStoreModule(): CardDataStoreModule {
            return cardDataStoreModuleInstance ?: throw IllegalStateException(
                "CardDataStoreModule not initialized. Application may not have started properly."
            )
        }
        
        /**
         * Get PN532DeviceModule instance (Phase 2B Day 1)
         * @throws IllegalStateException if module not initialized
         */
        fun getPN532Module(): com.nfsp00f33r.app.hardware.PN532DeviceModule {
            return pn532DeviceModuleInstance ?: throw IllegalStateException(
                "PN532DeviceModule not initialized. Application may not have started properly."
            )
        }
        
        /**
         * Get SecureMasterPasswordModule instance (Phase 2B Day 2)
         * @throws IllegalStateException if module not initialized
         */
        fun getPasswordModule(): com.nfsp00f33r.app.storage.SecureMasterPasswordModule {
            return passwordModuleInstance ?: throw IllegalStateException(
                "SecureMasterPasswordModule not initialized. Application may not have started properly."
            )
        }
        
        /**
         * Get NfcHceModule instance (Phase 2B Days 3-4)
         * @throws IllegalStateException if module not initialized
         */
        fun getNfcHceModule(): com.nfsp00f33r.app.nfc.NfcHceModule {
            return nfcHceModuleInstance ?: throw IllegalStateException(
                "NfcHceModule not initialized. Application may not have started properly."
            )
        }
        
        /**
         * Get EmulationModule instance (Phase 2B Days 5-6)
         * @throws IllegalStateException if module not initialized
         */
        fun getEmulationModule(): com.nfsp00f33r.app.emulation.EmulationModule {
            return emulationModuleInstance ?: throw IllegalStateException(
                "EmulationModule not initialized. Application may not have started properly."
            )
        }

        /**
         * Get SecureMasterPasswordManager instance
         * @throws IllegalStateException if not initialized
         */
        fun getPasswordManager(): com.nfsp00f33r.app.storage.SecureMasterPasswordManager {
            return passwordManager
        }
        
        /**
         * Check if initialization is complete
         */
        fun isInitializationComplete(): Boolean {
            return instance?.isInitialized ?: false
        }
        
        /**
         * Set initialization progress callback and start initialization
         */
        fun setInitializationCallback(callback: (InitializationState) -> Unit) {
            instance?.initializationCallback = callback
            // Start initialization now that callback is set
            instance?.startInitialization()
        }
        
        /**
         * Clear initialization callback
         */
        fun clearInitializationCallback() {
            instance?.initializationCallback = null
        }
    }
}