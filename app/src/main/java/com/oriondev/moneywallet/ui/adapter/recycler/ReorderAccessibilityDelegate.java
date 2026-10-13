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

import android.os.Bundle;
import android.view.View;
import android.view.ViewParent;

import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.oriondev.moneywallet.R;

/**
 * Set it on the row in onBindViewHolder, not onCreateViewHolder. With accessibility on,
 * RecyclerView keeps this as the row's original delegate and calls it after adding the row's
 * position, so it must never call RecyclerView's item delegate itself. A row bound with
 * accessibility off loses its delegate when it goes to the recycled pool, and the next bind
 * puts it back.
 */
public class ReorderAccessibilityDelegate extends AccessibilityDelegateCompat {

    public interface Mover {

        boolean moveItem(int from, int to);
    }

    private final Mover mMover;

    public ReorderAccessibilityDelegate(Mover mover) {
        mMover = mover;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
        super.onInitializeAccessibilityNodeInfo(host, info);
        int position = getPosition(host);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        if (position > 0) {
            addAction(host, info, R.id.action_move_to_top, R.string.action_move_to_top);
            addAction(host, info, R.id.action_move_up, R.string.action_move_up);
        }
        if (position < getLastPosition(host)) {
            addAction(host, info, R.id.action_move_down, R.string.action_move_down);
            addAction(host, info, R.id.action_move_to_bottom, R.string.action_move_to_bottom);
        }
    }

    @Override
    public boolean performAccessibilityAction(View host, int action, Bundle args) {
        int position = getPosition(host);
        if (position != RecyclerView.NO_POSITION) {
            int target = RecyclerView.NO_POSITION;
            if (action == R.id.action_move_to_top) {
                target = 0;
            } else if (action == R.id.action_move_up) {
                target = position - 1;
            } else if (action == R.id.action_move_down) {
                target = position + 1;
            } else if (action == R.id.action_move_to_bottom) {
                target = getLastPosition(host);
            }
            if (target != RecyclerView.NO_POSITION) {
                boolean moved = target != position && mMover.moveItem(position, target);
                if (moved) {
                    ((RecyclerView) host.getParent()).scrollToPosition(target);
                }
                return moved;
            }
        }
        return super.performAccessibilityAction(host, action, args);
    }

    private static int getPosition(View host) {
        ViewParent parent = host.getParent();
        return parent instanceof RecyclerView
                ? ((RecyclerView) parent).getChildAdapterPosition(host) : RecyclerView.NO_POSITION;
    }

    private static int getLastPosition(View host) {
        RecyclerView.Adapter<?> adapter = ((RecyclerView) host.getParent()).getAdapter();
        return adapter != null ? adapter.getItemCount() - 1 : -1;
    }

    private static void addAction(View host, AccessibilityNodeInfoCompat info, int id, int label) {
        info.addAction(new AccessibilityNodeInfoCompat.AccessibilityActionCompat(id, host.getContext().getString(label)));
    }
}
