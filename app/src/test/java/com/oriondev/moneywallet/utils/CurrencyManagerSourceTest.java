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

package com.oriondev.moneywallet.utils;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The deadlock this class was rewritten to remove needed two things at once: a reader of the cache
 * that could block on a lock, and a reload that held that same lock while it read the database.
 * An importer runs a whole import inside one transaction, which holds the single SQLite connection
 * the app shares, and asks this class for a currency on every row. So the importer waited on the
 * lock while the reload waited on the connection.
 *
 * Only one of those two has to go for the cycle to be impossible, and it is the readers that went.
 * If nothing a thread inside a transaction can call acquires anything, that thread can never be
 * made to wait inside this class, whatever invalidateCache does. READERS below is every such
 * method, and it has to stay that way if a new one is added.
 *
 * invalidateCache does still hold a lock across the database, because two reloads have to be kept
 * from interleaving and nothing cheaper orders them correctly, and so does initialize, which does
 * the same load. That is safe only while the two things the second and third cases pin stay true:
 * nothing but those two takes that lock, and neither is called from inside a transaction. Both
 * refuse at runtime if anything does.
 *
 * What this is and is not. It reads the source and matches text, so it is a tripwire against the
 * old code coming back, not a proof that the new code is correct. Someone determined to reintroduce
 * a lock can pick a spelling none of these look for. The behavior that can be run instead is in
 * CurrencyManagerConcurrencyTest. Comments and string literals are stripped first, so nothing here
 * can be satisfied by a comment describing code the file no longer has, and whitespace is collapsed
 * so a statement may be wrapped, following ReportLoaderSourceTest.
 *
 * A failure means read the source and decide, not that the cache is broken.
 */
public class CurrencyManagerSourceTest {

    /** A java string literal, then a line comment, then a block comment. Literal first. */
    private static final Pattern STRIPPED = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\])*\"|//.*?$|/[*].*?[*]/",
            Pattern.DOTALL | Pattern.MULTILINE);

    /** Every way of taking a lock that this file could plausibly be rewritten to use. */
    private static final String[] WAYS_TO_TAKE_A_LOCK = {
            "synchronized", ".lock()", ".lockInterruptibly()", ".tryLock(", "Lock ",
    };

    private static String readSource() {
        return readSource("src/main/java/com/oriondev/moneywallet/utils/CurrencyManager.java");
    }

    private static String readSource(String path) {
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
            String source = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            return STRIPPED.matcher(source).replaceAll(" ").replaceAll("\\s+", " ");
        } catch (IOException e) {
            fail("Cannot read " + path + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * The signature and everything to its matching closing brace. Literals and comments are gone
     * by the time this runs and the file has no character literal, so every brace left is real.
     */
    private static String bodyOf(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(signature + " is gone", start >= 0);
        int depth = 0;
        for (int i = source.indexOf('{', start); i < source.length(); i++) {
            char character = source.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth == 0) {
                return source.substring(start, i + 1);
            }
        }
        fail("no closing brace found for " + signature);
        return null;
    }

    /** How many blocks deep a position sits inside a body. 1 is a statement of the method. */
    private static int depthOf(String body, int position) {
        int depth = 0;
        for (int i = 0; i < position; i++) {
            char character = body.charAt(i);
            if (character == '{') {
                depth++;
            } else if (character == '}') {
                depth--;
            }
        }
        return depth;
    }

    /** Every way out of this class that a thread inside a database transaction can reach. */
    private static final String[] READERS = {
            "public static CurrencyUnit getCurrency(String iso) {",
            "public static Collection<CurrencyUnit> getCurrencies() {",
            "public static CurrencyUnit getDefaultCurrency() {",
            "public static ExchangeRate getExchangeRate(CurrencyUnit currency1, CurrencyUnit currency2) {",
            "public static ExchangeRateCache getExchangeRateCache() {",
            "public static int getDecimals(CurrencyUnit currency) {",
    };

    /**
     * CurrencyManager.getExchangeRate is one of the READERS below and hands straight to
     * ExchangeRateCache, so the rule that a reader takes no lock has to hold one file down too.
     * These two cases are what holds it, since every case here reads CurrencyManager.java alone.
     */
    @Test
    public void theExchangeRateCacheIsAConcurrentMapAndNotAPlainOne() {
        String source = readSource(
                "src/main/java/com/oriondev/moneywallet/storage/cache/ExchangeRateCache.java");
        assertTrue("the exchange rate cache is no longer a ConcurrentHashMap. It is written by "
                        + "the rate download thread and read from the main thread on every "
                        + "keystroke, so a plain map can answer null for a rate it holds and can "
                        + "hand back a CacheObj whose fields are still at their defaults",
                source.contains("mCacheMemory = new ConcurrentHashMap<>();"));
        assertFalse("something in ExchangeRateCache builds a plain HashMap again",
                source.contains("new HashMap"));
        // the price of a map that refuses a null key, paid at all three places a key touches it.
        // A currency row can carry a null iso and CurrencyManager.getExchangeRate does not check
        // for one, so without these the throw replaces the null the plain map used to answer, and
        // on the load path it would take the app down at startup instead of on that row
        String reader = bodyOf(source,
                "public ExchangeRate getExchangeRate(String currency1, String currency2) {");
        assertTrue("getExchangeRate no longer refuses a null iso before the lookup. A "
                        + "ConcurrentHashMap throws on a null key where the map it replaced "
                        + "answered null and this method returned null",
                reader.contains("if (currency1 == null || currency2 == null) { return null; }"));
        // Boxed in from both sides. Above the equal check, two null isos stop taking that branch
        // and a currency converted to itself answers nothing instead of a rate of one. Below the
        // lookup it is simply too late, and the string reads the same wherever it sits, so
        // presence alone pins nothing
        assertTrue("the null iso guard moved above the equality check, so getExchangeRate answers "
                        + "nothing for two null isos where it used to answer a rate of one",
                reader.indexOf("TextUtils.equals") < reader.indexOf("currency1 == null"));
        assertTrue("the null iso guard sits below the lookup it exists to come before, so a null "
                        + "iso reaches ConcurrentHashMap.get and throws",
                reader.indexOf("currency1 == null") < reader.indexOf("mCacheMemory.get"));
        // and on the method's own path, not tucked inside the equality branch above it. Nested
        // there it reads as being in the right place and between the right two lines, and a
        // single null iso never reaches it at all. Depth 1 is a statement of the method
        assertEquals("the null iso guard is nested inside a block, so the case it exists for takes "
                        + "the branch around it and throws on the lookup",
                1, depthOf(reader, reader.indexOf("currency1 == null")));
        String writer = bodyOf(source,
                "public void setExchangeRate(String currency, double rate, long timestamp) {");
        assertTrue("setExchangeRate no longer refuses a null iso, so a rate filed under none "
                        + "throws on the put instead of being dropped",
                writer.contains("if (currency == null) { return; }"));
        assertTrue("setExchangeRate refuses a null iso only after storing it, which is the throw "
                        + "the guard exists to avoid",
                writer.indexOf("currency == null") < writer.indexOf("mCacheMemory.put"));
        assertEquals("setExchangeRate's null iso guard is nested inside a block instead of "
                        + "standing on the method's own path", 1,
                depthOf(writer, writer.indexOf("currency == null")));
        String load = bodyOf(source, "private void loadCacheInMemory() {");
        assertTrue("loadCacheInMemory no longer skips a row with no iso, so one in cache.db throws "
                        + "while the cache is being built and takes the app down at startup",
                load.contains("if (iso == null) { continue; }"));
        assertTrue("loadCacheInMemory skips a row with no iso only after putting it, so the row it "
                        + "means to drop still throws during startup",
                load.indexOf("iso == null") < load.indexOf("mCacheMemory.put"));
        // depth 3 is inside the cursor check and inside the row loop, which is where a per row
        // skip has to sit. Anywhere shallower it runs once or not at all
        assertEquals("loadCacheInMemory's skip is not inside the row loop, so it does not run per "
                        + "row", 3, depthOf(load, load.indexOf("iso == null")));
        // and the reader must not reach the storage at all. Every mCacheStorage call enters
        // SQLiteOpenHelper.getReadableDatabase, which is synchronized, so a fallback read here
        // would put a monitor AND a disk read on the keystroke path that takes nothing. The token
        // scan below cannot see that lock, because it is spelled in another file
        assertFalse("getExchangeRate reaches mCacheStorage. Every call on it enters a "
                        + "synchronized SQLiteOpenHelper method, so the reader this file pins as "
                        + "lock free would take one, and wait on a disk read on the main thread",
                reader.contains("mCacheStorage"));
    }

    /**
     * Over the whole file and not one method body, for the reason the reload case below gives: a
     * reader that took its lock inside a helper it calls would pass a per body check. Four of the
     * five spellings open no block either, so counting is the only way to see them.
     *
     * A lock here would not deadlock an importer, since cache.db is its own helper and shares no
     * connection with database.db. It would stall a main thread keystroke behind a disk write,
     * and it would put a monitor under a method this file pins as taking nothing.
     */
    @Test
    public void nothingInTheExchangeRateCacheTakesALock() {
        String source = readSource(
                "src/main/java/com/oriondev/moneywallet/storage/cache/ExchangeRateCache.java");
        for (String lock : WAYS_TO_TAKE_A_LOCK) {
            assertEquals("ExchangeRateCache takes a lock through " + lock.trim() + ". "
                            + "CurrencyManager.getExchangeRate is listed below as a reader that "
                            + "takes nothing and it hands straight to this class, so a lock "
                            + "anywhere in the file can end up on the read path",
                    0, count(source, lock));
        }
    }

    /**
     * The revive in SQLDatabase.insertCurrency is dead code unless a caller asks for it, and this
     * is the only line in the app that does. Every case that exercises the revive calls
     * insertCurrency directly with a flag it builds itself, so deleting this line leaves all of
     * them green and quietly puts the behaviour back to what it was.
     */
    @Test
    public void theDefaultSetAsksForARowThatIsThereToBeBroughtBack() {
        String body = bodyOf(readSource(),
                "private static Map<String, CurrencyUnit> loadDefaultCurrencies(Context context) {");
        assertTrue("the default set no longer asks for a row that is present and not served to be "
                        + "brought back, so its inserts collide with those rows and land nothing, "
                        + "which is the whole of what this repairs",
                body.contains("put(Contract.Currency.REVIVE_IF_DELETED, true)"));
        // and it answers with the table and not with the file it just tried to write. Without
        // this, a revive that stops working goes back to publishing currencies the provider
        // serves none of, which is the failure the revive exists to end
        assertTrue("the default set answers with the map it built from the asset file again, so a "
                        + "seed that lands nothing still publishes every currency in that file",
                body.contains("return loadUserCurrencies(context);"));
    }

    @Test
    public void noReaderOfTheCacheTakesALock() {
        String source = readSource();
        for (String reader : READERS) {
            String body = bodyOf(source, reader);
            for (String lock : WAYS_TO_TAKE_A_LOCK) {
                assertFalse(reader + " takes a lock again through " + lock.trim() + ". An importer "
                                + "calls in here while holding the one database connection, so a "
                                + "reader that can block is half of the deadlock this removed",
                        body.contains(lock));
            }
        }
    }

    /**
     * The reader cases above read one method body each, so a reader that took its lock inside a
     * helper it calls would pass every one of them. These close that from the other side, over the
     * whole file: every lock taken any way at all has to be RELOAD_LOCK, only invalidateCache and
     * initialize may name it, and the reload has to both load and publish inside it.
     */
    @Test
    public void theOnlyLockIsReloadLockAndOnlyTheReloadTakesIt() {
        String source = readSource();
        // over the WHOLE file and not just the reader bodies, because a reader that calls a
        // helper which locks passes every case that reads one body at a time
        for (String lock : WAYS_TO_TAKE_A_LOCK) {
            int taken = count(source, lock);
            int onReloadLock = "synchronized".equals(lock)
                    ? count(source, "synchronized (RELOAD_LOCK)") : 0;
            assertTrue("CurrencyManager takes a lock through " + lock.trim() + " that is not "
                            + "RELOAD_LOCK. Whichever method holds it, an importer holding the one "
                            + "database connection can be made to wait on it, which is half of the "
                            + "deadlock this removed",
                    taken == onReloadLock);
        }
        String reload = bodyOf(source, "public static void invalidateCache(Context context) {");
        String setUp = bodyOf(source, "public static void initialize(Context context) {");
        // the declaration, and every other mention inside invalidateCache. A helper that took
        // RELOAD_LOCK for a reader would raise the first count and not the second
        assertTrue("something other than invalidateCache and initialize names RELOAD_LOCK. It is "
                        + "held across a database read, which is only safe while the two methods "
                        + "that take it both refuse to run inside a transaction",
                count(source, "RELOAD_LOCK")
                        == count(reload, "RELOAD_LOCK") + count(setUp, "RELOAD_LOCK") + 1);
        // and the load has to be INSIDE it. Lifted out, the lock guards the assignment alone,
        // which nothing needs, and two reloads interleave their read and their publish again. The
        // cache then keeps whichever finished last, which is not whichever read last
        assertTrue("invalidateCache reads the currencies outside RELOAD_LOCK and only publishes "
                        + "under it. That is the interleaving this lock exists to stop: two "
                        + "reloads both read, and the cache keeps the one that finished last "
                        + "instead of the one that read last",
                depthOf(reload, reload.indexOf("loadCurrencies(context)")) == 2);
        // and so does the assignment. Reading under the lock and publishing outside it orders the
        // reads and nothing else, so the cache still keeps whichever reload finished last
        assertTrue("invalidateCache publishes outside RELOAD_LOCK. Two reloads then read one at a "
                        + "time and still publish in whatever order they finish, so a reload that "
                        + "read an older table can overwrite one that read a newer one",
                depthOf(reload, reload.indexOf("mInstance.mCurrencyCache =")) == 2);
        // initialize loads the same way and takes the same lock, so its load has to be inside it
        // too. Built outside and only assigned under the lock, a first launch can seed the table
        // while a reload on another thread is reading it
        // initialize is pinned by position and not by depth alone. Depth 2 is reached just as
        // well by the null check with no lock around it at all, which is the very shape the
        // message below names, so the construction has to sit AFTER the lock is entered
        for (String loader : new String[] {"invalidateCache", "initialize"}) {
            String body = "invalidateCache".equals(loader) ? reload : setUp;
            int locked = body.indexOf("synchronized (RELOAD_LOCK)");
            assertTrue(loader + " no longer takes RELOAD_LOCK at all, so the two loaders can run "
                    + "their read and their publish at the same time", locked >= 0);
            // before the lock, by position. At depth 1 but below the block it is useless, and a
            // caller already inside a transaction walks straight into the old deadlock
            int refuses = body.indexOf("refuseIfInsideATransaction()");
            assertTrue(loader + " no longer refuses before it takes RELOAD_LOCK. A caller that "
                            + "already holds a database connection then takes the lock across a "
                            + "read that needs it, which is the deadlock this removed",
                    refuses >= 0 && refuses < locked && depthOf(body, refuses) == 1);
        }
        assertTrue("initialize builds the instance outside RELOAD_LOCK and only assigns it under "
                        + "the lock, so its load and a reload can run at the same time. That is "
                        + "the interleaving the lock exists to stop",
                setUp.indexOf("new CurrencyManager(context)")
                        > setUp.indexOf("synchronized (RELOAD_LOCK)")
                        && depthOf(setUp, setUp.indexOf("new CurrencyManager(context)")) >= 2);
        // the map the readers walk without a lock is only safe while nothing can write to it
        assertTrue("loadCurrencies no longer wraps the map it publishes. A later write to a "
                        + "published map is what the volatile stops carrying, and the walk in "
                        + "CurrencyManagerConcurrencyTest is what breaks first",
                source.contains("return Collections.unmodifiableMap(currencies);"));
    }

    /**
     * The other half of that argument, and the half this file cannot see: the lock is held across
     * a database read, so it is only safe while no thread that already holds the one database
     * connection can reach invalidateCache. These are the three callers that exist, all of which
     * call it with no transaction open. A new one is not necessarily wrong, it just has to be
     * checked, which is what this is for.
     */
    @Test
    public void nothingNewCallsTheReloadWithoutBeingChecked() {
        String[] allowed = {
                "service/BackupHandlerIntentService.java",
                "service/UpgradeLegacyEditionIntentService.java",
                "ui/activity/NewEditCurrencyActivity.java",
                "App.java",
        };
        List<String> callers = new ArrayList<>();
        List<File> files = new ArrayList<>();
        for (File root : productionSourceRoots()) {
            files.addAll(sourceFiles(root));
        }
        for (File file : files) {
            String text = read(file);
            if (text.contains("CurrencyManager.invalidateCache(")
                    || text.contains("CurrencyManager.initialize(")) {
                String path = file.getPath().replace(File.separatorChar, '/');
                boolean known = false;
                for (String one : allowed) {
                    known = known || path.endsWith(one);
                }
                if (!known) {
                    callers.add(path);
                }
            }
        }
        assertTrue("a new caller of CurrencyManager.invalidateCache or initialize: " + callers
                        + ". Both hold a "
                        + "lock across a database read, so read the new caller and make sure it is "
                        + "not inside a transaction, then add it here",
                callers.isEmpty());
    }

    /**
     * Every production source set, not just main. floss and osm are flavor source sets and are
     * compiled into the app exactly like main is, so a caller added in one of them ships.
     */
    private static List<File> productionSourceRoots() {
        File app = new File("src");
        if (!app.isDirectory()) {
            app = new File("app/src");
        }
        assertTrue("cannot find the source root at " + app.getAbsolutePath(), app.isDirectory());
        List<File> roots = new ArrayList<>();
        File[] sets = app.listFiles();
        assertTrue("no source sets under " + app.getAbsolutePath(), sets != null);
        for (File set : sets) {
            String name = set.getName();
            if (set.isDirectory() && !name.equals("test") && !name.equals("androidTest")) {
                File java = new File(set, "java");
                if (java.isDirectory()) {
                    roots.add(java);
                }
            }
        }
        assertTrue("no production source set has a java directory", !roots.isEmpty());
        return roots;
    }

    private static List<File> sourceFiles(File directory) {
        List<File> found = new ArrayList<>();
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    found.addAll(sourceFiles(child));
                } else if (child.getName().endsWith(".java") || child.getName().endsWith(".kt")) {
                    found.add(child);
                }
            }
        }
        return found;
    }

    private static String read(File file) {
        try {
            return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            fail("cannot read " + file + ": " + e.getMessage());
            return null;
        }
    }

    private static int count(String text, String needle) {
        int found = 0;
        for (int at = text.indexOf(needle); at >= 0; at = text.indexOf(needle, at + 1)) {
            found++;
        }
        return found;
    }

    @Test
    public void bothStaticsThatCarryTheCacheAreVolatile() {
        String source = readSource();
        // the cache itself, so a reader on another thread sees the map that was published and not
        // a stale one
        assertTrue("the currency cache field is no longer volatile",
                source.contains("private volatile Map<String, CurrencyUnit> mCurrencyCache;"));
        // and the holder, because the cache field is no longer final. A final field publishes the
        // object it points at even when its holder is reached through a race, and volatile does
        // not, so a racing reader could otherwise see mInstance set and its cache still null
        assertTrue("the singleton field is no longer volatile, and the cache field it carries is "
                        + "not final. getCurrency, getCurrencies, getExchangeRate and "
                        + "getExchangeRateCache all read mInstance with no lock, so without it a "
                        + "reader can see mInstance set and the map it carries still null",
                source.contains("private static volatile CurrencyManager mInstance;"));
    }
}
