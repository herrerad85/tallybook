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
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataExporter;
import com.oriondev.moneywallet.utils.IconLoader;
import com.oriondev.moneywallet.utils.DateUtils;
import com.oriondev.moneywallet.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Created by andrea on 23/12/18.
 */
public abstract class AbstractDataImporter {

    /*package-local*/ static final long NO_CATEGORY = -1L;

    private final Context mContext;

    /**
     * Per wallet, the transactions it already held, read the first time this import reaches that
     * wallet and so before it wrote anything there. A match is used up when a row is skipped
     * against it, and rows this import writes are never added.
     */
    private final Map<Long, SavedRows> mSavedRows = new HashMap<>();

    private int mAlreadySavedRows;

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

    /**
     * How many rows this import skipped because the wallet already held a transaction, or a
     * category line of a split one, with the same date, direction, amount and description, once
     * {@link #importData()} has run. A split transaction is matched by its total or by its lines,
     * never by both, and which one is settled by every row of the file before the first row is
     * matched, when the rows were counted first through {@link #countRow}. When no row was
     * counted, as for a file read through a mapping, every split transaction is matched by its
     * total.
     */
    public int getAlreadySavedRows() {
        return mAlreadySavedRows;
    }

    /**
     * @return false when the row was skipped because the wallet already held it.
     */
    protected boolean insertTransaction(String wallet, CurrencyUnit currencyUnit, String category,
                                     Date datetime, Long money, int direction, String description,
                                     String event, String place, String people, String note) {
        ContentResolver contentResolver = getContext().getContentResolver();
        // the first step consists in checking if a wallet with the same name and currency already
        // exists in the database, if not, create it and keep the id as reference
        long walletId = getOrCreateWallet(contentResolver, wallet, currencyUnit);
        return insertTransaction(contentResolver, walletId, category, datetime, money, direction, description, event, place, people, note);
    }

    /**
     * The same, into a wallet that already exists and was picked by id, whose currency the
     * amount is already in.
     */
    protected boolean insertTransaction(long walletId, String category, Date datetime, Long money,
                                     int direction, String description, String event, String place,
                                     String people, String note) {
        return insertTransaction(getContext().getContentResolver(), walletId, category, datetime, money, direction, description, event, place, people, note);
    }

    /**
     * Counts a row of the file against the wallet it names, before any row is saved, so that each
     * split transaction is settled by the whole file and not by whichever row reaches it first.
     * Creates nothing, since a wallet that does not exist yet held no rows and so needs no count.
     */
    protected void countRow(String wallet, CurrencyUnit currencyUnit, Date datetime, long money,
                            int direction, String description) {
        ContentResolver contentResolver = getContext().getContentResolver();
        Long walletId = findWallet(contentResolver, wallet, currencyUnit);
        if (walletId != null) {
            countRow(contentResolver, walletId, datetime, money, direction, description);
        }
    }

    private void countRow(ContentResolver contentResolver, long walletId, Date datetime, long money,
                          int direction, String description) {
        savedRows(contentResolver, walletId).count(rowKey(DateUtils.getSQLDateTimeString(datetime),
                direction, money, description));
    }

    private boolean insertTransaction(ContentResolver contentResolver, long walletId, String category,
                                   Date datetime, Long money, int direction, String description,
                                   String event, String place, String people, String note) {
        String date = DateUtils.getSQLDateTimeString(datetime);
        if (isAlreadySaved(contentResolver, walletId, date, direction, money, description)) {
            mAlreadySavedRows++;
            return false;
        }
        ContentValues contentValues = new TransactionContentValuesBuilder()
                .walletId(walletId)
                // the second step consists in checking if a category with the same name and direction
                // already exists in the database, if not, create it and keep the id as reference
                .categoryId(getOrCreateCategory(contentResolver, category, direction))
                // the third step consists in adding the datetime, the money and the direction, the
                // description and the note related to this transaction
                .date(date)
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
        return true;
    }

    /**
     * Whether the wallet already holds this row, so that importing the same file again does not
     * save it twice. Two identical rows in one file are both saved the first time, since only rows
     * that were in the wallet before the import count, and both skipped the next time.
     */
    private boolean isAlreadySaved(ContentResolver contentResolver, long walletId, String date,
                                   int direction, long money, String description) {
        return savedRows(contentResolver, walletId).use(rowKey(date, direction, money, description));
    }

    private SavedRows savedRows(ContentResolver contentResolver, long walletId) {
        SavedRows saved = mSavedRows.get(walletId);
        if (saved == null) {
            saved = readSavedRows(contentResolver, walletId);
            mSavedRows.put(walletId, saved);
        }
        return saved;
    }

    /**
     * A split transaction the wallet held before the import. A file made before it was split holds
     * it as one row for its total, and an export made after holds it as one row per category line.
     */
    private static final class SavedSplit {

        private final List<Object> mTotal;
        private final List<List<Object>> mLines;

        private SavedSplit(List<Object> total, List<List<Object>> lines) {
            mTotal = total;
            mLines = lines;
        }
    }

    /**
     * What a wallet held before the import, as how many more rows each key can match. An unsplit
     * transaction offers its total once. A split one offers its total or its lines, never both,
     * and which is settled once, from the rows counted before the first row is matched, so the
     * order of the rows does not decide it.
     */
    private static final class SavedRows {

        private final Map<List<Object>, Integer> mLeft = new HashMap<>();

        /** The split transactions, until they are settled into mLeft. */
        private List<SavedSplit> mSplits = new ArrayList<>();

        /**
         * How many rows of the file hold each line key of a split transaction. Only those keys
         * are counted, so this grows with the wallet's split transactions and not with the file.
         */
        private Map<List<Object>, Integer> mFileRows = new HashMap<>();

        private void count(List<Object> key) {
            mFileRows.computeIfPresent(key, (any, rows) -> rows + 1);
        }

        private boolean use(List<Object> key) {
            if (mSplits != null) {
                settle();
            }
            Integer left = mLeft.get(key);
            if (left == null) {
                return false;
            }
            if (left == 1) {
                mLeft.remove(key);
            } else {
                mLeft.put(key, left - 1);
            }
            return true;
        }

        /**
         * A split transaction is matched by its lines when the file holds each of them as many
         * times as the transaction has it, and by its total otherwise. As many file rows as there
         * are saved unsplit transactions with the same key are left out of the count, so unsplit
         * transactions equal to the parts of a receipt leave the receipt to its total. A part of
         * zero is a line the file has to hold too, so a file made before the split, which holds
         * only the total, settles that receipt to its total.
         */
        private void settle() {
            Map<List<Object>, Integer> unsplit = new HashMap<>(mLeft);
            for (SavedSplit split : mSplits) {
                Map<List<Object>, Integer> needed = new HashMap<>();
                for (List<Object> line : split.mLines) {
                    needed.merge(line, 1, Integer::sum);
                }
                boolean byLines = true;
                for (Map.Entry<List<Object>, Integer> line : needed.entrySet()) {
                    int available = mFileRows.get(line.getKey()) - unsplit.getOrDefault(line.getKey(), 0);
                    if (available < line.getValue()) {
                        byLines = false;
                    }
                }
                for (List<Object> key : byLines ? split.mLines : Collections.singletonList(split.mTotal)) {
                    mLeft.merge(key, 1, Integer::sum);
                }
            }
            mSplits = null;
            mFileRows = null;
        }
    }

    // one query per source per wallet and not one per row, at the cost of holding the wallet's
    // rows until the import ends. The category lines are kept only for a transaction with a part
    // among them, since any other gives one line equal to its total
    private static SavedRows readSavedRows(ContentResolver contentResolver, long walletId) {
        Map<Long, List<Object>> totals = new HashMap<>();
        List<String> arguments = new ArrayList<>(CSVDataExporter.ROWS_SELECTION_ARGS);
        arguments.add(String.valueOf(walletId));
        String selection = CSVDataExporter.ROWS_SELECTION + " AND " + Contract.Transaction.WALLET_ID + " = ?";
        String[] selectionArgs = arguments.toArray(new String[0]);
        Cursor cursor = contentResolver.query(DataContentProvider.CONTENT_TRANSACTIONS,
                new String[] {Contract.Transaction.ID, Contract.Transaction.DATE,
                        Contract.Transaction.DIRECTION, Contract.Transaction.MONEY,
                        Contract.Transaction.DESCRIPTION},
                selection, selectionArgs, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    totals.put(cursor.getLong(0), rowKey(cursor.getString(1), cursor.getInt(2),
                            cursor.getLong(3), cursor.getString(4)));
                }
            } finally {
                cursor.close();
            }
        }
        Map<Long, List<List<Object>>> lines = new HashMap<>();
        Set<Long> split = new HashSet<>();
        cursor = contentResolver.query(DataContentProvider.CONTENT_CATEGORY_LINES,
                new String[] {Contract.Transaction.ID, Contract.Transaction.SPLIT_ID,
                        Contract.Transaction.DATE, Contract.Transaction.DIRECTION,
                        Contract.Transaction.MONEY, Contract.Transaction.DESCRIPTION},
                selection, selectionArgs, null);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(0);
                    if (!cursor.isNull(1)) {
                        split.add(id);
                    }
                    lines.computeIfAbsent(id, key -> new ArrayList<>()).add(rowKey(cursor.getString(2),
                            cursor.getInt(3), cursor.getLong(4), cursor.getString(5)));
                }
            } finally {
                cursor.close();
            }
        }
        SavedRows saved = new SavedRows();
        for (Map.Entry<Long, List<Object>> total : totals.entrySet()) {
            if (split.contains(total.getKey())) {
                List<List<Object>> transactionLines = lines.get(total.getKey());
                saved.mSplits.add(new SavedSplit(total.getValue(), transactionLines));
                for (List<Object> line : transactionLines) {
                    saved.mFileRows.put(line, 0);
                }
            } else {
                saved.mLeft.merge(total.getValue(), 1, Integer::sum);
            }
        }
        return saved;
    }

    /**
     * A row with no description holds null when a mapped import saved it and an empty string when
     * an empty cell in this app's own format did, so the two are the same key. The description is
     * trimmed because both import paths trim it and the editor does not. A zero amount has no
     * direction, since the export writes a zero expense as 0 and the import reads 0 as income.
     */
    private static List<Object> rowKey(String date, int direction, long money, String description) {
        return Arrays.<Object>asList(date, money == 0 ? Contract.Direction.INCOME : direction, money,
                description == null ? "" : description.trim());
    }

    private long getOrCreateWallet(ContentResolver contentResolver, String name, CurrencyUnit currencyUnit) {
        Long walletId = findWallet(contentResolver, name, currencyUnit);
        if (walletId != null) {
            return walletId;
        }
        // if we reached this line, the wallet does not exists and we have to create it
        ContentValues contentValues = new ContentValues();
        contentValues.put(Contract.Wallet.NAME, name);
        contentValues.put(Contract.Wallet.ICON, generateRandomIcon(name));
        contentValues.put(Contract.Wallet.CURRENCY, currencyUnit.getIso());
        contentValues.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        contentValues.put(Contract.Wallet.START_MONEY, 0L);
        contentValues.put(Contract.Wallet.ARCHIVED, false);
        Uri result = contentResolver.insert(DataContentProvider.CONTENT_WALLETS, contentValues);
        if (result == null) {
            throw new RuntimeException("Failed to create the new wallet");
        }
        return ContentUris.parseId(result);
    }

    /**
     * The id of the wallet a row naming this wallet and currency belongs to, or null when there is
     * none yet.
     */
    private static Long findWallet(ContentResolver contentResolver, String name, CurrencyUnit currencyUnit) {
        Uri uri = DataContentProvider.CONTENT_WALLETS;
        String[] projection = new String[] {Contract.Wallet.ID};
        // trimmed because the wallet editor saves a name as typed and the import trims the one in
        // the file, so a name ending in a space would otherwise get a second wallet. SQLite TRIM
        // strips only spaces unless told the rest, and a wallet whose stored name is already the
        // trimmed one is sorted first, so "Cash" still wins over a newer "Cash "
        String trimmedName = "TRIM(" + Contract.Wallet.NAME + ", ' ' || char(9) || char(10) || char(13))";
        String selection = trimmedName + " = ? AND " + Contract.Wallet.CURRENCY + " = ?";
        String[] selectionArgs = new String[] {name, currencyUnit.getIso()};
        String sortOrder = "(" + Contract.Wallet.NAME + " = " + trimmedName + ") DESC, " + Contract.Wallet.ID + " DESC";
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
        return null;
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