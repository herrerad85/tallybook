package com.oriondev.moneywallet.picker

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

import com.oriondev.moneywallet.ui.activity.CalculatorActivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class MoneyPickerTest {

    companion object {

        private const val TAG = "MoneyPickerTest::Picker"
    }

    @Test
    fun aPickerAskedForAgainWithNegativesAllowedStartsTheCalculatorAllowingThem() {
        assertTrue(calculatorAllowsNegative(false, true))
    }

    @Test
    fun aPickerAskedForAgainWithNegativesRefusedStartsTheCalculatorRefusingThem() {
        assertFalse(calculatorAllowsNegative(true, false))
    }

    private fun calculatorAllowsNegative(created: Boolean, askedAgain: Boolean): Boolean {
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val fragmentManager = controller.get().supportFragmentManager
            val first = MoneyPicker.createPicker(fragmentManager, TAG, null, 1250L, created)
            fragmentManager.executePendingTransactions()
            val again = MoneyPicker.createPicker(fragmentManager, TAG, null, 1250L, askedAgain)
            assertSame(first, again)
            again.showPicker()
            val started: Intent? = shadowOf(controller.get()).getNextStartedActivity()
            assertNotNull(started)
            assertEquals(CalculatorActivity::class.java.name, started!!.component!!.className)
            assertEquals(1250L, started.getLongExtra(CalculatorActivity.MONEY, 0L))
            assertTrue(started.hasExtra(CalculatorActivity.ALLOW_NEGATIVE))
            return started.getBooleanExtra(CalculatorActivity.ALLOW_NEGATIVE, !askedAgain)
        } finally {
            controller.close()
        }
    }
}
