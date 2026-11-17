package com.oriondev.moneywallet.storage.database.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.SQLDatabaseImporter;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.model.Attachment;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class AttachmentExportTest {

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    private Context mContext;
    private File mAttachmentFolder;
    private final List<File> mExported = new ArrayList<>();

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mAttachmentFolder = new File(mFolder.getRoot(), "attachments");
        FileUtils.forceMkdir(mAttachmentFolder);
    }

    @Test
    public void aNameThatLeavesTheAttachmentFolderIsNotExported() throws Exception {
        write(mFolder.getRoot(), "statement", "outside statement");
        insert("../statement");

        exporter().exportAttachments(mAttachmentFolder);

        assertEquals(Collections.<File>emptyList(), mExported);
    }

    @Test
    public void aPlainNameWhoseFileIsInTheAttachmentFolderIsExported() throws Exception {
        write(mAttachmentFolder, "statement", "attached statement");
        insert("statement");

        exporter().exportAttachments(mAttachmentFolder);

        assertEquals(Collections.singletonList(new File(mAttachmentFolder, "statement")), mExported);
    }

    private void insert(String file) {
        Attachment attachment = new Attachment();
        attachment.mFile = file;
        attachment.mName = "receipt.pdf";
        attachment.mType = "application/pdf";
        attachment.mSize = 7L;
        attachment.mUUID = file;
        attachment.mLastEdit = 1L;
        SQLDatabaseImporter.insert(mContext.getContentResolver(), attachment);
    }

    private AbstractBackupExporter exporter() {
        return new AbstractBackupExporter(mContext.getContentResolver(), new File(mFolder.getRoot(), "backup.mwbx")) {

            @Override
            public void exportDatabase(@NonNull File tempFolder) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void exportAttachmentFiles(@NonNull List<File> fileList) {
                mExported.addAll(fileList);
            }

        };
    }

    private static void write(File folder, String name, String content) throws IOException {
        FileUtils.writeStringToFile(new File(folder, name), content, StandardCharsets.UTF_8);
    }
}
