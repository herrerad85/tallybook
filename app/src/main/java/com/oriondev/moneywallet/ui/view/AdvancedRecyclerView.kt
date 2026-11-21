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

package com.oriondev.moneywallet.ui.view

import android.content.Context
import androidx.annotation.StringRes
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.recyclerview.widget.RecyclerView
import android.util.AttributeSet
import android.view.View
import android.widget.TextView

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.utils.SystemBars

/**
 * Created by andrea on 26/01/18.
 */
class AdvancedRecyclerView : SwipeRefreshLayout {

    private var mRecyclerView: RecyclerView? = null
    private var mProgressWheel: View? = null
    private var mEmptyTextView: TextView? = null

    private var mEmptyTextRes: Int = 0
    private var mErrorTextRes: Int = 0
    private var mCurrentState: State? = null

    constructor(context: Context) : super(context) {
        initialize(context, null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs)
    }

    /**
     * The navigation bar inset is asked for here and not in view_advanced_recycler, because every
     * instance in the app inflates that one file, an attribute set there would turn the inset on
     * for all of them at once, and some of these lists are inside a secondary panel or a pager
     * page where it does not belong.
     */
    private fun initialize(context: Context, attrs: AttributeSet?) {
        inflate(context, R.layout.view_advanced_recycler, this)
        mRecyclerView = findViewById(R.id.recycler_view)
        mProgressWheel = findViewById(R.id.progress_wheel)
        mEmptyTextView = findViewById(R.id.empty_text_view)
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AdvancedRecyclerView, 0, 0)
        val insetSides: Boolean
        val insetBottom: Boolean
        try {
            insetSides = typedArray.getBoolean(R.styleable.AdvancedRecyclerView_systemBarInsetSides, true)
            insetBottom = typedArray.getBoolean(R.styleable.AdvancedRecyclerView_systemBarInsetBottom, false)
        } finally {
            typedArray.recycle()
        }
        if (insetBottom) {
            SystemBars.pad(mRecyclerView, false, insetSides, true)
        }
    }

    fun getRecyclerView(): RecyclerView? {
        return mRecyclerView
    }

    fun getTextView(): TextView? {
        return mEmptyTextView
    }

    fun setLayoutManager(layoutManager: RecyclerView.LayoutManager?) {
        mRecyclerView!!.layoutManager = layoutManager
    }

    fun setAdapter(adapter: RecyclerView.Adapter<*>?) {
        mRecyclerView!!.adapter = adapter
    }

    /**
     * The text is also written by [setState]. Setting it on the view here as well
     * is what lets a caller refine the message after the empty view is already on screen, with
     * no further state change to carry it there.
     */
    fun setEmptyText(@StringRes resId: Int) {
        mEmptyTextRes = resId
        mEmptyTextView!!.setText(resId)
    }

    fun setErrorText(@StringRes resId: Int) {
        mErrorTextRes = resId
    }

    fun setState(state: State?) {
        if (mCurrentState != state) {
            when (state) {
                State.EMPTY -> {
                    mRecyclerView!!.visibility = View.GONE
                    mProgressWheel!!.visibility = View.GONE
                    mEmptyTextView!!.setText(mEmptyTextRes)
                    mEmptyTextView!!.visibility = View.VISIBLE
                    setRefreshing(false)
                }
                State.LOADING -> {
                    mRecyclerView!!.visibility = View.GONE
                    mProgressWheel!!.visibility = View.VISIBLE
                    mEmptyTextView!!.visibility = View.GONE
                    setRefreshing(false)
                }
                State.REFRESHING -> {
                    mRecyclerView!!.visibility = View.GONE
                    mProgressWheel!!.visibility = View.GONE
                    mEmptyTextView!!.visibility = View.GONE
                    setRefreshing(true)
                }
                State.READY -> {
                    mRecyclerView!!.visibility = View.VISIBLE
                    mProgressWheel!!.visibility = View.GONE
                    mEmptyTextView!!.visibility = View.GONE
                    setRefreshing(false)
                }
                State.ERROR -> {
                    mRecyclerView!!.visibility = View.GONE
                    mProgressWheel!!.visibility = View.GONE
                    mEmptyTextView!!.setText(mErrorTextRes)
                    mEmptyTextView!!.visibility = View.VISIBLE
                    setRefreshing(false)
                }
                null -> {
                }
            }
        }
    }

    enum class State {
        EMPTY,
        LOADING,
        REFRESHING,
        READY,
        ERROR
    }
}