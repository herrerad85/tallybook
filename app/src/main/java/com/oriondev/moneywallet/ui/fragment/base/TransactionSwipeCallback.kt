package com.oriondev.moneywallet.ui.fragment.base

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View

import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import androidx.core.graphics.withClip
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.color.MaterialColors
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.ui.adapter.recycler.TransactionCursorAdapter

class TransactionSwipeCallback(
        recyclerView: RecyclerView,
        private val adapter: TransactionCursorAdapter,
        private val selectionMode: TransactionSelectionMode
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {

    private val swipedIds = HashMap<RecyclerView.ViewHolder, Long>()
    private val drawnBack = HashSet<RecyclerView.ViewHolder>()
    private var gestureId: Long? = null
    private var selectedViewHolder: RecyclerView.ViewHolder? = null

    private val touchListener = object : RecyclerView.SimpleOnItemTouchListener(), View.OnTouchListener {

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) {
                val position = rv.findChildViewUnder(e.x, e.y)?.let { rv.getChildViewHolder(it).bindingAdapterPosition }
                gestureId = if (position != null && adapter.isSwipeable(position)) {
                    adapter.getItemId(position)
                } else {
                    RecyclerView.NO_ID
                }
            } else {
                onTouch(rv, e)
            }
            return false
        }

        override fun onTouch(v: View, e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
                gestureId = RecyclerView.NO_ID
            }
            return false
        }
    }

    private val background = MaterialColors.getColor(recyclerView, androidx.appcompat.R.attr.colorError).toDrawable()
    private val icon: Drawable = AppCompatResources.getDrawable(recyclerView.context,
            R.drawable.ic_delete_black_24dp)!!.mutate().apply {
        setTint(MaterialColors.getColor(recyclerView, com.google.android.material.R.attr.colorOnError))
    }
    private val iconMargin = recyclerView.resources.getDimensionPixelSize(
            R.dimen.material_component_lists_two_line_avatar_start_margin)

    override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position = viewHolder.bindingAdapterPosition
        val id = gestureId
        return if (id != null && adapter.isSwipeable(position) && adapter.getItemId(position) == id) {
            super.getSwipeDirs(recyclerView, viewHolder)
        } else {
            if (id != null) {
                gestureId = RecyclerView.NO_ID
            }
            0
        }
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                        target: RecyclerView.ViewHolder): Boolean = false

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        super.onSelectedChanged(viewHolder, actionState)
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder != null) {
            val id = adapter.getItemId(viewHolder.bindingAdapterPosition)
            swipedIds[viewHolder] = id
            drawnBack.remove(viewHolder)
            selectedViewHolder = viewHolder
        } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
            selectedViewHolder = null
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        swipedIds.remove(viewHolder)
        drawnBack.remove(viewHolder)
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (viewHolder === selectedViewHolder) {
            return
        }
        val id = swipedIds.remove(viewHolder) ?: return
        val position = viewHolder.bindingAdapterPosition
        if (!adapter.isSwipeable(position) || adapter.getItemId(position) != id) {
            drawBack(viewHolder)
            return
        }
        selectionMode.delete(id) { drawBack(viewHolder) }
    }

    private fun drawBack(viewHolder: RecyclerView.ViewHolder) {
        val position = viewHolder.bindingAdapterPosition
        if (position != RecyclerView.NO_POSITION) {
            drawnBack.add(viewHolder)
            adapter.notifyItemChanged(position)
        }
    }

    override fun onChildDraw(c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float,
                             dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
        val view = viewHolder.itemView
        val moved = dX.toInt()
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && moved != 0 && view.parent != null
                && viewHolder !in drawnBack) {
            val left = if (moved < 0) view.right + moved else view.left
            val right = if (moved < 0) view.right else view.left + moved
            background.setBounds(left, view.top, right, view.bottom)
            background.draw(c)
            val iconLeft = if (moved < 0) view.right - iconMargin - icon.intrinsicWidth else view.left + iconMargin
            val iconTop = view.top + (view.height - icon.intrinsicHeight) / 2
            icon.setBounds(iconLeft, iconTop, iconLeft + icon.intrinsicWidth, iconTop + icon.intrinsicHeight)
            c.withClip(left, view.top, right, view.bottom) {
                icon.draw(this)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }

    companion object {

        @JvmStatic
        @SuppressLint("ClickableViewAccessibility")
        fun attach(recyclerView: RecyclerView, adapter: TransactionCursorAdapter, selectionMode: TransactionSelectionMode) {
            val callback = TransactionSwipeCallback(recyclerView, adapter, selectionMode)
            recyclerView.addOnItemTouchListener(callback.touchListener)
            recyclerView.setOnTouchListener(callback.touchListener)
            ItemTouchHelper(callback).attachToRecyclerView(recyclerView)
        }
    }
}
