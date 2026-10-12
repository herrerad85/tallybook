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

package com.oriondev.moneywallet.ui.adapter.recycler;

import android.app.Activity;
import android.content.Context;
import android.database.MatrixCursor;
import android.os.Looper;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAccessibilityManager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class PersonRowAccessibilityTest {

    private ActivityController<Activity> mController;
    private Context mContext;
    private ShadowAccessibilityManager mAccessibility;
    private PersonCursorAdapter mAdapter;
    private RecyclerView mRecyclerView;
    private long mLongClickedId = -1;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(Activity.class).setup();
        mContext = new ContextThemeWrapper(mController.get(), R.style.MoneyWalletAppTheme);
        mAccessibility = shadowOf(mContext.getSystemService(AccessibilityManager.class));
        MatrixCursor cursor = new MatrixCursor(new String[] {
                Contract.Person.ID, Contract.Person.NAME, Contract.Person.ICON});
        cursor.addRow(new Object[] {1L, "First", null});
        cursor.addRow(new Object[] {2L, "Second", null});
        cursor.addRow(new Object[] {3L, "Third", null});
        mAdapter = new PersonCursorAdapter(new PersonCursorAdapter.ActionListener() {

            @Override
            public void onPersonClick(long id) {}

            @Override
            public void onPersonLongClick(long id) {
                mLongClickedId = id;
            }
        });
        mAdapter.changeCursor(cursor);
        mRecyclerView = new RecyclerView(mContext);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
    }

    @After
    public void tearDown() {
        mController.close();
    }

    /**
     * With a screen reader on, RecyclerView puts its own item delegate on each bound row. The row
     * must still report its position, stay clickable and carry one long press, the named one, and
     * performing that long press must reach the listener instead of looping between delegates.
     */
    @Test
    public void rowBoundWithAccessibilityOnHasPositionAndNamedLongPress() {
        mAccessibility.setEnabled(true);
        attach();

        View row = rowAt(1);
        AccessibilityNodeInfo info = row.createAccessibilityNodeInfo();
        assertNotNull(info.getCollectionItemInfo());
        assertEquals(1, info.getCollectionItemInfo().getRowIndex());
        assertTrue(info.isClickable());
        assertEquals(mContext.getString(R.string.action_open_person_details), longPressLabel(info));

        assertTrue(row.performAccessibilityAction(AccessibilityNodeInfo.ACTION_LONG_CLICK, null));
        assertEquals(2L, mLongClickedId);
    }

    /**
     * A row bound while accessibility was off gets its delegate replaced on the way through the
     * pool. Turning a screen reader on afterwards must not cost it the long press label.
     */
    @Test
    public void rowBoundWithAccessibilityOffKeepsNamedLongPress() {
        mAccessibility.setEnabled(false);
        attach();
        mAccessibility.setEnabled(true);
        mAdapter.notifyDataSetChanged();
        idle();

        AccessibilityNodeInfo info = rowAt(1).createAccessibilityNodeInfo();
        assertEquals(mContext.getString(R.string.action_open_person_details), longPressLabel(info));
    }

    private void attach() {
        mRecyclerView.setAdapter(mAdapter);
        mController.get().setContentView(mRecyclerView);
        idle();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private View rowAt(int position) {
        View row = mRecyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull(row);
        return row;
    }

    private static String longPressLabel(AccessibilityNodeInfo info) {
        String label = null;
        for (AccessibilityNodeInfo.AccessibilityAction action : info.getActionList()) {
            if (action.getId() == AccessibilityNodeInfo.ACTION_LONG_CLICK) {
                label = String.valueOf(action.getLabel());
            }
        }
        return label;
    }
}
