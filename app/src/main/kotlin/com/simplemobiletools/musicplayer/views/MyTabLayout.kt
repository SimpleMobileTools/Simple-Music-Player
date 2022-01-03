package com.simplemobiletools.musicplayer.views

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import com.google.android.material.tabs.TabLayout

// make the tabLayout scrollable horizotally when needed, else make sure it fills whole width
class MyTabLayout : TabLayout {
    constructor(context: Context?) : super(context!!)
    constructor(context: Context?, attrs: AttributeSet?) : super(context!!, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context!!, attrs, defStyleAttr)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        try {
            if (tabCount == 0) {
                return
            }

            val tabLayout = getChildAt(0) as ViewGroup
            val childCount = tabLayout.childCount
            val widths = IntArray(childCount + 1)

            for (i in 0 until childCount) {
                widths[i] = tabLayout.getChildAt(i).measuredWidth
                widths[childCount] += widths[i]
            }

            val measuredWidth = measuredWidth
            for (i in 0 until childCount) {
                tabLayout.getChildAt(i).minimumWidth = measuredWidth * widths[i] / widths[childCount]
            }
        } catch (ignored: Exception) {
        }
    }
}
