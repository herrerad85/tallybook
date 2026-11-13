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

import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.CallSuper;
import androidx.annotation.MenuRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import android.view.MenuItem;
import android.widget.Toast;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelScrollActivity;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.util.Arrays;
import java.util.Date;

/**
 * This activity is an abstract implementation that act as a base for every
 * activity that is capable to insert or modify an item of the application.
 * It simplifies the development creating a much easier api to use.
 */
public abstract class NewEditItemActivity extends SinglePanelScrollActivity {

    public static final String MODE = "NewEditItemActivity::Mode";
    public static final String ID = "NewEditItemActivity::Id";

    private static final String SS_FILLED_VALUES = "NewEditItemActivity::SavedState::FilledValues";

    public enum Mode {

        /**
         * This mode indicates that a new item must be created.
         */
        NEW_ITEM,

        /**
         *  When the mode is EDIT_ITEM, the caller MUST set a valid item id or a RuntimeException
         *  is thrown.
         */
        EDIT_ITEM
    }

    private Mode mMode;
    private Long mId;

    private String[] mFilledValues;

    @Override
    @CallSuper
    protected void onViewCreated(Bundle savedInstanceState) {
        Intent intent = getIntent();
        if (intent != null) {
            mMode = (Mode) intent.getSerializableExtra(MODE);
            if (mMode != null) {
                if (mMode == Mode.EDIT_ITEM) {
                    mId = intent.getLongExtra(ID, 0L);
                    if (mId <= 0L) {
                        throw new RuntimeException("Trying to edit an item with an invalid id");
                    }
                } else if (mMode == Mode.NEW_ITEM) {
                    mId = -1L;
                }
            } else {
                mMode = Mode.NEW_ITEM;
                mId = -1L;
            }
        }
    }

    @Override
    @CallSuper
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        if (savedInstanceState == null) {
            onFieldsFilled();
        } else {
            mFilledValues = savedInstanceState.getStringArray(SS_FILLED_VALUES);
        }
        if (getFieldValues() != null) {
            getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {

                @Override
                public void handleOnBackPressed() {
                    if (!Arrays.equals(mFilledValues, getFieldValues())) {
                        ThemedDialog.buildMaterialDialog(NewEditItemActivity.this)
                                .setTitle(R.string.title_discard_changes)
                                .setPositiveButton(R.string.action_discard, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        onDiscardChanges();
                                        finish();
                                    }

                                })
                                .setNegativeButton(R.string.action_keep_editing, null)
                                .show();
                    } else {
                        onDiscardChanges();
                        finish();
                    }
                }

            });
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArray(SS_FILLED_VALUES, mFilledValues);
    }

    @Override
    @StringRes
    protected int getActivityTitleRes() {
        return getActivityTileRes(mMode);
    }

    protected Mode getMode() {
        return mMode;
    }

    protected long getItemId() {
        return mId;
    }

    /**
     * This method is an implementation of the standard {@link SinglePanelActivity#getActivityTitleRes()}
     * to let the sub activity return the correct title according to the current mode.
     * @param mode current mode of the activity.
     * @return the string to set as title.
     */
    @StringRes
    protected abstract int getActivityTileRes(Mode mode);

    @Override
    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_new_edit_item;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_save_changes) {
            onSaveChanges(mMode);
        }
        return false;
    }

    /**
     * When this method is called, the sub activity should check for data consistency
     * and correctness, then modify the data inside the content provider.
     * @param mode current mode of the activity.
     */
    protected abstract void onSaveChanges(Mode mode);

    /**
     * @return the values of every field the user can change, compared on back with the ones the
     *         editor was filled with to decide whether to ask before closing, or null when the
     *         editor closes without asking.
     */
    protected String[] getFieldValues() {
        return null;
    }

    protected void onFieldsFilled() {
        mFilledValues = getFieldValues();
    }

    protected void onDiscardChanges() {

    }

    /**
     * Ends a save, saying first that the item is not counted yet when the date it was saved with
     * is ahead of now. The transaction and transfer lists, the per item lists, the overview and
     * every balance all filter on DATETIME(date) <= DATETIME('now', 'localtime'), so a row dated
     * ahead is written and then counts for nothing, and the save reads as though it did nothing.
     *
     * A toast rather than a dialog, because this activity is closing and a dialog would have to
     * hold it open to be read.
     *
     * The wording says what the balance does rather than where the row is. The editor can close
     * back to another application entirely, or to search, which carries no date filter and does
     * list the row.
     *
     * @param date the date the item was saved with.
     */
    protected void finishSaveDatedAt(@Nullable Date date) {
        if (date != null && date.after(new Date())) {
            Toast.makeText(this, R.string.message_saved_dated_ahead, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}