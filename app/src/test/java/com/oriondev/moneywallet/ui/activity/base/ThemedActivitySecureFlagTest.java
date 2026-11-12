package com.oriondev.moneywallet.ui.activity.base;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.FrameLayout;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.AboutActivity;
import com.oriondev.moneywallet.ui.activity.LockActivity;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ThemedActivitySecureFlagTest {

    private ActivityController<? extends Activity> mController;

    @After
    public void tearDown() {
        if (mController != null && !mController.get().isDestroyed()) {
            mController.pause().stop().destroy();
        }
    }

    @Test
    public void aScreenBehindTheLockIsSecureWhileTheLockIsOn() {
        for (LockMode mode : new LockMode[]{LockMode.PIN, LockMode.SEQUENCE, LockMode.FINGERPRINT}) {
            PreferenceManager.setCurrentLockMode(mode);
            assertTrue(mode.name(), isSecure(resume(Robolectric.buildActivity(AboutActivity.class))));
            mController.pause().stop().destroy();
        }
    }

    @Test
    public void aScreenBehindTheLockIsNotSecureWhileTheLockIsOff() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        assertFalse(isSecure(resume(Robolectric.buildActivity(AboutActivity.class))));
    }

    @Test
    public void theLockScreenIsSecureWhileTheLockIsOn() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        Intent intent = new Intent(context(), LockActivity.class);
        assertTrue(isSecure(resume(Robolectric.buildActivity(LockActivity.class, intent))));
    }

    @Test
    public void theLockScreenIsNotSecureWhileTheLockIsOff() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        Intent intent = new Intent(context(), LockActivity.class)
                .putExtra(LockActivity.MODE, LockMode.PIN)
                .putExtra(LockActivity.ACTION, LockActivity.ACTION_ENABLE);
        assertFalse(isSecure(resume(Robolectric.buildActivity(LockActivity.class, intent))));
    }

    @Test
    public void turningTheLockOffClearsTheFlagOnTheNextResume() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        Activity activity = resume(Robolectric.buildActivity(AboutActivity.class));
        assertTrue(isSecure(activity));
        mController.pause();
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        mController.resume();
        assertFalse(isSecure(activity));
    }

    @Test
    public void turningTheLockOnSetsTheFlagOnTheNextResume() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        Activity activity = resume(Robolectric.buildActivity(AboutActivity.class));
        assertFalse(isSecure(activity));
        mController.pause();
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        mController.resume();
        assertTrue(isSecure(activity));
    }

    @Test
    public void aResumeWithTheLockUnchangedLeavesTheWindowAlone() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        CountingActivity activity = (CountingActivity) resume(Robolectric.buildActivity(CountingActivity.class));
        assertFalse(isSecure(activity));
        assertEquals("lock off", 0, changesOverResumes(activity));
        mController.pause();
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        mController.resume();
        assertTrue(isSecure(activity));
        assertEquals("lock on", 0, changesOverResumes(activity));
    }

    private Activity resume(ActivityController<? extends Activity> controller) {
        mController = controller;
        return controller.setup().get();
    }

    private int changesOverResumes(CountingActivity activity) {
        activity.mChanges = 0;
        for (int i = 0; i < 5; i++) {
            mController.pause();
            mController.resume();
        }
        return activity.mChanges;
    }

    private static Context context() {
        return ApplicationProvider.getApplicationContext();
    }

    private static boolean isSecure(Activity activity) {
        return (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
    }

    public static class CountingActivity extends ThemedActivity {

        private int mChanges;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(new FrameLayout(this));
        }

        @Override
        public void onWindowAttributesChanged(WindowManager.LayoutParams params) {
            mChanges++;
            super.onWindowAttributesChanged(params);
        }
    }
}
