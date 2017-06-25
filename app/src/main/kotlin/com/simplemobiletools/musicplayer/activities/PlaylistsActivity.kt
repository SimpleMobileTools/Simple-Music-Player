package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import android.support.v7.widget.DividerItemDecoration
import android.view.Menu
import android.view.MenuItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.PlaylistsAdapter
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.extensions.playlistChanged
import com.simplemobiletools.musicplayer.interfaces.RefreshItemsListener
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
                playlists_list.apply {
                    adapter = PlaylistsAdapter(this@PlaylistsActivity, it, this@PlaylistsActivity) {
                        getPlaylists()
                        playlistChanged(it.id)
                    }

                    DividerItemDecoration(context, DividerItemDecoration.VERTICAL).apply {
                        setDrawable(context.resources.getDrawable(R.drawable.divider))
                        addItemDecoration(this)
                    }
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
