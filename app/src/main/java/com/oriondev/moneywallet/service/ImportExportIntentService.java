package com.oriondev.moneywallet.service;

import android.app.IntentService;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import com.oriondev.moneywallet.broadcast.LocalAction;
import com.oriondev.moneywallet.model.DataFormat;
import com.oriondev.moneywallet.model.Wallet;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter;
import com.oriondev.moneywallet.storage.database.data.AbstractDataImporter;
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataExporter;
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataImporter;
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping;
import com.oriondev.moneywallet.storage.database.data.pdf.PDFDataExporter;
import com.oriondev.moneywallet.storage.database.data.xls.XLSDataExporter;
import com.oriondev.moneywallet.utils.DateUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by andrea on 20/12/18.
 */
public class ImportExportIntentService extends IntentService {

    public static final String MODE = "ImportExportIntentService::Arguments::Mode";
    public static final String FORMAT = "ImportExportIntentService::Arguments::Format";
    public static final String START_DATE = "ImportExportIntentService::Arguments::StartDate";
    public static final String END_DATE = "ImportExportIntentService::Arguments::EndDate";
    public static final String WALLETS = "ImportExportIntentService::Arguments::Wallets";
    public static final String FOLDER = "ImportExportIntentService::Arguments::Folder";
    public static final String FILE = "ImportExportIntentService::Arguments::File";
    public static final String UNIQUE_WALLET = "ImportExportIntentService::Arguments::UniqueWallet";
    public static final String OPTIONAL_COLUMNS = "ImportExportIntentService::Arguments::OptionalColumns";
    public static final String MAPPING = "ImportExportIntentService::Arguments::Mapping";

    public static final String RESULT_FILE_URI = "ImportExportIntentService::Results::FileUri";
    public static final String RESULT_FILE_TYPE = "ImportExportIntentService::Results::FileType";
    public static final String EXCEPTION = "ImportExportIntentService::Results::Exception";
    public static final String ROUNDED_AMOUNTS = "ImportExportIntentService::Results::RoundedAmounts";

    public static final int MODE_EXPORT = 0;
    public static final int MODE_IMPORT = 1;

    private TaskReporter mReporter;

    public ImportExportIntentService() {
        super("ImportExportIntentService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent != null) {
            mReporter = new TaskReporter(this);
            int mode = intent.getIntExtra(MODE, MODE_EXPORT);
            switch (mode) {
                case MODE_EXPORT:
                    handleExport(intent);
                    break;
                case MODE_IMPORT:
                    handleImport(intent);
                    break;
            }
        }
    }

    private void handleImport(@NonNull Intent intent) {
        notifyTaskStarted(LocalAction.ACTION_IMPORT_SERVICE_STARTED);
        try {
            // extract parameters from the intent
            DataFormat dataFormat = (DataFormat) intent.getSerializableExtra(FORMAT);
            File file = (File) intent.getSerializableExtra(FILE);
            CsvImportMapping mapping = (CsvImportMapping) intent.getSerializableExtra(MAPPING);
            if (dataFormat == null) {
                throw new IllegalArgumentException("parameter is null [FORMAT]");
            }
            if (file == null || !file.exists()) {
                throw new IllegalArgumentException("parameter is null or not a file [FILE]");
            }
            // initialize the correct data importer
            AbstractDataImporter dataImporter = getDataImporter(dataFormat, file, mapping);
            int roundedAmounts;
            try {
                dataImporter.importData();
                roundedAmounts = dataImporter.getRoundedAmounts();
            } finally {
                dataImporter.close();
            }
            notifyTaskFinished(LocalAction.ACTION_IMPORT_SERVICE_FINISHED, roundedAmounts);
        } catch (Exception e) {
            notifyTaskFailed(LocalAction.ACTION_IMPORT_SERVICE_FAILED, e);
        }
    }

    private void handleExport(@NonNull Intent intent) {
        notifyTaskStarted(LocalAction.ACTION_EXPORT_SERVICE_STARTED);
        try {
            // extract parameters from the intent
            DataFormat dataFormat = (DataFormat) intent.getSerializableExtra(FORMAT);
            Date startDate = (Date) intent.getSerializableExtra(START_DATE);
            Date endDate = (Date) intent.getSerializableExtra(END_DATE);
            Wallet[] wallets = getWalletList(intent, WALLETS);
            File folder = (File) intent.getSerializableExtra(FOLDER);
            boolean uniqueWallet = intent.getBooleanExtra(UNIQUE_WALLET, false);
            String[] optionalColumns = intent.getStringArrayExtra(OPTIONAL_COLUMNS);
            // check necessary parameters
            if (dataFormat == null) {
                throw new IllegalArgumentException("parameter is null [FORMAT]");
            }
            if (wallets == null || wallets.length == 0) {
                throw new IllegalArgumentException("parameter is null or empty [WALLETS]");
            }
            if (folder == null || !folder.isDirectory()) {
                throw new IllegalArgumentException("parameter is null or not a directory [FOLDER]");
            }
            AbstractDataExporter dataExporter = exportTransactions(this, dataFormat, folder, startDate, endDate, wallets, uniqueWallet, optionalColumns);
            // if no exception has been thrown so far, we can ask the exporter
            // for the output file: we can pass the uri of this file inside the intent
            // Uri resultUri = Uri.fromFile(dataExporter.getOutputFile());
            String authority = getPackageName() + ".storage.file";
            File outputFile = dataExporter.getOutputFile();
            Uri resultUri = FileProvider.getUriForFile(this, authority, outputFile);
            String resultType = dataExporter.getResultType();
            // send a successful intent to inform the receivers that the operation succeed
            notifyTaskFinished(LocalAction.ACTION_EXPORT_SERVICE_FINISHED, resultUri, resultType);
        } catch (Exception e) {
            notifyTaskFailed(LocalAction.ACTION_EXPORT_SERVICE_FAILED, e);
        }
    }

    /**
     * Export the transactions into a new file inside the folder and return the closed exporter,
     * which holds the output file and its type. A null wallet array exports every wallet.
     * Runs on the calling thread.
     */
    static AbstractDataExporter exportTransactions(@NonNull Context context, @NonNull DataFormat dataFormat,
                                                   @NonNull File folder, @Nullable Date startDate,
                                                   @Nullable Date endDate, @Nullable Wallet[] wallets,
                                                   boolean uniqueWallet, @Nullable String[] optionalColumns)
            throws IOException {
        // initialize the correct data exporter
        AbstractDataExporter dataExporter = getDataExporter(context, dataFormat, folder);
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = DataContentProvider.CONTENT_TRANSACTIONS;
        // initialize the selection builder with common variables
        StringBuilder selectionBuilder = new StringBuilder();
        List<String> selectionArguments = new ArrayList<>();
        // append rule to limit to the end date or to the current date
        selectionBuilder.append("DATE (" + Contract.Transaction.DATE + ") <= DATE(?)");
        selectionArguments.add(DateUtils.getSQLDateString(getFixedEndDate(endDate)));
        // if provided, apply a rule to the start date
        if (startDate != null) {
            selectionBuilder.append(" AND DATE (" + Contract.Transaction.DATE + ") >= DATE(?)");
            selectionArguments.add(DateUtils.getSQLDateString(startDate));
        }
        if (dataFormat == DataFormat.CSV) {
            // CSV is the only format that can be imported back, and it carries nothing that
            // pairs the two legs of a transfer, so importing them recreates each leg as an
            // ordinary transaction. The fee stays: it is one expense, not half of anything.
            selectionBuilder.append(" AND (" + Contract.Transaction.TYPE + " != ? OR " +
                    Contract.Transaction.CATEGORY_TAG + " = ?)");
            selectionArguments.add(String.valueOf(Contract.TransactionType.TRANSFER));
            selectionArguments.add(Contract.CategoryTag.TRANSFER_TAX);
        }
        String sortOrder = Contract.Transaction.DATE + " DESC";
        // check if we should create a unique wallet or if we can export each wallet
        // in a separate way
        boolean multiWallet = wallets != null && wallets.length > 1 && dataExporter.isMultiWalletSupported() && !uniqueWallet;
        String[] columns = dataExporter.getColumns(!multiWallet, optionalColumns);
        // before starting with the export logic, check if the exporter should
        // store the people names into his internal cache to speedup the procedure
        if (dataExporter.shouldLoadPeople()) {
            Cursor cursor = contentResolver.query(DataContentProvider.CONTENT_PEOPLE, null, null, null, null);
            if (cursor != null) {
                dataExporter.cachePeople(cursor);
                cursor.close();
            }
        }
        // handle the export logic differently
        if (multiWallet) {
            // execute a query for each wallet: we should clone the original builder
            // to avoid mistakes during each successive query
            for (Wallet wallet : wallets) {
                String selection = selectionBuilder + " AND " + Contract.Transaction.WALLET_ID + " = ?";
                String[] arguments = selectionArguments.toArray(new String[selectionArguments.size() + 1]);
                arguments[arguments.length - 1] = String.valueOf(wallet.getId());
                Cursor cursor = contentResolver.query(uri, null, selection, arguments, sortOrder);
                if (cursor != null) {
                    dataExporter.exportData(cursor, columns, wallet);
                    cursor.close();
                }
            }
        } else {
            // execute only a large query: we can modify the original builder here
            if (wallets != null) {
                selectionBuilder.append(" AND (");
                for (int i = 0; i < wallets.length; i++) {
                    if (i != 0) {
                        selectionBuilder.append(" OR ");
                    }
                    selectionBuilder.append(Contract.Transaction.WALLET_ID + " = ?");
                    selectionArguments.add(String.valueOf(wallets[i].getId()));
                }
                selectionBuilder.append(")");
            }
            Cursor cursor = contentResolver.query(uri, null, selectionBuilder.toString(), selectionArguments.toArray(new String[selectionArguments.size()]), sortOrder);
            if (cursor != null) {
                dataExporter.exportData(cursor, columns, wallets);
                cursor.close();
            }
        }
        // close the exporter to flush and close all the open streams
        dataExporter.close();
        return dataExporter;
    }

    private Wallet[] getWalletList(Intent intent, String key) {
        Parcelable[] parcelableArray = intent.getParcelableArrayExtra(key);
        Wallet[] wallets = new Wallet[parcelableArray.length];
        for (int i = 0; i < parcelableArray.length; i++) {
            wallets[i] = (Wallet) parcelableArray[i];
        }
        return wallets;
    }

    private void notifyTaskStarted(String action) {
        mReporter.broadcast(action);
    }

    private void notifyTaskFinished(String action, int roundedAmounts) {
        Bundle extras = new Bundle();
        extras.putInt(ROUNDED_AMOUNTS, roundedAmounts);
        mReporter.broadcast(action, extras);
    }

    private void notifyTaskFinished(String action, Uri resultUri, String resultType) {
        Bundle extras = new Bundle();
        extras.putParcelable(RESULT_FILE_URI, resultUri);
        extras.putString(RESULT_FILE_TYPE, resultType);
        mReporter.broadcast(action, extras);
    }

    private void notifyTaskFailed(String action, Exception exception) {
        Bundle extras = new Bundle();
        extras.putSerializable(EXCEPTION, exception);
        mReporter.broadcast(action, extras);
    }

    private AbstractDataImporter getDataImporter(DataFormat dataFormat, File file, @Nullable CsvImportMapping mapping) throws IOException {
        switch (dataFormat) {
            case CSV:
                return new CSVDataImporter(this, file, mapping);
            default:
                throw new RuntimeException("DataFormat not supported");
        }
    }

    private static AbstractDataExporter getDataExporter(Context context, DataFormat dataFormat, File folder) throws IOException {
        switch (dataFormat) {
            case CSV:
                return new CSVDataExporter(context, folder);
            case XLS:
                return new XLSDataExporter(context, folder);
            case PDF:
                return new PDFDataExporter(context, folder);
            default:
                throw new RuntimeException("DataFormat not supported");
        }
    }

    private static Date getFixedEndDate(Date endDate) {
        Date now = new Date();
        if (endDate != null) {
            long minMillis = Math.min(now.getTime(), endDate.getTime());
            return new Date(minMillis);
        }
        return now;
    }
}