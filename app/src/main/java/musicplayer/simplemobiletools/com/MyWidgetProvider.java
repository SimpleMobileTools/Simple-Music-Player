package musicplayer.simplemobiletools.com;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

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
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            case PREVIOUS:
                break;
            case PLAYPAUSE:
                break;
            case NEXT:
                break;
            case STOP:
                break;
            default:
                super.onReceive(context, intent);
        }
    }
}
