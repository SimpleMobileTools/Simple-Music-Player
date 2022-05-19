package com.simplemobiletools.musicplayer.helpers

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.writeLn
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.M3uExporter.ExportResult.*
import com.simplemobiletools.musicplayer.models.Track
import java.io.OutputStream

class M3uExporter {
    val HEADER = "#EXTM3U"
    val ENTRY = "#EXTINF:"
    val DURATION_SEPARATOR = ","

    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    fun exportPlaylist(
        activity: BaseSimpleActivity,
        outputStream: OutputStream?,
        tracks: ArrayList<Track>,
        callback: (result: ExportResult) -> Unit
    ) {
        if (outputStream == null) {
            callback(EXPORT_FAIL)
            return
        }

        //if (showExportingToast) {
        activity.toast(R.string.exporting)
        //}

        outputStream.bufferedWriter().use { out ->
            out.writeLn(HEADER)
            for (track in tracks) {
                out.writeLn(ENTRY + track.duration + DURATION_SEPARATOR + track.artist + " - " + track.title)
                out.writeLn(track.path)
            }

            out.close()
        }

        callback(EXPORT_OK)
    }
}
