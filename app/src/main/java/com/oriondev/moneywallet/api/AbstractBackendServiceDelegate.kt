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

package com.oriondev.moneywallet.api

import android.content.Context
import android.content.Intent

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

/**
 * Created by andrea on 21/11/18.
 */
abstract class AbstractBackendServiceDelegate {

    private val mListener: BackendServiceStatusListener?

    constructor(listener: BackendServiceStatusListener?) {
        mListener = listener
    }

    abstract fun getId(): String

    @StringRes
    abstract fun getBackupCoverMessage(): Int

    @StringRes
    abstract fun getName(): Int

    @StringRes
    abstract fun getBackupCoverAction(): Int

    abstract fun isServiceEnabled(context: Context?): Boolean

    /**
     * Registers the activity result launchers this backend needs. A Fragment refuses a
     * registration once its view has been created, so call this from onAttach or onCreate,
     * never from [setup], which runs on a click. A backend that registers a launcher
     * here needs this called before its [setup].
     */
    open fun registerLaunchers(fragment: Fragment) {
        // most backends have nothing to register
    }

    @Throws(BackendException::class)
    abstract fun setup(activity: ComponentActivity?)

    @Throws(BackendException::class)
    abstract fun teardown(activity: ComponentActivity?)

    open fun handlePermissionsResult(context: Context?, requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean {
        return false
    }

    open fun handleActivityResult(context: Context?, requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        return false
    }

    /**
     * Whether the user can disconnect (forget/revoke) this backend. Most backends can be
     * disconnected; the local external-memory backend cannot, since it holds no session to drop.
     */
    open fun isDisconnectable(): Boolean {
        return true
    }

    /**
     * Whether [setup] can be run again on an enabled backend to pick a different location.
     */
    open fun isFolderChangeable(): Boolean {
        return false
    }

    /**
     * A line naming where this backend stores its backups, or null if it has none to show. It
     * may query another app's provider, so call it off the main thread.
     */
    open fun describeLocation(context: Context?): String? {
        return null
    }

    protected fun setBackendServiceEnabled(enabled: Boolean) {
        if (mListener != null) {
            mListener.onBackendStatusChange(enabled)
        }
    }

    interface BackendServiceStatusListener {

        fun onBackendStatusChange(enabled: Boolean)
    }
}
