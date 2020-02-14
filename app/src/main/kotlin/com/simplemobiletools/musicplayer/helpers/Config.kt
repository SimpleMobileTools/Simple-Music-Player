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

    var equalizer: Int
        get() = prefs.getInt(EQUALIZER, 0)
        set(equalizer) = prefs.edit().putInt(EQUALIZER, equalizer).apply()

    var currentPlaylist: Int
        get() = prefs.getInt(CURRENT_PLAYLIST, ALL_SONGS_PLAYLIST_ID)
        set(currentPlaylist) = prefs.edit().putInt(CURRENT_PLAYLIST, currentPlaylist).apply()

    var repeatSong: Boolean
        get() = prefs.getBoolean(REPEAT_SONG, false)
        set(repeat) = prefs.edit().putBoolean(REPEAT_SONG, repeat).apply()

    var autoplay: Boolean
        get() = prefs.getBoolean(AUTOPLAY, true)
        set(autoplay) = prefs.edit().putBoolean(AUTOPLAY, autoplay).apply()

    var showAlbumCover: Boolean
        get() = prefs.getBoolean(SHOW_ALBUM_COVER, true)
        set(showAlbumCover) = prefs.edit().putBoolean(SHOW_ALBUM_COVER, showAlbumCover).apply()

    // initial playlist tries to load all songs from the device, store unwanted song paths here
    var ignoredPaths: Set<String>
        get() = prefs.getStringSet(IGNORED_PATHS, HashSet<String>())!!
        set(ignoredPaths) = prefs.edit().putStringSet(IGNORED_PATHS, ignoredPaths).apply()

    fun addIgnoredPaths(paths: ArrayList<String>) {
        val currIgnoredPaths = HashSet<String>(ignoredPaths)
        currIgnoredPaths.addAll(paths)
        ignoredPaths = currIgnoredPaths
    }

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
}
