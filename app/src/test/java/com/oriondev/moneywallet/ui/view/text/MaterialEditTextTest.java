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

package com.oriondev.moneywallet.ui.view.text;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.ContextThemeWrapper;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.AttributeSetBuilder;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.shadows.ShadowLegacyCanvas;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Calls onDrawBottomLine on a laid out field and reads the rectangle it drew off the canvas
 * shadow, because the underline reaching back over the slot the cancel button reserves is a pair
 * of edge values that only ever show up as pixels and nothing else on this classpath can see them.
 */
@RunWith(RobolectricTestRunner.class)
public class MaterialEditTextTest {

    private static final int WIDTH = 320;
    private static final int HEIGHT = 120;

    // the default density is mdpi, so one dp is one px and SIDE_PADDING_DP 8 and
    // CANCEL_BUTTON_PADDING_DP 36 are 8 px and 36 px
    private static final int SIDE_PADDING = 8;
    private static final int CANCEL_SLOT = 36;

    @Before
    public void theDensityIsTheOneTheseFiguresAreIn() {
        assertEquals("the figures below are pixel counts taken at 160 dpi, where one dp is one px",
                160, ApplicationProvider.getApplicationContext()
                        .getResources().getDisplayMetrics().densityDpi);
    }

    private MaterialEditText field(boolean showCancelButton) {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme);
        AttributeSet attributes = showCancelButton
                ? Robolectric.buildAttributeSet()
                        .addAttribute(R.attr.met_showCancelButton, "true").build()
                : Robolectric.buildAttributeSet().build();
        MaterialEditText view = new MaterialEditText(context, attributes);
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        return view;
    }

    private ShadowLegacyCanvas.RectPaintHistoryEvent bottomLineOf(MaterialEditText view) {
        Canvas canvas = new Canvas();
        view.onDrawBottomLine(canvas);
        ShadowLegacyCanvas shadow = (ShadowLegacyCanvas) Shadows.shadowOf(canvas);
        assertEquals("onDrawBottomLine drew something other than one rectangle",
                1, shadow.getRectPaintHistoryCount());
        return shadow.getLastDrawnRect();
    }

    private void assertRunsTheFullWidth(ShadowLegacyCanvas.RectPaintHistoryEvent line) {
        assertEquals("the line does not start at the side padding", (float) SIDE_PADDING, line.left, 0f);
        assertEquals("the line does not end at the side padding",
                (float) (WIDTH - SIDE_PADDING), line.right, 0f);
    }

    @Test
    public void leftToRightWithNoButtonTheLineRunsBetweenTheSidePaddings() {
        MaterialEditText view = field(false);
        assertEquals(SIDE_PADDING, view.getPaddingLeft());
        assertEquals(SIDE_PADDING, view.getPaddingRight());
        assertRunsTheFullWidth(bottomLineOf(view));
    }

    @Test
    public void leftToRightWithAButtonTheLineKeepsTheWidthItHasWithout() {
        MaterialEditText view = field(true);
        assertEquals(SIDE_PADDING, view.getPaddingLeft());
        assertEquals("the button slot is not held on the right", SIDE_PADDING + CANCEL_SLOT,
                view.getPaddingRight());
        assertRunsTheFullWidth(bottomLineOf(view));
    }

    // the bare ldrtl qualifier is overridden by the en-US locale on this Robolectric, so the
    // locale goes with it
    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    public void rightToLeftWithAButtonTheLineKeepsTheWidthItHasWithout() {
        MaterialEditText view = field(true);
        assertTrue("the configuration did not come up right to left",
                Utils.isRtl(view.getResources()));
        assertEquals("the button slot is not held on the left", SIDE_PADDING + CANCEL_SLOT,
                view.getPaddingLeft());
        assertEquals(SIDE_PADDING, view.getPaddingRight());
        assertRunsTheFullWidth(bottomLineOf(view));
    }

    @Test
    @Config(qualifiers = "ar-rEG-ldrtl")
    public void rightToLeftWithNoButtonTheLineRunsBetweenTheSidePaddings() {
        MaterialEditText view = field(false);
        assertTrue("the configuration did not come up right to left",
                Utils.isRtl(view.getResources()));
        assertEquals(SIDE_PADDING, view.getPaddingLeft());
        assertEquals(SIDE_PADDING, view.getPaddingRight());
        assertRunsTheFullWidth(bottomLineOf(view));
    }

    @Test
    public void bothEdgesOfTheLineCarryTheScrollOffset() {
        MaterialEditText view = field(false);
        int scroll = 50;
        view.setScrollX(scroll);
        assertEquals("the field did not take the scroll offset", scroll, view.getScrollX());
        ShadowLegacyCanvas.RectPaintHistoryEvent line = bottomLineOf(view);
        // in production the canvas onDraw receives is already shifted by the scroll, so both
        // edges add it back; the bare canvas here records the raw arguments, offset included
        assertEquals("the left edge does not move with the scroll offset",
                (float) (SIDE_PADDING + scroll), line.left, 0f);
        assertEquals("the right edge does not move with the scroll offset",
                (float) (WIDTH - SIDE_PADDING + scroll), line.right, 0f);
    }

    private MaterialEditText floatingLabelField(String xmlHint) {
        Context context = new ContextThemeWrapper(ApplicationProvider.getApplicationContext(),
                R.style.MoneyWalletAppTheme);
        AttributeSetBuilder attributes = Robolectric.buildAttributeSet()
                .addAttribute(R.attr.met_mode, "floatingLabel");
        if (xmlHint != null) {
            attributes.addAttribute(android.R.attr.hint, xmlHint);
        }
        return new MaterialEditText(context, attributes.build());
    }

    // under the legacy graphics mode a label in the xml makes no room above the text either
    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    public void aLabelSetAfterInflationGetsTheRoomOfOneSetInXml() {
        int withoutLabel = floatingLabelField(null).getPaddingTop();
        int withXmlLabel = floatingLabelField("Food").getPaddingTop();
        assertTrue("a label in the xml makes no room above the text, so this cannot tell",
                withXmlLabel > withoutLabel);
        MaterialEditText view = floatingLabelField(null);
        view.setFloatingLabelText("Food");
        assertEquals("the label set later does not make the room one from the xml does",
                withXmlLabel, view.getPaddingTop());
    }

    @Test
    public void aLabelSetAfterInflationIsDrawnOnceThereIsText() {
        MaterialEditText view = floatingLabelField(null);
        view.setFloatingLabelText("Food");
        view.setText("12.00");
        view.measure(View.MeasureSpec.makeMeasureSpec(WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(HEIGHT, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, WIDTH, HEIGHT);
        // the label slides in when the text stops being empty, so run that animation out
        ShadowLooper.idleMainLooper(1, TimeUnit.SECONDS);
        Canvas canvas = new Canvas();
        view.onDrawFloatingLabel(canvas);
        ShadowLegacyCanvas shadow = (ShadowLegacyCanvas) Shadows.shadowOf(canvas);
        assertEquals("the label was not drawn", 1, shadow.getTextHistoryCount());
        assertEquals("Food", shadow.getDrawnTextEvent(0).text);
    }
}
