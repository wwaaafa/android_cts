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

package android.telephony.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.OutcomeReceiver;
import android.telephony.satellite.PointingInfo;
import android.telephony.satellite.SatelliteCallback;
import android.telephony.satellite.SatelliteCapabilities;
import android.telephony.satellite.SatelliteDatagram;
import android.telephony.satellite.SatelliteManager;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class SatelliteManagerTest {
    private static final String TAG = "SatelliteManagerTest";

    private static final long TIMEOUT = 1000;

    private SatelliteManager mSatelliteManager;

    @Before
    public void setUp() throws Exception {
        assumeTrue(getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_TELEPHONY_SATELLITE));
        mSatelliteManager = getContext().getSystemService(SatelliteManager.class);
    }

    @Test
    public void testSatellitePositionUpdates() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatellitePositionUpdateListenerTest callback =
                new SatellitePositionUpdateListenerTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.startSatellitePositionUpdates(
                        getContext().getMainExecutor(), error::offer, callback));
    }

    @Test
    public void testRequestMaxCharactersPerSatelliteTextMessage() {
        final AtomicReference<Integer> maxCharacters = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Integer, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Integer result) {
                        maxCharacters.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.requestMaxCharactersPerSatelliteTextMessage(
                        getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testProvisionSatelliteService() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.provisionSatelliteService(
                        "", null, getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testDeprovisionSatelliteService() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.deprovisionSatelliteService(
                        "", getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRegisterForSatelliteProvisionStateChanged() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);
        SatelliteProvisionStateListenerTest satelliteProvisionStateListener =
                new SatelliteProvisionStateListenerTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.registerForSatelliteProvisionStateChanged(
                        getContext().getMainExecutor(), error::offer,
                        satelliteProvisionStateListener));
    }

    @Test
    public void testRequestIsSatelliteProvisioned() {
        final AtomicReference<Boolean> provisioned = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        provisioned.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                () -> mSatelliteManager.requestIsSatelliteProvisioned(
                        getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testSetSatelliteEnabled() {
        LinkedBlockingQueue<Integer> error = new LinkedBlockingQueue<>(1);

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.setSatelliteEnabled(
                true, getContext().getMainExecutor(), error::offer));
        assertThrows(SecurityException.class, () -> mSatelliteManager.setSatelliteEnabled(
                false, getContext().getMainExecutor(), error::offer));
    }

    @Test
    public void testRequestIsSatelliteEnabled() {
        final AtomicReference<Boolean> enabled = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        enabled.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestIsSatelliteEnabled(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testRequestIsSatelliteSupported() throws Exception {
        final AtomicReference<Boolean> supported = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        OutcomeReceiver<Boolean, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(Boolean result) {
                        supported.set(result);
                        latch.countDown();
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                        latch.countDown();
                    }
                };

        mSatelliteManager.requestIsSatelliteSupported(getContext().getMainExecutor(),
                receiver);
        assertTrue(latch.await(TIMEOUT, TimeUnit.MILLISECONDS));

        assertNotNull(errorCode.get());
        int error = errorCode.get();
        if (error == SatelliteManager.SATELLITE_ERROR_NONE) {
            assertNotNull(supported.get());
        } else {
            assertNull(supported.get());
        }
    }

    @Test
    public void testRequestSatelliteCapabilities() {
        final AtomicReference<SatelliteCapabilities> capabilities = new AtomicReference<>();
        final AtomicReference<Integer> errorCode = new AtomicReference<>();
        OutcomeReceiver<SatelliteCapabilities, SatelliteManager.SatelliteException> receiver =
                new OutcomeReceiver<>() {
                    @Override
                    public void onResult(SatelliteCapabilities result) {
                        capabilities.set(result);
                    }

                    @Override
                    public void onError(SatelliteManager.SatelliteException exception) {
                        errorCode.set(exception.getErrorCode());
                    }
                };

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, () -> mSatelliteManager.requestSatelliteCapabilities(
                getContext().getMainExecutor(), receiver));
    }

    @Test
    public void testSatelliteModemStateChanges() throws Exception {
        SatelliteStateListenerTest callback = new SatelliteStateListenerTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .registerForSatelliteModemStateChange(getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .unregisterForSatelliteModemStateChange(callback));
    }

    @Test
    public void testSatelliteDatagramListener() throws Exception {
        SatelliteDatagramListenerTest callback = new SatelliteDatagramListenerTest();

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .registerForSatelliteDatagram(SatelliteManager.DATAGRAM_TYPE_SOS_SMS,
                        getContext().getMainExecutor(), callback));

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class, ()-> mSatelliteManager
                .unregisterForSatelliteDatagram(callback));
    }

    @Test
    public void testPollPendingSatelliteDatagrams() throws Exception {
        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.pollPendingSatelliteDatagrams());
    }

    @Test
    public void testSendSatelliteDatagram() {
        LinkedBlockingQueue<Integer> resultListener = new LinkedBlockingQueue<>(1);

        String mText = "This is a test datagram message";
        SatelliteDatagram datagram = new SatelliteDatagram(mText.getBytes());

        // Throws SecurityException as we do not have SATELLITE_COMMUNICATION permission.
        assertThrows(SecurityException.class,
                ()-> mSatelliteManager.sendSatelliteDatagram(
                        SatelliteManager.DATAGRAM_TYPE_SOS_SMS, datagram,
                        getContext().getMainExecutor(), resultListener::offer));
    }

    private Context getContext() {
        return InstrumentationRegistry.getContext();
    }

    private void grantSatellitePermission() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.SATELLITE_COMMUNICATION);
    }

    private static class SatellitePositionUpdateListenerTest extends SatelliteCallback
            implements SatelliteCallback.SatellitePositionUpdateListener {
        @Override
        public void onSatellitePositionUpdate(PointingInfo pointingInfo) {
            Log.d(TAG, "onSatellitePositionUpdate: pointingInfo=" + pointingInfo);
        }

        @Override
        public void onMessageTransferStateUpdate(int state) {
            Log.d(TAG, "onMessageTransferStateUpdate: state=" + state);
        }
    }

    private static class SatelliteProvisionStateListenerTest extends SatelliteCallback
            implements SatelliteCallback.SatelliteProvisionStateListener {
        @Override
        public void onSatelliteProvisionStateChanged(boolean provisioned) {
            Log.d(TAG, "onSatelliteProvisionStateChanged: features="
                    + ", provisioned=" + provisioned);
        }
    }

    private static class SatelliteStateListenerTest extends SatelliteCallback
            implements SatelliteCallback.SatelliteStateListener {

        @Override
        public void onSatelliteModemStateChange(int state) {
            Log.d(TAG, "onSatelliteModemStateChange: state=" + state);
        }

        @Override
        public void onPendingMessageCount(int count) {
            Log.d(TAG, "onPendingMessageCount: count=" + count);
        }
    }

    private static class SatelliteDatagramListenerTest extends SatelliteCallback
            implements SatelliteCallback.SatelliteDatagramListener {

        @Override
        public void onSatelliteDatagrams(SatelliteDatagram[] datagrams) {
            Log.d(TAG, "onSatelliteDatagram");
        }
    }
}
