package com.oriondev.moneywallet.ui.view

import android.content.Context

import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AdvancedRecyclerViewTest {

    companion object {

        private const val LEFT_INSET = 48
        private const val TOP_INSET = 24
        private const val RIGHT_INSET = 96
        private const val BOTTOM_INSET = 32
    }

    private lateinit var context: Context
    private lateinit var bars: WindowInsetsCompat
    private var sidePadding = 0

    @Before
    fun setUp() {
        context = ContextThemeWrapper(ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme)
        bars = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(LEFT_INSET, TOP_INSET, RIGHT_INSET, BOTTOM_INSET))
                .build()
        sidePadding = context.resources.getDimensionPixelSize(R.dimen.view_advanced_recycler_empty_text_side_padding)
    }

    @Test
    fun theEmptyTextMovesClearOfANavigationBarOnEitherSide() {
        val attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.systemBarInsetBottom, "true").build()
        val list = AdvancedRecyclerView(context, attributes)
        val text = list.getTextView()!!
        val top = text.paddingTop
        val bottom = text.paddingBottom
        ViewCompat.dispatchApplyWindowInsets(list, bars)
        assertEquals(sidePadding + LEFT_INSET, text.paddingLeft)
        assertEquals(sidePadding + RIGHT_INSET, text.paddingRight)
        assertEquals(top, text.paddingTop)
        assertEquals(bottom, text.paddingBottom)
    }

    @Test
    fun aListThatLeavesTheSidesKeepsTheEmptyTextWhereTheLayoutPutIt() {
        val attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.systemBarInsetBottom, "true")
                .addAttribute(R.attr.systemBarInsetSides, "false").build()
        val list = AdvancedRecyclerView(context, attributes)
        val text = list.getTextView()!!
        ViewCompat.dispatchApplyWindowInsets(list, bars)
        assertEquals(sidePadding, text.paddingLeft)
        assertEquals(sidePadding, text.paddingRight)
    }

    @Test
    fun theEmptyTextMovesClearOfACutoutOnOneSideAndANavigationBarOnTheOther() {
        val attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.systemBarInsetBottom, "true").build()
        val list = AdvancedRecyclerView(context, attributes)
        val text = list.getTextView()!!
        val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(LEFT_INSET, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, RIGHT_INSET, 0))
                .build()
        ViewCompat.dispatchApplyWindowInsets(list, insets)
        assertEquals(sidePadding + LEFT_INSET, text.paddingLeft)
        assertEquals(sidePadding + RIGHT_INSET, text.paddingRight)
    }

    @Test
    fun aListBuiltWithoutAttributesKeepsTheEmptyTextWhereTheLayoutPutIt() {
        val list = AdvancedRecyclerView(context)
        val text = list.getTextView()!!
        ViewCompat.dispatchApplyWindowInsets(list, bars)
        assertEquals(sidePadding, text.paddingLeft)
        assertEquals(sidePadding, text.paddingRight)
    }

    @Test
    fun theEmptyTextFollowsTheBarsAndTheCutoutWhenTheyChangeSides() {
        val attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.systemBarInsetBottom, "true").build()
        val list = AdvancedRecyclerView(context, attributes)
        val text = list.getTextView()!!
        ViewCompat.dispatchApplyWindowInsets(list, bars)
        val insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(RIGHT_INSET, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(0, 0, LEFT_INSET, 0))
                .build()
        ViewCompat.dispatchApplyWindowInsets(list, insets)
        assertEquals(sidePadding + RIGHT_INSET, text.paddingLeft)
        assertEquals(sidePadding + LEFT_INSET, text.paddingRight)
    }
}
