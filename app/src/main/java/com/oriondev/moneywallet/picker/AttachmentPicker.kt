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

package com.oriondev.moneywallet.picker

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.webkit.MimeTypeMap

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.Attachment
import com.oriondev.moneywallet.service.AttachmentHandlerIntentService

import java.io.File
import java.util.ArrayDeque
import java.util.ArrayList
import java.util.Locale
import java.util.Queue

/**
 * Created by andrea on 27/03/18.
 */
class AttachmentPicker : Fragment() {

    companion object {

        private const val SS_EDIT_MODE = "AttachmentPicker::SavedState::EditMode"
        private const val SS_OLD_ATTACHMENT_LIST = "AttachmentPicker::SavedState::OldAttachmentList"
        private const val SS_NEW_ATTACHMENT_LIST = "AttachmentPicker::SavedState::NewAttachmentList"
        private const val SS_DELETED_ATTACHMENT_LIST = "AttachmentPicker::SavedState::DeletedAttachmentList"

        private const val ARG_EDIT_MODE = "AttachmentPicker::Arguments::EditMode"
        private const val ARG_OLD_ATTACHMENT_LIST = "AttachmentPicker::Arguments::OldAttachmentList"

        private const val ALL_FILES = "*/*"
        private const val REQUEST_CODE_FILE_PICKER = 36347

        @JvmStatic
        fun createPicker(fragmentManager: FragmentManager, tag: String?, attachments: ArrayList<Attachment>?): AttachmentPicker {
            var picker = fragmentManager.findFragmentByTag(tag) as AttachmentPicker?
            if (picker == null) {
                picker = AttachmentPicker()
                val arguments = Bundle()
                if (attachments != null) {
                    arguments.putBoolean(ARG_EDIT_MODE, true)
                    arguments.putParcelableArrayList(ARG_OLD_ATTACHMENT_LIST, attachments)
                } else {
                    arguments.putBoolean(ARG_EDIT_MODE, false)
                }
                picker.arguments = arguments
                fragmentManager.beginTransaction().add(picker, tag).commit()
            }
            return picker
        }
    }

    private var mEditMode: Boolean = false
    private var mController: Controller? = null

    private var mOldAttachments: ArrayList<Attachment>? = null
    private var mNewAttachments: ArrayList<Attachment>? = null
    private var mDeletedAttachments: ArrayList<Attachment>? = null

    private var mQueuedUris: Queue<Uri> = ArrayDeque()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            mEditMode = savedInstanceState.getBoolean(SS_EDIT_MODE)
            mOldAttachments = savedInstanceState.getParcelableArrayList(SS_OLD_ATTACHMENT_LIST)
            mNewAttachments = savedInstanceState.getParcelableArrayList(SS_NEW_ATTACHMENT_LIST)
            mDeletedAttachments = savedInstanceState.getParcelableArrayList(SS_DELETED_ATTACHMENT_LIST)
        } else {
            val arguments: Bundle? = arguments
            if (arguments != null) {
                mEditMode = arguments.getBoolean(ARG_EDIT_MODE)
                if (mEditMode) {
                    mOldAttachments = arguments.getParcelableArrayList(ARG_OLD_ATTACHMENT_LIST)
                } else {
                    mOldAttachments = ArrayList()
                }
            } else {
                mEditMode = false
                mOldAttachments = ArrayList()
            }
            mNewAttachments = ArrayList()
            mDeletedAttachments = ArrayList()
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        if (!mQueuedUris.isEmpty()) {
            // check if there is some uri queued that should be processed
            while (!mQueuedUris.isEmpty()) {
                val queuedUri = mQueuedUris.poll()
                onFileSelected(queuedUri)
            }
        } else {
            fireCallbackSafely()
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is Controller) {
            mController = context
        }
        val intentFilter = IntentFilter()
        intentFilter.addAction(LocalAction.ACTION_ATTACHMENT_OP_STARTED)
        intentFilter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FINISHED)
        intentFilter.addAction(LocalAction.ACTION_ATTACHMENT_OP_FAILED)
        LocalBroadcastManager.getInstance(context).registerReceiver(mBroadcastReceiver, intentFilter)
    }

    fun areAllAttachmentsReady(): Boolean {
        for (attachment in mNewAttachments!!) {
            if (attachment.getStatus() === Attachment.Status.PENDING) {
                return false
            }
        }
        return true
    }

    fun remove(attachment: Attachment) {
        if (mEditMode && mOldAttachments!!.contains(attachment)) {
            // simply flag the item as deleted: in this way, if the user
            // decides to cancel the modification of the attachments,
            // they are not definitively lost.
            mOldAttachments!!.remove(attachment)
            mDeletedAttachments!!.add(attachment)
        } else {
            // remove the file permanently
            mNewAttachments!!.remove(attachment)
            deleteAttachmentAsync(attachment)
        }
        fireCallbackSafely()
    }

    fun showPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.setType(ALL_FILES)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        startActivityForResult(intent, REQUEST_CODE_FILE_PICKER)
    }

    fun cleanUp(rollback: Boolean) {
        if (rollback) {
            for (attachment in mNewAttachments!!) {
                deleteAttachmentAsync(attachment)
            }
        } else {
            for (attachment in mDeletedAttachments!!) {
                deleteAttachmentAsync(attachment)
            }
        }
    }

    fun getCurrentAttachments(): List<Attachment> {
        val attachments: MutableList<Attachment> = ArrayList()
        attachments.addAll(mOldAttachments!!)
        attachments.addAll(mNewAttachments!!)
        return attachments
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_FILE_PICKER) {
            if (resultCode == Activity.RESULT_OK) {
                val clipData: ClipData? = data!!.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        val item = clipData.getItemAt(i)
                        val uri: Uri? = item.uri
                        if (uri != null) {
                            onFileSelected(uri)
                        }
                    }
                    return
                }
                val uri: Uri? = data.data
                if (uri != null) {
                    onFileSelected(uri)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    fun addFileFromUri(uri: Uri) {
        mQueuedUris.add(uri)
    }

    private fun onFileSelected(uri: Uri) {
        val activity: Activity? = activity
        if (activity != null) {
            // retrieve information from the given uri
            var attachment: Attachment? = null
            if (uri.scheme!!.equals(ContentResolver.SCHEME_CONTENT)) {
                val contentResolver = activity.contentResolver
                val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        attachment = Attachment(
                                0L, Attachment.generateFileUID(),
                                cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)),
                                contentResolver.getType(uri),
                                cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE)),
                                Attachment.Status.PENDING
                        )
                    }
                    cursor.close()
                }
            } else {
                // this may be caused by an app that has returned an uri that points to a file
                // on the filesystem (e.g. 'file:///storage/emulated/0/...'). in this case, no
                // valid cursor is returned and the attachment is not loaded. we can try to
                // check if the user has already granted the read permission on the external
                // memory and load it as a generic file
                val permission = Manifest.permission.READ_EXTERNAL_STORAGE
                val status = activity.checkCallingOrSelfPermission(permission)
                if (status == PackageManager.PERMISSION_GRANTED) {
                    val file = File(uri.path)
                    if (file.exists()) {
                        val extension: String? = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                        var contentType = "application/octet-stream"
                        if (extension != null) {
                            val mimeType: String? = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase(Locale.getDefault()))
                            if (mimeType != null) {
                                contentType = mimeType
                            }
                        }
                        attachment = Attachment(0L,
                                Attachment.generateFileUID(),
                                file.name,
                                contentType,
                                file.length(),
                                Attachment.Status.PENDING
                        )
                    }
                }
            }
            if (attachment != null) {
                mNewAttachments!!.add(attachment)
                fireCallbackSafely()
                createAttachmentAsync(uri, attachment)
            }
        }
    }

    private fun createAttachmentAsync(uri: Uri?, attachment: Attachment?) {
        val activity: Activity? = activity
        if (activity != null) {
            val intent = Intent(activity, AttachmentHandlerIntentService::class.java)
            intent.setData(uri)
            intent.putExtra(AttachmentHandlerIntentService.ATTACHMENT, attachment)
            intent.putExtra(AttachmentHandlerIntentService.ACTION, AttachmentHandlerIntentService.ACTION_CREATE)
            activity.startService(intent)
        }
    }

    private fun deleteAttachmentAsync(attachment: Attachment?) {
        val activity: Activity? = activity
        if (activity != null) {
            val intent = Intent(activity, AttachmentHandlerIntentService::class.java)
            intent.putExtra(AttachmentHandlerIntentService.ATTACHMENT, attachment)
            intent.putExtra(AttachmentHandlerIntentService.ACTION, AttachmentHandlerIntentService.ACTION_DELETE)
            activity.startService(intent)
        }
    }

    private fun fireCallbackSafely() {
        mController?.onAttachmentListChanged(getCurrentAttachments())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SS_EDIT_MODE, mEditMode)
        outState.putParcelableArrayList(SS_OLD_ATTACHMENT_LIST, mOldAttachments)
        outState.putParcelableArrayList(SS_NEW_ATTACHMENT_LIST, mNewAttachments)
        outState.putParcelableArrayList(SS_DELETED_ATTACHMENT_LIST, mDeletedAttachments)
    }

    override fun onDetach() {
        super.onDetach()
        val activity: Activity? = activity
        if (activity != null) {
            LocalBroadcastManager.getInstance(activity).unregisterReceiver(mBroadcastReceiver)
        }
        mController = null
    }

    interface Controller {

        fun onAttachmentListChanged(attachments: List<Attachment>?)
    }

    private var mBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent?) {
            val action: String? = intent!!.action
            if (action != null) {
                val type = intent.getIntExtra(AttachmentHandlerIntentService.ACTION, 0)
                if (type == AttachmentHandlerIntentService.ACTION_CREATE) {
                    val attachment: Attachment? = intent.getParcelableExtra(AttachmentHandlerIntentService.ATTACHMENT)
                    when (action) {
                        LocalAction.ACTION_ATTACHMENT_OP_STARTED ->
                            updateAttachmentStatus(attachment, Attachment.Status.PENDING, null)
                        LocalAction.ACTION_ATTACHMENT_OP_FINISHED ->
                            updateAttachmentStatus(attachment, Attachment.Status.READY, null)
                        LocalAction.ACTION_ATTACHMENT_OP_FAILED -> {
                            val error: String? = intent.getStringExtra(AttachmentHandlerIntentService.ERROR)
                            updateAttachmentStatus(attachment, Attachment.Status.PENDING, error)
                        }
                    }
                }
            }
        }

    }

    private fun updateAttachmentStatus(attachment: Attachment?, status: Attachment.Status?, error: String?) {
        for (attach in mNewAttachments!!) {
            if (attach.equals(attachment)) {
                attach.setId(attachment!!.getId())
                attach.setStatus(status, error)
                fireCallbackSafely()
                break
            }
        }
    }
}
