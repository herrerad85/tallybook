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

package com.oriondev.moneywallet.ui.fragment.secondary;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SQLiteDataException;
import com.oriondev.moneywallet.ui.activity.NewEditCategoryActivity;
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity;
import com.oriondev.moneywallet.ui.activity.TransactionListActivity;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;

/**
 * Created by andrea on 01/04/18.
 */
public class CategoryItemFragment extends SecondaryPanelFragment implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final int CATEGORY_LOADER_ID = 246327;

    private View mProgressLayout;
    private View mMainLayout;

    private ImageView mAvatarImageView;
    private TextView mNameTextView;
    private TextView mParentCategoryTextView;
    private RadioGroup mCategoryTypeRadioGroup;
    private RadioButton mIncomeRadioButton;
    private RadioButton mExpenseRadioButton;
    private CheckBox mShowReportCheckBox;

    @Override
    protected void onCreateHeaderView(LayoutInflater inflater, @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_header_show_icon_name_item, parent, true);
        mAvatarImageView = view.findViewById(R.id.avatar_image_view);
        mNameTextView = view.findViewById(R.id.name_text_view);
    }

    @Override
    protected void onCreateBodyView(LayoutInflater inflater, @NonNull ViewGroup parent, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_panel_show_category_item, parent, true);
        mProgressLayout = view.findViewById(R.id.secondary_panel_progress_wheel);
        mMainLayout = view.findViewById(R.id.secondary_panel_layout);
        mParentCategoryTextView = view.findViewById(R.id.parent_category_text_view);
        mCategoryTypeRadioGroup = view.findViewById(R.id.category_type);
        mIncomeRadioButton = view.findViewById(R.id.income_radio_button);
        mExpenseRadioButton = view.findViewById(R.id.expense_radio_button);
        mShowReportCheckBox = view.findViewById(R.id.show_report_check_box);
    }

    @Override
    protected String getTitle() {
        return getString(R.string.title_fragment_item_category);
    }

    @Override
    protected int onInflateMenu() {
        return R.menu.menu_list_edit_delete_item;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_show_transaction_list) {
            Intent intent = new Intent(getActivity(), TransactionListActivity.class);
            intent.putExtra(TransactionListActivity.CATEGORY_ID, getItemId());
            startActivity(intent);
        } else if (itemId == R.id.action_edit_item) {
            Intent intent = new Intent(getActivity(), NewEditCategoryActivity.class);
            intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
            intent.putExtra(NewEditItemActivity.ID, getItemId());
            startActivity(intent);
        } else if (itemId == R.id.action_delete_item) {
            showDeleteDialog(getActivity());
        }
        return false;
    }

    private void showDeleteDialog(Context context) {
        ThemedDialog.buildMaterialDialog(context)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_delete_category)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Activity activity = getActivity();
                        if (activity != null) {
                            try {
                                Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, getItemId());
                                ContentResolver contentResolver = activity.getContentResolver();
                                contentResolver.delete(uri, null, null);
                                navigateBackSafely();
                                showItemId(0L);
                            } catch (SQLiteDataException e) {
                                if (e.getErrorCode() == Contract.ErrorCode.CATEGORY_IN_SPLIT) {
                                    ThemedDialog.buildMaterialDialog(activity)
                                            .setTitle(R.string.title_error)
                                            .setMessage(describeSplitRefusal(activity, e.getItemIds()))
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                    return;
                                }
                                int contentRes = 0;
                                switch (e.getErrorCode()) {
                                    case Contract.ErrorCode.CATEGORY_HAS_CHILDREN:
                                        contentRes = R.string.message_error_delete_category_with_children;
                                        break;
                                    case Contract.ErrorCode.CATEGORY_IN_USE:
                                        contentRes = R.string.message_error_delete_category_in_use;
                                        break;
                                    case Contract.ErrorCode.SYSTEM_CATEGORY_NOT_MODIFIABLE:
                                        contentRes = R.string.message_error_delete_system_category;
                                        break;
                                }
                                if (contentRes != 0) {
                                    ThemedDialog.buildMaterialDialog(activity)
                                            .setTitle(R.string.title_error)
                                            .setMessage(contentRes)
                                            .setPositiveButton(android.R.string.ok, null)
                                            .show();
                                }
                            }
                        }
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /**
     * Names the newest of the split transactions holding a part in this category by its date and
     * its description, or its amount when it has none, and counts the others.
     */
    /*package-local*/ static String describeSplitRefusal(Context context, long[] transactionIds) {
        String name = "?";
        String date = "?";
        if (transactionIds.length > 0) {
            Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionIds[0]);
            String[] projection = new String[] {
                    Contract.Transaction.DATE,
                    Contract.Transaction.DESCRIPTION,
                    Contract.Transaction.MONEY,
                    Contract.Transaction.WALLET_CURRENCY
            };
            try (Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    date = DateFormatter.getFormattedDate(DateUtils.getDateFromSQLDateTimeString(
                            cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DATE))));
                    name = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.DESCRIPTION));
                    if (TextUtils.isEmpty(name)) {
                        name = MoneyFormatter.getInstance().getNotTintedString(CurrencyManager.getCurrency(
                                cursor.getString(cursor.getColumnIndexOrThrow(Contract.Transaction.WALLET_CURRENCY))),
                                cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Transaction.MONEY)));
                    }
                }
            }
        }
        String message = context.getString(R.string.message_error_delete_category_in_split, name, date);
        int more = transactionIds.length - 1;
        if (more > 0) {
            message += " " + context.getResources().getQuantityString(
                    R.plurals.message_error_delete_category_in_more_splits, more, more);
        }
        return message;
    }

    @Override
    protected void onShowItemId(long itemId) {
        setLoadingScreen(true);
        LoaderManager.getInstance(this).restartLoader(CATEGORY_LOADER_ID, null, this);
    }

    private void setLoadingScreen(boolean loading) {
        if (loading) {
            mAvatarImageView.setImageDrawable(null);
            mNameTextView.setText(null);
            mProgressLayout.setVisibility(View.VISIBLE);
            mMainLayout.setVisibility(View.GONE);
        } else {
            mProgressLayout.setVisibility(View.GONE);
            mMainLayout.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Activity activity = getActivity();
        if (activity != null) {
            Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, getItemId());
            String[] projection = new String[] {
                    Contract.Category.NAME,
                    Contract.Category.ICON,
                    Contract.Category.PARENT,
                    Contract.Category.PARENT_NAME,
                    Contract.Category.TYPE,
                    Contract.Category.SHOW_REPORT,
                    Contract.Category.PARENT_SHOW_REPORT
            };
            return new CursorLoader(getActivity(), uri, projection, null, null, null);
        }
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor) {
        if (cursor != null && cursor.moveToFirst()) {
            Icon icon = IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Category.ICON)));
            IconLoader.loadInto(icon, mAvatarImageView);
            mNameTextView.setText(cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)));
            if (!cursor.isNull(cursor.getColumnIndex(Contract.Category.PARENT))) {
                mParentCategoryTextView.setText(cursor.getString(cursor.getColumnIndex(Contract.Category.PARENT_NAME)));
                mParentCategoryTextView.setVisibility(View.VISIBLE);
            } else {
                mParentCategoryTextView.setVisibility(View.GONE);
            }
            Contract.CategoryType type = Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE)));
            if (type != null) {
                switch (type) {
                    case INCOME:
                        mIncomeRadioButton.setChecked(true);
                        mCategoryTypeRadioGroup.setVisibility(View.VISIBLE);
                        break;
                    case EXPENSE:
                        mExpenseRadioButton.setChecked(true);
                        mCategoryTypeRadioGroup.setVisibility(View.VISIBLE);
                        break;
                    default:
                        mCategoryTypeRadioGroup.setVisibility(View.GONE);
                        break;
                }
            } else {
                mCategoryTypeRadioGroup.setVisibility(View.GONE);
            }
            if (cursor.getInt(cursor.getColumnIndex(Contract.Category.SHOW_REPORT)) == 1) {
                mShowReportCheckBox.setChecked(true);
                // the reports keep a transaction only when its category and its parent are both
                // included, so a subcategory under a hidden parent is out however its own box
                // reads. A top level category joins no parent row, and the null that leaves here
                // means included, which is how the report filter reads it too.
                int parentShowReport = cursor.getColumnIndex(Contract.Category.PARENT_SHOW_REPORT);
                int parentName = cursor.getColumnIndex(Contract.Category.PARENT_NAME);
                if (!cursor.isNull(parentShowReport) && cursor.getInt(parentShowReport) == 0) {
                    mShowReportCheckBox.setText(getString(R.string.hint_show_category_report_off_parent,
                            cursor.getString(parentName)));
                } else {
                    mShowReportCheckBox.setText(R.string.hint_show_category_report_on);
                }
            } else {
                mShowReportCheckBox.setChecked(false);
                mShowReportCheckBox.setText(R.string.hint_show_category_report_off);
            }
        } else {
            showItemId(0L);
        }
        setLoadingScreen(false);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        // nothing to release
    }
}