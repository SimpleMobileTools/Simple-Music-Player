package org.fossify.musicplayer.dialogs

import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.beGone
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.views.MyAppCompatCheckbox
import org.fossify.musicplayer.databinding.DialogManageVisibleTabsBinding
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.helpers.*

class ManageVisibleTabsDialog(val activity: BaseSimpleActivity, val callback: (result: Int) -> Unit) {
    private val binding by activity.viewBinding(DialogManageVisibleTabsBinding::inflate)
    private val tabs = LinkedHashMap<Int, MyAppCompatCheckbox>()

    init {
        tabs.apply {
            put(TAB_PLAYLISTS, binding.manageVisibleTabsPlaylists)
            put(TAB_FOLDERS, binding.manageVisibleTabsFolders)
            put(TAB_ARTISTS, binding.manageVisibleTabsArtists)
            put(TAB_ALBUMS, binding.manageVisibleTabsAlbums)
            put(TAB_TRACKS, binding.manageVisibleTabsTracks)
            put(TAB_GENRES, binding.manageVisibleTabsGenres)
        }

        if (!isQPlus()) {
            tabs.remove(TAB_FOLDERS)
            binding.manageVisibleTabsFolders.beGone()
        }

        val showTabs = activity.config.showTabs
        for ((key, value) in tabs) {
            value.isChecked = showTabs and key != 0
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(org.fossify.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this)
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        for ((key, value) in tabs) {
            if (value.isChecked) {
                result += key
            }
        }

        if (result == 0) {
            result = allTabsMask
        }

        callback(result)
    }
}
