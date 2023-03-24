/*
 * Copyright (C) 2016 The Android Open Source Project
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
package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.HardwareBufferRenderer;
import android.graphics.Rect;
import android.graphics.RenderNode;
import android.hardware.HardwareBuffer;
import android.os.Handler;
import android.os.Looper;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;
import android.uirendering.cts.testinfrastructure.DrawActivity;
import android.uirendering.cts.testinfrastructure.Tracer;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.uirendering.cts.util.BitmapAsserter;
import android.view.AttachedSurfaceControl;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SynchronousPixelCopy;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class SurfaceViewTests extends ActivityTestBase {

    @Rule
    public final Tracer name = new Tracer();

    static final CanvasCallback sGreenCanvasCallback =
            new CanvasCallback((canvas, width, height) -> canvas.drawColor(Color.GREEN));
    static final CanvasCallback sWhiteCanvasCallback =
            new CanvasCallback((canvas, width, height) -> canvas.drawColor(Color.WHITE));
    static final CanvasCallback sRedCanvasCallback =
            new CanvasCallback((canvas, width, height) -> canvas.drawColor(Color.RED));

    private static class CanvasCallback implements SurfaceHolder.Callback {
        final CanvasClient mCanvasClient;
        private CountDownLatch mFirstDrawLatch;

        public CanvasCallback(CanvasClient canvasClient) {
            mCanvasClient = canvasClient;
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            Canvas canvas = holder.lockCanvas();
            mCanvasClient.draw(canvas, width, height);
            holder.unlockCanvasAndPost(canvas);

            if (mFirstDrawLatch != null) {
                mFirstDrawLatch.countDown();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
        }

        public void setFence(CountDownLatch fence) {
            mFirstDrawLatch = fence;
        }
    }

    static ObjectAnimator createInfiniteAnimator(Object target, String prop,
            float start, float end) {
        ObjectAnimator a = ObjectAnimator.ofFloat(target, prop, start, end);
        a.setRepeatMode(ObjectAnimator.REVERSE);
        a.setRepeatCount(ObjectAnimator.INFINITE);
        a.setDuration(200);
        a.setInterpolator(new LinearInterpolator());
        a.start();
        return a;
    }
    private final Screenshotter mScreenshotter = testPositionInfo -> {
        Bitmap source = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(source,
                testPositionInfo.screenOffset.x, testPositionInfo.screenOffset.y,
                TEST_WIDTH, TEST_HEIGHT);
    };

    // waitForRedraw checks that HWUI finished drawing but SurfaceFlinger may be backpressured, so
    // synchronizing by applying no-op transactions with UI draws instead.
    private void waitForScreenshottable() throws InterruptedException {
        AttachedSurfaceControl rootSurfaceControl =
                getActivity().getWindow().getRootSurfaceControl();

        CountDownLatch latch = new CountDownLatch(1);
        SurfaceControl stub = new SurfaceControl.Builder().setName("test").build();
        rootSurfaceControl.applyTransactionOnDraw(
                rootSurfaceControl.buildReparentTransaction(stub));
        rootSurfaceControl.applyTransactionOnDraw(
                new SurfaceControl.Transaction().reparent(stub, null)
                        .addTransactionCommittedListener(Runnable::run, latch::countDown));
        getActivity().waitForRedraw();
        latch.await(5, TimeUnit.SECONDS);
    }

    @FlakyTest(bugId = 244426304)
    @Test
    public void testMovingWhiteSurfaceView() {
        // A moving SurfaceViews with white content against a white background should be invisible
        ViewInitializer initializer = new ViewInitializer() {
            ObjectAnimator mAnimator;
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                mAnimator = createInfiniteAnimator(root, "translationY", 0, 50);

                SurfaceView surfaceViewA = new SurfaceView(view.getContext());
                surfaceViewA.getHolder().addCallback(sWhiteCanvasCallback);
                root.addView(surfaceViewA, new FrameLayout.LayoutParams(
                        90, 40, Gravity.START | Gravity.TOP));
            }
            @Override
            public void teardownView() {
                mAnimator.cancel();
            }
        };
        createTest()
                .addLayout(R.layout.frame_layout, initializer, true)
                .withScreenshotter(mScreenshotter)
                .runWithAnimationVerifier(new ColorVerifier(Color.WHITE, 0 /* zero tolerance */));
    }

    private static class SurfaceViewHelper implements ViewInitializer, Screenshotter, SurfaceHolder.Callback {
        private final CanvasClient mCanvasClient;
        private final CountDownLatch mFence = new CountDownLatch(1);
        private SurfaceView mSurfaceView = null;
        private boolean mHasSurface = false;

        public SurfaceViewHelper(CanvasClient canvasClient) {
            mCanvasClient = canvasClient;
        }

        @Override
        public Bitmap takeScreenshot(TestPositionInfo testPositionInfo) {
            SynchronousPixelCopy copy = new SynchronousPixelCopy();
            Bitmap dest = Bitmap.createBitmap(
                    TEST_WIDTH, TEST_HEIGHT, Config.ARGB_8888);
            Rect srcRect = new Rect(0, 0, TEST_WIDTH, TEST_HEIGHT);
            int copyResult = copy.request(mSurfaceView, srcRect, dest);
            Assert.assertEquals(PixelCopy.SUCCESS, copyResult);
            return dest;
        }

        @Override
        public void initializeView(View view) {
            FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
            mSurfaceView = new SurfaceView(view.getContext());
            mSurfaceView.getHolder().addCallback(this);
            onSurfaceViewCreated(mSurfaceView);
            root.addView(mSurfaceView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        }

        public SurfaceView getSurfaceView() {
            return mSurfaceView;
        }


        public boolean hasSurface() {
            return mHasSurface;
        }


        public void onSurfaceViewCreated(SurfaceView surfaceView) {

        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            mHasSurface = true;
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            // TODO: Remove the post() which is a temporary workaround for b/32484713
            mSurfaceView.post(() -> {
                Canvas canvas = holder.lockHardwareCanvas();
                mCanvasClient.draw(canvas, width, height);
                holder.unlockCanvasAndPost(canvas);
                mFence.countDown();
            });
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            mHasSurface = false;
        }

        public CountDownLatch getFence() {
            return mFence;
        }
    }

    @Test
    public void testSurfaceHolderHardwareCanvas() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            Assert.assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.GREEN);
        });
        createTest()
                .addLayout(R.layout.frame_layout, helper, true, helper.getFence())
                .withScreenshotter(helper)
                .runWithVerifier(new ColorVerifier(Color.GREEN, 0 /* zero tolerance */));
    }

    @Test
    public void testSurfaceViewHolePunchWithLayer() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            Assert.assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.GREEN);
        }
        ) {
            @Override
            public void onSurfaceViewCreated(SurfaceView surfaceView) {
                surfaceView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }
        };
        createTest()
                .addLayout(R.layout.frame_layout, helper, true, helper.getFence())
                .withScreenshotter(helper)
                .runWithVerifier(new ColorVerifier(Color.GREEN, 0 /* zero tolerance */));

    }

    @Test
    public void surfaceViewMediaLayer() {
        // Add a shared latch which will fire after both callbacks are complete.
        CountDownLatch latch = new CountDownLatch(2);
        sGreenCanvasCallback.setFence(latch);
        sRedCanvasCallback.setFence(latch);

        ViewInitializer initializer = new ViewInitializer() {
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                SurfaceView surfaceViewA = new SurfaceView(view.getContext());
                surfaceViewA.setZOrderMediaOverlay(true);
                surfaceViewA.getHolder().addCallback(sRedCanvasCallback);

                root.addView(surfaceViewA, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                SurfaceView surfaceViewB = new SurfaceView(view.getContext());
                surfaceViewB.getHolder().addCallback(sGreenCanvasCallback);

                root.addView(surfaceViewB, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer, true, latch)
                .withScreenshotter(mScreenshotter)
                // The red layer is the media overlay, so it must be on top.
                .runWithVerifier(new ColorVerifier(Color.RED, 0 /* zero tolerance */));
    }

    @Test
    public void surfaceViewBlendZAbove() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            Assert.assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.BLACK);
        }
        ) {
            @Override
            public void onSurfaceViewCreated(SurfaceView surfaceView) {
                surfaceView.setAlpha(0.25f);
                surfaceView.setZOrderOnTop(true);
            }
        };
        createTest()
                .addLayout(R.layout.frame_layout, helper, true, helper.getFence())
                .withScreenshotter(mScreenshotter)
                .runWithVerifier(new ColorVerifier(
                        Color.rgb(191, 191, 191), 1 /* blending tolerance */));
    }

    @Test
    public void surfaceViewBlendZBelow() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            Assert.assertTrue(canvas.isHardwareAccelerated());
            canvas.drawColor(Color.BLACK);
        }
        ) {
            @Override
            public void onSurfaceViewCreated(SurfaceView surfaceView) {
                surfaceView.setAlpha(0.25f);
            }
        };
        createTest()
                .addLayout(R.layout.frame_layout, helper, true, helper.getFence())
                .withScreenshotter(mScreenshotter)
                .runWithVerifier(new ColorVerifier(
                        Color.rgb(191, 191, 191), 1 /* blending tolerance */));
    }

    @Test
    public void surfaceViewSurfaceLifecycleFollowsVisibilityByDefault() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            canvas.drawColor(Color.BLACK);
        });

        DrawActivity activity = getActivity();
        try {
            activity.enqueueRenderSpecAndWait(R.layout.frame_layout, null, helper, false, false);
            assertTrue(helper.hasSurface());
            activity.runOnUiThread(() -> helper.getSurfaceView().setVisibility(View.INVISIBLE));
            activity.waitForRedraw();
            assertFalse(helper.hasSurface());
        } finally {
            activity.reset();
        }
    }

    @Test
    public void surfaceViewSurfaceLifecycleFollowsVisibility() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            canvas.drawColor(Color.BLACK);
        });

        DrawActivity activity = getActivity();
        try {
            activity.enqueueRenderSpecAndWait(R.layout.frame_layout, null, helper, false, false);
            assertTrue(helper.hasSurface());
            activity.runOnUiThread(() -> {
                helper.getSurfaceView()
                       .setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_VISIBILITY);
                helper.getSurfaceView().setVisibility(View.INVISIBLE);
            });
            activity.waitForRedraw();
            assertFalse(helper.hasSurface());
        } finally {
            activity.reset();
        }
    }

    @Test
    public void surfaceViewSurfaceLifecycleFollowsAttachment() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            canvas.drawColor(Color.BLACK);
        });

        DrawActivity activity = getActivity();
        try {
            activity.enqueueRenderSpecAndWait(R.layout.frame_layout, null, helper, false, false);
            assertTrue(helper.hasSurface());
            activity.runOnUiThread(() -> {
                helper.getSurfaceView()
                        .setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
                helper.getSurfaceView().setVisibility(View.INVISIBLE);
            });
            activity.waitForRedraw();
            assertTrue(helper.hasSurface());
        } finally {
            activity.reset();
        }
    }

    @Test
    public void surfaceViewSurfaceLifecycleFollowsAttachmentWithOverlaps() {
        // Add a shared latch which will fire after both callbacks are complete.
        CountDownLatch latch = new CountDownLatch(2);
        sGreenCanvasCallback.setFence(latch);
        sRedCanvasCallback.setFence(latch);

        ViewInitializer initializer = new ViewInitializer() {
            @Override
            public void initializeView(View view) {
                FrameLayout root = (FrameLayout) view.findViewById(R.id.frame_layout);
                SurfaceView surfaceViewA = new SurfaceView(view.getContext());
                surfaceViewA.setVisibility(View.VISIBLE);
                surfaceViewA.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_VISIBILITY);
                surfaceViewA.getHolder().addCallback(sRedCanvasCallback);

                root.addView(surfaceViewA, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));

                SurfaceView surfaceViewB = new SurfaceView(view.getContext());
                surfaceViewB.setVisibility(View.INVISIBLE);
                surfaceViewB.setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
                surfaceViewB.getHolder().addCallback(sGreenCanvasCallback);

                root.addView(surfaceViewB, new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT));
            }
        };

        createTest()
                .addLayout(R.layout.frame_layout, initializer, true, latch)
                .withScreenshotter(mScreenshotter)
                .runWithVerifier(new ColorVerifier(Color.RED, 0 /* zero tolerance */));
    }

    @Test
    public void surfaceViewSurfaceLifecycleChangesFromFollowsAttachmentToFollowsVisibility() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            canvas.drawColor(Color.BLACK);
        });

        DrawActivity activity = getActivity();
        try {
            activity.enqueueRenderSpecAndWait(R.layout.frame_layout, null, helper, false, false);
            assertTrue(helper.hasSurface());
            activity.runOnUiThread(() -> {
                helper.getSurfaceView()
                        .setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
                helper.getSurfaceView().setVisibility(View.INVISIBLE);
            });
            activity.waitForRedraw();
            assertTrue(helper.hasSurface());
            activity.runOnUiThread(() -> {
                helper.getSurfaceView()
                        .setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_VISIBILITY);
            });
            activity.waitForRedraw();
            assertFalse(helper.hasSurface());
        } finally {
            activity.reset();
        }
    }

    @Test
    public void surfaceViewSurfaceLifecycleChangesFromFollowsVisibilityToFollowsAttachment() {
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            canvas.drawColor(Color.BLACK);
        });

        DrawActivity activity = getActivity();
        try {
            activity.enqueueRenderSpecAndWait(R.layout.frame_layout, null, helper, false, false);
            assertTrue(helper.hasSurface());
            activity.runOnUiThread(() -> {
                helper.getSurfaceView()
                        .setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_VISIBILITY);
                helper.getSurfaceView().setVisibility(View.INVISIBLE);
            });
            activity.waitForRedraw();
            assertFalse(helper.hasSurface());
            activity.runOnUiThread(() -> {
                helper.getSurfaceView()
                        .setSurfaceLifecycle(SurfaceView.SURFACE_LIFECYCLE_FOLLOWS_ATTACHMENT);
            });
            activity.waitForRedraw();
            assertTrue(helper.hasSurface());
        } finally {
            activity.reset();
        }
    }

    @Test
    public void surfaceViewAppliesTransactionsToFrame()
            throws InterruptedException {
        SurfaceControl blueLayer = new SurfaceControl.Builder()
                .setName("SurfaceViewTests")
                .setHidden(false)
                .build();
        SurfaceViewHelper helper = new SurfaceViewHelper((canvas, width, height) -> {
            Assert.assertNotNull(canvas);
            canvas.drawColor(Color.RED);
        });

        DrawActivity activity = getActivity();
        try {
            TestPositionInfo testInfo = activity
                    .enqueueRenderSpecAndWait(R.layout.frame_layout, null, helper, true, false);
            assertTrue(helper.hasSurface());
            helper.getFence().await(3, TimeUnit.SECONDS);
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch transactionCommitted = new CountDownLatch(1);
            activity.runOnUiThread(() -> {
                SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()
                        .reparent(blueLayer, helper.getSurfaceView().getSurfaceControl())
                        .setLayer(blueLayer, 1)
                        .addTransactionCommittedListener(Runnable::run,
                                transactionCommitted::countDown);

                int width = helper.getSurfaceView().getWidth();
                int height = helper.getSurfaceView().getHeight();
                long usage = HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
                        | HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
                        | HardwareBuffer.USAGE_COMPOSER_OVERLAY;
                HardwareBuffer buffer = HardwareBuffer.create(
                        width, height, HardwareBuffer.RGBA_8888, 1, usage);
                HardwareBufferRenderer renderer = new HardwareBufferRenderer(buffer);
                RenderNode node = new RenderNode("content");
                node.setPosition(0, 0, width, height);
                Canvas canvas = node.beginRecording();
                canvas.drawColor(Color.BLUE);
                node.endRecording();
                renderer.setContentRoot(node);
                Handler handler = new Handler(Looper.getMainLooper());
                renderer.obtainRenderRequest().draw(Executors.newSingleThreadExecutor(), result -> {
                    handler.post(() -> {
                        transaction.setBuffer(blueLayer, buffer, result.getFence());
                        helper.getSurfaceView().applyTransactionToFrame(transaction);
                        latch.countDown();
                    });
                });
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            // Wait for an additional second to ensure that the transaction reparenting the blue
            // layer is not applied.
            assertFalse(transactionCommitted.await(1, TimeUnit.SECONDS));
            Bitmap screenshot = mScreenshotter.takeScreenshot(testInfo);
            BitmapAsserter asserter =
                    new BitmapAsserter(this.getClass().getSimpleName(), name.getMethodName());
            asserter.assertBitmapIsVerified(
                    screenshot, new ColorVerifier(Color.RED, 0), getName(), "");
            activity.runOnUiThread(() -> {
                SurfaceHolder holder = helper.getSurfaceView().getHolder();
                Canvas canvas = holder.lockHardwareCanvas();
                canvas.drawColor(Color.GREEN);
                holder.unlockCanvasAndPost(canvas);
            });
            assertTrue(transactionCommitted.await(1, TimeUnit.SECONDS));
            screenshot = mScreenshotter.takeScreenshot(testInfo);
            // Now that a new frame was drawn, the blue layer should be overlaid now.
            asserter.assertBitmapIsVerified(
                    screenshot, new ColorVerifier(Color.BLUE, 0), getName(), "");
        } finally {
            activity.reset();
        }
    }

    // Regression test for b/269113414
    @Test
    public void surfaceViewOffscreenDoesNotPeekThrough() throws InterruptedException {

        // Add a shared latch which will fire after both callbacks are complete.
        CountDownLatch latch = new CountDownLatch(2);
        sGreenCanvasCallback.setFence(latch);
        sRedCanvasCallback.setFence(latch);

        DrawActivity activity = getActivity();

        SurfaceView surfaceViewRed = new SurfaceView(activity);
        surfaceViewRed.getHolder().addCallback(sRedCanvasCallback);
        SurfaceView surfaceViewGreen = new SurfaceView(activity);
        surfaceViewGreen.setZOrderMediaOverlay(true);
        surfaceViewGreen.getHolder().addCallback(sGreenCanvasCallback);

        int width = activity.getWindow().getDecorView().getWidth();
        int height = activity.getWindow().getDecorView().getHeight();

        ViewInitializer initializer = (View view) -> {
            FrameLayout root = view.findViewById(R.id.frame_layout);
            root.addView(surfaceViewRed, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));

            root.addView(surfaceViewGreen, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        };

        try {
            TestPositionInfo testInfo = activity.enqueueRenderSpecAndWait(
                    R.layout.frame_layout, null, initializer, true, false);
            latch.await(5, TimeUnit.SECONDS);

            BitmapAsserter asserter =
                    new BitmapAsserter(this.getClass().getSimpleName(), name.getMethodName());

            // Layout the SurfaceView way offscreen which would cause it to get quick rejected.
            activity.runOnUiThread(() -> {
                surfaceViewGreen.layout(
                        width * 2,
                        height * 2,
                        width * 2 + TEST_WIDTH,
                        height * 2 + TEST_HEIGHT);
            });
            waitForScreenshottable();
            Bitmap screenshot = mScreenshotter.takeScreenshot(testInfo);
            asserter.assertBitmapIsVerified(
                    screenshot,
                    new ColorVerifier(Color.RED, 0), getName(),
                    "Verifying red SurfaceControl");
        } finally {
            activity.reset();
        }
    }


}
