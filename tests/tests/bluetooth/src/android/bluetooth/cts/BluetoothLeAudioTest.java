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

package android.bluetooth.cts;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;

import static org.junit.Assert.assertThrows;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothLeAudio;
import android.bluetooth.BluetoothLeAudioCodecStatus;
import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiLevelUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothLeAudioTest extends AndroidTestCase {
    private static final String TAG = BluetoothLeAudioTest.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect

    private boolean mHasBluetooth;
    private BluetoothAdapter mAdapter;

    private BluetoothLeAudio mBluetoothLeAudio;
    private boolean mIsLeAudioSupported;
    private boolean mIsProfileReady;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;
    private Executor mTestExecutor;
    private TestCallback mTestCallback;
    private boolean mCodecConfigChangedCalled;
    private boolean mGroupNodeAddedCalled;
    private boolean mGroupNodeRemovedCalled;
    private boolean mGroupStatusChangedCalled;
    private BluetoothDevice mTestDevice;
    private int mTestGroupId;
    private int mTestGroupStatus;

    private static final BluetoothLeAudioCodecConfig LC3_16KHZ_CONFIG =
        new BluetoothLeAudioCodecConfig.Builder()
            .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
            .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000)
            .build();

    private static final List<BluetoothLeAudioCodecConfig> mCapabilitiesConfig =
        new ArrayList() {{
                add(LC3_16KHZ_CONFIG);
        }};

    private static final BluetoothLeAudioCodecStatus mTestStatus =
        new BluetoothLeAudioCodecStatus(LC3_16KHZ_CONFIG, LC3_16KHZ_CONFIG,
                mCapabilitiesConfig, mCapabilitiesConfig,
                mCapabilitiesConfig, mCapabilitiesConfig);

    class TestCallback implements BluetoothLeAudio.Callback {
        @Override
        public void onCodecConfigChanged(int groupId,
                                         BluetoothLeAudioCodecStatus status) {
            mCodecConfigChangedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(status == mTestStatus);
        }
        @Override
        public void onGroupNodeAdded(BluetoothDevice device, int groupId) {
            mGroupNodeAddedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(device == mTestDevice);
        }
        @Override
        public void onGroupNodeRemoved(BluetoothDevice device, int groupId) {
            mGroupNodeRemovedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(device == mTestDevice);
        }
        @Override
        public void onGroupStatusChanged(int groupId, int groupStatus) {
            mGroupStatusChangedCalled = true;
            assertTrue(groupId == mTestGroupId);
            assertTrue(groupStatus == mTestGroupStatus);
        }
    };


    @Override
    public void setUp() throws Exception {
        super.setUp();
        if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.TIRAMISU)) {
            mHasBluetooth = getContext().getPackageManager().hasSystemFeature(
                    PackageManager.FEATURE_BLUETOOTH);

            if (!mHasBluetooth) return;
            TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);

            BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
            mAdapter = manager.getAdapter();
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

            mProfileConnectedlock = new ReentrantLock();
            mConditionProfileIsConnected  = mProfileConnectedlock.newCondition();
            mIsProfileReady = false;
            mBluetoothLeAudio = null;

            mIsLeAudioSupported =  TestUtils.getProfileConfigValueOrDie(BluetoothProfile.LE_AUDIO);
            if (!mIsLeAudioSupported) return;

            mAdapter.getProfileProxy(getContext(), new BluetoothLeAudioServiceListener(),
                    BluetoothProfile.LE_AUDIO);

            mTestExecutor = mContext.getMainExecutor();
            mTestCallback = new TestCallback();
        }
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            if (mBluetoothLeAudio != null) {
                mBluetoothLeAudio.close();
                mBluetoothLeAudio = null;
                mIsProfileReady = false;
            }
            if (mAdapter != null) {
                assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
                mAdapter = null;
            }
            TestUtils.dropPermissionAsShellUid();
        }
    }

    public void testGetConnectedDevices() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices = mBluetoothLeAudio.getConnectedDevices();
        assertTrue(connectedDevices.isEmpty());
    }

    public void testGetDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns empty list if bluetooth is not enabled
        List<BluetoothDevice> connectedDevices =
                mBluetoothLeAudio.getDevicesMatchingConnectionStates(null);
        assertTrue(connectedDevices.isEmpty());
    }

    public void testGetConnectionState() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        // Verify returns false when invalid input is given
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeAudio.getConnectionState(null));

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothProfile.STATE_DISCONNECTED,
                mBluetoothLeAudio.getConnectionState(mTestDevice));
    }

    public void testGetAudioLocation() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        // Verify returns false if bluetooth is not enabled
        assertEquals(BluetoothLeAudio.AUDIO_LOCATION_INVALID,
                mBluetoothLeAudio.getAudioLocation(mTestDevice));
    }

    public void test_setgetConnectionPolicy() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertFalse(mBluetoothLeAudio.setConnectionPolicy(null, 0));
        assertEquals(BluetoothProfile.CONNECTION_POLICY_FORBIDDEN,
                mBluetoothLeAudio.getConnectionPolicy(null));
    }

    public void testRegisterCallbackNoPermission() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        TestUtils.dropPermissionAsShellUid();
        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        // Verify throws SecurityException without permission.BLUETOOTH_PRIVILEGED
        assertThrows(SecurityException.class,
            () -> mBluetoothLeAudio.registerCallback(mTestExecutor, mTestCallback));

        TestUtils.adoptPermissionAsShellUid(BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED);
    }

    public void testRegisterUnregisterCallback() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        // Verify parameter
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeAudio.registerCallback(null, mTestCallback));
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeAudio.registerCallback(mTestExecutor, null));
        assertThrows(NullPointerException.class, () ->
                mBluetoothLeAudio.unregisterCallback(null));

        // Test success register unregister
        try {
            mBluetoothLeAudio.registerCallback(mTestExecutor, mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from register(): " + e.toString());
        }

        try {
            mBluetoothLeAudio.unregisterCallback(mTestCallback);
        } catch (Exception e) {
            fail("Exception caught from unregister(): " + e.toString());
        }
    }

    public void testCallback() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        mTestGroupId = 1;
        mTestDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");
        mTestGroupStatus = 0;

        mCodecConfigChangedCalled = false;
        mGroupNodeAddedCalled = false;
        mGroupStatusChangedCalled = false;
        mGroupNodeRemovedCalled = false;

        mTestCallback.onCodecConfigChanged(mTestGroupId, mTestStatus);
        mTestCallback.onGroupNodeAdded(mTestDevice, mTestGroupId);
        mTestCallback.onGroupNodeRemoved(mTestDevice, mTestGroupId);
        mTestCallback.onGroupStatusChanged(mTestGroupId, mTestGroupStatus);

        assertTrue(mCodecConfigChangedCalled);
        assertTrue(mGroupNodeAddedCalled);
        assertTrue(mGroupNodeRemovedCalled);
        assertTrue(mGroupStatusChangedCalled);
    }

    public void testGetConnectedGroupLeadDevice() {
        if (!(mHasBluetooth && mIsLeAudioSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothLeAudio);

        assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));

        int groupId = 1;

        // Verify returns null for unknown group id
        assertEquals(null, mBluetoothLeAudio.getConnectedGroupLeadDevice(groupId));
    }

    private boolean waitForProfileConnect() {
        mProfileConnectedlock.lock();
        try {
            // Wait for the Adapter to be disabled
            while (!mIsProfileReady) {
                if (!mConditionProfileIsConnected.await(
                        PROXY_CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                    // Timeout
                    Log.e(TAG, "Timeout while waiting for Profile Connect");
                    break;
                } // else spurious wakeups
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "waitForProfileConnect: interrrupted");
        } finally {
            mProfileConnectedlock.unlock();
        }
        return mIsProfileReady;
    }

    private final class BluetoothLeAudioServiceListener implements
            BluetoothProfile.ServiceListener {

        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothLeAudio = (BluetoothLeAudio) proxy;
            mIsProfileReady = true;
            try {
                mConditionProfileIsConnected.signal();
            } finally {
                mProfileConnectedlock.unlock();
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
        }
    }
}
