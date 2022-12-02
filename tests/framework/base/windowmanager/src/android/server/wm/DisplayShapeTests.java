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

package android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.RoundedCorner.POSITION_BOTTOM_LEFT;
import static android.view.RoundedCorner.POSITION_TOP_LEFT;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.graphics.Path;
import android.graphics.RectF;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.DisplayShape;
import android.view.RoundedCorner;
import android.view.View;

import org.junit.Before;
import org.junit.Test;

/**
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:DisplayShapeTests
 */
@Presubmit
@android.server.wm.annotation.Group3
public class DisplayShapeTests extends WindowManagerTestBase {
    private static final String TAG = "DisplayShapeTests";

    private Display mDisplay;

    @Before
    public void setUp() throws Exception {
        mDisplay = mDm.getDisplay(DEFAULT_DISPLAY);
    }

    @Test
    public void testNonNull() {
        final DisplayShape shape = mDisplay.getShape();
        assertNotNull(shape);

        final Path path = shape.getPath();
        assertNotNull(path);
        assertFalse(path.isEmpty());
    }

    @Test
    public void testDisplayShapeConfig() {
        boolean hasRoundedCorners = false;
        RoundedCorner r;
        for (int i = POSITION_TOP_LEFT; i <= POSITION_BOTTOM_LEFT; i++) {
            r = mDisplay.getRoundedCorner(POSITION_TOP_LEFT);
            if (r == null) {
                continue;
            }
            hasRoundedCorners = true;
            break;
        }

        final Path path = mDisplay.getShape().getPath();
        final boolean isRect = path.isRect(null);

        // By default, if the config for display shape is not set, the returned shape will be
        // rectangular. The config must be set if the display has rounded corners.
        assertThat("The display has rounded corners but the returned path is a rectangular shape."
                + " Please set the config(config_mainDisplayShape) for display shape.",
                isRect, not(hasRoundedCorners));
    }

    @Test
    public void testDisplayShapeFromWindowInsets() {
        DisplayShapeTests.TestActivity activity =
                startActivity(DisplayShapeTests.TestActivity.class, DEFAULT_DISPLAY);

        final DisplayShape fromDisplay = mDisplay.getShape();
        final View decorView = activity.getWindow().getDecorView();
        final DisplayShape fromInsets = decorView.getRootWindowInsets().getDisplayShape();

        final int[] location = new int[2];
        decorView.getLocationOnScreen(location);
        final RectF boundsFromDisplay = getBoundsFromPath(fromDisplay.getPath());
        boundsFromDisplay.offset(-location[0], -location[1]);
        final RectF boundsFromView = getBoundsFromPath(fromInsets.getPath());
        assertEquals(boundsFromView, boundsFromDisplay);
    }

    @Test
    public void testDisplayShapeOnVirtualDisplay() {
        try (MultiDisplayTestBase.VirtualDisplaySession session =
                     new MultiDisplayTestBase.VirtualDisplaySession()) {
            // Setup a simulated display.
            WindowManagerState.DisplayContent dc = session.setSimulateDisplay(true).createDisplay();
            Display simulatedDisplay = mContext.getSystemService(DisplayManager.class)
                    .getDisplay(dc.mId);
            final DisplayShape shape = simulatedDisplay.getShape();
            assertNotNull(shape);
            assertTrue(shape.getPath().isRect(null));

            final int displayWidth = dc.getDisplayRect().width();
            final int displayHeight = dc.getDisplayRect().height();
            final RectF expectRect = new RectF(0, 0, displayWidth, displayHeight);
            final RectF actualRect = getBoundsFromPath(shape.getPath());
            assertEquals(expectRect, actualRect);
        }
    }

    private static RectF getBoundsFromPath(Path path) {
        final RectF rect = new RectF();
        path.computeBounds(rect, false);
        return rect;
    }

    public static class TestActivity extends FocusableActivity {
    }
}
