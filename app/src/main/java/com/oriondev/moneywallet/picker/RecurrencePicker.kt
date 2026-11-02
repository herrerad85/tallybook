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

package com.oriondev.moneywallet.picker;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.oriondev.moneywallet.model.RecurrenceSetting;
import com.oriondev.moneywallet.ui.fragment.dialog.RecurrencePickerDialog;

/**
 * Created by andrea on 07/11/18.
 */

public class RecurrencePicker extends Fragment implements RecurrencePickerDialog.Callback {

    private static final String SS_RECURRENCE_SETTING = "RecurrencePicker::SavedState::RecurrenceSetting";
    private static final String SS_CHOSEN = "RecurrencePicker::SavedState::Chosen";

    private static final String ARG_RECURRENCE_SETTING = "RecurrencePicker::Argument::RecurrenceSetting";
    private static final String ARG_END_TYPE_ENABLED = "RecurrencePicker::Argument::EndTypeEnabled";
    private static final String ARG_ONCE_A_PERIOD = "RecurrencePicker::Argument::OnceAPeriod";

    private Controller mController;

    private RecurrenceSetting mRecurrenceSetting;

    private boolean mChosen;

    private boolean mEndTypeEnabled;

    private boolean mOnceAPeriod;

    private RecurrencePickerDialog mRecurrencePickerDialog;

    public static RecurrencePicker createPicker(FragmentManager fragmentManager, String tag, RecurrenceSetting recurrenceSetting) {
        return createPicker(fragmentManager, tag, recurrenceSetting, true, false);
    }

    /**
     * @param endTypeEnabled false to hide the control that stops the recurrence after a date or a
     *                       number of times, for a caller whose recurrence can only run forever.
     * @param onceAPeriod    true to hide the weekday multi select, for a caller that reads the
     *                       stretch between one occurrence and the next as a length and so needs
     *                       every stretch to be the same.
     */
    public static RecurrencePicker createPicker(FragmentManager fragmentManager, String tag, RecurrenceSetting recurrenceSetting, boolean endTypeEnabled, boolean onceAPeriod) {
        RecurrencePicker recurrencePicker = (RecurrencePicker) fragmentManager.findFragmentByTag(tag);
        if (recurrencePicker == null) {
            Bundle arguments = new Bundle();
            arguments.putParcelable(ARG_RECURRENCE_SETTING, recurrenceSetting);
            arguments.putBoolean(ARG_END_TYPE_ENABLED, endTypeEnabled);
            arguments.putBoolean(ARG_ONCE_A_PERIOD, onceAPeriod);
            recurrencePicker = new RecurrencePicker();
            recurrencePicker.setArguments(arguments);
            fragmentManager.beginTransaction().add(recurrencePicker, tag).commit();
        }
        return recurrencePicker;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Controller) {
            mController = (Controller) context;
        } else if (getParentFragment() instanceof Controller) {
            mController = (Controller) getParentFragment();
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        mEndTypeEnabled = arguments == null || arguments.getBoolean(ARG_END_TYPE_ENABLED, true);
        mOnceAPeriod = arguments != null && arguments.getBoolean(ARG_ONCE_A_PERIOD, false);
        if (savedInstanceState != null) {
            mRecurrenceSetting = savedInstanceState.getParcelable(SS_RECURRENCE_SETTING);
            mChosen = savedInstanceState.getBoolean(SS_CHOSEN, false);
        } else {
            if (arguments != null && arguments.containsKey(ARG_RECURRENCE_SETTING)) {
                mRecurrenceSetting = arguments.getParcelable(ARG_RECURRENCE_SETTING);
            } else {
                throw new IllegalStateException("RecurrencePicker not initialized correctly. Please use RecurrencePicker.createPicker(...) instead.");
            }
        }
        mRecurrencePickerDialog = (RecurrencePickerDialog) getChildFragmentManager().findFragmentByTag(getDialogTag());
        if (mRecurrencePickerDialog == null) {
            mRecurrencePickerDialog = RecurrencePickerDialog.newInstance();
        }
        mRecurrencePickerDialog.setCallback(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fireCallbackSafely();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(SS_RECURRENCE_SETTING, mRecurrenceSetting);
        outState.putBoolean(SS_CHOSEN, mChosen);
    }

    /**
     * Whether the schedule the picker holds was chosen in its dialog, as opposed to the one it
     * was created with. A caller that would otherwise anchor the schedule somewhere of its own
     * asks this first, so that a choice already made is not quietly undone.
     */
    public boolean isChosen() {
        return mChosen;
    }

    private void fireCallbackSafely() {
        if (mController != null) {
            mController.onRecurrenceSettingChanged(getTag(), mRecurrenceSetting);
        }
    }

    public RecurrenceSetting getCurrentSettings() {
        return mRecurrenceSetting;
    }

    /**
     * Replace the schedule the picker holds, as if it had been chosen in the dialog.
     */
    public void setCurrentSettings(RecurrenceSetting recurrenceSetting) {
        // this is the caller putting a schedule in, not a person choosing one, so it leaves the
        // picker as unchosen: a caller that asks whether a schedule was chosen is asking about
        // the dialog, and answering yes here would stop it seeding the picker ever again
        mRecurrenceSetting = recurrenceSetting;
        fireCallbackSafely();
    }

    private String getDialogTag() {
        return getTag() + "::DialogFragment";
    }

    public void showPicker() {
        mRecurrencePickerDialog.showPicker(getChildFragmentManager(), getDialogTag(), mRecurrenceSetting, mEndTypeEnabled, mOnceAPeriod);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mController = null;
    }

    @Override
    public void onRecurrenceSettingChanged(RecurrenceSetting recurrenceSetting) {
        mRecurrenceSetting = recurrenceSetting;
        mChosen = true;
        fireCallbackSafely();
    }

    public interface Controller {

        void onRecurrenceSettingChanged(String tag, RecurrenceSetting recurrenceSetting);
    }
}