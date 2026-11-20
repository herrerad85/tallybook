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

package com.oriondev.moneywallet.ui.view

import android.content.Context
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

import androidx.core.os.BundleCompat

import com.bumptech.glide.Glide
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.utils.Utils

/**
 * Created by andrea on 30/03/18.
 */
class AttachmentView : LinearLayout {

    companion object {

        private const val INDICATOR_ROTATION_EXPANDED = 180f
        private const val INDICATOR_ROTATION_COLLAPSED = 0f

        private const val SS_SUPER = "AttachmentView::SavedState::Super"
        private const val SS_EXPANDED = "AttachmentView::SavedState::Expanded"
    }

    private var mContainer: LinearLayout? = null
    private var mIndicator: ImageView? = null
    private var mAttachments: List<Attachment>? = null

    private var mController: Controller? = null

    private var mAllowRemove = true

    private var mExpanded = true

    constructor(context: Context) : super(context) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(context)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(context)
    }

    private fun initialize(context: Context) {
        setOrientation(VERTICAL)
        val view = inflate(context, R.layout.layout_attachment_view, this)
        mContainer = view.findViewById(R.id.attachment_container)
        mIndicator = view.findViewById(R.id.expansion_indicator_image_view)
        applyExpanded(false)
        view.findViewById<View>(R.id.attachment_header).setOnClickListener(object : OnClickListener {

            override fun onClick(v: View?) {
                setExpanded(mContainer!!.visibility != VISIBLE)
            }

        })
    }

    private fun setExpanded(expanded: Boolean) {
        mExpanded = expanded
        applyExpanded(true)
    }

    /**
     * The container fades in and out on its own because its parent carries
     * android:animateLayoutChanges. Only the indicator has to be turned by hand, and on a
     * restore it is set outright so a rotation does not replay as an animation.
     */
    private fun applyExpanded(animate: Boolean) {
        mContainer!!.visibility = if (mExpanded) VISIBLE else GONE
        val rotation = if (mExpanded) INDICATOR_ROTATION_EXPANDED else INDICATOR_ROTATION_COLLAPSED
        if (animate) {
            mIndicator!!.animate().rotation(rotation)
        } else {
            mIndicator!!.rotation = rotation
        }
    }

    override fun onSaveInstanceState(): Parcelable? {
        val state = Bundle()
        state.putParcelable(SS_SUPER, super.onSaveInstanceState())
        state.putBoolean(SS_EXPANDED, mExpanded)
        return state
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state is Bundle) {
            val bundle: Bundle = state
            super.onRestoreInstanceState(BundleCompat.getParcelable(bundle, SS_SUPER, Parcelable::class.java))
            mExpanded = bundle.getBoolean(SS_EXPANDED, true)
            applyExpanded(false)
        } else {
            super.onRestoreInstanceState(state)
        }
    }

    /**
     * The rows this view builds in addAttachment all come from one layout, so every row after
     * the first repeats the ids of the row before it. Freezing and thawing self only keeps the
     * saved state of this view to the one flag above and out of reach of those repeats.
     */
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>?) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>?) {
        dispatchThawSelfOnly(container)
    }

    fun setController(controller: Controller?) {
        mController = controller
    }

    fun setAllowRemove(allowRemove: Boolean) {
        mAllowRemove = allowRemove
    }

    fun setAttachments(attachments: List<Attachment>?) {
        mAttachments = attachments
        mContainer!!.removeAllViewsInLayout()
        if (mAttachments != null) {
            for (i in 0 until mAttachments!!.size) {
                addAttachment(i, mAttachments!![i])
            }
        }
    }

    private fun addAttachment(position: Int, attachment: Attachment) {
        val view = LayoutInflater.from(context).inflate(R.layout.layout_attachment_item, mContainer, false)
        val avatarImageView = view.findViewById<ImageView>(R.id.avatar_image_view)
        val primaryTextView = view.findViewById<TextView>(R.id.primary_text_view)
        val secondaryTextView = view.findViewById<TextView>(R.id.secondary_text_view)
        val removeImageView = view.findViewById<ImageView>(R.id.remove_image_view)
        Glide.with(this)
                .load(Attachment.getIconResByType(attachment.getType()))
                .into(avatarImageView)
        primaryTextView.text = attachment.getName()
        if (attachment.getStatus() == Attachment.Status.READY) {
            view.setOnClickListener(object : OnClickListener {
                override fun onClick(v: View?) {
                    if (mController != null) {
                        if (position >= 0 && position < mAttachments!!.size) {
                            val attachment = mAttachments!![position]
                            mController!!.onAttachmentClick(attachment)
                        }
                    }
                }
            })
            removeImageView.setOnClickListener(object : OnClickListener {

                override fun onClick(v: View?) {
                    if (mController != null) {
                        if (position >= 0 && position < mAttachments!!.size) {
                            val attachment = mAttachments!![position]
                            mController!!.onAttachmentDelete(attachment)
                        }
                    }
                }

            })
            secondaryTextView.text = Utils.readableFileSize(attachment.getSize())
        } else {
            secondaryTextView.setText(R.string.hint_operation_in_progress)
            removeImageView.visibility = INVISIBLE
        }
        if (!mAllowRemove) {
            removeImageView.visibility = GONE
        }
        mContainer!!.addView(view)
    }

    private fun onAttachmentDelete(v: View) {
        if (mController != null && mAllowRemove) {
            val position = v.tag as Int
            if (position >= 0 && position < mAttachments!!.size) {
                mController!!.onAttachmentDelete(mAttachments!![position])
            }
        }
    }

    interface Controller {

        fun onAttachmentClick(attachment: Attachment?)

        fun onAttachmentDelete(attachment: Attachment?)
    }
}