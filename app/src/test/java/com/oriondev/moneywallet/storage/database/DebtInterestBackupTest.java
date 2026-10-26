package com.oriondev.moneywallet.storage.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

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
import java.io.InputStream;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class DebtInterestBackupTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final long EDIT = 1565000000000L;

    private static final long CASH = 901;
    private static final long DEBT = 940;
    private static final long CREDIT = 941;
    private static final long DEBT_INTEREST_ROW = 950;
    private static final long CREDIT_INTEREST_ROW = 951;

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        PreferenceManager.initialize(mContext);
        TestDatabases.useFreshDatabase(mContext);
        db().execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                "last_edit, deleted) VALUES (?, 'Cash', 'EUR', 'wallet-901', ?, 0)",
                new Object[] {CASH, EDIT});
    }

    @After
    public void tearDown() {
        DataContentProvider.closeStagingDatabase();
    }

    @Test
    public void aBackupCarriesInterestToItsDebtAndItsCategory() throws Exception {
        insertDebt(DEBT, Contract.DebtType.DEBT);
        insertDebt(CREDIT, Contract.DebtType.CREDIT);
        insertInterest(DEBT_INTEREST_ROW, DEBT, Contract.CategoryTag.DEBT_INTEREST,
                Schema.Direction.EXPENSE, 40L);
        insertInterest(CREDIT_INTEREST_ROW, CREDIT, Contract.CategoryTag.CREDIT_INTEREST,
                Schema.Direction.INCOME, 25L);

        File backup = exportBackup();
        assertEquals(6, new JSONObject(readDatabaseJson(backup)).getJSONObject("header")
                .getInt("version_code"));
        restore(backup);

        assertEquals(0L, count("transactions", "transaction_id = " + DEBT_INTEREST_ROW));
        assertEquals(1L, restoredInterest(DEBT_INTEREST_ROW, DEBT,
                Contract.CategoryTag.DEBT_INTEREST, Schema.Direction.EXPENSE, 40L));
        assertEquals(1L, restoredInterest(CREDIT_INTEREST_ROW, CREDIT,
                Contract.CategoryTag.CREDIT_INTEREST, Schema.Direction.INCOME, 25L));
    }

    @Test
    public void aBackupWithoutTheInterestCategoriesRestoresWithBoth() throws Exception {
        db().execSQL("DELETE FROM categories WHERE category_tag IN (?, ?)", new Object[] {
                Contract.CategoryTag.DEBT_INTEREST, Contract.CategoryTag.CREDIT_INTEREST});
        assertEquals(0L, interestCategories(Contract.CategoryTag.DEBT_INTEREST));
        assertEquals(0L, interestCategories(Contract.CategoryTag.CREDIT_INTEREST));

        restore(exportBackup());

        assertEquals(1L, interestCategories(Contract.CategoryTag.DEBT_INTEREST));
        assertEquals(1L, interestCategories(Contract.CategoryTag.CREDIT_INTEREST));
    }

    private void insertDebt(long id, Contract.DebtType type) {
        db().execSQL("INSERT INTO debts (debt_id, debt_type, debt_icon, debt_description, " +
                        "debt_date, debt_wallet, debt_money, uuid, last_edit, deleted) " +
                        "VALUES (?, ?, ?, 'Loan', '2026-07-01', ?, 2000, ?, ?, 0)",
                new Object[] {id, type.getValue(), ICON, CASH, "debt-" + id, EDIT});
    }

    private void insertInterest(long id, long debt, String tag, int direction, long money) {
        db().execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_category, transaction_direction, " +
                        "transaction_type, transaction_wallet, transaction_debt, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, '2026-07-01 10:00:00', ?, ?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, money, systemCategory(tag), direction,
                        Contract.TransactionType.DEBT, CASH, debt, "transaction-" + id, EDIT});
    }

    private long systemCategory(String tag) {
        Cursor cursor = db().rawQuery("SELECT category_id FROM categories WHERE category_tag = ?",
                new String[] {tag});
        try {
            assertTrue(tag, cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private long restoredInterest(long id, long debt, String tag, int direction, long money) {
        return count("transactions AS t " +
                "JOIN categories AS c ON t.transaction_category = c.category_id " +
                "JOIN debts AS d ON t.transaction_debt = d.debt_id",
                "t.uuid = 'transaction-" + id + "' AND c.category_tag = '" + tag + "' AND " +
                        "d.uuid = 'debt-" + debt + "' AND t.transaction_direction = " + direction +
                        " AND t.transaction_money = " + money + " AND t.transaction_type = " +
                        Contract.TransactionType.DEBT);
    }

    private long interestCategories(String tag) {
        return count("categories", "category_tag = '" + tag + "' AND deleted = 0");
    }

    private File exportBackup() throws Exception {
        File folder = new File(mContext.getCacheDir(), "interest-export");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        File backup = new File(mContext.getCacheDir(), "interest.mwbx");
        new DefaultBackupExporter(mContext.getContentResolver(), backup, null).exportDatabase(folder);
        return backup;
    }

    private void restore(File backup) throws Exception {
        File folder = new File(mContext.getCacheDir(), "interest-import");
        assertTrue(folder.mkdirs() || folder.isDirectory());
        new DefaultBackupImporter(mContext, backup, null).importDatabase(folder);
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

    private SQLiteDatabase db() {
        return SQLDatabase.getShared(mContext).getWritableDatabase();
    }

    private long count(String table, String selection) {
        Cursor cursor = db().rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + selection, null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }
}
