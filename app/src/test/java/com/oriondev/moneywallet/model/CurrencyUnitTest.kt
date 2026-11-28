package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class CurrencyUnitTest {

    @Test
    fun twoSeparatelyBuiltUnitsWithTheSameFieldsAreEqual() {
        val first = CurrencyUnit("EUR", "Euro", "€", 2)
        val second = CurrencyUnit("EUR", "Euro", "€", 2)
        assertNotSame(first, second)
        assertEquals(first, second)
        assertEquals(second, first)
    }

    @Test
    fun twoUnitsWithTheSameIsoAreEqualWhateverTheirNameSymbolAndDecimals() {
        val first = CurrencyUnit("JPY", "Japanese Yen", "¥", 0)
        val second = CurrencyUnit("JPY", "Yen", "円", 2)
        assertEquals("Japanese Yen", first.name)
        assertEquals("Yen", second.name)
        assertEquals(first, second)
        assertEquals(second, first)
    }

    @Test
    fun twoUnitsWithDifferentIsosAreNotEqualEvenWithEveryOtherFieldTheSame() {
        val first = CurrencyUnit("USD", "Dollar", "$", 2)
        val second = CurrencyUnit("CAD", "Dollar", "$", 2)
        assertEquals("USD", first.iso)
        assertEquals("CAD", second.iso)
        assertNotEquals(first, second)
        assertNotEquals(second, first)
    }

    @Test
    fun aUnitIsNotEqualToNullOrToItsIsoString() {
        val unit = CurrencyUnit("GBP", "Pound", "£", 2)
        assertNotEquals(unit, null)
        assertNotEquals(unit, "GBP")
    }

    @Test
    fun symbolOrIsoIsTheIsoWhenTheSymbolIsNull() {
        val unit = CurrencyUnit("XAF", "CFA Franc BEAC", null, 0)
        assertEquals("XAF", unit.symbolOrIso)
    }

    @Test
    fun symbolOrIsoIsTheIsoWhenTheSymbolIsEmpty() {
        val unit = CurrencyUnit("XOF", "CFA Franc BCEAO", "", 0)
        assertEquals("XOF", unit.symbolOrIso)
    }

    @Test
    fun symbolOrIsoIsTheSymbolWhenOneIsSet() {
        val unit = CurrencyUnit("USD", "US Dollar", "$", 2)
        assertEquals("$", unit.symbolOrIso)
    }

    @Test
    fun aUnitBuiltWithANullSymbolKeepsANullSymbol() {
        val unit = CurrencyUnit("XAF", "CFA Franc BEAC", null, 0)
        assertNull(unit.symbol)
    }

    @Test
    fun twoUnitsWithTheSameSymbolBothTakeTheirIsoAsTheSymbol() {
        val cny = CurrencyUnit("CNY", "Chinese Yuan", "¥", 2)
        val jpy = CurrencyUnit("JPY", "Japanese Yen", "¥", 0)
        val shownCny = cny.withIsoSymbolIfSymbolsMatch(jpy)
        val shownJpy = jpy.withIsoSymbolIfSymbolsMatch(cny)
        assertEquals("CNY", shownCny.symbol)
        assertEquals("Chinese Yuan", shownCny.name)
        assertEquals(2, shownCny.decimals)
        assertEquals("JPY", shownJpy.symbol)
        assertEquals("Japanese Yen", shownJpy.name)
        assertEquals(0, shownJpy.decimals)
        assertEquals("¥", cny.symbol)
    }

    @Test
    fun twoUnitsWithDifferentSymbolsAreReturnedUnchanged() {
        val usd = CurrencyUnit("USD", "US Dollar", "$", 2)
        val eur = CurrencyUnit("EUR", "Euro", "€", 2)
        assertSame(usd, usd.withIsoSymbolIfSymbolsMatch(eur))
        assertSame(eur, eur.withIsoSymbolIfSymbolsMatch(usd))
    }

    @Test
    fun aUnitWithNoSymbolMatchesAUnitWhoseSymbolIsItsIso() {
        val xaf = CurrencyUnit("XAF", "CFA Franc BEAC", null, 0)
        val custom = CurrencyUnit("ZZZ", "Custom", "XAF", 2)
        assertEquals("XAF", xaf.withIsoSymbolIfSymbolsMatch(custom).symbolOrIso)
        assertEquals("ZZZ", custom.withIsoSymbolIfSymbolsMatch(xaf).symbolOrIso)
    }
}
