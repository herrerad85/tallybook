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

package com.oriondev.moneywallet.background

import com.oriondev.moneywallet.testing.SourceScanner

import org.junit.Test

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail

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
class ReportLoaderSourceTest {

    companion object {

        private val LOADERS = arrayOf(
                "OverviewDataLoader",
                "PeriodDetailFlowLoader",
                "PeriodDetailSummaryLoader"
        )

        private const val APPEND =
                "selection += \" AND \" + Contract.Transaction.REPORT_FILTER"

        private const val NOT_TRANSFER = "AND \" + Contract.Transaction.NOT_TRANSFER"
    }

    @Test
    fun everyReportLoaderAppliesTheReportFilter() {
        for (loader in LOADERS) {
            assertTrue("a report screen that stops filtering on REPORT_FILTER counts a category "
                    + "kept out of the reports again, and disagrees with the other two: " + loader,
                    endsAStatement(readSource(loader)!!, APPEND))
        }
    }

    /**
     * The count is the assertion, not the presence. OverviewDataLoader appends it on the incomes
     * chart and again on the expenses chart, so one branch losing it while the other keeps it is
     * what a presence check would miss.
     */
    @Test
    fun everyScreenThatSumsOneDirectionHoldsTransfersOut() {
        assertEquals("a chart that stops filtering on NOT_TRANSFER plots both halves of a "
                + "transfer as money earned and money spent, and disagrees with the header on "
                + "the transactions list: OverviewDataLoader",
                2, countOf(NOT_TRANSFER, readSource("OverviewDataLoader")!!))
        assertEquals("a tab that stops filtering on NOT_TRANSFER counts both halves of a "
                + "transfer into its total and onto its pie, and disagrees with the header on "
                + "the transactions list: PeriodDetailFlowLoader",
                1, countOf(NOT_TRANSFER, readSource("PeriodDetailFlowLoader")!!))
    }

    private fun endsAStatement(source: String, statement: String): Boolean {
        val flat = source.replace('\n', ' ')
        var at = flat.indexOf(statement)
        while (at >= 0) {
            if (isStatementEnd(source, at + statement.length)) {
                return true
            }
            at = flat.indexOf(statement, at + 1)
        }
        return false
    }

    private fun isStatementEnd(source: String, at: Int): Boolean {
        var at = at
        if (at < source.length && source[at] == ' ') {
            at++
        }
        if (at == source.length) {
            return true
        }
        val c = source[at]
        if (c == ';' || c == '}') {
            return true
        }
        if (c != '\n') {
            return false
        }
        return at + 1 == source.length || ".?&|".indexOf(source[at + 1]) < 0
    }

    private fun countOf(statement: String, text: String): Int {
        val source = text.replace('\n', ' ')
        var count = 0
        var at = source.indexOf(statement)
        while (at >= 0) {
            count++
            at = source.indexOf(statement, at + statement.length)
        }
        return count
    }

    private fun readSource(loader: String): String? {
        val path = "src/main/java/com/oriondev/moneywallet/background/" + loader + ".kt"
        var file = File(path)
        if (!file.exists()) {
            // a runner rooted at the repo root instead of the module, which the precedent
            // handles the same way
            file = File("app/" + path)
        }
        if (!file.exists()) {
            fail("Cannot find " + loader + ".kt at " + file.absolutePath)
        }
        try {
            val source = String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8)
            return stripComments(source).replace(Regex("[^\\S\\n]*\\n\\s*"), "\n").replace(Regex("[^\\S\\n]+"), " ")
        } catch (e: IOException) {
            fail("Cannot read " + loader + ".kt: " + e.message)
            return null
        }
    }

    private fun stripComments(source: String): String {
        val stripped = StringBuilder()
        var at = 0
        while (at < source.length) {
            var end = SourceScanner.literalEnd(source, at)
            if (end >= 0) {
                stripped.append(source, at, end)
                at = end
                continue
            }
            end = SourceScanner.commentEnd(source, at)
            if (end >= 0) {
                stripped.append(' ')
                at = end
            } else {
                stripped.append(source[at])
                at++
            }
        }
        return stripped.toString()
    }

}
