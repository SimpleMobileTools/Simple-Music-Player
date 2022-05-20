package com.simplemobiletools.musicplayer.helpers

import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.models.Track
import net.bjoernpetersen.m3u.M3uParser
import net.bjoernpetersen.m3u.model.M3uEntry
import java.io.File

class M3uImporter(val activity: SimpleActivity) {

    fun importPlaylist(path: String, playListId: Int): List<Track> {
        val m3uEntries: List<M3uEntry> = M3uParser.parse(File(path).inputStream().reader())

        val deviceTracks = activity.tracksDAO.getAll()
            .filter { it.playListId == 0 }

        val playlistTracks = mutableListOf<Track>()
        for (entry in m3uEntries) {

            for (deviceTrack in deviceTracks) {
                if (entry.location.toString() == deviceTrack.path || entry.title == deviceTrack.title) {
                    val copy = deviceTrack.copy(id = 0, playListId = playListId)
                    playlistTracks.add(copy)
                }
            }
        }

        activity.tracksDAO.insertAll(playlistTracks)

        return playlistTracks
    }
}
