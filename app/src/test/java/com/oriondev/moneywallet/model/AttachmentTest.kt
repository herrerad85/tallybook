package com.oriondev.moneywallet.model

import android.os.Parcel

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

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

    private fun roundTrip(attachment: Attachment): Attachment {
        val parcel = Parcel.obtain()
        attachment.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val back = Attachment.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        return back
    }
}
