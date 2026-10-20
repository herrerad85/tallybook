package com.oriondev.moneywallet.ui.view.theme;

import android.content.Context;
import android.os.Looper;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.shape.MaterialShapeDrawable;
import com.oriondev.moneywallet.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class AppBarLiftColorTest {

    private static final int HEADER = 0xFF3F51B5;

    @Test
    public void liftingKeepsTheHeaderColorInEveryTheme() {
        int[] themes = {R.style.MoneyWalletAppTheme, R.style.MoneyWalletAppThemeDark,
                R.style.MoneyWalletAppThemeDeepDark};
        for (int theme : themes) {
            Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(), theme);
            AppBarLayout appBar = new AppBarLayout(context);
            appBar.setBackgroundColor(HEADER);

            appBar.setLifted(true);
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2));

            MaterialShapeDrawable background = (MaterialShapeDrawable) appBar.getBackground();
            assertEquals(context.getResources().getResourceEntryName(theme),
                    HEADER, background.getFillColor().getDefaultColor());
        }
    }
}
