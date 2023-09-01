package com.simplemobiletools.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.os.Bundle
import android.widget.RemoteViews
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getColoredBitmap
import com.simplemobiletools.commons.extensions.getLaunchIntent
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SplashActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.maybePreparePlayer
import com.simplemobiletools.musicplayer.extensions.togglePlayback
import com.simplemobiletools.musicplayer.playback.PlaybackService

class MyWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        performUpdate(context)
    }

    private fun performUpdate(context: Context) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
            val views = getRemoteViews(appWidgetManager, context, it)
            updateColors(context, views)
            setupButtons(context, views)
            updateSongInfo(views, PlaybackService.currentMediaItem?.mediaMetadata)
            updatePlayPauseButton(context, views, PlaybackService.isPlaying)
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    override fun onEnabled(context: Context) = triggerUpdate(context)

    override fun onReceive(context: Context, intent: Intent) {
        when (val action = intent.action) {
            TRACK_STATE_CHANGED -> performUpdate(context)
            PREVIOUS, PLAYPAUSE, NEXT -> handlePlayerControls(context, action)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, newOptions: Bundle) = triggerUpdate(context)

    private fun handlePlayerControls(context: Context, action: String) {
        maybePreparePlayer(context) { player, _ ->
            if (player.currentMediaItem == null) {
                val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
                intent.addFlags(FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                when (action) {
                    NEXT -> player.seekToNextMediaItem()
                    PREVIOUS -> player.seekToPreviousMediaItem()
                    PLAYPAUSE -> player.togglePlayback()
                }
            }
        }
    }

    private fun maybePreparePlayer(context: Context, callback: (player: MediaController, prepared: Boolean) -> Unit) {
        SimpleMediaController.getInstance(context).withController {
            maybePreparePlayer(context) { success ->
                callback(this, success)
            }
        }
    }

    private fun triggerUpdate(context: Context) {
        performUpdate(context)
        maybePreparePlayer(context) { _, success ->
            if (success) {
                performUpdate(context)
            }
        }
    }

    private fun setupIntent(context: Context, views: RemoteViews, action: String, id: Int) {
        val intent = Intent(context, MyWidgetProvider::class.java)
        intent.action = action
        val pendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun setupAppOpenIntent(context: Context, views: RemoteViews, id: Int) {
        val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun updateSongInfo(views: RemoteViews, currSong: MediaMetadata?) {
        if (currSong != null) {
            views.setTextViewText(R.id.song_info_title, currSong.title)
            views.setTextViewText(R.id.song_info_artist, currSong.artist)
        }
    }

    private fun updatePlayPauseButton(context: Context, views: RemoteViews, isPlaying: Boolean) {
        val drawableId = if (isPlaying) com.simplemobiletools.commons.R.drawable.ic_pause_vector else com.simplemobiletools.commons.R.drawable.ic_play_vector
        val widgetTextColor = context.config.widgetTextColor
        val icon = context.resources.getColoredBitmap(drawableId, widgetTextColor)
        views.setImageViewBitmap(R.id.play_pause_btn, icon)
    }

    private fun updateColors(context: Context, views: RemoteViews) {
        val config = context.config
        val widgetBgColor = config.widgetBgColor
        val widgetTextColor = config.widgetTextColor

        views.apply {
            applyColorFilter(R.id.widget_background, widgetBgColor)
            setTextColor(R.id.song_info_title, widgetTextColor)
            setTextColor(R.id.song_info_artist, widgetTextColor)
            setImageViewBitmap(
                R.id.previous_btn,
                context.resources.getColoredBitmap(
                    com.simplemobiletools.commons.R.drawable.ic_previous_vector,
                    widgetTextColor
                )
            )
            setImageViewBitmap(R.id.next_btn, context.resources.getColoredBitmap(com.simplemobiletools.commons.R.drawable.ic_next_vector, widgetTextColor))
        }
    }

    private fun setupButtons(context: Context, views: RemoteViews) {
        setupIntent(context, views, PREVIOUS, R.id.previous_btn)
        setupIntent(context, views, PLAYPAUSE, R.id.play_pause_btn)
        setupIntent(context, views, NEXT, R.id.next_btn)

        setupAppOpenIntent(context, views, R.id.song_info_title)
        setupAppOpenIntent(context, views, R.id.song_info_artist)
    }

    private fun getRemoteViews(appWidgetManager: AppWidgetManager, context: Context, widgetId: Int): RemoteViews {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        if (widgetId == context.config.widgetIdToMeasure && context.config.initialWidgetHeight == 0) {
            context.config.initialWidgetHeight = minHeight
        }

        val layoutId = if (minHeight < context.config.initialWidgetHeight / 2) {
            R.layout.small_widget
        } else {
            R.layout.widget
        }

        return RemoteViews(context.packageName, layoutId)
    }

    private fun getComponentName(context: Context) = ComponentName(context, MyWidgetProvider::class.java)
}
