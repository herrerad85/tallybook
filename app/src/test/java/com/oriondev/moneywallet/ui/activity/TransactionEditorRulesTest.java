package com.oriondev.moneywallet.ui.activity;

import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.ui.activity.TransactionEditorRules.SavingRow;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The transaction editor's rules, driven directly. Every case here used to be pinned by matching
 * the text of NewEditTransactionActivity, or was not pinned at all.
 */
public class TransactionEditorRulesTest {

    private static final int DEPOSIT = Contract.Direction.EXPENSE;
    private static final int WITHDRAWAL = Contract.Direction.INCOME;

    private static TransactionEditorRules rules(int type, boolean debtPayment) {
        TransactionEditorRules rules = new TransactionEditorRules();
        rules.setType(type);
        rules.setDebtPayment(debtPayment);
        return rules;
    }

    // ---- what the editor hides -------------------------------------------------------------

    @Test
    public void anOrdinaryTransactionOffersBothFields() {
        TransactionEditorRules rules = rules(TransactionEditorRules.TYPE_STANDARD, false);
        assertFalse(rules.hidesCategoryField());
        assertFalse(rules.hidesWalletField());
    }

    @Test
    public void aSavingTransactionHidesBothFields() {
        TransactionEditorRules rules = rules(TransactionEditorRules.TYPE_SAVING, false);
        assertTrue(rules.hidesCategoryField());
        assertTrue("a saving's progress is summed with no currency in it, so the wallet a saving "
                + "transaction sits in must not be changeable from the editor",
                rules.hidesWalletField());
    }

    @Test
    public void aSavingTransactionHidesItsWalletFieldWhateverTheDebtFlagSays() {
        assertTrue("the debt payment flag must not reach the saving case, or a saving row "
                + "carrying a paid category gets its wallet field back",
                rules(TransactionEditorRules.TYPE_SAVING, true).hidesWalletField());
    }

    @Test
    public void aTransactionFromAModelOffersBothFields() {
        TransactionEditorRules rules = rules(TransactionEditorRules.TYPE_MODEL, false);
        assertFalse("a row entered from a model is an ordinary transaction and has to be filed "
                + "under a category the user picks", rules.hidesCategoryField());
        assertFalse(rules.hidesWalletField());
    }

    @Test
    public void aTransferOffersBothFields() {
        TransactionEditorRules rules = rules(TransactionEditorRules.TYPE_TRANSFER, false);
        assertFalse(rules.hidesCategoryField());
        assertFalse(rules.hidesWalletField());
    }

    @Test
    public void anOrdinaryTransactionKeepsItsWalletFieldEvenWithTheFlagSet() {
        assertFalse("only a debt row reads the flag at all",
                rules(TransactionEditorRules.TYPE_STANDARD, true).hidesWalletField());
    }

    @Test
    public void aDebtPaymentHidesBothFields() {
        TransactionEditorRules rules = rules(TransactionEditorRules.TYPE_DEBT, true);
        assertTrue(rules.hidesCategoryField());
        assertTrue("a debt's progress is summed with no currency in it either",
                rules.hidesWalletField());
    }

    @Test
    public void aDebtsMasterTransactionKeepsItsWalletField() {
        TransactionEditorRules rules = rules(TransactionEditorRules.TYPE_DEBT, false);
        assertTrue("a master transaction is filed under the debt's own category, so the field "
                + "stays hidden", rules.hidesCategoryField());
        assertFalse("editing a master transaction's wallet moves the debt itself through "
                + "syncDebtOfMasterTransaction, so hiding the field there would take that away",
                rules.hidesWalletField());
    }

    @Test
    public void theDebtPaymentFlagStartsFalse() {
        assertFalse("a debt row the flag was never worked out for keeps its wallet field",
                new TransactionEditorRules().isDebtPayment());
    }

    // ---- what makes a row a payment --------------------------------------------------------

    /** Never the constant itself, since a cursor hands this out and nothing interns it. */
    private static String read(String value) {
        return new StringBuilder(value).toString();
    }

    @Test
    public void onlyThePaidCategoriesMakeARowAPayment() {
        assertTrue(TransactionEditorRules.isDebtPaymentTag(read(Contract.CategoryTag.PAID_DEBT)));
        assertTrue(TransactionEditorRules.isDebtPaymentTag(read(Contract.CategoryTag.PAID_CREDIT)));
        assertFalse("the debt's own category is a master transaction, not a payment",
                TransactionEditorRules.isDebtPaymentTag(Contract.CategoryTag.DEBT));
        assertFalse(TransactionEditorRules.isDebtPaymentTag(Contract.CategoryTag.CREDIT));
        assertFalse("a tag read off a name column, or a fifth constructor argument dropped, "
                + "leaves this null", TransactionEditorRules.isDebtPaymentTag(null));
        assertFalse(TransactionEditorRules.isDebtPaymentTag("Groceries"));
    }

    // ---- which category a new row is filed under -------------------------------------------

    @Test
    public void theDebtActionNamesTheKindOfDebt() {
        assertEquals(Contract.DebtType.DEBT,
                TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_PAY));
        assertEquals(Contract.DebtType.CREDIT,
                TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_RECEIVE));
        assertNull("a launch naming no action names no kind",
                TransactionEditorRules.debtTypeFor(0));
    }

    @Test
    public void settlingInFullNamesTheSameKindAsItsPlainAction() {
        assertEquals(Contract.DebtType.DEBT,
                TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_PAY_IN_FULL));
        assertEquals(Contract.DebtType.CREDIT,
                TransactionEditorRules.debtTypeFor(TransactionEditorRules.DEBT_RECEIVE_IN_FULL));
    }

    @Test
    public void onlyTheInFullActionsSettleInFull() {
        assertTrue(TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_PAY_IN_FULL));
        assertTrue(TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_RECEIVE_IN_FULL));
        assertFalse("pay keeps opening on nothing",
                TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_PAY));
        assertFalse(TransactionEditorRules.settlesInFull(TransactionEditorRules.DEBT_RECEIVE));
        assertFalse(TransactionEditorRules.settlesInFull(0));
    }

    // ---- what settling a debt in full opens on ---------------------------------------------

    @Test
    public void settlingInFullOpensOnWhatIsLeftToSettle() {
        assertEquals(7500L, TransactionEditorRules.settleDebtPrefill(10000L, 2500L));
    }

    @Test
    public void settlingInFullReadsADebtsNegativeProgressAsPaid() {
        assertEquals("a debt's payments are expenses and sum negative, and reading the sign as it "
                + "comes would offer more than the debt itself",
                7500L, TransactionEditorRules.settleDebtPrefill(10000L, -2500L));
    }

    @Test
    public void settlingInFullADebtPaidPastItsTargetOffersNothing() {
        assertEquals(0L, TransactionEditorRules.settleDebtPrefill(10000L, -12000L));
        assertEquals(0L, TransactionEditorRules.settleDebtPrefill(10000L, 10000L));
    }

    @Test
    public void eachKindOfDebtIsFiledUnderItsOwnPaidCategory() {
        assertEquals(Contract.CategoryTag.PAID_DEBT,
                TransactionEditorRules.debtCategoryTag(Contract.DebtType.DEBT));
        assertEquals(Contract.CategoryTag.PAID_CREDIT,
                TransactionEditorRules.debtCategoryTag(Contract.DebtType.CREDIT));
        assertNull("no kind, no category, and the editor runs no query",
                TransactionEditorRules.debtCategoryTag(null));
    }

    @Test
    public void aDepositAndAWithdrawAreFiledUnderTheirOwnCategories() {
        assertEquals(Contract.CategoryTag.SAVING_DEPOSIT, TransactionEditorRules
                .savingCategoryTag(TransactionEditorRules.SAVING_DEPOSIT));
        assertEquals(Contract.CategoryTag.SAVING_WITHDRAW, TransactionEditorRules
                .savingCategoryTag(TransactionEditorRules.SAVING_WITHDRAW));
    }

    @Test
    public void withdrawEverythingIsFiledUnderTheWithdrawCategory() {
        assertEquals("withdraw everything is a withdrawal. The activity used to reach this by "
                + "falling through one case into the other, where a break left the tag null and "
                + "the query below crashed the editor as it opened",
                Contract.CategoryTag.SAVING_WITHDRAW, TransactionEditorRules
                        .savingCategoryTag(TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING));
    }

    @Test
    public void anActionThatNamesNothingIsFiledNowhere() {
        assertNull("a crafted intent carrying no saving action used to bind null into the "
                + "category query and crash the editor as it opened",
                TransactionEditorRules.savingCategoryTag(0));
    }

    @Test
    public void onlyWithdrawEverythingCompletesTheSaving() {
        assertTrue(TransactionEditorRules
                .completesTheSaving(TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING));
        assertFalse(TransactionEditorRules
                .completesTheSaving(TransactionEditorRules.SAVING_WITHDRAW));
        assertFalse(TransactionEditorRules
                .completesTheSaving(TransactionEditorRules.SAVING_DEPOSIT));
        assertFalse(TransactionEditorRules.completesTheSaving(0));
    }

    // ---- the prefill and the ceiling -------------------------------------------------------

    @Test
    public void theWithdrawEverythingPrefillIsWhatTheSavingCanGive() {
        assertEquals(76000L, TransactionEditorRules.withdrawEverythingPrefill(76000L));
    }

    @Test
    public void theWithdrawEverythingPrefillIsNeverNegative() {
        assertEquals("a saving already carrying more withdrawals than it holds gives a negative "
                + "figure, and saving the field untouched would write a withdrawal of a negative "
                + "amount", 0L, TransactionEditorRules.withdrawEverythingPrefill(-4240L));
    }

    @Test
    public void aSavingWhoseRowsDoNotComeBackPrefillsNothing() {
        assertEquals(0L, TransactionEditorRules.withdrawEverythingPrefill(null));
    }

    @Test
    public void theCeilingIsHeldAtNothing() {
        assertEquals("a saving already under zero on some date from here on would otherwise "
                + "refuse every save of every row it carries, an amount of nothing included",
                0L, TransactionEditorRules.withdrawLimit(-1L));
        assertEquals(76000L, TransactionEditorRules.withdrawLimit(76000L));
    }

    @Test
    public void anAmountOfNothingIsNeverRefused() {
        assertTrue("or a stored withdrawal of nothing could never have its own note or date "
                + "corrected, and only deletion would be open",
                TransactionEditorRules.isWithinLimit(0L, 0L));
    }

    @Test
    public void theCeilingRefusesOnlyWhatIsOverIt() {
        assertTrue(TransactionEditorRules.isWithinLimit(75999L, 76000L));
        assertTrue("the ceiling itself is allowed",
                TransactionEditorRules.isWithinLimit(76000L, 76000L));
        assertFalse(TransactionEditorRules.isWithinLimit(76001L, 76000L));
    }

    // ---- when the ceiling applies at all ---------------------------------------------------

    @Test
    public void theCeilingAppliesInTheSavingsOwnCurrency() {
        assertTrue(TransactionEditorRules.ceilingApplies(read("EUR"), read("EUR")));
    }

    @Test
    public void theCeilingStepsAsideForAWalletInAnotherCurrency() {
        assertFalse("the two amounts are not comparable as they stand and this screen converts "
                + "nowhere else", TransactionEditorRules.ceilingApplies("EUR", "USD"));
    }

    @Test
    public void theCeilingStepsAsideWhenEitherCurrencyIsUnresolvable() {
        assertFalse("a saving whose own currency the app cannot resolve, which a restored backup "
                + "can produce", TransactionEditorRules.ceilingApplies(null, "EUR"));
        assertFalse(TransactionEditorRules.ceilingApplies("EUR", null));
        assertFalse(TransactionEditorRules.ceilingApplies(null, null));
    }

    // ---- the stored row that is kept or lowered --------------------------------------------

    @Test
    public void aStoredWithdrawalKeptExactlyAsItWasIsNeverRefused() {
        assertTrue(TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                500L, "2026-09-01 10:00:00", 500L, "2026-09-01 10:00:00"));
    }

    @Test
    public void aStoredWithdrawalLoweredIsNeverRefused() {
        assertTrue("two withdrawals that already have a saving under zero would otherwise freeze "
                + "each other, and deleting one would be the only way out",
                TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                        400L, "2026-09-01 10:00:00", 500L, "2026-09-01 10:00:00"));
    }

    @Test
    public void aStoredWithdrawalRaisedIsHeldToTheCeiling() {
        assertFalse(TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                600L, "2026-09-01 10:00:00", 500L, "2026-09-01 10:00:00"));
    }

    @Test
    public void aStoredWithdrawalMovedEarlierIsHeldToTheCeiling() {
        assertFalse("moving a stored row earlier is a new drain on every date it moves across",
                TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                        500L, "2026-08-01 10:00:00", 500L, "2026-09-01 10:00:00"));
    }

    @Test
    public void aStoredWithdrawalMovedLaterIsNeverRefused() {
        assertTrue(TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                500L, "2026-10-01 10:00:00", 500L, "2026-09-01 10:00:00"));
    }

    // ---- how the walk reads one row --------------------------------------------------------

    @Test
    public void aWithdrawalIsTheIncomeHalfOfThePair() {
        assertTrue("it pays money into the wallet. Reading the other constant here compiles and "
                + "turns every row into its opposite",
                TransactionEditorRules.isWithdrawal(Contract.Direction.INCOME));
        assertFalse(TransactionEditorRules.isWithdrawal(Contract.Direction.EXPENSE));
    }

    @Test
    public void aWithdrawalTakesTheSavingDownAndADepositPutsMoneyIn() {
        assertEquals(-500L, TransactionEditorRules.signedAmount(
                new SavingRow(1L, "2026-09-01 10:00:00", 500L, WITHDRAWAL, true)));
        assertEquals(500L, TransactionEditorRules.signedAmount(
                new SavingRow(1L, "2026-09-01 10:00:00", 500L, DEPOSIT, true)));
    }

    @Test
    public void anUnconfirmedDepositIsLeftOut() {
        assertFalse("landing takes being confirmed, and money that never arrives must not pay "
                + "for a withdrawal that does", TransactionEditorRules.countsTowardBalance(
                        new SavingRow(1L, "2026-09-01 10:00:00", 500L, DEPOSIT, false)));
    }

    @Test
    public void anUnconfirmedWithdrawalIsCounted() {
        assertTrue("leaving it out hands out a ceiling it can then take the saving under",
                TransactionEditorRules.countsTowardBalance(
                        new SavingRow(1L, "2026-09-01 10:00:00", 500L, WITHDRAWAL, false)));
    }

    @Test
    public void aConfirmedDepositIsCounted() {
        assertTrue(TransactionEditorRules.countsTowardBalance(
                new SavingRow(1L, "2026-09-01 10:00:00", 500L, DEPOSIT, true)));
    }

    // ---- the walk ---------------------------------------------------------------------------

    private static List<SavingRow> ledger() {
        return new ArrayList<>(Arrays.asList(
                new SavingRow(3L, "2026-09-01 10:00:00", 90000L, DEPOSIT, true),
                new SavingRow(1L, "2026-09-10 10:00:00", 15000L, WITHDRAWAL, true),
                new SavingRow(2L, "2026-09-20 10:00:00", 30000L, DEPOSIT, true)));
    }

    @Test
    public void theWalkCountsEveryRowFromTheDateOnScreen() {
        assertEquals("start 10000 plus 90000 in, minus 15000 out, is 85000 before the deposit on "
                + "the 20th lifts it", 85000L, TransactionEditorRules.lowestSavingBalanceFrom(
                        10000L, ledger(), -1L, "2026-09-01 10:00:00"));
    }

    @Test
    public void theWalkCountsFromTheDateItIsGivenAndNotFromToday() {
        assertEquals("the same rows from a date after all of them answer what the saving ends "
                + "up holding, not the 85000 it dips to on the way. A row dated earlier drains "
                + "every day from its own date onwards, which is the defect this replaced",
                115000L, TransactionEditorRules.lowestSavingBalanceFrom(
                        10000L, ledger(), -1L, "2026-09-25 10:00:00"));
    }

    @Test
    public void theWalkLeavesOutTheRowBeingEdited() {
        assertEquals("the row being edited must not be held against its own drain, or it could "
                + "never be raised", 100000L, TransactionEditorRules.lowestSavingBalanceFrom(
                        10000L, ledger(), 1L, "2026-09-01 10:00:00"));
    }

    @Test
    public void theWalkOrdersTheRowsItself() {
        List<SavingRow> shuffled = ledger();
        Collections.reverse(shuffled);
        assertEquals("the answer must not depend on the order the caller read the rows in. "
                + "The ids run against the dates on purpose, or a sort by id passes as a sort "
                + "by date",
                85000L, TransactionEditorRules.lowestSavingBalanceFrom(
                        10000L, shuffled, -1L, "2026-09-01 10:00:00"));
    }

    @Test
    public void theWalkLeavesOutAnUnconfirmedDepositThatWouldPayForAWithdrawal() {
        List<SavingRow> rows = new ArrayList<>(Arrays.asList(
                new SavingRow(1L, "2026-09-01 10:00:00", 300000L, DEPOSIT, false),
                new SavingRow(2L, "2026-10-01 10:00:00", 300000L, WITHDRAWAL, true)));
        assertEquals("an unconfirmed deposit funding a withdrawal dated next month is the "
                + "reported defect: the goal went to 3,000.00 negative when that row landed",
                -300000L, TransactionEditorRules.lowestSavingBalanceFrom(
                        0L, rows, -1L, "2026-09-01 10:00:00"));
    }

    @Test
    public void theWalkOverNoRowsAnswersTheStartMoney() {
        assertEquals(10000L, TransactionEditorRules.lowestSavingBalanceFrom(
                10000L, new ArrayList<SavingRow>(), -1L, "2026-09-01 10:00:00"));
    }

    // ---- the state the editor carries across a recreate ------------------------------------

    @Test
    public void theRulesCarryTheirStateAcrossASerializeRoundTrip() throws Exception {
        TransactionEditorRules before = new TransactionEditorRules();
        before.setType(TransactionEditorRules.TYPE_DEBT);
        before.setDebtId(7L);
        before.setSavingId(88L);
        before.setSavingCompleted(true);
        before.setDebtPayment(true);
        TransactionEditorRules after = roundTrip(before);
        assertEquals(TransactionEditorRules.TYPE_DEBT, after.getType());
        assertEquals(Long.valueOf(7L), after.getDebtId());
        assertEquals("asserting null here passes whether or not the field survives, which is "
                + "how a lost saving id would read", Long.valueOf(88L), after.getSavingId());
        assertTrue(after.isSavingCompleted());
        assertTrue("losing this gives a payment its wallet field back on the next rotation",
                after.isDebtPayment());
    }

    private static TransactionEditorRules roundTrip(TransactionEditorRules rules) throws Exception {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        java.io.ObjectOutputStream out = new java.io.ObjectOutputStream(bytes);
        out.writeObject(rules);
        out.close();
        java.io.ObjectInputStream in = new java.io.ObjectInputStream(
                new java.io.ByteArrayInputStream(bytes.toByteArray()));
        return (TransactionEditorRules) in.readObject();
    }

}
