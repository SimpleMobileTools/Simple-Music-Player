package org.fossify.musicplayer.activities

import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.interfaces.RefreshRecyclerViewListener
import org.fossify.musicplayer.adapters.ExcludedFoldersAdapter
import org.fossify.musicplayer.databinding.ActivityExcludedFoldersBinding
import org.fossify.musicplayer.extensions.config

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
