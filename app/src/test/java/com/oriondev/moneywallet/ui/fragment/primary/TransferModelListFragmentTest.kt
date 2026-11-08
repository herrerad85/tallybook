package com.oriondev.moneywallet.ui.fragment.primary

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.activity.BackupListActivity
import com.oriondev.moneywallet.ui.activity.NewEditItemActivity
import com.oriondev.moneywallet.ui.activity.NewEditTransferActivity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class TransferModelListFragmentTest {

    companion object {
        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        private const val TAG_FRAGMENT = "TransferModelListFragmentTest::Fragment"
    }

    private lateinit var mContext: Context
    private lateinit var mResolver: ContentResolver
    private var mDollars: Long = 0
    private var mEuros: Long = 0

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mResolver = mContext.contentResolver
        mDollars = insertWallet("Dollars", "USD")
        mEuros = insertWallet("Euros", "EUR")
    }

    @Test
    fun addingAModelThatNeedsARateOpensTheEditorAndInsertsNothing() {
        val model = insertModel(1000L, 0L)
        val before = countTransfers()
        onFragment { activity, fragment ->
            fragment.onModelAddClick(model)
            val next: Intent? = shadowOf(activity).nextStartedActivity
            assertNotNull("the tap started nothing", next)
            assertEquals(NewEditTransferActivity::class.java.name, next!!.component!!.className)
            assertEquals(NewEditItemActivity.Mode.NEW_ITEM, next.getSerializableExtra(NewEditItemActivity.MODE))
            assertEquals(NewEditTransferActivity.TYPE_MODEL, next.getIntExtra(NewEditTransferActivity.TYPE, -1))
            assertEquals(model, next.getLongExtra(NewEditTransferActivity.MODEL_ID, -1L))
        }
        assertEquals(before, countTransfers())
    }

    @Test
    fun addingAModelThatReceivesAnAmountInsertsOneTransferAndOpensNothing() {
        val model = insertModel(1000L, 920L)
        val before = countTransfers()
        onFragment { activity, fragment ->
            fragment.onModelAddClick(model)
            assertNull(shadowOf(activity).nextStartedActivity)
        }
        assertEquals(before + 1, countTransfers())
    }

    private fun onFragment(check: (BackupListActivity, TransferModelListFragment) -> Unit) {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = TransferModelListFragment()
                activity.supportFragmentManager
                        .beginTransaction()
                        .add(android.R.id.content, fragment, TAG_FRAGMENT)
                        .commitNow()
                shadowOf(activity).clearNextStartedActivities()
                check(activity, fragment)
            }
        }
    }

    private fun countTransfers(): Int {
        val cursor = mResolver.query(DataContentProvider.CONTENT_TRANSFERS,
                arrayOf(Contract.Transfer.ID), null, null, null)!!
        val count = cursor.count
        cursor.close()
        return count
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

    private fun insertModel(moneyFrom: Long, moneyTo: Long): Long {
        val values = ContentValues()
        values.put(Contract.TransferModel.DESCRIPTION, "Savings")
        values.put(Contract.TransferModel.WALLET_FROM_ID, mDollars)
        values.put(Contract.TransferModel.WALLET_TO_ID, mEuros)
        values.put(Contract.TransferModel.MONEY_FROM, moneyFrom)
        values.put(Contract.TransferModel.MONEY_TO, moneyTo)
        values.put(Contract.TransferModel.MONEY_TAX, 0L)
        values.put(Contract.TransferModel.CONFIRMED, true)
        values.put(Contract.TransferModel.COUNT_IN_TOTAL, true)
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFER_MODELS, values)!!)
    }
}
