/*
 * Copyright (c) 2026.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.drawable;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A category icon drawn as a 13 by 13 grid of dots on a round tile. The grids live in one asset,
 * each a name line followed by 13 rows of '#' for a dot and '.' for none, keyed by the icon's
 * resource name without the ic_icon_ prefix.
 * <p>
 * The colors are picked on every draw rather than when the drawable is built, so a view that is
 * redrawn after a theme change shows the new theme without being handed a new drawable.
 */
public class DotMatrixDrawable extends Drawable {

    public static final String ASSET = "resources/dot_icons.txt";
    public static final String RESOURCE_PREFIX = "ic_icon_";
    public static final int GRID = 13;

    private static final int LIGHT_TILE = 0xFFF4EDE0;
    private static final int LIGHT_STROKE = 0xFFE3D8C6;
    private static final int LIGHT_INK = 0xFFD4541E;
    private static final int DARK_TILE = 0xFF2B2622;
    private static final int DARK_STROKE = 0xFF3A332C;
    private static final int DARK_INK = 0xFFE8743A;

    private static Map<String, String[]> sGrids;

    // TALL is for a single character and SMALL for two, both as rows of '#' for a dot and '.' for none
    static final Map<Character, String[]> TALL = font(
            "A", "..###.. .#...#. #.....# #.....# ####### #.....# #.....# #.....# #.....#",
            "B", "######. #.....# #.....# #.....# ######. #.....# #.....# #.....# ######.",
            "C", ".#####. #.....# #...... #...... #...... #...... #...... #.....# .#####.",
            "D", "#####.. #....#. #.....# #.....# #.....# #.....# #.....# #....#. #####..",
            "E", "####### #...... #...... #...... ######. #...... #...... #...... #######",
            "F", "####### #...... #...... #...... ######. #...... #...... #...... #......",
            "G", ".#####. #.....# #...... #...... #..#### #.....# #.....# #.....# .#####.",
            "H", "#.....# #.....# #.....# #.....# ####### #.....# #.....# #.....# #.....#",
            "I", ".#####. ...#... ...#... ...#... ...#... ...#... ...#... ...#... .#####.",
            "J", "..##### .....#. .....#. .....#. .....#. .....#. .....#. #....#. .####..",
            "K", "#.....# #....#. #...#.. #..#... ###.... #..#... #...#.. #....#. #.....#",
            "L", "#...... #...... #...... #...... #...... #...... #...... #...... #######",
            "M", "#.....# ##...## #.#.#.# #..#..# #.....# #.....# #.....# #.....# #.....#",
            "N", "#.....# #.....# ##....# #.#...# #..#..# #...#.# #....## #.....# #.....#",
            "O", ".#####. #.....# #.....# #.....# #.....# #.....# #.....# #.....# .#####.",
            "P", "######. #.....# #.....# #.....# ######. #...... #...... #...... #......",
            "Q", ".#####. #.....# #.....# #.....# #.....# #.....# #...#.# #....#. .####.#",
            "R", "######. #.....# #.....# #.....# ######. #...#.. #....#. #.....# #.....#",
            "S", ".#####. #.....# #...... #...... .#####. ......# ......# #.....# .#####.",
            "T", "####### ...#... ...#... ...#... ...#... ...#... ...#... ...#... ...#...",
            "U", "#.....# #.....# #.....# #.....# #.....# #.....# #.....# #.....# .#####.",
            "V", "#.....# #.....# #.....# #.....# .#...#. .#...#. ..#.#.. ..#.#.. ...#...",
            "W", "#.....# #.....# #.....# #.....# #..#..# #..#..# #.#.#.# ##...## #.....#",
            "X", "#.....# #.....# .#...#. ..#.#.. ...#... ..#.#.. .#...#. #.....# #.....#",
            "Y", "#.....# #.....# .#...#. ..#.#.. ...#... ...#... ...#... ...#... ...#...",
            "Z", "####### ......# .....#. ....#.. ...#... ..#.... .#..... #...... #######",
            "0", ".#####. #.....# #....## #...#.# #..#..# #.#...# ##....# #.....# .#####.",
            "1", "...#... ..##... .#.#... ...#... ...#... ...#... ...#... ...#... .#####.",
            "2", ".#####. #.....# ......# .....#. ....#.. ...#... ..#.... .#..... #######",
            "3", ".#####. #.....# ......# ......# ..####. ......# ......# #.....# .#####.",
            "4", "....##. ...#.#. ..#..#. .#...#. #....#. ####### .....#. .....#. .....#.",
            "5", "####### #...... #...... ######. ......# ......# ......# #.....# .#####.",
            "6", "..####. .#..... #...... #...... ######. #.....# #.....# #.....# .#####.",
            "7", "####### ......# .....#. ....#.. ...#... ..#.... ..#.... ..#.... ..#....",
            "8", ".#####. #.....# #.....# #.....# .#####. #.....# #.....# #.....# .#####.",
            "9", ".#####. #.....# #.....# #.....# .###### ......# ......# .....#. .####..",
            "?", ".#####. #.....# ......# .....#. ....#.. ...#... ...#... ....... ...#...");
    static final Map<Character, String[]> SMALL = font(
            "A", ".###. #...# #...# ##### #...# #...# #...#",
            "B", "####. #...# #...# ####. #...# #...# ####.",
            "C", ".###. #...# #.... #.... #.... #...# .###.",
            "D", "###.. #..#. #...# #...# #...# #..#. ###..",
            "E", "##### #.... #.... ####. #.... #.... #####",
            "F", "##### #.... #.... ####. #.... #.... #....",
            "G", ".###. #...# #.... #.### #...# #...# .####",
            "H", "#...# #...# #...# ##### #...# #...# #...#",
            "I", ".###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.",
            "J", "..### ...#. ...#. ...#. ...#. #..#. .##..",
            "K", "#...# #..#. #.#.. ##... #.#.. #..#. #...#",
            "L", "#.... #.... #.... #.... #.... #.... #####",
            "M", "#...# ##.## #.#.# #.#.# #...# #...# #...#",
            "N", "#...# #...# ##..# #.#.# #..## #...# #...#",
            "O", ".###. #...# #...# #...# #...# #...# .###.",
            "P", "####. #...# #...# ####. #.... #.... #....",
            "Q", ".###. #...# #...# #...# #.#.# #..#. .##.#",
            "R", "####. #...# #...# ####. #.#.. #..#. #...#",
            "S", ".#### #.... #.... .###. ....# ....# ####.",
            "T", "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
            "U", "#...# #...# #...# #...# #...# #...# .###.",
            "V", "#...# #...# #...# #...# #...# .#.#. ..#..",
            "W", "#...# #...# #...# #.#.# #.#.# #.#.# .#.#.",
            "X", "#...# #...# .#.#. ..#.. .#.#. #...# #...#",
            "Y", "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..",
            "Z", "##### ....# ...#. ..#.. .#... #.... #####",
            "0", ".###. #...# #..## #.#.# ##..# #...# .###.",
            "1", "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
            "2", ".###. #...# ....# ...#. ..#.. .#... #####",
            "3", "##### ...#. ..#.. ...#. ....# #...# .###.",
            "4", "...#. ..##. .#.#. #..#. ##### ...#. ...#.",
            "5", "##### #.... ####. ....# ....# #...# .###.",
            "6", "..##. .#... #.... ####. #...# #...# .###.",
            "7", "##### ....# ...#. ..#.. .#... .#... .#...",
            "8", ".###. #...# #...# .###. #...# #...# .###.",
            "9", ".###. #...# #...# .#### ....# ...#. .##..",
            "?", ".###. #...# ....# ...#. ..#.. ..... ..#..");

    /**
     * @return the drawable for this icon resource, or null when it has no grid.
     */
    public static DotMatrixDrawable forResource(Context context, String resourceName) {
        if (resourceName == null || !resourceName.startsWith(RESOURCE_PREFIX)) {
            return null;
        }
        String[] grid = getGrids(context).get(resourceName.substring(RESOURCE_PREFIX.length()));
        return grid != null ? new DotMatrixDrawable(grid) : null;
    }

    /**
     * @return the drawable for the text of a color icon on a tile tinted by its disc color, or
     * null when the text is not one or two characters that the fonts have.
     */
    public static DotMatrixDrawable forText(String text, int discColor) {
        String[] grid = letters(text);
        return grid != null ? new DotMatrixDrawable(grid, discColor) : null;
    }

    static String[] letters(String text) {
        if (text == null || text.isEmpty() || text.length() > 2) {
            return null;
        }
        char[] chars = text.toCharArray();
        for (int i = 0; i < text.length(); i++) {
            chars[i] = Character.toUpperCase(chars[i]);
            // a Turkish locale uppercases i to dotted capital I
            chars[i] = chars[i] == '\u0130' ? 'I' : chars[i];
            if (!TALL.containsKey(chars[i])) {
                return null;
            }
        }
        char[][] grid = new char[GRID][GRID];
        for (char[] row : grid) {
            Arrays.fill(row, '.');
        }
        if (text.length() == 1) {
            place(grid, TALL.get(chars[0]), 3);
        } else {
            place(grid, SMALL.get(chars[0]), 1);
            place(grid, SMALL.get(chars[1]), 7);
        }
        String[] rows = new String[GRID];
        for (int r = 0; r < GRID; r++) {
            rows[r] = new String(grid[r]);
        }
        return rows;
    }

    private static void place(char[][] grid, String[] glyph, int left) {
        int top = (GRID - glyph.length) / 2;
        for (int r = 0; r < glyph.length; r++) {
            for (int c = 0; c < glyph[r].length(); c++) {
                if (glyph[r].charAt(c) == '#') {
                    grid[top + r][left + c] = '#';
                }
            }
        }
    }

    private static Map<Character, String[]> font(String... entries) {
        Map<Character, String[]> font = new HashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            font.put(entries[i].charAt(0), entries[i + 1].split(" "));
        }
        return font;
    }

    /**
     * @return the tile, stroke and ink of a letter tile. The tile leans toward the disc color and
     * the ink starts as the disc color, stepping toward black or white until it reads at 3:1.
     */
    static int[] tint(int discColor, boolean dark) {
        int tile = mix(dark ? DARK_TILE : LIGHT_TILE, discColor, dark ? 0.32 : 0.22);
        int stroke = mix(tile, discColor, 0.40);
        int target = luminance(tile) > 0.4 ? 0xFF000000 : 0xFFFFFFFF;
        int ink = discColor | 0xFF000000;
        for (int i = 0; i < 20 && contrast(ink, tile) < 3.0; i++) {
            ink = mix(ink, target, 0.10);
        }
        return new int[] {tile, stroke, ink};
    }

    static int mix(int a, int b, double t) {
        int color = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int x = (a >> shift) & 0xFF;
            int y = (b >> shift) & 0xFF;
            color |= (int) Math.round(x + (y - x) * t) << shift;
        }
        return color;
    }

    // WCAG 2 relative luminance and contrast ratio
    static double luminance(int color) {
        return 0.2126 * linear(color >> 16) + 0.7152 * linear(color >> 8) + 0.0722 * linear(color);
    }

    private static double linear(int channel) {
        double c = (channel & 0xFF) / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double contrast(int a, int b) {
        double x = luminance(a);
        double y = luminance(b);
        return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
    }

    // synchronized because the pie chart loader asks for its icons off the main thread
    private static synchronized Map<String, String[]> getGrids(Context context) {
        if (sGrids == null) {
            try (InputStream inputStream = context.getAssets().open(ASSET)) {
                sGrids = parse(new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                e.printStackTrace();
                sGrids = Collections.emptyMap();
            }
        }
        return sGrids;
    }

    /**
     * Reads the grids without checking them. The unit test over the asset is what holds every
     * grid to GRID rows of GRID characters.
     */
    public static Map<String, String[]> parse(BufferedReader reader) throws IOException {
        Map<String, String[]> grids = new HashMap<>();
        String name;
        while ((name = reader.readLine()) != null) {
            if (name.isEmpty()) {
                continue;
            }
            String[] rows = new String[GRID];
            for (int i = 0; i < GRID; i++) {
                rows[i] = reader.readLine();
            }
            grids.put(name, rows);
        }
        return grids;
    }

    private final String[] mGrid;
    private final Paint mTilePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mInkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Integer mDiscColor;
    private int mAlpha = 255;

    private DotMatrixDrawable(String[] grid) {
        this(grid, null);
    }

    private DotMatrixDrawable(String[] grid, Integer discColor) {
        mGrid = grid;
        mDiscColor = discColor;
        mTilePaint.setStyle(Paint.Style.FILL);
        mStrokePaint.setStyle(Paint.Style.STROKE);
        mInkPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        int w = b.width();
        int h = b.height();
        int side = Math.min(w, h);
        if (side <= 0) {
            return;
        }
        boolean dark = ThemeEngine.getTheme().isDark();
        if (mDiscColor != null) {
            int[] colors = tint(mDiscColor, dark);
            setColor(mTilePaint, colors[0]);
            setColor(mStrokePaint, colors[1]);
            setColor(mInkPaint, colors[2]);
        } else {
            setColor(mTilePaint, dark ? DARK_TILE : LIGHT_TILE);
            setColor(mStrokePaint, dark ? DARK_STROKE : LIGHT_STROKE);
            setColor(mInkPaint, dark ? DARK_INK : LIGHT_INK);
        }
        float stroke = Math.max(1f, side / 40f);
        float cx = b.exactCenterX();
        float cy = b.exactCenterY();
        canvas.drawCircle(cx, cy, side / 2f, mTilePaint);
        mStrokePaint.setStrokeWidth(stroke);
        canvas.drawCircle(cx, cy, (side - stroke) / 2f, mStrokePaint);

        int pitch = Math.max(2, (int) Math.floor(side * 0.70f / GRID));
        int left = b.left + (int) Math.floor((w - pitch * GRID) / 2f);
        int top = b.top + (int) Math.floor((h - pitch * GRID) / 2f);
        // below 4px a circle blurs into a smudge, so the dots become squares a pixel apart
        boolean squares = pitch < 4;
        float dot = 0.26f * pitch;
        for (int r = 0; r < GRID; r++) {
            String row = mGrid[r];
            for (int c = 0; c < GRID; c++) {
                if (row.charAt(c) != '#') {
                    continue;
                }
                int x = left + c * pitch;
                int y = top + r * pitch;
                if (squares) {
                    canvas.drawRect(x, y, x + pitch - 1, y + pitch - 1, mInkPaint);
                } else {
                    canvas.drawCircle(x + pitch / 2f, y + pitch / 2f, dot, mInkPaint);
                }
            }
        }
    }

    private void setColor(Paint paint, int color) {
        paint.setColor(color);
        paint.setAlpha(mAlpha);
    }

    // no size of its own, so an ImageView or the pie chart stretches it to whatever bounds it has
    @Override
    public int getIntrinsicWidth() {
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        return -1;
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mTilePaint.setColorFilter(colorFilter);
        mStrokePaint.setColorFilter(colorFilter);
        mInkPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
