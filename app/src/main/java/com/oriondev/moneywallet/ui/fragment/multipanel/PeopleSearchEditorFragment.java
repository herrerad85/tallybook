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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.background.SearchPeopleLoader;

import java.text.NumberFormat;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The people on a transaction or a side of a transfer, each with the count it alone matches.
 */
public class PeopleSearchEditorFragment extends SearchEditorFragment implements LoaderManager.LoaderCallbacks<List<SearchPeopleLoader.Person>> {

    private static final int LOADER_ID = 1;

    private ViewGroup mList;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        mList = view.findViewById(R.id.search_list_linear_layout);
        bindClear(view, () -> getFilter().setPeopleIds(Collections.emptySet()));
        LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this);
    }

    @NonNull
    @Override
    public Loader<List<SearchPeopleLoader.Person>> onCreateLoader(int id, @Nullable Bundle args) {
        return new SearchPeopleLoader(requireContext());
    }

    @Override
    public void onLoadFinished(@NonNull Loader<List<SearchPeopleLoader.Person>> loader, List<SearchPeopleLoader.Person> people) {
        mList.removeAllViews();
        Set<Long> ticked = getFilter().getPeopleIds();
        NumberFormat format = NumberFormat.getIntegerInstance();
        for (SearchPeopleLoader.Person person : people) {
            long id = person.getId();
            int count = person.getCount();
            boolean checked = ticked.contains(id);
            // a ticked person stays listed, so the tick can be taken off
            if (count == 0 && !checked) {
                continue;
            }
            String description = getResources().getQuantityString(R.plurals.search_person_description, count, person.getName(), count);
            addCheckRow(mList, person.getIcon(), person.getName(), format.format(count), description, false, checked, (button, isChecked) -> {
                Set<Long> ids = new TreeSet<>(getFilter().getPeopleIds());
                if (isChecked) {
                    ids.add(id);
                } else {
                    ids.remove(id);
                }
                getFilter().setPeopleIds(ids);
                onFilterChanged();
            });
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<SearchPeopleLoader.Person>> loader) {
    }
}
