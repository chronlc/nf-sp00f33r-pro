package com.nfsp00f33r.app.activities

import android.os.Bundle
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.app.PendingIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import android.util.Log
import androidx.core.view.WindowCompat
import com.nfsp00f33r.app.theme.theme.NfSp00fTheme
import com.nfsp00f33r.app.screens.dashboard.DashboardScreen
import com.nfsp00f33r.app.screens.cardreading.CardReadingScreen
import com.nfsp00f33r.app.screens.emulation.EmulationScreen
import com.nfsp00f33r.app.screens.database.DatabaseScreen
import com.nfsp00f33r.app.screens.analysis.AnalysisScreen
import com.nfsp00f33r.app.hardware.HardwareDetectionService
import com.nfsp00f33r.app.permissions.PermissionManager
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {
    private lateinit var hardwareDetectionService: HardwareDetectionService
    private lateinit var permissionManager: PermissionManager
    private var hardwareStatus = mutableStateOf(HardwareDetectionService.HardwareStatus())
    
    // NFC Components for dual-mode operation
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var isNfcReaderModeEnabled = false
    
    // NFC event sharing with CardReadingViewModel
    private var lastNfcTag = mutableStateOf<Tag?>(null)
    private var nfcDebugInfo = mutableStateOf("Waiting for NFC...")
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("MainActivity", "onCreate - Starting MainActivity")
        
        try {
            // Set system bars to black
            enableEdgeToEdge()
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = android.graphics.Color.BLACK
            window.navigationBarColor = android.graphics.Color.BLACK
            Log.i("MainActivity", "UI configuration complete")
            
            // Initialize NFC adapter for dual-mode operation
            initializeNfcAdapter()
            
            // Initialize permission manager
            Log.i("MainActivity", "Initializing PermissionManager")
            permissionManager = PermissionManager(this)
            
            // Check and request permissions on startup
            Log.i("MainActivity", "Starting permission check")
            lifecycleScope.launch {
                try {
                    Log.i("MainActivity", "Checking if all permissions are granted...")
                    val allGranted = permissionManager.checkAllPermissions()
                    Log.i("MainActivity", "Permission check result: allGranted=$allGranted")
                    
                    if (!allGranted) {
                        Log.i("MainActivity", "Some permissions missing - requesting permissions")
                        permissionManager.requestPermissions { granted ->
                            Log.i("MainActivity", "Permission request callback: granted=$granted")
                            if (granted) {
                                Log.i("MainActivity", "All essential permissions granted - starting hardware detection")
                            } else {
                                Log.i("MainActivity", "Some non-essential permissions denied - starting with available permissions")
                            }
                            // Always start hardware detection since we have location and Bluetooth permissions
                            initializeHardwareService()
                        }
                    } else {
                        Log.i("MainActivity", "All permissions already granted - initializing hardware service")
                        initializeHardwareService()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Error in permission check: ${e.message}", e)
                }
            }
            
            Log.i("MainActivity", "Setting content")
            setContent { 
                NfSp00fTheme { 
                    val currentStatus by hardwareStatus
                    NfSp00fApp(hardwareStatus = currentStatus) 
                } 
            }
            Log.i("MainActivity", "onCreate complete")
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}", e)
        }
    }
    
    private fun initializeHardwareService() {
        Log.i("MainActivity", "Initializing hardware detection service...")
        hardwareDetectionService = HardwareDetectionService(this, this, permissionManager)
        
        // Set hardware status callback to update dashboard
        hardwareDetectionService.setStatusCallback { status ->
            hardwareStatus.value = status
            Log.i("MainActivity", "Hardware status updated: Score ${status.hardwareScore}/100 - ${status.statusMessage}")
        }
        
        Log.i("MainActivity", "Starting hardware detection...")
        lifecycleScope.launch {
            try {
                hardwareDetectionService.startDetection()
                Log.i("MainActivity", "Hardware detection started successfully")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start hardware detection: ${e.message}", e)
            }
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }
    
    override fun onResume() {
        super.onResume()
        enableNfcReaderMode()
    }
    
    override fun onPause() {
        super.onPause()
        disableNfcReaderMode()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.e("MainActivity", "===== onNewIntent CALLED =====")
        Log.e("MainActivity", "Intent action: ${intent?.action}")
        setIntent(intent) // Update activity intent for SINGLE_TOP launch mode
        handleNfcIntent(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        disableNfcReaderMode()
        if (::hardwareDetectionService.isInitialized) {
            hardwareDetectionService.cleanup()
        }
    }
    
    companion object {
        // Shared NFC state for ViewModels to access
        var currentNfcTag: Tag? = null
        var nfcDebugMessage: String = "NFC System Ready"
        var lastNfcDetectionTime: Long = 0L
        
        // Callback for auto-navigation to Card Reading tab
        var onEmvCardDetected: (() -> Unit)? = null
        
        fun updateNfcState(tag: Tag?, debugMessage: String) {
            currentNfcTag = tag
            nfcDebugMessage = debugMessage
            lastNfcDetectionTime = System.currentTimeMillis()
            Log.i("MainActivity", "NFC State Updated: $debugMessage")
            
            // Trigger auto-navigation callback if EMV card detected
            if (tag != null) {
                onEmvCardDetected?.invoke()
            }
        }
    }
    
    /**
     * Initialize NFC Adapter for dual-mode operation
     * - HCE Service runs automatically in background
     * - Reader Mode is managed by activity lifecycle
     */
    private fun initializeNfcAdapter() {
        try {
            nfcAdapter = NfcAdapter.getDefaultAdapter(this)
            
            if (nfcAdapter == null) {
                Log.w("MainActivity", "NFC not supported on this device")
                return
            }
            
            val adapter = nfcAdapter
            if (adapter == null || !adapter.isEnabled) {
                Log.w("MainActivity", "NFC is disabled - user needs to enable it")
                return
            }
            
            // Create pending intent for foreground dispatch
            pendingIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_MUTABLE
            )
            
            Log.i("MainActivity", "NFC Adapter initialized successfully")
            Log.i("MainActivity", "HCE Service: Available in background for card emulation")
            Log.i("MainActivity", "Reader Mode: Ready for card scanning")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to initialize NFC adapter: ${e.message}", e)
        }
    }
    
    /**
     * Enable NFC Reader Mode for scanning cards
     * This works alongside the HCE service without conflicts
     */
    private fun enableNfcReaderMode() {
        val adapter = nfcAdapter
        if (adapter == null || !adapter.isEnabled || isNfcReaderModeEnabled) {
            return
        }
        
        try {
            // Enable foreground dispatch for all NFC technologies
            val techFilters = arrayOf(
                arrayOf(IsoDep::class.java.name),
                arrayOf("android.nfc.tech.NfcA"),
                arrayOf("android.nfc.tech.NfcB")
            )
            
            val adapter = nfcAdapter
            if (adapter != null) {
                adapter.enableForegroundDispatch(
                    this,
                    pendingIntent,
                    null, // Accept all NDEF message types
                    techFilters
                )
            }
            
            isNfcReaderModeEnabled = true
            Log.i("MainActivity", "NFC Reader Mode enabled - ready to scan EMV cards")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to enable NFC reader mode: ${e.message}", e)
        }
    }
    
    /**
     * Disable NFC Reader Mode
     * HCE Service continues running in background
     */
    private fun disableNfcReaderMode() {
        if (!isNfcReaderModeEnabled) {
            return
        }
        
        try {
            val adapter = nfcAdapter
            if (adapter != null) {
                adapter.disableForegroundDispatch(this)
            }
            isNfcReaderModeEnabled = false
            Log.d("MainActivity", "NFC foreground dispatch disabled")
            
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to disable NFC reader mode: ${e.message}", e)
        }
    }
    
    /**
     * Handle NFC card detection intents
     * Routes cards to the appropriate screen/handler
     */
    private fun handleNfcIntent(intent: Intent?) {
        Log.e("MainActivity", "===== handleNfcIntent CALLED =====")
        if (intent == null) {
            Log.e("MainActivity", "Intent is NULL - aborting")
            return
        }
        
        val action = intent.action
        Log.e("MainActivity", "NFC Intent received: $action")
        val extras = intent.extras
        val extraKeys = if (extras != null) {
            val keySet = extras.keySet()
            if (keySet != null) keySet.joinToString(", ") else "null keySet"
        } else "no extras"
        Log.e("MainActivity", "Intent extras: $extraKeys")
        
        when (action) {
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                val tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                }
                
                if (tag != null) {
                    Log.e("MainActivity", "✅ NFC Tag detected via intent - processing...")
                    handleNfcTag(tag)
                } else {
                    Log.e("MainActivity", "❌ NFC Intent received but NO TAG FOUND")
                }
            }
            else -> {
                Log.e("MainActivity", "Unknown NFC action: $action")
            }
        }
    }
    
    /**
     * Process detected NFC tag/card
     * Determines card type and routes to appropriate handler
     */
    private fun handleNfcTag(tag: Tag) {
        try {
            Log.e("MainActivity", "===== NFC CARD DETECTED =====")
            Log.e("MainActivity", "Card tech list: ${tag.techList.joinToString(", ")}")
            Log.e("MainActivity", "Card ID: ${tag.id.joinToString(":") { "%02X".format(it) }}")
            
            // Check if it's an ISO-DEP card (EMV/Payment card)
            if (tag.techList.contains(IsoDep::class.java.name)) {
                Log.e("MainActivity", "✅ EMV/ISO-DEP card detected - routing to Card Reading screen")
                
                // Switch to Card Reading tab if not already there
                // This would need to be passed to the Compose state management
                // For now, just log the detection
                
                val isoDep = IsoDep.get(tag)
                if (isoDep != null) {
                    val card = isoDep
                    Log.e("MainActivity", "IsoDep instance acquired")
                    val historicalBytes = card.historicalBytes
                    val historyString = if (historicalBytes != null) {
                        historicalBytes.joinToString(":") { "%02X".format(it) }
                    } else {
                        "None"
                    }
                    Log.e("MainActivity", "Historical bytes: $historyString")
                    
                    // Share card data with CardReadingViewModel
                    updateNfcState(
                        tag,
                        "EMV Card: ${tag.id.joinToString(":") { "%02X".format(it) }} - ISO-DEP Ready"
                    )
                    
                    // Trigger card processing
                    Log.e("MainActivity", "✅ NFC card data shared with ViewModels via companion object")
                }
            } else {
                Log.e("MainActivity", "Non-EMV card detected - general NFC processing")
            }
            
        } catch (e: Exception) {
            Log.e("MainActivity", "❌ ERROR processing NFC tag: ${e.message}", e)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfSp00fApp(hardwareStatus: HardwareDetectionService.HardwareStatus) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    
    // Set up auto-navigation callback for EMV card detection
    DisposableEffect(Unit) {
        MainActivity.onEmvCardDetected = {
            // Switch to Card Reading tab (index 1)
            selectedTab = 1
            Log.i("MainActivity", "Auto-switching to Card Reading tab for EMV card")
        }
        onDispose {
            MainActivity.onEmvCardDetected = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Security,
                            contentDescription = "Security Shield",
                            tint = Color(0xFF4CAF50)
                        )
                        Column {
                            Text(
                                "nf-sp00f33r",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4CAF50)
                            )
                            Text(
                                "HCE + Reader Mode",
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                color = Color(0xFF4CAF50).copy(alpha = 0.7f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.Black) {
                val items = listOf(
                    "Dashboard" to Icons.Default.Dashboard,
                    "Read" to Icons.Default.Nfc,
                    "Emulate" to Icons.Default.Security,
                    "Database" to Icons.Default.Storage,
                    "Analysis" to Icons.Default.Analytics
                )

                items.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                icon,
                                contentDescription = label,
                                tint = if (selectedTab == index) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.6f)
                            )
                        },
                        label = { 
                            Text(
                                label, 
                                color = if (selectedTab == index) Color(0xFF4CAF50) else Color(0xFF4CAF50).copy(alpha = 0.6f)
                            ) 
                        },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF4CAF50),
                            selectedTextColor = Color(0xFF4CAF50),
                            unselectedIconColor = Color(0xFF4CAF50).copy(alpha = 0.6f),
                            unselectedTextColor = Color(0xFF4CAF50).copy(alpha = 0.6f),
                            indicatorColor = Color.Transparent
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (selectedTab) {
                0 -> DashboardScreen()
                1 -> CardReadingScreen()
                2 -> EmulationScreen()
                3 -> DatabaseScreen()
                4 -> AnalysisScreen()
            }
        }
    }
}
