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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A detail panel closes itself through navigateBackSafely after every item delete. That closes
 * the panel and nothing else, while back still ends the selection. On the phone layout the panel
 * is already hidden here, as after a back press made before the delete returned.
 */
@RunWith(RobolectricTestRunner.class)
public class MultiPanelSelectionTest {

    @Test
    public void aPanelClosingItselfKeepsTheSelectionAndBackStillEndsIt() {
        checkThePanelCloseKeepsTheSelection();
    }

    @Test
    @Config(qualifiers = "w840dp")
    public void onTheTwoPanelLayoutAPanelClosingItselfKeepsTheSelectionAndBackStillEndsIt() {
        checkThePanelCloseKeepsTheSelection();
    }

    private static void checkThePanelCloseKeepsTheSelection() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                Host host = new Host();
                activity.getSupportFragmentManager().beginTransaction()
                        .add(android.R.id.content, host)
                        .commitNow();
                Panel panel = new Panel();
                host.getChildFragmentManager().beginTransaction()
                        .add(R.id.secondary_panel_container, panel)
                        .commitNow();
                SelectionForwarder forwarder = new SelectionForwarder();
                TransactionCursorAdapter adapter = new TransactionCursorAdapter(forwarder);
                forwarder.mSelectionMode = new TransactionSelectionMode(host, host, adapter);
                adapter.setSelectedIds(new long[] {1L});
                String selectedTitle = host.getString(R.string.title_transactions_selected, 1);
                assertEquals(selectedTitle, String.valueOf(host.getPrimaryToolbar().getTitle()));
                panel.navigateBackSafely();
                assertEquals(1, adapter.getSelectedIds().length);
                assertEquals(selectedTitle, String.valueOf(host.getPrimaryToolbar().getTitle()));
                assertTrue(host.navigateBack());
                assertEquals(0, adapter.getSelectedIds().length);
                assertFalse(host.navigateBack());
            });
        }
    }

    public static class Host extends MultiPanelFragment {

        @Override
        protected void onCreatePrimaryPanel(LayoutInflater inflater, @NonNull ViewGroup primaryPanel, @Nullable Bundle savedInstanceState) {
            // nothing drawn
        }

        @Override
        protected void onCreateSecondaryPanel(LayoutInflater inflater, @NonNull ViewGroup secondaryPanel, @Nullable Bundle savedInstanceState) {
            // the case adds its panel itself
        }

        @Override
        protected int getTitleRes() {
            return R.string.app_name;
        }
    }

    public static class Panel extends SecondaryPanelFragment {

        @Override
        protected void onCreateHeaderView(LayoutInflater inflater, @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
            // nothing drawn
        }

        @Override
        protected void onCreateBodyView(LayoutInflater inflater, @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
            // nothing drawn
        }

        @Override
        protected String getTitle() {
            return "";
        }

        @Override
        protected int onInflateMenu() {
            return 0;
        }

        @Override
        public boolean onMenuItemClick(MenuItem item) {
            return false;
        }

        @Override
        protected void onShowItemId(long itemId) {
            // nothing drawn
        }
    }

    private static class SelectionForwarder implements TransactionCursorAdapter.ActionListener {

        private TransactionSelectionMode mSelectionMode;

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
            // never called here
        }

        @Override
        public void onTransactionClick(long id) {
            // never called here
        }

        @Override
        public void onSelectionChanged(int count) {
            mSelectionMode.onSelectionChanged(count);
        }
    }
}
