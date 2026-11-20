/*
 * Copyright (c) 2018.
 *
 * Portions derived from datepicker-timeline, Copyright (c) 2015 Yann Badoual,
 * MIT License.
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

package com.oriondev.moneywallet.ui.view.calendar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

/** Simple view to draw a colored and sized dot */
class DotView : View {

    private var paint: Paint? = null
    private var size = -1

    constructor(context: Context) : this(context, null)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint!!.style = Paint.Style.FILL
        paint!!.color = Color.WHITE
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (size == -1) {
            // Default size if full view
            size = w
        }
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle((measuredWidth / 2).toFloat(), (measuredHeight / 2).toFloat(), (size / 2).toFloat(), paint!!)
    }

    fun setColor(@ColorInt color: Int) {
        paint!!.color = color
        invalidate()
    }

    fun setColorRes(@ColorRes colorRes: Int) {
        setColor(ContextCompat.getColor(context, colorRes))
    }

    fun setCircleSize(size: Int) {
        this.size = size
        invalidate()
    }

    fun setCircleSizeDp(sizeInDp: Int) {
        setCircleSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, sizeInDp.toFloat(), resources.displayMetrics).toInt())
    }
}
