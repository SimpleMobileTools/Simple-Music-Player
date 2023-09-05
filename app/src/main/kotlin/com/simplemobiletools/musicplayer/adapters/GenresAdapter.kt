package com.simplemobiletools.musicplayer.adapters

import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ItemGenreBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getGenreCoverArt
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Genre
import com.simplemobiletools.musicplayer.models.Track

class GenresAdapter(activity: BaseSimpleActivity, items: ArrayList<Genre>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<Genre>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_genres

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGenreBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val genre = items.getOrNull(position) ?: return
        holder.bindView(genre, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, genre)
        }
        bindViewHolder(holder)
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectedTracks(): ArrayList<Track> {
        return context.audioHelper.getGenreTracks(getSelectedItems())
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(context) {
            ensureBackgroundThread {
                val selectedGenres = getSelectedItems()
                val positions = selectedGenres.mapNotNull { genre -> items.indexOfFirstOrNull { it.id == genre.id } } as ArrayList<Int>
                val tracks = context.audioHelper.getGenreTracks(selectedGenres)
                context.audioHelper.deleteGenres(selectedGenres)

                context.deleteTracks(tracks) {
                    context.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (items.size > it) {
                                items.removeAt(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupView(view: View, genre: Genre) {
        ItemGenreBinding.bind(view).apply {
            root.setupViewBackground(activity)
            genreFrame.isSelected = selectedKeys.contains(genre.hashCode())
            genreTitle.text = if (textToHighlight.isEmpty()) {
                genre.title
            } else {
                genre.title.highlightTextPart(textToHighlight, properPrimaryColor)
            }

            genreTitle.setTextColor(textColor)

            val tracks = resources.getQuantityString(R.plurals.tracks_plural, genre.trackCnt, genre.trackCnt)
            genreTracks.text = tracks
            genreTracks.setTextColor(textColor)

            activity.getGenreCoverArt(genre) { coverArt ->
                loadImage(genreImage, coverArt, placeholderBig)
            }
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.genreSorting) ?: ""
}
