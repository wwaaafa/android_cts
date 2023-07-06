/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.admin.cts;

import android.admin.app.CtsDeviceAdminActivationTestActivity;
import android.admin.app.CtsDeviceAdminActivationTestActivity.OnActivityResultListener;
import android.admin.app.CtsDeviceAdminBrokenReceiver;
import android.admin.app.CtsDeviceAdminBrokenReceiver2;
import android.admin.app.CtsDeviceAdminBrokenReceiver3;
import android.admin.app.CtsDeviceAdminBrokenReceiver4;
import android.admin.app.CtsDeviceAdminBrokenReceiver5;
import android.admin.app.CtsDeviceAdminDeactivatedReceiver;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import android.test.ActivityInstrumentationTestCase2;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

/**
 * Tests for the standard way of activating a Device Admin: by starting system UI via an
 * {@link Intent} with {@link DevicePolicyManager#ACTION_ADD_DEVICE_ADMIN}. The test requires that
 * the {@code CtsDeviceAdmin.apk} be installed.
 */
public class DeviceAdminActivationTest
    extends ActivityInstrumentationTestCase2<CtsDeviceAdminActivationTestActivity> {

    private static final String TAG = DeviceAdminActivationTest.class.getSimpleName();

    // IMPLEMENTATION NOTE: Because Device Admin activation requires the use of
    // Activity.startActivityForResult, this test creates an empty Activity which then invokes
    // startActivityForResult.

    private static final int REQUEST_CODE_ACTIVATE_ADMIN = 1;

    /**
     * Maximum duration of time (milliseconds) after which the effects of programmatic actions in
     * this test should have affected the UI.
     */
    private static final int UI_EFFECT_TIMEOUT_MILLIS = 5000;

    private boolean mDeviceAdmin;

    @Mock private OnActivityResultListener mMockOnActivityResultListener;

    public DeviceAdminActivationTest() {
        super(CtsDeviceAdminActivationTestActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        getActivity().setOnActivityResultListener(mMockOnActivityResultListener);
        mDeviceAdmin = getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_DEVICE_ADMIN);
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            finishActivateDeviceAdminActivity();
        } finally {
            super.tearDown();
        }
    }

    public void testActivateGoodReceiverDisplaysActivationUi() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testActivateGoodReceiverDisplaysActivationUi");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminDeactivatedReceiver.class);
        assertWithTimeoutOnActivityResultNotInvoked();
        // The UI is up and running. Assert that dismissing the UI returns the corresponding result
        // to the test activity.
        finishActivateDeviceAdminActivity();
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
    }

    public void testActivateBrokenReceiverFails() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testActivateBrokenReceiverFails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver.class);
    }

    public void testActivateBrokenReceiver2Fails() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver2Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver2.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver2.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver2.class);
    }

    public void testActivateBrokenReceiver3Fails() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver3Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver3.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver3.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver3.class);
    }

    public void testActivateBrokenReceiver4Fails() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver4Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver4.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver4.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver4.class);
    }

    public void testActivateBrokenReceiver5Fails() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testActivateBrokenReceiver5Fails");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver5.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminBrokenReceiver5.class);
        assertWithTimeoutOnActivityResultInvokedWithResultCode(Activity.RESULT_CANCELED);
        assertDeviceAdminDeactivated(CtsDeviceAdminBrokenReceiver5.class);
    }

    public void testNonSystemAppCantBecomeAdmin_withKeyEvent() throws Exception {
        if (!mDeviceAdmin) {
            Log.w(TAG, "Skipping testNonSystemAppCantBecomeAdmin_withKeyEvent");
            return;
        }
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
        startAddDeviceAdminActivityForResult(CtsDeviceAdminDeactivatedReceiver.class);
        assertWithTimeoutOnActivityResultNotInvoked();

        // inject KEYCODE_TAB and then KEYCODE_ENTER on "Activate" button should fail.
        injectUntrustedKeyEventToActivateDeviceAdmin();
        assertWithTimeoutOnActivityResultNotInvoked();

        finishActivateDeviceAdminActivity();
        assertDeviceAdminDeactivated(CtsDeviceAdminDeactivatedReceiver.class);
    }

    /**
     * Inject KeyEvents to tap the "Activate this device admin app" button.
     * Sends the "tab" KeyEvent to selects the first button, then the "enter" KeyEvent presses it.
      */
    private void injectUntrustedKeyEventToActivateDeviceAdmin() {
        sendUntrustedDownUpKeyEvents(KeyEvent.KEYCODE_TAB);
        sendUntrustedDownUpKeyEvents(KeyEvent.KEYCODE_ENTER);
    }

    /**
     * Creates a untrusted KeyEvent with KEYCODE_ENTER that appears to originate from a physical
     * keyboard.
     */
    private KeyEvent createUntrustedKeyEvent(int action, int keyCode) {
        return new KeyEvent(
                SystemClock.uptimeMillis(),
                SystemClock.uptimeMillis(),
                action,
                keyCode,
                0,
                0,
                0,
                0,
                0 /* flags: not setting FLAG_FROM_SYSTEM */,
                InputDevice.SOURCE_KEYBOARD
        );
    }

    void sendUntrustedDownUpKeyEvents(int keyCode) {
        UiAutomation uiAutomation = getInstrumentation().getUiAutomation();
        uiAutomation.injectInputEvent(
                createUntrustedKeyEvent(KeyEvent.ACTION_DOWN, keyCode), true /* sync */);
        uiAutomation.injectInputEvent(
                createUntrustedKeyEvent(KeyEvent.ACTION_UP, keyCode), true /* sync */);
    }

    private void startAddDeviceAdminActivityForResult(Class<?> receiverClass) {
        getActivity().startActivityForResult(
                getAddDeviceAdminIntent(receiverClass),
                REQUEST_CODE_ACTIVATE_ADMIN);
    }

    private Intent getAddDeviceAdminIntent(Class<?> receiverClass) {
        return new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(
                    DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                    new ComponentName(
                            getInstrumentation().getTargetContext(),
                            receiverClass));
    }

    private void assertWithTimeoutOnActivityResultNotInvoked() {
        SystemClock.sleep(UI_EFFECT_TIMEOUT_MILLIS);
        Mockito.verify(mMockOnActivityResultListener, Mockito.never())
                .onActivityResult(
                        Mockito.eq(REQUEST_CODE_ACTIVATE_ADMIN),
                        Mockito.anyInt(),
                        Mockito.nullable(Intent.class));
    }

    private void assertWithTimeoutOnActivityResultInvokedWithResultCode(int expectedResultCode) {
        ArgumentCaptor<Integer> resultCodeCaptor = ArgumentCaptor.forClass(int.class);
        Mockito.verify(mMockOnActivityResultListener, Mockito.timeout(UI_EFFECT_TIMEOUT_MILLIS))
                .onActivityResult(
                        Mockito.eq(REQUEST_CODE_ACTIVATE_ADMIN),
                        resultCodeCaptor.capture(),
                        Mockito.nullable(Intent.class));
        assertEquals(expectedResultCode, (int) resultCodeCaptor.getValue());
    }

    private void finishActivateDeviceAdminActivity() {
        getActivity().finishActivity(REQUEST_CODE_ACTIVATE_ADMIN);
    }

    private void assertDeviceAdminDeactivated(Class<?> receiverClass) {
        DevicePolicyManager devicePolicyManager =
                (DevicePolicyManager) getActivity().getSystemService(
                        Context.DEVICE_POLICY_SERVICE);
        assertFalse(devicePolicyManager.isAdminActive(
                new ComponentName(getInstrumentation().getTargetContext(), receiverClass)));
    }
}
