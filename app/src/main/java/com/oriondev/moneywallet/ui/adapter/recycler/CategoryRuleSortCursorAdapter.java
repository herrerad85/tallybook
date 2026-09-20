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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CategoryRuleSortCursorAdapter extends AbstractCursorAdapter<CategoryRuleSortCursorAdapter.RuleViewHolder> {

    private int mIndexRuleId;
    private int mIndexPattern;
    private int mIndexCategoryName;

    private final CategoryRuleSortListener mListener;
    private final List<Integer> mSortedIndices;

    public CategoryRuleSortCursorAdapter(CategoryRuleSortListener listener) {
        super(null, Contract.CategoryRule.ID);
        mListener = listener;
        mSortedIndices = new ArrayList<>();
    }

    @Override
    protected void onLoadColumnIndices(@NonNull Cursor cursor) {
        mIndexRuleId = cursor.getColumnIndex(Contract.CategoryRule.ID);
        mIndexPattern = cursor.getColumnIndex(Contract.CategoryRule.PATTERN);
        mIndexCategoryName = cursor.getColumnIndex(Contract.CategoryRule.CATEGORY_NAME);
    }

    @Override
    public void onBindViewHolder(@NonNull RuleViewHolder holder, int position) {
        if (position >= 0 && position < mSortedIndices.size()) {
            super.onBindViewHolder(holder, mSortedIndices.get(position));
        }
    }

    @Override
    public void onBindViewHolder(RuleViewHolder holder, Cursor cursor) {
        holder.mPatternTextView.setText(cursor.getString(mIndexPattern));
        holder.mCategoryTextView.setText(cursor.getString(mIndexCategoryName));
    }

    @NonNull
    @Override
    public RuleViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View itemView = inflater.inflate(R.layout.adapter_category_rule_sort_item, parent, false);
        return new RuleViewHolder(itemView);
    }

    @Override
    public long getItemId(int position) {
        if (position >= 0 && position < mSortedIndices.size()) {
            return super.getItemId(mSortedIndices.get(position));
        }
        return 0L;
    }

    @Override
    public Cursor swapCursor(Cursor newCursor) {
        Cursor oldCursor = super.swapCursor(newCursor);
        mSortedIndices.clear();
        if (newCursor != null) {
            for (int i = 0; i < newCursor.getCount(); i++) {
                mSortedIndices.add(i);
            }
        }
        return oldCursor;
    }

    public boolean moveItem(int from, int to) {
        if (Math.min(from, to) >= 0 && Math.max(from, to) < mSortedIndices.size()) {
            Collections.swap(mSortedIndices, from, to);
            notifyItemMoved(from, to);
            return true;
        }
        return false;
    }

    public List<Long> getSortedCategoryRuleIds() {
        List<Long> ruleIds = new ArrayList<>();
        for (int i = 0; i < mSortedIndices.size(); i++) {
            Cursor cursor = getSafeCursor(mSortedIndices.get(i));
            if (cursor != null) {
                ruleIds.add(cursor.getLong(mIndexRuleId));
            }
        }
        return ruleIds;
    }

    /*package-local*/ class RuleViewHolder extends RecyclerView.ViewHolder implements View.OnTouchListener {

        private TextView mPatternTextView;
        private TextView mCategoryTextView;
        private ImageView mActionImageView;

        /*package-local*/ RuleViewHolder(View itemView) {
            super(itemView);
            mPatternTextView = itemView.findViewById(R.id.primary_text_view);
            mCategoryTextView = itemView.findViewById(R.id.secondary_text_view);
            mActionImageView = itemView.findViewById(R.id.action_image_view);
            mActionImageView.setOnTouchListener(this);
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                if (mListener != null) {
                    mListener.onCategoryRuleDragStarted(this);
                }
            }
            return false;
        }
    }

    public interface CategoryRuleSortListener {

        void onCategoryRuleDragStarted(RecyclerView.ViewHolder viewHolder);
    }
}
