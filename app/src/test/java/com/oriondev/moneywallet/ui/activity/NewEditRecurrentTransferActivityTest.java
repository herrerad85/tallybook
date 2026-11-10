package com.oriondev.moneywallet.ui.activity;

import android.Manifest;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Parcel;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.CurrencyConverterPicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowActivity;

import java.util.Date;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Opens the recurrent transfer editor on a stored recurrence, against the real content provider
 * over a fresh database. Wallet A is EUR with two decimals and wallet B is JPY with none, so 10000
 * (100.00 EUR) against 15000 yen is a rate of 150.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditRecurrentTransferActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private Context mContext;
    private ContentResolver mResolver;
    private long mWalletA;
    private long mWalletB;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
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

    @Test
    public void aRateLineFromACurrencyWithoutASymbolStartsWithItsIsoCode() {
        long francs = insertWallet("Douala", "XAF");
        long dollars = insertWallet("Dollars", "USD");
        long recurrence = insertRecurrentTransfer(1000L, 60000L, francs, dollars);
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(recurrence))) {
            scenario.onActivity(activity -> {
                TextView rate = activity.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertTrue(rate.getText().toString().startsWith("XAF -> "));
                assertFalse(rate.getText().toString().contains("null"));
            });
        }
    }

    @Test
    public void aRateLineToACurrencyWithoutASymbolNamesItByItsIsoCode() {
        long dollars = insertWallet("Dollars", "USD");
        long francs = insertWallet("Douala", "XAF");
        long recurrence = insertRecurrentTransfer(1000L, 6000L, dollars, francs);
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(recurrence))) {
            scenario.onActivity(activity -> {
                TextView rate = activity.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertTrue(rate.getText().toString().startsWith("$ -> XAF: "));
                assertFalse(rate.getText().toString().contains("null"));
            });
        }
    }

    @Test
    @Config(sdk = 33)
    public void aNewRecurrenceAsksForTheNotificationPermissionOnce() {
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                ShadowActivity.PermissionsRequest request = shadowOf(activity).getLastRequestedPermission();
                assertNotNull(request);
                assertEquals(1, request.requestedPermissions.length);
                assertEquals(Manifest.permission.POST_NOTIFICATIONS, request.requestedPermissions[0]);
            });
        }
    }

    @Test
    @Config(sdk = 33)
    public void anEditedRecurrenceDoesNotAskForTheNotificationPermission() {
        long recurrence = insertRecurrentTransfer(10000L, 15000L, mWalletA, mWalletB);
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(editIntent(recurrence))) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    @Config(sdk = 33)
    public void aRecreatedNewRecurrenceDoesNotAskAgain() {
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNotNull(shadowOf(activity).getLastRequestedPermission()));
            scenario.recreate();
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    @Config(sdk = 32)
    public void aNewRecurrenceDoesNotAskBeforeAndroid13() {
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    @Config(sdk = 33)
    public void aSecondNewRecurrenceDoesNotAskAgain() {
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNotNull(shadowOf(activity).getLastRequestedPermission()));
        }
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getLastRequestedPermission()));
        }
    }

    @Test
    public void aNewRecurrenceOnTheTotalWalletOpensOnAWalletNamedInLowercaseBeforeTheCapitalizedOnes() {
        insertWallet("Zed", "EUR");
        long apple = insertWallet("apple", "EUR");
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity ->
                    assertEquals(apple, walletFromPicker(activity).getCurrentWallet().getId()));
        }
    }

    @Test
    public void aCrossCurrencyRecurrenceWithNoStoredRateIsRefusedWithADialog() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        assertNull(CurrencyManager.getExchangeRate(CurrencyManager.getCurrency("EUR"),
                CurrencyManager.getCurrency("JPY")));
        int before = countRecurrences();
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                walletToPicker(activity).onWalletSelected(new Wallet(mWalletB, "Tokyo",
                        IconLoader.parse(ICON), CurrencyManager.getCurrency("JPY"), 0L, 0L));
                moneyPicker(activity).setMoney(10000L);
                save(activity);
                assertFalse(activity.isFinishing());
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull(dialog);
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.error_input_missing_exchange_rate),
                        message.getText().toString());
            });
        }
        assertEquals(before, countRecurrences());
    }

    @Test
    public void switchingTheSourceToTheDestinationCurrencyWithNoStoredRateSavesTheSameAmount() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        CurrencyUnit yen = CurrencyManager.getCurrency("JPY");
        assertNull(CurrencyManager.getExchangeRate(CurrencyManager.getCurrency("EUR"), yen));
        long osaka = insertWallet("Osaka", "JPY");
        int before = countRecurrences();
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                walletToPicker(activity).onWalletSelected(new Wallet(mWalletB, "Tokyo",
                        IconLoader.parse(ICON), yen, 0L, 0L));
                walletFromPicker(activity).onWalletSelected(new Wallet(osaka, "Osaka",
                        IconLoader.parse(ICON), yen, 0L, 0L));
                moneyPicker(activity).setMoney(10000L);
                save(activity);
                assertNull(ShadowDialog.getLatestDialog());
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countRecurrences());
        Cursor row = newestRecurrence();
        assertEquals(osaka, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_ID)));
        assertEquals(mWalletB, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.WALLET_TO_ID)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_FROM)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_TO)));
        row.close();
    }

    @Test
    public void aRecurrenceWithNoSourceWalletShowsNoExchangeRateDialog() {
        archiveWallet(mWalletA);
        archiveWallet(mWalletB);
        int before = countRecurrences();
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                assertFalse(walletFromPicker(activity).isSelected());
                walletToPicker(activity).onWalletSelected(new Wallet(mWalletB, "Tokyo",
                        IconLoader.parse(ICON), CurrencyManager.getCurrency("JPY"), 0L, 0L));
                moneyPicker(activity).setMoney(10000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.wallet_from_edit_text).validate());
            });
        }
        assertEquals(before, countRecurrences());
    }

    @Test
    public void aRecurrenceWithNoDestinationWalletShowsNoExchangeRateDialog() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countRecurrences();
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(mWalletA, walletFromPicker(activity).getCurrentWallet().getId());
                assertFalse(walletToPicker(activity).isSelected());
                moneyPicker(activity).setMoney(10000L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertNull(ShadowDialog.getLatestDialog());
                assertFalse(field(activity, R.id.wallet_to_edit_text).validate());
            });
        }
        assertEquals(before, countRecurrences());
    }

    @Test
    public void aCrossCurrencyRecurrenceWithARateSetInTheDialogIsSaved() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countRecurrences();
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                walletToPicker(activity).onWalletSelected(new Wallet(mWalletB, "Tokyo",
                        IconLoader.parse(ICON), CurrencyManager.getCurrency("JPY"), 0L, 0L));
                converterPicker(activity).onExchangeRateChanged(0.5D);
                moneyPicker(activity).setMoney(10000L);
                save(activity);
                assertNull(ShadowDialog.getLatestDialog());
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countRecurrences());
        Cursor row = newestRecurrence();
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_ID)));
        assertEquals(mWalletB, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.WALLET_TO_ID)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_FROM)));
        assertEquals((10000L / 100L) / 2L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_TO)));
        row.close();
    }

    @Test
    public void switchingTheSourceToTheDestinationCurrencyDropsATypedRate() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        CurrencyUnit yen = CurrencyManager.getCurrency("JPY");
        long osaka = insertWallet("Osaka", "JPY");
        int before = countRecurrences();
        try (ActivityScenario<NewEditRecurrentTransferActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                walletToPicker(activity).onWalletSelected(new Wallet(mWalletB, "Tokyo",
                        IconLoader.parse(ICON), yen, 0L, 0L));
                converterPicker(activity).onExchangeRateChanged(0.5D);
                walletFromPicker(activity).onWalletSelected(new Wallet(osaka, "Osaka",
                        IconLoader.parse(ICON), yen, 0L, 0L));
                moneyPicker(activity).setMoney(10000L);
                save(activity);
                assertNull(ShadowDialog.getLatestDialog());
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countRecurrences());
        Cursor row = newestRecurrence();
        assertEquals(osaka, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_ID)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_FROM)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_TO)));
        row.close();
    }

    @Test
    public void aProcessDeathOnAnEditedRecurrenceKeepsARateThatNoStoredRateMatches() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        assertNull(CurrencyManager.getExchangeRate(CurrencyManager.getCurrency("USD"),
                CurrencyManager.getCurrency("JPY")));
        long dollars = insertWallet("Dollars", "USD");
        long recurrence = insertRecurrentTransfer(10000L, 5000L, dollars, mWalletB);
        int before = countRecurrences();
        Intent intent = editIntent(recurrence);
        ActivityController<NewEditRecurrentTransferActivity> opened = Robolectric.buildActivity(NewEditRecurrentTransferActivity.class, intent).setup();
        try (ActivityController<NewEditRecurrentTransferActivity> restored = afterProcessDeath(opened, intent)) {
            NewEditRecurrentTransferActivity activity = restored.get();
            save(activity);
            assertNull(ShadowDialog.getLatestDialog());
            assertTrue(activity.isFinishing());
        }
        assertEquals(before, countRecurrences());
        Cursor row = newestRecurrence();
        assertEquals(dollars, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.WALLET_FROM_ID)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_FROM)));
        assertEquals(5000L, row.getLong(row.getColumnIndex(Contract.RecurrentTransfer.MONEY_TO)));
        row.close();
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

    private void archiveWallet(long walletId) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.ARCHIVED, true);
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId),
                values, null, null);
    }

    private static Intent newIntent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditRecurrentTransferActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
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

    private static MoneyPicker moneyPicker(NewEditRecurrentTransferActivity activity) {
        return (MoneyPicker) activity.getSupportFragmentManager()
                .findFragmentByTag("NewEditRecurrentTransferActivity::Tag::MoneyPicker");
    }

    private static WalletPicker walletToPicker(NewEditRecurrentTransferActivity activity) {
        return (WalletPicker) activity.getSupportFragmentManager()
                .findFragmentByTag("NewEditRecurrentTransferActivity::Tag::WalletToPicker");
    }

    private static WalletPicker walletFromPicker(NewEditRecurrentTransferActivity activity) {
        return (WalletPicker) activity.getSupportFragmentManager()
                .findFragmentByTag("NewEditRecurrentTransferActivity::Tag::WalletFromPicker");
    }

    private static CurrencyConverterPicker converterPicker(NewEditRecurrentTransferActivity activity) {
        return (CurrencyConverterPicker) activity.getSupportFragmentManager()
                .findFragmentByTag("NewEditRecurrentTransferActivity::Tag::ConverterPicker");
    }

    private static MaterialEditText field(NewEditRecurrentTransferActivity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    private static void save(NewEditRecurrentTransferActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        toolbar.getMenu().performIdentifierAction(R.id.action_save_changes, 0);
    }

    private int countRecurrences() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_RECURRENT_TRANSFERS,
                new String[] {Contract.RecurrentTransfer.ID}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private Cursor newestRecurrence() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_RECURRENT_TRANSFERS,
                new String[] {Contract.RecurrentTransfer.WALLET_FROM_ID, Contract.RecurrentTransfer.WALLET_TO_ID,
                        Contract.RecurrentTransfer.MONEY_FROM, Contract.RecurrentTransfer.MONEY_TO},
                null, null, Contract.RecurrentTransfer.ID + " DESC");
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private static ActivityController<NewEditRecurrentTransferActivity> afterProcessDeath(
            ActivityController<NewEditRecurrentTransferActivity> controller, Intent intent) {
        Bundle state = new Bundle();
        controller.pause().saveInstanceState(state).stop().destroy();
        return Robolectric.buildActivity(NewEditRecurrentTransferActivity.class, intent).setup(throughAParcel(state));
    }

    private static Bundle throughAParcel(Bundle state) {
        Parcel parcel = Parcel.obtain();
        try {
            state.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            Bundle copy = Bundle.CREATOR.createFromParcel(parcel);
            copy.setClassLoader(NewEditRecurrentTransferActivityTest.class.getClassLoader());
            return copy;
        } finally {
            parcel.recycle();
        }
    }
}
