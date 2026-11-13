package com.oriondev.moneywallet.ui.activity;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.View;
import android.widget.CheckBox;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;

import com.oriondev.moneywallet.R;
import com.oriondev.moneywallet.model.Attachment;
import com.oriondev.moneywallet.model.Category;
import com.oriondev.moneywallet.model.ColorIcon;
import com.oriondev.moneywallet.picker.AttachmentPicker;
import com.oriondev.moneywallet.picker.CategoryPicker;
import com.oriondev.moneywallet.picker.DateTimePicker;
import com.oriondev.moneywallet.picker.IconPicker;
import com.oriondev.moneywallet.picker.MoneyPicker;
import com.oriondev.moneywallet.service.AttachmentHandlerIntentService;
import com.oriondev.moneywallet.storage.database.Contract;
import com.oriondev.moneywallet.storage.database.DataContentProvider;
import com.oriondev.moneywallet.storage.database.TestDatabases;
import com.oriondev.moneywallet.storage.database.TransactionContentValuesBuilder;
import com.oriondev.moneywallet.storage.database.TransferContentValuesBuilder;
import com.oriondev.moneywallet.ui.view.text.MaterialEditText;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.shadows.ShadowActivity;
import org.robolectric.shadows.ShadowDialog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class NewEditItemActivityTest {

    private static final String ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"T\"}";
    private static final String DATE = "2019-04-05";
    private static final String DATE_TIME = "2019-04-05 14:30:27";

    private static final String BUDGET_MONEY_PICKER = "NewEditBudgetActivity::Tag::MoneyPicker";
    private static final String TRANSACTION_CATEGORY_PICKER = "NewEditTransactionActivity::Tag::CategoryPicker";
    private static final String TRANSACTION_MONEY_PICKER = "NewEditTransactionActivity::Tag::MoneyPicker";
    private static final String TRANSACTION_ATTACHMENT_PICKER = "NewEditTransactionActivity::Tag::AttachmentPicker";
    private static final String TRANSACTION_PART_CATEGORY_PICKER = "NewEditTransactionActivity::Tag::PartCategoryPicker";
    private static final String TRANSACTION_PART_MONEY_PICKER = "NewEditTransactionActivity::Tag::PartMoneyPicker";
    private static final String TRANSFER_ATTACHMENT_PICKER = "NewEditTransferModelActivity::Tag::AttachmentPicker";
    private static final String WALLET_ICON_PICKER = "NewEditWalletActivity::Tag::IconPicker";
    private static final String DEBT_DATE_PICKER = "NewEditDebtActivity::Tag::DatePicker";
    private static final String SAVING_MONEY_PICKER = "NewEditSavingActivity::Tag::MoneyPicker";
    private static final String REMOVED_ICON = "{\"type\":\"color\",\"color\":\"#000000\",\"name\":\"m\"}";

    private ContentResolver mResolver;
    private long mWallet;
    private long mOtherWallet;
    private long mCategory;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        TestDatabases.useFreshDatabase(context);
        mResolver = context.getContentResolver();
        mWallet = insertWallet("Cash");
        mOtherWallet = insertWallet("Bank");
        mCategory = insertCategory("Groceries");
    }

    @Test
    public void aNewPersonLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditPersonActivity.class));
    }

    @Test
    public void aNewPersonWithANoteAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditPersonActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void keepEditingLeavesANewPersonOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditPersonActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void discardClosesANewPerson() {
        assertDiscardCloses(newIntent(NewEditPersonActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void aStoredPersonLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditPersonActivity.class, insertPerson()));
    }

    @Test
    public void aStoredPersonStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(editIntent(NewEditPersonActivity.class, insertPerson()));
    }

    @Test
    public void aNameTypedAndDeletedAgainClosesOnBack() {
        try (ActivityScenario<NewEditPersonActivity> scenario =
                     ActivityScenario.launch(newIntent(NewEditPersonActivity.class))) {
            scenario.onActivity(activity -> {
                MaterialEditText name = activity.findViewById(R.id.name_edit_text);
                name.setText("Ann");
                AlertDialog dialog = askOnBack(activity);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                name.setText("");
                activity.onBackPressed();
                assertTrue("back left the editor open", activity.isFinishing());
            });
        }
    }

    @Test
    public void aNewCategoryLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditCategoryActivity.class));
    }

    @Test
    public void aNewCategoryWithTheReportBoxFlippedAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditCategoryActivity.class), NewEditItemActivityTest::flipShowReport);
    }

    @Test
    public void keepEditingLeavesANewCategoryOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditCategoryActivity.class), NewEditItemActivityTest::flipShowReport);
    }

    @Test
    public void discardClosesANewCategory() {
        assertDiscardCloses(newIntent(NewEditCategoryActivity.class), NewEditItemActivityTest::flipShowReport);
    }

    @Test
    public void aStoredCategoryLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditCategoryActivity.class, mCategory));
    }

    @Test
    public void aStoredCategoryStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(editIntent(NewEditCategoryActivity.class, mCategory));
    }

    @Test
    public void aNewCategoryRuleLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditCategoryRuleActivity.class));
    }

    @Test
    public void aNewCategoryRuleWithAPatternAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditCategoryRuleActivity.class), NewEditItemActivityTest::typePattern);
    }

    @Test
    public void keepEditingLeavesANewCategoryRuleOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditCategoryRuleActivity.class), NewEditItemActivityTest::typePattern);
    }

    @Test
    public void discardClosesANewCategoryRule() {
        assertDiscardCloses(newIntent(NewEditCategoryRuleActivity.class), NewEditItemActivityTest::typePattern);
    }

    @Test
    public void aStoredCategoryRuleLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditCategoryRuleActivity.class, insertCategoryRule()));
    }

    @Test
    public void aNewEventLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditEventActivity.class));
    }

    @Test
    public void aNewEventWithANoteAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditEventActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void keepEditingLeavesANewEventOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditEventActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void discardClosesANewEvent() {
        assertDiscardCloses(newIntent(NewEditEventActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void aStoredEventLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditEventActivity.class, insertEvent()));
    }

    @Test
    public void aStoredEventStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(editIntent(NewEditEventActivity.class, insertEvent()));
    }

    @Test
    public void aNewPlaceLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditPlaceActivity.class));
    }

    @Test
    public void aNewPlaceWithAnAddressAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditPlaceActivity.class), NewEditItemActivityTest::typeAddress);
    }

    @Test
    public void keepEditingLeavesANewPlaceOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditPlaceActivity.class), NewEditItemActivityTest::typeAddress);
    }

    @Test
    public void discardClosesANewPlace() {
        assertDiscardCloses(newIntent(NewEditPlaceActivity.class), NewEditItemActivityTest::typeAddress);
    }

    @Test
    public void aStoredPlaceLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditPlaceActivity.class, insertPlace()));
    }

    @Test
    public void aNewWalletLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditWalletActivity.class));
    }

    @Test
    public void aNewWalletWithANoteAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditWalletActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void keepEditingLeavesANewWalletOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditWalletActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void discardClosesANewWallet() {
        assertDiscardCloses(newIntent(NewEditWalletActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void aStoredWalletLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditWalletActivity.class, mWallet));
    }

    @Test
    public void aStoredWalletWhoseIconWasRemovedStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(editIntent(NewEditWalletActivity.class, insertWallet("my bank", REMOVED_ICON)));
    }

    @Test
    public void aStoredWalletWhoseIconWasRemovedClosesOnBackAfterItsNameIsTypedAndDeleted() {
        long wallet = insertWallet("my bank", REMOVED_ICON);
        try (ActivityScenario<NewEditItemActivity> scenario =
                     ActivityScenario.launch(editIntent(NewEditWalletActivity.class, wallet))) {
            scenario.onActivity(activity -> {
                MaterialEditText name = activity.findViewById(R.id.name_edit_text);
                name.setText("my bank card");
                name.setText("my bank");
                activity.onBackPressed();
                assertTrue("back left the editor open", activity.isFinishing());
                assertNull("back asked about an editor nothing was changed in", ShadowDialog.getLatestDialog());
            });
        }
    }

    @Test
    public void aStoredWalletWithAnotherIconColorAsksOnBack() {
        assertChangeAsksOnBack(editIntent(NewEditWalletActivity.class, mWallet), activity ->
                ((IconPicker) activity.getSupportFragmentManager().findFragmentByTag(WALLET_ICON_PICKER))
                        .setCurrentIcon(new ColorIcon("#FF0000", "T")));
    }

    @Test
    public void theToolbarArrowAsksAboutAChangedWallet() {
        try (ActivityScenario<NewEditWalletActivity> scenario =
                     ActivityScenario.launch(newIntent(NewEditWalletActivity.class))) {
            scenario.onActivity(activity -> {
                typeNote(activity);
                clickNavigationArrow(activity);
                assertAsking(activity);
                assertFalse("the arrow closed an editor holding a change", activity.isFinishing());
            });
        }
    }

    @Test
    public void theToolbarArrowClosesAnUntouchedWallet() {
        try (ActivityScenario<NewEditWalletActivity> scenario =
                     ActivityScenario.launch(newIntent(NewEditWalletActivity.class))) {
            scenario.onActivity(activity -> {
                clickNavigationArrow(activity);
                assertTrue("the arrow left the editor open", activity.isFinishing());
                assertNull("the arrow asked about an untouched editor", ShadowDialog.getLatestDialog());
            });
        }
    }

    @Test
    public void aNewSavingLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditSavingActivity.class));
    }

    @Test
    public void aNewSavingWithANoteAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditSavingActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void keepEditingLeavesANewSavingOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditSavingActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void discardClosesANewSaving() {
        assertDiscardCloses(newIntent(NewEditSavingActivity.class), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void aStoredSavingLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditSavingActivity.class, insertSaving()));
    }

    @Test
    public void aStoredSavingStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(editIntent(NewEditSavingActivity.class, insertSaving()));
    }

    @Test
    public void aStoredSavingWithAnotherTargetAsksOnBack() {
        assertChangeAsksOnBack(editIntent(NewEditSavingActivity.class, insertSaving()), activity ->
                ((MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(SAVING_MONEY_PICKER))
                        .setMoney(3000L));
    }

    @Test
    public void aNewDebtLeftAloneClosesOnBack() {
        assertBackCloses(debtIntent());
    }

    @Test
    public void aNewDebtWithANoteAsksOnBack() {
        assertChangeAsksOnBack(debtIntent(), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void keepEditingLeavesANewDebtOpen() {
        assertKeepEditingStaysOpen(debtIntent(), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void discardClosesANewDebt() {
        assertDiscardCloses(debtIntent(), NewEditItemActivityTest::typeNote);
    }

    @Test
    public void aStoredDebtLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditDebtActivity.class, insertDebt()));
    }

    @Test
    public void aStoredDebtStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(editIntent(NewEditDebtActivity.class, insertDebt()));
    }

    @Test
    public void aStoredDebtWithAnotherDateAsksOnBack() {
        assertChangeAsksOnBack(editIntent(NewEditDebtActivity.class, insertDebt()), activity ->
                ((DateTimePicker) activity.getSupportFragmentManager().findFragmentByTag(DEBT_DATE_PICKER))
                        .setCurrentDateTime(new Date(0L)));
    }

    @Test
    public void aNewBudgetLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditBudgetActivity.class));
    }

    @Test
    public void aNewBudgetWithALimitAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditBudgetActivity.class), NewEditItemActivityTest::setBudgetLimit);
    }

    @Test
    public void keepEditingLeavesANewBudgetOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditBudgetActivity.class), NewEditItemActivityTest::setBudgetLimit);
    }

    @Test
    public void discardClosesANewBudget() {
        assertDiscardCloses(newIntent(NewEditBudgetActivity.class), NewEditItemActivityTest::setBudgetLimit);
    }

    @Test
    public void aStoredBudgetLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditBudgetActivity.class, insertBudget()));
    }

    @Test
    public void aNewRecurrentTransactionLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditRecurrentTransactionActivity.class));
    }

    @Test
    public void aNewRecurrentTransactionWithADescriptionAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditRecurrentTransactionActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void keepEditingLeavesANewRecurrentTransactionOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditRecurrentTransactionActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void discardClosesANewRecurrentTransaction() {
        assertDiscardCloses(newIntent(NewEditRecurrentTransactionActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void aStoredRecurrentTransactionLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditRecurrentTransactionActivity.class, insertRecurrentTransaction()));
    }

    @Test
    public void aNewRecurrentTransferLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditRecurrentTransferActivity.class));
    }

    @Test
    public void aNewRecurrentTransferWithADescriptionAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditRecurrentTransferActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void keepEditingLeavesANewRecurrentTransferOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditRecurrentTransferActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void discardClosesANewRecurrentTransfer() {
        assertDiscardCloses(newIntent(NewEditRecurrentTransferActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void aStoredRecurrentTransferLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditRecurrentTransferActivity.class, insertRecurrentTransfer()));
    }

    @Test
    public void aNewTransactionModelLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditTransactionModelActivity.class));
    }

    @Test
    public void aNewTransactionModelWithADescriptionAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditTransactionModelActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void keepEditingLeavesANewTransactionModelOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditTransactionModelActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void discardClosesANewTransactionModel() {
        assertDiscardCloses(newIntent(NewEditTransactionModelActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void aStoredTransactionModelLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditTransactionModelActivity.class, insertTransactionModel()));
    }

    @Test
    public void aNewTransferModelLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditTransferModelActivity.class));
    }

    @Test
    public void aNewTransferModelWithADescriptionAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditTransferModelActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void keepEditingLeavesANewTransferModelOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditTransferModelActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void discardClosesANewTransferModel() {
        assertDiscardCloses(newIntent(NewEditTransferModelActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void aStoredTransferModelLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditTransferModelActivity.class, insertTransferModel()));
    }

    @Test
    public void aNewTransferLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditTransferActivity.class));
    }

    @Test
    public void aNewTransferWithADescriptionAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditTransferActivity.class), NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void keepEditingLeavesANewTransferOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditTransferActivity.class), NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void discardClosesANewTransfer() {
        assertDiscardCloses(newIntent(NewEditTransferActivity.class), NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void aStoredTransferLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditTransferActivity.class, insertTransfer()));
    }

    @Test
    public void aNewTransactionLeftAloneClosesOnBack() {
        assertBackCloses(newIntent(NewEditTransactionActivity.class));
    }

    @Test
    public void aNewTransactionWithADescriptionAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditTransactionActivity.class), NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void keepEditingLeavesANewTransactionOpen() {
        assertKeepEditingStaysOpen(newIntent(NewEditTransactionActivity.class),
                NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void discardClosesANewTransaction() {
        assertDiscardCloses(newIntent(NewEditTransactionActivity.class), NewEditItemActivityTest::typeDescription);
    }

    @Test
    public void aStoredTransactionLeftAloneClosesOnBack() {
        assertBackCloses(editIntent(NewEditTransactionActivity.class, insertTransaction()));
    }

    @Test
    public void aNewTransactionWithAnAmountAsksOnBack() {
        assertChangeAsksOnBack(newIntent(NewEditTransactionActivity.class), activity ->
                ((MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(TRANSACTION_MONEY_PICKER))
                        .setMoney(1500L));
    }

    @Test
    public void aStoredTransactionWithAnotherCategoryAsksOnBack() {
        Category coffee = new Category(insertCategory("Coffee"), "Coffee", null, Contract.CategoryType.EXPENSE);
        assertChangeAsksOnBack(editIntent(NewEditTransactionActivity.class, insertTransaction()), activity ->
                ((CategoryPicker) activity.getSupportFragmentManager().findFragmentByTag(TRANSACTION_CATEGORY_PICKER))
                        .setCategory(coffee));
    }

    @Test
    public void aStoredTransactionWithASplitPartAsksOnBack() {
        Category household = new Category(insertCategory("Household"), "Household", null,
                Contract.CategoryType.EXPENSE);
        assertChangeAsksOnBack(editIntent(NewEditTransactionActivity.class, insertTransaction()), activity -> {
            activity.findViewById(R.id.split_edit_text).performClick();
            ((CategoryPicker) activity.getSupportFragmentManager().findFragmentByTag(TRANSACTION_PART_CATEGORY_PICKER))
                    .setCategory(household);
            ((MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(TRANSACTION_PART_MONEY_PICKER))
                    .setMoney(500L);
        });
    }

    @Test
    public void aTransactionFilledInByAnotherAppClosesOnBack() {
        long coffee = insertCategory("Coffee");
        ContentValues rule = new ContentValues();
        rule.put(Contract.CategoryRule.PATTERN, "coffee");
        rule.put(Contract.CategoryRule.CATEGORY_ID, coffee);
        mResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, rule);
        Intent intent = newIntent(NewEditTransactionActivity.class);
        intent.setAction(NewEditTransactionActivity.ACTION_ADD_TRANSACTION);
        intent.putExtra(NewEditTransactionActivity.EXTRA_DESCRIPTION, "Coffee at the station");
        try (ActivityScenario<NewEditTransactionActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                CategoryPicker picker = (CategoryPicker) activity.getSupportFragmentManager()
                        .findFragmentByTag(TRANSACTION_CATEGORY_PICKER);
                assertEquals(coffee, picker.getCurrentCategory().getId());
                activity.onBackPressed();
                assertTrue("back left the editor open", activity.isFinishing());
                assertNull("back asked about an editor nothing was changed in", ShadowDialog.getLatestDialog());
            });
        }
    }

    @Test
    public void aChangeMadeBeforeARotationStillAsksOnBack() {
        try (ActivityScenario<NewEditTransactionActivity> scenario =
                     ActivityScenario.launch(newIntent(NewEditTransactionActivity.class))) {
            scenario.onActivity(NewEditItemActivityTest::typeDescription);
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals("Coffee", description(activity).getTextAsString());
                askOnBack(activity);
                assertFalse("back closed an editor holding a change", activity.isFinishing());
            });
        }
    }

    @Test
    public void anUntouchedEditorStillClosesOnBackAfterARotation() {
        assertBackClosesAfterARotation(newIntent(NewEditTransactionActivity.class));
    }

    @Test
    public void keepEditingAfterAddingAnAttachmentKeepsItsFile() throws IOException {
        assertKeepEditingKeepsAttachment(newIntent(NewEditTransactionActivity.class), TRANSACTION_ATTACHMENT_PICKER);
    }

    @Test
    public void discardAfterAddingAnAttachmentDeletesItsFile() throws IOException {
        assertDiscardDeletesAttachment(newIntent(NewEditTransactionActivity.class), TRANSACTION_ATTACHMENT_PICKER);
    }

    @Test
    public void keepEditingAfterAddingAnAttachmentToATransferKeepsItsFile() throws IOException {
        assertKeepEditingKeepsAttachment(newIntent(NewEditTransferActivity.class), TRANSFER_ATTACHMENT_PICKER);
    }

    @Test
    public void discardAfterAddingAnAttachmentToATransferDeletesItsFile() throws IOException {
        assertDiscardDeletesAttachment(newIntent(NewEditTransferActivity.class), TRANSFER_ATTACHMENT_PICKER);
    }

    private static void assertKeepEditingKeepsAttachment(Intent intent, String pickerTag) throws IOException {
        Application application = ApplicationProvider.getApplicationContext();
        File file = createReceipt(application);
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                Attachment attachment = attach(activity, pickerTag, file);
                AlertDialog dialog = askOnBack(activity);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertFalse("keep editing closed the editor", activity.isFinishing());
                assertFalse("keep editing deleted the attachment", isDeleteRequested(application, attachment));
                assertEquals(1, attachmentPicker(activity, pickerTag).getCurrentAttachments().size());
            });
        } finally {
            shadowOf(application).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
            assertTrue(file.delete());
        }
    }

    private static void assertDiscardDeletesAttachment(Intent intent, String pickerTag) throws IOException {
        Application application = ApplicationProvider.getApplicationContext();
        File file = createReceipt(application);
        shadowOf(application).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                Attachment attachment = attach(activity, pickerTag, file);
                AlertDialog dialog = askOnBack(activity);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertTrue("discard left the editor open", activity.isFinishing());
                assertTrue("discard kept the attachment", isDeleteRequested(application, attachment));
            });
        } finally {
            shadowOf(application).denyPermissions(Manifest.permission.READ_EXTERNAL_STORAGE);
            assertTrue(file.delete());
        }
    }

    private static void assertBackCloses(Intent intent) {
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                activity.onBackPressed();
                assertTrue("back left the editor open", activity.isFinishing());
                assertNull("back asked about an editor nothing was changed in", ShadowDialog.getLatestDialog());
            });
        }
    }

    private static void assertBackClosesAfterARotation(Intent intent) {
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                activity.onBackPressed();
                assertTrue("back left the editor open", activity.isFinishing());
                assertNull("back asked about an editor nothing was changed in", ShadowDialog.getLatestDialog());
            });
        }
    }

    private static void assertChangeAsksOnBack(Intent intent, ActivityScenario.ActivityAction<NewEditItemActivity> change) {
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                change.perform(activity);
                askOnBack(activity);
                assertFalse("back closed an editor holding a change", activity.isFinishing());
            });
        }
    }

    private static void assertKeepEditingStaysOpen(Intent intent, ActivityScenario.ActivityAction<NewEditItemActivity> change) {
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                change.perform(activity);
                AlertDialog dialog = askOnBack(activity);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertFalse(dialog.isShowing());
                assertFalse("keep editing closed the editor", activity.isFinishing());
            });
        }
    }

    private static void assertDiscardCloses(Intent intent, ActivityScenario.ActivityAction<NewEditItemActivity> change) {
        try (ActivityScenario<NewEditItemActivity> scenario = ActivityScenario.launch(intent)) {
            scenario.onActivity(activity -> {
                change.perform(activity);
                AlertDialog dialog = askOnBack(activity);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                shadowOf(Looper.getMainLooper()).idle();
                assertTrue("discard left the editor open", activity.isFinishing());
            });
        }
    }

    private static AlertDialog askOnBack(NewEditItemActivity activity) {
        activity.onBackPressed();
        return assertAsking(activity);
    }

    private static AlertDialog assertAsking(NewEditItemActivity activity) {
        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertTrue("the editor closed without asking", dialog != null && dialog.isShowing());
        assertEquals(activity.getString(R.string.action_discard),
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).getText().toString());
        assertEquals(activity.getString(R.string.action_keep_editing),
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).getText().toString());
        return dialog;
    }

    private static void clickNavigationArrow(NewEditItemActivity activity) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        ArrayList<View> views = new ArrayList<>();
        toolbar.findViewsWithText(views, toolbar.getNavigationContentDescription(),
                View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
        assertEquals(1, views.size());
        views.get(0).performClick();
    }

    private static void typeNote(NewEditItemActivity activity) {
        ((MaterialEditText) activity.findViewById(R.id.note_edit_text)).setText("Paid in cash");
    }

    private static void typeDescription(NewEditItemActivity activity) {
        description(activity).setText("Coffee");
    }

    private static void typePattern(NewEditItemActivity activity) {
        ((MaterialEditText) activity.findViewById(R.id.pattern_edit_text)).setText("coffee");
    }

    private static void typeAddress(NewEditItemActivity activity) {
        ((MaterialEditText) activity.findViewById(R.id.address_edit_text)).setText("Main street");
    }

    private static void flipShowReport(NewEditItemActivity activity) {
        ((CheckBox) activity.findViewById(R.id.show_report_check_box)).performClick();
    }

    private static void setBudgetLimit(NewEditItemActivity activity) {
        ((MoneyPicker) activity.getSupportFragmentManager().findFragmentByTag(BUDGET_MONEY_PICKER)).setMoney(5000L);
    }

    private static MaterialEditText description(NewEditItemActivity activity) {
        return activity.findViewById(R.id.description_edit_text);
    }

    private static AttachmentPicker attachmentPicker(NewEditItemActivity activity, String tag) {
        return (AttachmentPicker) activity.getSupportFragmentManager().findFragmentByTag(tag);
    }

    private static Attachment attach(NewEditItemActivity activity, String pickerTag, File file) {
        Toolbar toolbar = activity.findViewById(R.id.primary_toolbar);
        toolbar.getMenu().performIdentifierAction(R.id.action_attach_file, 0);
        ShadowActivity shadow = shadowOf(activity);
        Intent request = shadow.getNextStartedActivityForResult().intent;
        shadow.receiveResult(request, Activity.RESULT_OK, new Intent().setData(Uri.fromFile(file)));
        List<Attachment> attachments = attachmentPicker(activity, pickerTag).getCurrentAttachments();
        assertEquals(1, attachments.size());
        return attachments.get(0);
    }

    private static boolean isDeleteRequested(Application application, Attachment attachment) {
        boolean requested = false;
        Intent intent;
        while ((intent = shadowOf(application).getNextStartedService()) != null) {
            Attachment target = intent.getParcelableExtra(AttachmentHandlerIntentService.ATTACHMENT);
            if (intent.getIntExtra(AttachmentHandlerIntentService.ACTION, 0) == AttachmentHandlerIntentService.ACTION_DELETE
                    && attachment.equals(target)) {
                requested = true;
            }
        }
        return requested;
    }

    private static File createReceipt(Context context) throws IOException {
        File file = File.createTempFile("receipt", ".txt", context.getCacheDir());
        try (FileOutputStream stream = new FileOutputStream(file)) {
            stream.write(new byte[] {1, 2, 3});
        }
        return file;
    }

    private static Intent newIntent(Class<? extends NewEditItemActivity> editor) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), editor);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.NEW_ITEM);
        return intent;
    }

    private static Intent editIntent(Class<? extends NewEditItemActivity> editor, long id) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(), editor);
        intent.putExtra(NewEditItemActivity.MODE, NewEditItemActivity.Mode.EDIT_ITEM);
        intent.putExtra(NewEditItemActivity.ID, id);
        return intent;
    }

    private static Intent debtIntent() {
        Intent intent = newIntent(NewEditDebtActivity.class);
        intent.putExtra(NewEditDebtActivity.TYPE, Contract.DebtType.DEBT);
        return intent;
    }

    private long insertWallet(String name) {
        return insertWallet(name, ICON);
    }

    private long insertWallet(String name, String icon) {
        ContentValues values = new ContentValues();
        values.put(Contract.Wallet.NAME, name);
        values.put(Contract.Wallet.ICON, icon);
        values.put(Contract.Wallet.CURRENCY, "EUR");
        values.put(Contract.Wallet.START_MONEY, 0L);
        values.put(Contract.Wallet.COUNT_IN_TOTAL, true);
        values.put(Contract.Wallet.ARCHIVED, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_WALLETS, values));
    }

    private long insertCategory(String name) {
        ContentValues values = new ContentValues();
        values.put(Contract.Category.NAME, name);
        values.put(Contract.Category.ICON, ICON);
        values.put(Contract.Category.TYPE, Contract.CategoryType.EXPENSE.getValue());
        values.put(Contract.Category.SHOW_REPORT, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORIES, values));
    }

    private long insertCategoryRule() {
        ContentValues values = new ContentValues();
        values.put(Contract.CategoryRule.PATTERN, "market");
        values.put(Contract.CategoryRule.CATEGORY_ID, mCategory);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_CATEGORY_RULES, values));
    }

    private long insertPerson() {
        ContentValues values = new ContentValues();
        values.put(Contract.Person.NAME, "Ann");
        values.put(Contract.Person.ICON, ICON);
        values.put(Contract.Person.NOTE, "Neighbor");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PEOPLE, values));
    }

    private long insertEvent() {
        ContentValues values = new ContentValues();
        values.put(Contract.Event.NAME, "Fair");
        values.put(Contract.Event.ICON, ICON);
        values.put(Contract.Event.START_DATE, "2019-04-01");
        values.put(Contract.Event.END_DATE, "2019-04-30");
        values.put(Contract.Event.NOTE, "Spring");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_EVENTS, values));
    }

    private long insertPlace() {
        ContentValues values = new ContentValues();
        values.put(Contract.Place.NAME, "Market");
        values.put(Contract.Place.ICON, ICON);
        values.put(Contract.Place.ADDRESS, "Main street");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_PLACES, values));
    }

    private long insertSaving() {
        ContentValues values = new ContentValues();
        values.put(Contract.Saving.DESCRIPTION, "Holiday");
        values.put(Contract.Saving.ICON, ICON);
        values.put(Contract.Saving.START_MONEY, 0L);
        values.put(Contract.Saving.END_MONEY, 2000L);
        values.put(Contract.Saving.WALLET_ID, mWallet);
        values.put(Contract.Saving.END_DATE, DATE);
        values.put(Contract.Saving.COMPLETE, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_SAVINGS, values));
    }

    private long insertDebt() {
        ContentValues values = new ContentValues();
        values.put(Contract.Debt.TYPE, Contract.DebtType.DEBT.getValue());
        values.put(Contract.Debt.ICON, ICON);
        values.put(Contract.Debt.DESCRIPTION, "Loan");
        values.put(Contract.Debt.DATE, DATE);
        values.put(Contract.Debt.WALLET_ID, mWallet);
        values.put(Contract.Debt.MONEY, 20000L);
        values.put(Contract.Debt.ARCHIVED, false);
        values.put(Contract.Debt.INSERT_MASTER_TRANSACTION, false);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_DEBTS, values));
    }

    private long insertBudget() {
        ContentValues values = new ContentValues();
        values.put(Contract.Budget.TYPE, Contract.BudgetType.EXPENSES.getValue());
        values.put(Contract.Budget.MONEY, 90000L);
        values.put(Contract.Budget.CURRENCY, "EUR");
        values.put(Contract.Budget.WALLET_IDS, "<" + mWallet + ">");
        values.put(Contract.Budget.START_DATE, "2019-04-01");
        values.put(Contract.Budget.END_DATE, "2019-04-30");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_BUDGETS, values));
    }

    private long insertRecurrentTransaction() {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransaction.MONEY, 1000L);
        values.put(Contract.RecurrentTransaction.DESCRIPTION, "Rent");
        values.put(Contract.RecurrentTransaction.CATEGORY_ID, mCategory);
        values.put(Contract.RecurrentTransaction.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.RecurrentTransaction.WALLET_ID, mWallet);
        values.put(Contract.RecurrentTransaction.CONFIRMED, true);
        values.put(Contract.RecurrentTransaction.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransaction.START_DATE, DATE);
        values.put(Contract.RecurrentTransaction.RULE, "FREQ=MONTHLY");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSACTIONS, values));
    }

    private long insertRecurrentTransfer() {
        ContentValues values = new ContentValues();
        values.put(Contract.RecurrentTransfer.DESCRIPTION, "Rent share");
        values.put(Contract.RecurrentTransfer.WALLET_FROM_ID, mWallet);
        values.put(Contract.RecurrentTransfer.WALLET_TO_ID, mOtherWallet);
        values.put(Contract.RecurrentTransfer.MONEY_FROM, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TO, 1000L);
        values.put(Contract.RecurrentTransfer.MONEY_TAX, 0L);
        values.put(Contract.RecurrentTransfer.CONFIRMED, true);
        values.put(Contract.RecurrentTransfer.COUNT_IN_TOTAL, true);
        values.put(Contract.RecurrentTransfer.START_DATE, DATE);
        values.put(Contract.RecurrentTransfer.RULE, "FREQ=MONTHLY");
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_RECURRENT_TRANSFERS, values));
    }

    private long insertTransactionModel() {
        ContentValues values = new ContentValues();
        values.put(Contract.TransactionModel.MONEY, 1500L);
        values.put(Contract.TransactionModel.DESCRIPTION, "Weekly shop");
        values.put(Contract.TransactionModel.CATEGORY_ID, mCategory);
        values.put(Contract.TransactionModel.DIRECTION, Contract.Direction.EXPENSE);
        values.put(Contract.TransactionModel.WALLET_ID, mWallet);
        values.put(Contract.TransactionModel.CONFIRMED, true);
        values.put(Contract.TransactionModel.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTION_MODELS, values));
    }

    private long insertTransferModel() {
        ContentValues values = new ContentValues();
        values.put(Contract.TransferModel.DESCRIPTION, "Rent share");
        values.put(Contract.TransferModel.WALLET_FROM_ID, mWallet);
        values.put(Contract.TransferModel.WALLET_TO_ID, mOtherWallet);
        values.put(Contract.TransferModel.MONEY_FROM, 1000L);
        values.put(Contract.TransferModel.MONEY_TO, 1000L);
        values.put(Contract.TransferModel.MONEY_TAX, 0L);
        values.put(Contract.TransferModel.CONFIRMED, true);
        values.put(Contract.TransferModel.COUNT_IN_TOTAL, true);
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFER_MODELS, values));
    }

    private long insertTransfer() {
        ContentValues values = new TransferContentValuesBuilder()
                .description("Stored transfer")
                .date(DATE_TIME)
                .fromWalletId(mWallet)
                .toWalletId(mOtherWallet)
                .taxWalletId(mWallet)
                .fromMoney(1000L)
                .toMoney(1000L)
                .taxMoney(0L)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSFERS, values));
    }

    private long insertTransaction() {
        ContentValues values = new TransactionContentValuesBuilder()
                .money(1500L)
                .date(DATE_TIME)
                .description("Weekly shop")
                .categoryId(mCategory)
                .direction(Contract.Direction.EXPENSE)
                .type(Contract.TransactionType.STANDARD)
                .walletId(mWallet)
                .note("")
                .confirmed(true)
                .countInTotal(true)
                .build();
        return ContentUris.parseId(mResolver.insert(DataContentProvider.CONTENT_TRANSACTIONS, values));
    }
}
