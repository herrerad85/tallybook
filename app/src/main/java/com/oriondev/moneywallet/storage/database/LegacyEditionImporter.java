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

package com.oriondev.moneywallet.storage.database;

import android.content.ContentResolver;
import android.content.Context;

import com.oriondev.moneywallet.model.Group;
import com.oriondev.moneywallet.storage.database.legacy.LegacyDatabaseImporter;
import com.oriondev.moneywallet.storage.database.legacy.LegacyUserPreferences;
import com.oriondev.moneywallet.storage.preference.PreferenceManager;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

/**
 * Created by andrea on 10/08/18.
 */
public class LegacyEditionImporter {

    public static final String DATABASE_NAME = "MoneyWallet";

    private final static String LEGACY_IMAGE_FOLDER = "images";
    private final static String ATTACHMENT_FOLDER = "attachments";
    
    private final Context mContext;
    private final LegacyDatabaseImporter mDatabaseImporter;

    public LegacyEditionImporter(Context context) throws ImportException {
        mContext = context;
        File databaseFile = context.getDatabasePath(DATABASE_NAME);
        mDatabaseImporter = new LegacyDatabaseImporter(databaseFile);
    }

    /**
     * The rows go in inside one transaction, so a failure that reaches this method leaves the
     * ledger as it was instead of half merged. There is no copy of it to fall back on here, since
     * this one merges into the database in place and never moves it aside.
     *
     * What that does not cover, because it is worth knowing before trusting the sentence above.
     * A row the current schema will not take throws out of the insert, checked on an emulator and
     * not reasoned about, and every insert in LegacyDatabaseImporter sits in its own catch that
     * drops it. So that row is skipped, the upgrade carries on and reports success, and only a
     * failure that gets past those catches reaches the rollback. Moving the attachment files is a
     * second step that runs after this one has committed, so its failures cannot be undone either;
     * the attachment rows are in here with everything else.
     *
     * The legacy file is closed and deleted after the transaction and not inside it. Deleting it
     * is the one step here that cannot be undone, and a commit that failed with the delete already
     * done would take the data the upgrade was reading from with it.
     */
    public void importDatabase() throws ImportException {
        ContentResolver contentResolver = mContext.getContentResolver();
        try {
            DataContentProvider.runInOneTransaction(mContext, () -> {
                importRows(contentResolver);
                return null;
            });
        } catch (ImportException | RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new ImportException(e.getMessage());
        }
        // close the database file in order to delete the file on disk
        mDatabaseImporter.close();
        mContext.deleteDatabase(DATABASE_NAME);
    }

    private void importRows(ContentResolver contentResolver) throws ImportException {
        mDatabaseImporter.importWallets(contentResolver);
        mDatabaseImporter.importCategories(contentResolver);
        mDatabaseImporter.importEvents(contentResolver);
        mDatabaseImporter.importPlaces(contentResolver);
        mDatabaseImporter.importPeople(contentResolver);
        mDatabaseImporter.importEventPeople(contentResolver);
        mDatabaseImporter.importDebts(contentResolver);
        mDatabaseImporter.importDebtPeople(contentResolver);
        mDatabaseImporter.importBudgets(contentResolver);
        mDatabaseImporter.importBudgetWallets(contentResolver);
        mDatabaseImporter.importBudgetCategories(contentResolver);
        mDatabaseImporter.importCategoryRules(contentResolver);
        mDatabaseImporter.importSavings(contentResolver);
        mDatabaseImporter.importRecurrentTransactions(contentResolver);
        mDatabaseImporter.importRecurrentTransfers(contentResolver);
        mDatabaseImporter.importTransactions(contentResolver);
        mDatabaseImporter.importTransactionPeople(contentResolver);
        mDatabaseImporter.importTransactionModels(contentResolver);
        mDatabaseImporter.importTransfers(contentResolver);
        mDatabaseImporter.importTransferPeople(contentResolver);
        mDatabaseImporter.importTransferModels(contentResolver);
        mDatabaseImporter.importAttachments(contentResolver);
        mDatabaseImporter.importTransactionAttachments(contentResolver);
        mDatabaseImporter.importTransferAttachments(contentResolver);
    }

    public void importAttachments() throws ImportException {
        File baseFolder = mContext.getExternalFilesDir(null);
        File imageFolder = new File(baseFolder, LEGACY_IMAGE_FOLDER);
        File attachmentFolder = new File(baseFolder, ATTACHMENT_FOLDER);
        if (imageFolder.exists()) {
            // iterate all the files that are placed inside this folder and,
            // for any given name, checks if the database importer has already
            // generated a unique identifier (in this case the file is a valid
            // attachment to copy)
            File[] children = imageFolder.listFiles();
            if (children != null && children.length > 0) {
                // ensure that the attachment folder exists or create it
                if (!attachmentFolder.exists()) {
                    try {
                        FileUtils.forceMkdir(attachmentFolder);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                // iterate all the files inside the folder
                ContentResolver contentResolver = mContext.getContentResolver();
                for (File child : children) {
                    String uniqueId = mDatabaseImporter.getAttachmentId(child.getName());
                    if (uniqueId != null) {
                        File attachment = new File(attachmentFolder, uniqueId);
                        try {
                            FileUtils.moveFile(child, attachment);
                        } catch (IOException e) {
                            throw new ImportException(e.getMessage());
                        }
                        mDatabaseImporter.importAttachment(contentResolver, uniqueId, attachment.length());
                    }
                }
            }
            // delete the images folder because it is no more necessary
            FileUtils.deleteQuietly(imageFolder);
        }
    }

    public void importPreferences() throws ImportException {
        // obtain all the current legacy values from the user preferences
        LegacyUserPreferences legacyUserPreferences = new LegacyUserPreferences(mContext);
        int dateFormat = legacyUserPreferences.getDateFormat();
        int colorIncomes = legacyUserPreferences.getColorIn();
        int colorExpenses = legacyUserPreferences.getColorOut();
        Group groupType = legacyUserPreferences.getGroupType();
        int firstDayOfMonth = legacyUserPreferences.getFirstDayOfMonth();
        int firstDayOfWeek = legacyUserPreferences.getFirstDayOfWeek();
        boolean reminderEnabled = legacyUserPreferences.isReminderEnabled();
        int reminderHour = legacyUserPreferences.getReminderHour();
        boolean currencyEnabled = legacyUserPreferences.isCurrencySymbolEnabled();
        boolean groupDigitsEnabled = legacyUserPreferences.isGroupDigitsEnabled();
        boolean roundDecimalsEnabled = legacyUserPreferences.isRoundDecimalsEnabled();
        // destroy the legacy shared preferences file to avoid key collisions
        legacyUserPreferences.destroy();
        // update the current PreferenceManager to restore the legacy values
        PreferenceManager.setDateFormat(dateFormat);
        PreferenceManager.setCurrentIncomeColor(colorIncomes);
        PreferenceManager.setCurrentExpenseColor(colorExpenses);
        PreferenceManager.setCurrentGroupType(groupType);
        PreferenceManager.setCurrentFirstDayOfMonth(firstDayOfMonth);
        PreferenceManager.setCurrentFirstDayOfWeek(firstDayOfWeek);
        if (reminderEnabled) {
            PreferenceManager.setCurrentDailyReminder(mContext, reminderHour);
        } else {
            PreferenceManager.setCurrentDailyReminder(mContext, PreferenceManager.DAILY_REMINDER_DISABLED);
        }
        PreferenceManager.setCurrencyEnabled(currencyEnabled);
        PreferenceManager.setGroupDigitsEnabled(groupDigitsEnabled);
        PreferenceManager.setRoundDecimalsEnabled(roundDecimalsEnabled);
        PreferenceManager.setIsFirstStartDone(true);
    }
}