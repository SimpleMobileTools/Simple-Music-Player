package com.simplemobiletools.musicplayer.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.RemoteViews
import com.simplemobiletools.commons.dialogs.ColorPickerDialog
import com.simplemobiletools.commons.dialogs.WidgetLockedDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.MyWidgetProvider
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.widget.*
import kotlinx.android.synthetic.main.widget.view.*
import kotlinx.android.synthetic.main.widget_config.*
import kotlinx.android.synthetic.main.widget_controls.*

class WidgetConfigureActivity : SimpleActivity() {
    private var mBgAlpha = 0f
    private var mWidgetId = 0
    private var mBgColor = 0
    private var mTextColor = 0
    private var mBgColorWithoutTransparency = 0
    private var mWidgetLockedDialog: WidgetLockedDialog? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_config)
        initVariables()

        val isCustomizingColors = intent.extras?.getBoolean(IS_CUSTOMIZING_COLORS) ?: false
        mWidgetId = intent.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID && !isCustomizingColors) {
            finish()
        }

        config_save.setOnClickListener { saveConfig() }
        config_bg_color.setOnClickListener { pickBackgroundColor() }
        config_text_color.setOnClickListener { pickTextColor() }

        val currSong = MusicService.mCurrTrack
        if (currSong != null) {
            song_info_title.text = currSong.title
            song_info_artist.text = currSong.artist
        } else {
            song_info_title.text = getString(R.string.artist)
            song_info_artist.text = getString(R.string.song_title)
        }

        if (!isCustomizingColors && !isOrWasThankYouInstalled()) {
            mWidgetLockedDialog = WidgetLockedDialog(this) {
                if (!isOrWasThankYouInstalled()) {
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (mWidgetLockedDialog != null && isOrWasThankYouInstalled()) {
            mWidgetLockedDialog?.dismissDialog()
        }
    }

    private fun initVariables() {
        mBgColor = config.widgetBgColor
        mBgAlpha = Color.alpha(mBgColor) / 255.toFloat()

        mBgColorWithoutTransparency = Color.rgb(Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor))
        config_bg_seekbar.progress = (mBgAlpha * 100).toInt()
        updateBackgroundColor()
        config_bg_seekbar.onSeekBarChangeListener { progress ->
            mBgAlpha = progress / 100.toFloat()
            updateBackgroundColor()
        }

        mTextColor = config.widgetTextColor
        updateTextColor()
    }

    private fun saveConfig() {
        val appWidgetManager = AppWidgetManager.getInstance(this) ?: return
        val views = RemoteViews(packageName, R.layout.widget).apply {
            applyColorFilter(R.id.widget_background, mBgColor)
        }

        appWidgetManager.updateAppWidget(mWidgetId, views)

        storeWidgetColors()
        requestWidgetUpdate()

        if (config.initialWidgetHeight == 0) {
            config.widgetIdToMeasure = mWidgetId
        }

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    private fun storeWidgetColors() {
        config.apply {
            widgetBgColor = mBgColor
            widgetTextColor = mTextColor
        }
    }

    private fun requestWidgetUpdate() {
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateBackgroundColor() {
        mBgColor = mBgColorWithoutTransparency.adjustAlpha(mBgAlpha)
        config_player.widget_background.applyColorFilter(mBgColor)
        config_save.setBackgroundColor(mBgColor)
        config_bg_color.setFillWithStroke(mBgColor, Color.BLACK)
    }

    private fun updateTextColor() {
        config_text_color.setFillWithStroke(mTextColor, Color.BLACK)

        config_save.setTextColor(mTextColor)
        song_info_title.setTextColor(mTextColor)
        song_info_artist.setTextColor(mTextColor)

        previous_btn.drawable.applyColorFilter(mTextColor)
        play_pause_btn.drawable.applyColorFilter(mTextColor)
        next_btn.drawable.applyColorFilter(mTextColor)
    }

    private fun pickBackgroundColor() {
        ColorPickerDialog(this, mBgColorWithoutTransparency) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mBgColorWithoutTransparency = color
                updateBackgroundColor()
            }
        }
    }

    private fun pickTextColor() {
        ColorPickerDialog(this, mTextColor) { wasPositivePressed, color ->
            if (wasPositivePressed) {
                mTextColor = color
                updateTextColor()
            }
        }
    }
}
