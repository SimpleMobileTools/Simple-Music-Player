package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.musicplayer.helpers.Config

open class SimpleActivity : BaseSimpleActivity() {
    lateinit var config: Config

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        config = Config.newInstance(applicationContext)
    }
}
