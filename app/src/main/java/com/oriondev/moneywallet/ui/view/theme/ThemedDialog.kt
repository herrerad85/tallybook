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

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.content.res.Resources
import android.graphics.Color
import android.graphics.drawable.InsetDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText

import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView

import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.SecureWindow

/**
 * Created by andrea on 20/08/18.
 */
object ThemedDialog {

    // What material-dialogs faded a disabled action to.
    private const val DISABLED_BUTTON_ALPHA = 0.4f

    @JvmStatic
    fun buildMaterialDialog(context: Context?): MaterialAlertDialogBuilder {
        return ThemedAlertDialogBuilder(context)
    }

    /**
     * The accent as it lands on a dialog, contrast checked against the dialog surface so it falls
     * back to readable text when the user picks an accent that disappears there. Public because a
     * dialog outside this package tints a widget of its own with it.
     */
    @JvmStatic
    fun getAccentColor(): Int {
        val theme = ThemeEngine.getTheme()
        val background = theme.colorCardBackground
        return Util.visibleOr(theme.colorAccent, background, theme.getBestTextColor(background))
    }

    /**
     * The faded foreground for a control that is idle or disabled on a dialog, the same hint color
     * the theme engine hands a hint on that surface. It is read off the dialog surface and not off
     * the accent, so it stays visible whatever accent the user picks.
     */
    @JvmStatic
    fun getIdleColor(): Int {
        val theme = ThemeEngine.getTheme()
        return theme.getBestHintColor(theme.colorCardBackground)
    }

    /**
     * The one definition of which dialog style the app draws a dialog with. The builder and the
     * scrollable custom view both read it here so the two cannot drift apart.
     */
    @StyleRes
    private fun getDialogTheme(): Int {
        return if (ThemeEngine.getTheme().mode == ThemeEngine.Mode.DEEP_DARK) R.style.MoneyWalletAlertDialogDeepDark
                else if (ThemeEngine.getTheme().isDark) R.style.MoneyWalletAlertDialogDark else R.style.MoneyWalletAlertDialogLight
    }

    /**
     * In deep dark a dialog is black on a black screen, where the scrim cannot darken anything,
     * so it gets a 1dp outline to show its edge. The alert dialog and both pickers paint their
     * window with a MaterialShapeDrawable, which the alert dialog and the date picker wrap in an
     * InsetDrawable, and none of them reads a stroke from a style. Call it once the window background is set.
     */
    @JvmStatic
    fun outlineDeepDark(dialog: Dialog?) {
        if (ThemeEngine.getTheme().mode != ThemeEngine.Mode.DEEP_DARK || dialog!!.window == null) {
            return
        }
        var background = dialog.window!!.decorView.background
        if (background is InsetDrawable) {
            background = background.drawable
        }
        if (background is MaterialShapeDrawable) {
            val context = dialog.context
            background.setStroke(context.resources.displayMetrics.density,
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, Color.GRAY))
        }
    }

    /**
     * AlertDialog.setView does not scroll what it is given, so the layouts that used to ask
     * material-dialogs to wrap them in a scroller carry their own.
     */
    @JvmStatic
    fun inflateScrollableView(context: Context, @LayoutRes layoutRes: Int): View {
        val themedContext: Context = ContextThemeWrapper(context, getDialogTheme())
        val scrollView = NestedScrollView(themedContext)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The framework paints selectableItemBackground over the scroller's whole bounds while
            // the scroller holds focus itself, which it takes outside touch mode once an arrow key
            // scrolls the focused child off screen.
            scrollView.defaultFocusHighlightEnabled = false
        }
        val content = LayoutInflater.from(themedContext).inflate(layoutRes, scrollView, false)
        val resources: Resources = context.resources
        val sidePadding = resources.getDimensionPixelSize(R.dimen.material_dialog_custom_view_start_end_padding)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.material_dialog_custom_view_top_bottom_padding)
        // The side padding replaces what the layout declared instead of adding to it, which is
        // what material-dialogs did with a wrapped custom view, so a layout that already carries
        // its own 24dp keeps 24dp. The scroller does not clip it so the content can scroll
        // through the vertical padding.
        content.setPaddingRelative(sidePadding, 0, sidePadding, 0)
        scrollView.setPadding(0, verticalPadding, 0, verticalPadding)
        scrollView.clipToPadding = false
        scrollView.addView(content)
        return scrollView
    }

    /**
     * Raises the soft keyboard with the dialog and keeps the positive button disabled while the
     * field is empty, which is what material-dialogs did for an input declared without allowing an
     * empty value. The button only exists once the dialog has been shown, so this runs after show.
     */
    @JvmStatic
    fun showWithInput(dialog: AlertDialog, editText: EditText, allowEmpty: Boolean) {
        tintInput(editText)
        // A caller that prefills the field would otherwise leave the caret in front of that text
        // and have the first keystroke land at the front of the stored value.
        editText.setSelection(editText.length())
        dialog.window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE or
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
        dialog.show()
        if (allowEmpty) {
            return
        }
        val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        positiveButton.isEnabled = editText.length() > 0
        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {
                positiveButton.isEnabled = text!!.length > 0
            }

            override fun afterTextChanged(text: Editable?) {}
        })
    }

    /**
     * Paints the underline with the accent while the field is live and leaves it faded while the
     * field is disabled or idle, and paints the typed text and the hint. All of it comes from the
     * theme engine, not from colorControlNormal or textColorPrimary on the inflating context.
     */
    @JvmStatic
    fun tintInput(editText: EditText) {
        val accent = getAccentColor()
        val idle = getIdleColor()
        val colors = ColorStateList(
                arrayOf(
                        intArrayOf(-android.R.attr.state_enabled),
                        intArrayOf(-android.R.attr.state_pressed, -android.R.attr.state_focused),
                        intArrayOf()
                ),
                intArrayOf(idle, idle, accent)
        )
        ViewCompat.setBackgroundTintList(editText, colors)
        TintHelper.setCursorTint(editText, accent)
        val theme = ThemeEngine.getTheme()
        editText.setTextColor(theme.getBestTextColor(theme.colorCardBackground))
        editText.setHintTextColor(idle)
    }

    /**
     * The single alert dialog factory for the app. It picks the light or dark dialog theme and
     * puts the accent on the buttons, which only exist once the dialog is shown.
     */
    private class ThemedAlertDialogBuilder : MaterialAlertDialogBuilder {

        private val mButtonColors: ColorStateList

        constructor(context: Context?) : super(context!!, getDialogTheme()) {
            val accent = getAccentColor()
            mButtonColors = ColorStateList(
                    arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
                    intArrayOf(Util.adjustAlpha(accent, DISABLED_BUTTON_ALPHA), accent)
            )
        }

        override fun create(): AlertDialog {
            val dialog = super.create()
            SecureWindow.update(dialog.window!!)
            outlineDeepDark(dialog)
            dialog.setOnShowListener(object : DialogInterface.OnShowListener {

                override fun onShow(unused: DialogInterface?) {
                    tintButton(dialog, AlertDialog.BUTTON_POSITIVE)
                    tintButton(dialog, AlertDialog.BUTTON_NEGATIVE)
                    tintButton(dialog, AlertDialog.BUTTON_NEUTRAL)
                }
            })
            return dialog
        }

        private fun tintButton(dialog: AlertDialog, which: Int) {
            val button = dialog.getButton(which)
            if (button != null) {
                // A state list, not a single color, so a disabled button still reads as disabled.
                button.setTextColor(mButtonColors)
            }
        }
    }

    /**
     * The date picker, on the style the theme engine picks. The selection is milliseconds in UTC
     * because that is the only thing MaterialDatePicker accepts; the caller owns the conversion.
     * MaterialDatePicker.getThemeResId returns the id handed to setTheme as soon as it is non
     * zero, and onCreateDialog then reads materialCalendarStyle off a Dialog built with it, which
     * ThemeOverlay.Material3.MaterialCalendar sets. So the app theme needs no calendar
     * attribute of its own.
     */
    @JvmStatic
    fun buildDatePickerDialog(selectionUtcMillis: Long): MaterialDatePicker<Long> {
        return MaterialDatePicker.Builder.datePicker()
                .setTheme(if (ThemeEngine.getTheme().mode == ThemeEngine.Mode.DEEP_DARK) R.style.MoneyWalletDatePickerDeepDark
                        else if (ThemeEngine.getTheme().isDark) R.style.MoneyWalletDatePickerDark else R.style.MoneyWalletDatePickerLight)
                .setSelection(selectionUtcMillis)
                .build()
    }

    /**
     * The time picker, opened on its keyboard input mode, which is the closest thing the library
     * this replaces had to a number pad.
     */
    @JvmStatic
    fun buildTimePickerDialog(is24HourMode: Boolean, hourOfDay: Int, minute: Int): MaterialTimePicker {
        return MaterialTimePicker.Builder()
                .setTheme(if (ThemeEngine.getTheme().mode == ThemeEngine.Mode.DEEP_DARK) R.style.MoneyWalletTimePickerDeepDark
                        else if (ThemeEngine.getTheme().isDark) R.style.MoneyWalletTimePickerDark else R.style.MoneyWalletTimePickerLight)
                .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                .setTimeFormat(if (is24HourMode) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setHour(hourOfDay)
                .setMinute(minute)
                .build()
    }

    @JvmStatic
    fun buildBottomSheet(context: Context): BottomSheetMenuBuilder {
        return BottomSheetMenuBuilder(context)
    }

    /**
     * Which bottom sheet style the app draws a sheet with, picked the same way
     * getDialogTheme picks the alert dialog style.
     */
    @StyleRes
    private fun getBottomSheetTheme(): Int {
        return if (ThemeEngine.getTheme().mode == ThemeEngine.Mode.DEEP_DARK) R.style.MoneyWalletBottomSheetDeepDark
                else if (ThemeEngine.getTheme().isDark) R.style.MoneyWalletBottomSheetDark else R.style.MoneyWalletBottomSheetLight
    }

    /**
     * A bottom sheet whose rows are a NavigationView menu built in code, which is the same widget
     * the navigation drawer uses. It keeps the call shape of the builder it replaces: a title,
     * then items, then a click listener, then createDialog.
     */
    class BottomSheetMenuBuilder {

        private val mDialog: BottomSheetDialog
        private val mNavigationView: NavigationView

        private var mMenu: Menu

        internal constructor(context: Context) {
            mDialog = BottomSheetDialog(context, getBottomSheetTheme())
            mNavigationView = NavigationView(mDialog.context)
            // The sheet behind the menu already carries the surface color, from the style above,
            // and rounds its own top corners, which an opaque menu background would square off.
            mNavigationView.setBackgroundColor(Color.TRANSPARENT)
            // NavigationView extends ScrimInsetsFrameLayout, whose constructor installs an inset
            // listener that hands the bottom system window inset to NavigationMenuPresenter, and
            // that pads the menu list by it. BottomSheetBehavior has already padded the sheet
            // itself by the same inset earlier in the same dispatch, and returns the insets
            // unconsumed, so the menu list would carry a second copy and the last row would sit a
            // navigation bar height above the sheet floor. Replacing the listener leaves the list
            // on its own design_navigation_padding_bottom.
            ViewCompat.setOnApplyWindowInsetsListener(mNavigationView) { v, insets -> WindowInsetsCompat.CONSUMED }
            val theme = ThemeEngine.getTheme()
            mNavigationView.itemTextColor = ColorStateList.valueOf(theme.textColorPrimary)
            mNavigationView.itemIconTintList = ColorStateList.valueOf(theme.iconColor)
            mMenu = mNavigationView.menu
        }

        /**
         * The header above the rows. NavigationView draws the title of a submenu as a group
         * header, so every item added after this call goes into that submenu.
         */
        fun addTitleItem(@StringRes titleRes: Int): BottomSheetMenuBuilder {
            mMenu = mMenu.addSubMenu(titleRes)
            return this
        }

        fun addItem(id: Int, @StringRes titleRes: Int, @DrawableRes iconRes: Int): BottomSheetMenuBuilder {
            mMenu.add(Menu.NONE, id, Menu.NONE, titleRes).setIcon(iconRes)
            return this
        }

        fun setItemClickListener(listener: NavigationView.OnNavigationItemSelectedListener?): BottomSheetMenuBuilder {
            mNavigationView.setNavigationItemSelectedListener(object : NavigationView.OnNavigationItemSelectedListener {

                override fun onNavigationItemSelected(item: MenuItem): Boolean {
                    // The builder this replaces dismissed the sheet on a tap, and every caller
                    // opens something else from here.
                    mDialog.dismiss()
                    return listener!!.onNavigationItemSelected(item)
                }

            })
            return this
        }

        fun createDialog(): BottomSheetDialog {
            mDialog.setContentView(mNavigationView)
            return mDialog
        }
    }
}