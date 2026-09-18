package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Event;
import com.oriondev.moneywallet.model.MoneyScale;
import com.oriondev.moneywallet.model.Person;
import com.oriondev.moneywallet.model.Place;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.AttachmentPicker;
import com.oriondev.moneywallet.picker.CurrencyConverterPicker;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.picker.EventPicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.picker.PersonPicker;
import com.oriondev.moneywallet.picker.PlacePicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

/**
 * Drives the real transfer editor on the JVM, against the real content provider over a fresh
 * database, and reads back through the provider what it wrote. The cases cover the call sites
 * where the two amounts, the three wallet ids and the exchange rate are handed over, which is
 * where a transposed argument or a dropped scale correction compiles and ships, and the fields
 * the screen puts those numbers on, which is where a swapped symbol or a missing refresh shows.
 *
 * Amounts are minor units. Wallet A is EUR with two decimals, wallet B is JPY with none and
 * wallet C is EUR again, so a rate of 150 turns 10000 (100.00 EUR) into 15000 yen.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditTransferActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    // a stored date well in the past, so a save that stamps the row with the current time is a
    // different string and the toast for a transfer dated ahead never fires. The seconds are non
    // zero, so a save that drops them down to the minute is a different string too
    private static final String FIXTURE_DATE = "2019-04-05 14:30:27";

    // a second fixed past date, written by one case only, so a transaction row carrying it is a
    // row that case moved and can be told apart from every other row in the table
    private static final String MOVED_DATE = "2020-11-22 09:15:43";

    private static final long DAY = 24L * 60L * 60L * 1000L;

    private static final String CONVERTER_PICKER = "NewEditTransferModelActivity::Tag::ConverterPicker";
    private static final String MONEY_PICKER = "NewEditTransferModelActivity::Tag::MoneyPicker";
    private static final String DATETIME_PICKER = "NewEditTransferModelActivity::Tag::DateTimePicker";
    private static final String WALLET_FROM_PICKER = "NewEditTransferModelActivity::Tag::WalletFromPicker";
    private static final String WALLET_TO_PICKER = "NewEditTransferModelActivity::Tag::WalletToPicker";
    private static final String TAX_PICKER = "NewEditTransferModelActivity::Tag::TaxPicker";
    private static final String EVENT_PICKER = "NewEditTransferModelActivity::Tag::EventPicker";
    private static final String PLACE_PICKER = "NewEditTransferModelActivity::Tag::PlacePicker";
    private static final String PERSON_PICKER = "NewEditTransferModelActivity::Tag::PersonPicker";
    private static final String ATTACHMENT_PICKER = "NewEditTransferModelActivity::Tag::AttachmentPicker";

    private Context mContext;
    private ContentResolver mResolver;
    private long mUnusedWallet;
    private long mWalletA;
    private long mWalletB;
    private long mWalletC;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        // one throwaway wallet takes id 1, so the first wallet row is never the current wallet
        mUnusedWallet = insertWallet("Unused", "EUR");
        mWalletA = insertWallet("Cash", "EUR");
        mWalletB = insertWallet("Tokyo", "JPY");
        mWalletC = insertWallet("Bank", "EUR");
        // a different number of throwaway rows per table, so the first person, place, attachment,
        // event and model a case makes are ids 6, 7, 9, 10 and 5 and none of them equals a wallet
        // id, which are 1 to 4. An id read off the wrong column is then a different number
        for (int i = 0; i < 5; i++) {
            insertPerson("Unused");
        }
        for (int i = 0; i < 6; i++) {
            insertPlace();
        }
        for (int i = 0; i < 8; i++) {
            insertAttachment();
        }
        for (int i = 0; i < 9; i++) {
            insertEvent();
        }
        // the throwaway models carry no place and no event, so they add nothing to those two tables
        for (int i = 0; i < 4; i++) {
            insertModel(1L, 1L, mWalletA, mWalletC);
        }
    }

    @Test
    public void anEditedCrossScaleTransferKeepsEveryStoredFieldWhenNothingIsTouched() {
        long place = insertPlace();
        long event = insertEvent();
        // 100.00 EUR at a rate of 150 is 15000 of a currency that holds no decimals
        long transfer = insertTransfer(mWalletA, mWalletB, 10000L, 15000L, 250L,
                "Wire fee included", place, event, true, false, null, null);
        int before = countTransfers();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(transfer))) {
            scenario.onActivity(activity -> {
                assertEquals("Stored transfer", fieldText(activity, R.id.description_edit_text));
                assertEquals(10000L, moneyPicker(activity).getCurrentMoney());
                assertEquals(250L, taxPicker(activity).getCurrentMoney());
                assertEquals("Wire fee included", fieldText(activity, R.id.note_edit_text));
                assertTrue(checkBox(activity, R.id.confirmed_checkbox).isChecked());
                assertFalse(checkBox(activity, R.id.count_in_total_checkbox).isChecked());
                assertEquals("Market", fieldText(activity, R.id.place_edit_text));
                assertEquals("Fair", fieldText(activity, R.id.event_edit_text));
                assertEquals(place, placePicker(activity).getCurrentPlace().getId());
                assertEquals(event, eventPicker(activity).getCurrentEvent().getId());
                assertEquals("Cash", fieldText(activity, R.id.wallet_from_edit_text));
                assertEquals("Tokyo", fieldText(activity, R.id.wallet_to_edit_text));
                assertFalse(fieldText(activity, R.id.date_edit_text).isEmpty());
                assertFalse(fieldText(activity, R.id.time_edit_text).isEmpty());
                TextView rate = activity.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals(euroToYenRateLine(150D), rate.getText().toString());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countTransfers());
        Cursor row = transferRow(transfer);
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)));
        assertEquals(mWalletB, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY)));
        assertEquals(15000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_MONEY)));
        assertEquals(250L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID)));
        assertEquals(FIXTURE_DATE, row.getString(row.getColumnIndex(Contract.Transfer.DATE)));
        assertEquals("Stored transfer", row.getString(row.getColumnIndex(Contract.Transfer.DESCRIPTION)));
        assertEquals("Wire fee included", row.getString(row.getColumnIndex(Contract.Transfer.NOTE)));
        assertEquals(place, row.getLong(row.getColumnIndex(Contract.Transfer.PLACE_ID)));
        assertEquals(event, row.getLong(row.getColumnIndex(Contract.Transfer.EVENT_ID)));
        assertEquals(1, row.getInt(row.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(0, row.getInt(row.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        row.close();
        // the transfer is confirmed and dated in the past but is not counted in the total, so the
        // three transaction rows it writes are left out of both wallet balances
        assertEquals(0L, walletTotal(mWalletA));
        assertEquals(0L, walletTotal(mWalletB));
    }

    @Test
    public void anEditedSameCurrencyTransferWritesTheRetypedAmountTheDescriptionAndTheNewFee() {
        long transfer = insertTransfer(mWalletA, mWalletC, 25000L, 25000L, 0L);
        int before = countTransfers();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(transfer))) {
            scenario.onActivity(activity -> {
                assertEquals(25000L, moneyPicker(activity).getCurrentMoney());
                assertEquals(0L, taxPicker(activity).getCurrentMoney());
                moneyPicker(activity).setMoney(30000L);
                taxPicker(activity).setMoney(700L);
                // the throwaway wallet holds the same currency as wallet C, so the rate the edit
                // branch derived from the two stored amounts is still the rate between the two
                // wallets on screen and the converted amount is the typed one
                walletToPicker(activity).onWalletSelected(new Wallet(mUnusedWallet, "Unused",
                        IconLoader.parse(ICON), CurrencyManager.getCurrency("EUR"), 0L, 0L));
                assertEquals("Unused", fieldText(activity, R.id.wallet_to_edit_text));
                field(activity, R.id.description_edit_text).setText("Renamed");
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countTransfers());
        Cursor row = transferRow(transfer);
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)));
        assertEquals(mUnusedWallet, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)));
        assertEquals(30000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY)));
        assertEquals(30000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_MONEY)));
        // the stored transfer carried no fee, so the update has to add a tax leg it never had
        assertEquals(700L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID)));
        assertEquals("Renamed", row.getString(row.getColumnIndex(Contract.Transfer.DESCRIPTION)));
        assertEquals(FIXTURE_DATE, row.getString(row.getColumnIndex(Contract.Transfer.DATE)));
        assertEquals(1, row.getInt(row.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(1, row.getInt(row.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        row.close();
        // the wallet balance is a sum over the transaction rows, not over the transfer row, and it
        // reads the date off those rows, so each of the three has to carry the transfer's own date
        assertEquals(Arrays.asList(FIXTURE_DATE, FIXTURE_DATE, FIXTURE_DATE), transactionDates());
        // confirmed, counted and dated in the past, so the source wallet moves by the amount and
        // the fee and the new destination by the amount, while wallet C, which the stored transfer
        // paid into, is left with nothing on it
        assertEquals(-30700L, walletTotal(mWalletA));
        assertEquals(0L, walletTotal(mWalletC));
        assertEquals(30000L, walletTotal(mUnusedWallet));
    }

    @Test
    public void aNewTransferOpensOnTheCurrentWalletAndIsRefusedWithNoDestination() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        int before = countTransfers();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(newItemIntent(NewEditTransferActivity.TYPE_STANDARD))) {
            scenario.onActivity(activity -> {
                assertEquals(mWalletA, walletFromPicker(activity).getCurrentWallet().getId());
                assertEquals("Cash", fieldText(activity, R.id.wallet_from_edit_text));
                assertFalse(walletToPicker(activity).isSelected());
                assertEquals("", fieldText(activity, R.id.wallet_to_edit_text));
                assertEquals(0L, moneyPicker(activity).getCurrentMoney());
                save(activity);
                assertFalse(activity.isFinishing());
            });
        }
        assertEquals(before, countTransfers());
    }

    @Test
    public void aNewTransferOnTheTotalWalletOpensOnTheFirstWalletRow() {
        PreferenceManager.setCurrentWallet(mContext, PreferenceManager.TOTAL_WALLET_ID);
        long first = firstWalletRow();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(newItemIntent(NewEditTransferActivity.TYPE_STANDARD))) {
            scenario.onActivity(activity -> {
                assertEquals(first, walletFromPicker(activity).getCurrentWallet().getId());
                assertEquals(mUnusedWallet, walletFromPicker(activity).getCurrentWallet().getId());
            });
        }
    }

    @Test
    public void aSavedTransferCarriesTheWalletIdsTheTaxAndTheConvertedAmount() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        long place = insertPlace();
        long event = insertEvent();
        int before = countTransfers();
        long now = System.currentTimeMillis();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(newItemIntent(NewEditTransferActivity.TYPE_STANDARD))) {
            scenario.onActivity(activity -> {
                CurrencyUnit euro = CurrencyManager.getCurrency("EUR");
                CurrencyUnit yen = CurrencyManager.getCurrency("JPY");
                assertEquals(euro, taxPicker(activity).getCurrentCurrency());
                walletToPicker(activity).onWalletSelected(new Wallet(mWalletB, "Tokyo",
                        IconLoader.parse(ICON), yen, 0L, 0L));
                converterPicker(activity).onExchangeRateChanged(150D);
                moneyPicker(activity).setMoney(12345L);
                assertEquals(euro, moneyPicker(activity).getCurrentCurrency());
                assertEquals(euro.getSymbol(), textOf(activity, R.id.currency_text_view));
                assertEquals(MoneyFormatter.getInstance().getNotTintedString(euro, 12345L,
                                MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN),
                        textOf(activity, R.id.money_text_view));
                assertEquals(MoneyFormatter.getInstance().getNotTintedString(yen, 18517L),
                        textOf(activity, R.id.secondary_money_text_view));
                assertEquals(euroToYenRateLine(150D),
                        textOf(activity, R.id.exchange_rate_text_view));
                taxPicker(activity).setMoney(500L);
                assertEquals(MoneyFormatter.getInstance().getNotTintedString(euro, 500L),
                        fieldText(activity, R.id.money_tax_edit_text));
                field(activity, R.id.description_edit_text).setText("Holiday money");
                field(activity, R.id.note_edit_text).setText("Bank counter");
                placePicker(activity).setCurrentPlace(new Place(place, "Market",
                        IconLoader.parse(ICON), null, null, null));
                eventPicker(activity).setCurrentEvent(new Event(event, "Fair",
                        IconLoader.parse(ICON), null, null));
                checkBox(activity, R.id.confirmed_checkbox).setChecked(false);
                checkBox(activity, R.id.count_in_total_checkbox).setChecked(true);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransfers());
        Cursor row = newestTransfer();
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)));
        assertEquals(mWalletB, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)));
        assertEquals(12345L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY)));
        // 123.45 EUR at 150 is 18517.5 yen, truncated toward zero by MoneyScale.convert
        assertEquals(18517L, MoneyScale.convert(12345L, 2, 0, 150D));
        assertEquals(18517L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_MONEY)));
        assertEquals(500L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID)));
        assertEquals(0, row.getInt(row.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(1, row.getInt(row.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        assertEquals("Holiday money", row.getString(row.getColumnIndex(Contract.Transfer.DESCRIPTION)));
        assertEquals("Bank counter", row.getString(row.getColumnIndex(Contract.Transfer.NOTE)));
        assertEquals(place, row.getLong(row.getColumnIndex(Contract.Transfer.PLACE_ID)));
        assertEquals(event, row.getLong(row.getColumnIndex(Contract.Transfer.EVENT_ID)));
        assertDatedNow(row, now);
        String saved = row.getString(row.getColumnIndex(Contract.Transfer.DATE));
        row.close();
        // the balances are a sum over the three transaction rows the insert writes and they read
        // the date off those rows, so all three have to carry the date the transfer row carries
        assertEquals(3, legDatesMatching(saved));
        // the transfer is counted in the total but is not confirmed, so neither wallet moves
        assertEquals(0L, walletTotal(mWalletA));
        assertEquals(0L, walletTotal(mWalletB));
    }

    @Test
    public void aTransferPrefilledFromAModelKeepsTheModelsAmountsAndFlags() {
        long place = insertPlace();
        long event = insertEvent();
        long model = insertModel(10000L, 15000L, mWalletA, mWalletB, 700L, true, false, place, event);
        Intent intent = newItemIntent(NewEditTransferActivity.TYPE_MODEL);
        intent.putExtra(NewEditTransferActivity.MODEL_ID, model);
        int before = countTransfers();
        try (ActivityScenario<NewEditTransferActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals("Rent share", fieldText(activity, R.id.description_edit_text));
                assertEquals("Template note", fieldText(activity, R.id.note_edit_text));
                assertEquals("Market", fieldText(activity, R.id.place_edit_text));
                assertEquals("Fair", fieldText(activity, R.id.event_edit_text));
                assertEquals(place, placePicker(activity).getCurrentPlace().getId());
                assertEquals(event, eventPicker(activity).getCurrentEvent().getId());
                assertEquals(mWalletA, walletFromPicker(activity).getCurrentWallet().getId());
                assertEquals(mWalletB, walletToPicker(activity).getCurrentWallet().getId());
                assertEquals(CurrencyManager.getCurrency("EUR"),
                        moneyPicker(activity).getCurrentCurrency());
                assertEquals(10000L, moneyPicker(activity).getCurrentMoney());
                assertEquals(700L, taxPicker(activity).getCurrentMoney());
                assertTrue(checkBox(activity, R.id.confirmed_checkbox).isChecked());
                assertFalse(checkBox(activity, R.id.count_in_total_checkbox).isChecked());
                TextView rate = activity.findViewById(R.id.exchange_rate_text_view);
                assertEquals(View.VISIBLE, rate.getVisibility());
                assertEquals(euroToYenRateLine(150D), rate.getText().toString());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransfers());
        Cursor row = newestTransfer();
        assertEquals(15000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_MONEY)));
        assertEquals(700L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(1, row.getInt(row.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(0, row.getInt(row.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        assertEquals("Rent share", row.getString(row.getColumnIndex(Contract.Transfer.DESCRIPTION)));
        assertEquals("Template note", row.getString(row.getColumnIndex(Contract.Transfer.NOTE)));
        assertEquals(place, row.getLong(row.getColumnIndex(Contract.Transfer.PLACE_ID)));
        assertEquals(event, row.getLong(row.getColumnIndex(Contract.Transfer.EVENT_ID)));
        row.close();
        // the model is confirmed but is not counted in the total, so neither wallet moves
        assertEquals(0L, walletTotal(mWalletA));
        assertEquals(0L, walletTotal(mWalletB));
        // the other pairing of the two flags, opened only, so neither checkbox the branch writes
        // can be a constant and the two columns cannot be read into each other
        long swapped = insertModel(10000L, 15000L, mWalletA, mWalletB, 700L, false, true, null, null);
        Intent swappedIntent = newItemIntent(NewEditTransferActivity.TYPE_MODEL);
        swappedIntent.putExtra(NewEditTransferActivity.MODEL_ID, swapped);
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(swappedIntent)) {
            scenario.onActivity(activity -> {
                assertFalse(checkBox(activity, R.id.confirmed_checkbox).isChecked());
                assertTrue(checkBox(activity, R.id.count_in_total_checkbox).isChecked());
            });
        }
    }

    @Test
    public void insertTransferFromModelCopiesTheWalletsTheTaxAndTheFlags() {
        long place = insertPlace();
        long event = insertEvent();
        long model = insertModel(10000L, 15000L, mWalletA, mWalletB, 700L, false, false, place, event);
        int before = countTransfers();
        long now = System.currentTimeMillis();
        Uri uri = NewEditTransferActivity.insertTransferFromModel(mContext, model);
        assertNotNull(uri);
        assertEquals(before + 1, countTransfers());
        Cursor row = transferRow(ContentUris.parseId(uri));
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)));
        assertEquals(mWalletB, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)));
        assertEquals(10000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY)));
        assertEquals(15000L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_MONEY)));
        assertEquals(700L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(mWalletA, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID)));
        assertEquals(0, row.getInt(row.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(0, row.getInt(row.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        assertEquals("Rent share", row.getString(row.getColumnIndex(Contract.Transfer.DESCRIPTION)));
        assertEquals("Template note", row.getString(row.getColumnIndex(Contract.Transfer.NOTE)));
        assertEquals(place, row.getLong(row.getColumnIndex(Contract.Transfer.PLACE_ID)));
        assertEquals(event, row.getLong(row.getColumnIndex(Contract.Transfer.EVENT_ID)));
        assertDatedNow(row, now);
        String saved = row.getString(row.getColumnIndex(Contract.Transfer.DATE));
        row.close();
        // the same three rows, written by the same insert, on the path that takes no screen
        assertEquals(3, legDatesMatching(saved));
        // neither flag is set on the first model, so nothing it writes reaches a wallet balance
        assertEquals(0L, walletTotal(mWalletA));
        assertEquals(0L, walletTotal(mWalletB));
        // a second model confirmed but not counted, so a constant on either column is visible
        long walletABeforeConfirmed = walletTotal(mWalletA);
        long walletBBeforeConfirmed = walletTotal(mWalletB);
        long confirmedOnly = insertModel(10000L, 15000L, mWalletA, mWalletB, 700L, true, false, null, null);
        Uri confirmedUri = NewEditTransferActivity.insertTransferFromModel(mContext, confirmedOnly);
        assertNotNull(confirmedUri);
        assertEquals(before + 2, countTransfers());
        Cursor confirmedRow = transferRow(ContentUris.parseId(confirmedUri));
        assertEquals(1, confirmedRow.getInt(confirmedRow.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(0, confirmedRow.getInt(confirmedRow.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        confirmedRow.close();
        assertEquals(walletABeforeConfirmed, walletTotal(mWalletA));
        assertEquals(walletBBeforeConfirmed, walletTotal(mWalletB));
        // a third model with the two flags the other way round, so a swap is visible too
        long countedOnly = insertModel(10000L, 15000L, mWalletA, mWalletB, 700L, false, true, null, null);
        Uri countedUri = NewEditTransferActivity.insertTransferFromModel(mContext, countedOnly);
        assertNotNull(countedUri);
        assertEquals(before + 3, countTransfers());
        Cursor countedRow = transferRow(ContentUris.parseId(countedUri));
        assertEquals(0, countedRow.getInt(countedRow.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(1, countedRow.getInt(countedRow.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        countedRow.close();
        // a fourth model with both flags on, the one pairing whose insert reaches the balances
        long walletABeforeBoth = walletTotal(mWalletA);
        long walletBBeforeBoth = walletTotal(mWalletB);
        long both = insertModel(10000L, 15000L, mWalletA, mWalletB, 700L, true, true, null, null);
        Uri bothUri = NewEditTransferActivity.insertTransferFromModel(mContext, both);
        assertNotNull(bothUri);
        assertEquals(before + 4, countTransfers());
        Cursor bothRow = transferRow(ContentUris.parseId(bothUri));
        assertEquals(1, bothRow.getInt(bothRow.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(1, bothRow.getInt(bothRow.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        bothRow.close();
        // the source pays the amount and the fee, the destination takes the amount
        assertEquals(walletABeforeBoth - 10700L, walletTotal(mWalletA));
        assertEquals(walletBBeforeBoth + 15000L, walletTotal(mWalletB));
    }

    @Test
    public void anEditedTransferKeepsItsPeopleAndItsAttachment() {
        long alice = insertPerson("Alice");
        long bob = insertPerson("Bob");
        long attachment = insertAttachment();
        // the one stored transfer that is not confirmed, carries no fee and has neither a place nor
        // an event, so the editor has to put all four back the way it found them
        long transfer = insertTransfer(mWalletA, mWalletC, 25000L, 25000L, 0L, "", null, null,
                false, true, new long[] {alice, bob}, new long[] {attachment});
        int before = countTransfers();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(transfer))) {
            scenario.onActivity(activity -> {
                assertFalse(checkBox(activity, R.id.confirmed_checkbox).isChecked());
                assertTrue(checkBox(activity, R.id.count_in_total_checkbox).isChecked());
                assertEquals(Arrays.asList(alice, bob), idsOf(personPicker(activity).getCurrentPeople()));
                assertEquals("Alice, Bob", fieldText(activity, R.id.people_edit_text));
                assertTrue(attachmentIds(activity).contains(attachment));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before, countTransfers());
        assertEquals(Arrays.asList(alice, bob), linkedIds(transfer, "people", Contract.Person.ID));
        assertEquals(Arrays.asList(attachment),
                linkedIds(transfer, "attachments", Contract.Attachment.ID));
        Cursor row = transferRow(transfer);
        assertEquals(0, row.getInt(row.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(1, row.getInt(row.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Transfer.PLACE_ID)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Transfer.EVENT_ID)));
        row.close();
        // an unconfirmed transfer is left out of the total whatever its count in total flag says
        assertEquals(0L, walletTotal(mWalletA));
        assertEquals(0L, walletTotal(mWalletC));
    }

    @Test
    public void anEditRemovingTheFeeAndThePlaceDeletesTheTaxLegAndClearsThePlace() {
        long place = insertPlace();
        long event = insertEvent();
        long transfer = insertTransfer(mWalletA, mWalletB, 10000L, 15000L, 250L,
                "Wire fee included", place, event, true, true, null, null);
        int transfersBefore = countTransfers();
        int transactionsBefore = countTransactions();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(transfer))) {
            scenario.onActivity(activity -> {
                assertEquals(250L, taxPicker(activity).getCurrentMoney());
                assertEquals(place, placePicker(activity).getCurrentPlace().getId());
                taxPicker(activity).setMoney(0L);
                placePicker(activity).setCurrentPlace(null);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(transfersBefore, countTransfers());
        // the fee is one of the three transaction rows a transfer writes, so dropping it takes the
        // row away instead of leaving it behind at zero
        assertEquals(transactionsBefore - 1, countTransactions());
        Cursor row = transferRow(transfer);
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Transfer.PLACE_ID)));
        assertEquals(event, row.getLong(row.getColumnIndex(Contract.Transfer.EVENT_ID)));
        row.close();
        // confirmed, counted and dated in the past, and with no fee left to pay
        assertEquals(-10000L, walletTotal(mWalletA));
        assertEquals(15000L, walletTotal(mWalletB));
    }

    @Test
    public void anEditChangingTheWalletTheDateAndTheFeeCarriesAllThreeOntoTheTransactionRows() {
        long transfer = insertTransfer(mWalletA, mWalletB, 10000L, 15000L, 250L, "", null, null,
                true, true, null, null);
        int transfersBefore = countTransfers();
        int transactionsBefore = countTransactions();
        // confirmed, counted and dated in the past, so the stored transfer already moves both
        // wallets, the source by the amount and the fee
        assertEquals(-10250L, walletTotal(mWalletA));
        assertEquals(15000L, walletTotal(mWalletB));
        Date moved = DateUtils.getDateFromSQLDateTimeString(MOVED_DATE);
        assertNotNull(moved);
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(transfer))) {
            scenario.onActivity(activity -> {
                assertEquals(mWalletA, walletFromPicker(activity).getCurrentWallet().getId());
                // wallet C holds the same currency as wallet A, so the rate the edit branch derived
                // from the two stored amounts is still the rate between the two wallets on screen
                walletFromPicker(activity).onWalletSelected(new Wallet(mWalletC, "Bank",
                        IconLoader.parse(ICON), CurrencyManager.getCurrency("EUR"), 0L, 0L));
                assertEquals("Bank", fieldText(activity, R.id.wallet_from_edit_text));
                assertEquals(CurrencyManager.getCurrency("EUR"),
                        moneyPicker(activity).getCurrentCurrency());
                taxPicker(activity).setMoney(900L);
                dateTimePicker(activity).setCurrentDateTime(moved);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(transfersBefore, countTransfers());
        // the transfer already had a fee, so the three rows are rewritten where they stand and
        // none of them is added or taken away
        assertEquals(transactionsBefore, countTransactions());
        Cursor row = transferRow(transfer);
        assertEquals(mWalletC, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)));
        assertEquals(mWalletB, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)));
        assertEquals(mWalletC, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID)));
        assertEquals(900L, row.getLong(row.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(MOVED_DATE, row.getString(row.getColumnIndex(Contract.Transfer.DATE)));
        row.close();
        // this case is the only writer of the moved date, so a transaction row carrying it is one
        // of the three legs of this transfer, and all three have to carry it
        assertEquals(3, legDatesMatching(MOVED_DATE));
        List<String> expected = new ArrayList<>(Arrays.asList(
                leg(mWalletC, Contract.Direction.EXPENSE),
                leg(mWalletB, Contract.Direction.INCOME),
                leg(mWalletC, Contract.Direction.EXPENSE)));
        Collections.sort(expected);
        assertEquals(expected, legsOn(MOVED_DATE));
        // both legs the source pays moved off wallet A, which is left with nothing on it
        assertEquals(0L, walletTotal(mWalletA));
        assertEquals(-10900L, walletTotal(mWalletC));
        assertEquals(15000L, walletTotal(mWalletB));
    }

    @Test
    public void aRecreateKeepsTheTypedAmountTheDescriptionAndTheSourceWallet() {
        PreferenceManager.setCurrentWallet(mContext, mWalletA);
        AtomicReference<NewEditTransferActivity> first = new AtomicReference<>();
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(newItemIntent(NewEditTransferActivity.TYPE_STANDARD))) {
            scenario.onActivity(activity -> {
                first.set(activity);
                moneyPicker(activity).setMoney(7777L);
                field(activity, R.id.description_edit_text).setText("Moving money");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotSame(first.get(), activity);
                assertEquals(7777L, moneyPicker(activity).getCurrentMoney());
                assertEquals("Moving money", fieldText(activity, R.id.description_edit_text));
                assertEquals(mWalletA, walletFromPicker(activity).getCurrentWallet().getId());
            });
        }
    }

    @Test
    public void aRecreateOnAnEditedTransferKeepsTheRetypedValuesOverTheStoredRow() {
        long transfer = insertTransfer(mWalletA, mWalletB, 10000L, 15000L, 0L);
        try (ActivityScenario<NewEditTransferActivity> scenario =
                     ActivityScenario.launch(editIntent(transfer))) {
            scenario.onActivity(activity -> {
                field(activity, R.id.description_edit_text).setText("Retyped");
                moneyPicker(activity).setMoney(4321L);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Retyped", fieldText(activity, R.id.description_edit_text));
                assertEquals(4321L, moneyPicker(activity).getCurrentMoney());
                assertEquals(mWalletA, walletFromPicker(activity).getCurrentWallet().getId());
            });
        }
    }

    // fixtures

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

    private long insertPerson(String name) {
        ContentValues person = new ContentValues();
        person.put(Contract.Person.NAME, name);
        person.put(Contract.Person.ICON, ICON);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, person));
    }

    private long insertPlace() {
        ContentValues place = new ContentValues();
        place.put(Contract.Place.NAME, "Market");
        place.put(Contract.Place.ICON, ICON);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PLACES, place));
    }

    private long insertEvent() {
        ContentValues event = new ContentValues();
        event.put(Contract.Event.NAME, "Fair");
        event.put(Contract.Event.ICON, ICON);
        event.put(Contract.Event.START_DATE, DateUtils.getSQLDateString(daysFromNow(-30)));
        event.put(Contract.Event.END_DATE, DateUtils.getSQLDateString(daysFromNow(30)));
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_EVENTS, event));
    }

    private long insertAttachment() {
        ContentValues attachment = new ContentValues();
        attachment.put(Contract.Attachment.FILE, "receipt.jpg");
        attachment.put(Contract.Attachment.NAME, "receipt.jpg");
        attachment.put(Contract.Attachment.TYPE, "image/jpeg");
        attachment.put(Contract.Attachment.SIZE, 1234L);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, attachment));
    }

    private long insertModel(long moneyFrom, long moneyTo, long from, long to) {
        return insertModel(moneyFrom, moneyTo, from, to, 0L, true, true, null, null);
    }

    private long insertModel(long moneyFrom, long moneyTo, long from, long to, long tax,
                             boolean confirmed, boolean countInTotal, Long placeId, Long eventId) {
        ContentValues values = new ContentValues();
        values.put(Contract.TransferModel.DESCRIPTION, "Rent share");
        values.put(Contract.TransferModel.WALLET_FROM_ID, from);
        values.put(Contract.TransferModel.WALLET_TO_ID, to);
        values.put(Contract.TransferModel.MONEY_FROM, moneyFrom);
        values.put(Contract.TransferModel.MONEY_TO, moneyTo);
        values.put(Contract.TransferModel.MONEY_TAX, tax);
        values.put(Contract.TransferModel.NOTE, "Template note");
        values.put(Contract.TransferModel.EVENT_ID, eventId);
        values.put(Contract.TransferModel.PLACE_ID, placeId);
        values.put(Contract.TransferModel.CONFIRMED, confirmed);
        values.put(Contract.TransferModel.COUNT_IN_TOTAL, countInTotal);
        return ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_TRANSFER_MODELS, values));
    }

    private long insertTransfer(long from, long to, long moneyFrom, long moneyTo, long tax) {
        return insertTransfer(from, to, moneyFrom, moneyTo, tax, "", null, null, true, true,
                null, null);
    }

    private long insertTransfer(long from, long to, long moneyFrom, long moneyTo, long tax,
                                String note, Long placeId, Long eventId, boolean confirmed,
                                boolean countInTotal, long[] peopleIds, long[] attachmentIds) {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Stored transfer")
                .date(FIXTURE_DATE)
                .fromWalletId(from)
                .toWalletId(to)
                .taxWalletId(from)
                .fromMoney(moneyFrom)
                .toMoney(moneyTo)
                .taxMoney(tax)
                .note(note)
                .placeId(placeId)
                .eventId(eventId)
                .confirmed(confirmed)
                .countInTotal(countInTotal)
                .peopleIds(idList(peopleIds))
                .attachmentIds(idList(attachmentIds))
                .build();
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values));
    }

    private static String idList(long[] ids) {
        if (ids == null || ids.length == 0) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i != 0) {
                builder.append(",");
            }
            builder.append("<").append(ids[i]).append(">");
        }
        return builder.toString();
    }

    private static Date daysFromNow(int days) {
        return new Date(System.currentTimeMillis() + days * DAY);
    }

    // intents

    private static Intent newItemIntent(int type) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransferActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        intent.putExtra(NewEditTransferActivity.TYPE, type);
        return intent;
    }

    private static Intent editIntent(long transferId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransferActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, transferId);
        return intent;
    }

    // driving the screen

    private static CurrencyConverterPicker converterPicker(NewEditTransferActivity activity) {
        return (CurrencyConverterPicker) activity.getSupportFragmentManager()
                .findFragmentByTag(CONVERTER_PICKER);
    }

    private static MoneyPicker moneyPicker(NewEditTransferActivity activity) {
        return (MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(MONEY_PICKER);
    }

    private static MoneyPicker taxPicker(NewEditTransferActivity activity) {
        return (MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(TAX_PICKER);
    }

    private static DateTimePicker dateTimePicker(NewEditTransferActivity activity) {
        return (DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag(DATETIME_PICKER);
    }

    private static WalletPicker walletFromPicker(NewEditTransferActivity activity) {
        return (WalletPicker) activity.getSupportFragmentManager().findFragmentByTag(WALLET_FROM_PICKER);
    }

    private static WalletPicker walletToPicker(NewEditTransferActivity activity) {
        return (WalletPicker) activity.getSupportFragmentManager().findFragmentByTag(WALLET_TO_PICKER);
    }

    private static PlacePicker placePicker(NewEditTransferActivity activity) {
        return (PlacePicker) activity.getSupportFragmentManager().findFragmentByTag(PLACE_PICKER);
    }

    private static EventPicker eventPicker(NewEditTransferActivity activity) {
        return (EventPicker) activity.getSupportFragmentManager().findFragmentByTag(EVENT_PICKER);
    }

    private static PersonPicker personPicker(NewEditTransferActivity activity) {
        return (PersonPicker) activity.getSupportFragmentManager().findFragmentByTag(PERSON_PICKER);
    }

    private static AttachmentPicker attachmentPicker(NewEditTransferActivity activity) {
        return (AttachmentPicker) activity.getSupportFragmentManager().findFragmentByTag(ATTACHMENT_PICKER);
    }

    private static MaterialEditText field(NewEditTransferActivity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    private static String fieldText(NewEditTransferActivity activity, int viewId) {
        return field(activity, viewId).getTextAsString();
    }

    private static String textOf(NewEditTransferActivity activity, int viewId) {
        TextView textView = activity.findViewById(viewId);
        return textView.getText().toString();
    }

    private static CheckBox checkBox(NewEditTransferActivity activity, int viewId) {
        return activity.findViewById(viewId);
    }

    /**
     * The line the header carries above the amount when the two wallets hold different currencies,
     * built the way the activity builds it, for the EUR to JPY direction every case here uses.
     */
    private static String euroToYenRateLine(double rate) {
        return String.format(Locale.getDefault(), "%s -> %s: %.2f",
                CurrencyManager.getCurrency("EUR").getSymbol(),
                CurrencyManager.getCurrency("JPY").getSymbol(), rate);
    }

    /**
     * Saves through the toolbar menu, the way a tap on the check mark does, so the listener the
     * screen registers on the toolbar is part of what is under test.
     *
     * The dispatch answers false even after it has run the save: NewEditItemActivity.onMenuItemClick
     * calls onSaveChanges and then returns false for every item, Toolbar hands that answer back to
     * MenuItemImpl.invoke, and MenuBuilder.performItemAction returns it unchanged. What proves the
     * dispatch arrived is the row each case reads back afterwards.
     */
    private static void save(NewEditTransferActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        boolean handled = toolbar.getMenu().performIdentifierAction(R.id.action_save_changes, 0);
        assertFalse("the toolbar menu answered " + handled, handled);
    }

    private static List<Long> idsOf(Person[] people) {
        List<Long> ids = new ArrayList<>();
        for (Person person : people) {
            ids.add(person.getId());
        }
        return ids;
    }

    private static List<Long> attachmentIds(NewEditTransferActivity activity) {
        List<Long> ids = new ArrayList<>();
        for (Attachment attachment : attachmentPicker(activity).getCurrentAttachments()) {
            ids.add(attachment.getId());
        }
        return ids;
    }

    // reading back

    private int countTransfers() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSFERS,
                new String[] {Contract.Transfer.ID}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private int countTransactions() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private List<String> transactionDates() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.DATE}, null, null, null);
        List<String> dates = new ArrayList<>();
        while (cursor.moveToNext()) {
            dates.add(cursor.getString(0));
        }
        cursor.close();
        return dates;
    }

    /**
     * How many transaction rows carry one date. A transfer writes three of them and the balances
     * are summed over those rows, so a date that reached the transfer row and not its legs is a
     * date the balances never see.
     */
    private int legDatesMatching(String date) {
        int count = 0;
        for (String stored : transactionDates()) {
            if (date.equals(stored)) {
                count++;
            }
        }
        return count;
    }

    /**
     * The wallet id and the direction of every transaction row carrying one date, one string per
     * row and sorted, so three legs can be compared as a multiset without depending on the order
     * the provider hands them back.
     */
    private List<String> legsOn(String date) {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.DATE, Contract.Transaction.WALLET_ID,
                        Contract.Transaction.DIRECTION}, null, null, null);
        List<String> legs = new ArrayList<>();
        while (cursor.moveToNext()) {
            if (date.equals(cursor.getString(0))) {
                legs.add(leg(cursor.getLong(1), cursor.getInt(2)));
            }
        }
        cursor.close();
        Collections.sort(legs);
        return legs;
    }

    private static String leg(long walletId, int direction) {
        return "wallet " + walletId + " direction " + direction;
    }

    /**
     * The balance the provider reports for one wallet, which is a sum over the transaction rows
     * and not over the transfer row, taken through the same uri and the same column the editor
     * reads when it opens a new transfer on the current wallet.
     */
    private long walletTotal(long walletId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, walletId);
        Cursor cursor = mResolver.query(uri, new String[] {Contract.Wallet.TOTAL_MONEY},
                null, null, null);
        assertTrue(cursor.moveToFirst());
        long total = cursor.getLong(0);
        cursor.close();
        return total;
    }

    private long firstWalletRow() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_WALLETS,
                new String[] {Contract.Wallet.ID}, null, null, null);
        assertTrue(cursor.moveToFirst());
        long id = cursor.getLong(0);
        cursor.close();
        return id;
    }

    private static void assertDatedNow(Cursor row, long now) {
        Date date = DateUtils.getDateFromSQLDateTimeString(
                row.getString(row.getColumnIndex(Contract.Transfer.DATE)));
        assertNotNull(date);
        assertTrue("the row is dated " + date, Math.abs(date.getTime() - now) < 60000L);
    }

    private static String[] transferProjection() {
        return new String[] {
                Contract.Transfer.ID,
                Contract.Transfer.DESCRIPTION,
                Contract.Transfer.DATE,
                Contract.Transfer.TRANSACTION_FROM_WALLET_ID,
                Contract.Transfer.TRANSACTION_TO_WALLET_ID,
                Contract.Transfer.TRANSACTION_TAX_WALLET_ID,
                Contract.Transfer.TRANSACTION_FROM_MONEY,
                Contract.Transfer.TRANSACTION_TO_MONEY,
                Contract.Transfer.TRANSACTION_TAX_MONEY,
                Contract.Transfer.NOTE,
                Contract.Transfer.PLACE_ID,
                Contract.Transfer.EVENT_ID,
                Contract.Transfer.CONFIRMED,
                Contract.Transfer.COUNT_IN_TOTAL
        };
    }

    private Cursor transferRow(long transferId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, transferId);
        Cursor cursor = mResolver.query(uri, transferProjection(), null, null, null);
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private Cursor newestTransfer() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSFERS, transferProjection(),
                null, null, Contract.Transfer.ID + " DESC");
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private List<Long> linkedIds(long transferId, String path, String column) {
        Uri uri = Uri.withAppendedPath(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSFERS, transferId), path);
        Cursor cursor = mResolver.query(uri, new String[] {column}, null, null, null);
        List<Long> ids = new ArrayList<>();
        while (cursor.moveToNext()) {
            ids.add(cursor.getLong(0));
        }
        cursor.close();
        return ids;
    }

}
