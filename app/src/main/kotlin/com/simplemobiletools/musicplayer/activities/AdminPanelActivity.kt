package com.simplemobiletools.musicplayer.activities

import android.os.Build
import android.os.Bundle
import android.util.Base64
import com.simplemobiletools.commons.extensions.viewBinding
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.databinding.ActivityAdminPanelBinding
import com.simplemobiletools.musicplayer.helpers.FlagCrypto
import javax.crypto.AEADBadTagException

class AdminPanelActivity : SimpleActivity() {

    private val flag_abdou_2 = "C867Wzjm6G3C0SJHUqQfRku7hOt2u5EpzEF5rkQGULR2JWtIUhs0/+EnCnqRFc+qUW7sgs6EiNm2p5Q="

    private val binding by viewBinding(ActivityAdminPanelBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        title = getString(R.string.admin_panel_title)

        val key = FlagCrypto.deriveKey(Build.MANUFACTURER, Build.MODEL)
        val encrypted = Base64.decode(flag_abdou_2, Base64.DEFAULT)
        binding.adminPanelFlag.text = try {
            String(FlagCrypto.decrypt(encrypted, key))
        } catch (e: AEADBadTagException) {
            getString(R.string.admin_panel_error)
        } catch (e: Exception) {
            getString(R.string.admin_panel_error)
        }
    }
}
