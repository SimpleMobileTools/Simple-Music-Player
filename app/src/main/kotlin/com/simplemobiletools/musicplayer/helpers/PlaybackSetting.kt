package com.simplemobiletools.musicplayer.helpers

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.simplemobiletools.musicplayer.R

enum class PlaybackSetting(
    @DrawableRes val iconRes: Int,
    @StringRes val actionCompletedStringRes: Int,
    @StringRes val tooltipStringRes: Int,
    @StringRes val contentDescriptionStringRes: Int
) {
    REPEAT_PLAYLIST(
        iconRes = R.drawable.ic_repeat_playlist_vector,
        actionCompletedStringRes = R.string.repeat_playlist_enabled,
        tooltipStringRes = R.string.repeat_playlist,
        contentDescriptionStringRes = R.string.enable_song_repetition
    ),
    REPEAT_SONG(
        iconRes = R.drawable.ic_repeat_one_song_vector,
        actionCompletedStringRes = R.string.song_repetition_enabled,
        tooltipStringRes = R.string.enable_song_repetition,
        contentDescriptionStringRes = R.string.enable_stop_playback_after_current_song
    ),
    STOP_AFTER_CURRENT_SONG(
        iconRes = R.drawable.ic_play_one_song_vector,
        actionCompletedStringRes = R.string.stop_playback_after_current_song_enabled,
        tooltipStringRes = R.string.enable_stop_playback_after_current_song,
        contentDescriptionStringRes = R.string.repeat_playlist
    );

    val nextPlaybackOption: PlaybackSetting
        get() = when (this) {
            REPEAT_PLAYLIST -> REPEAT_SONG
            REPEAT_SONG -> STOP_AFTER_CURRENT_SONG
            STOP_AFTER_CURRENT_SONG -> REPEAT_PLAYLIST
        }
}
