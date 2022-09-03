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
package android.companion.cts.multiprocess

import android.companion.cts.common.DEVICE_DISPLAY_NAME_A
import android.companion.cts.common.TestBase
import android.companion.cts.common.assertApplicationBinds
import android.companion.cts.common.killProcess
import android.os.SystemClock
import android.platform.test.annotations.AppModeFull
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.test.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test CDM binderDied rebinding.
 * Run: atest CtsCompanionDeviceManagerMultiProcessTestCases:RebindServiceTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(AndroidJUnit4::class)
class RebindServiceTest : TestBase() {
    @Test
    fun test_rebind_primary() {
        // Create a self-managed association.
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)
        // Publish device's presence and wait for callback.
        cdm.notifyDeviceAppeared(associationId)

        assertApplicationBinds(cdm)
        // Wait for secondary service to start.
        SystemClock.sleep(2000)
        // Kill both primary and secondary processes.
        killProcess(":primary")
        killProcess(":secondary")

        // Schedule rebind in 10 seconds but give it 11 seconds.
        SystemClock.sleep(11000)
        // Primary and secondary services should not be bound.
        assertServiceNotBound("PrimaryCompanionService")
        assertServiceNotBound("SecondaryCompanionService")
    }

    @Test
    fun test_rebind_secondary() {
        // Create a self-managed association.
        val associationId = createSelfManagedAssociation(DEVICE_DISPLAY_NAME_A)

        // Publish device's presence and wait for callback.
        cdm.notifyDeviceAppeared(associationId)

        assertApplicationBinds(cdm)
        // Wait for secondary service to start.
        SystemClock.sleep(2000)
        // Kill secondary process.
        killProcess(":secondary")

        // Schedule rebind in 10 seconds but give it 11 seconds.
        SystemClock.sleep(11000)
        // Secondary service should be bound.
        assertServiceBound("SecondaryCompanionService")
        // Primary service should be still bound.
        assertServiceBound("PrimaryCompanionService")
    }

    private fun assertServiceBound(component: String) {
        val output = runShellCommand("dumpsys activity services $component")
        val lines = output.lines()
        lines.forEach { line ->
            if (line.contains("ConnectionRecord") && line.contains(component)) {
                return
            }
        }

        fail("Service $component not bound.")
    }

    private fun assertServiceNotBound(component: String) {
        val output = runShellCommand("dumpsys activity services $component")
        val lines = output.lines()
        lines.forEach { line ->
            if (line.contains("ConnectionRecord") && line.contains(component)) {
                fail("Service $component should not bound.")
            }
        }
    }
}
