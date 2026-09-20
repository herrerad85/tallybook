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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What {@link SQLDatabase#getCategoryRuleMatch(String)} answers for a description. It is the one
 * read the automatic category fill makes, and the only other cases standing behind it need a
 * device, so these run it on real SQLite in the JVM the way
 * {@link SQLDatabaseUpgradeTest} runs the migration.
 *
 * Every rule here is written through {@link SQLDatabase#insertCategoryRule(ContentValues)}, the
 * path the editor writes by, except the one flagged deleted, which nothing in the app writes and
 * only a restored backup can carry, so the test writes it straight into the table.
 */
@RunWith(RobolectricTestRunner.class)
public class CategoryRuleMatchTest {

    private static final String NAME = "rules.db";

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
        insertCategory(db, FOOD, "Food");
        insertCategory(db, PETROL, "Petrol");
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void aDescriptionHoldingThePatternAnswersWithThatRulesCategory() {
        insertRule("tesco", FOOD, 0);
        assertEquals(Long.valueOf(FOOD), match("weekly shop at tesco extra"));
    }

    /** A lower case rule against a description the user capitalised. */
    @Test
    public void aLowerCasePatternMatchesACapitalisedDescription() {
        insertRule("tesco", FOOD, 0);
        assertEquals(Long.valueOf(FOOD), match("TESCO Extra"));
    }

    /** The other direction, a pattern the user capitalised against a lower case description. */
    @Test
    public void aCapitalisedPatternMatchesALowerCaseDescription() {
        insertRule("TeSCo", FOOD, 0);
        assertEquals(Long.valueOf(FOOD), match("weekly shop at tesco extra"));
    }

    /**
     * A percent and an underscore are wildcards under LIKE, which is why the lookup is written
     * with instr. They stand for themselves, so a pattern carrying one matches only a
     * description carrying the same character in the same place.
     */
    @Test
    public void aPercentInThePatternIsNotAWildcard() {
        insertRule("100% juice", FOOD, 0);
        assertNull(match("100 juice"));
        assertEquals(Long.valueOf(FOOD), match("Bought 100% juice today"));
    }

    @Test
    public void anUnderscoreInThePatternIsNotAWildcard() {
        insertRule("ju_ce", FOOD, 0);
        assertNull(match("orange juice"));
        assertEquals(Long.valueOf(FOOD), match("orange ju_ce"));
    }

    /**
     * Both patterns are in the description and only one answer comes back. The rule with the
     * higher index is written first, so the order the rows went in cannot be what decides it.
     */
    @Test
    public void theLowestIndexWinsWhenTwoRulesMatch() {
        insertRule("petrol", PETROL, 7);
        insertRule("tesco", FOOD, 3);
        assertEquals(Long.valueOf(FOOD), match("tesco petrol station"));
    }

    /**
     * Two rules sit on the same index and both patterns are in the description. Nothing in the
     * schema stops a repeated index and a restore writes the column straight from the file, so
     * the lookup answers with the older of the two instead of leaving the choice to SQLite.
     * Petrol goes in first and so holds the lower id.
     */
    @Test
    public void theOlderRuleWinsWhenTwoRulesShareAnIndex() {
        insertRule("petrol", PETROL, 4);
        insertRule("tesco", FOOD, 4);
        assertEquals(Long.valueOf(PETROL), match("tesco petrol station"));
    }

    @Test
    public void aDeletedRuleNeverMatches() {
        insertDeletedRule("tesco", FOOD, 0);
        assertNull(match("weekly shop at tesco extra"));
    }

    /**
     * A description no rule covers is the common case, since the rules are a short list and the
     * lookup runs on every description the user leaves.
     */
    @Test
    public void aDescriptionNoRuleCoversComesBackEmpty() {
        insertRule("tesco", FOOD, 0);
        Cursor cursor = mDatabase.getCategoryRuleMatch("corner shop");
        assertNotNull(cursor);
        try {
            assertEquals(0, cursor.getCount());
        } finally {
            cursor.close();
        }
    }

    /** An empty table is the state every install starts in, and it answers the same way. */
    @Test
    public void noRulesAtAllComesBackEmpty() {
        assertNull(match("weekly shop at tesco extra"));
    }

    /**
     * The category a description is filed under, read the way the content provider reads it.
     *
     * @param description text to match the patterns against.
     * @return the category id, or null when no rule matches.
     */
    private Long match(String description) {
        Cursor cursor = mDatabase.getCategoryRuleMatch(description);
        assertNotNull(cursor);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            return cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID));
        } finally {
            cursor.close();
        }
    }

    private void insertRule(String pattern, long category, int index) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.CategoryRule.PATTERN, pattern);
        contentValues.put(Contract.CategoryRule.CATEGORY_ID, category);
        contentValues.put(Contract.CategoryRule.INDEX, index);
        assertTrue(mDatabase.insertCategoryRule(contentValues) > 0);
    }

    private void insertDeletedRule(String pattern, long category, int index) {
        mDatabase.getWritableDatabase().execSQL("INSERT INTO category_rules (rule_pattern, " +
                        "rule_category, rule_index, uuid, last_edit, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, 1)",
                new Object[] {pattern, category, index, "rule-" + pattern, EDIT});
    }

    private void insertCategory(SQLiteDatabase db, long id, String name) {
        db.execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                        "category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, name, ICON, Schema.CategoryType.EXPENSE, "category-" + id, EDIT});
    }
}
