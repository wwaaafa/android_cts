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

import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeThat;

import android.car.VehicleAreaDoor;
import android.car.VehicleAreaMirror;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehicleAreaWindow;
import android.car.VehiclePropertyIds;
import android.car.VehiclePropertyType;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.CarInternalErrorException;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.GetPropertyCallback;
import android.car.hardware.property.CarPropertyManager.GetPropertyRequest;
import android.car.hardware.property.CarPropertyManager.GetPropertyResult;
import android.car.hardware.property.CarPropertyManager.PropertyAsyncError;
import android.car.hardware.property.PropertyNotAvailableAndRetryException;
import android.car.hardware.property.PropertyNotAvailableErrorCode;
import android.car.hardware.property.PropertyNotAvailableException;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;

import androidx.annotation.Nullable;

import com.android.internal.annotations.GuardedBy;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.hamcrest.Matchers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class VehiclePropertyVerifier<T> {
    private static final String TAG = VehiclePropertyVerifier.class.getSimpleName();
    private static final String CAR_PROPERTY_VALUE_SOURCE_GETTER = "Getter";
    private static final String CAR_PROPERTY_VALUE_SOURCE_CALLBACK = "Callback";
    private static final float FLOAT_INEQUALITY_THRESHOLD = 0.00001f;
    private static final int VENDOR_ERROR_CODE_MINIMUM_VALUE = 0x0;
    private static final int VENDOR_ERROR_CODE_MAXIMUM_VALUE = 0xffff;
    private static final ImmutableSet<Integer> WHEEL_AREAS = ImmutableSet.of(
            VehicleAreaWheel.WHEEL_LEFT_FRONT, VehicleAreaWheel.WHEEL_LEFT_REAR,
            VehicleAreaWheel.WHEEL_RIGHT_FRONT, VehicleAreaWheel.WHEEL_RIGHT_REAR);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_WHEEL_AREA_IDS =
            generateAllPossibleAreaIds(WHEEL_AREAS);
    private static final ImmutableSet<Integer> WINDOW_AREAS = ImmutableSet.of(
            VehicleAreaWindow.WINDOW_FRONT_WINDSHIELD, VehicleAreaWindow.WINDOW_REAR_WINDSHIELD,
            VehicleAreaWindow.WINDOW_ROW_1_LEFT, VehicleAreaWindow.WINDOW_ROW_1_RIGHT,
            VehicleAreaWindow.WINDOW_ROW_2_LEFT, VehicleAreaWindow.WINDOW_ROW_2_RIGHT,
            VehicleAreaWindow.WINDOW_ROW_3_LEFT, VehicleAreaWindow.WINDOW_ROW_3_RIGHT,
            VehicleAreaWindow.WINDOW_ROOF_TOP_1, VehicleAreaWindow.WINDOW_ROOF_TOP_2);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_WINDOW_AREA_IDS =
            generateAllPossibleAreaIds(WINDOW_AREAS);
    private static final ImmutableSet<Integer> MIRROR_AREAS = ImmutableSet.of(
            VehicleAreaMirror.MIRROR_DRIVER_LEFT, VehicleAreaMirror.MIRROR_DRIVER_RIGHT,
            VehicleAreaMirror.MIRROR_DRIVER_CENTER);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_MIRROR_AREA_IDS =
            generateAllPossibleAreaIds(MIRROR_AREAS);
    private static final ImmutableSet<Integer> SEAT_AREAS = ImmutableSet.of(
            VehicleAreaSeat.SEAT_ROW_1_LEFT, VehicleAreaSeat.SEAT_ROW_1_CENTER,
            VehicleAreaSeat.SEAT_ROW_1_RIGHT, VehicleAreaSeat.SEAT_ROW_2_LEFT,
            VehicleAreaSeat.SEAT_ROW_2_CENTER, VehicleAreaSeat.SEAT_ROW_2_RIGHT,
            VehicleAreaSeat.SEAT_ROW_3_LEFT, VehicleAreaSeat.SEAT_ROW_3_CENTER,
            VehicleAreaSeat.SEAT_ROW_3_RIGHT);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_SEAT_AREA_IDS =
            generateAllPossibleAreaIds(SEAT_AREAS);
    private static final ImmutableSet<Integer> DOOR_AREAS = ImmutableSet.of(
            VehicleAreaDoor.DOOR_ROW_1_LEFT, VehicleAreaDoor.DOOR_ROW_1_RIGHT,
            VehicleAreaDoor.DOOR_ROW_2_LEFT, VehicleAreaDoor.DOOR_ROW_2_RIGHT,
            VehicleAreaDoor.DOOR_ROW_3_LEFT, VehicleAreaDoor.DOOR_ROW_3_RIGHT,
            VehicleAreaDoor.DOOR_HOOD, VehicleAreaDoor.DOOR_REAR);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_DOOR_AREA_IDS =
            generateAllPossibleAreaIds(DOOR_AREAS);
    private static final ImmutableSet<Integer> PROPERTY_NOT_AVAILABLE_ERROR_CODES =
            ImmutableSet.of(
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_DISABLED,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_LOW,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_SPEED_HIGH,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_POOR_VISIBILITY,
                    PropertyNotAvailableErrorCode.NOT_AVAILABLE_SAFETY);

    private final CarPropertyManager mCarPropertyManager;
    private final int mPropertyId;
    private final String mPropertyName;
    private final int mAccess;
    private final int mAreaType;
    private final int mChangeMode;
    private final Class<T> mPropertyType;
    private final boolean mRequiredProperty;
    private final Optional<ConfigArrayVerifier> mConfigArrayVerifier;
    private final Optional<CarPropertyValueVerifier<T>> mCarPropertyValueVerifier;
    private final Optional<AreaIdsVerifier> mAreaIdsVerifier;
    private final Optional<CarPropertyConfigVerifier> mCarPropertyConfigVerifier;
    private final ImmutableSet<Integer> mPossibleConfigArrayValues;
    private final ImmutableSet<T> mAllPossibleEnumValues;
    private final ImmutableSet<T> mAllPossibleUnwritableValues;
    private final boolean mRequirePropertyValueToBeInConfigArray;
    private final boolean mVerifySetterWithConfigArrayValues;
    private final boolean mRequireMinMaxValues;
    private final boolean mRequireMinValuesToBeZero;
    private final boolean mRequireZeroToBeContainedInMinMaxRanges;
    private final boolean mPossiblyDependentOnHvacPowerOn;
    private final ImmutableSet<String> mReadPermissions;
    private final ImmutableList<ImmutableSet<String>> mWritePermissions;

    private boolean mIsCarPropertyConfigCached;
    private CarPropertyConfig<T> mCachedCarPropertyConfig;
    private SparseArray<SparseArray<?>> mPropertyToAreaIdValues;

    private VehiclePropertyVerifier(
            CarPropertyManager carPropertyManager,
            int propertyId,
            int access,
            int areaType,
            int changeMode,
            Class<T> propertyType,
            boolean requiredProperty,
            Optional<ConfigArrayVerifier> configArrayVerifier,
            Optional<CarPropertyValueVerifier<T>> carPropertyValueVerifier,
            Optional<AreaIdsVerifier> areaIdsVerifier,
            Optional<CarPropertyConfigVerifier> carPropertyConfigVerifier,
            ImmutableSet<Integer> possibleConfigArrayValues,
            ImmutableSet<T> allPossibleEnumValues,
            ImmutableSet<T> allPossibleUnwritableValues,
            boolean requirePropertyValueToBeInConfigArray,
            boolean verifySetterWithConfigArrayValues,
            boolean requireMinMaxValues,
            boolean requireMinValuesToBeZero,
            boolean requireZeroToBeContainedInMinMaxRanges,
            boolean possiblyDependentOnHvacPowerOn,
            ImmutableSet<String> readPermissions,
            ImmutableList<ImmutableSet<String>> writePermissions) {
        assertWithMessage("Must set car property manager").that(carPropertyManager).isNotNull();
        mCarPropertyManager = carPropertyManager;
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
        mCarPropertyConfigVerifier = carPropertyConfigVerifier;
        mPossibleConfigArrayValues = possibleConfigArrayValues;
        mAllPossibleEnumValues = allPossibleEnumValues;
        mAllPossibleUnwritableValues = allPossibleUnwritableValues;
        mRequirePropertyValueToBeInConfigArray = requirePropertyValueToBeInConfigArray;
        mVerifySetterWithConfigArrayValues = verifySetterWithConfigArrayValues;
        mRequireMinMaxValues = requireMinMaxValues;
        mRequireMinValuesToBeZero = requireMinValuesToBeZero;
        mRequireZeroToBeContainedInMinMaxRanges = requireZeroToBeContainedInMinMaxRanges;
        mPossiblyDependentOnHvacPowerOn = possiblyDependentOnHvacPowerOn;
        mReadPermissions = readPermissions;
        mWritePermissions = writePermissions;
        mPropertyToAreaIdValues = new SparseArray<>();
    }

    public static <T> Builder<T> newBuilder(
            int propertyId, int access, int areaType, int changeMode, Class<T> propertyType,
            CarPropertyManager carPropertyManager) {
        return new Builder<>(propertyId, access, areaType, changeMode, propertyType,
                carPropertyManager);
    }

    public String getPropertyName() {
        return mPropertyName;
    }

    @Nullable
    public static <U> U getDefaultValue(Class<?> clazz) {
        if (clazz == Boolean.class) {
            return (U) Boolean.TRUE;
        }
        if (clazz == Integer.class) {
            return (U) (Integer) 2;
        }
        if (clazz == Float.class) {
            return (U) (Float) 2.f;
        }
        if (clazz == Long.class) {
            return (U) (Long) 2L;
        }
        if (clazz == Integer[].class) {
            return (U) new Integer[]{2};
        }
        if (clazz == Float[].class) {
            return (U) new Float[]{2.f};
        }
        if (clazz == Long[].class) {
            return (U) new Long[]{2L};
        }
        if (clazz == String.class) {
            return (U) new String("test");
        }
        if (clazz == byte[].class) {
            return (U) new byte[]{(byte) 0xbe, (byte) 0xef};
        }
        return null;
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

    /**
     * Gets the car property config for the current property or reads from cache if already cached.
     */
    public @Nullable CarPropertyConfig<T> getCarPropertyConfig() {
        if (!mIsCarPropertyConfigCached)  {
            mCachedCarPropertyConfig = (CarPropertyConfig<T>) mCarPropertyManager
                    .getCarPropertyConfig(mPropertyId);
            mIsCarPropertyConfigCached = true;
        }
        return mCachedCarPropertyConfig;
    }

    public boolean isSupported() {
        return getCarPropertyConfig() != null;
    }

    public void verify() {
        ImmutableSet.Builder<String> permissionsBuilder = ImmutableSet.<String>builder();
        for (ImmutableSet<String> writePermissions: mWritePermissions) {
            permissionsBuilder.addAll(writePermissions);
        }
        ImmutableSet<String> allPermissions = permissionsBuilder.addAll(mReadPermissions).build();

        runWithShellPermissionIdentity(
                () -> {
                    CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
                    if (carPropertyConfig == null) {
                        if (mAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ || mAccess
                                == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                            assertThrows("Test does not have correct permissions granted for "
                                            + mPropertyName + ". Requested permissions: "
                                            + allPermissions,
                                    IllegalArgumentException.class,
                                    () -> mCarPropertyManager.getProperty(mPropertyId, /*areaId=*/
                                            0));
                        } else if (mAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
                            assertThrows("Test does not have correct permissions granted for "
                                            + mPropertyName + ". Requested permissions: "
                                            + allPermissions,
                                    IllegalArgumentException.class,
                                    () -> mCarPropertyManager.setProperty(mPropertyType,
                                            mPropertyId, /*areaId=*/
                                            0, getDefaultValue(mPropertyType)));
                        }
                    }

                    if (mRequiredProperty) {
                        assertWithMessage("Must support " + mPropertyName).that(isSupported())
                                .isTrue();
                    } else {
                        assumeThat("Skipping " + mPropertyName
                                        + " CTS test because the property is not supported on "
                                        + "this vehicle",
                                carPropertyConfig, Matchers.notNullValue());
                    }

                    verifyCarPropertyConfig();
                }, allPermissions.toArray(new String[0]));

        verifyPermissionNotGrantedException();
        verifyReadPermissions();
        verifyWritePermissions();
    }

    private void verifyReadPermissions() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (String readPermission: mReadPermissions) {
            if (carPropertyConfig.getAccess()
                    == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                verifyReadPermissionCannotWrite(readPermission, mWritePermissions);
            }
            verifyReadPermissionGivesAccessToReadApis(readPermission);
        }
    }

    private void verifyWritePermissions() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (ImmutableSet<String> writePermissions: mWritePermissions) {
            if (carPropertyConfig.getAccess() != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
                verifyWritePermissionsCannotRead(writePermissions, mReadPermissions);
            }
            if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                return;
            }
            if (writePermissions.size() > 1) {
                verifyIndividualWritePermissionsCannotWrite(writePermissions);
            }
            verifyWritePermissionsGiveAccessToWriteApis(writePermissions, mReadPermissions);
        }
    }

    private void verifyReadPermissionCannotWrite(String readPermission,
            ImmutableList<ImmutableSet<String>> writePermissions) {
        // If the read permission is the same as the write permission and the property does not
        // require any other write permissions we skip this permission.
        for (ImmutableSet<String> writePermissionSet: writePermissions) {
            if (writePermissionSet.size() == 1 && writePermissionSet.contains(readPermission)) {
                return;
            }
        }
        runWithShellPermissionIdentity(
                () -> {
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be written to without write"
                                    + " permissions.",
                            SecurityException.class,
                            () -> mCarPropertyManager.setProperty(mPropertyType, mPropertyId,
                                    /* areaId = */ 0, getDefaultValue(mPropertyType)));
                }, readPermission);
    }

    private void verifyReadPermissionGivesAccessToReadApis(String readPermission) {
        runWithShellPermissionIdentity(
                () -> {
                    assertThat(mCarPropertyManager.getCarPropertyConfig(mPropertyId)).isNotNull();
                    turnOnHvacPowerIfHvacPowerDependent();
                    try {
                        verifyCarPropertyValueGetter();
                        verifyCarPropertyValueCallback();
                        verifyGetPropertiesAsync();
                    } finally {
                        // Restore HVAC power even if test fails.
                        turnOffHvacPowerIfHvacPowerDependent();
                    }
                }, readPermission);
    }

    private void verifyWritePermissionsCannotRead(ImmutableSet<String> writePermissions,
            ImmutableSet<String> allReadPermissions) {
        // If there is any write permission that is also a read permission we skip the permissions.
        if (!Collections.disjoint(writePermissions, allReadPermissions)) {
            return;
        }
        runWithShellPermissionIdentity(
                () -> {
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be read without read"
                                    + " permissions.",
                            SecurityException.class,
                            () -> mCarPropertyManager.getProperty(mPropertyId, /* areaId = */ 0));
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be listened to without read"
                                    + " permissions.",
                            SecurityException.class,
                            () -> verifyCarPropertyValueCallback());
                    assertThrows(
                            mPropertyName
                                    + " - property ID: "
                                    + mPropertyId
                                    + " should not be able to be read without read"
                                    + " permissions.",
                            SecurityException.class,
                            () -> verifyGetPropertiesAsync());
                }, writePermissions.toArray(new String[0]));
    }

    private void verifyIndividualWritePermissionsCannotWrite(
            ImmutableSet<String> writePermissions) {
        String writePermissionsNeededString = String.join(", ", writePermissions);
        for (String writePermission: writePermissions) {
            runWithShellPermissionIdentity(
                    () -> {
                        assertThat(mCarPropertyManager.getCarPropertyConfig(mPropertyId)).isNull();
                        assertThrows(
                                mPropertyName
                                        + " - property ID: "
                                        + mPropertyId
                                        + " should not be able to be written to without all of the"
                                        + " following permissions granted: "
                                        + writePermissionsNeededString,
                                SecurityException.class,
                                () -> mCarPropertyManager.setProperty(mPropertyType, mPropertyId,
                                        /* areaId = */ 0, getDefaultValue(mPropertyType)));
                    }, writePermission);
        }
    }

    private void verifyWritePermissionsGiveAccessToWriteApis(ImmutableSet<String> writePermissions,
            ImmutableSet<String> readPermissions) {
        runWithShellPermissionIdentity(
                () -> {
                    turnOnHvacPowerIfHvacPowerDependent();
                    storeCurrentValues();
                    try {
                        verifyCarPropertyValueSetter();
                        // TODO(b/266000988): verifySetProeprtiesAsync(...)
                    } finally {
                        // Restore property value and HVAC power even if test fails.
                        restoreInitialValues();
                        turnOffHvacPowerIfHvacPowerDependent();
                    }
                }, ImmutableSet.<String>builder()
                        .addAll(writePermissions)
                        .addAll(readPermissions)
                        .build().toArray(new String[0]));
    }

    private void turnOnHvacPowerIfHvacPowerDependent() {
        if (!mPossiblyDependentOnHvacPowerOn) {
            return;
        }

        CarPropertyConfig<Boolean> hvacPowerOnCarPropertyConfig = (CarPropertyConfig<Boolean>)
                mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.HVAC_POWER_ON);
        if (hvacPowerOnCarPropertyConfig == null
                || !hvacPowerOnCarPropertyConfig.getConfigArray().contains(mPropertyId)) {
            return;
        }

        SparseArray<Boolean> hvacPowerStateByAreaId = (SparseArray<Boolean>)
                getInitialValuesByAreaId(hvacPowerOnCarPropertyConfig, mCarPropertyManager);
        mPropertyToAreaIdValues.put(VehiclePropertyIds.HVAC_POWER_ON, hvacPowerStateByAreaId);

        // Turn the power on for all supported HVAC area IDs.
        setBooleanPropertyInAllAreaIds(hvacPowerOnCarPropertyConfig, /* setValue: */ Boolean.TRUE);
    }

    private void turnOffHvacPowerIfHvacPowerDependent() {
        if (!mPossiblyDependentOnHvacPowerOn) {
            return;
        }

        CarPropertyConfig<Boolean> hvacPowerOnCarPropertyConfig = (CarPropertyConfig<Boolean>)
                mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.HVAC_POWER_ON);
        if (hvacPowerOnCarPropertyConfig == null
                || !hvacPowerOnCarPropertyConfig.getConfigArray().contains(mPropertyId)) {
            return;
        }

        // Turn the power off for all supported HVAC area IDs.
        setBooleanPropertyInAllAreaIds(hvacPowerOnCarPropertyConfig, /* setValue: */ Boolean.FALSE);
        verifySetNotAvailable();

        SparseArray<Boolean> hvacPowerStateByAreaId = (SparseArray<Boolean>)
                mPropertyToAreaIdValues.get(VehiclePropertyIds.HVAC_POWER_ON);
        restoreInitialValuesByAreaId(hvacPowerOnCarPropertyConfig, mCarPropertyManager,
                hvacPowerStateByAreaId);
    }

    /**
     * Stores the property's current values for all areas so that they can be restored later.
     */
    public void storeCurrentValues() {
        SparseArray<T> mAreaIdToInitialValue = getInitialValuesByAreaId(getCarPropertyConfig(),
                mCarPropertyManager);
        if (mAreaIdToInitialValue == null) {
            return;
        }
        mPropertyToAreaIdValues.put(mPropertyId, mAreaIdToInitialValue);
    }

    /**
     * Restore the property's values to original values stored by previous
     * {@link #storeCurrentValues}.
     *
     * Do nothing if no stored current values are available.
     */
    public void restoreInitialValues() {
        SparseArray<T> mAreaIdToInitialValue = (SparseArray<T>)
                mPropertyToAreaIdValues.get(mPropertyId);
        if (mAreaIdToInitialValue == null) {
            Log.w(TAG, "No stored values to restore to, ignore");
            return;
        }
        restoreInitialValuesByAreaId(getCarPropertyConfig(), mCarPropertyManager,
                mAreaIdToInitialValue);
    }

    // Get a map storing the property's area Ids to the initial values.
    @Nullable
    private static <U> SparseArray<U> getInitialValuesByAreaId(
            CarPropertyConfig<U> carPropertyConfig, CarPropertyManager carPropertyManager) {
        if (carPropertyConfig.getAccess() != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
            return null;
        }
        SparseArray<U> areaIdToInitialValue = new SparseArray<U>();
        int propertyId = carPropertyConfig.getPropertyId();
        String propertyName = VehiclePropertyIds.toString(propertyId);
        for (int areaId : carPropertyConfig.getAreaIds()) {
            CarPropertyValue<U> carPropertyValue = null;
            try {
                carPropertyValue = carPropertyManager.getProperty(propertyId, areaId);
            } catch (PropertyNotAvailableAndRetryException | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                Log.w(TAG, "Failed to get property:" + propertyName + " at area ID: " + areaId
                        + " to save initial car property value. Error: " + e);
                continue;
            }
            if (carPropertyValue == null) {
                Log.w(TAG, "Failed to get property:" + propertyName + " at area ID: " + areaId
                        + " to save initial car property value.");
                continue;
            }
            areaIdToInitialValue.put(areaId, (U) carPropertyValue.getValue());
        }
        return areaIdToInitialValue;
    }

    /**
     * Set boolean property to a desired value in all supported area IDs.
     */
    private void setBooleanPropertyInAllAreaIds(CarPropertyConfig<Boolean> booleanCarPropertyConfig,
            Boolean setValue) {
        int propertyId = booleanCarPropertyConfig.getPropertyId();
        for (int areaId : booleanCarPropertyConfig.getAreaIds()) {
            if (mCarPropertyManager.getBooleanProperty(propertyId, areaId) == setValue) {
                continue;
            }
            CarPropertyValue<Boolean> carPropertyValue = setPropertyAndWaitForChange(
                    mCarPropertyManager, propertyId, Boolean.class, areaId, setValue);
            assertWithMessage(
                    VehiclePropertyIds.toString(propertyId)
                            + " carPropertyValue is null for area id: " + areaId)
                    .that(carPropertyValue).isNotNull();
        }
    }

    // Restore the initial values of the property provided by {@code areaIdToInitialValue}.
    private static <U> void restoreInitialValuesByAreaId(CarPropertyConfig<U> carPropertyConfig,
            CarPropertyManager carPropertyManager, SparseArray<U> areaIdToInitialValue) {
        int propertyId = carPropertyConfig.getPropertyId();
        String propertyName = VehiclePropertyIds.toString(propertyId);
        for (int i = 0; i < areaIdToInitialValue.size(); i++) {
            int areaId = areaIdToInitialValue.keyAt(i);
            U originalValue = areaIdToInitialValue.valueAt(i);
            CarPropertyValue<U> currentCarPropertyValue = null;
            try {
                currentCarPropertyValue = carPropertyManager.getProperty(propertyId, areaId);
            } catch (PropertyNotAvailableAndRetryException | PropertyNotAvailableException
                    | CarInternalErrorException e) {
                Log.w(TAG, "Failed to get property:" + propertyName + " at area ID: " + areaId
                        + " to restore initial car property value. Error: " + e);
                continue;
            }
            if (currentCarPropertyValue == null) {
                Log.w(TAG, "Failed to get property:" + propertyName + " at area ID: " + areaId
                        + " to restore initial car property value.");
                continue;
            }
            U currentValue = (U) currentCarPropertyValue.getValue();
            if (valueEquals(originalValue, currentValue)) {
                continue;
            }
            CarPropertyValue<U> carPropertyValue = setPropertyAndWaitForChange(carPropertyManager,
                    propertyId, carPropertyConfig.getPropertyType(), areaId, originalValue);
            assertWithMessage(
                    "Failed to restore car property value for property: " + propertyName
                            + " at area ID: " + areaId + " to its original value: " + originalValue
                            + ", current value: " + currentValue)
                    .that(carPropertyValue).isNotNull();
        }
    }

    /**
     * Gets the possible values that could be set to.
     *
     * The values returned here must not cause {@code IllegalArgumentException} for set.
     *
     * Returns {@code null} or empty array if we don't know possible values.
     */
    public @Nullable Collection<T> getPossibleValues(int areaId) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (Boolean.class.equals(carPropertyConfig.getPropertyType())) {
            return (List<T>) List.of(Boolean.TRUE, Boolean.FALSE);
        } else if (Integer.class.equals(carPropertyConfig.getPropertyType())) {
            return (List<T>) getPossibleIntegerValues(areaId);
        } else if (Float.class.equals(carPropertyConfig.getPropertyType())) {
            return getPossibleFloatValues();
        }
        return null;
    }

    /**
     * Gets the possible values for an integer property.
     */
    private List<Integer> getPossibleIntegerValues(int areaId) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        List<Integer> possibleValues = new ArrayList<>();
        if (mPropertyId == VehiclePropertyIds.HVAC_FAN_DIRECTION) {
            int[] availableHvacFanDirections = mCarPropertyManager.getIntArrayProperty(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE, areaId);
            for (int i = 0; i < availableHvacFanDirections.length; i++) {
                possibleValues.add(availableHvacFanDirections[i]);
            }
            return possibleValues;
        }
        if (mVerifySetterWithConfigArrayValues) {
            for (Integer value : carPropertyConfig.getConfigArray()) {
                possibleValues.add(value);
            }
            return possibleValues;
        }

        if (!mAllPossibleEnumValues.isEmpty()) {
            AreaIdConfig areaIdConfig = carPropertyConfig.getAreaIdConfig(areaId);
            for (Integer value : (List<Integer>) areaIdConfig.getSupportedEnumValues()) {
                if (mAllPossibleUnwritableValues.isEmpty()
                        || !mAllPossibleUnwritableValues.contains(value)) {
                    possibleValues.add(value);
                }
            }
        } else {
            Integer minValue = (Integer) carPropertyConfig.getMinValue(areaId);
            Integer maxValue = (Integer) carPropertyConfig.getMaxValue(areaId);
            if (minValue != null && maxValue != null) {
                List<Integer> valuesToSet = IntStream.rangeClosed(
                        minValue.intValue(), maxValue.intValue()).boxed().collect(
                        Collectors.toList());
                for (int i = 0; i < valuesToSet.size(); i++) {
                    possibleValues.add(valuesToSet.get(i));
                }
            }

        }
        return possibleValues;
    }

    /**
     * Gets the possible values for an float property.
     */
    private Collection<T> getPossibleFloatValues() {
        if (mPropertyId != VehiclePropertyIds.HVAC_TEMPERATURE_SET) {
            return new ArrayList<>();
        }
        List<Integer> hvacTempSetConfigArray = getCarPropertyConfig().getConfigArray();
        ImmutableSet.Builder<Float> possibleHvacTempSetValuesBuilder = ImmutableSet.builder();
        // For HVAC_TEMPERATURE_SET, the configArray specifies the supported temperature values
        // for the property. configArray[0] is the lower bound of the supported temperatures in
        // Celsius. configArray[1] is the upper bound of the supported temperatures in Celsius.
        // configArray[2] is the supported temperature increment between the two bounds. All
        // configArray values are Celsius*10 since the configArray is List<Integer> but
        // HVAC_TEMPERATURE_SET is a Float type property.
        for (int possibleHvacTempSetValue = hvacTempSetConfigArray.get(0);
                possibleHvacTempSetValue <= hvacTempSetConfigArray.get(1);
                possibleHvacTempSetValue += hvacTempSetConfigArray.get(2)) {
            possibleHvacTempSetValuesBuilder.add((float) possibleHvacTempSetValue / 10.0f);
        }
        return (Collection<T>) possibleHvacTempSetValuesBuilder.build();
    }

    private void verifyCarPropertyValueSetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
            verifySetPropertyFails();
            return;
        }
        if (Boolean.class.equals(carPropertyConfig.getPropertyType())) {
            verifyBooleanPropertySetter();
        } else if (Integer.class.equals(carPropertyConfig.getPropertyType())) {
            verifyIntegerPropertySetter();
        } else if (Float.class.equals(carPropertyConfig.getPropertyType())) {
            verifyFloatPropertySetter();
        } else if (mPropertyId == VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION) {
            verifyHvacTemperatureValueSuggestionSetter();
        }
    }

    private void verifySetPropertyFails() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertThrows(
                mPropertyName
                        + " is a read_only property so setProperty should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () -> mCarPropertyManager.setProperty(mPropertyType, mPropertyId,
                        carPropertyConfig.getAreaIds()[0], getDefaultValue(mPropertyType)));
    }

    private void verifyBooleanPropertySetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            for (Boolean valueToSet: List.of(Boolean.TRUE, Boolean.FALSE)) {
                verifySetProperty(areaId, (T) valueToSet);
            }
        }
    }


    private void verifyIntegerPropertySetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            for (Integer valueToSet : getPossibleIntegerValues(areaId)) {
                verifySetProperty(areaId, (T) valueToSet);
            }
        }
        if (!mAllPossibleEnumValues.isEmpty()) {
            for (AreaIdConfig<?> areaIdConfig : carPropertyConfig.getAreaIdConfigs()) {
                for (T valueToSet : (List<T>) areaIdConfig.getSupportedEnumValues()) {
                    if (!mAllPossibleUnwritableValues.isEmpty()
                            && mAllPossibleUnwritableValues.contains(valueToSet)) {
                        assertThrows("Trying to set an unwritable value: " + valueToSet
                                + " to property: " + mPropertyId + " should throw an "
                                + "IllegalArgumentException",
                                IllegalArgumentException.class,
                                () -> setPropertyAndWaitForChange(
                                        mCarPropertyManager, mPropertyId,
                                        carPropertyConfig.getPropertyType(),
                                        areaIdConfig.getAreaId(), valueToSet));
                    }
                }
            }
        }
    }

    private void verifyFloatPropertySetter() {
        Collection<T> possibleValues = getPossibleFloatValues();
        if (!possibleValues.isEmpty()) {
            for (T valueToSet : possibleValues) {
                for (int areaId : getCarPropertyConfig().getAreaIds()) {
                    verifySetProperty(areaId, valueToSet);
                }
            }
        }
    }

    private void verifySetProperty(int areaId, T valueToSet) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
            Log.w(TAG, "Property: " + mPropertyName + " will be altered during the test and it is"
                    + " not possible to restore.");
            verifySetPropertyOkayOrThrowExpectedExceptions(areaId, valueToSet);
            return;
        }
        CarPropertyValue<T> currentCarPropertyValue = mCarPropertyManager.getProperty(mPropertyId,
                areaId);
        verifyCarPropertyValue(currentCarPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_GETTER);
        if (valueEquals(valueToSet, currentCarPropertyValue.getValue())) {
            return;
        }
        CarPropertyValue<T> updatedCarPropertyValue = setPropertyAndWaitForChange(
                mCarPropertyManager, mPropertyId, carPropertyConfig.getPropertyType(), areaId,
                valueToSet);
        verifyCarPropertyValue(updatedCarPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
    }

    private void verifyHvacTemperatureValueSuggestionSetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        CarPropertyConfig<?> hvacTemperatureSetCarPropertyConfig =
                mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        if (hvacTemperatureSetCarPropertyConfig == null) {
            return;
        }
        List<Integer> hvacTemperatureSetConfigArray =
                hvacTemperatureSetCarPropertyConfig.getConfigArray();
        float minTempInCelsius = hvacTemperatureSetConfigArray.get(0).floatValue() / 10f;
        float minTempInFahrenheit = hvacTemperatureSetConfigArray.get(3).floatValue() / 10f;

        Float[] temperatureRequest = new Float[] {
            /* requestedValue = */ minTempInCelsius,
            /* units = */ (float) 0x30, // VehicleUnit#CELSIUS
            /* suggestedValueInCelsius = */ 0f,
            /* suggestedValueInFahrenheit = */ 0f
        };
        Float[] expectedTemperatureResponse = new Float[] {
            /* requestedValue = */ minTempInCelsius,
            /* units = */ (float) 0x30, // VehicleUnit#CELSIUS
            /* suggestedValueInCelsius = */ minTempInCelsius,
            /* suggestedValueInFahrenheit = */ minTempInFahrenheit
        };
        for (int areaId: carPropertyConfig.getAreaIds()) {
            CarPropertyValue<Float[]> updatedCarPropertyValue = setPropertyAndWaitForChange(
                    mCarPropertyManager, mPropertyId, Float[].class, areaId,
                    temperatureRequest, expectedTemperatureResponse);
            verifyCarPropertyValue(updatedCarPropertyValue, areaId,
                    CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
            verifyHvacTemperatureValueSuggestionResponse(updatedCarPropertyValue.getValue());
        }
    }

    private void verifySetPropertyOkayOrThrowExpectedExceptions(int areaId, T valueToSet) {
        try {
            mCarPropertyManager.setProperty(mPropertyType, mPropertyId, areaId, valueToSet);
        } catch (PropertyNotAvailableAndRetryException e) {
        } catch (PropertyNotAvailableException e) {
            verifyPropertyNotAvailableException(e);
        } catch (CarInternalErrorException e) {
            verifyInternalErrorException(e);
        } catch (Exception e) {
            assertWithMessage("Unexpected exception thrown when trying to setProperty on "
                    + mPropertyName + ": " + e).fail();
        }
    }

    private void verifySetNotAvailable() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAccess() != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
            return;
        }
        for (int areaId : carPropertyConfig.getAreaIds()) {
            CarPropertyValue<T> currentValue = null;
            try {
                // getProperty may/may not throw exception when the property is not available.
                currentValue = mCarPropertyManager.getProperty(mPropertyId, areaId);
                T valueToSet = getDefaultValue(mPropertyType);
                if (valueToSet == null) {
                    assertWithMessage("Testing mixed type property is not supported").fail();
                }
                verifySetProperty(areaId, valueToSet);
            } catch (Exception e) {
                // In normal cases, this should throw PropertyNotAvailableException.
                // In rare cases, the value we are setting is the same as the current value,
                // which makes the set operation a no-op. So it is possible that no exception
                // is thrown here.
                // It is also possible that this may throw IllegalArgumentException if the value to
                // set is not valid.
                assertWithMessage("If exception is thrown for setting hvac property that is not "
                        + "available, the exception type is correct").that(
                        e.getClass()).isAnyOf(PropertyNotAvailableException.class,
                        IllegalArgumentException.class);
            }
            if (currentValue == null) {
                // If the property is not available for getting, continue.
                continue;
            }
            CarPropertyValue<T> newValue = mCarPropertyManager.getProperty(mPropertyId, areaId);
            assertWithMessage("setting HVAC dependent property: " + mPropertyName
                    + "  while hvac power is off must have no effect").that(newValue.getValue())
                    .isEqualTo(currentValue.getValue());
        }

    }

    private static int getUpdatesPerAreaId(int changeMode) {
        return changeMode != CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS
                ? 1 : 2;
    }

    private static long getRegisterCallbackTimeoutMillis(int changeMode, float minSampleRate) {
        long timeoutMillis = 1500;
        if (changeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            float secondsToMillis = 1_000;
            long bufferMillis = 1_000; // 1 second
            timeoutMillis = ((long) ((1.0f / minSampleRate) * secondsToMillis
                    * getUpdatesPerAreaId(changeMode))) + bufferMillis;
        }
        return timeoutMillis;
    }

    private void verifyCarPropertyValueCallback() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
            verifyCallbackFails();
            return;
        }
        int updatesPerAreaId = getUpdatesPerAreaId(mChangeMode);
        long timeoutMillis = getRegisterCallbackTimeoutMillis(mChangeMode,
                carPropertyConfig.getMinSampleRate());

        CarPropertyValueCallback carPropertyValueCallback = new CarPropertyValueCallback(
                mPropertyName, carPropertyConfig.getAreaIds(), updatesPerAreaId, timeoutMillis);
        SparseArray<List<CarPropertyValue<?>>> areaIdToCarPropertyValues;
        try {
            assertWithMessage("Failed to register callback for " + mPropertyName).that(
                    mCarPropertyManager.registerCallback(carPropertyValueCallback, mPropertyId,
                            carPropertyConfig.getMaxSampleRate())).isTrue();
            areaIdToCarPropertyValues = carPropertyValueCallback.getAreaIdToCarPropertyValues();
        } finally { // TODO(b/269891334): finally block can be removed once bug is fixed.
            mCarPropertyManager.unregisterCallback(carPropertyValueCallback, mPropertyId);
        }

        for (int areaId : carPropertyConfig.getAreaIds()) {
            List<CarPropertyValue<?>> carPropertyValues = areaIdToCarPropertyValues.get(areaId);
            assertWithMessage(
                    mPropertyName + " callback value list is null for area ID: " + areaId).that(
                    carPropertyValues).isNotNull();
            assertWithMessage(mPropertyName + " callback values did not receive " + updatesPerAreaId
                    + " updates for area ID: " + areaId).that(carPropertyValues.size()).isAtLeast(
                    updatesPerAreaId);
            for (CarPropertyValue<?> carPropertyValue : carPropertyValues) {
                verifyCarPropertyValue(carPropertyValue, carPropertyValue.getAreaId(),
                        CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
                if (mPropertyId == VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION) {
                    verifyHvacTemperatureValueSuggestionResponse(
                            (Float[]) carPropertyValue.getValue());
                }
            }
        }
    }

    private void verifyCallbackFails() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        int updatesPerAreaId = getUpdatesPerAreaId(mChangeMode);
        long timeoutMillis = getRegisterCallbackTimeoutMillis(mChangeMode,
                carPropertyConfig.getMinSampleRate());

        CarPropertyValueCallback carPropertyValueCallback = new CarPropertyValueCallback(
                mPropertyName, carPropertyConfig.getAreaIds(), updatesPerAreaId, timeoutMillis);
        assertThrows(
                mPropertyName
                        + " is a write_only property so registerCallback should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () -> mCarPropertyManager.registerCallback(carPropertyValueCallback, mPropertyId,
                    carPropertyConfig.getMaxSampleRate()));
    }

    private void verifyCarPropertyConfig() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(mPropertyName + " CarPropertyConfig must have correct property ID")
                .that(carPropertyConfig.getPropertyId())
                .isEqualTo(mPropertyId);
        int access = carPropertyConfig.getAccess();
        if (mAccess == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
            assertWithMessage(
                            mPropertyName
                                    + " must be "
                                    + accessToString(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ)
                                    + ", "
                                    + accessToString(
                                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE)
                                    + ", or "
                                    + accessToString(
                                            CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE))
                    .that(access)
                    .isIn(
                            ImmutableSet.of(
                                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                                    CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE));
        } else {
            assertWithMessage(mPropertyName + " must be " + accessToString(mAccess))
                    .that(access)
                    .isEqualTo(mAccess);
        }
        assertWithMessage(mPropertyName + " must be " + areaTypeToString(mAreaType))
                .that(carPropertyConfig.getAreaType())
                .isEqualTo(mAreaType);
        assertWithMessage(mPropertyName + " must be " + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getChangeMode())
                .isEqualTo(mChangeMode);
        assertWithMessage(mPropertyName + " must be " + mPropertyType + " type property")
                .that(carPropertyConfig.getPropertyType())
                .isEqualTo(mPropertyType);

        int[] areaIds = carPropertyConfig.getAreaIds();
        assertWithMessage(mPropertyName + "'s must have at least 1 area ID defined")
                .that(areaIds.length).isAtLeast(1);
        assertWithMessage(mPropertyName + "'s area IDs must all be unique: " + Arrays.toString(
                areaIds)).that(ImmutableSet.copyOf(Arrays.stream(
                areaIds).boxed().collect(Collectors.toList())).size()
                == areaIds.length).isTrue();

        if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL) {
            assertWithMessage(
                            mPropertyName
                                    + "'s AreaIds must contain a single 0 since it is "
                                    + areaTypeToString(mAreaType))
                    .that(areaIds)
                    .isEqualTo(new int[] {0});
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_WHEEL_AREA_IDS);
            verifyNoAreaOverlapInAreaIds(WHEEL_AREAS);
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_WINDOW_AREA_IDS);
            verifyNoAreaOverlapInAreaIds(WINDOW_AREAS);
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_MIRROR_AREA_IDS);
            verifyNoAreaOverlapInAreaIds(MIRROR_AREAS);
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_SEAT
                && mPropertyId != VehiclePropertyIds.INFO_DRIVER_SEAT) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_SEAT_AREA_IDS);
            verifyNoAreaOverlapInAreaIds(SEAT_AREAS);
        } else if (mAreaType == VehicleAreaType.VEHICLE_AREA_TYPE_DOOR) {
            verifyValidAreaIdsForAreaType(ALL_POSSIBLE_DOOR_AREA_IDS);
            verifyNoAreaOverlapInAreaIds(DOOR_AREAS);
        }
        if (mAreaIdsVerifier.isPresent()) {
            mAreaIdsVerifier.get().verify(areaIds);
        }

        if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS) {
            verifyContinuousCarPropertyConfig();
        } else {
            verifyNonContinuousCarPropertyConfig();
        }

        mCarPropertyConfigVerifier.ifPresent(
                carPropertyConfigVerifier -> carPropertyConfigVerifier.verify(carPropertyConfig));

        if (!mPossibleConfigArrayValues.isEmpty()) {
            assertWithMessage(mPropertyName + " configArray must specify supported values")
                    .that(carPropertyConfig.getConfigArray().size())
                    .isGreaterThan(0);
            for (Integer supportedValue : carPropertyConfig.getConfigArray()) {
                assertWithMessage(
                                mPropertyName
                                        + " configArray value must be a defined "
                                        + "value: "
                                        + supportedValue)
                        .that(supportedValue)
                        .isIn(mPossibleConfigArrayValues);
            }
        }

        mConfigArrayVerifier.ifPresent(configArrayVerifier -> configArrayVerifier.verify(
                carPropertyConfig.getConfigArray()));

        if (mPossibleConfigArrayValues.isEmpty() && !mConfigArrayVerifier.isPresent()
                && !mCarPropertyConfigVerifier.isPresent()) {
            assertWithMessage(mPropertyName + " configArray is undefined, so it must be empty")
                    .that(carPropertyConfig.getConfigArray().size())
                    .isEqualTo(0);
        }

        for (int areaId : areaIds) {
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
            if (areaIdMinValue != null || areaIdMaxValue != null) {
                assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + "'s max value must be >= min value")
                        .that(verifyMaxAndMin(areaIdMinValue, areaIdMaxValue))
                        .isTrue();
            }

            if (mRequirePropertyValueToBeInConfigArray) {
                List<?> supportedEnumValues = carPropertyConfig.getAreaIdConfig(
                        areaId).getSupportedEnumValues();
                assertWithMessage(mPropertyName + " - areaId: " + areaId
                        + "'s supported enum values must match the values in the config array.")
                        .that(carPropertyConfig.getConfigArray())
                        .containsExactlyElementsIn(supportedEnumValues);
            }

            if (mChangeMode == CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE
                    && !mAllPossibleEnumValues.isEmpty()) {
                List<?> supportedEnumValues = carPropertyConfig.getAreaIdConfig(
                        areaId).getSupportedEnumValues();
                assertWithMessage(mPropertyName + " - areaId: " + areaId
                        + "'s supported enum values must be defined").that(
                        supportedEnumValues).isNotEmpty();
                assertWithMessage(mPropertyName + " - areaId: " + areaId
                        + "'s supported enum values must not contain any duplicates").that(
                        supportedEnumValues).containsNoDuplicates();
                assertWithMessage(
                        mPropertyName + " - areaId: " + areaId + "'s supported enum values "
                                + supportedEnumValues + " must all exist in all possible enum set "
                                + mAllPossibleEnumValues).that(
                        mAllPossibleEnumValues.containsAll(supportedEnumValues)).isTrue();
            } else {
                assertWithMessage(mPropertyName + " - areaId: " + areaId
                        + "'s supported enum values must be empty since property does not support"
                        + " an enum").that(
                        carPropertyConfig.getAreaIdConfig(
                                areaId).getSupportedEnumValues()).isEmpty();
            }
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

    private void verifyContinuousCarPropertyConfig() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                        mPropertyName
                                + " must define max sample rate since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMaxSampleRate())
                .isGreaterThan(0);
        assertWithMessage(
                        mPropertyName
                                + " must define min sample rate since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMinSampleRate())
                .isGreaterThan(0);
        assertWithMessage(mPropertyName + " max sample rate must be >= min sample rate")
                .that(carPropertyConfig.getMaxSampleRate() >= carPropertyConfig.getMinSampleRate())
                .isTrue();
    }

    private void verifyNonContinuousCarPropertyConfig() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                        mPropertyName
                                + " must define max sample rate as 0 since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMaxSampleRate())
                .isEqualTo(0);
        assertWithMessage(
                        mPropertyName
                                + " must define min sample rate as 0 since change mode is "
                                + changeModeToString(mChangeMode))
                .that(carPropertyConfig.getMinSampleRate())
                .isEqualTo(0);
    }

    private void verifyCarPropertyValueGetter() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
            verifyGetPropertyFails();
            return;
        }
        for (int areaId : carPropertyConfig.getAreaIds()) {
            CarPropertyValue<?> carPropertyValue = null;
            try {
                carPropertyValue = mCarPropertyManager.getProperty(mPropertyId, areaId);
            } catch (PropertyNotAvailableException e) {
                verifyPropertyNotAvailableException(e);
                // If the property is not available for getting, continue.
                continue;
            } catch (CarInternalErrorException e) {
                verifyInternalErrorException(e);
                continue;
            }

            verifyCarPropertyValue(carPropertyValue, areaId, CAR_PROPERTY_VALUE_SOURCE_GETTER);
            if (mPropertyId == VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION) {
                verifyHvacTemperatureValueSuggestionResponse((Float[]) carPropertyValue.getValue());
            }
        }
    }

    private void verifyGetPropertyFails() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertThrows(
                mPropertyName
                        + " is a write_only property so getProperty should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () -> mCarPropertyManager.getProperty(mPropertyId,
                        carPropertyConfig.getAreaIds()[0]));
    }

    private static void verifyPropertyNotAvailableException(PropertyNotAvailableException e) {
        assertThat(((PropertyNotAvailableException) e).getDetailedErrorCode())
                .isIn(PROPERTY_NOT_AVAILABLE_ERROR_CODES);
        int vendorErrorCode = e.getVendorErrorCode();
        assertThat(vendorErrorCode).isAtLeast(VENDOR_ERROR_CODE_MINIMUM_VALUE);
        assertThat(vendorErrorCode).isAtMost(VENDOR_ERROR_CODE_MAXIMUM_VALUE);
    }

    private static void verifyInternalErrorException(CarInternalErrorException e) {
        int vendorErrorCode = e.getVendorErrorCode();
        assertThat(vendorErrorCode).isAtLeast(VENDOR_ERROR_CODE_MINIMUM_VALUE);
        assertThat(vendorErrorCode).isAtMost(VENDOR_ERROR_CODE_MAXIMUM_VALUE);
    }

    private void verifyCarPropertyValue(CarPropertyValue<?> carPropertyValue, int expectedAreaId,
            String source) {
        verifyCarPropertyValue(carPropertyValue.getPropertyId(),
                carPropertyValue.getAreaId(), carPropertyValue.getStatus(),
                carPropertyValue.getTimestamp(), (T) carPropertyValue.getValue(), expectedAreaId,
                source);
    }

    private void verifyCarPropertyValue(
            int propertyId, int areaId, int status, long timestampNanos, T value,
            int expectedAreaId, String source) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        mCarPropertyValueVerifier.ifPresent(
                propertyValueVerifier -> propertyValueVerifier.verify(carPropertyConfig, propertyId,
                        areaId, timestampNanos, value));
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " value must have correct property ID")
                .that(propertyId)
                .isEqualTo(mPropertyId);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " value must have correct area id: "
                                + areaId)
                .that(areaId)
                .isEqualTo(expectedAreaId);
        assertWithMessage(mPropertyName + " - areaId: " + areaId + " - source: " + source
                + " area ID must be in carPropertyConfig#getAreaIds()").that(Arrays.stream(
                carPropertyConfig.getAreaIds()).boxed().collect(Collectors.toList()).contains(
               areaId)).isTrue();
        assertWithMessage(
                         mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " value must have AVAILABLE status")
                .that(status)
                .isEqualTo(CarPropertyValue.STATUS_AVAILABLE);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " timestamp must use the SystemClock.elapsedRealtimeNanos() time"
                                + " base")
                .that(timestampNanos)
                .isAtLeast(0);
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " timestamp must use the SystemClock.elapsedRealtimeNanos() time"
                                + " base")
                .that(timestampNanos)
                .isLessThan(SystemClock.elapsedRealtimeNanos());
        assertWithMessage(
                        mPropertyName
                                + " - areaId: "
                                + areaId
                                + " - source: "
                                + source
                                + " must return "
                                + mPropertyType
                                + " type value")
                .that(value.getClass())
                .isEqualTo(mPropertyType);

        if (mRequirePropertyValueToBeInConfigArray) {
            assertWithMessage(
                            mPropertyName
                                    + " - areaId: "
                                    + areaId
                                    + " - source: "
                                    + source
                                    + " value must be listed in configArray,"
                                    + " configArray:")
                    .that(carPropertyConfig.getConfigArray())
                    .contains(value);
        }

        List<T> supportedEnumValues = carPropertyConfig.getAreaIdConfig(
                areaId).getSupportedEnumValues();
        if (!supportedEnumValues.isEmpty()) {
            assertWithMessage(mPropertyName + " - areaId: " + areaId + " - source: " + source
                    + " value must be listed in getSupportedEnumValues()").that(value).isIn(
                    supportedEnumValues);
        }

        T areaIdMinValue = (T) carPropertyConfig.getMinValue(areaId);
        T areaIdMaxValue = (T) carPropertyConfig.getMaxValue(areaId);
        if (areaIdMinValue != null && areaIdMaxValue != null) {
            assertWithMessage(
                    "Property value: " + value + " must be between the max: "
                            + areaIdMaxValue + " and min: " + areaIdMinValue
                            + " values for area ID: " + Integer.toHexString(areaId)).that(
                            verifyValueInRange(
                                    areaIdMinValue,
                                    areaIdMaxValue,
                                    (T) value))
                    .isTrue();
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

    private static ImmutableSet<Integer> generateAllPossibleAreaIds(ImmutableSet<Integer> areas) {
        ImmutableSet.Builder<Integer> allPossibleAreaIdsBuilder = ImmutableSet.builder();
        for (int i = 1; i <= areas.size(); i++) {
            allPossibleAreaIdsBuilder.addAll(Sets.combinations(areas, i).stream().map(areaCombo -> {
                Integer possibleAreaId = 0;
                for (Integer area : areaCombo) {
                    possibleAreaId |= area;
                }
                return possibleAreaId;
            }).collect(Collectors.toList()));
        }
        return allPossibleAreaIdsBuilder.build();
    }

    private void verifyValidAreaIdsForAreaType(ImmutableSet<Integer> allPossibleAreaIds) {
        for (int areaId : getCarPropertyConfig().getAreaIds()) {
            assertWithMessage(
                    mPropertyName + "'s area ID must be a valid " + areaTypeToString(mAreaType)
                            + " area ID").that(areaId).isIn(allPossibleAreaIds);
        }
    }

    private void verifyNoAreaOverlapInAreaIds(ImmutableSet<Integer> areas) {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAreaIds().length < 2) {
            return;
        }
        ImmutableSet<Integer> areaIds = ImmutableSet.copyOf(Arrays.stream(
                carPropertyConfig.getAreaIds()).boxed().collect(Collectors.toList()));
        List<Integer> areaIdOverlapCheckResults = Sets.combinations(areaIds, 2).stream().map(
                areaIdPair -> {
                    List<Integer> areaIdPairAsList = areaIdPair.stream().collect(
                            Collectors.toList());
                    return areaIdPairAsList.get(0) & areaIdPairAsList.get(1);
                }).collect(Collectors.toList());

        assertWithMessage(
                mPropertyName + " area IDs: " + Arrays.toString(carPropertyConfig.getAreaIds())
                        + " must contain each area only once (e.g. no bitwise AND overlap) for "
                        + "the area type: " + areaTypeToString(mAreaType)).that(
                Collections.frequency(areaIdOverlapCheckResults, 0)
                        == areaIdOverlapCheckResults.size()).isTrue();
    }

    private void verifyPermissionNotGrantedException() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        assertWithMessage(
                    mPropertyName
                            + " - property ID: "
                            + mPropertyId
                            + " CarPropertyConfig should not be accessible without permissions.")
                .that(mCarPropertyManager.getCarPropertyConfig(mPropertyId))
                .isNull();

        int access = carPropertyConfig.getAccess();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            if (access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                    || access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                assertThrows(
                        mPropertyName
                                + " - property ID: "
                                + mPropertyId
                                + " - area ID: "
                                + areaId
                                + " should not be able to be read without permissions.",
                        SecurityException.class,
                        () -> mCarPropertyManager.getProperty(mPropertyId, areaId));
            }
            if (access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                    || access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                assertThrows(
                        mPropertyName
                                + " - property ID: "
                                + mPropertyId
                                + " - area ID: "
                                + areaId
                                + " should not be able to be written to without permissions.",
                        SecurityException.class,
                        () -> mCarPropertyManager.setProperty(mPropertyType, mPropertyId, areaId,
                                getDefaultValue(mPropertyType)));
            }
        }

        if (access == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
            return;
        }
        int updatesPerAreaId = getUpdatesPerAreaId(mChangeMode);
        long timeoutMillis = getRegisterCallbackTimeoutMillis(mChangeMode,
                carPropertyConfig.getMinSampleRate());

        CarPropertyValueCallback carPropertyValueCallback = new CarPropertyValueCallback(
                mPropertyName, carPropertyConfig.getAreaIds(), updatesPerAreaId, timeoutMillis);
        try {
            assertWithMessage(
                    mPropertyName
                            + " - property ID: "
                            + mPropertyId
                            + " should not be able to be listened to without permissions.")
                    .that(mCarPropertyManager.registerCallback(
                            carPropertyValueCallback, mPropertyId,
                            carPropertyConfig.getMaxSampleRate())).isFalse();
        } finally {
            // TODO(b/269891334): registerCallback needs to fix exception handling
            mCarPropertyManager.unregisterCallback(carPropertyValueCallback, mPropertyId);
        }
    }

    private void verifyHvacTemperatureValueSuggestionResponse(Float[] temperatureSuggestion) {
        Float suggestedTempInCelsius = temperatureSuggestion[2];
        Float suggestedTempInFahrenheit = temperatureSuggestion[3];
        CarPropertyConfig<?> hvacTemperatureSetCarPropertyConfig =
                mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.HVAC_TEMPERATURE_SET);
        if (hvacTemperatureSetCarPropertyConfig == null) {
            return;
        }
        List<Integer> hvacTemperatureSetConfigArray =
                hvacTemperatureSetCarPropertyConfig.getConfigArray();

        Integer minTempInCelsiusTimesTen =
                hvacTemperatureSetConfigArray.get(0);
        Integer maxTempInCelsiusTimesTen =
                hvacTemperatureSetConfigArray.get(1);
        Integer incrementInCelsiusTimesTen =
                hvacTemperatureSetConfigArray.get(2);
        verifyHvacTemperatureIsValid(suggestedTempInCelsius, minTempInCelsiusTimesTen,
                maxTempInCelsiusTimesTen, incrementInCelsiusTimesTen);

        Integer minTempInFahrenheitTimesTen =
                hvacTemperatureSetConfigArray.get(3);
        Integer maxTempInFahrenheitTimesTen =
                hvacTemperatureSetConfigArray.get(4);
        Integer incrementInFahrenheitTimesTen =
                hvacTemperatureSetConfigArray.get(5);
        verifyHvacTemperatureIsValid(suggestedTempInFahrenheit, minTempInFahrenheitTimesTen,
                maxTempInFahrenheitTimesTen, incrementInFahrenheitTimesTen);

        int suggestedTempInCelsiusTimesTen = suggestedTempInCelsius.intValue() * 10;
        int suggestedTempInFahrenheitTimesTen = suggestedTempInFahrenheit.intValue() * 10;
        int numIncrementsCelsius =
                (suggestedTempInCelsiusTimesTen - minTempInCelsiusTimesTen)
                        / incrementInCelsiusTimesTen;
        int numIncrementsFahrenheit =
                (suggestedTempInFahrenheitTimesTen - minTempInFahrenheitTimesTen)
                        / incrementInFahrenheitTimesTen;
        assertWithMessage(
                        "The temperature in Celsius must be equivalent to the temperature in"
                            + " Fahrenheit.")
                .that(numIncrementsFahrenheit)
                .isEqualTo(numIncrementsCelsius);
    }

    public static void verifyHvacTemperatureIsValid(float temp, int minTempTimesTen,
            int maxTempTimesTen, int incrementTimesTen) {
        Float tempMultiplied = temp * 10.0f;
        int intTempTimesTen = tempMultiplied.intValue();
        assertWithMessage(
                        "The temperature value " + intTempTimesTen + " must be at least "
                            + minTempTimesTen + " and at most " + maxTempTimesTen)
                .that(intTempTimesTen >= minTempTimesTen && intTempTimesTen <= maxTempTimesTen)
                .isTrue();

        int remainder = (intTempTimesTen - minTempTimesTen) % incrementTimesTen;
        assertWithMessage(
                        "The temperature value " + intTempTimesTen
                            + " is not a valid temperature value. Valid values start from "
                            + minTempTimesTen
                            + " and increment by " + incrementTimesTen
                            + " until the max temperature setting of " + maxTempTimesTen)
                .that(remainder)
                .isEqualTo(0);
    }

    public interface ConfigArrayVerifier {
        void verify(List<Integer> configArray);
    }

    public interface CarPropertyValueVerifier<T> {
        void verify(CarPropertyConfig<T> carPropertyConfig, int propertyId, int areaId,
                long timestampNanos, T value);
    }

    public interface AreaIdsVerifier {
        void verify(int[] areaIds);
    }

    public interface CarPropertyConfigVerifier {
        void verify(CarPropertyConfig<?> carPropertyConfig);
    }

    public static class Builder<T> {
        private final int mPropertyId;
        private final int mAccess;
        private final int mAreaType;
        private final int mChangeMode;
        private final Class<T> mPropertyType;
        private final CarPropertyManager mCarPropertyManager;
        private boolean mRequiredProperty = false;
        private Optional<ConfigArrayVerifier> mConfigArrayVerifier = Optional.empty();
        private Optional<CarPropertyValueVerifier<T>> mCarPropertyValueVerifier = Optional.empty();
        private Optional<AreaIdsVerifier> mAreaIdsVerifier = Optional.empty();
        private Optional<CarPropertyConfigVerifier> mCarPropertyConfigVerifier = Optional.empty();
        private ImmutableSet<Integer> mPossibleConfigArrayValues = ImmutableSet.of();
        private ImmutableSet<T> mAllPossibleEnumValues = ImmutableSet.of();
        private ImmutableSet<T> mAllPossibleUnwritableValues = ImmutableSet.of();
        private boolean mRequirePropertyValueToBeInConfigArray = false;
        private boolean mVerifySetterWithConfigArrayValues = false;
        private boolean mRequireMinMaxValues = false;
        private boolean mRequireMinValuesToBeZero = false;
        private boolean mRequireZeroToBeContainedInMinMaxRanges = false;
        private boolean mPossiblyDependentOnHvacPowerOn = false;
        private final ImmutableSet.Builder<String> mReadPermissionsBuilder = ImmutableSet.builder();
        private final ImmutableList.Builder<ImmutableSet<String>> mWritePermissionsBuilder =
                ImmutableList.builder();

        private Builder(int propertyId, int access, int areaType, int changeMode,
                Class<T> propertyType, CarPropertyManager carPropertyManager) {
            mPropertyId = propertyId;
            mAccess = access;
            mAreaType = areaType;
            mChangeMode = changeMode;
            mPropertyType = propertyType;
            mCarPropertyManager = carPropertyManager;
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
                CarPropertyValueVerifier<T> carPropertyValueVerifier) {
            mCarPropertyValueVerifier = Optional.of(carPropertyValueVerifier);
            return this;
        }

        public Builder<T> setAreaIdsVerifier(AreaIdsVerifier areaIdsVerifier) {
            mAreaIdsVerifier = Optional.of(areaIdsVerifier);
            return this;
        }

        public Builder<T> setCarPropertyConfigVerifier(
                CarPropertyConfigVerifier carPropertyConfigVerifier) {
            mCarPropertyConfigVerifier = Optional.of(carPropertyConfigVerifier);
            return this;
        }

        public Builder<T> setPossibleConfigArrayValues(
                ImmutableSet<Integer> possibleConfigArrayValues) {
            mPossibleConfigArrayValues = possibleConfigArrayValues;
            return this;
        }

        public Builder<T> setAllPossibleEnumValues(ImmutableSet<T> allPossibleEnumValues) {
            mAllPossibleEnumValues = allPossibleEnumValues;
            return this;
        }

        public Builder<T> setAllPossibleUnwritableValues(
                ImmutableSet<T> allPossibleUnwritableValues) {
            mAllPossibleUnwritableValues = allPossibleUnwritableValues;
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

        public Builder<T> setPossiblyDependentOnHvacPowerOn() {
            mPossiblyDependentOnHvacPowerOn = true;
            return this;
        }

        public Builder<T> addReadPermission(String readPermission) {
            mReadPermissionsBuilder.add(readPermission);
            return this;
        }

        /**
         * Add a single permission that alone can be used to update the property. Any set of
         * permissions in {@code mWritePermissionsBuilder} can be used to set the property.
         *
         * @param writePermission a permission used to update the property
         */
        public Builder<T> addWritePermission(String writePermission) {
            mWritePermissionsBuilder.add(ImmutableSet.of(writePermission));
            return this;
        }

        /**
         * Add a set of permissions that together can be used to update the property. Any set of
         * permissions in {@code mWritePermissionsBuilder} can be used to set the property.
         *
         * @param writePermissionSet a set of permissions that together can be used to update the
         * property.
         */
        public Builder<T> addWritePermission(ImmutableSet<String> writePermissionSet) {
            mWritePermissionsBuilder.add(writePermissionSet);
            return this;
        }

        public VehiclePropertyVerifier<T> build() {
            return new VehiclePropertyVerifier<>(
                    mCarPropertyManager,
                    mPropertyId,
                    mAccess,
                    mAreaType,
                    mChangeMode,
                    mPropertyType,
                    mRequiredProperty,
                    mConfigArrayVerifier,
                    mCarPropertyValueVerifier,
                    mAreaIdsVerifier,
                    mCarPropertyConfigVerifier,
                    mPossibleConfigArrayValues,
                    mAllPossibleEnumValues,
                    mAllPossibleUnwritableValues,
                    mRequirePropertyValueToBeInConfigArray,
                    mVerifySetterWithConfigArrayValues,
                    mRequireMinMaxValues,
                    mRequireMinValuesToBeZero,
                    mRequireZeroToBeContainedInMinMaxRanges,
                    mPossiblyDependentOnHvacPowerOn,
                    mReadPermissionsBuilder.build(),
                    mWritePermissionsBuilder.build());
        }
    }

    private static class CarPropertyValueCallback implements
            CarPropertyManager.CarPropertyEventCallback {
        private final String mPropertyName;
        private final int[] mAreaIds;
        private final int mTotalCarPropertyValuesPerAreaId;
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final SparseArray<List<CarPropertyValue<?>>> mAreaIdToCarPropertyValues =
                new SparseArray<>();
        private final long mTimeoutMillis;

        CarPropertyValueCallback(String propertyName, int[] areaIds,
                int totalCarPropertyValuesPerAreaId, long timeoutMillis) {
            mPropertyName = propertyName;
            mAreaIds = areaIds;
            mTotalCarPropertyValuesPerAreaId = totalCarPropertyValuesPerAreaId;
            mTimeoutMillis = timeoutMillis;
            synchronized (mLock) {
                for (int areaId : mAreaIds) {
                    mAreaIdToCarPropertyValues.put(areaId, new ArrayList<>());
                }
            }
        }

        public SparseArray<List<CarPropertyValue<?>>> getAreaIdToCarPropertyValues() {
            boolean awaitSuccess = false;
            try {
                awaitSuccess = mCountDownLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onChangeEvent callback(s) for " + mPropertyName
                        + " threw an exception: " + e).fail();
            }
            synchronized (mLock) {
                assertWithMessage("Never received " + mTotalCarPropertyValuesPerAreaId
                        + "  CarPropertyValues for all " + mPropertyName + "'s areaIds: "
                        + Arrays.toString(mAreaIds) + " before " + mTimeoutMillis + " ms timeout - "
                        + mAreaIdToCarPropertyValues).that(awaitSuccess).isTrue();
                return mAreaIdToCarPropertyValues.clone();
            }
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            synchronized (mLock) {
                if (hasEnoughCarPropertyValuesForEachAreaIdLocked()) {
                    return;
                }
                mAreaIdToCarPropertyValues.get(carPropertyValue.getAreaId()).add(carPropertyValue);
                if (hasEnoughCarPropertyValuesForEachAreaIdLocked()) {
                    mCountDownLatch.countDown();
                }
            }
        }

        @GuardedBy("mLock")
        private boolean hasEnoughCarPropertyValuesForEachAreaIdLocked() {
            for (int areaId : mAreaIds) {
                List<CarPropertyValue<?>> carPropertyValues = mAreaIdToCarPropertyValues.get(
                        areaId);
                if (carPropertyValues == null
                        || carPropertyValues.size() < mTotalCarPropertyValuesPerAreaId) {
                    return false;
                }
            }
            return true;
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
        private T mReceivedValue = null;

        SetterCallback(int propertyId, int areaId, T expectedSetValue) {
            mPropertyId = propertyId;
            mPropertyName = VehiclePropertyIds.toString(propertyId);
            mAreaId = areaId;
            mExpectedSetValue = expectedSetValue;
        }

        private String valueToString(T value) {
            if (value.getClass().isArray()) {
                return Arrays.toString((Object[]) value);
            }
            return value.toString();
        }

        public CarPropertyValue<?> waitForUpdatedCarPropertyValue() {
            try {
                assertWithMessage(
                        "Never received onChangeEvent(s) for " + mPropertyName + " new value: "
                                + valueToString(mExpectedSetValue) + " before 5s timeout."
                                + " Received: "
                                + (mReceivedValue == null
                                    ? "No value"
                                    : valueToString(mReceivedValue)))
                        .that(mCountDownLatch.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onChangeEvent set callback for "
                        + mPropertyName + " threw an exception: " + e).fail();
            }
            return mUpdatedCarPropertyValue;
        }

        @Override
        public void onChangeEvent(CarPropertyValue carPropertyValue) {
            if (mUpdatedCarPropertyValue != null || carPropertyValue.getPropertyId() != mPropertyId
                    || carPropertyValue.getAreaId() != mAreaId
                    || carPropertyValue.getStatus() != CarPropertyValue.STATUS_AVAILABLE
                    || carPropertyValue.getTimestamp() <= mCreationTimeNanos
                    || carPropertyValue.getTimestamp() >= SystemClock.elapsedRealtimeNanos()) {
                return;
            }
            mReceivedValue = (T) carPropertyValue.getValue();
            if (!valueEquals(mExpectedSetValue, mReceivedValue)) {
                return;
            }
            mUpdatedCarPropertyValue = carPropertyValue;
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
        }
    }

    private static <V> boolean valueEquals(V v1, V v2) {
        return (v1 instanceof Float && floatEquals((Float) v1, (Float) v2))
                || (v1 instanceof Float[] && floatArrayEquals((Float[]) v1, (Float[]) v2))
                || (v1 instanceof Long[] && longArrayEquals((Long[]) v1, (Long[]) v2))
                || (v1 instanceof Integer[] && integerArrayEquals((Integer[]) v1, (Integer[]) v2))
                || v1.equals(v2);
    }

    private static boolean floatEquals(float f1, float f2) {
        return Math.abs(f1 - f2) < FLOAT_INEQUALITY_THRESHOLD;
    }

    private static boolean floatArrayEquals(Float[] f1, Float[] f2) {
        return Arrays.equals(f1, f2);
    }

    private static boolean longArrayEquals(Long[] l1, Long[] l2) {
        return Arrays.equals(l1, l2);
    }

    private static boolean integerArrayEquals(Integer[] i1, Integer[] i2) {
        return Arrays.equals(i1, i2);
    }

    private class CarPropertyCallback implements GetPropertyCallback {
        private final CountDownLatch mCountDownLatch;
        private final int mGetPropertyResultsCount;
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private List<GetPropertyResult<?>> mGetPropertyResults;

        public List<GetPropertyResult<?>> waitForGetPropertyResults() {
            try {
                assertWithMessage("Received " + (mGetPropertyResultsCount
                        - mCountDownLatch.getCount()) + " onSuccess(s), expected "
                        + mGetPropertyResultsCount + " onSuccess(s)").that(mCountDownLatch.await(
                                5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                assertWithMessage("Waiting for onSuccess threw an exception: " + e
                        ).fail();
            }

            return mGetPropertyResults;
        }

        @Override
        public void onSuccess(GetPropertyResult getPropertyResult) {
            synchronized (mLock) {
                mGetPropertyResults.add(getPropertyResult);
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onFailure(PropertyAsyncError getPropertyError) {
            assertWithMessage("PropertyAsyncError with requestId "
                    + getPropertyError.getRequestId() + " returned with async error code: "
                    + getPropertyError.getErrorCode() + " and vendor error code: "
                    + getPropertyError.getVendorErrorCode()).fail();
        }

        CarPropertyCallback(int getPropertyResultsCount) {
            mCountDownLatch = new CountDownLatch(getPropertyResultsCount);
            mGetPropertyResults = new ArrayList<>();
            mGetPropertyResultsCount = getPropertyResultsCount;
        }
    }

    private void verifyGetPropertiesAsync() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        if (carPropertyConfig.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE) {
            verifyGetPropertiesAsyncFails();
            return;
        }

        List<GetPropertyRequest> getPropertyRequests = new ArrayList<>();
        SparseIntArray requestIdToAreaIdMap = new SparseIntArray();
        for (int areaId : carPropertyConfig.getAreaIds()) {
            GetPropertyRequest getPropertyRequest = mCarPropertyManager.generateGetPropertyRequest(
                    mPropertyId, areaId);
            int requestId = getPropertyRequest.getRequestId();
            requestIdToAreaIdMap.put(requestId, areaId);
            getPropertyRequests.add(getPropertyRequest);
        }

        CarPropertyCallback carPropertyCallback = new CarPropertyCallback(
                requestIdToAreaIdMap.size());
        mCarPropertyManager.getPropertiesAsync(getPropertyRequests, /* cancellationSignal: */ null,
                /* callbackExecutor: */ null, carPropertyCallback);
        List<GetPropertyResult<?>> getPropertyResults =
                carPropertyCallback.waitForGetPropertyResults();

        for (GetPropertyResult<?> getPropertyResult : getPropertyResults) {
            int requestId = getPropertyResult.getRequestId();
            int propertyId = getPropertyResult.getPropertyId();
            if (requestIdToAreaIdMap.indexOfKey(requestId) < 0) {
                assertWithMessage("getPropertiesAsync received unknown requestId "
                        + requestId + " with property ID "
                        + VehiclePropertyIds.toString(propertyId)).fail();
            }
            Integer expectedAreaId = requestIdToAreaIdMap.get(requestId);
            verifyCarPropertyValue(propertyId, getPropertyResult.getAreaId(),
                    CarPropertyValue.STATUS_AVAILABLE, getPropertyResult.getTimestampNanos(),
                    (T) getPropertyResult.getValue(), expectedAreaId,
                    CAR_PROPERTY_VALUE_SOURCE_CALLBACK);
            if (mPropertyId == VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION) {
                verifyHvacTemperatureValueSuggestionResponse(
                        (Float[]) getPropertyResult.getValue());
            }
        }
    }

    private void verifyGetPropertiesAsyncFails() {
        CarPropertyConfig<T> carPropertyConfig = getCarPropertyConfig();
        List<GetPropertyRequest> getPropertyRequests = new ArrayList<>();
        GetPropertyRequest getPropertyRequest = mCarPropertyManager.generateGetPropertyRequest(
                    mPropertyId, carPropertyConfig.getAreaIds()[0]);
        getPropertyRequests.add(getPropertyRequest);
        CarPropertyCallback carPropertyCallback = new CarPropertyCallback(
                /* getPropertyResultsCount: */ 1);
        assertThrows(
                mPropertyName
                        + " is a write_only property so getPropertiesAsync should throw an"
                        + " IllegalArgumentException.",
                IllegalArgumentException.class,
                () -> mCarPropertyManager.getPropertiesAsync(getPropertyRequests,
                        /* cancellationSignal: */ null, /* callbackExecutor: */ null,
                        carPropertyCallback));
    }

    private static <U> CarPropertyValue<U> setPropertyAndWaitForChange(
            CarPropertyManager carPropertyManager, int propertyId, Class<U> propertyType,
            int areaId, U valueToSet) {
        return setPropertyAndWaitForChange(carPropertyManager, propertyId, propertyType, areaId,
                valueToSet, valueToSet);
    }

    private static <U> CarPropertyValue<U> setPropertyAndWaitForChange(
            CarPropertyManager carPropertyManager, int propertyId, Class<U> propertyType,
            int areaId, U valueToSet, U expectedValueToGet) {
        SetterCallback setterCallback = new SetterCallback(propertyId, areaId, expectedValueToGet);
        assertWithMessage("Failed to register setter callback for " + VehiclePropertyIds.toString(
                propertyId)).that(carPropertyManager.registerCallback(setterCallback, propertyId,
                CarPropertyManager.SENSOR_RATE_FASTEST)).isTrue();
        try {
            carPropertyManager.setProperty(propertyType, propertyId, areaId, valueToSet);
        } catch (PropertyNotAvailableException e) {
            verifyPropertyNotAvailableException(e);
            return null;
        } catch (CarInternalErrorException e) {
            verifyInternalErrorException(e);
            return null;
        }

        CarPropertyValue<U> carPropertyValue = setterCallback.waitForUpdatedCarPropertyValue();
        carPropertyManager.unregisterCallback(setterCallback, propertyId);
        return carPropertyValue;
    }
}
