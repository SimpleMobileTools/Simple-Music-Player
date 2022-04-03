package com.simplemobiletools.musicplayer.adapters

import android.view.View
import android.view.ViewGroup
import androidx.viewpager.widget.PagerAdapter
import com.simplemobiletools.commons.extensions.getProperPrimaryColor
import com.simplemobiletools.commons.extensions.getProperTextColor
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.fragments.MyViewPagerFragment
import com.simplemobiletools.musicplayer.helpers.*

class ViewPagerAdapter(val activity: SimpleActivity) : PagerAdapter() {
    val showTabs = activity.config.showTabs

    override fun instantiateItem(container: ViewGroup, position: Int): Any {
        val layout = getFragment(position)
        val view = activity.layoutInflater.inflate(layout, container, false)
        container.addView(view)

        (view as MyViewPagerFragment).apply {
            setupFragment(activity)
            setupColors(activity.getProperTextColor(), activity.getProperPrimaryColor())
        }

        return view
    }

    override fun destroyItem(container: ViewGroup, position: Int, item: Any) {
        container.removeView(item as View)
    }

    override fun getCount() = tabsList.filter { it and showTabs != 0 }.size

    override fun isViewFromObject(view: View, item: Any) = view == item

    private fun getFragment(position: Int): Int {
        val fragments = arrayListOf<Int>()
        if (showTabs and TAB_PLAYLISTS != 0) {
            fragments.add(R.layout.fragment_playlists)
        }

        if (showTabs and TAB_FOLDERS != 0) {
            fragments.add(R.layout.fragment_folders)
        }

        if (showTabs and TAB_ARTISTS != 0) {
            fragments.add(R.layout.fragment_artists)
        }

        if (showTabs and TAB_ALBUMS != 0) {
            fragments.add(R.layout.fragment_albums)
        }

        if (showTabs and TAB_TRACKS != 0) {
            fragments.add(R.layout.fragment_tracks)
        }

        return fragments[position]
    }
}
