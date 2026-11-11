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

package com.oriondev.moneywallet.ui.preference;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;
import android.util.AttributeSet;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

/**
 * Created by andrea on 15/04/18.
 */
public class ThemedPreference extends Preference {

    public ThemedPreference(Context context) {
        super(context);
        initialize();
    }

    public ThemedPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public ThemedPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    private void initialize() {
        setLayoutResource(R.layout.layout_preference_material_design);
    }

    /**
     * Repaints the row after the library has bound it.
     * <p>
     * PreferenceViewHolder copies the title's colors as the row is built and PreferenceGroupAdapter
     * puts that copy back before every bind, so a row built in one theme mode and rebound in
     * another loses its title to the mode it was built under. Deep dark made that unreadable,
     * because the copy was black and the window behind it was too.
     */
    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        ThemeEngine.applyTheme(holder.itemView, true);
    }
}
