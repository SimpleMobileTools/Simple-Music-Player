package com.simplemobiletools.musicplayer.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.fragments.MyViewPagerFragment

class ViewPagerAdapter(val activity: SimpleActivity) : PagerAdapter() {
    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment).apply {
            setupFragment(activity)
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = 3

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int) = when (position) {
        0 -> R.layout.fragment_songs
        1 -> R.layout.fragment_playlists
        else -> R.layout.fragment_artists
    }
}
