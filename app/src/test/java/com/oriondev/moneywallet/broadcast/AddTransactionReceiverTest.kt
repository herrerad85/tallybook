package com.oriondev.moneywallet.broadcast;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.os.Bundle;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowBroadcastPendingResult;
import org.robolectric.shadows.ShadowLog;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class AddTransactionReceiverTest {

    private static final String ACTION = "io.github.herrerad85.tallybook.action.SAVE_TRANSACTION";
    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private String mCode;
    private long mCashWallet;
    private long mBankWallet;
    private long[] mTwinWallets;
    private long mSalary;
    private long mSnacks;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        mCashWallet = insertWallet("Cash", "EUR");
        mBankWallet = insertWallet("Bank", "JPY");
        mTwinWallets = new long[] {insertWallet("Twin", "EUR"), insertWallet("Twin", "EUR")};
        mSalary = insertCategory("Payroll", Contract.CategoryType.INCOME);
        mSnacks = insertCategory("Snacks", Contract.CategoryType.EXPENSE);
        insertRule("coffee", mSnacks);
        PreferenceManager.setDefaultWallet(mCashWallet);
        mCode = AddTransactionReceiver.newCode();
        PreferenceManager.setAutomationCode(mCode);
        PreferenceManager.setAutomationSaveEnabled(true);
    }

    @Test
    public void aCodeIsThirtyTwoLowercaseHexCharacters() {
        assertTrue(AddTransactionReceiver.newCode().matches("[0-9a-f]{32}"));
    }

    @Test
    public void aNewCodeDiffersFromTheLastOne() {
        assertNotEquals(AddTransactionReceiver.newCode(), AddTransactionReceiver.newCode());
    }

    @Test
    public void aBroadcastWithNoCategoryOrTheDefaultOneReachesTheReceiver() {
        Intent plain = new Intent(ACTION).setPackage(mContext.getPackageName());
        Intent withDefault = new Intent(ACTION).setPackage(mContext.getPackageName())
                .addCategory(Intent.CATEGORY_DEFAULT);
        for (Intent intent : new Intent[] {plain, withDefault}) {
            List<ResolveInfo> receivers = mContext.getPackageManager().queryBroadcastReceivers(intent, 0);
            assertEquals(1, receivers.size());
            assertEquals(AddTransactionReceiver.class.getName(), receivers.get(0).activityInfo.name);
        }
    }

    @Test
    public void theReceiverIsExportedSoAnAutomationAppCanReachIt() throws Exception {
        assertTrue(mContext.getPackageManager().getReceiverInfo(
                new ComponentName(mContext, AddTransactionReceiver.class), 0).exported);
    }

    @Test
    public void theRightCodeSavesOneUnconfirmedRowWithEveryField() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "cash");
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "-12.346");
        intent.putExtra(NewEditTransactionActivity.EXTRA_CATEGORY, "payroll");
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Bonus");
        intent.putExtra(NewEditTransactionActivity.EXTRA_NOTE, "March");
        long before = System.currentTimeMillis();
        receive(intent);
        assertEquals(1, countTransactions());
        try (Cursor cursor = onlyTransaction()) {
            assertEquals(mCashWallet, longOf(cursor, Contract.Transaction.WALLET_ID));
            assertEquals(1235L, longOf(cursor, Contract.Transaction.MONEY));
            assertEquals(Contract.Direction.INCOME, longOf(cursor, Contract.Transaction.DIRECTION));
            assertEquals(mSalary, longOf(cursor, Contract.Transaction.CATEGORY_ID));
            assertEquals(Contract.TransactionType.STANDARD, longOf(cursor, Contract.Transaction.TYPE));
            assertEquals("Bonus", stringOf(cursor, Contract.Transaction.DESCRIPTION));
            assertEquals("March", stringOf(cursor, Contract.Transaction.NOTE));
            assertEquals(0L, longOf(cursor, Contract.Transaction.CONFIRMED));
            assertEquals(1L, longOf(cursor, Contract.Transaction.COUNT_IN_TOTAL));
            long date = DateUtils.getDateFromSQLDateTimeString(stringOf(cursor, Contract.Transaction.DATE)).getTime();
            assertTrue(date >= before - 2000L && date <= System.currentTimeMillis() + 2000L);
        }
    }

    @Test
    public void theAmountIsRoundedToTheWalletsOwnDecimals() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "Bank");
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "1234.6");
        receive(intent);
        try (Cursor cursor = onlyTransaction()) {
            assertEquals(mBankWallet, longOf(cursor, Contract.Transaction.WALLET_ID));
            assertEquals(1235L, longOf(cursor, Contract.Transaction.MONEY));
        }
    }

    @Test
    public void aDescriptionARuleMatchesFillsTheCategory() {
        receive(request());
        try (Cursor cursor = onlyTransaction()) {
            assertEquals(mSnacks, longOf(cursor, Contract.Transaction.CATEGORY_ID));
            assertEquals(Contract.Direction.EXPENSE, longOf(cursor, Contract.Transaction.DIRECTION));
        }
    }

    @Test
    public void aCategoryNameNoCategoryCarriesFallsBackToTheRules() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_CATEGORY, "Nothing");
        receive(intent);
        try (Cursor cursor = onlyTransaction()) {
            assertEquals(mSnacks, longOf(cursor, Contract.Transaction.CATEGORY_ID));
        }
    }

    @Test
    public void anAmbiguousWalletNameTakesTheDefaultWallet() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "Twin");
        PreferenceManager.setDefaultWallet(mBankWallet);
        receive(intent);
        try (Cursor cursor = onlyTransaction()) {
            assertEquals(mBankWallet, longOf(cursor, Contract.Transaction.WALLET_ID));
        }
    }

    @Test
    public void withNoDefaultWalletTheFirstWalletByIndexAndNameIsTaken() {
        PreferenceManager.setDefaultWallet(PreferenceManager.NO_DEFAULT_WALLET);
        receive(request());
        try (Cursor cursor = onlyTransaction()) {
            assertEquals(mBankWallet, longOf(cursor, Contract.Transaction.WALLET_ID));
        }
    }

    @Test
    public void noWalletThatIsNotArchivedSavesNothing() {
        archiveWallet(mCashWallet);
        archiveWallet(mBankWallet);
        for (long wallet : mTwinWallets) {
            archiveWallet(wallet);
        }
        receive(request());
        assertEquals(0, countTransactions());
    }

    @Test
    public void aWalletInACurrencyTheAppDoesNotKnowSavesNothing() {
        insertWallet("Odd", "ZZZ");
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "Odd");
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void aWrongCodeSavesNothing() {
        Intent intent = request();
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, AddTransactionReceiver.newCode());
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void aMissingCodeSavesNothing() {
        Intent intent = request();
        intent.removeExtra(AddTransactionReceiver.EXTRA_CODE);
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void anEmptyCodeSavesNothing() {
        Intent intent = request();
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, "");
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void theStoredCodeWithoutItsLastCharacterSavesNothing() {
        Intent intent = request();
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, mCode.substring(0, mCode.length() - 1));
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void aRefusalLogsOneWarningAndNoLogHoldsTheCodeOrTheText() {
        String wrongCode = AddTransactionReceiver.newCode();
        Intent wrong = request();
        wrong.putExtra(AddTransactionReceiver.EXTRA_CODE, wrongCode);
        receive(wrong);
        Intent noCategory = request();
        noCategory.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Rent");
        receive(noCategory);
        assertEquals(0, countTransactions());
        Intent saved = request();
        saved.putExtra(NewEditTransactionActivity.EXTRA_CATEGORY, "payroll");
        saved.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Bonus qx7");
        saved.putExtra(NewEditTransactionActivity.EXTRA_NOTE, "March qx9");
        receive(saved);
        assertEquals(1, countTransactions());
        int warnings = 0;
        for (ShadowLog.LogItem item : ShadowLog.getLogs()) {
            String text = String.valueOf(item.msg);
            assertFalse(text, text.contains(mCode));
            assertFalse(text, text.contains(wrongCode));
            assertFalse(text, text.contains("qx7"));
            assertFalse(text, text.contains("qx9"));
            if ("AddTransactionReceiver".equals(item.tag) && item.type == Log.WARN) {
                warnings++;
            }
        }
        assertEquals(2, warnings);
    }

    @Test
    public void theSwitchOffSavesNothingEvenWithACodeLeftOver() {
        PreferenceManager.setAutomationSaveEnabled(false);
        receive(request());
        assertEquals(0, countTransactions());
    }

    @Test
    public void theSwitchOnWithNoStoredCodeSavesNothing() {
        PreferenceManager.setAutomationCode(null);
        Intent intent = request();
        intent.removeExtra(AddTransactionReceiver.EXTRA_CODE);
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void theCodeFromBeforeNewCodeSavesNothing() {
        PreferenceManager.setAutomationCode(AddTransactionReceiver.newCode());
        receive(request());
        assertEquals(0, countTransactions());
    }

    @Test
    public void noCategoryAndNoRuleMatchSavesNothing() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Rent");
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void noCategoryAndNoDescriptionSavesNothing() {
        insertRule("ul", mSalary);
        Intent intent = request();
        intent.removeExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION);
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void aZeroAmountSavesNothing() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "0.004");
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void anUnreadableAmountSavesNothing() {
        Intent intent = request();
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "twelve");
        receive(intent);
        assertEquals(0, countTransactions());
    }

    @Test
    public void aSaveLeavesTheOrderedResultUntouched() {
        assertResultUntouched(request());
        assertEquals(1, countTransactions());
    }

    @Test
    public void aRefusalLeavesTheOrderedResultUntouched() {
        Intent intent = request();
        intent.removeExtra(AddTransactionReceiver.EXTRA_CODE);
        assertResultUntouched(intent);
        assertEquals(0, countTransactions());
    }

    private void assertResultUntouched(Intent intent) {
        Bundle extras = new Bundle();
        extras.putString("caller", "kept");
        BroadcastReceiver.PendingResult pendingResult = ReflectionHelpers.callStaticMethod(
                ShadowBroadcastPendingResult.class, "create",
                ClassParameter.from(int.class, 7),
                ClassParameter.from(String.class, "data"),
                ClassParameter.from(Bundle.class, extras),
                ClassParameter.from(boolean.class, true));
        AddTransactionReceiver receiver = new AddTransactionReceiver();
        ReflectionHelpers.callInstanceMethod(receiver, "setPendingResult",
                ClassParameter.from(BroadcastReceiver.PendingResult.class, pendingResult));
        receiver.onReceive(mContext, intent);
        assertEquals(7, receiver.getResultCode());
        assertEquals("data", receiver.getResultData());
        assertSame(extras, receiver.getResultExtras(false));
        assertEquals(1, extras.size());
        assertEquals("kept", extras.getString("caller"));
    }

    private Intent request() {
        Intent intent = new Intent(ACTION);
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, mCode);
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "4.20");
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Coffee");
        return intent;
    }

    private void receive(Intent intent) {
        new AddTransactionReceiver().onReceive(mContext, intent);
    }

    private int countTransactions() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, null, null, null)) {
            return cursor.getCount();
        }
    }

    private Cursor onlyTransaction() {
        assertEquals(1, countTransactions());
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS, new String[] {
                Contract.Transaction.ID, Contract.Transaction.WALLET_ID, Contract.Transaction.MONEY,
                Contract.Transaction.DIRECTION, Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.TYPE, Contract.Transaction.DESCRIPTION, Contract.Transaction.NOTE,
                Contract.Transaction.CONFIRMED, Contract.Transaction.COUNT_IN_TOTAL,
                Contract.Transaction.DATE}, null, null, null);
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private static long longOf(Cursor cursor, String column) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column));
    }

    private static String stringOf(Cursor cursor, String column) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
    }

    private long insertWallet(String name, String currency) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private void archiveWallet(long wallet) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.ARCHIVED, true);
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, wallet),
                values, null, null);
    }

    private long insertCategory(String name, Contract.CategoryType type) {
        ContentValues category = new ContentValues();
        category.put(Contract.Category.NAME, name);
        category.put(Contract.Category.ICON, ICON);
        category.put(Contract.Category.TYPE, type.getValue());
        category.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category));
    }

    private void insertRule(String pattern, long category) {
        ContentValues rule = new ContentValues();
        rule.put(Contract.CategoryRule.PATTERN, pattern);
        rule.put(Contract.CategoryRule.CATEGORY_ID, category);
        mResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, rule);
    }
}
