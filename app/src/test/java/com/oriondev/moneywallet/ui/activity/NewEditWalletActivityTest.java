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
import android.widget.ListView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Drives the wallet editor's account group field against the real content provider over a fresh
 * database. The names a wallet can be put into come out of that database, so the picker is the
 * one part of this screen that cannot be read off the layout.
 */
@RunWith(RobolectricTestRunner.class)
public class NewEditWalletActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ContentResolver mResolver;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
    }

    /**
     * The names in use, between the two entries that put the wallet in no group and ask for a new
     * one, in the order the drawer draws them. That order ignores case, so a lower case name does
     * not sit below every upper case one the way plain text ordering would put it.
     */
    @Test
    public void thePickerOffersTheNamesInUseInTheOrderTheDrawerDrawsThem() {
        insertWallet("Deposit", "apartment");
        insertWallet("Utilities", "Bills");
        insertWallet("Pension", null);
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                assertEquals(List.of(activity.getString(R.string.wallet_group_none),
                                "apartment", "Bills",
                                activity.getString(R.string.action_new_wallet_group)),
                        openPicker(activity));
            });
        }
    }

    /**
     * A name typed into New group is not on any wallet until the editor is saved, so the picker
     * has to carry it anyway. Reopening the picker used to tick "No group" over a field reading
     * the new name, with the only route back to it being to type it again.
     */
    @Test
    public void aNameTypedIntoNewGroupIsTickedWhenThePickerIsOpenedAgain() {
        insertWallet("Utilities", "Bills");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                typeNewGroup(activity, "Kids");
                assertEquals("Kids", groupField(activity).getTextAsString());
                List<String> items = openPicker(activity);
                assertEquals(List.of(activity.getString(R.string.wallet_group_none),
                                "Bills", "Kids",
                                activity.getString(R.string.action_new_wallet_group)),
                        items);
                assertEquals(items.indexOf("Kids"), latestPicker().getListView().getCheckedItemPosition());
            });
        }
    }

    /**
     * New group takes free text, and two wallets in "Savings" and "savings" draw under two
     * headings. Somebody typing the second one means the group they can already see, so the name
     * already in use is the one that is stored.
     */
    @Test
    public void newGroupReusesAnOfferedNameThatDiffersOnlyByCase() {
        insertWallet("Rainy day", "Savings");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                typeNewGroup(activity, "savings");
                assertEquals("Savings", groupField(activity).getTextAsString());
            });
        }
    }

    /** A name nothing else is using is stored as it was typed. */
    @Test
    public void newGroupKeepsANameNothingElseIsUsing() {
        insertWallet("Rainy day", "Savings");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                typeNewGroup(activity, "School run");
                assertEquals("School run", groupField(activity).getTextAsString());
            });
        }
    }

    /**
     * The name the field is showing is not a name in use, it is the one being chosen, so typing it
     * again with different capitalization has to take. Folding onto it left the first spelling in
     * the field with no message and no way back to the second one.
     */
    @Test
    public void aNameTypedIntoNewGroupCanBeTypedAgainWithDifferentCase() {
        insertWallet("Pension", null);
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                typeNewGroup(activity, "kids");
                typeNewGroup(activity, "Kids");
                assertEquals("Kids", groupField(activity).getTextAsString());
            });
        }
    }

    /** A name that is all spaces is not a name, and creating a group never leaves one. */
    @Test
    public void newGroupOverOnlySpacesLeavesTheFieldWhereItWas() {
        insertWallet("Rainy day", "Savings");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(newIntent())) {
            scenario.onActivity(activity -> {
                typeNewGroup(activity, "Savings");
                typeNewGroup(activity, "  ");
                assertEquals("Savings", groupField(activity).getTextAsString());
            });
        }
    }

    /**
     * Putting a wallet that already exists into a group is what the picker is for, and it is an
     * edit. The editor has to load the stored name into the field, and the picked one has to reach
     * the row, which takes a write naming the group column and a provider that passes it through.
     */
    @Test
    public void aStoredGroupLoadsIntoTheFieldAndAPickedOneReachesTheRow() {
        insertWallet("Utilities", "Bills");
        long wallet = insertWallet("Rainy day", "Savings");
        insertWallet("Emergency", "Savings");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(editIntent(wallet))) {
            scenario.onActivity(activity -> {
                assertEquals("Savings", groupField(activity).getTextAsString());
                List<String> items = openPicker(activity);
                assertEquals(List.of(activity.getString(R.string.wallet_group_none),
                                "Bills", "Savings",
                                activity.getString(R.string.action_new_wallet_group)),
                        items);
                ListView list = latestPicker().getListView();
                assertEquals(items.indexOf("Savings"), list.getCheckedItemPosition());
                pick(list, items.indexOf("Bills"));
                assertEquals("Bills", groupField(activity).getTextAsString());
                save(activity);
                assertEquals("Bills", storedGroup(wallet));
            });
        }
    }

    /** Leaving a group is an edit too, and the row has to end up with no group at all. */
    @Test
    public void noGroupTakesAnEditedWalletOutOfItsGroup() {
        long wallet = insertWallet("Rainy day", "Savings");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(editIntent(wallet))) {
            scenario.onActivity(activity -> {
                openPicker(activity);
                pick(latestPicker().getListView(), 0);
                save(activity);
                assertNull(storedGroup(wallet));
            });
        }
    }

    /**
     * The wallet being edited is not a wallet the typed name has to join, so its own saved group
     * is no reason to fold the typing onto the old spelling.
     */
    @Test
    public void theOnlyWalletInAGroupCanRetypeItWithDifferentCase() {
        long wallet = insertWallet("Kids fund", "kids");
        try (ActivityScenario<NewEditWalletActivity> scenario = ActivityScenario.launch(editIntent(wallet))) {
            scenario.onActivity(activity -> {
                typeNewGroup(activity, "Kids");
                save(activity);
                assertEquals("Kids", storedGroup(wallet));
            });
        }
    }

    private static void pick(ListView list, int position) {
        list.performItemClick(list, position, list.getAdapter().getItemId(position));
        idle();
    }

    /** Saves through the toolbar menu, the way a tap on the check mark does. */
    private static void save(NewEditWalletActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        toolbar.getMenu().performIdentifierAction(R.id.action_save_changes, 0);
        idle();
    }

    /** The group the row carries, read back through the provider the editor wrote through. */
    private String storedGroup(long wallet) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, wallet);
        try (Cursor cursor = mResolver.query(uri,
                new String[] {Contract.Wallet.GROUP}, null, null, null)) {
            assertTrue("the wallet row is gone", cursor != null && cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.GROUP));
        }
    }

    /** Taps the group field and returns what the picker offers, in its own order. */
    private static List<String> openPicker(NewEditWalletActivity activity) {
        groupField(activity).performClick();
        idle();
        ListView list = latestPicker().getListView();
        List<String> items = new ArrayList<>();
        for (int i = 0; i < list.getAdapter().getCount(); i++) {
            items.add(String.valueOf(list.getAdapter().getItem(i)));
        }
        return items;
    }

    /** Walks the picker's last entry into the input dialog and confirms the typed name. */
    private static void typeNewGroup(NewEditWalletActivity activity, String name) {
        List<String> items = openPicker(activity);
        ListView list = latestPicker().getListView();
        list.performItemClick(list, items.size() - 1, list.getAdapter().getItemId(items.size() - 1));
        idle();
        AlertDialog input = latestPicker();
        EditText field = input.findViewById(R.id.dialog_input_edit_text);
        field.setText(name);
        input.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        idle();
    }

    /** The dialog buttons answer on the main looper, which the test drives by hand. */
    private static void idle() {
        shadowOf(Looper.getMainLooper()).idle();
    }

    private static AlertDialog latestPicker() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertTrue("the last dialog shown is not the one this drives", dialog instanceof AlertDialog);
        return (AlertDialog) dialog;
    }

    private static MaterialEditText groupField(NewEditWalletActivity activity) {
        return activity.findViewById(R.id.group_edit_text);
    }

    private static Intent newIntent() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditWalletActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private static Intent editIntent(long wallet) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                NewEditWalletActivity.class);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, wallet);
        return intent;
    }

    private long insertWallet(String name, String group) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        values.put(Contract.Wallet.GROUP, group);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }
}
