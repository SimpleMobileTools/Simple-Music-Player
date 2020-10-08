package com.simplemobiletools.musicplayer.models

import java.util.*

class Events {
    class SongChanged(val song: Track?)
    class SongStateChanged(val isPlaying: Boolean)
    class PlaylistUpdated(val songs: ArrayList<Track>)
    class ProgressUpdated(val progress: Int)
    class SleepTimerChanged(val seconds: Int)
    class NoStoragePermission
}
