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

package android.server.wm;

import static android.app.AppOpsManager.MODE_ERRORED;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.UserManager;
import android.provider.DeviceConfig;

import androidx.annotation.CallSuper;

import com.android.compatibility.common.util.AppOpsUtils;

import org.junit.After;
import org.junit.Before;

import java.util.List;
import java.util.stream.Stream;

public abstract class BackgroundActivityTestBase extends ActivityManagerTestBase {
    static final String APP_A_PACKAGE = "android.server.wm.backgroundactivity.appa";
    static final android.server.wm.backgroundactivity.appa.Components APP_A =
            android.server.wm.backgroundactivity.appa.Components.get(APP_A_PACKAGE);
    static final android.server.wm.backgroundactivity.appa.Components APP_A_33 =
            android.server.wm.backgroundactivity.appa.Components.get(APP_A_PACKAGE + "33");

    static final String APP_B_PACKAGE = "android.server.wm.backgroundactivity.appb";
    static final android.server.wm.backgroundactivity.appb.Components APP_B =
            android.server.wm.backgroundactivity.appb.Components.get(APP_B_PACKAGE);
    static final android.server.wm.backgroundactivity.appb.Components APP_B_33 =
            android.server.wm.backgroundactivity.appb.Components.get(APP_B_PACKAGE + "33");

    static final List<android.server.wm.backgroundactivity.appa.Components> ALL_A =
            List.of(APP_A, APP_A_33);
    static final List<android.server.wm.backgroundactivity.appb.Components> ALL_B =
            List.of(APP_B, APP_B_33);

    static final String SHELL_PACKAGE = "com.android.shell";

    ServiceConnection mBalServiceConnection;

    static final int ACTIVITY_FOCUS_TIMEOUT_MS = 3000;

    private String initialAsmFlagVal;
    private String initialBalFlagVal;

    @Before
    public void enableFeatureFlags() {
        runWithShellPermission(() -> {
            initialAsmFlagVal = DeviceConfig.getProperty("window_manager",
                    "asm_restrictions_enabled");
            DeviceConfig.setProperty("window_manager",
                    "asm_restrictions_enabled", "0", false);

            initialBalFlagVal = DeviceConfig.getProperty("window_manager",
                    "enable_default_rescind_bal_privileges_from_pending_intent_sender");
            DeviceConfig.setProperty("window_manager",
                    "enable_default_rescind_bal_privileges_from_pending_intent_sender", "true",
                    false);
        });
    }

    @After
    public void disableFeatureFlags() {
        runWithShellPermission(() -> {
            if (initialAsmFlagVal != null) {
                DeviceConfig.setProperty("window_manager",
                        "asm_restrictions_enabled", initialAsmFlagVal, false);
            } else {
                DeviceConfig.deleteProperty("window_manager",
                        "asm_restrictions_enabled");
            }

            if (initialBalFlagVal != null) {
                DeviceConfig.setProperty("window_manager",
                        "enable_default_rescind_bal_privileges_from_pending_intent_sender",
                        initialBalFlagVal, false);
            } else {
                DeviceConfig.deleteProperty("window_manager",
                        "enable_default_rescind_bal_privileges_from_pending_intent_sender");
            }
        });
    }

    @Override
    @Before
    @CallSuper
    public void setUp() throws Exception {
        // disable SAW appopp for AppA (it's granted automatically when installed in CTS)
        for (android.server.wm.backgroundactivity.appa.Components components : ALL_A) {
            AppOpsUtils.setOpMode(components.APP_PACKAGE_NAME, "android:system_alert_window",
                    MODE_ERRORED);
            assertEquals(AppOpsUtils.getOpMode(components.APP_PACKAGE_NAME,
                            "android:system_alert_window"),
                    MODE_ERRORED);
        }

        super.setUp();

        for (android.server.wm.backgroundactivity.appa.Components appA : ALL_A) {
            assertNull(mWmState.getTaskByActivity(appA.BACKGROUND_ACTIVITY));
            assertNull(mWmState.getTaskByActivity(appA.FOREGROUND_ACTIVITY));
            runShellCommand("cmd deviceidle tempwhitelist -d 100000 "
                    + appA.APP_PACKAGE_NAME);
        }
        for (android.server.wm.backgroundactivity.appb.Components appB : ALL_B) {
            assertNull(mWmState.getTaskByActivity(appB.FOREGROUND_ACTIVITY));
            runShellCommand("cmd deviceidle tempwhitelist -d 100000 "
                    + appB.FOREGROUND_ACTIVITY.getPackageName());
        }
    }

    @After
    public void tearDown() throws Exception {
        // We do this before anything else, because having an active device owner can prevent us
        // from being able to force stop apps. (b/142061276)
        for (android.server.wm.backgroundactivity.appa.Components appA : ALL_A) {
            runWithShellPermissionIdentity(() -> {
                runShellCommand("dpm remove-active-admin --user 0 "
                        + appA.SIMPLE_ADMIN_RECEIVER.flattenToString());
                if (UserManager.isHeadlessSystemUserMode()) {
                    // Must also remove the PO from current user
                    runShellCommand("dpm remove-active-admin --user cur "
                            + appA.SIMPLE_ADMIN_RECEIVER.flattenToString());
                }
            });
            stopTestPackage(appA.APP_PACKAGE_NAME);
            AppOpsUtils.reset(appA.APP_PACKAGE_NAME);

        }
        for (android.server.wm.backgroundactivity.appb.Components appB : ALL_B) {
            stopTestPackage(appB.APP_PACKAGE_NAME);
        }
        AppOpsUtils.reset(SHELL_PACKAGE);
        if (mBalServiceConnection != null) {
            mContext.unbindService(mBalServiceConnection);
        }
    }

    boolean waitForActivityFocused(ComponentName componentName) {
        return waitForActivityFocused(ACTIVITY_FOCUS_TIMEOUT_MS, componentName);
    }

    void assertTaskStack(ComponentName[] expectedComponents,
            ComponentName sourceComponent) {
        if (expectedComponents == null) {
            assertNull(mWmState.getTaskByActivity(sourceComponent));
            return;
        }
        List<WindowManagerState.Activity> actual = mWmState.getTaskByActivity(
                sourceComponent).mActivities;
        assertEquals(expectedComponents.length, actual.size());
        int size = expectedComponents.length;
        for (int i = 0; i < size; i++) {
            assertEquals(expectedComponents[i].flattenToShortString(), actual.get(i).getName());
        }
    }

    Intent getLaunchActivitiesBroadcast(android.server.wm.backgroundactivity.appa.Components appA,
            ComponentName... componentNames) {
        Intent broadcastIntent = new Intent(
                appA.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES);
        Intent[] intents = Stream.of(componentNames)
                .map(c -> {
                    Intent intent = new Intent();
                    intent.setComponent(c);
                    return intent;
                })
                .toArray(Intent[]::new);
        broadcastIntent.putExtra(appA.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_INTENTS, intents);
        return broadcastIntent;
    }
}
