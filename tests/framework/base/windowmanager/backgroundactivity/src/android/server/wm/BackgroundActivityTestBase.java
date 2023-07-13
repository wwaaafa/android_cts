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
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.WINDOWING_MODE_PINNED;
import static android.server.wm.ComponentNameUtils.getActivityName;
import static android.server.wm.backgroundactivity.common.CommonComponents.COMMON_FOREGROUND_ACTIVITY_EXTRAS;
import static android.server.wm.backgroundactivity.common.CommonComponents.TEST_SERVICE;

import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.UserManager;
import android.server.wm.WindowManagerState.Task;
import android.server.wm.backgroundactivity.appa.Components;
import android.server.wm.backgroundactivity.common.ITestService;
import android.util.Log;

import androidx.annotation.CallSuper;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;

import org.junit.After;
import org.junit.Before;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BackgroundActivityTestBase extends ActivityManagerTestBase {

    private static final String TAG = BackgroundActivityTestBase.class.getSimpleName();

    static final String APP_A_PACKAGE = "android.server.wm.backgroundactivity.appa";
    static final Components APP_A = Components.get(APP_A_PACKAGE);
    static final Components APP_A_33 = Components.get(APP_A_PACKAGE + "33");

    static final String APP_B_PACKAGE = "android.server.wm.backgroundactivity.appb";
    static final Components APP_B = Components.get(APP_B_PACKAGE);
    static final Components APP_B_33 = Components.get(APP_B_PACKAGE + "33");

    static final String APP_C_PACKAGE = "android.server.wm.backgroundactivity.appc";
    static final Components APP_C = Components.get(APP_C_PACKAGE);
    static final Components APP_C_33 = Components.get(APP_C_PACKAGE + "33");

    static final List<Components> ALL_APPS =
            List.of(APP_A, APP_A_33, APP_B, APP_B_33, APP_C, APP_C_33);

    static final String SHELL_PACKAGE = "com.android.shell";
    static final int ACTIVITY_FOCUS_TIMEOUT_MS = 3000;

    // TODO(b/258792202): Cleanup with feature flag
    static final String NAMESPACE_WINDOW_MANAGER = "window_manager";
    static final String ASM_RESTRICTIONS_ENABLED =
            "ActivitySecurity__asm_restrictions_enabled";
    private static final int TEST_SERVICE_SETUP_TIMEOUT_MS = 1000;
    final DeviceConfigStateHelper mDeviceConfig =
            new DeviceConfigStateHelper(NAMESPACE_WINDOW_MANAGER);

    private final Map<ComponentName, FutureConnection<ITestService>> mServiceConnections =
            new HashMap<>();

    @Before
    public void enableFeatureFlags() {
        mDeviceConfig.set(ASM_RESTRICTIONS_ENABLED, "1");
    }

    @After
    public void disableFeatureFlags() throws Exception {
        mDeviceConfig.close();
    }

    @Override
    @Before
    @CallSuper
    public void setUp() throws Exception {
        // disable SAW appopp (it's granted automatically when installed in CTS)
        for (Components components : ALL_APPS) {
            AppOpsUtils.setOpMode(components.APP_PACKAGE_NAME, "android:system_alert_window",
                    MODE_ERRORED);
            assertEquals(AppOpsUtils.getOpMode(components.APP_PACKAGE_NAME,
                            "android:system_alert_window"),
                    MODE_ERRORED);
        }

        super.setUp();

        for (Components app : ALL_APPS) {
            assertNull(mWmState.getTaskByActivity(app.BACKGROUND_ACTIVITY));
            assertNull(mWmState.getTaskByActivity(app.FOREGROUND_ACTIVITY));
            runShellCommand("cmd deviceidle tempwhitelist -d 100000 "
                    + app.APP_PACKAGE_NAME);
        }
    }

    @After
    public void tearDown() throws Exception {
        // We do this before anything else, because having an active device owner can prevent us
        // from being able to force stop apps. (b/142061276)
        for (Components app : ALL_APPS) {
            runWithShellPermissionIdentity(() -> {
                runShellCommand("dpm remove-active-admin --user 0 "
                        + app.SIMPLE_ADMIN_RECEIVER.flattenToString());
                if (UserManager.isHeadlessSystemUserMode()) {
                    // Must also remove the PO from current user
                    runShellCommand("dpm remove-active-admin --user cur "
                            + app.SIMPLE_ADMIN_RECEIVER.flattenToString());
                }
            });
            stopTestPackage(app.APP_PACKAGE_NAME);
            AppOpsUtils.reset(app.APP_PACKAGE_NAME);

        }
        AppOpsUtils.reset(SHELL_PACKAGE);
        for (FutureConnection<ITestService> fc : mServiceConnections.values()) {
            mContext.unbindService(fc);
        }
    }

    boolean waitForActivityFocused(ComponentName componentName) {
        return waitForActivityFocused(ACTIVITY_FOCUS_TIMEOUT_MS, componentName);
    }

    void assertPinnedStackDoesNotExist() {
        mWmState.assertDoesNotContainStack("Must not contain pinned stack.",
                WINDOWING_MODE_PINNED, ACTIVITY_TYPE_STANDARD);
    }
    void assertTaskStackIsEmpty(ComponentName sourceComponent) {
        Task task = mWmState.getTaskByActivity(sourceComponent);
        assertWithMessage("task for %s", sourceComponent.flattenToShortString()).that(task)
                .isNull();
    }

    void assertTaskStackHasComponents(ComponentName sourceComponent,
            ComponentName... expectedComponents) {
        Task task = mWmState.getTaskByActivity(sourceComponent);
        assertWithMessage("task for %s", sourceComponent.flattenToShortString()).that(task)
                .isNotNull();
        Log.d(TAG, "Task for " + sourceComponent.flattenToShortString() + ": " + task
                + " Activities: " + task.mActivities);
        List<String> actualNames = getActivityNames(task.mActivities);
        List<String> expectedNames = Arrays.stream(expectedComponents)
                .map((c) -> c.flattenToShortString()).collect(Collectors.toList());

        assertWithMessage("task activities").that(actualNames)
                .containsExactlyElementsIn(expectedNames).inOrder();
    }

    void assertTaskDoesNotHaveVisibleComponents(ComponentName sourceComponent,
            ComponentName... expectedComponents) {
        Task task = mWmState.getTaskByActivity(sourceComponent);
        Log.d(TAG, "Task for " + sourceComponent.flattenToShortString() + ": " + task);
        List<WindowManagerState.Activity> actual = getVisibleActivities(task.mActivities);
        Log.v(TAG, "Task activities: all=" + task.mActivities + ", visible=" + actual);
        if (actual == null) {
            return;
        }
        List<String> actualNames = getActivityNames(actual);
        List<String> expectedNames = Arrays.stream(expectedComponents)
                .map((c) -> c.flattenToShortString()).collect(Collectors.toList());

        assertWithMessage("task activities").that(actualNames).containsNoneIn(expectedNames);
    }

    List<WindowManagerState.Activity> getVisibleActivities(
            List<WindowManagerState.Activity> activities) {
        return activities.stream().filter(WindowManagerState.Activity::isVisible)
                .collect(Collectors.toList());
    }

    List<String> getActivityNames(List<WindowManagerState.Activity> activities) {
        return activities.stream().map(a -> a.getName()).collect(Collectors.toList());
    }

    Intent getLaunchActivitiesBroadcast(Components app,
            ComponentName... componentNames) {
        Intent broadcastIntent = new Intent(
                app.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES);
        Intent[] intents = Stream.of(componentNames)
                .map(c -> {
                    Intent intent = new Intent();
                    intent.setComponent(c);
                    return intent;
                })
                .toArray(Intent[]::new);
        broadcastIntent.putExtra(app.FOREGROUND_ACTIVITY_EXTRA.LAUNCH_INTENTS, intents);
        return broadcastIntent;
    }

    class ActivityStartVerifier {
        private Intent mBroadcastIntent = new Intent();
        private Intent mLaunchIntent = new Intent();

        ActivityStartVerifier setupTaskWithForegroundActivity(Components app) {
            setupTaskWithForegroundActivity(app, -1);
            return this;
        }

        ActivityStartVerifier setupTaskWithForegroundActivity(Components app, int id) {
            Intent intent = new Intent();
            intent.setComponent(app.FOREGROUND_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(COMMON_FOREGROUND_ACTIVITY_EXTRAS.ACTIVITY_ID, id);
            mContext.startActivity(intent);
            mWmState.waitForValidState(app.FOREGROUND_ACTIVITY);
            return this;
        }

        ActivityStartVerifier setupTaskWithEmbeddingActivity(Components app) {
            Intent intent = new Intent();
            intent.setComponent(app.FOREGROUND_EMBEDDING_ACTIVITY);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivity(intent);
            mWmState.waitForValidState(app.FOREGROUND_EMBEDDING_ACTIVITY);
            return this;
        }

        ActivityStartVerifier startFromForegroundActivity(Components app) {
            mBroadcastIntent.setAction(
                    app.FOREGROUND_ACTIVITY_ACTIONS.LAUNCH_BACKGROUND_ACTIVITIES);
            return this;
        }

        ActivityStartVerifier startFromForegroundActivity(Components app, int id) {
            startFromForegroundActivity(app);
            mBroadcastIntent.putExtra(COMMON_FOREGROUND_ACTIVITY_EXTRAS.ACTIVITY_ID, id);
            return this;
        }

        ActivityStartVerifier startFromEmbeddingActivity(Components app) {
            mBroadcastIntent.setAction(
                    app.FOREGROUND_EMBEDDING_ACTIVITY_ACTIONS.LAUNCH_EMBEDDED_ACTIVITY);
            return this;
        }

        ActivityStartVerifier withBroadcastExtra(String key, boolean value) {
            mBroadcastIntent.putExtra(key, value);
            return this;
        }

        ActivityStartVerifier activity(ComponentName to) {
            mLaunchIntent.setComponent(to);
            mBroadcastIntent.putExtra(COMMON_FOREGROUND_ACTIVITY_EXTRAS.LAUNCH_INTENTS,
                    new Intent[]{mLaunchIntent});
            return this;
        }

        ActivityStartVerifier activity(ComponentName to, int id) {
            activity(to);
            mLaunchIntent.putExtra(COMMON_FOREGROUND_ACTIVITY_EXTRAS.ACTIVITY_ID, id);
            return this;
        }

        /**
         * Broadcasts the specified intents, asserts that the launch succeeded or failed, then
         * resets all ActivityStartVerifier state (i.e - intent component and flags) so the
         * ActivityStartVerifier can be reused.
         */
        ActivityStartVerifier executeAndAssertLaunch(boolean succeeds) {
            mContext.sendBroadcast(mBroadcastIntent);

            ComponentName launchedComponent = mLaunchIntent.getComponent();
            mWmState.waitForValidState(launchedComponent);
            boolean result = waitForActivityFocused(launchedComponent);
            assertEquals("Activity: " + launchedComponent.flattenToShortString() + " launch ",
                    succeeds, result);

            // Reset intents to remove any added flags
            reset();
            return this;
        }

        void reset() {
            mBroadcastIntent = new Intent();
            mLaunchIntent = new Intent();
        }

        ActivityStartVerifier thenAssert(Runnable run) {
            run.run();
            return this;
        }

        ActivityStartVerifier thenAssertTaskStack(ComponentName... expectedComponents) {
            assertTaskStackHasComponents(expectedComponents[expectedComponents.length - 1],
                    expectedComponents);
            return this;
        }

        /**
         * <pre>
         * | expectedRootActivity | expectedEmbeddedActivities |
         * |  fragment 1 - left   |     fragment 0 - right     |
         * |----------------------|----------------------------|
         * |                      |             A4             |  top
         * |                      |             A3             |
         * |          A1          |             A2             |  bottom
         * </pre>
         * @param expectedEmbeddedActivities The expected activities on the right side of the split
         *                                   (fragment 0), top to bottom
         * @param expectedRootActivity The expected activity on the left side of the split
         *                             (fragment 1)
         */
        ActivityStartVerifier thenAssertEmbeddingTaskStack(
                ComponentName[] expectedEmbeddedActivities, ComponentName expectedRootActivity) {
            List<WindowManagerState.TaskFragment> fragments = mWmState.getTaskByActivity(
                    expectedRootActivity).getTaskFragments();
            assertEquals(2, fragments.size());

            List<WindowManagerState.Activity> embeddedActivities = fragments.get(0).getActivities();
            List<WindowManagerState.Activity> rootActivity = fragments.get(1).getActivities();

            assertEquals(1, rootActivity.size());
            assertEquals(expectedRootActivity.flattenToShortString(),
                    rootActivity.get(0).getName());

            assertEquals(expectedEmbeddedActivities.length, embeddedActivities.size());
            for (int i = 0; i < expectedEmbeddedActivities.length; i++) {
                assertEquals(expectedEmbeddedActivities[i].flattenToShortString(),
                        embeddedActivities.get(i).getName());
            }
            return this;
        }
    }


    protected void assertActivityFocused(ComponentName componentName) {
        assertActivityFocused(ACTIVITY_FOCUS_TIMEOUT_MS, componentName);
    }

    protected void assertActivityNotFocused(ComponentName componentName) {
        assertActivityNotFocused(ACTIVITY_FOCUS_TIMEOUT_MS, componentName);
    }

    protected void assertActivityFocused(ComponentName componentName, String message) {
        assertActivityFocused(ACTIVITY_FOCUS_TIMEOUT_MS, componentName, message);
    }

    protected void assertActivityNotFocused(ComponentName componentName, String message) {
        assertActivityNotFocused(ACTIVITY_FOCUS_TIMEOUT_MS, componentName, message);
    }

    /** Asserts the activity is focused before timeout. */
    protected void assertActivityFocused(int timeoutMs, ComponentName componentName) {
        assertActivityFocused(timeoutMs, componentName,
                "activity should be focused within " + timeoutMs + "ms");
    }

    /** Asserts the activity is not focused until timeout. */
    protected void assertActivityNotFocused(int timeoutMs, ComponentName componentName) {
        assertActivityNotFocused(timeoutMs, componentName,
                "activity should not be focused within " + timeoutMs + "ms");
    }

    /** Asserts the activity is focused before timeout. */
    protected void assertActivityFocused(int timeoutMs, ComponentName componentName,
            String message) {
        waitForActivityResumed(timeoutMs, componentName);
        assertWithMessage(message).that(mWmState.getFocusedActivity()).isEqualTo(
                getActivityName(componentName));
    }

    /** Asserts the activity is not focused until timeout. */
    protected void assertActivityNotFocused(int timeoutMs, ComponentName componentName,
            String message) {
        waitForActivityResumed(timeoutMs, componentName);
        assertWithMessage(message).that(mWmState.getFocusedActivity())
                .isNotEqualTo(getActivityName(componentName));
    }

    protected ITestService getTestService(Components c) throws Exception {
        return getTestService(new ComponentName(c.APP_PACKAGE_NAME, TEST_SERVICE));
    }

    private ITestService getTestService(ComponentName componentName) throws Exception {
        FutureConnection<ITestService> futureConnection = mServiceConnections.get(componentName);
        if (futureConnection == null) {
            // need to setup new test service connection for the component
            Intent bindIntent = new Intent();
            bindIntent.setComponent(componentName);
            futureConnection = new FutureConnection<>(ITestService.Stub::asInterface);
            mServiceConnections.put(componentName, futureConnection);
            boolean success = mContext.bindService(bindIntent, futureConnection,
                    Context.BIND_AUTO_CREATE);
            assertTrue("Failed to setup " + componentName.toString(), success);
        }
        return futureConnection.get(TEST_SERVICE_SETUP_TIMEOUT_MS);
    }

}
