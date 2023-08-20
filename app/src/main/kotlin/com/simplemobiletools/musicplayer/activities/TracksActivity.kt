package com.simplemobiletools.musicplayer.activities

import android.app.Activity
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.helpers.isOreoPlus
import com.simplemobiletools.commons.helpers.isQPlus
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.TracksAdapter
import com.simplemobiletools.musicplayer.adapters.TracksAdapter.Companion.TYPE_ALBUM
import com.simplemobiletools.musicplayer.adapters.TracksAdapter.Companion.TYPE_FOLDER
import com.simplemobiletools.musicplayer.adapters.TracksAdapter.Companion.TYPE_PLAYLIST
import com.simplemobiletools.musicplayer.adapters.TracksAdapter.Companion.TYPE_TRACKS
import com.simplemobiletools.musicplayer.adapters.TracksHeaderAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.ExportPlaylistDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.getFolderTracks
import com.simplemobiletools.musicplayer.extensions.getMediaStoreIdFromPath
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.helpers.M3uExporter.ExportResult
import com.simplemobiletools.musicplayer.models.*
import kotlinx.android.synthetic.main.activity_tracks.*
import kotlinx.android.synthetic.main.view_current_track_bar.current_track_bar
import org.greenrobot.eventbus.EventBus
import java.io.OutputStream

// this activity is used for displaying Playlist and Folder tracks, also Album tracks with a possible album header at the top
// Artists -> Albums -> Tracks
class TracksActivity : SimpleMusicActivity() {
    private val PICK_EXPORT_FILE_INTENT = 2

    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null
    private var tracksIgnoringSearch = ArrayList<Track>()
    private var playlist: Playlist? = null
    private var folder: String? = null
    private var sourceType = 0
    private var lastFilePickerPath = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tracks)
        setupOptionsMenu()
        refreshMenuItems()

        updateMaterialActivityViews(tracks_coordinator, tracks_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(tracks_list, tracks_toolbar)

        val properPrimaryColor = getProperPrimaryColor()
        tracks_fastscroller.updateColors(properPrimaryColor)
        tracks_placeholder.setTextColor(getProperTextColor())
        tracks_placeholder_2.setTextColor(properPrimaryColor)
        tracks_placeholder_2.underlineText()
        tracks_placeholder_2.setOnClickListener {
            addFolderToPlaylist()
        }

        setupCurrentTrackBar(current_track_bar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(tracks_toolbar, NavigationIcon.Arrow, searchMenuItem = searchMenuItem)
        refreshTracks()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_EXPORT_FILE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            try {
                val outputStream = contentResolver.openOutputStream(resultData.data!!)
                exportPlaylistTo(outputStream)
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun refreshMenuItems() {
        tracks_toolbar.menu.apply {
            findItem(R.id.search).isVisible = sourceType != TYPE_ALBUM
            findItem(R.id.sort).isVisible = sourceType != TYPE_ALBUM
            findItem(R.id.add_file_to_playlist).isVisible = sourceType == TYPE_PLAYLIST
            findItem(R.id.add_folder_to_playlist).isVisible = sourceType == TYPE_PLAYLIST
            findItem(R.id.export_playlist).isVisible = sourceType == TYPE_PLAYLIST && isOreoPlus()
        }
    }

    private fun setupOptionsMenu() {
        setupSearch(tracks_toolbar.menu)
        tracks_toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.add_file_to_playlist -> addFileToPlaylist()
                R.id.add_folder_to_playlist -> addFolderToPlaylist()
                R.id.export_playlist -> tryExportPlaylist()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
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

    private fun refreshTracks() {
        val playlistType = object : TypeToken<Playlist>() {}.type
        playlist = Gson().fromJson<Playlist>(intent.getStringExtra(PLAYLIST), playlistType)
        if (playlist != null) {
            sourceType = TYPE_PLAYLIST
        }

        val albumType = object : TypeToken<Album>() {}.type
        val album = Gson().fromJson<Album>(intent.getStringExtra(ALBUM), albumType)
        if (album != null) {
            sourceType = TYPE_ALBUM
        }

        val genreType = object : TypeToken<Genre>() {}.type
        val genre = Gson().fromJson<Genre>(intent.getStringExtra(GENRE), genreType)
        if (genre != null) {
            sourceType = TYPE_TRACKS
        }

        folder = intent.getStringExtra(FOLDER)
        if (folder != null) {
            sourceType = TYPE_FOLDER
            tracks_placeholder_2.beGone()
        }

        val titleToUse = playlist?.title ?: album?.title ?: genre?.title ?: folder ?: ""
        tracks_toolbar.title = titleToUse
        refreshMenuItems()

        ensureBackgroundThread {
            val tracks = ArrayList<Track>()
            val listItems = ArrayList<ListItem>()
            when (sourceType) {
                TYPE_PLAYLIST -> {
                    val playlistTracks = audioHelper.getPlaylistTracks(playlist!!.id)
                    runOnUiThread {
                        tracks_placeholder.beVisibleIf(playlistTracks.isEmpty())
                        tracks_placeholder_2.beVisibleIf(playlistTracks.isEmpty())
                    }

                    tracks.addAll(playlistTracks)
                    listItems.addAll(tracks)
                }
                TYPE_ALBUM -> {
                    val albumTracks = audioHelper.getAlbumTracks(album.id)
                    tracks.addAll(albumTracks)

                    val header = AlbumHeader(album.id, album.title, album.coverArt, album.year, tracks.size, tracks.sumOf { it.duration }, album.artist)
                    listItems.add(header)
                    listItems.addAll(tracks)
                }
                TYPE_TRACKS -> {
                    val genreTracks = audioHelper.getGenreTracks(genre.id)
                    tracks.addAll(genreTracks)
                }
                else -> {
                    val folderTracks = audioHelper.getFolderTracks(folder.orEmpty())
                    runOnUiThread {
                        tracks_placeholder.beVisibleIf(folderTracks.isEmpty())
                    }

                    tracks.addAll(folderTracks)
                    listItems.addAll(tracks)
                }
            }

            runOnUiThread {
                if (sourceType == TYPE_ALBUM) {
                    val currAdapter = tracks_list.adapter
                    if (currAdapter == null) {
                        TracksHeaderAdapter(this, listItems, tracks_list) {
                            itemClicked(it as Track)
                        }.apply {
                            tracks_list.adapter = this
                        }

                        if (areSystemAnimationsEnabled) {
                            tracks_list.scheduleLayoutAnimation()
                        }
                    } else {
                        (currAdapter as TracksHeaderAdapter).updateItems(listItems)
                    }
                } else {
                    val currAdapter = tracks_list.adapter
                    if (currAdapter == null) {
                        TracksAdapter(
                            activity = this,
                            recyclerView = tracks_list,
                            sourceType = sourceType,
                            folder = folder,
                            playlist = playlist,
                            items = tracks
                        ) {
                            itemClicked(it as Track)
                        }.apply {
                            tracks_list.adapter = this
                        }

                        if (areSystemAnimationsEnabled) {
                            tracks_list.scheduleLayoutAnimation()
                        }
                    } else {
                        (currAdapter as TracksAdapter).updateItems(tracks)
                    }
                }
            }
        }
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this, ACTIVITY_PLAYLIST_FOLDER, playlist, folder) {
            val adapter = tracks_list.adapter as? TracksAdapter ?: return@ChangeSortingDialog
            val tracks = adapter.items
            val sorting = when (sourceType) {
                TYPE_PLAYLIST -> config.getProperPlaylistSorting(playlist?.id ?: -1)
                TYPE_TRACKS -> config.trackSorting
                else -> config.getProperFolderSorting(folder ?: "")
            }

            tracks.sortSafely(sorting)
            adapter.updateItems(tracks, forceUpdate = true)

            if (sourceType == TYPE_TRACKS) {
                EventBus.getDefault().post(Events.RefreshTracks())
            }
        }
    }

    private fun addFileToPlaylist() {
        FilePickerDialog(this, lastFilePickerPath, enforceStorageRestrictions = false) { path ->
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
            var track = audioHelper.getTrack(mediaStoreId)
            if (track == null) {
                track = RoomHelper(this).getTrackFromPath(path)
            }

            if (track != null) {
                track.id = 0
                track.playListId = playlist!!.id
                audioHelper.insertTracks(listOf(track))
                refreshPlaylist()
            }
        }
    }

    private fun addFolderToPlaylist() {
        FilePickerDialog(this, pickFile = false, enforceStorageRestrictions = false) {
            ensureBackgroundThread {
                getFolderTracks(it, true) { tracks ->
                    tracks.forEach {
                        it.playListId = playlist!!.id
                    }

                    audioHelper.insertTracks(tracks)
                    refreshPlaylist()
                }
            }
        }
    }

    private fun onSearchOpened() {
        tracksIgnoringSearch = (tracks_list.adapter as? TracksAdapter)?.items ?: return
    }

    private fun onSearchClosed() {
        (tracks_list.adapter as? TracksAdapter)?.updateItems(tracksIgnoringSearch)
        tracks_placeholder.beGoneIf(tracksIgnoringSearch.isNotEmpty())
    }

    private fun onSearchQueryChanged(text: String) {
        val filtered = tracksIgnoringSearch.filter {
            it.title.contains(text, true) || ("${it.artist} - ${it.album}").contains(text, true)
        }.toMutableList() as ArrayList<Track>
        (tracks_list.adapter as? TracksAdapter)?.updateItems(filtered, text)
        tracks_placeholder.beGoneIf(filtered.isNotEmpty())
    }

    private fun refreshPlaylist() {
        EventBus.getDefault().post(Events.PlaylistsUpdated())

        val newTracks = audioHelper.getPlaylistTracks(playlist!!.id)
        runOnUiThread {
            (tracks_list.adapter as? TracksAdapter)?.updateItems(newTracks)
            tracks_placeholder.beVisibleIf(newTracks.isEmpty())
            tracks_placeholder_2.beVisibleIf(newTracks.isEmpty())
        }
    }

    private fun itemClicked(track: Track) {
        val tracks = when (sourceType) {
            TYPE_ALBUM -> (tracks_list.adapter as? TracksHeaderAdapter)?.items?.filterIsInstance<Track>()
            else -> (tracks_list.adapter as? TracksAdapter)?.items
        } ?: ArrayList()

        handleNotificationPermission { granted ->
            if (granted) {
                val startIndex = tracks.indexOf(track)
                prepareAndPlay(tracks, startIndex)
            } else {
                PermissionRequiredDialog(this, R.string.allow_notifications_music_player, { openNotificationSettings() })
            }
        }
    }

    private fun tryExportPlaylist() {
        if (isQPlus()) {
            ExportPlaylistDialog(this, config.lastExportPath, true) { file ->
                Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                    type = MIME_TYPE_M3U
                    putExtra(Intent.EXTRA_TITLE, file.name)
                    addCategory(Intent.CATEGORY_OPENABLE)

                    try {
                        startActivityForResult(this, PICK_EXPORT_FILE_INTENT)
                    } catch (e: ActivityNotFoundException) {
                        toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                    } catch (e: Exception) {
                        showErrorToast(e)
                    }
                }
            }
        } else {
            handlePermission(getPermissionToRequest()) { granted ->
                if (granted) {
                    ExportPlaylistDialog(this, config.lastExportPath, false) { file ->
                        getFileOutputStream(file.toFileDirItem(this), true) { outputStream ->
                            exportPlaylistTo(outputStream)
                        }
                    }
                }
            }
        }
    }

    private fun exportPlaylistTo(outputStream: OutputStream?) {
        val tracks = (tracks_list.adapter as TracksAdapter).items

        if (tracks.isEmpty()) {
            toast(R.string.no_entries_for_exporting)
            return
        }

        M3uExporter(this).exportPlaylist(outputStream, tracks) { result ->
            toast(
                when (result) {
                    ExportResult.EXPORT_OK -> R.string.exporting_successful
                    ExportResult.EXPORT_PARTIAL -> R.string.exporting_some_entries_failed
                    else -> R.string.exporting_failed
                }
            )
        }
    }
}
