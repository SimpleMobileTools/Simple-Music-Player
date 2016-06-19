package com.simplemobiletools.musicplayer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.simplemobiletools.musicplayer.Constants;
import com.simplemobiletools.musicplayer.Utils;

public class HeadsetPlugReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isInitialStickyBroadcast() && intent.getAction().equals(Intent.ACTION_HEADSET_PLUG)) {
            final int state = intent.getIntExtra("state", -1);
            // we care only about the case where the headphone gets unplugged
            if (state == 0) {
                Utils.sendIntent(context, Constants.PAUSE);
            }
        }
    }
}
