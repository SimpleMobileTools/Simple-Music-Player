package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Genre
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_genre.view.genre_frame
import kotlinx.android.synthetic.main.item_genre.view.genre_title
import kotlinx.android.synthetic.main.item_genre.view.genre_tracks

class GenresAdapter(activity: BaseSimpleActivity, var genres: ArrayList<Genre>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""
    private val placeholderBig = resources.getBiggerPlaceholder(textColor)
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_genres

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_genre, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = genres.getOrNull(position) ?: return
        holder.bindView(genre, true, true) { itemView, layoutPosition ->
            setupView(itemView, genre)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = genres.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = genres.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = genres.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = genres.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun addToPlaylist() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            activity.runOnUiThread {
                activity.addTracksToPlaylist(allSelectedTracks) {
                    finishActMode()
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun addToQueue() {
        ensureBackgroundThread {
            activity.addTracksToQueue(getAllSelectedTracks()) {
                finishActMode()
            }
        }
    }

    private fun getAllSelectedTracks(): ArrayList<Track> {
        return activity.audioHelper.getGenreTracks(getSelectedGenres())
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                val selectedGenres = getSelectedGenres()
                val positions = selectedGenres.mapNotNull { genre -> genres.indexOfFirstOrNull { it.id == genre.id } } as ArrayList<Int>
                val tracks = activity.audioHelper.getGenreTracks(selectedGenres)
                activity.audioHelper.deleteGenres(selectedGenres)

                activity.deleteTracks(tracks) {
                    activity.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (genres.size > it) {
                                genres.removeAt(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun getSelectedGenres(): List<Genre> = genres.filter { selectedKeys.contains(it.hashCode()) }.toList()

    fun updateItems(newItems: ArrayList<Genre>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != genres.hashCode()) {
            genres = newItems.clone() as ArrayList<Genre>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, genre: Genre) {
        view.apply {
            setupViewBackground(activity)
            genre_frame?.isSelected = selectedKeys.contains(genre.hashCode())
            genre_title.text = if (textToHighlight.isEmpty()) {
                genre.title
            } else {
                genre.title.highlightTextPart(textToHighlight, properPrimaryColor)
            }

            genre_title.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, genre.trackCnt, genre.trackCnt)
            genre_tracks.text = tracks
            genre_tracks.setTextColor(textColor)

            activity.getGenreCoverArt(genre) { coverArt ->
                val options = RequestOptions()
                    .error(placeholderBig)
                    .transform(CenterCrop(), RoundedCorners(cornerRadius))

                activity.ensureActivityNotDestroyed {
                    Glide.with(activity)
                        .load(coverArt)
                        .apply(options)
                        .into(findViewById(R.id.genre_image))
                }
            }
        }
    }

    override fun onChange(position: Int) = genres.getOrNull(position)?.getBubbleText() ?: ""
}
