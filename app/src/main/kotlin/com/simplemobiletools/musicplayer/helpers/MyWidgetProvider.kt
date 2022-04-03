package com.simplemobiletools.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.simplemobiletools.commons.extensions.applyColorFilter
import com.simplemobiletools.commons.extensions.getColoredBitmap
import com.simplemobiletools.commons.extensions.getLaunchIntent
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SplashActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService

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
            updateSongInfo(views, MusicService.mCurrTrack)
            updatePlayPauseButton(context, views, MusicService.getIsPlaying())
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    override fun onEnabled(context: Context) {
        context.sendIntent(BROADCAST_STATUS)
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            TRACK_CHANGED -> songChanged(context, intent)
            TRACK_STATE_CHANGED -> songStateChanged(context, intent)
            PREVIOUS, PLAYPAUSE, NEXT -> handlePlayerControls(context, action)
            else -> super.onReceive(context, intent)
        }
    }

    private fun handlePlayerControls(context: Context, action: String) {
        if (MusicService.mCurrTrack == null) {
            val intent = context.getLaunchIntent() ?: Intent(context, SplashActivity::class.java)
            context.startActivity(intent)
        } else {
            context.sendIntent(action)
        }
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        performUpdate(context)
        context.sendIntent(BROADCAST_STATUS)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions)
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

    private fun songChanged(context: Context, intent: Intent) {
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        val song = intent.getSerializableExtra(NEW_TRACK) as? Track
        appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
            val views = getRemoteViews(appWidgetManager, context, it)
            updateSongInfo(views, song)
            updatePlayPauseButton(context, views, MusicService.getIsPlaying())
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    private fun updateSongInfo(views: RemoteViews, currSong: Track?) {
        views.setTextViewText(R.id.song_info_title, currSong?.title ?: "")
        views.setTextViewText(R.id.song_info_artist, currSong?.artist ?: "")
    }

    private fun songStateChanged(context: Context, intent: Intent) {
        val isPlaying = intent.getBooleanExtra(IS_PLAYING, false)
        val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
        appWidgetManager.getAppWidgetIds(getComponentName(context)).forEach {
            val views = getRemoteViews(appWidgetManager, context, it)
            updatePlayPauseButton(context, views, isPlaying)
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    private fun updatePlayPauseButton(context: Context, views: RemoteViews, isPlaying: Boolean) {
        val drawableId = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
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
            setImageViewBitmap(R.id.previous_btn, context.resources.getColoredBitmap(R.drawable.ic_previous_vector, widgetTextColor))
            setImageViewBitmap(R.id.next_btn, context.resources.getColoredBitmap(R.drawable.ic_next_vector, widgetTextColor))
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
