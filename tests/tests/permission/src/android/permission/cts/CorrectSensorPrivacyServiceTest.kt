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

package android.permission.cts

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.Context
import android.content.Intent
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.hardware.SensorPrivacyManager.TOGGLE_TYPE_SOFTWARE
import android.os.Build
import android.os.Process
import android.platform.test.annotations.AppModeFull
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.waitForBroadcasts
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@AppModeFull(reason = "Cannot set system settings as instant app")
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU, codeName = "Tiramisu")
class CorrectSensorPrivacyServiceTest {

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val permissionControllerPackage = context.packageManager.permissionControllerPackageName
    private val sensorPrivacyManager = context.getSystemService(SensorPrivacyManager::class.java)!!
    private val unsupportedSensors: List<Int> = run {
        val sensors = mutableListOf<Int>()
        if (!isSensorSupported(CAMERA)) {
            sensors.add(CAMERA)
        }
        if (!isSensorSupported(MICROPHONE)) {
            sensors.add(MICROPHONE)
        }
        sensors
    }
    private var oldStates = mutableMapOf<Int, Boolean>()

    @Before
    fun setup() {
        assumeTrue(unsupportedSensors.isNotEmpty())
        runShellCommand("input keyevent KEYCODE_WAKEUP")
        runShellCommand("wm dismiss-keyguard")
        unsupportedSensors.forEach { sensor ->
            oldStates[sensor] = isSensorPrivacyEnabled(sensor)
        }
        resetPermissionController()
    }

    @After
    fun restoreState() {
        unsupportedSensors.forEach { sensor ->
            setSensorPrivacy(sensor, oldStates[sensor] ?: false)
            oldStates.remove(sensor)
        }
    }

    @Test
    fun verifyJobCleansUpState() {
        unsupportedSensors.forEach { sensor ->
            setSensorPrivacy(sensor, true)
        }

        triggerJobScheduling()

        eventually {
            unsupportedSensors.forEach { sensor ->
                assertFalse(isSensorPrivacyEnabled(sensor))
            }
        }
    }

    private fun isSensorSupported(sensor: Int): Boolean {
        return callWithShellPermissionIdentity {
            sensorPrivacyManager.supportsSensorToggle(sensor)
        }
    }

    private fun isSensorPrivacyEnabled(sensor: Int): Boolean {
        return callWithShellPermissionIdentity {
            sensorPrivacyManager.isSensorPrivacyEnabled(TOGGLE_TYPE_SOFTWARE, sensor)
        }
    }

    private fun resetPermissionController() {
        PermissionUtils.clearAppState(permissionControllerPackage)
        val automation = getAutomation()
        TestUtils.awaitJobUntilRequestedState(
            permissionControllerPackage,
            CORRECT_STATE_JOB_ID,
            TIMEOUT_MILLIS,
            automation,
            "unknown"
        )
        automation.destroy()

        runShellCommand(
            "cmd jobscheduler reset-execution-quota -u " +
                "${Process.myUserHandle().identifier} $permissionControllerPackage")
        runShellCommand("cmd jobscheduler reset-schedule-quota")
    }

    private fun setSensorPrivacy(sensor: Int, enable: Boolean) {
        runWithShellPermissionIdentity {
            sensorPrivacyManager.setSensorPrivacy(sensor, enable)
        }
    }

    private fun triggerJobScheduling() {
        val jobSetupReceiverIntent = Intent(ACTION_CORRECT_SENSOR_PRIVACY)
        jobSetupReceiverIntent.setPackage(permissionControllerPackage)
        jobSetupReceiverIntent.flags = Intent.FLAG_RECEIVER_FOREGROUND
        val resolveInfos = context.packageManager.queryBroadcastReceivers(jobSetupReceiverIntent, 0)
        assertTrue(
            "No broadcast receivers found for $ACTION_CORRECT_SENSOR_PRIVACY", resolveInfos.size > 0
        )

        context.sendBroadcast(jobSetupReceiverIntent)
        waitForBroadcasts()
    }

    private fun getAutomation(): UiAutomation {
        return instrumentation.getUiAutomation(
            UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES
        )
    }

    companion object {
        private const val CORRECT_STATE_JOB_ID = 10
        private const val TIMEOUT_MILLIS: Long = 10000
        private const val ACTION_CORRECT_SENSOR_PRIVACY =
            "android.safetycenter.action.CORRECT_SENSOR_PRIVACY"
        private const val SAFETY_CENTER_RECEIVER_CLASS =
            "com.android.permissioncontroller.privacysources.SafetyCenterReceiver"
    }
}
