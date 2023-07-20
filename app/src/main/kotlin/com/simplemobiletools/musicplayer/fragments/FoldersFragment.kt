package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.ExcludedFoldersActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.FoldersAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.mediaScanner
import com.simplemobiletools.musicplayer.helpers.FOLDER
import com.simplemobiletools.musicplayer.helpers.TAB_FOLDERS
import com.simplemobiletools.musicplayer.models.Folder
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.fragment_folders.view.folders_fastscroller
import kotlinx.android.synthetic.main.fragment_folders.view.folders_list
import kotlinx.android.synthetic.main.fragment_folders.view.folders_placeholder
import kotlinx.android.synthetic.main.fragment_folders.view.folders_placeholder_2

class FoldersFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var foldersIgnoringSearch = ArrayList<Folder>()

    override fun setupFragment(activity: BaseSimpleActivity) {
        ensureBackgroundThread {
            val tracks = context.audioHelper.getAllTracks()
            val foldersMap = tracks.groupBy { it.folderName }
            val folders = ArrayList<Folder>()
            val excludedFolders = activity.config.excludedFolders
            for ((title, folderTracks) in foldersMap) {
                val path = (folderTracks.firstOrNull()?.path?.getParentPath() ?: "").removeSuffix("/")
                if (excludedFolders.contains(path)) {
                    continue
                }

                val folder = Folder(title, folderTracks.size, path)
                folders.add(folder)

                if (!context.config.wereTrackFoldersAdded) {
                    folderTracks.forEach {
                        context.audioHelper.updateTrackFolder(title, it.mediaStoreId)
                    }
                }
            }

            context.config.wereTrackFoldersAdded = true

            activity.runOnUiThread {
                val scanning = activity.mediaScanner.isScanning()
                folders_placeholder.text = if (scanning) {
                    context.getString(R.string.loading_files)
                } else {
                    context.getString(R.string.no_items_found)
                }
                folders_placeholder.beVisibleIf(folders.isEmpty())
                folders_fastscroller.beGoneIf(folders_placeholder.isVisible())
                folders_placeholder_2.beVisibleIf(folders.isEmpty() && context.config.excludedFolders.isNotEmpty() && !scanning)
                folders_placeholder_2.underlineText()

                folders_placeholder_2.setOnClickListener {
                    activity.startActivity(Intent(activity, ExcludedFoldersActivity::class.java))
                }

                Folder.sorting = activity.config.folderSorting
                folders.sort()
                foldersIgnoringSearch = folders

                val adapter = folders_list.adapter
                if (adapter == null) {
                    FoldersAdapter(activity, folders, folders_list) {
                        activity.hideKeyboard()
                        Intent(activity, TracksActivity::class.java).apply {
                            putExtra(FOLDER, (it as Folder).title)
                            activity.startActivity(this)
                        }
                    }.apply {
                        folders_list.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        folders_list.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as FoldersAdapter).updateItems(folders)
                }
            }
        }
    }

    override fun finishActMode() {
        (folders_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = foldersIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Folder>
        (folders_list.adapter as? FoldersAdapter)?.updateItems(filtered, text)
        folders_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        (folders_list.adapter as? FoldersAdapter)?.updateItems(foldersIgnoringSearch)
        folders_placeholder.beGoneIf(foldersIgnoringSearch.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_FOLDERS) {
            val adapter = folders_list.adapter as? FoldersAdapter ?: return@ChangeSortingDialog
            val folders = adapter.folders
            Folder.sorting = activity.config.folderSorting
            folders.sort()
            adapter.updateItems(folders, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        folders_placeholder.setTextColor(textColor)
        folders_fastscroller.updateColors(adjustedPrimaryColor)
        folders_placeholder_2.setTextColor(adjustedPrimaryColor)
    }
}
