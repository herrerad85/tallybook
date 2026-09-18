package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.elevation.ElevationOverlayProvider;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.model.WalletAccount;
import com.oriondev.moneywallet.service.BackupHandlerIntentService;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.fragment.multipanel.CategoryMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.TransactionMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.singlepanel.OverviewSinglePanelFragment;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Drives the main screen's drawer on the JVM against the real content provider: which section
 * opens, what back does, and which wallet the header and the preference end up on.
 */
@RunWith(RobolectricTestRunner.class)
public class MainActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String VECTOR_ICON = "{\"type\":\"resource\",\"resource\":\"ic_icon_slippers\"}";
    private static final int TOTAL_ITEM = MainActivity.ID_WALLET_FIRST + (int) PreferenceManager.TOTAL_WALLET_ID;

    private ContentResolver mResolver;
    private long mFirstWallet;
    private long mSecondWallet;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        PreferenceManager.setCurrentWallet(context, PreferenceManager.NO_CURRENT_WALLET);
        // named against their order, so a list sorted by name instead of by index shows
        mFirstWallet = insertWallet("Cash", 0, 0L, true, false);
        mSecondWallet = insertWallet("Bank", 1, 0L, true, false);
    }

    @Test
    public void theFirstLaunchOpensTransactionsOnTheFirstWallet() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                assertEquals(mFirstWallet, PreferenceManager.getCurrentWallet());
                assertEquals("Cash", headerName(activity));
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, drawer(activity).getCheckedItem().getItemId());
                assertTrue(section(activity) instanceof TransactionMultiPanelViewPagerFragment);
                ImageView icon = activity.findViewById(R.id.wallet_icon_image_view);
                assertNotNull(icon.getDrawable());
                assertNotNull(icon.getBackground());
                assertNotNull(drawer(activity).getItemBackground());
                assertNull(drawer(activity).getItemIconTintList());
                assertTrue(drawer(activity).getClipToOutline());
                assertTrue(drawer(activity).getBackground() instanceof MaterialShapeDrawable);
                assertEquals(ThemeEngine.getTheme().getDrawerBackgroundColor(), ((MaterialShapeDrawable) drawer(activity).getBackground()).getFillColor().getDefaultColor());
                assertFalse(new ElevationOverlayProvider(activity).isThemeElevationOverlayEnabled());
            });
        }
    }

    @Test
    public void theMainScreenInflatesItsFloatingActionButton() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                View fab = activity.findViewById(R.id.floating_action_button);
                assertTrue(fab instanceof FloatingActionButton);
            });
        }
    }

    @Test
    public void aLaunchOpensOnTheWalletThePreferenceNames() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(), mSecondWallet);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                assertEquals("Bank", headerName(activity));
                assertEquals(mSecondWallet, PreferenceManager.getCurrentWallet());
            });
        }
    }

    @Test
    public void anEmptyLedgerShowsNoWalletAndOffersOnlyTheTwoActions() {
        for (long id : new long[] {mFirstWallet, mSecondWallet}) {
            mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, id), null, null);
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                assertEquals(activity.getString(R.string.msg_no_wallet_found), headerName(activity));
                assertEquals(PreferenceManager.NO_CURRENT_WALLET, PreferenceManager.getCurrentWallet());
                Menu menu = drawer(activity).getMenu();
                assertNull(menu.findItem(TOTAL_ITEM));
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                assertTrue(menu.findItem(MainActivity.ID_ACTION_NEW_WALLET).isVisible());
                assertTrue(menu.findItem(MainActivity.ID_ACTION_MANAGE_WALLET).isVisible());
                assertEquals(View.GONE, activity.findViewById(R.id.first_wallet_image_view).getVisibility());
            });
        }
    }

    @Test
    public void aReloadRebuildsTheWalletRowsInsteadOfStackingThem() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                Menu menu = drawer(activity).getMenu();
                assertEquals(3, walletRows(menu));
                // a restore from backup asks the drawer to load the wallets again
                Intent restored = new Intent(LocalAction.ACTION_BACKUP_SERVICE_FINISHED)
                        .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE);
                LocalBroadcastManager.getInstance(activity).sendBroadcast(restored);
                ((TextView) activity.findViewById(R.id.wallet_name_text_view)).setText("");
                awaitWallets(activity);
                assertEquals(3, walletRows(menu));
                assertFalse(menu.findItem(walletItem(mFirstWallet)).isVisible());
                assertEquals("Cash", headerName(activity));
            });
        }
    }

    @Test
    public void aPreferenceNamingNoListedWalletShowsTheFirstAndIsLeftAlone() {
        PreferenceManager.setCurrentWallet(ApplicationProvider.getApplicationContext(), 999L);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                assertEquals("Cash", headerName(activity));
                assertEquals(999L, PreferenceManager.getCurrentWallet());
            });
        }
    }

    @Test
    public void aSectionTapLoadsItsFragmentAndBackReturnsToTransactions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Menu menu = drawer(activity).getMenu();
                assertTrue(menu.performIdentifierAction(MainActivity.ID_SECTION_CATEGORIES, 0));
                runPending(activity);
                assertTrue(section(activity) instanceof CategoryMultiPanelViewPagerFragment);
                activity.onBackPressed();
                runPending(activity);
                assertTrue(section(activity) instanceof TransactionMultiPanelViewPagerFragment);
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, drawer(activity).getCheckedItem().getItemId());
                assertFalse(activity.isFinishing());
                activity.onBackPressed();
                assertTrue(activity.isFinishing());
            });
        }
    }

    @Test
    public void backClosesAnOpenDrawerAndLeavesTheSectionAlone() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Menu menu = drawer(activity).getMenu();
                activity.onNavigationItemSelected(menu.findItem(MainActivity.ID_SECTION_CATEGORIES));
                runPending(activity);
                DrawerLayout layout = activity.findViewById(R.id.drawer_layout);
                layout.openDrawer(GravityCompat.START, false);
                assertTrue(layout.isDrawerOpen(GravityCompat.START));
                // the close animates, and the JVM never draws a frame; what back must not do
                // is move the section or finish the activity while the drawer had it
                activity.onBackPressed();
                runPending(activity);
                assertTrue(section(activity) instanceof CategoryMultiPanelViewPagerFragment);
                assertEquals(MainActivity.ID_SECTION_CATEGORIES, drawer(activity).getCheckedItem().getItemId());
                assertFalse(activity.isFinishing());
            });
        }
    }

    @Test
    public void theSectionSurvivesARecreate() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.onNavigationItemSelected(drawer(activity).getMenu().findItem(MainActivity.ID_SECTION_CATEGORIES));
                runPending(activity);
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                runPending(activity);
                assertTrue(section(activity) instanceof CategoryMultiPanelViewPagerFragment);
                assertEquals(MainActivity.ID_SECTION_CATEGORIES, drawer(activity).getCheckedItem().getItemId());
            });
        }
    }

    @Test
    public void aToolTapOpensItsScreenAndLeavesTheSectionAlone() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Menu menu = drawer(activity).getMenu();
                assertFalse(activity.onNavigationItemSelected(menu.findItem(MainActivity.ID_SECTION_CALCULATOR)));
                Intent next = shadowOf(activity).getNextStartedActivity();
                assertEquals(CalculatorActivity.class.getName(), next.getComponent().getClassName());
                runPending(activity);
                assertTrue(section(activity) instanceof TransactionMultiPanelViewPagerFragment);
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, drawer(activity).getCheckedItem().getItemId());
            });
        }
    }

    @Test
    public void pickingAWalletFromTheListMovesTheHeaderAndThePreference() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                Menu menu = drawer(activity).getMenu();
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                assertFalse(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS).isVisible());
                assertFalse(menu.findItem(MainActivity.ID_SECTION_CALCULATOR).isVisible());
                assertFalse(menu.findItem(MainActivity.ID_SECTION_SETTING).isVisible());
                MenuItem bank = menu.findItem(walletItem(mSecondWallet));
                assertEquals("Bank", bank.getTitle().toString());
                assertTrue(bank.isVisible());
                assertEquals(walletItem(mFirstWallet), drawer(activity).getCheckedItem().getItemId());
                assertFalse(activity.onNavigationItemSelected(bank));
                assertEquals(mSecondWallet, PreferenceManager.getCurrentWallet());
                assertEquals("Bank", headerName(activity));
                assertTrue(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS).isVisible());
                assertTrue(menu.findItem(MainActivity.ID_SECTION_CALCULATOR).isVisible());
                assertTrue(menu.findItem(MainActivity.ID_SECTION_SETTING).isVisible());
                assertFalse(menu.findItem(walletItem(mSecondWallet)).isVisible());
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, drawer(activity).getCheckedItem().getItemId());
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                assertEquals(walletItem(mSecondWallet), drawer(activity).getCheckedItem().getItemId());
            });
        }
    }

    @Test
    public void pickingAWalletNamesItInTheToolbarBeforeItsLoaderLands() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                Toolbar toolbar = section(activity).requireView().findViewById(R.id.primary_toolbar);
                awaitSubtitle(toolbar, "Cash");
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertFalse(activity.onNavigationItemSelected(drawer(activity).getMenu().findItem(walletItem(mSecondWallet))));
                // only the change broadcast, which the switch queues first. Idling could also
                // deliver the reloaded row and hide an empty subtitle in between
                shadowOf(Looper.getMainLooper()).runOneTask();
                assertEquals("Bank", String.valueOf(toolbar.getSubtitle()));
                awaitSubtitle(toolbar, "Bank");
                for (int i = 0; i < 20; i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    sleep();
                }
                assertEquals("Bank", String.valueOf(toolbar.getSubtitle()));
                ContentValues values = new ContentValues();
                values.put(Contract.Wallet.NAME, "Savings");
                mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mSecondWallet), values, null, null);
                awaitSubtitle(toolbar, "Savings");
            });
        }
    }

    @Test
    public void pickingAWalletNamesItInTheOverviewToolbarBeforeItsLoaderLands() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                assertTrue(drawer(activity).getMenu().performIdentifierAction(MainActivity.ID_SECTION_OVERVIEW, 0));
                runPending(activity);
                assertTrue(section(activity) instanceof OverviewSinglePanelFragment);
                Toolbar toolbar = section(activity).requireView().findViewById(R.id.primary_toolbar);
                awaitSubtitle(toolbar, "Cash");
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertFalse(activity.onNavigationItemSelected(drawer(activity).getMenu().findItem(walletItem(mSecondWallet))));
                shadowOf(Looper.getMainLooper()).runOneTask();
                assertEquals("Bank", String.valueOf(toolbar.getSubtitle()));
                awaitSubtitle(toolbar, "Bank");
                for (int i = 0; i < 20; i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    sleep();
                }
                assertEquals("Bank", String.valueOf(toolbar.getSubtitle()));
                ContentValues values = new ContentValues();
                values.put(Contract.Wallet.NAME, "Savings");
                mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mSecondWallet), values, null, null);
                awaitSubtitle(toolbar, "Savings");
            });
        }
    }

    @Test
    public void aRowNamingAWalletThatIsGoneIsIgnored() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                Menu menu = drawer(activity).getMenu();
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                // a row bound before a reload carries the id of a wallet the list no longer has
                MenuItem stale = menu.add(4, MainActivity.ID_WALLET_FIRST + 999, Menu.NONE, "Gone");
                assertFalse(activity.onNavigationItemSelected(stale));
                assertEquals(mFirstWallet, PreferenceManager.getCurrentWallet());
                assertEquals("Cash", headerName(activity));
                assertTrue(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS).isVisible());
            });
        }
    }

    @Test
    public void theTotalEntryComesAfterTheWalletsAndIsItsOwnCurrentWallet() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                MenuItem total = drawer(activity).getMenu().findItem(TOTAL_ITEM);
                assertEquals(activity.getString(R.string.total_wallet_name), total.getTitle().toString());
                activity.onNavigationItemSelected(total);
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, PreferenceManager.getCurrentWallet());
                assertEquals(activity.getString(R.string.total_wallet_name), headerName(activity));
            });
        }
    }

    @Test
    public void anArchivedWalletStaysOutOfTheListAndInTheTotalAndAnExcludedOneStaysOut() {
        insertWallet("Old", 2, 50000L, true, true);
        insertWallet("Side", 3, 70000L, false, false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                Menu menu = drawer(activity).getMenu();
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                assertNull(findByTitle(menu, "Old"));
                assertTrue(findByTitle(menu, "Side").isVisible());
                String expected = MoneyFormatter.getInstance().getNotTintedString(new Money("EUR", 50000L));
                assertEquals(expected, ((TextView) menu.findItem(TOTAL_ITEM).getActionView()).getText().toString());
                activity.onNavigationItemSelected(menu.findItem(TOTAL_ITEM));
                assertEquals(expected, ((TextView) activity.findViewById(R.id.wallet_money_text_view)).getText().toString());
            });
        }
    }

    @Test
    public void theQuickSwitchIconsHoldTheNextTwoWalletsAndSwitchToThem() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                View first = activity.findViewById(R.id.first_wallet_image_view);
                View second = activity.findViewById(R.id.second_wallet_image_view);
                assertEquals(mSecondWallet, quickWallet(first).getId());
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, quickWallet(second).getId());
                assertEquals(View.VISIBLE, second.getVisibility());
                assertNotNull(((ImageView) first).getDrawable());
                assertEquals("Bank", first.getContentDescription().toString());
                first.performClick();
                assertEquals(mSecondWallet, PreferenceManager.getCurrentWallet());
                assertEquals("Bank", headerName(activity));
                assertEquals(mFirstWallet, quickWallet(first).getId());
                assertEquals("Cash", first.getContentDescription().toString());
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, quickWallet(second).getId());
                second.performClick();
                assertEquals(PreferenceManager.TOTAL_WALLET_ID, PreferenceManager.getCurrentWallet());
                assertEquals(mFirstWallet, quickWallet(first).getId());
                assertEquals(mSecondWallet, quickWallet(second).getId());
            });
        }
    }

    @Test
    public void aQuickSwitchWalletWithAPickerIconLoadsWithoutACrash() {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.ICON, VECTOR_ICON);
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mSecondWallet), values, null, null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                ImageView first = activity.findViewById(R.id.first_wallet_image_view);
                assertEquals(mSecondWallet, quickWallet(first).getId());
                assertEquals(View.VISIBLE, first.getVisibility());
                // the picker icon comes through Glide, which lands on the main looper later
                for (int i = 0; i < 80 && first.getDrawable() == null; i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }
                assertNotNull(first.getDrawable());
            });
        }
    }

    @Test
    public void noWalletCountingInTheTotalMeansNoTotalEntry() {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.COUNT_IN_TOTAL, false);
        for (long id : new long[] {mFirstWallet, mSecondWallet}) {
            mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, id), values, null, null);
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                assertNull(drawer(activity).getMenu().findItem(TOTAL_ITEM));
                assertEquals(mSecondWallet, quickWallet(activity.findViewById(R.id.first_wallet_image_view)).getId());
                assertEquals(View.GONE, activity.findViewById(R.id.second_wallet_image_view).getVisibility());
            });
        }
    }

    @Test
    public void newWalletOpensTheEditorAndPutsTheSectionsBack() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                awaitWallets(activity);
                Menu menu = drawer(activity).getMenu();
                activity.findViewById(R.id.navigation_drawer_header).performClick();
                assertFalse(activity.onNavigationItemSelected(menu.findItem(MainActivity.ID_ACTION_NEW_WALLET)));
                Intent next = shadowOf(activity).getNextStartedActivity();
                assertEquals(NewEditWalletActivity.class.getName(), next.getComponent().getClassName());
                assertTrue(menu.findItem(MainActivity.ID_SECTION_TRANSACTIONS).isVisible());
                assertEquals(MainActivity.ID_SECTION_TRANSACTIONS, drawer(activity).getCheckedItem().getItemId());
            });
        }
    }

    private static int walletItem(long walletId) {
        return MainActivity.ID_WALLET_FIRST + (int) walletId;
    }

    private static WalletAccount quickWallet(View icon) {
        return (WalletAccount) icon.getTag(icon.getId());
    }

    private static int walletRows(Menu menu) {
        int rows = 0;
        for (int i = 0; i < menu.size(); i++) {
            if (menu.getItem(i).getItemId() >= MainActivity.ID_WALLET_FIRST) {
                rows++;
            }
        }
        return rows;
    }

    private static MenuItem findByTitle(Menu menu, String title) {
        for (int i = 0; i < menu.size(); i++) {
            if (title.contentEquals(menu.getItem(i).getTitle())) {
                return menu.getItem(i);
            }
        }
        return null;
    }

    private static NavigationView drawer(MainActivity activity) {
        return activity.findViewById(R.id.navigation_view);
    }

    private static Fragment section(MainActivity activity) {
        return activity.getSupportFragmentManager().findFragmentById(R.id.fragment_container);
    }

    private static String headerName(MainActivity activity) {
        return ((TextView) activity.findViewById(R.id.wallet_name_text_view)).getText().toString();
    }

    private static void runPending(MainActivity activity) {
        activity.getSupportFragmentManager().executePendingTransactions();
    }

    /**
     * The wallets arrive through a cursor loader on a background thread and land on the main
     * looper, which the test drives by hand.
     */
    private static void awaitWallets(MainActivity activity) {
        for (int i = 0; i < 200 && headerName(activity).isEmpty(); i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertFalse("the wallets never loaded", headerName(activity).isEmpty());
    }

    private static void awaitSubtitle(Toolbar toolbar, String subtitle) {
        for (int i = 0; i < 200 && !subtitle.equals(String.valueOf(toolbar.getSubtitle())); i++) {
            shadowOf(Looper.getMainLooper()).idle();
            sleep();
        }
        assertEquals(subtitle, String.valueOf(toolbar.getSubtitle()));
    }

    private static void sleep() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private long insertWallet(String name, int index, long startMoney, boolean countInTotal, boolean archived) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, startMoney);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, countInTotal);
        values.put(Contract.Wallet.ARCHIVED, archived);
        values.put(Contract.Wallet.INDEX, index);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }
}