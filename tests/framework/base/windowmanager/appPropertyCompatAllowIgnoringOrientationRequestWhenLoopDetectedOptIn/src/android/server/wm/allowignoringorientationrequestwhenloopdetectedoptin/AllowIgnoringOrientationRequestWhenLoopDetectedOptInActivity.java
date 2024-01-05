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

package android.server.wm.allowignoringorientationrequestwhenloopdetectedoptin;

import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;

import android.app.Activity;
import android.os.Bundle;

public class AllowIgnoringOrientationRequestWhenLoopDetectedOptInActivity extends Activity {

    private static final int MIN_COUNT_TO_IGNORE_REQUEST_IN_LOOP = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onStart();
        // Create orientation request loop
        for (int i = 0; i <= MIN_COUNT_TO_IGNORE_REQUEST_IN_LOOP; i++) {
            setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        }
    }

}
