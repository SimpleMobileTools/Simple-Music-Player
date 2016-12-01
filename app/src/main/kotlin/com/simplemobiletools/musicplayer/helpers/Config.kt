package com.simplemobiletools.musicplayer.helpers

import android.content.Context
import android.content.SharedPreferences
import com.simplemobiletools.musicplayer.Constants
import com.simplemobiletools.musicplayer.extensions.getSharedPrefs

class Config private constructor(context: Context) {
    private val mPrefs: SharedPreferences

    companion object {
        val SORT_BY_TITLE = 0

        fun newInstance(context: Context) = Config(context)
    }

    init {
        mPrefs = context.getSharedPrefs()
    }

    var isFirstRun: Boolean
        get() = mPrefs.getBoolean(Constants.IS_FIRST_RUN, true)
        set(firstRun) = mPrefs.edit().putBoolean(Constants.IS_FIRST_RUN, firstRun).apply()

    var isDarkTheme: Boolean
        get() = mPrefs.getBoolean(Constants.IS_DARK_THEME, false)
        set(isDarkTheme) = mPrefs.edit().putBoolean(Constants.IS_DARK_THEME, isDarkTheme).apply()

    var isShuffleEnabled: Boolean
        get() = mPrefs.getBoolean(Constants.SHUFFLE, true)
        set(shuffle) = mPrefs.edit().putBoolean(Constants.SHUFFLE, shuffle).apply()

    var isNumericProgressEnabled: Boolean
        get() = mPrefs.getBoolean(Constants.NUMERIC_PROGRESS, false)
        set(enabled) = mPrefs.edit().putBoolean(Constants.NUMERIC_PROGRESS, enabled).apply()

    var sorting: Int
        get() = mPrefs.getInt(Constants.SORTING, SORT_BY_TITLE)
        set(sorting) = mPrefs.edit().putInt(Constants.SORTING, sorting).apply()

    var equalizer: Int
        get() = mPrefs.getInt(Constants.EQUALIZER, 0)
        set(equalizer) = mPrefs.edit().putInt(Constants.EQUALIZER, equalizer).apply()

    var repeatSong: Boolean
        get() = mPrefs.getBoolean(Constants.REPEAT_SONG, false)
        set(repeat) = mPrefs.edit().putBoolean(Constants.REPEAT_SONG, repeat).apply()
}
