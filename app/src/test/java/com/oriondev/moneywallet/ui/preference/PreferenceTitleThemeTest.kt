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

package com.oriondev.moneywallet.ui.preference

import android.widget.FrameLayout
import android.widget.TextView

import androidx.appcompat.app.AppCompatActivity
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceViewHolder

import com.oriondev.moneywallet.ui.view.theme.ThemeEngine

import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull

/**
 * A settings row keeps the current title color when its value changes.
 * <p>
 * The library copies the title's colors as the row is built and puts that copy back on every
 * bind, so a row built under one theme and rebound under another used to lose its title to the
 * theme it was built with. Deep dark made that unreadable, because the copy was black and the
 * window behind it was too.
 */
@RunWith(RobolectricTestRunner::class)
class PreferenceTitleThemeTest {

    private lateinit var mAdapter: PreferenceGroupAdapter
    private var mTitleColorAsBuilt: Int = 0

    /** The theme lives in shared preferences the engine holds open, so it outlives the test. */
    @After
    fun restoreTheDefaultMode() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
    }

    /**
     * Every row that draws its title in the primary text color. The theme type row is a
     * ThemedListPreference and it rebinds inside the same tap that changes the mode, so it is the
     * one that has to survive.
     */
    @Test
    fun aRowReboundAfterAModeChangeKeepsTheNewModesTitleColor() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val activity = controller.get()
            assertAModeChangeSurvivesARebind(activity, ThemedPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ColorPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ThemedListPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ThemedInputPreference(activity))
            assertAModeChangeSurvivesARebind(activity, ThemedSwitchPreference(activity))
        } finally {
            controller.close()
        }
    }

    /**
     * A section header draws its title in the accent, so the accent itself is what has to change
     * for the header to be pinned at all. The engine keeps an accent only where it clears a
     * contrast floor against the window and the card, and both of those are per mode, so the
     * color here holds up in all three modes and the assertion reads the accent back instead of
     * trusting it landed.
     */
    @Test
    fun aCategoryReboundAfterAnAccentChangeKeepsTheNewAccent() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val originalAccent = ThemeEngine.getTheme().colorAccent
        try {
            val activity = controller.get()
            ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
            val holder = buildRow(activity, ThemedPreferenceCategory(activity))
            val header = titleOf(holder)

            ThemeEngine.setColorAccent(0xFF009688.toInt())
            ThemeEngine.applyTheme(holder.itemView, true)
            val after = header.currentTextColor
            assertEquals("the contrast floor dropped the accent before it reached the header",
                    ThemeEngine.getTheme().colorAccent, after)
            assertNotEquals("the header was built in the color the rebind has to keep, so a rebind"
                    + " proves nothing", mTitleColorAsBuilt, after)

            mAdapter.onBindViewHolder(holder, 0)
            assertEquals("the rebind put back the accent the header was built with",
                    after, header.currentTextColor)
        } finally {
            ThemeEngine.setColorAccent(originalAccent)
            controller.close()
        }
    }

    private fun assertAModeChangeSurvivesARebind(activity: AppCompatActivity, preference: Preference) {
        val name = preference.javaClass.simpleName
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT)
        val holder = buildRow(activity, preference)
        val title = titleOf(holder)
        val light = ThemeEngine.getTheme().textColorPrimary
        assertEquals(name + " did not start in the light mode color",
                light, title.currentTextColor)

        ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK)
        ThemeEngine.applyTheme(holder.itemView, true)
        val deepDark = ThemeEngine.getTheme().textColorPrimary
        assertEquals(name + " was not repainted for deep dark",
                deepDark, title.currentTextColor)
        assertNotEquals(name + " was built in the color the rebind has to keep, so a rebind proves"
                + " nothing", mTitleColorAsBuilt, deepDark)

        mAdapter.onBindViewHolder(holder, 0)
        assertEquals(name + " lost its title to the color it was built with",
                deepDark, title.currentTextColor)
    }

    /**
     * Builds one row through the library, the way a settings screen does, and paints it for the
     * theme in force. Nothing themes a view in a unit test, so the row is painted here by hand,
     * after the holder has already copied the platform's own color. On a device the inflater
     * paints first and the copy is the engine's, which is a different value and the same defect.
     * <p>
     * The color the row is built in is recorded, because that is what a rebind restores, and a
     * test that expects the rebind to land on that same color would pass with the fix deleted.
     */
    private fun buildRow(activity: AppCompatActivity, preference: Preference): PreferenceViewHolder {
        preference.title = "Income color"
        val manager = PreferenceManager(activity)
        val screen = manager.createPreferenceScreen(activity)
        screen.addPreference(preference)
        mAdapter = PreferenceGroupAdapter(screen)
        val holder = mAdapter.onCreateViewHolder(
                FrameLayout(activity), mAdapter.getItemViewType(0))
        mTitleColorAsBuilt = titleOf(holder).currentTextColor
        ThemeEngine.applyTheme(holder.itemView, true)
        return holder
    }

    private fun titleOf(holder: PreferenceViewHolder): TextView {
        val title = holder.findViewById(android.R.id.title) as TextView?
        assertNotNull("the row has no title view", title)
        return title!!
    }
}
