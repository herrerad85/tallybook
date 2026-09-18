package com.oriondev.moneywallet.storage.database.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Two things are pinned here.
 *
 * The first is chooseCategory, run for real against a mocked cursor. It is the half of the lookup
 * that decides which matched category an imported row is filed on. An income or expense category
 * is its direction, but a system category is not: the transaction editor derives one from the
 * category and writes it over the row on every save, so a row given a system category whose tag
 * implies the other direction is flipped the first time it is opened and saved, and the wallet
 * total moves by twice the amount.
 *
 * The second is the lookup around it, driven for real. The importer is run through the content
 * provider on a fresh database seeded the way a first launch seeds it, and the transaction row it
 * writes is read back out of the same provider, so the projection, the selection arguments and the
 * line that hands the cursor to the guard are all covered by what lands in the table.
 */
@RunWith(RobolectricTestRunner.class)
public class AbstractDataImporterTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final Date DATE = new Date(1565000000000L);
    private static final long MONEY = 1200L;

    private Context mContext;
    private ContentResolver mResolver;
    private TestImporter mImporter;
    private CurrencyUnit mEuro;
    private long mTaxId;
    private String mTaxName;
    private long mTransferId;
    private String mTransferName;
    private long mFood;

    /** AbstractDataImporter is abstract, so something concrete has to exist before insertTransaction can be called. */
    private static class TestImporter extends AbstractDataImporter {

        TestImporter(Context context) throws IOException {
            super(context, new File("unused.csv"));
        }

        @Override
        public void importData() {
        }

        @Override
        public int getRoundedAmounts() {
            return 0;
        }

        @Override
        public void close() {
        }
    }

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mResolver = mContext.getContentResolver();
        mImporter = new TestImporter(mContext);
        mEuro = CurrencyManager.getCurrency("EUR");
        assertNotNull("no EUR currency to import against", mEuro);
        // the seeded names come from string resources and the ids from the order the seed runs
        // in, so both are read out of the database and neither is written down here
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORIES,
                new String[] {Contract.Category.ID, Contract.Category.NAME,
                        Contract.Category.TYPE, Contract.Category.TAG}, null, null, null);
        assertNotNull(cursor);
        try {
            while (cursor.moveToNext()) {
                String tag = cursor.getString(cursor.getColumnIndex(Contract.Category.TAG));
                if (Contract.CategoryTag.TAX.equals(tag)) {
                    mTaxId = cursor.getLong(cursor.getColumnIndex(Contract.Category.ID));
                    mTaxName = cursor.getString(cursor.getColumnIndex(Contract.Category.NAME));
                } else if (Contract.CategoryTag.TRANSFER.equals(tag)) {
                    mTransferId = cursor.getLong(cursor.getColumnIndex(Contract.Category.ID));
                    mTransferName = cursor.getString(cursor.getColumnIndex(Contract.Category.NAME));
                }
            }
        } finally {
            cursor.close();
        }
        if (mTaxName == null || mTransferName == null) {
            fail("the fresh database does not carry a seeded tax and transfer category");
        }
        mFood = insertCategory("Food", Contract.CategoryType.EXPENSE);
    }

    private static final int TYPE = 0;
    private static final int TAG = 1;
    private static final int ID = 2;

    /**
     * A cursor over the given rows, each an id, a type and a tag, in the order the query returns
     * them. Position starts before the first row, and the rewind chooseCategory does is stubbed, so
     * a test can hand over a cursor that has already been read.
     */
    private static Cursor cursorOver(Object[]... rows) {
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnIndex(Contract.Category.TYPE)).thenReturn(TYPE);
        when(cursor.getColumnIndex(Contract.Category.TAG)).thenReturn(TAG);
        when(cursor.getColumnIndex(Contract.Category.ID)).thenReturn(ID);
        final int[] at = {-1};
        when(cursor.moveToNext()).thenAnswer(call -> ++at[0] < rows.length);
        when(cursor.moveToFirst()).thenAnswer(call -> {
            at[0] = 0;
            return rows.length > 0;
        });
        when(cursor.moveToPosition(-1)).thenAnswer(call -> {
            at[0] = -1;
            return false;
        });
        when(cursor.getLong(ID)).thenAnswer(call -> (Long) rows[at[0]][0]);
        when(cursor.getInt(TYPE)).thenAnswer(call -> ((Contract.CategoryType) rows[at[0]][1]).getValue());
        when(cursor.getString(TAG)).thenAnswer(call -> (String) rows[at[0]][2]);
        return cursor;
    }

    private static Object[] row(long id, Contract.CategoryType type, String tag) {
        return new Object[] {id, type, tag};
    }

    @Test
    public void anOrdinaryCategoryHoldsARowOfItsOwnDirection() {
        assertEquals(7L, AbstractDataImporter.chooseCategory(
                cursorOver(row(7L, Contract.CategoryType.EXPENSE, null)), Contract.Direction.EXPENSE));
        assertEquals(7L, AbstractDataImporter.chooseCategory(
                cursorOver(row(7L, Contract.CategoryType.INCOME, null)), Contract.Direction.INCOME));
        assertEquals("an expense category cannot hold an income row",
                AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                        cursorOver(row(7L, Contract.CategoryType.EXPENSE, null)), Contract.Direction.INCOME));
    }

    @Test
    public void aTagWithNoKnownDirectionIsRefusedForBoth() {
        // A transfer is written as two legs, one per wallet, so both directions appear
        // under the transfer tag and Category.getDirection has no case for it. What it returns
        // there is the same zero it returns for an expense, which is why these are asserted
        // rather than left to it.
        for (int direction : new int[] {Contract.Direction.EXPENSE, Contract.Direction.INCOME}) {
            assertEquals("the transfer tag has no direction of its own",
                    AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                            cursorOver(row(1L, Contract.CategoryType.SYSTEM, Contract.CategoryTag.TRANSFER)),
                            direction));
            assertEquals("a system row with no tag is not one this build seeded",
                    AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                            cursorOver(row(1L, Contract.CategoryType.SYSTEM, null)), direction));
            assertEquals("nor is one carrying a tag this build does not know, which a restore can "
                    + "write and a later version can introduce",
                    AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                            cursorOver(row(1L, Contract.CategoryType.SYSTEM, "system::tenth")), direction));
            assertEquals("an empty tag is not null and must be refused the same way",
                    AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                            cursorOver(row(1L, Contract.CategoryType.SYSTEM, "")), direction));
        }
    }

    @Test
    public void everyKnownTagIsOneTheDirectionLookupAnswersFor() {
        // Each tag is checked by being accepted for the direction it implies and refused for the
        // other. For an income tag that also catches its case going missing from
        // Category.getDirection, since the fall through zero is not the income the pair requires.
        // For an expense tag it does not: the fall through zero and a real expense case are the
        // same answer, and nothing here can tell them apart.
        String[] expense = {Contract.CategoryTag.CREDIT, Contract.CategoryTag.PAID_DEBT,
                Contract.CategoryTag.SAVING_DEPOSIT, Contract.CategoryTag.TAX,
                Contract.CategoryTag.TRANSFER_TAX};
        String[] income = {Contract.CategoryTag.DEBT, Contract.CategoryTag.PAID_CREDIT,
                Contract.CategoryTag.SAVING_WITHDRAW};
        for (String tag : expense) {
            assertEquals(tag + " is an expense", 3L, AbstractDataImporter.chooseCategory(
                    cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)), Contract.Direction.EXPENSE));
            assertEquals(tag + " must not hold an income row",
                    AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                            cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)), Contract.Direction.INCOME));
        }
        for (String tag : income) {
            assertEquals(tag + " is an income", 3L, AbstractDataImporter.chooseCategory(
                    cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)), Contract.Direction.INCOME));
            assertEquals(tag + " must not hold an expense row",
                    AbstractDataImporter.NO_CATEGORY, AbstractDataImporter.chooseCategory(
                            cursorOver(row(3L, Contract.CategoryType.SYSTEM, tag)), Contract.Direction.EXPENSE));
        }
    }

    @Test
    public void aRefusedCandidateIsPassedOverRatherThanEndingTheSearch() {
        assertEquals("a system row the guard refuses must not hide a category the user made "
                + "behind it",
                40L, AbstractDataImporter.chooseCategory(cursorOver(
                        row(8L, Contract.CategoryType.SYSTEM, Contract.CategoryTag.SAVING_DEPOSIT),
                        row(40L, Contract.CategoryType.INCOME, null)), Contract.Direction.INCOME));
        assertEquals("the first candidate that holds the row wins, and the query orders them "
                + "newest first",
                40L, AbstractDataImporter.chooseCategory(cursorOver(
                        row(40L, Contract.CategoryType.EXPENSE, null),
                        row(8L, Contract.CategoryType.SYSTEM, Contract.CategoryTag.SAVING_DEPOSIT)),
                        Contract.Direction.EXPENSE));
        assertEquals("an empty cursor finds nothing",
                AbstractDataImporter.NO_CATEGORY,
                AbstractDataImporter.chooseCategory(cursorOver(), Contract.Direction.EXPENSE));
    }

    /**
     * Every other lookup in the importer opens with moveToFirst, so wrapping this one in the same
     * call is the likeliest edit anyone makes to it. The scan rewinds, which keeps that edit from
     * quietly dropping the first and best candidate.
     */
    @Test
    public void aCursorSomebodyElseAlreadyMovedStillFindsTheFirstRow() {
        Cursor cursor = cursorOver(row(40L, Contract.CategoryType.EXPENSE, null));
        cursor.moveToFirst();
        assertEquals(40L, AbstractDataImporter.chooseCategory(cursor, Contract.Direction.EXPENSE));
    }

    @Test
    public void anExpenseRowNamingTheSeededTaxCategoryLandsOnIt() {
        int before = countCategories();
        importRow(mTaxName, Contract.Direction.EXPENSE);
        Cursor transaction = newestTransaction();
        try {
            assertEquals("the row did not reach the seeded tax category", mTaxId,
                    transaction.getLong(transaction.getColumnIndex(Contract.Transaction.CATEGORY_ID)));
            assertEquals(Contract.Direction.EXPENSE,
                    transaction.getInt(transaction.getColumnIndex(Contract.Transaction.DIRECTION)));
            assertEquals(MONEY,
                    transaction.getLong(transaction.getColumnIndex(Contract.Transaction.MONEY)));
        } finally {
            transaction.close();
        }
        assertEquals("a category was created for a name the database already holds",
                before, countCategories());
    }

    @Test
    public void anIncomeRowNamingTheSeededTaxCategoryIsRefusedItAndGetsAnOrdinaryOne() {
        int before = countCategories();
        importRow(mTaxName, Contract.Direction.INCOME);
        long categoryId = newestTransactionCategory();
        assertNotEquals("an income row was filed on the tax category, which the editor would "
                + "rewrite to an expense on the first save", mTaxId, categoryId);
        Cursor category = categoryRow(categoryId);
        try {
            assertEquals(Contract.CategoryType.INCOME.getValue(),
                    category.getInt(category.getColumnIndex(Contract.Category.TYPE)));
            assertNull(category.getString(category.getColumnIndex(Contract.Category.TAG)));
        } finally {
            category.close();
        }
        assertEquals(before + 1, countCategories());
    }

    @Test
    public void aRowNamingTheTransferCategoryNeverLandsOnItInEitherDirection() {
        int before = countCategories();
        for (int direction : new int[] {Contract.Direction.EXPENSE, Contract.Direction.INCOME}) {
            importRow(mTransferName, direction);
            long categoryId = newestTransactionCategory();
            assertNotEquals("a transfer is written as two legs, so both directions appear under "
                    + "that tag and neither can be filed on it", mTransferId, categoryId);
            Cursor category = categoryRow(categoryId);
            try {
                assertEquals(direction == Contract.Direction.INCOME
                                ? Contract.CategoryType.INCOME.getValue()
                                : Contract.CategoryType.EXPENSE.getValue(),
                        category.getInt(category.getColumnIndex(Contract.Category.TYPE)));
            } finally {
                category.close();
            }
        }
        assertEquals(before + 2, countCategories());
    }

    @Test
    public void aUserExpenseCategoryIsReusedByAnExpenseRowAndNotByAnIncomeRow() {
        int before = countCategories();
        importRow("Food", Contract.Direction.EXPENSE);
        assertEquals("an expense row did not reuse the expense category of its own name",
                mFood, newestTransactionCategory());
        assertEquals(before, countCategories());

        importRow("Food", Contract.Direction.INCOME);
        long income = newestTransactionCategory();
        assertNotEquals(mFood, income);
        Cursor category = categoryRow(income);
        try {
            assertEquals("Food", category.getString(category.getColumnIndex(Contract.Category.NAME)));
            assertEquals(Contract.CategoryType.INCOME.getValue(),
                    category.getInt(category.getColumnIndex(Contract.Category.TYPE)));
        } finally {
            category.close();
        }
        assertEquals(before + 1, countCategories());

        importRow("Food", Contract.Direction.INCOME);
        assertEquals("the second income row did not reuse the income category the first made",
                income, newestTransactionCategory());
        assertEquals(before + 1, countCategories());
    }

    @Test
    public void aRowTheWalletAlreadyHoldsIsSkippedAndCounted() throws IOException {
        importRow("Food", Contract.Direction.EXPENSE);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    /**
     * Only rows that were in the wallet before the import count, so a row a file carries twice is
     * saved twice the first time, and a third copy is saved on the next import.
     */
    @Test
    public void aRowRepeatedInOneFileIsSavedEachTimeItIsNew() throws IOException {
        importRow("Food", Contract.Direction.EXPENSE);
        importRow("Food", Contract.Direction.EXPENSE);
        assertEquals(2, countTransactions());
        assertEquals(0, mImporter.getAlreadySavedRows());

        TestImporter again = new TestImporter(mContext);
        for (int i = 0; i < 3; i++) {
            again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                    "desc", null, null, null, null);
        }
        assertEquals(3, countTransactions());
        assertEquals(2, again.getAlreadySavedRows());
    }

    @Test
    public void noDescriptionMatchesAnEmptyOne() throws IOException {
        mImporter.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                null, null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    @Test
    public void aDescriptionSavedWithSpacesMatchesTheTrimmedOne() throws IOException {
        mImporter.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "Coffee ", null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "Coffee", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    @Test
    public void aZeroExpenseMatchesTheZeroIncomeTheExportTurnsItInto() throws IOException {
        mImporter.insertTransaction("Cash", mEuro, "Food", DATE, 0L, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, 0L, Contract.Direction.INCOME,
                "desc", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    @Test
    public void aWalletNameSavedWithASpaceIsFoundByTheTrimmedOne() throws IOException {
        mImporter.insertTransaction("Cash ", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    /** SQLite TRIM strips only spaces unless told the rest, and Java trim strips a tab too. */
    @Test
    public void aWalletNameSavedWithATabIsFoundByTheTrimmedOne() throws IOException {
        mImporter.insertTransaction("Cash\t", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    /** SQLite stops reading a statement at a NUL, so the name must never be part of the SQL text. */
    @Test
    public void aWalletNameWithANulInsideIsImported() throws IOException {
        mImporter.insertTransaction("Ca\0sh", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Ca\0sh", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        assertEquals(1, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    /** A wallet saved under the exact name wins over a newer one that only matches trimmed. */
    @Test
    public void theWalletWithTheExactNameWinsOverANewerPaddedOne() throws IOException {
        importRow("Food", Contract.Direction.EXPENSE);
        mImporter.insertTransaction("Cash ", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "other", null, null, null, null);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        assertEquals(2, countTransactions());
        assertEquals(1, again.getAlreadySavedRows());
    }

    /** Each field the match compares, changed on its own, makes a row that is saved. */
    @Test
    public void aRowDifferingInAnyComparedFieldIsSaved() throws IOException {
        importRow("Food", Contract.Direction.EXPENSE);
        TestImporter again = new TestImporter(mContext);
        again.insertTransaction("Other", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        again.insertTransaction("Cash", mEuro, "Food", new Date(DATE.getTime() + 1000L), MONEY,
                Contract.Direction.EXPENSE, "desc", null, null, null, null);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY + 1, Contract.Direction.EXPENSE,
                "desc", null, null, null, null);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.INCOME,
                "desc", null, null, null, null);
        again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                "other", null, null, null, null);
        assertEquals(6, countTransactions());
        assertEquals(0, again.getAlreadySavedRows());
    }

    /**
     * The match reads the same rows a CSV export writes, so a debt row, a saving row and a transfer
     * fee are found again whatever type they were saved with, and a transfer leg, which the export
     * leaves out, is not.
     */
    @Test
    public void everyRowTheExportWritesIsMatchedAndATransferLegIsNot() throws IOException {
        importRow("Food", Contract.Direction.EXPENSE);
        long wallet = newestTransactionWallet();
        insertTyped(wallet, "debt", Contract.TransactionType.DEBT, systemCategory(Contract.CategoryTag.DEBT));
        insertTyped(wallet, "saving", Contract.TransactionType.SAVING,
                systemCategory(Contract.CategoryTag.SAVING_DEPOSIT));
        insertTyped(wallet, "fee", Contract.TransactionType.TRANSFER,
                systemCategory(Contract.CategoryTag.TRANSFER_TAX));
        insertTyped(wallet, "leg", Contract.TransactionType.TRANSFER, mTransferId);
        TestImporter again = new TestImporter(mContext);
        for (String description : new String[] {"debt", "saving", "fee", "leg"}) {
            again.insertTransaction("Cash", mEuro, "Food", DATE, MONEY, Contract.Direction.EXPENSE,
                    description, null, null, null, null);
        }
        assertEquals(3, again.getAlreadySavedRows());
        assertEquals(6, countTransactions());
    }

    private long newestTransactionWallet() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.WALLET_ID}, null, null, Contract.Transaction.ID + " DESC");
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private void insertTyped(long wallet, String description, int type, long category) {
        ContentValues values = new ContentValues();
        values.put(Contract.Transaction.MONEY, MONEY);
        values.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(DATE));
        values.put(Contract.Transaction.DESCRIPTION, description);
        values.put(Contract.Transaction.CATEGORY_ID, category);
        values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.Transaction.TYPE, type);
        values.put(Contract.Transaction.WALLET_ID, wallet);
        values.put(Contract.Transaction.CONFIRMED, true);
        values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        assertNotNull(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }

    private long systemCategory(String tag) {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORIES,
                new String[] {Contract.Category.ID}, Contract.Category.TAG + " = ?",
                new String[] {tag}, null);
        assertNotNull(cursor);
        try {
            assertTrue("no system category tagged " + tag, cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private int countTransactions() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID}, null, null, null);
        assertNotNull(cursor);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }

    private void importRow(String category, int direction) {
        mImporter.insertTransaction("Cash", mEuro, category, DATE, MONEY, direction,
                "desc", null, null, null, null);
    }

    private long insertCategory(String name, Contract.CategoryType type) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, type.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private Cursor newestTransaction() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID,
                        Contract.Transaction.DIRECTION, Contract.Transaction.MONEY},
                null, null, Contract.Transaction.ID + " DESC");
        assertNotNull(cursor);
        assertTrue("the importer wrote no transaction row", cursor.moveToFirst());
        return cursor;
    }

    private long newestTransactionCategory() {
        Cursor cursor = newestTransaction();
        try {
            return cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ID));
        } finally {
            cursor.close();
        }
    }

    private Cursor categoryRow(long categoryId) {
        Cursor cursor = mResolver.query(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId),
                new String[] {Contract.Category.ID, Contract.Category.NAME,
                        Contract.Category.TYPE, Contract.Category.TAG}, null, null, null);
        assertNotNull(cursor);
        assertTrue("no category row " + categoryId, cursor.moveToFirst());
        return cursor;
    }

    private int countCategories() {
        Cursor cursor = mResolver.query(DataContentProvider.CONTENT_CATEGORIES,
                new String[] {Contract.Category.ID}, null, null, null);
        assertNotNull(cursor);
        int count = cursor.getCount();
        cursor.close();
        return count;
    }
}
