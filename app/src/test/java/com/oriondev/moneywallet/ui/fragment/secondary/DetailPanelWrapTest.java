package com.oriondev.moneywallet.ui.fragment.secondary;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.text.Layout;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.BackupListActivity;
import com.oriondev.moneywallet.ui.fragment.base.SecondaryPanelFragment;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.function.Consumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w320dp-420dpi")
public class DetailPanelWrapTest {

    private static final String TWO_LINE_NOTE = "First line of the note\nSecond line of the note";
    private static final String LONG_NAME = "A name long enough that it cannot fit on one line of the panel";
    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    @Before
    public void setUp() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void thePersonDetailsShowBothLinesOfTheNote() {
        ContentValues values = new ContentValues();
        values.put(Contract.Person.NAME, "Ann");
        values.put(Contract.Person.ICON, ICON);
        values.put(Contract.Person.NOTE, TWO_LINE_NOTE);
        long person = insert(DataContentProvider.CONTENT_PEOPLE, values);
        showPanel(new PersonItemFragment(), person, panel ->
                assertWholeTextShown(panel.findViewById(R.id.note_text_view), TWO_LINE_NOTE, 2));
    }

    @Test
    public void theWalletDetailsShowBothLinesOfTheNoteAndTheWholeGroup() {
        long wallet = insertWallet("EUR");
        showPanel(new WalletItemFragment(), wallet, panel -> {
            assertWholeTextShown(panel.findViewById(R.id.note_text_view), TWO_LINE_NOTE, 2);
            assertWholeTextShown(panel.findViewById(R.id.group_text_view), LONG_NAME, 2);
        });
    }

    @Test
    @Config(fontScale = 2.0f)
    public void theWalletDetailsShowTheWholeCurrencyNameAtTheLargestFont() {
        long wallet = insertWallet("ANG");
        showPanel(new WalletItemFragment(), wallet, panel ->
                assertWholeTextShown(panel.findViewById(R.id.currency_text_view),
                        "Netherlands Antillian Guilder", 1));
    }

    @Test
    public void theCategoryDetailsShowTheWholeParentName() {
        ContentValues parent = category(LONG_NAME);
        long parentId = insert(DataContentProvider.CONTENT_CATEGORIES, parent);
        ContentValues child = category("Child");
        child.put(Contract.Category.PARENT, parentId);
        long childId = insert(DataContentProvider.CONTENT_CATEGORIES, child);
        showPanel(new CategoryItemFragment(), childId, panel ->
                assertWholeTextShown(panel.findViewById(R.id.parent_category_text_view), LONG_NAME, 2));
    }

    private static long insertWallet(String currency) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, "Cash");
        values.put(Contract.Wallet.ICON, ICON);
        values.put(Contract.Wallet.CURRENCY, currency);
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        values.put(Contract.Wallet.GROUP, LONG_NAME);
        values.put(Contract.Wallet.NOTE, TWO_LINE_NOTE);
        return insert(DataContentProvider.CONTENT_WALLETS, values);
    }

    private static ContentValues category(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return values;
    }

    private static long insert(Uri uri, ContentValues values) {
        Context context = ApplicationProvider.getApplicationContext();
        return ContentUris.parseId(context.getContentResolver().insert(uri, values));
    }

    private static void showPanel(SecondaryPanelFragment fragment, long id, Consumer<View> check) {
        try (ActivityScenario<BackupListActivity> scenario =
                     ActivityScenario.launch(BackupListActivity.class)) {
            scenario.onActivity(activity -> {
                activity.getSupportFragmentManager()
                        .beginTransaction()
                        .add(android.R.id.content, fragment)
                        .commitNow();
                fragment.showItemId(id);
                View wheel = fragment.requireView().findViewById(R.id.secondary_panel_progress_wheel);
                for (int i = 0; i < 200 && wheel.getVisibility() != View.GONE; i++) {
                    shadowOf(Looper.getMainLooper()).idle();
                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        throw new AssertionError(e);
                    }
                }
                assertEquals("the item never loaded", View.GONE, wheel.getVisibility());
                shadowOf(Looper.getMainLooper()).idle();
                check.accept(fragment.requireView());
            });
        }
    }

    private static void assertWholeTextShown(TextView field, String expected, int minLines) {
        assertEquals(expected, field.getText().toString());
        assertEquals(View.VISIBLE, field.getVisibility());
        Layout layout = field.getLayout();
        assertTrue("\"" + field.getText() + "\" is laid out on " + layout.getLineCount()
                        + " line(s), so it was squeezed onto one line and cut",
                layout.getLineCount() >= minLines);
        int room = field.getWidth() - field.getCompoundPaddingLeft()
                - field.getCompoundPaddingRight();
        for (int line = 0; line < layout.getLineCount(); line++) {
            assertTrue("\"" + field.getText() + "\" line " + line + " is "
                            + layout.getLineMax(line) + "px in a field with " + room
                            + "px of room, so the rest of it is cut off",
                    layout.getLineMax(line) <= room);
            assertEquals("\"" + field.getText() + "\" line " + line + " ends in an ellipsis",
                    0, layout.getEllipsisCount(line));
        }
        int height = field.getHeight() - field.getPaddingTop() - field.getPaddingBottom();
        assertTrue("\"" + field.getText() + "\" needs " + layout.getHeight() + "px and "
                        + "the field has " + height + "px, so the last line is cut off",
                layout.getHeight() <= height);
    }
}
