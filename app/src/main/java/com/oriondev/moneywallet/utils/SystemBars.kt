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

package com.oriondev.moneywallet.utils

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver

import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Guideline
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * The app draws behind the status bar and the navigation bar, so every surface that must not end up
 * underneath one asks for the insets here.
 *
 * Each call reads the view's current padding or margin once and treats it as the baseline. The
 * listener then sets the value to that baseline plus the inset rather than adding to what is already
 * there: insets are dispatched again on every rotation, keyboard toggle and multi window resize, so
 * an increment would grow without bound.
 *
 * Neither method consumes. Siblings further down the same window need the same insets, and a view
 * that swallowed them would leave the others under a bar.
 */
object SystemBars {

    /**
     * Holds a view clear of the bars it asks about. The background still paints to the edge of the
     * display, since padding does not shrink it, so a toolbar keeps its color running under the
     * status bar while its content moves down.
     *
     * Asking for both edges is how a screen that centers its content stays centered between the
     * bars: padding only one of them would move the content half as far, because the centering
     * follows the padding.
     *
     * The sides are separate because a view is not always at the edge of the window. Two of the
     * app bars sit inside a scroll view that has already taken the sides, and on the wide layouts a
     * scroll view sits inside a card that never reaches the edge; taking the sides again there
     * indents the content twice.
     *
     * clipToPadding goes off whenever the bottom is taken, so rows pass under the navigation bar
     * and are still reachable by scrolling, which is the point of drawing behind it. Padding alone
     * would only move the cut.
     */
    @JvmStatic
    fun pad(view: View?, top: Boolean, sides: Boolean, bottom: Boolean) {
        val baseLeft = view!!.paddingLeft
        val baseTop = view.paddingTop
        val baseRight = view.paddingRight
        val baseBottom = view.paddingBottom
        if (bottom && view is ViewGroup) {
            view.clipToPadding = false
        }
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = barsAndCutout(insets)
            v.setPadding(if (sides) baseLeft + bars.left else baseLeft,
                    if (top) baseTop + bars.top else baseTop,
                    if (sides) baseRight + bars.right else baseRight,
                    if (bottom) baseBottom + bars.bottom else baseBottom)
            insets
        }
        requestInsetsOnAttach(view)
    }

    /**
     * For a control anchored to the bottom of the window that cannot scroll out of the way, a
     * floating action button or a keypad row. Margin and not padding, so the control moves whole
     * instead of growing.
     */
    @JvmStatic
    fun marginBottomAndSides(view: View?) {
        val params = view!!.layoutParams
        if (params !is ViewGroup.MarginLayoutParams) {
            return
        }
        val margins: ViewGroup.MarginLayoutParams = params
        val baseLeft = margins.leftMargin
        val baseRight = margins.rightMargin
        val baseBottom = margins.bottomMargin
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bars = barsAndCutout(insets)
            val current = v.layoutParams as ViewGroup.MarginLayoutParams?
            current!!.leftMargin = baseLeft + bars.left
            current.rightMargin = baseRight + bars.right
            current.bottomMargin = baseBottom + bars.bottom
            v.layoutParams = current
            insets
        }
        requestInsetsOnAttach(view)
    }

    /**
     * View.requestApplyInsets walks up to the window and returns without doing anything when the
     * view has no parent yet, which is every view still inside its own constructor and every view
     * built in onCreateView. So the request is made again on attach, which is the only moment that
     * reliably comes after the view joins a window. Without it a subtree added after the window's
     * first dispatch, a settings category opened from the list for instance, waits for something
     * else to ask before it ever hears about the bars.
     */
    private fun requestInsetsOnAttach(view: View) {
        view.requestApplyInsets()
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {

            override fun onViewAttachedToWindow(attached: View) {
                attached.requestApplyInsets()
            }

            override fun onViewDetachedFromWindow(detached: View) {
                // nothing to undo: the inset listener lives on the view and is still wanted
            }
        })
    }

    /**
     * Keeps a horizontal guideline at the bottom of the toolbar, and never above the distance the
     * layout gives it, counted from the toolbar's own top. The wide layouts start their panel
     * cards partway up an extended app bar, and that toolbar sits below the status bar and grows
     * when a title and subtitle need two lines; a card hung at a fixed distance would cover it. A
     * guideline is not a view that can be constrained to another one, so its distance is set here
     * instead.
     *
     * The listener goes on the app bar and not on the toolbar. The app bar lays the toolbar out
     * first and only then moves it down by the status bar, which calls no listener of the toolbar,
     * so the toolbar's own listener sees its top without the status bar. The app bar's listener
     * runs after both, and a taller toolbar lays the app bar out again too. It sets the guideline
     * in that pass, but the view root drops a layout request made during layout by a view that is
     * gone, and a guideline always is. So the request is made again just before the draw and that
     * draw is skipped, which lays the parent out once more before a frame with the card over the
     * toolbar can be shown.
     */
    @JvmStatic
    fun keepGuidelineBelowToolbar(guideline: Guideline?, toolbar: View?) {
        if (guideline == null || guideline.parent !is View) {
            return
        }
        if (toolbar == null || toolbar.parent !is View
                || (toolbar.parent as View).parent !== guideline.parent) {
            return
        }
        val base = (guideline.layoutParams as ConstraintLayout.LayoutParams?)!!.guideBegin
        val appBar = toolbar.parent as View
        appBar.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val begin = v.top + toolbar.top + Math.max(base, toolbar.height)
            if (begin == (guideline.layoutParams as ConstraintLayout.LayoutParams?)!!.guideBegin) {
                return@addOnLayoutChangeListener
            }
            guideline.setGuidelineBegin(begin)
            val observer = v.viewTreeObserver
            observer.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {

                override fun onPreDraw(): Boolean {
                    // A dead observer has handed its listeners to the view's current one.
                    (if (observer.isAlive) observer else v.viewTreeObserver).removeOnPreDrawListener(this)
                    guideline.requestLayout()
                    return false
                }
            })
        }
    }

    /**
     * A drawer takes the bar or cutout on the side it opens from, so its rows and header are not
     * under a side navigation bar in landscape. The listener goes on the drawer's parent and not on
     * the drawer, whose own listener is material's and is what pads its menu and paints its status
     * bar scrim; setting one on the drawer would replace that.
     */
    @JvmStatic
    fun padDrawerStart(parent: View?, drawer: View?) {
        val baseStart = drawer!!.paddingStart
        ViewCompat.setOnApplyWindowInsetsListener(parent!!) { v, insets ->
            val bars = barsAndCutout(insets)
            val rtl = v.layoutDirection == View.LAYOUT_DIRECTION_RTL
            drawer.setPaddingRelative(baseStart + (if (rtl) bars.right else bars.left),
                    drawer.paddingTop, drawer.paddingEnd, drawer.paddingBottom)
            insets
        }
        requestInsetsOnAttach(parent)
    }

    private fun barsAndCutout(insets: WindowInsetsCompat): Insets {
        return insets.getInsets(WindowInsetsCompat.Type.systemBars()
                or WindowInsetsCompat.Type.displayCutout())
    }
}
