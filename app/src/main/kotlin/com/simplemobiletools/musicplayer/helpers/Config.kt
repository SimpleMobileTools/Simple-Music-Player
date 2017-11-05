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

    var sorting: Int
        get() = prefs.getInt(SORTING, SORT_BY_TITLE)
        set(sorting) = prefs.edit().putInt(SORTING, sorting).apply()

    var equalizer: Int
        get() = prefs.getInt(EQUALIZER, 0)
        set(equalizer) = prefs.edit().putInt(EQUALIZER, equalizer).apply()

    var currentPlaylist: Int
        get() = prefs.getInt(CURRENT_PLAYLIST, DBHelper.ALL_SONGS_ID)
        set(currentPlaylist) = prefs.edit().putInt(CURRENT_PLAYLIST, currentPlaylist).apply()

    var repeatSong: Boolean
        get() = prefs.getBoolean(REPEAT_SONG, false)
        set(repeat) = prefs.edit().putBoolean(REPEAT_SONG, repeat).apply()

    var autoplay: Boolean
        get() = prefs.getBoolean(AUTOPLAY, true)
        set(autoplay) = prefs.edit().putBoolean(AUTOPLAY, autoplay).apply()

    // initial playlist tries to load all songs from the device, store unwanted song paths here
    var ignoredPaths: Set<String>
        get() = prefs.getStringSet(IGNORED_PATHS, HashSet<String>())
        set(ignoredPaths) = prefs.edit().putStringSet(IGNORED_PATHS, ignoredPaths).apply()

    fun addIgnoredPaths(paths: ArrayList<String>) {
        val currIgnoredPaths = HashSet<String>(ignoredPaths)
        currIgnoredPaths.addAll(paths)
        ignoredPaths = currIgnoredPaths
    }
}
