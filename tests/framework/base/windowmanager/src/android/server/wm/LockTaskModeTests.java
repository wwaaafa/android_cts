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

package android.server.wm;

import static android.app.ActivityManager.LOCK_TASK_MODE_LOCKED;
import static android.app.ActivityManager.LOCK_TASK_MODE_NONE;
import static android.app.ActivityManager.LOCK_TASK_MODE_PINNED;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.app.Components.TEST_ACTIVITY;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerState.Task;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceTestCases:LockTaskModeTests
 */
@Presubmit
public class LockTaskModeTests extends ActivityManagerTestBase {

    private static final String[] LOCK_TASK_PACKAGES_ALLOWLIST =
        new String[] {"android.server.wm.app"};

    @Test
    @ApiTest(apis = {"android.app.ActivityTaskManager#updateLockTaskPackages",
            "android.app.ActivityTaskManager#startSystemLockTaskMode"})
    public void
            startAllowedPackageIntoLockTaskMode_anotherAppPinned_exitsPinningEntersLockTaskMode() {
        // clear lock task
        Context systemUserContext =
                mContext.createContextAsUser(UserHandle.SYSTEM, /* flags= */ 0);
        runWithShellPermission(() -> mAtm.updateLockTaskPackages(systemUserContext,
                new String[0]));
        assertThat(mAm.getLockTaskModeState()).isEqualTo(LOCK_TASK_MODE_NONE);

        try {
            // pin app
            ComponentName testActivity = new ComponentName(mContext, TestActivity.class);
            launchActivity(testActivity);
            Task task = mWmState.getRootTaskByActivity(testActivity);
            runWithShellPermission(() -> mAtm.startSystemLockTaskMode(task.mTaskId));
            waitForOrFail("Task in app pinning mode", () -> {
                return mAm.getLockTaskModeState() == LOCK_TASK_MODE_PINNED;
            });

            // setup lock task mode allowlist
            runWithShellPermission(() -> mAtm.updateLockTaskPackages(systemUserContext,
                    LOCK_TASK_PACKAGES_ALLOWLIST));

            // start allowed package into lock task mode
            mContext.startActivity(new Intent()
                            .addFlags(FLAG_ACTIVITY_NEW_TASK)
                            .setComponent(TEST_ACTIVITY),
                    ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle());
            waitAndAssertResumedActivity(TEST_ACTIVITY, "Activity must be started and resumed");
            waitForOrFail("Task in lock task mode", () -> {
                return mAm.getLockTaskModeState() == LOCK_TASK_MODE_LOCKED;
            });
        } finally {
            // cleanup
            runWithShellPermission(() -> mAtm.updateLockTaskPackages(systemUserContext,
                    new String[0]));
        }
    }

    /* An empty Activity which will be used in app pinning. */
    public static final class TestActivity extends Activity {}
}
