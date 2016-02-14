package musicplayer.simplemobiletools.com;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class HeadsetPlugReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isInitialStickyBroadcast() && intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
            int state = intent.getIntExtra("state", -1);
            // we care only about the case where the headphone gets unplugged
            if (state == 0) {
                Utils.sendIntent(context, Constants.PAUSE);
            }
        }
    }
}
