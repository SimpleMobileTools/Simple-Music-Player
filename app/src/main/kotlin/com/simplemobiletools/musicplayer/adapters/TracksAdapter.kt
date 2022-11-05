package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.dialogs.EditDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_CUSTOM
import com.simplemobiletools.musicplayer.helpers.TagHelper
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.models.Playlist
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_track.view.*
import org.greenrobot.eventbus.EventBus
import java.util.*

class TracksAdapter(
    activity: BaseSimpleActivity,
    var tracks: ArrayList<Track>,
    val isPlaylistContent: Boolean,
    recyclerView: MyRecyclerView,
    val playlist: Playlist? = null,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate, ItemTouchHelperContract {

    private val tagHelper by lazy { TagHelper(activity) }
    private var textToHighlight = ""
    private val placeholder = resources.getBiggerPlaceholder(textColor)
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()
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
        val track = tracks.getOrNull(position) ?: return
        holder.bindView(track, true, true) { itemView, layoutPosition ->
            setupView(itemView, track, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = tracks.size

    override fun prepareActionMode(menu: Menu) {
        menu.apply {
            findItem(R.id.cab_remove_from_playlist).isVisible = isPlaylistContent
            findItem(R.id.cab_rename).isVisible =
                isOneItemSelected() && getSelectedTracks().firstOrNull()?.let { !it.path.startsWith("content://") && tagHelper.isEditTagSupported(it) } == true
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
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = tracks.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = tracks.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = tracks.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {
        if (isPlaylistContent) {
            notifyDataSetChanged()
        }
    }

    override fun onActionModeDestroyed() {
        if (isPlaylistContent) {
            notifyDataSetChanged()
        }
    }

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
        activity.showTrackProperties(selectedTracks)
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

    private fun getSelectedTracks(): List<Track> = tracks.filter { selectedKeys.contains(it.hashCode()) }

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
    }

    private fun setupView(view: View, track: Track, holder: ViewHolder) {
        view.apply {
            track_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_title.text = if (textToHighlight.isEmpty()) track.title else track.title.highlightTextPart(textToHighlight, properPrimaryColor)
            track_drag_handle.beVisibleIf(isPlaylistContent && selectedKeys.isNotEmpty())
            track_drag_handle.applyColorFilter(textColor)
            track_drag_handle.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }

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

    override fun onChange(position: Int) = tracks.getOrNull(position)?.getBubbleText() ?: ""

    private fun displayEditDialog() {
        getSelectedTracks().firstOrNull()?.let { selectedTrack ->
            EditDialog(activity as SimpleActivity, selectedTrack) { track ->
                val trackIndex = tracks.indexOfFirstOrNull { it.mediaStoreId == track.mediaStoreId } ?: return@EditDialog
                tracks[trackIndex] = track
                notifyItemChanged(trackIndex)
                finishActMode()

                activity.refreshAfterEdit(track)
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        activity.config.saveCustomPlaylistSorting(playlist!!.id, PLAYER_SORT_BY_CUSTOM)
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(tracks, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(tracks, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onRowClear(myViewHolder: ViewHolder?) {
        ensureBackgroundThread {
            var index = 0
            tracks.forEach {
                it.orderInPlaylist = index++
                activity.tracksDAO.updateOrderInPlaylist(index, it.id)
            }
        }
    }
}
