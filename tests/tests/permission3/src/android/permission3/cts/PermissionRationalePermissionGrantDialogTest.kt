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

package android.permission3.cts

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.CAMERA
import android.os.Build
import android.provider.DeviceConfig
import android.safetylabel.SafetyLabelConstants.PERMISSION_RATIONALE_ENABLED
import android.support.test.uiautomator.By
import androidx.test.filters.SdkSuppress
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.modules.utils.build.SdkLevel
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Permission rationale in Grant Permission Dialog tests. Permission rationale is only available on
 * U+
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class PermissionRationalePermissionGrantDialogTest : BaseUsePermissionTest() {

    @get:Rule
    val deviceConfigPermissionRationaleEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PERMISSION_RATIONALE_ENABLED,
            true.toString())

    @get:Rule
    val deviceConfigTestSafetyLabelDataEnabled =
        DeviceConfigStateChangerRule(
            context,
            DeviceConfig.NAMESPACE_PRIVACY,
            PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED,
            false.toString())

    @Before
    fun setup() {
        Assume.assumeTrue("Permission rationale is only available on U+", SdkLevel.isAtLeastU())
        Assume.assumeFalse(isAutomotive)
        Assume.assumeFalse(isTv)
        Assume.assumeFalse(isWatch)
    }

    @Test
    fun requestLocationPerm_flagDisabled_noPermissionRationale() {
        setDeviceConfigPrivacyProperty(PERMISSION_RATIONALE_ENABLED, false.toString())
        installPackageWithInstallSourceAndMetadata()

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_apkHasNoInstallSource_noPermissionRationale() {
        installPackageWithoutInstallSource()

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_noAppMetadata_noPermissionRationale() {
        installPackageWithInstallSourceAndNoMetadata()

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_invalidAppMetadata_noPermissionRationale() {
        installPackageWithInstallSourceAndInvalidMetadata()

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestCameraPerm_noPermissionRationale() {
        installPackageWithInstallSourceAndMetadata()

        assertAppHasPermission(CAMERA, false)

        requestAppPermissionsForNoResult(CAMERA) {
            assertPermissionRationaleOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_noAppMetadata_placeholderDataFlagEnabled_hasPermissionRationale() {
        setDeviceConfigPrivacyProperty(
            PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED, true.toString())
        installPackageWithInstallSourceAndNoMetadata()

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)
        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(true)
        }
    }

    @Test
    fun requestCoarseLocationPerm_hasPermissionRationale() {
        installPackageWithInstallSourceAndMetadata()

        assertAppHasPermission(ACCESS_COARSE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_COARSE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(true)
        }
    }

    @Test
    fun requestFineLocationPerm_hasPermissionRationale() {
        installPackageWithInstallSourceAndMetadata()

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {
            assertPermissionRationaleOnGrantDialogIsVisible(true)
        }
    }

    @Test
    fun requestLocationPerm_clicksPermissionRationale_startsPermissionRationaleActivity() {
        installPackageWithInstallSourceAndMetadata()

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {
            clickPermissionRationaleViewInGrantDialog()
            assertPermissionRationaleActivityTitleIsVisible(true)
            assertPermissionRationaleOnGrantDialogIsVisible(false)
        }
    }

    @Test
    fun requestLocationPerm_clicksPermissionRationale_startsPermissionRationaleActivity_comesBack(
    ) {
        installPackageWithInstallSourceAndMetadata()

        assertAppHasPermission(ACCESS_FINE_LOCATION, false)

        requestAppPermissionsForNoResult(ACCESS_FINE_LOCATION) {
            clickPermissionRationaleViewInGrantDialog()
            waitForIdle()
            assertPermissionRationaleActivityTitleIsVisible(true)
            pressBack()
            waitForIdle()
            assertPermissionRationaleActivityTitleIsVisible(false)
            assertPermissionRationaleOnGrantDialogIsVisible(true)
        }
    }

    private fun installPackageWithInstallSourceAndMetadata() {
        installPackageViaSession(
            apkName = APP_APK_NAME_31,
            appMetadata = AppMetadata.createDefaultAppMetadata())
    }

    private fun installPackageWithInstallSourceAndNoMetadata() {
        installPackageViaSession(apkName = APP_APK_NAME_31)
    }

    private fun installPackageWithInstallSourceAndInvalidMetadata() {
        installPackageViaSession(
            apkName = APP_APK_NAME_31,
            appMetadata = AppMetadata.createInvalidAppMetadata())
    }

    private fun installPackageWithoutInstallSource() {
        // TODO(b/257293222): Update/remove when hooking up PackageManager APIs
        installPackage(APP_APK_PATH_31)
    }

    private fun assertPermissionRationaleOnGrantDialogIsVisible(expected: Boolean) {
        findView(By.res(GRANT_DIALOG_PERMISSION_RATIONALE_CONTAINER_VIEW), expected = expected)
    }

    private fun assertPermissionRationaleActivityTitleIsVisible(expected: Boolean) {
        findView(By.res(PERMISSION_RATIONALE_ACTIVITY_TITLE_VIEW), expected = expected)
    }

    companion object {
        // TODO(b/257293222): Remove when hooking up PackageManager APIs
        private const val PRIVACY_PLACEHOLDER_SAFETY_LABEL_DATA_ENABLED =
            "privacy_placeholder_safety_label_data_enabled"
    }
}
