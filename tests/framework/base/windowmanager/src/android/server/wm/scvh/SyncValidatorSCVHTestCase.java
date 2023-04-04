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

package android.server.wm.scvh;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.os.SystemProperties;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.ISurfaceValidatorTestCase;
import android.view.cts.surfacevalidator.PixelChecker;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.window.SurfaceSyncGroup;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SyncValidatorSCVHTestCase implements ISurfaceValidatorTestCase {
    private static final String TAG = "SCVHSyncValidatorTestCase";

    private static final int HW_TIMEOUT_MULTIPLIER =
            SystemProperties.getInt("ro.hw_timeout_multiplier", 1);

    private final Size[] mSizes = new Size[]{new Size(500, 500), new Size(700, 400),
            new Size(300, 800), new Size(200, 200)};
    private int mLastSizeIndex = 0;

    private final long mDelayMs;
    private final boolean mOverrideDefaultDuration;

    private SurfaceControlViewHostHelper mSurfaceControlViewHostHelper;

    private IAttachEmbeddedWindow mIAttachEmbeddedWindow;

    private Handler mHandler;
    private TextView mTextView;
    private SurfaceView mSurfaceView;
    private SurfacePackage mSurfacePackage;

    private final CountDownLatch mReadyToStart = new CountDownLatch(1);

    public SyncValidatorSCVHTestCase(long delayMs, boolean overrideDefaultDuration) {
        mDelayMs = delayMs;
        mOverrideDefaultDuration = overrideDefaultDuration;
    }

    private final Runnable mResizeWithSurfaceSyncGroup = new Runnable() {
        @Override
        public void run() {
            Size size = mSizes[mLastSizeIndex % mSizes.length];

            Runnable svResizeRunnable = () -> {
                ViewGroup.LayoutParams svParams = mSurfaceView.getLayoutParams();
                svParams.width = size.getWidth();
                svParams.height = size.getHeight();
                mSurfaceView.setLayoutParams(svParams);

                mTextView.setText(size.getWidth() + "x" + size.getHeight());
            };

            Runnable embeddedResizeRunnable = () -> {
                try {
                    final WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                            size.getWidth(), size.getHeight(),
                            WindowManager.LayoutParams.TYPE_APPLICATION, 0,
                            PixelFormat.TRANSPARENT);
                    mIAttachEmbeddedWindow.relayout(lp);
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to call relayout for embedded window");
                }
            };

            SurfaceSyncGroup syncGroup = new SurfaceSyncGroup(TAG);
            syncGroup.add(mSurfaceView.getRootSurfaceControl(), svResizeRunnable);
            syncGroup.add(mSurfacePackage, embeddedResizeRunnable);
            syncGroup.markSyncReady();

            mLastSizeIndex++;

            mHandler.postDelayed(this, mDelayMs + 50);
        }
    };

    @Override
    public PixelChecker getChecker() {
        return new PixelChecker(Color.BLACK) {
            @Override
            public boolean checkPixels(int matchingPixelCount, int width, int height) {
                // Content has been set up yet.
                if (mSurfacePackage == null) {
                    return true;
                }
                return matchingPixelCount == 0;
            }
        };
    }

    @Override
    public void start(Context context, FrameLayout parent) {
        mHandler = new Handler(Looper.getMainLooper());

        mSurfaceControlViewHostHelper = new SurfaceControlViewHostHelper(TAG, mReadyToStart,
                context, mDelayMs, mSizes[0]);

        mSurfaceControlViewHostHelper.bindEmbeddedService();

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(mSizes[0].getWidth(),
                mSizes[0].getHeight());
        layoutParams.gravity = Gravity.CENTER;

        mSurfaceView = mSurfaceControlViewHostHelper.attachSurfaceView(parent, layoutParams);

        mTextView = new TextView(context);
        mTextView.setTextColor(Color.GREEN);
        mTextView.setText(mSizes[0].getWidth() + "x" + mSizes[0].getHeight());
        FrameLayout.LayoutParams txtParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        txtParams.gravity = Gravity.TOP | Gravity.LEFT;
        parent.addView(mTextView, txtParams);
    }

    @Override
    public void waitForReady() {
        boolean ready = false;
        try {
            ready = mReadyToStart.await(3 * HW_TIMEOUT_MULTIPLIER, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for SCVH to attach");
        }

        assertTrue("Failed to attach SCVH", ready);

        mSurfacePackage = mSurfaceControlViewHostHelper.getSurfacePackage();
        mIAttachEmbeddedWindow = mSurfaceControlViewHostHelper.getAttachedEmbeddedWindow();

        mHandler.post(mResizeWithSurfaceSyncGroup);
    }

    @Override
    public void end() {
        mHandler.removeCallbacks(mResizeWithSurfaceSyncGroup);
    }

    @Override
    public boolean hasAnimation() {
        return !mOverrideDefaultDuration;
    }
}
