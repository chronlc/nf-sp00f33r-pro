package com.nfsp00f33r.app.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.security.KeyStore

/**
 * SecureMasterPasswordManager - Phase 1A Day 4
 * 
 * Manages secure storage and retrieval of the master password for CardDataStore encryption.
 * Uses Android's EncryptedSharedPreferences backed by Android Keystore.
 * 
 * Security Features:
 * - Master password encrypted with AES-256-GCM
 * - Key stored in Android Keystore (hardware-backed if available)
 * - Protection against root access and app data extraction
 * - Automatic key generation on first use
 * 
 * Note: This is production-grade security for Android app storage.
 * The master password itself is used to derive the CardDataStore encryption key.
 */
class SecureMasterPasswordManager(private val context: Context) {

    private val TAG = "SecureMasterPasswordManager"
    
    companion object {
        private const val ENCRYPTED_PREFS_FILE = "nf_sp00f33r_secure_prefs"
        private const val KEY_MASTER_PASSWORD = "master_password"
        private const val KEY_PASSWORD_SET = "password_set"
        private const val KEY_FIRST_LAUNCH = "first_launch"
    }

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .setKeyGenParameterSpec(
                KeyGenParameterSpec.Builder(
                    MasterKey.DEFAULT_MASTER_KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            .build()
    }

    private val encryptedPrefs: SharedPreferences by lazy {
        EncryptedSharedPreferences.create(
            context,
            ENCRYPTED_PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    /**
     * Check if this is the first app launch (no password set yet)
     */
    fun isFirstLaunch(): Boolean {
        return try {
            encryptedPrefs.getBoolean(KEY_FIRST_LAUNCH, true)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking first launch status", e)
            true // Default to first launch if there's an error
        }
    }

    /**
     * Check if master password is set
     */
    fun isPasswordSet(): Boolean {
        return try {
            encryptedPrefs.getBoolean(KEY_PASSWORD_SET, false)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking password set status", e)
            false
        }
    }

    /**
     * Set the master password (encrypted storage)
     * Returns true if successful, false otherwise
     */
    fun setMasterPassword(password: String): Boolean {
        return try {
            if (password.length < 8) {
                Log.w(TAG, "Password too short (minimum 8 characters)")
                return false
            }

            encryptedPrefs.edit()
                .putString(KEY_MASTER_PASSWORD, password)
                .putBoolean(KEY_PASSWORD_SET, true)
                .putBoolean(KEY_FIRST_LAUNCH, false)
                .apply()

            Log.i(TAG, "Master password set successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set master password", e)
            false
        }
    }

    /**
     * Get the master password (decrypted)
     * Returns null if not set or error occurs
     */
    fun getMasterPassword(): String? {
        return try {
            encryptedPrefs.getString(KEY_MASTER_PASSWORD, null)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to retrieve master password", e)
            null
        }
    }

    /**
     * Verify if provided password matches stored password
     */
    fun verifyPassword(password: String): Boolean {
        val storedPassword = getMasterPassword()
        return storedPassword != null && storedPassword == password
    }

    /**
     * Change the master password
     * Returns true if successful, false otherwise
     */
    fun changeMasterPassword(oldPassword: String, newPassword: String): Boolean {
        return try {
            if (!verifyPassword(oldPassword)) {
                Log.w(TAG, "Old password verification failed")
                return false
            }

            if (newPassword.length < 8) {
                Log.w(TAG, "New password too short (minimum 8 characters)")
                return false
            }

            setMasterPassword(newPassword)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to change master password", e)
            false
        }
    }

    /**
     * Clear all stored password data (use with caution!)
     * This will require re-encryption of all card data
     */
    fun clearPassword(): Boolean {
        return try {
            encryptedPrefs.edit()
                .remove(KEY_MASTER_PASSWORD)
                .putBoolean(KEY_PASSWORD_SET, false)
                .putBoolean(KEY_FIRST_LAUNCH, true)
                .apply()

            Log.w(TAG, "Master password cleared - all encrypted data will be inaccessible!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear master password", e)
            false
        }
    }

    /**
     * Validate password strength
     * Returns validation result with error message if invalid
     */
    fun validatePasswordStrength(password: String): PasswordValidation {
        return when {
            password.length < 8 -> PasswordValidation(
                isValid = false,
                message = "Password must be at least 8 characters"
            )
            password.length > 128 -> PasswordValidation(
                isValid = false,
                message = "Password too long (maximum 128 characters)"
            )
            !password.any { it.isDigit() } -> PasswordValidation(
                isValid = false,
                message = "Password must contain at least one digit"
            )
            !password.any { it.isLetter() } -> PasswordValidation(
                isValid = false,
                message = "Password must contain at least one letter"
            )
            password.all { it.isLetterOrDigit() } -> PasswordValidation(
                isValid = false,
                message = "Password should contain at least one special character"
            )
            else -> PasswordValidation(
                isValid = true,
                message = "Password strength: Good"
            )
        }
    }

    /**
     * Password validation result
     */
    data class PasswordValidation(
        val isValid: Boolean,
        val message: String
    )
}
