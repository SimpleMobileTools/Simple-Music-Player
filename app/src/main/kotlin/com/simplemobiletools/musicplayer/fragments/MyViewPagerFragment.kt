package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    abstract fun setupFragment(activity: BaseSimpleActivity)

    abstract fun finishActMode()

    abstract fun onSearchQueryChanged(text: String)

    abstract fun onSearchClosed()

    abstract fun onSortOpen(activity: SimpleActivity)

    abstract fun setupColors(textColor: Int, adjustedPrimaryColor: Int)
}
