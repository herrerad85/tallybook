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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Locale;

import static org.junit.Assert.assertEquals;

/**
 * Opens the transfer model editor on a stored model, against the real content provider over a
 * fresh database. Wallet A is EUR with two decimals and wallet B is JPY with none, so 10000
 * (100.00 EUR) against 15000 yen is a rate of 150.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditTransferModelActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

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
    public void anEditedCrossScaleModelShowsTheRateLineWithAnAsciiArrow() {
        long model = insertModel(10000L, 15000L, mWalletA, mWalletB);
        try (ActivityScenario<NewEditTransferModelActivity> scenario =
                     ActivityScenario.launch(editIntent(model))) {
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

    private long insertModel(long moneyFrom, long moneyTo, long from, long to) {
        ContentValues values = new ContentValues();
        values.put(Contract.TransferModel.DESCRIPTION, "Rent share");
        values.put(Contract.TransferModel.WALLET_FROM_ID, from);
        values.put(Contract.TransferModel.WALLET_TO_ID, to);
        values.put(Contract.TransferModel.MONEY_FROM, moneyFrom);
        values.put(Contract.TransferModel.MONEY_TO, moneyTo);
        values.put(Contract.TransferModel.MONEY_TAX, 0L);
        values.put(Contract.TransferModel.NOTE, "Template note");
        values.put(Contract.TransferModel.CONFIRMED, true);
        values.put(Contract.TransferModel.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_TRANSFER_MODELS, values));
    }

    private static Intent editIntent(long modelId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransferModelActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, modelId);
        return intent;
    }

    private static String euroToYenRateLine(double rate) {
        return String.format(Locale.getDefault(), "%s -> %s: %.2f",
                CurrencyManager.getCurrency("EUR").getSymbol(),
                CurrencyManager.getCurrency("JPY").getSymbol(), rate);
    }
}
