package com.oriondev.moneywallet.storage.database

import android.content.ContentUris
import android.content.Context
import android.net.Uri

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.preference.PreferenceManager

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class TransferWalletNameTest {

    companion object {

        private const val DELETED_AT = 1765000000000L
    }

    private lateinit var ledger: DeletedLedger
    private var cash = 0L
    private var savings = 0L
    private var groceries = 0L
    private var salary = 0L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        PreferenceManager.initialize(context)
        TestDatabases.useFreshDatabase(context)
        ledger = DeletedLedger(context)
        cash = ledger.insertWallet("Cash", "EUR")
        savings = ledger.insertWallet("Savings", "EUR")
        groceries = ledger.insertCategory("Groceries", Contract.CategoryType.EXPENSE)
        salary = ledger.insertCategory("Salary", Contract.CategoryType.INCOME)
    }

    @Test
    fun eachLegOfATransferCarriesTheWalletOfTheOtherLeg() {
        val expense = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val income = ledger.insertTransaction(ledger.transaction(cash, salary, 200L).apply {
            put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME)
        })
        val (from, to, tax) = ledger.legsOf(ledger.insertTransfer(cash, savings, cash, emptyList(), emptyList(), null))

        val names = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(expense to null, income to null, from to "Savings", to to "Cash", tax to null), names)
        assertEquals("Savings", itemOf(from)[Contract.Transaction.TRANSFER_WALLET_NAME])
        assertEquals("Cash", itemOf(to)[Contract.Transaction.TRANSFER_WALLET_NAME])
        assertNull(itemOf(tax)[Contract.Transaction.TRANSFER_WALLET_NAME])
        assertNull(itemOf(expense)[Contract.Transaction.TRANSFER_WALLET_NAME])
        assertNull(itemOf(income)[Contract.Transaction.TRANSFER_WALLET_NAME])
    }

    @Test
    fun eachLegOfATransferCarriesTheDirectionItHasInTheTransfer() {
        val expense = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val (from, to, tax) = ledger.legsOf(ledger.insertTransfer(cash, savings, cash, emptyList(), emptyList(), null))

        val directions = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_DIRECTION]
        }

        assertEquals(mapOf(expense to null, from to Contract.Direction.EXPENSE.toLong(),
                to to Contract.Direction.INCOME.toLong(), tax to null), directions)
        assertEquals(Contract.Direction.INCOME.toLong(), itemOf(to)[Contract.Transaction.TRANSFER_DIRECTION])
    }

    @Test
    fun anArchivedWalletStillGivesItsName() {
        val from = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))[0]
        ledger.database().execSQL("UPDATE wallets SET wallet_archived = 1 WHERE wallet_id = $savings")

        assertEquals("Savings", itemOf(from)[Contract.Transaction.TRANSFER_WALLET_NAME])
    }

    @Test
    fun peopleOnALegKeepItOneRow() {
        val from = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))[0]
        val anna = ledger.insertPerson("Anna")
        val bruno = ledger.insertPerson("Bruno")
        for (person in listOf(anna, bruno)) {
            ledger.database().execSQL("INSERT INTO transaction_people (_transaction, _person, uuid, last_edit, deleted) " +
                    "VALUES ($from, $person, 'leg_person_$person', 1, 0)")
        }

        val rows = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS, "${Contract.Transaction.ID} = ?", from.toString())

        assertEquals(1, rows.size)
        assertEquals("Savings", rows[0][Contract.Transaction.TRANSFER_WALLET_NAME])
        assertEquals(setOf(anna, bruno), ledger.idsOf(rows[0][Contract.Transaction.PEOPLE_IDS]))
        assertEquals(2, (rows[0][Contract.Transaction.PEOPLE_IDS] as String).count { it == '<' })
    }

    @Test
    fun theLegsOfADeletedTransferCarryTheWalletOfTheOtherLeg() {
        val transfer = ledger.insertTransfer(cash, savings, cash, emptyList(), emptyList(), null)
        val (from, to, tax) = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)

        val names = ledger.view(Uri.withAppendedPath(DataContentProvider.CONTENT_TRANSACTIONS, "deleted")).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(from to "Savings", to to "Cash", tax to null), names)
    }

    @Test
    fun theLegsOfADeletedTransferWhoseRowWasFlaggedAtAnotherTimeCarryNoWallet() {
        val transfer = ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null)
        val (from, to) = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        ledger.database().execSQL("UPDATE transfers SET deleted = ${DELETED_AT + 1} WHERE transfer_id = $transfer")

        val names = ledger.view(Uri.withAppendedPath(DataContentProvider.CONTENT_TRANSACTIONS, "deleted")).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(from to null, to to null), names)
    }

    @Test
    fun aDeletedLegWhoseOtherLegWasFlaggedAtAnotherTimeCarriesNoWallet() {
        val transfer = ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null)
        val (from, to) = ledger.legsOf(transfer)
        ledger.flagTransferDeleted(transfer, DELETED_AT)
        ledger.database().execSQL("UPDATE transactions SET deleted = ${DELETED_AT + 1} WHERE transaction_id = $to")

        val names = ledger.view(Uri.withAppendedPath(DataContentProvider.CONTENT_TRANSACTIONS, "deleted")).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(from to null, to to null), names)
    }

    @Test
    fun categoryLinesAndSharesCarryTheWalletOfTheOtherLeg() {
        val expense = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val (from, to) = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))

        for (uri in listOf(DataContentProvider.CONTENT_CATEGORY_LINES, DataContentProvider.CONTENT_CATEGORY_SHARES)) {
            val names = ledger.view(uri).associate {
                it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
            }

            assertEquals(mapOf(expense to null, from to "Savings", to to "Cash"), names)
        }
    }

    @Test
    fun aWalletBudgetListsTheLegLeavingItWithTheWalletItGoesTo() {
        val expense = ledger.insertTransaction(ledger.transaction(cash, groceries, 100L))
        val from = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))[0]

        val names = ledger.view(budgetTransactions(Schema.BudgetType.EXPENSES, cash)).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(expense to null, from to "Savings"), names)
    }

    @Test
    fun eachLegKeepsTheDirectionItHasInTheTransferAndNotTheOneItStores() {
        val (from, to) = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))
        ledger.database().execSQL("UPDATE transactions SET transaction_direction = ${Contract.Direction.INCOME} " +
                "WHERE transaction_id = $from")
        ledger.database().execSQL("UPDATE transactions SET transaction_direction = ${Contract.Direction.EXPENSE} " +
                "WHERE transaction_id = $to")

        val rows = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associateBy { it[Contract.Transaction.ID] }
        val fromRows = ledger.view(budgetTransactions(Schema.BudgetType.INCOMES, cash))
        val toRows = ledger.view(budgetTransactions(Schema.BudgetType.EXPENSES, savings))

        assertEquals(Contract.Direction.INCOME.toLong(), rows.getValue(from)[Contract.Transaction.DIRECTION])
        assertEquals(Contract.Direction.EXPENSE.toLong(), rows.getValue(to)[Contract.Transaction.DIRECTION])
        for (row in listOf(rows.getValue(from), fromRows.single())) {
            assertEquals(from, row[Contract.Transaction.ID])
            assertEquals(Contract.Direction.EXPENSE.toLong(), row[Contract.Transaction.TRANSFER_DIRECTION])
            assertEquals("Savings", row[Contract.Transaction.TRANSFER_WALLET_NAME])
        }
        for (row in listOf(rows.getValue(to), toRows.single())) {
            assertEquals(to, row[Contract.Transaction.ID])
            assertEquals(Contract.Direction.INCOME.toLong(), row[Contract.Transaction.TRANSFER_DIRECTION])
            assertEquals("Cash", row[Contract.Transaction.TRANSFER_WALLET_NAME])
        }
    }

    @Test
    fun theLegsOfATransferRowFlaggedDeletedCarryNoWallet() {
        val transfer = ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null)
        val (from, to) = ledger.legsOf(transfer)
        ledger.database().execSQL("UPDATE transfers SET deleted = 1 WHERE transfer_id = $transfer")

        val names = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }
        val budgetNames = ledger.view(budgetTransactions(Schema.BudgetType.EXPENSES, cash)).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(from to null, to to null), names)
        assertEquals(mapOf(from to null), budgetNames)
    }

    @Test
    fun anIncomesBudgetListsTheLegOfATransferRowFlaggedDeletedWithNoWallet() {
        val transfer = ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null)
        val to = ledger.legsOf(transfer)[1]
        val budget = budgetTransactions(Schema.BudgetType.INCOMES, savings)
        val before = ledger.view(budget).single()
        ledger.database().execSQL("UPDATE transfers SET deleted = 1 WHERE transfer_id = $transfer")

        val after = ledger.view(budget).single()

        assertEquals(to, before[Contract.Transaction.ID])
        assertEquals("Cash", before[Contract.Transaction.TRANSFER_WALLET_NAME])
        assertEquals(Contract.Direction.INCOME.toLong(), before[Contract.Transaction.TRANSFER_DIRECTION])
        assertEquals(to, after[Contract.Transaction.ID])
        assertNull(after[Contract.Transaction.TRANSFER_WALLET_NAME])
        assertNull(after[Contract.Transaction.TRANSFER_DIRECTION])
    }

    @Test
    fun aLegWhoseOtherLegIsFlaggedDeletedCarriesNoWallet() {
        val (from, to) = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))
        ledger.database().execSQL("UPDATE transactions SET deleted = $DELETED_AT WHERE transaction_id = $to")

        val names = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }
        val budgetNames = ledger.view(budgetTransactions(Schema.BudgetType.EXPENSES, cash)).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(from to null), names)
        assertEquals(mapOf(from to null), budgetNames)
    }

    @Test
    fun aLegWhoseOtherLegIsInAWalletFlaggedDeletedCarriesNoWallet() {
        val (from, to) = ledger.legsOf(ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null))
        val budget = budgetTransactions(Schema.BudgetType.EXPENSES, cash)
        val before = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }
        val budgetBefore = ledger.view(budget).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }
        ledger.database().execSQL("UPDATE wallets SET deleted = 1 WHERE wallet_id = $savings")

        val names = ledger.view(DataContentProvider.CONTENT_TRANSACTIONS).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }
        val budgetNames = ledger.view(budget).associate {
            it[Contract.Transaction.ID] to it[Contract.Transaction.TRANSFER_WALLET_NAME]
        }

        assertEquals(mapOf(from to "Savings", to to "Cash"), before)
        assertEquals(mapOf(from to "Savings"), budgetBefore)
        assertEquals(mapOf(from to null), names)
        assertEquals(mapOf(from to null), budgetNames)
    }

    @Test
    fun aSecondTransferRowNamingTheSameLegsKeepsThePeopleOfALegOnce() {
        val transfer = ledger.insertTransfer(cash, savings, null, emptyList(), emptyList(), null)
        val from = ledger.legsOf(transfer)[0]
        val anna = ledger.insertPerson("Anna")
        ledger.database().execSQL("INSERT INTO transaction_people (_transaction, _person, uuid, last_edit, deleted) " +
                "VALUES ($from, $anna, 'leg_person_$anna', 1, 0)")
        val uris = listOf(DataContentProvider.CONTENT_TRANSACTIONS, DataContentProvider.CONTENT_CATEGORY_LINES,
                DataContentProvider.CONTENT_CATEGORY_SHARES, budgetTransactions(Schema.BudgetType.EXPENSES, cash))
        val before = uris.associateWith {
            ledger.view(it, "${Contract.Transaction.ID} = ?", from.toString()).single()[Contract.Transaction.PEOPLE_IDS]
        }
        ledger.database().execSQL("INSERT INTO transfers (transfer_description, transfer_date, transfer_transaction_from, " +
                "transfer_transaction_to, uuid, last_edit, deleted) SELECT transfer_description, transfer_date, " +
                "transfer_transaction_from, transfer_transaction_to, 'transfer_copy', 1, 0 FROM transfers " +
                "WHERE transfer_id = $transfer")

        val after = uris.associateWith { uri ->
            ledger.view(uri, "${Contract.Transaction.ID} = ?", from.toString()).map { it[Contract.Transaction.PEOPLE_IDS] }
        }

        assertEquals(2L, ledger.count("transfers", "transfer_transaction_from = $from AND deleted = 0"))
        assertEquals(uris.associateWith { "<$anna>" }, before)
        assertEquals(before.mapValues { listOf(it.value) }, after)
    }

    private fun budgetTransactions(type: Int, wallet: Long): Uri {
        ledger.database().execSQL("INSERT INTO budgets (budget_type, budget_category, budget_start_date, budget_end_date, " +
                "budget_money, budget_currency, uuid, last_edit, deleted) VALUES (?, NULL, ?, ?, 100000, 'EUR', " +
                "'budget_$wallet', 1, 0)", arrayOf<Any>(type,
                LocalDate.now().minusDays(10).toString(), LocalDate.now().plusDays(10).toString()))
        val budget = ledger.rows("budgets", "uuid = 'budget_$wallet'").single()["budget_id"] as Long
        ledger.database().execSQL("INSERT INTO budget_wallets (_budget, _wallet, uuid, last_edit, deleted) VALUES (?, ?, " +
                "'budget_wallet_$wallet', 1, 0)", arrayOf<Any>(budget, wallet))
        return Uri.withAppendedPath(ContentUris.withAppendedId(DataContentProvider.CONTENT_BUDGETS, budget), "transactions")
    }

    private fun itemOf(transaction: Long): Map<String, Any?> =
            ledger.view(ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transaction)).single()
}
