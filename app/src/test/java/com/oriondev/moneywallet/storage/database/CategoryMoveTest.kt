package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.database.SQLException
import android.net.Uri
import android.os.Bundle

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.MoneyFormatter

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class CategoryMoveTest {

    companion object {

        private const val DELETED_AT = 1765000000000L

        private val REFERENCES = mapOf(
                "categories" to "category_parent",
                "category_rules" to "rule_category",
                "budgets" to "budget_category",
                "budget_categories" to "_category",
                "transactions" to "transaction_category",
                "transaction_splits" to "split_category",
                "transaction_models" to "model_transaction_category",
                "recurrent_transactions" to "recurrent_transaction_category")
    }

    private lateinit var ledger: DeletedLedger
    private var changed = 0L
    private var cash = 0L
    private var groceries = 0L
    private var household = 0L
    private var home = 0L
    private var salary = 0L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        changed = PreferenceManager.getLastTimeDataIsChanged()
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        household = ledger.insertCategory("Household", Contract.CategoryType.EXPENSE)
        home = ledger.insertCategory("Home", Contract.CategoryType.EXPENSE)
        salary = ledger.insertCategory("Salary", Contract.CategoryType.INCOME)
    }

    @After
    fun tearDown() {
        PreferenceManager.setLastTimeDataIsChanged(changed)
    }

    @Test
    fun everyRowThatUsesTheCategoryMovesToTheTargetAndNothingElseChanges() {
        val splits = useEverywhere()
        val lines = linesOf(splits)
        val before = dump()
        val startedAt = System.currentTimeMillis()

        assertNotNull(move(household, home))

        val after = dump()
        assertEquals(0L, ledger.count("categories", "category_id = $household"))
        for ((table, column) in REFERENCES) {
            assertEquals("$table still uses the category", 0L, ledger.count(table, "$column = $household"))
        }
        for (table in listOf("transactions", "transaction_splits", "transaction_models", "recurrent_transactions")) {
            assertEquals("a live row of $table did not move", true, after.getValue(table).any { it[REFERENCES[table]] == home && it["deleted"] == 0L })
            assertEquals("a deleted row of $table did not move", true, after.getValue(table).any { it[REFERENCES[table]] == home && it["deleted"] != 0L })
        }
        for (table in listOf("budgets", "budget_categories", "category_rules", "categories")) {
            assertTrue("no row of $table moved", after.getValue(table).any { it[REFERENCES[table]] == home })
        }
        assertEquals(movedFrom(before, after, startedAt), after)
        assertEquals(lines.mapValues { (_, it) -> it.map { line -> if (line.first == household) home to line.second else line } }, linesOf(splits))
        for ((transaction, transactionLines) in linesOf(splits)) {
            assertEquals(ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_money"],
                    transactionLines.sumOf { it.second })
        }
    }

    @Test
    fun aBudgetCoveringBothKeepsOneRowForTheTargetAndUndoGivesBackBoth() {
        val budget = ledger.insertBudget(home, cash)
        ledger.database().execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                "VALUES (?, ?, 'budget_household', 7, 0)", arrayOf<Any>(budget, household))
        val before = dump()
        val target = ledger.rows("budget_categories", "_category = $home").single()

        val record = move(household, home)!!

        assertEquals(listOf(target), ledger.rows("budget_categories", "_budget = $budget"))
        assertEquals(0L, ledger.count("budget_categories", "_category = $household"))

        undo(record)

        assertEquals(before, dump())
    }

    @Test
    fun aBudgetThatHadDroppedTheTargetCoversItAgainAndUndoDropsItAgain() {
        val budget = ledger.insertBudget(household, cash)
        ledger.database().execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                "VALUES (?, ?, 'budget_home', 7, 1)", arrayOf<Any>(budget, home))
        val before = dump()

        val record = move(household, home)!!

        assertEquals(listOf(0L), ledger.rows("budget_categories", "_budget = $budget").map { it["deleted"] })
        assertEquals(listOf("budget_home"), ledger.rows("budget_categories", "_budget = $budget").map { it["uuid"] })

        undo(record)

        assertEquals(before, dump())
    }

    @Test
    fun aDeletedTransactionRestoredAfterTheMoveComesBackUnderTheTarget() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        move(household, home)

        assertEquals(1, ledger.restoreTransaction(transaction))
        val row = ledger.rows("transactions", "transaction_id = $transaction").single()
        assertEquals(home, row["transaction_category"])
        assertEquals(0L, row["deleted"])
    }

    @Test
    fun undoPutsBackEveryTableAsItWasWithTheSameIdAndUuid() {
        useEverywhere()
        val category = ledger.rows("categories", "category_id = $household").single()
        val before = dump()

        val record = move(household, home)!!
        undo(record)

        assertEquals(before, dump())
        assertEquals(listOf(category), ledger.rows("categories", "category_id = $household"))
    }

    @Test
    fun undoLeavesInItsNewCategoryATransactionTheUserChangedSinceTheMove() {
        useEverywhere()
        val retagged = ledger.insertTransaction(ledger.transaction(cash, household, 400L))
        val before = dump()

        val record = move(household, home)!!
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(retagged), ContentValues().apply {
            put(Contract.Transaction.CATEGORY_ID, groceries)
        }, null, null))
        val edited = ledger.rows("transactions", "transaction_id = $retagged").single()
        undo(record)

        assertEquals(edited, ledger.rows("transactions", "transaction_id = $retagged").single())
        assertEquals(groceries, edited["transaction_category"])
        val expected = before.mapValues { (table, rows) ->
            if (table == "transactions") rows.map { if (it["transaction_id"] == retagged) edited else it } else rows
        }
        assertEquals(expected, dump())
    }

    @Test
    fun undoLeavesInTheTargetAPartTheEditorSavedAgainSinceTheMove() {
        useEverywhere()
        val edited = ledger.insertSplitTransaction(cash, groceries, listOf(household), emptyList(), emptyList(), null)
        val before = dump()

        val record = move(household, home)!!
        waitUntilAfter(ledger.rows("transaction_splits", "_transaction = $edited").single()["last_edit"] as Long)
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(edited), ledger.transaction(cash, groceries, 1000L).apply {
            put(Contract.Transaction.DESCRIPTION, "Receipt and parking")
            put(Contract.Transaction.PEOPLE_IDS, ledger.ids(emptyList()))
            put(Contract.Transaction.ATTACHMENT_IDS, ledger.ids(emptyList()))
            put(Contract.Transaction.SPLIT_PARTS, SplitPartsCodec.encode(listOf(SplitPartsCodec.Part(home, 300L, "part 0"))))
        }, null, null))
        val transaction = ledger.rows("transactions", "transaction_id = $edited").single()
        val parts = ledger.rows("transaction_splits", "_transaction = $edited")
        val saved = dump()
        undo(record)

        assertEquals(listOf(home), ledger.rows("transaction_splits", "_transaction = $edited").map { it["split_category"] })
        assertEquals(parts, ledger.rows("transaction_splits", "_transaction = $edited"))
        val expected = before.mapValues { (table, rows) ->
            when (table) {
                "transactions" -> rows.map { if (it["transaction_id"] == edited) transaction else it }
                "transaction_splits" -> (rows.filter { it["_transaction"] != edited } + parts)
                        .sortedBy { it["uuid"].toString() }
                "sqlite_sequence" -> saved.getValue(table)
                else -> rows
            }
        }
        assertEquals(expected, dump())
        assertEquals(mapOf(edited to listOf(groceries to 700L, home to 300L)), linesOf(listOf(edited)))
    }

    @Test
    fun undoLeavesAloneABudgetCategoryTheBudgetEditorBroughtBackSinceTheMove() {
        val budget = ledger.insertBudget(groceries, cash)
        ledger.database().execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                "VALUES (?, ?, 'budget_household', 7, 1)", arrayOf<Any>(budget, household))
        val row = ledger.rows("budgets", "budget_id = $budget").single()

        val record = move(household, home)!!
        waitUntilAfter(ledger.rows("budget_categories", "uuid = 'budget_household'").single()["last_edit"] as Long)
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), ContentValues().apply {
            put(Contract.Budget.TYPE, Contract.BudgetType.CATEGORY.value)
            put(Contract.Budget.CATEGORY_IDS, ledger.ids(listOf(groceries, home)))
            put(Contract.Budget.START_DATE, row["budget_start_date"] as String)
            put(Contract.Budget.END_DATE, row["budget_end_date"] as String)
            putNull(Contract.Budget.RULE)
            putNull(Contract.Budget.RULE_START)
            put(Contract.Budget.MONEY, row["budget_money"] as Long)
            putNull(Contract.Budget.TAG)
            put(Contract.Budget.WALLET_IDS, ledger.ids(listOf(cash)))
        }, null, null))
        undo(record)

        assertEquals(setOf(groceries, home), ledger.rows("budget_categories", "_budget = $budget AND deleted = 0")
                .map { it["_category"] }.toSet())
        assertEquals(2, ledger.rows("budget_categories", "_budget = $budget AND deleted = 0").size)
        assertEquals(0L, ledger.count("budget_categories", "_budget = $budget AND _category = $household AND deleted = 0"))
    }

    @Test
    fun undoLeavesInTheTargetATransactionWhoseAmountChangedSinceTheMove() {
        val kept = ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        val edited = ledger.insertTransaction(ledger.transaction(cash, household, 400L))
        ledger.database().execSQL("UPDATE transactions SET last_edit = 11 WHERE transaction_id = ?", arrayOf<Any>(kept))

        val record = move(household, home)!!
        val stamp = ledger.rows("transactions", "transaction_id = $kept").single()["last_edit"] as Long
        waitUntilAfter(stamp)
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(edited), ledger.transaction(cash, home, 1500L), null, null))
        undo(record)

        val row = ledger.rows("transactions", "transaction_id = $edited").single()
        assertEquals(home, row["transaction_category"])
        assertEquals(1500L, row["transaction_money"])
        assertTrue("the edit time went back to ${row["last_edit"]}", row["last_edit"] as Long > stamp)
        val untouched = ledger.rows("transactions", "transaction_id = $kept").single()
        assertEquals(household, untouched["transaction_category"])
        assertEquals(11L, untouched["last_edit"])
    }

    @Test
    fun aTransactionTrashedAfterTheMoveGoesBackOnUndoAndIsRestoredThere() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, household, 900L))

        val record = move(household, home)!!
        assertEquals(1, ledger.flagTransactionDeleted(transaction, DELETED_AT))
        undo(record)

        val trashed = ledger.rows("transactions", "transaction_id = $transaction").single()
        assertEquals(household, trashed["transaction_category"])
        assertEquals(DELETED_AT, trashed["deleted"])
        assertEquals(1, ledger.restoreTransaction(transaction))
        val restored = ledger.rows("transactions", "transaction_id = $transaction").single()
        assertEquals(household, restored["transaction_category"])
        assertEquals(0L, restored["deleted"])
    }

    @Test
    fun anUndoThatCannotPutTheCategoryBackChangesNothing() {
        useEverywhere()
        val uuid = ledger.rows("categories", "category_id = $household").single()["uuid"]
        val record = move(household, home)!!
        ledger.database().execSQL("INSERT INTO categories (category_name, category_icon, category_type, uuid, " +
                "last_edit, deleted) VALUES ('Imported', ?, ?, ?, 1, 0)",
                arrayOf<Any?>(DeletedLedger.ICON, Contract.CategoryType.EXPENSE.value, uuid))
        val before = dump()

        assertEquals(false, restored(undo(record)))

        assertEquals(before, dump())
    }

    @Test
    fun aCategoryWithALiveSubcategoryIsStillRefusedAndNothingChanges() {
        useEverywhere()
        ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
            put(Contract.Category.NAME, "Garden")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
            put(Contract.Category.PARENT, household)
        })

        assertRefused(household, home, Contract.ErrorCode.CATEGORY_HAS_CHILDREN)
    }

    @Test
    fun aSystemCategoryIsStillRefusedAndNothingChanges() {
        val system = ledger.systemCategory(Contract.CategoryTag.TRANSFER)
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))

        assertRefused(system, home, Contract.ErrorCode.SYSTEM_CATEGORY_NOT_MODIFIABLE)
    }

    @Test
    fun movingACategoryOntoItselfChangesNothing() {
        useEverywhere()
        val before = dump()

        assertNull(move(household, household))

        assertEquals(before, dump())
    }

    @Test
    fun aTargetOfAnotherTypeIsRefusedAndNothingChanges() {
        useEverywhere()

        assertRefused(household, salary, Contract.ErrorCode.CATEGORY_NOT_CONSISTENT)
        assertRefused(household, ledger.systemCategory(Contract.CategoryTag.TRANSFER), Contract.ErrorCode.CATEGORY_NOT_CONSISTENT)
        assertRefused(household, 9999L, Contract.ErrorCode.CATEGORY_NOT_CONSISTENT)
    }

    @Test
    fun aMoveThatFailsAtItsLastStepLeavesEveryRowAsItWas() {
        useEverywhere()
        val before = dump()
        ledger.database().execSQL("CREATE TEMP TRIGGER refuse_category_delete BEFORE DELETE ON categories " +
                "BEGIN SELECT RAISE(ABORT, 'refused'); END")
        try {
            try {
                move(household, home)
                fail("the move went through")
            } catch (expected: SQLException) {
            }
        } finally {
            ledger.database().execSQL("DROP TRIGGER refuse_category_delete")
        }

        assertEquals(before, dump())
    }

    @Test
    fun theMoveAndTheUndoTellTheTransactionCategoryAndBudgetLists() {
        useEverywhere()
        val lists = listOf(DataContentProvider.CONTENT_TRANSACTIONS, DataContentProvider.CONTENT_CATEGORIES,
                DataContentProvider.CONTENT_BUDGETS, DataContentProvider.CONTENT_TRANSACTION_MODELS,
                DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, DataContentProvider.CONTENT_CATEGORY_RULES)
        var record: Bundle? = null

        assertEquals(lists.toSet(), announcedBy(lists) { record = move(household, home) }.toSet())
        assertEquals(lists.toSet(), announcedBy(lists) { undo(record!!) }.toSet())
    }

    @Test
    fun theUseCountNamesTheTransactionsOnceAndTheOtherUsesWithoutACount() {
        assertEquals(0L to false, use(household))
        useEverywhere()
        assertEquals(2L to true, use(household))
        val budgetOnly = ledger.insertCategory("Budget only", Contract.CategoryType.EXPENSE)
        ledger.database().execSQL("UPDATE budgets SET budget_category = ? WHERE uuid = 'budget_groceries'", arrayOf<Any>(budgetOnly))
        assertEquals(0L to true, use(budgetOnly))
        val child = ledger.insertCategory("Child", Contract.CategoryType.EXPENSE)
        ledger.database().execSQL("UPDATE categories SET category_parent = ? WHERE category_id = ?", arrayOf<Any>(budgetOnly, child))
        assertEquals(0L to false, use(budgetOnly))
        assertEquals(0L to false, use(ledger.systemCategory(Contract.CategoryTag.TRANSFER)))
    }

    @Test
    fun anUndoAfterTheTargetChangedTypePutsNothingBackAndTheTransactionCanStillBeRestored() {
        val transaction = ledger.insertSplitTransaction(cash, household, listOf(household), emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        val record = move(household, home)!!
        waitUntilAfter(stampOf("transactions", "transaction_id = $transaction"))
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, home), ContentValues().apply {
            put(Contract.Category.NAME, "Home")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            putNull(Contract.Category.PARENT)
            put(Contract.Category.TYPE, Contract.CategoryType.INCOME.value)
            put(Contract.Category.SHOW_REPORT, true)
        }, null, null))

        assertEquals(false, restored(undo(record)))
        assertEquals(0L, ledger.count("categories", "category_id = $household"))
        assertEquals(home, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
        assertEquals(listOf(home), ledger.rows("transaction_splits", "_transaction = $transaction").map { it["split_category"] })
        assertEquals(1, ledger.restoreTransaction(transaction))
        assertEquals(0L, ledger.rows("transactions", "transaction_id = $transaction").single()["deleted"])
    }

    @Test
    fun anUndoAfterTheParentWasDeletedReportsThatNothingWasPutBack() {
        val parent = ledger.insertCategory("Rooms", Contract.CategoryType.EXPENSE)
        val child = ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
            put(Contract.Category.NAME, "Garden")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
            put(Contract.Category.PARENT, parent)
        })!!)
        val transaction = ledger.insertTransaction(ledger.transaction(cash, child, 900L))
        val budget = ledger.insertBudget(child, cash)

        val record = move(child, home)!!
        assertEquals(1, ledger.resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, parent), null, null))

        assertEquals(false, restored(undo(record)))
        assertEquals(0L, ledger.count("categories", "category_id = $child"))
        assertEquals(home, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
        assertEquals(home, ledger.rows("budgets", "budget_id = $budget").single()["budget_category"])
        assertEquals(listOf(home), ledger.rows("budget_categories", "_budget = $budget").map { it["_category"] })
    }

    @Test
    fun anUndoAfterTheParentChangedTypeReportsThatNothingWasPutBack() {
        val parent = ledger.insertCategory("Rooms", Contract.CategoryType.EXPENSE)
        val child = ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
            put(Contract.Category.NAME, "Garden")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
            put(Contract.Category.PARENT, parent)
        })!!)
        val transaction = ledger.insertTransaction(ledger.transaction(cash, child, 900L))

        val record = move(child, home)!!
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, parent), ContentValues().apply {
            put(Contract.Category.NAME, "Rooms")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            putNull(Contract.Category.PARENT)
            put(Contract.Category.TYPE, Contract.CategoryType.INCOME.value)
            put(Contract.Category.SHOW_REPORT, true)
        }, null, null))
        val edited = ledger.rows("categories", "category_id = $parent")

        assertEquals(false, restored(undo(record)))
        assertEquals(0L, ledger.count("categories", "category_id = $child"))
        assertEquals(home, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
        assertEquals(edited, ledger.rows("categories", "category_id = $parent"))
    }

    @Test
    fun anUndoAfterTheParentGotAParentReportsThatNothingWasPutBack() {
        val parent = ledger.insertCategory("Rooms", Contract.CategoryType.EXPENSE)
        val house = ledger.insertCategory("House", Contract.CategoryType.EXPENSE)
        val child = ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
            put(Contract.Category.NAME, "Garden")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
            put(Contract.Category.PARENT, parent)
        })!!)
        val transaction = ledger.insertTransaction(ledger.transaction(cash, child, 900L))

        val record = move(child, home)!!
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, parent), ContentValues().apply {
            put(Contract.Category.NAME, "Rooms")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.PARENT, house)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
        }, null, null))
        val edited = ledger.rows("categories", "category_id = $parent")

        assertEquals(false, restored(undo(record)))
        assertEquals(0L, ledger.count("categories", "category_id = $child"))
        assertEquals(home, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
        assertEquals(edited, ledger.rows("categories", "category_id = $parent"))
    }

    @Test
    fun undoPutsASubcategoryBackUnderItsParent() {
        val parent = ledger.insertCategory("Rooms", Contract.CategoryType.EXPENSE)
        val child = ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
            put(Contract.Category.NAME, "Garden")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
            put(Contract.Category.PARENT, parent)
        })!!)
        val transaction = ledger.insertTransaction(ledger.transaction(cash, child, 900L))
        val category = ledger.rows("categories", "category_id = $child").single()

        val record = move(child, home)!!

        assertEquals(true, restored(undo(record)))
        assertEquals(listOf(category), ledger.rows("categories", "category_id = $child"))
        assertEquals(parent, category["category_parent"])
        assertEquals(child, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
    }

    @Test
    fun undoLeavesInTheTargetAPartTheWalletChangeFixedSinceTheMove() {
        useEverywhere()
        val yen = ledger.insertWallet("Yen", "JPY")
        val changed = ledger.insertSplitTransaction(cash, groceries, listOf(household), emptyList(), emptyList(), null)
        val before = dump()

        val record = move(household, home)!!
        waitUntilAfter(stampOf("transaction_splits", "_transaction = $changed"))
        val from = CurrencyManager.getCurrency("EUR").decimals
        val to = CurrencyManager.getCurrency("JPY").decimals
        assertTrue(from != to)
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(changed), ContentValues().apply {
            put(Contract.Transaction.WALLET_ID, yen)
            put(Contract.Transaction.MONEY, MoneyFormatter.normalize(1000L, from, to))
            put(Contract.Transaction.FIX_SPLIT_DECIMALS, to - from)
        }, null, null))
        val transaction = ledger.rows("transactions", "transaction_id = $changed").single()
        val parts = ledger.rows("transaction_splits", "_transaction = $changed")
        undo(record)

        assertEquals(listOf(home), ledger.rows("transaction_splits", "_transaction = $changed").map { it["split_category"] })
        assertEquals(parts, ledger.rows("transaction_splits", "_transaction = $changed"))
        val expected = before.mapValues { (table, rows) ->
            when (table) {
                "transactions" -> rows.map { if (it["transaction_id"] == changed) transaction else it }
                "transaction_splits" -> rows.map { row -> parts.singleOrNull { it["uuid"] == row["uuid"] } ?: row }
                else -> rows
            }
        }
        assertEquals(expected, dump())
    }

    @Test
    fun undoLeavesInTheTargetACategoryRuleTheEditorSavedSinceTheMove() {
        val changed = insertRule("rent")
        val untouched = insertRule("water")

        val record = move(household, home)!!
        waitUntilAfter(stampOf("category_rules", "rule_id = $untouched"))
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORY_RULES, changed), ContentValues().apply {
            put(Contract.CategoryRule.PATTERN, "rent and water")
            put(Contract.CategoryRule.CATEGORY_ID, home)
        }, null, null))
        undo(record)

        assertEquals(mapOf(changed to home, untouched to household), categoriesOf("category_rules", "rule_id", "rule_category"))
    }

    @Test
    fun undoLeavesInTheTargetABudgetTheEditorSavedSinceTheMove() {
        val (changed, untouched) = insertBudgets()

        val record = move(household, home)!!
        waitUntilAfter(stampOf("budgets", "budget_id = $untouched"))
        saveBudget(changed)
        undo(record)

        assertEquals(mapOf(changed to home, untouched to household), categoriesOf("budgets", "budget_id", "budget_category"))
    }

    @Test
    fun undoLeavesInTheTargetABudgetCategoryTheEditorSavedSinceTheMove() {
        val (changed, untouched) = insertBudgets()

        val record = move(household, home)!!
        waitUntilAfter(stampOf("budget_categories", "_budget = $untouched"))
        saveBudget(changed)
        undo(record)

        assertEquals(mapOf(changed to home, untouched to household), categoriesOf("budget_categories", "_budget", "_category"))
    }

    @Test
    fun undoLeavesInTheTargetAModelTheEditorSavedSinceTheMove() {
        val changed = insertModel()
        val untouched = insertModel()

        val record = move(household, home)!!
        waitUntilAfter(stampOf("transaction_models", "model_id = $untouched"))
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, changed),
                model(home).apply { put(Contract.TransactionModel.DESCRIPTION, "Weekly shop and fuel") }, null, null))
        undo(record)

        assertEquals(mapOf(changed to home, untouched to household), categoriesOf("transaction_models", "model_id", "model_transaction_category"))
    }

    @Test
    fun undoLeavesInTheTargetARecurrenceTheTaskAdvancedSinceTheMove() {
        val changed = insertRecurrence()
        val untouched = insertRecurrence()

        val record = move(household, home)!!
        waitUntilAfter(stampOf("recurrent_transactions", "recurrent_transaction_id = $untouched"))
        val pointer = LocalDate.now().toString()
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, changed), ContentValues().apply {
            put(Contract.RecurrentTransaction.LAST_OCCURRENCE, pointer)
            put(Contract.RecurrentTransaction.NEXT_OCCURRENCE, pointer)
        }, null, null))
        undo(record)

        assertEquals(mapOf(changed to home, untouched to household),
                categoriesOf("recurrent_transactions", "recurrent_transaction_id", "recurrent_transaction_category"))
    }

    @Test
    fun undoLeavesUnderTheTargetASubcategoryChangedSinceTheMove() {
        val changed = insertDeletedChild("category_changed")
        val untouched = insertDeletedChild("category_untouched")

        val record = move(household, home)!!
        val stamp = stampOf("categories", "category_id = $untouched")
        waitUntilAfter(stamp)
        ledger.database().execSQL("UPDATE categories SET last_edit = ? WHERE category_id = ?",
                arrayOf<Any>(System.currentTimeMillis(), changed))
        assertTrue(stampOf("categories", "category_id = $changed") > stamp)
        undo(record)

        assertEquals(mapOf(changed to home, untouched to household), categoriesOf("categories", "category_id", "category_parent"))
    }

    private fun useEverywhere(): List<Long> {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        val split = ledger.insertSplitTransaction(cash, groceries, listOf(household), emptyList(), emptyList(), null)
        ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val deleted = ledger.insertTransaction(ledger.transaction(cash, household, 800L))
        ledger.flagTransactionDeleted(deleted, DELETED_AT)
        val deletedSplit = ledger.insertSplitTransaction(cash, groceries, listOf(household, groceries), emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(deletedSplit, DELETED_AT)
        for ((uuid, deletedFlag) in listOf("model_live" to 0, "model_deleted" to 1)) {
            ledger.database().execSQL("INSERT INTO transaction_models (model_transaction_money, model_transaction_description, " +
                    "model_transaction_category, model_transaction_direction, model_transaction_wallet, uuid, last_edit, " +
                    "deleted) VALUES (500, 'Rent', ?, ?, ?, ?, 3, ?)",
                    arrayOf<Any>(household, Schema.Direction.EXPENSE, cash, uuid, deletedFlag))
        }
        for ((uuid, deletedFlag) in listOf("recurrence_live" to 0, "recurrence_deleted" to 1)) {
            ledger.database().execSQL("INSERT INTO recurrent_transactions (recurrent_transaction_money, " +
                    "recurrent_transaction_category, recurrent_transaction_direction, recurrent_transaction_wallet, " +
                    "recurrent_transaction_start_date, recurrent_transaction_last_occurrence, recurrent_transaction_rule, " +
                    "uuid, last_edit, deleted) VALUES (600, ?, ?, ?, '2026-01-01', '2026-01-01', 'FREQ=MONTHLY', ?, 4, ?)",
                    arrayOf<Any>(household, Schema.Direction.EXPENSE, cash, uuid, deletedFlag))
        }
        ledger.insertBudget(household, cash)
        ledger.database().execSQL("INSERT INTO budgets (budget_type, budget_category, budget_start_date, budget_end_date, " +
                "budget_money, budget_currency, uuid, last_edit, deleted) VALUES (?, ?, '2026-01-01', '2026-12-31', " +
                "50000, 'EUR', 'budget_join', 5, 0)", arrayOf<Any>(Schema.BudgetType.CATEGORY, groceries))
        val joined = ledger.rows("budgets", "uuid = 'budget_join'").single()["budget_id"] as Long
        for ((uuid, category) in listOf("join_groceries" to groceries, "join_household" to household)) {
            ledger.database().execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                    "VALUES (?, ?, ?, 6, 0)", arrayOf<Any>(joined, category, uuid))
        }
        ledger.database().execSQL("INSERT INTO category_rules (rule_pattern, rule_category, rule_index, uuid, last_edit, " +
                "deleted) VALUES ('rent', ?, 0, 'rule_rent', 2, 0)", arrayOf<Any>(household))
        ledger.database().execSQL("INSERT INTO categories (category_name, category_icon, category_type, category_parent, " +
                "uuid, last_edit, deleted) VALUES ('Old', ?, ?, ?, 'category_old', 1, 1)",
                arrayOf<Any>(DeletedLedger.ICON, Contract.CategoryType.EXPENSE.value, household))
        return listOf(split, deletedSplit)
    }

    private fun move(category: Long, target: Long): Bundle? =
            ledger.resolver.call(DataContentProvider.CONTENT_ALL, DataContentProvider.CALL_MOVE_CATEGORY, null, Bundle().apply {
                putLong(DataContentProvider.KEY_CATEGORY_ID, category)
                putLong(DataContentProvider.KEY_TARGET_ID, target)
            })

    private fun undo(record: Bundle): Bundle? =
            ledger.resolver.call(DataContentProvider.CONTENT_ALL, DataContentProvider.CALL_RESTORE_CATEGORY, null, record)

    private fun restored(result: Bundle?): Boolean? = result?.getBoolean(DataContentProvider.KEY_RESTORED, true)

    private fun stampOf(table: String, where: String): Long = ledger.rows(table, where).single()["last_edit"] as Long

    private fun waitUntilAfter(stamp: Long) {
        while (System.currentTimeMillis() <= stamp) {
            Thread.yield()
        }
    }

    private fun categoriesOf(table: String, id: String, column: String): Map<Any?, Any?> =
            ledger.rows(table, "$column IS NOT NULL").associate { it[id] to it[column] }

    private fun insertRule(pattern: String): Long =
            ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, ContentValues().apply {
                put(Contract.CategoryRule.PATTERN, pattern)
                put(Contract.CategoryRule.CATEGORY_ID, household)
            })!!)

    private fun insertBudgets(): Pair<Long, Long> {
        val changed = ledger.insertBudget(household, cash)
        ledger.database().execSQL("INSERT INTO budgets (budget_type, budget_category, budget_start_date, budget_end_date, " +
                "budget_money, budget_currency, uuid, last_edit, deleted) VALUES (?, ?, '2026-01-01', '2026-12-31', " +
                "50000, 'EUR', 'budget_untouched', 5, 0)", arrayOf<Any>(Schema.BudgetType.CATEGORY, household))
        val untouched = ledger.rows("budgets", "uuid = 'budget_untouched'").single()["budget_id"] as Long
        ledger.database().execSQL("INSERT INTO budget_categories (_budget, _category, uuid, last_edit, deleted) " +
                "VALUES (?, ?, 'join_untouched', 6, 0)", arrayOf<Any>(untouched, household))
        return changed to untouched
    }

    private fun saveBudget(budget: Long) {
        val row = ledger.rows("budgets", "budget_id = $budget").single()
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), ContentValues().apply {
            put(Contract.Budget.TYPE, Contract.BudgetType.CATEGORY.value)
            put(Contract.Budget.CATEGORY_IDS, ledger.ids(listOf(home)))
            put(Contract.Budget.START_DATE, row["budget_start_date"] as String)
            put(Contract.Budget.END_DATE, row["budget_end_date"] as String)
            putNull(Contract.Budget.RULE)
            putNull(Contract.Budget.RULE_START)
            put(Contract.Budget.MONEY, 120000L)
            putNull(Contract.Budget.TAG)
            put(Contract.Budget.WALLET_IDS, ledger.ids(listOf(cash)))
        }, null, null))
    }

    private fun model(category: Long): ContentValues = ContentValues().apply {
        put(Contract.TransactionModel.MONEY, 1500L)
        put(Contract.TransactionModel.DESCRIPTION, "Weekly shop")
        put(Contract.TransactionModel.CATEGORY_ID, category)
        put(Contract.TransactionModel.DIRECTION, Contract.Direction.EXPENSE)
        put(Contract.TransactionModel.WALLET_ID, cash)
        put(Contract.TransactionModel.CONFIRMED, true)
        put(Contract.TransactionModel.COUNT_IN_TOTAL, true)
    }

    private fun insertModel(): Long =
            ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_TRANSACTION_MODELS, model(household))!!)

    private fun insertRecurrence(): Long =
            ContentUris.parseId(ledger.resolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, ContentValues().apply {
                put(Contract.RecurrentTransaction.MONEY, 1000L)
                put(Contract.RecurrentTransaction.DESCRIPTION, "Rent")
                put(Contract.RecurrentTransaction.CATEGORY_ID, household)
                put(Contract.RecurrentTransaction.DIRECTION, Contract.Direction.EXPENSE)
                put(Contract.RecurrentTransaction.WALLET_ID, cash)
                put(Contract.RecurrentTransaction.CONFIRMED, true)
                put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, true)
                put(Contract.RecurrentTransaction.START_DATE, LocalDate.now().minusDays(10).toString())
                put(Contract.RecurrentTransaction.RULE, "FREQ=MONTHLY")
            })!!)

    private fun insertDeletedChild(uuid: String): Long {
        ledger.database().execSQL("INSERT INTO categories (category_name, category_icon, category_type, category_parent, " +
                "uuid, last_edit, deleted) VALUES ('Old', ?, ?, ?, ?, 1, 1)",
                arrayOf<Any>(DeletedLedger.ICON, Contract.CategoryType.EXPENSE.value, household, uuid))
        return ledger.rows("categories", "uuid = '$uuid'").single()["category_id"] as Long
    }

    private fun use(category: Long): Pair<Long, Boolean> =
            ledger.resolver.call(DataContentProvider.CONTENT_ALL, DataContentProvider.CALL_CATEGORY_IN_USE, null, Bundle().apply {
                putLong(DataContentProvider.KEY_CATEGORY_ID, category)
            })!!.let { it.getLong(DataContentProvider.KEY_TRANSACTIONS) to it.getBoolean(DataContentProvider.KEY_IN_USE) }

    private fun assertRefused(category: Long, target: Long, errorCode: Int) {
        val before = dump()
        try {
            move(category, target)
            fail("the move went through")
        } catch (e: SQLiteDataException) {
            assertEquals(errorCode, e.errorCode)
        }
        assertEquals(before, dump())
    }

    private fun dump(): Map<String, List<Map<String, Any?>>> =
            ledger.rows("sqlite_master", "type = 'table'").map { it["name"] as String }.sorted().associateWith { table ->
                ledger.rows(table, "1").sortedBy { (it["uuid"] ?: it.toString()).toString() }
            }

    private fun movedFrom(before: Map<String, List<Map<String, Any?>>>, after: Map<String, List<Map<String, Any?>>>,
                          startedAt: Long): Map<String, List<Map<String, Any?>>> = before.mapValues { (table, rows) ->
        val column = REFERENCES[table]
        rows.filter { !(table == "categories" && it["category_id"] == household) }.map { row ->
            if (column != null && row[column] == household) {
                val moved = after.getValue(table).single { it["uuid"] == row["uuid"] }
                assertTrue("$table moved without a new edit time", moved["last_edit"] as Long >= startedAt)
                row + mapOf(column to home, "last_edit" to moved["last_edit"])
            } else {
                row
            }
        }
    }

    private fun linesOf(transactions: List<Long>): Map<Long, List<Pair<Long, Long>>> =
            (ledger.view(DataContentProvider.CONTENT_CATEGORY_LINES) +
                    ledger.view(Uri.withAppendedPath(DataContentProvider.CONTENT_CATEGORY_LINES, "deleted")))
                    .filter { it[Contract.Transaction.ID] in transactions }
                    .groupBy({ it[Contract.Transaction.ID] as Long },
                            { (it[Contract.Transaction.CATEGORY_ID] as Long) to (it[Contract.Transaction.MONEY] as Long) })
                    .mapValues { (_, lines) -> lines.sortedWith(compareBy({ it.first }, { it.second })) }

    private fun announcedBy(uris: List<Uri>, action: () -> Unit): List<Uri?> {
        val announced = mutableListOf<Uri?>()
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                announced.add(uri)
            }
        }
        for (uri in uris) {
            ledger.resolver.registerContentObserver(uri, false, observer)
        }
        try {
            action()
        } finally {
            ledger.resolver.unregisterContentObserver(observer)
        }
        return announced
    }
}
