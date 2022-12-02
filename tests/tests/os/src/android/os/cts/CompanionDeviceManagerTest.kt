/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.os.cts

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.Instrumentation
import android.app.UiAutomation
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.PackageManager.FEATURE_AUTOMOTIVE
import android.content.pm.PackageManager.FEATURE_COMPANION_DEVICE_SETUP
import android.content.pm.PackageManager.FEATURE_LEANBACK
import android.net.MacAddress
import android.os.Binder
import android.os.Bundle
import android.os.Parcelable
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import android.util.Size
import android.util.SizeF
import android.util.SparseArray
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject2
import android.support.test.uiautomator.Until
import com.android.compatibility.common.util.MatcherUtils.hasIdThat
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UI_ROOT
import com.android.compatibility.common.util.UiAutomatorUtils
import com.android.compatibility.common.util.click
import com.android.compatibility.common.util.depthFirstSearch
import com.android.compatibility.common.util.textAsString
import com.android.compatibility.common.util.uiDump
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import java.io.Serializable
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

/**
 * Test for [CompanionDeviceManager]
 */
@RunWith(AndroidJUnit4::class)
class CompanionDeviceManagerTest {
    companion object {
        const val COMPANION_APPROVE_WIFI_CONNECTIONS =
                "android.permission.COMPANION_APPROVE_WIFI_CONNECTIONS"
        const val DUMMY_MAC_ADDRESS = "00:00:00:00:00:10"
        const val MANAGE_COMPANION_DEVICES = "android.permission.MANAGE_COMPANION_DEVICES"
        const val SHELL_PACKAGE_NAME = "com.android.shell"
        const val TEST_APP_PACKAGE_NAME = "android.os.cts.companiontestapp"
        const val TEST_APP_APK_LOCATION = "/data/local/tmp/cts/os/CtsCompanionTestApp.apk"
        const val CDM_UI_PACKAGE_NAME = "com.android.companiondevicemanager"
        const val RECYCLER_VIEW_CLASS = "androidx.recyclerview.widget.RecyclerView"

        val DEVICE_LIST_ITEM_SELECTOR: BySelector = By.res(CDM_UI_PACKAGE_NAME, "list_item_device")
        val DEVICE_LIST_SELECTOR: BySelector = By.pkg(CDM_UI_PACKAGE_NAME)
                .clazz(RECYCLER_VIEW_CLASS)
                .hasChild(DEVICE_LIST_ITEM_SELECTOR)
    }

    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val userId = context.userId
    private val packageName = context.packageName!!

    private val pm: PackageManager by lazy { context.packageManager }
    private val hasFeatureCompanionDeviceSetup: Boolean by lazy {
        pm.hasSystemFeature(FEATURE_COMPANION_DEVICE_SETUP)
    }

    private val cdm: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)
    }
    private val isAuto: Boolean by lazy { pm.hasSystemFeature(FEATURE_AUTOMOTIVE) }
    private val isTV: Boolean by lazy { pm.hasSystemFeature(FEATURE_LEANBACK) }

    @Before
    fun assumeHasFeature() {
        assumeTrue(hasFeatureCompanionDeviceSetup)
    }

    @After
    fun cleanUp() {
        // If the devices does not have the feature or is an Auto, the test didn't run, and the
        // clean up is not needed (will actually crash if the feature is missing).
        // See assumeHasFeature @Before method.
        if (!hasFeatureCompanionDeviceSetup || isAuto) return

        // Remove associations
        val associations = getAssociatedDevices(TEST_APP_PACKAGE_NAME)
        for (address in associations) {
            runShellCommandOrThrow(
                    "cmd companiondevice disassociate $userId $TEST_APP_PACKAGE_NAME $address")
        }

        // Uninstall test app
        uninstallAppWithoutAssertion(TEST_APP_PACKAGE_NAME)
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testIsDeviceAssociated() {
        assertFalse(isCdmAssociated(DUMMY_MAC_ADDRESS, packageName, MANAGE_COMPANION_DEVICES))
        assertFalse(isShellAssociated(DUMMY_MAC_ADDRESS, packageName))

        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            assertTrue(isCdmAssociated(DUMMY_MAC_ADDRESS, packageName, MANAGE_COMPANION_DEVICES))
            assertTrue(isShellAssociated(DUMMY_MAC_ADDRESS, packageName))
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testIsDeviceAssociatedWithCompanionApproveWifiConnectionsPermission() {
        assertTrue(isCdmAssociated(
            DUMMY_MAC_ADDRESS, SHELL_PACKAGE_NAME, MANAGE_COMPANION_DEVICES,
            COMPANION_APPROVE_WIFI_CONNECTIONS))
        assertFalse(isShellAssociated(DUMMY_MAC_ADDRESS, SHELL_PACKAGE_NAME))
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testDump() {
        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            val output = runShellCommand("dumpsys companiondevice")
            assertThat(output, containsString(packageName))
            assertThat(output, containsString(DUMMY_MAC_ADDRESS))
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    @Ignore("b/212535524")
    fun testRequestNotifications() {
        // Skip this test for Android TV due to NotificationAccessConfirmationActivity only exists
        // in Settings but not in TvSettings for Android TV devices (b/199224565).
        assumeFalse(isTV)

        installApk("--user $userId $TEST_APP_APK_LOCATION")
        startApp(TEST_APP_PACKAGE_NAME)

        uiDevice.waitAndFind(By.desc("name filter")).text = ""
        uiDevice.waitForIdle()

        click("Associate")

        uiDevice.wait(Until.findObject(DEVICE_LIST_SELECTOR), 20_000)
                ?.findObject(DEVICE_LIST_ITEM_SELECTOR)
                ?.click()
                ?: throw AssertionError("Empty device list")

        waitForIdle()

        val deviceForNotifications = getEventually({
            click("Request Notifications")
            waitFindNode(hasIdThat(containsString("button1")),
                    failMsg = "The Request Notifications dialog is not showing up",
                    timeoutMs = 5_000)
                    .assertNotNull { "Request Notifications is not implemented" }
        }, 60_000)

        deviceForNotifications!!.click()

        waitForIdle()
    }

    private fun getAssociatedDevices(
        pkg: String,
        user: UserHandle = android.os.Process.myUserHandle()
    ): List<String> {
        return runShellCommandOrThrow("cmd companiondevice list ${user.identifier}")
                .lines()
                .filter { it.startsWith(pkg) }
                .map { it.substringAfterLast(" ") }
    }

    private fun isShellAssociated(macAddress: String, packageName: String): Boolean {
        return runShellCommand("cmd companiondevice list $userId")
                .lines()
                .any {
                    packageName in it && macAddress in it
                }
    }

    private fun isCdmAssociated(
        macAddress: String,
        packageName: String,
        vararg permissions: String
    ): Boolean {
        return runWithShellPermissionIdentity(ThrowingSupplier {
            cdm.isDeviceAssociatedForWifiConnection(packageName,
                    MacAddress.fromString(macAddress), context.user)
        }, *permissions)
    }

    private fun UiDevice.waitAndFind(selector: BySelector): UiObject2 =
            wait(Until.findObject(selector), 1000)

    private fun click(label: String) {
        waitFindObject(byTextIgnoreCase(label)).click()
        waitForIdle()
    }

    private fun uninstallAppWithoutAssertion(packageName: String) {
        runShellCommandOrThrow("pm uninstall $packageName")
    }

    private fun installApk(apk: String) {
        assertThat(runShellCommandOrThrow("pm install -r $apk"), containsString("Success"))
    }

    /**
     * For some reason waitFindObject sometimes fails to find UI that is present in the view hierarchy
     */
    private fun waitFindNode(
            matcher: Matcher<AccessibilityNodeInfo>,
            failMsg: String? = null,
            timeoutMs: Long = 10_000
    ): AccessibilityNodeInfo {
        return getEventually({
            val ui = UI_ROOT
            ui.depthFirstSearch { node ->
                matcher.matches(node)
            }.assertNotNull {
                buildString {
                    if (failMsg != null) {
                        appendLine(failMsg)
                    }
                    appendLine("No view found matching $matcher:\n\n${uiDump(ui)}")
                }
            }
        }, timeoutMs)
    }

    private fun waitFindObject(selector: BySelector): UiObject2 {
        return waitFindObject(instrumentation.uiAutomation, selector)
    }

    private fun waitFindObject(uiAutomation: UiAutomation, selector: BySelector): UiObject2 {
        try {
            return UiAutomatorUtils.waitFindObject(selector)
        } catch (e: RuntimeException) {
            val ui = uiAutomation.rootInActiveWindow

            val title = ui.depthFirstSearch { node ->
                node.viewIdResourceName?.contains("alertTitle") == true
            }
            val okButton = ui.depthFirstSearch { node ->
                node.textAsString?.equals("OK", ignoreCase = true) ?: false
            }

            if (title?.text?.toString() == "Android System" && okButton != null) {
                // Auto dismiss occasional system dialogs to prevent interfering with the test
                okButton.click()
                return UiAutomatorUtils.waitFindObject(selector)
            } else {
                throw e
            }
        }
    }

    private fun byTextIgnoreCase(txt: String): BySelector {
        return By.text(Pattern.compile(txt, Pattern.CASE_INSENSITIVE))
    }

    private fun waitForIdle() {
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(1000, 10000)
    }

    private inline fun <T> eventually(crossinline action: () -> T): T {
        val res = AtomicReference<T>()
        SystemUtil.eventually {
            res.set(action())
        }
        return res.get()
    }

    private fun awaitAppState(pkg: String, stateMatcher: Matcher<Int>) {
        val context: Context = InstrumentationRegistry.getTargetContext()
        eventually {
            runWithShellPermissionIdentity {
                val packageImportance = context
                        .getSystemService(ActivityManager::class.java)!!
                        .getPackageImportance(pkg)
                assertThat(packageImportance, stateMatcher)
            }
        }
    }

    private fun startApp(packageName: String) {
        val context = InstrumentationRegistry.getTargetContext()
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        context.startActivity(intent)
        awaitAppState(packageName, Matchers.lessThanOrEqualTo(IMPORTANCE_TOP_SLEEPING))
        waitForIdle()
    }

    private inline fun <T> T?.assertNotNull(errorMsg: () -> String): T {
        return if (this == null) throw AssertionError(errorMsg()) else this
    }
}

operator fun Bundle.set(key: String, value: Any?) {
    if (value is Array<*> && value.isArrayOf<Parcelable>()) {
        putParcelableArray(key, value as Array<Parcelable>)
        return
    }
    if (value is Array<*> && value.isArrayOf<CharSequence>()) {
        putCharSequenceArray(key, value as Array<CharSequence>)
        return
    }
    when (value) {
        is Byte -> putByte(key, value)
        is Char -> putChar(key, value)
        is Short -> putShort(key, value)
        is Float -> putFloat(key, value)
        is CharSequence -> putCharSequence(key, value)
        is Parcelable -> putParcelable(key, value)
        is Size -> putSize(key, value)
        is SizeF -> putSizeF(key, value)
        is ArrayList<*> -> putParcelableArrayList(key, value as ArrayList<Parcelable>)
        is SparseArray<*> -> putSparseParcelableArray(key, value as SparseArray<Parcelable>)
        is Serializable -> putSerializable(key, value)
        is ByteArray -> putByteArray(key, value)
        is ShortArray -> putShortArray(key, value)
        is CharArray -> putCharArray(key, value)
        is FloatArray -> putFloatArray(key, value)
        is Bundle -> putBundle(key, value)
        is Binder -> putBinder(key, value)
        else -> throw IllegalArgumentException("" + value)
    }
}