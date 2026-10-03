package com.imomali.chatapp

import android.content.res.ColorStateList
import android.graphics.Color
import com.google.android.material.button.MaterialButton

/** Secondary actions stay visible without competing with the screen's primary action. */
internal fun MaterialButton.secondary() = apply {
    backgroundTintList = ColorStateList.valueOf(Color.TRANSPARENT)
    setTextColor(ColorStateList(
        arrayOf(intArrayOf(android.R.attr.state_enabled), intArrayOf()),
        intArrayOf(Color.rgb(81, 67, 188), Color.rgb(117, 117, 130))))
    elevation = 0f
    stateListAnimator = null
}
