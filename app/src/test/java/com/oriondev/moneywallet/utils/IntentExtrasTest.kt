package com.oriondev.moneywallet.utils

import android.content.ContentResolver

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@RunWith(RobolectricTestRunner::class)
class IntentExtrasTest {

    @Test
    fun aLookupFindsNothingWhenTheResolverReturnsNoCursor() {
        val resolver = mock(ContentResolver::class.java)
        `when`(resolver.query(any(), any(), anyString(), any(), any())).thenReturn(null)
        assertNull(IntentExtras.findWalletId(resolver, "Cash"))
        assertNull(IntentExtras.findCategoryId(resolver, "Salary"))
    }

    @Test
    fun anAmountIsRoundedToTheNearestMinorUnitWithoutItsSign() {
        assertEquals(1235L, IntentExtras.readAmount("12.346", 2))
        assertEquals(1235L, IntentExtras.readAmount("(12.346)", 2))
        assertEquals(1200L, IntentExtras.readAmount(12, 2))
    }

    @Test
    fun aNumberWhoseTextHasAnExponentIsReadAsItsValue() {
        assertEquals(1500000050L, IntentExtras.readAmount(15000000.5, 2))
        assertEquals(1500000000L, IntentExtras.readAmount(15000000f, 2))
        assertEquals(150000000000L, IntentExtras.readAmount(1500000000L, 2))
        assertEquals(1L, IntentExtras.readAmount(0.00075, 3))
    }

    @Test
    fun aStringAmountIsTrimmed() {
        assertEquals(1250L, IntentExtras.readAmount(" 12.50\n", 2))
    }

    @Test
    fun anAmountThatDoesNotReadIsZero() {
        assertEquals(0L, IntentExtras.readAmount(Double.NaN, 2))
        assertEquals(0L, IntentExtras.readAmount(true, 2))
        assertEquals(0L, IntentExtras.readAmount("99999999999999999999", 2))
    }

    @Test
    fun aNumberIsReadFromItsPrintedValue() {
        assertEquals(268L, IntentExtras.readAmount(2.675f, 2))
        assertEquals(9007199254740993L, IntentExtras.readAmount(9007199254740993L, 0))
    }

    @Test
    fun aNumberTooLargeToHoldIsZeroWithoutRoundingIt() {
        assertZeroWithinASecond(BigDecimal("1E+20000000"))
    }

    @Test
    fun aNumberTooSmallToReachAMinorUnitIsZeroWithoutRoundingIt() {
        assertZeroWithinASecond(BigDecimal("1E-20000000"))
    }

    @Test
    fun aStringLongerThanFortyCharactersIsZeroWhateverItsValue() {
        assertZeroWithinASecond(String(CharArray(996)).replace('\u0000', '0') + "1250")
    }

    @Test
    fun aStringOfFortyOneCharactersIsZero() {
        assertEquals(0L, IntentExtras.readAmount(String(CharArray(36)).replace('\u0000', '0') + "12.50", 2))
    }

    @Test
    fun aStringOfFortyCharactersReads() {
        assertEquals(1250L, IntentExtras.readAmount(String(CharArray(35)).replace('\u0000', '0') + "12.50", 2))
    }

    @Test
    fun theLargestLongReadsAtZeroDecimals() {
        assertEquals(Long.MAX_VALUE, IntentExtras.readAmount(Long.MAX_VALUE, 0))
    }

    @Test
    fun aBigNumberThatFitsStillReads() {
        assertEquals(10000L, IntentExtras.readAmount(BigDecimal("99." + String(CharArray(37)).replace('\u0000', '9')), 2))
        assertEquals(Long.MAX_VALUE, IntentExtras.readAmount(BigInteger.valueOf(Long.MAX_VALUE), 0))
    }

    @Test
    fun aMegabyteNumberIsZeroWithoutPrintingIt() {
        val huge = BigInteger.ONE.shiftLeft(8 shl 20)
        assertZeroWithinASecond(huge)
        assertZeroWithinASecond(BigDecimal(huge, 2))
    }

    @Test
    fun everyTypeOnTheListReads() {
        assertEquals(1200L, IntentExtras.readAmount(12.toShort(), 2))
        assertEquals(1200L, IntentExtras.readAmount(12.toByte(), 2))
        assertEquals(1250L, IntentExtras.readAmount(StringBuilder("12.50"), 2))
    }

    @Test
    fun anExtraOfAnyOtherTypeIsZeroEvenWhenItPrintsAnAmount() {
        assertEquals(0L, IntentExtras.readAmount(object : Any() {
            override fun toString(): String {
                throw AssertionError()
            }
        }, 2))
        assertEquals(0L, IntentExtras.readAmount(AtomicInteger(12), 2))
        assertEquals(0L, IntentExtras.readAmount('5', 2))
    }

    private fun assertZeroWithinASecond(extra: Any) {
        val start = System.nanoTime()
        assertEquals(0L, IntentExtras.readAmount(extra, 2))
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1))
    }
}
