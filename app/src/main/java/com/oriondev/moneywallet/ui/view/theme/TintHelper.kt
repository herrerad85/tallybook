/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.view.theme

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.core.graphics.drawable.DrawableCompat
import androidx.appcompat.widget.SwitchCompat
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.RadioButton
import android.widget.SeekBar

/**
 * Created by andrea on 11/04/18.
 */
internal object TintHelper {

    private val COLOR_BUTTON_DISABLED_LIGHT = Color.parseColor("#1F000000")
    private val COLOR_BUTTON_DISABLED_DARK = Color.parseColor("#1F000000")

    private val COLOR_CONTROL_DISABLED_DARK = Color.parseColor("#43000000")
    private val COLOR_CONTROL_DISABLED_LIGHT = Color.parseColor("#4DFFFFFF")
    private val COLOR_CONTROL_NORMAL_DARK = Color.parseColor("#B3FFFFFF")
    private val COLOR_CONTROL_NORMAL_LIGHT = Color.parseColor("#8A000000")

    private val COLOR_SWITCH_THUMB_DISABLED_LIGHT = Color.parseColor("#FFBDBDBD")
    private val COLOR_SWITCH_THUMB_DISABLED_DARK = Color.parseColor("#FF424242")
    private val COLOR_SWITCH_THUMB_NORMAL_LIGHT = Color.parseColor("#FFFAFAFA")
    private val COLOR_SWITCH_THUMB_NORMAL_DARK = Color.parseColor("#FFBDBDBD")
    private val COLOR_SWITCH_TRACK_DISABLED_LIGHT = Color.parseColor("#1F000000")
    private val COLOR_SWITCH_TRACK_DISABLED_DARK = Color.parseColor("#1AFFFFFF")
    private val COLOR_SWITCH_TRACK_NORMAL_LIGHT = Color.parseColor("#43000000")
    private val COLOR_SWITCH_TRACK_NORMAL_DARK = Color.parseColor("#4DFFFFFF")

    // Tints the drawable it was given, after mutate() so the tint does not reach every drawable
    // sharing its state. From API 23 the wrap is the identity, so the caller's object comes back,
    // and setCursorTint relies on that: the editor keeps drawing the drawable it loaded first.
    @JvmStatic
    @CheckResult
    fun createTintedDrawable(drawable: Drawable?, @ColorInt color: Int): Drawable? {
        var drawable = drawable
        if (drawable == null) {
            return null
        }
        drawable = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.SRC_IN)
        DrawableCompat.setTint(drawable, color)
        return drawable
    }

    @JvmStatic
    @CheckResult
    fun createTintedDrawable(drawable: Drawable?, colorStateList: ColorStateList): Drawable? {
        var drawable = drawable
        if (drawable == null) {
            return null
        }
        drawable = DrawableCompat.wrap(drawable.mutate())
        DrawableCompat.setTintList(drawable, colorStateList)
        return drawable
    }

    private fun modifySwitchDrawable(context: Context, from: Drawable?, @ColorInt tint: Int, thumb: Boolean, useDarker: Boolean): Drawable? {
        var tint = tint
        if (useDarker) {
            tint = Util.shiftColor(tint, 1.1f)
        }
        tint = Util.adjustAlpha(tint, if (thumb) 1.0f else 0.5f)
        val disabled: Int
        val normal: Int
        if (thumb) {
            disabled = if (useDarker) COLOR_SWITCH_THUMB_DISABLED_DARK else COLOR_SWITCH_THUMB_DISABLED_LIGHT
            normal = if (useDarker) COLOR_SWITCH_THUMB_NORMAL_DARK else COLOR_SWITCH_THUMB_NORMAL_LIGHT
        } else {
            disabled = if (useDarker) COLOR_SWITCH_TRACK_DISABLED_DARK else COLOR_SWITCH_TRACK_DISABLED_LIGHT
            normal = if (useDarker) COLOR_SWITCH_TRACK_NORMAL_DARK else COLOR_SWITCH_TRACK_NORMAL_LIGHT
        }
        val stateList = ColorStateList(arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_activated, -android.R.attr.state_checked),
                    intArrayOf(android.R.attr.state_enabled, android.R.attr.state_activated),
                    intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                ), intArrayOf(disabled, normal, tint, tint)
        )
        return createTintedDrawable(from, stateList)
    }

    @JvmStatic
    fun applyTint(button: Button, @ColorInt color: Int, @ColorInt rippleColor: Int, useDarker: Boolean) {
        val disabled = if (useDarker) COLOR_BUTTON_DISABLED_DARK else COLOR_BUTTON_DISABLED_LIGHT
        val pressed = Util.shiftColor(color, if (useDarker) 0.9f else 1.1f)
        val activated = Util.shiftColor(color, if (useDarker) 1.1f else 0.9f)
        val colorStateList = ColorStateList(
                arrayOf(
                        intArrayOf(-android.R.attr.state_enabled), intArrayOf(android.R.attr.state_enabled)
                ),
                intArrayOf(disabled, color)
        )
        if (button.background is RippleDrawable) {
            val rippleDrawable = button.background as RippleDrawable
            rippleDrawable.setColor(ColorStateList.valueOf(rippleColor))
        }
        var drawable = button.background
        if (drawable != null) {
            drawable = createTintedDrawable(drawable, colorStateList)
            button.background = drawable
        }
    }

    @JvmStatic
    fun applyTint(checkBox: CheckBox, @ColorInt color: Int, useDarker: Boolean) {
        val stateList = ColorStateList(arrayOf(
                intArrayOf(-android.R.attr.state_enabled),
                intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
                intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
        ),
                intArrayOf(
                        Util.stripAlpha(if (useDarker) COLOR_CONTROL_DISABLED_DARK else COLOR_CONTROL_DISABLED_LIGHT),
                        if (useDarker) COLOR_CONTROL_NORMAL_DARK else COLOR_CONTROL_NORMAL_LIGHT,
                        color
                )
        )
        checkBox.buttonTintList = stateList
    }

    @JvmStatic
    fun applyTint(radioButton: RadioButton, @ColorInt color: Int, useDarker: Boolean) {
        val stateList = ColorStateList(arrayOf(
                                        intArrayOf(-android.R.attr.state_enabled),
                                        intArrayOf(android.R.attr.state_enabled, -android.R.attr.state_checked),
                                        intArrayOf(android.R.attr.state_enabled, android.R.attr.state_checked)
                                    ),
                                    intArrayOf(
                                        Util.stripAlpha(if (useDarker) COLOR_CONTROL_DISABLED_DARK else COLOR_CONTROL_DISABLED_LIGHT),
                                        if (useDarker) COLOR_CONTROL_NORMAL_DARK else COLOR_CONTROL_NORMAL_LIGHT,
                                        color
                                    )
        )
        radioButton.buttonTintList = stateList
    }

    @JvmStatic
    fun applyTint(switchCompat: SwitchCompat, @ColorInt color: Int, useDarker: Boolean) {
        if (switchCompat.trackDrawable != null) {
            switchCompat.trackDrawable =
                    modifySwitchDrawable(
                            switchCompat.context,
                            switchCompat.trackDrawable,
                            color,
                            false,
                            useDarker
                    )
        }
        if (switchCompat.thumbDrawable != null) {
            switchCompat.thumbDrawable =
                    modifySwitchDrawable(
                            switchCompat.context,
                            switchCompat.thumbDrawable,
                            color,
                            true,
                            useDarker
                    )
        }
    }

    @JvmStatic
    fun applyTint(seekBar: SeekBar, @ColorInt color: Int, useDarker: Boolean) {
        val colorStateList = ColorStateList(
                arrayOf(
                        intArrayOf(-android.R.attr.state_enabled),
                        intArrayOf(android.R.attr.state_enabled)
                ),
                intArrayOf(
                        if (useDarker) COLOR_CONTROL_DISABLED_DARK else COLOR_CONTROL_DISABLED_LIGHT,
                        color
                )
        )
        seekBar.thumbTintList = colorStateList
        seekBar.progressTintList = colorStateList
    }

    /**
     * Below API 29 the cursor drawable is private to the text view, so it keeps the theme's color.
     */
    @JvmStatic
    fun setCursorTint(editText: EditText, @ColorInt color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val cursor = editText.textCursorDrawable
            if (cursor != null) {
                editText.textCursorDrawable = createTintedDrawable(cursor, color)
            }
        }
    }
}