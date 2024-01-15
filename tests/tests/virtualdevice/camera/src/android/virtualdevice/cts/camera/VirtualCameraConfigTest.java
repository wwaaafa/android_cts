/*
 * Copyright 2023 The Android Open Source Project
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

package android.virtualdevice.cts.camera;

import static androidx.test.core.app.ApplicationProvider.getApplicationContext;

import static org.junit.Assert.assertThrows;

import android.companion.virtual.camera.VirtualCameraCallback;
import android.companion.virtual.camera.VirtualCameraConfig;
import android.graphics.ImageFormat;
import android.os.Parcel;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.Executor;

@RequiresFlagsEnabled(android.companion.virtual.flags.Flags.FLAG_VIRTUAL_CAMERA)
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualCameraConfigTest {

    private static final String CAMERA_NAME = "Virtual Camera";
    private static final int CAMERA_WIDTH = 640;
    private static final int CAMERA_HEIGHT = 480;
    private static final int CAMERA_FORMAT = ImageFormat.YUV_420_888;
    private static final int CAMERA_MAX_FPS = 30;
    private static final int CAMERA_SENSOR_ORIENTATION = VirtualCameraConfig.SENSOR_ORIENTATION_0;

    @Rule
    public MockitoRule mRule = MockitoJUnit.rule();

    @Mock
    private VirtualCameraCallback mCallback;

    private final Executor mExecutor = getApplicationContext().getMainExecutor();

    @Test
    public void virtualCameraConfigBuilder_buildsCorrectConfig() {
        VirtualCameraConfig config = new VirtualCameraConfig.Builder()
                .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                .setName(CAMERA_NAME)
                .setVirtualCameraCallback(mExecutor, mCallback)
                .setSensorOrientation(CAMERA_SENSOR_ORIENTATION)
                .build();

        VirtualCameraUtils.assertVirtualCameraConfig(config, CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_NAME);
    }

    @Test
    public void virtualCameraConfigBuilder_tooSmallWidth_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(0 /* width */, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooLargeWidth_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(3000 /* width */, CAMERA_HEIGHT, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooSmallHeight_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, 0 /* height */, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooLargeHeight_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, 3000 /* height */, CAMERA_FORMAT,
                                CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_invalidFormat_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, -1 /* format */,
                                CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooLowMaximumFramesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                0 /* maximumFramesPerSecond */)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_tooHighMaximumFramesPerSecond_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT,
                                100 /* maximumFramesPerSecond */)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullName_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setName(null)
                        .setVirtualCameraCallback(mExecutor, mCallback)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullCallback_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(mExecutor, null /* callback */)
                        .build());
    }

    @Test
    public void virtualCameraConfigBuilder_nullExecutor_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new VirtualCameraConfig.Builder()
                        .addStreamConfig(CAMERA_WIDTH, CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS)
                        .setName(CAMERA_NAME)
                        .setVirtualCameraCallback(null /* executor */, mCallback)
                        .build());
    }

    @Test
    public void parcelAndUnparcel_matches() {
        VirtualCameraConfig original = VirtualCameraUtils.createVirtualCameraConfig(CAMERA_WIDTH,
                CAMERA_HEIGHT, CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION,
                CAMERA_NAME, mExecutor, mCallback);

        final Parcel parcel = Parcel.obtain();
        original.writeToParcel(parcel, 0 /* flags */);
        parcel.setDataPosition(0);
        final VirtualCameraConfig recreated =
                VirtualCameraConfig.CREATOR.createFromParcel(parcel);

        VirtualCameraUtils.assertVirtualCameraConfig(recreated, CAMERA_WIDTH, CAMERA_HEIGHT,
                CAMERA_FORMAT, CAMERA_MAX_FPS, CAMERA_SENSOR_ORIENTATION, CAMERA_NAME);
    }
}
