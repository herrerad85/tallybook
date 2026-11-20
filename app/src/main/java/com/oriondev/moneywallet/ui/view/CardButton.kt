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

package com.oriondev.moneywallet.ui.view

import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.TypedValue

import com.oriondev.moneywallet.R

/**
 * Created by andrea on 08/03/18.
 */
open class CardButton : androidx.appcompat.widget.AppCompatButton {

    constructor(context: Context) : super(context) {
        initialize(resources)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(resources)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(resources)
    }

    private fun initialize(resources: Resources) {
        // setTypeface(TypefaceCache.get(getContext(), TypefaceCache.ROBOTO_MEDIUM));
        setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.material_component_lists_card_button_text_size))
        minWidth = resources.getDimensionPixelSize(R.dimen.material_component_lists_card_button_min_width)
        val internalPadding = resources.getDimensionPixelSize(R.dimen.material_component_lists_card_button_internal_padding)
        setPadding(
                internalPadding,
                paddingTop,
                internalPadding,
                paddingBottom
        )
    }
}