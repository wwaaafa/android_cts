/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.time.cts.host;


import static android.app.time.cts.shell.DeviceConfigKeys.LocationTimeZoneManager.KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS;
import static android.app.time.cts.shell.DeviceConfigKeys.NAMESPACE_SYSTEM_TIME;
import static android.app.time.cts.shell.DeviceConfigShellHelper.SYNC_DISABLED_MODE_UNTIL_REBOOT;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_OK;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.DEPENDENCY_STATUS_TEMPORARILY_UNAVAILABLE;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FAKE_TZPS_APP_APK;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FAKE_TZPS_APP_PACKAGE;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.OPERATION_STATUS_OK;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_CERTAIN;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_DISABLED;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_INITIALIZING;
import static android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.PROVIDER_STATE_UNCERTAIN;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.time.ControllerStateEnum;
import android.app.time.DetectionAlgorithmStatusEnum;
import android.app.time.LocationTimeZoneManagerServiceStateProto;
import android.app.time.LocationTimeZoneProviderEventProto;
import android.app.time.TimeZoneProviderStateEnum;
import android.app.time.TimeZoneProviderStateProto;
import android.app.time.cts.shell.DeviceConfigShellHelper;
import android.app.time.cts.shell.DeviceShellCommandExecutor;
import android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper;
import android.app.time.cts.shell.FakeTimeZoneProviderAppShellHelper.FakeTimeZoneProviderShellHelper;
import android.app.time.cts.shell.LocationShellHelper;
import android.app.time.cts.shell.LocationTimeZoneManagerShellHelper;
import android.app.time.cts.shell.TimeZoneDetectorShellHelper;
import android.app.time.cts.shell.host.HostShellCommandExecutor;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.google.protobuf.Parser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/** Host-side CTS tests for the location time zone manager service. */
@RunWith(DeviceJUnit4ClassRunner.class)
public class LocationTimeZoneManagerHostTest extends BaseHostJUnit4Test {

    private boolean mOriginalLocationEnabled;
    private boolean mOriginalAutoDetectionEnabled;
    private boolean mOriginalGeoDetectionEnabled;
    private TimeZoneDetectorShellHelper mTimeZoneDetectorShellHelper;
    private LocationTimeZoneManagerShellHelper mLocationTimeZoneManagerShellHelper;
    private DeviceConfigShellHelper mDeviceConfigShellHelper;
    private DeviceConfigShellHelper.PreTestState mDeviceConfigPreTestState;
    private LocationShellHelper mLocationShellHelper;
    private FakeTimeZoneProviderShellHelper mPrimaryFakeTimeZoneProviderShellHelper;
    private FakeTimeZoneProviderShellHelper mSecondaryFakeTimeZoneProviderShellHelper;

    @Before
    public void setUp() throws Exception {
        DeviceShellCommandExecutor shellCommandExecutor = new HostShellCommandExecutor(getDevice());
        mLocationTimeZoneManagerShellHelper =
                new LocationTimeZoneManagerShellHelper(shellCommandExecutor);

        // Confirm the service being tested is present. It can be turned off, in which case there's
        // nothing to test.
        mLocationTimeZoneManagerShellHelper.assumeLocationTimeZoneManagerIsPresent();

        // Install the app that hosts the fake providers.
        // Installations are tracked in BaseHostJUnit4Test and uninstalled automatically.
        installPackage(FAKE_TZPS_APP_APK);

        mTimeZoneDetectorShellHelper = new TimeZoneDetectorShellHelper(shellCommandExecutor);
        mLocationShellHelper = new LocationShellHelper(shellCommandExecutor);
        mDeviceConfigShellHelper = new DeviceConfigShellHelper(shellCommandExecutor);

        // Stop device_config updates for the duration of the test.
        mDeviceConfigPreTestState = mDeviceConfigShellHelper.setSyncModeForTest(
                SYNC_DISABLED_MODE_UNTIL_REBOOT, NAMESPACE_SYSTEM_TIME);

        // All tests start with the location_time_zone_manager disabled so that providers can be
        // configured.
        mLocationTimeZoneManagerShellHelper.stop();

        // Make sure locations is enabled, otherwise the geo detection feature will be disabled
        // whatever the geolocation detection setting is set to.
        mOriginalLocationEnabled = mLocationShellHelper.isLocationEnabledForCurrentUser();
        if (!mOriginalLocationEnabled) {
            mLocationShellHelper.setLocationEnabledForCurrentUser(true);
        }

        // Make sure automatic time zone detection is enabled, otherwise the geo detection feature
        // will be disabled whatever the geolocation detection setting is set to.
        mOriginalAutoDetectionEnabled = mTimeZoneDetectorShellHelper.isAutoDetectionEnabled();
        if (!mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(true);
        }

        // On devices with no location time zone providers (e.g. AOSP), we cannot turn geo detection
        // on until the test LTZPs are configured as the time_zone_detector will refuse.
        mOriginalGeoDetectionEnabled = mTimeZoneDetectorShellHelper.isGeoDetectionEnabled();

        FakeTimeZoneProviderAppShellHelper fakeTimeZoneProviderAppShellHelper =
                new FakeTimeZoneProviderAppShellHelper(shellCommandExecutor);
        // Delay until the fake TZPS app can be found.
        fakeTimeZoneProviderAppShellHelper.waitForInstallation();
        mPrimaryFakeTimeZoneProviderShellHelper =
                fakeTimeZoneProviderAppShellHelper.getPrimaryLocationProviderHelper();
        mSecondaryFakeTimeZoneProviderShellHelper =
                fakeTimeZoneProviderAppShellHelper.getSecondaryLocationProviderHelper();
    }

    @After
    public void tearDown() throws Exception {
        if (!mLocationTimeZoneManagerShellHelper.isLocationTimeZoneManagerPresent()) {
            // Nothing to tear down.
            return;
        }

        // Reset the geoDetectionEnabled state while there is at least one LTZP configured: this
        // setting cannot be modified if there are no LTZPs on the device, e.g. on AOSP.
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(mOriginalGeoDetectionEnabled);

        // Turn off the service before we reset configuration, otherwise it will restart itself
        // repeatedly.
        mLocationTimeZoneManagerShellHelper.stop();

        // Reset settings and server flags as best we can.
        if (mTimeZoneDetectorShellHelper.isAutoDetectionEnabled()
                != mOriginalAutoDetectionEnabled) {
            mTimeZoneDetectorShellHelper.setAutoDetectionEnabled(mOriginalAutoDetectionEnabled);
        }
        mLocationShellHelper.setLocationEnabledForCurrentUser(mOriginalLocationEnabled);
        mDeviceConfigShellHelper.restoreDeviceConfigStateForTest(mDeviceConfigPreTestState);

        // Attempt to start the service. It may not start if there are no providers configured,
        // but that is ok.
        mLocationTimeZoneManagerShellHelper.start();
    }

    @Test
    public void testOnlyPrimary_suggestionMade() throws Exception {
        testOnlyPrimary_suggestionMade(false);
    }

    @Test
    public void testOnlyPrimary_suggestionMade_legacy() throws Exception {
        testOnlyPrimary_suggestionMade(true);
    }

    /** Tests what happens when there's only a primary provider and it makes a suggestion. */
    private void testOnlyPrimary_suggestionMade(boolean useLegacyApi) throws Exception {
        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertNotCreated();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_PROVIDERS_INITIALIZING,
                    ControllerStateEnum.CONTROLLER_STATE_STOPPED,
                    ControllerStateEnum.CONTROLLER_STATE_INITIALIZING);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_INITIALIZING);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_PERM_FAILED);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApi);

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_CERTAIN);
            assertLastEventWithSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList());
        }
    }

    @Test
    public void test_dupeSuggestionsMade_rateLimited() throws Exception {
        test_dupeSuggestionsMade_rateLimited(false);
    }

    @Test
    public void test_dupeSuggestionsMade_rateLimited_legacy() throws Exception {
        test_dupeSuggestionsMade_rateLimited(true);
    }

    /**
     * Demonstrates that duplicate equivalent reports made by location time zone providers within
     * a threshold time are ignored. It focuses on a single LTZP setup (primary only); the behavior
     * for the secondary is assumed to be identical.
     */
    private void test_dupeSuggestionsMade_rateLimited(boolean useLegacyApis) throws Exception {
        // Set the rate setting sufficiently high that rate limiting will definitely take place.
        mDeviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS,
                Long.toString(Duration.ofMinutes(10).toMillis()));

        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);

        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertNotCreated();

        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApis);
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate time zone suggestion.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApis);
        assertPrimaryMadeNoReport();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/Paris", useLegacyApis);
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate time zone suggestion.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/Paris", useLegacyApis);
        assertPrimaryMadeNoReport();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report uncertain.
        reportUncertain(mPrimaryFakeTimeZoneProviderShellHelper, useLegacyApis);
        assertPrimaryReportedUncertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate uncertain report.
        reportUncertain(mPrimaryFakeTimeZoneProviderShellHelper, useLegacyApis);
        assertPrimaryMadeNoReport();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/Paris", useLegacyApis);
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();
    }

    @Test
    public void test_dupeSuggestionsMade_notRateLimited() throws Exception {
        test_dupeSuggestionsMade_notRateLimited(false);
    }

    @Test
    public void test_dupeSuggestionsMade_notRateLimited_legacy() throws Exception {
        test_dupeSuggestionsMade_notRateLimited(true);
    }

    /**
     * Demonstrates that duplicate equivalent reports made by location time zone providers above
     * a threshold time are not filtered. It focuses on a single LTZP setup (primary only); the
     * behavior for the secondary is assumed to be identical.
     */
    private void test_dupeSuggestionsMade_notRateLimited(boolean useLegacyApis) throws Exception {
        // Set the rate sufficiently low that rate limiting will not take place.
        mDeviceConfigShellHelper.put(NAMESPACE_SYSTEM_TIME,
                KEY_LTZP_EVENT_FILTERING_AGE_THRESHOLD_MILLIS,
                "0");

        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = null;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertNotCreated();

        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report a new time zone.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApis);
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate time zone suggestion.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApis);
        assertPrimaryReportedCertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Report uncertain.
        reportUncertain(mPrimaryFakeTimeZoneProviderShellHelper, useLegacyApis);
        assertPrimaryReportedUncertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Duplicate uncertain report.
        reportUncertain(mPrimaryFakeTimeZoneProviderShellHelper, useLegacyApis);
        assertPrimaryReportedUncertain();
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();
    }

    private void assertPrimaryReportedCertain() throws Exception {
        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
    }

    private void assertPrimaryMadeNoReport() throws Exception {
        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertProviderStates(serviceState.getPrimaryProviderStatesList());
    }

    private void assertPrimaryReportedUncertain() throws Exception {
        LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
        assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_UNCERTAIN);
    }

    /** Tests what happens when there's only a secondary provider and it makes a suggestion. */
    @Test
    public void testOnlySecondary_suggestionMade() throws Exception {
        testOnlySecondary_suggestionMade(false);
    }

    /** Tests what happens when there's only a secondary provider and it makes a suggestion. */
    @Test
    public void testOnlySecondary_suggestionMade_legacy() throws Exception {
        testOnlySecondary_suggestionMade(true);
    }

    private void testOnlySecondary_suggestionMade(boolean useLegacyApis) throws Exception {
        String testPrimaryLocationTimeZoneProviderPackageName = null;
        String testSecondaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates */);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertNotCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertCreated();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_PROVIDERS_INITIALIZING,
                    ControllerStateEnum.CONTROLLER_STATE_STOPPED,
                    ControllerStateEnum.CONTROLLER_STATE_INITIALIZING);
            assertLastEventWithoutSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_PERM_FAILED);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        reportSuccess(mSecondaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApis);

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_CERTAIN);
            assertLastEventWithSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList());

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);
        }
    }

    @Test
    public void testPrimaryAndSecondary() throws Exception {
        testPrimaryAndSecondary(false);
    }

    @Test
    public void testPrimaryAndSecondary_legacy() throws Exception {
        testPrimaryAndSecondary(true);
    }

    /**
     * Tests what happens when there's both a primary and a secondary provider, the primary starts
     * by being uncertain, the secondary makes a suggestion, then the primary makes a suggestion.
     */
    private void testPrimaryAndSecondary(boolean useLegacyApis) throws Exception {
        String testPrimaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        String testSecondaryLocationTimeZoneProviderPackageName = FAKE_TZPS_APP_PACKAGE;
        mLocationTimeZoneManagerShellHelper.startWithTestProviders(
                testPrimaryLocationTimeZoneProviderPackageName,
                testSecondaryLocationTimeZoneProviderPackageName,
                true /* recordProviderStates*/);
        mTimeZoneDetectorShellHelper.setGeoDetectionEnabled(true);
        mPrimaryFakeTimeZoneProviderShellHelper.assertCreated();
        mSecondaryFakeTimeZoneProviderShellHelper.assertCreated();

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_PROVIDERS_INITIALIZING,
                    ControllerStateEnum.CONTROLLER_STATE_STOPPED,
                    ControllerStateEnum.CONTROLLER_STATE_INITIALIZING);
            assertLastEventWithoutSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED,
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_INITIALIZING);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_DISABLED);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Make the primary report being uncertain. This should cause the secondary to be started.
        reportUncertain(mPrimaryFakeTimeZoneProviderShellHelper, useLegacyApis);

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState);
            assertLastEventWithoutSuggestion(serviceState);
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_UNCERTAIN);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_UNCERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_INITIALIZING);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(
                    PROVIDER_STATE_INITIALIZING);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Make the secondary report being certain.
        reportSuccess(mSecondaryFakeTimeZoneProviderShellHelper, "Europe/London", useLegacyApis);

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState,
                    ControllerStateEnum.CONTROLLER_STATE_CERTAIN);
            assertLastEventWithSuggestion(serviceState, "Europe/London");
            assertProviderStates(serviceState.getPrimaryProviderStatesList());
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_UNCERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);
        }
        mLocationTimeZoneManagerShellHelper.clearRecordedProviderStates();

        // Make the primary report being certain.
        reportSuccess(mPrimaryFakeTimeZoneProviderShellHelper, "Europe/Paris", useLegacyApis);

        {
            LocationTimeZoneManagerServiceStateProto serviceState = dumpServiceState();
            assertControllerStateHistory(serviceState);
            assertLastEventWithSuggestion(serviceState, "Europe/Paris");
            assertProviderStates(serviceState.getPrimaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_CERTAIN);
            mPrimaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_CERTAIN);

            assertProviderStates(serviceState.getSecondaryProviderStatesList(),
                    TimeZoneProviderStateEnum.TIME_ZONE_PROVIDER_STATE_DISABLED);
            mSecondaryFakeTimeZoneProviderShellHelper.assertCurrentState(PROVIDER_STATE_DISABLED);
        }
    }

    private static void assertControllerStateHistory(
            LocationTimeZoneManagerServiceStateProto serviceState,
            ControllerStateEnum... expectedStates) {
        List<ControllerStateEnum> expectedStatesList = Arrays.asList(expectedStates);
        List<ControllerStateEnum> actualStates = serviceState.getControllerStatesList();
        assertEquals(expectedStatesList, actualStates);
    }

    private static void assertLastEventWithoutSuggestion(
            LocationTimeZoneManagerServiceStateProto actualServiceState) {
        assertTrue(actualServiceState.hasLastEvent());
        assertFalse(actualServiceState.getLastEvent().hasSuggestion());

        LocationTimeZoneProviderEventProto lastEvent = actualServiceState.getLastEvent();
        assertEquals(DetectionAlgorithmStatusEnum.DETECTION_ALGORITHM_STATUS_RUNNING,
                lastEvent.getAlgorithmStatus().getStatus());
    }

    private static void assertLastEventWithSuggestion(
            LocationTimeZoneManagerServiceStateProto actualServiceState,
            String... expectedTimeZones) {
        assertFalse(expectedTimeZones == null || expectedTimeZones.length == 0);

        assertTrue(actualServiceState.hasLastEvent());
        LocationTimeZoneProviderEventProto lastEvent = actualServiceState.getLastEvent();

        assertEquals(DetectionAlgorithmStatusEnum.DETECTION_ALGORITHM_STATUS_RUNNING,
                lastEvent.getAlgorithmStatus().getStatus());

        List<String> expectedTimeZonesList = Arrays.asList(expectedTimeZones);
        List<String> actualTimeZonesList = lastEvent.getSuggestion().getZoneIdsList();
        assertEquals(expectedTimeZonesList, actualTimeZonesList);
    }

    private static void assertProviderStates(List<TimeZoneProviderStateProto> actualStates,
            TimeZoneProviderStateEnum... expectedStates) {
        List<TimeZoneProviderStateEnum> expectedStatesList = Arrays.asList(expectedStates);
        assertEquals("Expected states: " + expectedStatesList + ", but was " + actualStates,
                expectedStatesList.size(), actualStates.size());
        for (int i = 0; i < expectedStatesList.size(); i++) {
            assertEquals("Expected states: " + expectedStatesList + ", but was " + actualStates,
                    expectedStates[i], actualStates.get(i).getState());
        }
    }

    private LocationTimeZoneManagerServiceStateProto dumpServiceState() throws Exception {
        byte[] protoBytes = mLocationTimeZoneManagerShellHelper.dumpState();
        Parser<LocationTimeZoneManagerServiceStateProto> parser =
                LocationTimeZoneManagerServiceStateProto.parser();
        return parser.parseFrom(protoBytes);
    }

    /**
     * Method used to report success when it shouldn't matter whether newer APIs that include status
     * or older APIs that don't are used. The status provided for newer APIs is a generic "success"
     * status.
     */
    private void reportSuccess(FakeTimeZoneProviderShellHelper providerShellHelper, String zoneId,
            boolean useLegacyApi) throws Exception {
        if (useLegacyApi) {
            providerShellHelper.reportSuccessLegacy(zoneId);
        } else {
            providerShellHelper.reportSuccess(
                    zoneId, DEPENDENCY_STATUS_OK, DEPENDENCY_STATUS_OK);
        }
    }

    /**
     * Method used to report uncertainty when it shouldn't matter whether newer APIs that include
     * status or older APIs that don't are used. The status provided for newer APIs is a generic
     * "uncertain" status that doesn't trigger any interesting behavior.
     */
    private void reportUncertain(FakeTimeZoneProviderShellHelper providerShellHelper,
            boolean useLegacyApis) throws Exception {
        if (useLegacyApis) {
            providerShellHelper.reportUncertainLegacy();
        } else {
            providerShellHelper.reportUncertain(
                    DEPENDENCY_STATUS_TEMPORARILY_UNAVAILABLE, DEPENDENCY_STATUS_OK,
                    OPERATION_STATUS_OK);
        }
    }
}
