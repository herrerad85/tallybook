package com.oriondev.moneywallet.ui.drawable;

import com.oriondev.moneywallet.utils.Utils;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Robolectric only for Utils.PALETTE, which is built with Color.rgb. The fonts and the color math
 * need nothing from Android.
 */
@RunWith(RobolectricTestRunner.class)
public class DotMatrixLettersTest {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789?";

    @Test
    public void everyTallGlyphIsSevenByNine() {
        assertFont(DotMatrixDrawable.TALL, 7, 9);
    }

    @Test
    public void everySmallGlyphIsFiveBySeven() {
        assertFont(DotMatrixDrawable.SMALL, 5, 7);
    }

    @Test
    public void everySingleAndPairFitsWithoutLosingADot() {
        for (char a : CHARACTERS.toCharArray()) {
            String[] single = grid(String.valueOf(a));
            assertPlaced(single, DotMatrixDrawable.TALL.get(a), 3, 2);
            assertEquals(String.valueOf(a), dots(DotMatrixDrawable.TALL.get(a)), dots(single));
            for (char b : CHARACTERS.toCharArray()) {
                String[] pair = grid("" + a + b);
                assertPlaced(pair, DotMatrixDrawable.SMALL.get(a), 1, 3);
                assertPlaced(pair, DotMatrixDrawable.SMALL.get(b), 7, 3);
                int expected = dots(DotMatrixDrawable.SMALL.get(a)) + dots(DotMatrixDrawable.SMALL.get(b));
                assertEquals("" + a + b, expected, dots(pair));
            }
        }
    }

    private static void assertPlaced(String[] grid, String[] glyph, int left, int top) {
        for (int r = 0; r < glyph.length; r++) {
            assertEquals(glyph[r], grid[top + r].substring(left, left + glyph[r].length()));
        }
    }

    @Test
    public void textOutsideTheFontsFallsBackToTheClassicDisc() {
        for (String text : new String[] {null, "", "ABC", "é", "É", "A "}) {
            assertNull(String.valueOf(text), DotMatrixDrawable.forText(text, 0xFF000000));
        }
        assertNotNull(DotMatrixDrawable.forText("?", 0xFFFFC107));
        assertNotNull(DotMatrixDrawable.forText("BF", 0xFF43A047));
    }

    @Test
    public void lowerCaseDrawsAsUpperCase() {
        assertArrayEquals(DotMatrixDrawable.letters("C"), DotMatrixDrawable.letters("c"));
        assertArrayEquals(DotMatrixDrawable.letters("AB"), DotMatrixDrawable.letters("Ab"));
        assertArrayEquals(DotMatrixDrawable.letters("YI"), DotMatrixDrawable.letters("Y\u0130"));
        assertNotNull(DotMatrixDrawable.forText("c", 0xFF000000));
    }

    @Test
    public void inkClearsThreeToOneOnBothThemes() {
        List<Integer> colors = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            colors.add(Utils.getRandomMDColor(i));
        }
        for (int color : new int[] {0xFF000000, 0xFF43A047, 0xFFD32F2F, 0xFFFFC107, 0xFFFFEB3B, 0xFF1A237E}) {
            colors.add(color);
        }
        for (int color : colors) {
            for (boolean dark : new boolean[] {false, true}) {
                int[] tint = DotMatrixDrawable.tint(color, dark);
                double contrast = DotMatrixDrawable.contrast(tint[2], tint[0]);
                assertTrue(String.format("#%06X dark=%b is %.2f", color & 0xFFFFFF, dark, contrast), contrast >= 3.0);
            }
        }
    }

    // values from the review page script for the same colors
    @Test
    public void colorsMatchTheReviewPage() {
        assertArrayEquals(new int[] {0xFFF6EDBC, 0xFFFAEC88, 0xFF877E20}, DotMatrixDrawable.tint(0xFFFFEB3B, false));
        assertArrayEquals(new int[] {0xFF6F652A, 0xFFA99B31, 0xFFFFEB3B}, DotMatrixDrawable.tint(0xFFFFEB3B, true));
        assertArrayEquals(new int[] {0xFFBEB9AF, 0xFF726F69, 0xFF000000}, DotMatrixDrawable.tint(0xFF000000, false));
        assertArrayEquals(new int[] {0xFF1D1A17, 0xFF11100E, 0xFF6A6A6A}, DotMatrixDrawable.tint(0xFF000000, true));
    }

    private static void assertFont(Map<Character, String[]> font, int width, int height) {
        assertEquals(CHARACTERS.length(), font.size());
        for (char c : CHARACTERS.toCharArray()) {
            String[] rows = font.get(c);
            assertNotNull(String.valueOf(c), rows);
            assertEquals(String.valueOf(c), height, rows.length);
            for (String row : rows) {
                assertTrue(c + " row \"" + row + "\"", row.matches("[#.]{" + width + "}"));
            }
        }
    }

    private static String[] grid(String text) {
        String[] rows = DotMatrixDrawable.letters(text);
        assertNotNull(text, rows);
        assertEquals(text, DotMatrixDrawable.GRID, rows.length);
        for (String row : rows) {
            assertTrue(text + " row \"" + row + "\"", row.matches("[#.]{" + DotMatrixDrawable.GRID + "}"));
        }
        return rows;
    }

    private static int dots(String[] rows) {
        int dots = 0;
        for (String row : rows) {
            for (int i = 0; i < row.length(); i++) {
                if (row.charAt(i) == '#') {
                    dots++;
                }
            }
        }
        return dots;
    }
}
