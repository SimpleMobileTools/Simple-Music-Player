package com.simplemobiletools.musicplayer.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.models.Song
import kotlinx.android.synthetic.main.song.view.*
import java.util.*

class SongAdapter(context: Context, val songs: ArrayList<Song>) : BaseAdapter() {
    companion object {
        lateinit var mInflater: LayoutInflater
    }

    init {
        mInflater = LayoutInflater.from(context)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        var view = convertView
        val holder: ViewHolder
        if (view == null) {
            view = mInflater.inflate(R.layout.song, parent, false)
            holder = ViewHolder(view)
            view.tag = holder
        } else {
            holder = view.tag as ViewHolder
        }

        val song = songs[position]
        holder.title.text = song.title
        holder.artist.text = song.artist

        return view!!
    }

    override fun getCount() = songs.size

    override fun getItem(i: Int) = songs[i]

    override fun getItemId(i: Int) = 0L

    internal class ViewHolder constructor(view: View) {
        val title = view.song_title
        val artist = view.song_artist
    }
}
