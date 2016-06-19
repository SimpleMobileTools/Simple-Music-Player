package com.simplemobiletools.musicplayer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.RemoteViews;

import com.simplemobiletools.musicplayer.activities.MainActivity;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class MyWidgetProvider extends AppWidgetProvider {
    private static RemoteViews mRemoteViews;
    private static AppWidgetManager mWidgetManager;
    private static Context mContext;
    private static Intent mIntent;
    private static Bus mBus;
    private static Song mCurrSong;
    private static Bitmap mPlayBitmap;
    private static Bitmap mPauseBitmap;

    private static boolean mIsPlaying;
    private static int[] mWidgetIds;

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        initVariables(context);
        super.onUpdate(context, appWidgetManager, appWidgetIds);
    }

    private void setupIntent(String action, int id) {
        mIntent.setAction(action);
        final PendingIntent pendingIntent = PendingIntent.getBroadcast(mContext, 0, mIntent, 0);

        if (mRemoteViews != null)
            mRemoteViews.setOnClickPendingIntent(id, pendingIntent);
    }

    private void setupAppOpenIntent(int id) {
        final Intent intent = new Intent(mContext, MainActivity.class);
        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        mRemoteViews.setOnClickPendingIntent(id, pendingIntent);
    }

    private void initVariables(Context context) {
        mContext = context;
        mIntent = new Intent(mContext, MyWidgetProvider.class);
        updateWidgetIds();
        for (int widgetId : mWidgetIds) {
            mRemoteViews = getRemoteViews(mWidgetManager, mContext, widgetId);
        }

        setupViews(mContext);

        if (mBus == null) {
            mBus = BusProvider.getInstance();
        }
        registerBus();
    }

    private void updateWidgetIds() {
        final ComponentName component = new ComponentName(mContext, MyWidgetProvider.class);
        mWidgetManager = AppWidgetManager.getInstance(mContext);
        mWidgetIds = mWidgetManager.getAppWidgetIds(component);
    }

    private SharedPreferences initPrefs(Context context) {
        return context.getSharedPreferences(Constants.PREFS, Context.MODE_PRIVATE);
    }

    @Subscribe
    public void songChangedEvent(Events.SongChanged event) {
        mCurrSong = event.getSong();
        updateSongInfo();
        updateWidgets();
    }

    private void updateSongInfo() {
        String title = "";
        String artist = "";
        if (mCurrSong != null) {
            title = mCurrSong.getTitle();
            artist = mCurrSong.getArtist();
        }

        if (mRemoteViews == null)
            return;

        mRemoteViews.setTextViewText(R.id.songTitle, title);
        mRemoteViews.setTextViewText(R.id.songArtist, artist);
    }

    @Subscribe
    public void songStateChanged(Events.SongStateChanged event) {
        if (mIsPlaying == event.getIsPlaying())
            return;

        mIsPlaying = event.getIsPlaying();
        updatePlayPauseButton();
        updateWidgets();
    }

    private void updatePlayPauseButton() {
        Bitmap bmp = mPlayBitmap;

        if (mIsPlaying)
            bmp = mPauseBitmap;

        mRemoteViews.setImageViewBitmap(R.id.playPauseBtn, bmp);
    }

    private void updateWidgets() {
        for (int widgetId : mWidgetIds) {
            mWidgetManager.updateAppWidget(widgetId, mRemoteViews);
        }
    }

    private void updateColors(Context context) {
        final SharedPreferences prefs = initPrefs(context);
        final Resources res = context.getResources();
        final int defaultColor = res.getColor(R.color.dark_grey_transparent);
        final int newBgColor = prefs.getInt(Constants.WIDGET_BG_COLOR, defaultColor);
        final int newTextColor = prefs.getInt(Constants.WIDGET_TEXT_COLOR, Color.WHITE);

        if (mRemoteViews == null)
            initVariables(context);

        mRemoteViews.setInt(R.id.widget_holder, "setBackgroundColor", newBgColor);

        mRemoteViews.setInt(R.id.songTitle, "setTextColor", newTextColor);
        mRemoteViews.setInt(R.id.songArtist, "setTextColor", newTextColor);

        Bitmap bmp = Utils.getColoredIcon(res, newTextColor, R.mipmap.previous);
        mRemoteViews.setImageViewBitmap(R.id.previousBtn, bmp);

        mPlayBitmap = Utils.getColoredIcon(res, newTextColor, R.mipmap.play);
        mPauseBitmap = Utils.getColoredIcon(res, newTextColor, R.mipmap.pause);

        bmp = Utils.getColoredIcon(res, newTextColor, R.mipmap.next);
        mRemoteViews.setImageViewBitmap(R.id.nextBtn, bmp);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (mRemoteViews == null || mWidgetManager == null || mBus == null || mContext == null) {
            initVariables(context);
        }

        final String action = intent.getAction();
        switch (action) {
            case Constants.PREVIOUS:
            case Constants.PLAYPAUSE:
            case Constants.NEXT:
                Utils.sendIntent(context, action);
                break;
            default:
                super.onReceive(context, intent);
        }
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        super.onDeleted(context, appWidgetIds);
        unregisterBus();
        updateWidgetIds();
    }

    private void setupButtons() {
        setupIntent(Constants.PREVIOUS, R.id.previousBtn);
        setupIntent(Constants.PLAYPAUSE, R.id.playPauseBtn);
        setupIntent(Constants.NEXT, R.id.nextBtn);

        setupAppOpenIntent(R.id.songTitle);
        setupAppOpenIntent(R.id.songArtist);
    }

    private void setupViews(Context context) {
        updateWidgetIds();
        updateColors(context);
        setupButtons();
        updateSongInfo();
        updatePlayPauseButton();
        updateWidgets();
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int widgetId, Bundle newOptions) {
        mRemoteViews = getRemoteViews(appWidgetManager, context, widgetId);
        mWidgetManager = appWidgetManager;
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
            mBus.register(this);
        } catch (Exception e) {
        }
    }

    private void unregisterBus() {
        try {
            mBus.unregister(this);
        } catch (Exception e) {
        }
    }
}
