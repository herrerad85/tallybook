package com.oriondev.moneywallet.ui.activity

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem

import androidx.loader.content.CursorLoader
import androidx.loader.content.Loader
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.service.BackupHandlerIntentService
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.DeletedItems
import com.oriondev.moneywallet.ui.activity.base.SinglePanelSimpleListActivity
import com.oriondev.moneywallet.ui.adapter.recycler.AbstractCursorAdapter
import com.oriondev.moneywallet.ui.adapter.recycler.DeletedItemCursorAdapter
import com.oriondev.moneywallet.ui.fragment.base.DeletedItemSnackbar
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

import java.util.concurrent.Executor
import java.util.concurrent.Executors

class RecentlyDeletedActivity : SinglePanelSimpleListActivity(), DeletedItemCursorAdapter.ActionListener {

    companion object {

        private const val TAG = "RecentlyDeletedActivity"

        private val sDeletedItemExecutor: Executor = Executors.newSingleThreadExecutor { Thread(it, "RecentlyDeletedItems") }
        private val sMainHandler = Handler(Looper.getMainLooper())
    }

    private var mEmptyAllItem: MenuItem? = null

    private var mNewestDeletedAt = 0L

    private val mBroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent?) {
            if (intent?.getIntExtra(BackupHandlerIntentService.ACTION, 0) == BackupHandlerIntentService.ACTION_RESTORE) {
                recreateLoader()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LocalBroadcastManager.getInstance(this).registerReceiver(mBroadcastReceiver,
                IntentFilter(LocalAction.ACTION_BACKUP_SERVICE_FINISHED).apply {
                    addAction(LocalAction.ACTION_BACKUP_SERVICE_FAILED)
                })
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mBroadcastReceiver)
        super.onDestroy()
    }

    override fun onPrepareRecyclerView(recyclerView: AdvancedRecyclerView) {
        recyclerView.setLayoutManager(LinearLayoutManager(this))
        recyclerView.setEmptyText(R.string.message_no_deleted_item_found)
    }

    override fun onCreateAdapter(): AbstractCursorAdapter<*> = DeletedItemCursorAdapter(this)

    override fun getActivityTitleRes(): Int = R.string.title_activity_recently_deleted

    override fun onInflateMenu(): Int = R.menu.menu_recently_deleted

    override fun onMenuCreated(menu: Menu) {
        mEmptyAllItem = menu.findItem(R.id.action_empty_all)
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_empty_all) {
            ThemedDialog.buildMaterialDialog(this)
                    .setMessage(R.string.message_empty_recently_deleted)
                    .setPositiveButton(R.string.action_empty) { _, _ ->
                        delete(DeletedItems.deletedUri(DataContentProvider.CONTENT_ALL),
                                arrayOf(mNewestDeletedAt.toString()))
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            return true
        }
        return false
    }

    override fun isFloatingActionButtonEnabled(): Boolean = false

    override fun onCreateLoader(id: Int, args: Bundle?): Loader<Cursor> =
            CursorLoader(this, DeletedItems.deletedUri(DataContentProvider.CONTENT_ALL), null, null, null, null)

    override fun onLoadFinished(loader: Loader<Cursor>, cursor: Cursor?) {
        super.onLoadFinished(loader, cursor)
        mEmptyAllItem?.isVisible = cursor != null && cursor.count > 0
        mNewestDeletedAt = 0L
        if (cursor != null) {
            val index = cursor.getColumnIndex(Contract.DeletedItem.DELETED_AT)
            for (position in 0 until cursor.count) {
                cursor.moveToPosition(position)
                mNewestDeletedAt = maxOf(mNewestDeletedAt, cursor.getLong(index))
            }
        }
    }

    override fun onLoaderReset(loader: Loader<Cursor>) {
        super.onLoaderReset(loader)
        mNewestDeletedAt = 0L
    }

    override fun onDeletedItemClick(type: Int, id: Long, deletedAt: Long, uuid: String, title: CharSequence) {
        val selectionArgs = arrayOf(deletedAt.toString(), uuid)
        val items = if (type == Contract.DeletedItemType.TRANSFER) {
            DataContentProvider.CONTENT_TRANSFERS
        } else {
            DataContentProvider.CONTENT_TRANSACTIONS
        }
        ThemedDialog.buildMaterialDialog(this)
                .setTitle(title)
                .setPositiveButton(R.string.action_restore) { _, _ ->
                    restore(ContentUris.withAppendedId(items, id), selectionArgs)
                }
                .setNeutralButton(R.string.action_delete_forever) { _, _ ->
                    showDeleteForeverDialog(ContentUris.withAppendedId(DeletedItems.deletedUri(items), id),
                            selectionArgs)
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun showDeleteForeverDialog(deletedItemUri: Uri, selectionArgs: Array<String>) {
        ThemedDialog.buildMaterialDialog(this)
                .setMessage(R.string.message_delete_forever)
                .setPositiveButton(R.string.action_delete_forever) { _, _ -> delete(deletedItemUri, selectionArgs) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun restore(itemUri: Uri, selectionArgs: Array<String>) {
        val resolver = applicationContext.contentResolver
        sDeletedItemExecutor.execute {
            val refused = DeletedItems.restore(resolver, listOf(itemUri), selectionArgs)
            sMainHandler.post {
                if (!isDestroyed && refused.isNotEmpty()) {
                    DeletedItemSnackbar.showRestoreError(this, refused)
                }
            }
        }
    }

    private fun delete(uri: Uri, selectionArgs: Array<String>?) {
        val resolver = applicationContext.contentResolver
        sDeletedItemExecutor.execute {
            try {
                resolver.delete(uri, null, selectionArgs)
            } catch (t: Throwable) {
                Log.e(TAG, "unable to delete $uri", t)
            }
        }
    }
}
