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

package android.wifi.mockwifi;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Connects Wifi Framework to MockWifiModemService. */
public class MockWifiModemServiceConnector {

    private static final String TAG = "MockWifiModemServiceConnector";

    private static final String COMMAND_BASE = "cmd wifi ";
    private static final String COMMAND_SET_MODEM_SERVICE = "set-mock-wifimodem-service ";
    private static final String COMMAND_GET_MODEM_SERVICE = "get-mock-wifimodem-service ";
    private static final String COMMAND_SERVICE_IDENTIFIER = "-s ";
    private static final String COMMAND_GET_MOCK_WIFI_MODEM_SERVICE_FROM_FRAMEWORK =
            COMMAND_BASE + COMMAND_GET_MODEM_SERVICE;
    private static final String COMMAND_CLEAR_MOCK_WIFI_MODEM_SERVICE_IN_FRAMEWORK =
            COMMAND_BASE + COMMAND_SET_MODEM_SERVICE;

    private static final int BIND_LOCAL_MOCKMODEM_SERVICE_TIMEOUT_MS = 5000;
    private static final int BIND_WIFI_INTERFACE_READY_TIMEOUT_MS = 5000;

    private Instrumentation mInstrumentation;
    private MockWifiModemService mMockWifiModemService;
    private MockWifiModemServiceConnection mMockWifiModemServiceConn;
    private boolean mFrameworkIsBoundToService;
    private String mModemServiceName;

    private class MockWifiModemServiceConnection implements ServiceConnection {

        private final CountDownLatch mLatch;

        MockWifiModemServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMockWifiModemService = ((MockWifiModemService.LocalBinder) service).getService();
            String serviceName = name.getPackageName() + "/" + name.getClassName();
            updateModemServiceName(serviceName);
            mLatch.countDown();
            Log.d(TAG, "MockWifiModemServiceConnection - " + serviceName + " onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMockWifiModemService = null;
            Log.d(TAG, "MockWifiModemServiceConnection - onServiceDisconnected");
        }
    }

    public MockWifiModemServiceConnector(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    private boolean isReturnResultTrue(String result) {
        return "true".equals(result);
    }

    private boolean setupLocalMockWifiModemService() {
        Log.d(TAG, "setupLocalMockWifiModemService");
        if (mMockWifiModemService != null) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        if (mMockWifiModemServiceConn == null) {
            mMockWifiModemServiceConn = new MockWifiModemServiceConnection(latch);
        }

        mInstrumentation
                .getContext()
                .bindService(
                        new Intent(mInstrumentation.getContext(), MockWifiModemService.class),
                        mMockWifiModemServiceConn,
                        Context.BIND_AUTO_CREATE);
        try {
            return latch.await(BIND_LOCAL_MOCKMODEM_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    private String constructSetMockWifiModemServiceCommand() {
        return COMMAND_BASE
                + COMMAND_SET_MODEM_SERVICE
                + COMMAND_SERVICE_IDENTIFIER
                + mModemServiceName;
    }

    private boolean setMockWifiModemService() throws Exception {
        String result =
                SystemUtil.runShellCommand(mInstrumentation,
                        constructSetMockWifiModemServiceCommand());
        Log.d(TAG, "setMockWifiModemService result: |" + result + "|");
        if (isReturnResultTrue(result)) {
            mFrameworkIsBoundToService = true;
            return true;
        }
        return false;
    }

    private String getMockWifiModemServiceNameFromFramework() throws Exception {
        String serviceName =
                SystemUtil.runShellCommand(
                        mInstrumentation, COMMAND_GET_MOCK_WIFI_MODEM_SERVICE_FROM_FRAMEWORK);
        Log.d(TAG, "getMockWifiModemServiceNameFromFramework : " + serviceName);
        return serviceName;
    }

    private boolean isServiceTheSame(String serviceA, String serviceB) {
        // An empty service name is same as null service.
        if (TextUtils.isEmpty(serviceA) && TextUtils.isEmpty(serviceB)) {
            return true;
        }
        return TextUtils.equals(serviceA, serviceB);
    }

    private void updateModemServiceName(String serviceName) {
        mModemServiceName = serviceName;
    }

    /**
     * Bind to the local implementation of MockWifiModemService.
     *
     * @return true if this request succeeded, false otherwise.
     */
    private boolean connectMockWifiModemServiceLocally() {
        if (!setupLocalMockWifiModemService()) {
            Log.w(TAG, "connectMockWifiModemServiceLocally: couldn't set up service.");
            return false;
        }
        return true;
    }

    private boolean switchFrameworkConnectionToMockWifiModemService() throws Exception {
        boolean isComplete = false;

        if (setMockWifiModemService()) {
            isComplete =
                    mMockWifiModemService.waitForLatchCountdown(
                            MockWifiModemService.LATCH_WIFI_INTERFACES_READY,
                            BIND_WIFI_INTERFACE_READY_TIMEOUT_MS);
        }
        Log.i(TAG, "switchFrameworkConnectionToMockWifiModemService result is " + isComplete);
        return isComplete;
    }

    private boolean checkMockWifiModemService(String serviceName) throws Exception {
        return isServiceTheSame(mModemServiceName, serviceName);
    }


    /**
     * Trigger the wifi framework to bind to the MockWifiModemService.
     *
     * @return true if this request succeeded, false otherwise.
     */
    public boolean connectMockWifiModemService() throws Exception {
        if (!connectMockWifiModemServiceLocally()) {
            Log.e(TAG, "fail to connect to mock wifi modem service locally");
            return false;
        }

        boolean result = checkMockWifiModemService(getMockWifiModemServiceNameFromFramework());
        if (result) {
            mFrameworkIsBoundToService = true;
        } else {
            result = switchFrameworkConnectionToMockWifiModemService();
        }

        return result;
    }

    /**
     * Trigger the wifi framework to unbind to the MockWifiModemService.
     *
     * @return true if this request succeeded, false otherwise.
     */
    public boolean disconnectMockWifiModemService() throws Exception {
        boolean isComplete = triggerFrameworkDisconnectionFromMockWifiModemService();

        // Remove local connection
        Log.d(TAG, "disconnectMockWifiModemService" + isComplete);
        if (mMockWifiModemServiceConn != null) {
            mInstrumentation.getContext().unbindService(mMockWifiModemServiceConn);
            mMockWifiModemService = null;
        }

        return isComplete;
    }

    private boolean triggerFrameworkDisconnectionFromMockWifiModemService() throws Exception {
        if (!mFrameworkIsBoundToService) {
            Log.d(TAG, "Service didn't override.");
            return true;
        }

        boolean result = clearMockWifiModemServiceOverride();
        if (result) mFrameworkIsBoundToService = false;

        return result;
    }

    private boolean clearMockWifiModemServiceOverride() throws Exception {
        String result =
                SystemUtil.runShellCommand(mInstrumentation,
                        COMMAND_CLEAR_MOCK_WIFI_MODEM_SERVICE_IN_FRAMEWORK);
        return isReturnResultTrue(result);
    }


    public MockWifiModemService getMockWifiModemService() {
        return mMockWifiModemService;
    }
}
