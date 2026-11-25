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

import android.content.Context
import android.util.AttributeSet

import com.oriondev.moneywallet.ui.view.text.MaterialTextView

/**
 * Created by andrea on 01/05/18.
 */
class ThemedMaterialTextView : MaterialTextView, ThemeEngine.ThemeConsumer {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun onApplyTheme(theme: ITheme?) {
        setLeftIconColor(theme!!.iconColor)
        setTextColor(theme.textColorPrimary)
        setFloatingLabelColor(theme.textColorSecondary)
        setBottomLineColor(theme.textColorSecondary)
    }
}