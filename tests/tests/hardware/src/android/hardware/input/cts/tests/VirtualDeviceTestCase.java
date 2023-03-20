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

package android.hardware.input.cts.tests;

import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.companion.AssociationInfo;
import android.companion.CompanionDeviceManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.collect.ImmutableList;

import org.junit.Rule;

import java.util.Objects;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class VirtualDeviceTestCase extends InputTestCase {

    private static final String TAG = "VirtualDeviceTestCase";
    private static final int BARRIER_TIMEOUT = 1;

    private static final int ARBITRARY_SURFACE_TEX_ID = 1;

    protected static final int PRODUCT_ID = 1;
    protected static final int VENDOR_ID = 1;
    protected static final int DISPLAY_WIDTH = 100;
    protected static final int DISPLAY_HEIGHT = 100;

    private InputDevice mInputDevice;

    // Uses:
    // Manifest.permission.CREATE_VIRTUAL_DEVICE,
    // Manifest.permission.ADD_TRUSTED_DISPLAY
    // These cannot be specified as part of the call as ADD_TRUSTED_DISPLAY is hidden and therefore
    // not visible to CTS.
    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation());

    private final CyclicBarrier mBarrier = new CyclicBarrier(2);
    private final InputManager.InputDeviceListener mInputDeviceListener =
            new InputManager.InputDeviceListener() {
                @Override
                public void onInputDeviceAdded(int deviceId) {
                    mInputDevice = mInputManager.getInputDevice(deviceId);
                    try {
                        mBarrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        Log.e(TAG, "Unexpected exception while waiting on CyclicBarrier", e);
                    }
                }

                @Override
                public void onInputDeviceRemoved(int deviceId) {
                    if (mInputDevice != null && deviceId == mInputDevice.getId()) {
                        mInputDevice = null;
                    }
                }

                @Override
                public void onInputDeviceChanged(int deviceId) {
                }
            };

    InputManager mInputManager;

    VirtualDeviceManager.VirtualDevice mVirtualDevice;
    VirtualDisplay mVirtualDisplay;

    /** Helper class to drop permissions temporarily and restore them at the end of a test. */
    static final class DropShellPermissionsTemporarily implements AutoCloseable {
        DropShellPermissionsTemporarily() {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }

        @Override
        public void close() {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity();
        }
    }

    @Override
    void onBeforeLaunchActivity() {
        final Context context = InstrumentationRegistry.getTargetContext();
        final PackageManager packageManager = context.getPackageManager();
        // TVs do not support companion
        assumeTrue(packageManager.hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP));
        // Virtual input devices only operate on virtual displays
        assumeTrue(packageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display.
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                packageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));

        final String packageName = context.getPackageName();
        associateCompanionDevice(packageName);
        AssociationInfo associationInfo = null;
        for (AssociationInfo ai : context.getSystemService(CompanionDeviceManager.class)
                .getMyAssociations()) {
            if (packageName.equals(ai.getPackageName())) {
                associationInfo = ai;
                break;
            }
        }
        if (associationInfo == null) {
            fail("Could not create association for test");
            return;
        }
        final VirtualDeviceManager virtualDeviceManager =
                context.getSystemService(VirtualDeviceManager.class);
        mVirtualDevice = virtualDeviceManager.createVirtualDevice(associationInfo.getId(),
                new VirtualDeviceParams.Builder().build());
        mVirtualDisplay = mVirtualDevice.createVirtualDisplay(
                /* width= */ DISPLAY_WIDTH,
                /* height= */ DISPLAY_HEIGHT,
                /* dpi= */ 50,
                /* surface= */ new Surface(new SurfaceTexture(ARBITRARY_SURFACE_TEX_ID)),
                /* flags= */ 0,
                /* executor= */ Runnable::run,
                /* callback= */ null);
        if (mVirtualDisplay == null) {
            fail("Could not create virtual display");
        }
    }

    @Override
    void onSetUp() {
        mInputManager = mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        mInputManager.registerInputDeviceListener(mInputDeviceListener,
                new Handler(Looper.getMainLooper()));
        onSetUpVirtualInputDevice();
        waitForInputDevice();
        // Tap to gain window focus on the activity
        tapActivityToFocus();
    }

    abstract void onSetUpVirtualInputDevice();

    abstract void onTearDownVirtualInputDevice();

    @Override
    void onTearDown() {
        try {
            onTearDownVirtualInputDevice();
        } finally {
            if (mTestActivity != null) {
                mTestActivity.finish();
            }
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
            }
            if (mVirtualDevice != null) {
                mVirtualDevice.close();
            }
            final Context context = InstrumentationRegistry.getTargetContext();
            mInputManager.unregisterInputDeviceListener(mInputDeviceListener);
            disassociateCompanionDevice(context.getPackageName());
        }
    }

    @Override
    @Nullable
    Bundle getActivityOptions() {
        return ActivityOptions.makeBasic()
                .setLaunchDisplayId(mVirtualDisplay.getDisplay().getDisplayId())
                .toBundle();
    }

    protected InputDevice getInputDevice() {
        Objects.requireNonNull(mInputDevice, "Failed to get InputDevice");
        return mInputDevice;
    }

    protected void waitForInputDevice() {
        try {
            mBarrier.await(BARRIER_TIMEOUT, TimeUnit.SECONDS);
        } catch (InterruptedException | BrokenBarrierException | TimeoutException e) {
            fail("Virtual input device setup was interrupted or timed out.");
        }
        mBarrier.reset();
    }

    private void associateCompanionDevice(String packageName) {
        // Associate this package for user 0 with a zeroed-out MAC address (not used in this test)
        SystemUtil.runShellCommand(
                String.format("cmd companiondevice associate %d %s 00:00:00:00:00:00",
                        Process.myUserHandle().getIdentifier(), packageName));
    }

    private void disassociateCompanionDevice(String packageName) {
        SystemUtil.runShellCommand(
                String.format("cmd companiondevice disassociate %d %s 00:00:00:00:00:00",
                        Process.myUserHandle().getIdentifier(), packageName));
    }

    private void tapActivityToFocus() {
        final Point p = getViewCenterOnScreen(mTestActivity.getWindow().getDecorView());
        final int displayId = mTestActivity.getDisplayId();

        final long downTime = SystemClock.elapsedRealtime();
        final MotionEvent downEvent = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, p.x, p.y, 0 /* metaState */);
        downEvent.setDisplayId(displayId);
        final MotionEvent upEvent = MotionEvent.obtain(downTime, SystemClock.elapsedRealtime(),
                MotionEvent.ACTION_UP, p.x, p.y, 0 /* metaState */);
        upEvent.setDisplayId(displayId);

        try {
            mInstrumentation.sendPointerSync(downEvent);
            mInstrumentation.sendPointerSync(upEvent);
        } catch (IllegalArgumentException e) {
            fail("Failed to sending taps to the activity. Is the device unlocked?");
        }

        verifyEvents(ImmutableList.of(downEvent, upEvent));
    }

    private static Point getViewCenterOnScreen(@NonNull View view) {
        final int[] location = new int[2];
        view.getLocationOnScreen(location);
        return new Point(location[0] + view.getWidth() / 2,
                location[1] + view.getHeight() / 2);
    }

    public VirtualDisplay createUnownedVirtualDisplay() {
        return DisplayManager.createVirtualDisplay(
                "test",
                /* width= */ DISPLAY_WIDTH,
                /* height= */ DISPLAY_HEIGHT,
                /* displayIdToMirror= */ 50,
                /* surface= */ null
        );
    }
}
