package com.simplemobiletools.musicplayer.adapters

import android.content.ContentUris
import android.provider.MediaStore
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.dialogs.PropertiesDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.FastScroller
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.addTracksToPlaylist
import com.simplemobiletools.musicplayer.extensions.addTracksToQueue
import com.simplemobiletools.musicplayer.extensions.deleteTracks
import com.simplemobiletools.musicplayer.extensions.tracksDAO
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_track.view.*
import org.greenrobot.eventbus.EventBus
import java.util.*

class TracksAdapter(activity: SimpleActivity, var tracks: ArrayList<Track>, val isPlaylistContent: Boolean, recyclerView: MyRecyclerView,
                    fastScroller: FastScroller, itemClick: (Any) -> Unit) : MyRecyclerViewAdapter(activity, recyclerView, fastScroller, itemClick) {

    private var textToHighlight = ""
    private val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, textColor)
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_tracks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_track, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = tracks.getOrNull(position) ?: return
        holder.bindView(track, true, true) { itemView, layoutPosition ->
            setupView(itemView, track)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = tracks.size

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_remove_from_playlist).isVisible = isPlaylistContent
        }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_properties -> showProperties()
            R.id.cab_remove_from_playlist -> removeFromPlaylist()
            R.id.cab_delete -> askConfirmDelete()
        }
    }

    override fun getSelectableItemCount() = tracks.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = tracks.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = tracks.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun addToPlaylist() {
        activity.addTracksToPlaylist(getSelectedTracks()) {
            finishActMode()
            notifyDataSetChanged()
        }
    }

    private fun addToQueue() {
        activity.addTracksToQueue(getSelectedTracks()) {
            finishActMode()
        }
    }

    private fun showProperties() {
        val selectedTracks = getSelectedTracks()
        val selectedPaths = selectedTracks.map { track ->
            if (track.path.isNotEmpty()) {
                track.path
            } else {
                ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, track.mediaStoreId).toString()
            }
        }

        if (selectedPaths.size <= 1) {
            PropertiesDialog(activity, selectedPaths.first(), false)
        } else {
            PropertiesDialog(activity, selectedPaths, false)
        }
    }

    private fun removeFromPlaylist() {
        ensureBackgroundThread {
            val positions = ArrayList<Int>()
            val selectedTracks = getSelectedTracks()
            selectedTracks.forEach { track ->
                val position = tracks.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                if (position != -1) {
                    positions.add(position)
                }
            }

            activity.tracksDAO.removeSongsFromPlaylists(selectedTracks)
            EventBus.getDefault().post(Events.PlaylistsUpdated())
            activity.runOnUiThread {
                positions.sortDescending()
                removeSelectedItems(positions)
                positions.forEach {
                    tracks.removeAt(it)
                }
            }
        }
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = tracks.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                activity.deleteTracks(selectedTracks) {
                    activity.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (tracks.size > it) {
                                tracks.removeAt(it)
                            }
                        }

                        finishActMode()
                    }
                }
            }
        }
    }

    private fun getSelectedTracks(): List<Track> = tracks.filter { selectedKeys.contains(it.hashCode()) }.toList()

    fun updateItems(newItems: ArrayList<Track>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != tracks.hashCode()) {
            tracks = newItems.clone() as ArrayList<Track>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
        fastScroller?.measureRecyclerView()
    }

    private fun setupView(view: View, track: Track) {
        view.apply {
            track_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_title.text = if (textToHighlight.isEmpty()) track.title else track.title.highlightTextPart(textToHighlight, adjustedPrimaryColor)

            arrayOf(track_id, track_title, track_duration).forEach {
                it.setTextColor(textColor)
            }

            track_duration.text = track.duration.getFormattedDuration()
            val options = RequestOptions()
                .error(placeholder)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))

            Glide.with(activity)
                .load(track.coverArt)
                .apply(options)
                .into(findViewById(R.id.track_image))

            track_image.beVisible()
            track_id.beGone()
        }
    }
}
