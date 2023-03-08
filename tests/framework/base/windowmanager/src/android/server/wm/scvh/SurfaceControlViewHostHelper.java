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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import java.util.concurrent.CountDownLatch;

public class SurfaceControlViewHostHelper {
    private final String mTag;

    private final Object mLock = new Object();
    private boolean mIsAttached;

    private IAttachEmbeddedWindow mIAttachEmbeddedWindow;

    private SurfaceView mSurfaceView;

    private final int mDisplayId;

    private final long mDelayMs;
    private SurfaceControlViewHost.SurfacePackage mSurfacePackage;

    private final Size mInitialSize;

    private boolean mSurfaceCreated;

    private final CountDownLatch mReadyLatch;

    private final Context mContext;

    SurfaceControlViewHostHelper(String tag, CountDownLatch countDownLatch, Context context,
            long delayMs, Size initialSize) {
        mTag = tag;
        mContext = context;
        mDisplayId = context.getDisplayId();
        mDelayMs = delayMs;
        mInitialSize = initialSize;
        mReadyLatch = countDownLatch;
    }

    public SurfaceView attachSurfaceView(ViewGroup parent, ViewGroup.LayoutParams layoutParams) {
        mSurfaceView = new SurfaceView(mContext);
        mSurfaceView.getHolder().addCallback(mCallback);
        parent.addView(mSurfaceView, layoutParams);

        return mSurfaceView;
    }

    private final ServiceConnection mConnection = new ServiceConnection() {
        // Called when the connection with the service is established
        public void onServiceConnected(ComponentName className, IBinder service) {
            Log.d(mTag, "Service Connected");
            synchronized (mLock) {
                mIAttachEmbeddedWindow = IAttachEmbeddedWindow.Stub.asInterface(service);
            }
            if (isReadyToAttach()) {
                attachEmbedded();
            }
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(mTag, "Service Disconnected");
            mIAttachEmbeddedWindow = null;
            synchronized (mLock) {
                mIsAttached = false;
            }
        }
    };

    final SurfaceHolder.Callback mCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            synchronized (mLock) {
                mSurfaceCreated = true;
            }
            if (isReadyToAttach()) {
                attachEmbedded();
            }
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                int height) {
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        }
    };

    private boolean isReadyToAttach() {
        synchronized (mLock) {
            if (!mSurfaceCreated) {
                Log.d(mTag, "surface is not created");
            }
            if (mIAttachEmbeddedWindow == null) {
                Log.d(mTag, "Service is not attached");
            }
            if (mIsAttached) {
                Log.d(mTag, "Already attached");
            }

            return mSurfaceCreated && mIAttachEmbeddedWindow != null && !mIsAttached;
        }
    }

    private void attachEmbedded() {
        synchronized (mLock) {
            mIsAttached = true;
        }
        try {
            mSurfacePackage = mIAttachEmbeddedWindow.attachEmbedded(mSurfaceView.getHostToken(),
                    mInitialSize.getWidth(), mInitialSize.getHeight(), mDisplayId, mDelayMs);
            mSurfaceView.setChildSurfacePackage(mSurfacePackage);
        } catch (RemoteException e) {
            Log.e(mTag, "Failed to attach embedded window");
        }

        mReadyLatch.countDown();
    }

    public Pair<SurfaceControlViewHost.SurfacePackage, IAttachEmbeddedWindow> waitForReady() {
        return new Pair<>(mSurfacePackage, mIAttachEmbeddedWindow);
    }

    public SurfaceControlViewHost.SurfacePackage getSurfacePackage() {
        return mSurfacePackage;
    }

    public IAttachEmbeddedWindow getAttachedEmbeddedWindow() {
        return mIAttachEmbeddedWindow;
    }

    public void bindEmbeddedService() {
        Intent intent = new Intent(mContext, EmbeddedSCVHService.class);
        intent.setAction(EmbeddedSCVHService.class.getName());
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
}
