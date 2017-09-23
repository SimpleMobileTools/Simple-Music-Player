package com.simplemobiletools.musicplayer.helpers

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.widget.RemoteViews
import com.simplemobiletools.commons.extensions.getColoredIcon
import com.simplemobiletools.commons.extensions.setBackgroundColor
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SplashActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Song
import com.simplemobiletools.musicplayer.services.MusicService
import com.squareup.otto.Bus
import com.squareup.otto.Subscribe

class MyWidgetProvider : AppWidgetProvider() {
    companion object {
        private var mRemoteViews: RemoteViews? = null
    }

    private var mBus: Bus? = null
    private var mCurrSong: Song? = null
    private var mPlayBitmap: Bitmap? = null
    private var mPauseBitmap: Bitmap? = null
    private var mIsPlaying = false
    private var mWidgetIds: IntArray? = null

    lateinit var mWidgetManager: AppWidgetManager
    lateinit var mContext: Context

    private fun getCellsForSize(size: Int): Int {
        var n = 2
        while (70 * n - 30 < size) {
            ++n
        }
        return n - 1
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        initVariables(context)
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun initVariables(context: Context) {
        mContext = context
        mWidgetManager = AppWidgetManager.getInstance(mContext)
        updateWidgetIds()
        for (widgetId in mWidgetIds!!) {
            mRemoteViews = getRemoteViews(mWidgetManager, mContext, widgetId)
        }

        setupViews(mContext)

        if (mBus == null) {
            mBus = BusProvider.instance
        }
        registerBus()
        updateSong(MusicService.mCurrSong)
        updateSongState(MusicService.getIsPlaying())
    }

    private fun setupIntent(action: String, id: Int) {
        val intent = Intent(mContext, MyWidgetProvider::class.java)
        intent.action = action
        val pendingIntent = PendingIntent.getBroadcast(mContext, 0, intent, 0)
        mRemoteViews!!.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun setupAppOpenIntent(id: Int) {
        val intent = Intent(mContext, SplashActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0)
        mRemoteViews!!.setOnClickPendingIntent(id, pendingIntent)
    }

    private fun updateWidgetIds() {
        val component = ComponentName(mContext, MyWidgetProvider::class.java)
        mWidgetManager = AppWidgetManager.getInstance(mContext)
        mWidgetIds = mWidgetManager.getAppWidgetIds(component)
    }

    @Subscribe
    fun songChangedEvent(event: Events.SongChanged) {
        updateSong(event.song)
    }

    private fun updateSong(song: Song?) {
        mCurrSong = song
        updateSongInfo()
        updateWidgets()
    }

    private fun updateSongInfo() {
        var title = ""
        var artist = ""
        if (mCurrSong != null) {
            title = mCurrSong!!.title
            artist = mCurrSong!!.artist
        }

        mRemoteViews!!.setTextViewText(R.id.song_title, title)
        mRemoteViews!!.setTextViewText(R.id.song_artist, artist)
    }

    @Subscribe
    fun songStateChanged(event: Events.SongStateChanged) {
        updateSongState(event.isPlaying)
    }

    private fun updateSongState(isPlaying: Boolean) {
        if (mIsPlaying == isPlaying)
            return

        mIsPlaying = isPlaying
        updatePlayPauseButton()
        updateWidgets()
    }

    private fun updatePlayPauseButton() {
        val bmp = if (mIsPlaying) mPauseBitmap else mPlayBitmap
        mRemoteViews!!.setImageViewBitmap(R.id.play_pause_btn, bmp)
    }

    private fun updateWidgets() {
        for (widgetId in mWidgetIds!!) {
            mWidgetManager.updateAppWidget(widgetId, mRemoteViews)
        }
    }

    private fun updateColors() {
        val config = mContext.config
        val res = mContext.resources
        val widgetBgColor = config.widgetBgColor
        val widgetTextColor = config.widgetTextColor
        var bmp = res.getColoredIcon(widgetTextColor, R.drawable.ic_previous)

        mRemoteViews!!.apply {
            setBackgroundColor(R.id.widget_holder, widgetBgColor)
            setTextColor(R.id.song_title, widgetTextColor)
            setTextColor(R.id.song_artist, widgetTextColor)

            setImageViewBitmap(R.id.previous_btn, bmp)

            mPlayBitmap = res.getColoredIcon(widgetTextColor, R.drawable.ic_play)
            mPauseBitmap = res.getColoredIcon(widgetTextColor, R.drawable.ic_pause)

            bmp = res.getColoredIcon(widgetTextColor, R.drawable.ic_next)
            setImageViewBitmap(R.id.next_btn, bmp)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            PREVIOUS, PLAYPAUSE, NEXT -> context.sendIntent(action)
            else -> super.onReceive(context, intent)
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        unregisterBus()
        mContext = context
        updateWidgetIds()
    }

    private fun setupButtons() {
        setupIntent(PREVIOUS, R.id.previous_btn)
        setupIntent(PLAYPAUSE, R.id.play_pause_btn)
        setupIntent(NEXT, R.id.next_btn)

        setupAppOpenIntent(R.id.song_title)
        setupAppOpenIntent(R.id.song_artist)
    }

    private fun setupViews(context: Context) {
        mContext = context
        updateWidgetIds()
        updateColors()
        setupButtons()
        updateSongInfo()
        updatePlayPauseButton()
        updateWidgets()
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int, newOptions: Bundle) {
        if (mRemoteViews == null)
            initVariables(context)

        mRemoteViews = getRemoteViews(appWidgetManager, context, widgetId)
        mWidgetManager = appWidgetManager
        setupViews(context)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions)
    }

    private fun getRemoteViews(appWidgetManager: AppWidgetManager, context: Context, widgetId: Int): RemoteViews {
        val options = appWidgetManager.getAppWidgetOptions(widgetId)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val rows = getCellsForSize(minHeight)

        var layoutId = R.layout.widget
        if (rows == 1)
            layoutId = R.layout.small_widget

        return RemoteViews(context.packageName, layoutId)
    }

    private fun registerBus() {
        try {
            mBus!!.register(this)
        } catch (e: Exception) {
        }
    }

    private fun unregisterBus() {
        try {
            mBus!!.unregister(this)
        } catch (e: Exception) {
        }
    }
}
