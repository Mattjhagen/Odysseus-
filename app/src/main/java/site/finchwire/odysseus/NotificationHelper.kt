package site.finchwire.odysseus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput

object NotificationHelper {
    const val CHANNEL_ID = "odysseus_chat"
    const val BG_CHANNEL_ID = "odysseus_bg"
    const val REPLY_ACTION = "site.finchwire.odysseus.REPLY"
    const val EXTRA_REPLY = "extra_reply"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val chatChannel = NotificationChannel(
                CHANNEL_ID,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(chatChannel)

            val bgChannel = NotificationChannel(
                BG_CHANNEL_ID,
                "Background Execution",
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(bgChannel)
        }
    }

    fun showReplyNotification(context: Context, message: String) {
        val replyLabel = "Reply"
        val remoteInput = RemoteInput.Builder(EXTRA_REPLY)
            .setLabel(replyLabel)
            .build()

        val replyIntent = Intent(context, ReplyReceiver::class.java).apply {
            action = REPLY_ACTION
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val action = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            replyLabel,
            replyPendingIntent
        ).addRemoteInput(remoteInput).build()

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            1,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Odysseus")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .addAction(action)
            .setContentIntent(contentPendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, notification)
    }

    fun dismissReplyNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(1001)
    }
}
