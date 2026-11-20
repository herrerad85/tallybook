/*
 * Copyright (c) 2018.
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

package com.oriondev.moneywallet.ui.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

/**
 * Created by andrea on 02/03/18.
 */
open class CategoryChildIndicator : View {

    private var mPaint: Paint? = null
    private var mIsLast: Boolean = false

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize()
    }

    private fun initialize() {
        mPaint = Paint()
        mPaint!!.isAntiAlias = true
        mIsLast = true
    }

    fun setLast(last: Boolean) {
        mIsLast = last
    }

    fun setLineColor(color: Int) {
        mPaint!!.color = color
        invalidate()
    }

    public override fun onDraw(c: Canvas) {
        val width = measuredWidth
        val height = measuredHeight
        val lineSize = 8
        if (width == 0 || height == 0) {
            return
        }
        // The row around this view is mirrored in a right to left layout and this canvas is not,
        // so the child's icon ends up on the left while the arm below still runs to the right
        // edge. Mirroring the canvas puts the arm on the side the icon is on.
        //
        // The pivot is the integer width / 2, which is what the strokes below are built around,
        // not width / 2f. At an odd width those differ by half a pixel and the mirror would move
        // the vertical stroke a pixel instead of mapping it onto itself.
        //
        // Saved and restored because onDrawForeground runs after this on the same canvas, so a
        // foreground or a ripple added to this view later would otherwise be mirrored too.
        val savedCanvas = c.save()
        if (layoutDirection == LAYOUT_DIRECTION_RTL) {
            c.scale(-1f, 1f, (width / 2).toFloat(), 0f)
        }
        val startX = (width / 2) - (lineSize / 2)
        var endX = startX + lineSize
        var startY = 0
        var endY = if (mIsLast) ((height / 2) + (lineSize / 2)) else height
        c.drawRect(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), mPaint!!)
        endX = width
        startY = (height / 2) + (lineSize / 2)
        endY = startY + lineSize
        c.drawRect(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), mPaint!!)
        c.restoreToCount(savedCanvas)
    }
}