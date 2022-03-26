/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.autofillservice.cts.dialog;

import static android.autofillservice.cts.testcore.Helper.ID_PASSWORD;
import static android.autofillservice.cts.testcore.Helper.ID_USERNAME;
import static android.autofillservice.cts.testcore.Helper.assertHasFlags;
import static android.autofillservice.cts.testcore.Helper.enableFillDialogFeature;
import static android.service.autofill.FillRequest.FLAG_SUPPORTS_FILL_DIALOG;

import android.autofillservice.cts.activities.LoginActivity;
import android.autofillservice.cts.commontests.AutoFillServiceTestCase;
import android.autofillservice.cts.testcore.CannedFillResponse;
import android.autofillservice.cts.testcore.CannedFillResponse.CannedDataset;
import android.autofillservice.cts.testcore.InstrumentedAutoFillService.FillRequest;
import android.support.test.uiautomator.UiObject2;
import android.view.View;

import org.junit.Test;


/**
 * This is the test cases for the fill dialog UI.
 */
public class LoginActivityTest extends AutoFillServiceTestCase.ManualActivityLaunch {

    @Test
    public void testShowFillDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with a dataset > fill dialog should have two buttons
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();

        // Check onFillRequest has the flag: FLAG_SUPPORTS_FILL_DIALOG
        final FillRequest fillRequest = sReplier.getNextFillRequest();
        assertHasFlags(fillRequest.flags, FLAG_SUPPORTS_FILL_DIALOG);
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog
        mUiBot.selectByRelativeIdFromUiDevice(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify the content of fill dialog, and then select dataset in fill dialog
        mUiBot.assertFillDialogHeader("Dialog Header");
        mUiBot.assertFillDialogRejectButton();
        mUiBot.assertFillDialogAcceptButton();
        final UiObject2 picker = mUiBot.assertFillDialogDatasets("Dialog Presentation");

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset(picker, "Dialog Presentation");

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    public void testShowFillDialog_twoSuggestions_oneButton() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with two datasets > fill dialog should only one button
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setField(ID_PASSWORD, "SWEET")
                        .setPresentation(createPresentation("Dropdown Presentation2"))
                        .setDialogPresentation(createPresentation("Dialog Presentation2"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill on the password field and verify fill dialog is shown
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field
        mUiBot.selectByRelativeIdFromUiDevice(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        // Verify the content of fill dialog
        mUiBot.assertFillDialogHeader("Dialog Header");
        mUiBot.assertFillDialogRejectButton();
        mUiBot.assertFillDialogNoAcceptButton();
        final UiObject2 picker =
                mUiBot.assertFillDialogDatasets("Dialog Presentation", "Dialog Presentation2");

        // Set expected value, then select dataset
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset(picker, "Dialog Presentation");

        // Check the results.
        activity.assertAutoFilled();
    }

    @Test
    public void testShowFillDialog_switchToUnsupportedField_fallbackDropdown() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Trigger autofill on the password field and verify fill dialog is shown.
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        mUiBot.selectByRelativeIdFromUiDevice(ID_PASSWORD);
        mUiBot.waitForIdleSync();

        mUiBot.assertFillDialogDatasets("Dialog presentation");

        // Click on username field, and verify dropdown UI is shown
        mUiBot.selectByRelativeIdFromUiDevice(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("Dropdown Presentation");

        // Verify dropdown UI works
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset("Dropdown Presentation");

        activity.assertAutoFilled();
    }

    @Test
    public void testFillDialog_fromUnsupportedFieldSwitchToSupported_noFillDialog()
            throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog presentation"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on username field, and verify dropdown UI is shown.
        mUiBot.selectByRelativeIdFromUiDevice(ID_USERNAME);
        mUiBot.waitForIdleSync();

        mUiBot.assertDatasets("Dropdown Presentation");

        // Click on password field and verify dropdown is still shown
        // can't use mUiBot.selectByRelativeId(ID_PASSWORD), because will click on dropdown UI
        activity.onPassword(View::requestFocus);
        mUiBot.waitForIdleSync();

        // Verify dropdown UI actually works in this case.
        activity.expectAutoFill("dude", "sweet");
        mUiBot.selectDataset("Dropdown Presentation");

        activity.assertAutoFilled();
    }

    @Test
    public void testShowFillDialog_datasetNoDialogPresentation_notShownInDialog() throws Exception {
        // Enable feature and test service
        enableFillDialogFeature(sContext);
        enableService();

        // Set response with one dataset is no dialog presentation
        final CannedFillResponse.Builder builder = new CannedFillResponse.Builder()
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "dude")
                        .setField(ID_PASSWORD, "sweet")
                        .setPresentation(createPresentation("Dropdown Presentation"))
                        .setDialogPresentation(createPresentation("Dialog Presentation"))
                        .build())
                .addDataset(new CannedDataset.Builder()
                        .setField(ID_USERNAME, "DUDE")
                        .setField(ID_PASSWORD, "SWEET")
                        .setPresentation(createPresentation("Dropdown Presentation2"))
                        .build())
                .setDialogHeader(createPresentation("Dialog Header"))
                .setDialogTriggerIds(ID_PASSWORD);
        sReplier.addResponse(builder.build());

        // Start activity and autofill
        LoginActivity activity = startLoginActivity();
        mUiBot.waitForIdleSync();
        sReplier.getNextFillRequest();
        mUiBot.waitForIdleSync();

        // Click on password field to trigger fill dialog, then select
        mUiBot.selectByRelativeIdFromUiDevice(ID_PASSWORD);
        mUiBot.waitForIdleSync();
        activity.expectAutoFill("dude", "sweet");

        mUiBot.selectFillDialogDataset("Dialog Presentation");

        activity.assertAutoFilled();
    }
}
