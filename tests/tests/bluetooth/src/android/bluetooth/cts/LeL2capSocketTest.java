/*
 * Copyright 2020 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

@RunWith(AndroidJUnit4.class)
public class LeL2capSocketTest {

    private static final int NUM_ITERATIONS_FOR_REPEATED_TEST = 100;

    private Context mContext;

    private BluetoothAdapter mAdapter = null;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        if (!TestUtils.isBleSupported(mContext)) {
            return;
        }
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .adoptShellPermissionIdentity(android.Manifest.permission.BLUETOOTH_CONNECT);
        mAdapter = BluetoothAdapter.getDefaultAdapter();
        assertNotNull("BluetoothAdapter.getDefaultAdapter() returned null. "
                + "Does this device have a Bluetooth adapter?", mAdapter);
        if (!mAdapter.isEnabled()) {
            assertTrue(BTAdapterUtils.enableAdapter(mAdapter, mContext));
        }
    }

    @After
    public void tearDown() throws Exception {
        if (!TestUtils.isBleSupported(mContext)) {
            return;
        }
        if (mAdapter != null) {
            assertTrue(BTAdapterUtils.disableAdapter(mAdapter, mContext));
        }
        mAdapter = null;
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
            .dropShellPermissionIdentity();
    }


    @SmallTest
    @Test
    public void testOpenInsecureLeL2capServerSocketOnce() {
        if (!TestUtils.isBleSupported(mContext)) {
            return;
        }
        assertTrue("Bluetooth is not enabled", mAdapter.isEnabled());
        try {
            final BluetoothServerSocket serverSocket = mAdapter.listenUsingInsecureL2capChannel();
            assertNotNull("Failed to get server socket", serverSocket);
            serverSocket.close();
        } catch (IOException exp) {
            fail("IOException while opening and closing server socket: " + exp);
        }
    }

    @SmallTest
    @Test
    public void testOpenInsecureLeL2capServerSocketRepeatedly() {
        if (!TestUtils.isBleSupported(mContext)) {
            return;
        }
        assertTrue("Bluetooth is not enabled", mAdapter.isEnabled());
        try {
            for (int i = 0; i < NUM_ITERATIONS_FOR_REPEATED_TEST; i++) {
                final BluetoothServerSocket serverSocket =
                        mAdapter.listenUsingInsecureL2capChannel();
                assertNotNull("Failed to get server socket", serverSocket);
                serverSocket.close();
            }
        } catch (IOException exp) {
            fail("IOException while opening and closing server socket: " + exp);
        }
    }

    @SmallTest
    @Test
    public void testOpenSecureLeL2capServerSocketOnce() {
        if (!TestUtils.isBleSupported(mContext)) {
            return;
        }
        assertTrue("Bluetooth is not enabled", mAdapter.isEnabled());
        try {
            final BluetoothServerSocket serverSocket = mAdapter.listenUsingL2capChannel();
            assertNotNull("Failed to get server socket", serverSocket);
            serverSocket.close();
        } catch (IOException exp) {
            fail("IOException while opening and closing server socket: " + exp);
        }
    }

    @SmallTest
    @Test
    public void testOpenSecureLeL2capServerSocketRepeatedly() {
        if (!TestUtils.isBleSupported(mContext)) {
            return;
        }
        assertTrue("Bluetooth is not enabled", mAdapter.isEnabled());
        try {
            for (int i = 0; i < NUM_ITERATIONS_FOR_REPEATED_TEST; i++) {
                final BluetoothServerSocket serverSocket = mAdapter.listenUsingL2capChannel();
                assertNotNull("Failed to get server socket", serverSocket);
                serverSocket.close();
            }
        } catch (IOException exp) {
            fail("IOException while opening and closing server socket: " + exp);
        }
    }
}
