package com.simplemobiletools.musicplayer.views

import android.content.Context
import android.graphics.Rect
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatTextView

class MarqueeTextView(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : AppCompatTextView(context!!, attrs, defStyleAttr),
    View.OnLayoutChangeListener {

    constructor(context: Context?) : this(context, null)

    constructor(context: Context?, attrs: AttributeSet?) : this(context, attrs, 0)

    init {
        setSingleLine()
        ellipsize = TextUtils.TruncateAt.MARQUEE
        marqueeRepeatLimit = -1
        isSelected = true
        addOnLayoutChangeListener(this)
    }

    override fun isFocused() = true

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        if (hasWindowFocus) super.onWindowFocusChanged(hasWindowFocus)
    }

    override fun onFocusChanged(focused: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (focused) super.onFocusChanged(focused, direction, previouslyFocusedRect)
    }

    override fun onLayoutChange(v: View?, left: Int, top: Int, right: Int, bottom: Int, oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int) {
        val layoutParams = layoutParams
        layoutParams.height = bottom - top
        layoutParams.width = right - left
        removeOnLayoutChangeListener(this)
        setLayoutParams(layoutParams)
    }
}
