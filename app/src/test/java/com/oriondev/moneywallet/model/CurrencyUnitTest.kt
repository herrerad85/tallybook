package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
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
}
