package musicplayer.simplemobiletools.com;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class MyWidgetProvider extends AppWidgetProvider {
    private static final String PREVIOUS = "previous";
    private static final String PLAYPAUSE = "playpause";
    private static final String NEXT = "next";
    private static final String STOP = "stop";

    private int[] widgetIds;
    private static RemoteViews remoteViews;
    private static AppWidgetManager widgetManager;
    private static Context cxt;
    private static Intent intent;
    private static Bus bus;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        initVariables(context);
        cxt = context;

        intent = new Intent(context, MyWidgetProvider.class);
        setupIntent(PREVIOUS, R.id.previousBtn);
        setupIntent(PLAYPAUSE, R.id.playPauseBtn);
        setupIntent(NEXT, R.id.nextBtn);
        setupIntent(STOP, R.id.stopBtn);

        appWidgetManager.updateAppWidget(appWidgetIds, remoteViews);
    }

    private void setupIntent(String action, int id) {
        intent.setAction(action);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(cxt, 0, intent, 0);
        remoteViews.setOnClickPendingIntent(id, pendingIntent);
    }

    private void initVariables(Context context) {
        final ComponentName component = new ComponentName(context, MyWidgetProvider.class);
        remoteViews = new RemoteViews(context.getPackageName(), R.layout.widget);
        widgetManager = AppWidgetManager.getInstance(context);
        widgetIds = widgetManager.getAppWidgetIds(component);
        if (bus == null) {
            bus = BusProvider.getInstance();
            bus.register(this);
        }
    }

    @Subscribe
    public void songChangedEvent(Events.SongChanged event) {
        final Song newSong = event.getSong();
        remoteViews.setTextViewText(R.id.songTitle, newSong.getTitle());
        remoteViews.setTextViewText(R.id.songArtist, newSong.getArtist());
        updateWidget();
    }

    private void updateWidget() {
        widgetManager.updateAppWidget(widgetIds, remoteViews);
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
        if (bus != null)
            bus.unregister(this);
    }
}
