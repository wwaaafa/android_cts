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

package android.photopicker.cts;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;
import androidx.test.uiautomator.UiDevice;




import org.junit.Assume;
import org.junit.Before;

import java.io.IOException;

/**
 * Photo Picker Base class for Photo Picker tests. This includes common setup methods
 * required for all Photo Picker tests.
 */
public class PhotoPickerBaseTest {
    private static final String TAG = "PhotoPickerBaseTest";
    public static int REQUEST_CODE = 42;
    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    protected static final String sTargetPackageName =
            sInstrumentation.getTargetContext().getPackageName();
    protected static final UiDevice sDevice = UiDevice.getInstance(sInstrumentation);

    protected GetResultActivity mActivity;
    protected Context mContext;

    @Before
    public void setUp() throws Exception {
        Assume.assumeTrue(isHardwareSupported());

        final String setSyncDelayCommand =
                "device_config put storage pickerdb.default_sync_delay_ms 0";
        sDevice.executeShellCommand(setSyncDelayCommand);

        mContext = sInstrumentation.getContext();
        final Intent intent = new Intent(mContext, GetResultActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        // Wake up the device and dismiss the keyguard before the test starts
        sDevice.executeShellCommand("input keyevent KEYCODE_WAKEUP");
        sDevice.executeShellCommand("wm dismiss-keyguard");

        mActivity = (GetResultActivity) sInstrumentation.startActivitySync(intent);
        // Wait for the UI Thread to become idle.
        sInstrumentation.waitForIdleSync();
        mActivity.clearResult();
        sDevice.waitForIdle();
    }

    static boolean isHardwareSupported() {
        // These UI tests are not optimised for Watches, TVs, Auto;
        // IoT devices do not have a UI to run these UI tests
        PackageManager pm = sInstrumentation.getContext().getPackageManager();
        return !pm.hasSystemFeature(pm.FEATURE_EMBEDDED)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_LEANBACK)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    protected static void setCloudProvider(@Nullable String authority) throws Exception {
        if (authority == null) {
            sDevice.executeShellCommand(
                    "content call"
                            + " --user " + UserHandle.myUserId()
                            + " --uri content://media/ --method set_cloud_provider --extra"
                            + " cloud_provider:n:null");
        } else {
            sDevice.executeShellCommand(
                    "content call"
                            + " --user " + UserHandle.myUserId()
                            + " --uri content://media/ --method set_cloud_provider --extra"
                            + " cloud_provider:s:"
                            + authority);
        }
    }

    protected static String getCurrentCloudProvider() throws IOException {
        final String out =
                sDevice.executeShellCommand(
                        "content call"
                                + " --user " + UserHandle.myUserId()
                                + " --uri content://media/ --method get_cloud_provider");
        return extractCloudProvider(out);
    }

    private static String extractCloudProvider(String out) {
        if (out == null) {
            Log.d(TAG, "Failed request to get current cloud provider");
            return null;
        }
        String cloudprovider = (out.split("=")[1]);
        cloudprovider = cloudprovider.substring(0, cloudprovider.length() - 3);
        if (cloudprovider.equals("null")) {
            return null;
        }
        return cloudprovider;
    }
}

