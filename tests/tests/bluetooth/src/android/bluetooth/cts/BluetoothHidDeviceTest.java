/*
 * Copyright (C) 2022 The Android Open Source Project
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

/* You may obtain a copy of the License at
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

import static org.junit.Assert.assertThrows;

import android.app.UiAutomation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.test.AndroidTestCase;
import android.util.Log;

import androidx.test.InstrumentationRegistry;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BluetoothHidDeviceTest extends AndroidTestCase {
    private static final String TAG = BluetoothHidDevice.class.getSimpleName();

    private static final int PROXY_CONNECTION_TIMEOUT_MS = 500;  // ms timeout for Proxy Connect
    private static final String PROFILE_SUPPORTED_HID_DEVICE = "profile_supported_hid_device";

    private boolean mHasBluetooth;
    private boolean mIsHidSupported;
    private boolean mIsProfileReady;
    private BluetoothAdapter mAdapter;
    private UiAutomation mUiAutomation;
    private Condition mConditionProfileIsConnected;
    private ReentrantLock mProfileConnectedlock;
    private BluetoothHidDeviceAppSdpSettings mSettings;
    private ExecutorService mExecutor;
    private BluetoothHidDevice mBluetoothHidDevice;


    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHasBluetooth =
                getContext().getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
        if (!mHasBluetooth) return;
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);

        BluetoothManager manager = getContext().getSystemService(BluetoothManager.class);
        mAdapter = manager.getAdapter();
        assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));

        mProfileConnectedlock = new ReentrantLock();
        mConditionProfileIsConnected = mProfileConnectedlock.newCondition();
        mIsProfileReady = false;
        mBluetoothHidDevice = null;
        mExecutor = Executors.newSingleThreadExecutor();

        Resources bluetoothResources = mContext.getPackageManager().getResourcesForApplication(
                "com.android.bluetooth.services");
        int hidSupportId = bluetoothResources.getIdentifier(PROFILE_SUPPORTED_HID_DEVICE, "bool",
                "com.android.bluetooth.services");
        mIsHidSupported = bluetoothResources.getBoolean(hidSupportId);
        if (!mIsHidSupported) return;

        mAdapter.getProfileProxy(getContext(), new BluetoothHidServiceListener(),
                BluetoothProfile.HID_DEVICE);
    }

    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (mHasBluetooth) {
            if (mAdapter != null && mBluetoothHidDevice != null) {
                mAdapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, mBluetoothHidDevice);
                mBluetoothHidDevice = null;
                mIsProfileReady = false;
            }
            mUiAutomation.adoptShellPermissionIdentity(BLUETOOTH_CONNECT);
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
            mAdapter = null;
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    public void test_getDevicesMatchingConnectionStates() {
        if (!(mHasBluetooth && mIsHidSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        assertEquals(mBluetoothHidDevice.getDevicesMatchingConnectionStates(
                        new int[]{BluetoothProfile.STATE_CONNECTED}),
                new ArrayList<BluetoothDevice>()
        );
    }

    public void test_getConnectionState() {
        if (!(mHasBluetooth && mIsHidSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertEquals(mBluetoothHidDevice.getConnectionState(testDevice),
                BluetoothProfile.STATE_DISCONNECTED);
    }

    public void test_connect() {
        if (!(mHasBluetooth && mIsHidSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHidDevice.connect(testDevice));
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothHidDevice.connect(testDevice));
    }

    public void test_disconnect() {
        if (!(mHasBluetooth && mIsHidSupported)) return;

        assertTrue(waitForProfileConnect());
        assertNotNull(mBluetoothHidDevice);

        BluetoothDevice testDevice = mAdapter.getRemoteDevice("00:11:22:AA:BB:CC");

        assertFalse(mBluetoothHidDevice.disconnect(testDevice));
        mUiAutomation.dropShellPermissionIdentity();
        assertThrows(SecurityException.class, () -> mBluetoothHidDevice.connect(testDevice));
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

    private final class BluetoothHidServiceListener implements
            BluetoothProfile.ServiceListener {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            mProfileConnectedlock.lock();
            mBluetoothHidDevice = (BluetoothHidDevice) proxy;
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
