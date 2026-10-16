/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.fragment.secondary;

import android.os.Looper;

import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class SystemColorsSettingTest {

    private static final String TAG_FRAGMENT = "SystemColorsSettingTest::Fragment";

    private interface Check {

        void run(UserInterfaceSettingFragment fragment);
    }

    private boolean mOriginalSystemColors;
    private int mOriginalPrimary;
    private int mOriginalAccent;

    @Before
    public void saveTheTheme() {
        mOriginalSystemColors = ThemeEngine.isSystemColorsEnabled();
        mOriginalPrimary = ThemeEngine.getSavedColorPrimary();
        mOriginalAccent = ThemeEngine.getSavedColorAccent();
    }

    @After
    public void restoreTheTheme() {
        ThemeEngine.setColorPrimary(mOriginalPrimary);
        ThemeEngine.setColorAccent(mOriginalAccent);
        ThemeEngine.setUseSystemColors(mOriginalSystemColors);
    }

    private static void onSettings(Check check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UserInterfaceSettingFragment fragment = new UserInterfaceSettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                check.run(fragment);
            });
        }
    }

    /** Robolectric's own maker is on the library's list of makers with dynamic color. */
    @Test
    @Config(sdk = {24, 32})
    public void theSwitchIsHiddenWhereDynamicColorIsUnavailable() {
        ShadowBuild.setManufacturer("acme");
        ShadowBuild.setBrand("acme");
        ThemeEngine.setUseSystemColors(true);
        onSettings(fragment -> {
            assertFalse(fragment.findPreference("system_colors").isVisible());
            assertTrue(fragment.findPreference("theme_color_primary").isVisible());
            assertTrue(fragment.findPreference("theme_color_accent").isVisible());
        });
    }

    @Test
    @Config(sdk = 33)
    public void theColorRowsAreHiddenWhileTheSystemColorsAreOn() {
        ThemeEngine.setUseSystemColors(true);
        onSettings(fragment -> {
            SwitchPreferenceCompat system = fragment.findPreference("system_colors");
            assertTrue(system.isVisible());
            assertTrue(system.isChecked());
            assertFalse(fragment.findPreference("theme_color_primary").isVisible());
            assertFalse(fragment.findPreference("theme_color_accent").isVisible());
        });
    }

    @Test
    @Config(sdk = 33)
    public void turningTheSwitchStoresItAndHidesOrShowsTheColorRows() {
        ThemeEngine.setUseSystemColors(false);
        onSettings(fragment -> {
            Preference primary = fragment.findPreference("theme_color_primary");
            Preference accent = fragment.findPreference("theme_color_accent");
            assertTrue(primary.isVisible());
            assertTrue(accent.isVisible());

            fragment.<SwitchPreferenceCompat>findPreference("system_colors").performClick();
            assertTrue(ThemeEngine.isSystemColorsEnabled());
            assertFalse(primary.isVisible());
            assertFalse(accent.isVisible());

            fragment.<SwitchPreferenceCompat>findPreference("system_colors").performClick();
            assertFalse(ThemeEngine.isSystemColorsEnabled());
            assertTrue(primary.isVisible());
            assertTrue(accent.isVisible());
        });
    }

    @Test
    @Config(sdk = 33)
    public void thePickersStartFromTheSavedColorsWhileTheSystemColorsAreOn() {
        ThemeEngine.setUseSystemColors(true);
        int savedPrimary = ThemeEngine.getTheme().getColorPrimary() ^ 0x00FFFFFF;
        int savedAccent = ThemeEngine.getTheme().getColorAccent() ^ 0x00FFFFFF;
        ThemeEngine.setColorPrimary(savedPrimary);
        ThemeEngine.setColorAccent(savedAccent);
        onSettings(fragment -> {
            shadowOf(Looper.getMainLooper()).idle();
            assertEquals(savedPrimary,
                    startColor(fragment, "UserInterfaceSettingFragment::Tag::ColorPrimary"));
            assertEquals(savedAccent,
                    startColor(fragment, "UserInterfaceSettingFragment::Tag::ColorAccent"));
        });
    }

    private static int startColor(UserInterfaceSettingFragment fragment, String tag) {
        return fragment.getChildFragmentManager().findFragmentByTag(tag).requireArguments()
                .getInt("ColorPicker::Arguments::DefaultColor");
    }
}
