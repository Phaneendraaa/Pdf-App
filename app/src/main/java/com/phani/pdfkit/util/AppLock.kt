package com.phani.pdfkit.util

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object AppLock {
    private val authenticators: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    /** True when the phone has a fingerprint/face or screen lock Pdf Kit can use. */
    fun available(context: Context): Boolean =
        BiometricManager.from(context).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS

    fun prompt(activity: FragmentActivity, onUnlocked: () -> Unit) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onUnlocked()
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Pdf Kit")
            .setSubtitle("Your files are protected")
            .apply {
                if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(authenticators)
                else setNegativeButtonText("Cancel")
            }
            .build()
        prompt.authenticate(info)
    }
}
