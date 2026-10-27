package com.oriondev.moneywallet.storage.database;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter;
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.util.Zip4jConstants;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class BackupImportFailureTest {

    private static final String DATABASE_ENTRY = "databases/database.json";
    private static final String[] ARRAYS = {"currencies", "wallets", "categories", "events",
            "places", "people", "event_people", "debts", "debt_people", "budgets",
            "budget_wallets", "budget_categories", "category_rules", "savings",
            "recurrent_transactions", "recurrent_transfers", "transactions",
            "transaction_splits", "transaction_people", "transaction_models", "transfers",
            "transfer_people", "transfer_models", "attachments", "transaction_attachment",
            "transfer_attachment"};

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
    }

    @After
    public void tearDown() {
        DataContentProvider.closeStagingDatabase();
    }

    @Test
    public void aNewerBackupLargerThanTheReaderBufferIsRefusedAsNewer() throws Exception {
        char[] padding = new char[256 * 1024];
        Arrays.fill(padding, ' ');
        File backup = zipJson("newer.mwbx", "{\"header\":{\"version_code\":99}" +
                new String(padding) + ",\"currencies\":[]}");

        String message = restoreFailure(backup);

        assertTrue(message, message.contains("newer version"));
    }

    @Test
    public void aBackupFailingAfterTheHeaderShowsItsOwnError() throws Exception {
        char[] padding = new char[256 * 1024];
        Arrays.fill(padding, ' ');
        File backup = zipJson("misnamed.mwbx", "{\"header\":{\"version_code\":6},\"wallets\":[]" +
                new String(padding) + "}");

        String message = restoreFailure(backup);

        assertTrue(message, message.contains("expected = 'currencies'"));
    }

    @Test
    public void aFullyReadBackupWithABadCrcIsStillRefused() throws Exception {
        File backup = exportBackup();
        new DefaultBackupImporter(mContext, backup, null).importDatabase(importFolder());
        DataContentProvider.closeStagingDatabase();
        corruptCrc(backup);

        String message = restoreFailure(backup);

        assertTrue(message, message.contains("invalid CRC"));
    }

    @Test
    public void aValidBackupEndingOnAReadBoundaryIsRestored() throws Exception {
        StringBuilder tail = new StringBuilder();
        for (String array : ARRAYS) {
            tail.append(",\"").append(array).append("\":[]");
        }
        tail.append('}');
        String head = "{\"header\":{\"version_code\":6}";
        char[] padding = new char[8193 - head.length() - tail.length()];
        Arrays.fill(padding, ' ');
        File backup = zipJson("boundary.mwbx", head + new String(padding) + tail);

        new DefaultBackupImporter(mContext, backup, null).importDatabase(importFolder());
    }

    private String restoreFailure(File backup) throws Exception {
        try {
            new DefaultBackupImporter(mContext, backup, null).importDatabase(importFolder());
        } catch (ImportException e) {
            return e.getMessage();
        }
        fail("the backup was restored");
        return null;
    }

    private File importFolder() {
        File folder = new File(mContext.getCacheDir(), "failure-import");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        return folder;
    }

    private File zipJson(String name, String json) throws Exception {
        File folder = new File(mContext.getCacheDir(), "failure-json");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        File source = new File(folder, "database.json");
        OutputStream stream = new FileOutputStream(source);
        try {
            stream.write(json.getBytes(StandardCharsets.UTF_8));
        } finally {
            stream.close();
        }
        File backup = new File(mContext.getCacheDir(), name);
        ZipParameters parameters = new ZipParameters();
        parameters.setRootFolderInZip("databases/");
        parameters.setCompressionMethod(Zip4jConstants.COMP_DEFLATE);
        parameters.setCompressionLevel(Zip4jConstants.DEFLATE_LEVEL_NORMAL);
        new net.lingala.zip4j.core.ZipFile(backup).addFile(source, parameters);
        return backup;
    }

    private File exportBackup() throws Exception {
        File folder = new File(mContext.getCacheDir(), "failure-export");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        File backup = new File(mContext.getCacheDir(), "corrupt.mwbx");
        new DefaultBackupExporter(mContext.getContentResolver(), backup, null).exportDatabase(folder);
        return backup;
    }

    private static void corruptCrc(File backup) throws Exception {
        int crc;
        ZipFile zip = new ZipFile(backup);
        try {
            assertEquals(1, zip.size());
            crc = (int) zip.getEntry(DATABASE_ENTRY).getCrc();
        } finally {
            zip.close();
        }
        byte[] bytes = Files.readAllBytes(backup.toPath());
        int patched = 0;
        for (int i = 0; i + 4 <= bytes.length; i++) {
            int offset;
            int signature = readInt(bytes, i);
            if (signature == 0x04034b50) {
                offset = i + 14;
            } else if (signature == 0x02014b50) {
                offset = i + 16;
            } else if (signature == 0x08074b50) {
                offset = i + 4;
            } else {
                continue;
            }
            if (offset + 4 <= bytes.length && readInt(bytes, offset) == crc) {
                bytes[offset] ^= 0x01;
                patched++;
            }
        }
        assertTrue(patched > 0);
        Files.write(backup.toPath(), bytes);
    }

    private static int readInt(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | (bytes[offset + 1] & 0xff) << 8 |
                (bytes[offset + 2] & 0xff) << 16 | (bytes[offset + 3] & 0xff) << 24;
    }
}
