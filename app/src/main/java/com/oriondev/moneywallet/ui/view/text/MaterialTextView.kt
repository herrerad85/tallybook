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

package com.oriondev.moneywallet.ui.view.text

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.AppCompatTextView
import android.util.AttributeSet

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.cache.TypefaceCache

/**
 * Created by andrea on 01/05/18.
 */
open class MaterialTextView : AppCompatTextView {

    companion object {

        private const val ICON_CONTAINER_SIZE_DP = 48
        private const val ICON_MARGIN_SIZE_DP = 8
        private const val BOTTOM_PADDING_DP = 8
        private const val BOTTOM_LINE_PADDING_DP = 8
        private const val SIDE_PADDING_DP = 8
    }

    private var mIconColor: Int = 0

    private var mMode: Mode? = null
    private var mLeftDrawable: Drawable? = null
    private var mHint: String? = null
    private var mBottomLineEnabled: Boolean = false

    private var mIconMargin: Int = 0
    private var mBottomLineSize: Int = 0

    private var mFloatingLabelPaint: Paint? = null
    private var mBottomLinePaint: Paint? = null

    private var mMeter: Rect? = null

    constructor(context: Context) : super(context) {
        initialize(context, null, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context, attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context, attrs, defStyleAttr)
    }

    private fun initialize(context: Context, attrs: AttributeSet?, defStyleAttr: Int) {
        mFloatingLabelPaint = Paint()
        mFloatingLabelPaint!!.isAntiAlias = true
        mBottomLinePaint = Paint()
        mBottomLinePaint!!.isAntiAlias = true
        mMeter = Rect()
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MaterialTextView, defStyleAttr, 0)
        try {
            onParseAttributes(typedArray)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
        calculatePadding()
    }

    protected fun onParseAttributes(typedArray: TypedArray) {
        mMode = Mode.getMode(typedArray.getInt(R.styleable.MaterialTextView_mtv_mode, Mode.STANDARD.mMode))
        mIconMargin = typedArray.getDimensionPixelSize(R.styleable.MaterialTextView_mtv_leftIconMargin, getPixels(ICON_MARGIN_SIZE_DP))
        mIconColor = typedArray.getColor(R.styleable.MaterialTextView_mtv_leftIconColor, Color.GRAY)
        val leftIcon = typedArray.getResourceId(R.styleable.MaterialTextView_mtv_leftIcon, -1)
        if (leftIcon > 0) {
            mLeftDrawable = Utils.getDrawableCompat(context, leftIcon)
            applyTinting(mLeftDrawable, mIconColor)
        }
        mHint = typedArray.getString(R.styleable.MaterialTextView_mtv_floatingLabelText)
        if (mHint == null) {
            val hint = getHint()
            if (hint != null) {
                mHint = hint.toString()
            }
        }
        mFloatingLabelPaint!!.color = typedArray.getColor(R.styleable.MaterialTextView_mtv_floatingLabelTextColor, Color.GRAY)
        mFloatingLabelPaint!!.textSize = typedArray.getDimension(R.styleable.MaterialTextView_mtv_floatingLabelTextSize, context.resources.getDimension(R.dimen.material_component_floating_label_text_size))
        val floatingLabelTypeface = typedArray.getString(R.styleable.MaterialTextView_mtv_floatingLabelTextTypeface)
        if (floatingLabelTypeface != null) {
            mFloatingLabelPaint!!.typeface = TypefaceCache.get(context, floatingLabelTypeface)
        }
        mBottomLineEnabled = typedArray.getBoolean(R.styleable.MaterialTextView_mtv_bottomLineEnabled, false)
        mBottomLinePaint!!.color = typedArray.getColor(R.styleable.MaterialTextView_mtv_bottomLineColor, Color.GRAY)
        mBottomLineSize = typedArray.getDimensionPixelSize(R.styleable.MaterialTextView_mtv_bottomLineSize, getPixels(1))
    }

    private fun calculatePadding() {
        var paddingLeft = 0
        var paddingTop = 0
        var paddingBottom = 0
        var paddingRight = 0
        // add default 8 dp padding
        paddingLeft += getPixels(SIDE_PADDING_DP)
        paddingRight += getPixels(SIDE_PADDING_DP)
        // calculate padding left / right
        if (mLeftDrawable != null) {
            val extraPadding = getPixels(ICON_CONTAINER_SIZE_DP) + mIconMargin
            if (isRtl()) {
                paddingRight += extraPadding
            } else {
                paddingLeft += extraPadding
            }
        }
        // calculate bottom padding
        paddingBottom += getPixels(BOTTOM_PADDING_DP)
        // calculate top padding
        paddingTop += getPixels(16)
        if (mMode === Mode.FLOATING_LABEL && mHint != null) {
            // append floating label text size
            mFloatingLabelPaint!!.getTextBounds(mHint, 0, mHint!!.length, mMeter)
            paddingTop += mMeter!!.height()
        }
        // apply padding
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    private fun isRtl(): Boolean {
        return Utils.isRtl(resources)
    }

    private fun getPixels(dp: Int): Int {
        return Utils.getPixels(dp, resources).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // measure
        val measuredWidth = getMeasuredWidth()
        val measuredHeight = getMeasuredHeight()
        if (Math.min(measuredWidth, measuredHeight) == 0) {
            // skip drawing
            return
        }
        onDrawLeftIcon(canvas)
        onDrawFloatingLabel(canvas)
        onDrawBottomLine(canvas)
    }

    protected fun onDrawLeftIcon(canvas: Canvas) {
        if (mLeftDrawable != null) {
            val rectTop = paddingTop - getPixels(12)
            val rectLeft = if (isRtl()) (measuredWidth - paddingRight + mIconMargin) else getPixels(SIDE_PADDING_DP)
            val padding = Utils.getPixels(12, resources).toInt()
            val iconTop = rectTop + padding
            val iconLeft = rectLeft + padding + scrollX
            val size = Utils.getPixels(24, resources).toInt()
            mLeftDrawable!!.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
            mLeftDrawable!!.draw(canvas)
        }
    }

    protected fun onDrawFloatingLabel(canvas: Canvas) {
        if (mMode === Mode.FLOATING_LABEL && mHint != null) {
            mFloatingLabelPaint!!.getTextBounds(mHint, 0, mHint!!.length, mMeter)
            val startY = paddingTop + mMeter!!.height()
            val endY = paddingTop - getPixels(8)
            val startX = scrollX + (if (isRtl()) (measuredWidth - paddingRight - mMeter!!.width()) else paddingLeft)
            if (endY < startY) {
                canvas.drawText(mHint!!, startX.toFloat(), endY.toFloat(), mFloatingLabelPaint!!)
            }
        }
    }

    protected fun onDrawBottomLine(canvas: Canvas) {
        if (mBottomLineEnabled) {
            val top = measuredHeight - paddingBottom + getPixels(BOTTOM_LINE_PADDING_DP) - mBottomLineSize
            val left = paddingLeft + scrollX
            val right = scrollX + measuredWidth - paddingRight
            canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    right.toFloat(),
                    (top + mBottomLineSize).toFloat(),
                    mBottomLinePaint!!
            )
        }
    }

    private fun applyTinting(drawable: Drawable?, color: Int) {
        if (drawable != null) {
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    fun setMode(mode: Mode?) {
        if (mMode !== mode) {
            mMode = mode
            calculatePadding()
        }
    }

    fun getMode(): Mode? {
        return mMode
    }

    fun setFloatingLabelColor(color: Int) {
        mFloatingLabelPaint!!.color = color
    }

    fun setBottomLineColor(color: Int) {
        mBottomLinePaint!!.color = color
    }

    fun setLeftIconColor(iconColor: Int) {
        mIconColor = iconColor
        if (mLeftDrawable != null) {
            applyTinting(mLeftDrawable, mIconColor)
        }
    }

    fun setLeftDrawable(@DrawableRes drawable: Int) {
        setLeftDrawable(Utils.getDrawableCompat(context, drawable))
    }

    fun setLeftDrawable(drawable: Drawable?) {
        mLeftDrawable = drawable
        applyTinting(mLeftDrawable, mIconColor)
        calculatePadding()
    }
}
