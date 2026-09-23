package com.oriondev.moneywallet.api.saf;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.model.SAFFile;
import com.oriondev.moneywallet.storage.preference.BackendManager;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import androidx.activity.ComponentActivity;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;

/**
 * Backend service used to access files over the android storage access framework.
 */
public class SAFBackendService extends AbstractBackendServiceDelegate {

    /**
     * Flag containing both {@link Intent#FLAG_GRANT_READ_URI_PERMISSION} and
     * {@link Intent#FLAG_GRANT_WRITE_URI_PERMISSION}.
     */
    private static final int FLAG_URI_READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION |
    Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
    private static final String PREFERENCE_FILE = "storage_access_framework";
    /**
     * Preference key were the root uri is stored.
     */
    private static final String URI = "uri";

    private ActivityResultLauncher<Uri> mFolderLauncher;

    public SAFBackendService(BackendServiceStatusListener listener) {
        super(listener);
    }

    /**
     * Extension of the {@link OpenDocumentTree} contract including
     * extra {@link #FLAG_URI_READ_WRITE} and {@link Intent#FLAG_GRANT_PERSISTABLE_URI_PERMISSION}
     * flags.
     */
    private static class DocumentTreeContract extends OpenDocumentTree {
        @NonNull
        @Override
        public Intent createIntent(@NonNull Context context, @Nullable Uri input) {
            Intent intent = super.createIntent(context, input);
            intent.setFlags(FLAG_URI_READ_WRITE |
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            return intent;
        }
    }

    @Override
    public String getId() {
        return BackendServiceFactory.SERVICE_ID_SAF;
    }

    @Override
    public int getName() {
        return R.string.service_backup_storage_access_framework;
    }

    @Override
    public int getBackupCoverMessage() {
        return R.string.cover_message_backup_storage_access_framework_title;
    }

    @Override
    public int getBackupCoverAction() {
        return R.string.cover_message_backup_storage_access_framework_button;
    }

    @Override
    public boolean isServiceEnabled(Context context) {
        return getUri(context) != null;
    }

    @Override
    public void registerLaunchers(@NonNull final Fragment fragment) {
        mFolderLauncher = fragment.registerForActivityResult(
            new DocumentTreeContract(),
            new ActivityResultCallback<Uri>() {
                @Override
                public void onActivityResult(Uri uri) {
                    Activity activity = fragment.requireActivity();
                    if (uri != null) {
                        Uri previous = getUri(activity);
                        activity.getContentResolver().takePersistableUriPermission(
                                uri,
                                FLAG_URI_READ_WRITE
                        );
                        storeUri(activity, uri);
                        if (previous != null && !previous.equals(uri)) {
                            onFolderChanged(activity.getApplicationContext(), previous, uri);
                        }
                    }
                    // a Change folder that was cancelled keeps the folder already in use
                    setBackendServiceEnabled(getUri(activity) != null);
                }
            });
    }

    /**
     * Moves auto backup to the new folder, then drops the permission on the folder being
     * replaced. The auto backup folder is a document inside the old folder, and once that
     * permission is gone every scheduled backup to it would fail, so the permission goes last:
     * if the process dies first, auto backup still points at a folder the app can reach.
     */
    private static void onFolderChanged(final Context context, final Uri previous, final Uri current) {
        new Thread(() -> {
            // a second change can start before this one finishes, and the checks against the
            // stored folder below only hold if the two do not interleave
            synchronized (FOLDER_CHANGE_LOCK) {
                String backendId = BackendServiceFactory.SERVICE_ID_SAF;
                String encoded = BackendManager.getAutoBackupFolder(backendId);
                Uri replaced = null;
                // a quick change back finds auto backup still inside the folder it returns to
                if (encoded != null && !current.equals(treeOf(encoded))) {
                    DocumentFile root = DocumentFile.fromTreeUri(context, current);
                    SAFFile folder = root != null ? new SAFFile(root) : null;
                    if (!current.equals(getUri(context))) {
                        // a later change replaced this folder and moves auto backup itself
                    } else if (folder != null && folder.getName() != null) {
                        BackendManager.setAutoBackupFolder(backendId, folder.encodeToString());
                        replaced = treeOf(encoded);
                    } else {
                        // a descriptor with no name cannot be decoded again
                        BackendManager.disableAutoBackupAfterFailure(backendId);
                        BackendManager.setAutoBackupFolder(backendId, null);
                        replaced = treeOf(encoded);
                    }
                }
                releaseUnlessInUse(context, previous);
                if (replaced != null && !replaced.equals(previous)) {
                    releaseUnlessInUse(context, replaced);
                }
            }
        }, "saf-auto-backup-folder").start();
    }

    private static final Object FOLDER_CHANGE_LOCK = new Object();

    private static void releaseUnlessInUse(Context context, Uri tree) {
        if (tree.equals(getUri(context))) {
            return;
        }
        try {
            context.getContentResolver().releasePersistableUriPermission(tree, FLAG_URI_READ_WRITE);
        } catch (SecurityException e) {
            Log.w("SAFBackendService", "No permission held on the replaced folder", e);
        }
    }

    /**
     * The picked folder an encoded auto backup folder lies in, or null when it names none.
     */
    private static Uri treeOf(String encoded) {
        SAFFile folder = SAFFile.decode(encoded);
        if (folder == null || !DocumentsContract.isTreeUri(folder.getUri())) {
            return null;
        }
        Uri uri = folder.getUri();
        return DocumentsContract.buildTreeDocumentUri(uri.getAuthority(), DocumentsContract.getTreeDocumentId(uri));
    }

    @Override
    public boolean isDisconnectable() {
        return false;
    }

    @Override
    public boolean isFolderChangeable() {
        return true;
    }

    @Override
    public String describeLocation(Context context) {
        Uri uri = getUri(context);
        if (uri == null) {
            return null;
        }
        DocumentFile root = DocumentFile.fromTreeUri(context, uri);
        String folder = root != null ? root.getName() : null;
        if (folder == null) {
            return null;
        }
        PackageManager packageManager = context.getPackageManager();
        ProviderInfo provider = packageManager.resolveContentProvider(uri.getAuthority(), 0);
        if (provider == null) {
            return folder;
        }
        return context.getString(R.string.backup_location_in_app, provider.loadLabel(packageManager), folder);
    }

    @Override
    public void setup(final ComponentActivity activity) {
        mFolderLauncher.launch(null);
    }

    @Override
    public void teardown(final ComponentActivity activity) {
        if (getUri(activity) == null) {
            return;
        }
        ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_backup_service_storage_access_framework_disconnect)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // read at the tap: Change folder can store a new folder while this
                        // dialog is up, and the pending change releases the one it replaced
                        Uri uri = getUri(activity);
                        if (uri != null) {
                            try {
                                activity.getContentResolver()
                                        .releasePersistableUriPermission(uri, FLAG_URI_READ_WRITE);
                            } catch (SecurityException e) {
                                Log.w("SAFBackendService", "No permission held on the folder", e);
                            }
                        }
                        clearUri(activity);
                        setBackendServiceEnabled(false);
                    }

                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }

    /*package private*/ static Uri getUri(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCE_FILE, Context.MODE_PRIVATE
        );
        String accessToken = preferences.getString(URI, null);
        if (accessToken == null) {
            return null;
        }
        return Uri.parse(accessToken);
    }

    /*package private*/ static void clearUri(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCE_FILE, Context.MODE_PRIVATE
        );
        preferences.edit().clear().apply();
    }

    /*package private*/ static void storeUri(Context context, Uri uri) {
        SharedPreferences preferences = context.getSharedPreferences(
                PREFERENCE_FILE, Context.MODE_PRIVATE
        );
        preferences.edit().putString(URI, uri.toString()).apply();
    }

    @Override
    public boolean handleActivityResult(Context context, int requestCode, int resultCode, Intent data) {
        // Do nothing. This is handled by the ActivityResultCallback.
        return false;
    }
}
