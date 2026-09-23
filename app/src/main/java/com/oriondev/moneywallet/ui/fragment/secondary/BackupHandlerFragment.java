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

package com.oriondev.moneywallet.ui.fragment.secondary;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.widget.EditText;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.appcompat.widget.Toolbar;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.api.BackendException;
import com.oriondev.moneywallet.api.AbstractBackendServiceDelegate;
import com.oriondev.moneywallet.api.BackendServiceFactory;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.IFile;
import com.oriondev.moneywallet.service.BackupHandlerIntentService;
import com.oriondev.moneywallet.storage.database.backup.BackupManager;
import com.oriondev.moneywallet.ui.adapter.recycler.BackupFileAdapter;
import com.oriondev.moneywallet.ui.fragment.base.MultiPanelFragment;
import com.oriondev.moneywallet.ui.fragment.base.NavigableFragment;
import com.oriondev.moneywallet.ui.fragment.dialog.AutoBackupSettingDialog;
import com.oriondev.moneywallet.ui.view.AdvancedRecyclerView;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrea on 21/11/18.
 */
public class BackupHandlerFragment extends Fragment implements BackupFileAdapter.Controller, SwipeRefreshLayout.OnRefreshListener, Toolbar.OnMenuItemClickListener, AbstractBackendServiceDelegate.BackendServiceStatusListener {

    private static final String ARG_ALLOW_BACKUP = "BackupHandlerFragment::Arguments::AllowBackup";
    private static final String ARG_ALLOW_RESTORE = "BackupHandlerFragment::Arguments::AllowRestore";
    private static final String ARG_BACKEND_ID = "BackupHandlerFragment::Arguments::BackendId";

    public static final String BACKUP_SERVICE_CALLER_ID = "BackupHandlerFragment";

    private static final IFile ROOT_FOLDER = null;

    private boolean mAllowBackup;
    private boolean mAllowRestore;
    private AbstractBackendServiceDelegate mBackendService;
    private List<IFile> mFileStack;

    private AdvancedRecyclerView mAdvancedRecyclerView;
    private BackupFileAdapter mBackupAdapter;

    private View mCoverLayout;
    private View mPrimaryLayout;

    private Toolbar mToolbar;
    private Toolbar mCoverToolbar;

    private AutoBackupSettingDialog mAutoBackupSettingDialog;

    private LocalBroadcastManager mLocalBroadcastManager;

    public static BackupHandlerFragment newInstance(String backendId, boolean allowBackup, boolean allowRestore) {
        BackupHandlerFragment fragment = new BackupHandlerFragment();
        Bundle arguments = new Bundle();
        arguments.putBoolean(ARG_ALLOW_BACKUP, allowBackup);
        arguments.putBoolean(ARG_ALLOW_RESTORE, allowRestore);
        arguments.putString(ARG_BACKEND_ID, backendId);
        fragment.setArguments(arguments);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle arguments = getArguments();
        if (arguments != null) {
            mAllowBackup = arguments.getBoolean(ARG_ALLOW_BACKUP, true);
            mAllowRestore = arguments.getBoolean(ARG_ALLOW_RESTORE, true);
            String backendId = arguments.getString(ARG_BACKEND_ID, null);
            mBackendService = BackendServiceFactory.getServiceById(backendId, this);
            if (mBackendService != null) {
                mBackendService.registerLaunchers(this);
            }
            mFileStack = new ArrayList<>();
            openOnDefaultFolder();
        } else {
            throw new IllegalStateException("Arguments bundle is null, please instantiate the fragment using the newInstance() method instead.");
        }
        // initialize the dialog fragment used to schedule auto-backups
        mAutoBackupSettingDialog = new AutoBackupSettingDialog();
        // bind to local broadcast manager to get notified of background operations
        Activity activity = getActivity();
        if (activity != null) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(LocalAction.ACTION_BACKUP_SERVICE_STARTED);
            intentFilter.addAction(LocalAction.ACTION_BACKUP_SERVICE_FINISHED);
            intentFilter.addAction(LocalAction.ACTION_BACKUP_SERVICE_FAILED);
            mLocalBroadcastManager = LocalBroadcastManager.getInstance(activity);
            mLocalBroadcastManager.registerReceiver(mLocalBroadcastReceiver, intentFilter);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mBackendService != null) {
            if (mBackendService.isServiceEnabled(getActivity())) {
                hideCoverView();
                loadCurrentFolder();
            } else {
                showCoverView();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocalBroadcastManager != null) {
            mLocalBroadcastManager.unregisterReceiver(mLocalBroadcastReceiver);
        }
    }

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        ViewGroup view = (ViewGroup) inflater.inflate(R.layout.fragment_secondary_panel_backup_list, container, false);
        mPrimaryLayout = view.findViewById(R.id.primary_layout);
        mToolbar = view.findViewById(R.id.secondary_toolbar);
        mCoverLayout = view.findViewById(R.id.cover_layout);
        mCoverToolbar = view.findViewById(R.id.cover_toolbar);
        mToolbar.setTitle(getTitle());
        Fragment parent = getParentFragment();
        if (parent instanceof MultiPanelFragment && !((MultiPanelFragment) parent).isExtendedLayout()) {
            mToolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
            mToolbar.setNavigationOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    Fragment parent = getParentFragment();
                    if (parent instanceof NavigableFragment) {
                        ((NavigableFragment) parent).navigateBack();
                    }
                }

            });
            mToolbar.inflateMenu(R.menu.menu_backup_service_remote);
            mToolbar.setOnMenuItemClickListener(this);
            if (mCoverToolbar != null) {
                mCoverToolbar.setNavigationIcon(R.drawable.ic_arrow_back_black_24dp);
                mCoverToolbar.setNavigationOnClickListener(new View.OnClickListener() {

                    @Override
                    public void onClick(View v) {
                        Fragment parent = getParentFragment();
                        if (parent instanceof NavigableFragment) {
                            ((NavigableFragment) parent).navigateBack();
                        }
                    }

                });
            }
        }
        mAdvancedRecyclerView = view.findViewById(R.id.advanced_recycler_view);
        FloatingActionButton floatingActionButton = view.findViewById(R.id.floating_action_button);
        mAdvancedRecyclerView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mBackupAdapter = new BackupFileAdapter(this);
        mAdvancedRecyclerView.setEmptyText(R.string.message_no_file_found);
        mAdvancedRecyclerView.setAdapter(mBackupAdapter);
        mAdvancedRecyclerView.setOnRefreshListener(this);
        if (mAllowBackup) {
            floatingActionButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    View inputView = LayoutInflater.from(v.getContext()).inflate(R.layout.dialog_input, null);
                    final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
                    inputEditText.setHint(R.string.hint_password);
                    inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    AlertDialog dialog = ThemedDialog.buildMaterialDialog(v.getContext())
                            .setTitle(R.string.title_backup_create)
                            .setMessage(R.string.message_backup_create)
                            .setView(inputView)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Activity activity = getActivity();
                                    if (activity != null) {
                                        CharSequence input = inputEditText.getText();
                                        IFile folder = mFileStack.isEmpty() ? ROOT_FOLDER : mFileStack.get(mFileStack.size() - 1);
                                        Intent intent = new Intent(activity, BackupHandlerIntentService.class);
                                        intent.putExtra(BackupHandlerIntentService.BACKEND_ID, mBackendService.getId());
                                        intent.putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_BACKUP);
                                        intent.putExtra(BackupHandlerIntentService.PARENT_FOLDER, folder);
                                        if (input.length() > 0) {
                                            intent.putExtra(BackupHandlerIntentService.PASSWORD, input.toString());
                                        }
                                        intent.putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID);
                                        activity.startService(intent);
                                    }
                                }

                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .create();
                    // the three argument input() allowed an empty value, so an empty password is valid here
                    ThemedDialog.showWithInput(dialog, inputEditText, true);
                }

            });
        } else {
            floatingActionButton.setVisibility(View.GONE);
        }
        TextView coverTextView = view.findViewById(R.id.cover_text_view);
        Button coverActionButton = view.findViewById(R.id.cover_action_button);
        if (mBackendService != null) {
            coverTextView.setText(mBackendService.getBackupCoverMessage());
            coverActionButton.setText(mBackendService.getBackupCoverAction());
            coverActionButton.setOnClickListener(new View.OnClickListener() {

                @Override
                public void onClick(View v) {
                    try {
                        mBackendService.setup(getActivity());
                    } catch (BackendException e) {
                        e.printStackTrace();
                    }
                }

            });
        }
        return view;
    }

    protected void showCoverView() {
        if (mCoverLayout != null) {
            mPrimaryLayout.setVisibility(View.GONE);
            mCoverLayout.setVisibility(View.VISIBLE);
        }
    }

    protected void hideCoverView() {
        if (mCoverLayout != null) {
            // setup menu item visibility
            setMenuItemVisibility(R.id.action_disconnect, mBackendService.isDisconnectable());
            setMenuItemVisibility(R.id.action_change_folder, mBackendService.isFolderChangeable());
            // setup layout visibility
            mCoverLayout.setVisibility(View.GONE);
            mPrimaryLayout.setVisibility(View.VISIBLE);
            showLocation();
        }
    }

    private void showLocation() {
        // a WebDAV check can finish after this fragment is detached
        if (getContext() == null) {
            return;
        }
        final Context context = getContext().getApplicationContext();
        final AbstractBackendServiceDelegate backendService = mBackendService;
        new Thread(() -> {
            final String location = backendService.describeLocation(context);
            mToolbar.post(() -> mToolbar.setSubtitle(location));
        }, "backup-location").start();
    }

    protected int getTitle() {
        return mBackendService != null ? mBackendService.getName() : 0;
    }

    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_backup_service_remote;
    }

    protected void setMenuItemVisibility(int id, boolean visible) {
        Menu menu = mToolbar.getMenu();
        if (menu != null) {
            MenuItem menuItem = menu.findItem(id);
            if (menuItem != null) {
                menuItem.setVisible(visible);
            }
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_disconnect) {
            try {
                mBackendService.teardown(getActivity());
            } catch (BackendException e) {
                e.printStackTrace();
            }
        } else if (itemId == R.id.action_change_folder) {
            // the folders on the stack belong to the folder being replaced
            mFileStack.clear();
            try {
                mBackendService.setup(getActivity());
            } catch (BackendException e) {
                e.printStackTrace();
            }
        } else if (itemId == R.id.action_auto_backup) {
            String tag = getTag() + "::AutoBackupSettingDialog";
            mAutoBackupSettingDialog.show(getChildFragmentManager(), tag, mBackendService.getId());
        }
        return false;
    }

    protected boolean isStackEmpty() {
        return mFileStack.isEmpty();
    }

    public void loadCurrentFolder() {
        loadFolder(mFileStack.isEmpty() ? ROOT_FOLDER : mFileStack.get(mFileStack.size() - 1));
    }

    protected void loadFolder(IFile folder) {
        Activity activity = getActivity();
        if (activity != null) {
            Intent intent = new Intent(activity, BackupHandlerIntentService.class);
            intent.putExtra(BackupHandlerIntentService.BACKEND_ID, mBackendService.getId());
            intent.putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_LIST);
            intent.putExtra(BackupHandlerIntentService.PARENT_FOLDER, folder);
            intent.putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID);
            activity.startService(intent);
        }
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
    public void onFileClick(final IFile file) {
        if (file.isDirectory()) {
            mFileStack.add(file);
            mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
            loadFolder(file);
        } else {
            Activity activity = getActivity();
            if (mAllowRestore && activity != null) {
                if (file.getName().endsWith(BackupManager.BACKUP_EXTENSION_STANDARD)) {
                    ThemedDialog.buildMaterialDialog(activity)
                            .setTitle(R.string.title_backup_restore)
                            .setMessage(R.string.message_backup_restore_standard)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(getActivity(), BackupHandlerIntentService.class);
                                    intent.putExtra(BackupHandlerIntentService.BACKEND_ID, mBackendService.getId());
                                    intent.putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE);
                                    intent.putExtra(BackupHandlerIntentService.BACKUP_FILE, file);
                                    intent.putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID);
                                    getActivity().startService(intent);
                                }

                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                } else if (file.getName().endsWith(BackupManager.BACKUP_EXTENSION_PROTECTED)) {
                    View inputView = LayoutInflater.from(activity).inflate(R.layout.dialog_input, null);
                    final EditText inputEditText = inputView.findViewById(R.id.dialog_input_edit_text);
                    inputEditText.setHint(R.string.hint_password);
                    inputEditText.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    AlertDialog dialog = ThemedDialog.buildMaterialDialog(activity)
                            .setTitle(R.string.title_backup_restore)
                            .setMessage(R.string.message_backup_restore_protected)
                            .setView(inputView)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(getActivity(), BackupHandlerIntentService.class);
                                    intent.putExtra(BackupHandlerIntentService.BACKEND_ID, mBackendService.getId());
                                    intent.putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE);
                                    intent.putExtra(BackupHandlerIntentService.BACKUP_FILE, file);
                                    intent.putExtra(BackupHandlerIntentService.PASSWORD, inputEditText.getText().toString());
                                    intent.putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID);
                                    getActivity().startService(intent);
                                }

                            })
                            .create();
                    ThemedDialog.showWithInput(dialog, inputEditText, false);
                } else if (file.getName().endsWith(BackupManager.BACKUP_EXTENSION_LEGACY)) {
                    ThemedDialog.buildMaterialDialog(activity)
                            .setTitle(R.string.title_backup_restore)
                            .setMessage(R.string.message_backup_restore_legacy)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(getActivity(), BackupHandlerIntentService.class);
                                    intent.putExtra(BackupHandlerIntentService.BACKEND_ID, mBackendService.getId());
                                    intent.putExtra(BackupHandlerIntentService.ACTION, BackupHandlerIntentService.ACTION_RESTORE);
                                    intent.putExtra(BackupHandlerIntentService.BACKUP_FILE, file);
                                    intent.putExtra(BackupHandlerIntentService.CALLER_ID, BACKUP_SERVICE_CALLER_ID);
                                    getActivity().startService(intent);
                                }

                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                }
            }
        }
    }

    @Override
    public void onRefresh() {
        loadCurrentFolder();
    }

    @Override
    public void onBackendStatusChange(boolean enabled) {
        if (enabled) {
            hideCoverView();
            if (isStackEmpty()) {
                // asked again here: at onCreate the backend may not have been usable yet,
                // and a default resolved then can have come back empty handed
                openOnDefaultFolder();
                loadCurrentFolder();
            }
        } else {
            showCoverView();
        }
    }

    private void openOnDefaultFolder() {
        if (!mFileStack.isEmpty() || mBackendService == null) {
            return;
        }
        IFile defaultFolder = BackendServiceFactory.getFile(mBackendService.getId(), null);
        if (defaultFolder != null) {
            mFileStack.add(defaultFolder);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
        if (!mBackendService.handlePermissionsResult(getActivity(), requestCode, permissions, grantResults)) {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (!mBackendService.handleActivityResult(getActivity(), requestCode, resultCode, data)) {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            String callerId = intent.getStringExtra(BackupHandlerIntentService.CALLER_ID);
            if (!BACKUP_SERVICE_CALLER_ID.equals(callerId)) {
                // the service has sent a message using the local broadcast manager but it
                // is not directed to this fragment. we can simply ignore it. this is useful
                // to avoid that the dialog appear when the auto backup is fired by the
                // system and the user is browsing the backup section of the application.
                return;
            }
            if (TextUtils.equals(action, LocalAction.ACTION_BACKUP_SERVICE_STARTED)) {
                int operation = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0);
                if (operation == BackupHandlerIntentService.ACTION_LIST) {
                    mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.LOADING);
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKUP_SERVICE_FINISHED)) {
                int operation = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0);
                if (operation == BackupHandlerIntentService.ACTION_LIST) {
                    List<IFile> files = intent.getParcelableArrayListExtra(BackupHandlerIntentService.FOLDER_CONTENT);
                    mBackupAdapter.setFileList(files, mFileStack.size() > 0);
                    if (mBackupAdapter.getItemCount() == 0) {
                        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.EMPTY);
                    } else {
                        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.READY);
                    }
                } else if (operation == BackupHandlerIntentService.ACTION_BACKUP) {
                    IFile backup = intent.getParcelableExtra(BackupHandlerIntentService.BACKUP_FILE);
                    mBackupAdapter.addFileToList(backup);
                    // reveal the list in case the folder was empty before this backup,
                    // otherwise the empty state keeps hiding the newly created file
                    if (mBackupAdapter.getItemCount() > 0) {
                        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.READY);
                    }
                }
            } else if (TextUtils.equals(action, LocalAction.ACTION_BACKUP_SERVICE_FAILED)) {
                int operation = intent.getIntExtra(BackupHandlerIntentService.ACTION, 0);
                if (operation == BackupHandlerIntentService.ACTION_LIST) {
                    Exception exception = (Exception) intent.getSerializableExtra(BackupHandlerIntentService.EXCEPTION);
                    if (exception instanceof BackendException && ((BackendException) exception).isRecoverable()) {
                        mBackupAdapter.setFileList(null, false);
                        mAdvancedRecyclerView.setErrorText(R.string.message_error_backend_recoverable);
                        mAdvancedRecyclerView.setState(AdvancedRecyclerView.State.ERROR);
                    } else {
                        try {
                            mBackendService.teardown(getActivity());
                        } catch (BackendException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

    };
}