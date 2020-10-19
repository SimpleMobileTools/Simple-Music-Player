package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import com.simplemobiletools.musicplayer.activities.SimpleActivity

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    abstract fun setupFragment(activity: SimpleActivity)

    abstract fun finishActMode()
}
