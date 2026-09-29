/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.storage.database;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class DataContentProviderRestoreTest {

    @Test
    public void aRestoreClearsTheDefaultWallet() {
        Context context = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(context);
        TestDatabases.useFreshDatabase(context);
        PreferenceManager.setDefaultWallet(7L);
        DataContentProvider.notifyDatabaseIsChanged(context);
        assertEquals(PreferenceManager.NO_DEFAULT_WALLET, PreferenceManager.getDefaultWallet());
    }
}
