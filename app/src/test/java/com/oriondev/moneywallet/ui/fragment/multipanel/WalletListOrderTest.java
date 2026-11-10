package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.appcompat.app.AppCompatActivity;
import androidx.loader.content.CursorLoader;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

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
public class WalletListOrderTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"P\"}";

    @Test
    public void theWalletsAtTheSameIndexAreListedInNameOrderWhateverTheCaseOrAccentOfTheirFirstLetter() {
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
        ActivityController<AppCompatActivity> controller = Robolectric.buildActivity(AppCompatActivity.class).setup();
        WalletMultiPanelFragment fragment = new WalletMultiPanelFragment();
        controller.get().getSupportFragmentManager().beginTransaction()
                .add(android.R.id.content, fragment)
                .commitNow();
        List<String> listed = new ArrayList<>();
        try (Cursor cursor = ((CursorLoader) fragment.onCreateLoader(0, null)).loadInBackground()) {
            while (cursor.moveToNext()) {
                listed.add(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME)));
            }
        }
        controller.pause().stop().destroy();
        assertEquals(Arrays.asList("apple", "bob", "\u00c9mile", "Zed"), listed);
    }
}
