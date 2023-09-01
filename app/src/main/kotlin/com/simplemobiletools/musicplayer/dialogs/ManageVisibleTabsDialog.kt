package com.simplemobiletools.musicplayer.dialogs

import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.views.MyAppCompatCheckbox
import com.simplemobiletools.musicplayer.databinding.DialogManageVisibleTabsBinding
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.*

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
            .setPositiveButton(com.simplemobiletools.commons.R.string.ok) { _, _ -> dialogConfirmed() }
            .setNegativeButton(com.simplemobiletools.commons.R.string.cancel, null)
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
