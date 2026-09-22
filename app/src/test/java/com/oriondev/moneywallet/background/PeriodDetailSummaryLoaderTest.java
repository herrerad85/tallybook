package com.oriondev.moneywallet.background;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.PeriodDetailSummaryData;
import com.oriondev.moneywallet.model.PeriodMoney;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * The period summary sorts each row by its category tag. A transfer leg reaches the net and the
 * transfer figure but neither bar, while a transfer fee is money spent. Seen from one wallet, the
 * transfer figure is signed by the leg's direction.
 */
@RunWith(RobolectricTestRunner.class)
public class PeriodDetailSummaryLoaderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void transferLegsAndTheirFeeLandInTheirOwnFigures() {
        long wallet = insertWallet("Checking");
        long other = insertWallet("Savings");
        PreferenceManager.setCurrentWallet(mContext, wallet);
        insertIncome(wallet, 50L);
        insertTransfer(other, wallet, 300L, 0L);
        insertTransfer(wallet, other, 100L, 10L);

        PeriodDetailSummaryData data = new PeriodDetailSummaryLoader(mContext,
                at(1, 0, 0, 0, 0), at(31, 23, 59, 59, 999),
                PeriodDetailSummaryLoader.GROUP_BY_YEAR).loadInBackground();

        assertEquals(1, data.getPeriodCount());
        PeriodMoney period = data.getPeriodMoney(0);
        assertEquals("only the ordinary income is earned", 50L, period.getIncomes().getMoney("EUR"));
        assertEquals("only the fee is spent", 10L, period.getExpenses().getMoney("EUR"));
        assertEquals("300 in and 100 out", 200L, period.getTransfers().getMoney("EUR"));
        assertEquals(240L, period.getNetIncomes().getMoney("EUR"));
        assertEquals(240L, data.getNetIncomes().getMoney("EUR"));
    }

    private static Date at(int day, int hour, int minute, int second, int millisecond) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(2026, Calendar.JANUARY, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, millisecond);
        return calendar.getTime();
    }

    private static String midMonth() {
        return DateUtils.getSQLDateTimeString(at(15, 12, 0, 0, 0));
    }

    private long insertWallet(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private void insertIncome(long walletId, long money) {
        ContentValues category = new ContentValues();
        category.put(Contract.Category.NAME, "Salary");
        category.put(Contract.Category.ICON, ICON);
        category.put(Contract.Category.TYPE, Contract.CategoryType.INCOME.getValue());
        category.put(Contract.Category.SHOW_REPORT, true);
        long categoryId = ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category));
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, midMonth());
        values.put(Contract.Transaction.DESCRIPTION, "Pay");
        values.put(Contract.Transaction.CATEGORY_ID, categoryId);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        values.put(Contract.Transaction.WALLET_ID, walletId);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values);
    }

    private void insertTransfer(long fromWalletId, long toWalletId, long money, long fee) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Move")
                .date(midMonth())
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(money)
                .toMoney(money)
                .taxMoney(fee)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values);
    }
}
