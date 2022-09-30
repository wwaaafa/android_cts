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

package android.voiceinteraction.cts;

import static android.content.pm.PackageManager.FEATURE_MICROPHONE;
import static android.voiceinteraction.cts.testcore.VoiceInteractionDetectionHelper.testHotwordDetection;

import android.platform.test.annotations.AppModeFull;
import android.service.voice.AlwaysOnHotwordDetector;
import android.voiceinteraction.common.Utils;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "No real use case for instant mode hotword detector")
public class AlwaysOnHotwordDetectorTest extends AbstractVoiceInteractionBasicTestCase {
    static final String TAG = "HotwordDetectionServiceBasicTest";

    @Rule
    public RequiredFeatureRule REQUIRES_MIC_RULE = new RequiredFeatureRule(FEATURE_MICROPHONE);

    @Override
    public String getVoiceInteractionService() {
        return "android.voiceinteraction.cts/"
                + "android.voiceinteraction.service.BasicVoiceInteractionService";
    }

    @Test
    public void testAlwaysOnHotwordDetector_startRecognitionWithData() {
        // creates detector
        testHotwordDetection(mActivityTestRule, mContext,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_TEST,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_RESULT_INTENT,
                Utils.HOTWORD_DETECTION_SERVICE_TRIGGER_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.DSP_DETECTOR_ENROLL_FAKE_DSP_MODEL,
                Utils.DSP_DETECTOR_AVAILABILITY_RESULT_INTENT,
                AlwaysOnHotwordDetector.STATE_KEYPHRASE_ENROLLED,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);

        testHotwordDetection(mActivityTestRule, mContext,
                Utils.DSP_DETECTOR_START_RECOGNITION_WITH_DATA_TEST,
                Utils.DSP_DETECTOR_START_RECOGNITION_RESULT_INTENT,
                Utils.DSP_DETECTOR_START_RECOGNITION_RESULT_SUCCESS,
                Utils.HOTWORD_DETECTION_SERVICE_BASIC);
    }
}
