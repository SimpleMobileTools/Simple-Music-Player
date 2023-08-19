package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import androidx.media3.common.MediaItem
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.SimpleControllerActivity

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    abstract fun setupFragment(activity: BaseSimpleActivity)

    abstract fun finishActMode()

    abstract fun onSearchQueryChanged(text: String)

    abstract fun onSearchClosed()

    abstract fun onSortOpen(activity: SimpleActivity)

    abstract fun setupColors(textColor: Int, adjustedPrimaryColor: Int)

    fun playMediaItems(mediaItems: List<MediaItem>, startIndex: Int = 0, startPosition: Long = 0, startActivity: Boolean = true) {
        (context as SimpleControllerActivity).playMediaItems(mediaItems, startIndex, startPosition, startActivity)
    }
}
