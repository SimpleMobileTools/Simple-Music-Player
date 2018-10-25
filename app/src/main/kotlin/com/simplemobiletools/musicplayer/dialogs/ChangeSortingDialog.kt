package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.view.View
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import kotlinx.android.synthetic.main.dialog_change_sorting.view.*

class ChangeSortingDialog(val activity: Activity, val callback: () -> Unit) {
    private var currSorting = 0
    var config = activity.config
    var view: View = activity.layoutInflater.inflate(R.layout.dialog_change_sorting, null)

    init {
        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
                .setNegativeButton(R.string.cancel, null)
                .create().apply {
                    activity.setupDialogStuff(view, this, R.string.sort_by)
                }

        currSorting = config.sorting
        setupSortRadio()
        setupOrderRadio()
    }

    private fun setupSortRadio() {
        val sortingRadio = view.sorting_dialog_radio_sorting

        val sortBtn = when {
            currSorting and SORT_BY_TITLE != 0 -> sortingRadio.sorting_dialog_radio_title
            currSorting and SORT_BY_ARTIST != 0 -> sortingRadio.sorting_dialog_radio_artist
            currSorting and SORT_BY_PATH != 0 -> sortingRadio.sorting_dialog_radio_path
            else -> sortingRadio.sorting_dialog_radio_duration
        }
        sortBtn.isChecked = true
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
        var sorting = when (sortingRadio.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_title -> SORT_BY_TITLE
            R.id.sorting_dialog_radio_artist -> SORT_BY_ARTIST
            R.id.sorting_dialog_radio_path -> SORT_BY_PATH
            else -> SORT_BY_DURATION
        }

        if (view.sorting_dialog_radio_order.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        config.sorting = sorting
        callback()
    }
}
