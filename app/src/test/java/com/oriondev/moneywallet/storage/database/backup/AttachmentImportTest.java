package com.oriondev.moneywallet.storage.database.backup;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.storage.database.ImportException;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
public class AttachmentImportTest {

    @Rule
    public TemporaryFolder mFolder = new TemporaryFolder();

    @Rule
    public TemporaryFolder mOtherFolder = new TemporaryFolder();

    private Context mContext;
    private File mAttachmentFolder;

    @Before
    public void setUp() throws IOException {
        mContext = ApplicationProvider.getApplicationContext();
        mAttachmentFolder = new File(mFolder.getRoot(), "attachments");
        write(mAttachmentFolder, "receipt", "old receipt");
        write(mAttachmentFolder, "invoice", "old invoice");
    }

    @Test
    public void aFailureAfterTheFirstFileKeepsThePreviousAttachments() throws Exception {
        AbstractBackupImporter importer = importer(folder -> {
            write(folder, "statement", "new statement");
            throw new IOException("No space left on device");
        });

        try {
            importer.importAttachments(mAttachmentFolder);
            fail("the attachments were imported");
        } catch (ImportException e) {
            assertEquals("No space left on device", e.getMessage());
        }

        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void aRuntimeExceptionReachesTheCallerAndKeepsThePreviousAttachments() throws Exception {
        IllegalStateException failure = new IllegalStateException("zip entry is corrupt");
        AbstractBackupImporter importer = importer(folder -> {
            write(folder, "statement", "new statement");
            throw failure;
        });

        try {
            importer.importAttachments(mAttachmentFolder);
            fail("the attachments were imported");
        } catch (IllegalStateException e) {
            assertSame(failure, e);
        }

        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void anImportExceptionReachesTheCallerAndKeepsThePreviousAttachments() throws Exception {
        ImportException failure = new ImportException("Decryption failed: missing user password");
        AbstractBackupImporter importer = importer(folder -> {
            write(folder, "statement", "new statement");
            throw failure;
        });

        try {
            importer.importAttachments(mAttachmentFolder);
            fail("the attachments were imported");
        } catch (ImportException e) {
            assertSame(failure, e);
            assertEquals("Decryption failed: missing user password", e.getMessage());
        }

        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void aSuccessfulImportReplacesThePreviousAttachments() throws Exception {
        importer(folder -> {
            write(folder, "statement", "new statement");
            write(folder, "ticket", "new ticket");
        }).importAttachments(mAttachmentFolder);

        assertEquals(Arrays.asList("statement", "ticket"), names(mAttachmentFolder));
        assertEquals("new statement", read(mAttachmentFolder, "statement"));
        assertEquals("new ticket", read(mAttachmentFolder, "ticket"));
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void aStagingFolderLeftByAnEarlierRestoreIsNotImported() throws Exception {
        write(new File(mFolder.getRoot(), "attachments.staging"), "stale", "stale file");

        importer(folder -> write(folder, "statement", "new statement")).importAttachments(mAttachmentFolder);

        assertEquals(Collections.singletonList("statement"), names(mAttachmentFolder));
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void anAsideFolderBesideTheAttachmentsIsDeleted() throws Exception {
        write(new File(mFolder.getRoot(), "attachments.bk"), "stale", "stale file");

        importFailure(folder -> {
            throw new IOException("No space left on device");
        });

        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void anAsideFolderWithoutTheAttachmentFolderIsPutBackBeforeTheImport() throws Exception {
        FileUtils.moveDirectory(mAttachmentFolder, new File(mFolder.getRoot(), "attachments.bk"));
        List<String> handed = new ArrayList<>();

        importFailure(folder -> {
            handed.addAll(names(mAttachmentFolder));
            throw new IOException("No space left on device");
        });

        assertEquals(Arrays.asList("invoice", "receipt"), handed);
        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void anAsideFolderBesideAnEmptyAttachmentFolderIsPutBack() throws Exception {
        FileUtils.moveDirectory(mAttachmentFolder, new File(mFolder.getRoot(), "attachments.bk"));
        FileUtils.forceMkdir(mAttachmentFolder);

        importFailure(folder -> {
            throw new IOException("No space left on device");
        });

        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void anAsideFolderWithoutTheAttachmentFolderIsReplacedByASuccessfulImport() throws Exception {
        FileUtils.moveDirectory(mAttachmentFolder, new File(mFolder.getRoot(), "attachments.bk"));

        importer(folder -> write(folder, "statement", "new statement")).importAttachments(mAttachmentFolder);

        assertEquals(Collections.singletonList("statement"), names(mAttachmentFolder));
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void theAttachmentsStayWhenTheyCannotBeMovedAside() throws Exception {
        String message = importFailure(folder -> {
            write(folder, "statement", "new statement");
            assertTrue(new File(mFolder.getRoot(), "attachments.bk").createNewFile());
        });

        assertEquals("Cannot move the previous attachments aside", message);
        assertPreviousAttachments();
        assertEquals(Arrays.asList("attachments", "attachments.bk"), names(mFolder.getRoot()));
        assertTrue(new File(mFolder.getRoot(), "attachments.bk").isFile());
    }

    @Test
    public void theAttachmentsArePutBackWhenTheImportCannotTakeTheirPlace() throws Exception {
        String message = importFailure(folder -> {
            write(folder, "statement", "new statement");
            FileUtils.deleteDirectory(folder);
        });

        assertEquals("Cannot put the imported attachments in place", message);
        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
    }

    @Test
    public void theStagingFolderIsRemovedWhenItCannotTakeThePlaceOfThePreviousAttachments() throws Exception {
        File parent = mOtherFolder.getRoot();
        File attachmentFolder = new File(mFolder.getRoot(), "attachments") {

            @Override
            public File getParentFile() {
                return parent;
            }

        };

        try {
            importer(folder -> assertTrue(folder.setWritable(false))).importAttachments(attachmentFolder);
            fail("the attachments were imported");
        } catch (ImportException e) {
            assertEquals("Cannot put the imported attachments in place", e.getMessage());
        }

        assertPreviousAttachments();
        assertEquals(Collections.singletonList("attachments"), names(mFolder.getRoot()));
        assertEquals(Collections.<String>emptyList(), names(parent));
    }

    private String importFailure(Extraction extraction) {
        try {
            importer(extraction).importAttachments(mAttachmentFolder);
        } catch (ImportException e) {
            return e.getMessage();
        }
        fail("the attachments were imported");
        return null;
    }

    private void assertPreviousAttachments() throws IOException {
        assertEquals(Arrays.asList("invoice", "receipt"), names(mAttachmentFolder));
        assertEquals("old invoice", read(mAttachmentFolder, "invoice"));
        assertEquals("old receipt", read(mAttachmentFolder, "receipt"));
    }

    private AbstractBackupImporter importer(Extraction extraction) {
        return new AbstractBackupImporter(mContext, new File(mFolder.getRoot(), "backup.mwbx")) {

            @Override
            protected void importDatabase(@NonNull File temporaryFolder) {
                throw new UnsupportedOperationException();
            }

            @Override
            protected void importAttachmentFiles(File attachmentFolder) throws IOException, ImportException {
                extraction.extract(attachmentFolder);
            }

        };
    }

    private static void write(File folder, String name, String content) throws IOException {
        FileUtils.writeStringToFile(new File(folder, name), content, StandardCharsets.UTF_8);
    }

    private static String read(File folder, String name) throws IOException {
        return FileUtils.readFileToString(new File(folder, name), StandardCharsets.UTF_8);
    }

    private static List<String> names(File folder) {
        String[] children = folder.list();
        List<String> names = new ArrayList<>(children != null ? Arrays.asList(children) : Collections.<String>emptyList());
        Collections.sort(names);
        return names;
    }

    private interface Extraction {

        void extract(File folder) throws IOException, ImportException;
    }
}
