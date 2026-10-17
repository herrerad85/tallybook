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

package com.oriondev.moneywallet.ui.activity.base;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import androidx.annotation.CallSuper;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.ViewGroupCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import android.view.View;
import android.view.ViewGroup;

import com.google.android.material.color.ColorResourcesOverride;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.color.DynamicColorsOptions;
import com.google.android.material.color.MaterialColorUtilitiesHelper;
import com.google.android.material.color.utilities.Hct;
import com.google.android.material.color.utilities.SchemeContent;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.view.theme.ITheme;
import com.oriondev.moneywallet.ui.view.theme.ThemeEngine;
import com.oriondev.moneywallet.utils.Utils;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * This activity is used as base activity for all the application activities.
 * It will automatically apply the current theme to all the views that are subscribed
 * to the ThemeEngine.
 * The first step is done during the inflation of the layout: here the theme properties are
 * automatically set to the view that is subscribed just after the creation.
 * The activity will than register itself as an observer for the current theme changes.
 * Whenever a property of the current theme changes, the observer will be notified.
 * Before the destruction the activity MUST un subscribe as observer to avoid memory leaks.
 */
public abstract class ThemedActivity extends AppCompatActivity implements ThemeEngine.ThemeObserver {

    private static final String THEMED_VIEW_PACKAGE = "com.oriondev.moneywallet.ui.view.theme.Themed";

    private static final Map<String, Constructor<?>> sThemedViewConstructors = new HashMap<>();

    private boolean mAppBarScrolledPastStatusBar;

    private ThemeEngine.Mode mThemeMode;

    private int mSeedColorPrimary;

    private boolean mSystemColors;

    /**
     * The XML theme for the mode, and the color roles seeded from the saved primary or taken from
     * the wallpaper, are set here, before any view exists, which is why a change of any of them
     * recreates the activity in onThemeChanged instead of repainting it. A previewed primary is not
     * saved, so it only repaints.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mThemeMode = ThemeEngine.getTheme().getMode();
        if (mThemeMode == ThemeEngine.Mode.DARK) {
            setTheme(R.style.MoneyWalletAppThemeDark);
        } else if (mThemeMode == ThemeEngine.Mode.DEEP_DARK) {
            setTheme(R.style.MoneyWalletAppThemeDeepDark);
        }
        mSeedColorPrimary = ThemeEngine.getSavedColorPrimary();
        mSystemColors = ThemeEngine.isSystemColorsEnabled();
        if (mSystemColors) {
            applySystemColors();
        } else {
            applySeededColors();
        }
        super.onCreate(savedInstanceState);
        // Both bars go transparent and the window stops reserving space for them, on every release
        // and not only where Android 15 enforces it, so one arrangement covers the whole range.
        // Each surface then asks for the insets it needs through SystemBars.
        WindowCompat.enableEdgeToEdge(getWindow());
        ThemeEngine.registerObserver(this);
    }

    /**
     * Workaround: does what DynamicColors.applyToActivityIfAvailable does with a content source,
     * except that a system contrast below the standard level is seeded at the standard level. From
     * Android 14 the library seeds at the system level and has no option to change it, and below
     * the standard level the secondary text in dialogs and pickers is too dim to read. A Samsung
     * S25 on One UI 8.5 ships at that level. The classes it calls are restricted to the library,
     * so a Material update can change them.
     * The personalized colors also set the surfaces, so deep dark puts its black surfaces back
     * once they are in.
     */
    @SuppressLint("RestrictedApi")
    private void applySeededColors() {
        if (!DynamicColors.isDynamicColorAvailable()) {
            return;
        }
        float contrast = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            UiModeManager uiModeManager = getSystemService(UiModeManager.class);
            if (uiModeManager != null) {
                contrast = Math.max(0, uiModeManager.getContrast());
            }
        }
        SchemeContent scheme = new SchemeContent(Hct.fromInt(mSeedColorPrimary),
                mThemeMode != ThemeEngine.Mode.LIGHT, contrast);
        ColorResourcesOverride override = ColorResourcesOverride.getInstance();
        if (override == null || !override.applyIfPossible(this,
                MaterialColorUtilitiesHelper.createColorResourcesIdsToColorValues(scheme))) {
            return;
        }
        if (mThemeMode == ThemeEngine.Mode.DEEP_DARK) {
            getTheme().applyStyle(R.style.ThemeOverlay_MoneyWallet_DeepDark, true);
        }
    }

    /**
     * The wallpaper colors also set the surfaces, so deep dark puts its black surfaces back, but
     * only once the library has applied them.
     */
    private void applySystemColors() {
        DynamicColors.applyToActivityIfAvailable(this, new DynamicColorsOptions.Builder()
                .setOnAppliedCallback(activity -> {
                    if (mThemeMode == ThemeEngine.Mode.DEEP_DARK) {
                        getTheme().applyStyle(R.style.ThemeOverlay_MoneyWallet_DeepDark, true);
                    }
                })
                .build());
    }

    /**
     * Themed views are handed the current theme as they are inflated. AppCompatActivity is itself
     * the layout inflater factory and routes through here every view it does not create itself, so
     * this is the hook for it.
     * <p>
     * This used to be a separate factory installed over AppCompat's by clearing the private
     * LayoutInflater.mFactorySet field through reflection. That field has not been reachable for
     * several Android releases, and the failure was caught and printed rather than raised, so the
     * factory was silently never installed. Nothing else themes the hierarchy at startup, so
     * onApplyTheme was running on nothing at all until the user changed a theme setting, which is
     * the one thing that walks the tree.
     */
    @Override
    public View onCreateView(View parent, String name, Context context, AttributeSet attrs) {
        View themed = onCreateThemedView(name, context, attrs);
        if (themed != null) {
            return themed;
        }
        return super.onCreateView(parent, name, context, attrs);
    }

    private View onCreateThemedView(String name, Context context, AttributeSet attrs) {
        if (!name.startsWith(THEMED_VIEW_PACKAGE)) {
            return null;
        }
        View view;
        try {
            view = (View) getThemedViewConstructor(name).newInstance(context, attrs);
        } catch (Exception e) {
            // let the normal inflation path build it: it uses the same constructor and will report
            // a genuinely missing class or constructor far better than this can
            e.printStackTrace();
            return null;
        }
        // deliberately outside the catch above. A view that was built correctly is worth keeping
        // even if theming it fails, and rebuilding it would run its constructor a second time
        ThemeEngine.applyTheme(view, false);
        return view;
    }

    /**
     * Cached because this runs for every themed view of every inflation, and a list row can carry a
     * dozen of them. LayoutInflater and AppCompat both keep the same kind of map for the same
     * reason. Bounded by the number of distinct themed tags in the layouts, currently about forty,
     * and it holds only names and constructors, so nothing with a lifecycle is retained.
     */
    private static Constructor<?> getThemedViewConstructor(String name) throws Exception {
        Constructor<?> constructor = sThemedViewConstructors.get(name);
        if (constructor == null) {
            constructor = Class.forName(name).getConstructor(Context.class, AttributeSet.class);
            sThemedViewConstructors.put(name, constructor);
        }
        return constructor;
    }

    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        onThemeSetup(ThemeEngine.getTheme());
    }

    @Override
    public void setContentView(int layoutResID) {
        super.setContentView(layoutResID);
        applyKeyboardInset();
    }

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        applyKeyboardInset();
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        super.setContentView(view, params);
        applyKeyboardInset();
    }

    /**
     * The window no longer resizes itself for the keyboard once it stops fitting system windows, so
     * the keyboard arrives as an inset and the content root is lifted by it here. Only the part of
     * the keyboard that reaches past the navigation bar is taken, because whatever sits at the
     * bottom of the screen has already asked SystemBars for the bar itself and the two would
     * otherwise stack.
     * <p>
     * installCompatInsetsDispatch is what makes the rest of this work below Android 11. There the
     * first child to consume an inset stops its siblings from ever seeing it, and this app has
     * several windows where a drawer and a panel are siblings. It does nothing from Android 11 up,
     * where the platform already hands every child the same insets.
     */
    private void applyKeyboardInset() {
        final View content = findViewById(android.R.id.content);
        if (content == null) {
            return;
        }
        ViewGroupCompat.installCompatInsetsDispatch(content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (view, insets) -> {
            int keyboard = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            int bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            view.setPadding(0, 0, 0, Math.max(keyboard - bars, 0));
            return insets;
        });
        content.requestApplyInsets();
    }

    @Override
    protected void onDestroy() {
        // Before the teardown, not after it: a fragment being torn down inside super.onDestroy
        // can change the theme, and this activity is in no state to be repainted.
        ThemeEngine.unregisterObserver(this);
        super.onDestroy();
    }

    @Override
    public void onThemeChanged(ITheme theme) {
        if (theme.getMode() != mThemeMode) {
            recreate();
            return;
        }
        if (ThemeEngine.isSystemColorsEnabled() != mSystemColors) {
            recreate();
            return;
        }
        if (DynamicColors.isDynamicColorAvailable() && !mSystemColors
                && ThemeEngine.getSavedColorPrimary() != mSeedColorPrimary) {
            recreate();
            return;
        }
        ThemeEngine.applyTheme(getWindow().peekDecorView(), true);
        onThemeSetup(theme);
    }

    /**
     * This method is called by the activity when the activity has been created and
     * dynamically when the theme engine detects a change of a value of the theme.
     * @param theme current theme to apply
     */
    @CallSuper
    protected void onThemeSetup(ITheme theme) {
        setupActivityBaseTheme(theme);
    }

    private void setupActivityBaseTheme(ITheme theme) {
        onThemeSystemBarScrim(theme);
        onThemeSystemBarIcons(theme);
        onThemeTaskDescription(theme);
        onThemeWindowBackground(theme);
    }

    /**
     * The color the app itself draws behind the status bar. On nearly every screen that is the
     * toolbar, which now runs under the bar instead of stopping below it. A screen where something
     * else ends up there says so by overriding this, and the icon color follows.
     */
    protected int getColorBehindStatusBar(ITheme theme) {
        return mAppBarScrolledPastStatusBar
                ? theme.getColorWindowForeground() : theme.getColorPrimaryBackground();
    }

    /**
     * For the screens that put their toolbar inside the scrolling content, the new and edit forms
     * and the detail panels, where it leaves the top of the window as soon as the user scrolls and
     * the content behind the status bar changes color underneath them. Without this the icons stay
     * chosen for the toolbar and go white on a white form.
     * <p>
     * Does nothing when the app bar is not inside the scroller, which is how the wide layouts are
     * built, there the app bar belongs to the window and never moves, so the answer never changes.
     */
    public void followScrollForStatusBarIcons(View scroller, View appBar) {
        if (scroller == null || appBar == null || !isDescendant(scroller, appBar)) {
            return;
        }
        scroller.setOnScrollChangeListener(
                (view, x, y, oldX, oldY) -> updateStatusBarIconsForScroll(scroller, appBar));
        // A rotation restores the scroll position without a scroll event, so the answer has to be
        // worked out again once the restored geometry exists.
        scroller.addOnLayoutChangeListener(
                (v, l, t, r, b, ol, ot, or_, ob) -> updateStatusBarIconsForScroll(scroller, appBar));
    }

    private void updateStatusBarIconsForScroll(View scroller, View appBar) {
        WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(scroller);
        int statusBar = insets == null ? 0
                : insets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
        boolean past = appBar.getBottom() - scroller.getScrollY() <= statusBar;
        if (past != mAppBarScrolledPastStatusBar) {
            mAppBarScrolledPastStatusBar = past;
            onThemeSystemBarIcons(ThemeEngine.getTheme());
        }
    }

    /**
     * A screen whose toolbar scrolls away hands the icon color back when it leaves. A panel is
     * closed by being made invisible, so no scroll event says the toolbar is back at the top and
     * nothing else would put the icons right.
     */
    public void resetStatusBarIconsToAppBar() {
        if (mAppBarScrolledPastStatusBar) {
            mAppBarScrolledPastStatusBar = false;
            onThemeSystemBarIcons(ThemeEngine.getTheme());
        }
    }

    private static boolean isDescendant(View ancestor, View view) {
        for (Object parent = view.getParent(); parent instanceof View; parent = ((View) parent).getParent()) {
            if (parent == ancestor) {
                return true;
            }
        }
        return false;
    }

    /**
     * The color the app itself draws behind the navigation bar, which on a scrolling screen is the
     * content the list is drawn on.
     */
    protected int getColorBehindNavigationBar(ITheme theme) {
        return theme.getColorWindowForeground();
    }

    /**
     * What keeps three button navigation readable over the content is the icon color, and
     * onThemeSystemBarIcons below picks that from whatever the app draws behind the bar. That only
     * works from Android 8, which is the first release that can put dark icons on the navigation
     * bar; before it they are always white, so a pale list needs a scrim under them instead.
     * <p>
     * From Android 8 up the bar is left fully transparent. Asking the platform to enforce contrast
     * instead would paint a band over the bottom of every list, which is the letterbox this whole
     * change exists to remove.
     */
    protected void onThemeSystemBarScrim(ITheme theme) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return;
        }
        getWindow().setNavigationBarColor(
                ContextCompat.getColor(this, R.color.system_bar_scrim_dark));
    }

    protected void onThemeSystemBarIcons(ITheme theme) {
        WindowInsetsControllerCompat controller =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(Utils.isColorLight(getColorBehindStatusBar(theme)));
        controller.setAppearanceLightNavigationBars(
                Utils.isColorLight(getColorBehindNavigationBar(theme)));
    }

    protected void onThemeTaskDescription(ITheme theme) {
        String name = getString(R.string.app_name);
        Bitmap icon = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
        setTaskDescription(new ActivityManager.TaskDescription(name, icon, theme.getColorPrimary()));
    }

    protected void onThemeWindowBackground(ITheme theme) {
        View view = getWindow().getDecorView();
        view.setBackgroundColor(theme.getColorWindowBackground());
    }
}
