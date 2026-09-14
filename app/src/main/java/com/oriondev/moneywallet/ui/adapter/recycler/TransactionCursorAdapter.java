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

package com.oriondev.moneywallet.ui.adapter.recycler;

import android.database.Cursor;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.storage.wrapper.AbstractHeaderCursor;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by andrea on 03/03/18.
 */
public class TransactionCursorAdapter extends AbstractCursorAdapter<RecyclerView.ViewHolder> {

    private final ActionListener mActionListener;
    private final boolean mHeaderOpensReport;

    private int mIndexType;
    private int mIndexHeaderStartDate;
    private int mIndexHeaderEndDate;
    private int mIndexHeaderMoney;
    private int mIndexHeaderIncome;
    private int mIndexHeaderExpense;
    private int mIndexHeaderGroupType;
    private int mIndexCategoryName;
    private int mIndexCategoryIcon;
    private int mIndexTransactionId;
    private int mIndexTransactionType;
    private int mIndexTransactionDirection;
    private int mIndexTransactionDescription;
    private int mIndexTransactionDate;
    private int mIndexTransactionMoney;
    private int mIndexCurrency;

    private MoneyFormatter mMoneyFormatter;

    /**
     * Cursor row of each row on screen, in order. Hiding a period's transactions leaves their
     * cursor rows out of this list, so every position this adapter is asked about is read
     * through it.
     */
    private final List<Integer> mVisibleRows = new ArrayList<>();

    /**
     * Keys of the periods whose transactions are hidden, as of the last rebuild. Read from the
     * preference every time instead of kept, because the two lists that draw headers with this
     * adapter, the transactions list and a filtered transactions list, share one stored set and
     * are alive at once, since the filtered list opens over the main one. An adapter that
     * trusted its own copy would write the others' hiding away.
     */
    private final Set<String> mCollapsedPeriods = new HashSet<>();

    private final Set<Long> mSelectedIds = new HashSet<>();

    public TransactionCursorAdapter(ActionListener actionListener) {
        this(actionListener, false);
    }

    /**
     * Three of the four screens using this adapter answer a header click with an empty method, so
     * the arrow and the click both wait to be asked for. A screen that shows the arrow has to
     * open something from onHeaderClick, and one that does not must not show it.
     */
    public TransactionCursorAdapter(ActionListener actionListener, boolean headerOpensReport) {
        super(null, Contract.Transaction.ID);
        mActionListener = actionListener;
        mHeaderOpensReport = headerOpensReport;
        mMoneyFormatter = MoneyFormatter.getInstance();
    }

    @Override
    protected void onLoadColumnIndices(@NonNull Cursor cursor) {
        mIndexType = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_ITEM_TYPE);
        mIndexHeaderStartDate = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_START_DATE);
        mIndexHeaderEndDate = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_END_DATE);
        mIndexHeaderMoney = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_MONEY);
        mIndexHeaderIncome = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_INCOME);
        mIndexHeaderExpense = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_EXPENSE);
        mIndexHeaderGroupType = cursor.getColumnIndex(TransactionHeaderCursor.COLUMN_HEADER_GROUP_TYPE);
        mIndexCategoryName = cursor.getColumnIndex(Contract.Transaction.CATEGORY_NAME);
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Transaction.CATEGORY_ICON);
        mIndexTransactionId = cursor.getColumnIndex(Contract.Transaction.ID);
        mIndexTransactionType = cursor.getColumnIndex(Contract.Transaction.TYPE);
        mIndexTransactionDirection = cursor.getColumnIndex(Contract.Transaction.DIRECTION);
        mIndexTransactionDescription = cursor.getColumnIndex(Contract.Transaction.DESCRIPTION);
        mIndexTransactionDate = cursor.getColumnIndex(Contract.Transaction.DATE);
        mIndexTransactionMoney = cursor.getColumnIndex(Contract.Transaction.MONEY);
        mIndexCurrency = cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY);
        // The superclass calls this from two places: when it takes a new cursor, after that
        // cursor is in place and before it tells the list anything changed, and from its own
        // constructor. The constructor call cannot land here, because this adapter is always
        // built with no cursor and the fields the walk below needs do not exist until super
        // returns.
        rebuildVisibleRows();
        dropSelectedIdsNotOnScreen();
    }

    // ponytail: pages every row on screen on the main thread, but only while something is selected
    private void dropSelectedIdsNotOnScreen() {
        // no data yet means a restored selection still waits for its first load
        if (mSelectedIds.isEmpty() || !isDataValid()) {
            return;
        }
        Set<Long> onScreen = new HashSet<>();
        for (int position = 0; position < getItemCount(); position++) {
            int cursorPosition = cursorPosition(position);
            if (!isItemAt(cursorPosition)) {
                continue;
            }
            Cursor cursor = getSafeCursor(cursorPosition);
            if (cursor != null) {
                onScreen.add(cursor.getLong(mIndexTransactionId));
            }
        }
        if (mSelectedIds.retainAll(onScreen) && mActionListener != null) {
            mActionListener.onSelectionChanged(mSelectedIds.size());
        }
    }

    private boolean isItemAt(int cursorPosition) {
        return mIndexType == -1 || !((AbstractHeaderCursor<?>) getCursor()).isHeaderAt(cursorPosition);
    }

    private boolean isSelectable(Cursor cursor) {
        // a transfer leg is refused by the database, so it is refused here before it can be picked
        return cursor.getInt(mIndexTransactionType) != Contract.TransactionType.TRANSFER;
    }

    public long[] getSelectedIds() {
        long[] ids = new long[mSelectedIds.size()];
        int index = 0;
        for (long id : mSelectedIds) {
            ids[index++] = id;
        }
        return ids;
    }

    public void setSelectedIds(long[] ids) {
        mSelectedIds.clear();
        for (long id : ids) {
            mSelectedIds.add(id);
        }
        if (getCursor() != null) {
            dropSelectedIdsNotOnScreen();
        }
        notifySelectionChanged();
    }

    public void clearSelection() {
        if (!mSelectedIds.isEmpty()) {
            mSelectedIds.clear();
            notifySelectionChanged();
        }
    }

    public void selectAll() {
        for (int position = 0; position < getItemCount(); position++) {
            int cursorPosition = cursorPosition(position);
            if (!isItemAt(cursorPosition)) {
                continue;
            }
            Cursor cursor = getSafeCursor(cursorPosition);
            if (cursor != null && isSelectable(cursor)) {
                mSelectedIds.add(cursor.getLong(mIndexTransactionId));
            }
        }
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        notifyDataSetChanged();
        if (mActionListener != null) {
            mActionListener.onSelectionChanged(mSelectedIds.size());
        }
    }

    private void rebuildVisibleRows() {
        mVisibleRows.clear();
        mCollapsedPeriods.clear();
        mCollapsedPeriods.addAll(PreferenceManager.getCollapsedPeriods());
        Cursor cursor = getCursor();
        if (cursor == null) {
            return;
        }
        if (mIndexType == -1) {
            return;
        }
        // Moving the wrapper to an item row moves the SQLite cursor under it, so asking every row
        // what it is paged the whole result set on the main thread on every load. The header rows
        // are the only ones this walk reads, and the wrapper names them without being moved.
        AbstractHeaderCursor<?> headerCursor = (AbstractHeaderCursor<?>) cursor;
        boolean headerIsCollapsed = false;
        for (int position = 0; position < cursor.getCount(); position++) {
            if (headerCursor.isHeaderAt(position)) {
                cursor.moveToPosition(position);
                headerIsCollapsed = mCollapsedPeriods.contains(periodKey(
                        cursor.getInt(mIndexHeaderGroupType),
                        cursor.getString(mIndexHeaderStartDate)));
                mVisibleRows.add(position);
            } else if (!headerIsCollapsed) {
                mVisibleRows.add(position);
            }
        }
    }

    /**
     * The key a period is stored under: the grouping it was drawn with, then the date it starts
     * on. The grouping is part of it because a day and the month it opens start on the same date,
     * and folding one must not fold the other when the user changes the grouping and comes back.
     */
    private static String periodKey(int groupType, String startDate) {
        return groupType + ":" + startDate;
    }

    /**
     * Draws the rows again against what is stored now. The list this adapter is in is not the
     * only one reading that store, so a list coming back to the front has to ask again instead
     * of drawing what it last built. A set that has not changed is left alone, because the main
     * list calls this every time it resumes, and rebuilding walks the whole cursor and rebinds
     * every row on the main thread. Without that, every return from a transaction, the report or
     * the settings paid for a fold nobody had made.
     */
    public void reloadCollapsedPeriods() {
        if (PreferenceManager.getCollapsedPeriods().equals(mCollapsedPeriods)) {
            return;
        }
        rebuildVisibleRows();
        dropSelectedIdsNotOnScreen();
        notifyDataSetChanged();
    }

    /*package-local*/ void togglePeriod(String key) {
        // Read, change, write, so that the other list this adapter shares the stored set with
        // keeps its own hiding.
        Set<String> stored = PreferenceManager.getCollapsedPeriods();
        if (!stored.remove(key)) {
            stored.add(key);
        }
        PreferenceManager.setCollapsedPeriods(stored);
        rebuildVisibleRows();
        dropSelectedIdsNotOnScreen();
        notifyDataSetChanged();
    }

    /**
     * The cursor row an on screen position stands for, or -1 when there is none. Everything that
     * takes an on screen position goes through this, and so does every read of the cursor from a
     * click.
     */
    private int cursorPosition(int position) {
        if (mIndexType == -1) {
            // The calendar and the search results hand this adapter a plain cursor with no header
            // rows, so nothing folds and positions map straight through. The search rebuilds its
            // cursor per keystroke, so a boxed copy of every row there would be paid per character.
            return position;
        }
        return position >= 0 && position < mVisibleRows.size() ? mVisibleRows.get(position) : -1;
    }

    @Override
    public int getItemCount() {
        // Guarded, because a cursor swapped away for null leaves the rows built for it behind,
        // since the superclass only walks a cursor it actually has.
        if (!isDataValid()) {
            return 0;
        }
        return mIndexType == -1 ? getCursor().getCount() : mVisibleRows.size();
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(cursorPosition(position));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        super.onBindViewHolder(holder, cursorPosition(position));
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, Cursor cursor) {
        if (viewHolder instanceof HeaderViewHolder) {
            onBindHeaderViewHolder((HeaderViewHolder) viewHolder, cursor);
        } else if (viewHolder instanceof TransactionViewHolder) {
            onBindItemViewHolder((TransactionViewHolder) viewHolder, cursor);
        }
    }

    private void onBindItemViewHolder(TransactionViewHolder holder, Cursor cursor) {
        Icon icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon));
        IconLoader.loadInto(icon, holder.mAvatarImageView);
        holder.mPrimaryTextView.setText(cursor.getString(mIndexCategoryName));
        holder.mSecondaryTextView.setText(cursor.getString(mIndexTransactionDescription));
        CurrencyUnit currency = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency));
        long money = cursor.getLong(mIndexTransactionMoney);
        if (cursor.getInt(mIndexTransactionDirection) == Contract.Direction.INCOME) {
            mMoneyFormatter.applyTintedIncome(holder.mMoneyTextView, currency, money);
        } else {
            mMoneyFormatter.applyTintedExpense(holder.mMoneyTextView, currency, money);
        }
        Date date = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexTransactionDate));
        DateFormatter.applyDate(holder.mDateTextView, date);
        holder.itemView.setActivated(mSelectedIds.contains(cursor.getLong(mIndexTransactionId)));
    }

    private void onBindHeaderViewHolder(HeaderViewHolder holder, Cursor cursor) {
        Date start = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderStartDate));
        Date end = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderEndDate));
        DateFormatter.applyDateRange(holder.mLeftTextView, start, end);
        Money money = Money.parse(cursor.getString(mIndexHeaderMoney));
        // untinted, because with the plus and minus setting off, which is the default, this
        // class shows a sign on an amount it does not color and leaves the sign off one it
        // does. A tinted difference printed 45.00 for a stretch that spent 45 and earned
        // nothing, and left the color to say which way it went, on a line whose other two
        // figures are colored whatever they hold
        mMoneyFormatter.applyNotTinted(holder.mRightTextView, money);
        Money income = Money.parse(cursor.getString(mIndexHeaderIncome));
        Money expense = Money.parse(cursor.getString(mIndexHeaderExpense));
        mMoneyFormatter.applyTintedIncome(holder.mIncomeTextView, orZero(income, money));
        mMoneyFormatter.applyTintedExpense(holder.mExpenseTextView, orZero(expense, money));
        bindPeriodToggle(holder, cursor);
    }

    private void bindPeriodToggle(HeaderViewHolder holder, Cursor cursor) {
        boolean collapsed = mCollapsedPeriods.contains(periodKey(
                cursor.getInt(mIndexHeaderGroupType), cursor.getString(mIndexHeaderStartDate)));
        holder.mPeriodToggle.setRotation(collapsed ? 0f : 180f);
        // Named, because a screen reader reaches the arrow on its own and every one of them
        // would otherwise read the same words. The date range is the one the row just drew.
        holder.mPeriodToggle.setContentDescription(holder.itemView.getContext().getString(
                collapsed ? R.string.description_show_period_transactions
                        : R.string.description_hide_period_transactions,
                holder.mLeftTextView.getText()));
    }

    /**
     * The figure itself, or a zero in the currencies the difference was counted in when the
     * figure holds none of its own. An amount with no currency in it renders as the placeholder
     * for a value nobody knows, and a stretch that only spent has a known zero income.
     *
     * The zero is put here and not into the running totals because a figure that has rows of
     * its own must not gain a currency it has none in, which is what putting it in the totals
     * did. A figure with no rows at all still takes every currency the header counted, so on a
     * header counting two it reads as two zeros and is cut off the way any long figure is.
     */
    /*package-local*/ static Money orZero(Money money, Money counted) {
        if (money.getNumberOfCurrencies() > 0) {
            return money;
        }
        Money zero = new Money();
        for (String currency : counted.getCurrencies()) {
            zero.addMoney(currency, 0);
        }
        return zero;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TransactionHeaderCursor.TYPE_HEADER) {
            View itemView = inflater.inflate(R.layout.adapter_transaction_header_item, parent, false);
            return new HeaderViewHolder(itemView);
        } else if (viewType == TransactionHeaderCursor.TYPE_ITEM){
            View itemView = inflater.inflate(R.layout.adapter_transaction_item, parent, false);
            return new TransactionViewHolder(itemView);
        } else {
            throw new IllegalArgumentException("Invalid view type: " + viewType);
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (mIndexType != -1) {
            return getSafeCursor(cursorPosition(position)).getInt(mIndexType);
        } else {
            return TransactionHeaderCursor.TYPE_ITEM;
        }
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView mLeftTextView;
        private TextView mRightTextView;
        private TextView mIncomeTextView;
        private TextView mExpenseTextView;
        private ImageView mPeriodToggle;

        /*package-local*/ HeaderViewHolder(View itemView) {
            super(itemView);
            mLeftTextView = itemView.findViewById(R.id.left_text_view);
            mRightTextView = itemView.findViewById(R.id.right_text_view);
            mIncomeTextView = itemView.findViewById(R.id.income_text_view);
            mExpenseTextView = itemView.findViewById(R.id.expense_text_view);
            mPeriodToggle = itemView.findViewById(R.id.period_toggle_image_view);
            // Its own listener, because a tap on the row still means what it always did: open
            // the report of this period, or nothing at all.
            mPeriodToggle.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View view) {
                    Cursor cursor = getSafeCursor(cursorPosition(getAdapterPosition()));
                    if (cursor != null) {
                        togglePeriod(periodKey(cursor.getInt(mIndexHeaderGroupType),
                                cursor.getString(mIndexHeaderStartDate)));
                    }
                }

            });
            // the arrow is a ThemedImageView, which tints itself from the row it sits on and
            // repaints when the mode changes. A theme attribute in the vector would resolve
            // against the xml theme, which is the light one whatever mode the user picked
            itemView.findViewById(R.id.report_image_view)
                    .setVisibility(mHeaderOpensReport ? View.VISIBLE : View.GONE);
            if (mHeaderOpensReport) {
                itemView.setOnClickListener(this);
            } else {
                // no destination, so the row keeps its ripple to itself
                itemView.setBackground(null);
            }
        }

        @Override
        public void onClick(View v) {
            if (mActionListener != null) {
                Cursor cursor = getSafeCursor(cursorPosition(getAdapterPosition()));
                if (cursor != null) {
                    Date start = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderStartDate));
                    Date end = DateUtils.getDateFromSQLDateTimeString(cursor.getString(mIndexHeaderEndDate));
                    mActionListener.onHeaderClick(start, end);
                }
            }
        }
    }

    /*package-local*/ class TransactionViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private ImageView mAvatarImageView;
        private TextView mPrimaryTextView;
        private TextView mMoneyTextView;
        private TextView mSecondaryTextView;
        private TextView mDateTextView;

        /*package-local*/ TransactionViewHolder(View itemView) {
            super(itemView);
            mAvatarImageView = itemView.findViewById(R.id.avatar_image_view);
            mPrimaryTextView = itemView.findViewById(R.id.primary_text_view);
            mMoneyTextView = itemView.findViewById(R.id.money_text_view);
            mSecondaryTextView = itemView.findViewById(R.id.secondary_text_view);
            mDateTextView = itemView.findViewById(R.id.date_text_view);
            // the ripple color, because it is translucent and so reads on every light and dark
            // background, while an xml attribute would resolve against the light theme only
            StateListDrawable selected = new StateListDrawable();
            selected.addState(new int[] {android.R.attr.state_activated},
                    new ColorDrawable(ThemeEngine.getTheme().getColorRipple()));
            itemView.setBackground(new LayerDrawable(new Drawable[] {selected, itemView.getBackground()}));
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mActionListener != null) {
                Cursor cursor = getSafeCursor(cursorPosition(getAdapterPosition()));
                if (cursor == null) {
                    return;
                }
                if (mSelectedIds.isEmpty()) {
                    mActionListener.onTransactionClick(cursor.getLong(mIndexTransactionId));
                } else if (isSelectable(cursor)) {
                    long id = cursor.getLong(mIndexTransactionId);
                    if (!mSelectedIds.remove(id)) {
                        mSelectedIds.add(id);
                    }
                    notifySelectionChanged();
                }
            }
        }

        @Override
        public boolean onLongClick(View v) {
            Cursor cursor = getSafeCursor(cursorPosition(getAdapterPosition()));
            if (cursor == null || !isSelectable(cursor)) {
                return false;
            }
            if (mSelectedIds.add(cursor.getLong(mIndexTransactionId))) {
                notifySelectionChanged();
            }
            return true;
        }
    }

    public interface ActionListener {

        void onHeaderClick(Date startDate, Date endDate);

        void onTransactionClick(long id);

        void onSelectionChanged(int count);
    }
}