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
        get() = prefs.getInt(CURRENT_PLAYLIST, ALL_TRACKS_PLAYLIST_ID)
        set(currentPlaylist) = prefs.edit().putInt(CURRENT_PLAYLIST, currentPlaylist).apply()

    var repeatTrack: Boolean
        get() = prefs.getBoolean(REPEAT_TRACK, false)
        set(repeat) = prefs.edit().putBoolean(REPEAT_TRACK, repeat).apply()

    var autoplay: Boolean
        get() = prefs.getBoolean(AUTOPLAY, true)
        set(autoplay) = prefs.edit().putBoolean(AUTOPLAY, autoplay).apply()

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

    // update the way cover art is stored from version 5.4.0, do it only at upgrading from an older version
    var wereCoversUpdated: Boolean
        get() = prefs.getBoolean(WERE_COVERS_UPDATED, false)
        set(wereCoversUpdated) = prefs.edit().putBoolean(WERE_COVERS_UPDATED, wereCoversUpdated).apply()

    var wereInitialTracksFetched: Boolean
        get() = prefs.getBoolean(WERE_INITIAL_TRACKS_FETCHED, false)
        set(wereInitialTracksFetched) = prefs.edit().putBoolean(WERE_INITIAL_TRACKS_FETCHED, wereInitialTracksFetched).apply()

    var playlistSorting: Int
        get() = prefs.getInt(PLAYLIST_SORTING, PLAYER_SORT_BY_TITLE)
        set(playlistSorting) = prefs.edit().putInt(PLAYLIST_SORTING, playlistSorting).apply()

    var playlistTracksSorting: Int
        get() = prefs.getInt(PLAYLIST_TRACKS_SORTING, PLAYER_SORT_BY_TITLE)
        set(playlistTracksSorting) = prefs.edit().putInt(PLAYLIST_TRACKS_SORTING, playlistTracksSorting).apply()

    var artistSorting: Int
        get() = prefs.getInt(ARTIST_SORTING, PLAYER_SORT_BY_TITLE)
        set(artistSorting) = prefs.edit().putInt(ARTIST_SORTING, artistSorting).apply()

    var albumSorting: Int
        get() = prefs.getInt(ALBUM_SORTING, PLAYER_SORT_BY_TITLE)
        set(albumSorting) = prefs.edit().putInt(ALBUM_SORTING, albumSorting).apply()

    var trackSorting: Int
        get() = prefs.getInt(TRACK_SORTING, PLAYER_SORT_BY_TITLE)
        set(trackSorting) = prefs.edit().putInt(TRACK_SORTING, trackSorting).apply()
}
