package com.simplemobiletools.musicplayer.fragments

import android.app.Activity
import android.content.Context
import android.provider.MediaStore.Audio
import android.util.AttributeSet
import com.simplemobiletools.commons.extensions.getIntValue
import com.simplemobiletools.commons.extensions.getStringValue
import com.simplemobiletools.commons.extensions.showErrorToast
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.adapters.ArtistsAdapter
import com.simplemobiletools.musicplayer.models.Album
import com.simplemobiletools.musicplayer.models.Artist
import kotlinx.android.synthetic.main.fragment_artists.view.*

class ArtistsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment(activity: SimpleActivity) {
        getArtists(activity) { artists ->
            ArtistsAdapter(activity, artists, artists_list) {
                openArtist(activity, it as Artist)
            }.apply {
                artists_list.adapter = this
            }
        }
    }

    private fun openArtist(activity: Activity, artist: Artist) {
        getAlbums(activity, artist) {

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

            try {
                val cursor = activity.contentResolver.query(uri, projection, null, null, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getIntValue(Audio.Artists._ID)
                            val title = cursor.getStringValue(Audio.Artists.ARTIST)
                            val albumCnt = cursor.getIntValue(Audio.ArtistColumns.NUMBER_OF_ALBUMS)
                            val trackCnt = cursor.getIntValue(Audio.ArtistColumns.NUMBER_OF_TRACKS)
                            val artist = Artist(id, title, albumCnt, trackCnt)
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

    private fun getAlbums(activity: Activity, artist: Artist, callback: (artists: ArrayList<Album>) -> Unit) {
        ensureBackgroundThread {
            val albums = ArrayList<Album>()
            val uri = Audio.Albums.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                Audio.Albums._ID,
                Audio.Albums.ARTIST,
                Audio.Albums.ALBUM)

            var selection = "${Audio.Albums.ARTIST} = ?"
            var selectionArgs = arrayOf(artist.title)

            if (isQPlus()) {
                selection = "${Audio.Albums.ARTIST_ID} = ?"
                selectionArgs = arrayOf(artist.id.toString())
            }

            try {
                val cursor = activity.contentResolver.query(uri, projection, selection, selectionArgs, null)
                cursor?.use {
                    if (cursor.moveToFirst()) {
                        do {
                            val id = cursor.getIntValue(Audio.Albums._ID)
                            val artistName = cursor.getStringValue(Audio.Albums.ARTIST)
                            val title = cursor.getStringValue(Audio.Albums.ALBUM)
                            val album = Album(id, artistName, title)
                            albums.add(album)
                        } while (cursor.moveToNext())
                    }
                }
            } catch (e: Exception) {
                activity.showErrorToast(e)
            }

            activity.runOnUiThread {
                callback(albums)
            }
        }
    }
}
