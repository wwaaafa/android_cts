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

import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.os.SystemClock;

import com.google.common.collect.ImmutableSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class VehiclePropertyVerifier<T> {
    private final static String CAR_PROPERTY_VALUE_SOURCE_GETTER = "Getter";
    private final static String CAR_PROPERTY_VALUE_SOURCE_CALLBACK = "Callback";
    private static final ImmutableSet<Integer> WHEEL_AREA_IDS = ImmutableSet.of(
            VehicleAreaWheel.WHEEL_LEFT_FRONT, VehicleAreaWheel.WHEEL_LEFT_REAR,
            VehicleAreaWheel.WHEEL_RIGHT_FRONT, VehicleAreaWheel.WHEEL_RIGHT_REAR);

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
    private final boolean mRequirePropertyValueToBeInConfigArray;
    private final boolean mVerifySetterWithConfigArrayValues;

    private VehiclePropertyVerifier(int propertyId, int access, int areaType, int changeMode,
            Class<T> propertyType, boolean requiredProperty,
            Optional<ConfigArrayVerifier> configArrayVerifier,
            Optional<CarPropertyValueVerifier> carPropertyValueVerifier,
            Optional<AreaIdsVerifier> areaIdsVerifier,
            ImmutableSet<Integer> possibleConfigArrayValues,
            boolean requirePropertyValueToBeInConfigArray,
            boolean verifySetterWithConfigArrayValues) {
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
        mRequirePropertyValueToBeInConfigArray = requirePropertyValueToBeInConfigArray;
        mVerifySetterWithConfigArrayValues = verifySetterWithConfigArrayValues;
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
        if (!mVerifySetterWithConfigArrayValues) {
            return;
        }
        for (Integer valueToSet : carPropertyConfig.getConfigArray()) {
            for (int areaId : carPropertyConfig.getAreaIds()) {
                CarPropertyValue<Integer> currentCarPropertyValue = carPropertyManager.getProperty(
                        mPropertyId, areaId);
                verifyCarPropertyValue(carPropertyConfig, currentCarPropertyValue, areaId,
                        CAR_PROPERTY_VALUE_SOURCE_GETTER);
                if (currentCarPropertyValue.getValue().equals(valueToSet)) {
                    continue;
                }
                SetterCallback setterCallback = new SetterCallback(mPropertyId, mPropertyName,
                        areaId, valueToSet);
                assertWithMessage("Failed to register setter callback for " + mPropertyName).that(
                        carPropertyManager.registerCallback(setterCallback, mPropertyId,
                                CarPropertyManager.SENSOR_RATE_FASTEST)).isTrue();
                carPropertyManager.setIntProperty(mPropertyId, areaId, valueToSet);
                CarPropertyValue<?> updatedCarPropertyValue =
                        setterCallback.waitForUpdatedCarPropertyValue();
                verifyCarPropertyValue(carPropertyConfig, updatedCarPropertyValue, areaId,
                        CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
                carPropertyManager.unregisterCallback(setterCallback, mPropertyId);
            }
        }
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
            CarPropertyValueCallback carPropertyValueCallback =
                    new CarPropertyValueCallback(mPropertyName, 1);
            assertWithMessage("Failed to register callback for " + mPropertyName).that(
                    carPropertyManager.registerCallback(carPropertyValueCallback, mPropertyId,
                            CarPropertyManager.SENSOR_RATE_FASTEST)).isTrue();
            List<CarPropertyValue<?>> carPropertyValues =
                    carPropertyValueCallback.getCarPropertyValues();
            carPropertyManager.unregisterCallback(carPropertyValueCallback, mPropertyId);

            for (CarPropertyValue<?> carPropertyValue : carPropertyValues) {
                verifyCarPropertyValue(carPropertyConfig, carPropertyValue,
                        carPropertyValue.getAreaId(), CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
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

        if (mAreaIdsVerifier.isPresent()) {
            mAreaIdsVerifier.get().verify(carPropertyConfig.getAreaIds());
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL) {
            assertWithMessage(
                    mPropertyName + "'s AreaIds must contain a single 0 since it is "
                            + areaTypeToString(mAreaType))
                    .that(carPropertyConfig.getAreaIds()).isEqualTo(new int[]{0});
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL) {
            assertWithMessage(mPropertyName + "'s Area IDs must all be unique").that(
                    ImmutableSet.copyOf(Arrays.stream(
                            carPropertyConfig.getAreaIds()).boxed().collect(
                            Collectors.toList())).size()
                            == carPropertyConfig.getAreaIds().length).isTrue();
            for (int areaId : carPropertyConfig.getAreaIds()) {
                assertWithMessage(mPropertyName + "'s area ID: " + areaId
                        + " must be a valid VehicleAreaWheel area ID").that(
                        WHEEL_AREA_IDS.contains(areaId)).isTrue();
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
            if (areaIdMinValue == null || areaIdMaxValue == null) {
                continue;
            }
            assertWithMessage(
                    mPropertyName + " - areaId: " + areaId + "'s max value must be >= min value")
                    .that(verifyMaxAndMin(areaIdMinValue, areaIdMaxValue)).isTrue();
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
        private boolean mRequirePropertyValueToBeInConfigArray = false;
        private boolean mVerifySetterWithConfigArrayValues = false;


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

        public Builder<T> requirePropertyValueTobeInConfigArray() {
            mRequirePropertyValueToBeInConfigArray = true;
            return this;
        }

        public Builder<T> verifySetterWithConfigArrayValues() {
            mVerifySetterWithConfigArrayValues = true;
            return this;
        }

        public VehiclePropertyVerifier<T> build() {
            return new VehiclePropertyVerifier<>(mPropertyId, mAccess, mAreaType, mChangeMode,
                    mPropertyType, mRequiredProperty, mConfigArrayVerifier,
                    mCarPropertyValueVerifier, mAreaIdsVerifier, mPossibleConfigArrayValues,
                    mRequirePropertyValueToBeInConfigArray, mVerifySetterWithConfigArrayValues);
        }
    }

    private static class CarPropertyValueCallback implements
            CarPropertyManager.CarPropertyEventCallback {
        private final String mPropertyName;
        private final int mTotalOnChangeEvents;
        private final CountDownLatch mCountDownLatch;
        private final List<CarPropertyValue<?>> mCarPropertyValues = new ArrayList<>();

        public CarPropertyValueCallback(String propertyName, int totalOnChangeEvents) {
            mPropertyName = propertyName;
            mTotalOnChangeEvents = totalOnChangeEvents;
            mCountDownLatch = new CountDownLatch(totalOnChangeEvents);
        }

        public List<CarPropertyValue<?>> getCarPropertyValues() {
            try {
                assertWithMessage(
                        "Never received " + mTotalOnChangeEvents + "  onChangeEvent(s) for "
                                + mPropertyName + " callback before 1500ms timeout").that(
                        mCountDownLatch.await(1500, TimeUnit.MILLISECONDS)).isTrue();
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
