/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.ui.widget;

import android.content.Context;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

@LargeTest
public class WalletWidgetPreferencesTest {

    private static final int WIDGET_A = 900001;
    private static final int WIDGET_B = 900002;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        clear();
    }

    @After
    public void tearDown() {
        clear();
    }

    private void clear() {
        WalletWidgetPreferences.delete(mContext, new int[] {WIDGET_A, WIDGET_B});
    }

    @Test
    public void anUnknownWidgetHasNoWallet() throws Exception {
        assertEquals(WalletWidgetPreferences.NO_WALLET, WalletWidgetPreferences.getWallet(mContext, WIDGET_A));
        // Off is the answer that keeps a balance off a locked phone, so it has to be what an
        // unconfigured widget reads as and not merely what the checkbox happens to start at.
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(mContext, WIDGET_A));
    }

    @Test
    public void twoWidgetsKeepSeparateWallets() throws Exception {
        WalletWidgetPreferences.save(mContext, WIDGET_A, 11L, false);
        WalletWidgetPreferences.save(mContext, WIDGET_B, 22L, true);
        // The whole point of placing this widget more than once. Writing one placement must not
        // reach another, which a single stored wallet id would not survive.
        assertEquals(11L, WalletWidgetPreferences.getWallet(mContext, WIDGET_A));
        assertEquals(22L, WalletWidgetPreferences.getWallet(mContext, WIDGET_B));
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(mContext, WIDGET_A));
        assertTrue(WalletWidgetPreferences.isShowWhenLocked(mContext, WIDGET_B));
    }

    @Test
    public void savingAgainReplacesBothValues() throws Exception {
        WalletWidgetPreferences.save(mContext, WIDGET_A, 11L, true);
        WalletWidgetPreferences.save(mContext, WIDGET_A, 33L, false);
        assertEquals(33L, WalletWidgetPreferences.getWallet(mContext, WIDGET_A));
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(mContext, WIDGET_A));
    }

    @Test
    public void deletingOneWidgetLeavesTheOther() throws Exception {
        WalletWidgetPreferences.save(mContext, WIDGET_A, 11L, true);
        WalletWidgetPreferences.save(mContext, WIDGET_B, 22L, true);
        WalletWidgetPreferences.delete(mContext, new int[] {WIDGET_A});
        // A launcher hands the same id out again after a widget is removed, so a deleted entry
        // that lingered would point the next widget at a wallet nobody chose for it.
        assertEquals(WalletWidgetPreferences.NO_WALLET, WalletWidgetPreferences.getWallet(mContext, WIDGET_A));
        assertFalse(WalletWidgetPreferences.isShowWhenLocked(mContext, WIDGET_A));
        assertEquals(22L, WalletWidgetPreferences.getWallet(mContext, WIDGET_B));
        assertTrue(WalletWidgetPreferences.isShowWhenLocked(mContext, WIDGET_B));
    }
}
