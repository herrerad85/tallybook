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

package com.oriondev.moneywallet.storage.wrapper;

import android.database.Cursor;
import android.database.CursorWrapper;

import java.util.Date;

public class BudgetPeriodCursor extends CursorWrapper {

    private final Date[][] mPeriodStartDates;
    private final long[][] mPeriodProgress;

    public BudgetPeriodCursor(Cursor cursor, Date[][] periodStartDates, long[][] periodProgress) {
        super(cursor);
        mPeriodStartDates = periodStartDates;
        mPeriodProgress = periodProgress;
    }

    public Date[] getPeriodStartDates() {
        int position = getPosition();
        return position >= 0 && position < mPeriodStartDates.length ? mPeriodStartDates[position] : null;
    }

    public long[] getPeriodProgress() {
        int position = getPosition();
        return position >= 0 && position < mPeriodProgress.length ? mPeriodProgress[position] : null;
    }
}
