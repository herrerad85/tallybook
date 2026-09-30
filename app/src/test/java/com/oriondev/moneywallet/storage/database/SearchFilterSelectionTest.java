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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.os.Parcel;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.model.Pair;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.model.SearchFilter.AmountOp;
import com.oriondev.moneywallet.model.SearchFilter.AmountSide;
import com.oriondev.moneywallet.model.SearchFilter.Match;
import com.oriondev.moneywallet.model.SearchFilter.Status;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Which transactions a {@link SearchFilter} selects, run through {@link SQLDatabase#getTransactions}
 * on real SQLite the way {@link CategoryRuleMatchTest} runs its lookup. Every expected set is
 * written from what the rows mean, never from the SQL the filter builds.
 */
@RunWith(RobolectricTestRunner.class)
public class SearchFilterSelectionTest {

    private static final String NAME = "search.db";

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private static final String DATE = "2024-03-15 10:00:00";

    private SQLDatabase mDatabase;
    private Map<String, Integer> mDecimals;

    private long mEuro;
    private long mEuroSavings;
    private long mYen;
    private long mDinar;
    private long mFood;
    private long mRent;
    private long mAlice;
    private long mBob;
    private long mCarol;
    private long mHoliday;
    private long mCafe;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        mDatabase = new SQLDatabase(context, NAME);
        mEuro = insertWallet("Euro", "EUR");
        mEuroSavings = insertWallet("Euro savings", "EUR");
        mYen = insertWallet("Yen", "JPY");
        mDinar = insertWallet("Dinar", "BHD");
        mDecimals = new HashMap<>();
        mDecimals.put("EUR", 2);
        mDecimals.put("JPY", 0);
        mDecimals.put("BHD", 3);
        mFood = insertCategory("Food");
        mRent = insertCategory("Rent");
        mAlice = insertPerson("Alice");
        mBob = insertPerson("Bob");
        mCarol = insertPerson("Carol");
        mHoliday = insertEvent("Summer Holiday");
        mCafe = insertPlace("Cafe Luna");
    }

    @After
    public void tearDown() {
        mDatabase.close();
    }

    @Test
    public void anEmptyFilterHasNoSelectionAndListsEveryRow() {
        long a = insert(row(mEuro, 100));
        long b = insert(row(mYen, 200));
        SearchFilter filter = new SearchFilter();
        Pair<String, String[]> selection = filter.toSelection(null);
        assertNull(selection.getL());
        assertNull(selection.getR());
        assertEquals(ids(a, b), select(filter));
    }

    @Test
    public void categoryListsOnlyRowsInTickedCategories() {
        long food = insert(row(mEuro, 100));
        ContentValues rent = row(mEuro, 100);
        rent.put(Contract.Transaction.CATEGORY_ID, mRent);
        long rentId = insert(rent);
        SearchFilter filter = new SearchFilter();
        filter.setCategoryIds(Collections.singletonList(mFood));
        assertEquals(ids(food), select(filter));
        filter.setCategoryIds(Arrays.asList(mFood, mRent));
        assertEquals(ids(food, rentId), select(filter));
    }

    @Test
    public void atLeastRoundsTheBoundUpInCurrenciesOfZeroTwoAndThreeDecimals() {
        insert(row(mEuro, 1234));
        long euro = insert(row(mEuro, 1235));
        insert(row(mYen, 12));
        long yen = insert(row(mYen, 13));
        insert(row(mDinar, 12340));
        long dinar = insert(row(mDinar, 12341));
        assertEquals(ids(euro, yen, dinar), select(amount(AmountOp.AT_LEAST, "12.3405")));
    }

    @Test
    public void atMostRoundsTheBoundDownInCurrenciesOfZeroTwoAndThreeDecimals() {
        long euro = insert(row(mEuro, 1234));
        insert(row(mEuro, 1235));
        long yen = insert(row(mYen, 12));
        insert(row(mYen, 13));
        long dinar = insert(row(mDinar, 12340));
        insert(row(mDinar, 12341));
        assertEquals(ids(euro, yen, dinar), select(amount(AmountOp.AT_MOST, "12.3405")));
    }

    @Test
    public void exactlyMatchesTheSameAmountInEveryCurrency() {
        long euro = insert(row(mEuro, 1200));
        insert(row(mEuro, 12));
        long yen = insert(row(mYen, 12));
        long dinar = insert(row(mDinar, 12000));
        insert(row(mDinar, 12));
        assertEquals(ids(euro, yen, dinar), select(amount(AmountOp.EXACTLY, "12")));
    }

    @Test
    public void exactlyWithAFractionACurrencyCannotHoldDropsOnlyThatCurrency() {
        long euro = insert(row(mEuro, 1250));
        insert(row(mYen, 12));
        insert(row(mYen, 13));
        long dinar = insert(row(mDinar, 12500));
        assertEquals(ids(euro, dinar), select(amount(AmountOp.EXACTLY, "12.5")));
    }

    @Test
    public void betweenGivenBackwardsSwapsAndRoundsBothEndsInward() {
        insert(row(mEuro, 1200));
        long euroLow = insert(row(mEuro, 1201));
        long euroHigh = insert(row(mEuro, 1300));
        insert(row(mEuro, 1301));
        insert(row(mYen, 12));
        long yen = insert(row(mYen, 13));
        insert(row(mYen, 14));
        insert(row(mDinar, 12000));
        long dinarLow = insert(row(mDinar, 12001));
        long dinarHigh = insert(row(mDinar, 13005));
        insert(row(mDinar, 13006));
        SearchFilter filter = new SearchFilter();
        filter.setAmount(AmountOp.BETWEEN, AmountSide.EITHER, new BigDecimal("13.005"), new BigDecimal("12.001"));
        assertEquals(ids(euroLow, euroHigh, yen, dinarLow, dinarHigh), select(filter));
    }

    @Test
    public void amountBoundsCompareAsNumbersNotAsText() {
        long nine = insert(row(mEuro, 900));
        long ten = insert(row(mEuro, 1000));
        assertEquals(ids(ten), select(amount(AmountOp.AT_LEAST, "9.50")));
        assertEquals(ids(nine), select(amount(AmountOp.AT_MOST, "9.50")));
    }

    @Test
    public void amountSideOutListsExpensesAndInListsIncomes() {
        long out = insert(row(mEuro, 1000));
        ContentValues income = row(mEuro, 1000);
        income.put(Contract.Transaction.DIRECTION, Contract.Direction.INCOME);
        long in = insert(income);
        assertEquals(ids(out, in), select(amount(AmountOp.EXACTLY, "10", AmountSide.EITHER)));
        assertEquals(ids(out), select(amount(AmountOp.EXACTLY, "10", AmountSide.OUT)));
        assertEquals(ids(in), select(amount(AmountOp.EXACTLY, "10", AmountSide.IN)));
    }

    @Test
    public void anAmountNoCurrencyCanHoldMatchesNothingInAllMode() {
        insert(row(mEuro, 0));
        insert(row(mYen, 0));
        insert(row(mDinar, 0));
        ContentValues unconfirmed = row(mEuro, 0);
        unconfirmed.put(Contract.Transaction.CONFIRMED, false);
        insert(unconfirmed);
        for (AmountSide side : AmountSide.values()) {
            SearchFilter filter = amount(AmountOp.EXACTLY, "0.0001", side);
            assertEquals(side.name(), ids(), select(filter));
            filter.setStatus(Status.CONFIRMED);
            assertEquals(side.name(), ids(), select(filter));
        }
    }

    @Test
    public void anAmountNoCurrencyCanHoldLeavesTheOtherTypesToDecideInAnyMode() {
        long euro = insert(row(mEuro, 0));
        long yen = insert(row(mYen, 0));
        ContentValues unconfirmed = row(mDinar, 0);
        unconfirmed.put(Contract.Transaction.CONFIRMED, false);
        insert(unconfirmed);
        for (AmountSide side : AmountSide.values()) {
            SearchFilter filter = amount(AmountOp.EXACTLY, "0.0001", side);
            filter.setMatch(Match.ANY);
            assertEquals(side.name(), ids(), select(filter));
            filter.setStatus(Status.CONFIRMED);
            assertEquals(side.name(), ids(euro, yen), select(filter));
        }
    }

    @Test
    public void anAmountTooBigForALongClampsInsteadOfThrowing() {
        long a = insert(row(mEuro, 1000));
        long b = insert(row(mYen, 1000000000000000L));
        String huge = "1E+30";
        assertEquals(ids(), select(amount(AmountOp.AT_LEAST, huge)));
        assertEquals(ids(a, b), select(amount(AmountOp.AT_MOST, huge)));
        assertEquals(ids(), select(amount(AmountOp.EXACTLY, huge)));
        SearchFilter between = new SearchFilter();
        between.setAmount(AmountOp.BETWEEN, AmountSide.EITHER, BigDecimal.ZERO, new BigDecimal(huge));
        assertEquals(ids(a, b), select(between));
    }

    @Test
    public void dateBetweenTwoDaysIncludesBothWholeDays() {
        insert(dated("2024-03-09 23:59:59"));
        long first = insert(dated("2024-03-10 00:00:00"));
        long last = insert(dated("2024-03-12 23:59:59"));
        insert(dated("2024-03-13 00:00:00"));
        SearchFilter filter = new SearchFilter();
        filter.setDates("2024-03-10", "2024-03-12");
        assertEquals(ids(first, last), select(filter));
        filter.setDates("2024-03-12", "2024-03-10");
        assertEquals(ids(first, last), select(filter));
    }

    @Test
    public void dateWithOneEndOpenReachesToTheOtherSide() {
        long before = insert(dated("2020-01-01 08:00:00"));
        long day = insert(dated("2024-03-10 12:00:00"));
        long after = insert(dated("2030-06-01 08:00:00"));
        SearchFilter filter = new SearchFilter();
        filter.setDates("2024-03-10", null);
        assertEquals(ids(day, after), select(filter));
        filter.setDates(null, "2024-03-10");
        assertEquals(ids(before, day), select(filter));
    }

    @Test
    public void dateEndingOnTheLastDayOfAMonthOrYearIncludesThatDayOnly() {
        long leap = insert(dated("2024-02-29 23:59:59"));
        insert(dated("2024-03-01 00:00:00"));
        long newYearsEve = insert(dated("2024-12-31 23:59:59"));
        insert(dated("2025-01-01 00:00:00"));
        SearchFilter filter = new SearchFilter();
        filter.setDates("2024-02-29", "2024-02-29");
        assertEquals(ids(leap), select(filter));
        filter.setDates("2024-12-31", "2024-12-31");
        assertEquals(ids(newYearsEve), select(filter));
    }

    @Test
    public void textMatchesDescriptionNoteEventAndPlaceIgnoringCase() {
        ContentValues description = row(mEuro, 100);
        description.put(Contract.Transaction.DESCRIPTION, "LUNA park tickets");
        long byDescription = insert(description);
        ContentValues note = row(mEuro, 100);
        note.put(Contract.Transaction.NOTE, "paid at luna");
        long byNote = insert(note);
        ContentValues event = row(mEuro, 100);
        event.put(Contract.Transaction.EVENT_ID, mHoliday);
        long byEvent = insert(event);
        ContentValues place = row(mEuro, 100);
        place.put(Contract.Transaction.PLACE_ID, mCafe);
        long byPlace = insert(place);
        insert(row(mEuro, 100));
        SearchFilter filter = new SearchFilter();
        filter.setText("  Luna ");
        assertEquals(ids(byDescription, byNote, byPlace), select(filter));
        filter.setText("HOLIDAY");
        assertEquals(ids(byEvent), select(filter));
    }

    @Test
    public void textWithAPercentOrAnUnderscoreMatchesThoseCharactersLiterally() {
        long percent = insert(described("50% off"));
        insert(described("500 units"));
        long underscore = insert(described("file_name"));
        insert(described("filename"));
        insert(described("filexname"));
        SearchFilter filter = new SearchFilter();
        filter.setText("50%");
        assertEquals(ids(percent), select(filter));
        filter.setText("e_n");
        assertEquals(ids(underscore), select(filter));
    }

    @Test
    public void textEmptyAfterTrimmingIsUnset() {
        long a = insert(described("anything"));
        SearchFilter filter = new SearchFilter();
        filter.setText(" \t ");
        assertNull(filter.getText());
        assertNull(filter.toSelection(null).getL());
        assertEquals(ids(a), select(filter));
    }

    @Test
    public void peopleListsLinkedTransactionsAndEverySideOfLinkedTransfers() {
        long alice = insert(withPeople("<" + mAlice + ">"));
        long bob = insert(withPeople("<" + mBob + ">"));
        long both = insert(withPeople("<" + mAlice + ">,<" + mBob + ">"));
        insert(withPeople(null));
        long[] taxed = insertTransfer(mEuro, mEuroSavings, 50L, "<" + mAlice + ">");
        long[] untaxed = insertTransfer(mEuro, mYen, 0L, "<" + mCarol + ">");
        insertTransfer(mEuro, mEuroSavings, 25L, null);
        assertEquals(3, taxed.length);
        assertEquals(2, untaxed.length);
        SearchFilter filter = new SearchFilter();
        filter.setPeopleIds(Collections.singletonList(mAlice));
        assertEquals(ids(alice, both, taxed[0], taxed[1], taxed[2]), select(filter));
        filter.setPeopleIds(Collections.singletonList(mCarol));
        assertEquals(ids(untaxed[0], untaxed[1]), select(filter));
        filter.setPeopleIds(Arrays.asList(mBob, mCarol));
        assertEquals(ids(bob, both, untaxed[0], untaxed[1]), select(filter));
    }

    @Test
    public void peopleDoesNotListAPersonWhoseIdStartsWithTheTickedOne() {
        long longer;
        do {
            longer = insertPerson("Dave");
        } while (!String.valueOf(longer).startsWith(String.valueOf(mAlice)));
        long alice = insert(withPeople("<" + mAlice + ">"));
        insert(withPeople("<" + longer + ">"));
        SearchFilter filter = new SearchFilter();
        filter.setPeopleIds(Collections.singletonList(mAlice));
        assertEquals(ids(alice), select(filter));
    }

    @Test
    public void peopleDoesNotListAPersonWhoseIdEndsWithTheTickedOne() {
        long longer;
        do {
            longer = insertPerson("Dave");
        } while (longer == mAlice || !String.valueOf(longer).endsWith(String.valueOf(mAlice)));
        long alice = insert(withPeople("<" + mAlice + ">"));
        insert(withPeople("<" + longer + ">"));
        SearchFilter filter = new SearchFilter();
        filter.setPeopleIds(Collections.singletonList(mAlice));
        assertEquals(ids(alice), select(filter));
    }

    @Test
    public void statusListsConfirmedOrUnconfirmedRows() {
        long confirmed = insert(row(mEuro, 100));
        ContentValues pending = row(mEuro, 100);
        pending.put(Contract.Transaction.CONFIRMED, false);
        long unconfirmed = insert(pending);
        SearchFilter filter = new SearchFilter();
        filter.setStatus(Status.CONFIRMED);
        assertEquals(ids(confirmed), select(filter));
        filter.setStatus(Status.UNCONFIRMED);
        assertEquals(ids(unconfirmed), select(filter));
    }

    @Test
    public void walletListsRowsInTickedWallets() {
        long euro = insert(row(mEuro, 100));
        long yen = insert(row(mYen, 100));
        insert(row(mDinar, 100));
        SearchFilter filter = new SearchFilter();
        filter.setWallets(Arrays.asList(mEuro, mYen), false);
        assertEquals(ids(euro, yen), select(filter));
    }

    @Test
    public void walletWithTransfersOnlyListsThatWalletsSideOfEachTransfer() {
        insert(row(mEuro, 100));
        long[] taxed = insertTransfer(mEuro, mEuroSavings, 50L, null);
        long[] incoming = insertTransfer(mYen, mEuro, 0L, null);
        long[] elsewhere = insertTransfer(mYen, mDinar, 0L, null);
        SearchFilter filter = new SearchFilter();
        filter.setWallets(Collections.singletonList(mEuro), true);
        assertEquals(ids(taxed[0], taxed[2], incoming[1]), select(filter));
        filter.setWallets(Collections.<Long>emptyList(), true);
        assertEquals(ids(taxed[0], taxed[1], taxed[2], incoming[0], incoming[1],
                elsewhere[0], elsewhere[1]), select(filter));
    }

    @Test
    public void allNeedsEveryTypeAndAnyNeedsOne() {
        long both = insert(row(mEuro, 100));
        long walletOnly = insert(row(mEuro, 100, false));
        long statusOnly = insert(row(mYen, 100));
        insert(row(mYen, 100, false));
        SearchFilter filter = new SearchFilter();
        filter.setWallets(Collections.singletonList(mEuro), false);
        filter.setStatus(Status.CONFIRMED);
        assertEquals(ids(both), select(filter));
        filter.setMatch(Match.ANY);
        assertEquals(ids(both, walletOnly, statusOnly), select(filter));
    }

    @Test
    public void allKeepsATypeJoinedByOrTogetherAgainstTheOtherTypes() {
        long food = insert(described("luna park"));
        ContentValues rent = row(mEuro, 100);
        rent.put(Contract.Transaction.CATEGORY_ID, mRent);
        rent.put(Contract.Transaction.NOTE, "luna park");
        insert(rent);
        insert(row(mEuro, 100));
        SearchFilter filter = new SearchFilter();
        filter.setCategoryIds(Collections.singletonList(mFood));
        filter.setText("luna");
        assertEquals(ids(food), select(filter));
    }

    @Test
    public void aParcelRoundTripKeepsEveryType() {
        SearchFilter filter = new SearchFilter();
        filter.setMatch(Match.ANY);
        filter.setCategoryIds(Arrays.asList(mRent, mFood));
        filter.setAmount(AmountOp.BETWEEN, AmountSide.IN, new BigDecimal("12.50"), new BigDecimal("3"));
        filter.setDates("2024-01-01", "2024-02-01");
        filter.setText("luna");
        filter.setPeopleIds(Arrays.asList(mBob, mAlice));
        filter.setStatus(Status.UNCONFIRMED);
        filter.setWallets(Arrays.asList(mYen, mEuro), true);
        Parcel parcel = Parcel.obtain();
        filter.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        SearchFilter back = SearchFilter.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        assertEquals(Match.ANY, back.getMatch());
        assertEquals(ids(mFood, mRent), back.getCategoryIds());
        assertEquals(AmountOp.BETWEEN, back.getAmountOp());
        assertEquals(AmountSide.IN, back.getAmountSide());
        assertEquals(new BigDecimal("12.50"), back.getAmount());
        assertEquals(new BigDecimal("3"), back.getAmountTo());
        assertEquals("2024-01-01", back.getDateFrom());
        assertEquals("2024-02-01", back.getDateTo());
        assertEquals("luna", back.getText());
        assertEquals(ids(mAlice, mBob), back.getPeopleIds());
        assertEquals(Status.UNCONFIRMED, back.getStatus());
        assertEquals(ids(mEuro, mYen), back.getWalletIds());
        assertTrue(back.isTransfersOnly());
        assertTrue(back.isAmountSet());
        assertSameSelection(filter, back);
        assertSameSelection(filter, filter.copy());
    }

    @Test
    public void aParcelRoundTripKeepsUnsetTypesUnset() {
        SearchFilter empty = new SearchFilter();
        SearchFilter textOnly = new SearchFilter();
        textOnly.setMatch(Match.ANY);
        textOnly.setText("luna");
        for (SearchFilter filter : Arrays.asList(empty, textOnly)) {
            Parcel parcel = Parcel.obtain();
            filter.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            SearchFilter back = SearchFilter.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            for (SearchFilter copy : Arrays.asList(back, filter.copy())) {
                assertEquals(filter.getMatch(), copy.getMatch());
                assertTrue(copy.getCategoryIds().isEmpty());
                assertNull(copy.getAmountOp());
                assertNull(copy.getAmountSide());
                assertNull(copy.getAmount());
                assertNull(copy.getAmountTo());
                assertFalse(copy.isAmountSet());
                assertNull(copy.getDateFrom());
                assertNull(copy.getDateTo());
                assertEquals(filter.getText(), copy.getText());
                assertTrue(copy.getPeopleIds().isEmpty());
                assertNull(copy.getStatus());
                assertTrue(copy.getWalletIds().isEmpty());
                assertFalse(copy.isTransfersOnly());
                assertSameSelection(filter, copy);
            }
        }
    }

    @Test
    public void unsettingTheAmountClearsItsValues() {
        SearchFilter filter = new SearchFilter();
        filter.setAmount(AmountOp.BETWEEN, AmountSide.IN, new BigDecimal("5"), new BigDecimal("50"));
        assertEquals(new BigDecimal("50"), filter.getAmountTo());
        filter.setAmount(null, AmountSide.OUT, BigDecimal.ONE, BigDecimal.TEN);
        assertFalse(filter.isAmountSet());
        assertNull(filter.getAmountOp());
        assertNull(filter.getAmountSide());
        assertNull(filter.getAmount());
        assertNull(filter.getAmountTo());
        assertNull(filter.toSelection(null).getL());
    }

    @Test
    public void anAmountOtherThanBetweenDropsItsOtherEnd() {
        SearchFilter filter = new SearchFilter();
        filter.setAmount(AmountOp.AT_LEAST, AmountSide.OUT, BigDecimal.ONE, BigDecimal.TEN);
        assertEquals(AmountOp.AT_LEAST, filter.getAmountOp());
        assertEquals(BigDecimal.ONE, filter.getAmount());
        assertNull(filter.getAmountTo());
    }

    @Test
    public void anAmountWithoutItsNumberIsUnset() {
        long a = insert(row(mEuro, 100));
        SearchFilter noAmount = new SearchFilter();
        noAmount.setAmount(AmountOp.AT_LEAST, AmountSide.OUT, null, null);
        SearchFilter noAmountTo = new SearchFilter();
        noAmountTo.setAmount(AmountOp.BETWEEN, AmountSide.OUT, BigDecimal.ONE, null);
        SearchFilter noAmountFrom = new SearchFilter();
        noAmountFrom.setAmount(AmountOp.BETWEEN, AmountSide.OUT, null, BigDecimal.ONE);
        for (SearchFilter filter : Arrays.asList(noAmount, noAmountTo, noAmountFrom)) {
            assertFalse(filter.isAmountSet());
            assertNull(filter.getAmountOp());
            assertNull(filter.getAmountSide());
            assertNull(filter.getAmount());
            assertNull(filter.getAmountTo());
            Pair<String, String[]> selection = filter.toSelection(mDecimals);
            assertNull(selection.getL());
            assertNull(selection.getR());
            assertEquals(ids(a), select(filter));
        }
    }

    private void assertSameSelection(SearchFilter expected, SearchFilter actual) {
        Pair<String, String[]> a = expected.toSelection(mDecimals);
        Pair<String, String[]> b = actual.toSelection(mDecimals);
        assertEquals(a.getL(), b.getL());
        assertArrayEquals(a.getR(), b.getR());
    }

    private SearchFilter amount(AmountOp op, String value) {
        return amount(op, value, AmountSide.EITHER);
    }

    private SearchFilter amount(AmountOp op, String value, AmountSide side) {
        SearchFilter filter = new SearchFilter();
        filter.setAmount(op, side, new BigDecimal(value), null);
        return filter;
    }

    private Set<Long> select(SearchFilter filter) {
        Pair<String, String[]> selection = filter.toSelection(filter.isAmountSet() ? mDecimals : null);
        Cursor cursor = mDatabase.getTransactions(new String[] {Contract.Transaction.ID},
                selection.getL(), selection.getR(), null);
        assertNotNull(cursor);
        try {
            Set<Long> ids = new TreeSet<>();
            while (cursor.moveToNext()) {
                ids.add(cursor.getLong(0));
            }
            return ids;
        } finally {
            cursor.close();
        }
    }

    private static Set<Long> ids(long... ids) {
        Set<Long> set = new TreeSet<>();
        for (long id : ids) {
            set.add(id);
        }
        return set;
    }

    private ContentValues row(long wallet, long money) {
        return row(wallet, money, true);
    }

    private ContentValues row(long wallet, long money, boolean confirmed) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transaction.MONEY, money);
        contentValues.put(Contract.Transaction.DATE, DATE);
        contentValues.put(Contract.Transaction.DESCRIPTION, "Spent");
        contentValues.put(Contract.Transaction.CATEGORY_ID, mFood);
        contentValues.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
        contentValues.put(Contract.Transaction.TYPE, Contract.TransactionType.STANDARD);
        contentValues.put(Contract.Transaction.WALLET_ID, wallet);
        contentValues.put(Contract.Transaction.CONFIRMED, confirmed);
        contentValues.put(Contract.Transaction.COUNT_IN_TOTAL, true);
        return contentValues;
    }

    private ContentValues dated(String date) {
        ContentValues contentValues = row(mEuro, 100);
        contentValues.put(Contract.Transaction.DATE, date);
        return contentValues;
    }

    private ContentValues described(String description) {
        ContentValues contentValues = row(mEuro, 100);
        contentValues.put(Contract.Transaction.DESCRIPTION, description);
        return contentValues;
    }

    private ContentValues withPeople(String people) {
        ContentValues contentValues = row(mEuro, 100);
        contentValues.put(Contract.Transaction.PEOPLE_IDS, people);
        return contentValues;
    }

    private long insert(ContentValues contentValues) {
        long id = mDatabase.insertTransaction(contentValues);
        assertTrue(id > 0);
        return id;
    }

    /** The ids of the rows the transfer wrote: from, to, then tax when there is one. */
    private long[] insertTransfer(long from, long to, long tax, String people) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Transfer.DESCRIPTION, "Moved");
        contentValues.put(Contract.Transfer.DATE, DATE);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from);
        contentValues.put(Contract.Transfer.TRANSACTION_FROM_MONEY, 1000L);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to);
        contentValues.put(Contract.Transfer.TRANSACTION_TO_MONEY, 1000L);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, from);
        contentValues.put(Contract.Transfer.TRANSACTION_TAX_MONEY, tax);
        contentValues.put(Contract.Transfer.CONFIRMED, true);
        contentValues.put(Contract.Transfer.COUNT_IN_TOTAL, true);
        contentValues.put(Contract.Transfer.PEOPLE_IDS, people);
        long transfer = mDatabase.insertTransfer(contentValues);
        assertTrue(transfer > 0);
        Cursor cursor = mDatabase.getReadableDatabase().rawQuery("SELECT " +
                Schema.Transfer.TRANSACTION_FROM + ", " + Schema.Transfer.TRANSACTION_TO + ", " +
                Schema.Transfer.TRANSACTION_TAX + " FROM " + Schema.Transfer.TABLE + " WHERE " +
                Schema.Transfer.ID + " = ?", new String[] {String.valueOf(transfer)});
        try {
            assertTrue(cursor.moveToFirst());
            return cursor.isNull(2)
                    ? new long[] {cursor.getLong(0), cursor.getLong(1)}
                    : new long[] {cursor.getLong(0), cursor.getLong(1), cursor.getLong(2)};
        } finally {
            cursor.close();
        }
    }

    private long insertWallet(String name, String currency) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, ICON);
        contentValues.put(Contract.Wallet.CURRENCY, currency);
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        long id = mDatabase.insertWallet(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertCategory(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, name);
        contentValues.put(Contract.Category.ICON, ICON);
        contentValues.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        contentValues.put(Contract.Category.SHOW_REPORT, true);
        long id = mDatabase.insertCategory(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertPerson(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Person.NAME, name);
        contentValues.put(Contract.Person.ICON, ICON);
        long id = mDatabase.insertPerson(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertEvent(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Event.NAME, name);
        contentValues.put(Contract.Event.ICON, ICON);
        contentValues.put(Contract.Event.START_DATE, "2024-01-01");
        contentValues.put(Contract.Event.END_DATE, "2024-12-31");
        long id = mDatabase.insertEvent(contentValues);
        assertTrue(id > 0);
        return id;
    }

    private long insertPlace(String name) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Place.NAME, name);
        contentValues.put(Contract.Place.ICON, ICON);
        long id = mDatabase.insertPlace(contentValues);
        assertTrue(id > 0);
        return id;
    }
}
