package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class DeletedRestoreTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
    }

    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var bank = 0L
    private var groceries = 0L
    private var household = 0L
    private var fuel = 0L
    private var ann = 0L
    private var bob = 0L
    private var party = 0L
    private var budget = 0L
    private var debt = 0L
    private var saving = 0L

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        bank = ledger.insertWallet("Bank", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        household = ledger.insertCategory("Household", Contract.CategoryType.EXPENSE)
        fuel = ledger.insertCategory("Fuel", Contract.CategoryType.EXPENSE)
        ann = ledger.insertPerson("Ann")
        bob = ledger.insertPerson("Bob")
        party = ledger.insertEvent("Party")
        budget = ledger.insertBudget(groceries, cash)
        debt = ledger.insertDebt(cash, 2000L, listOf(ann))
        saving = ledger.insertSaving(cash)
    }

    @Test
    fun aDeletedTransactionCarriesOneTimeOnEveryRowAndLeavesEveryView() {
        val before = totals()
        val transaction = insertSplitTransaction()
        val counted = totals()
        val shown = itemViews(transaction)
        assertNotEquals(before, counted)
        assertEquals(2, shown.getValue("people").size)
        assertEquals(2, shown.getValue("attachments").size)
        assertEquals(2, shown.getValue("splits").size)
        assertEquals(3, shown.getValue("lines").size)

        assertEquals(1, ledger.flagTransactionDeleted(transaction, DELETED_AT))

        val rows = ledger.transactionRows(transaction)
        assertEquals(9, rows.size)
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(rows))
        assertEquals(before, totals())
        assertTrue(itemViews(transaction).values.all { it.isEmpty() })
    }

    @Test
    fun aDeletedDebtPaymentAndSavingDepositLeaveTheirTotals() {
        val before = totals()
        val payment = insertPayment(cash)
        val deposit = insertDeposit()
        assertNotEquals(before["debt"], totals()["debt"])
        assertNotEquals(before["saving"], totals()["saving"])

        assertEquals(1, ledger.flagTransactionDeleted(payment))
        assertEquals(1, ledger.flagTransactionDeleted(deposit))

        assertEquals(before, totals())
    }

    @Test
    fun aDeletedTransferCarriesOneTimeOnItsRowsAndItsThreeLegs() {
        val before = totals()
        val transfer = insertTransfer()
        assertNotEquals(before, totals())
        assertEquals(3, ledger.legsOf(transfer).size)

        assertEquals(1, ledger.flagTransferDeleted(transfer, DELETED_AT))

        val rows = ledger.transferRows(transfer)
        assertEquals(10, rows.size)
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(rows))
        assertEquals(before, totals())
        assertTrue(ledger.view(ledger.transferUri(transfer)).isEmpty())
    }

    @Test
    fun aLegOfATransferIsRefusedAndNothingIsWritten() {
        val transfer = insertTransfer()
        for (leg in ledger.legsOf(transfer)) {
            try {
                ledger.flagTransactionDeleted(leg)
                fail("a leg of a transfer was deleted by itself")
            } catch (e: SQLiteDataException) {
                assertEquals(Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER, e.errorCode)
            }
        }
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transferRows(transfer)))
    }

    @Test
    fun itemsDeletedTogetherCarryTheTimeTheyWereGiven() {
        val first = insertSplitTransaction()
        val second = ledger.insertTransaction(ledger.transaction(bank, fuel, 700L))
        val transfer = insertTransfer()

        assertEquals(1, ledger.flagTransactionDeleted(first, DELETED_AT))
        assertEquals(1, ledger.flagTransactionDeleted(second, DELETED_AT))
        assertEquals(1, ledger.flagTransferDeleted(transfer, DELETED_AT))

        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(first) +
                ledger.transactionRows(second) + ledger.transferRows(transfer)))
    }

    @Test
    fun anItemDeletedWithNoTimeCarriesTheTimeOfTheDelete() {
        val transaction = insertSplitTransaction()
        val start = System.currentTimeMillis()
        assertEquals(1, ledger.flagTransactionDeleted(transaction))
        val end = System.currentTimeMillis()
        val deletedAt = ledger.deletedOf(ledger.transactionRows(transaction)).single() as Long
        assertTrue(deletedAt in start..end)
    }

    @Test
    fun anItemAlreadyDeletedOrMissingChangesNothing() {
        val transaction = insertSplitTransaction()
        val transfer = insertTransfer()
        assertEquals(1, ledger.flagTransactionDeleted(transaction, DELETED_AT))
        assertEquals(1, ledger.flagTransferDeleted(transfer, DELETED_AT))

        assertEquals(0, ledger.flagTransactionDeleted(transaction, DELETED_AT + 1))
        assertEquals(0, ledger.flagTransferDeleted(transfer, DELETED_AT + 1))
        assertEquals(0, ledger.flagTransactionDeleted(99999L))
        assertEquals(0, ledger.flagTransferDeleted(99999L))
        assertEquals(0, ledger.restoreTransaction(99999L))
        assertEquals(0, ledger.restoreTransfer(99999L))

        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(transaction) +
                ledger.transferRows(transfer)))
    }

    @Test
    fun aRestoredTransactionComesBackAsItWas() {
        val transaction = insertSplitTransaction()
        val rows = ledger.transactionRows(transaction)
        val counted = totals()
        val shown = itemViews(transaction)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        assertEquals(1, ledger.restoreTransaction(transaction))

        assertEquals(rows, ledger.transactionRows(transaction))
        assertEquals(counted, totals())
        assertEquals(shown, itemViews(transaction))
        assertEquals(0, ledger.restoreTransaction(transaction))
    }

    @Test
    fun aRestoredTransferComesBackAsItWas() {
        val transfer = insertTransfer()
        val rows = ledger.transferRows(transfer)
        val counted = totals()
        val shown = transferViews(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        assertEquals(1, ledger.restoreTransfer(transfer))

        assertEquals(rows, ledger.transferRows(transfer))
        assertEquals(counted, totals())
        assertEquals(shown, transferViews(transfer))
    }

    @Test
    fun aLegOfADeletedTransferIsNotRestoredByItself() {
        val transfer = insertTransfer()
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        for (leg in ledger.legsOf(transfer)) {
            try {
                ledger.restoreTransaction(leg)
                fail("a leg of a deleted transfer was restored by itself")
            } catch (e: SQLiteDataException) {
                assertEquals(Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER, e.errorCode)
            }
        }
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transferRows(transfer)))
    }

    @Test
    fun aSharedAttachmentIsLiveOnceEitherOwnerIsRestored() {
        val attachment = ledger.insertAttachment("shared")
        val first = insertWithAttachment(attachment)
        val second = insertWithAttachment(attachment)

        ledger.flagTransactionDeleted(first, DELETED_AT)
        assertEquals(0L, attachmentDeleted(attachment))
        ledger.flagTransactionDeleted(second, DELETED_AT + 1)
        assertEquals(DELETED_AT + 1, attachmentDeleted(attachment))
        ledger.restoreTransaction(first)

        assertEquals(0L, attachmentDeleted(attachment))
        assertEquals(1, ledger.view(Uri.withAppendedPath(ledger.transactionUri(first), "attachments")).size)
        assertEquals(1, ledger.deleteTransaction(second))
        assertEquals(1L, ledger.count("attachments", "attachment_id = $attachment"))
        assertTrue(File(ledger.attachmentFolder, "shared").exists())
    }

    @Test
    fun aSharedAttachmentSurvivesWhileADeletedOwnerCanStillBeRestored() {
        val attachment = ledger.insertAttachment("shared")
        val first = insertWithAttachment(attachment)
        val second = insertWithAttachment(attachment)
        ledger.flagTransactionDeleted(first, DELETED_AT)
        ledger.flagTransactionDeleted(second, DELETED_AT + 1)

        assertEquals(1, ledger.deleteTransaction(second))

        assertEquals(1L, ledger.count("attachments", "attachment_id = $attachment"))
        assertTrue(File(ledger.attachmentFolder, "shared").exists())
        assertEquals(1, ledger.restoreTransaction(first))
        assertEquals(0L, attachmentDeleted(attachment))
        assertEquals(1, ledger.view(Uri.withAppendedPath(ledger.transactionUri(first), "attachments")).size)
    }

    @Test
    fun aRestoreRefusedByTheCurrencyOfADebtPaymentWritesNothing() {
        val payment = insertPayment(bank)
        ledger.flagTransactionDeleted(payment, DELETED_AT)
        val rows = ledger.transactionRows(payment)
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(
                DataContentProvider.CONTENT_WALLETS, bank), ContentValues().apply {
            put(Contract.Wallet.CURRENCY, "USD")
        }, null, null))

        try {
            ledger.restoreTransaction(payment)
            fail("a payment on a euro debt was restored into a dollar wallet")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }

        assertEquals(rows, ledger.transactionRows(payment))
    }

    @Test
    fun aRestoreRefusedByItsPartsWritesNothing() {
        val transaction = insertSplitTransaction()
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(
                DataContentProvider.CONTENT_CATEGORIES, fuel), ContentValues().apply {
            put(Contract.Category.TYPE, Contract.CategoryType.INCOME.value)
        }, null, null))
        val rows = ledger.transactionRows(transaction)

        try {
            ledger.restoreTransaction(transaction)
            fail("a transaction was restored with a part in a category of the other direction")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.SPLIT_NOT_VALID, e.errorCode)
        }

        assertEquals(rows, ledger.transactionRows(transaction))
    }

    @Test
    fun aRestoredMasterTransactionFollowsTheDebtAsItIsNow() {
        val master = ledger.rows("transactions", "transaction_debt = $debt").single()["transaction_id"] as Long
        val time = (ledger.rows("transactions", "transaction_id = $master").single()["transaction_date"] as String).substring(11)
        ledger.flagTransactionDeleted(master, DELETED_AT)
        val date = LocalDate.now().minusDays(5).toString()
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(
                DataContentProvider.CONTENT_DEBTS, debt), ContentValues().apply {
            put(Contract.Debt.MONEY, 3500L)
            put(Contract.Debt.DESCRIPTION, "Car loan")
            put(Contract.Debt.DATE, date)
            put(Contract.Debt.TYPE, Contract.DebtType.CREDIT.value)
            put(Contract.Debt.WALLET_ID, bank)
            put(Contract.Debt.NOTE, "Signed")
            put(Contract.Debt.PEOPLE_IDS, ledger.ids(listOf(bob)))
        }, null, null))
        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(ledger.transactionRows(master)))

        assertEquals(1, ledger.restoreTransaction(master))

        val row = ledger.view(ledger.transactionUri(master)).single()
        assertEquals(3500L, row[Contract.Transaction.MONEY])
        assertEquals("Car loan", row[Contract.Transaction.DESCRIPTION])
        assertEquals("$date $time", row[Contract.Transaction.DATE])
        assertEquals(Contract.Direction.EXPENSE.toLong(), row[Contract.Transaction.DIRECTION])
        assertEquals(ledger.systemCategory(Contract.CategoryTag.CREDIT), row[Contract.Transaction.CATEGORY_ID])
        assertEquals(bank, row[Contract.Transaction.WALLET_ID])
        assertEquals("Signed", row[Contract.Transaction.NOTE])
        assertEquals("<$bob>", row[Contract.Transaction.PEOPLE_IDS])
    }

    @Test
    fun aDeleteARestoreAndACleanUpAreAnnouncedOnTheirLists() {
        val transaction = insertSplitTransaction()
        val transfer = insertTransfer()

        val announced = announcedBy {
            ledger.flagTransactionDeleted(transaction)
            ledger.restoreTransaction(transaction)
            ledger.flagTransferDeleted(transfer)
            ledger.restoreTransfer(transfer)
            ledger.deleteFlaggedItems(DELETED_AT)
        }

        assertEquals(listOf(DataContentProvider.CONTENT_TRANSACTIONS, DataContentProvider.CONTENT_TRANSACTIONS,
                DataContentProvider.CONTENT_TRANSFERS, DataContentProvider.CONTENT_TRANSFERS), announced)
    }

    @Test
    fun aDeleteOrACleanUpThatChangesNothingIsNotAnnounced() {
        val transaction = insertSplitTransaction()
        val transfer = insertTransfer()
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val liveTransfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val changed = PreferenceManager.getLastTimeDataIsChanged()
        PreferenceManager.setLastTimeDataIsChanged(1L)
        try {
            val announced = announcedBy {
                assertEquals(0, ledger.flagTransactionDeleted(transaction, DELETED_AT + 1))
                assertEquals(0, ledger.flagTransferDeleted(transfer, DELETED_AT + 1))
                assertEquals(0, ledger.flagTransactionDeleted(live, 0L))
                assertEquals(0, ledger.flagTransferDeleted(liveTransfer, -1L))
                assertEquals(0, ledger.deleteFlaggedItems(DELETED_AT - 1))
            }

            assertEquals(emptyList<Uri?>(), announced)
            assertEquals(1L, PreferenceManager.getLastTimeDataIsChanged())
        } finally {
            PreferenceManager.setLastTimeDataIsChanged(changed)
        }
        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(live) + ledger.transferRows(liveTransfer)))
    }

    @Test
    fun aCleanUpOfOneTransferIsAnnouncedOnBothLists() {
        val transfer = insertTransfer()
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        var result = 0
        val announced = announcedBy {
            result = ledger.deleteFlaggedItems(DELETED_AT)
        }

        assertEquals(Pair(1, listOf(DataContentProvider.CONTENT_TRANSACTIONS, DataContentProvider.CONTENT_TRANSFERS)),
                Pair(result, announced))
        assertEquals(0L, ledger.count("transfers", "transfer_id = $transfer"))
    }

    @Test
    fun aCleanUpOfOneTransactionIsAnnouncedOnBothLists() {
        val transaction = insertSplitTransaction()
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        var result = 0
        val announced = announcedBy {
            result = ledger.deleteFlaggedItems(DELETED_AT)
        }

        assertEquals(Pair(1, listOf(DataContentProvider.CONTENT_TRANSACTIONS, DataContentProvider.CONTENT_TRANSFERS)),
                Pair(result, announced))
        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(transaction))
    }

    @Test
    fun aDepositIsNotRestoredIntoASavingMovedToAnotherCurrency() {
        val travel = ledger.insertWallet("Travel", "USD")
        val deposit = insertDeposit()
        ledger.flagTransactionDeleted(deposit, DELETED_AT)
        assertEquals(1, updateSaving(travel))

        assertDepositIsNotRestored(deposit)
    }

    @Test
    fun aDepositIsNotRestoredFromAWalletMovedToAnotherCurrency() {
        val deposit = insertDeposit()
        assertEquals(1, updateSaving(bank))
        ledger.flagTransactionDeleted(deposit, DELETED_AT)
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(
                DataContentProvider.CONTENT_WALLETS, cash), ContentValues().apply {
            put(Contract.Wallet.CURRENCY, "USD")
        }, null, null))

        assertDepositIsNotRestored(deposit)
    }

    @Test
    fun aDepositIsRestoredIntoASavingMovedToAWalletOfTheSameCurrency() {
        val deposit = insertDeposit()
        ledger.flagTransactionDeleted(deposit, DELETED_AT)
        assertEquals(1, updateSaving(bank))

        assertEquals(1, ledger.restoreTransaction(deposit))

        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(deposit)))
        assertEquals(cash, ledger.rows("transactions", "transaction_id = $deposit").single()["transaction_wallet"])
    }

    @Test
    fun aDeletedTransactionIsNotUpdated() {
        val transaction = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel), listOf(ann, bob),
                listOf(ledger.insertAttachment("receipt")), party)
        val live = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel), listOf(ann, bob),
                listOf(ledger.insertAttachment("invoice")), party)
        val cy = ledger.insertPerson("Cy")
        val values = ledger.transaction(bank, groceries, 2000L).apply {
            put(Contract.Transaction.SPLIT_PARTS, SplitPartsCodec.encode(listOf(SplitPartsCodec.Part(household, 500L, "new"))))
            put(Contract.Transaction.PEOPLE_IDS, ledger.ids(listOf(cy)))
            put(Contract.Transaction.ATTACHMENT_IDS, ledger.ids(listOf(ledger.insertAttachment("statement"))))
        }
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        val rows = transactionTables()

        assertEquals(0, ledger.resolver.update(ledger.transactionUri(transaction), values, null, null))

        assertEquals(rows, transactionTables())
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(live), values, null, null))
        assertEquals(bank, ledger.rows("transactions", "transaction_id = $live").single()["transaction_wallet"])
    }

    @Test
    fun aDeletedTransactionUpdatedWithItsPartsIsStillRefusedByThem() {
        val transaction = insertSplitTransaction()
        val parts = SplitPartsCodec.encode(listOf(SplitPartsCodec.Part(household, 300L, "part 0"),
                SplitPartsCodec.Part(fuel, 301L, "part 1")))
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        assertEquals(0, ledger.resolver.update(ledger.transactionUri(transaction),
                ledger.transaction(cash, groceries, 1000L).apply {
                    put(Contract.Transaction.SPLIT_PARTS, parts)
                }, null, null))
        assertEquals(1, ledger.resolver.update(ContentUris.withAppendedId(
                DataContentProvider.CONTENT_CATEGORIES, fuel), ContentValues().apply {
            put(Contract.Category.TYPE, Contract.CategoryType.INCOME.value)
        }, null, null))
        val rows = ledger.transactionRows(transaction)

        try {
            ledger.restoreTransaction(transaction)
            fail("a transaction was restored with a part in a category of the other direction")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.SPLIT_NOT_VALID, e.errorCode)
        }

        assertEquals(rows, ledger.transactionRows(transaction))
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L))
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(live),
                ledger.transaction(cash, groceries, 1000L).apply {
                    put(Contract.Transaction.SPLIT_PARTS, SplitPartsCodec.encode(listOf(
                            SplitPartsCodec.Part(household, 300L, "part 0"))))
                }, null, null))
        assertEquals(1L, ledger.count("transaction_splits", "_transaction = $live AND deleted = 0"))
    }

    @Test
    fun aDeletedTransferIsNotUpdated() {
        val transfer = ledger.insertTransfer(cash, bank, null, listOf(ann), listOf(ledger.insertAttachment("slip")), party)
        val live = ledger.insertTransfer(cash, bank, null, listOf(ann), listOf(ledger.insertAttachment("statement")), party)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val rows = transferTables()

        assertEquals(0, ledger.resolver.update(ledger.transferUri(transfer),
                ledger.transfer(cash, bank, cash, listOf(bob), emptyList(), party), null, null))

        assertEquals(rows, transferTables())
        assertEquals(1, ledger.resolver.update(ledger.transferUri(live),
                ledger.transfer(cash, bank, cash, listOf(bob), emptyList(), party), null, null))
        assertEquals(3, ledger.legsOf(live).size)
        assertEquals(0L, ledger.count("transfer_attachment", "_transfer = $live"))
    }

    @Test
    fun aFeeOfADeletedTransferIsNotUpdatedByItself() {
        val transfer = insertTransfer()
        val fee = ledger.legsOf(transfer)[2]
        val live = ledger.insertTransaction(ledger.transaction(cash, groceries, 400L))
        val values = ContentValues().apply {
            put(Contract.Transaction.WALLET_ID, bank)
        }
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        val rows = ledger.transferRows(transfer)

        assertEquals(0, ledger.resolver.update(ledger.transactionUri(fee), values, null, null))

        assertEquals(rows, ledger.transferRows(transfer))
        assertEquals(cash, ledger.rows("transactions", "transaction_id = $fee").single()["transaction_wallet"])
        assertEquals(1, ledger.resolver.update(ledger.transactionUri(live), values, null, null))
        assertEquals(bank, ledger.rows("transactions", "transaction_id = $live").single()["transaction_wallet"])
    }

    @Test
    fun aDeleteTimeAtOrBelowZeroOrNotANumberDeletesNothing() {
        val transaction = insertSplitTransaction()
        val transfer = insertTransfer()
        val rows = ledger.transactionRows(transaction) + ledger.transferRows(transfer)

        for (deletedAt in listOf("0", "-1", "-$DELETED_AT", "abc", "")) {
            assertEquals(0, ledger.resolver.delete(Uri.withAppendedPath(ledger.transactionUri(transaction), "deleted"),
                    null, arrayOf(deletedAt)))
            assertEquals(0, ledger.resolver.delete(Uri.withAppendedPath(ledger.transferUri(transfer), "deleted"),
                    null, arrayOf(deletedAt)))
        }

        assertEquals(rows, ledger.transactionRows(transaction) + ledger.transferRows(transfer))
    }

    @Test
    fun aDeleteTimeOfOneIsAccepted() {
        val transaction = insertSplitTransaction()
        val transfer = insertTransfer()

        assertEquals(1, ledger.flagTransactionDeleted(transaction, 1L))
        assertEquals(1, ledger.flagTransferDeleted(transfer, 1L))

        assertEquals(setOf<Any?>(1L), ledger.deletedOf(ledger.transactionRows(transaction) + ledger.transferRows(transfer)))
    }

    @Test
    fun aDeleteWithNoTimeOrAnEmptyListCarriesTheTimeOfTheDelete() {
        val transaction = insertSplitTransaction()
        val transfer = insertTransfer()
        val start = System.currentTimeMillis()

        assertEquals(1, ledger.resolver.delete(Uri.withAppendedPath(ledger.transactionUri(transaction), "deleted"),
                null, emptyArray()))
        assertEquals(1, ledger.flagTransferDeleted(transfer))

        val end = System.currentTimeMillis()
        for (rows in listOf(ledger.transactionRows(transaction), ledger.transferRows(transfer))) {
            assertTrue(ledger.deletedOf(rows).single() as Long in start..end)
        }
    }

    @Test
    fun aLegWhoseTransferIsGoneIsNotRestoredAndIsCleanedUp() {
        val transfer = ledger.insertTransfer(cash, bank, null, emptyList(), emptyList(), null)
        val legs = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        ledger.database().execSQL("DELETE FROM transfers WHERE transfer_id = $transfer")
        ledger.database().execSQL("DELETE FROM transactions WHERE transaction_id = ${legs[1]}")
        ledger.database().execSQL("INSERT INTO transaction_people (_transaction, _person, uuid, last_edit, deleted) " +
                "VALUES (?, ?, 'leg_person', 1, ?)", arrayOf<Any>(legs[0], ann, DELETED_AT))
        val rows = ledger.transactionRows(legs[0])
        assertEquals(Contract.TransactionType.TRANSFER.toLong(), rows.first()["transaction_type"])
        assertEquals(2, rows.size)

        try {
            ledger.restoreTransaction(legs[0])
            fail("half of a transfer that no longer exists was restored")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER, e.errorCode)
        }

        assertEquals(rows, ledger.transactionRows(legs[0]))
        assertEquals(1, ledger.deleteFlaggedItems(DELETED_AT + 1))
        assertEquals(emptyList<Map<String, Any?>>(), ledger.transactionRows(legs[0]))
        assertEquals(0L, ledger.count("transaction_people", "_transaction = ${legs[0]}"))
    }

    @Test
    fun aDeletedTransactionWhosePartTheDecimalsCutBroughtToZeroIsRestored() {
        val transaction = ledger.insertTransaction(ledger.transaction(cash, groceries, 1000L).apply {
            put(Contract.Transaction.SPLIT_PARTS, SplitPartsCodec.encode(listOf(SplitPartsCodec.Part(household, 950L, "part 0"),
                    SplitPartsCodec.Part(fuel, 50L, "part 1"))))
        })
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        assertEquals(listOf(1000L, 950L, 50L), amounts(transaction))
        assertEquals(1, ledger.resolver.update(Uri.withAppendedPath(DataContentProvider.CONTENT_CURRENCIES, "EUR"),
                ContentValues().apply {
                    put(Contract.Currency.DECIMALS, 0)
                    put(Contract.Currency.FIX_MONEY_DECIMALS, true)
                }, null, null))
        assertEquals(listOf(10L, 9L, 0L), amounts(transaction))

        assertEquals(1, ledger.restoreTransaction(transaction))

        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(transaction)))
        assertEquals(listOf(9L, 0L), ledger.rows("transaction_splits", "_transaction = $transaction AND deleted = 0")
                .map { it["split_money"] })
        assertEquals(2, ledger.view(Uri.withAppendedPath(ledger.transactionUri(transaction), "splits")).size)
    }

    @Test
    fun aDeletedTransactionWhosePartsAddUpToMoreThanItsTotalIsRestored() {
        val transaction = insertSplitTransaction()
        ledger.flagTransactionDeleted(transaction, DELETED_AT)
        ledger.database().execSQL("UPDATE transaction_splits SET split_money = 900 WHERE _transaction = $transaction")
        assertEquals(listOf(1000L, 900L, 900L), amounts(transaction))

        assertEquals(1, ledger.restoreTransaction(transaction))

        assertEquals(setOf<Any?>(0L), ledger.deletedOf(ledger.transactionRows(transaction)))
        assertEquals(listOf(1000L, 900L, 900L), amounts(transaction))
    }

    @Test
    fun aDeletedTransactionListsItsPeopleAsALiveOneDoes() {
        val transaction = insertSplitTransaction()
        val live = insertSplitTransaction()
        ledger.flagTransactionDeleted(transaction, DELETED_AT)

        assertEquals(setOf(ann, bob), ledger.idsOf(ledger.viewValue(Uri.withAppendedPath(
                DataContentProvider.CONTENT_TRANSACTIONS, "deleted"), Contract.Transaction.PEOPLE_IDS,
                Contract.Transaction.ID + " = ?", transaction.toString())))
        assertEquals(setOf(ann, bob), ledger.idsOf(ledger.viewValue(DataContentProvider.CONTENT_TRANSACTIONS,
                Contract.Transaction.PEOPLE_IDS, Contract.Transaction.ID + " = ?", live.toString())))
    }

    private fun insertSplitTransaction(): Long = ledger.insertSplitTransaction(cash, groceries, listOf(household, fuel),
            listOf(ann, bob), listOf(ledger.insertAttachment("receipt"), ledger.insertAttachment("invoice")), party)

    private fun insertTransfer(): Long = ledger.insertTransfer(cash, bank, cash, listOf(ann, bob),
            listOf(ledger.insertAttachment("slip"), ledger.insertAttachment("statement")), party)

    private fun insertWithAttachment(attachment: Long): Long =
            ledger.insertTransaction(ledger.transaction(cash, groceries, 400L).apply {
                put(Contract.Transaction.ATTACHMENT_IDS, "<$attachment>")
            })

    private fun insertPayment(wallet: Long): Long = ledger.insertTransaction(
            ledger.transaction(wallet, ledger.systemCategory(Contract.CategoryTag.PAID_DEBT), 500L).apply {
                put(Contract.Transaction.TYPE, Contract.TransactionType.DEBT)
                put(Contract.Transaction.DEBT_ID, debt)
            })

    private fun insertDeposit(): Long = ledger.insertTransaction(
            ledger.transaction(cash, ledger.systemCategory(Contract.CategoryTag.SAVING_DEPOSIT), 800L).apply {
                put(Contract.Transaction.TYPE, Contract.TransactionType.SAVING)
                put(Contract.Transaction.SAVING_ID, saving)
            })

    private fun updateSaving(wallet: Long): Int = ledger.resolver.update(ContentUris.withAppendedId(
            DataContentProvider.CONTENT_SAVINGS, saving), ContentValues().apply {
        put(Contract.Saving.WALLET_ID, wallet)
    }, null, null)

    private fun assertDepositIsNotRestored(deposit: Long) {
        val rows = ledger.transactionRows(deposit)
        val shown = ledger.view(DataContentProvider.CONTENT_SAVINGS, Contract.Saving.ID + " = ?", saving.toString())

        try {
            ledger.restoreTransaction(deposit)
            fail("a deposit was restored into a saving read in another currency")
        } catch (e: SQLiteDataException) {
            assertEquals(Contract.ErrorCode.WALLETS_NOT_CONSISTENT, e.errorCode)
        }

        assertEquals(setOf<Any?>(DELETED_AT), ledger.deletedOf(rows))
        assertEquals(rows, ledger.transactionRows(deposit))
        assertEquals(shown, ledger.view(DataContentProvider.CONTENT_SAVINGS, Contract.Saving.ID + " = ?", saving.toString()))
    }

    private fun transactionTables(): List<Map<String, Any?>> = listOf("transactions", "transaction_people",
            "transaction_splits", "transaction_attachment", "attachments").flatMap { ledger.rows(it, "1") }

    private fun transferTables(): List<Map<String, Any?>> = listOf("transfers", "transfer_people",
            "transfer_attachment").flatMap { ledger.rows(it, "1") } + transactionTables()

    private fun amounts(transaction: Long): List<Any?> =
            ledger.rows("transactions", "transaction_id = $transaction").map { it["transaction_money"] } +
                    ledger.rows("transaction_splits", "_transaction = $transaction").map { it["split_money"] }

    private fun announcedBy(action: () -> Unit): List<Uri?> {
        val announced = mutableListOf<Uri?>()
        val observer = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                announced.add(uri)
            }
        }
        ledger.resolver.registerContentObserver(DataContentProvider.CONTENT_TRANSACTIONS, false, observer)
        ledger.resolver.registerContentObserver(DataContentProvider.CONTENT_TRANSFERS, false, observer)
        try {
            action()
        } finally {
            ledger.resolver.unregisterContentObserver(observer)
        }
        return announced
    }

    private fun attachmentDeleted(attachment: Long): Any? =
            ledger.rows("attachments", "attachment_id = $attachment").single()["deleted"]

    private fun totals(): Map<String, Any?> = mapOf(
            "cash" to ledger.viewValue(DataContentProvider.CONTENT_WALLETS, Contract.Wallet.TOTAL_MONEY,
                    Contract.Wallet.ID + " = ?", cash.toString()),
            "bank" to ledger.viewValue(DataContentProvider.CONTENT_WALLETS, Contract.Wallet.TOTAL_MONEY,
                    Contract.Wallet.ID + " = ?", bank.toString()),
            "budget" to ledger.viewValue(DataContentProvider.CONTENT_BUDGETS, Contract.Budget.PROGRESS,
                    Contract.Budget.ID + " = ?", budget.toString()),
            "debt" to ledger.viewValue(DataContentProvider.CONTENT_DEBTS, Contract.Debt.PROGRESS,
                    Contract.Debt.ID + " = ?", debt.toString()),
            "saving" to ledger.viewValue(DataContentProvider.CONTENT_SAVINGS, Contract.Saving.PROGRESS,
                    Contract.Saving.ID + " = ?", saving.toString()),
            "event" to ledger.viewValue(DataContentProvider.CONTENT_EVENTS, Contract.Event.PROGRESS,
                    Contract.Event.ID + " = ?", party.toString()),
            "transactions" to ledger.view(DataContentProvider.CONTENT_TRANSACTIONS),
            "transfers" to ledger.view(DataContentProvider.CONTENT_TRANSFERS),
            "lines" to ledger.view(DataContentProvider.CONTENT_CATEGORY_LINES))

    private fun itemViews(transaction: Long): Map<String, List<Map<String, Any?>>> {
        val uri = ledger.transactionUri(transaction)
        return mapOf(
                "detail" to ledger.view(uri),
                "people" to ledger.view(Uri.withAppendedPath(uri, "people")),
                "attachments" to ledger.view(Uri.withAppendedPath(uri, "attachments")),
                "splits" to ledger.view(Uri.withAppendedPath(uri, "splits")),
                "lines" to ledger.view(DataContentProvider.CONTENT_CATEGORY_LINES,
                        Contract.Transaction.ID + " = ?", transaction.toString()))
    }

    private fun transferViews(transfer: Long): Map<String, List<Map<String, Any?>>> {
        val uri = ledger.transferUri(transfer)
        return mapOf(
                "detail" to ledger.view(uri),
                "people" to ledger.view(Uri.withAppendedPath(uri, "people")),
                "attachments" to ledger.view(Uri.withAppendedPath(uri, "attachments")))
    }
}
