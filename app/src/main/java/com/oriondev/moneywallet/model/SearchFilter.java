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

package com.oriondev.moneywallet.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import com.oriondev.moneywallet.storage.database.Contract;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The state of the search screen: one criterion per type, each unset when null or empty, and
 * whether a transaction has to meet all of the set ones or any of them. It turns into a
 * selection over the columns of {@link Contract.Transaction}.
 */
public class SearchFilter implements Parcelable {

    public enum Match {
        ALL, ANY
    }

    public enum AmountOp {
        EXACTLY, AT_LEAST, AT_MOST, BETWEEN
    }

    public enum AmountSide {
        EITHER, OUT, IN
    }

    public enum Status {
        UNCONFIRMED, CONFIRMED
    }

    private static final BigDecimal LONG_MIN = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal LONG_MAX = BigDecimal.valueOf(Long.MAX_VALUE);

    private Match mMatch = Match.ALL;
    private final TreeSet<Long> mCategoryIds = new TreeSet<>();
    private AmountOp mAmountOp;
    private AmountSide mAmountSide;
    private BigDecimal mAmount;
    private BigDecimal mAmountTo;
    private String mDateFrom;
    private String mDateTo;
    private String mText;
    private final TreeSet<Long> mPeopleIds = new TreeSet<>();
    private Status mStatus;
    private final TreeSet<Long> mWalletIds = new TreeSet<>();
    private boolean mTransfersOnly;

    public SearchFilter() {
    }

    protected SearchFilter(Parcel in) {
        mMatch = (Match) in.readSerializable();
        readIds(in, mCategoryIds);
        mAmountOp = (AmountOp) in.readSerializable();
        mAmountSide = (AmountSide) in.readSerializable();
        mAmount = readDecimal(in);
        mAmountTo = readDecimal(in);
        mDateFrom = in.readString();
        mDateTo = in.readString();
        mText = in.readString();
        readIds(in, mPeopleIds);
        mStatus = (Status) in.readSerializable();
        readIds(in, mWalletIds);
        mTransfersOnly = in.readByte() != 0;
    }

    public static final Creator<SearchFilter> CREATOR = new Creator<SearchFilter>() {
        @Override
        public SearchFilter createFromParcel(Parcel in) {
            return new SearchFilter(in);
        }

        @Override
        public SearchFilter[] newArray(int size) {
            return new SearchFilter[size];
        }
    };

    public Match getMatch() {
        return mMatch;
    }

    public void setMatch(Match match) {
        mMatch = match;
    }

    public Set<Long> getCategoryIds() {
        return Collections.unmodifiableSet(mCategoryIds);
    }

    public void setCategoryIds(Collection<Long> ids) {
        mCategoryIds.clear();
        mCategoryIds.addAll(ids);
    }

    public AmountOp getAmountOp() {
        return mAmountOp;
    }

    public AmountSide getAmountSide() {
        return mAmountSide;
    }

    public BigDecimal getAmount() {
        return mAmount;
    }

    public BigDecimal getAmountTo() {
        return mAmountTo;
    }

    /**
     * @param op the comparison, or null to unset the amount.
     * @param side which direction of money counts.
     * @param amount the typed number, in major units of each wallet's currency.
     * @param amountTo the other end, read only by {@link AmountOp#BETWEEN}, in either order.
     */
    public void setAmount(AmountOp op, AmountSide side, BigDecimal amount, BigDecimal amountTo) {
        if (amount == null || (op == AmountOp.BETWEEN && amountTo == null)) {
            op = null;
        }
        mAmountOp = op;
        mAmountSide = op != null ? side : null;
        mAmount = op != null ? amount : null;
        mAmountTo = op == AmountOp.BETWEEN ? amountTo : null;
    }

    public boolean isAmountSet() {
        return mAmountOp != null;
    }

    public String getDateFrom() {
        return mDateFrom;
    }

    public String getDateTo() {
        return mDateTo;
    }

    /**
     * @param from first day, as the day part of a stored date, or null for no lower end.
     * @param to last day, as the day part of a stored date, or null for no upper end.
     */
    public void setDates(String from, String to) {
        mDateFrom = from;
        mDateTo = to;
    }

    public String getText() {
        return mText;
    }

    public void setText(String text) {
        String trimmed = text != null ? text.trim() : null;
        mText = TextUtils.isEmpty(trimmed) ? null : trimmed;
    }

    public Set<Long> getPeopleIds() {
        return Collections.unmodifiableSet(mPeopleIds);
    }

    public void setPeopleIds(Collection<Long> ids) {
        mPeopleIds.clear();
        mPeopleIds.addAll(ids);
    }

    public Status getStatus() {
        return mStatus;
    }

    public void setStatus(Status status) {
        mStatus = status;
    }

    public Set<Long> getWalletIds() {
        return Collections.unmodifiableSet(mWalletIds);
    }

    public boolean isTransfersOnly() {
        return mTransfersOnly;
    }

    public void setWallets(Collection<Long> ids, boolean transfersOnly) {
        mWalletIds.clear();
        mWalletIds.addAll(ids);
        mTransfersOnly = transfersOnly;
    }

    public SearchFilter copy() {
        Parcel parcel = Parcel.obtain();
        try {
            writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            return new SearchFilter(parcel);
        } finally {
            parcel.recycle();
        }
    }

    /**
     * The selection for {@link Contract.Transaction} rows meeting this filter.
     *
     * @param decimals decimals of every currency in use, keyed by iso code; read only when the
     *                 amount is set.
     * @return the selection and its arguments, both null when no type is set.
     */
    public Pair<String, String[]> toSelection(Map<String, Integer> decimals) {
        List<String> clauses = new ArrayList<>();
        List<String> args = new ArrayList<>();
        if (!mCategoryIds.isEmpty()) {
            clauses.add(Contract.Transaction.CATEGORY_ID + " IN (" + placeholders(mCategoryIds.size()) + ")");
            addIds(args, mCategoryIds);
        }
        if (mAmountOp != null) {
            clauses.add(amountClause(decimals, args));
        }
        if (mDateFrom != null || mDateTo != null) {
            clauses.add(dateClause(args));
        }
        if (mText != null) {
            String[] columns = new String[] {Contract.Transaction.DESCRIPTION,
                    Contract.Transaction.NOTE, Contract.Transaction.EVENT_NAME,
                    Contract.Transaction.PLACE_NAME};
            List<String> terms = new ArrayList<>();
            for (String column : columns) {
                terms.add("instr(lower(" + column + "), lower(?)) > 0");
                args.add(mText);
            }
            clauses.add(TextUtils.join(" OR ", terms));
        }
        if (!mPeopleIds.isEmpty()) {
            List<String> terms = new ArrayList<>();
            for (Long id : mPeopleIds) {
                terms.add(Contract.Transaction.PEOPLE_IDS + " LIKE ?");
                args.add("%<" + id + ">%");
            }
            terms.add(Contract.transferSideWithPeopleSelection(mPeopleIds.size()));
            addIds(args, mPeopleIds);
            clauses.add(TextUtils.join(" OR ", terms));
        }
        if (mStatus != null) {
            clauses.add(Contract.Transaction.CONFIRMED + " = ?");
            args.add(mStatus == Status.CONFIRMED ? "1" : "0");
        }
        if (!mWalletIds.isEmpty() || mTransfersOnly) {
            List<String> terms = new ArrayList<>();
            if (!mWalletIds.isEmpty()) {
                terms.add(Contract.Transaction.WALLET_ID + " IN (" + placeholders(mWalletIds.size()) + ")");
                addIds(args, mWalletIds);
            }
            if (mTransfersOnly) {
                terms.add(Contract.Transaction.TYPE + " = ?");
                args.add(String.valueOf(Contract.TransactionType.TRANSFER));
            }
            clauses.add(TextUtils.join(" AND ", terms));
        }
        if (clauses.isEmpty()) {
            return new Pair<>(null, null);
        }
        String selection = "(" + TextUtils.join(mMatch == Match.ANY ? ") OR (" : ") AND (", clauses) + ")";
        return new Pair<>(selection, args.toArray(new String[0]));
    }

    private String amountClause(Map<String, Integer> decimals, List<String> args) {
        List<String> terms = new ArrayList<>();
        for (Map.Entry<String, Integer> currency : new TreeMap<>(decimals).entrySet()) {
            BigDecimal amount = mAmount.movePointRight(currency.getValue());
            String iso = currency.getKey();
            switch (mAmountOp) {
                case EXACTLY:
                    if (amount.remainder(BigDecimal.ONE).signum() == 0) {
                        terms.add(currencyTerm("= ?"));
                        addBounds(args, iso, amount);
                    }
                    break;
                case AT_LEAST:
                    terms.add(currencyTerm(">= ?"));
                    addBounds(args, iso, amount.setScale(0, RoundingMode.CEILING));
                    break;
                case AT_MOST:
                    terms.add(currencyTerm("<= ?"));
                    addBounds(args, iso, amount.setScale(0, RoundingMode.FLOOR));
                    break;
                case BETWEEN:
                    BigDecimal other = mAmountTo.movePointRight(currency.getValue());
                    terms.add(currencyTerm("BETWEEN ? AND ?"));
                    addBounds(args, iso, amount.min(other).setScale(0, RoundingMode.CEILING),
                            amount.max(other).setScale(0, RoundingMode.FLOOR));
                    break;
            }
        }
        // Without a clause an amount no wallet can hold would match every row in All mode.
        if (terms.isEmpty()) {
            return "0";
        }
        String clause = TextUtils.join(" OR ", terms);
        if (mAmountSide == AmountSide.OUT || mAmountSide == AmountSide.IN) {
            clause = "(" + clause + ") AND " + Contract.Transaction.DIRECTION + " = ?";
            args.add(String.valueOf(mAmountSide == AmountSide.OUT
                    ? Contract.Direction.EXPENSE : Contract.Direction.INCOME));
        }
        return clause;
    }

    private String dateClause(List<String> args) {
        String from = mDateFrom;
        String to = mDateTo;
        if (from != null && to != null && from.compareTo(to) > 0) {
            from = mDateTo;
            to = mDateFrom;
        }
        List<String> terms = new ArrayList<>();
        if (from != null) {
            terms.add(Contract.Transaction.DATE + " >= ?");
            args.add(from + " 00:00:00");
        }
        if (to != null) {
            terms.add(Contract.Transaction.DATE + " < ?");
            args.add(nextDay(to) + " 00:00:00");
        }
        return TextUtils.join(" AND ", terms);
    }

    private static String currencyTerm(String comparison) {
        return "(" + Contract.Transaction.WALLET_CURRENCY + " = ? AND " + Contract.Transaction.MONEY + " " + comparison + ")";
    }

    private static void addBounds(List<String> args, String iso, BigDecimal... bounds) {
        args.add(iso);
        for (BigDecimal bound : bounds) {
            args.add(String.valueOf(bound.max(LONG_MIN).min(LONG_MAX).longValueExact()));
        }
    }

    private static String nextDay(String day) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US);
        try {
            calendar.setTime(format.parse(day));
        } catch (ParseException e) {
            throw new IllegalArgumentException("Not a yyyy-MM-dd day: " + day, e);
        }
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        return format.format(calendar.getTime());
    }

    private static String placeholders(int count) {
        return TextUtils.join(", ", Collections.nCopies(count, "?"));
    }

    private static void addIds(List<String> args, Set<Long> ids) {
        for (Long id : ids) {
            args.add(String.valueOf(id));
        }
    }

    private static long[] toArray(Set<Long> ids) {
        long[] array = new long[ids.size()];
        int i = 0;
        for (Long id : ids) {
            array[i++] = id;
        }
        return array;
    }

    private static void readIds(Parcel in, Set<Long> ids) {
        for (long id : in.createLongArray()) {
            ids.add(id);
        }
    }

    private static BigDecimal readDecimal(Parcel in) {
        String value = in.readString();
        return value != null ? new BigDecimal(value) : null;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeSerializable(mMatch);
        dest.writeLongArray(toArray(mCategoryIds));
        dest.writeSerializable(mAmountOp);
        dest.writeSerializable(mAmountSide);
        dest.writeString(mAmount != null ? mAmount.toString() : null);
        dest.writeString(mAmountTo != null ? mAmountTo.toString() : null);
        dest.writeString(mDateFrom);
        dest.writeString(mDateTo);
        dest.writeString(mText);
        dest.writeLongArray(toArray(mPeopleIds));
        dest.writeSerializable(mStatus);
        dest.writeLongArray(toArray(mWalletIds));
        dest.writeByte((byte) (mTransfersOnly ? 1 : 0));
    }
}
