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
import android.content.DialogInterface;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.RecurrenceSetting;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.ui.view.theme.ThemedSpinner;
import com.oriondev.moneywallet.utils.DateFormatter;

import java.util.Date;
import java.util.Locale;

/**
 * Created by andrea on 07/11/18.
 */
public class RecurrencePickerDialog extends DialogFragment implements DateTimePicker.Controller {

    private static final String TAG_START_DATE_PICKER = "RecurrencePickerDialog::Tag::StartDatePicker";
    private static final String TAG_END_DATE_PICKER = "RecurrencePickerDialog::Tag::EndDatePicker";

    private static final String SS_RECURRENCE_SETTING = "RecurrencePickerDialog::SavedState::RecurrenceSetting";
    private static final String SS_END_TYPE_ENABLED = "RecurrencePickerDialog::SavedState::EndTypeEnabled";
    private static final String SS_ONCE_A_PERIOD = "RecurrencePickerDialog::SavedState::OnceAPeriod";

    public static RecurrencePickerDialog newInstance() {
        return new RecurrencePickerDialog();
    }

    private Callback mCallback;

    private RecurrenceSetting mRecurrenceSetting;

    private boolean mEndTypeEnabled = true;

    private boolean mOnceAPeriod = false;

    private ThemedSpinner mRecurrenceTypeSpinner;
    private MaterialEditText mRecurrenceStartDateSpinner;
    private EditText mRecurrenceEveryNumberEditText;
    private TextView mRecurrenceEveryItemTextView;
    private LinearLayout mRecurrenceTypeWeeklyLayout;
    private CheckBox mRecurrenceTypeWeeklySundayRadioButton;
    private CheckBox mRecurrenceTypeWeeklyMondayRadioButton;
    private CheckBox mRecurrenceTypeWeeklyTuesdayRadioButton;
    private CheckBox mRecurrenceTypeWeeklyWednesdayRadioButton;
    private CheckBox mRecurrenceTypeWeeklyThursdayRadioButton;
    private CheckBox mRecurrenceTypeWeeklyFridayRadioButton;
    private CheckBox mRecurrenceTypeWeeklySaturdayRadioButton;
    private LinearLayout mRecurrenceTypeMonthlyLayout;
    private RadioButton mRecurrenceTypeMonthlySameDayRadioButton;
    private ThemedSpinner mRecurrenceEndTypeSpinner;
    private LinearLayout mRecurrenceTimesLayout;
    private EditText mRecurrenceTimesNumberEditText;
    private MaterialEditText mRecurrenceEndDateSpinner;

    private DateTimePicker mStartDatePicker;
    private DateTimePicker mEndDatePicker;

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Activity activity = getActivity();
        if (activity == null) {
            return super.onCreateDialog(savedInstanceState);
        }
        if (savedInstanceState != null) {
            mRecurrenceSetting = savedInstanceState.getParcelable(SS_RECURRENCE_SETTING);
            mEndTypeEnabled = savedInstanceState.getBoolean(SS_END_TYPE_ENABLED, true);
            mOnceAPeriod = savedInstanceState.getBoolean(SS_ONCE_A_PERIOD, false);
        }
        // create the dialog
        View view = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_recurrence_setting_picker);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.dialog_recurrence_title)
                .setView(view)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mRecurrenceSetting = getCurrentRecurrenceSetting();
                        if (mCallback != null) {
                            mCallback.onRecurrenceSettingChanged(mRecurrenceSetting);
                        }
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
        mRecurrenceTypeSpinner = view.findViewById(R.id.type_spinner);
        mRecurrenceStartDateSpinner = view.findViewById(R.id.start_date_spinner);
        mRecurrenceEveryNumberEditText = view.findViewById(R.id.every_number_edit_text);
        mRecurrenceEveryItemTextView = view.findViewById(R.id.every_item_text_view);
        mRecurrenceTypeWeeklyLayout = view.findViewById(R.id.type_weekly_layout);
        mRecurrenceTypeWeeklySundayRadioButton = view.findViewById(R.id.type_weekly_sunday);
        mRecurrenceTypeWeeklyMondayRadioButton = view.findViewById(R.id.type_weekly_monday);
        mRecurrenceTypeWeeklyTuesdayRadioButton = view.findViewById(R.id.type_weekly_tuesday);
        mRecurrenceTypeWeeklyWednesdayRadioButton = view.findViewById(R.id.type_weekly_wednesday);
        mRecurrenceTypeWeeklyThursdayRadioButton = view.findViewById(R.id.type_weekly_thursday);
        mRecurrenceTypeWeeklyFridayRadioButton = view.findViewById(R.id.type_weekly_friday);
        mRecurrenceTypeWeeklySaturdayRadioButton = view.findViewById(R.id.type_weekly_saturday);
        mRecurrenceTypeMonthlyLayout = view.findViewById(R.id.type_monthly_layout);
        mRecurrenceTypeMonthlySameDayRadioButton = view.findViewById(R.id.type_monthly_repeat_same_day);
        mRecurrenceEndTypeSpinner = view.findViewById(R.id.end_type_spinner);
        mRecurrenceTimesLayout = view.findViewById(R.id.end_type_for_layout);
        mRecurrenceTimesNumberEditText = view.findViewById(R.id.end_type_for_edit_text);
        mRecurrenceEndDateSpinner = view.findViewById(R.id.end_date_spinner);
        // these two rows open a date picker instead of a dropdown, so they must not take focus
        // or raise the keyboard the way an editable field would
        mRecurrenceStartDateSpinner.setTextViewMode(true);
        mRecurrenceEndDateSpinner.setTextViewMode(true);
        // configure spinners
        mRecurrenceTypeSpinner.setAdapter(new ThemedSpinner.Adapter(activity,
                getString(R.string.recurrence_type_daily),
                getString(R.string.recurrence_type_weekly),
                getString(R.string.recurrence_type_monthly),
                getString(R.string.recurrence_type_yearly)
        ));
        mRecurrenceEndTypeSpinner.setAdapter(new ThemedSpinner.Adapter(activity,
                getString(R.string.recurrence_end_type_forever),
                getString(R.string.recurrence_end_type_until),
                getString(R.string.recurrence_end_type_for)
        ));
        mRecurrenceTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        updateRecurrenceType(RecurrenceSetting.TYPE_DAILY, true);
                        break;
                    case 1:
                        updateRecurrenceType(RecurrenceSetting.TYPE_WEEKLY, true);
                        break;
                    case 2:
                        updateRecurrenceType(RecurrenceSetting.TYPE_MONTHLY, true);
                        break;
                    case 3:
                        updateRecurrenceType(RecurrenceSetting.TYPE_YEARLY, true);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // only reached when the adapter empties, which it never does here
            }

        });
        mRecurrenceStartDateSpinner.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mStartDatePicker.showDatePicker();
            }

        });
        mRecurrenceTypeWeeklySundayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceTypeWeeklyMondayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceTypeWeeklyTuesdayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceTypeWeeklyWednesdayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceTypeWeeklyThursdayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceTypeWeeklyFridayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceTypeWeeklySaturdayRadioButton.setOnCheckedChangeListener(mWeekdayChangeListener);
        mRecurrenceEndTypeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0:
                        updateRecurrenceEndType(RecurrenceSetting.END_FOREVER, true);
                        break;
                    case 1:
                        updateRecurrenceEndType(RecurrenceSetting.END_UNTIL, true);
                        break;
                    case 2:
                        updateRecurrenceEndType(RecurrenceSetting.END_FOR, true);
                        break;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // only reached when the adapter empties, which it never does here
            }

        });
        mRecurrenceEndDateSpinner.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mEndDatePicker.showDatePicker();
            }

        });
        // create sub-pickers
        FragmentManager fragmentManager = getChildFragmentManager();
        mStartDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_START_DATE_PICKER, mRecurrenceSetting.getStartDate());
        mEndDatePicker = DateTimePicker.createPicker(fragmentManager, TAG_END_DATE_PICKER, mRecurrenceSetting.getEndDate());
        // update the whole ui using the recurrence object
        updateRecurrenceType(mRecurrenceSetting.getType(), false);
        updateRecurrenceOffsetValue(mRecurrenceSetting.getOffsetValue());
        updateRecurrenceWeekDays(mRecurrenceSetting.getWeekDays());
        updateRecurrenceMonthDay(mRecurrenceSetting.getMonthDay());
        updateRecurrenceEndType(mRecurrenceSetting.getEndType(), false);
        updateRecurrenceOccurrenceValue(mRecurrenceSetting.getOccurrenceValue());
        if (view != null && !mEndTypeEnabled) {
            view.findViewById(R.id.end_layout).setVisibility(View.GONE);
        }
        if (mOnceAPeriod) {
            mRecurrenceTypeWeeklyLayout.setVisibility(View.GONE);
        }
        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SS_RECURRENCE_SETTING, getCurrentRecurrenceSetting());
        outState.putBoolean(SS_END_TYPE_ENABLED, mEndTypeEnabled);
        outState.putBoolean(SS_ONCE_A_PERIOD, mOnceAPeriod);
    }

    private void updateRecurrenceType(int type, boolean self) {
        switch (type) {
            case RecurrenceSetting.TYPE_DAILY:
                mRecurrenceEveryItemTextView.setText(R.string.recurrence_hint_days);
                break;
            case RecurrenceSetting.TYPE_WEEKLY:
                mRecurrenceEveryItemTextView.setText(R.string.recurrence_hint_weeks);
                break;
            case RecurrenceSetting.TYPE_MONTHLY:
                mRecurrenceEveryItemTextView.setText(R.string.recurrence_hint_months);
                break;
            case RecurrenceSetting.TYPE_YEARLY:
                mRecurrenceEveryItemTextView.setText(R.string.recurrence_hint_years);
                break;
        }
        mRecurrenceTypeWeeklyLayout.setVisibility(type == RecurrenceSetting.TYPE_WEEKLY && !mOnceAPeriod ? View.VISIBLE : View.GONE);
        mRecurrenceTypeMonthlyLayout.setVisibility(type == RecurrenceSetting.TYPE_MONTHLY ? View.VISIBLE : View.GONE);
        if (!self) {
            switch (type) {
                case RecurrenceSetting.TYPE_DAILY:
                    mRecurrenceTypeSpinner.setSelection(0);
                    break;
                case RecurrenceSetting.TYPE_WEEKLY:
                    mRecurrenceTypeSpinner.setSelection(1);
                    break;
                case RecurrenceSetting.TYPE_MONTHLY:
                    mRecurrenceTypeSpinner.setSelection(2);
                    break;
                case RecurrenceSetting.TYPE_YEARLY:
                    mRecurrenceTypeSpinner.setSelection(3);
                    break;
            }
        }
    }

    private void updateRecurrenceOffsetValue(int offset) {
        mRecurrenceEveryNumberEditText.setText(String.format(Locale.getDefault(), "%d", offset));
    }

    private void updateRecurrenceWeekDays(boolean[] days) {
        mRecurrenceTypeWeeklySundayRadioButton.setChecked(days[0]);
        mRecurrenceTypeWeeklyMondayRadioButton.setChecked(days[1]);
        mRecurrenceTypeWeeklyTuesdayRadioButton.setChecked(days[2]);
        mRecurrenceTypeWeeklyWednesdayRadioButton.setChecked(days[3]);
        mRecurrenceTypeWeeklyThursdayRadioButton.setChecked(days[4]);
        mRecurrenceTypeWeeklyFridayRadioButton.setChecked(days[5]);
        mRecurrenceTypeWeeklySaturdayRadioButton.setChecked(days[6]);
    }

    private void updateRecurrenceMonthDay(int flag) {
        mRecurrenceTypeMonthlySameDayRadioButton.setChecked(flag == RecurrenceSetting.FLAG_MONTHLY_SAME_DAY);
    }

    private void updateRecurrenceEndType(int type, boolean self) {
        mRecurrenceEndDateSpinner.setVisibility(type == RecurrenceSetting.END_UNTIL ? View.VISIBLE : View.GONE);
        mRecurrenceTimesLayout.setVisibility(type == RecurrenceSetting.END_FOR ? View.VISIBLE : View.GONE);
        if (!self) {
            switch (type) {
                case RecurrenceSetting.END_FOREVER:
                    mRecurrenceEndTypeSpinner.setSelection(0);
                    break;
                case RecurrenceSetting.END_UNTIL:
                    mRecurrenceEndTypeSpinner.setSelection(1);
                    break;
                case RecurrenceSetting.END_FOR:
                    mRecurrenceEndTypeSpinner.setSelection(2);
                    break;
            }
        }
    }

    private void updateRecurrenceOccurrenceValue(int occurrences) {
        mRecurrenceTimesNumberEditText.setText(String.format(Locale.getDefault(), "%d", occurrences));
    }

    private int getCurrentRecurrenceType() {
        switch (mRecurrenceTypeSpinner.getSelectedItemPosition()) {
            case 0:
                return RecurrenceSetting.TYPE_DAILY;
            case 1:
                return RecurrenceSetting.TYPE_WEEKLY;
            case 2:
                return RecurrenceSetting.TYPE_MONTHLY;
            case 3:
                return RecurrenceSetting.TYPE_YEARLY;
        }
        return RecurrenceSetting.TYPE_DAILY;
    }

    private int getCurrentRecurrenceOffset() {
        try {
            return Integer.parseInt(mRecurrenceEveryNumberEditText.getText().toString());
        } catch (NumberFormatException ignore) {}
        return 1;
    }

    private boolean[] getCurrentRecurrenceWeekDays() {
        return new boolean[] {
                mRecurrenceTypeWeeklySundayRadioButton.isChecked(),
                mRecurrenceTypeWeeklyMondayRadioButton.isChecked(),
                mRecurrenceTypeWeeklyTuesdayRadioButton.isChecked(),
                mRecurrenceTypeWeeklyWednesdayRadioButton.isChecked(),
                mRecurrenceTypeWeeklyThursdayRadioButton.isChecked(),
                mRecurrenceTypeWeeklyFridayRadioButton.isChecked(),
                mRecurrenceTypeWeeklySaturdayRadioButton.isChecked()
        };
    }

    private int getCurrentRecurrenceEndType() {
        switch (mRecurrenceEndTypeSpinner.getSelectedItemPosition()) {
            case 0:
                return RecurrenceSetting.END_FOREVER;
            case 1:
                return RecurrenceSetting.END_UNTIL;
            case 2:
                return RecurrenceSetting.END_FOR;
        }
        return RecurrenceSetting.END_FOREVER;
    }

    private int getCurrentRecurrenceEndOccurrences() {
        try {
            return Integer.parseInt(mRecurrenceTimesNumberEditText.getText().toString());
        } catch (NumberFormatException ignore) {}
        return 1;
    }

    public RecurrenceSetting getCurrentRecurrenceSetting() {
        Date startDate = mStartDatePicker.getCurrentDateTime();
        int type = getCurrentRecurrenceType();
        RecurrenceSetting.Builder builder = new RecurrenceSetting.Builder(startDate, type);
        builder.setOffset(getCurrentRecurrenceOffset());
        if (type == RecurrenceSetting.TYPE_WEEKLY && !mOnceAPeriod) {
            builder.setRepeatWeekDay(getCurrentRecurrenceWeekDays());
        } else if (type == RecurrenceSetting.TYPE_MONTHLY) {
            builder.setRepeatSameMonthDay();
        } else if (type == RecurrenceSetting.TYPE_YEARLY) {
            builder.setRepeatSameYearDay();
        }
        switch (getCurrentRecurrenceEndType()) {
            case RecurrenceSetting.END_UNTIL:
                builder.setEndUntil(mEndDatePicker.getCurrentDateTime());
                break;
            case RecurrenceSetting.END_FOR:
                builder.setEndFor(getCurrentRecurrenceEndOccurrences());
                break;
        }
        return builder.build();
    }

    public void setCallback(Callback callback) {
        mCallback = callback;
    }

    public void showPicker(FragmentManager fragmentManager, String tag, RecurrenceSetting recurrenceSetting, boolean endTypeEnabled, boolean onceAPeriod) {
        mRecurrenceSetting = recurrenceSetting;
        mEndTypeEnabled = endTypeEnabled;
        mOnceAPeriod = onceAPeriod;
        show(fragmentManager, tag);
    }

    @Override
    public void onDateTimeChanged(String tag, Date date) {
        switch (tag) {
            case TAG_START_DATE_PICKER:
                mRecurrenceStartDateSpinner.setText(DateFormatter.getFormattedDate(date));
                break;
            case TAG_END_DATE_PICKER:
                mRecurrenceEndDateSpinner.setText(DateFormatter.getFormattedDate(date));
                break;
        }
    }

    private CompoundButton.OnCheckedChangeListener mWeekdayChangeListener = new CompoundButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!mRecurrenceTypeWeeklySundayRadioButton.isChecked() && !mRecurrenceTypeWeeklyMondayRadioButton.isChecked() && !mRecurrenceTypeWeeklyTuesdayRadioButton.isChecked() && !mRecurrenceTypeWeeklyWednesdayRadioButton.isChecked() && !mRecurrenceTypeWeeklyThursdayRadioButton.isChecked() && !mRecurrenceTypeWeeklyFridayRadioButton.isChecked() && !mRecurrenceTypeWeeklySaturdayRadioButton.isChecked()) {
                // if no checkbox is checked, force this checkbox to not be unchecked
                buttonView.setChecked(true);
            }
        }

    };

    public interface Callback {

        void onRecurrenceSettingChanged(RecurrenceSetting recurrenceSetting);
    }
}