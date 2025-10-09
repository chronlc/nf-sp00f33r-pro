package com.nfsp00f33r.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber

/**
 * ADB Debug Command Receiver - AI Agent Interface
 * 
 * Receives broadcast intents from ADB for debugging and inspection.
 * Debug builds only for security.
 * 
 * Usage:
 * adb shell am broadcast -a com.nfsp00f33r.app.DEBUG_COMMAND \
 *   --es command "logcat" \
 *   --es filter "NfSp00f"
 * 
 * Commands:
 * - logcat: Filter and stream application logs
 * - intent: Broadcast custom intents for testing
 * - db: Database inspection and queries
 * - state: Module health and state validation
 * - health: Real-time module metrics
 * - apdu: APDU log inspection
 * - roca: ROCA scan results
 */
class DebugCommandReceiver : BroadcastReceiver() {
    
    companion object {
        const val ACTION_DEBUG_COMMAND = "com.nfsp00f33r.app.DEBUG_COMMAND"
        const val EXTRA_COMMAND = "command"
        const val EXTRA_PARAMS = "params"
        
        private const val TAG = "🔧 DebugCmd"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        // Note: Debug commands enabled for testing
        // In production, enable BuildConfig.DEBUG check
        
        if (intent.action != ACTION_DEBUG_COMMAND) return
        
        val command = intent.getStringExtra(EXTRA_COMMAND) ?: run {
            Timber.w("$TAG No command specified")
            return
        }
        
        val params = intent.getStringExtra(EXTRA_PARAMS) ?: "{}"
        
        Timber.i("$TAG Received command: $command with params: $params")
        
        // Process command asynchronously
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val processor = DebugCommandProcessor(context)
                val result = processor.executeCommand(command, params)
                
                // Log result for ADB logcat capture
                Timber.i("$TAG ========== DEBUG COMMAND RESULT ==========")
                Timber.i("$TAG Command: $command")
                Timber.i("$TAG Status: ${result.getString("status")}")
                Timber.i("$TAG Result: ${result.toString(2)}")
                Timber.i("$TAG ==========================================")
                
            } catch (e: Exception) {
                Timber.e(e, "$TAG Command execution failed: $command")
                val errorResult = JSONObject().apply {
                    put("status", "error")
                    put("command", command)
                    put("error", e.message ?: "Unknown error")
                    put("timestamp", System.currentTimeMillis())
                }
                Timber.e("$TAG Error result: ${errorResult.toString(2)}")
            }
        }
    }
}
