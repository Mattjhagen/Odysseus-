package site.finchwire.odysseus

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

/**
 * Compact activity for the Galaxy Z Flip cover screen.
 *
 * The outer display on Z Flip 5/6/7 is ~3.4" and roughly square (≈360×400dp).
 * Three "pages" swiped vertically:
 *   0 — Lock screen  (biometric unlock)
 *   1 — Status card  (server + quick actions)
 *   2 — Mini WebView (scaled Odysseus UI)
 */
class CoverActivity : FragmentActivity() {
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
        setContent { OdysseusTheme { CoverScreen(activity = this) } }
    }
}

private enum class CoverPage { LOCK, STATUS, WEB }

@Composable
fun CoverScreen(activity: FragmentActivity) {
    val context  = LocalContext.current
    val scope    = rememberCoroutineScope()

    var page         by remember { mutableStateOf(CoverPage.LOCK) }
    var authError    by remember { mutableStateOf<String?>(null) }
    var serverUrl    by remember { mutableStateOf("https://finchwire.site") }
    var isLocal      by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    val webViewRef   = remember { mutableStateOf<WebView?>(null) }

    LaunchedEffect(Unit) {
        activity.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            ServerRouter.urlFlow(context).collect { (url, local) ->
                serverUrl = url; isLocal = local
                webViewRef.value?.loadUrl(url)
            }
        }
    }

    val fileChooserLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val intent = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && intent != null) {
            val results = android.webkit.WebChromeClient.FileChooserParams.parseResult(result.resultCode, intent)
            AppState.fileChooserCallback?.onReceiveValue(results)
        } else {
            AppState.fileChooserCallback?.onReceiveValue(null)
        }
        AppState.fileChooserCallback = null
    }

    fun authenticate() {
        authError = null
        BiometricHelper.prompt(
            activity = activity,
            onSuccess = {
                page = CoverPage.STATUS
                CredentialStore.load(context)?.let { creds ->
                    webViewRef.value?.injectAutoLogin(creds.username, creds.password)
                }
            },
            onFailure = { authError = it }
        )
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
            }
        }
    }

    // Vertical swipe to change page (when unlocked)
    val swipeModifier = if (page != CoverPage.LOCK) {
        Modifier.pointerInput(page) {
            detectVerticalDragGestures { _, delta ->
                if (delta < -40 && page == CoverPage.STATUS) page = CoverPage.WEB
                if (delta >  40 && page == CoverPage.WEB)    page = CoverPage.STATUS
                if (delta >  80 && page == CoverPage.STATUS) page = CoverPage.LOCK
            }
        }
    } else Modifier

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(swipeModifier)
    ) {
        // WebView runs in background always so it's warm
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
                    wv.configure(
                        onProgressChanged = { loadProgress = it },
                        onShowFileChooser = { intent, callback ->
                            AppState.fileChooserCallback?.onReceiveValue(null)
                            AppState.fileChooserCallback = callback
                            fileChooserLauncher.launch(intent)
                        },
                        onPageFinished = {
                            
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

                            if (page != CoverPage.LOCK) {
                                CredentialStore.load(ctx)?.let { c ->
                                    wv.injectAutoLogin(c.username, c.password)
                                }
                            }
                        },
                        onReceivedError = { /* ignore for cover screen */ }
                    )
                    wv.loadUrl(serverUrl)
                    // Scale down the web content to fit the cover screen
                    wv.settings.textZoom = 75
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .alpha(if (page == CoverPage.WEB) 1f else 0f)
        )

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val entering = targetState.ordinal > initialState.ordinal
                if (entering) slideInVertically { it } + fadeIn() togetherWith
                              slideOutVertically { -it } + fadeOut()
                else          slideInVertically { -it } + fadeIn() togetherWith
                              slideOutVertically { it } + fadeOut()
            }
        ) { current ->
            when (current) {
                CoverPage.LOCK   -> CoverLockPage(authError) { authenticate() }
                CoverPage.STATUS -> CoverStatusPage(
                    isLocal = isLocal,
                    serverUrl = serverUrl,
                    onLock = { page = CoverPage.LOCK },
                    onOpenFull = {
                        context.startActivity(Intent(context, MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    onSwipeHint = { page = CoverPage.WEB }
                )
                CoverPage.WEB    -> CoverWebOverlay(
                    progress = loadProgress,
                    onBack = { page = CoverPage.STATUS }
                )
            }
        }

        // Page dots
        if (page != CoverPage.LOCK) {
            Column(
                Modifier.align(Alignment.CenterEnd).padding(end = 6.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                listOf(CoverPage.STATUS, CoverPage.WEB).forEach { p ->
                    Box(
                        Modifier
                            .size(if (p == page) 7.dp else 4.dp)
                            .clip(CircleShape)
                            .background(if (p == page) Color.White else Color.White.copy(0.25f))
                    )
                }
            }
        }
    }

    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    val lifecycleState by lifecycleOwner.lifecycle.currentStateFlow.collectAsState()
    var hasPromptedBiometrics by remember { mutableStateOf(false) }

    LaunchedEffect(lifecycleState, page) {
        if (lifecycleState == Lifecycle.State.RESUMED && page == CoverPage.LOCK && !hasPromptedBiometrics) {
            hasPromptedBiometrics = true
            authenticate()
        }
    }
}

@Composable
private fun CoverLockPage(authError: String?, onUnlock: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Indigo orb
            Box(
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(listOf(Color(0xFFA78BFA), Color(0xFF4C1D95)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("O", color = Color.White, fontSize = 36.sp,
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
            }

            Text("Odysseus", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Light,
                fontFamily = FontFamily.Serif)

            authError?.let {
                Text(it, color = Color(0xFFEF5350), fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    textAlign = TextAlign.Center)
            }

            Button(
                onClick = onUnlock,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6C3EF4)),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp)
            ) {
                Icon(Icons.Default.Fingerprint, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Unlock", fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun CoverStatusPage(
    isLocal: Boolean,
    serverUrl: String,
    onLock: () -> Unit,
    onOpenFull: () -> Unit,
    onSwipeHint: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0812))
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Connection card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1A1430),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            Modifier.size(8.dp).clip(CircleShape)
                                .background(if (isLocal) Color(0xFF4CAF50) else Color(0xFF5B86E5))
                        )
                        Text(
                            if (isLocal) "Connected locally" else "Connected remotely",
                            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        serverUrl.removePrefix("https://").removePrefix("http://"),
                        color = Color.White.copy(0.4f), fontSize = 11.sp
                    )
                }
            }

            // Action buttons
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CoverIconButton(Icons.Default.OpenInFull, "Full screen", onClick = onOpenFull)
                CoverIconButton(Icons.Default.Web,        "Mini browser", onClick = onSwipeHint)
                CoverIconButton(Icons.Default.Lock,       "Lock",         onClick = onLock)
            }
        }

        // Swipe hint
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.KeyboardArrowUp, null,
                tint = Color.White.copy(0.25f), modifier = Modifier.size(16.dp))
            Text("swipe for browser", color = Color.White.copy(0.2f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun CoverWebOverlay(progress: Int, onBack: () -> Unit) {
    // Minimal chrome over the WebView for the cover screen
    Box(Modifier.fillMaxSize()) {
        // Semi-transparent top bar
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(0.55f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, null,
                    tint = Color.White.copy(0.7f), modifier = Modifier.size(18.dp))
            }
            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.weight(1f).height(2.dp),
                    color = Color(0xFF6C3EF4),
                    trackColor = Color.Transparent
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            Text("Odysseus", color = Color.White.copy(0.5f), fontSize = 10.sp,
                modifier = Modifier.padding(end = 4.dp))
        }
    }
}

@Composable
private fun CoverIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = Modifier.size(48.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color(0xFF1A1430),
                contentColor = Color(0xFF9D71F8)
            )
        ) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
        }
        Text(label, color = Color.White.copy(0.4f), fontSize = 9.sp)
    }
}

@Suppress("NOTHING_TO_INLINE")
private inline fun Modifier.alpha(value: Float): Modifier =
    this.graphicsLayer { alpha = value }
