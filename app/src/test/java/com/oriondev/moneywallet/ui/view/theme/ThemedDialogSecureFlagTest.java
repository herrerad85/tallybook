package com.oriondev.moneywallet.ui.view.theme;

import android.app.Dialog;
import android.view.WindowManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;

import com.oriondev.moneywallet.model.LockMode;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowLooper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class ThemedDialogSecureFlagTest {

    private static final String TAG_PICKER = "ThemedDialogSecureFlagTest::Picker";

    private ActivityController<AppCompatActivity> mController;

    @Before
    public void setUp() {
        mController = Robolectric.buildActivity(AppCompatActivity.class).setup();
    }

    @After
    public void tearDown() {
        mController.close();
    }

    @Test
    public void anAlertDialogIsSecureWhileTheLockIsOn() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        assertTrue(isSecure(ThemedDialog.buildMaterialDialog(mController.get()).create()));
    }

    @Test
    public void anAlertDialogIsNotSecureWhileTheLockIsOff() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        assertFalse(isSecure(ThemedDialog.buildMaterialDialog(mController.get()).create()));
    }

    @Test
    public void theDatePickerIsSecureWhileTheLockIsOn() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        assertTrue(isSecure(showPicker("DatePicker").getDialog()));
    }

    @Test
    public void theDatePickerIsNotSecureWhileTheLockIsOff() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        assertFalse(isSecure(showPicker("DatePicker").getDialog()));
    }

    @Test
    public void theTimePickerIsSecureWhileTheLockIsOn() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        assertTrue(isSecure(showPicker("TimePicker").getDialog()));
    }

    @Test
    public void theTimePickerIsNotSecureWhileTheLockIsOff() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE);
        assertFalse(isSecure(showPicker("TimePicker").getDialog()));
    }

    @Test
    public void theDatePickerIsStillSecureAfterARecreate() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        DialogFragment shown = showPicker("DatePicker");
        mController.recreate();
        DialogFragment rebuilt = shownPicker("DatePicker");
        assertNotSame(shown, rebuilt);
        assertTrue(isSecure(rebuilt.getDialog()));
    }

    @Test
    public void theTimePickerIsStillSecureAfterARecreate() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN);
        DialogFragment shown = showPicker("TimePicker");
        mController.recreate();
        DialogFragment rebuilt = shownPicker("TimePicker");
        assertNotSame(shown, rebuilt);
        assertTrue(isSecure(rebuilt.getDialog()));
    }

    private DialogFragment showPicker(String picker) {
        AppCompatActivity activity = mController.get();
        DateTimePicker dateTimePicker = DateTimePicker.createPicker(activity.getSupportFragmentManager(), TAG_PICKER, null);
        activity.getSupportFragmentManager().executePendingTransactions();
        if (picker.equals("DatePicker")) {
            dateTimePicker.showDatePicker();
        } else {
            dateTimePicker.showTimePicker();
        }
        return shownPicker(picker);
    }

    private DialogFragment shownPicker(String picker) {
        AppCompatActivity activity = mController.get();
        activity.getSupportFragmentManager().executePendingTransactions();
        DateTimePicker dateTimePicker = (DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag(TAG_PICKER);
        dateTimePicker.getChildFragmentManager().executePendingTransactions();
        ShadowLooper.idleMainLooper();
        DialogFragment shown = (DialogFragment) dateTimePicker.getChildFragmentManager().findFragmentByTag(TAG_PICKER + "::" + picker);
        assertNotNull(picker, shown);
        return shown;
    }

    private static boolean isSecure(Dialog dialog) {
        return (dialog.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE) != 0;
    }
}
