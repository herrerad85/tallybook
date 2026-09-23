/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.widget.EditText;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.widget.Toolbar;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.material.navigation.NavigationView;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.model.WalletAccount;
import com.oriondev.moneywallet.service.BackupHandlerIntentService;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.base.BaseActivity;
import com.oriondev.moneywallet.ui.fragment.base.NavigableFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.BudgetMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.CategoryMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.DebtMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.EventMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.ModelMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.PersonMultiPanelFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.PlaceMultiPanelFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.RecurrenceMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.SavingMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.SettingMultiPanelFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.TransactionMultiPanelViewPagerFragment;
import com.oriondev.moneywallet.ui.fragment.singlepanel.OverviewSinglePanelFragment;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.ui.view.theme.ThemedRecyclerView;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;
import com.oriondev.moneywallet.utils.SystemBars;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends BaseActivity implements DrawerController, NavigationView.OnNavigationItemSelectedListener, LoaderManager.LoaderCallbacks<Cursor>  {

    private static final String SAVED_SELECTION = "MainActivity::current_selection";

    private static final int LOADER_WALLETS = 1;

    // The drawer menu holds the sections in the first three groups and the wallet list in the
    // last one; the header arrow swaps which of the two is visible.
    private static final int GROUP_SECTIONS = 1;
    private static final int GROUP_TOOLS = 2;
    private static final int GROUP_SETTINGS = 3;
    private static final int GROUP_WALLETS = 4;

    /*package-local*/ static final int ID_SECTION_TRANSACTIONS = 0;
    /*package-local*/ static final int ID_SECTION_CATEGORIES = 1;
    /*package-local*/ static final int ID_SECTION_OVERVIEW = 2;
    /*package-local*/ static final int ID_SECTION_DEBTS = 3;
    /*package-local*/ static final int ID_SECTION_BUDGETS = 4;
    /*package-local*/ static final int ID_SECTION_SAVINGS = 5;
    /*package-local*/ static final int ID_SECTION_EVENTS = 6;
    /*package-local*/ static final int ID_SECTION_RECURRENCES = 7;
    /*package-local*/ static final int ID_SECTION_MODELS = 8;
    /*package-local*/ static final int ID_SECTION_PLACES = 9;
    /*package-local*/ static final int ID_SECTION_PEOPLE = 10;
    /*package-local*/ static final int ID_SECTION_CALCULATOR = 11;
    /*package-local*/ static final int ID_SECTION_CONVERTER = 12;
    /*package-local*/ static final int ID_SECTION_ATM = 13;
    /*package-local*/ static final int ID_SECTION_BANK = 14;
    /*package-local*/ static final int ID_SECTION_SETTING = 15;
    /*package-local*/ static final int ID_SECTION_ABOUT = 17;

    /*package-local*/ static final int ID_ACTION_NEW_WALLET = 18;
    /*package-local*/ static final int ID_ACTION_MANAGE_WALLET = 19;
    // Every account group header takes this plus its position in the drawn order.
    /*package-local*/ static final int ID_GROUP_FIRST = 20;
    // Every wallet entry takes this plus the wallet's own id, so a row bound before a reload
    // still names its wallet.
    /*package-local*/ static final int ID_WALLET_FIRST = 100;

    // Alphabetical, ignoring case, and never merging two names that differ only by case: the
    // TreeMap would drop one of them and draw its wallets under the other's heading.
    /*package-local*/ static final Comparator<String> GROUP_NAME_ORDER = (first, second) -> {
        int result = first.compareToIgnoreCase(second);
        return result != 0 ? result : first.compareTo(second);
    };

    private final MoneyFormatter mMoneyFormatter = MoneyFormatter.getInstance();
    private final List<WalletAccount> mWallets = new ArrayList<>();

    private DrawerLayout mDrawerLayout;
    private NavigationView mNavigationView;
    private ActionBarDrawerToggle mDrawerToggle;
    private View mHeaderView;

    private ImageView mWalletIconView;
    private ImageView mFirstWalletView;
    private ImageView mSecondWalletView;
    private TextView mWalletNameView;
    private TextView mWalletMoneyView;
    private ImageView mWalletListArrowView;

    private WalletAccount mCurrentWallet;
    private boolean mWalletListShown;

    private int mCurrentSelection;
    private Fragment mCurrentFragment;

    private Cursor mCursor;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initializeUi();
        loadUi(savedInstanceState);
        registerReceiver();
        if (savedInstanceState == null && BackendManager.hasUnseenAutoBackupFailure()) {
            showAutoBackupFailureDialog();
        }
    }

    /**
     * The failure is only marked seen when the user answers, so a dialog lost to a rotation is
     * shown again at the next launch.
     */
    private void showAutoBackupFailureDialog() {
        ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_auto_backup_disabled_at_launch)
                .setPositiveButton(R.string.action_open, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BackendManager.markAutoBackupFailuresSeen();
                        Intent intent = new Intent(MainActivity.this, BackupListActivity.class);
                        intent.putExtra(BackupListActivity.BACKUP_MODE, BackupListActivity.FULL);
                        startActivity(intent);
                    }

                })
                .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        BackendManager.markAutoBackupFailuresSeen();
                    }

                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {

                    @Override
                    public void onCancel(DialogInterface dialog) {
                        BackendManager.markAutoBackupFailuresSeen();
                    }

                })
                .show();
    }

    /**
     * Initialize all the ui elements of the activity.
     */
    private void initializeUi() {
        setContentView(R.layout.activity_main);
        initializeNavigationDrawer();
    }

    /**
     * This method must be called during the initialization of the activity in order to
     * setup the account header and the navigation drawer.
     */
    private void initializeNavigationDrawer() {
        mDrawerLayout = findViewById(R.id.drawer_layout);
        // Here and not beside the toggle in setToolbar, which every section calls, so a listener
        // added there would be added again on each one and never taken off.
        mDrawerLayout.addDrawerListener(new DrawerLayout.SimpleDrawerListener() {

            @Override
            public void onDrawerOpened(View drawerView) {
                onThemeSystemBarIcons(ThemeEngine.getTheme());
            }

            @Override
            public void onDrawerClosed(View drawerView) {
                onThemeSystemBarIcons(ThemeEngine.getTheme());
            }

        });
        mNavigationView = findViewById(R.id.navigation_view);
        SystemBars.padDrawerStart(mDrawerLayout, mNavigationView);
        mNavigationView.setNavigationItemSelectedListener(this);
        // the section icons are tinted one by one below, so the wallet icons keep their colors
        mNavigationView.setItemIconTintList(null);
        Menu menu = mNavigationView.getMenu();
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_TRANSACTIONS, R.drawable.ic_shopping_cart_24dp, R.string.menu_transaction);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_CATEGORIES, R.drawable.ic_table_large_24dp, R.string.menu_category);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_OVERVIEW, R.drawable.ic_equalizer_24dp, R.string.menu_overview);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_DEBTS, R.drawable.ic_debt_24dp, R.string.menu_debt);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_BUDGETS, R.drawable.ic_budget_24dp, R.string.menu_budget);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_SAVINGS, R.drawable.ic_saving_24dp, R.string.menu_saving);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_EVENTS, R.drawable.ic_assistant_photo_24dp, R.string.menu_event);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_RECURRENCES, R.drawable.ic_restore_24dp, R.string.menu_recurrences);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_MODELS, R.drawable.ic_bookmark_black_24dp, R.string.menu_models);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_PLACES, R.drawable.ic_place_24dp, R.string.menu_place);
        addEntry(menu, GROUP_SECTIONS, ID_SECTION_PEOPLE, R.drawable.ic_people_black_24dp, R.string.menu_people);
        menu.setGroupCheckable(GROUP_SECTIONS, true, false);
        addEntry(menu, GROUP_TOOLS, ID_SECTION_CALCULATOR, R.drawable.ic_calculator_24dp, R.string.menu_calculator);
        addEntry(menu, GROUP_TOOLS, ID_SECTION_CONVERTER, R.drawable.ic_converter_24dp, R.string.menu_converter);
        addEntry(menu, GROUP_TOOLS, ID_SECTION_ATM, R.drawable.ic_credit_card_24dp, R.string.menu_search_atm);
        addEntry(menu, GROUP_TOOLS, ID_SECTION_BANK, R.drawable.ic_account_balance_24dp, R.string.menu_search_bank);
        addEntry(menu, GROUP_SETTINGS, ID_SECTION_SETTING, R.drawable.ic_settings_24dp, R.string.menu_setting).setCheckable(true);
        addEntry(menu, GROUP_SETTINGS, ID_SECTION_ABOUT, R.drawable.ic_info_outline_24dp, R.string.menu_about);
        mHeaderView = mNavigationView.getHeaderView(0);
        // Top only, to match the menu below it. The navigation view pads its own list from the
        // top and bottom and leaves the sides to its inset scrims, so a header that also took
        // the sides would sit inset from the rows under it.
        SystemBars.pad(mHeaderView, true, false, false);
        mHeaderView.setOnClickListener(view -> showWalletList(!mWalletListShown));
        mWalletIconView = mHeaderView.findViewById(R.id.wallet_icon_image_view);
        mFirstWalletView = mHeaderView.findViewById(R.id.first_wallet_image_view);
        mSecondWalletView = mHeaderView.findViewById(R.id.second_wallet_image_view);
        mWalletNameView = mHeaderView.findViewById(R.id.wallet_name_text_view);
        mWalletMoneyView = mHeaderView.findViewById(R.id.wallet_money_text_view);
        mWalletListArrowView = mHeaderView.findViewById(R.id.wallet_list_arrow_image_view);
        View.OnClickListener quickSwitch = view -> switchWallet((WalletAccount) view.getTag(view.getId()));
        mFirstWalletView.setOnClickListener(quickSwitch);
        mSecondWalletView.setOnClickListener(quickSwitch);
    }

    /**
     * Add one entry to the navigation drawer, with its icon tinted from the current theme.
     * @param menu the drawer menu.
     * @param group of the entry, the drawer draws a divider where the group changes.
     * @param identifier integer id of the entry.
     * @param icon drawable resource of the icon of the entry.
     * @param name string resource of the name of the entry.
     * @return the created entry.
     */
    private MenuItem addEntry(Menu menu, int group, int identifier, @DrawableRes int icon, @StringRes int name) {
        MenuItem item = menu.add(group, identifier, Menu.NONE, name).setIcon(icon).setActionView(new Space(this));
        tintEntry(item, ThemeEngine.getTheme());
        return item;
    }

    private void tintEntry(MenuItem item, ITheme theme) {
        Drawable icon = DrawableCompat.wrap(item.getIcon()).mutate();
        DrawableCompat.setTintList(icon, checkedStates(theme.getDrawerSelectedIconColor(), theme.getDrawerIconColor()));
        item.setIcon(icon);
    }

    private static ColorStateList checkedStates(int checkedColor, int color) {
        return new ColorStateList(new int[][] {{android.R.attr.state_checked}, {}}, new int[] {checkedColor, color});
    }

    private void loadUi(Bundle savedInstanceState) {
        int selection = ID_SECTION_TRANSACTIONS;
        if (savedInstanceState != null) {
            selection = savedInstanceState.getInt(SAVED_SELECTION, ID_SECTION_TRANSACTIONS);
        }
        // TODO maybe we can let the user to specify a preference for the first section to load
        selectSection(selection);
        getSupportLoaderManager().restartLoader(LOADER_WALLETS, null, this);
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(LocalAction.ACTION_BACKUP_SERVICE_FINISHED);
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver, filter);
    }

    /**
     * Store all the instance information in order to restore them if the activity is recreated.
     * The only information to store here is the current section loaded. The fragment will manage
     * the lifecycle internally, no need to save his state here.
     * @param savedState of the current instance of the activity.
     */
    @Override
    protected void onSaveInstanceState(Bundle savedState) {
        super.onSaveInstanceState(savedState);
        savedState.putInt(SAVED_SELECTION, mCurrentSelection);
    }

    /**
     * Override this method to check when the back button is pressed if the drawer is open.
     * If open it will be closed.
     */
    @Override
    public void onBackPressed() {
        if (mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            closeDrawer();
        } else if (mCurrentFragment instanceof NavigableFragment) {
            if (!((NavigableFragment) mCurrentFragment).navigateBack() && !selectTransactionsSection()) {
                super.onBackPressed();
            }
        } else if (!selectTransactionsSection()) {
            super.onBackPressed();
        }
    }

    /**
     * Move back to the transactions section the way a drawer tap does, so the highlighted
     * drawer entry and the fragment on screen cannot drift apart.
     * @return true when the section change has been queued, false when transactions is
     * already showing, in which case the caller should fall back to the default behavior.
     * The fragment transaction is committed, not run, so the previous section is still on
     * screen when this returns.
     */
    private boolean selectTransactionsSection() {
        if (mCurrentSelection == ID_SECTION_TRANSACTIONS) {
            return false;
        }
        selectSection(ID_SECTION_TRANSACTIONS);
        return true;
    }

    /**
     * Highlight a section in the drawer and load its fragment.
     * @param identifier of the section.
     */
    private void selectSection(int identifier) {
        mCurrentSelection = identifier;
        mNavigationView.setCheckedItem(identifier);
        loadSection(identifier);
    }

    private void closeDrawer() {
        mDrawerLayout.closeDrawer(GravityCompat.START);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * Set toolbar for this activity. Every section brings its own toolbar, so the toggle that
     * draws the drawer icon on it and opens the drawer from it is rebuilt on each one.
     * @param toolbar to set as main toolbar.
     */
    @Override
    public void setToolbar(Toolbar toolbar) {
        if (mDrawerToggle != null) {
            mDrawerLayout.removeDrawerListener(mDrawerToggle);
        }
        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar,
                R.string.description_navigation_drawer_open, R.string.description_navigation_drawer_close);
        mDrawerLayout.addDrawerListener(mDrawerToggle);
        mDrawerToggle.syncState();
    }

    /**
     * Set the lock mode for the activity drawer.
     * @param lockMode to set to the navigation drawer.
     */
    @Override
    public void setDrawerLockMode(int lockMode) {
        mDrawerLayout.setDrawerLockMode(lockMode);
    }

    /**
     * Callback when a drawer entry is tapped, a section, a tool, or one of the wallet list
     * entries the header shows in their place.
     * @param item tapped.
     * @return true if the entry should be highlighted, which is only right for a section.
     */
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int identifier = item.getItemId();
        if (item.getGroupId() == GROUP_WALLETS) {
            if (identifier == ID_ACTION_NEW_WALLET) {
                startActivity(new Intent(this, NewEditWalletActivity.class));
            } else if (identifier == ID_ACTION_MANAGE_WALLET) {
                startActivity(new Intent(this, WalletListActivity.class));
            } else {
                WalletAccount wallet = findWallet(identifier - ID_WALLET_FIRST);
                if (wallet != null) {
                    switchWallet(wallet);
                    return false;
                }
            }
            showWalletList(false);
            closeDrawer();
            return false;
        }
        switch (identifier) {
            case ID_SECTION_CALCULATOR:
                startActivity(new Intent(this, CalculatorActivity.class));
                break;
            case ID_SECTION_CONVERTER:
                startActivity(new Intent(this, CurrencyConverterActivity.class));
                break;
            case ID_SECTION_ATM:
                showAtmSearchDialog();
                break;
            case ID_SECTION_BANK:
                showBankSearchDialog();
                break;
            case ID_SECTION_ABOUT:
                startActivity(new Intent(this, AboutActivity.class));
                break;
            default:
                selectSection(identifier);
                closeDrawer();
                return true;
        }
        closeDrawer();
        return false;
    }

    /**
     * Make a wallet the current one: the header shows it, the preference that every screen
     * filters by is written, and the drawer goes back to the sections and closes.
     * @param wallet to switch to.
     */
    private void switchWallet(WalletAccount wallet) {
        mCurrentWallet = wallet;
        PreferenceManager.setCurrentWallet(this, wallet.getId(), wallet.getName());
        bindHeader();
        buildWalletMenu();
        showWalletList(false);
        closeDrawer();
    }

    /**
     * Swap the sections for the wallet list, or back. The highlighted entry follows: the
     * current wallet while the list is shown, the current section otherwise.
     * @param show true for the wallet list.
     */
    private void showWalletList(boolean show) {
        mWalletListShown = show;
        Menu menu = mNavigationView.getMenu();
        menu.setGroupVisible(GROUP_SECTIONS, !show);
        menu.setGroupVisible(GROUP_TOOLS, !show);
        menu.setGroupVisible(GROUP_SETTINGS, !show);
        menu.setGroupVisible(GROUP_WALLETS, show);
        if (show) {
            if (mCurrentWallet != null) {
                mNavigationView.setCheckedItem(walletItemId(mCurrentWallet));
            }
        } else {
            mNavigationView.setCheckedItem(mCurrentSelection);
        }
        mWalletListArrowView.animate().rotation(show ? 180 : 0).start();
    }

    private void showAtmSearchDialog() {
        View inputView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
        inputEditText.setHint(R.string.hint_atm_name);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_atm_search)
                .setView(inputView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri mapUri = Uri.parse("geo:0,0?q=atm " + inputEditText.getText());
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                        try {
                            startActivity(mapIntent);
                        } catch (ActivityNotFoundException ignore) {
                            showActivityNotFoundDialog();
                        }
                    }

                })
                .create();
        ThemedDialog.showWithInput(dialog, inputEditText, false);
    }

    private void showBankSearchDialog() {
        View inputView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
        inputEditText.setHint(R.string.hint_bank_name);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_bank_search)
                .setView(inputView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Uri mapUri = Uri.parse("geo:0,0?q=bank " + inputEditText.getText());
                        Intent mapIntent = new Intent(Intent.ACTION_VIEW, mapUri);
                        try {
                            startActivity(mapIntent);
                        } catch (ActivityNotFoundException ignore) {
                            showActivityNotFoundDialog();
                        }
                    }

                })
                .create();
        ThemedDialog.showWithInput(dialog, inputEditText, false);
    }

    private void showActivityNotFoundDialog() {
        ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_error)
                .setMessage(R.string.message_error_activity_not_found)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /**
     * Load the fragment of the specified section inside the frame of the activity.
     * If the fragment is already in the stack of the fragment manager this method will
     * reuse it without spending time in recreating a new one.
     * @param identifier of the section.
     */
    private void loadSection(int identifier) {
        // The outgoing screen may have left the icons set for a scrolled panel, and nothing
        // else runs on this path.
        resetStatusBarIconsToAppBar();
        FragmentManager manager = getSupportFragmentManager();
        String tag = getTagById(identifier);
        mCurrentFragment = manager.findFragmentByTag(tag);
        if (mCurrentFragment == null) {
            mCurrentFragment = buildFragmentById(identifier);
        }
        manager.beginTransaction().replace(R.id.fragment_container, mCurrentFragment, tag).commit();
    }

    /**
     * Generate a unique string as tag to identify every fragment into the fragment manager.
     * @param identifier of the drawer item.
     * @return a unique tag.
     */
    private String getTagById(int identifier) {
        return String.format(Locale.ENGLISH, "MainActivity::drawer::%d", identifier);
    }

    /**
     * This method creates a new fragment of the specified section.
     * @param identifier of the section.
     * @return the new created fragment.
     * @throws IllegalArgumentException if the provided id is not a
     * valid section identifier.
     */
    private Fragment buildFragmentById(int identifier) {
        switch (identifier) {
            case ID_SECTION_TRANSACTIONS:
                return new TransactionMultiPanelViewPagerFragment();
            case ID_SECTION_CATEGORIES:
                return new CategoryMultiPanelViewPagerFragment();
            case ID_SECTION_OVERVIEW:
                return new OverviewSinglePanelFragment();
            case ID_SECTION_DEBTS:
                return new DebtMultiPanelViewPagerFragment();
            case ID_SECTION_BUDGETS:
                return new BudgetMultiPanelViewPagerFragment();
            case ID_SECTION_SAVINGS:
                return new SavingMultiPanelViewPagerFragment();
            case ID_SECTION_EVENTS:
                return new EventMultiPanelViewPagerFragment();
            case ID_SECTION_RECURRENCES:
                return new RecurrenceMultiPanelViewPagerFragment();
            case ID_SECTION_MODELS:
                return new ModelMultiPanelViewPagerFragment();
            case ID_SECTION_PLACES:
                return new PlaceMultiPanelFragment();
            case ID_SECTION_PEOPLE:
                return new PersonMultiPanelFragment();
            case ID_SECTION_SETTING:
                return new SettingMultiPanelFragment();
            default:
                throw new IllegalArgumentException("Invalid section id: " + identifier);
        }
    }

    /**
     * Query content resolver to retrieve all wallets from the database.
     * @param id of the loader.
     * @param args bundle of arguments.
     * @return the cursor loader that will retrieve the content from the database.
     */
    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String[] projection = new String[] {
                Contract.Wallet.ID,
                Contract.Wallet.NAME,
                Contract.Wallet.ICON,
                Contract.Wallet.COUNT_IN_TOTAL,
                Contract.Wallet.CURRENCY,
                Contract.Wallet.START_MONEY,
                Contract.Wallet.TOTAL_MONEY,
                Contract.Wallet.ARCHIVED,
                Contract.Wallet.GROUP
        };
        Uri uri = DataContentProvider.CONTENT_WALLETS;
        String sortOrder = Contract.Wallet.INDEX + " ASC, " + Contract.Wallet.NAME + " ASC";
        return new CursorLoader(this, uri, projection, null, null, sortOrder);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        mCursor = cursor;
        mWallets.clear();
        Money total = new Money();
        if (cursor != null) {
            int indexWalletId = cursor.getColumnIndex(Contract.Wallet.ID);
            int indexWalletName = cursor.getColumnIndex(Contract.Wallet.NAME);
            int indexWalletIcon = cursor.getColumnIndex(Contract.Wallet.ICON);
            int indexCurrency = cursor.getColumnIndex(Contract.Wallet.CURRENCY);
            int indexWalletInitial = cursor.getColumnIndex(Contract.Wallet.START_MONEY);
            int indexWalletTotal = cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY);
            int indexWalletArchived = cursor.getColumnIndex(Contract.Wallet.ARCHIVED);
            int indexInTotal = cursor.getColumnIndex(Contract.Wallet.COUNT_IN_TOTAL);
            int indexWalletGroup = cursor.getColumnIndex(Contract.Wallet.GROUP);
            for (int i = 0; i < cursor.getCount(); i++) {
                cursor.moveToPosition(i);
                String currency = cursor.getString(indexCurrency);
                long money = cursor.getLong(indexWalletInitial) + cursor.getLong(indexWalletTotal);
                if (cursor.getInt(indexWalletArchived) == 0) {
                    mWallets.add(new WalletAccount(
                            cursor.getLong(indexWalletId),
                            cursor.getString(indexWalletName),
                            IconLoader.parse(cursor.getString(indexWalletIcon)),
                            new Money(currency, money),
                            cursor.getString(indexWalletGroup)
                    ));
                }
                // an archived wallet stays out of the list but still counts toward the total
                if (cursor.getInt(indexInTotal) == 1) {
                    total.addMoney(currency, money);
                }
            }
        }
        if (!mWallets.isEmpty() && total.getNumberOfCurrencies() > 0) {
            String name = getString(R.string.total_wallet_name);
            mWallets.add(new WalletAccount(PreferenceManager.TOTAL_WALLET_ID, name, new ColorIcon("#000000", name.substring(0, 1)), total));
        }
        long currentWalletId = PreferenceManager.getCurrentWallet();
        mCurrentWallet = findWallet(currentWalletId);
        if (mCurrentWallet == null && !mWallets.isEmpty()) {
            // the header falls back to the first wallet. Only when no wallet has ever been chosen
            // is that choice written as the current one, which also broadcasts the change
            mCurrentWallet = mWallets.get(0);
            if (currentWalletId == PreferenceManager.NO_CURRENT_WALLET) {
                PreferenceManager.setCurrentWallet(this, mCurrentWallet.getId(), mCurrentWallet.getName());
            }
        }
        buildWalletMenu();
        bindHeader();
    }

    private WalletAccount findWallet(long id) {
        for (WalletAccount wallet : mWallets) {
            if (wallet.getId() == id) {
                return wallet;
            }
        }
        return null;
    }

    /**
     * Rebuild the wallet group of the drawer menu from the wallet list, the account groups first
     * in alphabetical order, each led by a row carrying the group's own subtotal, then the wallets
     * that are in no group, then the two wallet actions. Every row carries its balance at the end.
     *
     * The wallet list keeps the order the loader gave it inside each section, and the Total is the
     * last entry of it and belongs to no group, so a database where nobody has named a group comes
     * out as the menu built before groups existed.
     */
    private void buildWalletMenu() {
        ITheme theme = ThemeEngine.getTheme();
        Menu menu = mNavigationView.getMenu();
        menu.removeGroup(GROUP_WALLETS);
        Map<String, List<WalletAccount>> groups = new TreeMap<>(GROUP_NAME_ORDER);
        List<WalletAccount> ungrouped = new ArrayList<>();
        for (WalletAccount wallet : mWallets) {
            if (TextUtils.isEmpty(wallet.getGroup())) {
                ungrouped.add(wallet);
            } else {
                List<WalletAccount> members = groups.get(wallet.getGroup());
                if (members == null) {
                    members = new ArrayList<>();
                    groups.put(wallet.getGroup(), members);
                }
                members.add(wallet);
            }
        }
        int headerId = ID_GROUP_FIRST;
        for (Map.Entry<String, List<WalletAccount>> group : groups.entrySet()) {
            Money subtotal = new Money();
            for (WalletAccount wallet : group.getValue()) {
                subtotal.addMoney(wallet.getMoney());
            }
            // the row is disabled so that it cannot be tapped or checked; the library binds an
            // action view and the title color of a disabled row exactly as it binds an enabled one
            menu.add(GROUP_WALLETS, headerId++, Menu.NONE, group.getKey())
                    .setActionView(buildMoneyView(subtotal, theme.getDrawerTextColor()))
                    .setEnabled(false);
            for (WalletAccount wallet : group.getValue()) {
                addWalletEntry(menu, wallet, theme);
            }
        }
        for (WalletAccount wallet : ungrouped) {
            addWalletEntry(menu, wallet, theme);
        }
        addEntry(menu, GROUP_WALLETS, ID_ACTION_NEW_WALLET, R.drawable.ic_add_24dp, R.string.action_new_wallet);
        addEntry(menu, GROUP_WALLETS, ID_ACTION_MANAGE_WALLET, R.drawable.ic_settings_24dp, R.string.action_manage_wallets);
        menu.setGroupVisible(GROUP_WALLETS, mWalletListShown);
        if (mWalletListShown && mCurrentWallet != null) {
            mNavigationView.setCheckedItem(walletItemId(mCurrentWallet));
        }
    }

    private void addWalletEntry(Menu menu, WalletAccount wallet, ITheme theme) {
        int textColor = wallet == mCurrentWallet ? theme.getDrawerSelectedTextColor() : theme.getDrawerTextColor();
        menu.add(GROUP_WALLETS, walletItemId(wallet), Menu.NONE, wallet.getName())
                .setIcon(wallet.getIcon().getDrawable(this))
                .setActionView(buildMoneyView(wallet.getMoney(), textColor))
                .setCheckable(true);
    }

    private TextView buildMoneyView(Money money, int textColor) {
        TextView moneyView = new TextView(this);
        moneyView.setText(mMoneyFormatter.getNotTintedString(money));
        moneyView.setTextColor(textColor);
        moneyView.setGravity(Gravity.CENTER_VERTICAL);
        return moneyView;
    }

    private static int walletItemId(WalletAccount wallet) {
        return ID_WALLET_FIRST + (int) wallet.getId();
    }

    /**
     * Show the current wallet in the header, and the first two other wallets as the one tap
     * switches at its top end.
     */
    private void bindHeader() {
        if (mCurrentWallet != null) {
            IconLoader.loadInto(mCurrentWallet.getIcon(), mWalletIconView);
            mWalletIconView.setVisibility(View.VISIBLE);
            mWalletNameView.setText(mCurrentWallet.getName());
            mWalletMoneyView.setText(mMoneyFormatter.getNotTintedString(mCurrentWallet.getMoney()));
        } else {
            mWalletIconView.setVisibility(View.INVISIBLE);
            mWalletNameView.setText(R.string.msg_no_wallet_found);
            mWalletMoneyView.setText(R.string.msg_add_one_wallet);
        }
        List<WalletAccount> others = new ArrayList<>(mWallets);
        others.remove(mCurrentWallet);
        bindQuickSwitch(mFirstWalletView, others.size() > 0 ? others.get(0) : null);
        bindQuickSwitch(mSecondWalletView, others.size() > 1 ? others.get(1) : null);
    }

    private void bindQuickSwitch(ImageView view, WalletAccount wallet) {
        // keyed, because Glide keeps its own request in the plain tag of any view it loads into
        view.setTag(view.getId(), wallet);
        view.setVisibility(wallet != null ? View.VISIBLE : View.GONE);
        if (wallet != null) {
            IconLoader.loadInto(wallet.getIcon(), view);
            view.setContentDescription(wallet.getName());
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        if (mCursor != null) {
            if (!mCursor.isClosed()) {
                mCursor.close();
            }
            mCursor = null;
        }
    }

    @Override
    protected void onThemeSetup(ITheme theme) {
        super.onThemeSetup(theme);
        applyNavigationDrawerTheme(theme);
    }

    private void applyNavigationDrawerTheme(ITheme theme) {
        applyNavigationDrawerHeaderTheme(theme);
        applyNavigationDrawerBodyTheme(theme);
    }

    private void applyNavigationDrawerHeaderTheme(ITheme theme) {
        int backgroundColor = theme.getColorPrimary();
        int textColor = theme.getBestTextColor(backgroundColor);
        mHeaderView.setBackgroundColor(backgroundColor);
        mWalletNameView.setTextColor(textColor);
        mWalletMoneyView.setTextColor(textColor);
        mWalletListArrowView.setColorFilter(textColor, PorterDuff.Mode.SRC_ATOP);
        // a thin ring keeps the wallet icon visible when its color is the header's own
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(Math.round(getResources().getDisplayMetrics().density), ColorUtils.setAlphaComponent(textColor, 0x66));
        mWalletIconView.setBackground(ring);
    }

    private void applyNavigationDrawerBodyTheme(ITheme theme) {
        // the library gives the drawer its outline, and with it its shadow, only while the background
        // is still the shape drawable it built, so that drawable is recolored and not replaced
        Drawable background = mNavigationView.getBackground();
        if (background instanceof MaterialShapeDrawable) {
            ((MaterialShapeDrawable) background).setFillColor(ColorStateList.valueOf(theme.getDrawerBackgroundColor()));
        } else {
            mNavigationView.setBackgroundColor(theme.getDrawerBackgroundColor());
        }
        mNavigationView.setItemTextColor(checkedStates(theme.getDrawerSelectedTextColor(), theme.getDrawerTextColor()));
        // the row's own foreground is the theme's ripple, so the background only marks the open entry
        StateListDrawable checkedBackground = new StateListDrawable();
        checkedBackground.addState(new int[] {android.R.attr.state_checked}, new ColorDrawable(theme.getDrawerSelectedItemColor()));
        checkedBackground.addState(new int[0], new ColorDrawable(Color.TRANSPARENT));
        mNavigationView.setItemBackground(checkedBackground);
        Menu menu = mNavigationView.getMenu();
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getGroupId() != GROUP_WALLETS) {
                tintEntry(item, theme);
            }
        }
        buildWalletMenu();
        for (int i = 0; i < mNavigationView.getChildCount(); i++) {
            View child = mNavigationView.getChildAt(i);
            if (child instanceof RecyclerView) {
                ThemedRecyclerView.applyTheme((RecyclerView) child, theme);
            }
        }
    }

    /**
     * An open drawer covers the toolbar, and the menu scrolls under the status bar, so what is up
     * there is the drawer's own scrim over its background, not anything the rest of the app
     * painted. The two are composited here instead of guessed, since the drawer background follows
     * the theme and the scrim does not. The drawer is asked directly, because a drawer restored
     * already open after a rotation never fires the opened callback.
     */
    @Override
    protected int getColorBehindStatusBar(ITheme theme) {
        if (!mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            return super.getColorBehindStatusBar(theme);
        }
        int scrim = ContextCompat.getColor(this, R.color.system_bar_scrim_dark);
        return ColorUtils.compositeColors(scrim, theme.getDrawerBackgroundColor());
    }

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                int action = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0);
                if (action == BackupHandlerIntentService.ACTION_RESTORE) {
                    getSupportLoaderManager().restartLoader(LOADER_WALLETS, null, MainActivity.this);
                }
            }
        }

    };
}