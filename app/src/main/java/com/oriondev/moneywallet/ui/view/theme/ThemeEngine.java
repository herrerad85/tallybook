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

package com.oriondev.moneywallet.ui.view.theme;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import androidx.annotation.UiThread;
import androidx.core.graphics.ColorUtils;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.color.DynamicColors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This class is responsible to dynamically theme the user interface at runtime.
 */
public class ThemeEngine implements ITheme {

    private static final String FILE_NAME = "theme.config";
    private static final String COLOR_PRIMARY = "color_primary";
    private static final String COLOR_PRIMARY_DARK = "color_primary_dark";
    private static final String COLOR_ACCENT = "color_accent";
    private static final String MODE = "mode";
    private static final String SYSTEM_COLORS = "system_colors";

    private static final int DEFAULT_COLOR_PRIMARY = Color.parseColor("#3f51b5");
    private static final int DEFAULT_COLOR_PRIMARY_DARK = Color.parseColor("#303F9F");
    private static final int DEFAULT_COLOR_ACCENT = Color.parseColor("#FF4081");
    private static final Mode DEFAULT_MODE = Mode.LIGHT;

    private static final int[] DEFAULT_TEXT_COLOR_PRIMARY = new int[] {
            Color.parseColor("#DE000000"), // OK
            Color.parseColor("#FFFFFFFF"), // OK
            Color.parseColor("#FFFFFFFF")  // OK
    };

    private static final int[] DEFAULT_TEXT_COLOR_PRIMARY_INVERSE = new int[] {
            Color.parseColor("#DEFFFFFF"), // OK
            Color.parseColor("#FF000000"), // OK
            Color.parseColor("#FF000000")  // OK
    };

    private static final int[] DEFAULT_TEXT_COLOR_SECONDARY = new int[] {
            Color.parseColor("#8A000000"), // OK
            Color.parseColor("#B3FFFFFF"), // OK
            Color.parseColor("#B3FFFFFF")  // OK
    };

    private static final int[] DEFAULT_TEXT_COLOR_SECONDARY_INVERSE = new int[] {
            Color.parseColor("#8AFFFFFF"), // OK
            Color.parseColor("#B3000000"), // OK
            Color.parseColor("#B3000000")
    };

    private static final int[] DEFAULT_ICON_COLOR = new int[] {
            Color.parseColor("#8A000000"), // OK
            Color.parseColor("#8AFFFFFF"), // OK
            Color.parseColor("#8AFFFFFF")  // OK
    };

    private static final int[] DEFAULT_HINT_TEXT_COLOR = new int[] {
            Color.parseColor("#61000000"), // OK
            Color.parseColor("#80FFFFFF"), // OK
            Color.parseColor("#80FFFFFF")  // OK
    };

    private static final int[] DEFAULT_COLOR_CARD_BACKGROUND = new int[] {
            Color.parseColor("#FFFFFF"), // OK
            Color.parseColor("#424242"), // OK
            Color.parseColor("#000000")  // OK
    };

    private static final int[] DEFAULT_COLOR_WINDOW_FOREGROUND = new int[] {
            Color.parseColor("#FFFFFF"), // OK
            Color.parseColor("#303030"), // OK
            Color.parseColor("#000000")  // OK
    };

    private static final int[] DEFAULT_COLOR_WINDOW_BACKGROUND = new int[] {
            Color.parseColor("#FAFAFA"), // OK
            Color.parseColor("#212121"), // OK
            Color.parseColor("#000000")  // OK
    };

    private static final int[] DEFAULT_COLOR_RIPPLE = new int[] {
            Color.parseColor("#1f000000"), // OK
            Color.parseColor("#33ffffff"), // OK
            Color.parseColor("#33ffffff") // OK
    };

    private static final int[] DRAWER_BACKGROUND_COLOR = new int[] {
            Color.parseColor("#F9F9F9"),
            Color.parseColor("#303030"),
            Color.parseColor("#000000")
    };

    private static final int[] DRAWER_ICON_COLOR = new int[] {
            Color.parseColor("#8A000000"),
            Color.parseColor("#8AFFFFFF"),
            Color.parseColor("#8AFFFFFF")
    };

    private static final int[] DRAWER_TEXT_COLOR = new int[] {
            Color.parseColor("#DE000000"),
            Color.parseColor("#DEFFFFFF"),
            Color.parseColor("#DEFFFFFF")
    };

    private static final int[] DRAWER_SELECTED_ITEM_COLOR = new int[] {
            Color.parseColor("#E8E8E8"),
            Color.parseColor("#202020"),
            Color.parseColor("#202020")
    };

    private static final int INDEX_MODE_LIGHT = 0;
    private static final int INDEX_MODE_DARK = 1;
    private static final int INDEX_MODE_DEEP_DARK = 2;

    private static final List<ThemeObserver> mThemeObserverList = new ArrayList<>();

    private static ThemeEngine sInstance;

    private static Integer sPreviewColorPrimary;
    private static Integer sPreviewColorAccent;

    public static void initialize(Context context) {
        if (sInstance == null) {
            sInstance = new ThemeEngine(context);
        }
    }

    public static void registerObserver(ThemeObserver observer) {
        mThemeObserverList.add(observer);
    }

    public static void unregisterObserver(ThemeObserver observer) {
        mThemeObserverList.remove(observer);
    }

    /**
     * Shows a color without storing it, so a chooser can repaint the screen behind it while it
     * is open. The stored value is untouched, which is what makes cancelling free. Clear it from
     * clearPreview once the chooser is gone.
     * <p>
     * The color that is stored is never an override, and a chooser opens on it and can be walked
     * back to it. That is not only to save a repaint of every observer. The stored dark primary
     * is its own value while a previewed one is derived from the primary, and out of the box
     * those two are not the same color, so showing the stored primary as an override would move
     * the status bar to a color no screen will have once the chooser is gone.
     *
     * @return whether the screen is now showing something other than what is stored
     */
    @UiThread
    public static boolean previewColorPrimary(int colorPrimary) {
        if (sInstance == null) {
            return false;
        }
        Integer showing = colorPrimary == sInstance.getStoredColorPrimary() ? null : colorPrimary;
        if (!Objects.equals(showing, sPreviewColorPrimary)) {
            sPreviewColorPrimary = showing;
            notifyObservers();
        }
        return sPreviewColorPrimary != null;
    }

    @UiThread
    public static boolean previewColorAccent(int colorAccent) {
        if (sInstance == null) {
            return false;
        }
        Integer showing = colorAccent == sInstance.getStoredColorAccent() ? null : colorAccent;
        if (!Objects.equals(showing, sPreviewColorAccent)) {
            sPreviewColorAccent = showing;
            notifyObservers();
        }
        return sPreviewColorAccent != null;
    }

    @UiThread
    public static void clearPreview() {
        if (sPreviewColorPrimary == null && sPreviewColorAccent == null) {
            return;
        }
        // OK stores the color it was previewing, so by the time the chooser is gone the override
        // and the stored value are the same color and dropping it moves nothing on screen.
        boolean showsSomethingElse = sInstance == null
                || (sPreviewColorPrimary != null && sPreviewColorPrimary != sInstance.getStoredColorPrimary())
                || (sPreviewColorAccent != null && sPreviewColorAccent != sInstance.getStoredColorAccent());
        sPreviewColorPrimary = null;
        sPreviewColorAccent = null;
        if (showsSomethingElse) {
            notifyObservers();
        }
    }

    @UiThread
    public static void setColorPrimary(int colorPrimary) {
        if (sInstance != null) {
            // Against the stored value, never the getter: a preview of this same color is showing
            // while the chooser's OK button is what calls this, and comparing with the getter would
            // read that preview and skip the write.
            if (colorPrimary != sInstance.getStoredColorPrimary()) {
                sInstance.mPreferences.edit().putInt(COLOR_PRIMARY, colorPrimary).apply();
                sInstance.mPreferences.edit().putInt(COLOR_PRIMARY_DARK, Util.darkenColor(colorPrimary)).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    public static void setColorAccent(int colorAccent) {
        if (sInstance != null) {
            if (colorAccent != sInstance.getStoredColorAccent()) {
                sInstance.mPreferences.edit().putInt(COLOR_ACCENT, colorAccent).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    public static void setMode(Mode mode) {
        if (sInstance != null && mode != null) {
            if (mode != sInstance.getMode()) {
                sInstance.mPreferences.edit().putInt(MODE, mode.getIndex()).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    public static void setUseSystemColors(boolean useSystemColors) {
        if (sInstance != null) {
            if (useSystemColors != isSystemColorsEnabled()) {
                sInstance.mPreferences.edit().putBoolean(SYSTEM_COLORS, useSystemColors).apply();
                notifyObservers();
            }
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    /**
     * Whether the user asked for the wallpaper colors. Where dynamic color is not available the
     * app keeps the picked colors even while this is true.
     */
    public static boolean isSystemColorsEnabled() {
        if (sInstance != null) {
            return sInstance.mPreferences.getBoolean(SYSTEM_COLORS, false);
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    @UiThread
    private static void notifyObservers() {
        if (sInstance != null) {
            for (ThemeObserver observer : mThemeObserverList) {
                observer.onThemeChanged(sInstance);
            }
        }
    }

    public static ITheme getTheme() {
        if (sInstance != null) {
            return sInstance;
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    /**
     * The primary the user saved. While a chooser is open getColorPrimary answers with its preview
     * instead, and while the system colors are in use with the wallpaper color.
     */
    public static int getSavedColorPrimary() {
        if (sInstance != null) {
            return sInstance.getStoredColorPrimary();
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    public static int getSavedColorAccent() {
        if (sInstance != null) {
            return sInstance.getStoredColorAccent();
        } else {
            throw new RuntimeException("ThemeEngine not initialized!");
        }
    }

    public static void applyTheme(View view, boolean propagate) {
        if (view instanceof ThemeConsumer) {
            ((ThemeConsumer) view).onApplyTheme(sInstance);
        }
        if (propagate && view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                applyTheme(((ViewGroup) view).getChildAt(i), true);
            }
        }
    }

    private final Context mContext;
    private final SharedPreferences mPreferences;

    private ThemeEngine(Context context) {
        mContext = context.getApplicationContext();
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    // Each getter holds the override it read, since it can be cleared between a null check
    // and a use.
    @Override
    public int getColorPrimary() {
        Integer preview = sPreviewColorPrimary;
        if (preview != null) {
            return noAlpha(preview);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && usesSystemColors()) {
            return mContext.getColor(android.R.color.system_primary_light);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && usesSystemColors()) {
            return mContext.getColor(android.R.color.system_accent1_600);
        }
        return getStoredColorPrimary();
    }

    @Override
    public int getColorPrimaryDark() {
        Integer preview = sPreviewColorPrimary;
        if (preview != null) {
            return noAlpha(Util.darkenColor(preview));
        }
        return noAlpha(mPreferences.getInt(COLOR_PRIMARY_DARK, DEFAULT_COLOR_PRIMARY_DARK));
    }

    @Override
    public int getColorPrimaryBackground() {
        return getMode() == Mode.DEEP_DARK ? Color.BLACK : getColorPrimary();
    }

    @Override
    public int getColorAccent() {
        Integer preview = sPreviewColorAccent;
        if (preview != null) {
            return noAlpha(preview);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && usesSystemColors()) {
            return mContext.getColor(isDark() ? android.R.color.system_secondary_dark
                    : android.R.color.system_secondary_light);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && usesSystemColors()) {
            return mContext.getColor(isDark() ? android.R.color.system_accent2_200
                    : android.R.color.system_accent2_600);
        }
        return getStoredColorAccent();
    }

    private boolean usesSystemColors() {
        return mPreferences.getBoolean(SYSTEM_COLORS, false) && DynamicColors.isDynamicColorAvailable();
    }

    private int getStoredColorPrimary() {
        return noAlpha(mPreferences.getInt(COLOR_PRIMARY, DEFAULT_COLOR_PRIMARY));
    }

    private int getStoredColorAccent() {
        return noAlpha(mPreferences.getInt(COLOR_ACCENT, DEFAULT_COLOR_ACCENT));
    }

    private int noAlpha(int color) {
        return ColorUtils.setAlphaComponent(color, 255);
    }

    @Override
    public Mode getMode() {
        switch (mPreferences.getInt(MODE, DEFAULT_MODE.getIndex())) {
            case INDEX_MODE_LIGHT:
                return Mode.LIGHT;
            case INDEX_MODE_DARK:
                return Mode.DARK;
            case INDEX_MODE_DEEP_DARK:
                return Mode.DEEP_DARK;
            default:
                // the stored value has been manually altered
                return Mode.LIGHT;
        }
    }

    @Override
    public boolean isDark() {
        return getMode() != Mode.LIGHT;
    }

    @Override
    public int getTextColorPrimary() {
        return DEFAULT_TEXT_COLOR_PRIMARY[getMode().getIndex()];
    }

    @Override
    public int getTextColorSecondary() {
        return DEFAULT_TEXT_COLOR_SECONDARY[getMode().getIndex()];
    }

    @Override
    public int getTextColorPrimaryInverse() {
        return DEFAULT_TEXT_COLOR_PRIMARY_INVERSE[getMode().getIndex()];
    }

    @Override
    public int getTextColorSecondaryInverse() {
        return DEFAULT_TEXT_COLOR_SECONDARY_INVERSE[getMode().getIndex()];
    }

    @Override
    public int getColorCardBackground() {
        return DEFAULT_COLOR_CARD_BACKGROUND[getMode().getIndex()];
    }

    @Override
    public int getColorWindowForeground() {
        return DEFAULT_COLOR_WINDOW_FOREGROUND[getMode().getIndex()];
    }

    @Override
    public int getColorWindowBackground() {
        return DEFAULT_COLOR_WINDOW_BACKGROUND[getMode().getIndex()];
    }

    @Override
    public int getColorRipple() {
        return DEFAULT_COLOR_RIPPLE[getMode().getIndex()];
    }

    @Override
    public int getIconColor() {
        return DEFAULT_ICON_COLOR[getMode().getIndex()];
    }

    @Override
    public int getHintTextColor() {
        return DEFAULT_HINT_TEXT_COLOR[getMode().getIndex()];
    }

    @Override
    public int getErrorColor() {
        return Color.RED;
    }

    @Override
    public int getDrawerBackgroundColor() {
        return DRAWER_BACKGROUND_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerIconColor() {
        return DRAWER_ICON_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerSelectedIconColor() {
        return drawerSelectedForeground();
    }

    @Override
    public int getDrawerTextColor() {
        return DRAWER_TEXT_COLOR[getMode().getIndex()];
    }

    @Override
    public int getDrawerSelectedTextColor() {
        return drawerSelectedForeground();
    }

    // The opaque black or white, not the surface's own icon or text color: those are what the
    // closed entries are already drawn in.
    private int drawerSelectedForeground() {
        int background = getDrawerSelectedItemColor();
        return Util.visibleOr(getColorPrimary(), background, getBestColor(background));
    }

    @Override
    public int getDrawerSelectedItemColor() {
        return DRAWER_SELECTED_ITEM_COLOR[getMode().getIndex()];
    }

    @Override
    public int getBestColor(int background) {
        return Util.isColorLight(background) ? Color.BLACK : Color.WHITE;
    }

    @Override
    public int getBestTextColor(int background) {
        int index = Util.isColorLight(background) ? INDEX_MODE_LIGHT : INDEX_MODE_DARK;
        return DEFAULT_TEXT_COLOR_PRIMARY[index];
    }

    @Override
    public int getBestHintColor(int background) {
        int index = Util.isColorLight(background) ? INDEX_MODE_LIGHT : INDEX_MODE_DARK;
        return DEFAULT_HINT_TEXT_COLOR[index];
    }

    @Override
    public int getVisibleColor(int color, int fallback) {
        return Util.holdsUpAnywhere(this, color) ? color : fallback;
    }

    @Override
    public int getBestIconColor(int background) {
        int index = Util.isColorLight(background) ? INDEX_MODE_LIGHT : INDEX_MODE_DARK;
        return DEFAULT_ICON_COLOR[index];
    }

    private int getColorByMode(int colorLight, int colorDark) {
        switch (mPreferences.getInt(MODE, DEFAULT_MODE.getIndex())) {
            case INDEX_MODE_LIGHT:
                return colorLight;
            default:
                return colorDark;
        }
    }

    public enum Mode {

        LIGHT(INDEX_MODE_LIGHT),
        DARK(INDEX_MODE_DARK),
        DEEP_DARK(INDEX_MODE_DEEP_DARK);

        private final int mIndex;

        /*package-local*/ Mode(int index) {
            mIndex = index;
        }

        /*package-local*/ int getIndex() {
            return mIndex;
        }
    }

    public interface ThemeObserver {

        void onThemeChanged(ITheme theme);
    }

    public interface ThemeConsumer {

        void onApplyTheme(ITheme theme);
    }
}