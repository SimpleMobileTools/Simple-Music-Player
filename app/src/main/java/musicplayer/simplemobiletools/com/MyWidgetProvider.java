package musicplayer.simplemobiletools.com;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class MyWidgetProvider extends AppWidgetProvider {
    private static final String PREVIOUS = "previous";
    private static final String PLAYPAUSE = "playpause";
    private static final String NEXT = "next";
    private static final String STOP = "stop";

    private static int widgetId;
    private static RemoteViews remoteViews;
    private static AppWidgetManager widgetManager;
    private static Context cxt;
    private static Intent intent;
    private static Bus bus;
    private static Song currSong;
    private static boolean isPlaying;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        initVariables(context);
        cxt = context;

        intent = new Intent(context, MyWidgetProvider.class);
        setupButtons(appWidgetManager);
        updateSongInfo();
    }

    private void setupIntent(String action, int id) {
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(cxt, 0, intent, 0);
        remoteViews.setOnClickPendingIntent(id, pendingIntent);
    }

    private void initVariables(Context context) {
        final ComponentName component = new ComponentName(context, MyWidgetProvider.class);
        widgetManager = AppWidgetManager.getInstance(context);
        final int[] widgetIds = widgetManager.getAppWidgetIds(component);
        if (widgetIds.length == 0)
            return;

        widgetId = widgetIds[0];
        remoteViews = getRemoteViews(widgetManager, context, widgetId);

        if (bus == null) {
            bus = BusProvider.getInstance();
            bus.register(this);
        }
    }

    @Subscribe
    public void songChangedEvent(Events.SongChanged event) {
        currSong = event.getSong();
        updateSongInfo();
    }

    private void updateSongInfo() {
        String title = "";
        String artist = "";
        if (currSong != null) {
            title = currSong.getTitle();
            artist = currSong.getArtist();
        }
        remoteViews.setTextViewText(R.id.songTitle, title);
        remoteViews.setTextViewText(R.id.songArtist, artist);
        updateWidget();
    }

    @Subscribe
    public void songStateChanged(Events.SongStateChanged event) {
        isPlaying = event.getIsPlaying();
        setupPlayPauseButton();
    }

    private void setupPlayPauseButton() {
        int icon = R.mipmap.play_white;

        if (isPlaying)
            icon = R.mipmap.pause_white;

        remoteViews.setImageViewResource(R.id.playPauseBtn, icon);
        updateWidget();
    }

    private void updateWidget() {
        widgetManager.updateAppWidget(widgetId, remoteViews);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (remoteViews == null || widgetManager == null || widgetId == 0 || bus == null)
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
        if (bus != null) {
            try {
                bus.unregister(this);
            } catch (Exception e) {
            }
        }
    }

    private void setupButtons(AppWidgetManager appWidgetManager) {
        setupIntent(PREVIOUS, R.id.previousBtn);
        setupIntent(PLAYPAUSE, R.id.playPauseBtn);
        setupIntent(NEXT, R.id.nextBtn);
        setupIntent(STOP, R.id.stopBtn);
        appWidgetManager.updateAppWidget(widgetId, remoteViews);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int widgetId, Bundle newOptions) {
        remoteViews = getRemoteViews(appWidgetManager, context, widgetId);
        setupButtons(appWidgetManager);
        updateSongInfo();
        setupPlayPauseButton();
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
}
