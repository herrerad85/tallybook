package com.oriondev.moneywallet.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DebtInterestTest {

    private static final String NAME = "debt-interest.db";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String DATE = "2026-07-01 10:00:00";

    private static final String FUTURE_DATE = "2099-01-01 10:00:00";

    private SQLDatabase mDatabase;
    private long mWallet;
    private long mDebt;
    private long mCredit;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = new SQLDatabase(context, NAME);
        mWallet = insertWallet();
        mDebt = insertDebt(Contract.DebtType.DEBT);
        mCredit = insertDebt(Contract.DebtType.CREDIT);
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void aDebtWithNoInterestReadsNull() {
        insert(mDebt, Contract.CategoryTag.PAID_DEBT, Contract.Direction.EXPENSE, 500L, DATE, true);
        assertNull(column(mDebt, Contract.Debt.INTEREST));
    }

    @Test
    public void interestLeavesProgressAlone() {
        insert(mDebt, Contract.CategoryTag.PAID_DEBT, Contract.Direction.EXPENSE, 500L, DATE, true);
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 40L, DATE, true);
        assertEquals(Long.valueOf(-500L), column(mDebt, Contract.Debt.PROGRESS));
        assertEquals(Long.valueOf(-40L), column(mDebt, Contract.Debt.INTEREST));
    }

    @Test
    public void interestOnACreditIsSummedAsIncome() {
        insert(mCredit, Contract.CategoryTag.PAID_CREDIT, Contract.Direction.INCOME, 300L, DATE, true);
        insert(mCredit, Contract.CategoryTag.CREDIT_INTEREST, Contract.Direction.INCOME, 25L, DATE, true);
        insert(mCredit, Contract.CategoryTag.CREDIT_INTEREST, Contract.Direction.INCOME, 15L, DATE, true);
        assertEquals(Long.valueOf(300L), column(mCredit, Contract.Debt.PROGRESS));
        assertEquals(Long.valueOf(40L), column(mCredit, Contract.Debt.INTEREST));
    }

    @Test
    public void interestSkipsUnconfirmedAndFutureRows() {
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 40L, DATE, true);
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 7L, DATE, false);
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 9L, FUTURE_DATE, true);
        assertEquals(Long.valueOf(-40L), column(mDebt, Contract.Debt.INTEREST));
    }

    @Test
    public void interestSkipsDeletedRows() {
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 40L, DATE, true);
        long deleted = insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE,
                60L, DATE, true);
        ContentValues softDeleted = new ContentValues();
        softDeleted.put(Schema.Transaction.DELETED, true);
        assertEquals(1, mDatabase.getWritableDatabase().update(Schema.Transaction.TABLE, softDeleted,
                Schema.Transaction.ID + " = ?", new String[] {String.valueOf(deleted)}));
        assertEquals(Long.valueOf(-40L), column(mDebt, Contract.Debt.INTEREST));
    }

    @Test
    public void interestCountsOnlyItsOwnDebt() {
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 40L, DATE, true);
        insert(mCredit, Contract.CategoryTag.CREDIT_INTEREST, Contract.Direction.INCOME, 25L, DATE, true);
        assertEquals(Long.valueOf(-40L), column(mDebt, Contract.Debt.INTEREST));
        assertEquals(Long.valueOf(25L), column(mCredit, Contract.Debt.INTEREST));
    }

    @Test
    public void deletingADebtDeletesItsInterest() {
        insert(mDebt, Contract.CategoryTag.PAID_DEBT, Contract.Direction.EXPENSE, 500L, DATE, true);
        insert(mDebt, Contract.CategoryTag.DEBT_INTEREST, Contract.Direction.EXPENSE, 40L, DATE, true);
        assertEquals(1, mDatabase.deleteDebt(mDebt));
        assertEquals(0, transactionsOf(mDebt));
    }

    private long insert(long debtId, String tag, int direction, long money, String date,
                        boolean confirmed) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, money);
        contentValues.put(Contract.Transaction.DATE, date);
        contentValues.put(Contract.Transaction.DESCRIPTION, "debt row");
        contentValues.put(Contract.Transaction.CATEGORY_ID, systemCategory(tag));
        contentValues.put(Contract.Transaction.DIRECTION, direction);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT);
        contentValues.put(Contract.Transaction.WALLET_ID, mWallet);
        contentValues.put(Contract.Transaction.DEBT_ID, debtId);
        contentValues.put(Contract.Transaction.CONFIRMED, confirmed);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        long id = mDatabase.insertTransaction(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertWallet() {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, "Cash");
        contentValues.put(Contract.Wallet.ICON, ICON);
        contentValues.put(Contract.Wallet.CURRENCY, "EUR");
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        long id = mDatabase.insertWallet(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertDebt(Contract.DebtType type) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Debt.TYPE, type.getValue());
        contentValues.put(Contract.Debt.ICON, ICON);
        contentValues.put(Contract.Debt.DESCRIPTION, "Loan");
        contentValues.put(Contract.Debt.DATE, "2026-07-01");
        contentValues.put(Contract.Debt.WALLET_ID, mWallet);
        contentValues.put(Contract.Debt.MONEY, 2000L);
        contentValues.put(Contract.Debt.ARCHIVED, false);
        contentValues.put(Contract.Debt.INSERT_MASTER_TRANSACTION, false);
        long id = mDatabase.insertDebt(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long systemCategory(String tag) {
        Cursor cursor = mDatabase.getCategories(new String[] {Contract.Category.ID},
                Contract.Category.TAG + " = ?", new String[] {tag}, null);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID));
        } finally {
            cursor.close();
        }
    }

    private Long column(long debtId, String column) {
        Cursor cursor = mDatabase.getDebt(debtId, new String[] {column});
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            int index = cursor.getColumnIndexOrThrow(column);
            return cursor.isNull(index) ? null : cursor.getLong(index);
        } finally {
            cursor.close();
        }
    }

    private int transactionsOf(long debtId) {
        Cursor cursor = mDatabase.getTransactions(new String[] {Contract.Transaction.ID},
                Contract.Transaction.DEBT_ID + " = ?", new String[] {String.valueOf(debtId)}, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }
}
