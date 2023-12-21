package org.fossify.musicplayer.fragments

import android.app.Activity
import android.content.Context
import android.util.AttributeSet
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.musicplayer.R
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.adapters.TracksAdapter
import org.fossify.musicplayer.databinding.FragmentTracksBinding
import org.fossify.musicplayer.dialogs.ChangeSortingDialog
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.mediaScanner
import org.fossify.musicplayer.extensions.viewBinding
import org.fossify.musicplayer.helpers.TAB_TRACKS
import org.fossify.musicplayer.models.Track
import org.fossify.musicplayer.models.sortSafely

// Artists -> Albums -> Tracks
class TracksFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var tracks = ArrayList<Track>()
    private val binding by viewBinding(FragmentTracksBinding::bind)

    override fun setupFragment(activity: BaseSimpleActivity) {
        ensureBackgroundThread {
            tracks = context.audioHelper.getAllTracks()

            val excludedFolders = context.config.excludedFolders
            tracks = tracks.filter {
                !excludedFolders.contains(it.path.getParentPath())
            }.toMutableList() as ArrayList<Track>

            activity.runOnUiThread {
                val scanning = activity.mediaScanner.isScanning()
                binding.tracksPlaceholder.text = if (scanning) {
                    context.getString(R.string.loading_files)
                } else {
                    context.getString(org.fossify.commons.R.string.no_items_found)
                }
                binding.tracksPlaceholder.beVisibleIf(tracks.isEmpty())
                val adapter = binding.tracksList.adapter
                if (adapter == null) {
                    TracksAdapter(activity = activity, recyclerView = binding.tracksList, sourceType = TracksAdapter.TYPE_TRACKS, items = tracks) {
                        activity.hideKeyboard()
                        activity.handleNotificationPermission { granted ->
                            if (granted) {
                                val startIndex = tracks.indexOf(it as Track)
                                prepareAndPlay(tracks, startIndex)
                            } else {
                                if (context is Activity) {
                                    PermissionRequiredDialog(
                                        activity,
                                        org.fossify.commons.R.string.allow_notifications_music_player,
                                        { activity.openNotificationSettings() }
                                    )
                                }
                            }
                        }
                    }.apply {
                        binding.tracksList.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        binding.tracksList.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as TracksAdapter).updateItems(tracks)
                }
            }
        }
    }

    override fun finishActMode() {
        getAdapter()?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = tracks.filter {
            it.title.contains(text, true) || ("${it.artist} - ${it.album}").contains(text, true)
        }.toMutableList() as ArrayList<Track>
        getAdapter()?.updateItems(filtered, text)
        binding.tracksPlaceholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        getAdapter()?.updateItems(tracks)
        binding.tracksPlaceholder.beGoneIf(tracks.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_TRACKS) {
            val adapter = getAdapter() ?: return@ChangeSortingDialog
            tracks.sortSafely(activity.config.trackSorting)
            adapter.updateItems(tracks, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        binding.tracksPlaceholder.setTextColor(textColor)
        binding.tracksFastscroller.updateColors(adjustedPrimaryColor)
        getAdapter()?.updateColors(textColor)
    }

    private fun getAdapter() = binding.tracksList.adapter as? TracksAdapter
}
