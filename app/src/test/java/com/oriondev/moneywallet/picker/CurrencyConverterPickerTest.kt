package com.oriondev.moneywallet.picker

import android.os.Bundle
import android.os.Parcel
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.model.CurrencyUnit
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.utils.CurrencyManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class CurrencyConverterPickerTest {

    companion object {

        private const val TAG = "CurrencyConverterPickerTest::Picker"
    }

    @Before
    fun setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun aPickerWithNoStoredRateBetweenTwoCurrenciesIsNotReady() {
        val euro = CurrencyManager.getCurrency("EUR")
        val dollar = CurrencyManager.getCurrency("USD")
        assertNull(CurrencyManager.getExchangeRate(euro, dollar))
        withPicker(euro, dollar) { picker ->
            assertEquals(0L, picker.convert(10000L))
            assertFalse(picker.isReady())
        }
    }

    @Test
    fun aRateOfZeroTypedInTheDialogIsNotReady() {
        assertFalse(readyAfterTyping(0.0))
    }

    @Test
    fun aNegativeRateTypedInTheDialogIsNotReady() {
        assertFalse(readyAfterTyping(-1.5))
    }

    @Test
    fun aRateThatIsNotANumberIsNotReady() {
        assertFalse(readyAfterTyping(Double.NaN))
    }

    @Test
    fun anInfiniteRateIsNotReady() {
        assertFalse(readyAfterTyping(Double.POSITIVE_INFINITY))
    }

    @Test
    fun aPositiveRateTypedInTheDialogIsReady() {
        assertTrue(readyAfterTyping(0.92))
    }

    @Test
    fun twoWalletsInTheSameCurrencyAreReadyAtARateOfOne() {
        val euro = CurrencyManager.getCurrency("EUR")
        withPicker(euro, euro) { picker ->
            assertEquals(12345L, picker.convert(12345L))
            assertTrue(picker.isReady())
        }
    }

    @Test
    fun anEqualCurrencyPairWithARateOfZeroIsNotReady() {
        assertFalse(readyAfterTypingOnEqualCurrencies(0.0))
    }

    @Test
    fun anEqualCurrencyPairWithARateThatIsNotANumberIsNotReady() {
        assertFalse(readyAfterTypingOnEqualCurrencies(Double.NaN))
    }

    @Test
    fun movingTheFirstCurrencyOntoTheSecondReloadsARateOfOne() {
        val yen = CurrencyManager.getCurrency("JPY")
        withPicker(CurrencyManager.getCurrency("EUR"), yen) { picker ->
            picker.onExchangeRateChanged(0.5)
            picker.setCurrency1(yen)
            assertEquals(10000L, picker.convert(10000L))
            assertTrue(picker.isReady())
        }
    }

    @Test
    fun settingTheSameFirstCurrencyAgainKeepsTheTypedRate() {
        val euro = CurrencyManager.getCurrency("EUR")
        withPicker(euro, CurrencyManager.getCurrency("JPY")) { picker ->
            picker.onExchangeRateChanged(0.5)
            picker.setCurrency1(CurrencyManager.getCurrency("EUR"))
            assertEquals(50L, picker.convert(10000L))
            assertTrue(picker.isReady())
        }
    }

    @Test
    fun movingTheFirstCurrencyToOneWithNoStoredRateDropsTheTypedRate() {
        val dollar = CurrencyManager.getCurrency("USD")
        val yen = CurrencyManager.getCurrency("JPY")
        assertNull(CurrencyManager.getExchangeRate(dollar, yen))
        withPicker(CurrencyManager.getCurrency("EUR"), yen) { picker ->
            picker.onExchangeRateChanged(0.5)
            picker.setCurrency1(dollar)
            assertFalse(picker.isReady())
        }
    }

    @Test
    fun aTypedRateSurvivesAProcessDeathAndTheSameFirstCurrencyPickedAgain() {
        val saved = Bundle()
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val fragmentManager = controller.get().supportFragmentManager
            val picker = CurrencyConverterPicker.createPicker(fragmentManager, TAG,
                    CurrencyManager.getCurrency("EUR"), CurrencyManager.getCurrency("JPY"), 0.0)
            fragmentManager.executePendingTransactions()
            picker.onExchangeRateChanged(0.5)
            controller.saveInstanceState(saved)
        } finally {
            controller.close()
        }
        val restored: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup(throughAParcel(saved))
        try {
            val picker = restored.get().supportFragmentManager.findFragmentByTag(TAG) as CurrencyConverterPicker
            assertNotSame(CurrencyManager.getCurrency("EUR"), firstCurrencyOf(picker))
            picker.setCurrency1(CurrencyManager.getCurrency("EUR"))
            assertEquals(50L, picker.convert(10000L))
            assertTrue(picker.isReady())
        } finally {
            restored.close()
        }
    }

    @Test
    fun movingTheSecondCurrencyToOneWithNoStoredRateDropsTheTypedRate() {
        val euro = CurrencyManager.getCurrency("EUR")
        val dollar = CurrencyManager.getCurrency("USD")
        assertNull(CurrencyManager.getExchangeRate(euro, dollar))
        withPicker(euro, CurrencyManager.getCurrency("JPY")) { picker ->
            picker.onExchangeRateChanged(150.0)
            picker.setCurrency2(dollar)
            assertFalse(picker.isReady())
        }
    }

    @Test
    fun movingTheSecondCurrencyOntoTheFirstReloadsARateOfOne() {
        val euro = CurrencyManager.getCurrency("EUR")
        withPicker(euro, CurrencyManager.getCurrency("JPY")) { picker ->
            picker.onExchangeRateChanged(0.5)
            picker.setCurrency2(euro)
            assertEquals(10000L, picker.convert(10000L))
            assertTrue(picker.isReady())
        }
    }

    private fun readyAfterTypingOnEqualCurrencies(rate: Double): Boolean {
        var ready = true
        val euro = CurrencyManager.getCurrency("EUR")
        withPicker(euro, euro) { picker ->
            picker.onExchangeRateChanged(rate)
            ready = picker.isReady()
        }
        return ready
    }

    private fun readyAfterTyping(rate: Double): Boolean {
        var ready = false
        withPicker(CurrencyManager.getCurrency("EUR"), CurrencyManager.getCurrency("USD")) { picker ->
            picker.onExchangeRateChanged(rate)
            ready = picker.isReady()
        }
        return ready
    }

    private fun withPicker(currency1: CurrencyUnit?, currency2: CurrencyUnit?, block: (CurrencyConverterPicker) -> Unit) {
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val fragmentManager = controller.get().supportFragmentManager
            val picker = CurrencyConverterPicker.createPicker(fragmentManager, TAG, currency1, currency2, 0.0)
            fragmentManager.executePendingTransactions()
            block(picker)
        } finally {
            controller.close()
        }
    }

    private fun throughAParcel(state: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            state.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val copy = Bundle.CREATOR.createFromParcel(parcel)
            copy.classLoader = javaClass.classLoader
            return copy
        } finally {
            parcel.recycle()
        }
    }

    private fun firstCurrencyOf(picker: CurrencyConverterPicker): Any? {
        val field = CurrencyConverterPicker::class.java.getDeclaredField("mCurrency1")
        field.isAccessible = true
        return field.get(picker)
    }
}
