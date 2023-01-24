/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.devicepolicy.cts;

import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL;
import static com.android.queryable.queries.ActivityQuery.activity;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppActivityReference;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests to ensure apps are properly restricted when the user is in quiet mode.
 */
@RunWith(BedsteadJUnit4.class)
public class QuietModeTest {
    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final TestApp sTestApp = sDeviceState.testApps().query()
            .whereActivities()
            .contains(activity().where().exported().isTrue())
            .get();

    private static final Context sContext = TestApis.context().instrumentedContext();

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void startActivityInQuietProfile_quietModeDialogShown() throws Exception {
        UserReference workProfile = sDeviceState.workProfile();
        try (TestAppInstance instance = sTestApp.install(workProfile)) {
            // Override "Turn on work apps" dialog title to avoid depending on a particular string.
            TestApis.devicePolicy().resources().strings().set(
                    "Core.UNLAUNCHABLE_APP_WORK_PAUSED_TITLE", R.string.test_string_1);

            TestAppActivityReference activityReference =
                    instance.activities().query().whereActivity().exported().isTrue().get();

            workProfile.setQuietMode(true);

            Intent intent = new Intent()
                    .addFlags(FLAG_ACTIVITY_NEW_TASK)
                    .setComponent(activityReference.component().componentName());
            sContext.startActivityAsUser(intent, new Bundle(), workProfile.userHandle());

            assertThat(TestApis.ui().device()
                    .findObject(new UiSelector().text("test string 1"))
                    .exists()).isTrue();
        } finally {
            TestApis.devicePolicy().resources().strings()
                    .reset("Core.UNLAUNCHABLE_APP_WORK_PAUSED_TITLE");
            workProfile.setQuietMode(false);
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void quietMode_profileKeptRunning() throws Exception {
        assumeTrue("Keep profiles available feature not enabled", keepProfilesRunningEnabled());
        UserReference workProfile = sDeviceState.workProfile();
        try {
            workProfile.setQuietMode(true);

            // If user is being stopped, let it finish.
            TestApis.broadcasts().waitForBroadcastBarrier("Ensure asynchronous work done");

            assertThat(workProfile.isRunning()).isTrue();
            // This requires running code in the profile user and will throw an exception if the
            // user is stopped.
            assertThat(sDeviceState.dpc().userManager().isManagedProfile()).isTrue();
            // The DPC should be suspended in quiet mode if the profile is kept running.
            assertThat(sContext.getPackageManager().isPackageSuspendedForUser(
                    sDeviceState.dpc().packageName(), workProfile.id())).isTrue();
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    @Postsubmit(reason = "new test")
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    @Test
    public void quietMode_profileStopped() throws Exception {
        assumeFalse("Keep profiles available feature is enabled", keepProfilesRunningEnabled());
        UserReference workProfile = sDeviceState.workProfile();
        try {
            workProfile.setQuietMode(true);

            // Profile should be stopped.
            Poll.forValue("profile running", workProfile::isRunning).toBeEqualTo(false)
                    .errorOnFail().await();

            // The DPC shouldn't be suspended.
            assertThat(sDeviceState.dpc().testApp().pkg().isSuspended(workProfile)).isFalse();
        } finally {
            workProfile.setQuietMode(false);
        }
    }

    private boolean keepProfilesRunningEnabled() throws Exception {
        return ShellCommand.builder("dumpsys device_policy").execute()
                .contains("Keep profiles running: true");
    }
}
