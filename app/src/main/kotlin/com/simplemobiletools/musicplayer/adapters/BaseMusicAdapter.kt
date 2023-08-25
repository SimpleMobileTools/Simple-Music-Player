package com.simplemobiletools.musicplayer.adapters

import android.graphics.drawable.Drawable
import android.view.Menu
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleControllerActivity
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.helpers.TagHelper
import com.simplemobiletools.musicplayer.models.Track
import com.simplemobiletools.musicplayer.playback.PlaybackService

abstract class BaseMusicAdapter<Type>(
    var items: ArrayList<Type>,
    activity: BaseSimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit
) : MyRecyclerViewAdapter(activity, recyclerView, itemClick) {

    val ctx = activity as SimpleControllerActivity

    var textToHighlight = ""
    val tagHelper by lazy { TagHelper(ctx) }
    val placeholder by lazy { resources.getSmallPlaceholder(textColor) }
    val placeholderBig by lazy { resources.getBiggerPlaceholder(textColor) }
    open val cornerRadius by lazy { resources.getDimension(R.dimen.rounded_corner_radius_small).toInt() }

    override fun getItemCount() = items.size

    override fun getSelectableItemCount() = items.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = items.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = items.indexOfFirst { it.hashCode() == key }

    override fun prepareActionMode(menu: Menu) {}

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    open fun getSelectedTracks(): List<Track> = getSelectedItems().filterIsInstance<Track>().toList()

    open fun getAllSelectedTracks(): List<Track> = getSelectedTracks()

    open fun getSelectedItems(): List<Type> {
        return items.filter { selectedKeys.contains(it.hashCode()) }.toList()
    }

    fun shouldShowPlayNext(): Boolean {
        if (!isOneItemSelected()) {
            return false
        }

        val currentMedia = PlaybackService.currentMediaItem ?: return false
        val selectedTrack = getSelectedTracks().firstOrNull()
        return selectedTrack != null && !currentMedia.isSameMedia(selectedTrack)
    }

    fun shouldShowRename(): Boolean {
        if (!isOneItemSelected()) {
            return false
        }

        val selectedTrack = getSelectedTracks().firstOrNull() ?: return false
        return !selectedTrack.path.startsWith("content://") && tagHelper.isEditTagSupported(selectedTrack)
    }

    fun addToQueue() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            ctx.runOnUiThread {
                ctx.addTracksToQueue(allSelectedTracks) {
                    finishActMode()
                }
            }
        }
    }

    fun playNextInQueue() {
        ensureBackgroundThread {
            getSelectedTracks().firstOrNull()?.let { selectedTrack ->
                ctx.runOnUiThread {
                    ctx.playNextInQueue(selectedTrack) {
                        finishActMode()
                    }
                }
            }
        }
    }

    fun addToPlaylist() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            ctx.runOnUiThread {
                ctx.addTracksToPlaylist(allSelectedTracks) {
                    finishActMode()
                    notifyDataChanged()
                }
            }
        }
    }

    fun shareFiles() {
        ensureBackgroundThread {
            ctx.shareFiles(getAllSelectedTracks())
        }
    }

    fun showProperties() {
        ensureBackgroundThread {
            val selectedTracks = getAllSelectedTracks()
            if (selectedTracks.isEmpty()) {
                return@ensureBackgroundThread
            }

            ctx.runOnUiThread {
                ctx.showTrackProperties(selectedTracks)
            }
        }
    }

    fun updateItems(newItems: ArrayList<Type>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != items.hashCode()) {
            items = newItems.clone() as ArrayList<Type>
            textToHighlight = highlightText
            notifyDataChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataChanged()
        }
    }

    fun loadImage(imageView: ImageView, resource: Any?, placeholder: Drawable) {
        val options = RequestOptions()
            .error(placeholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        ctx.ensureActivityNotDestroyed {
            Glide.with(ctx)
                .load(resource)
                .apply(options)
                .into(imageView)
        }
    }

    fun notifyDataChanged() = notifyItemRangeChanged(0, itemCount)
}
