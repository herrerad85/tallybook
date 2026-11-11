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

package com.oriondev.moneywallet.ui.behavior

import android.content.Context
import androidx.coordinatorlayout.widget.CoordinatorLayout
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import android.util.AttributeSet
import android.view.View

import com.oriondev.moneywallet.R

/**
 * A custom behavior for the Floating Action Button when it is attached to a CoordinatorLayout. It
 * hides the button while a snackbar covers it and shows it again once the snackbar is gone.
 */
class ScrollingFloatingActionButtonBehavior : CoordinatorLayout.Behavior<FloatingActionButton> {

    private var mHiddenForSnackbar: Boolean = false

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    override fun layoutDependsOn(parent: CoordinatorLayout, fab: FloatingActionButton, dependency: View): Boolean {
        return dependency is Snackbar.SnackbarLayout
    }

    override fun onDependentViewChanged(parent: CoordinatorLayout, fab: FloatingActionButton, dependency: View): Boolean {
        if (fab.visibility == View.VISIBLE && parent.doViewsOverlap(fab, dependency)) {
            mHiddenForSnackbar = true
            fab.hide()
        }
        return false
    }

    override fun onDependentViewRemoved(parent: CoordinatorLayout, fab: FloatingActionButton, dependency: View) {
        if (mHiddenForSnackbar) {
            mHiddenForSnackbar = false
            fab.show()
        }
    }
}
