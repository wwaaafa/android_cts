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

import static android.server.wm.backgroundactivity.common.CommonComponents.COMMON_FOREGROUND_ACTIVITY_EXTRAS;

import org.junit.Ignore;
import org.junit.Test;

public class ActivitySecurityModelTest extends BackgroundActivityTestBase {
    /*
     * Targets: A(curr), B(curr)
     * Setup: A B | (bottom -- top)
     * Launcher: B
     * Started: A
     */
    @Test
    public void testTopLaunchesActivity_launchAllowed() {
        new ActivityStartVerifier()
                .setupTaskWithForegroundActivity(APP_A)
                .startFromForegroundActivity(APP_A)
                .activity(APP_B.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);

        // Current State: A B | (bottom -- top)
        // Test - B launches A - succeeds
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_B)
                .activity(APP_A.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);
    }

    /*
     * Targets: A(curr), B(curr)
     * Setup: A B | (bottom -- top)
     * Launcher: A
     * Started: A
     */
    @Test
    public void testActivitySandwich_launchBlocked() {
        new ActivityStartVerifier()
                .setupTaskWithForegroundActivity(APP_A)
                .startFromForegroundActivity(APP_A)
                .activity(APP_B.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);

        // Current State: A B
        // Test - A launches A - fails
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_A)
                .activity(APP_A.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);
    }

    /*
     * Targets: A(curr), B(33)
     * Setup: A B | (bottom -- top)
     * Launcher: A
     * Started: A
     */
    @Test
    @Ignore(value = "Enable when ag/20721147 merges")
    public void testActivitySandwich_started33_launchAllowed() {
        new ActivityStartVerifier()
                .setupTaskWithForegroundActivity(APP_A)
                .startFromForegroundActivity(APP_A)
                .activity(APP_B_33.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_B_33.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);

        // Current State: A B | (bottom -- top)
        // Test - A launches A - succeeds
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_A)
                .activity(APP_A.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B_33.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);
    }

    /*
     * Targets: A(33), B(curr)
     * Setup: A B | (bottom -- top)
     * Launcher: A
     * Started: A
     */
    @Test
    public void testActivitySandwich_launcher33_launchAllowed() {
        new ActivityStartVerifier()
                .setupTaskWithForegroundActivity(APP_A_33)
                .startFromForegroundActivity(APP_A_33)
                .activity(APP_B.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A_33.FOREGROUND_ACTIVITY);

        // Current State: A B | (bottom -- top)
        // Test - A launches A - succeeds
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_A_33)
                .activity(APP_A_33.FOREGROUND_ACTIVITY)
                .execute()
                .thenAssertTaskStack(
                        APP_A_33.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A_33.FOREGROUND_ACTIVITY);
    }

    /*
     * Targets: A(curr), B(curr)
     * Setup: A1 B1 A2 | (bottom -- top)
     * Launcher: A1
     * Started: A3
     */
    @Test
    public void testTopUidButNonTopActivity_launchAllowed() {
        new ActivityStartVerifier()
                .setupTaskWithForegroundActivity(APP_A, 1)
                .startFromForegroundActivity(APP_A, 1)
                .activity(APP_B.FOREGROUND_ACTIVITY, 1)
                .execute()
                .startFromForegroundActivity(APP_B, 1)
                .activity(APP_A.FOREGROUND_ACTIVITY, 2)
                .execute()
                .thenAssertTaskStack(
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);

        // Current State: A1 B1 A2 | (bottom -- top)
        // Test - A1 launches A3 - succeeds
        new ActivityStartVerifier()
                .startFromForegroundActivity(APP_A, 1)
                .activity(APP_A.FOREGROUND_ACTIVITY, 3)
                .execute()
                .thenAssertTaskStack(
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY,
                        APP_B.FOREGROUND_ACTIVITY,
                        APP_A.FOREGROUND_ACTIVITY);
    }

    /*
     * Targets: A(curr)
     * Setup: A
     * Launcher: A
     * Started: A
     */
    @Test
    public void testTopFinishesThenLaunchesActivity_launchAllowed() {
        new ActivityStartVerifier()
                .setupTaskWithForegroundActivity(APP_A)
                .thenAssertTaskStack(APP_A.FOREGROUND_ACTIVITY)
                // Current State: A
                // Test - A finishes, then launches A - succeeds
                .startFromForegroundActivity(APP_A)
                .withBroadcastExtra(COMMON_FOREGROUND_ACTIVITY_EXTRAS.FINISH_FIRST, true)
                .activity(APP_A.BACKGROUND_ACTIVITY)
                .execute()
                .thenAssert(() -> mWmState.waitAndAssertActivityRemoved(APP_A.FOREGROUND_ACTIVITY))
                .thenAssertTaskStack(
                        APP_A.BACKGROUND_ACTIVITY);
    }
}
