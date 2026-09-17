package com.oriondev.moneywallet.storage.database.data;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;

import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.Icon;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Created by andrea on 23/12/18.
 */
public abstract class AbstractDataImporter {

    /*package-local*/ static final long NO_CATEGORY = -1L;

    private final Context mContext;

    public AbstractDataImporter(Context context, File file) throws IOException {
        mContext = context;
    }

    protected Context getContext() {
        return mContext;
    }

    public abstract void importData() throws IOException;

    /**
     * How many amounts this import had to round to fit the currency they were in, once
     * {@link #importData()} has run, and zero before it. A format that cannot carry an amount
     * the currency does not hold answers zero, but it has to say so rather than inherit it.
     */
    public abstract int getRoundedAmounts();

    protected void insertTransaction(String wallet, CurrencyUnit currencyUnit, String category,
                                     Date datetime, Long money, int direction, String description,
                                     String event, String place, String people, String note) {
        ContentResolver contentResolver = getContext().getContentResolver();
        // the first step consists in checking if a wallet with the same name and currency already
        // exists in the database, if not, create it and keep the id as reference
        long walletId = getOrCreateWallet(contentResolver, wallet, currencyUnit);
        insertTransaction(contentResolver, walletId, category, datetime, money, direction, description, event, place, people, note);
    }

    /**
     * The same, into a wallet that already exists and was picked by id, whose currency the
     * amount is already in.
     */
    protected void insertTransaction(long walletId, String category, Date datetime, Long money,
                                     int direction, String description, String event, String place,
                                     String people, String note) {
        insertTransaction(getContext().getContentResolver(), walletId, category, datetime, money, direction, description, event, place, people, note);
    }

    private void insertTransaction(ContentResolver contentResolver, long walletId, String category,
                                   Date datetime, Long money, int direction, String description,
                                   String event, String place, String people, String note) {
        ContentValues contentValues = new TransactionContentValuesBuilder()
                .walletId(walletId)
                // the second step consists in checking if a category with the same name and direction
                // already exists in the database, if not, create it and keep the id as reference
                .categoryId(getOrCreateCategory(contentResolver, category, direction))
                // the third step consists in adding the datetime, the money and the direction, the
                // description and the note related to this transaction
                .date(DateUtils.getSQLDateTimeString(datetime))
                .direction(direction)
                .money(money)
                .description(description)
                .note(note)
                // the fourth step consists in checking if the event has been provided,
                // if not, simply ignore it because we cannot know the date range
                .eventId(getEvent(contentResolver, event))
                // the fifth step consists in checking if a place with the same name already
                // exists in the database, if not, create it and keep the id as reference
                .placeId(getOrCreatePlace(contentResolver, place))
                // the sixth step consists in checking if a set of people with the same name already
                // exists in the database, if not, create them and keep the ids as reference
                .peopleIds(getOrCreatePeople(contentResolver, people))
                // the last step consists in inserting the entity inside the database
                .type(Contract.TransactionType.STANDARD)
                .confirmed(true)
                .countInTotal(true)
                .build();
        Uri result = contentResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, contentValues);
        if (result == null) {
            // the provider refused the row and says so by answering nothing. Every other insert
            // here already checks, and this one used to drop the answer, so a refused transaction
            // was skipped in silence and the import went on to report that it had worked
            throw new RuntimeException("Failed to save the transaction");
        }
    }

    private long getOrCreateWallet(ContentResolver contentResolver, String name, CurrencyUnit currencyUnit) {
        Uri uri = DataContentProvider.CONTENT_WALLETS;
        String[] projection = new String[] {Contract.Wallet.ID};
        String selection = Contract.Wallet.NAME + " = ? AND " + Contract.Wallet.CURRENCY + " = ?";
        String[] selectionArgs = new String[] {name, currencyUnit.getIso()};
        String sortOrder = Contract.Wallet.ID + " DESC";
        Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    return cursor.getLong(cursor.getColumnIndex(Contract.Wallet.ID));
                }
            } finally {
                cursor.close();
            }
        }
        // if we reached this line, the wallet does not exists and we have to create it
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, generateRandomIcon(name));
        contentValues.put(Contract.Wallet.CURRENCY, currencyUnit.getIso());
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.ARCHIVED, false);
        Uri result = contentResolver.insert(uri, contentValues);
        if (result == null) {
            throw new RuntimeException("Failed to create the new wallet");
        }
        return ContentUris.parseId(result);
    }

    private long getOrCreateCategory(ContentResolver contentResolver, String name, int direction) {
        Uri uri = DataContentProvider.CONTENT_CATEGORIES;
        Contract.CategoryType type = direction == Contract.Direction.INCOME ? Contract.CategoryType.INCOME : Contract.CategoryType.EXPENSE;
        String[] projection = new String[] {Contract.Category.ID, Contract.Category.TYPE, Contract.Category.TAG};
        String selection = Contract.Category.NAME + " = ? AND (" + Contract.Category.TYPE + " = ? OR " + Contract.Category.TYPE + " = ?)";
        String[] selectionArgs = new String[] {name,
                String.valueOf(type.getValue()),
                String.valueOf(Contract.CategoryType.SYSTEM.getValue())
        };
        String sortOrder = Contract.Category.ID + " DESC";
        Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
        if (cursor != null) {
            try {
                long categoryId = chooseCategory(cursor, direction);
                if (categoryId != NO_CATEGORY) {
                    return categoryId;
                }
            } finally {
                cursor.close();
            }
        }
        // if we reached this line, the category does not exists and we have to create it
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Category.NAME, name);
        contentValues.put(Contract.Category.ICON, generateRandomIcon(name));
        contentValues.put(Contract.Category.TYPE, type.getValue());
        contentValues.putNull(Contract.Category.PARENT);
        contentValues.put(Contract.Category.SHOW_REPORT, true);
        Uri result = contentResolver.insert(uri, contentValues);
        if (result == null) {
            throw new RuntimeException("Failed to create the new category");
        }
        return ContentUris.parseId(result);
    }

    /**
     * The system tags {@link Category#getDirection()} has a case for. It returns the same zero for
     * a tag it does not name as it does for an expense, so a tag missing from here cannot be read
     * as one. The transfer tag is absent because a transfer is written as two legs, one per
     * wallet, so both directions appear under it.
     */
    private static final Set<String> TAGS_WITH_A_KNOWN_DIRECTION = new HashSet<>(Arrays.asList(
            Contract.CategoryTag.CREDIT,
            Contract.CategoryTag.PAID_DEBT,
            Contract.CategoryTag.SAVING_DEPOSIT,
            Contract.CategoryTag.TAX,
            Contract.CategoryTag.TRANSFER_TAX,
            Contract.CategoryTag.DEBT,
            Contract.CategoryTag.PAID_CREDIT,
            Contract.CategoryTag.SAVING_WITHDRAW
    ));

    /**
     * The id of the first category in the cursor that can hold a row of this direction, or
     * {@link #NO_CATEGORY} when none can. A candidate that cannot hold the row is passed over
     * rather than ending the search, so a system category the guard refuses does not hide a
     * category the user made behind it. The cursor is rewound first, so wrapping the call in the
     * moveToFirst every other lookup in this class opens with does not skip a candidate.
     */
    /*package-local*/ static long chooseCategory(Cursor cursor, int direction) {
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            if (keepsDirection(cursor, direction)) {
                return cursor.getLong(cursor.getColumnIndex(Contract.Category.ID));
            }
        }
        return NO_CATEGORY;
    }

    /**
     * Whether a category holds a row of this direction without the direction being rewritten
     * later.
     *
     * An income or expense category is its direction, so it holds a row of that direction and no
     * other. A system category is not: the transaction editor derives a direction from the
     * category, and for a system category that means from its tag, through
     * {@link Category#getDirection()}. It writes that over whatever the row held, on every save. A
     * row given a system category whose tag implies the other direction would be flipped the first
     * time it was opened and saved, moving the wallet total by twice its amount.
     */
    private static boolean keepsDirection(Cursor cursor, int direction) {
        Contract.CategoryType type = Contract.CategoryType.fromValue(
                cursor.getInt(cursor.getColumnIndex(Contract.Category.TYPE)));
        String tag = cursor.getString(cursor.getColumnIndex(Contract.Category.TAG));
        if (type == Contract.CategoryType.SYSTEM && !TAGS_WITH_A_KNOWN_DIRECTION.contains(tag)) {
            return false;
        }
        return new Category(0, null, null, type, tag).getDirection() == direction;
    }

    private Long getEvent(ContentResolver contentResolver, String name) {
        if (!TextUtils.isEmpty(name)) {
            Uri uri = DataContentProvider.CONTENT_EVENTS;
            String[] projection = new String[] {Contract.Event.ID};
            String selection = Contract.Event.NAME + " = ?";
            String[] selectionArgs = new String[] {name};
            String sortOrder = Contract.Event.ID + " DESC";
            Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(cursor.getColumnIndex(Contract.Event.ID));
                    }
                } finally {
                    cursor.close();
                }
            }
        }
        return null;
    }

    private Long getOrCreatePlace(ContentResolver contentResolver, String name) {
        if (!TextUtils.isEmpty(name)) {
            Uri uri = DataContentProvider.CONTENT_PLACES;
            String[] projection = new String[] {Contract.Place.ID};
            String selection = Contract.Place.NAME + " = ?";
            String[] selectionArgs = new String[] {name};
            String sortOrder = Contract.Place.ID + " DESC";
            Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
            if (cursor != null) {
                try {
                    if (cursor.moveToFirst()) {
                        return cursor.getLong(cursor.getColumnIndex(Contract.Place.ID));
                    }
                } finally {
                    cursor.close();
                }
            }
            // if we reached this line, the place does not exists and we have to create it
            ContentValues contentValues = new ContentValues();
            contentValues.put(Contract.Place.NAME, name);
            contentValues.put(Contract.Place.ICON, generateRandomIcon(name));
            contentValues.putNull(Contract.Place.ADDRESS);
            contentValues.putNull(Contract.Place.LATITUDE);
            contentValues.putNull(Contract.Place.LONGITUDE);
            Uri result = contentResolver.insert(uri, contentValues);
            if (result == null) {
                throw new RuntimeException("Failed to create the new place");
            }
            return ContentUris.parseId(result);
        }
        return null;
    }

    private String getOrCreatePeople(ContentResolver contentResolver, String people) {
        if (!TextUtils.isEmpty(people)) {
            List<Long> peopleIds = new ArrayList<>();
            for (String person : people.split(",")) {
                String name = person.trim();
                if (!TextUtils.isEmpty(name)) {
                    boolean personFound = false;
                    Uri uri = DataContentProvider.CONTENT_PEOPLE;
                    String[] projection = new String[] {Contract.Person.ID};
                    String selection = Contract.Person.NAME + " = ?";
                    String[] selectionArgs = new String[] {name};
                    String sortOrder = Contract.Person.ID + " DESC";
                    Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder);
                    if (cursor != null) {
                        try {
                            if (cursor.moveToFirst()) {
                                personFound = true;
                                peopleIds.add(cursor.getLong(cursor.getColumnIndex(Contract.Person.ID)));
                            }
                        } finally {
                            cursor.close();
                        }
                    }
                    if (!personFound) {
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(Contract.Person.NAME, name);
                        contentValues.put(Contract.Person.ICON, name);
                        contentValues.putNull(Contract.Person.NOTE);
                        Uri result = contentResolver.insert(uri, contentValues);
                        if (result == null) {
                            throw new RuntimeException("Failed to create the new person");
                        }
                        peopleIds.add(ContentUris.parseId(result));
                    }
                }
            }
            if (!peopleIds.isEmpty()) {
                StringBuilder peopleIdBuilder = new StringBuilder();
                for (int i = 0; i < peopleIds.size(); i++) {
                    if (i != 0) {
                        peopleIdBuilder.append(",");
                    }
                    peopleIdBuilder.append(String.format(Locale.ENGLISH, "<%d>", peopleIds.get(i)));
                }
                return peopleIdBuilder.toString();
            }
        }
        return null;
    }

    private String generateRandomIcon(String name) {
        int randomColor = Utils.getRandomMDColor();
        String iconText = IconLoader.getColorIconString(name);
        Icon icon = new ColorIcon(randomColor, iconText);
        return icon.toString();
    }

    public abstract void close() throws IOException;
}