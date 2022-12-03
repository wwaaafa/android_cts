/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.cts.graphics.framerateoverride;

import android.Manifest;
import android.app.compat.CompatChanges;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.support.test.uiautomator.UiDevice;
import android.sysprop.SurfaceFlingerProperties;
import android.util.Log;
import android.view.Display;
import android.view.Window;
import android.view.WindowManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.cts.graphics.framerateoverride.FrameRateOverrideTestActivity.FrameRateObserver;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tests for frame rate override and the behaviour of {@link Display#getRefreshRate()} and
 * {@link Display.Mode#getRefreshRate()} Api.
 */
@RunWith(AndroidJUnit4.class)
public final class FrameRateOverrideTest {
    private static final String TAG = "FrameRateOverrideTest";
    // See b/170503758 for more details
    private static final long DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID = 170503758;

    // The tolerance within which we consider refresh rates are equal
    private static final float REFRESH_RATE_TOLERANCE = 0.01f;

    private int mInitialMatchContentFrameRate;
    private DisplayManager mDisplayManager;
    private UiDevice mUiDevice;
    private final Handler mHandler = new Handler(Looper.getMainLooper());


    @Rule
    public ActivityTestRule<FrameRateOverrideTestActivity> mActivityRule =
            new ActivityTestRule<>(FrameRateOverrideTestActivity.class);

    @Before
    public void setUp() throws Exception {
        mUiDevice = UiDevice.getInstance(
                        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation());
        mUiDevice.wakeUp();
        mUiDevice.executeShellCommand("wm dismiss-keyguard");

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.LOG_COMPAT_CHANGE,
                        Manifest.permission.READ_COMPAT_CHANGE_CONFIG,
                        Manifest.permission.MODIFY_REFRESH_RATE_SWITCHING_TYPE,
                        Manifest.permission.OVERRIDE_DISPLAY_MODE_REQUESTS,
                        Manifest.permission.MANAGE_GAME_MODE);

        mDisplayManager = mActivityRule.getActivity().getSystemService(DisplayManager.class);
        mInitialMatchContentFrameRate = toSwitchingType(
                mDisplayManager.getMatchContentFrameRateUserPreference());
        mDisplayManager.setRefreshRateSwitchingType(
                DisplayManager.SWITCHING_TYPE_RENDER_FRAME_RATE_ONLY);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(true);
        boolean changeIsEnabled =
                CompatChanges.isChangeEnabled(DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID);
        Log.i(TAG, "DISPLAY_MODE_RETURNS_PHYSICAL_REFRESH_RATE_CHANGEID is "
                + (changeIsEnabled ? "enabled" : "disabled"));
    }

    @After
    public void tearDown() {
        mDisplayManager.setRefreshRateSwitchingType(mInitialMatchContentFrameRate);
        mDisplayManager.setShouldAlwaysRespectAppRequestedMode(false);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private int toSwitchingType(int matchContentFrameRateUserPreference) {
        switch (matchContentFrameRateUserPreference) {
            case DisplayManager.MATCH_CONTENT_FRAMERATE_NEVER:
                return DisplayManager.SWITCHING_TYPE_NONE;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_SEAMLESSS_ONLY:
                return DisplayManager.SWITCHING_TYPE_WITHIN_GROUPS;
            case DisplayManager.MATCH_CONTENT_FRAMERATE_ALWAYS:
                return DisplayManager.SWITCHING_TYPE_ACROSS_AND_WITHIN_GROUPS;
            default:
                return -1;
        }
    }

    private void setMode(Display.Mode mode) {
        Log.i(TAG, "Setting display refresh rate to " + mode.getRefreshRate());
        mHandler.post(() -> {
            Window window = mActivityRule.getActivity().getWindow();
            WindowManager.LayoutParams params = window.getAttributes();
            params.preferredDisplayModeId = mode.getModeId();
            params.preferredRefreshRate = 0;
            params.preferredMinDisplayRefreshRate = 0;
            params.preferredMaxDisplayRefreshRate = 0;
            window.setAttributes(params);
        });
    }

    private static boolean areEqual(float a, float b) {
        return Math.abs(a - b) <= REFRESH_RATE_TOLERANCE;
    }

    // Find refresh rates with the same resolution. If FrameRateOverride is only enabled for
    // native refresh rates, then this function returns refresh rates which natively supports
    // half that rate with the same resolution (for example, a 120Hz mode when the device also
    // supports a 60Hz mode).
    private List<Display.Mode> getModesToTest() {
        List<Display.Mode> modesToTest = new ArrayList<>();
        if (!SurfaceFlingerProperties.enable_frame_rate_override().orElse(false)) {
            Log.i(TAG, "Frame rate override is not enabled, skipping");
            return modesToTest;
        }

        Display.Mode[] modes = mActivityRule.getActivity().getDisplay().getSupportedModes();
        for (Display.Mode mode : modes) {
            for (Display.Mode otherMode : modes) {
                if (mode.getModeId() == otherMode.getModeId()) {
                    continue;
                }

                if (mode.getPhysicalHeight() != otherMode.getPhysicalHeight()
                        || mode.getPhysicalWidth() != otherMode.getPhysicalWidth()) {
                    continue;
                }

                boolean overrideForNativeRates = SurfaceFlingerProperties
                        .frame_rate_override_for_native_rates().orElse(true);
                if (overrideForNativeRates) {
                    // only add if this refresh rate is a multiple of the other
                    if (areEqual(mode.getRefreshRate(), 2 * otherMode.getRefreshRate())) {
                        modesToTest.add(mode);
                    }
                } else {
                    modesToTest.add(mode);
                }
            }
        }

        return modesToTest;
    }

    // Use WindowManager.LayoutParams#preferredMinDisplayRefreshRate and
    // WindowManager.LayoutParams#preferredMaxDisplayRefreshRateto set the frame rate override.
    // This would be communicated to SF by DM setting the render frame rate policy to a single
    // value, which is the preferred refresh rate.
    private void testGlobalFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        if (!SurfaceFlingerProperties.frame_rate_override_global().orElse(false)) {
            Log.i(TAG, "Global frame rate override is not enabled, skipping");
            return;
        }

        for (Display.Mode mode : getModesToTest()) {
            //setPreferredRefreshRate(mode.getRefreshRate());
            setMode(mode);
            activity.testFrameRateOverride(
                    activity.new PreferredRefreshRateFrameTest(),
                    frameRateObserver, mode.getRefreshRate());
            Log.i(TAG, "\n");
        }
        Log.i(TAG, "\n");
    }

    // Use WindowManager.LayoutParams#preferredDisplayModeId to lock the physical display refresh
    // rate. This would be communicated to SF by DM setting the physical refresh rate frame rate
    // a single value, which is the refresh rate of the preferredDisplayModeId, while still
    // allowing render rate switching. The test will use Surface#setFrameRate to tell SF the
    // preferred render rate and to enable the frame rate override for the test app.
    private void testAppFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        for (Display.Mode mode : getModesToTest()) {
            setMode(mode);
            activity.testFrameRateOverride(activity.new SurfaceSetFrameRateTest(),
                    frameRateObserver, mode.getRefreshRate());
            Log.i(TAG, "\n");
        }
        Log.i(TAG, "\n");
    }

    private void testGameModeFrameRateOverride(FrameRateObserver frameRateObserver)
            throws InterruptedException, IOException {
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        for (Display.Mode mode : getModesToTest()) {
            setMode(mode);
            activity.testFrameRateOverride(
                    activity.new GameModeTest(mUiDevice),
                    frameRateObserver, mode.getRefreshRate());
            Log.i(TAG, "\n");
        }
        Log.i(TAG, "\n");
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest.testAppBackpressureDisplayModeReturnsPhysicalRefreshRateEnabled
     * and
     * FrameRateOverrideHostTest.testAppBackpressureDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testAppBackpressure()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Backpressure Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest.testAppChoreographerDisplayModeReturnsPhysicalRefreshRateEnabled
     * and
     * FrameRateOverrideHostTest.testAppChoreographerDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testAppChoreographer()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Choreographer Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testAppDisplayGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     * and
     * FrameRateOverrideHostTest
     * .testAppDisplayGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testAppDisplayGetRefreshRate()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Display#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testAppDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testAppDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ true));
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testAppDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testAppDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting App Override Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testAppFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ false));
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest.testGlobalBackpressureDisplayModeReturnsPhysicalRefreshRateEnabled
     * and
     * FrameRateOverrideHostTest.testGlobalBackpressureDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testGlobalBackpressure()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Backpressure Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest.testGlobalChoreographerDisplayModeReturnsPhysicalRefreshRateEnabled
     * and
     * FrameRateOverrideHostTest
     *      .testGlobalChoreographerDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testGlobalChoreographer()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Choreographer Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGlobalDisplayGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     * and
     * FrameRateOverrideHostTest
     * .testGlobalDisplayGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testGlobalDisplayGetRefreshRate()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Display#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGlobalDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGlobalDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ true));
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGlobalDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testGlobalDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Global Override Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGlobalFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ false));
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeBackpressureDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeBackpressure() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Backpressure Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new BackpressureFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeChoreographerDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeChoreographer() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Choreographer Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new ChoreographerFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeDisplayGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeDisplayGetRefreshRate() throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(activity.new DisplayGetRefreshRateFrameRateObserver());
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled
     */
    @Test
    public void testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateDisabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ false));
    }

    /**
     * Test run by
     * FrameRateOverrideHostTest
     * .testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled
     */
    @Test
    public void testGameModeDisplayModeGetRefreshRateDisplayModeReturnsPhysicalRefreshRateEnabled()
            throws InterruptedException, IOException {
        Log.i(TAG, "**** Starting Game Mode Display.Mode#getRefreshRate Test ****");
        FrameRateOverrideTestActivity activity = mActivityRule.getActivity();
        testGameModeFrameRateOverride(
                activity.new DisplayModeGetRefreshRateFrameRateObserver(
                        /*displayModeReturnsPhysicalRefreshRateEnabled*/ true));
    }
}
