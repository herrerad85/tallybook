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

import android.os.Build;
import android.widget.EdgeEffect;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Colors the over scroll glow through public API only. Android 12 and later stretch the content
 * instead of drawing a glow, so none of this is visible there.
 */
final class EdgeGlowUtil {

    /*package-local*/ static void setEdgeGlowColor(@NonNull RecyclerView recyclerView, @ColorInt final int color) {
        // Setting a factory drops the effects the view already holds, so a theme applied later
        // colors the ones it creates next.
        recyclerView.setEdgeEffectFactory(new RecyclerView.EdgeEffectFactory() {

            @NonNull
            @Override
            protected EdgeEffect createEdgeEffect(@NonNull RecyclerView view, int direction) {
                EdgeEffect edgeEffect = super.createEdgeEffect(view, direction);
                edgeEffect.setColor(color);
                return edgeEffect;
            }
        });
    }

    /*package-local*/ static void setEdgeGlowColor(@NonNull ScrollView scrollView, @ColorInt int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scrollView.setEdgeEffectColor(color);
        }
    }

    /*package-local*/ static void setEdgeGlowColor(@NonNull HorizontalScrollView scrollView, @ColorInt int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            scrollView.setEdgeEffectColor(color);
        }
    }
}
