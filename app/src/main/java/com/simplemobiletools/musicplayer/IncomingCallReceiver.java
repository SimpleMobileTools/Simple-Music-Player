package com.simplemobiletools.musicplayer;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class IncomingCallReceiver extends PhoneStateListener {
    private static Context mContext;

    public IncomingCallReceiver(Context context) {
        mContext = context;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        super.onCallStateChanged(state, incomingNumber);

        if (state == TelephonyManager.CALL_STATE_RINGING) {
            Utils.sendIntent(mContext, Constants.CALL_START);
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            Utils.sendIntent(mContext, Constants.CALL_STOP);
        }
    }
}
