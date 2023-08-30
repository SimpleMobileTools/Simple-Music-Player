package com.simplemobiletools.musicplayer.playback

import android.os.CountDownTimer
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.models.Events
import org.greenrobot.eventbus.EventBus

private var isActive = false
private var sleepTimer: CountDownTimer? = null

internal fun PlaybackService.toggleSleepTimer() {
    if (isActive) {
        stopSleepTimer()
    } else {
        startSleepTimer()
    }
}

internal fun PlaybackService.startSleepTimer() {
    val millisInFuture = config.sleepInTS - System.currentTimeMillis() + 1000L
    sleepTimer?.cancel()
    sleepTimer = object : CountDownTimer(millisInFuture, 1000) {
        override fun onTick(millisUntilFinished: Long) {
            val seconds = (millisUntilFinished / 1000).toInt()
            EventBus.getDefault().post(Events.SleepTimerChanged(seconds))
        }

        override fun onFinish() {
            config.sleepInTS = 0
            EventBus.getDefault().post(Events.SleepTimerChanged(0))
            stopSleepTimer()
            stopService()
        }
    }

    sleepTimer?.start()
    isActive = true
}

internal fun PlaybackService.stopSleepTimer() {
    sleepTimer?.cancel()
    sleepTimer = null
    isActive = false
    config.sleepInTS = 0
}
