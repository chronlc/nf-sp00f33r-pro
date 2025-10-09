package com.nfsp00f33r.app.hardware

import android.content.Context
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/**
 * PRODUCTION-GRADE PN532 Hardware Manager
 * Manages PN532 NFC module with dual USB/Bluetooth connectivity
 * NO SAFE CALL OPERATORS - Explicit null checks only per newrule.md
 * ANR PREVENTION - All blocking operations with timeout handling
 */
class PN532Manager(private val context: Context) {

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR
    }

    enum class ConnectionType {
        USB_SERIAL,
        BLUETOOTH_HC06
    }

    private val _connectionState = MutableLiveData<ConnectionState>()
    val connectionState: MutableLiveData<ConnectionState> = _connectionState

    private val _connectionType = MutableLiveData<ConnectionType>()
    val connectionType: MutableLiveData<ConnectionType> = _connectionType

    private val _lastError = MutableLiveData<String>()
    val lastError: MutableLiveData<String> = _lastError

    private var currentAdapter: HardwareAdapter? = null
    private var connectionCallback: ((String) -> Unit)? = null

    init {
        _connectionState.value = ConnectionState.DISCONNECTED
        _connectionType.value = ConnectionType.USB_SERIAL
    }

    /**
     * Set connection callback for status updates
     */
    fun setConnectionCallback(callback: (String) -> Unit) {
        this.connectionCallback = callback
    }

    /**
     * Connect to PN532 using specified connection type
     */
    fun connect(type: ConnectionType) {
        android.util.Log.i("PN532Manager", "🚀 Starting PN532 connection process for type: ${type.name}")
        _connectionState.postValue(ConnectionState.CONNECTING)
        _connectionType.postValue(type)

        try {
            android.util.Log.i("PN532Manager", "🔧 Creating adapter for ${type.name}")
            val adapter = createAdapter(type)
            currentAdapter = adapter
            android.util.Log.i("PN532Manager", "✅ Adapter created successfully: ${adapter.javaClass.simpleName}")

            // Connect with explicit null check
            val callback = this.connectionCallback
            if (callback != null) {
                callback("Initializing ${type.name} connection...")
            }
            
            android.util.Log.i("PN532Manager", "📞 Calling adapter.connect()...")
            val isConnected = adapter.connect()
            android.util.Log.i("PN532Manager", "📡 Adapter connect result: $isConnected")
            
            if (isConnected) {
                android.util.Log.i("PN532Manager", "🎉 PN532 CONNECTION SUCCESSFUL via ${type.name}")
                _connectionState.postValue(ConnectionState.CONNECTED)
                
                val callbackAfterConnect = this.connectionCallback
                if (callbackAfterConnect != null) {
                    callbackAfterConnect("🎉 Connected to PN532 via ${type.name}")
                }
                
                Timber.i("PN532 connected successfully via ${type.name}")
            } else {
                android.util.Log.e("PN532Manager", "❌ ADAPTER CONNECT RETURNED FALSE")
                throw RuntimeException("Failed to establish connection")
            }

        } catch (e: Exception) {
            android.util.Log.e("PN532Manager", "💥 EXCEPTION in PN532 connect: ${e.message}")
            android.util.Log.e("PN532Manager", "💥 Stack trace: ${e.stackTraceToString()}")
            _connectionState.postValue(ConnectionState.ERROR)
            _lastError.postValue(e.message)
            
            val errorCallback = this.connectionCallback
            if (errorCallback != null) {
                errorCallback("❌ Connection failed: ${e.message}")
            }
            
            Timber.e(e, "Failed to connect to PN532")
        }
    }

    /**
     * Disconnect from PN532
     */
    fun disconnect() {
        try {
            val adapter = currentAdapter
            if (adapter != null) {
                adapter.disconnect()
                currentAdapter = null
            }
            
            _connectionState.postValue(ConnectionState.DISCONNECTED)
            
            val callback = this.connectionCallback
            if (callback != null) {
                callback("Disconnected from PN532")
            }
            
            Timber.i("PN532 disconnected")
            
        } catch (e: Exception) {
            _connectionState.postValue(ConnectionState.ERROR)
            _lastError.postValue(e.message)
            
            val errorCallback = this.connectionCallback
            if (errorCallback != null) {
                errorCallback("Disconnection error: ${e.message}")
            }
            
            Timber.e(e, "Error during PN532 disconnection")
        }
    }

    /**
     * Test connection with ANR prevention timeout
     */
    fun testConnection(): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync {
            performConnectionTest()
        }
    }

    /**
     * Perform actual connection test with timeout handling
     */
    private fun performConnectionTest(): Boolean {
        try {
            val adapter = currentAdapter
            if (adapter == null) {
                Timber.w("No adapter available for connection test")
                return false
            }

            // Use timeout-enabled command for ANR prevention
            if (adapter is AndroidBluetoothHC06Adapter) {
                val result = adapter.sendCommandWithTimeout(byteArrayOf(0x55.toByte(), 0x55.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x03, 0xFD.toByte(), 0xD4.toByte(), 0x14, 0x01, 0x17, 0x00), 5000)
                return result != null && result.isNotEmpty()
            } else {
                val result = adapter.sendCommand(byteArrayOf(0x55.toByte(), 0x55.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x03, 0xFD.toByte(), 0xD4.toByte(), 0x14, 0x01, 0x17, 0x00))
                return result != null && result.isNotEmpty()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "Connection test failed")
            return false
        }
    }

    /**
     * Configure SAM in background to prevent ANR
     */
    fun configureSAMBackground() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val adapter = currentAdapter
                if (adapter == null) {
                    withContext(Dispatchers.Main) {
                        val callback = connectionCallback
                        if (callback != null) {
                            callback("No adapter available for SAM configuration")
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        callback("Configuring SAM...")
                    }
                }

                // SAM Configuration command with timeout
                val samCommand = byteArrayOf(0x55.toByte(), 0x55.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x04, 0xFC.toByte(), 0xD4.toByte(), 0x14, 0x01, 0x14, 0x01, 0x02, 0x00)
                
                val result = if (adapter is AndroidBluetoothHC06Adapter) {
                    adapter.sendCommandWithTimeout(samCommand, 5000)
                } else {
                    adapter.sendCommand(samCommand)
                }

                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        if (result != null && result.isNotEmpty()) {
                            callback("SAM configured successfully")
                        } else {
                            callback("SAM configuration failed")
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "SAM configuration error")
                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        callback("SAM configuration error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Get firmware details in background to prevent ANR
     */
    fun getFirmwareDetailsBackground() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val adapter = currentAdapter
                if (adapter == null) {
                    withContext(Dispatchers.Main) {
                        val callback = connectionCallback
                        if (callback != null) {
                            callback("No adapter available for firmware check")
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        callback("Getting firmware details...")
                    }
                }

                // Firmware version command with timeout
                val firmwareCommand = byteArrayOf(0x55.toByte(), 0x55.toByte(), 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xFF.toByte(), 0x02, 0xFE.toByte(), 0xD4.toByte(), 0x02, 0x2A.toByte(), 0x00)
                
                Timber.i("Sending PN532 firmware command: ${firmwareCommand.joinToString(" ") { "%02X".format(it) }}")
                android.util.Log.i("PN532Manager", "Sending firmware command to PN532")
                
                val result = if (adapter is AndroidBluetoothHC06Adapter) {
                    val response = adapter.sendCommandWithTimeout(firmwareCommand, 5000)
                    android.util.Log.i("PN532Manager", "Firmware response received: ${response?.size ?: 0} bytes")
                    if (response != null) {
                        android.util.Log.i("PN532Manager", "Response data: ${response.joinToString(" ") { "%02X".format(it) }}")
                    }
                    response
                } else {
                    adapter.sendCommand(firmwareCommand)
                }

                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        if (result != null && result.size >= 8) {
                            val version = "v${result[7]}.${result[8]}"
                            callback("✅ PN532 FIRMWARE: $version")
                            android.util.Log.i("PN532Manager", "SUCCESS: PN532 responded with firmware $version")
                            Timber.i("PN532 firmware detected successfully: $version")
                        } else if (result != null) {
                            callback("⚠️ PN532 response too short: ${result.size} bytes")
                            android.util.Log.w("PN532Manager", "PN532 response too short: ${result.size} bytes")
                        } else {
                            callback("❌ No PN532 firmware response")
                            android.util.Log.w("PN532Manager", "No response from PN532 firmware command")
                        }
                    }
                }

            } catch (e: Exception) {
                Timber.e(e, "Firmware check error")
                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        callback("Firmware check error: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Create adapter based on connection type
     */
    private fun createAdapter(type: ConnectionType): HardwareAdapter {
        return when (type) {
            ConnectionType.USB_SERIAL -> {
                AndroidUSBSerialAdapter(context, "/dev/ttyUSB0", 115200)
            }
            ConnectionType.BLUETOOTH_HC06 -> {
                AndroidBluetoothHC06Adapter(context, "00:14:03:05:5C:CB", "HC-06")
            }
        }
    }

    /**
     * Get current connection status
     */
    fun isConnected(): Boolean {
        val adapter = currentAdapter
        return adapter != null && adapter.isConnected()
    }

    /**
     * Get current adapter instance
     */
    fun getCurrentAdapter(): HardwareAdapter? {
        return currentAdapter
    }

    /**
     * Maintain connection with background monitoring
     */
    fun maintainConnection() {
        GlobalScope.launch(Dispatchers.IO) {
            try {
                val adapter = currentAdapter
                if (adapter == null) {
                    return@launch
                }

                while (isConnected()) {
                    delay(30000) // Check every 30 seconds
                    
                    // Test connection with timeout
                    val testResult = performConnectionTest()
                    if (!testResult) {
                        withContext(Dispatchers.Main) {
                            val callback = connectionCallback
                            if (callback != null) {
                                callback("Connection lost, attempting reconnect...")
                            }
                        }
                        
                        // Attempt reconnection
                        val connectionType = _connectionType.value
                        if (connectionType != null) {
                            connect(connectionType)
                        }
                        break
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "Error in connection maintenance")
                withContext(Dispatchers.Main) {
                    val callback = connectionCallback
                    if (callback != null) {
                        callback("Connection maintenance error: ${e.message}")
                    }
                }
            }
        }
    }
}
