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

import android.content.Context;
import android.database.Cursor;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.MaterialProgressBar;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import java.util.Calendar;
import java.util.Date;

/**
 * Created by andrea on 06/03/18.
 */
public class BudgetCursorAdapter extends AbstractCursorAdapter<BudgetCursorAdapter.ViewHolder> {

    private static final String COLOR_INCOMES = "#43A047";
    private static final String COLOR_EXPENSES = "#D32F2F";

    @VisibleForTesting
    static Long sToday;

    private int mIndexId;
    private int mIndexType;
    private int mIndexTarget;
    private int mIndexStartDate;
    private int mIndexEndDate;
    private int mIndexCategoryIcon;
    private int mIndexCategoryType;
    private int mIndexCategoryName;
    private int mIndexCurrency;
    private int mIndexProgress;

    private MoneyFormatter mMoneyFormatter;

    private final ActionListener mActionListener;

    public BudgetCursorAdapter(ActionListener actionListener) {
        super(null, Contract.Budget.ID);
        mActionListener = actionListener;
        mMoneyFormatter = MoneyFormatter.getInstance();
    }

    @Override
    protected void onLoadColumnIndices(@NonNull Cursor cursor) {
        mIndexId = cursor.getColumnIndex(Contract.Budget.ID);
        mIndexType = cursor.getColumnIndex(Contract.Budget.TYPE);
        mIndexTarget = cursor.getColumnIndex(Contract.Budget.MONEY);
        mIndexStartDate = cursor.getColumnIndex(Contract.Budget.START_DATE);
        mIndexEndDate = cursor.getColumnIndex(Contract.Budget.END_DATE);
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Budget.CATEGORY_ICON);
        mIndexCategoryType = cursor.getColumnIndex(Contract.Budget.CATEGORY_TYPE);
        mIndexCategoryName = cursor.getColumnIndex(Contract.Budget.CATEGORY_NAME);
        mIndexCurrency = cursor.getColumnIndex(Contract.Budget.CURRENCY);
        mIndexProgress = cursor.getColumnIndex(Contract.Budget.PROGRESS);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor) {
        Contract.BudgetType type = Contract.BudgetType.fromValue(cursor.getInt(mIndexType));
        if (type != null) {
            switch (type) {
                case INCOMES:
                    String incomes = holder.getContext().getString(R.string.hint_incomes);
                    holder.mDescriptionTextView.setText(incomes);
                    IconLoader.loadInto(new ColorIcon(COLOR_INCOMES, incomes.substring(0, 1)), holder.mAvatarImageView);
                    break;
                case EXPENSES:
                    String expenses = holder.getContext().getString(R.string.hint_expenses);
                    holder.mDescriptionTextView.setText(expenses);
                    IconLoader.loadInto(new ColorIcon(COLOR_EXPENSES, expenses.substring(0, 1)), holder.mAvatarImageView);
                    break;
                case CATEGORY:
                    Icon icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon));
                    IconLoader.loadInto(icon, holder.mAvatarImageView);
                    holder.mDescriptionTextView.setText(cursor.getString(mIndexCategoryName));
                    break;
            }
        }
        CurrencyUnit currency = CurrencyManager.getCurrency(cursor.getString(mIndexCurrency));
        long target = cursor.getLong(mIndexTarget);
        long progress = Math.abs(cursor.getLong(mIndexProgress));
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, currency, target);
        // calculate difference
        holder.mProgressBar.setMaxValue(target);
        holder.mProgressBar.setProgressValue(progress);
        long difference = Math.max(target - progress, 0L);
        boolean expenses = type == Contract.BudgetType.EXPENSES || (type == Contract.BudgetType.CATEGORY
                && Contract.CategoryType.fromValue(cursor.getInt(mIndexCategoryType)) == Contract.CategoryType.EXPENSE);
        boolean overrun = expenses && progress > target;
        // fill the views
        mMoneyFormatter.applyNotTinted(holder.mUsedTextView, currency, progress);
        if (overrun) {
            holder.mAvailableHeaderTextView.setText(R.string.adapter_over_money);
            holder.mProgressBar.setReachedBarColor(ThemeEngine.getTheme().getVisibleColor(
                    PreferenceManager.getCurrentExpenseColor(), PreferenceManager.getDefaultColorExpense()));
            mMoneyFormatter.applyNotTinted(holder.mAvailableTextView, currency, progress - target);
        } else {
            holder.mAvailableHeaderTextView.setText(R.string.adapter_available_money);
            ThemeEngine.applyTheme(holder.mProgressBar, false);
            mMoneyFormatter.applyNotTinted(holder.mAvailableTextView, currency, difference);
        }
        // check if budget is expired
        Context context = holder.mDateTextView.getContext();
        Date today = today().getTime();
        Date endDate = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexEndDate));
        String formattedEndDate = DateFormatter.getFormattedDate(endDate);
        boolean expired = DateUtils.isBefore(endDate, today);
        if (expired) {
            holder.mDateTextView.setText(context.getString(R.string.relative_string_ended_on, formattedEndDate));
        } else {
            holder.mDateTextView.setText(context.getString(R.string.relative_string_will_end_on, formattedEndDate));
        }
        long leftPerDay = 0L;
        if (expenses && !overrun && !expired) {
            Date startDate = null;
            try {
                startDate = DateUtils.getDateFromSQLDateString(cursor.getString(mIndexStartDate));
            } catch (RuntimeException e) {
                Log.w("BudgetCursorAdapter", "Budget start date does not parse", e);
            }
            if (startDate != null) {
                Date from = DateUtils.isAfter(startDate, today) ? startDate : today;
                long days = DateUtils.getCalendarDaysBetween(from, endDate) + 1;
                if (days > 0) {
                    leftPerDay = difference / days;
                }
            }
        }
        if (leftPerDay > 0) {
            mMoneyFormatter.applyNotTinted(holder.mLeftPerDayTextView, currency, leftPerDay);
            holder.mLeftPerDayHeaderTextView.setVisibility(View.VISIBLE);
            holder.mLeftPerDayTextView.setVisibility(View.VISIBLE);
        } else {
            holder.mLeftPerDayHeaderTextView.setVisibility(View.GONE);
            holder.mLeftPerDayTextView.setVisibility(View.GONE);
        }
    }

    private static Calendar today() {
        Calendar today = Calendar.getInstance();
        if (sToday != null) {
            today.setTimeInMillis(sToday);
        }
        DateUtils.setTime(today, 0, 0, 0, 0);
        return today;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemView = inflater.inflate(R.layout.adapter_budget_item, parent, false);
        return new ViewHolder(itemView);
    }

    /*package-local*/ class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private ImageView mAvatarImageView;
        private TextView mDescriptionTextView;
        private TextView mMoneyTextView;
        private MaterialProgressBar mProgressBar;
        private TextView mUsedTextView;
        private TextView mAvailableHeaderTextView;
        private TextView mAvailableTextView;
        private TextView mLeftPerDayHeaderTextView;
        private TextView mLeftPerDayTextView;
        private TextView mDateTextView;

        /*package-local*/ ViewHolder(View itemView) {
            super(itemView);
            mAvatarImageView = itemView.findViewById(R.id.avatar_image_view);
            mDescriptionTextView = itemView.findViewById(R.id.primary_text_view);
            mMoneyTextView = itemView.findViewById(R.id.money_text_view);
            mProgressBar = itemView.findViewById(R.id.progress_bar);
            mUsedTextView = itemView.findViewById(R.id.used_body_text_view);
            mAvailableHeaderTextView = itemView.findViewById(R.id.available_header_text_view);
            mAvailableTextView = itemView.findViewById(R.id.available_body_text_view);
            mLeftPerDayHeaderTextView = itemView.findViewById(R.id.left_per_day_header_text_view);
            mLeftPerDayTextView = itemView.findViewById(R.id.left_per_day_body_text_view);
            mDateTextView = itemView.findViewById(R.id.date_text_view);
            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mActionListener != null) {
                Cursor cursor = getSafeCursor(getAdapterPosition());
                if (cursor != null) {
                    mActionListener.onBudgetClick(cursor.getLong(mIndexId));
                }
            }
        }

        /*package-local*/ Context getContext() {
            return itemView.getContext();
        }
    }

    public interface ActionListener {

        void onBudgetClick(long id);
    }
}