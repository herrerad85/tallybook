package com.oriondev.moneywallet.ui.adapter.recycler

import android.database.Cursor
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

import androidx.recyclerview.widget.RecyclerView

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.wrapper.TransactionHeaderCursor
import com.oriondev.moneywallet.storage.wrapper.TransferHeaderCursor
import com.oriondev.moneywallet.utils.DateFormatter

import java.util.Date

class DeletedItemCursorAdapter(private val mActionListener: ActionListener) :
        AbstractCursorAdapter<DeletedItemCursorAdapter.DeletedItemViewHolder>(null, Contract.DeletedItem.ID) {

    private val mTransactionAdapter = TransactionCursorAdapter(null)
    private val mTransferAdapter = TransferCursorAdapter(null)

    private var mIndexType = 0
    private var mIndexId = 0
    private var mIndexDeletedAt = 0
    private var mIndexUuid = 0

    override fun onLoadColumnIndices(cursor: Cursor) {
        mIndexType = cursor.getColumnIndex(Contract.DeletedItem.TYPE)
        mIndexId = cursor.getColumnIndex(Contract.DeletedItem.ID)
        mIndexDeletedAt = cursor.getColumnIndex(Contract.DeletedItem.DELETED_AT)
        mIndexUuid = cursor.getColumnIndex(Contract.DeletedItem.UUID)
    }

    override fun swapCursor(newCursor: Cursor?): Cursor? {
        mTransactionAdapter.swapCursor(newCursor)
        mTransferAdapter.swapCursor(newCursor)
        return super.swapCursor(newCursor)
    }

    override fun getItemViewType(position: Int): Int = getSafeCursor(position)!!.getInt(mIndexType)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeletedItemViewHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(R.layout.adapter_deleted_item, parent, false) as ViewGroup
        val row = if (viewType == Contract.DeletedItemType.TRANSFER) {
            mTransferAdapter.onCreateViewHolder(itemView, TransferHeaderCursor.TYPE_ITEM)
        } else {
            mTransactionAdapter.onCreateViewHolder(itemView, TransactionHeaderCursor.TYPE_ITEM)
        }
        return DeletedItemViewHolder(itemView, row)
    }

    override fun onBindViewHolder(holder: DeletedItemViewHolder, cursor: Cursor) {
        if (cursor.getInt(mIndexType) == Contract.DeletedItemType.TRANSFER) {
            mTransferAdapter.onBindViewHolder(holder.mRow, cursor)
        } else {
            mTransactionAdapter.onBindViewHolder(holder.mRow, cursor)
        }
        val deletedAt = DateFormatter.getFormattedDate(Date(cursor.getLong(mIndexDeletedAt)))
        holder.mDeletedTextView.text = holder.itemView.context.getString(R.string.relative_string_deleted_on, deletedAt)
    }

    inner class DeletedItemViewHolder(itemView: ViewGroup, val mRow: RecyclerView.ViewHolder) :
            RecyclerView.ViewHolder(itemView), View.OnClickListener {

        val mDeletedTextView: TextView = itemView.findViewById(R.id.deleted_text_view)

        init {
            itemView.addView(mRow.itemView, 0)
            mRow.itemView.setOnClickListener(null)
            mRow.itemView.setOnLongClickListener(null)
            mRow.itemView.isClickable = false
            mRow.itemView.isLongClickable = false
            mRow.itemView.background = null
            itemView.setOnClickListener(this)
        }

        override fun onClick(v: View) {
            val cursor = getSafeCursor(bindingAdapterPosition) ?: return
            val description = mRow.itemView.findViewById<TextView>(R.id.secondary_text_view).text
            val title = if (TextUtils.isEmpty(description)) {
                mRow.itemView.findViewById<TextView>(R.id.primary_text_view).text
            } else {
                description
            }
            mActionListener.onDeletedItemClick(cursor.getInt(mIndexType), cursor.getLong(mIndexId),
                    cursor.getLong(mIndexDeletedAt), cursor.getString(mIndexUuid), title)
        }
    }

    interface ActionListener {

        fun onDeletedItemClick(type: Int, id: Long, deletedAt: Long, uuid: String, title: CharSequence)
    }
}
