/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.permission4.cts

import android.Manifest
import android.app.Instrumentation
import android.app.UiAutomation
import android.app.compat.CompatChanges
import android.content.AttributionSource
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.os.Process
import android.permission.PermissionManager
import android.provider.DeviceConfig
import android.provider.Settings
import android.server.wm.WindowManagerStateHelper
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiSelector
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.DisableAnimationRule
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

private const val APP_LABEL = "CtsCameraMicAccess"
private const val APP_PKG = "android.permission4.cts.appthataccessescameraandmic"
private const val SHELL_PKG = "com.android.shell"
private const val USE_CAMERA = "use_camera"
private const val USE_MICROPHONE = "use_microphone"
private const val USE_HOTWORD = "use_hotword"
private const val INTENT_ACTION = "test.action.USE_CAMERA_OR_MIC"
private const val PRIVACY_CHIP_ID = "com.android.systemui:id/privacy_chip"
private const val CAR_MIC_PRIVACY_CHIP_ID = "com.android.systemui:id/mic_privacy_chip"
private const val CAR_CAMERA_PRIVACY_CHIP_ID = "com.android.systemui:id/camera_privacy_chip"
private const val PRIVACY_ITEM_ID = "com.android.systemui:id/privacy_item"
private const val INDICATORS_FLAG = "camera_mic_icons_enabled"
private const val PERMISSION_INDICATORS_NOT_PRESENT = 162547999L
private const val IDLE_TIMEOUT_MILLIS: Long = 1000
private const val UNEXPECTED_TIMEOUT_MILLIS = 1000
private const val TIMEOUT_MILLIS: Long = 20000
private const val TV_MIC_INDICATOR_WINDOW_TITLE = "MicrophoneCaptureIndicator"

class CameraMicIndicatorsPermissionTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.context
    private val uiAutomation: UiAutomation = instrumentation.uiAutomation
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)
    private val packageManager: PackageManager = context.packageManager
    private val permissionManager: PermissionManager =
        context.getSystemService(PermissionManager::class.java)!!

    private val isTv = packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private var wasEnabled = false
    private val micLabel = packageManager.getPermissionGroupInfo(
        Manifest.permission_group.MICROPHONE, 0).loadLabel(packageManager).toString()
    private val cameraLabel = packageManager.getPermissionGroupInfo(
        Manifest.permission_group.CAMERA, 0).loadLabel(packageManager).toString()

    private var screenTimeoutBeforeTest: Long = 0L

    @get:Rule
    val disableAnimationRule = DisableAnimationRule()

    @Before
    fun setUp() {
        runWithShellPermissionIdentity {
            screenTimeoutBeforeTest = Settings.System.getLong(
                context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT
            )
            Settings.System.putLong(
                context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT, 1800000L
            )
        }

        uiDevice.wakeUp()
        runShellCommand(instrumentation, "wm dismiss-keyguard")

        uiDevice.findObject(By.text("Close"))?.click()
        wasEnabled = setIndicatorsEnabledStateIfNeeded(true)
        // If the change Id is not present, then isChangeEnabled will return true. To bypass this,
        // the change is set to "false" if present.
        assumeFalse("feature not present on this device", callWithShellPermissionIdentity {
            CompatChanges.isChangeEnabled(PERMISSION_INDICATORS_NOT_PRESENT, Process.SYSTEM_UID)
        })
    }

    private fun setIndicatorsEnabledStateIfNeeded(shouldBeEnabled: Boolean): Boolean {
        var currentlyEnabled = false
        runWithShellPermissionIdentity {
            currentlyEnabled = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                INDICATORS_FLAG, true)
            if (currentlyEnabled != shouldBeEnabled) {
                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG,
                    shouldBeEnabled.toString(), false)
            }
        }
        return currentlyEnabled
    }

    @After
    fun tearDown() {
        if (!wasEnabled) {
            setIndicatorsEnabledStateIfNeeded(false)
        }
        runWithShellPermissionIdentity {
            Settings.System.putLong(
                context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT,
                screenTimeoutBeforeTest
            )
        }
        if (!isTv) {
            pressBack()
            pressBack()
        }
        pressHome()
        pressHome()
        Thread.sleep(3000)
    }

    private fun openApp(useMic: Boolean, useCamera: Boolean, useHotword: Boolean) {
        context.startActivity(Intent(INTENT_ACTION).apply {
            putExtra(USE_CAMERA, useCamera)
            putExtra(USE_MICROPHONE, useMic)
            putExtra(USE_HOTWORD, useHotword)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    @Test
    fun testCameraIndicator() {
        val manager = context.getSystemService(CameraManager::class.java)!!
        assumeTrue(manager.cameraIdList.isNotEmpty())
        testCameraAndMicIndicator(useMic = false, useCamera = true)
    }

    @Test
    fun testMicIndicator() {
        testCameraAndMicIndicator(useMic = true, useCamera = false)
    }

    @Test
    fun testHotwordIndicatorBehavior() {
        testCameraAndMicIndicator(useMic = false, useCamera = false, useHotword = true)
    }

    @Test
    fun testChainUsageWithOtherUsage() {
        // TV has only the mic icon
        assumeFalse(isTv)
        testCameraAndMicIndicator(useMic = false, useCamera = true, chainUsage = true)
    }

    private fun testCameraAndMicIndicator(
        useMic: Boolean,
        useCamera: Boolean,
        useHotword: Boolean = false,
        chainUsage: Boolean = false
    ) {
        var chainAttribution: AttributionSource? = null
        openApp(useMic, useCamera, useHotword)
        try {
            eventually {
                val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL))
                assertTrue("View with text $APP_LABEL not found", appView.exists())
            }
            if (chainUsage) {
                chainAttribution = createChainAttribution()
                runWithShellPermissionIdentity {
                    val ret = permissionManager.checkPermissionForStartDataDelivery(
                        Manifest.permission.RECORD_AUDIO, chainAttribution!!, "")
                    Assert.assertEquals(PermissionManager.PERMISSION_GRANTED, ret)
                }
            }

            if (isTv) {
                assertTvIndicatorsShown(useMic, useCamera, useHotword)
            } else if (packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
                assertCarIndicatorsShown(useMic, useCamera, useHotword, chainUsage)
            } else {
                // Hotword gets remapped to RECORD_AUDIO on handheld, so handheld should show a mic
                // indicator
                uiDevice.openQuickSettings()
                assertPrivacyChipAndIndicatorsPresent(useMic || useHotword, useCamera, chainUsage)
            }
        } finally {
            if (chainAttribution != null) {
                permissionManager.finishDataDelivery(Manifest.permission.RECORD_AUDIO,
                chainAttribution!!)
            }
        }
    }

    private fun assertTvIndicatorsShown(useMic: Boolean, useCamera: Boolean, useHotword: Boolean) {
        if (useMic || useHotword) {
            val found = WindowManagerStateHelper()
                .waitFor("Waiting for the mic indicator window to come up") {
                    it.containsWindow(TV_MIC_INDICATOR_WINDOW_TITLE) &&
                    it.isWindowVisible(TV_MIC_INDICATOR_WINDOW_TITLE)
                }
            if (useMic) {
                assertTrue("Did not find chip", found)
            } else {
                assertFalse("Found chip, but did not expect to", found)
            }
        }
        if (useCamera) {
            // There is no camera indicator on TVs.
        }
    }

    private fun assertCarIndicatorsShown(
        useMic: Boolean,
        useCamera: Boolean,
        useHotword: Boolean,
        chainUsage: Boolean
    ) {
        eventually {
            // Ensure the privacy chip is present (or not)
            var micPrivacyChip = uiDevice.findObject(By.res(CAR_MIC_PRIVACY_CHIP_ID))
            var cameraPrivacyChip = uiDevice.findObject(By.res(CAR_CAMERA_PRIVACY_CHIP_ID))
            if (useMic) {
                assertNotNull("Did not find mic chip", micPrivacyChip)
                // Click to chip to show the panel.
                micPrivacyChip.click()
            } else if (useCamera) {
                assertNotNull("Did not find camera chip", cameraPrivacyChip)
                // Click to chip to show the panel.
                cameraPrivacyChip.click()
            } else if (useHotword) {
                assertNull("Found mic chip, but did not expect to", micPrivacyChip)
                assertNull("Found camera chip, but did not expect to", cameraPrivacyChip)
            }
        }

        eventually {
            if (chainUsage) {
                assertChainMicAndOtherCameraUsed()
                return@eventually
            }
            if (useHotword) {
                // There should be no privacy panel when using hot word
                val micLabelView = uiDevice.findObject(UiSelector().textContains(micLabel))
                assertFalse("View with text $micLabel found, but did not expect to",
                    micLabelView.exists())
                val cameraLabelView = uiDevice.findObject(UiSelector().textContains(cameraLabel))
                assertFalse("View with text $cameraLabel found, but did not expect to",
                    cameraLabelView.exists())
                val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL))
                assertFalse("View with text $APP_LABEL found, but did not expect to",
                    appView.exists())
            } else if (useMic) {
                // There should be a mic privacy panel after mic privacy chip is clicked
                val micLabelView = uiDevice.findObject(UiSelector().textContains(micLabel))
                assertTrue("View with text $micLabel not found", micLabelView.exists())
                val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL))
                assertTrue("View with text $APP_LABEL not found", appView.exists())
            } else if (useCamera) {
                // There should be a camera privacy panel after camera privacy chip is clicked
                val cameraLabelView = uiDevice.findObject(UiSelector().textContains(cameraLabel))
                assertTrue("View with text $cameraLabel not found", cameraLabelView.exists())
                val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL))
                assertTrue("View with text $APP_LABEL not found", appView.exists())
            }
        }
    }

    private fun assertPrivacyChipAndIndicatorsPresent(
        useMic: Boolean,
        useCamera: Boolean,
        chainUsage: Boolean
    ) {
        // Ensure the privacy chip is present
        eventually {
            val privacyChip = uiDevice.findObject(UiSelector().resourceId(PRIVACY_CHIP_ID))
            assertTrue("view with id $PRIVACY_CHIP_ID not found", privacyChip.exists())
            privacyChip.click()
        }

        eventually {
            if (chainUsage) {
                assertChainMicAndOtherCameraUsed()
                return@eventually
            }
            if (useMic) {
                val iconView = uiDevice.findObject(UiSelector().descriptionContains(micLabel))
                assertTrue("View with description $micLabel not found", iconView.exists())
            }
            if (useCamera) {
                val iconView = uiDevice.findObject(UiSelector().descriptionContains(cameraLabel))
                assertTrue("View with text $APP_LABEL not found", iconView.exists())
            }
            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL))
            assertTrue("View with text $APP_LABEL not found", appView.exists())
        }
    }

    private fun createChainAttribution(): AttributionSource? {
        var attrSource: AttributionSource? = null
        runWithShellPermissionIdentity {
            try {
                val appUid = packageManager.getPackageUid(APP_PKG, 0)
                val childAttribution = AttributionSource(appUid, APP_PKG, null)
                val attribution = AttributionSource(Process.myUid(), context.packageName, null,
                    null, permissionManager.registerAttributionSource(childAttribution))
                attrSource = permissionManager.registerAttributionSource(attribution)
            } catch (e: PackageManager.NameNotFoundException) {
                Assert.fail("Expected to find a UID for $APP_LABEL")
            }
        }
        return attrSource
    }

    private fun assertChainMicAndOtherCameraUsed() {
        val shellLabel = try {
            context.packageManager.getApplicationInfo(SHELL_PKG, 0)
                .loadLabel(context.packageManager).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            "Did not find shell package"
        }

        val usageViews = uiDevice.findObjects(By.res(PRIVACY_ITEM_ID))
        assertEquals("Expected two usage views", 2, usageViews.size)
        val appViews = uiDevice.findObjects(By.textContains(APP_LABEL))
        assertEquals("Expected two $APP_LABEL view", 2, appViews.size)
        val shellView = uiDevice.findObjects(By.textContains(shellLabel))
        assertEquals("Expected only one shell view", 1, shellView.size)
    }

    private fun pressBack() {
        uiDevice.pressBack()
        waitForIdle()
    }

    private fun pressHome() {
        uiDevice.pressHome()
        waitForIdle()
    }

    private fun waitForIdle() =
        uiAutomation.waitForIdle(IDLE_TIMEOUT_MILLIS, TIMEOUT_MILLIS)
}