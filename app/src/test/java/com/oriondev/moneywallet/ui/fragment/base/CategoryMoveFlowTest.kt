package com.oriondev.moneywallet.ui.fragment.base

import android.app.Activity
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.view.View
import android.widget.TextView

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.activity.CategoryPickerActivity
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addPanel
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooper
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.shownDialogMessages
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarText
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarsOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.undoOf
import com.oriondev.moneywallet.ui.fragment.secondary.CategoryItemFragment

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class CategoryMoveFlowTest {

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private var changed = 0L
    private var cash = 0L
    private var groceries = 0L
    private var household = 0L
    private var home = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        changed = PreferenceManager.getLastTimeDataIsChanged()
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        household = ledger.insertCategory("Household", Contract.CategoryType.EXPENSE)
        home = ledger.insertCategory("Home", Contract.CategoryType.EXPENSE)
    }

    @After
    fun tearDown() {
        PreferenceManager.setLastTimeDataIsChanged(changed)
    }

    @Test
    fun anUnusedCategoryAsksTheConfirmOfTodayAndIsDeleted() {
        launch { activity ->
            val (host, panel) = showCategory(activity, household)
            assertEquals(context.getString(R.string.message_delete_category), tapDelete(panel))
            press(DialogInterface.BUTTON_POSITIVE)
            assertEquals(0L, ledger.count("categories", "category_id = $household"))
            assertPanelClosed(host, panel)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aCategoryTransactionsUseCountsEachOnceInTheMoveDialog() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        ledger.insertSplitTransaction(cash, household, listOf(household, groceries), emptyList(), emptyList(), null)
        ledger.insertSplitTransaction(cash, groceries, listOf(household), emptyList(), emptyList(), null)
        launch { activity ->
            val (_, panel) = showCategory(activity, household)
            assertEquals(context.resources.getQuantityString(R.plurals.message_move_category_transactions, 3, 3), tapDelete(panel))
            assertEquals(context.getString(R.string.action_move), button(DialogInterface.BUTTON_POSITIVE).text.toString())
        }
    }

    @Test
    fun aCategoryOnlyABudgetUsesAsksToMoveWithoutACount() {
        ledger.insertBudget(household, cash)
        launch { activity ->
            val (_, panel) = showCategory(activity, household)
            assertEquals(context.getString(R.string.message_move_category_items), tapDelete(panel))
        }
    }

    @Test
    fun movePicksACategoryOfTheSameTypeAndPickingItselfChangesNothing() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        launch { activity ->
            val (_, panel) = showCategory(activity, household)
            tapDelete(panel)
            val picker = pressMove(activity)
            val before = ledger.rows("categories", "1") + ledger.rows("transactions", "1")

            pick(activity, picker, household)

            assertEquals(before, ledger.rows("categories", "1") + ledger.rows("transactions", "1"))
            assertEquals(household, panel.itemId)
            assertEquals(emptyList<String>(), shownDialogMessages())
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aCanceledPickerChangesNothing() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        launch { activity ->
            val (_, panel) = showCategory(activity, household)
            tapDelete(panel)
            val picker = pressMove(activity)
            val before = ledger.rows("categories", "1") + ledger.rows("transactions", "1")

            shadowOf(activity).receiveResult(picker, Activity.RESULT_CANCELED, null)
            idleMainLooper()

            assertEquals(before, ledger.rows("categories", "1") + ledger.rows("transactions", "1"))
            assertEquals(household, panel.itemId)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aPickMovesAndDeletesAndUndoOnTheSnackbarPutsItBack() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        ledger.insertBudget(household, cash)
        val before = ledger.rows("categories", "1") + ledger.rows("transactions", "1") + ledger.rows("budget_categories", "1")
        launch { activity ->
            val (host, panel) = showCategory(activity, household)
            tapDelete(panel)
            pick(activity, pressMove(activity), home)

            assertEquals(0L, ledger.count("categories", "category_id = $household"))
            assertEquals(home, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
            assertPanelClosed(host, panel)
            val snackbar = snackbarsOf(activity).single()
            assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
            assertEquals(context.getString(R.string.snackbar_message_category_deleted), snackbarText(snackbar))
            val undo = undoOf(snackbar)
            assertEquals(context.getString(R.string.action_undo), undo.text.toString())

            undo.performClick()
            idleMainLooper()

            assertEquals(before, ledger.rows("categories", "1") + ledger.rows("transactions", "1") + ledger.rows("budget_categories", "1"))
            assertEquals(0L, panel.itemId)
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    fun aPickIsNotDeliveredAgainToTheNextCategoryWhenTheActivityIsRecreated() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            var before = emptyList<Map<String, Any?>>()
            scenario.onActivity { activity ->
                val (host, panel) = showCategory(activity, household)
                tapDelete(panel)
                pressMove(activity)
                val picker = shadowOf(activity).nextStartedActivityForResult
                activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK,
                        Intent().putExtra(CategoryPickerActivity.RESULT_CATEGORY,
                                Category(home, "Picked", null, Contract.CategoryType.EXPENSE)))
                idleMainLooper()
                assertEquals(0L, ledger.count("categories", "category_id = $household"))
                assertPanelClosed(host, panel)

                host.showSecondaryPanel()
                panel.showItemId(groceries)
                idleMainLooper()
                assertEquals(groceries, panel.itemId)
                before = ledger.rows("categories", "1") + ledger.rows("transactions", "1")
            }

            scenario.recreate()

            scenario.onActivity {
                idleMainLooper()
                assertEquals(before, ledger.rows("categories", "1") + ledger.rows("transactions", "1"))
                assertEquals(1L, ledger.count("categories", "category_id = $groceries"))
            }
        }
    }

    @Test
    fun aSubcategoryAddedWhileThePickerIsOpenRefusesTheMoveWithTodaysMessage() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        launch { activity ->
            val (_, panel) = showCategory(activity, household)
            tapDelete(panel)
            val picker = pressMove(activity)
            insertChild(household)

            pick(activity, picker, home)

            assertEquals(listOf(context.getString(R.string.message_error_delete_category_with_children)), shownDialogMessages())
            assertEquals(1L, ledger.count("categories", "category_id = $household"))
            assertEquals(household, panel.itemId)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aCategoryInUseWithASubcategoryIsStillRefusedAfterTodaysConfirm() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        insertChild(household)
        launch { activity ->
            val (_, panel) = showCategory(activity, household)
            assertEquals(context.getString(R.string.message_delete_category), tapDelete(panel))
            press(DialogInterface.BUTTON_POSITIVE)
            assertEquals(listOf(context.getString(R.string.message_error_delete_category_with_children)), shownDialogMessages())
            assertEquals(1L, ledger.count("categories", "category_id = $household"))
        }
    }

    @Test
    fun aSystemCategoryIsStillRefusedAfterTodaysConfirm() {
        val system = ledger.systemCategory(Contract.CategoryTag.TRANSFER)
        launch { activity ->
            val (_, panel) = showCategory(activity, system)
            assertEquals(context.getString(R.string.message_delete_category), tapDelete(panel))
            press(DialogInterface.BUTTON_POSITIVE)
            assertEquals(listOf(context.getString(R.string.message_error_delete_system_category)), shownDialogMessages())
            assertEquals(1L, ledger.count("categories", "category_id = $system"))
        }
    }

    @Test
    fun pickingItselfLeavesNoPickerBehindAcrossTwoRecreates() {
        ledger.insertTransaction(ledger.transaction(cash, household, 900L))
        ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val before = ledger.rows("categories", "1") + ledger.rows("transactions", "1")
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val (_, panel) = showCategory(activity, household)
                tapDelete(panel)
                pressMove(activity)
                val picker = shadowOf(activity).nextStartedActivityForResult
                activity.activityResultRegistry.dispatchResult(picker.requestCode, Activity.RESULT_OK,
                        Intent().putExtra(CategoryPickerActivity.RESULT_CATEGORY,
                                Category(household, "Picked", null, Contract.CategoryType.EXPENSE)))
                idleMainLooper()
                assertEquals(household, panel.itemId)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                idleMainLooper()
                val (host, panel) = shownCategory(activity)
                host.showSecondaryPanel()
                panel.showItemId(groceries)
                idleMainLooper()
                assertEquals(groceries, panel.itemId)
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                idleMainLooper()
                assertEquals(before, ledger.rows("categories", "1") + ledger.rows("transactions", "1"))
                assertEquals(emptyList<CategoryPicker>(),
                        shownCategory(activity).second.childFragmentManager.fragments.filterIsInstance<CategoryPicker>())
            }
        }
    }

    @Test
    fun anUndoThatCannotPutTheCategoryBackSaysSo() {
        val parent = ledger.insertCategory("Rooms", Contract.CategoryType.EXPENSE)
        insertChild(parent)
        val child = ledger.rows("categories", "category_name = 'Garden'").single()["category_id"] as Long
        val transaction = ledger.insertTransaction(ledger.transaction(cash, child, 900L))
        launch { activity ->
            val (_, panel) = showCategory(activity, child)
            tapDelete(panel)
            pick(activity, pressMove(activity), home)
            assertEquals(0L, ledger.count("categories", "category_id = $child"))
            assertEquals(1, ledger.resolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, parent), null, null))

            undoOf(snackbarsOf(activity).single()).performClick()
            idleMainLooper()

            assertEquals(listOf(context.getString(R.string.message_error_restore_category)), shownDialogMessages())
            assertEquals(0L, ledger.count("categories", "category_id = $child"))
            assertEquals(home, ledger.rows("transactions", "transaction_id = $transaction").single()["transaction_category"])
        }
    }

    private fun launch(action: (BackupListActivity) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }

    private fun showCategory(activity: BackupListActivity, category: Long): Pair<AppBarHost, CategoryItemFragment> {
        val host = addHost(activity)
        val panel = CategoryItemFragment()
        addPanel(host, panel, category)
        return host to panel
    }

    private fun shownCategory(activity: BackupListActivity): Pair<AppBarHost, CategoryItemFragment> {
        val host = activity.supportFragmentManager.fragments.filterIsInstance<AppBarHost>().single()
        return host to host.childFragmentManager.fragments.filterIsInstance<CategoryItemFragment>().single()
    }

    private fun tapDelete(panel: CategoryItemFragment): String {
        val toolbar = panel.requireView().findViewById<Toolbar>(R.id.secondary_toolbar)
        toolbar.menu.performIdentifierAction(R.id.action_delete_item, 0)
        idleMainLooper()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        return dialog.findViewById<TextView>(android.R.id.message)!!.text.toString()
    }

    private fun button(which: Int) = (ShadowDialog.getLatestDialog() as AlertDialog).getButton(which)

    private fun press(which: Int) {
        button(which).performClick()
        idleMainLooper()
    }

    private fun pressMove(activity: BackupListActivity): Intent {
        assertNull(shadowOf(activity).nextStartedActivity)
        press(DialogInterface.BUTTON_POSITIVE)
        val picker = shadowOf(activity).nextStartedActivity
        assertEquals(CategoryPickerActivity::class.java.name, picker.component!!.className)
        assertEquals(Contract.CategoryType.EXPENSE, picker.getSerializableExtra(CategoryPickerActivity.ONLY_TYPE))
        return picker
    }

    private fun pick(activity: BackupListActivity, picker: Intent, category: Long) {
        shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, Intent().putExtra(CategoryPickerActivity.RESULT_CATEGORY,
                Category(category, "Picked", null, Contract.CategoryType.EXPENSE)))
        idleMainLooper()
    }

    private fun insertChild(parent: Long) {
        ledger.resolver.insert(DataContentProvider.CONTENT_CATEGORIES, ContentValues().apply {
            put(Contract.Category.NAME, "Garden")
            put(Contract.Category.ICON, DeletedLedger.ICON)
            put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
            put(Contract.Category.SHOW_REPORT, true)
            put(Contract.Category.PARENT, parent)
        })
    }

    private fun assertPanelClosed(host: AppBarHost, panel: CategoryItemFragment) {
        assertEquals(0L, panel.itemId)
        assertEquals(View.VISIBLE, host.primaryPanel.visibility)
    }
}
