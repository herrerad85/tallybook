package com.oriondev.moneywallet.ui.fragment.dialog;

import android.database.Cursor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * Shows the wallet picker from a bare host activity and reads the query its loader was built
 * with, which decides the wallets it lists.
 */
@RunWith(RobolectricTestRunner.class)
public class WalletPickerDialogTest {

    private static final String TAG_DIALOG = "WalletPickerDialogTest::Dialog";

    @Before
    public void setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void aPickerGivenACurrencyAsksOnlyForWalletsInIt() {
        CursorLoader loader = showSinglePicker("EUR");
        assertEquals(Contract.Wallet.ARCHIVED + " = 0 AND " + Contract.Wallet.CURRENCY + " = ?",
                loader.getSelection());
        assertArrayEquals(new String[] {"EUR"}, loader.getSelectionArgs());
    }

    @Test
    public void aPickerGivenNoCurrencyAsksForEveryWalletThatIsNotArchived() {
        CursorLoader loader = showSinglePicker(null);
        assertEquals(Contract.Wallet.ARCHIVED + " = 0", loader.getSelection());
        assertNull(loader.getSelectionArgs());
    }

    /** A picker restored after the host is recreated still lists only the wallets in its currency. */
    @Test
    public void aPickerGivenACurrencyKeepsItWhenTheHostIsRecreated() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            WalletPickerDialog.newInstance().showSinglePicker(
                    controller.get().getSupportFragmentManager(), TAG_DIALOG, null, "EUR");
            controller.get().getSupportFragmentManager().executePendingTransactions();
            controller.recreate();
            WalletPickerDialog dialog = (WalletPickerDialog) controller.get()
                    .getSupportFragmentManager().findFragmentByTag(TAG_DIALOG);
            assertNotNull(dialog);
            CursorLoader loader = (CursorLoader) LoaderManager.getInstance(dialog).<Cursor>getLoader(1);
            assertEquals(Contract.Wallet.ARCHIVED + " = 0 AND " + Contract.Wallet.CURRENCY + " = ?",
                    loader.getSelection());
            assertArrayEquals(new String[] {"EUR"}, loader.getSelectionArgs());
        } finally {
            controller.close();
        }
    }

    /** Shows a single picker, through the overload without a currency when currencyIso is null. */
    private static CursorLoader showSinglePicker(String currencyIso) {
        // the host is a bare AppCompatActivity, which the manifest does not declare, so it is
        // driven through a controller
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();
            WalletPickerDialog dialog = WalletPickerDialog.newInstance();
            if (currencyIso == null) {
                dialog.showSinglePicker(activity.getSupportFragmentManager(), TAG_DIALOG, null);
            } else {
                dialog.showSinglePicker(activity.getSupportFragmentManager(), TAG_DIALOG, null, currencyIso);
            }
            activity.getSupportFragmentManager().executePendingTransactions();
            return (CursorLoader) LoaderManager.getInstance(dialog).<Cursor>getLoader(1);
        } finally {
            controller.close();
        }
    }
}
