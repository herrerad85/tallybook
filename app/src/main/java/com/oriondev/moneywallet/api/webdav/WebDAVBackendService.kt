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

package com.oriondev.moneywallet.api.webdav

import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.Toast

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes

import androidx.appcompat.app.AlertDialog
import com.oriondev.moneywallet.R
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate
import com.oriondev.moneywallet.api.BackendServiceFactory
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog
import com.oriondev.moneywallet.utils.Urls

/**
 * Setup is a plain dialog rather than an activity result flow, because a self hosted server needs
 * three typed values and no external app. That means this delegate has to report success itself:
 * nothing else will notice that the user connected.
 */
class WebDAVBackendService : AbstractBackendServiceDelegate {

    constructor(listener: BackendServiceStatusListener?) : super(listener)

    override fun getId(): String {
        return BackendServiceFactory.SERVICE_ID_WEBDAV
    }

    @StringRes
    override fun getName(): Int {
        return R.string.service_backup_webdav
    }

    @StringRes
    override fun getBackupCoverMessage(): Int {
        return R.string.cover_message_backup_webdav_title
    }

    @StringRes
    override fun getBackupCoverAction(): Int {
        return R.string.cover_message_backup_webdav_button
    }

    override fun isServiceEnabled(context: Context?): Boolean {
        return getServerUrl(context!!) != null
    }

    override fun setup(activity: ComponentActivity?) {
        showSetupDialog(activity!!, getServerUrl(activity), getUsername(activity))
    }

    private fun showSetupDialog(activity: ComponentActivity, url: String?, username: String?) {
        val view = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup)
        val dialog = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.service_backup_webdav)
                .setView(view)
                .setPositiveButton(R.string.action_connect, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create()

        val urlField = view.findViewById<EditText>(R.id.webdav_url_edit_text)
        val usernameField = view.findViewById<EditText>(R.id.webdav_username_edit_text)
        val passwordField = view.findViewById<EditText>(R.id.webdav_password_edit_text)
        urlField.setText(url)
        usernameField.setText(username)

        dialog.show()
        // Replacing the listener on the button itself is what keeps Connect from closing the form,
        // so a rejected url can be corrected without typing everything again. The button only
        // exists once the dialog has been shown.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(object : View.OnClickListener {

            override fun onClick(v: View?) {
                val typedUrl = urlField.text.toString().trim { it <= ' ' }
                val typedUsername = usernameField.text.toString().trim { it <= ' ' }
                val typedPassword = passwordField.text.toString()
                if (typedUrl.isEmpty()) {
                    urlField.error = activity.getString(R.string.hint_webdav_url)
                    return
                }
                if (!Urls.isAcceptableUrl(typedUrl)) {
                    urlField.error = activity.getString(R.string.message_webdav_url_scheme)
                    return
                }
                verifyAndStore(activity, dialog, typedUrl, typedUsername, typedPassword)
            }
        })
    }

    /**
     * Credentials are checked against the server before they are saved, so a typo is reported while
     * the form is still open rather than surfacing later as an empty folder listing.
     */
    private fun verifyAndStore(activity: ComponentActivity, dialog: AlertDialog,
                               url: String, username: String, password: String) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        Thread(object : Runnable {

            override fun run() {
                var failure: String? = null
                try {
                    WebDAVClient(url, username, password).checkConnection()
                } catch (e: WebDAVException) {
                    // Carry the cause into the message. "Could not list" on its own tells the user
                    // nothing they can act on, and the cause is where the real reason lives.
                    Log.e("WebDAVBackendService", "Connection check failed", e)
                    val cause = e.cause
                    failure = if (cause != null) e.message + ": " + cause.message else e.message
                }
                val result = failure
                activity.runOnUiThread(object : Runnable {

                    override fun run() {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = true
                        if (result != null) {
                            Toast.makeText(activity, result, Toast.LENGTH_LONG).show()
                            return
                        }
                        storeCredentials(activity, url, username, password)
                        dialog.dismiss()
                        setBackendServiceEnabled(true)
                    }
                })
            }
        }).start()
    }

    override fun teardown(activity: ComponentActivity?) {
        ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_backup_service_webdav_disconnect)
                .setPositiveButton(android.R.string.yes, object : DialogInterface.OnClickListener {

                    override fun onClick(dialog: DialogInterface?, which: Int) {
                        clearCredentials(activity!!)
                        setBackendServiceEnabled(false)
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show()
    }

    companion object {

        private const val PREFERENCES = "webdav"
        private const val KEY_URL = "server_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"

        private fun preferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        }

        @JvmStatic
        internal fun getServerUrl(context: Context): String? {
            return preferences(context).getString(KEY_URL, null)
        }

        @JvmStatic
        internal fun getUsername(context: Context): String? {
            return preferences(context).getString(KEY_USERNAME, null)
        }

        @JvmStatic
        internal fun getPassword(context: Context): String? {
            return preferences(context).getString(KEY_PASSWORD, null)
        }

        private fun storeCredentials(context: Context, url: String, username: String, password: String) {
            preferences(context).edit()
                    .putString(KEY_URL, url)
                    .putString(KEY_USERNAME, username)
                    .putString(KEY_PASSWORD, password)
                    .apply()
        }

        private fun clearCredentials(context: Context) {
            preferences(context).edit().clear().apply()
        }
    }
}
