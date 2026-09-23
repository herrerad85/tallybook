package com.oriondev.moneywallet.storage.preference;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class BackendManagerTest {

    private static final String BACKEND = "webdav";

    @Before
    public void setUp() {
        BackendManager.initialize(ApplicationProvider.<Context>getApplicationContext());
    }

    @Test
    public void aFailureIsUnseenUntilTheLaunchPromptIsAnswered() {
        BackendManager.setAutoBackupEnabled(BACKEND, true);
        assertFalse(BackendManager.hasUnseenAutoBackupFailure());
        BackendManager.disableAutoBackupAfterFailure(BACKEND);
        assertTrue(BackendManager.hasUnseenAutoBackupFailure());
        BackendManager.markAutoBackupFailuresSeen();
        assertFalse(BackendManager.hasUnseenAutoBackupFailure());
    }

    @Test
    public void turningAutoBackupBackOnForgetsTheFailure() {
        BackendManager.setAutoBackupEnabled(BACKEND, true);
        BackendManager.disableAutoBackupAfterFailure(BACKEND);
        BackendManager.setAutoBackupEnabled(BACKEND, true);
        assertFalse(BackendManager.hasUnseenAutoBackupFailure());
    }

    @Test
    public void aFailureAfterAnAnsweredOneIsUnseenAgain() {
        BackendManager.setAutoBackupEnabled(BACKEND, true);
        BackendManager.disableAutoBackupAfterFailure(BACKEND);
        BackendManager.markAutoBackupFailuresSeen();
        BackendManager.setAutoBackupEnabled(BACKEND, true);
        BackendManager.disableAutoBackupAfterFailure(BACKEND);
        assertTrue(BackendManager.hasUnseenAutoBackupFailure());
    }

    @Test
    public void aFailureRecordedBeforeThePromptExistedIsUnseen() {
        ApplicationProvider.<Context>getApplicationContext()
                .getSharedPreferences("backend_preferences", Context.MODE_PRIVATE)
                .edit().putBoolean("auto_backup_disabled_by_failure_" + BACKEND, true).commit();
        assertTrue(BackendManager.hasUnseenAutoBackupFailure());
    }
}
