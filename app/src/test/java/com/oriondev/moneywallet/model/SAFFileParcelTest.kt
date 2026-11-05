package com.oriondev.moneywallet.model

import android.os.Parcel

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SAFFileParcelTest {

    @Test
    fun aParcelRoundTripKeepsEveryFieldOfADirectory() {
        val uri = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
        val file = SAFFile(encoded(uri, "Backups", 4096L, true))
        val back = roundTrip(file)
        assertEquals(uri, back.getUri().toString())
        assertEquals("Backups", back.getName())
        assertEquals(4096L, back.getSize())
        assertTrue(back.isDirectory())
    }

    @Test
    fun aParcelRoundTripKeepsEveryFieldOfAFile() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ABackups%2Fwallet.mwbx"
        val file = SAFFile(encoded(uri, "wallet.mwbx", 123456789L, false))
        val back = roundTrip(file)
        assertEquals(uri, back.getUri().toString())
        assertEquals("wallet.mwbx", back.getName())
        assertEquals(123456789L, back.getSize())
        assertFalse(back.isDirectory())
    }

    private fun encoded(uri: String, name: String, size: Long, isDirectory: Boolean): String {
        val jsonObject = JSONObject()
        jsonObject.put("uri", uri)
        jsonObject.put("name", name)
        jsonObject.put("size", size)
        jsonObject.put("isDir", isDirectory)
        return jsonObject.toString()
    }

    private fun roundTrip(file: SAFFile): SAFFile {
        val parcel = Parcel.obtain()
        file.writeToParcel(parcel, 0)
        parcel.setDataPosition(0)
        val back = SAFFile.CREATOR.createFromParcel(parcel)
        parcel.recycle()
        return back
    }
}
