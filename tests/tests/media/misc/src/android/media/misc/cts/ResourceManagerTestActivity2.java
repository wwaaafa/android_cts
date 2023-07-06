/*
 * Copyright 2015 The Android Open Source Project
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

package android.media.misc.cts;

import android.util.Log;

public class ResourceManagerTestActivity2 extends ResourceManagerTestActivityBase {
    @Override
    protected void onResume() {
        TAG = "ResourceManagerTestActivity2";

        Log.d(TAG, "onResume called.");
        super.onResume();

        // Try to create as many as MAX_INSTANCES codecs from this foreground activity
        // so that we run into Resource conflict (INSUFFICIENT_RESOURCE) situation
        // and eventually reclaim a codec from the background activity.
        int codecCount = allocateCodecs(MAX_INSTANCES);
        // See if we have failed to create at least one codec.
        int result = (codecCount > 0) ? RESULT_OK : RESULT_CANCELED;
        finishWithResult(result);
    }
}
