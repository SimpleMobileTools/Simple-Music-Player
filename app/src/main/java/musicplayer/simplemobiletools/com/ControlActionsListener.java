package musicplayer.simplemobiletools.com;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ControlActionsListener extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            case Constants.PREVIOUS:
            case Constants.PLAYPAUSE:
            case Constants.NEXT:
            case Constants.STOP:
            case Constants.FINISH:
                context.startService(new Intent(action));
                break;
            default:
                break;
        }
    }
}
