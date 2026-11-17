package com.oriondev.moneywallet.storage.database;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

@RunWith(RobolectricTestRunner.class)
public class TransactionAttachmentDeleteTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private File mExternalFolder;
    private File mAttachmentFolder;
    private long mWallet;
    private long mCategory;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        mExternalFolder = mContext.getExternalFilesDir(null);
        mAttachmentFolder = new File(mExternalFolder, Attachment.FOLDER_NAME);
        write(new File(mAttachmentFolder, "kept"), "kept attachment");
        mWallet = insertWallet();
        mCategory = insertCategory();
    }

    @Test
    public void aDeletedTransactionDeletesTheFileOfItsAttachment() throws IOException {
        write(new File(mAttachmentFolder, "3f9c0a7d1e"), "receipt");
        long transaction = insertTransaction(insertAttachment("3f9c0a7d1e"));

        deleteTransaction(transaction);

        assertFalse(new File(mAttachmentFolder, "3f9c0a7d1e").exists());
        assertEquals("kept attachment", read(new File(mAttachmentFolder, "kept")));
    }

    @Test
    public void aDeletedTransactionDeletesNothingOutsideTheAttachmentFolder() throws IOException {
        write(new File(mExternalFolder, "marker"), "marker");
        long transaction = insertTransaction(insertAttachment("../marker"));

        deleteTransaction(transaction);

        assertEquals("marker", read(new File(mExternalFolder, "marker")));
        assertEquals("kept attachment", read(new File(mAttachmentFolder, "kept")));
    }

    private void deleteTransaction(long transaction) {
        assertEquals(1, mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transaction), null, null));
    }

    private long insertWallet() {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, "Cash");
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertCategory() {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, "Groceries");
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertAttachment(String file) {
        ContentValues values = new ContentValues();
        values.put(Contract.Attachment.FILE, file);
        values.put(Contract.Attachment.NAME, "March receipt.pdf");
        values.put(Contract.Attachment.TYPE, "application/pdf");
        values.put(Contract.Attachment.SIZE, 98765L);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_ATTACHMENTS, values));
    }

    private long insertTransaction(long attachment) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, 1000L);
        values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00");
        values.put(Contract.Transaction.DESCRIPTION, "receipt");
        values.put(Contract.Transaction.CATEGORY_ID, mCategory);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        values.put(Contract.Transaction.WALLET_ID, mWallet);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        values.put(Contract.Transaction.ATTACHMENT_IDS, "<" + attachment + ">");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    private static void write(File file, String content) throws IOException {
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    }

    private static String read(File file) throws IOException {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }
}
