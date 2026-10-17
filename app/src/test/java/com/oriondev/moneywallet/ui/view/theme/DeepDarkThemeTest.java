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
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.picker.DateTimePicker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DeepDarkThemeTest {

    private ThemeEngine.Mode mOriginalMode;

    @Before
    public void saveTheMode() {
        mOriginalMode = ThemeEngine.getTheme().getMode();
    }

    @After
    public void restoreTheMode() {
        ThemeEngine.setMode(mOriginalMode);
    }

    @Test
    public void deepDarkPaintsTheCardTheWindowAndTheDrawerBlack() {
        ITheme theme = ThemeEngine.getTheme();

        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        assertEquals(0xFFFFFFFF, theme.getColorCardBackground());
        assertEquals(0xFFFAFAFA, theme.getColorWindowBackground());
        assertEquals(0xFFF9F9F9, theme.getDrawerBackgroundColor());
        assertEquals(0xFFE8E8E8, theme.getDrawerSelectedItemColor());

        ThemeEngine.setMode(ThemeEngine.Mode.DARK);
        assertEquals(0xFF424242, theme.getColorCardBackground());
        assertEquals(0xFF212121, theme.getColorWindowBackground());
        assertEquals(0xFF303030, theme.getDrawerBackgroundColor());
        assertEquals(0xFF202020, theme.getDrawerSelectedItemColor());

        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
        assertEquals(0xFF000000, theme.getColorCardBackground());
        assertEquals(0xFF000000, theme.getColorWindowBackground());
        assertEquals(0xFF000000, theme.getDrawerBackgroundColor());
        assertEquals(0xFF202020, theme.getDrawerSelectedItemColor());
    }

    @Test
    public void deepDarkDialogsTakeTheDeepDarkStylesAndDarkKeepsTheDarkOnes() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();

            ThemeEngine.setMode(ThemeEngine.Mode.DARK);
            assertEquals(R.style.MoneyWalletAlertDialogDark, alertDialogStyle(activity));
            assertEquals(R.style.MoneyWalletBottomSheetDark, bottomSheetStyle(activity));
            assertEquals(R.style.MoneyWalletDatePickerDark, datePickerStyle());
            assertEquals(R.style.MoneyWalletTimePickerDark, timePickerStyle());

            ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
            assertEquals(R.style.MoneyWalletAlertDialogDeepDark, alertDialogStyle(activity));
            assertEquals(R.style.MoneyWalletBottomSheetDeepDark, bottomSheetStyle(activity));
            assertEquals(R.style.MoneyWalletDatePickerDeepDark, datePickerStyle());
            assertEquals(R.style.MoneyWalletTimePickerDeepDark, timePickerStyle());
        } finally {
            controller.close();
        }
    }

    @Test
    public void deepDarkStylesPaintTheirSurfaceBlack() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();
            int high = com.google.android.material.R.attr.colorSurfaceContainerHigh;
            assertEquals(0xFF000000, color(activity, R.style.MoneyWalletAlertDialogDeepDark, high));
            assertEquals(0xFF000000, color(activity, R.style.MoneyWalletDatePickerDeepDark, high));
            assertEquals(0xFF000000, color(activity, R.style.MoneyWalletTimePickerDeepDark, high));
            assertEquals(0xFF202020, color(activity, R.style.MoneyWalletTimePickerDeepDark,
                    com.google.android.material.R.attr.colorSurfaceContainerHighest));
            assertEquals(0xFF000000, color(activity, R.style.MoneyWalletBottomSheetDeepDark,
                    com.google.android.material.R.attr.colorSurfaceContainerLow));
        } finally {
            controller.close();
        }
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    public void deepDarkThemeAndOverlayPaintEverySurfaceContainerBlack() {
        Resources resources = Robolectric.buildActivity(AppCompatActivity.class).get().getResources();
        Resources.Theme theme = resources.newTheme();
        theme.applyStyle(R.style.MoneyWalletAppThemeDeepDark, true);
        assertSurfacesBlack(theme);
        TypedValue value = new TypedValue();
        assertTrue(theme.resolveAttribute(com.google.android.material.R.attr.popupMenuBackground, value, true));
        assertEquals(R.drawable.popup_background_deep_dark, value.resourceId);
        Context themed = new ContextThemeWrapper(Robolectric.buildActivity(AppCompatActivity.class).setup().get(),
                R.style.MoneyWalletAppThemeDeepDark);
        assertOutlinedPopup(themed, themed.getDrawable(value.resourceId));

        // The overlay goes on after the seeded or system colors, which bring their own containers.
        Resources.Theme overlaid = resources.newTheme();
        overlaid.applyStyle(R.style.MoneyWalletAppThemeDark, true);
        overlaid.applyStyle(R.style.ThemeOverlay_MoneyWallet_DeepDark, true);
        assertSurfacesBlack(overlaid);
    }

    @Test
    public void lightAndDarkThemesKeepTheirPopupAndContainers() {
        Context activity = Robolectric.buildActivity(AppCompatActivity.class).setup().get();
        for (int style : new int[] {R.style.MoneyWalletAppTheme, R.style.MoneyWalletAppThemeDark}) {
            Context themed = new ContextThemeWrapper(activity, style);
            TypedValue value = new TypedValue();
            assertTrue(themed.getTheme().resolveAttribute(
                    com.google.android.material.R.attr.popupMenuBackground, value, true));
            assertNotEquals(R.drawable.popup_background_deep_dark, value.resourceId);
            assertNotEquals(0xFF000000, MaterialColors.getColor(themed,
                    com.google.android.material.R.attr.colorSurfaceContainer, 0));
        }
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    public void deepDarkOutlinesTheAlertDialogAndDarkDoesNot() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();

            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
            assertEquals(0f, outline(ThemedDialog.buildMaterialDialog(activity).create()).getStrokeWidth(), 0f);

            ThemeEngine.setMode(ThemeEngine.Mode.DARK);
            assertEquals(0f, outline(ThemedDialog.buildMaterialDialog(activity).create()).getStrokeWidth(), 0f);

            ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
            MaterialShapeDrawable outline = outline(ThemedDialog.buildMaterialDialog(activity).create());
            assertEquals(3f, outline.getStrokeWidth(), 0f);
            assertEquals(MaterialColors.getColor(activity, com.google.android.material.R.attr.colorOutline, 0),
                    outline.getStrokeColor().getDefaultColor());
        } finally {
            controller.close();
        }
    }

    @Test
    public void deepDarkOutlinesBothPickersAgainAfterARecreate() {
        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
        for (String picker : new String[] {"DatePicker", "TimePicker"}) {
            ActivityController<AppCompatActivity> controller =
                    Robolectric.buildActivity(AppCompatActivity.class).setup();
            try {
                AppCompatActivity activity = controller.get();
                DateTimePicker dateTimePicker = DateTimePicker.createPicker(activity.getSupportFragmentManager(), "dt", null);
                activity.getSupportFragmentManager().executePendingTransactions();
                if (picker.equals("DatePicker")) {
                    dateTimePicker.showDatePicker();
                } else {
                    dateTimePicker.showTimePicker();
                }
                float density = activity.getResources().getDisplayMetrics().density;
                assertEquals(picker, density, outline(shownPicker(activity, picker)).getStrokeWidth(), 0f);

                controller.recreate();
                assertEquals(picker, density, outline(shownPicker(controller.get(), picker)).getStrokeWidth(), 0f);
            } finally {
                controller.close();
            }
        }
    }

    @Test
    @Config(qualifiers = "xxhdpi")
    public void deepDarkGivesTheSpinnerTheOutlinedPopupAndDarkKeepsTheGrayOne() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            ThemedSpinner spinner = new ThemedSpinner(controller.get());

            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
            spinner.onApplyTheme(ThemeEngine.getTheme());
            assertEquals(0xFFFFFFFF, ((ColorDrawable) spinner.getPopupBackground()).getColor());

            ThemeEngine.setMode(ThemeEngine.Mode.DARK);
            spinner.onApplyTheme(ThemeEngine.getTheme());
            assertEquals(0xFF424242, ((ColorDrawable) spinner.getPopupBackground()).getColor());

            ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
            spinner.onApplyTheme(ThemeEngine.getTheme());
            assertOutlinedPopup(spinner.getContext(), spinner.getPopupBackground());
        } finally {
            controller.close();
        }
    }

    /** Runs at xxhdpi, where the 1dp stroke is 3px and a stroke fixed at 1px fails. */
    private static void assertOutlinedPopup(Context context, Drawable popup) {
        GradientDrawable shape = (GradientDrawable) popup;
        assertEquals(0xFF000000, shape.getColor().getDefaultColor());
        Object state = shape.getConstantState();
        assertEquals(3, (int) ReflectionHelpers.getField(state, "mStrokeWidth"));
        assertEquals(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, 0),
                ((ColorStateList) ReflectionHelpers.getField(state, "mStrokeColors")).getDefaultColor());
        Rect padding = new Rect();
        shape.getPadding(padding);
        assertEquals(new Rect(0, 24, 0, 24), padding);
    }

    private static void assertSurfacesBlack(Resources.Theme theme) {
        int[] attrs = {
                com.google.android.material.R.attr.colorSurfaceContainerLowest,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                com.google.android.material.R.attr.colorSurfaceContainer,
                com.google.android.material.R.attr.colorSurfaceContainerHigh,
                com.google.android.material.R.attr.colorSurfaceContainerHighest,
                com.google.android.material.R.attr.colorSurfaceVariant};
        TypedValue value = new TypedValue();
        for (int attr : attrs) {
            assertTrue(theme.resolveAttribute(attr, value, true));
            assertEquals(0xFF000000, value.data);
        }
    }

    private static int color(Context context, int style, int attr) {
        return MaterialColors.getColor(new ContextThemeWrapper(context, style), attr, 0);
    }

    private static DialogFragment shownPicker(AppCompatActivity activity, String picker) {
        activity.getSupportFragmentManager().executePendingTransactions();
        DateTimePicker dateTimePicker = (DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag("dt");
        dateTimePicker.getChildFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        return (DialogFragment) dateTimePicker.getChildFragmentManager().findFragmentByTag("dt::" + picker);
    }

    private static MaterialShapeDrawable outline(DialogFragment picker) {
        return outline(picker.getDialog());
    }

    private static MaterialShapeDrawable outline(Dialog dialog) {
        Drawable background = dialog.getWindow().getDecorView().getBackground();
        if (background instanceof InsetDrawable) {
            background = ((InsetDrawable) background).getDrawable();
        }
        return (MaterialShapeDrawable) background;
    }

    private int alertDialogStyle(Context context) {
        return themeResId(ThemedDialog.buildMaterialDialog(context).getContext());
    }

    private int bottomSheetStyle(Context context) {
        return themeResId(ThemedDialog.buildBottomSheet(context).createDialog().getContext());
    }

    /** Both dialogs wrap their context in android.view.ContextThemeWrapper, whose getThemeResId is hidden. */
    private int themeResId(Context context) {
        return ReflectionHelpers.callInstanceMethod(context, "getThemeResId");
    }

    private int datePickerStyle() {
        return ThemedDialog.buildDatePickerDialog(0).requireArguments().getInt("OVERRIDE_THEME_RES_ID");
    }

    private int timePickerStyle() {
        return ThemedDialog.buildTimePickerDialog(true, 0, 0).requireArguments()
                .getInt("TIME_PICKER_OVERRIDE_THEME_RES_ID");
    }
}
