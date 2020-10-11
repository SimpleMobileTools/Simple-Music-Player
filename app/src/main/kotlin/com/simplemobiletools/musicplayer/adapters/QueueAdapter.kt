package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getColoredDrawableWithColor
import com.simplemobiletools.commons.extensions.getFormattedDuration
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.item_track_queue.view.*
import java.util.*

class QueueAdapter(activity: SimpleActivity, val items: ArrayList<Track>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
        MyRecyclerViewAdapter(activity, recyclerView, null, itemClick) {

    private val placeholder = resources.getColoredDrawableWithColor(R.drawable.ic_headset, textColor)

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_queue

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_track_queue, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items.getOrNull(position) ?: return
        holder.bindView(item, true, true) { itemView, layoutPosition ->
            setupView(itemView, item)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = items.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {}

    override fun getSelectableItemCount() = items.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.id?.toInt()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.id.toInt() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun getItemWithKey(key: Int) = items.firstOrNull { it.id.toInt() == key }

    private fun setupView(view: View, track: Track) {
        view.apply {
            track_queue_frame?.isSelected = selectedKeys.contains(track.id.toInt())
            track_queue_title.text = track.title

            arrayOf(track_queue_title, track_queue_duration).forEach {
                val color = if (track == MusicService.mCurrTrack) primaryColor else textColor
                it.setTextColor(color)
            }

            track_queue_duration.text = track.duration.getFormattedDuration()

            if (track.coverArt.isEmpty()) {
                track_queue_image.setImageDrawable(placeholder)
            } else {
                val options = RequestOptions()
                    .error(placeholder)
                    .transform(CenterCrop(), RoundedCorners(8))

                Glide.with(activity)
                    .load(track.coverArt)
                    .apply(options)
                    .into(findViewById(R.id.track_queue_image))
            }
        }
    }
}
