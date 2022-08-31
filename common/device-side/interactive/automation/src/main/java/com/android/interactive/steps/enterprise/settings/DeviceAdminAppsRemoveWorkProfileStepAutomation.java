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

package com.android.interactive.steps.enterprise.settings;

import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.nene.TestApis;
import com.android.interactive.Automation;
import com.android.interactive.Nothing;
import com.android.interactive.annotations.AutomationFor;

@AutomationFor("com.android.interactive.steps.enterprise.settings.DeviceAdminAppsRemoveWorkProfileStep")
public final class DeviceAdminAppsRemoveWorkProfileStepAutomation implements Automation<Nothing> {

    @Override
    public Nothing automate() throws Throwable {
        UiScrollable settingsItem = new UiScrollable(new UiSelector()
                .className("androidx.recyclerview.widget.RecyclerView"));
        settingsItem.getChildByText(new UiSelector()
                .className(TextView.class), "RemoteDPC").click();

        // Now on the confirmation page
        UiScrollable confirmPageList = new UiScrollable(new UiSelector()
                .className(ScrollView.class));

        confirmPageList.getChildByText(new UiSelector()
                .className(Button.class), "Remove work profile").click();

        // Confirm
        TestApis.ui().device().findObject(new UiSelector().text("Delete").className(
                Button.class)).click();

        return Nothing.NOTHING;
    }
}
