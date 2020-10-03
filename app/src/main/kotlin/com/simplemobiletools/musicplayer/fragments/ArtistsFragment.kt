package com.simplemobiletools.musicplayer.fragments

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.MediaStore.Audio
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getLongValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.activities.AlbumsActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.ArtistsAdapter
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.models.Artist
import kotlinx.android.synthetic.main.fragment_artists.view.*

// Artists -> Albums -> Songs
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

    private fun getArtists(activity: Activity, callback: (artists: ArrayList<Artist>) -> Unit) {
        ensureBackgroundThread {
            val artists = ArrayList<Artist>()
            val uri = Audio.Artists.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                Audio.Artists._ID,
                Audio.Artists.ARTIST,
                Audio.ArtistColumns.NUMBER_OF_ALBUMS,
                Audio.ArtistColumns.NUMBER_OF_TRACKS
            )

            val selection = "${Audio.ArtistColumns.NUMBER_OF_ALBUMS} > 0"

            try {
                val cursor = activity.contentResolver.query(uri, projection, selection, null, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getIntValue(Audio.Artists._ID)
                            val title = cursor.getStringValue(Audio.Artists.ARTIST)
                            val albumCnt = cursor.getIntValue(Audio.ArtistColumns.NUMBER_OF_ALBUMS)
                            val trackCnt = cursor.getIntValue(Audio.ArtistColumns.NUMBER_OF_TRACKS)
                            val albumArtId = getArtistAlbumId(activity, id)
                            val artist = Artist(id, title, albumCnt, trackCnt, albumArtId)
                            artists.add(artist)
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

    private fun getArtistAlbumId(activity: Activity, artistId: Int): Long {
        val uri = Audio.Albums.EXTERNAL_CONTENT_URI
        val projection = arrayOf(Audio.Albums._ID)
        val selection = "${Audio.Albums.ARTIST_ID} = ?"
        val selectionArgs = arrayOf(artistId.toString())
        try {
            val cursor = activity.contentResolver.query(uri, projection, selection, selectionArgs, null)
            cursor?.use {
                if (cursor.moveToFirst()) {
                    return cursor.getLongValue(Audio.Albums._ID)
                }
            }
        } catch (e: Exception) {
        }

        return 0L
    }
}
