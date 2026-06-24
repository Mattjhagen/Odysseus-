package site.finchwire.odysseus

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle

@Composable
fun PinPadOverlay(
    activity: FragmentActivity,
    authError: String?,
    onUnlock: () -> Unit,
    onSetPin: (String) -> Unit,
    pinMode: PinMode = PinMode.VERIFY
) {
    val context = LocalContext.current
    var enteredPin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var hasPromptedBiometrics by remember { mutableStateOf(false) }

    // Auto-prompt biometrics when in VERIFY mode and activity is resumed
    LaunchedEffect(lifecycleState, pinMode) {
        if (lifecycleState == Lifecycle.State.RESUMED && pinMode == PinMode.VERIFY && !hasPromptedBiometrics) {
            hasPromptedBiometrics = true
            if (BiometricHelper.canAuthenticate(activity)) {
                BiometricHelper.prompt(
                    activity = activity,
                    onSuccess = { onUnlock() },
                    onFailure = { localError = it }
                )
            }
        }
    }

    val displayError = localError ?: authError

    Box(Modifier.fillMaxSize().background(Color(0xFF0A0812))) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (pinMode == PinMode.SETUP) "Set App PIN" else "Enter PIN",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium
                )
                if (displayError != null) {
                    Text(displayError, color = Color(0xFFEF5350), fontSize = 14.sp)
                }
            }

            // PIN Dots
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (i in 0 until 4) {
                    val isFilled = i < enteredPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) Color(0xFF6C3EF4) else Color.Transparent)
                            .border(1.5.dp, if (isFilled) Color(0xFF6C3EF4) else Color.White.copy(0.3f), CircleShape)
                    )
                }
            }

            // Keypad
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                for (row in 0 until 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        for (col in 0 until 3) {
                            val digit = row * 3 + col + 1
                            PinButton(text = digit.toString()) {
                                if (enteredPin.length < 4) {
                                    enteredPin += digit
                                    localError = null
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    // Fingerprint / Biometric button
                    if (pinMode == PinMode.VERIFY && BiometricHelper.canAuthenticate(activity)) {
                        PinIconButton(icon = Icons.Default.Fingerprint) {
                            BiometricHelper.prompt(
                                activity = activity,
                                onSuccess = { onUnlock() },
                                onFailure = { localError = it }
                            )
                        }
                    } else {
                        Spacer(Modifier.size(72.dp))
                    }

                    PinButton(text = "0") {
                        if (enteredPin.length < 4) {
                            enteredPin += "0"
                            localError = null
                        }
                    }

                    // Backspace button
                    PinIconButton(icon = Icons.Default.Backspace) {
                        if (enteredPin.isNotEmpty()) {
                            enteredPin = enteredPin.dropLast(1)
                            localError = null
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(enteredPin) {
        if (enteredPin.length == 4) {
            if (pinMode == PinMode.SETUP) {
                onSetPin(enteredPin)
                enteredPin = ""
            } else {
                val storedPin = CredentialStore.getAppPin(context)
                if (storedPin == enteredPin || storedPin == null) {
                    onUnlock()
                } else {
                    localError = "Incorrect PIN"
                    enteredPin = ""
                }
            }
        }
    }
}

enum class PinMode { VERIFY, SETUP }

@Composable
private fun PinButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color.White.copy(0.05f))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun PinIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White.copy(0.7f), modifier = Modifier.size(28.dp))
    }
}
