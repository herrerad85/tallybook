package com.oriondev.moneywallet.storage.preference;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class DotMatrixIconsPreferenceTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
    }

    @Test
    public void aNewInstallStartsOnClassic() {
        assertFalse(PreferenceManager.isDotMatrixIconsEnabled());
    }
}
