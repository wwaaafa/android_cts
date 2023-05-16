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

package android.app.cts.broadcasts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.BroadcastOptions;
import android.content.Intent;
import android.os.PowerExemptionManager;
import android.os.SystemClock;
import android.provider.DeviceConfig;

import com.android.app.cts.broadcasts.Common;
import com.android.app.cts.broadcasts.ICommandReceiver;
import com.android.compatibility.common.util.AmUtils;
import com.android.compatibility.common.util.DeviceConfigStateHelper;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(BroadcastsTestRunner.class)
public class BroadcastFreezerTest extends BaseBroadcastTest {

    // How much to delay freezing by.
    // Should be greater than SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS
    private static final long APP_FREEZING_DELAY_MS = 15_000;

    // How long after freezer timeout to verify the app frozen state
    private static final long ERROR_MARGIN_MS = 5_000;

    // Test delaying freezer via BroadcastOptions
    @Test
    public void testBroadcastOptions_appFreezingDelayed() throws Exception {
        assumeTrue(isModernBroadcastQueueEnabled());
        assumeTrue(isAppFreezerEnabled());

        TestServiceConnection connection = bindToHelperService(HELPER_PKG1);
        try (DeviceConfigStateHelper deviceConfigStateHelper = new DeviceConfigStateHelper(
                DeviceConfig.NAMESPACE_ACTIVITY_MANAGER_NATIVE_BOOT)) {
            deviceConfigStateHelper.set(KEY_FREEZE_DEBOUNCE_TIMEOUT,
                    String.valueOf(SHORT_FREEZER_TIMEOUT_MS));

            final Intent intent = new Intent(Common.ORDERED_BROADCAST_ACTION)
                    .putExtra(TEST_EXTRA1, TEST_VALUE1)
                    .setPackage(HELPER_PKG1);
            final BroadcastOptions options = BroadcastOptions.makeBasic();
            options.setTemporaryAppAllowlist(APP_FREEZING_DELAY_MS,
                    PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_APP_FREEZING_DELAYED,
                    PowerExemptionManager.REASON_PUSH_MESSAGING_DEFERRABLE,
                    "Normal Priority");
            int testPid = -1;
            long startTimeMs = 0;
            ICommandReceiver cmdReceiver;
            try {
                cmdReceiver = connection.getCommandReceiver();
                testPid = cmdReceiver.getPid();
                final ResultReceiver resultReceiver = new ResultReceiver();

                SystemUtil.runWithShellPermissionIdentity(() -> {
                    mContext.sendOrderedBroadcast(intent, null, options.toBundle(),
                            resultReceiver, null, 0, null, null);
                });
                AmUtils.waitForBroadcastBarrier();
                assertThat(resultReceiver.getResult())
                        .isEqualTo(Common.ORDERED_BROADCAST_RESULT_DATA);
                // Start the timer
                startTimeMs = SystemClock.uptimeMillis();

                // Try to override it with a broadcast with a smaller delay. This is to make sure
                // that a second delay request that's shorter than the first doesn't override
                // the longer first delay.
                options.setTemporaryAppAllowlist(SHORT_FREEZER_TIMEOUT_MS,
                        PowerExemptionManager.TEMPORARY_ALLOW_LIST_TYPE_APP_FREEZING_DELAYED,
                        PowerExemptionManager.REASON_PUSH_MESSAGING_DEFERRABLE,
                        "Normal Priority");
                SystemUtil.runWithShellPermissionIdentity(() -> {
                    mContext.sendOrderedBroadcast(intent, null, options.toBundle(),
                            resultReceiver, null, 0, null, null);
                });
                AmUtils.waitForBroadcastBarrier();
                assertThat(resultReceiver.getResult())
                        .isEqualTo(Common.ORDERED_BROADCAST_RESULT_DATA);
            } finally {
                // Unbind service so that the app can be cached
                connection.unbind();
            }

            SystemClock.sleep(SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS
                    - (SystemClock.uptimeMillis() - startTimeMs));
            assertFalse("Should not be frozen in "
                    + (SHORT_FREEZER_TIMEOUT_MS + ERROR_MARGIN_MS) + "ms",
                    isProcessFrozen(testPid));
            SystemClock.sleep(APP_FREEZING_DELAY_MS + ERROR_MARGIN_MS
                    - (SystemClock.uptimeMillis() - startTimeMs));
            assertTrue("Unfrozen for longer than expected", isProcessFrozen(testPid));
        } finally {
            connection.unbind();
        }
    }
}
