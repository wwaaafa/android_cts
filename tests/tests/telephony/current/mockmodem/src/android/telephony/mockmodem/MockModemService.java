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

package android.telephony.mockmodem;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.radio.RadioError;
import android.hardware.radio.RadioResponseInfo;
import android.hardware.radio.RadioResponseType;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MockModemService extends Service {
    private static final String TAG = "MockModemService";
    private static final String RESOURCE_PACKAGE_NAME = "android";

    public static final int TEST_TIMEOUT_MS = 30000;
    public static final String IRADIOCONFIG_INTERFACE = "android.telephony.mockmodem.iradioconfig";
    public static final String IRADIOMODEM_INTERFACE = "android.telephony.mockmodem.iradiomodem";
    public static final String IRADIOSIM_INTERFACE = "android.telephony.mockmodem.iradiosim";
    public static final String IRADIONETWORK_INTERFACE =
            "android.telephony.mockmodem.iradionetwork";
    public static final String IRADIODATA_INTERFACE = "android.telephony.mockmodem.iradiodata";
    public static final String IRADIOMESSAGING_INTERFACE =
            "android.telephony.mockmodem.iradiomessaging";
    public static final String IRADIOVOICE_INTERFACE = "android.telephony.mockmodem.iradiovoice";
    public static final String PHONE_ID = "phone_id";

    private static Context sContext;
    private static MockModemConfigInterface[] sMockModemConfigInterfaces;
    private static IRadioConfigImpl sIRadioConfigImpl;
    private static IRadioModemImpl[] sIRadioModemImpl;
    private static IRadioSimImpl[] sIRadioSimImpl;
    private static IRadioNetworkImpl[] sIRadioNetworkImpl;
    private static IRadioDataImpl[] sIRadioDataImpl;
    private static IRadioMessagingImpl[] sIRadioMessagingImpl;
    private static IRadioVoiceImpl[] sIRadioVoiceImpl;

    public static final byte PHONE_ID_0 = 0x00;
    public static final byte PHONE_ID_1 = 0x01;

    public static final int LATCH_MOCK_MODEM_SERVICE_READY = 0;
    public static final int LATCH_RADIO_INTERFACES_READY = 1;
    public static final int LATCH_MAX = 2;

    private static final int IRADIO_CONFIG_INTERFACE_NUMBER = 1;
    private static final int IRADIO_INTERFACE_NUMBER = 6;

    private TelephonyManager mTelephonyManager;
    private int mNumOfSim;
    private int mNumOfPhone;
    private static final int DEFAULT_SUB_ID = 0;

    private Object mLock;
    protected static CountDownLatch[] sLatches;
    private LocalBinder mBinder;

    // For local access of this Service.
    class LocalBinder extends Binder {
        MockModemService getService() {
            return MockModemService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Mock Modem Service Created");

        sContext = InstrumentationRegistry.getInstrumentation().getContext();
        mTelephonyManager = sContext.getSystemService(TelephonyManager.class);
        mNumOfSim = getNumPhysicalSlots();
        mNumOfPhone = mTelephonyManager.getActiveModemCount();
        Log.d(TAG, "Support number of phone = " + mNumOfPhone + ", number of SIM = " + mNumOfSim);

        mLock = new Object();

        sLatches = new CountDownLatch[LATCH_MAX];
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
            if (i == LATCH_RADIO_INTERFACES_READY) {
                int radioInterfaceNumber =
                        IRADIO_CONFIG_INTERFACE_NUMBER + mNumOfPhone * IRADIO_INTERFACE_NUMBER;
                sLatches[i] = new CountDownLatch(radioInterfaceNumber);
            } else {
                sLatches[i] = new CountDownLatch(1);
            }
        }

        sMockModemConfigInterfaces = new MockModemConfigBase[mNumOfPhone];
        for (int i = 0; i < mNumOfPhone; i++) {
            sMockModemConfigInterfaces[i] =
                    new MockModemConfigBase(sContext, i, mNumOfSim, mNumOfPhone);
        }

        sIRadioConfigImpl = new IRadioConfigImpl(this, sMockModemConfigInterfaces, PHONE_ID_0);
        sIRadioModemImpl = new IRadioModemImpl[mNumOfPhone];
        sIRadioSimImpl = new IRadioSimImpl[mNumOfPhone];
        sIRadioNetworkImpl = new IRadioNetworkImpl[mNumOfPhone];
        sIRadioDataImpl = new IRadioDataImpl[mNumOfPhone];
        sIRadioMessagingImpl = new IRadioMessagingImpl[mNumOfPhone];
        sIRadioVoiceImpl = new IRadioVoiceImpl[mNumOfPhone];
        for (int i = 0; i < mNumOfPhone; i++) {
            sIRadioModemImpl[i] = new IRadioModemImpl(this, sMockModemConfigInterfaces, i);
            sIRadioSimImpl[i] = new IRadioSimImpl(this, sMockModemConfigInterfaces, i);
            sIRadioNetworkImpl[i] = new IRadioNetworkImpl(this, sMockModemConfigInterfaces, i);
            sIRadioDataImpl[i] = new IRadioDataImpl(this, sContext, sMockModemConfigInterfaces, i);
            sIRadioMessagingImpl[i] = new IRadioMessagingImpl(this, sMockModemConfigInterfaces, i);
            sIRadioVoiceImpl[i] = new IRadioVoiceImpl(this, sMockModemConfigInterfaces, i);
        }

        mBinder = new LocalBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {

        String action = intent.getAction();
        if (action == null) {
            countDownLatch(LATCH_MOCK_MODEM_SERVICE_READY);
            Log.i(TAG, "onBind-Local");
            return mBinder;
        }

        byte phoneId = intent.getByteExtra(PHONE_ID, PHONE_ID_0);

        if (phoneId > PHONE_ID_1) {
            Log.e(TAG, "Not suuport for phone " + phoneId);
            return null;
        }

        if (action.startsWith(IRADIOCONFIG_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioConfig " + phoneId);
            return sIRadioConfigImpl;
        } else if (action.startsWith(IRADIOMODEM_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioModem " + phoneId);
            return sIRadioModemImpl[phoneId];
        } else if (action.startsWith(IRADIOSIM_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioSim " + phoneId);
            return sIRadioSimImpl[phoneId];
        } else if (action.startsWith(IRADIONETWORK_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioNetwork " + phoneId);
            return sIRadioNetworkImpl[phoneId];
        } else if (action.startsWith(IRADIODATA_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioData " + phoneId);
            return sIRadioDataImpl[phoneId];
        } else if (action.startsWith(IRADIOMESSAGING_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioMessaging " + phoneId);
            return sIRadioMessagingImpl[phoneId];
        } else if (action.startsWith(IRADIOVOICE_INTERFACE)) {
            Log.i(TAG, "onBind-IRadioVoice " + phoneId);
            return sIRadioVoiceImpl[phoneId];
        }

        return null;
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public int getNumPhysicalSlots() {
        int numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MIN;
        int resourceId =
                sContext.getResources()
                        .getIdentifier(
                                "config_num_physical_slots", "integer", RESOURCE_PACKAGE_NAME);

        if (resourceId > 0) {
            numPhysicalSlots = sContext.getResources().getInteger(resourceId);
        } else {
            Log.d(TAG, "Fail to get the resource Id, using default: " + numPhysicalSlots);
        }

        if (numPhysicalSlots > MockSimService.MOCK_SIM_SLOT_MAX) {
            Log.d(
                    TAG,
                    "Number of physical Slot ("
                            + numPhysicalSlots
                            + ") > mock sim slot support. Reset to max number supported ("
                            + MockSimService.MOCK_SIM_SLOT_MAX
                            + ").");
            numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MAX;
        } else if (numPhysicalSlots <= MockSimService.MOCK_SIM_SLOT_MIN) {
            Log.d(
                    TAG,
                    "Number of physical Slot ("
                            + numPhysicalSlots
                            + ") < mock sim slot support. Reset to min number supported ("
                            + MockSimService.MOCK_SIM_SLOT_MIN
                            + ").");
            numPhysicalSlots = MockSimService.MOCK_SIM_SLOT_MIN;
        }

        return numPhysicalSlots;
    }

    public RadioResponseInfo makeSolRsp(int serial) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = RadioError.NONE;

        return rspInfo;
    }

    public RadioResponseInfo makeSolRsp(int serial, int error) {
        RadioResponseInfo rspInfo = new RadioResponseInfo();
        rspInfo.type = RadioResponseType.SOLICITED;
        rspInfo.serial = serial;
        rspInfo.error = error;

        return rspInfo;
    }

    public boolean initialize(int simprofile) {
        Log.d(TAG, "initialize simprofile = " + simprofile);
        boolean result = true;

        // Sync mock modem status between modules
        for (int i = 0; i < mNumOfPhone; i++) {
            // Set initial SIM profile
            sMockModemConfigInterfaces[i].changeSimProfile(simprofile, TAG);

            // Sync modem configurations to radio modules
            sMockModemConfigInterfaces[i].notifyAllRegistrantNotifications();

            // Connect to telephony framework
            sIRadioModemImpl[i].rilConnected();
        }

        return result;
    }

    public MockModemConfigInterface[] getMockModemConfigInterfaces() {
        return sMockModemConfigInterfaces;
    }

    public IRadioConfigImpl getIRadioConfig() {
        return sIRadioConfigImpl;
    }

    public IRadioModemImpl getIRadioModem() {
        return getIRadioModem(PHONE_ID_0);
    }

    public IRadioModemImpl getIRadioModem(byte phoneId) {
        return sIRadioModemImpl[phoneId];
    }

    public IRadioSimImpl getIRadioSim() {
        return getIRadioSim(PHONE_ID_0);
    }

    public IRadioSimImpl getIRadioSim(byte phoneId) {
        return sIRadioSimImpl[phoneId];
    }

    public IRadioNetworkImpl getIRadioNetwork() {
        return getIRadioNetwork(PHONE_ID_0);
    }

    public IRadioNetworkImpl getIRadioNetwork(byte phoneId) {
        return sIRadioNetworkImpl[phoneId];
    }

    public IRadioVoiceImpl getIRadioVoice() {
        return getIRadioVoice(PHONE_ID_0);
    }

    public IRadioVoiceImpl getIRadioVoice(byte phoneId) {
        return sIRadioVoiceImpl[phoneId];
    }

    public IRadioMessagingImpl getIRadioMessaging() {
        return getIRadioMessaging(PHONE_ID_0);
    }

    public IRadioMessagingImpl getIRadioMessaging(byte phoneId) {
        return sIRadioMessagingImpl[phoneId];
    }

    public IRadioDataImpl getIRadioData() {
        return getIRadioData(PHONE_ID_0);
    }

    public IRadioDataImpl getIRadioData(byte phoneId) {
        return sIRadioDataImpl[phoneId];
    }
}
