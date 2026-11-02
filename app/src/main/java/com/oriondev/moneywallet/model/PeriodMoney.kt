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

package com.oriondev.moneywallet.model;

import com.oriondev.moneywallet.utils.DateUtils;

import java.util.Date;

/**
 * Created by andrea on 14/08/18.
 */
public class PeriodMoney {

    private final Date mStartDate;
    private final Date mEndDate;
    private final Money mIncomes;
    private final Money mExpenses;
    private final Money mNetIncomes;
    private final Money mTransfers;

    public PeriodMoney(Date startDate, Date endDate) {
        mStartDate = startDate;
        mEndDate = endDate;
        mIncomes = new Money();
        mExpenses = new Money();
        mNetIncomes = new Money();
        mTransfers = new Money();
    }

    public void addIncome(String currency, long money) {
        mIncomes.addMoney(currency, money);
        mNetIncomes.addMoney(currency, money);
    }

    public void addExpense(String currency, long money) {
        mExpenses.addMoney(currency, money);
        mNetIncomes.removeMoney(currency, money);
    }

    /**
     * Count money moved between two of the user's own wallets. It reaches the net, because a
     * wallet that sent money holds less of it afterwards, and it reaches neither of the other
     * two, because nothing was earned or spent.
     *
     * It is also kept on its own, signed, because the net is the only figure on the summary
     * screen that counts it. The two bar series do not, so a period that only moved money draws
     * both bars at nothing over a net that moved, and this is the figure that says why.
     */
    public void addTransfer(String currency, long money, boolean incoming) {
        if (incoming) {
            mNetIncomes.addMoney(currency, money);
            mTransfers.addMoney(currency, money);
        } else {
            mNetIncomes.removeMoney(currency, money);
            mTransfers.removeMoney(currency, money);
        }
    }

    public Date getStartDate() {
        return mStartDate;
    }

    public Date getEndDate() {
        return mEndDate;
    }

    public Money getIncomes() {
        return mIncomes;
    }

    public Money getExpenses() {
        return mExpenses;
    }

    public Money getNetIncomes() {
        return mNetIncomes;
    }

    public Money getTransfers() {
        return mTransfers;
    }

    public boolean isTimeRange() {
        return DateUtils.isTheSameDayAndHour(mStartDate, mEndDate);
    }
}