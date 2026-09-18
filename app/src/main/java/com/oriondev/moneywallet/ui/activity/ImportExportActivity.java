package com.oriondev.moneywallet.ui.activity;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.MenuRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.FragmentManager;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.DataFormat;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.picker.ExportColumnsPicker;
import com.oriondev.moneywallet.picker.ImportExportFormatPicker;
import com.oriondev.moneywallet.picker.WalletPicker;
import com.oriondev.moneywallet.service.ImportExportIntentService;
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;
import com.oriondev.moneywallet.ui.activity.base.SinglePanelActivity;
import com.oriondev.moneywallet.ui.fragment.dialog.GenericProgressDialog;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;
import com.oriondev.moneywallet.ui.view.text.Validator;
import com.oriondev.moneywallet.ui.view.theme.ThemedDialog;
import com.oriondev.moneywallet.utils.DateFormatter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Created by andrea on 19/12/18.
 */
public class ImportExportActivity extends SinglePanelActivity implements ImportExportFormatPicker.Controller, DateTimePicker.Controller, WalletPicker.MultiWalletController, WalletPicker.SingleWalletController, ExportColumnsPicker.Controller {

    public static final String MODE = "ImportExportActivity::Argument::Mode";

    public static final int MODE_EXPORT = 0;
    public static final int MODE_IMPORT = 1;

    private static final String TAG_DATA_FORMAT_PICKER = "ImportExportActivity::Tag::DataFormatPicker";
    private static final String TAG_START_DATE_TIME_PICKER = "ImportExportActivity::Tag::StartDateTimePicker";
    private static final String TAG_END_DATE_TIME_PICKER = "ImportExportActivity::Tag::EndDateTimePicker";
    private static final String TAG_WALLET_PICKER = "ImportExportActivity::Tag::WalletPicker";
    private static final String TAG_IMPORT_WALLET_PICKER = "ImportExportActivity::Tag::ImportWalletPicker";
    private static final String TAG_COLUMNS_PICKER = "ImportExportActivity::Tag::ColumnsPicker";
    private static final String TAG_PROGRESS_DIALOG = "ImportExportActivity::tag::GenericProgressDialog";

    private static final String SS_IMPORT_FILE = "ImportExportActivity::SavedState::ImportFile";
    private static final String SS_EXPORT_FOLDER_URI = "ImportExportActivity::SavedState::ExportFolderUri";
    private static final String SS_IMPORT_MAPPING = "ImportExportActivity::SavedState::ImportMapping";
    static final String SS_TOKEN = "ImportExportActivity::SavedState::Token";

    private MaterialEditText mImportFormatEditText;
    private MaterialEditText mExportFormatEditText;
    private View mExportCsvNoTransfersTextView;
    private MaterialEditText mStartDateEditText;
    private MaterialEditText mEndDateEditText;
    private MaterialEditText mWalletsEditText;
    private MaterialEditText mImportFileEditText;
    private MaterialEditText mExportFolderEditText;
    private MaterialEditText mExportColumnsEditText;
    private CheckBox mUniqueWalletCheckbox;
    private View mImportMappingLayout;
    private MaterialEditText mImportWalletEditText;
    private MaterialEditText mDateColumnEditText;
    private MaterialEditText mAmountColumnEditText;
    private MaterialEditText mDescriptionColumnEditText;
    private MaterialEditText mNoteColumnEditText;
    private MaterialEditText mCategoryColumnEditText;
    private MaterialEditText mDateFormatEditText;
    private MaterialEditText mDecimalSeparatorEditText;
    private CheckBox mSpendingPositiveCheckbox;

    private ImportExportFormatPicker mDataFormatPicker;
    private DateTimePicker mStartDateTimePicker;
    private DateTimePicker mEndDateTimePicker;
    private WalletPicker mWalletPicker;
    private ExportColumnsPicker mExportColumnsPicker;
    private WalletPicker mImportWalletPicker;

    // Scoped-storage file access: the user picks files and folders through the Storage
    // Access Framework instead of a raw filesystem browser. Imports are staged into app
    // storage so the File-based importer can read them; exports are written into app
    // storage and then copied into the picked folder through a content uri.
    private ActivityResultLauncher<String[]> mImportFileLauncher;
    private ActivityResultLauncher<Uri> mExportFolderLauncher;
    private File mImportFile;
    private Uri mExportFolderUri;

    // The header of the staged import file, and the choices made on the mapping section for a
    // file this app did not write. The wallet is held by its own picker.
    private CsvImportMapping.Header mImportHeader;
    private CsvImportMapping mImportMapping = new CsvImportMapping();

    private int mMode;

    private ArrayList<String> mTokens = new ArrayList<>();

    private GenericProgressDialog mProgressDialog;
    private LocalBroadcastManager mLocalBroadcastManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImportFileLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(),
                new ActivityResultCallback<Uri>() {

                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            onImportFileSelected(uri);
                        }
                    }

                });
        mExportFolderLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocumentTree(),
                new ActivityResultCallback<Uri>() {

                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            onExportFolderSelected(uri);
                        }
                    }

                });
        if (savedInstanceState != null) {
            ArrayList<String> tokens = savedInstanceState.getStringArrayList(SS_TOKEN);
            if (tokens != null) {
                mTokens = tokens;
            }
            String importPath = savedInstanceState.getString(SS_IMPORT_FILE);
            if (importPath != null) {
                mImportFile = new File(importPath);
                mImportFileEditText.setText(mImportFile.getName());
                try {
                    mImportHeader = CsvImportMapping.readHeader(mImportFile);
                    onImportHeaderRead((CsvImportMapping) savedInstanceState.getSerializable(SS_IMPORT_MAPPING));
                } catch (IOException | RuntimeException e) {
                    // the staged copy can no longer be read, so ask for the file again
                    mImportFile = null;
                    mImportFileEditText.setText(null);
                }
            }
            String exportFolderUri = savedInstanceState.getString(SS_EXPORT_FOLDER_URI);
            if (exportFolderUri != null) {
                mExportFolderUri = Uri.parse(exportFolderUri);
                DocumentFile folder = DocumentFile.fromTreeUri(this, mExportFolderUri);
                mExportFolderEditText.setText(folder != null ? folder.getName() : null);
            }
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(LocalAction.ACTION_IMPORT_SERVICE_STARTED);
        intentFilter.addAction(LocalAction.ACTION_IMPORT_SERVICE_FINISHED);
        intentFilter.addAction(LocalAction.ACTION_IMPORT_SERVICE_FAILED);
        intentFilter.addAction(LocalAction.ACTION_EXPORT_SERVICE_STARTED);
        intentFilter.addAction(LocalAction.ACTION_EXPORT_SERVICE_FINISHED);
        intentFilter.addAction(LocalAction.ACTION_EXPORT_SERVICE_FAILED);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(this);
        mLocalBroadcastManager.registerReceiver(mLocalBroadcastReceiver, intentFilter);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mLocalBroadcastManager != null) {
            mLocalBroadcastManager.unregisterReceiver(mLocalBroadcastReceiver);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mImportFile != null) {
            outState.putString(SS_IMPORT_FILE, mImportFile.getAbsolutePath());
        }
        if (mExportFolderUri != null) {
            outState.putString(SS_EXPORT_FOLDER_URI, mExportFolderUri.toString());
        }
        outState.putSerializable(SS_IMPORT_MAPPING, mImportMapping);
        outState.putStringArrayList(SS_TOKEN, mTokens);
    }

    @Override
    protected void onCreatePanelView(LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.layout_panel_import_export, parent, true);
        mImportFormatEditText = view.findViewById(R.id.import_format_edit_text);
        mExportFormatEditText = view.findViewById(R.id.export_format_edit_text);
        mExportCsvNoTransfersTextView = view.findViewById(R.id.export_csv_no_transfers_text_view);
        mStartDateEditText = view.findViewById(R.id.start_date_edit_text);
        mEndDateEditText = view.findViewById(R.id.end_date_edit_text);
        mWalletsEditText = view.findViewById(R.id.wallets_edit_text);
        mImportFileEditText = view.findViewById(R.id.import_file_edit_text);
        mExportFolderEditText = view.findViewById(R.id.export_folder_edit_text);
        mExportColumnsEditText = view.findViewById(R.id.export_optional_columns_edit_text);
        mUniqueWalletCheckbox = view.findViewById(R.id.export_unique_wallet_checkbox);
        mImportMappingLayout = view.findViewById(R.id.import_mapping_layout);
        mImportWalletEditText = view.findViewById(R.id.import_wallet_edit_text);
        mDateColumnEditText = view.findViewById(R.id.import_date_column_edit_text);
        mAmountColumnEditText = view.findViewById(R.id.import_amount_column_edit_text);
        mDescriptionColumnEditText = view.findViewById(R.id.import_description_column_edit_text);
        mNoteColumnEditText = view.findViewById(R.id.import_note_column_edit_text);
        mCategoryColumnEditText = view.findViewById(R.id.import_category_column_edit_text);
        mDateFormatEditText = view.findViewById(R.id.import_date_format_edit_text);
        mDecimalSeparatorEditText = view.findViewById(R.id.import_decimal_separator_edit_text);
        mSpendingPositiveCheckbox = view.findViewById(R.id.import_spending_positive_checkbox);
        // check activity mode and update ui
        mMode = getActivityMode();
        mImportFormatEditText.setVisibility(mMode == MODE_IMPORT ? View.VISIBLE : View.GONE);
        mExportFormatEditText.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        mStartDateEditText.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        mEndDateEditText.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        mWalletsEditText.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        mImportFileEditText.setVisibility(mMode == MODE_IMPORT ? View.VISIBLE : View.GONE);
        mExportFolderEditText.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        mExportColumnsEditText.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        mUniqueWalletCheckbox.setVisibility(mMode == MODE_EXPORT ? View.VISIBLE : View.GONE);
        // attach listeners to views
        mImportFormatEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                DataFormat[] dataFormats = new DataFormat[]{
                        DataFormat.CSV
                };
                mDataFormatPicker.showPicker(dataFormats);
            }

        });
        mExportFormatEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                DataFormat[] dataFormats = new DataFormat[]{
                        DataFormat.CSV,
                        DataFormat.XLS,
                        DataFormat.PDF
                };
                mDataFormatPicker.showPicker(dataFormats);
            }

        });
        mStartDateEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mStartDateTimePicker.showDatePicker();
            }

        });
        mStartDateEditText.setOnCancelButtonClickListener(new MaterialEditText.CancelButtonListener() {

            @Override
            public boolean onCancelButtonClick(@NonNull MaterialEditText materialEditText) {
                mStartDateTimePicker.setCurrentDateTime(null);
                return false;
            }

        });
        mEndDateEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mEndDateTimePicker.showDatePicker();
            }

        });
        mEndDateEditText.setOnCancelButtonClickListener(new MaterialEditText.CancelButtonListener() {

            @Override
            public boolean onCancelButtonClick(@NonNull MaterialEditText materialEditText) {
                mEndDateTimePicker.setCurrentDateTime(null);
                return false;
            }

        });
        mWalletsEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mWalletPicker.showMultiWalletPicker();
            }

        });
        mImportFileEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                // accept any mime type: csv files are reported inconsistently by providers
                mImportFileLauncher.launch(new String[]{"*/*"});
            }

        });
        mExportFolderEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mExportFolderLauncher.launch(null);
            }

        });
        // disable edit texts
        mImportFormatEditText.setTextViewMode(true);
        mExportFormatEditText.setTextViewMode(true);
        mStartDateEditText.setTextViewMode(true);
        mEndDateEditText.setTextViewMode(true);
        mWalletsEditText.setTextViewMode(true);
        mImportFileEditText.setTextViewMode(true);
        mExportFolderEditText.setTextViewMode(true);
        mExportColumnsEditText.setTextViewMode(true);
        mImportWalletEditText.setTextViewMode(true);
        mDateColumnEditText.setTextViewMode(true);
        mAmountColumnEditText.setTextViewMode(true);
        mDescriptionColumnEditText.setTextViewMode(true);
        mNoteColumnEditText.setTextViewMode(true);
        mCategoryColumnEditText.setTextViewMode(true);
        mDateFormatEditText.setTextViewMode(true);
        mDecimalSeparatorEditText.setTextViewMode(true);
        mImportWalletEditText.setOnClickListener(v -> mImportWalletPicker.showSingleWalletPicker());
        mDateColumnEditText.setOnClickListener(v -> showColumnPicker(mDateColumnEditText, false,
                mImportMapping.date, column -> mImportMapping.date = column));
        mAmountColumnEditText.setOnClickListener(v -> showColumnPicker(mAmountColumnEditText, false,
                mImportMapping.amount, column -> mImportMapping.amount = column));
        mDescriptionColumnEditText.setOnClickListener(v -> showColumnPicker(mDescriptionColumnEditText, true,
                mImportMapping.description, column -> mImportMapping.description = column));
        mNoteColumnEditText.setOnClickListener(v -> showColumnPicker(mNoteColumnEditText, true,
                mImportMapping.note, column -> mImportMapping.note = column));
        mCategoryColumnEditText.setOnClickListener(v -> showColumnPicker(mCategoryColumnEditText, true,
                mImportMapping.category, column -> mImportMapping.category = column));
        mDateFormatEditText.setOnClickListener(v -> showChoicePicker(mDateFormatEditText,
                CsvImportMapping.DATE_PATTERNS,
                Arrays.asList(CsvImportMapping.DATE_PATTERNS).indexOf(mImportMapping.datePattern),
                which -> mImportMapping.datePattern = CsvImportMapping.DATE_PATTERNS[which]));
        mDecimalSeparatorEditText.setOnClickListener(v -> showChoicePicker(mDecimalSeparatorEditText,
                new String[] {getString(R.string.csv_import_decimal_dot), getString(R.string.csv_import_decimal_comma)},
                mImportMapping.decimalComma ? 1 : 0,
                which -> mImportMapping.decimalComma = which == 1));
        addRequiredValidator(mImportWalletEditText, R.string.error_input_missing_wallet,
                () -> mImportWalletPicker.isSelected());
        addRequiredValidator(mDateColumnEditText, R.string.csv_import_error_missing_date_column,
                () -> mImportMapping.date != CsvImportMapping.NONE);
        addRequiredValidator(mAmountColumnEditText, R.string.csv_import_error_missing_amount_column,
                () -> mImportMapping.amount != CsvImportMapping.NONE);
        addRequiredValidator(mDateFormatEditText, R.string.csv_import_error_missing_date_format,
                () -> mImportMapping.datePattern != null);
        // attach validators
        mImportFormatEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_format);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mDataFormatPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mExportFormatEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_format);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mDataFormatPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mWalletsEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_multiple_wallets);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mWalletPicker.isSelected();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mImportFileEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_input_file);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mImportFile != null;
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mExportFolderEditText.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(R.string.error_input_missing_output_folder);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return mExportFolderUri != null;
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
        mExportColumnsEditText.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mExportColumnsPicker.showPicker();
            }

        });
        // initialize pickers
        FragmentManager fragmentManager = getSupportFragmentManager();
        mDataFormatPicker = ImportExportFormatPicker.createPicker(fragmentManager, TAG_DATA_FORMAT_PICKER);
        mStartDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_START_DATE_TIME_PICKER, null);
        mEndDateTimePicker = DateTimePicker.createPicker(fragmentManager, TAG_END_DATE_TIME_PICKER, null);
        mWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_WALLET_PICKER, (Wallet[]) null);
        mExportColumnsPicker = ExportColumnsPicker.createPicker(fragmentManager, TAG_COLUMNS_PICKER);
        mImportWalletPicker = WalletPicker.createPicker(fragmentManager, TAG_IMPORT_WALLET_PICKER, (Wallet) null);
        mProgressDialog = (GenericProgressDialog) fragmentManager.findFragmentByTag(TAG_PROGRESS_DIALOG);
    }

    private int getActivityMode() {
        Intent intent = getIntent();
        if (intent != null) {
            return intent.getIntExtra(MODE, MODE_EXPORT);
        }
        return MODE_EXPORT;
    }

    @Override
    protected int getActivityTitleRes() {
        switch (mMode) {
            case MODE_EXPORT:
                return R.string.title_activity_export_data;
            case MODE_IMPORT:
                return R.string.title_activity_import_data;
        }
        return 0;
    }

    @Override
    @MenuRes
    protected int onInflateMenu() {
        return R.menu.menu_import_export;
    }

    @Override
    protected void onMenuCreated(Menu menu) {
        switch (mMode) {
            case MODE_IMPORT:
                menu.findItem(R.id.action_import_data).setVisible(true);
                menu.findItem(R.id.action_export_data).setVisible(false);
                break;
            case MODE_EXPORT:
                menu.findItem(R.id.action_import_data).setVisible(false);
                menu.findItem(R.id.action_export_data).setVisible(true);
                break;
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_import_data) {
            if (mImportFormatEditText.validate() && mImportFileEditText.validate() && (!isImportMappingShown() || validateImportMapping())) {
                ThemedDialog.buildMaterialDialog(this)
                        .setTitle(R.string.title_warning)
                        .setMessage(R.string.message_data_import_without_backup)
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                importData();
                            }

                        })
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        } else if (itemId == R.id.action_export_data) {
            if (mExportFormatEditText.validate() && mWalletsEditText.validate() && mExportFolderEditText.validate()) {
                exportData();
            }
        }
        return false;
    }

    void importData() {
        Intent intent = new Intent(this, ImportExportIntentService.class);
        intent.putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_IMPORT);
        intent.putExtra(ImportExportIntentService.FORMAT, mDataFormatPicker.getCurrentFormat());
        intent.putExtra(ImportExportIntentService.FILE, mImportFile);
        if (isImportMappingShown()) {
            mImportMapping.separator = mImportHeader.separator;
            mImportMapping.spendingPositive = mSpendingPositiveCheckbox.isChecked();
            PreferenceManager.setCsvImportMapping(CsvImportMapping.signature(mImportHeader.cells), mImportMapping.encode());
            Wallet wallet = mImportWalletPicker.getCurrentWallet();
            mImportMapping.walletId = wallet.getId();
            intent.putExtra(ImportExportIntentService.MAPPING, mImportMapping);
        }
        String token = UUID.randomUUID().toString();
        mTokens.add(token);
        intent.putExtra(ImportExportIntentService.TOKEN, token);
        startService(intent);
    }

    void exportData() {
        Intent intent = new Intent(this, ImportExportIntentService.class);
        intent.putExtra(ImportExportIntentService.MODE, ImportExportIntentService.MODE_EXPORT);
        intent.putExtra(ImportExportIntentService.FORMAT, mDataFormatPicker.getCurrentFormat());
        intent.putExtra(ImportExportIntentService.START_DATE, mStartDateTimePicker.getCurrentDateTime());
        intent.putExtra(ImportExportIntentService.END_DATE, mEndDateTimePicker.getCurrentDateTime());
        intent.putExtra(ImportExportIntentService.WALLETS, mWalletPicker.getCurrentWallets());
        // the exporter writes into app storage; the result is copied to the picked
        // folder once the service reports the export finished
        intent.putExtra(ImportExportIntentService.FOLDER, getExportCacheDir());
        intent.putExtra(ImportExportIntentService.UNIQUE_WALLET, mUniqueWalletCheckbox.isChecked());
        intent.putExtra(ImportExportIntentService.OPTIONAL_COLUMNS, mExportColumnsPicker.getCurrentServiceColumns());
        String token = UUID.randomUUID().toString();
        mTokens.add(token);
        intent.putExtra(ImportExportIntentService.TOKEN, token);
        startService(intent);
    }

    private void onImportFileSelected(Uri uri) {
        // a wallet picked for the previous file must not carry over to this one
        mImportWalletPicker.onWalletSelected(null);
        try {
            String displayName = queryDisplayName(uri);
            if (TextUtils.isEmpty(displayName)) {
                displayName = "import";
            }
            // stage the selected content into app storage so the File-based importer can read it
            File staged = new File(getCacheDir(), "import_" + displayName.replace('/', '_'));
            try (InputStream input = getContentResolver().openInputStream(uri);
                 OutputStream output = new FileOutputStream(staged)) {
                if (input == null) {
                    throw new IOException("unable to open the selected file");
                }
                copyStream(input, output);
            }
            mImportFile = staged;
            mImportFileEditText.setText(displayName);
            try {
                mImportHeader = CsvImportMapping.readHeader(staged);
            } catch (RuntimeException e) {
                // a file with nothing in it, reported the same way as a file that cannot be read
                throw new IOException(e.getMessage(), e);
            }
            onImportHeaderRead(null);
            // try to detect the file type starting from the file extension
            if (!mDataFormatPicker.isSelected()) {
                int dot = displayName.lastIndexOf('.');
                if (dot >= 0) {
                    String extension = displayName.substring(dot).toLowerCase(Locale.ENGLISH);
                    if (extension.equals(".csv")) {
                        mDataFormatPicker.setCurrentFormat(DataFormat.CSV);
                    }
                }
            }
        } catch (IOException e) {
            mImportFile = null;
            mImportFileEditText.setText(null);
            mImportHeader = null;
            onImportHeaderRead(null);
            ThemedDialog.buildMaterialDialog(this)
                    .setTitle(R.string.title_failed)
                    .setMessage(getString(R.string.message_data_import_failed, e.getMessage()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
        }
    }

    /**
     * Shows the mapping section when the staged file was not written by this app, and hides it
     * when it was. A file just picked is filled in from what was last used for a file with the
     * same header, when that still fits it. The wallet never is, since a remembered wallet id can
     * name a different wallet once a backup has been restored.
     *
     * @param chosen the choices to show, which is what a restored screen passes back, or null to
     *               look up the remembered ones.
     */
    private void onImportHeaderRead(@Nullable CsvImportMapping chosen) {
        boolean mapped = mImportHeader != null && !mImportHeader.nativeHeader;
        mImportMappingLayout.setVisibility(mapped ? View.VISIBLE : View.GONE);
        if (!mapped) {
            return;
        }
        if (chosen == null) {
            chosen = getRememberedImportMapping();
            mSpendingPositiveCheckbox.setChecked(chosen.spendingPositive);
        }
        mImportMapping = chosen;
        updateImportMappingFields();
    }

    private CsvImportMapping getRememberedImportMapping() {
        String signature = CsvImportMapping.signature(mImportHeader.cells);
        CsvImportMapping remembered = CsvImportMapping.decode(PreferenceManager.getCsvImportMapping(signature));
        int columns = mImportHeader.cells.length;
        if (remembered != null && remembered.date < columns && remembered.amount < columns
                && remembered.description < columns && remembered.note < columns
                && remembered.category < columns) {
            return remembered;
        }
        return new CsvImportMapping();
    }

    private boolean isImportMappingShown() {
        return mImportMappingLayout.getVisibility() == View.VISIBLE;
    }

    private boolean validateImportMapping() {
        return mImportWalletEditText.validate() && mDateColumnEditText.validate()
                && mAmountColumnEditText.validate() && mDateFormatEditText.validate();
    }

    private void updateImportMappingFields() {
        mDateColumnEditText.setText(getColumnName(mImportMapping.date));
        mAmountColumnEditText.setText(getColumnName(mImportMapping.amount));
        mDescriptionColumnEditText.setText(getColumnName(mImportMapping.description));
        mNoteColumnEditText.setText(getColumnName(mImportMapping.note));
        mCategoryColumnEditText.setText(getColumnName(mImportMapping.category));
        mDateFormatEditText.setText(mImportMapping.datePattern);
        mDecimalSeparatorEditText.setText(mImportMapping.decimalComma
                ? R.string.csv_import_decimal_comma : R.string.csv_import_decimal_dot);
    }

    /** The header cell of a column, or its position when the cell is blank and would show nothing. */
    private String getColumnName(int column) {
        if (column == CsvImportMapping.NONE) {
            return null;
        }
        String name = mImportHeader.cells[column].trim();
        return name.isEmpty() ? getString(R.string.csv_import_column_unnamed, column + 1) : name;
    }

    private void showColumnPicker(MaterialEditText field, boolean optional, int current, IntConsumer onPicked) {
        int offset = optional ? 1 : 0;
        String[] items = new String[mImportHeader.cells.length + offset];
        if (optional) {
            items[0] = getString(R.string.csv_import_column_none);
        }
        for (int i = 0; i < mImportHeader.cells.length; i++) {
            items[i + offset] = getColumnName(i);
        }
        showChoicePicker(field, items, current + offset, which -> onPicked.accept(which - offset));
    }

    private void showChoicePicker(MaterialEditText field, String[] items, int checked, IntConsumer onPicked) {
        ThemedDialog.buildMaterialDialog(this)
                .setTitle(field.getHint())
                .setSingleChoiceItems(items, checked, (dialog, which) -> {
                    onPicked.accept(which);
                    updateImportMappingFields();
                    dialog.dismiss();
                })
                .show();
    }

    private void addRequiredValidator(MaterialEditText field, int errorRes, BooleanSupplier isValid) {
        field.addValidator(new Validator() {

            @NonNull
            @Override
            public String getErrorMessage() {
                return getString(errorRes);
            }

            @Override
            public boolean isValid(@NonNull CharSequence charSequence) {
                return isValid.getAsBoolean();
            }

            @Override
            public boolean autoValidate() {
                return false;
            }

        });
    }

    private void onExportFolderSelected(Uri uri) {
        // the tree grant is valid for this process, which covers the export that follows
        mExportFolderUri = uri;
        DocumentFile folder = DocumentFile.fromTreeUri(this, uri);
        mExportFolderEditText.setText(folder != null ? folder.getName() : null);
    }

    private File getExportCacheDir() {
        File directory = new File(getCacheDir(), "export");
        if (!directory.exists()) {
            directory.mkdirs();
        }
        return directory;
    }

    private void saveExportToSelectedFolder(Uri sourceUri, String type) throws IOException {
        if (mExportFolderUri == null || sourceUri == null) {
            throw new IOException("missing export destination");
        }
        DocumentFile folder = DocumentFile.fromTreeUri(this, mExportFolderUri);
        if (folder == null) {
            throw new IOException("unable to open the destination folder");
        }
        String name = queryDisplayName(sourceUri);
        if (TextUtils.isEmpty(name)) {
            name = sourceUri.getLastPathSegment();
        }
        // the display name already carries the right extension, so use a neutral mime
        // type to keep the provider from rewriting the file name
        DocumentFile target = folder.createFile("application/octet-stream", name);
        if (target == null) {
            throw new IOException("unable to create the file in the destination folder");
        }
        try (InputStream input = getContentResolver().openInputStream(sourceUri);
             OutputStream output = getContentResolver().openOutputStream(target.getUri())) {
            if (input == null || output == null) {
                throw new IOException("unable to copy the export to the destination");
            }
            copyStream(input, output);
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    return cursor.getString(index);
                }
            }
        }
        return null;
    }

    // data files are small, so the copy runs synchronously on the calling thread
    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        output.flush();
    }

    @Override
    protected boolean isFloatingActionButtonEnabled() {
        // the floating action button is not required here
        return false;
    }

    @Override
    public void onFormatChanged(String tag, DataFormat format) {
        if (format != null) {
            switch (format) {
                case CSV:
                    mImportFormatEditText.setText(R.string.hint_data_format_csv);
                    mExportFormatEditText.setText(R.string.hint_data_format_csv);
                    if (mMode == MODE_EXPORT) {
                        mExportColumnsEditText.setVisibility(View.VISIBLE);
                        mExportCsvNoTransfersTextView.setVisibility(View.VISIBLE);
                    }
                    break;
                case XLS:
                    mImportFormatEditText.setText(R.string.hint_data_format_xls);
                    mExportFormatEditText.setText(R.string.hint_data_format_xls);
                    if (mMode == MODE_EXPORT) {
                        mExportColumnsEditText.setVisibility(View.VISIBLE);
                        mExportCsvNoTransfersTextView.setVisibility(View.GONE);
                    }
                    break;
                case PDF:
                    mImportFormatEditText.setText(R.string.hint_data_format_pdf);
                    mExportFormatEditText.setText(R.string.hint_data_format_pdf);
                    if (mMode == MODE_EXPORT) {
                        mExportColumnsEditText.setVisibility(View.VISIBLE);
                        mExportCsvNoTransfersTextView.setVisibility(View.GONE);
                    }
                    break;
            }
        } else {
            mImportFormatEditText.setText(null);
            mExportFormatEditText.setText(null);
            mExportColumnsEditText.setVisibility(View.GONE);
            mExportCsvNoTransfersTextView.setVisibility(View.GONE);
        }
        onFormatOrWalletChanged();
    }

    @Override
    public void onDateTimeChanged(String tag, Date date) {
        switch (tag) {
            case TAG_START_DATE_TIME_PICKER:
                if (date != null) {
                    DateFormatter.applyDate(mStartDateEditText, date);
                } else {
                    mStartDateEditText.setText(null);
                }
                break;
            case TAG_END_DATE_TIME_PICKER:
                if (date != null) {
                    DateFormatter.applyDate(mEndDateEditText, date);
                } else {
                    mEndDateEditText.setText(null);
                }
                break;
        }
    }

    @Override
    public void onWalletChanged(String tag, Wallet wallet) {
        mImportWalletEditText.setText(wallet != null ? wallet.getName() : null);
    }

    @Override
    public void onWalletListChanged(String tag, Wallet[] wallets) {
        if (wallets != null && wallets.length > 0) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < wallets.length; i++) {
                if (i != 0) {
                    builder.append(", ");
                }
                builder.append(wallets[i].getName());
            }
            mWalletsEditText.setText(builder);
        } else {
            mWalletsEditText.setText(null);
        }
        onFormatOrWalletChanged();
    }

    private void onFormatOrWalletChanged() {
        if (mMode == MODE_EXPORT) {
            if (mWalletPicker.isSelected()) {
                Wallet[] wallets = mWalletPicker.getCurrentWallets();
                if (wallets != null && wallets.length > 1) {
                    DataFormat dataFormat = mDataFormatPicker.getCurrentFormat();
                    if (dataFormat != null) {
                        switch (dataFormat) {
                            case CSV:
                                mUniqueWalletCheckbox.setVisibility(View.GONE);
                                break;
                            case XLS:
                            case PDF:
                                mUniqueWalletCheckbox.setVisibility(View.VISIBLE);
                                break;
                        }
                        return;
                    }
                }
            }
        }
        mUniqueWalletCheckbox.setVisibility(View.GONE);
    }

    @Override
    public void onExportColumnsChanged(String tag, String[] columns) {
        if (columns != null && columns.length > 0) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < columns.length; i++) {
                if (i != 0) {
                    builder.append(", ");
                }
                builder.append(columns[i]);
            }
            mExportColumnsEditText.setText(builder);
        } else {
            mExportColumnsEditText.setText(null);
        }
    }

    private BroadcastReceiver mLocalBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, final Intent intent) {
            // another import or export screen can be alive underneath and must not react to this one's work
            if (!mTokens.contains(intent.getStringExtra(ImportExportIntentService.TOKEN))) {
                return;
            }
            String action = intent.getAction();
            if (action != null) {
                switch (action) {
                    case LocalAction.ACTION_IMPORT_SERVICE_STARTED:
                        if (mProgressDialog == null) {
                            mProgressDialog = GenericProgressDialog.newInstance(R.string.title_data_importing, R.string.message_data_import_running, true);
                        }
                        mProgressDialog.show(getSupportFragmentManager(), TAG_PROGRESS_DIALOG);
                        break;
                    case LocalAction.ACTION_IMPORT_SERVICE_FINISHED:
                        if (mProgressDialog != null) {
                            mProgressDialog.dismissAllowingStateLoss();
                            mProgressDialog = null;
                        }
                        // An amount a file carries more precisely than its currency can hold is
                        // rounded on the way in, so the screen that says the import worked says
                        // that too rather than leaving the user to find it in the ledger.
                        int roundedAmounts = intent.getIntExtra(ImportExportIntentService.ROUNDED_AMOUNTS, 0);
                        String message = roundedAmounts > 0
                                ? getString(R.string.message_data_import_success_rounded, roundedAmounts)
                                : getString(R.string.message_data_import_success);
                        int alreadySavedRows = intent.getIntExtra(ImportExportIntentService.ALREADY_SAVED_ROWS, 0);
                        if (alreadySavedRows > 0) {
                            message += " " + getString(R.string.message_data_import_already_saved, alreadySavedRows);
                        }
                        ThemedDialog.buildMaterialDialog(ImportExportActivity.this)
                                .setTitle(R.string.title_success)
                                .setMessage(message)
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        break;
                    case LocalAction.ACTION_IMPORT_SERVICE_FAILED:
                        if (mProgressDialog != null) {
                            mProgressDialog.dismissAllowingStateLoss();
                            mProgressDialog = null;
                        }
                        Exception exception = (Exception) intent.getSerializableExtra(ImportExportIntentService.EXCEPTION);
                        ThemedDialog.buildMaterialDialog(ImportExportActivity.this)
                                .setTitle(R.string.title_failed)
                                .setMessage(getString(R.string.message_data_import_failed, exception.getMessage()))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        break;
                    case LocalAction.ACTION_EXPORT_SERVICE_STARTED:
                        if (mProgressDialog == null) {
                            mProgressDialog = GenericProgressDialog.newInstance(R.string.title_data_exporting, R.string.message_data_export_running, true);
                        }
                        mProgressDialog.show(getSupportFragmentManager(), TAG_PROGRESS_DIALOG);
                        break;
                    case LocalAction.ACTION_EXPORT_SERVICE_FINISHED:
                        if (mProgressDialog != null) {
                            mProgressDialog.dismissAllowingStateLoss();
                            mProgressDialog = null;
                        }
                        final Uri resultUri = intent.getParcelableExtra(ImportExportIntentService.RESULT_FILE_URI);
                        final String resultType = intent.getStringExtra(ImportExportIntentService.RESULT_FILE_TYPE);
                        try {
                            saveExportToSelectedFolder(resultUri, resultType);
                        } catch (IOException e) {
                            ThemedDialog.buildMaterialDialog(ImportExportActivity.this)
                                    .setTitle(R.string.title_failed)
                                    .setMessage(getString(R.string.message_data_export_failed, e.getMessage()))
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show();
                            break;
                        }
                        ThemedDialog.buildMaterialDialog(ImportExportActivity.this)
                                .setTitle(R.string.title_success)
                                .setMessage(R.string.message_data_export_success)
                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        if (resultUri != null) {
                                            Intent target = new Intent(Intent.ACTION_VIEW);
                                            target.setDataAndType(resultUri, resultType);
                                            target.setFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                                            target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                                            Intent chooser = Intent.createChooser(target, getString(R.string.action_open));
                                            try {
                                                startActivity(chooser);
                                            } catch (ActivityNotFoundException ignore) {
                                                // no activity to handle this type of file
                                            }
                                        }
                                    }

                                })
                                .setNegativeButton(android.R.string.cancel, null)
                                .show();
                        break;
                    case LocalAction.ACTION_EXPORT_SERVICE_FAILED:
                        if (mProgressDialog != null) {
                            mProgressDialog.dismissAllowingStateLoss();
                            mProgressDialog = null;
                        }
                        exception = (Exception) intent.getSerializableExtra(ImportExportIntentService.EXCEPTION);
                        ThemedDialog.buildMaterialDialog(ImportExportActivity.this)
                                .setTitle(R.string.title_failed)
                                .setMessage(getString(R.string.message_data_export_failed, exception.getMessage()))
                                .setPositiveButton(android.R.string.ok, null)
                                .show();
                        break;
                }
            }
        }

    };
}
