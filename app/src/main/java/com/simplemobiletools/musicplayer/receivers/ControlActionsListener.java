package com.simplemobiletools.musicplayer.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.simplemobiletools.musicplayer.Constants;
import com.simplemobiletools.musicplayer.Utils;

public class ControlActionsListener extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            case Constants.PREVIOUS:
            case Constants.PLAYPAUSE:
            case Constants.NEXT:
            case Constants.FINISH:
                Utils.sendIntent(context, action);
                break;
            default:
                break;
        }
    }
}
