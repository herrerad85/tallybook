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

package com.oriondev.moneywallet.api.webdav;

import com.oriondev.moneywallet.model.WebDAVFile;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the parts of the WebDAV client that do not touch the network: URL handling and the
 * PROPFIND response parser. Those are where the interoperability bugs live, because servers
 * disagree about href shape and namespace prefixes while the HTTP verbs themselves are trivial.
 */
public class WebDAVClientTest {

    private static List<WebDAVFile> parse(String xml, String basePath, String requestedPath) throws Exception {
        return WebDAVClient.parsePropfind(
                new ByteArrayInputStream(xml.getBytes(Charset.forName("UTF-8"))),
                basePath,
                requestedPath
        );
    }

    @Test
    public void normalizeBaseUrl_alwaysEndsWithExactlyOneSlash() {
        assertEquals("https://cloud.example.org/dav/", WebDAVClient.normalizeBaseUrl("https://cloud.example.org/dav"));
        assertEquals("https://cloud.example.org/dav/", WebDAVClient.normalizeBaseUrl("https://cloud.example.org/dav/"));
        assertEquals("https://cloud.example.org/dav/", WebDAVClient.normalizeBaseUrl("https://cloud.example.org/dav///"));
        assertEquals("https://cloud.example.org/dav/", WebDAVClient.normalizeBaseUrl("  https://cloud.example.org/dav  "));
    }

    @Test
    public void encodePath_escapesSegmentsButKeepsSeparators() {
        assertEquals("backups/my%20backup.zip", WebDAVClient.encodePath("backups/my backup.zip"));
        assertEquals("a/b/c.zip", WebDAVClient.encodePath("a/b/c.zip"));
        assertEquals("", WebDAVClient.encodePath(""));
    }

    @Test
    public void encodePath_escapesNonAsciiRatherThanSendingItRaw() {
        assertEquals("d%C3%A9penses/f%C3%A9vrier.zip", WebDAVClient.encodePath("dépenses/février.zip"));
    }

    @Test
    public void encodePath_usesPercentTwentyNotPlusForSpaces() {
        assertFalse(WebDAVClient.encodePath("two words").contains("+"));
    }

    @Test
    public void hrefToRelativePath_handlesAbsoluteUrlAndAbsolutePathForms() {
        assertEquals("backups/one.zip",
                WebDAVClient.hrefToRelativePath("https://cloud.example.org/dav/backups/one.zip", "/dav/"));
        assertEquals("backups/one.zip",
                WebDAVClient.hrefToRelativePath("/dav/backups/one.zip", "/dav/"));
    }

    @Test
    public void hrefToRelativePath_decodesAndTrimsTrailingSlashOnCollections() {
        assertEquals("my backup", WebDAVClient.hrefToRelativePath("/dav/my%20backup/", "/dav/"));
    }

    @Test
    public void trimSlashes_stripsBothEnds() {
        assertEquals("a/b", WebDAVClient.trimSlashes("/a/b/"));
        assertEquals("", WebDAVClient.trimSlashes("/"));
        assertEquals("", WebDAVClient.trimSlashes(null));
    }

    @Test
    public void nameOf_takesTheLastSegment() {
        assertEquals("one.zip", WebDAVClient.nameOf("backups/one.zip"));
        assertEquals("one.zip", WebDAVClient.nameOf("one.zip"));
    }

    @Test
    public void parsePropfind_readsChildrenAndDropsTheRequestedFolderItself() throws Exception {
        String xml = "<?xml version=\"1.0\"?>"
                + "<d:multistatus xmlns:d=\"DAV:\">"
                + "  <d:response><d:href>/dav/backups/</d:href><d:propstat><d:prop>"
                + "    <d:resourcetype><d:collection/></d:resourcetype>"
                + "  </d:prop></d:propstat></d:response>"
                + "  <d:response><d:href>/dav/backups/one.zip</d:href><d:propstat><d:prop>"
                + "    <d:resourcetype/><d:getcontentlength>1234</d:getcontentlength>"
                + "  </d:prop></d:propstat></d:response>"
                + "  <d:response><d:href>/dav/backups/nested/</d:href><d:propstat><d:prop>"
                + "    <d:resourcetype><d:collection/></d:resourcetype>"
                + "  </d:prop></d:propstat></d:response>"
                + "</d:multistatus>";

        List<WebDAVFile> files = parse(xml, "/dav/", "backups");

        assertEquals(2, files.size());
        assertEquals("one.zip", files.get(0).getName());
        assertEquals("backups/one.zip", files.get(0).getPath());
        assertEquals(1234L, files.get(0).getSize());
        assertFalse(files.get(0).isDirectory());
        assertEquals("nested", files.get(1).getName());
        assertTrue(files.get(1).isDirectory());
        assertEquals(0L, files.get(1).getSize());
    }

    /**
     * The reason the parser is namespace aware. Nextcloud, ownCloud and mod_dav do not agree on the
     * prefix, so matching the literal tag text would work against one server and fail against the
     * next.
     */
    @Test
    public void parsePropfind_ignoresTheNamespacePrefixTheServerChose() throws Exception {
        String xml = "<?xml version=\"1.0\"?>"
                + "<D:multistatus xmlns:D=\"DAV:\" xmlns:lp1=\"DAV:\">"
                + "  <D:response><D:href>https://host.example.org/dav/two.zip</D:href><D:propstat><D:prop>"
                + "    <lp1:resourcetype/><lp1:getcontentlength>7</lp1:getcontentlength>"
                + "  </D:prop></D:propstat></D:response>"
                + "</D:multistatus>";

        List<WebDAVFile> files = parse(xml, "/dav/", "");

        assertEquals(1, files.size());
        assertEquals("two.zip", files.get(0).getName());
        assertEquals(7L, files.get(0).getSize());
    }

    @Test
    public void parsePropfind_decodesPercentEncodedNames() throws Exception {
        String xml = "<?xml version=\"1.0\"?>"
                + "<d:multistatus xmlns:d=\"DAV:\">"
                + "  <d:response><d:href>/dav/my%20backup.zip</d:href><d:propstat><d:prop>"
                + "    <d:resourcetype/><d:getcontentlength>3</d:getcontentlength>"
                + "  </d:prop></d:propstat></d:response>"
                + "</d:multistatus>";

        List<WebDAVFile> files = parse(xml, "/dav/", "");

        assertEquals(1, files.size());
        assertEquals("my backup.zip", files.get(0).getName());
    }

    @Test
    public void parsePropfind_survivesAMissingContentLength() throws Exception {
        String xml = "<?xml version=\"1.0\"?>"
                + "<d:multistatus xmlns:d=\"DAV:\">"
                + "  <d:response><d:href>/dav/odd.zip</d:href><d:propstat><d:prop>"
                + "    <d:resourcetype/>"
                + "  </d:prop></d:propstat></d:response>"
                + "</d:multistatus>";

        List<WebDAVFile> files = parse(xml, "/dav/", "");

        assertEquals(1, files.size());
        assertEquals(0L, files.get(0).getSize());
    }
}
