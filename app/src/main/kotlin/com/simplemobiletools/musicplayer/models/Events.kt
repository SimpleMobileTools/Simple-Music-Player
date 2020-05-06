package com.simplemobiletools.musicplayer.models

import java.util.*

class Events {
    class SongChanged(val song: Song?)
    class SongStateChanged(val isPlaying: Boolean)
    class PlaylistUpdated(val songs: ArrayList<Song>)
    class ProgressUpdated(val progress: Int)
    class SleepTimerChanged(val seconds: Int)
    class NoStoragePermission
}
