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
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.StateListDrawable
import android.graphics.drawable.shapes.OvalShape
import android.util.AttributeSet
import android.util.TypedValue
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout

import androidx.annotation.ColorInt

import com.oriondev.moneywallet.utils.Utils

/**
 * One color of the color chooser's grid, and the preview beside its hex field. Ported from the
 * CircleView of material-dialogs 0.9.6.0, under the MIT license, minus the parts it drew nothing
 * with.
 */
class ColorSwatchView : FrameLayout {

    companion object {

        private const val RING_WIDTH_DP = 5f
        private const val WHITE_RING_WIDTH_DP = 3f
        private const val PRESSED_ALPHA = 0.7f

        @ColorInt
        private fun shiftColor(@ColorInt color: Int, by: Float): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            hsv[2] *= by
            return Color.HSVToColor(hsv)
        }

        @ColorInt
        private fun translucent(@ColorInt color: Int): Int {
            return Color.argb(Math.round(Color.alpha(color) * PRESSED_ALPHA),
                    Color.red(color), Color.green(color), Color.blue(color))
        }
    }

    private val mRingWidth: Int
    private val mWhiteRingWidth: Int

    private val mRingPaint: Paint
    private val mWhitePaint: Paint
    private val mFillPaint: Paint

    private var mSwatchSelected: Boolean = false

    constructor(context: Context) : this(context, null, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        mRingWidth = dp(RING_WIDTH_DP)
        mWhiteRingWidth = dp(WHITE_RING_WIDTH_DP)
        mWhitePaint = Paint()
        mWhitePaint.isAntiAlias = true
        mWhitePaint.color = Color.WHITE
        mFillPaint = Paint()
        mFillPaint.isAntiAlias = true
        mRingPaint = Paint()
        mRingPaint.isAntiAlias = true
        setColor(Color.DKGRAY)
        setWillNotDraw(false)
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                resources.displayMetrics).toInt()
    }

    fun setColor(@ColorInt color: Int) {
        mFillPaint.color = color
        mRingPaint.color = shiftColor(color, 0.9f)
        val pressedCircle = ShapeDrawable(OvalShape())
        pressedCircle.paint.color = translucent(shiftColor(color, 1.1f))
        val pressed = StateListDrawable()
        pressed.addState(intArrayOf(android.R.attr.state_pressed), pressedCircle)
        val ripple = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_pressed)),
                intArrayOf(shiftColor(color, 1.1f))
        )
        foreground = RippleDrawable(ripple, pressed, null)
        contentDescription = Utils.getHexColor(color)
        invalidate()
    }

    // The ring is the chooser's own flag, not the framework's selected flag, which GridView also
    // writes for its own reasons.
    fun setSwatchSelected(selected: Boolean) {
        mSwatchSelected = selected
        invalidate()
    }

    fun isSwatchSelected(): Boolean {
        return mSwatchSelected
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo?) {
        super.onInitializeAccessibilityNodeInfo(info)
        info!!.isSelected = mSwatchSelected
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredWidth, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = measuredWidth / 2
        val centerY = measuredHeight / 2
        val outerRadius = measuredWidth / 2
        if (mSwatchSelected) {
            val whiteRadius = outerRadius - mRingWidth
            canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), outerRadius.toFloat(), mRingPaint)
            canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), whiteRadius.toFloat(), mWhitePaint)
            canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), (whiteRadius - mWhiteRingWidth).toFloat(), mFillPaint)
        } else {
            canvas.drawCircle(centerX.toFloat(), centerY.toFloat(), outerRadius.toFloat(), mFillPaint)
        }
    }
}