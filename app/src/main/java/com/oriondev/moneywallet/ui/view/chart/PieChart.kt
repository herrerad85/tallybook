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

package com.oriondev.moneywallet.ui.view.chart

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View

import com.oriondev.moneywallet.R

import java.text.NumberFormat

/**
 * Created by andrea on 13/08/18.
 */
open class PieChart : View {

    companion object {

        private const val MIN_DISTANCE = 15f
        private const val MAX_ITERATIONS = 10000

        private const val DEFAULT_HOLE_ENABLED = true
        private const val DEFAULT_SHADOW_ENABLED = true
        private const val DEFAULT_PERCENTAGE_ENABLED = false
        private val DEFAULT_SHADOW_COLOR = Color.parseColor("#40000000")
        private const val DEFAULT_HOLE_COLOR = Color.WHITE
        private const val DEFAULT_LINE_COLOR = Color.GRAY
        private const val DEFAULT_LINE_SIZE = 3f
        private const val PERCENTAGE_TEXT_SIZE_SP = 12f
        private const val LABEL_MARGIN_DP = 2f

        internal fun labelDistance(iconAngle: Float, textDistance: Float, iconDistance: Float, iconRadius: Float, margin: Float, halfAdvance: Float, halfInkHeight: Float): Float {
            val cos = Math.abs(Math.cos(Math.toRadians(iconAngle.toDouble()))).toFloat()
            val sin = Math.abs(Math.sin(Math.toRadians(iconAngle.toDouble()))).toFloat()
            return Math.max(textDistance, iconDistance + iconRadius * (cos + sin) + margin + halfAdvance * cos + halfInkHeight * sin)
        }
    }

    // current data
    private val mColors = intArrayOf(
            Color.rgb(204, 198, 24),
            Color.rgb(229, 163, 25),
            Color.rgb(232, 111, 40),
            Color.rgb(212, 75, 145),
            Color.rgb(117, 96, 165),
            Color.rgb(54, 142, 92),
            Color.rgb(129, 191, 22),
            Color.rgb(224, 184, 26),
            Color.rgb(229, 138, 24),
            Color.rgb(235, 89, 92),
            Color.rgb(167, 78, 160),
            Color.rgb(66, 117, 138),
            Color.rgb(85, 169, 48)
    )

    private var mHoleEnabled: Boolean = false
    private var mShadowEnabled: Boolean = false
    private var mPercentageEnabled: Boolean = false

    private var mRectangle: RectF? = null
    private var mSlicePaint: Paint? = null
    private var mLinePaint: Paint? = null
    private val mTextBounds = Rect()

    private var mIsRunning: Boolean = false
    private var mPieData: PieData? = null

    private var mShadowColor: Int = 0
    private var mHoleColor: Int = 0
    private var mLineColor: Int = 0

    private var mPercentFormat: NumberFormat? = null

    constructor(context: Context) : super(context) {
        initialize(context, null, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        initialize(context, attrs, defStyleAttr, defStyleRes)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        // initialize parameters
        mIsRunning = false
        mRectangle = RectF()
        mSlicePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mLinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        mLinePaint!!.textAlign = Paint.Align.CENTER
        mPercentFormat = NumberFormat.getPercentInstance()
        // obtain attributes
        val a = context.theme.obtainStyledAttributes(attrs, R.styleable.PieChart, defStyleAttr, defStyleRes)
        try {
            mHoleEnabled = a.getBoolean(R.styleable.PieChart_pie_chartHoleEnabled, DEFAULT_HOLE_ENABLED)
            mShadowEnabled = a.getBoolean(R.styleable.PieChart_pie_chartShadowEnabled, DEFAULT_SHADOW_ENABLED)
            mPercentageEnabled = a.getBoolean(R.styleable.PieChart_pie_chartPercentageEnabled, DEFAULT_PERCENTAGE_ENABLED)
            mShadowColor = a.getColor(R.styleable.PieChart_pie_chartShadowColor, DEFAULT_SHADOW_COLOR)
            mHoleColor = a.getColor(R.styleable.PieChart_pie_chartHoleColor, DEFAULT_HOLE_COLOR)
            mLineColor = a.getColor(R.styleable.PieChart_pie_chartLineColor, DEFAULT_LINE_COLOR)
            mLinePaint!!.color = mLineColor
            mLinePaint!!.strokeWidth = a.getFloat(R.styleable.PieChart_pie_chartLineSize, DEFAULT_LINE_SIZE)
        } catch (e: Exception) {
            // initialize variables at default state
            mHoleEnabled = DEFAULT_HOLE_ENABLED
            mShadowEnabled = DEFAULT_SHADOW_ENABLED
            mPercentageEnabled = DEFAULT_PERCENTAGE_ENABLED
            mShadowColor = DEFAULT_SHADOW_COLOR
            mHoleColor = DEFAULT_HOLE_COLOR
            mLineColor = DEFAULT_LINE_COLOR
            mLinePaint!!.strokeWidth = DEFAULT_LINE_SIZE
        } finally {
            if (a != null) {
                a.recycle()
            }
        }
    }

    /**
     * The label drawn on a wedge, in the digits and the percent sign of the language the app
     * is in. Package visible so the test can read it.
     */
    fun percentLabel(sweepAngle: Float): String {
        return mPercentFormat!!.format(Math.round(sweepAngle * 100 / 360f) / 100.0)
    }

    fun setHoleEnabled(enabled: Boolean) {
        mHoleEnabled = enabled
        if (!mHoleEnabled) {
            mShadowEnabled = false
        }
        invalidate()
    }

    fun setLineColor(color: Int) {
        mLineColor = color
    }

    fun setHoleColor(color: Int) {
        mHoleColor = color
    }

    fun setShadowColor(color: Int) {
        mShadowColor = color
    }

    fun setChartData(pieData: PieData?) {
        mPieData = pieData
        if (mPieData == null) {
            mIsRunning = false
            return
        }
        mIsRunning = true
        // calculate chart data async
        Thread(object : Runnable {

            override fun run() {
                // check if some slices should be grouped
                val maxSlices = (360f / MIN_DISTANCE).toInt()
                if (mPieData!!.size() > maxSlices) {
                    // create "other" slice
                    val slicesToRemove = mPieData!!.size() - maxSlices + 1
                    var extraSliceValue = 0L
                    for (i in 0 until slicesToRemove) {
                        // lock for smallest slice
                        var smallSliceIndex = -1
                        var smallSliceValue: Long = 0
                        for (n in 0 until mPieData!!.size()) {
                            val sliceValue = mPieData!!.get(n).getValue()
                            if (smallSliceIndex == -1 || smallSliceValue > sliceValue) {
                                smallSliceIndex = n
                                smallSliceValue = sliceValue
                            }
                        }
                        // remove index and add slice value
                        extraSliceValue += smallSliceValue
                        mPieData!!.remove(smallSliceIndex)
                    }
                    // create extra slice
                    mPieData!!.add(PieSlice("others", extraSliceValue, null, true))
                }
                // calculate slice size
                var totalValue = 0f
                for (slice in mPieData!!.getAllSlices()) {
                    totalValue += slice.getValue()
                }
                // calculate start and sweep angle
                var startAngle = 0f
                for (i in 0 until mPieData!!.size()) {
                    // process slice by slice
                    val sweepAngle = 360f * mPieData!!.get(i).getValue() / totalValue
                    mPieData!!.get(i).setStartAngle(startAngle)
                    mPieData!!.get(i).setIconAngle(startAngle + (sweepAngle / 2))
                    mPieData!!.get(i).setSweepAngle(sweepAngle)
                    // update start angle for the next slice
                    startAngle += sweepAngle
                }
                // move icons if overlaps
                var icons = 1
                var backAngle = 0f
                var i = 1
                while ((icons > 1 || iconOverlaps()) && i < MAX_ITERATIONS) {
                    // get current and previous angle
                    val previousAngle = mPieData!!.get((i - 1) % mPieData!!.size()).getIconAngle() + ((i - 1) / mPieData!!.size() * 360f)
                    val currentAngle = mPieData!!.get(i % mPieData!!.size()).getIconAngle() + (i / mPieData!!.size() * 360f)
                    // compare if overlaps
                    val difference = currentAngle - previousAngle + backAngle
                    if (difference < MIN_DISTANCE) {
                        // icons overlaps
                        val moveDegrees = MIN_DISTANCE - difference
                        mPieData!!.get(i % mPieData!!.size()).increaseIconAngle(moveDegrees)
                        icons += 1
                        backAngle += moveDegrees
                    } else {
                        // icon not overlaps, move everything back if needed
                        if (icons > 1) {
                            val moveAngle = backAngle / icons
                            for (j in (i - 1) downTo (i - icons)) {
                                mPieData!!.get(j % mPieData!!.size()).decreaseIconAngle(moveAngle)
                            }
                            // reset flags
                            icons = 1
                            backAngle = 0f
                        }
                    }
                    i++
                }
                mIsRunning = false
                postInvalidate()
            }

        }).start()
    }

    private fun iconOverlaps(): Boolean {
        // check if icons overlaps
        for (i in 1..mPieData!!.size()) {
            // get current slice angle and the previous one
            val previousAngle = mPieData!!.get((i - 1) % mPieData!!.size()).getIconAngle() + ((i - 1) / mPieData!!.size() * 360f)
            val currentAngle = mPieData!!.get(i % mPieData!!.size()).getIconAngle() + (i / mPieData!!.size() * 360f)
            // check if current angle is not okay
            if ((currentAngle - previousAngle) < MIN_DISTANCE) {
                return true
            }
        }
        return false
    }

    private fun scaleIcon(source: Bitmap, iconRadius: Float): Bitmap {
        val size = (iconRadius * 2).toInt()
        return Bitmap.createScaledBitmap(source, size, size, false)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // get current height param and height size
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)
        // calculate best height from width size
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val bestHeight = (width * 0.75).toInt()
        // set appropriate height size
        val height: Int
        if (heightMode == MeasureSpec.EXACTLY) {
            height = heightSize
        } else if (heightMode == MeasureSpec.AT_MOST) {
            height = Math.min(bestHeight, heightSize)
        } else {
            height = bestHeight
        }
        // pass new measure specific to super class
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
        // need to call super method
        setMeasuredDimension(width, height)
    }

    public override fun onDraw(c: Canvas) {
        // stop if size is 0
        val measuredWidth = getMeasuredWidth()
        val measuredHeight = getMeasuredHeight()
        if (measuredWidth == 0 || measuredHeight == 0) return
        // if data is null or empty return
        if (mPieData == null || mPieData!!.size() == 0 || mIsRunning || mColors.size == 0) return
        // get view size
        val centerX = (measuredWidth / 2).toFloat()
        val centerY = (measuredHeight / 2).toFloat()
        val maxRadius = Math.min(centerX, centerY)
        val chartRadius = maxRadius * 0.5f
        val iconDistance = maxRadius * 0.8f
        val iconRadius = maxRadius * 0.1f
        val textDistance = maxRadius * 0.95f
        mLinePaint!!.color = mLineColor
        mLinePaint!!.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP,
                PERCENTAGE_TEXT_SIZE_SP, resources.displayMetrics)
        // set padding
        mRectangle!!.set(centerX - chartRadius, centerY - chartRadius, centerX + chartRadius, centerY + chartRadius)
        // calculating initial angle
        for (i in 0 until mPieData!!.size()) {
            // retrieve slice object from list
            val pieSlice = mPieData!!.get(i)
            // set slice color and draw pie slice
            val startAngle = pieSlice.getStartAngle()
            val sweepAngle = pieSlice.getSweepAngle()
            mSlicePaint!!.color = mColors[i % mColors.size]
            c.drawArc(mRectangle!!, startAngle, sweepAngle, true, mSlicePaint!!)
            // calculate start point
            val startingAngle = startAngle + (sweepAngle / 2)
            val startX = ((Math.cos(Math.toRadians(startingAngle.toDouble())) * chartRadius).toFloat()) + centerX
            val startY = ((Math.sin(Math.toRadians(startingAngle.toDouble())) * chartRadius).toFloat()) + centerY
            // calculate icon position
            val iconAngle = mPieData!!.get(i).getIconAngle()
            val iconX = ((Math.cos(Math.toRadians(iconAngle.toDouble())) * iconDistance).toFloat()) + centerX
            val iconY = ((Math.sin(Math.toRadians(iconAngle.toDouble())) * iconDistance).toFloat()) + centerY
            // draw line
            c.drawLine(startX, startY, iconX, iconY, mLinePaint!!)
            // draw icon
            val drawable = mPieData!!.get(i).getIcon()
            if (drawable != null) {
                drawable.setBounds((iconX - iconRadius).toInt(), (iconY - iconRadius).toInt(), (iconX + iconRadius).toInt(), (iconY + iconRadius).toInt())
                drawable.draw(c)
                // c.drawBitmap(scaleIcon(bitmap, iconRadius), iconX - iconRadius, iconY - iconRadius, mSlicePaint);
            } else {
                c.drawCircle(iconX, iconY, iconRadius, mSlicePaint!!)
            }
            // draw percentage
            if (mPercentageEnabled) {
                val label = percentLabel(sweepAngle)
                mLinePaint!!.getTextBounds(label, 0, label.length, mTextBounds)
                // half the advance, which is what a centered alignment lays the string out around
                val halfAdvance = mLinePaint!!.measureText(label) / 2
                val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LABEL_MARGIN_DP, resources.displayMetrics)
                val distance = labelDistance(iconAngle, textDistance, iconDistance, iconRadius, margin,
                        halfAdvance, mTextBounds.height() / 2f)
                // calculate center text coordinates, putting the middle of the glyphs on the point.
                // The old offset put the baseline half a text size below it, more than half the
                // height of these glyphs, so the label sat low
                var textX = ((Math.cos(Math.toRadians(iconAngle.toDouble())) * distance).toFloat()) + centerX
                var textY = ((Math.sin(Math.toRadians(iconAngle.toDouble())) * distance).toFloat()) + centerY - ((mTextBounds.top + mTextBounds.bottom) / 2f)
                // a percentage cut off by the edge of the view reads as a different number rather
                // than as a clipped label, so one that would cross an edge is moved in instead, at
                // the cost of sitting nearer its icon. A label wider than the view cannot be saved
                // this way and is still cut, from the trailing end
                textX = Math.max(Math.min(textX, measuredWidth - halfAdvance), halfAdvance)
                textY = Math.max(Math.min(textY, (measuredHeight - mTextBounds.bottom).toFloat()), (-mTextBounds.top).toFloat())
                c.drawText(label, textX, textY, mLinePaint!!)
            }
        }
        // draw hole if required
        if (mHoleEnabled) {
            if (mShadowEnabled) {
                // draw blurred circle
                mSlicePaint!!.color = mShadowColor
                c.drawCircle(centerX, centerY, chartRadius * 0.70f, mSlicePaint!!)
            }
            // draw white hole
            mSlicePaint!!.color = mHoleColor
            c.drawCircle(centerX, centerY, chartRadius * 0.50f, mSlicePaint!!)
        }
    }
}
