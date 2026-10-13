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
import android.widget.TextView;

import androidx.core.view.ViewCompat;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Each sort screen holds items numbered from 1 in that order, in a list attached to an activity so that
 * RecyclerView puts its own item delegate on the rows whenever accessibility is on.
 */
@RunWith(RobolectricTestRunner.class)
public class ReorderAccessibilityDelegateTest {

    private static final List<Integer> ALL_MOVES = Arrays.asList(R.id.action_move_to_top,
            R.id.action_move_up, R.id.action_move_down, R.id.action_move_to_bottom);

    private ActivityController<Activity> mController;
    private Context mContext;
    private ShadowAccessibilityManager mAccessibility;
    private RecyclerView mRecyclerView;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(Activity.class).setup();
        mContext = new ContextThemeWrapper(mController.get(), R.style.MoneyWalletAppTheme);
        mAccessibility = shadowOf(mContext.getSystemService(AccessibilityManager.class));
        mRecyclerView = new RecyclerView(mContext);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(mContext));
    }

    @After
    public void tearDown() {
        mController.close();
    }

    @Test
    public void eachRowOffersOnlyTheMovesItCanMakeAndKeepsItsPosition() {
        mAccessibility.setEnabled(true);
        attachRules(4);
        assertEquals(Arrays.asList(R.id.action_move_down, R.id.action_move_to_bottom), movesOn(rowAt(0)));
        assertEquals(ALL_MOVES, movesOn(rowAt(1)));
        assertEquals(Arrays.asList(R.id.action_move_to_top, R.id.action_move_up), movesOn(rowAt(3)));
        AccessibilityNodeInfo info = rowAt(2).createAccessibilityNodeInfo();
        assertNotNull(info.getCollectionItemInfo());
        assertEquals(2, info.getCollectionItemInfo().getRowIndex());
        assertTrue(rowAt(2).isFocusable());
    }

    @Test
    public void everyMoveSavesTheOrderOnScreen() {
        mAccessibility.setEnabled(true);
        CategoryRuleSortCursorAdapter adapter = attachRules(4);
        move(1, R.id.action_move_up);
        assertEquals(Arrays.asList(2L, 1L, 3L, 4L), adapter.getSortedCategoryRuleIds());
        move(2, R.id.action_move_down);
        assertEquals(Arrays.asList(2L, 1L, 4L, 3L), adapter.getSortedCategoryRuleIds());
        move(3, R.id.action_move_to_top);
        assertEquals(Arrays.asList(3L, 2L, 1L, 4L), adapter.getSortedCategoryRuleIds());
        move(0, R.id.action_move_to_bottom);
        assertEquals(Arrays.asList(2L, 1L, 4L, 3L), adapter.getSortedCategoryRuleIds());
        for (int position = 0; position < 4; position++) {
            TextView pattern = rowAt(position).findViewById(R.id.primary_text_view);
            assertEquals("p" + adapter.getSortedCategoryRuleIds().get(position), pattern.getText().toString());
        }
    }

    /**
     * A row bound while accessibility was off loses its delegate on the way through the pool.
     * Turning a screen reader on afterwards must not cost it the moves.
     */
    @Test
    public void rowBoundWithAccessibilityOffKeepsItsMoves() {
        mAccessibility.setEnabled(false);
        CategoryRuleSortCursorAdapter adapter = attachRules(4);
        mAccessibility.setEnabled(true);
        adapter.notifyDataSetChanged();
        idle();
        assertEquals(ALL_MOVES, movesOn(rowAt(1)));
    }

    @Test
    public void aMovedRowStaysOnScreenInAListLongerThanTheScreen() {
        mAccessibility.setEnabled(true);
        attachRules(30);
        move(2, R.id.action_move_to_bottom);
        assertEquals("p3", patternAt(29));
        move(29, R.id.action_move_to_top);
        assertEquals("p3", patternAt(0));
        move(1, R.id.action_move_up);
        assertEquals("p1", patternAt(0));
    }

    @Test
    public void aRowKeepsTheActionsEveryViewHas() {
        mAccessibility.setEnabled(true);
        attachRules(4);
        assertTrue(rowAt(1).createAccessibilityNodeInfo().isFocusable());
        assertTrue(rowAt(1).performAccessibilityAction(AccessibilityNodeInfo.ACTION_FOCUS, null));
        assertTrue(rowAt(1).isFocused());
    }

    @Test
    public void aRowOutsideTheListOffersAndMakesNoMove() {
        List<Integer> moved = new ArrayList<>();
        View row = new View(mContext);
        ViewCompat.setAccessibilityDelegate(row, new ReorderAccessibilityDelegate((from, to) -> {
            moved.add(from);
            return true;
        }));
        assertEquals(new ArrayList<Integer>(), movesOn(row));
        for (int action : ALL_MOVES) {
            assertFalse(row.performAccessibilityAction(action, null));
        }
        assertEquals(new ArrayList<Integer>(), moved);
    }

    @Test
    public void walletAndCategoryScreensMoveTheRow() {
        mAccessibility.setEnabled(true);
        MatrixCursor walletCursor = new MatrixCursor(new String[] {
                Contract.Wallet.ID, Contract.Wallet.NAME, Contract.Wallet.ICON});
        MatrixCursor categoryCursor = new MatrixCursor(new String[] {
                Contract.Category.ID, Contract.Category.ICON, Contract.Category.NAME});
        for (long id = 1; id <= 4; id++) {
            walletCursor.addRow(new Object[] {id, "w" + id, null});
            categoryCursor.addRow(new Object[] {id, null, "c" + id});
        }
        WalletSortCursorAdapter wallets = new WalletSortCursorAdapter(null);
        wallets.swapCursor(walletCursor);
        attach(wallets);
        move(3, R.id.action_move_to_top);
        assertEquals(Arrays.asList(4L, 1L, 2L, 3L), wallets.getSortedCategoryIds());
        CategorySortCursorAdapter categories = new CategorySortCursorAdapter(null);
        categories.swapCursor(categoryCursor);
        attach(categories);
        move(3, R.id.action_move_to_top);
        assertEquals(Arrays.asList(4L, 1L, 2L, 3L), categories.getSortedCategoryIds());
    }

    private CategoryRuleSortCursorAdapter attachRules(int count) {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                Contract.CategoryRule.ID, Contract.CategoryRule.PATTERN, Contract.CategoryRule.CATEGORY_NAME});
        for (long id = 1; id <= count; id++) {
            cursor.addRow(new Object[] {id, "p" + id, "Category"});
        }
        CategoryRuleSortCursorAdapter adapter = new CategoryRuleSortCursorAdapter(null);
        adapter.swapCursor(cursor);
        attach(adapter);
        return adapter;
    }

    private void attach(RecyclerView.Adapter<?> adapter) {
        mRecyclerView.setAdapter(adapter);
        if (mRecyclerView.getParent() == null) {
            mController.get().setContentView(mRecyclerView);
        }
        idle();
    }

    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private View rowAt(int position) {
        View row = mRecyclerView.getLayoutManager().findViewByPosition(position);
        assertNotNull("nothing was laid out at position " + position, row);
        return row;
    }

    private String patternAt(int position) {
        TextView pattern = rowAt(position).findViewById(R.id.primary_text_view);
        return pattern.getText().toString();
    }

    private void move(int position, int action) {
        assertTrue(rowAt(position).performAccessibilityAction(action, null));
        idle();
    }

    private static List<Integer> movesOn(View row) {
        List<Integer> moves = new ArrayList<>();
        for (AccessibilityNodeInfo.AccessibilityAction action : row.createAccessibilityNodeInfo().getActionList()) {
            if (ALL_MOVES.contains(action.getId())) {
                moves.add(action.getId());
            }
        }
        return moves;
    }
}
