package com.oriondev.moneywallet.ui.activity;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAppTask;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The launcher hands off to the main screen unless its own task already has the main screen at
 * the base, which must then be left as it was.
 */
@RunWith(RobolectricTestRunner.class)
public class LauncherActivityTest {

    private Context mContext;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        PreferenceManager.setIsFirstStartDone(false);
    }

    @Test
    public void aLaunchOnTopOfTheMainScreenStartsNoSecondOne() {
        insertWallet();
        LauncherActivity activity = launch(false, MainActivity.class, true);
        assertTrue(activity.isFinishing());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    @Test
    public void aLaunchOnTopOfTheTransactionEditorOpensTheMainScreen() {
        insertWallet();
        LauncherActivity activity = launch(false, NewEditTransactionActivity.class, true);
        assertMainScreenStarted(activity);
    }

    @Test
    public void aLaunchThatStartsTheTaskOpensTheMainScreen() {
        insertWallet();
        LauncherActivity activity = launch(true, null, false);
        assertMainScreenStarted(activity);
        assertTrue(activity.isFinishing());
    }

    @Test
    public void aLaunchInsideAnotherAppsTaskOpensTheMainScreen() {
        insertWallet();
        LauncherActivity activity = launch(false, MainActivity.class, false);
        assertMainScreenStarted(activity);
    }

    @Test
    public void theMainScreenIsLeftAsItWasBeforeTheWelcomeScreenIsConsidered() {
        LauncherActivity activity = launch(false, MainActivity.class, true);
        assertTrue(activity.isFinishing());
        assertNull(shadowOf(activity).getNextStartedActivity());
    }

    private static void assertMainScreenStarted(LauncherActivity activity) {
        Intent next = shadowOf(activity).getNextStartedActivity();
        assertEquals(MainActivity.class.getName(), next.getComponent().getClassName());
    }

    private void insertWallet() {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, "Cash");
        values.put(Contract.Wallet.ICON, "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"C\"}");
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        values.put(Contract.Wallet.INDEX, 0);
        mContext.getContentResolver().insert(DataContentProvider.CONTENT_WALLETS, values);
    }

    /**
     * @param base the activity at the base of the app task listed for this activity
     * @param ownTask whether that app task has this activity's task id, or belongs to another task
     */
    private LauncherActivity launch(boolean taskRoot, Class<?> base, boolean ownTask) {
        Intent intent = new Intent(mContext, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityController<LauncherActivity> controller = Robolectric.buildActivity(LauncherActivity.class, intent);
        LauncherActivity activity = controller.get();
        shadowOf(activity).setIsTaskRoot(taskRoot);
        ActivityManager.RecentTaskInfo info = new ActivityManager.RecentTaskInfo();
        info.persistentId = ownTask ? activity.getTaskId() : activity.getTaskId() + 1;
        info.baseActivity = base != null ? new ComponentName(mContext, base) : null;
        ActivityManager.AppTask task = ShadowAppTask.newInstance();
        shadowOf(task).setTaskInfo(info);
        ActivityManager manager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        shadowOf(manager).setAppTasks(Collections.singletonList(task));
        return controller.create().get();
    }
}
