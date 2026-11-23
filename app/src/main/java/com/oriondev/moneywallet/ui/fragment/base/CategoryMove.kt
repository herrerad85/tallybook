package com.oriondev.moneywallet.ui.fragment.base

import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.database.SQLException
import android.os.Bundle

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Category
import com.oriondev.moneywallet.picker.CategoryPicker
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.SQLiteDataException
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog

object CategoryMove {

    private const val TAG_CATEGORY_PICKER = "CategoryMove::Tag::CategoryPicker"

    @JvmStatic
    fun showMoveDialog(panel: SecondaryPanelFragment, categoryId: Long): Boolean {
        val activity = panel.activity ?: return false
        val resolver = activity.contentResolver
        val use = resolver.call(DataContentProvider.CONTENT_ALL, DataContentProvider.CALL_CATEGORY_IN_USE, null,
                Bundle().apply { putLong(DataContentProvider.KEY_CATEGORY_ID, categoryId) })
        if (use == null || !use.getBoolean(DataContentProvider.KEY_IN_USE)) {
            return false
        }
        val transactions = use.getLong(DataContentProvider.KEY_TRANSACTIONS).toInt()
        val message = if (transactions > 0) {
            activity.resources.getQuantityString(R.plurals.message_move_category_transactions, transactions, transactions)
        } else {
            activity.getString(R.string.message_move_category_items)
        }
        val type = typeOf(resolver, categoryId)
        ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.title_warning)
                .setMessage(message)
                .setPositiveButton(R.string.action_move) { _, _ -> showPicker(panel, type) }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        return true
    }

    @JvmStatic
    fun onCategoryPicked(panel: SecondaryPanelFragment, categoryId: Long, category: Category?): Boolean {
        if (category == null || !panel.isAdded) {
            return false
        }
        val fragmentManager = panel.childFragmentManager
        val picker = fragmentManager.findFragmentByTag(TAG_CATEGORY_PICKER) ?: return false
        fragmentManager.beginTransaction().remove(picker).commitAllowingStateLoss()
        if (category.getId() == categoryId) {
            return false
        }
        val activity = panel.requireActivity()
        val resolver = activity.applicationContext.contentResolver
        val record = try {
            resolver.call(DataContentProvider.CONTENT_ALL, DataContentProvider.CALL_MOVE_CATEGORY, null, Bundle().apply {
                putLong(DataContentProvider.KEY_CATEGORY_ID, categoryId)
                putLong(DataContentProvider.KEY_TARGET_ID, category.getId())
            })
        } catch (e: SQLiteDataException) {
            showRefusal(activity, e)
            null
        } ?: return false
        val host = panel.parentFragment
        if (host is MultiPanelFragment) {
            DeletedItemSnackbar.show(host.primaryPanel, activity.getString(R.string.snackbar_message_category_deleted)) {
                restore(activity, resolver, record)
            }
        }
        return true
    }

    private fun showPicker(panel: SecondaryPanelFragment, type: Contract.CategoryType?) {
        if (!panel.isAdded) {
            return
        }
        val fragmentManager = panel.childFragmentManager
        val picker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, null as Category?)
        fragmentManager.executePendingTransactions()
        picker.showPicker(type)
    }

    private fun typeOf(resolver: ContentResolver, categoryId: Long): Contract.CategoryType? =
            resolver.query(ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId),
                    arrayOf(Contract.Category.TYPE), null, null, null)?.use {
                if (it.moveToFirst()) Contract.CategoryType.fromValue(it.getInt(0)) else null
            }

    private fun restore(activity: Activity, resolver: ContentResolver, record: Bundle) {
        val restored = try {
            resolver.call(DataContentProvider.CONTENT_ALL, DataContentProvider.CALL_RESTORE_CATEGORY, null, record)
                    ?.getBoolean(DataContentProvider.KEY_RESTORED) == true
        } catch (e: SQLException) {
            false
        }
        if (!restored) {
            ThemedDialog.buildMaterialDialog(activity)
                    .setTitle(R.string.title_error)
                    .setMessage(R.string.message_error_restore_category)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
        }
    }

    private fun showRefusal(activity: Activity, e: SQLiteDataException) {
        val message = when (e.errorCode) {
            Contract.ErrorCode.CATEGORY_HAS_CHILDREN -> R.string.message_error_delete_category_with_children
            Contract.ErrorCode.SYSTEM_CATEGORY_NOT_MODIFIABLE -> R.string.message_error_delete_system_category
            else -> return
        }
        ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.title_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
    }
}
