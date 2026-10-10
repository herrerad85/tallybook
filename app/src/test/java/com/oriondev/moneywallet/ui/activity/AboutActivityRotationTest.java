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

import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * About must keep its content through a recreate that swaps the panel layout, which changes the
 * id of the panel container.
 */
@RunWith(RobolectricTestRunner.class)
public class AboutActivityRotationTest {

    @Test
    @Config(qualifiers = "w400dp-h800dp")
    public void contentSurvivesGoingToTheCard() {
        checkRecreate("w700dp-h500dp");
    }

    @Test
    @Config(qualifiers = "w700dp-h500dp")
    public void contentSurvivesLeavingTheCard() {
        checkRecreate("w400dp-h800dp");
    }

    private static void checkRecreate(String newQualifiers) {
        try (ActivityScenario<AboutActivity> scenario = ActivityScenario.launch(AboutActivity.class)) {
            RuntimeEnvironment.setQualifiers(newQualifiers);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(1, activity.getSupportFragmentManager().getFragments().size());
                Fragment fragment = activity.getSupportFragmentManager().getFragments().get(0);
                View view = fragment.requireView();
                assertNotNull(view.getParent());
                assertTrue(view.isAttachedToWindow());
            });
        }
    }
}
