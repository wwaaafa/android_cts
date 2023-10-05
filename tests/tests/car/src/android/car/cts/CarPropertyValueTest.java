/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;

import android.car.Car;
import android.car.VehicleAreaType;
import android.car.cts.utils.ShellPermissionUtils;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.PropertyAccessDeniedSecurityException;
import android.car.test.ApiCheckerRule.Builder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.util.SparseArray;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.Test.None;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot get car related permissions.")
public final class CarPropertyValueTest extends AbstractCarTestCase {

    private static final String TAG = CarPropertyValueTest.class.getSimpleName();

    private final List<CarPropertyValue> mCarPropertyValues = new ArrayList<>();
    private final SparseArray<CarPropertyConfig> mPropIdToConfig = new SparseArray<>();

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() throws Exception {
        CarPropertyManager carPropertyManager = (CarPropertyManager) getCar().getCarManager(
                Car.PROPERTY_SERVICE);
        ShellPermissionUtils.runWithShellPermissionIdentity(() -> {
            List<CarPropertyConfig> configs = carPropertyManager.getPropertyList();
            for (CarPropertyConfig cfg : configs) {
                mPropIdToConfig.put(cfg.getPropertyId(), cfg);
                if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                        || cfg.getAccess()
                        == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                    if (cfg.isGlobalProperty()) {
                        CarPropertyValue value = getCarPropertyValue(carPropertyManager,
                                cfg, /*areaId=*/0);
                        if (value != null) {
                            Assert.assertEquals(value.getPropertyId(), cfg.getPropertyId());
                            mCarPropertyValues.add(value);
                        }
                    } else {
                        for (int areaId : cfg.getAreaIds()) {
                            CarPropertyValue value = getCarPropertyValue(carPropertyManager, cfg,
                                    areaId);
                            if (value != null) {
                                Assert.assertEquals(value.getPropertyId(), cfg.getPropertyId());
                                mCarPropertyValues.add(value);
                            }
                        }
                    }
                }
            }
        });
        assertThat(mPropIdToConfig.size()).isAtLeast(4);
        assertThat(mCarPropertyValues.size()).isAtLeast(4);
    }

    private CarPropertyValue<?> getCarPropertyValue(CarPropertyManager carPropertyManager,
            CarPropertyConfig<?> carPropertyConfig, int areaId) {
        try {
            return carPropertyManager.getProperty(carPropertyConfig.getPropertyType(),
                    carPropertyConfig.getPropertyId(), areaId);
        } catch (IllegalStateException | CarInternalErrorException
                | PropertyAccessDeniedSecurityException | IllegalArgumentException e) {
            return null;
        }
    }

    @Test
    public void testGetPropertyId() {
        for (CarPropertyValue propertyValue : mCarPropertyValues) {
            int propId = propertyValue.getPropertyId();
            CarPropertyConfig cfg = mPropIdToConfig.get(propId);
            Assert.assertNotNull(cfg);
        }
    }

    @Test
    public void testGetPropertyAreaId() {
        for (CarPropertyValue propertyValue : mCarPropertyValues) {
            int areaId = propertyValue.getAreaId();
            CarPropertyConfig cfg = mPropIdToConfig.get(propertyValue.getPropertyId());
            if (cfg.isGlobalProperty()) {
                Assert.assertEquals(areaId, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
            } else {
                List<Integer> expectedAreaIds = new ArrayList<>();
                for (int area : cfg.getAreaIds()) {
                    expectedAreaIds.add(area);
                }
                assertThat(expectedAreaIds).contains(areaId);
            }
        }
    }

    @Test(expected = None.class /* no exception expected*/)
    public void testGetPropertyTimestamp() {
        for (CarPropertyValue propertyValue : mCarPropertyValues) {
            propertyValue.getTimestamp();
        }
    }

    @Test
    public void testGetPropertyStatus() {
        List<Integer> expectedStatus = Arrays.asList(
                CarPropertyValue.STATUS_AVAILABLE,
                CarPropertyValue.STATUS_ERROR,
                CarPropertyValue.STATUS_UNAVAILABLE);
        for (CarPropertyValue propertyValue : mCarPropertyValues) {
            int status = propertyValue.getStatus();
            assertThat(expectedStatus).contains(status);
        }
    }

    @Test
    public void testGetValue() {
        for (CarPropertyValue propertyValue : mCarPropertyValues) {
            Assert.assertNotNull(propertyValue.getValue());
        }
    }
}
