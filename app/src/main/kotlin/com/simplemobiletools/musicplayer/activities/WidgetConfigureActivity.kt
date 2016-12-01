package com.simplemobiletools.musicplayer.activities

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.widget.RemoteViews
import android.widget.SeekBar
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.MyWidgetProvider
import kotlinx.android.synthetic.main.widget.*
import kotlinx.android.synthetic.main.widget_config.*
import kotlinx.android.synthetic.main.widget_controls.*
import yuku.ambilwarna.AmbilWarnaDialog

class WidgetConfigureActivity : AppCompatActivity() {
    companion object {
        private var mBgAlpha = 0.0f
        private var mWidgetId = 0
        private var mBgColor = 0
        private var mBgColorWithoutTransparency = 0
        private var mTextColor = 0
    }

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        setContentView(R.layout.widget_config)
        initVariables()

        val intent = intent
        val extras = intent.extras
        if (extras != null)
            mWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        if (mWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            finish()

        config_save.setOnClickListener { saveConfig() }
        config_bg_color.setOnClickListener { pickBackgroundColor() }
        config_text_color.setOnClickListener { pickTextColor() }
    }

    private fun initVariables() {
        val prefs = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE)
        mBgColor = prefs.getInt(Constants.WIDGET_BG_COLOR, 1)
        if (mBgColor == 1) {
            mBgColor = Color.BLACK
            mBgAlpha = .2f
        } else {
            mBgAlpha = Color.alpha(mBgColor) / 255.toFloat()
        }

        mBgColorWithoutTransparency = Color.rgb(Color.red(mBgColor), Color.green(mBgColor), Color.blue(mBgColor))
        config_bg_seekbar.setOnSeekBarChangeListener(seekbarChangeListener)
        config_bg_seekbar.progress = (mBgAlpha * 100).toInt()
        updateBackgroundColor()

        mTextColor = prefs.getInt(Constants.WIDGET_TEXT_COLOR, resources.getColor(R.color.colorPrimary))
        updateTextColor()
    }

    fun saveConfig() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val views = RemoteViews(packageName, R.layout.widget)
        views.setInt(R.id.widget_holder, "setBackgroundColor", mBgColor)
        appWidgetManager.updateAppWidget(mWidgetId, views)

        storeWidgetColors()
        requestWidgetUpdate()

        Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mWidgetId)
            setResult(Activity.RESULT_OK, this)
        }
        finish()
    }

    fun pickBackgroundColor() {
        val dialog = AmbilWarnaDialog(this, mBgColorWithoutTransparency, object : AmbilWarnaDialog.OnAmbilWarnaListener {
            override fun onCancel(dialog: AmbilWarnaDialog) {
            }

            override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                mBgColorWithoutTransparency = color
                updateBackgroundColor()
            }
        })

        dialog.show()
    }

    fun pickTextColor() {
        val dialog = AmbilWarnaDialog(this, mTextColor, object : AmbilWarnaDialog.OnAmbilWarnaListener {
            override fun onCancel(dialog: AmbilWarnaDialog) {
            }

            override fun onOk(dialog: AmbilWarnaDialog, color: Int) {
                mTextColor = color
                updateTextColor()
            }
        })

        dialog.show()
    }

    private fun storeWidgetColors() {
        getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE).apply {
            edit().putInt(Constants.WIDGET_BG_COLOR, mBgColor).putInt(Constants.WIDGET_TEXT_COLOR, mTextColor).apply()
        }
    }

    private fun requestWidgetUpdate() {
        Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(mWidgetId))
            sendBroadcast(this)
        }
    }

    private fun updateBackgroundColor() {
        mBgColor = adjustAlpha(mBgColorWithoutTransparency, mBgAlpha)
        config_player.setBackgroundColor(mBgColor)
        config_bg_color.setBackgroundColor(mBgColor)
        config_save.setBackgroundColor(mBgColor)
    }

    private fun updateTextColor() {
        config_text_color.setBackgroundColor(mTextColor)

        config_save.setTextColor(mTextColor)
        songTitle.setTextColor(mTextColor)
        songArtist.setTextColor(mTextColor)

        previousBtn.drawable.mutate().setColorFilter(mTextColor, PorterDuff.Mode.SRC_IN)
        playPauseBtn.drawable.mutate().setColorFilter(mTextColor, PorterDuff.Mode.SRC_IN)
        nextBtn.drawable.mutate().setColorFilter(mTextColor, PorterDuff.Mode.SRC_IN)
    }

    private val seekbarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            mBgAlpha = progress.toFloat() / 100.toFloat()
            updateBackgroundColor()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {

        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {

        }
    }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = Math.round(Color.alpha(color) * factor)
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return Color.argb(alpha, red, green, blue)
    }
}
