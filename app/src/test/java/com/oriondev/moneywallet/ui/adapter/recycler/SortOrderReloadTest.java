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

import android.database.MatrixCursor;

import com.oriondev.moneywallet.storage.database.Contract;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;

/**
 * Coming back to a sort screen hands the adapter the cursor it already holds, and the order the
 * user has not saved yet has to survive that.
 */
@RunWith(RobolectricTestRunner.class)
public class SortOrderReloadTest {

    @Test
    public void walletOrderSurvivesTheSameCursorAgain() {
        WalletSortCursorAdapter adapter = new WalletSortCursorAdapter(null);
        MatrixCursor cursor = rows(Contract.Wallet.ID, Contract.Wallet.ICON, Contract.Wallet.NAME);
        adapter.changeCursor(cursor);
        adapter.moveItem(0, 1);
        adapter.changeCursor(cursor);
        assertEquals(Arrays.asList(2L, 1L, 3L), adapter.getSortedCategoryIds());
        adapter.changeCursor(rows(Contract.Wallet.ID, Contract.Wallet.ICON, Contract.Wallet.NAME));
        assertEquals(Arrays.asList(1L, 2L, 3L), adapter.getSortedCategoryIds());
    }

    @Test
    public void categoryOrderSurvivesTheSameCursorAgain() {
        CategorySortCursorAdapter adapter = new CategorySortCursorAdapter(null);
        MatrixCursor cursor = rows(Contract.Category.ID, Contract.Category.ICON,
                Contract.Category.NAME);
        adapter.changeCursor(cursor);
        adapter.moveItem(0, 1);
        adapter.changeCursor(cursor);
        assertEquals(Arrays.asList(2L, 1L, 3L), adapter.getSortedCategoryIds());
        adapter.changeCursor(rows(Contract.Category.ID, Contract.Category.ICON,
                Contract.Category.NAME));
        assertEquals(Arrays.asList(1L, 2L, 3L), adapter.getSortedCategoryIds());
    }

    @Test
    public void ruleOrderSurvivesTheSameCursorAgain() {
        CategoryRuleSortCursorAdapter adapter = new CategoryRuleSortCursorAdapter(null);
        MatrixCursor cursor = rows(Contract.CategoryRule.ID, Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_NAME);
        adapter.changeCursor(cursor);
        adapter.moveItem(0, 1);
        adapter.changeCursor(cursor);
        assertEquals(Arrays.asList(2L, 1L, 3L), adapter.getSortedCategoryRuleIds());
        adapter.changeCursor(rows(Contract.CategoryRule.ID, Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_NAME));
        assertEquals(Arrays.asList(1L, 2L, 3L), adapter.getSortedCategoryRuleIds());
    }

    private static MatrixCursor rows(String id, String second, String third) {
        MatrixCursor cursor = new MatrixCursor(new String[] {id, second, third});
        cursor.addRow(new Object[] {1L, null, "a"});
        cursor.addRow(new Object[] {2L, null, "b"});
        cursor.addRow(new Object[] {3L, null, "c"});
        return cursor;
    }
}
