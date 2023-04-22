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

package android.security.cts;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.app.BroadcastOptions;
import android.os.Bundle;
import android.os.PowerExemptionManager;
import android.platform.test.annotations.AsbSecurityTest;

import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CVE_2023_20950 extends StsExtraBusinessLogicTestCase {

    @Test
    @AsbSecurityTest(cveBugId = 195756028)
    public void test_CVE_2023_20950() {
        BroadcastOptions bo;

        bo = BroadcastOptions.makeBasic();
        Bundle bundle = bo.toBundle();

        // Only background activity launch key is set.
        assertEquals(1, bundle.size());
        assertTrue(bundle.containsKey("android.pendingIntent.backgroundActivityAllowed"));

        // Check the default values about temp-allowlist.
        assertBroadcastOption_noTemporaryAppAllowList(bo);
    }

    private BroadcastOptions cloneViaBundle(BroadcastOptions bo) {
        final Bundle b = bo.toBundle();

        // If toBundle() returns null, that means the BroadcastOptions was the default values.
        return b == null ? BroadcastOptions.makeBasic() : new BroadcastOptions(b);
    }

    private void assertBroadcastOptionTemporaryAppAllowList(
            BroadcastOptions bo,
            long expectedDuration,
            int expectedAllowListType,
            int expectedReasonCode,
            String expectedReason) {
        assertEquals(expectedAllowListType, bo.getTemporaryAppAllowlistType());
        assertEquals(expectedDuration, bo.getTemporaryAppAllowlistDuration());
        assertEquals(expectedReasonCode, bo.getTemporaryAppAllowlistReasonCode());
        assertEquals(expectedReason, bo.getTemporaryAppAllowlistReason());

        // Clone the BO and check it too.
        BroadcastOptions cloned = cloneViaBundle(bo);
        assertEquals(expectedAllowListType, cloned.getTemporaryAppAllowlistType());
        assertEquals(expectedDuration, cloned.getTemporaryAppAllowlistDuration());
        assertEquals(expectedReasonCode, cloned.getTemporaryAppAllowlistReasonCode());
        assertEquals(expectedReason, cloned.getTemporaryAppAllowlistReason());
    }

    private void assertBroadcastOption_noTemporaryAppAllowList(BroadcastOptions bo) {
        assertBroadcastOptionTemporaryAppAllowList(bo,
                /* duration= */ 0,
                PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_NONE,
                PowerExemptionManager.REASON_UNKNOWN,
                /* reason= */ null);
    }
}
