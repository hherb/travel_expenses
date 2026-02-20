package com.travelexpenses.android

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.fragment.app.FragmentActivity
import com.travelexpenses.android.auth.BiometricAvailability
import com.travelexpenses.android.auth.BiometricHelper
import com.travelexpenses.android.auth.LockScreen
import com.travelexpenses.android.auth.PinManager
import com.travelexpenses.android.navigation.AppNavigation
import com.travelexpenses.android.ui.settings.AutoLockTimeout
import com.travelexpenses.android.ui.theme.TravelExpensesTheme
import org.koin.android.ext.android.inject

class MainActivity : FragmentActivity() {

    private val pinManager: PinManager by inject()
    private val biometricHelper: BiometricHelper by inject()

    private var lastActiveTime: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TravelExpensesTheme {
                val securityEnabled = pinManager.isPinSet() || isBiometricEnabled()
                var isLocked by remember { mutableStateOf(securityEnabled) }

                if (isLocked && securityEnabled) {
                    val canBiometric = biometricHelper.canAuthenticate(this) == BiometricAvailability.AVAILABLE
                        && isBiometricEnabled()
                    LockScreen(
                        onPinVerified = { isLocked = false },
                        onBiometricRequest = {
                            biometricHelper.authenticate(
                                activity = this,
                                onSuccess = { isLocked = false },
                                onError = { /* User sees biometric error, can fall back to PIN */ },
                                onFallback = { /* Stay on PIN entry */ },
                            )
                        },
                        biometricAvailable = canBiometric,
                        pinManager = pinManager,
                    )
                } else {
                    AppNavigation()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        lastActiveTime = SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        if (lastActiveTime > 0L && shouldAutoLock()) {
            recreate()
        }
    }

    private fun shouldAutoLock(): Boolean {
        if (!pinManager.isPinSet() && !isBiometricEnabled()) return false
        val timeoutSeconds = getAutoLockTimeoutSeconds()
        if (timeoutSeconds == 0) return true // Immediate
        val elapsed = (SystemClock.elapsedRealtime() - lastActiveTime) / 1000
        return elapsed >= timeoutSeconds
    }

    private fun getAutoLockTimeoutSeconds(): Int {
        val prefs = getSharedPreferences("travel_expenses_security", Context.MODE_PRIVATE)
        val name = prefs.getString("auto_lock_timeout", AutoLockTimeout.IMMEDIATE.name)
        val timeout = AutoLockTimeout.entries.find { it.name == name } ?: AutoLockTimeout.IMMEDIATE
        return timeout.seconds
    }

    private fun isBiometricEnabled(): Boolean {
        val prefs = getSharedPreferences("travel_expenses_security", Context.MODE_PRIVATE)
        return prefs.getBoolean("biometric_enabled", false)
    }
}
