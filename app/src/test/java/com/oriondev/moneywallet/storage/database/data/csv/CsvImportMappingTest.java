package com.oriondev.moneywallet.storage.database.data.csv;

import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Everything a mapped import decides before it saves a row. The import itself reads the picked
 * wallet from a database, so CsvMappedImportTest drives it.
 */
public class CsvImportMappingTest {

    @Test
    public void theSeparatorIsTheOneUsedMostOutsideQuotes() {
        assertEquals(',', CsvImportMapping.detectSeparator("date,amount,memo"));
        assertEquals(';', CsvImportMapping.detectSeparator("date;amount;memo"));
        assertEquals('\t', CsvImportMapping.detectSeparator("date\tamount\tmemo"));
        assertEquals(',', CsvImportMapping.detectSeparator("\"a;b;c\",memo"));
        assertEquals(';', CsvImportMapping.detectSeparator("\"a,b,c\";amount;memo"));
    }

    @Test
    public void aTieOrNoSeparatorAtAllIsAComma() {
        assertEquals(',', CsvImportMapping.detectSeparator("date,amount;memo"));
        assertEquals(',', CsvImportMapping.detectSeparator("date;amount\tmemo"));
        assertEquals(',', CsvImportMapping.detectSeparator("date"));
        assertEquals(',', CsvImportMapping.detectSeparator(""));
    }

    @Test
    public void aHeaderIsReadPastItsMarkWithTheSeparatorItUses() throws IOException {
        File file = write("﻿Date;Amount;\"Memo; long\"\n01/02/2026;12,50;x\n");
        CsvImportMapping.Header header = CsvImportMapping.readHeader(file);
        assertEquals(';', header.separator);
        assertArrayEquals(new String[] {"Date", "Amount", "Memo; long"}, header.cells);
        assertFalse(header.nativeHeader);
    }

    @Test
    public void aHeaderThisAppWroteIsNative() throws IOException {
        File file = write("﻿\"wallet\",\"currency\",\"category\",\"datetime\",\"money\",\"note\"\n");
        assertTrue(CsvImportMapping.readHeader(file).nativeHeader);
    }

    /**
     * A quoted cell whose last character is a backslash, as a Windows path is written. The app's
     * own import reads a backslash as an escape, so it cannot read this line at all, and the file
     * used to be refused outright under the reader's own wording about an unterminated field.
     */
    @Test
    public void aHeaderTheAppsOwnImportCannotReadIsNotNative() throws IOException {
        CsvImportMapping.Header header = CsvImportMapping.readHeader(
                write("Konto,\"Pfad\\\",Betrag,Text\n01/02/2026,x,12.50,y\n"));
        assertArrayEquals(new String[] {"Konto", "Pfad\\", "Betrag", "Text"}, header.cells);
        assertFalse(header.nativeHeader);
        assertFalse(CsvImportMapping.readHeader(
                write("wallet,currency,category,datetime,money,\"Pfad\\\"\n")).nativeHeader);
        assertTrue(CsvImportMapping.readHeader(
                write("wallet,currency,category,datetime,money\n")).nativeHeader);
    }

    /** One stray quote is not a header line at all, and neither parser reads it. */
    @Test
    public void aHeaderWithASingleStrayQuoteIsStillRefused() throws IOException {
        File file = write("Konto,Pfa\"d,Betrag\n01/02/2026,x,12.50\n");
        try {
            CsvImportMapping.readHeader(file);
            fail("a header with a quote that is never closed has to be refused");
        } catch (IOException expected) {
            assertEquals("Line 1: a quote in this row is not closed on the same line, and every row has to fit on one line",
                    expected.getMessage());
        }
    }

    @Test
    public void aNativeHeaderNeedsAllFiveNamesSeparatedByCommas() throws IOException {
        assertTrue(CsvImportMapping.isNativeHeader("money,datetime,category,currency,wallet"));
        assertFalse(CsvImportMapping.isNativeHeader("wallet,currency,category,datetime"));
        assertFalse(CsvImportMapping.isNativeHeader("wallet;currency;category;datetime;money"));
        assertFalse(CsvImportMapping.isNativeHeader("Wallet,Currency,Category,Datetime,Money"));
    }

    @Test
    public void anEmptyFileIsRefusedWhenItsHeaderIsRead() throws IOException {
        try {
            CsvImportMapping.readHeader(write(""));
            fail("a file with nothing in it has to be refused");
        } catch (RuntimeException expected) {
            assertEquals("the file has no header line in it", expected.getMessage());
        }
    }

    @Test
    public void theSignatureIgnoresCaseAndSurroundingSpace() {
        assertEquals(CsvImportMapping.signature(new String[] {"date", "amount"}),
                CsvImportMapping.signature(new String[] {" Date ", "AMOUNT"}));
        assertNotEquals(CsvImportMapping.signature(new String[] {"date", "amount"}),
                CsvImportMapping.signature(new String[] {"amount", "date"}));
    }

    @Test
    public void aCellHoldingACommaOrAQuoteCannotCollide() {
        assertNotEquals(CsvImportMapping.signature(new String[] {"a,b", "c"}),
                CsvImportMapping.signature(new String[] {"a", "b,c"}));
        assertNotEquals(CsvImportMapping.signature(new String[] {"a\",\"b"}),
                CsvImportMapping.signature(new String[] {"a", "b"}));
        assertNotEquals(CsvImportMapping.signature(new String[] {""}),
                CsvImportMapping.signature(new String[] {"", ""}));
    }

    @Test
    public void theRememberedSettingsSurviveARoundTripWithoutTheWallet() {
        CsvImportMapping mapping = new CsvImportMapping();
        mapping.separator = ';';
        mapping.date = 3;
        mapping.amount = 0;
        mapping.description = CsvImportMapping.NONE;
        mapping.note = 2;
        mapping.category = 1;
        mapping.datePattern = "dd.MM.yyyy";
        mapping.decimalComma = true;
        mapping.spendingPositive = true;
        mapping.walletId = 42;
        CsvImportMapping decoded = CsvImportMapping.decode(mapping.encode());
        assertNotNull(decoded);
        assertEquals(3, decoded.date);
        assertEquals(0, decoded.amount);
        assertEquals(CsvImportMapping.NONE, decoded.description);
        assertEquals(2, decoded.note);
        assertEquals(1, decoded.category);
        assertEquals("dd.MM.yyyy", decoded.datePattern);
        assertTrue(decoded.decimalComma);
        assertTrue(decoded.spendingPositive);
        assertEquals(0, decoded.walletId);
        assertFalse(mapping.encode().contains("42"));
    }

    @Test
    public void somethingEncodeDidNotWriteDecodesToNull() {
        assertNull(CsvImportMapping.decode(null));
        assertNull(CsvImportMapping.decode(""));
        assertNull(CsvImportMapping.decode("garbage"));
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\""));
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\",\"x\""));
        assertNull(CsvImportMapping.decode("\"zero\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""));
        assertNull(CsvImportMapping.decode("\"-1\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""));
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-2\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""));
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yy-MM-dd\",\"false\",\"false\""));
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"yes\",\"false\""));
        assertNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false"));
        assertNotNull(CsvImportMapping.decode("\"0\",\"1\",\"-1\",\"-1\",\"-1\",\"yyyy-MM-dd\",\"false\",\"false\""));
    }

    @Test
    public void everyDateFormatReadsADateAlone() {
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("2026-08-12", "yyyy-MM-dd"));
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("12/08/2026", "dd/MM/yyyy"));
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("08/12/2026", "MM/dd/yyyy"));
        assertEquals(at(2026, 8, 12, 0, 0, 0), CsvImportMapping.parseDate("12.08.2026", "dd.MM.yyyy"));
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("2026-3-5", "yyyy-MM-dd"));
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("5/3/2026", "dd/MM/yyyy"));
    }

    @Test
    public void everyDateFormatReadsATimeWithAndWithoutSeconds() {
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("2026-08-12 09:30", "yyyy-MM-dd"));
        assertEquals(at(2026, 8, 12, 9, 30, 15), CsvImportMapping.parseDate("2026-08-12 09:30:15", "yyyy-MM-dd"));
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("12/08/2026 9:30", "dd/MM/yyyy"));
        assertEquals(at(2026, 8, 12, 21, 30, 15), CsvImportMapping.parseDate("12/08/2026 21:30:15", "dd/MM/yyyy"));
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("08/12/2026 09:30", "MM/dd/yyyy"));
        assertEquals(at(2026, 8, 12, 9, 30, 15), CsvImportMapping.parseDate("08/12/2026 09:30:15", "MM/dd/yyyy"));
        assertEquals(at(2026, 8, 12, 9, 30, 0), CsvImportMapping.parseDate("12.08.2026 09:30", "dd.MM.yyyy"));
        assertEquals(at(2026, 8, 12, 9, 30, 15), CsvImportMapping.parseDate("12.08.2026 09:30:15", "dd.MM.yyyy"));
    }

    @Test
    public void aDateThatIsNotRealInTheChosenFormatIsRefused() {
        refusesDate("2026-02-30", "yyyy-MM-dd");
        refusesDate("2026-13-01", "yyyy-MM-dd");
        refusesDate("12/31/2026", "dd/MM/yyyy");
        refusesDate("31/12/2026", "MM/dd/yyyy");
        refusesDate("08/12/26", "MM/dd/yyyy");
        refusesDate("12/08/26", "dd/MM/yyyy");
        refusesDate("26-08-12", "yyyy-MM-dd");
        refusesDate("12.08.26", "dd.MM.yyyy");
        refusesDate("12026-08-12", "yyyy-MM-dd");
        refusesDate("2026-08-12x", "yyyy-MM-dd");
        refusesDate("2026-08-12T09:30", "yyyy-MM-dd");
        refusesDate("2026-08-12 09:30:15 UTC", "yyyy-MM-dd");
        refusesDate("2026-08-12  09:30", "yyyy-MM-dd");
        refusesDate("2026-08-12 24:00", "yyyy-MM-dd");
        refusesDate("2026-08-12 09:3", "yyyy-MM-dd");
        refusesDate("12.08.2026", "dd/MM/yyyy");
        refusesDate("", "yyyy-MM-dd");
    }

    /** SimpleDateFormat puts a two digit year within 80 years before today and 20 after. */
    @Test
    public void aTwoDigitYearTakesItsCenturyFromTheWindow() {
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("05/03/26", "dd/MM/yy"));
        assertEquals(at(1999, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("03/05/99", "MM/dd/yy"));
        assertEquals(at(2026, 3, 5, 0, 0, 0), CsvImportMapping.parseDate("05.03.26", "dd.MM.yy"));
        refusesDate("31/02/26", "dd/MM/yy");
        refusesDate("05/03/2026", "dd/MM/yy");
        refusesDate("03/05/1999", "MM/dd/yy");
        refusesDate("05.03.2026", "dd.MM.yy");
    }

    @Test
    public void anAmountWithADotForDecimals() {
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1234.56", false));
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1,234.56", false));
        assertEquals(new BigDecimal("-1234567.8"), CsvImportMapping.parseAmount("-1,234,567.8", false));
        assertEquals(new BigDecimal("12"), CsvImportMapping.parseAmount("+12", false));
        assertEquals(new BigDecimal("1234"), CsvImportMapping.parseAmount("1,234", false));
        assertEquals(new BigDecimal("1234.5"), CsvImportMapping.parseAmount("1 234.5", false));
        assertEquals(new BigDecimal("1234.5"), CsvImportMapping.parseAmount("1 234.5", false));
    }

    /** A space groups digits only where the style's own mark would, and never beside it. */
    @Test
    public void aSpaceGroupsThousandsInThreesInEitherStyle() {
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1 234.56", false));
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1 234,56", true));
        assertEquals(new BigDecimal("12345678"), CsvImportMapping.parseAmount("12 345 678", false));
        assertEquals(new BigDecimal("12345678"), CsvImportMapping.parseAmount("12 345 678", true));
        assertEquals(new BigDecimal("-1234.5"), CsvImportMapping.parseAmount("-1 234.5", false));
        for (boolean decimalComma : new boolean[] {false, true}) {
            refusesAmount("12 50", decimalComma);
            refusesAmount("12 50", decimalComma);
            refusesAmount("1 2 3.4", decimalComma);
            refusesAmount("1 2 3,4", decimalComma);
            refusesAmount("1234 567", decimalComma);
            refusesAmount("1 2345", decimalComma);
            refusesAmount("- 1 234", decimalComma);
            refusesAmount("1 234 ", decimalComma);
        }
        refusesAmount("1 234,567.89", false);
        refusesAmount("1 234.567,89", true);
    }

    @Test
    public void anAmountWithACommaForDecimals() {
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1234,56", true));
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1.234,56", true));
        assertEquals(new BigDecimal("-1234567.8"), CsvImportMapping.parseAmount("-1.234.567,8", true));
        assertEquals(new BigDecimal("-12.50"), CsvImportMapping.parseAmount("-12,50", true));
        assertEquals(new BigDecimal("12"), CsvImportMapping.parseAmount("+12", true));
        assertEquals(new BigDecimal("1234.5"), CsvImportMapping.parseAmount("1 234,5", true));
    }

    /** The reason the reading is strict. A loose read of these would import a different number. */
    @Test
    public void anAmountInTheOtherStyleIsRefused() {
        refusesAmount("12,50", false);
        refusesAmount("1.234,56", false);
        refusesAmount("1,234.56", true);
        refusesAmount("12.50", true);
    }

    @Test
    public void anAmountThatIsNotClearlyOneNumberIsRefused() {
        refusesAmount("1,23.4", false);
        refusesAmount("1,2345", false);
        refusesAmount("1234,567.8", false);
        refusesAmount("12.", false);
        refusesAmount(".5", false);
        refusesAmount("--1", false);
        refusesAmount("1e3", false);
        refusesAmount("", false);
        refusesAmount("1.23,4", true);
        refusesAmount("12,", true);
    }

    @Test
    public void aCurrencySymbolBeforeOrAfterTheNumberIsDropped() {
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("$1,234.56", false));
        assertEquals(new BigDecimal("12.00"), CsvImportMapping.parseAmount("$12.00", false));
        assertEquals(new BigDecimal("-5"), CsvImportMapping.parseAmount("-$5", false));
        assertEquals(new BigDecimal("-5"), CsvImportMapping.parseAmount("$-5", false));
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1.234,56 €", true));
        assertEquals(new BigDecimal("-1234.56"), CsvImportMapping.parseAmount("-1.234,56 €", true));
        assertEquals(new BigDecimal("1234.56"), CsvImportMapping.parseAmount("1.234,56 €", true));
        assertEquals(new BigDecimal("5"), CsvImportMapping.parseAmount("£5", false));
        assertEquals(new BigDecimal("1234"), CsvImportMapping.parseAmount("¥1,234", false));
    }

    @Test
    public void anAmountInParenthesesIsNegative() {
        assertEquals(new BigDecimal("-12.50"), CsvImportMapping.parseAmount("(12.50)", false));
        assertEquals(new BigDecimal("-1234.56"), CsvImportMapping.parseAmount("($1,234.56)", false));
    }

    @Test
    public void anAmountWithATrailingMinusIsNegative() {
        assertEquals(new BigDecimal("-1234.56"), CsvImportMapping.parseAmount("1234.56-", false));
        assertEquals(new BigDecimal("-1234.56"), CsvImportMapping.parseAmount("1.234,56 €-", true));
    }

    /** Two signs, two symbols, or a symbol that is really a code have no one clear reading. */
    @Test
    public void aSignOrSymbolThatIsNotClearIsRefused() {
        for (String cell : new String[] {"(-5)", "-5-", "+5-", "$5$", "5- €", "USD 5", "R$ 5", "CHF 5",
                "$ $5", "$  5", "5  $", "()", "-", "$"}) {
            refusesAmount(cell, false);
        }
    }

    /** Grouping never starts with 0, so these used to import 500 and 250. */
    @Test
    public void aThousandsGroupStartingWithZeroIsRefused() {
        refusesAmount("0,500", false);
        refusesAmount("00,250", false);
        refusesAmount("0 500", false);
        refusesAmount("0.500", true);
        refusesAmount("0 500", true);
        assertEquals(new BigDecimal("1500"), CsvImportMapping.parseAmount("1,500", false));
        assertEquals(new BigDecimal("1500"), CsvImportMapping.parseAmount("1.500", true));
        assertEquals(new BigDecimal("0.50"), CsvImportMapping.parseAmount("0.50", false));
        assertEquals(new BigDecimal("0.50"), CsvImportMapping.parseAmount("0,50", true));
        assertEquals(new BigDecimal("10000"), CsvImportMapping.parseAmount("10,000", false));
    }

    private static void refusesDate(String cell, String pattern) {
        try {
            Date parsed = CsvImportMapping.parseDate(cell, pattern);
            fail(cell + " has to be refused as " + pattern + ", but it read as " + parsed);
        } catch (RuntimeException expected) {
            assertEquals("the date \"" + cell + "\" is not a real date written as " + pattern
                    + ", with or without a time", expected.getMessage());
        }
    }

    private static void refusesAmount(String cell, boolean decimalComma) {
        try {
            BigDecimal parsed = CsvImportMapping.parseAmount(cell, decimalComma);
            fail(cell + " has to be refused, but it read as " + parsed);
        } catch (RuntimeException expected) {
            assertEquals("the amount \"" + cell + "\" is not a number written as "
                    + (decimalComma ? "1.234,56" : "1,234.56"), expected.getMessage());
        }
    }

    private static File write(String contents) throws IOException {
        File file = File.createTempFile("tallybook-mapped-import", ".csv");
        file.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write(contents);
        }
        return file;
    }

    private static Date at(int year, int month, int day, int hour, int minute, int second) {
        Calendar calendar = new GregorianCalendar(year, month - 1, day, hour, minute, second);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
}
