package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.musicplayer.R
import kotlinx.android.synthetic.main.activity_excluded_folders.*

class ExcludedFoldersActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_excluded_folders)
        updateFolders()
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(excluded_folders_toolbar, NavigationIcon.Arrow)
    }

    private fun updateFolders() {

    }
}
