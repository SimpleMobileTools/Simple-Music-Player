package musicplayer.simplemobiletools.com;

import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import com.squareup.otto.Bus;

public class IncomingCallReceiver extends PhoneStateListener {
    @Override
    public void onCallStateChanged(int state, String incomingNumber) {
        super.onCallStateChanged(state, incomingNumber);

        final Bus bus = BusProvider.getInstance();
        if (state == TelephonyManager.CALL_STATE_RINGING) {
            bus.post(new Events.IncomingCallStart());
        } else if (state == TelephonyManager.CALL_STATE_IDLE || state == TelephonyManager.CALL_STATE_OFFHOOK) {
            bus.post(new Events.IncomingCallStop());
        }
    }
}
