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

package com.oriondev.moneywallet.ui.view;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.StateListDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;

import com.oriondev.moneywallet.utils.Utils;

/**
 * One color of the color chooser's grid, and the preview beside its hex field. Ported from the
 * CircleView of material-dialogs 0.9.6.0, under the MIT license, minus the parts it drew nothing
 * with.
 */
public class ColorSwatchView extends FrameLayout {

    private static final float RING_WIDTH_DP = 5f;
    private static final float WHITE_RING_WIDTH_DP = 3f;
    private static final float PRESSED_ALPHA = 0.7f;

    private final int mRingWidth;
    private final int mWhiteRingWidth;

    private final Paint mRingPaint;
    private final Paint mWhitePaint;
    private final Paint mFillPaint;

    private boolean mSwatchSelected;

    public ColorSwatchView(Context context) {
        this(context, null, 0);
    }

    public ColorSwatchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorSwatchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mRingWidth = dp(RING_WIDTH_DP);
        mWhiteRingWidth = dp(WHITE_RING_WIDTH_DP);
        mWhitePaint = new Paint();
        mWhitePaint.setAntiAlias(true);
        mWhitePaint.setColor(Color.WHITE);
        mFillPaint = new Paint();
        mFillPaint.setAntiAlias(true);
        mRingPaint = new Paint();
        mRingPaint.setAntiAlias(true);
        setColor(Color.DKGRAY);
        setWillNotDraw(false);
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics());
    }

    @ColorInt
    private static int shiftColor(@ColorInt int color, float by) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= by;
        return Color.HSVToColor(hsv);
    }

    @ColorInt
    private static int translucent(@ColorInt int color) {
        return Color.argb(Math.round(Color.alpha(color) * PRESSED_ALPHA),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    public void setColor(@ColorInt int color) {
        mFillPaint.setColor(color);
        mRingPaint.setColor(shiftColor(color, 0.9f));
        ShapeDrawable pressedCircle = new ShapeDrawable(new OvalShape());
        pressedCircle.getPaint().setColor(translucent(shiftColor(color, 1.1f)));
        StateListDrawable pressed = new StateListDrawable();
        pressed.addState(new int[] {android.R.attr.state_pressed}, pressedCircle);
        ColorStateList ripple = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_pressed}},
                new int[] {shiftColor(color, 1.1f)}
        );
        setForeground(new RippleDrawable(ripple, pressed, null));
        setContentDescription(Utils.getHexColor(color));
        invalidate();
    }

    // The ring is the chooser's own flag, not the framework's selected flag, which GridView also
    // writes for its own reasons.
    public void setSwatchSelected(boolean selected) {
        mSwatchSelected = selected;
        invalidate();
    }

    public boolean isSwatchSelected() {
        return mSwatchSelected;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setSelected(mSwatchSelected);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredWidth(), getMeasuredWidth());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int centerX = getMeasuredWidth() / 2;
        int centerY = getMeasuredHeight() / 2;
        int outerRadius = getMeasuredWidth() / 2;
        if (mSwatchSelected) {
            int whiteRadius = outerRadius - mRingWidth;
            canvas.drawCircle(centerX, centerY, outerRadius, mRingPaint);
            canvas.drawCircle(centerX, centerY, whiteRadius, mWhitePaint);
            canvas.drawCircle(centerX, centerY, whiteRadius - mWhiteRingWidth, mFillPaint);
        } else {
            canvas.drawCircle(centerX, centerY, outerRadius, mFillPaint);
        }
    }
}
