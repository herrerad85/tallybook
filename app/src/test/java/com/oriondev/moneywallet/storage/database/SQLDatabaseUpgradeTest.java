package com.oriondev.moneywallet.storage.database;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteConstraintException;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * What {@link SQLDatabase#onUpgrade} does to a database an older release left behind. Nothing else
 * runs it, so every case here is the only thing standing behind a step of it.
 *
 * Three cases are a database that has never held the join table, at each of the versions a release
 * stamped without it: 2, which upstream stamped up to 4.0.4.1, 3, which it stamped from then on and
 * so did every release of this app up to 1.5.0, and 4, which 1.6.0 stamped. A version 1 database
 * takes the same path as a version 2 one, the step between them having been withdrawn. Their tables
 * are written out here instead of taken from {@link Schema}, because Schema is what a fresh install
 * gets today and a migration reads what a shipped release wrote. The columns the later two arrive
 * with are the exception, added by the same statements the migration would have added them with.
 *
 * A fourth is a database this app wrote with the version put back to 3, which is what installing a
 * release older than the one that wrote it leaves, since onDowngrade does nothing and the helper
 * stamps the version anyway. That one uses Schema, the schema being the one it wrote. A fifth is a
 * database the migration cannot convert at all.
 *
 * Four cases after those cover the step that moves nine currencies to two decimals, all on a
 * database this app wrote at version 5: the money it carries, the same database arriving a second
 * time, a currency the owner had already set above zero, and the asset file agreeing with the list
 * the step walks.
 *
 * No budget here is flagged deleted, because none can be. The delete is a real delete on every
 * build ever shipped, the soft delete beside it was gated on a constant that has read false since
 * the initial commit and came out in 06b9009, and a backup carries no deleted budget either.
 */
@RunWith(RobolectricTestRunner.class)
public class SQLDatabaseUpgradeTest {

    private static final String CREATE_TABLE_WALLET_BEFORE_3 = "CREATE TABLE wallets (" +
            "wallet_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "wallet_name TEXT NOT NULL, " +
            "wallet_icon TEXT, " +
            "wallet_currency TEXT NOT NULL, " +
            "wallet_start_money INTEGER NOT NULL DEFAULT 0, " +
            "wallet_count_in_total INTEGER NOT NULL DEFAULT 1, " +
            "wallet_note TEXT, " +
            "wallet_archived INTEGER NOT NULL DEFAULT 0, " +
            "wallet_tag TEXT, " +
            "uuid TEXT NOT NULL UNIQUE, " +
            "last_edit INTEGER NOT NULL, " +
            "deleted INTEGER NOT NULL DEFAULT 0)";

    private static final String CREATE_TABLE_CATEGORY_BEFORE_3 = "CREATE TABLE categories (" +
            "category_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "category_name TEXT NOT NULL, " +
            "category_icon TEXT NOT NULL, " +
            "category_type INTEGER NOT NULL, " +
            "category_parent INTEGER, " +
            "category_show_report INTEGER NOT NULL DEFAULT 1, " +
            "category_tag TEXT, " +
            "uuid TEXT NOT NULL UNIQUE, " +
            "last_edit INTEGER NOT NULL, " +
            "deleted INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY (category_parent) REFERENCES categories(category_id) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)";

    private static final String CREATE_TABLE_BUDGET_BEFORE_4 = "CREATE TABLE budgets (" +
            "budget_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "budget_type INTEGER NOT NULL, " +
            "budget_category INTEGER, " +
            "budget_start_date DATETIME NOT NULL, " +
            "budget_end_date DATETIME NOT NULL, " +
            "budget_money INTEGER NOT NULL, " +
            "budget_currency TEXT NOT NULL, " +
            "budget_tag TEXT, " +
            "uuid TEXT NOT NULL UNIQUE, " +
            "last_edit INTEGER NOT NULL, " +
            "deleted INTEGER NOT NULL DEFAULT 0, " +
            "FOREIGN KEY (budget_category) REFERENCES categories(category_id) " +
            "ON UPDATE NO ACTION ON DELETE CASCADE)";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    /** Every seeded row is stamped in 2019, so a row the migration writes or restamps is told apart by its own value. */
    private static final long EDIT = 1565000000000L;

    private static final String NAME = "upgrade.db";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
    }

    /**
     * A database from before any of the three steps, carried forward by the release installed over
     * it. All three have to land in that one launch, because the second launch sees the version
     * already stamped and skips every one of them. A version 1 database arrives here too, taking
     * the same path.
     */
    @Test
    public void aDatabaseFromBeforeAllThreeStepsComesAllTheWayForwardInOneLaunch() {
        upgradeFromBeforeTheJoinTable(2);
    }

    /**
     * What a user of any release from upstream 4.0.5 to this app's 1.5.0 is holding. The columns
     * of the first step are there, because the release that stamped this version is the one that
     * added them, and the other two steps have still to run.
     */
    @Test
    public void aDatabaseWhereOnlyTheFirstStepHasEverRun() {
        upgradeFromBeforeTheJoinTable(3);
    }

    /**
     * What a 1.6.0 user is holding, and the upgrade this migration runs most often, since 1.6.0 is
     * the release before the join table. Only the last step has still to run, so this is the one
     * case where the table has to be created by a launch that skips both ALTER blocks.
     */
    @Test
    public void aDatabaseWhereEverythingButTheJoinTableHasRun() {
        upgradeFromBeforeTheJoinTable(4);
    }

    /**
     * A database no release has written the join table into, at each version one of them stamped.
     * They differ by the columns their own upgrade had already added, and by nothing else the
     * migration reads.
     */
    private void upgradeFromBeforeTheJoinTable(int version) {
        SQLiteDatabase old = mContext.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        old.execSQL(CREATE_TABLE_WALLET_BEFORE_3);
        old.execSQL(CREATE_TABLE_CATEGORY_BEFORE_3);
        old.execSQL(CREATE_TABLE_BUDGET_BEFORE_4);
        // every release has shipped a currency table, and the decimals step reads it. The
        // declaration has not changed since the initial commit, so Schema is the one a
        // release of any of these versions wrote
        old.execSQL(Schema.CREATE_TABLE_CURRENCY);
        // each release added its own columns by its own upgrade, so they are added here the same
        // way instead of being declared, and the fixture stays the tables that release shipped
        if (version >= 3) {
            old.execSQL(Schema.CREATE_WALLET_INDEX_COLUMN);
            old.execSQL(Schema.CREATE_CATEGORY_INDEX_COLUMN);
        }
        if (version >= 4) {
            old.execSQL(Schema.CREATE_BUDGET_RULE_COLUMN);
            old.execSQL(Schema.CREATE_BUDGET_RULE_START_COLUMN);
        }
        insertWallet(old, 1, "Cash");
        insertCategory(old, 1, "Food");
        insertCategory(old, 2, "Rent");
        insertCategory(old, 3, "Fun");
        insertBudget(old, 1, Schema.BudgetType.CATEGORY, 1L);
        // two budgets can cover one category, and each needs its own row
        insertBudget(old, 5, Schema.BudgetType.CATEGORY, 1L);
        // the ordinary budget, the one most databases are full of. It carries no category at all,
        // and the fill has to leave it alone, since the join table refuses a row without one
        insertBudget(old, 4, Schema.BudgetType.EXPENSES, null);
        // no release has ever written these two, since every editor clears the category when the
        // type is changed to one that does not cover categories. They are here because the two
        // clearing steps of migration 5 are written to defend against exactly this, and nothing
        // else in the file reaches either one. One of each type, since both steps are written
        // against the type that does cover categories and not against these
        insertBudget(old, 2, Schema.BudgetType.EXPENSES, 2L);
        insertBudget(old, 3, Schema.BudgetType.INCOMES, 3L);
        old.setVersion(version);
        old.close();

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        // written out so that the next version bump has to come through here
        assertEquals(8, upgraded.getVersion());
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"));
        assertEquals(0L, count(upgraded, "pragma_foreign_key_check", null));
        assertSchemaMatchesAFreshInstall(upgraded);

        // the rules table arrives with the upgrade and starts empty, no column and no preference
        // held these before it
        assertEquals(0L, count(upgraded, Schema.CategoryRule.TABLE, null));

        // the rows that were already there are carried over, the index columns come out on them at
        // the default the app sorts and reads by, and the two budget rule columns come out empty,
        // whichever of those this run added and whichever the fixture arrived with
        assertEquals(1L, count(upgraded, Schema.Wallet.TABLE, null));
        assertEquals("the upgrade neither seeds nor drops categories",
                3L, count(upgraded, Schema.Category.TABLE, null));
        assertEquals(5L, count(upgraded, Schema.Budget.TABLE, null));
        assertEquals(Long.valueOf(0L),
                number(upgraded, "SELECT wallet_index FROM wallets WHERE wallet_id = 1"));
        assertEquals(Long.valueOf(0L),
                number(upgraded, "SELECT category_index FROM categories WHERE category_id = 1"));
        assertNull(number(upgraded, "SELECT budget_rule FROM budgets WHERE budget_id = 1"));
        assertNull(number(upgraded, "SELECT budget_rule_start FROM budgets WHERE budget_id = 1"));

        // both budgets that covered a category come out covering it through the table, each with
        // its own row, stamped in milliseconds like every other row this app writes
        assertEquals(4L, count(upgraded, Schema.BudgetCategory.TABLE, null));
        assertEquals(2L, count(upgraded, Schema.BudgetCategory.TABLE, "_category = 1 AND deleted = 0"));
        assertEquals(Long.valueOf(1L), categoryColumnOf(upgraded, 1));
        assertStampedNow(upgraded, "SELECT last_edit FROM budget_categories WHERE _budget = 1");

        // the budget with no category is left with none, and neither of those is asserted, since
        // a fill that tried to give it one throws on the join table and takes the whole upgrade
        // with it before anything here is read

        // the two of other types keep neither. Their rows are flagged and not removed, which is
        // what an ordinary edit does to the same rows
        assertEquals(1L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 2 AND deleted = 1"));
        assertEquals(1L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 3 AND deleted = 1"));
        assertNull(categoryColumnOf(upgraded, 2));
        assertNull(categoryColumnOf(upgraded, 3));
        // and none of them is restamped, whether the migration rewrote its column or not. That
        // value is the age a backup carries for the row
        assertEquals(Long.valueOf(EDIT),
                number(upgraded, "SELECT last_edit FROM budgets WHERE budget_id = 2"));
        assertEquals(Long.valueOf(EDIT),
                number(upgraded, "SELECT last_edit FROM budgets WHERE budget_id = 1"));

        // the table the upgrade just built has to take its rows with whichever end of them goes
        // first. Category 2 is what proves the category end, since the budget holding that row
        // came out of the upgrade with a null column, so no other key can carry the row away
        upgraded.execSQL("DELETE FROM categories WHERE category_id = 2");
        assertEquals(0L, count(upgraded, Schema.BudgetCategory.TABLE, "_category = 2"));
        upgraded.execSQL("DELETE FROM budgets WHERE budget_id = 1");
        assertEquals(0L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 1"));
        helper.close();
    }

    /**
     * A database the migration cannot convert. The launch has to fail and leave the version where
     * it was, because SQLiteOpenHelper stamps the new one the moment onUpgrade returns without
     * throwing, and a database stamped as converted is never handed to the migration again. So a
     * failure that is caught and carried on from is worse than the crash it replaces, it is
     * permanent.
     */
    @Test
    public void aFailedUpgradeLeavesTheVersionWhereItWas() {
        SQLiteDatabase old = mContext.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        old.execSQL(CREATE_TABLE_WALLET_BEFORE_3);
        old.execSQL(CREATE_TABLE_CATEGORY_BEFORE_3);
        old.execSQL(CREATE_TABLE_BUDGET_BEFORE_4);
        // a budget naming a category that is not there, which the fill cannot write a row for
        // while onConfigure has foreign keys on. This connection is not the one it configures
        insertBudget(old, 1, Schema.BudgetType.CATEGORY, 99L);
        old.setVersion(2);
        old.close();

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        try {
            helper.getWritableDatabase();
            fail("a database the migration cannot convert came back converted, so either the fill "
                    + "no longer refuses an orphan row or the failure was caught and carried on from");
        } catch (SQLiteConstraintException expected) {
            helper.close();
        }

        SQLiteDatabase after = mContext.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        assertEquals(2, after.getVersion());
        // and nothing the run did before it failed is left behind either
        assertEquals(0L, count(after, "pragma_table_list",
                "name = '" + Schema.BudgetCategory.TABLE + "'"));
        after.close();
    }

    /**
     * A database this app wrote, put back to the version 1.5.0 and everything before it stamps.
     * The columns the budget rule step adds are already there, so its guards have to skip it, or
     * the statement throws and takes every read of the database with it.
     */
    @Test
    public void aDatabasePutBackToTheVersionBeforeTheJoinTable() {
        upgradeAgainOverRowsThatAreAlreadyThere(3);
    }

    /**
     * A database this app wrote, put back to the version before the rules table, arriving at the
     * upgrade a second time. The create is guarded, so the rules already stored stay where they
     * are instead of the table being replaced with an empty one.
     */
    @Test
    public void rulesSurviveTheUpgradeRunningASecondTime() {
        SQLDatabase writer = new SQLDatabase(mContext, NAME);
        SQLiteDatabase old = writer.getWritableDatabase();
        insertCategory(old, 900, "Food");
        insertCategoryRule(old, 1, "tesco", 900, 0);
        insertCategoryRule(old, 2, "shell", 900, 1);
        old.setVersion(6);
        writer.close();

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(8, upgraded.getVersion());
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"));
        assertEquals(0L, count(upgraded, "pragma_foreign_key_check", null));
        assertEquals(2L, count(upgraded, Schema.CategoryRule.TABLE, null));
        assertEquals(1L, count(upgraded, Schema.CategoryRule.TABLE,
                "rule_pattern = 'tesco' AND rule_category = 900 AND rule_index = 0"));
        helper.close();
    }

    /**
     * A database this app wrote before the group column, arriving at the upgrade. The column is
     * added and every wallet already stored reads null out of it, which is what puts them all in
     * the drawer's ungrouped run and leaves the menu as the release before this one drew it.
     */
    @Test
    public void walletsFromBeforeTheGroupColumnComeForwardInNoGroup() {
        SQLDatabase writer = new SQLDatabase(mContext, NAME);
        SQLiteDatabase old = writer.getWritableDatabase();
        old.execSQL("ALTER TABLE wallets DROP COLUMN wallet_group");
        insertWalletIn(old, 901, "Cash", "EUR", 1500L, false);
        insertWalletIn(old, 902, "Bank", "EUR", 2500L, false);
        old.setVersion(7);
        writer.close();

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(8, upgraded.getVersion());
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"));
        assertEquals(2L, count(upgraded, Schema.Wallet.TABLE, "wallet_group IS NULL"));
        helper.close();
    }

    /**
     * A database this app wrote, put back to the version before the group column, arriving at the
     * upgrade a second time. The add is guarded, so the groups already stored stay where they are
     * instead of the ALTER throwing and taking every read of the database with it.
     */
    @Test
    public void groupsSurviveTheUpgradeRunningASecondTime() {
        SQLDatabase writer = new SQLDatabase(mContext, NAME);
        SQLiteDatabase old = writer.getWritableDatabase();
        insertWalletIn(old, 901, "Cash", "EUR", 1500L, false);
        insertWalletIn(old, 902, "Bank", "EUR", 2500L, false);
        old.execSQL("UPDATE wallets SET wallet_group = 'Savings' WHERE wallet_id = 902");
        old.setVersion(7);
        writer.close();

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(8, upgraded.getVersion());
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"));
        assertEquals(1L, count(upgraded, Schema.Wallet.TABLE, "wallet_group = 'Savings'"));
        assertEquals(1L, count(upgraded, Schema.Wallet.TABLE,
                "wallet_group IS NULL AND wallet_id = 901"));
        helper.close();
    }

    private void insertCategoryRule(SQLiteDatabase db, long id, String pattern, long category,
                                    int index) {
        db.execSQL("INSERT INTO category_rules (rule_id, rule_pattern, rule_category, " +
                        "rule_index, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, pattern, category, index, "rule-" + id, EDIT});
    }

    /**
     * The join table is already there and already holds rows, so the create has to leave it alone
     * and the fill has to leave the categories a budget covers as they are. The column an older
     * release wrote names at most one of them.
     */
    private void upgradeAgainOverRowsThatAreAlreadyThere(int version) {
        SQLiteDatabase old = mContext.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        old.execSQL(Schema.CREATE_TABLE_CATEGORY);
        old.execSQL(Schema.CREATE_TABLE_BUDGET);
        old.execSQL(Schema.CREATE_TABLE_BUDGET_CATEGORY);
        // the group step reads the wallet table, and a database this app wrote has one
        old.execSQL(Schema.CREATE_TABLE_WALLET);
        // every release has shipped a currency table, and the decimals step reads it. The
        // declaration has not changed since the initial commit, so Schema is the one a
        // release of any of these versions wrote
        old.execSQL(Schema.CREATE_TABLE_CURRENCY);
        insertCategory(old, 1, "Food");
        insertCategory(old, 5, "Rent");
        insertCategory(old, 7, "Fun");
        insertCategory(old, 9, "Travel");
        // budgets are numbered a hundred up so that no budget id is also a category id here. The
        // join table carries one of each, and a predicate naming the wrong one of the two reads
        // the same either way while the two sets overlap

        // covers two categories and used to cover a third, which is what an edit leaves behind. The
        // column names a fourth, which is all a release that predates the table could write
        insertBudget(old, 101, Schema.BudgetType.CATEGORY, 9L);
        insertBudgetCategory(old, 101, 5, false);
        insertBudgetCategory(old, 101, 7, false);
        insertBudgetCategory(old, 101, 1, true);
        // covers nothing live, and the category its column names is the one it holds flagged
        insertBudget(old, 102, Schema.BudgetType.CATEGORY, 5L);
        insertBudgetCategory(old, 102, 5, true);
        // changed to a type that does not cover categories by a release that could not see the
        // table, so the column is cleared as that release always clears it and the row it never
        // saw is still live, holding that category against deletion
        insertBudget(old, 103, Schema.BudgetType.EXPENSES, null);
        insertBudgetCategory(old, 103, 7, false);
        // and one it had already dropped before the type was changed. Flagging what is flagged
        // again would restamp it, and a stale deletion carrying today's time beats a newer edit
        // wherever these rows are compared by it
        insertBudgetCategory(old, 103, 1, true);
        // a budget covering the lowest category anything here covers, so the column of a budget
        // that does not cover it has to be read from that budget's own rows and not from the table
        insertBudget(old, 104, Schema.BudgetType.CATEGORY, 1L);
        insertBudgetCategory(old, 104, 1, false);
        old.setVersion(version);
        old.close();

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(8, upgraded.getVersion());
        assertEquals("ok", text(upgraded, "PRAGMA integrity_check"));
        assertEquals(0L, count(upgraded, "pragma_foreign_key_check", null));
        assertEquals(7L, count(upgraded, Schema.BudgetCategory.TABLE, null));

        // the two it covers survive, the one it dropped stays dropped, and the category the older
        // release put in the column is not added to any of them
        assertEquals(2L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 101 AND deleted = 0"));
        assertEquals(1L, count(upgraded, Schema.BudgetCategory.TABLE,
                "_budget = 101 AND _category = 1 AND deleted = 1"));
        assertEquals(0L, count(upgraded, Schema.BudgetCategory.TABLE, "_category = 9"));
        // the column is put back on the lowest category the budget actually covers, so nothing
        // reading it alone shows a category no screen has the budget covering. Lower ids are
        // covered here by another budget, and this one keeps its own
        assertEquals(Long.valueOf(5L), categoryColumnOf(upgraded, 101));
        assertEquals(Long.valueOf(1L), categoryColumnOf(upgraded, 104));

        // the budget holding only a flagged row for the category its column names comes out
        // covering that category, and not carrying a column no live row backs
        assertEquals(1L, count(upgraded, Schema.BudgetCategory.TABLE,
                "_budget = 102 AND _category = 5 AND deleted = 0"));
        assertEquals(Long.valueOf(5L), categoryColumnOf(upgraded, 102));

        // the one whose type was changed loses the row it was left holding, flagged and restamped
        // the way an ordinary edit flags it, while the row it had already dropped is left as it is
        assertEquals(2L, count(upgraded, Schema.BudgetCategory.TABLE, "_budget = 103 AND deleted = 1"));
        assertStampedNow(upgraded,
                "SELECT last_edit FROM budget_categories WHERE _budget = 103 AND _category = 7");
        assertEquals(Long.valueOf(EDIT), number(upgraded,
                "SELECT last_edit FROM budget_categories WHERE _budget = 103 AND _category = 1"));
        helper.close();
    }

    /**
     * The tables an ALTER step touches have to come out of an upgrade with the columns and the
     * foreign keys a fresh install gets, or an upgraded database and a new one are two different
     * databases running the same queries. Compared by name and not in order, since a column an
     * ALTER adds lands at the end while the same column is declared in the middle.
     *
     * Only those three. The join table is created from the same statement on both paths, so
     * comparing it here would be comparing a statement against itself. So is any table on a run
     * where the fixture arrived with the columns already added and no ALTER had anything to do.
     */
    private void assertSchemaMatchesAFreshInstall(SQLiteDatabase upgraded) {
        SQLDatabase helper = new SQLDatabase(mContext, "fresh.db");
        SQLiteDatabase fresh = helper.getWritableDatabase();
        for (String table : new String[] {Schema.Wallet.TABLE, Schema.Category.TABLE,
                Schema.Budget.TABLE}) {
            String columns = declaration(upgraded, "table_info", table);
            // a pragma against a table that is not there answers nothing at all, so without this
            // a name that stopped matching a table would compare two empty strings and pass
            assertFalse(table + " is not in the upgraded database", columns.isEmpty());
            assertEquals(table + " columns", declaration(fresh, "table_info", table), columns);
            assertEquals(table + " foreign keys", declaration(fresh, "foreign_key_list", table),
                    declaration(upgraded, "foreign_key_list", table));
        }
        helper.close();
    }

    /**
     * The migration and the asset file are two sources of truth for the same nine currencies, and
     * an upgraded install reads one while a fresh install reads the other. Putting a
     * "decimals" key of zero back on any of them would leave the two disagreeing with nothing
     * else to catch it.
     */
    @Test
    public void theAssetFileAgreesWithTheCurrenciesTheStepMoves() throws Exception {
        StringBuilder json = new StringBuilder();
        InputStream stream = mContext.getAssets().open("resources/currencies.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
        } finally {
            reader.close();
        }
        JSONArray shipped = new JSONArray(json.toString());
        for (String iso : SQLDatabase.CURRENCIES_MOVED_TO_TWO_DECIMALS) {
            boolean found = false;
            for (int i = 0; i < shipped.length(); i++) {
                JSONObject currency = shipped.getJSONObject(i);
                if (iso.equals(currency.getString("code"))) {
                    found = true;
                    // the same read CurrencyManager.loadDefaultCurrencies makes
                    assertEquals(iso + " is seeded at the wrong scale on a fresh install",
                            2, currency.optInt("decimals", 2));
                }
            }
            assertTrue(iso + " is not in the asset file at all", found);
        }
    }

    /**
     * The nine currencies that move to two decimals. Every amount stored in one of them is
     * multiplied so the ledger reads the same afterwards, and a currency the step does not name is
     * left where it is, which is what the second wallet and its rows are here for.
     */
    @Test
    public void aCurrencyMovedToTwoDecimalsCarriesItsMoneyWithIt() {
        writeADatabaseAtVersionFive(0);

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(8, upgraded.getVersion());
        assertEquals(Long.valueOf(2L), decimalsOf(upgraded, "AMD"));
        assertEquals(Long.valueOf(15000000L), startMoneyOf(upgraded, 901));
        assertEquals(Long.valueOf(150000L), moneyOf(upgraded, 910));
        assertEquals(Long.valueOf(3000000L), budgetMoneyOf(upgraded, 920));
        // a row flagged deleted is reached as well, which the query getWallets used to run was
        // not. No shipped build writes that flag on a wallet, so this pins the query and not a
        // state a user can produce
        assertEquals(Long.valueOf(70000L), startMoneyOf(upgraded, 903));
        assertEquals(Long.valueOf(20000L), moneyOf(upgraded, 911));

        // the euro rows are the control. A step that walked every row instead of the currency it
        // names would move these as well, and every assert above would still read the same
        assertEquals(Long.valueOf(2L), decimalsOf(upgraded, "EUR"));
        assertEquals(Long.valueOf(5000L), startMoneyOf(upgraded, 902));
        assertEquals(Long.valueOf(250L), moneyOf(upgraded, 912));
        assertEquals(Long.valueOf(41000L), budgetMoneyOf(upgraded, 921));

        // the seven tables a wallet based walk reaches through wallet 901
        assertEquals(Long.valueOf(410000L), number(upgraded,
                "SELECT debt_money FROM debts WHERE debt_id = 930"));
        assertEquals(Long.valueOf(520000L), number(upgraded,
                "SELECT saving_start_money FROM savings WHERE saving_id = 940"));
        assertEquals(Long.valueOf(630000L), number(upgraded,
                "SELECT saving_end_money FROM savings WHERE saving_id = 940"));
        assertEquals(Long.valueOf(740000L), number(upgraded,
                "SELECT model_transaction_money FROM transaction_models WHERE model_id = 950"));
        assertEquals(Long.valueOf(190000L), number(upgraded,
                "SELECT recurrent_transaction_money FROM recurrent_transactions WHERE recurrent_transaction_id = 970"));

        // the leg leaving the dram wallet and the tax paid with it move, the leg arriving in
        // the euro wallet does not
        assertEquals(Long.valueOf(850000L), number(upgraded,
                "SELECT model_transfer_from_money FROM transfer_models WHERE model_id = 960"));
        assertEquals(Long.valueOf(170000L), number(upgraded,
                "SELECT model_transfer_tax_money FROM transfer_models WHERE model_id = 960"));
        assertEquals(Long.valueOf(9600L), number(upgraded,
                "SELECT model_transfer_to_money FROM transfer_models WHERE model_id = 960"));

        // and crossing the other way, only the amount arriving moves
        assertEquals(Long.valueOf(2100L), number(upgraded,
                "SELECT model_transfer_from_money FROM transfer_models WHERE model_id = 961"));
        assertEquals(Long.valueOf(400L), number(upgraded,
                "SELECT model_transfer_tax_money FROM transfer_models WHERE model_id = 961"));
        assertEquals(Long.valueOf(320000L), number(upgraded,
                "SELECT model_transfer_to_money FROM transfer_models WHERE model_id = 961"));

        assertEquals(Long.valueOf(260000L), number(upgraded,
                "SELECT recurrent_transfer_from_money FROM recurrent_transfers WHERE recurrent_transfer_id = 980"));
        assertEquals(Long.valueOf(30000L), number(upgraded,
                "SELECT recurrent_transfer_tax_money FROM recurrent_transfers WHERE recurrent_transfer_id = 980"));
        assertEquals(Long.valueOf(2700L), number(upgraded,
                "SELECT recurrent_transfer_to_money FROM recurrent_transfers WHERE recurrent_transfer_id = 980"));
        assertEquals(Long.valueOf(3300L), number(upgraded,
                "SELECT recurrent_transfer_from_money FROM recurrent_transfers WHERE recurrent_transfer_id = 981"));
        assertEquals(Long.valueOf(500L), number(upgraded,
                "SELECT recurrent_transfer_tax_money FROM recurrent_transfers WHERE recurrent_transfer_id = 981"));
        assertEquals(Long.valueOf(340000L), number(upgraded,
                "SELECT recurrent_transfer_to_money FROM recurrent_transfers WHERE recurrent_transfer_id = 981"));
        helper.close();
    }

    /**
     * The same database arriving a second time, which is what installing a release older than this
     * one and upgrading again leaves, since onDowngrade only stamps the version back. The step
     * reads the decimals it is about to write, so the second pass finds two and does nothing. A
     * pass that keyed off the version alone would multiply by a hundred again.
     */
    @Test
    public void theSecondTimeTheStepRunsItMovesNothing() {
        writeADatabaseAtVersionFive(0);

        SQLDatabase first = new SQLDatabase(mContext, NAME);
        first.getWritableDatabase();
        first.close();

        SQLiteDatabase downgraded = mContext.openOrCreateDatabase(NAME, Context.MODE_PRIVATE, null);
        downgraded.setVersion(5);
        downgraded.close();

        SQLDatabase second = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = second.getWritableDatabase();

        assertEquals(8, upgraded.getVersion());
        assertEquals(Long.valueOf(2L), decimalsOf(upgraded, "AMD"));
        assertEquals(Long.valueOf(15000000L), startMoneyOf(upgraded, 901));
        assertEquals(Long.valueOf(150000L), moneyOf(upgraded, 910));
        assertEquals(Long.valueOf(3000000L), budgetMoneyOf(upgraded, 920));
        second.close();
    }

    /**
     * A currency the owner set to something other than zero in Manage currencies. Only a row
     * reading zero is moved, so this one and the money scaled to it are both left alone. An
     * owner who chooses zero is not covered, because that value is the shipped one and the step
     * cannot tell the two apart.
     */
    @Test
    public void decimalsTheOwnerSetAboveZeroAreLeftAlone() {
        writeADatabaseAtVersionFive(3);

        SQLDatabase helper = new SQLDatabase(mContext, NAME);
        SQLiteDatabase upgraded = helper.getWritableDatabase();

        assertEquals(Long.valueOf(3L), decimalsOf(upgraded, "AMD"));
        assertEquals(Long.valueOf(150000L), startMoneyOf(upgraded, 901));
        assertEquals(Long.valueOf(1500L), moneyOf(upgraded, 910));
        assertEquals(Long.valueOf(30000L), budgetMoneyOf(upgraded, 920));
        helper.close();
    }

    /**
     * A database this app wrote, holding one of the nine currencies at the decimals given and the
     * euro beside it, with a wallet, a transaction and a budget in each. The third wallet carries
     * the deleted flag, which no shipped build writes on a wallet, so it is there to pin the query
     * the step runs and not a state a user can reach. Its transaction is live like the others.
     */
    private void writeADatabaseAtVersionFive(int amdDecimals) {
        // onCreate writes the tables and the version is then put back, instead of the tables
        // being declared here the way the join table fixtures declare theirs. The step adds no
        // table and no column, so the schema either side of it is the same one, and the rescale
        // reaches nine tables that all have to be there for it to get to the end
        SQLDatabase writer = new SQLDatabase(mContext, NAME);
        SQLiteDatabase old = writer.getWritableDatabase();
        insertCurrency(old, "AMD", amdDecimals);
        insertCurrency(old, "EUR", 2);
        // above every id onCreate seeds, since it writes the default set of categories
        insertCategory(old, 900, "Food");
        insertWalletIn(old, 901, "Dram", "AMD", 150000L, false);
        insertWalletIn(old, 902, "Euro", "EUR", 5000L, false);
        insertWalletIn(old, 903, "Old dram", "AMD", 700L, true);
        insertTransaction(old, 910, 901, 1500L);
        insertTransaction(old, 911, 903, 200L);
        insertTransaction(old, 912, 902, 250L);
        insertBudgetIn(old, 920, "AMD", 30000L);
        insertBudgetIn(old, 921, "EUR", 41000L);
        // the rescale walks nine tables and every one of them needs a row, or the branch that
        // reaches it is free to do nothing. Every amount here is its own number, so a pass
        // that wrote the right value to the wrong column would still be caught
        insertDebt(old, 930, 901, 4100L);
        insertSaving(old, 940, 901, 5200L, 6300L);
        insertTransactionModel(old, 950, 901, 7400L);
        // out of the dram wallet and into the euro one. The tax follows the leg it is paid
        // from, so this row has two of its three amounts rescaled and the third left alone
        insertTransferModel(old, 960, 901, 902, 8500L, 9600L, 1700L);
        // and the same crossing the other way, where only the amount arriving is rescaled
        insertTransferModel(old, 961, 902, 901, 2100L, 3200L, 400L);
        insertRecurrentTransaction(old, 970, 901, 1900L);
        insertRecurrentTransfer(old, 980, 901, 902, 2600L, 2700L, 300L);
        // and one arriving in the dram wallet, or the pass that matches the wallet the
        // money lands in has nothing to move and reads the same either way
        insertRecurrentTransfer(old, 981, 902, 901, 3300L, 3400L, 500L);
        old.setVersion(5);
        writer.close();
    }

    private void insertCurrency(SQLiteDatabase db, String iso, int decimals) {
        db.execSQL("INSERT INTO currencies (currency_iso, currency_name, currency_symbol, " +
                        "currency_decimals, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                new Object[] {iso, iso, iso, decimals, "currency-" + iso, EDIT});
    }

    private void insertWalletIn(SQLiteDatabase db, long id, String name, String iso,
                                long startMoney, boolean deleted) {
        db.execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, " +
                        "wallet_start_money, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, ?)",
                new Object[] {id, name, iso, startMoney, "wallet-" + id, EDIT, deleted ? 1 : 0});
    }

    private void insertTransaction(SQLiteDatabase db, long id, long wallet, long money) {
        db.execSQL("INSERT INTO transactions (transaction_id, transaction_money, " +
                        "transaction_date, transaction_category, transaction_direction, " +
                        "transaction_type, transaction_wallet, uuid, last_edit, deleted) " +
                        "VALUES (?, ?, '2026-09-01 12:00:00', 900, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, money, Schema.Direction.EXPENSE,
                        Contract.TransactionType.STANDARD, wallet, "transaction-" + id, EDIT});
    }

    private void insertBudgetIn(SQLiteDatabase db, long id, String iso, long money) {
        db.execSQL("INSERT INTO budgets (budget_id, budget_type, budget_start_date, " +
                        "budget_end_date, budget_money, budget_currency, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, '2026-09-01', '2026-09-30', ?, ?, ?, ?, 0)",
                new Object[] {id, Schema.BudgetType.EXPENSES, money, iso, "budget-" + id, EDIT});
    }

    private void insertDebt(SQLiteDatabase db, long id, long wallet, long money) {
        db.execSQL("INSERT INTO debts (debt_id, debt_type, debt_icon, debt_description, " +
                        "debt_date, debt_wallet, debt_money, uuid, last_edit, deleted) " +
                        "VALUES (?, 0, ?, 'Loan', '2026-09-01', ?, ?, ?, ?, 0)",
                new Object[] {id, ICON, wallet, money, "debt-" + id, EDIT});
    }

    private void insertSaving(SQLiteDatabase db, long id, long wallet, long start, long end) {
        db.execSQL("INSERT INTO savings (saving_id, saving_description, saving_icon, " +
                        "saving_start_money, saving_end_money, saving_wallet, uuid, last_edit, " +
                        "deleted) VALUES (?, 'Goal', ?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, ICON, start, end, wallet, "saving-" + id, EDIT});
    }

    private void insertTransactionModel(SQLiteDatabase db, long id, long wallet, long money) {
        db.execSQL("INSERT INTO transaction_models (model_id, " +
                        "model_transaction_money, model_transaction_category, " +
                        "model_transaction_direction, model_transaction_wallet, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, 900, ?, ?, ?, ?, 0)",
                new Object[] {id, money, Schema.Direction.EXPENSE, wallet, "model-" + id, EDIT});
    }

    private void insertTransferModel(SQLiteDatabase db, long id, long from, long to, long moneyFrom,
                                     long moneyTo, long tax) {
        db.execSQL("INSERT INTO transfer_models (model_id, model_transfer_from_wallet, " +
                        "model_transfer_to_wallet, model_transfer_from_money, " +
                        "model_transfer_to_money, model_transfer_tax_money, uuid, last_edit, " +
                        "deleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, from, to, moneyFrom, moneyTo, tax, "transfer-model-" + id, EDIT});
    }

    private void insertRecurrentTransaction(SQLiteDatabase db, long id, long wallet, long money) {
        db.execSQL("INSERT INTO recurrent_transactions (recurrent_transaction_id, " +
                        "recurrent_transaction_money, recurrent_transaction_category, " +
                        "recurrent_transaction_direction, recurrent_transaction_wallet, " +
                        "recurrent_transaction_start_date, recurrent_transaction_last_occurrence, " +
                        "recurrent_transaction_rule, uuid, last_edit, deleted) " +
                        "VALUES (?, ?, 900, ?, ?, '2026-09-01', '2026-09-01', 'FREQ=MONTHLY', ?, ?, 0)",
                new Object[] {id, money, Schema.Direction.EXPENSE, wallet, "recurrence-" + id, EDIT});
    }

    private void insertRecurrentTransfer(SQLiteDatabase db, long id, long from, long to,
                                         long moneyFrom, long moneyTo, long tax) {
        db.execSQL("INSERT INTO recurrent_transfers (recurrent_transfer_id, " +
                        "recurrent_transfer_from_wallet, recurrent_transfer_to_wallet, " +
                        "recurrent_transfer_from_money, recurrent_transfer_to_money, " +
                        "recurrent_transfer_tax_money, recurrent_transfer_start_date, " +
                        "recurrent_transfer_last_occurrence, recurrent_transfer_rule, uuid, " +
                        "last_edit, deleted) " +
                        "VALUES (?, ?, ?, ?, ?, ?, '2026-09-01', '2026-09-01', 'FREQ=MONTHLY', ?, ?, 0)",
                new Object[] {id, from, to, moneyFrom, moneyTo, tax, "recurrent-transfer-" + id, EDIT});
    }

    private Long decimalsOf(SQLiteDatabase db, String iso) {
        return number(db, "SELECT currency_decimals FROM currencies WHERE currency_iso = '" + iso + "'");
    }

    private Long startMoneyOf(SQLiteDatabase db, long wallet) {
        return number(db, "SELECT wallet_start_money FROM wallets WHERE wallet_id = " + wallet);
    }

    private Long moneyOf(SQLiteDatabase db, long transaction) {
        return number(db, "SELECT transaction_money FROM transactions WHERE transaction_id = " + transaction);
    }

    private Long budgetMoneyOf(SQLiteDatabase db, long budget) {
        return number(db, "SELECT budget_money FROM budgets WHERE budget_id = " + budget);
    }

    /** One line per row of the pragma, sorted, with the position columns left out. */
    private String declaration(SQLiteDatabase db, String pragma, String table) {
        Cursor cursor = db.rawQuery("PRAGMA " + pragma + "(" + table + ")", null);
        List<String> rows = new ArrayList<>();
        try {
            while (cursor.moveToNext()) {
                StringBuilder row = new StringBuilder();
                for (int column = 0; column < cursor.getColumnCount(); column++) {
                    String name = cursor.getColumnName(column);
                    if ("cid".equals(name) || "id".equals(name) || "seq".equals(name)) {
                        continue;
                    }
                    row.append(name).append('=').append(cursor.getString(column)).append(' ');
                }
                rows.add(row.toString());
            }
        } finally {
            cursor.close();
        }
        Collections.sort(rows);
        StringBuilder joined = new StringBuilder();
        for (String row : rows) {
            joined.append(row).append('\n');
        }
        return joined.toString();
    }

    private void insertWallet(SQLiteDatabase db, long id, String name) {
        db.execSQL("INSERT INTO wallets (wallet_id, wallet_name, wallet_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, 'EUR', ?, ?, 0)",
                new Object[] {id, name, "wallet-" + id, EDIT});
    }

    private void insertCategory(SQLiteDatabase db, long id, String name) {
        db.execSQL("INSERT INTO categories (category_id, category_name, category_icon, " +
                        "category_type, uuid, last_edit, deleted) VALUES (?, ?, ?, ?, ?, ?, 0)",
                new Object[] {id, name, ICON, Schema.CategoryType.EXPENSE, "category-" + id, EDIT});
    }

    private void insertBudget(SQLiteDatabase db, long id, int type, Long category) {
        db.execSQL("INSERT INTO budgets (budget_id, budget_type, budget_category, " +
                        "budget_start_date, budget_end_date, budget_money, budget_currency, uuid, " +
                        "last_edit, deleted) VALUES (?, ?, ?, '2026-09-01', '2026-09-30', 30000, " +
                        "'EUR', ?, ?, 0)",
                new Object[] {id, type, category, "budget-" + id, EDIT});
    }

    /**
     * A row of the join table as the app writes one. The uuid is deliberately not the one the fill
     * derives, because the app writes a random uuid here and a row carrying the derived one would
     * let the fill collide on the uuid instead of on the pair of keys.
     */
    private void insertBudgetCategory(SQLiteDatabase db, long budget, long category, boolean deleted) {
        db.execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                        "VALUES (?, ?, ?, ?, ?)",
                new Object[] {budget, category, "3f7a1c9e-" + budget + "-" + category, EDIT,
                        deleted ? 1 : 0});
    }

    /**
     * A row the migration wrote or restamped carries the moment of the upgrade. Read as a window
     * and not as a floor, since a floor says nothing about a stamp in the future and nothing about
     * one shifted by a timezone. The shifted one is caught only where the machine running this is
     * not itself on UTC, and every offset in use is wider than the window.
     */
    private void assertStampedNow(SQLiteDatabase db, String sql) {
        long stamped = number(db, sql);
        long since = System.currentTimeMillis() - stamped;
        assertTrue("stamped " + stamped + ", which is " + since + "ms ago",
                since >= 0 && since < 300000L);
    }

    private Long categoryColumnOf(SQLiteDatabase db, long budget) {
        return number(db, "SELECT budget_category FROM budgets WHERE budget_id = " + budget);
    }

    private Long number(SQLiteDatabase db, String sql) {
        Cursor cursor = db.rawQuery(sql, null);
        try {
            assertTrue("no row for " + sql, cursor.moveToFirst());
            return cursor.isNull(0) ? null : cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private long count(SQLiteDatabase db, String table, String selection) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table +
                (selection != null ? " WHERE " + selection : ""), null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getLong(0);
        } finally {
            cursor.close();
        }
    }

    private String text(SQLiteDatabase db, String sql) {
        Cursor cursor = db.rawQuery(sql, null);
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        } finally {
            cursor.close();
        }
    }
}
