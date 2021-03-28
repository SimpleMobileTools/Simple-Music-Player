package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.AlbumsActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.ArtistsAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getArtists
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.helpers.TAB_ARTISTS
import com.simplemobiletools.musicplayer.models.Artist
import kotlinx.android.synthetic.main.fragment_artists.view.*

// Artists -> Albums -> Tracks
class ArtistsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var artistsIgnoringSearch = ArrayList<Artist>()

    override fun setupFragment(activity: SimpleActivity) {
        activity.getArtists { artists ->
            Artist.sorting = activity.config.artistSorting
            artists.sort()

            activity.runOnUiThread {
                artists_placeholder.text = context.getString(R.string.no_items_found)
                artists_placeholder.beVisibleIf(artists.isEmpty())

                val adapter = artists_list.adapter
                if (adapter == null) {
                    ArtistsAdapter(activity, artists, artists_list, artists_fastscroller) {
                        Intent(activity, AlbumsActivity::class.java).apply {
                            putExtra(ARTIST, Gson().toJson(it as Artist))
                            activity.startActivity(this)
                        }
                    }.apply {
                        artists_list.adapter = this
                    }

                    artists_list.scheduleLayoutAnimation()
                    artists_fastscroller.setViews(artists_list) {
                        val artist = (artists_list.adapter as ArtistsAdapter).artists.getOrNull(it)
                        artists_fastscroller.updateBubbleText(artist?.getBubbleText() ?: "")
                    }
                } else {
                    (adapter as ArtistsAdapter).updateItems(artists)
                }
            }
        }
    }

    override fun finishActMode() {
        (artists_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = artistsIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Artist>
        (artists_list.adapter as? ArtistsAdapter)?.updateItems(filtered, text)
        artists_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchOpened() {
        artistsIgnoringSearch = (artists_list?.adapter as? ArtistsAdapter)?.artists ?: ArrayList()
    }

    override fun onSearchClosed() {
        (artists_list.adapter as? ArtistsAdapter)?.updateItems(artistsIgnoringSearch)
        artists_placeholder.beGoneIf(artistsIgnoringSearch.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_ARTISTS) {
            val adapter = artists_list.adapter as? ArtistsAdapter ?: return@ChangeSortingDialog
            val artists = adapter.artists
            Artist.sorting = activity.config.artistSorting
            artists.sort()
            adapter.updateItems(artists, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        artists_fastscroller.updatePrimaryColor()
        artists_fastscroller.updateBubbleColors()
    }
}
