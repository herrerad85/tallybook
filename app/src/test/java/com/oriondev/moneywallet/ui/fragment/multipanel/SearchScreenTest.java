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
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.oriondev.moneywallet.R;
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
    public void theRailHoldsTheMatchToggleThenTheTextChip() {
        try (ActivityScenario<SearchActivity> scenario = ActivityScenario.launch(SearchActivity.class)) {
            scenario.onActivity(activity -> {
                ChipGroup rail = activity.findViewById(R.id.search_rail_chip_group);
                assertEquals(2, rail.getChildCount());
                assertEquals("All", ((Chip) rail.getChildAt(0)).getText().toString());
                assertEquals("Text", ((Chip) rail.getChildAt(1)).getText().toString());
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
                assertEquals(2, ((ChipGroup) activity.findViewById(R.id.search_rail_chip_group)).getChildCount());
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
        return (Chip) ((ChipGroup) activity.findViewById(R.id.search_rail_chip_group)).getChildAt(1);
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
