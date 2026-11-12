package com.oriondev.moneywallet.service

import android.app.IntentService
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.FileProvider

import com.oriondev.moneywallet.broadcast.LocalAction
import com.oriondev.moneywallet.model.DataFormat
import com.oriondev.moneywallet.model.Wallet
import com.oriondev.moneywallet.storage.database.Contract
import com.oriondev.moneywallet.storage.database.DataContentProvider
import com.oriondev.moneywallet.storage.database.data.AbstractDataExporter
import com.oriondev.moneywallet.storage.database.data.AbstractDataImporter
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataExporter
import com.oriondev.moneywallet.storage.database.data.csv.CSVDataImporter
import com.oriondev.moneywallet.storage.database.data.csv.CsvImportMapping
import com.oriondev.moneywallet.storage.database.data.pdf.PDFDataExporter
import com.oriondev.moneywallet.storage.database.data.xls.XLSDataExporter
import com.oriondev.moneywallet.utils.DateUtils

import java.io.File
import java.io.IOException
import java.util.Date

/**
 * Created by andrea on 20/12/18.
 */
class ImportExportIntentService : IntentService {

    companion object {

        const val MODE = "ImportExportIntentService::Arguments::Mode"
        const val FORMAT = "ImportExportIntentService::Arguments::Format"
        const val START_DATE = "ImportExportIntentService::Arguments::StartDate"
        const val END_DATE = "ImportExportIntentService::Arguments::EndDate"
        const val WALLETS = "ImportExportIntentService::Arguments::Wallets"
        const val FOLDER = "ImportExportIntentService::Arguments::Folder"
        const val FILE = "ImportExportIntentService::Arguments::File"
        const val UNIQUE_WALLET = "ImportExportIntentService::Arguments::UniqueWallet"
        const val OPTIONAL_COLUMNS = "ImportExportIntentService::Arguments::OptionalColumns"
        const val MAPPING = "ImportExportIntentService::Arguments::Mapping"
        const val TOKEN = "ImportExportIntentService::Arguments::Token"

        const val RESULT_FILE_URI = "ImportExportIntentService::Results::FileUri"
        const val RESULT_FILE_TYPE = "ImportExportIntentService::Results::FileType"
        const val EXCEPTION = "ImportExportIntentService::Results::Exception"
        const val ROUNDED_AMOUNTS = "ImportExportIntentService::Results::RoundedAmounts"
        const val ALREADY_SAVED_ROWS = "ImportExportIntentService::Results::AlreadySavedRows"

        const val MODE_EXPORT = 0
        const val MODE_IMPORT = 1

        /**
         * Export the transactions into a new file inside the folder and return the closed exporter,
         * which holds the output file and its type. A null wallet array exports every wallet.
         * Runs on the calling thread.
         */
        @JvmStatic
        @Throws(IOException::class)
        fun exportTransactions(context: Context, dataFormat: DataFormat,
                               folder: File, startDate: Date?,
                               endDate: Date?, wallets: Array<Wallet?>?,
                               uniqueWallet: Boolean, optionalColumns: Array<String>?): AbstractDataExporter {
            // initialize the correct data exporter
            val dataExporter = getDataExporter(context, dataFormat, folder)
            val contentResolver: ContentResolver = context.contentResolver
            val uri: Uri = DataContentProvider.CONTENT_CATEGORY_LINES
            // initialize the selection builder with common variables
            val selectionBuilder = StringBuilder()
            val selectionArguments: ArrayList<String> = ArrayList()
            // append rule to limit to the end date or to the current date
            selectionBuilder.append("DATE (" + Contract.Transaction.DATE + ") <= DATE(?)")
            selectionArguments.add(DateUtils.getSQLDateString(getFixedEndDate(endDate)))
            // if provided, apply a rule to the start date
            if (startDate != null) {
                selectionBuilder.append(" AND DATE (" + Contract.Transaction.DATE + ") >= DATE(?)")
                selectionArguments.add(DateUtils.getSQLDateString(startDate))
            }
            if (dataFormat == DataFormat.CSV) {
                selectionBuilder.append(" AND " + CSVDataExporter.ROWS_SELECTION)
                selectionArguments.addAll(CSVDataExporter.ROWS_SELECTION_ARGS)
            }
            val sortOrder = Contract.Transaction.DATE + " DESC"
            // check if we should create a unique wallet or if we can export each wallet
            // in a separate way
            val multiWallet = wallets != null && wallets.size > 1 && dataExporter.isMultiWalletSupported() && !uniqueWallet
            val columns = dataExporter.getColumns(!multiWallet, optionalColumns)
            // before starting with the export logic, check if the exporter should
            // store the people names into his internal cache to speedup the procedure
            if (dataExporter.shouldLoadPeople()) {
                val cursor: Cursor? = contentResolver.query(DataContentProvider.CONTENT_PEOPLE, null, null, null, null)
                if (cursor != null) {
                    dataExporter.cachePeople(cursor)
                    cursor.close()
                }
            }
            // handle the export logic differently
            if (multiWallet) {
                // execute a query for each wallet: we should clone the original builder
                // to avoid mistakes during each successive query
                for (wallet in wallets) {
                    val selection = selectionBuilder.toString() + " AND " + Contract.Transaction.WALLET_ID + " = ?"
                    val arguments = selectionArguments.toArray(arrayOfNulls<String>(selectionArguments.size + 1))
                    arguments[arguments.size - 1] = wallet!!.getId().toString()
                    val cursor: Cursor? = contentResolver.query(uri, null, selection, arguments, sortOrder)
                    if (cursor != null) {
                        dataExporter.exportData(cursor, columns, wallet)
                        cursor.close()
                    }
                }
            } else {
                // execute only a large query: we can modify the original builder here
                if (wallets != null) {
                    selectionBuilder.append(" AND (")
                    for (i in wallets.indices) {
                        if (i != 0) {
                            selectionBuilder.append(" OR ")
                        }
                        selectionBuilder.append(Contract.Transaction.WALLET_ID + " = ?")
                        selectionArguments.add(wallets[i]!!.getId().toString())
                    }
                    selectionBuilder.append(")")
                }
                val cursor: Cursor? = contentResolver.query(uri, null, selectionBuilder.toString(), selectionArguments.toArray(arrayOfNulls<String>(selectionArguments.size)), sortOrder)
                if (cursor != null) {
                    dataExporter.exportData(cursor, columns, *(wallets ?: arrayOf()))
                    cursor.close()
                }
            }
            // close the exporter to flush and close all the open streams
            dataExporter.close()
            return dataExporter
        }

        @Throws(IOException::class)
        private fun getDataExporter(context: Context, dataFormat: DataFormat, folder: File): AbstractDataExporter {
            when (dataFormat) {
                DataFormat.CSV -> {
                    return CSVDataExporter(context, folder)
                }
                DataFormat.XLS -> {
                    return XLSDataExporter(context, folder)
                }
                DataFormat.PDF -> {
                    return PDFDataExporter(context, folder)
                }
                else -> {
                    throw RuntimeException("DataFormat not supported")
                }
            }
        }

        private fun getFixedEndDate(endDate: Date?): Date {
            val now = Date()
            if (endDate != null) {
                val minMillis = Math.min(now.time, endDate.time)
                return Date(minMillis)
            }
            return now
        }
    }

    private var mReporter: TaskReporter? = null
    private var mToken: String? = null

    constructor() : super("ImportExportIntentService")

    override fun onHandleIntent(intent: Intent?) {
        if (intent != null) {
            mReporter = TaskReporter(this)
            mToken = intent.getStringExtra(TOKEN)
            val mode = intent.getIntExtra(MODE, MODE_EXPORT)
            when (mode) {
                MODE_EXPORT -> {
                    handleExport(intent)
                }
                MODE_IMPORT -> {
                    handleImport(intent)
                }
            }
        }
    }

    private fun handleImport(intent: Intent) {
        notifyTaskStarted(LocalAction.ACTION_IMPORT_SERVICE_STARTED)
        try {
            // extract parameters from the intent
            val dataFormat = intent.getSerializableExtra(FORMAT) as DataFormat?
            val file = intent.getSerializableExtra(FILE) as File?
            val mapping = intent.getSerializableExtra(MAPPING) as CsvImportMapping?
            if (dataFormat == null) {
                throw IllegalArgumentException("parameter is null [FORMAT]")
            }
            if (file == null || !file.exists()) {
                throw IllegalArgumentException("parameter is null or not a file [FILE]")
            }
            // initialize the correct data importer
            val dataImporter = getDataImporter(dataFormat, file, mapping)
            val roundedAmounts: Int
            val alreadySavedRows: Int
            try {
                dataImporter.importData()
                roundedAmounts = dataImporter.getRoundedAmounts()
                alreadySavedRows = dataImporter.getAlreadySavedRows()
            } finally {
                dataImporter.close()
            }
            notifyTaskFinished(LocalAction.ACTION_IMPORT_SERVICE_FINISHED, roundedAmounts, alreadySavedRows)
        } catch (e: Exception) {
            notifyTaskFailed(LocalAction.ACTION_IMPORT_SERVICE_FAILED, e)
        }
    }

    private fun handleExport(intent: Intent) {
        notifyTaskStarted(LocalAction.ACTION_EXPORT_SERVICE_STARTED)
        try {
            // extract parameters from the intent
            val dataFormat = intent.getSerializableExtra(FORMAT) as DataFormat?
            val startDate = intent.getSerializableExtra(START_DATE) as Date?
            val endDate = intent.getSerializableExtra(END_DATE) as Date?
            val wallets = getWalletList(intent, WALLETS)
            val folder = intent.getSerializableExtra(FOLDER) as File?
            val uniqueWallet = intent.getBooleanExtra(UNIQUE_WALLET, false)
            val optionalColumns: Array<String>? = intent.getStringArrayExtra(OPTIONAL_COLUMNS)
            // check necessary parameters
            if (dataFormat == null) {
                throw IllegalArgumentException("parameter is null [FORMAT]")
            }
            if (wallets == null || wallets.size == 0) {
                throw IllegalArgumentException("parameter is null or empty [WALLETS]")
            }
            if (folder == null || !folder.isDirectory()) {
                throw IllegalArgumentException("parameter is null or not a directory [FOLDER]")
            }
            val dataExporter = exportTransactions(this, dataFormat, folder, startDate, endDate, wallets, uniqueWallet, optionalColumns)
            // if no exception has been thrown so far, we can ask the exporter
            // for the output file: we can pass the uri of this file inside the intent
            // Uri resultUri = Uri.fromFile(dataExporter.getOutputFile());
            val authority = packageName + ".storage.file"
            val outputFile = dataExporter.getOutputFile()
            val resultUri = FileProvider.getUriForFile(this, authority, outputFile)
            val resultType = dataExporter.getResultType()
            // send a successful intent to inform the receivers that the operation succeed
            notifyTaskFinished(LocalAction.ACTION_EXPORT_SERVICE_FINISHED, resultUri, resultType)
        } catch (e: Exception) {
            notifyTaskFailed(LocalAction.ACTION_EXPORT_SERVICE_FAILED, e)
        }
    }

    private fun getWalletList(intent: Intent, key: String): Array<Wallet?>? {
        val parcelableArray: Array<Parcelable>? = intent.getParcelableArrayExtra(key)
        val wallets = arrayOfNulls<Wallet>(parcelableArray!!.size)
        for (i in parcelableArray.indices) {
            wallets[i] = parcelableArray[i] as Wallet?
        }
        return wallets
    }

    private fun notifyTaskStarted(action: String) {
        val extras = Bundle()
        extras.putString(TOKEN, mToken)
        mReporter!!.broadcast(action, extras)
    }

    private fun notifyTaskFinished(action: String, roundedAmounts: Int, alreadySavedRows: Int) {
        val extras = Bundle()
        extras.putInt(ROUNDED_AMOUNTS, roundedAmounts)
        extras.putInt(ALREADY_SAVED_ROWS, alreadySavedRows)
        extras.putString(TOKEN, mToken)
        mReporter!!.broadcast(action, extras)
    }

    private fun notifyTaskFinished(action: String, resultUri: Uri?, resultType: String?) {
        val extras = Bundle()
        extras.putParcelable(RESULT_FILE_URI, resultUri)
        extras.putString(RESULT_FILE_TYPE, resultType)
        extras.putString(TOKEN, mToken)
        mReporter!!.broadcast(action, extras)
    }

    private fun notifyTaskFailed(action: String, exception: Exception) {
        val extras = Bundle()
        extras.putSerializable(EXCEPTION, exception)
        extras.putString(TOKEN, mToken)
        mReporter!!.broadcast(action, extras)
    }

    @Throws(IOException::class)
    private fun getDataImporter(dataFormat: DataFormat, file: File, mapping: CsvImportMapping?): AbstractDataImporter {
        when (dataFormat) {
            DataFormat.CSV -> {
                return CSVDataImporter(this, file, mapping)
            }
            else -> {
                throw RuntimeException("DataFormat not supported")
            }
        }
    }
}
