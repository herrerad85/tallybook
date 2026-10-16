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

package com.oriondev.moneywallet.ui.activity.base;

import android.app.UiModeManager;
import android.graphics.Color;
import android.util.TypedValue;

import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Below Android 12 is sdk 24 because the suite already loads it. Each further sdk keeps another
 * copy of the framework in the one test JVM, and sdk 30 here was enough to run the whole suite out
 * of memory. The system contrast test is sdk 36 for the same reason.
 */
@RunWith(RobolectricTestRunner.class)
public class ThemedActivityDynamicColorTest {

    private static final int STATIC_COLOR_PRIMARY = 0xFF24389C;
    private static final int GREEN = 0xFF2E7D32;

    private final List<ActivityController<?>> mControllers = new ArrayList<>();

    private int mOriginalPrimary;
    private ThemeEngine.Mode mOriginalMode;
    private boolean mOriginalSystemColors;

    @Before
    public void saveTheTheme() {
        ThemeEngine.clearPreview();
        mOriginalPrimary = ThemeEngine.getSavedColorPrimary();
        mOriginalMode = ThemeEngine.getTheme().getMode();
        mOriginalSystemColors = ThemeEngine.isSystemColorsEnabled();
        ThemeEngine.setUseSystemColors(false);
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        ThemeEngine.setColorPrimary(GREEN);
    }

    /**
     * ThemeEngine keeps its observers in a static list for the whole run, so a screen left alive
     * here would be recreated by a later test's theme change. Destroyed before the theme is put
     * back for the same reason.
     */
    @After
    public void restoreTheTheme() {
        for (ActivityController<?> controller : mControllers) {
            if (!controller.get().isDestroyed()) {
                controller.destroy();
            }
        }
        ThemeEngine.clearPreview();
        ThemeEngine.setColorPrimary(mOriginalPrimary);
        ThemeEngine.setMode(mOriginalMode);
        ThemeEngine.setUseSystemColors(mOriginalSystemColors);
    }

    @Test
    @Config(sdk = 33)
    public void theRolesFollowTheSavedPrimaryFromAndroid12() {
        int primary = resolve(launch(), androidx.appcompat.R.attr.colorPrimary);
        assertNotEquals("the static role stayed", STATIC_COLOR_PRIMARY, primary);
        float[] hsv = new float[3];
        Color.colorToHSV(primary, hsv);
        assertTrue("primary " + Integer.toHexString(primary) + " is not a green",
                hsv[0] >= 90 && hsv[0] <= 160);
    }

    @Test
    @Config(sdk = 24)
    public void theStaticRolesStayBeforeAndroid12() {
        assertEquals(STATIC_COLOR_PRIMARY,
                resolve(launch(), androidx.appcompat.R.attr.colorPrimary));
    }

    @Test
    @Config(sdk = 33)
    public void deepDarkStaysBlackOverTheSeededRoles() {
        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
        RecreateCountingActivity activity = launch();
        assertEquals(Color.BLACK, resolve(activity, android.R.attr.colorBackground));
        assertEquals(Color.BLACK, resolve(activity, com.google.android.material.R.attr.colorSurface));
        assertEquals(Color.BLACK,
                resolve(activity, com.google.android.material.R.attr.colorSurfaceDim));
    }

    @Test
    @Config(sdk = 33)
    public void aSavedPrimaryRebuildsTheActivityAndAPreviewDoesNot() {
        RecreateCountingActivity activity = launch();
        ThemeEngine.previewColorPrimary(GREEN ^ 0x00FFFFFF);
        assertEquals("a preview rebuilt the activity", 0, activity.mRecreates);
        ThemeEngine.clearPreview();

        ThemeEngine.setColorPrimary(GREEN ^ 0x00FFFFFF);
        assertEquals(1, activity.mRecreates);
    }

    @Test
    @Config(sdk = 24)
    public void aSavedPrimaryOnlyRepaintsBeforeAndroid12() {
        RecreateCountingActivity activity = launch();
        ThemeEngine.setColorPrimary(GREEN ^ 0x00FFFFFF);
        assertEquals(0, activity.mRecreates);
    }

    @Test
    @Config(sdk = 33)
    public void turningTheSystemColorsOnRebuildsTheActivityAndTheSameValueDoesNot() {
        RecreateCountingActivity activity = launch();
        ThemeEngine.setUseSystemColors(true);
        assertEquals(1, activity.mRecreates);
        ThemeEngine.setUseSystemColors(true);
        assertEquals("setting the value it already had rebuilt the activity", 1, activity.mRecreates);
    }

    @Test
    @Config(sdk = 33)
    public void turningTheSystemColorsOffRebuildsTheActivityAndTheSameValueDoesNot() {
        ThemeEngine.setUseSystemColors(true);
        RecreateCountingActivity activity = launch();
        ThemeEngine.setUseSystemColors(false);
        assertEquals(1, activity.mRecreates);
        ThemeEngine.setUseSystemColors(false);
        assertEquals("setting the value it already had rebuilt the activity", 1, activity.mRecreates);
    }

    @Test
    @Config(sdk = 33)
    public void aSavedPrimaryDoesNotRebuildTheActivityWhileTheSystemColorsAreOn() {
        ThemeEngine.setUseSystemColors(true);
        RecreateCountingActivity activity = launch();
        ThemeEngine.setColorPrimary(GREEN ^ 0x00FFFFFF);
        assertEquals(0, activity.mRecreates);
    }

    @Test
    @Config(sdk = {33, 34})
    public void theSystemColorsReplaceTheStaticRoles() {
        ThemeEngine.setUseSystemColors(true);
        RecreateCountingActivity activity = launch();
        int primary = resolve(activity, androidx.appcompat.R.attr.colorPrimary);
        assertNotEquals("the static role stayed", STATIC_COLOR_PRIMARY, primary);
        assertEquals("the toolbar and the components disagree", primary,
                ThemeEngine.getTheme().getColorPrimary());
        assertEquals(resolve(activity, com.google.android.material.R.attr.colorSecondary),
                ThemeEngine.getTheme().getColorAccent());
    }

    @Test
    @Config(sdk = {33, 34})
    public void darkTakesTheDarkSystemRolesWithoutTheBlackSurfaces() {
        ThemeEngine.setUseSystemColors(true);
        ThemeEngine.setMode(ThemeEngine.Mode.DARK);
        RecreateCountingActivity activity = launch();
        assertEquals(resolve(activity, com.google.android.material.R.attr.colorSecondary),
                ThemeEngine.getTheme().getColorAccent());
        assertNotEquals(Color.BLACK, resolve(activity, android.R.attr.colorBackground));
    }

    @Test
    @Config(sdk = {33, 34})
    public void deepDarkStaysBlackOverTheSystemColors() {
        ThemeEngine.setUseSystemColors(true);
        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
        RecreateCountingActivity activity = launch();
        assertEquals(resolve(activity, com.google.android.material.R.attr.colorSecondary),
                ThemeEngine.getTheme().getColorAccent());
        assertEquals(Color.BLACK, resolve(activity, android.R.attr.colorBackground));
        assertEquals(Color.BLACK, resolve(activity, com.google.android.material.R.attr.colorSurface));
        assertEquals(Color.BLACK,
                resolve(activity, com.google.android.material.R.attr.colorSurfaceDim));
        assertNotEquals("the static role stayed", 0xFFBAC3FF,
                resolve(activity, androidx.appcompat.R.attr.colorPrimary));
    }

    @Test
    @Config(sdk = 36)
    public void aContrastBelowTheStandardLevelSeedsTheStandardRoles() {
        int standard = onSurfaceVariantAtContrast(0);
        assertEquals(standard, onSurfaceVariantAtContrast(-1));
        assertNotEquals("the contrast never reached the seeding", standard,
                onSurfaceVariantAtContrast(1));
    }

    private int onSurfaceVariantAtContrast(float contrast) {
        shadowOf(RuntimeEnvironment.getApplication().getSystemService(UiModeManager.class))
                .setContrast(contrast);
        return resolve(launch(), com.google.android.material.R.attr.colorOnSurfaceVariant);
    }

    private RecreateCountingActivity launch() {
        ActivityController<RecreateCountingActivity> controller =
                Robolectric.buildActivity(RecreateCountingActivity.class);
        mControllers.add(controller);
        return controller.setup().get();
    }

    private static int resolve(RecreateCountingActivity activity, int attr) {
        TypedValue value = new TypedValue();
        assertTrue(activity.getTheme().resolveAttribute(attr, value, true));
        return value.data;
    }

    public static class RecreateCountingActivity extends ThemedActivity {

        private int mRecreates;

        @Override
        public void recreate() {
            mRecreates++;
        }
    }
}
