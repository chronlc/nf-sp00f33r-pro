package com.nfsp00f33r.app.activities

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nfsp00f33r.app.R
import com.nfsp00f33r.app.application.InitializationState
import com.nfsp00f33r.app.application.NfSp00fApplication
import com.nfsp00f33r.app.theme.theme.NfSp00fTheme

class SplashActivity : ComponentActivity() {
    
    private val initState = mutableStateOf(InitializationState())
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.i("SplashActivity", "onCreate - Starting splash screen with initialization")

        setContent {
            NfSp00fTheme { SplashScreen(initState.value) }
        }
        
        // Set up initialization callback - this will also start initialization
        NfSp00fApplication.setInitializationCallback { state ->
            android.util.Log.d("SplashActivity", "Progress update: ${state.currentStep} (${(state.progress * 100).toInt()}%)")
            initState.value = state
            
            // Navigate to MainActivity when initialization complete
            if (state.isComplete && state.error == null) {
                android.util.Log.i("SplashActivity", "Initialization complete - transitioning to MainActivity")
                
                // Direct transition after organic timing from delays
                startActivity(Intent(this, com.nfsp00f33r.app.activities.MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            } else if (state.error != null) {
                android.util.Log.e("SplashActivity", "Initialization error: ${state.error}")
                // TODO: Show error dialog or fallback behavior
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        NfSp00fApplication.clearInitializationCallback()
    }
}

@Composable
fun SplashScreen(initState: InitializationState) {
    var isVisible by remember { mutableStateOf(false) }

    // Trigger animations on composition
    LaunchedEffect(Unit) { isVisible = true }

    // Animation specs following design guidelines
    val fadeInAnimation = animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
    )

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Background logo with subtle alpha
        Image(
            painter = painterResource(id = R.drawable.nfspoof3),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(0.1f)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(fadeInAnimation.value)
        ) {
            // Logo
            Image(
                painter = painterResource(id = R.drawable.nfspoof3),
                contentDescription = "nf-sp00f Logo",
                modifier = Modifier.size(120.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main title
            Text(
                text = "NFC PhreaK BoX",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Default,
                color = Color(0xFF4CAF50),
                textAlign = TextAlign.Center,
                letterSpacing = 0.25.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = "RFiD TooLKiT",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Default,
                color = Color(0xFFFFFFFF),
                textAlign = TextAlign.Center,
                letterSpacing = 0.15.sp,
                textDecoration = TextDecoration.Underline
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Progress indicator with actual progress value
            CircularProgressIndicator(
                progress = initState.progress,
                modifier = Modifier.size(48.dp),
                color = if (initState.error != null) Color(0xFFF44336) else Color(0xFF4CAF50),
                strokeWidth = 4.dp,
                trackColor = Color(0xFF1E1E1E)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Initialization message
            Text(
                text = initState.currentStep.ifEmpty { "Initializing..." },
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = if (initState.error != null) Color(0xFFF44336) else Color(0xFF4CAF50),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
            
            // Progress percentage
            if (initState.progress > 0f) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(initState.progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = Color(0xFF888888),
                    textAlign = TextAlign.Center
                )
            }
            
            // Error message if present
            if (initState.error != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Error: ${initState.error}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    color = Color(0xFFF44336),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
        }
    }
}