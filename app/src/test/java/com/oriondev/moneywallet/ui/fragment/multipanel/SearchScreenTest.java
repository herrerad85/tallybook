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

package com.oriondev.moneywallet.ui.fragment.multipanel;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.os.Looper;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.CompoundButton;
import android.widget.HorizontalScrollView;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.SearchRailLoader;
import com.oriondev.moneywallet.model.SearchFilter;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.ui.activity.NewEditTransactionActivity;
import com.oriondev.moneywallet.ui.activity.SearchActivity;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The rail, the editor panel and the ways it closes, driven through the real activity.
 */
@RunWith(RobolectricTestRunner.class)
public class SearchScreenTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";

    @Test
    public void theRailHoldsTheMatchToggleThenTheCategoryChip() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Status", "Wallet"), railTexts(activity));
                assertFalse(activity.findViewById(R.id.search_rail_scroll_view).isSaveEnabled());
            });
        }
    }

    @Test
    public void theMatchToggleFlipsBetweenAllAndAny() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip toggle = (Chip) ((ChipGroup) activity.findViewById(R.id.search_rail_chip_group)).getChildAt(0);
                toggle.performClick();
                assertEquals(SearchFilter.Match.ANY, search(activity).getFilter().getMatch());
                assertEquals("Any", toggle.getText().toString());
                toggle.performClick();
                assertEquals(SearchFilter.Match.ALL, search(activity).getFilter().getMatch());
                assertEquals("All", toggle.getText().toString());
            });
        }
    }

    @Test
    public void theTextChipOpensTheEditorAndATapOnItClosesIt() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                ChipGroup rail = activity.findViewById(R.id.search_rail_chip_group);
                View panel = activity.findViewById(R.id.search_editor_panel);
                assertEquals(View.GONE, panel.getVisibility());
                assertFalse(rail.isClickable());
                textChip(activity).performClick();
                assertEquals(View.VISIBLE, panel.getVisibility());
                assertTrue(editor(activity) instanceof TextSearchEditorFragment);
                assertTrue(rail.isClickable());
                assertFalse(rail.isFocusable());
                assertEquals("Close editor", rail.getContentDescription().toString());
                textChip(activity).performClick();
                assertEquals(View.GONE, panel.getVisibility());
                assertNull(editor(activity));
                assertFalse(rail.isClickable());
                assertNull(rail.getContentDescription());
            });
        }
    }

    @Test
    public void typingSetsTheFilterAndTheChipShowsTheText() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                EditText field = activity.findViewById(R.id.search_text_edit_text);
                assertTrue(field.hasFocus());
                field.setText("  coffee ");
                assertEquals("coffee", search(activity).getFilter().getText());
                assertEquals("\u201Ccoffee\u201D", textChip(activity).getText().toString());
            });
        }
    }

    @Test
    public void typingNarrowsTheResultStripAndTheList() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        insertExpenses("Morning coffee", "Rent");
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitSummary(activity, "2 results");
                assertEquals("list rows", 2, listItemCount(activity));
                textChip(activity).performClick();
                ((EditText) activity.findViewById(R.id.search_text_edit_text)).setText("coffee");
                awaitSummary(activity, "1 result");
                assertEquals("list rows", 1, listItemCount(activity));
            });
        }
    }

    @Test
    public void clearUnsetsTheTextAndCloses() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                ((EditText) activity.findViewById(R.id.search_text_edit_text)).setText("coffee");
                activity.findViewById(R.id.search_clear_button).performClick();
                assertNull(search(activity).getFilter().getText());
                assertNull(editor(activity));
                assertEquals("Text", textChip(activity).getText().toString());
            });
        }
    }

    @Test
    public void reopeningTheTextEditorShowsTheText() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                ((EditText) activity.findViewById(R.id.search_text_edit_text)).setText("coffee");
                textChip(activity).performClick();
                textChip(activity).performClick();
                assertEquals("coffee", ((EditText) activity.findViewById(R.id.search_text_edit_text)).getText().toString());
            });
        }
    }

    @Test
    public void backClosesTheEditorBeforeLeaving() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                activity.onBackPressed();
                assertNull(editor(activity));
                assertFalse(activity.isFinishing());
                activity.onBackPressed();
                assertTrue(activity.isFinishing());
            });
        }
    }

    @Test
    public void aTapOnTheRailBackgroundClosesTheEditor() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                activity.findViewById(R.id.search_rail_chip_group).performClick();
                assertNull(editor(activity));
            });
        }
    }

    @Test
    public void aTapOutsideClosesOnTheUpAndReachesNothingUnderIt() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                View strip = activity.findViewById(R.id.search_summary_text_view);
                int[] at = centerOf(strip);
                boolean[] reached = new boolean[1];
                strip.setOnTouchListener((v, event) -> reached[0] = true);
                assertTrue(activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, at)));
                assertTrue(editor(activity) != null);
                assertTrue(activity.dispatchTouchEvent(event(MotionEvent.ACTION_UP, at)));
                assertNull(editor(activity));
                assertFalse(reached[0]);
            });
        }
    }

    @Test
    public void aTouchOnTheMatchToggleFlipsItAndLeavesTheEditorOpen() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> textChip(activity).performClick());
            scenario.onActivity(activity -> {
                int[] at = centerOf(((ChipGroup) activity.findViewById(R.id.search_rail_chip_group)).getChildAt(0));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, at));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_UP, at));
                // the chip posts its click
                shadowOf(Looper.getMainLooper()).idle();
                assertTrue(editor(activity) != null);
                assertEquals(SearchFilter.Match.ANY, search(activity).getFilter().getMatch());
            });
        }
    }

    @Test
    public void aCancelledGestureOutsideLeavesTheEditorOpen() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                int[] at = centerOf(activity.findViewById(R.id.search_summary_text_view));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, at));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_CANCEL, at));
                assertTrue(editor(activity) != null);
            });
        }
    }

    @Test
    public void aTapInsideThePanelDoesNotClose() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> textChip(activity).performClick());
            scenario.onActivity(activity -> {
                View panel = activity.findViewById(R.id.search_editor_panel);
                int[] at = centerOf(panel);
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, at));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_UP, at));
                assertTrue(editor(activity) != null);
                assertTrue(panel.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, new int[] {1, 1})));
            });
        }
    }

    @Test
    public void anOpenEditorAndItsTextSurviveARecreate() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                textChip(activity).performClick();
                ((EditText) activity.findViewById(R.id.search_text_edit_text)).setText("coffee");
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(editor(activity) instanceof TextSearchEditorFragment);
                assertEquals(View.VISIBLE, activity.findViewById(R.id.search_editor_panel).getVisibility());
                assertTrue(activity.findViewById(R.id.search_rail_chip_group).isClickable());
                assertEquals("coffee", search(activity).getFilter().getText());
                assertEquals("coffee", ((EditText) activity.findViewById(R.id.search_text_edit_text)).getText().toString());
                assertEquals(6, ((ChipGroup) activity.findViewById(R.id.search_rail_chip_group)).getChildCount());
            });
        }
    }

    @Test
    public void aResultOverTheEditorBlocksItsFocusAndHidesItFromBackAndTaps() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> textChip(activity).performClick());
            scenario.onActivity(activity -> {
                SearchMultiPanelFragment search = search(activity);
                int before = primaryPanel(activity).getDescendantFocusability();
                int[] at = centerOf(activity.findViewById(R.id.search_summary_text_view));
                search.showSecondaryPanel();
                assertEquals(ViewGroup.FOCUS_BLOCK_DESCENDANTS, primaryPanel(activity).getDescendantFocusability());
                assertFalse(search.isEditorShown());
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, at));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_UP, at));
                activity.onBackPressed();
                assertTrue(editor(activity) != null);
                assertEquals(View.VISIBLE, primaryPanel(activity).getVisibility());
                assertEquals(before, primaryPanel(activity).getDescendantFocusability());
                assertTrue(search.isEditorShown());
            });
        }
    }

    @Test
    public void hidingTheResultPanelWithNoShowLeavesTheFocusAlone() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                SearchMultiPanelFragment search = search(activity);
                primaryPanel(activity).setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
                search.hideSecondaryPanel();
                assertEquals(ViewGroup.FOCUS_AFTER_DESCENDANTS, primaryPanel(activity).getDescendantFocusability());
            });
        }
    }

    @Test
    @Config(qualifiers = "port")
    public void portraitKeepsTheKeyboardRestoreAndResizes() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.amountShowsKeyboardOnOpen());
                assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED,
                        activity.getWindow().getAttributes().softInputMode);
            });
        }
    }

    @Test
    @Config(qualifiers = "land")
    public void landscapeHidesTheRestoredKeyboardAndResizes() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                assertFalse(activity.amountShowsKeyboardOnOpen());
                assertEquals(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN,
                        activity.getWindow().getAttributes().softInputMode);
            });
        }
    }

    @Test
    public void theStatusEditorSetsTheStatusAtOnceAndClearUnsetsItAndCloses() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        fixture.transaction(euro, null, true);
        fixture.transaction(euro, null, false);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitSummary(activity, "2 results");
                Chip status = chip(activity, "Status");
                status.performClick();
                assertTrue(editor(activity) instanceof StatusSearchEditorFragment);
                activity.findViewById(R.id.search_status_unconfirmed_radio_button).performClick();
                assertEquals(SearchFilter.Status.UNCONFIRMED, search(activity).getFilter().getStatus());
                assertEquals("Unconfirmed", status.getText().toString());
                assertEquals("Status, Unconfirmed", status.getContentDescription().toString());
                awaitSummary(activity, "1 result");
                activity.findViewById(R.id.search_status_confirmed_radio_button).performClick();
                assertEquals(SearchFilter.Status.CONFIRMED, search(activity).getFilter().getStatus());
                assertEquals("Confirmed", status.getText().toString());
                activity.findViewById(R.id.search_clear_button).performClick();
                assertNull(search(activity).getFilter().getStatus());
                assertNull(editor(activity));
                assertEquals("Status", status.getText().toString());
                awaitSummary(activity, "2 results");
            });
        }
    }

    @Test
    public void thePeopleEditorTicksPeopleInListOrderAndClearUnsetsItAndCloses() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long savings = fixture.wallet("Savings", false);
        long bob = fixture.person("Bob");
        long alice = fixture.person("Alice");
        fixture.transaction(euro, "<" + alice + ">", true);
        fixture.transaction(euro, null, true);
        fixture.transfer(euro, savings, 0L, "<" + bob + ">");
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitSummary(activity, "4 results");
                awaitRail(activity);
                Chip people = chip(activity, "People");
                people.performClick();
                assertTrue(editor(activity) instanceof PeopleSearchEditorFragment);
                await("the people rows", () -> listRows(activity).getChildCount() == 2);
                assertEquals("1", rowCount(row(activity, "Alice")));
                // both sides of the transfer
                assertEquals("2", rowCount(row(activity, "Bob")));
                row(activity, "Bob").performClick();
                assertEquals(Collections.singleton(bob), search(activity).getFilter().getPeopleIds());
                awaitSummary(activity, "2 results");
                row(activity, "Alice").performClick();
                assertEquals("Alice, Bob", people.getText().toString());
                assertEquals("People, Alice, Bob", people.getContentDescription().toString());
                awaitSummary(activity, "3 results");
                activity.findViewById(R.id.search_clear_button).performClick();
                assertTrue(search(activity).getFilter().getPeopleIds().isEmpty());
                assertNull(editor(activity));
                assertEquals("People", people.getText().toString());
            });
        }
    }

    @Test
    public void aPersonOnNothingIsLeftOutOfThePeopleListUnlessTicked() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long alice = fixture.person("Alice");
        long carol = fixture.person("Carol");
        fixture.transaction(euro, "<" + alice + ">", true);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip people = chip(activity, "People");
                people.performClick();
                await("the people rows", () -> listRows(activity).getChildCount() > 0);
                assertEquals(Collections.singletonList("Alice"), rowNames(activity));
                activity.onBackPressed();
                search(activity).getFilter().setPeopleIds(Collections.singleton(carol));
                search(activity).onFilterChanged();
                people.performClick();
                await("the people rows", () -> listRows(activity).getChildCount() > 0);
                assertEquals(Arrays.asList("Alice", "Carol"), rowNames(activity));
                assertEquals("0", rowCount(row(activity, "Carol")));
                assertTrue(((CompoundButton) row(activity, "Carol").findViewById(R.id.search_row_check_box)).isChecked());
            });
        }
    }

    @Test
    public void theWalletEditorTicksWalletsAndTransfersOnlyAndClearUnsetsItAndCloses() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long attic = fixture.wallet("Attic", true);
        long euro = fixture.wallet("Euro", false);
        long cash = fixture.wallet("Cash", false);
        fixture.transaction(euro, null, true);
        fixture.transaction(cash, null, true);
        fixture.transfer(euro, attic, 0L, null);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitSummary(activity, "4 results");
                awaitRail(activity);
                Chip wallet = chip(activity, "Wallet");
                wallet.performClick();
                assertTrue(editor(activity) instanceof WalletSearchEditorFragment);
                await("the wallet rows", () -> listRows(activity).getChildCount() == 3);
                assertEquals(Arrays.asList("Cash", "Euro", "Attic"), rowNames(activity));
                assertTrue(row(activity, "Attic").findViewById(R.id.search_row_name_text_view).getAlpha() < 1f);
                assertEquals("Attic (archived)", row(activity, "Attic").getContentDescription().toString());
                assertNull(row(activity, "Euro").getContentDescription());
                assertEquals(1f, row(activity, "Euro").findViewById(R.id.search_row_name_text_view).getAlpha(), 0f);
                CompoundButton transfersOnly = activity.findViewById(R.id.search_transfers_only_switch);
                assertEquals(View.VISIBLE, transfersOnly.getVisibility());
                transfersOnly.performClick();
                assertTrue(search(activity).getFilter().isTransfersOnly());
                assertEquals("Transfers", wallet.getText().toString());
                // each side of the one transfer
                awaitSummary(activity, "2 results");
                row(activity, "Euro").performClick();
                assertEquals(Collections.singleton(euro), search(activity).getFilter().getWalletIds());
                assertEquals("Euro (transfers)", wallet.getText().toString());
                awaitSummary(activity, "1 result");
                transfersOnly.performClick();
                assertEquals("Euro", wallet.getText().toString());
                assertEquals("Wallet, Euro", wallet.getContentDescription().toString());
                awaitSummary(activity, "2 results");
                row(activity, "Cash").performClick();
                assertEquals("Cash, Euro", wallet.getText().toString());
                activity.findViewById(R.id.search_clear_button).performClick();
                assertTrue(search(activity).getFilter().getWalletIds().isEmpty());
                assertFalse(search(activity).getFilter().isTransfersOnly());
                assertNull(editor(activity));
                assertEquals("Wallet", wallet.getText().toString());
            });
        }
    }

    // from API 26 a clickable view is focusable anyway
    @Test
    @Config(sdk = 24)
    public void editorRowsTakeKeyboardFocusOnApi24() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long cash = fixture.wallet("Cash", false);
        fixture.transaction(euro, null, true);
        fixture.transaction(cash, null, true);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                chip(activity, "Wallet").performClick();
                await("the wallet rows", () -> listRows(activity).getChildCount() == 2);
                assertTrue(row(activity, "Euro").isFocusable());
                assertTrue(row(activity, "Cash").isFocusable());
            });
        }
    }

    @Test
    public void aCloseByBackMovesTheSetChipsToTheFront() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity);
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Confirmed", "Wallet"), railTexts(activity));
                activity.onBackPressed();
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void aCloseByATapOutsideMovesTheSetChipsToTheFront() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity);
                int[] at = centerOf(activity.findViewById(R.id.search_summary_text_view));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_DOWN, at));
                activity.dispatchTouchEvent(event(MotionEvent.ACTION_UP, at));
                assertNull(editor(activity));
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void aCloseByClearMovesTheSetChipsToTheFront() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity);
                chip(activity, "Text").performClick();
                ((EditText) activity.findViewById(R.id.search_text_edit_text)).setText("coffee");
                activity.findViewById(R.id.search_clear_button).performClick();
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void aCloseByTheOpenChipLeavesTheOrderUntilTheNextCloseByAnotherRoute() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip status = setStatusConfirmed(activity);
                status.performClick();
                assertNull(editor(activity));
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Confirmed", "Wallet"), railTexts(activity));
                chip(activity, "Wallet").performClick();
                activity.onBackPressed();
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void aCloseByTheRailBackgroundLeavesTheOrderUntilTheNextCloseByAnotherRoute() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity);
                activity.findViewById(R.id.search_rail_chip_group).performClick();
                assertNull(editor(activity));
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Confirmed", "Wallet"), railTexts(activity));
                chip(activity, "Text").performClick();
                activity.onBackPressed();
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void switchingEditorsByAChipTapMovesNoChip() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity);
                chip(activity, "Wallet").performClick();
                assertTrue(editor(activity) instanceof WalletSearchEditorFragment);
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Confirmed", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void aRecreateWithATypeSetAndNoEditorOpenPutsTheSetChipFirst() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity).performClick();
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Confirmed", "Wallet"), railTexts(activity));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNull(editor(activity));
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
            });
        }
    }

    @Test
    public void aRecreateWithTypesSetAndAnEditorOpenPutsTheSetChipsFirst() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                setStatusConfirmed(activity);
                chip(activity, "Wallet").performClick();
                activity.findViewById(R.id.search_transfers_only_switch).performClick();
                assertEquals(Arrays.asList("All", "Category", "Text", "People", "Confirmed", "Transfers"), railTexts(activity));
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(editor(activity) instanceof WalletSearchEditorFragment);
                assertEquals(Arrays.asList("All", "Confirmed", "Transfers", "Category", "Text", "People"), railTexts(activity));
            });
        }
    }

    @Test
    @Config(qualifiers = "w180dp")
    public void aRecreateWithAnEditorOpenBringsItsChipIntoView() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        fixtureThatHidesNothing();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                chip(activity, "Wallet").performClick();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                HorizontalScrollView scrollView = activity.findViewById(R.id.search_rail_scroll_view);
                Chip wallet = chip(activity, "Wallet");
                assertTrue("the rail is wider than the screen", scrollView.getChildAt(0).getWidth() > scrollView.getWidth());
                assertTrue(scrollView.getScrollX() > 0);
                assertTrue(wallet.getRight() <= scrollView.getScrollX() + scrollView.getWidth());
            });
        }
    }

    @Test
    @Config(qualifiers = "w180dp")
    public void tappingAChipPartOffTheEdgeBringsItIntoView() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        fixtureThatHidesNothing();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                shadowOf(Looper.getMainLooper()).idle();
                HorizontalScrollView scrollView = activity.findViewById(R.id.search_rail_scroll_view);
                Chip wallet = chip(activity, "Wallet");
                // the chip's middle on the right edge
                scrollView.scrollTo((wallet.getLeft() + wallet.getRight()) / 2 - scrollView.getWidth(), 0);
                assertTrue("wallet starts in view", wallet.getLeft() < scrollView.getScrollX() + scrollView.getWidth());
                assertTrue("wallet ends off the edge", wallet.getRight() > scrollView.getScrollX() + scrollView.getWidth());
                wallet.performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertTrue(editor(activity) instanceof WalletSearchEditorFragment);
                assertTrue("wallet right " + wallet.getRight() + ", view ends at " + (scrollView.getScrollX() + scrollView.getWidth()),
                        wallet.getRight() <= scrollView.getScrollX() + scrollView.getWidth());
            });
        }
    }

    @Test
    @Config(qualifiers = "w180dp")
    public void namesThatLandAfterARecreateLeaveTheOpenChipInView() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        Fixture fixture = new Fixture();
        List<Long> people = Arrays.asList(fixture.person("Alexandria Montgomery"),
                fixture.person("Bartholomew Fitzgerald"), fixture.person("Cornelius Vanderbilt"));
        TestDatabases.hold(context);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            try {
                scenario.onActivity(activity -> {
                    search(activity).getFilter().setPeopleIds(new java.util.TreeSet<>(people));
                    search(activity).onFilterChanged();
                    chip(activity, "Wallet").performClick();
                });
                scenario.recreate();
                scenario.onActivity(activity -> {
                    // the rail is laid out before the names land
                    assertNull(search(activity).getRailResult());
                    assertTrue(activity.findViewById(R.id.search_rail_scroll_view).getWidth() > 0);
                });
            } finally {
                scenario.onActivity(activity -> TestDatabases.release(context));
            }
            scenario.onActivity(activity -> {
                awaitRail(activity);
                shadowOf(Looper.getMainLooper()).idle();
                HorizontalScrollView scrollView = activity.findViewById(R.id.search_rail_scroll_view);
                Chip wallet = chip(activity, "Wallet");
                assertTrue(railTexts(activity).get(1).startsWith("Alexandria Montgomery"));
                assertTrue("wallet right " + wallet.getRight() + ", view ends at " + (scrollView.getScrollX() + scrollView.getWidth()),
                        wallet.getRight() <= scrollView.getScrollX() + scrollView.getWidth());
            });
        }
    }

    @Test
    public void aRecreateWithNoEditorOpenLeavesTheRailAtItsStart() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> assertEquals(0, activity.findViewById(R.id.search_rail_scroll_view).getScrollX()));
        }
    }

    @Test
    public void everyTypeShowsUntilTheRailLoadLandsThenTheRuledOutOnesHide() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        fixtureWithNothingToPick();
        TestDatabases.hold(context);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    assertNull(search(activity).getRailResult());
                    assertEquals(Arrays.asList(true, true, true, true), visibleTypes(activity));
                } finally {
                    TestDatabases.release(context);
                }
                awaitRail(activity);
                // Text, People, Status, Wallet
                assertEquals(Arrays.asList(true, false, false, false), visibleTypes(activity));
            });
        }
    }

    @Test
    public void aSetTypeIsNeverHidden() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        fixtureWithNothingToPick();
        TestDatabases.hold(context);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    setStatusConfirmed(activity);
                    activity.onBackPressed();
                } finally {
                    TestDatabases.release(context);
                }
                awaitRail(activity);
                assertEquals(Arrays.asList("All", "Confirmed", "Category", "Text", "People", "Wallet"), railTexts(activity));
                assertEquals(View.VISIBLE, chip(activity, "Confirmed").getVisibility());
                assertEquals(View.GONE, chip(activity, "People").getVisibility());
                assertEquals(View.GONE, chip(activity, "Wallet").getVisibility());
            });
        }
    }

    @Test
    public void aLoadThatLandsWithAnEditorOpenHidesNothingUntilTheClose() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        fixtureWithNothingToPick();
        TestDatabases.hold(context);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                try {
                    chip(activity, "Wallet").performClick();
                } finally {
                    TestDatabases.release(context);
                }
                awaitRail(activity);
                assertEquals(Arrays.asList(true, true, true, true), visibleTypes(activity));
                activity.onBackPressed();
                assertEquals(Arrays.asList(true, false, false, false), visibleTypes(activity));
            });
        }
    }

    @Test
    public void aRecreateWithAnEditorOpenHidesTheRuledOutTypesButTheOpenOne() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        fixtureWithNothingToPick();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                chip(activity, "Wallet").performClick();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(editor(activity) instanceof WalletSearchEditorFragment);
                assertEquals(Arrays.asList(true, false, false, true), visibleTypes(activity));
            });
        }
    }

    @Test
    public void theChipOfAnOpenEditorStaysShownThroughAReload() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        Fixture fixture = fixtureWithNothingToPick();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                Chip wallet = chip(activity, "Wallet");
                assertEquals(View.GONE, wallet.getVisibility());
                // a chip hidden under a finger still gets the click on the lift
                wallet.performClick();
                assertEquals(View.VISIBLE, wallet.getVisibility());
                SearchRailLoader.Result first = search(activity).getRailResult();
                fixture.transaction(fixture.mWallet, null, true);
                await("the rail reload", () -> search(activity).getRailResult() != first);
                assertEquals(View.VISIBLE, wallet.getVisibility());
                assertEquals(View.GONE, chip(activity, "People").getVisibility());
            });
        }
    }

    @Test
    public void aPersonOnATransferKeepsPeopleShownAndASecondWalletKeepsWalletShown() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long savings = fixture.wallet("Savings", false);
        long alice = fixture.person("Alice");
        fixture.transfer(euro, savings, 0L, "<" + alice + ">");
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                // Text, People, Status, Wallet
                assertEquals(Arrays.asList(true, true, false, true), visibleTypes(activity));
            });
        }
    }

    @Test
    public void anUnconfirmedRowKeepsStatusShown() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        fixture.transaction(euro, null, false);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                assertEquals(Arrays.asList(true, false, true, false), visibleTypes(activity));
            });
        }
    }

    @Test
    public void theCategoryChipShowsWhenEveryOtherRuleHides() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        fixtureWithNothingToPick();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitRail(activity);
                assertEquals(Arrays.asList(true, false, false, false), visibleTypes(activity));
                assertEquals(View.VISIBLE, chip(activity, "Category").getVisibility());
            });
        }
    }

    @Test
    public void theCategoryEditorTicksAParentWithEveryChildAndClearUnsetsItAndCloses() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Map<String, Long> ids = categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                awaitSummary(activity, "8 results");
                Chip category = openCategoryEditor(activity);
                assertTrue(editor(activity) instanceof CategorySearchEditorFragment);
                assertEquals("Expenses", tabs(activity).getTabAt(0).getText().toString());
                assertEquals("Incomes", tabs(activity).getTabAt(1).getText().toString());
                assertEquals(0, tabs(activity).getSelectedTabPosition());
                assertEquals(withSystem("Food", "Misc"), rowNames(activity));
                // its own row and its children's three
                assertEquals("4", rowCount(row(activity, "Food")));
                assertEquals("1", rowCount(row(activity, "Misc")));
                assertEquals("0", rowCount(row(activity, "Debt")));
                row(activity, "Food").performClick();
                assertEquals(idsOf(ids, "Food", "Groceries", "Dining"), search(activity).getFilter().getCategoryIds());
                assertEquals("Food", category.getText().toString());
                assertEquals("Category, Food", category.getContentDescription().toString());
                awaitSummary(activity, "4 results");
                activity.findViewById(R.id.search_clear_button).performClick();
                assertTrue(search(activity).getFilter().getCategoryIds().isEmpty());
                assertNull(editor(activity));
                assertEquals("Category", category.getText().toString());
                awaitSummary(activity, "8 results");
            });
        }
    }

    @Test
    public void untickingOneChildLeavesTheParentDashedAndTappingTheDashTicksTheRest() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Map<String, Long> ids = categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip category = openCategoryEditor(activity);
                row(activity, "Food").performClick();
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Food"));
                expand(activity, "Food");
                assertEquals(Arrays.asList("Food", "Dining", "Groceries", "Misc"), rowNames(activity).subList(0, 4));
                assertEquals("1", rowCount(row(activity, "Dining")));
                assertEquals("2", rowCount(row(activity, "Groceries")));
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Groceries"));
                row(activity, "Groceries").performClick();
                assertEquals(idsOf(ids, "Food", "Dining"), search(activity).getFilter().getCategoryIds());
                assertEquals(MaterialCheckBox.STATE_INDETERMINATE, checkedState(activity, "Food"));
                assertDashDrawn(activity, "Food");
                assertEquals("Partly checked", AccessibilityNodeInfoCompat.wrap(row(activity, "Food")
                        .createAccessibilityNodeInfo()).getStateDescription().toString());
                assertEquals(MaterialCheckBox.STATE_UNCHECKED, checkedState(activity, "Groceries"));
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Dining"));
                assertEquals("Food, Dining", category.getText().toString());
                awaitSummary(activity, "2 results");
                row(activity, "Food").performClick();
                assertEquals(idsOf(ids, "Food", "Groceries", "Dining"), search(activity).getFilter().getCategoryIds());
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Food"));
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Groceries"));
                assertEquals("Food", category.getText().toString());
                row(activity, "Food").performClick();
                assertTrue(search(activity).getFilter().getCategoryIds().isEmpty());
                assertEquals(MaterialCheckBox.STATE_UNCHECKED, checkedState(activity, "Food"));
                assertEquals(MaterialCheckBox.STATE_UNCHECKED, checkedState(activity, "Dining"));
                assertEquals("Category", category.getText().toString());
            });
        }
    }

    @Test
    @Config(sdk = 24)
    public void aPartlyTickedParentDrawsTheDashOnApi24() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                openCategoryEditor(activity);
                row(activity, "Food").performClick();
                expand(activity, "Food");
                row(activity, "Groceries").performClick();
                assertEquals(MaterialCheckBox.STATE_INDETERMINATE, checkedState(activity, "Food"));
                assertDashDrawn(activity, "Food");
            });
        }
    }

    @Test
    public void theSystemGroupSitsUnderBothTabsWithOneTickAndFindsBothSidesOfATransfer() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip category = openCategoryEditor(activity);
                assertEquals(Collections.singletonList("System"), headings(activity));
                row(activity, "Transfer").performClick();
                assertEquals(1, search(activity).getFilter().getCategoryIds().size());
                awaitSummary(activity, "2 results");
                tabs(activity).getTabAt(1).select();
                assertEquals(withSystem("Salary"), rowNames(activity));
                assertEquals(Collections.singletonList("System"), headings(activity));
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Transfer"));
                assertEquals("Transfer", category.getText().toString());
            });
        }
    }

    @Test
    public void theCategoryChipNamesTheTicksInEditorOrderWithTheFirstOnesIcon() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip category = openCategoryEditor(activity);
                assertNotNull("the unset symbol takes the chip's tint", category.getChipIconTint());
                expand(activity, "Food");
                row(activity, "Transfer").performClick();
                tabs(activity).getTabAt(1).select();
                row(activity, "Salary").performClick();
                tabs(activity).getTabAt(0).select();
                row(activity, "Groceries").performClick();
                assertEquals("Groceries, Salary, Transfer", category.getText().toString());
                assertEquals("Category, Groceries, Salary, Transfer", category.getContentDescription().toString());
                assertNotNull(category.getChipIcon());
                assertNull("a category icon keeps its own colors", category.getChipIconTint());
                awaitSummary(activity, "5 results");
            });
        }
    }

    @Test
    public void aCategoryIconTheChipCannotDrawFallsBackToTheTypeSymbol() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long lost = fixture.category("Lost", Contract.CategoryType.EXPENSE, null, "{\"type\":\"resource\",\"resource\":\"no_such_icon\"}");
        fixture.transaction(euro, lost, null, true);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip category = openCategoryEditor(activity);
                row(activity, "Lost").performClick();
                assertEquals("Lost", category.getText().toString());
                assertNotNull(category.getChipIcon());
                assertNotNull(category.getChipIconTint());
            });
        }
    }

    @Test
    public void anUnsetChipShowsItsTypeSymbolBeforeAndAfterATick() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip category = openCategoryEditor(activity);
                assertEquals(R.drawable.ic_table_large_24dp, iconResource(category));
                assertEquals(R.drawable.ic_search_black_24dp, iconResource(chip(activity, "Text")));
                row(activity, "Food").performClick();
                row(activity, "Food").performClick();
                assertEquals("Category", category.getText().toString());
                assertEquals(R.drawable.ic_table_large_24dp, iconResource(category));
            });
        }
    }

    @Test
    public void theCategoryChipShowsTheIconOfTheFirstTickInEditorOrder() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long aaa = fixture.category("Aaa", Contract.CategoryType.INCOME, null, "{\"type\":\"resource\",\"resource\":\"ic_animals_02\"}");
        long mid = fixture.category("Mid", Contract.CategoryType.EXPENSE, null, "{\"type\":\"resource\",\"resource\":\"ic_animals_01\"}");
        long zzz = fixture.category("Zzz", Contract.CategoryType.INCOME, null, "{\"type\":\"resource\",\"resource\":\"ic_bank_transfer_in_24dp\"}");
        fixture.transaction(euro, aaa, null, true);
        fixture.transaction(euro, mid, null, true);
        fixture.transaction(euro, zzz, null, true);
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                Chip category = openCategoryEditor(activity);
                tabs(activity).getTabAt(1).select();
                row(activity, "Aaa").performClick();
                tabs(activity).getTabAt(0).select();
                row(activity, "Mid").performClick();
                tabs(activity).getTabAt(1).select();
                row(activity, "Zzz").performClick();
                assertEquals("Mid, Aaa, Zzz", category.getText().toString());
                assertEquals(R.drawable.ic_animals_01, iconResource(category));
            });
        }
    }

    @Test
    public void aTabSwitchOpensTheOtherListAtItsTop() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                openCategoryEditor(activity);
                shadowOf(Looper.getMainLooper()).idle();
                ScrollView list = activity.findViewById(R.id.search_list_scroll_view);
                list.scrollTo(0, list.getChildAt(0).getHeight());
                assertTrue("the expense list scrolled down", list.getScrollY() > 0);
                tabs(activity).getTabAt(1).select();
                shadowOf(Looper.getMainLooper()).idle();
                assertEquals(0, list.getScrollY());
            });
        }
    }

    @Test
    public void aTabSwitchStopsAFlingStillRunningOnTheOldList() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                openCategoryEditor(activity);
                shadowOf(Looper.getMainLooper()).idle();
                ScrollView list = activity.findViewById(R.id.search_list_scroll_view);
                list.fling(4000);
                shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
                list.computeScroll();
                assertTrue("the expense list is flinging", list.getScrollY() > 0);
                tabs(activity).getTabAt(1).select();
                shadowOf(Looper.getMainLooper()).idle();
                for (int frame = 0; frame < 10; frame++) {
                    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(16));
                    list.computeScroll();
                }
                assertEquals(0, list.getScrollY());
            });
        }
    }

    @Test
    public void anOpenCategoryEditorKeepsItsTabItsExpandedParentsAndTheFilterThroughARecreate() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        Map<String, Long> ids = categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                openCategoryEditor(activity);
                expand(activity, "Food");
                row(activity, "Groceries").performClick();
                tabs(activity).getTabAt(1).select();
            });
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertTrue(editor(activity) instanceof CategorySearchEditorFragment);
                assertEquals(1, tabs(activity).getSelectedTabPosition());
                await("the category rows", () -> !rowNames(activity).isEmpty());
                assertEquals(withSystem("Salary"), rowNames(activity));
                assertEquals(idsOf(ids, "Groceries"), search(activity).getFilter().getCategoryIds());
                awaitRail(activity);
                assertEquals("Groceries", chip(activity, "Groceries").getText().toString());
                tabs(activity).getTabAt(0).select();
                assertEquals(Arrays.asList("Food", "Dining", "Groceries", "Misc"), rowNames(activity).subList(0, 4));
                assertEquals(MaterialCheckBox.STATE_CHECKED, checkedState(activity, "Groceries"));
                assertEquals(MaterialCheckBox.STATE_INDETERMINATE, checkedState(activity, "Food"));
            });
        }
    }

    @Test
    public void aLongCategoryListScrollsInsideThePanelWithTheTabsAndClearInIt() {
        TestDatabases.useFreshDatabase(ApplicationProvider.getApplicationContext());
        categoryFixture();
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                openCategoryEditor(activity);
                shadowOf(Looper.getMainLooper()).idle();
                ScrollView list = activity.findViewById(R.id.search_list_scroll_view);
                View panel = activity.findViewById(R.id.search_editor_panel);
                View strip = activity.findViewById(R.id.search_strip);
                assertTrue("the list is taller than its room", list.getChildAt(0).getHeight() > list.getHeight());
                int[] panelAt = centerOf(panel);
                int panelTop = panelAt[1] - panel.getHeight() / 2;
                int panelBottom = panelTop + panel.getHeight();
                assertTrue("the panel stays under the strip", panelTop >= centerOf(strip)[1] + strip.getHeight() / 2);
                for (View view : new View[] {tabs(activity), activity.findViewById(R.id.search_clear_button)}) {
                    int[] at = centerOf(view);
                    assertTrue(at[1] - view.getHeight() / 2 >= panelTop);
                    assertTrue(at[1] + view.getHeight() / 2 <= panelBottom);
                }
            });
        }
    }

    /**
     * Food with one row, Groceries under it with two and Dining under it with one, Salary with
     * one, the fixture's own Misc with one, and an untaxed transfer between two wallets.
     */
    private static Map<String, Long> categoryFixture() {
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        long savings = fixture.wallet("Savings", false);
        Map<String, Long> ids = new HashMap<>();
        ids.put("Food", fixture.category("Food", Contract.CategoryType.EXPENSE, null, ICON));
        ids.put("Groceries", fixture.category("Groceries", Contract.CategoryType.EXPENSE, ids.get("Food"), ICON));
        ids.put("Dining", fixture.category("Dining", Contract.CategoryType.EXPENSE, ids.get("Food"), ICON));
        ids.put("Salary", fixture.category("Salary", Contract.CategoryType.INCOME, null, ICON));
        for (String name : new String[] {"Food", "Groceries", "Groceries", "Dining", "Salary"}) {
            fixture.transaction(euro, ids.get(name), null, true);
        }
        fixture.transaction(euro, null, true);
        fixture.transfer(euro, savings, 0L, null);
        return ids;
    }

    private static Set<Long> idsOf(Map<String, Long> ids, String... names) {
        Set<Long> picked = new HashSet<>();
        for (String name : names) {
            picked.add(ids.get(name));
        }
        return picked;
    }

    /**
     * The names, then the nine system categories, which every fresh database holds.
     */
    private static List<String> withSystem(String... names) {
        List<String> all = new ArrayList<>(Arrays.asList(names));
        all.addAll(Arrays.asList("Credit", "Credit paid", "Debt", "Debt paid", "Deposit", "Tax",
                "Transfer", "Transfer tax", "Withdraw"));
        return all;
    }

    private static Chip openCategoryEditor(SearchActivity activity) {
        awaitRail(activity);
        Chip category = chip(activity, "Category");
        category.performClick();
        await("the category rows", () -> !rowNames(activity).isEmpty());
        return category;
    }

    private static int iconResource(Chip chip) {
        return shadowOf(chip.getChipIcon()).getCreatedFromResId();
    }

    private static TabLayout tabs(SearchActivity activity) {
        return activity.findViewById(R.id.search_category_tab_layout);
    }

    private static void expand(SearchActivity activity, String name) {
        row(activity, name).findViewById(R.id.search_row_expand_image_view).performClick();
    }

    private static int checkedState(SearchActivity activity, String name) {
        return ((MaterialCheckBox) row(activity, name).findViewById(R.id.search_row_check_box)).getCheckedState();
    }

    // the app theme is not Material 3, so the dash needs the row layout's own button and icon
    private static void assertDashDrawn(SearchActivity activity, String name) {
        MaterialCheckBox box = row(activity, name).findViewById(R.id.search_row_check_box);
        assertEquals(com.google.android.material.R.drawable.mtrl_checkbox_button,
                shadowOf(box.getButtonDrawable()).getCreatedFromResId());
        assertEquals(com.google.android.material.R.drawable.mtrl_checkbox_button_icon,
                shadowOf(box.getButtonIconDrawable()).getCreatedFromResId());
    }

    private static List<String> headings(SearchActivity activity) {
        ViewGroup rows = listRows(activity);
        List<String> headings = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            if (rows.getChildAt(i) instanceof TextView) {
                headings.add(((TextView) rows.getChildAt(i)).getText().toString());
            }
        }
        return headings;
    }

    /**
     * One wallet, no transfer, no person on anything and every row confirmed.
     */
    private static Fixture fixtureWithNothingToPick() {
        Fixture fixture = new Fixture();
        fixture.mWallet = fixture.wallet("Euro", false);
        fixture.person("Alice");
        fixture.transaction(fixture.mWallet, null, true);
        return fixture;
    }

    /**
     * Two wallets and an unconfirmed row with a person on it.
     */
    private static void fixtureThatHidesNothing() {
        Fixture fixture = new Fixture();
        long euro = fixture.wallet("Euro", false);
        fixture.wallet("Savings", false);
        fixture.transaction(euro, "<" + fixture.person("Alice") + ">", false);
    }

    /**
     * Opens Status and picks Confirmed, which leaves the editor open.
     */
    private static Chip setStatusConfirmed(SearchActivity activity) {
        Chip status = chip(activity, "Status");
        status.performClick();
        activity.findViewById(R.id.search_status_confirmed_radio_button).performClick();
        assertEquals("Confirmed", status.getText().toString());
        return status;
    }

    private static List<String> railTexts(SearchActivity activity) {
        ChipGroup rail = activity.findViewById(R.id.search_rail_chip_group);
        List<String> texts = new ArrayList<>();
        for (int i = 0; i < rail.getChildCount(); i++) {
            texts.add(((Chip) rail.getChildAt(i)).getText().toString());
        }
        return texts;
    }

    private static Chip chip(SearchActivity activity, String text) {
        ChipGroup rail = activity.findViewById(R.id.search_rail_chip_group);
        for (int i = 0; i < rail.getChildCount(); i++) {
            Chip chip = (Chip) rail.getChildAt(i);
            if (chip.getText().toString().equals(text)) {
                return chip;
            }
        }
        throw new AssertionError("no chip reads " + text);
    }

    /**
     * Whether the Text, People, Status and Wallet chips show, in that order, with nothing set.
     */
    private static List<Boolean> visibleTypes(SearchActivity activity) {
        List<Boolean> visible = new ArrayList<>();
        for (String type : new String[] {"Text", "People", "Status", "Wallet"}) {
            visible.add(chip(activity, type).getVisibility() == View.VISIBLE);
        }
        return visible;
    }

    private static ViewGroup listRows(SearchActivity activity) {
        return activity.findViewById(R.id.search_list_linear_layout);
    }

    private static List<String> rowNames(SearchActivity activity) {
        ViewGroup rows = listRows(activity);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < rows.getChildCount(); i++) {
            TextView name = rows.getChildAt(i).findViewById(R.id.search_row_name_text_view);
            if (name != null) {
                names.add(name.getText().toString());
            }
        }
        return names;
    }

    private static View row(SearchActivity activity, String name) {
        ViewGroup rows = listRows(activity);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            TextView nameView = row.findViewById(R.id.search_row_name_text_view);
            if (nameView != null && nameView.getText().toString().equals(name)) {
                return row;
            }
        }
        throw new AssertionError("no row reads " + name);
    }

    private static String rowCount(View row) {
        return ((TextView) row.findViewById(R.id.search_row_count_text_view)).getText().toString();
    }

    private static void awaitRail(SearchActivity activity) {
        await("the rail load", () -> search(activity).getRailResult() != null);
    }

    /**
     * Loaders run on a background thread and land on the main looper, which the test drives by
     * hand.
     */
    private static void await(String what, BooleanSupplier condition) {
        for (int i = 0; i < 200 && !condition.getAsBoolean(); i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertTrue("waited for " + what, condition.getAsBoolean());
    }

    /**
     * Rows written through the provider, in euro, under one expense category.
     */
    private static class Fixture {

        private final ContentResolver mResolver = ApplicationProvider.getApplicationContext().getContentResolver();
        private final long mCategory;
        private long mWallet;

        private Fixture() {
            ContentValues category = new ContentValues();
            category.put(Contract.Category.NAME, "Misc");
            category.put(Contract.Category.ICON, ICON);
            category.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
            category.put(Contract.Category.SHOW_REPORT, true);
            mCategory = ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, category));
        }

        private long wallet(String name, boolean archived) {
            ContentValues values = new ContentValues();
            values.put(Contract.Wallet.NAME, name);
            values.put(Contract.Wallet.ICON, ICON);
            values.put(Contract.Wallet.CURRENCY, "EUR");
            values.put(Contract.Wallet.START_MONEY, 0L);
            values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
            values.put(Contract.Wallet.ARCHIVED, archived);
            return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
        }

        private long person(String name) {
            ContentValues values = new ContentValues();
            values.put(Contract.Person.NAME, name);
            values.put(Contract.Person.ICON, ICON);
            return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, values));
        }

        private long category(String name, Contract.CategoryType type, Long parent, String icon) {
            ContentValues values = new ContentValues();
            values.put(Contract.Category.NAME, name);
            values.put(Contract.Category.ICON, icon);
            values.put(Contract.Category.TYPE, type.getValue());
            values.put(Contract.Category.SHOW_REPORT, true);
            values.put(Contract.Category.PARENT, parent);
            return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
        }

        private void transaction(long wallet, String people, boolean confirmed) {
            transaction(wallet, mCategory, people, confirmed);
        }

        private void transaction(long wallet, long category, String people, boolean confirmed) {
            ContentValues values = new ContentValues();
            values.put(Contract.Transaction.MONEY, 200L);
            values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00");
            values.put(Contract.Transaction.DESCRIPTION, "Row");
            values.put(Contract.Transaction.CATEGORY_ID, category);
            values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
            values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
            values.put(Contract.Transaction.WALLET_ID, wallet);
            values.put(Contract.Transaction.CONFIRMED, confirmed);
            values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
            values.put(Contract.Transaction.PEOPLE_IDS, people);
            mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values);
        }

        /** A tax above zero writes a third row, in the from wallet. */
        private void transfer(long from, long to, long tax, String people) {
            ContentValues values = new ContentValues();
            values.put(Contract.Transfer.DESCRIPTION, "Moved");
            values.put(Contract.Transfer.DATE, "2026-01-15 12:00:00");
            values.put(Contract.Transfer.TRANSACTION_FROM_WALLET_ID, from);
            values.put(Contract.Transfer.TRANSACTION_FROM_MONEY, 1000L);
            values.put(Contract.Transfer.TRANSACTION_TO_WALLET_ID, to);
            values.put(Contract.Transfer.TRANSACTION_TO_MONEY, 1000L);
            values.put(Contract.Transfer.TRANSACTION_TAX_WALLET_ID, from);
            values.put(Contract.Transfer.TRANSACTION_TAX_MONEY, tax);
            values.put(Contract.Transfer.CONFIRMED, true);
            values.put(Contract.Transfer.COUNT_IN_TOTAL, true);
            values.put(Contract.Transfer.PEOPLE_IDS, people);
            mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values);
        }
    }

    private static SearchMultiPanelFragment search(SearchActivity activity) {
        for (Fragment fragment : activity.getSupportFragmentManager().getFragments()) {
            if (fragment instanceof SearchMultiPanelFragment) {
                return (SearchMultiPanelFragment) fragment;
            }
        }
        throw new AssertionError("no search fragment");
    }

    private static Fragment editor(SearchActivity activity) {
        return search(activity).getChildFragmentManager().findFragmentById(R.id.search_editor_container);
    }

    private static ViewGroup primaryPanel(SearchActivity activity) {
        return activity.findViewById(R.id.primary_panel_constraint_layout);
    }

    private static Chip textChip(SearchActivity activity) {
        return (Chip) ((ChipGroup) activity.findViewById(R.id.search_rail_chip_group)).getChildAt(2);
    }

    /**
     * One row per match, since the search cursor carries no date header rows.
     */
    private static int listItemCount(SearchActivity activity) {
        return ((AdvancedRecyclerView) activity.findViewById(R.id.advanced_recycler_view)).getRecyclerView().getAdapter().getItemCount();
    }

    private static int[] centerOf(View view) {
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        assertTrue(view.getWidth() > 0 && view.getHeight() > 0);
        return new int[] {location[0] + view.getWidth() / 2, location[1] + view.getHeight() / 2};
    }

    private static MotionEvent event(int action, int[] at) {
        long now = SystemClock.uptimeMillis();
        return MotionEvent.obtain(now, now, action, at[0], at[1], 0);
    }

    /**
     * The results arrive through a cursor loader on a background thread and land on the main
     * looper, which the test drives by hand.
     */
    private static void awaitSummary(SearchActivity activity, String count) {
        TextView strip = activity.findViewById(R.id.search_summary_text_view);
        for (int i = 0; i < 200 && !strip.getText().toString().startsWith(count); i++) {
            shadowOf(Looper.getMainLooper()).idle();
            try {
                Thread.sleep(25);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
        assertTrue("the strip reads \"" + strip.getText() + "\"", strip.getText().toString().startsWith(count));
    }

    /**
     * One euro wallet with an expense for each description.
     */
    private static void insertExpenses(String... descriptions) {
        Context context = ApplicationProvider.getApplicationContext();
        ContentResolver resolver = context.getContentResolver();
        ContentValues category = new ContentValues();
        category.put(Contract.Category.NAME, "Misc");
        category.put(Contract.Category.ICON, ICON);
        category.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        category.put(Contract.Category.SHOW_REPORT, true);
        long categoryId = ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_CATEGORIES, category));
        ContentValues wallet = new ContentValues();
        wallet.put(Contract.Wallet.NAME, "Euro");
        wallet.put(Contract.Wallet.ICON, ICON);
        wallet.put(Contract.Wallet.CURRENCY, "EUR");
        wallet.put(Contract.Wallet.START_MONEY, 0L);
        wallet.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        wallet.put(Contract.Wallet.ARCHIVED, false);
        long walletId = ContentUris.parseId(resolver.insert(DataContentProvider.CONTENT_WALLETS, wallet));
        for (String description : descriptions) {
            ContentValues values = new ContentValues();
            values.put(Contract.Transaction.MONEY, 200L);
            values.put(Contract.Transaction.DATE, "2026-01-15 12:00:00");
            values.put(Contract.Transaction.DESCRIPTION, description);
            values.put(Contract.Transaction.CATEGORY_ID, categoryId);
            values.put(Contract.Transaction.DIRECTION, Contract.Direction.EXPENSE);
            values.put(Contract.Transaction.TYPE, NewEditTransactionActivity.TYPE_STANDARD);
            values.put(Contract.Transaction.WALLET_ID, walletId);
            values.put(Contract.Transaction.CONFIRMED, true);
            values.put(Contract.Transaction.COUNT_IN_TOTAL, true);
            resolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values);
        }
    }
}
