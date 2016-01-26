package musicplayer.simplemobiletools.com;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class MyWidgetProvider extends AppWidgetProvider {
    private static final String PREVIOUS = "previous";
    private static final String PLAYPAUSE = "playpause";
    private static final String NEXT = "next";
    private static final String STOP = "stop";

    private static int[] widgetIds;
    private static RemoteViews remoteViews;
    private static AppWidgetManager widgetManager;
    private static Context cxt;
    private static Intent intent;
    private static Bus bus;
    private static Song currSong;
    private static boolean isPlaying;
    private static Bitmap playBitmap;
    private static Bitmap pauseBitmap;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        initVariables(cxt);
    }

    private void setupIntent(String action, int id) {
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(cxt, 0, intent, 0);

        if (remoteViews != null)
            remoteViews.setOnClickPendingIntent(id, pendingIntent);
    }

    private void initVariables(Context context) {
        cxt = context;
        final ComponentName component = new ComponentName(cxt, MyWidgetProvider.class);

        widgetManager = AppWidgetManager.getInstance(cxt);
        widgetIds = widgetManager.getAppWidgetIds(component);
        if (widgetIds.length == 0)
            return;

        remoteViews = getRemoteViews(widgetManager, cxt, widgetIds[0]);
        intent = new Intent(cxt, MyWidgetProvider.class);
        setupViews(cxt);

        if (bus == null) {
            bus = BusProvider.getInstance();
        }
        registerBus();
    }

    private SharedPreferences initPrefs(Context context) {
        return context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
    }

    @Subscribe
    public void songChangedEvent(Events.SongChanged event) {
        currSong = event.getSong();
        updateSongInfo();
        updateWidget();
    }

    private void updateSongInfo() {
        String title = "";
        String artist = "";
        if (currSong != null) {
            title = currSong.getTitle();
            artist = currSong.getArtist();
        }

        if (remoteViews == null)
            return;

        remoteViews.setTextViewText(R.id.songTitle, title);
        remoteViews.setTextViewText(R.id.songArtist, artist);
    }

    @Subscribe
    public void songStateChanged(Events.SongStateChanged event) {
        isPlaying = event.getIsPlaying();
        updatePlayPauseButton();
        updateWidget();
    }

    private void updatePlayPauseButton() {
        Bitmap bmp = playBitmap;

        if (isPlaying)
            bmp = pauseBitmap;

        remoteViews.setImageViewBitmap(R.id.playPauseBtn, bmp);
    }

    private void updateWidget() {
        widgetManager.updateAppWidget(widgetIds, remoteViews);
    }

    private void updateColors(Context context) {
        final SharedPreferences prefs = initPrefs(context);
        final int defaultColor = context.getResources().getColor(R.color.dark_grey);
        final int newBgColor = prefs.getInt(Constants.WIDGET_BG_COLOR, defaultColor);
        final int newTextColor = prefs.getInt(Constants.WIDGET_TEXT_COLOR, Color.WHITE);

        remoteViews.setInt(R.id.widget_holder, "setBackgroundColor", newBgColor);

        final int alpha = Color.alpha(newTextColor);
        remoteViews.setInt(R.id.songTitle, "setTextColor", newTextColor);
        remoteViews.setInt(R.id.songArtist, "setTextColor", newTextColor);

        Bitmap bmp = getColoredIcon(context, newTextColor, R.mipmap.previous_white);
        remoteViews.setInt(R.id.previousBtn, "setAlpha", alpha);
        remoteViews.setImageViewBitmap(R.id.previousBtn, bmp);

        playBitmap = getColoredIcon(context, newTextColor, R.mipmap.play_white);
        pauseBitmap = getColoredIcon(context, newTextColor, R.mipmap.pause_white);
        remoteViews.setInt(R.id.playPauseBtn, "setAlpha", alpha);

        bmp = getColoredIcon(context, newTextColor, R.mipmap.next_white);
        remoteViews.setInt(R.id.nextBtn, "setAlpha", alpha);
        remoteViews.setImageViewBitmap(R.id.nextBtn, bmp);

        bmp = getColoredIcon(context, newTextColor, R.mipmap.stop_white);
        remoteViews.setInt(R.id.stopBtn, "setAlpha", alpha);
        remoteViews.setImageViewBitmap(R.id.stopBtn, bmp);
    }

    private Bitmap getColoredIcon(Context context, int newTextColor, int id) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        final Bitmap bmp = BitmapFactory.decodeResource(context.getResources(), id, options);
        final Paint paint = new Paint();
        final ColorFilter filter = new LightingColorFilter(newTextColor, 1);
        paint.setColorFilter(filter);
        final Canvas canvas = new Canvas(bmp);
        canvas.drawBitmap(bmp, 0, 0, paint);
        return bmp;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (remoteViews == null || widgetManager == null || widgetIds == null || bus == null)
            initVariables(context);

        final String action = intent.getAction();
        switch (action) {
            case PREVIOUS:
                bus.post(new Events.PreviousSong());
                break;
            case PLAYPAUSE:
                bus.post(new Events.PlayPauseSong());
                break;
            case NEXT:
                bus.post(new Events.NextSong());
                break;
            case STOP:
                bus.post(new Events.StopSong());
                break;
            default:
                super.onReceive(context, intent);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        unregisterBus();
    }

    private void setupButtons() {
        setupIntent(PREVIOUS, R.id.previousBtn);
        setupIntent(PLAYPAUSE, R.id.playPauseBtn);
        setupIntent(NEXT, R.id.nextBtn);
        setupIntent(STOP, R.id.stopBtn);
    }

    private void setupViews(Context context) {
        updateColors(context);
        setupButtons();
        updateSongInfo();
        updatePlayPauseButton();
        updateWidget();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int widgetId, Bundle newOptions) {
        remoteViews = getRemoteViews(appWidgetManager, context, widgetId);
        widgetManager = appWidgetManager;
        setupViews(context);
        super.onAppWidgetOptionsChanged(context, appWidgetManager, widgetId, newOptions);
    }

    private RemoteViews getRemoteViews(AppWidgetManager appWidgetManager, Context context, int widgetId) {
        final Bundle options = appWidgetManager.getAppWidgetOptions(widgetId);
        final int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT);
        final int rows = getCellsForSize(minHeight);

        int layoutId = R.layout.widget;
        if (rows == 1)
            layoutId = R.layout.small_widget;

        return new RemoteViews(context.getPackageName(), layoutId);
    }

    private static int getCellsForSize(int size) {
        int n = 2;
        while (70 * n - 30 < size) {
            ++n;
        }
        return n - 1;
    }

    private void registerBus() {
        try {
            bus.register(this);
        } catch (Exception e) {
        }
    }

    private void unregisterBus() {
        try {
            bus.unregister(this);
        } catch (Exception e) {
        }
    }
}
