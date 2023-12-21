package org.fossify.musicplayer.adapters

import android.annotation.SuppressLint
import android.view.View
import android.view.ViewGroup
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.highlightTextPart
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import org.fossify.musicplayer.R
import org.fossify.musicplayer.databinding.ItemArtistBinding
import org.fossify.musicplayer.extensions.audioHelper
import org.fossify.musicplayer.extensions.config
import org.fossify.musicplayer.extensions.getArtistCoverArt
import org.fossify.musicplayer.inlines.indexOfFirstOrNull
import org.fossify.musicplayer.models.Artist
import org.fossify.musicplayer.models.Track

class ArtistsAdapter(activity: BaseSimpleActivity, items: ArrayList<Artist>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<Artist>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_artists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemArtistBinding.inflate(layoutInflater, parent, false)
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = items.getOrNull(position) ?: return
        holder.bindView(artist, allowSingleClick = true, allowLongClick = true) { itemView, _ ->
            setupView(itemView, artist)
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
        val albums = context.audioHelper.getArtistAlbums(getSelectedItems())
        return context.audioHelper.getAlbumTracks(albums)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(context) {
            ensureBackgroundThread {
                val selectedArtists = getSelectedItems()
                val positions = selectedArtists.mapNotNull { artist -> items.indexOfFirstOrNull { it.id == artist.id } } as ArrayList<Int>
                val tracks = context.audioHelper.getArtistTracks(selectedArtists)

                context.audioHelper.deleteArtists(selectedArtists)
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

    private fun setupView(view: View, artist: Artist) {
        ItemArtistBinding.bind(view).apply {
            root.setupViewBackground(context)
            artistFrame.isSelected = selectedKeys.contains(artist.hashCode())
            artistTitle.text = if (textToHighlight.isEmpty()) artist.title else artist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            artistTitle.setTextColor(textColor)

            val albums = resources.getQuantityString(R.plurals.albums_plural, artist.albumCnt, artist.albumCnt)
            val tracks = resources.getQuantityString(R.plurals.tracks_plural, artist.trackCnt, artist.trackCnt)
            @SuppressLint("SetTextI18n")
            artistAlbumsTracks.text = "$albums, $tracks"
            artistAlbumsTracks.setTextColor(textColor)

            context.getArtistCoverArt(artist) { coverArt ->
                loadImage(artistImage, coverArt, placeholder)
            }
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(context.config.artistSorting) ?: ""
}
