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

import static android.voiceinteraction.cts.testcore.Helper.WAIT_TIMEOUT_IN_MS;

import android.os.Bundle;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetector;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.VoiceInteractionService;
import android.util.Log;
import android.voiceinteraction.cts.testcore.Helper;

import androidx.annotation.NonNull;

import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The base {@link VoiceInteractionService} that provides the common methods for subclass.
 */
public abstract class BaseVoiceInteractionService extends VoiceInteractionService {

    private final String mTag = getClass().getSimpleName();
    public static final int STATUS_NO_CALLBACK_CALLED = -1;

    // The service instance
    public static VoiceInteractionService sService;
    // The CountDownLatch waits for service connect
    public static CountDownLatch sConnectLatch;
    public static CountDownLatch sDisconnectLatch;

    // The CountDownLatch waits for onPrepareToShowSession
    public static CountDownLatch sPrepareToShowSessionLatch;

    // The CountDownLatch waits for a service init result
    // TODO: rename to mHotwordDetectionServiceInitializedLatch, keep this name until the
    //  refactor done. The original tests use trigger in many places. To make the mapping asier,
    //  keep the current name now.
    public CountDownLatch mServiceTriggerLatch;

    HotwordDetector mSoftwareHotwordDetector = null;
    // The AlwaysOnHotwordDetector created by createAlwaysOnHotwordDetector() API
    AlwaysOnHotwordDetector mAlwaysOnHotwordDetector = null;
    // Throws IllegalStateException when calling createAlwaysOnHotwordDetector() API
    private boolean mIsCreateDetectorIllegalStateExceptionThrow;
    // Throws SecurityException when calling createAlwaysOnHotwordDetector() API
    private boolean mIsCreateDetectorSecurityExceptionThrow;

    private Bundle mPrepareToShowSessionArgs = new Bundle();
    private int mPrepareToShowSessionFlags = -1;

    // An AlwaysOnHotwordDetector.Callback no nothing on callback methods
    final AlwaysOnHotwordDetector.Callback mNoOpHotwordDetectorCallback =
            new AlwaysOnHotwordDetector.Callback() {
                @Override
                public void onAvailabilityChanged(int status) {
                    // no-op
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
                    Log.i(mTag, "onHotwordDetectionServiceInitialized status = " + status);
                }

                @Override
                public void onHotwordDetectionServiceRestarted() {
                    // no-op
                }
            };

    // the status of onHotwordDetectionServiceInitialized()
    int mInitializedStatus = STATUS_NO_CALLBACK_CALLED;

    int mAvailabilityStatus = STATUS_NO_CALLBACK_CALLED;

    @Override
    public void onReady() {
        super.onReady();
        Log.d(mTag, "onReady()");
        sService = this;
        if (sConnectLatch != null) {
            sConnectLatch.countDown();
        }
    }

    @Override
    public void onShutdown() {
        super.onShutdown();
        Log.d(mTag, "onShutdown()");
        if (sDisconnectLatch != null) {
            sDisconnectLatch.countDown();
        }
    }

    @Override
    public void onPrepareToShowSession(Bundle args, int flags) {
        Log.d(mTag, "onPrepareToShowSession args = " + args + ", flags =" + flags);
        if (sPrepareToShowSessionLatch != null) {
            sPrepareToShowSessionLatch.countDown();
        }
        mPrepareToShowSessionArgs = args;
        mPrepareToShowSessionFlags = flags;
    }

    @Override
    public void onShowSessionFailed(Bundle args) {
        Log.d(mTag, "onShowSessionFailed args = " + args);
    }

    /**
     * Reset the static variables of this service.
     */
    public static void resetStaticValues() {
        sService = null;
        sConnectLatch = null;
        sDisconnectLatch = null;
        sPrepareToShowSessionLatch = null;
    }

    /**
     * Init the CountDownLatch that is used to wait for service onReady() and onShutdown().
     */
    public static void initServiceConnectionLatches() {
        sConnectLatch = new CountDownLatch(1);
        sDisconnectLatch = new CountDownLatch(1);
    }

    /**
     * Init the CountDownLatch that is used to wait for onPrepareToShowSession.
     */
    public static void initShowSessionLatch() {
        sPrepareToShowSessionLatch = new CountDownLatch(1);
    }

    /**
     * Wait for service onReady().
     */
    public static void waitServiceConnect() throws InterruptedException {
        if (sConnectLatch == null) {
            throw new AssertionError("Should init connect CountDownLatch");
        }
        if (!sConnectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("VoiceInteractionService doesn't start.");
        }
        sConnectLatch = null;
    }

    /**
     * Wait for service onShutdown().
     */
    public static void waitServiceDisconnect() throws InterruptedException {
        if (sDisconnectLatch == null) {
            throw new AssertionError("Should init disconnect CountDownLatch");
        }
        if (!sDisconnectLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("VoiceInteractionService doesn't shut down.");
        }
        sDisconnectLatch = null;
    }

    /**
     * Wait for onPrepareToShowSession
     */
    public static void waitOnPrepareToShowSession() throws InterruptedException {
        if (sPrepareToShowSessionLatch == null) {
            throw new AssertionError("Should init prepareToShowSession CountDownLatch");
        }
        if (!sPrepareToShowSessionLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            throw new AssertionError("onPrepareToShowSession has not been triggered");
        }
        sPrepareToShowSessionLatch = null;
    }

    public static VoiceInteractionService getService() {
        return sService;
    }

    /**
     * Returns if createAlwaysOnHotwordDetector throws IllegalStateException.
     */
    public boolean isCreateDetectorIllegalStateExceptionThrow() {
        return mIsCreateDetectorIllegalStateExceptionThrow;
    }

    /**
     * Returns if createAlwaysOnHotwordDetector throws SecurityException.
     */
    public boolean isCreateDetectorSecurityExceptionThrow() {
        return mIsCreateDetectorSecurityExceptionThrow;
    }

    /**
     * Returns the Service's AlwaysOnHotwordDetector.
     */
    public AlwaysOnHotwordDetector getAlwaysOnHotwordDetector() {
        return mAlwaysOnHotwordDetector;
    }

    /**
     * Returns the Service's SoftwareHotwordDetector.
     */
    public HotwordDetector getSoftwareHotwordDetector() {
        return mSoftwareHotwordDetector;
    }

    /**
     * Wait for onHotwordDetectionServiceInitialized() be called or exception throws when creating
     * AlwaysOnHotwordDetector.
     */
    public void waitHotwordDetectionServiceInitializedCalledOrException()
            throws InterruptedException {
        Log.d(mTag, "waitHotwordDetectionServiceInitializedCalledOrException(), latch="
                + mServiceTriggerLatch);
        if (mServiceTriggerLatch == null
                || !mServiceTriggerLatch.await(WAIT_TIMEOUT_IN_MS, TimeUnit.MILLISECONDS)) {
            Log.w(mTag, "waitAndGetHotwordServiceInitializedResult()");
            mServiceTriggerLatch = null;
            throw new AssertionError("HotwordService initialized fail.");
        }
        mServiceTriggerLatch = null;
    }

    private void resetVales() {
        mIsCreateDetectorIllegalStateExceptionThrow = false;
        mIsCreateDetectorSecurityExceptionThrow = false;
    }

    /**
     * Return the args from onPrepareToShowSession.
     */
    @NonNull
    public Bundle getPrepareToShowSessionArgs() {
        return mPrepareToShowSessionArgs;
    }

    /**
     * Return the flags from onPrepareToShowSession.
     */
    public int getPrepareToShowSessionFlags() {
        return mPrepareToShowSessionFlags;
    }


    /**
     * Return the result for onHotwordDetectionServiceInitialized().
     */
    public int getHotwordDetectionServiceInitializedResult() {
        return mInitializedStatus;
    }

    AlwaysOnHotwordDetector callCreateAlwaysOnHotwordDetector(
            AlwaysOnHotwordDetector.Callback callback) {
        Log.i(mTag, "callCreateAlwaysOnHotwordDetector()");
        try {
            resetVales();
            return createAlwaysOnHotwordDetector(/* keyphrase */ "Hello Android",
                    Locale.forLanguageTag("en-US"),
                    Helper.createFakePersistableBundleData(),
                    Helper.createFakeSharedMemoryData(),
                    callback);
        } catch (IllegalStateException | SecurityException e) {
            Log.w(mTag, "callCreateAlwaysOnHotwordDetector() exception: " + e);
            if (mServiceTriggerLatch != null) {
                mServiceTriggerLatch.countDown();
            }
            if (e instanceof IllegalStateException) {
                mIsCreateDetectorIllegalStateExceptionThrow = true;
            } else {
                mIsCreateDetectorSecurityExceptionThrow = true;
            }
        }
        return null;
    }

    HotwordDetector callCreateSoftwareHotwordDetector(HotwordDetector.Callback callback) {
        Log.i(mTag, "callCreateSoftwareHotwordDetector()");
        try {
            resetVales();
            return createHotwordDetector(Helper.createFakePersistableBundleData(),
                    Helper.createFakeSharedMemoryData(), callback);
        } catch (Exception e) {
            Log.w(mTag, "callCreateSoftwareHotwordDetector() exception: " + e);
            if (mServiceTriggerLatch != null) {
                mServiceTriggerLatch.countDown();
            }
            if (e instanceof IllegalStateException) {
                mIsCreateDetectorIllegalStateExceptionThrow = true;
            }
        }
        return null;
    }
}
