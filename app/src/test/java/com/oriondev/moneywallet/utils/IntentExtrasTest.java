package com.oriondev.moneywallet.utils;

import android.content.ContentResolver;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(RobolectricTestRunner.class)
public class IntentExtrasTest {

    @Test
    public void aLookupFindsNothingWhenTheResolverReturnsNoCursor() {
        ContentResolver resolver = mock(ContentResolver.class);
        when(resolver.query(any(), any(), anyString(), any(), any())).thenReturn(null);
        assertNull(IntentExtras.findWalletId(resolver, "Cash"));
        assertNull(IntentExtras.findCategoryId(resolver, "Salary"));
    }

    @Test
    public void anAmountIsRoundedToTheNearestMinorUnitWithoutItsSign() {
        assertEquals(1235L, IntentExtras.readAmount("12.346", 2));
        assertEquals(1235L, IntentExtras.readAmount("(12.346)", 2));
        assertEquals(1200L, IntentExtras.readAmount(12, 2));
    }

    @Test
    public void aNumberWhoseTextHasAnExponentIsReadAsItsValue() {
        assertEquals(1500000050L, IntentExtras.readAmount(15000000.5d, 2));
        assertEquals(1500000000L, IntentExtras.readAmount(15000000f, 2));
        assertEquals(150000000000L, IntentExtras.readAmount(1500000000L, 2));
        assertEquals(1L, IntentExtras.readAmount(0.00075d, 3));
    }

    @Test
    public void aStringAmountIsTrimmed() {
        assertEquals(1250L, IntentExtras.readAmount(" 12.50\n", 2));
    }

    @Test
    public void anAmountThatDoesNotReadIsZero() {
        assertEquals(0L, IntentExtras.readAmount(Double.NaN, 2));
        assertEquals(0L, IntentExtras.readAmount(Boolean.TRUE, 2));
        assertEquals(0L, IntentExtras.readAmount("99999999999999999999", 2));
    }

    @Test
    public void aNumberIsReadFromItsPrintedValue() {
        assertEquals(268L, IntentExtras.readAmount(2.675f, 2));
        assertEquals(9007199254740993L, IntentExtras.readAmount(9007199254740993L, 0));
    }

    @Test
    public void aNumberTooLargeToHoldIsZeroWithoutRoundingIt() {
        assertZeroWithinASecond(new BigDecimal("1E+20000000"));
    }

    @Test
    public void aNumberTooSmallToReachAMinorUnitIsZeroWithoutRoundingIt() {
        assertZeroWithinASecond(new BigDecimal("1E-20000000"));
    }

    @Test
    public void aStringLongerThanFortyCharactersIsZeroWhateverItsValue() {
        assertZeroWithinASecond(new String(new char[996]).replace('\0', '0') + "1250");
    }

    @Test
    public void aStringOfFortyOneCharactersIsZero() {
        assertEquals(0L, IntentExtras.readAmount(new String(new char[36]).replace('\0', '0') + "12.50", 2));
    }

    @Test
    public void aStringOfFortyCharactersReads() {
        assertEquals(1250L, IntentExtras.readAmount(new String(new char[35]).replace('\0', '0') + "12.50", 2));
    }

    @Test
    public void theLargestLongReadsAtZeroDecimals() {
        assertEquals(Long.MAX_VALUE, IntentExtras.readAmount(Long.MAX_VALUE, 0));
    }

    @Test
    public void aBigNumberThatFitsStillReads() {
        assertEquals(10000L, IntentExtras.readAmount(new BigDecimal("99." + new String(new char[37]).replace('\0', '9')), 2));
        assertEquals(Long.MAX_VALUE, IntentExtras.readAmount(BigInteger.valueOf(Long.MAX_VALUE), 0));
    }

    @Test
    public void aMegabyteNumberIsZeroWithoutPrintingIt() {
        BigInteger huge = BigInteger.ONE.shiftLeft(8 << 20);
        assertZeroWithinASecond(huge);
        assertZeroWithinASecond(new BigDecimal(huge, 2));
    }

    @Test
    public void everyTypeOnTheListReads() {
        assertEquals(1200L, IntentExtras.readAmount((short) 12, 2));
        assertEquals(1200L, IntentExtras.readAmount((byte) 12, 2));
        assertEquals(1250L, IntentExtras.readAmount(new StringBuilder("12.50"), 2));
    }

    @Test
    public void anExtraOfAnyOtherTypeIsZeroEvenWhenItPrintsAnAmount() {
        assertEquals(0L, IntentExtras.readAmount(new Object() {
            @Override
            public String toString() {
                throw new AssertionError();
            }
        }, 2));
        assertEquals(0L, IntentExtras.readAmount(new AtomicInteger(12), 2));
        assertEquals(0L, IntentExtras.readAmount('5', 2));
    }

    private static void assertZeroWithinASecond(Object extra) {
        long start = System.nanoTime();
        assertEquals(0L, IntentExtras.readAmount(extra, 2));
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1));
    }
}
