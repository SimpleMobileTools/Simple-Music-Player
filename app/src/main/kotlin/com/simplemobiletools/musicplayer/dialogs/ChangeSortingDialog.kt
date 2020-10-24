package com.simplemobiletools.musicplayer.dialogs

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.RadioGroup
import androidx.appcompat.app.AlertDialog
import com.simplemobiletools.commons.extensions.setupDialogStuff
import com.simplemobiletools.commons.helpers.SORT_DESCENDING
import com.simplemobiletools.commons.models.RadioItem
import com.simplemobiletools.commons.views.MyCompatRadioButton
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.config
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TITLE
import com.simplemobiletools.musicplayer.helpers.PLAYER_SORT_BY_TRACK_COUNT
import kotlinx.android.synthetic.main.dialog_change_sorting.view.*

class ChangeSortingDialog(val activity: Activity, val tabIndex: Int, val callback: () -> Unit) {
    private var currSorting = 0
    var view: View = activity.layoutInflater.inflate(R.layout.dialog_change_sorting, null)

    init {
        currSorting = activity.config.playlistSorting
        setupSortRadio()
        setupOrderRadio()

        AlertDialog.Builder(activity)
            .setPositiveButton(R.string.ok) { dialog, which -> dialogConfirmed() }
            .setNegativeButton(R.string.cancel, null)
            .create().apply {
                activity.setupDialogStuff(view, this, R.string.sort_by)
            }
    }

    private fun setupSortRadio() {
        val radioItems = arrayListOf(
            RadioItem(0, activity.getString(R.string.title), PLAYER_SORT_BY_TITLE),
            RadioItem(1, activity.getString(R.string.track_count), PLAYER_SORT_BY_TRACK_COUNT)
        )

        radioItems.forEach { radioItem ->
            activity.layoutInflater.inflate(R.layout.small_radio_button, null).apply {
                findViewById<MyCompatRadioButton>(R.id.small_radio_button).apply {
                    text = radioItem.title
                    isChecked = currSorting and (radioItem.value as Int) != 0
                    id = radioItem.value as Int
                }

                view.sorting_dialog_radio_sorting.addView(this, RadioGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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

        if (currSorting != sorting) {
            activity.config.playlistSorting = sorting
            callback()
        }
    }
}
