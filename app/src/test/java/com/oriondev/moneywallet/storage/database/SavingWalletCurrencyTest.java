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
 * When a wallet may change currency while a saving and some of its deposits or withdrawals sit in
 * two wallets, asked of {@link SQLDatabase#updateWallet} on real SQLite in the JVM. A saving adds
 * them up with no currency in the sum, so a change on either side would count one currency at
 * face value as the other.
 */
@RunWith(RobolectricTestRunner.class)
public class SavingWalletCurrencyTest {

    private static final String NAME = "saving_wallets.db";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String DATE = "2026-07-01 10:00:00";

    private SQLDatabase mDatabase;
    private long mEuroWallet;
    private long mOtherEuroWallet;
    private long mDollarWallet;
    private long mSaving;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = new SQLDatabase(context, NAME);
        mEuroWallet = insertWallet("Cash", "EUR");
        mOtherEuroWallet = insertWallet("Bank", "EUR");
        mDollarWallet = insertWallet("Travel", "USD");
        mSaving = insertSaving(mEuroWallet);
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void changingTheCurrencyOfTheWalletADepositSitsInIsRefused() {
        insertAndMoveSaving(deposit(mEuroWallet));
        assertCurrencyChangeIsRefused(mEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
    }

    @Test
    public void changingTheCurrencyOfTheSavingsWalletIsRefusedWhileADepositSitsElsewhere() {
        insertAndMoveSaving(deposit(mEuroWallet));
        assertCurrencyChangeIsRefused(mOtherEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
    }

    @Test
    public void changingTheCurrencyOfTheWalletAWithdrawalSitsInIsRefused() {
        insertAndMoveSaving(withdrawal(mEuroWallet));
        assertCurrencyChangeIsRefused(mEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
    }

    @Test
    public void changingTheCurrencyOfTheSavingsWalletIsRefusedWhileAWithdrawalSitsElsewhere() {
        insertAndMoveSaving(withdrawal(mEuroWallet));
        assertCurrencyChangeIsRefused(mOtherEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
    }

    @Test
    public void aFutureUnconfirmedDepositAlsoHoldsTheCurrency() {
        ContentValues deposit = deposit(mEuroWallet);
        deposit.put(Contract.Transaction.DATE, "2099-01-01 10:00:00");
        deposit.put(Contract.Transaction.CONFIRMED, false);
        insertAndMoveSaving(deposit);
        assertCurrencyChangeIsRefused(mEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
    }

    @Test
    public void aWalletWithASplitDebtPaymentAndASplitSavingIsRefusedForTheDebt() {
        long debt = insertDebt(mEuroWallet);
        assertTrue(mDatabase.insertTransaction(payment(mOtherEuroWallet, debt)) > 0);
        insertAndMoveSaving(deposit(mEuroWallet));
        assertCurrencyChangeIsRefused(mOtherEuroWallet, Contract.ErrorCode.WALLETS_NOT_CONSISTENT);
    }

    @Test
    public void savingTheSameCurrencyOnAWalletWithADepositElsewhereIsAccepted() {
        insertAndMoveSaving(deposit(mEuroWallet));
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, "Savings");
        contentValues.put(Contract.Wallet.CURRENCY, "EUR");
        assertEquals(1, mDatabase.updateWallet(mEuroWallet, contentValues));
        assertEquals("Savings", walletColumn(mEuroWallet, Contract.Wallet.NAME));
        assertEquals("EUR", currencyOf(mEuroWallet));
    }

    @Test
    public void archivingTheWalletADepositSitsInIsAccepted() {
        insertAndMoveSaving(deposit(mEuroWallet));
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.ARCHIVED, true);
        assertEquals(1, mDatabase.updateWallet(mEuroWallet, contentValues));
        assertEquals("1", walletColumn(mEuroWallet, Contract.Wallet.ARCHIVED));
        assertEquals("EUR", currencyOf(mEuroWallet));
    }

    @Test
    public void reorderingTheSavingsWalletIsAcceptedWhileADepositSitsElsewhere() {
        insertAndMoveSaving(deposit(mEuroWallet));
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.INDEX, 7);
        assertEquals(1, mDatabase.updateWallet(mOtherEuroWallet, contentValues));
        assertEquals("7", walletColumn(mOtherEuroWallet, Contract.Wallet.INDEX));
        assertEquals("EUR", currencyOf(mOtherEuroWallet));
    }

    @Test
    public void changingTheCurrencyOfAWalletHoldingASavingAndAllItsDepositsIsAccepted() {
        assertTrue(mDatabase.insertTransaction(deposit(mEuroWallet)) > 0);
        assertTrue(mDatabase.insertTransaction(withdrawal(mEuroWallet)) > 0);
        assertCurrencyChangeIsAccepted(mEuroWallet);
    }

    @Test
    public void aDeletedDepositLeftInTheOtherWalletDoesNotHoldTheCurrency() {
        long deposit = insertAndMoveSaving(deposit(mEuroWallet));
        ContentValues deleted = new ContentValues();
        deleted.put(Schema.Transaction.DELETED, true);
        assertEquals(1, mDatabase.getWritableDatabase().update(Schema.Transaction.TABLE, deleted,
                Schema.Transaction.ID + " = ?", new String[] {String.valueOf(deposit)}));
        assertCurrencyChangeIsAccepted(mEuroWallet);
    }

    @Test
    public void movingTheSavingBackToItsDepositsLetsTheCurrencyChange() {
        insertAndMoveSaving(deposit(mEuroWallet));
        assertCurrencyChangeIsRefused(mEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
        assertEquals(1, mDatabase.updateSaving(mSaving, movedTo(mEuroWallet)));
        assertCurrencyChangeIsAccepted(mEuroWallet);
    }

    /**
     * A ledger from before checkSavingWalletCurrency can hold a saving already in another currency
     * than its deposits. The deposits' wallet is held, and the move back that puts it right is let
     * through.
     */
    @Test
    public void aSavingAlreadyInAnotherCurrencyThanItsDepositsIsRefusedAndCanMoveBack() {
        assertTrue(mDatabase.insertTransaction(deposit(mEuroWallet)) > 0);
        ContentValues moved = new ContentValues();
        moved.put(Schema.Saving.WALLET, mDollarWallet);
        assertEquals(1, mDatabase.getWritableDatabase().update(Schema.Saving.TABLE, moved,
                Schema.Saving.ID + " = ?", new String[] {String.valueOf(mSaving)}));
        assertCurrencyChangeIsRefused(mEuroWallet, Contract.ErrorCode.SAVING_IN_OTHER_WALLET);
        assertEquals(1, mDatabase.updateSaving(mSaving, movedTo(mEuroWallet)));
        assertEquals(mEuroWallet, walletOfSaving(mSaving));
    }

    private long insertAndMoveSaving(ContentValues transaction) {
        long id = mDatabase.insertTransaction(transaction);
        assertTrue(id > 0);
        assertEquals(1, mDatabase.updateSaving(mSaving, movedTo(mOtherEuroWallet)));
        assertEquals(mOtherEuroWallet, walletOfSaving(mSaving));
        return id;
    }

    private void assertCurrencyChangeIsRefused(long walletId, int errorCode) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.CURRENCY, "USD");
        try {
            mDatabase.updateWallet(walletId, contentValues);
            fail("The currency of a wallet on one side of a saving split over two wallets was changed");
        } catch (SQLiteDataException e) {
            assertEquals(errorCode, e.getErrorCode());
        }
        assertEquals("EUR", currencyOf(walletId));
    }

    private void assertCurrencyChangeIsAccepted(long walletId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.CURRENCY, "USD");
        assertEquals(1, mDatabase.updateWallet(walletId, contentValues));
        assertEquals("USD", currencyOf(walletId));
    }

    private ContentValues movedTo(long walletId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Saving.WALLET_ID, walletId);
        return contentValues;
    }

    private ContentValues deposit(long walletId) {
        return savingTransaction(walletId, Contract.CategoryTag.SAVING_DEPOSIT,
                Contract.Direction.EXPENSE);
    }

    private ContentValues withdrawal(long walletId) {
        return savingTransaction(walletId, Contract.CategoryTag.SAVING_WITHDRAW,
                Contract.Direction.INCOME);
    }

    private ContentValues savingTransaction(long walletId, String tag, int direction) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, 500L);
        contentValues.put(Contract.Transaction.DATE, DATE);
        contentValues.put(Contract.Transaction.DESCRIPTION, "saving");
        contentValues.put(Contract.Transaction.CATEGORY_ID, systemCategory(tag));
        contentValues.put(Contract.Transaction.DIRECTION, direction);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        contentValues.put(Contract.Transaction.WALLET_ID, walletId);
        contentValues.put(Contract.Transaction.SAVING_ID, mSaving);
        contentValues.put(Contract.Transaction.CONFIRMED, true);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return contentValues;
    }

    private ContentValues payment(long walletId, long debtId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, 500L);
        contentValues.put(Contract.Transaction.DATE, DATE);
        contentValues.put(Contract.Transaction.DESCRIPTION, "payment");
        contentValues.put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.PAID_DEBT));
        contentValues.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT);
        contentValues.put(Contract.Transaction.WALLET_ID, walletId);
        contentValues.put(Contract.Transaction.DEBT_ID, debtId);
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

    private long insertSaving(long walletId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Saving.DESCRIPTION, "Holiday");
        contentValues.put(Contract.Saving.ICON, ICON);
        contentValues.put(Contract.Saving.START_MONEY, 0L);
        contentValues.put(Contract.Saving.END_MONEY, 2000L);
        contentValues.put(Contract.Saving.WALLET_ID, walletId);
        contentValues.put(Contract.Saving.COMPLETE, false);
        long id = mDatabase.insertSaving(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertDebt(long walletId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        contentValues.put(Contract.Debt.ICON, ICON);
        contentValues.put(Contract.Debt.DESCRIPTION, "Loan");
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

    private long walletOfSaving(long savingId) {
        Cursor cursor = mDatabase.getSaving(savingId, new String[] {Contract.Saving.WALLET_ID});
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Saving.WALLET_ID));
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
}
