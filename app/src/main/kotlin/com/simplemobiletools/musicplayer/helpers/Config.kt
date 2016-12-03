package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.content.SharedPreferences
import com.simplemobiletools.musicplayer.extensions.getSharedPrefs

class Config private constructor(context: Context) {
    private val mPrefs: SharedPreferences

    companion object {
        fun newInstance(context: Context) = Config(context)
    }

    init {
        mPrefs = context.getSharedPrefs()
    }

    var isFirstRun: Boolean
        get() = mPrefs.getBoolean(IS_FIRST_RUN, true)
        set(firstRun) = mPrefs.edit().putBoolean(IS_FIRST_RUN, firstRun).apply()

    var isDarkTheme: Boolean
        get() = mPrefs.getBoolean(IS_DARK_THEME, false)
        set(isDarkTheme) = mPrefs.edit().putBoolean(IS_DARK_THEME, isDarkTheme).apply()

    var isShuffleEnabled: Boolean
        get() = mPrefs.getBoolean(SHUFFLE, true)
        set(shuffle) = mPrefs.edit().putBoolean(SHUFFLE, shuffle).apply()

    var isNumericProgressEnabled: Boolean
        get() = mPrefs.getBoolean(NUMERIC_PROGRESS, false)
        set(enabled) = mPrefs.edit().putBoolean(NUMERIC_PROGRESS, enabled).apply()

    var sorting: Int
        get() = mPrefs.getInt(SORTING, SORT_BY_TITLE)
        set(sorting) = mPrefs.edit().putInt(SORTING, sorting).apply()

    var equalizer: Int
        get() = mPrefs.getInt(EQUALIZER, 0)
        set(equalizer) = mPrefs.edit().putInt(EQUALIZER, equalizer).apply()

    var repeatSong: Boolean
        get() = mPrefs.getBoolean(REPEAT_SONG, false)
        set(repeat) = mPrefs.edit().putBoolean(REPEAT_SONG, repeat).apply()
}
