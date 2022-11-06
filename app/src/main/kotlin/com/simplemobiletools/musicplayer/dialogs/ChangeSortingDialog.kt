package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import com.simplemobiletools.commons.extensions.beVisibleIf
import com.simplemobiletools.commons.extensions.getAlertDialogBuilder
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.views.MyCompatRadioButton
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.*
import com.simplemobiletools.musicplayer.models.Playlist
import kotlinx.android.synthetic.main.dialog_change_sorting.view.*

class ChangeSortingDialog(val activity: Activity, val location: Int, val playlist: Playlist? = null, val path: String? = null, val callback: () -> Unit) {
    private val config = activity.config
    private var currSorting = 0
    private val view: View

    init {
        view = activity.layoutInflater.inflate(R.layout.dialog_change_sorting, null).apply {
            use_for_this_playlist_divider.beVisibleIf(playlist != null || path != null)
            sorting_dialog_use_for_this_only.beVisibleIf(playlist != null || path != null)

            if (playlist != null) {
                sorting_dialog_use_for_this_only.isChecked = config.hasCustomPlaylistSorting(playlist.id)
            } else if (path != null) {
                sorting_dialog_use_for_this_only.text = activity.getString(R.string.use_for_this_folder)
                sorting_dialog_use_for_this_only.isChecked = config.hasCustomSorting(path)
            }
        }

        currSorting = when (location) {
            TAB_PLAYLISTS -> config.playlistSorting
            TAB_FOLDERS -> config.folderSorting
            TAB_ARTISTS -> config.artistSorting
            TAB_ALBUMS -> config.albumSorting
            TAB_TRACKS -> config.trackSorting
            else -> if (playlist != null) {
                config.getProperPlaylistSorting(playlist.id)
            } else if (path != null) {
                config.getProperFolderSorting(path)
            } else {
                config.playlistTracksSorting
            }
        }

        setupSortRadio()
        setupOrderRadio()

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(view, this, R.string.sort_by)
            }
    }

    private fun setupSortRadio() {
        val radioItems = ArrayList<RadioItem>()
        when (location) {
            TAB_PLAYLISTS, TAB_FOLDERS -> {
                radioItems.add(RadioItem(0, activity.getString(R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.track_count), PLAYER_SORT_BY_TRACK_COUNT))
            }
            TAB_ARTISTS -> {
                radioItems.add(RadioItem(0, activity.getString(R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.album_count), PLAYER_SORT_BY_ALBUM_COUNT))
                radioItems.add(RadioItem(2, activity.getString(R.string.track_count), PLAYER_SORT_BY_TRACK_COUNT))
            }
            TAB_ALBUMS -> {
                radioItems.add(RadioItem(0, activity.getString(R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.artist_name), PLAYER_SORT_BY_ARTIST_TITLE))
                radioItems.add(RadioItem(2, activity.getString(R.string.year), PLAYER_SORT_BY_YEAR))
            }
            TAB_TRACKS -> {
                radioItems.add(RadioItem(0, activity.getString(R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.artist), PLAYER_SORT_BY_ARTIST_TITLE))
                radioItems.add(RadioItem(2, activity.getString(R.string.duration), PLAYER_SORT_BY_DURATION))
                radioItems.add(RadioItem(3, activity.getString(R.string.track_number), PLAYER_SORT_BY_TRACK_ID))
            }
            ACTIVITY_PLAYLIST_FOLDER -> {
                radioItems.add(RadioItem(0, activity.getString(R.string.title), PLAYER_SORT_BY_TITLE))
                radioItems.add(RadioItem(1, activity.getString(R.string.artist), PLAYER_SORT_BY_ARTIST_TITLE))
                radioItems.add(RadioItem(2, activity.getString(R.string.duration), PLAYER_SORT_BY_DURATION))
                radioItems.add(RadioItem(3, activity.getString(R.string.track_number), PLAYER_SORT_BY_TRACK_ID))

                if (playlist != null) {
                    radioItems.add(RadioItem(4, activity.getString(R.string.custom), PLAYER_SORT_BY_CUSTOM))
                }
            }
        }

        view.sorting_dialog_radio_sorting.setOnCheckedChangeListener { group, checkedId ->
            view.sorting_order_divider.beVisibleIf(checkedId != PLAYER_SORT_BY_CUSTOM)
            view.sorting_dialog_radio_order.beVisibleIf(checkedId != PLAYER_SORT_BY_CUSTOM)
        }

        radioItems.forEach { radioItem ->
            activity.layoutInflater.inflate(R.layout.small_radio_button, null).apply {
                findViewById<MyCompatRadioButton>(R.id.small_radio_button).apply {
                    text = radioItem.title
                    isChecked = currSorting and (radioItem.value as Int) != 0
                    id = radioItem.value as Int
                }

                view.sorting_dialog_radio_sorting.addView(
                    this,
                    RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                )
            }
        }
    }

    private fun setupOrderRadio() {
        val orderRadio = view.sorting_dialog_radio_order
        var orderBtn = orderRadio.sorting_dialog_radio_ascending

        if (currSorting and SORT_DESCENDING != 0) {
            orderBtn = orderRadio.sorting_dialog_radio_descending
        }

        orderBtn.isChecked = true
    }

    private fun dialogConfirmed() {
        val sortingRadio = view.sorting_dialog_radio_sorting
        var sorting = sortingRadio.checkedRadioButtonId

        if (view.sorting_dialog_radio_order.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        if (currSorting != sorting || location == ACTIVITY_PLAYLIST_FOLDER) {
            when (location) {
                TAB_PLAYLISTS -> config.playlistSorting = sorting
                TAB_FOLDERS -> config.folderSorting = sorting
                TAB_ARTISTS -> config.artistSorting = sorting
                TAB_ALBUMS -> config.albumSorting = sorting
                TAB_TRACKS -> config.trackSorting = sorting
                ACTIVITY_PLAYLIST_FOLDER -> {
                    if (view.sorting_dialog_use_for_this_only.isChecked) {
                        if (playlist != null) {
                            config.saveCustomPlaylistSorting(playlist.id, sorting)
                        } else if (path != null) {
                            config.saveCustomSorting(path, sorting)
                        }
                    } else {
                        if (playlist != null) {
                            config.removeCustomPlaylistSorting(playlist.id)
                        } else if (path != null) {
                            config.removeCustomSorting(path)
                        }
                        config.playlistTracksSorting = sorting
                    }
                }
            }

            callback()
        }
    }
}
