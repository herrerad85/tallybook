package com.oriondev.moneywallet.utils

import com.oriondev.moneywallet.model.Money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MoneyFormatterTest {

    companion object {

        private const val DIVIDER = " · "
    }

    private fun money(vararg amounts: Pair<String, Long>): Money {
        val money = Money()
        for ((currency, amount) in amounts) {
            money.addMoney(currency, amount)
        }
        return money
    }

    private fun parts(text: String, divider: String): Set<String> {
        return text.split(divider).toSet()
    }

    @Test
    fun currenciesWithDistinctSymbolsAreJoinedByAMiddleDotAndKeepTheirSymbols() {
        val text = MoneyFormatter.getInstance().getNotTintedString(money("EUR" to 1050L, "USD" to 2000L))
        assertEquals(setOf("€ 10.50", "$ 20.00"), parts(text, DIVIDER))
        assertFalse(text.contains(" - "))
    }

    @Test
    fun currenciesSharingASymbolPrintTheirIsoCodeInsteadOfTheSymbol() {
        val text = MoneyFormatter.getInstance().getNotTintedString(money("USD" to 2000L, "MXN" to 3000L))
        assertEquals(setOf("USD 20.00", "MXN 30.00"), parts(text, DIVIDER))
        assertFalse(text.contains("$"))
    }

    @Test
    fun onlyTheCurrenciesSharingASymbolPrintTheirIsoCodeAmongThree() {
        val text = MoneyFormatter.getInstance().getNotTintedString(money("EUR" to 1050L, "USD" to 2000L, "MXN" to 3000L))
        assertEquals(setOf("€ 10.50", "USD 20.00", "MXN 30.00"), parts(text, DIVIDER))
    }

    @Test
    fun aSingleCurrencyAmountKeepsItsSymbol() {
        val formatter = MoneyFormatter.getInstance()
        assertEquals("$ 16,431.69", formatter.getNotTintedString(Money("USD", 1643169L)))
        assertEquals("$ 16,431.69", formatter.getTintedString(Money("USD", 1643169L)).toString())
    }

    @Test
    fun theTintedStringOfAnAmountReadsTheSameAsTheNotTintedOne() {
        val formatter = MoneyFormatter.getInstance()
        val distinct = formatter.getTintedString(money("EUR" to 1050L, "USD" to 2000L)).toString()
        val shared = formatter.getTintedString(money("USD" to 2000L, "MXN" to 3000L)).toString()
        assertEquals(setOf("€ 10.50", "$ 20.00"), parts(distinct, DIVIDER))
        assertEquals(setOf("USD 20.00", "MXN 30.00"), parts(shared, DIVIDER))
    }

    @Test
    fun aDividerSetOnTheFormatterGoesBetweenTheCurrencies() {
        val formatter = MoneyFormatter.getInstance()
        formatter.setDivider("\n")
        val amount = money("EUR" to 1050L, "USD" to 2000L, "MXN" to 3000L)
        val expected = setOf("€ 10.50", "USD 20.00", "MXN 30.00")
        assertEquals(expected, parts(formatter.getNotTintedString(amount), "\n"))
        assertEquals(expected, parts(formatter.getTintedString(amount).toString(), "\n"))
    }

    @Test
    fun aSingleCurrencyWithoutASymbolPrintsItsIsoCode() {
        val formatter = MoneyFormatter.getInstance()
        val notTinted = formatter.getNotTintedString(Money("XAF", 1000L))
        val tinted = formatter.getTintedString(Money("XAF", 1000L)).toString()
        assertEquals("XAF 1,000", notTinted)
        assertEquals("XAF 1,000", tinted)
        assertFalse(notTinted.contains("null"))
        assertFalse(tinted.contains("null"))
    }

    @Test
    fun aCurrencyWithoutASymbolNextToOneWithASymbolPrintsItsIsoCode() {
        val text = MoneyFormatter.getInstance().getNotTintedString(money("XAF" to 1000L, "USD" to 2000L))
        assertEquals(setOf("XAF 1,000", "$ 20.00"), parts(text, DIVIDER))
        assertFalse(text.contains("null"))
    }

    @Test
    fun twoCurrenciesWithoutASymbolEachPrintTheirIsoCode() {
        val text = MoneyFormatter.getInstance().getNotTintedString(money("XAF" to 1000L, "XOF" to 2500L))
        assertEquals(setOf("XAF 1,000", "XOF 2,500"), parts(text, DIVIDER))
        assertFalse(text.contains("null"))
    }
}
