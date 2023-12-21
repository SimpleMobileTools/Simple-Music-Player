package org.fossify.musicplayer.dialogs

import androidx.appcompat.app.AlertDialog
import org.fossify.commons.dialogs.FilePickerDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.databinding.DialogExportPlaylistBinding
import org.fossify.musicplayer.extensions.config
import java.io.File

class ExportPlaylistDialog(
    val activity: SimpleActivity,
    val path: String,
    val hidePath: Boolean,
    private val callback: (file: File) -> Unit
) {
    private var ignoreClicks = false
    private var realPath = path.ifEmpty { activity.internalStoragePath }
    private val binding by activity.viewBinding(DialogExportPlaylistBinding::inflate)

    init {
        binding.apply {
            exportPlaylistFolder.text = activity.humanizePath(realPath)

            val fileName = "playlist_${activity.getCurrentFormattedDateTime()}"
            exportPlaylistFilename.setText(fileName)

            if (hidePath) {
                exportPlaylistFolderLabel.beGone()
                exportPlaylistFolder.beGone()
            } else {
                exportPlaylistFolder.setOnClickListener {
                    activity.hideKeyboard(exportPlaylistFilename)
                    FilePickerDialog(activity, realPath, false, showFAB = true) {
                        exportPlaylistFolder.text = activity.humanizePath(it)
                        realPath = it
                    }
                }
            }
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.export_playlist) { alertDialog ->
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val filename = binding.exportPlaylistFilename.value
                        when {
                            filename.isEmpty() -> activity.toast(org.fossify.commons.R.string.empty_name)
                            filename.isAValidFilename() -> {
                                val file = File(realPath, "$filename.m3u")
                                if (!hidePath && file.exists()) {
                                    activity.toast(org.fossify.commons.R.string.name_taken)
                                    return@setOnClickListener
                                }

                                ignoreClicks = true
                                ensureBackgroundThread {
                                    activity.config.lastExportPath = file.absolutePath.getParentPath()
                                    callback(file)
                                    alertDialog.dismiss()
                                }
                            }

                            else -> activity.toast(org.fossify.commons.R.string.invalid_name)
                        }
                    }
                }
            }
    }
}
