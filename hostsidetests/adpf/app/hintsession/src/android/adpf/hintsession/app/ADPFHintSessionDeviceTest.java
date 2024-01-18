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

package android.adpf.hintsession.app;

import static android.adpf.common.ADPFHintSessionConstants.TEST_NAME_KEY;

import static org.junit.Assert.fail;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.device.collectors.util.SendToInstrumentation;
import android.os.Bundle;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class ADPFHintSessionDeviceTest {
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();

    @Test
    public void testAdpfHintSession() {
        final Context context = mInstrumentation.getContext();
        final Intent intent = new Intent(context, ADPFHintSessionDeviceActivity.class);
        // TODO: pass config to app
        intent.putExtra(TEST_NAME_KEY, "testAdpfHintSession");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        ADPFHintSessionDeviceActivity activity = (ADPFHintSessionDeviceActivity) mInstrumentation
                .startActivitySync(intent);
        // this will wait until the test is complete
        activity.waitForTestFinished();

        // report metrics
        final Bundle returnBundle = new Bundle();
        Map<String, String> metrics = activity.getMetrics();
        if (metrics.containsKey("failure")) {
            fail("Failed with error: " + metrics.get("failure"));
        }
        metrics.forEach(returnBundle::putString);
        SendToInstrumentation.sendBundle(mInstrumentation, returnBundle);
    }
}
