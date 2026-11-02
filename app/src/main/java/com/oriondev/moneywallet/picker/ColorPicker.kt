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

package com.oriondev.moneywallet.picker;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.ui.fragment.dialog.ColorChooserDialog;

/**
 * Created by andrea on 15/04/18.
 */
public class ColorPicker extends Fragment implements ColorChooserDialog.Callback {

    private static final String SS_CURRENT_COLOR = "ColorPicker::SavedState::CurrentColor";
    private static final String SS_ACCENT_PALETTE = "ColorPicker::SavedState::AccentPalette";
    private static final String ARG_DEFAULT_COLOR = "ColorPicker::Arguments::DefaultColor";
    private static final String ARG_ACCENT_PALETTE = "ColorPicker::Arguments::AccentPalette";

    public static ColorPicker createPicker(FragmentManager fragmentManager, String tag, int color, boolean accentPalette, Controller controller) {
        ColorPicker picker = (ColorPicker) fragmentManager.findFragmentByTag(tag);
        if (picker == null) {
            picker = new ColorPicker();
            Bundle arguments = new Bundle();
            arguments.putInt(ARG_DEFAULT_COLOR, color);
            arguments.putBoolean(ARG_ACCENT_PALETTE, accentPalette);
            picker.setArguments(arguments);
            fragmentManager.beginTransaction().add(picker, tag).commit();
        }
        picker.setController(controller);
        return picker;
    }

    private Controller mController;

    private int mCurrentColor;
    private boolean mAccentPalette;
    private boolean mPreviewed;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        if (context instanceof Controller) {
            mController = (Controller) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCurrentColor = savedInstanceState.getInt(SS_CURRENT_COLOR);
            mAccentPalette = savedInstanceState.getBoolean(SS_ACCENT_PALETTE);
        } else {
            Bundle arguments = getArguments();
            if (arguments != null) {
                mCurrentColor = arguments.getInt(ARG_DEFAULT_COLOR);
                mAccentPalette = arguments.getBoolean(ARG_ACCENT_PALETTE);
            } else {
                mCurrentColor = Color.BLACK;
                mAccentPalette = false;
            }
        }
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        fireCallbackSafely(true);
    }

    private void fireCallbackSafely(boolean autoFired) {
        if (mController != null) {
            mController.onColorChanged(getTag(), mCurrentColor, autoFired);
        }
    }

    private String getDialogTag() {
        return getTag() + "::DialogFragment";
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(SS_CURRENT_COLOR, mCurrentColor);
        outState.putBoolean(SS_ACCENT_PALETTE, mAccentPalette);
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    public void showPicker() {
        FragmentManager fragmentManager = getChildFragmentManager();
        String tag = getDialogTag();
        // showNow adds the dialog before it returns, so a second tap in the same frame finds it.
        if (fragmentManager.findFragmentByTag(tag) == null) {
            ColorChooserDialog.newInstance(R.string.dialog_color_picker_title, mAccentPalette, mCurrentColor)
                    .showNow(fragmentManager, tag);
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mController = null;
    }

    @Override
    public void onColorSelection(ColorChooserDialog dialog, int selectedColor) {
        mCurrentColor = selectedColor;
        fireCallbackSafely(false);
    }

    @Override
    public boolean onColorPreview(ColorChooserDialog dialog, int color) {
        mPreviewed = mController != null && mController.onColorPreview(getTag(), color);
        return mPreviewed;
    }

    /** Only a picker that showed something ends anything, since ending it is not per picker. */
    @Override
    public void onColorChooserDismissed(ColorChooserDialog dialog) {
        if (mPreviewed && mController != null) {
            mPreviewed = false;
            mController.onColorPreviewEnded();
        }
    }

    public interface Controller {

        void onColorChanged(String tag, int color, boolean autoFired);

        /** @return true if the color is now showing somewhere, so the chooser can drop its dim. */
        boolean onColorPreview(String tag, int color);

        void onColorPreviewEnded();
    }
}