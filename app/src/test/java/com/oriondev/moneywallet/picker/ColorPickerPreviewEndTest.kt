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

package com.oriondev.moneywallet.picker

import androidx.appcompat.app.AppCompatActivity

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * Ending a preview is not per picker, it drops whatever is being shown, so a picker whose chooser
 * showed nothing has to stay out of it. Income color and expense color are two of those, and their
 * chooser closing must not take the theme preview with it.
 */
@RunWith(RobolectricTestRunner::class)
class ColorPickerPreviewEndTest {

    companion object {

        private const val THEME_TAG = "theme"
        private const val PLAIN_TAG = "plain"
    }

    @Test
    fun aChooserThatShowedNothingEndsNothing() {
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val recorder = RecordingController()
            val theme = ColorPicker.createPicker(
                    controller.get().supportFragmentManager, THEME_TAG, 0xFF3F51B5.toInt(), false, recorder)
            val plain = ColorPicker.createPicker(
                    controller.get().supportFragmentManager, PLAIN_TAG, 0xFF2196F3.toInt(), false, recorder)

            assertTrue(theme.onColorPreview(null, 0xFF009688.toInt()))
            plain.onColorPreview(null, 0xFF4CAF50.toInt())
            plain.onColorChooserDismissed(null)
            assertEquals("the chooser that showed nothing ended the one that did", 0, recorder.mEnded)

            theme.onColorChooserDismissed(null)
            assertEquals(1, recorder.mEnded)
        } finally {
            controller.close()
        }
    }

    /** Answers the way the settings screen does, where only the theme colors reach the engine. */
    private class RecordingController : ColorPicker.Controller {

        var mEnded: Int = 0

        override fun onColorChanged(tag: String?, color: Int, autoFired: Boolean) {}

        override fun onColorPreview(tag: String?, color: Int): Boolean {
            return THEME_TAG == tag
        }

        override fun onColorPreviewEnded() {
            mEnded++
        }
    }
}
