/*
 * Copyright (c) 2018.
 *
 * This file is part of MoneyWallet.
 *
 * MoneyWallet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MoneyWallet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MoneyWallet.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.oriondev.moneywallet.utils

import org.junit.Test

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class TileAddressTest {

    @Test
    fun aBaseAddressIsUsable() {
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/"))
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/osm"))
    }

    @Test
    fun aCompleteTemplateIsUsable() {
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{y}.jpg"))
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{y}.png?key=abc"))
    }

    @Test
    fun aHalfTemplateIsRejected() {
        // the first would have a second tile path appended; the other two would be used as they
        // stand, with the missing coordinate no longer varying, so a whole zoom level or a whole
        // column draws one repeated image
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}.png"))
    }

    @Test
    fun aTemplateCarryingAPlaceholderWeDoNotSubstituteIsRejected() {
        // the first is the form nearly every provider publishes, and leaving {s} in place means
        // the host never resolves; the second puts an unsupported placeholder in the path instead
        assertFalse(Urls.isUsableTileAddress("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{y}{r}.png"))
    }

    @Test
    fun aFragmentIsRejectedInATemplateToo() {
        // the base address case is covered below. Nothing after a fragment is ever sent, so every
        // tile would resolve to whatever precedes it
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/#{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://map.example.org/#{z}/{x}/{y}?key=abc"))
    }

    @Test
    fun percentEncodedPlaceholdersAreRejected() {
        // a browser address bar encodes braces, so a copy paste arrives in this shape. With none
        // of the three counted it would otherwise be taken for a base address, have a tile path
        // appended, and reach the server with the encoded braces still in it
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/%7Bz%7D/%7Bx%7D/%7By%7D.png"))
        assertFalse(Urls.isUsableTileAddress("https://%7Bs%7D.tile.openstreetmap.org/"))
    }

    @Test
    fun aRepeatedPlaceholderSubstitutesConsistently() {
        assertEquals("https://tiles.example.org/14/8189/8188/14.png",
                Urls.tileUrl("https://tiles.example.org/{z}/{x}/{y}/{z}.png", 14, 8189, 8188))
    }

    @Test
    fun aBaseAddressCarryingAnUnknownPlaceholderIsRejectedToo() {
        // the same fatal address reached through the other door: with no known placeholders to
        // count, an earlier version treated these as base addresses and completed them
        assertFalse(Urls.isUsableTileAddress("https://{s}.tile.openstreetmap.org/"))
        assertFalse(Urls.isUsableTileAddress("https://{s}.tiles.example.org"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{style}"))
    }

    @Test
    fun placeholdersSpelledInCapitalsAreRejectedRatherThanTakenAsABaseAddress() {
        // nothing lowercases these, so counted as zero they would be taken for a base address and
        // have a tile path appended after them
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{Z}/{X}/{Y}.png"))
    }

    @Test
    fun anInvertedYTemplateIsRejected() {
        // TMS numbers y from the bottom. This code neither inverts nor substitutes {-y}, so it is
        // refused as an unsupported placeholder rather than left to reach the server literally
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/{z}/{x}/{-y}.png"))
    }

    @Test
    fun aBaseAddressWithAQueryOrFragmentIsRejected() {
        // the appended tile path would land inside the query value rather than in the path
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/?key=abc"))
        // and after a fragment it would never be sent at all
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/#section"))
    }

    @Test
    fun anAddressWithNoHostIsRejected() {
        // none of these names a server, by one route or another: an empty host, a host that is
        // only userinfo, a port with no host, an unparseable port, or an address pasted twice.
        // Three hand rolled versions of the check each let a different one of them through
        assertFalse(Urls.isUsableTileAddress("https:///"))
        assertFalse(Urls.isUsableTileAddress("https:///tiles/{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://:8080/tiles"))
        assertFalse(Urls.isUsableTileAddress("https://user@/tiles"))
        assertFalse(Urls.isUsableTileAddress("https://user:pass@/{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://user@:8080/tiles"))
        assertFalse(Urls.isUsableTileAddress("https://https://tiles.example.org/"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org:abc/"))
    }

    @Test
    fun credentialsInTheAddressAreRejected() {
        // no http client here turns userinfo into an auth header, so it cannot work, and it would
        // sit in plain view in the settings row
        assertFalse(Urls.isUsableTileAddress("https://user:secret@tiles.example.org/{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://user@tiles.example.org/"))
    }

    @Test
    fun whitespaceOtherThanASpaceIsRejectedInTheHostToo() {
        assertFalse(Urls.isUsableTileAddress("https://tiles\texample.org/"))
        assertFalse(Urls.isUsableTileAddress("https://tiles\nexample.org/"))
    }

    @Test
    fun anIpv6LiteralIsAccepted() {
        // bracketed literals are the shape most likely to break an authority check on the next edit
        assertTrue(Urls.isUsableTileAddress("https://[2001:db8::1]/{z}/{x}/{y}.png"))
        assertTrue(Urls.isUsableTileAddress("https://[::1]:8080/"))
    }

    @Test
    fun aPlaceholderInTheHostIsRejected() {
        // substituting here gives a host that changes as the user pans, which is the subdomain
        // sharding mistake wearing a placeholder this code does substitute
        assertFalse(Urls.isUsableTileAddress("https://{z}.tiles.example.org/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://%7Bz%7D.tiles.example.org/{x}/{y}.png"))
    }

    @Test
    fun whitespaceAnywhereIsRejected() {
        // trimming the field only strips the ends, so anything interior survives to the request
        assertFalse(Urls.isUsableTileAddress("https://tiles example.org/"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/my tiles/{z}/{x}/{y}.png"))
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/a	b/{z}/{x}/{y}.png"))
    }

    @Test
    fun aHostnameTheFetcherWouldAcceptIsNotRefused() {
        // an underscore and a non ascii name are both ordinary on a home network and both accepted
        // by the clients that do the fetching. A stricter validator refuses a working address
        assertTrue(Urls.isUsableTileAddress("https://tile_server.lan/{z}/{x}/{y}.png"))
        assertTrue(Urls.isUsableTileAddress("https://münchen.example.org/"))
        // odd, but the userinfo is empty and the host is real, so this fetches. Refusing it would
        // be the same over strictness in a different spelling
        assertTrue(Urls.isUsableTileAddress("https://@tiles.example.org/"))
    }

    @Test
    fun aPortAndAnIpLiteralAreStillAccepted() {
        // the shape a self hoster actually types, and the reason the authority check cannot simply
        // refuse a colon
        assertTrue(Urls.isUsableTileAddress("https://192.168.1.10:8080/{z}/{x}/{y}.png"))
        assertTrue(Urls.isUsableTileAddress("https://tiles.example.org:8443/"))
    }

    @Test
    fun anEncodedBraceIsMatchedWhicheverCaseItIsWrittenIn() {
        // percent encoding is case insensitive, so a lowercase %7b has to be caught as well
        assertFalse(Urls.isUsableTileAddress("https://tiles.example.org/%7bz%7d/%7bx%7d/%7by%7d.png"))
    }

    @Test
    fun aTileAddressIsBuiltFromTheTemplate() {
        assertEquals("https://tiles.example.org/14/8189/8188.png",
                Urls.tileUrl("https://tiles.example.org/{z}/{x}/{y}.png", 14, 8189, 8188))
        assertEquals("https://tiles.example.org/tile/3/1/2@2x.jpg?key=abc",
                Urls.tileUrl("https://tiles.example.org/tile/{z}/{x}/{y}@2x.jpg?key=abc", 3, 1, 2))
    }

    @Test
    fun aCompletedBaseAddressBuildsTheUsualTilePath() {
        val template = Urls.asTileTemplate("https://tiles.example.org/basecase")
        assertEquals("https://tiles.example.org/basecase/14/8189/8188.png",
                Urls.tileUrl(template, 14, 8189, 8188))
    }

    @Test
    fun anUnknownSchemeAndAMissingSchemeAreBothRejected() {
        // deliberately not asserting the http branch, which differs by build variant, so this
        // suite says the same thing whichever variant runs it
        assertFalse(Urls.isUsableTileAddress("ftp://tiles.example.org/"))
        assertFalse(Urls.isUsableTileAddress("tiles.example.org"))
    }

    @Test
    fun aSchemeWithNothingAfterItIsRejected() {
        // not an address, and it would otherwise be completed and produce a blank map
        assertFalse(Urls.isUsableTileAddress("https://"))
        assertFalse(Urls.isAcceptableUrl("https://"))
    }

    @Test
    fun theSchemeIsMatchedWithoutRegardToCase() {
        // a keyboard that capitalises the first letter of a field produces this, and the scheme is
        // case insensitive per RFC 3986
        assertTrue(Urls.isAcceptableUrl("HTTPS://tiles.example.org/"))
        assertTrue(Urls.isAcceptableUrl("Https://tiles.example.org/"))
    }

    @Test
    fun aNullAddressIsRejectedRatherThanThrowing() {
        // the stored value is null whenever the user has never set one. Every caller happens to
        // check emptiness first, so this is the guard staying true rather than a reachable path
        assertFalse(Urls.isAcceptableUrl(null))
        assertFalse(Urls.isUsableTileAddress(null))
    }

    @Test
    fun aBaseAddressIsCompletedIntoATemplate() {
        assertEquals("https://tiles.example.org/{z}/{x}/{y}.png",
                Urls.asTileTemplate("https://tiles.example.org/"))
        assertEquals("https://tiles.example.org/{z}/{x}/{y}.png",
                Urls.asTileTemplate("https://tiles.example.org"))
        assertEquals("https://tiles.example.org/osm/{z}/{x}/{y}.png",
                Urls.asTileTemplate("https://tiles.example.org/osm"))
    }

    @Test
    fun aCompleteTemplateIsLeftExactlyAsItIs() {
        val template = "https://tiles.example.org/tile/{z}/{x}/{y}@2x.jpg?key=abc"
        assertEquals(template, Urls.asTileTemplate(template))
    }
}
