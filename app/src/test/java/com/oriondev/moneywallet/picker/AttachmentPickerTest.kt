package com.oriondev.moneywallet.picker

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Looper
import android.webkit.MimeTypeMap
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.core.app.ApplicationProvider

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.service.AttachmentHandlerIntentService

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ActivityController

import java.io.File

@RunWith(RobolectricTestRunner::class)
class AttachmentPickerTest {

    companion object {

        private const val TAG = "AttachmentPickerTest::Picker"
    }

    @Test
    fun aFileWithAnUpperCaseExtensionTakesTheMimeTypeOfItsLowerCaseExtension() {
        val mimeTypeMap = shadowOf(MimeTypeMap.getSingleton())
        mimeTypeMap.clearMappings()
        mimeTypeMap.addExtensionMimeTypeMapping("jpg", "image/jpeg")
        withHeldFile { _, picker, held ->
            assertEquals(1, picker.getCurrentAttachments().size)
            assertEquals("PHOTO.JPG", held.getName())
            assertEquals("image/jpeg", held.getType())
            assertEquals(5L, held.getSize())
            assertEquals(Attachment.Status.PENDING, held.getStatus())
            assertFalse(picker.areAllAttachmentsReady())
        }
    }

    @Test
    fun aFinishedCreationMarksTheHeldAttachmentReady() {
        withHeldFile { activity, picker, held ->
            send(activity, LocalAction.ACTION_ATTACHMENT_OP_FINISHED, copyOf(held, 41L), null)
            assertEquals(Attachment.Status.READY, picker.getCurrentAttachments()[0].getStatus())
            assertEquals(41L, picker.getCurrentAttachments()[0].getId())
            assertTrue(picker.areAllAttachmentsReady())
        }
    }

    @Test
    fun aStartedCreationMovesAReadyAttachmentBackToPending() {
        withHeldFile { activity, picker, held ->
            send(activity, LocalAction.ACTION_ATTACHMENT_OP_FINISHED, copyOf(held, 41L), null)
            assertTrue(picker.areAllAttachmentsReady())
            send(activity, LocalAction.ACTION_ATTACHMENT_OP_STARTED, copyOf(held, 0L), null)
            assertEquals(Attachment.Status.PENDING, picker.getCurrentAttachments()[0].getStatus())
            assertEquals(0L, picker.getCurrentAttachments()[0].getId())
            assertFalse(picker.areAllAttachmentsReady())
        }
    }

    @Test
    fun aFailedCreationMovesAReadyAttachmentBackToPending() {
        withHeldFile { activity, picker, held ->
            send(activity, LocalAction.ACTION_ATTACHMENT_OP_FINISHED, copyOf(held, 41L), null)
            assertTrue(picker.areAllAttachmentsReady())
            send(activity, LocalAction.ACTION_ATTACHMENT_OP_FAILED, copyOf(held, 0L), "disk full")
            assertEquals(Attachment.Status.PENDING, picker.getCurrentAttachments()[0].getStatus())
            assertFalse(picker.areAllAttachmentsReady())
        }
    }

    private fun withHeldFile(body: (AppCompatActivity, AttachmentPicker, Attachment) -> Unit) {
        val application = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)
        val file = File(application.cacheDir, "PHOTO.JPG")
        file.writeBytes(byteArrayOf(1, 2, 3, 4, 5))
        val controller: ActivityController<AppCompatActivity> =
                Robolectric.buildActivity(AppCompatActivity::class.java).setup()
        try {
            val fragmentManager = controller.get().supportFragmentManager
            val picker = AttachmentPicker.createPicker(fragmentManager, TAG, null)
            picker.addFileFromUri(Uri.fromFile(file))
            fragmentManager.executePendingTransactions()
            shadowOf(Looper.getMainLooper()).idle()
            val attachments = picker.getCurrentAttachments()
            assertEquals(1, attachments.size)
            body(controller.get(), picker, attachments[0])
        } finally {
            controller.close()
            file.delete()
        }
    }

    private fun copyOf(held: Attachment, id: Long): Attachment {
        return Attachment(id, held.getFile(), held.getName(), held.getType(), held.getSize(), Attachment.Status.PENDING)
    }

    private fun send(activity: AppCompatActivity, action: String, attachment: Attachment, error: String?) {
        val intent = Intent(action)
        intent.putExtra(AttachmentHandlerIntentService.ATTACHMENT, attachment)
        intent.putExtra(AttachmentHandlerIntentService.ACTION, AttachmentHandlerIntentService.ACTION_CREATE)
        if (error != null) {
            intent.putExtra(AttachmentHandlerIntentService.ERROR, error)
        }
        LocalBroadcastManager.getInstance(activity).sendBroadcast(intent)
        shadowOf(Looper.getMainLooper()).idle()
    }
}
