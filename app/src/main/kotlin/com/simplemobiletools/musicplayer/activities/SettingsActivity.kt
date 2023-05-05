package com.simplemobiletools.musicplayer.activities

import android.content.Intent
import android.os.Bundle
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.IS_CUSTOMIZING_COLORS
import com.simplemobiletools.commons.helpers.NavigationIcon
import com.simplemobiletools.commons.helpers.isTiramisuPlus
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.dialogs.ManageVisibleTabsDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.helpers.*
import kotlinx.android.synthetic.main.activity_settings.*
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        updateMaterialActivityViews(settings_coordinator, settings_holder, useTransparentNavigation = true, useTopSearchMenu = false)
        setupMaterialScrollListener(settings_nested_scrollview, settings_toolbar)
    }

    override fun onResume() {
        super.onResume()
        setupToolbar(settings_toolbar, NavigationIcon.Arrow)

        setupPurchaseThankYou()
        setupCustomizeColors()
        setupCustomizeWidgetColors()
        setupUseEnglish()
        setupLanguage()
        setupManageExcludedFolders()
        setupManageShownTabs()
        setupSwapPrevNext()
        setupReplaceTitle()
        setupGaplessPlayback()
        updateTextColors(settings_nested_scrollview)

        arrayOf(settings_color_customization_section_label, settings_general_settings_label).forEach {
            it.setTextColor(getProperPrimaryColor())
        }
    }

    private fun setupPurchaseThankYou() {
        settings_purchase_thank_you_holder.beGoneIf(isOrWasThankYouInstalled())
        settings_purchase_thank_you_holder.setOnClickListener {
            launchPurchaseThankYouIntent()
        }
    }

    private fun setupCustomizeColors() {
        settings_color_customization_label.text = getCustomizeColorsString()
        settings_color_customization_holder.setOnClickListener {
            handleCustomizeColorsClick()
        }
    }

    private fun setupCustomizeWidgetColors() {
        settings_widget_color_customization_holder.setOnClickListener {
            Intent(this, WidgetConfigureActivity::class.java).apply {
                putExtra(IS_CUSTOMIZING_COLORS, true)
                startActivity(this)
            }
        }
    }

    private fun setupUseEnglish() {
        settings_use_english_holder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settings_use_english.isChecked = config.useEnglish
        settings_use_english_holder.setOnClickListener {
            settings_use_english.toggle()
            config.useEnglish = settings_use_english.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() {
        settings_language.text = Locale.getDefault().displayLanguage
        settings_language_holder.beVisibleIf(isTiramisuPlus())
        settings_language_holder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun setupSwapPrevNext() {
        settings_swap_prev_next.isChecked = config.swapPrevNext
        settings_swap_prev_next_holder.setOnClickListener {
            settings_swap_prev_next.toggle()
            config.swapPrevNext = settings_swap_prev_next.isChecked
        }
    }

    private fun setupReplaceTitle() {
        settings_show_filename.text = getReplaceTitleText()
        settings_show_filename_holder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(SHOW_FILENAME_NEVER, getString(R.string.never)),
                RadioItem(SHOW_FILENAME_IF_UNAVAILABLE, getString(R.string.title_is_not_available)),
                RadioItem(SHOW_FILENAME_ALWAYS, getString(R.string.always))
            )

            RadioGroupDialog(this@SettingsActivity, items, config.showFilename) {
                config.showFilename = it as Int
                settings_show_filename.text = getReplaceTitleText()
                sendIntent(REFRESH_LIST)
            }
        }
    }

    private fun getReplaceTitleText() = getString(
        when (config.showFilename) {
            SHOW_FILENAME_NEVER -> R.string.never
            SHOW_FILENAME_IF_UNAVAILABLE -> R.string.title_is_not_available
            else -> R.string.always
        }
    )

    private fun setupManageShownTabs() {
        settings_manage_shown_tabs_holder.setOnClickListener {
            ManageVisibleTabsDialog(this)
        }
    }

    private fun setupManageExcludedFolders() {
        settings_manage_excluded_folders_holder.setOnClickListener {
            startActivity(Intent(this, ExcludedFoldersActivity::class.java))
        }
    }

    private fun setupGaplessPlayback() {
        settings_gapless_playback.isChecked = config.gaplessPlayback
        settings_gapless_playback_holder.setOnClickListener {
            settings_gapless_playback.toggle()
            config.gaplessPlayback = settings_gapless_playback.isChecked
            sendIntent(UPDATE_GAPLESS_PLAYBACK)
        }
    }
}
