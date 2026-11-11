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

package com.oriondev.moneywallet.background;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The three report loaders each build their own selection string, so the rule about a category
 * kept out of the reports lives in one constant and is appended three times. SQLDatabaseTest
 * covers what that constant selects. Nothing covers whether a loader still appends it, and a
 * loader that stops puts back the defect where one period reads differently on the overview list,
 * on the two flow tabs and on the summary tab.
 *
 * The same holds for NOT_TRANSFER, which keeps money moved between two of the user's own wallets
 * out of a figure that sums one direction on its own. Two of the three append it, and the third
 * shows a net and classifies its rows in Kotlin, so it is absent there on purpose.
 *
 * This reads the source, so it is a tripwire against a revert and not a proof of behavior. It
 * cannot see a loader that appends the filter and then reassigns selection underneath it, or one
 * that builds a string it never queries with, because no loader is executed here.
 *
 * Comments are stripped before anything is matched, so the assertion cannot be satisfied by a
 * comment naming the constant the loader no longer appends. String literals are NOT stripped,
 * unlike the precedent, because the statement being matched contains one. Whitespace is collapsed,
 * so the statement may be wrapped, but the spelling is pinned exactly and a respelling fails with
 * no defect present. A failure means read the loader and decide.
 */
public class ReportLoaderSourceTest {

    private static final String[] LOADERS = new String[] {
            "OverviewDataLoader",
            "PeriodDetailFlowLoader",
            "PeriodDetailSummaryLoader"
    };

    private static final String APPEND =
            "selection += \" AND \" + Contract.Transaction.REPORT_FILTER";

    private static final String NOT_TRANSFER = "AND \" + Contract.Transaction.NOT_TRANSFER";

    @Test
    public void everyReportLoaderAppliesTheReportFilter() {
        for (String loader : LOADERS) {
            assertTrue("a report screen that stops filtering on REPORT_FILTER counts a category "
                    + "kept out of the reports again, and disagrees with the other two: " + loader,
                    endsAStatement(readSource(loader), APPEND));
        }
    }

    /**
     * The count is the assertion, not the presence. OverviewDataLoader appends it on the incomes
     * chart and again on the expenses chart, so one branch losing it while the other keeps it is
     * what a presence check would miss.
     */
    @Test
    public void everyScreenThatSumsOneDirectionHoldsTransfersOut() {
        assertEquals("a chart that stops filtering on NOT_TRANSFER plots both halves of a "
                + "transfer as money earned and money spent, and disagrees with the header on "
                + "the transactions list: OverviewDataLoader",
                2, countOf(NOT_TRANSFER, readSource("OverviewDataLoader")));
        assertEquals("a tab that stops filtering on NOT_TRANSFER counts both halves of a "
                + "transfer into its total and onto its pie, and disagrees with the header on "
                + "the transactions list: PeriodDetailFlowLoader",
                1, countOf(NOT_TRANSFER, readSource("PeriodDetailFlowLoader")));
    }

    private boolean endsAStatement(String source, String statement) {
        String flat = source.replace('\n', ' ');
        int at = flat.indexOf(statement);
        while (at >= 0) {
            if (isStatementEnd(source, at + statement.length())) {
                return true;
            }
            at = flat.indexOf(statement, at + 1);
        }
        return false;
    }

    private boolean isStatementEnd(String source, int at) {
        if (at < source.length() && source.charAt(at) == ' ') {
            at++;
        }
        if (at == source.length()) {
            return true;
        }
        char c = source.charAt(at);
        if (c == ';' || c == '}') {
            return true;
        }
        if (c != '\n') {
            return false;
        }
        return at + 1 == source.length() || ".?&|".indexOf(source.charAt(at + 1)) < 0;
    }

    private int countOf(String statement, String text) {
        String source = text.replace('\n', ' ');
        int count = 0;
        int at = source.indexOf(statement);
        while (at >= 0) {
            count++;
            at = source.indexOf(statement, at + statement.length());
        }
        return count;
    }

    private String readSource(String loader) {
        String path = "src/main/java/com/oriondev/moneywallet/background/" + loader + ".kt";
        File file = new File(path);
        if (!file.exists()) {
            // a runner rooted at the repo root instead of the module, which the precedent
            // handles the same way
            file = new File("app/" + path);
        }
        if (!file.exists()) {
            fail("Cannot find " + loader + ".kt at " + file.getAbsolutePath());
        }
        try {
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return stripComments(source).replaceAll("[^\\S\\n]*\\n\\s*", "\n").replaceAll("[^\\S\\n]+", " ");
        } catch (IOException e) {
            fail("Cannot read " + loader + ".kt: " + e.getMessage());
            return null;
        }
    }

    private static String stripComments(String source) {
        StringBuilder stripped = new StringBuilder();
        int at = 0;
        while (at < source.length()) {
            int end = literalEnd(source, at);
            if (end >= 0) {
                stripped.append(source, at, end);
                at = end;
                continue;
            }
            end = commentEnd(source, at);
            if (end >= 0) {
                stripped.append(' ');
                at = end;
            } else {
                stripped.append(source.charAt(at));
                at++;
            }
        }
        return stripped.toString();
    }

    private static int literalEnd(String source, int at) {
        if (source.startsWith("\"\"\"", at)) {
            return rawStringEnd(source, at + 3);
        }
        char c = source.charAt(at);
        if (c == '"') {
            return stringEnd(source, at + 1);
        }
        if (c == '\'') {
            return charEnd(source, at + 1);
        }
        return -1;
    }

    private static int commentEnd(String source, int at) {
        if (source.startsWith("//", at)) {
            int newline = source.indexOf('\n', at);
            return newline < 0 ? source.length() : newline;
        }
        if (source.startsWith("/*", at)) {
            return blockCommentEnd(source, at);
        }
        return -1;
    }

    private static int rawStringEnd(String source, int at) {
        while (at < source.length()) {
            if (source.startsWith("\"\"\"", at)) {
                at += 3;
                while (at < source.length() && source.charAt(at) == '"') {
                    at++;
                }
                return at;
            }
            if (source.startsWith("${", at)) {
                at = templateEnd(source, at + 2);
            } else {
                at++;
            }
        }
        return source.length();
    }

    private static int stringEnd(String source, int at) {
        while (at < source.length()) {
            char c = source.charAt(at);
            if (c == '\\') {
                at += 2;
            } else if (c == '"') {
                return at + 1;
            } else if (source.startsWith("${", at)) {
                at = templateEnd(source, at + 2);
            } else if (c == '\n') {
                return at;
            } else {
                at++;
            }
        }
        return source.length();
    }

    private static int templateEnd(String source, int at) {
        int depth = 1;
        while (at < source.length()) {
            int end = literalEnd(source, at);
            if (end < 0) {
                end = commentEnd(source, at);
            }
            if (end >= 0) {
                at = end;
                continue;
            }
            char c = source.charAt(at);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return at + 1;
                }
            }
            at++;
        }
        return source.length();
    }

    private static int charEnd(String source, int at) {
        if (at < source.length() && source.charAt(at) == '\\') {
            at += 2;
        } else {
            at++;
        }
        while (at < source.length() && source.charAt(at) != '\'' && source.charAt(at) != '\n') {
            at++;
        }
        return at < source.length() && source.charAt(at) == '\'' ? at + 1 : at;
    }

    private static int blockCommentEnd(String source, int at) {
        int depth = 0;
        while (at < source.length()) {
            if (source.startsWith("/*", at)) {
                depth++;
                at += 2;
            } else if (source.startsWith("*/", at)) {
                depth--;
                at += 2;
                if (depth == 0) {
                    return at;
                }
            } else {
                at++;
            }
        }
        return source.length();
    }

}
