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

package com.oriondev.moneywallet.ui.adapter.recycler;

import android.content.res.Resources;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CardLongTitleTest {

    private static final int[] LAYOUTS = new int[] {
            R.layout.adapter_budget_item,
            R.layout.adapter_debt_item,
            R.layout.adapter_saving_item,
            R.layout.adapter_model_transaction_item,
            R.layout.adapter_model_transfer_item
    };

    private static final String CATEGORIES =
            "Groceries, Restaurants, Coffee, Transport, Fuel, Parking, Entertainment, Utilities";
    private static final String DESCRIPTION =
            "Rent and shared bills for the flat, split with two friends, paid each first Monday";
    private static final String SHORT_TITLE = "Rent";
    private static final String MONEY = "€ 1,250.00";
    private static final String WIDE_MONEY = "€ 1,250,000,000.00";
    private static final String WALLETS = String.format(Locale.ENGLISH, "%s -> %s",
            "Everyday Household Checking Account", "Long Term Emergency Savings Reserve");
    private static final int TEXT_GUIDELINE_DP = 40;
    private static final int MARGIN_ABOVE_THE_BAR_DP = 8;
    private static final int MARGIN_ABOVE_THE_SECONDARY_TEXT_DP = 0;
    private static final int GAP_UNDER_THE_BAR_DP = 12;
    private static final int GAP_UNDER_THE_SECONDARY_TEXT_DP = 5;

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 0.8f)
    public void aLongTitleWrapsDownwardInsideTheTopSectionAtSmallText() {
        assertEveryLongTitleInside();
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi")
    public void aLongTitleWrapsDownwardInsideTheTopSectionAtDefaultText() {
        assertEveryLongTitleInside();
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 1.3f)
    public void aLongTitleWrapsDownwardInsideTheTopSectionAtLargeText() {
        assertEveryLongTitleInside();
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 2.0f)
    public void aLongTitleWrapsDownwardInsideTheTopSectionAtTheLargestText() {
        assertEveryLongTitleInside();
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 2.0f)
    public void aWideAmountKeepsTheTitleBesideItInsideTheTopSectionAtTheLargestText() {
        for (int layout : LAYOUTS) {
            ViewGroup section = topSectionOf(layout, longTitleOf(layout), WIDE_MONEY);
            assertLongTitleInside(layout, section);
            assertTitleBeforeTheMoney(layout, section);
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi")
    public void aOneLineTitleKeepsTheGapUnderTheViewBelowItWithTheAvatarInPlace() {
        for (int layout : LAYOUTS) {
            ViewGroup section = topSectionOf(layout, SHORT_TITLE, MONEY);
            assertTrue(nameOf(layout) + " section height " + section.getHeight(),
                    section.getHeight() >= pixels(R.dimen.material_component_lists_card_top_layout_height));
            assertGapUnderTheViewBelowTheTitle(layout, section);
            assertTitleAtTheAvatarTop(layout, section);
            assertAvatarAtTheTop(layout, section);
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 0.8f)
    public void aOneLineTitleAtSmallTextKeepsTheOneLineSectionWithTheViewBelowItUnderTheTextGuideline() {
        for (int layout : LAYOUTS) {
            ViewGroup section = topSectionOf(layout, SHORT_TITLE, MONEY);
            assertEquals(nameOf(layout) + " section height",
                    pixels(R.dimen.material_component_lists_card_top_layout_height), section.getHeight());
            assertViewBelowTheTitleUnderTheTextGuideline(layout, section);
            assertTitleAtTheAvatarTop(layout, section);
            assertAvatarAtTheTop(layout, section);
        }
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi")
    public void aLongTransferWalletLineWrapsDownwardInsideTheTopSectionAtDefaultText() {
        assertTransferWalletLineInside();
    }

    @Test
    @Config(qualifiers = "en-w360dp-xhdpi", fontScale = 2.0f)
    public void aLongTransferWalletLineWrapsDownwardInsideTheTopSectionAtTheLargestText() {
        assertTransferWalletLineInside();
    }

    private void assertEveryLongTitleInside() {
        for (int layout : LAYOUTS) {
            assertLongTitleInside(layout, topSectionOf(layout, longTitleOf(layout), MONEY));
        }
    }

    private void assertLongTitleInside(int layout, ViewGroup section) {
        assertTitleInside(layout, section);
        assertTitleAtTheAvatarTop(layout, section);
        assertMoneyOnTheFirstLine(layout, section);
        assertViewBelowTheTitleInside(layout, section);
        assertTallerThanTheOneLineSection(layout, section);
        assertGapUnderTheViewBelowTheTitle(layout, section);
        assertAvatarAtTheTop(layout, section);
    }

    private void assertTransferWalletLineInside() {
        int layout = R.layout.adapter_model_transfer_item;
        ViewGroup section = topSectionOf(layout,
                resources().getString(R.string.system_category_transfer), MONEY, WALLETS);
        assertOneLineTitleOverAWrappedWalletLine(layout, section);
        assertTitleAtTheAvatarTop(layout, section);
        assertMoneyOnTheFirstLine(layout, section);
        assertViewBelowTheTitleInside(layout, section);
        assertTallerThanTheOneLineSection(layout, section);
        assertGapUnderTheViewBelowTheTitle(layout, section);
        assertAvatarAtTheTop(layout, section);
    }

    private void assertTitleInside(int layout, ViewGroup section) {
        TextView title = section.findViewById(R.id.primary_text_view);
        String name = nameOf(layout);
        assertTrue(name + " title lines " + title.getLineCount(), title.getLineCount() >= 2);
        assertTrue(name + " title top at " + title.getTop(), title.getTop() >= 0);
        assertTrue(name + " title bottom at " + title.getBottom() + " inside the section of "
                + section.getHeight(), title.getBottom() <= section.getHeight());
        assertEveryLineShown(layout, "title", title);
    }

    private void assertOneLineTitleOverAWrappedWalletLine(int layout, ViewGroup section) {
        TextView title = section.findViewById(R.id.primary_text_view);
        TextView wallets = section.findViewById(R.id.secondary_text_view);
        String name = nameOf(layout);
        assertEquals(name + " title lines", 1, title.getLineCount());
        assertTrue(name + " wallet line lines " + wallets.getLineCount(), wallets.getLineCount() >= 2);
        assertEveryLineShown(layout, "wallet line", wallets);
        assertEveryLineShown(layout, "title", title);
    }

    private void assertEveryLineShown(int layout, String view, TextView text) {
        int textHeight = text.getLayout().getLineBottom(text.getLineCount() - 1)
                + text.getCompoundPaddingTop() + text.getCompoundPaddingBottom();
        assertTrue(nameOf(layout) + " " + view + " height " + text.getHeight()
                + " under its text of " + textHeight, text.getHeight() >= textHeight);
    }

    private void assertTitleAtTheAvatarTop(int layout, ViewGroup section) {
        assertEquals(nameOf(layout) + " title top", expectedAvatarTop(),
                section.findViewById(R.id.primary_text_view).getTop());
    }

    private void assertTitleBeforeTheMoney(int layout, ViewGroup section) {
        View title = section.findViewById(R.id.primary_text_view);
        View money = section.findViewById(R.id.money_text_view);
        String name = nameOf(layout);
        assertTrue(name + " title ends at " + title.getRight() + " past the money at "
                + money.getLeft(), title.getRight() <= money.getLeft());
        assertTrue(name + " money ends at " + money.getRight() + " outside the section of "
                + section.getWidth(), money.getRight() <= section.getWidth());
    }

    private void assertMoneyOnTheFirstLine(int layout, ViewGroup section) {
        TextView title = section.findViewById(R.id.primary_text_view);
        TextView money = section.findViewById(R.id.money_text_view);
        String name = nameOf(layout);
        assertTrue(name + " money top at " + money.getTop(), money.getTop() >= 0);
        assertEquals(name + " money baseline", title.getTop() + title.getBaseline(),
                money.getTop() + money.getBaseline());
    }

    private void assertViewBelowTheTitleInside(int layout, ViewGroup section) {
        View title = section.findViewById(R.id.primary_text_view);
        View money = section.findViewById(R.id.money_text_view);
        View below = viewBelowTheTitle(section);
        String name = nameOf(layout);
        int textBottom = Math.max(title.getBottom(), money.getBottom());
        assertTrue(name + " view below the title at " + below.getTop() + " above " + textBottom,
                below.getTop() >= textBottom);
        assertTrue(name + " view below the title ends at " + below.getBottom()
                + " outside the section of " + section.getHeight(),
                below.getBottom() <= section.getHeight());
    }

    private void assertViewBelowTheTitleUnderTheTextGuideline(int layout, ViewGroup section) {
        View below = viewBelowTheTitle(section);
        int margin = below.getId() == R.id.progress_bar
                ? MARGIN_ABOVE_THE_BAR_DP : MARGIN_ABOVE_THE_SECONDARY_TEXT_DP;
        assertEquals(nameOf(layout) + " view below the title top",
                dipPixels(TEXT_GUIDELINE_DP + margin), below.getTop());
    }

    private void assertTallerThanTheOneLineSection(int layout, ViewGroup section) {
        assertTrue(nameOf(layout) + " section height " + section.getHeight(),
                section.getHeight() > pixels(R.dimen.material_component_lists_card_top_layout_height));
    }

    private void assertGapUnderTheViewBelowTheTitle(int layout, ViewGroup section) {
        View below = viewBelowTheTitle(section);
        int gap = below.getId() == R.id.progress_bar
                ? GAP_UNDER_THE_BAR_DP : GAP_UNDER_THE_SECONDARY_TEXT_DP;
        assertEquals(nameOf(layout) + " gap under the view below the title", dipPixels(gap),
                section.getHeight() - below.getBottom());
    }

    private void assertAvatarAtTheTop(int layout, ViewGroup section) {
        assertEquals(nameOf(layout) + " avatar top", expectedAvatarTop(),
                section.findViewById(R.id.avatar_image_view).getTop());
    }

    private static int expectedAvatarTop() {
        int margin = pixels(R.dimen.material_component_lists_card_avatar_top_margin);
        return margin + (pixels(R.dimen.material_component_lists_card_top_layout_height)
                - margin - pixels(R.dimen.material_component_lists_card_avatar_height)) / 2;
    }

    private static View viewBelowTheTitle(ViewGroup section) {
        View progress = section.findViewById(R.id.progress_bar);
        return progress != null ? progress : section.findViewById(R.id.secondary_text_view);
    }

    private static String longTitleOf(int layout) {
        return layout == R.layout.adapter_budget_item ? CATEGORIES : DESCRIPTION;
    }

    private static ViewGroup topSectionOf(int layout, String title, String amount) {
        ContextThemeWrapper context = new ContextThemeWrapper(
                ApplicationProvider.getApplicationContext(), R.style.MoneyWalletAppTheme);
        FrameLayout parent = new FrameLayout(context);
        LayoutInflater.from(context).inflate(layout, parent, true);
        TextView primary = parent.findViewById(R.id.primary_text_view);
        TextView money = parent.findViewById(R.id.money_text_view);
        primary.setText(title);
        money.setText(amount);
        int width = context.getResources().getDisplayMetrics().widthPixels;
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        parent.layout(0, 0, width, parent.getMeasuredHeight());
        return (ViewGroup) primary.getParent();
    }

    private static ViewGroup topSectionOf(int layout, String title, String amount, String secondary) {
        ViewGroup section = topSectionOf(layout, title, amount);
        TextView secondaryText = section.findViewById(R.id.secondary_text_view);
        secondaryText.setText(secondary);
        View parent = section.getRootView();
        parent.measure(
                View.MeasureSpec.makeMeasureSpec(parent.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        parent.layout(0, 0, parent.getWidth(), parent.getMeasuredHeight());
        return section;
    }

    private static String nameOf(int layout) {
        return resources().getResourceEntryName(layout);
    }

    private static int pixels(int dimen) {
        return resources().getDimensionPixelSize(dimen);
    }

    private static int dipPixels(int dip) {
        int complex = dip << TypedValue.COMPLEX_MANTISSA_SHIFT
                | TypedValue.COMPLEX_RADIX_23p0 << TypedValue.COMPLEX_RADIX_SHIFT
                | TypedValue.COMPLEX_UNIT_DIP;
        return TypedValue.complexToDimensionPixelSize(complex, resources().getDisplayMetrics());
    }

    private static Resources resources() {
        return ApplicationProvider.getApplicationContext().getResources();
    }
}
