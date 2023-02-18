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
package android.autofillservice.cts.servicebehavior;

import static android.autofillservice.cts.testcore.Helper.enablePccDetectionFeature;

import android.autofillservice.cts.commontests.FieldClassificationServiceManualActivityLaunchTestCase;

import org.junit.Test;

public class PccFieldClassificationTest extends
        FieldClassificationServiceManualActivityLaunchTestCase {

    @Test
    public void testFieldClassificationRequestIsSentWhenScreenEntered() throws Exception {
        enableService();
        enablePccDetectionFeature(sContext, "username");
        enablePccDetectionService();

        startLoginActivity();

        sClassificationReplier.getNextFieldClassificationRequest();
        sClassificationReplier.assertNoUnhandledFieldClassificationRequests();
    }
}

