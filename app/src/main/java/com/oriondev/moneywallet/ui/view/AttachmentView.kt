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

package com.oriondev.moneywallet.ui.view;

import android.content.Context;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.os.BundleCompat;

import com.bumptech.glide.Glide;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.utils.Utils;

import java.util.List;

/**
 * Created by andrea on 30/03/18.
 */
public class AttachmentView extends LinearLayout {

    private static final float INDICATOR_ROTATION_EXPANDED = 180f;
    private static final float INDICATOR_ROTATION_COLLAPSED = 0f;

    private static final String SS_SUPER = "AttachmentView::SavedState::Super";
    private static final String SS_EXPANDED = "AttachmentView::SavedState::Expanded";

    private LinearLayout mContainer;
    private ImageView mIndicator;
    private List<Attachment> mAttachments;

    private Controller mController;

    private boolean mAllowRemove = true;

    private boolean mExpanded = true;

    public AttachmentView(Context context) {
        super(context);
        initialize(context);
    }

    public AttachmentView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize(context);
    }

    public AttachmentView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize(context);
    }

    private void initialize(Context context) {
        setOrientation(VERTICAL);
        View view = inflate(context, R.layout.layout_attachment_view, this);
        mContainer = view.findViewById(R.id.attachment_container);
        mIndicator = view.findViewById(R.id.expansion_indicator_image_view);
        applyExpanded(false);
        view.findViewById(R.id.attachment_header).setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                setExpanded(mContainer.getVisibility() != VISIBLE);
            }

        });
    }

    private void setExpanded(boolean expanded) {
        mExpanded = expanded;
        applyExpanded(true);
    }

    /**
     * The container fades in and out on its own because its parent carries
     * android:animateLayoutChanges. Only the indicator has to be turned by hand, and on a
     * restore it is set outright so a rotation does not replay as an animation.
     */
    private void applyExpanded(boolean animate) {
        mContainer.setVisibility(mExpanded ? VISIBLE : GONE);
        float rotation = mExpanded ? INDICATOR_ROTATION_EXPANDED : INDICATOR_ROTATION_COLLAPSED;
        if (animate) {
            mIndicator.animate().rotation(rotation);
        } else {
            mIndicator.setRotation(rotation);
        }
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle state = new Bundle();
        state.putParcelable(SS_SUPER, super.onSaveInstanceState());
        state.putBoolean(SS_EXPANDED, mExpanded);
        return state;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            Bundle bundle = (Bundle) state;
            super.onRestoreInstanceState(BundleCompat.getParcelable(bundle, SS_SUPER, Parcelable.class));
            mExpanded = bundle.getBoolean(SS_EXPANDED, true);
            applyExpanded(false);
        } else {
            super.onRestoreInstanceState(state);
        }
    }

    /**
     * The rows this view builds in addAttachment all come from one layout, so every row after
     * the first repeats the ids of the row before it. Freezing and thawing self only keeps the
     * saved state of this view to the one flag above and out of reach of those repeats.
     */
    @Override
    protected void dispatchSaveInstanceState(SparseArray<Parcelable> container) {
        dispatchFreezeSelfOnly(container);
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    public void setController(Controller controller) {
        mController = controller;
    }

    public void setAllowRemove(boolean allowRemove) {
        mAllowRemove = allowRemove;
    }

    public void setAttachments(List<Attachment> attachments) {
        mAttachments = attachments;
        mContainer.removeAllViewsInLayout();
        if (mAttachments != null) {
            for (int i = 0; i < mAttachments.size(); i++) {
                addAttachment(i, mAttachments.get(i));
            }
        }
    }

    private void addAttachment(final int position, Attachment attachment) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.layout_attachment_item, mContainer, false);
        ImageView avatarImageView = view.findViewById(R.id.avatar_image_view);
        TextView primaryTextView = view.findViewById(R.id.primary_text_view);
        TextView secondaryTextView = view.findViewById(R.id.secondary_text_view);
        ImageView removeImageView = view.findViewById(R.id.remove_image_view);
        Glide.with(this)
                .load(Attachment.getIconResByType(attachment.getType()))
                .into(avatarImageView);
        primaryTextView.setText(attachment.getName());
        if (attachment.getStatus() == Attachment.Status.READY) {
            view.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (mController != null) {
                        if (position >= 0 && position < mAttachments.size()) {
                            Attachment attachment = mAttachments.get(position);
                            mController.onAttachmentClick(attachment);
                        }
                    }
                }
            });
            removeImageView.setOnClickListener(new OnClickListener() {

                @Override
                public void onClick(View v) {
                    if (mController != null) {
                        if (position >= 0 && position < mAttachments.size()) {
                            Attachment attachment = mAttachments.get(position);
                            mController.onAttachmentDelete(attachment);
                        }
                    }
                }

            });
            secondaryTextView.setText(Utils.readableFileSize(attachment.getSize()));
        } else {
            secondaryTextView.setText(R.string.hint_operation_in_progress);
            removeImageView.setVisibility(INVISIBLE);
        }
        if (!mAllowRemove) {
            removeImageView.setVisibility(GONE);
        }
        mContainer.addView(view);
    }

    private void onAttachmentDelete(View v) {
        if (mController != null && mAllowRemove) {
            int position = (int) v.getTag();
            if (position >= 0 && position < mAttachments.size()) {
                mController.onAttachmentDelete(mAttachments.get(position));
            }
        }
    }

    public interface Controller {

        void onAttachmentClick(Attachment attachment);

        void onAttachmentDelete(Attachment attachment);
    }
}