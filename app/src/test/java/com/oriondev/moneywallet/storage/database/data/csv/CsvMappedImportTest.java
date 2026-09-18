package com.oriondev.moneywallet.storage.database.data.csv;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * A mapped import run for real against a fresh database, for what CsvImportMappingTest cannot
 * reach without one: a row that reads fine before the row that is refused, and a file that is
 * saved.
 */
@RunWith(RobolectricTestRunner.class)
public class CsvMappedImportTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"W\"}";

    private Context mContext;
    private ContentResolver mResolver;
    private long mWalletId;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, "Checking");
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.ARCHIVED, false);
        mWalletId = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    /**
     * The file from the device. The thousands comma in the second row is not quoted, so the row
     * reads as five cells, and read by position it used to save 1 under a new category named
     * 234.56. The row before it is fine, so the refusal also shows the whole import is held back.
     */
    @Test
    public void aRowWithMoreCellsThanTheHeaderRefusesTheImportAndSavesNothing() throws IOException {
        int categories = count(DataContentProvider.CONTENT_CATEGORIES, Contract.Category.ID);
        String refusal = refusal(write("Date,Merchant,Amount,Category\n"
                + "09/01/2026,\"Hardware, Inc.\",12.00,Home\n"
                + "09/02/2026,\"Hardware, Inc.\",1,234.56,Home\n"), deviceMapping());
        assertEquals("Line 3: the row has 5 cells and the header has 4, and every row needs as many as the header",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
        assertEquals(categories, count(DataContentProvider.CONTENT_CATEGORIES, Contract.Category.ID));
    }

    /** The numbering the device showed, a refusal on the second data row naming line 3. */
    @Test
    public void aRefusalOnTheSecondDataRowNamesLineThree() throws IOException {
        String refusal = refusal(write("Date,Merchant,Amount,Category\n"
                + "09/01/2026,Grocer,1.00,Food\n"
                + "09/02/2026,Grocer,\"12,50\",Food\n"), deviceMapping());
        assertEquals("Line 3: the amount \"12,50\" is not a number written as 1,234.56", refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /**
     * A blank line between rows is a row of one empty cell to the reader, so it is refused like
     * any other row that does not match the header, and nothing before it is saved.
     */
    @Test
    public void aBlankLineBetweenRowsRefusesTheImport() throws IOException {
        String refusal = refusal(write("Date,Merchant,Amount,Category\n"
                + "09/01/2026,Grocer,1.00,Food\n"
                + "\n"
                + "09/02/2026,Grocer,2.00,Food\n"), deviceMapping());
        assertEquals("Line 3: the row has 1 cell and the header has 4, and every row needs as many as the header",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /** The line ending after the last row, as nearly every tool writes one, is not a row of its own. */
    @Test
    public void aLineEndingAfterTheLastRowImportsEveryRowAndNothingMore() throws IOException {
        importFile(write("Date,Merchant,Amount,Category\n"
                + "09/01/2026,\"Hardware, Inc.\",12.00,Home\n"
                + "09/02/2026,Grocer,3.50,\n"), deviceMapping());
        assertEquals(2, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
        importFile(write("Date,Merchant,Amount,Category\r\n"
                + "09/03/2026,Grocer,4.00,Food\r\n"), deviceMapping());
        assertEquals(3, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /**
     * A mapped import read as far as its first row. The file carries a byte order mark, uses
     * semicolons, and quotes a cell holding one, so the refusal quoting "1;5" can only happen if
     * the reader split on the mapping's separator and kept the quoted cell whole. It names line 2,
     * which it can only do if the header line was skipped, since the header's own amount cell
     * would otherwise be refused first.
     */
    @Test
    public void aMappedImportSkipsTheHeaderAndSplitsOnTheMappingSeparator() throws IOException {
        File file = write("\uFEFFDate;Amount;Memo\n\"01/02/2026\";\"1;5\";x\n");
        assertEquals("Line 2: the amount \"1;5\" is not a number written as 1,234.56", refusal(file, semicolonMapping()));
    }

    @Test
    public void aMappedImportRefusesAMissingOrEmptyRequiredCell() throws IOException {
        assertEquals("Line 2: the date is empty, and every row needs one",
                refusal(write("Date;Amount\n  ;1.00\n"), semicolonMapping()));
        assertEquals("Line 2: the amount is empty, and every row needs one",
                refusal(write("Date;Amount\n01/02/2026;\n"), semicolonMapping()));
        assertEquals("Line 2: the date \"2026-02-01\" is not a real date written as dd/MM/yyyy, with or without a time",
                refusal(write("Date;Amount\n2026-02-01;1.00\n"), semicolonMapping()));
    }

    /**
     * Each row here has a date and an amount that read fine, so without the count check it would
     * be saved. A separator inside quotes is part of one cell, in the header and in a row alike.
     */
    @Test
    public void aMappedRowWithMoreOrFewerCellsThanTheHeaderIsRefused() throws IOException {
        assertEquals("Line 2: the row has 2 cells and the header has 3, and every row needs as many as the header",
                refusal(write("Date;Amount;Memo\n01/02/2026;1.00\n"), semicolonMapping()));
        assertEquals("Line 2: the row has 1 cell and the header has 3, and every row needs as many as the header",
                refusal(write("Date;Amount;Memo\n01/02/2026\n"), semicolonMapping()));
        assertEquals("Line 2: the row has 4 cells and the header has 3, and every row needs as many as the header",
                refusal(write("Date;Amount;\"Memo; long\"\n01/02/2026;1.00;x;y\n"), semicolonMapping()));
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /**
     * A backslash just before a closing quote used to escape the quote, so the first two rows
     * read as one row of three cells, saved 01/02 at the amount of 01/03 and lost 01/03.
     */
    @Test
    public void aBackslashBeforeAClosingQuoteEndsTheCell() throws IOException {
        importFile(write("Date,Description,Amount\n"
                + "01/02/2026,\"C:\\\",-5.00\n"
                + "01/03/2026,\"D:\\\",-3.00\n"
                + "01/04/2026,Shop,-1.00\n"), commaMapping());
        assertEquals("C:\\ 500|D:\\ 300|Shop 100|", savedRows());
    }

    @Test
    public void aBackslashInsideADescriptionIsKept() throws IOException {
        importFile(write("Date,Description,Amount\n01/02/2026,PAYPAL\\ACME,-5.00\n"), commaMapping());
        assertEquals("PAYPAL\\ACME 500|", savedRows());
    }

    /**
     * The wallet picked on the import screen was EUR, and its currency was edited to JPY before
     * the import ran. The amount is saved in the scale of JPY, which has no decimals.
     */
    @Test
    public void anAmountIsSavedInTheCurrencyTheWalletHasWhenTheImportRuns() throws IOException {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.CURRENCY, "JPY");
        assertEquals(1, mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mWalletId),
                values, null, null));
        importFile(write("Date,Description,Amount\n01/02/2026,Shop,1500\n"), commaMapping());
        assertEquals("Shop 1500|", savedRows());
    }

    /** The wallet is looked up before any row is read, so its refusal comes before a bad row's. */
    @Test
    public void aDeletedWalletIsRefusedBeforeAnyRowAndSavesNothing() throws IOException {
        assertEquals(1, mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, mWalletId),
                null, null));
        assertEquals("the wallet picked for this import no longer exists",
                refusal(write("Date,Description,Amount\n01/02/2026,Shop,1.00\nnot a date,Shop,1.00\n"), commaMapping()));
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /**
     * The inch marks are not quoted, so the first one opens a quote the second one closes three
     * rows later. The joined row has three cells like the header, and it used to save 09/01 at the
     * amount of 09/04 and lose the rows in between.
     */
    @Test
    public void aRowJoinedByAStrayQuotePairIsRefusedNamingTheLineItStartsOn() throws IOException {
        String refusal = refusal(write("Date,Description,Amount\n"
                + "09/01/2026,Monitor 27\" LG,-199.00\n"
                + "09/02/2026,Coffee,-3.50\n"
                + "09/03/2026,Salary,2500.00\n"
                + "09/04/2026,Tablet 11\" case,-20.00\n"
                + "09/05/2026,Bread,-2.00\n"), commaMapping());
        assertEquals("Line 2: a quote in this row is not closed on the same line, and every row has to fit on one line",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    @Test
    public void aStrayQuotePairAfterGoodRowsNamesTheLineTheJoinedRowStartsOn() throws IOException {
        String refusal = refusal(write("Date,Description,Amount\n"
                + "09/01/2026,Coffee,-3.50\n"
                + "09/02/2026,Bread,-2.00\n"
                + "09/03/2026,Monitor 27\" LG,-199.00\n"
                + "09/04/2026,Salary,2500.00\n"
                + "09/05/2026,Tablet 11\" case,-20.00\n"
                + "09/06/2026,Milk,-1.00\n"), commaMapping());
        assertEquals("Line 4: a quote in this row is not closed on the same line, and every row has to fit on one line",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /**
     * A single inch mark opens a quote that nothing closes, so the reader runs out of file and
     * hands back no row at all. That used to reach the user as opencsv's own wording about an
     * unterminated quote, with a dump of the text it had read and no line number.
     */
    @Test
    public void aRowWithOneStrayQuoteIsRefusedNamingTheLineItStartsOn() throws IOException {
        String refusal = refusal(write("Date,Description,Amount\n"
                + "09/01/2026,Monitor 27\" LG,-199.00\n"
                + "09/02/2026,Coffee,-3.50\n"), commaMapping());
        assertEquals("Line 2: a quote in this row is not closed on the same line, and every row has to fit on one line",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    @Test
    public void oneStrayQuoteAfterGoodRowsNamesTheLineItStartsOn() throws IOException {
        String refusal = refusal(write("Date,Description,Amount\n"
                + "09/01/2026,Coffee,-3.50\n"
                + "09/02/2026,Bread,-2.00\n"
                + "09/03/2026,Monitor 27\" LG,-199.00\n"
                + "09/04/2026,Milk,-1.00\n"), commaMapping());
        assertEquals("Line 4: a quote in this row is not closed on the same line, and every row has to fit on one line",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /** The last row, with no line ending after it, is still the line the refusal names. */
    @Test
    public void oneStrayQuoteOnTheLastRowWithNoLineEndingNamesThatLine() throws IOException {
        String refusal = refusal(write("Date,Description,Amount\n"
                + "09/01/2026,Coffee,-3.50\n"
                + "09/02/2026,Tablet 11\" case,-20.00"), commaMapping());
        assertEquals("Line 3: a quote in this row is not closed on the same line, and every row has to fit on one line",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /** A line break inside quotes is valid CSV, and a mapped import still refuses it. */
    @Test
    public void aQuotedLineBreakIsRefusedNamingTheLineItStartsOn() throws IOException {
        String refusal = refusal(write("Date,Description,Amount\n"
                + "09/01/2026,Coffee,-3.50\n"
                + "09/02/2026,\"two\nlines\",-2.00\n"
                + "09/03/2026,Bread,-1.00\n"), commaMapping());
        assertEquals("Line 3: a quote in this row is not closed on the same line, and every row has to fit on one line",
                refusal);
        assertEquals(0, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    /** Line endings of two characters and a quoted separator are still one line per row. */
    @Test
    public void aFileWithCrlfEndingsAndAQuotedSeparatorImportsEveryRow() throws IOException {
        importFile(write("Date,Description,Amount\r\n"
                + "01/02/2026,\"Shop, Inc.\",-5.00\r\n"
                + "01/03/2026,Cafe,-3.00\r\n"
                + "01/04/2026,Bakery,-1.00"), commaMapping());
        assertEquals("Shop, Inc. 500|Cafe 300|Bakery 100|", savedRows());
    }

    /**
     * Through both read passes and the transaction the second one runs in, so a row is counted
     * once and not once per pass. The file repeats a row, which the first import saves twice.
     */
    @Test
    public void importingTheSameFileAgainSavesNothingAndCountsEveryRow() throws IOException {
        File file = write("Date,Description,Amount\n"
                + "01/02/2026,Shop,-5.00\n"
                + "01/02/2026,Shop,-5.00\n"
                + "01/03/2026,,-3.00\n");
        assertEquals(0, importCountingSkipped(file, commaMapping()));
        assertEquals(3, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
        assertEquals(3, importCountingSkipped(file, commaMapping()));
        assertEquals(3, count(DataContentProvider.CONTENT_TRANSACTIONS, Contract.Transaction.ID));
    }

    @Test
    public void aRowSkippedOnReimportIsNotCountedAsRounded() throws IOException {
        File file = write("Date,Description,Amount\n01/02/2026,Shop,-5.005\n");
        CSVDataImporter first = new CSVDataImporter(mContext, file, commaMapping());
        CSVDataImporter second = new CSVDataImporter(mContext, file, commaMapping());
        try {
            first.importData();
            assertEquals(1, first.getRoundedAmounts());
            second.importData();
            assertEquals(1, second.getAlreadySavedRows());
            assertEquals(0, second.getRoundedAmounts());
        } finally {
            first.close();
            second.close();
        }
    }

    @Test
    public void aRowSkippedOnReimportOfOurOwnFormatIsNotCountedAsRounded() throws IOException {
        File file = write("wallet,currency,category,datetime,money,description\n"
                + "Checking,EUR,Food,2026-01-02 10:00:00,-5.005,Shop\n");
        CSVDataImporter first = new CSVDataImporter(mContext, file);
        CSVDataImporter second = new CSVDataImporter(mContext, file);
        try {
            first.importData();
            assertEquals(1, first.getRoundedAmounts());
            second.importData();
            assertEquals(1, second.getAlreadySavedRows());
            assertEquals(0, second.getRoundedAmounts());
        } finally {
            first.close();
            second.close();
        }
    }

    private int importCountingSkipped(File file, CsvImportMapping mapping) throws IOException {
        CSVDataImporter importer = new CSVDataImporter(mContext, file, mapping);
        try {
            importer.importData();
            return importer.getAlreadySavedRows();
        } finally {
            importer.close();
        }
    }

    private CsvImportMapping semicolonMapping() {
        CsvImportMapping mapping = new CsvImportMapping();
        mapping.separator = ';';
        mapping.date = 0;
        mapping.amount = 1;
        mapping.description = 2;
        mapping.datePattern = "dd/MM/yyyy";
        mapping.walletId = mWalletId;
        return mapping;
    }

    private CsvImportMapping commaMapping() {
        CsvImportMapping mapping = new CsvImportMapping();
        mapping.separator = ',';
        mapping.date = 0;
        mapping.description = 1;
        mapping.amount = 2;
        mapping.datePattern = "MM/dd/yyyy";
        mapping.walletId = mWalletId;
        return mapping;
    }

    /** Every saved row as its description and money, oldest first. */
    private String savedRows() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.DESCRIPTION, Contract.Transaction.MONEY}, null, null,
                Contract.Transaction.DATE + " ASC");
        assertNotNull(cursor);
        StringBuilder rows = new StringBuilder();
        try {
            while (cursor.moveToNext()) {
                rows.append(cursor.getString(0)).append(' ').append(cursor.getLong(1)).append('|');
            }
        } finally {
            cursor.close();
        }
        return rows.toString();
    }

    private CsvImportMapping deviceMapping() {
        CsvImportMapping mapping = new CsvImportMapping();
        mapping.separator = ',';
        mapping.date = 0;
        mapping.description = 1;
        mapping.amount = 2;
        mapping.category = 3;
        mapping.datePattern = "MM/dd/yyyy";
        mapping.spendingPositive = true;
        mapping.walletId = mWalletId;
        return mapping;
    }

    private void importFile(File file, CsvImportMapping mapping) throws IOException {
        CSVDataImporter importer = new CSVDataImporter(mContext, file, mapping);
        try {
            importer.importData();
        } finally {
            importer.close();
        }
    }

    private String refusal(File file, CsvImportMapping mapping) throws IOException {
        try {
            importFile(file, mapping);
            fail("the file has to be refused");
            return null;
        } catch (RuntimeException expected) {
            return expected.getMessage();
        }
    }

    private int count(Uri uri, String idColumn) {
        Cursor cursor = mResolver.query(uri, new String[] {idColumn}, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    private static File write(String contents) throws IOException {
        File file = File.createTempFile("tallybook-mapped-import", ".csv");
        file.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write(contents);
        }
        return file;
    }
}
