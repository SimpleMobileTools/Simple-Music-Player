package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.PlaylistsAdapter
import com.simplemobiletools.musicplayer.extensions.dbHelper
import com.simplemobiletools.musicplayer.interfaces.DeleteItemsListener
import kotlinx.android.synthetic.main.activity_playlists.*
import java.util.*

class PlaylistsActivity : SimpleActivity(), DeleteItemsListener {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlists)
        getPlaylists()
    }

    private fun getPlaylists() {
        dbHelper.getPlaylists {
            runOnUiThread {
                playlists_list.adapter = PlaylistsAdapter(this, it, this) {

                }
            }
        }
    }

    override fun deleteItems(ids: ArrayList<Int>) {

    }
}
