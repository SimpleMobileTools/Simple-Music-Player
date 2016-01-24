package musicplayer.simplemobiletools.com;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.SeekBar;
import android.widget.TextView;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;
import yuku.ambilwarna.AmbilWarnaDialog;

public class MyWidgetConfigure extends Activity implements SeekBar.OnSeekBarChangeListener {
    @Bind(R.id.config_seekbar) SeekBar seekBar;
    @Bind(R.id.config_player) View background;
    @Bind(R.id.config_background_color) View backgroundColorPicker;
    @Bind(R.id.songTitle) TextView songTitle;
    @Bind(R.id.songArtist) TextView songArtist;
    private int widgetId;
    private int newBgColor;
    private int colorWithoutTransparency;
    private float alpha;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.widget_config);
        ButterKnife.bind(this);
        initVariables();

        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null)
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            finish();

        setResult(RESULT_CANCELED);
    }

    private void initVariables() {
        alpha = 0.5f;
        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setProgress((int) (alpha * 100));
        newBgColor = Color.BLACK;
        newBgColor = adjustAlpha(newBgColor, alpha);
        colorWithoutTransparency = Color.BLACK;
        background.setBackgroundColor(newBgColor);
        backgroundColorPicker.setBackgroundColor(Color.BLACK);
        songTitle.setText("Song Title");
        songArtist.setText("Song Artist");
    }

    @OnClick(R.id.config_save)
    public void saveConfig() {
        final AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(this);
        final RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget);
        views.setInt(R.id.widget_holder, "setBackgroundColor", newBgColor);
        appWidgetManager.updateAppWidget(widgetId, views);

        storeWidgetBackground();
        requestWidgetUpdate();

        final Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, resultValue);
        finish();
    }

    @OnClick(R.id.config_background_color)
    public void pickBackgroundColor() {
        AmbilWarnaDialog dialog = new AmbilWarnaDialog(this, colorWithoutTransparency, new AmbilWarnaDialog.OnAmbilWarnaListener() {
            @Override
            public void onCancel(AmbilWarnaDialog dialog) {
            }

            @Override
            public void onOk(AmbilWarnaDialog dialog, int color) {
                backgroundColorPicker.setBackgroundColor(color);
                colorWithoutTransparency = color;
                updateBackgroundColor();
            }
        });

        dialog.show();
    }

    private void storeWidgetBackground() {
        final SharedPreferences prefs = getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
        prefs.edit().putInt(Constants.WIDGET_BG_COLOR, newBgColor).apply();
    }

    private void requestWidgetUpdate() {
        final Intent intent = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE, null, this, MyWidgetProvider.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{widgetId});
        sendBroadcast(intent);
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        alpha = (float) progress / (float) 100;
        updateBackgroundColor();
    }

    private void updateBackgroundColor() {
        newBgColor = adjustAlpha(colorWithoutTransparency, alpha);
        background.setBackgroundColor(newBgColor);
    }

    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
