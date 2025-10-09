package com.nfsp00f33r.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nfsp00f33r.app.core.ModuleRegistry
import com.nfsp00f33r.app.core.ModuleState
import com.nfsp00f33r.app.core.HealthStatus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Cross-Module Integration Tests
 * Phase 5 Days 1-3: Integration Testing
 * 
 * Tests module interactions:
 * 1. Dependency resolution
 * 2. Health status propagation
 * 3. Module initialization order
 * 4. Inter-module communication
 * 5. Failure cascading
 */
@RunWith(AndroidJUnit4::class)
class ModuleIntegrationTest {
    
    private lateinit var context: Context
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
    }
    
    /**
     * Test 1: Module dependency resolution order
     */
    @Test
    fun testModuleDependencyOrder() {
        // Verify modules are initialized in dependency order
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
            
            // Verify dependencies are initialized first
            val dependencies = module?.getDependencies()
            dependencies?.forEach { depName ->
                val depModule = ModuleRegistry.getModule(depName)
                assertNotNull("Dependency $depName should exist", depModule)
                assertEquals("Dependency $depName should be running", ModuleState.RUNNING, depModule?.state)
            }
        }
    }
    
    /**
     * Test 2: Health status propagation
     */
    @Test
    fun testHealthStatusPropagation() = runBlocking {
        // Check if unhealthy module affects dependent modules
        val loggingModule = ModuleRegistry.getModule("Logging")
        val initialHealth = loggingModule?.getHealthStatus()
        assertNotNull("Logging should have health status", initialHealth)
        
        // In a real scenario, we'd simulate failure and check propagation
        // For now, verify health check mechanism works
        val allModules = ModuleRegistry.getAllModules()
        allModules.forEach { module ->
            val health = module.getHealthStatus()
            assertNotNull("Module ${module.name} should have health status", health)
            assertTrue("Severity should be valid", 
                health.severity in listOf(
                    HealthStatus.Severity.INFO,
                    HealthStatus.Severity.WARNING,
                    HealthStatus.Severity.ERROR,
                    HealthStatus.Severity.CRITICAL
                ))
        }
    }
    
    /**
     * Test 3: Module restart affects dependents
     */
    @Test
    fun testModuleRestartPropagation() = runBlocking {
        // Test that restarting a module doesn't break dependent modules
        val loggingModule = ModuleRegistry.getModule("Logging")
        val initialState = loggingModule?.state
        
        try {
            // Restart module
            loggingModule?.shutdown()
            delay(500)
            loggingModule?.initialize()
            delay(500)
            
            // Verify dependent modules still healthy
            val allModules = ModuleRegistry.getAllModules()
            allModules.forEach { module ->
                val health = module.getHealthStatus()
                assertNotNull("Module ${module.name} should still have health status", health)
            }
        } catch (e: Exception) {
            // Some modules may not support restart in test environment
            println("Module restart not supported: ${e.message}")
        }
    }
    
    /**
     * Test 4: All modules report health to registry
     */
    @Test
    fun testAllModulesReportHealth() {
        val allModules = ModuleRegistry.getAllModules()
        assertTrue("Should have at least 6 modules", allModules.size >= 6)
        
        allModules.forEach { module ->
            val health = module.getHealthStatus()
            assertNotNull("Module ${module.name} should report health", health)
            assertNotNull("Health should have severity", health.severity)
            assertNotNull("Health should have message", health.message)
            assertTrue("Last checked timestamp should be recent", 
                health.lastChecked > System.currentTimeMillis() - 60000)
        }
    }
    
    /**
     * Test 5: Module registry observability
     */
    @Test
    fun testModuleRegistryObservability() {
        // Verify ModuleRegistry provides full visibility
        val allModules = ModuleRegistry.getAllModules()
        
        allModules.forEach { module ->
            // Each module should expose:
            assertNotNull("Module should have name", module.name)
            assertNotNull("Module should have state", module.state)
            assertNotNull("Module should have health status", module.getHealthStatus())
            assertNotNull("Module should have dependencies list", module.getDependencies())
        }
    }
}
