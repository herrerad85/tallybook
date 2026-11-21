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

package com.oriondev.moneywallet.ui.view.theme

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.UiThread
import androidx.core.graphics.ColorUtils
import android.view.View
import android.view.ViewGroup

import com.google.android.material.color.DynamicColors

import java.util.Objects

/**
 * This class is responsible to dynamically theme the user interface at runtime.
 */
class ThemeEngine : ITheme {

    companion object {

        private const val FILE_NAME = "theme.config"
        private const val COLOR_PRIMARY = "color_primary"
        private const val COLOR_PRIMARY_DARK = "color_primary_dark"
        private const val COLOR_ACCENT = "color_accent"
        private const val MODE = "mode"
        private const val SYSTEM_COLORS = "system_colors"

        private val DEFAULT_COLOR_PRIMARY = Color.parseColor("#3f51b5")
        private val DEFAULT_COLOR_PRIMARY_DARK = Color.parseColor("#303F9F")
        private val DEFAULT_COLOR_ACCENT = Color.parseColor("#FF4081")
        private val DEFAULT_MODE = Mode.LIGHT

        private val DEFAULT_TEXT_COLOR_PRIMARY = intArrayOf(
                Color.parseColor("#DE000000"), // OK
                Color.parseColor("#FFFFFFFF"), // OK
                Color.parseColor("#FFFFFFFF")  // OK
        )

        private val DEFAULT_TEXT_COLOR_PRIMARY_INVERSE = intArrayOf(
                Color.parseColor("#DEFFFFFF"), // OK
                Color.parseColor("#FF000000"), // OK
                Color.parseColor("#FF000000")  // OK
        )

        private val DEFAULT_TEXT_COLOR_SECONDARY = intArrayOf(
                Color.parseColor("#8A000000"), // OK
                Color.parseColor("#B3FFFFFF"), // OK
                Color.parseColor("#B3FFFFFF")  // OK
        )

        private val DEFAULT_TEXT_COLOR_SECONDARY_INVERSE = intArrayOf(
                Color.parseColor("#8AFFFFFF"), // OK
                Color.parseColor("#B3000000"), // OK
                Color.parseColor("#B3000000")
        )

        private val DEFAULT_ICON_COLOR = intArrayOf(
                Color.parseColor("#8A000000"), // OK
                Color.parseColor("#8AFFFFFF"), // OK
                Color.parseColor("#8AFFFFFF")  // OK
        )

        private val DEFAULT_HINT_TEXT_COLOR = intArrayOf(
                Color.parseColor("#61000000"), // OK
                Color.parseColor("#80FFFFFF"), // OK
                Color.parseColor("#80FFFFFF")  // OK
        )

        private val DEFAULT_COLOR_CARD_BACKGROUND = intArrayOf(
                Color.parseColor("#FFFFFF"), // OK
                Color.parseColor("#424242"), // OK
                Color.parseColor("#000000")  // OK
        )

        private val DEFAULT_COLOR_WINDOW_FOREGROUND = intArrayOf(
                Color.parseColor("#FFFFFF"), // OK
                Color.parseColor("#303030"), // OK
                Color.parseColor("#000000")  // OK
        )

        private val DEFAULT_COLOR_WINDOW_BACKGROUND = intArrayOf(
                Color.parseColor("#FAFAFA"), // OK
                Color.parseColor("#212121"), // OK
                Color.parseColor("#000000")  // OK
        )

        private val DEFAULT_COLOR_RIPPLE = intArrayOf(
                Color.parseColor("#1f000000"), // OK
                Color.parseColor("#33ffffff"), // OK
                Color.parseColor("#33ffffff") // OK
        )

        private val DRAWER_BACKGROUND_COLOR = intArrayOf(
                Color.parseColor("#F9F9F9"),
                Color.parseColor("#303030"),
                Color.parseColor("#000000")
        )

        private val DRAWER_ICON_COLOR = intArrayOf(
                Color.parseColor("#8A000000"),
                Color.parseColor("#8AFFFFFF"),
                Color.parseColor("#8AFFFFFF")
        )

        private val DRAWER_TEXT_COLOR = intArrayOf(
                Color.parseColor("#DE000000"),
                Color.parseColor("#DEFFFFFF"),
                Color.parseColor("#DEFFFFFF")
        )

        private val DRAWER_SELECTED_ITEM_COLOR = intArrayOf(
                Color.parseColor("#E8E8E8"),
                Color.parseColor("#202020"),
                Color.parseColor("#202020")
        )

        private const val INDEX_MODE_LIGHT = 0
        private const val INDEX_MODE_DARK = 1
        private const val INDEX_MODE_DEEP_DARK = 2
        private const val INDEX_MODE_SYSTEM = 3

        private val mThemeObserverList: MutableList<ThemeObserver> = ArrayList()

        private var sInstance: ThemeEngine? = null

        private var sPreviewColorPrimary: Int? = null
        private var sPreviewColorAccent: Int? = null

        @JvmStatic
        fun initialize(context: Context) {
            if (sInstance == null) {
                sInstance = ThemeEngine(context)
            }
        }

        @JvmStatic
        fun registerObserver(observer: ThemeObserver) {
            mThemeObserverList.add(observer)
        }

        @JvmStatic
        fun unregisterObserver(observer: ThemeObserver) {
            mThemeObserverList.remove(observer)
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
        @JvmStatic
        @UiThread
        fun previewColorPrimary(colorPrimary: Int): Boolean {
            if (sInstance == null) {
                return false
            }
            val showing: Int? = if (colorPrimary == sInstance!!.getStoredColorPrimary()) null else colorPrimary
            if (!Objects.equals(showing, sPreviewColorPrimary)) {
                sPreviewColorPrimary = showing
                notifyObservers()
            }
            return sPreviewColorPrimary != null
        }

        @JvmStatic
        @UiThread
        fun previewColorAccent(colorAccent: Int): Boolean {
            if (sInstance == null) {
                return false
            }
            val showing: Int? = if (colorAccent == sInstance!!.getStoredColorAccent()) null else colorAccent
            if (!Objects.equals(showing, sPreviewColorAccent)) {
                sPreviewColorAccent = showing
                notifyObservers()
            }
            return sPreviewColorAccent != null
        }

        @JvmStatic
        @UiThread
        fun clearPreview() {
            if (sPreviewColorPrimary == null && sPreviewColorAccent == null) {
                return
            }
            // OK stores the color it was previewing, so by the time the chooser is gone the override
            // and the stored value are the same color and dropping it moves nothing on screen.
            val showsSomethingElse = sInstance == null
                    || (sPreviewColorPrimary != null && sPreviewColorPrimary != sInstance!!.getStoredColorPrimary())
                    || (sPreviewColorAccent != null && sPreviewColorAccent != sInstance!!.getStoredColorAccent())
            sPreviewColorPrimary = null
            sPreviewColorAccent = null
            if (showsSomethingElse) {
                notifyObservers()
            }
        }

        @JvmStatic
        @UiThread
        fun setColorPrimary(colorPrimary: Int) {
            if (sInstance != null) {
                // Against the stored value, never the getter: a preview of this same color is showing
                // while the chooser's OK button is what calls this, and comparing with the getter would
                // read that preview and skip the write.
                if (colorPrimary != sInstance!!.getStoredColorPrimary()) {
                    sInstance!!.mPreferences.edit().putInt(COLOR_PRIMARY, colorPrimary).apply()
                    sInstance!!.mPreferences.edit().putInt(COLOR_PRIMARY_DARK, Util.darkenColor(colorPrimary)).apply()
                    notifyObservers()
                }
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        @UiThread
        fun setColorAccent(colorAccent: Int) {
            if (sInstance != null) {
                if (colorAccent != sInstance!!.getStoredColorAccent()) {
                    sInstance!!.mPreferences.edit().putInt(COLOR_ACCENT, colorAccent).apply()
                    notifyObservers()
                }
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        @UiThread
        fun setMode(mode: Mode?) {
            if (sInstance != null && mode != null) {
                if (mode.getIndex() != sInstance!!.getStoredMode()) {
                    sInstance!!.mPreferences.edit().putInt(MODE, mode.getIndex()).apply()
                    notifyObservers()
                }
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        @UiThread
        fun setFollowSystem() {
            if (sInstance != null) {
                if (!isFollowingSystem()) {
                    sInstance!!.mPreferences.edit().putInt(MODE, INDEX_MODE_SYSTEM).apply()
                    notifyObservers()
                }
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        fun isFollowingSystem(): Boolean {
            if (sInstance != null) {
                return sInstance!!.getStoredMode() == INDEX_MODE_SYSTEM
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        @UiThread
        fun setUseSystemColors(useSystemColors: Boolean) {
            if (sInstance != null) {
                if (useSystemColors != isSystemColorsEnabled()) {
                    sInstance!!.mPreferences.edit().putBoolean(SYSTEM_COLORS, useSystemColors).apply()
                    notifyObservers()
                }
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        /**
         * Whether the user asked for the wallpaper colors. Where dynamic color is not available the
         * app keeps the picked colors even while this is true.
         */
        @JvmStatic
        fun isSystemColorsEnabled(): Boolean {
            if (sInstance != null) {
                return sInstance!!.mPreferences.getBoolean(SYSTEM_COLORS, false)
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @UiThread
        private fun notifyObservers() {
            if (sInstance != null) {
                for (observer in mThemeObserverList) {
                    observer.onThemeChanged(sInstance)
                }
            }
        }

        @JvmStatic
        fun getTheme(): ITheme {
            if (sInstance != null) {
                return sInstance!!
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        /**
         * The primary the user saved. While a chooser is open getColorPrimary answers with its preview
         * instead, and while the system colors are in use with the wallpaper color.
         */
        @JvmStatic
        fun getSavedColorPrimary(): Int {
            if (sInstance != null) {
                return sInstance!!.getStoredColorPrimary()
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        fun getSavedColorAccent(): Int {
            if (sInstance != null) {
                return sInstance!!.getStoredColorAccent()
            } else {
                throw RuntimeException("ThemeEngine not initialized!")
            }
        }

        @JvmStatic
        fun applyTheme(view: View?, propagate: Boolean) {
            if (view is ThemeConsumer) {
                view.onApplyTheme(sInstance)
            }
            if (propagate && view is ViewGroup) {
                var i = 0
                while (i < view.childCount) {
                    applyTheme(view.getChildAt(i), true)
                    i++
                }
            }
        }
    }

    private val mContext: Context
    private val mPreferences: SharedPreferences

    private constructor(context: Context) {
        mContext = context.applicationContext
        mPreferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    }

    // Each getter holds the override it read, since it can be cleared between a null check
    // and a use.
    override val colorPrimary: Int
        get() {
            val preview = sPreviewColorPrimary
            if (preview != null) {
                return noAlpha(preview)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && usesSystemColors()) {
                return mContext.getColor(android.R.color.system_primary_light)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && usesSystemColors()) {
                return mContext.getColor(android.R.color.system_accent1_600)
            }
            return getStoredColorPrimary()
        }

    override val colorPrimaryDark: Int
        get() {
            val preview = sPreviewColorPrimary
            if (preview != null) {
                return noAlpha(Util.darkenColor(preview))
            }
            return noAlpha(mPreferences.getInt(COLOR_PRIMARY_DARK, DEFAULT_COLOR_PRIMARY_DARK))
        }

    override val colorPrimaryBackground: Int
        get() = if (mode == Mode.DEEP_DARK) Color.BLACK else colorPrimary

    override val colorAccent: Int
        get() {
            val preview = sPreviewColorAccent
            if (preview != null) {
                return noAlpha(preview)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && usesSystemColors()) {
                return mContext.getColor(if (isDark) android.R.color.system_secondary_dark
                        else android.R.color.system_secondary_light)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && usesSystemColors()) {
                return mContext.getColor(if (isDark) android.R.color.system_accent2_200
                        else android.R.color.system_accent2_600)
            }
            return getStoredColorAccent()
        }

    private fun usesSystemColors(): Boolean {
        return mPreferences.getBoolean(SYSTEM_COLORS, false) && DynamicColors.isDynamicColorAvailable()
    }

    private fun getStoredColorPrimary(): Int {
        return noAlpha(mPreferences.getInt(COLOR_PRIMARY, DEFAULT_COLOR_PRIMARY))
    }

    private fun getStoredColorAccent(): Int {
        return noAlpha(mPreferences.getInt(COLOR_ACCENT, DEFAULT_COLOR_ACCENT))
    }

    private fun noAlpha(color: Int): Int {
        return ColorUtils.setAlphaComponent(color, 255)
    }

    private fun getStoredMode(): Int {
        return mPreferences.getInt(MODE, DEFAULT_MODE.getIndex())
    }

    override val mode: Mode
        get() {
            when (getStoredMode()) {
                INDEX_MODE_LIGHT ->
                    return Mode.LIGHT
                INDEX_MODE_DARK ->
                    return Mode.DARK
                INDEX_MODE_DEEP_DARK ->
                    return Mode.DEEP_DARK
                INDEX_MODE_SYSTEM ->
                    return if ((mContext.resources.configuration.uiMode and
                            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
                        Mode.DARK else Mode.LIGHT
                else ->
                    // the stored value has been manually altered
                    return Mode.LIGHT
            }
        }

    override val isDark: Boolean
        get() = mode != Mode.LIGHT

    override val textColorPrimary: Int
        get() = DEFAULT_TEXT_COLOR_PRIMARY[mode.getIndex()]

    override val textColorSecondary: Int
        get() = DEFAULT_TEXT_COLOR_SECONDARY[mode.getIndex()]

    override val textColorPrimaryInverse: Int
        get() = DEFAULT_TEXT_COLOR_PRIMARY_INVERSE[mode.getIndex()]

    override val textColorSecondaryInverse: Int
        get() = DEFAULT_TEXT_COLOR_SECONDARY_INVERSE[mode.getIndex()]

    override val colorCardBackground: Int
        get() = DEFAULT_COLOR_CARD_BACKGROUND[mode.getIndex()]

    override val colorWindowForeground: Int
        get() = DEFAULT_COLOR_WINDOW_FOREGROUND[mode.getIndex()]

    override val colorWindowBackground: Int
        get() = DEFAULT_COLOR_WINDOW_BACKGROUND[mode.getIndex()]

    override val colorRipple: Int
        get() = DEFAULT_COLOR_RIPPLE[mode.getIndex()]

    override val iconColor: Int
        get() = DEFAULT_ICON_COLOR[mode.getIndex()]

    override val hintTextColor: Int
        get() = DEFAULT_HINT_TEXT_COLOR[mode.getIndex()]

    override val errorColor: Int
        get() = Color.RED

    override val drawerBackgroundColor: Int
        get() = DRAWER_BACKGROUND_COLOR[mode.getIndex()]

    override val drawerIconColor: Int
        get() = DRAWER_ICON_COLOR[mode.getIndex()]

    override val drawerSelectedIconColor: Int
        get() = drawerSelectedForeground()

    override val drawerTextColor: Int
        get() = DRAWER_TEXT_COLOR[mode.getIndex()]

    override val drawerSelectedTextColor: Int
        get() = drawerSelectedForeground()

    // The opaque black or white, not the surface's own icon or text color: those are what the
    // closed entries are already drawn in.
    private fun drawerSelectedForeground(): Int {
        val background = drawerSelectedItemColor
        return Util.visibleOr(colorPrimary, background, getBestColor(background))
    }

    override val drawerSelectedItemColor: Int
        get() = DRAWER_SELECTED_ITEM_COLOR[mode.getIndex()]

    override fun getBestColor(background: Int): Int {
        return if (Util.isColorLight(background)) Color.BLACK else Color.WHITE
    }

    override fun getBestTextColor(background: Int): Int {
        val index = if (Util.isColorLight(background)) INDEX_MODE_LIGHT else INDEX_MODE_DARK
        return DEFAULT_TEXT_COLOR_PRIMARY[index]
    }

    override fun getBestHintColor(background: Int): Int {
        val index = if (Util.isColorLight(background)) INDEX_MODE_LIGHT else INDEX_MODE_DARK
        return DEFAULT_HINT_TEXT_COLOR[index]
    }

    override fun getVisibleColor(color: Int, fallback: Int): Int {
        return if (Util.holdsUpAnywhere(this, color)) color else fallback
    }

    override fun getBestIconColor(background: Int): Int {
        val index = if (Util.isColorLight(background)) INDEX_MODE_LIGHT else INDEX_MODE_DARK
        return DEFAULT_ICON_COLOR[index]
    }

    enum class Mode {

        LIGHT(INDEX_MODE_LIGHT),
        DARK(INDEX_MODE_DARK),
        DEEP_DARK(INDEX_MODE_DEEP_DARK);

        private val mIndex: Int

        constructor(index: Int) {
            mIndex = index
        }

        internal fun getIndex(): Int {
            return mIndex
        }
    }

    interface ThemeObserver {

        fun onThemeChanged(theme: ITheme?)
    }

    interface ThemeConsumer {

        fun onApplyTheme(theme: ITheme?)
    }
}