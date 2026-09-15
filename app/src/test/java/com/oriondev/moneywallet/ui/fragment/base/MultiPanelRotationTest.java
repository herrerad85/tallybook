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

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.utils.Utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A detail panel emptied on the two panel layout must not come back full screen after rotating to one panel.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w840dp")
public class MultiPanelRotationTest {

    private static final String HOST_TAG = "host";

    @Test
    public void anEmptiedPanelIsNotRestoredFullScreen() {
        checkRotation(true, false, View.VISIBLE, View.GONE);
    }

    @Test
    public void aPanelShowingAnItemIsRestoredFullScreen() {
        checkRotation(false, false, View.GONE, View.VISIBLE);
    }

    @Test
    public void aRestoredPanelWhoseItemIsGoneCloses() {
        checkRotation(false, true, View.VISIBLE, View.GONE);
    }

    private static void checkRotation(boolean emptyThePanel, boolean itemGoneAfterRestore, int primaryVisibility, int secondaryVisibility) {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                MultiPanelSelectionTest.Host host = new MultiPanelSelectionTest.Host();
                activity.getSupportFragmentManager().beginTransaction()
                        .add(android.R.id.content, host, HOST_TAG)
                        .commitNow();
                MultiPanelSelectionTest.Panel panel = new MultiPanelSelectionTest.Panel();
                host.getChildFragmentManager().beginTransaction()
                        .add(R.id.secondary_panel_container, panel)
                        .commitNow();
                assertTrue(host.isExtendedLayout());
                host.showSecondaryPanel();
                panel.showItemId(5L);
                if (emptyThePanel) {
                    panel.showItemId(0L);
                }
            });
            RuntimeEnvironment.setQualifiers("w400dp");
            scenario.recreate();
            scenario.onActivity(activity -> {
                MultiPanelSelectionTest.Host host = (MultiPanelSelectionTest.Host) activity.getSupportFragmentManager().findFragmentByTag(HOST_TAG);
                assertFalse(host.isExtendedLayout());
                if (itemGoneAfterRestore) {
                    // what an item fragment's loader does when the restored id has no row
                    for (Fragment fragment : host.getChildFragmentManager().getFragments()) {
                        ((SecondaryPanelFragment) fragment).showItemId(0L);
                    }
                }
                View secondaryPanel = Utils.findViewGroupByIds(host.requireView(),
                        R.id.secondary_panel_frame_layout,
                        R.id.secondary_panel_card_view
                );
                assertEquals(primaryVisibility, host.getPrimaryPanel().getVisibility());
                assertEquals(secondaryVisibility, secondaryPanel.getVisibility());
            });
        }
    }
}
