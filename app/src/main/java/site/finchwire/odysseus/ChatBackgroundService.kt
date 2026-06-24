package site.finchwire.odysseus

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class ChatBackgroundService : Service() {

    companion object {
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                NotificationHelper.createChannels(this)
                val notification = NotificationCompat.Builder(this, NotificationHelper.BG_CHANNEL_ID)
                    .setContentTitle("Odysseus is thinking...")
                    .setContentText("Keeping connection alive in background.")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .build()
                startForeground(2001, notification)
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
