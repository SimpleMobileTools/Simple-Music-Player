package com.simplemobiletools.musicplayer.fragments

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import com.google.gson.Gson
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.areSystemAnimationsEnabled
import com.simplemobiletools.commons.extensions.beGoneIf
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.hideKeyboard
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.activities.SimpleActivity
import com.simplemobiletools.musicplayer.activities.TracksActivity
import com.simplemobiletools.musicplayer.adapters.GenresAdapter
import com.simplemobiletools.musicplayer.dialogs.ChangeSortingDialog
import com.simplemobiletools.musicplayer.extensions.audioHelper
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.extensions.mediaScanner
import com.simplemobiletools.musicplayer.helpers.GENRE
import com.simplemobiletools.musicplayer.helpers.TAB_GENRES
import com.simplemobiletools.musicplayer.models.Genre
import kotlinx.android.synthetic.main.fragment_genres.view.genres_fastscroller
import kotlinx.android.synthetic.main.fragment_genres.view.genres_list
import kotlinx.android.synthetic.main.fragment_genres.view.genres_placeholder

class GenresFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet) {
    private var genres = ArrayList<Genre>()

    override fun setupFragment(activity: BaseSimpleActivity) {
        Genre.sorting = context.config.genreSorting
        ensureBackgroundThread {
            val cachedGenres = activity.audioHelper.getAllGenres()
            activity.runOnUiThread {
                gotGenres(activity, cachedGenres)
            }
        }
    }

    private fun gotGenres(activity: BaseSimpleActivity, cachedGenres: ArrayList<Genre>) {
        genres = cachedGenres
        activity.runOnUiThread {
            val scanning = activity.mediaScanner.isScanning()
            genres_placeholder.text = if (scanning) {
                context.getString(R.string.loading_files)
            } else {
                context.getString(R.string.no_items_found)
            }
            genres_placeholder.beVisibleIf(genres.isEmpty())

            val adapter = genres_list.adapter
            if (adapter == null) {
                GenresAdapter(activity, genres, genres_list) {
                    activity.hideKeyboard()
                    Intent(activity, TracksActivity::class.java).apply {
                        putExtra(GENRE, Gson().toJson(it as Genre))
                        activity.startActivity(this)
                    }
                }.apply {
                    genres_list.adapter = this
                }

                if (context.areSystemAnimationsEnabled) {
                    genres_list.scheduleLayoutAnimation()
                }
            } else {
                val oldItems = (adapter as GenresAdapter).genres
                if (oldItems.sortedBy { it.id }.hashCode() != genres.sortedBy { it.id }.hashCode()) {
                    adapter.updateItems(genres)
                }
            }
        }
    }

    override fun finishActMode() {
        (genres_list.adapter as? MyRecyclerViewAdapter)?.finishActMode()
    }

    override fun onSearchQueryChanged(text: String) {
        val filtered = genres.filter { it.title.contains(text, true) }.toMutableList() as ArrayList<Genre>
        (genres_list.adapter as? GenresAdapter)?.updateItems(filtered, text)
        genres_placeholder.beVisibleIf(filtered.isEmpty())
    }

    override fun onSearchClosed() {
        (genres_list.adapter as? GenresAdapter)?.updateItems(genres)
        genres_placeholder.beGoneIf(genres.isNotEmpty())
    }

    override fun onSortOpen(activity: SimpleActivity) {
        ChangeSortingDialog(activity, TAB_GENRES) {
            val adapter = genres_list.adapter as? GenresAdapter ?: return@ChangeSortingDialog
            Genre.sorting = activity.config.genreSorting
            genres.sort()
            adapter.updateItems(genres, forceUpdate = true)
        }
    }

    override fun setupColors(textColor: Int, adjustedPrimaryColor: Int) {
        genres_placeholder.setTextColor(textColor)
        genres_fastscroller.updateColors(adjustedPrimaryColor)
    }
}
