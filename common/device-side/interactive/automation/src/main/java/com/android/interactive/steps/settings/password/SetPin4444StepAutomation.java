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

package com.android.interactive.steps.settings.password;

import android.widget.EditText;
import android.widget.TextView;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.Nothing;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.settings.password.SetPin4444Step")
public final class SetPin4444StepAutomation implements Automation<Nothing> {
    @Override
    public Nothing automate() throws Exception {
        TestApis.ui().device().findObject(
                new UiSelector()
                        .className(TextView.class).text("Continue without Pixel Imprint")).click();

        TestApis.ui().device().findObject(
                new UiSelector()
                        .className(TextView.class).text("PIN")).click();

        TestApis.ui().device().findObject(
                new UiSelector()
                        .className(EditText.class)).setText("4444");

        // Due to some UIAutomator bug we need to keep tapping Next until we get past this screen...

        UiObject nextButton = TestApis.ui().device().findObject(new UiSelector().text("Next"));
        while (nextButton.exists()) {
            nextButton.click();
        }

        TestApis.ui().device().findObject(
                new UiSelector()
                        .className(EditText.class)).setText("4444");

        UiObject confirmButton = TestApis.ui().device().findObject(
                new UiSelector().text("Confirm"));
        while (confirmButton.exists()) {
            confirmButton.click();
        }

        return null;
    }
}
