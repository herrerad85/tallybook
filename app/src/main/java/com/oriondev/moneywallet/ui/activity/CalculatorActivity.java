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

import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.EquationSolver;
import com.oriondev.moneywallet.utils.SystemBars;

/**
 * Created by andre on 23/03/2018.
 */
public class CalculatorActivity extends SinglePanelActivity implements View.OnClickListener, EquationSolver.Controller {

    public static final String ACTIVITY_MODE = "CalculatorActivity::Parameters::ActivityMode";
    public static final String CURRENCY = "CalculatorActivity::Parameters::Currency";
    public static final String MONEY = "CalculatorActivity::Parameters::Money";
    public static final String ALLOW_NEGATIVE = "CalculatorActivity::Parameters::AllowNegative";

    private static final String SS_CURSOR = "CalculatorActivity::SavedState::Cursor";

    public static final int MODE_CALCULATOR = 0;
    public static final int MODE_KEYPAD = 1;

    private static final String OP_000 = "000";
    private static final String OP_0 = "0";
    private static final String OP_1 = "1";
    private static final String OP_2 = "2";
    private static final String OP_3 = "3";
    private static final String OP_4 = "4";
    private static final String OP_5 = "5";
    private static final String OP_6 = "6";
    private static final String OP_7 = "7";
    private static final String OP_8 = "8";
    private static final String OP_9 = "9";
    private static final String OP_POINT = ".";
    private static final String OP_CLEAR = "C";
    private static final String OP_CANCEL = "B";
    private static final String OP_ADDITION = "A";
    private static final String OP_SUBTRACTION = "S";
    private static final String OP_MULTIPLICATION = "M";
    private static final String OP_DIVISION = "D";
    private static final String OP_EXECUTE = "E";

    private static final ActionMode.Callback NO_TEXT_ACTION_MODE = new ActionMode.Callback() {

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            return false;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
        }
    };

    private EditText mDisplayEditText;
    private Button mActionButton;
    private EquationSolver mSolver;

    private boolean mKeypadMode;
    private boolean mAllowNegative;
    private boolean mRendering;

    /**
     * The keypad fills the bottom of this screen and carries the primary background, so that is what is
     * behind the navigation bar here, not the list background the base class assumes.
     */
    @Override
    protected int getColorBehindNavigationBar(ITheme theme) {
        return theme.getColorPrimaryBackground();
    }

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_panel_calculator, parent, true);
        // The keypad fills the lower half of the window and nothing here scrolls, so the keys
        // would otherwise sit partly under the navigation bar.
        SystemBars.pad(view.findViewById(R.id.keypad_container),
                false, getResources().getBoolean(R.bool.panel_fills_window), true);
        mDisplayEditText = view.findViewById(R.id.display_text_view);
        // The keypad is the only writer of the field, and the filter refuses every other change.
        // The field's own state restore would go through that filter and come back empty, so the
        // render from the solver is the only source of the text and the cursor is carried here.
        mDisplayEditText.setShowSoftInputOnFocus(false);
        mDisplayEditText.setFilters(new InputFilter[] {(source, start, end, dest, dstart, dend) ->
                mRendering ? null : dest.subSequence(dstart, dend)});
        mDisplayEditText.setSaveEnabled(false);
        // The popup's cut and paste could not reach the field, so the popup goes.
        mDisplayEditText.setCustomSelectionActionModeCallback(NO_TEXT_ACTION_MODE);
        mDisplayEditText.setCustomInsertionActionModeCallback(NO_TEXT_ACTION_MODE);
        mDisplayEditText.requestFocus();
        mActionButton = view.findViewById(R.id.keyboard_action_button);
        registerListener(view.findViewById(R.id.keyboard_000_button), OP_000);
        registerListener(view.findViewById(R.id.keyboard_0_button), OP_0);
        registerListener(view.findViewById(R.id.keyboard_1_button), OP_1);
        registerListener(view.findViewById(R.id.keyboard_2_button), OP_2);
        registerListener(view.findViewById(R.id.keyboard_3_button), OP_3);
        registerListener(view.findViewById(R.id.keyboard_4_button), OP_4);
        registerListener(view.findViewById(R.id.keyboard_5_button), OP_5);
        registerListener(view.findViewById(R.id.keyboard_6_button), OP_6);
        registerListener(view.findViewById(R.id.keyboard_7_button), OP_7);
        registerListener(view.findViewById(R.id.keyboard_8_button), OP_8);
        registerListener(view.findViewById(R.id.keyboard_9_button), OP_9);
        registerListener(view.findViewById(R.id.keyboard_clear_button), OP_CLEAR);
        registerListener(view.findViewById(R.id.keyboard_cancel_button), OP_CANCEL);
        registerListener(view.findViewById(R.id.keyboard_point_button), OP_POINT);
        registerListener(view.findViewById(R.id.keyboard_division_button), OP_DIVISION);
        registerListener(view.findViewById(R.id.keyboard_multiplication_button), OP_MULTIPLICATION);
        registerListener(view.findViewById(R.id.keyboard_addition_button), OP_ADDITION);
        registerListener(view.findViewById(R.id.keyboard_subtraction_button), OP_SUBTRACTION);
        registerListener(view.findViewById(R.id.keyboard_action_button), OP_EXECUTE);
        mSolver = new EquationSolver(savedInstanceState, this);
        if (savedInstanceState != null) {
            setCursor(savedInstanceState.getInt(SS_CURSOR, mDisplayEditText.length()));
        }
    }

    @Override
    protected void onViewCreated(Bundle savedInstanceState) {
        Intent intent = getIntent();
        mKeypadMode = intent.getIntExtra(ACTIVITY_MODE, MODE_CALCULATOR) == MODE_KEYPAD;
        mAllowNegative = intent.getBooleanExtra(ALLOW_NEGATIVE, false);
        if (savedInstanceState == null) {
            CurrencyUnit currency = intent.getParcelableExtra(CURRENCY);
            long money = intent.getLongExtra(MONEY, 0L);
            mSolver.setValue(currency, money);
        }
        updateActionButton();
    }

    private void registerListener(View button, String operation) {
        button.setTag(operation);
        button.setOnClickListener(this);
    }

    @Override
    protected int getActivityTitleRes() {
        return R.string.title_activity_calculator;
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        return false;
    }

    @Override
    public void onClick(View button) {
        String operation = (String) button.getTag();
        switch (operation) {
            case OP_CLEAR:
                mSolver.clear();
                break;
            case OP_CANCEL:
                setCursor(mSolver.backspace(mDisplayEditText.getSelectionStart()));
                break;
            case OP_EXECUTE:
                execute();
                break;
            case OP_ADDITION:
                mSolver.appendOperation(EquationSolver.Operation.ADDITION);
                break;
            case OP_SUBTRACTION:
                mSolver.appendOperation(EquationSolver.Operation.SUBTRACTION);
                break;
            case OP_MULTIPLICATION:
                mSolver.appendOperation(EquationSolver.Operation.MULTIPLICATION);
                break;
            case OP_DIVISION:
                mSolver.appendOperation(EquationSolver.Operation.DIVISION);
                break;
            case OP_POINT:
                setCursor(mSolver.insertPoint(mDisplayEditText.getSelectionStart()));
                break;
            default:
                setCursor(mSolver.insertNumber(operation, mDisplayEditText.getSelectionStart()));
                break;
        }
    }

    /**
     * The three editing keys render from inside the solver call, and that render puts the cursor
     * at the end of the display, so this is what moves it back to where the edit landed.
     */
    private void setCursor(int cursor) {
        mDisplayEditText.setSelection(Math.min(Math.max(cursor, 0), mDisplayEditText.length()));
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mSolver.onSaveInstanceState(outState);
        outState.putInt(SS_CURSOR, mDisplayEditText.getSelectionStart());
    }

    private void execute() {
        if (mSolver.isPendingOperation()) {
            if (!mSolver.execute(true)) {
                // TODO: show error!
            }
        } else if (mKeypadMode) {
            long money = mSolver.getResult();
            // The screen that opened the keypad says whether a negative belongs in the field it is
            // filling. Where it does not, an amount below zero is not handed back, and is left on
            // the display so it can be corrected.
            if (money < 0 && !mAllowNegative) {
                ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.message_error_negative_amount)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            Intent intent = new Intent();
            intent.putExtra(MONEY, money);
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    public void onUpdateDisplay(String text) {
        mRendering = true;
        mDisplayEditText.setText(text);
        mRendering = false;
        mDisplayEditText.setSelection(text.length());
        updateActionButton();
    }

    /**
     * The action key doubles as the equals key of the calculator and as the submit key of the
     * amount keypad. Showing "=" for the common case of just typing an amount is unclear, so in
     * keypad mode with no pending equation we show a confirm affordance instead, while still
     * keeping "=" whenever there is a pending operation to compute.
     */
    private void updateActionButton() {
        if (mActionButton == null || mSolver == null) {
            return;
        }
        boolean confirm = mKeypadMode && !mSolver.isPendingOperation();
        if (confirm) {
            mActionButton.setText(R.string.keyboard_confirm);
            mActionButton.setContentDescription(getString(R.string.description_calculator_confirm));
        } else {
            mActionButton.setText(R.string.keyboard_equal);
            mActionButton.setContentDescription(getString(R.string.description_calculator_equals));
        }
    }
}
