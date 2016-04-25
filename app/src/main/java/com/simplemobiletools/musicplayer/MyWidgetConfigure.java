package com.simplemobiletools.musicplayer;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import yuku.ambilwarna.AmbilWarnaDialog;

public class MyWidgetConfigure extends AppCompatActivity {
    @Bind(R.id.config_bg_seekbar) SeekBar bgSeekBar;
    @Bind(R.id.config_player) View widgetBackground;
    @Bind(R.id.config_bg_color) View bgColorPicker;
    @Bind(R.id.config_text_color) View textColorPicker;
    @Bind(R.id.config_save) Button saveBtn;

    @Bind(R.id.songTitle) TextView songTitle;
    @Bind(R.id.songArtist) TextView songArtist;

    @Bind(R.id.previousBtn) ImageView prevBtn;
    @Bind(R.id.playPauseBtn) ImageView playPauseBtn;
    @Bind(R.id.nextBtn) ImageView nextBtn;

    private int widgetId;
    private int bgColor;
    private int bgColorWithoutTransparency;
    private float bgAlpha;

    private int textColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_config);
        ButterKnife.bind(this);
        initVariables();

        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null)
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            finish();
    }

    private void initVariables() {
        final SharedPreferences prefs = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
        bgColor = prefs.getInt(Constants.WIDGET_BG_COLOR, 0);
        if (bgColor == 0) {
            bgColor = Color.BLACK;
            bgAlpha = .5f;
        } else {
            bgAlpha = Color.alpha(bgColor) / (float) 255;
        }

        bgColorWithoutTransparency = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor));
        bgSeekBar.setOnSeekBarChangeListener(bgSeekbarChangeListener);
        bgSeekBar.setProgress((int) (bgAlpha * 100));
        updateBackgroundColor();

        textColor = prefs.getInt(Constants.WIDGET_TEXT_COLOR, Color.WHITE);
        updateTextColor();

        songTitle.setText("Song Title");
        songArtist.setText("Song Artist");
    }

    @OnClick(R.id.config_save)
    public void saveConfig() {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        final RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
        views.setInt(R.id.widget_holder, "setBackgroundColor", bgColor);
        appWidgetManager.updateAppWidget(widgetId, views);

        storeWidgetColors();
        requestWidgetUpdate();

        final Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    @OnClick(R.id.config_bg_color)
    public void pickBackgroundColor() {
        AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, bgColorWithoutTransparency, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                bgColorWithoutTransparency = color;
                updateBackgroundColor();
            }
        });

        dialog.show();
    }

    @OnClick(R.id.config_text_color)
    public void pickTextColor() {
        AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, textColor, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                textColor = color;
                updateTextColor();
            }
        });

        dialog.show();
    }

    private void storeWidgetColors() {
        final SharedPreferences prefs = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(Constants.WIDGET_BG_COLOR, bgColor).apply();
        prefs.edit().putInt(Constants.WIDGET_TEXT_COLOR, textColor).apply();
    }

    private void requestWidgetUpdate() {
        final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        sendBroadcast(intent);
    }

    private void updateBackgroundColor() {
        bgColor = adjustAlpha(bgColorWithoutTransparency, bgAlpha);
        widgetBackground.setBackgroundColor(bgColor);
        bgColorPicker.setBackgroundColor(bgColor);
        saveBtn.setBackgroundColor(bgColor);
    }

    private void updateTextColor() {
        textColorPicker.setBackgroundColor(textColor);

        saveBtn.setTextColor(textColor);
        songTitle.setTextColor(textColor);
        songArtist.setTextColor(textColor);

        prevBtn.getDrawable().mutate().setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        playPauseBtn.getDrawable().mutate().setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
        nextBtn.getDrawable().mutate().setColorFilter(textColor, PorterDuff.Mode.SRC_IN);
    }

    private SeekBar.OnSeekBarChangeListener bgSeekbarChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            bgAlpha = (float) progress / (float) 100;
            updateBackgroundColor();
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {

        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {

        }
    };

    private int adjustAlpha(int color, float factor) {
        final int alpha = Math.round(Color.alpha(color) * factor);
        final int red = Color.red(color);
        final int green = Color.green(color);
        final int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }
}
