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

import android.os.Bundle;

import androidx.annotation.VisibleForTesting;

import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.MoneyScale;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Created by andrea on 31/03/18.
 */
public class EquationSolver {

    private static final String SS_FIRST_NUMBER = "EquationSolver::SavedState::FirstNumber";
    private static final String SS_SECOND_NUMBER = "EquationSolver::SavedState::SecondNumber";
    private static final String SS_OPERATION_NUMBER = "EquationSolver::SavedState::Operation";
    private static final String SS_CURRENCY = "EquationSolver::SavedState::Currency";
    private static final String SS_COMPUTED = "EquationSolver::SavedState::Computed";

    private final Controller mController;

    @VisibleForTesting
    /*package-local*/ String mFirstNumber;
    @VisibleForTesting
    /*package-local*/ String mSecondNumber;
    @VisibleForTesting
    /*package-local*/ Operation mOperation;
    @VisibleForTesting
    /*package-local*/ CurrencyUnit mCurrency;
    /**
     * True while mFirstNumber holds an answer an operation produced rather than a number the
     * keypad typed. getResult rounds an answer to the currency scale and leaves a typed number
     * to toMinorUnits, which truncates whatever will not fit.
     */
    @VisibleForTesting
    /*package-local*/ boolean mComputed;

    public EquationSolver(Bundle savedInstanceState, Controller controller) {
        mController = controller;
        if (savedInstanceState != null) {
            mFirstNumber = savedInstanceState.getString(SS_FIRST_NUMBER, "0");
            mSecondNumber = savedInstanceState.getString(SS_SECOND_NUMBER, null);
            mOperation = (Operation) savedInstanceState.getSerializable(SS_OPERATION_NUMBER);
            mCurrency = savedInstanceState.getParcelable(SS_CURRENCY);
            mComputed = savedInstanceState.getBoolean(SS_COMPUTED, false);
        } else {
            mFirstNumber = "0";
            mSecondNumber = null;
            mOperation = null;
            mCurrency = null;
            mComputed = false;
        }
        updateDisplaySafely();
    }

    public void setValue(CurrencyUnit currency, long money) {
        // Scale taken through CurrencyManager for the reason getResult gives.
        int decimals = CurrencyManager.getDecimals(currency);
        if (money != 0L && decimals > 0) {
            mFirstNumber = MoneyScale.toHumanAmount(money, decimals).toPlainString();
        } else {
            mFirstNumber = String.valueOf(money);
        }
        mSecondNumber = null;
        mOperation = null;
        mCurrency = currency;
        mComputed = false;
        updateDisplaySafely();
    }

    public void onSaveInstanceState(Bundle outState) {
        outState.putString(SS_FIRST_NUMBER, mFirstNumber);
        outState.putString(SS_SECOND_NUMBER, mSecondNumber);
        outState.putSerializable(SS_OPERATION_NUMBER, mOperation);
        // The constructor reads this key back, and the activity only calls setValue when there is
        // no saved state, so leaving the currency out made getResult return the typed number
        // rather than its minor units after a rotation: 12.50 came back as 12, not 1250.
        outState.putParcelable(SS_CURRENCY, mCurrency);
        // Same trap as the currency above: a key the bundle does not carry comes back as the
        // default, so an answer still on the display after a rotation would be truncated rather
        // than rounded when it reaches the ledger.
        outState.putBoolean(SS_COMPUTED, mComputed);
    }

    public void clear() {
        mFirstNumber = "0";
        mSecondNumber = null;
        mOperation = null;
        mComputed = false;
        updateDisplaySafely();
    }

    public void appendOperation(Operation operation) {
        if (mOperation == null || execute(false)) {
            mOperation = operation;
            updateDisplaySafely();
        }
    }

    /**
     * The three editing keys act at a cursor, an index into the string updateDisplaySafely
     * renders, and hand back where the cursor sits once the edit is in. An index up to the length
     * of the first number addresses the first number; anything past that addresses the second one,
     * with the three character operator span pointing at its start. An index outside the display
     * means the end of the display.
     */
    private Target locate(int cursor) {
        int first = firstLength();
        if (mOperation == null) {
            return new Target(true, cursor < 0 || cursor > first ? first : cursor);
        }
        int second = mSecondNumber == null ? 0 : mSecondNumber.length();
        int end = first + 3 + second;
        int at = cursor < 0 || cursor > end ? end : cursor;
        if (at <= first) {
            return new Target(true, at);
        }
        return new Target(false, Math.max(at - first - 3, 0));
    }

    private int firstLength() {
        return mFirstNumber == null ? 0 : mFirstNumber.length();
    }

    private int cursorOf(Target target, int offset) {
        return target.mFirst ? offset : firstLength() + 3 + offset;
    }

    private void store(Target target, String number) {
        if (target.mFirst) {
            clearComputedIfValueChanged(number);
            mFirstNumber = number;
        } else {
            mSecondNumber = number;
        }
    }

    public int insertPoint(int cursor) {
        Target target = locate(cursor);
        String number = target.mFirst ? mFirstNumber : mSecondNumber;
        int offset = target.mOffset;
        if (number == null || number.isEmpty() || number.equals("0")) {
            number = "0.";
            offset = 2;
        } else if (!number.contains(".")) {
            int sign = number.startsWith("-") ? 1 : 0;
            if (offset <= sign) {
                // A point typed in front of the digits opens a fraction instead of splitting one.
                number = number.substring(0, sign) + "0." + number.substring(sign);
                offset = sign + 2;
            } else {
                number = number.substring(0, offset) + "." + number.substring(offset);
                offset++;
            }
        }
        store(target, number);
        updateDisplaySafely();
        return cursorOf(target, offset);
    }

    public int insertNumber(String digit, int cursor) {
        Target target = locate(cursor);
        String number = target.mFirst ? mFirstNumber : mSecondNumber;
        int offset = target.mOffset;
        if (number == null || number.isEmpty() || number.equals("0")) {
            number = digit.equals("000") ? "0" : digit;
            offset = number.length();
        } else {
            if (number.startsWith("-") && offset == 0) {
                // A digit typed in front of a sign belongs after it.
                offset = 1;
            }
            number = number.substring(0, offset) + digit + number.substring(offset);
            offset += digit.length();
        }
        store(target, number);
        updateDisplaySafely();
        return cursorOf(target, offset);
    }

    public int backspace(int cursor) {
        Target target = locate(cursor);
        String number = target.mFirst ? mFirstNumber : mSecondNumber;
        int offset = target.mOffset;
        boolean atOperator = false;
        if (offset == 0) {
            if (!target.mFirst && (number == null || number.isEmpty())) {
                // Nothing left of the second number to delete, so the operator goes instead.
                mOperation = null;
                mSecondNumber = null;
                atOperator = true;
            } else if (!target.mFirst) {
                // Nothing to delete here, so the cursor steps back over the operator instead.
                atOperator = true;
            }
        } else {
            number = number.substring(0, offset - 1) + number.substring(offset);
            offset--;
            if (target.mFirst && number.isEmpty()) {
                number = "0";
                offset = 1;
            }
            store(target, number);
        }
        updateDisplaySafely();
        return atOperator ? firstLength() : cursorOf(target, offset);
    }

    public boolean isPendingOperation() {
        return mOperation != null;
    }

    public boolean execute(boolean fireCallback) {
        BigDecimal first = parseNumber(mFirstNumber);
        BigDecimal second = parseNumber(mSecondNumber);
        try {
            BigDecimal result;
            switch (mOperation) {
                case ADDITION:
                    result = first.add(second);
                    break;
                case SUBTRACTION:
                    result = first.subtract(second);
                    break;
                case MULTIPLICATION:
                    result = first.multiply(second);
                    break;
                case DIVISION:
                    // divide(divisor, roundingMode) returns a value whose scale is the dividend's,
                    // so a 10 typed with no decimals rounded 10 / 4 to 2. Divide at 16 significant
                    // digits instead. Narrowing to the currency scale here would narrow every step
                    // of a chain rather than the answer: with a zero decimal currency, 10.0 / 4 * 4
                    // would give 8. getResult narrows the finished equation once.
                    result = first.divide(second, MathContext.DECIMAL64);
                    break;
                default:
                    result = BigDecimal.valueOf(0);
                    break;
            }
            // String.valueOf renders a small enough result in scientific notation, the product
            // 0.001 * 0.0001 as 1E-7, and the next keypress appends to whatever string lands here.
            mFirstNumber = result.toPlainString();
            if (mFirstNumber.endsWith(".0")) {
                mFirstNumber = mFirstNumber.substring(0, mFirstNumber.length() - 2);
            }
            mSecondNumber = null;
            mOperation = null;
            // An operation that hands the amount back unchanged has computed nothing the ledger
            // can see, and the display still reads the number that was there before it. The equals
            // key runs the equation whether or not a second number was entered, and appendOperation
            // runs it again when a second operator is pressed, so both reach here against the zero
            // parseNumber returns for a second number nobody typed. Adding that zero and
            // multiplying by one land in the same place: leave the flag as it was found, so a typed
            // amount stays typed and an answer stays an answer.
            if (result.compareTo(first) != 0) {
                mComputed = true;
            }
            if (fireCallback) {
                updateDisplaySafely();
            }
            return true;
        } catch (ArithmeticException ignore) {
            ignore.printStackTrace();
        }
        return false;
    }

    public long getResult() {
        if (isPendingOperation() && !execute(false)) {
            // Error occurred during calculation
            return 0L;
        }
        BigDecimal parsedNumber = parseNumber(mFirstNumber);
        // A currency this installation cannot resolve is worth two decimals here, which is what
        // CurrencyManager.getDecimals answers for a null one and what MoneyFormatter divides the
        // same row by when it cannot resolve it either. This used to return the typed number as
        // if it were already minor units, so a wallet naming a missing currency stored a typed
        // 12.50 as 12, which MoneyFormatter then renders as 0.12.
        int decimals = CurrencyManager.getDecimals(mCurrency);
        if (mComputed) {
            // An answer is rounded to the currency scale, so 20.00 / 3 stores 667. A typed
            // number falls through to toMinorUnits, which truncates what will not fit: 64.9 on a
            // zero decimal currency stays 64, as the entry tests pin.
            parsedNumber = parsedNumber.setScale(decimals, RoundingMode.HALF_EVEN);
        }
        return MoneyScale.toMinorUnits(parsedNumber, decimals);
    }

    /**
     * Clears the computed flag when the candidate first number differs in value from the one on
     * the display. Compared as numbers, not as strings: appending a zero to an answer, or
     * backspacing one off it, leaves the display reading the same amount, and a keypress that
     * changes nothing does not retype what is there.
     */
    private void clearComputedIfValueChanged(String number) {
        if (parseNumber(number).compareTo(parseNumber(mFirstNumber)) != 0) {
            mComputed = false;
        }
    }

    private BigDecimal parseNumber(String number) {
        String safe = number == null || number.isEmpty() ? "0" : number;
        if (safe.endsWith(".")) {
            safe = safe.substring(0, safe.length() - 1);
        }
        try {
            return new BigDecimal(safe);
        } catch (NumberFormatException ignore) {}
        return BigDecimal.valueOf(0);
    }

    private static class Target {

        private final boolean mFirst;
        private final int mOffset;

        private Target(boolean first, int offset) {
            mFirst = first;
            mOffset = offset;
        }
    }

    public enum Operation {
        ADDITION,
        SUBTRACTION,
        MULTIPLICATION,
        DIVISION
    }

    private void updateDisplaySafely() {
        if (mController != null) {
            StringBuilder builder = new StringBuilder();
            builder.append(mFirstNumber);
            if (mOperation != null) {
                switch (mOperation) {
                    case ADDITION:
                        builder.append(" + ");
                        break;
                    case SUBTRACTION:
                        builder.append(" − ");
                        break;
                    case MULTIPLICATION:
                        builder.append(" × ");
                        break;
                    case DIVISION:
                        builder.append(" ÷ ");
                        break;
                }
                if (mSecondNumber != null) {
                    builder.append(mSecondNumber);
                }
            }
            mController.onUpdateDisplay(builder.toString());
        }
    }

    public interface Controller {

        void onUpdateDisplay(String text);
    }
}