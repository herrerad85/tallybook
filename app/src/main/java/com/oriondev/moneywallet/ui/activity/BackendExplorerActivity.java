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

package com.oriondev.moneywallet.ui.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.EditText;
import androidx.annotation.MenuRes;
import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Lifecycle;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import android.text.InputType;
import android.text.TextUtils;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.service.BackendHandlerIntentService;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity;
import com.oriondev.moneywallet.ui.adapter.recycler.BackupFileAdapter;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrea on 26/11/18.
 */
public class BackendExplorerActivity extends SinglePanelActivity implements SwipeRefreshLayout.OnRefreshListener, BackupFileAdapter.Controller {

    public static final String BACKEND_ID = "BackendExplorerActivity::Arguments::BackendId";

    public static final String RESULT_FILE = "BackendExplorerActivity::Result::File";

    private static final IFile ROOT_FOLDER = null;

    private AdvancedRecyclerView mAdvancedRecyclerView;
    private BackupFileAdapter mAdapter;

    private String mBackendId;
    private List<IFile> mFileStack;

    private LocalBroadcastManager mLocalBroadcastManager;

    private boolean mReloadOnStart;

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_activity_single_panel_body_list, parent, true);
        mAdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view);
        mAdvancedRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mAdvancedRecyclerView.setEmptyText(R.string.message_no_file_found);
        mAdapter = new BackupFileAdapter(this);
        mAdvancedRecyclerView.setAdapter(mAdapter);
        mAdvancedRecyclerView.setOnRefreshListener(this);
        // unpack intent info
        Intent intent = getIntent();
        mBackendId = intent.getStringExtra(BACKEND_ID);
        mFileStack = new ArrayList<>();
        // open where a backup would go, the same folder the backup screen opens on
        IFile defaultFolder = BackendServiceFactory.getFile(mBackendId, null);
        if (defaultFolder != null) {
            mFileStack.add(defaultFolder);
        }
        // attach the activity to the service
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocalAction.ACTION_BACKEND_SERVICE_STARTED);
        intentFilter.addAction(LocalAction.ACTION_BACKEND_SERVICE_FINISHED);
        intentFilter.addAction(LocalAction.ACTION_BACKEND_SERVICE_FAILED);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mLocalBroadcastManager.registerReceiver(mLocalBroadcastReceiver, intentFilter);
    }

    @Override
    protected void onViewCreated(Bundle savedInstanceState) {
        loadCurrentFolder();
    }

    @Override
    protected void onSetupFloatingActionButton(FloatingActionButton floatingActionButton) {
        super.onSetupFloatingActionButton(floatingActionButton);
        if (floatingActionButton != null) {
            floatingActionButton.setImageResource(R.drawable.ic_create_new_folder_black_24dp);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mReloadOnStart) {
            mReloadOnStart = false;
            loadCurrentFolder();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocalBroadcastManager != null) {
            mLocalBroadcastManager.unregisterReceiver(mLocalBroadcastReceiver);
        }
    }

    @Override
    protected int getActivityTitleRes() {
        return R.string.title_activity_backend_folder_picker;
    }

    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_backend_explorer;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_select_folder) {
            IFile folder = getCurrentFolder();
            if (folder == null) {
                // this backend's own default, rather than a local path it cannot decode
                folder = BackendServiceFactory.getFile(mBackendId, null);
            }
            if (folder == null) {
                // The top of the list is not a folder this backend can name. Choosing it used
                // to send back a result carrying no file, and the settings dialog read that as
                // the answer and dropped the folder it already held.
                Toast.makeText(this, R.string.message_backend_open_a_folder, Toast.LENGTH_LONG).show();
                return false;
            }
            Intent intent = new Intent();
            intent.putExtra(RESULT_FILE, folder);
            setResult(RESULT_OK, intent);
            finish();
        }
        return false;
    }

    @Override
    public void navigateBack() {
        int stackSize = mFileStack.size();
        if (stackSize > 0) {
            mFileStack.remove(stackSize - 1);
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
            loadCurrentFolder();
        }
    }

    @Override
    public void onFileClick(IFile file) {
        if (file.isDirectory()) {
            mFileStack.add(file);
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
            loadFolder(file);
        }
    }

    @Override
    public void onRefresh() {
        loadCurrentFolder();
    }

    private IFile getCurrentFolder() {
        return mFileStack.isEmpty() ? ROOT_FOLDER : mFileStack.get(mFileStack.size() - 1);
    }

    private void loadCurrentFolder() {
        loadFolder(getCurrentFolder());
    }

    private void loadFolder(IFile folder) {
        Intent intent = new Intent(this, BackendHandlerIntentService.class);
        intent.putExtra(BackendHandlerIntentService.BACKEND_ID, mBackendId);
        intent.putExtra(BackendHandlerIntentService.ACTION, BackendHandlerIntentService.ACTION_LIST);
        intent.putExtra(BackendHandlerIntentService.PARENT_FOLDER, folder);
        startService(intent);
    }

    @Override
    protected void onFloatingActionButtonClick() {
        View inputView = LayoutInflater.from(this).inflate(R.layout.dialog_input, null);
        final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
        inputEditText.setHint(R.string.hint_new_folder);
        inputEditText.setInputType(InputType.TYPE_CLASS_TEXT);
        AlertDialog dialog = ThemedDialog.buildMaterialDialog(this)
                .setTitle(R.string.title_backend_create_folder)
                .setMessage(R.string.message_backend_create_folder)
                .setView(inputView)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(BackendExplorerActivity.this, BackendHandlerIntentService.class);
                        intent.putExtra(BackendHandlerIntentService.BACKEND_ID, mBackendId);
                        intent.putExtra(BackendHandlerIntentService.ACTION, BackendHandlerIntentService.ACTION_CREATE_FOLDER);
                        intent.putExtra(BackendHandlerIntentService.PARENT_FOLDER, getCurrentFolder());
                        intent.putExtra(BackendHandlerIntentService.FOLDER_NAME, inputEditText.getText().toString());
                        startService(intent);
                    }

                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        ThemedDialog.showWithInput(dialog, inputEditText, false);
    }

    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_STARTED)) {
                int operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0);
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_FINISHED)) {
                int operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0);
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    List<IFile> files = intent.getParcelableArrayListExtra(BackendHandlerIntentService.FOLDER_CONTENT);
                    mAdapter.setFileList(files, mFileStack.size() > 0);
                    if (mAdapter.getItemCount() == 0) {
                        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.EMPTY);
                    } else {
                        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.READY);
                    }
                } else if (operation == BackendHandlerIntentService.ACTION_CREATE_FOLDER) {
                    if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
                        loadCurrentFolder();
                    } else {
                        // starting a service from the background can throw, so reload once the screen is back
                        mReloadOnStart = true;
                    }
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKEND_SERVICE_FAILED)) {
                int operation = intent.getIntExtra(BackendHandlerIntentService.ACTION, 0);
                if (operation == BackendHandlerIntentService.ACTION_LIST) {
                    mAdapter.setFileList(null, false);
                    mAdvancedRecyclerView.setErrorText(R.string.message_error_backend_recoverable);
                    mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.ERROR);
                } else if (operation == BackendHandlerIntentService.ACTION_CREATE_FOLDER) {
                    Toast.makeText(context, R.string.message_error_backend_recoverable, Toast.LENGTH_LONG).show();
                }
            }
        }

    };
}