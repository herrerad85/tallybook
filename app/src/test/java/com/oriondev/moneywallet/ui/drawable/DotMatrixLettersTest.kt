package com.oriondev.moneywallet.ui.drawable

import com.oriondev.moneywallet.utils.Utils

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.util.ArrayList

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * Robolectric only for Utils.PALETTE, which is built with Color.rgb. The fonts and the color math
 * need nothing from Android.
 */
@RunWith(RobolectricTestRunner::class)
class DotMatrixLettersTest {

    companion object {

        private const val CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?"
    }

    @Test
    fun everyTallGlyphIsSevenByNine() {
        assertFont(DotMatrixDrawable.TALL, 7, 9)
    }

    @Test
    fun everySmallGlyphIsFiveBySeven() {
        assertFont(DotMatrixDrawable.SMALL, 5, 7)
    }

    @Test
    fun everySingleAndPairFitsWithoutLosingADot() {
        for (a in CHARACTERS.toCharArray()) {
            val single = grid(a.toString())
            assertPlaced(single, DotMatrixDrawable.TALL.get(a), 3, 2)
            assertEquals(a.toString(), dots(DotMatrixDrawable.TALL.get(a)), dots(single))
            for (b in CHARACTERS.toCharArray()) {
                val pair = grid("" + a + b)
                assertPlaced(pair, DotMatrixDrawable.SMALL.get(a), 1, 3)
                assertPlaced(pair, DotMatrixDrawable.SMALL.get(b), 7, 3)
                val expected = dots(DotMatrixDrawable.SMALL.get(a)) + dots(DotMatrixDrawable.SMALL.get(b))
                assertEquals("" + a + b, expected, dots(pair))
            }
        }
    }

    private fun assertPlaced(grid: Array<String?>, glyph: Array<String>?, left: Int, top: Int) {
        for (r in 0 until glyph!!.size) {
            assertEquals(glyph[r], grid[top + r]!!.substring(left, left + glyph[r].length))
        }
    }

    @Test
    fun textOutsideTheFontsFallsBackToTheClassicDisc() {
        for (text in arrayOf(null, "", "ABC", "é", "É", "A ")) {
            assertNull(text.toString(), DotMatrixDrawable.forText(text, 0xFF000000.toInt()))
        }
        assertNotNull(DotMatrixDrawable.forText("?", 0xFFFFC107.toInt()))
        assertNotNull(DotMatrixDrawable.forText("BF", 0xFF43A047.toInt()))
    }

    @Test
    fun lowerCaseDrawsAsUpperCase() {
        assertArrayEquals(DotMatrixDrawable.letters("C"), DotMatrixDrawable.letters("c"))
        assertArrayEquals(DotMatrixDrawable.letters("AB"), DotMatrixDrawable.letters("Ab"))
        assertArrayEquals(DotMatrixDrawable.letters("YI"), DotMatrixDrawable.letters("Yİ"))
        assertNotNull(DotMatrixDrawable.forText("c", 0xFF000000.toInt()))
    }

    @Test
    fun inkClearsThreeToOneOnBothThemes() {
        val colors: MutableList<Int> = ArrayList()
        for (i in 0 until 13) {
            colors.add(Utils.getRandomMDColor(i))
        }
        for (color in intArrayOf(0xFF000000.toInt(), 0xFF43A047.toInt(), 0xFFD32F2F.toInt(), 0xFFFFC107.toInt(), 0xFFFFEB3B.toInt(), 0xFF1A237E.toInt())) {
            colors.add(color)
        }
        for (color in colors) {
            for (dark in booleanArrayOf(false, true)) {
                val tint = DotMatrixDrawable.tint(color, dark)
                val contrast = DotMatrixDrawable.contrast(tint[2], tint[0])
                assertTrue(String.format("#%06X dark=%b is %.2f", color and 0xFFFFFF, dark, contrast), contrast >= 3.0)
            }
        }
    }

    // values from the review page script for the same colors
    @Test
    fun colorsMatchTheReviewPage() {
        assertArrayEquals(intArrayOf(0xFFF6EDBC.toInt(), 0xFFFAEC88.toInt(), 0xFF877E20.toInt()), DotMatrixDrawable.tint(0xFFFFEB3B.toInt(), false))
        assertArrayEquals(intArrayOf(0xFF6F652A.toInt(), 0xFFA99B31.toInt(), 0xFFFFEB3B.toInt()), DotMatrixDrawable.tint(0xFFFFEB3B.toInt(), true))
        assertArrayEquals(intArrayOf(0xFFBEB9AF.toInt(), 0xFF726F69.toInt(), 0xFF000000.toInt()), DotMatrixDrawable.tint(0xFF000000.toInt(), false))
        assertArrayEquals(intArrayOf(0xFF1D1A17.toInt(), 0xFF11100E.toInt(), 0xFF6A6A6A.toInt()), DotMatrixDrawable.tint(0xFF000000.toInt(), true))
    }

    private fun assertFont(font: Map<Char, Array<String>>, width: Int, height: Int) {
        assertEquals(CHARACTERS.length, font.size)
        for (c in CHARACTERS.toCharArray()) {
            val rows = font.get(c)
            assertNotNull(c.toString(), rows)
            assertEquals(c.toString(), height, rows!!.size)
            for (row in rows) {
                assertTrue(c.toString() + " row \"" + row + "\"", row.matches(Regex("[#.]{" + width + "}")))
            }
        }
    }

    private fun grid(text: String): Array<String?> {
        val rows = DotMatrixDrawable.letters(text)
        assertNotNull(text, rows)
        assertEquals(text, DotMatrixDrawable.GRID, rows!!.size)
        for (row in rows) {
            assertTrue(text + " row \"" + row + "\"", row!!.matches(Regex("[#.]{" + DotMatrixDrawable.GRID + "}")))
        }
        return rows
    }

    private fun dots(rows: Array<out String?>?): Int {
        var dots = 0
        for (row in rows!!) {
            for (i in 0 until row!!.length) {
                if (row[i] == '#') {
                    dots++
                }
            }
        }
        return dots
    }
}
