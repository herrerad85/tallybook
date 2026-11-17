package com.oriondev.moneywallet.ui.behavior;

import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.oriondev.moneywallet.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

/**
 * Shows a snackbar over a button carrying the behavior, in an activity on the app theme, and
 * lets the coordinator dispatch to the behavior on its own.
 */
@RunWith(RobolectricTestRunner.class)
public class ScrollingFloatingActionButtonBehaviorTest {

    private ActivityController<AppCompatActivity> mController;
    private CoordinatorLayout mCoordinator;
    private FloatingActionButton mFab;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(AppCompatActivity.class).setup();
        AppCompatActivity activity = mController.get();
        mCoordinator = new CoordinatorLayout(activity);
        mFab = new FloatingActionButton(activity);
        CoordinatorLayout.LayoutParams params = new CoordinatorLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.setBehavior(new ScrollingFloatingActionButtonBehavior(activity, null));
        mCoordinator.addView(mFab, params);
        activity.setContentView(mCoordinator);
        settle();
    }

    @After
    public void tearDown() {
        mController.close();
    }

    private void settle() {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));
    }

    private View showSnackbar() {
        Snackbar snackbar = Snackbar.make(mCoordinator,
                R.string.snackbar_message_model_transaction_added, Snackbar.LENGTH_INDEFINITE);
        snackbar.show();
        settle();
        View view = snackbar.getView();
        assertEquals("the snackbar never reached the coordinator", mCoordinator, view.getParent());
        return view;
    }

    private void removeSnackbar(View view) {
        mCoordinator.removeView(view);
        settle();
    }

    @Test
    public void aSnackbarOverTheButtonHidesIt() {
        assertEquals(View.VISIBLE, mFab.getVisibility());
        showSnackbar();
        assertEquals("the button is still visible under the snackbar",
                View.GONE, mFab.getVisibility());
    }

    @Test
    public void theButtonComesBackOnceTheSnackbarIsGone() {
        removeSnackbar(showSnackbar());
        assertEquals(View.VISIBLE, mFab.getVisibility());
        assertEquals("the button stayed shrunk after the snackbar left", 1f, mFab.getScaleX(), 0f);
        assertEquals(1f, mFab.getScaleY(), 0f);
    }

    @Test
    public void aButtonTheScreenHidBeforeTheSnackbarStaysHidden() {
        mFab.setVisibility(View.GONE);
        removeSnackbar(showSnackbar());
        assertEquals("the snackbar leaving showed a button the screen does not want",
                View.GONE, mFab.getVisibility());
    }
}
