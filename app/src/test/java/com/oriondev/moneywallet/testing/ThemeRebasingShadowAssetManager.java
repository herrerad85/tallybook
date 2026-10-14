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

package com.oriondev.moneywallet.testing;

import android.content.res.AssetManager;
import android.os.Build;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.res.android.CppAssetManager2;
import org.robolectric.res.android.Registries;
import org.robolectric.shadow.api.ShadowPicker;
import org.robolectric.shadows.ShadowArscAssetManager10;
import org.robolectric.shadows.ShadowArscAssetManager14;
import org.robolectric.shadows.ShadowAssetManager;
import org.robolectric.util.ReflectionHelpers;

/**
 * Robolectric leaves nativeThemeRebase empty, so a theme keeps its old asset manager when a
 * ResourcesLoader is added to the Resources it belongs to, and the next style applied to it fails
 * a CHECK. Dynamic color seeded from a color adds one in every ThemedActivity from Android 12.
 * This does what the platform's Theme::Rebase does, and is registered for every test in
 * robolectric.properties.
 * <p>
 * Robolectric's own picker still chooses the shadow, and only the two that can meet a rebase are
 * swapped for a subclass that adds it. Extending the Android 14 one below Android 14 breaks
 * every test, which is why there are two.
 */
@Implements(value = AssetManager.class, minSdk = Build.VERSION_CODES.Q,
        shadowPicker = ThemeRebasingShadowAssetManager.Picker.class, isInAndroidSdk = false)
public class ThemeRebasingShadowAssetManager extends ShadowArscAssetManager10 {

    @Implementation(minSdk = Build.VERSION_CODES.S)
    protected static void nativeThemeRebase(long ptr, long themePtr, int[] styleIds, boolean[] force,
                                            int count) {
        rebase(ptr, themePtr, styleIds, force, count);
    }

    private static void rebase(long ptr, long themePtr, int[] styleIds, boolean[] force, int count) {
        CppAssetManager2 assetManager = Registries.NATIVE_ASSET_MANAGER_REGISTRY.getNativeObject(ptr);
        CppAssetManager2.Theme theme = Registries.NATIVE_THEME9_REGISTRY.getNativeObject(themePtr);
        theme.Clear();
        ReflectionHelpers.setField(theme, "asset_manager_", assetManager);
        for (int i = 0; i < count; i++) {
            theme.ApplyStyle(styleIds[i], force[i]);
        }
    }

    @Implements(value = AssetManager.class, minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
            shadowPicker = ThemeRebasingShadowAssetManager.Picker.class, isInAndroidSdk = false)
    public static class FromAndroid14 extends ShadowArscAssetManager14 {

        @Implementation
        protected static void nativeThemeRebase(long ptr, long themePtr, int[] styleIds,
                                                boolean[] force, int count) {
            rebase(ptr, themePtr, styleIds, force, count);
        }
    }

    public static class Picker implements ShadowPicker<ShadowAssetManager> {

        private final ShadowAssetManager.Picker mPicker = new ShadowAssetManager.Picker();

        @Override
        public Class<? extends ShadowAssetManager> pickShadowClass() {
            Class<? extends ShadowAssetManager> picked = mPicker.pickShadowClass();
            if (picked == ShadowArscAssetManager10.class) {
                return ThemeRebasingShadowAssetManager.class;
            }
            if (picked == ShadowArscAssetManager14.class) {
                return FromAndroid14.class;
            }
            return picked;
        }
    }
}
