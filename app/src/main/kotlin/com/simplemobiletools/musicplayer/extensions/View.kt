package com.simplemobiletools.musicplayer.extensions

import android.view.View
import androidx.viewbinding.ViewBinding

inline fun <T : ViewBinding> View.viewBinding(crossinline bind: (View) -> T) =
    lazy(LazyThreadSafetyMode.NONE) {
        bind(this)
    }
