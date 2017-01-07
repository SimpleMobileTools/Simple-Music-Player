package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import android.view.LayoutInflater
import android.view.View
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.helpers.*
import kotlinx.android.synthetic.main.dialog_change_sorting.view.*

class ChangeSortingDialog(val activity: Activity, val callback: () -> Unit) : DialogInterface.OnClickListener {
    private var currSorting = 0
    var config: Config = Config.newInstance(activity)
    var view: View = LayoutInflater.from(activity).inflate(R.layout.dialog_change_sorting, null)

    init {
        AlertDialog.Builder(activity)
                .setPositiveButton(R.string.ok, this)
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
        var sortBtn = sortingRadio.sorting_dialog_radio_duration

        if (currSorting and SORT_BY_TITLE != 0) {
            sortBtn = sortingRadio.sorting_dialog_radio_title
        } else if (currSorting and SORT_BY_ARTIST != 0) {
            sortBtn = sortingRadio.sorting_dialog_radio_artist
        } else if (currSorting and SORT_BY_FILE_NAME != 0) {
            sortBtn = sortingRadio.sorting_dialog_radio_file_name
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

    override fun onClick(dialog: DialogInterface, which: Int) {
        val sortingRadio = view.sorting_dialog_radio_sorting
        var sorting = when (sortingRadio.checkedRadioButtonId) {
            R.id.sorting_dialog_radio_title -> SORT_BY_TITLE
            R.id.sorting_dialog_radio_artist -> SORT_BY_ARTIST
            R.id.sorting_dialog_radio_file_name -> SORT_BY_FILE_NAME
            else -> SORT_BY_DURATION
        }

        if (view.sorting_dialog_radio_order.checkedRadioButtonId == R.id.sorting_dialog_radio_descending) {
            sorting = sorting or SORT_DESCENDING
        }

        config.sorting = sorting
        callback.invoke()
    }
}
