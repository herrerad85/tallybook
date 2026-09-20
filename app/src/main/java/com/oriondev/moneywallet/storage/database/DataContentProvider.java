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

package com.oriondev.moneywallet.storage.database;

import android.annotation.SuppressLint;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.BuildConfig;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.widget.WalletWidgetProvider;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Created by andrea on 17/01/18.
 */
public class DataContentProvider extends ContentProvider {

    /*package-local*/ static final String AUTHORITY = BuildConfig.APPLICATION_ID + ".storage.data";

    public static final Uri CONTENT_CURRENCIES = Uri.parse("content://" + AUTHORITY + "/currencies");
    public static final Uri CONTENT_WALLETS = Uri.parse("content://" + AUTHORITY + "/wallets");
    public static final Uri CONTENT_TRANSACTIONS = Uri.parse("content://" + AUTHORITY + "/transactions");
    public static final Uri CONTENT_TRANSFERS = Uri.parse("content://" + AUTHORITY + "/transfers");
    public static final Uri CONTENT_CATEGORIES = Uri.parse("content://" + AUTHORITY + "/categories");
    public static final Uri CONTENT_DEBTS = Uri.parse("content://" + AUTHORITY + "/debts");
    public static final Uri CONTENT_BUDGETS = Uri.parse("content://" + AUTHORITY + "/budgets");
    public static final Uri CONTENT_SAVINGS = Uri.parse("content://" + AUTHORITY + "/savings");
    public static final Uri CONTENT_EVENTS = Uri.parse("content://" + AUTHORITY + "/events");
    public static final Uri CONTENT_RECURRENT_TRANSACTIONS = Uri.parse("content://" + AUTHORITY + "/recurrences/transactions");
    public static final Uri CONTENT_RECURRENT_TRANSFERS = Uri.parse("content://" + AUTHORITY + "/recurrences/transfers");
    public static final Uri CONTENT_TRANSACTION_MODELS = Uri.parse("content://" + AUTHORITY + "/models/transactions");
    public static final Uri CONTENT_TRANSFER_MODELS = Uri.parse("content://" + AUTHORITY + "/models/transfers");
    public static final Uri CONTENT_PLACES = Uri.parse("content://" + AUTHORITY + "/places");
    public static final Uri CONTENT_PEOPLE = Uri.parse("content://" + AUTHORITY + "/people");
    public static final Uri CONTENT_CATEGORY_RULES = Uri.parse("content://" + AUTHORITY + "/category_rules");
    public static final Uri CONTENT_ATTACHMENTS = Uri.parse("content://" + AUTHORITY + "/attachments");

    /**
     * Every uri above sits under this one. An observer registered here with descendants included
     * is told about every change this provider announces, whichever of those uris the write named,
     * because the framework collects an observer sitting above a uri for a notification on
     * anything below it.
     *
     * It is what a cursor this provider hands out is registered on, and what the widget watches.
     */
    public static final Uri CONTENT_ALL = Uri.parse("content://" + AUTHORITY);

    private static final int CURRENCY_LIST = 1;
    private static final int WALLET_LIST = 2;
    private static final int TRANSACTION_LIST = 3;
    private static final int TRANSFER_LIST = 4;
    private static final int CATEGORY_LIST = 5;
    private static final int DEBT_LIST = 6;
    private static final int BUDGET_LIST = 7;
    private static final int SAVING_LIST = 8;
    private static final int EVENT_LIST = 9;
    private static final int RECURRENT_TRANSACTION_LIST = 10;
    private static final int RECURRENT_TRANSFER_LIST = 11;
    private static final int TRANSACTION_MODEL_LIST = 12;
    private static final int TRANSFER_MODEL_LIST = 13;
    private static final int PLACE_LIST = 14;
    private static final int PERSON_LIST = 15;
    private static final int ATTACHMENT_LIST = 16;
    private static final int ATTACHMENT_ITEM = 17;

    private static final int CURRENCY_ITEM = 18;
    private static final int WALLET_ITEM = 19;
    private static final int TRANSACTION_ITEM = 20;
    private static final int TRANSFER_ITEM = 21;
    private static final int CATEGORY_ITEM = 22;
    private static final int DEBT_ITEM = 23;
    private static final int BUDGET_ITEM = 24;
    private static final int SAVING_ITEM = 25;
    private static final int EVENT_ITEM = 26;
    private static final int RECURRENT_TRANSACTION_ITEM = 27;
    private static final int RECURRENT_TRANSFER_ITEM = 28;
    private static final int TRANSACTION_MODEL_ITEM = 29;
    private static final int TRANSFER_MODEL_ITEM = 30;
    private static final int PLACE_ITEM = 31;
    private static final int PERSON_ITEM = 32;

    private static final int TRANSACTION_ATTACHMENTS = 33;
    private static final int TRANSACTION_PEOPLE = 34;
    private static final int TRANSFER_ATTACHMENTS = 35;
    private static final int TRANSFER_PEOPLE = 36;
    private static final int DEBT_PEOPLE = 37;
    private static final int BUDGET_WALLETS = 38;
    private static final int BUDGET_CATEGORIES = 46;

    private static final int CATEGORY_TRANSACTION_LIST = 39;
    private static final int DEBT_TRANSACTION_LIST = 40;
    private static final int BUDGET_TRANSACTION_LIST = 41;
    private static final int SAVING_TRANSACTION_LIST = 42;
    private static final int EVENT_TRANSACTION_LIST = 43;
    private static final int PLACE_TRANSACTION_LIST = 44;
    private static final int PERSON_TRANSACTION_LIST = 45;

    private static final int CATEGORY_RULE_LIST = 47;
    private static final int CATEGORY_RULE_ITEM = 48;
    private static final int CATEGORY_RULE_MATCH = 49;

    private static final UriMatcher mUriMatcher = createUriMatcher();

    private static UriMatcher createUriMatcher() {
        UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);
        matcher.addURI(AUTHORITY, "currencies", CURRENCY_LIST);
        matcher.addURI(AUTHORITY, "currencies/*", CURRENCY_ITEM);
        matcher.addURI(AUTHORITY, "wallets", WALLET_LIST);
        matcher.addURI(AUTHORITY, "wallets/#", WALLET_ITEM);
        matcher.addURI(AUTHORITY, "transactions", TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "transactions/#", TRANSACTION_ITEM);
        matcher.addURI(AUTHORITY, "transactions/#/attachments", TRANSACTION_ATTACHMENTS);
        matcher.addURI(AUTHORITY, "transactions/#/people", TRANSACTION_PEOPLE);
        matcher.addURI(AUTHORITY, "transfers", TRANSFER_LIST);
        matcher.addURI(AUTHORITY, "transfers/#", TRANSFER_ITEM);
        matcher.addURI(AUTHORITY, "transfers/#/attachments", TRANSFER_ATTACHMENTS);
        matcher.addURI(AUTHORITY, "transfers/#/people", TRANSFER_PEOPLE);
        matcher.addURI(AUTHORITY, "categories", CATEGORY_LIST);
        matcher.addURI(AUTHORITY, "categories/#", CATEGORY_ITEM);
        matcher.addURI(AUTHORITY, "categories/#/transactions", CATEGORY_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "debts", DEBT_LIST);
        matcher.addURI(AUTHORITY, "debts/#", DEBT_ITEM);
        matcher.addURI(AUTHORITY, "debts/#/people", DEBT_PEOPLE);
        matcher.addURI(AUTHORITY, "debts/#/transactions", DEBT_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "budgets", BUDGET_LIST);
        matcher.addURI(AUTHORITY, "budgets/#", BUDGET_ITEM);
        matcher.addURI(AUTHORITY, "budgets/#/wallets", BUDGET_WALLETS);
        matcher.addURI(AUTHORITY, "budgets/#/categories", BUDGET_CATEGORIES);
        matcher.addURI(AUTHORITY, "budgets/#/transactions", BUDGET_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "savings", SAVING_LIST);
        matcher.addURI(AUTHORITY, "savings/#", SAVING_ITEM);
        matcher.addURI(AUTHORITY, "savings/#/transactions", SAVING_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "events", EVENT_LIST);
        matcher.addURI(AUTHORITY, "events/#", EVENT_ITEM);
        matcher.addURI(AUTHORITY, "events/#/transactions", EVENT_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "recurrences/transactions", RECURRENT_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "recurrences/transactions/#", RECURRENT_TRANSACTION_ITEM);
        matcher.addURI(AUTHORITY, "recurrences/transfers", RECURRENT_TRANSFER_LIST);
        matcher.addURI(AUTHORITY, "recurrences/transfers/#", RECURRENT_TRANSFER_ITEM);
        matcher.addURI(AUTHORITY, "models/transactions", TRANSACTION_MODEL_LIST);
        matcher.addURI(AUTHORITY, "models/transactions/#", TRANSACTION_MODEL_ITEM);
        matcher.addURI(AUTHORITY, "models/transfers", TRANSFER_MODEL_LIST);
        matcher.addURI(AUTHORITY, "models/transfers/#", TRANSFER_MODEL_ITEM);
        matcher.addURI(AUTHORITY, "category_rules", CATEGORY_RULE_LIST);
        matcher.addURI(AUTHORITY, "category_rules/#", CATEGORY_RULE_ITEM);
        // the description is one encoded path segment, so a slash or a space typed into it
        // travels whole and comes back out of getLastPathSegment decoded
        matcher.addURI(AUTHORITY, "category_rules/match/*", CATEGORY_RULE_MATCH);
        matcher.addURI(AUTHORITY, "places", PLACE_LIST);
        matcher.addURI(AUTHORITY, "places/#", PLACE_ITEM);
        matcher.addURI(AUTHORITY, "places/#/transactions", PLACE_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "people", PERSON_LIST);
        matcher.addURI(AUTHORITY, "people/#", PERSON_ITEM);
        matcher.addURI(AUTHORITY, "people/#/transactions", PERSON_TRANSACTION_LIST);
        matcher.addURI(AUTHORITY, "attachments", ATTACHMENT_LIST);
        matcher.addURI(AUTHORITY, "attachments/#", ATTACHMENT_ITEM);
        return matcher;
    }

    /**
     * Read on every use instead of held in a field. A restore swaps the file underneath both
     * providers and closes the old helper, and a field would then be a closed handle until the
     * next process start.
     */
    private SQLDatabase db() {
        return SQLDatabase.getShared(getContext());
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    /**
     * Every cursor is registered on the whole provider and not on the entities its own query
     * reads. A cursor that names its own is a list that has to be kept in step with the joins
     * under it and with what each write announces, and getting that list wrong shows up as a
     * screen holding a figure that is no longer true. One of those lists already needed a uri
     * added by hand when saving a debt's master transaction started writing the debt's people.
     *
     * What it costs is reloads. Twenty five of the forty six cases named two entities or fewer,
     * ten of them exactly one, and every case now wakes for writes it did not name. The ten are
     * cheap single table queries; the transaction list is not, and it wakes for a currency or a
     * budget now where it named nine other things and not those. An import announces once per
     * list when it commits and not once a row, but a run of single row writes reaches further
     * than it used to. Attaching several files to one transaction, or the recurrence job posting
     * a backlog, are the two to picture. A reload is the query run again and then the whole list
     * rebound on the main thread, since nothing here sets an update throttle on a loader and the
     * adapters swap a cursor with notifyDataSetChanged.
     *
     * The cursor stays wrapped for one uri because the wrapper replays a change that arrives
     * between this method returning and a loader registering on the cursor. AbstractCursor drops
     * that one.
     */
    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Cursor cursor = null;
        switch (mUriMatcher.match(uri)) {
            case CURRENCY_LIST:
                cursor = new MultiUriCursorWrapper(db().getCurrencies(projection, selection, selectionArgs, sortOrder));
                break;
            case CURRENCY_ITEM:
                cursor = new MultiUriCursorWrapper(db().getCurrency(uri.getLastPathSegment(), projection));
                break;
            case WALLET_LIST:
                cursor = new MultiUriCursorWrapper(db().getWallets(projection, selection, selectionArgs, sortOrder));
                break;
            case WALLET_ITEM:
                cursor = new MultiUriCursorWrapper(db().getWallet(ContentUris.parseId(uri), projection));
                break;
            case TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getTransactions(projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSACTION_ITEM:
                cursor = new MultiUriCursorWrapper(db().getTransaction(ContentUris.parseId(uri), projection));
                break;
            case TRANSACTION_ATTACHMENTS:
                cursor = new MultiUriCursorWrapper(db().getTransactionAttachments(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSACTION_PEOPLE:
                cursor = new MultiUriCursorWrapper(db().getTransactionPeople(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSFER_LIST:
                cursor = new MultiUriCursorWrapper(db().getTransfers(projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSFER_ITEM:
                cursor = new MultiUriCursorWrapper(db().getTransfer(ContentUris.parseId(uri), projection));
                break;
            case TRANSFER_ATTACHMENTS:
                cursor = new MultiUriCursorWrapper(db().getTransferAttachments(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSFER_PEOPLE:
                cursor = new MultiUriCursorWrapper(db().getTransferPeople(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case CATEGORY_LIST:
                cursor = new MultiUriCursorWrapper(db().getCategories(projection, selection, selectionArgs, sortOrder));
                break;
            case CATEGORY_ITEM:
                cursor = new MultiUriCursorWrapper(db().getCategory(ContentUris.parseId(uri), projection));
                break;
            case CATEGORY_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getCategoryTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case DEBT_LIST:
                cursor = new MultiUriCursorWrapper(db().getDebts(projection, selection, selectionArgs, sortOrder));
                break;
            case DEBT_ITEM:
                cursor = new MultiUriCursorWrapper(db().getDebt(ContentUris.parseId(uri), projection));
                break;
            case DEBT_PEOPLE:
                cursor = new MultiUriCursorWrapper(db().getDebtPeople(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case DEBT_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getDebtTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case BUDGET_LIST:
                cursor = new MultiUriCursorWrapper(db().getBudgets(projection, selection, selectionArgs, sortOrder));
                break;
            case BUDGET_ITEM:
                cursor = new MultiUriCursorWrapper(db().getBudget(ContentUris.parseId(uri), projection));
                break;
            case BUDGET_WALLETS:
                cursor = new MultiUriCursorWrapper(db().getBudgetWallets(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case BUDGET_CATEGORIES:
                cursor = new MultiUriCursorWrapper(db().getBudgetCategories(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case BUDGET_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getBudgetTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case SAVING_LIST:
                cursor = new MultiUriCursorWrapper(db().getSavings(projection, selection, selectionArgs, sortOrder));
                break;
            case SAVING_ITEM:
                cursor = new MultiUriCursorWrapper(db().getSaving(ContentUris.parseId(uri), projection));
                break;
            case SAVING_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getSavingTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case EVENT_LIST:
                cursor = new MultiUriCursorWrapper(db().getEvents(projection, selection, selectionArgs, sortOrder));
                break;
            case EVENT_ITEM:
                cursor = new MultiUriCursorWrapper(db().getEvent(ContentUris.parseId(uri), projection));
                break;
            case EVENT_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getEventTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case RECURRENT_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getRecurrentTransactions(projection, selection, selectionArgs, sortOrder));
                break;
            case RECURRENT_TRANSACTION_ITEM:
                cursor = new MultiUriCursorWrapper(db().getRecurrentTransaction(ContentUris.parseId(uri), projection));
                break;
            case RECURRENT_TRANSFER_LIST:
                cursor = new MultiUriCursorWrapper(db().getRecurrentTransfers(projection, selection, selectionArgs, sortOrder));
                break;
            case RECURRENT_TRANSFER_ITEM:
                cursor = new MultiUriCursorWrapper(db().getRecurrentTransfer(ContentUris.parseId(uri), projection));
                break;
            case TRANSACTION_MODEL_LIST:
                cursor = new MultiUriCursorWrapper(db().getTransactionModels(projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSACTION_MODEL_ITEM:
                cursor = new MultiUriCursorWrapper(db().getTransactionModel(ContentUris.parseId(uri), projection));
                break;
            case TRANSFER_MODEL_LIST:
                cursor = new MultiUriCursorWrapper(db().getTransferModels(projection, selection, selectionArgs, sortOrder));
                break;
            case TRANSFER_MODEL_ITEM:
                cursor = new MultiUriCursorWrapper(db().getTransferModel(ContentUris.parseId(uri), projection));
                break;
            case CATEGORY_RULE_LIST:
                cursor = new MultiUriCursorWrapper(db().getCategoryRules(projection, selection, selectionArgs, sortOrder));
                break;
            case CATEGORY_RULE_ITEM:
                cursor = new MultiUriCursorWrapper(db().getCategoryRule(ContentUris.parseId(uri), projection));
                break;
            case CATEGORY_RULE_MATCH:
                cursor = new MultiUriCursorWrapper(db().getCategoryRuleMatch(uri.getLastPathSegment()));
                break;
            case PLACE_LIST:
                cursor = new MultiUriCursorWrapper(db().getPlaces(projection, selection, selectionArgs, sortOrder));
                break;
            case PLACE_ITEM:
                cursor = new MultiUriCursorWrapper(db().getPlace(ContentUris.parseId(uri), projection));
                break;
            case PLACE_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getPlaceTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case PERSON_LIST:
                cursor = new MultiUriCursorWrapper(db().getPeople(projection, selection, selectionArgs, sortOrder));
                break;
            case PERSON_ITEM:
                cursor = new MultiUriCursorWrapper(db().getPerson(ContentUris.parseId(uri), projection));
                break;
            case PERSON_TRANSACTION_LIST:
                cursor = new MultiUriCursorWrapper(db().getPeopleTransactions(parseIdAtIndex(uri, 1), projection, selection, selectionArgs, sortOrder));
                break;
            case ATTACHMENT_LIST:
                cursor = new MultiUriCursorWrapper(db().getAttachments(projection, selection, selectionArgs, sortOrder));
                break;
            case ATTACHMENT_ITEM:
                cursor = new MultiUriCursorWrapper(db().getAttachment(ContentUris.parseId(uri), projection));
                break;
        }
        if (cursor != null) {
            cursor.setNotificationUri(getContentResolver(), CONTENT_ALL);
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (mUriMatcher.match(uri)) {
            case CURRENCY_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.currency";
            case CURRENCY_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.currency";
            case WALLET_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.wallet";
            case WALLET_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.wallet";
            case TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case TRANSACTION_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.transaction";
            case TRANSACTION_ATTACHMENTS:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.attachments";
            case TRANSACTION_PEOPLE:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person";
            case TRANSFER_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transfer";
            case TRANSFER_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.transfer";
            case TRANSFER_ATTACHMENTS:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.attachments";
            case TRANSFER_PEOPLE:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person";
            case CATEGORY_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.category";
            case CATEGORY_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.category";
            case CATEGORY_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case DEBT_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.debt";
            case DEBT_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.debt";
            case DEBT_PEOPLE:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person";
            case DEBT_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case BUDGET_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.budget";
            case BUDGET_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.budget";
            case BUDGET_WALLETS:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.wallet";
            case BUDGET_CATEGORIES:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.category";
            case BUDGET_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case SAVING_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.saving";
            case SAVING_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.saving";
            case SAVING_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case EVENT_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.event";
            case EVENT_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.event";
            case EVENT_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case RECURRENT_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.recurrence.transaction";
            case RECURRENT_TRANSACTION_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.recurrence.transaction";
            case RECURRENT_TRANSFER_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.recurrence.transfer";
            case RECURRENT_TRANSFER_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.recurrence.transfer";
            case TRANSACTION_MODEL_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.model.transaction";
            case TRANSACTION_MODEL_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.model.transaction";
            case TRANSFER_MODEL_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.model.transfer";
            case TRANSFER_MODEL_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.model.transfer";
            case CATEGORY_RULE_LIST:
            case CATEGORY_RULE_MATCH:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.category_rule";
            case CATEGORY_RULE_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.category_rule";
            case PLACE_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.place";
            case PLACE_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.place";
            case PLACE_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case PERSON_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.person";
            case PERSON_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.person";
            case PERSON_TRANSACTION_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.transaction";
            case ATTACHMENT_LIST:
                return "vnd.android.cursor.dir/vnd.com.oriondev.moneywallet.storage.attachment";
            case ATTACHMENT_ITEM:
                return "vnd.android.cursor.item/vnd.com.oriondev.moneywallet.storage.attachment";
        }
        return null;
    }

    /**
     * Every write this provider makes runs inside one transaction, so a method that touches more
     * than one table cannot leave the database part way through. The observers are told only once
     * the commit has gone through, since a transaction that rolls back would otherwise have
     * already announced a change that never happened.
     */
    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues contentValues) {
        Uri objectUri = SQLDatabase.inSharedTransaction(getContext(),
                database -> insertInTransaction(database, uri, contentValues));
        if (objectUri != null) {
            // the list, not the row, for the import path. See notifyObservers
            notifyObservers(objectUri, uri);
        }
        return objectUri;
    }

    private Uri insertInTransaction(SQLDatabase database, Uri uri, ContentValues contentValues) {
        String currencyIso = null;
        long objectId = 0L;
        switch (mUriMatcher.match(uri)) {
            case CURRENCY_LIST:
                currencyIso = database.insertCurrency(contentValues);
                break;
            case WALLET_LIST:
                objectId = database.insertWallet(contentValues);
                break;
            case TRANSACTION_LIST:
                objectId = database.insertTransaction(contentValues);
                break;
            case TRANSFER_LIST:
                objectId = database.insertTransfer(contentValues);
                break;
            case CATEGORY_LIST:
                objectId = database.insertCategory(contentValues);
                break;
            case DEBT_LIST:
                objectId = database.insertDebt(contentValues);
                break;
            case BUDGET_LIST:
                objectId = database.insertBudget(contentValues);
                break;
            case SAVING_LIST:
                objectId = database.insertSaving(contentValues);
                break;
            case EVENT_LIST:
                objectId = database.insertEvent(contentValues);
                break;
            case RECURRENT_TRANSACTION_LIST:
                objectId = database.insertRecurrentTransaction(contentValues);
                break;
            case RECURRENT_TRANSFER_LIST:
                objectId = database.insertRecurrentTransfer(contentValues);
                break;
            case TRANSACTION_MODEL_LIST:
                objectId = database.insertTransactionModel(contentValues);
                break;
            case TRANSFER_MODEL_LIST:
                objectId = database.insertTransferModel(contentValues);
                break;
            case CATEGORY_RULE_LIST:
                objectId = database.insertCategoryRule(contentValues);
                break;
            case PLACE_LIST:
                objectId = database.insertPlace(contentValues);
                break;
            case PERSON_LIST:
                objectId = database.insertPerson(contentValues);
                break;
            case ATTACHMENT_LIST:
                objectId = database.insertAttachment(contentValues);
                break;
        }
        if (currencyIso != null) {
            return Uri.withAppendedPath(uri, currencyIso);
        }
        if (objectId > 0L) {
            return ContentUris.withAppendedId(uri, objectId);
        }
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
        // one element so the body running inside the transaction can hand the uri back out. A
        // lambda cannot assign a local, and deriving it a second time out here would be the same
        // sixteen cases written twice
        Uri[] notifyUri = new Uri[1];
        int result = SQLDatabase.inSharedTransaction(getContext(),
                database -> deleteInTransaction(database, uri, notifyUri));
        // out here with the notify, and not in the WALLET_ITEM case where it used to sit, because
        // it writes a preference and broadcasts to every open screen and neither of those can be
        // rolled back. Reading the preference after the delete is the same read, nothing in the
        // database is what it answers from. Every wallet delete still passes through this point,
        // which is the reason it lives in the provider and not in the screen that starts one.
        //
        // Being out here no longer means the delete has committed. A delete made inside
        // runInOneTransaction commits only when that import does, so an import that deleted the
        // wallet in use and then failed leaves the wallet back in the ledger with the preference
        // already moved off it. Nothing deletes inside one today, and whoever writes the first
        // importer that does has to answer this
        Context context = getContext();
        if (result > 0 && context != null && mUriMatcher.match(uri) == WALLET_ITEM
                && ContentUris.parseId(uri) == PreferenceManager.getCurrentWallet()) {
            // the deleted wallet still has its id stored as the current one, and every query the
            // filter reaches keeps filtering on it, so all of them come back with nothing
            PreferenceManager.setCurrentWallet(context, PreferenceManager.TOTAL_WALLET_ID);
        }
        if (notifyUri[0] != null) {
            notifyObservers(notifyUri[0], notifyUri[0]);
        }
        return result;
    }

    private int deleteInTransaction(SQLDatabase database, Uri uri, Uri[] notifyUri) {
        int result = 0;
        switch (mUriMatcher.match(uri)) {
            case CURRENCY_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_CURRENCIES;
                result = database.deleteCurrency(uri.getLastPathSegment());
                break;
            case WALLET_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_WALLETS;
                result = database.deleteWallet(ContentUris.parseId(uri));
                break;
            case TRANSACTION_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_TRANSACTIONS;
                result = database.deleteTransaction(ContentUris.parseId(uri));
                break;
            case TRANSFER_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_TRANSFERS;
                result = database.deleteTransfer(ContentUris.parseId(uri));
                break;
            case CATEGORY_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_CATEGORIES;
                result = database.deleteCategory(ContentUris.parseId(uri));
                break;
            case DEBT_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_DEBTS;
                result = database.deleteDebt(ContentUris.parseId(uri));
                break;
            case BUDGET_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_BUDGETS;
                result = database.deleteBudget(ContentUris.parseId(uri));
                break;
            case SAVING_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_SAVINGS;
                result = database.deleteSaving(ContentUris.parseId(uri));
                break;
            case EVENT_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_EVENTS;
                result = database.deleteEvent(ContentUris.parseId(uri));
                break;
            case RECURRENT_TRANSACTION_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS;
                result = database.deleteRecurrentTransaction(ContentUris.parseId(uri));
                break;
            case RECURRENT_TRANSFER_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_RECURRENT_TRANSFERS;
                result = database.deleteRecurrentTransfer(ContentUris.parseId(uri));
                break;
            case TRANSACTION_MODEL_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_TRANSACTION_MODELS;
                result = database.deleteTransactionModel(ContentUris.parseId(uri));
                break;
            case TRANSFER_MODEL_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_TRANSFER_MODELS;
                result = database.deleteTransferModel(ContentUris.parseId(uri));
                break;
            case CATEGORY_RULE_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_CATEGORY_RULES;
                result = database.deleteCategoryRule(ContentUris.parseId(uri));
                break;
            case PLACE_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_PLACES;
                result = database.deletePlace(ContentUris.parseId(uri));
                break;
            case PERSON_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_PEOPLE;
                result = database.deletePerson(ContentUris.parseId(uri));
                break;
            case ATTACHMENT_ITEM:
                notifyUri[0] = DataContentProvider.CONTENT_ATTACHMENTS;
                result = database.deleteAttachment(ContentUris.parseId(uri));
                break;
        }
        return result;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int result = SQLDatabase.inSharedTransaction(getContext(),
                database -> updateInTransaction(database, uri, values));
        if (result > 0) {
            notifyObservers(uri, uri);
        }
        return result;
    }

    private int updateInTransaction(SQLDatabase database, Uri uri, ContentValues values) {
        int result = 0;
        switch (mUriMatcher.match(uri)) {
            case CURRENCY_ITEM:
                result = database.updateCurrency(uri.getLastPathSegment(), values);
                break;
            case WALLET_ITEM:
                result = database.updateWallet(ContentUris.parseId(uri), values);
                break;
            case TRANSACTION_ITEM:
                result = database.updateTransaction(ContentUris.parseId(uri), values);
                break;
            case TRANSFER_ITEM:
                result = database.updateTransfer(ContentUris.parseId(uri), values);
                break;
            case CATEGORY_ITEM:
                result = database.updateCategory(ContentUris.parseId(uri), values);
                break;
            case DEBT_ITEM:
                result = database.updateDebt(ContentUris.parseId(uri), values);
                break;
            case BUDGET_ITEM:
                result = database.updateBudget(ContentUris.parseId(uri), values);
                break;
            case SAVING_ITEM:
                result = database.updateSaving(ContentUris.parseId(uri), values);
                break;
            case EVENT_ITEM:
                result = database.updateEvent(ContentUris.parseId(uri), values);
                break;
            case RECURRENT_TRANSACTION_ITEM:
                result = database.updateRecurrentTransaction(ContentUris.parseId(uri), values);
                break;
            case RECURRENT_TRANSFER_ITEM:
                result = database.updateRecurrentTransfer(ContentUris.parseId(uri), values);
                break;
            case TRANSACTION_MODEL_ITEM:
                result = database.updateTransactionModel(ContentUris.parseId(uri), values);
                break;
            case TRANSFER_MODEL_ITEM:
                result = database.updateTransferModel(ContentUris.parseId(uri), values);
                break;
            case CATEGORY_RULE_ITEM:
                result = database.updateCategoryRule(ContentUris.parseId(uri), values);
                break;
            case PLACE_ITEM:
                result = database.updatePlace(ContentUris.parseId(uri), values);
                break;
            case PERSON_ITEM:
                result = database.updatePerson(ContentUris.parseId(uri), values);
                break;
        }
        return result;
    }

    private ContentResolver getContentResolver() {
        Context context = getContext();
        return context != null ? context.getContentResolver() : null;
    }

    /**
     * The uris a write on this thread would have announced, while an import holds one transaction
     * open around it. Null when this thread is not inside one, which is every write outside an
     * import and so the only path that announces anything the moment it is made.
     */
    private static final ThreadLocal<Set<Uri>> sDeferredNotifications = new ThreadLocal<>();

    /**
     * True when this thread is inside runInOneTransaction, and so holds a database connection for
     * as long as that runs. Which connection depends on the thread: a staged import holds the
     * staging one, everything else holds the one the app shares.
     *
     * This is narrower than holding a connection. A single provider write holds one too, through
     * inSharedTransaction, and does not set this. It covers the case worth covering, which is a
     * caller that has wrapped other work in a transaction and reaches something that takes a lock
     * across the database. SQLDatabase.resetShared refuses on a wider condition, the swap read
     * hold count, which it can read because that lock is private to it. Reaching the same
     * condition from here would take an accessor on SQLDatabase, which is worth doing the day
     * something inside a plain provider write can reach code that locks across the database.
     */
    public static boolean isInsideOneTransaction() {
        return sDeferredNotifications.get() != null;
    }

    /**
     * Tells the observers about a write, or remembers it for {@link #runInOneTransaction} to tell
     * them once the import it is running has committed. Announcing each row as it goes in would
     * name rows that are not committed yet, and a failed import rolls every one of them back with
     * nothing to take the announcements back.
     *
     * An import remembers whatToRemember and not the row it wrote. Insert passes the list it
     * inserted into, so a file of a hundred thousand rows holds one uri instead of a hundred
     * thousand and fires one announcement at the end instead of a hundred thousand. Nothing is
     * lost by it, an observer registered on a row below that list is told when the list is
     * announced, checked on an emulator by anImportAnnouncesTheListAndReachesTheRowsUnderIt and
     * not reasoned about. Delete and update pass their own uri unchanged, and update's is a row
     * uri, so an importer that updates rows would go back to one entry each. None does today.
     */
    private void notifyObservers(Uri uri, Uri whatToRemember) {
        Set<Uri> deferred = sDeferredNotifications.get();
        if (deferred != null) {
            deferred.add(whatToRemember);
            return;
        }
        PreferenceManager.setLastTimeDataIsChanged(System.currentTimeMillis());
        ContentResolver contentResolver = getContentResolver();
        if (contentResolver != null) {
            contentResolver.notifyChange(uri, null);
        }
    }

    /**
     * Runs an import inside one transaction, so a row it refuses part way through takes every row
     * before it back out. Each row still goes in through a provider, and each of those opens a
     * transaction of its own; SQLDatabase runs those inline once one is already open on the
     * thread, so there is one transaction and one commit.
     *
     * This covers SyncContentProvider's writes as well as this provider's. Both resolve the same
     * shared helper, so a transaction opened on it here encloses whatever either of them writes on
     * this thread. Of the two only this provider announces anything, and those announcements are
     * held back until the commit. The one thing that is not held back is the preference a wallet
     * delete writes, which is out of the database entirely and is described where it happens.
     *
     * Joining is decided from what this method itself left on the thread, so it catches another
     * call to this method and nothing else. Calling it from inside a provider write, or from
     * inside a body SQLDatabase is already running, would not join and would announce while that
     * transaction is still open. It is the entry point an importer starts from, not something to
     * reach for once a write is already running.
     *
     * What it costs, and it is more than it looks. The database is opened without write ahead
     * logging, so SQLite hands the whole app one connection, and an open transaction holds it.
     * Every other thread that touches the ledger waits for the import to finish, reads as well as
     * writes, and some of those writes are made on the main thread, where waiting is a frozen
     * screen. Keep what runs in here down to the writes themselves and keep the caller somewhere
     * that can wait, or somewhere nothing else is running yet. The import service and the legacy
     * upgrade service are the first. CurrencyManager writing the default currency set is the
     * other, and it reaches here three ways: from a first launch, where nothing else has started
     * yet; from a restore of a legacy backup, which carries no currencies and so leaves the table
     * empty, on the backup service thread while the user interface is live; and from deleting the
     * last currency, on the main thread. The last two can both make a user wait.
     *
     * Public, and here, because SQLDatabase is package local and the importers sit in a sub
     * package, so they cannot name it.
     */
    public static <T> T runInOneTransaction(Context context, Callable<T> body) throws Exception {
        if (sDeferredNotifications.get() != null) {
            // already inside one on this thread, so join it the way SQLDatabase.inTransaction
            // does. Opening a second would replace the outer's remembered uris with its own and
            // then announce them while the outer transaction is still open and can still roll back
            return body.call();
        }
        Set<Uri> deferred = new LinkedHashSet<>();
        sDeferredNotifications.set(deferred);
        T result;
        try {
            result = SQLDatabase.inSharedTransaction(context, database -> {
                try {
                    return body.call();
                } catch (RuntimeException e) {
                    throw e;
                } catch (Exception e) {
                    // the transaction body cannot declare a checked exception, and the importers
                    // all throw one. Carried out and rethrown below so the caller still sees the
                    // failure it knows how to report, and not a wrapper around it
                    throw new CheckedFailure(e);
                }
            });
        } catch (CheckedFailure e) {
            throw (Exception) e.getCause();
        } finally {
            sDeferredNotifications.remove();
        }
        // reached only when the transaction committed, so every uri here names rows that are
        // really in the ledger
        ContentResolver contentResolver = context.getContentResolver();
        if (!deferred.isEmpty()) {
            PreferenceManager.setLastTimeDataIsChanged(System.currentTimeMillis());
            for (Uri uri : deferred) {
                contentResolver.notifyChange(uri, null);
            }
        }
        return result;
    }

    private static class CheckedFailure extends RuntimeException {

        private CheckedFailure(Exception cause) {
            super(cause);
        }
    }

    /**
     * Parse an id from a uri starting from the end of the string.
     * @param uri to parse from.
     * @param index of the id starting from the end.
     * @return the parsed id.
     */
    private long parseIdAtIndex(Uri uri, int index) {
        List<String> segments = uri.getPathSegments();
        int fixedIndex = segments.size() - index;
        return Long.parseLong(segments.get(fixedIndex - 1));
    }

    /**
     * Runs a replacement of the database file with the shared helper closed and no write of this
     * provider in flight. The runnable does the file work and nothing else.
     *
     * Public, and here, because SQLDatabase is package local and the importers that replace the
     * database file sit in a sub package, so they cannot name it.
     */
    public static void replaceDatabaseFile(Context context, Runnable swap) {
        SQLDatabase.resetShared(context, swap);
    }

    /**
     * Sends the calling thread database work to a file of its own until the matching close. A
     * restore uses it to build its import somewhere other than the live ledger.
     *
     * No other thread is affected and no lock is taken, which is the whole reason the restore
     * redirect is per thread. Public for the same reason as the method above.
     */
    public static void openStagingDatabase(Context context, String name) {
        SQLDatabase.openStaging(context, name);
    }

    /**
     * Closes the calling thread staging database and sends its work back to the live ledger.
     * Does nothing when there is no staging database, so it is safe in a finally.
     */
    public static void closeStagingDatabase() {
        SQLDatabase.closeStaging();
    }

    @SuppressLint("Recycle")
    public static void notifyDatabaseIsChanged(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        ContentProviderClient client = contentResolver.acquireContentProviderClient(AUTHORITY);
        if (client != null) {
            ContentProvider contentProvider = client.getLocalContentProvider();
            if (contentProvider instanceof DataContentProvider) {
                SQLDatabase.resetShared(context);
                PreferenceManager.setCurrentWallet(context, PreferenceManager.NO_CURRENT_WALLET);
                // Same reason the current wallet is cleared just above. Every wallet is inserted
                // fresh by a restore, so the ids come back naming other wallets, and a widget is
                // pointed at one by its id. Left alone it would show a wallet nobody chose and
                // file new transactions into it. Nothing on this path calls notifyChange either,
                // which is why the redraw is asked for here.
                WalletWidgetProvider.forgetConfiguredWallets(context);
            }
            client.close();
        }
    }
}