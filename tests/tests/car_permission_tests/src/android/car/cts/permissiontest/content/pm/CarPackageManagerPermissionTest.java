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
package android.car.cts.permissiontest.content.pm;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.car.Car;
import android.car.content.pm.CarAppBlockingPolicy;
import android.car.content.pm.CarPackageManager;
import android.content.ComponentName;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import android.car.cts.permissiontest.AbstractCarManagerPermissionTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class contains security permission tests for {@link CarPackageManager}.
 */
@RunWith(AndroidJUnit4.class)
public class CarPackageManagerPermissionTest extends AbstractCarManagerPermissionTest {
    private CarPackageManager mPm;

    @Before
    public void setUp() throws Exception {
        super.connectCar();
        mPm = (CarPackageManager) mCar.getCarManager(Car.PACKAGE_SERVICE);
    }

    @Test
    public void testRestartTask() {
        assertThrows(SecurityException.class, () -> mPm.restartTask(0));
    }

    @Test
    public void testSetAppBlockingPolicy() {
        String packageName = "com.android";
        CarAppBlockingPolicy policy = new CarAppBlockingPolicy(null, null);
        assertThrows(SecurityException.class, () -> mPm.setAppBlockingPolicy(packageName, policy,
                0));
    }

    @Test
    public void testIsActivityDistractionOptimized() {
        assertThat(mPm.isActivityDistractionOptimized("blah", "someClass")).isFalse();
    }

    @Test
    public void testIsServiceDistractionOptimized() {
        assertThat(mPm.isServiceDistractionOptimized("blah", "someClass")).isFalse();
    }

    @Test
    public void testIsActivityBackedBySafeActivity() {
        assertThat(mPm.isActivityBackedBySafeActivity(new ComponentName("blah", "someClass")))
                .isFalse();
    }

    @Test
    public void testGetTargetCarApiVersion() {
        assertThrows(SecurityException.class, () -> mPm.getTargetCarVersion("Y U NO THROW?"));
    }
}
