package musicplayer.simplemobiletools.com;

import android.content.Context;
import android.widget.Toast;

public class Utils {
    public static void showToast(Context context, int msgId) {
        Toast.makeText(context, context.getResources().getString(msgId), Toast.LENGTH_SHORT).show();
    }
}
