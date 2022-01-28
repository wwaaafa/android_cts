/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.safetycenter.cts

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_IMMUTABLE
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_SAFETY_CENTER
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.res.Resources
import android.os.Build.VERSION_CODES.TIRAMISU
import android.provider.DeviceConfig
import android.safetycenter.SafetyCenterData
import android.safetycenter.SafetyCenterManager
import android.safetycenter.SafetyCenterManager.ACTION_REFRESH_SAFETY_SOURCES
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_PAGE_OPEN
import android.safetycenter.SafetyCenterManager.REFRESH_REASON_RESCAN_BUTTON_CLICK
import android.safetycenter.SafetyCenterManager.OnSafetyCenterDataChangedListener
import android.safetycenter.SafetySourceData
import android.safetycenter.SafetySourceIssue
import android.safetycenter.SafetySourceIssue.SEVERITY_LEVEL_CRITICAL_WARNING
import android.safetycenter.SafetySourceStatus
import android.safetycenter.SafetySourceStatus.STATUS_LEVEL_CRITICAL_WARNING
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.TimeoutCancellationException
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterManagerTest {
    private val context: Context = getApplicationContext()
    private val safetyCenterManager = context.getSystemService(SafetyCenterManager::class.java)!!
    private val safetySourceBroadcastReceiver = SafetySourceBroadcastReceiver()
    private val sourceId = "source_id"
    private val somePendingIntent = PendingIntent.getActivity(
        context, 0 /* requestCode */,
        Intent(ACTION_SAFETY_CENTER).addFlags(FLAG_ACTIVITY_NEW_TASK),
        FLAG_IMMUTABLE
    )
    private val safetySourceDataOnPageOpen = SafetySourceData.Builder(sourceId)
        .setStatus(
            SafetySourceStatus.Builder(
                "safetySourceDataOnPageOpen status title",
                "safetySourceDataOnPageOpen status summary",
                SafetySourceStatus.STATUS_LEVEL_NONE,
                somePendingIntent
            )
                .build()
        )
        .build()
    private val safetySourceDataOnRescanClick = SafetySourceData.Builder(sourceId)
        .setStatus(
            SafetySourceStatus.Builder(
                "safetySourceDataOnRescanClick status title",
                "safetySourceDataOnRescanClick status summary",
                SafetySourceStatus.STATUS_LEVEL_RECOMMENDATION,
                somePendingIntent
            )
                .build()
        ).build()

    @Before
    @After
    fun clearSafetyCenterDataBetweenTest() {
        safetyCenterManager.clearDataWithPermission()
    }

    @Test
    fun getLastSafetyCenterUpdate_noUpdate_returnsNull() {
        val lastSafetyCenterUpdate =
            safetyCenterManager.getLastUpdateWithPermission("some_unknown_id")

        assertThat(lastSafetyCenterUpdate).isNull()
    }

    @Test
    fun sendSafetyCenterUpdate_getLastSafetyCenterUpdateReturnsNewValue() {
        val id = "some_known_id"
        val safetyCenterUpdate = SafetySourceData.Builder(id).build()
        safetyCenterManager.sendUpdateWithPermission(safetyCenterUpdate)

        val lastSafetyCenterUpdate =
            safetyCenterManager.getLastUpdateWithPermission(id)

        assertThat(lastSafetyCenterUpdate).isEqualTo(safetyCenterUpdate)
    }

    @Test
    fun sendSafetyCenterUpdate_withSameId_replacesValue() {
        val id = "some_known_id"
        val firstSafetyCenterUpdate = SafetySourceData.Builder(id).build()
        safetyCenterManager.sendUpdateWithPermission(firstSafetyCenterUpdate)

        val secondSafetyCenterUpdate = SafetySourceData.Builder(id).setStatus(
            SafetySourceStatus.Builder(
                "Status title", "Summary of the status", STATUS_LEVEL_CRITICAL_WARNING,
                somePendingIntent
            ).build()
        ).addIssue(
            SafetySourceIssue.Builder(
                "Issue id", "Issue title", "Summary of the issue",
                SEVERITY_LEVEL_CRITICAL_WARNING
            ).addAction(
                SafetySourceIssue.Action.Builder(
                    "Solve issue",
                    somePendingIntent
                ).build()
            ).build()
        ).build()
        safetyCenterManager.sendUpdateWithPermission(secondSafetyCenterUpdate)

        val lastSafetyCenterUpdate =
            safetyCenterManager.getLastUpdateWithPermission(id)

        assertThat(lastSafetyCenterUpdate).isEqualTo(secondSafetyCenterUpdate)
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigEnabled_andFlagEnabled_returnsTrue() {
        if (!deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ true.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled =
            safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isTrue()
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigEnabled_andFlagDisabled_returnsFalse() {
        if (!deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ false.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled =
            safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigDisabled_andFlagEnabled_returnsFalse() {
        if (deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ true.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled =
            safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_whenConfigDisabled_andFlagDisabled_returnsFalse() {
        if (deviceSupportsSafetyCenter()) {
            return
        }

        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(
                DeviceConfig.NAMESPACE_PRIVACY,
                PROPERTY_SAFETY_CENTER_ENABLED,
                /* value = */ false.toString(),
                /* makeDefault = */ false
            )
        }

        val isSafetyCenterEnabled =
            safetyCenterManager.isSafetyCenterEnabledWithPermission()

        assertThat(isSafetyCenterEnabled).isFalse()
    }

    @Test
    fun isSafetyCenterEnabled_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.isSafetyCenterEnabled
        }
    }

    @Test
    fun refreshSafetySources_withoutManageSafetyCenterPermission_throwsSecurityException() {
        registerBroadcastReceiver()

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.refreshSafetySources(REFRESH_REASON_RESCAN_BUTTON_CLICK)
        }
        unregisterBroadcastReceiver()
    }

    @Test
    fun refreshSafetySources_withRefreshReasonRescanButtonClick_sourceSendsRescanData() {
        registerBroadcastReceiver()
        safetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataOnRescanClick

        safetyCenterManager.refreshSafetySourcesWithPermissionAndWait(
            REFRESH_REASON_RESCAN_BUTTON_CLICK
        )

        val lastSafetyCenterUpdate =
            safetyCenterManager.getLastUpdateWithPermission(sourceId)
        assertThat(lastSafetyCenterUpdate).isEqualTo(safetySourceDataOnRescanClick)
        unregisterBroadcastReceiver()
    }

    @Test
    fun refreshSafetySources_withRefreshReasonPageOpen_sourceSendsPageOpenData() {
        registerBroadcastReceiver()
        safetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOnPageOpen

        safetyCenterManager.refreshSafetySourcesWithPermissionAndWait(REFRESH_REASON_PAGE_OPEN)

        val lastSafetyCenterUpdate =
            safetyCenterManager.getLastUpdateWithPermission(sourceId)
        assertThat(lastSafetyCenterUpdate).isEqualTo(safetySourceDataOnPageOpen)
        unregisterBroadcastReceiver()
    }

    @Test
    fun refreshSafetySources_withInvalidRefreshSeason_throwsIllegalArgumentException() {
        registerBroadcastReceiver()
        safetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOnPageOpen
        safetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataOnRescanClick

        assertFailsWith(IllegalArgumentException::class) {
            safetyCenterManager.refreshSafetySourcesWithPermissionAndWait(500)
        }
        unregisterBroadcastReceiver()
    }

    @Test
    fun refreshSafetySources_withUnregisteredReceiver_sourceDoesNotSendData() {
        safetySourceBroadcastReceiver.safetySourceDataOnPageOpen = safetySourceDataOnPageOpen
        safetySourceBroadcastReceiver.safetySourceDataOnRescanClick = safetySourceDataOnRescanClick

        assertFailsWith(TimeoutCancellationException::class) {
            safetyCenterManager.refreshSafetySourcesWithPermissionAndWait(REFRESH_REASON_PAGE_OPEN)
        }
        val lastSafetyCenterUpdate =
            safetyCenterManager.getLastUpdateWithPermission(sourceId)
        assertThat(lastSafetyCenterUpdate).isNull()
    }

    @Test
    fun getSafetyCenterData_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.safetyCenterData
        }
    }

    // Object required instead of lambda because kotlin wraps functions in a new Java functional
    // interface implementation each time they are referenced/cast to a Java interface: b/215569072
    private val emptyListener = object : OnSafetyCenterDataChangedListener {
        override fun onSafetyCenterDataChanged(data: SafetyCenterData) {}
    }

    @Test
    fun addOnSafetyCenterDataChangedListener_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.addOnSafetyCenterDataChangedListener(
                    context.mainExecutor, emptyListener)
        }
    }

    @Test
    fun removeOnSafetyCenterDataChangedListener_whenAppDoesNotHoldPermission_methodThrows() {
        safetyCenterManager.addOnSafetyCenterDataChangedListenerWithPermission(
                context.mainExecutor, emptyListener)

        assertFailsWith(SecurityException::class) {
            safetyCenterManager.removeOnSafetyCenterDataChangedListener(emptyListener)
        }
    }

    @Test
    fun dismissSafetyIssue_whenAppDoesNotHoldPermission_methodThrows() {
        assertFailsWith(SecurityException::class) {
            safetyCenterManager.dismissSafetyIssue("bleh")
        }
    }

    private fun deviceSupportsSafetyCenter() =
        context.resources.getBoolean(
            Resources.getSystem().getIdentifier(
                "config_enableSafetyCenter",
                "bool",
                "android"
            )
        )

    private fun registerBroadcastReceiver() =
        context.registerReceiver(
            safetySourceBroadcastReceiver,
            IntentFilter(ACTION_REFRESH_SAFETY_SOURCES)
        )

    private fun unregisterBroadcastReceiver() =
        context.unregisterReceiver(safetySourceBroadcastReceiver)

    private fun SafetyCenterManager.refreshSafetySourcesWithPermissionAndWait(refreshReason: Int) {
        refreshSafetySourcesWithPermission(refreshReason)
        safetySourceBroadcastReceiver.waitTillOnReceiveComplete(BROADCAST_TIMEOUT)
    }

    companion object {
        /** Name of the flag that determines whether SafetyCenter is enabled. */
        const val PROPERTY_SAFETY_CENTER_ENABLED = "safety_center_is_enabled"
        private val BROADCAST_TIMEOUT: Duration = Duration.ofMillis(5000)
    }
}
