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

import android.os.Looper;
import android.util.TypedValue;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * On the wide layouts the primary panel card overlaps the lower part of the app bar, but never the
 * toolbar. A title and subtitle in Persian make the toolbar taller than the action bar, and the card
 * must still start below it, and so it must when the status bar pushes the toolbar down. A toolbar
 * no taller than the action bar leaves the card one action bar below the toolbar's top.
 */
@RunWith(RobolectricTestRunner.class)
@Config(qualifiers = "w600dp")
public class PanelCardBelowToolbarTest {

    private static final int STATUS_BAR = 100;

    @Test
    public void aToolbarNoTallerThanTheActionBarLeavesTheCardWhereItWas() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View root = addHost(activity);
                Toolbar toolbar = root.findViewById(R.id.primary_toolbar);
                // Robolectric's font metrics make even a one line title plus its margins 49px against an
                // action bar of 48, so the text goes and the toolbar keeps the action bar height.
                toolbar.setTitle(null);
                toolbar.setSubtitle(null);
                shadowOf(Looper.getMainLooper()).idle();
                View appBar = applyStatusBar(root);
                View card = root.findViewById(R.id.primary_panel_body_container_card_view);
                assertEquals(STATUS_BAR, toolbar.getTop());
                assertEquals(actionBarSize(activity), toolbar.getHeight());
                assertEquals(appBar.getTop() + toolbar.getTop() + actionBarSize(activity), card.getTop());
            });
        }
    }

    @Test
    public void aTallToolbarPushesTheCardBelowIt() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View root = addHost(activity);
                shadowOf(Looper.getMainLooper()).idle();
                View toolbar = root.findViewById(R.id.primary_toolbar);
                toolbar.setMinimumHeight(3 * actionBarSize(activity));
                toolbar.requestLayout();
                shadowOf(Looper.getMainLooper()).idle();
                View appBar = root.findViewById(R.id.primary_app_bar_container);
                View card = root.findViewById(R.id.primary_panel_body_container_card_view);
                int toolbarBottom = appBar.getTop() + toolbar.getBottom();
                assertTrue("card top " + card.getTop() + " is above the toolbar bottom " + toolbarBottom
                                + " (toolbar " + toolbar.getTop() + ".." + toolbar.getBottom() + ")",
                        card.getTop() >= toolbarBottom);
            });
        }
    }

    @Test
    public void theStatusBarPushesTheCardDownWithTheToolbar() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View root = addHost(activity);
                shadowOf(Looper.getMainLooper()).idle();
                View appBar = applyStatusBar(root);
                View toolbar = root.findViewById(R.id.primary_toolbar);
                View card = root.findViewById(R.id.primary_panel_body_container_card_view);
                assertEquals(STATUS_BAR, toolbar.getTop());
                int toolbarBottom = appBar.getTop() + toolbar.getBottom();
                assertTrue("card top " + card.getTop() + " is above the toolbar bottom " + toolbarBottom
                                + " (app bar " + appBar.getTop() + ".." + appBar.getBottom()
                                + ", toolbar " + toolbar.getTop() + ".." + toolbar.getBottom() + ")",
                        card.getTop() >= toolbarBottom);
            });
        }
    }

    /**
     * Hands the app bar a status bar, which is where the insets land on a device, and lays the
     * screen out again. Returns the app bar.
     */
    private static View applyStatusBar(View root) {
        View appBar = root.findViewById(R.id.primary_app_bar_container);
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, STATUS_BAR, 0, 0))
                .build();
        ViewCompat.dispatchApplyWindowInsets(appBar, insets);
        appBar.requestLayout();
        shadowOf(Looper.getMainLooper()).idle();
        return appBar;
    }

    private static View addHost(BackupListActivity activity) {
        MultiPanelSelectionTest.Host host = new MultiPanelSelectionTest.Host();
        activity.getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, host)
                .commitNow();
        return host.requireView();
    }

    private static int actionBarSize(BackupListActivity activity) {
        TypedValue value = new TypedValue();
        activity.getTheme().resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true);
        return TypedValue.complexToDimensionPixelSize(value.data, activity.getResources().getDisplayMetrics());
    }
}
