package com.simplemobiletools.musicplayer.dialogs

import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.config
import kotlinx.android.synthetic.main.dialog_export_playlist.view.*
import java.io.File

class ExportPlaylistDialog(
    val activity: SimpleActivity,
    val playlistName: String,
    val path: String,
    val hidePath: Boolean,
    private val callback: (file: File) -> Unit
) {
    private var ignoreClicks = false
    private var realPath = if (path.isEmpty()) activity.internalStoragePath else path

    init {
        val view = (activity.layoutInflater.inflate(R.layout.dialog_export_playlist, null) as ViewGroup).apply {
            export_playlist_folder.text = activity.humanizePath(realPath)

            // TODO: format name
            val fileName = "${playlistName}_playlist_${activity.getCurrentFormattedDateTime()}"
            export_playlist_filename.setText(fileName)

            if (hidePath) {
                export_playlist_folder_label.beGone()
                export_playlist_folder.beGone()
            } else {
                export_playlist_folder.setOnClickListener {
                    activity.hideKeyboard(export_playlist_filename)
                    FilePickerDialog(activity, realPath, false, showFAB = true) {
                        export_playlist_folder.text = activity.humanizePath(it)
                        realPath = it
                    }
                }
            }

        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.export_playlist) {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = view.export_playlist_filename.value
                        when {
                            filename.isEmpty() -> activity.toast(R.string.empty_name)
                            filename.isAValidFilename() -> {
                                val file = File(realPath, "$filename.m3u")
                                if (!hidePath && file.exists()) {
                                    activity.toast(R.string.name_taken)
                                    return@setOnClickListener
                                }

                                ignoreClicks = true
                                ensureBackgroundThread {
                                    activity.config.lastExportPath = file.absolutePath.getParentPath()
                                    callback(file)
                                    dismiss()
                                }
                            }
                            else -> activity.toast(R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
