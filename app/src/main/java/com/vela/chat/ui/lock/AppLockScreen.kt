package com.vela.chat.ui.lock

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vela.chat.ui.theme.nova.NovaTokens
import com.vela.chat.util.AppLockController

/**
 * Full-screen lock shown when the app-lock feature engages: PIN entry plus
 * device-credential unlock (framework biometric prompt on API 28+, the
 * confirm-credential intent on API 26/27). No external dependencies.
 */
@Composable
fun AppLockScreen(controller: AppLockController) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    val confirmCredential = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) controller.unlock()
    }

    fun launchDeviceCredential() {
        val activity = context as? Activity ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val builder = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                    .setTitle("Unlock Vela")
                    .setSubtitle("Confirm to continue")
                // setAllowedAuthenticators exists only from API 30; API 28/29 use
                // the (now-deprecated) setDeviceCredentialAllowed.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    builder.setAllowedAuthenticators(
                        android.hardware.biometrics.BiometricManager.Authenticators.DEVICE_CREDENTIAL or
                            android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_WEAK,
                    )
                } else {
                    @Suppress("DEPRECATION")
                    builder.setDeviceCredentialAllowed(true)
                }
                builder.build().authenticate(
                    android.os.CancellationSignal(),
                    ContextCompat.getMainExecutor(activity),
                    object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) {
                            controller.unlock()
                        }

                        override fun onAuthenticationError(code: Int, errString: CharSequence) {
                            // User/system cancels are normal (lock stays); anything
                            // else falls back to the PIN pad with a hint.
                            if (code != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED &&
                                code != android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED
                            ) {
                                error = "Device unlock failed — use your PIN."
                            }
                        }
                    },
                )
            } else {
                controller.createConfirmCredentialIntent("Unlock Vela", "Confirm to continue")?.let {
                    confirmCredential.launch(it)
                }
            }
        } catch (e: Exception) {
            // Missing USE_BIOMETRIC, no enrolled authenticator, OEM quirks — the
            // PIN pad is always the fallback. The lock screen must never crash.
            error = "Device unlock unavailable — use your PIN."
        }
    }

    // Offer device credentials (biometric) automatically when the lock appears.
    LaunchedEffect(Unit) {
        if (controller.isDeviceSecure()) launchDeviceCredential()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(NovaTokens.Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(NovaTokens.Spacing.md))
        Text("Vela is locked", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(NovaTokens.Spacing.lg))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; error = null },
            label = { Text("PIN") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            isError = error != null,
            supportingText = { error?.let { Text(it) } },
            singleLine = true,
        )
        Spacer(Modifier.height(NovaTokens.Spacing.md))
        Button(
            onClick = {
                keyboard?.hide()
                if (controller.verifyPin(pin)) {
                    controller.unlock()
                } else {
                    error = "Incorrect PIN"
                }
            },
            enabled = pin.isNotBlank(),
        ) { Text("Unlock") }
        if (controller.isDeviceSecure()) {
            Spacer(Modifier.height(NovaTokens.Spacing.sm))
            OutlinedButton(onClick = { launchDeviceCredential() }) { Text("Use device credentials") }
        }
    }
}
