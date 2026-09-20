package com.oriondev.moneywallet.storage.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * What {@link SQLDatabase#updateCategoryRule(long, ContentValues)} leaves behind when it is given
 * only part of a rule. The sort screen writes the index on its own, and the pattern and the
 * category columns are both NOT NULL, so this runs on real SQLite in the JVM the way
 * {@link CategoryRuleMatchTest} runs the lookup.
 */
@RunWith(RobolectricTestRunner.class)
public class CategoryRuleUpdateTest {

    private static final String NAME = "rule-updates.db";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final long EDIT = 1565000000000L;

    private static final long FOOD = 900L;

    private static final long PETROL = 901L;

    private SQLDatabase mDatabase;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = new SQLDatabase(context, NAME);
        SQLiteDatabase db = mDatabase.getWritableDatabase();
        insertCategory(db, FOOD, "Food", "category-food");
        insertCategory(db, PETROL, "Petrol", "category-petrol");
    }

    private void insertCategory(SQLiteDatabase db, long id, String name, String uuid) {
        db.execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                        "category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, name, ICON, Schema.CategoryType.EXPENSE, uuid, EDIT});
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    /**
     * The sort screen renumbers by sending one index only update per rule, one call per row, so
     * two rules go in here and each is given a new index in turn. Every call has to reach its own
     * row and leave the other rule's pattern, category and index alone.
     */
    @Test
    public void anUpdateCarryingOnlyTheIndexLeavesThePatternAndTheCategoryAlone() {
        ContentValues insert = new ContentValues();
        insert.put(Contract.CategoryRule.PATTERN, "tesco");
        insert.put(Contract.CategoryRule.CATEGORY_ID, FOOD);
        insert.put(Contract.CategoryRule.INDEX, 3);
        long ruleId = mDatabase.insertCategoryRule(insert);
        assertTrue(ruleId > 0);

        ContentValues other = new ContentValues();
        other.put(Contract.CategoryRule.PATTERN, "aldi");
        other.put(Contract.CategoryRule.CATEGORY_ID, PETROL);
        other.put(Contract.CategoryRule.INDEX, 5);
        long otherId = mDatabase.insertCategoryRule(other);
        assertTrue(otherId > 0);

        ContentValues update = new ContentValues();
        update.put(Contract.CategoryRule.INDEX, 2);
        assertEquals(1, mDatabase.updateCategoryRule(ruleId, update));

        ContentValues otherUpdate = new ContentValues();
        otherUpdate.put(Contract.CategoryRule.INDEX, 1);
        assertEquals(1, mDatabase.updateCategoryRule(otherId, otherUpdate));

        String[] projection = new String[] {
                Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_ID,
                Contract.CategoryRule.INDEX
        };
        Cursor cursor = mDatabase.getCategoryRule(ruleId, projection);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("tesco", cursor.getString(cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)));
            assertEquals(FOOD, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)));
            assertEquals(2, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)));
        } finally {
            cursor.close();
        }

        Cursor otherCursor = mDatabase.getCategoryRule(otherId, projection);
        assertNotNull(otherCursor);
        try {
            assertTrue(otherCursor.moveToFirst());
            assertEquals("aldi", otherCursor.getString(otherCursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)));
            assertEquals(PETROL, otherCursor.getLong(otherCursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)));
            assertEquals(1, otherCursor.getInt(otherCursor.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)));
        } finally {
            otherCursor.close();
        }
    }

    /**
     * The other half of the same method is the one the rule editor uses. It sends the pattern and
     * the category together and never sends an index, so both have to reach the row and the index
     * has to survive. The row count the method returns cannot stand in for this, because the last
     * edit stamp is written whatever else is skipped, so an update that wrote nothing the user
     * asked for still comes back as one row.
     *
     * A second rule sits beside it and is never updated. Without one the table holds a single row
     * and an update that names no row at all reads the same as one that names the right row.
     */
    @Test
    public void anUpdateCarryingThePatternAndTheCategoryWritesBothAndLeavesTheIndex() {
        ContentValues insert = new ContentValues();
        insert.put(Contract.CategoryRule.PATTERN, "tesco");
        insert.put(Contract.CategoryRule.CATEGORY_ID, FOOD);
        insert.put(Contract.CategoryRule.INDEX, 3);
        long ruleId = mDatabase.insertCategoryRule(insert);
        assertTrue(ruleId > 0);

        ContentValues other = new ContentValues();
        other.put(Contract.CategoryRule.PATTERN, "aldi");
        other.put(Contract.CategoryRule.CATEGORY_ID, FOOD);
        other.put(Contract.CategoryRule.INDEX, 5);
        long otherId = mDatabase.insertCategoryRule(other);
        assertTrue(otherId > 0);

        ContentValues update = new ContentValues();
        update.put(Contract.CategoryRule.PATTERN, "shell");
        update.put(Contract.CategoryRule.CATEGORY_ID, PETROL);
        assertEquals(1, mDatabase.updateCategoryRule(ruleId, update));

        String[] projection = new String[] {
                Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_ID,
                Contract.CategoryRule.INDEX
        };
        Cursor cursor = mDatabase.getCategoryRule(ruleId, projection);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("shell", cursor.getString(cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)));
            assertEquals(PETROL, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)));
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)));
        } finally {
            cursor.close();
        }

        Cursor otherCursor = mDatabase.getCategoryRule(otherId, projection);
        assertNotNull(otherCursor);
        try {
            assertTrue(otherCursor.moveToFirst());
            assertEquals("aldi", otherCursor.getString(otherCursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)));
            assertEquals(FOOD, otherCursor.getLong(otherCursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)));
            assertEquals(5, otherCursor.getInt(otherCursor.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)));
        } finally {
            otherCursor.close();
        }
    }

    /**
     * The editor is the only screen that sends the pattern and it always sends the category with
     * it, so the two are never separated in the app. They are separate conditions in the method,
     * and a pattern only update has to leave the category and the index where they are and store
     * the pattern the way the user typed it, capitals and all. An id that names no row has to come
     * back as zero, because the count this method returns is what the content provider uses to
     * decide whether anything changed.
     */
    @Test
    public void anUpdateCarryingOnlyThePatternLeavesTheCategoryAndKeepsTheCase() {
        ContentValues insert = new ContentValues();
        insert.put(Contract.CategoryRule.PATTERN, "tesco");
        insert.put(Contract.CategoryRule.CATEGORY_ID, FOOD);
        insert.put(Contract.CategoryRule.INDEX, 3);
        long ruleId = mDatabase.insertCategoryRule(insert);
        assertTrue(ruleId > 0);

        ContentValues update = new ContentValues();
        update.put(Contract.CategoryRule.PATTERN, "Shell Express");
        assertEquals(1, mDatabase.updateCategoryRule(ruleId, update));
        assertEquals(0, mDatabase.updateCategoryRule(ruleId + 1000, update));

        String[] projection = new String[] {
                Contract.CategoryRule.PATTERN,
                Contract.CategoryRule.CATEGORY_ID,
                Contract.CategoryRule.INDEX
        };
        Cursor cursor = mDatabase.getCategoryRule(ruleId, projection);
        assertNotNull(cursor);
        try {
            assertTrue(cursor.moveToFirst());
            assertEquals("Shell Express", cursor.getString(cursor.getColumnIndexOrThrow(Contract.CategoryRule.PATTERN)));
            assertEquals(FOOD, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID)));
            assertEquals(3, cursor.getInt(cursor.getColumnIndexOrThrow(Contract.CategoryRule.INDEX)));
        } finally {
            cursor.close();
        }
    }
}
