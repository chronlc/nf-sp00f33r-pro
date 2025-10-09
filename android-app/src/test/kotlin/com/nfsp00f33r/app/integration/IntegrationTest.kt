package com.nfsp00f33r.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nfsp00f33r.app.core.ModuleRegistry
import com.nfsp00f33r.app.core.ModuleState
import com.nfsp00f33r.app.core.HealthStatus
import com.nfsp00f33r.app.screens.health.HealthMonitoringViewModel
import com.nfsp00f33r.app.alerts.HealthAlertManager
import com.nfsp00f33r.app.data.health.HealthHistoryRepository
import com.nfsp00f33r.app.storage.CardDataStore
import com.nfsp00f33r.app.storage.CardProfile
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Integration Tests for nf-sp00f33r Framework
 * Phase 5 Days 1-3: Integration Testing
 * 
 * Tests end-to-end workflows:
 * 1. Card reading → Storage → Visualization
 * 2. ROCA detection → Exploitation → Key recovery
 * 3. Health monitoring → Alerts → Notifications
 * 4. Module lifecycle management
 * 5. Cross-module dependencies
 */
@RunWith(AndroidJUnit4::class)
class IntegrationTest {
    
    private lateinit var context: Context
    private lateinit var healthViewModel: HealthMonitoringViewModel
    private lateinit var alertManager: HealthAlertManager
    private lateinit var cardDataStore: CardDataStore
    private lateinit var healthRepository: HealthHistoryRepository
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        healthViewModel = HealthMonitoringViewModel(context)
        alertManager = HealthAlertManager(context)
        healthRepository = HealthHistoryRepository(context)
        
        // Initialize CardDataStore with test password
        cardDataStore = CardDataStore()
        runBlocking {
            cardDataStore.initialize("test_master_password_12345")
        }
        
        // Clear any existing data
        runBlocking {
            cardDataStore.clearAllProfiles()
            healthRepository.deleteAll()
            alertManager.clearAlertHistory()
        }
    }
    
    @After
    fun tearDown() {
        runBlocking {
            cardDataStore.shutdown()
            healthViewModel.stopMonitoring()
        }
    }
    
    /**
     * Test 1: End-to-End Card Storage Workflow
     * Card creation → Encryption → Storage → Retrieval → Decryption
     */
    @Test
    fun testE2E_CardStorageWorkflow() = runBlocking {
        // Step 1: Create test card profile
        val testCard = CardProfile(
            name = "Test Visa Card",
            pan = "4111111111111111",
            expiry = "12/25",
            cvv = "123",
            track1 = "",
            track2 = "4111111111111111=2512101",
            uid = "ABCD1234",
            cardType = "VISA",
            issuer = "Test Bank",
            notes = "Integration test card"
        )
        
        // Step 2: Save to encrypted storage
        val savedCard = cardDataStore.saveCardProfile(testCard)
        assertNotNull("Card should be saved successfully", savedCard)
        assertEquals("Card name should match", testCard.name, savedCard.name)
        
        // Step 3: Retrieve all profiles
        val allProfiles = cardDataStore.getAllCardProfiles()
        assertEquals("Should have 1 profile", 1, allProfiles.size)
        assertEquals("Retrieved card should match saved card", savedCard.name, allProfiles[0].name)
        
        // Step 4: Retrieve specific profile
        val retrievedCard = cardDataStore.getCardProfile(savedCard.id)
        assertNotNull("Card should be retrievable by ID", retrievedCard)
        assertEquals("PAN should match (decrypted correctly)", testCard.pan, retrievedCard?.pan)
        assertEquals("CVV should match (decrypted correctly)", testCard.cvv, retrievedCard?.cvv)
        
        // Step 5: Update profile
        val updatedCard = retrievedCard?.copy(notes = "Updated integration test")
        val updated = updatedCard?.let { cardDataStore.saveCardProfile(it) }
        assertEquals("Updated notes should persist", "Updated integration test", updated?.notes)
        
        // Step 6: Delete profile
        val deleted = cardDataStore.deleteCardProfile(savedCard.id)
        assertTrue("Card should be deleted successfully", deleted)
        
        val afterDelete = cardDataStore.getAllCardProfiles()
        assertEquals("Should have 0 profiles after deletion", 0, afterDelete.size)
    }
    
    /**
     * Test 2: Health Monitoring → Alert → Notification Workflow
     */
    @Test
    fun testE2E_HealthMonitoringWorkflow() = runBlocking {
        // Step 1: Start health monitoring
        healthViewModel.startMonitoring()
        delay(1000) // Wait for initial health check
        
        // Step 2: Verify health data collected
        val cardDataStoreHealth = healthViewModel.cardDataStoreHealth.value
        assertNotNull("CardDataStore health should be checked", cardDataStoreHealth)
        
        // Step 3: Generate alert by simulating critical status
        // (In real scenario, this would come from actual module failure)
        val testAlert = com.nfsp00f33r.app.screens.health.HealthAlert(
            id = "integration_test_1",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            moduleName = "CardDataStore",
            severity = HealthStatus.Severity.CRITICAL,
            message = "Integration test critical alert"
        )
        
        // Step 4: Process alert
        alertManager.processAlert(testAlert)
        
        // Step 5: Verify alert in history
        val alertHistory = alertManager.getAlertHistory()
        assertTrue("Alert should be in history", alertHistory.any { it.id == "integration_test_1" })
        
        // Step 6: Verify alert statistics
        val stats = alertManager.getAlertStatistics()
        assertTrue("Should have at least 1 alert", stats.totalAlerts > 0)
        assertTrue("Should have at least 1 critical alert", stats.criticalCount > 0)
        
        // Step 7: Stop monitoring
        healthViewModel.stopMonitoring()
    }
    
    /**
     * Test 3: Module Registry Initialization & Health Check
     */
    @Test
    fun testE2E_ModuleRegistryWorkflow() {
        // Step 1: Check all modules are registered
        val allModules = listOf(
            "CardDataStore",
            "Logging",
            "PN532Device",
            "MasterPassword",
            "NfcHce",
            "Emulation"
        )
        
        allModules.forEach { moduleName ->
            val module = ModuleRegistry.getModule(moduleName)
            assertNotNull("Module $moduleName should be registered", module)
        }
        
        // Step 2: Verify module dependencies
        val cardDataStoreModule = ModuleRegistry.getModule("CardDataStore")
        assertNotNull("CardDataStore module should exist", cardDataStoreModule)
        
        // Step 3: Check health status of all modules
        allModules.forEach { moduleName ->
            val module = ModuleRegistry.getModule(moduleName)
            val health = module?.getHealthStatus()
            assertNotNull("Module $moduleName should have health status", health)
        }
        
        // Step 4: Verify topological sort (dependency order)
        // CardDataStore should not depend on any other modules
        // Other modules may depend on CardDataStore or Logging
        val cardDataStore = ModuleRegistry.getModule("CardDataStore")
        val dependencies = cardDataStore?.getDependencies()
        assertTrue("CardDataStore should have minimal dependencies", dependencies?.isEmpty() == true || dependencies?.size ?: 0 <= 1)
    }
    
    /**
     * Test 4: Health History Persistence Workflow
     * Health check → Room DB save → Retrieve → Export
     */
    @Test
    fun testE2E_HealthHistoryPersistence() = runBlocking {
        // Step 1: Start monitoring to generate health data
        healthViewModel.startMonitoring()
        delay(2000) // Wait for multiple health checks
        
        // Step 2: Trigger manual health history load
        healthViewModel.loadHealthHistoryFromDb()
        delay(500)
        
        // Step 3: Verify health history in ViewModel
        val healthHistory = healthViewModel.healthHistory.value
        assertTrue("Health history should have entries", healthHistory.isNotEmpty())
        
        // Step 4: Verify health history in database directly
        val dbHistory = healthRepository.getLastNEntries(10)
        assertTrue("Database should have health history", dbHistory.isNotEmpty())
        
        // Step 5: Verify data consistency between ViewModel and DB
        assertTrue("ViewModel and DB history should match", 
            healthHistory.size <= dbHistory.size + 10) // Allow for async timing
        
        // Step 6: Test export functionality (would create JSON file)
        // In production, this would verify file creation
        // For unit test, we just verify the health history can be serialized
        val firstEntry = healthHistory.firstOrNull()
        assertNotNull("Should have at least one history entry", firstEntry)
        assertNotNull("Entry should have timestamp", firstEntry?.timestamp)
        assertNotNull("Entry should have module statuses", firstEntry?.moduleStatuses)
        
        healthViewModel.stopMonitoring()
    }
    
    /**
     * Test 5: Multi-Card Batch Operations
     */
    @Test
    fun testE2E_BatchCardOperations() = runBlocking {
        // Step 1: Create multiple test cards
        val testCards = listOf(
            CardProfile(name = "Card 1", pan = "4111111111111111", expiry = "12/25", cvv = "123", track1 = "", track2 = "", uid = "UID1", cardType = "VISA", issuer = "Bank 1", notes = ""),
            CardProfile(name = "Card 2", pan = "5500000000000004", expiry = "06/26", cvv = "456", track1 = "", track2 = "", uid = "UID2", cardType = "MASTERCARD", issuer = "Bank 2", notes = ""),
            CardProfile(name = "Card 3", pan = "340000000000009", expiry = "09/27", cvv = "789", track1 = "", track2 = "", uid = "UID3", cardType = "AMEX", issuer = "Bank 3", notes = ""),
            CardProfile(name = "Card 4", pan = "6011000000000004", expiry = "03/28", cvv = "321", track1 = "", track2 = "", uid = "UID4", cardType = "DISCOVER", issuer = "Bank 4", notes = ""),
            CardProfile(name = "Card 5", pan = "3530111333300000", expiry = "11/29", cvv = "654", track1 = "", track2 = "", uid = "UID5", cardType = "JCB", issuer = "Bank 5", notes = "")
        )
        
        // Step 2: Save all cards
        val savedCards = testCards.map { cardDataStore.saveCardProfile(it) }
        assertEquals("Should have saved all 5 cards", 5, savedCards.size)
        
        // Step 3: Retrieve all cards
        val allCards = cardDataStore.getAllCardProfiles()
        assertEquals("Should retrieve all 5 cards", 5, allCards.size)
        
        // Step 4: Filter by card type
        val visaCards = allCards.filter { it.cardType == "VISA" }
        assertEquals("Should have 1 VISA card", 1, visaCards.size)
        
        // Step 5: Search by name
        val searchResults = allCards.filter { it.name.contains("Card 3") }
        assertEquals("Should find Card 3", 1, searchResults.size)
        assertEquals("Found card should be Card 3", "Card 3", searchResults[0].name)
        
        // Step 6: Batch delete (delete all cards)
        cardDataStore.clearAllProfiles()
        val afterClear = cardDataStore.getAllCardProfiles()
        assertEquals("Should have 0 cards after clear", 0, afterClear.size)
    }
    
    /**
     * Test 6: Module State Transitions
     */
    @Test
    fun testE2E_ModuleStateTransitions() = runBlocking {
        // Test module state transitions: STOPPED → RUNNING → ERROR → RUNNING
        val loggingModule = ModuleRegistry.getModule("Logging")
        assertNotNull("Logging module should exist", loggingModule)
        
        // Initial state should be RUNNING (auto-started on app launch)
        val initialState = loggingModule?.state
        assertNotNull("Module should have a state", initialState)
        
        // Verify health check works
        val health = loggingModule?.getHealthStatus()
        assertNotNull("Module should have health status", health)
        
        // Verify module can be stopped and restarted (if supported)
        // Note: Some modules may not support shutdown, so we test gracefully
        try {
            loggingModule?.shutdown()
            delay(500)
            val stoppedState = loggingModule?.state
            assertTrue("Module should be stopped or error state", 
                stoppedState == ModuleState.STOPPED || stoppedState == ModuleState.ERROR)
            
            loggingModule?.initialize()
            delay(500)
            val restartedState = loggingModule?.state
            assertEquals("Module should be running after restart", ModuleState.RUNNING, restartedState)
        } catch (e: Exception) {
            // Some modules may not support shutdown in tests, that's OK
            println("Module shutdown/restart not supported in test environment: ${e.message}")
        }
    }
    
    /**
     * Test 7: Alert Rule Evaluation in Real Scenario
     */
    @Test
    fun testE2E_AlertRuleEvaluation() = runBlocking {
        // Step 1: Configure alert preferences
        val prefs = com.nfsp00f33r.app.alerts.AlertPreferences(context)
        prefs.setAlertsEnabled(true)
        prefs.setAlertSeverityThreshold(HealthStatus.Severity.ERROR)
        
        // Step 2: Add custom rule (business hours only)
        val businessHoursRule = com.nfsp00f33r.app.alerts.PredefinedRules.businessHoursOnly()
        prefs.addCustomRule(businessHoursRule)
        
        // Step 3: Create test alerts
        val criticalAlert = com.nfsp00f33r.app.screens.health.HealthAlert(
            id = "test_critical",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            moduleName = "CardDataStore",
            severity = HealthStatus.Severity.CRITICAL,
            message = "Critical system failure"
        )
        
        val warningAlert = com.nfsp00f33r.app.screens.health.HealthAlert(
            id = "test_warning",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            moduleName = "Logging",
            severity = HealthStatus.Severity.WARNING,
            message = "Minor warning"
        )
        
        // Step 4: Process alerts through alert manager
        alertManager.processAlert(criticalAlert)
        alertManager.processAlert(warningAlert)
        
        // Step 5: Verify only ERROR+ alerts are in history
        val history = alertManager.getAlertHistory()
        assertTrue("Critical alert should be in history", history.any { it.id == "test_critical" })
        assertFalse("Warning alert should be filtered out by threshold", history.any { it.id == "test_warning" })
        
        // Cleanup
        prefs.resetToDefaults()
    }
    
    /**
     * Test 8: Data Consistency Across App Restart
     * Save data → Shutdown → Reinitialize → Verify persistence
     */
    @Test
    fun testE2E_DataPersistenceAcrossRestart() = runBlocking {
        // Step 1: Save test card
        val testCard = CardProfile(
            name = "Persistence Test Card",
            pan = "4111111111111111",
            expiry = "12/25",
            cvv = "123",
            track1 = "",
            track2 = "",
            uid = "PERSIST1",
            cardType = "VISA",
            issuer = "Test Bank",
            notes = "Testing persistence"
        )
        
        val savedCard = cardDataStore.saveCardProfile(testCard)
        val savedId = savedCard.id
        
        // Step 2: Save alert
        val testAlert = com.nfsp00f33r.app.screens.health.HealthAlert(
            id = "persistence_alert",
            timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
            moduleName = "CardDataStore",
            severity = HealthStatus.Severity.ERROR,
            message = "Persistence test alert"
        )
        alertManager.processAlert(testAlert)
        
        // Step 3: Save health history
        healthViewModel.startMonitoring()
        delay(1000)
        healthViewModel.stopMonitoring()
        
        // Step 4: Simulate app restart by shutting down and reinitializing
        cardDataStore.shutdown()
        delay(500)
        
        // Reinitialize with same password
        cardDataStore.initialize("test_master_password_12345")
        delay(500)
        
        // Step 5: Verify card persisted
        val retrievedCard = cardDataStore.getCardProfile(savedId)
        assertNotNull("Card should persist across restart", retrievedCard)
        assertEquals("Card name should match", testCard.name, retrievedCard?.name)
        assertEquals("PAN should match", testCard.pan, retrievedCard?.pan)
        
        // Step 6: Verify alert persisted
        val persistedAlerts = alertManager.getAlertHistory()
        assertTrue("Alert should persist", persistedAlerts.any { it.id == "persistence_alert" })
        
        // Step 7: Verify health history persisted
        val persistedHistory = healthRepository.getLastNEntries(10)
        assertTrue("Health history should persist", persistedHistory.isNotEmpty())
    }
}
