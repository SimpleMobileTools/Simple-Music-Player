package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.PlaylistsAdapter
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.interfaces.RefreshItemsListener
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.activity_playlists.*

class PlaylistsActivity : SimpleActivity(), RefreshItemsListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        getPlaylists()
    }

    private fun getPlaylists() {
        dbHelper.getPlaylists {
            runOnUiThread {
                PlaylistsAdapter(this@PlaylistsActivity, it, this@PlaylistsActivity, playlists_list) {
                    getPlaylists()
                    playlistChanged((it as Playlist).id)
                }.apply {
                    setupDragListener(true)
                    playlists_list.adapter = this
                }
            }
        }
    }

    override fun refreshItems() {
        getPlaylists()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_playlists, menu)
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
