package com.oriondev.moneywallet.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Looper;

import androidx.core.content.FileProvider;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.DataFormat;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.utils.CurrencyManager;
import com.oriondev.moneywallet.utils.IconLoader;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.robolectric.Shadows.shadowOf;

/**
 * The service hands back the token it was started with on every broadcast, so the screen that
 * started the work can tell its own broadcasts apart.
 */
@RunWith(RobolectricTestRunner.class)
public class ImportExportIntentServiceTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    private Context mContext;
    private final Map<String, String> mTokens = new HashMap<>();
    private final Map<String, Intent> mBroadcasts = new HashMap<>();

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocalAction.ACTION_IMPORT_SERVICE_STARTED);
        filter.addAction(LocalAction.ACTION_IMPORT_SERVICE_FINISHED);
        filter.addAction(LocalAction.ACTION_IMPORT_SERVICE_FAILED);
        filter.addAction(LocalAction.ACTION_EXPORT_SERVICE_STARTED);
        filter.addAction(LocalAction.ACTION_EXPORT_SERVICE_FINISHED);
        filter.addAction(LocalAction.ACTION_EXPORT_SERVICE_FAILED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                mTokens.put(intent.getAction(), intent.getStringExtra(ImportExportIntentService.TOKEN));
                mBroadcasts.put(intent.getAction(), intent);
            }

        }, filter);
    }

    @Test
    public void theStartAndTheFailureOfAnImportCarryTheToken() {
        run(new Intent(mContext, ImportExportIntentService.class)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT));
        assertEquals(2, mTokens.size());
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_STARTED));
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_FAILED));
    }

    @Test
    public void theStartAndTheFailureOfAnExportCarryTheToken() {
        run(new Intent(mContext, ImportExportIntentService.class)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT));
        assertEquals(2, mTokens.size());
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_STARTED));
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_FAILED));
    }

    /** The service handles queued jobs one after another on the same instance. */
    @Test
    public void aQueuedJobCarriesItsOwnToken() {
        ImportExportIntentService service = Robolectric.setupService(ImportExportIntentService.class);
        run(service, new Intent(mContext, ImportExportIntentService.class)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT), "a");
        run(service, new Intent(mContext, ImportExportIntentService.class)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT), "b");
        assertEquals("b", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_STARTED));
        assertEquals("b", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_FAILED));
    }

    @Test
    public void theStartAndTheEndOfAnImportThatWorksCarryTheToken() throws IOException {
        File file = File.createTempFile("tallybook-import", ".csv");
        file.deleteOnExit();
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            writer.write("\"wallet\",\"currency\",\"category\",\"datetime\",\"money\"\n"
                    + "\"Cash\",\"EUR\",\"Food\",\"2026-08-12 09:00:00\",\"-1.00\"\n");
        }
        run(new Intent(mContext, ImportExportIntentService.class)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT)
                .putExtra(ImportExportIntentService.FORMAT, DataFormat.CSV)
                .putExtra(ImportExportIntentService.FILE, file));
        assertReached(LocalAction.ACTION_IMPORT_SERVICE_FINISHED, LocalAction.ACTION_IMPORT_SERVICE_FAILED);
        assertEquals(2, mTokens.size());
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_STARTED));
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_FINISHED));
    }

    @Test
    public void theStartAndTheEndOfAnExportCarryTheToken() {
        File folder = new File(mContext.getCacheDir(), "export");
        assertTrue(folder.isDirectory() || folder.mkdirs());
        Wallet wallet = new Wallet(1L, "Cash", IconLoader.parse(ICON), CurrencyManager.getCurrency("EUR"), 0L, 0L);
        // FileProvider only accepts a path whose separator is a forward slash, so on a Windows
        // host it refuses every file. The uri it hands back is not what this test is about.
        try (MockedStatic<FileProvider> provider = mockStatic(FileProvider.class)) {
            provider.when(() -> FileProvider.getUriForFile(any(Context.class), anyString(), any(File.class)))
                    .thenAnswer(call -> Uri.fromFile(call.getArgument(2)));
            run(new Intent(mContext, ImportExportIntentService.class)
                    .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT)
                    .putExtra(ImportExportIntentService.FORMAT, DataFormat.CSV)
                    .putExtra(ImportExportIntentService.WALLETS, new Wallet[]{wallet})
                    .putExtra(ImportExportIntentService.FOLDER, folder));
        }
        assertReached(LocalAction.ACTION_EXPORT_SERVICE_FINISHED, LocalAction.ACTION_EXPORT_SERVICE_FAILED);
        assertEquals(2, mTokens.size());
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_STARTED));
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_FINISHED));
    }

    private void run(Intent intent) {
        run(Robolectric.setupService(ImportExportIntentService.class), intent, "t");
    }

    private void run(ImportExportIntentService service, Intent intent, String token) {
        service.onHandleIntent(intent.putExtra(ImportExportIntentService.TOKEN, token));
        shadowOf(Looper.getMainLooper()).idle();
    }

    /** Names the exception when the work failed, so a red run says why the end was not reached. */
    private void assertReached(String finished, String failed) {
        Intent failure = mBroadcasts.get(failed);
        if (failure != null) {
            throw new AssertionError("the work failed instead of finishing",
                    (Throwable) failure.getSerializableExtra(ImportExportIntentService.EXCEPTION));
        }
        assertTrue(finished + " was never sent", mBroadcasts.containsKey(finished));
    }
}
