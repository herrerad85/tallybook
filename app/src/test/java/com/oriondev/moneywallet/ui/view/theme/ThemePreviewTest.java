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

import android.content.Context;

import androidx.core.graphics.ColorUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowBuild;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * The color a chooser is sitting on is shown without being stored, so cancelling costs nothing and
 * OK is still what writes.
 * <p>
 * Every color here is derived from what is stored when the test starts, because the engine is one
 * static holding one preferences file for the whole JVM and a test that ran earlier may have
 * written to it.
 */
@RunWith(RobolectricTestRunner.class)
public class ThemePreviewTest {

    private int mOriginalPrimary;
    private int mOriginalAccent;
    private boolean mOriginalSystemColors;

    @Before
    public void readTheStoredTheme() {
        // The getters answer with a preview while one is live, so one left behind by anything
        // else would be read here as the stored color and then written by the teardown.
        ThemeEngine.clearPreview();
        mOriginalSystemColors = ThemeEngine.isSystemColorsEnabled();
        ThemeEngine.setUseSystemColors(false);
        mOriginalPrimary = ThemeEngine.getTheme().getColorPrimary();
        mOriginalAccent = ThemeEngine.getTheme().getColorAccent();
    }

    /**
     * The dark primary comes back derived from the primary instead of whatever it held, because the
     * engine writes the pair together and nothing writes one of them alone. That is the state of any
     * install where a color has been picked, and it cannot be put back, so nothing in this class may
     * assert anything that only holds on the pair a fresh install ships with. Such an assertion
     * cannot fail once any method here has run, which is every method but the first.
     */
    @After
    public void restoreTheStoredTheme() {
        ThemeEngine.clearPreview();
        ThemeEngine.setColorPrimary(mOriginalPrimary);
        ThemeEngine.setColorAccent(mOriginalAccent);
        ThemeEngine.setUseSystemColors(mOriginalSystemColors);
    }

    @Test
    public void aPreviewIsWhatTheThemeReadsBackAndClearingItPutsTheStoredColorsBack() {
        ITheme theme = ThemeEngine.getTheme();
        int previewedPrimary = inverted(mOriginalPrimary);
        int previewedAccent = inverted(mOriginalAccent);

        ThemeEngine.previewColorPrimary(previewedPrimary);
        ThemeEngine.previewColorAccent(previewedAccent);
        assertEquals(previewedPrimary, theme.getColorPrimary());
        assertEquals(previewedAccent, theme.getColorAccent());
        assertEquals("the status bar follows the previewed primary, not the stored one",
                opaque(Util.darkenColor(previewedPrimary)), theme.getColorPrimaryDark());

        ThemeEngine.clearPreview();
        assertEquals(mOriginalPrimary, theme.getColorPrimary());
        assertEquals(mOriginalAccent, theme.getColorAccent());
    }

    /**
     * OK arrives while the preview of that same color is still up, so a write that compared what it
     * was handed against the theme would find no change and store nothing.
     */
    @Test
    public void aColorStoredWhileItsOwnPreviewIsUpSurvivesThePreviewBeingCleared() {
        ITheme theme = ThemeEngine.getTheme();
        int chosenPrimary = inverted(mOriginalPrimary);
        int chosenAccent = inverted(mOriginalAccent);

        ThemeEngine.previewColorPrimary(chosenPrimary);
        ThemeEngine.setColorPrimary(chosenPrimary);
        ThemeEngine.previewColorAccent(chosenAccent);
        ThemeEngine.setColorAccent(chosenAccent);
        ThemeEngine.clearPreview();

        assertEquals(chosenPrimary, theme.getColorPrimary());
        assertEquals(chosenAccent, theme.getColorAccent());
        assertEquals(opaque(Util.darkenColor(chosenPrimary)), theme.getColorPrimaryDark());
    }

    /**
     * The chooser opens on the color that is stored and can be walked back to it, and neither
     * is an override. Showing one would repaint every screen for a color nobody picked, and
     * move the status bar with it, because a stored dark primary is its own value and an
     * untouched one is not a darkened primary.
     */
    @Test
    public void theStoredColorIsNeverAnOverride() {
        ITheme theme = ThemeEngine.getTheme();
        int[] repaints = new int[1];
        ThemeEngine.ThemeObserver observer = ignored -> repaints[0]++;
        ThemeEngine.registerObserver(observer);
        try {
            assertFalse("opening on the stored color previewed it",
                    ThemeEngine.previewColorPrimary(mOriginalPrimary));
            assertFalse(ThemeEngine.previewColorAccent(mOriginalAccent));
            assertEquals("previewing what is already stored repainted the screen", 0, repaints[0]);

            assertTrue(ThemeEngine.previewColorPrimary(inverted(mOriginalPrimary)));
            assertEquals(1, repaints[0]);
            ThemeEngine.previewColorPrimary(inverted(mOriginalPrimary));
            assertEquals("the color that is already showing repainted again", 1, repaints[0]);

            assertFalse("walking back to the stored color left an override showing",
                    ThemeEngine.previewColorPrimary(mOriginalPrimary));
            assertEquals(mOriginalPrimary, theme.getColorPrimary());
        } finally {
            ThemeEngine.unregisterObserver(observer);
        }
    }

    /**
     * OK stores the color that is showing and the chooser then goes, so the drop is not a change
     * and repainting every screen for it is work nobody sees.
     */
    @Test
    public void droppingAPreviewThatWasJustStoredRepaintsNothing() {
        int[] repaints = new int[1];
        ThemeEngine.ThemeObserver observer = ignored -> repaints[0]++;
        ThemeEngine.previewColorPrimary(inverted(mOriginalPrimary));
        ThemeEngine.setColorPrimary(inverted(mOriginalPrimary));
        ThemeEngine.registerObserver(observer);
        try {
            ThemeEngine.clearPreview();
            assertEquals(0, repaints[0]);

            ThemeEngine.previewColorPrimary(mOriginalPrimary);
            ThemeEngine.clearPreview();
            assertEquals("a color that is not stored has to repaint on the way in and on the way out",
                    2, repaints[0]);
        } finally {
            ThemeEngine.unregisterObserver(observer);
        }
    }

    @Test
    @Config(sdk = 33)
    public void theSystemColorsAreWhatTheThemeReadsWhileTheyAreOnAndThePicksStayStored() {
        ITheme theme = ThemeEngine.getTheme();
        Context application = RuntimeEnvironment.getApplication();
        int systemPrimary = application.getColor(android.R.color.system_accent1_600);
        int systemAccent = application.getColor(android.R.color.system_accent2_600);
        ThemeEngine.setColorPrimary(inverted(systemPrimary));
        ThemeEngine.setColorAccent(inverted(systemAccent));

        ThemeEngine.setUseSystemColors(true);
        assertEquals(systemPrimary, theme.getColorPrimary());
        assertEquals(systemAccent, theme.getColorAccent());
        assertEquals(inverted(systemPrimary), ThemeEngine.getSavedColorPrimary());
        assertEquals(inverted(systemAccent), ThemeEngine.getSavedColorAccent());

        ThemeEngine.setUseSystemColors(false);
        assertEquals(inverted(systemPrimary), theme.getColorPrimary());
        assertEquals(inverted(systemAccent), theme.getColorAccent());
    }

    @Test
    @Config(sdk = 33)
    public void theSystemAccentIsToneEightyInBothDarkModes() {
        Context application = RuntimeEnvironment.getApplication();
        int lightTone = application.getColor(android.R.color.system_accent2_600);
        int darkTone = application.getColor(android.R.color.system_accent2_200);
        assertNotEquals(lightTone, darkTone);
        ThemeEngine.Mode originalMode = ThemeEngine.getTheme().getMode();
        ThemeEngine.setUseSystemColors(true);
        try {
            ThemeEngine.setMode(ThemeEngine.Mode.DARK);
            assertEquals(darkTone, ThemeEngine.getTheme().getColorAccent());
            ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
            assertEquals(darkTone, ThemeEngine.getTheme().getColorAccent());
            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
            assertEquals(lightTone, ThemeEngine.getTheme().getColorAccent());
        } finally {
            ThemeEngine.setMode(originalMode);
        }
    }

    @Test
    @Config(sdk = 33)
    public void aLivePreviewStillWinsWhileTheSystemColorsAreOn() {
        ITheme theme = ThemeEngine.getTheme();
        ThemeEngine.setUseSystemColors(true);
        int systemPrimary = theme.getColorPrimary();
        int systemAccent = theme.getColorAccent();
        ThemeEngine.setColorPrimary(systemPrimary);
        ThemeEngine.setColorAccent(systemAccent);

        ThemeEngine.previewColorPrimary(inverted(systemPrimary));
        ThemeEngine.previewColorAccent(inverted(systemAccent));
        assertEquals(inverted(systemPrimary), theme.getColorPrimary());
        assertEquals(inverted(systemAccent), theme.getColorAccent());
    }

    /** Robolectric's own maker is on the library's list of makers with dynamic color. */
    @Test
    @Config(sdk = {24, 32})
    public void theSystemColorsChangeNothingWhereDynamicColorIsUnavailable() {
        ShadowBuild.setManufacturer("acme");
        ShadowBuild.setBrand("acme");
        ThemeEngine.setUseSystemColors(true);
        assertEquals(mOriginalPrimary, ThemeEngine.getTheme().getColorPrimary());
        assertEquals(mOriginalAccent, ThemeEngine.getTheme().getColorAccent());
    }

    /** Opaque, and never the color it was handed. */
    private int inverted(int color) {
        return color ^ 0x00FFFFFF;
    }

    private int opaque(int color) {
        return ColorUtils.setAlphaComponent(color, 255);
    }
}
