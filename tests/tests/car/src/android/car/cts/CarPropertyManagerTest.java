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
 * limitations under the License.
 */

package android.car.cts;

import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.car.Car;
import android.car.EvConnectorType;
import android.car.FuelType;
import android.car.GsrComplianceType;
import android.car.PortLocationType;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehicleAreaWheel;
import android.car.VehicleGear;
import android.car.VehicleIgnitionState;
import android.car.VehiclePropertyIds;
import android.car.VehicleUnit;
import android.car.annotation.ApiRequirements;
import android.car.cts.utils.VehiclePropertyVerifier;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.car.hardware.property.EvChargeState;
import android.car.hardware.property.EvRegenerativeBrakingState;
import android.car.hardware.property.EvStoppingMode;
import android.car.hardware.property.VehicleElectronicTollCollectionCardStatus;
import android.car.hardware.property.VehicleElectronicTollCollectionCardType;
import android.car.hardware.property.VehicleLightState;
import android.car.hardware.property.VehicleLightSwitch;
import android.car.hardware.property.VehicleTurnSignal;
import android.car.test.ApiCheckerRule.Builder;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.CddTest;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot get car related permissions.")
public final class CarPropertyManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarPropertyManagerTest.class.getSimpleName();

    private static final long WAIT_CALLBACK = 1500L;
    private static final int NO_EVENTS = 0;
    private static final int ONCHANGE_RATE_EVENT_COUNTER = 1;
    private static final int UI_RATE_EVENT_COUNTER = 5;
    private static final int FAST_OR_FASTEST_EVENT_COUNTER = 10;
    private static final long ASYNC_WAIT_TIMEOUT_IN_SEC = 15_000;
    private static final ImmutableSet<Integer> PORT_LOCATION_TYPES =
            ImmutableSet.<Integer>builder()
                    .add(
                            PortLocationType.UNKNOWN,
                            PortLocationType.FRONT_LEFT,
                            PortLocationType.FRONT_RIGHT,
                            PortLocationType.REAR_RIGHT,
                            PortLocationType.REAR_LEFT,
                            PortLocationType.FRONT,
                            PortLocationType.REAR)
                    .build();
    private static final ImmutableSet<Integer> VEHICLE_GEARS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleGear.GEAR_UNKNOWN,
                            VehicleGear.GEAR_NEUTRAL,
                            VehicleGear.GEAR_REVERSE,
                            VehicleGear.GEAR_PARK,
                            VehicleGear.GEAR_DRIVE,
                            VehicleGear.GEAR_FIRST,
                            VehicleGear.GEAR_SECOND,
                            VehicleGear.GEAR_THIRD,
                            VehicleGear.GEAR_FOURTH,
                            VehicleGear.GEAR_FIFTH,
                            VehicleGear.GEAR_SIXTH,
                            VehicleGear.GEAR_SEVENTH,
                            VehicleGear.GEAR_EIGHTH,
                            VehicleGear.GEAR_NINTH)
                    .build();
    private static final ImmutableSet<Integer> DISTANCE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.MILLIMETER, VehicleUnit.METER,
                    VehicleUnit.KILOMETER, VehicleUnit.MILE).build();
    private static final ImmutableSet<Integer> VOLUME_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.MILLILITER, VehicleUnit.LITER,
                    VehicleUnit.US_GALLON, VehicleUnit.IMPERIAL_GALLON).build();
    private static final ImmutableSet<Integer> PRESSURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.KILOPASCAL, VehicleUnit.PSI,
                    VehicleUnit.BAR).build();
    private static final ImmutableSet<Integer> BATTERY_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.WATT_HOUR, VehicleUnit.AMPERE_HOURS,
                    VehicleUnit.KILOWATT_HOUR).build();
    private static final ImmutableSet<Integer> SPEED_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.METER_PER_SEC,
                    VehicleUnit.MILES_PER_HOUR, VehicleUnit.KILOMETERS_PER_HOUR).build();
    private static final ImmutableSet<Integer> TURN_SIGNAL_STATES =
            ImmutableSet.<Integer>builder().add(VehicleTurnSignal.STATE_NONE,
                    VehicleTurnSignal.STATE_RIGHT, VehicleTurnSignal.STATE_LEFT).build();
    private static final ImmutableSet<Integer> VEHICLE_LIGHT_STATES =
            ImmutableSet.<Integer>builder().add(VehicleLightState.STATE_OFF,
                    VehicleLightState.STATE_ON, VehicleLightState.STATE_DAYTIME_RUNNING).build();
    private static final ImmutableSet<Integer> VEHICLE_LIGHT_SWITCHES =
            ImmutableSet.<Integer>builder().add(VehicleLightSwitch.STATE_OFF,
                    VehicleLightSwitch.STATE_ON, VehicleLightSwitch.STATE_DAYTIME_RUNNING,
                    VehicleLightSwitch.STATE_AUTOMATIC).build();
    private static final ImmutableSet<Integer> EV_STOPPING_MODES =
            ImmutableSet.<Integer>builder().add(EvStoppingMode.STATE_OTHER,
                    EvStoppingMode.STATE_CREEP, EvStoppingMode.STATE_ROLL,
                    EvStoppingMode.STATE_HOLD).build();
    private static final ImmutableSet<Integer> HVAC_TEMPERATURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.CELSIUS,
                    VehicleUnit.FAHRENHEIT).build();
    private static final ImmutableSet<Integer> SINGLE_HVAC_FAN_DIRECTIONS = ImmutableSet.of(
            /*VehicleHvacFanDirection.FACE=*/0x1, /*VehicleHvacFanDirection.FLOOR=*/0x2,
            /*VehicleHvacFanDirection.DEFROST=*/0x4);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_HVAC_FAN_DIRECTIONS =
            generateAllPossibleHvacFanDirections();
    private static final ImmutableSet<Integer> VEHICLE_SEAT_OCCUPANCY_STATES = ImmutableSet.of(
            /*VehicleSeatOccupancyState.UNKNOWN=*/0, /*VehicleSeatOccupancyState.VACANT=*/1,
            /*VehicleSeatOccupancyState.OCCUPIED=*/2);
    private static final ImmutableList<Integer>
            PERMISSION_READ_DRIVER_MONITORING_SETTINGS_PROPERTIES = ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED)
                    .build();
    private static final ImmutableList<Integer>
            PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED)
                    .build();
    private static final ImmutableList<Integer>
            PERMISSION_READ_DRIVER_MONITORING_STATES_PROPERTIES = ImmutableList.<Integer>builder()
                    .add()
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_ENERGY_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.FUEL_LEVEL,
                            VehiclePropertyIds.EV_BATTERY_LEVEL,
                            VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
                            VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                            VehiclePropertyIds.RANGE_REMAINING,
                            VehiclePropertyIds.FUEL_LEVEL_LOW,
                            VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT,
                            VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT,
                            VehiclePropertyIds.EV_CHARGE_STATE,
                            VehiclePropertyIds.EV_CHARGE_SWITCH,
                            VehiclePropertyIds.EV_CHARGE_TIME_REMAINING,
                            VehiclePropertyIds.EV_REGENERATIVE_BRAKING_STATE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_ENERGY_PORTS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.FUEL_DOOR_OPEN,
                            VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
                            VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_EXTERIOR_ENVIRONMENT_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(VehiclePropertyIds.NIGHT_MODE, VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_INFO_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.INFO_MAKE,
                            VehiclePropertyIds.INFO_MODEL,
                            VehiclePropertyIds.INFO_MODEL_YEAR,
                            VehiclePropertyIds.INFO_FUEL_CAPACITY,
                            VehiclePropertyIds.INFO_FUEL_TYPE,
                            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                            VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
                            VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION,
                            VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS,
                            VehiclePropertyIds.INFO_EV_PORT_LOCATION,
                            VehiclePropertyIds.INFO_DRIVER_SEAT,
                            VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS,
                            VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_TYPE,
                            VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_STATUS,
                            VehiclePropertyIds.GENERAL_SAFETY_REGULATION_COMPLIANCE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_POWERTRAIN_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.GEAR_SELECTION,
                            VehiclePropertyIds.CURRENT_GEAR,
                            VehiclePropertyIds.PARKING_BRAKE_ON,
                            VehiclePropertyIds.PARKING_BRAKE_AUTO_APPLY,
                            VehiclePropertyIds.IGNITION_STATE,
                            VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL,
                            VehiclePropertyIds.EV_STOPPING_MODE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_POWERTRAIN_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL,
                            VehiclePropertyIds.EV_STOPPING_MODE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_SPEED_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.PERF_VEHICLE_SPEED,
                            VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                            VehiclePropertyIds.WHEEL_TICK)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_CAR_DISPLAY_UNITS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.DISTANCE_DISPLAY_UNITS,
                            VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                            VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                            VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS,
                            VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
                            VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_STEERING_WHEEL_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.STEERING_WHEEL_DEPTH_POS,
                            VehiclePropertyIds.STEERING_WHEEL_DEPTH_MOVE,
                            VehiclePropertyIds.STEERING_WHEEL_HEIGHT_POS,
                            VehiclePropertyIds.STEERING_WHEEL_HEIGHT_MOVE,
                            VehiclePropertyIds.STEERING_WHEEL_THEFT_LOCK_ENABLED,
                            VehiclePropertyIds.STEERING_WHEEL_LOCKED,
                            VehiclePropertyIds.STEERING_WHEEL_EASY_ACCESS_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_AIRBAGS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.SEAT_AIRBAG_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_ADAS_SETTINGS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                            VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                            VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                            VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                            VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_ADAS_SETTINGS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                            VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                            VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                            VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                            VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_ADAS_STATES_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add()
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_ADAS_STATES_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add()
                    .build();

    /** contains property Ids for the properties required by CDD */
    private final ArraySet<Integer> mPropertyIds = new ArraySet<>();
    private CarPropertyManager mCarPropertyManager;

    private static ImmutableSet<Integer> generateAllPossibleHvacFanDirections() {
        ImmutableSet.Builder<Integer> allPossibleFanDirectionsBuilder = ImmutableSet.builder();
        for (int i = 1; i <= SINGLE_HVAC_FAN_DIRECTIONS.size(); i++) {
            allPossibleFanDirectionsBuilder.addAll(Sets.combinations(SINGLE_HVAC_FAN_DIRECTIONS,
                    i).stream().map(hvacFanDirectionCombo -> {
                Integer possibleHvacFanDirection = 0;
                for (Integer hvacFanDirection : hvacFanDirectionCombo) {
                    possibleHvacFanDirection |= hvacFanDirection;
                }
                return possibleHvacFanDirection;
            }).collect(Collectors.toList()));
        }
        return allPossibleFanDirectionsBuilder.build();
    }

    private static void verifyWheelTickConfigArray(int supportedWheels, int wheelToVerify,
            int configArrayIndex, int wheelTicksToUm) {
        if ((supportedWheels & wheelToVerify) != 0) {
            assertWithMessage(
                            "WHEEL_TICK configArray["
                                    + configArrayIndex
                                    + "] must specify the ticks to micrometers for "
                                    + wheelToString(wheelToVerify))
                    .that(wheelTicksToUm)
                    .isGreaterThan(0);
        } else {
            assertWithMessage(
                            "WHEEL_TICK configArray["
                                    + configArrayIndex
                                    + "] should be zero since "
                                    + wheelToString(wheelToVerify)
                                    + "is not supported")
                    .that(wheelTicksToUm)
                    .isEqualTo(0);
        }
    }

    private static void verifyWheelTickValue(
            int supportedWheels, int wheelToVerify, int valueIndex, Long ticks) {
        if ((supportedWheels & wheelToVerify) == 0) {
            assertWithMessage(
                            "WHEEL_TICK value["
                                    + valueIndex
                                    + "] should be zero since "
                                    + wheelToString(wheelToVerify)
                                    + "is not supported")
                    .that(ticks)
                    .isEqualTo(0);
        }
    }

    private static String wheelToString(int wheel) {
        switch (wheel) {
            case VehicleAreaWheel.WHEEL_LEFT_FRONT:
                return "WHEEL_LEFT_FRONT";
            case VehicleAreaWheel.WHEEL_RIGHT_FRONT:
                return "WHEEL_RIGHT_FRONT";
            case VehicleAreaWheel.WHEEL_RIGHT_REAR:
                return "WHEEL_RIGHT_REAR";
            case VehicleAreaWheel.WHEEL_LEFT_REAR:
                return "WHEEL_LEFT_REAR";
            default:
                return Integer.toString(wheel);
        }
    }

    // TODO(b/242350638): remove once all tests are annotated
    // Also, while fixing those, make sure the proper versions were set in the ApiRequirements
    // annotations added to @CddTests
    // Finally, make these changes on a child bug of 242350638
    @Override
    protected void configApiCheckerRule(Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() throws Exception {
        mCarPropertyManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        mPropertyIds.add(VehiclePropertyIds.PERF_VEHICLE_SPEED);
        mPropertyIds.add(VehiclePropertyIds.GEAR_SELECTION);
        mPropertyIds.add(VehiclePropertyIds.NIGHT_MODE);
        mPropertyIds.add(VehiclePropertyIds.PARKING_BRAKE_ON);
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList()}
     */
    @Test
    public void testGetPropertyList() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        assertThat(allConfigs).isNotNull();
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList(ArraySet)}
     */
    @Test
    public void testGetPropertyListWithArraySet() {
        runWithShellPermissionIdentity(
                () -> {
                    List<CarPropertyConfig> requiredConfigs =
                            mCarPropertyManager.getPropertyList(mPropertyIds);
                    // Vehicles need to implement all of those properties
                    assertThat(requiredConfigs.size()).isEqualTo(mPropertyIds.size());
                },
                Car.PERMISSION_EXTERIOR_ENVIRONMENT,
                Car.PERMISSION_POWERTRAIN,
                Car.PERMISSION_SPEED);
    }

    /**
     * Test for {@link CarPropertyManager#getCarPropertyConfig(int)}
     */
    @Test
    public void testGetPropertyConfig() {
        runWithShellPermissionIdentity(
                () -> {
                    List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
                    for (CarPropertyConfig cfg : allConfigs) {
                        assertThat(mCarPropertyManager.getCarPropertyConfig(cfg.getPropertyId()))
                                .isNotNull();
                    }
                });
    }

    /**
     * Test for {@link CarPropertyManager#getAreaId(int, int)}
     */
    @Test
    public void testGetAreaId() {
        runWithShellPermissionIdentity(
                () -> {
                    // For global properties, getAreaId should always return 0.
                    List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
                    for (CarPropertyConfig cfg : allConfigs) {
                        if (cfg.isGlobalProperty()) {
                            assertThat(
                                            mCarPropertyManager.getAreaId(
                                                    cfg.getPropertyId(),
                                                    VehicleAreaSeat.SEAT_ROW_1_LEFT))
                                    .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                        } else {
                            int[] areaIds = cfg.getAreaIds();
                            // Because areaId in propConfig must not be overlapped with each other.
                            // The result should be itself.
                            for (int areaIdInConfig : areaIds) {
                                int areaIdByCarPropertyManager =
                                        mCarPropertyManager.getAreaId(
                                                cfg.getPropertyId(), areaIdInConfig);
                                assertThat(areaIdByCarPropertyManager).isEqualTo(areaIdInConfig);
                            }
                        }
                    }
                });
    }

    @Test
    public void testInvalidMustNotBeImplemented() {
        runWithShellPermissionIdentity(
                () -> {
                    assertThat(mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.INVALID))
                            .isNull();
                });
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportGearSelection() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.GEAR_SELECTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireProperty()
                .setPossibleConfigArrayValues(VEHICLE_GEARS)
                .requirePropertyValueTobeInConfigArray()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportNightMode() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.NIGHT_MODE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .requireProperty()
                .addReadPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
                .build()
                .verify(mCarPropertyManager);
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportPerfVehicleSpeed() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_VEHICLE_SPEED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .requireProperty()
                .addReadPermission(Car.PERMISSION_SPEED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testPerfVehicleSpeedDisplayIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_SPEED)
                .build()
                .verify(mCarPropertyManager);
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportParkingBrakeOn() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PARKING_BRAKE_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .requireProperty()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEmergencyLaneKeepAssistEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testAdaptiveCruiseControlEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHandsOnDetectionEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testWheelTickIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WHEEL_TICK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Long[].class)
                .setConfigArrayVerifier(
                        configArray -> {
                            assertWithMessage("WHEEL_TICK config array must be size 5")
                                    .that(configArray.size())
                                    .isEqualTo(5);

                            int supportedWheels = configArray.get(0);
                            assertWithMessage(
                                            "WHEEL_TICK config array first element specifies which"
                                                + " wheels are supported")
                                    .that(supportedWheels)
                                    .isGreaterThan(VehicleAreaWheel.WHEEL_UNKNOWN);
                            assertWithMessage(
                                            "WHEEL_TICK config array first element specifies which"
                                                + " wheels are supported")
                                    .that(supportedWheels)
                                    .isAtMost(
                                            VehicleAreaWheel.WHEEL_LEFT_FRONT
                                                    | VehicleAreaWheel.WHEEL_RIGHT_FRONT
                                                    | VehicleAreaWheel.WHEEL_LEFT_REAR
                                                    | VehicleAreaWheel.WHEEL_RIGHT_REAR);

                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_FRONT,
                                    1,
                                    configArray.get(1));
                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                                    2,
                                    configArray.get(2));
                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_REAR,
                                    3,
                                    configArray.get(3));
                            verifyWheelTickConfigArray(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_REAR,
                                    4,
                                    configArray.get(4));
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            List<Integer> wheelTickConfigArray = carPropertyConfig.getConfigArray();
                            int supportedWheels = wheelTickConfigArray.get(0);

                            Long[] wheelTicks = (Long[]) carPropertyValue.getValue();
                            assertWithMessage("WHEEL_TICK Long[] value must be size 5")
                                    .that(wheelTicks.length)
                                    .isEqualTo(5);

                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_FRONT,
                                    1,
                                    wheelTicks[1]);
                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_FRONT,
                                    2,
                                    wheelTicks[2]);
                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_RIGHT_REAR,
                                    3,
                                    wheelTicks[3]);
                            verifyWheelTickValue(
                                    supportedWheels,
                                    VehicleAreaWheel.WHEEL_LEFT_REAR,
                                    4,
                                    wheelTicks[4]);
                        })
                .addReadPermission(Car.PERMISSION_SPEED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testInfoVinIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_VIN,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        String.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) ->
                                assertWithMessage("INFO_VIN must be 17 characters")
                                        .that((String) carPropertyValue.getValue())
                                        .hasLength(17))
                .addReadPermission(Car.PERMISSION_IDENTIFICATION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testInfoMakeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MAKE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        String.class)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoModelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MODEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        String.class)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoModelYearIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MODEL_YEAR,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoFuelCapacityIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_FUEL_CAPACITY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) ->
                                assertWithMessage(
                                                "INFO_FUEL_CAPACITY Float value must be greater"
                                                    + " than or equal 0")
                                        .that((Float) carPropertyValue.getValue())
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoFuelTypeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_FUEL_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            Integer[] fuelTypes = (Integer[]) carPropertyValue.getValue();
                            assertWithMessage("INFO_FUEL_TYPE must specify at least 1 fuel type")
                                    .that(fuelTypes.length)
                                    .isGreaterThan(0);
                            for (Integer fuelType : fuelTypes) {
                                assertWithMessage(
                                                "INFO_FUEL_TYPE must be a defined fuel type: "
                                                        + fuelType)
                                        .that(fuelType)
                                        .isIn(
                                                ImmutableSet.builder()
                                                        .add(
                                                                FuelType.UNKNOWN,
                                                                FuelType.UNLEADED,
                                                                FuelType.LEADED,
                                                                FuelType.DIESEL_1,
                                                                FuelType.DIESEL_2,
                                                                FuelType.BIODIESEL,
                                                                FuelType.E85,
                                                                FuelType.LPG,
                                                                FuelType.CNG,
                                                                FuelType.LNG,
                                                                FuelType.ELECTRIC,
                                                                FuelType.HYDROGEN,
                                                                FuelType.OTHER)
                                                        .build());
                            }
                        })
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoEvBatteryCapacityIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) ->
                                assertWithMessage(
                                                "INFO_EV_BATTERY_CAPACITY Float value must be"
                                                    + " greater than or equal to 0")
                                        .that((Float) carPropertyValue.getValue())
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoEvConnectorTypeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            Integer[] evConnectorTypes = (Integer[]) carPropertyValue.getValue();
                            assertWithMessage(
                                            "INFO_EV_CONNECTOR_TYPE must specify at least 1"
                                                + " connection type")
                                    .that(evConnectorTypes.length)
                                    .isGreaterThan(0);
                            for (Integer evConnectorType : evConnectorTypes) {
                                assertWithMessage(
                                                "INFO_EV_CONNECTOR_TYPE must be a defined"
                                                    + " connection type: "
                                                        + evConnectorType)
                                        .that(evConnectorType)
                                        .isIn(
                                                ImmutableSet.builder()
                                                        .add(
                                                                EvConnectorType.UNKNOWN,
                                                                EvConnectorType.J1772,
                                                                EvConnectorType.MENNEKES,
                                                                EvConnectorType.CHADEMO,
                                                                EvConnectorType.COMBO_1,
                                                                EvConnectorType.COMBO_2,
                                                                EvConnectorType.TESLA_ROADSTER,
                                                                EvConnectorType.TESLA_HPWC,
                                                                EvConnectorType.TESLA_SUPERCHARGER,
                                                                EvConnectorType.GBT,
                                                                EvConnectorType.GBT_DC,
                                                                EvConnectorType.SCAME,
                                                                EvConnectorType.OTHER)
                                                        .build());
                            }
                        })
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoFuelDoorLocationIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .setPossibleCarPropertyValues(PORT_LOCATION_TYPES)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoEvPortLocationIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EV_PORT_LOCATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .setPossibleCarPropertyValues(PORT_LOCATION_TYPES)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoMultiEvPortLocationsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            Integer[] evPortLocations = (Integer[]) carPropertyValue.getValue();
                            assertWithMessage(
                                            "INFO_MULTI_EV_PORT_LOCATIONS must specify at least 1"
                                                + " port location")
                                    .that(evPortLocations.length)
                                    .isGreaterThan(0);
                            for (Integer evPortLocation : evPortLocations) {
                                assertWithMessage(
                                                "INFO_MULTI_EV_PORT_LOCATIONS must be a defined"
                                                    + " port location: "
                                                        + evPortLocation)
                                        .that(evPortLocation)
                                        .isIn(PORT_LOCATION_TYPES);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoDriverSeatIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_DRIVER_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                VehicleAreaSeat.SEAT_UNKNOWN,
                                VehicleAreaSeat.SEAT_ROW_1_LEFT,
                                VehicleAreaSeat.SEAT_ROW_1_CENTER,
                                VehicleAreaSeat.SEAT_ROW_1_RIGHT))
                .setAreaIdsVerifier(
                        areaIds ->
                                assertWithMessage(
                                                "Even though INFO_DRIVER_SEAT is"
                                                    + " VEHICLE_AREA_TYPE_SEAT, it is meant to be"
                                                    + " VEHICLE_AREA_TYPE_GLOBAL, so its AreaIds"
                                                    + " must contain a single 0")
                                        .that(areaIds)
                                        .isEqualTo(
                                                new int[] {
                                                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL
                                                }))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testInfoExteriorDimensionsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            Integer[] exteriorDimensions = (Integer[]) carPropertyValue.getValue();
                            assertWithMessage(
                                            "INFO_EXTERIOR_DIMENSIONS must specify all 8 dimension"
                                                + " measurements")
                                    .that(exteriorDimensions.length)
                                    .isEqualTo(8);
                            for (Integer exteriorDimension : exteriorDimensions) {
                                assertWithMessage(
                                                "INFO_EXTERIOR_DIMENSIONS measurement must be"
                                                    + " greater than 0")
                                        .that(exteriorDimension)
                                        .isGreaterThan(0);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testElectronicTollCollectionCardTypeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                VehicleElectronicTollCollectionCardType.UNKNOWN,
                                VehicleElectronicTollCollectionCardType
                                        .JP_ELECTRONIC_TOLL_COLLECTION_CARD,
                                VehicleElectronicTollCollectionCardType
                                        .JP_ELECTRONIC_TOLL_COLLECTION_CARD_V2))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testElectronicTollCollectionCardStatusIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_STATUS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                VehicleElectronicTollCollectionCardStatus.UNKNOWN,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_VALID,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_INVALID,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_NOT_INSERTED))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testGeneralSafetyRegulationComplianceIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.GENERAL_SAFETY_REGULATION_COMPLIANCE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                GsrComplianceType.GSR_COMPLIANCE_TYPE_NOT_REQUIRED,
                                GsrComplianceType.GSR_COMPLIANCE_TYPE_REQUIRED_V1))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEnvOutsideTemperatureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testCurrentGearIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CURRENT_GEAR,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(VEHICLE_GEARS)
                .requirePropertyValueTobeInConfigArray()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testParkingBrakeAutoApplyIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PARKING_BRAKE_AUTO_APPLY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testIgnitionStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.IGNITION_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                VehicleIgnitionState.UNDEFINED,
                                VehicleIgnitionState.LOCK,
                                VehicleIgnitionState.OFF,
                                VehicleIgnitionState.ACC,
                                VehicleIgnitionState.ON,
                                VehicleIgnitionState.START))
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvBrakeRegenerationLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .addWritePermission(Car.PERMISSION_CONTROL_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvStoppingModeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_STOPPING_MODE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(EV_STOPPING_MODES)
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .addWritePermission(Car.PERMISSION_CONTROL_POWERTRAIN)
                .build()
                .verify(mCarPropertyManager);
    }
    @Test
    public void testAbsActiveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ABS_ACTIVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CAR_DYNAMICS_STATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testTractionControlActiveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TRACTION_CONTROL_ACTIVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CAR_DYNAMICS_STATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testDoorPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testDoorMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testDoorLockIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testDoorChildLockEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_CHILD_LOCK_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testMirrorZPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Z_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testMirrorZMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Z_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testMirrorYPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Y_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testMirrorYMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Y_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testMirrorLockIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testMirrorFoldIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_FOLD,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testMirrorAutoFoldEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_AUTO_FOLD_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testMirrorAutoTiltEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_AUTO_TILT_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testWindowPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDOW_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testWindowMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDOW_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testWindowLockIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDOW_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelDepthPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_DEPTH_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelDepthMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_DEPTH_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelHeightPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_HEIGHT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelTheftLockEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_THEFT_LOCK_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelLockedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LOCKED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelEasyAccessEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_EASY_ACCESS_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testDistanceDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DISTANCE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(DISTANCE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_VENDOR_EXTENSION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFuelVolumeDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(VOLUME_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_VENDOR_EXTENSION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testTirePressureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TIRE_PRESSURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .requireMinMaxValues()
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) ->
                                assertWithMessage(
                                                "TIRE_PRESSURE Float value"
                                                        + " at Area ID equals to "
                                                        + carPropertyValue.getAreaId()
                                                        + " must be greater than or equal 0")
                                        .that((Float) carPropertyValue.getValue())
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_TIRES)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testCriticallyLowTirePressureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            int areaId = carPropertyValue.getAreaId();

                            assertWithMessage(
                                            "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                    + "at Area ID equals to"
                                                    + areaId
                                                    + " must be greater than or equal 0")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtLeast(0);

                            CarPropertyConfig<?> tirePressureConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.TIRE_PRESSURE);

                            if (tirePressureConfig == null
                                    || tirePressureConfig.getMinValue(areaId) == null) {
                                return;
                            }

                            assertWithMessage(
                                            "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                    + "at Area ID equals to"
                                                    + areaId
                                                    + " must not exceed"
                                                    + " minFloatValue in TIRE_PRESSURE")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtMost((Float) tirePressureConfig.getMinValue(areaId));
                        })
                .addReadPermission(Car.PERMISSION_TIRES)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testTirePressureDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(PRESSURE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_VENDOR_EXTENSION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvBatteryDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(BATTERY_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_VENDOR_EXTENSION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testVehicleSpeedDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(SPEED_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_VENDOR_EXTENSION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFuelConsumptionUnitsDistanceOverTimeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(Car.PERMISSION_CONTROL_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_VENDOR_EXTENSION)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFuelLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FUEL_LEVEL Float value must be greater than or equal"
                                                + " 0")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtLeast(0);

                            if (mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.INFO_FUEL_CAPACITY)
                                    == null) {
                                return;
                            }

                            CarPropertyValue<?> infoFuelCapacityValue =
                                    mCarPropertyManager.getProperty(
                                            VehiclePropertyIds.INFO_FUEL_CAPACITY,
                                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

                            assertWithMessage(
                                            "FUEL_LEVEL Float value must not exceed"
                                                + " INFO_FUEL_CAPACITY Float value")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtMost((Float) infoFuelCapacityValue.getValue());
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvBatteryLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BATTERY_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "EV_BATTERY_LEVEL Float value must be greater than or"
                                                + " equal 0")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtLeast(0);

                            if (mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
                                    == null) {
                                return;
                            }

                            CarPropertyValue<?> infoEvBatteryCapacityValue =
                                    mCarPropertyManager.getProperty(
                                            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);

                            assertWithMessage(
                                            "EV_BATTERY_LEVEL Float value must not exceed "
                                                    + "INFO_EV_BATTERY_CAPACITY Float "
                                                    + "value")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtMost((Float) infoEvBatteryCapacityValue.getValue());
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvCurrentBatteryCapacityIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "EV_CURRENT_BATTERY_CAPACITY Float value must be"
                                                    + "greater than or equal 0")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtLeast(0);

                            if (mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY)
                                    == null) {
                                return;
                            }

                            CarPropertyValue<?> infoEvBatteryCapacityValue =
                                    mCarPropertyManager.getProperty(
                                            VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                                            /*areaId=*/0);

                            assertWithMessage(
                                            "EV_CURRENT_BATTERY_CAPACITY Float value must not"
                                                    + "exceed INFO_EV_BATTERY_CAPACITY Float "
                                                    + "value")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtMost((Float) infoEvBatteryCapacityValue.getValue());
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvBatteryInstantaneousChargeRateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testRangeRemainingIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.RANGE_REMAINING,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "RANGE_REMAINING Float value must be greater than or"
                                                + " equal 0")
                                    .that((Float) carPropertyValue.getValue())
                                    .isAtLeast(0);
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .addWritePermission(Car.PERMISSION_ADJUST_RANGE_REMAINING)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFuelLevelLowIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_LEVEL_LOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFuelDoorOpenIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_DOOR_OPEN,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_ENERGY_PORTS)
                .addWritePermission(Car.PERMISSION_CONTROL_ENERGY_PORTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargePortOpenIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_ENERGY_PORTS)
                .addWritePermission(Car.PERMISSION_CONTROL_ENERGY_PORTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargePortConnectedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_ENERGY_PORTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeCurrentDrawLimitIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class)
                .setConfigArrayVerifier(
                        configArray -> {
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT config array must be size"
                                                + " 1")
                                    .that(configArray.size())
                                    .isEqualTo(1);

                            int maxCurrentDrawThresholdAmps = configArray.get(0);
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT config array first"
                                                + " element specifies max current draw allowed by"
                                                + " vehicle in amperes.")
                                    .that(maxCurrentDrawThresholdAmps)
                                    .isGreaterThan(0);
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            List<Integer> evChargeCurrentDrawLimitConfigArray =
                                    carPropertyConfig.getConfigArray();
                            int maxCurrentDrawThresholdAmps =
                                    evChargeCurrentDrawLimitConfigArray.get(0);

                            Float evChargeCurrentDrawLimit = (Float) carPropertyValue.getValue();
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT value must be greater"
                                                + " than 0")
                                    .that(evChargeCurrentDrawLimit)
                                    .isGreaterThan(0);
                            assertWithMessage(
                                            "EV_CHARGE_CURRENT_DRAW_LIMIT value must be less than"
                                                + " or equal to max current draw by the vehicle")
                                    .that(evChargeCurrentDrawLimit)
                                    .isAtMost(maxCurrentDrawThresholdAmps);
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargePercentLimitIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class)
                .setConfigArrayVerifier(
                        configArray -> {
                            for (int i = 0; i < configArray.size(); i++) {
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT configArray["
                                                        + i
                                                        + "] valid charge percent limit must be"
                                                        + " greater than 0")
                                        .that(configArray.get(i))
                                        .isGreaterThan(0);
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT configArray["
                                                        + i
                                                        + "] valid charge percent limit must be at"
                                                        + " most 100")
                                        .that(configArray.get(i))
                                        .isAtMost(100);
                            }
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            List<Integer> evChargePercentLimitConfigArray =
                                    carPropertyConfig.getConfigArray();
                            Float evChargePercentLimit = (Float) carPropertyValue.getValue();

                            if (evChargePercentLimitConfigArray.isEmpty()) {
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT value must be greater than"
                                                    + " 0")
                                        .that(evChargePercentLimit)
                                        .isGreaterThan(0);
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT value must be at most 100")
                                        .that(evChargePercentLimit)
                                        .isAtMost(100);
                            } else {
                                assertWithMessage(
                                                "EV_CHARGE_PERCENT_LIMIT value must be in the"
                                                    + " configArray valid charge percent limit"
                                                    + " list")
                                        .that(evChargePercentLimit.intValue())
                                        .isIn(evChargePercentLimitConfigArray);
                            }
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(ImmutableSet.of(
                                                    EvChargeState.STATE_UNKNOWN,
                                                    EvChargeState.STATE_CHARGING,
                                                    EvChargeState.STATE_FULLY_CHARGED,
                                                    EvChargeState.STATE_NOT_CHARGING,
                                                    EvChargeState.STATE_ERROR))
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_ENERGY)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvChargeTimeRemainingIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_TIME_REMAINING,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Integer.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FUEL_LEVEL Integer value must be greater than or equal"
                                                + " 0")
                                    .that((Integer) carPropertyValue.getValue())
                                    .isAtLeast(0);
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEvRegenerativeBrakingStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_REGENERATIVE_BRAKING_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(
                                            ImmutableSet.of(
                                                    EvRegenerativeBrakingState.STATE_UNKNOWN,
                                                    EvRegenerativeBrakingState.STATE_DISABLED,
                                                    EvRegenerativeBrakingState
                                                            .STATE_PARTIALLY_ENABLED,
                                                    EvRegenerativeBrakingState
                                                            .STATE_FULLY_ENABLED))
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testPerfSteeringAngleIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_STEERING_ANGLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_READ_STEERING_STATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testPerfRearSteeringAngleIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_REAR_STEERING_ANGLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_READ_STEERING_STATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEngineCoolantTempIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEngineOilLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_OIL_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                /*VehicleOilLevel.CRITICALLY_LOW=*/ 0,
                                /*VehicleOilLevel.LOW=*/ 1,
                                /*VehicleOilLevel.NORMAL=*/ 2,
                                /*VehicleOilLevel.HIGH=*/ 3,
                                /*VehicleOilLevel.ERROR=*/ 4))
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEngineOilTempIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_OIL_TEMP,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEngineRpmIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_RPM,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) ->
                                assertWithMessage(
                                                "ENGINE_RPM Float value must be greater than or"
                                                    + " equal 0")
                                        .that((Float) carPropertyValue.getValue())
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testEngineIdleAutoStopEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_IDLE_AUTO_STOP_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .addWritePermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testPerfOdometerIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_ODOMETER,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) ->
                                assertWithMessage(
                                                "PERF_ODOMETER Float value must be greater than or"
                                                    + " equal 0")
                                        .that((Float) carPropertyValue.getValue())
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_MILEAGE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testTurnSignalStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TURN_SIGNAL_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(TURN_SIGNAL_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHeadlightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HEADLIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHighBeamLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFogLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FOG_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FRONT_FOG_LIGHTS_STATE must not be implemented"
                                                    + "when FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE))
                                    .isNull();

                            assertWithMessage(
                                            "REAR_FOG_LIGHTS_STATE must not be implemented"
                                                    + "when FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.REAR_FOG_LIGHTS_STATE))
                                    .isNull();
                        })
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHazardLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HAZARD_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFrontFogLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_STATE must not be implemented"
                                                    + "when FRONT_FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FOG_LIGHTS_STATE))
                                    .isNull();
                        })
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testRearFogLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.REAR_FOG_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_STATE must not be implemented"
                                                    + "when REAR_FOG_LIGHTS_STATE is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FOG_LIGHTS_STATE))
                                    .isNull();
                        })
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testCabinLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CABIN_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testReadingLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.READING_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testVehicleCurbWeightIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.VEHICLE_CURB_WEIGHT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class)
                .setConfigArrayVerifier(
                        configArray -> {
                            assertWithMessage(
                                            "VEHICLE_CURB_WEIGHT configArray must contain the gross"
                                                + " weight in kilograms")
                                    .that(configArray)
                                    .hasSize(1);
                            assertWithMessage(
                                            "VEHICLE_CURB_WEIGHT configArray[0] must contain the"
                                                + " gross weight in kilograms and be greater than"
                                                + " zero")
                                    .that(configArray.get(0))
                                    .isGreaterThan(0);
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            Integer curbWeightKg = (Integer) carPropertyValue.getValue();
                            Integer grossWeightKg = carPropertyConfig.getConfigArray().get(0);

                            assertWithMessage("VEHICLE_CURB_WEIGHT must be greater than zero")
                                    .that(curbWeightKg)
                                    .isGreaterThan(0);
                            assertWithMessage(
                                            "VEHICLE_CURB_WEIGHT must be less than the gross"
                                                + " weight")
                                    .that(curbWeightKg)
                                    .isLessThan(grossWeightKg);
                        })
                .addReadPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHeadlightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HEADLIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testTrailerPresentIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TRAILER_PRESENT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(
                        ImmutableSet.of(
                                /*TrailerState.UNKNOWN=*/
                                0, /*TrailerState.NOT_PRESENT*/
                                1, /*TrailerState.PRESENT=*/
                                2, /*TrailerState.ERROR=*/
                                3))
                .addReadPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHighBeamLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFogLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FOG_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FRONT_FOG_LIGHTS_SWITCH must not be implemented"
                                                    + "when FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH))
                                    .isNull();

                            assertWithMessage(
                                            "REAR_FOG_LIGHTS_SWITCH must not be implemented"
                                                    + "when FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH))
                                    .isNull();
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHazardLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HAZARD_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testFrontFogLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_SWITCH must not be implemented"
                                                    + "when FRONT_FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FOG_LIGHTS_SWITCH))
                                    .isNull();
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testRearFogLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            assertWithMessage(
                                            "FOG_LIGHTS_SWITCH must not be implemented"
                                                    + "when REAR_FOG_LIGHTS_SWITCH is implemented")
                                    .that(
                                            mCarPropertyManager.getCarPropertyConfig(
                                                    VehiclePropertyIds.FOG_LIGHTS_SWITCH))
                                    .isNull();
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testCabinLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CABIN_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testReadingLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.READING_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSteeringWheelLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig"})
    public void testSeatMemorySelectIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_MEMORY_SELECT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            int[] areaIds = carPropertyConfig.getAreaIds();
                            CarPropertyConfig<?> seatMemorySetCarPropertyConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.SEAT_MEMORY_SET);

                            assertWithMessage(
                                            "SEAT_MEMORY_SET must be implemented if "
                                                    + "SEAT_MEMORY_SELECT is implemented")
                                    .that(seatMemorySetCarPropertyConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "SEAT_MEMORY_SELECT area IDs must match the area IDs of"
                                                + " SEAT_MEMORY_SET")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            seatMemorySetCarPropertyConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));

                            for (int areaId : areaIds) {
                                Integer seatMemorySetAreaIdMaxValue =
                                        (Integer)
                                                seatMemorySetCarPropertyConfig.getMaxValue(areaId);
                                assertWithMessage(
                                                "SEAT_MEMORY_SET - area ID: "
                                                        + areaId
                                                        + " must have max value defined")
                                        .that(seatMemorySetAreaIdMaxValue)
                                        .isNotNull();
                                assertWithMessage(
                                                "SEAT_MEMORY_SELECT - area ID: "
                                                        + areaId
                                                        + "'s max value must be equal to"
                                                        + " SEAT_MEMORY_SET's max value under the"
                                                        + " same area ID")
                                        .that(seatMemorySetAreaIdMaxValue)
                                        .isEqualTo(carPropertyConfig.getMaxValue(areaId));
                            }
                        })
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig"})
    public void testSeatMemorySetIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_MEMORY_SET,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            int[] areaIds = carPropertyConfig.getAreaIds();
                            CarPropertyConfig<?> seatMemorySelectCarPropertyConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.SEAT_MEMORY_SELECT);

                            assertWithMessage(
                                            "SEAT_MEMORY_SELECT must be implemented if "
                                                    + "SEAT_MEMORY_SET is implemented")
                                    .that(seatMemorySelectCarPropertyConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "SEAT_MEMORY_SET area IDs must match the area IDs of "
                                                    + "SEAT_MEMORY_SELECT")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            seatMemorySelectCarPropertyConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));

                            for (int areaId : areaIds) {
                                Integer seatMemorySelectAreaIdMaxValue =
                                        (Integer)
                                                seatMemorySelectCarPropertyConfig.getMaxValue(
                                                        areaId);
                                assertWithMessage(
                                                "SEAT_MEMORY_SELECT - area ID: "
                                                        + areaId
                                                        + " must have max value defined")
                                        .that(seatMemorySelectAreaIdMaxValue)
                                        .isNotNull();
                                assertWithMessage(
                                                "SEAT_MEMORY_SET - area ID: "
                                                        + areaId
                                                        + "'s max value must be equal to"
                                                        + " SEAT_MEMORY_SELECT's max value under"
                                                        + " the same area ID")
                                        .that(seatMemorySelectAreaIdMaxValue)
                                        .isEqualTo(carPropertyConfig.getMaxValue(areaId));
                            }
                        })
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatBeltBuckledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BELT_BUCKLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatBeltHeightPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BELT_HEIGHT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatBeltHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatForeAftPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FORE_AFT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatForeAftMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FORE_AFT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatBackrestAngle1PosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatBackrestAngle1MoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatBackrestAngle2PosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatBackrestAngle2MoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeightPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEIGHT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatDepthPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_DEPTH_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatDepthMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_DEPTH_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatTiltPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_TILT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatTiltMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_TILT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatLumbarForeAftPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatLumbarForeAftMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatLumbarSideSupportPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatLumbarSideSupportMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatHeadrestHeightPosMustNotBeImplemented() {
        runWithShellPermissionIdentity(
                () -> {
                    assertWithMessage(
                                "SEAT_HEADREST_HEIGHT_POS has been deprecated and should not be"
                                + " implemented. Use SEAT_HEADREST_HEIGHT_POS_V2 instead.")
                        .that(
                                mCarPropertyManager.getCarPropertyConfig(
                                        VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS))
                        .isNull();
                },
                Car.PERMISSION_CONTROL_CAR_SEATS);
    }

    @Test
    public void testSeatHeadrestHeightPosV2IfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS_V2,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeadrestHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeadrestAnglePosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeadrestAngleMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeadrestForeAftPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatHeadrestForeAftMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatFootwellLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatFootwellLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatEasyAccessEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_EASY_ACCESS_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatAirbagEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_AIRBAG_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_AIRBAGS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_AIRBAGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatCushionSideSupportPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatCushionSideSupportMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatLumbarVerticalPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatLumberVerticalMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testSeatWalkInPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_WALK_IN_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testSeatOccupancyIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_OCCUPANCY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleCarPropertyValues(VEHICLE_SEAT_OCCUPANCY_STATES)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacDefrosterIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_DEFROSTER,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHvacElectricDefrosterOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHvacSideMirrorHeatIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHvacSteeringWheelHeatIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHvacTemperatureDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossibleConfigArrayValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testHvacTemperatureValueSuggestionIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float[].class)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacPowerOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_POWER_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setConfigArrayVerifier(
                        configArray -> {
                            CarPropertyConfig<?> hvacPowerOnCarPropertyConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_POWER_ON);
                            for (int powerDependentProperty : configArray) {
                                CarPropertyConfig<?> powerDependentCarPropertyConfig =
                                        mCarPropertyManager.getCarPropertyConfig(
                                                powerDependentProperty);
                                if (powerDependentCarPropertyConfig == null) {
                                    continue;
                                }
                                assertWithMessage(
                                                "HVAC_POWER_ON configArray must only contain"
                                                    + " VehicleAreaSeat type properties: "
                                                        + VehiclePropertyIds.toString(
                                                                powerDependentProperty))
                                        .that(powerDependentCarPropertyConfig.getAreaType())
                                        .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_SEAT);

                                for (int powerDependentAreaId :
                                        powerDependentCarPropertyConfig.getAreaIds()) {
                                    boolean powerDependentAreaIdIsContained = false;
                                    for (int hvacPowerOnAreaId :
                                            hvacPowerOnCarPropertyConfig.getAreaIds()) {
                                        if ((powerDependentAreaId & hvacPowerOnAreaId)
                                                == powerDependentAreaId) {
                                            powerDependentAreaIdIsContained = true;
                                            break;
                                        }
                                    }
                                    assertWithMessage(
                                            "HVAC_POWER_ON's area IDs must contain the area IDs"
                                                    + " of power dependent property: "
                                                    + VehiclePropertyIds.toString(
                                                    powerDependentProperty)).that(
                                            powerDependentAreaIdIsContained).isTrue();
                                }
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacFanSpeedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_SPEED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .requireMinMaxValues()
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacFanDirectionAvailableIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        areaIds -> {
                            CarPropertyConfig<?> hvacFanDirectionCarPropertyConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION must be implemented if "
                                                    + "HVAC_FAN_DIRECTION_AVAILABLE is implemented")
                                    .that(hvacFanDirectionCarPropertyConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area IDs must match the"
                                                + " area IDs of HVAC_FAN_DIRECTION")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            hvacFanDirectionCarPropertyConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            Integer[] fanDirectionValues = (Integer[]) carPropertyValue.getValue();
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + carPropertyValue.getAreaId()
                                                    + " must have at least 1 direction defined")
                                    .that(fanDirectionValues.length)
                                    .isAtLeast(1);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + carPropertyValue.getAreaId()
                                                    + " values all must all be unique: "
                                                    + Arrays.toString(fanDirectionValues))
                                    .that(fanDirectionValues.length)
                                    .isEqualTo(ImmutableSet.copyOf(fanDirectionValues).size());
                            for (Integer fanDirection : fanDirectionValues) {
                                assertWithMessage(
                                                "HVAC_FAN_DIRECTION_AVAILABLE's area ID: "
                                                        + carPropertyValue.getAreaId()
                                                        + " must be a valid combination of fan"
                                                        + " directions")
                                        .that(fanDirection)
                                        .isIn(ALL_POSSIBLE_HVAC_FAN_DIRECTIONS);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacFanDirectionIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        areaIds -> {
                            CarPropertyConfig<?> hvacFanDirectionAvailableConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE must be implemented if "
                                                    + "HVAC_FAN_DIRECTION is implemented")
                                    .that(hvacFanDirectionAvailableConfig)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION area IDs must match the area IDs of"
                                                + " HVAC_FAN_DIRECTION_AVAILABLE")
                                    .that(
                                            Arrays.stream(areaIds)
                                                    .boxed()
                                                    .collect(Collectors.toList()))
                                    .containsExactlyElementsIn(
                                            Arrays.stream(
                                                            hvacFanDirectionAvailableConfig
                                                                    .getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));
                        })
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, carPropertyValue) -> {
                            CarPropertyValue<Integer[]> hvacFanDirectionAvailableCarPropertyValue =
                                    mCarPropertyManager.getProperty(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                                            carPropertyValue.getAreaId());
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE value must be available")
                                    .that(hvacFanDirectionAvailableCarPropertyValue)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION area ID "
                                                    + carPropertyValue.getAreaId()
                                                    + " value must be in list for"
                                                    + " HVAC_FAN_DIRECTION_AVAILABLE")
                                    .that(carPropertyValue.getValue())
                                    .isIn(
                                            Arrays.asList(
                                                    hvacFanDirectionAvailableCarPropertyValue
                                                            .getValue()));
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacTemperatureCurrentIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacTemperatureSetIfSupported() {
        VehiclePropertyVerifier.Builder<Float> hvacTempSetVerifierBuilder =
                VehiclePropertyVerifier.newBuilder(
                                VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                                CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                                VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                                CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                                Float.class)
                        .setPossiblyDependentOnHvacPowerOn()
                        .setCarPropertyConfigVerifier(
                                carPropertyConfig -> {
                                    List<Integer> configArray = carPropertyConfig.getConfigArray();
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET config "
                                                            + "array must be size 6")
                                            .that(configArray.size())
                                            .isEqualTo(6);

                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET lower bound must be less"
                                                        + " than the upper bound for the supported"
                                                        + " temperatures in Celsius")
                                            .that(configArray.get(0))
                                            .isLessThan(configArray.get(1));
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET increment in Celsius"
                                                            + " must be greater than 0")
                                            .that(configArray.get(2))
                                            .isGreaterThan(0);
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET increment in Celsius must"
                                                        + " be less than the difference between the"
                                                        + " upper and lower bound supported"
                                                        + " temperatures")
                                            .that(configArray.get(2))
                                            .isLessThan(configArray.get(1) - configArray.get(0));
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET increment in Celsius must"
                                                        + " evenly space the gap between upper and"
                                                        + " lower bound")
                                            .that(
                                                    (configArray.get(1) - configArray.get(0))
                                                            % configArray.get(2))
                                            .isEqualTo(0);

                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET lower bound must be less"
                                                        + " than the upper bound for the supported"
                                                        + " temperatures in Fahrenheit")
                                            .that(configArray.get(3))
                                            .isLessThan(configArray.get(4));
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET increment in Fahrenheit"
                                                            + " must be greater than 0")
                                            .that(configArray.get(5))
                                            .isGreaterThan(0);
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET increment in Fahrenheit"
                                                        + " must be less than the difference"
                                                        + " between the upper and lower bound"
                                                        + " supported temperatures")
                                            .that(configArray.get(5))
                                            .isLessThan(configArray.get(4) - configArray.get(3));
                                    assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET increment in Fahrenheit"
                                                        + " must evenly space the gap between upper"
                                                        + " and lower bound")
                                            .that(
                                                    (configArray.get(4) - configArray.get(3))
                                                            % configArray.get(5))
                                            .isEqualTo(0);
                                    assertWithMessage(
                                            "HVAC_TEMPERATURE_SET number of supported values for "
                                                    + "Celsius and Fahrenheit must be equal.").that(
                                            (configArray.get(1) - configArray.get(0))
                                                    / configArray.get(2)).isEqualTo(
                                            (configArray.get(4) - configArray.get(3))
                                                    / configArray.get(5));

                                    int[] supportedAreaIds = carPropertyConfig.getAreaIds();
                                    int configMinValue = configArray.get(0);
                                    int configMaxValue = configArray.get(1);
                                    for (int i = 0; i < supportedAreaIds.length; i++) {
                                        int areaId = supportedAreaIds[i];
                                        Float minValueFloat =
                                                (Float) carPropertyConfig.getMinValue(areaId);
                                        if (minValueFloat != null) {
                                            Integer minValueInt = (int) (minValueFloat * 10);
                                            assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET minimum value: "
                                                            + minValueInt
                                                            + " at areaId: "
                                                            + areaId
                                                            + " should be equal to minimum"
                                                            + " value specified in config"
                                                            + " array: "
                                                            + configMinValue)
                                                    .that(minValueInt)
                                                    .isEqualTo(configMinValue);
                                        }
                                        Float maxValueFloat =
                                                (Float) carPropertyConfig.getMaxValue(areaId);
                                        if (maxValueFloat != null) {
                                            Integer maxValueInt = (int) (maxValueFloat * 10);
                                            assertWithMessage(
                                                    "HVAC_TEMPERATURE_SET maximum value: "
                                                            + maxValueInt
                                                            + " at areaId: "
                                                            + areaId
                                                            + " should be equal to maximum"
                                                            + " value specified in config"
                                                            + " array: "
                                                            + configMaxValue)
                                                    .that(maxValueInt)
                                                    .isEqualTo(configMaxValue);
                                        }
                                    }
                                });

        runWithShellPermissionIdentity(
                () -> {
                    CarPropertyConfig<?> hvacTempSetConfig =
                            mCarPropertyManager.getCarPropertyConfig(
                                    VehiclePropertyIds.HVAC_TEMPERATURE_SET);
                    if (hvacTempSetConfig != null) {
                        List<Integer> hvacTempSetConfigArray = hvacTempSetConfig.getConfigArray();
                        ImmutableSet.Builder<Float> possibleHvacTempSetValuesBuilder =
                                ImmutableSet.builder();
                        for (int possibleHvacTempSetValue = hvacTempSetConfigArray.get(0);
                                possibleHvacTempSetValue <= hvacTempSetConfigArray.get(1);
                                possibleHvacTempSetValue += hvacTempSetConfigArray.get(2)) {
                            possibleHvacTempSetValuesBuilder.add(
                                    (float) possibleHvacTempSetValue / 10.0f);
                        }
                        hvacTempSetVerifierBuilder.setPossibleCarPropertyValues(
                                possibleHvacTempSetValuesBuilder.build());
                    }
                },
                Car.PERMISSION_CONTROL_CAR_CLIMATE);

        hvacTempSetVerifierBuilder
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacAcOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacMaxAcOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_MAX_AC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacMaxDefrostOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacRecircOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_RECIRC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacAutoOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AUTO_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacSeatTemperatureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacActualFanSpeedRpmIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacAutoRecircOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacSeatVentilationIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SEAT_VENTILATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)"
            })
    public void testHvacDualOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_DUAL_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .setPossiblyDependentOnHvacPowerOn()
                .setAreaIdsVerifier(
                        areaIds -> {
                            CarPropertyConfig<?> hvacTempSetCarPropertyConfig =
                                    mCarPropertyManager.getCarPropertyConfig(
                                            VehiclePropertyIds.HVAC_TEMPERATURE_SET);
                            if (hvacTempSetCarPropertyConfig == null) {
                                return;
                            }
                            ImmutableSet<Integer> hvacTempSetAreaIds =
                                    ImmutableSet.copyOf(
                                            Arrays.stream(hvacTempSetCarPropertyConfig.getAreaIds())
                                                    .boxed()
                                                    .collect(Collectors.toList()));
                            ImmutableSet.Builder<Integer> allPossibleHvacDualOnAreaIdsBuilder =
                                    ImmutableSet.builder();
                            for (int i = 2; i <= hvacTempSetAreaIds.size(); i++) {
                                allPossibleHvacDualOnAreaIdsBuilder.addAll(
                                        Sets.combinations(hvacTempSetAreaIds, i).stream()
                                                .map(
                                                        areaIdCombo -> {
                                                            Integer possibleHvacDualOnAreaId = 0;
                                                            for (Integer areaId : areaIdCombo) {
                                                                possibleHvacDualOnAreaId |= areaId;
                                                            }
                                                            return possibleHvacDualOnAreaId;
                                                        })
                                                .collect(Collectors.toList()));
                            }
                            ImmutableSet<Integer> allPossibleHvacDualOnAreaIds =
                                    allPossibleHvacDualOnAreaIdsBuilder.build();
                            for (int areaId : areaIds) {
                                assertWithMessage(
                                                "HVAC_DUAL_ON area ID: "
                                                        + areaId
                                                        + " must be a combination of"
                                                        + " HVAC_TEMPERATURE_SET area IDs: "
                                                        + Arrays.toString(
                                                                hvacTempSetCarPropertyConfig
                                                                        .getAreaIds()))
                                        .that(areaId)
                                        .isIn(allPossibleHvacDualOnAreaIds);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testAutomaticEmergencyBrakingEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testForwardCollisionWarningEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testBlindSpotWarningEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testLaneDepartureWarningEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @Test
    public void testLaneCenteringAssistEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify(mCarPropertyManager);
    }

    @SuppressWarnings("unchecked")
    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getPropertyList(ArraySet)",
            "android.car.hardware.property.CarPropertyManager#getBooleanProperty",
            "android.car.hardware.property.CarPropertyManager#getIntProperty",
            "android.car.hardware.property.CarPropertyManager#getFloatProperty",
            "android.car.hardware.property.CarPropertyManager#getIntArrayProperty",
            "android.car.hardware.property.CarPropertyManager#getProperty(Class, int, int)"})
    public void testGetAllSupportedReadablePropertiesSync() {
        runWithShellPermissionIdentity(
                () -> {
                    List<CarPropertyConfig> configs =
                            mCarPropertyManager.getPropertyList(mPropertyIds);
                    for (CarPropertyConfig cfg : configs) {
                        if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                            int[] areaIds = getAreaIdsHelper(cfg);
                            int propId = cfg.getPropertyId();
                            // no guarantee if we can get values, just call and check if it throws
                            // exception.
                            if (cfg.getPropertyType() == Boolean.class) {
                                for (int areaId : areaIds) {
                                    mCarPropertyManager.getBooleanProperty(propId, areaId);
                                }
                            } else if (cfg.getPropertyType() == Integer.class) {
                                for (int areaId : areaIds) {
                                    mCarPropertyManager.getIntProperty(propId, areaId);
                                }
                            } else if (cfg.getPropertyType() == Float.class) {
                                for (int areaId : areaIds) {
                                    mCarPropertyManager.getFloatProperty(propId, areaId);
                                }
                            } else if (cfg.getPropertyType() == Integer[].class) {
                                for (int areId : areaIds) {
                                    mCarPropertyManager.getIntArrayProperty(propId, areId);
                                }
                            } else {
                                for (int areaId : areaIds) {
                                    mCarPropertyManager.getProperty(
                                            cfg.getPropertyType(), propId, areaId);
                                }
                            }
                        }
                    }
                });
    }

    /**
     * Test for {@link CarPropertyManager#getPropertiesAsync}
     *
     * Generates GetPropertyRequest objects for supported readable properties and verifies if there
     * are no exceptions or request timeouts.
     */
    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getPropertiesAsync(List, "
            + "CancellationSignal, Executor, GetPropertyCallback)"})
    public void testGetAllSupportedReadablePropertiesAsync() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    Executor executor = Executors.newFixedThreadPool(1);
                    Set<Integer> pendingRequests = new ArraySet<>();
                    List<CarPropertyManager.GetPropertyRequest> getPropertyRequests =
                            new ArrayList<>();
                    List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList();
                    for (CarPropertyConfig cfg : configs) {
                        if (cfg.getAccess() != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ
                                && cfg.getAccess()
                                        != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                            continue;
                        }
                        int[] areaIds = cfg.getAreaIds();
                        int propId = cfg.getPropertyId();
                        for (int areaId : areaIds) {
                            CarPropertyManager.GetPropertyRequest gpr =
                                    mCarPropertyManager.generateGetPropertyRequest(propId, areaId);
                            getPropertyRequests.add(gpr);
                            pendingRequests.add(gpr.getRequestId());
                        }
                    }

                    TestGetPropertyAsyncCallback testGetPropertyAsyncCallback =
                            new TestGetPropertyAsyncCallback(pendingRequests);
                    mCarPropertyManager.getPropertiesAsync(
                            getPropertyRequests,
                            /* cancellationSignal= */ null,
                            executor,
                            testGetPropertyAsyncCallback);
                    testGetPropertyAsyncCallback.waitAndFinish();
                    assertWithMessage(testGetPropertyAsyncCallback.getResultList().toString())
                            .that(testGetPropertyAsyncCallback.getErrorList().isEmpty())
                            .isTrue();
                });
    }

    private static final class TestGetPropertyAsyncCallback implements
            CarPropertyManager.GetPropertyCallback {
        private final CountDownLatch mCountDownLatch;
        private final Set<Integer> mPendingRequests;
        private final int mNumberOfRequests;
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final List<String> mErrorList = new ArrayList<>();
        @GuardedBy("mLock")
        private final List<String> mResultList = new ArrayList<>();

        TestGetPropertyAsyncCallback(Set<Integer> pendingRequests) {
            mNumberOfRequests = pendingRequests.size();
            mCountDownLatch = new CountDownLatch(mNumberOfRequests);
            mPendingRequests = pendingRequests;
        }

        @Override
        public void onSuccess(@NonNull CarPropertyManager.GetPropertyResult getPropertyResult) {
            int requestId = getPropertyResult.getRequestId();
            synchronized (mLock) {
                if (!mPendingRequests.contains(requestId)) {
                    mErrorList.add("Request ID: " + requestId + " not present");
                    return;
                } else {
                    mPendingRequests.remove(requestId);
                    mResultList.add("Request ID: " + requestId + " complete with onSuccess()");
                }
            }
            mCountDownLatch.countDown();
        }

        @Override
        public void onFailure(@NonNull CarPropertyManager.GetPropertyError getPropertyError) {
            int requestId = getPropertyError.getRequestId();
            synchronized (mLock) {
                if (!mPendingRequests.contains(requestId)) {
                    mErrorList.add("Request ID: " + requestId + " not present");
                    return;
                } else {
                    mResultList.add("Request ID: " + requestId + " complete with onFailure()");
                    mPendingRequests.remove(requestId);
                }
            }
            mCountDownLatch.countDown();
        }

        public void waitAndFinish() throws InterruptedException {
            boolean res = mCountDownLatch.await(ASYNC_WAIT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            synchronized (mLock) {
                if (!res) {
                    mErrorList.add(
                            "Not enough responses received for getPropertiesAsync before timeout "
                                    + "(10s), expected " + mNumberOfRequests + " responses, got "
                                    + mPendingRequests.size());
                }
            }
        }

        public List<String> getErrorList() {
            List<String> errorList;
            synchronized (mLock) {
                errorList = new ArrayList<>(mErrorList);
            }
            return errorList;
        }

        public List<String> getResultList() {
            List<String> resultList;
            synchronized (mLock) {
                resultList = new ArrayList<>(mResultList);
            }
            return resultList;
        }
    }

    @Test
    public void testGetIntArrayProperty() {
        runWithShellPermissionIdentity(
                () -> {
                    List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
                    for (CarPropertyConfig cfg : allConfigs) {
                        if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE
                                || cfg.getAccess()
                                        == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                                || cfg.getPropertyType() != Integer[].class) {
                            // skip the test if the property is not readable or not an int array
                            // type
                            // property.
                            continue;
                        }
                        switch (cfg.getPropertyId()) {
                            case VehiclePropertyIds.INFO_FUEL_TYPE:
                                int[] fuelTypes =
                                        mCarPropertyManager.getIntArrayProperty(
                                                cfg.getPropertyId(),
                                                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                                verifyEnumsRange(EXPECTED_FUEL_TYPES, fuelTypes);
                                break;
                            case VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS:
                                int[] evPortLocations =
                                        mCarPropertyManager.getIntArrayProperty(
                                                cfg.getPropertyId(),
                                                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                                verifyEnumsRange(EXPECTED_PORT_LOCATIONS, evPortLocations);
                                break;
                            default:
                                int[] areaIds = getAreaIdsHelper(cfg);
                                for (int areaId : areaIds) {
                                    mCarPropertyManager.getIntArrayProperty(
                                            cfg.getPropertyId(), areaId);
                                }
                        }
                    }
                });
    }

    private void verifyEnumsRange(List<Integer> expectedResults, int[] results) {
        assertThat(results).isNotNull();
        // If the property is not implemented in cars, getIntArrayProperty returns an empty array.
        if (results.length == 0) {
            return;
        }
        for (int result : results) {
            assertThat(result).isIn(expectedResults);
        }
    }

    @Test
    public void testIsPropertyAvailable() {
        runWithShellPermissionIdentity(
                () -> {
                    List<CarPropertyConfig> configs =
                            mCarPropertyManager.getPropertyList(mPropertyIds);

                    for (CarPropertyConfig cfg : configs) {
                        int[] areaIds = getAreaIdsHelper(cfg);
                        for (int areaId : areaIds) {
                            assertThat(
                                            mCarPropertyManager.isPropertyAvailable(
                                                    cfg.getPropertyId(), areaId))
                                    .isTrue();
                        }
                    }
                });
    }

    @Test
    public void testRegisterCallback() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    // Test on registering a invalid property
                    int invalidPropertyId = -1;
                    boolean isRegistered =
                            mCarPropertyManager.registerCallback(
                                    new CarPropertyEventCounter(), invalidPropertyId, 0);
                    assertThat(isRegistered).isFalse();

                    // Test for continuous properties
                    int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
                    CarPropertyConfig<?> carPropertyConfig =
                            mCarPropertyManager.getCarPropertyConfig(
                                    VehiclePropertyIds.PERF_VEHICLE_SPEED);
                    float secondsToMillis = 1_000;
                    long bufferMillis = 1_000; // 1 second
                    // timeoutMillis is set to the maximum expected time needed to receive the
                    // required
                    // number of PERF_VEHICLE_SPEED events for test. If the test does not receive
                    // the
                    // required number of events before the timeout expires, it fails.
                    long timeoutMillis =
                            ((long)
                                            ((1.0f / carPropertyConfig.getMinSampleRate())
                                                    * secondsToMillis
                                                    * UI_RATE_EVENT_COUNTER))
                                    + bufferMillis;
                    CarPropertyEventCounter speedListenerUI =
                            new CarPropertyEventCounter(timeoutMillis);
                    CarPropertyEventCounter speedListenerFast = new CarPropertyEventCounter();

                    assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isEqualTo(NO_EVENTS);
                    assertThat(speedListenerUI.receivedError(vehicleSpeed)).isEqualTo(NO_EVENTS);
                    assertThat(speedListenerUI.receivedErrorWithErrorCode(vehicleSpeed))
                            .isEqualTo(NO_EVENTS);
                    assertThat(speedListenerFast.receivedEvent(vehicleSpeed)).isEqualTo(NO_EVENTS);
                    assertThat(speedListenerFast.receivedError(vehicleSpeed)).isEqualTo(NO_EVENTS);
                    assertThat(speedListenerFast.receivedErrorWithErrorCode(vehicleSpeed))
                            .isEqualTo(NO_EVENTS);

                    speedListenerUI.resetCountDownLatch(UI_RATE_EVENT_COUNTER);
                    mCarPropertyManager.registerCallback(
                            speedListenerUI, vehicleSpeed, CarPropertyManager.SENSOR_RATE_UI);
                    mCarPropertyManager.registerCallback(
                            speedListenerFast,
                            vehicleSpeed,
                            CarPropertyManager.SENSOR_RATE_FASTEST);
                    speedListenerUI.assertOnChangeEventCalled();
                    mCarPropertyManager.unregisterCallback(speedListenerUI);
                    mCarPropertyManager.unregisterCallback(speedListenerFast);

                    assertThat(speedListenerUI.receivedEvent(vehicleSpeed))
                            .isGreaterThan(NO_EVENTS);
                    assertThat(speedListenerFast.receivedEvent(vehicleSpeed))
                            .isAtLeast(speedListenerUI.receivedEvent(vehicleSpeed));
                    // The test did not change property values, it should not get error with error
                    // codes.
                    assertThat(speedListenerUI.receivedErrorWithErrorCode(vehicleSpeed))
                            .isEqualTo(NO_EVENTS);
                    assertThat(speedListenerFast.receivedErrorWithErrorCode(vehicleSpeed))
                            .isEqualTo(NO_EVENTS);

                    // Test for on_change properties
                    int nightMode = VehiclePropertyIds.NIGHT_MODE;
                    CarPropertyEventCounter nightModeListener = new CarPropertyEventCounter();
                    nightModeListener.resetCountDownLatch(ONCHANGE_RATE_EVENT_COUNTER);
                    mCarPropertyManager.registerCallback(nightModeListener, nightMode, 0);
                    nightModeListener.assertOnChangeEventCalled();
                    assertThat(nightModeListener.receivedEvent(nightMode)).isEqualTo(1);
                    mCarPropertyManager.unregisterCallback(nightModeListener);
                });
    }

    @Test
    public void testUnregisterCallback() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
                    CarPropertyEventCounter speedListenerNormal = new CarPropertyEventCounter();
                    CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter();

                    mCarPropertyManager.registerCallback(
                            speedListenerNormal,
                            vehicleSpeed,
                            CarPropertyManager.SENSOR_RATE_NORMAL);

                    // test on unregistering a callback that was never registered
                    try {
                        mCarPropertyManager.unregisterCallback(speedListenerUI);
                    } catch (Exception e) {
                        Assert.fail();
                    }

                    mCarPropertyManager.registerCallback(
                            speedListenerUI, vehicleSpeed, CarPropertyManager.SENSOR_RATE_UI);
                    speedListenerUI.resetCountDownLatch(UI_RATE_EVENT_COUNTER);
                    speedListenerUI.assertOnChangeEventCalled();
                    mCarPropertyManager.unregisterCallback(speedListenerNormal, vehicleSpeed);

                    int currentEventNormal = speedListenerNormal.receivedEvent(vehicleSpeed);
                    int currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
                    // Because we copy the callback outside the lock, so even after
                    // unregisterCallback, one
                    // callback that is already copied out still might be called.
                    // As a result, we verify that the callback is not called more than once.
                    speedListenerNormal.assertOnChangeEventNotCalledWithinMs(WAIT_CALLBACK);

                    assertThat(speedListenerNormal.receivedEvent(vehicleSpeed))
                            .isEqualTo(currentEventNormal);
                    assertThat(speedListenerUI.receivedEvent(vehicleSpeed))
                            .isNotEqualTo(currentEventUI);

                    mCarPropertyManager.unregisterCallback(speedListenerUI);
                    speedListenerUI.assertOnChangeEventNotCalledWithinMs(WAIT_CALLBACK);

                    currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
                    assertThat(speedListenerUI.receivedEvent(vehicleSpeed))
                            .isEqualTo(currentEventUI);
                });
    }

    @Test
    public void testUnregisterWithPropertyId() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    // Ignores the test if wheel_tick property does not exist in the car.
                    assumeTrue(
                            "WheelTick is not available, skip unregisterCallback test",
                            mCarPropertyManager.isPropertyAvailable(
                                    VehiclePropertyIds.WHEEL_TICK,
                                    VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

                    CarPropertyConfig wheelTickConfig =
                            mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.WHEEL_TICK);
                    CarPropertyConfig speedConfig =
                            mCarPropertyManager.getCarPropertyConfig(
                                    VehiclePropertyIds.PERF_VEHICLE_SPEED);
                    float maxSampleRateHz =
                            Math.max(
                                    wheelTickConfig.getMaxSampleRate(),
                                    speedConfig.getMaxSampleRate());
                    int eventCounter = getCounterBySampleRate(maxSampleRateHz);

                    // Ignores the test if sampleRates for properties are too low.
                    assumeTrue(
                            "The SampleRates for properties are too low, "
                                    + "skip testUnregisterWithPropertyId test",
                            eventCounter != 0);
                    CarPropertyEventCounter speedAndWheelTicksListener =
                            new CarPropertyEventCounter();

                    // CarService will register them to the maxSampleRate in CarPropertyConfig
                    mCarPropertyManager.registerCallback(
                            speedAndWheelTicksListener,
                            VehiclePropertyIds.PERF_VEHICLE_SPEED,
                            CarPropertyManager.SENSOR_RATE_FASTEST);
                    mCarPropertyManager.registerCallback(
                            speedAndWheelTicksListener,
                            VehiclePropertyIds.WHEEL_TICK,
                            CarPropertyManager.SENSOR_RATE_FASTEST);
                    speedAndWheelTicksListener.resetCountDownLatch(eventCounter);
                    speedAndWheelTicksListener.assertOnChangeEventCalled();

                    // Tests unregister the individual property
                    mCarPropertyManager.unregisterCallback(
                            speedAndWheelTicksListener, VehiclePropertyIds.PERF_VEHICLE_SPEED);

                    // Updates counter after unregistering the PERF_VEHICLE_SPEED
                    int wheelTickEventCounter =
                            getCounterBySampleRate(wheelTickConfig.getMaxSampleRate());
                    speedAndWheelTicksListener.resetCountDownLatch(wheelTickEventCounter);
                    speedAndWheelTicksListener.assertOnChangeEventCalled();
                    int speedEventCountAfterFirstCountDown =
                            speedAndWheelTicksListener.receivedEvent(
                                    VehiclePropertyIds.PERF_VEHICLE_SPEED);
                    int wheelTickEventCountAfterFirstCountDown =
                            speedAndWheelTicksListener.receivedEvent(VehiclePropertyIds.WHEEL_TICK);

                    speedAndWheelTicksListener.resetCountDownLatch(wheelTickEventCounter);
                    speedAndWheelTicksListener.assertOnChangeEventCalled();
                    int speedEventCountAfterSecondCountDown =
                            speedAndWheelTicksListener.receivedEvent(
                                    VehiclePropertyIds.PERF_VEHICLE_SPEED);
                    int wheelTickEventCountAfterSecondCountDown =
                            speedAndWheelTicksListener.receivedEvent(VehiclePropertyIds.WHEEL_TICK);

                    assertThat(speedEventCountAfterFirstCountDown)
                            .isEqualTo(speedEventCountAfterSecondCountDown);
                    assertThat(wheelTickEventCountAfterSecondCountDown)
                            .isGreaterThan(wheelTickEventCountAfterFirstCountDown);
                });
    }

    @Test
    public void testNoPropertyPermissionsGranted() {
        assertWithMessage("CarPropertyManager.getPropertyList()")
                .that(mCarPropertyManager.getPropertyList())
                .isEmpty();
    }

    @Test
    public void testPermissionReadDriverMonitoringSettingsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_DRIVER_MONITORING_SETTINGS_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS);
    }

    @Test
    public void testPermissionControlDriverMonitoringSettingsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS);
    }

    @Test
    public void testPermissionReadDriverMonitoringStatesGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_DRIVER_MONITORING_STATES_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_DRIVER_MONITORING_STATES);
    }

    @Test
    public void testPermissionCarEnergyGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_ENERGY_PROPERTIES);
                    }
                },
                Car.PERMISSION_ENERGY);
    }

    @Test
    public void testPermissionCarEnergyPortsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_ENERGY_PORTS_PROPERTIES);
                    }
                },
                Car.PERMISSION_ENERGY_PORTS);
    }

    @Test
    public void testPermissionCarExteriorEnvironmentGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_EXTERIOR_ENVIRONMENT_PROPERTIES);
                    }
                },
                Car.PERMISSION_EXTERIOR_ENVIRONMENT);
    }

    @Test
    public void testPermissionCarInfoGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_INFO_PROPERTIES);
                    }
                },
                Car.PERMISSION_CAR_INFO);
    }

    @Test
    public void testPermissionCarPowertrainGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_POWERTRAIN_PROPERTIES);
                    }
                },
                Car.PERMISSION_POWERTRAIN);
    }

    @Test
    public void testPermissionControlCarPowertrainGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_POWERTRAIN_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_POWERTRAIN);
    }

    @Test
    public void testPermissionCarSpeedGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_SPEED_PROPERTIES);
                    }
                },
                Car.PERMISSION_SPEED);
    }

    @Test
    public void testPermissionReadCarDisplayUnitsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_CAR_DISPLAY_UNITS_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_DISPLAY_UNITS);
    }

    @Test
    public void testPermissionControlSteeringWheelGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_STEERING_WHEEL_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_STEERING_WHEEL);
    }

    @Test
    public void testPermissionControlCarAirbagsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_AIRBAGS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_AIRBAGS);
    }

    @Test
    public void testPermissionReadAdasSettingsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_ADAS_SETTINGS_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_ADAS_SETTINGS);
    }

    @Test
    public void testPermissionControlAdasSettingsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_ADAS_SETTINGS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_ADAS_SETTINGS);
    }

    @Test
    public void testPermissionReadAdasStatesGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_ADAS_STATES_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_ADAS_STATES);
    }

    @Test
    public void testPermissionControlAdasStatesGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                        "%s",
                                        VehiclePropertyIds.toString(
                                                carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_ADAS_STATES_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_ADAS_STATES);
    }

    private int getCounterBySampleRate(float maxSampleRateHz) {
        if (Float.compare(maxSampleRateHz, (float) FAST_OR_FASTEST_EVENT_COUNTER) > 0) {
            return FAST_OR_FASTEST_EVENT_COUNTER;
        } else if (Float.compare(maxSampleRateHz, (float) UI_RATE_EVENT_COUNTER) > 0) {
            return UI_RATE_EVENT_COUNTER;
        } else if (Float.compare(maxSampleRateHz, (float) ONCHANGE_RATE_EVENT_COUNTER) > 0) {
            return ONCHANGE_RATE_EVENT_COUNTER;
        } else {
            return 0;
        }
    }

    // Returns {0} if the property is global property, otherwise query areaId for CarPropertyConfig
    private int[] getAreaIdsHelper(CarPropertyConfig config) {
        if (config.isGlobalProperty()) {
            return new int[]{0};
        } else {
            return config.getAreaIds();
        }
    }

    private static class CarPropertyEventCounter implements CarPropertyEventCallback {
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final SparseArray<Integer> mEventCounter = new SparseArray<>();
        @GuardedBy("mLock")
        private final SparseArray<Integer> mErrorCounter = new SparseArray<>();
        @GuardedBy("mLock")
        private final SparseArray<Integer> mErrorWithErrorCodeCounter = new SparseArray<>();
        @GuardedBy("mLock")
        private int mCounter = FAST_OR_FASTEST_EVENT_COUNTER;
        @GuardedBy("mLock")
        private CountDownLatch mCountDownLatch = new CountDownLatch(mCounter);
        private final long mTimeoutMillis;

        CarPropertyEventCounter(long timeoutMillis) {
            mTimeoutMillis = timeoutMillis;
        }

        CarPropertyEventCounter() {
            this(WAIT_CALLBACK);
        }

        public int receivedEvent(int propId) {
            int val;
            synchronized (mLock) {
                val = mEventCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedError(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedErrorWithErrorCode(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorWithErrorCodeCounter.get(propId, 0);
            }
            return val;
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            synchronized (mLock) {
                int val = mEventCounter.get(value.getPropertyId(), 0) + 1;
                mEventCounter.put(value.getPropertyId(), val);
                mCountDownLatch.countDown();
            }
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            synchronized (mLock) {
                int val = mErrorCounter.get(propId, 0) + 1;
                mErrorCounter.put(propId, val);
            }
        }

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {
            synchronized (mLock) {
                int val = mErrorWithErrorCodeCounter.get(propId, 0) + 1;
                mErrorWithErrorCodeCounter.put(propId, val);
            }
        }

        public void resetCountDownLatch(int counter) {
            synchronized (mLock) {
                mCountDownLatch = new CountDownLatch(counter);
                mCounter = counter;
            }
        }

        public void assertOnChangeEventCalled() throws InterruptedException {
            CountDownLatch countDownLatch;
            int counter;
            synchronized (mLock) {
                countDownLatch = mCountDownLatch;
                counter = mCounter;
            }
            if (!countDownLatch.await(mTimeoutMillis, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException(
                        "Callback is not called "
                                + counter
                                + "times in "
                                + mTimeoutMillis
                                + " ms. It was only called "
                                + (counter - countDownLatch.getCount())
                                + " times.");
            }
        }

        public void assertOnChangeEventNotCalledWithinMs(long durationInMs)
                throws InterruptedException {
            CountDownLatch countDownLatch;
            synchronized (mLock) {
                mCountDownLatch = new CountDownLatch(1);
                countDownLatch = mCountDownLatch;
            }
            long timeoutMillis = 2 * durationInMs;
            long startTimeMillis = SystemClock.uptimeMillis();
            while (true) {
                if (countDownLatch.await(durationInMs, TimeUnit.MILLISECONDS)) {
                    if (SystemClock.uptimeMillis() - startTimeMillis > timeoutMillis) {
                        // If we are still receiving events when timeout happens, the test
                        // failed.
                        throw new IllegalStateException(
                                "We are still receiving callback within "
                                        + durationInMs
                                        + " seconds after "
                                        + timeoutMillis
                                        + " ms.");
                    }
                    // Receive a event within the time period. This means there are still events
                    // being generated. Wait for another period and hope the events stop.
                    synchronized (mLock) {
                        mCountDownLatch = new CountDownLatch(1);
                        countDownLatch = mCountDownLatch;
                    }
                } else {
                    break;
                }
            }
        }
    }
}
