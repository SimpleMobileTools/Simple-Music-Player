package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import kotlinx.android.synthetic.main.fragment_folders.view.*

class FoldersFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    override fun setupFragment(activity: SimpleActivity) {}

    override fun finishActMode() {
        (folders_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {}

    override fun onSearchOpened() {}

    override fun onSearchClosed() {}

    override fun onSortOpen(activity: SimpleActivity) {}

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        folders_placeholder.setTextColor(textColor)
        folders_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
