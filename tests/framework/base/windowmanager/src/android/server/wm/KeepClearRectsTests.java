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

package android.server.wm;

import static android.server.wm.app.Components.KEEP_CLEAR_RECTS_ACTIVITY;
import static android.server.wm.app.Components.KeepClearRectsActivity.EXTRA_KEEP_CLEAR_RECTS;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.systemBars;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import static java.util.Collections.EMPTY_LIST;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.cts.R;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;

import androidx.test.filters.FlakyTest;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Presubmit
@FlakyTest(detail = "Promote once confirmed non-flaky")
public class KeepClearRectsTests extends WindowManagerTestBase {
    private static final List<Rect> TEST_KEEP_CLEAR_RECTS =
            Arrays.asList(new Rect(0, 0, 25, 25),
                          new Rect(30, 0, 50, 25),
                          new Rect(25, 25, 50, 50),
                          new Rect(10, 30, 20, 50));
    private static final List<Rect> TEST_KEEP_CLEAR_RECTS_2 =
            Arrays.asList(new Rect(55, 0, 75, 15),
                          new Rect(50, 15, 60, 25),
                          new Rect(75, 25, 90, 50),
                          new Rect(90, 0, 100, 10));
    private static final Rect TEST_VIEW_BOUNDS = new Rect(0, 0, 100, 100);
    private static final String USE_KEEP_CLEAR_ATTR_LAYOUT = "use_keep_clear_attr_layout";

    private TestActivitySession<TestActivity> mTestSession;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTestSession = createManagedTestActivitySession();
    }

    @Test
    public void testSetPreferKeepClearAttr() {
        final Intent intent = new Intent(mContext, TestActivity.class);
        intent.putExtra(USE_KEEP_CLEAR_ATTR_LAYOUT, true);
        mTestSession.launchTestActivityOnDisplaySync(null, intent, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        // To be kept in sync with res/layout/keep_clear_attr_activity
        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        assertSameElements(Arrays.asList(keepClearRect), getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testSetPreferKeepClearSingleView() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        final View v = createTestViewInActivity(activity, keepClearRect);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));

        assertSameElements(Arrays.asList(keepClearRect), getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testSetPreferKeepClearTwoViews() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        final View v = createTestViewInActivity(activity, keepClearRect);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));

        assertSameElements(Arrays.asList(keepClearRect), getKeepClearRectsForActivity(activity));

        final Rect keepClearRect2 = new Rect(25, 25, 50, 50);
        final View v2 = createTestViewInActivity(activity, keepClearRect2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClear(true));

        assertSameElements(Arrays.asList(keepClearRect, keepClearRect2),
                getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testSetMultipleKeepClearRectsSingleView() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final View v = createTestViewInActivity(activity);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));

        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testSetMultipleKeepClearRectsTwoViews() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final View v1 = createTestViewInActivity(activity);
        final View v2 = createTestViewInActivity(activity);
        mTestSession.runOnMainSyncAndWait(() -> {
            v1.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
            v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS_2);
        });

        final List<Rect> expected = new ArrayList(TEST_KEEP_CLEAR_RECTS);
        expected.addAll(TEST_KEEP_CLEAR_RECTS_2);
        assertSameElements(expected, getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testIsPreferKeepClearSingleView() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        assertFalse(v.isPreferKeepClear());
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertTrue(v.isPreferKeepClear());

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(false));
        assertFalse(v.isPreferKeepClear());
    }

    @Test
    public void testGetPreferKeepClearRectsSingleView() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        assertSameElements(EMPTY_LIST, v.getPreferKeepClearRects());
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        assertSameElements(TEST_KEEP_CLEAR_RECTS, v.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS_2));
        assertSameElements(TEST_KEEP_CLEAR_RECTS_2, v.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(EMPTY_LIST));
        assertSameElements(EMPTY_LIST, v.getPreferKeepClearRects());
    }

    @Test
    public void testGettersPreferKeepClearRectsTwoViews() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds1 = new Rect(0, 0, 60, 60);
        final Rect viewBounds2 = new Rect(0, 0, 90, 90);
        final View v1 = createTestViewInActivity(activity, viewBounds1);
        final View v2 = createTestViewInActivity(activity, viewBounds2);
        mTestSession.runOnMainSyncAndWait(() -> {
            v1.setPreferKeepClear(true);
            v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });

        assertTrue(v1.isPreferKeepClear());
        assertSameElements(TEST_KEEP_CLEAR_RECTS, v2.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(false));
        assertFalse(v1.isPreferKeepClear());
        assertSameElements(TEST_KEEP_CLEAR_RECTS, v2.getPreferKeepClearRects());

        mTestSession.runOnMainSyncAndWait(() -> {
            v1.setPreferKeepClear(true);
            v2.setPreferKeepClearRects(EMPTY_LIST);
        });
        assertTrue(v1.isPreferKeepClear());
        assertSameElements(EMPTY_LIST, v2.getPreferKeepClearRects());
    }

    @Test
    public void testSetPreferKeepClearOverridesMultipleRects() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(false));
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testIgnoreKeepClearRectsFromGoneViews() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.GONE));
        assertSameElements(EMPTY_LIST, getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.VISIBLE));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setPreferKeepClear(false);
            v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.GONE));
        assertSameElements(EMPTY_LIST, getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.VISIBLE));
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity));

        final Rect viewBounds2 = new Rect(60, 60, 90, 90);
        final View v2 = createTestViewInActivity(activity, viewBounds2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClear(true));

        final List<Rect> expected = new ArrayList(TEST_KEEP_CLEAR_RECTS);
        expected.add(viewBounds2);
        assertSameElements(expected, getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> v.setVisibility(View.GONE));
        assertSameElements(Arrays.asList(viewBounds2), getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setVisibility(View.VISIBLE);
            v2.setVisibility(View.GONE);
        });
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setVisibility(View.VISIBLE);
            v2.setVisibility(View.VISIBLE);
        });
        assertSameElements(expected, getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testIgnoreKeepClearRectsFromDetachedViews() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity));

        mTestSession.runOnMainSyncAndWait(() -> ((ViewGroup) v.getParent()).removeView(v));
        assertSameElements(EMPTY_LIST, getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testKeepClearRectsGetTranslatedToWindowSpace() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(30, 30, 60, 60);
        final View v = createTestViewInActivity(activity, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        final List<Rect> expected = new ArrayList();
        for (Rect r : TEST_KEEP_CLEAR_RECTS) {
            Rect newRect = new Rect(r);
            newRect.offset(viewBounds.left, viewBounds.top);
            expected.add(newRect);
        }

        assertSameElements(expected, getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testSetKeepClearRectsOnDisplaySingleWindow() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect keepClearRect = new Rect(0, 0, 25, 25);
        final View v = createTestViewInActivity(activity, keepClearRect);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(keepClearRect), getKeepClearRectsOnDefaultDisplay());

        mTestSession.runOnMainSyncAndWait(() -> {
            v.setPreferKeepClear(false);
            v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS);
        });
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsOnDefaultDisplay());

        activity.finishAndRemoveTask();
        mWmState.waitAndAssertActivityRemoved(activity.getComponentName());
        assertSameElements(EMPTY_LIST, getKeepClearRectsOnDefaultDisplay());
    }


    @Test
    public void testKeepClearRectsOnDisplayTwoWindows() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 25, 25);
        final View v1 = createTestViewInActivity(activity, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity));

        final String title = "KeepCleasrRectsTestWindow";
        mTestSession.runOnMainSyncAndWait(() -> {
            final View testView = new View(activity);
            testView.setPreferKeepClear(true);
            testView.setBackgroundColor(Color.argb(20, 255, 0, 0));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.gravity = Gravity.TOP | Gravity.START;
            params.width = 50;
            params.height = 50;
            params.setTitle(title);
            activity.getWindowManager().addView(testView, params);
        });
        mWmState.waitAndAssertWindowSurfaceShown(title, true);

        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity));
    }

    @Test
    public void testKeepClearRectsOnDisplayTwoFullscreenActivities() {
        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity1 = mTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 25, 25);
        final View v1 = createTestViewInActivity(activity1, viewBounds);
        mTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsForActivity(activity1));

        final TestActivitySession<TranslucentTestActivity> translucentTestSession =
                createManagedTestActivitySession();
        translucentTestSession.launchTestActivityOnDisplaySync(
                TranslucentTestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity2 = translucentTestSession.getActivity();

        final View v2 = createTestViewInActivity(activity2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity2));

        mWmState.assertVisibility(activity1.getComponentName(), true);
        mWmState.assertVisibility(activity2.getComponentName(), true);

        // Since both activities are fullscreen, WM only takes the keep clear areas from the top one
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsOnDefaultDisplay());
    }

    @Test
    public void testDisplayHasKeepClearRectsOnlyFromVisibleWindows() {
        final TestActivitySession<TranslucentTestActivity> translucentTestSession =
                createManagedTestActivitySession();
        translucentTestSession.launchTestActivityOnDisplaySync(
                TranslucentTestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity1 = translucentTestSession.getActivity();

        final Rect viewBounds = new Rect(0, 0, 25, 25);
        final View v1 = createTestViewInActivity(activity1, viewBounds);
        translucentTestSession.runOnMainSyncAndWait(() -> v1.setPreferKeepClear(true));
        assertSameElements(Arrays.asList(viewBounds), getKeepClearRectsOnDefaultDisplay());

        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity2 = mTestSession.getActivity();
        final View v2 = createTestViewInActivity(activity2);
        mTestSession.runOnMainSyncAndWait(() -> v2.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS));
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsForActivity(activity2));

        mWmState.waitAndAssertVisibilityGone(activity1.getComponentName());
        mWmState.assertVisibility(activity2.getComponentName(), true);

        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsOnDefaultDisplay());
    }

    @Test
    public void testDisplayHasKeepClearAreasFromTwoActivitiesInSplitscreen() {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());

        getLaunchActivityBuilder()
            .setUseInstrumentation()
            .setIntentExtra(extra -> {
                extra.putParcelableArrayList(EXTRA_KEEP_CLEAR_RECTS,
                        new ArrayList(TEST_KEEP_CLEAR_RECTS));
            })
            .setTargetActivity(KEEP_CLEAR_RECTS_ACTIVITY)
            .execute();

        waitAndAssertResumedActivity(KEEP_CLEAR_RECTS_ACTIVITY, KEEP_CLEAR_RECTS_ACTIVITY
                + " must be resumed");
        assertSameElements(TEST_KEEP_CLEAR_RECTS, getKeepClearRectsOnDefaultDisplay());

        mTestSession.launchTestActivityOnDisplaySync(TestActivity.class, DEFAULT_DISPLAY);
        final TestActivity activity = mTestSession.getActivity();

        final View v = createTestViewInActivity(activity);
        mTestSession.runOnMainSyncAndWait(() -> v.setPreferKeepClearRects(TEST_KEEP_CLEAR_RECTS_2));

        mWmState.assertVisibility(activity.getComponentName(), true);

        assertSameElements(TEST_KEEP_CLEAR_RECTS_2, getKeepClearRectsOnDefaultDisplay());

        moveActivitiesToSplitScreen(activity.getComponentName(), KEEP_CLEAR_RECTS_ACTIVITY);

        waitAndAssertResumedActivity(activity.getComponentName(), activity.getComponentName()
                + " must be resumed");
        waitAndAssertResumedActivity(KEEP_CLEAR_RECTS_ACTIVITY, KEEP_CLEAR_RECTS_ACTIVITY
                + " must be resumed");

        mWmState.assertVisibility(activity.getComponentName(), true);
        mWmState.assertVisibility(KEEP_CLEAR_RECTS_ACTIVITY, true);

        assertSameElements(TEST_KEEP_CLEAR_RECTS_2,
                getKeepClearRectsForActivity(activity));

        final List<Rect> expected = new ArrayList();
        final WindowManagerState.WindowState windowState =
                mWmState.getWindowState(KEEP_CLEAR_RECTS_ACTIVITY);
        for (Rect r : TEST_KEEP_CLEAR_RECTS) {
            Rect rectInScreenSpace = new Rect(r);
            rectInScreenSpace.offset(windowState.getFrame().left, windowState.getFrame().top);
            expected.add(rectInScreenSpace);
        }
        assertSameElements(expected,
                getKeepClearRectsForActivityComponent(KEEP_CLEAR_RECTS_ACTIVITY));
        expected.addAll(TEST_KEEP_CLEAR_RECTS_2);
        assertSameElements(expected, getKeepClearRectsOnDefaultDisplay());
    }

    private View createTestViewInActivity(TestActivity activity) {
        return createTestViewInActivity(activity, new Rect(0, 0, 100, 100));
    }

    private View createTestViewInActivity(TestActivity activity, Rect viewBounds) {
        final View newView = new View(activity);
        final LayoutParams params = new LayoutParams(viewBounds.width(), viewBounds.height());
        params.leftMargin = viewBounds.left;
        params.topMargin = viewBounds.top;
        mTestSession.runOnMainSyncAndWait(() -> {
            activity.addView(newView, params);
        });
        return newView;
    }

    private List<Rect> getKeepClearRectsForActivity(Activity activity) {
        return getKeepClearRectsForActivityComponent(activity.getComponentName());
    }

    private List<Rect> getKeepClearRectsForActivityComponent(ComponentName activityComponent) {
        mWmState.computeState();
        return mWmState.getWindowState(activityComponent).getKeepClearRects();
    }

    private List<Rect> getKeepClearRectsOnDefaultDisplay() {
        mWmState.computeState();
        return mWmState.getDisplay(DEFAULT_DISPLAY).getKeepClearRects();
    }

    public static class TestActivity extends FocusableActivity {
        private RelativeLayout mRootView;

        public void addView(View v, LayoutParams params) {
            mRootView.addView(v, params);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (getIntent().getBooleanExtra(USE_KEEP_CLEAR_ATTR_LAYOUT, false)) {
                setContentView(R.layout.keep_clear_attr_activity);
            } else {
                setContentView(R.layout.keep_clear_rects_activity);
            }
            mRootView = findViewById(R.id.root);

            getWindow().setDecorFitsSystemWindows(false);
            getWindow().getInsetsController().hide(systemBars());
        }
    }

    public static class TranslucentTestActivity extends TestActivity {}

    private static <T> void assertSameElements(List<T> expected, List<T> actual) {
        if (!hasSameElements(expected, actual)) {
            assertEquals(expected, actual);
        }
    }

    private static <T> boolean hasSameElements(List<T> fst, List<T> snd) {
        if (fst.size() != snd.size()) return false;

        for (T a : fst) {
            if (!snd.contains(a)) {
                return false;
            }
        }
        return true;
    }
}
