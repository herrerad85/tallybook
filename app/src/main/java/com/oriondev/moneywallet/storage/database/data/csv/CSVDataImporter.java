package com.oriondev.moneywallet.storage.database.data.csv;

import android.content.Context;
import android.database.Cursor;

import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.CSVReaderHeaderAware;
import com.opencsv.exceptions.CsvMalformedLineException;
import com.opencsv.exceptions.CsvValidationException;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.CurrencyUnit;
import com.oriondev.moneywallet.model.MoneyScale;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.data.AbstractDataImporter;
import com.oriondev.moneywallet.storage.database.data.Constants;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.DateUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.Reader;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.TreeSet;

/**
 * Created by andrea on 23/12/18.
 */
public class CSVDataImporter extends AbstractDataImporter {

    /** The length of yyyy-MM-dd, which the date parse itself does not bound. */
    private static final int SQL_DATE_LENGTH = 10;

    /** What a file saved as Unicode text can carry in front of its first character. */
    private static final char BYTE_ORDER_MARK = '\uFEFF';

    private static final int MAPPED_HEADER_LINES = 1;

    private final File mFile;

    private final CsvImportMapping mMapping;

    private final CSVReader mReader;

    private final int mHeaderCells;

    private CurrencyUnit mMappedCurrency;

    private int mRoundedAmounts;

    public CSVDataImporter(Context context, File file) throws IOException {
        this(context, file, null);
    }

    /**
     * @param mapping how to read a file this app did not write, or null for a file it did, which
     *                is then read exactly as the two argument constructor has always read it.
     */
    public CSVDataImporter(Context context, File file, CsvImportMapping mapping) throws IOException {
        super(context, file);
        mFile = file;
        mMapping = mapping;
        // read the way the mapping screen read it, so the count is of the columns that were mapped
        mHeaderCells = mapping == null ? 0 : CsvImportMapping.readHeader(file).cells.length;
        mReader = openReader();
    }

    /**
     * The one place in this class that opens the file. What was reported was not a fault in the
     * reader on its own, it was a file reaching a reader that had not dropped the byte order mark
     * the file starts with, and this class opens the file twice. Opening it in one place is what
     * keeps the second pass from drifting away from the first.
     */
    private CSVReader openReader() throws IOException {
        Reader reader = openFile(mFile);
        try {
            if (mMapping == null) {
                return new CSVReaderHeaderAware(reader);
            }
            // read by position, which also copes with a header that repeats a name or leaves one
            // blank. The header line is skipped, since the mapping already says what it holds
            return new CSVReaderBuilder(reader)
                    .withCSVParser(CsvImportMapping.parser(mMapping.separator))
                    .withSkipLines(MAPPED_HEADER_LINES)
                    .build();
        } catch (IOException | RuntimeException failed) {
            closeQuietly(reader);
            throw failed;
        }
    }

    /**
     * The file, with any byte order mark it starts with dropped. Some tools write one in front of
     * the first character of a file they save as UTF-8, and the character has no width, so
     * anything that decodes the file as UTF-8 shows a header that reads correctly. It is a
     * character like any other to the reader, so it joins the first header cell: the file names a
     * column nobody will ever look up, and every row is then refused for having no wallet column.
     *
     * More than one can pile up when a file that already carries one goes back through a tool
     * that adds one, and every one of them has to come off for the same reason the first does.
     *
     * The charset is named here so the mark decodes to the same character on a desktop as it does
     * on a device. Android's default is already UTF-8, so nothing about an import changes. A test
     * running on a host whose default is something else would otherwise read the mark as
     * characters of its own and fail for a reason that has nothing to do with the file.
     *
     * A file with nothing left after the mark is refused here, with a message a person can read.
     * The reader this hands the file to asks the header for its length without checking whether
     * there was a header at all, so a file with nothing in it has always reached the failure
     * dialog as a null pointer message. Dropping the mark put the file that holds only a mark
     * into that same case, which is why the check belongs here. It is a check for no characters
     * and not for no header line: a file holding one line ending and nothing else still reports a
     * successful import of nothing, exactly as it did before.
     */
    /*package-local*/ static Reader openFile(File file) throws IOException {
        PushbackReader reader = new PushbackReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"));
        try {
            int first = reader.read();
            while (first == BYTE_ORDER_MARK) {
                first = reader.read();
            }
            if (first == -1) {
                throw new RuntimeException("the file has no header line in it");
            }
            reader.unread(first);
            return reader;
        } catch (IOException | RuntimeException failed) {
            closeQuietly(reader);
            throw failed;
        }
    }

    /**
     * Closes a reader that is being abandoned. A failure closing it would otherwise replace the
     * failure that made it worth abandoning, which is the one the person needs to read.
     */
    private static void closeQuietly(Reader reader) {
        try {
            reader.close();
        } catch (IOException closing) {
            // nothing useful to do with it, and it is not the failure being reported
        }
    }

    /**
     * Reads the whole file once without saving anything, then reads it again to save it. Saving
     * each row as it was read left every row before a bad one in the ledger, under a message
     * telling the user the import had failed. Reading twice parses every row twice. That is the
     * cost of not keeping the parsed rows: an import file can be any size, and a list of them
     * would grow with it.
     */
    @Override
    public void importData() throws IOException {
        if (mMapping != null) {
            mMappedCurrency = requireMappedWallet();
        }
        try (CSVReader check = openReader()) {
            readRows(check, false);
        }
        // only the saving pass. The transaction holds the one database connection the whole app
        // has, so reading the file inside it would hold that connection across a full parse for
        // nothing, twice over on a file that is fine
        try {
            DataContentProvider.runInOneTransaction(getContext(), () -> {
                readRows(mReader, true);
                return null;
            });
        } catch (IOException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    /**
     * Reads every row and throws on the first bad one. Saves a row only when write is true.
     * {@link #importData} calls this twice, with write off and then on. The first pass is what
     * names the line a bad row is on, and it keeps a file that was never going to work from
     * opening a write transaction at all. A row the database itself refuses reaches only the
     * second pass, and the transaction that pass runs in takes the rows before it back out.
     */
    private void readRows(CSVReader reader, boolean write) throws IOException {
        while (true) {
            Map<String, String> lineMap = null;
            String[] cells = null;
            if (mMapping == null) {
                lineMap = readMap((CSVReaderHeaderAware) reader);
                if (lineMap == null) {
                    return;
                }
            } else {
                long before = reader.getLinesRead();
                // the first read also counts the skipped header, and a stray quote can join lines
                // into a row with as many cells as the header
                long start = Math.max(before, MAPPED_HEADER_LINES) + 1;
                try {
                    cells = readNext(reader);
                } catch (CsvMalformedLineException quoteNeverClosed) {
                    // the quote stays open to the end of the file, so there is no row handed back
                    // for the check below to refuse
                    throw rowSpansLines(start);
                }
                if (cells == null) {
                    return;
                }
                if (reader.getLinesRead() > start) {
                    throw rowSpansLines(start);
                }
            }
            try {
                if (mMapping == null) {
                    readRow(lineMap, write);
                } else {
                    readMappedRow(cells, write);
                }
            } catch (RuntimeException e) {
                // Only the checking pass blames a line. The saving pass is where
                // insertTransaction runs, and "Line 37: Failed to create the new wallet" would
                // send the user to look at a row that is fine.
                if (write) {
                    throw e;
                }
                throw new RuntimeException("Line " + reader.getLinesRead() + ": " + e.getMessage(), e);
            }
        }
    }

    private static RuntimeException rowSpansLines(long start) {
        return new RuntimeException("Line " + start
                + ": a quote in this row is not closed on the same line, and every row has to fit on one line");
    }

    /**
     * opencsv declares a checked CsvValidationException on readMap, and IOException is what
     * every caller above this class already handles. A row the parser cannot read throws
     * CsvMalformedLineException, which is already an IOException and passes through untouched.
     */
    private static Map<String, String> readMap(CSVReaderHeaderAware reader) throws IOException {
        try {
            return reader.readMap();
        } catch (CsvValidationException e) {
            throw new IOException(e);
        }
    }

    /** The same as {@link #readMap}, for a reader that hands rows back by position. */
    private static String[] readNext(CSVReader reader) throws IOException {
        try {
            return reader.readNext();
        } catch (CsvValidationException e) {
            throw new IOException(e);
        }
    }

    private void readRow(Map<String, String> lineMap, boolean write) {
        // extract required information from the csv file
        String wallet = required(lineMap, Constants.COLUMN_WALLET);
        String currency = required(lineMap, Constants.COLUMN_CURRENCY);
        String category = required(lineMap, Constants.COLUMN_CATEGORY);
        String datetimeString = required(lineMap, Constants.COLUMN_DATETIME);
        String moneyString = required(lineMap, Constants.COLUMN_MONEY);
        // extract the optional information from the csv file
        String description = getTrimmedString(lineMap.get(Constants.COLUMN_DESCRIPTION));
        String event = getTrimmedString(lineMap.get(Constants.COLUMN_EVENT));
        String people = getTrimmedString(lineMap.get(Constants.COLUMN_PEOPLE));
        String place = getTrimmedString(lineMap.get(Constants.COLUMN_PLACE));
        String note = getTrimmedString(lineMap.get(Constants.COLUMN_NOTE));
        // try to build the internal transaction state starting from strings
        CurrencyUnit currencyUnit = currencyUnit(currency);
        BigDecimal moneyDecimal;
        try {
            moneyDecimal = new BigDecimal(moneyString.replaceAll(",", "."));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid money amount (" + e.getMessage() + ")");
        }
        long money = toMinorUnits(moneyDecimal, moneyString, currencyUnit.getDecimals());
        int direction = directionOf(money);
        Date datetime = parseDatetime(datetimeString);
        if (write && insertTransaction(wallet, currencyUnit, category, datetime, Math.abs(money), direction, description, event, place, people, note)) {
            countIfRounded(money, currencyUnit.getDecimals(), moneyDecimal);
        }
    }

    /**
     * A row of a file this app did not write. Every row goes to the one wallet picked for the
     * import, in that wallet's currency, and a row with no category goes under the fallback one.
     */
    private void readMappedRow(String[] cells, boolean write) {
        if (cells.length != mHeaderCells) {
            throw new RuntimeException("the row has " + cells.length + (cells.length == 1 ? " cell" : " cells")
                    + " and the header has " + mHeaderCells + ", and every row needs as many as the header");
        }
        String dateString = mappedRequired(cells, mMapping.date, "date");
        String amountString = mappedRequired(cells, mMapping.amount, "amount");
        String description = mappedOptional(cells, mMapping.description);
        String note = mappedOptional(cells, mMapping.note);
        String category = mappedOptional(cells, mMapping.category);
        BigDecimal amount = CsvImportMapping.parseAmount(amountString, mMapping.decimalComma);
        if (mMapping.spendingPositive) {
            amount = amount.negate();
        }
        Date datetime = CsvImportMapping.parseDate(dateString, mMapping.datePattern);
        long money = toMinorUnits(amount, amountString, mMappedCurrency.getDecimals());
        int direction = directionOf(money);
        if (write) {
            // read only when saving, so checking a row never reads a string resource
            if (category == null) {
                category = getContext().getString(R.string.csv_import_default_category);
            }
            if (insertTransaction(mMapping.walletId, category, datetime, Math.abs(money), direction, description, null, null, null, note)) {
                countIfRounded(money, mMappedCurrency.getDecimals(), amount);
            }
        }
    }

    /** The cell a required field was mapped to, trimmed. */
    private static String mappedRequired(String[] cells, int column, String field) {
        String value = mappedOptional(cells, column);
        if (value == null) {
            throw new RuntimeException("the " + field + " is empty, and every row needs one");
        }
        return value;
    }

    /** The cell an optional field was mapped to, trimmed, or null when it is absent or empty. */
    private static String mappedOptional(String[] cells, int column) {
        if (column < 0 || column >= cells.length || cells[column] == null) {
            return null;
        }
        String value = cells[column].trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * Checked once, before either pass. The wallet was picked on a screen that could have stayed
     * open while it was deleted, and a row saved under an id that no longer exists would belong to
     * no wallet at all. Its currency is read here too, since it can be edited after the pick.
     *
     * @return the currency the wallet has now, which every amount is converted into.
     */
    private CurrencyUnit requireMappedWallet() {
        Cursor cursor = getContext().getContentResolver().query(DataContentProvider.CONTENT_WALLETS,
                new String[] {Contract.Wallet.ID, Contract.Wallet.CURRENCY}, Contract.Wallet.ID + " = ?",
                new String[] {String.valueOf(mMapping.walletId)}, null);
        String currency = null;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    currency = cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.CURRENCY));
                }
            } finally {
                cursor.close();
            }
        }
        if (currency == null) {
            throw new RuntimeException("the wallet picked for this import no longer exists");
        }
        return currencyUnit(currency);
    }

    private static CurrencyUnit currencyUnit(String currency) {
        CurrencyUnit currencyUnit = CurrencyManager.getCurrency(currency);
        if (currencyUnit == null) {
            throw new RuntimeException("Unknown currency unit (" + currency + ")");
        }
        return currencyUnit;
    }

    private static int directionOf(long money) {
        return money < 0 ? Contract.Direction.EXPENSE : Contract.Direction.INCOME;
    }

    @Override
    public int getRoundedAmounts() {
        return mRoundedAmounts;
    }

    /**
     * The amount of a row in minor units. Rounded rather than cut off, because a file written
     * elsewhere carries whatever precision that place kept, and nothing here shows the amount
     * for review before it is saved. The row's own currency column decides the scale.
     *
     * @param cell the money column as the row wrote it, which is what a refusal quotes.
     */
    private long toMinorUnits(BigDecimal amount, String cell, int decimals) {
        long money;
        try {
            money = MoneyScale.toMinorUnitsRounded(amount, decimals);
        } catch (ArithmeticException e) {
            throw new RuntimeException("Money amount is out of range (" + cell + ")");
        }
        // The one value abs cannot turn positive, which would otherwise reach the ledger as a
        // negative amount sitting on an expense.
        if (money == Long.MIN_VALUE) {
            throw new RuntimeException("Money amount is out of range (" + cell + ")");
        }
        return money;
    }

    /**
     * Rows the currency could not hold exactly are counted, so the screen that reports the
     * import can say so. The test is the stored amount read back against what the row said, not
     * the rounded amount against the cut off one: a row rounded down lands where cutting off
     * would have left it, and its value moved just the same. Counted only for a row this import
     * saved.
     */
    private void countIfRounded(long money, int decimals, BigDecimal amount) {
        if (MoneyScale.toHumanAmount(money, decimals).compareTo(amount) != 0) {
            mRoundedAmounts++;
        }
    }

    /**
     * The value of a column every row needs. A column the header does not have and a cell left
     * empty are mistakes in different places, so they do not get the same message.
     *
     * The refusal lists the columns the row was read into, because the header a person is looking
     * at and the columns the reader built out of it are not always the same thing. A file
     * separated by semicolons reads as one column named after the whole line, and without the
     * list there is nothing on screen a person could use to see that.
     *
     * Sorted, and the message says so. The reader keeps these in a hash table, so their own order
     * has nothing to do with the file, and a list that looks like an order invites a person to
     * compare it against their header and conclude the app moved their columns around. Sorted by
     * character and not by any language's alphabet, which is why the message does not call it
     * alphabetical: a capital letter sorts before every lowercase one, and an accented letter
     * sorts after all of them.
     */
    /*package-local*/ static String required(Map<String, String> lineMap, String column) {
        String value = lineMap.get(column);
        if (value == null) {
            throw new RuntimeException("no " + column + " column was found. The columns read out "
                    + "of the header line, sorted, are " + new TreeSet<>(lineMap.keySet()));
        }
        value = value.trim();
        if (value.isEmpty()) {
            throw new RuntimeException("the " + column + " is empty, and every row needs one");
        }
        return value;
    }

    /**
     * A datetime, or a date on its own. A date with no time is the obvious thing to write by
     * hand, and it used to end the whole import.
     *
     * The datetime is tried first because that is what this app's own export writes. The order
     * is not what makes this safe; the two checks below are, and they hold in either order.
     */
    /*package-local*/ static Date parseDatetime(String value) {
        try {
            return DateUtils.getDateFromSQLDateTimeString(value);
        } catch (RuntimeException notADatetime) {
            // fall through and read it as a date on its own
        }
        Date date;
        try {
            date = DateUtils.getDateFromSQLDateString(value);
        } catch (RuntimeException notADate) {
            throw notADate(value);
        }
        // What this parse does not throw for, it can still read as a different day than the one
        // written down, so the answer is written back out and compared. That is what refuses
        // anything still carrying a time, since the parse stops at the first character it cannot
        // use and "2026-08-12T09:30:15" would come back as the 12th with the time gone, and
        // anything naming a day past the end of its month, since it rolls those forward and
        // "2026-02-30" would come back as March 2nd. The length covers the one thing the
        // comparison cannot see. A year is padded out to four digits and never cut short, so a
        // short year fails the comparison anyway, but a year past 9999 is written back at its own
        // length: "12026-08-12" compares equal to itself and would land in the year 12026. Every
        // value refused here was already refused before a date on its own was accepted at all.
        // The length is tested first, so for most of these it is the check that fires.
        if (value.length() != SQL_DATE_LENGTH || !DateUtils.getSQLDateString(date).equals(value)) {
            throw notADate(value);
        }
        return date;
    }

    private static RuntimeException notADate(String value) {
        return new RuntimeException("the datetime \"" + value + "\" is not a real date as "
                + "yyyy-MM-dd HH:mm:ss or yyyy-MM-dd");
    }

    private String getTrimmedString(String source) {
        if (source != null) {
            return source.trim();
        }
        return null;
    }

    @Override
    public void close() throws IOException {
        mReader.close();
    }
}
