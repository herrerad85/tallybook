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

package com.oriondev.moneywallet.ui.activity;

import com.oriondev.moneywallet.storage.database.Contract;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * What the transaction editor decides, apart from the screen it decides it on. Plain values in
 * and plain values out, so each rule can be driven from a JVM test instead of being pinned by
 * matching the text of the activity.
 *
 * The editor keeps the loading. Every branch of it chooses which row to read and reading takes a
 * content resolver, so what lives here is only what is decided once the values are in hand.
 *
 * It carries the five pieces of state the editor holds across a recreate. They travel as one
 * object under one bundle key, so the keys cannot collide with each other.
 */
public class TransactionEditorRules implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int TYPE_STANDARD = 0;
    public static final int TYPE_TRANSFER = 1;
    public static final int TYPE_DEBT = 2;
    public static final int TYPE_SAVING = 3;
    public static final int TYPE_MODEL = 4;

    public static final int DEBT_PAY = 1;
    public static final int DEBT_RECEIVE = 2;
    public static final int DEBT_PAY_IN_FULL = 3;
    public static final int DEBT_RECEIVE_IN_FULL = 4;

    public static final int SAVING_DEPOSIT = 1;
    public static final int SAVING_WITHDRAW = 2;
    public static final int SAVING_WITHDRAW_EVERYTHING = 3;

    private int mType = TYPE_STANDARD;
    private Long mDebtId = null;
    private Long mSavingId = null;
    private boolean mSavingCompleted = false;
    private boolean mDebtPayment = false;

    public int getType() {
        return mType;
    }

    public void setType(int type) {
        mType = type;
    }

    public Long getDebtId() {
        return mDebtId;
    }

    public void setDebtId(Long debtId) {
        mDebtId = debtId;
    }

    public Long getSavingId() {
        return mSavingId;
    }

    public void setSavingId(Long savingId) {
        mSavingId = savingId;
    }

    public boolean isSavingCompleted() {
        return mSavingCompleted;
    }

    public void setSavingCompleted(boolean savingCompleted) {
        mSavingCompleted = savingCompleted;
    }

    public boolean isDebtPayment() {
        return mDebtPayment;
    }

    public void setDebtPayment(boolean debtPayment) {
        mDebtPayment = debtPayment;
    }

    /**
     * A debt row and a saving row are both filed under a category the editor picks for them, so
     * neither offers the field.
     */
    public boolean hidesCategoryField() {
        return mType == TYPE_DEBT || mType == TYPE_SAVING;
    }

    /**
     * A saving's progress and a debt's progress are each summed over the transactions filed
     * against them with no currency anywhere in that sum, so a row moved onto a wallet held in
     * another currency is added at face value and 500 euros count as 500 dollars.
     *
     * A debt's master transaction is not one of those rows. It carries the same TYPE_DEBT as a
     * payment and is told apart only by its category, and editing its wallet moves the debt
     * itself through syncDebtOfMasterTransaction, so it keeps the field.
     */
    public boolean hidesWalletField() {
        return mType == TYPE_SAVING || (mType == TYPE_DEBT && mDebtPayment);
    }

    /** Whether a category this row is filed under makes it a payment and not a debt's own row. */
    public static boolean isDebtPaymentTag(String categoryTag) {
        return Contract.CategoryTag.PAID_DEBT.equals(categoryTag)
                || Contract.CategoryTag.PAID_CREDIT.equals(categoryTag);
    }

    /**
     * The kind of debt an action names, for a launch that carries no debt row of its own. Answers
     * null for anything else, which is what a crafted intent naming no action gives.
     */
    public static Contract.DebtType debtTypeFor(int debtAction) {
        switch (debtAction) {
            case DEBT_PAY:
            case DEBT_PAY_IN_FULL:
                return Contract.DebtType.DEBT;
            case DEBT_RECEIVE:
            case DEBT_RECEIVE_IN_FULL:
                return Contract.DebtType.CREDIT;
            default:
                return null;
        }
    }

    /** Only the in full actions open on an amount; pay and receive open on nothing. */
    public static boolean settlesInFull(int debtAction) {
        return debtAction == DEBT_PAY_IN_FULL || debtAction == DEBT_RECEIVE_IN_FULL;
    }

    /**
     * What settling in full opens on, which is the figure the debt card shows as left to settle.
     * A debt's payments sum negative, hence the abs, and one paid past its target offers nothing.
     */
    public static long settleDebtPrefill(long target, long progress) {
        return Math.max(target - Math.abs(progress), 0L);
    }

    /** The category a new debt row is filed under, or null when the kind is not known. */
    public static String debtCategoryTag(Contract.DebtType debtType) {
        if (debtType == null) {
            return null;
        }
        switch (debtType) {
            case DEBT:
                return Contract.CategoryTag.PAID_DEBT;
            case CREDIT:
                return Contract.CategoryTag.PAID_CREDIT;
            default:
                return null;
        }
    }

    /**
     * The category a new saving row is filed under. Withdraw everything is a withdrawal, so the
     * two answer the same tag; the activity used to reach that by falling through one case into
     * the other, where a break left the tag null and the query below crashed the editor as it
     * opened.
     */
    public static String savingCategoryTag(int savingAction) {
        switch (savingAction) {
            case SAVING_DEPOSIT:
                return Contract.CategoryTag.SAVING_DEPOSIT;
            case SAVING_WITHDRAW:
            case SAVING_WITHDRAW_EVERYTHING:
                return Contract.CategoryTag.SAVING_WITHDRAW;
            default:
                return null;
        }
    }

    /** Only withdraw everything empties the saving, so only it marks the saving complete. */
    public static boolean completesTheSaving(int savingAction) {
        return savingAction == SAVING_WITHDRAW_EVERYTHING;
    }

    /**
     * The most a withdrawal may take. Held at nothing, since a saving already under zero on some
     * date from here on gives a negative figure and no amount at all would clear it.
     */
    public static long withdrawLimit(long lowestBalance) {
        return Math.max(lowestBalance, 0L);
    }

    /**
     * What withdraw everything opens on, which is the same figure the check applies when the save
     * is pressed, since the row it writes is dated now. A saving whose rows do not come back
     * offers nothing.
     */
    public static long withdrawEverythingPrefill(Long lowestBalance) {
        return lowestBalance != null ? withdrawLimit(lowestBalance) : 0L;
    }

    /** Whether an amount clears the ceiling. */
    public static boolean isWithinLimit(long money, long limit) {
        return money <= limit;
    }

    /**
     * The ceiling is in the saving's own currency. A row sitting in a wallet held in another one
     * is not comparable with it as it stands and this screen converts nowhere else, so the check
     * steps aside instead of comparing amounts that do not mean the same thing. A saving that
     * gives back no row at all leaves its currency null and stops here too.
     */
    public static boolean ceilingApplies(String savingCurrencyIso, String walletCurrencyIso) {
        return savingCurrencyIso != null && walletCurrencyIso != null
                && savingCurrencyIso.equals(walletCurrencyIso);
    }

    /**
     * Whether the row being edited is being kept or lowered on a date it does not move back from.
     * Such a save takes nothing the saving is not already giving, so it is never refused.
     *
     * Without this, two withdrawals that already have a saving under zero would freeze each
     * other, since neither can be lowered while the other one alone is more than the saving
     * holds, and deleting one would be the only way out. Moving a stored row earlier is a new
     * drain on the dates it moves across and is held to the ceiling like any other.
     */
    public static boolean isStoredWithdrawalKeptOrLowered(long money, String date,
                                                          long storedMoney, String storedDate) {
        return money <= storedMoney && date.compareTo(storedDate) >= 0;
    }

    /** A withdrawal is the income half of the pair, since it pays money into the wallet. */
    public static boolean isWithdrawal(int direction) {
        return direction == Contract.Direction.INCOME;
    }

    /**
     * A deposit that is not confirmed is left out, because landing takes being confirmed and
     * money that never arrives must not pay for a withdrawal that does. A withdrawal is counted
     * whether it is confirmed or not, for the mirror reason, that leaving it out hands out a
     * ceiling it can then take the saving under.
     */
    public static boolean countsTowardBalance(SavingRow row) {
        return isWithdrawal(row.direction) || row.confirmed;
    }

    /** A withdrawal takes the saving down and a deposit puts money in. */
    public static long signedAmount(SavingRow row) {
        return isWithdrawal(row.direction) ? -row.money : row.money;
    }

    /**
     * The lowest a saving's balance reaches from a moment onwards, over the rows it already
     * carries.
     *
     * The rows are put in date order here rather than taken in the order they arrive, so the
     * answer does not depend on how the caller asked for them. A date this app writes is a fixed
     * width yyyy-MM-dd HH:mm:ss of ASCII, so comparing the strings is comparing the times, which
     * is the same order {@link Contract#lowestSavingBalanceFrom(long, String[], long[], String)}
     * compares against and the same order SQLite gives on that column.
     *
     * excludedId names the row being edited, whose own drain has to come out before the walk or
     * the row would be held against itself. A new row names nothing, since the id of a new item
     * is minus one and no row carries it.
     */
    public static long lowestSavingBalanceFrom(long startMoney, List<SavingRow> rows,
                                               long excludedId, String from) {
        List<SavingRow> counted = new ArrayList<>();
        for (SavingRow row : rows) {
            if (row.id != excludedId && countsTowardBalance(row)) {
                counted.add(row);
            }
        }
        Collections.sort(counted, new Comparator<SavingRow>() {

            @Override
            public int compare(SavingRow left, SavingRow right) {
                return left.date.compareTo(right.date);
            }

        });
        String[] dates = new String[counted.size()];
        long[] signedMoney = new long[counted.size()];
        for (int i = 0; i < counted.size(); i++) {
            dates[i] = counted.get(i).date;
            signedMoney[i] = signedAmount(counted.get(i));
        }
        return Contract.lowestSavingBalanceFrom(startMoney, dates, signedMoney, from);
    }

    /** One row filed against a saving, as the walk above needs it. */
    public static final class SavingRow {

        public final long id;
        public final String date;
        public final long money;
        public final int direction;
        public final boolean confirmed;

        public SavingRow(long id, String date, long money, int direction, boolean confirmed) {
            this.id = id;
            this.date = date;
            this.money = money;
            this.direction = direction;
            this.confirmed = confirmed;
        }

    }

}
