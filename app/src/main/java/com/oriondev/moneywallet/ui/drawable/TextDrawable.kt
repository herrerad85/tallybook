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

package com.oriondev.moneywallet.ui.drawable

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.OvalShape
import android.graphics.drawable.shapes.RectShape
import android.graphics.drawable.shapes.RoundRectShape

import java.util.Locale

/**
 * This class has been imported from this repository: https://github.com/amulyakhare/TextDrawable
 * It has been modified to correctly recycle Typefaces and not waste memory.
 */
class TextDrawable : ShapeDrawable {

    private val textPaint: Paint
    private val borderPaint: Paint
    private val text: String?
    private val color: Int
    private val shape: RectShape
    private val height: Int
    private val width: Int
    private val fontSize: Int
    private val radius: Float
    private val borderThickness: Int

    private constructor(shape: RectShape, height: Int, width: Int, radius: Float, text: String?, toUpperCase: Boolean,
                        color: Int, fontSize: Int, textColor: Int, isBold: Boolean, font: Typeface?,
                        borderThickness: Int) : super(shape) {
        // shape properties
        this.shape = shape
        this.height = height
        this.width = width
        this.radius = radius
        // text and color
        this.text = if (toUpperCase) text!!.uppercase(Locale.getDefault()) else text
        this.color = color
        // text paint settings
        this.fontSize = fontSize
        textPaint = Paint()
        textPaint.color = textColor
        textPaint.isAntiAlias = true
        textPaint.isFakeBoldText = isBold
        textPaint.style = Paint.Style.FILL
        textPaint.typeface = font
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.strokeWidth = borderThickness.toFloat()
        // border paint settings
        this.borderThickness = borderThickness
        borderPaint = Paint()
        borderPaint.color = getDarkerShade(color)
        borderPaint.style = Paint.Style.STROKE
        borderPaint.strokeWidth = this.borderThickness.toFloat()
        // drawable paint color
        val paint = getPaint()
        paint.color = color
    }

    private fun getDarkerShade(color: Int): Int {
        return Color.rgb((SHADE_FACTOR * Color.red(color)).toInt(),
                (SHADE_FACTOR * Color.green(color)).toInt(),
                (SHADE_FACTOR * Color.blue(color)).toInt())
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        val r = bounds
        // draw border
        if (borderThickness > 0) {
            drawBorder(canvas)
        }
        val count = canvas.save()
        canvas.translate(r.left.toFloat(), r.top.toFloat())
        // draw text
        val width = r.width() //this.width < 0 ? r.width() : this.width;
        val height = r.height() //this.height < 0 ? r.height() : this.height;
        val fontSize = if (this.fontSize < 0) (Math.min(width, height) / 2) else this.fontSize
        textPaint.textSize = fontSize.toFloat()
        canvas.drawText(text!!, (width / 2).toFloat(), height / 2 - ((textPaint.descent() + textPaint.ascent()) / 2), textPaint)
        canvas.restoreToCount(count)
    }

    private fun drawBorder(canvas: Canvas) {
        val rect = RectF(bounds)
        rect.inset((borderThickness / 2).toFloat(), (borderThickness / 2).toFloat())
        if (shape is OvalShape) {
            canvas.drawOval(rect, borderPaint)
        }
        else if (shape is RoundRectShape) {
            canvas.drawRoundRect(rect, radius, radius, borderPaint)
        }
        else {
            canvas.drawRect(rect, borderPaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        textPaint.alpha = alpha
    }

    override fun setColorFilter(cf: ColorFilter?) {
        textPaint.colorFilter = cf
    }

    override fun getOpacity(): Int {
        return PixelFormat.TRANSLUCENT
    }

    override fun getIntrinsicWidth(): Int {
        return width
    }

    override fun getIntrinsicHeight(): Int {
        return height
    }

    companion object {

        private const val SHADE_FACTOR = 0.9f

        @JvmStatic
        fun builder(): IShapeBuilder {
            return Builder()
        }
    }

    class Builder : IConfigBuilder, IShapeBuilder, IBuilder {

        private var text: String?

        private var color: Int

        private var borderThickness: Int

        private var width: Int

        private var height: Int

        private var font: Typeface?

        private var shape: RectShape

        @JvmField
        var textColor: Int

        private var fontSize: Int

        private var isBold: Boolean

        private var toUpperCase: Boolean

        @JvmField
        var radius: Float = 0f

        internal constructor() {
            text = ""
            color = Color.GRAY
            textColor = Color.WHITE
            borderThickness = 0
            width = -1
            height = -1
            shape = RectShape()
            font = Typeface.create("sans-serif-light", Typeface.NORMAL)
            fontSize = -1
            isBold = false
            toUpperCase = false
        }

        override fun width(width: Int): IConfigBuilder {
            this.width = width
            return this
        }

        override fun height(height: Int): IConfigBuilder {
            this.height = height
            return this
        }

        override fun textColor(color: Int): IConfigBuilder {
            this.textColor = color
            return this
        }

        override fun withBorder(thickness: Int): IConfigBuilder {
            this.borderThickness = thickness
            return this
        }

        override fun useFont(font: Typeface?): IConfigBuilder {
            this.font = font
            return this
        }

        override fun fontSize(size: Int): IConfigBuilder {
            this.fontSize = size
            return this
        }

        override fun bold(): IConfigBuilder {
            this.isBold = true
            return this
        }

        override fun toUpperCase(): IConfigBuilder {
            this.toUpperCase = true
            return this
        }

        override fun beginConfig(): IConfigBuilder {
            return this
        }

        override fun endConfig(): IShapeBuilder {
            return this
        }

        override fun rect(): IBuilder {
            this.shape = RectShape()
            return this
        }

        override fun round(): IBuilder {
            this.shape = OvalShape()
            return this
        }

        override fun roundRect(radius: Int): IBuilder {
            this.radius = radius.toFloat()
            val radii = floatArrayOf(radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat(), radius.toFloat())
            this.shape = RoundRectShape(radii, null, null)
            return this
        }

        override fun buildRect(text: String?, color: Int): TextDrawable {
            rect()
            return build(text, color)
        }

        override fun buildRoundRect(text: String?, color: Int, radius: Int): TextDrawable {
            roundRect(radius)
            return build(text, color)
        }

        override fun buildRound(text: String?, color: Int): TextDrawable {
            round()
            return build(text, color)
        }

        override fun build(text: String?, color: Int): TextDrawable {
            this.color = color
            this.text = text
            return TextDrawable(shape, height, width, radius, this.text, toUpperCase, this.color, fontSize, textColor,
                    isBold, font, borderThickness)
        }
    }

    interface IConfigBuilder {

        fun width(width: Int): IConfigBuilder

        fun height(height: Int): IConfigBuilder

        fun textColor(color: Int): IConfigBuilder

        fun withBorder(thickness: Int): IConfigBuilder

        fun useFont(font: Typeface?): IConfigBuilder

        fun fontSize(size: Int): IConfigBuilder

        fun bold(): IConfigBuilder

        fun toUpperCase(): IConfigBuilder

        fun endConfig(): IShapeBuilder
    }

    interface IBuilder {

        fun build(text: String?, color: Int): TextDrawable
    }

    interface IShapeBuilder {

        fun beginConfig(): IConfigBuilder

        fun rect(): IBuilder

        fun round(): IBuilder

        fun roundRect(radius: Int): IBuilder

        fun buildRect(text: String?, color: Int): TextDrawable

        fun buildRoundRect(text: String?, color: Int, radius: Int): TextDrawable

        fun buildRound(text: String?, color: Int): TextDrawable
    }
}
