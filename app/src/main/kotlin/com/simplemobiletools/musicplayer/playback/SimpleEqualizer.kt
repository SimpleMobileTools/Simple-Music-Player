package com.simplemobiletools.musicplayer.playback

import android.content.Context
import android.media.audiofx.Equalizer
import androidx.media3.common.util.UnstableApi
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.simplemobiletools.commons.extensions.toast
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.EQUALIZER_PRESET_CUSTOM
import com.simplemobiletools.musicplayer.playback.player.SimpleMusicPlayer

@UnstableApi
object SimpleEqualizer {
    lateinit var instance: Equalizer
        internal set

    fun setupEqualizer(context: Context, player: SimpleMusicPlayer) {
        try {
            val preset = context.config.equalizerPreset
            instance = Equalizer(0, player.getAudioSessionId())
            if (!instance.enabled) {
                instance.enabled = true
            }

            if (preset != EQUALIZER_PRESET_CUSTOM) {
                instance.usePreset(preset.toShort())
            } else {
                val minValue = instance.bandLevelRange[0]
                val bandType = object : TypeToken<HashMap<Short, Int>>() {}.type
                val equalizerBands = Gson().fromJson<HashMap<Short, Int>>(context.config.equalizerBands, bandType) ?: HashMap()

                for ((key, value) in equalizerBands) {
                    val newValue = value + minValue
                    if (instance.getBandLevel(key) != newValue.toShort()) {
                        instance.setBandLevel(key, newValue.toShort())
                    }
                }
            }
        } catch (ignored: Exception) {
            context.toast(com.simplemobiletools.commons.R.string.unknown_error_occurred)
        }
    }

    fun release() {
        if (::instance.isInitialized) {
            instance.release()
        }
    }
}
