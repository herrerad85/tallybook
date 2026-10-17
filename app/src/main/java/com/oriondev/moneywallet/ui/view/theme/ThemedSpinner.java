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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.appcompat.widget.AppCompatSpinner;

import com.oriondev.moneywallet.R;

/**
 * Created by andrea on 20/08/18.
 */
public class ThemedSpinner extends AppCompatSpinner implements ThemeEngine.ThemeConsumer {

    private static final int COLOR_BACKGROUND_LIGHT = Color.WHITE;
    private static final int COLOR_BACKGROUND_DARK = Color.parseColor("#424242");

    public ThemedSpinner(Context context) {
        super(context);
    }

    public ThemedSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ThemedSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("RestrictedApi")
    @Override
    public void onApplyTheme(ITheme theme) {
        // Widget.AppCompat.Spinner.Underlined draws both the underline and the arrow from one
        // background, abc_spinner_textfield_background_material, whose two layers are the alpha
        // only nine patches abc_textfield_default_mtrl_alpha and abc_spinner_mtrl_am_alpha, so a
        // single background tint colors both. ViewCompat.setBackgroundTintList in androidx.core
        // 1.18.0 is a straight call on to View.setBackgroundTintList, which leaves the AppCompat
        // background helper holding no color, so the color is handed to that helper directly.
        // appcompat 1.8.0 carries @RestrictTo(LIBRARY_GROUP_PREFIX) on that setter, which is what
        // the suppression above is for.
        setSupportBackgroundTintList(ColorStateList.valueOf(theme.getIconColor()));
        setPopupBackgroundDrawable(theme.getMode() == ThemeEngine.Mode.DEEP_DARK
                ? AppCompatResources.getDrawable(getContext(), R.drawable.popup_background_deep_dark)
                : new ColorDrawable(theme.isDark() ? COLOR_BACKGROUND_DARK : COLOR_BACKGROUND_LIGHT));
    }

    /**
     * The rows of a ThemedSpinner. A Spinner draws the collapsed row and the popup rows through
     * its adapter, so the text color the theme picks has to be applied there rather than on the
     * spinner itself.
     */
    public static class Adapter extends ArrayAdapter<CharSequence> {

        public Adapter(Context context, CharSequence... items) {
            super(context, android.R.layout.simple_spinner_item, items);
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return paint(super.getView(position, convertView, parent));
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return paint(super.getDropDownView(position, convertView, parent));
        }

        private View paint(View view) {
            // Read at bind time so the color is right whichever order the theme and the adapter
            // arrive in. Both layouts above resolve to a TextView.
            ((TextView) view).setTextColor(ThemeEngine.getTheme().getTextColorPrimary());
            return view;
        }
    }
}
