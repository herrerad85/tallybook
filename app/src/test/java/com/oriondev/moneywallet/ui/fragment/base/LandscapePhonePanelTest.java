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

package com.oriondev.moneywallet.ui.fragment.base;

import android.view.View;

import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A phone on its side is wider than 600dp, and wider than 840dp on a large one, but shorter than
 * 480dp. It gets the full width panel of a phone held upright, not the card or the two panels.
 */
@RunWith(RobolectricTestRunner.class)
public class LandscapePhonePanelTest {

    @Test
    @Config(qualifiers = "w832dp-h384dp-land")
    public void aLandscapePhoneGetsNoCard() {
        checkFullWidthPanel();
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    public void aLargeLandscapePhoneGetsOnePanel() {
        checkFullWidthPanel();
    }

    private static void checkFullWidthPanel() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                MultiPanelSelectionTest.Host host = new MultiPanelSelectionTest.Host();
                activity.getSupportFragmentManager().beginTransaction()
                        .add(android.R.id.content, host)
                        .commitNow();
                View root = host.requireView();
                assertFalse(host.isExtendedLayout());
                assertNotNull(root.findViewById(R.id.primary_panel_body_container_frame_layout));
                assertNull(root.findViewById(R.id.primary_panel_body_container_card_view));
                assertTrue(activity.getResources().getBoolean(R.bool.panel_fills_window));
                assertTrue(activity.getResources().getBoolean(R.bool.secondary_panel_fills_window));
            });
        }
    }
}
