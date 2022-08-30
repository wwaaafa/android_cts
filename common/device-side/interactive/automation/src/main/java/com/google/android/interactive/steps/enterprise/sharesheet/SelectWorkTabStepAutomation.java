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

package com.google.android.interactive.steps.enterprise.sharesheet;

import static com.android.interactive.Nothing.NOTHING;

import android.widget.Button;

import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.Nothing;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.google.android.interactive.steps.enterprise.sharesheet.SelectWorkTabStep")
public class SelectWorkTabStepAutomation implements Automation<Nothing> {
    @Override
    public Nothing automate() throws Throwable {
        TestApis.ui().device().findObject(new UiSelector().text("Work")
                .className(Button.class)).click();

        return NOTHING;
    }
}
