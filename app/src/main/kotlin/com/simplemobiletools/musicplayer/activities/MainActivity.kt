package com.simplemobiletools.musicplayer.activities

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.simplemobiletools.commons.dialogs.RadioGroupDialog
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.FAQItem
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.models.Release
import com.simplemobiletools.musicplayer.BuildConfig
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.adapters.ViewPagerAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.dialogs.SleepTimerCustomDialog
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.queueDAO
import com.simplemobiletools.musicplayer.extensions.sendIntent
import com.simplemobiletools.musicplayer.fragments.MyViewPagerFragment
import com.simplemobiletools.musicplayer.helpers.INIT_QUEUE
import com.simplemobiletools.musicplayer.helpers.REFRESH_LIST
import com.simplemobiletools.musicplayer.helpers.START_SLEEP_TIMER
import com.simplemobiletools.musicplayer.helpers.STOP_SLEEP_TIMER
import com.simplemobiletools.musicplayer.models.Events
import com.simplemobiletools.musicplayer.services.MusicService
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_albums.*
import kotlinx.android.synthetic.main.fragment_artists.*
import kotlinx.android.synthetic.main.fragment_playlists.*
import kotlinx.android.synthetic.main.fragment_tracks.*
import kotlinx.android.synthetic.main.view_current_track_bar.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {
    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null
    private var bus: EventBus? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)

        handlePermission(PERMISSION_WRITE_STORAGE) {
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
    }

    override fun onResume() {
        super.onResume()
        updateTextColors(main_holder)
        sleep_timer_holder.background = ColorDrawable(config.backgroundColor)
        sleep_timer_stop.applyColorFilter(config.textColor)
        updateCurrentTrackBar()
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        setupSearch(menu)

        menu.apply {
            findItem(R.id.sleep_timer).isVisible = false
            findItem(R.id.sort).isVisible = false
        }

        updateMenuItemColors(menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu) = true

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.sort -> showSortingDialog()
            R.id.sleep_timer -> showSleepTimer()
            R.id.settings -> launchSettings()
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String) = false

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        getCurrentFragment().onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                getCurrentFragment().onSearchOpened()
                isSearchOpen = true
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                getCurrentFragment().onSearchClosed()
                isSearchOpen = false
                return true
            }
        })
    }

    private fun initActivity() {
        bus = EventBus.getDefault()
        bus!!.register(this)
        initFragments()
        sleep_timer_stop.setOnClickListener { stopSleepTimer() }

        current_track_bar.setOnClickListener {
            Intent(this, TrackActivity::class.java).apply {
                startActivity(this)
            }
        }

        if (MusicService.mCurrTrack == null) {
            ensureBackgroundThread {
                if (queueDAO.getAll().isNotEmpty()) {
                    sendIntent(INIT_QUEUE)
                }
            }
        }
    }

    private fun initFragments() {
        viewpager.adapter = ViewPagerAdapter(this)
        viewpager.offscreenPageLimit = 3
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                if (isSearchOpen) {
                    searchMenuItem?.collapseActionView()
                }
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
            }
        })

        main_tabs_holder.onTabSelectionChanged(
            tabSelectedAction = {
                viewpager.currentItem = it.position
            }
        )

        val tabLabels = arrayOf(getString(R.string.playlists), getString(R.string.artists), getString(R.string.albums), getString(R.string.tracks))
        main_tabs_holder.apply {
            setTabTextColors(config.textColor, getAdjustedPrimaryColor())
            setSelectedTabIndicatorColor(getAdjustedPrimaryColor())
            removeAllTabs()

            for (i in tabLabels.indices) {
                val tab = newTab().setText(tabLabels[i])
                addTab(tab, i, i == 0)
            }
        }

        viewpager.currentItem = config.lastUsedViewPagerPage
    }

    private fun getCurrentFragment(): MyViewPagerFragment {
        return when (viewpager.currentItem) {
            0 -> playlists_fragment_holder
            1 -> artists_fragment_holder
            2 -> albums_fragment_holder
            else -> tracks_fragment_holder
        }
    }

    private fun showSortingDialog() {
        ChangeSortingDialog(this) {
            sendIntent(REFRESH_LIST)
        }
    }

    private fun updateCurrentTrackBar() {
        current_track_bar.updateColors()
        current_track_bar.updateCurrentTrack(MusicService.mCurrTrack)
        current_track_bar.updateTrackState(MusicService.getIsPlaying())
    }

    private fun showSleepTimer() {
        val minutes = getString(R.string.minutes_raw)
        val hour = resources.getQuantityString(R.plurals.hours, 1, 1)

        val items = arrayListOf(
            RadioItem(5 * 60, "5 $minutes"),
            RadioItem(10 * 60, "10 $minutes"),
            RadioItem(20 * 60, "20 $minutes"),
            RadioItem(30 * 60, "30 $minutes"),
            RadioItem(60 * 60, hour))

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
        sleep_timer_holder.beVisible()
        sendIntent(START_SLEEP_TIMER)
    }

    private fun stopSleepTimer() {
        sendIntent(STOP_SLEEP_TIMER)
        sleep_timer_holder.beGone()
    }

    private fun getAllFragments() = arrayListOf(artists_fragment_holder, playlists_fragment_holder)

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackChangedEvent(event: Events.TrackChanged) {
        current_track_bar.updateCurrentTrack(event.track)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackStateChanged(event: Events.TrackStateChanged) {
        current_track_bar.updateTrackState(event.isPlaying)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun noStoragePermission(event: Events.NoStoragePermission) {
        toast(R.string.no_storage_permissions)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun sleepTimerChanged(event: Events.SleepTimerChanged) {
        sleep_timer_holder.beVisible()
        sleep_timer_value.text = event.seconds.getFormattedDuration()

        if (event.seconds == 0) {
            finish()
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun playlistsUpdated(event: Events.PlaylistsUpdated) {
        playlists_fragment_holder?.setupFragment(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun trackDeleted(event: Events.TrackDeleted) {
        getAllFragments().forEach {
            it.setupFragment(this)
        }
    }

    private fun launchSettings() {
        startActivity(Intent(applicationContext, SettingsActivity::class.java))
    }

    private fun launchAbout() {
        val licenses = LICENSE_EVENT_BUS or LICENSE_PICASSO

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text),
            FAQItem(R.string.faq_1_title_commons, R.string.faq_1_text_commons),
            FAQItem(R.string.faq_4_title_commons, R.string.faq_4_text_commons),
            FAQItem(R.string.faq_2_title_commons, R.string.faq_2_text_commons),
            FAQItem(R.string.faq_6_title_commons, R.string.faq_6_text_commons))

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
