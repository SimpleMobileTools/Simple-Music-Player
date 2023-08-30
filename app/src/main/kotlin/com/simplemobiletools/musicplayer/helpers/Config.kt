package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import com.simplemobiletools.commons.helpers.BaseConfig
import java.util.*

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

    fun saveCustomPlaylistSorting(playlistId: Int, value: Int) {
        prefs.edit().putInt(SORT_PLAYLIST_PREFIX + playlistId, value).apply()
    }

    fun getCustomPlaylistSorting(playlistId: Int) = prefs.getInt(SORT_PLAYLIST_PREFIX + playlistId, sorting)

    fun removeCustomPlaylistSorting(playlistId: Int) {
        prefs.edit().remove(SORT_PLAYLIST_PREFIX + playlistId).apply()
    }

    fun hasCustomPlaylistSorting(playlistId: Int) = prefs.contains(SORT_PLAYLIST_PREFIX + playlistId)

    fun getProperPlaylistSorting(playlistId: Int) = if (hasCustomPlaylistSorting(playlistId)) {
        getCustomPlaylistSorting(playlistId)
    } else {
        playlistTracksSorting
    }

    fun getProperFolderSorting(path: String) = if (hasCustomSorting(path)) {
        getFolderSorting(path)
    } else {
        playlistTracksSorting
    }

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

    var genreSorting: Int
        get() = prefs.getInt(GENRE_SORTING, PLAYER_SORT_BY_TITLE)
        set(genreSorting) = prefs.edit().putInt(GENRE_SORTING, genreSorting).apply()

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

    var wasAllTracksPlaylistCreated: Boolean
        get() = prefs.getBoolean(WAS_ALL_TRACKS_PLAYLIST_CREATED, false)
        set(wasAllTracksPlaylistCreated) = prefs.edit().putBoolean(WAS_ALL_TRACKS_PLAYLIST_CREATED, wasAllTracksPlaylistCreated).apply()

    var tracksRemovedFromAllTracksPlaylist: MutableSet<String>
        get() = prefs.getStringSet(TRACKS_REMOVED_FROM_ALL_TRACKS_PLAYLIST, HashSet())!!
        set(tracksRemovedFromAllTracksPlaylist) = prefs.edit().remove(TRACKS_REMOVED_FROM_ALL_TRACKS_PLAYLIST)
            .putStringSet(TRACKS_REMOVED_FROM_ALL_TRACKS_PLAYLIST, tracksRemovedFromAllTracksPlaylist)
            .apply()

    var showTabs: Int
        get() = prefs.getInt(SHOW_TABS, allTabsMask)
        set(showTabs) = prefs.edit().putInt(SHOW_TABS, showTabs).apply()

    var excludedFolders: MutableSet<String>
        get() = prefs.getStringSet(EXCLUDED_FOLDERS, HashSet())!!
        set(excludedFolders) = prefs.edit().remove(EXCLUDED_FOLDERS).putStringSet(EXCLUDED_FOLDERS, excludedFolders).apply()

    fun addExcludedFolder(path: String) {
        addExcludedFolders(HashSet(Arrays.asList(path)))
    }

    fun addExcludedFolders(paths: Set<String>) {
        val currExcludedFolders = HashSet(excludedFolders)
        currExcludedFolders.addAll(paths.map { it.removeSuffix("/") })
        excludedFolders = currExcludedFolders.filter { it.isNotEmpty() }.toHashSet()
    }

    fun removeExcludedFolder(path: String) {
        val currExcludedFolders = HashSet(excludedFolders)
        currExcludedFolders.remove(path)
        excludedFolders = currExcludedFolders
    }

    var gaplessPlayback: Boolean
        get() = prefs.getBoolean(GAPLESS_PLAYBACK, false)
        set(gaplessPlayback) = prefs.edit().putBoolean(GAPLESS_PLAYBACK, gaplessPlayback).apply()
}
