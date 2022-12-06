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

import android.service.voice.VoiceInteractionService;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The base {@link VoiceInteractionService} that provides the common methods for subclass.
 */
public abstract class BaseVoiceInteractionService extends VoiceInteractionService {

    public static final String SERVICE_PACKAGE = "android.voiceinteraction.cts";

    private final String mTag = getClass().getSimpleName();
    public static final int STATUS_NO_CALLBACK_CALLED = -1;


    // The service instance
    public static VoiceInteractionService sService;
    // The timeout to wait for async result
    public static final long WAIT_TIMEOUT_IN_MS = 5_000;
    // The CountDownLatch waits for service connect
    public static CountDownLatch sConnectLatch;
    // The CountDownLatch waits for a service init result
    // TODO: rename to mHotwordDetectionServiceInitializedLatch, keep this name until the
    //  refactor done. The original tests use trigger in many places. To make the mapping asier,
    //  keep the current name now.
    public CountDownLatch mServiceTriggerLatch;

    public static CountDownLatch sDisconnectLatch;

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

    /**
     * Reset the static variables of this service.
     */
    public static void resetStaticValues() {
        sService = null;
        sConnectLatch = null;
        sDisconnectLatch = null;
    }

    /**
     * Init the CountDownLatch that is used to wait for service onReady() and onShutdown().
     */
    public static void initServiceConnectionLatches() {
        sConnectLatch = new CountDownLatch(1);
        sDisconnectLatch = new CountDownLatch(1);
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

    public static VoiceInteractionService getService() {
        return sService;
    }
}
