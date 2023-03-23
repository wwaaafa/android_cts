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

import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.car.cts.utils.ShellPermissionUtils.runWithShellPermissionIdentity;
import static android.car.hardware.property.CarPropertyManager.GetPropertyResult;
import static android.car.hardware.property.CarPropertyManager.SetPropertyResult;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
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
import android.car.hardware.CarHvacFanDirection;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.AreaIdConfig;
import android.car.hardware.property.AutomaticEmergencyBrakingState;
import android.car.hardware.property.BlindSpotWarningState;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.car.hardware.property.CruiseControlCommand;
import android.car.hardware.property.CruiseControlState;
import android.car.hardware.property.CruiseControlType;
import android.car.hardware.property.EmergencyLaneKeepAssistState;
import android.car.hardware.property.ErrorState;
import android.car.hardware.property.EvChargeState;
import android.car.hardware.property.EvRegenerativeBrakingState;
import android.car.hardware.property.EvStoppingMode;
import android.car.hardware.property.ForwardCollisionWarningState;
import android.car.hardware.property.HandsOnDetectionDriverState;
import android.car.hardware.property.HandsOnDetectionWarning;
import android.car.hardware.property.LaneCenteringAssistCommand;
import android.car.hardware.property.LaneCenteringAssistState;
import android.car.hardware.property.LaneDepartureWarningState;
import android.car.hardware.property.LaneKeepAssistState;
import android.car.hardware.property.LocationCharacterization;
import android.car.hardware.property.TrailerState;
import android.car.hardware.property.VehicleElectronicTollCollectionCardStatus;
import android.car.hardware.property.VehicleElectronicTollCollectionCardType;
import android.car.hardware.property.VehicleLightState;
import android.car.hardware.property.VehicleLightSwitch;
import android.car.hardware.property.VehicleOilLevel;
import android.car.hardware.property.VehicleTurnSignal;
import android.car.hardware.property.VehicleVendorPermission;
import android.car.hardware.property.WindshieldWipersState;
import android.car.hardware.property.WindshieldWipersSwitch;
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
import androidx.annotation.Nullable;
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
import java.util.Collection;
import java.util.List;
import java.util.Objects;
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
    private static final ImmutableSet<Integer> TRAILER_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            TrailerState.STATE_UNKNOWN,
                            TrailerState.STATE_NOT_PRESENT,
                            TrailerState.STATE_PRESENT,
                            TrailerState.STATE_ERROR)
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
    private static final ImmutableSet<Integer> VEHICLE_OIL_LEVELS =
            ImmutableSet.<Integer>builder()
                    .add(
                            VehicleOilLevel.LEVEL_CRITICALLY_LOW,
                            VehicleOilLevel.LEVEL_LOW,
                            VehicleOilLevel.LEVEL_NORMAL,
                            VehicleOilLevel.LEVEL_HIGH,
                            VehicleOilLevel.LEVEL_ERROR)
                    .build();
    private static final ImmutableSet<Integer> WINDSHIELD_WIPERS_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            WindshieldWipersState.OTHER,
                            WindshieldWipersState.OFF,
                            WindshieldWipersState.ON,
                            WindshieldWipersState.SERVICE)
                    .build();
    private static final ImmutableSet<Integer> WINDSHIELD_WIPERS_SWITCHES =
            ImmutableSet.<Integer>builder()
                    .add(
                            WindshieldWipersSwitch.OTHER,
                            WindshieldWipersSwitch.OFF,
                            WindshieldWipersSwitch.MIST,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_1,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_2,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_3,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_4,
                            WindshieldWipersSwitch.INTERMITTENT_LEVEL_5,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_1,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_2,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_3,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_4,
                            WindshieldWipersSwitch.CONTINUOUS_LEVEL_5,
                            WindshieldWipersSwitch.AUTO,
                            WindshieldWipersSwitch.SERVICE)
                    .build();
    private static final ImmutableSet<Integer> EV_STOPPING_MODES =
            ImmutableSet.<Integer>builder().add(EvStoppingMode.STATE_OTHER,
                    EvStoppingMode.STATE_CREEP, EvStoppingMode.STATE_ROLL,
                    EvStoppingMode.STATE_HOLD).build();

    private static final ImmutableSet<Integer> LOCATION_CHARACTERIZATIONS =
            ImmutableSet.<Integer>builder()
                    .add(
                            LocationCharacterization.PRIOR_LOCATIONS,
                            LocationCharacterization.GYROSCOPE_FUSION,
                            LocationCharacterization.ACCELEROMETER_FUSION,
                            LocationCharacterization.COMPASS_FUSION,
                            LocationCharacterization.WHEEL_SPEED_FUSION,
                            LocationCharacterization.STEERING_ANGLE_FUSION,
                            LocationCharacterization.CAR_SPEED_FUSION,
                            LocationCharacterization.DEAD_RECKONED,
                            LocationCharacterization.RAW_GNSS_ONLY)
                    .build();
    private static final ImmutableSet<Integer> HVAC_TEMPERATURE_DISPLAY_UNITS =
            ImmutableSet.<Integer>builder().add(VehicleUnit.CELSIUS,
                    VehicleUnit.FAHRENHEIT).build();
    private static final ImmutableSet<Integer> EMERGENCY_LANE_KEEP_ASSIST_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            EmergencyLaneKeepAssistState.OTHER,
                            EmergencyLaneKeepAssistState.ENABLED,
                            EmergencyLaneKeepAssistState.WARNING_LEFT,
                            EmergencyLaneKeepAssistState.WARNING_RIGHT,
                            EmergencyLaneKeepAssistState.ACTIVATED_STEER_LEFT,
                            EmergencyLaneKeepAssistState.ACTIVATED_STEER_RIGHT,
                            EmergencyLaneKeepAssistState.USER_OVERRIDE)
                    .build();
    private static final ImmutableSet<Integer> CRUISE_CONTROL_TYPES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CruiseControlType.OTHER,
                            CruiseControlType.STANDARD,
                            CruiseControlType.ADAPTIVE,
                            CruiseControlType.PREDICTIVE)
                    .build();
    private static final ImmutableSet<Integer> CRUISE_CONTROL_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CruiseControlState.OTHER,
                            CruiseControlState.ENABLED,
                            CruiseControlState.ACTIVATED,
                            CruiseControlState.USER_OVERRIDE,
                            CruiseControlState.SUSPENDED,
                            CruiseControlState.FORCED_DEACTIVATION_WARNING)
                    .build();
    private static final ImmutableSet<Integer> CRUISE_CONTROL_COMMANDS =
            ImmutableSet.<Integer>builder()
                    .add(
                            CruiseControlCommand.ACTIVATE,
                            CruiseControlCommand.SUSPEND,
                            CruiseControlCommand.INCREASE_TARGET_SPEED,
                            CruiseControlCommand.DECREASE_TARGET_SPEED,
                            CruiseControlCommand.INCREASE_TARGET_TIME_GAP,
                            CruiseControlCommand.DECREASE_TARGET_TIME_GAP)
                    .build();
    private static final ImmutableSet<Integer> HANDS_ON_DETECTION_DRIVER_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            HandsOnDetectionDriverState.OTHER,
                            HandsOnDetectionDriverState.HANDS_ON,
                            HandsOnDetectionDriverState.HANDS_OFF)
                    .build();
    private static final ImmutableSet<Integer> HANDS_ON_DETECTION_WARNINGS =
            ImmutableSet.<Integer>builder()
                    .add(
                            HandsOnDetectionWarning.OTHER,
                            HandsOnDetectionWarning.NO_WARNING,
                            HandsOnDetectionWarning.WARNING)
                    .build();
    private static final ImmutableSet<Integer> ERROR_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            ErrorState.OTHER_ERROR_STATE,
                            ErrorState.NOT_AVAILABLE_DISABLED,
                            ErrorState.NOT_AVAILABLE_SPEED_LOW,
                            ErrorState.NOT_AVAILABLE_SPEED_HIGH,
                            ErrorState.NOT_AVAILABLE_POOR_VISIBILITY,
                            ErrorState.NOT_AVAILABLE_SAFETY)
                    .build();
    private static final ImmutableSet<Integer> AUTOMATIC_EMERGENCY_BRAKING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            AutomaticEmergencyBrakingState.OTHER,
                            AutomaticEmergencyBrakingState.ENABLED,
                            AutomaticEmergencyBrakingState.ACTIVATED,
                            AutomaticEmergencyBrakingState.USER_OVERRIDE)
                    .build();
    private static final ImmutableSet<Integer> FORWARD_COLLISION_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            ForwardCollisionWarningState.OTHER,
                            ForwardCollisionWarningState.NO_WARNING,
                            ForwardCollisionWarningState.WARNING)
                    .build();
    private static final ImmutableSet<Integer> BLIND_SPOT_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            BlindSpotWarningState.OTHER,
                            BlindSpotWarningState.NO_WARNING,
                            BlindSpotWarningState.WARNING)
                    .build();
    private static final ImmutableSet<Integer> LANE_DEPARTURE_WARNING_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneDepartureWarningState.OTHER,
                            LaneDepartureWarningState.NO_WARNING,
                            LaneDepartureWarningState.WARNING_LEFT,
                            LaneDepartureWarningState.WARNING_RIGHT)
                    .build();
    private static final ImmutableSet<Integer> LANE_KEEP_ASSIST_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneKeepAssistState.OTHER,
                            LaneKeepAssistState.ENABLED,
                            LaneKeepAssistState.ACTIVATED_STEER_LEFT,
                            LaneKeepAssistState.ACTIVATED_STEER_RIGHT,
                            LaneKeepAssistState.USER_OVERRIDE)
                    .build();
    private static final ImmutableSet<Integer> LANE_CENTERING_ASSIST_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneCenteringAssistState.OTHER,
                            LaneCenteringAssistState.ENABLED,
                            LaneCenteringAssistState.ACTIVATION_REQUESTED,
                            LaneCenteringAssistState.ACTIVATED,
                            LaneCenteringAssistState.USER_OVERRIDE,
                            LaneCenteringAssistState.FORCED_DEACTIVATION_WARNING)
                    .build();
    private static final ImmutableSet<Integer> LANE_CENTERING_ASSIST_COMMANDS =
            ImmutableSet.<Integer>builder()
                    .add(
                            LaneCenteringAssistCommand.ACTIVATE,
                            LaneCenteringAssistCommand.DEACTIVATE)
                    .build();
    private static final ImmutableSet<Integer> SINGLE_HVAC_FAN_DIRECTIONS = ImmutableSet.of(
            /*VehicleHvacFanDirection.FACE=*/0x1, /*VehicleHvacFanDirection.FLOOR=*/0x2,
            /*VehicleHvacFanDirection.DEFROST=*/0x4);
    private static final ImmutableSet<Integer> ALL_POSSIBLE_HVAC_FAN_DIRECTIONS =
            generateAllPossibleHvacFanDirections();
    private static final ImmutableSet<Integer> VEHICLE_SEAT_OCCUPANCY_STATES = ImmutableSet.of(
            /*VehicleSeatOccupancyState.UNKNOWN=*/0, /*VehicleSeatOccupancyState.VACANT=*/1,
            /*VehicleSeatOccupancyState.OCCUPIED=*/2);
    private static final ImmutableSet<Integer> CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            CarHvacFanDirection.UNKNOWN)
                    .build();
    private static final ImmutableSet<Integer> CRUISE_CONTROL_TYPE_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder()
                    .addAll(ERROR_STATES)
                    .add(
                            CruiseControlType.OTHER)
                    .build();
    private static final ImmutableSet<Integer> EV_STOPPING_MODE_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            EvStoppingMode.STATE_OTHER)
                    .build();
    private static final ImmutableSet<Integer> WINDSHIELD_WIPERS_SWITCH_UNWRITABLE_STATES =
            ImmutableSet.<Integer>builder()
                    .add(
                            WindshieldWipersSwitch.OTHER)
                    .build();

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
                    .add(
                            VehiclePropertyIds.HANDS_ON_DETECTION_DRIVER_STATE,
                            VehiclePropertyIds.HANDS_ON_DETECTION_WARNING)
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
                            VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
                            VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS)
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
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_SEATS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.SEAT_MEMORY_SELECT,
                            VehiclePropertyIds.SEAT_MEMORY_SET,
                            VehiclePropertyIds.SEAT_BELT_BUCKLED,
                            VehiclePropertyIds.SEAT_BELT_HEIGHT_POS,
                            VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE,
                            VehiclePropertyIds.SEAT_FORE_AFT_POS,
                            VehiclePropertyIds.SEAT_FORE_AFT_MOVE,
                            VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
                            VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE,
                            VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
                            VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE,
                            VehiclePropertyIds.SEAT_HEIGHT_POS,
                            VehiclePropertyIds.SEAT_HEIGHT_MOVE,
                            VehiclePropertyIds.SEAT_DEPTH_POS,
                            VehiclePropertyIds.SEAT_DEPTH_MOVE,
                            VehiclePropertyIds.SEAT_TILT_POS,
                            VehiclePropertyIds.SEAT_TILT_MOVE,
                            VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS,
                            VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE,
                            VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
                            VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
                            VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS,
                            VehiclePropertyIds.SEAT_HEADREST_HEIGHT_POS_V2,
                            VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE,
                            VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS,
                            VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE,
                            VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS,
                            VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE,
                            VehiclePropertyIds.SEAT_EASY_ACCESS_ENABLED,
                            VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_POS,
                            VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_MOVE,
                            VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_POS,
                            VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_MOVE,
                            VehiclePropertyIds.SEAT_WALK_IN_POS,
                            VehiclePropertyIds.SEAT_OCCUPANCY)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_IDENTIFICATION_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.INFO_VIN)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_MILEAGE_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.PERF_ODOMETER)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_STEERING_STATE_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.PERF_STEERING_ANGLE,
                            VehiclePropertyIds.PERF_REAR_STEERING_ANGLE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_ENGINE_DETAILED_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                            VehiclePropertyIds.ENGINE_OIL_LEVEL,
                            VehiclePropertyIds.ENGINE_OIL_TEMP,
                            VehiclePropertyIds.ENGINE_RPM,
                            VehiclePropertyIds.ENGINE_IDLE_AUTO_STOP_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_ENERGY_PORTS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.FUEL_DOOR_OPEN,
                            VehiclePropertyIds.EV_CHARGE_PORT_OPEN)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_ADJUST_RANGE_REMAINING_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.RANGE_REMAINING)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_TIRES_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.TIRE_PRESSURE,
                            VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_EXTERIOR_LIGHTS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.TURN_SIGNAL_STATE,
                            VehiclePropertyIds.HEADLIGHTS_STATE,
                            VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                            VehiclePropertyIds.FOG_LIGHTS_STATE,
                            VehiclePropertyIds.HAZARD_LIGHTS_STATE,
                            VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE,
                            VehiclePropertyIds.REAR_FOG_LIGHTS_STATE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_DYNAMICS_STATE_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.ABS_ACTIVE,
                            VehiclePropertyIds.TRACTION_CONTROL_ACTIVE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_CLIMATE_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.HVAC_FAN_SPEED,
                            VehiclePropertyIds.HVAC_FAN_DIRECTION,
                            VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
                            VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                            VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                            VehiclePropertyIds.HVAC_DEFROSTER,
                            VehiclePropertyIds.HVAC_AC_ON,
                            VehiclePropertyIds.HVAC_MAX_AC_ON,
                            VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
                            VehiclePropertyIds.HVAC_RECIRC_ON,
                            VehiclePropertyIds.HVAC_DUAL_ON,
                            VehiclePropertyIds.HVAC_AUTO_ON,
                            VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
                            VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
                            VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
                            VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                            VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                            VehiclePropertyIds.HVAC_POWER_ON,
                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                            VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
                            VehiclePropertyIds.HVAC_SEAT_VENTILATION,
                            VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_DOORS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.DOOR_POS,
                            VehiclePropertyIds.DOOR_MOVE,
                            VehiclePropertyIds.DOOR_LOCK,
                            VehiclePropertyIds.DOOR_CHILD_LOCK_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_MIRRORS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.MIRROR_Z_POS,
                            VehiclePropertyIds.MIRROR_Z_MOVE,
                            VehiclePropertyIds.MIRROR_Y_POS,
                            VehiclePropertyIds.MIRROR_Y_MOVE,
                            VehiclePropertyIds.MIRROR_LOCK,
                            VehiclePropertyIds.MIRROR_FOLD,
                            VehiclePropertyIds.MIRROR_AUTO_FOLD_ENABLED,
                            VehiclePropertyIds.MIRROR_AUTO_TILT_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_WINDOWS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.WINDOW_POS,
                            VehiclePropertyIds.WINDOW_MOVE,
                            VehiclePropertyIds.WINDOW_LOCK)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_WINDSHIELD_WIPERS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.WINDSHIELD_WIPERS_PERIOD,
                            VehiclePropertyIds.WINDSHIELD_WIPERS_STATE,
                            VehiclePropertyIds.WINDSHIELD_WIPERS_SWITCH)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_WINDSHIELD_WIPERS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.WINDSHIELD_WIPERS_SWITCH)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_EXTERIOR_LIGHTS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.HEADLIGHTS_SWITCH,
                            VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH,
                            VehiclePropertyIds.FOG_LIGHTS_SWITCH,
                            VehiclePropertyIds.HAZARD_LIGHTS_SWITCH,
                            VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH,
                            VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_INTERIOR_LIGHTS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_STATE,
                            VehiclePropertyIds.CABIN_LIGHTS_STATE,
                            VehiclePropertyIds.READING_LIGHTS_STATE,
                            VehiclePropertyIds.STEERING_WHEEL_LIGHTS_STATE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_INTERIOR_LIGHTS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_SWITCH,
                            VehiclePropertyIds.CABIN_LIGHTS_SWITCH,
                            VehiclePropertyIds.READING_LIGHTS_SWITCH,
                            VehiclePropertyIds.STEERING_WHEEL_LIGHTS_SWITCH)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CAR_EPOCH_TIME_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.EPOCH_TIME)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_CAR_ENERGY_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT,
                            VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT,
                            VehiclePropertyIds.EV_CHARGE_SWITCH)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_PRIVILEGED_CAR_INFO_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.VEHICLE_CURB_WEIGHT,
                            VehiclePropertyIds.TRAILER_PRESENT)
                    .build();
    private static final ImmutableList<Integer>
            PERMISSION_CONTROL_DISPLAY_UNITS_VENDOR_EXTENSION_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.DISTANCE_DISPLAY_UNITS,
                            VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                            VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                            VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS,
                            VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
                            VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_ADAS_SETTINGS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                            VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                            VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_KEEP_ASSIST_ENABLED,
                            VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                            VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                            VehiclePropertyIds.CRUISE_CONTROL_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_ADAS_SETTINGS_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                            VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                            VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                            VehiclePropertyIds.LANE_KEEP_ASSIST_ENABLED,
                            VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                            VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                            VehiclePropertyIds.CRUISE_CONTROL_ENABLED)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_READ_ADAS_STATES_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_STATE,
                            VehiclePropertyIds.FORWARD_COLLISION_WARNING_STATE,
                            VehiclePropertyIds.BLIND_SPOT_WARNING_STATE,
                            VehiclePropertyIds.LANE_DEPARTURE_WARNING_STATE,
                            VehiclePropertyIds.LANE_KEEP_ASSIST_STATE,
                            VehiclePropertyIds.LANE_CENTERING_ASSIST_STATE,
                            VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_STATE,
                            VehiclePropertyIds.CRUISE_CONTROL_TYPE,
                            VehiclePropertyIds.CRUISE_CONTROL_STATE,
                            VehiclePropertyIds.CRUISE_CONTROL_TARGET_SPEED,
                            VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP,
                            VehiclePropertyIds
                                    .ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_ADAS_STATES_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.LANE_CENTERING_ASSIST_COMMAND,
                            VehiclePropertyIds.CRUISE_CONTROL_TYPE,
                            VehiclePropertyIds.CRUISE_CONTROL_COMMAND,
                            VehiclePropertyIds.CRUISE_CONTROL_TARGET_SPEED,
                            VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP)
                    .build();
    private static final ImmutableList<Integer> PERMISSION_CONTROL_GLOVE_BOX_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.GLOVE_BOX_DOOR_POS,
                            VehiclePropertyIds.GLOVE_BOX_LOCKED)
                  .build();
    private static final ImmutableList<Integer>
            PERMISSION_ACCESS_FINE_LOCATION_PROPERTIES =
            ImmutableList.<Integer>builder()
                    .add(
                            VehiclePropertyIds.LOCATION_CHARACTERIZATION)
                    .build();
    private static final ImmutableList<String> VENDOR_PROPERTY_PERMISSIONS =
            ImmutableList.<String>builder()
                    .add(
                            Car.PERMISSION_VENDOR_EXTENSION,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_WINDOW,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_WINDOW,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_DOOR,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_DOOR,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_SEAT,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_SEAT,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_MIRROR,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_MIRROR,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_INFO,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_INFO,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_ENGINE,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_ENGINE,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_HVAC,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_HVAC,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_LIGHT,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_LIGHT,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_1,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_1,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_2,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_2,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_3,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_3,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_4,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_4,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_5,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_5,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_6,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_6,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_7,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_7,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_8,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_8,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_9,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_9,
                            VehicleVendorPermission.PERMISSION_GET_CAR_VENDOR_CATEGORY_10,
                            VehicleVendorPermission.PERMISSION_SET_CAR_VENDOR_CATEGORY_10)
                    .build();

    private static final int VEHICLE_PROPERTY_GROUP_MASK = 0xf0000000;
    private static final int VEHICLE_PROPERTY_GROUP_VENDOR = 0x20000000;
    private static final int LOCATION_CHARACTERIZATION_VALID_VALUES_MASK =
            LocationCharacterization.PRIOR_LOCATIONS
            | LocationCharacterization.GYROSCOPE_FUSION
            | LocationCharacterization.ACCELEROMETER_FUSION
            | LocationCharacterization.COMPASS_FUSION
            | LocationCharacterization.WHEEL_SPEED_FUSION
            | LocationCharacterization.STEERING_ANGLE_FUSION
            | LocationCharacterization.CAR_SPEED_FUSION
            | LocationCharacterization.DEAD_RECKONED
            | LocationCharacterization.RAW_GNSS_ONLY;

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

    private static void verifyEnumValuesAreDistinct(
            ImmutableSet<Integer>... possibleCarPropertyValues) {
        ImmutableSet.Builder<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder();
        int numCarPropertyValues = 0;
        for (ImmutableSet<Integer> values: possibleCarPropertyValues) {
            combinedCarPropertyValues.addAll(values);
            numCarPropertyValues += values.size();
        }
        int combinedCarPropertyValuesLength = combinedCarPropertyValues.build().size();
        assertWithMessage("The number of distinct enum values")
                .that(combinedCarPropertyValuesLength)
                .isEqualTo(numCarPropertyValues);
    }

    private static void verifyWindshieldWipersSwitchLevelsAreConsecutive(
            List<Integer> supportedEnumValues, ImmutableList<Integer> levels, int areaId) {
        for (int i = 0; i < levels.size(); i++) {
            Integer level = levels.get(i);
            if (supportedEnumValues.contains(level)) {
                for (int j = i + 1; j < levels.size(); j++) {
                    assertWithMessage(
                                    "For VehicleAreaWindow area ID " + areaId + ", "
                                        + WindshieldWipersSwitch.toString(levels.get(j))
                                        + " must be supported if "
                                        + WindshieldWipersSwitch.toString(level)
                                        + " is supported.")
                            .that(levels.get(j))
                            .isIn(supportedEnumValues);
                }
                break;
            }
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

    private VehiclePropertyVerifier<?>[] getAllVerifiers() {
        return new VehiclePropertyVerifier[] {
             getGearSelectionVerifier(),
             getNightModeVerifier(),
             getPerfVehicleSpeedVerifier(),
             getPerfVehicleSpeedDisplayVerifier(),
             getParkingBrakeOnVerifier(),
             getEmergencyLaneKeepAssistEnabledVerifier(),
             getEmergencyLaneKeepAssistStateVerifier(),
             getCruiseControlEnabledVerifier(),
             getCruiseControlTypeVerifier(),
             getCruiseControlStateVerifier(),
             getCruiseControlCommandVerifier(),
             getCruiseControlTargetSpeedVerifier(),
             getAdaptiveCruiseControlTargetTimeGapVerifier(),
             getAdaptiveCruiseControlLeadVehicleMeasuredDistanceVerifier(),
             getHandsOnDetectionEnabledVerifier(),
             getHandsOnDetectionDriverStateVerifier(),
             getHandsOnDetectionWarningVerifier(),
             getWheelTickVerifier(),
             getInfoVinVerifier(),
             getInfoMakeVerifier(),
             getInfoModelVerifier(),
             getInfoFuelCapacityVerifier(),
             getInfoFuelTypeVerifier(),
             getInfoEvBatteryCapacityVerifier(),
             getInfoEvConnectorTypeVerifier(),
             getInfoFuelDoorLocationVerifier(),
             getInfoEvPortLocationVerifier(),
             getInfoMultiEvPortLocationsVerifier(),
             getInfoDriverSeatVerifier(),
             getInfoExteriorDimensionsVerifier(),
             getEpochTimeVerifier(),
             getLocationCharacterizationVerifier(),
             getElectronicTollCollectionCardTypeVerifier(),
             getElectronicTollCollectionCardStatusVerifier(),
             getGeneralSafetyRegulationComplianceVerifier(),
             getEnvOutsideTemperatureVerifier(),
             getCurrentGearVerifier(),
             getParkingBrakeAutoApplyVerifier(),
             getIgnitionStateVerifier(),
             getEvBrakeRegenerationLevelVerifier(),
             getEvStoppingModeVerifier(),
             getAbsActiveVerifier(),
             getTractionControlActiveVerifier(),
             getDoorPosVerifier(),
             getDoorMoveVerifier(),
             getDoorLockVerifier(),
             getDoorChildLockEnabledVerifier(),
             // TODO(b/273988725): Put all verifiers here.
        };
    }

    private VehiclePropertyVerifier<Integer> getGearSelectionVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.GEAR_SELECTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireProperty()
                .setAllPossibleEnumValues(VEHICLE_GEARS)
                .setPossibleConfigArrayValues(VEHICLE_GEARS)
                .requirePropertyValueTobeInConfigArray()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build();
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportGearSelection() {
        getGearSelectionVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getNightModeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.NIGHT_MODE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .requireProperty()
                .addReadPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
                .build();
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportNightMode() {
        getNightModeVerifier().verify();
    }

    private VehiclePropertyVerifier<Float> getPerfVehicleSpeedVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_VEHICLE_SPEED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .requireProperty()
                .addReadPermission(Car.PERMISSION_SPEED)
                .build();
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportPerfVehicleSpeed() {
        getPerfVehicleSpeedVerifier().verify();
    }

    private VehiclePropertyVerifier<Float> getPerfVehicleSpeedDisplayVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_VEHICLE_SPEED_DISPLAY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_SPEED)
                .build();
    }

    @Test
    public void testPerfVehicleSpeedDisplayIfSupported() {
        getPerfVehicleSpeedDisplayVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getParkingBrakeOnVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PARKING_BRAKE_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .requireProperty()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build();
    }

    @CddTest(requirements = {"2.5.1"})
    @Test
    @ApiRequirements(
            minCarVersion = ApiRequirements.CarVersion.TIRAMISU_0,
            minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public void testMustSupportParkingBrakeOn() {
        getParkingBrakeOnVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getEmergencyLaneKeepAssistEnabledVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build();
    }

    @Test
    public void testEmergencyLaneKeepAssistEnabledIfSupported() {
        getEmergencyLaneKeepAssistEnabledVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getEmergencyLaneKeepAssistStateVerifier() {
        ImmutableSet<Integer> possibleEnumValues = ImmutableSet.<Integer>builder()
                .addAll(EMERGENCY_LANE_KEEP_ASSIST_STATES)
                .addAll(ERROR_STATES)
                .build();

        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EMERGENCY_LANE_KEEP_ASSIST_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(possibleEnumValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build();
    }

    @Test
    public void testEmergencyLaneKeepAssistStateIfSupported() {
        getEmergencyLaneKeepAssistStateVerifier().verify();
    }

    @Test
    public void testEmergencyLaneKeepAssistStateAndErrorStateDontIntersect() {
        verifyEnumValuesAreDistinct(EMERGENCY_LANE_KEEP_ASSIST_STATES, ERROR_STATES);
    }

    private VehiclePropertyVerifier<Boolean> getCruiseControlEnabledVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build();
    }

    @Test
    public void testCruiseControlEnabledIfSupported() {
        getCruiseControlEnabledVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getCruiseControlTypeVerifier() {
        ImmutableSet<Integer> possibleEnumValues = ImmutableSet.<Integer>builder()
                .addAll(CRUISE_CONTROL_TYPES)
                .addAll(ERROR_STATES)
                .build();

        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(possibleEnumValues)
                .setAllPossibleUnwritableValues(CRUISE_CONTROL_TYPE_UNWRITABLE_STATES)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_STATES)
                .build();
    }

    @Test
    public void testCruiseControlTypeIfSupported() {
        getCruiseControlTypeVerifier().verify();
    }

    @Test
    public void testCruiseControlTypeAndErrorStateDontIntersect() {
        verifyEnumValuesAreDistinct(CRUISE_CONTROL_TYPES, ERROR_STATES);
    }

    private VehiclePropertyVerifier<Integer> getCruiseControlStateVerifier() {
        ImmutableSet<Integer> possibleEnumValues = ImmutableSet.<Integer>builder()
                .addAll(CRUISE_CONTROL_STATES)
                .addAll(ERROR_STATES)
                .build();

        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(possibleEnumValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build();
    }

    @Test
    public void testCruiseControlStateIfSupported() {
        getCruiseControlStateVerifier().verify();
    }

    @Test
    public void testCruiseControlStateAndErrorStateDontIntersect() {
        verifyEnumValuesAreDistinct(CRUISE_CONTROL_STATES, ERROR_STATES);
    }

    private VehiclePropertyVerifier<Integer> getCruiseControlCommandVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_COMMAND,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(CRUISE_CONTROL_COMMANDS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_STATES)
                .build();
    }

    @Test
    public void testCruiseControlCommandIfSupported() {
        getCruiseControlCommandVerifier().verify();
    }

    private VehiclePropertyVerifier<Float> getCruiseControlTargetSpeedVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRUISE_CONTROL_TARGET_SPEED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, mCarPropertyManager)
                .requireMinMaxValues()
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            List<? extends AreaIdConfig<?>> areaIdConfigs = carPropertyConfig
                                    .getAreaIdConfigs();
                            for (AreaIdConfig<?> areaIdConfig : areaIdConfigs) {
                                assertWithMessage("Min/Max values must be non-negative")
                                        .that((Float) areaIdConfig.getMinValue())
                                        .isAtLeast(0F);
                            }
                        })
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build();
    }

    @Test
    public void testCruiseControlTargetSpeedIfSupported() {
        getCruiseControlTargetSpeedVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getAdaptiveCruiseControlTargetTimeGapVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            List<Integer> configArray = carPropertyConfig.getConfigArray();

                            for (Integer configArrayValue : configArray) {
                                assertWithMessage("configArray values of "
                                        + "ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP must be "
                                        + "positive. Detected value " + configArrayValue + " in "
                                        + "configArray " + configArray)
                                        .that(configArrayValue)
                                        .isGreaterThan(0);
                            }

                            for (int i = 0; i < configArray.size() - 1; i++) {
                                assertWithMessage("configArray values of "
                                        + "ADAPTIVE_CRUISE_CONTROL_TARGET_TIME_GAP must be in "
                                        + "ascending order. Detected value " + configArray.get(i)
                                        + " is greater than or equal to " + configArray.get(i + 1)
                                        + " in configArray " + configArray)
                                        .that(configArray.get(i))
                                        .isLessThan(configArray.get(i + 1));
                            }
                        })
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_STATES)
                .build();
    }

    @Test
    public void testAdaptiveCruiseControlTargetTimeGapIfSupported() {
        getAdaptiveCruiseControlTargetTimeGapVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer>
            getAdaptiveCruiseControlLeadVehicleMeasuredDistanceVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ADAPTIVE_CRUISE_CONTROL_LEAD_VEHICLE_MEASURED_DISTANCE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build();
    }

    @Test
    public void testAdaptiveCruiseControlLeadVehicleMeasuredDistanceIfSupported() {
        getAdaptiveCruiseControlLeadVehicleMeasuredDistanceVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getHandsOnDetectionEnabledVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HANDS_ON_DETECTION_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_DRIVER_MONITORING_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_DRIVER_MONITORING_SETTINGS)
                .build();
    }

    @Test
    public void testHandsOnDetectionEnabledIfSupported() {
        getHandsOnDetectionEnabledVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getHandsOnDetectionDriverStateVerifier() {
        ImmutableSet<Integer> possibleEnumValues = ImmutableSet.<Integer>builder()
                .addAll(HANDS_ON_DETECTION_DRIVER_STATES)
                .addAll(ERROR_STATES)
                .build();

        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HANDS_ON_DETECTION_DRIVER_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(possibleEnumValues)
                .addReadPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES)
                .build();
    }

    @Test
    public void testHandsOnDetectionDriverStateIfSupported() {
        getHandsOnDetectionDriverStateVerifier().verify();
    }

    @Test
    public void testHandsOnDetectionDriverStateAndErrorStateDontIntersect() {
        verifyEnumValuesAreDistinct(HANDS_ON_DETECTION_DRIVER_STATES, ERROR_STATES);
    }

    private VehiclePropertyVerifier<Integer> getHandsOnDetectionWarningVerifier() {
        ImmutableSet<Integer> possibleEnumValues = ImmutableSet.<Integer>builder()
                .addAll(HANDS_ON_DETECTION_WARNINGS)
                .addAll(ERROR_STATES)
                .build();

        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HANDS_ON_DETECTION_WARNING,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(possibleEnumValues)
                .addReadPermission(Car.PERMISSION_READ_DRIVER_MONITORING_STATES)
                .build();
    }

    @Test
    public void testHandsOnDetectionWarningIfSupported() {
        getHandsOnDetectionWarningVerifier().verify();
    }

    @Test
    public void testHandsOnDetectionWarningAndErrorStateDontIntersect() {
        verifyEnumValuesAreDistinct(HANDS_ON_DETECTION_WARNINGS, ERROR_STATES);
    }

    public VehiclePropertyVerifier<Long[]> getWheelTickVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WHEEL_TICK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Long[].class, mCarPropertyManager)
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
                        (carPropertyConfig, propertyId, areaId, timestampNanos, wheelTicks) -> {
                            List<Integer> wheelTickConfigArray = carPropertyConfig.getConfigArray();
                            int supportedWheels = wheelTickConfigArray.get(0);

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
                .build();
    }

    @Test
    public void testWheelTickIfSupported() {
        getWheelTickVerifier().verify();
    }

    private VehiclePropertyVerifier<String> getInfoVinVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_VIN,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        String.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, vin) ->
                                assertWithMessage("INFO_VIN must be 17 characters")
                                        .that(vin)
                                        .hasLength(17))
                .addReadPermission(Car.PERMISSION_IDENTIFICATION)
                .build();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                    "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testInfoVinIfSupported() {
        getInfoVinVerifier().verify();
    }

    private VehiclePropertyVerifier<String> getInfoMakeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MAKE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        String.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testInfoMakeIfSupported() {
        getInfoMakeVerifier().verify();
    }

    private VehiclePropertyVerifier<String> getInfoModelVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MODEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        String.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testInfoModelYearIfSupported() {
        getInfoModelVerifier().verify();
    }

    private VehiclePropertyVerifier<Float> getInfoFuelCapacityVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_FUEL_CAPACITY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, fuelCapacity) ->
                                assertWithMessage(
                                                "INFO_FUEL_CAPACITY Float value must be greater"
                                                    + " than or equal 0")
                                        .that(fuelCapacity)
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testInfoFuelCapacityIfSupported() {
        getInfoFuelCapacityVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer[]> getInfoFuelTypeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_FUEL_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, fuelTypes) -> {
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
                .build();
    }

    @Test
    public void testInfoFuelTypeIfSupported() {
        getInfoFuelTypeVerifier().verify();
    }

    private VehiclePropertyVerifier<Float> getInfoEvBatteryCapacityVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evBatteryCapacity) ->
                                        assertWithMessage(
                                                        "INFO_EV_BATTERY_CAPACITY Float value must"
                                                            + " be greater than or equal to 0")
                                                .that(evBatteryCapacity)
                                                .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testInfoEvBatteryCapacityIfSupported() {
        getInfoEvBatteryCapacityVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer[]> getInfoEvConnectorTypeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EV_CONNECTOR_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evConnectorTypes) -> {
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
                .build();
    }

    @Test
    public void testInfoEvConnectorTypeIfSupported() {
        getInfoEvConnectorTypeVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getInfoFuelDoorLocationVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_FUEL_DOOR_LOCATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(PORT_LOCATION_TYPES)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testInfoFuelDoorLocationIfSupported() {
        getInfoFuelDoorLocationVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getInfoEvPortLocationVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EV_PORT_LOCATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(PORT_LOCATION_TYPES)
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testInfoEvPortLocationIfSupported() {
        getInfoEvPortLocationVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer[]> getInfoMultiEvPortLocationsVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evPortLocations) -> {
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
                .build();
    }

    @Test
    public void testInfoMultiEvPortLocationsIfSupported() {
        getInfoMultiEvPortLocationsVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getInfoDriverSeatVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_DRIVER_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
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
                .build();
    }

    @Test
    public void testInfoDriverSeatIfSupported() {
        getInfoDriverSeatVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer[]> getInfoExteriorDimensionsVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.INFO_EXTERIOR_DIMENSIONS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                exteriorDimensions) -> {
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
                .build();
    }

    @Test
    public void testInfoExteriorDimensionsIfSupported() {
        getInfoExteriorDimensionsVerifier().verify();
    }

    private VehiclePropertyVerifier<Long> getEpochTimeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EPOCH_TIME,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Long.class, mCarPropertyManager)
                .addWritePermission(Car.PERMISSION_CAR_EPOCH_TIME)
                .build();
    }

    @Test
    public void testEpochTimeIfSupported() {
        getEpochTimeVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getLocationCharacterizationVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LOCATION_CHARACTERIZATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, value) -> {
                            boolean deadReckonedIsSet = (value
                                    & LocationCharacterization.DEAD_RECKONED)
                                    == LocationCharacterization.DEAD_RECKONED;
                            boolean rawGnssOnlyIsSet = (value
                                    & LocationCharacterization.RAW_GNSS_ONLY)
                                    == LocationCharacterization.RAW_GNSS_ONLY;
                            assertWithMessage("LOCATION_CHARACTERIZATION must not be 0 "
                                    + "Found value: " + value)
                                    .that(value)
                                    .isNotEqualTo(0);
                            assertWithMessage("LOCATION_CHARACTERIZATION must not have any bits "
                                    + "set outside of the bit flags defined in "
                                    + "LocationCharacterization. Found value: " + value)
                                    .that(value & LOCATION_CHARACTERIZATION_VALID_VALUES_MASK)
                                    .isEqualTo(value);
                            assertWithMessage("LOCATION_CHARACTERIZATION must have one of "
                                    + "DEAD_RECKONED or RAW_GNSS_ONLY set. They both cannot be set "
                                    + "either. Found value: " + value)
                                    .that(deadReckonedIsSet ^ rawGnssOnlyIsSet)
                                    .isTrue();
                        })
                .addReadPermission(ACCESS_FINE_LOCATION)
                .build();
    }

    @Test
    public void testLocationCharacterizationIfSupported() {
        getLocationCharacterizationVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getElectronicTollCollectionCardTypeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_TYPE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleElectronicTollCollectionCardType.UNKNOWN,
                                VehicleElectronicTollCollectionCardType
                                        .JP_ELECTRONIC_TOLL_COLLECTION_CARD,
                                VehicleElectronicTollCollectionCardType
                                        .JP_ELECTRONIC_TOLL_COLLECTION_CARD_V2))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testElectronicTollCollectionCardTypeIfSupported() {
        getElectronicTollCollectionCardTypeVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getElectronicTollCollectionCardStatusVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ELECTRONIC_TOLL_COLLECTION_CARD_STATUS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleElectronicTollCollectionCardStatus.UNKNOWN,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_VALID,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_INVALID,
                                VehicleElectronicTollCollectionCardStatus
                                        .ELECTRONIC_TOLL_COLLECTION_CARD_NOT_INSERTED))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testElectronicTollCollectionCardStatusIfSupported() {
        getElectronicTollCollectionCardStatusVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getGeneralSafetyRegulationComplianceVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.GENERAL_SAFETY_REGULATION_COMPLIANCE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                GsrComplianceType.GSR_COMPLIANCE_TYPE_NOT_REQUIRED,
                                GsrComplianceType.GSR_COMPLIANCE_TYPE_REQUIRED_V1))
                .addReadPermission(Car.PERMISSION_CAR_INFO)
                .build();
    }

    @Test
    public void testGeneralSafetyRegulationComplianceIfSupported() {
        getGeneralSafetyRegulationComplianceVerifier().verify();
    }

    private VehiclePropertyVerifier<Float> getEnvOutsideTemperatureVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENV_OUTSIDE_TEMPERATURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_EXTERIOR_ENVIRONMENT)
                .build();
    }

    @Test
    public void testEnvOutsideTemperatureIfSupported() {
        getEnvOutsideTemperatureVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getCurrentGearVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CURRENT_GEAR,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_GEARS)
                .setPossibleConfigArrayValues(VEHICLE_GEARS)
                .requirePropertyValueTobeInConfigArray()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build();
    }

    @Test
    public void testCurrentGearIfSupported() {
        getCurrentGearVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getParkingBrakeAutoApplyVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PARKING_BRAKE_AUTO_APPLY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build();
    }

    @Test
    public void testParkingBrakeAutoApplyIfSupported() {
        getParkingBrakeAutoApplyVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getIgnitionStateVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.IGNITION_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
                        ImmutableSet.of(
                                VehicleIgnitionState.UNDEFINED,
                                VehicleIgnitionState.LOCK,
                                VehicleIgnitionState.OFF,
                                VehicleIgnitionState.ACC,
                                VehicleIgnitionState.ON,
                                VehicleIgnitionState.START))
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .build();
    }

    @Test
    public void testIgnitionStateIfSupported() {
        getIgnitionStateVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getEvBrakeRegenerationLevelVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BRAKE_REGENERATION_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .addWritePermission(Car.PERMISSION_CONTROL_POWERTRAIN)
                .build();
    }

    @Test
    public void testEvBrakeRegenerationLevelIfSupported() {
        getEvBrakeRegenerationLevelVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getEvStoppingModeVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_STOPPING_MODE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(EV_STOPPING_MODES)
                .setAllPossibleUnwritableValues(EV_STOPPING_MODE_UNWRITABLE_STATES)
                .addReadPermission(Car.PERMISSION_POWERTRAIN)
                .addWritePermission(Car.PERMISSION_CONTROL_POWERTRAIN)
                .build();
    }

    @Test
    public void testEvStoppingModeIfSupported() {
        getEvStoppingModeVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getAbsActiveVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ABS_ACTIVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_DYNAMICS_STATE)
                .build();
    }

    @Test
    public void testAbsActiveIfSupported() {
        getAbsActiveVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getTractionControlActiveVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TRACTION_CONTROL_ACTIVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_DYNAMICS_STATE)
                .build();
    }

    @Test
    public void testTractionControlActiveIfSupported() {
        getTractionControlActiveVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getDoorPosVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build();
    }

    @Test
    public void testDoorPosIfSupported() {
        getDoorPosVerifier().verify();
    }

    private VehiclePropertyVerifier<Integer> getDoorMoveVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build();
    }

    @Test
    public void testDoorMoveIfSupported() {
        getDoorMoveVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getDoorLockVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build();
    }

    @Test
    public void testDoorLockIfSupported() {
        getDoorLockVerifier().verify();
    }

    private VehiclePropertyVerifier<Boolean> getDoorChildLockEnabledVerifier() {
        return VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DOOR_CHILD_LOCK_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_DOOR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_DOORS)
                .build();
    }

    @Test
    public void testDoorChildLockEnabledIfSupported() {
        getDoorChildLockEnabledVerifier().verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testMirrorZPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Z_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testMirrorZMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Z_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testMirrorYPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Y_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testMirrorYMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_Y_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testMirrorLockIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testMirrorFoldIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_FOLD,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    public void testMirrorAutoFoldEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_AUTO_FOLD_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    public void testMirrorAutoTiltEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.MIRROR_AUTO_TILT_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_MIRRORS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testWindowPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDOW_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testWindowMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDOW_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testWindowLockIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDOW_LOCK,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_WINDOWS)
                .build()
                .verify();
    }

    @Test
    public void testWindshieldWipersPeriodIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDSHIELD_WIPERS_PERIOD,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS)
                .build()
                .verify();
    }

    @Test
    public void testWindshieldWipersStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDSHIELD_WIPERS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(WINDSHIELD_WIPERS_STATES)
                .addReadPermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS)
                .build()
                .verify();
    }

    @Test
    public void testWindshieldWipersSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.WINDSHIELD_WIPERS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(WINDSHIELD_WIPERS_SWITCHES)
                .setAllPossibleUnwritableValues(WINDSHIELD_WIPERS_SWITCH_UNWRITABLE_STATES)
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            // Test to ensure that for both INTERMITTENT_LEVEL_* and
                            // CONTINUOUS_LEVEL_* the supportedEnumValues are consecutive.
                            // E.g. levels 1,2,3 is a valid config, but 1,3,4 is not valid because
                            // level 2 must be supported if level 3 or greater is supported.
                            ImmutableList<Integer> intermittentLevels =
                                    ImmutableList.<Integer>builder()
                                            .add(
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_5,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_4,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_3,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_2,
                                                    WindshieldWipersSwitch.INTERMITTENT_LEVEL_1)
                                            .build();

                            ImmutableList<Integer> continuousLevels =
                                    ImmutableList.<Integer>builder()
                                            .add(
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_5,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_4,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_3,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_2,
                                                    WindshieldWipersSwitch.CONTINUOUS_LEVEL_1)
                                            .build();

                            for (int areaId: carPropertyConfig.getAreaIds()) {
                                AreaIdConfig<Integer> areaIdConfig =
                                        (AreaIdConfig<Integer>) carPropertyConfig
                                                .getAreaIdConfig(areaId);
                                List<Integer> supportedEnumValues =
                                        areaIdConfig.getSupportedEnumValues();

                                verifyWindshieldWipersSwitchLevelsAreConsecutive(
                                        supportedEnumValues, intermittentLevels, areaId);
                                verifyWindshieldWipersSwitchLevelsAreConsecutive(
                                        supportedEnumValues, continuousLevels, areaId);
                            }
                        })
                .addReadPermission(Car.PERMISSION_READ_WINDSHIELD_WIPERS)
                .addWritePermission(Car.PERMISSION_CONTROL_WINDSHIELD_WIPERS)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelDepthPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_DEPTH_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelDepthMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_DEPTH_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelHeightPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_HEIGHT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelTheftLockEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_THEFT_LOCK_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelLockedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LOCKED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelEasyAccessEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_EASY_ACCESS_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .addWritePermission(Car.PERMISSION_CONTROL_STEERING_WHEEL)
                .build()
                .verify();
    }

    @Test
    public void testGloveBoxDoorPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.GLOVE_BOX_DOOR_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_GLOVE_BOX)
                .addWritePermission(Car.PERMISSION_CONTROL_GLOVE_BOX)
                .build()
                .verify();
    }

    @Test
    public void testGloveBoxLockedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.GLOVE_BOX_LOCKED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_GLOVE_BOX)
                .addWritePermission(Car.PERMISSION_CONTROL_GLOVE_BOX)
                .build()
                .verify();
    }

    @Test
    public void testDistanceDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.DISTANCE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(DISTANCE_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(DISTANCE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(ImmutableSet.of(Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                        Car.PERMISSION_VENDOR_EXTENSION))
                .build()
                .verify();
    }

    @Test
    public void testFuelVolumeDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_VOLUME_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VOLUME_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(VOLUME_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(ImmutableSet.of(Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                        Car.PERMISSION_VENDOR_EXTENSION))
                .build()
                .verify();
    }

    @Test
    public void testTirePressureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TIRE_PRESSURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .requireMinMaxValues()
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, tirePressure) ->
                                assertWithMessage(
                                                "TIRE_PRESSURE Float value"
                                                        + " at Area ID equals to "
                                                        + areaId
                                                        + " must be greater than or equal 0")
                                        .that(tirePressure)
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_TIRES)
                .build()
                .verify();
    }

    @Test
    public void testCriticallyLowTirePressureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CRITICALLY_LOW_TIRE_PRESSURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WHEEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                criticallyLowTirePressure) -> {
                            assertWithMessage(
                                            "CRITICALLY_LOW_TIRE_PRESSURE Float value"
                                                    + "at Area ID equals to"
                                                    + areaId
                                                    + " must be greater than or equal 0")
                                    .that(criticallyLowTirePressure)
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
                                    .that(criticallyLowTirePressure)
                                    .isAtMost((Float) tirePressureConfig.getMinValue(areaId));
                        })
                .addReadPermission(Car.PERMISSION_TIRES)
                .build()
                .verify();
    }

    @Test
    public void testTirePressureDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TIRE_PRESSURE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(PRESSURE_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(PRESSURE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(ImmutableSet.of(Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                        Car.PERMISSION_VENDOR_EXTENSION))
                .build()
                .verify();
    }

    @Test
    public void testEvBatteryDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BATTERY_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(BATTERY_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(BATTERY_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(ImmutableSet.of(Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                        Car.PERMISSION_VENDOR_EXTENSION))
                .build()
                .verify();
    }

    @Test
    public void testVehicleSpeedDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.VEHICLE_SPEED_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(SPEED_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(SPEED_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(ImmutableSet.of(Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                        Car.PERMISSION_VENDOR_EXTENSION))
                .build()
                .verify();
    }

    @Test
    public void testFuelConsumptionUnitsDistanceOverTimeIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_CONSUMPTION_UNITS_DISTANCE_OVER_VOLUME,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addWritePermission(ImmutableSet.of(Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                        Car.PERMISSION_VENDOR_EXTENSION))
                .build()
                .verify();
    }

    @Test
    public void testFuelLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, fuelLevel) -> {
                            assertWithMessage(
                                            "FUEL_LEVEL Float value must be greater than or equal"
                                                + " 0")
                                    .that(fuelLevel)
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
                                    .that(fuelLevel)
                                    .isAtMost((Float) infoFuelCapacityValue.getValue());
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testEvBatteryLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BATTERY_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, evBatteryLevel) -> {
                            assertWithMessage(
                                            "EV_BATTERY_LEVEL Float value must be greater than or"
                                                + " equal 0")
                                    .that(evBatteryLevel)
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
                                    .that(evBatteryLevel)
                                    .isAtMost((Float) infoEvBatteryCapacityValue.getValue());
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testEvCurrentBatteryCapacityIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CURRENT_BATTERY_CAPACITY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evCurrentBatteryCapacity) -> {
                            assertWithMessage(
                                            "EV_CURRENT_BATTERY_CAPACITY Float value must be"
                                                    + "greater than or equal 0")
                                    .that(evCurrentBatteryCapacity)
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
                                    .that(evCurrentBatteryCapacity)
                                    .isAtMost((Float) infoEvBatteryCapacityValue.getValue());
                        })
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testEvBatteryInstantaneousChargeRateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_BATTERY_INSTANTANEOUS_CHARGE_RATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testRangeRemainingIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.RANGE_REMAINING,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, rangeRemaining) ->
                                assertWithMessage(
                                                "RANGE_REMAINING Float value must be greater than"
                                                    + " or equal 0")
                                        .that(rangeRemaining)
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_ENERGY)
                .addWritePermission(Car.PERMISSION_ADJUST_RANGE_REMAINING)
                .build()
                .verify();
    }

    @Test
    public void testFuelLevelLowIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_LEVEL_LOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testFuelDoorOpenIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FUEL_DOOR_OPEN,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_ENERGY_PORTS)
                .addWritePermission(Car.PERMISSION_CONTROL_ENERGY_PORTS)
                .build()
                .verify();
    }

    @Test
    public void testEvChargePortOpenIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_PORT_OPEN,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_ENERGY_PORTS)
                .addWritePermission(Car.PERMISSION_CONTROL_ENERGY_PORTS)
                .build()
                .verify();
    }

    @Test
    public void testEvChargePortConnectedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_PORT_CONNECTED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_ENERGY_PORTS)
                .build()
                .verify();
    }

    @Test
    public void testEvChargeCurrentDrawLimitIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_CURRENT_DRAW_LIMIT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, mCarPropertyManager)
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
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evChargeCurrentDrawLimit) -> {
                            List<Integer> evChargeCurrentDrawLimitConfigArray =
                                    carPropertyConfig.getConfigArray();
                            int maxCurrentDrawThresholdAmps =
                                    evChargeCurrentDrawLimitConfigArray.get(0);

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
                .verify();
    }

    @Test
    public void testEvChargePercentLimitIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_PERCENT_LIMIT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, mCarPropertyManager)
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
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evChargePercentLimit) -> {
                            List<Integer> evChargePercentLimitConfigArray =
                                    carPropertyConfig.getConfigArray();

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
                .verify();
    }

    @Test
    public void testEvChargeStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
                                            ImmutableSet.of(
                                                    EvChargeState.STATE_UNKNOWN,
                                                    EvChargeState.STATE_CHARGING,
                                                    EvChargeState.STATE_FULLY_CHARGED,
                                                    EvChargeState.STATE_NOT_CHARGING,
                                                    EvChargeState.STATE_ERROR))
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testEvChargeSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_ENERGY)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testEvChargeTimeRemainingIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_CHARGE_TIME_REMAINING,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Integer.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                evChargeTimeRemaining) ->
                                        assertWithMessage(
                                                        "EV_CHARGE_TIME_REMAINING Integer value"
                                                            + " must be greater than or equal 0")
                                                .that(evChargeTimeRemaining)
                                                .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testEvRegenerativeBrakingStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.EV_REGENERATIVE_BRAKING_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(
                                            ImmutableSet.of(
                                                    EvRegenerativeBrakingState.STATE_UNKNOWN,
                                                    EvRegenerativeBrakingState.STATE_DISABLED,
                                                    EvRegenerativeBrakingState
                                                            .STATE_PARTIALLY_ENABLED,
                                                    EvRegenerativeBrakingState
                                                            .STATE_FULLY_ENABLED))
                .addReadPermission(Car.PERMISSION_ENERGY)
                .build()
                .verify();
    }

    @Test
    public void testPerfSteeringAngleIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_STEERING_ANGLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_STEERING_STATE)
                .build()
                .verify();
    }

    @Test
    public void testPerfRearSteeringAngleIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_REAR_STEERING_ANGLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_STEERING_STATE)
                .build()
                .verify();
    }

    @Test
    public void testEngineCoolantTempIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_COOLANT_TEMP,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify();
    }

    @Test
    public void testEngineOilLevelIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_OIL_LEVEL,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_OIL_LEVELS)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify();
    }

    @Test
    public void testEngineOilTempIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_OIL_TEMP,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify();
    }

    @Test
    public void testEngineRpmIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_RPM,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, engineRpm) ->
                                assertWithMessage(
                                                "ENGINE_RPM Float value must be greater than or"
                                                    + " equal 0")
                                        .that(engineRpm)
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify();
    }

    @Test
    public void testEngineIdleAutoStopEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.ENGINE_IDLE_AUTO_STOP_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .addWritePermission(Car.PERMISSION_CAR_ENGINE_DETAILED)
                .build()
                .verify();
    }

    @Test
    public void testPerfOdometerIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.PERF_ODOMETER,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS,
                        Float.class, mCarPropertyManager)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, perfOdometer) ->
                                assertWithMessage(
                                                "PERF_ODOMETER Float value must be greater than or"
                                                    + " equal 0")
                                        .that(perfOdometer)
                                        .isAtLeast(0))
                .addReadPermission(Car.PERMISSION_MILEAGE)
                .build()
                .verify();
    }

    @Test
    public void testTurnSignalStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TURN_SIGNAL_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(TURN_SIGNAL_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testHeadlightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HEADLIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testHighBeamLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HIGH_BEAM_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testFogLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FOG_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos, fogLightsState) -> {
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
                .verify();
    }

    @Test
    public void testHazardLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HAZARD_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testFrontFogLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FRONT_FOG_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                frontFogLightsState) -> {
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
                .verify();
    }

    @Test
    public void testRearFogLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.REAR_FOG_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                rearFogLightsState) -> {
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
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testCabinLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CABIN_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
            "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
            "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
            "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testReadingLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.READING_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testVehicleCurbWeightIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.VEHICLE_CURB_WEIGHT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer.class, mCarPropertyManager)
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
                        (carPropertyConfig, propertyId, areaId, timestampNanos, curbWeightKg) -> {
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
                .verify();
    }

    @Test
    public void testHeadlightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HEADLIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testTrailerPresentIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.TRAILER_PRESENT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(TRAILER_STATES)
                .addReadPermission(Car.PERMISSION_PRIVILEGED_CAR_INFO)
                .build()
                .verify();
    }

    @Test
    public void testHighBeamLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HIGH_BEAM_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testFogLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FOG_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                fogLightsSwitch) -> {
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
                .verify();
    }

    @Test
    public void testHazardLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HAZARD_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testFrontFogLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FRONT_FOG_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                frontFogLightsSwitch) -> {
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
                .verify();
    }

    @Test
    public void testRearFogLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.REAR_FOG_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .setCarPropertyValueVerifier(
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                rearFogLightsSwitch) -> {
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
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testCabinLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.CABIN_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testReadingLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.READING_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testSteeringWheelLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.STEERING_WHEEL_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig"})
    public void testSeatMemorySelectIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_MEMORY_SELECT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
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
                .verify();
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getCarPropertyConfig"})
    public void testSeatMemorySetIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_MEMORY_SET,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
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
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatBeltBuckledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BELT_BUCKLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatBeltHeightPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BELT_HEIGHT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatBeltHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BELT_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatForeAftPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FORE_AFT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatForeAftMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FORE_AFT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatBackrestAngle1PosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatBackrestAngle1MoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_1_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatBackrestAngle2PosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatBackrestAngle2MoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_BACKREST_ANGLE_2_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeightPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEIGHT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatDepthPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_DEPTH_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatDepthMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_DEPTH_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatTiltPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_TILT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatTiltMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_TILT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatLumbarForeAftPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatLumbarForeAftMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_FORE_AFT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatLumbarSideSupportPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatLumbarSideSupportMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_SIDE_SUPPORT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
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
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeadrestHeightMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_HEIGHT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeadrestAnglePosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_ANGLE_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeadrestAngleMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_ANGLE_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeadrestForeAftPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatHeadrestForeAftMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_HEADREST_FORE_AFT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatFootwellLightsStateIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_STATES)
                .addReadPermission(Car.PERMISSION_READ_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testSeatFootwellLightsSwitchIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_FOOTWELL_LIGHTS_SWITCH,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_LIGHT_SWITCHES)
                .addReadPermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .addWritePermission(Car.PERMISSION_CONTROL_INTERIOR_LIGHTS)
                .build()
                .verify();
    }

    @Test
    public void testSeatEasyAccessEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_EASY_ACCESS_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatAirbagEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_AIRBAG_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_AIRBAGS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_AIRBAGS)
                .build()
                .verify();
    }

    @Test
    public void testSeatCushionSideSupportPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatCushionSideSupportMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_CUSHION_SIDE_SUPPORT_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatLumbarVerticalPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatLumberVerticalMoveIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_LUMBAR_VERTICAL_MOVE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    public void testSeatWalkInPosIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_WALK_IN_POS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testSeatOccupancyIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.SEAT_OCCUPANCY,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(VEHICLE_SEAT_OCCUPANCY_STATES)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_SEATS)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacDefrosterIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_DEFROSTER,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    public void testHvacElectricDefrosterOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_ELECTRIC_DEFROSTER_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_WINDOW,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    public void testHvacSideMirrorHeatIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SIDE_MIRROR_HEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    public void testHvacSteeringWheelHeatIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_STEERING_WHEEL_HEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    public void testHvacTemperatureDisplayUnitsIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_DISPLAY_UNITS,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                .setPossibleConfigArrayValues(HVAC_TEMPERATURE_DISPLAY_UNITS)
                .requirePropertyValueTobeInConfigArray()
                .verifySetterWithConfigArrayValues()
                .addReadPermission(Car.PERMISSION_READ_DISPLAY_UNITS)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    public void testHvacTemperatureValueSuggestionIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_VALUE_SUGGESTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float[].class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacPowerOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_POWER_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
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
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacFanSpeedIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_SPEED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .requireMinMaxValues()
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacFanDirectionAvailableIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_STATIC,
                        Integer[].class, mCarPropertyManager)
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
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                fanDirectionValues) -> {
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + areaId
                                                    + " must have at least 1 direction defined")
                                    .that(fanDirectionValues.length)
                                    .isAtLeast(1);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE area ID: "
                                                    + areaId
                                                    + " values all must all be unique: "
                                                    + Arrays.toString(fanDirectionValues))
                                    .that(fanDirectionValues.length)
                                    .isEqualTo(ImmutableSet.copyOf(fanDirectionValues).size());
                            for (Integer fanDirection : fanDirectionValues) {
                                assertWithMessage(
                                                "HVAC_FAN_DIRECTION_AVAILABLE's area ID: "
                                                        + areaId
                                                        + " must be a valid combination of fan"
                                                        + " directions")
                                        .that(fanDirection)
                                        .isIn(ALL_POSSIBLE_HVAC_FAN_DIRECTIONS);
                            }
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacFanDirectionIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_FAN_DIRECTION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
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
                        (carPropertyConfig, propertyId, areaId, timestampNanos,
                                hvacFanDirection) -> {
                            CarPropertyValue<Integer[]> hvacFanDirectionAvailableCarPropertyValue =
                                    mCarPropertyManager.getProperty(
                                            VehiclePropertyIds.HVAC_FAN_DIRECTION_AVAILABLE,
                                            areaId);
                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION_AVAILABLE value must be available")
                                    .that(hvacFanDirectionAvailableCarPropertyValue)
                                    .isNotNull();

                            assertWithMessage(
                                            "HVAC_FAN_DIRECTION area ID "
                                                    + areaId
                                                    + " value must be in list for"
                                                    + " HVAC_FAN_DIRECTION_AVAILABLE")
                                    .that(hvacFanDirection)
                                    .isIn(
                                            Arrays.asList(
                                                    hvacFanDirectionAvailableCarPropertyValue
                                                            .getValue()));
                        })
                .setAllPossibleUnwritableValues(CAR_HVAC_FAN_DIRECTION_UNWRITABLE_STATES)
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacTemperatureCurrentIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_CURRENT,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacTemperatureSetIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_TEMPERATURE_SET,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Float.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .setCarPropertyConfigVerifier(
                        carPropertyConfig -> {
                            List<Integer> configArray = carPropertyConfig.getConfigArray();
                            assertWithMessage("HVAC_TEMPERATURE_SET config array must be size 6")
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
                                Float minValueFloat = (Float) carPropertyConfig.getMinValue(areaId);
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
                                Float maxValueFloat = (Float) carPropertyConfig.getMaxValue(areaId);
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
                        })
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacAcOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacMaxAcOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_MAX_AC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacMaxDefrostOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_MAX_DEFROST_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacRecircOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_RECIRC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacAutoOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AUTO_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacSeatTemperatureIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SEAT_TEMPERATURE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireZeroToBeContainedInMinMaxRanges()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacActualFanSpeedRpmIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_ACTUAL_FAN_SPEED_RPM,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacAutoRecircOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_AUTO_RECIRC_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacSeatVentilationIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_SEAT_VENTILATION,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setPossiblyDependentOnHvacPowerOn()
                .requireMinMaxValues()
                .requireMinValuesToBeZero()
                .addReadPermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .addWritePermission(Car.PERMISSION_CONTROL_CAR_CLIMATE)
                .build()
                .verify();
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#getProperty(int, int)",
                "android.car.hardware.property.CarPropertyManager#setProperty",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback, int)",
                "android.car.hardware.property.PropertyNotAvailableException#getVendorErrorCode",
                "android.car.hardware.property.CarInternalErrorException#getVendorErrorCode",
                "android.car.hardware.property.CarPropertyManager"
                        + "$PropertyAsyncError#getVendorErrorCode"
            })
    public void testHvacDualOnIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.HVAC_DUAL_ON,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_SEAT,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
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
                .verify();
    }

    @Test
    public void testAutomaticEmergencyBrakingEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify();
    }

    @Test
    public void testAutomaticEmergencyBrakingStateIfSupported() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(AUTOMATIC_EMERGENCY_BRAKING_STATES)
                .addAll(ERROR_STATES)
                .build();

        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.AUTOMATIC_EMERGENCY_BRAKING_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testAutomaticEmergencyBrakingStateWithErrorState() {
        verifyEnumValuesAreDistinct(AUTOMATIC_EMERGENCY_BRAKING_STATES, ERROR_STATES);
    }

    @Test
    public void testForwardCollisionWarningEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FORWARD_COLLISION_WARNING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify();
    }

    @Test
    public void testForwardCollisionWarningStateIfSupported() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(FORWARD_COLLISION_WARNING_STATES)
                .addAll(ERROR_STATES)
                .build();

        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.FORWARD_COLLISION_WARNING_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testForwardCollisionWarningStateWithErrorState() {
        verifyEnumValuesAreDistinct(FORWARD_COLLISION_WARNING_STATES, ERROR_STATES);
    }

    @Test
    public void testBlindSpotWarningEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.BLIND_SPOT_WARNING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify();
    }

    @Test
    public void testBlindSpotWarningStateIfSupported() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(BLIND_SPOT_WARNING_STATES)
                .addAll(ERROR_STATES)
                .build();

        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.BLIND_SPOT_WARNING_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_MIRROR,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testBlindSpotWarningStateWithErrorState() {
        verifyEnumValuesAreDistinct(BLIND_SPOT_WARNING_STATES, ERROR_STATES);
    }

    @Test
    public void testLaneDepartureWarningEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_DEPARTURE_WARNING_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify();
    }

    @Test
    public void testLaneDepartureWarningStateIfSupported() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(LANE_DEPARTURE_WARNING_STATES)
                .addAll(ERROR_STATES)
                .build();

        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_DEPARTURE_WARNING_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testLaneDepartureWarningStateWithErrorState() {
        verifyEnumValuesAreDistinct(LANE_DEPARTURE_WARNING_STATES, ERROR_STATES);
    }

    @Test
    public void testLaneKeepAssistEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_KEEP_ASSIST_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify();
    }

    @Test
    public void testLaneKeepAssistStateIfSupported() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(LANE_KEEP_ASSIST_STATES)
                .addAll(ERROR_STATES)
                .build();

        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_KEEP_ASSIST_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testLaneKeepAssistStateWithErrorState() {
        verifyEnumValuesAreDistinct(LANE_KEEP_ASSIST_STATES, ERROR_STATES);
    }

    @Test
    public void testLaneCenteringAssistEnabledIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_ENABLED,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Boolean.class, mCarPropertyManager)
                .addReadPermission(Car.PERMISSION_READ_ADAS_SETTINGS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_SETTINGS)
                .build()
                .verify();
    }

    @Test
    public void testLaneCenteringAssistCommandIfSupported() {
        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_COMMAND,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(LANE_CENTERING_ASSIST_COMMANDS)
                .addWritePermission(Car.PERMISSION_CONTROL_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testLaneCenteringAssistStateIfSupported() {
        ImmutableSet<Integer> combinedCarPropertyValues = ImmutableSet.<Integer>builder()
                .addAll(LANE_CENTERING_ASSIST_STATES)
                .addAll(ERROR_STATES)
                .build();

        VehiclePropertyVerifier.newBuilder(
                        VehiclePropertyIds.LANE_CENTERING_ASSIST_STATE,
                        CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ,
                        VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL,
                        CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE,
                        Integer.class, mCarPropertyManager)
                .setAllPossibleEnumValues(combinedCarPropertyValues)
                .addReadPermission(Car.PERMISSION_READ_ADAS_STATES)
                .build()
                .verify();
    }

    @Test
    public void testLaneCenteringAssistStateWithErrorState() {
        verifyEnumValuesAreDistinct(LANE_CENTERING_ASSIST_STATES, ERROR_STATES);
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
        runWithShellPermissionIdentity(() -> {
            Executor executor = Executors.newFixedThreadPool(1);
            Set<Integer> pendingRequests = new ArraySet<>();
            List<CarPropertyManager.GetPropertyRequest> getPropertyRequests =
                    new ArrayList<>();
            Set<PropIdAreaId> requestPropIdAreaIds = new ArraySet<>();

            VehiclePropertyVerifier<?>[] verifiers = getAllVerifiers();
            for (int i = 0; i < verifiers.length; i++) {
                VehiclePropertyVerifier verifier = verifiers[i];
                if (!verifier.isSupported()) {
                    continue;
                }
                CarPropertyConfig cfg = verifier.getCarPropertyConfig();
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
                    requestPropIdAreaIds.add(new PropIdAreaId(propId, areaId));
                }
            }

            int expectedResultCount = pendingRequests.size();

            TestPropertyAsyncCallback testGetPropertyAsyncCallback =
                    new TestPropertyAsyncCallback(pendingRequests);
            mCarPropertyManager.getPropertiesAsync(
                    getPropertyRequests,
                    /* cancellationSignal= */ null,
                    executor,
                    testGetPropertyAsyncCallback);
            testGetPropertyAsyncCallback.waitAndFinish();

            assertThat(testGetPropertyAsyncCallback.getErrorList()).isEmpty();
            int resultCount = testGetPropertyAsyncCallback.getResultList().size();
            assertWithMessage("must receive at least " + expectedResultCount + " results, got "
                    + resultCount).that(resultCount).isEqualTo(expectedResultCount);

            for (PropIdAreaId receivedPropIdAreaId :
                    testGetPropertyAsyncCallback.getReceivedPropIdAreaIds()) {
                assertWithMessage("received unexpected result for " + receivedPropIdAreaId)
                        .that(requestPropIdAreaIds).contains(receivedPropIdAreaId);
            }
        });
    }

    private static final class PropIdAreaId {
        private final int mPropId;
        private final int mAreaId;

        PropIdAreaId(int propId, int areaId) {
            mPropId = propId;
            mAreaId = areaId;
        }

        PropIdAreaId(PropIdAreaId other) {
            mPropId = other.mPropId;
            mAreaId = other.mAreaId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mAreaId, mPropId);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (other.getClass() != this.getClass()) {
                return false;
            }

            PropIdAreaId o = (PropIdAreaId) other;
            return mPropId == o.mPropId && mAreaId == o.mAreaId;
        }

        @Override
        public String toString() {
            return "{propId: " + mPropId + ", areaId: " + mAreaId + "}";
        }
    }

    private static final class TestPropertyAsyncCallback implements
            CarPropertyManager.GetPropertyCallback,
            CarPropertyManager.SetPropertyCallback {
        private final CountDownLatch mCountDownLatch;
        private final Set<Integer> mPendingRequests;
        private final int mNumberOfRequests;
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final List<String> mErrorList = new ArrayList<>();
        @GuardedBy("mLock")
        private final List<String> mResultList = new ArrayList<>();
        @GuardedBy("mLock")
        private final List<PropIdAreaId> mReceivedPropIdAreaIds = new ArrayList();

        TestPropertyAsyncCallback(Set<Integer> pendingRequests) {
            mNumberOfRequests = pendingRequests.size();
            mCountDownLatch = new CountDownLatch(mNumberOfRequests);
            mPendingRequests = pendingRequests;
        }

        private static String toMsg(int requestId, int propId, int areaId) {
            return "Request ID: " + requestId + " (propId: " + propId + ", areaId: " + areaId + ")";
        }

        @Override
        public void onSuccess(@NonNull GetPropertyResult<?> gotPropertyResult) {
            int requestId = gotPropertyResult.getRequestId();
            int propId = gotPropertyResult.getPropertyId();
            int areaId = gotPropertyResult.getAreaId();
            synchronized (mLock) {
                if (!mPendingRequests.contains(requestId)) {
                    mErrorList.add(toMsg(requestId, propId, areaId) + " not present");
                    return;
                } else {
                    mPendingRequests.remove(requestId);
                    mResultList.add(toMsg(requestId, propId, areaId)
                            + " complete with onSuccess()");
                }

                if (gotPropertyResult.getValue() == null) {
                    mErrorList.add("The property value for " + toMsg(requestId, propId, areaId)
                            + " is null");
                } else {
                    mReceivedPropIdAreaIds.add(new PropIdAreaId(propId, areaId));
                }
            }
            mCountDownLatch.countDown();
        }

        @Override
        public void onSuccess(@NonNull SetPropertyResult setPropertyResult) {
            // TODO(b/264719384): Implement this.
        }

        @Override
        public void onFailure(@NonNull CarPropertyManager.PropertyAsyncError error) {
            int requestId = error.getRequestId();
            int propId = error.getPropertyId();
            int areaId = error.getAreaId();
            synchronized (mLock) {
                if (!mPendingRequests.contains(requestId)) {
                    mErrorList.add(toMsg(requestId, propId, areaId) + " not present");
                    return;
                } else {
                    mResultList.add(toMsg(requestId, propId, areaId)
                            + " complete with onFailure()");
                    mPendingRequests.remove(requestId);
                    mReceivedPropIdAreaIds.add(new PropIdAreaId(propId, areaId));
                }
            }
            mCountDownLatch.countDown();
        }

        public void waitAndFinish() throws InterruptedException {
            boolean res = mCountDownLatch.await(ASYNC_WAIT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
            synchronized (mLock) {
                if (!res) {
                    int gotRequestsCount = mNumberOfRequests - mPendingRequests.size();
                    mErrorList.add(
                            "Not enough responses received for getPropertiesAsync before timeout "
                                    + "(" + ASYNC_WAIT_TIMEOUT_IN_SEC + "s), expected "
                                    + mNumberOfRequests + " responses, got "
                                    + gotRequestsCount);
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

        public List<PropIdAreaId> getReceivedPropIdAreaIds() {
            List<PropIdAreaId> receivedPropIdAreaIds;
            synchronized (mLock) {
                receivedPropIdAreaIds = new ArrayList<>(mReceivedPropIdAreaIds);
            }
            return receivedPropIdAreaIds;
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
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#registerCallback"
            })
    public void testRegisterCallbackWithInvalidProp() throws Exception {
        runWithShellPermissionIdentity(() -> {
            int invalidPropertyId = -1;

            assertThat(mCarPropertyManager.registerCallback(
                    new CarPropertyEventCounter(), invalidPropertyId, /* updateRateHz= */ 0))
                    .isFalse();
        });
    }

    @Test
    @ApiTest(
            apis = {
                "android.car.hardware.property.CarPropertyManager#getCarPropertyConfig",
                "android.car.hardware.property.CarPropertyManager#registerCallback",
                "android.car.hardware.property.CarPropertyManager#"
                        + "unregisterCallback(CarPropertyEventCallback)"
            })
    public void testRegisterCallback() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
                    CarPropertyConfig<?> carPropertyConfig =
                            mCarPropertyManager.getCarPropertyConfig(
                                    VehiclePropertyIds.PERF_VEHICLE_SPEED);
                    float secondToMillis = 1_000;
                    long bufferMillis = 1_000; // 1 second
                    // timeoutMillis is set to the maximum expected time needed to receive the
                    // required
                    // number of PERF_VEHICLE_SPEED events for test. If the test does not receive
                    // the
                    // required number of events before the timeout expires, it fails.
                    long timeoutMillis =
                            ((long)
                                            ((1.0f / carPropertyConfig.getMinSampleRate())
                                                    * secondToMillis
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
    public void testPermissionControlGloveBoxGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_GLOVE_BOX_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_GLOVE_BOX);
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
    public void testPermissionControlCarSeatsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_SEATS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_SEATS);
    }

    @Test
    public void testPermissionIdentificationGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_IDENTIFICATION_PROPERTIES);
                    }
                },
                Car.PERMISSION_IDENTIFICATION);
    }

    @Test
    public void testPermissionMileageGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_MILEAGE_PROPERTIES);
                    }
                },
                Car.PERMISSION_MILEAGE);
    }

    @Test
    public void testPermissionReadSteeringStateGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_STEERING_STATE_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_STEERING_STATE);
    }

    @Test
    public void testPermissionCarEngineDetailedGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_ENGINE_DETAILED_PROPERTIES);
                    }
                },
                Car.PERMISSION_CAR_ENGINE_DETAILED);
    }

    @Test
    public void testPermissionControlEnergyPortsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_ENERGY_PORTS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_ENERGY_PORTS);
    }

    @Test
    public void testPermissionAdjustRangeRemainingGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_ADJUST_RANGE_REMAINING_PROPERTIES);
                    }
                },
                Car.PERMISSION_ADJUST_RANGE_REMAINING);
    }

    @Test
    public void testPermissionTiresGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_TIRES_PROPERTIES);
                    }
                },
                Car.PERMISSION_TIRES);
    }

    @Test
    public void testPermissionExteriorLightsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_EXTERIOR_LIGHTS_PROPERTIES);
                    }
                },
                Car.PERMISSION_EXTERIOR_LIGHTS);
    }

    @Test
    public void testPermissionCarDynamicsStateGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_DYNAMICS_STATE_PROPERTIES);
                    }
                },
                Car.PERMISSION_CAR_DYNAMICS_STATE);
    }

    @Test
    public void testPermissionControlCarClimateGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_CLIMATE_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_CLIMATE);
    }

    @Test
    public void testPermissionControlCarDoorsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_DOORS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_DOORS);
    }

    @Test
    public void testPermissionControlCarMirrorsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_MIRRORS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_MIRRORS);
    }

    @Test
    public void testPermissionControlCarWindowsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_WINDOWS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_WINDOWS);
    }

    @Test
    public void testPermissionReadWindshieldWipersGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_WINDSHIELD_WIPERS_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_WINDSHIELD_WIPERS);
    }

    @Test
    public void testPermissionControlWindshieldWipersGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_WINDSHIELD_WIPERS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_WINDSHIELD_WIPERS);
    }

    @Test
    public void testPermissionControlExteriorLightsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_EXTERIOR_LIGHTS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_EXTERIOR_LIGHTS);
    }

    @Test
    public void testPermissionReadInteriorLightsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_READ_INTERIOR_LIGHTS_PROPERTIES);
                    }
                },
                Car.PERMISSION_READ_INTERIOR_LIGHTS);
    }

    @Test
    public void testPermissionControlInteriorLightsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_INTERIOR_LIGHTS_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_INTERIOR_LIGHTS);
    }

    @Test
    public void testPermissionCarEpochTimeGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CAR_EPOCH_TIME_PROPERTIES);
                    }
                },
                Car.PERMISSION_CAR_EPOCH_TIME);
    }

    @Test
    public void testPermissionControlCarEnergyGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_CAR_ENERGY_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_CAR_ENERGY);
    }

    @Test
    public void testPermissionPrivilegedCarInfoGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_PRIVILEGED_CAR_INFO_PROPERTIES);
                    }
                },
                Car.PERMISSION_PRIVILEGED_CAR_INFO);
    }

    @Test
    public void testPermissionControlDisplayUnitsAndVendorExtensionGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        if ((carPropertyConfig.getPropertyId() & VEHICLE_PROPERTY_GROUP_MASK)
                                == VEHICLE_PROPERTY_GROUP_VENDOR) {
                            continue;
                        }
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_CONTROL_DISPLAY_UNITS_VENDOR_EXTENSION_PROPERTIES);
                    }
                },
                Car.PERMISSION_CONTROL_DISPLAY_UNITS,
                Car.PERMISSION_VENDOR_EXTENSION);
    }

    @Test
    public void testPermissionControlDisplayUnitsGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    assertWithMessage(
                            "There must be no exposed properties when only "
                                    + "PERMISSION_CONTROL_DISPLAY_UNITS is granted. Found: "
                                    + mCarPropertyManager.getPropertyList())
                            .that(mCarPropertyManager.getPropertyList())
                            .isEmpty();
                },
                Car.PERMISSION_CONTROL_DISPLAY_UNITS);
    }

    @Test
    public void testVendorPermissionsGranted() {
        for (String vendorPermission : VENDOR_PROPERTY_PERMISSIONS) {
            runWithShellPermissionIdentity(
                    () -> {
                        for (CarPropertyConfig<?> carPropertyConfig :
                                mCarPropertyManager.getPropertyList()) {
                            assertWithMessage(
                                    "There must be no non-vendor properties exposed by vendor "
                                            + "permissions. Found: " + VehiclePropertyIds.toString(
                                            carPropertyConfig.getPropertyId()) + " exposed by: "
                                            + vendorPermission)
                                    .that(carPropertyConfig.getPropertyId()
                                            & VEHICLE_PROPERTY_GROUP_MASK)
                                    .isEqualTo(VEHICLE_PROPERTY_GROUP_VENDOR);
                        }
                    },
                    vendorPermission);
        }
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

    @Test
    public void testPermissionAccessFineLocationGranted() {
        runWithShellPermissionIdentity(
                () -> {
                    for (CarPropertyConfig<?> carPropertyConfig :
                            mCarPropertyManager.getPropertyList()) {
                        assertWithMessage(
                                "%s",
                                VehiclePropertyIds.toString(
                                        carPropertyConfig.getPropertyId()))
                                .that(carPropertyConfig.getPropertyId())
                                .isIn(PERMISSION_ACCESS_FINE_LOCATION_PROPERTIES);
                    }
                },
                ACCESS_FINE_LOCATION);
    }

    private <T> @Nullable CarPropertyManager.SetPropertyRequest<T> addSetPropertyRequest(
            List<CarPropertyManager.SetPropertyRequest<?>> setPropertyRequests,
            int propId, int areaId, VehiclePropertyVerifier<?> verifier, Class<T> propertyType) {
        Collection<T> possibleValues = (Collection<T>) verifier.getPossibleValues(areaId);
        if (possibleValues == null || possibleValues.isEmpty()) {
            Log.w(TAG, "we can't find possible values to set for property: "
                    +  verifier.getPropertyName() + ", areaId: " + areaId
                    + ", ignore setting the property.");
            return null;
        }
        CarPropertyManager.SetPropertyRequest<T> spr =
                mCarPropertyManager.generateSetPropertyRequest(propId, areaId,
                        possibleValues.iterator().next());
        setPropertyRequests.add(spr);
        return spr;
    }

    /**
     * Test for {@link CarPropertyManager#setPropertiesAsync}
     *
     * Generates SetPropertyRequest objects for supported writable properties and verifies if there
     * are no exceptions or request timeouts.
     */
    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#setPropertiesAsync(List, "
            + "long, CancellationSignal, Executor, SetPropertyCallback)",
            "android.car.hardware.property.CarPropertyManager#generateSetPropertyRequest"})
    public void testSetAllSupportedReadWrtePropertiesAsync() throws Exception {
        runWithShellPermissionIdentity(() -> {
            Executor executor = Executors.newFixedThreadPool(1);
            Set<Integer> pendingRequests = new ArraySet<>();
            List<CarPropertyManager.SetPropertyRequest<?>> setPropertyRequests =
                    new ArrayList<>();
            Set<PropIdAreaId> requestPropIdAreaIds = new ArraySet<>();

            VehiclePropertyVerifier<?>[] verifiers = getAllVerifiers();
            for (int i = 0; i < verifiers.length; i++) {
                VehiclePropertyVerifier verifier = verifiers[i];
                if (!verifier.isSupported()) {
                    continue;
                }
                CarPropertyConfig cfg = verifier.getCarPropertyConfig();
                if (cfg.getAccess()
                                != CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE) {
                    continue;
                }

                int[] areaIds = cfg.getAreaIds();
                int propId = cfg.getPropertyId();
                for (int areaId : areaIds) {
                    CarPropertyManager.SetPropertyRequest<?> spr;
                    spr = this.addSetPropertyRequest(setPropertyRequests, propId, areaId, verifier,
                            cfg.getPropertyType());
                    if (spr == null) {
                        continue;
                    }
                    pendingRequests.add(spr.getRequestId());
                    requestPropIdAreaIds.add(new PropIdAreaId(propId, areaId));
                }
            }

            int expectedResultCount = pendingRequests.size();

            TestPropertyAsyncCallback callback = new TestPropertyAsyncCallback(pendingRequests);
            mCarPropertyManager.setPropertiesAsync(setPropertyRequests, /* timeoutInMs= */ 1000,
                    /* cancellationSignal= */ null, executor, callback);

            // TODO(b/264719384): Add validation for the results.
            // TODO(b/274128789): Restore the values to their previous values after the test.
        });
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#generateSetPropertyRequest"})
    public void testGenerateSetPropertyRequest() throws Exception {
        assertThrows(NullPointerException.class, () -> {
            mCarPropertyManager.generateSetPropertyRequest(VehiclePropertyIds.FUEL_LEVEL,
                    /* areaId= */ 1, /* value= */ null);
        });

        CarPropertyManager.SetPropertyRequest request;
        request = mCarPropertyManager.generateSetPropertyRequest(VehiclePropertyIds.FUEL_LEVEL,
                /* areaId= */ 1, /* value= */ Integer.valueOf(1));

        int requestId1 = request.getRequestId();
        assertThat(request.getPropertyId()).isEqualTo(VehiclePropertyIds.FUEL_LEVEL);
        assertThat(request.getAreaId()).isEqualTo(1);
        assertThat(request.getValue()).isEqualTo(1);

        request = mCarPropertyManager.generateSetPropertyRequest(VehiclePropertyIds.INFO_VIN,
                /* areaId= */ 2, /* value= */ new String("1234"));

        int requestId2 = request.getRequestId();
        assertThat(request.getPropertyId()).isEqualTo(VehiclePropertyIds.INFO_VIN);
        assertThat(request.getAreaId()).isEqualTo(2);
        assertThat(request.getValue()).isEqualTo(new String("1234"));
        assertWithMessage("generateSetPropertyRequest must generate unique IDs").that(requestId1)
                .isNotEqualTo(requestId2);
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#getProperty(int, int)"})
    public void testGetProperty_multipleRequestsAtOnce_mustNotThrowException() throws Exception {
        runWithShellPermissionIdentity(
                () -> {
                    // We only allow 16 sync operations at once at car service. The client will
                    // try to issue 32 requests at the same time, but 16 of them will be bounced
                    // back and will be retried later.
                    Executor executor = Executors.newFixedThreadPool(32);
                    CountDownLatch cd = new CountDownLatch(32);
                    for (int i = 0; i < 32; i++) {
                        executor.execute(() -> {
                            mCarPropertyManager.getProperty(
                                                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                                                VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                            cd.countDown();
                        });
                    }
                    cd.await(ASYNC_WAIT_TIMEOUT_IN_SEC, TimeUnit.SECONDS);
                },
                Car.PERMISSION_SPEED);
    }

    @Test
    @ApiTest(apis = {"android.car.hardware.property.CarPropertyManager#generateSetPropertyRequest",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#setUpdateRateHz",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#"
                    + "setWaitForPropertyUpdate",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#getPropertyId",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#getAreaId",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#getValue",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#getUpdateRateHz",
            "android.car.hardware.property.CarPropertyManager$SetPropertyRequest#"
                    + "isWaitForPropertyUpdate"})
    public void testSetPropertyRequestSettersGetters() throws Exception {
        int testPropId = 1;
        int testAreaId = 2;
        Float valueToSet = Float.valueOf(3.1f);
        float testUpdateRateHz = 4.1f;
        CarPropertyManager.SetPropertyRequest spr =
                mCarPropertyManager.generateSetPropertyRequest(testPropId, testAreaId, valueToSet);
        spr.setUpdateRateHz(testUpdateRateHz);

        assertThat(spr.getPropertyId()).isEqualTo(testPropId);
        assertThat(spr.getAreaId()).isEqualTo(testAreaId);
        assertThat(spr.getValue()).isEqualTo(valueToSet);
        assertThat(spr.getUpdateRateHz()).isEqualTo(testUpdateRateHz);
        assertWithMessage("waitForPropertyUpdate is true by default").that(
                spr.isWaitForPropertyUpdate()).isTrue();

        spr.setWaitForPropertyUpdate(false);

        assertThat(spr.isWaitForPropertyUpdate()).isFalse();
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
