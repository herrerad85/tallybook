package com.oriondev.moneywallet.model

import org.junit.Assert.assertEquals
import org.junit.Test

class LicenseTest {

    @Test
    fun mitParsesToMit() {
        assertEquals(License.Type.MIT, typeOf("MIT"))
    }

    @Test
    fun iscParsesToIsc() {
        assertEquals(License.Type.ISC, typeOf("ISC"))
    }

    @Test
    fun apacheParsesToApache() {
        assertEquals(License.Type.APACHE, typeOf("APACHE"))
    }

    @Test
    fun gpl2ParsesToGpl2() {
        assertEquals(License.Type.GPL2, typeOf("GPL2"))
    }

    @Test
    fun gpl3ParsesToGpl3() {
        assertEquals(License.Type.GPL3, typeOf("GPL3"))
    }

    @Test
    fun agplParsesToAgpl() {
        assertEquals(License.Type.AGPL, typeOf("AGPL"))
    }

    @Test
    fun customParsesToCustom() {
        assertEquals(License.Type.CUSTOM, typeOf("CUSTOM"))
    }

    @Test
    fun aNullTypeIsUnknown() {
        assertEquals(License.Type.UNKNOWN, typeOf(null))
    }

    @Test
    fun anUnrecognizedTypeIsUnknown() {
        assertEquals(License.Type.UNKNOWN, typeOf("BSD"))
        assertEquals(License.Type.UNKNOWN, typeOf("mit"))
        assertEquals(License.Type.UNKNOWN, typeOf(""))
    }

    @Test
    fun theConstructorKeepsTheOtherFields() {
        val license = License("Gson", "Google", "https://github.com/google/gson", "2.10", "Copyright 2008 Google", "APACHE")
        assertEquals("Gson", license.getName())
        assertEquals("Google", license.getAuthor())
        assertEquals("https://github.com/google/gson", license.getUrl())
        assertEquals("2.10", license.getVersion())
        assertEquals("Copyright 2008 Google", license.getCopyright())
        assertEquals(License.Type.APACHE, license.getType())
    }

    private fun typeOf(type: String?): License.Type {
        return License("name", "author", "https://example.org", "1.0", "copyright", type).getType()
    }
}
