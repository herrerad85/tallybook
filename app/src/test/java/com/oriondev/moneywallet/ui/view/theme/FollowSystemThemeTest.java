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

package com.oriondev.moneywallet.ui.view.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class FollowSystemThemeTest {

    private static final String MODE = "mode";

    private Context mEngineContext;
    private SharedPreferences mEnginePreferences;
    private boolean mOriginalStored;
    private int mOriginalMode;
    private int mOriginalNight;

    private int mNotified;

    private final ThemeEngine.ThemeObserver mObserver = new ThemeEngine.ThemeObserver() {

        @Override
        public void onThemeChanged(ITheme theme) {
            mNotified++;
        }

    };

    @Before
    public void saveTheStoredModeAndTheNightMode() {
        ITheme theme = ThemeEngine.getTheme();
        mEngineContext = ReflectionHelpers.getField(theme, "mContext");
        mEnginePreferences = ReflectionHelpers.getField(theme, "mPreferences");
        mOriginalStored = mEnginePreferences.contains(MODE);
        mOriginalMode = mEnginePreferences.getInt(MODE, 0);
        mOriginalNight = mEngineContext.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK;
    }

    @After
    public void restoreTheStoredModeAndTheNightMode() {
        ThemeEngine.unregisterObserver(mObserver);
        if (mOriginalStored) {
            mEnginePreferences.edit().putInt(MODE, mOriginalMode).apply();
        } else {
            mEnginePreferences.edit().remove(MODE).apply();
        }
        setNight(mOriginalNight);
    }

    private void store(int mode) {
        mEnginePreferences.edit().putInt(MODE, mode).apply();
    }

    private void setNight(int night) {
        Resources resources = mEngineContext.getResources();
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK) | night;
        resources.updateConfiguration(configuration, resources.getDisplayMetrics());
        assertEquals("the night mode did not reach the configuration the engine reads", night,
                mEngineContext.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
    }

    private static ThemeEngine.Mode mode() {
        return ThemeEngine.getTheme().getMode();
    }

    @Test
    public void eachStoredModeGivesItsModeWhateverTheSystemNightMode() {
        int[] nights = {Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES};
        for (int night : nights) {
            setNight(night);
            store(0);
            assertEquals(ThemeEngine.Mode.LIGHT, mode());
            store(1);
            assertEquals(ThemeEngine.Mode.DARK, mode());
            store(2);
            assertEquals(ThemeEngine.Mode.DEEP_DARK, mode());
            assertFalse(ThemeEngine.isFollowingSystem());
        }
    }

    @Test
    public void followSystemGivesDarkWhileTheSystemIsInNightMode() {
        store(3);
        setNight(Configuration.UI_MODE_NIGHT_YES);
        assertTrue(ThemeEngine.isFollowingSystem());
        assertEquals(ThemeEngine.Mode.DARK, mode());
        assertTrue(ThemeEngine.getTheme().isDark());
    }

    @Test
    public void followSystemGivesLightWhileTheSystemIsNotInNightMode() {
        store(3);
        setNight(Configuration.UI_MODE_NIGHT_NO);
        assertTrue(ThemeEngine.isFollowingSystem());
        assertEquals(ThemeEngine.Mode.LIGHT, mode());
    }

    @Test
    public void nothingStoredGivesLightEvenInNightMode() {
        mEnginePreferences.edit().remove(MODE).apply();
        setNight(Configuration.UI_MODE_NIGHT_YES);
        assertEquals(ThemeEngine.Mode.LIGHT, mode());
        assertFalse(ThemeEngine.isFollowingSystem());
    }

    @Test
    public void anUnknownStoredValueGivesLight() {
        setNight(Configuration.UI_MODE_NIGHT_YES);
        store(7);
        assertEquals(ThemeEngine.Mode.LIGHT, mode());
        store(-1);
        assertEquals(ThemeEngine.Mode.LIGHT, mode());
        assertFalse(ThemeEngine.isFollowingSystem());
    }

    @Test
    public void choosingDarkWhileFollowingASystemInNightModeStoresDark() {
        store(3);
        setNight(Configuration.UI_MODE_NIGHT_YES);
        assertEquals(ThemeEngine.Mode.DARK, mode());

        ThemeEngine.setMode(ThemeEngine.Mode.DARK);
        assertFalse(ThemeEngine.isFollowingSystem());
        assertEquals(1, mEnginePreferences.getInt(MODE, 0));

        setNight(Configuration.UI_MODE_NIGHT_NO);
        assertEquals(ThemeEngine.Mode.DARK, mode());
    }

    @Test
    public void choosingLightWhileFollowingASystemNotInNightModeStoresLight() {
        store(3);
        setNight(Configuration.UI_MODE_NIGHT_NO);
        assertEquals(ThemeEngine.Mode.LIGHT, mode());

        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
        assertFalse(ThemeEngine.isFollowingSystem());
        assertEquals(0, mEnginePreferences.getInt(MODE, 3));

        setNight(Configuration.UI_MODE_NIGHT_YES);
        assertEquals(ThemeEngine.Mode.LIGHT, mode());
    }

    @Test
    public void choosingTheStoredModeAgainNotifiesNothing() {
        store(1);
        ThemeEngine.registerObserver(mObserver);
        ThemeEngine.setMode(ThemeEngine.Mode.DARK);
        assertEquals(0, mNotified);
        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
        assertEquals(1, mNotified);
        assertEquals(2, mEnginePreferences.getInt(MODE, 0));
    }

    @Test
    public void followSystemStoresItAndNotifiesWhenItChangesTheStoredValue() {
        store(0);
        ThemeEngine.registerObserver(mObserver);
        ThemeEngine.setFollowSystem();
        assertEquals(1, mNotified);
        assertEquals(3, mEnginePreferences.getInt(MODE, 0));
        assertTrue(ThemeEngine.isFollowingSystem());
    }

    @Test
    public void followSystemNotifiesNothingWhenItIsAlreadyStored() {
        store(3);
        ThemeEngine.registerObserver(mObserver);
        ThemeEngine.setFollowSystem();
        assertEquals(0, mNotified);
        assertTrue(ThemeEngine.isFollowingSystem());
    }
}
