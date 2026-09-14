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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.CurrentWalletController;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelAppBarItemFragment;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.calendar.MonthView;
import com.oriondev.moneywallet.ui.view.calendar.OnDateSelectedListener;
import com.oriondev.moneywallet.ui.view.calendar.TimelineView;
import com.oriondev.moneywallet.utils.DateUtils;

import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by andrea on 06/04/18.
 */
public class CalendarMultiPanelFragment extends MultiPanelAppBarItemFragment implements MonthView.OnMonthSelectedListener, OnDateSelectedListener, TimelineView.OnMonthScrolledListener, SwipeRefreshLayout.OnRefreshListener, TransactionCursorAdapter.ActionListener, LoaderManager.LoaderCallbacks<Cursor>, CurrentWalletController {

    private static final String SECONDARY_PANEL_FRAGMENT_TAG = "CalendarMultiPanelFragment::Tag::TransactionItemFragment";

    private static final String ARG_SELECTED_YEAR = "CalendarMultiPanelFragment::Arguments::Year";
    private static final String ARG_SELECTED_MONTH = "CalendarMultiPanelFragment::Arguments::Month";
    private static final String ARG_SELECTED_DAY = "CalendarMultiPanelFragment::Arguments::Day";

    private static final String STATE_SELECTED_YEAR = "CalendarMultiPanelFragment::State::Year";
    private static final String STATE_SELECTED_MONTH = "CalendarMultiPanelFragment::State::Month";
    private static final String STATE_SELECTED_DAY = "CalendarMultiPanelFragment::State::Day";

    private static final int DEFAULT_LOADER_ID = 4834;
    private static final int MARKED_DAYS_LOADER_ID = 4835;

    private static final String COLUMN_DAY = "day";

    /** No day at all, which no real date can collide with because no year is negative here. */
    static final int NO_DAY = -1;

    /**
     * One row per day that has a transaction. DISTINCT rides in the first column because the
     * provider passes a projection straight into the SELECT clause.
     */
    private static final String[] MARKED_DAYS_PROJECTION = new String[] {
            "DISTINCT DATE(" + Contract.Transaction.DATE + ") AS " + COLUMN_DAY
    };

    private BroadcastReceiver mCurrentWalletObserver;

    private MonthView mMonthView;
    private TimelineView mTimelineView;
    private AdvancedRecyclerView mAdvancedRecyclerView;
    private AbstractCursorAdapter mAbstractCursorAdapter;
    private TransactionSelectionMode mSelectionMode;

    @Override
    protected View onInflateRootLayout(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_multi_panel_appbar_without_scroll, container, false);
    }

    @Override
    protected void onCreatePrimaryAppBar(LayoutInflater inflater, @NonNull ViewGroup primaryAppBar, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_calendar_app_bar_layout, primaryAppBar, true);
        mMonthView = view.findViewById(R.id.month_view);
        mMonthView.setFirstDate(1900, Calendar.JANUARY);
        mMonthView.setOnMonthSelectedListener(this);
    }

    @Override
    protected void onCreatePrimaryPanel(LayoutInflater inflater, @NonNull ViewGroup primaryPanel, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_calendar_primary_panel, primaryPanel, true);
        mTimelineView = view.findViewById(R.id.timeline_view);
        mAdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view);
        mTimelineView.setFirstDate(1900, Calendar.JANUARY, 1);
        mTimelineView.setLastDate(2100, Calendar.DECEMBER, 31);
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(this);
        mSelectionMode = new TransactionSelectionMode(this, this, adapter);
        mAbstractCursorAdapter = adapter;
        mAdvancedRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mAdvancedRecyclerView.setEmptyText(R.string.message_no_transaction_found);
        mAdvancedRecyclerView.setAdapter(mAbstractCursorAdapter);
        mAdvancedRecyclerView.setOnRefreshListener(this);
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        if (savedInstanceState != null && savedInstanceState.containsKey(STATE_SELECTED_YEAR)) {
            year = savedInstanceState.getInt(STATE_SELECTED_YEAR);
            month = savedInstanceState.getInt(STATE_SELECTED_MONTH);
            day = savedInstanceState.getInt(STATE_SELECTED_DAY);
        }
        // The listener is attached after the first selection, and the first load is made here,
        // because the strip reports nothing when the day selected is the one it already holds.
        // After setFirstDate above that day is 1 January 1900, which a restored date can equal.
        mTimelineView.setSelectedDate(year, month, day);
        mTimelineView.setOnDateSelectedListener(this);
        mTimelineView.setOnMonthScrolledListener(this);
        onDateSelected(year, month, day, mTimelineView.getSelectedPosition());
        LoaderManager.getInstance(this).initLoader(MARKED_DAYS_LOADER_ID, null, mMarkedDaysCallbacks);
    }

    /**
     * The day is saved as a date and not as the strip position that also identifies it.
     */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mTimelineView != null) {
            outState.putInt(STATE_SELECTED_YEAR, mTimelineView.getSelectedYear());
            outState.putInt(STATE_SELECTED_MONTH, mTimelineView.getSelectedMonth());
            outState.putInt(STATE_SELECTED_DAY, mTimelineView.getSelectedDay());
        }
        if (mSelectionMode != null) {
            mSelectionMode.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSelectionMode.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    protected SecondaryPanelFragment onCreateSecondaryPanel() {
        return new TransactionItemFragment();
    }

    @Override
    protected String getSecondaryFragmentTag() {
        return SECONDARY_PANEL_FRAGMENT_TAG;
    }

    @Override
    protected int getTitleRes() {
        return R.string.title_activity_calendar;
    }

    @Override
    protected boolean showsCurrentWallet() {
        return true;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return false;
    }

    @Override
    public void onMonthSelected(int year, int month, int index) {
        mTimelineView.setSelectedDate(year, month, 1);
    }

    @Override
    public void onDateSelected(int year, int month, int day, int index) {
        mMonthView.setSelectedMonth(year, month, false, true);
        loadTransactions(year, month, day);
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
    }

    /**
     * The month row names the month the strip is on, so dragging the strip into another month
     * moves the row with it. A day number says nothing about which month it belongs to, and the
     * row is the only thing on the screen that appears to answer that.
     *
     * Nothing is done while the strip is still inside the month the row already shows, because
     * setSelectedMonth centers the row on every call and the row would slide under the finger
     * through a whole month of cells.
     *
     * Without callListener the row would call back into onMonthSelected below, which sends the
     * strip to the first of the month and takes it out from under the drag. The day selected and
     * the list under the strip are left alone, which is why the transactions are not loaded again.
     */
    @Override
    public void onMonthScrolled(int year, int month) {
        if (year == mMonthView.getSelectedYear() && month == mMonthView.getSelectedMonth()) {
            return;
        }
        mMonthView.setSelectedMonth(year, month, false, true);
    }

    @Override
    public void onHeaderClick(Date startDate, Date endDate) {
        // never used here
    }

    @Override
    public void onTransactionClick(long id) {
        showItemId(id);
        showSecondaryPanel();
    }

    @Override
    public void onSelectionChanged(int count) {
        mSelectionMode.onSelectionChanged(count);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        if (activity != null && args != null) {
            Date date = DateUtils.getDate(
                    args.getInt(ARG_SELECTED_YEAR),
                    args.getInt(ARG_SELECTED_MONTH),
                    args.getInt(ARG_SELECTED_DAY)
            );
            Uri uri = DataContentProvider.CONTENT_TRANSACTIONS;
            String selection = walletSelection()
                    + " AND DATE(" + Contract.Transaction.DATE + ") == DATE('" + DateUtils.getSQLDateString(date) + "')";
            String sortOrder = Contract.Transaction.DATE + " DESC";
            return new CursorLoader(activity, uri, null, selection, null, sortOrder);
        }
        return null;
    }

    /**
     * The wallet this screen is about. The marks under the strip come from this same rule, so a
     * day is marked when the list has something to show for it and not when it has not.
     *
     * The wallet id is written into the text and not bound, so neither query carries arguments.
     */
    private static String walletSelection() {
        long currentWallet = PreferenceManager.getCurrentWallet();
        if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
            return Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1";
        }
        return Contract.Transaction.WALLET_ID + " = " + currentWallet;
    }

    /**
     * The days this wallet has transactions on, one row each, which is all the strip needs to
     * know.
     *
     * ponytail: few rows come back and that says nothing about the work. The provider has no
     * query that answers this on its own, so the dates come from the transaction query, whose
     * GROUP BY leaves the wallet clause outside a subquery SQLite cannot push it into. EXPLAIN
     * QUERY PLAN answers SCAN t, every transaction row in every wallet, and it answers the same
     * for the day list this screen already runs on every day tapped. Tapping a day does not run
     * this one again, so a tap costs what it always did, and the second scan falls on opening the
     * screen, changing wallet, pulling to refresh, and any change the cursor is watching for. A
     * provider uri of its own is the way out.
     */
    private final LoaderManager.LoaderCallbacks<Cursor> mMarkedDaysCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
            return new CursorLoader(requireContext(), DataContentProvider.CONTENT_TRANSACTIONS,
                    MARKED_DAYS_PROJECTION, walletSelection(), null, null);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
            if (mTimelineView != null) {
                mTimelineView.setMarkedDays(readDays(cursor));
            }
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            if (mTimelineView != null) {
                mTimelineView.setMarkedDays(Collections.emptySet());
            }
        }

    };

    /**
     * The dates of a marked days cursor, as the keys the strip matches its cells against.
     */
    private static Set<Integer> readDays(@Nullable Cursor cursor) {
        if (cursor == null) {
            return Collections.emptySet();
        }
        Set<Integer> days = new HashSet<>(cursor.getCount());
        int index = cursor.getColumnIndex(COLUMN_DAY);
        for (cursor.moveToPosition(-1); cursor.moveToNext(); ) {
            int day = dayKey(cursor.getString(index));
            if (day != NO_DAY) {
                days.add(day);
            }
        }
        return days;
    }

    /**
     * One yyyy-MM-dd date read as the key the strip matches its cells against, or {@link #NO_DAY}
     * for anything that is not one. The month is put in the strip's terms here, counted from zero.
     *
     * DATE() returns either that spelling or null, so a value SQLite could not read arrives as
     * null and a value it could is ten characters of digits and dashes.
     */
    static int dayKey(@Nullable String date) {
        if (date == null || date.length() != 10) {
            return NO_DAY;
        }
        return TimelineView.dayKey(
                Integer.parseInt(date.substring(0, 4)),
                Integer.parseInt(date.substring(5, 7)) - 1,
                Integer.parseInt(date.substring(8, 10)));
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        mAbstractCursorAdapter.changeCursor(cursor);
        if (cursor != null && cursor.getCount() > 0) {
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.READY);
        } else {
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.EMPTY);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mAbstractCursorAdapter.changeCursor(null);
    }

    @Override
    public void onRefresh() {
        loadTransactions(
                mTimelineView.getSelectedYear(),
                mTimelineView.getSelectedMonth(),
                mTimelineView.getSelectedDay()
        );
        // a pull is what somebody does when the screen looks wrong, and the marks are part of
        // what they are looking at
        loadMarkedDays();
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.REFRESHING);
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        mCurrentWalletObserver = PreferenceManager.registerCurrentWalletObserver(context, this);
    }

    @Override
    public void onDetach() {
        PreferenceManager.unregisterCurrentWalletObserver(getActivity(), mCurrentWalletObserver);
        super.onDetach();
    }

    @Override
    public void onCurrentWalletChanged(long walletId) {
        if (mTimelineView == null) {
            return;
        }
        mSelectionMode.finish();
        // this screen names the wallet in its toolbar, so the day list has to follow it
        loadTransactions(
                mTimelineView.getSelectedYear(),
                mTimelineView.getSelectedMonth(),
                mTimelineView.getSelectedDay()
        );
        loadMarkedDays();
        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
    }

    private void loadTransactions(int year, int month, int day) {
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_SELECTED_YEAR, year);
        arguments.putInt(ARG_SELECTED_MONTH, month);
        arguments.putInt(ARG_SELECTED_DAY, day);
        LoaderManager.getInstance(this).restartLoader(DEFAULT_LOADER_ID, arguments, this);
    }

    /**
     * Read again for a wallet the query cannot be told about, since the wallet is read where the
     * selection is built. A transaction added or removed inside this wallet needs none of this,
     * because the cursor is watching the transactions it came from.
     */
    private void loadMarkedDays() {
        LoaderManager.getInstance(this).restartLoader(MARKED_DAYS_LOADER_ID, null, mMarkedDaysCallbacks);
    }
}