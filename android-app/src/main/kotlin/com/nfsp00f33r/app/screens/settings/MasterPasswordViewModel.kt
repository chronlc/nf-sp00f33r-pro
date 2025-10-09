package com.nfsp00f33r.app.screens.settings

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfsp00f33r.app.application.NfSp00fApplication
import com.nfsp00f33r.app.storage.SecureMasterPasswordManager
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * MasterPasswordViewModel - Phase 1A Day 4
 * Updated Phase 2B Day 2: Use SecureMasterPasswordModule
 * 
 * Manages master password setup and validation for encrypted card storage.
 * Handles first-launch flow and password change operations.
 */
class MasterPasswordViewModel : ViewModel() {

    // Phase 2B Day 2: Use module system instead of direct manager
    private val passwordModule by lazy {
        NfSp00fApplication.getPasswordModule()
    }

    // UI State
    var password by mutableStateOf("")
        private set

    var confirmPassword by mutableStateOf("")
        private set

    var passwordError by mutableStateOf<String?>(null)
        private set

    var confirmError by mutableStateOf<String?>(null)
        private set

    var isPasswordVisible by mutableStateOf(false)
        private set

    var isConfirmVisible by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var setupComplete by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Password strength indicator
    var passwordStrength by mutableStateOf<PasswordStrength>(PasswordStrength.NONE)
        private set

    enum class PasswordStrength {
        NONE,
        WEAK,
        FAIR,
        GOOD,
        STRONG
    }

    /**
     * Update password field
     */
    fun updatePassword(newPassword: String) {
        password = newPassword
        passwordError = null
        errorMessage = null
        updatePasswordStrength(newPassword)
        validatePasswordMatch()
    }

    /**
     * Update confirm password field
     */
    fun updateConfirmPassword(newConfirm: String) {
        confirmPassword = newConfirm
        confirmError = null
        errorMessage = null
        validatePasswordMatch()
    }

    /**
     * Toggle password visibility
     */
    fun togglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible
    }

    /**
     * Toggle confirm password visibility
     */
    fun toggleConfirmVisibility() {
        isConfirmVisible = !isConfirmVisible
    }

    /**
     * Validate password match
     */
    private fun validatePasswordMatch() {
        if (confirmPassword.isNotEmpty() && password != confirmPassword) {
            confirmError = "Passwords do not match"
        } else {
            confirmError = null
        }
    }

    /**
     * Update password strength indicator
     */
    private fun updatePasswordStrength(pwd: String) {
        passwordStrength = when {
            pwd.length < 8 -> PasswordStrength.NONE
            pwd.length < 10 -> PasswordStrength.WEAK
            pwd.length < 12 && hasDigitAndLetter(pwd) -> PasswordStrength.FAIR
            pwd.length >= 12 && hasDigitAndLetter(pwd) && hasSpecialChar(pwd) -> PasswordStrength.GOOD
            pwd.length >= 16 && hasDigitAndLetter(pwd) && hasSpecialChar(pwd) && hasMixedCase(pwd) -> PasswordStrength.STRONG
            else -> PasswordStrength.FAIR
        }
    }

    private fun hasDigitAndLetter(pwd: String): Boolean {
        return pwd.any { it.isDigit() } && pwd.any { it.isLetter() }
    }

    private fun hasSpecialChar(pwd: String): Boolean {
        return pwd.any { !it.isLetterOrDigit() }
    }

    private fun hasMixedCase(pwd: String): Boolean {
        return pwd.any { it.isUpperCase() } && pwd.any { it.isLowerCase() }
    }

    /**
     * Setup master password (first launch)
     */
    fun setupMasterPassword(onSuccess: () -> Unit) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null

            try {
                // Validate password - Phase 2B Day 2: Use module
                val validation = passwordModule.validatePasswordStrength(password)
                if (!validation.isValid) {
                    passwordError = validation.message
                    isLoading = false
                    return@launch
                }

                // Validate confirmation
                if (password != confirmPassword) {
                    confirmError = "Passwords do not match"
                    isLoading = false
                    return@launch
                }

                // Set password - Phase 2B Day 2: Use module
                val success = passwordModule.setMasterPassword(password)
                if (success) {
                    Timber.i("Master password set successfully")
                    setupComplete = true
                    onSuccess()
                } else {
                    errorMessage = "Failed to set master password. Please try again."
                }
            } catch (e: Exception) {
                Timber.e(e, "Error setting master password")
                errorMessage = "Error: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * Skip password setup (use temporary password)
     * NOT RECOMMENDED for production use
     */
    fun skipPasswordSetup(onSkip: () -> Unit) {
        Timber.w("User skipped master password setup - using temporary password")
        setupComplete = true
        onSkip()
    }
}
