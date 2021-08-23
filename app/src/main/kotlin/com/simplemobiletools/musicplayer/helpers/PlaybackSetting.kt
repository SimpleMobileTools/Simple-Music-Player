package com.simplemobiletools.musicplayer.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.simplemobiletools.musicplayer.R

enum class PlaybackSetting(
    @DrawableRes val iconRes: Int,
    @StringRes val descriptionStringRes: Int
) {
    REPEAT_OFF(
        iconRes = R.drawable.ic_repeat_playlist_vector,
        descriptionStringRes = R.string.repeat_off
    ),
    REPEAT_PLAYLIST(
        iconRes = R.drawable.ic_repeat_playlist_vector,
        descriptionStringRes = R.string.repeat_playlist
    ),
    REPEAT_TRACK(
        iconRes = R.drawable.ic_repeat_one_song_vector,
        descriptionStringRes = R.string.repeat_song
    ),
    STOP_AFTER_CURRENT_TRACK(
        iconRes = R.drawable.ic_play_one_song_vector,
        descriptionStringRes = R.string.stop_playback_after_current_song
    );

    val contentDescriptionStringRes: Int
        @StringRes get() = nextPlaybackOption.descriptionStringRes

    val nextPlaybackOption: PlaybackSetting
        get() = when (this) {
            REPEAT_OFF -> REPEAT_PLAYLIST
            REPEAT_PLAYLIST -> REPEAT_TRACK
            REPEAT_TRACK -> STOP_AFTER_CURRENT_TRACK
            STOP_AFTER_CURRENT_TRACK -> REPEAT_OFF
        }
}
