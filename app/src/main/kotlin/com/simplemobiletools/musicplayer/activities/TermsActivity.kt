package com.simplemobiletools.musicplayer.activities

import android.os.Bundle
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.musicplayer.databinding.ActivityTermsBinding

class TermsActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityTermsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(com.simplemobiletools.musicplayer.R.string.terms_title)

        val web = binding.termsIconWeb
        web.settings.allowFileAccess = true
        web.setBackgroundColor(0x00000000)
        web.loadUrl("file:///android_asset/terms_icon.svg")
    }
}
