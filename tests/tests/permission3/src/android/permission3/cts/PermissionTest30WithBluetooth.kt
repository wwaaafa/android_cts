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

package android.permission3.cts

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.cts.BTAdapterUtils
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.os.BuildCompat
import androidx.test.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runtime Bluetooth-permission behavior of apps targeting API 30
 */
class PermissionTest30WithBluetooth : BaseUsePermissionTest() {

    val AUTHORITY = "android.permission3.cts.usepermission.AccessBluetoothOnCommand"
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private var bluetoothAdapterWasEnabled: Boolean = false

    private enum class BluetoothScanResult {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    @Before
    fun installApp() {
        installPackage(APP_APK_PATH_30_WITH_BLUETOOTH)
    }

    @Before
    fun enableBluetooth() {
        assumeTrue(supportsBluetooth())
        bluetoothAdapter = context.getSystemService(BluetoothManager::class.java).adapter
        bluetoothAdapterWasEnabled = bluetoothAdapter.isEnabled()
        assertTrue(BTAdapterUtils.enableAdapter(bluetoothAdapter, context))
        runShellCommandOrThrow("dumpsys activity service" +
                " com.android.bluetooth/.btservice.AdapterService set-test-mode enabled")
    }

    @After
    fun disableBluetooth() {
        assumeTrue(supportsBluetooth())
        runShellCommandOrThrow("dumpsys activity service" +
                " com.android.bluetooth/.btservice.AdapterService set-test-mode disabled")
        if (!bluetoothAdapterWasEnabled)
            assertTrue(BTAdapterUtils.disableAdapter(bluetoothAdapter, context))
    }

    @Test
    fun testGivenBluetoothIsDeniedWhenScanIsAttemptedThenThenGetEmptyScanResult() {
        assumeTrue(BuildCompat.isAtLeastS())
        revokeAppPermissions(android.Manifest.permission.BLUETOOTH_SCAN)
        assertEquals(BluetoothScanResult.EMPTY, scanForBluetoothDevices())
    }

    private fun scanForBluetoothDevices(): BluetoothScanResult {
        val resolver = InstrumentationRegistry.getTargetContext().getContentResolver()
        val result = resolver.call(AUTHORITY, "", null, null)
        return BluetoothScanResult.values()[result!!.getInt(Intent.EXTRA_INDEX)]
    }

    private fun supportsBluetooth(): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
}
