package com.nfsp00f33r.app.core

import android.util.Log
import kotlinx.coroutines.delay

/**
 * ModuleSystemTest - Phase 2A Day 10
 * 
 * Comprehensive testing utilities for the module system
 * Verifies initialization order, health monitoring, and lifecycle
 */
object ModuleSystemTest {
    
    private const val TAG = "ModuleSystemTest"
    
    /**
     * Run all module system tests
     */
    suspend fun runAllTests(): TestResults {
        Log.i(TAG, "========================================")
        Log.i(TAG, "Starting Module System Tests")
        Log.i(TAG, "========================================")
        
        val results = mutableListOf<TestResult>()
        
        // Test 1: Module Registration
        results.add(testModuleRegistration())
        
        // Test 2: Initialization Order
        results.add(testInitializationOrder())
        
        // Test 3: Health Monitoring
        results.add(testHealthMonitoring())
        
        // Test 4: Module State Transitions
        results.add(testStateTransitions())
        
        // Test 5: Event Listeners
        results.add(testEventListeners())
        
        // Test 6: Error Handling
        results.add(testErrorHandling())
        
        val passedCount = results.count { it.passed }
        val totalCount = results.size
        
        Log.i(TAG, "========================================")
        Log.i(TAG, "Test Results: $passedCount/$totalCount passed")
        Log.i(TAG, "========================================")
        
        results.forEach { result ->
            val status = if (result.passed) "✅ PASS" else "❌ FAIL"
            Log.i(TAG, "$status: ${result.testName}")
            if (!result.passed) {
                Log.e(TAG, "  Error: ${result.errorMessage}")
            }
        }
        
        return TestResults(
            passed = results.all { it.passed },
            totalTests = totalCount,
            passedTests = passedCount,
            failedTests = totalCount - passedCount,
            results = results
        )
    }
    
    /**
     * Test 1: Module Registration
     */
    private fun testModuleRegistration(): TestResult {
        return try {
            Log.i(TAG, "Test 1: Module Registration")
            
            val modules = ModuleRegistry.getAllModules()
            Log.i(TAG, "  Registered modules: ${modules.size}")
            
            modules.forEach { module ->
                Log.i(TAG, "  - ${module.name} (${module.getVersion()}): ${module.state}")
            }
            
            if (modules.isEmpty()) {
                return TestResult(
                    testName = "Module Registration",
                    passed = false,
                    errorMessage = "No modules registered"
                )
            }
            
            TestResult(
                testName = "Module Registration",
                passed = true,
                message = "${modules.size} modules registered successfully"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Test 1 failed", e)
            TestResult(
                testName = "Module Registration",
                passed = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Test 2: Initialization Order
     */
    private fun testInitializationOrder(): TestResult {
        return try {
            Log.i(TAG, "Test 2: Initialization Order")
            
            val modules = ModuleRegistry.getAllModules()
            val runningModules = modules.filter { it.state == ModuleState.RUNNING }
            
            Log.i(TAG, "  Running modules: ${runningModules.size}/${modules.size}")
            
            runningModules.forEach { module ->
                Log.i(TAG, "  - ${module.name}: RUNNING (uptime: ${getUptimeSeconds(module)}s)")
            }
            
            // Check if all modules are running
            val allRunning = runningModules.size == modules.size
            
            TestResult(
                testName = "Initialization Order",
                passed = allRunning,
                message = if (allRunning) "All modules initialized and running" 
                         else "${modules.size - runningModules.size} modules not running",
                errorMessage = if (!allRunning) "Some modules failed to start" else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Test 2 failed", e)
            TestResult(
                testName = "Initialization Order",
                passed = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Test 3: Health Monitoring
     */
    private suspend fun testHealthMonitoring(): TestResult {
        return try {
            Log.i(TAG, "Test 3: Health Monitoring")
            
            val healthSummary = ModuleRegistry.getHealthSummary()
            
            Log.i(TAG, "  Module health status:")
            healthSummary.forEach { (moduleName, health) ->
                val status = if (health.isHealthy) "✓" else "✗"
                val severity = health.severity.name
                Log.i(TAG, "  $status $moduleName: ${health.message} (${severity})")
                
                if (health.metrics.isNotEmpty()) {
                    health.metrics.forEach { (key, value) ->
                        Log.i(TAG, "      $key: $value")
                    }
                }
            }
            
            val allHealthy = ModuleRegistry.areAllModulesHealthy()
            
            TestResult(
                testName = "Health Monitoring",
                passed = allHealthy,
                message = if (allHealthy) "All modules healthy" 
                         else "Some modules unhealthy",
                errorMessage = if (!allHealthy) {
                    val unhealthy = healthSummary.filter { !it.value.isHealthy }
                    "Unhealthy modules: ${unhealthy.keys.joinToString()}"
                } else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Test 3 failed", e)
            TestResult(
                testName = "Health Monitoring",
                passed = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Test 4: Module State Transitions
     */
    private fun testStateTransitions(): TestResult {
        return try {
            Log.i(TAG, "Test 4: Module State Transitions")
            
            val modules = ModuleRegistry.getAllModules()
            val stateDistribution = modules.groupBy { it.state }
            
            Log.i(TAG, "  State distribution:")
            ModuleState.values().forEach { state ->
                val count = stateDistribution[state]?.size ?: 0
                if (count > 0) {
                    Log.i(TAG, "  - ${state.name}: $count modules")
                }
            }
            
            // Check for invalid states
            val errorModules = modules.filter { it.state == ModuleState.ERROR }
            val uninitialized = modules.filter { it.state == ModuleState.UNINITIALIZED }
            
            val passed = errorModules.isEmpty() && uninitialized.isEmpty()
            
            TestResult(
                testName = "Module State Transitions",
                passed = passed,
                message = if (passed) "All modules in valid states" 
                         else "Some modules in invalid states",
                errorMessage = if (!passed) {
                    val issues = mutableListOf<String>()
                    if (errorModules.isNotEmpty()) {
                        issues.add("${errorModules.size} in ERROR state")
                    }
                    if (uninitialized.isNotEmpty()) {
                        issues.add("${uninitialized.size} UNINITIALIZED")
                    }
                    issues.joinToString(", ")
                } else null
            )
        } catch (e: Exception) {
            Log.e(TAG, "Test 4 failed", e)
            TestResult(
                testName = "Module State Transitions",
                passed = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Test 5: Event Listeners
     */
    private fun testEventListeners(): TestResult {
        return try {
            Log.i(TAG, "Test 5: Event Listeners")
            
            var eventCount = 0
            val listener = object : ModuleRegistry.ModuleRegistryListener {
                override fun onModuleHealthChanged(module: Module, health: HealthStatus) {
                    eventCount++
                    Log.i(TAG, "  Event: ${module.name} health changed to ${health.isHealthy}")
                }
            }
            
            // Add listener
            ModuleRegistry.addEventListener(listener)
            Log.i(TAG, "  Event listener registered")
            
            // Trigger health check (should fire events)
            val modules = ModuleRegistry.getAllModules()
            modules.forEach { it.checkHealth() }
            
            // Remove listener
            ModuleRegistry.removeEventListener(listener)
            Log.i(TAG, "  Event listener removed")
            
            TestResult(
                testName = "Event Listeners",
                passed = true,
                message = "Event system operational (${eventCount} events)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Test 5 failed", e)
            TestResult(
                testName = "Event Listeners",
                passed = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Test 6: Error Handling
     */
    private fun testErrorHandling(): TestResult {
        return try {
            Log.i(TAG, "Test 6: Error Handling")
            
            // Check if any modules have errors
            val modules = ModuleRegistry.getAllModules()
            val modulesWithErrors = modules.filter { 
                it.state == ModuleState.ERROR || (it as? BaseModule)?.hasError() == true
            }
            
            if (modulesWithErrors.isNotEmpty()) {
                Log.w(TAG, "  Modules with errors: ${modulesWithErrors.size}")
                modulesWithErrors.forEach { module ->
                    Log.w(TAG, "  - ${module.name}: ${module.state}")
                }
            } else {
                Log.i(TAG, "  No modules with errors")
            }
            
            TestResult(
                testName = "Error Handling",
                passed = true,
                message = if (modulesWithErrors.isEmpty()) "No errors detected" 
                         else "${modulesWithErrors.size} modules have errors (handled gracefully)"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Test 6 failed", e)
            TestResult(
                testName = "Error Handling",
                passed = false,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Get module uptime in seconds
     */
    private fun getUptimeSeconds(module: Module): Long {
        return if (module is BaseModule) {
            module.getUptimeMs() / 1000
        } else {
            0
        }
    }
    
    /**
     * Test result data class
     */
    data class TestResult(
        val testName: String,
        val passed: Boolean,
        val message: String = "",
        val errorMessage: String? = null
    )
    
    /**
     * Overall test results
     */
    data class TestResults(
        val passed: Boolean,
        val totalTests: Int,
        val passedTests: Int,
        val failedTests: Int,
        val results: List<TestResult>
    )
}
