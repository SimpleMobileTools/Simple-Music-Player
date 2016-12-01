package com.simplemobiletools.musicplayer;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;

import java.util.Locale;

public class Utils {
    public static void sendIntent(Context context, String action) {
        final Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    public static Bitmap getColoredIcon(Resources res, int newColor, int id) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        final Bitmap bmp = BitmapFactory.decodeResource(res, id, options);
        final Paint paint = new Paint();
        final ColorFilter filter = new PorterDuffColorFilter(newColor, PorterDuff.Mode.SRC_IN);
        paint.setColorFilter(filter);
        final Canvas canvas = new Canvas(bmp);
        canvas.drawBitmap(bmp, 0, 0, paint);
        return bmp;
    }

    public static String getTimeString(int duration) {
        final StringBuilder sb = new StringBuilder(8);
        final int hours = duration / (60 * 60);
        final int minutes = (duration % (60 * 60)) / 60;
        final int seconds = ((duration % (60 * 60)) % 60);

        if (duration > 3600) {
            sb.append(String.format(Locale.getDefault(), "%02d", hours)).append(":");
        }

        sb.append(String.format(Locale.getDefault(), "%02d", minutes));
        sb.append(":").append(String.format(Locale.getDefault(), "%02d", seconds));

        return sb.toString();
    }
}
