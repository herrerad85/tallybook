package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class TransferItemFragmentTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ContentResolver mResolver;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
    }

    @Test
    public void aTransferInOneCurrencyShowsNeitherTheAmountReceivedNorTheRate() {
        long transferId = insertTransfer(insertWallet("Cash", "EUR"), 500L, insertWallet("Bank", "EUR"), 450L);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View view = showTransfer(activity, transferId);
                assertEquals(View.GONE, view.findViewById(R.id.received_text_view).getVisibility());
                assertEquals(View.GONE, view.findViewById(R.id.exchange_rate_text_view).getVisibility());
            });
        }
    }

    @Test
    public void aTransferAcrossCurrenciesShowsTheAmountReceivedAndTheRate() {
        long transferId = insertTransfer(insertWallet("Cash", "USD"), 10000L, insertWallet("Bank", "JPY"), 15231L);
        CurrencyUnit usd = CurrencyManager.getCurrency("USD");
        CurrencyUnit jpy = CurrencyManager.getCurrency("JPY");
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View view = showTransfer(activity, transferId);
                TextView received = view.findViewById(R.id.received_text_view);
                TextView rate = view.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, received.getVisibility());
                assertEquals(MoneyFormatter.getInstance().getNotTintedString(jpy, 15231L,
                        MoneyFormatter.CurrencyMode.ALWAYS_SHOWN), received.getText().toString());
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals("1 " + usd.getSymbolOrIso() + " = 152.31 " + jpy.getSymbolOrIso(), rate.getText().toString());
            });
        }
    }

    @Test
    public void aSmallRateKeepsFourSignificantDigits() {
        long transferId = insertTransfer(insertWallet("Cash", "JPY"), 100000L, insertWallet("Bank", "USD"), 65656L);
        CurrencyUnit usd = CurrencyManager.getCurrency("USD");
        CurrencyUnit jpy = CurrencyManager.getCurrency("JPY");
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView rate = showTransfer(activity, transferId).findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals("1 " + jpy.getSymbolOrIso() + " = 0.006566 " + usd.getSymbolOrIso(), rate.getText().toString());
            });
        }
    }

    @Test
    public void nothingReceivedHidesTheRate() {
        long transferId = insertTransfer(insertWallet("Cash", "USD"), 10000L, insertWallet("Bank", "JPY"), 0L);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View view = showTransfer(activity, transferId);
                assertEquals(View.VISIBLE, view.findViewById(R.id.received_text_view).getVisibility());
                assertEquals(View.GONE, view.findViewById(R.id.exchange_rate_text_view).getVisibility());
            });
        }
    }

    @Test
    public void nothingSentHidesTheRate() {
        long transferId = insertTransfer(insertWallet("Cash", "USD"), 0L, insertWallet("Bank", "JPY"), 15231L);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View view = showTransfer(activity, transferId);
                assertEquals(View.VISIBLE, view.findViewById(R.id.received_text_view).getVisibility());
                assertEquals(View.GONE, view.findViewById(R.id.exchange_rate_text_view).getVisibility());
            });
        }
    }

    @Test
    public void aCurrencyTheAppDoesNotKnowShowsAQuestionMarkBeforeTheAmountReceived() {
        CurrencyUnit eur = CurrencyManager.getCurrency("EUR");
        long cash = insertWallet("Cash", "ZZZ");
        long bank = insertWallet("Bank", "EUR");
        long toCash = insertTransfer(bank, 9000L, cash, 10000L);
        long toBank = insertTransfer(cash, 10000L, bank, 9000L);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView received = showTransfer(activity, toCash).findViewById(R.id.received_text_view);
                assertEquals("? 100.00", received.getText().toString());
                received = showTransfer(activity, toBank).findViewById(R.id.received_text_view);
                assertEquals(MoneyFormatter.getInstance().getNotTintedString(eur, 9000L,
                        MoneyFormatter.CurrencyMode.ALWAYS_SHOWN), received.getText().toString());
                assertFalse(received.getText().toString(), received.getText().toString().contains("?"));
            });
        }
    }

    @Test
    public void aCurrencyTheAppDoesNotKnowHidesTheRateAndShowsTheAmountReceived() {
        assertNull(CurrencyManager.getCurrency("ZZZ"));
        long cash = insertWallet("Cash", "ZZZ");
        long bank = insertWallet("Bank", "EUR");
        long[] transferIds = {insertTransfer(cash, 10000L, bank, 9000L), insertTransfer(bank, 9000L, cash, 10000L)};
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                for (long transferId : transferIds) {
                    View view = showTransfer(activity, transferId);
                    assertEquals(View.VISIBLE, view.findViewById(R.id.received_text_view).getVisibility());
                    assertEquals(View.GONE, view.findViewById(R.id.exchange_rate_text_view).getVisibility());
                }
            });
        }
    }

    @Test
    public void twoCurrenciesWithTheSameSymbolShowTheirIsoCodes() {
        CurrencyUnit usd = CurrencyManager.getCurrency("USD");
        CurrencyUnit cad = CurrencyManager.getCurrency("CAD");
        assertEquals(usd.getSymbolOrIso(), cad.getSymbolOrIso());
        long transferId = insertTransfer(insertWallet("Cash", "USD"), 10000L, insertWallet("Bank", "CAD"), 13600L);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View view = showTransfer(activity, transferId);
                TextView received = view.findViewById(R.id.received_text_view);
                TextView rate = view.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals(activity.getString(R.string.hint_exchange_rate_value, "USD", "1.36", "CAD"),
                        rate.getText().toString());
                assertEquals(View.VISIBLE, received.getVisibility());
                assertTrue(received.getText().toString(), received.getText().toString().contains("CAD"));
                assertFalse(received.getText().toString(), received.getText().toString().contains("$"));
            });
        }
    }

    @Test
    public void twoCurrenciesWithTheSameSymbolKeepTheirOwnDecimals() {
        CurrencyUnit cny = CurrencyManager.getCurrency("CNY");
        CurrencyUnit jpy = CurrencyManager.getCurrency("JPY");
        assertEquals(cny.getSymbolOrIso(), jpy.getSymbolOrIso());
        assertEquals(2, cny.getDecimals());
        assertEquals(0, jpy.getDecimals());
        long transferId = insertTransfer(insertWallet("Cash", "CNY"), 10000L, insertWallet("Bank", "JPY"), 2000L);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                View view = showTransfer(activity, transferId);
                TextView received = view.findViewById(R.id.received_text_view);
                TextView rate = view.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals(activity.getString(R.string.hint_exchange_rate_value, "CNY", "20.00", "JPY"),
                        rate.getText().toString());
                assertEquals(View.VISIBLE, received.getVisibility());
                assertTrue(received.getText().toString(), received.getText().toString().contains("JPY"));
                assertFalse(received.getText().toString(), received.getText().toString().contains(jpy.getSymbolOrIso()));
                assertEquals("JPY " + MoneyFormatter.getInstance().getNotTintedString(jpy, 2000L,
                        MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN), received.getText().toString());
            });
        }
    }

    private View showTransfer(FragmentActivity activity, long transferId) {
        TransferItemFragment fragment = new TransferItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        fragment.showItemId(transferId);
        idleMainLooper();
        TextView money = fragment.requireView().findViewById(R.id.money_text_view);
        for (int i = 0; i < 200 && TextUtils.isEmpty(money.getText()); i++) {
            idleMainLooper();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertFalse("the transfer never showed", TextUtils.isEmpty(money.getText()));
        return fragment.requireView();
    }

    private void idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle();
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

    private long insertTransfer(long fromWalletId, long fromMoney, long toWalletId, long toMoney) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Move")
                .date(DateUtils.getSQLDateTimeString(new Date()))
                .fromWalletId(fromWalletId)
                .toWalletId(toWalletId)
                .taxWalletId(fromWalletId)
                .fromMoney(fromMoney)
                .toMoney(toMoney)
                .taxMoney(0L)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values));
    }
}
