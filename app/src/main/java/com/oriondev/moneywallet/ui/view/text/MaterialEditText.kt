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

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import android.text.Editable
import android.text.TextPaint
import android.text.TextUtils
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.cache.TypefaceCache

import java.util.ArrayList

/**
 * Created by andrea on 27/01/18.
 */
open class MaterialEditText : androidx.appcompat.widget.AppCompatEditText, ValueAnimator.AnimatorUpdateListener, View.OnTouchListener {

    companion object {

        private const val ANIMATION_DURATION = 300
        private const val ICON_CONTAINER_SIZE_DP = 48
        private const val ICON_MARGIN_SIZE_DP = 8
        private const val NO_ERROR_BOTTOM_PADDING_DP = 16
        private const val BOTTOM_LINE_PADDING_DP = 8
        private const val SIDE_PADDING_DP = 8
        private const val CANCEL_BUTTON_PADDING_DP = 36
        private const val CANCEL_BUTTON_SIZE_DP = 24
    }

    private var mIconColorNormal: Int = 0
    private var mIconColorFocused: Int = 0
    private var mFloatingLabelColorNormal: Int = 0
    private var mFloatingLabelColorFocused: Int = 0
    private var mBottomLineColorNormal: Int = 0
    private var mBottomLineColorFocused: Int = 0
    private var mBottomLineColorError: Int = 0
    private var mErrorTextColor: Int = 0

    private var mMode: Mode? = null
    private var mLeftDrawable: Array<Drawable>? = null
    private var mHint: String? = null

    private var mError: Boolean = false
    private var mErrorMessage: String? = null
    private var mValidators: MutableList<Validator>? = null
    private var mAnimator: ValueAnimator? = null
    private var mLabelVisible: Boolean = false
    private var mCancelButton: Drawable? = null
    private var mShowCancelButton: Boolean = false

    private var mIconMargin: Int = 0
    private var mDefaultBottomLineSize: Int = 0
    private var mFocusedBottomLineSize: Int = 0

    private var mCancelButtonListener: CancelButtonListener? = null

    private var mFloatingLabelPaint: TextPaint? = null
    private var mErrorTextPaint: Paint? = null
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
        // initialize paints
        mFloatingLabelPaint = TextPaint()
        mFloatingLabelPaint!!.isAntiAlias = true
        mErrorTextPaint = Paint()
        mErrorTextPaint!!.isAntiAlias = true
        mBottomLinePaint = Paint()
        mBottomLinePaint!!.isAntiAlias = true
        mMeter = Rect()
        mLabelVisible = false
        // disable background
        background = null
        // parse attributes from xml
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.MaterialEditText, defStyleAttr, 0)
        try {
            onParseAttributes(typedArray)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            typedArray.recycle()
        }
        // create validator manager and calculate view padding
        mValidators = ArrayList()
        setupValidators()
        calculatePadding()
        setOnTouchListener(this)
    }

    protected fun onParseAttributes(typedArray: TypedArray) {
        mMode = Mode.getMode(typedArray.getInt(R.styleable.MaterialEditText_met_mode, Mode.STANDARD.mMode))
        mIconMargin = typedArray.getDimensionPixelSize(R.styleable.MaterialEditText_met_leftIconMargin, getPixels(ICON_MARGIN_SIZE_DP))
        mIconColorNormal = typedArray.getColor(R.styleable.MaterialEditText_met_leftIconColorNormal, Color.GRAY)
        mIconColorFocused = typedArray.getColor(R.styleable.MaterialEditText_met_leftIconColorFocused, Color.BLACK)
        val leftIcon = typedArray.getResourceId(R.styleable.MaterialEditText_met_leftIcon, -1)
        if (leftIcon > 0) {
            val drawable = Utils.getDrawableCompat(context, leftIcon)
            mLeftDrawable = generateDrawables(drawable)
            applyTinting(mLeftDrawable)
        }
        mHint = typedArray.getString(R.styleable.MaterialEditText_met_floatingLabelText)
        if (mHint == null) {
            val hint = getHint()
            if (hint != null) {
                mHint = hint.toString()
            }
        }
        mFloatingLabelColorNormal = typedArray.getColor(R.styleable.MaterialEditText_met_floatingLabelTextColorNormal, Color.GRAY)
        mFloatingLabelColorFocused = typedArray.getColor(R.styleable.MaterialEditText_met_floatingLabelTextColorFocused, Color.BLACK)
        mFloatingLabelPaint!!.textSize = typedArray.getDimension(R.styleable.MaterialEditText_met_floatingLabelTextSize, context.resources.getDimension(R.dimen.material_component_floating_label_text_size))
        val floatingLabelTypeface = typedArray.getString(R.styleable.MaterialEditText_met_floatingLabelTextTypeface)
        if (floatingLabelTypeface != null) {
            mFloatingLabelPaint!!.typeface = TypefaceCache.get(context, floatingLabelTypeface)
        }
        mBottomLineColorNormal = typedArray.getColor(R.styleable.MaterialEditText_met_bottomLineColorNormal, Color.GRAY)
        mBottomLineColorFocused = typedArray.getColor(R.styleable.MaterialEditText_met_bottomLineColorFocused, Color.BLACK)
        mBottomLineColorError = typedArray.getColor(R.styleable.MaterialEditText_met_bottomLineColorError, Color.RED)
        mDefaultBottomLineSize = typedArray.getDimensionPixelSize(R.styleable.MaterialEditText_met_bottomLineColorUnfocusedSize, getPixels(1))
        mFocusedBottomLineSize = typedArray.getDimensionPixelSize(R.styleable.MaterialEditText_met_bottomLineColorFocusedSize, getPixels(2))
        mErrorTextColor = typedArray.getColor(R.styleable.MaterialEditText_met_errorTextColor, Color.RED)
        mErrorTextPaint!!.textSize = typedArray.getDimension(R.styleable.MaterialEditText_met_errorTextSize, context.resources.getDimension(R.dimen.material_component_bottom_line_error_text_size))
        val errorTypeface = typedArray.getString(R.styleable.MaterialEditText_met_errorTextTypeface)
        if (errorTypeface != null) {
            mErrorTextPaint!!.typeface = TypefaceCache.get(context, errorTypeface)
        }
        mShowCancelButton = typedArray.getBoolean(R.styleable.MaterialEditText_met_showCancelButton, false)
        val cancelButtonRes = typedArray.getResourceId(R.styleable.MaterialEditText_met_cancelButton, R.drawable.ic_clear_black_24dp)
        mCancelButton = Utils.getDrawableCompat(context, cancelButtonRes)
        applyTinting(mCancelButton, mBottomLineColorNormal)
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
        // calculate cancel button padding
        if (mShowCancelButton) {
            val cancelButtonSize = getPixels(CANCEL_BUTTON_PADDING_DP)
            if (isRtl()) {
                paddingLeft += cancelButtonSize
            } else {
                paddingRight += cancelButtonSize
            }
        }
        // calculate bottom padding
        paddingBottom += getPixels(NO_ERROR_BOTTOM_PADDING_DP)
        if (mError) {
            // append error text size
            mErrorTextPaint!!.getTextBounds(mErrorMessage, 0, mErrorMessage!!.length, mMeter)
            paddingBottom += mMeter!!.height()
        }
        // calculate top padding
        paddingTop += getPixels(16)
        if (mMode === Mode.FLOATING_LABEL && mHint != null) {
            // append floating label text size
            paddingTop += getFloatingLabelHeight()
        }
        // apply padding
        setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom)
    }

    private fun getFloatingLabelHeight(): Int {
        return -mFloatingLabelPaint!!.fontMetricsInt.ascent
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
        onDrawCancelButton(canvas)
        onDrawErrorMessage(canvas)
    }

    protected fun onDrawLeftIcon(canvas: Canvas) {
        if (mLeftDrawable != null) {
            val drawable = if (hasFocus()) mLeftDrawable!![1] else mLeftDrawable!![0]
            val rectTop = paddingTop - getPixels(8)
            val rectLeft = if (isRtl()) (measuredWidth - paddingRight + mIconMargin) else getPixels(SIDE_PADDING_DP)
            val padding = Utils.getPixels(12, resources).toInt()
            val iconTop = rectTop + padding
            val iconLeft = rectLeft + padding + scrollX
            val size = Utils.getPixels(24, resources).toInt()
            drawable.setBounds(iconLeft, iconTop, iconLeft + size, iconTop + size)
            drawable.draw(canvas)
        }
    }

    protected fun onDrawFloatingLabel(canvas: Canvas) {
        if (mMode === Mode.FLOATING_LABEL && mHint != null) {
            mFloatingLabelPaint!!.color = if (hasFocus()) mFloatingLabelColorFocused else mFloatingLabelColorNormal
            val label = TextUtils.ellipsize(mHint, mFloatingLabelPaint, (measuredWidth - paddingLeft - paddingRight + (if (mShowCancelButton) getPixels(CANCEL_BUTTON_PADDING_DP) else 0)).toFloat(), TextUtils.TruncateAt.END).toString()
            val startY = paddingTop + getFloatingLabelHeight()
            val endY = paddingTop - getPixels(8)
            val startX = scrollX + (if (isRtl()) (measuredWidth - paddingRight - mFloatingLabelPaint!!.measureText(label).toInt()) else paddingLeft)
            val textY = startY - ((startY - endY) * getAnimatedY()).toInt()
            if (textY < startY) {
                canvas.drawText(label, startX.toFloat(), textY.toFloat(), mFloatingLabelPaint!!)
            }
        }
    }

    protected fun onDrawBottomLine(canvas: Canvas) {
        if (mError) {
            mBottomLinePaint!!.color = mBottomLineColorError
        } else if (hasFocus()) {
            mBottomLinePaint!!.color = mBottomLineColorFocused
        } else {
            mBottomLinePaint!!.color = mBottomLineColorNormal
        }
        val lineHeight = if (hasFocus()) mFocusedBottomLineSize else mDefaultBottomLineSize
        val top = measuredHeight - paddingBottom + getPixels(BOTTOM_LINE_PADDING_DP) - lineHeight
        // the cancel button reserves CANCEL_BUTTON_PADDING_DP inside the padding on the side it is
        // drawn, and the line reaches back over that to keep the width it has with no button shown
        val left = paddingLeft + scrollX - (if (mShowCancelButton && isRtl()) getPixels(CANCEL_BUTTON_PADDING_DP) else 0)
        val right = scrollX + measuredWidth - paddingRight + (if (mShowCancelButton && !isRtl()) getPixels(CANCEL_BUTTON_PADDING_DP) else 0)
        canvas.drawRect(
                left.toFloat(),
                top.toFloat(),
                right.toFloat(),
                (top + lineHeight).toFloat(),
                mBottomLinePaint!!
        )
    }

    protected fun onDrawCancelButton(canvas: Canvas) {
        if (mShowCancelButton && mCancelButton != null && text!!.length > 0) {
            val iconSize = getPixels(CANCEL_BUTTON_SIZE_DP)
            val iconPadding = getPixels(CANCEL_BUTTON_PADDING_DP)
            val internalMargin = (iconPadding - iconSize) / 2
            val left = scrollX + internalMargin + (if (isRtl()) (paddingLeft - iconPadding) else (measuredWidth - paddingRight))
            val top = paddingTop
            mCancelButton!!.setBounds(left, top, left + iconSize, top + iconSize)
            mCancelButton!!.draw(canvas)
        }
    }

    protected fun onDrawErrorMessage(canvas: Canvas) {
        if (mError) {
            mErrorTextPaint!!.color = mErrorTextColor
            mErrorTextPaint!!.getTextBounds(mErrorMessage, 0, mErrorMessage!!.length, mMeter)
            val startX = if (isRtl()) (measuredWidth - paddingRight - mMeter!!.width()) else paddingLeft
            val startY = measuredHeight - getPixels(4)
            canvas.drawText(mErrorMessage!!, (startX + scrollX).toFloat(), startY.toFloat(), mErrorTextPaint!!)
        }
    }

    private fun showFloatingLabel() {
        if (mAnimator != null) {
            mAnimator!!.cancel()
        }
        mAnimator = ValueAnimator.ofFloat(0f, 1f)
        mAnimator!!.setDuration(ANIMATION_DURATION.toLong())
        mAnimator!!.addUpdateListener(this)
        mAnimator!!.start()
    }

    private fun hideFloatingLabel() {
        if (mAnimator != null) {
            mAnimator!!.cancel()
        }
        mAnimator = ValueAnimator.ofFloat(1f, 0f)
        mAnimator!!.setDuration(ANIMATION_DURATION.toLong())
        mAnimator!!.addUpdateListener(this)
        mAnimator!!.start()
    }

    private fun getAnimatedY(): Float {
        if (mAnimator != null) {
            return mAnimator!!.animatedValue as Float
        }
        return 0f
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

    /**
     * Sets the label drawn above the text. TextView.setHint cannot do it, since it is final and
     * the label is read from the hint only once, when the view is inflated.
     */
    fun setFloatingLabelText(label: String?) {
        mHint = label
        calculatePadding()
    }

    fun getFloatingLabelText(): String? {
        return mHint
    }

    fun setFloatingLabelColorNormal(floatingLabelColorNormal: Int) {
        mFloatingLabelColorNormal = floatingLabelColorNormal
    }

    fun setFloatingLabelColorFocused(floatingLabelColorFocused: Int) {
        mFloatingLabelColorFocused = floatingLabelColorFocused
    }

    fun setBottomLineColorNormal(bottomLineColorNormal: Int) {
        mBottomLineColorNormal = bottomLineColorNormal
        applyTinting(mCancelButton, bottomLineColorNormal)
    }

    fun setBottomLineColorFocused(bottomLineColorFocused: Int) {
        mBottomLineColorFocused = bottomLineColorFocused
    }

    fun setBottomLineColorError(bottomLineColorError: Int) {
        mBottomLineColorError = bottomLineColorError
    }

    fun setLeftIconColorNormal(iconColorNormal: Int) {
        mIconColorNormal = iconColorNormal
        if (mLeftDrawable != null) {
            mLeftDrawable!![0].setColorFilter(mIconColorNormal, PorterDuff.Mode.SRC_IN)
        }
    }

    fun setLeftIconColorFocused(iconColorFocused: Int) {
        mIconColorFocused = iconColorFocused
        if (mLeftDrawable != null) {
            mLeftDrawable!![1].setColorFilter(mIconColorFocused, PorterDuff.Mode.SRC_IN)
        }
    }

    fun setLeftDrawable(@DrawableRes drawable: Int) {
        setLeftDrawable(Utils.getDrawableCompat(context, drawable))
    }

    fun setLeftDrawable(drawable: Drawable?) {
        mLeftDrawable = generateDrawables(drawable)
        applyTinting(mLeftDrawable)
        calculatePadding()
    }

    fun setTextViewMode(textViewMode: Boolean) {
        setFocusable(!textViewMode)
        setFocusableInTouchMode(!textViewMode)
        setLongClickable(!textViewMode)
    }

    fun getTextAsString(): String {
        return text!!.toString()
    }

    fun setOnCancelButtonClickListener(cancelButtonClickListener: CancelButtonListener?) {
        mCancelButtonListener = cancelButtonClickListener
    }

    private fun generateDrawables(drawable: Drawable?): Array<Drawable>? {
        if (drawable == null) {
            return null
        }
        return arrayOf(drawable, drawable.constantState!!.newDrawable())
    }

    private fun applyTinting(drawables: Array<Drawable>?) {
        if (drawables != null) {
            drawables[0].setColorFilter(mIconColorNormal, PorterDuff.Mode.SRC_ATOP)
            drawables[1].setColorFilter(mIconColorFocused, PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun applyTinting(drawable: Drawable?, color: Int) {
        if (drawable != null) {
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
        }
    }

    private fun setupValidators() {
        addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {

            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validate(s, true)
            }

            override fun afterTextChanged(s: Editable?) {
                val isEmpty = s!!.length == 0
                if (mLabelVisible && isEmpty) {
                    mLabelVisible = false
                    hideFloatingLabel()
                } else if (!mLabelVisible && !isEmpty) {
                    mLabelVisible = true
                    showFloatingLabel()
                }
            }

        })
    }

    fun showError(@StringRes errorMessage: Int) {
        setError(context.getString(errorMessage))
    }

    fun showError(errorMessage: String?) {
        mError = true
        mErrorMessage = errorMessage
        invalidate()
    }

    fun addValidator(validator: Validator) {
        mValidators!!.add(validator)
    }

    fun removeValidator(validator: Validator?) {
        mValidators!!.remove(validator)
    }

    fun removeAllValidators() {
        mValidators!!.clear()
    }

    fun getValidatorCount(): Int {
        return mValidators!!.size
    }

    fun validate(): Boolean {
        validate(text, false)
        return !mError
    }

    protected fun validate(charSequence: CharSequence?, onlyAutoValidator: Boolean) {
        var shouldInvalidate = false
        if (mError) {
            mError = false
            mErrorMessage = null
            shouldInvalidate = true
        }
        for (validator in mValidators!!) {
            if (!onlyAutoValidator || validator.autoValidate()) {
                mError = !validator.isValid(charSequence!!)
                if (mError) {
                    mErrorMessage = validator.getErrorMessage()
                    shouldInvalidate = true
                    break
                }
            }
        }
        if (shouldInvalidate) {
            calculatePadding()
        }
    }

    /**
     * Exposes the label as the hint to accessibility services, since a real hint would size the
     * field to fit it even while it is not shown.
     */
    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo?) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hint == null && mHint != null) {
            info!!.hintText = mHint
        }
    }

    override fun onAnimationUpdate(animation: ValueAnimator) {
        ViewCompat.postInvalidateOnAnimation(this)
    }

    override fun onTouch(v: View?, event: MotionEvent?): Boolean {
        if (event!!.action == MotionEvent.ACTION_DOWN) {
            if (mShowCancelButton && isClearButtonAction(event)) {
                var actionConsumed = false
                if (mCancelButtonListener != null) {
                    actionConsumed = mCancelButtonListener!!.onCancelButtonClick(this@MaterialEditText)
                }
                if (!actionConsumed) {
                    setText("")
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isClearButtonAction(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        val iconPadding = getPixels(CANCEL_BUTTON_PADDING_DP)
        val left = if (isRtl()) (paddingLeft - iconPadding) else (measuredWidth - paddingRight)
        val right = left + iconPadding
        val top = paddingTop
        val bottom = top + iconPadding
        return x >= left && x <= right && y >= top && y <= bottom
    }

    interface CancelButtonListener {

        fun onCancelButtonClick(materialEditText: MaterialEditText): Boolean
    }
}
