/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.storage.database;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link Contract.Transaction#SPLIT_PARTS}, written with an insert or an update, holds every
 * part of the transaction as a JSON array of objects, each holding {@link Contract.TransactionSplit#PART_CATEGORY},
 * {@link Contract.TransactionSplit#PART_MONEY} and {@link Contract.TransactionSplit#PART_NOTE}.
 * The parts it holds replace every part the transaction had, an empty array leaves it unsplit,
 * and a write without it leaves the parts alone. Refused with
 * {@link Contract.ErrorCode#SPLIT_NOT_VALID} when the parts could not be counted.
 */
public class SplitPartsCodec {

    /**
     * Only a number that is not finite is refused, and both numbers here are longs.
     */
    public static String encode(List<Part> parts) {
        JSONArray array = new JSONArray();
        try {
            for (Part part : parts) {
                JSONObject object = new JSONObject();
                object.put(Contract.TransactionSplit.PART_CATEGORY, part.mCategoryId);
                object.put(Contract.TransactionSplit.PART_MONEY, part.mMoney);
                object.put(Contract.TransactionSplit.PART_NOTE, part.mNote != null ? part.mNote : JSONObject.NULL);
                array.put(object);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        return array.toString();
    }

    public static List<Part> decode(String encodedParts) throws JSONException {
        List<Part> parts = new ArrayList<>();
        JSONArray array = new JSONArray(TextUtils.isEmpty(encodedParts) ? "[]" : encodedParts);
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.getJSONObject(i);
            parts.add(new Part(object.getLong(Contract.TransactionSplit.PART_CATEGORY),
                    object.getLong(Contract.TransactionSplit.PART_MONEY),
                    object.isNull(Contract.TransactionSplit.PART_NOTE)
                            ? null : object.getString(Contract.TransactionSplit.PART_NOTE)));
        }
        return parts;
    }

    public static class Part {

        private final long mCategoryId;
        private final long mMoney;
        private final String mNote;

        public Part(long categoryId, long money, String note) {
            mCategoryId = categoryId;
            mMoney = money;
            mNote = note;
        }

        public long getCategoryId() {
            return mCategoryId;
        }

        public long getMoney() {
            return mMoney;
        }

        public String getNote() {
            return mNote;
        }
    }
}
