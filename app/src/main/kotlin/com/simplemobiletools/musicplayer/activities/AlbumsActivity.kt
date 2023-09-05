package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.dialogs.PermissionRequiredDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.AlbumsTracksAdapter
import com.simplemobiletools.musicplayer.databinding.ActivityAlbumsBinding
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.helpers.ALBUM
import com.simplemobiletools.musicplayer.helpers.ARTIST
import com.simplemobiletools.musicplayer.models.*

// Artists -> Albums -> Tracks
class AlbumsActivity : SimpleMusicActivity() {

    private val binding by viewBinding(ActivityAlbumsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        updateMaterialActivityViews(binding.albumsCoordinator, binding.albumsHolder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(binding.albumsList, binding.albumsToolbar)

        binding.albumsFastscroller.updateColors(getProperPrimaryColor())

        val artistType = object : TypeToken<Artist>() {}.type
        val artist = Gson().fromJson<Artist>(intent.getStringExtra(ARTIST), artistType)
        binding.albumsToolbar.title = artist.title

        ensureBackgroundThread {
            val albums = audioHelper.getArtistAlbums(artist.id)
            val listItems = ArrayList<ListItem>()
            val albumsSectionLabel = resources.getQuantityString(R.plurals.albums_plural, albums.size, albums.size)
            listItems.add(AlbumSection(albumsSectionLabel))
            listItems.addAll(albums)

            val albumTracks = audioHelper.getAlbumTracks(albums)
            val trackFullDuration = albumTracks.sumOf { it.duration }

            var tracksSectionLabel = resources.getQuantityString(R.plurals.tracks_plural, albumTracks.size, albumTracks.size)
            tracksSectionLabel += " • ${trackFullDuration.getFormattedDuration(true)}"
            listItems.add(AlbumSection(tracksSectionLabel))
            listItems.addAll(albumTracks)

            runOnUiThread {
                AlbumsTracksAdapter(this, listItems, binding.albumsList) {
                    hideKeyboard()
                    if (it is Album) {
                        Intent(this, TracksActivity::class.java).apply {
                            putExtra(ALBUM, Gson().toJson(it))
                            startActivity(this)
                        }
                    } else {
                        handleNotificationPermission { granted ->
                            if (granted) {
                                val startIndex = albumTracks.indexOf(it as Track)
                                prepareAndPlay(albumTracks, startIndex)
                            } else {
                                PermissionRequiredDialog(
                                    this,
                                    com.simplemobiletools.commons.R.string.allow_notifications_music_player,
                                    { openNotificationSettings() }
                                )
                            }
                        }
                    }
                }.apply {
                    binding.albumsList.adapter = this
                }

                if (areSystemAnimationsEnabled) {
                    binding.albumsList.scheduleLayoutAnimation()
                }
            }
        }

        setupCurrentTrackBar(binding.currentTrackBar.root)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(binding.albumsToolbar, NavigationIcon.Arrow)
    }
}
