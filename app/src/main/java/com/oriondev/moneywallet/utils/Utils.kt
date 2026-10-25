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

package com.oriondev.moneywallet.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Color
import android.os.Build
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import android.view.View
import android.view.ViewGroup

import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.preference.PreferenceManager
import com.oriondev.moneywallet.storage.database.backup.BackupManager

import java.text.DecimalFormat
import java.util.Random

/**
 * Created by andrea on 03/02/18.
 */

object Utils {

    private val PALETTE = intArrayOf(
            Color.rgb(204, 198, 24),
            Color.rgb(229, 163, 25),
            Color.rgb(232, 111, 40),
            Color.rgb(212, 75, 145),
            Color.rgb(117, 96, 165),
            Color.rgb(54, 142, 92),
            Color.rgb(129, 191, 22),
            Color.rgb(224, 184, 26),
            Color.rgb(229, 138, 24),
            Color.rgb(235, 89, 92),
            Color.rgb(167, 78, 160),
            Color.rgb(66, 117, 138),
            Color.rgb(85, 169, 48)
    )

    @JvmStatic
    fun getHexColor(color: Int): String {
        return String.format("#%06X", (0xFFFFFF and color))
    }

    @JvmStatic
    fun isColorLight(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        return red * 0.299 + green * 0.587 + blue * 0.114 > 186
    }

    @JvmStatic
    fun getBestColor(color: Int): Int {
        if (isColorLight(color)) {
            return Color.BLACK
        }
        return Color.WHITE
    }

    @JvmStatic
    fun readableFileSize(size: Long): String {
        if (size <= 0) return "0"
        val units = arrayOf("B", "kB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }

    /**
     * From Android 13 POST_NOTIFICATIONS is a runtime permission, and an app targeting 33 or
     * later gets no prompt from the system, so anything that speaks through a notification has
     * to ask for itself.
     *
     * At most once per install. Android treats a second refusal as permanent, and the caller
     * cannot tell a never asked state from an exhausted one, so the count is kept here instead.
     * Callers record the ask with [PreferenceManager.setAskedNotificationPermission].
     */
    @JvmStatic
    fun shouldAskNotificationPermission(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PreferenceManager.hasAskedNotificationPermission()
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun getFileIcon(extension: String?): Int {
        if (extension != null) {
            when (extension) {
                BackupManager.BACKUP_EXTENSION_LEGACY,
                BackupManager.BACKUP_EXTENSION_STANDARD,
                BackupManager.BACKUP_EXTENSION_PROTECTED ->
                    return R.drawable.ic_notification
                ".txt",
                ".doc",
                ".docx" ->
                    return R.drawable.ic_file_document_24dp
                ".png",
                ".jpg",
                ".jpeg",
                ".gif",
                ".bmp" ->
                    return R.drawable.ic_file_image_24dp
                ".mp4" ->
                    return R.drawable.ic_file_video_24dp
                ".pdf" ->
                    return R.drawable.ic_file_pdf_24dp
            }
        }
        return R.drawable.ic_file_outline_24dp
    }

    @JvmStatic
    fun getRandomMDColor(index: Int): Int {
        return PALETTE[index % PALETTE.size]
    }

    @JvmStatic
    fun getRandomMDColor(): Int {
        return PALETTE[Random().nextInt(PALETTE.size)]
    }

    @JvmStatic
    fun findViewGroupByIds(activity: Activity, @IdRes vararg resIds: Int): ViewGroup? {
        val rootView = activity.window.decorView
        val contentView = rootView.findViewById<View>(android.R.id.content)
        return findViewGroupByIds(if (contentView != null) contentView else rootView, *resIds)
    }

    @JvmStatic
    fun findViewGroupByIds(root: View?, @IdRes vararg resIds: Int): ViewGroup? {
        for (redId in resIds) {
            val view = root!!.findViewById<ViewGroup>(redId)
            if (view != null) {
                return view
            }
        }
        return null
    }

    /**
     * @param cursor a wallet row cursor whose projection includes the name. Moved to its first
     *               row, but not closed: the caller, or its loader, still owns it.
     * @return the wallet name, or null when the cursor is null or holds no row.
     */
    @JvmStatic
    fun readWalletName(cursor: Cursor?): String? {
        if (cursor != null && cursor.moveToFirst()) {
            return cursor.getString(cursor.getColumnIndexOrThrow(Contract.Wallet.NAME))
        }
        return null
    }

    @JvmStatic
    fun wrapAsArrayList(list: List<IFile>?): ArrayList<IFile> {
        if (list is ArrayList<*>) {
            return list as ArrayList<IFile>
        } else {
            val wrappedList = ArrayList<IFile>()
            wrappedList.addAll(list!!)
            return wrappedList
        }
    }

}
