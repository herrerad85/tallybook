package com.oriondev.moneywallet.ui.fragment.base

import android.content.Context
import android.database.Cursor
import android.database.CursorWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.google.android.material.color.MaterialColors

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionSwipeRuleTest
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.addPanel
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.executorOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooper
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.idleMainLooperFor
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.removeHost
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.shownDialogMessages
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarText
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.snackbarsOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.undoOf
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemScreen.whileParked
import com.oriondev.moneywallet.ui.fragment.secondary.TransactionItemFragment

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowChoreographer
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock

import java.time.Duration
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransactionSwipeCallbackTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
        private const val WIDTH = 1080
        private const val HEIGHT = 2000
        private const val STEP = 6f
        private const val MOVES = 120
        private const val SHORT = 60
        private const val STILL = 20
        private const val FRAMES = 60
        private const val TASKS = 20
    }

    private lateinit var context: Context
    private lateinit var ledger: DeletedLedger
    private lateinit var storedPeriods: Set<String>
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L

    private val worker = executorOf(TransactionSelectionMode::class.java)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        storedPeriods = PreferenceManager.getCollapsedPeriods()
        PreferenceManager.setCollapsedPeriods(emptySet())
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
    }

    @After
    fun tearDown() {
        PreferenceManager.setCollapsedPeriods(storedPeriods)
    }

    @Test
    fun aRowThatCanSwipeMovesRightOnlyInEitherLayoutDirectionAndOneThatCannotDoesNotMoveAtAll() {
        launch { activity ->
            val screen = Screen(addHost(activity), TransactionSwipeRuleTest.twoDays())
            val list = screen.list
            for (layoutDirection in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                list.layoutDirection = layoutDirection
                layOut(list)
                assertEquals(layoutDirection, list.layoutDirection)
                assertEquals(listOf(false, true, true, true, false, false, true), (0 until screen.adapter.itemCount).map {
                    screen.dragged(holderAt(list, it), true) > 0f
                })
                assertEquals(List(screen.adapter.itemCount) { 0f }, (0 until screen.adapter.itemCount).map {
                    screen.dragged(holderAt(list, it), false)
                })
            }
            screen.adapter.setSelectedIds(longArrayOf(1L))
            layOut(list)
            assertEquals(List(screen.adapter.itemCount * 2) { 0f }, (0 until screen.adapter.itemCount).flatMap {
                listOf(screen.dragged(holderAt(list, it), true), screen.dragged(holderAt(list, it), false))
            })
        }
    }

    @Test
    fun theThemeErrorColorAndTheIconFillOnlyThePartOfTheRowThatMovedOnTheSideItLeft() {
        for (theme in listOf(R.style.MoneyWalletAppTheme, R.style.MoneyWalletAppThemeDark, R.style.MoneyWalletAppThemeDeepDark)) {
            val adapter = TransactionCursorAdapter(Listener())
            adapter.changeCursor(TransactionSwipeRuleTest.rows(Triple(1L, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00")))
            val list = listOn(ContextThemeWrapper(context, theme), adapter)
            val callback = TransactionSwipeCallback(list, adapter, TransactionSelectionMode(AppBarHost(), AppBarHost(), adapter))
            val error = MaterialColors.getColor(list, androidx.appcompat.R.attr.colorError)
            val onError = MaterialColors.getColor(list, com.google.android.material.R.attr.colorOnError)
            assertTrue("the icon cannot be read on its background", ColorUtils.calculateContrast(onError, error) >= 4.5)
            val row = holderAt(list, 0)
            val margin = list.resources.getDimensionPixelSize(R.dimen.material_component_lists_two_line_avatar_start_margin)
            val icon = AppCompatResources.getDrawable(list.context, R.drawable.ic_delete_black_24dp)!!.intrinsicWidth
            val middle = row.itemView.top + row.itemView.height / 2
            val toTheLeft = drawn(callback, list, row, -300f)
            assertEquals(error, toTheLeft.getPixel(1079, middle))
            assertEquals(error, toTheLeft.getPixel(1080 - 299, middle))
            assertEquals(0, toTheLeft.getPixel(1080 - 301, middle))
            assertEquals(onError, toTheLeft.getPixel(1080 - margin - icon / 2, middle))
            val toTheRight = drawn(callback, list, row, 300f)
            assertEquals(error, toTheRight.getPixel(0, middle))
            assertEquals(error, toTheRight.getPixel(299, middle))
            assertEquals(0, toTheRight.getPixel(301, middle))
            assertEquals(onError, toTheRight.getPixel(margin + icon / 2, middle))
        }
    }

    @Test
    fun aRowSwipedRightIsDeletedAloneClosesItsPanelAndUndoBringsItBack() {
        val first = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val second = ledger.insertTransaction(ledger.transaction(cash, groceries, 700L))
        val firstStored = ledger.transactionRows(first)
        val secondStored = ledger.transactionRows(second)
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, first)
            val screen = Screen(host, stored(first, second))
            val holder = holderAt(screen.list, 0)
            whileParked(worker) {
                screen.swipe(holder, true)
                screen.runFrames()
            }
            screen.runFrames()
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(first)))
            assertEquals(secondStored, ledger.transactionRows(second))
            assertEquals("a deleted row was drawn back before the reload took it away",
                    holder.itemView.width.toFloat(), holder.itemView.translationX)
            assertEquals(0L, panel.itemId)
            assertEquals(View.GONE, host.requireView().findViewById<View>(R.id.secondary_panel_frame_layout).visibility)
            val snackbar = snackbarsOf(activity).single()
            assertSame("the snackbar is not on the primary panel", host.primaryPanel, snackbar.parent)
            assertEquals(context.resources.getQuantityString(R.plurals.snackbar_message_transactions_deleted, 1, 1),
                    snackbarText(snackbar))
            whileParked(worker) {
                undoOf(snackbar).performClick()
            }
            assertEquals(firstStored, ledger.transactionRows(first))
            assertEquals(secondStored, ledger.transactionRows(second))
            assertEquals(emptyList<String>(), shownDialogMessages())
        }
    }

    @Test
    fun aLegPassedToTheOneIdDeleteIsRefusedWithTheDialogAndIsNotDeleted() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val transferStored = ledger.transferRows(transfer)
        launch { activity ->
            val host = addHost(activity)
            val panel = TransactionItemFragment()
            addPanel(host, panel, leg)
            val screen = Screen(host, stored(leg))
            var notDeleted = 0
            whileParked(worker) {
                screen.selectionMode.delete(leg) { notDeleted++ }
                assertEquals("the outcome was handed back before the delete ran", 0, notDeleted)
            }
            assertEquals(1, notDeleted)
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            assertEquals(transferStored, ledger.transferRows(transfer))
            assertEquals(leg, panel.itemId)
            (ShadowDialog.getLatestDialog() as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            idleMainLooper()
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertEquals(1, notDeleted)
        }
    }

    @Test
    fun aSwipedRowTheDatabaseRefusesIsDrawnBackInPlace() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val transferStored = ledger.transferRows(transfer)
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, TransactionSwipeRuleTest.rows(
                    Triple(leg, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00")))
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            assertEquals(transferStored, ledger.transferRows(transfer))
            screen.assertDrawnBackAt(0)
        }
    }

    @Test
    fun aSwipedRowAlreadyDeletedElsewhereIsDrawnBackAndKeepsItsOwnTime() {
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(plain))
            assertEquals(1, ledger.flagTransactionDeleted(plain, DELETED_AT))
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(plain)))
            screen.assertDrawnBackAt(0)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertNull(ShadowDialog.getLatestDialog())
        }
    }

    @Test
    fun aSwipeThatEndsAfterASelectionOpenedDeletesNothingAndIsDrawnBack() {
        val first = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val second = ledger.insertTransaction(ledger.transaction(cash, groceries, 700L))
        val firstStored = ledger.transactionRows(first)
        val secondStored = ledger.transactionRows(second)
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(first, second))
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                screen.adapter.setSelectedIds(longArrayOf(second))
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(firstStored, ledger.transactionRows(first))
            assertEquals(secondStored, ledger.transactionRows(second))
            screen.assertDrawnBackAt(0)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aRowThatCanNoLongerSwipeWhenItsSwipeEndsIsDrawnBackAndNotDeleted() {
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val plainStored = ledger.transactionRows(plain)
        val cursor = ChangingCursor(TransactionSwipeRuleTest.rows(
                Triple(plain, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00")),
                Contract.Transaction.TYPE, Contract.TransactionType.TRANSFER.toLong())
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, cursor)
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                cursor.changed = true
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(plainStored, ledger.transactionRows(plain))
            screen.assertDrawnBackAt(0)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aRowShowingAnotherTransactionWhenItsSwipeEndsDeletesNeitherAndIsDrawnBack() {
        val swiped = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val shown = ledger.insertTransaction(ledger.transaction(cash, groceries, 700L))
        val swipedStored = ledger.transactionRows(swiped)
        val shownStored = ledger.transactionRows(shown)
        val cursor = ChangingCursor(TransactionSwipeRuleTest.rows(
                Triple(swiped, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00")),
                Contract.Transaction.ID, shown)
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, cursor)
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                cursor.changed = true
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(swipedStored, ledger.transactionRows(swiped))
            assertEquals(shownStored, ledger.transactionRows(shown))
            screen.assertDrawnBackAt(0)
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aReloadWhileTheSwipedRowRunsOutDeletesNothingAndEveryRowEndsInPlace() {
        val ids = (1..5).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        val storedRows = ids.map { ledger.transactionRows(it) }
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            assertEquals(ids[2], screen.adapter.getItemId(2))
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 2), true)
                screen.adapter.changeCursor(stored(*ids.drop(1).toLongArray()))
                layOut(screen.list)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals("the row the reload moved under the swipe was deleted", storedRows[3], ledger.transactionRows(ids[3]))
            assertEquals(storedRows, ids.map { ledger.transactionRows(it) })
            assertEquals(List(4) { 0f }, (0 until screen.list.childCount).map { screen.list.getChildAt(it).translationX })
            assertEquals(emptyList<View>(), snackbarsOf(activity))
        }
    }

    @Test
    fun aSwipeWhoseScreenIsGoneBeforeTheDeleteComesBackStillDeletesAndShowsNothing() {
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(plain))
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                screen.runFrames()
                removeHost(activity, host)
            }
            assertNotEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(plain)))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            assertNull(ShadowDialog.getLatestDialog())
        }
    }

    @Test
    fun aReloadWhileTheFingerIsStillDownNearTheTopDeletesNothingAndTheNextSwipeDeletesTheRowItStartsOn() {
        reloadWhileTheFingerIsStillDown(3, 1, true)
    }

    @Test
    fun aReloadWhileTheFingerIsStillDownFarFromTheTopDeletesNothingAndTheNextSwipeDeletesTheRowItStartsOn() {
        reloadWhileTheFingerIsStillDown(12, 6, true)
    }

    @Test
    fun aReloadWhileTheFingerIsStillDownNearTheTopThenLiftedDeletesNothing() {
        reloadWhileTheFingerIsStillDown(3, 1, false)
    }

    @Test
    fun aReloadWhileTheFingerIsStillDownFarFromTheTopThenLiftedDeletesNothing() {
        reloadWhileTheFingerIsStillDown(12, 6, false)
    }

    @Test
    fun aRowGrabbedAgainWhileItSlidesBackIsDeletedAlone() {
        val ids = (1..4).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            whileParked(worker) {
                val row = holderAt(screen.list, 2)
                screen.letGoShort(row)
                screen.runFrames(3)
                screen.grabAgain(row)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[2] }, deletedOf(ids))
            screen.assertEveryRowInPlace(holderAt(screen.list, 2))
        }
    }

    @Test
    fun aRowGrabbedAgainWhileItSlidesBackAfterAnotherRowWasDraggedIsDeletedAlone() {
        val ids = (1..4).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val row = holderAt(screen.list, 2)
            whileParked(worker) {
                screen.letGoShort(row)
                screen.dragAndBringBack(holderAt(screen.list, 0))
                screen.grabAgain(row)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[2] }, deletedOf(ids))
            screen.assertEveryRowInPlace(row)
        }
    }

    @Test
    fun twoRowsSwipedBackToBackAreBothDeletedAndARowSwipedAfterTheReloadIsDeletedToo() {
        val ids = (1..5).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val first = holderAt(screen.list, 1)
            val second = holderAt(screen.list, 3)
            whileParked(worker) {
                screen.swipeFrom(80f, first)
                val y = screen.middleOf(second)
                screen.press(80f, y)
                screen.moveTo(80f + STEP, y)
                screen.drawFrame()
                assertTrue("the first row is no longer running out", first.itemView.translationX in 1f..WIDTH - 1f)
                for (i in 2..MOVES) {
                    screen.moveTo(80f + i * STEP, y)
                }
                screen.lift(80f + MOVES * STEP, y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[1] || it == ids[3] }, deletedOf(ids))
            val left = listOf(ids[0], ids[2], ids[4])
            screen.adapter.changeCursor(stored(*left.toLongArray()))
            layOut(screen.list)
            screen.runFrames()
            assertEquals(left, (0 until screen.adapter.itemCount).map { screen.adapter.getItemId(it) })
            screen.assertEveryRowInPlace()
            whileParked(worker) {
                screen.swipeFrom(80f, holderAt(screen.list, 1))
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[1] || it == ids[2] || it == ids[3] }, deletedOf(ids))
        }
    }

    @Test
    fun aDragOnTheTopRowWhileARowFartherDownSlidesBackDeletesNothing() {
        val ids = (1..4).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            whileParked(worker) {
                val row = holderAt(screen.list, 2)
                screen.letGoShort(row)
                screen.runFrames(3)
                assertTrue("the row is not sliding back", row.itemView.translationX in 1f..SHORT * STEP - 1f)
                val y = screen.middleOf(holderAt(screen.list, 0))
                screen.lift(screen.dragRight(600f, y, SHORT), y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(4) { false }, deletedOf(ids))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            screen.assertEveryRowInPlace()
        }
    }

    @Test
    fun theTopRowGrabbedAgainWhileItSlidesBackIsDeletedAlone() {
        val ids = (1..4).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val row = holderAt(screen.list, 0)
            whileParked(worker) {
                screen.letGoShort(row)
                screen.runFrames(3)
                val sliding = row.itemView.translationX
                assertTrue("the row is not sliding back", sliding in 1f..SHORT * STEP - 1f)
                val y = screen.middleOf(row)
                val from = 80f + sliding
                screen.press(from, y)
                screen.runFrames(3)
                assertEquals("the row was not grabbed where it was sliding", sliding, row.itemView.translationX, 1f)
                val moves = ((WIDTH / 2f - sliding / 2f) / STEP).toInt()
                val to = from + moves * STEP
                for (i in 1..moves) {
                    screen.moveTo(from + i * STEP, y)
                }
                for (i in 1..STILL) {
                    screen.moveTo(to, y)
                }
                screen.drawFrame()
                screen.lift(to, y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[0] }, deletedOf(ids))
            screen.assertEveryRowInPlace(row)
        }
    }

    @Test
    fun aTapWithASecondFingerFarFromWhereTheFirstWentDownDeletesNothing() {
        tapWithASecondFinger(900f)
    }

    @Test
    fun aTapWithASecondFingerWhereTheFirstWentDownDeletesNothing() {
        tapWithASecondFinger(100f)
    }

    @Test
    fun aTapWithANewFingerOnTheRowTheFirstWentDownOnDeletesNothing() {
        tapWithASecondFinger(900f, 1)
    }

    @Test
    fun aSwipeWithOneFingerAfterATapWithASecondFingerDeletesThatRowAlone() {
        val ids = (1..6).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            whileParked(worker) {
                screen.tapWithASecondFinger(900f, 3)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(6) { false }, deletedOf(ids))
            whileParked(worker) {
                screen.swipeFrom(80f, holderAt(screen.list, 2))
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[2] }, deletedOf(ids))
            assertEquals(1, snackbarsOf(activity).size)
        }
    }

    @Test
    fun liftingTheFingerThatDragsARowWhileAnotherFingerRestsDeletesNothingAndTheRowSlidesBack() {
        val ids = (1..6).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val row = holderAt(screen.list, 1)
            val y = screen.middleOf(row)
            val resting = screen.middleOf(holderAt(screen.list, 4))
            val to = 80f + 10 * STEP
            whileParked(worker) {
                screen.touchPointers(MotionEvent.ACTION_DOWN, Triple(0, 80f, y))
                for (i in 1..10) {
                    screen.touchPointers(MotionEvent.ACTION_MOVE, Triple(0, 80f + i * STEP, y))
                }
                screen.drawFrame()
                assertEquals(10 * STEP, row.itemView.translationX)
                screen.touchPointers(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                        Triple(0, to, y), Triple(1, 950f, resting))
                screen.touchPointers(pointerAction(MotionEvent.ACTION_POINTER_UP, 0),
                        Triple(0, to, y), Triple(1, 950f, resting))
                screen.touchPointers(MotionEvent.ACTION_MOVE, Triple(1, 951f, resting))
                screen.touchPointers(MotionEvent.ACTION_UP, Triple(1, 951f, resting))
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(6) { false }, deletedOf(ids))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            screen.assertEveryRowInPlace()
        }
    }

    @Test
    fun aTouchOnTheTopRowThatGrabsARowSlidingBackFartherDownAndAReloadWhileTheFingerIsDownDeletesNothing() {
        val ids = (1..6).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            whileParked(worker) {
                val row = holderAt(screen.list, 4)
                screen.letGo(80f, row, 85)
                val sliding = row.itemView.translationX
                assertTrue("the row is not sliding back", sliding in WIDTH / 2f - 40f..85 * STEP)
                val y = screen.middleOf(holderAt(screen.list, 0))
                val from = sliding + 200f
                screen.press(from, y)
                screen.adapter.changeCursor(stored(*ids.dropLast(1).toLongArray()))
                layOut(screen.list)
                assertEquals(ids[0], screen.adapter.getItemId(0))
                screen.runFrames(15)
                for (moved in listOf(1, 37)) {
                    screen.moveTo(from + moved, y)
                    screen.drawFrame()
                    val top = holderAt(screen.list, 0).itemView.translationX
                    assertTrue("the top row moved by $top while the finger moved by $moved", top <= moved)
                }
                screen.lift(from + 37, y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(6) { false }, deletedOf(ids))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            screen.assertEveryRowInPlace()
        }
    }

    @Test
    fun aRowSwipedAgainAfterAReloadStoppedItsRunOutIsDeletedOnceAndNotDrawnBack() {
        val ids = (1..6).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val deletes = mutableListOf<Long>()
            var notDeleted = 0
            val screen = Screen(host, stored(*ids.toLongArray())) { adapter ->
                object : TransactionSelectionMode(host, host, adapter) {
                    override fun delete(id: Long, onNotDeleted: Runnable) {
                        deletes.add(id)
                        super.delete(id) {
                            notDeleted++
                            onNotDeleted.run()
                        }
                    }
                }
            }
            val holder = holderAt(screen.list, 1)
            whileParked(worker) {
                screen.swipe(holder, true)
                screen.drawFrame()
                screen.runFramesFor(40)
                assertTrue("the row is not running out", holder.itemView.translationX in 1f..WIDTH - 1f)
                val dragged = screen.middleOf(holderAt(screen.list, 4))
                val to = screen.holdRight(dragged)
                screen.drawFrame()
                screen.adapter.changeCursor(stored(*ids.toLongArray()))
                layOut(screen.list)
                assertSame(screen.list, holder.itemView.parent)
                assertEquals(1, holder.bindingAdapterPosition)
                assertEquals(0f, holder.itemView.translationX)
                screen.liftNow(to, dragged)
                screen.runFramesFor(10)
                val y = screen.middleOf(holder)
                screen.press(80f, y)
                for (i in 1..8) {
                    screen.runFramesFor(10)
                    screen.moveToNow(80f + i * 90f, y)
                }
                screen.liftNow(80f + 8 * 90f, y)
                screen.runFramesFor(1000)
            }
            screen.runFrames()
            assertEquals(listOf(ids[1]), deletes)
            assertEquals(0, notDeleted)
            assertEquals(ids.map { it == ids[1] }, deletedOf(ids))
            assertEquals(1, snackbarsOf(activity).size)
            for (i in 0 until screen.list.childCount) {
                val row = screen.list.getChildViewHolder(screen.list.getChildAt(i))
                val position = row.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && screen.adapter.getItemId(position) == ids[1]) {
                    assertEquals("the deleted row was drawn back", WIDTH.toFloat(), row.itemView.translationX)
                }
            }
        }
    }

    @Test
    fun aSwipedRowTheDatabaseRefusesShowsNoErrorColorWhileItComesBack() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, TransactionSwipeRuleTest.rows(
                    Triple(leg, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00")))
            val y = screen.middleOf(holderAt(screen.list, 0)).toInt()
            val error = MaterialColors.getColor(screen.list, androidx.appcompat.R.attr.colorError)
            whileParked(worker) {
                screen.swipe(holderAt(screen.list, 0), true)
                screen.runFrames()
            }
            assertEquals(listOf(context.getString(R.string.message_error_delete_transaction_of_transfer)),
                    shownDialogMessages())
            var animated = 0
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            for (i in 0 until FRAMES) {
                bitmap.eraseColor(0)
                screen.list.draw(Canvas(bitmap))
                if (screen.list.itemAnimator!!.isRunning) {
                    animated++
                }
                for (x in listOf(5, 300, 540, 800, 1075)) {
                    assertNotEquals("frame $i shows the error color at $x", error, bitmap.getPixel(x, y))
                }
                idleMainLooperFor(16)
            }
            assertTrue("the row never animated back", animated > 1)
            screen.assertDrawnBackAt(0)
        }
    }

    @Test
    fun aRowDrawnBackThenShownAgainAfterAReloadShowsTheErrorColorWhenDragged() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val leg = ledger.legsOf(transfer).first()
        val plain = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val other = ledger.insertTransaction(ledger.transaction(cash, groceries, 700L))
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, TransactionSwipeRuleTest.rows(
                    Triple(leg, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00"),
                    Triple(plain, Contract.TransactionType.STANDARD, "2019-03-15 09:00:00")))
            val refused = holderAt(screen.list, 0)
            whileParked(worker) {
                screen.swipe(refused, true)
                screen.runFrames()
            }
            screen.runFrames()
            screen.assertDrawnBackAt(0)
            assertNull(refused.itemView.parent)
            screen.adapter.changeCursor(TransactionSwipeRuleTest.rows(
                    Triple(leg, Contract.TransactionType.STANDARD, "2019-03-15 10:00:00"),
                    Triple(plain, Contract.TransactionType.STANDARD, "2019-03-15 09:00:00"),
                    Triple(other, Contract.TransactionType.STANDARD, "2019-03-15 08:00:00")))
            layOut(screen.list)
            assertSame(refused, holderAt(screen.list, 2))
            val y = screen.middleOf(refused)
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val to = screen.dragRight(80f, y, STILL)
            screen.list.draw(Canvas(bitmap))
            screen.lift(to, y)
            assertEquals(MaterialColors.getColor(screen.list, androidx.appcompat.R.attr.colorError),
                    bitmap.getPixel(2, y.toInt()))
            screen.runFrames()
        }
    }

    @Test
    fun aSwipeEndingForARowAReloadTookAwayDeletesNothingWhileANewDragHoldsThatRowAndTheNewSwipeDeletesOnce() {
        val ids = (1..6).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val holder = holderAt(screen.list, 2)
            var shown = 0L
            whileParked(worker) {
                screen.swipe(holder, true)
                screen.adapter.changeCursor(stored(*ids.drop(1).toLongArray()))
                layOut(screen.list)
                assertSame(screen.list, holder.itemView.parent)
                shown = screen.adapter.getItemId(holder.bindingAdapterPosition)
                val y = screen.middleOf(holder)
                screen.press(80f, y)
                for (i in 1..STILL) {
                    screen.moveToNow(80f + i * STEP, y)
                }
                screen.runFrames()
                assertEquals("the finger no longer holds the row", STILL * STEP, holder.itemView.translationX)
            }
            assertEquals("a row was deleted while the finger was still down", List(6) { false }, deletedOf(ids))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            val y = screen.middleOf(holder)
            whileParked(worker) {
                for (i in STILL + 1..MOVES) {
                    screen.moveTo(80f + i * STEP, y)
                }
                screen.lift(80f + MOVES * STEP, y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == shown }, deletedOf(ids))
            assertEquals(1, snackbarsOf(activity).size)
            assertEquals("a deleted row was drawn back before the reload took it away",
                    WIDTH.toFloat(), holder.itemView.translationX)
        }
    }

    @Test
    fun aRowAReloadTookAwayMidDragLeavesNoErrorColorBehindTheRowNowInItsPlace() {
        val ids = (1..5).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launchWithPausedFrames { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val dragged = holderAt(screen.list, 2)
            val y = screen.middleOf(dragged)
            val error = MaterialColors.getColor(screen.list, androidx.appcompat.R.attr.colorError)
            whileParked(worker) {
                val to = screen.dragRight(80f, y, SHORT)
                screen.drawFrame()
                assertEquals(SHORT * STEP, dragged.itemView.translationX)
                screen.adapter.changeCursor(stored(*ids.drop(1).toLongArray()))
                layOut(screen.list)
                assertNull(dragged.itemView.parent)
                var slid = 0
                val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
                for (i in 0 until FRAMES) {
                    bitmap.eraseColor(0)
                    screen.list.draw(Canvas(bitmap))
                    val moved = dragged.itemView.translationX.toInt()
                    if (moved > 2) {
                        slid++
                        for (x in listOf(1, moved / 2, moved - 2)) {
                            assertNotEquals("frame $i shows the error color at $x", error, bitmap.getPixel(x, y.toInt()))
                        }
                    }
                    idleMainLooperFor(16)
                }
                assertTrue("the row taken away never slid back", slid > 1)
                assertEquals(0f, dragged.itemView.translationX)
                screen.lift(to, y)
                screen.runFrames()
            }
            assertEquals(List(5) { false }, deletedOf(ids))
        }
    }

    @Test
    fun aReloadBeforeTheFingerMovesFarEnoughToSwipeDeletesNothingAndTheNextSwipeWorks() {
        val ids = (1..5).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            val y = screen.middleOf(holderAt(screen.list, 2))
            whileParked(worker) {
                screen.press(80f, y)
                screen.adapter.changeCursor(stored(*ids.drop(1).toLongArray()))
                layOut(screen.list)
                assertEquals(ids[3], screen.adapter.getItemId(2))
                for (i in 1..MOVES) {
                    screen.moveToNow(80f + i * STEP, y)
                }
                screen.lift(80f + MOVES * STEP, y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(5) { false }, deletedOf(ids))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            screen.assertEveryRowInPlace()
            whileParked(worker) {
                screen.swipeFrom(80f, holderAt(screen.list, 2))
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[3] }, deletedOf(ids))
        }
    }

    private fun tapWithASecondFinger(x: Float, tappedRow: Int = 3) {
        val ids = (1..6).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            whileParked(worker) {
                screen.tapWithASecondFinger(x, tappedRow)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(6) { false }, deletedOf(ids))
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            screen.assertEveryRowInPlace()
        }
    }

    private fun pointerAction(action: Int, index: Int): Int = action or (index shl MotionEvent.ACTION_POINTER_INDEX_SHIFT)

    private fun reloadWhileTheFingerIsStillDown(count: Int, dragged: Int, moveBeforeLift: Boolean) {
        val ids = (1..count).map { ledger.insertTransaction(ledger.transaction(cash, groceries, 100L * it)) }
        launch { activity ->
            val host = addHost(activity)
            val screen = Screen(host, stored(*ids.toLongArray()))
            assertEquals(ids[dragged], screen.adapter.getItemId(dragged))
            val y = screen.middleOf(holderAt(screen.list, dragged))
            whileParked(worker) {
                val to = screen.dragRight(80f, y, MOVES)
                screen.adapter.changeCursor(stored(*ids.drop(1).toLongArray()))
                layOut(screen.list)
                assertEquals(ids[dragged + 1], screen.adapter.getItemId(dragged))
                if (moveBeforeLift) {
                    screen.moveTo(to, y)
                }
                screen.lift(to, y)
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(List(count) { false }, deletedOf(ids))
            assertEquals(count - 1, screen.list.childCount)
            screen.assertEveryRowInPlace()
            assertEquals(emptyList<View>(), snackbarsOf(activity))
            whileParked(worker) {
                screen.swipeFrom(80f, holderAt(screen.list, dragged))
                screen.runFrames()
            }
            screen.runFrames()
            assertEquals(ids.map { it == ids[dragged + 1] }, deletedOf(ids))
        }
    }

    private fun deletedOf(ids: List<Long>): List<Boolean> =
            ids.map { ledger.deletedOf(ledger.transactionRows(it)) != setOf<Any?>(0L) }

    private fun launchWithPausedFrames(action: (BackupListActivity) -> Unit) {
        launch { activity ->
            val paused = ShadowChoreographer.isPaused()
            ShadowChoreographer.setPaused(true)
            try {
                action(activity)
            } finally {
                ShadowChoreographer.setPaused(paused)
            }
        }
    }

    private fun launch(action: (BackupListActivity) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }

    private fun stored(vararg ids: Long): Cursor = context.contentResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
            null, Contract.Transaction.ID + " IN (" + ids.joinToString(", ") + ")", null,
            Contract.Transaction.ID + " ASC")!!

    private fun listOn(context: Context, adapter: TransactionCursorAdapter): RecyclerView =
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                layOut(this)
            }

    private fun layOut(list: RecyclerView) {
        list.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY))
        list.layout(0, 0, WIDTH, HEIGHT)
    }

    private fun drawn(callback: TransactionSwipeCallback, list: RecyclerView, row: RecyclerView.ViewHolder,
                      moved: Float): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        callback.onChildDraw(Canvas(bitmap), list, row, moved, 0f, ItemTouchHelper.ACTION_STATE_SWIPE, true)
        return bitmap
    }

    private fun holderAt(list: RecyclerView, position: Int): RecyclerView.ViewHolder =
            list.findViewHolderForAdapterPosition(position)!!

    private inner class Screen(host: AppBarHost, cursor: Cursor,
                               selection: (TransactionCursorAdapter) -> TransactionSelectionMode = {
                                   TransactionSelectionMode(host, host, it)
                               }) {

        val adapter = TransactionCursorAdapter(Listener())
        val selectionMode = selection(adapter)
        val list = RecyclerView(host.requireActivity())
        private var downTime = 0L

        init {
            adapter.changeCursor(cursor)
            list.layoutManager = LinearLayoutManager(host.requireActivity())
            list.adapter = adapter
            host.primaryPanel.addView(list, FrameLayout.LayoutParams(WIDTH, HEIGHT))
            TransactionSwipeCallback.attach(list, adapter, selectionMode)
            layOut(list)
            idleMainLooper()
        }

        fun swipe(holder: RecyclerView.ViewHolder, rightward: Boolean) {
            val y = holder.itemView.top + holder.itemView.height / 2f
            val from = if (rightward) 80f else WIDTH - 80f
            val step = if (rightward) STEP else -STEP
            val time = SystemClock.uptimeMillis()
            touch(time, 0, MotionEvent.ACTION_DOWN, from, y)
            for (i in 1..MOVES) {
                touch(time, i * 10L, MotionEvent.ACTION_MOVE, from + i * step, y)
            }
            touch(time, (MOVES + 1) * 10L, MotionEvent.ACTION_UP, from + MOVES * step, y)
        }

        fun dragged(holder: RecyclerView.ViewHolder, rightward: Boolean): Float {
            val y = middleOf(holder)
            val from = if (rightward) 80f else WIDTH - 80f
            val to = from + STILL * if (rightward) STEP else -STEP
            val time = SystemClock.uptimeMillis()
            touch(time, 0, MotionEvent.ACTION_DOWN, from, y)
            for (i in 1..STILL) {
                touch(time, i * 10L, MotionEvent.ACTION_MOVE, from + (to - from) * i / STILL, y)
            }
            for (i in 1..STILL) {
                touch(time, (STILL + i) * 10L, MotionEvent.ACTION_MOVE, to, y)
            }
            drawFrame()
            val moved = holder.itemView.translationX
            touch(time, (2 * STILL + 1) * 10L, MotionEvent.ACTION_CANCEL, to, y)
            runFrames()
            return moved
        }

        fun middleOf(holder: RecyclerView.ViewHolder): Float = holder.itemView.top + holder.itemView.height / 2f

        fun dragRight(from: Float, y: Float, moves: Int): Float {
            press(from, y)
            for (i in 1..moves) {
                moveTo(from + i * STEP, y)
            }
            return from + moves * STEP
        }

        fun letGo(from: Float, holder: RecyclerView.ViewHolder, moves: Int) {
            val y = middleOf(holder)
            val to = dragRight(from, y, moves)
            for (i in 1..STILL) {
                moveTo(to, y)
            }
            drawFrame()
            lift(to, y)
            drawFrame()
        }

        fun letGoShort(holder: RecyclerView.ViewHolder) {
            letGo(80f, holder, SHORT)
            assertEquals(SHORT * STEP, holder.itemView.translationX)
        }

        fun dragAndBringBack(holder: RecyclerView.ViewHolder) {
            val y = middleOf(holder)
            press(80f, y)
            for (i in 1..6) {
                moveTo(80f + i * STEP, y)
            }
            drawFrame()
            assertTrue(holder.itemView.translationX > 0f)
            moveTo(80f, y)
            lift(80f, y)
            drawFrame()
        }

        fun grabAgain(holder: RecyclerView.ViewHolder) {
            drawFrame()
            val sliding = holder.itemView.translationX
            assertTrue("the row is not sliding back", sliding in 1f..SHORT * STEP - 1f)
            letGo(80f + sliding, holder, ((WIDTH / 2f - sliding / 2f) / STEP).toInt())
        }

        fun swipeFrom(from: Float, holder: RecyclerView.ViewHolder) {
            val y = middleOf(holder)
            lift(dragRight(from, y, MOVES), y)
        }

        fun press(x: Float, y: Float) {
            downTime = SystemClock.uptimeMillis()
            touch(downTime, 0L, MotionEvent.ACTION_DOWN, x, y)
        }

        fun moveTo(x: Float, y: Float) {
            idleMainLooperFor(10)
            touch(downTime, SystemClock.uptimeMillis() - downTime, MotionEvent.ACTION_MOVE, x, y)
        }

        fun moveToNow(x: Float, y: Float) {
            touch(downTime, SystemClock.uptimeMillis() - downTime, MotionEvent.ACTION_MOVE, x, y)
        }

        fun touchPointers(action: Int, vararg pointers: Triple<Int, Float, Float>) {
            if (action == MotionEvent.ACTION_DOWN) {
                downTime = SystemClock.uptimeMillis()
            } else {
                idleMainLooperFor(10)
            }
            val properties = pointers.map {
                MotionEvent.PointerProperties().apply {
                    id = it.first
                    toolType = MotionEvent.TOOL_TYPE_FINGER
                }
            }
            val coords = pointers.map {
                MotionEvent.PointerCoords().apply {
                    x = it.second
                    y = it.third
                    pressure = 1f
                    size = 1f
                }
            }
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, pointers.size,
                    properties.toTypedArray(), coords.toTypedArray(), 0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
            list.dispatchTouchEvent(event)
            event.recycle()
        }

        fun lift(x: Float, y: Float) {
            idleMainLooperFor(10)
            touch(downTime, SystemClock.uptimeMillis() - downTime, MotionEvent.ACTION_UP, x, y)
        }

        fun liftNow(x: Float, y: Float) {
            touch(downTime, SystemClock.uptimeMillis() - downTime, MotionEvent.ACTION_UP, x, y)
        }

        fun holdRight(y: Float): Float {
            val to = 80f + STILL * STEP
            press(80f, y)
            for (i in 1..STILL) {
                touch(downTime, i * 10L, MotionEvent.ACTION_MOVE, 80f + i * STEP, y)
            }
            for (i in 1..STILL) {
                touch(downTime, (STILL + i) * 10L, MotionEvent.ACTION_MOVE, to, y)
            }
            return to
        }

        fun tapWithASecondFinger(x: Float, tappedRow: Int) {
            val first = middleOf(holderAt(list, 1))
            val resting = middleOf(holderAt(list, 5))
            val tapped = middleOf(holderAt(list, tappedRow))
            touchPointers(MotionEvent.ACTION_DOWN, Triple(0, 100f, first))
            touchPointers(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 1),
                    Triple(0, 100f, first), Triple(1, 100f, resting))
            touchPointers(pointerAction(MotionEvent.ACTION_POINTER_UP, 0),
                    Triple(0, 100f, first), Triple(1, 100f, resting))
            touchPointers(pointerAction(MotionEvent.ACTION_POINTER_DOWN, 0),
                    Triple(0, x, tapped), Triple(1, 100f, resting))
            touchPointers(MotionEvent.ACTION_MOVE, Triple(0, x + 2f, tapped), Triple(1, 100f, resting))
            touchPointers(pointerAction(MotionEvent.ACTION_POINTER_UP, 0),
                    Triple(0, x + 2f, tapped), Triple(1, 100f, resting))
            touchPointers(MotionEvent.ACTION_UP, Triple(1, 100f, resting))
        }

        fun runFramesFor(millis: Int) {
            val looper = shadowOf(Looper.getMainLooper())
            for (i in 1..millis) {
                ShadowSystemClock.advanceBy(Duration.ofMillis(1))
                var tasks = 0
                while (!looper.isIdle && tasks < TASKS) {
                    looper.runOneTask()
                    tasks++
                }
                if (i % 16 == 0) {
                    drawFrame()
                }
            }
        }

        fun drawFrame() {
            list.draw(Canvas(Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)))
        }

        fun runFrames(frames: Int) {
            for (i in 0 until frames) {
                drawFrame()
                idleMainLooperFor(16)
            }
            drawFrame()
        }

        private fun touch(downTime: Long, after: Long, action: Int, x: Float, y: Float) {
            val event = MotionEvent.obtain(downTime, downTime + after, action, x, y, 0)
            list.dispatchTouchEvent(event)
            event.recycle()
        }

        fun runFrames() {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            for (i in 0 until FRAMES) {
                list.draw(Canvas(bitmap))
                idleMainLooperFor(16)
            }
        }

        fun assertDrawnBackAt(position: Int) {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            list.draw(Canvas(bitmap))
            val row = holderAt(list, position).itemView
            assertEquals(0f, row.translationX)
            assertNotEquals("the row is drawn as swiped away",
                    MaterialColors.getColor(list, androidx.appcompat.R.attr.colorError),
                    bitmap.getPixel(WIDTH / 2, row.top + row.height / 2))
        }

        fun assertEveryRowInPlace(deleted: RecyclerView.ViewHolder? = null) {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            list.draw(Canvas(bitmap))
            val error = MaterialColors.getColor(list, androidx.appcompat.R.attr.colorError)
            for (i in 0 until list.childCount) {
                val row = list.getChildAt(i)
                if (row === deleted?.itemView) {
                    assertEquals(WIDTH.toFloat(), row.translationX)
                    continue
                }
                assertEquals(0f, row.translationX)
                assertNotEquals("the row is drawn as swiped away", error, bitmap.getPixel(WIDTH / 2, row.top + row.height / 2))
            }
        }
    }

    private class ChangingCursor(cursor: Cursor, private val column: String, private val value: Long) : CursorWrapper(cursor) {

        var changed = false

        override fun getInt(columnIndex: Int): Int = if (isChanged(columnIndex)) value.toInt() else super.getInt(columnIndex)

        override fun getLong(columnIndex: Int): Long = if (isChanged(columnIndex)) value else super.getLong(columnIndex)

        private fun isChanged(columnIndex: Int): Boolean = changed && getColumnName(columnIndex) == column
    }

    private class Listener : TransactionCursorAdapter.ActionListener {

        override fun onHeaderClick(startDate: Date, endDate: Date) {
        }

        override fun onTransactionClick(id: Long) {
        }

        override fun onSelectionChanged(count: Int) {
        }
    }
}
