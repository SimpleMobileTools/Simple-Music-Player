package musicplayer.simplemobiletools.com;

import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class IncomingCallReceiver extends PhoneStateListener {
    private Context cxt;

    public IncomingCallReceiver(Context context) {
        cxt = context;
    }

    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        super.onCallStateChanged(state, incomingNumber);

        if (state == TelephonyManager.CALL_STATE_RINGING) {
            Utils.sendIntent(cxt, Constants.CALL_START);
        } else if (state == TelephonyManager.CALL_STATE_IDLE || state == TelephonyManager.CALL_STATE_OFFHOOK) {
            Utils.sendIntent(cxt, Constants.CALL_STOP);
        }
    }
}
