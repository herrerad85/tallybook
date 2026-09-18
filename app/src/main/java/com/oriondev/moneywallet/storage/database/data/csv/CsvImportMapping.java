package com.oriondev.moneywallet.storage.database.data.csv;

import com.opencsv.CSVParser;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVWriter;
import com.opencsv.ICSVParser;
import com.oriondev.moneywallet.storage.database.data.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How to read a CSV file this app did not write: which column holds each field, and how the
 * dates and amounts in it are written. Plain Java, so every helper here runs in a JVM test.
 */
public class CsvImportMapping implements Serializable {

    public static final int NONE = -1;

    public static final String[] DATE_PATTERNS = {
            "yyyy-MM-dd", "dd/MM/yyyy", "dd/MM/yy", "MM/dd/yyyy", "MM/dd/yy", "dd.MM.yyyy", "dd.MM.yy"};

    /** The digits each entry of {@link #DATE_PATTERNS} allows, in the same order. */
    private static final String[] DATE_DIGITS = {
            "\\d{4}-\\d{1,2}-\\d{1,2}",
            "\\d{1,2}/\\d{1,2}/\\d{4}",
            "\\d{1,2}/\\d{1,2}/\\d{2}",
            "\\d{1,2}/\\d{1,2}/\\d{4}",
            "\\d{1,2}/\\d{1,2}/\\d{2}",
            "\\d{1,2}\\.\\d{1,2}\\.\\d{4}",
            "\\d{1,2}\\.\\d{1,2}\\.\\d{2}"
    };

    private static final String TIME_DIGITS = "( \\d{1,2}:\\d{2}(:\\d{2})?)?";

    private static final String SPACE = "[ \\u00A0\\u202F]";

    // a grouped amount never starts with 0, so 0,500 is refused and not read as 500
    private static final Pattern DOT_AMOUNT = Pattern.compile(
            "^[+-]?(\\d+|[1-9]\\d{0,2}(,\\d{3})+|[1-9]\\d{0,2}(" + SPACE + "\\d{3})+)(\\.\\d+)?$");
    private static final Pattern COMMA_AMOUNT = Pattern.compile(
            "^[+-]?(\\d+|[1-9]\\d{0,2}(\\.\\d{3})+|[1-9]\\d{0,2}(" + SPACE + "\\d{3})+)(,\\d+)?$");

    private static final Pattern LEADING_SYMBOL = Pattern.compile("^([+-]?)\\p{Sc}" + SPACE + "?(.*)$");
    private static final Pattern TRAILING_SYMBOL = Pattern.compile("^(.*?)" + SPACE + "?\\p{Sc}$");

    private static final String[] NATIVE_COLUMNS = {
            Constants.COLUMN_WALLET,
            Constants.COLUMN_CURRENCY,
            Constants.COLUMN_CATEGORY,
            Constants.COLUMN_DATETIME,
            Constants.COLUMN_MONEY
    };

    public char separator = ',';
    public int date = NONE;
    public int amount = NONE;
    public int description = NONE;
    public int note = NONE;
    public int category = NONE;
    public String datePattern;
    public boolean decimalComma;
    public boolean spendingPositive;
    public long walletId;

    /** The first line of a file, as read to decide how to import it. */
    public static class Header {

        public final char separator;
        public final String[] cells;
        public final boolean nativeHeader;

        Header(char separator, String[] cells, boolean nativeHeader) {
            this.separator = separator;
            this.cells = cells;
            this.nativeHeader = nativeHeader;
        }
    }

    /**
     * The separator a header line uses: whichever of comma, semicolon and tab appears most often
     * outside double quotes. A tie, or none of them at all, is a comma.
     */
    public static char detectSeparator(String headerLine) {
        int commas = 0;
        int semicolons = 0;
        int tabs = 0;
        boolean quoted = false;
        for (int i = 0; i < headerLine.length(); i++) {
            char c = headerLine.charAt(i);
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted) {
                if (c == ',') {
                    commas++;
                } else if (c == ';') {
                    semicolons++;
                } else if (c == '\t') {
                    tabs++;
                }
            }
        }
        if (semicolons > commas && semicolons > tabs) {
            return ';';
        }
        if (tabs > commas && tabs > semicolons) {
            return '\t';
        }
        return ',';
    }

    /**
     * Reads the header of a file through the same opening the importer uses, so a byte order mark
     * is dropped here exactly as it is there, and a file with no characters in it is refused with
     * the same message.
     */
    public static Header readHeader(File file) throws IOException {
        try (BufferedReader reader = new BufferedReader(CSVDataImporter.openFile(file))) {
            // never null, since openFile refuses a file with nothing to read
            String line = reader.readLine();
            char separator = detectSeparator(line);
            String[] cells;
            try {
                cells = parser(separator).parseLine(line);
            } catch (IOException e) {
                throw new IOException("Line 1: a quote in this row is not closed on the same line,"
                        + " and every row has to fit on one line", e);
            }
            boolean nativeHeader;
            try {
                nativeHeader = isNativeHeader(line);
            } catch (IOException notReadableThatWay) {
                // a header the app's own import cannot read is not one it wrote, and the file is
                // still importable through a mapping
                nativeHeader = false;
            }
            return new Header(separator, cells, nativeHeader);
        }
    }

    /**
     * The parser for the header and the rows of a mapped file. A backslash is an ordinary
     * character here, as CSV escapes a quote by doubling it. With the opencsv default a backslash
     * is dropped, and one just before a closing quote joins the rows that follow into one.
     */
    static CSVParser parser(char separator) {
        return new CSVParserBuilder().withSeparator(separator).withEscapeChar(ICSVParser.NULL_CHARACTER).build();
    }

    /**
     * Whether a header line names every column this app's own import needs, read with a comma
     * the way that import reads it. Such a file goes through that import untouched.
     */
    public static boolean isNativeHeader(String headerLine) throws IOException {
        List<String> cells = Arrays.asList(new CSVParserBuilder().build().parseLine(headerLine));
        return cells.containsAll(Arrays.asList(NATIVE_COLUMNS));
    }

    /**
     * What a remembered mapping is stored under. Each cell is trimmed and lowercased, then the
     * cells are written as one quoted CSV line, so a cell holding a comma or a quote cannot make
     * two different headers read the same.
     */
    public static String signature(String[] headers) {
        String[] normalized = new String[headers.length];
        for (int i = 0; i < headers.length; i++) {
            normalized[i] = headers[i] == null ? "" : headers[i].trim().toLowerCase(Locale.ROOT);
        }
        return writeLine(normalized);
    }

    /**
     * The settings worth remembering for a header. The wallet is left out on purpose. Wallet row
     * ids are handed out again when a backup is restored, so a remembered id could quietly name a
     * different wallet from the one first picked.
     */
    public String encode() {
        return writeLine(new String[] {
                String.valueOf(date),
                String.valueOf(amount),
                String.valueOf(description),
                String.valueOf(note),
                String.valueOf(category),
                datePattern,
                String.valueOf(decimalComma),
                String.valueOf(spendingPositive)
        });
    }

    /** The settings {@link #encode()} wrote, or null when the text is not something it wrote. */
    public static CsvImportMapping decode(String encoded) {
        if (encoded == null) {
            return null;
        }
        try {
            String[] cells = new CSVParserBuilder().build().parseLine(encoded);
            if (cells == null || cells.length != 8) {
                return null;
            }
            CsvImportMapping mapping = new CsvImportMapping();
            mapping.date = Integer.parseInt(cells[0]);
            mapping.amount = Integer.parseInt(cells[1]);
            mapping.description = Integer.parseInt(cells[2]);
            mapping.note = Integer.parseInt(cells[3]);
            mapping.category = Integer.parseInt(cells[4]);
            mapping.datePattern = cells[5];
            if (mapping.date < 0 || mapping.amount < 0 || mapping.description < NONE
                    || mapping.note < NONE || mapping.category < NONE
                    || !Arrays.asList(DATE_PATTERNS).contains(mapping.datePattern)) {
                return null;
            }
            mapping.decimalComma = parseBoolean(cells[6]);
            mapping.spendingPositive = parseBoolean(cells[7]);
            return mapping;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean parseBoolean(String value) {
        if ("true".equals(value)) {
            return true;
        }
        if ("false".equals(value)) {
            return false;
        }
        throw new IllegalArgumentException(value);
    }

    private static String writeLine(String[] cells) {
        StringWriter out = new StringWriter();
        try (CSVWriter writer = new CSVWriter(out, ',', '"', '"', "")) {
            writer.writeNext(cells, true);
        } catch (IOException e) {
            // a StringWriter does not fail
            throw new IllegalStateException(e);
        }
        return out.toString();
    }

    /**
     * An amount, read strictly. Grouping is accepted only in threes, because a loose read of
     * "12,50" in a file that uses a dot for decimals drops the comma and imports 1250, and nothing
     * shows the amount for review before it is saved. A space groups exactly where the chosen
     * style's mark would, and one amount groups with one or the other, never both. Anything that
     * is not clearly one number in the chosen style is refused.
     * <p>
     * Bank exports also write a negative in parentheses, as (12.50), or with a trailing minus, as
     * 12.50-, and put one currency symbol before or after the number, with at most one space
     * between. Each of these is recognized as a whole form and read for what it means, never
     * stripped as loose characters, because a parenthesized negative that lost its parentheses
     * would import as income. A negative written that way cannot also carry its own sign, since
     * (-5) or -5- has no one clear reading.
     */
    public static BigDecimal parseAmount(String cell, boolean decimalComma) {
        String number = cell;
        boolean negative = false;
        if (number.length() >= 2 && number.startsWith("(") && number.endsWith(")")) {
            negative = true;
            number = number.substring(1, number.length() - 1);
        } else if (number.endsWith("-")) {
            negative = true;
            number = number.substring(0, number.length() - 1);
        }
        Matcher symbol = LEADING_SYMBOL.matcher(number);
        if (symbol.matches()) {
            number = symbol.group(1) + symbol.group(2);
        } else if ((symbol = TRAILING_SYMBOL.matcher(number)).matches()) {
            number = symbol.group(1);
        }
        BigDecimal amount = null;
        if (!(negative && (number.startsWith("+") || number.startsWith("-")))) {
            String compact = number.replaceAll(SPACE, "");
            if (decimalComma && COMMA_AMOUNT.matcher(number).matches()) {
                amount = new BigDecimal(compact.replace(".", "").replace(',', '.'));
            } else if (!decimalComma && DOT_AMOUNT.matcher(number).matches()) {
                amount = new BigDecimal(compact.replace(",", ""));
            }
        }
        if (amount == null) {
            throw new RuntimeException("the amount \"" + cell + "\" is not a number written as "
                    + (decimalComma ? "1.234,56" : "1,234.56"));
        }
        return negative ? amount.negate() : amount;
    }

    /**
     * A date in one of {@link #DATE_PATTERNS}, alone or followed by a space and a time with or
     * without seconds, read strictly. The digit counts are checked first because the parse on
     * its own accepts a two digit year where four are asked for, and a lenient parse rolls a day
     * past the end of its month into the next one. A two digit year takes its century from
     * SimpleDateFormat's own window, which places it within 80 years before today and 20 after.
     */
    public static Date parseDate(String cell, String datePattern) {
        int index = Arrays.asList(DATE_PATTERNS).indexOf(datePattern);
        if (index < 0) {
            throw new IllegalArgumentException("unknown date format " + datePattern);
        }
        if (cell.matches(DATE_DIGITS[index] + TIME_DIGITS)) {
            String pattern = datePattern;
            int colons = cell.length() - cell.replace(":", "").length();
            if (colons == 1) {
                pattern += " HH:mm";
            } else if (colons == 2) {
                pattern += " HH:mm:ss";
            }
            SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.ENGLISH);
            format.setLenient(false);
            ParsePosition position = new ParsePosition(0);
            Date parsed = format.parse(cell, position);
            if (parsed != null && position.getIndex() == cell.length()) {
                return parsed;
            }
        }
        throw new RuntimeException("the date \"" + cell + "\" is not a real date written as "
                + datePattern + ", with or without a time");
    }
}
