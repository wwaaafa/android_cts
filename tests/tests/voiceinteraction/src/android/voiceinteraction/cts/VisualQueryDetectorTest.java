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

import static android.voiceinteraction.cts.testcore.Helper.CTS_SERVICE_PACKAGE;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;


import android.content.Context;
import android.os.SystemClock;
import android.service.voice.SandboxedDetectionServiceBase;
import android.service.voice.VisualQueryDetector;
import android.util.Log;
import android.voiceinteraction.cts.services.CtsBasicVoiceInteractionService;
import android.voiceinteraction.cts.testcore.VoiceInteractionServiceConnectedRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Objects;


/**
 * Tests for {@link VisualQueryDetector} APIs.
 */
@RunWith(AndroidJUnit4.class)
public class VisualQueryDetectorTest {

    private static final String TAG = "VisualQueryDetectorTest";
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
        mService = (CtsBasicVoiceInteractionService) CtsBasicVoiceInteractionService.getService();
        // Check we can get the service, we need service object to call the service provided method
        Objects.requireNonNull(mService);
        // Wait the original HotwordDetectionService finish clean up to avoid flaky
        SystemClock.sleep(5_000);
    }

    @After
    public void tearDown() {
        mService = null;
    }

    @Test
    public void testVisualQueryDetector_initializationSuccess() throws Exception {
        // Create alwaysOnHotwordDetector and wait onHotwordDetectionServiceInitialized() callback
        mService.createVisualQueryDetector();

        // verify callback result
        mService.waitSandboxedDetectionServiceInitializedCalledOrException();

        // The VisualQueryDetector should be created correctly
        VisualQueryDetector visualQueryDetector = mService.getVisualQueryDetector();
        Objects.requireNonNull(visualQueryDetector);

        assertThat(mService.getSandboxedDetectionServiceInitializedResult()).isEqualTo(
                SandboxedDetectionServiceBase.INITIALIZATION_STATUS_SUCCESS);
    }
}
