package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.model.Person;
import com.oriondev.moneywallet.picker.AttachmentPicker;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.picker.PersonPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
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
import org.robolectric.shadows.ShadowDialog;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Drives the real editor on the JVM, against the real content provider over a fresh database,
 * and reads what it wrote. TransactionEditorRulesTest covers each rule on its own; this covers
 * the call sites, which is where a transposed argument or a dropped one compiles and ships.
 *
 * Amounts are minor units, so 1000 is 10.00. The saving fixture opens on 10.00, took a
 * confirmed 100.00 ten days ago, an unconfirmed 50.00 five days ago that must not count, and
 * gives back 30.00 ten days from now. From now onwards the lowest it reaches is 80.00; counted
 * from before the deposit it is 10.00.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditTransactionActivityTest {

    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String MONEY_PICKER = "NewEditTransactionActivity::Tag::MoneyPicker";
    private static final String DATETIME_PICKER = "NewEditTransactionActivity::Tag::DateTimePicker";
    private static final String CATEGORY_PICKER = "NewEditTransactionActivity::Tag::CategoryPicker";
    private static final String PERSON_PICKER = "NewEditTransactionActivity::Tag::PersonPicker";
    private static final String ATTACHMENT_PICKER = "NewEditTransactionActivity::Tag::AttachmentPicker";

    private ContentResolver mResolver;
    private long mEuroWallet;
    private long mDollarWallet;
    private long mSaving;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        // takes wallet id 1, so no wallet below shares its id with the saving, which is 1
        insertWallet("Unused", "EUR");
        mEuroWallet = insertWallet("Cash", "EUR");
        mDollarWallet = insertWallet("Bank", "USD");
        // a different number of unused rows per table, so the first place, event, person and
        // category a case makes are ids 4, 5, 6 and 7 and none of them equals a wallet, the
        // saving, a debt or a model, which are 1 to 3. An id read off the wrong column or from
        // the wrong object is then a different number, which an assertion on an id can see
        for (int i = 0; i < 3; i++) {
            insertPlace();
        }
        for (int i = 0; i < 4; i++) {
            insertEvent();
        }
        for (int i = 0; i < 5; i++) {
            insertPerson("Unused");
        }
        for (int i = 0; i < 6; i++) {
            insertCategory();
        }
        mSaving = insertSaving(1000L, 50000L, mEuroWallet);
        insertSavingRow(10000L, daysFromNow(-10), Contract.CategoryTag.SAVING_DEPOSIT, true, mEuroWallet);
        insertSavingRow(5000L, daysFromNow(-5), Contract.CategoryTag.SAVING_DEPOSIT, false, mEuroWallet);
        insertSavingRow(3000L, daysFromNow(10), Contract.CategoryTag.SAVING_WITHDRAW, true, mEuroWallet);
    }

    @Test
    public void aDepositOpensOnTheSavingsOwnWalletAndFilesTheRowAgainstTheSaving() {
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(savingIntent(NewEditTransactionActivity.SAVING_DEPOSIT))) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.wallet_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.category_edit_text).getVisibility());
                assertEquals("Cash", walletField(activity));
                // a deposit opens on nothing; only withdraw everything is prefilled
                assertEquals(0L, moneyPicker(activity).getCurrentMoney());
                moneyPicker(activity).setMoney(2500L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
        Cursor row = newestTransaction();
        assertEquals(2500L, row.getLong(row.getColumnIndex(Contract.Transaction.MONEY)));
        assertEquals(mEuroWallet, row.getLong(row.getColumnIndex(Contract.Transaction.WALLET_ID)));
        assertEquals(mSaving, row.getLong(row.getColumnIndex(Contract.Transaction.SAVING_ID)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Transaction.DEBT_ID)));
        assertEquals(NewEditTransactionActivity.TYPE_SAVING,
                row.getInt(row.getColumnIndex(Contract.Transaction.TYPE)));
        assertEquals(Contract.Direction.EXPENSE, row.getInt(row.getColumnIndex(Contract.Transaction.DIRECTION)));
        assertEquals(Contract.CategoryTag.SAVING_DEPOSIT,
                row.getString(row.getColumnIndex(Contract.Transaction.CATEGORY_TAG)));
        row.close();
    }

    @Test
    public void aWithdrawalIsFiledAsIncomeUnderTheWithdrawTag() {
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(100L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        // the fixture's own future withdrawal is the newest row until the editor writes one
        assertEquals(before + 1, countTransactions());
        Cursor row = newestTransaction();
        assertEquals(100L, row.getLong(row.getColumnIndex(Contract.Transaction.MONEY)));
        assertEquals(Contract.Direction.INCOME, row.getInt(row.getColumnIndex(Contract.Transaction.DIRECTION)));
        assertEquals(Contract.CategoryTag.SAVING_WITHDRAW,
                row.getString(row.getColumnIndex(Contract.Transaction.CATEGORY_TAG)));
        row.close();
    }

    @Test
    public void withdrawEverythingOpensOnTheLowestBalanceFromNowAndCompletesTheSaving() {
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(
                savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING))) {
            scenario.onActivity(activity -> {
                assertEquals(8000L, moneyPicker(activity).getCurrentMoney());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        Cursor row = newestTransaction();
        assertEquals(8000L, row.getLong(row.getColumnIndex(Contract.Transaction.MONEY)));
        assertEquals(Contract.Direction.INCOME, row.getInt(row.getColumnIndex(Contract.Transaction.DIRECTION)));
        row.close();
        assertTrue(savingIsComplete());
    }

    @Test
    public void aWithdrawalOverTheLowestBalanceIsRefusedAndOneAtItIsSaved() {
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(8001L);
                save(activity);
                assertFalse(activity.isFinishing());
                assertEquals(before, countTransactions());
                moneyPicker(activity).setMoney(8000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
        assertFalse(savingIsComplete());
    }

    @Test
    public void theCeilingIsCountedFromTheDateOnScreen() {
        int before = countTransactions();
        // from yesterday the lowest is still 8000, from before the deposit it is 1000
        Date onScreen = daysFromNow(-1);
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(2000L);
                dateTimePicker(activity).setCurrentDateTime(daysFromNow(-20));
                save(activity);
                assertFalse(activity.isFinishing());
                assertEquals(before, countTransactions());
                dateTimePicker(activity).setCurrentDateTime(onScreen);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
        Cursor row = newestTransaction();
        assertEquals("the row keeps the date on screen, not the moment it was saved",
                DateUtils.getSQLDateTimeString(onScreen),
                row.getString(row.getColumnIndex(Contract.Transaction.DATE)));
        row.close();
    }

    @Test
    public void anEditedWithdrawalIsNotHeldAgainstItself() {
        long row = insertSavingRow(5000L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, mEuroWallet);
        String storedDate = dateOf(row);
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(editIntent(row))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(7000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(7000L, moneyOf(row));
        assertEquals("an edit keeps the row's own date", storedDate, dateOf(row));
    }

    @Test
    public void aStoredWithdrawalOnASavingUnderZeroCanBeLoweredButNotRaised() {
        long saving = insertSaving(0L, 0L, mEuroWallet);
        long row = insertSavingRow(saving, 5000L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, mEuroWallet);
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(editIntent(row))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(5001L);
                save(activity);
                assertFalse(activity.isFinishing());
                // lowered, but moved to a date before the one it was stored on, is a new drain
                // on the day it moves across and is held to the ceiling, which is 0 here
                moneyPicker(activity).setMoney(4000L);
                dateTimePicker(activity).setCurrentDateTime(daysFromNow(-2));
                save(activity);
                assertFalse(activity.isFinishing());
                dateTimePicker(activity).setCurrentDateTime(daysFromNow(-1));
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(4000L, moneyOf(row));
    }

    @Test
    public void anAmountOfNothingIsNeverRefused() {
        long saving = insertSaving(0L, 0L, mEuroWallet);
        insertSavingRow(saving, 5000L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, mEuroWallet);
        Intent intent = savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW);
        intent.putExtra(NewEditTransactionActivity.SAVING_ID, saving);
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(1L);
                save(activity);
                assertFalse(activity.isFinishing());
                // the lowest this saving reaches is minus 5000 and the ceiling is held at
                // nothing, so the refusal names 0.00 and not a negative figure
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                TextView message = dialog.findViewById(android.R.id.message);
                assertEquals(activity.getString(R.string.error_saving_withdraw_over_balance,
                        MoneyFormatter.getInstance().getNotTintedString(
                                CurrencyManager.getCurrency("EUR"), 0L)),
                        message.getText().toString());
                moneyPicker(activity).setMoney(0L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
    }

    @Test
    public void theCeilingStepsAsideForARowHeldInAnotherCurrency() {
        long saving = insertSaving(0L, 0L, mEuroWallet);
        long row = insertSavingRow(saving, 100L, daysFromNow(-1), Contract.CategoryTag.SAVING_WITHDRAW, true, mDollarWallet);
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(editIntent(row))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(99999L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(99999L, moneyOf(row));
    }

    @Test
    public void aSavingIntentNamingNoActionClosesTheEditor() {
        Intent intent = savingIntent(0);
        intent.removeExtra(NewEditTransactionActivity.SAVING_ACTION);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            // finished from inside its own onCreate, so it is gone before the scenario can hand
            // it over
            assertEquals(Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    @Test
    public void aDebtPaymentHidesTheWalletFieldAndKeepsItHiddenAcrossARecreate() {
        long debt = insertDebt(mEuroWallet);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT);
        intent.putExtra(NewEditTransactionActivity.DEBT_ID, debt);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.wallet_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.category_edit_text).getVisibility());
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.wallet_edit_text).getVisibility());
                moneyPicker(activity).setMoney(700L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        Cursor row = newestTransaction();
        assertEquals(debt, row.getLong(row.getColumnIndex(Contract.Transaction.DEBT_ID)));
        assertTrue(row.isNull(row.getColumnIndex(Contract.Transaction.SAVING_ID)));
        assertEquals(NewEditTransactionActivity.TYPE_DEBT, row.getInt(row.getColumnIndex(Contract.Transaction.TYPE)));
        assertEquals(Contract.CategoryTag.PAID_DEBT, row.getString(row.getColumnIndex(Contract.Transaction.CATEGORY_TAG)));
        row.close();
    }

    @Test
    public void payingInFullOpensOnWhatIsLeftAndTheDebtsDescription() {
        long debt = insertPartlyPaidDebt();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(
                debtIntent(debt, NewEditTransactionActivity.DEBT_PAY_IN_FULL))) {
            scenario.onActivity(activity -> {
                assertEquals("10000 owed less the 2500 already paid, the figure the card shows",
                        7500L, moneyPicker(activity).getCurrentMoney());
                assertEquals("Rent", descriptionField(activity));
            });
        }
    }

    @Test
    public void payingOpensOnNothingAndTheDebtsDescription() {
        long debt = insertPartlyPaidDebt();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(
                debtIntent(debt, NewEditTransactionActivity.DEBT_PAY))) {
            scenario.onActivity(activity -> {
                assertEquals(0L, moneyPicker(activity).getCurrentMoney());
                assertEquals("Rent", descriptionField(activity));
            });
        }
    }

    @Test
    public void aDebtsOwnRowKeepsTheWalletField() {
        long debt = insertDebt(mEuroWallet);
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(editIntent(masterTransactionOf(debt)))) {
            scenario.onActivity(activity -> {
                assertEquals(View.VISIBLE, activity.findViewById(R.id.wallet_edit_text).getVisibility());
                assertEquals(View.GONE, activity.findViewById(R.id.category_edit_text).getVisibility());
            });
        }
    }

    @Test
    public void aDepositOverTheSavingsBalanceIsNeverRefused() {
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(savingIntent(NewEditTransactionActivity.SAVING_DEPOSIT))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(20000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
    }

    @Test
    public void aNewTransactionOpensOnTheWalletTheIntentNames() {
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        intent.putExtra(NewEditTransactionActivity.WALLET_ID, mDollarWallet);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertEquals("Bank", walletField(activity)));
        }
    }

    @Test
    public void aNewTransactionOpensOnTheCurrentWalletWhenTheIntentNamesNone() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(), mDollarWallet);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> assertEquals("Bank", walletField(activity)));
        }
    }

    @Test
    public void aNewTransactionOpenedFromAPersonHasThatPersonAttached() {
        ContentValues person = new ContentValues();
        person.put(Contract.Person.NAME, "Alice");
        person.put(Contract.Person.ICON, ICON);
        long personId = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, person));
        // no TYPE extra on purpose: the person screen, the launcher shortcut, the reminder and
        // the main screen's button all launch without one and rely on the default
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.PERSON_ID, personId);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals("Alice",
                        ((MaterialEditText) activity.findViewById(R.id.people_edit_text)).getTextAsString());
                // the id is what files the row; the label only shows the name
                assertEquals(personId, personPicker(activity).getCurrentPeople()[0].getId());
            });
        }
    }

    @Test
    public void aDebtLaunchWithNoDebtRowFilesUnderTheActionItNames() {
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT);
        intent.putExtra(NewEditTransactionActivity.DEBT_ID, 999L);
        intent.putExtra(NewEditTransactionActivity.DEBT_ACTION, NewEditTransactionActivity.DEBT_RECEIVE);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            // the action picks the paid credit category, which makes this a payment and hides
            // the wallet field; a debt row would have named the kind itself. Both debt tags
            // hide the field, so the category is what tells the two actions apart
            scenario.onActivity(activity -> {
                assertEquals(View.GONE, activity.findViewById(R.id.wallet_edit_text).getVisibility());
                assertEquals(Contract.CategoryTag.PAID_CREDIT,
                        categoryPicker(activity).getCurrentCategory().getTag());
            });
        }
    }

    @Test
    public void withdrawEverythingOnASavingUnderZeroOpensOnNothingAndWritesNothingNegative() {
        long saving = insertSaving(0L, 0L, mEuroWallet);
        insertSavingRow(saving, 5000L, daysFromNow(1), Contract.CategoryTag.SAVING_WITHDRAW, true, mEuroWallet);
        Intent intent = savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING);
        intent.putExtra(NewEditTransactionActivity.SAVING_ID, saving);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                // the lowest from now is minus 5000; the prefill is held at nothing
                assertEquals(0L, moneyPicker(activity).getCurrentMoney());
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        Cursor row = newestTransaction();
        assertEquals(0L, row.getLong(row.getColumnIndex(Contract.Transaction.MONEY)));
        row.close();
    }

    @Test
    public void aNewTransactionOnTheTotalWalletStillOpensOnAWallet() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(),
                PreferenceManager.TOTAL_WALLET_ID);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            // the total wallet is every wallet, so the editor takes the first one
            scenario.onActivity(activity -> assertEquals("Unused", walletField(activity)));
        }
    }

    @Test
    public void withdrawEverythingOffersTheWholeBalanceOnASavingPastItsTarget() {
        long saving = insertSaving(0L, 1000L, mEuroWallet);
        insertSavingRow(saving, 5000L, daysFromNow(-10), Contract.CategoryTag.SAVING_DEPOSIT, true, mEuroWallet);
        Intent intent = savingIntent(NewEditTransactionActivity.SAVING_WITHDRAW_EVERYTHING);
        intent.putExtra(NewEditTransactionActivity.SAVING_ID, saving);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            // the target is 1000 and the saving holds 5000; emptying it means all of it
            scenario.onActivity(activity -> assertEquals(5000L, moneyPicker(activity).getCurrentMoney()));
        }
    }

    @Test
    public void aDebtPaymentCarriesTheDebtsOwnPerson() {
        ContentValues person = new ContentValues();
        person.put(Contract.Person.NAME, "Bob");
        person.put(Contract.Person.ICON, ICON);
        long personId = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, person));
        long debt = insertDebt(mEuroWallet, personId);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT);
        intent.putExtra(NewEditTransactionActivity.DEBT_ID, debt);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity ->
                    assertEquals(personId, personPicker(activity).getCurrentPeople()[0].getId()));
        }
    }

    @Test
    public void aSavedStandardTransactionCarriesTheWalletAndCategoryIds() {
        long categoryId = insertCategory();
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        intent.putExtra(NewEditTransactionActivity.WALLET_ID, mDollarWallet);
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                categoryPicker(activity).setCategory(new Category(categoryId, "Groceries",
                        IconLoader.parse(ICON), Contract.CategoryType.EXPENSE, null));
                moneyPicker(activity).setMoney(700L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        // the name on screen is not the id on the row, and a row filed against a wallet or a
        // category that does not exist is dropped by the join with the editor reporting success
        assertEquals(before + 1, countTransactions());
        Cursor row = newestTransaction();
        assertEquals(mDollarWallet, row.getLong(row.getColumnIndex(Contract.Transaction.WALLET_ID)));
        assertEquals(categoryId, row.getLong(row.getColumnIndex(Contract.Transaction.CATEGORY_ID)));
        row.close();
    }

    @Test
    public void aSavedModelTransactionCarriesTheModelsIds() {
        long categoryId = insertCategory();
        long placeId = insertPlace();
        long eventId = insertEvent();
        long model = insertModel(4200L, mDollarWallet, categoryId, placeId, eventId);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL);
        intent.putExtra(NewEditTransactionActivity.MODEL_ID, model);
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
        Cursor row = newestTransaction();
        assertEquals(4200L, row.getLong(row.getColumnIndex(Contract.Transaction.MONEY)));
        assertEquals(mDollarWallet, row.getLong(row.getColumnIndex(Contract.Transaction.WALLET_ID)));
        assertEquals(categoryId, row.getLong(row.getColumnIndex(Contract.Transaction.CATEGORY_ID)));
        assertEquals(placeId, row.getLong(row.getColumnIndex(Contract.Transaction.PLACE_ID)));
        assertEquals(eventId, row.getLong(row.getColumnIndex(Contract.Transaction.EVENT_ID)));
        row.close();
    }

    @Test
    public void aDebtPaymentCarriesTheDebtsOwnPlace() {
        long placeId = insertPlace();
        long debt = insertDebt(mEuroWallet, null, placeId);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT);
        intent.putExtra(NewEditTransactionActivity.DEBT_ID, debt);
        int before = countTransactions();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(300L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(before + 1, countTransactions());
        Cursor row = newestTransaction();
        assertEquals(placeId, row.getLong(row.getColumnIndex(Contract.Transaction.PLACE_ID)));
        row.close();
    }

    @Test
    public void aCopyOpensOnTheOriginalsValuesWithoutItsAttachments() {
        long categoryId = insertCategory();
        long attachmentId = insertAttachment();
        ContentValues original = new ContentValues();
        original.put(Contract.Transaction.MONEY, 1234L);
        original.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(daysFromNow(-3)));
        original.put(Contract.Transaction.DESCRIPTION, "Lunch");
        original.put(Contract.Transaction.CATEGORY_ID, categoryId);
        original.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        original.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
        original.put(Contract.Transaction.WALLET_ID, mDollarWallet);
        original.put(Contract.Transaction.CONFIRMED, true);
        original.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        original.put(Contract.Transaction.ATTACHMENT_IDS, Contract.getObjectIds(new Attachment[] {
                new Attachment(attachmentId, "receipt.jpg", "receipt.jpg", "image/jpeg", 1234L)}));
        long originalId = ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, original));
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.DUPLICATE_ID, originalId);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(1234L, moneyPicker(activity).getCurrentMoney());
                assertEquals(categoryId, categoryPicker(activity).getCurrentCategory().getId());
                assertEquals("Bank", walletField(activity));
                // removing an attachment in the editor deletes its file outright, so a copy that
                // shared one would leave the original pointing at nothing
                assertTrue(attachmentPicker(activity).getCurrentAttachments().isEmpty());
            });
        }
    }

    @Test
    public void aModelFillsTheEditorFromItsOwnRow() {
        long model = insertModel(4200L, mDollarWallet);
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_MODEL);
        intent.putExtra(NewEditTransactionActivity.MODEL_ID, model);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                assertEquals(4200L, moneyPicker(activity).getCurrentMoney());
                assertEquals("Bank", walletField(activity));
            });
        }
    }

    // fixtures

    private long insertCategory() {
        ContentValues category = new ContentValues();
        category.put(Contract.Category.NAME, "Groceries");
        category.put(Contract.Category.ICON, ICON);
        category.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        category.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category));
    }

    private long insertPerson(String name) {
        ContentValues person = new ContentValues();
        person.put(Contract.Person.NAME, name);
        person.put(Contract.Person.ICON, ICON);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, person));
    }

    private long insertAttachment() {
        ContentValues attachment = new ContentValues();
        attachment.put(Contract.Attachment.FILE, "receipt.jpg");
        attachment.put(Contract.Attachment.NAME, "receipt.jpg");
        attachment.put(Contract.Attachment.TYPE, "image/jpeg");
        attachment.put(Contract.Attachment.SIZE, 1234L);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, attachment));
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

    private long insertModel(long money, long walletId) {
        return insertModel(money, walletId, insertCategory(), null, null);
    }

    private long insertModel(long money, long walletId, long categoryId, Long placeId, Long eventId) {
        ContentValues values = new ContentValues();
        values.put(Contract.TransactionModel.MONEY, money);
        values.put(Contract.TransactionModel.DESCRIPTION, "Weekly shop");
        values.put(Contract.TransactionModel.CATEGORY_ID, categoryId);
        values.put(Contract.TransactionModel.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.TransactionModel.WALLET_ID, walletId);
        values.put(Contract.TransactionModel.PLACE_ID, placeId);
        values.put(Contract.TransactionModel.EVENT_ID, eventId);
        values.put(Contract.TransactionModel.CONFIRMED, true);
        values.put(Contract.TransactionModel.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(
                mResolver.insert(DataContentProvider.CONTENT_TRANSACTION_MODELS, values));
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

    private long insertSaving(long startMoney, long endMoney, long walletId) {
        ContentValues values = new ContentValues();
        values.put(Contract.Saving.DESCRIPTION, "Trip");
        values.put(Contract.Saving.ICON, ICON);
        values.put(Contract.Saving.START_MONEY, startMoney);
        values.put(Contract.Saving.END_MONEY, endMoney);
        values.put(Contract.Saving.WALLET_ID, walletId);
        values.put(Contract.Saving.COMPLETE, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_SAVINGS, values));
    }

    private long insertSavingRow(long money, Date date, String tag, boolean confirmed, long walletId) {
        return insertSavingRow(mSaving, money, date, tag, confirmed, walletId);
    }

    private long insertSavingRow(long saving, long money, Date date, String tag, boolean confirmed,
                                 long walletId) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, money);
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(date));
        values.put(Contract.Transaction.DESCRIPTION, tag);
        values.put(Contract.Transaction.CATEGORY_ID, systemCategory(tag));
        values.put(Contract.Transaction.DIRECTION, Contract.CategoryTag.SAVING_WITHDRAW.equals(tag)
                ? Contract.Direction.INCOME : Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_SAVING);
        values.put(Contract.Transaction.WALLET_ID, walletId);
        values.put(Contract.Transaction.SAVING_ID, saving);
        values.put(Contract.Transaction.CONFIRMED, confirmed);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    private long insertDebt(long walletId) {
        return insertDebt(walletId, null, null);
    }

    private long insertDebt(long walletId, Long personId) {
        return insertDebt(walletId, personId, null);
    }

    /** A debt of 10000 named Rent with one confirmed payment of 2500 made yesterday. */
    private long insertPartlyPaidDebt() {
        ContentValues values = new ContentValues();
        values.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        values.put(Contract.Debt.ICON, ICON);
        values.put(Contract.Debt.DESCRIPTION, "Rent");
        values.put(Contract.Debt.DATE, DateUtils.getSQLDateString(daysFromNow(-3)));
        values.put(Contract.Debt.WALLET_ID, mEuroWallet);
        values.put(Contract.Debt.MONEY, 10000L);
        values.put(Contract.Debt.ARCHIVED, false);
        values.put(Contract.Debt.INSERT_MASTER_TRANSACTION, true);
        long debt = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_DEBTS, values));
        ContentValues payment = new ContentValues();
        payment.put(Contract.Transaction.MONEY, 2500L);
        payment.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(daysFromNow(-1)));
        payment.put(Contract.Transaction.DESCRIPTION, "Rent");
        payment.put(Contract.Transaction.CATEGORY_ID, systemCategory(Contract.CategoryTag.PAID_DEBT));
        payment.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        payment.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_DEBT);
        payment.put(Contract.Transaction.WALLET_ID, mEuroWallet);
        payment.put(Contract.Transaction.DEBT_ID, debt);
        payment.put(Contract.Transaction.CONFIRMED, true);
        payment.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, payment);
        return debt;
    }

    private long insertDebt(long walletId, Long personId, Long placeId) {
        ContentValues values = new ContentValues();
        if (personId != null) {
            values.put(Contract.Debt.PEOPLE_IDS,
                    Contract.getObjectIds(new Person[] {new Person(personId, "Bob", null)}));
        }
        values.put(Contract.Debt.PLACE_ID, placeId);
        values.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        values.put(Contract.Debt.ICON, ICON);
        values.put(Contract.Debt.DESCRIPTION, "Loan");
        values.put(Contract.Debt.DATE, DateUtils.getSQLDateString(daysFromNow(-3)));
        values.put(Contract.Debt.WALLET_ID, walletId);
        values.put(Contract.Debt.MONEY, 20000L);
        values.put(Contract.Debt.ARCHIVED, false);
        values.put(Contract.Debt.INSERT_MASTER_TRANSACTION, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_DEBTS, values));
    }

    private long systemCategory(String tag) {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORIES,
                new String[] {Contract.Category.ID}, Contract.Category.TAG + " = ?",
                new String[] {tag}, null);
        assertTrue("no system category tagged " + tag, cursor.moveToFirst());
        long id = cursor.getLong(0);
        cursor.close();
        return id;
    }

    private long masterTransactionOf(long debt) {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID},
                Contract.Transaction.DEBT_ID + " = ? AND " + Contract.Transaction.CATEGORY_TAG + " = ?",
                new String[] {String.valueOf(debt), Contract.CategoryTag.DEBT}, null);
        assertTrue("the debt has no row of its own", cursor.moveToFirst());
        long id = cursor.getLong(0);
        cursor.close();
        return id;
    }

    private static Date daysFromNow(int days) {
        return new Date(System.currentTimeMillis() + days * DAY);
    }

    // intents

    @Test
    public void aNewTransactionOpensTheCalculatorOnlyWhenTheSettingIsOn() {
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getNextStartedActivity()));
        }
        PreferenceManager.setAutoOpenCalculatorEnabled(true);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> assertStartedCalculator(activity));
        }
    }

    @Test
    public void theCalculatorOpensOnceAndNotAgainAfterARecreate() {
        PreferenceManager.setAutoOpenCalculatorEnabled(true);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> assertStartedCalculator(activity));
            scenario.recreate();
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getNextStartedActivity()));
        }
    }

    @Test
    public void theCalculatorDoesNotOpenAgainWhenTheEditorComesBack() {
        PreferenceManager.setAutoOpenCalculatorEnabled(true);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> assertStartedCalculator(activity));
            // the calculator closing brings the editor back through onResume
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getNextStartedActivity()));
        }
    }

    @Test
    public void theCalculatorStaysClosedWhenTheAmountIsAlreadyThere() {
        PreferenceManager.setAutoOpenCalculatorEnabled(true);
        long debt = insertPartlyPaidDebt();
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(
                debtIntent(debt, NewEditTransactionActivity.DEBT_PAY_IN_FULL))) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getNextStartedActivity()));
        }
        long empty = insertSavingRow(0L, daysFromNow(-1), Contract.CategoryTag.SAVING_DEPOSIT, true, mEuroWallet);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(editIntent(empty))) {
            scenario.onActivity(activity -> assertNull(shadowOf(activity).getNextStartedActivity()));
        }
    }

    @Test
    public void theCalculatorStillOpensAfterTheUnlockWhenTheEditorWasRecreatedUnderTheLock() {
        PreferenceManager.setAutoOpenCalculatorEnabled(true);
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        PreferenceManager.setLastLockTime(0L);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> assertEquals(LockActivity.class.getName(),
                    shadowOf(activity).getNextStartedActivity().getComponent().getClassName()));
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(LockActivity.class.getName(),
                    shadowOf(activity).getNextStartedActivity().getComponent().getClassName()));
            PreferenceManager.setLastLockTime(System.currentTimeMillis());
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertStartedCalculator(activity));
        }
    }

    @Test
    public void theCalculatorWaitsForTheLockScreenAndOpensAfterTheUnlock() {
        PreferenceManager.setAutoOpenCalculatorEnabled(true);
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        PreferenceManager.setLastLockTime(0L);
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(newItemIntent())) {
            scenario.onActivity(activity -> {
                Intent lock = shadowOf(activity).getNextStartedActivity();
                assertEquals(LockActivity.class.getName(), lock.getComponent().getClassName());
                assertNull(shadowOf(activity).getNextStartedActivity());
            });
            // what the lock screen records on a correct pin, then the editor comes back
            PreferenceManager.setLastLockTime(System.currentTimeMillis());
            scenario.moveToState(Lifecycle.State.STARTED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            scenario.onActivity(activity -> assertStartedCalculator(activity));
        }
    }

    private static void assertStartedCalculator(NewEditTransactionActivity activity) {
        Intent started = shadowOf(activity).getNextStartedActivity();
        assertEquals(CalculatorActivity.class.getName(), started.getComponent().getClassName());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    private static Intent newItemIntent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransactionActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private Intent savingIntent(int action) {
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_SAVING);
        intent.putExtra(NewEditTransactionActivity.SAVING_ID, mSaving);
        intent.putExtra(NewEditTransactionActivity.SAVING_ACTION, action);
        return intent;
    }

    private static Intent debtIntent(long debt, int action) {
        Intent intent = newItemIntent();
        intent.putExtra(NewEditTransactionActivity.TYPE, NewEditTransactionActivity.TYPE_DEBT);
        intent.putExtra(NewEditTransactionActivity.DEBT_ID, debt);
        intent.putExtra(NewEditTransactionActivity.DEBT_ACTION, action);
        return intent;
    }

    private static Intent editIntent(long transactionId) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditTransactionActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, transactionId);
        return intent;
    }

    // driving the screen

    private static MoneyPicker moneyPicker(NewEditTransactionActivity activity) {
        return (MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(MONEY_PICKER);
    }

    private static DateTimePicker dateTimePicker(NewEditTransactionActivity activity) {
        return (DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag(DATETIME_PICKER);
    }

    private static CategoryPicker categoryPicker(NewEditTransactionActivity activity) {
        return (CategoryPicker) activity.getSupportFragmentManager().findFragmentByTag(CATEGORY_PICKER);
    }

    private static PersonPicker personPicker(NewEditTransactionActivity activity) {
        return (PersonPicker) activity.getSupportFragmentManager().findFragmentByTag(PERSON_PICKER);
    }

    private static AttachmentPicker attachmentPicker(NewEditTransactionActivity activity) {
        return (AttachmentPicker) activity.getSupportFragmentManager().findFragmentByTag(ATTACHMENT_PICKER);
    }

    private static String walletField(NewEditTransactionActivity activity) {
        return ((MaterialEditText) activity.findViewById(R.id.wallet_edit_text)).getTextAsString();
    }

    private static String descriptionField(NewEditTransactionActivity activity) {
        return ((MaterialEditText) activity.findViewById(R.id.description_edit_text)).getTextAsString();
    }

    private static void save(NewEditTransactionActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        activity.onMenuItemClick(toolbar.getMenu().findItem(R.id.action_save_changes));
    }

    // reading back

    private int countTransactions() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, null, null, null);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private Cursor newestTransaction() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS, new String[] {
                Contract.Transaction.ID, Contract.Transaction.MONEY, Contract.Transaction.DATE,
                Contract.Transaction.WALLET_ID, Contract.Transaction.SAVING_ID,
                Contract.Transaction.DEBT_ID, Contract.Transaction.TYPE, Contract.Transaction.DIRECTION,
                Contract.Transaction.CATEGORY_TAG, Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.PLACE_ID, Contract.Transaction.EVENT_ID
        }, null, null, Contract.Transaction.ID + " DESC");
        assertTrue(cursor.moveToFirst());
        return cursor;
    }

    private long moneyOf(long transactionId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId);
        Cursor cursor = mResolver.query(uri, new String[] {Contract.Transaction.MONEY}, null, null, null);
        assertTrue(cursor.moveToFirst());
        long money = cursor.getLong(0);
        cursor.close();
        return money;
    }

    private String dateOf(long transactionId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId);
        Cursor cursor = mResolver.query(uri, new String[] {Contract.Transaction.DATE}, null, null, null);
        assertTrue(cursor.moveToFirst());
        String date = cursor.getString(0);
        cursor.close();
        return date;
    }

    private boolean savingIsComplete() {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, mSaving);
        Cursor cursor = mResolver.query(uri, new String[] {Contract.Saving.COMPLETE}, null, null, null);
        assertTrue(cursor.moveToFirst());
        boolean complete = cursor.getInt(0) == 1;
        cursor.close();
        return complete;
    }

}
