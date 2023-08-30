package com.simplemobiletools.musicplayer.adapters

import android.annotation.SuppressLint
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
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_artist.view.artist_albums_tracks
import kotlinx.android.synthetic.main.item_artist.view.artist_frame
import kotlinx.android.synthetic.main.item_artist.view.artist_title

class ArtistsAdapter(activity: BaseSimpleActivity, items: ArrayList<Artist>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    BaseMusicAdapter<Artist>(items, activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    override fun getActionMenuId() = R.menu.cab_artists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_artist, parent)

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
        val albums = ctx.audioHelper.getArtistAlbums(getSelectedItems())
        return ctx.audioHelper.getAlbumTracks(albums)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(ctx) {
            ensureBackgroundThread {
                val selectedArtists = getSelectedItems()
                val positions = selectedArtists.mapNotNull { artist -> items.indexOfFirstOrNull { it.id == artist.id } } as ArrayList<Int>
                val tracks = ctx.audioHelper.getArtistTracks(selectedArtists)

                ctx.audioHelper.deleteArtists(selectedArtists)
                ctx.deleteTracks(tracks) {
                    ctx.runOnUiThread {
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
        view.apply {
            setupViewBackground(ctx)
            artist_frame?.isSelected = selectedKeys.contains(artist.hashCode())
            artist_title.text = if (textToHighlight.isEmpty()) artist.title else artist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            artist_title.setTextColor(textColor)

            val albums = resources.getQuantityString(R.plurals.albums_plural, artist.albumCnt, artist.albumCnt)
            val tracks = resources.getQuantityString(R.plurals.tracks_plural, artist.trackCnt, artist.trackCnt)
            @SuppressLint("SetTextI18n")
            artist_albums_tracks.text = "$albums, $tracks"
            artist_albums_tracks.setTextColor(textColor)

            ctx.getArtistCoverArt(artist) { coverArt ->
                loadImage(findViewById(R.id.artist_image), coverArt, placeholder)
            }
        }
    }

    override fun onChange(position: Int) = items.getOrNull(position)?.getBubbleText(ctx.config.artistSorting) ?: ""
}
