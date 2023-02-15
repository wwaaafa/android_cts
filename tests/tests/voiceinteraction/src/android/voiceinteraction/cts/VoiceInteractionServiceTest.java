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

package android.voiceinteraction.cts;

import static android.Manifest.permission.CAPTURE_AUDIO_HOTWORD;
import static android.Manifest.permission.MANAGE_HOTWORD_DETECTION;
import static android.Manifest.permission.RECORD_AUDIO;
import static android.service.voice.VoiceInteractionSession.KEY_SHOW_SESSION_ID;
import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import android.app.UiAutomation;
import android.content.Context;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.service.voice.HotwordDetectionService;
import android.service.voice.HotwordRejectedResult;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;
import android.util.Log;
import android.voiceinteraction.common.Utils;
import android.voiceinteraction.cts.services.BaseVoiceInteractionService;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.Helper;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;

/**
 * Tests for {@link VoiceInteractionService} APIs.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode")
public class VoiceInteractionServiceTest {

    private static final String TAG = "VoiceInteractionServiceTest";
    private static final String KEY_SHOW_SESSION_TEST = "showSessionTest";

    // The VoiceInteractionService used by this test
    private static final String SERVICE_COMPONENT =
            "android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService";
    protected final Context mContext = getInstrumentation().getTargetContext();

    private CtsBasicVoiceInteractionService mService;

    @Rule
    public VoiceInteractionServiceConnectedRule mConnectedRule =
            new VoiceInteractionServiceConnectedRule(mContext, getTestVoiceInteractionService());

    public String getTestVoiceInteractionService() {
        Log.d(TAG, "getTestVoiceInteractionService()");
        return CTS_SERVICE_PACKAGE + "/" + SERVICE_COMPONENT;
    }

    @Before
    public void setup() {
        // VoiceInteractionServiceConnectedRule handles the service connected, we should be
        // able to get service
        mService = (CtsBasicVoiceInteractionService) BaseVoiceInteractionService.getService();
        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(mService);
    }

    @After
    public void tearDown() {
        mService = null;
    }

    @Test
    public void testShowSession_onPrepareToShowSessionCalled() throws Exception {
        final Bundle args = new Bundle();
        final int value = 100;
        args.putInt(KEY_SHOW_SESSION_TEST, value);
        final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;

        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        final Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.getInt(KEY_SHOW_SESSION_TEST, /* defaultValue= */ -1))
                .isEqualTo(value);
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        assertThat(mService.getPrepareToShowSessionFlags()).isEqualTo(flags);
    }

    @Test
    public void testShowSessionWithNullArgs_onPrepareToShowSessionCalledHasId() throws Exception {
        final int flags = VoiceInteractionSession.SHOW_WITH_ASSIST;

        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(/* args= */ null, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        final Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs).isNotNull();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        assertThat(mService.getPrepareToShowSessionFlags()).isEqualTo(flags);
    }

    @Test
    public void testShowSession_onPrepareToShowSessionCalledTwiceIdIsDifferent() throws Exception {
        final Bundle args = new Bundle();
        final int flags = 0;

        // trigger showSession first time
        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        // get the first showSession id
        Bundle resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        final int firstId = resultArgs.getInt(KEY_SHOW_SESSION_ID);

        // trigger showSession second time
        BaseVoiceInteractionService.initShowSessionLatch();
        mService.showSession(args, flags);
        BaseVoiceInteractionService.waitOnPrepareToShowSession();

        // get the second showSession id
        resultArgs = mService.getPrepareToShowSessionArgs();
        assertThat(resultArgs.containsKey(KEY_SHOW_SESSION_ID)).isTrue();
        final int secondId = resultArgs.getInt(KEY_SHOW_SESSION_ID);

        assertThat(secondId).isGreaterThan(firstId);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateAlwaysOnHotwordDetectorNoExecutorMainThread_callbackRunsOnMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ true,
                /* callbackShouldRunOnMainThread= */ true);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateAlwaysOnHotwordDetectorNoExecutorOtherThread_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetector(/* useExecutor= */ false, /* runOnMainThread= */ false,
                /* callbackShouldRunOnMainThread= */ false);
    }

    @ApiTest(apis = {
            "android.service.voice.VoiceInteractionService#createAlwaysOnHotwordDetector"})
    @Test
    public void testCreateAlwaysOnHotwordDetectorWithExecutor_callbackRunsOnNonMainThread()
            throws Exception {
        testCreateAlwaysOnHotwordDetector(/* useExecutor= */ true, /* runOnMainThread= */ true,
                /* callbackShouldRunOnMainThread= */ false);
    }

    private void testCreateAlwaysOnHotwordDetector(boolean useExecutor, boolean runOnMainThread,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        SystemClock.sleep(10_000);

        // Create alwaysOnHotwordDetector and wait result
        mService.createAlwaysOnHotwordDetector(useExecutor, runOnMainThread);

        // reset the value to non-expected value
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // verify callback result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();
        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                HotwordDetectionService.INITIALIZATION_STATUS_SUCCESS);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);

        // The AlwaysOnHotwordDetector should be created correctly
        AlwaysOnHotwordDetector alwaysOnHotwordDetector = mService.getAlwaysOnHotwordDetector();
        Objects.requireNonNull(alwaysOnHotwordDetector);

        // reset the value to non-expected value
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // override availability and wait onAvailabilityChanged() callback called
        mService.initAvailabilityChangeLatch();
        alwaysOnHotwordDetector.overrideAvailability(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);

        // verify callback result
        mService.waitAvailabilityChangedCalled();
        assertThat(mService.getHotwordDetectionServiceAvailabilityResult()).isEqualTo(
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);

        try {
            adoptShellPermissionIdentityForHotword();

            verifyDetectFromDspSuccess(alwaysOnHotwordDetector, callbackShouldRunOnMainThread);
            verifyDetectFromDspRejected(alwaysOnHotwordDetector, callbackShouldRunOnMainThread);
            verifyDetectFromDspError(alwaysOnHotwordDetector, callbackShouldRunOnMainThread);

            // destroy detector
            alwaysOnHotwordDetector.destroy();
        } finally {
            // Drop identity adopted.
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    private void adoptShellPermissionIdentityForHotword() {
        // Drop any identity adopted earlier.
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation.dropShellPermissionIdentity();
        // need to retain the identity until the callback is triggered
        uiAutomation.adoptShellPermissionIdentity(RECORD_AUDIO, CAPTURE_AUDIO_HOTWORD,
                MANAGE_HOTWORD_DETECTION);
    }

    private void verifyDetectFromDspSuccess(AlwaysOnHotwordDetector alwaysOnHotwordDetector,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // reset the value to non-expected value, test onDetected callback
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        mService.initDetectRejectLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
                /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onDetected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        AlwaysOnHotwordDetector.EventPayload detectResult =
                mService.getHotwordServiceOnDetectedResult();

        Helper.verifyDetectedResult(detectResult, Helper.DETECTED_RESULT);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }

    private void verifyDetectFromDspRejected(AlwaysOnHotwordDetector alwaysOnHotwordDetector,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // reset the value to non-expected value, test onRejected callback
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        mService.initDetectRejectLatch();
        // pass null data parameter
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
                /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), null,
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onRejected() called and verify the result
        mService.waitOnDetectOrRejectCalled();
        HotwordRejectedResult rejectedResult =
                mService.getHotwordServiceOnRejectedResult();

        assertThat(rejectedResult).isEqualTo(Helper.REJECTED_RESULT);
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }

    private void verifyDetectFromDspError(AlwaysOnHotwordDetector alwaysOnHotwordDetector,
            boolean callbackShouldRunOnMainThread)
            throws Exception {
        // reset the value to non-expected value, test onError callback
        mService.setIsDetectorCallbackRunningOnMainThread(!callbackShouldRunOnMainThread);

        // Update HotwordDetectionService options to delay detection, to cause a timeout
        PersistableBundle options = Helper.createFakePersistableBundleData();
        options.putInt(Utils.KEY_DETECTION_DELAY_MS, 5000);
        alwaysOnHotwordDetector.updateState(options,
                Helper.createFakeSharedMemoryData());

        mService.initOnErrorLatch();
        alwaysOnHotwordDetector.triggerHardwareRecognitionEventForTest(
                /* status= */ 0, /* soundModelHandle= */ 100, /* captureAvailable= */ true,
                /* captureSession= */ 101, /* captureDelayMs= */ 1000,
                /* capturePreambleMs= */ 1001, /* triggerInData= */ true,
                Helper.createFakeAudioFormat(), new byte[1024],
                Helper.createFakeKeyphraseRecognitionExtraList());

        // wait onError() called
        mService.waitOnErrorCalled();
        assertThat(mService.isDetectorCallbackRunningOnMainThread()).isEqualTo(
                callbackShouldRunOnMainThread);
    }
}
