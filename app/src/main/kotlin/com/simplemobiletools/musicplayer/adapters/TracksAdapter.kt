package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.ALL_TRACKS_PLAYLIST_ID
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_CUSTOM
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_track.view.*
import org.greenrobot.eventbus.EventBus

class TracksAdapter(
    activity: BaseSimpleActivity,
    recyclerView: MyRecyclerView,
    val sourceType: Int,
    val folder: String? = null,
    val playlist: Playlist? = null,
    items: ArrayList<Track>,
    itemClick: (Any) -> Unit
) : BaseMusicAdapter<Track>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate, ItemTouchHelperContract {

    private var touchHelper: ItemTouchHelper? = null
    private var startReorderDragListener: StartReorderDragListener

    init {
        setupDragListener(true)

        touchHelper = ItemTouchHelper(ItemMoveCallback(this))
        touchHelper!!.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                touchHelper?.startDrag(viewHolder)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_tracks

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_track, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = items.getOrNull(position) ?: return
        holder.bindView(track, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, track, holder)
        }
        bindViewHolder(holder)
    }

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_remove_from_playlist).isVisible = isPlaylistContent()
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
            R.id.cab_rename -> displayEditDialog()
            R.id.cab_remove_from_playlist -> removeFromPlaylist()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_select_all -> selectAll()
            R.id.cab_play_next -> playNextInQueue()
        }
    }

    override fun onActionModeCreated() {
        if (isPlaylistContent()) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    override fun onActionModeDestroyed() {
        if (isPlaylistContent()) {
            notifyItemRangeChanged(0, itemCount)
        }
    }

    private fun removeFromPlaylist() {
        ensureBackgroundThread {
            val positions = ArrayList<Int>()
            val selectedTracks = getSelectedTracks()
            selectedTracks.forEach { track ->
                val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                if (position != -1) {
                    positions.add(position)
                }
            }

            ctx.audioHelper.deleteTracks(selectedTracks)
            // this is to make sure these tracks aren't automatically re-added to the 'All tracks' playlist on rescan
            val removedTrackIds = selectedTracks.filter { it.playListId == ALL_TRACKS_PLAYLIST_ID }.map { it.mediaStoreId.toString() }
            if (removedTrackIds.isNotEmpty()) {
                val config = ctx.config
                config.tracksRemovedFromAllTracksPlaylist = config.tracksRemovedFromAllTracksPlaylist.apply {
                    addAll(removedTrackIds)
                }
            }

            EventBus.getDefault().post(Events.PlaylistsUpdated())
            ctx.runOnUiThread {
                positions.sortDescending()
                removeSelectedItems(positions)
                positions.forEach {
                    items.removeAt(it)
                }
            }
        }
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(ctx) {
            ensureBackgroundThread {
                val positions = ArrayList<Int>()
                val selectedTracks = getSelectedTracks()
                selectedTracks.forEach { track ->
                    val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                    if (position != -1) {
                        positions.add(position)
                    }
                }

                ctx.deleteTracks(selectedTracks) {
                    ctx.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (items.size > it) {
                                items.removeAt(it)
                            }
                        }

                        finishActMode()

                        // finish activity if all tracks are deleted
                        if (items.isEmpty() && !isPlaylistContent()) {
                            ctx.finish()
                        }
                    }
                }
            }
        }
    }

    override fun getSelectedTracks(): List<Track> = items.filter { selectedKeys.contains(it.hashCode()) }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupView(view: View, track: Track, holder: ViewHolder) {
        view.apply {
            setupViewBackground(ctx)
            track_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_title.text = if (textToHighlight.isEmpty()) track.title else track.title.highlightTextPart(textToHighlight, properPrimaryColor)
            track_info.text = if (textToHighlight.isEmpty()) {
                "${track.artist} • ${track.album}"
            } else {
                ("${track.artist} • ${track.album}").highlightTextPart(textToHighlight, properPrimaryColor)
            }
            track_drag_handle.beVisibleIf(isPlaylistContent() && selectedKeys.isNotEmpty())
            track_drag_handle.applyColorFilter(textColor)
            track_drag_handle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }

            arrayOf(track_id, track_title, track_info, track_duration).forEach {
                it.setTextColor(textColor)
            }

            track_duration.text = track.duration.getFormattedDuration()
            context.getTrackCoverArt(track) { coverArt ->
                loadImage(findViewById(R.id.track_image), coverArt, placeholderBig)
            }

            track_image.beVisible()
            track_id.beGone()
        }
    }

    override fun onChange(position: Int): String {
        val sorting = if (isPlaylistContent() && playlist != null) {
            ctx.config.getProperPlaylistSorting(playlist.id)
        } else if (sourceType == TYPE_FOLDER && folder != null) {
            ctx.config.getProperFolderSorting(folder)
        } else {
            ctx.config.trackSorting
        }

        return items.getOrNull(position)?.getBubbleText(sorting) ?: ""
    }

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(ctx, selectedTrack) { track ->
                val trackIndex = items.indexOfFirstOrNull { it.mediaStoreId == track.mediaStoreId } ?: return@EditDialog
                items[trackIndex] = track
                notifyItemChanged(trackIndex)
                finishActMode()

                ctx.refreshAfterEdit(track)
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        ctx.config.saveCustomPlaylistSorting(playlist!!.id, PLAYER_SORT_BY_CUSTOM)
        items.swap(fromPosition, toPosition)
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        ensureBackgroundThread {
            var index = 0
            items.forEach {
                it.orderInPlaylist = index++
                ctx.audioHelper.updateOrderInPlaylist(index, it.id)
            }
        }
    }

    private fun isPlaylistContent() = sourceType == TYPE_PLAYLIST

    companion object {
        const val TYPE_PLAYLIST = 1
        const val TYPE_FOLDER = 2
        const val TYPE_ALBUM = 3
        const val TYPE_TRACKS = 4
    }
}
