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

package com.oriondev.moneywallet.storage.database;

import android.app.Instrumentation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.model.Money;
import com.oriondev.moneywallet.utils.DateUtils;
import org.apache.commons.io.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertSame;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

/**
 * Created by andrea on 28/08/18.
 */
@LargeTest
public class SQLDatabaseTest {

    private Context mContext;
    private SQLDatabase mDatabase;
    private long mRealDatabaseLength;

    /**
     * Keeps the suite off the storage of the build it is running against. Database names are
     * prefixed and the attachment directory moves into a subdirectory, so SQLDatabase opens
     * test.database.db and deletes attachment files under a test folder, never the real ones
     * beside them. This replaces android.test.RenamingDelegatingContext, which shipped in the
     * android.test.runner library that this change removes.
     */
    private static class TestStorageContext extends ContextWrapper {

        private static final String PREFIX = "test.";
        private static final String EXTERNAL_SUBDIR = "test";

        TestStorageContext(Context base) {
            super(base);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                                                   SQLiteDatabase.CursorFactory factory) {
            return super.openOrCreateDatabase(PREFIX + name, mode, factory);
        }

        @Override
        public SQLiteDatabase openOrCreateDatabase(String name, int mode,
                                                   SQLiteDatabase.CursorFactory factory,
                                                   DatabaseErrorHandler errorHandler) {
            return super.openOrCreateDatabase(PREFIX + name, mode, factory, errorHandler);
        }

        @Override
        public File getDatabasePath(String name) {
            return super.getDatabasePath(PREFIX + name);
        }

        @Override
        public boolean deleteDatabase(String name) {
            return super.deleteDatabase(PREFIX + name);
        }

        /**
         * SQLDatabase.getAttachmentFolder resolves this and the delete paths then call delete()
         * on file names taken from a column, and a restored backup can put any name in that
         * column. The folder need not exist, since nothing lists it and a delete that finds
         * nothing returns false.
         */
        @Override
        public File getExternalFilesDir(String type) {
            File external = super.getExternalFilesDir(type);
            return external != null ? new File(external, EXTERNAL_SUBDIR) : null;
        }

        /**
         * Without this, SQLDatabase.getShared normalizes straight past the wrapper and the shared
         * helper opens the real ledger, so the whole shared static was untestable. ContextWrapper
         * answers this from the context it wraps, and every redirect above lives on this object.
         *
         * The cost is that the static is process wide, so a case that installs a test helper into
         * it has to put it back. releaseSharedHelper does that.
         */
        @Override
        public Context getApplicationContext() {
            return this;
        }
    }

    @Before
    public void setUp() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Context targetContext = instrumentation.getTargetContext();
        File testDatabase = targetContext.getDatabasePath(
                TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME);
        mRealDatabaseLength = targetContext.getDatabasePath(SQLDatabase.DATABASE_NAME).length();
        // the prefix has to name a different file, which is what everything below rests on and
        // is what a check written against the prefix itself cannot tell you
        assertFalse(targetContext.getDatabasePath(SQLDatabase.DATABASE_NAME).getPath()
                .equals(testDatabase.getPath()));
        mContext = new TestStorageContext(targetContext);
        assertEquals(testDatabase.getPath(),
                mContext.getDatabasePath(SQLDatabase.DATABASE_NAME).getPath());
        // remove the previous case's database. Through the unwrapped context, so the delete every
        // case makes cannot land on the real database when the wrapper is the thing that is broken
        targetContext.deleteDatabase(TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME);
        // create a new database for testing purposes
        mDatabase = new SQLDatabase(mContext, SQLDatabase.DATABASE_NAME);
        // and ask SQLDatabase which file it actually opened, whichever way it got there
        assertEquals(testDatabase.getPath(), mDatabase.getWritableDatabase().getPath());
        // point the process wide shared helper at the same test file. These cases run inside the
        // app's own process, so its providers have already put a helper built on the real context
        // into that static, and getShared hands back whatever is there. Without this an
        // inSharedTransaction case reads and writes the real ledger and only its rollback keeps
        // that from showing. resetShared replaces it unconditionally; releaseSharedHelper puts a
        // real one back afterwards
        SQLDatabase.resetShared(mContext);
        assertEquals(testDatabase.getPath(),
                sharedPath());
    }

    /**
     * Puts the process wide shared helper back on the real context. Any case that reached
     * getShared through the wrapper left a helper pointed at the test database in a static that
     * outlives this class. The replacement has opened nothing, since SQLiteOpenHelper opens on
     * first use, so the real ledger is not touched by putting it there.
     */
    @After
    public void releaseSharedHelper() {
        // the staged import goes first. A case that opened one and failed before closing it would
        // otherwise leave this thread writing into the staged file for every later case in the
        // run, since the redirect lives on the thread and the runner reuses it
        SQLDatabase.closeStaging();
        SQLDatabase.resetShared(InstrumentationRegistry.getInstrumentation().getTargetContext());
    }

    @After
    public void tearDown() {
        if (mDatabase == null) {
            return;
        }
        mDatabase.close();
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        targetContext.deleteDatabase(TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME);
        targetContext.deleteDatabase(
                TestStorageContext.PREFIX + SQLDatabaseImporter.STAGING_DATABASE_NAME);
        // the database the build under test owns was not unlinked, and was not replaced by a
        // smaller one. Against the size setUp saw and not against zero, so a device without that
        // database reads nothing against nothing and passes instead of failing every case
        assertTrue(targetContext.getDatabasePath(SQLDatabase.DATABASE_NAME).length()
                >= mRealDatabaseLength);
    }

    //////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////// INTERNAL METHODS FOR TESTING ///////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////

    private String getObjectIds(Long[] ids) {
        if (ids != null && ids.length > 0) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < ids.length; i++) {
                if (i != 0) {
                    builder.append(",");
                }
                builder.append(String.format(Locale.ENGLISH, "<%d>", ids[i]));
            }
            return builder.toString();
        }
        return null;
    }

    private long[] parseIds(String list) {
        if (!TextUtils.isEmpty(list)) {
            System.out.println(list);
            String[] encodedIds = list.split(",");
            long[] ids = new long[encodedIds.length];
            for (int i = 0; i < encodedIds.length; i++) {
                String encodedId = encodedIds[i];
                if (encodedId.startsWith("<") && encodedId.endsWith(">")) {
                    ids[i] = Long.parseLong(encodedId.substring(1, encodedId.length() - 1));
                } else {
                    String message = "The ids column not follow the pattern at index %d. Content: %s";
                    throw new SQLiteException(String.format(Locale.ENGLISH, message, i, list));
                }
            }
            return ids;
        }
        return null;
    }

    private int checkCursorMinSize(Cursor cursor, int minSize) {
        assertNotNull(cursor);
        int cursorSize = cursor.getCount();
        assertEquals(true, cursorSize >= minSize);
        cursor.close();
        return cursorSize;
    }

    /**
     * Rows straight from the table, with no deleted = 0 filter on the way. Every accessor this
     * class otherwise reads through carries that filter, so a row flagged as deleted and a row
     * that is gone look the same through them, and only a direct read separates the two. The
     * selection is where a caller names the flag it is asking about.
     */
    private void assertRowCount(String table, String selection, int expected) {
        Cursor cursor = mDatabase.getReadableDatabase()
                .query(table, null, selection, null, null, null, null);
        assertNotNull(cursor);
        try {
            // named, because every caller passes the same expected count and the same selection
            assertEquals(table, expected, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    private void checkCursorSize(Cursor cursor, int expectedSize) {
        assertNotNull(cursor);
        if (cursor.getCount() != expectedSize) {
            System.out.println("checkCursorSize is going to fail! printing cursor:");
            StringBuilder rowBuilder = new StringBuilder();
            rowBuilder.append(" | ");
            for (int i = 0; i < cursor.getColumnCount(); i++) {
                rowBuilder.append(cursor.getColumnName(i));
                rowBuilder.append(" | ");
            }
            System.out.println(rowBuilder);
            while (cursor.moveToNext()) {
                rowBuilder = new StringBuilder();
                rowBuilder.append(" | ");
                for (int i = 0; i < cursor.getColumnCount(); i++) {
                    rowBuilder.append(cursor.getString(i));
                    rowBuilder.append(" | ");
                }
                System.out.println(rowBuilder);
            }
        }
        assertEquals(expectedSize, cursor.getCount());
        cursor.close();
    }

    private void checkNullable(Cursor cursor, Object value, int index) {
        assertEquals(value == null, cursor.isNull(index));
        if (!cursor.isNull(index)) {
            if (value instanceof Long) {
                assertEquals((long) value, cursor.getLong(index));
            } else if (value instanceof Integer) {
                assertEquals((int) value, cursor.getInt(index));
            } else if (value instanceof String) {
                assertEquals((String) value, cursor.getString(index));
            } else if (value instanceof Double) {
                assertEquals(value, cursor.getDouble(index));
            }
        }
    }

    private void checkWalletId(long id, String name, String icon, String currency, String note,
                               boolean countInTotal, long startMoney, long totalMoney, boolean archived,
                               String tag) {
        Cursor cursor = mDatabase.getWallet(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(id, cursor.getLong(cursor.getColumnIndex(Contract.Wallet.ID)));
        assertEquals(name, cursor.getString(cursor.getColumnIndex(Contract.Wallet.NAME)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Wallet.ICON)));
        assertEquals(currency, cursor.getString(cursor.getColumnIndex(Contract.Wallet.CURRENCY)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Wallet.NOTE)));
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Wallet.COUNT_IN_TOTAL)));
        assertEquals(startMoney, cursor.getLong(cursor.getColumnIndex(Contract.Wallet.START_MONEY)));
        assertEquals(archived, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Wallet.ARCHIVED)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Wallet.TAG)));
        Money money = Money.parse(cursor.getString(cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY)));
        assertEquals(totalMoney, money.getMoney(currency));
        cursor.close();
    }

    private long insertWallet(String name, String icon, String currency, String note,
                             boolean countInTotal, long startMoney, boolean archived,
                             String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, icon);
        contentValues.put(Contract.Wallet.CURRENCY, currency);
        contentValues.put(Contract.Wallet.NOTE, note);
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.Wallet.START_MONEY, startMoney);
        contentValues.put(Contract.Wallet.ARCHIVED, archived);
        contentValues.put(Contract.Wallet.TAG, tag);
        return mDatabase.insertWallet(contentValues);
    }

    private int updateWallet(long walletId, String name, String icon, String currency, String note,
                             boolean countInTotal, long startMoney, boolean archived,
                             String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, icon);
        contentValues.put(Contract.Wallet.CURRENCY, currency);
        contentValues.put(Contract.Wallet.NOTE, note);
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.Wallet.START_MONEY, startMoney);
        contentValues.put(Contract.Wallet.ARCHIVED, archived);
        contentValues.put(Contract.Wallet.TAG, tag);
        return mDatabase.updateWallet(walletId, contentValues);
    }

    private void checkCategoryId(long categoryId, String name, String icon, int type, Long parentId,
                                    boolean showReport, String tag) {
        Cursor cursor = mDatabase.getCategory(categoryId, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(name, cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Category.ICON)));
        assertEquals(type, cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE)));
        checkNullable(cursor, parentId, cursor.getColumnIndex(Contract.Category.PARENT));
        assertEquals(showReport, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Category.SHOW_REPORT)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Category.TAG)));
        cursor.close();
    }

    private long insertCategory(String name, String icon, int type, Long parentId,
                               boolean showReport, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, name);
        contentValues.put(Contract.Category.ICON, icon);
        contentValues.put(Contract.Category.TYPE, type);
        contentValues.put(Contract.Category.PARENT, parentId);
        contentValues.put(Contract.Category.SHOW_REPORT, showReport);
        contentValues.put(Contract.Category.TAG, tag);
        return mDatabase.insertCategory(contentValues);
    }

    private long getSystemCategory(String tag) {
        String[] projection = new String[] {Contract.Category.ID};
        String selection = Contract.Category.TAG + " = ?";
        String[] selectionArgs = new String[] {tag};
        Cursor cursor = mDatabase.getCategories(projection, selection, selectionArgs, null);
        assertNotNull(cursor);
        assertEquals(true, cursor.moveToFirst());
        long categoryId = cursor.getLong(cursor.getColumnIndex(Contract.Category.ID));
        cursor.close();
        return categoryId;
    }

    private int updateCategory(long categoryId, String name, String icon, int type, Long parentId,
                               boolean showReport, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, name);
        contentValues.put(Contract.Category.ICON, icon);
        contentValues.put(Contract.Category.TYPE, type);
        contentValues.put(Contract.Category.PARENT, parentId);
        contentValues.put(Contract.Category.SHOW_REPORT, showReport);
        contentValues.put(Contract.Category.TAG, tag);
        return mDatabase.updateCategory(categoryId, contentValues);
    }

    private void checkEventId(long id, String name, String icon, Date startDate, Date endDate,
                              String note, String tag) {
        Cursor cursor = mDatabase.getEvent(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(name, cursor.getString(cursor.getColumnIndex(Contract.Event.NAME)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Event.ICON)));
        assertEquals(DateUtils.getSQLDateString(startDate), cursor.getString(cursor.getColumnIndex(Contract.Event.START_DATE)));
        assertEquals(DateUtils.getSQLDateString(endDate), cursor.getString(cursor.getColumnIndex(Contract.Event.END_DATE)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Event.NOTE)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Event.TAG)));
        cursor.close();
    }

    private long insertEvent(String name, String icon, Date startDate, Date endDate,
                             String note, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Event.NAME, name);
        contentValues.put(Contract.Event.ICON, icon);
        contentValues.put(Contract.Event.START_DATE, DateUtils.getSQLDateString(startDate));
        contentValues.put(Contract.Event.END_DATE, DateUtils.getSQLDateString(endDate));
        contentValues.put(Contract.Event.NOTE, note);
        contentValues.put(Contract.Event.TAG, tag);
        return mDatabase.insertEvent(contentValues);
    }

    private int updateEvent(long id, String name, String icon, Date startDate, Date endDate,
                            String note, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Event.NAME, name);
        contentValues.put(Contract.Event.ICON, icon);
        contentValues.put(Contract.Event.START_DATE, DateUtils.getSQLDateString(startDate));
        contentValues.put(Contract.Event.END_DATE, DateUtils.getSQLDateString(endDate));
        contentValues.put(Contract.Event.NOTE, note);
        contentValues.put(Contract.Event.TAG, tag);
        return mDatabase.updateEvent(id, contentValues);
    }

    private void checkPlaceId(long id, String name, String icon, String address, Double latitude,
                              Double longitude, String tag) {
        Cursor cursor = mDatabase.getPlace(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(name, cursor.getString(cursor.getColumnIndex(Contract.Place.NAME)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Place.ICON)));
        assertEquals(address, cursor.getString(cursor.getColumnIndex(Contract.Place.ADDRESS)));
        checkNullable(cursor, latitude, cursor.getColumnIndex(Contract.Place.LATITUDE));
        checkNullable(cursor, longitude, cursor.getColumnIndex(Contract.Place.LONGITUDE));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Place.TAG)));
        cursor.close();
    }

    private long insertPlace(String name, String icon, String address, Double latitude,
                             Double longitude, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Place.NAME, name);
        contentValues.put(Contract.Place.ICON, icon);
        contentValues.put(Contract.Place.ADDRESS, address);
        contentValues.put(Contract.Place.LATITUDE, latitude);
        contentValues.put(Contract.Place.LONGITUDE, longitude);
        contentValues.put(Contract.Place.TAG, tag);
        return mDatabase.insertPlace(contentValues);
    }

    private int updatePlace(long id, String name, String icon, String address, Double latitude,
                            Double longitude, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Place.NAME, name);
        contentValues.put(Contract.Place.ICON, icon);
        contentValues.put(Contract.Place.ADDRESS, address);
        contentValues.put(Contract.Place.LATITUDE, latitude);
        contentValues.put(Contract.Place.LONGITUDE, longitude);
        contentValues.put(Contract.Place.TAG, tag);
        return mDatabase.updatePlace(id, contentValues);
    }

    private long insertCategoryRule(String pattern, long categoryId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.CategoryRule.PATTERN, pattern);
        contentValues.put(Contract.CategoryRule.CATEGORY_ID, categoryId);
        return mDatabase.insertCategoryRule(contentValues);
    }

    /**
     * The category the rules name for a description, read the way the content provider reads it.
     *
     * @param description text to match the patterns against.
     * @return the category id, or null when no rule matches.
     */
    private Long matchCategoryRule(String description) {
        Cursor cursor = mDatabase.getCategoryRuleMatch(description);
        assertNotNull(cursor);
        Long categoryId = null;
        if (cursor.moveToFirst()) {
            categoryId = cursor.getLong(cursor.getColumnIndex(Contract.CategoryRule.CATEGORY_ID));
        }
        cursor.close();
        return categoryId;
    }

    private void checkCategoryRuleId(long id, String pattern, long categoryId, int index) {
        Cursor cursor = mDatabase.getCategoryRule(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(pattern, cursor.getString(cursor.getColumnIndex(Contract.CategoryRule.PATTERN)));
        assertEquals(categoryId, cursor.getLong(cursor.getColumnIndex(Contract.CategoryRule.CATEGORY_ID)));
        assertEquals(index, cursor.getInt(cursor.getColumnIndex(Contract.CategoryRule.INDEX)));
        cursor.close();
    }

    @Test
    public void categoryRulesAreAppendedInOrder() throws Exception {
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        long first = insertCategoryRule("tesco", food);
        long second = insertCategoryRule("shell", petrol);
        checkCategoryRuleId(first, "tesco", food, 0);
        checkCategoryRuleId(second, "shell", petrol, 1);
        checkCursorSize(mDatabase.getCategoryRules(null, null, null, null), 2);
    }

    @Test
    public void categoryRuleMatchesDescriptionWhateverTheCase() throws Exception {
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        insertCategoryRule("TeSCo", food);
        assertEquals(Long.valueOf(food), matchCategoryRule("Weekly shop at tesco extra"));
        assertEquals(Long.valueOf(food), matchCategoryRule("TESCO"));
        assertNull(matchCategoryRule("Corner shop"));
    }

    @Test
    public void categoryRuleMatchTakesTheLowestIndexThatFits() throws Exception {
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        // both patterns are in the description, and only the one appended first answers
        insertCategoryRule("tesco", food);
        insertCategoryRule("petrol", petrol);
        assertEquals(Long.valueOf(food), matchCategoryRule("tesco petrol station"));
    }

    @Test
    public void categoryRulePatternIsNotAWildcard() throws Exception {
        // a percent and an underscore are wildcards under LIKE, and the lookup uses instr, so
        // they stand for themselves and match nothing else
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        insertCategoryRule("100% ju_ce", food);
        assertNull(matchCategoryRule("orange juice"));
        assertEquals(Long.valueOf(food), matchCategoryRule("Bought 100% ju_ce today"));
    }

    @Test
    public void updateCategoryRuleReplacesPatternAndCategory() throws Exception {
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        long rule = insertCategoryRule("tesco", food);
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.CategoryRule.PATTERN, "shell");
        contentValues.put(Contract.CategoryRule.CATEGORY_ID, petrol);
        assertEquals(1, mDatabase.updateCategoryRule(rule, contentValues));
        checkCategoryRuleId(rule, "shell", petrol, 0);
        assertNull(matchCategoryRule("tesco extra"));
        assertEquals(Long.valueOf(petrol), matchCategoryRule("shell garage"));
    }

    @Test
    public void deleteCategoryRuleLeavesTheOthers() throws Exception {
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        long first = insertCategoryRule("tesco", food);
        insertCategoryRule("shell", petrol);
        assertEquals(1, mDatabase.deleteCategoryRule(first));
        checkCursorSize(mDatabase.getCategoryRules(null, null, null, null), 1);
        assertNull(matchCategoryRule("tesco extra"));
        assertEquals(Long.valueOf(petrol), matchCategoryRule("shell garage"));
    }

    @Test
    public void deletingACategoryTakesItsRulesWithIt() throws Exception {
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        insertCategoryRule("tesco", food);
        insertCategoryRule("shell", petrol);
        mDatabase.deleteCategory(food);
        checkCursorSize(mDatabase.getCategoryRules(null, null, null, null), 1);
        assertNull(matchCategoryRule("tesco extra"));
    }

    private void checkPersonId(long id, String name, String icon, String note, String tag) {
        Cursor cursor = mDatabase.getPerson(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(name, cursor.getString(cursor.getColumnIndex(Contract.Person.NAME)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Person.ICON)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Person.NOTE)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Person.TAG)));
        cursor.close();
    }

    private long insertPerson(String name, String icon, String note, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Person.NAME, name);
        contentValues.put(Contract.Person.ICON, icon);
        contentValues.put(Contract.Person.NOTE, note);
        contentValues.put(Contract.Person.TAG, tag);
        return mDatabase.insertPerson(contentValues);
    }

    private int updatePerson(long id, String name, String icon, String note, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Person.NAME, name);
        contentValues.put(Contract.Person.ICON, icon);
        contentValues.put(Contract.Person.NOTE, note);
        contentValues.put(Contract.Person.TAG, tag);
        return mDatabase.updatePerson(id, contentValues);
    }

    private void checkAttachmentId(long id, String file, String name, String type,
                                   long size, String tag) {
        Cursor cursor = mDatabase.getAttachment(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(file, cursor.getString(cursor.getColumnIndex(Contract.Attachment.FILE)));
        assertEquals(name, cursor.getString(cursor.getColumnIndex(Contract.Attachment.NAME)));
        assertEquals(type, cursor.getString(cursor.getColumnIndex(Contract.Attachment.TYPE)));
        assertEquals(size, cursor.getLong(cursor.getColumnIndex(Contract.Attachment.SIZE)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Attachment.TAG)));
        cursor.close();
    }

    private long insertAttachment(String file, String name, String type, long size, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Attachment.FILE, file);
        contentValues.put(Contract.Attachment.NAME, name);
        contentValues.put(Contract.Attachment.TYPE, type);
        contentValues.put(Contract.Attachment.SIZE, size);
        contentValues.put(Contract.Attachment.TAG, tag);
        return mDatabase.insertAttachment(contentValues);
    }

    private int updateAttachment(long id, String file, String name, String type,
                                 long size, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Attachment.FILE, file);
        contentValues.put(Contract.Attachment.NAME, name);
        contentValues.put(Contract.Attachment.TYPE, type);
        contentValues.put(Contract.Attachment.SIZE, size);
        contentValues.put(Contract.Attachment.TAG, tag);
        return mDatabase.updateAttachment(id, contentValues);
    }

    private void checkDebtId(long id, int type, String icon, String description, Date date, Date exp,
                             long walletId, String note, Long placeId, long money, boolean archived,
                             Long[] peopleIds, String tag) {
        Cursor cursor = mDatabase.getDebt(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(type, cursor.getInt(cursor.getColumnIndex(Contract.Debt.TYPE)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Debt.ICON)));
        assertEquals(description, cursor.getString(cursor.getColumnIndex(Contract.Debt.DESCRIPTION)));
        assertEquals(DateUtils.getSQLDateString(date), cursor.getString(cursor.getColumnIndex(Contract.Debt.DATE)));
        checkNullable(cursor, exp != null ? DateUtils.getSQLDateString(exp) : null, cursor.getColumnIndex(Contract.Debt.EXPIRATION_DATE));
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndex(Contract.Debt.WALLET_ID)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Debt.NOTE)));
        checkNullable(cursor, placeId, cursor.getColumnIndex(Contract.Debt.PLACE_ID));
        assertEquals(money, cursor.getLong(cursor.getColumnIndex(Contract.Debt.MONEY)));
        assertEquals(archived, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Debt.ARCHIVED)));
        assertEquals(getObjectIds(peopleIds), cursor.getString(cursor.getColumnIndex(Contract.Debt.PEOPLE_IDS)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Debt.TAG)));
        cursor.close();
    }

    private long insertDebt(int type, String icon, String description, Date date, Date exp,
                            long walletId, String note, Long placeId, long money, boolean archived,
                            Long[] peopleIds, String tag, boolean addMasterTransaction) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Debt.TYPE, type);
        contentValues.put(Contract.Debt.ICON, icon);
        contentValues.put(Contract.Debt.DESCRIPTION, description);
        contentValues.put(Contract.Debt.DATE, DateUtils.getSQLDateString(date));
        contentValues.put(Contract.Debt.EXPIRATION_DATE, exp != null ? DateUtils.getSQLDateString(exp) : null);
        contentValues.put(Contract.Debt.WALLET_ID, walletId);
        contentValues.put(Contract.Debt.NOTE, note);
        contentValues.put(Contract.Debt.PLACE_ID, placeId);
        contentValues.put(Contract.Debt.MONEY, money);
        contentValues.put(Contract.Debt.ARCHIVED, archived);
        contentValues.put(Contract.Debt.PEOPLE_IDS, getObjectIds(peopleIds));
        contentValues.put(Contract.Debt.TAG, tag);
        contentValues.put(Contract.Debt.INSERT_MASTER_TRANSACTION, addMasterTransaction);
        return mDatabase.insertDebt(contentValues);
    }

    private int updateDebt(long id, int type, String icon, String description, Date date, Date exp,
                           long walletId, String note, Long placeId, long money, boolean archived,
                           Long[] peopleIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Debt.TYPE, type);
        contentValues.put(Contract.Debt.ICON, icon);
        contentValues.put(Contract.Debt.DESCRIPTION, description);
        contentValues.put(Contract.Debt.DATE, DateUtils.getSQLDateString(date));
        contentValues.put(Contract.Debt.EXPIRATION_DATE, exp != null ? DateUtils.getSQLDateString(exp) : null);
        contentValues.put(Contract.Debt.WALLET_ID, walletId);
        contentValues.put(Contract.Debt.NOTE, note);
        contentValues.put(Contract.Debt.PLACE_ID, placeId);
        contentValues.put(Contract.Debt.MONEY, money);
        contentValues.put(Contract.Debt.ARCHIVED, archived);
        contentValues.put(Contract.Debt.PEOPLE_IDS, getObjectIds(peopleIds));
        contentValues.put(Contract.Debt.TAG, tag);
        return mDatabase.updateDebt(id, contentValues);
    }

    private void checkBudgetId(long id, int type, Long categoryId, Date startDate, Date endDate,
                               long money, String currency, Long[] walletIds, String tag, long progress) {
        Cursor cursor = mDatabase.getBudget(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(type, cursor.getInt(cursor.getColumnIndex(Contract.Budget.TYPE)));
        checkNullable(cursor, categoryId, cursor.getColumnIndex(Contract.Budget.CATEGORY_ID));
        assertEquals(DateUtils.getSQLDateString(startDate), cursor.getString(cursor.getColumnIndex(Contract.Budget.START_DATE)));
        assertEquals(DateUtils.getSQLDateString(endDate), cursor.getString(cursor.getColumnIndex(Contract.Budget.END_DATE)));
        assertEquals(money, cursor.getLong(cursor.getColumnIndex(Contract.Budget.MONEY)));
        assertEquals(currency, cursor.getString(cursor.getColumnIndex(Contract.Budget.CURRENCY)));
        assertEquals(getObjectIds(walletIds), cursor.getString(cursor.getColumnIndex(Contract.Budget.WALLET_IDS)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Budget.TAG)));
        assertEquals(progress, cursor.getLong(cursor.getColumnIndex(Contract.Budget.PROGRESS)));
        cursor.close();
    }

    private long insertBudget(int type, Long categoryId, Date startDate, Date endDate, long money,
                              String currency, Long[] walletIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Budget.TYPE, type);
        contentValues.put(Contract.Budget.CATEGORY_ID, categoryId);
        contentValues.put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate));
        contentValues.put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate));
        contentValues.put(Contract.Budget.MONEY, money);
        contentValues.put(Contract.Budget.CURRENCY, currency);
        System.out.println("[pre insert] " + Arrays.toString(walletIds));
        System.out.println("[post insert] " + getObjectIds(walletIds));
        contentValues.put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds));
        contentValues.put(Contract.Budget.TAG, tag);
        return mDatabase.insertBudget(contentValues);
    }

    private int updateBudget(long id, int type, Long categoryId, Date startDate, Date endDate,
                             long money, String currency, Long[] walletIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Budget.TYPE, type);
        contentValues.put(Contract.Budget.CATEGORY_ID, categoryId);
        contentValues.put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate));
        contentValues.put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate));
        contentValues.put(Contract.Budget.MONEY, money);
        contentValues.put(Contract.Budget.CURRENCY, currency);
        contentValues.put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds));
        contentValues.put(Contract.Budget.TAG, tag);
        return mDatabase.updateBudget(id, contentValues);
    }

    private long insertBudgetCovering(int type, Long[] categoryIds, Date startDate, Date endDate, long money,
                              String currency, Long[] walletIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Budget.TYPE, type);
        contentValues.put(Contract.Budget.CATEGORY_IDS, getObjectIds(categoryIds));
        contentValues.put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate));
        contentValues.put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate));
        contentValues.put(Contract.Budget.MONEY, money);
        contentValues.put(Contract.Budget.CURRENCY, currency);
        contentValues.put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds));
        contentValues.put(Contract.Budget.TAG, tag);
        return mDatabase.insertBudget(contentValues);
    }

    private int updateBudgetCovering(long id, int type, Long[] categoryIds, Date startDate, Date endDate,
                             long money, String currency, Long[] walletIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Budget.TYPE, type);
        contentValues.put(Contract.Budget.CATEGORY_IDS, getObjectIds(categoryIds));
        contentValues.put(Contract.Budget.START_DATE, DateUtils.getSQLDateString(startDate));
        contentValues.put(Contract.Budget.END_DATE, DateUtils.getSQLDateString(endDate));
        contentValues.put(Contract.Budget.MONEY, money);
        contentValues.put(Contract.Budget.CURRENCY, currency);
        contentValues.put(Contract.Budget.WALLET_IDS, getObjectIds(walletIds));
        contentValues.put(Contract.Budget.TAG, tag);
        return mDatabase.updateBudget(id, contentValues);
    }

    /**
     * Reads back what a budget covers, both the way the editor reads it and the way the list row
     * shows it.
     *
     * @param id of the budget.
     * @param categoryIds every category it should cover, lowest id first, which is the order
     *                    a query hands them over in.
     * @param joinedNames the names the list row should show, joined the way the query joins them.
     * @param progress the figure the budget should now be at.
     */
    private void checkBudgetCategories(long id, Long[] categoryIds, String joinedNames, long progress) {
        Cursor cursor = mDatabase.getBudget(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(getObjectIds(categoryIds), cursor.getString(cursor.getColumnIndex(Contract.Budget.CATEGORY_IDS)));
        assertEquals(joinedNames, cursor.getString(cursor.getColumnIndex(Contract.Budget.CATEGORY_NAME)));
        assertEquals((long) categoryIds[0], cursor.getLong(cursor.getColumnIndex(Contract.Budget.CATEGORY_ID)));
        assertEquals(progress, cursor.getLong(cursor.getColumnIndex(Contract.Budget.PROGRESS)));
        cursor.close();
    }

    private void checkSavingId(long id, String description, String icon, long startMoney,
                               long endMoney, long walletId, Date exp, boolean completed,
                               String note, String tag) {
        Cursor cursor = mDatabase.getSaving(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(description, cursor.getString(cursor.getColumnIndex(Contract.Saving.DESCRIPTION)));
        assertEquals(icon, cursor.getString(cursor.getColumnIndex(Contract.Saving.ICON)));
        assertEquals(startMoney, cursor.getLong(cursor.getColumnIndex(Contract.Saving.START_MONEY)));
        assertEquals(endMoney, cursor.getLong(cursor.getColumnIndex(Contract.Saving.END_MONEY)));
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndex(Contract.Saving.WALLET_ID)));
        checkNullable(cursor, exp != null ? DateUtils.getSQLDateString(exp) : null, cursor.getColumnIndex(Contract.Saving.END_DATE));
        assertEquals(completed, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Saving.COMPLETE)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Saving.NOTE)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Saving.TAG)));
        cursor.close();
    }

    private long insertSaving(String description, String icon, long startMoney, long endMoney,
                              long walletId, Date exp, boolean completed, String note, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Saving.DESCRIPTION, description);
        contentValues.put(Contract.Saving.ICON, icon);
        contentValues.put(Contract.Saving.START_MONEY, startMoney);
        contentValues.put(Contract.Saving.END_MONEY, endMoney);
        contentValues.put(Contract.Saving.WALLET_ID, walletId);
        contentValues.put(Contract.Saving.END_DATE, exp != null ? DateUtils.getSQLDateString(exp) : null);
        contentValues.put(Contract.Saving.COMPLETE, completed);
        contentValues.put(Contract.Saving.NOTE, note);
        contentValues.put(Contract.Saving.TAG, tag);
        return mDatabase.insertSaving(contentValues);
    }

    private int updateSaving(long id, String description, String icon, long startMoney,
                             long endMoney, long walletId, Date exp, boolean completed,
                             String note, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Saving.DESCRIPTION, description);
        contentValues.put(Contract.Saving.ICON, icon);
        contentValues.put(Contract.Saving.START_MONEY, startMoney);
        contentValues.put(Contract.Saving.END_MONEY, endMoney);
        contentValues.put(Contract.Saving.WALLET_ID, walletId);
        contentValues.put(Contract.Saving.END_DATE, exp != null ? DateUtils.getSQLDateString(exp) : null);
        contentValues.put(Contract.Saving.COMPLETE, completed);
        contentValues.put(Contract.Saving.NOTE, note);
        contentValues.put(Contract.Saving.TAG, tag);
        return mDatabase.updateSaving(id, contentValues);
    }

    private void checkTransactionId(long id, long money, Date datetime, String description, long categoryId,
                                    int direction, int type, long walletId, Long placeId, String note,
                                    Long eventId, Long savingId, Long debtId, boolean confirmed,
                                    boolean countInTotal, Long[] peopleIds, String tag) {
        Cursor cursor = mDatabase.getTransaction(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(money, cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY)));
        assertEquals(DateUtils.getSQLDateTimeString(datetime), cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE)));
        assertEquals(description, cursor.getString(cursor.getColumnIndex(Contract.Transaction.DESCRIPTION)));
        assertEquals(categoryId, cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ID)));
        assertEquals(direction, cursor.getInt(cursor.getColumnIndex(Contract.Transaction.DIRECTION)));
        assertEquals(type, cursor.getInt(cursor.getColumnIndex(Contract.Transaction.TYPE)));
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndex(Contract.Transaction.WALLET_ID)));
        checkNullable(cursor, placeId, cursor.getColumnIndex(Contract.Transaction.PLACE_ID));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Transaction.NOTE)));
        checkNullable(cursor, eventId, cursor.getColumnIndex(Contract.Transaction.EVENT_ID));
        checkNullable(cursor, savingId, cursor.getColumnIndex(Contract.Transaction.SAVING_ID));
        checkNullable(cursor, debtId, cursor.getColumnIndex(Contract.Transaction.DEBT_ID));
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CONFIRMED)));
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Transaction.COUNT_IN_TOTAL)));
        assertEquals(getObjectIds(peopleIds), cursor.getString(cursor.getColumnIndex(Contract.Transaction.PEOPLE_IDS)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Transaction.TAG)));
        cursor.close();
    }

    private long insertTransaction(long money, Date datetime, String description, long categoryId,
                                  int direction, int type, long walletId, Long placeId, String note,
                                  Long eventId, Long savingId, Long debtId, boolean confirmed,
                                  boolean countInTotal, Long[] peopleIds, Long[] attachmentIds,
                                  String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, money);
        contentValues.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(datetime));
        contentValues.put(Contract.Transaction.DESCRIPTION, description);
        contentValues.put(Contract.Transaction.CATEGORY_ID, categoryId);
        contentValues.put(Contract.Transaction.DIRECTION, direction);
        contentValues.put(Contract.Transaction.TYPE, type);
        contentValues.put(Contract.Transaction.WALLET_ID, walletId);
        contentValues.put(Contract.Transaction.PLACE_ID, placeId);
        contentValues.put(Contract.Transaction.NOTE, note);
        contentValues.put(Contract.Transaction.EVENT_ID, eventId);
        contentValues.put(Contract.Transaction.SAVING_ID, savingId);
        contentValues.put(Contract.Transaction.DEBT_ID, debtId);
        contentValues.put(Contract.Transaction.CONFIRMED, confirmed);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.Transaction.PEOPLE_IDS, getObjectIds(peopleIds));
        contentValues.put(Contract.Transaction.ATTACHMENT_IDS, getObjectIds(attachmentIds));
        contentValues.put(Contract.Transaction.TAG, tag);
        return mDatabase.insertTransaction(contentValues);
    }

    private int updateTransaction(long id, long money, Date datetime, String description, long categoryId,
                                  int direction, int type, long walletId, Long placeId, String note,
                                  Long eventId, Long savingId, Long debtId, boolean confirmed,
                                  boolean countInTotal, Long[] peopleIds, Long[] attachmentIds,
                                  String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, money);
        contentValues.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(datetime));
        contentValues.put(Contract.Transaction.DESCRIPTION, description);
        contentValues.put(Contract.Transaction.CATEGORY_ID, categoryId);
        contentValues.put(Contract.Transaction.DIRECTION, direction);
        contentValues.put(Contract.Transaction.TYPE, type);
        contentValues.put(Contract.Transaction.WALLET_ID, walletId);
        contentValues.put(Contract.Transaction.PLACE_ID, placeId);
        contentValues.put(Contract.Transaction.NOTE, note);
        contentValues.put(Contract.Transaction.EVENT_ID, eventId);
        contentValues.put(Contract.Transaction.SAVING_ID, savingId);
        contentValues.put(Contract.Transaction.DEBT_ID, debtId);
        contentValues.put(Contract.Transaction.CONFIRMED, confirmed);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.Transaction.PEOPLE_IDS, getObjectIds(peopleIds));
        contentValues.put(Contract.Transaction.ATTACHMENT_IDS, getObjectIds(attachmentIds));
        contentValues.put(Contract.Transaction.TAG, tag);
        return mDatabase.updateTransaction(id, contentValues);
    }

    private void checkTransferId(long id, String description, Date datetime, long walletFromId,
                                 long walletToId, Long walletTaxId, long moneyFrom,
                                 long moneyTo, long moneyTax, String note, Long placeId,
                                 Long eventId, boolean confirmed, boolean countInTotal,
                                 Long[] peopleIds, String tag) {
        Cursor cursor = mDatabase.getTransfer(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(description, cursor.getString(cursor.getColumnIndex(Contract.Transfer.DESCRIPTION)));
        assertEquals(DateUtils.getSQLDateTimeString(datetime), cursor.getString(cursor.getColumnIndex(Contract.Transfer.DATE)));
        assertEquals(walletFromId, cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_WALLET_ID)));
        assertEquals(walletToId, cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_WALLET_ID)));
        checkNullable(cursor, walletTaxId, cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_WALLET_ID));
        assertEquals(moneyFrom, cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_FROM_MONEY)));
        assertEquals(moneyTo, cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TO_MONEY)));
        assertEquals(moneyTax, cursor.getLong(cursor.getColumnIndex(Contract.Transfer.TRANSACTION_TAX_MONEY)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.Transfer.NOTE)));
        checkNullable(cursor, placeId, cursor.getColumnIndex(Contract.Transfer.PLACE_ID));
        checkNullable(cursor, eventId, cursor.getColumnIndex(Contract.Transfer.EVENT_ID));
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Transfer.CONFIRMED)));
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndex(Contract.Transfer.COUNT_IN_TOTAL)));
        assertEquals(getObjectIds(peopleIds), cursor.getString(cursor.getColumnIndex(Contract.Transfer.PEOPLE_IDS)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.Transfer.TAG)));
        cursor.close();
    }

    private long insertTransfer(String description, Date datetime, long walletFromId,
                                long walletToId, Long walletTaxId, long moneyFrom,
                                long moneyTo, long moneyTax, String note, Long placeId,
                                Long eventId, boolean confirmed, boolean countInTotal,
                                Long[] peopleIds, Long[] attachmentIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transfer.DESCRIPTION, description);
        contentValues.put(Contract.Transfer.DATE, DateUtils.getSQLDateTimeString(datetime));
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, walletFromId);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, walletToId);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, walletTaxId);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_MONEY, moneyFrom);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_MONEY, moneyTo);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_MONEY, moneyTax);
        contentValues.put(Contract.Transfer.NOTE, note);
        contentValues.put(Contract.Transfer.PLACE_ID, placeId);
        contentValues.put(Contract.Transfer.EVENT_ID, eventId);
        contentValues.put(Contract.Transfer.CONFIRMED, confirmed);
        contentValues.put(Contract.Transfer.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.Transfer.PEOPLE_IDS, getObjectIds(peopleIds));
        contentValues.put(Contract.Transfer.ATTACHMENT_IDS, getObjectIds(attachmentIds));
        contentValues.put(Contract.Transfer.TAG, tag);
        return mDatabase.insertTransfer(contentValues);
    }

    private int updateTransfer(long id, String description, Date datetime, long walletFromId,
                               long walletToId, Long walletTaxId, long moneyFrom,
                               long moneyTo, long moneyTax, String note, Long placeId,
                               Long eventId, boolean confirmed, boolean countInTotal,
                               Long[] peopleIds, Long[] attachmentIds, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transfer.DESCRIPTION, description);
        contentValues.put(Contract.Transfer.DATE, DateUtils.getSQLDateTimeString(datetime));
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, walletFromId);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, walletToId);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, walletTaxId);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_MONEY, moneyFrom);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_MONEY, moneyTo);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_MONEY, moneyTax);
        contentValues.put(Contract.Transfer.NOTE, note);
        contentValues.put(Contract.Transfer.PLACE_ID, placeId);
        contentValues.put(Contract.Transfer.EVENT_ID, eventId);
        contentValues.put(Contract.Transfer.CONFIRMED, confirmed);
        contentValues.put(Contract.Transfer.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.Transfer.PEOPLE_IDS, getObjectIds(peopleIds));
        contentValues.put(Contract.Transfer.ATTACHMENT_IDS, getObjectIds(attachmentIds));
        contentValues.put(Contract.Transfer.TAG, tag);
        return mDatabase.updateTransfer(id, contentValues);
    }

    private void checkTransactionModelId(long id, long money, String description, long categoryId,
                                         int direction, long walletId, Long placeId, String note,
                                         Long eventId, boolean confirmed, boolean countInTotal,
                                         String tag) {
        Cursor cursor = mDatabase.getTransactionModel(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(money, cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.MONEY)));
        assertEquals(description, cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.DESCRIPTION)));
        assertEquals(categoryId, cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ID)));
        assertEquals(direction, cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.DIRECTION)));
        assertEquals(walletId, cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ID)));
        checkNullable(cursor, placeId, cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.NOTE)));
        checkNullable(cursor, eventId, cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID));
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CONFIRMED)));
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.COUNT_IN_TOTAL)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.TAG)));
        cursor.close();
    }

    private long insertTransactionModel(long money, String description, long categoryId,
                                       int direction, long walletId, Long placeId, String note,
                                       Long eventId, boolean confirmed, boolean countInTotal,
                                       String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.TransactionModel.MONEY, money);
        contentValues.put(Contract.TransactionModel.DESCRIPTION, description);
        contentValues.put(Contract.TransactionModel.CATEGORY_ID, categoryId);
        contentValues.put(Contract.TransactionModel.DIRECTION, direction);
        contentValues.put(Contract.TransactionModel.WALLET_ID, walletId);
        contentValues.put(Contract.TransactionModel.PLACE_ID, placeId);
        contentValues.put(Contract.TransactionModel.EVENT_ID, eventId);
        contentValues.put(Contract.TransactionModel.NOTE, note);
        contentValues.put(Contract.TransactionModel.CONFIRMED, confirmed);
        contentValues.put(Contract.TransactionModel.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.TransactionModel.TAG, tag);
        return mDatabase.insertTransactionModel(contentValues);
    }

    private int updateTransactionModel(long id, long money, String description, long categoryId,
                                       int direction, long walletId, Long placeId, String note,
                                       Long eventId, boolean confirmed, boolean countInTotal,
                                       String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.TransactionModel.MONEY, money);
        contentValues.put(Contract.TransactionModel.DESCRIPTION, description);
        contentValues.put(Contract.TransactionModel.CATEGORY_ID, categoryId);
        contentValues.put(Contract.TransactionModel.DIRECTION, direction);
        contentValues.put(Contract.TransactionModel.WALLET_ID, walletId);
        contentValues.put(Contract.TransactionModel.PLACE_ID, placeId);
        contentValues.put(Contract.TransactionModel.EVENT_ID, eventId);
        contentValues.put(Contract.TransactionModel.NOTE, note);
        contentValues.put(Contract.TransactionModel.CONFIRMED, confirmed);
        contentValues.put(Contract.TransactionModel.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.TransactionModel.TAG, tag);
        return mDatabase.updateTransactionModel(id, contentValues);
    }

    private void checkTransferModelId(long id, String description, long walletFromId, long walletToId,
                                      long moneyFrom, long moneyTo, long moneyTax, String note,
                                      Long placeId, Long eventId, boolean confirmed,
                                      boolean countInTotal, String tag) {
        Cursor cursor = mDatabase.getTransferModel(id, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        assertEquals(true, cursor.moveToFirst());
        assertEquals(description, cursor.getString(cursor.getColumnIndex(Contract.TransferModel.DESCRIPTION)));
        assertEquals(walletFromId, cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_FROM_ID)));
        assertEquals(walletToId, cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.WALLET_TO_ID)));
        assertEquals(moneyFrom, cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_FROM)));
        assertEquals(moneyTo, cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_TO)));
        assertEquals(moneyTax, cursor.getLong(cursor.getColumnIndex(Contract.TransferModel.MONEY_TAX)));
        assertEquals(note, cursor.getString(cursor.getColumnIndex(Contract.TransferModel.NOTE)));
        checkNullable(cursor, placeId, cursor.getColumnIndex(Contract.TransferModel.PLACE_ID));
        checkNullable(cursor, eventId, cursor.getColumnIndex(Contract.TransferModel.EVENT_ID));
        assertEquals(confirmed, 1 == cursor.getInt(cursor.getColumnIndex(Contract.TransferModel.CONFIRMED)));
        assertEquals(countInTotal, 1 == cursor.getInt(cursor.getColumnIndex(Contract.TransferModel.COUNT_IN_TOTAL)));
        assertEquals(tag, cursor.getString(cursor.getColumnIndex(Contract.TransferModel.TAG)));
        cursor.close();
    }

    private long insertTransferModel(String description, long walletFromId, long walletToId,
                                     long moneyFrom, long moneyTo, long moneyTax, String note,
                                     Long placeId, Long eventId, boolean confirmed,
                                     boolean countInTotal, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.TransferModel.DESCRIPTION, description);
        contentValues.put(Contract.TransferModel.WALLET_FROM_ID, walletFromId);
        contentValues.put(Contract.TransferModel.WALLET_TO_ID, walletToId);
        contentValues.put(Contract.TransferModel.MONEY_FROM, moneyFrom);
        contentValues.put(Contract.TransferModel.MONEY_TO, moneyTo);
        contentValues.put(Contract.TransferModel.MONEY_TAX, moneyTax);
        contentValues.put(Contract.TransferModel.NOTE, note);
        contentValues.put(Contract.TransferModel.PLACE_ID, placeId);
        contentValues.put(Contract.TransferModel.EVENT_ID, eventId);
        contentValues.put(Contract.TransferModel.CONFIRMED, confirmed);
        contentValues.put(Contract.TransferModel.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.TransferModel.TAG, tag);
        return mDatabase.insertTransferModel(contentValues);
    }

    private int updateTransferModel(long id, String description, long walletFromId, long walletToId,
                                     long moneyFrom, long moneyTo, long moneyTax, String note,
                                     Long placeId, Long eventId, boolean confirmed,
                                     boolean countInTotal, String tag) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.TransferModel.DESCRIPTION, description);
        contentValues.put(Contract.TransferModel.WALLET_FROM_ID, walletFromId);
        contentValues.put(Contract.TransferModel.WALLET_TO_ID, walletToId);
        contentValues.put(Contract.TransferModel.WALLET_FROM_ID, walletFromId);
        contentValues.put(Contract.TransferModel.MONEY_FROM, moneyFrom);
        contentValues.put(Contract.TransferModel.MONEY_TO, moneyTo);
        contentValues.put(Contract.TransferModel.MONEY_TAX, moneyTax);
        contentValues.put(Contract.TransferModel.NOTE, note);
        contentValues.put(Contract.TransferModel.PLACE_ID, placeId);
        contentValues.put(Contract.TransferModel.EVENT_ID, eventId);
        contentValues.put(Contract.TransferModel.CONFIRMED, confirmed);
        contentValues.put(Contract.TransferModel.COUNT_IN_TOTAL, countInTotal);
        contentValues.put(Contract.TransferModel.TAG, tag);
        return mDatabase.updateTransferModel(id, contentValues);
    }

    /////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////// START THE TEST /////////////////////////////////////
    /////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Covers the wrapper's other three database methods, which nothing else in here reaches on
     * the runners this suite is run on. Without this they are three overrides whose loss nothing
     * would notice.
     */
    @Test
    public void everyDatabaseMethodOnTheWrapperIsPrefixed() {
        File expected = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getDatabasePath(TestStorageContext.PREFIX + SQLDatabase.DATABASE_NAME);
        // closed first, so the delete at the end is not unlinking a file still open here
        mDatabase.close();
        SQLiteDatabase threeArgument = mContext.openOrCreateDatabase(
                SQLDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null);
        try {
            assertEquals(expected.getPath(), threeArgument.getPath());
        } finally {
            threeArgument.close();
        }
        SQLiteDatabase fourArgument = mContext.openOrCreateDatabase(
                SQLDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null, null);
        try {
            assertEquals(expected.getPath(), fourArgument.getPath());
        } finally {
            fourArgument.close();
        }
        mContext.deleteDatabase(SQLDatabase.DATABASE_NAME);
        assertFalse(expected.exists());
    }

    /**
     * Proves the attachment deletes land in the wrapper's folder. The file is made under the
     * wrapper's own external directory, so nothing here writes into the app's attachments, and it
     * has to be gone afterwards.
     */
    @Test
    public void attachmentFileIsDeletedUnderTheTestFolder() throws Exception {
        // built from the unwrapped context, never from the wrapper, or an emptied EXTERNAL_SUBDIR
        // would move this file and the delete together and they would still agree
        File external = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getExternalFilesDir(null);
        // same reason as the prefix check in setUp, and before anything is written below
        assertFalse(external.equals(mContext.getExternalFilesDir(null)));
        File folder = new File(new File(external, TestStorageContext.EXTERNAL_SUBDIR),
                Attachment.FOLDER_NAME);
        assertTrue(folder.isDirectory() || folder.mkdirs());
        File sentinel = new File(folder, "path1");
        assertTrue(sentinel.isFile() || sentinel.createNewFile());
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 1", "encoded-icon-1", Contract.CategoryType.INCOME.getValue(), null, true, "tag-category-1");
        long id3 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id4 = insertTransaction(2000L, new Date(), "desc", id2, Contract.Direction.INCOME, 0, id1, null, "note", null, null, null, true, true, null, new Long[] {id3}, "tag");
        mDatabase.deleteTransaction(id4);
        assertFalse(sentinel.exists());
    }

    @Test
    public void insertWallet() throws Exception {
        // insert 4 wallets and then query for each one and check the returned id
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, false, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, false, "tag-wallet-4");
        // now query each wallet and check that everything is ok
        checkWalletId(id1, "Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, 0L, false, "tag-wallet-1");
        checkWalletId(id2, "Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, 0L, true, "tag-wallet-2");
        checkWalletId(id3, "Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, 0L, false, "tag-wallet-3");
        checkWalletId(id4, "Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, 0L, false, "tag-wallet-4");
    }

    @Test
    public void updateWallet() throws Exception {
        // insert 4 wallets and then query for each one and check the returned id
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, false, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, false, "tag-wallet-4");
        // now modify each wallet
        assertEquals(1, updateWallet(id1, "Test wallet 1-edited", "encoded-icon-1-edited", "USD", "note-wallet-1-edited", false, 4000L, true, "tag-wallet-1-edited"));
        assertEquals(1, updateWallet(id2, "Test wallet 2-edited", "encoded-icon-2-edited", "EUR", "note-wallet-2-edited", true, 3500L, true, "tag-wallet-2-edited"));
        assertEquals(1, updateWallet(id3, "Test wallet 3-edited", "encoded-icon-3-edited", "USD", "note-wallet-3-edited", false, 500L, false, "tag-wallet-3-edited"));
        assertEquals(1, updateWallet(id4, "Test wallet 4-edited", "encoded-icon-4-edited", "EUR", "note-wallet-4-edited", true, 7800L, false, "tag-wallet-4-edited"));
        // now check that the values are changed
        checkWalletId(id1, "Test wallet 1-edited", "encoded-icon-1-edited", "USD", "note-wallet-1-edited", false, 4000L, 0L, true, "tag-wallet-1-edited");
        checkWalletId(id2, "Test wallet 2-edited", "encoded-icon-2-edited", "EUR", "note-wallet-2-edited", true, 3500L, 0L, true, "tag-wallet-2-edited");
        checkWalletId(id3, "Test wallet 3-edited", "encoded-icon-3-edited", "USD", "note-wallet-3-edited", false, 500L, 0L, false, "tag-wallet-3-edited");
        checkWalletId(id4, "Test wallet 4-edited", "encoded-icon-4-edited", "EUR", "note-wallet-4-edited", true, 7800L, 0L, false, "tag-wallet-4-edited");
    }

    @Test
    public void deleteWallet() throws Exception {
        // insert 4 wallets and then query for each one and check the returned id
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, false, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", false, 7500L, false, "tag-wallet-4");
        // check that the returned cursor contains exactly 4 wallets
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 4);
        // now remove two wallets
        assertEquals(1, mDatabase.deleteWallet(id2));
        assertEquals(1, mDatabase.deleteWallet(id3));
        // recheck the wallet count
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 2);
    }

    @Test
    public void testEnsureDatabaseCleanAfterWalletDelete() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 5);
        Date endDate = calendar.getTime();
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 1, null, true, "category-tag-3");
        long id4 = insertTransaction(10, new Date(), "test", id3, Contract.Direction.EXPENSE, 0, id1, null, null, null, null, null, true, true, null, null, null);
        long id5 = insertTransactionModel(25, "desc", id3, Contract.Direction.INCOME, id1, null, null, null, true, true, null);
        long id6 = insertTransferModel("desc", id1, id2, 30L, 30L, 0L, "note", null, null, true, true, null);
        long id7 = insertSaving("desc", "encoded-icon", 0L, 100L, id1, null, false, null, null);
        long id8 = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon", "desc", new Date(), null, id1, null, null, 20, false, null, null, false);
        long id9 = insertBudget(Schema.BudgetType.CATEGORY, id3, startDate, endDate, 3000L, "EUR", new Long[] {id1, id2}, null);
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id3, startDate, endDate, 3000L, "EUR", new Long[] {id1, id2}, null, -10L);
        // now delete the wallet 1
        mDatabase.deleteWallet(id1);
        // check if everything has been deleted
        checkCursorSize(mDatabase.getTransaction(id4, null), 0);
        checkCursorSize(mDatabase.getTransactionModel(id5, null), 0);
        checkCursorSize(mDatabase.getTransferModel(id6, null), 0);
        checkCursorSize(mDatabase.getSaving(id7, null), 0);
        checkCursorSize(mDatabase.getDebt(id8, null), 0);
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id3, startDate, endDate, 3000L, "EUR", new Long[] {id2}, null, 0L);
    }

    /**
     * Puts a file where SQLDatabase.getAttachmentFolder will look for it, by the same route that
     * method takes. That is deliberate and it is not the identity a sentinel built from the
     * wrapper would be, because the pair of cases below differ only in whether the transaction
     * commits. A wrong folder makes the committed case fail, so the path is under test too.
     */
    private File writeAttachmentFile(String name) throws Exception {
        File folder = new File(mContext.getExternalFilesDir(null), Attachment.FOLDER_NAME);
        assertTrue("cannot create " + folder, folder.exists() || folder.mkdirs());
        File file = new File(folder, name);
        assertTrue("cannot create " + file, file.exists() || file.createNewFile());
        return file;
    }

    private long walletWithAnAttachedTransaction(String fileName) {
        long walletId = insertWallet("Attached", "icon", "EUR", "note", true, 0L, false, "tag");
        long categoryId = insertCategory("category", "icon", 0, null, true, "tag");
        long attachmentId = insertAttachment(fileName, "name", "mime-type", 90L, "tag");
        insertTransaction(10, new Date(), "desc", categoryId, Contract.Direction.EXPENSE, 0,
                walletId, null, null, null, null, null, true, true, null,
                new Long[] {attachmentId}, null);
        return walletId;
    }

    /**
     * The four cases below are the two sided proof of the transaction wrapper, and each pair needs
     * both halves. Without the committing case, a wrapper that rolled everything back and
     * committed nothing would satisfy the rollback case, and no other case in this class would
     * see it, because they all call SQLDatabase directly and never open a transaction at all.
     */
    @Test
    public void inTransactionCommitsWhatTheBodyWrote() {
        long walletId = mDatabase.inTransaction(() ->
                insertWallet("Committed", "icon", "EUR", "note", true, 0L, false, "tag"));
        assertTrue(walletId > 0L);
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 1);
    }

    @Test
    public void inTransactionRollsBackEveryWriteWhenTheBodyThrows() {
        insertWallet("Written before the transaction", "icon", "EUR", "note", true, 0L, false, "tag");
        try {
            mDatabase.<Long>inTransaction(() -> {
                insertWallet("Rolled back", "icon", "EUR", "note", true, 0L, false, "tag");
                insertPerson("Rolled back too", "icon", "note", "tag");
                throw new IllegalStateException("forced from the body");
            });
            fail("the body's exception did not leave inTransaction");
        } catch (IllegalStateException expected) {
            // it has to reach the caller, since that is how a provider learns its write is undone
        }
        // both writes inside the transaction are gone, and the one before it is untouched, so
        // this fails either way round: on a wrapper that does not roll back, and on one that
        // rolls back more than the transaction
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 1);
        checkCursorSize(mDatabase.getPeople(null, null, null, null), 0);
    }

    /**
     * The shared helper, its swap, and the lock between them had no coverage at all until the
     * wrapper started answering getApplicationContext for itself. Removing the lock left every
     * other case in this class green, which made it a guard nothing could see.
     */
    @Test
    public void theSharedHelperResolvesThroughTheTestWrapper() {
        // the assertion that everything below rests on. If the wrapper were bypassed here the
        // shared helper would be the real ledger, and these cases would be writing into it
        assertEquals(mContext.getDatabasePath(SQLDatabase.DATABASE_NAME).getPath(),
                sharedPath());
    }

    @Test
    public void resetSharedReplacesTheInstanceAndGetSharedKeepsIt() {
        SQLDatabase first = shared();
        assertTrue("getShared handed back two helpers for one process",
                first == shared());
        SQLDatabase.resetShared(mContext);
        assertFalse("resetShared left the old helper installed",
                first == shared());
    }

    @Test
    public void inSharedTransactionRunsOnTheSharedHelperAndRollsBack() {
        SQLDatabase shared = shared();
        try {
            SQLDatabase.<Long>inSharedTransaction(mContext, database -> {
                assertTrue("the body was handed a different helper than the transaction was "
                        + "opened on", database == shared);
                ContentValues cv = new ContentValues();
                cv.put(Schema.Wallet.NAME, "rolled back");
                cv.put(Schema.Wallet.ICON, "icon");
                cv.put(Schema.Wallet.CURRENCY, "EUR");
                cv.put(Schema.Wallet.START_MONEY, 0L);
                cv.put(Schema.Wallet.COUNT_IN_TOTAL, true);
                cv.put(Schema.Wallet.ARCHIVED, false);
                cv.put(Schema.Wallet.INDEX, 0);
                cv.put(Schema.Wallet.UUID, java.util.UUID.randomUUID().toString());
                cv.put(Schema.Wallet.LAST_EDIT, System.currentTimeMillis());
                cv.put(Schema.Wallet.DELETED, false);
                database.getWritableDatabase().insert(Schema.Wallet.TABLE, null, cv);
                throw new IllegalStateException("forced from the body");
            });
            fail("the body's exception did not leave inSharedTransaction");
        } catch (IllegalStateException expected) {
            // as elsewhere
        }
        Cursor cursor = shared.getWritableDatabase().query(Schema.Wallet.TABLE, null, null, null,
                null, null, null);
        assertNotNull(cursor);
        try {
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    /**
     * The only case that fails if the swap lock is deleted, downgraded or inverted. Everything
     * else in this class is single threaded and stays green without it.
     */
    @Test(timeout = 20000)
    public void resetSharedWaitsForAWriteAlreadyInFlight() throws Exception {
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch resetReturned = new CountDownLatch(1);
        Thread writer = new Thread(() -> SQLDatabase.inSharedTransaction(mContext, database -> {
            inside.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return 1L;
        }));
        writer.start();
        assertTrue("the write never entered the transaction", inside.await(10, TimeUnit.SECONDS));

        Thread resetter = new Thread(() -> {
            SQLDatabase.resetShared(mContext);
            resetReturned.countDown();
        });
        resetter.start();
        try {
            // it has to still be waiting. Closing the helper here is what sends the rest of an
            // unfinished write to a second connection with no transaction on it
            assertFalse("resetShared swapped the helper while a write was still in flight",
                    resetReturned.await(1, TimeUnit.SECONDS));
        } finally {
            // in a finally for the reason given on the case below
            release.countDown();
        }
        writer.join(10000);
        assertTrue("resetShared never completed once the write finished",
                resetReturned.await(10, TimeUnit.SECONDS));
        resetter.join(10000);
    }

    /**
     * The same exclusion for the overload that carries the work. The case above drives the plain
     * overload, so a version that took the write lock only there would keep it green while every
     * rename in AbstractBackupImporter ran with a write still open on the file being moved.
     */
    @Test(timeout = 20000)
    public void resetSharedWaitsForAWriteAlreadyInFlightBeforeRunningTheWork() throws Exception {
        final CountDownLatch inside = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch workRan = new CountDownLatch(1);
        Thread writer = new Thread(() -> SQLDatabase.inSharedTransaction(mContext, database -> {
            inside.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return 1L;
        }));
        writer.start();
        assertTrue("the write never entered the transaction", inside.await(10, TimeUnit.SECONDS));

        Thread resetter = new Thread(() -> SQLDatabase.resetShared(mContext, workRan::countDown));
        resetter.start();
        try {
            assertFalse("the work ran while a write was still in flight, which is the rename "
                            + "moving the database file out from under an open transaction",
                    workRan.await(1, TimeUnit.SECONDS));
        } finally {
            // in a finally, or a failed assertion leaves the writer parked forever inside an open
            // transaction holding the read lock, and the next resetShared waits on it for good.
            // The suite then hangs instead of reporting, which reads as a broken run and not as a
            // broken guard
            release.countDown();
        }
        writer.join(10000);
        assertTrue("the work never ran once the write finished",
                workRan.await(10, TimeUnit.SECONDS));
        resetter.join(10000);
    }

    /**
     * The slot a restore renames the database file in. If the work ran anywhere else the file
     * would move with a connection open on it, which strands that connection's journal at the
     * old path and refuses its next write.
     */
    @Test
    public void resetSharedRunsTheWorkWithTheFileClosedAndReplacesTheHelperAfterIt() {
        SQLDatabase first = shared();
        SQLiteDatabase open = first.getWritableDatabase();
        assertTrue("the helper was not open before the swap", open.isOpen());
        final boolean[] ran = new boolean[1];
        SQLDatabase.resetShared(mContext, () -> {
            ran[0] = true;
            assertFalse("the work ran with the database still open", open.isOpen());
            // and before the replacement, which is what leaves the path free for the rename
            assertTrue("the helper was replaced before the work ran",
                    shared() == first);
        });
        assertTrue("the work never ran", ran[0]);
        assertFalse("resetShared left the old helper installed",
                shared() == first);
    }

    @Test
    public void resetSharedInstallsAWorkingHelperEvenWhenTheWorkThrows() {
        SQLDatabase first = shared();
        try {
            SQLDatabase.resetShared(mContext, () -> {
                throw new IllegalStateException("forced from the work");
            });
            fail("the work's exception did not leave resetShared");
        } catch (IllegalStateException expected) {
            // it has to leave, since the caller is the one that knows what a failed file swap
            // means. What must not happen is the helper being left closed behind it
        }
        SQLDatabase replacement = shared();
        assertFalse("the closed helper was left installed after the work threw",
                replacement == first);
        assertTrue("the helper installed after the work threw cannot open the database",
                replacement.getWritableDatabase().isOpen());
    }

    private ContentValues wallet(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, "icon");
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.NOTE, "note");
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.ARCHIVED, false);
        values.put(Contract.Wallet.TAG, "tag");
        return values;
    }

    /**
     * A wallet row as a restore writes one. SyncContentProvider inserts raw with no defaults of
     * its own, so the columns SQLDatabase.insertWallet would have generated have to be here.
     */
    private ContentValues syncWallet(String name) {
        ContentValues values = new ContentValues();
        values.put(Schema.Wallet.NAME, name);
        values.put(Schema.Wallet.ICON, "icon");
        values.put(Schema.Wallet.CURRENCY, "EUR");
        values.put(Schema.Wallet.START_MONEY, 0L);
        values.put(Schema.Wallet.COUNT_IN_TOTAL, true);
        values.put(Schema.Wallet.ARCHIVED, false);
        values.put(Schema.Wallet.INDEX, 0);
        values.put(Schema.Wallet.NOTE, "note");
        values.put(Schema.Wallet.TAG, "tag");
        values.put(Schema.Wallet.UUID, java.util.UUID.randomUUID().toString());
        values.put(Schema.Wallet.LAST_EDIT, System.currentTimeMillis());
        values.put(Schema.Wallet.DELETED, false);
        return values;
    }

    /** Counts through whichever file this thread is currently resolving. */
    private long sharedWalletsNamed(String name) {
        Cursor cursor = SQLDatabase.getShared(mContext).getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + Schema.Wallet.TABLE
                        + " WHERE " + Schema.Wallet.NAME + " = ?", new String[]{name});
        try {
            cursor.moveToFirst();
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    /** The helper this thread resolves, which is the staged one while a staged import is open. */
    private SQLDatabase shared() {
        return SQLDatabase.getShared(mContext);
    }

    /** The file this thread is open on. */
    private String sharedPath() {
        return SQLDatabase.getShared(mContext).getWritableDatabase().getPath();
    }

    /**
     * The claim the whole redesign rests on, and the reason the redirect is per thread. The
     * importing thread has to write into the staged file, and every other thread has to go on
     * writing into the live ledger, because that is what keeps the live file name still and lets
     * every read in the app run with no lock at all.
     *
     * The first assertion is the one that matters. A staged name resolving to the live file
     * would make everything below pass without testing anything.
     */
    @Test
    public void aStagedImportRedirectsItsOwnThreadAndNoOther() throws InterruptedException {
        final String onImporter = "Written on the importing thread";
        final String onOther = "Written on another thread";
        File live = mContext.getDatabasePath(SQLDatabase.DATABASE_NAME);
        File staged = mContext.getDatabasePath(SQLDatabaseImporter.STAGING_DATABASE_NAME);
        assertFalse("the staged name resolves to the live file, so nothing below can fail",
                live.getPath().equals(staged.getPath()));
        assertEquals(live.getPath(), sharedPath());

        final String[] otherPath = new String[1];
        final Throwable[] otherFailure = new Throwable[1];
        SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
        try {
            assertEquals("the importing thread did not follow the staged name",
                    staged.getPath(), sharedPath());
            SQLDatabase.inSharedTransaction(mContext,
                    database -> database.insertWallet(wallet(onImporter)));

            Thread other = new Thread(() -> {
                try {
                    otherPath[0] = sharedPath();
                    SQLDatabase.inSharedTransaction(mContext,
                            database -> database.insertWallet(wallet(onOther)));
                } catch (Throwable t) {
                    otherFailure[0] = t;
                }
            });
            other.start();
            other.join();
        } finally {
            SQLDatabase.closeStaging();
        }
        assertNull("the other thread failed: " + otherFailure[0], otherFailure[0]);
        assertEquals("another thread was redirected to the staged file too",
                live.getPath(), otherPath[0]);
        assertEquals("the importing thread did not come back to the live database",
                live.getPath(), sharedPath());

        assertEquals("the other thread row is not in the live ledger",
                1, sharedWalletsNamed(onOther));
        assertEquals("the staged row reached the live ledger",
                0, sharedWalletsNamed(onImporter));

        // and the staged row really is in the staged file. Without this the assertion above
        // passes just as well when the insert wrote nowhere at all
        SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
        try {
            assertEquals("the staged row never reached the staged database",
                    1, sharedWalletsNamed(onImporter));
            assertEquals("the other thread row reached the staged database",
                    0, sharedWalletsNamed(onOther));
        } finally {
            SQLDatabase.closeStaging();
        }
    }

    /**
     * The path a restore actually writes on. Every row an import puts in goes ContentResolver to
     * SyncContentProvider, which resolves the helper on each call and takes no transaction, so a
     * redirect that only reached inSharedTransaction would leave every restored row in the live
     * ledger. inSharedTransaction is DataContentProvider entry point and no restore uses it.
     *
     * The provider is used against the live ledger first on purpose, and that is what gives this
     * case its teeth. SyncContentProvider.db resolves the helper on every use instead of holding
     * it in a field, and its own javadoc says so; hold it in a field instead and the read below
     * fills that field with the live helper, so the staged insert goes into the ledger and this
     * case fails. Without the read first, a field would be filled by the staged insert itself and
     * the same broken provider would pass.
     */
    @Test
    public void aRowWrittenThroughTheSyncProviderFollowsTheStagedRedirect() {
        final String viaProvider = "Written through the sync provider";
        ContentResolver resolver = mContext.getContentResolver();

        Cursor warmUp = resolver.query(SyncContentProvider.CONTENT_WALLETS, null, null, null, null);
        assertNotNull("the sync provider answered nothing on the live ledger", warmUp);
        warmUp.close();

        SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
        try {
            resolver.insert(SyncContentProvider.CONTENT_WALLETS, syncWallet(viaProvider));
            assertEquals("the row did not reach the staged database",
                    1, sharedWalletsNamed(viaProvider));
        } finally {
            SQLDatabase.closeStaging();
        }
        assertEquals("the row written through the sync provider landed in the live ledger",
                0, sharedWalletsNamed(viaProvider));
    }

    /**
     * closeStaging is called from a finally on a path where the open may never have happened, so
     * calling it with nothing staged, and calling it twice, both have to be no ops. If either
     * moved this thread off the live database the whole suite after it would read the wrong file.
     *
     * The second close here runs against a helper with a connection really open on it, since
     * openStaging opens the staged file instead of leaving it to the first row.
     */
    @Test
    public void closingAStagedImportIsSafeWithoutOneAndTwiceOver() {
        File live = mContext.getDatabasePath(SQLDatabase.DATABASE_NAME);
        SQLDatabase.closeStaging();
        assertEquals("closing with nothing staged moved this thread", live.getPath(), sharedPath());

        SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
        SQLDatabase.closeStaging();
        SQLDatabase.closeStaging();
        assertEquals("a second close moved this thread off the live database",
                live.getPath(), sharedPath());
    }

    /**
     * An import that writes no rows still has to leave a file behind for the promote to rename.
     * SQLiteOpenHelper creates the file on first use, so openStaging opens the staged database
     * itself; without that, a backup whose arrays are all empty writes nothing, the rename finds
     * no source and the restore reports a failure for a backup with nothing wrong with it. A
     * legacy backup of an empty install is the reachable one, since that importer reads no
     * currencies and so has no table that is always written.
     */
    @Test
    public void openingAStagedImportCreatesTheFileBeforeAnyRowIsWritten() {
        File staged = mContext.getDatabasePath(SQLDatabaseImporter.STAGING_DATABASE_NAME);
        staged.delete();
        assertFalse("the precondition failed, a staged file was already on disk", staged.exists());

        SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
        try {
            assertTrue("openStaging left no file for the promote to rename", staged.exists());
        } finally {
            SQLDatabase.closeStaging();
        }
        assertTrue("the staged file went away when the helper closed", staged.exists());
    }

    /**
     * Two staged imports on one thread would leak the first helper, leaving a connection open on
     * a file the promote is about to rename, so the second is refused instead. Nothing does this
     * today and the refusal is what keeps it that way.
     */
    @Test
    public void aSecondStagedImportOnOneThreadIsRefused() {
        SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
        try {
            SQLDatabase.openStaging(mContext, SQLDatabaseImporter.STAGING_DATABASE_NAME);
            fail("a second staged import was allowed on one thread");
        } catch (IllegalStateException expected) {
            assertTrue("refused for the wrong reason: " + expected.getMessage(),
                    expected.getMessage().contains("already open"));
        } finally {
            SQLDatabase.closeStaging();
        }
    }

    /**
     * What promoteStagedDatabase rests on. It renames the staged file straight over the live one
     * and deletes nothing ahead of it, so that a rename which fails leaves the user's ledger
     * where it was. That is only safe if a rename replaces an existing target instead of
     * refusing it, which is a platform promise and not one this code can make.
     */
    @Test
    public void aRenameOverAnExistingFileReplacesItInOneStep() throws IOException {
        File live = new File(mContext.getCacheDir(), "rename-live.db");
        File staged = new File(mContext.getCacheDir(), "rename-staged.db");
        try {
            FileUtils.write(live, "the ledger", "UTF-8");
            FileUtils.write(staged, "the import", "UTF-8");
            assertTrue("the rename refused an existing target, so a promote would have to delete "
                            + "the live database first and a failure there would destroy both",
                    staged.renameTo(live));
            assertEquals("the import", FileUtils.readFileToString(live, "UTF-8"));
            assertFalse("the staged file outlived its own rename", staged.exists());
        } finally {
            FileUtils.deleteQuietly(live);
            FileUtils.deleteQuietly(staged);
        }
    }


    @Test
    public void aNestedInTransactionRollsBackWithTheOuterOne() {
        // outside the transaction, so the count below fails both ways round: on a nested call that
        // committed on its own, and on one that rolled back past the wallet written before
        insertWallet("Written before the transaction", "icon", "EUR", "note", true, 0L, false, "tag");
        try {
            mDatabase.<Long>inTransaction(() -> {
                insertWallet("Written before the nested call", "icon", "EUR", "note", true, 0L, false, "tag");
                long nested = mDatabase.inTransaction(() ->
                        insertWallet("Nested", "icon", "EUR", "note", true, 0L, false, "tag"));
                // the nested call has to have written something for the count below to be
                // testing a rollback. A build that refused to nest never reaches this line, and
                // one that ran the body and threw the row away would fail here
                assertTrue("the nested call inserted nothing", nested > 0L);
                // its own type. IllegalStateException is what a build that refuses to nest throws
                // from the line above, and a catch on that type below cannot tell the two apart
                throw new ImportStopped();
            });
            fail("the body's exception did not leave inTransaction");
        } catch (ImportStopped expected) {
            // this is the failure the import path has to survive, a row is refused after earlier
            // rows have gone in through the provider, each of those opening a transaction of its
            // own. Only the wallet written before the outer transaction may be left
        }
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 1);
    }

    /** How the case below ends its import, told apart from anything the app itself raises. */
    private static class ImportStopped extends RuntimeException {
    }

    /** The values an import builds for a wallet it has to create before it can use one. */
    private ContentValues walletValues(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, "icon");
        contentValues.put(Contract.Wallet.CURRENCY, "EUR");
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.ARCHIVED, false);
        return contentValues;
    }

    /**
     * A transaction naming a wallet and a category that are not there. Every column the table
     * insists on is filled, so the only thing wrong with this row is the two ids, and the insert
     * fails on them and not on something left out.
     */
    private ContentValues transactionValuesOnMissingRows() {
        return transactionValues(987654321L, 987654321L);
    }

    /**
     * The whole of what this change buys an import, driven the way an import drives it: rows in
     * through the provider, one refused part way, and nothing left behind.
     */
    @Test
    public void aRowTheProviderRefusesTakesEveryRowBeforeItBackOut() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                Uri saved = contentResolver.insert(DataContentProvider.CONTENT_WALLETS,
                        walletValues("Saved before the refused row"));
                assertNotNull("the provider refused a wallet that is fine", saved);
                // foreign keys are on, so this one cannot go in, and the provider says so by
                // answering nothing. An importer that ignored that answer is what used to let a
                // refused row disappear while the import reported that it had worked
                Uri refused = contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS,
                        transactionValuesOnMissingRows());
                assertNull("the provider took a transaction on a wallet that does not exist, so "
                        + "this case is no longer testing a refusal", refused);
                // its own type, and not one the code under test also raises. Catching
                // IllegalStateException here would swallow the one a provider write throws when
                // it refuses to run inside a transaction already open, and this case would go
                // green on a build where no row ever reached the database
                throw new ImportStopped();
            });
            fail("the failure did not leave runInOneTransaction");
        } catch (ImportStopped expected) {
            // the importers all end the import here, and the transaction has to go with it
        }
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 0);
    }

    @Test
    public void everyRowOfAnImportThatFinishesIsThere() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        DataContentProvider.runInOneTransaction(mContext, () -> {
            contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("First"));
            contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Second"));
            return null;
        });
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 2);
    }

    /**
     * Watches the wallet list the way an open list screen does, descendants included, so it hears
     * about a write whichever uri that write announces.
     */
    private CountDownLatch watchWallets(ContentResolver contentResolver, ContentObserver[] out) {
        return watch(contentResolver, DataContentProvider.CONTENT_WALLETS, true, out);
    }

    private CountDownLatch watch(ContentResolver contentResolver, Uri uri, boolean descendants,
                                 ContentObserver[] out) {
        CountDownLatch told = new CountDownLatch(1);
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {

            @Override
            public void onChange(boolean selfChange) {
                told.countDown();
            }
        };
        contentResolver.registerContentObserver(uri, descendants, observer);
        out[0] = observer;
        return told;
    }

    /**
     * What an import announces, and what that reaches. Both watchers here take descendants off, so
     * each one hears about exactly one uri: the list watcher only if the list itself was
     * announced, and the row watcher only if announcing the list reaches the rows under it.
     *
     * The second half is the reason an import is allowed to remember one uri per list instead of
     * one per row, and it is a claim about the framework, so it is checked here and not argued.
     */
    @Test
    public void anImportAnnouncesTheListAndReachesTheRowsUnderIt() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        long existing = insertWallet("Open on a detail screen", "icon", "EUR", "note", true, 0L, false, "tag");
        ContentObserver[] listObserver = new ContentObserver[1];
        ContentObserver[] rowObserver = new ContentObserver[1];
        CountDownLatch listTold = watch(contentResolver, DataContentProvider.CONTENT_WALLETS, false, listObserver);
        CountDownLatch rowTold = watch(contentResolver,
                ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, existing), false, rowObserver);
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> contentResolver.insert(
                    DataContentProvider.CONTENT_WALLETS, walletValues("Imported")));
            assertTrue("the import announced the row it wrote instead of the list it wrote into",
                    listTold.await(5, TimeUnit.SECONDS));
            assertTrue("announcing the list did not reach a screen watching a row under it, so "
                            + "remembering the list instead of the row loses a watcher",
                    rowTold.await(5, TimeUnit.SECONDS));
        } finally {
            contentResolver.unregisterContentObserver(listObserver[0]);
            contentResolver.unregisterContentObserver(rowObserver[0]);
        }
    }

    @Test
    public void anImportThatFinishesTellsTheOpenScreens() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        ContentObserver[] observer = new ContentObserver[1];
        CountDownLatch told = watchWallets(contentResolver, observer);
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                assertNotNull("the provider refused a wallet that is fine, so this case is no "
                                + "longer testing an import that wrote anything",
                        contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Imported")));
                return null;
            });
            // that an import which finished says something at all. It does not pin WHEN, since a
            // latch that has already counted down reads the same either way, and an import that
            // announced every row as it went would satisfy this too. What pins the holding back
            // is anImportThatFailedAnnouncesNothing, where an announcement must never arrive
            assertTrue("nothing was announced after the import committed",
                    told.await(5, TimeUnit.SECONDS));
        } finally {
            contentResolver.unregisterContentObserver(observer[0]);
        }
    }

    @Test
    public void anImportThatFailedAnnouncesNothing() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        ContentObserver[] observer = new ContentObserver[1];
        CountDownLatch told = watchWallets(contentResolver, observer);
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                assertNotNull("the provider refused a wallet that is fine, so there is no row for "
                                + "this case to roll back",
                        contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Rolled back")));
                throw new ImportStopped();
            });
            fail("the failure did not leave runInOneTransaction");
        } catch (ImportStopped expected) {
            // the wallet above went in and came back out, and announcing it would send every open
            // screen to look for a row that is not there. Checked here and not in the finally,
            // where a failure of its own would replace the one the case was really reporting and
            // leave the observer registered on the process wide resolver for the rest of the run
            assertFalse("a row that was rolled back was announced anyway",
                    told.await(2, TimeUnit.SECONDS));
        } finally {
            contentResolver.unregisterContentObserver(observer[0]);
        }
    }

    @Test
    public void anImportInsideAnImportIsOneImport() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        ContentObserver[] observer = new ContentObserver[1];
        CountDownLatch told = watchWallets(contentResolver, observer);
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                // the inner has to run the body. Every assertion this case makes afterwards is a
                // negative one, so an inner branch that returned without calling the body at all
                // would satisfy every one of them
                assertNotNull("the nested call wrote nothing",
                        DataContentProvider.runInOneTransaction(mContext, () -> contentResolver.insert(
                                DataContentProvider.CONTENT_WALLETS, walletValues("Inner"))));
                // after the inner call returns, and this is the write that matters. An inner call
                // that cleared what the outer had left on the thread instead of leaving it alone
                // would send this one straight to the observers, mid transaction
                assertNotNull("the provider refused a wallet that is fine",
                        contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("After the inner")));
                throw new ImportStopped();
            });
            fail("the failure did not leave runInOneTransaction");
        } catch (ImportStopped expected) {
            // the inner call must not commit or announce anything of its own. Nothing nests
            // today, and this is what keeps the first caller that does from announcing rows the
            // outer transaction then rolls back
            assertFalse("the inner import announced rows the outer one rolled back",
                    told.await(2, TimeUnit.SECONDS));
        } finally {
            contentResolver.unregisterContentObserver(observer[0]);
        }
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 0);
    }

    /**
     * The success path of the same nesting, which is where an inner call that kept a set of its
     * own would show. Its row would go in and the outer commit would announce only what the outer
     * itself wrote, so an open screen would never hear about the rows the inner call made.
     */
    @Test
    public void anImportInsideAnImportAnnouncesTheInnerRowsToo() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        ContentObserver[] observer = new ContentObserver[1];
        CountDownLatch told = watch(contentResolver, DataContentProvider.CONTENT_WALLETS, false, observer);
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                assertNotNull("the nested call wrote nothing",
                        DataContentProvider.runInOneTransaction(mContext, () -> contentResolver.insert(
                                DataContentProvider.CONTENT_WALLETS, walletValues("Inner"))));
                return null;
            });
            assertTrue("the row the nested call wrote was never announced",
                    told.await(5, TimeUnit.SECONDS));
        } finally {
            contentResolver.unregisterContentObserver(observer[0]);
        }
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 1);
    }

    /**
     * The sixteen top level lists, which is every one of them and not a sample of them. The other
     * thirty branches of the switch are fourteen lists that hang off a row and sixteen single row
     * uris, and two of those are the case after this one.
     */
    private static final Uri[] LIST_URIS = new Uri[] {
            DataContentProvider.CONTENT_CURRENCIES,
            DataContentProvider.CONTENT_WALLETS,
            DataContentProvider.CONTENT_TRANSACTIONS,
            DataContentProvider.CONTENT_TRANSFERS,
            DataContentProvider.CONTENT_CATEGORIES,
            DataContentProvider.CONTENT_DEBTS,
            DataContentProvider.CONTENT_BUDGETS,
            DataContentProvider.CONTENT_SAVINGS,
            DataContentProvider.CONTENT_EVENTS,
            DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS,
            DataContentProvider.CONTENT_RECURRENT_TRANSFERS,
            DataContentProvider.CONTENT_TRANSACTION_MODELS,
            DataContentProvider.CONTENT_TRANSFER_MODELS,
            DataContentProvider.CONTENT_PLACES,
            DataContentProvider.CONTENT_PEOPLE,
            DataContentProvider.CONTENT_ATTACHMENTS
    };

    /**
     * Every column the table insists on, so a row built from two ids that are really there cannot
     * be refused at all.
     */
    private ContentValues transactionValues(long walletId, long categoryId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.WALLET_ID, walletId);
        contentValues.put(Contract.Transaction.CATEGORY_ID, categoryId);
        contentValues.put(Contract.Transaction.DATE, DateUtils.getSQLDateTimeString(new Date()));
        contentValues.put(Contract.Transaction.MONEY, 1000L);
        contentValues.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        contentValues.put(Contract.Transaction.CONFIRMED, true);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return contentValues;
    }

    private ContentValues categoryValues(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, name);
        contentValues.put(Contract.Category.ICON, "encoded-icon");
        contentValues.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        contentValues.put(Contract.Category.SHOW_REPORT, true);
        return contentValues;
    }

    /**
     * Every query of the currency table filters DELETED = 0, so a row carrying a 1 is one the app
     * does not serve while its iso still holds the primary key. A backup restored through
     * SyncContentProvider writes that column straight through, so an installation can hold every
     * currency and offer none, and the default set written to repair that collided with those rows
     * and landed nothing.
     */
    @Test
    public void insertCurrencyBringsBackARowThatIsThereAndNotServed() {
        assertEquals("TST", mDatabase.insertCurrency(currencyValues("TST")));
        markDeleted("TST");
        assertEquals("marking the row deleted did not stop it being served, so what follows is "
                + "not being asked anything", 0, servedCount("TST"));

        assertEquals("TST", mDatabase.insertCurrency(seedValues("TST")));

        assertEquals("the row is still not served, so the default set cannot be written back over "
                + "a table that holds it and offers nothing", 1, servedCount("TST"));
    }

    /**
     * The editor creates a currency through the same method and must not be given the revive. A
     * row that came from a backup as deleted holds that backup's name, symbol and decimals, so
     * answering the editor with it would discard everything the user typed and report success.
     */
    @Test
    public void insertCurrencyDoesNotBringBackARowForACallerThatDidNotAsk() {
        assertEquals("TST", mDatabase.insertCurrency(currencyValues("TST")));
        markDeleted("TST");

        assertNull("an insert that did not ask for the revive was answered with an existing row",
                mDatabase.insertCurrency(currencyValues("TST")));

        assertEquals("a caller that did not ask for the revive brought the row back anyway",
                0, servedCount("TST"));
    }

    /** The revive is for the iso it was asked about, not for every row that is not served. */
    @Test
    public void insertCurrencyBringsBackOnlyTheIsoItWasAskedAbout() {
        assertEquals("TST", mDatabase.insertCurrency(currencyValues("TST")));
        assertEquals("TSU", mDatabase.insertCurrency(currencyValues("TSU")));
        markDeleted("TST");
        markDeleted("TSU");

        assertEquals("TST", mDatabase.insertCurrency(seedValues("TST")));

        assertEquals(1, servedCount("TST"));
        assertEquals("reviving one currency brought back another the caller said nothing about",
                0, servedCount("TSU"));
    }

    /**
     * The scale is the reason this writes as little as it does. Money is stored as a long scaled
     * by the currency's decimals, which is why updateCurrency reads the old value first and sends
     * every amount held in that currency through fixCurrencyAmounts before it changes them.
     */
    @Test
    public void insertCurrencyLeavesTheScaleOfTheRowItBringsBack() {
        ContentValues owned = currencyValues("TST");
        owned.put(Contract.Currency.DECIMALS, 2);
        assertEquals("TST", mDatabase.insertCurrency(owned));
        markDeleted("TST");
        // the default set's own idea of this currency, at a different scale from the owner's
        ContentValues fromTheAssetFile = seedValues("TST");
        fromTheAssetFile.put(Contract.Currency.DECIMALS, 0);

        fromTheAssetFile.put(Contract.Currency.NAME, "From the asset file");
        fromTheAssetFile.put(Contract.Currency.SYMBOL, "A");

        assertEquals("TST", mDatabase.insertCurrency(fromTheAssetFile));

        assertEquals("bringing the row back moved its decimals, so every amount already stored in "
                        + "that currency now means something else, with nothing rescaled and "
                        + "nothing said", 2, servedDecimals("TST"));
        // the same rule one size down. These are the owner's, and the asset file is not entitled
        // to them either
        assertEquals("bringing the row back renamed the owner's currency from the asset file",
                "Currency TST", servedString("TST", Contract.Currency.NAME));
        assertEquals("bringing the row back took the owner's symbol from the asset file",
                "T", servedString("TST", Contract.Currency.SYMBOL));
        // the uuid is the row's identity in the backup it came from, and the column is NOT NULL
        // UNIQUE, so writing a computed one over it can also collide and raise out of update,
        // where the insert above would have answered a quiet -1
        assertEquals("bringing the row back rewrote the uuid it arrived with",
                "from-the-backup-TST", storedValue("TST", Schema.Currency.UUID));
        assertEquals("bringing the row back cleared the owner's favourite flag",
                "1", storedValue("TST", Schema.Currency.FAVOURITE));
        // and it is a write, so it says when it happened
        assertFalse("bringing the row back left the timestamp of the backup it came from, so an "
                        + "export carries the wrong last edit for it",
                "1".equals(storedValue("TST", Schema.Currency.LAST_EDIT)));
    }

    /**
     * The revive is for a row that is not served. Asking for it against one that is has to be
     * refused, or a caller that meant to insert is told it did and a row nobody edited has its
     * timestamp moved. The case below covers a caller that did not ask, and returns at the gate
     * before this clause is reached, so it cannot stand in for this one.
     */
    @Test
    public void insertCurrencyDoesNotBringBackARowThatIsAlreadyServed() {
        assertEquals("TST", mDatabase.insertCurrency(currencyValues("TST")));
        String beforeTheSecondInsert = storedValue("TST", Schema.Currency.LAST_EDIT);

        assertNull("an insert that asked for the revive was answered with a row that is already "
                        + "served, so a collision reads as a success",
                mDatabase.insertCurrency(seedValues("TST")));

        assertEquals("the refused insert moved the timestamp of a row it did not change",
                beforeTheSecondInsert, storedValue("TST", Schema.Currency.LAST_EDIT));
    }

    @Test
    public void insertCurrencyStillRefusesOneThatIsAlreadyServed() {
        assertEquals("TST", mDatabase.insertCurrency(currencyValues("TST")));

        assertNull("a second insert of a currency that is already there was accepted, so the "
                        + "currency editor can take over one that exists",
                mDatabase.insertCurrency(currencyValues("TST")));
    }

    /** What the default set sends, which is the only caller that asks for a revive. */
    private ContentValues seedValues(String iso) {
        ContentValues contentValues = currencyValues(iso);
        contentValues.put(Contract.Currency.REVIVE_IF_DELETED, true);
        return contentValues;
    }

    private String servedString(String iso, String column) {
        Cursor cursor = mDatabase.getCurrencies(new String[] {column},
                Contract.Currency.ISO + " = ?", new String[] {iso}, null);
        assertNotNull(cursor);
        try {
            assertTrue("no served row for " + iso, cursor.moveToFirst());
            return cursor.getString(cursor.getColumnIndex(column));
        } finally {
            cursor.close();
        }
    }

    /**
     * The state a restore leaves, and not merely a deleted row. A row that arrived from a backup
     * carries that backup's uuid, which is nothing this build would compute, and whatever the
     * owner had set on it. A ghost built by deleting one of our own inserts holds exactly the
     * values a fresh insert would write, so it cannot tell a revive that keeps them from one that
     * writes them again.
     */
    private void markDeleted(String iso) {
        ContentValues values = new ContentValues();
        values.put(Schema.Currency.DELETED, true);
        values.put(Schema.Currency.UUID, "from-the-backup-" + iso);
        values.put(Schema.Currency.FAVOURITE, true);
        // a timestamp from the backup and not one this run produced. Left as the insert wrote it,
        // the case below compares two readings of the clock taken a few statements apart and can
        // fail on a fast run for a defect that is not there
        values.put(Schema.Currency.LAST_EDIT, 1L);
        mDatabase.getWritableDatabase().update(Schema.Currency.TABLE, values,
                Schema.Currency.ISO + " = ?", new String[] {iso});
    }

    /** Straight off the table, for the columns the served view does not carry. */
    private String storedValue(String iso, String column) {
        Cursor cursor = mDatabase.getWritableDatabase().query(Schema.Currency.TABLE,
                new String[] {column}, Schema.Currency.ISO + " = ?", new String[] {iso},
                null, null, null);
        assertNotNull(cursor);
        try {
            assertTrue("no row at all for " + iso, cursor.moveToFirst());
            return cursor.getString(0);
        } finally {
            cursor.close();
        }
    }

    private int servedCount(String iso) {
        Cursor cursor = mDatabase.getCurrencies(new String[] {Contract.Currency.ISO},
                Contract.Currency.ISO + " = ?", new String[] {iso}, null);
        try {
            return cursor == null ? 0 : cursor.getCount();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private int servedDecimals(String iso) {
        Cursor cursor = mDatabase.getCurrencies(new String[] {Contract.Currency.DECIMALS},
                Contract.Currency.ISO + " = ?", new String[] {iso}, null);
        assertNotNull(cursor);
        try {
            assertTrue("no served row for " + iso, cursor.moveToFirst());
            return cursor.getInt(cursor.getColumnIndex(Contract.Currency.DECIMALS));
        } finally {
            cursor.close();
        }
    }

    private ContentValues currencyValues(String iso) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Currency.ISO, iso);
        contentValues.put(Contract.Currency.NAME, "Currency " + iso);
        contentValues.put(Contract.Currency.SYMBOL, "T");
        // the column is NOT NULL with no default, so a currency without it is refused
        contentValues.put(Contract.Currency.DECIMALS, 2);
        return contentValues;
    }

    /**
     * Watches a cursor the way a CursorLoader does, by registering on the cursor itself. An
     * observer put on the resolver by hand instead would prove that the framework delivers a
     * notification and say nothing about the uri the provider registered the cursor on, which is
     * the whole of what these cases are about.
     *
     * It keeps the uris it is told about instead of counting. A case that only counts cannot tell
     * its own write from any other write in the process, and these run in the live app, where the
     * recurrence alarm can fire and a widget observer is already registered. That was harmless
     * while a cursor only heard about the entities it named and is not now.
     */
    private BlockingQueue<Uri> watchCursor(Cursor cursor) {
        final BlockingQueue<Uri> heard = new LinkedBlockingQueue<>();
        cursor.registerContentObserver(new ContentObserver(new Handler(Looper.getMainLooper())) {

            @Override
            public void onChange(boolean selfChange, Uri uri) {
                heard.add(uri != null ? uri : Uri.EMPTY);
            }

        });
        return heard;
    }

    /**
     * The same, for an observer put on the resolver instead of on a cursor.
     */
    private BlockingQueue<Uri> watchUris(ContentResolver contentResolver, Uri uri,
                                         boolean descendants, ContentObserver[] out) {
        final BlockingQueue<Uri> heard = new LinkedBlockingQueue<>();
        ContentObserver observer = new ContentObserver(new Handler(Looper.getMainLooper())) {

            @Override
            public void onChange(boolean selfChange, Uri changed) {
                heard.add(changed != null ? changed : Uri.EMPTY);
            }

        };
        contentResolver.registerContentObserver(uri, descendants, observer);
        out[0] = observer;
        return heard;
    }

    /**
     * Two seconds of nothing at all, and not two seconds of anything but one named uri. A write
     * that announced the bare authority would reach this observer as that uri and not as the row
     * it wrote, so a case that only refused the row uri would let it through. Call it after
     * something else has been shown to have heard the write, or it is two seconds of waiting for
     * a notification that has not been sent yet and it passes whatever the truth is.
     */
    private void assertHeardNothing(BlockingQueue<Uri> heard, String message)
            throws InterruptedException {
        Uri uri = heard.poll(2000L, TimeUnit.MILLISECONDS);
        assertNull(message + ", it was told about " + uri, uri);
    }

    /**
     * Waits for one named uri and ignores anything else that arrives. Five seconds in total and
     * not five seconds per uri, so a run of unrelated announcements cannot stretch it.
     */
    private void assertHeard(BlockingQueue<Uri> heard, Uri expected, String message)
            throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + 5000L;
        for (long left = 5000L; left > 0L; left = deadline - SystemClock.uptimeMillis()) {
            Uri uri = heard.poll(left, TimeUnit.MILLISECONDS);
            if (uri == null) {
                break;
            }
            if (expected.equals(uri)) {
                return;
            }
        }
        fail(message);
    }

    /**
     * Two writes of two entities, and the second one is what makes this a check. A single write
     * cannot tell a cursor registered on the whole provider apart from one registered on whatever
     * that write happens to announce, so a provider that put every cursor on the currency list
     * would pass a currency only version of this on its own.
     *
     * A currency is the first, because it is the entity the fewest queries read. A provider that
     * registered each cursor on the entities its own query names would leave every cursor here
     * except the currency one hearing nothing about it.
     */
    @Test
    public void everyListCursorIsToldAboutAWriteToAnyEntity() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        assertEveryListCursorIsToldAbout(contentResolver, DataContentProvider.CONTENT_CURRENCIES,
                currencyValues("TSA"));
        assertEveryListCursorIsToldAbout(contentResolver, DataContentProvider.CONTENT_WALLETS,
                walletValues("Written while every list is open"));
    }

    /**
     * Opens its own cursors each time it is called. Watching one cursor twice would not work,
     * since the wrapper replays what it has already been told to an observer that registers after
     * it, so the second round would start with its latch already down.
     */
    private void assertEveryListCursorIsToldAbout(ContentResolver contentResolver, Uri listUri,
                                                  ContentValues values) throws Exception {
        Cursor[] cursors = new Cursor[LIST_URIS.length];
        List<BlockingQueue<Uri>> heard = new ArrayList<>();
        try {
            for (int i = 0; i < LIST_URIS.length; i++) {
                cursors[i] = contentResolver.query(LIST_URIS[i], null, null, null, null);
                assertNotNull("the provider answered nothing for " + LIST_URIS[i], cursors[i]);
                heard.add(watchCursor(cursors[i]));
            }
            Uri written = contentResolver.insert(listUri, values);
            assertNotNull("the provider refused a row that is fine, so nothing was written for "
                    + "these cursors to hear about", written);
            for (int i = 0; i < LIST_URIS.length; i++) {
                assertHeard(heard.get(i), written, "the cursor on " + LIST_URIS[i] + " was not "
                        + "told about " + written + ", so a screen holding it goes on showing "
                        + "what was true before that write");
            }
        } finally {
            for (Cursor cursor : cursors) {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }
    }

    /**
     * The two kinds of branch the case above does not open, a list that hangs off a row and a
     * single row.
     *
     * Two writes on the first, and for the same reason as above. A transaction because that is the
     * relation this cursor was given a uri for by hand, saving a debt's master transaction writes
     * the debt's people. A category because nothing about a debt's people reads one, so a provider
     * registering this cursor on what its own query names would fail there and pass the
     * transaction on its own. The single row uri takes a currency for the same job, since the debt
     * row reads a wallet, a category, a transaction, a person and a place and never a currency.
     */
    @Test
    public void aSubListAndASingleRowCursorAreToldAboutAWriteToAnotherEntity() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        long walletId = insertWallet("Wallet the debt is against", "encoded-icon", "EUR", null,
                true, 0L, false, null);
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon", "desc",
                new Date(), null, walletId, null, null, 2000L, false, null, null, false);
        long categoryId = insertCategory("Category the transaction is in", "encoded-icon",
                Contract.CategoryType.EXPENSE.getValue(), null, true, null);
        Uri people = Uri.withAppendedPath(
                ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, debtId), "people");
        assertCursorIsToldAbout(contentResolver, people,
                DataContentProvider.CONTENT_TRANSACTIONS, transactionValues(walletId, categoryId));
        assertCursorIsToldAbout(contentResolver, people,
                DataContentProvider.CONTENT_CATEGORIES, categoryValues("Written somewhere else"));
        assertCursorIsToldAbout(contentResolver,
                ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, debtId),
                DataContentProvider.CONTENT_CURRENCIES, currencyValues("TSD"));
    }

    /**
     * Its own cursor each time, for the reason the list helper opens its own.
     */
    private void assertCursorIsToldAbout(ContentResolver contentResolver, Uri cursorUri,
                                         Uri listUri, ContentValues values)
            throws Exception {
        Cursor cursor = contentResolver.query(cursorUri, null, null, null, null);
        try {
            assertNotNull("the provider answered nothing for " + cursorUri, cursor);
            BlockingQueue<Uri> heard = watchCursor(cursor);
            Uri written = contentResolver.insert(listUri, values);
            assertNotNull("the provider refused a row that is fine, so nothing was written for "
                    + "this cursor to hear about", written);
            assertHeard(heard, written, "the cursor on " + cursorUri + " was not told about "
                    + written + ", so the detail screen holding it goes on showing what was true "
                    + "before that write");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * The replay, which is the one thing MultiUriCursorWrapper still does that the platform cursor
     * does not, and the reason it is still wrapped now that there is one uri to register. A loader
     * registers on its cursor after the query it is loading has returned, and on a real screen the
     * time between the two is the first window filling. AbstractCursor drops a change that lands
     * in there.
     *
     * Writing and then registering proves nothing on its own, since the announcement is delivered
     * asynchronously and would reach the second watcher through the ordinary path if it had not
     * arrived yet. What rules that out is the lock: registerContentObserver takes the one onChange
     * holds across both its dispatch and its add, so the second registration cannot run until the
     * change has been stored. Waiting for the first watcher only says the dispatch happened, and
     * that dispatch is a post the main thread can drain before the add has run.
     */
    @Test
    public void aCursorHandsAChangeItWasAlreadyToldAboutToALateObserver() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(DataContentProvider.CONTENT_WALLETS, null, null,
                null, null);
        try {
            assertNotNull("the provider answered nothing for the wallet list", cursor);
            BlockingQueue<Uri> early = watchCursor(cursor);
            Uri written = contentResolver.insert(DataContentProvider.CONTENT_WALLETS,
                    walletValues("Written before the late observer"));
            assertNotNull("the provider refused a wallet that is fine, so nothing was written for "
                    + "this cursor to be told about", written);
            assertHeard(early, written, "the cursor was never told about " + written
                    + ", so this case cannot say anything about what a later observer gets");
            assertHeard(watchCursor(cursor), written, "a change the cursor had already been told "
                    + "about was not handed to an observer that registered after it, so a loader "
                    + "registering after its query would hold what was true before that write");
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * The registration the widget makes, made here rather than by calling the widget, since
     * nothing can read back what the framework was asked to watch. So this pins the mechanism and
     * not WalletWidgetObserver's own two lines, which no case reaches.
     *
     * The second half is the part worth having. Nothing ever announces CONTENT_ALL itself, so an
     * observer that did not ask for the uris below it hears none of these writes, and that is what
     * carries the whole registration.
     */
    @Test
    public void anObserverOnTheWholeProviderIsToldWhicheverUriTheWriteAnnounces() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        Uri[] lists = {DataContentProvider.CONTENT_CURRENCIES, DataContentProvider.CONTENT_WALLETS};
        ContentValues[] values = {currencyValues("TSB"), walletValues("Watched by the widget")};
        for (int i = 0; i < lists.length; i++) {
            ContentObserver[] wide = new ContentObserver[1];
            ContentObserver[] narrow = new ContentObserver[1];
            BlockingQueue<Uri> wideHeard =
                    watchUris(contentResolver, DataContentProvider.CONTENT_ALL, true, wide);
            BlockingQueue<Uri> narrowHeard =
                    watchUris(contentResolver, DataContentProvider.CONTENT_ALL, false, narrow);
            try {
                Uri written = contentResolver.insert(lists[i], values[i]);
                assertNotNull("the provider refused a row that is fine, so nothing was written "
                        + "for this observer to hear about", written);
                assertHeard(wideHeard, written, "an observer on the whole provider was not told "
                        + "about " + written + ", which is how the widget would go stale after a "
                        + "write");
                assertHeardNothing(narrowHeard, "an observer that did not ask for the uris below "
                        + "CONTENT_ALL was told about a write. Either the descendants flag is not "
                        + "what carries this, or something now announces the bare authority");
            } finally {
                contentResolver.unregisterContentObserver(wide[0]);
                contentResolver.unregisterContentObserver(narrow[0]);
            }
        }
    }

    /**
     * The two importers both throw a checked exception, and the transaction body cannot declare
     * one, so runInOneTransaction wraps it on the way in and unwraps it on the way out. Every
     * other case here throws an unchecked one and never reaches that path.
     */
    @Test
    public void aCheckedFailureLeavesTheImportAsItself() throws Exception {
        ContentResolver contentResolver = mContext.getContentResolver();
        IOException thrown = new IOException("the backup file ended early");
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                assertNotNull("the provider refused a wallet that is fine, so there is no row for "
                                + "this case to roll back",
                        contentResolver.insert(DataContentProvider.CONTENT_WALLETS, walletValues("Rolled back")));
                throw thrown;
            });
            fail("the failure did not leave runInOneTransaction");
        } catch (IOException expected) {
            // the same object, not one that merely reads the same. The screen that reports a
            // failed import branches on what it caught, and a build that rebuilt the failure from
            // its message would keep the words and lose the type
            assertSame("the failure was rebuilt on the way out instead of carried out", thrown, expected);
        }
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 0);
    }

    @Test
    public void aNestedInTransactionCommitsWithTheOuterOne() {
        long nestedId = mDatabase.inTransaction(() -> {
            insertWallet("Written before the nested call", "icon", "EUR", "note", true, 0L, false, "tag");
            return mDatabase.inTransaction(() ->
                    insertWallet("Nested", "icon", "EUR", "note", true, 0L, false, "tag"));
        });
        assertTrue("the nested call did not insert a row", nestedId > 0L);
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 2);
    }

    @Test
    public void aRolledBackWalletDeleteLeavesItsAttachmentFileOnDisk() throws Exception {
        File file = writeAttachmentFile("path1");
        long walletId = walletWithAnAttachedTransaction("path1");
        try {
            mDatabase.<Integer>inTransaction(() -> {
                mDatabase.deleteWallet(walletId);
                throw new IllegalStateException("forced after the delete");
            });
            fail("the body's exception did not leave inTransaction");
        } catch (IllegalStateException expected) {
            // as above
        }
        // the rows came back, so a file deleted here would be one the restored rows still name
        assertTrue("the attachment file was deleted before the commit", file.exists());
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 1);
        assertRowCount(Schema.Attachment.TABLE, null, 1);
    }

    @Test
    public void aCommittedWalletDeleteRemovesItsAttachmentFile() throws Exception {
        File file = writeAttachmentFile("path1");
        long walletId = walletWithAnAttachedTransaction("path1");
        mDatabase.inTransaction(() -> mDatabase.deleteWallet(walletId));
        assertFalse("the attachment file outlived a committed delete", file.exists());
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 0);
        assertRowCount(Schema.Attachment.TABLE, null, 0);
    }

    /**
     * The attachment names a nested body collects belong to the outer transaction. A nested call
     * that started a list of its own, or cleared the one already there, would leave this file on
     * the volume forever with no row naming it, and every count in this case would still be right.
     */
    @Test
    public void aNestedDeleteHandsItsAttachmentFilesToTheOuterCommit() throws Exception {
        File file = writeAttachmentFile("path1");
        long walletId = walletWithAnAttachedTransaction("path1");
        mDatabase.inTransaction(() -> mDatabase.inTransaction(() -> mDatabase.deleteWallet(walletId)));
        assertFalse("the file removed by the nested body outlived the outer commit", file.exists());
        assertRowCount(Schema.Attachment.TABLE, null, 0);
    }

    /**
     * The rollback half of the case above. A nested body that collected the names into a list of
     * its own and deleted them itself would pass that one, and would leave this rollback holding
     * rows that name files already gone from the volume.
     *
     * Its own file name, because the attachment folder is not cleaned between cases and another
     * one deliberately leaves path1 behind, which would make the assertion below pass on its own.
     */
    @Test
    public void aRolledBackNestedDeleteLeavesItsAttachmentFileOnDisk() throws Exception {
        File file = writeAttachmentFile("pathNestedRollback");
        long walletId = walletWithAnAttachedTransaction("pathNestedRollback");
        try {
            mDatabase.<Integer>inTransaction(() -> {
                mDatabase.inTransaction(() -> mDatabase.deleteWallet(walletId));
                throw new ImportStopped();
            });
            fail("the body's exception did not leave inTransaction");
        } catch (ImportStopped expected) {
            // the delete came back out, so the file the nested body collected has to still be here
        }
        assertTrue("the nested body deleted its attachment file before the outer transaction "
                + "committed, and the rollback has put back rows that name it", file.exists());
        assertRowCount(Schema.Attachment.TABLE, null, 1);
    }

    /**
     * The case the join exists for, and the only one that tells it apart from a real nested pair.
     * Android rolls the whole transaction back when an inner frame ends unmarked and says nothing,
     * so under a nested pair the row written after the catch would disappear at the commit while
     * this method returned as though it had worked.
     */
    @Test
    public void anOuterBodyThatCatchesANestedFailureStillCommits() {
        mDatabase.inTransaction(() -> {
            insertWallet("Written before the nested call", "icon", "EUR", "note", true, 0L, false, "tag");
            try {
                mDatabase.<Long>inTransaction(() -> {
                    throw new ImportStopped();
                });
                fail("the nested body's exception did not leave inTransaction");
            } catch (ImportStopped caughtAndCarriedOn) {
                // exactly what the old refusal made impossible and the join has to survive
            }
            return insertWallet("Written after the catch", "icon", "EUR", "note", true, 0L, false, "tag");
        });
        checkCursorSize(mDatabase.getWallets(null, null, null, null), 2);
    }

    @Test(expected = SQLiteDataException.class)
    public void deleteWalletInTransfer() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertTransfer("desc", new Date(), id1, id2, null, 10L, 10L, 0L, "note", null, null, true, true, null, null, "tag");
        // the wallet cannot be deleted because is used in the transfer 3
        mDatabase.deleteWallet(id2);
    }

    @Test
    public void insertTransaction() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 2", "encoded-icon-2", Contract.CategoryType.INCOME.getValue(), null, true, "tag-category-2");
        long id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        Date date = new Date();
        Long[] peopleIds = new Long[] {id4, id5};
        Long[] attachmentIds = new Long[] {id6, id7};
        long id8 = insertTransaction(2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, attachmentIds, "tag");
        checkTransactionId(id8, 2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, "tag");
    }

    @Test
    public void updateTransaction() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 2", "encoded-icon-2", Contract.CategoryType.INCOME.getValue(), null, true, "tag-category-2");
        long id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        Date date = new Date();
        Long[] peopleIds = new Long[] {id4, id5};
        Long[] attachmentIds = new Long[] {id6, id7};
        long id8 = insertTransaction(2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, attachmentIds, "tag");
        // now modify the transaction
        long id9 = insertWallet("Test wallet 9", "encoded-icon-9", "USD", "note-wallet-9", false, 2000L, false, "tag-wallet-9");
        long id10 = insertCategory("Test category 10", "encoded-icon-10", Contract.CategoryType.EXPENSE.getValue(), null, true, "tag-category-10");
        long id11 = insertPerson("person-11", "encoded-icon-11", "note-11", "tag-11");
        peopleIds = new Long[] {id4, id5, id11};
        assertEquals(1, updateTransaction(id8, 4000L, date, "desc-edited", id10, Contract.Direction.EXPENSE, 0, id9, null, "note-edited", null, null, null, true, false, peopleIds, null, "tag-edited"));
        // now check if transaction has been properly edited
        checkTransactionId(id8, 4000L, date, "desc-edited", id10, Contract.Direction.EXPENSE, 0, id9, null, "note-edited", null, null, null, true, false, peopleIds, "tag-edited");
    }

    @Test
    public void deleteTransaction() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 2", "encoded-icon-2", Contract.CategoryType.INCOME.getValue(), null, true, "tag-category-2");
        long id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        Date date = new Date();
        Long[] peopleIds = new Long[] {id4, id5};
        Long[] attachmentIds = new Long[] {id6, id7};
        long id8 = insertTransaction(2000L, date, "desc", id2, Contract.Direction.INCOME, 0, id1, id3, "note", null, null, null, true, true, peopleIds, attachmentIds, "tag");
        // check the transaction count
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 1);
        // now delete the transaction item
        mDatabase.deleteTransaction(id8);
        // recheck the transaction count
        checkCursorSize(mDatabase.getTransaction(id8, null), 0);
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 0);
    }

    @Test
    public void insertTransfer() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        long id8 = insertEvent("event-8", "encoded-icon-8", new Date(), new Date(), "note-8", "tag-8");
        Date date = new Date();
        Long[] peopleIds = new Long[] {id4, id5};
        Long[] attachmentIds = new Long[] {id6, id7};
        long id9 = insertTransfer("desc", date, id1, id2, id1, 10L, 10L, 4L, "note", id3, id8, true, true, peopleIds, attachmentIds, "tag");
        checkTransferId(id9, "desc", date, id1, id2, id1, 10L, 10L, 4L, "note", id3, id8, true, true, peopleIds, "tag");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 3);
    }

    @Test
    public void updateTransfer() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        long id8 = insertEvent("event-8", "encoded-icon-8", new Date(), new Date(), "note-8", "tag-8");
        Date date = new Date();
        Long[] peopleIds = new Long[] {id4, id5};
        Long[] attachmentIds = new Long[] {id6, id7};
        long id9 = insertTransfer("desc", date, id1, id2, null, 10L, 10L, 0L, "note", id3, id8, true, true, peopleIds, attachmentIds, "tag");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 2);
        // now we update the transfer adding the tax
        long id10 = insertPerson("person-10", "encoded-icon-10", "note-10", "tag-10");
        long id11 = insertPerson("person-11", "encoded-icon-11", "note-11", "tag-11");
        peopleIds = new Long[] {id10, id11};
        assertEquals(1, updateTransfer(id9, "desc-edited", date, id1, id2, id1, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, null, "tag-edited"));
        checkTransferId(id9, "desc-edited", date, id1, id2, id1, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, "tag-edited");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 3);
        // now we change the source wallet
        long id12 = insertWallet("Test wallet 12", "encoded-icon-12", "EUR", "note-wallet-12", true, 22000L, false, "tag-wallet-12");
        assertEquals(1, updateTransfer(id9, "desc-edited", date, id12, id2, id12, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, null, "tag-edited"));
        checkTransferId(id9, "desc-edited", date, id12, id2, id12, 10L, 10L, 4L, "note-edited", id3, id8, false, false, peopleIds, "tag-edited");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 3);
        // now remove again the tax
        assertEquals(1, updateTransfer(id9, "desc-edited", date, id12, id2, null, 10L, 10L, 0L, "note-edited", id3, id8, false, false, peopleIds, null, "tag-edited"));
        checkTransferId(id9, "desc-edited", date, id12, id2, null, 10L, 10L, 0L, "note-edited", id3, id8, false, false, peopleIds, "tag-edited");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 2);
    }

    @Test
    public void deleteTransfer() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id7 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        long id8 = insertEvent("event-8", "encoded-icon-8", new Date(), new Date(), "note-8", "tag-8");
        Date date = new Date();
        Long[] peopleIds = new Long[] {id4, id5};
        Long[] attachmentIds = new Long[] {id6, id7};
        long id9 = insertTransfer("desc", date, id1, id2, id1, 10L, 10L, 2L, "note", id3, id8, true, true, peopleIds, attachmentIds, "tag");
        checkCursorSize(mDatabase.getTransfers(null, null, null, null), 1);
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 3);
        // now delete the transfer
        mDatabase.deleteTransfer(id9);
        // recheck the table sizes
        checkCursorSize(mDatabase.getTransfer(id9, null), 0);
        checkCursorSize(mDatabase.getTransfers(null, null, null, null), 0);
        // also the transactions must be removed!!!
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 0);
    }

    @Test
    public void insertCategory() throws Exception {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 1, null, true, "category-tag-3");
        long id4 = insertCategory("category 4", "encoded-icon-4", 1, id3, true, "category-tag-4");
        long id5 = insertCategory("category 5", "encoded-icon-5", 1, null, true, "category-tag-5");
        checkCategoryId(id1, "category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        checkCategoryId(id2, "category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        checkCategoryId(id3, "category 3", "encoded-icon-3", 1, null, true, "category-tag-3");
        checkCategoryId(id4, "category 4", "encoded-icon-4", 1, id3, true, "category-tag-4");
        checkCategoryId(id5, "category 5", "encoded-icon-5", 1, null, true, "category-tag-5");
    }

    @Test(expected = SQLiteDataException.class)
    public void testInsertNestedCategories() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 0, id2, false, "category-tag-3");
    }

    @Test(expected = SQLiteDataException.class)
    public void testInsertInconsistentCategoryTree() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 1, id1, false, "category-tag-2");
    }

    @Test
    public void updateCategory() throws Exception {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 1, null, true, "category-tag-3");
        long id4 = insertCategory("category 4", "encoded-icon-4", 1, id3, true, "category-tag-4");
        long id5 = insertCategory("category 5", "encoded-icon-5", 1, null, true, "category-tag-5");
        // move category 5 as child of category 3 and edit category 2
        assertEquals(1, updateCategory(id2, "category 2-edited", "encoded-icon-2-edited", 0, null, true, "category-tag-2-edited"));
        assertEquals(1, updateCategory(id5, "category 5-edited", "encoded-icon-5-edited", 1, id3, false, "category-tag-5-edited"));
        // check that categories 1, 3 and 4 are not changed and category 2 and 5 are changed
        checkCategoryId(id1, "category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        checkCategoryId(id2, "category 2-edited", "encoded-icon-2-edited", 0, null, true, "category-tag-2-edited");
        checkCategoryId(id3, "category 3", "encoded-icon-3", 1, null, true, "category-tag-3");
        checkCategoryId(id4, "category 4", "encoded-icon-4", 1, id3, true, "category-tag-4");
        checkCategoryId(id5, "category 5-edited", "encoded-icon-5-edited", 1, id3, false, "category-tag-5-edited");
    }

    @Test(expected = SQLiteDataException.class)
    public void testUpdateNestedCategories1() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 0, null, false, "category-tag-3");
        // update category 1 to become children of category 3
        updateCategory(id1, "category 1", "encoded-icon-1", 0, id3, true, "category-tag-1");
    }

    @Test(expected = SQLiteDataException.class)
    public void testUpdateNestedCategories2() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 0, null, false, "category-tag-3");
        // update category 3 to become children of category 2
        updateCategory(id3, "category 3", "encoded-icon-3", 0, id2, false, "category-tag-3");
    }

    @Test(expected = SQLiteDataException.class)
    public void testUpdateInconsistentChildrenCategory() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 0, id1, false, "category-tag-3");
        // now change the type of the child category 3
        updateCategory(id3, "category 3", "encoded-icon-3", 1, id1, false, "category-tag-3");
    }

    @Test(expected = SQLiteDataException.class)
    public void testUpdateInconsistentParentCategory() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 0, id1, false, "category-tag-3");
        // now change the type of the parent category 1
        updateCategory(id1, "category 1", "encoded-icon-1", 1, null, true, "category-tag-1");
    }

    @Test
    public void deleteCategory() throws Exception {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        long id3 = insertCategory("category 3", "encoded-icon-3", 0, id1, false, "category-tag-3");
        // check cursor size
        int count = checkCursorMinSize(mDatabase.getCategories(null, null, null, null), 3);
        // now delete category 2 and 3
        assertEquals(1, mDatabase.deleteCategory(id2));
        assertEquals(1, mDatabase.deleteCategory(id3));
        // recheck cursor size
        int newCount = checkCursorMinSize(mDatabase.getCategories(null, null, null, null), 1);
        assertEquals(newCount, count - 2);
    }

    @Test(expected = SQLiteDataException.class)
    public void testDeleteCategoryWithChildren() {
        long id1 = insertCategory("category 1", "encoded-icon-1", 0, null, true, "category-tag-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, id1, false, "category-tag-2");
        // category 1 must not be removed because it has at least one child
        mDatabase.deleteCategory(id1);
    }

    @Test(expected = SQLiteDataException.class)
    public void testDeleteSystemCategory() {
        long categoryId = getSystemCategory(Contract.CategoryTag.TRANSFER);
        // a system category cannot be deleted
        mDatabase.deleteCategory(categoryId);
    }

    @Test(expected = SQLiteDataException.class)
    public void testDeleteCategoryUsedInTransaction() {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, null, true, "category-tag-2");
        long id3 = insertTransaction(2000L, new Date(), "description-3", id2, Contract.Direction.INCOME, 0, id1, null, "note-3", null, null, null, true, true, null, null, "tag-3");
        // the category cannot be deleted because it is used in transaction 3
        mDatabase.deleteCategory(id2);
    }

    @Test(expected = SQLiteDataException.class)
    public void testDeleteCategoryUsedInTransactionModel() {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, null, true, "category-tag-2");
        long id3 = insertTransactionModel(2000L, "description-3", id2, Contract.Direction.INCOME, id1, null, "note-3", null, true, true, "tag-3");
        // the category cannot be deleted because it is used in transaction-model 3
        mDatabase.deleteCategory(id2);
    }

    @Test(expected = SQLiteDataException.class)
    public void testDeleteCategoryUsedInBudget() {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("category 2", "encoded-icon-2", 0, null, true, "category-tag-2");
        long id3 = insertBudget(Schema.BudgetType.CATEGORY, id2, new Date(), new Date(), 3000L, "EUR", new Long[] {id1}, "tag");
        // the category cannot be deleted because it is used in budget 3
        mDatabase.deleteCategory(id2);
    }

    @Test
    public void insertDebt() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id3 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id4 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id5 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3");
        Date date = new Date();
        Long[] peopleIds1 = new Long[] {id3, id4, id5};
        long id6 = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1", false);
        checkDebtId(id6, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 0);
        long id7 = insertDebt(Contract.DebtType.CREDIT.getValue(), "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2", true);
        checkDebtId(id7, Contract.DebtType.CREDIT.getValue(), "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 1);
    }

    @Test
    public void updateDebt() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id3 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id4 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id5 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3");
        Date date = new Date();
        Long[] peopleIds1 = new Long[] {id3, id4};
        long id6 = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1", false);
        long id7 = insertDebt(Contract.DebtType.CREDIT.getValue(), "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2", true);
        checkDebtId(id6, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1");
        checkDebtId(id7, Contract.DebtType.CREDIT.getValue(), "encoded-icon-1", "desc-1", date, date, id1, "note-1", id2, 3000L, true, null, "tag-2");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 1);
        // now update both debts
        peopleIds1 = new Long[] {id4, id5};
        assertEquals(1, updateDebt(id6, Contract.DebtType.DEBT.getValue(), "encoded-icon-1-edited", "desc-1-edited", date, date, id1, "note-1-edited", id2, 3000L, true, peopleIds1, "tag-1-edited"));
        assertEquals(1, updateDebt(id7, Contract.DebtType.CREDIT.getValue(), "encoded-icon-2-edited", "desc-2-edited", date, null, id1, "note-1-edited", id2, 8000L, false, null, "tag-2-edited"));
        // now check that everything has been updated correctly
        checkDebtId(id6, Contract.DebtType.DEBT.getValue(), "encoded-icon-1-edited", "desc-1-edited", date, date, id1, "note-1-edited", id2, 3000L, true, peopleIds1, "tag-1-edited");
        checkDebtId(id7, Contract.DebtType.CREDIT.getValue(), "encoded-icon-2-edited", "desc-2-edited", date, null, id1, "note-1-edited", id2, 8000L, false, null, "tag-2-edited");
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 1);
    }

    @Test
    public void debtMasterTransactionTakesTheDebtDate() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        // A past date, so a failure reads as a wrong month, not a wrong time of day. What
        // actually left the existing insertDebt and updateDebt tests blind to this is that they
        // never assert the transaction's date at all, only how many rows came back.
        Date picked = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", picked, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true);
        assertEquals(DateUtils.getSQLDateTimeString(picked), masterTransactionDate(debtId));
        // and it follows the debt when the date is edited
        Date moved = DateUtils.getDateFromSQLDateString("2026-06-15");
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", moved, null, walletId, "note-1", null, 2000L, false, null, "tag-1"));
        assertEquals(DateUtils.getSQLDateTimeString(moved), masterTransactionDate(debtId));
    }

    private String masterTransactionDate(long debtId) {
        Cursor cursor = mDatabase.getTransactions(new String[] {Contract.Transaction.DATE},
                Contract.Transaction.DEBT_ID + " = ?", new String[] {String.valueOf(debtId)}, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        String date = cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE));
        cursor.close();
        return date;
    }

    @Test
    public void savingADebtKeepsItsMasterTransactionTime() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long categoryId = getSystemCategory(Contract.CategoryTag.DEBT);
        Date picked = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", picked, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true);
        // A master transaction is created at the start of the debt's day, so the time under test
        // has to be put there the way a user does it, through the transaction editor.
        Date afternoon = DateUtils.getDateFromSQLDateTimeString("2026-07-01 14:30:45");
        assertEquals(1, updateTransaction(masterTransactionId(debtId), 2000L, afternoon, "desc-1", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-1"));
        assertEquals("2026-07-01 14:30:45", masterTransactionDate(debtId));
        // The debt editor sends its date on every save, so this is a save that changed something
        // else and never opened the date field.
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-edited", picked, null, walletId, "note-1", null, 2000L, false, null, "tag-1"));
        assertEquals("2026-07-01 14:30:45", masterTransactionDate(debtId));
        // And a date that did move takes the transaction to the new day at the same time.
        Date moved = DateUtils.getDateFromSQLDateString("2026-06-15");
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-edited", moved, null, walletId, "note-1", null, 2000L, false, null, "tag-1"));
        assertEquals("2026-06-15 14:30:45", masterTransactionDate(debtId));
    }

    @Test
    public void editingTheMasterTransactionMovesTheDebt() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long otherWalletId = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long placeId = insertPlace("place-1", "encoded-icon-1", "fake-address-1", 7.3467, 8.364, "tag-1");
        long otherPlaceId = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 9.1234, 3.567, "tag-2");
        long personId1 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long personId2 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long personId3 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3");
        long categoryId = getSystemCategory(Contract.CategoryTag.DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        Long[] peopleIds = new Long[] {personId1, personId2};
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, walletId, "note-1", placeId, 2000L, false, peopleIds, "tag-1", true);
        long transactionId = masterTransactionId(debtId);
        // Everything a debt owns, moved at once at the method the transaction editor's save
        // reaches, which is where Edit on this row in the transaction list ends up. Without
        // the sync the debt keeps 2000.00, 2026-07-01, the first wallet, the first place, the old
        // description, the old note and the first two people.
        Date moved = DateUtils.getDateFromSQLDateTimeString("2026-08-25 14:30:00");
        Long[] editedPeopleIds = new Long[] {personId2, personId3};
        assertEquals(1, updateTransaction(transactionId, 3000L, moved, "desc-edited", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, otherWalletId, otherPlaceId,
                "note-edited", null, null, debtId, true, true, editedPeopleIds, null, "tag-transaction"));
        // The expiration date and the archived flag have no column on a transaction, and the
        // tag has one on both tables and is carried by neither direction, so all three are
        // asserted at the values the debt was inserted with. The tag asserted here is the debt's
        // own and the save above sends a different one to the transaction. The icon is asserted
        // unchanged for its own reason, encoded-icon-1 is not readable json, so renamedDebtIcon
        // leaves it alone. A debt whose icon does move is pinned by
        // renamingTheMasterTransactionMovesTheDebtIconLetters below.
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-edited", moved, null, otherWalletId, "note-edited", otherPlaceId, 3000L, false, editedPeopleIds, "tag-1");
        // And the transaction keeps the time of day it was given. The debt carries a day, so
        // writing the debt's date back onto the transaction would drop it to 00:00:00.
        assertEquals("2026-08-25 14:30:00", masterTransactionDate(debtId));
    }

    @Test
    public void editingADebtPaymentLeavesTheDebtAlone() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", false);
        // A payment carries the same type and the same debt id as a master transaction and is
        // told apart only by its category. Syncing one would drag the debt to the amount and the
        // day of whichever payment was edited.
        long paymentId = insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, walletId, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        Date moved = DateUtils.getDateFromSQLDateTimeString("2026-08-25 14:30:00");
        assertEquals(1, updateTransaction(paymentId, 800L, moved, "payment-edited", paidCategoryId,
                Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, walletId, null, "payment-note-edited",
                null, null, debtId, true, true, null, null, "tag-payment"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1");
    }

    @Test
    public void aBlankDescriptionIsNotCarriedToTheDebt() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long categoryId = getSystemCategory(Contract.CategoryTag.DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true);
        long transactionId = masterTransactionId(debtId);
        // The transaction editor has no validator on the description, so it can be blanked there.
        // Debt.DESCRIPTION is NOT NULL and the debt editor holds it non empty after a trim, so
        // carrying a blank across would leave a debt that its own editor then refuses to save.
        // One space and not the empty string, since one space is the blank a length test lets
        // through and the guard's own trim does not.
        //
        // The money moves in the same save and is asserted below at its new amount. Without it
        // this case passes just as well on a build that never syncs anything, which is the state
        // it is meant to tell apart from a working guard.
        assertEquals(1, updateTransaction(transactionId, 3000L, date, " ", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-transaction"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, walletId, "note-1", null, 3000L, false, null, "tag-1");
    }

    @Test
    public void renamingTheMasterTransactionMovesTheDebtIconLetters() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long categoryId = getSystemCategory(Contract.CategoryTag.DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        // The letters a debt of this name would have been given by its own editor.
        String storedIcon = new ColorIcon("#FF0000", "RP").toString();
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), storedIcon, "Rent Payment", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true);
        long transactionId = masterTransactionId(debtId);
        assertEquals(1, updateTransaction(transactionId, 2000L, date, "Car Loan", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-transaction"));
        // Same color, new letters. Without this the list row reads Car Loan beside an RP icon.
        String renamedIcon = new ColorIcon("#FF0000", "CL").toString();
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), renamedIcon, "Car Loan", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1");
    }

    @Test
    public void anEditThatIsNotARenameLeavesTheDebtIconAlone() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long categoryId = getSystemCategory(Contract.CategoryTag.DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        // Letters that do not match the description, which IconPicker.restoreColorIcon can write.
        // A debt whose letters already agree would pass this case whether the rename test is here
        // or not, since the value written would be the value already stored.
        String storedIcon = new ColorIcon("#FF0000", "r").toString();
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), storedIcon, "rent payment", date, null, walletId, "note-1", null, 2000L, false, null, "tag-1", true);
        long transactionId = masterTransactionId(debtId);
        // Same description, new amount. The editor sends the description on every save, so this
        // reaches the sync looking exactly like a rename does.
        assertEquals(1, updateTransaction(transactionId, 5000L, date, "rent payment", categoryId,
                Contract.Direction.INCOME, Contract.TransactionType.DEBT, walletId, null, "note-1",
                null, null, debtId, true, true, null, null, "tag-transaction"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), storedIcon, "rent payment", date, null, walletId, "note-1", null, 5000L, false, null, "tag-1");
    }

    /**
     * A debt is read in the currency of its wallet and what is left to settle is that amount less
     * its payments, added up with no currency in the sum. The payments stay in the wallet they
     * were filed against when the debt moves, so the move is refused.
     */
    @Test
    public void movingADebtAwayFromTheCurrencyItsPaymentsAreInIsRefused() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false);
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        try {
            updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1");
            fail("The debt was moved away from the currency its payments are in");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1");
    }

    /**
     * And the move back is allowed. A ledger that was moved across currencies before any of this
     * existed holds a debt in one currency and its payments in another, and the move that puts it
     * right is the one a check made against the debt's own wallet would refuse hardest. Deleting
     * the debt would then be the only way out, and that takes the payments with it.
     */
    @Test
    public void movingADebtBackToTheCurrencyItsPaymentsAreInIsAllowed() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        // The debt is held in euro and its payment sits in the dollar wallet.
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false);
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1");
    }

    /**
     * A mismatched debt can still be moved between two wallets of the currency it is read in. That
     * move puts nothing right and takes nothing further wrong, and refusing it would leave a
     * legacy ledger with one permitted wallet and deletion as the only other way out.
     */
    @Test
    public void movingAMismatchedDebtWithinItsOwnCurrencyIsAllowed() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long otherEuroWallet = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long dollarWallet = insertWallet("Test wallet 3", "encoded-icon-3", "USD", "note-wallet-3", true, 2000L, false, "tag-wallet-3");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false);
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, otherEuroWallet, "note-1", null, 2000L, false, null, "tag-1"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, otherEuroWallet, "note-1", null, 2000L, false, null, "tag-1");
    }

    /**
     * And a third currency is still refused, which is what separates the case above from no check
     * at all on a mismatched debt.
     */
    @Test
    public void movingAMismatchedDebtToAThirdCurrencyIsRefused() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long poundWallet = insertWallet("Test wallet 3", "encoded-icon-3", "GBP", "note-wallet-3", true, 2000L, false, "tag-wallet-3");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false);
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        try {
            updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, poundWallet, "note-1", null, 2000L, false, null, "tag-1");
            fail("The debt was moved to a currency matching neither what it holds nor its payments");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1");
    }

    /**
     * A debt whose payments are already spread over two currencies is still held to those two. It
     * cannot be put fully right by any single wallet, which is a reason to leave both of the
     * currencies it holds open and not a reason to let it go anywhere at all.
     */
    @Test
    public void movingAMixedDebtToACurrencyItHoldsNoneOfIsRefused() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long poundWallet = insertWallet("Test wallet 3", "encoded-icon-3", "GBP", "note-wallet-3", true, 2000L, false, "tag-wallet-3");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false);
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        try {
            updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, poundWallet, "note-1", null, 2000L, false, null, "tag-1");
            fail("The debt was moved to a currency it holds nothing in");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1");
    }

    /**
     * And either of the two currencies it does hold is still open to it.
     */
    @Test
    public void movingAMixedDebtToOneOfItsOwnCurrenciesIsAllowed() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", false);
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, dollarWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1");
    }

    /**
     * A debt with nothing filed against it strands nothing, so it can be moved anywhere. A debt
     * given its master transaction strands nothing either, since updateDebt carries that
     * transaction to the new wallet with it.
     */
    @Test
    public void movingADebtThatStrandsNothingGoesThrough() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", true);
        assertEquals(1, updateDebt(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1"));
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, dollarWallet, "note-1", null, 2000L, false, null, "tag-1");
        assertEquals(dollarWallet, masterTransactionWallet(debtId));
    }

    /**
     * The same move made from the other side. A debt's master transaction still offers its wallet,
     * and the sync carries that wallet onto the debt, so this reaches the debt without the debt
     * editor being opened.
     *
     * The transaction is asserted back where it started as well as the debt. Nothing here runs
     * inside a database transaction, so a refusal raised after the row was written would leave the
     * two in different wallets, which is worse than the move it refused.
     */
    @Test
    public void movingTheMasterTransactionToAnotherCurrencyMovesNothing() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long categoryId = getSystemCategory(Contract.CategoryTag.DEBT);
        long paidCategoryId = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long debtId = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1", true);
        // The payment is what the move would strand. Without one the debt has nothing left behind
        // and the move is allowed, which movingADebtThatStrandsNothingGoesThrough pins.
        insertTransaction(500L, date, "payment", paidCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.DEBT, euroWallet, null, "payment-note", null, null, debtId, true, true, null, null, "tag-payment");
        long transactionId = masterTransactionId(debtId);
        try {
            updateTransaction(transactionId, 2000L, date, "desc-1", categoryId,
                    Contract.Direction.INCOME, Contract.TransactionType.DEBT, dollarWallet, null,
                    "note-1", null, null, debtId, true, true, null, null, "tag-transaction");
            fail("The master transaction was moved to a wallet in another currency");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        checkDebtId(debtId, Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, euroWallet, "note-1", null, 2000L, false, null, "tag-1");
        assertEquals(euroWallet, masterTransactionWallet(debtId));
    }

    private long masterTransactionWallet(long debtId) {
        Cursor cursor = mDatabase.getTransaction(masterTransactionId(debtId),
                new String[] {Contract.Transaction.WALLET_ID});
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        long walletId = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.WALLET_ID));
        cursor.close();
        return walletId;
    }

    private long masterTransactionId(long debtId) {
        // By category, since a debt that also carries a payment has more than one transaction and
        // only one of them is the master. Every debt here is a debt and not a credit, so the debt
        // system category is the only one this has to match.
        Cursor cursor = mDatabase.getTransactions(new String[] {Contract.Transaction.ID},
                Contract.Transaction.DEBT_ID + " = ? AND " + Contract.Transaction.CATEGORY_ID + " = ?",
                new String[] {String.valueOf(debtId), String.valueOf(getSystemCategory(Contract.CategoryTag.DEBT))}, null);
        assertNotNull(cursor);
        assertEquals(1, cursor.getCount());
        cursor.moveToFirst();
        long id = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.ID));
        cursor.close();
        return id;
    }

    @Test
    public void deleteDebt() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        long id3 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id4 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id5 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3");
        long id6 = getSystemCategory(Contract.CategoryTag.PAID_DEBT);
        Date date = new Date();
        Long[] peopleIds1 = new Long[] {id3, id4};
        long id7 = insertDebt(Contract.DebtType.DEBT.getValue(), "encoded-icon-1", "desc-1", date, null, id1, "note-1", id2, 2000L, false, peopleIds1, "tag-1", true);
        long id8 = insertTransaction(10, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, null, null, null, null, id7, true, true, new Long[] {id5}, null, "tag");
        long id9 = insertTransaction(10, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, id2, null, null, null, id7, true, true, null, null, "tag");
        long id10 = insertTransaction(10, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, null, null, null, null, id7, true, true, new Long[] {id3, id4}, null, "tag");
        long id11 = insertTransaction(10, date, "desc", id6, Contract.Direction.EXPENSE, Contract.TransactionType.DEBT, id1, id2, null, null, null, id7, true, true, null, null, "tag");
        checkCursorSize(mDatabase.getDebts(null, null, null, null), 1);
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 5);
        // now delete the debt and check if all the related transactions are removed
        assertEquals(1, mDatabase.deleteDebt(id7));
        checkCursorSize(mDatabase.getDebts(null, null, null, null), 0);
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 0);
    }

    @Test
    public void insertBudget() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        long id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5");
        long id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds1 = new Long[] {id1, id3};
        Long[] walletIds2 = new Long[] {id1, id2, id3};
        Long[] walletIds3 = new Long[] {id4, id5};
        long id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1");
        long id8 = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2");
        long id9 = insertBudget(Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3");
        // now ensure everything is stored correctly
        checkBudgetId(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1", 0L);
        checkBudgetId(id8, Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2", 0L);
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3", 0L);
    }

    @Test
    public void budgetCoveringSeveralCategoriesCountsThemAll() throws Exception {
        long wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] wallets = new Long[] {wallet};
        long budget = insertBudgetCovering(Schema.BudgetType.CATEGORY, new Long[] {food, rent}, startDate, endDate, 5000L, "EUR", wallets, "tag-1");
        insertTransaction(100, new Date(), null, food, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        insertTransaction(250, new Date(), null, rent, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        // the control: a category the budget does not cover, in the same wallet and the same
        // period, so a budget that matched on the wallet alone would swallow it
        insertTransaction(700, new Date(), null, petrol, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        checkBudgetCategories(budget, new Long[] {food, rent}, "Food, Rent", -350L);
    }

    @Test
    public void budgetNamingOneCategoryStillFillsTheJoinTable() throws Exception {
        // every caller that predates a budget covering more than one category writes the single
        // column, and the queries read the join table, so the two have to meet
        long wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        long budget = insertBudget(Schema.BudgetType.CATEGORY, food, startDate, endDate, 5000L, "EUR", new Long[] {wallet}, "tag-1");
        insertTransaction(100, new Date(), null, food, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        checkBudgetCategories(budget, new Long[] {food}, "Food", -100L);
    }

    @Test
    public void updateBudgetReplacesTheCategoriesItCovers() throws Exception {
        long wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent");
        long petrol = insertCategory("Petrol", "encoded-icon-petrol", 1, null, true, "tag-petrol");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] wallets = new Long[] {wallet};
        long budget = insertBudgetCovering(Schema.BudgetType.CATEGORY, new Long[] {food, rent}, startDate, endDate, 5000L, "EUR", wallets, "tag-1");
        insertTransaction(100, new Date(), null, food, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        insertTransaction(250, new Date(), null, rent, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        insertTransaction(700, new Date(), null, petrol, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet, null, null, null, null, null, true, true, null, null, "tag");
        checkBudgetCategories(budget, new Long[] {food, rent}, "Food, Rent", -350L);
        // food is dropped and petrol is added, so the figure follows the new pair, and the row
        // that the update flagged deleted for rent comes back instead of being written twice
        updateBudgetCovering(budget, Schema.BudgetType.CATEGORY, new Long[] {rent, petrol}, startDate, endDate, 5000L, "EUR", wallets, "tag-1");
        checkBudgetCategories(budget, new Long[] {rent, petrol}, "Petrol, Rent", -950L);
    }

    @Test(expected = SQLiteDataException.class)
    public void deleteCategoryReachedOnlyThroughTheJoinTable() throws Exception {
        // the budget row itself names food, so rent is in the join table and nowhere else
        long wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent");
        insertBudgetCovering(Schema.BudgetType.CATEGORY, new Long[] {food, rent}, new Date(), new Date(), 3000L, "EUR", new Long[] {wallet}, "tag");
        mDatabase.deleteCategory(rent);
    }

    @Test
    public void deleteBudgetTakesItsCategoriesWithIt() throws Exception {
        long wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long food = insertCategory("Food", "encoded-icon-food", 1, null, true, "tag-food");
        long rent = insertCategory("Rent", "encoded-icon-rent", 1, null, true, "tag-rent");
        long budget = insertBudgetCovering(Schema.BudgetType.CATEGORY, new Long[] {food, rent}, new Date(), new Date(), 3000L, "EUR", new Long[] {wallet}, "tag");
        mDatabase.deleteBudget(budget);
        checkCursorSize(mDatabase.getBudget(budget, null), 0);
        // with the join rows gone the categories are free again
        mDatabase.deleteCategory(rent);
        mDatabase.deleteCategory(food);
    }

    @Test(expected = SQLiteDataException.class)
    public void insertBudgetWithNoWallets() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", null, "tag-1");
    }

    @Test(expected = SQLiteDataException.class)
    public void insertBudgetWithNotConsistentWallets() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        long id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5");
        long id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds = new Long[] {id1, id3, id4};
        insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
    }

    @Test(expected = SQLiteDataException.class)
    public void insertBudgetWithInvalidWallet() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds = new Long[] {id1, id2, id1 + id2};
        insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
    }

    @Test
    public void updateBudget() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        long id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5");
        long id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds1 = new Long[] {id1, id3};
        Long[] walletIds2 = new Long[] {id1, id2, id3};
        Long[] walletIds3 = new Long[] {id4, id5};
        long id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1");
        long id8 = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2");
        long id9 = insertBudget(Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3");
        // now update budget 8 and 9
        walletIds2 = new Long[] {id5};
        walletIds3 = new Long[] {id4};
        calendar.add(Calendar.MONTH, -1);
        Date newStartDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 9);
        Date newEndDate = calendar.getTime();
        assertEquals(1, updateBudget(id7, Schema.BudgetType.CATEGORY, id6, startDate, endDate, 1000L, "EUR", walletIds1, "tag-1-edited"));
        assertEquals(1, updateBudget(id8, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "USD", walletIds2, "tag-2-edited"));
        assertEquals(1, updateBudget(id9, Schema.BudgetType.CATEGORY, id6, newStartDate, newEndDate, 8000L, "USD", walletIds3, "tag-3-edited"));
        // now check that everything has been updated correctly
        checkBudgetId(id7, Schema.BudgetType.CATEGORY, id6, startDate, endDate, 1000L, "EUR", walletIds1, "tag-1-edited", 0L);
        checkBudgetId(id8, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "USD", walletIds2, "tag-2-edited", 0L);
        checkBudgetId(id9, Schema.BudgetType.CATEGORY, id6, newStartDate, newEndDate, 8000L, "USD", walletIds3, "tag-3-edited", 0L);
    }

    @Test(expected = SQLiteDataException.class)
    public void updateBudgetWithNoWallets() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds = new Long[] {id1, id2};
        long id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
        // now update the budget with no wallets
        updateBudget(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", null, "tag-1");
    }

    @Test(expected = SQLiteDataException.class)
    public void updateBudgetWithNotConsistentWallets() {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds = new Long[] {id1, id2};
        long id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
        // now update the budget with no wallets
        walletIds = new Long[] {id1, id4};
        updateBudget(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
    }

    @Test(expected = SQLiteDataException.class)
    public void updateBudgetWithInvalidWallet() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds = new Long[] {id1, id2};
        long id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
        // now update the budget with no wallets
        walletIds = new Long[] {id1, id2, id1 + id2 + id4};
        updateBudget(id7, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds, "tag-1");
    }

    @Test
    public void deleteBudget() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertWallet("Test wallet 3", "encoded-icon-3", "EUR", "note-wallet-3", true, 1000L, true, "tag-wallet-3");
        long id4 = insertWallet("Test wallet 4", "encoded-icon-4", "USD", "note-wallet-4", true, 2300L, true, "tag-wallet-4");
        long id5 = insertWallet("Test wallet 5", "encoded-icon-5", "USD", "note-wallet-5", true, 3500L, true, "tag-wallet-5");
        long id6 = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag");
        // test insert income budget
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] walletIds1 = new Long[] {id1, id3};
        Long[] walletIds2 = new Long[] {id1, id2, id3};
        Long[] walletIds3 = new Long[] {id4, id5};
        long id7 = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", walletIds1, "tag-1");
        long id8 = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 10000L, "EUR", walletIds2, "tag-2");
        long id9 = insertBudget(Schema.BudgetType.CATEGORY, id6, startDate, endDate, 13000L, "USD", walletIds3, "tag-3");
        // check budget count
        checkCursorSize(mDatabase.getBudgets(null, null, null, null), 3);
        // now delete all the budget one by one and check the budget count, than ensure all the
        // wallets and the category are not deleted
        assertEquals(1, mDatabase.deleteBudget(id7));
        checkCursorSize(mDatabase.getBudget(id7, null), 0);
        checkCursorSize(mDatabase.getBudgets(null, null, null, null), 2);
        assertEquals(1, mDatabase.deleteBudget(id8));
        checkCursorSize(mDatabase.getBudget(id8, null), 0);
        checkCursorSize(mDatabase.getBudgets(null, null, null, null), 1);
        assertEquals(1, mDatabase.deleteBudget(id9));
        checkCursorSize(mDatabase.getBudget(id9, null), 0);
        checkCursorSize(mDatabase.getBudgets(null, null, null, null), 0);
        checkCursorSize(mDatabase.getWallet(id1, null), 1);
        checkCursorSize(mDatabase.getWallet(id2, null), 1);
        checkCursorSize(mDatabase.getWallet(id3, null), 1);
        checkCursorSize(mDatabase.getWallet(id4, null), 1);
        checkCursorSize(mDatabase.getWallet(id5, null), 1);
        checkCursorSize(mDatabase.getCategory(id6, null), 1);
    }

    @Test
    public void considerExternalTransactionsInIncomeBudget() throws Exception {
        // Setup wallets
        long wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2");
        long wallet3 = insertWallet("Test wallet 3", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-3");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] wallets = new Long[] {wallet1, wallet2};

        // Setup budget of type income
        long incomeBudget = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1");

        // Setup transfer between wallets not in the budget
        insertTransfer("desc", startDate, wallet3, wallet1, null, 4000L, 4000L, 0L, "note", null, null, true, true, null, null, "tag-1");
        insertTransferModel("desc", wallet3, wallet1, 4000L, 4000L, 0L, "note", null, null, true, true, "tag-2");

        // Transfer should not be added towards the progress of the budgets
        long expectedProgress = 4000L;
        checkBudgetId(incomeBudget, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", expectedProgress);
    }

    @Test
    public void considerExternalTransactionsInExpenseBudget() throws Exception {
        // Setup wallets
        long wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2");
        long wallet3 = insertWallet("Test wallet 3", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-3");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] wallets = new Long[] {wallet1, wallet2};

        // Setup budget of type expense
        long expenseBudget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1");

        // Setup transfer between wallets not in the budget
        insertTransfer("desc", startDate, wallet1, wallet3, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1");
        insertTransferModel("desc", wallet1, wallet3, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2");

        // Transfer should not be added towards the progress of the budgets
        long expectedProgress = 4010L;
        checkBudgetId(expenseBudget, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", expectedProgress);
    }

    @Test
    public void ignoreInternalTransactionsInBudget() throws Exception {
        // Setup wallets
        long wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        Long[] wallets = new Long[] {wallet1, wallet2};

        // Setup budget of type income
        long incomeBudget = insertBudget(Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1");

        // Setup budget of type expense
        long expenseBudget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-2");

        // Setup transfer between the wallets of the budget
        insertTransfer("desc", startDate, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1");
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2");

        // Transfer should not be added towards the progress of the budgets
        checkBudgetId(incomeBudget, Schema.BudgetType.INCOMES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", 0L);
        checkBudgetId(expenseBudget, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-2", 10L);
    }

    @Test
    public void considerTransactionsWithinBudgetDurationOnly() throws Exception {
        // Setup wallets
        long wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        Date afterBudgetDuration = calendar.getTime();
        Long[] wallets = new Long[] {wallet1, wallet2};

        // Setup budget of type expense
        long expenseBudget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1");

        // Setup transfers
        insertTransfer("desc", startDate, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-3");
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-4");
        insertTransfer("desc", afterBudgetDuration, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1");
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2");

        // Setup transactions
        long category = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag");
        insertTransaction(100, startDate, null, category, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag");
        insertTransaction(100, afterBudgetDuration, null, category, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag");

        long expectedProgress = 110L;
        checkBudgetId(expenseBudget, Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1", expectedProgress);
    }

    @Test
    public void getBudgetTransactions() throws Exception {
        // Setup wallets
        long wallet1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long wallet2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-2");
        long wallet3 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, false, "tag-wallet-3");

        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -3);
        Date startDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 6);
        Date endDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 1);
        Date afterBudgetDuration = calendar.getTime();
        Long[] wallets = new Long[] {wallet1, wallet2};

        // Setup budget
        long budget = insertBudget(Schema.BudgetType.EXPENSES, null, startDate, endDate, 5000L, "EUR", wallets, "tag-1");

        // Setup transfers
        insertTransfer("desc", startDate, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-3");
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-4");

        insertTransfer("desc", afterBudgetDuration, wallet1, wallet2, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1");
        insertTransferModel("desc", wallet1, wallet2, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2");

        insertTransfer("desc", startDate, wallet1, wallet3, wallet1, 4000L, 4000L, 10L, "note", null, null, true, true, null, null, "tag-1");
        insertTransferModel("desc", wallet1, wallet3, 4000L, 4000L, 10L, "note", null, null, true, true, "tag-2");

        // Setup transactions
        long customCategory = insertCategory("Test category 1", "encoded-icon", 1, null, true, "category-tag");
        insertTransaction(100, startDate, null, customCategory, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag");
        insertTransaction(100, afterBudgetDuration, null, customCategory, Contract.Direction.EXPENSE, Contract.TransactionType.STANDARD, wallet1, null, null, null, null, null, true, true, null, null, "tag");

        Cursor transactions = mDatabase.getBudgetTransactions(budget, null, null, null, null);
        assertEquals(4, transactions.getCount());
        assertEquals(true, transactions.moveToFirst());
        assertEquals(mContext.getString(R.string.system_category_transfer_tax), transactions.getString(transactions.getColumnIndex(Contract.Transaction.CATEGORY_NAME)));
        assertEquals(true, transactions.moveToNext());
        assertEquals(mContext.getString(R.string.system_category_transfer), transactions.getString(transactions.getColumnIndex(Contract.Transaction.CATEGORY_NAME)));
        assertEquals(true, transactions.moveToNext());
        assertEquals(mContext.getString(R.string.system_category_transfer_tax), transactions.getString(transactions.getColumnIndex(Contract.Transaction.CATEGORY_NAME)));
    }

    @Test
    public void insertSaving() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        Date exp = new Date();
        long id2 = insertSaving("desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1");
        long id3 = insertSaving("desc-2", "encoded-icon", 500L, 23000L, id1, exp, true, "note-2", "tag-2");
        checkSavingId(id2, "desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1");
        checkSavingId(id3, "desc-2", "encoded-icon", 500L, 23000L, id1, exp, true, "note-2", "tag-2");
    }

    @Test
    public void updateSaving() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        Date exp = new Date();
        long id2 = insertSaving("desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1");
        long id3 = insertSaving("desc-2", "encoded-icon", 500L, 23000L, id1, exp, true, "note-2", "tag-2");
        // now update the saving
        assertEquals(1, updateSaving(id2, "desc-1-edited", "encoded-icon-edited", 3L, 100L, id1, exp, true, "note-1-edited", "tag-1-edited"));
        assertEquals(1, updateSaving(id3, "desc-2-edited", "encoded-icon-edited", 50L, 73000L, id1, null, false, "note-2-edited", "tag-2-edited"));
        // now check that both savings have been successfully update
        checkSavingId(id2, "desc-1-edited", "encoded-icon-edited", 3L, 100L, id1, exp, true, "note-1-edited", "tag-1-edited");
        checkSavingId(id3, "desc-2-edited", "encoded-icon-edited", 50L, 73000L, id1, null, false, "note-2-edited", "tag-2-edited");
    }

    /**
     * A saving is read in the currency of its wallet and its progress is worked out from its
     * deposits and withdrawals, added up with no currency in the sum. They stay in the wallet they
     * were filed against when the saving moves, so the move is refused.
     */
    @Test
    public void movingASavingAwayFromTheCurrencyItsDepositsAreInIsRefused() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long depositCategoryId = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1");
        insertTransaction(500L, date, "deposit", depositCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.SAVING, euroWallet, null, "deposit-note", null, savingId, null, true, true, null, null, "tag-deposit");
        try {
            updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1");
            fail("The saving was moved away from the currency its deposits are in");
        } catch (SQLiteDataException e) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.getErrorCode());
        }
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1");
    }

    /**
     * And the move back is allowed, for the reason spelled out on the debt half of this,
     * movingADebtBackToTheCurrencyItsPaymentsAreInIsAllowed.
     */
    @Test
    public void movingASavingBackToTheCurrencyItsDepositsAreInIsAllowed() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long depositCategoryId = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1");
        insertTransaction(500L, date, "deposit", depositCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.SAVING, dollarWallet, null, "deposit-note", null, savingId, null, true, true, null, null, "tag-deposit");
        assertEquals(1, updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1"));
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1");
    }

    /**
     * And the wallet is still a field you can change. Without this case a guard that refused every
     * move, or one that refused every save naming a wallet, passes the refusal above just as well.
     * The debt half of it is editingTheMasterTransactionMovesTheDebt, which moves a debt between
     * two wallets that agree.
     */
    @Test
    public void movingASavingToAWalletInTheSameCurrencyGoesThrough() throws Exception {
        long walletId = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long otherWalletId = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long depositCategoryId = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT);
        Date date = DateUtils.getDateFromSQLDateString("2026-07-01");
        long savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, walletId, null, false, "note-1", "tag-1");
        insertTransaction(500L, date, "deposit", depositCategoryId, Contract.Direction.EXPENSE,
                Contract.TransactionType.SAVING, walletId, null, "deposit-note", null, savingId, null, true, true, null, null, "tag-deposit");
        assertEquals(1, updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, otherWalletId, null, false, "note-1", "tag-1"));
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, otherWalletId, null, false, "note-1", "tag-1");
    }

    /**
     * A saving with nothing filed against it strands nothing, so it can be moved anywhere. A goal
     * created against the wrong wallet and corrected straight away is the ordinary case of this.
     */
    @Test
    public void movingASavingThatStrandsNothingGoesThrough() throws Exception {
        long euroWallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long dollarWallet = insertWallet("Test wallet 2", "encoded-icon-2", "USD", "note-wallet-2", true, 2000L, false, "tag-wallet-2");
        long savingId = insertSaving("desc-1", "encoded-icon", 0L, 10000L, euroWallet, null, false, "note-1", "tag-1");
        assertEquals(1, updateSaving(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1"));
        checkSavingId(savingId, "desc-1", "encoded-icon", 0L, 10000L, dollarWallet, null, false, "note-1", "tag-1");
    }

    /**
     * A saving's progress counts only the rows that have landed, which is confirmed and dated at
     * or before this moment, and it is what the savings list adds to its start money to show.
     *
     * The saving here carries a row of every kind that rule has to leave out, which separates it
     * from the rules it is most likely to be rewritten into: dropping the date test, dropping
     * the confirmed test, narrowing it to deposits, or counting every row the saving has all
     * give something else. It is those cases and not every rule that could be written, so a
     * rewrite landing on the same figure for this saving goes through.
     *
     * This pins one sum. It says nothing about the ceiling the transaction editor holds a
     * withdrawal to, which is worked out there over the rows in date order, and the sum here
     * carries no date order, so it is not a check that the saving stays above zero at every
     * moment.
     */
    @Test
    public void savingProgressCountsOnlyTheRowsThatHaveLanded() throws Exception {
        long wallet = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 0L, false, "tag-wallet-1");
        long saving = insertSaving("desc-1", "encoded-icon", 0L, 10000L, wallet, null, false, "note-1", "tag-1");
        long deposit = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT);
        long withdraw = getSystemCategory(Contract.CategoryTag.SAVING_WITHDRAW);
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, 1);
        Date nextMonth = calendar.getTime();
        // Landed, so the progress counts them: a confirmed deposit of 1000 and a confirmed
        // withdrawal of 100, both dated now. The withdrawal is the only landed row counted
        // negatively, so without it the progress cannot tell a rule that counts every landed row
        // from one that counts only landed deposits.
        insertTransaction(1000, new Date(), null, deposit, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag");
        insertTransaction(100, new Date(), null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag");
        // Left out by the date alone, one on each side: a confirmed deposit of 400 and a
        // confirmed withdrawal of 300, both dated next month. Without them a rule with no date
        // test at all reads the same as this one.
        insertTransaction(400, nextMonth, null, deposit, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag");
        insertTransaction(300, nextMonth, null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, true, true, null, null, "tag");
        // Left out by the confirmed test alone, one on each side: an unconfirmed withdrawal of
        // 200 and an unconfirmed deposit of 500, both dated now. Without them a rule that counts
        // every row dated at or before now reads the same as this one.
        insertTransaction(200, new Date(), null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, false, true, null, null, "tag");
        insertTransaction(500, new Date(), null, deposit, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, false, true, null, null, "tag");
        // Left out by both, an unconfirmed withdrawal of 50 dated next month, so a rule that
        // drops one test still has to drop the other to reach it.
        insertTransaction(50, nextMonth, null, withdraw, Contract.Direction.INCOME, Contract.TransactionType.SAVING, wallet, null, null, null, saving, null, false, true, null, null, "tag");

        Cursor cursor = mDatabase.getSavings(null, null, null, null);
        assertEquals(true, cursor.moveToFirst());
        assertEquals(900L, cursor.getLong(cursor.getColumnIndex(Contract.Saving.PROGRESS)));
        cursor.close();
    }

    @Test
    public void deleteSaving() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        Date exp = new Date();
        long id2 = insertSaving("desc-1", "encoded-icon", 0L, 10000L, id1, null, false, "note-1", "tag-1");
        // we can now add some transactions to this saving
        long id3 = getSystemCategory(Contract.CategoryTag.SAVING_DEPOSIT);
        long id4 = getSystemCategory(Contract.CategoryTag.SAVING_WITHDRAW);
        long id5 = insertTransaction(10, new Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag");
        long id6 = insertTransaction(20, new Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag");
        long id7 = insertTransaction(30, new Date(), null, id4, Contract.Direction.INCOME, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag");
        long id8 = insertTransaction(10, new Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag");
        long id9 = insertTransaction(20, new Date(), null, id3, Contract.Direction.EXPENSE, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag");
        long id10 = insertTransaction(30, new Date(), null, id4, Contract.Direction.INCOME, Contract.TransactionType.SAVING, id1, null, null, null, id2, null, true, true, null, null, "tag");
        // now check the number of savings and transactions
        checkCursorSize(mDatabase.getSavings(null, null, null, null), 1);
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 6);
        // now delete the saving
        assertEquals(1, mDatabase.deleteSaving(id2));
        // now ensure that all the transactions and the debt have been deleted
        checkCursorSize(mDatabase.getSavings(null, null, null, null), 0);
        checkCursorSize(mDatabase.getTransactions(null, null, null, null), 0);
    }

    @Test
    public void insertEvent() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -4);
        Date startDate = calendar.getTime();
        Date endDate = new Date();
        long id1 = insertEvent("Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1");
        long id2 = insertEvent("Event 2", "encoded-icon-2", startDate, endDate, "note-2", "tag-2");
        long id3 = insertEvent("Event 3", "encoded-icon-3", startDate, endDate, "note-3", "tag-3");
        checkEventId(id1, "Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1");
        checkEventId(id2, "Event 2", "encoded-icon-2",startDate, endDate, "note-2", "tag-2");
        checkEventId(id3, "Event 3", "encoded-icon-3", startDate, endDate, "note-3", "tag-3");
    }

    @Test
    public void updateEvent() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -4);
        Date startDate = calendar.getTime();
        Date endDate = new Date();
        long id1 = insertEvent("Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1");
        long id2 = insertEvent("Event 2", "encoded-icon-2", startDate, endDate, "note-2", "tag-2");
        long id3 = insertEvent("Event 3", "encoded-icon-3", startDate, endDate, "note-3", "tag-3");
        // update event 3
        calendar.add(Calendar.MONTH, 2);
        Date newStartDate = calendar.getTime();
        calendar.add(Calendar.MONTH, 5);
        Date newEndDate = calendar.getTime();
        assertEquals(1, updateEvent(id3, "Event 3-edited", "encoded-icon-3-edited", newStartDate, newEndDate, "note-3-edited", "tag-3-edited"));
        // check
        checkEventId(id1, "Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1");
        checkEventId(id2, "Event 2", "encoded-icon-2",startDate, endDate, "note-2", "tag-2");
        checkEventId(id3, "Event 3-edited", "encoded-icon-3-edited", newStartDate, newEndDate, "note-3-edited", "tag-3-edited");
    }

    @Test
    public void deleteEvent() throws Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MONTH, -4);
        Date startDate = calendar.getTime();
        Date endDate = new Date();
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1");
        long id3 = insertEvent("Event 1", "encoded-icon-1", startDate, endDate, "note-1", "tag-1");
        // add the event to all the possible items
        long id4 = insertTransaction(2000L, endDate, "desc", id2, Contract.Direction.INCOME, 0, id1, null, "note-1", id3, null, null, true, true, null, null, "tag-1");
        long id5 = insertTransactionModel(4503L, "desc", id2, Contract.Direction.INCOME, id1, null, "note", id3, true, true, "tag");
        long id6 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 2200L, false, "tag-wallet-2");
        long id7 = insertTransfer("desc", endDate, id1, id6, null, 4000L, 4000L, 0L, "note", null, id3, true, true, null, null, "tag-1");
        long id8 = insertTransferModel("desc", id1, id6, 4000L, 4000L, 0L, "note", null, id3, true, true, "tag-8");
        assertEquals(1, mDatabase.deleteEvent(id3));
        checkTransactionId(id4, 2000L, endDate, "desc", id2, Contract.Direction.INCOME, 0, id1, null, "note-1", null, null, null, true, true, null, "tag-1");
        checkTransactionModelId(id5, 4503L, "desc", id2, Contract.Direction.INCOME, id1, null, "note", null, true, true, "tag");
        checkTransferId(id7, "desc", endDate, id1, id6, null, 4000L, 4000L, 0L, "note", null, null, true, true, null, "tag-1");
        checkTransferModelId(id8, "desc", id1, id6, 4000L, 4000L, 0L, "note", null, null, true, true, "tag-8");
    }

    @Test
    public void insertTransactionModel() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1");
        long id3 = insertTransactionModel(302, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1");
        long id4 = insertTransactionModel(275, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2");
        checkTransactionModelId(id3, 302, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1");
        checkTransactionModelId(id4, 275, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2");
    }

    @Test
    public void updateTransactionModel() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1");
        long id3 = insertTransactionModel(302, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1");
        long id4 = insertTransactionModel(275, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2");
        // update transaction model 3
        assertEquals(1, updateTransactionModel(id3, 302, "desc-1-edited", id2, Contract.Direction.EXPENSE, id1, null, "note-1-edited", null, false, true, "tag-1-edited"));
        // check transaction models
        checkTransactionModelId(id3, 302, "desc-1-edited", id2, Contract.Direction.EXPENSE, id1, null, "note-1-edited", null, false, true, "tag-1-edited");
        checkTransactionModelId(id4, 275, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2");
    }

    @Test
    public void deleteTransactionModel() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1");
        long id3 = insertTransactionModel(302, "desc-1", id2, Contract.Direction.INCOME, id1, null, "note-1", null, true, true, "tag-1");
        long id4 = insertTransactionModel(275, "desc-2", id2, Contract.Direction.INCOME, id1, null, "note-2", null, false, false, "tag-2");
        // check the current count of transaction models
        checkCursorSize(mDatabase.getTransactionModels(null, null, null, null), 2);
        // now delete the two transaction models
        assertEquals(1, mDatabase.deleteTransactionModel(id3));
        assertEquals(1, mDatabase.deleteTransactionModel(id4));
        // recheck the current count of transaction models
        checkCursorSize(mDatabase.getTransactionModels(null, null, null, null), 0);
    }

    @Test
    public void insertTransferModel() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertTransferModel("desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1");
        long id4 = insertTransferModel("desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2");
        checkTransferModelId(id3, "desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1");
        checkTransferModelId(id4, "desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2");
    }

    @Test
    public void updateTransferModel() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertTransferModel("desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1");
        long id4 = insertTransferModel("desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2");
        // now update the transfer model 3
        assertEquals(1, updateTransferModel(id3, "desc-1-edited", id1, id2, 20L, 20L, 3L, "note-1-edited", null, null, false, true, "tag-1-edited"));
        // check the transfer models
        checkTransferModelId(id3, "desc-1-edited", id1, id2, 20L, 20L, 3L, "note-1-edited", null, null, false, true, "tag-1-edited");
        checkTransferModelId(id4, "desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2");
    }

    @Test
    public void deleteTransferModel() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertTransferModel("desc-1", id1, id2, 10L, 10L, 0L, "note-1", null, null, true, true, "tag-1");
        long id4 = insertTransferModel("desc-2", id1, id2, 5L, 5L, 10L, "note-2", null, null, true, false, "tag-2");
        // check the current count of transaction models
        checkCursorSize(mDatabase.getTransferModels(null, null, null, null), 2);
        // now delete the two transaction models
        assertEquals(1, mDatabase.deleteTransferModel(id3));
        assertEquals(1, mDatabase.deleteTransferModel(id4));
        // recheck the current count of transaction models
        checkCursorSize(mDatabase.getTransferModels(null, null, null, null), 0);
    }

    @Test
    public void insertPlace() throws Exception {
        long id1 = insertPlace("place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1");
        long id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        checkPlaceId(id1, "place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1");
        checkPlaceId(id2, "place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
    }

    @Test
    public void updatePlace() throws Exception {
        long id1 = insertPlace("place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1");
        long id2 = insertPlace("place-2", "encoded-icon-2", "fake-address-2", 7.3467, 8.364, "tag-2");
        // now update place 2
        assertEquals(1, updatePlace(id2, "place-2-edited", "encoded-icon-2-edited", "fake-address-2-edited", 2.354, 1.783, "tag-2-edited"));
        // check places
        checkPlaceId(id1, "place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1");
        checkPlaceId(id2, "place-2-edited", "encoded-icon-2-edited", "fake-address-2-edited", 2.354, 1.783, "tag-2-edited");
    }

    @Test
    public void deletePlace() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1");
        long id4 = insertPlace("place-1", "encoded-icon-1", "fake-address-1", null, null, "tag-1");
        // now create one element for every item that can be linked to this place
        Date date = new Date();
        long id5 = insertDebt(Contract.DebtType.DEBT.getValue(), "icon", "desc", date, null, id1, null, id4, 10000L, false, null, null, false);
        long id6 = insertTransaction(10, date, "desc", id3, Contract.Direction.INCOME, 0, id1, id4, null, null, null, null, true, true, null, null, null);
        long id7 = insertTransfer("desc", date, id1, id2, null, 10, 10, 0, null, id4, null, true, true, null, null, null);
        long id8 = insertTransactionModel(10, "desc", id3, Contract.Direction.INCOME, id1, id4, null, null, true, true, null);
        long id9 = insertTransferModel("desc", id1, id2, 10, 10, 0, null, id4, null, true, true, null);
        // now delete the place
        assertEquals(1, mDatabase.deletePlace(id4));
        // check if items has no place as expected
        checkCursorSize(mDatabase.getPlaces(null, null, null, null), 0);
        checkDebtId(id5, Contract.DebtType.DEBT.getValue(), "icon", "desc", date, null, id1, null, null, 10000L, false, null, null);
        checkTransactionId(id6, 10, date, "desc", id3, Contract.Direction.INCOME, 0, id1, null, null, null, null, null, true, true, null, null);
        checkTransferId(id7, "desc", date, id1, id2, null, 10, 10, 0, null, null, null, true, true, null, null);
        checkTransactionModelId(id8, 10, "desc", id3, Contract.Direction.INCOME, id1, null, null, null, true, true, null);
        checkTransferModelId(id9, "desc", id1, id2, 10, 10, 0, null, null, null, true, true, null);
    }

    @Test
    public void insertPerson() throws Exception {
        long id1 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id2 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        checkPersonId(id1, "person-1", "encoded-icon-1", "note-1", "tag-1");
        checkPersonId(id2, "person-2", "encoded-icon-2", "note-2", "tag-2");
    }

    @Test
    public void updatePerson() throws Exception {
        long id1 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id2 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        // now update person 2
        assertEquals(1, updatePerson(id2, "person-2-edited", "encoded-icon-2-edited", "note-2-edited", "tag-2-edited"));
        // check people
        checkPersonId(id1, "person-1", "encoded-icon-1", "note-1", "tag-1");
        checkPersonId(id2, "person-2-edited", "encoded-icon-2-edited", "note-2-edited", "tag-2-edited");
    }

    @Test
    public void deletePerson() throws Exception {
        long id1 = insertWallet("Test wallet 1", "encoded-icon-1", "EUR", "note-wallet-1", true, 2000L, false, "tag-wallet-1");
        long id2 = insertWallet("Test wallet 2", "encoded-icon-2", "EUR", "note-wallet-2", true, 3000L, true, "tag-wallet-2");
        long id3 = insertCategory("Test category 1", "encoded-icon-1", 0, null, true, "tag-1");
        long id4 = insertPerson("person-1", "encoded-icon-1", "note-1", "tag-1");
        long id5 = insertPerson("person-2", "encoded-icon-2", "note-2", "tag-2");
        long id6 = insertPerson("person-3", "encoded-icon-3", "note-3", "tag-3");
        // now attach the person 5 to every item that supports it
        Date date = new Date();
        long id7 = insertDebt(Contract.DebtType.DEBT.getValue(), "icon", "desc", date, null, id1, null, null, 10000L, false, new Long[] {id5, id6}, null, false);
        long id8 = insertTransaction(10, date, "desc", id3, Contract.Direction.INCOME, 0, id1, null, null, null, null, null, true, true, new Long[] {id4, id5}, null, null);
        long id9 = insertTransfer("desc", date, id1, id2, null, 10, 10, 0, null, null, null, true, true, new Long[] {id5}, null, null);
        // now delete the person 5
        assertEquals(1, mDatabase.deletePerson(id5));
        checkCursorSize(mDatabase.getPeople(null, null, null, null), 2);
        // now check that every item has no more person 5 in list
        checkDebtId(id7, Contract.DebtType.DEBT.getValue(), "icon", "desc", date, null, id1, null, null, 10000L, false, new Long[] {id6}, null);
        checkTransactionId(id8, 10, date, "desc", id3, Contract.Direction.INCOME, 0, id1, null, null, null, null, null, true, true, new Long[] {id4}, null);
        checkTransferId(id9, "desc", date, id1, id2, null, 10, 10, 0, null, null, null, true, true, null, null);
        // Every accessor the checks above read through filters on deleted = 0, so a row flagged
        // as deleted and a row that is gone look identical through all of them. Ask the tables
        // for the flag instead. The fixture gave person 5 a row in each of the four tables
        // asserted below.
        //
        // The five deletes in deletePerson used to share one boolean and could only go soft
        // together. That boolean is gone and nothing couples them now, so any subset of the five
        // can regress on its own, and this sees only the subsets that include the person delete,
        // because a real delete of the person row cascades every flagged link row away.
        //
        // The person table is asserted last on purpose. Put it first and a maintainer fixing the
        // person delete brings the cascade back, which hides every link delete still writing a
        // flag and lets the test go green with four of the five wrong. Last, each fix uncovers
        // the next failure.
        //
        // EventPeople is the one other table deletePerson writes, and it is not asserted here
        // because the fixture never links person 5 to an event, so the count would come back
        // zero whichever branch ran.
        assertRowCount(Schema.DebtPeople.TABLE, Schema.DebtPeople.DELETED + " = 1", 0);
        assertRowCount(Schema.TransactionPeople.TABLE, Schema.TransactionPeople.DELETED + " = 1", 0);
        assertRowCount(Schema.TransferPeople.TABLE, Schema.TransferPeople.DELETED + " = 1", 0);
        assertRowCount(Schema.Person.TABLE, Schema.Person.DELETED + " = 1", 0);
    }

    @Test
    public void insertAttachment() throws Exception {
        long id1 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id2 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        checkAttachmentId(id1, "path1", "name-1", "mime-type-1", 90L, "tag-1");
        checkAttachmentId(id2, "path2", "name-2", "mime-type-2", 4560L, "tag-2");
    }

    @Test
    public void updateAttachment() throws Exception {
        long id1 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id2 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        // now update attachment 2
        assertEquals(1, updateAttachment(id2, "path2-edited", "name-2-edited", "mime-type-2-edited", 3647L, "tag-2-edited"));
        // check attachments
        checkAttachmentId(id1, "path1", "name-1", "mime-type-1", 90L, "tag-1");
        checkAttachmentId(id2, "path2-edited", "name-2-edited", "mime-type-2-edited", 3647L, "tag-2-edited");
    }

    @Test
    public void deleteAttachment() throws Exception {
        long id1 = insertAttachment("path1", "name-1", "mime-type-1", 90L, "tag-1");
        long id2 = insertAttachment("path2", "name-2", "mime-type-2", 4560L, "tag-2");
        checkCursorSize(mDatabase.getAttachments(null, null, null, null), 2);
        assertEquals(1, mDatabase.deleteAttachment(id1));
        checkCursorSize(mDatabase.getAttachments(null, null, null, null), 1);
        assertEquals(1, mDatabase.deleteAttachment(id2));
        checkCursorSize(mDatabase.getAttachments(null, null, null, null), 0);
    }

    @Test
    public void reportFilterDropsAHiddenCategoryAndAHiddenParent() throws Exception {
        long wallet = insertWallet("Wallet", "icon", "EUR", null, true, 0L, false, "tag-wallet");
        long shown = insertCategory("Shown", "icon", 0, null, true, "tag-shown");
        long hidden = insertCategory("Hidden", "icon", 0, null, false, "tag-hidden");
        long shownChild = insertCategory("Shown child", "icon", 0, shown, true, "tag-shown-child");
        long hiddenChild = insertCategory("Hidden child", "icon", 0, shown, false, "tag-hidden-child");
        long childOfHidden = insertCategory("Child of hidden", "icon", 0, hidden, true, "tag-child-of-hidden");
        Date date = new Date();
        insertTransaction(100L, date, "on a shown category", shown, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-1");
        insertTransaction(100L, date, "on a hidden category", hidden, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-2");
        insertTransaction(100L, date, "on a shown child", shownChild, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-3");
        insertTransaction(100L, date, "on a hidden child", hiddenChild, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-4");
        insertTransaction(100L, date, "on a shown child of a hidden category", childOfHidden, Contract.Direction.EXPENSE, 0, wallet, null, null, null, null, null, true, true, null, null, "tag-5");
        // the reports keep the two rows whose own category and parent are both shown
        String[] projection = new String[] {Contract.Transaction.ID, Contract.Transaction.CATEGORY_ID};
        checkCursorSize(mDatabase.getTransactions(projection, Contract.Transaction.REPORT_FILTER, null, null), 2);
        // reading the own flag alone keeps the child of a hidden category, which is the third row
        checkCursorSize(mDatabase.getTransactions(projection, Contract.Transaction.CATEGORY_SHOW_REPORT + " = 1", null, null), 3);
        checkCursorSize(mDatabase.getTransactions(projection, null, null, null), 5);
    }

}
