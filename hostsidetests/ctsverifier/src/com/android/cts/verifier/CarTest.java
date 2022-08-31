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

package com.android.cts.verifier;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;
import com.android.interactive.annotations.Interactive;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public final class CarTest extends CtsVerifierTest {

    @Interactive
    @Test
    // MultiDisplayMode
    public void CarDockTest() throws Exception {
        excludeFeatures("android.hardware.type.television",
                "android.software.leanback",
                "android.hardware.type.watch",
                "android.hardware.type.automotive");

        runTest(".car.CarDockTestActivity");
    }

    @Interactive
    @Test
    // MultiDisplayMode
    public void PowerPolicyTest() throws Exception {
        requireFeatures("android.hardware.type.automotive");

        runTest(".car.PowerPolicyTestActivity");
    }

    @Interactive
    @Test
    // MultiDisplayMode
    public void GearSelectionTest() throws Exception {
        requireFeatures("android.hardware.type.automotive");

        runTest(".car.GearSelectionTestActivity");
    }

    @Interactive
    @Test
    // MultiDisplayMode
    public void ParkingBrakeOnTest() throws Exception {
        requireFeatures("android.hardware.type.automotive");

        runTest(".car.ParkingBrakeOnTestActivity");
    }

    @Interactive
    @Test
    // MultiDisplayMode
    @ApiTest(apis = "android.car.settings.CarSettings.Secure#KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE")
    public void CarLauncherTest() throws Exception {
        requireFeatures("android.hardware.type.automotive");

        runTest(".car.CarLauncherTestActivity");
    }

    @Interactive
    @Test
    // MultiDisplayMode
    @CddTest(requirements = "8.3/A-1-3|8.3/A-1-4")
    public void GarageModeTest() throws Exception {
        requireFeatures("android.hardware.type.automotive");

        runTest(".car.GarageModeTestActivity");
    }
}
