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

import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.PeriodDetailSummaryData;
import com.oriondev.moneywallet.model.PeriodMoney;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.MoneyFormatter;

/**
 * Created by andrea on 14/08/18.
 */
public class PeriodDetailSummaryAdapter extends RecyclerView.Adapter<PeriodDetailSummaryAdapter.ViewHolder> {

    private final Controller mController;

    private PeriodDetailSummaryData mData;

    private final MoneyFormatter mMoneyFormatter;

    public PeriodDetailSummaryAdapter(Controller controller) {
        mController = controller;
        mMoneyFormatter = MoneyFormatter.getInstance();
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.adapter_period_summary_item, parent, false));
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        PeriodMoney periodMoney = mData.getPeriodMoney(position);
        if (!periodMoney.isTimeRange()) {
            DateFormatter.applyDateRange(holder.mNameTextView, periodMoney.getStartDate(), periodMoney.getEndDate());
        } else {
            DateFormatter.applyTimeRange(holder.mNameTextView, periodMoney.getStartDate(), periodMoney.getEndDate());
        }
        // TODO: maybe can be useful to display also incomes and expenses
        // Untinted, because a tinted amount drops its sign with the plus and minus setting
        // off, which is the default, so a period that ended down printed as a positive number
        // with only the color saying otherwise. Same call the transactions list header makes.
        mMoneyFormatter.applyNotTinted(holder.mMoneyTextView, periodMoney.getNetIncomes());
        // The figure above counts money moved between the user's own wallets and the two bar
        // series do not, so a period that only moved money shows a net with both bars at
        // nothing. Naming what moved is what closes that, and a period that moved none says
        // nothing at all. Untinted and signed for the reason the transactions list header is,
        // the word Transfers does not say which way the money went.
        if (TransactionCursorAdapter.isZero(periodMoney.getTransfers())) {
            holder.mTransferTextView.setVisibility(View.GONE);
        } else {
            holder.mTransferTextView.setVisibility(View.VISIBLE);
            holder.mTransferTextView.setText(holder.itemView.getContext().getString(
                    R.string.hint_transfers) + " "
                    + mMoneyFormatter.getNotTintedString(periodMoney.getTransfers()));
        }
    }

    @Override
    public int getItemCount() {
        return mData != null ? mData.getPeriodCount() : 0;
    }

    public void setData(PeriodDetailSummaryData data) {
        mData = data;
        notifyDataSetChanged();
    }

    /*package-local*/ class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private TextView mNameTextView;
        private TextView mMoneyTextView;
        private TextView mTransferTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            mNameTextView = itemView.findViewById(R.id.name_text_view);
            mMoneyTextView = itemView.findViewById(R.id.money_text_view);
            mTransferTextView = itemView.findViewById(R.id.transfer_text_view);

            itemView.setOnClickListener(this);
        }

        @Override
        public void onClick(View v) {
            if (mController != null) {
                int index = getAdapterPosition();
                if (mData != null) {
                    PeriodMoney periodMoney = mData.getPeriodMoney(index);
                    mController.onPeriodClick(periodMoney);
                }
            }
        }
    }

    public interface Controller {

        void onPeriodClick(PeriodMoney periodMoney);
    }
}