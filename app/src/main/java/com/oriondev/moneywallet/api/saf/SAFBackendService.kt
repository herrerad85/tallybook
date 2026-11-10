package com.oriondev.moneywallet.api.saf

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ProviderInfo
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.model.SAFFile
import com.oriondev.moneywallet.storage.preference.BackendManager
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment

import java.util.Objects

/**
 * Backend service used to access files over the android storage access framework.
 */
class SAFBackendService : AbstractBackendServiceDelegate {

    private var mFolderLauncher: ActivityResultLauncher<Uri?>? = null

    constructor(listener: BackendServiceStatusListener?) : super(listener)

    /**
     * Extension of the [OpenDocumentTree] contract including
     * extra [FLAG_URI_READ_WRITE] and [Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION]
     * flags.
     */
    private class DocumentTreeContract : OpenDocumentTree() {
        override fun createIntent(context: Context, input: Uri?): Intent {
            val intent = super.createIntent(context, input)
            intent.flags = FLAG_URI_READ_WRITE or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            return intent
        }
    }

    override fun getId(): String {
        return BackendServiceFactory.SERVICE_ID_SAF
    }

    override fun getName(): Int {
        return R.string.service_backup_storage_access_framework
    }

    override fun getBackupCoverMessage(): Int {
        return R.string.cover_message_backup_storage_access_framework_title
    }

    override fun getBackupCoverAction(): Int {
        return R.string.cover_message_backup_storage_access_framework_button
    }

    override fun isServiceEnabled(context: Context?): Boolean {
        return getUri(context!!) != null
    }

    override fun registerLaunchers(fragment: Fragment) {
        mFolderLauncher = fragment.registerForActivityResult(
            DocumentTreeContract(),
            object : ActivityResultCallback<Uri?> {
                override fun onActivityResult(uri: Uri?) {
                    val activity: Activity = fragment.requireActivity()
                    if (uri != null) {
                        val previous = getUri(activity)
                        activity.contentResolver.takePersistableUriPermission(
                                uri,
                                FLAG_URI_READ_WRITE
                        )
                        storeUri(activity, uri)
                        if (previous != null && previous != uri) {
                            onFolderChanged(activity.applicationContext, previous, uri)
                        }
                    }
                    // a Change folder that was cancelled keeps the folder already in use
                    setBackendServiceEnabled(getUri(activity) != null)
                }
            })
    }

    override fun isDisconnectable(): Boolean {
        return false
    }

    override fun isFolderChangeable(): Boolean {
        return true
    }

    override fun describeLocation(context: Context?): String? {
        val uri = getUri(context!!)
        if (uri == null) {
            return null
        }
        val root = DocumentFile.fromTreeUri(context, uri)
        val folder = if (root != null) root.name else null
        if (folder == null) {
            return null
        }
        val packageManager = context.packageManager
        val provider: ProviderInfo? = packageManager.resolveContentProvider(uri.authority!!, 0)
        if (provider == null) {
            return folder
        }
        return context.getString(R.string.backup_location_in_app, provider.loadLabel(packageManager), folder)
    }

    override fun setup(activity: ComponentActivity?) {
        mFolderLauncher!!.launch(null)
    }

    override fun teardown(activity: ComponentActivity?) {
        if (getUri(activity!!) == null) {
            return
        }
        ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_backup_service_storage_access_framework_disconnect)
                .setPositiveButton(android.R.string.yes, object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        // read at the tap: Change folder can store a new folder while this
                        // dialog is up, and the pending change releases the one it replaced
                        val uri = getUri(activity)
                        if (uri != null) {
                            try {
                                activity.contentResolver
                                        .releasePersistableUriPermission(uri, FLAG_URI_READ_WRITE)
                            } catch (e: SecurityException) {
                                Log.w("SAFBackendService", "No permission held on the folder", e)
                            }
                        }
                        clearUri(activity)
                        setBackendServiceEnabled(false)
                    }

                })
                .setNegativeButton(android.R.string.no, null)
                .show()
    }

    override fun handleActivityResult(context: Context?, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        // Do nothing. This is handled by the ActivityResultCallback.
        return false
    }

    companion object {

        /**
         * Flag containing both [Intent.FLAG_GRANT_READ_URI_PERMISSION] and
         * [Intent.FLAG_GRANT_WRITE_URI_PERMISSION].
         */
        private const val FLAG_URI_READ_WRITE = Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val PREFERENCE_FILE = "storage_access_framework"
        /**
         * Preference key were the root uri is stored.
         */
        private const val URI = "uri"

        /**
         * Moves auto backup to the new folder, then drops the permission on the folder being
         * replaced. The auto backup folder is a document inside the old folder, and once that
         * permission is gone every scheduled backup to it would fail, so the permission goes last:
         * if the process dies first, auto backup still points at a folder the app can reach.
         */
        private fun onFolderChanged(context: Context, previous: Uri, current: Uri) {
            Thread({
                // a second change can start before this one finishes, and the checks against the
                // stored folder below only hold if the two do not interleave
                synchronized(FOLDER_CHANGE_LOCK) {
                    val backendId = BackendServiceFactory.SERVICE_ID_SAF
                    val encoded: String? = BackendManager.getAutoBackupFolder(backendId)
                    var replaced: Uri? = null
                    // a quick change back finds auto backup still inside the folder it returns to
                    if (encoded != null && current != treeOf(encoded)) {
                        val root = DocumentFile.fromTreeUri(context, current)
                        val folder = if (root != null) SAFFile(root) else null
                        if (current != getUri(context)) {
                            // a later change replaced this folder and moves auto backup itself
                        } else if (folder != null && folder.getName() != null) {
                            BackendManager.setAutoBackupFolder(backendId, folder.encodeToString())
                            replaced = treeOf(encoded)
                        } else {
                            // a descriptor with no name cannot be decoded again
                            BackendManager.disableAutoBackupAfterFailure(backendId)
                            BackendManager.setAutoBackupFolder(backendId, null)
                            replaced = treeOf(encoded)
                        }
                    }
                    releaseUnlessInUse(context, previous)
                    if (replaced != null && replaced != previous) {
                        releaseUnlessInUse(context, replaced)
                    }
                }
            }, "saf-auto-backup-folder").start()
        }

        private val FOLDER_CHANGE_LOCK = Any()

        private fun releaseUnlessInUse(context: Context, tree: Uri) {
            if (tree == getUri(context)) {
                return
            }
            try {
                context.contentResolver.releasePersistableUriPermission(tree, FLAG_URI_READ_WRITE)
            } catch (e: SecurityException) {
                Log.w("SAFBackendService", "No permission held on the replaced folder", e)
            }
        }

        /**
         * True for a Local folder file that does not lie in the folder connected now, which is what
         * a disconnect or a replaced folder leaves behind in the auto backup settings. Its permission
         * is gone, so a backup there can only fail. Every other file is false. Compares the decoded
         * tree ids, since a provider may encode one differently from how the platform rebuilds it.
         */
        @JvmStatic
        fun isOutsideCurrentFolder(context: Context?, file: IFile?): Boolean {
            if (file !is SAFFile) {
                return false
            }
            val uri = file.getUri()
            val root = getUri(context!!)
            return root == null || !DocumentsContract.isTreeUri(uri)
                    || !Objects.equals(root.authority, uri.authority)
                    || !DocumentsContract.getTreeDocumentId(root).equals(DocumentsContract.getTreeDocumentId(uri))
        }

        /**
         * The picked folder an encoded auto backup folder lies in, or null when it names none.
         */
        private fun treeOf(encoded: String): Uri? {
            val folder = SAFFile.decode(encoded)
            if (folder == null || !DocumentsContract.isTreeUri(folder.getUri())) {
                return null
            }
            val uri = folder.getUri()
            return DocumentsContract.buildTreeDocumentUri(uri.authority, DocumentsContract.getTreeDocumentId(uri))
        }

        @JvmStatic
        internal fun getUri(context: Context): Uri? {
            val preferences: SharedPreferences = context.getSharedPreferences(
                    PREFERENCE_FILE, Context.MODE_PRIVATE
            )
            val accessToken = preferences.getString(URI, null)
            if (accessToken == null) {
                return null
            }
            return Uri.parse(accessToken)
        }

        private fun clearUri(context: Context) {
            val preferences: SharedPreferences = context.getSharedPreferences(
                    PREFERENCE_FILE, Context.MODE_PRIVATE
            )
            preferences.edit().clear().apply()
        }

        private fun storeUri(context: Context, uri: Uri) {
            val preferences: SharedPreferences = context.getSharedPreferences(
                    PREFERENCE_FILE, Context.MODE_PRIVATE
            )
            preferences.edit().putString(URI, uri.toString()).apply()
        }
    }
}
