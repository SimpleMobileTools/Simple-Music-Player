package com.simplemobiletools.musicplayer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.extensions.sendIntent

class ControlActionsListener : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        when (action) {
            Constants.PREVIOUS, Constants.PLAYPAUSE, Constants.NEXT, Constants.FINISH -> context.sendIntent(action)
        }
    }
}
