package com.simplemobiletools.musicplayer;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LightingColorFilter;
import android.graphics.Paint;

public class Utils {
    public static void sendIntent(Context context, String action) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    public static Bitmap getColoredIcon(Resources res, int newTextColor, int id) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        final Bitmap bmp = BitmapFactory.decodeResource(res, id, options);
        final Paint paint = new Paint();
        final ColorFilter filter = new LightingColorFilter(newTextColor, 1);
        paint.setColorFilter(filter);
        final Canvas canvas = new Canvas(bmp);
        canvas.drawBitmap(bmp, 0, 0, paint);
        return bmp;
    }
}
