package com.oriondev.moneywallet.storage.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import com.oriondev.moneywallet.App;
import com.oriondev.moneywallet.utils.CurrencyManager;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Gives a JVM test a database of its own, seeded the way a first launch seeds it. The shared
 * helper is a static built from the first Application it sees and never rebuilt, and Robolectric
 * replaces the Application and its data directory for every test without touching that static,
 * so without this every test after the first runs on the first test's file. The reset is package
 * local, which is why this lives here.
 *
 * The currency map is a static too, built once from whichever database the first test had, and
 * a database the reset opens has no currency rows since only that first load writes them. The
 * reload finds the table empty, writes the defaults into it and rebuilds the map from them, so
 * every test starts from the same state a fresh install has.
 */
public final class TestDatabases {

    private TestDatabases() {
    }

    public static void useFreshDatabase(Context context) {
        awaitStartupDelete();
        SQLDatabase.resetShared(context);
        CurrencyManager.invalidateCache(context);
    }

    /**
     * Runs the statements on the shared database, stamps it with the given version and reopens
     * it, so the next query runs the upgrade from that version the way an installed ledger does.
     */
    public static void reopenAtVersion(Context context, int version, String... statements) {
        SQLiteDatabase database = SQLDatabase.getShared(context).getWritableDatabase();
        for (String statement : statements) {
            database.execSQL(statement);
        }
        database.setVersion(version);
        SQLDatabase.resetShared(context);
    }

    /**
     * Opens a transaction on the shared database from this thread. The database has one
     * connection and the transaction keeps it, so a query from any other thread waits for
     * {@link #release}.
     */
    public static void hold(Context context) {
        SQLDatabase.getShared(context).getWritableDatabase().beginTransaction();
    }

    public static void release(Context context) {
        SQLDatabase.getShared(context).getWritableDatabase().endTransaction();
    }

    private static void awaitStartupDelete() {
        CountDownLatch reached = new CountDownLatch(1);
        try {
            Field field = App.class.getDeclaredField("sDeleteExecutor");
            field.setAccessible(true);
            ((Executor) field.get(null)).execute(reached::countDown);
            if (!reached.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("the startup delete did not finish within 5 seconds");
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }

}
