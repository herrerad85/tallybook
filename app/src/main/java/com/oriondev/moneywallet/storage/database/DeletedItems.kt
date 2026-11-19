package com.oriondev.moneywallet.storage.database

import android.content.ContentResolver
import android.net.Uri

object DeletedItems {

    @JvmStatic
    fun deletedUri(itemUri: Uri): Uri = Uri.withAppendedPath(itemUri, "deleted")

    fun restore(resolver: ContentResolver, itemUris: List<Uri>, selectionArgs: Array<String>? = null):
            List<SQLiteDataException> = itemUris.mapNotNull { itemUri ->
        try {
            resolver.update(Uri.withAppendedPath(itemUri, "restore"), null, null, selectionArgs)
            null
        } catch (e: SQLiteDataException) {
            e
        }
    }
}
