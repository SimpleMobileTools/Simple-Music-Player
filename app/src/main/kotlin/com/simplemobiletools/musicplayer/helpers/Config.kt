package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    var isShuffleEnabled: Boolean
        get() = prefs.getBoolean(SHUFFLE, true)
        set(shuffle) = prefs.edit().putBoolean(SHUFFLE, shuffle).apply()

    var playbackSetting: PlaybackSetting
        get() = PlaybackSetting.values()[prefs.getInt(PLAYBACK_SETTING, PlaybackSetting.REPEAT_OFF.ordinal)]
        set(playbackSetting) = prefs.edit().putInt(PLAYBACK_SETTING, playbackSetting.ordinal).apply()

    var autoplay: Boolean
        get() = prefs.getBoolean(AUTOPLAY, true)
        set(autoplay) = prefs.edit().putBoolean(AUTOPLAY, autoplay).apply()

    var showFilename: Int
        get() = prefs.getInt(SHOW_FILENAME, SHOW_FILENAME_IF_UNAVAILABLE)
        set(showFilename) = prefs.edit().putInt(SHOW_FILENAME, showFilename).apply()

    var swapPrevNext: Boolean
        get() = prefs.getBoolean(SWAP_PREV_NEXT, false)
        set(swapPrevNext) = prefs.edit().putBoolean(SWAP_PREV_NEXT, swapPrevNext).apply()

    var lastSleepTimerSeconds: Int
        get() = prefs.getInt(LAST_SLEEP_TIMER_SECONDS, 30 * 60)
        set(lastSleepTimerSeconds) = prefs.edit().putInt(LAST_SLEEP_TIMER_SECONDS, lastSleepTimerSeconds).apply()

    var sleepInTS: Long
        get() = prefs.getLong(SLEEP_IN_TS, 0)
        set(sleepInTS) = prefs.edit().putLong(SLEEP_IN_TS, sleepInTS).apply()

    var playlistSorting: Int
        get() = prefs.getInt(PLAYLIST_SORTING, PLAYER_SORT_BY_TITLE)
        set(playlistSorting) = prefs.edit().putInt(PLAYLIST_SORTING, playlistSorting).apply()

    var playlistTracksSorting: Int
        get() = prefs.getInt(PLAYLIST_TRACKS_SORTING, PLAYER_SORT_BY_TITLE)
        set(playlistTracksSorting) = prefs.edit().putInt(PLAYLIST_TRACKS_SORTING, playlistTracksSorting).apply()

    var folderSorting: Int
        get() = prefs.getInt(FOLDER_SORTING, PLAYER_SORT_BY_TITLE)
        set(folderSorting) = prefs.edit().putInt(FOLDER_SORTING, folderSorting).apply()

    var artistSorting: Int
        get() = prefs.getInt(ARTIST_SORTING, PLAYER_SORT_BY_TITLE)
        set(artistSorting) = prefs.edit().putInt(ARTIST_SORTING, artistSorting).apply()

    var albumSorting: Int
        get() = prefs.getInt(ALBUM_SORTING, PLAYER_SORT_BY_TITLE)
        set(albumSorting) = prefs.edit().putInt(ALBUM_SORTING, albumSorting).apply()

    var trackSorting: Int
        get() = prefs.getInt(TRACK_SORTING, PLAYER_SORT_BY_TITLE)
        set(trackSorting) = prefs.edit().putInt(TRACK_SORTING, trackSorting).apply()

    var equalizerPreset: Int
        get() = prefs.getInt(EQUALIZER_PRESET, 0)
        set(equalizerPreset) = prefs.edit().putInt(EQUALIZER_PRESET, equalizerPreset).apply()

    var equalizerBands: String
        get() = prefs.getString(EQUALIZER_BANDS, "")!!
        set(equalizerBands) = prefs.edit().putString(EQUALIZER_BANDS, equalizerBands).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat(PLAYBACK_SPEED, 1f)
        set(playbackSpeed) = prefs.edit().putFloat(PLAYBACK_SPEED, playbackSpeed).apply()

    var playbackSpeedProgress: Int
        get() = prefs.getInt(PLAYBACK_SPEED_PROGRESS, -1)
        set(playbackSpeedProgress) = prefs.edit().putInt(PLAYBACK_SPEED_PROGRESS, playbackSpeedProgress).apply()

    var wereTrackFoldersAdded: Boolean
        get() = prefs.getBoolean(WERE_TRACK_FOLDERS_ADDED, false)
        set(wereTrackFoldersAdded) = prefs.edit().putBoolean(WERE_TRACK_FOLDERS_ADDED, wereTrackFoldersAdded).apply()

    var wasAllTracksPlaylistCreated: Boolean
        get() = prefs.getBoolean(WAS_ALL_TRACKS_PLAYLIST_CREATED, false)
        set(wasAllTracksPlaylistCreated) = prefs.edit().putBoolean(WAS_ALL_TRACKS_PLAYLIST_CREATED, wasAllTracksPlaylistCreated).apply()

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, allTabsMask)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()
}
