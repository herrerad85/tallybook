package com.oriondev.moneywallet.utils;

import android.os.Bundle;
import android.os.Parcelable;

import com.oriondev.moneywallet.model.CurrencyUnit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

@RunWith(MockitoJUnitRunner.class)
public class EquationSolverTest {

    @InjectMocks
    private EquationSolver equationSolver;

    /**
     * A cursor outside the display, which every editing key reads as the end of the display: the
     * position the keypad edited at before it grew a cursor.
     */
    private static final int END = -1;

    private void mockCurrencyUnit(int decimals) {
        equationSolver.mCurrency = new CurrencyUnit("", "", "", decimals);
    }

    /*
    Order of tests:
    For each supported currency based on decimal count:
    - Test zero value input
    - Test positive value input with expected number of decimals
    - Test negative value input with expected number of decimals
    - Test positive value input with the last decimal missing (except for zero decimal currency)
    - Test negative value input with the last decimal missing (except for zero decimal currency)
    - Test positive value input with an extra decimal
    - Test negative value input with an extra decimal
     */

    // Zero Decimal Currency

    @Test
    public void testGetResult_zeroDecimalCurrencyAndZeroValue() {
        mockCurrencyUnit(0);
        equationSolver.mFirstNumber = "0";

        long result = equationSolver.getResult();

        assertEquals(result, 0L);
    }

    @Test
    public void testGetResult_zeroDecimalCurrencyAndPositiveValue() {
        mockCurrencyUnit(0);
        equationSolver.mFirstNumber = "64";

        long result = equationSolver.getResult();

        assertEquals(result, 64L);
    }

    @Test
    public void testGetResult_zeroDecimalCurrencyAndNegativeValue() {
        mockCurrencyUnit(0);
        equationSolver.mFirstNumber = "-64";

        long result = equationSolver.getResult();

        assertEquals(result, -64L);
    }

    @Test
    public void testGetResult_zeroDecimalCurrencyAndPositiveValueWithExtraDecimal() {
        mockCurrencyUnit(0);
        equationSolver.mFirstNumber = "64.9";

        long result = equationSolver.getResult();

        assertEquals(result, 64L);
    }

    @Test
    public void testGetResult_zeroDecimalCurrencyAndNegativeValueWithExtraDecimal() {
        mockCurrencyUnit(0);
        equationSolver.mFirstNumber = "-64.9";

        long result = equationSolver.getResult();

        assertEquals(result, -64L);
    }

    // One Decimal Currency

    @Test
    public void testGetResult_oneDecimalCurrencyAndZeroValue() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "0";

        long result = equationSolver.getResult();

        assertEquals(result, 0L);
    }

    @Test
    public void testGetResult_oneDecimalCurrencyAndPositiveValue() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "64.9";

        long result = equationSolver.getResult();

        assertEquals(result, 649L);
    }

    @Test
    public void testGetResult_oneDecimalCurrencyAndNegativeValue() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "-64.9";

        long result = equationSolver.getResult();

        assertEquals(result, -649L);
    }

    @Test
    public void testGetResult_oneDecimalCurrencyAndPositiveValueWithMissingLastDecimal() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "64";

        long result = equationSolver.getResult();

        assertEquals(result, 640L);
    }

    @Test
    public void testGetResult_oneDecimalCurrencyAndNegativeValueWithMissingLastDecimal() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "-64";

        long result = equationSolver.getResult();

        assertEquals(result, -640L);
    }

    @Test
    public void testGetResult_oneDecimalCurrencyAndPositiveValueWithExtraDecimal() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "64.99";

        long result = equationSolver.getResult();

        assertEquals(result, 649L);
    }

    @Test
    public void testGetResult_oneDecimalCurrencyAndNegativeValueWithExtraDecimal() {
        mockCurrencyUnit(1);
        equationSolver.mFirstNumber = "-64.99";

        long result = equationSolver.getResult();

        assertEquals(result, -649L);
    }

    // Two Decimal Currency

    @Test
    public void testGetResult_towDecimalCurrencyAndZeroValue() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "0";

        long result = equationSolver.getResult();

        assertEquals(result, 0L);
    }

    @Test
    public void testGetResult_towDecimalCurrencyAndPositiveValue() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "64.99";

        long result = equationSolver.getResult();

        assertEquals(result, 6499L);
    }

    @Test
    public void testGetResult_towDecimalCurrencyAndNegativeValue() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "-64.99";

        long result = equationSolver.getResult();

        assertEquals(result, -6499L);
    }

    @Test
    public void testGetResult_towDecimalCurrencyAndPositiveValueWithMissingLastDecimal() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "64.9";

        long result = equationSolver.getResult();

        assertEquals(result, 6490L);
    }

    @Test
    public void testGetResult_towDecimalCurrencyAndNegativeValueWithMissingLastDecimal() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "-64.9";

        long result = equationSolver.getResult();

        assertEquals(result, -6490L);
    }

    @Test
    public void testGetResult_towDecimalCurrencyAndPositiveValueWithExtraDecimal() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "64.999";

        long result = equationSolver.getResult();

        assertEquals(result, 6499L);
    }

    @Test
    public void testGetResult_towDecimalCurrencyAndNegativeValueWithExtraDecimal() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "-64.999";

        long result = equationSolver.getResult();

        assertEquals(result, -6499L);
    }


    // Three Decimal Currency

    @Test
    public void testGetResult_threeDecimalCurrencyAndZeroValue() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "0";

        long result = equationSolver.getResult();

        assertEquals(result, 0L);
    }

    @Test
    public void testGetResult_threeDecimalCurrencyAndPositiveValue() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "64.999";

        long result = equationSolver.getResult();

        assertEquals(result, 64999L);
    }

    @Test
    public void testGetResult_threeDecimalCurrencyAndNegativeValue() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "-64.999";

        long result = equationSolver.getResult();

        assertEquals(result, -64999L);
    }

    @Test
    public void testGetResult_threeDecimalCurrencyAndPositiveValueWithMissingLastDecimal() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "64.99";

        long result = equationSolver.getResult();

        assertEquals(result, 64990L);
    }

    @Test
    public void testGetResult_threeDecimalCurrencyAndNegativeValueWithMissingLastDecimal() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "-64.99";

        long result = equationSolver.getResult();

        assertEquals(result, -64990L);
    }

    @Test
    public void testGetResult_threeDecimalCurrencyAndPositiveValueWithExtraDecimal() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "64.9999";

        long result = equationSolver.getResult();

        assertEquals(result, 64999L);
    }

    @Test
    public void testGetResult_threeDecimalCurrencyAndNegativeValueWithExtraDecimal() {
        mockCurrencyUnit(3);
        equationSolver.mFirstNumber = "-64.9999";

        long result = equationSolver.getResult();

        assertEquals(result, -64999L);
    }

    // Arithmetic

    private void type(String keys) {
        for (char key : keys.toCharArray()) {
            if (key == '.') {
                equationSolver.insertPoint(END);
            } else {
                equationSolver.insertNumber(String.valueOf(key), END);
            }
        }
    }

    private void enter(String first, EquationSolver.Operation operation, String second) {
        type(first);
        equationSolver.appendOperation(operation);
        type(second);
    }

    @Test
    public void testExecute_addition() {
        enter("2.75", EquationSolver.Operation.ADDITION, "0.25");

        assertTrue(equationSolver.execute(false));
        assertEquals("3.00", equationSolver.mFirstNumber);
    }

    @Test
    public void testExecute_subtraction() {
        enter("2.75", EquationSolver.Operation.SUBTRACTION, "0.25");

        assertTrue(equationSolver.execute(false));
        assertEquals("2.50", equationSolver.mFirstNumber);
    }

    @Test
    public void testExecute_multiplication() {
        enter("2.5", EquationSolver.Operation.MULTIPLICATION, "4");

        assertTrue(equationSolver.execute(false));
        assertEquals("10", equationSolver.mFirstNumber);
    }

    @Test
    public void testExecute_divisionOfIntegerDividendKeepsTheFraction() {
        mockCurrencyUnit(2);
        enter("10", EquationSolver.Operation.DIVISION, "4");

        assertTrue(equationSolver.execute(false));
        assertEquals("2.5", equationSolver.mFirstNumber);
        assertEquals(250L, equationSolver.getResult());
    }

    @Test
    public void testExecute_divisionIsIndependentOfTheDividendScale() {
        // The displayed string still carries the dividend's trailing zeros, the stored value is
        // what has to stop depending on how the dividend was typed.
        mockCurrencyUnit(2);

        enter("160", EquationSolver.Operation.DIVISION, "25");
        assertEquals(640L, equationSolver.getResult());

        equationSolver.clear();
        enter("160.00", EquationSolver.Operation.DIVISION, "25");
        assertEquals(640L, equationSolver.getResult());
    }

    @Test
    public void testExecute_chainedDivisionAndMultiplicationDoNotRoundBetweenSteps() {
        // Narrowing the quotient to the currency scale inside execute would round every step of a
        // chain: this one would come back as 8 with a zero decimal currency.
        mockCurrencyUnit(0);
        type("10.0");
        equationSolver.appendOperation(EquationSolver.Operation.DIVISION);
        type("4");
        equationSolver.appendOperation(EquationSolver.Operation.MULTIPLICATION);
        type("4");

        assertEquals(10L, equationSolver.getResult());
    }

    @Test
    public void testExecute_chainedDivisionKeepsTheDigitsBelowTheCurrencyScale() {
        mockCurrencyUnit(2);
        type("1.234");
        equationSolver.appendOperation(EquationSolver.Operation.DIVISION);
        type("2");
        equationSolver.appendOperation(EquationSolver.Operation.MULTIPLICATION);
        type("2");

        assertEquals(123L, equationSolver.getResult());
        assertEquals("1.234", equationSolver.mFirstNumber);
    }

    @Test
    public void testExecute_recurringDivisionKeepsSixteenSignificantDigits() {
        // The second case is the one that fails if the division stops rounding half up or half
        // even and starts truncating.
        enter("1", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));
        assertEquals("0.3333333333333333", equationSolver.mFirstNumber);

        equationSolver.clear();
        enter("2", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));
        assertEquals("0.6666666666666667", equationSolver.mFirstNumber);
    }

    @Test
    public void testGetResult_recurringDivisionRoundsAtTheCurrencyScale() {
        // A quotient that does not terminate keeps its digits on the display and is rounded, not
        // truncated, on the way to the ledger. Master stored 667 for the same division typed as
        // 20.00, where the dividend's scale it rounded at was the currency scale.
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");

        assertTrue(equationSolver.execute(false));
        assertEquals("6.666666666666667", equationSolver.mFirstNumber);
        assertEquals(667L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_anAnswerRoundsHalfEvenAndATypedNumberDoesNot() {
        // Half up would store 3 and 1 for the two answers. The typed number is the control: it
        // truncates, so rounding it at all would store 65.
        mockCurrencyUnit(0);
        enter("5", EquationSolver.Operation.DIVISION, "2");
        assertEquals(2L, equationSolver.getResult());

        equationSolver.clear();
        mockCurrencyUnit(0);
        enter("1", EquationSolver.Operation.DIVISION, "2");
        assertEquals(0L, equationSolver.getResult());

        equationSolver.clear();
        mockCurrencyUnit(0);
        type("64.9");
        assertEquals(64L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_anOperatorWithNoSecondNumberLeavesATypedAmountTyped() {
        // Confirming right after an operator key runs the equation against the zero a missing
        // second number parses to. That press computed nothing, so 0.999 has to truncate to 99
        // the way it does with no operator at all, rather than round up to a whole unit.
        mockCurrencyUnit(2);
        type("0.999");
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);

        assertEquals(99L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_aSecondOperatorPressLeavesATypedAmountTyped() {
        // appendOperation runs the pending equation before it takes the new operator, which is the
        // same empty run as the case above reached by pressing an operator key twice.
        mockCurrencyUnit(2);
        type("0.999");
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);

        assertEquals(99L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_anOperatorWithNoSecondNumberLeavesAnAnswerAnAnswer() {
        // The mirror of the two above: an empty run must not retype a quotient either, or the
        // rounding is lost to a stray keypress.
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);

        assertEquals(667L, equationSolver.getResult());
    }

    @Test
    public void testAppendNumber_aKeypressThatDoesNotChangeTheAmountLeavesAnAnswerAnAnswer() {
        // A zero appended to a quotient reads as the same amount, so it is not a retype. Compared
        // as strings rather than as numbers this stored 666.
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));

        equationSolver.insertNumber("0", END);

        assertEquals("6.6666666666666670", equationSolver.mFirstNumber);
        assertEquals(667L, equationSolver.getResult());
    }

    @Test
    public void testAppendNumber_anAnswerTypedOverAndTakenBackIsTypedFromThenOn() {
        // Editing an answer hands it to the keypad, and taking the edit back does not hand it
        // returned: the amount stores as the same digits typed from scratch would, 666 rather
        // than 667. The alternative, recording the answer and rounding again whenever the display
        // matches it, rounds a number that was typed by hand later in the same session.
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));

        equationSolver.insertNumber("5", END);
        equationSolver.backspace(END);

        assertEquals("6.666666666666667", equationSolver.mFirstNumber);
        assertEquals(666L, equationSolver.getResult());
    }

    @Test
    public void testAppendNumber_anAmountTypedAfterAnAnswerIsClearedAwayIsTyped() {
        // Backspacing an answer down to zero and typing an amount that happens to equal it must
        // store what those digits store on a fresh keypad. On a currency with no decimals any
        // half unit shows the difference: 7 typed, 8 rounded.
        mockCurrencyUnit(0);
        enter("15", EquationSolver.Operation.DIVISION, "2");
        assertTrue(equationSolver.execute(false));
        assertEquals("7.5", equationSolver.mFirstNumber);

        for (int press = 0; press < 5; press++) {
            equationSolver.backspace(END);
        }
        type("7.5");

        assertEquals(7L, equationSolver.getResult());
    }

    @Test
    public void testAppendNumber_anAmountRebuiltOnTopOfAnAnswerIsTyped() {
        mockCurrencyUnit(0);
        enter("15", EquationSolver.Operation.DIVISION, "2");
        assertTrue(equationSolver.execute(false));

        equationSolver.backspace(END);
        equationSolver.backspace(END);
        equationSolver.insertPoint(END);
        equationSolver.insertNumber("5", END);

        assertEquals("7.5", equationSolver.mFirstNumber);
        assertEquals(7L, equationSolver.getResult());
    }

    @Test
    public void testAppendNumber_aKeypressThatChangesTheAmountRetypesIt() {
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));

        equationSolver.insertNumber("5", END);

        assertEquals(666L, equationSolver.getResult());
    }

    @Test
    public void testCancel_backspaceThatChangesTheAmountRetypesIt() {
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));

        equationSolver.backspace(END);

        assertEquals(666L, equationSolver.getResult());
    }

    @Test
    public void testCancel_backspaceThatDoesNotChangeTheAmountLeavesAnAnswerAnAnswer() {
        // The mirror of the appended zero: taking a trailing zero back off an answer leaves the
        // display reading the same amount, so it is not a retype either.
        mockCurrencyUnit(2);
        enter("0.3335", EquationSolver.Operation.MULTIPLICATION, "2.0");
        assertTrue(equationSolver.execute(false));
        assertEquals("0.66700", equationSolver.mFirstNumber);

        equationSolver.backspace(END);

        assertEquals("0.6670", equationSolver.mFirstNumber);
        assertEquals(67L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_anOperationThatChangesNothingLeavesATypedAmountTyped() {
        // Adding a zero, multiplying by one and pressing an operator on a number nobody followed
        // up all hand back the amount that was typed, so none of them is an answer to round.
        mockCurrencyUnit(2);
        type("0.999");
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);
        type("0");
        assertEquals(99L, equationSolver.getResult());

        equationSolver.clear();
        mockCurrencyUnit(2);
        type("0.999");
        equationSolver.appendOperation(EquationSolver.Operation.MULTIPLICATION);
        type("1");
        assertEquals(99L, equationSolver.getResult());

        equationSolver.clear();
        mockCurrencyUnit(2);
        type("0.999");
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);
        equationSolver.insertPoint(END);
        assertEquals(99L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_aSecondNumberBackspacedAwayLeavesATypedAmountTyped() {
        mockCurrencyUnit(2);
        type("0.999");
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);
        type("5");
        equationSolver.backspace(END);

        assertEquals(99L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_aSecondNumberBackspacedAwayLeavesAnAnswerAnAnswer() {
        // The helper only looks at the first number, and this is the sequence that proves it has
        // to: the 1 typed here goes to the second number, and taking it back off must not retype
        // the quotient underneath.
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));
        equationSolver.appendOperation(EquationSolver.Operation.ADDITION);
        type("1");
        equationSolver.backspace(END);

        assertEquals(667L, equationSolver.getResult());
    }

    @Test
    public void testGetResult_everyOperationRoundsItsAnswer() {
        // Not only division. Master truncated all four, and a multiplication that lands below the
        // currency scale is where that shows: 2.5 * 3 stored 7 there and stores 8 here.
        mockCurrencyUnit(0);
        enter("2.5", EquationSolver.Operation.MULTIPLICATION, "3");
        assertEquals(8L, equationSolver.getResult());

        equationSolver.clear();
        mockCurrencyUnit(2);
        enter("10", EquationSolver.Operation.SUBTRACTION, "0.001");
        assertEquals(1000L, equationSolver.getResult());

        equationSolver.clear();
        mockCurrencyUnit(2);
        enter("0.005", EquationSolver.Operation.ADDITION, "0.001");
        assertEquals(1L, equationSolver.getResult());
    }

    @Test
    public void testExecute_multiplicationStaysInPlainNotation() {
        enter("0.001", EquationSolver.Operation.MULTIPLICATION, "0.0001");

        assertTrue(equationSolver.execute(false));
        assertEquals("0.0000001", equationSolver.mFirstNumber);
    }

    @Test
    public void testExecute_divisionStaysInPlainNotation() {
        enter("100", EquationSolver.Operation.DIVISION, "0.5");

        assertTrue(equationSolver.execute(false));
        assertEquals("200", equationSolver.mFirstNumber);
    }

    @Test
    public void testExecute_divisionByZeroFailsAndLeavesTheEquationAlone() {
        mockCurrencyUnit(2);
        enter("10", EquationSolver.Operation.DIVISION, "0");

        assertFalse(equationSolver.execute(false));
        assertEquals("10", equationSolver.mFirstNumber);
        assertTrue(equationSolver.isPendingOperation());
    }

    // Saved state

    @Test
    public void testOnSaveInstanceState_keepsTheCurrencyThroughARotation() {
        // CalculatorActivity only calls setValue when there is no saved state, so a currency the
        // bundle does not carry is gone for good: getResult then returns the bare typed number
        // instead of its minor units, and 12.50 reaches the ledger as 12.
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "12.50";
        Bundle bundle = fakeBundle();

        equationSolver.onSaveInstanceState(bundle);
        EquationSolver restored = new EquationSolver(bundle, null);

        assertEquals("12.50", restored.mFirstNumber);
        assertEquals(1250L, restored.getResult());
    }

    @Test
    public void testOnSaveInstanceState_keepsAnAnswerAnAnswerThroughARotation() {
        mockCurrencyUnit(2);
        enter("20", EquationSolver.Operation.DIVISION, "3");
        assertTrue(equationSolver.execute(false));
        Bundle bundle = fakeBundle();

        equationSolver.onSaveInstanceState(bundle);
        EquationSolver restored = new EquationSolver(bundle, null);

        assertEquals(667L, restored.getResult());
    }

    @Test
    public void testConstructor_aBundleWithoutTheComputedKeyLeavesTheAmountTyped() {
        // The default a missing key falls back to is what a bundle written by any earlier build
        // hands back, and it has to be the safe one: truncate rather than round an amount whose
        // history is unknown.
        Bundle bundle = fakeBundle();
        bundle.putString("EquationSolver::SavedState::FirstNumber", "6.666666666666667");
        bundle.putParcelable("EquationSolver::SavedState::Currency", new CurrencyUnit("", "", "", 2));

        EquationSolver restored = new EquationSolver(bundle, null);

        assertFalse(restored.mComputed);
        assertEquals(666L, restored.getResult());
    }

    /**
     * A Bundle backed by a map. The unit test classpath has the stub android.jar, whose Bundle
     * throws on every call, so the state has to live in the mock.
     */
    private Bundle fakeBundle() {
        final Map<String, Object> values = new HashMap<>();
        Answer<Object> put = invocation -> {
            values.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        };
        Answer<Object> get = invocation -> values.get(invocation.getArgument(0));
        // The two argument getters hand back the caller's default for a key the bundle never
        // carried, which is the case a bundle written by an earlier build produces.
        Answer<Object> getOrDefault = invocation -> {
            Object stored = values.get(invocation.getArgument(0));
            return stored != null ? stored : invocation.getArgument(1);
        };
        Bundle bundle = mock(Bundle.class);
        doAnswer(put).when(bundle).putString(anyString(), nullable(String.class));
        doAnswer(put).when(bundle).putSerializable(anyString(), nullable(Serializable.class));
        doAnswer(put).when(bundle).putParcelable(anyString(), nullable(Parcelable.class));
        doAnswer(put).when(bundle).putBoolean(anyString(), anyBoolean());
        doAnswer(getOrDefault).when(bundle).getString(anyString(), nullable(String.class));
        doAnswer(getOrDefault).when(bundle).getBoolean(anyString(), anyBoolean());
        doAnswer(get).when(bundle).getSerializable(anyString());
        doAnswer(get).when(bundle).getParcelable(anyString());
        return bundle;
    }

    @Test
    public void testGetResult_twoDecimalCurrencyAndDividedValue() {
        mockCurrencyUnit(2);
        enter("10", EquationSolver.Operation.DIVISION, "4");

        long result = equationSolver.getResult();

        assertEquals(250L, result);
    }

    // Unresolvable currency. mCurrency is null when the wallet on the screen names a currency
    // this installation does not have, which CurrencyManager.getCurrency answers null for. The
    // keypad reads and writes those rows at two decimals, the same scale MoneyFormatter renders
    // them at, instead of treating the typed number as minor units already.

    @Test
    public void testGetResult_unresolvableCurrencyAndTypedValue() {
        equationSolver.mFirstNumber = "12.50";

        long result = equationSolver.getResult();

        assertEquals(1250L, result);
    }

    @Test
    public void testGetResult_unresolvableCurrencyAndNegativeValue() {
        equationSolver.mFirstNumber = "-12.50";

        long result = equationSolver.getResult();

        assertEquals(-1250L, result);
    }

    @Test
    public void testGetResult_unresolvableCurrencyAndWholeValue() {
        equationSolver.mFirstNumber = "12";

        long result = equationSolver.getResult();

        assertEquals(1200L, result);
    }

    @Test
    public void testGetResult_unresolvableCurrencyTruncatesATypedThirdDecimal() {
        equationSolver.mFirstNumber = "12.509";

        long result = equationSolver.getResult();

        assertEquals(1250L, result);
    }

    @Test
    public void testGetResult_unresolvableCurrencyRoundsAComputedAnswer() {
        enter("20.00", EquationSolver.Operation.DIVISION, "3");

        long result = equationSolver.getResult();

        assertEquals(667L, result);
    }

    @Test
    public void testSetValue_unresolvableCurrencyReadsAStoredAmountBackWhole() {
        equationSolver.setValue(null, 1250L);

        assertEquals("12.50", equationSolver.mFirstNumber);
        assertEquals(1250L, equationSolver.getResult());
    }

    // setValue with a currency that does resolve. Without these, the scale it reads a stored
    // amount back at is pinned only where the currency is null, so replacing the lookup with a
    // literal 2 passes every other test in this class.

    @Test
    public void testSetValue_zeroDecimalCurrencyReadsAStoredAmountBackWhole() {
        equationSolver.setValue(new CurrencyUnit("", "", "", 0), 1250L);

        assertEquals("1250", equationSolver.mFirstNumber);
        assertEquals(1250L, equationSolver.getResult());
    }

    @Test
    public void testSetValue_twoDecimalCurrencyReadsAStoredAmountBackWhole() {
        equationSolver.setValue(new CurrencyUnit("", "", "", 2), 1250L);

        assertEquals("12.50", equationSolver.mFirstNumber);
        assertEquals(1250L, equationSolver.getResult());
    }

    @Test
    public void testSetValue_threeDecimalCurrencyReadsAStoredAmountBackWhole() {
        equationSolver.setValue(new CurrencyUnit("", "", "", 3), 1250L);

        assertEquals("1.250", equationSolver.mFirstNumber);
        assertEquals(1250L, equationSolver.getResult());
    }

    @Test
    public void testSetValue_oneDecimalCurrencyReadsAStoredAmountBackWhole() {
        equationSolver.setValue(new CurrencyUnit("", "", "", 1), 1250L);

        assertEquals("125.0", equationSolver.mFirstNumber);
        assertEquals(1250L, equationSolver.getResult());
    }

    @Test
    public void testSetValue_aNegativeStoredAmountReadsBackWhole() {
        // A wallet may open on a negative balance, and NewEditWalletActivity is the one screen
        // that hands the keypad a real currency with negatives allowed. Were this to fall to the
        // bare number, confirming without pressing a key would multiply the balance by a hundred.
        equationSolver.setValue(new CurrencyUnit("", "", "", 2), -1250L);

        assertEquals("-12.50", equationSolver.mFirstNumber);
        assertEquals(-1250L, equationSolver.getResult());
    }

    @Test
    public void testSetValue_anEmptyAmountStartsOnABareZero() {
        // CalculatorActivity passes 0 every time the keypad opens on an empty field, and
        // appendNumber only replaces the display when it reads exactly "0". Were this to arrive
        // as "0.00", the first digit typed would append to it, so a 5 would read "0.005" and
        // reach the ledger as nothing.
        equationSolver.setValue(new CurrencyUnit("", "", "", 2), 0L);

        assertEquals("0", equationSolver.mFirstNumber);

        equationSolver.insertNumber("5", END);

        assertEquals("5", equationSolver.mFirstNumber);
        assertEquals(500L, equationSolver.getResult());
    }

    // Editing at a cursor

    @Test
    public void testInsertNumber_insertsAtTheCursorInsideTheFirstNumber() {
        equationSolver.mFirstNumber = "12";

        assertEquals(2, equationSolver.insertNumber("5", 1));
        assertEquals("152", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertNumber_replacesABareZero() {
        equationSolver.mFirstNumber = "0";

        assertEquals(1, equationSolver.insertNumber("5", 0));
        assertEquals("5", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertNumber_snapsPastTheSign() {
        equationSolver.mFirstNumber = "-12";

        assertEquals(2, equationSolver.insertNumber("5", 0));
        assertEquals("-512", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertNumber_aCursorOutsideTheDisplayMeansItsEnd() {
        equationSolver.mFirstNumber = "12";

        assertEquals(3, equationSolver.insertNumber("5", END));
        assertEquals("125", equationSolver.mFirstNumber);

        equationSolver.mFirstNumber = "12";

        assertEquals(3, equationSolver.insertNumber("5", 99));
        assertEquals("125", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertNumber_editsTheFirstNumberWhileAnOperationIsPending() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = "34";

        assertEquals(2, equationSolver.insertNumber("5", 1));
        assertEquals("152", equationSolver.mFirstNumber);
        assertEquals("34", equationSolver.mSecondNumber);
    }

    @Test
    public void testInsertNumber_insertsAtTheCursorInsideTheSecondNumber() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = "34";

        assertEquals(8, equationSolver.insertNumber("5", 7));
        assertEquals("345", equationSolver.mSecondNumber);
    }

    @Test
    public void testInsertNumber_aCursorInTheOperatorSpanMeansTheStartOfTheSecondNumber() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = "34";

        assertEquals(6, equationSolver.insertNumber("5", 3));
        assertEquals("534", equationSolver.mSecondNumber);
    }

    @Test
    public void testInsertNumber_startsASecondNumberNobodyTyped() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = null;

        assertEquals(6, equationSolver.insertNumber("5", 4));
        assertEquals("5", equationSolver.mSecondNumber);
    }

    @Test
    public void testInsertPoint_atTheFrontOpensAFraction() {
        equationSolver.mFirstNumber = "12";

        assertEquals(2, equationSolver.insertPoint(0));
        assertEquals("0.12", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertPoint_splitsTheNumberAtTheCursor() {
        equationSolver.mFirstNumber = "12";

        assertEquals(2, equationSolver.insertPoint(1));
        assertEquals("1.2", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertPoint_leavesANumberThatAlreadyHasOneAlone() {
        equationSolver.mFirstNumber = "1.2";

        assertEquals(3, equationSolver.insertPoint(3));
        assertEquals("1.2", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertPoint_onABareZero() {
        equationSolver.mFirstNumber = "0";

        assertEquals(2, equationSolver.insertPoint(0));
        assertEquals("0.", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertPoint_atTheFrontOfASignedNumberOpensAFractionAfterTheSign() {
        equationSolver.mFirstNumber = "-12";

        assertEquals(3, equationSolver.insertPoint(1));
        assertEquals("-0.12", equationSolver.mFirstNumber);
    }

    @Test
    public void testBackspace_removesTheCharacterBeforeTheCursor() {
        equationSolver.mFirstNumber = "152";

        assertEquals(1, equationSolver.backspace(2));
        assertEquals("12", equationSolver.mFirstNumber);
    }

    @Test
    public void testBackspace_atTheFrontOfTheFirstNumberDoesNothing() {
        equationSolver.mFirstNumber = "152";

        assertEquals(0, equationSolver.backspace(0));
        assertEquals("152", equationSolver.mFirstNumber);
    }

    @Test
    public void testBackspace_aFirstNumberEmptiedBecomesZero() {
        equationSolver.mFirstNumber = "5";

        assertEquals(1, equationSolver.backspace(1));
        assertEquals("0", equationSolver.mFirstNumber);
    }

    @Test
    public void testBackspace_atTheFrontOfASecondNumberNobodyTypedDropsTheOperation() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = null;

        assertEquals(2, equationSolver.backspace(5));
        assertNull(equationSolver.mOperation);
        assertNull(equationSolver.mSecondNumber);
        assertEquals("12", equationSolver.mFirstNumber);
    }

    @Test
    public void testBackspace_atTheFrontOfATypedSecondNumberStepsBackOverTheOperator() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = "34";

        assertEquals(2, equationSolver.backspace(5));
        assertEquals("12", equationSolver.mFirstNumber);
        assertEquals("34", equationSolver.mSecondNumber);
        assertEquals(EquationSolver.Operation.ADDITION, equationSolver.mOperation);

        assertEquals(2, equationSolver.backspace(4));
        assertEquals("12", equationSolver.mFirstNumber);
        assertEquals("34", equationSolver.mSecondNumber);
        assertEquals(EquationSolver.Operation.ADDITION, equationSolver.mOperation);
    }

    @Test
    public void testBackspace_removesFromTheSecondNumber() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = "34";

        assertEquals(6, equationSolver.backspace(7));
        assertEquals("3", equationSolver.mSecondNumber);
    }

    @Test
    public void testInsertNumber_anInsertThatChangesTheAmountRetypesIt() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mComputed = true;

        equationSolver.insertNumber("5", 1);

        assertFalse(equationSolver.mComputed);
    }

    @Test
    public void testInsertNumber_anInsertThatDoesNotChangeTheAmountLeavesAnAnswerAnAnswer() {
        equationSolver.mFirstNumber = "12.5";
        equationSolver.mComputed = true;

        equationSolver.insertNumber("0", END);

        assertTrue(equationSolver.mComputed);
        assertEquals("12.50", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertNumber_insertsEveryDigitOfTheTripleZeroKeyAtTheCursor() {
        equationSolver.mFirstNumber = "12";

        assertEquals(4, equationSolver.insertNumber("000", 1));
        assertEquals("10002", equationSolver.mFirstNumber);
    }

    @Test
    public void testInsertNumber_anEditOfTheFirstNumberWhileAnOperationIsPendingRetypesIt() {
        mockCurrencyUnit(2);
        equationSolver.mFirstNumber = "6.666666666666667";
        equationSolver.mComputed = true;
        equationSolver.mOperation = EquationSolver.Operation.ADDITION;
        equationSolver.mSecondNumber = null;

        assertEquals(1, equationSolver.insertNumber("9", 0));
        assertEquals("96.666666666666667", equationSolver.mFirstNumber);
        assertFalse(equationSolver.mComputed);

        equationSolver.backspace(END);

        assertNull(equationSolver.mOperation);
        assertEquals(9666L, equationSolver.getResult());
    }

    @Test
    public void testInsertPoint_aPointThatChangesTheAmountRetypesIt() {
        equationSolver.mFirstNumber = "1235";
        equationSolver.mComputed = true;

        equationSolver.insertPoint(1);

        assertEquals("1.235", equationSolver.mFirstNumber);
        assertFalse(equationSolver.mComputed);
    }

    @Test
    public void testInsertPoint_aPointThatDoesNotChangeTheAmountLeavesAnAnswerAnAnswer() {
        equationSolver.mFirstNumber = "12";
        equationSolver.mComputed = true;

        equationSolver.insertPoint(END);

        assertEquals("12.", equationSolver.mFirstNumber);
        assertTrue(equationSolver.mComputed);
    }
}
