package com.simplemobiletools.musicplayer.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity

/** Helper class to manage all-things-notification. */
@SuppressLint("NewApi")
class NotificationHelper(private val context: Context) {

    private var notificationManager = context.notificationManager

    fun createNoPermissionNotification(): Notification {
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(context.getString(com.simplemobiletools.commons.R.string.no_storage_permissions))
            .setSmallIcon(R.drawable.ic_headset_small)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(getContentIntent())
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .build()
    }

    fun createMediaScannerNotification(contentText: String, progress: Int, max: Int): Notification {
        val title = context.getString(com.simplemobiletools.commons.R.string.scanning)
        return NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setSmallIcon(R.drawable.ic_headset_small)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setContentIntent(getContentIntent())
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setProgress(max, progress, progress == 0)
            .apply {
                if (contentText.isNotEmpty()) {
                    setContentText(contentText)
                }
            }.build()
    }

    fun notify(id: Int, notification: Notification) = notificationManager.notify(id, notification)

    fun cancel(id: Int) = notificationManager.cancel(id)

    private fun getContentIntent(): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 0, contentIntent, FLAG_IMMUTABLE)
    }

    companion object {
        const val NOTIFICATION_CHANNEL = "music_player_channel"
        const val NOTIFICATION_ID = 42

        @RequiresApi(26)
        private fun createNotificationChannel(context: Context, notificationManager: NotificationManager) {
            var notificationChannel: NotificationChannel? = notificationManager
                .getNotificationChannel(NOTIFICATION_CHANNEL)
            if (notificationChannel == null) {
                notificationChannel = NotificationChannel(
                    NOTIFICATION_CHANNEL,
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                )
                notificationChannel.enableLights(false)
                notificationChannel.enableVibration(false)
                notificationChannel.setShowBadge(false)

                notificationManager.createNotificationChannel(notificationChannel)
            }
        }

        fun createInstance(context: Context): NotificationHelper {
            if (isOreoPlus()) {
                createNotificationChannel(context, context.notificationManager)
            }
            return NotificationHelper(context)
        }
    }
}
