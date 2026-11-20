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

package com.oriondev.moneywallet.ui.view;

import android.content.Context;
import android.content.res.TypedArray;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.utils.SystemBars;

/**
 * Created by andrea on 26/01/18.
 */
public class AdvancedRecyclerView extends SwipeRefreshLayout {

    private RecyclerView mRecyclerView;
    private View mProgressWheel;
    private TextView mEmptyTextView;

    private int mEmptyTextRes;
    private int mErrorTextRes;
    private State mCurrentState;

    public AdvancedRecyclerView(Context context) {
        super(context);
        initialize(context, null);
    }

    public AdvancedRecyclerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    /**
     * The navigation bar inset is asked for here and not in view_advanced_recycler, because every
     * instance in the app inflates that one file, an attribute set there would turn the inset on
     * for all of them at once, and some of these lists are inside a secondary panel or a pager
     * page where it does not belong.
     */
    private void initialize(@NonNull Context context, @Nullable AttributeSet attrs) {
        inflate(context, R.layout.view_advanced_recycler, this);
        mRecyclerView = findViewById(R.id.recycler_view);
        mProgressWheel = findViewById(R.id.progress_wheel);
        mEmptyTextView = findViewById(R.id.empty_text_view);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.AdvancedRecyclerView, 0, 0);
        boolean insetSides;
        boolean insetBottom;
        try {
            insetSides = typedArray.getBoolean(R.styleable.AdvancedRecyclerView_systemBarInsetSides, true);
            insetBottom = typedArray.getBoolean(R.styleable.AdvancedRecyclerView_systemBarInsetBottom, false);
        } finally {
            typedArray.recycle();
        }
        if (insetBottom) {
            SystemBars.pad(mRecyclerView, false, insetSides, true);
        }
    }

    public RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    public TextView getTextView() {
        return mEmptyTextView;
    }

    public void setLayoutManager(RecyclerView.LayoutManager layoutManager) {
        mRecyclerView.setLayoutManager(layoutManager);
    }

    public void setAdapter(RecyclerView.Adapter adapter) {
        mRecyclerView.setAdapter(adapter);
    }

    /**
     * The text is also written by {@link #setState(State)}. Setting it on the view here as well
     * is what lets a caller refine the message after the empty view is already on screen, with
     * no further state change to carry it there.
     */
    public void setEmptyText(@StringRes int resId) {
        mEmptyTextRes = resId;
        mEmptyTextView.setText(resId);
    }

    public void setErrorText(@StringRes int resId) {
        mErrorTextRes = resId;
    }

    public void setState(State state) {
        if (mCurrentState != state) {
            switch (state) {
                case EMPTY:
                    mRecyclerView.setVisibility(View.GONE);
                    mProgressWheel.setVisibility(View.GONE);
                    mEmptyTextView.setText(mEmptyTextRes);
                    mEmptyTextView.setVisibility(View.VISIBLE);
                    setRefreshing(false);
                    break;
                case LOADING:
                    mRecyclerView.setVisibility(View.GONE);
                    mProgressWheel.setVisibility(View.VISIBLE);
                    mEmptyTextView.setVisibility(View.GONE);
                    setRefreshing(false);
                    break;
                case REFRESHING:
                    mRecyclerView.setVisibility(View.GONE);
                    mProgressWheel.setVisibility(View.GONE);
                    mEmptyTextView.setVisibility(View.GONE);
                    setRefreshing(true);
                    break;
                case READY:
                    mRecyclerView.setVisibility(View.VISIBLE);
                    mProgressWheel.setVisibility(View.GONE);
                    mEmptyTextView.setVisibility(View.GONE);
                    setRefreshing(false);
                    break;
                case ERROR:
                    mRecyclerView.setVisibility(View.GONE);
                    mProgressWheel.setVisibility(View.GONE);
                    mEmptyTextView.setText(mErrorTextRes);
                    mEmptyTextView.setVisibility(View.VISIBLE);
                    setRefreshing(false);
                    break;
            }
        }
    }

    public enum State {
        EMPTY,
        LOADING,
        REFRESHING,
        READY,
        ERROR
    }
}