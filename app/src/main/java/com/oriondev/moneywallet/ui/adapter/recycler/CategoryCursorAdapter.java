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
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.CategoryChildIndicator;
import com.oriondev.moneywallet.utils.IconLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Created by andrea on 12/02/18.
 */
public class CategoryCursorAdapter extends AbstractCursorAdapter<CategoryCursorAdapter.CategoryViewHolder> {

    private static final int TYPE_PARENT = 0;
    private static final int TYPE_CHILD = 1;

    private int mIndexCategoryId;
    private int mIndexCategoryIcon;
    private int mIndexCategoryName;
    private int mIndexCategoryParentId;

    /**
     * Cursor row of each row on screen, in order. Hiding a category's children leaves their
     * cursor rows out of this list, so every position this adapter is asked about is read
     * through it.
     */
    private final List<Integer> mVisibleRows = new ArrayList<>();

    /**
     * Ids of the categories whose children are hidden, as of the last rebuild. Read from the
     * preference every time rather than kept, because the pager holds one adapter per tab and
     * the picker opens over the category screen, so several of these are alive at once over one
     * stored set. An adapter that trusted its own copy would write the others' hiding away.
     */
    private final Set<Long> mCollapsedCategories = new HashSet<>();

    /**
     * Ids of the categories that have at least one child in the current cursor. Only these get
     * an arrow drawn, which is what says a category has children at all.
     */
    private final Set<Long> mCategoriesWithChildren = new HashSet<>();

    private final CategoryActionListener mListener;

    private String mQuery = "";

    public CategoryCursorAdapter(CategoryActionListener listener) {
        super(null, Contract.Category.ID);
        mListener = listener;
    }

    @Override
    protected void onLoadColumnIndices(@NonNull Cursor cursor) {
        mIndexCategoryId = cursor.getColumnIndex(Contract.Category.ID);
        mIndexCategoryIcon = cursor.getColumnIndex(Contract.Category.ICON);
        mIndexCategoryName = cursor.getColumnIndex(Contract.Category.NAME);
        mIndexCategoryParentId = cursor.getColumnIndex(Contract.Category.PARENT);
        // The superclass calls this from two places: when it takes a new cursor, after that
        // cursor is in place and before it tells the list anything changed, and from its own
        // constructor. The constructor call cannot land here, because this adapter is always
        // built with no cursor and the fields the walk below needs do not exist until super
        // returns.
        rebuildVisibleRows();
    }

    public void setQuery(String query) {
        mQuery = (query == null ? "" : query.trim()).toLowerCase(Locale.getDefault());
        rebuildVisibleRows();
        notifyDataSetChanged();
    }

    private boolean matches(String name) {
        return name != null && name.toLowerCase(Locale.getDefault()).contains(mQuery);
    }

    private void rebuildVisibleRows() {
        mVisibleRows.clear();
        mCategoriesWithChildren.clear();
        mCollapsedCategories.clear();
        for (String categoryId : PreferenceManager.getCollapsedCategories()) {
            mCollapsedCategories.add(Long.valueOf(categoryId));
        }
        Cursor cursor = getCursor();
        if (cursor == null) {
            return;
        }
        // The filter keeps a category whose own name matches and a category that has a matching
        // child, and under a matching category it keeps all of its children. A child the user
        // has hidden is shown again while a filter is on.
        Set<Long> parentsOfMatchingChildren = new HashSet<>();
        if (!mQuery.isEmpty()) {
            for (int position = 0; position < cursor.getCount(); position++) {
                cursor.moveToPosition(position);
                if (!cursor.isNull(mIndexCategoryParentId) && matches(cursor.getString(mIndexCategoryName))) {
                    parentsOfMatchingChildren.add(cursor.getLong(mIndexCategoryParentId));
                }
            }
        }
        Set<Long> matchingParents = new HashSet<>();
        Set<Long> categoriesAbove = new HashSet<>();
        for (int position = 0; position < cursor.getCount(); position++) {
            cursor.moveToPosition(position);
            if (cursor.isNull(mIndexCategoryParentId)) {
                long categoryId = cursor.getLong(mIndexCategoryId);
                categoriesAbove.add(categoryId);
                boolean nameMatches = matches(cursor.getString(mIndexCategoryName));
                if (nameMatches) {
                    matchingParents.add(categoryId);
                }
                if (mQuery.isEmpty() || nameMatches || parentsOfMatchingChildren.contains(categoryId)) {
                    mVisibleRows.add(position);
                }
                continue;
            }
            long parentId = cursor.getLong(mIndexCategoryParentId);
            mCategoriesWithChildren.add(parentId);
            if (mQuery.isEmpty()) {
                // A child whose category is not above it in this cursor has no arrow that could
                // bring it back, so it is shown rather than hidden. Two things leave one: the
                // query drops a category marked deleted out of its join while keeping the
                // children that name it, and SyncContentProvider inserts rows straight onto the
                // table, past the check that refuses a child of a category of another type.
                if (!mCollapsedCategories.contains(parentId) || !categoriesAbove.contains(parentId)) {
                    mVisibleRows.add(position);
                }
            } else if (matchingParents.contains(parentId) || matches(cursor.getString(mIndexCategoryName))) {
                mVisibleRows.add(position);
            }
        }
    }

    /**
     * Draws the rows again against what is stored now. The list this adapter is in is not the
     * only one reading that store, so a list coming back to the front has to ask again rather
     * than draw what it last built.
     */
    public void reloadCollapsedCategories() {
        rebuildVisibleRows();
        notifyDataSetChanged();
    }

    private void toggleChildren(long categoryId) {
        // Read, change, write, so that the tabs and screens this adapter shares the stored set
        // with keep their own hiding.
        Set<String> stored = PreferenceManager.getCollapsedCategories();
        if (!stored.remove(String.valueOf(categoryId))) {
            stored.add(String.valueOf(categoryId));
        }
        PreferenceManager.setCollapsedCategories(stored);
        rebuildVisibleRows();
        notifyDataSetChanged();
    }

    /**
     * The cursor row an on screen position stands for, or -1 when there is none. Everything that
     * takes an on screen position goes through this, and so does every read of the cursor from a
     * click.
     */
    private int cursorPosition(int position) {
        return position >= 0 && position < mVisibleRows.size() ? mVisibleRows.get(position) : -1;
    }

    @Override
    public int getItemCount() {
        // Guarded rather than returned outright: a cursor swapped away for null leaves the rows
        // built for it behind, since the superclass only walks a cursor it actually has.
        return isDataValid() ? mVisibleRows.size() : 0;
    }

    @Override
    public long getItemId(int position) {
        return super.getItemId(cursorPosition(position));
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        super.onBindViewHolder(holder, cursorPosition(position));
    }

    @Override
    public void onBindViewHolder(CategoryViewHolder holder, Cursor cursor) {
        Icon icon = IconLoader.parse(cursor.getString(mIndexCategoryIcon));
        IconLoader.loadInto(icon, holder.mIconImageView);
        String name = cursor.getString(mIndexCategoryName);
        holder.mNameTextView.setText(name);
        if (holder.getItemViewType() == TYPE_CHILD) {
            holder.mChildIndicator.setLast(isLastChild(cursor.getPosition()));
        } else {
            bindChildrenToggle(holder, cursor.getLong(mIndexCategoryId), name);
        }
    }

    private void bindChildrenToggle(CategoryViewHolder holder, long categoryId, String name) {
        // While a filter is on, every child that matches is already shown, so the arrow would
        // have nothing to do.
        boolean hasChildren = mCategoriesWithChildren.contains(categoryId) && mQuery.isEmpty();
        holder.mChildrenToggle.setVisibility(hasChildren ? View.VISIBLE : View.GONE);
        if (hasChildren) {
            boolean collapsed = mCollapsedCategories.contains(categoryId);
            holder.mChildrenToggle.setRotation(collapsed ? 0f : 180f);
            // Named, because a screen reader reaches the arrow on its own and every one of them
            // would otherwise read the same words.
            holder.mChildrenToggle.setContentDescription(holder.itemView.getContext().getString(
                    collapsed ? R.string.description_show_subcategories
                            : R.string.description_hide_subcategories, name));
        }
    }

    /**
     * Whether the child at this cursor row is the last one drawn under its category, which is
     * what tells the indicator to stop its line half way. It reads the next row on screen and
     * not the next row in the cursor, because a filter can hide a sibling that follows in the
     * cursor.
     */
    private boolean isLastChild(int cursorPosition) {
        Cursor cursor = getCursor();
        int index = Collections.binarySearch(mVisibleRows, cursorPosition);
        if (index >= 0 && index + 1 < mVisibleRows.size()) {
            cursor.moveToPosition(mVisibleRows.get(index + 1));
            boolean nextIsChild = !cursor.isNull(mIndexCategoryParentId);
            cursor.moveToPosition(cursorPosition);
            return !nextIsChild;
        }
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        Cursor cursor = getSafeCursor(cursorPosition(position));
        return cursor.isNull(mIndexCategoryParentId) ? TYPE_PARENT : TYPE_CHILD;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_PARENT) {
            View itemView = inflater.inflate(R.layout.adapter_category_item, parent, false);
            return new CategoryViewHolder(itemView);
        } else {
            View itemView = inflater.inflate(R.layout.adapter_sub_category_item, parent, false);
            return new CategoryViewHolder(itemView);
        }
    }

    /*package-local*/ class CategoryViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private CategoryChildIndicator mChildIndicator;
        private ImageView mIconImageView;
        private ImageView mChildrenToggle;
        private TextView mNameTextView;

        /*package-local*/ CategoryViewHolder(View itemView) {
            super(itemView);
            mChildIndicator = itemView.findViewById(R.id.category_child_indicator);
            mIconImageView = itemView.findViewById(R.id.icon_image_view);
            mChildrenToggle = itemView.findViewById(R.id.children_toggle_image_view);
            mNameTextView = itemView.findViewById(R.id.name_text_view);
            itemView.setOnClickListener(this);
            if (mChildrenToggle != null) {
                // Its own listener, because a tap on the row still means what it always did:
                // pick this category, or open it for editing.
                mChildrenToggle.setOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View view) {
                        Cursor cursor = getSafeCursor(cursorPosition(getAdapterPosition()));
                        if (cursor != null) {
                            toggleChildren(cursor.getLong(mIndexCategoryId));
                        }
                    }

                });
            }
        }

        @Override
        public void onClick(View v) {
            if (mListener != null) {
                Cursor cursor = getSafeCursor(cursorPosition(getAdapterPosition()));
                if (cursor != null) {
                    mListener.onCategoryClick(cursor.getLong(mIndexCategoryId));
                }
            }
        }
    }

    public interface CategoryActionListener {

        void onCategoryClick(long id);
    }
}
