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

import static android.view.Display.DEFAULT_DISPLAY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;

import static java.util.stream.Collectors.toList;

import android.app.UiAutomation;
import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneConfigChangeListener;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.test.ApiCheckerRule.Builder;
import android.hardware.display.DisplayManager;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;
import android.view.Display;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

@SmallTest
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Test relies on other server to connect to.")
public final class CarOccupantZoneManagerTest extends AbstractCarTestCase {

    private static String TAG = CarOccupantZoneManagerTest.class.getSimpleName();

    private OccupantZoneInfo mDriverZoneInfo;

    private CarOccupantZoneManager mCarOccupantZoneManager;

    private List<OccupantZoneInfo> mAllZones;

    private UiAutomation mUiAutomation;

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() throws Exception {
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mCarOccupantZoneManager =
                (CarOccupantZoneManager) getCar().getCarManager(Car.CAR_OCCUPANT_ZONE_SERVICE);

        mAllZones = mCarOccupantZoneManager.getAllOccupantZones();

        if (mCarOccupantZoneManager.hasDriverZone()) {
            // Retrieve the current driver zone info (disregarding the driver's seat - LHD or RHD).
            // Ensures there is only one driver zone info.
            List<OccupantZoneInfo> drivers =
                    mAllZones.stream().filter(
                            o -> o.occupantType
                                    == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER).collect(
                            toList());
            assertWithMessage("One driver zone").that(drivers).hasSize(1);
            mDriverZoneInfo = drivers.get(0);
        }
    }

    @After
    public void tearDown() throws Exception {
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getDisplayType(Display)"})
    public void testGetDisplayType_mainDisplay() {
        DisplayManager displayManager = mContext.getSystemService(DisplayManager.class);
        Display defaultDisplay = displayManager.getDisplay(DEFAULT_DISPLAY);

        assertThat(mCarOccupantZoneManager.getDisplayType(defaultDisplay)).isEqualTo(
                CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getUserForOccupant(OccupantZoneInfo)"})
    public void testDriverUserIdMustBeCurrentUser() {
        assumeDriverZone();

        int myUid = Process.myUid();
        assertWithMessage("Driver user id must correspond to current user id (Process.myUid: %s)",
                myUid).that(
                UserHandle.getUserId(myUid)).isEqualTo(
                mCarOccupantZoneManager.getUserForOccupant(mDriverZoneInfo));
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#hasDriverZone",
            "android.car.CarOccupantZoneManager#hasPassengerZones"})
    public void testHasDriverOrPassengerZone() {
        boolean hasDriverZone = false;
        boolean hasPassengerZone = false;

        for (OccupantZoneInfo info : mAllZones) {
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                hasDriverZone = true;
            } else {
                hasPassengerZone = true;
            }
        }

        assertWithMessage("hasDriverZone() should match config").that(
                mCarOccupantZoneManager.hasDriverZone()).isEqualTo(hasDriverZone);
        assertWithMessage("hasPassengerZone() should match config").that(
                mCarOccupantZoneManager.hasPassengerZones()).isEqualTo(hasPassengerZone);
        assertWithMessage("should have driver or passenger zone").that(
                hasDriverZone || hasPassengerZone).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getAllOccupantZones"})
    public void testAtLeastOneZone() {
        assertWithMessage("Should have at least one zone").that(mAllZones).isNotEmpty();
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getAllOccupantZones"})
    public void testZoneConfigs() {
        List<OccupantZoneInfo> driverZones = new LinkedList<>();
        List<OccupantZoneInfo> frontPassengerZones = new LinkedList<>();
        HashMap<Integer, List<OccupantZoneInfo>> rearPassengerZones = new HashMap<>();
        List<OccupantZoneInfo> invalidZones = new LinkedList<>();

        for (OccupantZoneInfo info : mAllZones) {
            if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                driverZones.add(info);
            } else if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_FRONT_PASSENGER) {
                frontPassengerZones.add(info);
            } else if (info.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_REAR_PASSENGER) {
                List<OccupantZoneInfo> list = rearPassengerZones.get(info.seat);
                if (list == null) {
                    list = new LinkedList<>();
                    rearPassengerZones.put(info.seat, list);
                }
                list.add(info);
            } else {
                invalidZones.add(info);
            }
        }

        assertWithMessage("Max one driver zone can exist").that(driverZones.size()).isAtMost(1);
        assertWithMessage("Max one front passenger zone can exist").that(
                frontPassengerZones.size()).isAtMost(1);
        for (Integer seat : rearPassengerZones.keySet()) {
            assertWithMessage("Max one zone can exist for seat " + seat).that(
                    rearPassengerZones.get(seat).size()).isAtMost(1);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getMyOccupantZone"})
    public void testMyZone() {
        OccupantZoneInfo info = mCarOccupantZoneManager.getMyOccupantZone();
        assumeTrue("Test user has no zone", info != null);

        OccupantZoneInfo info2 = mCarOccupantZoneManager.getOccupantZone(info.occupantType,
                info.seat);

        assertWithMessage("getMyOccupantZone and getOccupantZone should match").that(
                info).isEqualTo(info2);
        assertWithMessage("User Id for my zone").that(
                mCarOccupantZoneManager.getUserForOccupant(info)).isEqualTo(UserHandle.myUserId());
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getAllOccupantZones"})
    public void testExpectAtLeastDriverZoneExists() {
        assumeDriverZone();

        assertWithMessage(
                "Driver zone is expected to exist. Make sure a driver zone is properly defined in"
                        + " config.xml/config_occupant_display_mapping")
                .that(mCarOccupantZoneManager.getAllOccupantZones())
                .contains(mDriverZoneInfo);
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#getAllDisplaysForOccupant(OccupantZoneInfo)"
    })
    public void testDriverHasMainDisplay() {
        assumeDriverZone();

        assertWithMessage("Driver is expected to be associated with main display")
                .that(mCarOccupantZoneManager.getAllDisplaysForOccupant(mDriverZoneInfo))
                .contains(getDriverDisplay());
    }

    @Test
    @ApiTest(apis = {
            "android.car.CarOccupantZoneManager#getDisplayForOccupant(OccupantZoneInfo, int)"
    })
    public void testDriverDisplayIdIsDefaultDisplay() {
        assumeDriverZone();

        assertThat(getDriverDisplay().getDisplayId()).isEqualTo(DEFAULT_DISPLAY);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager"
            + "#registerOccupantZoneConfigChangeListener(OccupantZoneConfigChangeListener)"})
    public void testCanRegisterOccupantZoneConfigChangeListener() {
        OccupantZoneConfigChangeListener occupantZoneConfigChangeListener
                = createOccupantZoneConfigChangeListener();
        mCarOccupantZoneManager
                .registerOccupantZoneConfigChangeListener(occupantZoneConfigChangeListener);

        mCarOccupantZoneManager
                .unregisterOccupantZoneConfigChangeListener(occupantZoneConfigChangeListener);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager"
            + "#assignVisibleUserToOccupantZone(OccupantZoneInfo, UserHandle, int)"})
    public void testReassigningAlreadyAssignedZone() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        for (OccupantZoneInfo info : mAllZones) {
            int userId = mCarOccupantZoneManager.getUserForOccupant(info);
            if (userId != CarOccupantZoneManager.INVALID_USER_ID) {
                int result = mCarOccupantZoneManager.assignVisibleUserToOccupantZone(info,
                        UserHandle.of(userId), 0);
                assertWithMessage(
                        "Re-assigning the same user to zoneId:%s", info.zoneId).that(
                        result).isEqualTo(CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#INVALID_USER_ID"})
    public void testInvalidUserId() {
        assertWithMessage("INVALID_USER_ID does not match with UserHandle.USER_NULL").that(
                CarOccupantZoneManager.INVALID_USER_ID).isEqualTo(UserHandle.USER_NULL);
    }

    // TODO(b/243698156) Assign invisible user and confirm failure. U only
    // @Test
    // @ApiTest(apis = {"android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"})
    // public void testAssignInvisibleUser()

    // TODO(b/243698156) Create new visible user, assign it and stop the user and confirm that
    //  the zone is unassigned. U only
    // @Test
    // @ApiTest(apis = {"android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"})
    // public void testAssignAndStopUser()

    // TODO(b/243698156) Switch current user and confirm that non-current visible users'
    // assignment stays. U only
    // @Test
    // @ApiTest(apis = {"android.car.CarOccupantZoneManager#assignVisibleUserToOccupantZone"})
    // public void testAssignAndSwitch()

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager"
            + "#assignVisibleUserToOccupantZone(OccupantZoneInfo, UserHandle, int)"})
    public void testZoneAssignmentWithoutPermission() {
        assertThrows(SecurityException.class, () ->
                mCarOccupantZoneManager.assignVisibleUserToOccupantZone(mAllZones.get(0),
                        UserHandle.CURRENT, 0));
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager"
            + "#assignVisibleUserToOccupantZone(OccupantZoneInfo, UserHandle, int)"})
    public void testAssignInvalidUser() {
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        OccupantZoneInfo zone = mAllZones.get(0);
        // We would not create this many users. So keep it simple.
        UserHandle invalidUser = UserHandle.of(Integer.MAX_VALUE);
        assertWithMessage(
                "Check USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER").that(
                mCarOccupantZoneManager.assignVisibleUserToOccupantZone(zone, invalidUser,
                        0)).isEqualTo(
                CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_NON_VISIBLE_USER);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#unassignOccupantZone(OccupantZoneInfo)"})
    public void testUnassignDriverZone() {
        assumeDriverZone();

        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        OccupantZoneInfo driverZone = mCarOccupantZoneManager.getOccupantZone(
                CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER, 0);
        assertWithMessage("Driver zone must exist").that(driverZone).isNotNull();
        assertWithMessage("Unassigning driver zone should fail").that(
                mCarOccupantZoneManager.unassignOccupantZone(driverZone)).isEqualTo(
                CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_FAIL_DRIVER_ZONE);
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#unassignOccupantZone(OccupantZoneInfo)"})
    public void testUnassignPassengerZones() {
        assumeTrue("No passenger zone", mCarOccupantZoneManager.hasPassengerZones());

        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        for (OccupantZoneInfo zone : mCarOccupantZoneManager.getAllOccupantZones()) {
            if (zone.occupantType == CarOccupantZoneManager.OCCUPANT_TYPE_DRIVER) {
                continue;
            }
            int originalUser = mCarOccupantZoneManager.getUserForOccupant(zone);
            assertWithMessage("Unassigning passenger zone should work").that(
                    mCarOccupantZoneManager.unassignOccupantZone(zone)).isEqualTo(
                    CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);
            if (originalUser == CarOccupantZoneManager.INVALID_USER_ID) {
                continue;
            }
            assertWithMessage("Reassigning the same valid user should work").that(
                    mCarOccupantZoneManager.assignVisibleUserToOccupantZone(zone,
                            UserHandle.of(originalUser), 0)).isEqualTo(
                            CarOccupantZoneManager.USER_ASSIGNMENT_RESULT_OK);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.CarOccupantZoneManager#getDisplayIdForDriver"})
    public void testClusterDisplayIsPrivate() {
        assumeDriverZone();

        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_MANAGE_OCCUPANT_ZONE);

        int clusterDisplayId = mCarOccupantZoneManager.getDisplayIdForDriver(
                CarOccupantZoneManager.DISPLAY_TYPE_INSTRUMENT_CLUSTER);
        assumeTrue("No cluster display", clusterDisplayId != Display.INVALID_DISPLAY);

        DisplayManager dm = mContext.getSystemService(DisplayManager.class);
        Display clusterDisplay = dm.getDisplay(clusterDisplayId);
        // Fetching the private display expects null.
        if (clusterDisplay != null) {
            assertWithMessage("Cluster display#%s is private", clusterDisplayId)
                    .that((clusterDisplay.getFlags() & Display.FLAG_PRIVATE) != 0).isTrue();
        }
    }

    void assumeDriverZone() {
        assumeTrue("No driver zone", mCarOccupantZoneManager.hasDriverZone());
    }

    private Display getDriverDisplay() {
        Display driverDisplay =
                mCarOccupantZoneManager.getDisplayForOccupant(
                        mDriverZoneInfo, CarOccupantZoneManager.DISPLAY_TYPE_MAIN);
        assertWithMessage(
                "No display set for driver. Make sure a default display is set in"
                        + " config.xml/config_occupant_display_mapping")
                .that(driverDisplay)
                .isNotNull();
        return driverDisplay;
    }

    private OccupantZoneConfigChangeListener createOccupantZoneConfigChangeListener() {
        return new OccupantZoneConfigChangeListener() {
            public void onOccupantZoneConfigChanged(int changeFlags) {
                Log.i(TAG, "Got a confing change, flags: " + changeFlags);
            }
        };
    }
}
