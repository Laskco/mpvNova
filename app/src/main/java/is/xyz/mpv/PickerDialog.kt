package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View

internal interface PickerDialog {
    fun buildView(layoutInflater: LayoutInflater): View

    fun isInteger(): Boolean // eh....

    var number: Double?
}
