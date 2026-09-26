package com.oriondev.moneywallet.ui.fragment.secondary;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.UriPermission;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.test.core.app.ActivityScenario;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.SAFFile;
import com.oriondev.moneywallet.service.AutoBackupJobService;
import com.oriondev.moneywallet.service.BackupHandlerIntentService;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.fragment.dialog.AutoBackupSettingDialog;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.fakes.RoboMenuItem;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Every test drives the backup screen inside a resumed activity, which is where a backend used
 * to register its launcher and where AndroidX refuses a registration.
 */
@RunWith(RobolectricTestRunner.class)
public class BackupHandlerFragmentTest {

    @Before
    public void setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.<Context>getApplicationContext());
        SlowFolderProvider.entered = new CountDownLatch(1);
        SlowFolderProvider.release = new CountDownLatch(1);
        Robolectric.setupContentProvider(SlowFolderProvider.class, SLOW_FOLDER.getAuthority());
        SecondSlowFolderProvider.entered = new CountDownLatch(1);
        SecondSlowFolderProvider.release = new CountDownLatch(1);
        Robolectric.setupContentProvider(SecondSlowFolderProvider.class, SECOND_SLOW_FOLDER.getAuthority());
    }

    @Test
    @Config(sdk = 32)
    public void theExternalMemoryCoverButtonAsksForStoragePermission() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                clickTheCoverButton(activity, BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY);
                ShadowActivity.PermissionsRequest request = shadowOf(activity).getLastRequestedPermission();
                assertNotNull(request);
                assertEquals(Manifest.permission.WRITE_EXTERNAL_STORAGE, request.requestedPermissions[0]);
            });
        }
    }

    @Test
    @Config(sdk = {33, 36})
    public void theExternalMemoryScreenOpensFromAndroid13WithoutTheStoragePermission() {
        assertCoverVisibility(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, View.GONE, View.VISIBLE);
    }

    @Test
    @Config(sdk = 32)
    public void theExternalMemoryScreenStaysCoveredWithoutTheStoragePermissionBelowAndroid13() {
        assertCoverVisibility(BackendServiceFactory.SERVICE_ID_EXTERNAL_MEMORY, View.VISIBLE, View.GONE);
    }

    @Test
    public void theLocalFolderCoverButtonOpensTheFolderPicker() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                clickTheCoverButton(activity, BackendServiceFactory.SERVICE_ID_SAF);
                Intent picker = shadowOf(activity).getNextStartedActivity();
                assertNotNull(picker);
                assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker.getAction());
            });
        }
    }

    @Test
    public void changingTheLocalFolderSwitchesToTheNewOneAndReleasesTheOld() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        pickFromChangeFolder(NEW_FOLDER);
        joinFolderChange();
        assertEquals(NEW_FOLDER.toString(), storedLocalFolder(context));
        assertEquals(Collections.singletonList(NEW_FOLDER), persistedUris(context));
    }

    @Test
    public void changingTheLocalFolderMovesAutoBackupToTheNewOne() {
        Robolectric.setupContentProvider(NamedFolderProvider.class, NEW_FOLDER.getAuthority());
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        pickFromChangeFolder(NEW_FOLDER);
        joinFolderChange();
        SAFFile folder = SAFFile.decode(BackendManager.getAutoBackupFolder(BackendServiceFactory.SERVICE_ID_SAF));
        assertNotNull(folder);
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(NEW_FOLDER,
                DocumentsContract.getTreeDocumentId(NEW_FOLDER)).toString(), folder.getUri().toString());
        assertTrue(BackendManager.isAutoBackupEnabled(BackendServiceFactory.SERVICE_ID_SAF));
        assertEquals(Collections.singletonList(NEW_FOLDER), persistedUris(context));
    }

    @Test
    public void aNewFolderWithNoReadableNameTurnsAutoBackupOffAsAFailure() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        // no provider answers for the new folder, so its name comes back null
        pickFromChangeFolder(NEW_FOLDER);
        joinFolderChange();
        assertFalse(BackendManager.isAutoBackupEnabled(BackendServiceFactory.SERVICE_ID_SAF));
        assertTrue(BackendManager.isAutoBackupDisabledByFailure(BackendServiceFactory.SERVICE_ID_SAF));
        assertNull(BackendManager.getAutoBackupFolder(BackendServiceFactory.SERVICE_ID_SAF));
        assertEquals(Collections.singletonList(NEW_FOLDER), persistedUris(context));
    }

    @Test
    public void theOldFolderKeepsItsPermissionUntilAutoBackupHasMoved() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try {
            pickFromChangeFolder(SLOW_FOLDER);
            assertTrue(SlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            assertTrue(persistedUris(context).contains(OLD_FOLDER));
        } finally {
            SlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        assertEquals(Collections.singletonList(SLOW_FOLDER), persistedUris(context));
    }

    @Test
    public void aSecondChangeDuringASlowFirstOneEndsOnTheSecondFolder() throws Exception {
        Robolectric.setupContentProvider(NamedFolderProvider.class, NEW_FOLDER.getAuthority());
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try {
            pickFromChangeFolder(SLOW_FOLDER);
            assertTrue(SlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            pickFromChangeFolder(NEW_FOLDER);
        } finally {
            SlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        SAFFile folder = SAFFile.decode(BackendManager.getAutoBackupFolder(BackendServiceFactory.SERVICE_ID_SAF));
        assertNotNull(folder);
        assertEquals(DocumentsContract.buildDocumentUriUsingTree(NEW_FOLDER,
                DocumentsContract.getTreeDocumentId(NEW_FOLDER)).toString(), folder.getUri().toString());
        assertEquals(Collections.singletonList(NEW_FOLDER), persistedUris(context));
    }

    @Test
    public void changingBackDuringASlowChangeKeepsThePermissionOnTheFolderInUse() throws Exception {
        Robolectric.setupContentProvider(NamedFolderProvider.class, OLD_FOLDER.getAuthority());
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try {
            pickFromChangeFolder(SLOW_FOLDER);
            assertTrue(SlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            pickFromChangeFolder(OLD_FOLDER);
        } finally {
            SlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        assertEquals(OLD_FOLDER.toString(), storedLocalFolder(context));
        assertEquals(Collections.singletonList(OLD_FOLDER), persistedUris(context));
        SAFFile folder = SAFFile.decode(BackendManager.getAutoBackupFolder(BackendServiceFactory.SERVICE_ID_SAF));
        assertNotNull(folder);
        assertEquals("Backups", folder.getName());
    }

    @Test
    public void aDisconnectDuringTheSecondOfTwoChangesReleasesEveryFolder() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try {
            pickFromChangeFolder(SLOW_FOLDER);
            assertTrue(SlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            pickFromChangeFolder(SECOND_SLOW_FOLDER);
            SlowFolderProvider.release.countDown();
            assertTrue(SecondSlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            // what the teardown dialog does
            context.getContentResolver().releasePersistableUriPermission(SECOND_SLOW_FOLDER, READ_WRITE);
            context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE).edit().clear().commit();
        } finally {
            SlowFolderProvider.release.countDown();
            SecondSlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        assertEquals(Collections.emptyList(), persistedUris(context));
    }

    @Test
    public void aFolderPickedAfterADisconnectDuringAChangeIsTheOnlyOneHeld() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try {
            pickFromChangeFolder(SLOW_FOLDER);
            assertTrue(SlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            // what the teardown dialog does, then a pick from the cover button
            context.getContentResolver().releasePersistableUriPermission(SLOW_FOLDER, READ_WRITE);
            context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE).edit().clear().commit();
            useLocalFolder(context, NEW_FOLDER);
        } finally {
            SlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        assertEquals(Collections.singletonList(NEW_FOLDER), persistedUris(context));
    }

    @Test
    public void aDisconnectDuringASlowChangeReleasesTheOldFolder() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try {
            pickFromChangeFolder(SLOW_FOLDER);
            assertTrue(SlowFolderProvider.entered.await(5, TimeUnit.SECONDS));
            // what the teardown dialog does
            context.getContentResolver().releasePersistableUriPermission(SLOW_FOLDER, READ_WRITE);
            context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE).edit().clear().commit();
        } finally {
            SlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        assertEquals(Collections.emptyList(), persistedUris(context));
    }

    @Test
    public void aChangeAfterOneCutShortReleasesTheFolderAutoBackupWasLeftIn() {
        Robolectric.setupContentProvider(NamedFolderProvider.class, NEW_FOLDER.getAuthority());
        Context context = ApplicationProvider.getApplicationContext();
        // what a process death leaves between storing the picked folder and moving auto backup
        context.getContentResolver().takePersistableUriPermission(OLD_FOLDER, READ_WRITE);
        useLocalFolder(context, NEW_FOLDER);
        useAutoBackupFolderInsideOld();
        pickFromChangeFolder(THIRD_FOLDER);
        joinFolderChange();
        assertEquals(Collections.singletonList(THIRD_FOLDER), persistedUris(context));
    }

    @Test
    public void pickingTheSameLocalFolderAgainKeepsItsPermission() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        pickFromChangeFolder(OLD_FOLDER);
        joinFolderChange();
        assertEquals(Collections.singletonList(OLD_FOLDER), persistedUris(context));
    }

    @Test
    public void aDisconnectConfirmedAfterAPickReleasesThePickedFolder() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BackupHandlerFragment fragment = (BackupHandlerFragment) showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_SAF);
                fragment.onMenuItemClick(new RoboMenuItem(R.id.action_change_folder));
                Intent picker = shadowOf(activity).getNextStartedActivity();
                // a listing of the old folder fails while the picker is open
                Intent failure = new Intent(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                        .putExtra(BackupHandlerIntentService.CALLER_ID, BackupHandlerFragment.BACKUP_SERVICE_CALLER_ID)
                        .putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_LIST)
                        .putExtra(BackupHandlerIntentService.EXCEPTION, new BackendException("listing", false));
                LocalBroadcastManager.getInstance(activity).sendBroadcastSync(failure);
                AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
                assertNotNull(dialog);
                shadowOf(activity).receiveResult(picker, Activity.RESULT_OK, new Intent().setData(SLOW_FOLDER));
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
            });
        } finally {
            SlowFolderProvider.release.countDown();
        }
        joinFolderChange();
        assertNull(storedLocalFolder(context));
        assertEquals(Collections.emptyList(), persistedUris(context));
    }

    @Test
    public void theAutoBackupDialogShowsAFolderInAReplacedTreeAsUnavailable() {
        Context context = ApplicationProvider.getApplicationContext();
        // what a disconnect and then a pick from the cover button leave behind
        useLocalFolder(context, NEW_FOLDER);
        useAutoBackupFolderInsideOld();
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                AlertDialog dialog = showAutoBackupSettings(activity);
                TextView folder = dialog.findViewById(R.id.auto_backup_folder_text_view);
                assertEquals(activity.getString(R.string.hint_auto_backup_folder_unavailable), folder.getText().toString());
            });
        }
    }

    @Test
    public void anOpenAutoBackupDialogRefusesAFolderLeftByADisconnect() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        // the failed listing that raises the disconnect prompt turns auto backup off first
        BackendManager.setAutoBackupEnabled(BackendServiceFactory.SERVICE_ID_SAF, false);
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                AlertDialog dialog = showAutoBackupSettings(activity);
                // what the teardown dialog does
                context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE).edit().clear().commit();
                SwitchCompat enabled = dialog.findViewById(R.id.auto_backup_enable_switch);
                enabled.setChecked(true);
                dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
                assertTrue(dialog.isShowing());
                TextView folder = dialog.findViewById(R.id.auto_backup_folder_text_view);
                assertEquals(activity.getString(R.string.hint_auto_backup_folder_unavailable), folder.getText().toString());
            });
        }
        assertFalse(BackendManager.isAutoBackupEnabled(BackendServiceFactory.SERVICE_ID_SAF));
    }

    @Test
    public void theAutoBackupDialogShowsAFolderInTheConnectedTreeByName() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                AlertDialog dialog = showAutoBackupSettings(activity);
                TextView folder = dialog.findViewById(R.id.auto_backup_folder_text_view);
                assertEquals("Backups", folder.getText().toString());
                dialog.dismiss();
            });
        }
        // the same tree id under another provider is another tree
        useLocalFolder(context, Uri.parse("content://com.example.other/tree/primary%3AOld"));
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                AlertDialog dialog = showAutoBackupSettings(activity);
                TextView folder = dialog.findViewById(R.id.auto_backup_folder_text_view);
                assertEquals(activity.getString(R.string.hint_auto_backup_folder_unavailable), folder.getText().toString());
            });
        }
    }

    @Test
    public void aRestoredAutoBackupDialogShowsAFolderLeftByADisconnectAsUnavailable() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        useAutoBackupFolderInsideOld();
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                showAutoBackupSettings(activity);
                context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE).edit().clear().commit();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                shadowOf(Looper.getMainLooper()).idle();
                AutoBackupSettingDialog settings = (AutoBackupSettingDialog) activity.getSupportFragmentManager()
                        .findFragmentByTag("AutoBackupSettingDialog");
                TextView folder = settings.getDialog().findViewById(R.id.auto_backup_folder_text_view);
                assertEquals(activity.getString(R.string.hint_auto_backup_folder_unavailable), folder.getText().toString());
            });
        }
    }

    @Test
    public void theSweepReportsAFolderInAReplacedTreeAsUnavailable() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, NEW_FOLDER);
        useAutoBackupFolderInsideOld();
        // due now, whether or not the data changed
        BackendManager.setAutoBackupLastTime(BackendServiceFactory.SERVICE_ID_SAF, 0);
        BackendManager.setAutoBackupWhenDataIsChangedOnly(BackendServiceFactory.SERVICE_ID_SAF, false);
        AutoBackupJobService.runSweep(context);
        List<String> texts = new ArrayList<>();
        for (Notification notification : shadowOf((NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE)).getAllNotifications()) {
            texts.add(String.valueOf(notification.extras.getCharSequence(Notification.EXTRA_TEXT)));
        }
        assertEquals(Collections.singletonList(context.getString(R.string.notification_content_backup_error_location)), texts);
    }

    @Test
    public void cancellingChangeFolderKeepsTheFolderInUse() {
        Context context = ApplicationProvider.getApplicationContext();
        useLocalFolder(context, OLD_FOLDER);
        View primary = pickFromChangeFolder(null);
        assertEquals(OLD_FOLDER.toString(), storedLocalFolder(context));
        assertEquals(View.VISIBLE, primary.getVisibility());
    }

    @Test
    public void aWebDavCheckFinishingAfterTheScreenIsGoneDoesNotCrash() {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BackupHandlerFragment fragment = (BackupHandlerFragment) showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_WEBDAV);
                activity.getSupportFragmentManager().beginTransaction().remove(fragment).commitNow();
                // what WebDAVBackendService.verifyAndStore reaches when the check succeeds
                fragment.onBackendStatusChange(true);
            });
        }
    }

    private static final Uri OLD_FOLDER = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AOld");
    private static final Uri NEW_FOLDER = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ANew");
    private static final Uri THIRD_FOLDER = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AThird");
    private static final Uri SLOW_FOLDER = Uri.parse("content://com.example.slow/tree/Slow");
    private static final Uri SECOND_SLOW_FOLDER = Uri.parse("content://com.example.slow2/tree/Slow2");
    private static final int READ_WRITE =Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private static void useAutoBackupFolderInsideOld() {
        String folder = "{\"uri\":\"content://com.android.externalstorage.documents/tree/primary%3AOld/document/primary%3AOld%2FBackups\","
                + "\"name\":\"Backups\",\"size\":0,\"isDir\":true}";
        BackendManager.setAutoBackupFolder(BackendServiceFactory.SERVICE_ID_SAF, folder);
        BackendManager.setAutoBackupEnabled(BackendServiceFactory.SERVICE_ID_SAF, true);
    }

    /**
     * The folder change finishes on its own thread, after the picker result has returned.
     */
    private static void joinFolderChange() {
        for (Thread thread : Thread.getAllStackTraces().keySet()) {
            if ("saf-auto-backup-folder".equals(thread.getName())) {
                try {
                    thread.join(5000);
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                assertFalse("the folder change is still running", thread.isAlive());
            }
        }
    }

    /**
     * Answers the document queries a {@code DocumentFile} makes, naming every document "New".
     */
    public static class NamedFolderProvider extends ContentProvider {

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            MatrixCursor cursor = new MatrixCursor(projection);
            Object[] row = new Object[projection.length];
            for (int i = 0; i < projection.length; i++) {
                switch (projection[i]) {
                    case DocumentsContract.Document.COLUMN_DISPLAY_NAME:
                        row[i] = "New";
                        break;
                    case DocumentsContract.Document.COLUMN_MIME_TYPE:
                        row[i] = DocumentsContract.Document.MIME_TYPE_DIR;
                        break;
                    default:
                        row[i] = 0;
                }
            }
            cursor.addRow(row);
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            return 0;
        }
    }

    /**
     * Holds the folder change thread inside its first query until {@link #release} opens, the
     * way a slow cloud provider would. Other threads are answered at once.
     */
    public static class SlowFolderProvider extends NamedFolderProvider {

        static CountDownLatch entered;
        static CountDownLatch release;

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            if ("saf-auto-backup-folder".equals(Thread.currentThread().getName())) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.query(uri, projection, selection, selectionArgs, sortOrder);
        }
    }

    /**
     * The same as {@link SlowFolderProvider}, with latches of its own for a second folder.
     */
    public static class SecondSlowFolderProvider extends NamedFolderProvider {

        static CountDownLatch entered;
        static CountDownLatch release;

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
            if ("saf-auto-backup-folder".equals(Thread.currentThread().getName())) {
                entered.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return super.query(uri, projection, selection, selectionArgs, sortOrder);
        }
    }

    private static void useLocalFolder(Context context, Uri folder) {
        context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE)
                .edit().putString("uri", folder.toString()).commit();
        context.getContentResolver().takePersistableUriPermission(folder, READ_WRITE);
    }

    private static String storedLocalFolder(Context context) {
        return context.getSharedPreferences("storage_access_framework", Context.MODE_PRIVATE)
                .getString("uri", null);
    }

    private static List<Uri> persistedUris(Context context) {
        List<Uri> uris = new ArrayList<>();
        for (UriPermission permission : context.getContentResolver().getPersistedUriPermissions()) {
            uris.add(permission.getUri());
        }
        return uris;
    }

    /**
     * Opens the Local folder screen on a folder already in use, taps Change folder and answers
     * the picker with the given folder, or cancels it when that is null. Returns the list layout.
     */
    private View pickFromChangeFolder(@Nullable Uri picked) {
        View[] primary = new View[1];
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                BackupHandlerFragment fragment = (BackupHandlerFragment) showBackupHandler(activity, BackendServiceFactory.SERVICE_ID_SAF);
                fragment.onMenuItemClick(new RoboMenuItem(R.id.action_change_folder));
                Intent picker = shadowOf(activity).getNextStartedActivity();
                assertNotNull(picker);
                assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, picker.getAction());
                Intent result = picked != null ? new Intent().setData(picked) : null;
                shadowOf(activity).receiveResult(picker, picked != null ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
                primary[0] = fragment.requireView().findViewById(R.id.primary_layout);
            });
        }
        return primary[0];
    }

    private static AlertDialog showAutoBackupSettings(FragmentActivity activity) {
        AutoBackupSettingDialog settings = new AutoBackupSettingDialog();
        settings.show(activity.getSupportFragmentManager(), "AutoBackupSettingDialog", BackendServiceFactory.SERVICE_ID_SAF);
        activity.getSupportFragmentManager().executePendingTransactions();
        shadowOf(Looper.getMainLooper()).idle();
        return (AlertDialog) settings.getDialog();
    }

    private void clickTheCoverButton(FragmentActivity activity, String backendId) {
        Fragment fragment = showBackupHandler(activity, backendId);
        View coverActionButton = fragment.requireView().findViewById(R.id.cover_action_button);
        assertNotNull(coverActionButton);
        coverActionButton.performClick();
    }

    private Fragment showBackupHandler(FragmentActivity activity, String backendId) {
        Fragment fragment = BackupHandlerFragment.newInstance(backendId, true, true);
        activity.getSupportFragmentManager()
                .beginTransaction()
                .add(android.R.id.content, fragment, "BackupHandlerFragmentTest")
                .commitNow();
        return fragment;
    }

    private void assertCoverVisibility(String backendId, int expectedCover, int expectedPrimary) {
        try (ActivityScenario<BackupListActivity> scenario = ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                Fragment fragment = showBackupHandler(activity, backendId);
                View cover = fragment.requireView().findViewById(R.id.cover_layout);
                View primary = fragment.requireView().findViewById(R.id.primary_layout);
                assertEquals(expectedCover, cover.getVisibility());
                assertEquals(expectedPrimary, primary.getVisibility());
            });
        }
    }
}
