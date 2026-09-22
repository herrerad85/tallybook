package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

/**
 * The wallet detail panel shows the group a wallet belongs to, and draws no row at all for a
 * wallet in no group, the same way it treats the note.
 */
@RunWith(RobolectricTestRunner.class)
public class WalletItemFragmentTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String TAG_FRAGMENT = "WalletItemFragmentTest::Fragment";

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
    }

    @Test
    public void aWalletInAGroupShowsTheGroup() {
        long wallet = insertWallet("Checking", "Savings");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView group = showWallet(activity, wallet);
                assertEquals(View.VISIBLE, group.getVisibility());
                assertEquals("Savings", group.getText().toString());
            });
        }
    }

    @Test
    public void aWalletInNoGroupDrawsNoGroupRow() {
        long wallet = insertWallet("Checking", null);
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                TextView group = showWallet(activity, wallet);
                assertEquals(View.GONE, group.getVisibility());
            });
        }
    }

    @Test
    public void aPanelReusedForAGroupedWalletShowsTheRowItHidForTheLastOne() {
        long ungrouped = insertWallet("Checking", null);
        long grouped = insertWallet("Brokerage", "Savings");
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                WalletItemFragment fragment = addPanel(activity);
                assertEquals(View.GONE, loadWallet(fragment, ungrouped).getVisibility());
                TextView group = loadWallet(fragment, grouped);
                assertEquals(View.VISIBLE, group.getVisibility());
                assertEquals("Savings", group.getText().toString());
            });
        }
    }

    private TextView showWallet(FragmentActivity activity, long walletId) {
        return loadWallet(addPanel(activity), walletId);
    }

    private WalletItemFragment addPanel(FragmentActivity activity) {
        WalletItemFragment fragment = new WalletItemFragment();
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, TAG_FRAGMENT)
                .commitNow();
        return fragment;
    }

    private TextView loadWallet(WalletItemFragment fragment, long walletId) {
        fragment.showItemId(walletId);
        // the query runs on a background thread, and the wheel goes away only once its result lands
        View wheel = fragment.getView().findViewById(R.id.secondary_panel_progress_wheel);
        for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertEquals("the wallet never loaded", View.GONE, wheel.getVisibility());
        return fragment.getView().findViewById(R.id.group_text_view);
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
