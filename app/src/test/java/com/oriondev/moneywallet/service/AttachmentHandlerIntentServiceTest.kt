package com.oriondev.moneywallet.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Looper

import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.TestDatabases

import org.apache.commons.io.FileUtils
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.util.ReflectionHelpers

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.ArrayList

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AttachmentHandlerIntentServiceTest {

    companion object {

        private val NOT_PLAIN_FILE_NAMES = arrayOf("", ".", "..", "../marker", "..\\marker")
    }

    @get:Rule
    val mSource = TemporaryFolder()

    private lateinit var mContext: Context
    private var mExternalFolder: File? = null
    private lateinit var mAttachmentFolder: File
    private val mBroadcasts: MutableList<Intent> = ArrayList()

    @Before
    fun setUp() {
        mContext = ApplicationProvider.getApplicationContext()
        TestDatabases.useFreshDatabase(mContext)
        mExternalFolder = mContext.getExternalFilesDir(null)
        mAttachmentFolder = File(mExternalFolder, Attachment.FOLDER_NAME)
        val filter = IntentFilter()
        filter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FINISHED)
        filter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FAILED)
        LocalBroadcastManager.getInstance(mContext).registerReceiver(object : BroadcastReceiver() {

            override fun onReceive(context: Context, intent: Intent) {
                mBroadcasts.add(intent)
            }

        }, filter)
    }

    @Test
    @Throws(IOException::class)
    fun aDeleteOfANameThatIsNotAPlainFileNameFailsAndDeletesNothing() {
        for (name in NOT_PLAIN_FILE_NAMES) {
            write(File(mAttachmentFolder, "kept"), "kept attachment")
            write(File(mExternalFolder, "marker"), "marker")

            run(AttachmentHandlerIntentService.ACTION_DELETE, name, null)

            assertFailed(name)
            assertEquals(name, "kept attachment", read(File(mAttachmentFolder, "kept")))
            assertEquals(name, "marker", read(File(mExternalFolder, "marker")))
        }
    }

    @Test
    @Throws(IOException::class)
    fun aDeleteOfAPlainFileNameDeletesOnlyThatFile() {
        write(File(mAttachmentFolder, "kept"), "kept attachment")
        write(File(mAttachmentFolder, "3f9c0a7d1e"), "receipt")

        run(AttachmentHandlerIntentService.ACTION_DELETE, "3f9c0a7d1e", null)

        assertEquals(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, last().action)
        assertFalse(File(mAttachmentFolder, "3f9c0a7d1e").exists())
        assertEquals("kept attachment", read(File(mAttachmentFolder, "kept")))
    }

    @Test
    @Throws(IOException::class)
    fun aCreateOfANameThatIsNotAPlainFileNameFailsAndCreatesNothing() {
        val source = mSource.newFile("receipt.pdf")
        write(source, "receipt")
        for (name in NOT_PLAIN_FILE_NAMES) {
            FileUtils.deleteQuietly(File(mExternalFolder, "marker"))

            run(AttachmentHandlerIntentService.ACTION_CREATE, name, Uri.fromFile(source))

            assertFailed(name)
            assertFalse(name, File(mExternalFolder, "marker").exists())
            assertFalse(name, File(mAttachmentFolder, name).isFile)
            assertEquals(name, 0, countAttachments())
        }
    }

    @Test
    @Throws(IOException::class)
    fun aCreateOfAPlainFileNameCopiesTheFileIntoTheAttachmentFolder() {
        val source = mSource.newFile("receipt.pdf")
        write(source, "receipt")

        run(AttachmentHandlerIntentService.ACTION_CREATE, "3f9c0a7d1e", Uri.fromFile(source))

        assertEquals(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, last().action)
        assertEquals("receipt", read(File(mAttachmentFolder, "3f9c0a7d1e")))
        assertEquals(1, countAttachments())
    }

    private fun run(action: Int, name: String, data: Uri?) {
        val intent = Intent(mContext, AttachmentHandlerIntentService::class.java)
                .putExtra(AttachmentHandlerIntentService.ACTION, action)
                .putExtra(AttachmentHandlerIntentService.ATTACHMENT,
                        Attachment(0L, name, "receipt.pdf", "application/pdf", 7L))
                .setData(data)
        ReflectionHelpers.callInstanceMethod<Any>(Robolectric.setupService(AttachmentHandlerIntentService::class.java), "onHandleIntent",
                ReflectionHelpers.ClassParameter.from(Intent::class.java, intent))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun assertFailed(name: String) {
        val intent = last()
        assertEquals(name, LocalAction.ACTION_ATTACHMENT_OP_FAILED, intent.action)
        assertEquals(name, "Invalid attachment file name", intent.getStringExtra(AttachmentHandlerIntentService.ERROR))
    }

    private fun last(): Intent {
        assertFalse(mBroadcasts.isEmpty())
        return mBroadcasts.get(mBroadcasts.size - 1)
    }

    private fun countAttachments(): Int {
        val cursor = mContext.contentResolver.query(DataContentProvider.CONTENT_ATTACHMENTS, null, null, null, null)
        assertNotNull(cursor)
        try {
            return cursor!!.count
        } finally {
            cursor!!.close()
        }
    }

    @Throws(IOException::class)
    private fun write(file: File, content: String) {
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8)
    }

    @Throws(IOException::class)
    private fun read(file: File): String {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8)
    }
}
