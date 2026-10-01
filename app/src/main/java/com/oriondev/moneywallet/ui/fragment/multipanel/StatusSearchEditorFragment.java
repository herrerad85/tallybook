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
import android.widget.RadioGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.SearchFilter;

public class StatusSearchEditorFragment extends SearchEditorFragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_search_editor_status, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        RadioGroup radioGroup = view.findViewById(R.id.search_status_radio_group);
        SearchFilter.Status status = getFilter().getStatus();
        if (status != null) {
            radioGroup.check(status == SearchFilter.Status.UNCONFIRMED
                    ? R.id.search_status_unconfirmed_radio_button
                    : R.id.search_status_confirmed_radio_button);
        }
        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            getFilter().setStatus(checkedId == R.id.search_status_unconfirmed_radio_button
                    ? SearchFilter.Status.UNCONFIRMED : SearchFilter.Status.CONFIRMED);
            onFilterChanged();
        });
        bindClear(view, () -> getFilter().setStatus(null));
    }
}
