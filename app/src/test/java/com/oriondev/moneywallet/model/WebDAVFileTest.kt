package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WebDAVFileTest {

    @Test
    fun aStringWithASizeGivesThatSizeBack() {
        val file = WebDAVFile("{\"name\":\"wallet.mwbx\",\"path\":\"/backups/wallet.mwbx\",\"size\":52428,\"directory\":false}")
        assertEquals(52428L, file.getSize())
    }

    @Test
    fun aStringWithoutASizeGivesZero() {
        val file = WebDAVFile("{\"name\":\"wallet.mwbx\",\"path\":\"/backups/wallet.mwbx\",\"directory\":false}")
        assertEquals(0L, file.getSize())
    }

    @Test
    fun aStringWithTheDirectoryFlagIsADirectory() {
        val file = WebDAVFile("{\"name\":\"backups\",\"path\":\"/backups\",\"size\":0,\"directory\":true}")
        assertTrue(file.isDirectory())
    }

    @Test
    fun aStringWithoutTheDirectoryFlagIsNotADirectory() {
        val file = WebDAVFile("{\"name\":\"backups\",\"path\":\"/backups\",\"size\":17}")
        assertFalse(file.isDirectory())
        assertEquals(17L, file.getSize())
    }

    @Test
    fun aStringGivesItsNameAndPathBack() {
        val file = WebDAVFile("{\"name\":\"March 2024.mwbx\",\"path\":\"/my backups/March 2024.mwbx\",\"size\":9}")
        assertEquals("March 2024.mwbx", file.getName())
        assertEquals("/my backups/March 2024.mwbx", file.getPath())
    }

    @Test
    fun decodingTheEncodedStringKeepsEveryField() {
        val file = WebDAVFile("monthly.mwbx", "/remote/backups/monthly.mwbx", 987654321L, true)
        val back = WebDAVFile.decode(file.encodeToString())
        assertNotNull(back)
        assertEquals("monthly.mwbx", back!!.getName())
        assertEquals("/remote/backups/monthly.mwbx", back.getPath())
        assertEquals(987654321L, back.getSize())
        assertTrue(back.isDirectory())
    }
}
