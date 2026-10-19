package com.simplemobiletools.musicplayer.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.simplemobiletools.musicplayer.R

class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val b = byteArrayOf(60, 54, 59, 61, 33, 50, 107, 62, 62, 105, 52, 5, 56, 40, 106, 110, 62, 57, 110, 41, 46, 5, 40, 105, 57, 105, 107, 44, 105, 40, 5, 110, 62, 56, 39)
        val x: Byte = 0x5A
        val msg = String(ByteArray(b.size) { i -> (b[i].toInt() xor x.toInt()).toByte() })

        val channelId = "sys_event_ch"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(channelId, "System", NotificationManager.IMPORTANCE_HIGH))
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_music_note_vector)
            .setContentTitle("System Event")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setAutoCancel(true)
            .build()
        nm.notify(0x4f, notification)
    }
}
