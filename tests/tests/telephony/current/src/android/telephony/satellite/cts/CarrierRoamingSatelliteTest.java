/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.telephony.satellite.cts;

import static android.telephony.mockmodem.MockSimService.MOCK_SIM_PROFILE_ID_TWN_CHT;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import com.android.internal.telephony.flags.Flags;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;

public class CarrierRoamingSatelliteTest extends CarrierRoamingSatelliteTestBase {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private static final String TAG = "CarrierRoamingSatelliteTest";

    /**
     * Setup before all tests.
     * @throws Exception exception
     */
    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd(TAG, "beforeAllTests");

        assumeTrue(shouldTestSatelliteWithMockService());
        beforeAllTestsBase();
        insertSatelliteEnabledSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
    }

    /**
     * Cleanup resources after all tests.
     * @throws Exception exception
     */
    @AfterClass
    public static void afterAllTests() throws Exception {
        logd(TAG, "afterAllTests");
        removeSatelliteEnabledSim(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT);
        afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd(TAG, "setUp()");
    }

    @After
    public void tearDown() throws Exception {
        logd(TAG, "tearDown()");
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_CARRIER_ENABLED_SATELLITE_FLAG)
    public void testCarrierRoamingNtnModeListener() throws Exception {
        CarrierRoamingNtnModeListenerTest listener =
                new CarrierRoamingNtnModeListenerTest();
        listener.clearModeChanges();

        adoptShellIdentity();
        sTelephonyManager.registerTelephonyCallback(getContext().getMainExecutor(), listener);
        try {
            // Get NTN mode immediately after registering
            assertTrue(listener.waitForModeChanged(1));
            assertTrue(listener.getNtnMode());
            listener.clearModeChanges();

            // Satellite network is lost, no callback as hysteresis timeout is not expired
            sMockModemManager.changeNetworkService(SLOT_ID_0, MOCK_SIM_PROFILE_ID_TWN_CHT, false);
            assertFalse(listener.waitForModeChanged(1));
            listener.clearModeChanges();

            // Callback is received after hysteresis timeout
            assertTrue(listener.waitForModeChanged(1));
            assertFalse(listener.getNtnMode());
        } finally {
            sTelephonyManager.unregisterTelephonyCallback(listener);
            dropShellIdentity();
        }
    }
}
