package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
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
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.AlbumSection
import com.simplemobiletools.musicplayer.models.ListItem
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.playback.PlaybackService
import kotlinx.android.synthetic.main.item_album.view.album_frame
import kotlinx.android.synthetic.main.item_album.view.album_title
import kotlinx.android.synthetic.main.item_album.view.album_tracks
import kotlinx.android.synthetic.main.item_section.view.item_section
import kotlinx.android.synthetic.main.item_track.view.*

// we show both albums and individual tracks here
class AlbumsTracksAdapter(
    activity: SimpleActivity, items: ArrayList<ListItem>, recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : BaseMusicAdapter<ListItem>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private val ITEM_SECTION = 0
    private val ITEM_ALBUM = 1
    private val ITEM_TRACK = 2

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_albums_tracks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            ITEM_SECTION -> R.layout.item_section
            ITEM_ALBUM -> R.layout.item_album
            else -> R.layout.item_track
        }

        return createViewHolder(layout, parent)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        val allowClicks = item !is AlbumSection
        holder.bindView(item, allowClicks, allowClicks) { itemView, _ ->
            when (item) {
                is AlbumSection -> setupSection(itemView, item)
                is Album -> setupAlbum(itemView, item)
                else -> setupTrack(itemView, item as Track)
            }
        }
        bindViewHolder(holder)
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is AlbumSection -> ITEM_SECTION
            is Album -> ITEM_ALBUM
            else -> ITEM_TRACK
        }
    }

    override fun prepareActionMode(menu: Menu) {
        val firstTrack = getSelectedTracks().firstOrNull()
        menu.apply {
            findItem(R.id.cab_play_next).isVisible =
                isOneItemSelected() &&
                    PlaybackService.currentMediaItem != null &&
                    PlaybackService.currentMediaItem!!.mediaId != firstTrack?.mediaStoreId.toString() &&
                    firstTrack is Track
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

    override fun getSelectableItemCount() = items.filter { it !is AlbumSection }.size

    override fun getIsItemSelectable(position: Int) = items[position] !is AlbumSection

    private fun askConfirmDelete() {
        ConfirmationDialog(ctx) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getAllSelectedTracks()
                val selectedAlbums = getSelectedAlbums()

                positions += selectedTracks.mapNotNull { track -> items.indexOfFirstOrNull { it is Track && it.mediaStoreId == track.mediaStoreId } }
                positions += selectedAlbums.mapNotNull { album -> items.indexOfFirstOrNull { it is Album && it.id == album.id } }

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

    override fun getSelectedTracks(): ArrayList<Track> {
        return getSelectedItems().filterIsInstance<Track>().toMutableList() as ArrayList<Track>
    }

    override fun getAllSelectedTracks(): List<Track> {
        val tracks = getSelectedTracks()
        tracks.addAll(ctx.audioHelper.getAlbumTracks(getSelectedAlbums()))
        return tracks
    }

    private fun getSelectedAlbums(): List<Album> = getSelectedItems().filterIsInstance<Album>().toList()

    private fun setupAlbum(view: View, album: Album) {
        view.apply {
            album_frame?.isSelected = selectedKeys.contains(album.hashCode())
            album_title.text = album.title
            album_title.setTextColor(textColor)
            album_tracks.text = resources.getQuantityString(R.plurals.tracks_plural, album.trackCnt, album.trackCnt)
            album_tracks.setTextColor(textColor)

            ctx.getAlbumCoverArt(album) { coverArt ->
                loadImage(findViewById(R.id.album_image), coverArt, placeholderBig)
            }
        }
    }

    private fun setupTrack(view: View, track: Track) {
        view.apply {
            setupViewBackground(ctx)
            track_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_title.text = track.title
            track_title.setTextColor(textColor)
            track_info.text = track.album
            track_info.setTextColor(textColor)

            track_id.beGone()
            track_image.beVisible()
            track_duration.text = track.duration.getFormattedDuration()
            track_duration.setTextColor(textColor)

            ctx.getTrackCoverArt(track) { coverArt ->
                loadImage(findViewById(R.id.track_image), coverArt, placeholder)
            }
        }
    }

    private fun setupSection(view: View, section: AlbumSection) {
        view.apply {
            item_section.text = section.title
            item_section.setTextColor(textColor)
        }
    }

    override fun onChange(position: Int): CharSequence {
        return when (val listItem = items.getOrNull(position)) {
            is Track -> listItem.title
            is Album -> listItem.title
            is AlbumSection -> listItem.title
            else -> ""
        }
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(ctx as SimpleActivity, selectedTrack) { track ->
                val trackIndex = items.indexOfFirst { (it as? Track)?.mediaStoreId == track.mediaStoreId }
                if (trackIndex != -1) {
                    items[trackIndex] = track
                    notifyItemChanged(trackIndex)
                    finishActMode()
                }

                ctx.refreshAfterEdit(track)
            }
        }
    }
}
