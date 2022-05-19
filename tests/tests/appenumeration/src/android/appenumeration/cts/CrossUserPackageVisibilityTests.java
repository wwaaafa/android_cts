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

package android.appenumeration.cts;

import static android.Manifest.permission.CHANGE_COMPONENT_ENABLED_STATE;
import static android.Manifest.permission.SUSPEND_APPS;
import static android.appenumeration.cts.Constants.TARGET_STUB;
import static android.appenumeration.cts.Constants.TARGET_STUB_APK;
import static android.appenumeration.cts.Constants.TARGET_STUB_SHARED_USER;
import static android.appenumeration.cts.Constants.TARGET_STUB_SHARED_USER_APK;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN;
import static android.content.pm.PackageManager.SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE;

import static com.android.compatibility.common.util.ShellUtils.runShellCommand;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.os.UserHandle;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.nene.users.UserReference;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Verify that app without holding the {@link android.Manifest.permission.INTERACT_ACROSS_USERS}
 * can't detect the existence of another app in the different users on the device via the
 * side channel attacks.
 */
@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4.class)
public class CrossUserPackageVisibilityTests {
    private static final String CTS_SHIM_PACKAGE_NAME = "com.android.cts.ctsshim";
    private static final String PROPERTY_BOOLEAN = "android.cts.PROPERTY_BOOLEAN";

    private static final ComponentName TEST_ACTIVITY_COMPONENT_NAME = ComponentName.createRelative(
            TARGET_STUB, "android.appenumeration.cts.TestActivity");

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private Context mContext;
    private PackageManager mPackageManager;
    private UserReference mCurrentUser;
    private UserReference mOtherUser;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        mPackageManager = mContext.getPackageManager();

        // Get users
        final UserReference primaryUser = sDeviceState.primaryUser();
        if (primaryUser.id() == UserHandle.myUserId()) {
            mCurrentUser = primaryUser;
            mOtherUser = sDeviceState.secondaryUser();
        } else {
            mCurrentUser = sDeviceState.secondaryUser();
            mOtherUser = primaryUser;
        }

        uninstallPackage(TARGET_STUB);
        uninstallPackage(TARGET_STUB_SHARED_USER);
    }

    @After
    public void tearDown() {
        uninstallPackage(TARGET_STUB);
        uninstallPackage(TARGET_STUB_SHARED_USER);
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    @Test
    public void testIsPackageSuspended_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.isPackageSuspended(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.isPackageSuspended(TARGET_STUB));
    }

    @Test
    public void testGetTargetSdkVersion_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getTargetSdkVersion(TARGET_STUB));
    }

    @Test
    public void testCheckSignatures_cannotDetectStubPkg() {
        final String selfPackageName = mContext.getPackageName();
        assertThat(mPackageManager.checkSignatures(selfPackageName, TARGET_STUB))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
        assertThat(mPackageManager.checkSignatures(TARGET_STUB, selfPackageName))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.checkSignatures(selfPackageName, TARGET_STUB))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
        assertThat(mPackageManager.checkSignatures(TARGET_STUB, selfPackageName))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
    }

    @Test
    public void testGetAllIntentFilters_cannotDetectStubPkg() {
        assertThat(mPackageManager.getAllIntentFilters(TARGET_STUB)).isEmpty();

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThat(mPackageManager.getAllIntentFilters(TARGET_STUB)).isEmpty();
    }

    @Test
    public void testGetInstallerPackageName_cannotDetectStubPkg() {
        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getInstallerPackageName(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getInstallerPackageName(TARGET_STUB));
    }

    @Test
    public void testGetInstallSourceInfo_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstallSourceInfo(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getInstallSourceInfo(TARGET_STUB));
    }

    @Test
    public void testGetApplicationEnabledSetting_cannotDetectStubPkg() {
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getApplicationEnabledSetting(TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getApplicationEnabledSetting(TARGET_STUB));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testGetApplicationEnabledSetting_canSeeHiddenUntilInstalled() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(SUSPEND_APPS);
        uninstallPackageForUser(CTS_SHIM_PACKAGE_NAME, mCurrentUser);
        mPackageManager.setSystemAppState(
                CTS_SHIM_PACKAGE_NAME, SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_HIDDEN);
        try {
            // no throw exception
            mPackageManager.getApplicationEnabledSetting(CTS_SHIM_PACKAGE_NAME);
        } finally {
            mPackageManager.setSystemAppState(
                    CTS_SHIM_PACKAGE_NAME, SYSTEM_APP_STATE_HIDDEN_UNTIL_INSTALLED_VISIBLE);
            installExistPackageForUser(CTS_SHIM_PACKAGE_NAME, mCurrentUser);
        }
    }

    @Test
    public void testGetComponentEnabledSetting_cannotDetectStubPkg() {
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getComponentEnabledSetting(TEST_ACTIVITY_COMPONENT_NAME));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.getComponentEnabledSetting(TEST_ACTIVITY_COMPONENT_NAME));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testSetApplicationEnabledSetting_cannotDetectStubPkg() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(CHANGE_COMPONENT_ENABLED_STATE);
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationEnabledSetting(
                        TARGET_STUB, COMPONENT_ENABLED_STATE_ENABLED, 0));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationEnabledSetting(
                        TARGET_STUB, COMPONENT_ENABLED_STATE_ENABLED, 0));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testSetComponentEnabledSetting_cannotDetectStubPkg() {
        final ComponentName componentName = ComponentName.createRelative(
                TARGET_STUB, "android.appenumeration.cts.TestActivity");
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(CHANGE_COMPONENT_ENABLED_STATE);
        final IllegalArgumentException e1 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setComponentEnabledSetting(
                        componentName, COMPONENT_ENABLED_STATE_ENABLED, 0));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        final IllegalArgumentException e2 = assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setComponentEnabledSetting(
                        componentName, COMPONENT_ENABLED_STATE_ENABLED, 0));
        assertThat(e1.getMessage()).isEqualTo(e2.getMessage());
    }

    @Test
    public void testCheckUidSignatures_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final int uidStub = mPackageManager.getPackageUid(
                TARGET_STUB, PackageInfoFlags.of(0));
        final int uidStubSharedUser = mPackageManager.getPackageUid(
                TARGET_STUB_SHARED_USER, PackageInfoFlags.of(0));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        assertThat(mPackageManager.checkSignatures(uidStub, uidStubSharedUser))
                .isEqualTo(PackageManager.SIGNATURE_UNKNOWN_PACKAGE);
    }

    @Test
    public void testHasUidSigningCertificate_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final PackageInfo stubInfo =
                mPackageManager.getPackageInfo(TARGET_STUB,
                        PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));
        final PackageInfo stubSharedUserInfo =
                mPackageManager.getPackageInfo(TARGET_STUB_SHARED_USER,
                        PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        assertThat(mPackageManager.hasSigningCertificate(
                stubInfo.applicationInfo.uid,
                stubInfo.signingInfo.getApkContentsSigners()[0].toByteArray(),
                PackageManager.CERT_INPUT_RAW_X509)).isFalse();
        assertThat(mPackageManager.hasSigningCertificate(
                stubSharedUserInfo.applicationInfo.uid,
                stubSharedUserInfo.signingInfo.getApkContentsSigners()[0].toByteArray(),
                PackageManager.CERT_INPUT_RAW_X509)).isFalse();
    }

    @Test
    public void testGetNameForUid_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final int uidStub = mPackageManager.getPackageUid(
                TARGET_STUB, PackageInfoFlags.of(0));
        final int uidStubSharedUser = mPackageManager.getPackageUid(
                TARGET_STUB_SHARED_USER, PackageInfoFlags.of(0));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        assertThat(mPackageManager.getNameForUid(uidStub)).isNull();
        assertThat(mPackageManager.getNameForUid(uidStubSharedUser)).isNull();
    }

    @Test
    public void testGetNamesForUids_cannotDetectStubPkgs() throws Exception {
        installPackage(TARGET_STUB_APK);
        installPackage(TARGET_STUB_SHARED_USER_APK);
        final int uidStub = mPackageManager.getPackageUid(
                TARGET_STUB, PackageInfoFlags.of(0));
        final int uidStubSharedUser = mPackageManager.getPackageUid(
                TARGET_STUB_SHARED_USER, PackageInfoFlags.of(0));

        uninstallPackageForUser(TARGET_STUB, mCurrentUser);
        uninstallPackageForUser(TARGET_STUB_SHARED_USER, mCurrentUser);

        final List<String> names = Arrays.asList(
                mPackageManager.getNamesForUids(new int[] {uidStub, uidStubSharedUser}));
        assertThat(names).hasSize(2);
        names.forEach(name -> assertThat(name).isNull());
    }

    @Test
    public void testGetPackageProperty_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TARGET_STUB));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TARGET_STUB));
    }

    @Test
    public void testGetComponentProperty_cannotDetectStubPkg() {
        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TEST_ACTIVITY_COMPONENT_NAME));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(PackageManager.NameNotFoundException.class,
                () -> mPackageManager.getProperty(PROPERTY_BOOLEAN, TEST_ACTIVITY_COMPONENT_NAME));
    }

    @Test
    public void testSetApplicationCategoryHint_cannotDetectStubPkg() {
        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationCategoryHint(
                        TARGET_STUB, ApplicationInfo.CATEGORY_PRODUCTIVITY));

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        assertThrows(IllegalArgumentException.class,
                () -> mPackageManager.setApplicationCategoryHint(
                        TARGET_STUB, ApplicationInfo.CATEGORY_PRODUCTIVITY));
    }

    @Test
    public void testGetUnsuspendablePackages_cannotDetectStubPkg() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity(SUSPEND_APPS);
        String [] unsuspendable =
                mPackageManager.getUnsuspendablePackages(new String[] {TARGET_STUB});
        assertThat(unsuspendable).asList().contains(TARGET_STUB);

        installPackageForUser(TARGET_STUB_APK, mOtherUser);

        unsuspendable = mPackageManager.getUnsuspendablePackages(new String[] {TARGET_STUB});
        assertThat(unsuspendable).asList().contains(TARGET_STUB);
    }

    private static void installPackage(String apkPath) {
        installPackageForUser(apkPath, null);
    }

    private static void installPackageForUser(String apkPath, UserReference user) {
        installPackageForUser(apkPath, user,
                InstrumentationRegistry.getInstrumentation().getContext().getPackageName());
    }

    private static void installPackageForUser(String apkPath, UserReference user,
            String installerPackageName) {
        assertThat(new File(apkPath).exists()).isTrue();
        final StringBuilder cmd = new StringBuilder("pm install ");
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        cmd.append("-i ").append(installerPackageName).append(" ");
        cmd.append(apkPath);
        final String result = runShellCommand(cmd.toString());
        assertThat(result.trim()).contains("Success");
    }

    private static void installExistPackageForUser(String apkPath, UserReference user) {
        final StringBuilder cmd = new StringBuilder("pm install-existing --user ");
        cmd.append(user.id()).append(" ");
        cmd.append(apkPath);
        final String result = runShellCommand(cmd.toString());
        assertThat(result.trim()).contains("installed");
    }

    private static void uninstallPackage(String packageName) {
        uninstallPackageForUser(packageName, null /* user */);
    }

    private static void uninstallPackageForUser(String packageName, UserReference user) {
        final StringBuilder cmd = new StringBuilder("pm uninstall ");
        if (user != null) {
            cmd.append("--user ").append(user.id()).append(" ");
        }
        cmd.append(packageName);
        runShellCommand(cmd.toString());
    }
}
