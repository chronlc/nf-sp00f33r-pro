package com.nfsp00f33r.app.permissions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.util.Log

class PermissionManager(private val activity: ComponentActivity) {
    
    companion object {
        private const val TAG = "PermissionManager"
        private const val PERMISSION_REQUEST_CODE = 1001
    }
    
    private var permissionCallback: ((Boolean) -> Unit)? = null
    
    private val requiredPermissions = buildList {
        // Location permission (required for Bluetooth scanning)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        
        // Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) permissions
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        } else {
            // Legacy Bluetooth permissions for Android 11 and below
            add(Manifest.permission.BLUETOOTH)
            add(Manifest.permission.BLUETOOTH_ADMIN)
        }
        
        // Note: NFC, INTERNET, and WAKE_LOCK are auto-granted
        // Storage permissions removed - not needed for hardware detection
    }
    
    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        Log.i(TAG, "Permission results: $permissions")
        Log.i(TAG, "All permissions granted: $allGranted")
        
        permissionCallback?.invoke(allGranted)
        permissionCallback = null
    }
    
    fun checkAllPermissions(): Boolean {
        val missingPermissions = requiredPermissions.filter { permission ->
            val isGranted = ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
            Log.d(TAG, "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")
            !isGranted
        }
        
        Log.i(TAG, "Checking permissions - Missing: ${missingPermissions.size}/${requiredPermissions.size}")
        missingPermissions.forEach { permission ->
            Log.w(TAG, "Missing permission: $permission")
        }
        
        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "Required permissions not granted: $missingPermissions")
        } else {
            Log.i(TAG, "All required permissions are granted")
        }
        
        return missingPermissions.isEmpty()
    }
    
    suspend fun requestPermissions(callback: (Boolean) -> Unit) {
        if (checkAllPermissions()) {
            Log.i(TAG, "All permissions already granted")
            callback(true)
            return
        }
        
        val missingPermissions = requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
        
        Log.i(TAG, "Requesting ${missingPermissions.size} missing permissions")
        missingPermissions.forEach { permission ->
            Log.d(TAG, "Requesting permission: $permission")
        }
        
        permissionCallback = callback
        permissionLauncher.launch(missingPermissions.toTypedArray())
    }
    
    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            Log.i(TAG, "Legacy permission result - All granted: $allGranted")
            
            permissionCallback?.invoke(allGranted)
            permissionCallback = null
        }
    }
    
    fun getMissingPermissions(): List<String> {
        return requiredPermissions.filter { permission ->
            ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED
        }
    }
    
    fun getRequiredPermissions(): List<String> = requiredPermissions.toList()
}