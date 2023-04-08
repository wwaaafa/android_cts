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

package android.telephony.satellite.cts;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.os.Build;
import android.os.CancellationSignal;
import android.os.SystemProperties;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.telephony.satellite.stub.SatelliteError;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class SatelliteManagerTestOnMockService extends SatelliteManagerTestBase {
    private static final String ALLOW_MOCK_MODEM_PROPERTY = "persist.radio.allow_mock_modem";
    private static final boolean DEBUG = !"user".equals(Build.TYPE);
    private static final long TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS = 100;
    private static final long TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS = 60 * 10 * 1000;

    private static MockSatelliteServiceManager sMockSatelliteServiceManager;
    private static boolean sOriginalIsSatelliteEnabled = false;
    private static boolean sOriginalIsSatelliteProvisioned = false;


    @BeforeClass
    public static void beforeAllTests() throws Exception {
        logd("beforeAllTests");

        if (!shouldTestSatellite()) return;

        beforeAllTestsBase();
        enforceMockModemDeveloperSetting();

        sMockSatelliteServiceManager = new MockSatelliteServiceManager(
                InstrumentationRegistry.getInstrumentation());
        assertTrue(sMockSatelliteServiceManager.connectSatelliteService());

        grantSatellitePermission();
        assertTrue(isSatelliteSupported());
        sOriginalIsSatelliteProvisioned = isSatelliteProvisioned();
        if (!sOriginalIsSatelliteProvisioned) {
            logd("Provision satellite");

            SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                    new SatelliteProvisionStateCallbackTest();
            long registerError = sSatelliteManager.registerForSatelliteProvisionStateChanged(
                    getContext().getMainExecutor(), satelliteProvisionStateCallback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

            assertTrue(provisionSatellite());

            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertTrue(satelliteProvisionStateCallback.isProvisioned);
            sSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                    satelliteProvisionStateCallback);
        }

        sOriginalIsSatelliteEnabled = isSatelliteEnabled();
        if (!sOriginalIsSatelliteEnabled) {
            logd("Enable satellite");

            SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();
            long registerResult = sSatelliteManager.registerForSatelliteModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(true);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
            assertTrue(isSatelliteEnabled());
            sSatelliteManager.unregisterForSatelliteModemStateChanged(callback);
        }
        revokeSatellitePermission();
    }

    @AfterClass
    public static void afterAllTests() {
        logd("afterAllTests");

        if (!shouldTestSatellite()) return;

        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);

        grantSatellitePermission();
        logd("Restore enabled state");
        if (isSatelliteEnabled() != sOriginalIsSatelliteEnabled) {
            SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();
            long registerResult = sSatelliteManager.registerForSatelliteModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(sOriginalIsSatelliteEnabled);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(sOriginalIsSatelliteEnabled ? SatelliteManager.SATELLITE_MODEM_STATE_IDLE :
                    SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertTrue(isSatelliteEnabled() == sOriginalIsSatelliteEnabled);
            sSatelliteManager.unregisterForSatelliteModemStateChanged(callback);
        }

        logd("Restore provision state");
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForSatelliteProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);
        if (sOriginalIsSatelliteProvisioned) {
            if (!isSatelliteProvisioned()) {
                assertTrue(provisionSatellite());

                assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                assertTrue(satelliteProvisionStateCallback.isProvisioned);
            }
        } else {
            if (isSatelliteProvisioned()) {
                assertTrue(deprovisionSatellite());

                assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
                assertFalse(satelliteProvisionStateCallback.isProvisioned);
            }
        }
        sSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                satelliteProvisionStateCallback);
        revokeSatellitePermission();

        assertTrue(sMockSatelliteServiceManager.restoreSatelliteServicePackageName());
        sMockSatelliteServiceManager = null;
        afterAllTestsBase();
    }

    @Before
    public void setUp() throws Exception {
        logd("setUp");

        if (!shouldTestSatellite()) return;
        assumeTrue(sMockSatelliteServiceManager != null);
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
    }

    @After
    public void tearDown() {
        logd("tearDown");
    }

    @Test
    public void testProvisionSatelliteService() {
        if (!shouldTestSatellite()) return;

        logd("testProvisionSatelliteService: start");
        grantSatellitePermission();

        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteProvisionStateCallbackTest satelliteProvisionStateCallback =
                new SatelliteProvisionStateCallbackTest();
        long registerError = sSatelliteManager.registerForSatelliteProvisionStateChanged(
                getContext().getMainExecutor(), satelliteProvisionStateCallback);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerError);

        if (isSatelliteProvisioned()) {
            logd("testProvisionSatelliteService: dreprovision");
            assertTrue(deprovisionSatellite());
            assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
            assertFalse(satelliteProvisionStateCallback.isProvisioned);
        }

        logd("testProvisionSatelliteService: provision and cancel");
        satelliteProvisionStateCallback.clearProvisionedStates();
        CancellationSignal cancellationSignal = new CancellationSignal();
        String mText = "This is test provision data.";
        byte[] testProvisionData = mText.getBytes();
        sSatelliteManager.provisionSatelliteService(TOKEN, testProvisionData, cancellationSignal,
                getContext().getMainExecutor(), error::offer);
        cancellationSignal.cancel();

        Integer errorCode;
        try {
            errorCode = error.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testProvisionSatelliteService: Got InterruptedException ex=" + ex);
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, (long) errorCode);

        // Provision succeeded and then got canceled - deprovisioned
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(2));
        assertEquals(2, satelliteProvisionStateCallback.getTotalCountOfProvisionedStates());
        assertTrue(satelliteProvisionStateCallback.getProvisionedState(0));
        assertFalse(satelliteProvisionStateCallback.getProvisionedState(1));
        assertFalse(satelliteProvisionStateCallback.isProvisioned);
        assertFalse(isSatelliteProvisioned());

        logd("testProvisionSatelliteService: restore provision state");
        assertTrue(provisionSatellite());
        assertTrue(satelliteProvisionStateCallback.waitUntilResult(1));
        assertTrue(satelliteProvisionStateCallback.isProvisioned);
        sSatelliteManager.unregisterForSatelliteProvisionStateChanged(
                satelliteProvisionStateCallback);

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteModemStateChanged() {
        if (!shouldTestSatellite()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteStateCallbackTest callback = new SatelliteStateCallbackTest();
        boolean originalEnabledState = isSatelliteEnabled();
        boolean registerCallback = false;
        if (originalEnabledState) {
            registerCallback = true;

            long registerResult = sSatelliteManager.registerForSatelliteModemStateChanged(
                    getContext().getMainExecutor(), callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));

            requestSatelliteEnabled(false);

            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
            assertFalse(isSatelliteEnabled());
        }
        if (!registerCallback) {
            long registerResult = sSatelliteManager
                    .registerForSatelliteModemStateChanged(getContext().getMainExecutor(),
                            callback);
            assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
            assertTrue(callback.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback.modemState);
        }

        requestSatelliteEnabled(true);

        assertTrue(callback.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback.modemState);
        assertTrue(isSatelliteEnabled());

        SatelliteStateCallbackTest
                callback1 = new SatelliteStateCallbackTest();
        long registerResult = sSatelliteManager
                .registerForSatelliteModemStateChanged(getContext().getMainExecutor(), callback1);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, registerResult);
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
        sSatelliteManager.unregisterForSatelliteModemStateChanged(callback);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_TIMEOUT_MILLIS));

        // Verify state transitions: IDLE -> TRANSFERRING -> LISTENING -> IDLE
        sendSatelliteDatagramWithSuccessfulResult(callback1, true);

        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(
                TEST_SATELLITE_LISTENING_FOREVER_TIMEOUT_MILLIS));

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> LISTENING
        receiveSatelliteDatagramWithSuccessfulResult(callback1);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        sendSatelliteDatagramWithFailedResult(callback1);

        // Move to LISTENING state
        sendSatelliteDatagramWithSuccessfulResult(callback1, false);

        // Verify state transitions: LISTENING -> TRANSFERRING -> IDLE
        receiveSatelliteDatagramWithFailedResult(callback1);

        requestSatelliteEnabled(false);

        assertFalse(callback.waitUntilResult(1));
        assertTrue(callback1.waitUntilResult(1));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_OFF, callback1.modemState);
        assertFalse(isSatelliteEnabled());

        if (originalEnabledState) {
            // Restore original modem enabled state.
            requestSatelliteEnabled(true);

            assertTrue(callback1.waitUntilResult(1));
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE, callback1.modemState);
            assertTrue(isSatelliteEnabled());
        }
        sSatelliteManager.unregisterForSatelliteModemStateChanged(callback1);
        assertTrue(sMockSatelliteServiceManager.setSatelliteListeningTimeoutDuration(0));

        revokeSatellitePermission();
    }

    @Test
    public void testSatelliteDatagramReceivedAck() {
        if (!shouldTestSatellite()) return;

        grantSatellitePermission();

        assertTrue(isSatelliteProvisioned());

        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);

        assertTrue(satelliteDatagramCallback.waitUntilResult(1));
        assertArrayEquals(satelliteDatagramCallback.mDatagram.getSatelliteDatagram(),
                receivedText.getBytes());

        revokeSatellitePermission();
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithSuccessfulResult(
            SatelliteStateCallbackTest callback, boolean verifyListenToIdleTransition) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithSuccessfulResult: wrong modem state="
                    + callback.modemState);
            return;
        }

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendSatelliteDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_ERROR_NONE, (long) errorCode);

        /**
         * Modem state should have the following transitions:
         * 1) IDLE to TRANSFERRING.
         * 2) TRANSFERRING to LISTENING.
         * 3) LISTENING to IDLE.
         *
         * When verifyListenToIdleTransition is true, we expect the above 3 state transitions.
         * Otherwise, we expect only the first 2 transitions since satellite is still in LISTENING
         * state (timeout duration is long when verifyListenToIdleTransition is false).
         */
        int expectedNumberOfEvents = verifyListenToIdleTransition ? 3 : 2;
        assertTrue(callback.waitUntilResult(expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(1));

        /**
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * When verifyListenToIdleTransition is true, we expect satellite entering and then exiting
         * LISTENING state. Otherwise, we expect satellite entering and staying at LISTENING state.
         */
        expectedNumberOfEvents = verifyListenToIdleTransition ? 2 : 1;
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(
                expectedNumberOfEvents));
        assertEquals(expectedNumberOfEvents,
                sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));

        if (verifyListenToIdleTransition) {
            assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                    callback.getModemState(2));
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(1));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING or IDLE
     * state.
     */
    private void sendSatelliteDatagramWithFailedResult(SatelliteStateCallbackTest callback) {
        if (callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_LISTENING
                && callback.modemState != SatelliteManager.SATELLITE_MODEM_STATE_IDLE) {
            fail("sendSatelliteDatagramWithFailedResult: wrong modem state=" + callback.modemState);
            return;
        }
        boolean isFirstStateListening =
                (callback.modemState == SatelliteManager.SATELLITE_MODEM_STATE_LISTENING);

        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);
        String mText = "This is a test datagram message from user";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        sMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sSatelliteManager.sendSatelliteDatagram(
                SatelliteManager.DATAGRAM_TYPE_SOS_MESSAGE, datagram, true,
                getContext().getMainExecutor(), resultListener::offer);

        Integer errorCode;
        try {
            errorCode = resultListener.poll(TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            fail("testSatelliteModemStateChanged: Got InterruptedException in waiting"
                    + " for the sendSatelliteDatagram result code");
            return;
        }
        assertNotNull(errorCode);
        assertEquals(SatelliteManager.SATELLITE_ERROR, (long) errorCode);
        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        if (isFirstStateListening) {
            assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
            assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
            assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));
        }
        sMockSatelliteServiceManager.clearListeningEnabledList();
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithSuccessfulResult(
            SatelliteStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForSatelliteDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        String receivedText = "This is a test datagram message from satellite";
        android.telephony.satellite.stub.SatelliteDatagram receivedDatagram =
                new android.telephony.satellite.stub.SatelliteDatagram();
        receivedDatagram.data = receivedText.getBytes();

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.sendOnSatelliteDatagramReceived(receivedDatagram, 0);
        assertTrue(callback.waitUntilResult(1));
        assertEquals(1, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING,
                callback.getModemState(0));

        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertTrue(sMockSatelliteServiceManager.getListeningEnabled(0));
        sMockSatelliteServiceManager.clearListeningEnabledList();

        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
    }

    /**
     * Before calling this function, caller need to make sure the modem is in LISTENING state.
     */
    private void receiveSatelliteDatagramWithFailedResult(
            SatelliteStateCallbackTest callback) {
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_LISTENING, callback.modemState);

        // TODO (b/275086547): remove the below registerForSatelliteDatagram command when the bug
        // is resolved.
        SatelliteDatagramCallbackTest satelliteDatagramCallback =
                new SatelliteDatagramCallbackTest();
        sSatelliteManager.registerForSatelliteDatagram(
                getContext().getMainExecutor(), satelliteDatagramCallback);

        sMockSatelliteServiceManager.clearListeningEnabledList();
        callback.clearModemStates();
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.SATELLITE_ERROR);
        sMockSatelliteServiceManager.sendOnPendingDatagrams();
        assertTrue(sMockSatelliteServiceManager.waitForEventOnPollPendingSatelliteDatagrams(1));

        assertTrue(callback.waitUntilResult(2));
        assertEquals(2, callback.getTotalCountOfModemStates());
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_DATAGRAM_TRANSFERRING,
                callback.getModemState(0));
        assertEquals(SatelliteManager.SATELLITE_MODEM_STATE_IDLE,
                callback.getModemState(1));

        /**
         * On entering LISTENING state, we expect one event of EventOnSatelliteListeningEnabled with
         * value true. On exiting LISTENING state, we expect one event of
         * EventOnSatelliteListeningEnabled with value false.
         *
         * At the beginning of this function, satellite is in LISTENING state. It then transitions
         * to TRANSFERRING state. Thus, we expect one event of EventOnSatelliteListeningEnabled with
         * value false.
         */
        assertTrue(sMockSatelliteServiceManager.waitForEventOnSatelliteListeningEnabled(1));
        assertEquals(1, sMockSatelliteServiceManager.getTotalCountOfListeningEnabledList());
        assertFalse(sMockSatelliteServiceManager.getListeningEnabled(0));

        sMockSatelliteServiceManager.clearListeningEnabledList();
        sSatelliteManager.unregisterForSatelliteDatagram(satelliteDatagramCallback);
        sMockSatelliteServiceManager.setErrorCode(SatelliteError.ERROR_NONE);
    }

    private static void enforceMockModemDeveloperSetting() {
        boolean isAllowed = SystemProperties.getBoolean(ALLOW_MOCK_MODEM_PROPERTY, false);
        // Check for developer settings for user build. Always allow for debug builds
        if (!isAllowed && !DEBUG) {
            throw new IllegalStateException(
                    "!! Enable Mock Modem before running this test !! "
                            + "Developer options => Allow Mock Modem");
        }
    }
}
