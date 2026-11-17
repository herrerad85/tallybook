package com.oriondev.moneywallet.model

import android.content.Context
import android.net.Uri
import android.os.Parcel
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mockStatic
import org.robolectric.RobolectricTestRunner

import java.io.File

@RunWith(RobolectricTestRunner::class)
class AttachmentTest {

    @Test
    fun aParcelRoundTripKeepsEveryFieldInItsOwnPlace() {
        val attachment = Attachment(42L, "3f9c0a7d1e", "March receipt.pdf", "application/pdf", 98765L, Attachment.Status.PENDING)
        val back = roundTrip(attachment)
        assertEquals(42L, back.getId())
        assertEquals("3f9c0a7d1e", back.getFile())
        assertEquals("March receipt.pdf", back.getName())
        assertEquals("application/pdf", back.getType())
        assertEquals(98765L, back.getSize())
        assertEquals(Attachment.Status.PENDING, back.getStatus())
    }

    @Test
    fun aParcelRoundTripKeepsTheErrorStatusAndAnImageType() {
        val attachment = Attachment(7L, "b61e2d", "photo of the bill", "image/png", 1024L, Attachment.Status.ERROR)
        val back = roundTrip(attachment)
        assertEquals(7L, back.getId())
        assertEquals("b61e2d", back.getFile())
        assertEquals("photo of the bill", back.getName())
        assertEquals("image/png", back.getType())
        assertEquals(1024L, back.getSize())
        assertEquals(Attachment.Status.ERROR, back.getStatus())
    }

    @Test
    fun aPlainFileNameIsAccepted() {
        assertTrue(Attachment.isPlainFileName("3f9c0a7d1e"))
    }

    @Test
    fun eachNameThatIsNotAPlainFileNameIsRefused() {
        for (name in NOT_PLAIN_FILE_NAMES) {
            assertFalse(name, Attachment.isPlainFileName(name))
        }
    }

    @Test
    fun theUriOfAPlainFileNameNamesThatFileInTheAttachmentFolder() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val attachment = Attachment(1L, "3f9c0a7d1e", "March receipt.pdf", "application/pdf", 98765L)
        mockStatic(FileProvider::class.java).use { provider ->
            provider.`when`<Uri> { FileProvider.getUriForFile(any(Context::class.java), eq("io.github.herrerad85.tallybook.dev.storage.file"), any(File::class.java)) }
                    .thenAnswer { call -> Uri.fromFile(call.getArgument(2)) }
            assertEquals(Uri.fromFile(File(context.getExternalFilesDir(null), "attachments/3f9c0a7d1e")).toString(),
                    attachment.getUri(context).toString())
        }
    }

    @Test
    fun aNameThatIsNotAPlainFileNameHasNoUri() {
        val context: Context = ApplicationProvider.getApplicationContext()
        mockStatic(FileProvider::class.java).use { provider ->
            provider.`when`<Uri> { FileProvider.getUriForFile(any(Context::class.java), eq("io.github.herrerad85.tallybook.dev.storage.file"), any(File::class.java)) }
                    .thenAnswer { call -> Uri.fromFile(call.getArgument(2)) }
            for (name in NOT_PLAIN_FILE_NAMES) {
                val attachment = Attachment(1L, name, "March receipt.pdf", "application/pdf", 98765L)
                assertNull(name, attachment.getUri(context))
            }
        }
    }

    private fun roundTrip(attachment: Attachment): Attachment {
        val parcel = Parcel.obtain()
        attachment.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val back = Attachment.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        return back
    }

    companion object {

        private val NOT_PLAIN_FILE_NAMES = arrayOf("", ".", "..", "../database", "..\\database")
    }
}
