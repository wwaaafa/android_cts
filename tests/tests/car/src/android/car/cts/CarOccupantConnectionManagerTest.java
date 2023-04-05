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

package android.car.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNotNull;

import android.car.Car;
import android.car.CarOccupantZoneManager;
import android.car.CarOccupantZoneManager.OccupantZoneInfo;
import android.car.CarRemoteDeviceManager;
import android.car.occupantconnection.AbstractReceiverService;
import android.car.occupantconnection.CarOccupantConnectionManager;
import android.car.occupantconnection.CarOccupantConnectionManager.ConnectionRequestCallback;
import android.car.occupantconnection.Payload;
import android.car.test.ApiCheckerRule;
import android.car.test.mocks.JavaMockitoHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Test relies on other server to connect to.")
public final class CarOccupantConnectionManagerTest extends AbstractCarTestCase {

    private static final String TAG = CarOccupantConnectionManagerTest.class.getSimpleName();
    private static final String RECEIVER_ID = "test_receiver_endpoint_id";

    private static final long BINDING_TIMEOUT_MS = 3_000;
    private static final long WAIT_BEFORE_RESPOND_TO_REQUEST_MS = 2_000;
    private static final long CALLBACK_TIMEOUT_MS = WAIT_BEFORE_RESPOND_TO_REQUEST_MS + 2_000;
    private static final long EXCHANGE_PAYLOAD_TIMEOUT_MS = 10_000;

    private static final Payload PAYLOAD1 = new Payload(HexFormat.of().parseHex("1234"));
    private static final Payload PAYLOAD2 = new Payload(HexFormat.of().parseHex("5678"));

    private final Context mContext = InstrumentationRegistry.getContext();
    private final Executor mExecutor = mContext.getMainExecutor();
    private final TestServiceConnection mServiceConnection = new TestServiceConnection();

    private CarOccupantConnectionManager mOccupantConnectionManager;
    private CarOccupantZoneManager mOccupantZoneManager;
    private CarRemoteDeviceManager mRemoteDeviceManager;
    private TestReceiverService.LocalBinder mBinder;
    private OccupantZoneInfo mMyZone;
    private OccupantZoneInfo mActivePeerZone;

    // TODO(b/242350638): add missing annotations, remove (on child bug of 242350638)
    @Override
    protected void configApiCheckerRule(ApiCheckerRule.Builder builder) {
        Log.w(TAG, "Disabling API requirements check");
        builder.disableAnnotationsCheck();
    }

    @Before
    public void setUp() {
        mOccupantConnectionManager = getCar().getCarManager(CarOccupantConnectionManager.class);
        mRemoteDeviceManager = getCar().getCarManager(CarRemoteDeviceManager.class);
        // CarOccupantConnectionManager is available on multi-display builds only.
        // TODO(b/265091454): annotate the test with @RequireMultipleUsersOnMultipleDisplays.
        assumeNotNull("Skip the test because CarOccupantConnectionManager is not available on"
                + " this build", mOccupantConnectionManager);

        mOccupantZoneManager = getCar().getCarManager(CarOccupantZoneManager.class);
        mMyZone = mOccupantZoneManager.getMyOccupantZone();
        mActivePeerZone = getActivePeerZone();
    }

    @Test
    @ApiTest(apis = {
            "android.car.occupantconnection.CarOccupantConnectionManager#registerReceiver",
            "android.car.occupantconnection.CarOccupantConnectionManager#unregisterReceiver",
            "android.car.occupantconnection.AbstractReceiverService#onReceiverRegistered",
            "android.car.occupantconnection.AbstractReceiverService#onLocalServiceBind"})
    public void testRegisterAndUnregisterReceiver() {
        mOccupantConnectionManager.registerReceiver(RECEIVER_ID, mExecutor,
                (senderZone, payload) -> {
                });
        TestReceiverService receiverService = bindToLocalReceiverServiceAndWait();

        assertWithMessage("Failed to register the receiver").that(
                receiverService.mOnReceiverRegisteredInvokedRecords.contains(RECEIVER_ID)).isTrue();

        mOccupantConnectionManager.unregisterReceiver(RECEIVER_ID);

        assertWithMessage("Failed to unregister the receiver").that(
                receiverService.mOnReceiverRegisteredInvokedRecords.isEmpty()).isTrue();
    }

    @Test
    @ApiTest(apis = {
            "android.car.occupantconnection.CarOccupantConnectionManager#requestConnection",
            "android.car.occupantconnection.CarOccupantConnectionManager#cancelConnection"})
    public void testRequestAndCancelConnection() {
        assumeNotNull("Skip testRequestAndCancelConnection() because there is no active peer"
                        + " occupant zone with a peer app installed",
                mActivePeerZone);

        ConnectionRequestCallback connectionRequestCallback = new ConnectionRequestCallback() {
            @Override
            public void onConnected(@NonNull OccupantZoneInfo receiverZone) {
            }

            @Override
            public void onRejected(@NonNull OccupantZoneInfo receiverZone,
                    int rejectionReason) {
            }

            @Override
            public void onFailed(@NonNull OccupantZoneInfo receiverZone,
                    int connectionError) {
            }

            @Override
            public void onDisconnected(@NonNull OccupantZoneInfo receiverZone) {
            }
        };

        mOccupantConnectionManager.requestConnection(mActivePeerZone, mExecutor,
                connectionRequestCallback);
        // No exception should be thrown.
        mOccupantConnectionManager.cancelConnection(mActivePeerZone);

        mOccupantConnectionManager.requestConnection(mActivePeerZone, mExecutor,
                connectionRequestCallback);
        mOccupantConnectionManager.cancelConnection(mActivePeerZone);
    }

    /**
     * Test:
     * <ul>
     *   <li> The sender requests a connection to the receiver. Then the receiver rejects it.
     *   <li> The sender requests another connection to the receiver. Then the receiver accepts it.
     *   <li> The sender sends PAYLOAD1 to the receiver. Then the receiver verifies PAYLOAD1,
     *        requests two connections to the sender (the first request will be rejected, while
     *        the second one will be accepted), and sends PAYLOAD2 to the sender. Then the sender
     *        verifies PAYLOAD2.
     *   <li> The sender disconnects.
     * </ul>
     */
    @Test
    @ApiTest(apis = {
            "android.car.occupantconnection.CarOccupantConnectionManager#requestConnection",
            "android.car.occupantconnection.CarOccupantConnectionManager#isConnected",
            "android.car.occupantconnection.CarOccupantConnectionManager#sendPayload",
            "android.car.occupantconnection.CarOccupantConnectionManager#disconnect",
            "android.car.occupantconnection.AbstractReceiverService#acceptConnection",
            "android.car.occupantconnection.AbstractReceiverService#rejectConnection",
            "android.car.occupantconnection.AbstractReceiverService#onPayloadReceived"})
    public void testConnectAndSendPayload()
            throws CarOccupantConnectionManager.PayloadTransferException {
        assumeNotNull("Skip testConnectAndSendPayload() because there is no active peer"
                        + " occupant zone with a peer app installed",
                mActivePeerZone);

        TestReceiverService receiverService = bindToLocalReceiverServiceAndWait();

        boolean[] onConnectedInvoked = new boolean[1];
        boolean[] onRejectedInvoked = new boolean[1];
        int[] rejectionReasons = new int[1];
        ConnectionRequestCallback connectionRequestCallback = new ConnectionRequestCallback() {
            @Override
            public void onConnected(@NonNull OccupantZoneInfo receiverZone) {
                onConnectedInvoked[0] = true;
            }

            @Override
            public void onRejected(@NonNull OccupantZoneInfo receiverZone,
                    int rejectionReason) {
                onRejectedInvoked[0] = true;
                rejectionReasons[0] = rejectionReason;
            }

            @Override
            public void onFailed(@NonNull OccupantZoneInfo receiverZone,
                    int connectionError) {
            }

            @Override
            public void onDisconnected(@NonNull OccupantZoneInfo receiverZone) {
            }
        };

        // The receiver service will reject the first request.
        Log.v(TAG, "Sender requests a connection for the first time");
        mOccupantConnectionManager.requestConnection(mActivePeerZone, mExecutor,
                connectionRequestCallback);
        PollingCheck.waitFor(CALLBACK_TIMEOUT_MS,
                () -> !onConnectedInvoked[0] && onRejectedInvoked[0]
                        && rejectionReasons[0] == TestReceiverService.REJECTION_REASON);
        Log.v(TAG, "Sender's first request is rejected");

        // The receiver service will accept the second request.
        Log.v(TAG, "Sender requests another connection");
        onConnectedInvoked[0] = false;
        onRejectedInvoked[0] = false;
        mOccupantConnectionManager.requestConnection(mActivePeerZone, mExecutor,
                connectionRequestCallback);
        PollingCheck.waitFor(CALLBACK_TIMEOUT_MS,
                () -> onConnectedInvoked[0] && !onRejectedInvoked[0]);
        Log.v(TAG, "Sender's second request is accepted");

        assertWithMessage("It should be connected to %s", mActivePeerZone)
                .that(mOccupantConnectionManager.isConnected(mActivePeerZone)).isTrue();

        Log.v(TAG, "Sender sends PAYLOAD1 to the receiver");
        mOccupantConnectionManager.sendPayload(mActivePeerZone, PAYLOAD1);
        Pair<OccupantZoneInfo, Payload> expectedResponse = new Pair<>(mActivePeerZone, PAYLOAD2);
        PollingCheck.waitFor(EXCHANGE_PAYLOAD_TIMEOUT_MS,
                () -> receiverService.mOnPayloadReceivedInvokedRecords.contains(expectedResponse));
        Log.v(TAG, "Sender receives PAYLOAD2 from the receiver");

        mOccupantConnectionManager.disconnect(mActivePeerZone);
        assertWithMessage("Sender should be disconnected to %s", mActivePeerZone)
                .that(mOccupantConnectionManager.isConnected(mActivePeerZone)).isFalse();
    }

    private TestReceiverService bindToLocalReceiverServiceAndWait() {
        Log.v(TAG, "Binding to local receiver service");
        Intent intent = new Intent();
        intent.setClassName(mContext, TestReceiverService.class.getName());
        mContext.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
        try {
            JavaMockitoHelper.await(mServiceConnection.latch, BINDING_TIMEOUT_MS);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        Log.v(TAG, "Local receiver service bounded");
        return mBinder.getService();
    }

    /**
     * Returns a peer occupant zone that has peer app (cts test app) installed, or null if there is
     * no such occupant zone.
     */
    private OccupantZoneInfo getActivePeerZone() {
        if (mRemoteDeviceManager == null) {
            return null;
        }
        for (OccupantZoneInfo zone : mOccupantZoneManager.getAllOccupantZones()) {
            if (!zone.equals(mMyZone) && mRemoteDeviceManager.getEndpointPackageInfo(zone)
                    != null) {
                return zone;
            }
        }
        return null;
    }

    private final class TestServiceConnection implements ServiceConnection {

        public final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBinder = (TestReceiverService.LocalBinder) service;
            latch.countDown();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    /**
     * An implementation of AbstractReceiverService.
     * <p>
     * This service will wait for a while before responding to a connection request (to allow the
     * sender to cancel the request). After that, it will reject the first request, and accept the
     * second request.
     * <p>
     * When this service receives PAYLOAD1 from the sender, it will send two connection requests to
     * the sender's receiver service. The first request wil be rejected, while the second request
     * will be accepted. Once it is accepted, it will send PAYLOAD2 to the sender.
     */
    public static class TestReceiverService extends AbstractReceiverService {

        private static final int REJECTION_REASON = 123;

        // The following lists are used to verify an onFoo() method was invoked with certain
        // parameters.
        private final List<Pair<OccupantZoneInfo, Payload>> mOnPayloadReceivedInvokedRecords =
                new ArrayList<>();
        private final List<String> mOnReceiverRegisteredInvokedRecords = new ArrayList<>();

        private final LocalBinder mLocalBinder = new LocalBinder();

        private Car mCar;
        private CarOccupantConnectionManager mOccupantConnectionManager;
        private Boolean mRejected = Boolean.FALSE;

        private final Car.CarServiceLifecycleListener mCarServiceLifecycleListener =
                (car, ready) -> {
                    if (!ready) {
                        mOccupantConnectionManager = null;
                        return;
                    }
                    mCar = car;
                    mOccupantConnectionManager = car.getCarManager(
                            CarOccupantConnectionManager.class);
                };

        private final ConnectionRequestCallback mConnectionRequestCallback =
                new ConnectionRequestCallback() {
                    @Override
                    public void onConnected(@NonNull OccupantZoneInfo receiverZone) {
                        try {
                            Log.v(TAG, "The sender has accepted the receiver service's second"
                                    + " connection request, so the receiver service sends"
                                    + " PAYLOAD2 to it");
                            mOccupantConnectionManager.sendPayload(receiverZone, PAYLOAD2);
                        } catch (CarOccupantConnectionManager.PayloadTransferException e) {
                            throw new RuntimeException(e);
                        }
                    }

                    @Override
                    public void onRejected(@NonNull OccupantZoneInfo receiverZone,
                            int rejectionReason) {
                        // We foresee that the other receiver service would reject the first
                        // request. This is fine, because it will accept the second request.
                        Log.v(TAG, "Receiver service requests another connection to the"
                                + " sender because the first request was rejected");
                        mOccupantConnectionManager.requestConnection(receiverZone,
                                TestReceiverService.this.getMainExecutor(),
                                mConnectionRequestCallback);
                    }

                    @Override
                    public void onFailed(@NonNull OccupantZoneInfo receiverZone,
                            int connectionError) {
                    }

                    @Override
                    public void onDisconnected(@NonNull OccupantZoneInfo receiverZone) {
                    }
                };

        private class LocalBinder extends Binder {

            TestReceiverService getService() {
                return TestReceiverService.this;
            }
        }

        @Override
        public void onPayloadReceived(OccupantZoneInfo senderZone, Payload payload) {
            mOnPayloadReceivedInvokedRecords.add(new Pair(senderZone, payload));
            PollingCheck.waitFor(CALLBACK_TIMEOUT_MS, () -> mOccupantConnectionManager != null);

            if (PAYLOAD1.equals(payload)) {
                // Let the receiver service request a connection to the sender so that it can
                // send PAYLOAD2 to the sender.
                Log.v(TAG, "Receiver service receives PAYLOAD1, then requests a connection to the"
                        + " sender for the first time");
                mOccupantConnectionManager.requestConnection(senderZone,
                        TestReceiverService.this.getMainExecutor(), mConnectionRequestCallback);
            }
        }

        @Override
        public void onReceiverRegistered(String receiverEndpointId) {
            mOnReceiverRegisteredInvokedRecords.add(receiverEndpointId);
        }

        @Override
        public void onConnectionInitiated(OccupantZoneInfo senderZone, int senderAppState) {
            // Wait a while to allow some time for the sender to cancel the request.
            try {
                Thread.sleep(WAIT_BEFORE_RESPOND_TO_REQUEST_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            synchronized (mRejected) {
                // If the sender didn't cancel the request, reject the first request, and accept
                // the second request.
                if (!mRejected) {
                    rejectConnection(senderZone, REJECTION_REASON);
                    mRejected = true;
                } else {
                    acceptConnection(senderZone);
                }
            }
        }

        @Override
        public void onConnected(OccupantZoneInfo senderZone) {
        }

        @Override
        public void onConnectionCanceled(OccupantZoneInfo senderZone) {
        }

        @Override
        public void onDisconnected(OccupantZoneInfo senderZone) {
        }

        @Override
        public void onCreate() {
            super.onCreate();
            mCar = Car.createCar(this, /* handler= */ null,
                    Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER, mCarServiceLifecycleListener);
        }

        @Override
        public void onDestroy() {
            if (mCar != null && mCar.isConnected()) {
                mCar.disconnect();
                mCar = null;
            }
            super.onDestroy();
        }

        @Nullable
        @Override
        public IBinder onLocalServiceBind(@NonNull Intent intent) {
            return mLocalBinder;
        }
    }
}
