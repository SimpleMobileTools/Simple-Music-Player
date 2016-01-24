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

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MyWidgetConfigure extends Activity implements SeekBar.OnSeekBarChangeListener {
    @Bind(R.id.config_seekbar) SeekBar seekBar;
    @Bind(R.id.config_player) View background;
    private int widgetId;
    private int newBgColor;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.widget_config);
        ButterKnife.bind(this);

        seekBar.setOnSeekBarChangeListener(this);
        seekBar.setProgress(50);

        final Intent intent = getIntent();
        final Bundle extras = intent.getExtras();
        if (extras != null)
            widgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
            finish();

        setResult(RESULT_CANCELED);
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
        final float percent = (float) progress / (float) 100;
        final int alpha = (int) (255 * percent);
        newBgColor = Color.argb(alpha, 0, 0, 0);
        background.setBackgroundColor(newBgColor);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
    }
}
