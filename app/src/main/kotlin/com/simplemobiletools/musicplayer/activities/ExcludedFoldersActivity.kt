package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.interfaces.RefreshRecyclerViewListener
import com.simplemobiletools.musicplayer.adapters.ExcludedFoldersAdapter
import com.simplemobiletools.musicplayer.databinding.ActivityExcludedFoldersBinding
import com.simplemobiletools.musicplayer.extensions.config

class ExcludedFoldersActivity : SimpleActivity(), RefreshRecyclerViewListener {

    private val binding by viewBinding(ActivityExcludedFoldersBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.excludedFoldersCoordinator, binding.excludedFoldersList, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.excludedFoldersList, binding.excludedFoldersToolbar)
        updateFolders()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.excludedFoldersToolbar, NavigationIcon.Arrow)
    }

    private fun updateFolders() {
        val folders = config.excludedFolders.toMutableList() as ArrayList<String>
        binding.excludedFoldersPlaceholder.apply {
            beVisibleIf(folders.isEmpty())
            setTextColor(getProperTextColor())
        }

        val adapter = ExcludedFoldersAdapter(this, folders, this, binding.excludedFoldersList) {}
        binding.excludedFoldersList.adapter = adapter
    }

    override fun refreshItems() {
        updateFolders()
    }
}
