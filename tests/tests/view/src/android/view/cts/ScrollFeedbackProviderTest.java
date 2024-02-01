/**
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

package android.view.cts;

import static android.view.flags.Flags.FLAG_SCROLL_FEEDBACK_API;

import static com.google.common.truth.Truth.assertThat;

import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.ScrollFeedbackProvider;
import android.view.cts.util.InputDeviceIterators;

import androidx.test.filters.SmallTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * There currently is no strict API requirement for the feedback behavior, so this class only tests
 * that the feedback APIs do not crash when called.
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ScrollFeedbackProviderTest {
    @Rule
    public ActivityTestRule<ViewTestCtsActivity> mActivityRule =
            new ActivityTestRule<>(ViewTestCtsActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    private ScrollFeedbackProvider mProvider;

    /** Setup common for all tests. */
    @Before
    public void setup() {
        mProvider = ScrollFeedbackProvider.createProvider(
                mActivityRule.getActivity().findViewById(R.id.scroll_view));
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SCROLL_FEEDBACK_API)
    public void testDefaultProviderIsNotNull() {
        assertThat(mProvider).isNotNull();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SCROLL_FEEDBACK_API)
    public void testApisDontCrash_allExistingInputSourceAndAxesCombinations() {
        InputDeviceIterators.iteratorOverEveryInputDeviceMotionRange((deviceId, motionRange) -> {
            runScrollScenarios(deviceId, motionRange.getSource(), motionRange.getAxis());

        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SCROLL_FEEDBACK_API)
    public void testApisDontCrash_invalidDeviceIds() {
        InputDeviceIterators.iteratorOverInvalidDeviceIds((deviceId) -> {
            runScrollScenarios(
                    deviceId, InputDevice.SOURCE_ROTARY_ENCODER, MotionEvent.AXIS_SCROLL);
        });
    }

    @Test
    @RequiresFlagsEnabled(FLAG_SCROLL_FEEDBACK_API)
    public void testApisDontCrash_invalidInputSourceAndAxesCombinations() {
        InputDeviceIterators.iteratorOverEveryValidDeviceId((deviceId) -> {
            runScrollScenarios(deviceId, InputDevice.SOURCE_ROTARY_ENCODER, MotionEvent.AXIS_X);
        });
    }

    private void runScrollScenarios(int deviceId, int source, int axis) {
        mProvider.onScrollLimit(deviceId, source, axis, /* isStart= */ true);

        mProvider.onScrollProgress(deviceId, source, axis, /* deltaInPixels= */ 300);
        mProvider.onScrollProgress(deviceId, source, axis, /* deltaInPixels= */ 0);
        mProvider.onScrollProgress(deviceId, source, axis, /* deltaInPixels= */ -900);

        mProvider.onScrollLimit(deviceId, source, axis, /* isStart= */ false);

        mProvider.onSnapToItem(deviceId, source, axis);

        mProvider.onScrollLimit(deviceId, source, axis, /* isStart= */ false);
        mProvider.onScrollLimit(deviceId, source, axis, /* isStart= */ true);
    }
}
