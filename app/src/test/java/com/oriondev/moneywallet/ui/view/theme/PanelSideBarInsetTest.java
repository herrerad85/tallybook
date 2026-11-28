package com.oriondev.moneywallet.ui.view.theme;

import android.app.Activity;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.CalculatorActivity;
import com.oriondev.moneywallet.ui.activity.CurrencyConverterActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
public class PanelSideBarInsetTest {

    private static final int BAR = 126;

    private ThemeEngine.Mode mOriginalMode;

    @Before
    public void lightTheme() {
        mOriginalMode = ThemeEngine.getTheme().getMode();
        ThemeEngine.setMode(ThemeEngine.Mode.LIGHT);
    }

    @After
    public void restoreTheMode() {
        ThemeEngine.setMode(mOriginalMode);
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    public void calculatorInLandscapeKeepsTheDisplayClearOfABarOnTheRight() {
        calculator(Insets.of(0, 0, BAR, 0), 0, BAR);
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    public void calculatorInLandscapeKeepsTheDisplayClearOfABarOnTheLeft() {
        calculator(Insets.of(BAR, 0, 0, 0), BAR, 0);
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port")
    public void calculatorInPortraitPadsOnlyTheKeypadBottom() {
        try (ActivityScenario<CalculatorActivity> scenario = ActivityScenario.launch(CalculatorActivity.class)) {
            scenario.onActivity(activity -> {
                dispatch(activity, Insets.of(0, 0, 0, BAR));
                View root = activity.findViewById(R.id.calculator_panel);
                assertEquals(0, root.getPaddingLeft());
                assertEquals(0, root.getPaddingRight());
                assertEquals(0, root.getPaddingBottom());
                View keypad = activity.findViewById(R.id.keypad_container);
                assertEquals(BAR, keypad.getPaddingBottom());
                assertEquals(0, keypad.getPaddingLeft());
                assertEquals(0, keypad.getPaddingRight());
                View wrapper = (View) activity.findViewById(R.id.display_text_view).getParent();
                assertEquals(ThemeEngine.getTheme().getColorWindowForeground(), color(wrapper));
            });
        }
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    public void converterInLandscapeKeepsTheRowsClearOfABarOnTheRight() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        try (ActivityScenario<CurrencyConverterActivity> scenario = ActivityScenario.launch(CurrencyConverterActivity.class)) {
            scenario.onActivity(activity -> {
                dispatch(activity, Insets.of(0, 0, BAR, 0));
                ITheme theme = ThemeEngine.getTheme();
                ViewGroup root = activity.findViewById(R.id.currency_converter_panel);
                assertEquals(0, root.getPaddingLeft());
                assertEquals(BAR, root.getPaddingRight());
                assertEquals(theme.getColorPrimaryBackground(), color(root));
                View lastRow = activity.findViewById(R.id.keypad_last_row);
                assertEquals(0, lastRow.getPaddingLeft());
                assertEquals(0, lastRow.getPaddingRight());
                View top = root.getChildAt(0);
                assertEquals(theme.getColorWindowForeground(), color(top));
                assertNotEquals(theme.getColorPrimaryBackground(), color(top));
                assertEquals(root.getWidth() - BAR, top.getRight());
            });
        }
    }

    @Test
    @Config(qualifiers = "w411dp-h914dp-port")
    public void converterInPortraitPadsOnlyTheLastRowBottom() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        try (ActivityScenario<CurrencyConverterActivity> scenario = ActivityScenario.launch(CurrencyConverterActivity.class)) {
            scenario.onActivity(activity -> {
                dispatch(activity, Insets.of(0, 0, 0, BAR));
                ViewGroup root = activity.findViewById(R.id.currency_converter_panel);
                assertEquals(0, root.getPaddingLeft());
                assertEquals(0, root.getPaddingRight());
                assertEquals(0, root.getPaddingBottom());
                assertEquals(BAR, activity.findViewById(R.id.keypad_last_row).getPaddingBottom());
                assertEquals(ThemeEngine.getTheme().getColorWindowForeground(), color(root.getChildAt(0)));
            });
        }
    }

    private static void calculator(Insets bar, int left, int right) {
        try (ActivityScenario<CalculatorActivity> scenario = ActivityScenario.launch(CalculatorActivity.class)) {
            scenario.onActivity(activity -> {
                dispatch(activity, bar);
                ITheme theme = ThemeEngine.getTheme();
                View root = activity.findViewById(R.id.calculator_panel);
                assertEquals(left, root.getPaddingLeft());
                assertEquals(right, root.getPaddingRight());
                assertEquals(theme.getColorPrimaryBackground(), color(root));
                View keypad = activity.findViewById(R.id.keypad_container);
                assertEquals(0, keypad.getPaddingLeft());
                assertEquals(0, keypad.getPaddingRight());
                View wrapper = (View) activity.findViewById(R.id.display_text_view).getParent();
                assertNotEquals(root, wrapper);
                assertEquals(theme.getColorWindowForeground(), color(wrapper));
                assertNotEquals(theme.getColorPrimaryBackground(), color(wrapper));
                assertEquals(left, wrapper.getLeft());
                assertEquals(root.getWidth() - right, wrapper.getRight());
            });
        }
    }

    private static void dispatch(Activity activity, Insets bar) {
        WindowInsetsCompat insets = new WindowInsetsCompat.Builder()
                .setInsets(WindowInsetsCompat.Type.navigationBars(), bar)
                .build();
        View content = ((ViewGroup) activity.findViewById(android.R.id.content)).getChildAt(0);
        ViewCompat.dispatchApplyWindowInsets(content, insets);
        content.measure(View.MeasureSpec.makeMeasureSpec(content.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(content.getHeight(), View.MeasureSpec.EXACTLY));
        content.layout(content.getLeft(), content.getTop(), content.getRight(), content.getBottom());
    }

    private static int color(View view) {
        Drawable background = view.getBackground();
        return background instanceof ColorDrawable ? ((ColorDrawable) background).getColor() : 0;
    }
}
