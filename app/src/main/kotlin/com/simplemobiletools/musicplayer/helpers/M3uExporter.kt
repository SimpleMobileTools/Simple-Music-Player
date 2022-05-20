package com.simplemobiletools.musicplayer.helpers

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.commons.extensions.writeLn
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.M3uExporter.ExportResult.EXPORT_FAIL
import com.simplemobiletools.musicplayer.helpers.M3uExporter.ExportResult.EXPORT_OK
import com.simplemobiletools.musicplayer.models.Track
import java.io.OutputStream

class M3uExporter(val activity: BaseSimpleActivity) {

    enum class ExportResult {
        EXPORT_FAIL, EXPORT_OK, EXPORT_PARTIAL
    }

    fun exportPlaylist(
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
            out.writeLn(M3U_HEADER)
            for (track in tracks) {
                out.writeLn(M3U_ENTRY + track.duration + M3U_DURATION_SEPARATOR + track.artist + " - " + track.title)
                out.writeLn(track.path)
            }

            out.close()
        }

        callback(EXPORT_OK)
    }
}
