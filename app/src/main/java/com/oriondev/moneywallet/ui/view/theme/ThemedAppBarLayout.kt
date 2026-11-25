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

package com.oriondev.moneywallet.ui.view.theme;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.WindowInsets;

import androidx.core.graphics.Insets;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.appbar.AppBarLayout;

import com.oriondev.moneywallet.R;

/**
 * Created by andrea on 25/07/18.
 */
public class ThemedAppBarLayout extends AppBarLayout implements ThemeEngine.ThemeConsumer {

    private BackgroundColor mBackgroundColor;

    private boolean mInsetSides;

    private int mBasePaddingLeft;

    private int mBasePaddingRight;

    public ThemedAppBarLayout(Context context) {
        super(context);
        initialize(context, null);
    }

    public ThemedAppBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedAppBarLayout, 0, 0);
        boolean insetTop = true;
        boolean insetSides = true;
        try {
            mBackgroundColor = BackgroundColor.fromValue(typedArray.getInt(R.styleable.ThemedAppBarLayout_theme_backgroundColor, 0));
            insetTop = typedArray.getBoolean(R.styleable.ThemedAppBarLayout_systemBarInsetTop, true);
            insetSides = typedArray.getBoolean(R.styleable.ThemedAppBarLayout_systemBarInsetSides, true);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            typedArray.recycle();
        }
        mInsetSides = insetSides;
        mBasePaddingLeft = getPaddingLeft();
        mBasePaddingRight = getPaddingRight();
        if (insetTop) {
            // AppBarLayout does the status bar itself when this is set, it grows by the inset,
            // offsets its children down by it, and subtracts it from the range it will scroll. That
            // last part is why this is not padding of ours. A bar with scroll flags carries its own
            // padding away as it collapses, which slid the toolbar under the status bar on the
            // tabbed screens; the inset the library keeps is pinned and cannot scroll off.
            setFitsSystemWindows(true);
        }
    }

    /**
     * The sides are still ours, because the library only handles the top. Done around the dispatch
     * instead of through a listener, the library installs its own listener in its constructor, and
     * a second one would replace it and take the status bar handling above with it. The background
     * is not shrunk by padding, so the bar still paints its color out to the edge of the display.
     */
    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        if (mInsetSides) {
            Insets bars = WindowInsetsCompat.toWindowInsetsCompat(insets).getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            setPadding(mBasePaddingLeft + bars.left, getPaddingTop(),
                    mBasePaddingRight + bars.right, getPaddingBottom());
        }
        return super.dispatchApplyWindowInsets(insets);
    }

    @Override
    public void onApplyTheme(ITheme theme) {
        if (mBackgroundColor != null) {
            int background = getBackgroundColor(theme);
            setBackgroundColor(background);
        }
    }

    private int getBackgroundColor(ITheme theme) {
        if (mBackgroundColor != null) {
            if (mBackgroundColor == BackgroundColor.COLOR_PRIMARY) {
                return theme.getColorPrimaryBackground();
            } else {
                return theme.getColorPrimaryDark();
            }
        } else {
            return theme.getColorPrimaryBackground();
        }
    }

    public enum BackgroundColor {
        COLOR_PRIMARY(0),
        COLOR_PRIMARY_DARK(1);

        private int mValue;

        BackgroundColor(int value) {
            mValue = value;
        }

        static BackgroundColor fromValue(int value) {
            switch (value) {
                case 0:
                    return COLOR_PRIMARY;
                case 1:
                    return COLOR_PRIMARY_DARK;
                default:
                    return null;
            }
        }
    }
}