package com.simplemobiletools.musicplayer.helpers

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.support.v4.media.session.MediaSessionCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simplemobiletools.commons.extensions.notificationManager
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.MainActivity
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.receivers.ControlActionsListener
import com.simplemobiletools.musicplayer.receivers.NotificationDismissedReceiver
import com.simplemobiletools.musicplayer.services.MusicService

/** Helper class to manage all-things-notification. */
@SuppressLint("NewApi")
class NotificationHelper(private val context: Context, private val mediaSessionToken: MediaSessionCompat.Token) {

    private var notificationManager = context.notificationManager

    fun createPlayerNotification(track: Track?, isPlaying: Boolean, largeIcon: Bitmap?, callback: (Notification) -> Unit) {
        val title = track?.title.orEmpty()
        val artist = track?.artist.orEmpty()

        var postTime = 0L
        var showWhen = false
        var usesChronometer = false
        var ongoing = false
        if (isPlaying) {
            val position = MusicService.mPlayer!!.position()
            postTime = System.currentTimeMillis() - maxOf(position, 0)
            showWhen = true
            usesChronometer = true
            ongoing = true
        }

        val notificationDismissedIntent = Intent(context, NotificationDismissedReceiver::class.java).apply {
            action = NOTIFICATION_DISMISSED
        }

        val flags = PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val notificationDismissedPendingIntent = PendingIntent.getBroadcast(context, 0, notificationDismissedIntent, flags)

        val previousAction = NotificationCompat.Action.Builder(
            R.drawable.ic_previous_vector,
            context.getString(R.string.previous),
            getIntent(PREVIOUS)
        ).build()

        val nextAction = NotificationCompat.Action.Builder(
            R.drawable.ic_next_vector,
            context.getString(R.string.next),
            getIntent(NEXT)
        ).build()

        val playPauseIcon = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        val playPauseAction = NotificationCompat.Action.Builder(
            playPauseIcon,
            context.getString(R.string.playpause),
            getIntent(PLAYPAUSE)
        ).build()

        val dismissAction = NotificationCompat.Action.Builder(
            R.drawable.ic_cross_vector,
            context.getString(R.string.dismiss),
            getIntent(DISMISS)
        ).build()

        val builder = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL)
            .setContentTitle(title)
            .setContentText(artist)
            .setSmallIcon(R.drawable.ic_headset_small)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setWhen(postTime)
            .setShowWhen(showWhen)
            .setUsesChronometer(usesChronometer)
            .setContentIntent(getContentIntent())
            .setOngoing(ongoing)
            .setChannelId(NOTIFICATION_CHANNEL)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setShowActionsInCompactView(0, 1, 2)
                    .setMediaSession(mediaSessionToken)
            )
            .setDeleteIntent(notificationDismissedPendingIntent)
            .addAction(previousAction)
            .addAction(playPauseAction)
            .addAction(nextAction)
            .addAction(dismissAction)

        try {
            builder.setLargeIcon(largeIcon)
        } catch (ignored: OutOfMemoryError) {
        }

        callback(builder.build())
    }

    fun notify(id: Int, notification: Notification) = notificationManager.notify(id, notification)

    fun cancel(id: Int) = notificationManager.cancel(id)

    private fun getContentIntent(): PendingIntent {
        val contentIntent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(context, 0, contentIntent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun getIntent(action: String): PendingIntent {
        val intent = Intent(context, ControlActionsListener::class.java)
        intent.action = action
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        private const val NOTIFICATION_CHANNEL = "music_player_channel"
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

        fun createInstance(context: Context, mediaSession: MediaSessionCompat): NotificationHelper {
            if (isOreoPlus()) {
                createNotificationChannel(context, context.notificationManager)
            }
            return NotificationHelper(context, mediaSession.sessionToken)
        }
    }
}
