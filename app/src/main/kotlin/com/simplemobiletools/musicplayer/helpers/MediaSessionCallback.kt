package com.simplemobiletools.musicplayer.helpers

import android.content.Intent
import android.os.Bundle
import android.support.v4.media.session.MediaSessionCompat
import com.simplemobiletools.musicplayer.services.MusicService

class MediaSessionCallback(private val service: MusicService) : MediaSessionCompat.Callback() {

    override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
        service.handleMediaButton(mediaButtonEvent)
        return true
    }

    override fun onPlay() {
        service.resumeTrack()
    }

    override fun onPause() {
        service.pauseTrack()
    }

    // this can happen after all notifications have been dismissed, so avoid recreating them
    override fun onStop() {
        if (MusicService.mIsServiceInitialized) {
            service.pauseTrack()
        }
    }

    override fun onSkipToNext() {
        service.handleNext()
    }

    override fun onSkipToPrevious() {
        service.handlePrevious()
    }

    override fun onSeekTo(pos: Long) {
        service.updateProgress((pos / 1000).toInt())
    }

    override fun onCustomAction(action: String?, extras: Bundle?) {
        when (action) {
            DISMISS -> service.handleDismiss()
        }
    }
}
