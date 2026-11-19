package com.oriondev.moneywallet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper

import androidx.core.content.FileProvider
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.TestDatabases
import com.oriondev.moneywallet.utils.CurrencyManager
import com.oriondev.moneywallet.utils.IconLoader

import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStreamWriter
import java.util.HashMap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockStatic
import org.robolectric.Shadows.shadowOf

/**
 * The service hands back the token it was started with on every broadcast, so the screen that
 * started the work can tell its own broadcasts apart.
 */
@RunWith(RobolectricTestRunner::class)
class ImportExportIntentServiceTest {

    companion object {

        private const val ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}"
    }

    private lateinit var mContext: Context
    private val mTokens: MutableMap<String?, String?> = HashMap()
    private val mBroadcasts: MutableMap<String?, Intent> = HashMap()

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        val filter = IntentFilter()
        filter.addAction(LocalAction.ACTION_IMPORT_SERVICE_STARTED)
        filter.addAction(LocalAction.ACTION_IMPORT_SERVICE_FINISHED)
        filter.addAction(LocalAction.ACTION_IMPORT_SERVICE_FAILED)
        filter.addAction(LocalAction.ACTION_EXPORT_SERVICE_STARTED)
        filter.addAction(LocalAction.ACTION_EXPORT_SERVICE_FINISHED)
        filter.addAction(LocalAction.ACTION_EXPORT_SERVICE_FAILED)
        LocalBroadcastManager.getInstance(mContext).registerReceiver(object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                mTokens.put(intent.action, intent.getStringExtra(ImportExportIntentService.TOKEN))
                mBroadcasts.put(intent.action, intent)
            }

        }, filter)
    }

    @Test
    fun theStartAndTheFailureOfAnImportCarryTheToken() {
        run(Intent(mContext, ImportExportIntentService::class.java)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT))
        assertEquals(2, mTokens.size)
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_STARTED))
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_FAILED))
    }

    @Test
    fun theStartAndTheFailureOfAnExportCarryTheToken() {
        run(Intent(mContext, ImportExportIntentService::class.java)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT))
        assertEquals(2, mTokens.size)
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_STARTED))
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_FAILED))
    }

    /** The service handles queued jobs one after another on the same instance. */
    @Test
    fun aQueuedJobCarriesItsOwnToken() {
        val service = Robolectric.setupService(ImportExportIntentService::class.java)
        run(service, Intent(mContext, ImportExportIntentService::class.java)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT), "a")
        run(service, Intent(mContext, ImportExportIntentService::class.java)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT), "b")
        assertEquals("b", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_STARTED))
        assertEquals("b", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_FAILED))
    }

    @Test
    @Throws(IOException::class)
    fun theStartAndTheEndOfAnImportThatWorksCarryTheToken() {
        val file = File.createTempFile("tallybook-import", ".csv")
        file.deleteOnExit()
        OutputStreamWriter(FileOutputStream(file), "UTF-8").use { writer ->
            writer.write("\"wallet\",\"currency\",\"category\",\"datetime\",\"money\"\n"
                    + "\"Cash\",\"EUR\",\"Food\",\"2026-08-12 09:00:00\",\"-1.00\"\n")
        }
        run(Intent(mContext, ImportExportIntentService::class.java)
                .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT)
                .putExtra(ImportExportIntentService.FORMAT, DataFormat.CSV)
                .putExtra(ImportExportIntentService.FILE, file))
        assertReached(LocalAction.ACTION_IMPORT_SERVICE_FINISHED, LocalAction.ACTION_IMPORT_SERVICE_FAILED)
        assertEquals(2, mTokens.size)
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_STARTED))
        assertEquals("t", mTokens.get(LocalAction.ACTION_IMPORT_SERVICE_FINISHED))
    }

    @Test
    fun theStartAndTheEndOfAnExportCarryTheToken() {
        val folder = File(mContext.cacheDir, "export")
        assertTrue(folder.isDirectory || folder.mkdirs())
        val wallet = Wallet(1L, "Cash", IconLoader.parse(ICON), CurrencyManager.getCurrency("EUR"), 0L, 0L)
        // FileProvider only accepts a path whose separator is a forward slash, so on a Windows
        // host it refuses every file. The uri it hands back is not what this test is about.
        mockStatic(FileProvider::class.java).use { provider ->
            provider.`when`<Uri> { FileProvider.getUriForFile(any(Context::class.java), anyString(), any(File::class.java)) }
                    .thenAnswer { call -> Uri.fromFile(call.getArgument(2)) }
            run(Intent(mContext, ImportExportIntentService::class.java)
                    .putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT)
                    .putExtra(ImportExportIntentService.FORMAT, DataFormat.CSV)
                    .putExtra(ImportExportIntentService.WALLETS, arrayOf(wallet))
                    .putExtra(ImportExportIntentService.FOLDER, folder))
        }
        assertReached(LocalAction.ACTION_EXPORT_SERVICE_FINISHED, LocalAction.ACTION_EXPORT_SERVICE_FAILED)
        assertEquals(2, mTokens.size)
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_STARTED))
        assertEquals("t", mTokens.get(LocalAction.ACTION_EXPORT_SERVICE_FINISHED))
    }

    private fun run(intent: Intent) {
        run(Robolectric.setupService(ImportExportIntentService::class.java), intent, "t")
    }

    private fun run(service: ImportExportIntentService, intent: Intent, token: String) {
        ReflectionHelpers.callInstanceMethod<Any>(service, "onHandleIntent",
                ReflectionHelpers.ClassParameter.from(Intent::class.java, intent.putExtra(ImportExportIntentService.TOKEN, token)))
        shadowOf(Looper.getMainLooper()).idle()
    }

    /** Names the exception when the work failed, so a red run says why the end was not reached. */
    private fun assertReached(finished: String, failed: String) {
        val failure = mBroadcasts.get(failed)
        if (failure != null) {
            throw AssertionError("the work failed instead of finishing",
                    failure.getSerializableExtra(ImportExportIntentService.EXCEPTION) as Throwable?)
        }
        assertTrue(finished + " was never sent", mBroadcasts.containsKey(finished))
    }
}
