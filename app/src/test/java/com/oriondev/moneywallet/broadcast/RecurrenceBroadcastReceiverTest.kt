package com.oriondev.moneywallet.broadcast

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context

import androidx.core.app.JobIntentService
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.service.RecurrenceHandlerIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.notification.NotificationContract
import com.oriondev.moneywallet.utils.DateUtils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.util.ReflectionHelpers

import java.util.Date

@RunWith(RobolectricTestRunner::class)
class RecurrenceBroadcastReceiverTest {

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val DAY = 24L * 60L * 60L * 1000L
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver
    private lateinit var mJobScheduler: JobScheduler
    private lateinit var mAlarmManager: AlarmManager
    private lateinit var mNotificationManager: NotificationManager
    private var mDollars: Long = 0
    private var mEuros: Long = 0
    private var mBank: Long = 0

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
        mJobScheduler = mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        mAlarmManager = mContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        mNotificationManager = mContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.cancelAll()
        mDollars = insertWallet("Dollars", "USD")
        mEuros = insertWallet("Euros", "EUR")
        mBank = insertWallet("Bank", "EUR")
        ReflectionHelpers.getStaticField<HashMap<*, *>>(JobIntentService::class.java, "sClassWorkEnqueuer").clear()
        mJobScheduler.cancelAll()
        ShadowAlarmManager.reset()
    }

    @Test
    fun theServicesOwnCallWithAHeldTransferDueInThePastStartsNothingAndSchedulesNothing() {
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), daysFromNow(-3))
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext, false)
        assertTrue(recurrenceJobs().isEmpty())
        assertTrue(recurrenceAlarms().isEmpty())
    }

    @Test
    fun theServicesOwnCallWithAHeldTransferDueInThePastLeavesTheAlarmToTheNextNormalTransfer() {
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), daysFromNow(-3))
        val normalDate = daysFromNow(2)
        moveTo(insertRecurrentTransfer(mEuros, mBank, 1000L, 1000L), normalDate)
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext, false)
        assertTrue(recurrenceJobs().isEmpty())
        val alarms = recurrenceAlarms()
        assertEquals(1, alarms.size)
        assertEquals(DateUtils.getDateFromSQLDateString(normalDate).time, alarms[0].triggerAtTime)
    }

    @Test
    fun theDefaultCallWithAHeldTransferDueInThePastStartsTheService() {
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), daysFromNow(-3))
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext)
        assertEquals(1, recurrenceJobs().size)
        assertTrue(recurrenceAlarms().isEmpty())
    }

    @Test
    fun theDefaultCallWithAHeldTransferDueInTwoDaysSchedulesItsAlarm() {
        val heldDate = daysFromNow(2)
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), heldDate)
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext)
        assertTrue(recurrenceJobs().isEmpty())
        val alarms = recurrenceAlarms()
        assertEquals(1, alarms.size)
        assertEquals(DateUtils.getDateFromSQLDateString(heldDate).time, alarms[0].triggerAtTime)
    }

    @Test
    fun theDefaultCallAfterTheHeldTransferIsDeletedClearsTheRateNotification() {
        val id = insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L)
        moveTo(id, daysFromNow(-3))
        postNeedsRateNotification()
        assertEquals(1, mResolver.delete(ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id), null, null))
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext)
        assertTrue(recurrenceJobs().isEmpty())
        assertNull(needsRateNotification())
    }

    @Test
    fun theDefaultCallWithOnlyAHeldTransferDueInTwoDaysClearsTheRateNotification() {
        val heldDate = daysFromNow(2)
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), heldDate)
        postNeedsRateNotification()
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext)
        assertTrue(recurrenceJobs().isEmpty())
        val alarms = recurrenceAlarms()
        assertEquals(1, alarms.size)
        assertEquals(DateUtils.getDateFromSQLDateString(heldDate).time, alarms[0].triggerAtTime)
        assertNull(needsRateNotification())
    }

    @Test
    fun theDefaultCallWithAHeldTransferDueInThePastLeavesTheRateNotificationToTheRun() {
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), daysFromNow(-3))
        postNeedsRateNotification()
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext)
        assertEquals(1, recurrenceJobs().size)
        assertNotNull(needsRateNotification())
    }

    @Test
    fun theServicesOwnCallWithOnlyAHeldTransferDueInTwoDaysSchedulesItsAlarm() {
        val heldDate = daysFromNow(2)
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), heldDate)
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext, false)
        assertTrue(recurrenceJobs().isEmpty())
        val alarms = recurrenceAlarms()
        assertEquals(1, alarms.size)
        assertEquals(DateUtils.getDateFromSQLDateString(heldDate).time, alarms[0].triggerAtTime)
    }

    @Test
    fun theServicesOwnCallWithHeldTransfersDueInThePastAndInTwoDaysSchedulesTheLaterOne() {
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), daysFromNow(-3))
        val heldDate = daysFromNow(2)
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), heldDate)
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext, false)
        assertTrue(recurrenceJobs().isEmpty())
        val alarms = recurrenceAlarms()
        assertEquals(1, alarms.size)
        assertEquals(DateUtils.getDateFromSQLDateString(heldDate).time, alarms[0].triggerAtTime)
    }

    @Test
    fun theServicesOwnCallWithOnlyAHeldTransferDueTodayStartsNothingAndSchedulesNothing() {
        moveTo(insertRecurrentTransfer(mDollars, mEuros, 1000L, 0L), daysFromNow(0))
        RecurrenceBroadcastReceiver.scheduleRecurrenceTask(mContext, false)
        assertTrue(recurrenceJobs().isEmpty())
        assertTrue(recurrenceAlarms().isEmpty())
    }

    private fun postNeedsRateNotification() {
        NotificationManagerCompat.from(mContext).notify(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE,
                NotificationCompat.Builder(mContext, NotificationContract.NOTIFICATION_CHANNEL_RECURRENCE)
                        .setSmallIcon(R.drawable.ic_notification)
                        .setContentTitle(mContext.getString(R.string.notification_title_recurrence_needs_rate))
                        .build())
        assertNotNull(needsRateNotification())
    }

    private fun needsRateNotification() =
            shadowOf(mNotificationManager).getNotification(NotificationContract.NOTIFICATION_ID_RECURRENCE_NEEDS_RATE)

    private fun recurrenceJobs(): List<JobInfo> {
        return mJobScheduler.allPendingJobs.filter {
            it.service.className == RecurrenceHandlerIntentService::class.java.name
        }
    }

    private fun recurrenceAlarms(): List<ShadowAlarmManager.ScheduledAlarm> {
        return shadowOf(mAlarmManager).scheduledAlarms.filter {
            it.operation != null && shadowOf(it.operation).savedIntent.component?.className ==
                    RecurrenceBroadcastReceiver::class.java.name
        }
    }

    private fun daysFromNow(days: Int): String {
        return DateUtils.getSQLDateString(Date(System.currentTimeMillis() + days * DAY))
    }

    private fun moveTo(id: Long, date: String) {
        val uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, id)
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.LAST_OCCURRENCE, date)
        values.put(Contract.RecurrentTransfer.NEXT_OCCURRENCE, date)
        assertEquals(1, mResolver.update(uri, values, null, null))
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

    private fun insertRecurrentTransfer(from: Long, to: Long, moneyFrom: Long, moneyTo: Long): Long {
        val values = ContentValues()
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Savings")
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, from)
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, to)
        values.put(Contract.RecurrentTransfer.MONEY_FROM, moneyFrom)
        values.put(Contract.RecurrentTransfer.MONEY_TO, moneyTo)
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L)
        values.put(Contract.RecurrentTransfer.CONFIRMED, true)
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true)
        values.put(Contract.RecurrentTransfer.START_DATE, daysFromNow(30))
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=DAILY")
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values)!!)
    }
}
