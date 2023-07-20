package com.simplemobiletools.musicplayer.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TrackActivity
import com.simplemobiletools.musicplayer.adapters.TracksAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.mediaScanner
import com.simplemobiletools.musicplayer.extensions.resetQueueItems
import com.simplemobiletools.musicplayer.helpers.RESTART_PLAYER
import com.simplemobiletools.musicplayer.helpers.TAB_TRACKS
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.fragment_tracks.view.tracks_fastscroller
import kotlinx.android.synthetic.main.fragment_tracks.view.tracks_list
import kotlinx.android.synthetic.main.fragment_tracks.view.tracks_placeholder

// Artists -> Albums -> Tracks
class TracksFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var tracks = ArrayList<Track>()

    override fun setupFragment(activity: BaseSimpleActivity) {
        ensureBackgroundThread {
            Track.sorting = context.config.trackSorting
            tracks = context.audioHelper.getAllTracks()

            val excludedFolders = context.config.excludedFolders
            tracks = tracks.filter {
                !excludedFolders.contains(it.path.getParentPath())
            }.toMutableList() as ArrayList<Track>

            activity.runOnUiThread {
                val scanning = activity.mediaScanner.isScanning()
                tracks_placeholder.text = if (scanning) {
                    context.getString(R.string.loading_files)
                } else {
                    context.getString(R.string.no_items_found)
                }
                tracks_placeholder.beVisibleIf(tracks.isEmpty())
                val adapter = tracks_list.adapter
                if (adapter == null) {
                    TracksAdapter(activity, tracks, false, tracks_list) {
                        activity.hideKeyboard()
                        activity.handleNotificationPermission { granted ->
                            if (granted) {
                                activity.resetQueueItems(tracks) {
                                    Intent(activity, TrackActivity::class.java).apply {
                                        putExtra(TRACK, Gson().toJson(it))
                                        putExtra(RESTART_PLAYER, true)
                                        activity.startActivity(this)
                                    }
                                }
                            } else {
                                if (context is Activity) {
                                    PermissionRequiredDialog(activity, R.string.allow_notifications_music_player, { activity.openNotificationSettings() })
                                }
                            }
                        }
                    }.apply {
                        tracks_list.adapter = this
                    }

                    if (context.areSystemAnimationsEnabled) {
                        tracks_list.scheduleLayoutAnimation()
                    }
                } else {
                    (adapter as TracksAdapter).updateItems(tracks)
                }
            }
        }
    }

    override fun finishActMode() {
        (tracks_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = tracks.filter {
            it.title.contains(text, true) || ("${it.artist} - ${it.album}").contains(text, true)
        }.toMutableList() as ArrayList<Track>
        (tracks_list.adapter as? TracksAdapter)?.updateItems(filtered, text)
        tracks_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        (tracks_list.adapter as? TracksAdapter)?.updateItems(tracks)
        tracks_placeholder.beGoneIf(tracks.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_TRACKS) {
            val adapter = tracks_list.adapter as? TracksAdapter ?: return@ChangeSortingDialog
            val tracks = adapter.tracks
            Track.sorting = activity.config.trackSorting
            tracks.sort()
            adapter.updateItems(tracks, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        tracks_placeholder.setTextColor(textColor)
        tracks_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
