package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * Opens the recurrent transfer editor on a stored recurrence, against the real content provider
 * over a fresh database. Wallet A is EUR with two decimals and wallet B is JPY with none, so 10000
 * (100.00 EUR) against 15000 yen is a rate of 150.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditRecurrentTransferActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private ContentResolver mResolver;
    private long mWalletA;
    private long mWalletB;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        mWalletA = insertWallet("Cash", "EUR");
        mWalletB = insertWallet("Tokyo", "JPY");
    }

    @Test
    public void anEditedCrossScaleRecurrenceShowsTheRateLineWithAnAsciiArrow() {
        long recurrence = insertRecurrentTransfer(10000L, 15000L, mWalletA, mWalletB);
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(recurrence))) {
            scenario.onActivity(activity -> {
                TextView rate = activity.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals(euroToYenRateLine(150D), rate.getText().toString());
            });
        }
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

    // the start date is ahead, so the insert writes no occurrence into the transfer table
    private long insertRecurrentTransfer(long moneyFrom, long moneyTo, long from, long to) {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Rent share");
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, from);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, to);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, moneyFrom);
        values.put(Contract.RecurrentTransfer.MONEY_TO, moneyTo);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.NOTE, "Recurrence note");
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE,
                DateUtils.getSQLDateString(new Date(System.currentTimeMillis() + 30L * DAY)));
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=MONTHLY");
        return ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values));
    }

    private static Intent editIntent(long recurrenceId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditRecurrentTransferActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, recurrenceId);
        return intent;
    }

    private static String euroToYenRateLine(double rate) {
        return String.format(Locale.getDefault(), "%s -> %s: %.2f",
                CurrencyManager.getCurrency("EUR").getSymbol(),
                CurrencyManager.getCurrency("JPY").getSymbol(), rate);
    }
}
