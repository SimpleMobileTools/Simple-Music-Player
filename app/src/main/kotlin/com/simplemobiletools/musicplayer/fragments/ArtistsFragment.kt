package com.simplemobiletools.musicplayer.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore.Audio
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.activities.AlbumsActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.ArtistsAdapter
import com.simplemobiletools.musicplayer.extensions.getAlbumsSync
import com.simplemobiletools.musicplayer.extensions.getAlbumTracksSync
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.models.Artist
import kotlinx.android.synthetic.main.fragment_artists.view.*

// Artists -> Albums -> Tracks
class ArtistsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment(activity: SimpleActivity) {
        getArtists(activity) { artists ->
            ArtistsAdapter(activity, artists, artists_list) {
                Intent(activity, AlbumsActivity::class.java).apply {
                    putExtra(ARTIST, Gson().toJson(it as Artist))
                    activity.startActivity(this)
                }
            }.apply {
                artists_list.adapter = this
            }
        }
    }

    override fun finishActMode() {
        (artists_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    private fun getArtists(activity: Activity, callback: (artists: ArrayList<Artist>) -> Unit) {
        ensureBackgroundThread {
            val artists = ArrayList<Artist>()
            val uri = Audio.Artists.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                Audio.Artists._ID,
                Audio.Artists.ARTIST
            )

            try {
                val cursor = activity.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getIntValue(Audio.Artists._ID)
                            val title = cursor.getStringValue(Audio.Artists.ARTIST)
                            var artist = Artist(id, title, 0, 0, 0)
                            artist = fillArtistExtras(activity, artist)
                            if (artist.albumCnt > 0) {
                                artists.add(artist)
                            }
                        } while (cursor.moveToNext())
                    }
                }
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }

            activity.runOnUiThread {
                callback(artists)
            }
        }
    }

    private fun fillArtistExtras(activity: Activity, artist: Artist): Artist {
        val uri = Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            Audio.Albums._ID)

        val selection = "${Audio.Albums.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(artist.id.toString())

        artist.albumCnt = activity.getAlbumsSync(artist).size

        try {
            val cursor = activity.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    do {
                        val albumId = cursor.getLongValue(Audio.Albums._ID)
                        if (artist.albumArtId == 0L) {
                            artist.albumArtId = albumId
                        }

                        artist.trackCnt += activity.getAlbumTracksSync(albumId.toInt()).size
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
        }

        return artist
    }
}
