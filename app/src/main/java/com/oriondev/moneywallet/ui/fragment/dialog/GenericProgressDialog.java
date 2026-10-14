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

package com.oriondev.moneywallet.ui.fragment.dialog;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.BaseProgressIndicator;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.text.NumberFormat;

/**
 * Created by andre on 22/03/2018.
 */
public class GenericProgressDialog extends DialogFragment {

    private static final String ARG_TITLE_RES = "GenericProgressDialog::Arguments::TitleRes";
    private static final String ARG_CONTENT_RES = "GenericProgressDialog::Arguments::ContentRes";
    private static final String ARG_INDETERMINATE = "GenericProgressDialog::Arguments::Indeterminate";

    private BaseProgressIndicator<?> mProgressIndicator;
    private TextView mMessageTextView;
    private TextView mPercentageTextView;

    public static GenericProgressDialog newInstance(int title, int content, boolean indeterminate) {
        GenericProgressDialog dialog = new GenericProgressDialog();
        Bundle arguments = new Bundle();
        arguments.putInt(ARG_TITLE_RES, title);
        arguments.putInt(ARG_CONTENT_RES, content);
        arguments.putBoolean(ARG_INDETERMINATE, indeterminate);
        dialog.setArguments(arguments);
        dialog.setCancelable(false);
        return dialog;
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        if (activity == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        Bundle arguments = getArguments();
        int titleRes = arguments != null ? arguments.getInt(ARG_TITLE_RES) : 0;
        int contentRes = arguments != null ? arguments.getInt(ARG_CONTENT_RES) : 0;
        boolean indeterminate = arguments != null && arguments.getBoolean(ARG_INDETERMINATE);
        int layoutRes = indeterminate ? R.layout.dialog_progress_indeterminate
                : R.layout.dialog_progress_determinate;
        MaterialAlertDialogBuilder builder = ThemedDialog.buildMaterialDialog(activity);
        if (titleRes != 0) {
            builder.setTitle(titleRes);
        }
        AlertDialog dialog = builder.setCancelable(false).create();
        // The dialog's own context carries the light or dark dialog theme the factory picked, so
        // the layout's theme attributes have to resolve against it and not against the activity.
        // This is the same clone DialogFragment makes for a dialog it inflates a content view for,
        // and it has to be made by hand here because getLayoutInflater() hands back the plain
        // activity inflater while onCreateDialog is still running.
        View view = getLayoutInflater().cloneInContext(dialog.getContext()).inflate(layoutRes, null);
        mProgressIndicator = view.findViewById(R.id.dialog_progress_indicator);
        mProgressIndicator.setMax(100);
        mProgressIndicator.setIndicatorColor(ThemedDialog.getAccentColor());
        if (!indeterminate) {
            mProgressIndicator.setTrackColor(ThemedDialog.getIdleColor());
        }
        mMessageTextView = view.findViewById(R.id.dialog_progress_message);
        // Only the determinate layout carries a percentage label.
        mPercentageTextView = view.findViewById(R.id.dialog_progress_percentage);
        setMessage(contentRes);
        setProgress(0);
        dialog.setView(view);
        return dialog;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mProgressIndicator = null;
        mMessageTextView = null;
        mPercentageTextView = null;
    }

    public void updateProgress(int contentRes, int progress) {
        if (mProgressIndicator == null) {
            return;
        }
        setMessage(contentRes);
        setProgress(progress);
    }

    private void setMessage(int contentRes) {
        mMessageTextView.setText(contentRes != 0 ? getText(contentRes) : null);
    }

    private void setProgress(int progress) {
        mProgressIndicator.setProgress(progress);
        if (mPercentageTextView != null) {
            // material-dialogs wrote this label through its default progressPercentFormat, which
            // is NumberFormat.getPercentInstance().
            NumberFormat format = NumberFormat.getPercentInstance();
            mPercentageTextView.setText(format.format(progress / (float) mProgressIndicator.getMax()));
        }
    }
}