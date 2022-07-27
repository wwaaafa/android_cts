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

package android.car.cts.utils;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNotNull;

import android.car.VehicleAreaMirror;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehicleAreaWindow;
import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.os.SystemClock;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VehiclePropertyVerifier<T> {
    private final static String CAR_PROPERTY_VALUE_SOURCE_GETTER = "Getter";
    private final static String CAR_PROPERTY_VALUE_SOURCE_CALLBACK = "Callback";
    private static final ImmutableSet<Integer> WHEEL_AREA_IDS = ImmutableSet.of(
            VehicleAreaWheel.WHEEL_LEFT_FRONT, VehicleAreaWheel.WHEEL_LEFT_REAR,
            VehicleAreaWheel.WHEEL_RIGHT_FRONT, VehicleAreaWheel.WHEEL_RIGHT_REAR);
    private static final ImmutableSet<Integer> WINDOW_AREA_IDS = ImmutableSet.of(
            VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD, VehicleAreaWindow.WINDOW_REAR_WINDSHIELD,
            VehicleAreaWindow.WINDOW_ROW_1_LEFT, VehicleAreaWindow.WINDOW_ROW_1_RIGHT,
            VehicleAreaWindow.WINDOW_ROW_2_LEFT, VehicleAreaWindow.WINDOW_ROW_2_RIGHT,
            VehicleAreaWindow.WINDOW_ROW_3_LEFT, VehicleAreaWindow.WINDOW_ROW_3_RIGHT,
            VehicleAreaWindow.WINDOW_ROOF_TOP_1, VehicleAreaWindow.WINDOW_ROOF_TOP_2);
    private static final ImmutableSet<Integer> MIRROR_AREA_IDS = ImmutableSet.of(
            VehicleAreaMirror.MIRROR_DRIVER_LEFT, VehicleAreaMirror.MIRROR_DRIVER_RIGHT,
            VehicleAreaMirror.MIRROR_DRIVER_CENTER);

    private final int mPropertyId;
    private final String mPropertyName;
    private final int mAccess;
    private final int mAreaType;
    private final int mChangeMode;
    private final Class<T> mPropertyType;
    private final boolean mRequiredProperty;
    private final Optional<ConfigArrayVerifier> mConfigArrayVerifier;
    private final Optional<CarPropertyValueVerifier> mCarPropertyValueVerifier;
    private final Optional<AreaIdsVerifier> mAreaIdsVerifier;
    private final ImmutableSet<Integer> mPossibleConfigArrayValues;
    private final ImmutableSet<Integer> mPossibleCarPropertyValues;
    private final boolean mRequirePropertyValueToBeInConfigArray;
    private final boolean mVerifySetterWithConfigArrayValues;
    private final boolean mRequireMinMaxValues;
    private final boolean mRequireMinValuesToBeZero;
    private final boolean mRequireZeroToBeContainedInMinMaxRanges;

    private VehiclePropertyVerifier(int propertyId, int access, int areaType, int changeMode,
            Class<T> propertyType, boolean requiredProperty,
            Optional<ConfigArrayVerifier> configArrayVerifier,
            Optional<CarPropertyValueVerifier> carPropertyValueVerifier,
            Optional<AreaIdsVerifier> areaIdsVerifier,
            ImmutableSet<Integer> possibleConfigArrayValues,
            ImmutableSet<Integer> possibleCarPropertyValues,
            boolean requirePropertyValueToBeInConfigArray,
            boolean verifySetterWithConfigArrayValues,
            boolean requireMinMaxValues,
            boolean requireMinValuesToBeZero,
            boolean requireZeroToBeContainedInMinMaxRanges) {
        mPropertyId = propertyId;
        mPropertyName = VehiclePropertyIds.toString(propertyId);
        mAccess = access;
        mAreaType = areaType;
        mChangeMode = changeMode;
        mPropertyType = propertyType;
        mRequiredProperty = requiredProperty;
        mConfigArrayVerifier = configArrayVerifier;
        mCarPropertyValueVerifier = carPropertyValueVerifier;
        mAreaIdsVerifier = areaIdsVerifier;
        mPossibleConfigArrayValues = possibleConfigArrayValues;
        mPossibleCarPropertyValues = possibleCarPropertyValues;
        mRequirePropertyValueToBeInConfigArray = requirePropertyValueToBeInConfigArray;
        mVerifySetterWithConfigArrayValues = verifySetterWithConfigArrayValues;
        mRequireMinMaxValues = requireMinMaxValues;
        mRequireMinValuesToBeZero = requireMinValuesToBeZero;
        mRequireZeroToBeContainedInMinMaxRanges = requireZeroToBeContainedInMinMaxRanges;
    }

    public static <T> Builder<T> newBuilder(int propertyId, int access, int areaType,
            int changeMode,
            Class<T> propertyType) {
        return new Builder<>(propertyId, access, areaType, changeMode, propertyType);
    }

    private static String accessToString(int access) {
        switch (access) {
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE:
                return "VEHICLE_PROPERTY_ACCESS_NONE";
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ:
                return "VEHICLE_PROPERTY_ACCESS_READ";
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE:
                return "VEHICLE_PROPERTY_ACCESS_WRITE";
            case CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE:
                return "VEHICLE_PROPERTY_ACCESS_READ_WRITE";
            default:
                return Integer.toString(access);
        }
    }

    private static String areaTypeToString(int areaType) {
        switch (areaType) {
            case VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL:
                return "VEHICLE_AREA_TYPE_GLOBAL";
            case VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW:
                return "VEHICLE_AREA_TYPE_WINDOW";
            case VehicleAreaType.VEHICLE_AREA_TYPE_DOOR:
                return "VEHICLE_AREA_TYPE_DOOR";
            case VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR:
                return "VEHICLE_AREA_TYPE_MIRROR";
            case VehicleAreaType.VEHICLE_AREA_TYPE_SEAT:
                return "VEHICLE_AREA_TYPE_SEAT";
            case VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL:
                return "VEHICLE_AREA_TYPE_WHEEL";
            default:
                return Integer.toString(areaType);
        }
    }

    private static String changeModeToString(int changeMode) {
        switch (changeMode) {
            case CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC:
                return "VEHICLE_PROPERTY_CHANGE_MODE_STATIC";
            case CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE:
                return "VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE";
            case CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS:
                return "VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS";
            default:
                return Integer.toString(changeMode);
        }
    }

    public void verify(CarPropertyManager carPropertyManager) {
        CarPropertyConfig<?> carPropertyConfig = carPropertyManager.getCarPropertyConfig(
                mPropertyId);
        if (mRequiredProperty) {
            assertWithMessage("Must support " + mPropertyName).that(carPropertyConfig)
                    .isNotNull();
        } else {
            assumeNotNull(carPropertyConfig);
        }

        verifyCarPropertyConfig(carPropertyConfig);
        verifyCarPropertyValueGetter(carPropertyConfig, carPropertyManager);
        verifyCarPropertyValueCallback(carPropertyConfig, carPropertyManager);
        verifyCarPropertyValueSetter(carPropertyConfig, carPropertyManager);
    }

    private void verifyCarPropertyValueSetter(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
            return;
        }
        if (Boolean.class.equals(carPropertyConfig.getPropertyType())) {
            verifyBooleanPropertySetter(carPropertyConfig, carPropertyManager);
        } else if (Integer.class.equals(carPropertyConfig.getPropertyType())) {
            verifyIntegerPropertySetter(carPropertyConfig, carPropertyManager);
        }
    }

    private void verifyBooleanPropertySetter(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        for (int areaId : carPropertyConfig.getAreaIds()) {
            CarPropertyValue<Boolean> currentCarPropertyValue = carPropertyManager.getProperty(
                    mPropertyId, areaId);
            verifyCarPropertyValue(carPropertyConfig, currentCarPropertyValue, areaId,
                    CAR_PROPERTY_VALUE_SOURCE_GETTER);
            Boolean valueToSet = !currentCarPropertyValue.getValue();
            verifySetProperty((CarPropertyConfig<Boolean>) carPropertyConfig, carPropertyManager,
                    areaId, valueToSet);
            verifySetProperty((CarPropertyConfig<Boolean>) carPropertyConfig, carPropertyManager,
                    areaId, !valueToSet);
        }
    }

    private void verifyIntegerPropertySetter(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        if (mVerifySetterWithConfigArrayValues) {
            verifySetterWithConfigArrayValues(carPropertyConfig, carPropertyManager);
        } else if (!mPossibleCarPropertyValues.isEmpty()) {
            verifySetterWithPossibleCarPropertyValues(carPropertyConfig, carPropertyManager);
        } else {
            verifySetterWithMinMaxValues(carPropertyConfig, carPropertyManager);
        }
    }

    private void verifySetterWithConfigArrayValues(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        verifySetterWithIntegerValues(carPropertyConfig, carPropertyManager,
                carPropertyConfig.getConfigArray());
    }

    private void verifySetterWithPossibleCarPropertyValues(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        verifySetterWithIntegerValues(carPropertyConfig, carPropertyManager,
                mPossibleCarPropertyValues);
    }

    private void verifySetterWithIntegerValues(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager, Collection<Integer> valuesToSet) {
        for (Integer valueToSet : valuesToSet) {
            for (int areaId : carPropertyConfig.getAreaIds()) {
                verifyIntegerSetProperty(carPropertyConfig, carPropertyManager, areaId, valueToSet);
            }
        }
    }

    private void verifySetterWithMinMaxValues(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (carPropertyConfig.getMinValue(areaId) == null || carPropertyConfig.getMinValue(
                    areaId) == null) {
                continue;
            }
            List<Integer> valuesToSet = IntStream.rangeClosed(
                    ((Integer) carPropertyConfig.getMinValue(areaId)).intValue(),
                    ((Integer) carPropertyConfig.getMaxValue(areaId)).intValue()).boxed().collect(
                    Collectors.toList());

            for (Integer valueToSet : valuesToSet) {
                verifyIntegerSetProperty(carPropertyConfig, carPropertyManager, areaId, valueToSet);
            }
        }
    }

    private void verifyIntegerSetProperty(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager, int areaId, Integer valueToSet) {
        CarPropertyValue<Integer> currentCarPropertyValue = carPropertyManager.getProperty(
                mPropertyId, areaId);
        verifyCarPropertyValue(carPropertyConfig, currentCarPropertyValue, areaId,
                CAR_PROPERTY_VALUE_SOURCE_GETTER);
        if (currentCarPropertyValue.getValue().equals(valueToSet)) {
            return;
        }
        verifySetProperty((CarPropertyConfig<Integer>) carPropertyConfig, carPropertyManager,
                areaId, valueToSet);
    }

    private <T> void verifySetProperty(CarPropertyConfig<T> carPropertyConfig,
            CarPropertyManager carPropertyManager, int areaId, T valueToSet) {
        SetterCallback setterCallback = new SetterCallback(mPropertyId, mPropertyName, areaId,
                valueToSet);
        assertWithMessage("Failed to register setter callback for " + mPropertyName).that(
                carPropertyManager.registerCallback(setterCallback, mPropertyId,
                        CarPropertyManager.SENSOR_RATE_FASTEST)).isTrue();
        carPropertyManager.setProperty(carPropertyConfig.getPropertyType(), mPropertyId, areaId,
                valueToSet);
        CarPropertyValue<?> updatedCarPropertyValue =
                setterCallback.waitForUpdatedCarPropertyValue();
        verifyCarPropertyValue(carPropertyConfig, updatedCarPropertyValue, areaId,
                CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
        carPropertyManager.unregisterCallback(setterCallback, mPropertyId);
    }

    private void verifyCarPropertyValueCallback(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC
                || mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE) {
            CarPropertyValueCallback carPropertyValueCallback =
                    new CarPropertyValueCallback(mPropertyName,
                            carPropertyConfig.getAreaIds().length);
            assertWithMessage("Failed to register callback for " + mPropertyName).that(
                    carPropertyManager.registerCallback(carPropertyValueCallback, mPropertyId,
                            CarPropertyManager.SENSOR_RATE_ONCHANGE)).isTrue();
            List<CarPropertyValue<?>> carPropertyValues =
                    carPropertyValueCallback.getCarPropertyValues();
            carPropertyManager.unregisterCallback(carPropertyValueCallback, mPropertyId);

            for (CarPropertyValue<?> carPropertyValue : carPropertyValues) {
                verifyCarPropertyValue(carPropertyConfig, carPropertyValue,
                        carPropertyValue.getAreaId(), CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
            }
            assertWithMessage(mPropertyName
                    + " callback values did not cover all the property's area IDs").that(
                    carPropertyValues.stream().map(CarPropertyValue::getAreaId).collect(
                            Collectors.toList())
            ).containsExactlyElementsIn(
                    Arrays.stream(carPropertyConfig.getAreaIds()).boxed().collect(
                            Collectors.toList()));

        } else if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            int updatesPerAreaId = 2;
            int minimumTotalUpdates = updatesPerAreaId * carPropertyConfig.getAreaIds().length;
            float secondsToMillis = 1_000;
            long bufferMillis = 1_000; // 1 second
            long timeoutMillis =
                    ((long) ((1.0f / carPropertyConfig.getMinSampleRate()) * secondsToMillis
                            * minimumTotalUpdates)) + bufferMillis;
            CarPropertyValueCallback carPropertyValueCallback = new CarPropertyValueCallback(
                    mPropertyName, minimumTotalUpdates, timeoutMillis);
            assertWithMessage("Failed to register callback for " + mPropertyName).that(
                    carPropertyManager.registerCallback(carPropertyValueCallback, mPropertyId,
                            carPropertyConfig.getMaxSampleRate())).isTrue();
            List<CarPropertyValue<?>> carPropertyValues =
                    carPropertyValueCallback.getCarPropertyValues();
            carPropertyManager.unregisterCallback(carPropertyValueCallback, mPropertyId);

            for (CarPropertyValue<?> carPropertyValue : carPropertyValues) {
                verifyCarPropertyValue(carPropertyConfig, carPropertyValue,
                        carPropertyValue.getAreaId(), CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
            }

            for (int areaId : carPropertyConfig.getAreaIds()) {
                assertWithMessage(
                        mPropertyName + " callback values did not receive " + updatesPerAreaId
                                + " updates for area ID: " + areaId).that(
                        carPropertyValues.stream().filter(
                                carPropertyValue -> carPropertyValue.getAreaId()
                                        == areaId).count()).isAtLeast(updatesPerAreaId);
            }
        }
    }

    private void verifyCarPropertyConfig(CarPropertyConfig<?> carPropertyConfig) {
        assertWithMessage(mPropertyName + " CarPropertyConfig must have correct property ID")
                .that(carPropertyConfig.getPropertyId())
                .isEqualTo(mPropertyId);
        if (mAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
            assertWithMessage(mPropertyName + " must be " + accessToString(
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) + ", " + accessToString(
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) + ", or " + accessToString(
                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE))
                    .that(carPropertyConfig.getAccess())
                    .isIn(ImmutableSet.of(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE));
        } else {
            assertWithMessage(mPropertyName + " must be " + accessToString(mAccess))
                    .that(carPropertyConfig.getAccess()).isEqualTo(mAccess);
        }
        assertWithMessage(mPropertyName + " must be " + areaTypeToString(mAreaType))
                .that(carPropertyConfig.getAreaType())
                .isEqualTo(mAreaType);
        assertWithMessage(mPropertyName + " must be " + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getChangeMode())
                .isEqualTo(mChangeMode);
        assertWithMessage(mPropertyName + " must be " + mPropertyType + " type property")
                .that(carPropertyConfig.getPropertyType()).isEqualTo(mPropertyType);

        assertWithMessage(mPropertyName + "'s must have at least 1 area ID defined").that(
                carPropertyConfig.getAreaIds().length).isAtLeast(1);
        assertWithMessage(mPropertyName + "'s area IDs must all be unique: " + Arrays.toString(
                carPropertyConfig.getAreaIds())).that(ImmutableSet.copyOf(Arrays.stream(
                carPropertyConfig.getAreaIds()).boxed().collect(Collectors.toList())).size()
                == carPropertyConfig.getAreaIds().length).isTrue();
        if (mAreaIdsVerifier.isPresent()) {
            mAreaIdsVerifier.get().verify(carPropertyConfig.getAreaIds());
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL) {
            assertWithMessage(
                    mPropertyName + "'s AreaIds must contain a single 0 since it is "
                            + areaTypeToString(mAreaType))
                    .that(carPropertyConfig.getAreaIds()).isEqualTo(new int[]{0});
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL) {
            for (int areaId : carPropertyConfig.getAreaIds()) {
                assertWithMessage(mPropertyName + "'s area ID: " + areaId
                        + " must be a valid VehicleAreaWheel area ID").that(
                        WHEEL_AREA_IDS.contains(areaId)).isTrue();
            }
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW) {
            for (int areaId : carPropertyConfig.getAreaIds()) {
                assertWithMessage(mPropertyName
                        + "'s area ID must be a valid VehicleAreaWindow area ID").that(areaId).isIn(
                        WINDOW_AREA_IDS);
            }
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR) {
            for (int areaId : carPropertyConfig.getAreaIds()) {
                assertWithMessage(mPropertyName
                        + "'s area ID must be a valid VehicleAreaMirror area ID").that(areaId).isIn(
                        MIRROR_AREA_IDS);
            }
        }

        if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            verifyContinuousCarPropertyConfig(carPropertyConfig);
        } else {
            verifyNonContinuousCarPropertyConfig(carPropertyConfig);
        }

        if (!mPossibleConfigArrayValues.isEmpty()) {
            assertWithMessage(
                    mPropertyName + " configArray must specify supported values")
                    .that(carPropertyConfig.getConfigArray().size())
                    .isGreaterThan(0);
            for (Integer supportedValue : carPropertyConfig.getConfigArray()) {
                assertWithMessage(
                        mPropertyName + " configArray value must be a defined "
                                + "value: "
                                + supportedValue).that(
                        supportedValue).isIn(mPossibleConfigArrayValues);
            }
        }

        mConfigArrayVerifier.ifPresent(configArrayVerifier -> configArrayVerifier.verify(
                carPropertyConfig.getConfigArray()));

        if (mPossibleConfigArrayValues.isEmpty() && !mConfigArrayVerifier.isPresent()) {
            assertWithMessage(mPropertyName + " configArray is undefined, so it must be empty")
                    .that(carPropertyConfig.getConfigArray().size()).isEqualTo(0);
        }

        for (int areaId : carPropertyConfig.getAreaIds()) {
            T areaIdMinValue = (T) carPropertyConfig.getMinValue(areaId);
            T areaIdMaxValue = (T) carPropertyConfig.getMaxValue(areaId);
            if (mRequireMinMaxValues) {
                assertWithMessage(mPropertyName + " - area ID: " + areaId
                        + " must have min value defined").that(areaIdMinValue).isNotNull();
                assertWithMessage(mPropertyName + " - area ID: " + areaId
                        + " must have max value defined").that(areaIdMaxValue).isNotNull();
            }
            if (mRequireMinValuesToBeZero) {
                assertWithMessage(
                        mPropertyName + " - area ID: " + areaId + " min value must be zero").that(
                        areaIdMinValue).isEqualTo(0);
            }
            if (mRequireZeroToBeContainedInMinMaxRanges) {
                assertWithMessage(mPropertyName + " - areaId: " + areaId
                        + "'s max and min range must contain zero").that(
                        verifyMaxAndMinRangeContainsZero(areaIdMinValue, areaIdMaxValue)).isTrue();

            }
            if (areaIdMinValue == null || areaIdMaxValue == null) {
                continue;
            }
            assertWithMessage(
                    mPropertyName + " - areaId: " + areaId + "'s max value must be >= min value")
                    .that(verifyMaxAndMin(areaIdMinValue, areaIdMaxValue)).isTrue();
        }
    }

    private boolean verifyMaxAndMinRangeContainsZero(T min, T max) {
        int propertyType = mPropertyId & VehiclePropertyType.MASK;
        switch (propertyType) {
            case VehiclePropertyType.INT32:
                return (Integer) max >= 0 && (Integer) min <= 0;
            case VehiclePropertyType.INT64:
                return (Long) max >= 0 && (Long) min <= 0;
            case VehiclePropertyType.FLOAT:
                return (Float) max >= 0 && (Float) min <= 0;
            default:
                return false;
        }
    }

    private boolean verifyMaxAndMin(T min, T max) {
        int propertyType = mPropertyId & VehiclePropertyType.MASK;
        switch (propertyType) {
            case VehiclePropertyType.INT32:
                return (Integer) max >= (Integer) min;
            case VehiclePropertyType.INT64:
                return (Long) max >= (Long) min;
            case VehiclePropertyType.FLOAT:
                return (Float) max >= (Float) min;
            default:
                return false;
        }
    }

    private void verifyContinuousCarPropertyConfig(CarPropertyConfig<?> carPropertyConfig) {
        assertWithMessage(
                mPropertyName + " must define max sample rate since change mode is "
                        + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMaxSampleRate()).isGreaterThan(0);
        assertWithMessage(
                mPropertyName + " must define min sample rate since change mode is "
                        + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMinSampleRate()).isGreaterThan(0);
        assertWithMessage(mPropertyName + " max sample rate must be >= min sample rate")
                .that(carPropertyConfig.getMaxSampleRate() >=
                        carPropertyConfig.getMinSampleRate())
                .isTrue();
    }

    private void verifyNonContinuousCarPropertyConfig(CarPropertyConfig<?> carPropertyConfig) {
        assertWithMessage(mPropertyName + " must define max sample rate as 0 since change mode is "
                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMaxSampleRate()).isEqualTo(0);
        assertWithMessage(mPropertyName + " must define min sample rate as 0 since change mode is "
                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMinSampleRate()).isEqualTo(0);
    }

    private void verifyCarPropertyValueGetter(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyManager carPropertyManager) {
        for (int areaId : carPropertyConfig.getAreaIds()) {
            CarPropertyValue<?> carPropertyValue =
                    carPropertyManager.getProperty(
                            mPropertyId, areaId);

            verifyCarPropertyValue(carPropertyConfig, carPropertyValue, areaId,
                    CAR_PROPERTY_VALUE_SOURCE_GETTER);
        }
    }

    private void verifyCarPropertyValue(CarPropertyConfig<?> carPropertyConfig,
            CarPropertyValue<?> carPropertyValue, int areaId, String source) {
        assertWithMessage(
                mPropertyName + " - areaId: " + areaId + " - source: " + source
                        + " value must have correct property ID")
                .that(carPropertyValue.getPropertyId()).isEqualTo(mPropertyId);
        assertWithMessage(
                mPropertyName + " - areaId: " + areaId + " - source: " + source
                        + " value must have correct area id: "
                        + areaId)
                .that(carPropertyValue.getAreaId())
                .isEqualTo(areaId);
        assertWithMessage(mPropertyName + " - areaId: " + areaId + " - source: " + source
                + " area ID must be in carPropertyConfig#getAreaIds()").that(Arrays.stream(
                carPropertyConfig.getAreaIds()).boxed().collect(Collectors.toList()).contains(
                carPropertyValue.getAreaId())).isTrue();
        assertWithMessage(
                mPropertyName + " - areaId: " + areaId + " - source: " + source
                        + " value's status must be valid")
                .that(carPropertyValue.getStatus()).isIn(
                ImmutableSet.of(CarPropertyValue.STATUS_AVAILABLE,
                        CarPropertyValue.STATUS_UNAVAILABLE, CarPropertyValue.STATUS_ERROR));
        assertWithMessage(mPropertyName + " - areaId: " + areaId +
                " - source: " + source
                + " timestamp must use the SystemClock.elapsedRealtimeNanos() time base")
                .that(carPropertyValue.getTimestamp()).isAtLeast(0);
        assertWithMessage(mPropertyName + " - areaId: " + areaId +
                " - source: " + source
                + " timestamp must use the SystemClock.elapsedRealtimeNanos() time base")
                .that(carPropertyValue.getTimestamp()).isLessThan(
                SystemClock.elapsedRealtimeNanos());
        assertWithMessage(
                mPropertyName + " - areaId: " + areaId + " - source: " + source + " must return "
                        + mPropertyType
                        + " type value")
                .that(carPropertyValue.getValue().getClass()).isEqualTo(mPropertyType);

        if (mRequirePropertyValueToBeInConfigArray) {
            assertWithMessage(mPropertyName + " - areaId: " + areaId + " - source: " + source +
                    " value must be listed in configArray")
                    .that(carPropertyConfig.getConfigArray().contains(
                            carPropertyValue.getValue())).isTrue();
        }

        if (!mPossibleCarPropertyValues.isEmpty()) {
            assertWithMessage(mPropertyName + " - areaId: " + areaId + " - source: " + source
                    + " value must be listed in the Integer set")
                    .that(carPropertyValue.getValue()).isIn(mPossibleCarPropertyValues);
        }

        mCarPropertyValueVerifier.ifPresent(
                propertyValueVerifier -> propertyValueVerifier.verify(carPropertyConfig,
                        carPropertyValue));

        T areaIdMinValue = (T) carPropertyConfig.getMinValue(areaId);
        T areaIdMaxValue = (T) carPropertyConfig.getMaxValue(areaId);
        if (areaIdMinValue != null && areaIdMaxValue != null) {
            assertWithMessage(
                "carPropertyValue must be between the max and min values")
                .that(verifyValueInRange(areaIdMinValue, areaIdMaxValue,
                    (T) carPropertyValue.getValue())).isTrue();
        }
    }

    private boolean verifyValueInRange(T min, T max, T value) {
        int propertyType = mPropertyId & VehiclePropertyType.MASK;
        switch (propertyType) {
            case VehiclePropertyType.INT32:
                return ((Integer) value >= (Integer) min && (Integer) value <= (Integer) max);
            case VehiclePropertyType.INT64:
                return ((Long) value >= (Long) min && (Long) value <= (Long) max);
            case VehiclePropertyType.FLOAT:
                return ((Float) value >= (Float) min && (Float) value <= (Float) max);
            default:
                return false;
        }
    }

    public interface ConfigArrayVerifier {
        void verify(List<Integer> configArray);
    }

    public interface CarPropertyValueVerifier {
        void verify(CarPropertyConfig<?> carPropertyConfig, CarPropertyValue<?> carPropertyValue);
    }

    public interface AreaIdsVerifier {
        void verify(int[] areaIds);
    }

    public static class Builder<T> {
        private final int mPropertyId;
        private final int mAccess;
        private final int mAreaType;
        private final int mChangeMode;
        private final Class<T> mPropertyType;
        private boolean mRequiredProperty = false;
        private Optional<ConfigArrayVerifier> mConfigArrayVerifier = Optional.empty();
        private Optional<CarPropertyValueVerifier> mCarPropertyValueVerifier = Optional.empty();
        private Optional<AreaIdsVerifier> mAreaIdsVerifier = Optional.empty();
        private ImmutableSet<Integer> mPossibleConfigArrayValues = ImmutableSet.of();
        private ImmutableSet<Integer> mPossibleCarPropertyValues = ImmutableSet.of();
        private boolean mRequirePropertyValueToBeInConfigArray = false;
        private boolean mVerifySetterWithConfigArrayValues = false;
        private boolean mRequireMinMaxValues = false;
        private boolean mRequireMinValuesToBeZero = false;
        private boolean mRequireZeroToBeContainedInMinMaxRanges = false;

        private Builder(int propertyId, int access, int areaType, int changeMode,
                Class<T> propertyType) {
            mPropertyId = propertyId;
            mAccess = access;
            mAreaType = areaType;
            mChangeMode = changeMode;
            mPropertyType = propertyType;
        }

        public Builder<T> requireProperty() {
            mRequiredProperty = true;
            return this;
        }

        public Builder<T> setConfigArrayVerifier(ConfigArrayVerifier configArrayVerifier) {
            mConfigArrayVerifier = Optional.of(configArrayVerifier);
            return this;
        }

        public Builder<T> setCarPropertyValueVerifier(
                CarPropertyValueVerifier carPropertyValueVerifier) {
            mCarPropertyValueVerifier = Optional.of(carPropertyValueVerifier);
            return this;
        }

        public Builder<T> setAreaIdsVerifier(AreaIdsVerifier areaIdsVerifier) {
            mAreaIdsVerifier = Optional.of(areaIdsVerifier);
            return this;
        }

        public Builder<T> setPossibleConfigArrayValues(
                ImmutableSet<Integer> possibleConfigArrayValues) {
            mPossibleConfigArrayValues = possibleConfigArrayValues;
            return this;
        }

        public Builder<T> setPossibleCarPropertyValues(
                ImmutableSet<Integer> possibleCarPropertyValues) {
            mPossibleCarPropertyValues = possibleCarPropertyValues;
            return this;
        }

        public Builder<T> requirePropertyValueTobeInConfigArray() {
            mRequirePropertyValueToBeInConfigArray = true;
            return this;
        }

        public Builder<T> verifySetterWithConfigArrayValues() {
            mVerifySetterWithConfigArrayValues = true;
            return this;
        }

        public Builder<T> requireMinMaxValues() {
            mRequireMinMaxValues = true;
            return this;
        }

        public Builder<T> requireMinValuesToBeZero() {
            mRequireMinValuesToBeZero = true;
            return this;
        }

        public Builder<T> requireZeroToBeContainedInMinMaxRanges() {
            mRequireZeroToBeContainedInMinMaxRanges = true;
            return this;
        }

        public VehiclePropertyVerifier<T> build() {
            return new VehiclePropertyVerifier<>(mPropertyId, mAccess, mAreaType, mChangeMode,
                    mPropertyType, mRequiredProperty, mConfigArrayVerifier,
                    mCarPropertyValueVerifier, mAreaIdsVerifier, mPossibleConfigArrayValues,
                    mPossibleCarPropertyValues, mRequirePropertyValueToBeInConfigArray,
                    mVerifySetterWithConfigArrayValues, mRequireMinMaxValues,
                    mRequireMinValuesToBeZero, mRequireZeroToBeContainedInMinMaxRanges);
        }
    }

    private static class CarPropertyValueCallback implements
            CarPropertyManager.CarPropertyEventCallback {
        private final String mPropertyName;
        private final int mTotalOnChangeEvents;
        private final CountDownLatch mCountDownLatch;
        private final List<CarPropertyValue<?>> mCarPropertyValues = new ArrayList<>();
        private final long mTimeoutMillis;

        CarPropertyValueCallback(String propertyName, int totalOnChangeEvents) {
            this(propertyName, totalOnChangeEvents, 1500);
        }

        CarPropertyValueCallback(String propertyName, int totalOnChangeEvents,
                long timeoutMillis) {
            mPropertyName = propertyName;
            mTotalOnChangeEvents = totalOnChangeEvents;
            mCountDownLatch = new CountDownLatch(totalOnChangeEvents);
            mTimeoutMillis = timeoutMillis;
        }

        public List<CarPropertyValue<?>> getCarPropertyValues() {
            try {
                assertWithMessage(
                        "Never received " + mTotalOnChangeEvents + "  onChangeEvent(s) for "
                                + mPropertyName + " callback before " + mTimeoutMillis
                                + " ms timeout").that(
                        mCountDownLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS)).isTrue();
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onChangeEvent callback(s) for " + mPropertyName
                        + " threw an exception: " + e).fail();
            }
            return mCarPropertyValues;
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            mCarPropertyValues.add(value);
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
        }

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {
        }
    }


    private static class SetterCallback<T> implements CarPropertyManager.CarPropertyEventCallback {
        private final int mPropertyId;
        private final String mPropertyName;
        private final int mAreaId;
        private final T mExpectedSetValue;
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private final long mCreationTimeNanos = SystemClock.elapsedRealtimeNanos();
        private CarPropertyValue<?> mUpdatedCarPropertyValue = null;

        SetterCallback(int propertyId, String propertyName, int areaId, T expectedSetValue) {
            mPropertyId = propertyId;
            mPropertyName = propertyName;
            mAreaId = areaId;
            mExpectedSetValue = expectedSetValue;
        }

        public CarPropertyValue<?> waitForUpdatedCarPropertyValue() {
            try {
                assertWithMessage(
                        "Never received onChangeEvent(s) for " + mPropertyName + " new value: "
                                + mExpectedSetValue + " before 5s timeout").that(
                        mCountDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onChangeEvent set callback for " + mPropertyName
                        + " threw an exception: " + e).fail();
            }
            return mUpdatedCarPropertyValue;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            if (mUpdatedCarPropertyValue != null || carPropertyValue.getPropertyId() != mPropertyId
                    || carPropertyValue.getAreaId() != mAreaId
                    || carPropertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE
                    || carPropertyValue.getTimestamp() <= mCreationTimeNanos
                    || carPropertyValue.getTimestamp() >= SystemClock.elapsedRealtimeNanos()
                    || !carPropertyValue.getValue().equals(mExpectedSetValue)) {
                return;
            }
            mUpdatedCarPropertyValue = carPropertyValue;
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
        }
    }
}
