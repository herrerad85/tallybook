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
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import android.view.View;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.datepicker.MaterialPickerOnPositiveButtonClickListener;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.DateFormatter;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by andrea on 07/03/18.
 */

public class DateTimePicker extends Fragment {

    private static final String SS_CURRENT_DATETIME = "DateTimePicker::SavedState::CurrentDateTime";
    private static final String SS_PARCEL_PROBE_FIRST = "DateTimePicker::SavedState::ParcelProbeFirst";
    private static final String SS_PARCEL_PROBE_SECOND = "DateTimePicker::SavedState::ParcelProbeSecond";
    private static final String ARG_DEFAULT_DATETIME = "DateTimePicker::Arguments::DefaultDateTime";

    private Controller mController;

    private Calendar mDateTime;

    private boolean mRebuildDatePicker;

    public static DateTimePicker createPicker(FragmentManager fragmentManager, String tag, Date defaultDate) {
        DateTimePicker dateTimePicker = (DateTimePicker) fragmentManager.findFragmentByTag(tag);
        if (dateTimePicker == null) {
            Bundle arguments = new Bundle();
            if (defaultDate != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(defaultDate);
                arguments.putSerializable(ARG_DEFAULT_DATETIME, calendar);
            }
            dateTimePicker = new DateTimePicker();
            dateTimePicker.setArguments(arguments);
            fragmentManager.beginTransaction().add(dateTimePicker, tag).commit();
        }
        return dateTimePicker;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        // the fragment that created this picker is asked first. A child fragment is attached to
        // the activity, not to its parent, so a dialog that owns date pickers of its own would
        // otherwise lose them to an activity that happens to be a Controller too, and the dialog
        // would never hear the date it asked for.
        if (getParentFragment() instanceof Controller) {
            mController = (Controller) getParentFragment();
        } else if (context instanceof Controller) {
            mController = (Controller) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mDateTime = (Calendar) savedInstanceState.getSerializable(SS_CURRENT_DATETIME);
            mRebuildDatePicker = crossedParcel(savedInstanceState)
                    && getChildFragmentManager().findFragmentByTag(getDatePickerTag()) != null;
        } else {
            Bundle arguments = getArguments();
            if (arguments != null) {
                mDateTime = (Calendar) arguments.getSerializable(ARG_DEFAULT_DATETIME);
            }
        }
        reattachTimePickerListener();
        if (!mRebuildDatePicker) {
            reattachDatePickerListener();
        }
        getChildFragmentManager().registerFragmentLifecycleCallbacks(mPickerOutliner, false);
    }

    /**
     * Outlines a picker in deep dark. It is registered in onCreate so a picker the FragmentManager
     * rebuilds gets it too, and it runs on started because the date picker only sets its window
     * background in its own onStart.
     */
    private final FragmentManager.FragmentLifecycleCallbacks mPickerOutliner = new FragmentManager.FragmentLifecycleCallbacks() {

        @Override
        public void onFragmentStarted(@NonNull FragmentManager fragmentManager, @NonNull Fragment fragment) {
            if (fragment instanceof DialogFragment && ((DialogFragment) fragment).getDialog() != null) {
                ThemedDialog.outlineDeepDark(((DialogFragment) fragment).getDialog());
            }
        }

    };

    /**
     * MaterialTimePicker is a DialogFragment shown by tag. When the host is recreated the
     * FragmentManager rebuilds it, but the listeners live in a plain Set field on the picker and
     * are not part of its saved state, so a picker that came back on screen would do nothing when
     * confirmed. This finds it by the tag it was shown under and adds the listener again. The
     * rebuilt fragment is a new object with an empty listener set, so this cannot add a duplicate.
     * The time picker survives the rebuild intact, because it holds its selection alone in one
     * TimeModel and has no child fragment to disagree with.
     */
    private void reattachTimePickerListener() {
        FragmentManager fragmentManager = getChildFragmentManager();
        MaterialTimePicker timePicker = (MaterialTimePicker) fragmentManager.findFragmentByTag(getTimePickerTag());
        if (timePicker != null) {
            timePicker.addOnPositiveButtonClickListener(timeSetListener(timePicker));
        }
    }

    /**
     * The date picker takes its listener back the same way on the restores where that is enough.
     * MaterialDatePicker keeps the selection in a DateSelector that it saves under its own key,
     * while its child MaterialCalendar saves an equal copy under a second key, and on a restore
     * MaterialDatePicker reuses the calendar the FragmentManager rebuilt instead of seeding it
     * again. A Bundle handed back inside the process leaves both keys pointing at one object, and
     * the restored picker works with nothing more than its listener. Once that Bundle has been
     * through a Parcel the two keys unmarshal into two objects, and then a tapped day lands on
     * the calendar's copy while the header and the OK button both read the picker's. Only that
     * second case is rebuilt, in onStart.
     */
    private void reattachDatePickerListener() {
        FragmentManager fragmentManager = getChildFragmentManager();
        @SuppressWarnings("unchecked")
        MaterialDatePicker<Long> datePicker = (MaterialDatePicker<Long>) fragmentManager.findFragmentByTag(getDatePickerTag());
        if (datePicker != null) {
            datePicker.addOnPositiveButtonClickListener(mDateSetListener);
        }
    }

    /**
     * A picker that came back from a Parcel is closed and a new one opened from mDateTime, which
     * takes the path that seeds both halves from a single object. A day the user tapped but had not
     * confirmed is lost with it, which is why the in process restore above is kept separate.
     *
     * onStart is where the rebuild belongs. Fragment.performStart calls noteStateNotSaved and then
     * execPendingActions on the child FragmentManager before onStart, and dispatchStart after it,
     * so at this point the manager has neither saved its state nor is executing, and the close and
     * the open both land in the queue that dispatchStart drains on its way out.
     */
    @Override
    public void onStart() {
        super.onStart();
        if (mRebuildDatePicker) {
            mRebuildDatePicker = false;
            DialogFragment stale = (DialogFragment) getChildFragmentManager().findFragmentByTag(getDatePickerTag());
            if (stale != null) {
                stale.dismissAllowingStateLoss();
            }
            showDatePicker();
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fireCallbackSafely();
    }

    private void fireCallbackSafely() {
        if (mController != null) {
            mController.onDateTimeChanged(getTag(), getCurrentDateTime());
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SS_CURRENT_DATETIME, mDateTime);
        int[] probe = new int[1];
        outState.putIntArray(SS_PARCEL_PROBE_FIRST, probe);
        outState.putIntArray(SS_PARCEL_PROBE_SECOND, probe);
    }

    /**
     * The two restores are told apart by what a Parcel does to a Bundle, which is the same thing
     * that splits the picker into two DateSelector copies. onSaveInstanceState puts one array
     * under two keys. A Bundle handed back inside the process still holds the ArrayMap it was
     * written with, so both keys return that one array. A Bundle that has come through a Parcel is
     * read back an entry at a time and Parcel.createIntArray allocates for each of them, so the
     * two keys return two arrays.
     *
     * An int array is what is stored because nothing on the read side can hand back a shared
     * instance for one. A String can come back pooled from a Parcel.ReadWriteHelper and a boxed
     * int from the Integer.valueOf cache, and either would report an in process restore for a
     * Bundle that had crossed a Parcel.
     *
     * A Bundle written before these two keys existed has neither, and is read as a crossing. The
     * two mistakes do not cost the same. Rebuilding a picker that did not need it loses a day the
     * user tapped and did not confirm, and the picker still opens on the stored date. Leaving a
     * picker that did cross a Parcel in place leaves the header and the OK button reading a copy
     * the grid never writes, so OK stores a date the user did not choose.
     */
    private static boolean crossedParcel(@NonNull Bundle savedInstanceState) {
        int[] first = savedInstanceState.getIntArray(SS_PARCEL_PROBE_FIRST);
        return first == null || first != savedInstanceState.getIntArray(SS_PARCEL_PROBE_SECOND);
    }

    public boolean isSelected() {
        return mDateTime != null;
    }

    public Date getCurrentDateTime() {
        return mDateTime != null ? mDateTime.getTime() : null;
    }

    public void setCurrentDateTime(Date dateTime) {
        if (dateTime == null) {
            mDateTime = null;
        } else {
            if (mDateTime == null) {
                mDateTime = Calendar.getInstance();
                mDateTime.setTime(dateTime);
            } else {
                mDateTime.setTime(dateTime);
            }
        }
        fireCallbackSafely();
    }

    public void showDatePicker() {
        Calendar calendar = mDateTime != null ? mDateTime : Calendar.getInstance();
        Calendar utc = utcCalendar();
        utc.clear();
        utc.set(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH));
        MaterialDatePicker<Long> datePicker = ThemedDialog.buildDatePickerDialog(utc.getTimeInMillis());
        datePicker.addOnPositiveButtonClickListener(mDateSetListener);
        datePicker.show(getChildFragmentManager(), getDatePickerTag());
    }

    /**
     * MaterialDatePicker carries its selection as milliseconds in UTC with the time of day zeroed,
     * while mDateTime is a Calendar in the default time zone of the device. Both directions read
     * and write calendar fields through a UTC calendar, never through the millisecond value
     * directly, because the same instant falls on the previous day locally west of UTC.
     */
    private static Calendar utcCalendar() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private final MaterialPickerOnPositiveButtonClickListener<Long> mDateSetListener = new MaterialPickerOnPositiveButtonClickListener<Long>() {

        @Override
        public void onPositiveButtonClick(Long selection) {
            Calendar utc = utcCalendar();
            utc.setTimeInMillis(selection);
            if (mDateTime == null) {
                mDateTime = Calendar.getInstance();
            }
            mDateTime.set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH));
            fireCallbackSafely();
        }

    };

    private String getDatePickerTag() {
        return getTag() + "::DatePicker";
    }

    public void showTimePicker() {
        Calendar calendar = mDateTime != null ? mDateTime : Calendar.getInstance();
        // the 12 or 24 hour setting must match how the time is rendered next to this picker, which
        // follows the device setting and not a preference of this app
        MaterialTimePicker timePicker = ThemedDialog.buildTimePickerDialog(DateFormatter.is24HourFormat(),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE));
        timePicker.addOnPositiveButtonClickListener(timeSetListener(timePicker));
        timePicker.show(getChildFragmentManager(), getTimePickerTag());
    }

    /**
     * MaterialTimePicker reports a confirmation through a plain View.OnClickListener and leaves the
     * hour and the minute to be read back off the picker, so the listener has to hold the picker it
     * belongs to. Reading them there is in time, because the OK button walks its positive listeners
     * first and calls dismiss only after the last one.
     */
    private View.OnClickListener timeSetListener(final MaterialTimePicker picker) {
        return new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                if (mDateTime == null) {
                    mDateTime = Calendar.getInstance();
                }
                mDateTime.set(Calendar.HOUR_OF_DAY, picker.getHour());
                mDateTime.set(Calendar.MINUTE, picker.getMinute());
                fireCallbackSafely();
            }

        };
    }

    private String getTimePickerTag() {
        return getTag() + "::TimePicker";
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mController = null;
    }

    public interface Controller {

        void onDateTimeChanged(String tag, Date date);
    }
}