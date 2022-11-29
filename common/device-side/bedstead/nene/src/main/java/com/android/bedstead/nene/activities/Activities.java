/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.bedstead.nene.activities;

import static android.Manifest.permission.REAL_GET_TASKS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_ASSISTANT;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_DREAM;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_RECENTS;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_STANDARD;
import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.os.Build.VERSION_CODES.Q;

import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_ACTIVITY_TASKS;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.app.ActivityTaskManager;
import android.content.ComponentName;
import android.view.Display;

import androidx.annotation.Nullable;

import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.annotations.Experimental;
import com.android.bedstead.nene.exceptions.AdbException;
import com.android.bedstead.nene.exceptions.NeneException;
import com.android.bedstead.nene.packages.ComponentReference;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.utils.ShellCommand;
import com.android.bedstead.nene.utils.Versions;

import java.util.List;
import java.util.stream.Collectors;

public final class Activities {

    public static final Activities sInstance = new Activities();

    private Activities() {
    }


    /**
     * Wrap the given {@link NeneActivity} subclass to use Nene APIs.
     */
    public <E extends NeneActivity> Activity<E> wrap(Class<E> clazz, E activity) {
        return new Activity<>(activity, activity);
    }

    /**
     * Wrap the given {@link android.app.Activity} to use Nene APIs.
     */
    public LocalActivity wrap(android.app.Activity activity) {
        return new LocalActivity(activity);
    }

    /**
     * Get the {@link ComponentReference} instances for each activity at the top of a recent task.
     *
     * <p>This is ordered from most recent to least recent and only includes tasks on the
     * default display.
     */
    @Experimental
    @TargetApi(Q)
    public List<ComponentReference> recentActivities() {
        Versions.requireMinimumVersion(Q);

        try (PermissionContext p = TestApis.permissions().withPermission(REAL_GET_TASKS)) {
            ActivityManager activityManager =
                    TestApis.context().instrumentedContext().getSystemService(
                            ActivityManager.class);
            return activityManager.getRunningTasks(100).stream()
//                    .filter(r -> r.getDisplayId() == Display.DEFAULT_DISPLAY)
                    .map(r -> new ComponentReference(r.topActivity))
                    .collect(Collectors.toList());
        }
    }

    /**
     * Get the {@link ComponentReference} of the activity currently in the foreground of the default
     * display.
     */
    @Experimental
    @Nullable
    public ComponentReference foregroundActivity() {
        if (!Versions.meetsMinimumSdkVersionRequirement(Q)) {
            return foregroundActivityPreQ();
        }
        return recentActivities().stream().findFirst().orElse(null);
    }

    private ComponentReference foregroundActivityPreQ() {
        try {
            return ShellCommand.builder("dumpsys activity top")
                    .executeAndParseOutput((dumpsysOutput) -> {
                        // The final ACTIVITY is the one on top
                        String[] activitySplits = dumpsysOutput.split("ACTIVITY ");
                        String component = activitySplits[activitySplits.length - 1]
                                .split(" ", 2)[0];
                        ComponentName componentName = ComponentName.unflattenFromString(component);
                        return new ComponentReference(componentName);
                    });
        } catch (AdbException | RuntimeException e) {
            throw new NeneException("Error getting foreground activity pre Q", e);
        }
    }

    /**
     * Return the current state of task locking. The three possible outcomes
     * are {@link ActivityManager#LOCK_TASK_MODE_NONE},
     * {@link ActivityManager#LOCK_TASK_MODE_LOCKED}
     * and {@link ActivityManager#LOCK_TASK_MODE_PINNED}.
     */
    @Experimental
    public int getLockTaskModeState() {
        ActivityManager activityManager =
                TestApis.context().instrumentedContext().getSystemService(
                        ActivityManager.class);

        return activityManager.getLockTaskModeState();
    }

    private final int[] ALL_ACTIVITY_TYPE_BUT_HOME = {
            ACTIVITY_TYPE_STANDARD, ACTIVITY_TYPE_ASSISTANT, ACTIVITY_TYPE_RECENTS,
            ACTIVITY_TYPE_DREAM, ACTIVITY_TYPE_UNDEFINED
    };

    /**
     * Clear activities.
     */
    @Experimental
    public void clearAllActivities() {
        try (PermissionContext p = TestApis.permissions().withPermission(MANAGE_ACTIVITY_TASKS)) {
            TestApis.context().instrumentedContext().getSystemService(ActivityTaskManager.class)
                    .removeRootTasksWithActivityTypes(ALL_ACTIVITY_TYPE_BUT_HOME);
        }
    }
}
