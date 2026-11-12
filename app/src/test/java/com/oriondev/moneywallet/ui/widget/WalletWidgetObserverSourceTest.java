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

package com.oriondev.moneywallet.ui.widget;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * One registration that nothing which runs can check, pinned as text the way
 * DataContentProviderTransactionSourceTest pins what the provider's write methods must be reached
 * by. There is no way to ask the framework what it was asked to watch, and the instrumented case
 * that registers the same way pins what the framework does with those arguments and not that this
 * file passes them.
 *
 * Both arguments are all or nothing. Nothing announces CONTENT_ALL itself, so without the
 * descendants flag the widget is told about no write at all, and a narrower uri is the list this
 * change deleted coming back one entity at a time. Neither failure says anything at run time, the
 * widget just holds the figure it was drawn with.
 *
 * The lock in MultiUriCursorWrapper.registerContentObserver is NOT pinned here, and a case for it
 * was written and taken out again. The property is mutual exclusion between two methods, and a
 * check that reads the file can only see a token: three separate ways were found to leave that
 * token in place with the exclusion gone, and each pattern written to close one of them let
 * another through or failed on code that was right. A check that says a property holds when it
 * does not is worse than not having one, so what guards that lock is the comment on it.
 *
 * Comments and literals are stripped before matching, for the reason the precedent gives: an
 * assertion here must not be satisfied by a comment describing code the file no longer has. What
 * is matched allows any spacing, so rewrapping a line is not a failure. What it cannot see is a
 * call that is there and never reached.
 *
 * A failure means read the source and decide, not that the app is broken.
 */
public class WalletWidgetObserverSourceTest {

    private static final Pattern WATCHES_THE_WHOLE_PROVIDER = Pattern.compile(
            "registerContentObserver\\(\\s*DataContentProvider\\.CONTENT_ALL\\s*,\\s*true\\s*,");

    /** The leading dot keeps unregisterContentObserver out, which contains the rest of this. */
    private static final Pattern ANY_REGISTRATION = Pattern.compile(
            "\\.\\s*registerContentObserver\\s*\\(");

    /**
     * A Kotlin raw string, a Kotlin string literal, a char literal, a line comment, then a block
     * comment, nested or not. The literals go
     * first so that a comment marker inside one cannot cut the line short, and the char literal is
     * in the list so that a lone quote written as a character cannot invert the quote parity of
     * everything below it and stop comments being stripped at all.
     */
    private static String strip(String source) {
        StringBuilder stripped = new StringBuilder();
        int at = 0;
        while (at < source.length()) {
            int end = skipped(source, at);
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

    private static int skipped(String source, int at) {
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
            int end = skipped(source, at);
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

    private String source() {
        String path = "src/main/java/com/oriondev/moneywallet/ui/widget/WalletWidgetObserver.kt";
        File file = new File(path);
        if (!file.exists()) {
            // a runner rooted at the repo root instead of the module, which the precedent
            // handles the same way
            file = new File("app/" + path);
        }
        if (!file.exists()) {
            fail("Cannot find " + path + " at " + file.getAbsolutePath());
        }
        try {
            String read = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return strip(read);
        } catch (IOException e) {
            fail("Cannot read " + path + ": " + e.getMessage());
            return null;
        }
    }

    @Test
    public void theWidgetWatchesTheWholeProviderAndAsksForWhatIsUnderIt() {
        assertTrue("the widget no longer registers on the whole provider with descendants "
                        + "included. Nothing announces CONTENT_ALL itself, so without the flag it "
                        + "is told about nothing at all, and with a narrower uri it is told about "
                        + "nothing outside that one",
                WATCHES_THE_WHOLE_PROVIDER.matcher(source()).find());
    }

    @Test
    public void theWidgetRegistersOnce() {
        Matcher matcher = ANY_REGISTRATION.matcher(source());
        int registrations = 0;
        while (matcher.find()) {
            registrations++;
        }
        assertTrue("the widget registers on nothing at all", registrations > 0);
        // a second registration is how the deleted list would grow back, and it would also mean
        // two redraws asked for per write
        assertEquals("the widget registers on something as well as the whole provider",
                1, registrations);
    }
}
