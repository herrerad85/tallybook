package com.oriondev.moneywallet.broadcast

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.database.Cursor
import android.os.Bundle
import android.util.Log

import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity
import com.oriondev.moneywallet.utils.DateUtils

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowBroadcastPendingResult
import org.robolectric.shadows.ShadowLog
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

@RunWith(RobolectricTestRunner::class)
class AddTransactionReceiverTest {

    companion object {
        private const val ACTION = "io.github.herrerad85.tallybook.action.SAVE_TRANSACTION"
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver
    private lateinit var mCode: String
    private var mCashWallet: Long = 0
    private var mBankWallet: Long = 0
    private lateinit var mTwinWallets: LongArray
    private var mSalary: Long = 0
    private var mSnacks: Long = 0

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        PreferenceManager.initialize(mContext)
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
        mCashWallet = insertWallet("Cash", "EUR")
        mBankWallet = insertWallet("Bank", "JPY")
        mTwinWallets = longArrayOf(insertWallet("Twin", "EUR"), insertWallet("Twin", "EUR"))
        mSalary = insertCategory("Payroll", Contract.CategoryType.INCOME)
        mSnacks = insertCategory("Snacks", Contract.CategoryType.EXPENSE)
        insertRule("coffee", mSnacks)
        PreferenceManager.setDefaultWallet(mCashWallet)
        mCode = AddTransactionReceiver.newCode()
        PreferenceManager.setAutomationCode(mCode)
        PreferenceManager.setAutomationSaveEnabled(true)
    }

    @Test
    fun aCodeIsThirtyTwoLowercaseHexCharacters() {
        assertTrue(AddTransactionReceiver.newCode().matches(Regex("[0-9a-f]{32}")))
    }

    @Test
    fun aNewCodeDiffersFromTheLastOne() {
        assertNotEquals(AddTransactionReceiver.newCode(), AddTransactionReceiver.newCode())
    }

    @Test
    fun aBroadcastWithNoCategoryOrTheDefaultOneReachesTheReceiver() {
        val plain = Intent(ACTION).setPackage(mContext.packageName)
        val withDefault = Intent(ACTION).setPackage(mContext.packageName)
                .addCategory(Intent.CATEGORY_DEFAULT)
        for (intent in arrayOf(plain, withDefault)) {
            val receivers: List<ResolveInfo> = mContext.packageManager.queryBroadcastReceivers(intent, 0)
            assertEquals(1L, receivers.size.toLong())
            assertEquals(AddTransactionReceiver::class.java.name, receivers[0].activityInfo.name)
        }
    }

    @Test
    fun theReceiverIsExportedSoAnAutomationAppCanReachIt() {
        assertTrue(mContext.packageManager.getReceiverInfo(
                ComponentName(mContext, AddTransactionReceiver::class.java), 0).exported)
    }

    @Test
    fun theRightCodeSavesOneUnconfirmedRowWithEveryField() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "cash")
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "-12.346")
        intent.putExtra(NewEditTransactionActivity.EXTRA_CATEGORY, "payroll")
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Bonus")
        intent.putExtra(NewEditTransactionActivity.EXTRA_NOTE, "March")
        val before = System.currentTimeMillis()
        receive(intent)
        assertEquals(1L, countTransactions().toLong())
        onlyTransaction().use { cursor ->
            assertEquals(mCashWallet, longOf(cursor, Contract.Transaction.WALLET_ID))
            assertEquals(1235L, longOf(cursor, Contract.Transaction.MONEY))
            assertEquals(Contract.Direction.INCOME.toLong(), longOf(cursor, Contract.Transaction.DIRECTION))
            assertEquals(mSalary, longOf(cursor, Contract.Transaction.CATEGORY_ID))
            assertEquals(Contract.TransactionType.STANDARD.toLong(), longOf(cursor, Contract.Transaction.TYPE))
            assertEquals("Bonus", stringOf(cursor, Contract.Transaction.DESCRIPTION))
            assertEquals("March", stringOf(cursor, Contract.Transaction.NOTE))
            assertEquals(0L, longOf(cursor, Contract.Transaction.CONFIRMED))
            assertEquals(1L, longOf(cursor, Contract.Transaction.COUNT_IN_TOTAL))
            val date = DateUtils.getDateFromSQLDateTimeString(stringOf(cursor, Contract.Transaction.DATE)).time
            assertTrue(date >= before - 2000L && date <= System.currentTimeMillis() + 2000L)
        }
    }

    @Test
    fun theAmountIsRoundedToTheWalletsOwnDecimals() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "Bank")
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "1234.6")
        receive(intent)
        onlyTransaction().use { cursor ->
            assertEquals(mBankWallet, longOf(cursor, Contract.Transaction.WALLET_ID))
            assertEquals(1235L, longOf(cursor, Contract.Transaction.MONEY))
        }
    }

    @Test
    fun aDescriptionARuleMatchesFillsTheCategory() {
        receive(request())
        onlyTransaction().use { cursor ->
            assertEquals(mSnacks, longOf(cursor, Contract.Transaction.CATEGORY_ID))
            assertEquals(Contract.Direction.EXPENSE.toLong(), longOf(cursor, Contract.Transaction.DIRECTION))
        }
    }

    @Test
    fun aCategoryNameNoCategoryCarriesFallsBackToTheRules() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_CATEGORY, "Nothing")
        receive(intent)
        onlyTransaction().use { cursor ->
            assertEquals(mSnacks, longOf(cursor, Contract.Transaction.CATEGORY_ID))
        }
    }

    @Test
    fun anAmbiguousWalletNameTakesTheDefaultWallet() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "Twin")
        PreferenceManager.setDefaultWallet(mBankWallet)
        receive(intent)
        onlyTransaction().use { cursor ->
            assertEquals(mBankWallet, longOf(cursor, Contract.Transaction.WALLET_ID))
        }
    }

    @Test
    fun withNoDefaultWalletTheFirstWalletByIndexAndNameIsTaken() {
        PreferenceManager.setDefaultWallet(PreferenceManager.NO_DEFAULT_WALLET)
        receive(request())
        onlyTransaction().use { cursor ->
            assertEquals(mBankWallet, longOf(cursor, Contract.Transaction.WALLET_ID))
        }
    }

    @Test
    fun noWalletThatIsNotArchivedSavesNothing() {
        archiveWallet(mCashWallet)
        archiveWallet(mBankWallet)
        for (wallet in mTwinWallets) {
            archiveWallet(wallet)
        }
        receive(request())
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun aWalletInACurrencyTheAppDoesNotKnowSavesNothing() {
        insertWallet("Odd", "ZZZ")
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_WALLET, "Odd")
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun aWrongCodeSavesNothing() {
        val intent = request()
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, AddTransactionReceiver.newCode())
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun aMissingCodeSavesNothing() {
        val intent = request()
        intent.removeExtra(AddTransactionReceiver.EXTRA_CODE)
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun anEmptyCodeSavesNothing() {
        val intent = request()
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, "")
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun theStoredCodeWithoutItsLastCharacterSavesNothing() {
        val intent = request()
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, mCode.substring(0, mCode.length - 1))
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun aRefusalLogsOneWarningAndNoLogHoldsTheCodeOrTheText() {
        val wrongCode = AddTransactionReceiver.newCode()
        val wrong = request()
        wrong.putExtra(AddTransactionReceiver.EXTRA_CODE, wrongCode)
        receive(wrong)
        val noCategory = request()
        noCategory.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Rent")
        receive(noCategory)
        assertEquals(0L, countTransactions().toLong())
        val saved = request()
        saved.putExtra(NewEditTransactionActivity.EXTRA_CATEGORY, "payroll")
        saved.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Bonus qx7")
        saved.putExtra(NewEditTransactionActivity.EXTRA_NOTE, "March qx9")
        receive(saved)
        assertEquals(1L, countTransactions().toLong())
        var warnings = 0
        for (item in ShadowLog.getLogs()) {
            val text = java.lang.String.valueOf(item.msg)
            assertFalse(text, text.contains(mCode))
            assertFalse(text, text.contains(wrongCode))
            assertFalse(text, text.contains("qx7"))
            assertFalse(text, text.contains("qx9"))
            if ("AddTransactionReceiver" == item.tag && item.type == Log.WARN) {
                warnings++
            }
        }
        assertEquals(2L, warnings.toLong())
    }

    @Test
    fun theSwitchOffSavesNothingEvenWithACodeLeftOver() {
        PreferenceManager.setAutomationSaveEnabled(false)
        receive(request())
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun theSwitchOnWithNoStoredCodeSavesNothing() {
        PreferenceManager.setAutomationCode(null)
        val intent = request()
        intent.removeExtra(AddTransactionReceiver.EXTRA_CODE)
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun theCodeFromBeforeNewCodeSavesNothing() {
        PreferenceManager.setAutomationCode(AddTransactionReceiver.newCode())
        receive(request())
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun noCategoryAndNoRuleMatchSavesNothing() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Rent")
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun noCategoryAndNoDescriptionSavesNothing() {
        insertRule("ul", mSalary)
        val intent = request()
        intent.removeExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION)
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun aZeroAmountSavesNothing() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "0.004")
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun anUnreadableAmountSavesNothing() {
        val intent = request()
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "twelve")
        receive(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    @Test
    fun aSaveLeavesTheOrderedResultUntouched() {
        assertResultUntouched(request())
        assertEquals(1L, countTransactions().toLong())
    }

    @Test
    fun aRefusalLeavesTheOrderedResultUntouched() {
        val intent = request()
        intent.removeExtra(AddTransactionReceiver.EXTRA_CODE)
        assertResultUntouched(intent)
        assertEquals(0L, countTransactions().toLong())
    }

    private fun assertResultUntouched(intent: Intent) {
        val extras = Bundle()
        extras.putString("caller", "kept")
        val pendingResult = ReflectionHelpers.callStaticMethod<BroadcastReceiver.PendingResult>(
                ShadowBroadcastPendingResult::class.java, "create",
                ClassParameter.from(Int::class.javaPrimitiveType, 7),
                ClassParameter.from(String::class.java, "data"),
                ClassParameter.from(Bundle::class.java, extras),
                ClassParameter.from(Boolean::class.javaPrimitiveType, true))
        val receiver = AddTransactionReceiver()
        ReflectionHelpers.callInstanceMethod<Any>(receiver, "setPendingResult",
                ClassParameter.from(BroadcastReceiver.PendingResult::class.java, pendingResult))
        receiver.onReceive(mContext, intent)
        assertEquals(7L, receiver.resultCode.toLong())
        assertEquals("data", receiver.resultData)
        assertSame(extras, receiver.getResultExtras(false))
        assertEquals(1L, extras.size().toLong())
        assertEquals("kept", extras.getString("caller"))
    }

    private fun request(): Intent {
        val intent = Intent(ACTION)
        intent.putExtra(AddTransactionReceiver.EXTRA_CODE, mCode)
        intent.putExtra(NewEditTransactionActivity.EXTRA_AMOUNT, "4.20")
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Coffee")
        return intent
    }

    private fun receive(intent: Intent) {
        AddTransactionReceiver().onReceive(mContext, intent)
    }

    private fun countTransactions(): Int {
        mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                arrayOf(Contract.Transaction.ID), null, null, null).use { cursor ->
            return cursor!!.count
        }
    }

    private fun onlyTransaction(): Cursor {
        assertEquals(1L, countTransactions().toLong())
        val cursor = mResolver.query(DataContentProvider.CONTENT_TRANSACTIONS, arrayOf(
                Contract.Transaction.ID, Contract.Transaction.WALLET_ID, Contract.Transaction.MONEY,
                Contract.Transaction.DIRECTION, Contract.Transaction.CATEGORY_ID,
                Contract.Transaction.TYPE, Contract.Transaction.DESCRIPTION, Contract.Transaction.NOTE,
                Contract.Transaction.CONFIRMED, Contract.Transaction.COUNT_IN_TOTAL,
                Contract.Transaction.DATE), null, null, null)
        assertTrue(cursor!!.moveToFirst())
        return cursor
    }

    private fun longOf(cursor: Cursor, column: String): Long {
        return cursor.getLong(cursor.getColumnIndexOrThrow(column))
    }

    private fun stringOf(cursor: Cursor, column: String): String? {
        return cursor.getString(cursor.getColumnIndexOrThrow(column))
    }

    private fun insertWallet(name: String, currency: String): Long {
        val values = ContentValues()
        values.put(Contract.Wallet.NAME, name)
        values.put(Contract.Wallet.ICON, ICON)
        values.put(Contract.Wallet.CURRENCY, currency)
        values.put(Contract.Wallet.START_MONEY, 0L)
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true)
        values.put(Contract.Wallet.ARCHIVED, false)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values)!!)
    }

    private fun archiveWallet(wallet: Long) {
        val values = ContentValues()
        values.put(Contract.Wallet.ARCHIVED, true)
        mResolver.update(ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, wallet),
                values, null, null)
    }

    private fun insertCategory(name: String, type: Contract.CategoryType): Long {
        val category = ContentValues()
        category.put(Contract.Category.NAME, name)
        category.put(Contract.Category.ICON, ICON)
        category.put(Contract.Category.TYPE, type.getValue())
        category.put(Contract.Category.SHOW_REPORT, true)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category)!!)
    }

    private fun insertRule(pattern: String, category: Long) {
        val rule = ContentValues()
        rule.put(Contract.CategoryRule.PATTERN, pattern)
        rule.put(Contract.CategoryRule.CATEGORY_ID, category)
        mResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, rule)
    }
}
