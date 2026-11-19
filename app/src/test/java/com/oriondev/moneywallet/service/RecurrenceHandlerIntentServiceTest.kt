package com.oriondev.moneywallet.service

import android.app.Notification
import android.app.NotificationManager
import android.app.job.JobScheduler
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri

import androidx.core.app.JobIntentService
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.LockMode
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SplitLedger
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.notification.NotificationContract
import com.oriondev.moneywallet.utils.DateUtils

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.util.ReflectionHelpers

import java.util.ArrayList
import java.util.Calendar
import java.util.Date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.robolectric.Shadows.shadowOf

/**
 * Runs the recurrence task against the real content provider over a fresh database. The provider
 * insert posts every row a recurrence has due when it is saved and moves its pointer past today,
 * so each recurrence here starts in the future and has its pointer moved back afterwards, which
 * is the state the task finds when a row comes due after the save.
 */
@RunWith(RobolectricTestRunner::class)
class RecurrenceHandlerIntentServiceTest {

    companion object {

        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"

        private const val DAY = 24L * 60L * 60L * 1000L
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver
    private lateinit var mNotifications: ShadowNotificationManager
    private var mWalletA: Long = 0
    private var mWalletB: Long = 0
    private var mCategory: Long = 0

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
        PreferenceManager.setCurrentLockMode(LockMode.NONE)
        val manager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancelAll()
        mNotifications = shadowOf(manager)
        mWalletA = insertWallet("A")
        mWalletB = insertWallet("B")
        mCategory = insertCategory("Groceries")
    }

    @After
    fun tearDown() {
        PreferenceManager.setCurrentLockMode(LockMode.NONE)
        (mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
    }

    @Test
    fun oneDueTransactionNotifiesOnceWithItsDescription() {
        makeDue(insertRecurrentTransaction("Rent"), 5, true)
        runTask()
        assertEquals(1, mNotifications.allNotifications.size)
        val notification = recurrenceNotification()
        assertEquals(NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE, notification.channelId)
        assertEquals(NotificationManager.IMPORTANCE_LOW, (mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .getNotificationChannel(NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE).importance)
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification))
        assertEquals("Rent", text(notification))
    }

    @Test
    fun aDueTransactionWithoutDescriptionShowsItsCategory() {
        makeDue(insertRecurrentTransaction(""), 5, true)
        runTask()
        assertEquals("Groceries", text(recurrenceNotification()))
    }

    @Test
    fun aDueTransferWithoutDescriptionShowsItsWallets() {
        makeDue(insertRecurrentTransfer(""), 5, false)
        runTask()
        val notification = recurrenceNotification()
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification))
        assertEquals("Transfer: A -> B", text(notification))
    }

    @Test
    fun twoDueRowsInOneRunShowTheCountAndNoLabel() {
        // weekly from ten days back is due ten and three days ago
        makeDue(insertRecurrentTransaction("Rent", "FREQ=WEEKLY"), 10, true)
        runTask()
        val notification = recurrenceNotification()
        assertEquals(plural(2), title(notification))
        assertNull(text(notification))
    }

    @Test
    fun aSecondRunAddsToTheNotificationStillShowing() {
        makeDue(insertRecurrentTransaction("Rent"), 5, true)
        runTask()
        assertEquals("Rent", text(recurrenceNotification()))
        makeDue(insertRecurrentTransaction("Gym"), 5, true)
        runTask()
        assertEquals(1, mNotifications.allNotifications.size)
        val notification = recurrenceNotification()
        assertEquals(plural(2), title(notification))
        assertNull(text(notification))
        makeDue(insertRecurrentTransaction("Phone"), 5, true)
        runTask()
        assertEquals(plural(3), title(recurrenceNotification()))
    }

    @Test
    fun oneServiceRunTwiceCountsOnlyWhatEachRunPosted() {
        // the system reuses one service instance for every piece of queued work
        val service = Robolectric.buildService(RecurrenceHandlerIntentService::class.java).create().get()
        makeDue(insertRecurrentTransaction("Rent"), 5, true)
        ReflectionHelpers.callInstanceMethod<Any>(service, "onHandleWork", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent()))
        val before = recurrenceNotification()
        assertEquals("Rent", text(before))
        ReflectionHelpers.callInstanceMethod<Any>(service, "onHandleWork", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent()))
        assertSame(before, recurrenceNotification())
    }

    @Test
    fun theLockHidesTheLabel() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN)
        makeDue(insertRecurrentTransaction("Rent"), 5, true)
        runTask()
        val notification = recurrenceNotification()
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification))
        assertNull(text(notification))
    }

    @Test
    fun withoutTheLockThePublicVersionCarriesNoLabel() {
        makeDue(insertRecurrentTransaction("Rent"), 5, true)
        runTask()
        val notification = recurrenceNotification()
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertEquals(mContext.getString(R.string.notification_title_recurrence_added), title(notification.publicVersion))
        assertNull(text(notification.publicVersion))
    }

    @Test
    fun nothingDueLeavesTheNotificationAlone() {
        runTask()
        assertTrue(mNotifications.allNotifications.isEmpty())
        makeDue(insertRecurrentTransaction("Rent"), 5, true)
        runTask()
        val before = recurrenceNotification()
        runTask()
        assertSame(before, recurrenceNotification())
        assertEquals(1, mNotifications.allNotifications.size)
    }

    @Test
    fun aDueBudgetPeriodAlonePostsNoNotification() {
        val start = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - 70L * DAY))
        val end = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - 40L * DAY))
        val values = ContentValues()
        values.put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.value)
        values.put(Contract.Budget.MONEY, 100000L)
        values.put(Contract.Budget.WALLET_IDS, "<" + mWalletA + ">")
        values.put(Contract.Budget.START_DATE, start)
        values.put(Contract.Budget.END_DATE, end)
        values.put(Contract.Budget.RULE, "FREQ=MONTHLY")
        values.put(Contract.Budget.RULE_START, start)
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, values)
        val before = countRows(DataContentProvider.CONTENT_BUDGETS)
        runTask()
        // the run did open a period, so the silence is not a run that did nothing
        assertTrue(countRows(DataContentProvider.CONTENT_BUDGETS) > before)
        assertTrue(mNotifications.allNotifications.isEmpty())
    }

    @Test
    fun aDueTransferThatNeedsARateIsHeldAndNotified() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        val id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY")
        makeDue(id, 3, false)
        val last = readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE)
        val next = readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE)
        val before = countRows(DataContentProvider.CONTENT_TRANSFERS)
        runTask()
        assertEquals(before, countRows(DataContentProvider.CONTENT_TRANSFERS))
        assertTrue(postedTransfers(dollars).isEmpty())
        assertEquals(last, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE))
        assertEquals(next, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE))
        assertNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE))
        assertEquals(1, mNotifications.allNotifications.size)
        assertEquals(23461, NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE)
        val notification = needsRateNotification()
        assertEquals(NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE, notification.channelId)
        assertEquals("A recurring transfer needs an exchange rate", title(notification))
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification))
        assertEquals("Savings", text(notification))
    }

    @Test
    fun aHeldTransferGivenARatePostsEveryHeldDateOnTheNextRun() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        val id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY")
        val daysAgo = 3
        makeDue(id, daysAgo, false)
        val next = readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE)
        runTask()
        assertTrue(postedTransfers(dollars).isEmpty())
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Savings")
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, dollars)
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, euros)
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L)
        values.put(Contract.RecurrentTransfer.MONEY_TO, 920L)
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L)
        values.put(Contract.RecurrentTransfer.CONFIRMED, true)
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true)
        values.put(Contract.RecurrentTransfer.START_DATE, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.START_DATE))
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=DAILY")
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null))
        assertEquals(next, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE))
        runTask()
        val expected = dailyRows(next, 920L)
        assertEquals(expected, postedTransfers(dollars))
        val today = DateUtils.getSQLDateString(Date())
        assertTrue(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE)!!.compareTo(today) > 0)
    }

    @Test
    fun twoTransfersThatNeedARateShowTheCountAndNoLabel() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false)
        makeDue(insertRecurrentTransfer("Rent", dollars, euros, 5000L, 0L, "FREQ=DAILY"), 3, false)
        runTask()
        val notification = needsRateNotification()
        assertEquals("2 recurring transfers need an exchange rate", title(notification))
        assertEquals(mContext.resources.getQuantityString(R.plurals.notification_title_recurrences_need_rate, 2, 2), title(notification))
        assertNull(text(notification))
    }

    @Test
    fun theLockHidesTheLabelOfATransferThatNeedsARate() {
        PreferenceManager.setCurrentLockMode(LockMode.PIN)
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false)
        runTask()
        val notification = needsRateNotification()
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification))
        assertNull(text(notification))
        if (notification.publicVersion != null) {
            assertNull(text(notification.publicVersion))
        }
    }

    @Test
    fun withoutTheLockThePublicVersionOfTheRateNotificationCarriesNoLabel() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false)
        runTask()
        val notification = needsRateNotification()
        assertEquals("Savings", text(notification))
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertNotNull(notification.publicVersion)
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification.publicVersion))
        assertNull(text(notification.publicVersion))
    }

    @Test
    fun aSameCurrencyTransferOfNothingStillPosts() {
        val id = insertRecurrentTransfer("Nothing", mWalletA, mWalletB, 0L, 0L, "FREQ=DAILY")
        assertDailyTransferPosts(id, mWalletA, 0L)
    }

    @Test
    fun aSameCurrencyTransferThatReceivesNothingStillPosts() {
        val id = insertRecurrentTransfer("Nothing back", mWalletA, mWalletB, 1000L, 0L, "FREQ=DAILY")
        assertDailyTransferPosts(id, mWalletA, 0L)
    }

    @Test
    fun aCrossCurrencyTransferOfNothingStillPosts() {
        val dollars = insertWallet("Dollars", "USD")
        val id = insertRecurrentTransfer("Nothing", dollars, insertWallet("Euros", "EUR"), 0L, 0L, "FREQ=DAILY")
        assertDailyTransferPosts(id, dollars, 0L)
    }

    @Test
    fun aCrossCurrencyTransferWithAnAmountReceivedStillPosts() {
        val dollars = insertWallet("Dollars", "USD")
        val id = insertRecurrentTransfer("Savings", dollars, insertWallet("Euros", "EUR"), 1000L, 920L, "FREQ=DAILY")
        assertDailyTransferPosts(id, dollars, 920L)
    }

    @Test
    fun aTransferThatNeedsARateDoesNotHoldTheOthers() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        val held = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY")
        val normal = insertRecurrentTransfer("Rent", mWalletA, mWalletB, 1000L, 1000L, "FREQ=DAILY")
        val daysAgo = 3
        makeDue(held, daysAgo, false)
        makeDue(normal, daysAgo, false)
        val heldLast = readString(recurrentTransferUri(held), Contract.RecurrentTransfer.LAST_OCCURRENCE)
        val heldNext = readString(recurrentTransferUri(held), Contract.RecurrentTransfer.NEXT_OCCURRENCE)
        val expected = dailyRows(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE), 1000L)
        runTask()
        assertEquals(expected, postedTransfers(mWalletA))
        val today = DateUtils.getSQLDateString(Date())
        assertTrue(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE)!!.compareTo(today) > 0)
        assertTrue(postedTransfers(dollars).isEmpty())
        assertEquals(heldLast, readString(recurrentTransferUri(held), Contract.RecurrentTransfer.LAST_OCCURRENCE))
        assertEquals(heldNext, readString(recurrentTransferUri(held), Contract.RecurrentTransfer.NEXT_OCCURRENCE))
        assertEquals(2, mNotifications.allNotifications.size)
        assertEquals(plural(expected.size), title(recurrenceNotification()))
        assertEquals("Savings", text(needsRateNotification()))
    }

    @Test
    fun aNormalTransferNextToOneThatNeedsARateStillPosts() {
        val held = insertRecurrentTransfer("Savings", insertWallet("Dollars", "USD"), insertWallet("Euros", "EUR"), 1000L, 0L, "FREQ=DAILY")
        val normal = insertRecurrentTransfer("Rent", mWalletA, mWalletB, 1000L, 1000L, "FREQ=DAILY")
        val daysAgo = 3
        makeDue(held, daysAgo, false)
        makeDue(normal, daysAgo, false)
        val expected = dailyRows(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE), 1000L)
        runTask()
        assertEquals(expected, postedTransfers(mWalletA))
        val today = DateUtils.getSQLDateString(Date())
        assertTrue(readString(recurrentTransferUri(normal), Contract.RecurrentTransfer.NEXT_OCCURRENCE)!!.compareTo(today) > 0)
        assertNotNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE))
    }

    @Test
    fun aRunWithOnlyATransferThatNeedsARateDoesNotStartItselfAgain() {
        ReflectionHelpers.getStaticField<MutableMap<*, *>>(JobIntentService::class.java, "sClassWorkEnqueuer").clear()
        val scheduler = mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        makeDue(insertRecurrentTransfer("Savings", insertWallet("Dollars", "USD"), insertWallet("Euros", "EUR"), 1000L, 0L, "FREQ=DAILY"), 3, false)
        var runs = 0
        do {
            scheduler.cancelAll()
            runTask()
            runs++
        } while (!scheduler.allPendingJobs.isEmpty() && runs < 5)
        assertEquals(1, runs)
        assertTrue(scheduler.allPendingJobs.isEmpty())
    }

    @Test
    fun aRunWithOnlyAHeldTransferDueTodayNotifiesItAndDoesNotStartItselfAgain() {
        ReflectionHelpers.getStaticField<MutableMap<*, *>>(JobIntentService::class.java, "sClassWorkEnqueuer").clear()
        val scheduler = mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        val id = insertRecurrentTransfer("Savings", insertWallet("Dollars", "USD"), insertWallet("Euros", "EUR"), 1000L, 0L, "FREQ=DAILY")
        val today = DateUtils.getSQLDateString(Date())
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, today)
        values.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, today)
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null))
        var runs = 0
        do {
            scheduler.cancelAll()
            runTask()
            runs++
        } while (!scheduler.allPendingJobs.isEmpty() && runs < 5)
        assertEquals(1, runs)
        assertTrue(scheduler.allPendingJobs.isEmpty())
        assertEquals("Savings", text(needsRateNotification()))
        assertEquals(today, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE))
        assertEquals(today, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE))
    }

    @Test
    fun savingARateClearsTheRateNotificationOnTheNextRun() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        val id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY")
        makeDue(id, 3, false)
        runTask()
        needsRateNotification()
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Savings")
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, dollars)
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, euros)
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L)
        values.put(Contract.RecurrentTransfer.MONEY_TO, 920L)
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L)
        values.put(Contract.RecurrentTransfer.CONFIRMED, true)
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true)
        values.put(Contract.RecurrentTransfer.START_DATE, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.START_DATE))
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=DAILY")
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null))
        runTask()
        assertNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE))
        recurrenceNotification()
    }

    @Test
    fun oneServiceRunTwiceWithAHeldTransferNamesItOnce() {
        val service = Robolectric.buildService(RecurrenceHandlerIntentService::class.java).create().get()
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        makeDue(insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false)
        ReflectionHelpers.callInstanceMethod<Any>(service, "onHandleWork", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent()))
        ReflectionHelpers.callInstanceMethod<Any>(service, "onHandleWork", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent()))
        val notification = needsRateNotification()
        assertEquals(mContext.getString(R.string.notification_title_recurrence_needs_rate), title(notification))
        assertEquals("Savings", text(notification))
    }

    @Test
    fun aHeldTransferDueInTwoDaysIsNotNotified() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        val id = insertRecurrentTransfer("Savings", dollars, euros, 1000L, 0L, "FREQ=DAILY")
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_MONTH, 2)
        val pointer = DateUtils.getSQLDateString(calendar.time)
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, pointer)
        values.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, pointer)
        assertEquals(1, mResolver.update(recurrentTransferUri(id), values, null, null))
        runTask()
        assertNull(mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE))
        assertEquals(pointer, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.LAST_OCCURRENCE))
        assertEquals(pointer, readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE))
    }

    @Test
    fun aHeldTransferWithoutDescriptionShowsItsWalletsInOrder() {
        val dollars = insertWallet("Dollars", "USD")
        val euros = insertWallet("Euros", "EUR")
        makeDue(insertRecurrentTransfer("", dollars, euros, 1000L, 0L, "FREQ=DAILY"), 3, false)
        runTask()
        assertEquals(mContext.getString(R.string.notification_text_recurrence_transfer, "Dollars", "Euros"), text(needsRateNotification()))
    }

    @Test
    fun everyTransferADueRecurrencePostsIsLinkedToThatRecurrence() {
        val decoy = insertRecurrentTransfer("Decoy", mWalletB, mWalletA, 500L, 500L, "FREQ=DAILY")
        val id = insertRecurrentTransfer("Rent", mWalletA, mWalletB, 1000L, 1000L, "FREQ=DAILY")
        makeDue(id, 3, false)
        val expected = dailyRows(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE), 1000L)
        runTask()
        assertTrue(expected.size >= 2)
        assertEquals(expected, postedTransfers(mWalletA))
        assertTrue(postedTransfers(mWalletB).isEmpty())
        assertNotEquals(1L, id)
        assertNotEquals(decoy, id)
        val recurrences = storedTransferRecurrences()
        assertEquals(expected.size, recurrences.size)
        for (recurrence in recurrences) {
            assertEquals(id, recurrence)
        }
    }

    private fun dailyRows(from: String?, moneyTo: Long): List<String> {
        val rows: MutableList<String> = ArrayList()
        val today = DateUtils.getSQLDateString(Date())
        val calendar = Calendar.getInstance()
        calendar.time = DateUtils.getDateFromSQLDateString(from)
        while (DateUtils.getSQLDateString(calendar.time).compareTo(today) <= 0) {
            rows.add(DateUtils.getSQLDateString(calendar.time) + " " + moneyTo)
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        return rows
    }

    private fun assertDailyTransferPosts(id: Long, fromWalletId: Long, moneyTo: Long) {
        val daysAgo = 3
        makeDue(id, daysAgo, false)
        val expected = dailyRows(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE), moneyTo)
        runTask()
        assertEquals(expected, postedTransfers(fromWalletId))
        val today = DateUtils.getSQLDateString(Date())
        assertTrue(readString(recurrentTransferUri(id), Contract.RecurrentTransfer.NEXT_OCCURRENCE)!!.compareTo(today) > 0)
        assertEquals(plural(expected.size), title(recurrenceNotification()))
        assertEquals(1, mNotifications.allNotifications.size)
    }

    @Test
    fun aRolledExpensesPeriodKeepsFollowingTheCategoryBudgetsInsideIt() {
        val start = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - 70L * DAY))
        val end = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - 40L * DAY))
        val expenses = monthlyBudget(Contract.BudgetType.EXPENSES, 100000L, start, end)
        expenses.put(Contract.Budget.TAG, Contract.Budget.TAG_FOLLOWS_CATEGORIES)
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, expenses)
        val category = monthlyBudget(Contract.BudgetType.CATEGORY, 30000L, start, end)
        category.put(Contract.Budget.CATEGORY_IDS, "<" + mCategory + ">")
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, category)
        runTask()
        SplitLedger.database(mContext).execSQL("UPDATE budgets SET budget_money = 45000 WHERE budget_type = ? " +
                "AND budget_start_date > ?", arrayOf<Any>(Contract.BudgetType.CATEGORY.value, start))
        val cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS,
                arrayOf(Contract.Budget.START_DATE, Contract.Budget.TAG, Contract.Budget.SHOWN_MONEY),
                Contract.Budget.TYPE + " = ? AND " + Contract.Budget.RULE + " IS NOT NULL",
                arrayOf(Contract.BudgetType.EXPENSES.value.toString()), null)
        assertNotNull(cursor)
        try {
            assertEquals(1, cursor!!.count)
            assertTrue(cursor.moveToFirst())
            assertTrue(start.compareTo(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE))) < 0)
            assertEquals(Contract.Budget.TAG_FOLLOWS_CATEGORIES, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.TAG)))
            assertEquals(45000L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.SHOWN_MONEY)))
        } finally {
            cursor!!.close()
        }
    }

    @Test
    fun aRolledExpensesPeriodThatFollowsKeepsItsTypedLimitAndShowsTheSum() {
        val start = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - 70L * DAY))
        val end = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - 40L * DAY))
        val expenses = monthlyBudget(Contract.BudgetType.EXPENSES, 100000L, start, end)
        expenses.put(Contract.Budget.TAG, Contract.Budget.TAG_FOLLOWS_CATEGORIES)
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, expenses)
        val category = monthlyBudget(Contract.BudgetType.CATEGORY, 30000L, start, end)
        category.put(Contract.Budget.CATEGORY_IDS, "<" + mCategory + ">")
        mResolver.insert(DataContentProvider.CONTENT_BUDGETS, category)
        runTask()
        val cursor = mResolver.query(DataContentProvider.CONTENT_BUDGETS,
                arrayOf(Contract.Budget.START_DATE, Contract.Budget.RULE, Contract.Budget.MONEY,
                        Contract.Budget.SHOWN_MONEY),
                Contract.Budget.TYPE + " = ?", arrayOf(Contract.BudgetType.EXPENSES.value.toString()),
                Contract.Budget.START_DATE + " ASC")
        assertNotNull(cursor)
        try {
            assertTrue(cursor!!.count >= 2)
            var repeating = 0
            var i = 0
            while (cursor.moveToPosition(i)) {
                if (i == 0) {
                    assertEquals(start, cursor.getString(cursor.getColumnIndexOrThrow(Contract.Budget.START_DATE)))
                    assertTrue(cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Budget.RULE)))
                }
                if (!cursor.isNull(cursor.getColumnIndexOrThrow(Contract.Budget.RULE))) {
                    repeating++
                }
                assertEquals(100000L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.MONEY)))
                assertEquals(30000L, cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Budget.SHOWN_MONEY)))
                i++
            }
            assertEquals(1, repeating)
        } finally {
            cursor!!.close()
        }
    }

    private fun monthlyBudget(type: Contract.BudgetType, money: Long, start: String, end: String): ContentValues {
        val values = ContentValues()
        values.put(Contract.Budget.TYPE, type.value)
        values.put(Contract.Budget.MONEY, money)
        values.put(Contract.Budget.WALLET_IDS, "<" + mWalletA + ">")
        values.put(Contract.Budget.START_DATE, start)
        values.put(Contract.Budget.END_DATE, end)
        values.put(Contract.Budget.RULE, "FREQ=MONTHLY")
        values.put(Contract.Budget.RULE_START, start)
        return values
    }

    private fun runTask() {
        val service = Robolectric.buildService(RecurrenceHandlerIntentService::class.java).create().get()
        ReflectionHelpers.callInstanceMethod<Any>(service, "onHandleWork", ReflectionHelpers.ClassParameter.from(Intent::class.java, Intent()))
    }

    private fun recurrenceNotification(): Notification {
        val notification = mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE)
        assertNotNull(notification)
        return notification
    }

    private fun needsRateNotification(): Notification {
        val notification = mNotifications.getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE)
        assertNotNull(notification)
        return notification
    }

    private fun recurrentTransferUri(id: Long): Uri {
        return ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id)
    }

    private fun postedTransfers(fromWalletId: Long): List<String> {
        val cursor = mResolver.query(DataContentProvider.CONTENT_TRANSFERS,
                arrayOf(Contract.Transfer.DATE, Contract.Transfer.TRANSACTION_TO_MONEY),
                Contract.Transfer.TRANSACTION_FROM_WALLET_ID + " = ?", arrayOf(fromWalletId.toString()),
                Contract.Transfer.DATE + " ASC")
        assertNotNull(cursor)
        val rows: MutableList<String> = ArrayList()
        try {
            while (cursor!!.moveToNext()) {
                rows.add(cursor.getString(0).substring(0, 10) + " " + cursor.getLong(1))
            }
        } finally {
            cursor!!.close()
        }
        return rows
    }

    private fun storedTransferRecurrences(): List<Long?> {
        val cursor = SplitLedger.database(mContext).rawQuery("SELECT transaction_recurrence FROM transfers", null)
        assertNotNull(cursor)
        val recurrences: MutableList<Long?> = ArrayList()
        try {
            while (cursor.moveToNext()) {
                recurrences.add(if (cursor.isNull(0)) null else cursor.getLong(0))
            }
        } finally {
            cursor.close()
        }
        return recurrences
    }

    private fun plural(count: Int): String {
        return mContext.resources.getQuantityString(R.plurals.notification_title_recurrences_added, count, count)
    }

    private fun title(notification: Notification): String? {
        val title = notification.extras.getCharSequence(Notification.EXTRA_TITLE)
        return if (title != null) title.toString() else null
    }

    private fun text(notification: Notification): String? {
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
        return if (text != null) text.toString() else null
    }

    /**
     * Moves the pointer of a recurrence the given number of days back through the provider, the
     * way the task itself writes it, and checks it landed there.
     */
    private fun makeDue(id: Long, daysAgo: Int, transaction: Boolean) {
        val uri = ContentUris.withAppendedId(if (transaction) DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS
                else DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id)
        val pointer = DateUtils.getSQLDateString(Date(System.currentTimeMillis() - daysAgo * DAY))
        val values = ContentValues()
        values.put(if (transaction) Contract.RecurrentTransaction.LAST_OCCURRENCE else Contract.RecurrentTransfer.LAST_OCCURRENCE, pointer)
        values.put(if (transaction) Contract.RecurrentTransaction.NEXT_OCCURRENCE else Contract.RecurrentTransfer.NEXT_OCCURRENCE, pointer)
        assertEquals(1, mResolver.update(uri, values, null, null))
        val stored = readString(uri, if (transaction) Contract.RecurrentTransaction.NEXT_OCCURRENCE else Contract.RecurrentTransfer.NEXT_OCCURRENCE)
        assertEquals(pointer, stored)
        assertTrue(DateUtils.getDateFromSQLDateString(stored).time < System.currentTimeMillis() - DAY)
    }

    private fun readString(uri: Uri, column: String): String? {
        val cursor = mResolver.query(uri, null, null, null, null)
        assertNotNull(cursor)
        try {
            assertTrue(cursor!!.moveToFirst())
            return cursor.getString(cursor.getColumnIndex(column))
        } finally {
            cursor!!.close()
        }
    }

    private fun countRows(uri: Uri): Int {
        val cursor = mResolver.query(uri, null, null, null, null)
        assertNotNull(cursor)
        try {
            return cursor!!.count
        } finally {
            cursor!!.close()
        }
    }

    private fun futureStart(): String {
        return DateUtils.getSQLDateString(Date(System.currentTimeMillis() + 30L * DAY))
    }

    private fun insertWallet(name: String): Long {
        return insertWallet(name, "EUR")
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

    private fun insertCategory(name: String): Long {
        val values = ContentValues()
        values.put(Contract.Category.NAME, name)
        values.put(Contract.Category.ICON, ICON)
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.value)
        values.put(Contract.Category.SHOW_REPORT, true)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values)!!)
    }

    private fun insertRecurrentTransaction(description: String): Long {
        return insertRecurrentTransaction(description, "FREQ=MONTHLY")
    }

    private fun insertRecurrentTransaction(description: String, rule: String): Long {
        val values = ContentValues()
        values.put(Contract.RecurrentTransaction.MONEY, 1000L)
        values.put(Contract.RecurrentTransaction.DESCRIPTION, description)
        values.put(Contract.RecurrentTransaction.CATEGORY_ID, mCategory)
        values.put(Contract.RecurrentTransaction.DIRECTION, Contract.Direction.EXPENSE)
        values.put(Contract.RecurrentTransaction.WALLET_ID, mWalletA)
        values.put(Contract.RecurrentTransaction.CONFIRMED, true)
        values.put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, true)
        values.put(Contract.RecurrentTransaction.START_DATE, futureStart())
        values.put(Contract.RecurrentTransaction.RULE, rule)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, values)!!)
    }

    private fun insertRecurrentTransfer(description: String): Long {
        return insertRecurrentTransfer(description, mWalletA, mWalletB, 1000L, 1000L, "FREQ=MONTHLY")
    }

    private fun insertRecurrentTransfer(description: String, from: Long, to: Long, moneyFrom: Long, moneyTo: Long, rule: String): Long {
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.DESCRIPTION, description)
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, from)
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, to)
        values.put(Contract.RecurrentTransfer.MONEY_FROM, moneyFrom)
        values.put(Contract.RecurrentTransfer.MONEY_TO, moneyTo)
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L)
        values.put(Contract.RecurrentTransfer.CONFIRMED, true)
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true)
        values.put(Contract.RecurrentTransfer.START_DATE, futureStart())
        values.put(Contract.RecurrentTransfer.RULE, rule)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values)!!)
    }
}
