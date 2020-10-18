package com.simplemobiletools.musicplayer.activities

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.SongsAdapter
import com.simplemobiletools.musicplayer.extensions.getAlbumTracksSync
import com.simplemobiletools.musicplayer.extensions.getPlaylistTracks
import com.simplemobiletools.musicplayer.extensions.resetQueueItems
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.*
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_tracks.*
import kotlinx.android.synthetic.main.view_current_track_bar.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

// Artists -> Albums -> Tracks
class TracksActivity : SimpleActivity() {
    private var bus: EventBus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)

        bus = EventBus.getDefault()
        bus!!.register(this)

        val playlistType = object : TypeToken<Playlist>() {}.type
        val playlist = Gson().fromJson<Playlist>(intent.getStringExtra(PLAYLIST), playlistType)

        val albumType = object : TypeToken<Album>() {}.type
        val album = Gson().fromJson<Album>(intent.getStringExtra(ALBUM), albumType)

        title = playlist?.title ?: album.title

        ensureBackgroundThread {
            val tracks = ArrayList<Track>()
            val listItems = ArrayList<ListItem>()
            if (playlist != null) {
                val playlistTracks = getPlaylistTracks(playlist.id)
                tracks.addAll(playlistTracks)
                listItems.addAll(tracks)
            } else {
                val albumTracks = getAlbumTracksSync(album.id)
                albumTracks.sortWith(compareBy({ it.trackId }, { it.title.toLowerCase() }))
                tracks.addAll(albumTracks)

                val coverArt = ContentUris.withAppendedId(artworkUri, album.id.toLong()).toString()
                val header = AlbumHeader(album.title, coverArt, album.year, tracks.size, tracks.sumBy { it.duration }, album.artist)
                listItems.add(header)
                listItems.addAll(tracks)
            }

            runOnUiThread {
                val adapter = SongsAdapter(this, listItems, tracks_list, album == null, tracks_fastscroller) {
                    resetQueueItems(tracks) {
                        Intent(this, TrackActivity::class.java).apply {
                            putExtra(TRACK, Gson().toJson(it))
                            putExtra(RESTART_PLAYER, true)
                            startActivity(this)
                        }
                    }
                }.apply {
                    tracks_list.adapter = this
                }

                tracks_fastscroller.setViews(tracks_list) {
                    val listItem = adapter.items.getOrNull(it)
                    if (listItem is Track) {
                        tracks_fastscroller.updateBubbleText(listItem.title)
                    } else if (listItem is AlbumHeader) {
                        tracks_fastscroller.updateBubbleText(listItem.title)
                    }
                }
            }
        }

        current_track_bar.setOnClickListener {
            Intent(this, TrackActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentTrackBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun updateCurrentTrackBar() {
        current_track_bar.updateColors()
        current_track_bar.updateCurrentTrack(MusicService.mCurrTrack)
        current_track_bar.updateTrackState(MusicService.getIsPlaying())
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackChangedEvent(event: Events.TrackChanged) {
        current_track_bar.updateCurrentTrack(event.track)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackStateChanged(event: Events.TrackStateChanged) {
        current_track_bar.updateTrackState(event.isPlaying)
    }
}
