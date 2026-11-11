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

package com.oriondev.moneywallet.ui.drawable

import android.content.Context
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable

import com.oriondev.moneywallet.ui.view.theme.ThemeEngine

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Collections
import java.util.HashMap

/**
 * A category icon drawn as a 13 by 13 grid of dots on a round tile. The grids live in one asset,
 * each a name line followed by 13 rows of '#' for a dot and '.' for none, keyed by the icon's
 * resource name without the ic_icon_ prefix.
 * <p>
 * The colors are picked on every draw rather than when the drawable is built, so a view that is
 * redrawn after a theme change shows the new theme without being handed a new drawable.
 */
class DotMatrixDrawable : Drawable {

    companion object {

        const val ASSET = "resources/dot_icons.txt"
        const val RESOURCE_PREFIX = "ic_icon_"
        const val GRID = 13

        private const val LIGHT_TILE = 0xFFF4EDE0.toInt()
        private const val LIGHT_STROKE = 0xFFE3D8C6.toInt()
        private const val LIGHT_INK = 0xFFD4541E.toInt()
        private const val DARK_TILE = 0xFF2B2622.toInt()
        private const val DARK_STROKE = 0xFF3A332C.toInt()
        private const val DARK_INK = 0xFFE8743A.toInt()

        private var sGrids: Map<String, Array<String?>>? = null

        // TALL is for a single character and SMALL for two, both as rows of '#' for a dot and '.' for none
        @JvmField
        val TALL: Map<Char, Array<String>> = font(
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
                "?", ".#####. #.....# ......# .....#. ....#.. ...#... ...#... ....... ...#...")
        @JvmField
        val SMALL: Map<Char, Array<String>> = font(
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
                "?", ".###. #...# ....# ...#. ..#.. ..... ..#..")

        /**
         * @return the drawable for this icon resource, or null when it has no grid.
         */
        @JvmStatic
        fun forResource(context: Context?, resourceName: String?): DotMatrixDrawable? {
            if (resourceName == null || !resourceName.startsWith(RESOURCE_PREFIX)) {
                return null
            }
            val grid: Array<String?>? = getGrids(context)[resourceName.substring(RESOURCE_PREFIX.length)]
            return if (grid != null) DotMatrixDrawable(grid) else null
        }

        /**
         * @return the drawable for the text of a color icon on a tile tinted by its disc color, or
         * null when the text is not one or two characters that the fonts have.
         */
        @JvmStatic
        fun forText(text: String?, discColor: Int): DotMatrixDrawable? {
            val grid: Array<String?>? = letters(text)
            return if (grid != null) DotMatrixDrawable(grid, discColor) else null
        }

        @JvmStatic
        fun letters(text: String?): Array<String?>? {
            if (text == null || text.isEmpty() || text.length > 2) {
                return null
            }
            val chars = text.toCharArray()
            for (i in 0 until text.length) {
                chars[i] = Character.toUpperCase(chars[i])
                // a Turkish locale uppercases i to dotted capital I
                chars[i] = if (chars[i] == '\u0130') 'I' else chars[i]
                if (!TALL.containsKey(chars[i])) {
                    return null
                }
            }
            val grid = Array(GRID) { CharArray(GRID) }
            for (row in grid) {
                Arrays.fill(row, '.')
            }
            if (text.length == 1) {
                place(grid, TALL[chars[0]], 3)
            } else {
                place(grid, SMALL[chars[0]], 1)
                place(grid, SMALL[chars[1]], 7)
            }
            val rows = arrayOfNulls<String>(GRID)
            for (r in 0 until GRID) {
                rows[r] = String(grid[r])
            }
            return rows
        }

        private fun place(grid: Array<CharArray>, glyph: Array<String>?, left: Int) {
            val top = (GRID - glyph!!.size) / 2
            for (r in 0 until glyph.size) {
                for (c in 0 until glyph[r].length) {
                    if (glyph[r][c] == '#') {
                        grid[top + r][left + c] = '#'
                    }
                }
            }
        }

        private fun font(vararg entries: String): Map<Char, Array<String>> {
            val font: MutableMap<Char, Array<String>> = HashMap()
            for (i in 0 until entries.size step 2) {
                font.put(entries[i][0], entries[i + 1].split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray())
            }
            return font
        }

        /**
         * @return the tile, stroke and ink of a letter tile. The tile leans toward the disc color and
         * the ink starts as the disc color, stepping toward black or white until it reads at 3:1.
         */
        @JvmStatic
        fun tint(discColor: Int, dark: Boolean): IntArray {
            val tile = mix(if (dark) DARK_TILE else LIGHT_TILE, discColor, if (dark) 0.32 else 0.22)
            val stroke = mix(tile, discColor, 0.40)
            val target = if (luminance(tile) > 0.4) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            var ink = discColor or 0xFF000000.toInt()
            var i = 0
            while (i < 20 && contrast(ink, tile) < 3.0) {
                ink = mix(ink, target, 0.10)
                i++
            }
            return intArrayOf(tile, stroke, ink)
        }

        private fun mix(a: Int, b: Int, t: Double): Int {
            var color = 0xFF000000.toInt()
            for (shift in 0..16 step 8) {
                val x = (a shr shift) and 0xFF
                val y = (b shr shift) and 0xFF
                color = color or (Math.round(x + (y - x) * t).toInt() shl shift)
            }
            return color
        }

        // WCAG 2 relative luminance and contrast ratio
        private fun luminance(color: Int): Double {
            return 0.2126 * linear(color shr 16) + 0.7152 * linear(color shr 8) + 0.0722 * linear(color)
        }

        private fun linear(channel: Int): Double {
            val c = (channel and 0xFF) / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }

        @JvmStatic
        fun contrast(a: Int, b: Int): Double {
            val x = luminance(a)
            val y = luminance(b)
            return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05)
        }

        // synchronized because the pie chart loader asks for its icons off the main thread
        private fun getGrids(context: Context?): Map<String, Array<String?>> {
            synchronized(DotMatrixDrawable::class.java) {
                var grids = sGrids
                if (grids == null) {
                    grids = try {
                        context!!.assets.open(ASSET).use { inputStream: InputStream ->
                            parse(BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)))
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                        Collections.emptyMap()
                    }
                    sGrids = grids
                }
                return grids
            }
        }

        /**
         * Reads the grids without checking them. The unit test over the asset is what holds every
         * grid to GRID rows of GRID characters.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun parse(reader: BufferedReader): Map<String, Array<String?>> {
            val grids: MutableMap<String, Array<String?>> = HashMap()
            while (true) {
                val name: String = reader.readLine() ?: break
                if (name.isEmpty()) {
                    continue
                }
                val rows = arrayOfNulls<String>(GRID)
                for (i in 0 until GRID) {
                    rows[i] = reader.readLine()
                }
                grids.put(name, rows)
            }
            return grids
        }
    }

    private val mGrid: Array<String?>
    private val mTilePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mInkPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mDiscColor: Int?
    private var mAlpha = 255

    private constructor(grid: Array<String?>) : this(grid, null)

    private constructor(grid: Array<String?>, discColor: Int?) : super() {
        mGrid = grid
        mDiscColor = discColor
        mTilePaint.style = Paint.Style.FILL
        mStrokePaint.style = Paint.Style.STROKE
        mInkPaint.style = Paint.Style.FILL
    }

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width()
        val h = b.height()
        val side = Math.min(w, h)
        if (side <= 0) {
            return
        }
        val dark = ThemeEngine.getTheme().isDark
        if (mDiscColor != null) {
            val colors = tint(mDiscColor, dark)
            setColor(mTilePaint, colors[0])
            setColor(mStrokePaint, colors[1])
            setColor(mInkPaint, colors[2])
        } else {
            setColor(mTilePaint, if (dark) DARK_TILE else LIGHT_TILE)
            setColor(mStrokePaint, if (dark) DARK_STROKE else LIGHT_STROKE)
            setColor(mInkPaint, if (dark) DARK_INK else LIGHT_INK)
        }
        val stroke = Math.max(1f, side / 40f)
        val cx = b.exactCenterX()
        val cy = b.exactCenterY()
        canvas.drawCircle(cx, cy, side / 2f, mTilePaint)
        mStrokePaint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, (side - stroke) / 2f, mStrokePaint)

        val pitch = Math.max(2, Math.floor((side * 0.70f / GRID).toDouble()).toInt())
        val left = b.left + Math.floor(((w - pitch * GRID) / 2f).toDouble()).toInt()
        val top = b.top + Math.floor(((h - pitch * GRID) / 2f).toDouble()).toInt()
        // below 4px a circle blurs into a smudge, so the dots become squares a pixel apart
        val squares = pitch < 4
        val dot = 0.26f * pitch
        for (r in 0 until GRID) {
            val row = mGrid[r]
            for (c in 0 until GRID) {
                if (row!![c] != '#') {
                    continue
                }
                val x = left + c * pitch
                val y = top + r * pitch
                if (squares) {
                    canvas.drawRect(x.toFloat(), y.toFloat(), (x + pitch - 1).toFloat(), (y + pitch - 1).toFloat(), mInkPaint)
                } else {
                    canvas.drawCircle(x + pitch / 2f, y + pitch / 2f, dot, mInkPaint)
                }
            }
        }
    }

    private fun setColor(paint: Paint, color: Int) {
        paint.color = color
        paint.alpha = mAlpha
    }

    // no size of its own, so an ImageView or the pie chart stretches it to whatever bounds it has
    override fun getIntrinsicWidth(): Int {
        return -1
    }

    override fun getIntrinsicHeight(): Int {
        return -1
    }

    override fun setAlpha(alpha: Int) {
        mAlpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        mTilePaint.colorFilter = colorFilter
        mStrokePaint.colorFilter = colorFilter
        mInkPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }
}
