package com.oriondev.moneywallet.api.disk;

import android.Manifest;
import android.app.Application;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * From Android 13 the platform denies WRITE_EXTERNAL_STORAGE to an app targeting 33 or higher,
 * so the backend has to report itself enabled without it.
 */
@RunWith(RobolectricTestRunner.class)
public class DiskBackendServiceTest {

    private final DiskBackendService mService = new DiskBackendService(null);

    @Test
    @Config(sdk = {33, 36})
    public void theBackendIsEnabledFromAndroid13WithoutTheStoragePermission() {
        Application application = ApplicationProvider.getApplicationContext();
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        assertTrue(mService.isServiceEnabled(application));
    }

    @Test
    @Config(sdk = 32)
    public void theBackendStillNeedsTheStoragePermissionBelowAndroid13() {
        Application application = ApplicationProvider.getApplicationContext();
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        assertFalse(mService.isServiceEnabled(application));
        shadowOf(application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE);
        assertTrue(mService.isServiceEnabled(application));
    }
}
