package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.PlaylistsAdapter
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.extensions.playlistDAO
import com.simplemobiletools.musicplayer.interfaces.RefreshPlaylistsListener
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.activity_playlists.*

class PlaylistsActivity : SimpleActivity(), RefreshPlaylistsListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        getPlaylists()
    }

    private fun getPlaylists() {
        ensureBackgroundThread {
            val playlists = playlistDAO.getAll() as ArrayList<Playlist>
            runOnUiThread {
                PlaylistsAdapter(this@PlaylistsActivity, playlists, this@PlaylistsActivity, playlists_list) {
                    getPlaylists()
                    playlistChanged((it as Playlist).id)
                }.apply {
                    playlists_list.adapter = this
                }
            }
        }
    }

    override fun refreshItems() {
        getPlaylists()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_playlists, menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.create_playlist -> showCreatePlaylistFolder()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun showCreatePlaylistFolder() {
        NewPlaylistDialog(this) {
            getPlaylists()
        }
    }
}
