package site.finchwire.odysseus

import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { OdysseusTheme { MainScreen(activity = this) } }
    }
}

@Composable
fun MainScreen(activity: FragmentActivity) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var isLocked     by remember { mutableStateOf(true) }
    var authError    by remember { mutableStateOf<String?>(null) }
    var serverUrl    by remember { mutableStateOf("https://finchwire.site") }
    var isLocal      by remember { mutableStateOf(false) }
    var isAutoNavEnabled by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableStateOf(0) }
    var loadError    by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }

    val webViewRef = remember { mutableStateOf<WebView?>(null) }

    // Collect server URL changes
    LaunchedEffect(Unit) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ServerRouter.urlFlow(context).collect { (url, local) ->
                if (isAutoNavEnabled && url != serverUrl) {
                    serverUrl = url
                    isLocal = local
                    loadError = null
                    webViewRef.value?.loadUrl(url)
                }
            }
        }
    }

    fun authenticate() {
        authError = null
        BiometricHelper.prompt(
            activity = activity,
            onSuccess = {
                isLocked = false
                CredentialStore.load(context)?.let { creds ->
                    webViewRef.value?.injectAutoLogin(creds.username, creds.password)
                }
            },
            onFailure = { authError = it }
        )
    }

    Box(Modifier.fillMaxSize().background(Color.DarkGray)) {

        // WebView layer
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    webViewRef.value = wv
                    wv.visibility = android.view.View.VISIBLE
                    wv.configure(
                        onProgressChanged = { loadProgress = it },
                        onPageFinished = {
                            if (it != null && !it.contains("error")) loadError = null
                            if (!isLocked) {
                                CredentialStore.load(ctx)?.let { creds ->
                                    wv.injectAutoLogin(creds.username, creds.password)
                                }
                            }
                        },
                        onReceivedError = { loadError = it }
                    )
                    wv.loadUrl(serverUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Progress bar
        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                color = Color(0xFF6C3EF4),
                trackColor = Color.Transparent
            )
        }

        // Error message
        loadError?.let { error ->
            Column(
                Modifier.fillMaxSize().padding(32.dp).background(Color.Black.copy(0.85f)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.CloudOff, null, tint = Color.White.copy(0.3f), modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Connection Failed", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                Text(error, color = Color.White.copy(0.5f), fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = { loadError = null; webViewRef.value?.reload() }) {
                    Text("Retry")
                }
                if (isLocal) {
                    TextButton(onClick = {
                        isAutoNavEnabled = false
                        isLocal = false
                        serverUrl = "https://finchwire.site"
                        loadError = null
                        webViewRef.value?.loadUrl(serverUrl)
                    }) {
                        Text("Switch to Remote", color = Color(0xFF9D71F8))
                    }
                }
            }
        }

        // Top bar
        if (!isLocked) {
            TopBar(
                isLocal = isLocal,
                onLock = { isLocked = true },
                onReload = { webViewRef.value?.reload() },
                onSettings = { showSettings = true },
                onSwitchNetwork = {
                    isAutoNavEnabled = false
                    isLocal = !isLocal
                    serverUrl = if (isLocal) "http://192.168.1.73:7000" else "https://finchwire.site"
                    loadError = null
                    webViewRef.value?.loadUrl(serverUrl)
                },
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }

        // Lock overlay
        AnimatedVisibility(
            visible = isLocked,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            LockOverlay(
                authError = authError,
                onUnlock = { authenticate() }
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            context = context,
            onDismiss = { showSettings = false }
        )
    }

    // Auto-prompt on first launch
    LaunchedEffect(Unit) { authenticate() }
}

@Composable
private fun TopBar(
    isLocal: Boolean,
    onLock: () -> Unit,
    onReload: () -> Unit,
    onSettings: () -> Unit,
    onSwitchNetwork: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Server badge
        Surface(
            onClick = onSwitchNetwork,
            color = Color.Transparent,
            shape = CircleShape
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(Modifier.size(7.dp).background(if (isLocal) Color(0xFF4CAF50) else Color(0xFF5B86E5), CircleShape))
                Text(if (isLocal) "Local" else "Remote", color = Color.White.copy(0.5f), fontSize = 11.sp)
            }
        }
        Row {
            IconButton(onClick = onReload) { Icon(Icons.Default.Refresh, null, tint = Color.White.copy(0.7f)) }
            IconButton(onClick = onLock) { Icon(Icons.Default.Lock, null, tint = Color.White.copy(0.7f)) }
            IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, null, tint = Color.White.copy(0.7f)) }
        }
    }
}

@Composable
private fun LockOverlay(authError: String?, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            Text("Odysseus", color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Thin,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif)
            Text("finchwire.site", color = Color.White.copy(0.35f), fontSize = 12.sp)

            Spacer(Modifier.height(8.dp))

            authError?.let {
                Text(it, color = Color(0xFFEF5350), fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 40.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }

            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = CircleShape,
                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Unlock", color = Color.Black, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(context: android.content.Context, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf(CredentialStore.load(context)?.username ?: "") }
    var password by remember { mutableStateOf(CredentialStore.load(context)?.password ?: "") }
    var showPw   by remember { mutableStateOf(false) }
    var saved    by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0F0C1A)
    ) {
        Column(Modifier.padding(24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Auto-login credentials", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            Text("Stored in Android Keystore. Injected after biometric auth.",
                color = Color.White.copy(0.45f), fontSize = 13.sp)

            OutlinedTextField(
                value = username, onValueChange = { username = it },
                label = { Text("Username or email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldDarkColors()
            )
            OutlinedTextField(
                value = password, onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (showPw) androidx.compose.ui.text.input.VisualTransformation.None
                                       else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showPw = !showPw }) {
                        Icon(if (showPw) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            null, tint = Color.White.copy(0.5f))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = outlinedTextFieldDarkColors()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        CredentialStore.save(context, Credentials(username, password))
                        saved = true
                    },
                    enabled = username.isNotBlank() && password.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C3EF4))
                ) {
                    Icon(if (saved) Icons.Default.Check else Icons.Default.Save,
                        null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (saved) "Saved!" else "Save")
                }
                if (CredentialStore.load(context) != null) {
                    OutlinedButton(
                        onClick = { CredentialStore.clear(context); username = ""; password = "" },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF5350))
                    ) { Text("Remove") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun outlinedTextFieldDarkColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor    = Color.White,
    unfocusedTextColor  = Color.White,
    focusedBorderColor  = Color(0xFF6C3EF4),
    unfocusedBorderColor= Color.White.copy(0.25f),
    focusedLabelColor   = Color(0xFF9D71F8),
    unfocusedLabelColor = Color.White.copy(0.45f),
    cursorColor         = Color(0xFF6C3EF4)
)

@Composable
fun OdysseusTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary   = Color(0xFF6C3EF4),
            background= Color.Black,
            surface   = Color(0xFF0F0C1A)
        ),
        content = content
    )
}
