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

package com.oriondev.moneywallet.ui.view.theme;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.annotation.StyleRes;
import androidx.appcompat.app.AlertDialog;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.NestedScrollView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;
import com.oriondev.moneywallet.R;

/**
 * Created by andrea on 20/08/18.
 */
public class ThemedDialog {

    // What material-dialogs faded a disabled action to.
    private static final float DISABLED_BUTTON_ALPHA = 0.4f;

    public static MaterialAlertDialogBuilder buildMaterialDialog(Context context) {
        return new ThemedAlertDialogBuilder(context);
    }

    /**
     * The accent as it lands on a dialog, contrast checked against the dialog surface so it falls
     * back to readable text when the user picks an accent that disappears there. Public because a
     * dialog outside this package tints a widget of its own with it.
     */
    public static int getAccentColor() {
        ITheme theme = ThemeEngine.getTheme();
        int background = theme.getColorCardBackground();
        return Util.visibleOr(theme.getColorAccent(), background, theme.getBestTextColor(background));
    }

    /**
     * The faded foreground for a control that is idle or disabled on a dialog, the same hint color
     * the theme engine hands a hint on that surface. It is read off the dialog surface and not off
     * the accent, so it stays visible whatever accent the user picks.
     */
    public static int getIdleColor() {
        ITheme theme = ThemeEngine.getTheme();
        return theme.getBestHintColor(theme.getColorCardBackground());
    }

    /**
     * The one definition of which dialog style the app draws a dialog with. The builder and the
     * scrollable custom view both read it here so the two cannot drift apart.
     */
    @StyleRes
    private static int getDialogTheme() {
        return ThemeEngine.getTheme().getMode() == ThemeEngine.Mode.DEEP_DARK ? R.style.MoneyWalletAlertDialogDeepDark
                : ThemeEngine.getTheme().isDark() ? R.style.MoneyWalletAlertDialogDark : R.style.MoneyWalletAlertDialogLight;
    }

    /**
     * In deep dark a dialog is black on a black screen, where the scrim cannot darken anything,
     * so it gets a 1dp outline to show its edge. The alert dialog and both pickers paint their
     * window with a MaterialShapeDrawable, which the alert dialog and the date picker wrap in an
     * InsetDrawable, and none of them reads a stroke from a style. Call it once the window background is set.
     */
    public static void outlineDeepDark(Dialog dialog) {
        if (ThemeEngine.getTheme().getMode() != ThemeEngine.Mode.DEEP_DARK || dialog.getWindow() == null) {
            return;
        }
        Drawable background = dialog.getWindow().getDecorView().getBackground();
        if (background instanceof InsetDrawable) {
            background = ((InsetDrawable) background).getDrawable();
        }
        if (background instanceof MaterialShapeDrawable) {
            Context context = dialog.getContext();
            ((MaterialShapeDrawable) background).setStroke(context.getResources().getDisplayMetrics().density,
                    MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, Color.GRAY));
        }
    }

    /**
     * AlertDialog.setView does not scroll what it is given, so the layouts that used to ask
     * material-dialogs to wrap them in a scroller carry their own.
     */
    public static View inflateScrollableView(Context context, @LayoutRes int layoutRes) {
        Context themedContext = new ContextThemeWrapper(context, getDialogTheme());
        NestedScrollView scrollView = new NestedScrollView(themedContext);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // The framework paints selectableItemBackground over the scroller's whole bounds while
            // the scroller holds focus itself, which it takes outside touch mode once an arrow key
            // scrolls the focused child off screen.
            scrollView.setDefaultFocusHighlightEnabled(false);
        }
        View content = LayoutInflater.from(themedContext).inflate(layoutRes, scrollView, false);
        Resources resources = context.getResources();
        int sidePadding = resources.getDimensionPixelSize(R.dimen.material_dialog_custom_view_start_end_padding);
        int verticalPadding = resources.getDimensionPixelSize(R.dimen.material_dialog_custom_view_top_bottom_padding);
        // The side padding replaces what the layout declared instead of adding to it, which is
        // what material-dialogs did with a wrapped custom view, so a layout that already carries
        // its own 24dp keeps 24dp. The scroller does not clip it so the content can scroll
        // through the vertical padding.
        content.setPaddingRelative(sidePadding, 0, sidePadding, 0);
        scrollView.setPadding(0, verticalPadding, 0, verticalPadding);
        scrollView.setClipToPadding(false);
        scrollView.addView(content);
        return scrollView;
    }

    /**
     * Raises the soft keyboard with the dialog and keeps the positive button disabled while the
     * field is empty, which is what material-dialogs did for an input declared without allowing an
     * empty value. The button only exists once the dialog has been shown, so this runs after show.
     */
    public static void showWithInput(AlertDialog dialog, final EditText editText, final boolean allowEmpty) {
        tintInput(editText);
        // A caller that prefills the field would otherwise leave the caret in front of that text
        // and have the first keystroke land at the front of the stored value.
        editText.setSelection(editText.length());
        dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        dialog.show();
        if (allowEmpty) {
            return;
        }
        final Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        positiveButton.setEnabled(editText.length() > 0);
        editText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence text, int start, int before, int count) {
                positiveButton.setEnabled(text.length() > 0);
            }

            @Override
            public void afterTextChanged(Editable text) {}
        });
    }

    /**
     * Paints the underline with the accent while the field is live and leaves it faded while the
     * field is disabled or idle, and paints the typed text and the hint. All of it comes from the
     * theme engine, not from colorControlNormal or textColorPrimary on the inflating context.
     */
    public static void tintInput(EditText editText) {
        int accent = getAccentColor();
        int idle = getIdleColor();
        ColorStateList colors = new ColorStateList(
                new int[][] {
                        new int[] {-android.R.attr.state_enabled},
                        new int[] {-android.R.attr.state_pressed, -android.R.attr.state_focused},
                        new int[] {}
                },
                new int[] {idle, idle, accent}
        );
        ViewCompat.setBackgroundTintList(editText, colors);
        TintHelper.setCursorTint(editText, accent);
        ITheme theme = ThemeEngine.getTheme();
        editText.setTextColor(theme.getBestTextColor(theme.getColorCardBackground()));
        editText.setHintTextColor(idle);
    }

    /**
     * The single alert dialog factory for the app. It picks the light or dark dialog theme and
     * puts the accent on the buttons, which only exist once the dialog is shown.
     */
    private static class ThemedAlertDialogBuilder extends MaterialAlertDialogBuilder {

        private final ColorStateList mButtonColors;

        private ThemedAlertDialogBuilder(Context context) {
            super(context, getDialogTheme());
            int accent = getAccentColor();
            mButtonColors = new ColorStateList(
                    new int[][] {new int[] {-android.R.attr.state_enabled}, new int[] {}},
                    new int[] {Util.adjustAlpha(accent, DISABLED_BUTTON_ALPHA), accent}
            );
        }

        @Override
        public AlertDialog create() {
            final AlertDialog dialog = super.create();
            outlineDeepDark(dialog);
            dialog.setOnShowListener(new DialogInterface.OnShowListener() {

                @Override
                public void onShow(DialogInterface unused) {
                    tintButton(dialog, AlertDialog.BUTTON_POSITIVE);
                    tintButton(dialog, AlertDialog.BUTTON_NEGATIVE);
                    tintButton(dialog, AlertDialog.BUTTON_NEUTRAL);
                }
            });
            return dialog;
        }

        private void tintButton(AlertDialog dialog, int which) {
            Button button = dialog.getButton(which);
            if (button != null) {
                // A state list, not a single color, so a disabled button still reads as disabled.
                button.setTextColor(mButtonColors);
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
    public static MaterialDatePicker<Long> buildDatePickerDialog(long selectionUtcMillis) {
        return MaterialDatePicker.Builder.datePicker()
                .setTheme(ThemeEngine.getTheme().getMode() == ThemeEngine.Mode.DEEP_DARK ? R.style.MoneyWalletDatePickerDeepDark
                        : ThemeEngine.getTheme().isDark() ? R.style.MoneyWalletDatePickerDark : R.style.MoneyWalletDatePickerLight)
                .setSelection(selectionUtcMillis)
                .build();
    }

    /**
     * The time picker, opened on its keyboard input mode, which is the closest thing the library
     * this replaces had to a number pad.
     */
    public static MaterialTimePicker buildTimePickerDialog(boolean is24HourMode, int hourOfDay, int minute) {
        return new MaterialTimePicker.Builder()
                .setTheme(ThemeEngine.getTheme().getMode() == ThemeEngine.Mode.DEEP_DARK ? R.style.MoneyWalletTimePickerDeepDark
                        : ThemeEngine.getTheme().isDark() ? R.style.MoneyWalletTimePickerDark : R.style.MoneyWalletTimePickerLight)
                .setInputMode(MaterialTimePicker.INPUT_MODE_KEYBOARD)
                .setTimeFormat(is24HourMode ? TimeFormat.CLOCK_24H : TimeFormat.CLOCK_12H)
                .setHour(hourOfDay)
                .setMinute(minute)
                .build();
    }

    public static BottomSheetMenuBuilder buildBottomSheet(Context context) {
        return new BottomSheetMenuBuilder(context);
    }

    /**
     * Which bottom sheet style the app draws a sheet with, picked the same way
     * getDialogTheme picks the alert dialog style.
     */
    @StyleRes
    private static int getBottomSheetTheme() {
        return ThemeEngine.getTheme().getMode() == ThemeEngine.Mode.DEEP_DARK ? R.style.MoneyWalletBottomSheetDeepDark
                : ThemeEngine.getTheme().isDark() ? R.style.MoneyWalletBottomSheetDark : R.style.MoneyWalletBottomSheetLight;
    }

    /**
     * A bottom sheet whose rows are a NavigationView menu built in code, which is the same widget
     * the navigation drawer uses. It keeps the call shape of the builder it replaces: a title,
     * then items, then a click listener, then createDialog.
     */
    public static class BottomSheetMenuBuilder {

        private final BottomSheetDialog mDialog;
        private final NavigationView mNavigationView;

        private Menu mMenu;

        private BottomSheetMenuBuilder(Context context) {
            mDialog = new BottomSheetDialog(context, getBottomSheetTheme());
            mNavigationView = new NavigationView(mDialog.getContext());
            // The sheet behind the menu already carries the surface color, from the style above,
            // and rounds its own top corners, which an opaque menu background would square off.
            mNavigationView.setBackgroundColor(Color.TRANSPARENT);
            // NavigationView extends ScrimInsetsFrameLayout, whose constructor installs an inset
            // listener that hands the bottom system window inset to NavigationMenuPresenter, and
            // that pads the menu list by it. BottomSheetBehavior has already padded the sheet
            // itself by the same inset earlier in the same dispatch, and returns the insets
            // unconsumed, so the menu list would carry a second copy and the last row would sit a
            // navigation bar height above the sheet floor. Replacing the listener leaves the list
            // on its own design_navigation_padding_bottom.
            ViewCompat.setOnApplyWindowInsetsListener(mNavigationView, (v, insets) -> WindowInsetsCompat.CONSUMED);
            ITheme theme = ThemeEngine.getTheme();
            mNavigationView.setItemTextColor(ColorStateList.valueOf(theme.getTextColorPrimary()));
            mNavigationView.setItemIconTintList(ColorStateList.valueOf(theme.getIconColor()));
            mMenu = mNavigationView.getMenu();
        }

        /**
         * The header above the rows. NavigationView draws the title of a submenu as a group
         * header, so every item added after this call goes into that submenu.
         */
        public BottomSheetMenuBuilder addTitleItem(@StringRes int titleRes) {
            mMenu = mMenu.addSubMenu(titleRes);
            return this;
        }

        public BottomSheetMenuBuilder addItem(int id, @StringRes int titleRes, @DrawableRes int iconRes) {
            mMenu.add(Menu.NONE, id, Menu.NONE, titleRes).setIcon(iconRes);
            return this;
        }

        public BottomSheetMenuBuilder setItemClickListener(final NavigationView.OnNavigationItemSelectedListener listener) {
            mNavigationView.setNavigationItemSelectedListener(new NavigationView.OnNavigationItemSelectedListener() {

                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                    // The builder this replaces dismissed the sheet on a tap, and every caller
                    // opens something else from here.
                    mDialog.dismiss();
                    return listener.onNavigationItemSelected(item);
                }

            });
            return this;
        }

        public BottomSheetDialog createDialog() {
            mDialog.setContentView(mNavigationView);
            return mDialog;
        }
    }
}