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

package android.virtualdevice.cts.common;

import static android.Manifest.permission.ADD_ALWAYS_UNLOCKED_DISPLAY;
import static android.Manifest.permission.ADD_TRUSTED_DISPLAY;
import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.content.pm.PackageManager.FEATURE_FREEFORM_WINDOW_MANAGEMENT;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.WindowManagerStateHelper;
import android.view.Surface;
import android.virtualdevice.cts.common.util.VirtualDeviceTestUtils;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.internal.app.BlockedAppStreamingActivity;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A test rule that allows for testing VDM and virtual device features.
 */
@TargetApi(34)
public class VirtualDeviceRule implements TestRule {

    /** General permissions needed for created virtual devices and displays. */
    private static final String[] REQUIRED_PERMISSIONS = new String[] {
            CREATE_VIRTUAL_DEVICE,
            ADD_TRUSTED_DISPLAY,
            ADD_ALWAYS_UNLOCKED_DISPLAY
    };

    public static final VirtualDeviceParams DEFAULT_VIRTUAL_DEVICE_PARAMS =
            new VirtualDeviceParams.Builder().build();
    public static final VirtualDisplayConfig DEFAULT_VIRTUAL_DISPLAY_CONFIG =
            createDefaultVirtualDisplayConfigBuilder().build();
    public static final VirtualDisplayConfig TRUSTED_VIRTUAL_DISPLAY_CONFIG =
            VirtualDeviceTestUtils.createDefaultVirtualDisplayConfigBuilder()
                    .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY)
                    .build();

    public static final String DEFAULT_VIRTUAL_DISPLAY_NAME = "testVirtualDisplay";
    public static final int DEFAULT_VIRTUAL_DISPLAY_WIDTH = 640;
    public static final int DEFAULT_VIRTUAL_DISPLAY_HEIGHT = 480;
    public static final int DEFAULT_VIRTUAL_DISPLAY_DPI = 420;

    public static final ComponentName BLOCKED_ACTIVITY_COMPONENT =
            new ComponentName("android", BlockedAppStreamingActivity.class.getName());

    private static final Instrumentation sInstrumentation = getInstrumentation();

    private final RuleChain mRuleChain;
    private final FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();
    private final VirtualDeviceTrackerRule mTrackerRule = new VirtualDeviceTrackerRule();

    private final Context mContext = sInstrumentation.getContext();
    private final VirtualDeviceManager mVirtualDeviceManager =
            mContext.getSystemService(VirtualDeviceManager.class);
    private final PackageManager mPackageManager = mContext.getPackageManager();
    private final WindowManagerStateHelper mWmState = new WindowManagerStateHelper();

    /** Creates a rule with the required permissions for creating virtual devices and displays. */
    public static VirtualDeviceRule createDefault() {
        return new VirtualDeviceRule(REQUIRED_PERMISSIONS);
    }

    /** Creates a rule with any additional permission needed for the specific test. */
    public static VirtualDeviceRule withAdditionalPermissions(String... additionalPermissions) {
        return new VirtualDeviceRule(Stream.concat(
                Arrays.stream(REQUIRED_PERMISSIONS), Arrays.stream(additionalPermissions))
                .toArray(String[]::new));
    }

    private VirtualDeviceRule(String... permissions) {
        mRuleChain = RuleChain
                .outerRule(mFakeAssociationRule)
                .around(DeviceFlagsValueProvider.createCheckFlagsRule())
                .around(new PlatformCompatChangeRule())
                .around(new AdoptShellPermissionsRule(
                        sInstrumentation.getUiAutomation(), permissions))
                .around(mTrackerRule);
    }

    @Override
    public Statement apply(final Statement base, final Description description) {
        assumeNotNull(mVirtualDeviceManager);
        assumeTrue(mPackageManager.hasSystemFeature(
                PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        // TODO(b/261155110): Re-enable tests once freeform mode is supported in Virtual Display
        assumeFalse("Skipping test: VirtualDisplay window policy doesn't support freeform.",
                mPackageManager.hasSystemFeature(FEATURE_FREEFORM_WINDOW_MANAGEMENT));
        assumeFalse("Skipping test: not supported on automotive",
                mPackageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        return mRuleChain.apply(base, description);
    }

    /**
     * Creates a virtual device with default params that will be automatically closed when the
     * test is torn down.
     */
    @NonNull
    public VirtualDevice createManagedVirtualDevice() {
        return createManagedVirtualDevice(DEFAULT_VIRTUAL_DEVICE_PARAMS);
    }

    /**
     * Creates a virtual device with the given params that will be automatically closed when the
     * test is torn down.
     */
    @NonNull
    public VirtualDevice createManagedVirtualDevice(@NonNull VirtualDeviceParams params) {
        final VirtualDevice virtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(), params);
        mTrackerRule.mVirtualDevices.add(virtualDevice);
        return virtualDevice;
    }

    /**
     * Creates a virtual display associated with the given device that will be automatically
     * released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplay(@NonNull VirtualDevice virtualDevice) {
        return createManagedVirtualDisplay(virtualDevice, DEFAULT_VIRTUAL_DISPLAY_CONFIG);
    }

    /**
     * Creates a virtual display associated with the given device and flags that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplayWithFlags(
            @NonNull VirtualDevice virtualDevice, int flags) {
        return createManagedVirtualDisplay(virtualDevice,
                createDefaultVirtualDisplayConfigBuilder().setFlags(flags).build());
    }

    /**
     * Creates a virtual display associated with the given device and config that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedVirtualDisplay(@NonNull VirtualDevice virtualDevice,
            @NonNull VirtualDisplayConfig config) {
        final VirtualDisplay virtualDisplay = virtualDevice.createVirtualDisplay(
                config, /* executor= */ null, /* callback= */ null);
        if (virtualDisplay != null) {
            assertDisplayExists(virtualDisplay.getDisplay().getDisplayId());
        }
        mTrackerRule.mVirtualDisplays.add(virtualDisplay);
        return virtualDisplay;
    }

    /**
     * Creates a virtual display not associated with the any virtual device that will be
     * automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedUnownedVirtualDisplay() {
        return createManagedUnownedVirtualDisplay(DEFAULT_VIRTUAL_DISPLAY_CONFIG);
    }

    /**
     * Creates a virtual display not associated with the any virtual device with the given flags
     * that will be automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedUnownedVirtualDisplayWithFlags(int flags) {
        return createManagedUnownedVirtualDisplay(
                createDefaultVirtualDisplayConfigBuilder().setFlags(flags).build());
    }

    /**
     * Creates a virtual display not associated with the any virtual device with the given config
     * that will be automatically released when the test is torn down.
     */
    @Nullable
    public VirtualDisplay createManagedUnownedVirtualDisplay(@NonNull VirtualDisplayConfig config) {
        final VirtualDisplay virtualDisplay =
                mContext.getSystemService(DisplayManager.class).createVirtualDisplay(config);
        if (virtualDisplay != null) {
            assertDisplayExists(virtualDisplay.getDisplay().getDisplayId());
            mTrackerRule.mVirtualDisplays.add(virtualDisplay);
        }
        return virtualDisplay;
    }

    /**
     * Default config for virtual display creation, with a predefined name, dimensions and an empty
     * surface.
     */
    @NonNull
    public static VirtualDisplayConfig.Builder createDefaultVirtualDisplayConfigBuilder() {
        return new VirtualDisplayConfig.Builder(
                DEFAULT_VIRTUAL_DISPLAY_NAME, DEFAULT_VIRTUAL_DISPLAY_WIDTH,
                DEFAULT_VIRTUAL_DISPLAY_HEIGHT, DEFAULT_VIRTUAL_DISPLAY_DPI)
                .setSurface(new Surface());
    }

    /**
     * Blocks until the display with the given ID is available.
     */
    public void assertDisplayExists(int displayId) {
        mWmState.waitForWithAmState(state -> state.getDisplay(displayId) != null,
                "Waiting for display to be available");
    }

    /**
     * Blocks until the display with the given ID is removed.
     */
    public void assertDisplayDoesNotExist(int displayId) {
        mWmState.waitForWithAmState(state -> state.getDisplay(displayId) == null,
                "Waiting for display to be removed");
    }

    /** Returns the WM state helper. */
    public WindowManagerStateHelper getWmState() {
        return mWmState;
    }

    /** Drops the current CDM association. */
    public void dropCompanionDeviceAssociation() {
        mFakeAssociationRule.disassociate();
    }

    /**
     * Temporarily assumes the given permissions and executes the given supplier. Reverts any
     * permissions currently held after the execution.
     */
    public <T> T runWithTemporaryPermission(Supplier<T> supplier, String... permissions) {
        UiAutomation uiAutomation = sInstrumentation.getUiAutomation();
        final Set<String> currentPermissions = uiAutomation.getAdoptedShellPermissions();
        uiAutomation.adoptShellPermissionIdentity(permissions);
        try {
            return supplier.get();
        } finally {
            // Revert the permissions needed for the test again.
            uiAutomation.adoptShellPermissionIdentity(
                    currentPermissions.toArray(new String[currentPermissions.size()]));
        }
    }

    /**
     * Starts the activity for the given class on the given virtul display and blocks until it is
     * successfully launched there.
     */
    public <T extends Activity> T startActivityOnDisplaySync(
            VirtualDisplay virtualDisplay, Class<T> clazz) {
        final int displayId = virtualDisplay.getDisplay().getDisplayId();
        T activity = startActivityOnDisplaySync(displayId, clazz);
        assertActivityOnDisplay(activity, displayId);
        return activity;
    }

    /**
     * Starts the activity for the given class on the given display and blocks until it is
     * successfully launched there.
     */
    public <T extends Activity> T startActivityOnDisplaySync(int displayId, Class<T> clazz) {
        return (T) sInstrumentation.startActivitySync(new Intent(mContext, clazz)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                createActivityOptions(displayId));
    }

    /**
     * Creates activity options for launching activities on the given virtual display.
     */
    public static Bundle createActivityOptions(VirtualDisplay virtualDisplay) {
        return createActivityOptions(virtualDisplay.getDisplay().getDisplayId());
    }

    /**
     * Creates activity options for launching activities on the given display.
     */
    public static Bundle createActivityOptions(int displayId) {
        return ActivityOptions.makeBasic().setLaunchDisplayId(displayId).toBundle();
    }

    /**
     * Creates activity options for launching activities on the given display.
     */
    public <T extends Activity> void assertActivityOnDisplay(T activity, int displayId) {
        mWmState.assertActivityDisplayed(activity.getComponentName());
        assertThat(activity.getDisplay().getDisplayId()).isEqualTo(displayId);
    }

    /**
     * Internal rule that tracks all created virtual devices and displays and ensures they are
     * properly closed and released after the test.
     */
    private final class VirtualDeviceTrackerRule extends ExternalResource {

        final ArrayList<VirtualDevice> mVirtualDevices = new ArrayList<>();
        final ArrayList<VirtualDisplay> mVirtualDisplays = new ArrayList<>();

        @Override
        protected void after() {
            for (VirtualDevice virtualDevice : mVirtualDevices) {
                virtualDevice.close();
            }
            for (VirtualDisplay virtualDisplay : mVirtualDisplays) {
                virtualDisplay.release();
                assertDisplayDoesNotExist(virtualDisplay.getDisplay().getDisplayId());
            }
            super.after();
        }
    }
}
