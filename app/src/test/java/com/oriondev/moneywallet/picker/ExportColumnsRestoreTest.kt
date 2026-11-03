package com.oriondev.moneywallet.picker

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.ui.fragment.dialog.ExportColumnsDialogFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class ExportColumnsRestoreTest {

    @Before
    fun setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun tickAddedBeforeInProcessRecreationIsDeliveredByRestoredDialog() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = preselectEvent(first)
        picker.showPicker()
        setRow(openDialog(picker), NOTE, true)

        val second = recreate(first, throughParcel = false)
        try {
            val restored = exportColumnsPicker(second)
            press(openDialog(restored), DialogInterface.BUTTON_POSITIVE)
            assertEquals(listOf(AbstractDataExporter.COLUMN_EVENT, AbstractDataExporter.COLUMN_NOTE), columns(restored))
        } finally {
            second.close()
        }
    }

    @Test
    fun tickAddedBeforeProcessDeathIsDeliveredByRestoredDialog() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = preselectEvent(first)
        picker.showPicker()
        setRow(openDialog(picker), NOTE, true)

        val second = recreate(first, throughParcel = true)
        try {
            val restored = exportColumnsPicker(second)
            press(openDialog(restored), DialogInterface.BUTTON_POSITIVE)
            assertEquals(listOf(AbstractDataExporter.COLUMN_EVENT, AbstractDataExporter.COLUMN_NOTE), columns(restored))
        } finally {
            second.close()
        }
    }

    @Test
    fun untickBeforeProcessDeathIsDeliveredByRestoredDialogAsEmptySelection() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = preselectEvent(first)
        picker.showPicker()
        setRow(openDialog(picker), EVENT, false)

        val second = recreate(first, throughParcel = true)
        try {
            val restored = exportColumnsPicker(second)
            press(openDialog(restored), DialogInterface.BUTTON_POSITIVE)
            assertEquals(emptyList<String>(), columns(restored))
        } finally {
            second.close()
        }
    }

    @Test
    fun cancelledTickIsNotReusedWhenTheSameDialogOpensAgain() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val picker = preselectEvent(controller)
            picker.showPicker()
            val cancelled = openDialog(picker)
            setRow(cancelled, NOTE, true)
            press(cancelled, DialogInterface.BUTTON_NEGATIVE)
            assertEquals(listOf(AbstractDataExporter.COLUMN_EVENT), columns(picker))

            picker.showPicker()
            press(openDialog(picker), DialogInterface.BUTTON_POSITIVE)
            assertEquals(listOf(AbstractDataExporter.COLUMN_EVENT), columns(picker))
        } finally {
            controller.close()
        }
    }

    @Test
    fun tickWithoutRecreationIsDeliveredOnOk() {
        val controller = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val picker = ExportColumnsPicker.createPicker(controller.get().supportFragmentManager, PICKER_TAG)
            controller.get().supportFragmentManager.executePendingTransactions()
            assertEquals(emptyList<String>(), columns(picker))
            picker.showPicker()
            val dialog = openDialog(picker)
            setRow(dialog, PEOPLE, true)
            press(dialog, DialogInterface.BUTTON_POSITIVE)
            assertEquals(listOf(AbstractDataExporter.COLUMN_PEOPLE), columns(picker))
        } finally {
            controller.close()
        }
    }

    private fun preselectEvent(controller: ActivityController<AppCompatActivity>): ExportColumnsPicker {
        val picker = ExportColumnsPicker.createPicker(controller.get().supportFragmentManager, PICKER_TAG)
        controller.get().supportFragmentManager.executePendingTransactions()
        picker.showPicker()
        val dialog = openDialog(picker)
        setRow(dialog, EVENT, true)
        press(dialog, DialogInterface.BUTTON_POSITIVE)
        assertEquals(listOf(AbstractDataExporter.COLUMN_EVENT), columns(picker))
        return picker
    }

    private fun openDialog(picker: ExportColumnsPicker): AlertDialog {
        picker.getChildFragmentManager().executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()
        val fragment = picker.getChildFragmentManager().findFragmentByTag(DIALOG_TAG) as ExportColumnsDialogFragment
        val dialog = fragment.getDialog() as AlertDialog
        assertNotNull(dialog)
        return dialog
    }

    private fun setRow(dialog: AlertDialog, position: Int, checked: Boolean) {
        val listView = dialog.listView
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(!checked, listView.isItemChecked(position))
        val row = listView.getChildAt(position - listView.firstVisiblePosition)
        assertNotNull(row)
        listView.performItemClick(row, position, listView.adapter.getItemId(position))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(checked, listView.isItemChecked(position))
    }

    private fun press(dialog: AlertDialog, which: Int) {
        dialog.getButton(which).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun columns(picker: ExportColumnsPicker): List<String?>? =
        picker.getCurrentServiceColumns()?.toList()

    private fun recreate(
        first: ActivityController<AppCompatActivity>,
        throughParcel: Boolean
    ): ActivityController<AppCompatActivity> {
        val outState = Bundle()
        first.pause().stop().saveInstanceState(outState).destroy()
        val savedState = if (throughParcel) parceled(outState) else outState
        return Robolectric.buildActivity(AppCompatActivity::class.java).setup(savedState)
    }

    private fun parceled(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return parcel.readBundle(ApplicationProvider.getApplicationContext<Context>().classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    private fun exportColumnsPicker(controller: ActivityController<AppCompatActivity>): ExportColumnsPicker =
        controller.get().supportFragmentManager.findFragmentByTag(PICKER_TAG) as ExportColumnsPicker

    private companion object {
        const val EVENT = 0
        const val PEOPLE = 1
        const val NOTE = 3
        const val PICKER_TAG = "ExportColumnsRestoreTest::ExportColumnsPicker"
        const val DIALOG_TAG = "$PICKER_TAG::DialogFragment"
    }
}
