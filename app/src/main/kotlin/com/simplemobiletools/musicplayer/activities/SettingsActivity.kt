package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_ALWAYS
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_IF_UNAVAILABLE
import com.simplemobiletools.musicplayer.helpers.SHOW_FILENAME_NEVER
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*

class SettingsActivity : SimpleActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
    }

    override fun onResume() {
        super.onResume()

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupManagePlaylists()
        setupUseEnglish()
        setupShowAlbumCover()
        setupSwapPrevNext()
        setupEqualizer()
        setupReplaceTitle()
        updateTextColors(settings_scrollview)
        invalidateOptionsMenu()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        updateMenuItemColors(menu)
        return super.onCreateOptionsMenu(menu)
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beVisibleIf(!isThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_customize_colors_holder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupCustomizeWidgetColors() {
        settings_customize_widget_colors_holder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf(config.wasUseEnglishToggled || Locale.getDefault().language != "en")
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            System.exit(0)
        }
    }

    private fun setupManagePlaylists() {
        settings_manage_playlists_holder.setOnClickListener {
            startActivity(Intent(this, PlaylistsActivity::class.java))
        }
    }

    private fun setupShowAlbumCover() {
        settings_show_album_cover.isChecked = config.showAlbumCover
        settings_show_album_cover_holder.setOnClickListener {
            settings_show_album_cover.toggle()
            config.showAlbumCover = settings_show_album_cover.isChecked
        }
    }


    private fun setupSwapPrevNext() {
        settings_swap_prev_next.isChecked = config.swapPrevNext
        settings_swap_prev_next_holder.setOnClickListener {
            settings_swap_prev_next.toggle()
            config.swapPrevNext = settings_swap_prev_next.isChecked
        }
    }

    private fun setupEqualizer() {
        val equalizer = MusicService.mEqualizer ?: return
        val items = arrayListOf<RadioItem>()
        try {
            (0 until equalizer.numberOfPresets).mapTo(items) { RadioItem(it, equalizer.getPresetName(it.toShort())) }
        } catch (e: Exception) {
            settings_equalizer_holder.beGone()
            return
        }

        settings_equalizer.text = items[config.equalizer].title
        settings_equalizer_holder.setOnClickListener {
            RadioGroupDialog(this@SettingsActivity, items, config.equalizer) {
                config.equalizer = it as Int
                settings_equalizer.text = items[it].title
            }
        }
    }

    private fun setupReplaceTitle() {
        settings_show_filename.text = getShowFilenameText()
        settings_show_filename_holder.setOnClickListener {
            val items = arrayListOf(
                    RadioItem(SHOW_FILENAME_NEVER, getString(R.string.never)),
                    RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, getString(R.string.title_is_not_available)),
                    RadioItem(SHOW_FILENAME_ALWAYS, getString(R.string.always)))

            RadioGroupDialog(this@SettingsActivity, items, config.showFilename) {
                config.showFilename = it as Int
                settings_show_filename.text = getShowFilenameText()
                sendIntent(REFRESH_LIST)
            }
        }
    }

    private fun getShowFilenameText() = getString(when (config.showFilename) {
        SHOW_FILENAME_NEVER -> R.string.never
        SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
        else -> R.string.always
    })
}
