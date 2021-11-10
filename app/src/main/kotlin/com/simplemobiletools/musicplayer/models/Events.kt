package com.simplemobiletools.musicplayer.models

import java.util.*

class Events {
    class TrackChanged(val track: Track?)
    class NextTrackChanged(val track: Track?)
    class TrackStateChanged(val isPlaying: Boolean)
    class QueueUpdated(val tracks: ArrayList<Track>)
    class ProgressUpdated(val progress: Int)
    class SleepTimerChanged(val seconds: Int)
    class PlaylistsUpdated
    class TrackDeleted
    class NoStoragePermission
    class RefreshTracks
}
