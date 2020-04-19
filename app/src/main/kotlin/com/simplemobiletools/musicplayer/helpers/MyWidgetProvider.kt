package com.simplemobiletools.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import com.simplemobiletools.commons.extensions.getColoredBitmap
import com.simplemobiletools.commons.extensions.getLaunchIntent
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SplashActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.simplemobiletools.musicplayer.services.MusicService.Companion.mCurrSong

class MyWidgetProvider : AppWidgetProvider() {
    private var mContext: Context? = null

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        performUpdate(context)
    }

    private fun performUpdate(context: Context) {
        mContext = context
        val appWidgetManager = AppWidgetManager.getInstance(context)
        appWidgetManager.getAppWidgetIds(getComponentName()).forEach {
            val views = getRemoteViews(appWidgetManager, context, it)
            updateColors(views)
            setupButtons(views)
            updateSongInfo(views, MusicService.mCurrSong)
            updatePlayPauseButton(views, MusicService.getIsPlaying())
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    private fun getComponentName() = ComponentName(mContext!!, MyWidgetProvider::class.java)

    private fun setupIntent(views: RemoteViews, action: String, id: Int) {
        val intent = Intent(mContext, MyWidgetProvider::class.java)
        intent.action = action
        val pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun setupAppOpenIntent(views: RemoteViews, id: Int) {
        val intent = mContext?.getLaunchIntent() ?: Intent(mContext, SplashActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0)
        views.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun songChanged(intent: Intent) {
        val song = intent.getSerializableExtra(NEW_SONG) as? Song ?: return
        val appWidgetManager = AppWidgetManager.getInstance(mContext)
        appWidgetManager.getAppWidgetIds(getComponentName()).forEach {
            val views = getRemoteViews(appWidgetManager, mContext!!, it)
            updateSongInfo(views, song)
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    private fun updateSongInfo(views: RemoteViews, currSong: Song?) {
        var title = ""
        var artist = ""
        if (currSong != null) {
            title = mCurrSong!!.title
            artist = mCurrSong!!.artist
        }

        views.setTextViewText(R.id.song_info_title, title)
        views.setTextViewText(R.id.song_info_artist, artist)
    }

    private fun songStateChanged(intent: Intent) {
        val isPlaying = intent.getBooleanExtra(IS_PLAYING, false)
        val appWidgetManager = AppWidgetManager.getInstance(mContext)
        appWidgetManager.getAppWidgetIds(getComponentName()).forEach {
            val views = getRemoteViews(appWidgetManager, mContext!!, it)
            updatePlayPauseButton(views, isPlaying)
            appWidgetManager.updateAppWidget(it, views)
        }
    }

    private fun updatePlayPauseButton(views: RemoteViews, isPlaying: Boolean) {
        val drawableId = if (isPlaying) R.drawable.ic_pause_vector else R.drawable.ic_play_vector
        val widgetTextColor = mContext!!.config.widgetTextColor
        val icon = mContext!!.resources.getColoredBitmap(drawableId, widgetTextColor)
        views.setImageViewBitmap(R.id.play_pause_btn, icon)
    }

    private fun updateColors(views: RemoteViews) {
        val config = mContext!!.config
        val res = mContext!!.resources
        val widgetBgColor = config.widgetBgColor
        val widgetTextColor = config.widgetTextColor

        views.apply {
            setInt(R.id.widget_background, "setColorFilter", widgetBgColor)
            setInt(R.id.widget_background, "setImageAlpha", Color.alpha(widgetBgColor))
            setTextColor(R.id.song_info_title, widgetTextColor)
            setTextColor(R.id.song_info_artist, widgetTextColor)
            setImageViewBitmap(R.id.previous_btn, res.getColoredBitmap(R.drawable.ic_previous_vector, widgetTextColor))
            setImageViewBitmap(R.id.next_btn, res.getColoredBitmap(R.drawable.ic_next_vector, widgetTextColor))
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        mContext = context
        val action = intent.action
        when (action) {
            SONG_CHANGED -> songChanged(intent)
            SONG_STATE_CHANGED -> songStateChanged(intent)
            PREVIOUS, PLAYPAUSE, NEXT -> context.sendIntent(action)
            else -> super.onReceive(context, intent)
        }
    }

    private fun setupButtons(views: RemoteViews) {
        setupIntent(views, PREVIOUS, R.id.previous_btn)
        setupIntent(views, PLAYPAUSE, R.id.play_pause_btn)
        setupIntent(views, NEXT, R.id.next_btn)

        setupAppOpenIntent(views, R.id.song_info_title)
        setupAppOpenIntent(views, R.id.song_info_artist)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        performUpdate(context)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions)
    }

    private fun getRemoteViews(appWidgetManager: AppWidgetManager, context: Context, widgetId: Int): RemoteViews {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)

        if (widgetId == context.config.widgetIdToMeasure && context.config.initialWidgetHeight == 0) {
            context.config.initialWidgetHeight = minHeight
        }

        val layoutId = if (minHeight < context.config.initialWidgetHeight / 2) R.layout.small_widget else R.layout.widget
        return RemoteViews(context.packageName, layoutId)
    }
}
