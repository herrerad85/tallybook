package com.oriondev.moneywallet.ui.view.theme;

import android.graphics.Color;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.fragment.dialog.GenericProgressDialog;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Holds the two dialog layouts that are inflated outside a dialog's own themed context to the dark
 * surface they land on.
 */
@RunWith(RobolectricTestRunner.class)
public class ThemedDialogDarkModeTest {

    private static final String TAG_DIALOG = "ThemedDialogDarkModeTest::Dialog";

    /** The mode lives in shared preferences the engine holds open, so it outlives the test. */
    @After
    public void restoreLightMode() {
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
    }

    @Test
    public void aTintedInputTakesItsTextAndHintFromTheDialogSurface() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();

            int lightCard = switchTo(ThemeEngine.Mode.LIGHT);
            EditText onLight = inflateInput(activity);
            ThemedDialog.tintInput(onLight);
            assertDarker("light mode text", onLight.getCurrentTextColor(), lightCard);
            assertDarker("light mode hint", onLight.getCurrentHintTextColor(), lightCard);
            assertFadedHint("light mode", onLight);

            int darkCard = switchTo(ThemeEngine.Mode.DARK);
            assertNotEquals("the card background did not change with the mode", lightCard, darkCard);
            EditText onDark = inflateInput(activity);
            ThemedDialog.tintInput(onDark);
            assertLighter("dark mode text", onDark.getCurrentTextColor(), darkCard);
            assertLighter("dark mode hint", onDark.getCurrentHintTextColor(), darkCard);
            assertFadedHint("dark mode", onDark);
        } finally {
            controller.close();
        }
    }

    @Test
    public void aProgressDialogInDarkModeDrawsItsMessageOnTheDarkSurface() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();
            int darkCard = switchTo(ThemeEngine.Mode.DARK);
            GenericProgressDialog dialog = GenericProgressDialog.newInstance(
                    R.string.title_backup_creation, R.string.message_async_init, false);
            dialog.show(activity.getSupportFragmentManager(), TAG_DIALOG);
            activity.getSupportFragmentManager().executePendingTransactions();
            assertNotNull(dialog.getDialog());
            TextView message = dialog.getDialog().findViewById(R.id.dialog_progress_message);
            assertNotNull(message);
            assertLighter("progress message", message.getCurrentTextColor(), darkCard);
        } finally {
            controller.close();
        }
    }

    @Test
    public void aProgressDialogInLightModeDrawsItsMessageOnTheLightSurface() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();
            int lightCard = switchTo(ThemeEngine.Mode.LIGHT);
            GenericProgressDialog dialog = GenericProgressDialog.newInstance(
                    R.string.title_backup_creation, R.string.message_async_init, false);
            dialog.show(activity.getSupportFragmentManager(), TAG_DIALOG);
            activity.getSupportFragmentManager().executePendingTransactions();
            assertNotNull(dialog.getDialog());
            TextView message = dialog.getDialog().findViewById(R.id.dialog_progress_message);
            assertNotNull(message);
            assertDarker("progress message", message.getCurrentTextColor(), lightCard);
        } finally {
            controller.close();
        }
    }

    /**
     * The WebDAV form is the one wrapped layout carrying plain widgets, so its colors come from
     * whatever context inflateScrollableView hands the inflater.
     */
    @Test
    public void theWebdavFormTakesItsColorsFromTheDialogSurface() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();

            int darkCard = switchTo(ThemeEngine.Mode.DARK);
            View onDark = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup);
            EditText darkUrl = onDark.findViewById(R.id.webdav_url_edit_text);
            TextView darkNote = onDark.findViewById(R.id.webdav_hint_text_view);
            assertNotNull(darkUrl);
            assertNotNull(darkNote);
            assertLighter("dark mode url text", darkUrl.getCurrentTextColor(), darkCard);
            assertLighter("dark mode url hint", darkUrl.getCurrentHintTextColor(), darkCard);
            assertLighter("dark mode note", darkNote.getCurrentTextColor(), darkCard);

            int lightCard = switchTo(ThemeEngine.Mode.LIGHT);
            View onLight = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup);
            EditText lightUrl = onLight.findViewById(R.id.webdav_url_edit_text);
            TextView lightNote = onLight.findViewById(R.id.webdav_hint_text_view);
            assertDarker("light mode url text", lightUrl.getCurrentTextColor(), lightCard);
            assertDarker("light mode url hint", lightUrl.getCurrentHintTextColor(), lightCard);
            assertDarker("light mode note", lightNote.getCurrentTextColor(), lightCard);
        } finally {
            controller.close();
        }
    }

    /**
     * Robolectric runs in touch mode, and View.isDefaultFocusHighlightNeeded returns false on that
     * gate before it ever reads the flag, so the flag is what a test here can assert in place of
     * the highlight the framework would otherwise paint over a focused root.
     */
    @Test
    public void theFocusableDialogRootsSuppressTheDefaultFocusHighlight() {
        ActivityController<AppCompatActivity> controller =
                Robolectric.buildActivity(AppCompatActivity.class).setup();
        try {
            AppCompatActivity activity = controller.get();

            View chooser = activity.getLayoutInflater().inflate(R.layout.dialog_color_chooser, null);
            assertFalse("the color chooser root still takes the highlight",
                    chooser.getDefaultFocusHighlightEnabled());

            View scroller = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup);
            assertFalse("the wrapping scroller still takes the highlight",
                    scroller.getDefaultFocusHighlightEnabled());

            View recycler = activity.getLayoutInflater().inflate(R.layout.dialog_recycler_view, null);
            assertFalse("the recycler root still takes the highlight",
                    recycler.getDefaultFocusHighlightEnabled());
        } finally {
            controller.close();
        }
    }

    /** The inflater a caller reaches for, which carries the activity's theme and not a dialog's. */
    private EditText inflateInput(AppCompatActivity activity) {
        return activity.getLayoutInflater()
                .inflate(R.layout.dialog_input, null)
                .findViewById(R.id.dialog_input_edit_text);
    }

    /** Switches the engine's mode and hands back the card background that mode carries. */
    private int switchTo(ThemeEngine.Mode mode) {
        ThemeEngine.setMode(mode);
        return ThemeEngine.getTheme().getColorCardBackground();
    }

    // Util.isColorLight is the single test getBestTextColor and getBestHintColor branch on, so a
    // color and a background landing on opposite sides of it is what the engine calls readable.

    private void assertLighter(String what, int color, int background) {
        assertFalse(what + ": the background is not dark", Util.isColorLight(background));
        assertTrue(what + ": the color is not light on it", Util.isColorLight(color));
    }

    private void assertDarker(String what, int color, int background) {
        assertTrue(what + ": the background is not light", Util.isColorLight(background));
        assertFalse(what + ": the color is not dark on it", Util.isColorLight(color));
    }

    /** A hint that carries as much alpha as the typed text stops reading as a hint. */
    private void assertFadedHint(String what, EditText editText) {
        assertTrue(what + ": the hint is faded past reading",
                Color.alpha(editText.getCurrentHintTextColor()) >= 64);
        assertTrue(what + ": the hint is not faded below the text",
                Color.alpha(editText.getCurrentHintTextColor())
                        < Color.alpha(editText.getCurrentTextColor()));
    }
}
