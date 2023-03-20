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

package android.voiceinteraction.cts.services;

import static android.Manifest.permission.BIND_HOTWORD_DETECTION_SERVICE;
import static android.Manifest.permission.BIND_VISUAL_QUERY_DETECTION_SERVICE;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.voiceinteraction.cts.testcore.Helper.WAIT_TIMEOUT_IN_MS;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.SandboxedDetectionInitializer;
import android.service.voice.VisualQueryDetectionServiceFailure;
import android.service.voice.VisualQueryDetector;
import android.service.voice.VoiceInteractionService;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The {@link VoiceInteractionService} included a basic HotwordDetectionService for testing.
 */
public class CtsBasicVoiceInteractionService extends BaseVoiceInteractionService {
    private static final String TAG = "CtsBasicVoiceInteractionService";

    private final Handler mHandler;

    // The CountDownLatch waits for a service Availability change result
    private CountDownLatch mAvailabilityChangeLatch;
    // The CountDownLatch waits for a service detect or reject result
    private CountDownLatch mOnDetectRejectLatch;
    // The CountDownLatch waits for a service onError called
    private CountDownLatch mOnErrorLatch;
    // The CountDownLatch waits for vqds
    private CountDownLatch mOnQueryFinishRejectLatch;
    // The CountDownLatch waits for a service onHotwordDetectionServiceRestarted called
    private CountDownLatch mOnHotwordDetectionServiceRestartedLatch;

    private AlwaysOnHotwordDetector.EventPayload mDetectedResult;
    private HotwordRejectedResult mRejectedResult;
    private ArrayList<String> mStreamedQueries = new ArrayList<>();
    private String mCurrentQuery = "";

    public CtsBasicVoiceInteractionService() {
        HandlerThread handlerThread = new HandlerThread("CtsBasicVoiceInteractionService");
        handlerThread.start();
        mHandler = Handler.createAsync(handlerThread.getLooper());
    }

    public void createAlwaysOnHotwordDetectorNoHotwordDetectionService(boolean useExecutor,
            boolean runOnMainThread) {
        Log.i(TAG, "createAlwaysOnHotwordDetectorNoHotwordDetectionService");
        mServiceTriggerLatch = new CountDownLatch(1);

        AlwaysOnHotwordDetector.Callback callback = new AlwaysOnHotwordDetector.Callback() {
            @Override
            public void onAvailabilityChanged(int status) {
                Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                mAvailabilityStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mAvailabilityChangeLatch != null) {
                    mAvailabilityChangeLatch.countDown();
                }
            }

            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                // no-op
            }

            @Override
            public void onRejected(@NonNull HotwordRejectedResult result) {
                // no-op
            }

            @Override
            public void onError() {
                // no-op
            }

            @Override
            public void onRecognitionPaused() {
                // no-op
            }

            @Override
            public void onRecognitionResumed() {
                // no-op
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                // no-op
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                // no-op
            }
        };

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> runWithShellPermissionIdentity(() -> {
            mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetectorNoHotwordDetectionService(
                    callback, useExecutor);
            if (mServiceTriggerLatch != null) {
                mServiceTriggerLatch.countDown();
            }
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create AlwaysOnHotwordDetector.
     */
    public void createAlwaysOnHotwordDetector() {
        createAlwaysOnHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false);
    }

    /**
     * Create AlwaysOnHotwordDetector.
     */
    public void createAlwaysOnHotwordDetector(boolean useExecutor, boolean runOnMainThread) {
        Log.i(TAG, "createAlwaysOnHotwordDetector!!!!");
        mServiceTriggerLatch = new CountDownLatch(1);

        final AlwaysOnHotwordDetector.Callback callback = new AlwaysOnHotwordDetector.Callback() {
            @Override
            public void onAvailabilityChanged(int status) {
                Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                mAvailabilityStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mAvailabilityChangeLatch != null) {
                    mAvailabilityChangeLatch.countDown();
                }
            }

            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                Log.i(TAG, "onDetected");
                mDetectedResult = eventPayload;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onRejected(@NonNull HotwordRejectedResult result) {
                Log.i(TAG, "onRejected");
                mRejectedResult = result;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onError() {
                Log.i(TAG, "onError");
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnErrorLatch != null) {
                    mOnErrorLatch.countDown();
                }
            }

            @Override
            public void onRecognitionPaused() {
                Log.i(TAG, "onRecognitionPaused");
            }

            @Override
            public void onRecognitionResumed() {
                Log.i(TAG, "onRecognitionResumed");
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                if (status != HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
                    return;
                }
                mInitializedStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mServiceTriggerLatch != null) {
                    mServiceTriggerLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                Log.i(TAG, "onHotwordDetectionServiceRestarted");
                if (mOnHotwordDetectionServiceRestartedLatch != null) {
                    mOnHotwordDetectionServiceRestartedLatch.countDown();
                }
            }
        };

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> runWithShellPermissionIdentity(() -> {
            mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetector(callback, useExecutor);
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create an AlwaysOnHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createAlwaysOnHotwordDetectorWithoutManageHotwordDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateAlwaysOnHotwordDetector(mNoOpHotwordDetectorCallback)));
    }

    /**
     * Create a SoftwareHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createSoftwareHotwordDetectorWithoutManageHotwordDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateSoftwareHotwordDetector(mNoOpSoftwareDetectorCallback,
                        /* useExecutor= */ false)));
    }

    /**
     * Create an AlwaysOnHotwordDetector but doesn't hold MANAGE_HOTWORD_DETECTION but hold
     * BIND_HOTWORD_DETECTION_SERVICE.
     */
    public void createAlwaysOnHotwordDetectorHoldBindHotwordDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateAlwaysOnHotwordDetector(mNoOpHotwordDetectorCallback),
                BIND_HOTWORD_DETECTION_SERVICE));
    }

    public void createSoftwareHotwordDetector() {
        createSoftwareHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false);
    }

    public void createSoftwareHotwordDetector(boolean useExecutor, boolean runOnMainThread) {
        mServiceTriggerLatch = new CountDownLatch(1);

        final HotwordDetector.Callback callback = new HotwordDetector.Callback() {
            @Override
            public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                Log.i(TAG, "onDetected");
                mDetectedResult = eventPayload;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onError() {
                Log.i(TAG, "onError");
            }

            @Override
            public void onRecognitionPaused() {
                Log.i(TAG, "onRecognitionPaused");
            }

            @Override
            public void onRecognitionResumed() {
                Log.i(TAG, "onRecognitionResumed");
            }

            @Override
            public void onRejected(HotwordRejectedResult result) {
                Log.i(TAG, "onRejected");
                mRejectedResult = result;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mOnDetectRejectLatch != null) {
                    mOnDetectRejectLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceInitialized(int status) {
                Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                if (status != HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
                    return;
                }
                mInitializedStatus = status;
                setIsDetectorCallbackRunningOnMainThread(isRunningOnMainThread());
                if (mServiceTriggerLatch != null) {
                    mServiceTriggerLatch.countDown();
                }
            }

            @Override
            public void onHotwordDetectionServiceRestarted() {
                Log.i(TAG, "onHotwordDetectionServiceRestarted");
            }
        };

        final Handler handler = runOnMainThread ? new Handler(Looper.getMainLooper()) : mHandler;
        handler.post(() -> runWithShellPermissionIdentity(() -> {
            mSoftwareHotwordDetector = callCreateSoftwareHotwordDetector(callback, useExecutor);
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create a VisualQueryDetector but doesn't hold MANAGE_HOTWORD_DETECTION
     */
    public void createVisualQueryDetectorWithoutManageHotwordDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateVisualQueryDetector(mNoOpVisualQueryDetectorCallback)));
    }

    /**
     * Create a VisualQueryDetector but doesn't hold MANAGE_HOTWORD_DETECTION but hold
     * BIND_VISUAL_QUERY_DETECTION_SERVICE.
     */
    public void createVisualQueryDetectorHoldBindVisualQueryDetectionPermission() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(
                () -> callCreateVisualQueryDetector(mNoOpVisualQueryDetectorCallback),
                BIND_VISUAL_QUERY_DETECTION_SERVICE));
    }

    public void createVisualQueryDetector() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            VisualQueryDetector.Callback callback = new VisualQueryDetector.Callback() {
                @Override
                public void onQueryDetected(@NonNull String transcribedText) {
                    Log.i(TAG, "onQueryDetected");
                    mCurrentQuery += transcribedText;
                }

                @Override
                public void onQueryRejected() {
                    Log.i(TAG, "onQueryRejected");
                    // mStreamedQueries are used to store previously streamed queries for testing
                    // reason, regardless of the queries being rejected or finished.
                    mStreamedQueries.add(mCurrentQuery);
                    mCurrentQuery = "";
                    if (mOnQueryFinishRejectLatch != null) {
                        mOnQueryFinishRejectLatch.countDown();
                    }
                }

                @Override
                public void onQueryFinished() {

                    Log.i(TAG, "onQueryFinished");
                    mStreamedQueries.add(mCurrentQuery);
                    mCurrentQuery = "";
                    if (mOnQueryFinishRejectLatch != null) {
                        mOnQueryFinishRejectLatch.countDown();
                    }
                }

                @Override
                public void onVisualQueryDetectionServiceInitialized(int status) {
                    Log.i(TAG, "onVisualQueryDetectionServiceInitialized status = " + status);
                    if (status != SandboxedDetectionInitializer.INITIALIZATION_STATUS_SUCCESS) {
                        return;
                    }
                    mInitializedStatus = status;
                    if (mServiceTriggerLatch != null) {
                        mServiceTriggerLatch.countDown();
                    }
                }

                @Override
                public void onVisualQueryDetectionServiceRestarted() {
                    Log.i(TAG, "onVisualQueryDetectionServiceRestarted");
                }

                @Override
                public void onFailure(
                        VisualQueryDetectionServiceFailure visualQueryDetectionServiceFailure) {
                    Log.i(TAG, "onFailure visualQueryDetectionServiceFailure: "
                            + visualQueryDetectionServiceFailure);
                }

                @Override
                public void onUnknownFailure(String errorMessage) {
                    Log.i(TAG, "onUnknownFailure errorMessage: " + errorMessage);
                }
            };
            mVisualQueryDetector = callCreateVisualQueryDetector(callback);
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Create a CountDownLatch that is used to wait for onHotwordDetectionServiceRestarted() called
     */
    public void initOnHotwordDetectionServiceRestartedLatch() {
        mOnHotwordDetectionServiceRestartedLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for availability change
     */
    public void initAvailabilityChangeLatch() {
        mAvailabilityChangeLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that is used to wait for onDetected() or onRejected() result
     */
    public void initDetectRejectLatch() {
        mOnDetectRejectLatch = new CountDownLatch(1);
    }

    /**
     * Create a CountDownLatch that wait for onQueryFinished() or onQueryRejected() result
     */
    public void initQueryFinishRejectLatch(int numQueries) {
        mOnQueryFinishRejectLatch = new CountDownLatch(numQueries);
    }

    /**
     * Create a CountDownLatch that is used to wait for onError()
     */
    public void initOnErrorLatch() {
        mOnErrorLatch = new CountDownLatch(1);
    }

    /**
     * Returns the onDetected() result.
     */
    public AlwaysOnHotwordDetector.EventPayload getHotwordServiceOnDetectedResult() {
        return mDetectedResult;
    }

    /**
     * Returns the OnRejected() result.
     */
    public HotwordRejectedResult getHotwordServiceOnRejectedResult() {
        return mRejectedResult;
    }

    /**
     * Returns the OnQueryDetected() result.
     */
    public ArrayList<String> getStreamedQueriesResult() {
        return mStreamedQueries;
    }

    /**
     * Wait for onHotwordDetectionServiceRestarted() callback called.
     */
    public void waitOnHotwordDetectionServiceRestartedCalled() throws InterruptedException {
        Log.d(TAG, "waitOnHotwordDetectionServiceRestartedCalled(), latch="
                + mOnHotwordDetectionServiceRestartedLatch);
        if (mOnHotwordDetectionServiceRestartedLatch == null
                || !mOnHotwordDetectionServiceRestartedLatch.await(WAIT_TIMEOUT_IN_MS,
                TimeUnit.MILLISECONDS)) {
            mOnHotwordDetectionServiceRestartedLatch = null;
            throw new AssertionError(
                    "HotwordDetectionService onHotwordDetectionServiceRestarted not called.");
        }
        mOnHotwordDetectionServiceRestartedLatch = null;
    }

    /**
     * Wait for onAvailabilityChanged() callback called.
     */
    public void waitAvailabilityChangedCalled() throws InterruptedException {
        Log.d(TAG, "waitAvailabilityChangedCalled(), latch=" + mAvailabilityChangeLatch);
        if (mAvailabilityChangeLatch == null
                || !mAvailabilityChangeLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mAvailabilityChangeLatch = null;
            throw new AssertionError("HotwordDetectionService get ability fail.");
        }
        mAvailabilityChangeLatch = null;
    }

    /**
     * Return the result for onAvailabilityChanged().
     */
    public int getHotwordDetectionServiceAvailabilityResult() {
        return mAvailabilityStatus;
    }

    /**
     * Wait for onDetected() or OnRejected() callback called.
     */
    public void waitOnDetectOrRejectCalled() throws InterruptedException {
        Log.d(TAG, "waitOnDetectOrRejectCalled(), latch=" + mOnDetectRejectLatch);
        if (mOnDetectRejectLatch == null
                || !mOnDetectRejectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnDetectRejectLatch = null;
            throw new AssertionError("onDetected or OnRejected() fail.");
        }
        mOnDetectRejectLatch = null;
    }

    /**
     * Wait for onQueryFinished() or OnQueryRejected() callback called.
     */
    public void waitOnQueryFinishedRejectCalled() throws InterruptedException {
        Log.d(TAG, "waitOnQueryFinishedRejectCalled(), latch=" + mOnQueryFinishRejectLatch);
        if (mOnQueryFinishRejectLatch == null
                || !mOnQueryFinishRejectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnQueryFinishRejectLatch = null;
            throw new AssertionError("onDetected or OnRejected() fail.");
        }
        mOnQueryFinishRejectLatch = null;
    }

    /**
     * Wait for onError() callback called.
     */
    public void waitOnErrorCalled() throws InterruptedException {
        Log.d(TAG, "waitOnErrorCalled(), latch=" + mOnErrorLatch);
        if (mOnErrorLatch == null
                || !mOnErrorLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mOnErrorLatch = null;
            throw new AssertionError("OnError() fail.");
        }
        mOnErrorLatch = null;
    }
}
