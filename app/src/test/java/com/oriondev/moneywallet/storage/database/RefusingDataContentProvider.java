package com.oriondev.moneywallet.storage.database;

import android.content.ContentUris;
import android.content.ContentValues;
import android.net.Uri;

import androidx.annotation.NonNull;

// the database refuses no plain transaction on update, so a refusal test needs one refused on purpose
public class RefusingDataContentProvider extends DataContentProvider {

    public static long sRefusedId;

    public static String authority() {
        return AUTHORITY;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        if (ContentUris.parseId(uri) == sRefusedId) {
            throw new SQLiteDataException(Contract.ErrorCode.TRANSACTION_USED_IN_TRANSFER, "refused on purpose");
        }
        return super.update(uri, values, selection, selectionArgs);
    }
}
