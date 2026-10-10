/*
 * Copyright (c) 2026.
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

package com.oriondev.moneywallet.ui.activity;

import android.os.Looper;
import android.view.View;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.adapter.recycler.IconAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The icon grid has as many columns as fit across the list. On a phone on its side the panel
 * spans the whole display, on a tablet it is a card of at most 540dp or 640dp, and a side
 * navigation bar or a cutout takes its width out of the list on top of that.
 */
@RunWith(RobolectricTestRunner.class)
public class IconListSpanCountTest {

    @Test
    @Config(qualifiers = "w832dp-h384dp-land")
    public void aLandscapePhoneFillsItsWidthWithIcons() throws Exception {
        assertEquals((int) (832f / iconWidth()), spanCount(1f, 0, 0));
    }

    @Test
    @Config(qualifiers = "w914dp-h411dp-land")
    public void aWiderLandscapePhoneFillsItsWidthWithIcons() throws Exception {
        assertEquals(14, spanCount(1f, 0, 0));
    }

    @Test
    @Config(qualifiers = "w832dp-h384dp-land-xxhdpi")
    public void aDenserLandscapePhoneFillsItsWidthWithIcons() throws Exception {
        assertEquals((int) (832f / iconWidth()), spanCount(3f, 0, 0));
    }

    @Test
    @Config(qualifiers = "w832dp-h384dp-land")
    public void aSideNavigationBarTakesItsWidthFromTheIcons() throws Exception {
        assertEquals((832 - 2 * 48) / iconWidth(), spanCount(1f, 48, 48));
    }

    @Test
    @Config(qualifiers = "w832dp-h384dp-land-xxhdpi")
    public void aSideNavigationBarOnOneSideTakesItsWidthFromTheIcons() throws Exception {
        assertEquals((832 - 48) / iconWidth(), spanCount(3f, 0, 48));
    }

    @Test
    @Config(qualifiers = "w700dp-h600dp")
    public void aSmallTabletFitsTheIconsInItsCard() throws Exception {
        assertEquals(540 / iconWidth(), spanCount(1f, 0, 0));
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp")
    public void aLargeTabletFitsTheIconsInItsCard() throws Exception {
        assertEquals(640 / iconWidth(), spanCount(1f, 0, 0));
    }

    private static int iconWidth() throws Exception {
        Field field = IconListActivity.class.getDeclaredField("ICON_WIDTH_DP");
        field.setAccessible(true);
        return field.getInt(null);
    }

    /**
     * The count once the loader has put the icons on screen, after the padding on the left and
     * right of the list has grown by the given widths in dp, and checked against the cells on
     * screen.
     */
    private static int spanCount(float density, int extraLeftDp, int extraRightDp) {
        int[] spanCount = new int[1];
        try (ActivityScenario<IconListActivity> scenario = ActivityScenario.launch(IconListActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(density, activity.getResources().getDisplayMetrics().density, 0f);
                AdvancedRecyclerView advancedRecyclerView = activity.findViewById(R.id.advanced_recycler_view);
                RecyclerView list = advancedRecyclerView.getRecyclerView();
                long deadline = System.currentTimeMillis() + 10000;
                while (list.getVisibility() != View.VISIBLE || list.getChildCount() == 0) {
                    assertTrue("the icons are on screen", System.currentTimeMillis() < deadline);
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
                }
                int paddingLeft = list.getPaddingLeft() + Math.round(extraLeftDp * density);
                int paddingRight = list.getPaddingRight() + Math.round(extraRightDp * density);
                list.setPadding(paddingLeft, list.getPaddingTop(), paddingRight, list.getPaddingBottom());
                for (int frame = 0; frame < 3; frame++) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
                }
                assertEquals(paddingLeft, list.getPaddingLeft());
                assertEquals(paddingRight, list.getPaddingRight());
                spanCount[0] = ((GridLayoutManager) list.getLayoutManager()).getSpanCount();
                IconAdapter adapter = (IconAdapter) list.getAdapter();
                int contentWidth = list.getWidth() - paddingLeft - paddingRight;
                for (int index = 0; index < list.getChildCount(); index++) {
                    View child = list.getChildAt(index);
                    if (adapter.isHeader(list.getChildAdapterPosition(child))) {
                        assertEquals("a header fills its row", contentWidth, child.getWidth());
                    } else {
                        int spanIndex = ((GridLayoutManager.LayoutParams) child.getLayoutParams()).getSpanIndex();
                        assertEquals("an icon sits in its column", paddingLeft + spanIndex * contentWidth / (float) spanCount[0], child.getLeft(), 1);
                    }
                }
            });
        }
        return spanCount[0];
    }
}
