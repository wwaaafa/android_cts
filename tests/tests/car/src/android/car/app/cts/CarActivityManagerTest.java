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
package android.car.app.cts;

import static android.car.cts.utils.DisplayUtils.VirtualDisplaySession;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.app.CarActivityManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.Display;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(JUnit4.class)
public class CarActivityManagerTest {

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER
    private static final int FEATURE_DEFAULT_TASK_CONTAINER = 1;

    // Comes from android.window.DisplayAreaOrganizer.FEATURE_UNDEFINED
    private static final int FEATURE_UNDEFINED = -1;

    private static final long QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE = 1000;  // ms

    private static final long TOTAL_TIME_TO_WAIT_FOR_IDLE_STATE = 10_000;  // ms

    private final Context mContext = InstrumentationRegistry.getContext();
    private final Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context mTargetContext = mInstrumentation.getTargetContext();
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();
    private final ComponentName mTestActivity =
            new ComponentName(mTargetContext, TestActivity.class);
    private final ComponentName mFakedTestActivity = new ComponentName("fake_pkg", "fake_class");

    private CarActivityManager mCarActivityManager;

    @Before
    public void setUp() {
        Car car = Car.createCar(mContext);
        mUiAutomation.adoptShellPermissionIdentity(
                Car.PERMISSION_CONTROL_CAR_APP_LAUNCH,  // for CAM.setPersistentActivity
                // to launch an Activity in the virtual display
                Manifest.permission.ACTIVITY_EMBEDDING);
        mCarActivityManager =
            (CarActivityManager) car.getCarManager(Car.CAR_ACTIVITY_SERVICE);
        assertThat(mCarActivityManager).isNotNull();
    }

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetPersistentActivity() throws Exception {
        try (VirtualDisplaySession session = new VirtualDisplaySession()) {
            // create a secondary virtual display
            Display secondaryDisplay =
                    session.createDisplayWithDefaultDisplayMetricsAndWait(mContext, true);
            assertThat(secondaryDisplay).isNotNull();
            int secondaryDisplayId = secondaryDisplay.getDisplayId();

            // Need some delay to propagate the virtual display to ActivityTaskManager internal.
            // Without this, setPersistentActivity() would fail or cause the system crash.
            SystemClock.sleep(100);

            // assign the activity to the secondary virtual display
            int ret = mCarActivityManager.setPersistentActivity(mTestActivity,
                    secondaryDisplayId, FEATURE_DEFAULT_TASK_CONTAINER);
            assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

            // launch the activity
            Intent startIntent = Intent.makeMainActivity(mTestActivity)
                    .addFlags(FLAG_ACTIVITY_NEW_TASK);
            TestActivity activity = (TestActivity) mInstrumentation.startActivitySync(
                    startIntent, /* option */ null);

            // assert the activity is launched into the virtual display
            assertThat(activity.getDisplay().getDisplayId()).isEqualTo(secondaryDisplayId);

            // destroy the newly created activity and remove the launch display area assignment
            activity.finishAndRemoveTask();

            // Wait for the Activity is completely removed.
            assertThat(activity.waitForDestroyed()).isTrue();

            ret = mCarActivityManager.setPersistentActivity(mTestActivity,
                    secondaryDisplayId, FEATURE_UNDEFINED);
            assertThat(ret).isEqualTo(CarActivityManager.RESULT_SUCCESS);

            // re-launch again and assert that it is not launched in the secondaryDisplay
            activity = (TestActivity) mInstrumentation.startActivitySync(
                    startIntent, /* option */ null);
            assertThat(activity.getDisplay().getDisplayId()).isEqualTo(DEFAULT_DISPLAY);

            // tear down
            activity.finishAndRemoveTask();
        }
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForInvalidDisplayId() {
        int invalidDisplayId = 999999990;

        assertThrows(IllegalArgumentException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mFakedTestActivity, invalidDisplayId, FEATURE_DEFAULT_TASK_CONTAINER));
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForInvalidFeatureId() {
        int unknownFeatureId = 999999990;

        assertThrows(IllegalArgumentException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mFakedTestActivity, Display.DEFAULT_DISPLAY, unknownFeatureId));
    }

    @Test
    public void testSetPersistentActivity_throwsExceptionForUnregsiteringUnknownActivity() {
        // Tries to remove the Activity without registering it.
        assertThrows(ActivityNotFoundException.class,
                () -> mCarActivityManager.setPersistentActivity(
                        mFakedTestActivity, Display.DEFAULT_DISPLAY, FEATURE_UNDEFINED));
    }

    public static final class TestActivity extends Activity {
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(QUIET_TIME_TO_BE_CONSIDERED_IDLE_STATE, TimeUnit.MILLISECONDS);
        }
    }
}
