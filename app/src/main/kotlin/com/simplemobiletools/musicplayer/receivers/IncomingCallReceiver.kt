package com.simplemobiletools.musicplayer.receivers

import android.content.Context
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.extensions.sendIntent

class IncomingCallReceiver(val context: Context) : PhoneStateListener() {

    override fun onCallStateChanged(state: Int, incomingNumber: String) {
        super.onCallStateChanged(state, incomingNumber)

        if (state == TelephonyManager.CALL_STATE_RINGING) {
            context.sendIntent(Constants.CALL_START)
        } else if (state == TelephonyManager.CALL_STATE_IDLE) {
            context.sendIntent(Constants.CALL_STOP)
        }
    }
}
