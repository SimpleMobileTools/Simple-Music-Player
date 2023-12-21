package org.fossify.musicplayer.fragments

import android.content.Context
import android.util.AttributeSet
import android.widget.RelativeLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.musicplayer.activities.SimpleActivity
import org.fossify.musicplayer.activities.SimpleControllerActivity
import org.fossify.musicplayer.models.Track

abstract class MyViewPagerFragment(context: Context, attributeSet: AttributeSet) : RelativeLayout(context, attributeSet) {
    abstract fun setupFragment(activity: BaseSimpleActivity)

    abstract fun finishActMode()

    abstract fun onSearchQueryChanged(text: String)

    abstract fun onSearchClosed()

    abstract fun onSortOpen(activity: SimpleActivity)

    abstract fun setupColors(textColor: Int, adjustedPrimaryColor: Int)

    fun prepareAndPlay(tracks: List<Track>, startIndex: Int = 0, startPositionMs: Long = 0, startActivity: Boolean = true) {
        (context as SimpleControllerActivity).prepareAndPlay(tracks, startIndex, startPositionMs, startActivity)
    }
}
