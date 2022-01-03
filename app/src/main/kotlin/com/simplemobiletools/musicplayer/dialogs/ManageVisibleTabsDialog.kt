package com.simplemobiletools.musicplayer.dialogs

import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.commons.views.MyAppCompatCheckbox
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.*
import kotlinx.android.synthetic.main.dialog_manage_visible_tabs.view.*

class ManageVisibleTabsDialog(val activity: BaseSimpleActivity) {
    private var view = activity.layoutInflater.inflate(R.layout.dialog_manage_visible_tabs, null)
    private val tabs = LinkedHashMap<Int, Int>()

    init {
        tabs.apply {
            put(TAB_PLAYLISTS, R.id.manage_visible_tabs_playlists)
            put(TAB_FOLDERS, R.id.manage_visible_tabs_folders)
            put(TAB_ARTISTS, R.id.manage_visible_tabs_artists)
            put(TAB_ALBUMS, R.id.manage_visible_tabs_albums)
            put(TAB_TRACKS, R.id.manage_visible_tabs_tracks)
        }

        if (!isQPlus()) {
            tabs.remove(TAB_FOLDERS)
            view.manage_visible_tabs_folders.beGone()
        }

        val showTabs = activity.config.showTabs
        for ((key, value) in tabs) {
            view.findViewById<MyAppCompatCheckbox>(value).isChecked = showTabs and key != 0
        }

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this)
            }
    }

    private fun dialogConfirmed() {
        var result = 0
        for ((key, value) in tabs) {
            if (view.findViewById<MyAppCompatCheckbox>(value).isChecked) {
                result += key
            }
        }

        if (result == 0) {
            result = allTabsMask
        }

        activity.config.showTabs = result
    }
}
