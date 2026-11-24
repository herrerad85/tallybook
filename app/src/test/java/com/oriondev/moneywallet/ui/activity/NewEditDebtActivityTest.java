package com.oriondev.moneywallet.ui.activity;

import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class NewEditDebtActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String MONEY_PICKER = "NewEditDebtActivity::Tag::MoneyPicker";

    private ContentResolver mResolver;
    private long mWallet;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        mWallet = insertWallet();
    }

    @Test
    public void aNewCreditAtNothingIsRefusedAsMissingItsAmountWithoutAskingForTheMasterTransaction() {
        assertNewItemAtNothingRefused(Contract.DebtType.CREDIT);
    }

    @Test
    public void aNewDebtAtNothingIsRefusedAsMissingItsAmountWithoutAskingForTheMasterTransaction() {
        assertNewItemAtNothingRefused(Contract.DebtType.DEBT);
    }

    @Test
    public void aNewCreditWithAnAmountAsksForTheMasterTransactionAndIsSavedWithThatAmount() {
        try (ActivityScenario<NewEditDebtActivity> scenario =
                     ActivityScenario.launch(newItemIntent(Contract.DebtType.CREDIT))) {
            scenario.onActivity(activity -> {
                description(activity).setText("Loan");
                moneyPicker(activity).setMoney(5000L);
                save(activity);
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertTrue(dialog.isShowing());
                assertEquals(activity.getString(R.string.message_debt_insert_master_transaction),
                        message(dialog));
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals(1, countDebts());
        assertEquals(1, countTransactions());
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_DEBTS,
                new String[] {Contract.Debt.TYPE, Contract.Debt.MONEY}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(Contract.DebtType.CREDIT.getValue(),
                    cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Debt.TYPE)));
            assertEquals(5000L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.MONEY)));
        }
    }

    @Test
    public void aStoredDebtSetToNothingIsRefusedAsMissingItsAmount() {
        long debt = insertDebt(20000L);
        try (ActivityScenario<NewEditDebtActivity> scenario = ActivityScenario.launch(editIntent(debt))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(0L);
                save(activity);
                assertFalse(activity.isFinishing());
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertTrue(dialog.isShowing());
                assertEquals(activity.getString(R.string.error_input_missing_amount), message(dialog));
            });
        }
        assertEquals("20000", storedColumn(debt, Contract.Debt.MONEY));
    }

    @Test
    public void aStoredDebtCanBeSavedAtANewAmount() {
        long debt = insertDebt(20000L);
        try (ActivityScenario<NewEditDebtActivity> scenario = ActivityScenario.launch(editIntent(debt))) {
            scenario.onActivity(activity -> {
                moneyPicker(activity).setMoney(30000L);
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals("30000", storedColumn(debt, Contract.Debt.MONEY));
    }

    @Test
    public void aDebtStoredAtNothingCanStillHaveItsDescriptionCorrected() {
        long debt = insertDebt(0L);
        try (ActivityScenario<NewEditDebtActivity> scenario = ActivityScenario.launch(editIntent(debt))) {
            scenario.onActivity(activity -> {
                description(activity).setText("Corrected");
                save(activity);
                assertTrue(activity.isFinishing());
            });
        }
        assertEquals("Corrected", storedColumn(debt, Contract.Debt.DESCRIPTION));
        assertEquals("0", storedColumn(debt, Contract.Debt.MONEY));
    }

    private void assertNewItemAtNothingRefused(Contract.DebtType type) {
        try (ActivityScenario<NewEditDebtActivity> scenario = ActivityScenario.launch(newItemIntent(type))) {
            scenario.onActivity(activity -> {
                description(activity).setText("Loan");
                save(activity);
                assertFalse(activity.isFinishing());
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertTrue(dialog.isShowing());
                assertEquals(activity.getString(R.string.error_input_missing_amount), message(dialog));
                for (Dialog shown : ShadowDialog.getShownDialogs()) {
                    assertNotEquals(activity.getString(R.string.message_debt_insert_master_transaction),
                            message((AlertDialog) shown));
                }
            });
        }
        assertEquals(0, countDebts());
        assertEquals(0, countTransactions());
    }

    private static Intent newItemIntent(Contract.DebtType type) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), NewEditDebtActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        intent.putExtra(NewEditDebtActivity.TYPE, type);
        return intent;
    }

    private static Intent editIntent(long debt) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), NewEditDebtActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, debt);
        return intent;
    }

    private static MoneyPicker moneyPicker(NewEditDebtActivity activity) {
        return (MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(MONEY_PICKER);
    }

    private static EditText description(NewEditDebtActivity activity) {
        return activity.findViewById(R.id.description_edit_text);
    }

    private static String message(AlertDialog dialog) {
        TextView text = dialog.findViewById(android.R.id.message);
        return text.getText().toString();
    }

    private static void save(NewEditDebtActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        activity.onMenuItemClick(toolbar.getMenu().findItem(R.id.action_save_changes));
    }

    private int countDebts() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_DEBTS,
                new String[] {Contract.Debt.ID}, null, null, null)) {
            return cursor.getCount();
        }
    }

    private int countTransactions() {
        try (Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, null, null, null)) {
            return cursor.getCount();
        }
    }

    private String storedColumn(long debt, String column) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, debt);
        try (Cursor cursor = mResolver.query(uri, new String[] {column}, null, null, null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow(column));
        }
    }

    private long insertWallet() {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, "Cash");
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertDebt(long money) {
        ContentValues values = new ContentValues();
        values.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        values.put(Contract.Debt.ICON, ICON);
        values.put(Contract.Debt.DESCRIPTION, "Loan");
        values.put(Contract.Debt.DATE, DateUtils.getSQLDateString(new Date()));
        values.put(Contract.Debt.WALLET_ID, mWallet);
        values.put(Contract.Debt.MONEY, money);
        values.put(Contract.Debt.ARCHIVED, false);
        values.put(Contract.Debt.INSERT_MASTER_TRANSACTION, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_DEBTS, values));
    }
}
