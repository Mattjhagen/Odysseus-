package site.finchwire.odysseus

import android.content.Context
import android.content.Intent
import android.webkit.JavascriptInterface

class WebAppInterface(private val context: Context) {
    
    @JavascriptInterface
    fun startStream() {
        val intent = Intent(context, ChatBackgroundService::class.java).apply {
            action = ChatBackgroundService.ACTION_START
        }
        context.startForegroundService(intent)
    }

    @JavascriptInterface
    fun endStream(responseSnippet: String) {
        val intent = Intent(context, ChatBackgroundService::class.java).apply {
            action = ChatBackgroundService.ACTION_STOP
        }
        context.startService(intent)
        
        // Show notification if app is in background
        if (!AppState.isForeground) {
            NotificationHelper.showReplyNotification(context, responseSnippet)
        }
    }
}
