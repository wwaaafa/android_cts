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

package android.devicepolicy.internal

import android.app.admin.flags.Flags
import android.platform.test.annotations.RequiresFlagsEnabled
import android.platform.test.flag.junit.DeviceFlagsValueProvider
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.nene.TestApis
import com.google.common.truth.Truth.assertThat
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class DumpSysTest {

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val deviceState = DeviceState()
        var chain: TestRule = RuleChain
                .outerRule(DeviceFlagsValueProvider.createCheckFlagsRule())
                .around(deviceState)
    }

    @Test
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @RequiresFlagsEnabled(Flags.FLAG_DUMPSYS_POLICY_ENGINE_MIGRATION_ENABLED)
    fun dumpSys_containsPolicy() {
        try {
            deviceState.dpc().devicePolicyManager()
                    .setScreenCaptureDisabled(deviceState.dpc().componentName(), true)

            assertThat(
                TestApis.dumpsys().devicePolicy()
            ).contains("BooleanPolicyValue { mValue= true } }")
        } finally {
            deviceState.dpc().devicePolicyManager().setScreenCaptureDisabled(
                    deviceState.dpc().componentName(),
                    false
            )
        }
    }
}
