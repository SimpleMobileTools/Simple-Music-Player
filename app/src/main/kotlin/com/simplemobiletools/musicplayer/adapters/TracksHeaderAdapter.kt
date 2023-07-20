package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.beGone
import com.simplemobiletools.commons.extensions.beVisible
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.TagHelper
import com.simplemobiletools.musicplayer.models.AlbumHeader
import com.simplemobiletools.musicplayer.models.ListItem
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_album_header.view.album_artist
import kotlinx.android.synthetic.main.item_album_header.view.album_meta
import kotlinx.android.synthetic.main.item_album_header.view.album_title
import kotlinx.android.synthetic.main.item_track.view.*

class TracksHeaderAdapter(activity: SimpleActivity, var items: ArrayList<ListItem>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private val ITEM_HEADER = 0
    private val ITEM_TRACK = 1

    private val placeholder = resources.getBiggerPlaceholder(textColor)
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_big).toInt()
    private val tagHelper by lazy { TagHelper(activity) }

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_tracks_header

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            ITEM_HEADER -> R.layout.item_album_header
            else -> R.layout.item_track
        }

        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        val allowClicks = item !is AlbumHeader
        holder.bindView(item, allowClicks, allowClicks) { itemView, layoutPosition ->
            when (item) {
                is AlbumHeader -> setupHeader(itemView, item)
                else -> setupTrack(itemView, item as Track)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumHeader -> ITEM_HEADER
            else -> ITEM_TRACK
        }
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            val oneItemsSelected = isOneItemSelected()
            val selected = getSelectedTracks().firstOrNull()?.let { !it.path.startsWith("content://") && tagHelper.isEditTagSupported(it) } == true
            findItem(R.id.cab_rename).isVisible = oneItemsSelected && selected
            findItem(R.id.cab_play_next).isVisible =
                isOneItemSelected() && MusicService.mCurrTrack != getSelectedTracks().firstOrNull() && MusicService.mCurrTrack !== null
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
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_select_all -> selectAll()
            R.id.cab_play_next -> playNext()
        }
    }

    override fun getSelectableItemCount() = items.size - 1

    override fun getIsItemSelectable(position: Int) = position != 0

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.hashCode() == key }

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

    private fun playNext() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            activity.playNextInQueue(selectedTrack) {
                finishActMode()
            }
        }
    }

    private fun showProperties() {
        val selectedTracks = getSelectedTracks()
        activity.showTrackProperties(selectedTracks)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = items.indexOfFirst { it is Track && it.mediaStoreId == track.mediaStoreId }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                activity.deleteTracks(selectedTracks) {
                    activity.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            items.removeAt(it)
                        }
                    }
                }
            }
        }
    }

    private fun getSelectedTracks(): List<Track> = items.filter { it is Track && selectedKeys.contains(it.hashCode()) }.toList() as List<Track>

    fun updateItems(newItems: ArrayList<ListItem>) {
        if (newItems.hashCode() != items.hashCode()) {
            items = newItems.clone() as ArrayList<ListItem>
            notifyDataSetChanged()
            finishActMode()
        }
    }

    private fun setupTrack(view: View, track: Track) {
        view.apply {
            setupViewBackground(activity)
            track_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_title.text = track.title
            track_info.beGone()

            arrayOf(track_id, track_title, track_duration).forEach {
                it.setTextColor(textColor)
            }

            track_duration.text = track.duration.getFormattedDuration()
            track_id.text = track.trackId.toString()
            track_image.beGone()
            track_id.beVisible()
        }
    }

    private fun setupHeader(view: View, header: AlbumHeader) {
        view.apply {
            album_title.text = header.title
            album_artist.text = header.artist

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, header.trackCnt, header.trackCnt)
            var year = ""
            if (header.year != 0) {
                year = "${header.year} • "
            }

            album_meta.text = "$year$tracks • ${header.duration.getFormattedDuration(true)}"

            arrayOf(album_title, album_artist, album_meta).forEach {
                it.setTextColor(textColor)
            }

            val options = RequestOptions()
                .error(placeholder)
                .transform(CenterCrop(), RoundedCorners(cornerRadius))

            ensureBackgroundThread {
                val album = activity.audioHelper.getAlbum(header.id)
                if (album != null) {
                    activity.getAlbumCoverArt(album) { coverArt ->
                        activity.ensureActivityNotDestroyed {
                            Glide.with(activity)
                                .load(coverArt)
                                .apply(options)
                                .into(findViewById(R.id.album_image))
                        }
                    }
                } else {
                    activity.runOnUiThread {
                        findViewById<ImageView>(R.id.album_image).setImageDrawable(placeholder)
                    }
                }
            }
        }
    }

    override fun onChange(position: Int): CharSequence {
        val listItem = items.getOrNull(position)
        return when (listItem) {
            is Track -> listItem.getBubbleText()
            is AlbumHeader -> listItem.title
            else -> ""
        }
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(activity as SimpleActivity, selectedTrack) { track ->
                val trackIndex = items.indexOfFirst { (it as? Track)?.mediaStoreId == track.mediaStoreId }
                if (trackIndex != -1) {
                    items[trackIndex] = track
                    notifyItemChanged(trackIndex)
                    finishActMode()
                }

                activity.refreshAfterEdit(track)
            }
        }
    }
}
