package com.oriondev.moneywallet.picker

import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.os.Looper
import android.os.Parcel
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.test.core.app.ApplicationProvider
import com.oriondev.moneywallet.model.Icon
import com.oriondev.moneywallet.model.Person
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.ui.fragment.dialog.PeoplePickerDialog
import com.oriondev.moneywallet.ui.fragment.dialog.WalletPickerDialog
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
class PickerProcessDeathRestoreTest {

    @Before
    fun setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun personPickerKeepsBothPeopleAfterProcessDeath() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = PersonPicker.createPicker(first.get().supportFragmentManager, PERSON_TAG, null)
        first.get().supportFragmentManager.executePendingTransactions()
        picker.setPeople(arrayOf(Person(1L, "Ann", null), Person(2L, "Bob", null)))

        val second = recreate(first, throughParcel = true)
        try {
            val restored = personPicker(second)
            assertEquals(listOf(1L, 2L), restored.currentPeople.map { it.getId() })
            assertEquals(listOf("Ann", "Bob"), restored.currentPeople.map { it.getName() })
        } finally {
            second.close()
        }
    }

    @Test
    fun personPickerKeepsBothPeopleAfterInProcessRecreation() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = PersonPicker.createPicker(first.get().supportFragmentManager, PERSON_TAG, null)
        first.get().supportFragmentManager.executePendingTransactions()
        picker.setPeople(arrayOf(Person(1L, "Ann", null), Person(2L, "Bob", null)))

        val second = recreate(first, throughParcel = false)
        try {
            val restored = personPicker(second)
            assertEquals(listOf(1L, 2L), restored.currentPeople.map { it.getId() })
            assertEquals(listOf("Ann", "Bob"), restored.currentPeople.map { it.getName() })
        } finally {
            second.close()
        }
    }

    @Test
    fun multiWalletPickerKeepsBothWalletsAfterProcessDeath() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = WalletPicker.createPicker(first.get().supportFragmentManager, WALLET_TAG, null as Array<Wallet>?)
        first.get().supportFragmentManager.executePendingTransactions()
        val cash = wallet(11L, "Cash", "EUR")
        val bank = wallet(12L, "Bank", "USD")
        picker.onWalletsSelected(arrayOf(cash, bank))

        val second = recreate(first, throughParcel = true)
        try {
            val restored = walletPicker(second)
            assertEquals(listOf(11L, 12L), restored.currentWallets.map { it.getId() })
            assertEquals(listOf("Cash", "Bank"), restored.currentWallets.map { it.name })
            assertEquals(listOf("EUR", "USD"), restored.currentWallets.map { it.currency?.iso })
            assertEquals(listOf(cash, bank).map { iconOf(it)!!.toString() }, restored.currentWallets.map { iconOf(it)?.toString() })
        } finally {
            second.close()
        }
    }

    @Test
    fun multiWalletPickerWithNoWalletsRestoresNullAndUnselectedAfterProcessDeath() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        WalletPicker.createPicker(first.get().supportFragmentManager, WALLET_TAG, null as Array<Wallet>?)
        first.get().supportFragmentManager.executePendingTransactions()

        val second = recreate(first, throughParcel = true)
        try {
            val restored = walletPicker(second)
            assertNull(restored.currentWallets)
            assertFalse(restored.isSelected)
        } finally {
            second.close()
        }
    }

    @Test
    fun openPeoplePickerDialogKeepsBothSelectedPeopleAfterProcessDeath() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = PersonPicker.createPicker(first.get().supportFragmentManager, PERSON_TAG, null)
        first.get().supportFragmentManager.executePendingTransactions()
        picker.showPicker()
        picker.childFragmentManager.executePendingTransactions()
        val dialog = picker.childFragmentManager.findFragmentByTag(PERSON_DIALOG_TAG) as PeoplePickerDialog
        dialog.onPersonSelected(Person(1L, "Ann", null))
        dialog.onPersonSelected(Person(2L, "Bob", null))

        val second = recreate(first, throughParcel = true)
        try {
            val restored = personPicker(second)
            assertNull(restored.currentPeople)
            val restoredDialog = restored.childFragmentManager.findFragmentByTag(PERSON_DIALOG_TAG) as PeoplePickerDialog
            confirm(restoredDialog.dialog as AlertDialog)
            assertEquals(listOf(1L, 2L), restored.currentPeople.map { it.getId() })
            assertEquals(listOf("Ann", "Bob"), restored.currentPeople.map { it.getName() })
        } finally {
            second.close()
        }
    }

    @Test
    fun openMultiWalletPickerDialogKeepsBothSelectedWalletsAfterProcessDeath() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val picker = WalletPicker.createPicker(first.get().supportFragmentManager, WALLET_TAG, null as Array<Wallet>?)
        first.get().supportFragmentManager.executePendingTransactions()
        picker.showMultiWalletPicker()
        picker.childFragmentManager.executePendingTransactions()
        val dialog = picker.childFragmentManager.findFragmentByTag(WALLET_DIALOG_TAG) as WalletPickerDialog
        val cash = wallet(11L, "Cash", "EUR")
        val bank = wallet(12L, "Bank", "USD")
        dialog.onWalletClick(cash)
        dialog.onWalletClick(bank)

        val second = recreate(first, throughParcel = true)
        try {
            val restored = walletPicker(second)
            assertNull(restored.currentWallets)
            val restoredDialog = restored.childFragmentManager.findFragmentByTag(WALLET_DIALOG_TAG) as WalletPickerDialog
            confirm(restoredDialog.dialog as AlertDialog)
            assertEquals(listOf(11L, 12L), restored.currentWallets.map { it.getId() })
            assertEquals(listOf("Cash", "Bank"), restored.currentWallets.map { it.name })
            assertEquals(listOf("EUR", "USD"), restored.currentWallets.map { it.currency?.iso })
            assertEquals(listOf(cash, bank).map { iconOf(it)!!.toString() }, restored.currentWallets.map { iconOf(it)?.toString() })
        } finally {
            second.close()
        }
    }

    @Test
    fun singleWalletPickerDialogKeepsDefaultWalletSelectedAndDeliversClickAfterProcessDeath() {
        val first = Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        val cash = wallet(11L, "Cash", "EUR")
        val bank = wallet(12L, "Bank", "USD")
        val picker = WalletPicker.createPicker(first.get().supportFragmentManager, WALLET_TAG, cash)
        first.get().supportFragmentManager.executePendingTransactions()
        picker.showSingleWalletPicker("EUR")
        picker.childFragmentManager.executePendingTransactions()

        val second = recreate(first, throughParcel = true)
        try {
            val restored = walletPicker(second)
            assertEquals(11L, restored.currentWallet.getId())
            assertEquals("EUR", restored.currentWallet.currency?.iso)
            val restoredDialog = restored.childFragmentManager.findFragmentByTag(WALLET_DIALOG_TAG) as WalletPickerDialog
            assertNotNull(restoredDialog.dialog)
            assertTrue(restoredDialog.isWalletSelected(11L))
            assertFalse(restoredDialog.isWalletSelected(12L))
            restoredDialog.onWalletClick(bank)
            restored.childFragmentManager.executePendingTransactions()
            assertNull(restored.childFragmentManager.findFragmentByTag(WALLET_DIALOG_TAG))
            assertEquals(12L, restored.currentWallet.getId())
            assertEquals("Bank", restored.currentWallet.name)
            assertEquals("USD", restored.currentWallet.currency?.iso)
            assertEquals(iconOf(bank)!!.toString(), iconOf(restored.currentWallet)?.toString())
        } finally {
            second.close()
        }
    }

    private fun recreate(
        first: ActivityController<AppCompatActivity>,
        throughParcel: Boolean
    ): ActivityController<AppCompatActivity> {
        val outState = Bundle()
        first.pause().stop().saveInstanceState(outState).destroy()
        val savedState = if (throughParcel) parceled(outState) else outState
        return Robolectric.buildActivity(AppCompatActivity::class.java).setup(savedState)
    }

    private fun parceled(bundle: Bundle): Bundle {
        val parcel = Parcel.obtain()
        try {
            parcel.writeBundle(bundle)
            parcel.setDataPosition(0)
            return parcel.readBundle(ApplicationProvider.getApplicationContext<Context>().classLoader)!!
        } finally {
            parcel.recycle()
        }
    }

    private fun confirm(dialog: AlertDialog) {
        assertNotNull(dialog)
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun personPicker(controller: ActivityController<AppCompatActivity>): PersonPicker =
        controller.get().supportFragmentManager.findFragmentByTag(PERSON_TAG) as PersonPicker

    private fun walletPicker(controller: ActivityController<AppCompatActivity>): WalletPicker =
        controller.get().supportFragmentManager.findFragmentByTag(WALLET_TAG) as WalletPicker

    private fun wallet(id: Long, name: String, currency: String): Wallet =
        Wallet(id, name, IconLoader.parse(ICON), CurrencyManager.getCurrency(currency), 0L, 0L)

    private fun iconOf(wallet: Wallet): Icon? =
        Wallet::class.java.getDeclaredField("mIcon").apply { isAccessible = true }.get(wallet) as Icon?

    private companion object {
        const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
        const val PERSON_TAG = "PickerProcessDeathRestoreTest::PersonPicker"
        const val WALLET_TAG = "PickerProcessDeathRestoreTest::WalletPicker"
        const val PERSON_DIALOG_TAG = "$PERSON_TAG::DialogFragment"
        const val WALLET_DIALOG_TAG = "$WALLET_TAG::DialogFragment"
    }
}
