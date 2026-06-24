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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import android.Manifest
import android.os.Build
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

class MainActivity : FragmentActivity() {
    override fun onResume() {
        super.onResume()
        AppState.isForeground = true
        NotificationHelper.dismissReplyNotification(this)
    }

    override fun onPause() {
        super.onPause()
        AppState.isForeground = false
    }

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
    var isRefreshing by remember { mutableStateOf(false) }

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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // PinPadOverlay will handle biometrics automatically when displayed


    Box(Modifier.fillMaxSize().background(Color.DarkGray)) {

        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    webViewRef.value = wv
                    wv.addJavascriptInterface(WebAppInterface(ctx), "Android")
                    AppState.mainWebView = wv
                    wv.layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    wv.visibility = android.view.View.VISIBLE
                    wv.configure(
                        onProgressChanged = { loadProgress = it },
                        onPageFinished = {
                            if (it != null && !it.contains("error")) loadError = null
                            
                            val injectionScript = """
                                (function() {
                                    if (window.__androidInjected) return;
                                    window.__androidInjected = true;
                                    
                                    var sendBtn = document.querySelector('.send-btn');
                                    if (!sendBtn) return;
                                    
                                    var isStreaming = false;
                                    var observer = new MutationObserver(function(mutations) {
                                        mutations.forEach(function(mutation) {
                                            if (mutation.attributeName === 'data-mode') {
                                                var mode = sendBtn.getAttribute('data-mode');
                                                if (mode === 'streaming' && !isStreaming) {
                                                    isStreaming = true;
                                                    if (window.Android) window.Android.startStream();
                                                } else if (!mode && isStreaming) {
                                                    isStreaming = false;
                                                    var msgs = document.querySelectorAll('.msg-assistant .body');
                                                    var lastMsgText = msgs.length > 0 ? msgs[msgs.length - 1].innerText : 'Message received';
                                                    if (window.Android) window.Android.endStream(lastMsgText);
                                                }
                                            }
                                        });
                                    });
                                    observer.observe(sendBtn, { attributes: true });
                                })();
                            """.trimIndent()
                            wv.evaluateJavascript(injectionScript, null)

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
            modifier = Modifier.fillMaxSize().navigationBarsPadding().imePadding()
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
            var pinMode by remember { mutableStateOf(if (CredentialStore.getAppPin(context) == null) PinMode.SETUP else PinMode.VERIFY) }
            PinPadOverlay(
                activity = activity,
                authError = authError,
                onUnlock = {
                    isLocked = false
                    CredentialStore.load(context)?.let { creds ->
                        webViewRef.value?.injectAutoLogin(creds.username, creds.password)
                    }
                },
                onSetPin = { pin ->
                    CredentialStore.saveAppPin(context, pin)
                    pinMode = PinMode.VERIFY
                },
                pinMode = pinMode
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            context = context,
            onDismiss = { showSettings = false }
        )
    }
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

// LockOverlay removed in favor of PinPadOverlay


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(context: android.content.Context, onDismiss: () -> Unit) {
    var username by remember { mutableStateOf(CredentialStore.load(context)?.username ?: "") }
    var password by remember { mutableStateOf(CredentialStore.load(context)?.password ?: "") }
    var showPw   by remember { mutableStateOf(false) }
    var saved    by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted -> }

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
            
            Divider(color = Color.White.copy(0.1f))
            
            Button(
                onClick = {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // On older versions, permission is granted at install time
                        android.widget.Toast.makeText(context, "Notifications are already enabled on this Android version.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1B2E))
            ) {
                Icon(Icons.Default.Notifications, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Enable Push Notifications")
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
