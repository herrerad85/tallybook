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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Which wallet a debt payment may sit in, asked of {@link SQLDatabase#insertTransaction} and
 * {@link SQLDatabase#updateTransaction} on real SQLite in the JVM. A debt is read in the currency
 * of its wallet and its payments are added up with no currency in the sum, so a payment may go to
 * any wallet of that currency and to no other.
 */
@RunWith(RobolectricTestRunner.class)
public class DebtPaymentWalletCurrencyTest {

    private static final String NAME = "debt-payments.db";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String DATE = "2026-07-01 10:00:00";

    private SQLDatabase mDatabase;
    private long mEuroWallet;
    private long mOtherEuroWallet;
    private long mDollarWallet;
    private long mDebt;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = new SQLDatabase(context, NAME);
        mEuroWallet = insertWallet("Cash", "EUR");
        mOtherEuroWallet = insertWallet("Bank", "EUR");
        mDollarWallet = insertWallet("Travel", "USD");
        mDebt = insertDebt(mEuroWallet);
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void aPaymentIntoAnotherWalletOfTheDebtsCurrencyIsAccepted() {
        long payment = mDatabase.insertTransaction(payment(mOtherEuroWallet, "note"));
        assertTrue(payment > 0);
        assertEquals(mOtherEuroWallet, walletOf(payment));
    }

    @Test
    public void aPaymentIntoAWalletOfAnotherCurrencyIsRefusedAndWritesNothing() {
        try {
            mDatabase.insertTransaction(payment(mDollarWallet, "note"));
            fail("A euro debt was paid from a dollar wallet");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        assertEquals(0, paymentsOf(mDebt));
    }

    @Test
    public void aCreditRepaymentIntoAWalletOfAnotherCurrencyIsRefusedAndWritesNothing() {
        long credit = insertCredit(mEuroWallet);
        ContentValues repayment = payment(mDollarWallet, "note");
        repayment.put(Contract.Transaction.DEBT_ID, credit);
        repayment.put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.PAID_CREDIT));
        repayment.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME);
        try {
            mDatabase.insertTransaction(repayment);
            fail("A euro credit was repaid into a dollar wallet");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        assertEquals(0, paymentsOf(credit));
    }

    /**
     * A master transaction is filed under the debt's own category and is not a payment, so this
     * check leaves it alone. Moving it moves the debt, which checkDebtOfMasterTransactionWallet
     * answers for.
     */
    @Test
    public void aDebtsMasterTransactionIsNotHeldToIt() {
        ContentValues master = payment(mDollarWallet, "note");
        master.put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.DEBT));
        assertTrue(mDatabase.insertTransaction(master) > 0);
        assertTrue(insertDebt(mDollarWallet, true) > 0);
    }

    /**
     * A ledger from before this check can hold a payment already in another currency than its
     * debt. A save that keeps that wallet has to go through, or its note could never be edited.
     */
    @Test
    public void aMismatchedPaymentThatKeepsItsWalletCanStillBeEdited() {
        long payment = mDatabase.insertTransaction(payment(mEuroWallet, "note"));
        ContentValues moved = new ContentValues();
        moved.put(Schema.Transaction.WALLET, mDollarWallet);
        assertEquals(1, mDatabase.getWritableDatabase().update(Schema.Transaction.TABLE, moved,
                Schema.Transaction.ID + " = ?", new String[] {String.valueOf(payment)}));
        assertEquals(1, mDatabase.updateTransaction(payment, payment(mDollarWallet, "edited")));
        assertEquals(mDollarWallet, walletOf(payment));
        assertEquals("edited", noteOf(payment));
    }

    @Test
    public void movingAPaymentToAWalletOfAnotherCurrencyIsRefused() {
        long payment = mDatabase.insertTransaction(payment(mEuroWallet, "note"));
        try {
            mDatabase.updateTransaction(payment, payment(mDollarWallet, "edited"));
            fail("A payment on a euro debt was moved to a dollar wallet");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        assertEquals(mEuroWallet, walletOf(payment));
        assertEquals("note", noteOf(payment));
    }

    /** An update naming only the wallet is still held to the debt and category stored on the row. */
    @Test
    public void movingAPaymentByItsWalletAloneToAnotherCurrencyIsRefused() {
        long payment = mDatabase.insertTransaction(payment(mEuroWallet, "note"));
        ContentValues moved = new ContentValues();
        moved.put(Contract.Transaction.WALLET_ID, mDollarWallet);
        try {
            mDatabase.updateTransaction(payment, moved);
            fail("A payment on a euro debt was moved to a dollar wallet");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        assertEquals(mEuroWallet, walletOf(payment));
    }

    @Test
    public void movingAPaymentToAnotherWalletOfTheDebtsCurrencyIsAccepted() {
        long payment = mDatabase.insertTransaction(payment(mEuroWallet, "note"));
        assertEquals(1, mDatabase.updateTransaction(payment, payment(mOtherEuroWallet, "edited")));
        assertEquals(mOtherEuroWallet, walletOf(payment));
    }

    @Test
    public void changingTheCurrencyOfTheWalletARepaymentSitsInIsRefused() {
        assertTrue(mDatabase.insertTransaction(payment(mOtherEuroWallet, "note")) > 0);
        assertCurrencyChangeIsRefused(mOtherEuroWallet);
    }

    @Test
    public void changingTheCurrencyOfTheDebtsWalletIsRefusedWhileARepaymentSitsElsewhere() {
        assertTrue(mDatabase.insertTransaction(payment(mOtherEuroWallet, "note")) > 0);
        assertCurrencyChangeIsRefused(mEuroWallet);
    }

    @Test
    public void changingTheCurrencyOfTheWalletACreditRepaymentSitsInIsRefused() {
        long credit = insertCredit(mEuroWallet);
        ContentValues repayment = payment(mOtherEuroWallet, "note");
        repayment.put(Contract.Transaction.DEBT_ID, credit);
        repayment.put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.PAID_CREDIT));
        repayment.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME);
        assertTrue(mDatabase.insertTransaction(repayment) > 0);
        assertCurrencyChangeIsRefused(mOtherEuroWallet);
    }

    /** A pending repayment dated later still counts once it is confirmed, so it holds too. */
    @Test
    public void aFutureUnconfirmedRepaymentAlsoHoldsTheCurrency() {
        ContentValues repayment = payment(mOtherEuroWallet, "note");
        repayment.put(Contract.Transaction.DATE, "2099-01-01 10:00:00");
        repayment.put(Contract.Transaction.CONFIRMED, false);
        assertTrue(mDatabase.insertTransaction(repayment) > 0);
        assertCurrencyChangeIsRefused(mOtherEuroWallet);
    }

    /** The wallet editor sends the currency on every save, changed or not. */
    @Test
    public void savingTheSameCurrencyOnAWalletWithARepaymentElsewhereIsAccepted() {
        assertTrue(mDatabase.insertTransaction(payment(mOtherEuroWallet, "note")) > 0);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, "Savings");
        contentValues.put(Contract.Wallet.CURRENCY, "EUR");
        assertEquals(1, mDatabase.updateWallet(mOtherEuroWallet, contentValues));
        assertEquals("Savings", walletColumn(mOtherEuroWallet, Contract.Wallet.NAME));
        assertEquals("EUR", currencyOf(mOtherEuroWallet));
    }

    /** Archiving a wallet sends no currency at all, so the check is not asked. */
    @Test
    public void archivingTheWalletARepaymentSitsInIsAccepted() {
        assertTrue(mDatabase.insertTransaction(payment(mOtherEuroWallet, "note")) > 0);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.ARCHIVED, true);
        assertEquals(1, mDatabase.updateWallet(mOtherEuroWallet, contentValues));
        assertEquals("1", walletColumn(mOtherEuroWallet, Contract.Wallet.ARCHIVED));
        assertEquals("EUR", currencyOf(mOtherEuroWallet));
    }

    /** Sorting the wallets sends only the index. */
    @Test
    public void reorderingTheDebtsWalletIsAcceptedWhileARepaymentSitsElsewhere() {
        assertTrue(mDatabase.insertTransaction(payment(mOtherEuroWallet, "note")) > 0);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.INDEX, 7);
        assertEquals(1, mDatabase.updateWallet(mEuroWallet, contentValues));
        assertEquals("7", walletColumn(mEuroWallet, Contract.Wallet.INDEX));
        assertEquals("EUR", currencyOf(mEuroWallet));
    }

    @Test
    public void changingTheCurrencyOfAWalletHoldingBothADebtAndItsRepaymentIsAccepted() {
        long wallet = insertWallet("Home", "EUR");
        long debt = insertDebt(wallet);
        ContentValues repayment = payment(wallet, "note");
        repayment.put(Contract.Transaction.DEBT_ID, debt);
        assertTrue(mDatabase.insertTransaction(repayment) > 0);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.CURRENCY, "USD");
        assertEquals(1, mDatabase.updateWallet(wallet, contentValues));
        assertEquals("USD", currencyOf(wallet));
    }

    /** Tries to move a euro wallet to dollars and checks it is refused and still in euros. */
    private void assertCurrencyChangeIsRefused(long walletId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.CURRENCY, "USD");
        try {
            mDatabase.updateWallet(walletId, contentValues);
            fail("The currency of a wallet on one side of a repayment in another wallet was changed");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        assertEquals("EUR", currencyOf(walletId));
    }

    /** The values the transaction editor saves a payment on mDebt with. */
    private ContentValues payment(long walletId, String note) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, 500L);
        contentValues.put(Contract.Transaction.DATE, DATE);
        contentValues.put(Contract.Transaction.DESCRIPTION, "payment");
        contentValues.put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.PAID_DEBT));
        contentValues.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT);
        contentValues.put(Contract.Transaction.WALLET_ID, walletId);
        contentValues.put(Contract.Transaction.NOTE, note);
        contentValues.put(Contract.Transaction.DEBT_ID, mDebt);
        contentValues.put(Contract.Transaction.CONFIRMED, true);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return contentValues;
    }

    private long insertWallet(String name, String currency) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, ICON);
        contentValues.put(Contract.Wallet.CURRENCY, currency);
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        long id = mDatabase.insertWallet(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertDebt(long walletId) {
        return insertDebt(walletId, false);
    }

    private long insertDebt(long walletId, boolean masterTransaction) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        contentValues.put(Contract.Debt.ICON, ICON);
        contentValues.put(Contract.Debt.DESCRIPTION, "Loan");
        contentValues.put(Contract.Debt.DATE, "2026-07-01");
        contentValues.put(Contract.Debt.WALLET_ID, walletId);
        contentValues.put(Contract.Debt.MONEY, 2000L);
        contentValues.put(Contract.Debt.ARCHIVED, false);
        contentValues.put(Contract.Debt.INSERT_MASTER_TRANSACTION, masterTransaction);
        long id = mDatabase.insertDebt(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertCredit(long walletId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Debt.TYPE, Contract.DebtType.CREDIT.getValue());
        contentValues.put(Contract.Debt.ICON, ICON);
        contentValues.put(Contract.Debt.DESCRIPTION, "Lent");
        contentValues.put(Contract.Debt.DATE, "2026-07-01");
        contentValues.put(Contract.Debt.WALLET_ID, walletId);
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

    private long walletOf(long transactionId) {
        Cursor cursor = mDatabase.getTransaction(transactionId,
                new String[] {Contract.Transaction.WALLET_ID});
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_ID));
        } finally {
            cursor.close();
        }
    }

    private String noteOf(long transactionId) {
        Cursor cursor = mDatabase.getTransaction(transactionId,
                new String[] {Contract.Transaction.NOTE});
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.NOTE));
        } finally {
            cursor.close();
        }
    }

    private String currencyOf(long walletId) {
        return walletColumn(walletId, Contract.Wallet.CURRENCY);
    }

    private String walletColumn(long walletId, String column) {
        Cursor cursor = mDatabase.getWallet(walletId, new String[] {column});
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow(column));
        } finally {
            cursor.close();
        }
    }

    private int paymentsOf(long debtId) {
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
