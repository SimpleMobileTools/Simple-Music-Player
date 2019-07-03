package com.simplemobiletools.musicplayer.models

import java.util.*

class Events {
    class SongChanged internal constructor(val song: Song?)

    class SongStateChanged internal constructor(val isPlaying: Boolean)

    class PlaylistUpdated internal constructor(val songs: ArrayList<Song>)

    class ProgressUpdated internal constructor(val progress: Int)

    class SleepTimerChanged internal constructor(val seconds: Int)

    class NoStoragePermission internal constructor()
}
