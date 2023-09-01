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

package android.content.pm.cts;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.IIntentReceiver;
import android.content.IIntentSender;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageArchiver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.platform.test.annotations.AppModeFull;
import android.text.TextUtils;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.Until;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

// TODO(b/290775207) Add more test cases
@AppModeFull
@RunWith(AndroidJUnit4.class)
public class PackageArchiverTest {

    private static final String SAMPLE_APK_BASE = "/data/local/tmp/cts/content/";
    private static final String PACKAGE_NAME = "android.content.cts.mocklauncherapp";
    private static final String ACTIVITY_NAME = PACKAGE_NAME + ".Launcher";
    private static final String APK_PATH = SAMPLE_APK_BASE + "CtsContentMockLauncherTestApp.apk";

    @Rule
    public final Expect expect = Expect.create();

    private Context mContext;
    private UiDevice mUiDevice;

    private PackageArchiver mPackageArchiver;
    private StorageStatsManager mStorageStatsManager;
    private ArchiveIntentSender mIntentSender;

    @Before
    public void setup() throws Exception {
        assumeTrue("Form factor is not supported", isFormFactorSupported());
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mUiDevice = UiDevice.getInstance(
                androidx.test.InstrumentationRegistry.getInstrumentation());
        mContext = instrumentation.getContext();
        mPackageArchiver = mContext.getPackageManager().getPackageArchiver();
        mStorageStatsManager = mContext.getSystemService(StorageStatsManager.class);
        mIntentSender = new ArchiveIntentSender();
    }

    @After
    public void uninstall() {
        uninstallPackage();
    }

    @Test
    public void archiveApp_dataIsKept() throws Exception {
        installPackage();
        // This creates a data directory which will be verified later.
        launchTestActivity();
        PackageInfo packageInfo = getPackageInfo();


        runWithShellPermissionIdentity(
                () -> mPackageArchiver.requestArchive(PACKAGE_NAME,
                        new IntentSender((IIntentSender) mIntentSender)),
                Manifest.permission.DELETE_PACKAGES);

        assertThat(mIntentSender.mPackage.get()).isEqualTo(PACKAGE_NAME);
        assertThat(mIntentSender.mStatus.get()).isEqualTo(PackageInstaller.STATUS_SUCCESS);
        StorageStats stats =
                runWithShellPermissionIdentity(() ->
                                mStorageStatsManager.queryStatsForPackage(
                                        packageInfo.applicationInfo.storageUuid,
                                        packageInfo.packageName,
                                        UserHandle.of(UserHandle.myUserId())),
                        Manifest.permission.PACKAGE_USAGE_STATS);
        // This number is bound to fluctuate as the data created during app startup will change
        // over time. We only need to verify that the data directory is kept.
        assertTrue(stats.getDataBytes() > 0L);
    }

    @Test
    public void archiveApp_noInstaller() {
        installAppWithNoInstaller();

        PackageManager.NameNotFoundException e =
                runWithShellPermissionIdentity(
                        () -> assertThrows(
                                PackageManager.NameNotFoundException.class,
                                () -> mPackageArchiver.requestArchive(PACKAGE_NAME,
                                        new IntentSender((IIntentSender) mIntentSender))),
                        Manifest.permission.DELETE_PACKAGES);

        assertThat(e).hasMessageThat()
                .isEqualTo(TextUtils.formatSimple("No installer found to archive app %s.",
                        PACKAGE_NAME));
    }

    private static String installAppWithNoInstaller() {
        return SystemUtil.runShellCommand(String.format("pm install -r -t -g %s", APK_PATH));
    }

    private void launchTestActivity() {
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        mContext.startActivity(createTestActivityIntent(PACKAGE_NAME, ACTIVITY_NAME),
                options.toBundle());
        mUiDevice.wait(Until.hasObject(By.clazz(PACKAGE_NAME, ACTIVITY_NAME)),
                TimeUnit.SECONDS.toMillis(5));
    }

    private Intent createTestActivityIntent(String pkgName, String className) {
        final Intent intent = new Intent();
        intent.setClassName(pkgName, className);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private void uninstallPackage() {
        SystemUtil.runShellCommand(
                String.format("pm uninstall %s", PACKAGE_NAME));
    }

    private void installPackage() {
        assertEquals("Success\n", SystemUtil.runShellCommand(
                String.format("pm install -r -i %s -t -g %s", mContext.getPackageName(),
                        APK_PATH)));
    }

    private PackageInfo getPackageInfo() {
        try {
            return mContext.getPackageManager().getPackageInfo(
                    PACKAGE_NAME, /* flags= */ 0);
        } catch (PackageManager.NameNotFoundException e) {
            fail("Package " + PACKAGE_NAME + " not installed for user "
                    + mContext.getUser() + ": " + e);
        }
        return null;
    }

    private static boolean isFormFactorSupported() {
        return !FeatureUtil.isArc()
                && !FeatureUtil.isAutomotive()
                && !FeatureUtil.isTV()
                && !FeatureUtil.isWatch()
                && !FeatureUtil.isVrHeadset();
    }

    static class ArchiveIntentSender extends IIntentSender.Stub {

        final CompletableFuture<String> mPackage = new CompletableFuture<>();
        final CompletableFuture<Integer> mStatus = new CompletableFuture<>();
        final CompletableFuture<String> mMessage = new CompletableFuture<>();

        @Override
        public void send(int code, Intent intent, String resolvedType,
                IBinder whitelistToken, IIntentReceiver finishedReceiver,
                String requiredPermission, Bundle options) throws RemoteException {
            mPackage.complete(intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME));
            mStatus.complete(intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -100));
            mMessage.complete(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
        }

    }
}
