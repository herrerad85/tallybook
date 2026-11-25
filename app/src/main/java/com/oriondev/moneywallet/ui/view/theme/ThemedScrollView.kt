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
import android.widget.ScrollView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.utils.SystemBars;

/**
 * Created by andrea on 20/08/18.
 */
public class ThemedScrollView extends ScrollView implements ThemeEngine.ThemeConsumer {

    public ThemedScrollView(Context context) {
        super(context);
    }

    public ThemedScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize(context, attrs);
    }

    public ThemedScrollView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context, attrs);
    }

    public ThemedScrollView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize(context, attrs);
    }

    private void initialize(Context context, AttributeSet attrs) {
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.ThemedScrollView, 0, 0);
        boolean insetTop;
        boolean insetSides;
        boolean insetBottom;
        try {
            insetTop = typedArray.getBoolean(R.styleable.ThemedScrollView_systemBarInsetTop, false);
            insetSides = typedArray.getBoolean(R.styleable.ThemedScrollView_systemBarInsetSides, true);
            insetBottom = typedArray.getBoolean(R.styleable.ThemedScrollView_systemBarInsetBottom, false);
        } finally {
            typedArray.recycle();
        }
        if (insetTop || insetBottom) {
            SystemBars.pad(this, insetTop, insetSides, insetBottom);
        }
    }

    @Override
    public void onApplyTheme(ITheme theme) {
        EdgeGlowUtil.setEdgeGlowColor(this, theme.getColorPrimary());
        setBackgroundColor(theme.getColorWindowForeground());
    }
}