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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;

import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.ui.activity.TransactionListActivity;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.robolectric.Shadows.shadowOf;

/**
 * The transaction list of a budget or a category, opened through the real activity.
 */
@RunWith(RobolectricTestRunner.class)
public class BudgetTransactionListTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    @Test
    public void aBudgetOverTwoWalletsListsTheRowsOfBothWhileOneIsSelected() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        long category = insertCategory(resolver);
        long walletA = insertWallet(resolver, "A");
        long walletB = insertWallet(resolver, "B");
        long walletC = insertWallet(resolver, "C");
        long expenseA = insertExpense(resolver, walletA, category);
        long expenseB = insertExpense(resolver, walletB, category);
        insertExpense(resolver, walletC, category);
        long budgetId = insertBudget(resolver, Contract.BudgetType.EXPENSES, null, walletA, walletB);
        PreferenceManager.setCurrentWallet(context, walletA);
        Intent intent = new Intent(context, TransactionListActivity.class);
        intent.putExtra(TransactionListActivity.BUDGET_ID, budgetId);
        Set<Long> expected = new HashSet<>();
        expected.add(expenseA);
        expected.add(expenseB);
        assertEquals(expected, listedTransactionIds(intent));
    }

    @Test
    public void aCategoryBudgetOverTwoWalletsListsTheRowsOfBothWhileOneIsSelected() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        long category = insertCategory(resolver);
        long walletA = insertWallet(resolver, "A");
        long walletB = insertWallet(resolver, "B");
        long walletC = insertWallet(resolver, "C");
        long expenseA = insertExpense(resolver, walletA, category);
        long expenseB = insertExpense(resolver, walletB, category);
        insertExpense(resolver, walletC, category);
        long budgetId = insertBudget(resolver, Contract.BudgetType.CATEGORY, "<" + category + ">", walletA, walletB);
        PreferenceManager.setCurrentWallet(context, walletA);
        Intent intent = new Intent(context, TransactionListActivity.class);
        intent.putExtra(TransactionListActivity.BUDGET_ID, budgetId);
        Set<Long> expected = new HashSet<>();
        expected.add(expenseA);
        expected.add(expenseB);
        assertEquals(expected, listedTransactionIds(intent));
    }

    @Test
    public void aCategoryListsOnlyTheRowsOfTheSelectedWallet() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        long category = insertCategory(resolver);
        long walletA = insertWallet(resolver, "A");
        long walletB = insertWallet(resolver, "B");
        long expenseA = insertExpense(resolver, walletA, category);
        insertExpense(resolver, walletB, category);
        PreferenceManager.setCurrentWallet(context, walletA);
        Intent intent = new Intent(context, TransactionListActivity.class);
        intent.putExtra(TransactionListActivity.CATEGORY_ID, category);
        Set<Long> expected = new HashSet<>();
        expected.add(expenseA);
        assertEquals(expected, listedTransactionIds(intent));
    }

    /**
     * Launches the activity, waits until its loader has delivered, and returns the ids of the
     * transaction rows it lists, leaving out the date headers.
     */
    private static Set<Long> listedTransactionIds(Intent intent) {
        Set<Long> listed = new HashSet<>();
        try (ActivityScenario<TransactionListActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                TransactionMultiPanelFragment fragment = null;
                for (Fragment candidate : activity.getSupportFragmentManager().getFragments()) {
                    if (candidate instanceof TransactionMultiPanelFragment) {
                        fragment = (TransactionMultiPanelFragment) candidate;
                    }
                }
                LoaderManager loaders = LoaderManager.getInstance(fragment);
                RecyclerView.Adapter<?> adapter = ((AdvancedRecyclerView) fragment.requireView()
                        .findViewById(R.id.advanced_recycler_view)).getRecyclerView().getAdapter();
                for (int i = 0; i < 200 && (adapter.getItemCount() == 0 || loaders.hasRunningLoaders()); i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }
                assertFalse("a load is still running", loaders.hasRunningLoaders());
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    if (adapter.getItemViewType(i) == TransactionHeaderCursor.TYPE_ITEM) {
                        listed.add(adapter.getItemId(i));
                    }
                }
            });
        }
        return listed;
    }

    /**
     * The categories are stored only on a budget of the category type, any other type drops them.
     */
    private static long insertBudget(ContentResolver resolver, Contract.BudgetType type, String categoryIds, long walletA, long walletB) {
        ContentValues values = new ContentValues();
        values.put(Contract.Budget.TYPE, type.getValue());
        values.put(Contract.Budget.MONEY, 100000L);
        values.put(Contract.Budget.CURRENCY, "EUR");
        values.put(Contract.Budget.WALLET_IDS, "<" + walletA + ">,<" + walletB + ">");
        values.put(Contract.Budget.CATEGORY_IDS, categoryIds);
        values.put(Contract.Budget.START_DATE, "2026-01-01");
        values.put(Contract.Budget.END_DATE, "2026-01-31");
        return ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_BUDGETS, values));
    }

    private static long insertCategory(ContentResolver resolver) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, "Misc");
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private static long insertWallet(ContentResolver resolver, String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private static long insertExpense(ContentResolver resolver, long wallet, long category) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 200L);
        values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00");
        values.put(Contract.Transaction.DESCRIPTION, "Row");
        values.put(Contract.Transaction.CATEGORY_ID, category);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, wallet);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }
}
