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

package com.oriondev.moneywallet.api.webdav;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.ComponentActivity;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import androidx.appcompat.app.AlertDialog;
import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.Urls;

/**
 * Setup is a plain dialog rather than an activity result flow, because a self hosted server needs
 * three typed values and no external app. That means this delegate has to report success itself:
 * nothing else will notice that the user connected.
 */
public class WebDAVBackendService extends AbstractBackendServiceDelegate {

    private static final String PREFERENCES = "webdav";
    private static final String KEY_URL = "server_url";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";

    public WebDAVBackendService(BackendServiceStatusListener listener) {
        super(listener);
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    /*package-local*/ static String getServerUrl(Context context) {
        return preferences(context).getString(KEY_URL, null);
    }

    /*package-local*/ static String getUsername(Context context) {
        return preferences(context).getString(KEY_USERNAME, null);
    }

    /*package-local*/ static String getPassword(Context context) {
        return preferences(context).getString(KEY_PASSWORD, null);
    }

    private static void storeCredentials(Context context, String url, String username, String password) {
        preferences(context).edit()
                .putString(KEY_URL, url)
                .putString(KEY_USERNAME, username)
                .putString(KEY_PASSWORD, password)
                .apply();
    }

    private static void clearCredentials(Context context) {
        preferences(context).edit().clear().apply();
    }

    @Override
    public String getId() {
        return BackendServiceFactory.SERVICE_ID_WEBDAV;
    }

    @Override
    @StringRes
    public int getName() {
        return R.string.service_backup_webdav;
    }

    @Override
    @StringRes
    public int getBackupCoverMessage() {
        return R.string.cover_message_backup_webdav_title;
    }

    @Override
    @StringRes
    public int getBackupCoverAction() {
        return R.string.cover_message_backup_webdav_button;
    }

    @Override
    public boolean isServiceEnabled(Context context) {
        return getServerUrl(context) != null;
    }

    @Override
    public void setup(final ComponentActivity activity) {
        showSetupDialog(activity, getServerUrl(activity), getUsername(activity));
    }

    private void showSetupDialog(final ComponentActivity activity, @Nullable String url, @Nullable String username) {
        View view = ThemedDialog.inflateScrollableView(activity, R.layout.dialog_webdav_setup);
        final AlertDialog dialog = ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.service_backup_webdav)
                .setView(view)
                .setPositiveButton(R.string.action_connect, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        final EditText urlField = view.findViewById(R.id.webdav_url_edit_text);
        final EditText usernameField = view.findViewById(R.id.webdav_username_edit_text);
        final EditText passwordField = view.findViewById(R.id.webdav_password_edit_text);
        urlField.setText(url);
        usernameField.setText(username);

        dialog.show();
        // Replacing the listener on the button itself is what keeps Connect from closing the form,
        // so a rejected url can be corrected without typing everything again. The button only
        // exists once the dialog has been shown.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                String typedUrl = urlField.getText().toString().trim();
                String typedUsername = usernameField.getText().toString().trim();
                String typedPassword = passwordField.getText().toString();
                if (typedUrl.isEmpty()) {
                    urlField.setError(activity.getString(R.string.hint_webdav_url));
                    return;
                }
                if (!Urls.isAcceptableUrl(typedUrl)) {
                    urlField.setError(activity.getString(R.string.message_webdav_url_scheme));
                    return;
                }
                verifyAndStore(activity, dialog, typedUrl, typedUsername, typedPassword);
            }
        });
    }

    /**
     * Credentials are checked against the server before they are saved, so a typo is reported while
     * the form is still open rather than surfacing later as an empty folder listing.
     */
    private void verifyAndStore(final ComponentActivity activity, final AlertDialog dialog,
                                final String url, final String username, final String password) {
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
        new Thread(new Runnable() {

            @Override
            public void run() {
                String failure = null;
                try {
                    new WebDAVClient(url, username, password).checkConnection();
                } catch (WebDAVException e) {
                    // Carry the cause into the message. "Could not list" on its own tells the user
                    // nothing they can act on, and the cause is where the real reason lives.
                    Log.e("WebDAVBackendService", "Connection check failed", e);
                    Throwable cause = e.getCause();
                    failure = cause != null ? e.getMessage() + ": " + cause.getMessage() : e.getMessage();
                }
                final String result = failure;
                activity.runOnUiThread(new Runnable() {

                    @Override
                    public void run() {
                        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                        if (result != null) {
                            Toast.makeText(activity, result, Toast.LENGTH_LONG).show();
                            return;
                        }
                        storeCredentials(activity, url, username, password);
                        dialog.dismiss();
                        setBackendServiceEnabled(true);
                    }
                });
            }
        }).start();
    }

    @Override
    public void teardown(final ComponentActivity activity) {
        ThemedDialog.buildMaterialDialog(activity)
                .setTitle(R.string.title_warning)
                .setMessage(R.string.message_backup_service_webdav_disconnect)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearCredentials(activity);
                        setBackendServiceEnabled(false);
                    }
                })
                .setNegativeButton(android.R.string.no, null)
                .show();
    }
}
