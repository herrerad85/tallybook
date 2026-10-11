package com.oriondev.moneywallet.storage.database;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.backup.DefaultBackupExporter;
import com.oriondev.moneywallet.storage.database.backup.DefaultBackupImporter;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The parts a transaction is split into, through a backup and through every delete that can take
 * them. Each expected value is read from the fixture arrays below and never from the code the
 * case runs.
 */
@RunWith(RobolectricTestRunner.class)
public class TransactionSplitTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final long EDIT = 1565000000000L;

    private static final long CASH = 901;
    private static final long BANK = 902;
    private static final long FOOD = 910;
    private static final long HOUSEHOLD = 911;
    private static final long PHARMACY = 912;

    /** id, wallet, main category, total. */
    private static final long[][] PARENTS = {
            {920, CASH, FOOD, 10000},
            {921, CASH, HOUSEHOLD, 4000},
            {922, BANK, FOOD, 800},
    };

    /** id, parent, category, money. The note of each is in NOTES at the same index. */
    private static final long[][] PARTS = {
            {930, 920, HOUSEHOLD, 3000},
            {931, 920, PHARMACY, 2500},
            {932, 921, FOOD, 1000},
            {933, 922, PHARMACY, 300},
    };
    private static final String[] NOTES = {"soap", null, "bread", "plasters"};

    private Context mContext;
    private ContentResolver mResolver;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        SQLiteDatabase db = SQLDatabase.getShared(mContext).getWritableDatabase();
        insertWallet(db, CASH, "Cash");
        insertWallet(db, BANK, "Bank");
        insertCategory(db, FOOD, "Food");
        insertCategory(db, HOUSEHOLD, "Household");
        insertCategory(db, PHARMACY, "Pharmacy");
        for (long[] parent : PARENTS) {
            db.execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                            "transaction_date, transaction_category, transaction_direction, " +
                            "transaction_type, transaction_wallet, uuid, last_edit, deleted) " +
                            "VALUES (?, ?, '2026-09-01 12:00:00', ?, ?, ?, ?, ?, ?, 0)",
                    new Object[] {parent[0], parent[3], parent[2], Schema.Direction.EXPENSE,
                            Contract.TransactionType.STANDARD, parent[1], uuid("transaction", parent[0]),
                            EDIT});
        }
        for (int i = 0; i < PARTS.length; i++) {
            db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                            "split_money, split_note, uuid, last_edit, deleted) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, 0)",
                    new Object[] {PARTS[i][0], PARTS[i][1], PARTS[i][2], PARTS[i][3], NOTES[i],
                            uuid("split", PARTS[i][0]), EDIT});
        }
    }

    /**
     * Every part comes back on the parent it left with, under the category it left with, carrying
     * the same money and note, and every parent keeps its total and its main category.
     */
    @Test
    public void aBackupCarriesEveryPartToItsOwnParent() throws Exception {
        File backup = exportBackup();

        // the header is what an older release reads to refuse this file, and the parts have to sit
        // right after the transactions they name
        String json = readDatabaseJson(backup);
        assertEquals(5, new JSONObject(json).getJSONObject("header").getInt("version_code"));
        int transactions = json.indexOf("\"transactions\"");
        int splits = json.indexOf("\"transaction_splits\"");
        int people = json.indexOf("\"transaction_people\"");
        assertTrue(json, transactions >= 0 && transactions < splits && splits < people);

        restore(backup);
        // a restore numbers its rows afresh, so a fixture id still present means these reads are
        // landing on the ledger the backup was taken from
        assertEquals(0L, count("transactions", "transaction_id = " + PARENTS[0][0]));

        List<String> expected = new ArrayList<>();
        for (int i = 0; i < PARTS.length; i++) {
            long[] parent = parentOf(PARTS[i][1]);
            expected.add(uuid("split", PARTS[i][0]) + " parent=" + uuid("transaction", parent[0]) +
                    " total=" + parent[3] + " main=" + uuid("category", parent[2]) +
                    " category=" + uuid("category", PARTS[i][2]) + " money=" + PARTS[i][3] +
                    " note=" + NOTES[i]);
        }
        Collections.sort(expected);
        List<String> restored = new ArrayList<>();
        Cursor cursor = db().rawQuery("SELECT s.uuid, t.uuid, t.transaction_money, c.uuid, " +
                "pc.uuid, s.split_money, s.split_note FROM transaction_splits AS s " +
                "JOIN transactions AS t ON s._transaction = t.transaction_id " +
                "JOIN categories AS c ON t.transaction_category = c.category_id " +
                "JOIN categories AS pc ON s.split_category = pc.category_id", null);
        try {
            while (cursor.moveToNext()) {
                restored.add(cursor.getString(0) + " parent=" + cursor.getString(1) +
                        " total=" + cursor.getLong(2) + " main=" + cursor.getString(3) +
                        " category=" + cursor.getString(4) + " money=" + cursor.getLong(5) +
                        " note=" + cursor.getString(6));
            }
        } finally {
            cursor.close();
        }
        Collections.sort(restored);
        assertEquals(expected, restored);
        // the join above reaches a parent only through its parts, so the parents are counted alone
        assertEquals(PARENTS.length, count("transactions", "transaction_type = " +
                Contract.TransactionType.STANDARD));
    }

    /** A file written before parts existed carries no array for them, and restores as it did. */
    @Test
    public void aVersionFourBackupStillRestores() throws Exception {
        File backup = writeBackup(4);
        restore(backup);
        assertEquals(1L, count("transactions", "uuid = 'old-transaction' AND " +
                "transaction_money = 1234"));
        assertEquals(0L, count("transaction_splits", null));
    }

    /**
     * A file from a release newer than this one is refused by its header before any row is read,
     * which is what a release that predates parts does to a version 5 file.
     */
    @Test
    public void aBackupFromANewerReleaseIsRefusedByItsHeader() throws Exception {
        File backup = writeBackup(6);
        try {
            restore(backup);
            fail("a backup newer than this release was restored");
        } catch (ImportException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("newer version"));
        }
        // nothing reached the staging file, and the ledger it would have replaced is untouched
        assertEquals(0L, count("transaction_splits", null));
        DataContentProvider.closeStagingDatabase();
        assertEquals(PARTS.length, count("transaction_splits", null));
    }

    /**
     * The wallet delete removes its transactions in one statement without going through the per
     * transaction delete, so only the cascade on the parent key takes their parts.
     */
    @Test
    public void deletingAWalletTakesThePartsOfItsTransactions() {
        mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, CASH), null, null);
        assertEquals(partsOfWallet(BANK), count("transaction_splits", null));
        assertEquals(0L, count("transaction_splits", "_transaction IN (920, 921)"));
    }

    /**
     * A category only parts use leaves with them, and the parents stay with their totals and main
     * categories, so the money of the lost part reads under the main category again. The delete is
     * the bare statement a release that predates parts runs, since this one refuses a category a
     * part uses.
     */
    @Test
    public void deletingACategoryTakesThePartsUsingItAndLeavesTheirParents() {
        SQLDatabase.getShared(mContext).getWritableDatabase().execSQL(
                "DELETE FROM categories WHERE category_id = " + PHARMACY);
        long left = 0;
        for (long[] part : PARTS) {
            if (part[2] != PHARMACY) {
                left++;
            }
        }
        assertEquals(left, count("transaction_splits", null));
        assertEquals(0L, count("transaction_splits", "split_category = " + PHARMACY));
        for (long[] parent : PARENTS) {
            assertEquals(1L, count("transactions", "transaction_id = " + parent[0] +
                    " AND transaction_money = " + parent[3] + " AND transaction_category = " + parent[2]));
        }
    }

    /** A transaction delete removes its parts outright, the way it removes its people. */
    @Test
    public void deletingATransactionRemovesItsParts() {
        mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, 920), null, null);
        long left = 0;
        for (long[] part : PARTS) {
            if (part[1] != 920) {
                left++;
            }
        }
        // read with no flag filter, so a part flagged deleted and left behind would be counted
        assertEquals(0L, count("transaction_splits", "_transaction = 920"));
        assertEquals(left, count("transaction_splits", null));
    }

    /**
     * Accepting the rescale when a currency's decimals change moves every part with its total, or
     * the remainder under the main category would take the difference. The dollar wallet's row is
     * the control, since a pass that walked every part would move it too.
     */
    @Test
    public void changingTheDecimalsOfACurrencyRescalesItsParts() {
        SQLiteDatabase db = SQLDatabase.getShared(mContext).getWritableDatabase();
        // pinned, so the factor asserted below is known
        db.execSQL("UPDATE currencies SET currency_decimals = 2 WHERE currency_iso = 'EUR'");
        assertEquals(1L, count("currencies", "currency_iso = 'EUR' AND currency_decimals = 2"));
        db.execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                        "last_edit, deleted) VALUES (903, 'Dollars', 'USD', 'wallet-903', ?, 0)",
                new Object[] {EDIT});
        db.execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_category, transaction_direction, " +
                        "transaction_type, transaction_wallet, uuid, last_edit, deleted) " +
                        "VALUES (923, 600, '2026-09-01 12:00:00', ?, ?, ?, 903, 'transaction-923', ?, 0)",
                new Object[] {FOOD, Schema.Direction.EXPENSE, Contract.TransactionType.STANDARD, EDIT});
        db.execSQL("INSERT INTO transaction_splits (split_id, _transaction, split_category, " +
                        "split_money, uuid, last_edit, deleted) " +
                        "VALUES (934, 923, ?, 200, 'split-934', ?, 0)",
                new Object[] {PHARMACY, EDIT});

        ContentValues values = new ContentValues();
        values.put(Contract.Currency.DECIMALS, 3);
        values.put(Contract.Currency.FIX_MONEY_DECIMALS, true);
        assertEquals(1, mResolver.update(Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES,
                "EUR"), values, null, null));

        // two decimals to three, so every euro amount is ten times what the fixture wrote
        for (long[] part : PARTS) {
            assertEquals(1L, count("transaction_splits", "split_id = " + part[0] +
                    " AND split_money = " + part[3] * 10));
        }
        for (long[] parent : PARENTS) {
            assertEquals(1L, count("transactions", "transaction_id = " + parent[0] +
                    " AND transaction_money = " + parent[3] * 10));
        }
        assertEquals(1L, count("transaction_splits", "split_id = 934 AND split_money = 200"));
        assertEquals(1L, count("transactions", "transaction_id = 923 AND transaction_money = 600"));
    }

    private long partsOfWallet(long wallet) {
        long parts = 0;
        for (long[] part : PARTS) {
            if (parentOf(part[1])[1] == wallet) {
                parts++;
            }
        }
        return parts;
    }

    private long[] parentOf(long id) {
        for (long[] parent : PARENTS) {
            if (parent[0] == id) {
                return parent;
            }
        }
        throw new IllegalArgumentException("no parent " + id);
    }

    private File exportBackup() throws Exception {
        File folder = new File(mContext.getCacheDir(), "split-export");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        File backup = new File(mContext.getCacheDir(), "splits.mwbx");
        new DefaultBackupExporter(mResolver, backup, null).exportDatabase(folder);
        return backup;
    }

    /**
     * Imports into the staging file and leaves this thread pointed at it, so every read after
     * this sees what the import wrote. The promote that follows in the app is a rename over the
     * live file, which reads no row, and which File.renameTo refuses on a Windows host while the
     * live file exists.
     */
    private void restore(File backup) throws Exception {
        File folder = new File(mContext.getCacheDir(), "split-import");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        new DefaultBackupImporter(mContext, backup, null).importDatabase(folder);
    }

    @After
    public void tearDown() {
        DataContentProvider.closeStagingDatabase();
    }

    /**
     * A backup file as a release writing the given version would, holding one wallet, one category
     * and one transaction and every other array empty. Up to version 4 there is no array of parts.
     */
    private File writeBackup(int version) throws Exception {
        String[] arrays = {"currencies", "wallets", "categories", "events", "places", "people",
                "event_people", "debts", "debt_people", "budgets", "budget_wallets",
                "budget_categories", "category_rules", "savings", "recurrent_transactions",
                "recurrent_transfers", "transactions", "transaction_people", "transaction_models",
                "transfers", "transfer_people", "transfer_models", "attachments",
                "transaction_attachment", "transfer_attachment"};
        StringBuilder json = new StringBuilder("{\"header\":{\"version_code\":" + version + "}");
        for (String array : arrays) {
            json.append(",\"").append(array).append("\":[");
            if ("wallets".equals(array)) {
                json.append("{\"id\":\"old-wallet\",\"name\":\"Cash\",\"currency\":\"EUR\"," +
                        "\"start_money\":0,\"last_edit\":" + EDIT + "}");
            } else if ("categories".equals(array)) {
                json.append("{\"id\":\"old-category\",\"name\":\"Food\",\"icon\":" +
                        JSONObject.quote(ICON) + ",\"type\":" + Schema.CategoryType.EXPENSE +
                        ",\"last_edit\":" + EDIT + "}");
            } else if ("transactions".equals(array)) {
                json.append("{\"id\":\"old-transaction\",\"money\":1234," +
                        "\"date\":\"2026-09-01 12:00:00\",\"category\":\"old-category\"," +
                        "\"direction\":" + Schema.Direction.EXPENSE + ",\"type\":" +
                        Contract.TransactionType.STANDARD + ",\"wallet\":\"old-wallet\"," +
                        "\"last_edit\":" + EDIT + "}");
            }
            json.append("]");
        }
        json.append("}");
        File backup = new File(mContext.getCacheDir(), "version" + version + ".mwbx");
        ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(backup));
        try {
            zip.putNextEntry(new ZipEntry("databases/database.json"));
            zip.write(json.toString().getBytes("UTF-8"));
            zip.closeEntry();
        } finally {
            zip.close();
        }
        return backup;
    }

    private String readDatabaseJson(File backup) throws Exception {
        ZipFile zip = new ZipFile(backup);
        try {
            InputStream stream = zip.getInputStream(zip.getEntry("databases/database.json"));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                bytes.write(buffer, 0, read);
            }
            return bytes.toString("UTF-8");
        } finally {
            zip.close();
        }
    }

    private static String uuid(String kind, long id) {
        return kind + "-" + id;
    }

    private SQLiteDatabase db() {
        return SQLDatabase.getShared(mContext).getReadableDatabase();
    }

    private long count(String table, String selection) {
        Cursor cursor = db().rawQuery("SELECT COUNT(*) FROM " + table +
                (selection != null ? " WHERE " + selection : ""), null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private void insertWallet(SQLiteDatabase db, long id, String name) {
        db.execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, 'EUR', ?, ?, 0)",
                new Object[] {id, name, uuid("wallet", id), EDIT});
    }

    private void insertCategory(SQLiteDatabase db, long id, String name) {
        db.execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                        "category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, name, ICON, Schema.CategoryType.EXPENSE, uuid("category", id), EDIT});
    }
}
