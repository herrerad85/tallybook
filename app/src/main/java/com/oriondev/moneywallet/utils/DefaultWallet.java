/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.utils;

import android.content.ContentResolver;
import android.database.Cursor;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

/**
 * Picks the wallet a new item opens on. The wallet you are viewing wins while you are on one; the
 * wallet chosen in settings applies on the Total view, or when the viewed wallet has been archived
 * or deleted; the Total view is the last fallback. Returning TOTAL_WALLET_ID hands the choice to
 * the editor's existing Total branch.
 */
public class DefaultWallet {

    private DefaultWallet() {
    }

    public static long resolveNewItemWallet(ContentResolver resolver) {
        long current = PreferenceManager.getCurrentWallet();
        if (current == PreferenceManager.TOTAL_WALLET_ID) {
            return configuredOrTotal(resolver);
        }
        if (current != PreferenceManager.NO_CURRENT_WALLET && isUsable(resolver, current)) {
            return current;
        }
        return configuredOrTotal(resolver);
    }

    private static long configuredOrTotal(ContentResolver resolver) {
        long configured = PreferenceManager.getDefaultWallet();
        if (configured != PreferenceManager.NO_DEFAULT_WALLET && isUsable(resolver, configured)) {
            return configured;
        }
        return PreferenceManager.TOTAL_WALLET_ID;
    }

    // The collection uri, not wallets/#: the item route drops the selection and would report an
    // archived wallet as usable, so the fallback would never fire. The collection query also
    // excludes deleted rows in its own base filter.
    private static boolean isUsable(ContentResolver resolver, long walletId) {
        Cursor cursor = resolver.query(
                DataContentProvider.CONTENT_WALLETS,
                new String[]{Contract.Wallet.ID},
                Contract.Wallet.ID + " = ? AND " + Contract.Wallet.ARCHIVED + " = 0",
                new String[]{String.valueOf(walletId)},
                null
        );
        if (cursor != null) {
            try {
                return cursor.moveToFirst();
            } finally {
                cursor.close();
            }
        }
        return false;
    }
}
