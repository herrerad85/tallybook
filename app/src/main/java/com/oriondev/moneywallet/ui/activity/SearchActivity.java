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

package com.oriondev.moneywallet.ui.activity;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.WindowManager;

import androidx.fragment.app.Fragment;

import com.oriondev.moneywallet.ui.activity.base.MultiPanelActivity;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment;
import com.oriondev.moneywallet.ui.fragment.multipanel.SearchMultiPanelFragment;

/**
 * Created by andrea on 04/04/18.
 */
public class SearchActivity extends MultiPanelActivity {

    private static final String TAG_FRAGMENT_SEARCH = "SearchActivity::SearchMultiPanelFragment";

    private boolean mAmountShowsKeyboardOnOpen;

    /**
     * True from a down outside the editor panel and the rail, while an editor is open, to the end
     * of that gesture.
     */
    private boolean mSwallowingGesture;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // once, since a resize that crosses no width bucket does not relaunch
        mAmountShowsKeyboardOnOpen = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        // replaces both halves of the declared mode, so the resize flag is repeated here
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | (mAmountShowsKeyboardOnOpen
                ? WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
                : WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN));
        super.onCreate(savedInstanceState);
    }

    /**
     * In landscape the keyboard goes full screen and would cover the amount editor's choices.
     */
    public boolean amountShowsKeyboardOnOpen() {
        return mAmountShowsKeyboardOnOpen;
    }

    /**
     * A tap outside an open editor only closes it. The close waits for the up, since a back swipe
     * sends a down and then a cancel, and the Back after it closes the editor itself.
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(TAG_FRAGMENT_SEARCH);
        SearchMultiPanelFragment search = fragment instanceof SearchMultiPanelFragment ? (SearchMultiPanelFragment) fragment : null;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mSwallowingGesture = search != null && search.isEditorShown()
                    && !search.isOnEditorOrRail(event.getRawX(), event.getRawY());
        }
        if (!mSwallowingGesture) {
            return super.dispatchTouchEvent(event);
        }
        if (action == MotionEvent.ACTION_UP) {
            mSwallowingGesture = false;
            if (search != null) {
                search.closeEditor();
            }
        } else if (action == MotionEvent.ACTION_CANCEL) {
            mSwallowingGesture = false;
        }
        return true;
    }

    @Override
    protected MultiPanelFragment onCreateMultiPanelFragment() {
        return new SearchMultiPanelFragment();
    }

    @Override
    protected String getMultiPanelFragmentTag() {
        return TAG_FRAGMENT_SEARCH;
    }
}
