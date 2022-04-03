package com.simplemobiletools.musicplayer.activities

import android.app.SearchManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
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

// this activity is used for displaying Playlist and Folder tracks, also Album tracks with a possible album header at the top
// Artists -> Albums -> Tracks
class TracksActivity : SimpleActivity() {
    private val TYPE_PLAYLIST = 1
    private val TYPE_FOLDER = 2
    private val TYPE_ALBUM = 3

    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null
    private var tracksIgnoringSearch = ArrayList<Track>()
    private var bus: EventBus? = null
    private var playlist: Playlist? = null
    private var tracksType = 0
    private var lastFilePickerPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)
        bus = EventBus.getDefault()
        bus!!.register(this)

        val playlistType = object : TypeToken<Playlist>() {}.type
        playlist = Gson().fromJson<Playlist>(intent.getStringExtra(PLAYLIST), playlistType)
        if (playlist != null) {
            tracksType = TYPE_PLAYLIST
            invalidateOptionsMenu()
        }

        val albumType = object : TypeToken<Album>() {}.type
        val album = Gson().fromJson<Album>(intent.getStringExtra(ALBUM), albumType)
        if (album != null) {
            tracksType = TYPE_ALBUM
        }

        val folder = intent.getStringExtra(FOLDER)
        if (folder != null) {
            tracksType = TYPE_FOLDER
            tracks_placeholder_2.beGone()
        }

        val titleToUse = playlist?.title ?: album?.title ?: folder ?: ""
        title = titleToUse.replace("<", "&lt;")

        val properPrimaryColor = getProperPrimaryColor()
        tracks_fastscroller.updateColors(properPrimaryColor)
        tracks_placeholder.setTextColor(getProperTextColor())
        tracks_placeholder_2.setTextColor(properPrimaryColor)
        tracks_placeholder_2.underlineText()
        tracks_placeholder_2.setOnClickListener {
            addFolderToPlaylist()
        }

        ensureBackgroundThread {
            val showFilename = config.showFilename
            val tracks = ArrayList<Track>()
            val listItems = ArrayList<ListItem>()
            when (tracksType) {
                TYPE_PLAYLIST -> {
                    val playlistTracks = tracksDAO.getTracksFromPlaylist(playlist!!.id).map { track ->
                        track.title = track.getProperTitle(showFilename)
                        track
                    }
                    runOnUiThread {
                        tracks_placeholder.beVisibleIf(playlistTracks.isEmpty())
                        tracks_placeholder_2.beVisibleIf(playlistTracks.isEmpty())
                    }

                    tracks.addAll(playlistTracks)
                    listItems.addAll(tracks)
                }
                TYPE_ALBUM -> {
                    val albumTracks = getAlbumTracksSync(album.id)
                    albumTracks.sortWith(compareBy({ it.trackId }, { it.title.toLowerCase() }))
                    tracks.addAll(albumTracks)

                    val coverArt = ContentUris.withAppendedId(artworkUri, album.id).toString()
                    val header = AlbumHeader(album.title, coverArt, album.year, tracks.size, tracks.sumBy { it.duration }, album.artist)
                    listItems.add(header)
                    listItems.addAll(tracks)
                }
                else -> {
                    val folderTracks = tracksDAO.getTracksFromFolder(folder ?: "").map { track ->
                        track.title = track.getProperTitle(showFilename)
                        track
                    }
                    runOnUiThread {
                        tracks_placeholder.beVisibleIf(folderTracks.isEmpty())
                    }

                    tracks.addAll(folderTracks)
                    listItems.addAll(tracks)
                }
            }

            runOnUiThread {
                val adapter = if (tracksType == TYPE_ALBUM) {
                    TracksHeaderAdapter(this, listItems, tracks_list) {
                        itemClicked(it as Track)
                    }
                } else {
                    val isPlaylistContent = tracksType == TYPE_PLAYLIST
                    TracksAdapter(this, tracks, isPlaylistContent, tracks_list) {
                        itemClicked(it as Track)
                    }
                }

                tracks_list.adapter = adapter

                if (areSystemAnimationsEnabled) {
                    tracks_list.scheduleLayoutAnimation()
                }
            }
        }

        current_track_bar.setOnClickListener {
            hideKeyboard()
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
            findItem(R.id.search).isVisible = tracksType != TYPE_ALBUM
            findItem(R.id.sort).isVisible = tracksType != TYPE_ALBUM
            findItem(R.id.add_file_to_playlist).isVisible = tracksType == TYPE_PLAYLIST
            findItem(R.id.add_folder_to_playlist).isVisible = tracksType == TYPE_PLAYLIST
        }

        setupSearch(menu)
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

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                onSearchOpened()
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                onSearchClosed()
                isSearchOpen = false
                return true
            }
        })
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, ACTIVITY_PLAYLIST_FOLDER) {
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

    private fun onSearchOpened() {
        tracksIgnoringSearch = (tracks_list.adapter as? TracksAdapter)?.tracks ?: return
    }

    private fun onSearchClosed() {
        (tracks_list.adapter as? TracksAdapter)?.updateItems(tracksIgnoringSearch)
        tracks_placeholder.beGoneIf(tracksIgnoringSearch.isNotEmpty())
    }

    private fun onSearchQueryChanged(text: String) {
        val filtered = tracksIgnoringSearch.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Track>
        (tracks_list.adapter as? TracksAdapter)?.updateItems(filtered, text)
        tracks_placeholder.beGoneIf(filtered.isNotEmpty())
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
        val tracks = when (tracksType) {
            TYPE_ALBUM -> (tracks_list.adapter as? TracksHeaderAdapter)?.items?.filterIsInstance<Track>()?.toMutableList() as? ArrayList<Track> ?: ArrayList()
            else -> (tracks_list.adapter as? TracksAdapter)?.tracks?.toMutableList() as? ArrayList<Track> ?: ArrayList()
        }

        resetQueueItems(tracks) {
            hideKeyboard()
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
