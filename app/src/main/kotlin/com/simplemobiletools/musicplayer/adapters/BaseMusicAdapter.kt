package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.view.Menu
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
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

    val context = activity as SimpleControllerActivity

    var textToHighlight = ""
    val tagHelper by lazy { TagHelper(context) }
    var placeholder = resources.getSmallPlaceholder(textColor)
    var placeholderBig = resources.getBiggerPlaceholder(textColor)
    open val cornerRadius by lazy { resources.getDimension(com.simplemobiletools.commons.R.dimen.rounded_corner_radius_small).toInt() }

    init {
        setupDragListener(true)
    }

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

    open fun updateItems(newItems: ArrayList<Type>, highlightText: String = "", forceUpdate: Boolean = false) {
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
            context.runOnUiThread {
                context.addTracksToQueue(allSelectedTracks) {
                    finishActMode()
                }
            }
        }
    }

    fun playNextInQueue() {
        ensureBackgroundThread {
            getSelectedTracks().firstOrNull()?.let { selectedTrack ->
                context.runOnUiThread {
                    context.playNextInQueue(selectedTrack) {
                        finishActMode()
                    }
                }
            }
        }
    }

    fun addToPlaylist() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            context.runOnUiThread {
                context.addTracksToPlaylist(allSelectedTracks) {
                    finishActMode()
                    notifyDataChanged()
                }
            }
        }
    }

    fun shareFiles() {
        ensureBackgroundThread {
            context.shareFiles(getAllSelectedTracks())
        }
    }

    fun showProperties() {
        ensureBackgroundThread {
            val selectedTracks = getAllSelectedTracks()
            if (selectedTracks.isEmpty()) {
                return@ensureBackgroundThread
            }

            context.runOnUiThread {
                context.showTrackProperties(selectedTracks)
            }
        }
    }

    fun loadImage(imageView: ImageView, resource: Any?, placeholder: Drawable) {
        val options = RequestOptions()
            .error(placeholder)
            .transform(CenterCrop(), RoundedCorners(cornerRadius))

        context.ensureActivityNotDestroyed {
            Glide.with(context)
                .load(resource)
                .apply(options)
                .into(imageView)
        }
    }

    fun updateColors(newTextColor: Int) {
        if (textColor != newTextColor || properPrimaryColor != context.getProperPrimaryColor()) {
            updateTextColor(newTextColor)
            updatePrimaryColor()
            placeholder = resources.getSmallPlaceholder(textColor)
            placeholderBig = resources.getBiggerPlaceholder(textColor)
            notifyDataChanged()
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun notifyDataChanged() = if (itemCount == 0) {
        notifyDataSetChanged()
    } else {
        notifyItemRangeChanged(0, itemCount)
    }
}
