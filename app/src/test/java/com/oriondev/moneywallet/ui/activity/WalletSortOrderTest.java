package com.oriondev.moneywallet.ui.activity;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class WalletSortOrderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private ActivityController<WalletSortActivity> mController;

    @After
    public void tearDown() {
        if (mController != null && !mController.get().isDestroyed()) {
            mController.destroy();
        }
    }

    @Test
    public void walletsAtTheSameIndexAreOfferedInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        ContentResolver resolver = context.getContentResolver();
        for (String name : new String[] {"Zed", "apple", "bob", "\u00c9mile"}) {
            ContentValues values = new ContentValues();
            values.put(Contract.Wallet.NAME, name);
            values.put(Contract.Wallet.ICON, ICON);
            values.put(Contract.Wallet.CURRENCY, "EUR");
            values.put(Contract.Wallet.START_MONEY, 0L);
            values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
            resolver.insert(DataContentProvider.CONTENT_WALLETS, values);
        }
        mController = Robolectric.buildActivity(WalletSortActivity.class).setup();
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = ((CursorLoader) mController.get().onCreateLoader(0, null)).loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)));
            }
        }
        assertEquals(Arrays.asList("apple", "bob", "\u00c9mile", "Zed"), listed);
    }
}
