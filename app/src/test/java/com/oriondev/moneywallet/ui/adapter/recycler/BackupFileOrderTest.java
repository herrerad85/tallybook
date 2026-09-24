/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.adapter.recycler;

import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.model.LocalFile;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class BackupFileOrderTest {

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    @Test
    public void foldersComeFirstThenTheNewestBackup() throws IOException {
        List<IFile> files = new ArrayList<>();
        files.add(file("backup_2026-09-01_08-00-00.mwbx"));
        files.add(folder("archive"));
        files.add(file("backup_2026-09-23_21-15-00.mwbs"));
        files.add(folder("2025"));
        files.add(file("backup_2026-09-10_12-30-00.mwb"));
        BackupFileAdapter adapter = new BackupFileAdapter(null);
        adapter.setFileList(files, false);
        assertEquals(Arrays.asList("2025", "archive",
                "backup_2026-09-23_21-15-00.mwbs",
                "backup_2026-09-10_12-30-00.mwb",
                "backup_2026-09-01_08-00-00.mwbx"), names(files));
        adapter.addFileToList(file("backup_2026-09-24_09-30-00.mwbx"));
        assertEquals("backup_2026-09-24_09-30-00.mwbx", files.get(2).getName());
    }

    private IFile file(String name) throws IOException {
        return new LocalFile(mFolder.newFile(name));
    }

    private IFile folder(String name) throws IOException {
        return new LocalFile(mFolder.newFolder(name));
    }

    private static List<String> names(List<IFile> files) {
        List<String> names = new ArrayList<>();
        for (IFile file : files) {
            names.add(file.getName());
        }
        return names;
    }
}
