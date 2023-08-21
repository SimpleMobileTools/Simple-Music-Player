package com.simplemobiletools.musicplayer.activities

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.Player
import androidx.viewpager.widget.ViewPager
import com.simplemobiletools.commons.dialogs.FilePickerDialog
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.ViewPagerAdapter
import com.simplemobiletools.musicplayer.dialogs.NewPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.SelectPlaylistDialog
import com.simplemobiletools.musicplayer.dialogs.SleepTimerCustomDialog
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.fragments.MyViewPagerFragment
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.helpers.M3uImporter.ImportResult
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.playback.CustomCommands
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_albums.albums_fragment_holder
import kotlinx.android.synthetic.main.fragment_artists.artists_fragment_holder
import kotlinx.android.synthetic.main.fragment_folders.folders_fragment_holder
import kotlinx.android.synthetic.main.fragment_genres.genres_fragment_holder
import kotlinx.android.synthetic.main.fragment_playlists.playlists_fragment_holder
import kotlinx.android.synthetic.main.fragment_tracks.tracks_fragment_holder
import kotlinx.android.synthetic.main.view_current_track_bar.current_track_bar
import me.grantland.widget.AutofitHelper
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.io.FileOutputStream

class MainActivity : SimpleMusicActivity(), Player.Listener {
    private val PICK_IMPORT_SOURCE_INTENT = 1

    private var bus: EventBus? = null
    private var storedShowTabs = 0
    private var storedExcludedFolders = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        isMaterialActivity = true
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupOptionsMenu()
        refreshMenuItems()
        updateMaterialActivityViews(main_coordinator, main_holder, useTransparentNavigation = false, useTopSearchMenu = true)
        storeStateVariables()
        setupTabs()

        handlePermission(getPermissionToRequest()) {
            if (it) {
                initActivity()
            } else {
                toast(R.string.no_storage_permissions)
                finish()
            }
        }

        volumeControlStream = AudioManager.STREAM_MUSIC
        checkWhatsNewDialog()
        checkAppOnSDCard()
        withPlayer {
            maybePreparePlayer(context = this@MainActivity) { success ->
                if (success) {
                    broadcastUpdateWidgetState()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (storedShowTabs != config.showTabs) {
            config.lastUsedViewPagerPage = 0
            System.exit(0)
            return
        }

        updateMenuColors()
        updateTextColors(main_holder)
        setupTabColors()
        val properTextColor = getProperTextColor()
        val properPrimaryColor = getProperPrimaryColor()
        sleep_timer_holder.background = ColorDrawable(getProperBackgroundColor())
        sleep_timer_stop.applyColorFilter(properTextColor)
        loading_progress_bar.setIndicatorColor(properPrimaryColor)
        loading_progress_bar.trackColor = properPrimaryColor.adjustAlpha(LOWER_ALPHA)

        getAllFragments().forEach {
            it?.setupColors(properTextColor, properPrimaryColor)
        }

        if (storedExcludedFolders != config.excludedFolders.hashCode()) {
            refreshAllFragments()
        }
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
        config.lastUsedViewPagerPage = view_pager.currentItem
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onBackPressed() {
        if (main_menu.isSearchOpen) {
            main_menu.closeSearch()
        } else {
            super.onBackPressed()
        }
    }

    private fun refreshMenuItems() {
        main_menu.getToolbar().menu.apply {
            findItem(R.id.create_new_playlist).isVisible = getCurrentFragment() == playlists_fragment_holder
            findItem(R.id.create_playlist_from_folder).isVisible = getCurrentFragment() == playlists_fragment_holder
            findItem(R.id.import_playlist).isVisible = getCurrentFragment() == playlists_fragment_holder && isOreoPlus()
            findItem(R.id.more_apps_from_us).isVisible = !resources.getBoolean(R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        main_menu.getToolbar().inflateMenu(R.menu.menu_main)
        main_menu.toggleHideOnScroll(false)
        main_menu.setupMenu()

        main_menu.onSearchClosedListener = {
            getAllFragments().forEach {
                it?.onSearchClosed()
            }
        }

        main_menu.onSearchTextChangedListener = { text ->
            getCurrentFragment()?.onSearchQueryChanged(text)
        }

        main_menu.getToolbar().setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.sort -> showSortingDialog()
                R.id.rescan_media -> refreshAllFragments(showProgress = true)
                R.id.sleep_timer -> showSleepTimer()
                R.id.create_new_playlist -> createNewPlaylist()
                R.id.create_playlist_from_folder -> createPlaylistFromFolder()
                R.id.import_playlist -> tryImportPlaylist()
                R.id.equalizer -> launchEqualizer()
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.settings -> launchSettings()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun updateMenuColors() {
        updateStatusbarColor(getProperBackgroundColor())
        main_menu.updateColors()
    }

    private fun storeStateVariables() {
        config.apply {
            storedShowTabs = showTabs
            storedExcludedFolders = config.excludedFolders.hashCode()
        }
    }

    private fun initActivity() {
        bus = EventBus.getDefault()
        bus!!.register(this)
        // trigger a scan first so that the fragments will accurately reflect the scanning state
        mediaScanner.scan()
        initFragments()
        sleep_timer_stop.setOnClickListener { stopSleepTimer() }

        setupCurrentTrackBar(current_track_bar)
        refreshAllFragments()
    }

    private fun refreshAllFragments(showProgress: Boolean = config.appRunCount == 1) {
        if (showProgress) {
            loading_progress_bar.show()
        }

        handleNotificationPermission { granted ->
            mediaScanner.scan(progress = showProgress && granted) { complete ->
                runOnUiThread {
                    getAllFragments().forEach {
                        it?.setupFragment(this)
                    }

                    if (complete) {
                        loading_progress_bar.hide()
                    }

                    withPlayer {
                        sendCommand(CustomCommands.RELOAD_CONTENT)
                    }
                }
            }
        }
    }

    private fun initFragments() {
        view_pager.adapter = ViewPagerAdapter(this)
        view_pager.offscreenPageLimit = tabsList.size - 1
        view_pager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {}

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                refreshMenuItems()
            }
        })
        view_pager.currentItem = config.lastUsedViewPagerPage
    }

    private fun setupTabs() {
        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            if (config.showTabs and value != 0) {
                main_tabs_holder.newTab().setCustomView(R.layout.bottom_tablayout_item).apply {
                    customView?.findViewById<ImageView>(R.id.tab_item_icon)?.setImageDrawable(getTabIcon(value))
                    customView?.findViewById<TextView>(R.id.tab_item_label)?.text = getTabLabel(value)
                    AutofitHelper.create(customView?.findViewById(R.id.tab_item_label))
                    main_tabs_holder.addTab(this)
                }
            }
        }

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                updateBottomTabItemColors(it.customView, false)
            },
            tabSelectedAction = {
                main_menu.closeSearch()
                view_pager.currentItem = it.position
                updateBottomTabItemColors(it.customView, true)
            }
        )

        main_tabs_holder.beGoneIf(main_tabs_holder.tabCount == 1)
    }

    private fun setupTabColors() {
        val activeView = main_tabs_holder.getTabAt(view_pager.currentItem)?.customView
        updateBottomTabItemColors(activeView, true)

        getInactiveTabIndexes(view_pager.currentItem).forEach { index ->
            val inactiveView = main_tabs_holder.getTabAt(index)?.customView
            updateBottomTabItemColors(inactiveView, false)
        }

        val bottomBarColor = getBottomNavigationBackgroundColor()
        main_tabs_holder.setBackgroundColor(bottomBarColor)
        updateNavigationBarColor(bottomBarColor)
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            TAB_PLAYLISTS -> R.drawable.ic_playlist_vector
            TAB_FOLDERS -> R.drawable.ic_folders_vector
            TAB_ARTISTS -> R.drawable.ic_person_vector
            TAB_ALBUMS -> R.drawable.ic_album_vector
            TAB_GENRES -> R.drawable.ic_genre_vector
            else -> R.drawable.ic_music_note_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, getProperTextColor())
    }

    private fun getTabLabel(position: Int): String {
        val stringId = when (position) {
            TAB_PLAYLISTS -> R.string.playlists
            TAB_FOLDERS -> R.string.folders
            TAB_ARTISTS -> R.string.artists
            TAB_ALBUMS -> R.string.albums
            TAB_GENRES -> R.string.genres
            else -> R.string.tracks
        }

        return resources.getString(stringId)
    }

    private fun getCurrentFragment(): MyViewPagerFragment? {
        val showTabs = config.showTabs
        val fragments = arrayListOf<MyViewPagerFragment>()
        if (showTabs and TAB_PLAYLISTS != 0) {
            fragments.add(playlists_fragment_holder)
        }

        if (showTabs and TAB_FOLDERS != 0) {
            fragments.add(folders_fragment_holder)
        }

        if (showTabs and TAB_ARTISTS != 0) {
            fragments.add(artists_fragment_holder)
        }

        if (showTabs and TAB_ALBUMS != 0) {
            fragments.add(albums_fragment_holder)
        }

        if (showTabs and TAB_TRACKS != 0) {
            fragments.add(tracks_fragment_holder)
        }

        if (showTabs and TAB_GENRES != 0) {
            fragments.add(genres_fragment_holder)
        }

        return fragments.getOrNull(view_pager.currentItem)
    }

    private fun showSortingDialog() {
        getCurrentFragment()?.onSortOpen(this)
    }

    private fun createNewPlaylist() {
        NewPlaylistDialog(this) {
            EventBus.getDefault().post(Events.PlaylistsUpdated())
        }
    }

    private fun createPlaylistFromFolder() {
        FilePickerDialog(this, pickFile = false, enforceStorageRestrictions = false) {
            createPlaylistFrom(it)
        }
    }

    private fun createPlaylistFrom(path: String) {
        ensureBackgroundThread {
            getFolderTracks(path, true) { tracks ->
                runOnUiThread {
                    NewPlaylistDialog(this) { playlistId ->
                        tracks.forEach {
                            it.playListId = playlistId
                        }

                        ensureBackgroundThread {
                            audioHelper.insertTracks(tracks)
                            EventBus.getDefault().post(Events.PlaylistsUpdated())
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_IMPORT_SOURCE_INTENT && resultCode == Activity.RESULT_OK && resultData != null && resultData.data != null) {
            tryImportPlaylistFromFile(resultData.data!!)
        }
    }

    private fun tryImportPlaylistFromFile(uri: Uri) {
        when {
            uri.scheme == "file" -> showImportPlaylistDialog(uri.path!!)
            uri.scheme == "content" -> {
                val tempFile = getTempFile("imports", uri.path!!.getFilenameFromPath())
                if (tempFile == null) {
                    toast(R.string.unknown_error_occurred)
                    return
                }

                try {
                    val inputStream = contentResolver.openInputStream(uri)
                    val out = FileOutputStream(tempFile)
                    inputStream!!.copyTo(out)

                    showImportPlaylistDialog(tempFile.absolutePath)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }

            else -> toast(R.string.invalid_file_format)
        }
    }

    private fun tryImportPlaylist() {
        if (isQPlus()) {
            hideKeyboard()
            Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = MIME_TYPE_M3U

                try {
                    startActivityForResult(this, PICK_IMPORT_SOURCE_INTENT)
                } catch (e: ActivityNotFoundException) {
                    toast(R.string.system_service_disabled, Toast.LENGTH_LONG)
                } catch (e: Exception) {
                    showErrorToast(e)
                }
            }
        } else {
            handlePermission(PERMISSION_READ_STORAGE) { granted ->
                if (granted) {
                    showFilePickerDialog()
                }
            }
        }
    }

    private fun showFilePickerDialog() {
        FilePickerDialog(this, enforceStorageRestrictions = false) { path ->
            SelectPlaylistDialog(this) { id ->
                importPlaylist(path, id)
            }
        }
    }

    private fun showImportPlaylistDialog(path: String) {
        SelectPlaylistDialog(this) { id ->
            importPlaylist(path, id)
        }
    }

    private fun importPlaylist(path: String, id: Int) {
        ensureBackgroundThread {
            M3uImporter(this) { result ->
                runOnUiThread {
                    toast(
                        when (result) {
                            ImportResult.IMPORT_OK -> R.string.importing_successful
                            ImportResult.IMPORT_PARTIAL -> R.string.importing_some_entries_failed
                            else -> R.string.importing_failed
                        }
                    )

                    playlists_fragment_holder.setupFragment(this)
                }
            }.importPlaylist(path, id)
        }
    }

    private fun showSleepTimer() {
        val minutes = getString(R.string.minutes_raw)
        val hour = resources.getQuantityString(R.plurals.hours, 1, 1)

        val items = arrayListOf(
            RadioItem(5 * 60, "5 $minutes"),
            RadioItem(10 * 60, "10 $minutes"),
            RadioItem(20 * 60, "20 $minutes"),
            RadioItem(30 * 60, "30 $minutes"),
            RadioItem(60 * 60, hour)
        )

        if (items.none { it.id == config.lastSleepTimerSeconds }) {
            val lastSleepTimerMinutes = config.lastSleepTimerSeconds / 60
            val text = resources.getQuantityString(R.plurals.minutes, lastSleepTimerMinutes, lastSleepTimerMinutes)
            items.add(RadioItem(config.lastSleepTimerSeconds, text))
        }

        items.sortBy { it.id }
        items.add(RadioItem(-1, getString(R.string.custom)))

        RadioGroupDialog(this, items, config.lastSleepTimerSeconds) {
            if (it as Int == -1) {
                SleepTimerCustomDialog(this) {
                    if (it > 0) {
                        pickedSleepTimer(it)
                    }
                }
            } else if (it > 0) {
                pickedSleepTimer(it)
            }
        }
    }

    private fun pickedSleepTimer(seconds: Int) {
        config.lastSleepTimerSeconds = seconds
        config.sleepInTS = System.currentTimeMillis() + seconds * 1000
        startSleepTimer()
    }

    private fun startSleepTimer() {
        sleep_timer_holder.fadeIn()
        withPlayer {
            sendCommand(CustomCommands.TOGGLE_SLEEP_TIMER)
        }
    }

    private fun stopSleepTimer() {
        sleep_timer_holder.fadeOut()
        withPlayer {
            sendCommand(CustomCommands.TOGGLE_SLEEP_TIMER)
        }
    }

    private fun getAllFragments() = arrayListOf(
        playlists_fragment_holder,
        folders_fragment_holder,
        artists_fragment_holder,
        albums_fragment_holder,
        tracks_fragment_holder,
        genres_fragment_holder
    )

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun sleepTimerChanged(event: Events.SleepTimerChanged) {
        sleep_timer_value.text = event.seconds.getFormattedDuration()
        sleep_timer_holder.beVisible()

        if (event.seconds == 0) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playlistsUpdated(event: Events.PlaylistsUpdated) {
        playlists_fragment_holder?.setupFragment(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun tracksUpdated(event: Events.RefreshTracks) {
        tracks_fragment_holder?.setupFragment(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun shouldRefreshFragments(event: Events.RefreshFragments) {
        refreshAllFragments()
    }

    private fun launchEqualizer() {
        hideKeyboard()
        startActivity(Intent(applicationContext, EqualizerActivity::class.java))
    }

    private fun launchSettings() {
        hideKeyboard()
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_GLIDE or LICENSE_M3U_PARSER or LICENSE_AUTOFITTEXTVIEW

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_1_title_commons, R.string.faq_1_text_commons),
            FAQItem(R.string.faq_4_title_commons, R.string.faq_4_text_commons),
            FAQItem(R.string.faq_9_title_commons, R.string.faq_9_text_commons)
        )

        if (!resources.getBoolean(R.bool.hide_google_relations)) {
            faqItems.add(FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons))
            faqItems.add(FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))
        }

        startAboutActivity(R.string.app_name, licenses, BuildConfig.VERSION_NAME, faqItems, true)
    }

    private fun checkWhatsNewDialog() {
        arrayListOf<Release>().apply {
            add(Release(25, R.string.release_25))
            add(Release(27, R.string.release_27))
            add(Release(28, R.string.release_28))
            add(Release(37, R.string.release_37))
            add(Release(59, R.string.release_59))
            checkWhatsNew(this, BuildConfig.VERSION_CODE)
        }
    }
}
