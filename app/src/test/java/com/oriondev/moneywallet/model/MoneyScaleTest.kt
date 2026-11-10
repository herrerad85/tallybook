package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

import org.junit.Test

import java.math.BigDecimal

/**
 * Pure JVM tests for the canonical money scaling arithmetic. No Android
 * dependencies are needed because [MoneyScale] is plain Java.
 */
class MoneyScaleTest {

    // long minor units -> human amount -> long minor units must round trip exactly

    @Test
    fun roundTrip_zeroDecimalCurrency() {
        assertRoundTrip(0L, 0)
        assertRoundTrip(64L, 0)
        assertRoundTrip(-64L, 0)
        assertRoundTrip(123456789L, 0)
    }

    @Test
    fun roundTrip_twoDecimalCurrency() {
        assertRoundTrip(0L, 2)
        assertRoundTrip(6499L, 2)
        assertRoundTrip(-6499L, 2)
        assertRoundTrip(100L, 2)
        assertRoundTrip(-1L, 2)
    }

    @Test
    fun roundTrip_threeDecimalCurrency() {
        assertRoundTrip(0L, 3)
        assertRoundTrip(64999L, 3)
        assertRoundTrip(-64999L, 3)
        assertRoundTrip(1L, 3)
    }

    @Test
    fun roundTrip_largeValueNearDoublePrecisionEdge() {
        // 2^53 is the last integer a double can represent exactly. Values just
        // beyond it are what the old "money / Math.pow(10, d)" display path and
        // the old "(long)(money * Math.pow(10, offset))" migration path corrupt.
        val edge = 9007199254740993L // 2^53 + 1, not representable as a double
        assertRoundTrip(edge, 0)
        assertRoundTrip(edge, 2)
        assertRoundTrip(-edge, 3)
        assertRoundTrip(Long.MAX_VALUE, 0)
    }

    @Test
    fun toHumanAmount_producesExactDecimalString() {
        assertEquals("64.99", MoneyScale.toHumanAmount(6499L, 2).toPlainString())
        assertEquals("-64.99", MoneyScale.toHumanAmount(-6499L, 2).toPlainString())
        assertEquals("0.001", MoneyScale.toHumanAmount(1L, 3).toPlainString())
        assertEquals("64", MoneyScale.toHumanAmount(64L, 0).toPlainString())
        // the old double path would have rendered this as a lossy 9.007199254740992E15
        assertEquals("9007199254740993", MoneyScale.toHumanAmount(9007199254740993L, 0).toPlainString())
    }

    // human amount -> long minor units truncates toward zero (the policy every
    // call site relied on, and the one EquationSolverTest pins)

    @Test
    fun toMinorUnits_truncatesExtraFractionTowardZero() {
        assertEquals(6499L, MoneyScale.toMinorUnits(BigDecimal("64.999"), 2))
        assertEquals(-6499L, MoneyScale.toMinorUnits(BigDecimal("-64.999"), 2))
        assertEquals(6490L, MoneyScale.toMinorUnits(BigDecimal("64.9"), 2))
        assertEquals(64L, MoneyScale.toMinorUnits(BigDecimal("64.9"), 0))
        assertEquals(-64L, MoneyScale.toMinorUnits(BigDecimal("-64.9"), 0))
    }

    @Test
    fun toMinorUnits_nullAmountIsZero() {
        assertEquals(0L, MoneyScale.toMinorUnits(null, 2))
    }

    // human amount -> long minor units rounded to the nearest, half to even, which is what an
    // amount arriving from outside the app gets

    @Test
    fun toMinorUnitsRounded_roundsToTheNearestMinorUnit() {
        assertEquals(1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.019"), 2))
        assertEquals(1001L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.011"), 2))
        assertEquals(-1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("-10.019"), 2))
        assertEquals(65L, MoneyScale.toMinorUnitsRounded(BigDecimal("64.9"), 0))
    }

    @Test
    fun toMinorUnitsRounded_sendsAHalfToTheEvenNeighbour() {
        // this one only says the half is not dropped: rounding every half up agrees with it
        assertEquals(1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.015"), 2))
        // these two are what tell this apart from rounding every half up, which would
        // send them to 1003 and -1003
        assertEquals(1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.025"), 2))
        assertEquals(-1002L, MoneyScale.toMinorUnitsRounded(BigDecimal("-10.025"), 2))
    }

    @Test
    fun toMinorUnitsRounded_usesTheDecimalsItIsGiven() {
        assertEquals(10016L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.0155"), 3))
        // both halves go to the even neighbour, so one comes down and the other goes up
        assertEquals(64L, MoneyScale.toMinorUnitsRounded(BigDecimal("64.5"), 0))
        assertEquals(66L, MoneyScale.toMinorUnitsRounded(BigDecimal("65.5"), 0))
    }

    @Test(expected = ArithmeticException::class)
    fun toMinorUnitsRounded_refusesAnAmountTooLargeToHold() {
        // rounding carries this over the edge of a long, where truncating left it just inside
        MoneyScale.toMinorUnitsRounded(BigDecimal("92233720368547758.075"), 2)
    }

    @Test
    fun toMinorUnitsRounded_leavesAnAmountTheCurrencyHoldsAlone() {
        assertEquals(1001L, MoneyScale.toMinorUnitsRounded(BigDecimal("10.01"), 2))
        assertEquals(0L, MoneyScale.toMinorUnitsRounded(BigDecimal.ZERO, 2))
        assertEquals(1234567L, MoneyScale.toMinorUnitsRounded(BigDecimal("12345.67"), 2))
    }

    @Test
    fun toMinorUnitsRounded_nullAmountIsZero() {
        assertEquals(0L, MoneyScale.toMinorUnitsRounded(null, 2))
    }

    // exchange rate conversion across differing decimal counts

    @Test
    fun convert_zeroDecimalToTwoDecimal() {
        // 100000 JPY at 0.0068 USD per JPY = 680.00 USD = 68000 cents
        assertEquals(68000L, MoneyScale.convert(100000L, 0, 2, 0.0068))
    }

    @Test
    fun convert_twoDecimalToZeroDecimal() {
        // 68000 cents (680.00 USD) at 147 JPY per USD = 99960 JPY
        assertEquals(99960L, MoneyScale.convert(68000L, 2, 0, 147.0))
    }

    @Test
    fun convert_appliesRateExactlyOnce() {
        // 5.00 USD doubled must be 10.00 USD (1000 cents), not 5.00 and not 20.00
        assertEquals(1000L, MoneyScale.convert(500L, 2, 2, 2.0))
    }

    @Test
    fun convert_sameCurrencyUnitRateIsIdentity() {
        assertEquals(1234L, MoneyScale.convert(1234L, 2, 2, 1.0))
        assertEquals(0L, MoneyScale.convert(0L, 3, 3, 1.0))
    }

    // rescale (used by MoneyFormatter.normalize, which feeds the FIX_MONEY_DECIMALS
    // database migration and the legacy database import)

    @Test
    fun rescale_matchesOldDoublePathWhereDoubleWasExact() {
        // widening the scale (offset > 0) is exact integer growth
        assertEquals(6490L, MoneyScale.rescale(649L, 1))
        assertEquals(64900L, MoneyScale.rescale(649L, 2))
        // narrowing the scale (offset < 0) truncates toward zero
        assertEquals(64L, MoneyScale.rescale(649L, -1))
        assertEquals(-64L, MoneyScale.rescale(-649L, -1))
        assertEquals(0L, MoneyScale.rescale(1234L, 0) - 1234L)
    }

    @Test
    fun rescale_isExactWhereOldDoublePathCorrupted() {
        // The migration used (long)(money * Math.pow(10, offset)). With offset 0
        // that is (long)(money * 1.0), which still routes money through a double
        // and loses the low bit past 2^53. The exact path must not.
        val money = 9007199254740993L // 2^53 + 1
        val oldDoublePath = (money * Math.pow(10.0, 0.0)).toLong()
        assertEquals(9007199254740992L, oldDoublePath) // the pre-existing corruption
        assertEquals(money, MoneyScale.rescale(money, 0)) // exact
        assertNotEquals(oldDoublePath, MoneyScale.rescale(money, 0))
    }

    // regression: the CSV importer used new BigDecimal(Math.pow(10, decimals))
    // as the scaling multiplier. That is the classic double-constructor pattern.

    @Test
    fun csvImporter_canonicalMatchesOldPathForSupportedDecimals() {
        // For the decimals the app actually supports (0..3), Math.pow(10, d) is an
        // exact double, so the fix does not change any stored value. Lock that in.
        val amounts = arrayOf("0", "12345.67", "-0.99", "1000000.5", "70.07")
        for (d in 0..3) {
            for (amount in amounts) {
                val money = BigDecimal(amount)
                val oldPath = money.multiply(BigDecimal(Math.pow(10.0, d.toDouble()))).toLong()
                val canonical = MoneyScale.toMinorUnits(money, d)
                assertEquals("amount=" + amount + " decimals=" + d, oldPath, canonical)
            }
        }
        assertEquals(1234567L, MoneyScale.toMinorUnits(BigDecimal("12345.67"), 2))
    }

    @Test
    fun csvImporter_doubleConstructorMultiplierIsCorruptWhereCanonicalIsExact() {
        // 10^23 is the first power of ten that is not an exact double. new
        // BigDecimal(Math.pow(10, 23)) captures the binary tail and yields
        // 99999999999999991611392 instead of 10^23, so the old multiply would
        // mangle the amount. The canonical movePointRight path is exact.
        val amount = BigDecimal("1")
        val buggyMultiplier = BigDecimal(Math.pow(10.0, 23.0))
        val buggyResult = amount.multiply(buggyMultiplier)
        val canonicalResult = amount.movePointRight(23)
        val exact = BigDecimal.TEN.pow(23)

        assertNotEquals(exact, buggyResult)
        assertTrue(buggyResult.compareTo(BigDecimal("99999999999999991611392")) == 0)
        assertTrue(canonicalResult.compareTo(exact) == 0)
    }

    // regression: the three transfer editors derived the exchange rate on load as
    // (double) moneyTo / moneyFrom. convert() shifts the decimal point between the two
    // currency scales as well as applying the rate, so that ratio carries the shift and
    // saving applied it a second time.

    @Test
    fun deriveRate_thenConvert_leavesTheStoredAmountWhereItWas() {
        // Opening a transfer and saving it again without touching the amounts is the flow
        // that corrupted the destination value: the editor derives a rate from the stored
        // pair on load and converts with it on save.
        val storedPairs = arrayOf(
                longArrayOf(10000L, 15000L), longArrayOf(15000L, 10000L), longArrayOf(6499L, 5832L), longArrayOf(100000L, 92L), longArrayOf(7L, 900000L)
        )
        for (fromDecimals in 0..3) {
            for (toDecimals in 0..3) {
                for (pair in storedPairs) {
                    val from = pair[0]
                    val to = pair[1]
                    val where = ("from=" + from + " to=" + to
                            + " fromDecimals=" + fromDecimals + " toDecimals=" + toDecimals)

                    val rate = MoneyScale.deriveRate(from, to, fromDecimals, toDecimals)
                    val resaved = MoneyScale.convert(from, fromDecimals, toDecimals, rate)
                    // convert truncates toward zero, so a non terminating ratio can cost one
                    // minor unit. That is a property of convert and not of the derivation.
                    assertTrue(where + " moved to " + resaved, Math.abs(resaved - to) <= 1L)

                    if (fromDecimals != toDecimals) {
                        // the old derivation, kept here so this test discriminates: it is wrong
                        // by the whole scale factor between the two currencies, not by one unit
                        val oldRate = to.toDouble() / from
                        val oldResaved = MoneyScale.convert(from, fromDecimals, toDecimals, oldRate)
                        assertTrue(where + " old path happened to be right", Math.abs(oldResaved - to) > 1L)
                    }
                }
            }
        }
    }

    @Test
    fun deriveRate_matchesTheOldRatioWhenBothScalesAgree() {
        // Same decimal count on both sides is the case the bug was invisible in, and the
        // one every same currency transfer takes. It must not move.
        for (decimals in 0..3) {
            assertEquals(5832L.toDouble() / 6499L, MoneyScale.deriveRate(6499L, 5832L, decimals, decimals), 0.0)
        }
    }

    @Test
    fun deriveRate_appliesTheScaleShiftInTheDirectionConvertUndoes() {
        // 100.00 in a two decimal currency stored as 15000 in a zero decimal one is a rate
        // of 150, not the bare ratio of 1.5.
        assertEquals(150.0, MoneyScale.deriveRate(10000L, 15000L, 2, 0), 1e-9)
        assertEquals(1.5, MoneyScale.deriveRate(10000L, 15000L, 2, 2), 1e-9)
        assertEquals(0.015, MoneyScale.deriveRate(10000L, 15000L, 0, 2), 1e-9)
    }

    @Test
    fun deriveRate_zeroSourceAmountYieldsNoRateRatherThanANonFiniteOne() {
        assertEquals(0.0, MoneyScale.deriveRate(0L, 15000L, 2, 0), 0.0)
        assertEquals(0.0, MoneyScale.deriveRate(0L, 0L, 2, 2), 0.0)
    }

    @Test
    fun deriveRate_guardIsLoadBearingBecauseBigDecimalRejectsNonFiniteRates() {
        // Without the zero check the ratio is infinite or NaN, and convert cannot read it.
        assertTrue(java.lang.Double.isInfinite(15000L.toDouble() / 0L))
        assertTrue(java.lang.Double.isNaN(0L.toDouble() / 0L))
        for (nonFinite in doubleArrayOf(Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN)) {
            try {
                BigDecimal.valueOf(nonFinite)
                fail("BigDecimal.valueOf accepted " + nonFinite)
            } catch (expected: NumberFormatException) {
                // this is why deriveRate must not return it
            }
        }
    }

    private fun assertRoundTrip(minorUnits: Long, decimals: Int) {
        val human = MoneyScale.toHumanAmount(minorUnits, decimals)
        assertEquals("round trip minorUnits=" + minorUnits + " decimals=" + decimals,
                minorUnits, MoneyScale.toMinorUnits(human, decimals))
    }
}
