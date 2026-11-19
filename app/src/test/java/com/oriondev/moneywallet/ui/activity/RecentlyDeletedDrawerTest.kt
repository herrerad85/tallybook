package com.oriondev.moneywallet.ui.activity

import android.content.Context
import android.os.Looper
import android.view.Menu
import android.view.View

import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider

import com.google.android.material.navigation.NavigationView
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.ui.fragment.secondary.UserInterfaceSettingFragment

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
class RecentlyDeletedDrawerTest {

    private lateinit var context: Context
    private lateinit var hidden: Set<String>
    private var wallet = 0L

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(context)
        hidden = PreferenceManager.getHiddenDrawerEntries()
        wallet = PreferenceManager.getCurrentWallet()
        PreferenceManager.setHiddenDrawerEntries(emptySet())
    }

    @After
    fun tearDown() {
        PreferenceManager.setHiddenDrawerEntries(hidden)
        PreferenceManager.setCurrentWallet(context, wallet)
    }

    @Test
    fun theEntrySitsBetweenSettingsAndAboutAndOpensTheScreen() {
        launch { activity ->
            val menu = drawer(activity).menu
            val ids = (0 until menu.size()).map { menu.getItem(it).itemId }
            val settings = ids.indexOf(MainActivity.ID_SECTION_SETTING)
            assertEquals(listOf(MainActivity.ID_SECTION_SETTING, MainActivity.ID_SECTION_RECENTLY_DELETED,
                    MainActivity.ID_SECTION_ABOUT), ids.subList(settings, settings + 3))
            val item = menu.findItem(MainActivity.ID_SECTION_RECENTLY_DELETED)
            assertEquals(context.getString(R.string.menu_recently_deleted), item.title.toString())
            assertEquals(menu.findItem(MainActivity.ID_SECTION_SETTING).groupId, item.groupId)
            assertFalse(item.isCheckable)
            assertFalse(activity.onNavigationItemSelected(item))
            assertEquals(RecentlyDeletedActivity::class.java.name,
                    shadowOf(activity).nextStartedActivity.component!!.className)
        }
    }

    @Test
    fun theEntryIsLeftOutWhenHidden() {
        PreferenceManager.setHiddenDrawerEntries(setOf(MainActivity.ID_SECTION_RECENTLY_DELETED.toString()))
        launch { activity ->
            val menu: Menu = drawer(activity).menu
            assertNull(menu.findItem(MainActivity.ID_SECTION_RECENTLY_DELETED))
            assertNotNull(menu.findItem(MainActivity.ID_SECTION_SETTING))
            assertNotNull(menu.findItem(MainActivity.ID_SECTION_ABOUT))
        }
    }

    @Test
    fun theHideScreenListsTheEntryBeforeAboutAndStoresItsId() {
        ActivityScenario.launch(BackupListActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val fragment = UserInterfaceSettingFragment()
                activity.supportFragmentManager.beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow()
                fragment.findPreference<Preference>("drawer_entries")!!.performClick()
                shadowOf(Looper.getMainLooper()).idle()
                val dialog = ShadowDialog.getLatestDialog() as AlertDialog
                val list = dialog.listView
                val row = MainActivity.HIDEABLE_ENTRY_IDS.indexOf(MainActivity.ID_SECTION_RECENTLY_DELETED)
                assertEquals(listOf(context.getString(R.string.menu_recently_deleted), context.getString(R.string.menu_about)),
                        listOf(list.adapter.getItem(row).toString(), list.adapter.getItem(row + 1).toString()))
                list.measure(View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(4000, View.MeasureSpec.EXACTLY))
                list.layout(0, 0, 1080, 4000)
                assertTrue(list.isItemChecked(row))
                list.performItemClick(list.getChildAt(row - list.firstVisiblePosition), row, row.toLong())
                assertFalse(list.isItemChecked(row))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
        assertEquals(setOf(MainActivity.ID_SECTION_RECENTLY_DELETED.toString()), PreferenceManager.getHiddenDrawerEntries())
    }

    private fun launch(action: (MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { action(it) }
        }
    }

    private fun drawer(activity: MainActivity): NavigationView = activity.findViewById(R.id.navigation_view)
}
