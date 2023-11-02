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

package android.virtualdevice.cts.sensor;

import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_SENSORS;
import static android.hardware.Sensor.TYPE_ACCELEROMETER;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.companion.virtual.sensor.VirtualSensorCallback;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.Display;
import android.virtualdevice.cts.common.FakeAssociationRule;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;
import android.virtualdevice.cts.sensor.util.NativeSensorTestActivity;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.internal.os.BackgroundThread;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for native sensor behavior for virtual devices. */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualSensorNativeTest {

    private static final VirtualDisplayConfig VIRTUAL_DISPLAY_CONFIG =
            VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                    .build();

    private static final String VIRTUAL_SENSOR_NAME = "virtual device accelerometer name";

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            ADD_TRUSTED_DISPLAY,
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private Context mContext;
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private NativeSensorTestActivity mNativeSensorTestActivity;
    @Mock
    private VirtualSensorCallback mVirtualSensorCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = getApplicationContext();
        final PackageManager packageManager = mContext.getPackageManager();
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        VirtualDeviceManager vdm = mContext.getSystemService(VirtualDeviceManager.class);
        assumeTrue(vdm != null);

        mVirtualDevice = vdm.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(),
                new VirtualDeviceParams.Builder()
                        .setDevicePolicy(POLICY_TYPE_SENSORS, DEVICE_POLICY_CUSTOM)
                        .addVirtualSensorConfig(
                                new VirtualSensorConfig.Builder(
                                        TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                                        .build())
                        .setVirtualSensorCallback(
                                BackgroundThread.getExecutor(), mVirtualSensorCallback)
                        .build());
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                VIRTUAL_DISPLAY_CONFIG, null, null);
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    /** Activity running on the default device should get the default device sensors by default. */
    @Test
    public void activityOnDefaultDisplayGetsDefaultDeviceSensor() {
        Context deviceContext = mContext.createDeviceContext(Context.DEVICE_ID_DEFAULT);
        SensorManager sensorManager = deviceContext.getSystemService(SensorManager.class);
        Sensor defaultDeviceSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        assumeNotNull(defaultDeviceSensor);

        mNativeSensorTestActivity = launchTestActivityOnDisplay(Display.DEFAULT_DISPLAY);

        assertThat(mNativeSensorTestActivity.nativeGetDefaultAccelerometerName())
                .isEqualTo(defaultDeviceSensor.getName());
    }

    /** Activity running on the virtual device should get the virtual device sensors by default. */
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_NATIVE_VDM)
    @Test
    public void activityOnVirtualDisplayGetsVirtualDeviceSensor() {
        mNativeSensorTestActivity =
                launchTestActivityOnDisplay(mVirtualDisplay.getDisplay().getDisplayId());

        assertThat(mNativeSensorTestActivity.nativeGetDefaultAccelerometerName())
                .isEqualTo(VIRTUAL_SENSOR_NAME);
    }

    private NativeSensorTestActivity launchTestActivityOnDisplay(int displayId) {
        return (NativeSensorTestActivity) InstrumentationRegistry.getInstrumentation()
                .startActivitySync(
                        new Intent(mContext, NativeSensorTestActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                        | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        VirtualDeviceTestUtils.createActivityOptions(displayId));
    }
}
