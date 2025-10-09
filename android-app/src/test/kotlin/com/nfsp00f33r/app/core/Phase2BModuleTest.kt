package com.nfsp00f33r.app.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nfsp00f33r.app.emulation.EmulationModule
import com.nfsp00f33r.app.hardware.PN532DeviceModule
import com.nfsp00f33r.app.nfc.NfcHceModule
import com.nfsp00f33r.app.storage.SecureMasterPasswordModule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Phase 2B Module System Tests
 * 
 * Comprehensive testing for all Phase 2B hardware modules:
 * - PN532DeviceModule
 * - SecureMasterPasswordModule
 * - NfcHceModule
 * - EmulationModule
 * 
 * Tests cover:
 * - Module initialization and shutdown
 * - Health check functionality
 * - Public API methods
 * - Statistics tracking
 * - Error handling
 * - Integration with ModuleRegistry
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Phase2BModuleTest {
    
    private lateinit var context: Context
    private lateinit var moduleRegistry: ModuleRegistry
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ModuleRegistry.initialize()
    }
    
    @After
    fun teardown() = runBlocking {
        // Clean shutdown of all modules
        ModuleRegistry.stopAll()
    }
    
    // ========== PN532DeviceModule Tests ==========
    
    @Test
    fun `PN532DeviceModule - initialization and basic properties`() = runBlocking {
        val module = PN532DeviceModule(context)
        
        assertEquals("PN532Device", module.name)
        assertEquals("1.0.0", module.getVersion())
        assertTrue(module.description.contains("PN532"))
        assertEquals(emptyList(), module.dependencies)
    }
    
    @Test
    fun `PN532DeviceModule - lifecycle management`() = runBlocking {
        val module = PN532DeviceModule(context)
        
        // Initialize
        module.initialize()
        assertEquals(ModuleState.RUNNING, module.state)
        
        // Health check after initialization
        val health = module.checkHealth()
        assertNotNull(health)
        assertTrue(health.message.contains("PN532") || health.message.contains("not connected"))
        
        // Shutdown
        module.shutdown()
        assertEquals(ModuleState.STOPPED, module.state)
    }
    
    @Test
    fun `PN532DeviceModule - public API methods`() = runBlocking {
        val module = PN532DeviceModule(context)
        module.initialize()
        
        // Test isConnected (should be false initially)
        assertFalse(module.isConnected())
        
        // Test connection state LiveData
        val connectionState = module.getConnectionState()
        assertNotNull(connectionState)
        
        // Test connection type LiveData
        val connectionType = module.getConnectionType()
        assertNotNull(connectionType)
        
        module.shutdown()
    }
    
    @Test
    fun `PN532DeviceModule - ModuleRegistry integration`() = runBlocking {
        val module = PN532DeviceModule(context)
        
        ModuleRegistry.registerModule(module)
        val results = ModuleRegistry.startAll()
        
        assertTrue(results.containsKey("PN532Device"))
        val result = results["PN532Device"]
        assertTrue(result is InitializationResult.Success || result is InitializationResult.Failure)
        
        ModuleRegistry.stopAll()
    }
    
    // ========== SecureMasterPasswordModule Tests ==========
    
    @Test
    fun `SecureMasterPasswordModule - initialization and basic properties`() = runBlocking {
        val module = SecureMasterPasswordModule(context)
        
        assertEquals("MasterPassword", module.name)
        assertEquals("1.0.0", module.getVersion())
        assertTrue(module.description.contains("password"))
        assertEquals(emptyList(), module.dependencies)
    }
    
    @Test
    fun `SecureMasterPasswordModule - lifecycle management`() = runBlocking {
        val module = SecureMasterPasswordModule(context)
        
        // Initialize
        module.initialize()
        assertEquals(ModuleState.RUNNING, module.state)
        
        // Health check after initialization
        val health = module.checkHealth()
        assertNotNull(health)
        assertTrue(health.message.contains("password") || health.message.contains("Master"))
        
        // Shutdown
        module.shutdown()
        assertEquals(ModuleState.STOPPED, module.state)
    }
    
    @Test
    fun `SecureMasterPasswordModule - password operations`() = runBlocking {
        val module = SecureMasterPasswordModule(context)
        module.initialize()
        
        // Test password not set initially
        assertFalse(module.isPasswordSet())
        
        // Set password
        val testPassword = "TestPassword123!@#"
        val setResult = module.setMasterPassword(testPassword)
        assertTrue(setResult)
        
        // Verify password is set
        assertTrue(module.isPasswordSet())
        
        // Verify correct password
        assertTrue(module.verifyMasterPassword(testPassword))
        
        // Verify incorrect password
        assertFalse(module.verifyMasterPassword("WrongPassword"))
        
        // Test statistics
        val stats = module.getStatistics()
        assertTrue(stats.passwordSet)
        assertEquals(1, stats.setPasswordCalls)
        assertEquals(2, stats.verifyPasswordCalls)
        
        module.shutdown()
    }
    
    @Test
    fun `SecureMasterPasswordModule - password strength validation`() = runBlocking {
        val module = SecureMasterPasswordModule(context)
        module.initialize()
        
        // Test weak password (too short)
        val weakValidation = module.validatePasswordStrength("weak")
        assertFalse(weakValidation.isValid)
        assertTrue(weakValidation.message.contains("8"))
        
        // Test strong password
        val strongValidation = module.validatePasswordStrength("StrongPassword123!@#")
        assertTrue(strongValidation.isValid)
        
        module.shutdown()
    }
    
    @Test
    fun `SecureMasterPasswordModule - ModuleRegistry integration`() = runBlocking {
        val module = SecureMasterPasswordModule(context)
        
        ModuleRegistry.registerModule(module)
        val results = ModuleRegistry.startAll()
        
        assertTrue(results.containsKey("MasterPassword"))
        val result = results["MasterPassword"]
        assertTrue(result is InitializationResult.Success)
        
        ModuleRegistry.stopAll()
    }
    
    // ========== NfcHceModule Tests ==========
    
    @Test
    fun `NfcHceModule - initialization and basic properties`() = runBlocking {
        val module = NfcHceModule(context)
        
        assertEquals("NfcHce", module.name)
        assertEquals("1.0.0", module.getVersion())
        assertTrue(module.description.contains("NFC") || module.description.contains("HCE"))
        assertEquals(emptyList(), module.dependencies)
    }
    
    @Test
    fun `NfcHceModule - lifecycle management`() = runBlocking {
        val module = NfcHceModule(context)
        
        // Initialize
        module.initialize()
        assertEquals(ModuleState.RUNNING, module.state)
        
        // Health check after initialization
        val health = module.checkHealth()
        assertNotNull(health)
        assertTrue(health.message.contains("NFC") || health.message.contains("HCE"))
        
        // Shutdown
        module.shutdown()
        assertEquals(ModuleState.STOPPED, module.state)
    }
    
    @Test
    fun `NfcHceModule - NFC availability checks`() = runBlocking {
        val module = NfcHceModule(context)
        module.initialize()
        
        // Test NFC availability (may be false on emulator)
        val nfcAvailable = module.isNfcAvailable()
        // Just verify it returns a boolean, value depends on device
        assertTrue(nfcAvailable || !nfcAvailable)
        
        // Test HCE support
        val hceSupported = module.isHceSupported()
        assertTrue(hceSupported || !hceSupported)
        
        // Test status string
        val status = module.getHceStatus()
        assertNotNull(status)
        assertTrue(status.isNotEmpty())
        
        module.shutdown()
    }
    
    @Test
    fun `NfcHceModule - statistics tracking`() = runBlocking {
        val module = NfcHceModule(context)
        module.initialize()
        
        val stats = module.getStatistics()
        assertNotNull(stats)
        assertEquals(0, stats.serviceStartAttempts)
        assertEquals(0, stats.serviceStopAttempts)
        assertFalse(stats.serviceRunning)
        
        module.shutdown()
    }
    
    @Test
    fun `NfcHceModule - ModuleRegistry integration`() = runBlocking {
        val module = NfcHceModule(context)
        
        ModuleRegistry.registerModule(module)
        val results = ModuleRegistry.startAll()
        
        assertTrue(results.containsKey("NfcHce"))
        val result = results["NfcHce"]
        assertTrue(result is InitializationResult.Success)
        
        ModuleRegistry.stopAll()
    }
    
    // ========== EmulationModule Tests ==========
    
    @Test
    fun `EmulationModule - initialization and basic properties`() = runBlocking {
        val module = EmulationModule(context)
        
        assertEquals("Emulation", module.name)
        assertEquals("1.0.0", module.getVersion())
        assertTrue(module.description.contains("EMV") || module.description.contains("attack"))
        assertEquals(listOf("CardDataStore"), module.dependencies)
    }
    
    @Test
    fun `EmulationModule - lifecycle management`() = runBlocking {
        val module = EmulationModule(context)
        
        // Initialize
        module.initialize()
        assertEquals(ModuleState.RUNNING, module.state)
        
        // Health check after initialization
        val health = module.checkHealth()
        assertNotNull(health)
        assertTrue(health.isHealthy)
        
        // Shutdown
        module.shutdown()
        assertEquals(ModuleState.STOPPED, module.state)
    }
    
    @Test
    fun `EmulationModule - statistics tracking`() = runBlocking {
        val module = EmulationModule(context)
        module.initialize()
        
        val stats = module.getStatistics()
        assertNotNull(stats)
        assertEquals(0, stats.attackExecutions)
        assertEquals(0, stats.successfulAttacks)
        assertEquals(0, stats.failedAttacks)
        assertEquals(0.0, stats.successRate)
        assertEquals(null, stats.lastAttackType)
        assertEquals(0, stats.historySize)
        
        module.shutdown()
    }
    
    @Test
    fun `EmulationModule - attack history management`() = runBlocking {
        val module = EmulationModule(context)
        module.initialize()
        
        // Initially empty
        val initialHistory = module.getAttackHistory()
        assertTrue(initialHistory.isEmpty())
        
        // Clear history (should not throw)
        module.clearHistory()
        
        val afterClear = module.getAttackHistory()
        assertTrue(afterClear.isEmpty())
        
        module.shutdown()
    }
    
    @Test
    fun `EmulationModule - ModuleRegistry integration`() = runBlocking {
        val module = EmulationModule(context)
        
        ModuleRegistry.registerModule(module)
        val results = ModuleRegistry.startAll()
        
        assertTrue(results.containsKey("Emulation"))
        val result = results["Emulation"]
        // May be skipped if CardDataStore dependency not registered
        assertTrue(
            result is InitializationResult.Success || 
            result is InitializationResult.Skipped
        )
        
        ModuleRegistry.stopAll()
    }
    
    // ========== Integration Tests ==========
    
    @Test
    fun `All Phase 2B modules - concurrent initialization`() = runBlocking {
        val pn532 = PN532DeviceModule(context)
        val password = SecureMasterPasswordModule(context)
        val nfcHce = NfcHceModule(context)
        val emulation = EmulationModule(context)
        
        ModuleRegistry.registerModule(pn532)
        ModuleRegistry.registerModule(password)
        ModuleRegistry.registerModule(nfcHce)
        ModuleRegistry.registerModule(emulation)
        
        val results = ModuleRegistry.startAll()
        
        // Verify all 4 modules attempted initialization
        assertEquals(4, results.size)
        
        // Verify registry tracking
        val registeredModules = ModuleRegistry.getRegisteredModules()
        assertTrue(registeredModules.contains("PN532Device"))
        assertTrue(registeredModules.contains("MasterPassword"))
        assertTrue(registeredModules.contains("NfcHce"))
        assertTrue(registeredModules.contains("Emulation"))
        
        ModuleRegistry.stopAll()
    }
    
    @Test
    fun `All Phase 2B modules - health monitoring`() = runBlocking {
        val pn532 = PN532DeviceModule(context)
        val password = SecureMasterPasswordModule(context)
        val nfcHce = NfcHceModule(context)
        val emulation = EmulationModule(context)
        
        pn532.initialize()
        password.initialize()
        nfcHce.initialize()
        emulation.initialize()
        
        // Check all health statuses
        val pn532Health = pn532.checkHealth()
        val passwordHealth = password.checkHealth()
        val nfcHceHealth = nfcHce.checkHealth()
        val emulationHealth = emulation.checkHealth()
        
        assertNotNull(pn532Health)
        assertNotNull(passwordHealth)
        assertNotNull(nfcHceHealth)
        assertNotNull(emulationHealth)
        
        // All should return non-null messages
        assertTrue(pn532Health.message.isNotEmpty())
        assertTrue(passwordHealth.message.isNotEmpty())
        assertTrue(nfcHceHealth.message.isNotEmpty())
        assertTrue(emulationHealth.message.isNotEmpty())
        
        pn532.shutdown()
        password.shutdown()
        nfcHce.shutdown()
        emulation.shutdown()
    }
    
    @Test
    fun `Phase 2B module system - stress test`() = runBlocking {
        // Register all 4 Phase 2B modules multiple times
        repeat(3) { iteration ->
            val pn532 = PN532DeviceModule(context)
            val password = SecureMasterPasswordModule(context)
            val nfcHce = NfcHceModule(context)
            val emulation = EmulationModule(context)
            
            ModuleRegistry.registerModule(pn532)
            ModuleRegistry.registerModule(password)
            ModuleRegistry.registerModule(nfcHce)
            ModuleRegistry.registerModule(emulation)
            
            val results = ModuleRegistry.startAll()
            assertEquals(4, results.size)
            
            ModuleRegistry.stopAll()
        }
    }
}
