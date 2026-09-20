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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;

public class CategoryRuleCursorAdapter extends AbstractCursorAdapter<CategoryRuleCursorAdapter.ViewHolder> {

    private final ActionListener mActionListener;

    private int mIndexId;
    private int mIndexPattern;
    private int mIndexCategoryName;

    public CategoryRuleCursorAdapter(ActionListener actionListener) {
        super(null, Contract.CategoryRule.ID);
        mActionListener = actionListener;
    }

    @Override
    protected void onLoadColumnIndices(@NonNull Cursor cursor) {
        mIndexId = cursor.getColumnIndex(Contract.CategoryRule.ID);
        mIndexPattern = cursor.getColumnIndex(Contract.CategoryRule.PATTERN);
        mIndexCategoryName = cursor.getColumnIndex(Contract.CategoryRule.CATEGORY_NAME);
    }

    @Override
    public void onBindViewHolder(ViewHolder holder, Cursor cursor) {
        holder.mPatternTextView.setText(cursor.getString(mIndexPattern));
        holder.mCategoryTextView.setText(cursor.getString(mIndexCategoryName));
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemView = inflater.inflate(R.layout.adapter_category_rule_item, parent, false);
        return new ViewHolder(itemView);
    }

    /*package-local*/ class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private final TextView mPatternTextView;
        private final TextView mCategoryTextView;

        /*package-local*/ ViewHolder(View itemView) {
            super(itemView);
            mPatternTextView = itemView.findViewById(R.id.primary_text_view);
            mCategoryTextView = itemView.findViewById(R.id.secondary_text_view);
            itemView.setOnClickListener(this);
            itemView.setOnLongClickListener(this);
        }

        @Override
        public void onClick(View view) {
            if (mActionListener != null) {
                Cursor cursor = getSafeCursor(getAdapterPosition());
                if (cursor != null) {
                    mActionListener.onCategoryRuleClick(cursor.getLong(mIndexId));
                }
            }
        }

        @Override
        public boolean onLongClick(View view) {
            if (mActionListener == null) {
                return false;
            }
            Cursor cursor = getSafeCursor(getAdapterPosition());
            if (cursor != null) {
                mActionListener.onCategoryRuleLongClick(cursor.getLong(mIndexId),
                        cursor.getString(mIndexPattern));
            }
            // consumed whenever there is a listener to consume it, so the release that follows
            // cannot run performClick and open the editor on the row being deleted
            return true;
        }
    }

    public interface ActionListener {

        void onCategoryRuleClick(long id);

        void onCategoryRuleLongClick(long id, String pattern);
    }
}
