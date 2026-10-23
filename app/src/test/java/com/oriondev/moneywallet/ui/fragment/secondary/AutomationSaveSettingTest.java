package com.oriondev.moneywallet.ui.fragment.secondary;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Looper;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.SwitchPreferenceCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowDialog;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AutomationSaveSettingTest {

    private static final String TAG_FRAGMENT = "AutomationSaveSettingTest::Fragment";

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(context);
        TestDatabases.useFreshDatabase(context);
    }

    @Test
    public void theSwitchStartsOffWithItsCodeRowDisabled() {
        withSettings(fragment -> {
            assertFalse(automationSave(fragment).isChecked());
            assertFalse(automationCode(fragment).isEnabled());
            assertNull(PreferenceManager.getAutomationCode());
        });
    }

    @Test
    public void turningTheSwitchOnStoresANewCodeAndShowsIt() {
        withSettings(fragment -> {
            automationSave(fragment).callChangeListener(true);
            String code = PreferenceManager.getAutomationCode();
            assertNotNull(code);
            assertTrue(code.matches("[0-9a-f]{32}"));
            assertTrue(PreferenceManager.isAutomationSaveEnabled());
            assertTrue(automationSave(fragment).isChecked());
            assertTrue(automationCode(fragment).isEnabled());
            assertTrue(message(latestDialog()).contains(code));
        });
    }

    @Test
    public void turningTheSwitchOffDeletesTheCode() {
        withSettings(fragment -> {
            automationSave(fragment).callChangeListener(true);
            latestDialog().dismiss();
            automationSave(fragment).callChangeListener(false);
            assertNull(PreferenceManager.getAutomationCode());
            assertFalse(PreferenceManager.isAutomationSaveEnabled());
            assertFalse(automationSave(fragment).isChecked());
            assertFalse(automationCode(fragment).isEnabled());
        });
    }

    @Test
    public void newCodeReplacesTheStoredCodeAndUpdatesTheOpenDialog() {
        withSettings(fragment -> {
            automationSave(fragment).callChangeListener(true);
            String oldCode = PreferenceManager.getAutomationCode();
            AlertDialog dialog = latestDialog();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            String newCode = PreferenceManager.getAutomationCode();
            assertNotEquals(oldCode, newCode);
            assertTrue(newCode.matches("[0-9a-f]{32}"));
            assertTrue(dialog.isShowing());
            assertTrue(message(dialog).contains(newCode));
            assertFalse(message(dialog).contains(oldCode));
        });
    }

    @Test
    public void theCodeRowShowsTheStoredCodeAgain() {
        withSettings(fragment -> {
            automationSave(fragment).callChangeListener(true);
            latestDialog().dismiss();
            String storedCode = PreferenceManager.getAutomationCode();
            automationCode(fragment).getOnPreferenceClickListener().onPreferenceClick(automationCode(fragment));
            AlertDialog dialog = latestDialog();
            assertEquals(storedCode, PreferenceManager.getAutomationCode());
            assertTrue(dialog.isShowing());
            assertTrue(message(dialog).contains(storedCode));
        });
    }

    @Test
    public void aSwitchLeftOnShowsCheckedWithItsCodeRowEnabled() {
        PreferenceManager.setAutomationSaveEnabled(true);
        PreferenceManager.setAutomationCode("0123456789abcdef0123456789abcdef");
        withSettings(fragment -> {
            assertTrue(automationSave(fragment).isChecked());
            assertTrue(automationCode(fragment).isEnabled());
        });
    }

    @Test
    public void copyPutsTheCodeOnTheClipboardMarkedSensitive() {
        withSettings(fragment -> {
            automationSave(fragment).callChangeListener(true);
            AlertDialog dialog = latestDialog();
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).performClick();
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            shadowOf(Looper.getMainLooper()).idle();
            ClipboardManager clipboard = (ClipboardManager) fragment.requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = clipboard.getPrimaryClip();
            assertNotNull(clip);
            assertEquals(PreferenceManager.getAutomationCode(), clip.getItemAt(0).getText().toString());
            assertNotNull(clip.getDescription().getExtras());
            assertTrue(clip.getDescription().getExtras().getBoolean(ClipDescription.EXTRA_IS_SENSITIVE));
        });
    }

    private interface SettingsCheck {
        void run(UtilitySettingFragment fragment);
    }

    private static void withSettings(SettingsCheck check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                UtilitySettingFragment fragment = new UtilitySettingFragment();
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow();
                check.run(fragment);
            });
        }
    }

    private static SwitchPreferenceCompat automationSave(UtilitySettingFragment fragment) {
        SwitchPreferenceCompat preference = fragment.findPreference("automation_save");
        assertNotNull("the settings screen has no automation switch", preference);
        return preference;
    }

    private static Preference automationCode(UtilitySettingFragment fragment) {
        Preference preference = fragment.findPreference("automation_code");
        assertNotNull("the settings screen has no automation code row", preference);
        return preference;
    }

    private static AlertDialog latestDialog() {
        Dialog dialog = ShadowDialog.getLatestDialog();
        assertNotNull("no dialog is showing", dialog);
        return (AlertDialog) dialog;
    }

    private static String message(AlertDialog dialog) {
        TextView message = dialog.findViewById(android.R.id.message);
        assertNotNull(message);
        return message.getText().toString();
    }
}
