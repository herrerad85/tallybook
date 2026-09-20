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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Event;
import com.oriondev.moneywallet.model.Person;
import com.oriondev.moneywallet.model.Place;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.AttachmentPicker;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.picker.EventPicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.picker.PersonPicker;
import com.oriondev.moneywallet.picker.PlacePicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.SQLiteDataException;
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.view.AttachmentView;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.ui.view.text.Validator;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateFormatter;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.MoneyFormatter;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by andrea on 06/03/18.
 */
public class NewEditTransactionActivity extends NewEditItemActivity implements MoneyPicker.Controller,
                                                                            CategoryPicker.Controller,
                                                                            DateTimePicker.Controller,
                                                                            WalletPicker.SingleWalletController,
                                                                            EventPicker.Controller,
                                                                            PersonPicker.Controller,
                                                                            PlacePicker.Controller,
                                                                            AttachmentPicker.Controller, AttachmentView.Controller {

    public static final String TYPE = "NewEditTransactionActivity::Type";
    public static final String DEBT_ID = "NewEditTransactionActivity::DebtId";
    public static final String DEBT_ACTION = "NewEditTransactionActivity::DebtAction";
    public static final String SAVING_ID = "NewEditTransactionActivity::SavingId";
    public static final String SAVING_ACTION = "NewEditTransactionActivity::SavingAction";
    public static final String PERSON_ID = "NewEditTransactionActivity::PersonId";
    public static final String MODEL_ID = "NewEditTransactionActivity::ModelId";

    /**
     * Id of a transaction a new one is being copied from. Only read in {@link Mode#NEW_ITEM},
     * where it fills the editor from that transaction instead of from the arguments above.
     */
    public static final String DUPLICATE_ID = "NewEditTransactionActivity::DuplicateId";

    /**
     * The wallet a new transaction should open on, when the caller knows one. Without it the
     * editor opens on whichever wallet the app is currently showing, which is the right answer
     * from inside the app and the wrong one from a home screen widget, where the wallet the user
     * tapped is the one they meant.
     */
    public static final String WALLET_ID = "NewEditTransactionActivity::WalletId";

    public static final int TYPE_STANDARD = TransactionEditorRules.TYPE_STANDARD;
    public static final int TYPE_TRANSFER = TransactionEditorRules.TYPE_TRANSFER;
    public static final int TYPE_DEBT = TransactionEditorRules.TYPE_DEBT;
    public static final int TYPE_SAVING = TransactionEditorRules.TYPE_SAVING;
    public static final int TYPE_MODEL = TransactionEditorRules.TYPE_MODEL;

    public static final int DEBT_PAY = TransactionEditorRules.DEBT_PAY;
    public static final int DEBT_RECEIVE = TransactionEditorRules.DEBT_RECEIVE;
    public static final int DEBT_PAY_IN_FULL = TransactionEditorRules.DEBT_PAY_IN_FULL;
    public static final int DEBT_RECEIVE_IN_FULL = TransactionEditorRules.DEBT_RECEIVE_IN_FULL;

    public static final int SAVING_DEPOSIT = TransactionEditorRules.SAVING_DEPOSIT;
    public static final int SAVING_WITHDRAW = TransactionEditorRules.SAVING_WITHDRAW;
    public static final int SAVING_WITHDRAW_EVERYTHING =
            TransactionEditorRules.SAVING_WITHDRAW_EVERYTHING;

    private static final String TAG_MONEY_PICKER = "NewEditTransactionActivity::Tag::MoneyPicker";
    private static final String TAG_CATEGORY_PICKER = "NewEditTransactionActivity::Tag::CategoryPicker";
    private static final String TAG_DATETIME_PICKER = "NewEditTransactionActivity::Tag::DateTimePicker";
    private static final String TAG_WALLET_PICKER = "NewEditTransactionActivity::Tag::WalletPicker";
    private static final String TAG_EVENT_PICKER = "NewEditTransactionActivity::Tag::EventPicker";
    private static final String TAG_PLACE_PICKER = "NewEditTransactionActivity::Tag::PlacePicker";
    private static final String TAG_PERSON_PICKER = "NewEditTransactionActivity::Tag::PersonPicker";
    private static final String TAG_ATTACHMENT_PICKER = "NewEditTransactionActivity::Tag::AttachmentPicker";

    private static final String SS_RULES = "NewEditTransactionActivity::SavedState::Rules";

    private TextView mCurrencyTextView;
    private TextView mMoneyTextView;
    private MaterialEditText mDescriptionEditText;
    private MaterialEditText mCategoryEditText;
    private MaterialEditText mDateEditText;
    private MaterialEditText mTimeEditText;
    private MaterialEditText mWalletEditText;
    private MaterialEditText mEventEditText;
    private MaterialEditText mPeopleEditText;
    private MaterialEditText mPlaceEditText;
    private MaterialEditText mNoteEditText;
    private CheckBox mConfirmedCheckBox;
    private CheckBox mCountInTotalCheckBox;
    private AttachmentView mAttachmentView;

    private MoneyPicker mMoneyPicker;
    private CategoryPicker mCategoryPicker;
    private DateTimePicker mDateTimePicker;
    private WalletPicker mWalletPicker;
    private EventPicker mEventPicker;
    private PlacePicker mPlacePicker;
    private PersonPicker mPersonPicker;
    private AttachmentPicker mAttachmentPicker;

    private TransactionEditorRules mRules = new TransactionEditorRules();

    // true while the category on screen is one the rules filled in and the user has not touched.
    // It is not carried across a rotation, so a rotated editor treats the category as the user's
    // and leaves it alone, which is the safe direction to fail in
    private boolean mCategoryFilledByRule;

    private MoneyFormatter mMoneyFormatter = MoneyFormatter.getInstance();

    @Override
    protected void onCreateHeaderView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_header_new_edit_money_item, parent, true);
        mCurrencyTextView = view.findViewById(R.id.currency_text_view);
        mMoneyTextView = view.findViewById(R.id.money_text_view);
        // attach a listener to the views
        mMoneyTextView.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View view) {
                mMoneyPicker.showPicker();
            }

        });
    }

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_panel_new_edit_transaction, parent, true);
        mDescriptionEditText = view.findViewById(R.id.description_edit_text);
        mCategoryEditText = view.findViewById(R.id.category_edit_text);
        mDateEditText = view.findViewById(R.id.date_edit_text);
        mTimeEditText = view.findViewById(R.id.time_edit_text);
        mWalletEditText = view.findViewById(R.id.wallet_edit_text);
        mEventEditText = view.findViewById(R.id.event_edit_text);
        mPeopleEditText = view.findViewById(R.id.people_edit_text);
        mPlaceEditText = view.findViewById(R.id.place_edit_text);
        mNoteEditText = view.findViewById(R.id.note_edit_text);
        mConfirmedCheckBox = view.findViewById(R.id.confirmed_checkbox);
        mCountInTotalCheckBox = view.findViewById(R.id.count_in_total_checkbox);
        mAttachmentView = view.findViewById(R.id.attachment_view);
        // disable unused edit texts
        mCategoryEditText.setTextViewMode(true);
        mDateEditText.setTextViewMode(true);
        mTimeEditText.setTextViewMode(true);
        mWalletEditText.setTextViewMode(true);
        mEventEditText.setTextViewMode(true);
        mPeopleEditText.setTextViewMode(true);
        mPlaceEditText.setTextViewMode(true);
        // add validators
        mDateEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_date);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mDateTimePicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mCategoryEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_category);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mCategoryPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mWalletEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_wallet);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mWalletPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        // attach listeners
        mCategoryEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mCategoryPicker.showPicker();
            }

        });
        mDateEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mDateTimePicker.showDatePicker();
            }

        });
        mTimeEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mDateTimePicker.showTimePicker();
            }

        });
        mWalletEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mWalletPicker.showSingleWalletPicker();
            }

        });
        mEventEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mEventPicker.showPicker(mDateTimePicker.getCurrentDateTime());
            }

        });
        mEventEditText.setOnCancelButtonClickListener(new MaterialEditText.CancelButtonListener() {

            @Override
            public boolean onCancelButtonClick(@NonNull MaterialEditText materialEditText) {
                mEventPicker.setCurrentEvent(null);
                return false;
            }

        });
        mPeopleEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mPersonPicker.showPicker();
            }

        });
        mPeopleEditText.setOnCancelButtonClickListener(new MaterialEditText.CancelButtonListener() {

            @Override
            public boolean onCancelButtonClick(@NonNull MaterialEditText materialEditText) {
                mPersonPicker.setPeople(null);
                return false;
            }

        });
        mPlaceEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mPlacePicker.showPicker();
            }

        });
        mPlaceEditText.setOnCancelButtonClickListener(new MaterialEditText.CancelButtonListener() {

            @Override
            public boolean onCancelButtonClick(@NonNull MaterialEditText materialEditText) {
                mPlacePicker.setCurrentPlace(null);
                return false;
            }

        });
        mAttachmentView.setController(this);
    }

    @Override
    protected void onViewCreated(Bundle savedInstanceState) {
        super.onViewCreated(savedInstanceState);
        long money = 0L;
        Category category = null;
        Date datetime = null;
        Wallet wallet = null;
        Event event = null;
        Person[] people = null;
        Place place = null;
        ArrayList<Attachment> attachments = null;
        if (savedInstanceState == null) {
            ContentResolver contentResolver = getContentResolver();
            // A copy opens on what the transaction it came from holds, so it reads that row
            // through the same block the editor reads its own. Two things it does not take: the
            // date, which starts at now because the copy is being entered now, and the
            // attachments, since removing one in this editor deletes the attachment row and its
            // file outright, with no check for anything else pointing at it, which would leave a
            // copy holding nothing.
            //
            // Only a plain transaction is copied. The others each carry a link the editor does
            // not draw: a leg belongs to a transfer, and a payment or a deposit belongs to a debt
            // or a saving whose totals a second one would move with nothing on screen saying so.
            // The action is not offered for them, and the kind is checked again here rather than
            // trusted from the caller, since this activity is exported.
            long duplicateId = getMode() == Mode.NEW_ITEM ? getIntent().getLongExtra(DUPLICATE_ID, 0L) : 0L;
            boolean duplicating = duplicateId > 0L && isPlainTransaction(contentResolver, duplicateId);
            if (duplicating) {
                datetime = new Date();
            }
            if (getMode() == Mode.EDIT_ITEM || duplicating) {
                Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS,
                        duplicating ? duplicateId : getItemId());
                String[] projection = new String[] {
                        Contract.Transaction.MONEY,
                        Contract.Transaction.DATE,
                        Contract.Transaction.DESCRIPTION,
                        Contract.Transaction.CATEGORY_ID,
                        Contract.Transaction.CATEGORY_NAME,
                        Contract.Transaction.CATEGORY_ICON,
                        Contract.Transaction.CATEGORY_TYPE,
                        Contract.Transaction.CATEGORY_TAG,
                        Contract.Transaction.CATEGORY_SHOW_REPORT,
                        Contract.Transaction.TYPE,
                        Contract.Transaction.WALLET_ID,
                        Contract.Transaction.WALLET_NAME,
                        Contract.Transaction.WALLET_ICON,
                        Contract.Transaction.WALLET_CURRENCY,
                        Contract.Transaction.PLACE_ID,
                        Contract.Transaction.PLACE_NAME,
                        Contract.Transaction.PLACE_ICON,
                        Contract.Transaction.PLACE_ADDRESS,
                        Contract.Transaction.PLACE_LATITUDE,
                        Contract.Transaction.PLACE_LONGITUDE,
                        Contract.Transaction.NOTE,
                        Contract.Transaction.EVENT_ID,
                        Contract.Transaction.EVENT_NAME,
                        Contract.Transaction.EVENT_ICON,
                        Contract.Transaction.EVENT_NOTE,
                        Contract.Transaction.EVENT_START_DATE,
                        Contract.Transaction.EVENT_END_DATE,
                        Contract.Transaction.SAVING_ID,
                        Contract.Transaction.DEBT_ID,
                        Contract.Transaction.CONFIRMED,
                        Contract.Transaction.COUNT_IN_TOTAL
                };
                Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        money = cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY));
                        if (!duplicating) {
                            datetime = DateUtils.getDateFromSQLDateTimeString(cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE)));
                        }
                        mDescriptionEditText.setText(cursor.getString(cursor.getColumnIndex(Contract.Transaction.DESCRIPTION)));
                        category = new Category(
                                cursor.getLong(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Transaction.CATEGORY_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.CATEGORY_ICON))),
                                Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CATEGORY_TYPE))),
                                cursor.getString(cursor.getColumnIndex(Contract.Transaction.CATEGORY_TAG))
                        );
                        mRules.setType(cursor.getInt(cursor.getColumnIndex(Contract.Transaction.TYPE)));
                        wallet = new Wallet(
                                cursor.getLong(cursor.getColumnIndex(Contract.Transaction.WALLET_ID)),
                                cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_NAME)),
                                IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_ICON))),
                                CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Transaction.WALLET_CURRENCY))),
                                0L,0L
                        );
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.PLACE_ID))) {
                            place = new Place(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.PLACE_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Transaction.PLACE_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.PLACE_ICON))),
                                    cursor.getString(cursor.getColumnIndex(Contract.Transaction.PLACE_ADDRESS)),
                                    cursor.isNull(cursor.getColumnIndex(Contract.Transaction.PLACE_LATITUDE)) ? null : cursor.getDouble(cursor.getColumnIndex(Contract.Transaction.PLACE_LATITUDE)),
                                    cursor.isNull(cursor.getColumnIndex(Contract.Transaction.PLACE_LONGITUDE)) ? null : cursor.getDouble(cursor.getColumnIndex(Contract.Transaction.PLACE_LONGITUDE))
                            );
                        }
                        mNoteEditText.setText(cursor.getString(cursor.getColumnIndex(Contract.Transaction.NOTE)));
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.EVENT_ID))) {
                            event = new Event(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.EVENT_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_ICON))),
                                    DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_START_DATE))),
                                    DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.Transaction.EVENT_END_DATE)))
                            );
                        }
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.DEBT_ID))) {
                            mRules.setDebtId(cursor.getLong(cursor.getColumnIndex(Contract.Transaction.DEBT_ID)));
                        }
                        if (!cursor.isNull(cursor.getColumnIndex(Contract.Transaction.SAVING_ID))) {
                            mRules.setSavingId(cursor.getLong(cursor.getColumnIndex(Contract.Transaction.SAVING_ID)));
                        }
                        mConfirmedCheckBox.setChecked(cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CONFIRMED)) == 1);
                        mCountInTotalCheckBox.setChecked(cursor.getInt(cursor.getColumnIndex(Contract.Transaction.COUNT_IN_TOTAL)) == 1);
                    }
                    cursor.close();
                }
                // before continuing, check if the transaction is part of a transfer
                if (mRules.getType() == TYPE_TRANSFER) {
                    uri = DataContentProvider.CONTENT_TRANSFERS;
                    projection = new String[] {Contract.Transfer.ID};
                    String selection = Contract.Transfer.TRANSACTION_FROM_ID + " = ? OR " +
                                       Contract.Transfer.TRANSACTION_TO_ID + " = ? OR " +
                                       Contract.Transfer.TRANSACTION_TAX_ID + " = ?";
                    String[] selectionArgs = new String[] {String.valueOf(getItemId()),
                            String.valueOf(getItemId()), String.valueOf(getItemId())};
                    cursor = contentResolver.query(uri, projection, selection, selectionArgs, null);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                long transferId = cursor.getLong(cursor.getColumnIndex(Contract.Transfer.ID));
                                Intent intent = new Intent(this, NewEditTransferActivity.class);
                                intent.putExtra(NewEditTransferActivity.MODE, Mode.EDIT_ITEM);
                                intent.putExtra(NewEditTransferActivity.ID, transferId);
                                startActivity(intent);
                                finish();
                                return;
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                }
                // the previous cursor contains only a column with the list of ids of linked people.
                // we need instead to buildMaterialDialog the full person object so we must perform a separated
                // query to the database to obtain the full cursor.
                Uri peopleUri = Uri.withAppendedPath(uri, "people");
                projection = new String[] {
                        Contract.Person.ID,
                        Contract.Person.NAME,
                        Contract.Person.ICON
                };
                cursor = contentResolver.query(peopleUri, projection, null, null, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        people = new Person[cursor.getCount()];
                        for (int i = 0; cursor.moveToPosition(i) && i < cursor.getCount(); i++) {
                            people[i] = new Person(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Person.ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Person.NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Person.ICON)))
                            );
                        }
                    }
                    cursor.close();
                }
                // load all attachments
                if (!duplicating) {
                    attachments = new ArrayList<>();
                    Uri attachmentsUri = Uri.withAppendedPath(uri, "attachments");
                    projection = new String[] {
                            Contract.Attachment.ID,
                            Contract.Attachment.FILE,
                            Contract.Attachment.NAME,
                            Contract.Attachment.TYPE,
                            Contract.Attachment.SIZE
                    };
                    cursor = contentResolver.query(attachmentsUri, projection, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            for (int i = 0; cursor.moveToPosition(i) && i < cursor.getCount(); i++) {
                                Attachment attachment = new Attachment(
                                        cursor.getLong(cursor.getColumnIndex(Contract.Attachment.ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Attachment.FILE)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Attachment.NAME)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Attachment.TYPE)),
                                        cursor.getLong(cursor.getColumnIndex(Contract.Attachment.SIZE))
                                );
                                attachments.add(attachment);
                            }
                        }
                        cursor.close();
                    }
                }
            } else {
                Intent intent = getIntent();
                mRules.setType(intent.getIntExtra(TYPE, TYPE_STANDARD));
                if (mRules.getType() == TYPE_STANDARD) {
                    String[] projection = new String[] {
                            Contract.Wallet.ID,
                            Contract.Wallet.NAME,
                            Contract.Wallet.ICON,
                            Contract.Wallet.CURRENCY,
                            Contract.Wallet.START_MONEY,
                            Contract.Wallet.TOTAL_MONEY
                    };
                    long currentWallet = intent.getLongExtra(WALLET_ID, PreferenceManager.getCurrentWallet());
                    Cursor cursor;
                    if (currentWallet == PreferenceManager.TOTAL_WALLET_ID) {
                        Uri uri = DataContentProvider.CONTENT_WALLETS;
                        cursor = contentResolver.query(uri, projection, null, null, null);
                    } else {
                        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_WALLETS, currentWallet);
                        cursor = contentResolver.query(uri, projection, null, null, null);
                    }
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            wallet = new Wallet(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Wallet.ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Wallet.NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Wallet.ICON))),
                                    CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Wallet.CURRENCY))),
                                    cursor.getLong(cursor.getColumnIndex(Contract.Wallet.START_MONEY)),
                                    cursor.getLong(cursor.getColumnIndex(Contract.Wallet.TOTAL_MONEY))
                            );
                        }
                        cursor.close();
                    }
                    // opened from a person, so that person is already attached and the user only
                    // has to pick a category, which is what decides income or expense.
                    //
                    // Read here and nowhere else on purpose. An existing transaction must not have
                    // its people rewritten by an intent, and the debt and saving branches load
                    // people of their own that this would overwrite. Adding the extra to one of
                    // those launches would be silently ignored, not wrong.
                    if (intent.hasExtra(PERSON_ID)) {
                        Uri personUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_PEOPLE, intent.getLongExtra(PERSON_ID, 0L));
                        String[] personProjection = new String[] {
                                Contract.Person.ID,
                                Contract.Person.NAME,
                                Contract.Person.ICON
                        };
                        Cursor personCursor = contentResolver.query(personUri, personProjection, null, null, null);
                        if (personCursor != null) {
                            if (personCursor.moveToFirst()) {
                                people = new Person[] {
                                        new Person(
                                                personCursor.getLong(personCursor.getColumnIndex(Contract.Person.ID)),
                                                personCursor.getString(personCursor.getColumnIndex(Contract.Person.NAME)),
                                                IconLoader.parse(personCursor.getString(personCursor.getColumnIndex(Contract.Person.ICON)))
                                        )
                                };
                            }
                            personCursor.close();
                        }
                    }
                } else if (mRules.getType() == TYPE_TRANSFER) {
                    // In this case the activity has been launched to insert a new transfer so we
                    // have to simply start the correct activity and finish the current one.
                    startActivity(new Intent(this, NewEditTransferActivity.class));
                    finish();
                } else if (mRules.getType() == TYPE_DEBT) {
                    mRules.setDebtId(intent.getLongExtra(DEBT_ID, 0L));
                    Contract.DebtType debtType = null;
                    Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_DEBTS, mRules.getDebtId());
                    String[] projection = new String[] {
                            Contract.Debt.TYPE,
                            Contract.Debt.DESCRIPTION,
                            Contract.Debt.MONEY,
                            Contract.Debt.PROGRESS,
                            Contract.Debt.WALLET_ID,
                            Contract.Debt.WALLET_NAME,
                            Contract.Debt.WALLET_ICON,
                            Contract.Debt.WALLET_CURRENCY,
                            Contract.Debt.PLACE_ID,
                            Contract.Debt.PLACE_NAME,
                            Contract.Debt.PLACE_ICON,
                            Contract.Debt.PLACE_ADDRESS,
                            Contract.Debt.PLACE_LATITUDE,
                            Contract.Debt.PLACE_LONGITUDE,
                    };
                    Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            debtType = Contract.DebtType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Debt.TYPE)));
                            mDescriptionEditText.setText(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Debt.DESCRIPTION)));
                            if (TransactionEditorRules.settlesInFull(intent.getIntExtra(DEBT_ACTION, 0))) {
                                money = TransactionEditorRules.settleDebtPrefill(
                                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.MONEY)),
                                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Debt.PROGRESS)));
                            }
                            wallet = new Wallet(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Debt.WALLET_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Debt.WALLET_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Debt.WALLET_ICON))),
                                    CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Debt.WALLET_CURRENCY))),
                                    0L,0L
                            );
                            if (!cursor.isNull(cursor.getColumnIndex(Contract.Debt.PLACE_ID))) {
                                place = new Place(
                                        cursor.getLong(cursor.getColumnIndex(Contract.Debt.PLACE_ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Debt.PLACE_NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Debt.PLACE_ICON))),
                                        cursor.getString(cursor.getColumnIndex(Contract.Debt.PLACE_ADDRESS)),
                                        cursor.isNull(cursor.getColumnIndex(Contract.Debt.PLACE_LATITUDE)) ? null : cursor.getDouble(cursor.getColumnIndex(Contract.Debt.PLACE_LATITUDE)),
                                        cursor.isNull(cursor.getColumnIndex(Contract.Debt.PLACE_LONGITUDE)) ? null : cursor.getDouble(cursor.getColumnIndex(Contract.Debt.PLACE_LONGITUDE))
                                );
                            }
                        }
                        cursor.close();
                    }
                    // the previous cursor contains only a column with the list of ids of linked people.
                    // we need instead to buildMaterialDialog the full person object so we must perform a separated
                    // query to the database to obtain the full cursor.
                    uri = Uri.withAppendedPath(uri, "people");
                    projection = new String[] {
                            Contract.Person.ID,
                            Contract.Person.NAME,
                            Contract.Person.ICON
                    };
                    cursor = contentResolver.query(uri, projection, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            people = new Person[cursor.getCount()];
                            for (int i = 0; cursor.moveToPosition(i) && i < cursor.getCount(); i++) {
                                people[i] = new Person(
                                        cursor.getLong(cursor.getColumnIndex(Contract.Person.ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Person.NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Person.ICON)))
                                );
                            }
                        }
                        cursor.close();
                    }
                    // load the category associated with this debt
                    uri = DataContentProvider.CONTENT_CATEGORIES;
                    projection = new String[] {
                            Contract.Category.ID,
                            Contract.Category.NAME,
                            Contract.Category.ICON,
                            Contract.Category.TYPE,
                            Contract.Category.TAG
                    };
                    String where = Contract.Category.TAG + " = ?";
                    if (debtType == null) {
                        debtType = TransactionEditorRules.debtTypeFor(intent.getIntExtra(DEBT_ACTION, 0));
                    }
                    String debtTag = TransactionEditorRules.debtCategoryTag(debtType);
                    if (debtTag != null) {
                        String[] whereArgs = new String[] {debtTag};
                        cursor = contentResolver.query(uri, projection, where, whereArgs, null);
                        if (cursor != null) {
                            if (cursor.moveToFirst()) {
                                category = new Category(
                                        cursor.getLong(cursor.getColumnIndex(Contract.Category.ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Category.ICON))),
                                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE))),
                                        cursor.getString(cursor.getColumnIndex(Contract.Category.TAG))
                                );
                            }
                            cursor.close();
                        }
                    }
                } else if (mRules.getType() == TYPE_SAVING) {
                    mRules.setSavingId(intent.getLongExtra(SAVING_ID, 0L));
                    long startMoney = 0L;
                    // getItemId is the id of the transaction being edited, and this branch only
                    // runs when there is no transaction yet, so it was always the -1 assigned for
                    // a new item. That built the uri savings/-1, which matches no route in the
                    // content provider, so the query returned null without reaching the database,
                    // wallet stayed null, and the amount keypad opened with no currency to scale
                    // against: it read the typed digits as minor units and 2000 became 20.00.
                    // Load the saving the intent names, the way the debt branch above loads its
                    // debt.
                    Uri savingUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, mRules.getSavingId());
                    String[] projection = new String[] {
                            Contract.Saving.START_MONEY,
                            Contract.Saving.WALLET_ID,
                            Contract.Saving.WALLET_NAME,
                            Contract.Saving.WALLET_ICON,
                            Contract.Saving.WALLET_CURRENCY
                    };
                    Cursor cursor = contentResolver.query(savingUri, projection, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            startMoney = cursor.getLong(cursor.getColumnIndex(Contract.Saving.START_MONEY));
                            wallet = new Wallet(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Saving.WALLET_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_ICON))),
                                    CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_CURRENCY))),
                                    0L, 0L
                            );
                        }
                        cursor.close();
                    }
                    Uri uri = DataContentProvider.CONTENT_CATEGORIES;
                    projection = new String[] {
                            Contract.Category.ID,
                            Contract.Category.NAME,
                            Contract.Category.ICON,
                            Contract.Category.TYPE,
                            Contract.Category.TAG
                    };
                    String selection = Contract.Category.TAG + " = ?";
                    int action = intent.getIntExtra(SAVING_ACTION, 0);
                    // A saving row is filed under a category this screen picks, and an action it
                    // does not know names none. There is no editor to draw: the category field is
                    // hidden on a saving, so an empty picker cannot be filled in and its own
                    // validator would refuse every save against a view the user cannot see. Close
                    // instead. No launch inside the app reaches this, since every one of
                    // SavingListFragment's three sets an action, and this activity is exported.
                    if (TransactionEditorRules.savingCategoryTag(action) == null) {
                        finish();
                        return;
                    }
                    if (TransactionEditorRules.completesTheSaving(action)) {
                        // The row this writes opens dated now, so what it can actually take is
                        // the lowest the saving reaches from now onwards, which is the same
                        // figure the check applies when the save is pressed. Prefilling anything
                        // above it offers an amount this same screen then refuses.
                        Long lowest = readLowestSavingBalanceFrom(contentResolver, savingUri,
                                startMoney, DateUtils.getSQLDateTimeString(new Date()), -1L);
                        money = TransactionEditorRules.withdrawEverythingPrefill(lowest);
                        mRules.setSavingCompleted(true);
                    }
                    String[] selectionArgs = new String[] {
                            TransactionEditorRules.savingCategoryTag(action)
                    };
                    cursor = contentResolver.query(uri, projection, selection, selectionArgs, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            category = new Category(
                                    cursor.getLong(cursor.getColumnIndex(Contract.Category.ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.Category.NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.Category.ICON))),
                                    Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE))),
                                    cursor.getString(cursor.getColumnIndex(Contract.Category.TAG))
                            );
                        }
                        cursor.close();
                    }
                } else if (mRules.getType() == TYPE_MODEL) {
                    long modelId = intent.getLongExtra(MODEL_ID, 0L);
                    Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, modelId);
                    String[] projection = new String[] {
                            Contract.TransactionModel.MONEY,
                            Contract.TransactionModel.DESCRIPTION,
                            Contract.TransactionModel.CATEGORY_ID,
                            Contract.TransactionModel.CATEGORY_NAME,
                            Contract.TransactionModel.CATEGORY_ICON,
                            Contract.TransactionModel.CATEGORY_TYPE,
                            Contract.TransactionModel.CATEGORY_SHOW_REPORT,
                            Contract.TransactionModel.DIRECTION,
                            Contract.TransactionModel.WALLET_ID,
                            Contract.TransactionModel.WALLET_NAME,
                            Contract.TransactionModel.WALLET_ICON,
                            Contract.TransactionModel.WALLET_CURRENCY,
                            Contract.TransactionModel.PLACE_ID,
                            Contract.TransactionModel.PLACE_NAME,
                            Contract.TransactionModel.PLACE_ICON,
                            Contract.TransactionModel.PLACE_ADDRESS,
                            Contract.TransactionModel.PLACE_LATITUDE,
                            Contract.TransactionModel.PLACE_LONGITUDE,
                            Contract.TransactionModel.NOTE,
                            Contract.TransactionModel.EVENT_ID,
                            Contract.TransactionModel.EVENT_NAME,
                            Contract.TransactionModel.EVENT_ICON,
                            Contract.TransactionModel.EVENT_START_DATE,
                            Contract.TransactionModel.EVENT_END_DATE,
                            Contract.TransactionModel.CONFIRMED,
                            Contract.TransactionModel.COUNT_IN_TOTAL
                    };
                    Cursor cursor = contentResolver.query(uri, projection, null, null, null);
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            money = cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.MONEY));
                            mDescriptionEditText.setText(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.DESCRIPTION)));
                            category = new Category(
                                    cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ICON))),
                                    Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_TYPE)))
                            );
                            wallet = new Wallet(
                                    cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ID)),
                                    cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.WALLET_NAME)),
                                    IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ICON))),
                                    CurrencyManager.getCurrency(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.WALLET_CURRENCY))),
                                    0L, 0L
                            );
                            if (!cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID))) {
                                place = new Place(
                                        cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.PLACE_NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ICON))),
                                        cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ADDRESS)),
                                        cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LATITUDE)) ? null : cursor.getDouble(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LATITUDE)),
                                        cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LONGITUDE)) ? null : cursor.getDouble(cursor.getColumnIndex(Contract.TransactionModel.PLACE_LONGITUDE))
                                );
                            }
                            mNoteEditText.setText(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.NOTE)));
                            if (!cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID))) {
                                event = new Event(
                                        cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID)),
                                        cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_NAME)),
                                        IconLoader.parse(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ICON))),
                                        DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_START_DATE))),
                                        DateUtils.getDateFromSQLDateString(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.EVENT_END_DATE)))
                                );
                            }
                            mConfirmedCheckBox.setChecked(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CONFIRMED)) == 1);
                            mCountInTotalCheckBox.setChecked(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.COUNT_IN_TOTAL)) == 1);
                        }
                        cursor.close();
                    }
                }
                datetime = new Date();
            }
        } else {
            TransactionEditorRules restored =
                    (TransactionEditorRules) savedInstanceState.getSerializable(SS_RULES);
            if (restored != null) {
                mRules = restored;
            }
        }
        if (savedInstanceState == null && category != null) {
            mRules.setDebtPayment(TransactionEditorRules.isDebtPaymentTag(category.getTag()));
        }
        // depending on the type we must hide pickers that are now allowed to be changed
        if (mRules.hidesCategoryField()) {
            mCategoryEditText.setVisibility(View.GONE);
        }
        if (mRules.hidesWalletField()) {
            mWalletEditText.setVisibility(View.GONE);
        }
        // now we can create pickers with default values or existing item parameters
        // and update all the views according to the data
        FragmentManager fragmentManager = getSupportFragmentManager();
        mMoneyPicker = MoneyPicker.createPicker(fragmentManager, TAG_MONEY_PICKER, null, money);
        mCategoryPicker = CategoryPicker.createPicker(fragmentManager, TAG_CATEGORY_PICKER, category);
        mDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_DATETIME_PICKER, datetime);
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, wallet);
        mEventPicker = EventPicker.createPicker(fragmentManager, TAG_EVENT_PICKER, event);
        mPersonPicker = PersonPicker.createPicker(fragmentManager, TAG_PERSON_PICKER, people);
        mPlacePicker = PlacePicker.createPicker(fragmentManager, TAG_PLACE_PICKER, place);
        mAttachmentPicker = AttachmentPicker.createPicker(fragmentManager, TAG_ATTACHMENT_PICKER, attachments);
        // here and not with the other listeners, because this one reaches the category picker and
        // the pickers are built above. A focus listener and not a text watcher, so the lookup runs
        // once the user has finished typing instead of once per character
        mDescriptionEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {

            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    fillCategoryFromRules();
                }
            }

        });
        // check if the intent contains some predefined value for fields
        if (savedInstanceState == null) {
            fillFieldsFromIntent(getIntent());
        }
    }

    /**
     * Whether this id names a transaction that stands on its own, which is the only kind that is
     * copied. Asked before the load above rather than read out of it, because that load reaches
     * for a leg's transfer with the id of the item being edited, which a new item does not have,
     * so it would find nothing, leave the type saying transfer, and carry on filling the editor
     * in from a half of something.
     */
    private static boolean isPlainTransaction(ContentResolver contentResolver, long transactionId) {
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, transactionId);
        Cursor cursor = contentResolver.query(uri, new String[] {Contract.Transaction.TYPE}, null, null, null);
        boolean plain = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                plain = cursor.getInt(cursor.getColumnIndex(Contract.Transaction.TYPE)) == TYPE_STANDARD;
            }
            cursor.close();
        }
        return plain;
    }

    /**
     * Sets the category the rules name for what the description holds. A category the user picked
     * is left alone; one an earlier run of this put there is reconsidered, so correcting the
     * description corrects the category with it, and a correction that matches no rule empties the
     * field instead of saving the row under a category the description no longer names. The pick
     * stays editable, since it is made through the picker and repaints the field the same way a
     * pick of the user's own does.
     *
     * The field has to be on screen for this to run. A debt row and a saving row are both filed
     * under a category this editor chooses and hides, and one written there could be neither seen
     * nor changed. An existing transaction is left alone, and so is a new one that already
     * carries a category, which covers both a transaction started from a model and a copy of one.
     */
    private void fillCategoryFromRules() {
        if (getMode() != Mode.NEW_ITEM || mRules.hidesCategoryField()) {
            return;
        }
        if (mCategoryPicker.isSelected() && !mCategoryFilledByRule) {
            return;
        }
        String description = mDescriptionEditText.getTextAsString();
        Category category = TextUtils.isEmpty(description) ? null
                : readCategoryNamedBy(getContentResolver(), description);
        if (category == null && !mCategoryFilledByRule) {
            return;
        }
        // setCategory reaches onCategoryChanged on this thread before it returns, and that clears
        // the flag, so the flag is set after the call and not before it
        mCategoryPicker.setCategory(category);
        mCategoryFilledByRule = category != null;
    }

    /**
     * The category the stored rules file this description under, or null when none of them does.
     * The description travels as one path segment, which is what carries a slash or a space in it
     * through to the lookup whole.
     *
     * Read on the calling thread, which is how every other read in this editor is made.
     *
     * @param contentResolver resolver to read through.
     * @param description text the user typed on the transaction.
     * @return the category, or null when no rule matches or its category is gone.
     */
    private static Category readCategoryNamedBy(ContentResolver contentResolver, String description) {
        Uri matchUri = DataContentProvider.CONTENT_CATEGORY_RULES.buildUpon()
                .appendPath("match")
                .appendPath(description)
                .build();
        Long categoryId = null;
        Cursor cursor = contentResolver.query(matchUri,
                new String[] {Contract.CategoryRule.CATEGORY_ID}, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                categoryId = cursor.getLong(cursor.getColumnIndexOrThrow(Contract.CategoryRule.CATEGORY_ID));
            }
            cursor.close();
        }
        if (categoryId == null) {
            return null;
        }
        Uri categoryUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_CATEGORIES, categoryId);
        String[] projection = new String[] {
                Contract.Category.ID,
                Contract.Category.NAME,
                Contract.Category.ICON,
                Contract.Category.TYPE,
                Contract.Category.TAG
        };
        Category category = null;
        cursor = contentResolver.query(categoryUri, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                category = new Category(
                        cursor.getLong(cursor.getColumnIndexOrThrow(Contract.Category.ID)),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.NAME)),
                        IconLoader.parse(cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.ICON))),
                        Contract.CategoryType.fromValue(cursor.getInt(cursor.getColumnIndexOrThrow(Contract.Category.TYPE))),
                        cursor.getString(cursor.getColumnIndexOrThrow(Contract.Category.TAG))
                );
            }
            cursor.close();
        }
        return category;
    }

    private void fillFieldsFromIntent(Intent intent) {
        // check if the activity has been started from outside the application and some
        // other app has provided an extra stream of uris to set as attachments
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            Uri fileUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (fileUri != null) {
                mAttachmentPicker.addFileFromUri(fileUri);
            }
        }
        if (Intent.ACTION_SEND_MULTIPLE.equals(intent.getAction())) {
            ArrayList<Uri> fileUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (fileUris != null) {
                for (Uri uri : fileUris) {
                    if (uri != null) {
                        mAttachmentPicker.addFileFromUri(uri);
                    }
                }
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(SS_RULES, mRules);
    }

    @Override
    protected int getActivityTileRes(Mode mode) {
        switch (mode) {
            case NEW_ITEM:
                return R.string.title_activity_new_transaction;
            case EDIT_ITEM:
                return R.string.title_activity_edit_transaction;
            default:
                return -1;
        }
    }

    @Override
    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_new_edit_item_with_attachment;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_attach_file) {
            mAttachmentPicker.showPicker();
            return false;
        } else {
            return super.onMenuItemClick(item);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        mAttachmentPicker.cleanUp(true);
    }

    private boolean validate() {
        if (mDateEditText.validate() && mCategoryEditText.validate() && mWalletEditText.validate()) {
            if (mAttachmentPicker.areAllAttachmentsReady()) {
                return validateSavingWithdraw();
            } else {
                // TODO show error: wait for attachment load competition
            }
        }
        return false;
    }

    /**
     * The lowest a saving's balance reaches from a moment onwards, over the rows it already
     * carries, or null when those rows do not come back at all.
     *
     * The rows are held to the two saving categories, so this reads the same rows the saving's
     * own sums count. Which of them are counted, how each is signed and what order they are
     * walked in is
     * {@link TransactionEditorRules#lowestSavingBalanceFrom(long, List, long, String)}.
     */
    private Long readLowestSavingBalanceFrom(ContentResolver contentResolver, Uri savingUri,
                                             long startMoney, String from, long excludedId) {
        String[] projection = new String[] {
                Contract.Transaction.ID,
                Contract.Transaction.DATE,
                Contract.Transaction.MONEY,
                Contract.Transaction.DIRECTION,
                Contract.Transaction.CONFIRMED
        };
        String selection = Contract.Transaction.CATEGORY_TAG + " IN (?, ?)";
        String[] selectionArgs = new String[] {
                Contract.CategoryTag.SAVING_DEPOSIT,
                Contract.CategoryTag.SAVING_WITHDRAW
        };
        Cursor cursor = contentResolver.query(Uri.withAppendedPath(savingUri, "transactions"),
                projection, selection, selectionArgs, Contract.Transaction.DATE + " ASC");
        if (cursor == null) {
            return null;
        }
        List<TransactionEditorRules.SavingRow> rows = new ArrayList<>();
        while (cursor.moveToNext()) {
            rows.add(new TransactionEditorRules.SavingRow(
                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.ID)),
                    cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE)),
                    cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY)),
                    cursor.getInt(cursor.getColumnIndex(Contract.Transaction.DIRECTION)),
                    cursor.getInt(cursor.getColumnIndex(Contract.Transaction.CONFIRMED)) == 1
            ));
        }
        cursor.close();
        return TransactionEditorRules.lowestSavingBalanceFrom(startMoney, rows, excludedId, from);
    }

    /**
     * Reads the stored row this edit is replacing and asks
     * {@link TransactionEditorRules#isStoredWithdrawalKeptOrLowered(long, String, long, String)}
     * whether it is being kept or lowered.
     *
     * A new row answers false, since there is no stored row to compare with.
     */
    private boolean isStoredWithdrawalKeptOrLowered(ContentResolver contentResolver, long money,
                                                    String date) {
        if (getMode() != Mode.EDIT_ITEM) {
            return false;
        }
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, getItemId());
        String[] projection = new String[] {
                Contract.Transaction.MONEY,
                Contract.Transaction.DATE
        };
        Cursor cursor = contentResolver.query(uri, projection, null, null, null);
        boolean unchangedOrSmaller = false;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                unchangedOrSmaller = TransactionEditorRules.isStoredWithdrawalKeptOrLowered(
                        money, date,
                        cursor.getLong(cursor.getColumnIndex(Contract.Transaction.MONEY)),
                        cursor.getString(cursor.getColumnIndex(Contract.Transaction.DATE)));
            }
            cursor.close();
        }
        return unchangedOrSmaller;
    }

    /**
     * A withdrawal must not take the saving under zero on its own date, nor on any date after
     * it. What it may take is therefore the lowest the balance reaches from its date onwards,
     * counted over the saving's rows in date order.
     *
     * That replaces the two totals this used to hold, what the saving holds today and the lowest
     * it can end up at once everything already on it has happened. A total carries no date order
     * and neither of them said anything about the dates in between, so a withdrawal dated before
     * the deposit that funds it cleared both and left the saving under zero for the days between
     * the two.
     *
     * The figures are read here and not when the editor opened, because the answer depends on
     * the date on screen and that date is still being chosen while the editor is up. It is still
     * only a check on the way in. Whatever pays for a withdrawal can be lowered, deleted,
     * unconfirmed or dated later afterwards, or from another screen while this one waits, with
     * nothing refused, and the saving's own start money is editable on its own screen.
     *
     * The check runs on a withdrawal alone, so a deposit and an ordinary transaction are
     * unaffected. It steps aside for a wallet on screen whose currency the saving's figures
     * cannot be compared with, for a saving whose own currency the app cannot resolve, which a
     * restored backup can produce, and for a saving whose rows do not come back.
     *
     * The Confirmed box does not enter into it. A withdrawal is counted against the saving
     * whether it is ticked or not, because leaving an unconfirmed one out would hand out a
     * ceiling it can then take the saving under.
     */
    private boolean validateSavingWithdraw() {
        if (mRules.getSavingId() == null) {
            return true;
        }
        Category category = mCategoryPicker.getCurrentCategory();
        if (category == null || !Contract.CategoryTag.SAVING_WITHDRAW.equals(category.getTag())) {
            return true;
        }
        ContentResolver contentResolver = getContentResolver();
        Uri savingUri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, mRules.getSavingId());
        String[] projection = new String[] {
                Contract.Saving.START_MONEY,
                Contract.Saving.WALLET_CURRENCY
        };
        long startMoney = 0L;
        String currencyIso = null;
        Cursor cursor = contentResolver.query(savingUri, projection, null, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                startMoney = cursor.getLong(cursor.getColumnIndex(Contract.Saving.START_MONEY));
                currencyIso = cursor.getString(cursor.getColumnIndex(Contract.Saving.WALLET_CURRENCY));
            }
            cursor.close();
        }
        CurrencyUnit currency = currencyIso != null ? CurrencyManager.getCurrency(currencyIso) : null;
        CurrencyUnit walletCurrency = mWalletPicker.getCurrentWallet().getCurrency();
        if (!TransactionEditorRules.ceilingApplies(currency != null ? currency.getIso() : null,
                walletCurrency != null ? walletCurrency.getIso() : null)) {
            return true;
        }
        long money = mMoneyPicker.getCurrentMoney();
        String date = DateUtils.getSQLDateTimeString(mDateTimePicker.getCurrentDateTime());
        if (isStoredWithdrawalKeptOrLowered(contentResolver, money, date)) {
            return true;
        }
        Long lowest = readLowestSavingBalanceFrom(contentResolver, savingUri, startMoney, date,
                getItemId());
        if (lowest == null) {
            return true;
        }
        // An amount of nothing is never refused, and that matters. Refusing it would refuse every
        // save of a stored withdrawal of nothing, whatever the saving holds, so its own note and
        // date could never be corrected and only deletion would be open. The old withdraw
        // everything path could write such a row.
        long limit = TransactionEditorRules.withdrawLimit(lowest);
        if (TransactionEditorRules.isWithinLimit(money, limit)) {
            return true;
        }
        // The figure every time, with no separate wording for a ceiling of nothing. What a
        // saving holds today and what it has left to give are not the same number once a
        // withdraw is sitting on it unconfirmed or dated ahead, and the wording this replaced
        // said the saving was empty, which is false while the savings list still shows a
        // balance. A ceiling of 0.00 read beside that balance is still a surprise; it is at
        // least true, and it names the number the save is being held to.
        String message = getString(R.string.error_saving_withdraw_over_balance,
                mMoneyFormatter.getNotTintedString(currency, limit));
        ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_error)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
        return false;
    }

    @Override
    protected void onSaveChanges(Mode mode) {
        // the description field may never have lost focus, so the save is the second place the
        // rules are read. The guard inside leaves a category the user picked alone
        fillCategoryFromRules();
        if (validate()) {
            ContentValues contentValues = new TransactionContentValuesBuilder()
                    .money(mMoneyPicker.getCurrentMoney())
                    .date(DateUtils.getSQLDateTimeString(mDateTimePicker.getCurrentDateTime()))
                    .description(mDescriptionEditText.getTextAsString())
                    .categoryId(mCategoryPicker.getCurrentCategory().getId())
                    .direction(mCategoryPicker.getCurrentCategory().getDirection())
                    .type(mRules.getType())
                    .walletId(mWalletPicker.getCurrentWallet().getId())
                    .placeId(mPlacePicker.isSelected() ? mPlacePicker.getCurrentPlace().getId() : null)
                    .note(mNoteEditText.getTextAsString())
                    .eventId(mEventPicker.isSelected() ? mEventPicker.getCurrentEvent().getId() : null)
                    .savingId(mRules.getSavingId())
                    .debtId(mRules.getDebtId())
                    .confirmed(mConfirmedCheckBox.isChecked())
                    .countInTotal(mCountInTotalCheckBox.isChecked())
                    .peopleIds(Contract.getObjectIds(mPersonPicker.getCurrentPeople()))
                    .attachmentIds(Contract.getObjectIds(mAttachmentPicker.getCurrentAttachments()))
                    .build();
            ContentResolver contentResolver = getContentResolver();
            try {
                switch (mode) {
                    case NEW_ITEM:
                        contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues);
                        if (mRules.getSavingId() != null && mRules.isSavingCompleted()) {
                            setSavingCompleted(contentResolver, mRules.getSavingId());
                        }
                        break;
                    case EDIT_ITEM:
                        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTIONS, getItemId());
                        contentResolver.update(uri, contentValues, null, null);
                        break;
                }
            } catch (SQLiteDataException e) {
                if (e.getErrorCode() != Contract.ErrorCode.WALLETS_NOT_CONSISTENT) {
                    throw e;
                }
                ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_error)
                        .setMessage(R.string.error_debt_wallet_currency_not_consistent)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                return;
            }
            mAttachmentPicker.cleanUp(false);
            setResult(RESULT_OK);
            finishSaveDatedAt(mDateTimePicker.getCurrentDateTime());
        }
    }

    private void setSavingCompleted(ContentResolver contentResolver, long savingId) {
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Saving.COMPLETE, true);
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_SAVINGS, savingId);
        contentResolver.update(uri, contentValues, null, null);
    }

    public static Uri insertTransactionFromModel(Context context, long modelId) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(DataContentProvider.CONTENT_TRANSACTION_MODELS, modelId);
        String[] projection = new String[] {
                Contract.TransactionModel.MONEY,
                Contract.TransactionModel.DESCRIPTION,
                Contract.TransactionModel.CATEGORY_ID,
                Contract.TransactionModel.DIRECTION,
                Contract.TransactionModel.WALLET_ID,
                Contract.TransactionModel.PLACE_ID,
                Contract.TransactionModel.NOTE,
                Contract.TransactionModel.EVENT_ID,
                Contract.TransactionModel.CONFIRMED,
                Contract.TransactionModel.COUNT_IN_TOTAL
        };
        Cursor cursor = contentResolver.query(uri, projection, null, null, null);
        if (cursor != null) {
            Uri resultUri = null;
            if (cursor.moveToFirst()) {
                ContentValues contentValues = new TransactionContentValuesBuilder()
                        .money(cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.MONEY)))
                        .date(DateUtils.getSQLDateTimeString(new Date()))
                        .description(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.DESCRIPTION)))
                        .categoryId(cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.CATEGORY_ID)))
                        .direction(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.DIRECTION)))
                        .type(Contract.TransactionType.STANDARD)
                        .walletId(cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.WALLET_ID)))
                        .placeId(cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID)) ? null : cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.PLACE_ID)))
                        .note(cursor.getString(cursor.getColumnIndex(Contract.TransactionModel.NOTE)))
                        .eventId(cursor.isNull(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID)) ? null : cursor.getLong(cursor.getColumnIndex(Contract.TransactionModel.EVENT_ID)))
                        .confirmed(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.CONFIRMED)))
                        .countInTotal(cursor.getInt(cursor.getColumnIndex(Contract.TransactionModel.COUNT_IN_TOTAL)))
                        .build();
                resultUri = contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues);
            }
            cursor.close();
            return resultUri;
        }
        return null;
    }

    @Override
    public void onMoneyChanged(String tag, CurrencyUnit currency, long money) {
        if (currency != null) {
            mCurrencyTextView.setText(currency.getSymbol());
        } else {
            mCurrencyTextView.setText("?");
        }
        mMoneyTextView.setText(mMoneyFormatter.getNotTintedString(currency, money, MoneyFormatter.CurrencyMode.ALWAYS_HIDDEN));
    }

    @Override
    public void onCategoryChanged(String tag, Category category) {
        // every pick reaches here, the user's through the picker and the rules' through
        // setCategory, so the flag is cleared here and set again only by the rule fill itself
        mCategoryFilledByRule = false;
        if (category != null) {
            mCategoryEditText.setText(category.getName());
        } else {
            mCategoryEditText.setText(null);
        }
    }

    @Override
    public void onDateTimeChanged(String tag, Date date) {
        if (date != null) {
            DateFormatter.applyDate(mDateEditText, date);
            DateFormatter.applyTime(mTimeEditText, date);
        } else {
            mDateEditText.setText(null);
            mTimeEditText.setText(null);
        }
    }

    @Override
    public void onWalletChanged(String tag, Wallet wallet) {
        if (wallet != null) {
            mWalletEditText.setText(wallet.getName());
            mMoneyPicker.setCurrency(wallet.getCurrency());
        } else {
            mWalletEditText.setText(null);
            mMoneyPicker.setCurrency(null);
        }
    }

    @Override
    public void onEventChanged(String tag, Event event) {
        if (event != null) {
            mEventEditText.setText(event.getName());
        } else {
            mEventEditText.setText(null);
        }
    }

    @Override
    public void onPeopleChanged(String tag, Person[] people) {
        if (people != null) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < people.length; i++) {
                if (i != 0) {
                    builder.append(", ");
                }
                builder.append(people[i].getName());
            }
            mPeopleEditText.setText(builder);
        } else {
            mPeopleEditText.setText(null);
        }
    }

    @Override
    public void onPlaceChanged(String tag, Place place) {
        if (place != null) {
            mPlaceEditText.setText(place.getName());
        } else {
            mPlaceEditText.setText(null);
        }
    }

    @Override
    public void onAttachmentListChanged(List<Attachment> attachments) {
        mAttachmentView.setVisibility(attachments.isEmpty() ? View.GONE : View.VISIBLE);
        mAttachmentView.setAttachments(attachments);
    }

    @Override
    public void onAttachmentClick(Attachment attachment) {
        Attachment.openAttachment(this, attachment);
    }

    @Override
    public void onAttachmentDelete(Attachment attachment) {
        mAttachmentPicker.remove(attachment);
    }
}