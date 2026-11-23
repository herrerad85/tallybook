package com.oriondev.moneywallet.ui.adapter.recycler;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.ContextThemeWrapper;
import android.widget.FrameLayout;

import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.util.ReflectionHelpers;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class HeaderBackgroundTest {

    private static final int GREEN = Color.parseColor("#4CAF50");

    private static RecyclerView.ViewHolder headerHolder(boolean headerOpensReport) {
        TransactionCursorAdapter adapter = new TransactionCursorAdapter(new RecordsNothing(), headerOpensReport);
        FrameLayout parent = new FrameLayout(new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme));
        return adapter.onCreateViewHolder(parent, TransactionHeaderCursor.TYPE_HEADER);
    }

    private static Drawable headerBackground(boolean headerOpensReport) {
        return headerHolder(headerOpensReport).itemView.getBackground();
    }

    private static int headerColor() {
        switch (ThemeEngine.getTheme().getMode()) {
            case DARK:
            case DEEP_DARK:
                return ColorUtils.setAlphaComponent(Color.WHITE, 0x1F);
            default:
                return ColorUtils.setAlphaComponent(ThemeEngine.getTheme().getColorPrimary(), 0x29);
        }
    }

    @Test
    public void aHeaderThatOpensTheReportKeepsItsRippleOverTheColor() {
        Drawable background = headerBackground(true);
        assertEquals(LayerDrawable.class, background.getClass());
        LayerDrawable layers = (LayerDrawable) background;
        assertEquals(2, layers.getNumberOfLayers());
        assertTrue(layers.getDrawable(0) instanceof ColorDrawable);
        assertEquals(headerColor(), ((ColorDrawable) layers.getDrawable(0)).getColor());
        assertTrue(layers.getDrawable(1) instanceof RippleDrawable);
    }

    @Test
    public void theRippleOfAHeaderThatOpensTheReportRedrawsThroughTheLayers() {
        RecyclerView.ViewHolder holder = headerHolder(true);
        LayerDrawable layers = (LayerDrawable) holder.itemView.getBackground();
        Drawable ripple = layers.getDrawable(1);
        assertNotNull(ripple.getCallback());
        assertSame(layers, ripple.getCallback());
        assertSame(holder.itemView, layers.getCallback());
    }

    @Test
    public void aHeaderThatOpensNothingHasTheColorAlone() {
        Drawable background = headerBackground(false);
        assertTrue(background instanceof ColorDrawable);
        assertEquals(headerColor(), ((ColorDrawable) background).getColor());
    }

    @Test
    public void theColorFollowsTheStoredPrimary() {
        int primary = ThemeEngine.getSavedColorPrimary();
        SharedPreferences preferences = ReflectionHelpers.getField(
                ReflectionHelpers.getStaticField(ThemeEngine.class, "sInstance"), "mPreferences");
        String colorPrimaryDark = ReflectionHelpers.getStaticField(ThemeEngine.class, "COLOR_PRIMARY_DARK");
        boolean primaryDarkStored = preferences.contains(colorPrimaryDark);
        int primaryDark = preferences.getInt(colorPrimaryDark, 0);
        try {
            ThemeEngine.setColorPrimary(GREEN);
            assertEquals(GREEN, ThemeEngine.getTheme().getColorPrimary());
            Drawable background = headerBackground(false);
            assertEquals(ColorUtils.setAlphaComponent(GREEN, 0x29), ((ColorDrawable) background).getColor());
        } finally {
            ThemeEngine.setColorPrimary(primary);
            if (primaryDarkStored) {
                preferences.edit().putInt(colorPrimaryDark, primaryDark).apply();
            } else {
                preferences.edit().remove(colorPrimaryDark).apply();
            }
        }
    }

    @Test
    public void deepDarkTintsTheHeaderWithWhiteAndNotWithThePrimary() {
        ThemeEngine.Mode mode = ThemeEngine.getTheme().getMode();
        try {
            ThemeEngine.setMode(ThemeEngine.Mode.DEEP_DARK);
            assertNotEquals(ColorUtils.setAlphaComponent(ThemeEngine.getTheme().getColorPrimary(), 0x1F), headerColor());
            Drawable background = headerBackground(false);
            assertEquals(headerColor(), ((ColorDrawable) background).getColor());
        } finally {
            ThemeEngine.setMode(mode);
        }
    }

    @Test
    public void darkTintsTheHeaderWithWhiteAndNotWithThePrimary() {
        ThemeEngine.Mode mode = ThemeEngine.getTheme().getMode();
        try {
            ThemeEngine.setMode(ThemeEngine.Mode.DARK);
            int white = ColorUtils.setAlphaComponent(Color.WHITE, 0x1F);
            assertNotEquals(ColorUtils.setAlphaComponent(ThemeEngine.getTheme().getColorPrimary(), 0x1F), white);
            assertNotEquals(ColorUtils.setAlphaComponent(ThemeEngine.getTheme().getColorPrimary(), 0x3D), white);
            Drawable background = headerBackground(false);
            assertEquals(white, ((ColorDrawable) background).getColor());
        } finally {
            ThemeEngine.setMode(mode);
        }
    }

    private static class RecordsNothing implements TransactionCursorAdapter.ActionListener {

        @Override
        public void onHeaderClick(Date startDate, Date endDate) {
        }

        @Override
        public void onTransactionClick(long id) {
        }

        @Override
        public void onSelectionChanged(int count) {
        }
    }
}
