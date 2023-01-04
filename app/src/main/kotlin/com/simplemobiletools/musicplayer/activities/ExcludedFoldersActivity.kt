package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.ExcludedFoldersAdapter
import com.simplemobiletools.musicplayer.extensions.config
import kotlinx.android.synthetic.main.activity_excluded_folders.*

class ExcludedFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excluded_folders)

        updateMaterialActivityViews(excluded_folders_coordinator, excluded_folders_list, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(excluded_folders_list, excluded_folders_toolbar)
        updateFolders()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(excluded_folders_toolbar, NavigationIcon.Arrow)
    }

    private fun updateFolders() {
        val folders = config.excludedFolders.toMutableList() as ArrayList<String>
        excluded_folders_placeholder.apply {
            beVisibleIf(folders.isEmpty())
            setTextColor(getProperTextColor())
        }

        val adapter = ExcludedFoldersAdapter(this, folders, this, excluded_folders_list) {}
        excluded_folders_list.adapter = adapter
    }

    override fun refreshItems() {
        updateFolders()
    }
}
