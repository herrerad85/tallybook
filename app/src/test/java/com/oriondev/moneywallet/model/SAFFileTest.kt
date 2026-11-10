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

package com.oriondev.moneywallet.model;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Regression coverage for issue #177: a legacy auto-backup location persisted by older versions
 * (a raw filesystem path or a bare content URI) must NOT be treated as an encoded SAFFile
 * descriptor. Older builds passed such values straight into the JSON decoder, which threw inside
 * Application.onCreate and prevented the app from starting. {@link SAFFile#looksEncoded} is the
 * pure guard that lets callers fail soft instead.
 */
public class SAFFileTest {

    @Test
    public void legacyRawPathIsNotEncoded() {
        assertFalse(SAFFile.looksEncoded("/storage/emulated/0"));
        assertFalse(SAFFile.looksEncoded("content://com.android.externalstorage.documents/tree/primary"));
    }

    @Test
    public void nullOrBlankIsNotEncoded() {
        assertFalse(SAFFile.looksEncoded(null));
        assertFalse(SAFFile.looksEncoded(""));
        assertFalse(SAFFile.looksEncoded("   "));
    }

    @Test
    public void jsonDescriptorIsEncoded() {
        assertTrue(SAFFile.looksEncoded("{\"uri\":\"content://x\",\"name\":\"backups\"}"));
        assertTrue(SAFFile.looksEncoded("   {\"uri\":\"content://x\"}   "));
    }
}
