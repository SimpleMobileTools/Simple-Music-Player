package com.simplemobiletools.musicplayer.helpers

val SONG_POS = "song_position"
val PROGRESS = "progress"
val EDITED_SONG = "edited_song"

private val PATH = "com.simplemobiletools.musicplayer.action."

val INIT = PATH + "INIT"
val FINISH = PATH + "FINISH"
val PREVIOUS = PATH + "PREVIOUS"
val PAUSE = PATH + "PAUSE"
val PLAYPAUSE = PATH + "PLAYPAUSE"
val NEXT = PATH + "NEXT"
val EDIT = PATH + "EDIT"
val PLAYPOS = PATH + "PLAYPOS"
val REFRESH_LIST = PATH + "REFRESH_LIST"
val CALL_START = PATH + "CALL_START"
val CALL_STOP = PATH + "CALL_STOP"
val SET_PROGRESS = PATH + "SET_PROGRESS"
val SET_EQUALIZER = PATH + "SET_EQUALIZER"

// shared preferences
val SHUFFLE = "shuffle"
val SORTING = "track_sorting"
val EQUALIZER = "equalizer"
val REPEAT_SONG = "repeat_song"
val CURRENT_PLAYLIST = "current_playlist"
val WAS_INITIAL_PLAYLIST_FILLED = "was_initial_playlist_filled"

// sorting
val SORT_BY_TITLE = 1
val SORT_BY_ARTIST = 2
val SORT_BY_FILE_NAME = 4
val SORT_BY_DURATION = 8
