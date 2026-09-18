package com.oriondev.moneywallet.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;

import android.app.Application;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.service.ImportExportIntentService;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.robolectric.Shadows.shadowOf;

/**
 * Only the screen that started an import or export reacts to the service's broadcasts, so a
 * stopped screen underneath never commits a dialog after its state was saved (#311).
 */
@RunWith(RobolectricTestRunner.class)
public class ImportExportActivityTest {

    private static final String TAG_PROGRESS_DIALOG = "ImportExportActivity::tag::GenericProgressDialog";

    private Context mContext;
    private final List<ActivityController<ImportExportActivity>> mControllers = new ArrayList<>();

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
    }

    /**
     * The broadcast manager is one instance for the whole run, so a screen left registered would
     * still receive the next test's broadcasts.
     */
    @After
    public void tearDown() {
        for (ActivityController<ImportExportActivity> controller : mControllers) {
            if (!controller.get().isDestroyed()) {
                controller.destroy();
            }
        }
    }

    @Test
    public void aStoppedScreenIgnoresTheStartOfWorkItDidNotStart() {
        ActivityController<ImportExportActivity> controller = build().setup();
        controller.pause().saveInstanceState(new Bundle()).stop();
        String[] actions = {LocalAction.ACTION_IMPORT_SERVICE_STARTED, LocalAction.ACTION_EXPORT_SERVICE_STARTED};
        for (String action : actions) {
            send(new Intent(action).putExtra(ImportExportIntentService.TOKEN, "someone else"));
            send(new Intent(action));
        }
        assertNull(controller.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
    }

    @Test
    public void aRecreatedScreenShowsTheProgressOfItsOwnWork() {
        Bundle state = new Bundle();
        build().setup().saveInstanceState(state);
        state.putStringArrayList(ImportExportActivity.SS_TOKEN, mine());
        ActivityController<ImportExportActivity> controller = build().setup(state);
        send(new Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "mine"));
        assertNotNull(controller.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
    }

    /** A screen that holds a token of its own still ignores work somebody else started. */
    @Test
    public void aStoppedScreenWithWorkOfItsOwnIgnoresWorkItDidNotStart() {
        ActivityController<ImportExportActivity> controller = build().setup(restoredState());
        controller.pause().saveInstanceState(new Bundle()).stop();
        send(new Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "someone else"));
        send(new Intent(LocalAction.ACTION_EXPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "someone else"));
        assertNull(controller.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
    }

    /** The screen saves its tokens itself, so they last through a second recreation too. */
    @Test
    public void theTokensLastThroughASecondRecreation() {
        ActivityController<ImportExportActivity> second = build().setup(restoredState());
        Bundle saved = new Bundle();
        second.pause().saveInstanceState(saved).stop().destroy();
        ActivityController<ImportExportActivity> third = build().setup(saved);
        send(new Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, "mine"));
        assertNotNull(third.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
    }

    /**
     * Export starts with no dialog to confirm it, so two taps queue two jobs before the first one
     * starts. The screen keeps both tokens and still shows the progress of the first job.
     */
    @Test
    public void aSecondExportDoesNotHideTheFirstOne() {
        Application application = ApplicationProvider.getApplicationContext();
        ActivityController<ImportExportActivity> controller = build(ImportExportActivity.MODE_EXPORT).setup();
        controller.get().exportData();
        controller.get().exportData();
        String first = nextServiceToken(application);
        String second = nextServiceToken(application);
        assertNotNull(first);
        assertNotNull(second);
        assertNotEquals(first, second);
        send(new Intent(LocalAction.ACTION_EXPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, first));
        assertNotNull(controller.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));

        ActivityController<ImportExportActivity> importer = build().setup();
        importer.get().importData();
        importer.get().importData();
        String token = nextServiceToken(application);
        assertNotNull(token);
        send(new Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, token));
        assertNotNull(importer.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
    }

    /**
     * The crash in #311. Two import screens each started an import, and the lower one was stopped
     * after its state was saved, so only the upper one may react to the upper one's work.
     */
    @Test
    public void aStoppedImportScreenIgnoresTheImportOfTheScreenAboveIt() {
        Application application = ApplicationProvider.getApplicationContext();
        ActivityController<ImportExportActivity> lower = build().setup();
        lower.get().importData();
        assertNotNull(nextServiceToken(application));
        lower.pause().saveInstanceState(new Bundle()).stop();
        ActivityController<ImportExportActivity> upper = build().setup();
        upper.get().importData();
        String token = nextServiceToken(application);
        assertNotNull(token);
        send(new Intent(LocalAction.ACTION_IMPORT_SERVICE_STARTED).putExtra(ImportExportIntentService.TOKEN, token));
        assertNull(lower.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
        assertNotNull(upper.get().getSupportFragmentManager().findFragmentByTag(TAG_PROGRESS_DIALOG));
    }

    private static ArrayList<String> mine() {
        return new ArrayList<>(Collections.singletonList("mine"));
    }

    /** The state of a first screen, with a token it issued before it went away. */
    private Bundle restoredState() {
        Bundle state = new Bundle();
        build().setup().saveInstanceState(state);
        state.putStringArrayList(ImportExportActivity.SS_TOKEN, mine());
        return state;
    }

    /** The token on the next start of the import and export service, skipping any other service. */
    private static String nextServiceToken(Application application) {
        Intent intent;
        while ((intent = shadowOf(application).getNextStartedService()) != null) {
            if (intent.getComponent() != null && ImportExportIntentService.class.getName().equals(intent.getComponent().getClassName())) {
                return intent.getStringExtra(ImportExportIntentService.TOKEN);
            }
        }
        throw new AssertionError("the import and export service was not started");
    }

    private ActivityController<ImportExportActivity> build() {
        return build(ImportExportActivity.MODE_IMPORT);
    }

    private ActivityController<ImportExportActivity> build(int mode) {
        Intent intent = new Intent(mContext, ImportExportActivity.class)
                .putExtra(ImportExportActivity.MODE, mode);
        ActivityController<ImportExportActivity> controller = Robolectric.buildActivity(ImportExportActivity.class, intent);
        mControllers.add(controller);
        return controller;
    }

    private void send(Intent intent) {
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
        shadowOf(Looper.getMainLooper()).idle();
    }
}
