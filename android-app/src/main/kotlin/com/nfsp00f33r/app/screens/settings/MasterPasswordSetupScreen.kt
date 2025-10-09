package com.nfsp00f33r.app.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

/**
 * MasterPasswordSetupScreen - Phase 1A Day 4
 * 
 * First-launch screen for setting up encrypted storage master password.
 * Required before accessing any card data.
 * 
 * Uses SecureMasterPasswordManager for Android Keystore-backed password storage.
 * Implements real-time password validation and strength indicator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MasterPasswordSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: MasterPasswordViewModel = viewModel()
) {
    val focusManager = LocalFocusManager.current
    val confirmFocusRequester = remember { FocusRequester() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure Storage Setup") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Icon
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = "Security",
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Set Master Password",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Description
            Text(
                text = "Your master password encrypts all card data with AES-256-GCM. " +
                        "Choose a strong password you'll remember.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Password Field
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.updatePassword(it) },
                label = { Text("Master Password") },
                placeholder = { Text("Enter password (min 8 characters)") },
                leadingIcon = {
                    Icon(Icons.Default.Lock, "Password")
                },
                trailingIcon = {
                    IconButton(onClick = { viewModel.togglePasswordVisibility() }) {
                        Icon(
                            imageVector = if (viewModel.isPasswordVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = "Toggle password visibility"
                        )
                    }
                },
                visualTransformation = if (viewModel.isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { confirmFocusRequester.requestFocus() }
                ),
                isError = viewModel.passwordError != null,
                supportingText = {
                    if (viewModel.passwordError != null) {
                        Text(viewModel.passwordError!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Password Strength Indicator
            if (viewModel.password.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                PasswordStrengthIndicator(viewModel.passwordStrength)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Confirm Password Field
            OutlinedTextField(
                value = viewModel.confirmPassword,
                onValueChange = { viewModel.updateConfirmPassword(it) },
                label = { Text("Confirm Password") },
                placeholder = { Text("Re-enter password") },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, "Confirm")
                },
                trailingIcon = {
                    IconButton(onClick = { viewModel.toggleConfirmVisibility() }) {
                        Icon(
                            imageVector = if (viewModel.isConfirmVisible) {
                                Icons.Default.Visibility
                            } else {
                                Icons.Default.VisibilityOff
                            },
                            contentDescription = "Toggle confirm visibility"
                        )
                    }
                },
                visualTransformation = if (viewModel.isConfirmVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (viewModel.password.isNotEmpty() && 
                            viewModel.confirmPassword.isNotEmpty() &&
                            viewModel.passwordError == null &&
                            viewModel.confirmError == null) {
                            viewModel.setupMasterPassword(onSetupComplete)
                        }
                    }
                ),
                isError = viewModel.confirmError != null,
                supportingText = {
                    if (viewModel.confirmError != null) {
                        Text(viewModel.confirmError!!, color = MaterialTheme.colorScheme.error)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(confirmFocusRequester),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Error Message
            if (viewModel.errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = viewModel.errorMessage!!,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Setup Button
            Button(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.setupMasterPassword(onSetupComplete)
                },
                enabled = !viewModel.isLoading && 
                         viewModel.password.isNotEmpty() && 
                         viewModel.confirmPassword.isNotEmpty() &&
                         viewModel.passwordError == null &&
                         viewModel.confirmError == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                if (viewModel.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(Icons.Default.Check, "Setup", modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Setup Encrypted Storage", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Security Info
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        "Info",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Your password is stored securely using Android Keystore and never leaves your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordStrengthIndicator(strength: MasterPasswordViewModel.PasswordStrength) {
    val (color, label, progress) = when (strength) {
        MasterPasswordViewModel.PasswordStrength.NONE -> Triple(
            MaterialTheme.colorScheme.error,
            "Too Weak",
            0.2f
        )
        MasterPasswordViewModel.PasswordStrength.WEAK -> Triple(
            MaterialTheme.colorScheme.error,
            "Weak",
            0.4f
        )
        MasterPasswordViewModel.PasswordStrength.FAIR -> Triple(
            Color(0xFFFFA726),
            "Fair",
            0.6f
        )
        MasterPasswordViewModel.PasswordStrength.GOOD -> Triple(
            Color(0xFF66BB6A),
            "Good",
            0.8f
        )
        MasterPasswordViewModel.PasswordStrength.STRONG -> Triple(
            Color(0xFF4CAF50),
            "Strong",
            1.0f
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Password Strength:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
