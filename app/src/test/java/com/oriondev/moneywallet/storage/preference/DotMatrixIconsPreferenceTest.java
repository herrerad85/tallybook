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
public class DotMatrixIconsPreferenceTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
    }

    /**
     * Robolectric starts App with no database file, which is what a new install looks like.
     */
    @Test
    public void aNewInstallStartsOnDotMatrix() {
        assertTrue(PreferenceManager.isDotMatrixIconsEnabled());
    }

    @Test
    public void anUpgradeKeepsClassic() {
        clear();
        PreferenceManager.resolveDotMatrixIcons(true);
        assertFalse(PreferenceManager.isDotMatrixIconsEnabled());
    }

    @Test
    public void theResolvedValueNeverFlips() {
        clear();
        PreferenceManager.resolveDotMatrixIcons(true);
        PreferenceManager.resolveDotMatrixIcons(false);
        assertFalse(PreferenceManager.isDotMatrixIconsEnabled());
        PreferenceManager.setDotMatrixIconsEnabled(true);
        PreferenceManager.resolveDotMatrixIcons(true);
        assertTrue(PreferenceManager.isDotMatrixIconsEnabled());
    }

    private void clear() {
        mContext.getSharedPreferences("preferences", Context.MODE_PRIVATE).edit().clear().commit();
    }
}
