package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
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
import com.simplemobiletools.musicplayer.models.AlbumHeader
import com.simplemobiletools.musicplayer.models.ListItem
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_album_header.view.album_artist
import kotlinx.android.synthetic.main.item_album_header.view.album_meta
import kotlinx.android.synthetic.main.item_album_header.view.album_title
import kotlinx.android.synthetic.main.item_track.view.*

class TracksHeaderAdapter(activity: SimpleActivity, items: ArrayList<ListItem>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<ListItem>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private val ITEM_HEADER = 0
    private val ITEM_TRACK = 1

    override val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_big).toInt()

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
        holder.bindView(item, allowClicks, allowClicks) { itemView, _ ->
            when (item) {
                is AlbumHeader -> setupHeader(itemView, item)
                else -> setupTrack(itemView, item as Track)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumHeader -> ITEM_HEADER
            else -> ITEM_TRACK
        }
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_rename).isVisible = shouldShowRename()
            findItem(R.id.cab_play_next).isVisible = shouldShowPlayNext()
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
            R.id.cab_share -> shareFiles()
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_select_all -> selectAll()
            R.id.cab_play_next -> playNextInQueue()
        }
    }

    override fun getSelectableItemCount() = items.size - 1

    override fun getIsItemSelectable(position: Int) = position != 0

    private fun askConfirmDelete() {
        ConfirmationDialog(ctx) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = items.indexOfFirst { it is Track && it.mediaStoreId == track.mediaStoreId }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                ctx.deleteTracks(selectedTracks) {
                    ctx.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            items.removeAt(it)
                        }

                        // finish activity if all tracks are deleted
                        if (items.none { it is Track }) {
                            ctx.finish()
                        }
                    }
                }
            }
        }
    }

    override fun getSelectedTracks() = getSelectedItems().filterIsInstance<Track>().toList()

    private fun setupTrack(view: View, track: Track) {
        view.apply {
            setupViewBackground(ctx)
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

            @SuppressLint("SetTextI18n")
            album_meta.text = "$year$tracks • ${header.duration.getFormattedDuration(true)}"

            arrayOf(album_title, album_artist, album_meta).forEach {
                it.setTextColor(textColor)
            }

            ensureBackgroundThread {
                val album = ctx.audioHelper.getAlbum(header.id)
                if (album != null) {
                    ctx.getAlbumCoverArt(album) { coverArt ->
                        loadImage(findViewById(R.id.album_image), coverArt, placeholderBig)
                    }
                } else {
                    ctx.runOnUiThread {
                        findViewById<ImageView>(R.id.album_image).setImageDrawable(placeholderBig)
                    }
                }
            }
        }
    }

    override fun onChange(position: Int): CharSequence {
        return when (val listItem = items.getOrNull(position)) {
            is Track -> listItem.getBubbleText(ctx.config.trackSorting)
            is AlbumHeader -> listItem.title
            else -> ""
        }
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(ctx, selectedTrack) { track ->
                val trackIndex = items.indexOfFirst { (it as? Track)?.mediaStoreId == track.mediaStoreId }
                if (trackIndex != -1) {
                    items[trackIndex] = track
                    notifyItemChanged(trackIndex)
                    finishActMode()
                }

                ctx.refreshQueueAndTracks(track)
            }
        }
    }
}
