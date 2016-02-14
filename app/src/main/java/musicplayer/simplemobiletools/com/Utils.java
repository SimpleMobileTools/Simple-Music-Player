package musicplayer.simplemobiletools.com;

import android.content.Context;
import android.content.Intent;

public class Utils {
    public static void sendIntent(Context context, String action) {
        Intent intent = new Intent(context, MusicService.class);
        intent.setAction(action);
        context.startService(intent);
    }
}
