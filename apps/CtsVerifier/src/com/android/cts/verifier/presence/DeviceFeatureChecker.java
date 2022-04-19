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
package com.android.cts.verifier.presence;
import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.View;
import android.widget.Toast;
/**
 * Checks if a device supports a hardware feature needed for a test, and passes the test
 * automatically otherwise.
 */
public class DeviceFeatureChecker {
    /**
     * Checks if a feature is supported.
     *
     * @param feature must be a string defined in PackageManager
     */
    public static void checkFeatureSupported(Context context, View passButton, String feature) {
        if (!context.getPackageManager().hasSystemFeature(feature)) {
            String message = String.format("Device does not support %s, automatically passing test",
                    feature);
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            Log.e(context.getClass().getName(), message);
            passButton.performClick();
            Activity activity = (Activity) (context);
            activity.finish();
        }
    }
}
