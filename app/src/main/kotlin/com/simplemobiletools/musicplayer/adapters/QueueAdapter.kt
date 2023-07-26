package com.simplemobiletools.musicplayer.adapters

import android.content.Intent
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
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.interfaces.ItemMoveCallback
import com.simplemobiletools.commons.interfaces.ItemTouchHelperContract
import com.simplemobiletools.commons.interfaces.StartReorderDragListener
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.FINISH
import com.simplemobiletools.musicplayer.helpers.PLAY_TRACK
import com.simplemobiletools.musicplayer.helpers.TRACK_ID
import com.simplemobiletools.musicplayer.helpers.UPDATE_NEXT_TRACK
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_track_queue.view.*
import java.util.*

class QueueAdapter(activity: SimpleActivity, var items: ArrayList<Track>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), ItemTouchHelperContract, RecyclerViewFastScroller.OnPopupTextUpdate {

    private val placeholder = resources.getBiggerPlaceholder(textColor)
    private var startReorderDragListener: StartReorderDragListener
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()
    private var textToHighlight = ""

    init {
        setupDragListener(true)

        val touchHelper = ItemTouchHelper(ItemMoveCallback(this))
        touchHelper.attachToRecyclerView(recyclerView)

        startReorderDragListener = object : StartReorderDragListener {
            override fun requestDrag(viewHolder: RecyclerView.ViewHolder) {
                touchHelper.startDrag(viewHolder)
            }
        }
    }

    override fun getActionMenuId() = R.menu.cab_queue

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_track_queue, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bindView(item, true, true) { itemView, layoutPosition ->
            setupView(itemView, item, holder)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_remove_from_queue -> removeFromQueue()
            R.id.cab_delete_file -> deleteTracks()
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = items.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {
        notifyItemRangeChanged(0, itemCount)
    }

    override fun onActionModeDestroyed() {
        notifyItemRangeChanged(0, itemCount)
    }

    private fun removeFromQueue() {
        val positions = ArrayList<Int>()
        val selectedTracks = getSelectedTracks()
        selectedTracks.forEach { track ->
            val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
            if (position != -1) {
                positions.add(position)
            }
        }

        activity.removeQueueItems(selectedTracks) {
            refreshTracksList(positions, selectedTracks)
        }
    }

    private fun deleteTracks() {
        ConfirmationDialog(activity, "", R.string.delete_song_warning, R.string.ok, R.string.cancel) {
            val positions = ArrayList<Int>()
            val selectedTracks = getSelectedTracks()
            selectedTracks.forEach { track ->
                val position = items.indexOfFirst { it.mediaStoreId == track.mediaStoreId }
                if (position != -1) {
                    positions.add(position)
                }
            }

            activity.deleteTracks(selectedTracks) {
                refreshTracksList(positions, selectedTracks)
            }
        }
    }

    private fun refreshTracksList(positions: ArrayList<Int>, selectedTracks: List<Track>) {
        activity.runOnUiThread {
            if (selectedTracks.contains(MusicService.mCurrTrack)) {
                if (MusicService.mTracks.isEmpty()) {
                    activity.sendIntent(FINISH)
                    activity.finish()
                    return@runOnUiThread
                }

                Intent(activity, MusicService::class.java).apply {
                    action = PLAY_TRACK
                    putExtra(TRACK_ID, (MusicService.mTracks.first()).mediaStoreId)
                    activity.startService(this)
                }
            }

            positions.sortDescending()
            removeSelectedItems(positions)
        }
    }

    private fun addToPlaylist() {
        activity.addTracksToPlaylist(getSelectedTracks()) {
            finishActMode()
            notifyDataSetChanged()
        }
    }

    private fun getSelectedTracks(): List<Track> = items.filter { selectedKeys.contains(it.hashCode()) }.toList()

    private fun setupView(view: View, track: Track, holder: ViewHolder) {
        view.apply {
            setupViewBackground(activity)
            track_queue_frame?.isSelected = selectedKeys.contains(track.hashCode())
            track_queue_title.text = if (textToHighlight.isEmpty()) track.title else track.title.highlightTextPart(textToHighlight, properPrimaryColor)

            arrayOf(track_queue_title, track_queue_duration).forEach {
                val color = if (track == MusicService.mCurrTrack) context.getProperPrimaryColor() else textColor
                it.setTextColor(color)
            }

            track_queue_duration.text = track.duration.getFormattedDuration()
            track_queue_drag_handle.beVisibleIf(selectedKeys.isNotEmpty())
            track_queue_drag_handle.applyColorFilter(textColor)
            track_queue_drag_handle.setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    startReorderDragListener.requestDrag(holder)
                }
                false
            }

            activity.getTrackCoverArt(track) { coverArt ->
                val options = RequestOptions()
                    .error(placeholder)
                    .transform(CenterCrop(), RoundedCorners(cornerRadius))

                activity.ensureActivityNotDestroyed {
                    Glide.with(activity)
                        .load(coverArt)
                        .apply(options)
                        .into(findViewById(R.id.track_queue_image))
                }
            }
        }
    }

    override fun onRowMoved(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        activity.sendIntent(UPDATE_NEXT_TRACK)
    }

    override fun onRowClear(myViewHolder: ViewHolder?) {}

    override fun onRowSelected(myViewHolder: ViewHolder?) {}

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText() ?: ""

    fun updateItems(newItems: ArrayList<Track>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != items.hashCode()) {
            items = newItems.clone() as ArrayList<Track>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

}
