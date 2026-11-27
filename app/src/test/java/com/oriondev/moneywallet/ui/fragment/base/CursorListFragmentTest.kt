package com.oriondev.moneywallet.ui.fragment.base

import android.content.Context

import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.fragment.primary.SavingListFragment
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class CursorListFragmentTest {

    companion object {

        private const val LEFT_INSET = 48
        private const val TOP_INSET = 24
        private const val RIGHT_INSET = 96
        private const val BOTTOM_INSET = 32
    }

    private lateinit var context: Context
    private lateinit var insets: WindowInsetsCompat

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        insets = WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(LEFT_INSET, 0, 0, 0))
                .setInsets(WindowInsetsCompat.Type.statusBars(), Insets.of(0, TOP_INSET, 0, 0))
                .setInsets(WindowInsetsCompat.Type.navigationBars(), Insets.of(0, 0, RIGHT_INSET, BOTTOM_INSET))
                .build()
    }

    @Test
    fun theEmptyTextOfAPageThatFillsTheWindowMovesClearOfTheCutoutAndTheNavigationBar() {
        onList { list, sidePadding ->
            val text = list.getTextView()!!
            val rows = list.getRecyclerView()!!
            val top = text.paddingTop
            val bottom = text.paddingBottom
            val rowsBottom = rows.paddingBottom
            ViewCompat.dispatchApplyWindowInsets(list, insets)
            assertEquals(sidePadding + LEFT_INSET, text.paddingLeft)
            assertEquals(sidePadding + RIGHT_INSET, text.paddingRight)
            assertEquals(top, text.paddingTop)
            assertEquals(bottom, text.paddingBottom)
            assertEquals(rowsBottom + BOTTOM_INSET, rows.paddingBottom)
        }
    }

    @Test
    @Config(qualifiers = "w700dp-h600dp-land")
    fun theEmptyTextOfAPageInACardOnAWideScreenKeepsItsSides() {
        onList { list, sidePadding ->
            val text = list.getTextView()!!
            val rows = list.getRecyclerView()!!
            val rowsBottom = rows.paddingBottom
            ViewCompat.dispatchApplyWindowInsets(list, insets)
            assertEquals(sidePadding, text.paddingLeft)
            assertEquals(sidePadding, text.paddingRight)
            assertEquals(rowsBottom + BOTTOM_INSET, rows.paddingBottom)
        }
    }

    private fun onList(check: (AdvancedRecyclerView, Int) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = SavingListFragment.newInstance(true)
                activity.supportFragmentManager
                        .beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow()
                val sidePadding = activity.resources.getDimensionPixelSize(R.dimen.view_advanced_recycler_empty_text_side_padding)
                check(fragment.view as AdvancedRecyclerView, sidePadding)
            }
        }
    }
}
