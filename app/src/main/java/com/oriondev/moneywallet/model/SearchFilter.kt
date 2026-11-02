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

package com.oriondev.moneywallet.model

import android.os.Parcel
import android.os.Parcelable
import android.text.TextUtils

import com.oriondev.moneywallet.storage.database.Contract

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.ArrayList
import java.util.Calendar
import java.util.Collections
import java.util.Locale
import java.util.TimeZone
import java.util.TreeMap
import java.util.TreeSet

/**
 * The state of the search screen: one criterion per type, each unset when null or empty, and
 * whether a transaction has to meet all of the set ones or any of them. It turns into a
 * selection over the columns of [Contract.Transaction], and the sort into its order.
 */
class SearchFilter : Parcelable {

    enum class Match {
        ALL, ANY
    }

    enum class AmountOp {
        EXACTLY, AT_LEAST, AT_MOST, BETWEEN
    }

    enum class AmountSide {
        EITHER, OUT, IN
    }

    enum class Status {
        UNCONFIRMED, CONFIRMED
    }

    /**
     * The order of the results, each tied on the id so rows with equal keys hold still across
     * reloads. The amount sorts lead with the currency, so each currency's rows run together.
     */
    enum class Sort(private val mOrderBy: String) {
        NEWEST(Contract.Transaction.DATE + " DESC, " + Contract.Transaction.ID + " DESC"),
        OLDEST(Contract.Transaction.DATE + " ASC, " + Contract.Transaction.ID + " ASC"),
        CATEGORY(Contract.Transaction.CATEGORY_NAME + " COLLATE LOCALIZED ASC, "
                + Contract.Transaction.DATE + " DESC, " + Contract.Transaction.ID + " DESC"),
        LARGEST(Contract.Transaction.WALLET_CURRENCY + " ASC, " + Contract.Transaction.MONEY + " DESC, "
                + Contract.Transaction.DATE + " DESC, " + Contract.Transaction.ID + " DESC"),
        SMALLEST(Contract.Transaction.WALLET_CURRENCY + " ASC, " + Contract.Transaction.MONEY + " ASC, "
                + Contract.Transaction.DATE + " DESC, " + Contract.Transaction.ID + " DESC");

        fun getOrderBy(): String {
            return mOrderBy
        }

        fun isByAmount(): Boolean {
            return this === LARGEST || this === SMALLEST
        }
    }

    private var mMatch: Match? = Match.ALL
    private val mCategoryIds: TreeSet<Long> = TreeSet()
    private var mAmountOp: AmountOp? = null
    private var mAmountSide: AmountSide? = null
    private var mAmount: BigDecimal? = null
    private var mAmountTo: BigDecimal? = null
    private var mDateFrom: String? = null
    private var mDateTo: String? = null
    private var mText: String? = null
    private val mPeopleIds: TreeSet<Long> = TreeSet()
    private var mStatus: Status? = null
    private val mWalletIds: TreeSet<Long> = TreeSet()
    private var mTransfersOnly: Boolean = false
    private var mSort: Sort? = Sort.NEWEST

    constructor() {
    }

    protected constructor(source: Parcel) {
        mMatch = source.readSerializable() as Match?
        readIds(source, mCategoryIds)
        mAmountOp = source.readSerializable() as AmountOp?
        mAmountSide = source.readSerializable() as AmountSide?
        mAmount = readDecimal(source)
        mAmountTo = readDecimal(source)
        mDateFrom = source.readString()
        mDateTo = source.readString()
        mText = source.readString()
        readIds(source, mPeopleIds)
        mStatus = source.readSerializable() as Status?
        readIds(source, mWalletIds)
        mTransfersOnly = source.readByte().toInt() != 0
        mSort = source.readSerializable() as Sort?
    }

    fun getMatch(): Match? {
        return mMatch
    }

    fun setMatch(match: Match?) {
        mMatch = match
    }

    fun getCategoryIds(): Set<Long> {
        return Collections.unmodifiableSet(mCategoryIds)
    }

    fun setCategoryIds(ids: Collection<Long>?) {
        mCategoryIds.clear()
        mCategoryIds.addAll(ids!!)
    }

    fun getAmountOp(): AmountOp? {
        return mAmountOp
    }

    fun getAmountSide(): AmountSide? {
        return mAmountSide
    }

    fun getAmount(): BigDecimal? {
        return mAmount
    }

    fun getAmountTo(): BigDecimal? {
        return mAmountTo
    }

    /**
     * @param op the comparison, or null to unset the amount.
     * @param side which direction of money counts.
     * @param amount the typed number, in major units of each wallet's currency.
     * @param amountTo the other end, read only by [AmountOp.BETWEEN], in either order.
     */
    fun setAmount(op: AmountOp?, side: AmountSide?, amount: BigDecimal?, amountTo: BigDecimal?) {
        var op = op
        if (amount == null || (op === AmountOp.BETWEEN && amountTo == null)) {
            op = null
        }
        mAmountOp = op
        mAmountSide = if (op != null) side else null
        mAmount = if (op != null) amount else null
        mAmountTo = if (op === AmountOp.BETWEEN) amountTo else null
    }

    fun isAmountSet(): Boolean {
        return mAmountOp != null
    }

    fun getDateFrom(): String? {
        return mDateFrom
    }

    fun getDateTo(): String? {
        return mDateTo
    }

    /**
     * @param from first day, as the day part of a stored date, or null for no lower end.
     * @param to last day, as the day part of a stored date, or null for no upper end.
     */
    fun setDates(from: String?, to: String?) {
        mDateFrom = from
        mDateTo = to
    }

    fun getText(): String? {
        return mText
    }

    fun setText(text: String?) {
        val trimmed: String? = if (text != null) text.trim { it <= ' ' } else null
        mText = if (TextUtils.isEmpty(trimmed)) null else trimmed
    }

    fun getPeopleIds(): Set<Long> {
        return Collections.unmodifiableSet(mPeopleIds)
    }

    fun setPeopleIds(ids: Collection<Long>?) {
        mPeopleIds.clear()
        mPeopleIds.addAll(ids!!)
    }

    fun getStatus(): Status? {
        return mStatus
    }

    fun setStatus(status: Status?) {
        mStatus = status
    }

    fun getWalletIds(): Set<Long> {
        return Collections.unmodifiableSet(mWalletIds)
    }

    fun isTransfersOnly(): Boolean {
        return mTransfersOnly
    }

    fun setWallets(ids: Collection<Long>?, transfersOnly: Boolean) {
        mWalletIds.clear()
        mWalletIds.addAll(ids!!)
        mTransfersOnly = transfersOnly
    }

    fun getSort(): Sort? {
        return mSort
    }

    fun setSort(sort: Sort?) {
        mSort = sort
    }

    fun copy(): SearchFilter {
        val parcel = Parcel.obtain()
        try {
            writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return SearchFilter(parcel)
        } finally {
            parcel.recycle()
        }
    }

    /**
     * The selection for [Contract.Transaction] rows meeting this filter.
     *
     * @param decimals decimals of every currency in use, keyed by iso code; read only when the
     *                 amount is set.
     * @return the selection and its arguments, both null when no type is set.
     */
    fun toSelection(decimals: Map<String, Int>?): Pair<String?, Array<String?>?> {
        val clauses: MutableList<String> = ArrayList()
        val args: MutableList<String?> = ArrayList()
        if (!mCategoryIds.isEmpty()) {
            clauses.add(Contract.Transaction.CATEGORY_ID + " IN (" + placeholders(mCategoryIds.size) + ")")
            addIds(args, mCategoryIds)
        }
        if (mAmountOp != null) {
            clauses.add(amountClause(decimals, args))
        }
        if (mDateFrom != null || mDateTo != null) {
            clauses.add(dateClause(args))
        }
        if (mText != null) {
            val columns = arrayOf(Contract.Transaction.DESCRIPTION,
                    Contract.Transaction.NOTE, Contract.Transaction.EVENT_NAME,
                    Contract.Transaction.PLACE_NAME)
            val terms: MutableList<String> = ArrayList()
            for (column in columns) {
                terms.add("instr(lower(" + column + "), lower(?)) > 0")
                args.add(mText)
            }
            clauses.add(TextUtils.join(" OR ", terms))
        }
        if (!mPeopleIds.isEmpty()) {
            val terms: MutableList<String> = ArrayList()
            for (id in mPeopleIds) {
                terms.add(Contract.Transaction.PEOPLE_IDS + " LIKE ?")
                args.add("%<" + id + ">%")
            }
            terms.add(Contract.transferSideWithPeopleSelection(mPeopleIds.size))
            addIds(args, mPeopleIds)
            clauses.add(TextUtils.join(" OR ", terms))
        }
        if (mStatus != null) {
            clauses.add(Contract.Transaction.CONFIRMED + " = ?")
            args.add(if (mStatus === Status.CONFIRMED) "1" else "0")
        }
        if (!mWalletIds.isEmpty() || mTransfersOnly) {
            val terms: MutableList<String> = ArrayList()
            if (!mWalletIds.isEmpty()) {
                terms.add(Contract.Transaction.WALLET_ID + " IN (" + placeholders(mWalletIds.size) + ")")
                addIds(args, mWalletIds)
            }
            if (mTransfersOnly) {
                terms.add(Contract.Transaction.TYPE + " = ?")
                args.add(Contract.TransactionType.TRANSFER.toString())
            }
            clauses.add(TextUtils.join(" AND ", terms))
        }
        if (clauses.isEmpty()) {
            return Pair(null, null)
        }
        val selection = "(" + TextUtils.join(if (mMatch === Match.ANY) ") OR (" else ") AND (", clauses) + ")"
        return Pair(selection, args.toTypedArray())
    }

    private fun amountClause(decimals: Map<String, Int>?, args: MutableList<String?>): String {
        val terms: MutableList<String> = ArrayList()
        for (currency in TreeMap(decimals).entries) {
            val amount = mAmount!!.movePointRight(currency.value)
            val iso: String = currency.key
            when (mAmountOp!!) {
                AmountOp.EXACTLY -> {
                    if (amount.remainder(BigDecimal.ONE).signum() == 0) {
                        terms.add(currencyTerm("= ?"))
                        addBounds(args, iso, amount)
                    }
                }
                AmountOp.AT_LEAST -> {
                    terms.add(currencyTerm(">= ?"))
                    addBounds(args, iso, amount.setScale(0, RoundingMode.CEILING))
                }
                AmountOp.AT_MOST -> {
                    terms.add(currencyTerm("<= ?"))
                    addBounds(args, iso, amount.setScale(0, RoundingMode.FLOOR))
                }
                AmountOp.BETWEEN -> {
                    val other = mAmountTo!!.movePointRight(currency.value)
                    terms.add(currencyTerm("BETWEEN ? AND ?"))
                    addBounds(args, iso, amount.min(other).setScale(0, RoundingMode.CEILING),
                            amount.max(other).setScale(0, RoundingMode.FLOOR))
                }
            }
        }
        // Without a clause an amount no wallet can hold would match every row in All mode.
        if (terms.isEmpty()) {
            return "0"
        }
        var clause = TextUtils.join(" OR ", terms)
        if (mAmountSide === AmountSide.OUT || mAmountSide === AmountSide.IN) {
            clause = "(" + clause + ") AND " + Contract.Transaction.DIRECTION + " = ?"
            args.add((if (mAmountSide === AmountSide.OUT)
                    Contract.Direction.EXPENSE else Contract.Direction.INCOME).toString())
        }
        return clause
    }

    private fun dateClause(args: MutableList<String?>): String {
        var from = mDateFrom
        var to = mDateTo
        if (from != null && to != null && from.compareTo(to) > 0) {
            from = mDateTo
            to = mDateFrom
        }
        val terms: MutableList<String> = ArrayList()
        if (from != null) {
            terms.add(Contract.Transaction.DATE + " >= ?")
            args.add(from + " 00:00:00")
        }
        if (to != null) {
            terms.add(Contract.Transaction.DATE + " < ?")
            args.add(nextDay(to) + " 00:00:00")
        }
        return TextUtils.join(" AND ", terms)
    }

    override fun describeContents(): Int {
        return 0
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeSerializable(mMatch)
        dest.writeLongArray(toArray(mCategoryIds))
        dest.writeSerializable(mAmountOp)
        dest.writeSerializable(mAmountSide)
        dest.writeString(if (mAmount != null) mAmount.toString() else null)
        dest.writeString(if (mAmountTo != null) mAmountTo.toString() else null)
        dest.writeString(mDateFrom)
        dest.writeString(mDateTo)
        dest.writeString(mText)
        dest.writeLongArray(toArray(mPeopleIds))
        dest.writeSerializable(mStatus)
        dest.writeLongArray(toArray(mWalletIds))
        dest.writeByte((if (mTransfersOnly) 1 else 0).toByte())
        dest.writeSerializable(mSort)
    }

    companion object {

        private val LONG_MIN: BigDecimal = BigDecimal.valueOf(Long.MIN_VALUE)
        private val LONG_MAX: BigDecimal = BigDecimal.valueOf(Long.MAX_VALUE)

        @JvmField
        val CREATOR: Parcelable.Creator<SearchFilter> = object : Parcelable.Creator<SearchFilter> {
            override fun createFromParcel(source: Parcel): SearchFilter {
                return SearchFilter(source)
            }

            override fun newArray(size: Int): Array<SearchFilter?> {
                return arrayOfNulls(size)
            }
        }

        private fun currencyTerm(comparison: String): String {
            return "(" + Contract.Transaction.WALLET_CURRENCY + " = ? AND " + Contract.Transaction.MONEY + " " + comparison + ")"
        }

        private fun addBounds(args: MutableList<String?>, iso: String, vararg bounds: BigDecimal) {
            args.add(iso)
            for (bound in bounds) {
                args.add(bound.max(LONG_MIN).min(LONG_MAX).longValueExact().toString())
            }
        }

        private fun nextDay(day: String): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
            try {
                calendar.time = format.parse(day)
            } catch (e: ParseException) {
                throw IllegalArgumentException("Not a yyyy-MM-dd day: " + day, e)
            }
            calendar.add(Calendar.DAY_OF_MONTH, 1)
            return format.format(calendar.time)
        }

        private fun placeholders(count: Int): String {
            return TextUtils.join(", ", Collections.nCopies(count, "?"))
        }

        private fun addIds(args: MutableList<String?>, ids: Set<Long>) {
            for (id in ids) {
                args.add(id.toString())
            }
        }

        private fun toArray(ids: Set<Long>): LongArray {
            val array = LongArray(ids.size)
            var i = 0
            for (id in ids) {
                array[i++] = id
            }
            return array
        }

        private fun readIds(source: Parcel, ids: MutableSet<Long>) {
            for (id in source.createLongArray()!!) {
                ids.add(id)
            }
        }

        private fun readDecimal(source: Parcel): BigDecimal? {
            val value = source.readString()
            return if (value != null) BigDecimal(value) else null
        }
    }
}
