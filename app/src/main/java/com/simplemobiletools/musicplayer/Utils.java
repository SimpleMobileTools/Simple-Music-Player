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
import android.widget.EditText;
import android.widget.Toast;

public class Utils {
    public static void sendIntent(Context context, String action) {
        final Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        context.startService(intent);
    }

    public static Bitmap getColoredIcon(Resources res, int newTextColor, int id) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inMutable = true;
        final Bitmap bmp = BitmapFactory.decodeResource(res, id, options);
        final Paint paint = new Paint();
        final ColorFilter filter = new PorterDuffColorFilter(newTextColor, PorterDuff.Mode.SRC_IN);
        paint.setColorFilter(filter);
        final Canvas canvas = new Canvas(bmp);
        canvas.drawBitmap(bmp, 0, 0, paint);
        return bmp;
    }

    public static void showToast(Context context, int resId) {
        Toast.makeText(context, context.getResources().getString(resId), Toast.LENGTH_SHORT).show();
    }

    public static String getFilename(final String path) {
        return path.substring(path.lastIndexOf("/") + 1);
    }

    public static String getViewText(EditText editText) {
        return editText.getText().toString().trim();
    }
}
