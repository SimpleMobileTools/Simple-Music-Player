package com.simplemobiletools.musicplayer.activities

import android.content.ContentUris
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.TracksAdapter
import com.simplemobiletools.musicplayer.adapters.TracksHeaderAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.*
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
    private var playlist: Playlist? = null
    private var lastFilePickerPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)

        bus = EventBus.getDefault()
        bus!!.register(this)

        val playlistType = object : TypeToken<Playlist>() {}.type
        playlist = Gson().fromJson<Playlist>(intent.getStringExtra(PLAYLIST), playlistType)
        if (playlist != null) {
            invalidateOptionsMenu()
        }

        val albumType = object : TypeToken<Album>() {}.type
        val album = Gson().fromJson<Album>(intent.getStringExtra(ALBUM), albumType)

        title = playlist?.title ?: album.title

        tracks_placeholder.setTextColor(config.textColor)
        tracks_placeholder_2.setTextColor(getAdjustedPrimaryColor())
        tracks_placeholder_2.underlineText()
        tracks_placeholder_2.setOnClickListener {
            addFolderToPlaylist()
        }

        ensureBackgroundThread {
            val tracks = ArrayList<Track>()
            val listItems = ArrayList<ListItem>()
            if (playlist != null) {
                val playlistTracks = tracksDAO.getTracksFromPlaylist(playlist!!.id)
                runOnUiThread {
                    tracks_placeholder.beVisibleIf(playlistTracks.isEmpty())
                    tracks_placeholder_2.beVisibleIf(playlistTracks.isEmpty())
                }

                tracks.addAll(playlistTracks)
                listItems.addAll(tracks)
            } else {
                val albumTracks = getAlbumTracksSync(album.id)
                albumTracks.sortWith(compareBy({ it.trackId }, { it.title.toLowerCase() }))
                tracks.addAll(albumTracks)

                val coverArt = ContentUris.withAppendedId(artworkUri, album.id).toString()
                val header = AlbumHeader(album.title, coverArt, album.year, tracks.size, tracks.sumBy { it.duration }, album.artist)
                listItems.add(header)
                listItems.addAll(tracks)
            }

            runOnUiThread {
                val adapter = if (playlist != null) {
                    TracksAdapter(this, tracks, true, tracks_list, tracks_fastscroller) {
                        itemClicked(it as Track)
                    }
                } else {
                    TracksHeaderAdapter(this, listItems, tracks_list, tracks_fastscroller) {
                        itemClicked(it as Track)
                    }
                }

                tracks_list.adapter = adapter
                tracks_list.scheduleLayoutAnimation()

                tracks_fastscroller.setViews(tracks_list) {
                    val listItem = when (adapter) {
                        is TracksAdapter -> adapter.tracks.getOrNull(it)
                        is TracksHeaderAdapter -> adapter.items.getOrNull(it)
                        else -> return@setViews
                    }

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
        menuInflater.inflate(R.menu.menu_playlist, menu)

        menu.apply {
            findItem(R.id.sort).isVisible = playlist != null
            findItem(R.id.add_file_to_playlist).isVisible = playlist != null
            findItem(R.id.add_folder_to_playlist).isVisible = playlist != null
        }

        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.add_file_to_playlist -> addFileToPlaylist()
            R.id.add_folder_to_playlist -> addFolderToPlaylist()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, ACTIVITY_PLAYLIST) {
            val adapter = tracks_list.adapter as? TracksAdapter ?: return@ChangeSortingDialog
            val tracks = adapter.tracks
            Track.sorting = config.playlistTracksSorting
            tracks.sort()
            adapter.updateItems(tracks, forceUpdate = true)
        }
    }

    private fun addFileToPlaylist() {
        FilePickerDialog(this, lastFilePickerPath) { path ->
            ensureBackgroundThread {
                lastFilePickerPath = path
                if (path.isAudioFast()) {
                    addTrackFromPath(path, true)
                } else {
                    toast(R.string.invalid_file_format)
                }
            }
        }
    }

    private fun addTrackFromPath(path: String, rescanWrongPath: Boolean) {
        val mediaStoreId = getMediaStoreIdFromPath(path)
        if (mediaStoreId == 0L) {
            if (rescanWrongPath) {
                rescanPaths(arrayListOf(path)) {
                    addTrackFromPath(path, false)
                }
            } else {
                toast(R.string.unknown_error_occurred)
            }
        } else {
            var track = tracksDAO.getTrackWithMediaStoreId(mediaStoreId)
            if (track == null) {
                track = RoomHelper(this).getTrackFromPath(path)
            }

            if (track != null) {
                track.id = 0
                track.playListId = playlist!!.id
                tracksDAO.insert(track)
                refreshPlaylist()
            }
        }
    }

    private fun addFolderToPlaylist() {
        FilePickerDialog(this, pickFile = false) {
            ensureBackgroundThread {
                getFolderTracks(it, true) { tracks ->
                    tracks.forEach {
                        it.playListId = playlist!!.id
                    }

                    tracksDAO.insertAll(tracks)
                    refreshPlaylist()
                }
            }
        }
    }

    private fun refreshPlaylist() {
        EventBus.getDefault().post(Events.PlaylistsUpdated())

        val newTracks = tracksDAO.getTracksFromPlaylist(playlist!!.id).toMutableList() as ArrayList<Track>
        runOnUiThread {
            (tracks_list.adapter as? TracksAdapter)?.updateItems(newTracks)

            tracks_placeholder.beVisibleIf(newTracks.isEmpty())
            tracks_placeholder_2.beVisibleIf(newTracks.isEmpty())
        }
    }

    private fun updateCurrentTrackBar() {
        current_track_bar.updateColors()
        current_track_bar.updateCurrentTrack(MusicService.mCurrTrack)
        current_track_bar.updateTrackState(MusicService.getIsPlaying())
    }

    private fun itemClicked(track: Track) {
        val tracks = if (playlist != null) {
            (tracks_list.adapter as? TracksAdapter)?.tracks?.toMutableList() as? ArrayList<Track> ?: ArrayList()
        } else {
            (tracks_list.adapter as? TracksHeaderAdapter)?.items?.filterIsInstance<Track>()?.toMutableList() as? ArrayList<Track>
                ?: ArrayList()
        }

        resetQueueItems(tracks) {
            Intent(this, TrackActivity::class.java).apply {
                putExtra(TRACK, Gson().toJson(track))
                putExtra(RESTART_PLAYER, true)
                startActivity(this)
            }
        }
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
