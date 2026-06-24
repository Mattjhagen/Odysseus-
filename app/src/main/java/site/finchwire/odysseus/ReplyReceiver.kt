package site.finchwire.odysseus

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.core.app.RemoteInput
import androidx.core.app.NotificationCompat

class ReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == NotificationHelper.REPLY_ACTION) {
            val remoteInput = RemoteInput.getResultsFromIntent(intent)
            val replyText = remoteInput?.getCharSequence(NotificationHelper.EXTRA_REPLY)?.toString()
            
            if (replyText != null) {
                // Update the notification to show "Sending..."
                val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val repliedNotification = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Odysseus")
                    .setContentText("Sending reply...")
                    .setAutoCancel(true)
                    .build()
                manager.notify(1001, repliedNotification)

                // Inject the reply into the WebView
                Handler(Looper.getMainLooper()).post {
                    AppState.mainWebView?.evaluateJavascript(
                        """
                        (function() {
                            var input = document.getElementById('message');
                            if (input) {
                                input.value = '${replyText.replace("'", "\\'")}';
                                input.dispatchEvent(new Event('input', { bubbles: true }));
                                var btn = document.querySelector('.send-btn');
                                if (btn) {
                                    btn.click();
                                }
                            }
                        })();
                        """.trimIndent(), null
                    )
                }

                // Start the background service again since we just started a stream
                val serviceIntent = Intent(context, ChatBackgroundService::class.java).apply {
                    action = ChatBackgroundService.ACTION_START
                }
                context.startForegroundService(serviceIntent)

                // Dismiss notification shortly after
                Handler(Looper.getMainLooper()).postDelayed({
                    manager.cancel(1001)
                }, 1000)
            }
        }
    }
}
