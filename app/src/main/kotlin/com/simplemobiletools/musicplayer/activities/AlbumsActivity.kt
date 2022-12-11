package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.AlbumsTracksAdapter
import com.simplemobiletools.musicplayer.extensions.getAlbumTracksSync
import com.simplemobiletools.musicplayer.extensions.getAlbums
import com.simplemobiletools.musicplayer.extensions.resetQueueItems
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.helpers.RESTART_PLAYER
import com.simplemobiletools.musicplayer.helpers.TRACK
import com.simplemobiletools.musicplayer.models.*
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_albums.*
import kotlinx.android.synthetic.main.view_current_track_bar.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

// Artists -> Albums -> Tracks
class AlbumsActivity : SimpleActivity() {
    private var bus: EventBus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_albums)

        bus = EventBus.getDefault()
        bus!!.register(this)

        albums_fastscroller.updateColors(getProperPrimaryColor())

        val artistType = object : TypeToken<Artist>() {}.type
        val artist = Gson().fromJson<Artist>(intent.getStringExtra(ARTIST), artistType)
        albums_toolbar.title = artist.title

        getAlbums(artist) { albums ->
            val listItems = ArrayList<ListItem>()
            val albumsSectionLabel = resources.getQuantityString(R.plurals.albums_plural, albums.size, albums.size)
            listItems.add(AlbumSection(albumsSectionLabel))
            listItems.addAll(albums)

            var trackFullDuration = 0
            val tracksToAdd = ArrayList<Track>()
            albums.forEach {
                val tracks = getAlbumTracksSync(it.id)
                tracks.sortWith(compareBy({ it.trackId }, { it.title.toLowerCase() }))
                trackFullDuration += tracks.sumBy { it.duration }
                tracksToAdd.addAll(tracks)
            }

            var tracksSectionLabel = resources.getQuantityString(R.plurals.tracks_plural, tracksToAdd.size, tracksToAdd.size)
            tracksSectionLabel += " • ${trackFullDuration.getFormattedDuration(true)}"
            listItems.add(AlbumSection(tracksSectionLabel))
            listItems.addAll(tracksToAdd)

            artist_albums_fab_shuffle_play.setOnClickListener { view ->
                if(listItems.isNotEmpty()){
                    val randomTrack = tracksToAdd.random()
                    handleNotificationPermission { granted ->
                        if (granted) {
                            resetQueueItems(tracksToAdd) {
                                Intent(this, TrackActivity::class.java).apply {
                                    putExtra(TRACK, Gson().toJson(randomTrack))
                                    putExtra(RESTART_PLAYER, true)
                                    startActivity(this)
                                }
                            }
                        } else {
                            toast(R.string.no_post_notifications_permissions)
                        }
                    }
                }
            }

            runOnUiThread {
                AlbumsTracksAdapter(this, listItems, albums_list) {
                    hideKeyboard()
                    if (it is Album) {
                        Intent(this, TracksActivity::class.java).apply {
                            putExtra(ALBUM, Gson().toJson(it))
                            startActivity(this)
                        }
                    } else {
                        handleNotificationPermission { granted ->
                            if (granted) {
                                resetQueueItems(tracksToAdd) {
                                    Intent(this, TrackActivity::class.java).apply {
                                        putExtra(TRACK, Gson().toJson(it))
                                        putExtra(RESTART_PLAYER, true)
                                        startActivity(this)
                                    }
                                }
                            } else {
                                toast(R.string.no_post_notifications_permissions)
                            }
                        }
                    }
                }.apply {
                    albums_list.adapter = this
                }

                if (areSystemAnimationsEnabled) {
                    albums_list.scheduleLayoutAnimation()
                }
            }
        }

        current_track_bar.setOnClickListener {
            hideKeyboard()
            handleNotificationPermission { granted ->
                if (granted) {
                    Intent(this, TrackActivity::class.java).apply {
                        startActivity(this)
                    }
                } else {
                    toast(R.string.no_post_notifications_permissions)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateCurrentTrackBar()
        setupToolbar(albums_toolbar, NavigationIcon.Arrow)
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
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
