package com.oriondev.moneywallet.storage.database.data.csv;

import com.opencsv.CSVReaderHeaderAware;
import com.oriondev.moneywallet.utils.DateUtils;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Saving a row talks to a content resolver, and reading a currency out of a row talks to a static
 * the app sets up at startup, so neither can be driven from a JVM test. What is here reaches
 * neither, including a refusal that fires before the currency is read, which is enough to drive a
 * real import as far as its first row.
 *
 * What is not covered: saving a row, anything past the currency lookup in a row that is otherwise
 * good, and therefore the reader the saving pass uses, which is the one the constructor opens.
 */
public class CSVDataImporterTest {

    @Test
    public void aDatetimeKeepsItsTime() {
        assertEquals(at(2026, 8, 12, 9, 30, 15), CSVDataImporter.parseDatetime("2026-08-12 09:30:15"));
    }

    /**
     * Midnight in the zone the device is in. On the handful of days a zone skips midnight for
     * daylight saving, the lenient parse answers 01:00 on the same day instead.
     */
    @Test
    public void aDateOnItsOwnIsMidnight() {
        assertEquals(at(2026, 8, 12, 0, 0, 0), CSVDataImporter.parseDatetime("2026-08-12"));
    }

    /**
     * A fact about DateUtils, not a check on the importer: nothing done to the importer can turn
     * this red. It is here because it is the reason parseDatetime cannot just read the date and
     * stop. The test that goes red if the comparison in parseDatetime is removed is
     * aDayThatDoesNotExistIsRefusedWhenTheRowHasNoTime below, checked by removing it.
     */
    @Test
    public void theDateParseDropsTheTimeFromAFullDatetime() {
        assertEquals(at(2026, 8, 12, 0, 0, 0),
                DateUtils.getDateFromSQLDateString("2026-08-12 09:30:15"));
    }

    /**
     * The date parse stops at the first character it cannot use, so the first three of these
     * would read as a date with whatever follows it dropped, and the fourth as August 1st. None
     * is ten characters, so in practice the length refuses all four before the comparison is
     * reached; take the length away and the comparison refuses all four on its own. All four were
     * refused outright before a date on its own was accepted.
     */
    @Test
    public void aDateWithAnythingElseOnItIsRefused() {
        refuses("2026-08-12T09:30:15");
        refuses("2026-08-12 09:30");
        refuses("2026-08-12garbage");
        refuses("2026-8-1");
    }

    /**
     * Only when the row carries no time. A day past the end of its month is still rolled when a
     * time is written after it, because that value goes through the datetime parse, which is
     * untouched here and behaves as it always has: "2026-02-30 00:00:00" imports as March 2nd.
     * Tightening that would stop files importing that import today.
     */
    @Test
    public void aDayThatDoesNotExistIsRefusedWhenTheRowHasNoTime() {
        refuses("2026-02-30");
        refuses("2026-13-45");
        refuses("2026-00-00");
    }

    /**
     * A year past 9999 is written back out at its own length, so it compares equal to itself and
     * the comparison alone lets it through. It is the one thing the length is there for. A short
     * year is not: it is padded out to four digits, so "926-08-12" fails the comparison and is
     * refused whether the length is checked or not.
     */
    @Test
    public void aYearPastFourDigitsIsRefused() {
        refuses("12026-08-12");
        refuses("926-08-12");
    }

    /**
     * The sorted list in the expected message is load bearing, not decoration. A hash table hands
     * these two back as wallet then currency, so an assertion written the other way around goes
     * red the moment the sort is taken out of the refusal.
     */
    @Test
    public void aMissingColumnAndAnEmptyCellAreDifferentMessages() {
        Map<String, String> row = new HashMap<>();
        row.put("wallet", "Probe");
        row.put("currency", "  ");
        assertEquals("Probe", CSVDataImporter.required(row, "wallet"));
        assertEquals("no money column was found. The columns read out of the header line, sorted, are [currency, wallet]",
                messageFrom(row, "money"));
        assertEquals("the currency is empty, and every row needs one", messageFrom(row, "currency"));
    }

    /**
     * The whole point of listing the columns: the person reading the message is looking at a
     * header that says wallet, and this is the only thing that tells them what the reader made
     * of it. A semicolon separated file is the case that reaches this, and the map it builds
     * carries one key holding the whole header line.
     */
    @Test
    public void aMissingColumnSaysWhatTheColumnsWereReadAs() {
        Map<String, String> row = new HashMap<>();
        row.put("wallet\";\"currency\";\"category", "Probe");
        assertEquals("no wallet column was found. The columns read out of the header line, sorted, are [wallet\";\"currency\";\"category]",
                messageFrom(row, "wallet"));
    }

    /**
     * A file that opens with a byte order mark. Leave the mark in place and it joins the first
     * header cell, so the wallet column is named something no lookup will ever ask for and every
     * row is refused. It reads the header through the same reader the importer opens the file
     * with, because that reader is the piece under test here. Driving a whole import over the
     * same file is a separate test below.
     */
    @Test
    public void aByteOrderMarkDoesNotHideTheFirstColumn() throws Exception {
        File file = write("\uFEFF\"wallet\",\"currency\"\n\"Probe\",\"EUR\"\n");
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(CSVDataImporter.openFile(file))) {
            assertEquals("Probe", reader.readMap().get("wallet"));
        }
    }

    /**
     * The opposite mutation from the test above, and it catches a different one: this file has no
     * mark, so leaving the mark in place leaves this test green. It goes red when the first
     * character is dropped whether or not it is a mark, which would cost every header without one
     * its first character.
     */
    @Test
    public void aFileWithoutOneKeepsItsFirstCharacter() throws Exception {
        File file = write("\"wallet\",\"currency\"\n\"Probe\",\"EUR\"\n");
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(CSVDataImporter.openFile(file))) {
            assertEquals("Probe", reader.readMap().get("wallet"));
        }
    }

    /**
     * A file that opens with two marks. One tool adding a mark to a file another tool already
     * marked leaves both, and stopping after the first leaves the second one joined to the wallet
     * column, so every row is refused for having no wallet column and nothing on screen shows why.
     */
    @Test
    public void everyMarkAtTheFrontComesOff() throws Exception {
        File file = write("\uFEFF\uFEFF\"wallet\",\"currency\"\n\"Probe\",\"EUR\"\n");
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(CSVDataImporter.openFile(file))) {
            assertEquals("Probe", reader.readMap().get("wallet"));
        }
    }

    /**
     * The one test that drives a real import, and the only one that goes red with the checking
     * pass changed to open the file directly. Every other test here stays green while a marked
     * file is refused again.
     *
     * It reaches the checking pass and no further, so the reader the constructor opens for the
     * saving pass is not covered by anything. Changing that one line to open the file directly
     * leaves every test here green, and a marked file then passes the check and fails on the save.
     * Covering it needs a content resolver, which is what none of this can reach.
     *
     * The row leaves its wallet cell empty, so the refusal fires on the empty cell before the
     * currency is looked up, and nothing an app startup would have set up is ever reached. That
     * refusal can only fire if the wallet column was found, which is the whole point: with the
     * mark still on the file the message is about a wallet column that was never found instead.
     *
     * The context is null because nothing between here and the refusal reads it. A valid row
     * would read it, since the check pass counts each one against its wallet, and this file's
     * only row is refused first.
     */
    @Test
    public void anImportOpensTheFileThroughOpenFile() throws IOException {
        File file = write("\uFEFF\"wallet\",\"currency\",\"category\",\"datetime\",\"money\"\n"
                + "\"\",\"EUR\",\"Food\",\"2026-08-12 09:00:00\",\"1.00\"\n");
        CSVDataImporter importer = new CSVDataImporter(null, file);
        try {
            importer.importData();
            fail("a row with an empty wallet cell has to be refused");
        } catch (RuntimeException expected) {
            assertEquals("Line 2: the wallet is empty, and every row needs one",
                    expected.getMessage());
        } finally {
            importer.close();
        }
    }

    /**
     * A file holding a mark and nothing else, and a file holding nothing at all. Neither has
     * anything the reader can take a header from, and the reader asks the header for its length
     * without checking that there was one. The empty file has always reached the failure dialog
     * as a null pointer message. The mark only file reported a successful import of nothing until
     * the mark started being dropped, which put it in the same case as the empty one.
     *
     * Both hold no characters at all once the mark is gone, which is what the refusal checks for.
     * A file holding a line ending and nothing else is not covered and still reports a successful
     * import of nothing.
     */
    @Test
    public void aFileWithNoCharactersInItIsRefusedInWordsAPersonCanRead() throws IOException {
        assertEquals("the file has no header line in it", refusedContents("\uFEFF"));
        assertEquals("the file has no header line in it", refusedContents(""));
    }

    private static File write(String contents) throws IOException {
        File file = File.createTempFile("tallybook-import", ".csv");
        file.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write(contents);
        }
        return file;
    }

    private static String refusedContents(String contents) throws IOException {
        File file = write(contents);
        try {
            CSVDataImporter.openFile(file).close();
            fail("a file with no header line has to be refused");
            return null;
        } catch (RuntimeException expected) {
            return expected.getMessage();
        }
    }

    private static String messageFrom(Map<String, String> row, String column) {
        try {
            CSVDataImporter.required(row, column);
            fail(column + " has to be refused");
            return null;
        } catch (RuntimeException e) {
            return e.getMessage();
        }
    }

    @Test
    public void theMessageNamesTheValueAndBothFormats() {
        try {
            CSVDataImporter.parseDatetime("12/08/2026");
            fail("a date in another format has to be refused");
        } catch (RuntimeException e) {
            assertEquals("this message is what the failure dialog is built around",
                    "the datetime \"12/08/2026\" is not a real date as yyyy-MM-dd HH:mm:ss "
                            + "or yyyy-MM-dd",
                    e.getMessage());
        }
    }

    /**
     * Checks the whole message, not just that something was thrown. Checking only that the message
     * names the value is not enough: the exception DateUtils throws names it too, so these tests
     * would stay green with the date reading taken out altogether.
     */
    private static void refuses(String value) {
        try {
            Date parsed = CSVDataImporter.parseDatetime(value);
            fail(value + " has to be refused, but it read as " + DateUtils.getSQLDateTimeString(parsed));
        } catch (RuntimeException expected) {
            assertEquals("refused, but not by the importer's own check",
                    "the datetime \"" + value + "\" is not a real date as yyyy-MM-dd HH:mm:ss "
                            + "or yyyy-MM-dd",
                    expected.getMessage());
        }
    }

    private static Date at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = new GregorianCalendar(year, month - 1, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
