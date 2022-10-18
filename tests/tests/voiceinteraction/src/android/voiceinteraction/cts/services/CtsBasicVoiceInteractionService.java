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

import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import android.os.Handler;
import android.os.HandlerThread;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.VoiceInteractionService;
import android.util.Log;
import android.voiceinteraction.cts.testcore.Helper;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The {@link VoiceInteractionService} included a basic HotwordDetectionService for testing.
 */
public class CtsBasicVoiceInteractionService extends BaseVoiceInteractionService {
    private static final String TAG = "CtsBasicVoiceInteractionService";

    private final Handler mHandler;
    private AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;
    // The CountDownLatch waits for a service Availability change result
    private CountDownLatch mAvailabilityChangeLatch;

    public CtsBasicVoiceInteractionService() {
        HandlerThread handlerThread = new HandlerThread("CtsBasicVoiceInteractionService");
        handlerThread.start();
        mHandler = Handler.createAsync(handlerThread.getLooper());
    }

    /**
     * Create AlwaysOnHotwordDetector.
     */
    public void createAlwaysOnHotwordDetector() {
        mServiceTriggerLatch = new CountDownLatch(1);
        mHandler.post(() -> runWithShellPermissionIdentity(() -> {
            mAlwaysOnHotwordDetector = callCreateAlwaysOnHotwordDetector();
        }, MANAGE_HOTWORD_DETECTION));
    }

    /**
     * Returns the Service's AlwaysOnHotwordDetector.
     */
    public AlwaysOnHotwordDetector getAlwaysOnHotwordDetector() {
        return mAlwaysOnHotwordDetector;
    }

    /**
     * Create a CountDownLatch that is used to wait availability change
     */
    public void initAvailabilityChangeLatch() {
        mAvailabilityChangeLatch = new CountDownLatch(1);
    }

    /**
     * Wait for onHotwordDetectionServiceInitialized() and return the result.
     */
    public void waitHotwordDetectionServiceInitializedResult()
            throws InterruptedException {
        Log.d(TAG, "waitAndGetHotwordDetectionServiceInitializedResult(), latch="
                + mServiceTriggerLatch);
        if (mServiceTriggerLatch == null
                || !mServiceTriggerLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            mServiceTriggerLatch = null;
            throw new AssertionError("HotwordDetectionService initialized fail.");
        }
        mServiceTriggerLatch = null;
    }

    /**
     * Return the result for onHotwordDetectionServiceInitialized().
     */
    public int getHotwordDetectionServiceInitializedResult() {
        return mInitializedStatus;
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

    private AlwaysOnHotwordDetector callCreateAlwaysOnHotwordDetector() {
        Log.i(TAG, "callCreateAlwaysOnHotwordDetector()");
        try {
            return createAlwaysOnHotwordDetector(/* keyphrase */ "Hello Android",
                    Locale.forLanguageTag("en-US"),
                    Helper.createFakePersistableBundleData(),
                    Helper.createFakeSharedMemoryData(),
                    new AlwaysOnHotwordDetector.Callback() {
                        @Override
                        public void onAvailabilityChanged(int status) {
                            Log.i(TAG, "onAvailabilityChanged(" + status + ")");
                            mAvailabilityStatus = status;
                            if (mAvailabilityChangeLatch != null) {
                                mAvailabilityChangeLatch.countDown();
                            }
                        }

                        @Override
                        public void onDetected(AlwaysOnHotwordDetector.EventPayload eventPayload) {
                            Log.i(TAG, "onDetected");
                        }

                        @Override
                        public void onRejected(@NonNull HotwordRejectedResult result) {
                            super.onRejected(result);
                            Log.i(TAG, "onRejected");
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
                        public void onHotwordDetectionServiceInitialized(int status) {
                            super.onHotwordDetectionServiceInitialized(status);
                            Log.i(TAG, "onHotwordDetectionServiceInitialized status = " + status);
                            if (status != HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS) {
                                return;
                            }
                            mInitializedStatus = status;
                            if (mServiceTriggerLatch != null) {
                                mServiceTriggerLatch.countDown();
                            }
                        }

                        @Override
                        public void onHotwordDetectionServiceRestarted() {
                            super.onHotwordDetectionServiceRestarted();
                            Log.i(TAG, "onHotwordDetectionServiceRestarted");
                        }
                    });
        } catch (IllegalStateException | SecurityException e) {
            Log.w(TAG, "callCreateAlwaysOnHotwordDetector() exception: " + e);
        }
        return null;
    }
}
