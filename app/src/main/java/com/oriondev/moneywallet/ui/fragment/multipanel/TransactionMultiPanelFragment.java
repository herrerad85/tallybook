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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.view.View;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.CurrentWalletController;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter;
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelCursorListItemFragment;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.TransactionSelectionMode;
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.utils.DateUtils;

import java.util.Date;

/**
 * Created by andrea on 08/04/18.
 */
public class TransactionMultiPanelFragment extends MultiPanelCursorListItemFragment implements TransactionCursorAdapter.ActionListener, CurrentWalletController {

    private static final String FILTER_TYPE = "TransactionMultiPanelFragment::Arguments::FilterType";
    private static final String FILTER_ID = "TransactionMultiPanelFragment::Arguments::FilterId";
    private static final String FILTER_START_DATE = "TransactionMultiPanelFragment::Arguments::FilterStartDate";
    private static final String FILTER_END_DATE = "TransactionMultiPanelFragment::Arguments::FilterEndDate";

    private static final String SECONDARY_PANEL_TAG = "TransactionMultiPanelFragment::Tag::SecondaryPanel";

    private static final int HIDDEN_TRANSACTIONS_LOADER_ID = 60002;

    private static final String[] HIDDEN_PROJECTION = new String[] {
            Contract.Transaction.WALLET_ID,
            Contract.Transaction.WALLET_COUNT_IN_TOTAL,
            Contract.Transaction.DATE
    };

    public enum FilterType {
        CATEGORY,
        DEBT,
        BUDGET,
        SAVING,
        EVENT,
        PLACE,
        PERSON
    }

    public static TransactionMultiPanelFragment newInstance(FilterType type, long id, Date startDate, Date endDate) {
        TransactionMultiPanelFragment fragment = new TransactionMultiPanelFragment();
        Bundle arguments = new Bundle();
        arguments.putSerializable(FILTER_TYPE, type);
        arguments.putLong(FILTER_ID, id);
        arguments.putSerializable(FILTER_START_DATE, startDate);
        arguments.putSerializable(FILTER_END_DATE, endDate);
        fragment.setArguments(arguments);
        return fragment;
    }

    private BroadcastReceiver mBroadcastReceiver;
    private TransactionSelectionMode mSelectionMode;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mBroadcastReceiver = PreferenceManager.registerCurrentWalletObserver(context, this);
    }

    @Override
    public void onDetach() {
        PreferenceManager.unregisterCurrentWalletObserver(getActivity(), mBroadcastReceiver);
        super.onDetach();
    }

    @Override
    protected SecondaryPanelFragment onCreateSecondaryPanel() {
        return new TransactionItemFragment();
    }

    @Override
    protected String getSecondaryFragmentTag() {
        return SECONDARY_PANEL_TAG;
    }

    @Override
    protected void onPrepareRecyclerView(AdvancedRecyclerView recyclerView) {
        recyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        recyclerView.setEmptyText(R.string.message_no_transaction_found);
    }

    @Override
    protected AbstractCursorAdapter onCreateAdapter() {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(this);
        mSelectionMode = new TransactionSelectionMode(this, this, adapter);
        return adapter;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mSelectionMode.onRestoreInstanceState(savedInstanceState);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mSelectionMode != null) {
            mSelectionMode.onSaveInstanceState(outState);
        }
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        Bundle arguments = getArguments();
        if (activity != null && arguments != null) {
            Uri uri = buildItemTransactionsUri(arguments);
            Date startDate = (Date) arguments.getSerializable(FILTER_START_DATE);
            Date endDate = (Date) arguments.getSerializable(FILTER_END_DATE);
            String selection;
            String[] selectionArgs;
            long currentWallet = PreferenceManager.getCurrentWallet();
            if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                selection = Contract.Transaction.WALLET_COUNT_IN_TOTAL + " = 1";
                selectionArgs = null;
            } else {
                selection = Contract.Transaction.WALLET_ID + " = ?";
                selectionArgs = new String[] {String.valueOf(currentWallet)};
            }
            selection += " AND DATETIME(" + Contract.Transaction.DATE + ") <= DATETIME('now', 'localtime')";
            String dateRange = buildDateRangeSelection(startDate, endDate);
            if (dateRange != null) {
                selection += " AND " + dateRange;
            }
            String sortOrder = Contract.Transaction.DATE + " DESC";
            Group groupType = PreferenceManager.getCurrentGroupType();
            return new WrappedCursorLoader(activity, uri, null, selection, selectionArgs, sortOrder, groupType, startDate, endDate);
        }
        return null;
    }

    /**
     * The content uri of the transactions belonging to the item this screen was opened for.
     * Falls back to the unfiltered transaction uri when there is no item, which is the same
     * default the loader has always carried.
     */
    private static Uri buildItemTransactionsUri(@Nullable Bundle arguments) {
        Uri uri = DataContentProvider.CONTENT_TRANSACTIONS;
        if (arguments == null) {
            return uri;
        }
        FilterType type = (FilterType) arguments.getSerializable(FILTER_TYPE);
        if (type == null) {
            return uri;
        }
        long itemId = arguments.getLong(FILTER_ID);
        switch (type) {
            case CATEGORY:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, itemId);
                break;
            case DEBT:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, itemId);
                break;
            case BUDGET:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, itemId);
                break;
            case SAVING:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, itemId);
                break;
            case EVENT:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_EVENTS, itemId);
                break;
            case PLACE:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PLACES, itemId);
                break;
            case PERSON:
                uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, itemId);
                break;
        }
        return Uri.withAppendedPath(uri, "transactions");
    }

    /**
     * The period the caller asked for, which is scope this screen was given rather than scope it
     * applies on its own. Null when the caller asked for no period at all.
     */
    @Nullable
    private static String buildDateRangeSelection(@Nullable Date startDate, @Nullable Date endDate) {
        StringBuilder builder = new StringBuilder();
        if (startDate != null) {
            builder.append("DATETIME(").append(Contract.Transaction.DATE).append(") >= DATETIME('")
                    .append(DateUtils.getSQLDateTimeString(startDate)).append("')");
        }
        if (endDate != null) {
            if (builder.length() > 0) {
                builder.append(" AND ");
            }
            builder.append("DATETIME(").append(Contract.Transaction.DATE).append(") <= DATETIME('")
                    .append(DateUtils.getSQLDateTimeString(endDate)).append("')");
        }
        return builder.length() > 0 ? builder.toString() : null;
    }

    /**
     * An empty list on this screen has three meanings the user cannot tell apart: the item has
     * no transactions at all, it has some in a wallet that is not the selected one, or it has
     * some dated in the future. {@link #onCreateLoader(int, Bundle)} hides the second and third
     * without saying so, so when it comes back with nothing, ask what it is hiding.
     */
    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
        boolean empty = cursor == null || cursor.getCount() == 0;
        if (empty) {
            // back to the plain message before the base makes it visible: a refined one from an
            // earlier load would otherwise stand on screen while this load is being explained
            setEmptyText(R.string.message_no_transaction_found);
        }
        super.onLoadFinished(loader, cursor);
        if (empty) {
            LoaderManager.getInstance(this).restartLoader(HIDDEN_TRANSACTIONS_LOADER_ID, null, mHiddenTransactionsCallbacks);
        } else {
            LoaderManager.getInstance(this).destroyLoader(HIDDEN_TRANSACTIONS_LOADER_ID);
        }
    }

    private final LoaderManager.LoaderCallbacks<Cursor> mHiddenTransactionsCallbacks = new LoaderManager.LoaderCallbacks<Cursor>() {

        @NonNull
        @Override
        public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
            Bundle arguments = getArguments();
            Date startDate = arguments != null ? (Date) arguments.getSerializable(FILTER_START_DATE) : null;
            Date endDate = arguments != null ? (Date) arguments.getSerializable(FILTER_END_DATE) : null;
            // the same item, the same period, without the two clauses the user cannot see
            return new CursorLoader(getActivity(), buildItemTransactionsUri(arguments), HIDDEN_PROJECTION,
                    buildDateRangeSelection(startDate, endDate), null, null);
        }

        @Override
        public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
            setEmptyText(describeHiddenTransactions(data));
        }

        @Override
        public void onLoaderReset(@NonNull Loader<Cursor> loader) {
            // the message holds a copy of the string, not the cursor
        }

    };

    /**
     * Walks the rows on the main thread, and stops early only once both answers are known, so
     * an item with many transactions and none of them dated ahead is read in full. The cost is
     * bounded by that one item's transactions and is paid only on a list that is already empty.
     *
     * @param cursor every transaction of this item within the period asked for, including the
     *               ones the list itself filters out.
     * @return the message for an empty list, naming what is being hidden from it.
     */
    @StringRes
    private int describeHiddenTransactions(@Nullable Cursor cursor) {
        boolean otherWallets = false;
        boolean future = false;
        if (cursor != null) {
            long currentWallet = PreferenceManager.getCurrentWallet();
            // compared as text rather than parsed: the column and this string are both
            // yyyy-MM-dd HH:mm:ss in local time, which is the comparison the query itself makes
            String now = DateUtils.getSQLDateTimeString(new Date());
            int indexWalletId = cursor.getColumnIndex(Contract.Transaction.WALLET_ID);
            int indexCountInTotal = cursor.getColumnIndex(Contract.Transaction.WALLET_COUNT_IN_TOTAL);
            int indexDate = cursor.getColumnIndex(Contract.Transaction.DATE);
            for (int i = 0; i < cursor.getCount() && !(otherWallets && future); i++) {
                cursor.moveToPosition(i);
                boolean inSelectedWallet = currentWallet == PreferenceManager.TOTAL_WALLET_ID
                        ? cursor.getInt(indexCountInTotal) == 1
                        : cursor.getLong(indexWalletId) == currentWallet;
                if (!inSelectedWallet) {
                    otherWallets = true;
                } else {
                    // in the selected wallet and still missing from the list, so the date is
                    // the only clause that can be holding it back
                    String date = cursor.getString(indexDate);
                    future |= date != null && date.compareTo(now) > 0;
                }
            }
        }
        if (otherWallets && future) {
            return R.string.message_no_transaction_found_other_wallets_and_future;
        } else if (otherWallets) {
            return R.string.message_no_transaction_found_other_wallets;
        } else if (future) {
            return R.string.message_no_transaction_found_future;
        }
        return R.string.message_no_transaction_found;
    }

    @Override
    protected int getTitleRes() {
        return R.string.menu_transaction;
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
    public void onHeaderClick(Date startDate, Date endDate) {

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

    @Override
    public void onCurrentWalletChanged(long walletId) {
        mSelectionMode.finish();
        recreateLoader();
    }

    private static class WrappedCursorLoader extends CursorLoader {

        private final Group mGroup;
        private final Date mStartDate;
        private final Date mEndDate;

        private WrappedCursorLoader(@NonNull Context context, @NonNull Uri uri, @Nullable String[] projection, @Nullable String selection,
                                    @Nullable String[] selectionArgs, @Nullable String sortOrder, Group group, Date startDate, Date endDate) {
            super(context, uri, projection, selection, selectionArgs, sortOrder);
            mGroup = group;
            mStartDate = startDate;
            mEndDate = endDate;
        }

        @Override
        public Cursor loadInBackground() {
            Cursor cursor = super.loadInBackground();
            return new TransactionHeaderCursor(cursor, mGroup, mStartDate, mEndDate);
        }
    }
}