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

import androidx.fragment.app.Fragment;

import com.oriondev.moneywallet.model.SearchFilter;

/**
 * One type's editor in the search screen's editor panel. Every change applies at once, so the
 * results follow the value while it is adjusted.
 */
public abstract class SearchEditorFragment extends Fragment {

    /**
     * Read in onCreateView or later, since on a restore this fragment is created before the
     * search screen has read its filter back.
     */
    protected SearchFilter getFilter() {
        return getSearch().getFilter();
    }

    protected void onFilterChanged() {
        getSearch().onFilterChanged();
    }

    protected void close() {
        getSearch().closeEditor();
    }

    /**
     * @return true when {@link #onOpenedFromChip()} raises the keyboard, so the switch to this
     *         editor leaves it up.
     */
    protected boolean showsKeyboardOnOpen() {
        return false;
    }

    /**
     * Called after a chip tap opened this editor, never on a restore.
     */
    protected void onOpenedFromChip() {
    }

    private SearchMultiPanelFragment getSearch() {
        return (SearchMultiPanelFragment) requireParentFragment();
    }
}
