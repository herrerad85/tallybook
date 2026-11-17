package com.oriondev.moneywallet.service;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;

import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AttachmentHandlerIntentServiceTest {

    private static final String[] NOT_PLAIN_FILE_NAMES = {"", ".", "..", "../marker", "..\\marker"};

    @Rule
    public TemporaryFolder mSource = new TemporaryFolder();

    private Context mContext;
    private File mExternalFolder;
    private File mAttachmentFolder;
    private final List<Intent> mBroadcasts = new ArrayList<>();

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(mContext);
        mExternalFolder = mContext.getExternalFilesDir(null);
        mAttachmentFolder = new File(mExternalFolder, Attachment.FOLDER_NAME);
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FINISHED);
        filter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FAILED);
        LocalBroadcastManager.getInstance(mContext).registerReceiver(new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                mBroadcasts.add(intent);
            }

        }, filter);
    }

    @Test
    public void aDeleteOfANameThatIsNotAPlainFileNameFailsAndDeletesNothing() throws IOException {
        for (String name : NOT_PLAIN_FILE_NAMES) {
            write(new File(mAttachmentFolder, "kept"), "kept attachment");
            write(new File(mExternalFolder, "marker"), "marker");

            run(AttachmentHandlerIntentService.ACTION_DELETE, name, null);

            assertFailed(name);
            assertEquals(name, "kept attachment", read(new File(mAttachmentFolder, "kept")));
            assertEquals(name, "marker", read(new File(mExternalFolder, "marker")));
        }
    }

    @Test
    public void aDeleteOfAPlainFileNameDeletesOnlyThatFile() throws IOException {
        write(new File(mAttachmentFolder, "kept"), "kept attachment");
        write(new File(mAttachmentFolder, "3f9c0a7d1e"), "receipt");

        run(AttachmentHandlerIntentService.ACTION_DELETE, "3f9c0a7d1e", null);

        assertEquals(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, last().getAction());
        assertFalse(new File(mAttachmentFolder, "3f9c0a7d1e").exists());
        assertEquals("kept attachment", read(new File(mAttachmentFolder, "kept")));
    }

    @Test
    public void aCreateOfANameThatIsNotAPlainFileNameFailsAndCreatesNothing() throws IOException {
        File source = mSource.newFile("receipt.pdf");
        write(source, "receipt");
        for (String name : NOT_PLAIN_FILE_NAMES) {
            FileUtils.deleteQuietly(new File(mExternalFolder, "marker"));

            run(AttachmentHandlerIntentService.ACTION_CREATE, name, Uri.fromFile(source));

            assertFailed(name);
            assertFalse(name, new File(mExternalFolder, "marker").exists());
            assertFalse(name, new File(mAttachmentFolder, name).isFile());
            assertEquals(name, 0, countAttachments());
        }
    }

    @Test
    public void aCreateOfAPlainFileNameCopiesTheFileIntoTheAttachmentFolder() throws IOException {
        File source = mSource.newFile("receipt.pdf");
        write(source, "receipt");

        run(AttachmentHandlerIntentService.ACTION_CREATE, "3f9c0a7d1e", Uri.fromFile(source));

        assertEquals(LocalAction.ACTION_ATTACHMENT_OP_FINISHED, last().getAction());
        assertEquals("receipt", read(new File(mAttachmentFolder, "3f9c0a7d1e")));
        assertEquals(1, countAttachments());
    }

    private void run(int action, String name, Uri data) {
        Intent intent = new Intent(mContext, AttachmentHandlerIntentService.class)
                .putExtra(AttachmentHandlerIntentService.ACTION, action)
                .putExtra(AttachmentHandlerIntentService.ATTACHMENT,
                        new Attachment(0L, name, "receipt.pdf", "application/pdf", 7L))
                .setData(data);
        Robolectric.setupService(AttachmentHandlerIntentService.class).onHandleIntent(intent);
        shadowOf(Looper.getMainLooper()).idle();
    }

    private void assertFailed(String name) {
        Intent intent = last();
        assertEquals(name, LocalAction.ACTION_ATTACHMENT_OP_FAILED, intent.getAction());
        assertEquals(name, "Invalid attachment file name", intent.getStringExtra(AttachmentHandlerIntentService.ERROR));
    }

    private Intent last() {
        assertFalse(mBroadcasts.isEmpty());
        return mBroadcasts.get(mBroadcasts.size() - 1);
    }

    private int countAttachments() {
        Cursor cursor = mContext.getContentResolver().query(DataContentProvider.CONTENT_ATTACHMENTS, null, null, null, null);
        assertNotNull(cursor);
        try {
            return cursor.getCount();
        } finally {
            cursor.close();
        }
    }

    private static void write(File file, String content) throws IOException {
        FileUtils.writeStringToFile(file, content, StandardCharsets.UTF_8);
    }

    private static String read(File file) throws IOException {
        return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }
}
