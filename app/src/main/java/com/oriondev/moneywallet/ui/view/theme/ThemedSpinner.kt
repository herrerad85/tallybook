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

package com.oriondev.moneywallet.ui.view.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatSpinner

import com.oriondev.moneywallet.R

/**
 * Created by andrea on 20/08/18.
 */
class ThemedSpinner : AppCompatSpinner, ThemeEngine.ThemeConsumer {

    companion object {

        private const val COLOR_BACKGROUND_LIGHT = Color.WHITE
        private val COLOR_BACKGROUND_DARK = Color.parseColor("#424242")
    }

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @SuppressLint("RestrictedApi")
    override fun onApplyTheme(theme: ITheme?) {
        // Widget.AppCompat.Spinner.Underlined draws both the underline and the arrow from one
        // background, abc_spinner_textfield_background_material, whose two layers are the alpha
        // only nine patches abc_textfield_default_mtrl_alpha and abc_spinner_mtrl_am_alpha, so a
        // single background tint colors both. ViewCompat.setBackgroundTintList in androidx.core
        // 1.18.0 is a straight call on to View.setBackgroundTintList, which leaves the AppCompat
        // background helper holding no color, so the color is handed to that helper directly.
        // appcompat 1.8.0 carries @RestrictTo(LIBRARY_GROUP_PREFIX) on that setter, which is what
        // the suppression above is for.
        setSupportBackgroundTintList(ColorStateList.valueOf(theme!!.iconColor))
        setPopupBackgroundDrawable(if (theme.mode == ThemeEngine.Mode.DEEP_DARK)
                AppCompatResources.getDrawable(context, R.drawable.popup_background_deep_dark)
                else ColorDrawable(if (theme.isDark) COLOR_BACKGROUND_DARK else COLOR_BACKGROUND_LIGHT))
    }

    /**
     * The rows of a ThemedSpinner. A Spinner draws the collapsed row and the popup rows through
     * its adapter, so the text color the theme picks has to be applied there rather than on the
     * spinner itself.
     */
    class Adapter : ArrayAdapter<CharSequence> {

        constructor(context: Context?, vararg items: CharSequence) : super(context!!, android.R.layout.simple_spinner_item, items) {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            return paint(super.getView(position, convertView, parent))
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            return paint(super.getDropDownView(position, convertView, parent))
        }

        private fun paint(view: View): View {
            // Read at bind time so the color is right whichever order the theme and the adapter
            // arrive in. Both layouts above resolve to a TextView.
            (view as TextView).setTextColor(ThemeEngine.getTheme().textColorPrimary)
            return view
        }
    }
}
