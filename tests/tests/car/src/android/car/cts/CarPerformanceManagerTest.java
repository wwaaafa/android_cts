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
package android.car.cts;

import static android.os.Process.getThreadPriority;
import static android.os.Process.setThreadPriority;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.app.UiAutomation;
import android.car.Car;
import android.car.os.CarPerformanceManager;
import android.car.os.ThreadPolicyWithPriority;
import android.platform.test.annotations.AppModeFull;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SmallTest
@AppModeFull(reason = "Instant Apps cannot get car related permissions")
public class CarPerformanceManagerTest extends CarApiTestBase {

    private UiAutomation mUiAutomation;
    private CarPerformanceManager mCarPerformanceManager;
    private ThreadPolicyWithPriority mOriginalPolicyWithPriority;

    private boolean isApiAndPlatformSupported() {
        return Car.isApiAndPlatformVersionAtLeast(
                /* requiredApiVersionMajor= */ 33, /* requiredApiVersionMinor= */ 1,
                /* minPlatformSdkInt= */ 33) && Car.PLATFORM_VERSION_MINOR_INT >= 1;
    }

    private void setThreadPriorityGotThreadPriorityVerify(ThreadPolicyWithPriority p)
            throws Exception {
        assumeTrue("Thread management is not supported", isApiAndPlatformSupported());
        mCarPerformanceManager.setThreadPriority(p);

        ThreadPolicyWithPriority gotP = mCarPerformanceManager.getThreadPriority();

        assertThat(gotP.getPolicy()).isEqualTo(p.getPolicy());
        assertThat(gotP.getPriority()).isEqualTo(p.getPriority());
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_THREAD_PRIORITY);

        mCarPerformanceManager = (CarPerformanceManager) getCar().getCarManager(
                Car.CAR_PERFORMANCE_SERVICE);
        assertThat(mCarPerformanceManager).isNotNull();

        if (isApiAndPlatformSupported()) {
            mOriginalPolicyWithPriority = mCarPerformanceManager.getThreadPriority();
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mOriginalPolicyWithPriority != null) {
            mCarPerformanceManager.setThreadPriority(mOriginalPolicyWithPriority);
        }

        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    public void testSetThreadPriorityDefault() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0));
    }

    @Test
    public void testSetThreadPriorityFIFOMinPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO,
                ThreadPolicyWithPriority.PRIORITY_MIN));
    }

    @Test
    public void testSetThreadPriorityFIFOMaxPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO,
                ThreadPolicyWithPriority.PRIORITY_MAX));
    }

    @Test
    public void testSetThreadPriorityRRMinPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_RR,
                ThreadPolicyWithPriority.PRIORITY_MIN));
    }

    @Test
    public void testSetThreadPriorityRRMaxPriority() throws Exception {
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_RR,
                ThreadPolicyWithPriority.PRIORITY_MAX));
    }

    @Test
    public void testSetThreadPriorityDefaultKeepNiceValue() throws Exception {
        assumeTrue("Thread management is not supported", isApiAndPlatformSupported());
        int expectedNiceValue = 10;

        // Resume the test scheduling policy to default policy.
        mCarPerformanceManager.setThreadPriority(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0));
        // Set a nice value for regular scheduling policy.
        setThreadPriority(expectedNiceValue);

        // Change the scheduling policy.
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_FIFO,
                ThreadPolicyWithPriority.PRIORITY_MIN));

        // Change it back, the nice value should be resumed.
        setThreadPriorityGotThreadPriorityVerify(new ThreadPolicyWithPriority(
                ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0));

        assertThat(getThreadPriority(/* tid= */ 0)).isEqualTo(expectedNiceValue);
    }
}
