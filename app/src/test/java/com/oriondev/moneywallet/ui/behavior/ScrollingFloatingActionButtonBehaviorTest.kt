package com.oriondev.moneywallet.ui.behavior

import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup

import androidx.appcompat.app.AppCompatActivity
import androidx.coordinatorlayout.widget.CoordinatorLayout

import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import com.oriondev.moneywallet.R

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController

import java.time.Duration

import org.junit.Assert.assertEquals
import org.robolectric.Shadows.shadowOf

/**
 * Shows a snackbar over a button carrying the behavior, in an activity on the app theme, and
 * lets the coordinator dispatch to the behavior on its own.
 */
@RunWith(RobolectricTestRunner::class)
class ScrollingFloatingActionButtonBehaviorTest {

    private lateinit var mController: ActivityController<AppCompatActivity>
    private lateinit var mCoordinator: CoordinatorLayout
    private lateinit var mFab: FloatingActionButton

    @Before
    fun setUp() {
        mController = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val activity = mController.get()
        mCoordinator = CoordinatorLayout(activity)
        mFab = FloatingActionButton(activity)
        val params = CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        params.gravity = Gravity.BOTTOM or Gravity.END
        params.behavior = ScrollingFloatingActionButtonBehavior(activity, null)
        mCoordinator.addView(mFab, params)
        activity.setContentView(mCoordinator)
        settle()
    }

    @After
    fun tearDown() {
        mController.close()
    }

    private fun settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
    }

    private fun showSnackbar(): View {
        val snackbar = Snackbar.make(mCoordinator,
                R.string.snackbar_message_model_transaction_added, Snackbar.LENGTH_INDEFINITE)
        snackbar.show()
        settle()
        val view = snackbar.view
        assertEquals("the snackbar never reached the coordinator", mCoordinator, view.parent)
        return view
    }

    private fun removeSnackbar(view: View) {
        mCoordinator.removeView(view)
        settle()
    }

    @Test
    fun aSnackbarOverTheButtonHidesIt() {
        assertEquals(View.VISIBLE, mFab.visibility)
        showSnackbar()
        assertEquals("the button is still visible under the snackbar",
                View.GONE, mFab.visibility)
    }

    @Test
    fun theButtonComesBackOnceTheSnackbarIsGone() {
        removeSnackbar(showSnackbar())
        assertEquals(View.VISIBLE, mFab.visibility)
        assertEquals("the button stayed shrunk after the snackbar left", 1f, mFab.scaleX, 0f)
        assertEquals(1f, mFab.scaleY, 0f)
    }

    @Test
    fun aButtonTheScreenHidBeforeTheSnackbarStaysHidden() {
        mFab.visibility = View.GONE
        removeSnackbar(showSnackbar())
        assertEquals("the snackbar leaving showed a button the screen does not want",
                View.GONE, mFab.visibility)
    }
}
