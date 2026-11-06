package com.oriondev.moneywallet.picker

import android.content.Context
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.test.core.app.ApplicationProvider

import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

import java.util.ArrayList
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
class DateTimePickerTest {

    companion object {

        private const val TAG = "DateTimePickerTest::Picker"
        private const val PARENT_TAG = "DateTimePickerTest::Parent"
        private const val DATE_PICKER_TAG = "$TAG::DatePicker"
        private const val TIME_PICKER_TAG = "$TAG::TimePicker"
    }

    private val systemTimeZone = TimeZone.getDefault()

    @Before
    fun moveWestOfUtc() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"))
    }

    @After
    fun restoreSystemTimeZone() {
        TimeZone.setDefault(systemTimeZone)
    }

    @Test
    fun aConfirmedDateWestOfUtcLandsOnTheSelectedLocalDayAndKeepsTheTimeOfDay() {
        withPicker(localDate(2026, Calendar.JUNE, 17, 15, 40)) { _, picker ->
            picker.showDatePicker()
            settle(picker)
            val datePicker = datePickerOf(picker)
            assertEquals(utcMidnight(2026, Calendar.JUNE, 17), datePicker.selection)
            picker.setCurrentDateTime(localDate(2026, Calendar.JUNE, 10, 9, 20))
            confirm(datePicker)
            assertEquals(localDate(2026, Calendar.JUNE, 17, 9, 20), picker.getCurrentDateTime())
        }
    }

    @Test
    fun theDateDialogOpensOnUtcMidnightOfTheLocalDayWhateverTheTimeOfDay() {
        withPicker(localDate(2026, Calendar.JUNE, 10, 21, 45)) { _, picker ->
            picker.showDatePicker()
            settle(picker)
            assertEquals(utcMidnight(2026, Calendar.JUNE, 10), datePickerOf(picker).selection)
            assertEquals(localDate(2026, Calendar.JUNE, 10, 21, 45), picker.getCurrentDateTime())
        }
    }

    @Test
    fun anOpenDateDialogWhoseStateCrossedAParcelIsRebuiltFromThePickerDate() {
        val restored = restoreWithOpenDateDialog { saved -> parcelled(saved) }
        try {
            val picker = pickerOf(restored)
            val datePicker = datePickerOf(picker)
            assertEquals(utcMidnight(2026, Calendar.JUNE, 24), datePicker.selection)
            confirm(datePicker)
            assertEquals(localDate(2026, Calendar.JUNE, 24, 14, 35), picker.getCurrentDateTime())
        } finally {
            restored.close()
        }
    }

    @Test
    fun anOpenDateDialogRestoredInProcessIsLeftInPlaceAndStillConfirms() {
        val restored = restoreWithOpenDateDialog { saved -> saved }
        try {
            val picker = pickerOf(restored)
            val datePicker = datePickerOf(picker)
            assertEquals(utcMidnight(2026, Calendar.JUNE, 10), datePicker.selection)
            confirm(datePicker)
            assertEquals(localDate(2026, Calendar.JUNE, 10, 14, 35), picker.getCurrentDateTime())
        } finally {
            restored.close()
        }
    }

    @Test
    fun aPickerOnAParentFragmentReportsToThatFragmentAndNotToTheActivity() {
        val controller: ActivityController<ControllerActivity> =
                Robolectric.buildActivity(ControllerActivity::class.java).setup()
        try {
            val activity = controller.get()
            val parent = ControllerFragment()
            activity.supportFragmentManager.beginTransaction().add(parent, PARENT_TAG).commitNow()
            val picker = DateTimePicker.createPicker(parent.childFragmentManager, TAG,
                    localDate(2026, Calendar.JUNE, 10, 14, 35))
            parent.childFragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            parent.heard.clear()
            val chosen = localDate(2026, Calendar.JULY, 8, 16, 5)
            picker.setCurrentDateTime(chosen)
            assertEquals(listOf<Date?>(chosen), parent.heard)
            assertTrue(activity.heard.isEmpty())
        } finally {
            controller.close()
        }
    }

    @Test
    fun aConfirmedTimeTakesBothTheHourAndTheMinuteAndKeepsTheDate() {
        withPicker(localDate(2026, Calendar.JUNE, 10, 14, 35)) { _, picker ->
            picker.showTimePicker()
            settle(picker)
            val timePicker = picker.childFragmentManager.findFragmentByTag(TIME_PICKER_TAG) as MaterialTimePicker
            assertEquals(14, timePicker.hour)
            assertEquals(35, timePicker.minute)
            timePicker.hour = 19
            timePicker.minute = 8
            timePicker.requireDialog()
                    .findViewById<View>(com.google.android.material.R.id.material_timepicker_ok_button)
                    .performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(localDate(2026, Calendar.JUNE, 10, 19, 8), picker.getCurrentDateTime())
        }
    }

    private fun restoreWithOpenDateDialog(handBack: (Bundle) -> Bundle): ActivityController<AppCompatActivity> {
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val saved = Bundle()
        try {
            val fragmentManager = controller.get().supportFragmentManager
            val picker = DateTimePicker.createPicker(fragmentManager, TAG,
                    localDate(2026, Calendar.JUNE, 10, 14, 35))
            fragmentManager.executePendingTransactions()
            picker.showDatePicker()
            settle(picker)
            assertEquals(utcMidnight(2026, Calendar.JUNE, 10), datePickerOf(picker).selection)
            picker.setCurrentDateTime(localDate(2026, Calendar.JUNE, 24, 14, 35))
            controller.pause().stop().saveInstanceState(saved)
        } finally {
            controller.destroy()
        }
        val restored: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup(handBack(saved))
        shadowOf(Looper.getMainLooper()).idle()
        return restored
    }

    private fun parcelled(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return parcel.readBundle(ApplicationProvider.getApplicationContext<Context>().classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    private fun withPicker(start: Date, body: (ActivityController<AppCompatActivity>, DateTimePicker) -> Unit) {
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val fragmentManager = controller.get().supportFragmentManager
            val picker = DateTimePicker.createPicker(fragmentManager, TAG, start)
            fragmentManager.executePendingTransactions()
            assertEquals(start, picker.getCurrentDateTime())
            body(controller, picker)
        } finally {
            controller.close()
        }
    }

    private fun pickerOf(controller: ActivityController<AppCompatActivity>): DateTimePicker {
        return controller.get().supportFragmentManager.findFragmentByTag(TAG) as DateTimePicker
    }

    private fun datePickerOf(picker: DateTimePicker): MaterialDatePicker<*> {
        return picker.childFragmentManager.findFragmentByTag(DATE_PICKER_TAG) as MaterialDatePicker<*>
    }

    private fun settle(picker: DateTimePicker) {
        picker.childFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun confirm(datePicker: MaterialDatePicker<*>) {
        datePicker.requireDialog()
                .findViewById<View>(com.google.android.material.R.id.confirm_button)
                .performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun localDate(year: Int, month: Int, day: Int, hour: Int, minute: Int): Date {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(year, month, day, hour, minute)
        return calendar.time
    }

    private fun utcMidnight(year: Int, month: Int, day: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month, day)
        return calendar.timeInMillis
    }

    class ControllerActivity : AppCompatActivity(), DateTimePicker.Controller {

        val heard = ArrayList<Date?>()

        override fun onDateTimeChanged(tag: String?, date: Date?) {
            heard.add(date)
        }
    }

    class ControllerFragment : Fragment(), DateTimePicker.Controller {

        val heard = ArrayList<Date?>()

        override fun onDateTimeChanged(tag: String?, date: Date?) {
            heard.add(date)
        }
    }
}
